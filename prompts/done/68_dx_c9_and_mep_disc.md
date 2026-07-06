# DONE
# DX Duplex — C9 Non-Issue Note + MEP Discipline Classification

**Priority:** Data completeness. DX has 904 MEP elements already extracted and
round-tripping, but they're not discipline-classified. C9 axis failures (89)
are a matching artifact, not a real geometry error — document and move on.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Never invent.

## Read first

1. `docs/DuplexAnalysis.md` — the entire file. Understand the mirror model:
   half-unit + twin rotated π around X=4.4. 485 paired + 129 shared = 1099.
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.1–§10.4.4 — the flat discipline model.
   Disciplines are AD_Org on the line, not a BOM level. FLOOR→LEAF with grouping.
3. `IFCtoBOM/src/main/resources/classify_dx.yaml` — note lines 62-67 (disciplines
   commented out) and composition section (MIRRORED_PAIR).
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` lines 245-295 —
   RE path dispatches to Scope + Composition + Structural. CO path uses
   DisciplineBomBuilder. DX is RE.
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java` — the
   post-S100-p66 flat model (FLOOR→LEAF, no SET level). Study how it writes
   discipline-grouped LEAFs with VerbFactorizer.
6. `scripts/run_RosettaStones.sh` lines 464-519 — C9 matching algorithm. Uses
   ROW_NUMBER() position-sorted rank, not GUID. Understand why this fails for
   mirrored buildings.

## Context: Why C9 Fails for DX (89 elements)

C9 matches ref vs output elements by `ROW_NUMBER() OVER (PARTITION BY ifc_class
ORDER BY minX, minY, minZ, W, D, H, maxX, maxY, maxZ)`. For the mirrored duplex:

- Element counts match perfectly (57 IfcWall ref = 57 out, 21 IfcSlab = 21)
- But position-sort rank assigns different row numbers in ref vs output when
  elements have similar X positions near the party wall (X ≈ 4.4)
- Result: ref element N gets paired with a different output element N
- The W↔D "swaps" are actually **different walls being compared** (e.g.,
  ref `Exterior Brick 417×16966` paired with output `Interior Partition 4201×152`)

This is NOT a geometry error. It's a C9 matching limitation for mirrored buildings.
Walker rotation was already verified (DuplexAnalysis.md §Resolved Issue #4: "0
geometry divergences"). The same limitation would affect any MIRRORED_PAIR building.

## Task 1: Document C9 as Non-Issue in DuplexAnalysis.md

Add a new resolved issue in `docs/DuplexAnalysis.md` §Resolved Issues:

```markdown
### 5. C9 Axis Dimension — Matching Artifact (Not a Geometry Error)

C9 reports 89 wall/slab axis mismatches. Root cause: C9 matches elements by
position-sorted rank (`ROW_NUMBER` partitioned by `ifc_class`), not by GUID.
For mirrored buildings, elements near the party wall (X ≈ 4.4) have similar
positions, causing rank shuffles that pair different element types together.

Evidence: element counts match exactly (1099 ref = 1099 out), walker rotation
verified (§Resolved #4: 0 divergences), and the "mismatched" pairs show different
element names (e.g., ref `Exterior Brick` vs output `Interior Partition`).

**Status:** Non-issue. C9 matching needs GUID-based pairing to work correctly
for MIRRORED_PAIR buildings. Filed as future enhancement — does not affect
compilation correctness.
```

## Task 2: Enable DX MEP Discipline Classification

DX has 904 MEP elements from four IFC files (Architecture, Mechanical, MEP,
Plumbing) already federated into the extraction. They currently flow through
the RE structural path as undifferentiated LEAF lines. The goal is to classify
them by discipline using the flat model from §10.4.

### 2a. Uncomment and complete the disciplines section in classify_dx.yaml

```yaml
  disciplines:
    ARC: [IfcWall, IfcDoor, IfcWindow, IfcFurnishingElement, IfcCovering, IfcSlab, IfcRoof]
    STR: [IfcBeam, IfcMember, IfcPlate, IfcStairFlight, IfcRailing]
    MEP: [IfcFlowController, IfcFlowFitting, IfcFlowSegment, IfcFlowTerminal]
```

Note: DX MEP is lightweight (no 8-discipline split like TE). Three disciplines
are sufficient: ARC (architectural), STR (structural), MEP (all mechanical/
electrical/plumbing combined). The federated IFC doesn't separate plumbing from
HVAC at the element level.

### 2b. Add discipline column to I_Element_Extraction for DX

The RE path currently doesn't populate `I_Element_Extraction` (it goes straight
to BOM). For discipline classification to work, DX needs the extraction table.

**Investigation needed:** Check how TE populates `I_Element_Extraction.discipline`.
Trace from `IFCtoBOMPipeline.java` → extraction phase → how `discipline` is set.
DX extraction must populate the same column using the YAML disciplines map
(ifc_class → discipline code).

### 2c. Hybrid path: RE structural + discipline grouping

DX cannot switch to pure CO path — it needs Scope (room furniture) +
Composition (mirror) + Structural. But MEP elements should carry discipline
metadata.

**Approach:** After `StructuralBomBuilder.build()` writes FLOOR→LEAF lines for
MEP elements, the discipline code should be recorded on each `m_bom_line` row.
Check if `m_bom_line` already has a discipline or `ad_org_id` column. If not,
this is a schema question — investigate and document findings, don't add columns
without checking DATA_MODEL.md and BBC.md §3.

**Key constraint:** Do NOT change the RE path dispatch. DX stays RE. The
discipline classification is metadata on existing lines, not a new tree level.

## Task 3: Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_dx.yaml` — should still produce
   7/8 PASS (C9 remains a matching artifact). Element counts must stay 1099.
   MEP elements must still round-trip.
3. Check DX_BOM.db for discipline metadata on MEP lines (if schema allows).

## What NOT to do

- Do NOT change C9 matching algorithm (that's a separate prompt if ever needed)
- Do NOT switch DX from RE to CO path
- Do NOT add a DISCIPLINE SET BOM level (§10.4 says flat)
- Do NOT modify the mirror/composition logic
- Do NOT break SH (run `./scripts/run_RosettaStones.sh classify_sh.yaml` to verify)
- Do NOT modify sacred files unless absolutely necessary

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings after `# DONE`:
- What discipline columns exist in m_bom_line (if any)
- How TE populates I_Element_Extraction.discipline
- Whether DX extraction currently writes to I_Element_Extraction at all
- What schema changes (if any) are needed for DX discipline metadata

---

## Findings (S100-p68)

### What discipline columns exist in m_bom_line

**None.** The m_bom_line table has 35 columns (bom_child_id through scale_band)
but no `discipline`, `ad_org_id`, or equivalent column. The DisciplineBomBuilder
(CO path) uses discipline as an in-memory grouping key for VerbFactorizer, but
the written LEAF lines carry no discipline marker.

### How TE populates I_Element_Extraction.discipline

**I_Element_Extraction does not exist as a persistent table.** R13 (refactoring)
removed the table. ExtractionPopulator.populate() reads `elements_meta.discipline`
from the reference DB and passes it in-memory as `ExtractionElement.discipline()`.
The discipline value originates from the federated IFC model metadata (the
reference extraction DB has a `discipline` column on `elements_meta`). For TE,
each of the 8 federated IFC files carries its own discipline code, which is
written into elements_meta during Bonsai extraction.

For DX, the reference DB (`Duplex_extracted.db`) already has discipline
values: ARC, MEP, STR — correctly populated from the 4 federated IFC files.

### Whether DX extraction currently writes to I_Element_Extraction at all

**No.** R13 eliminated the persistent I_Element_Extraction table entirely.
All enrichment is in-memory via ExtractionPopulator.populate() which returns
`Map<String, List<ExtractionElement>>`. The discipline field flows through the
in-memory pipeline: ref DB → ExtractionPopulator → ExtractionElement.discipline()
→ BOM builders. Both RE and CO paths receive elements with discipline populated.

### What schema changes are needed for DX discipline metadata

**m_bom_line needs a discipline (or ad_org_id) column** to persist discipline
classification on LEAF lines. Currently:

- **CO path (DisciplineBomBuilder):** Groups elements by discipline for verb
  factorization but writes LEAF lines without discipline column. The grouping
  is used only for VerbFactorizer input partitioning.
- **RE path (StructuralBomBuilder):** Does not group by discipline at all.
  All elements flow through VerbFactorizer as one batch per floor.

The flat discipline model (DISC_VALIDATION_DB_SRS.md section 10.4) says
"Disciplines are AD_Org on the line, not a BOM level." This means m_bom_line
should carry `ad_org_id INTEGER` referencing the AD_Org table. This column
addition requires:

1. A migration SQL (append-only to migration/) adding `ad_org_id` to m_bom_line
2. DDL update in schema_snapshot_bom.sql
3. VerbFactorizer.insertLeafLine() to accept and write discipline/ad_org_id
4. StructuralBomBuilder to pass discipline from ExtractionElement to VerbFactorizer

**Scope:** This is a schema change requiring DATA_MODEL.md and BBC.md section 3
review before implementation. Deferred to a follow-up prompt.

### Verification results

- `mvn compile -q`: PASS
- DX Rosetta stones: 6/7 PASS (C9 FAIL = 89, expected matching artifact)
- Element count: 1099 (unchanged)
- SH Rosetta stones: 7/7 PASS (no regression)
- classify_dx.yaml: schema_version bumped 1 → 2, disciplines section uncommented
