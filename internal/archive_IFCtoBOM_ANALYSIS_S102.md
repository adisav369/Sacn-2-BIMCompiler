# IFCtoBOM Extraction Analysis — S102 Fleet Forensics

**Session:** S102 | **Date:** 2026-03-31 | **Fleet:** 34 buildings (DM excluded)
**Commits:** `77e8aa8d`, `d0fe8fa4`, `a7488edb`

---

## 1. Discipline OrderLines — Code Path (empirical)

**Question:** How are DISCs fired? Which method first touches them?

### Entry point

`CompilationPipeline.java:446` — RouteStage fires the Callout:

```java
// CompilationPipeline.java:446-458
int inserted = OrderLineProductCallout.onProductChanged(
        compileDb, erpDb, orderId, productCategory);     // PHASE 1
OrderLineProductCallout.applyYamlOverrides(compileDb, erpDb, orderId);  // PHASE 2
int expanded = OrderLineProductCallout.expandDisciplineLines(compileDb, erpDb, orderId);  // PHASE 3
```

### Phase 1: `onProductChanged()` — `OrderLineProductCallout.java:43-134`

Reads **hardcoded** category defaults (line 52-54):
- CO: `{FP, ELEC, ACMV, CW, SP, LPG}` (all 6 MEP)
- RE: `{ELEC, SP}` (2 only)
- IN: `{}` (none)

Then queries ERP.db for discipline BOM recipes (line 82):
```sql
SELECT Value, Name, AD_Org_ID FROM M_BOM WHERE AD_Org_ID > 0 ORDER BY AD_Org_ID
```

For each discipline in the default set, inserts a DISCIPLINE OrderLine via
`insertDisciplineLine()` (line 356-378):
```sql
INSERT INTO C_OrderLine (
  C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type,
  m_product_category_id, dx, dy, dz, M_Product_ID, Discipline, AD_Org_ID, Qty, locator_ref
) VALUES (?, ?, ?, ?, 'DISCIPLINE', ?, 0, 0, 0, ?, ?, ?, 0, ?)
```

`host_type='DISCIPLINE'`, `Qty=0` (fill-all directive), `dx/dy/dz=0` (parasitic).

### Phase 2: `applyYamlOverrides()` — `OrderLineProductCallout.java:143-182`

Reads `ad_sysconfig` in compile DB:
- `REMOVE_DISCIPLINES` → deactivates matching DISC lines
- `ADD_DISCIPLINES` → inserts missing disciplines

### Phase 3: `expandDisciplineLines()` — `OrderLineProductCallout.java:233-294`

For each DISCIPLINE OrderLine, reads BOM children from ERP.db (line 262):
```sql
SELECT bl.child_product_id, bl.qty, bl.verb_ref
FROM M_BOM_Line bl JOIN M_BOM b ON bl.M_BOM_ID = b.M_BOM_ID
WHERE b.Value = ? AND bl.IsActive = 'Y' ORDER BY bl.sequence
```

Inserts LEAF children with `host_type='LEAF'`, inheriting `AD_Org_ID` from parent.

### After Callout: Route execution

`CompilationPipeline.java:461-481` — queries DISC list and fires routing:
```java
List<String> disciplines = queryDisciplineList(compileDb, orderId);
// SQL: SELECT DISTINCT Discipline FROM C_OrderLine
//      WHERE C_Order_ID = ? AND host_type = 'DISCIPLINE'
RouteExecutor.RouteReport report = executor.executeRoutes(compileDb, disciplines, storeyZBands);
```

### TE verified output

```
DISC  | AD_Org | Lines | Elements | Via
------|--------|-------|----------|----
ARC   |      1 |   478 |   34,731 | BOM walk (not Callout — ARC is the root OrderLine)
STR   |      2 |    61 |    1,430 | BOM walk (structural BOMs under building)
FP    |      3 |   110 |    6,866 | Callout → expandDisciplineLines → RouteExecutor
ELEC  |      4 |   107 |    1,172 | Callout → expandDisciplineLines → RouteExecutor
ACMV  |      5 |   144 |    1,621 | Callout → expandDisciplineLines → RouteExecutor
CW    |      6 |   188 |    1,431 | Callout → expandDisciplineLines → RouteExecutor
SP    |      7 |   418 |      979 | Callout → expandDisciplineLines → RouteExecutor
LPG   |      8 |    26 |      209 | Callout → expandDisciplineLines → RouteExecutor
```

**All 8 DISCs in `c_orderline` in output.db.** ARC+STR come from BOM walk,
6 MEP DISCs come from Callout.

### Qualification

The 6 MEP Callout-generated DISCs produce OrderLines with `Qty` from ERP.db
BOM recipes, NOT from IFC element counts. The BOM walk places elements using
tack chain positions from the extraction. The Callout adds discipline metadata
but does NOT compute new positions — positions come from the tack chain.

---

## 2. LMP Compliance — Qualified Assessment

**Question:** Is the compiler cheating? Are positions copied from input?

### What the compiler DOES

`PlacementCollectorVisitor.java` walks the BOM tree. For each LEAF line
(line 375-440):
1. Accumulates `dx/dy/dz` from parent → child → leaf (tack chain)
2. Applies `allocated_width_mm/depth_mm/height_mm` for AABB sizing
3. Writes position to `elements_rtree` in output.db

The tack chain accumulates parent offsets:
```
compiled_pos = building_origin + floor_dz + room_dx/dy + element_dx/dy/dz
```

### What GEO proves

`PlacementCollectorVisitor.java:844-946` — `emitGeoSummary()`:

1. Collects compiled placements with IFC GUIDs (line 853):
   ```java
   if (p.elementRef() != null && IFC_GUID.matcher(p.elementRef()).matches())
   ```
2. Opens extraction DB, loads source positions (line 870):
   ```sql
   SELECT r.minX, r.minY, r.minZ FROM elements_meta m
   JOIN elements_rtree r ON m.id = r.id WHERE m.guid = ?
   ```
3. Computes relative offset drift (line 942-946):
   ```java
   double drift = Math.max(
     Math.abs((cI[0]-cJ[0]) - (eI[0]-eJ[0])),  // X delta
     Math.abs((cI[1]-cJ[1]) - (eI[1]-eJ[1])),  // Y delta
     Math.abs((cI[2]-cJ[2]) - (eI[2]-eJ[2]))   // Z delta
   ) * 1000.0;  // convert to mm
   ```

GEO compares **relative offsets between pairs**, not absolute positions.
This cancels world origin and proves the tack chain preserves spatial
relationships.

### What GEO does NOT prove

- **Route correctness:** TE output.db has 258 `system_edges`, 264 `system_nodes`.
  TE extraction has 8,056 MEP elements (IfcPipeFitting 4,243 + IfcPipeSegment
  3,821 + others). Route coverage: 258/8,056 = **3.2%**. The RouteBuilders
  produce a skeleton (riser→header→branch), not per-element routing.
- **P15/P16/P17 proofs:** Gated by `hasRelationalData` in ProveStage. Not
  firing for TE. P16 (waste gradient) and P17 (connectivity) are unverified.
- **Positions are from tack chain, not route computation.** The 48K elements
  get their positions from `m_bom_line.dx/dy/dz` (BOM-relative offsets
  computed during extraction), NOT from route algorithms. Routing adds
  system connectivity edges but does not override tack positions.

### Honest assessment

GEO proves the BOM tack chain reproduces IFC spatial relationships to 0.025mm
for TE (48K elements, 643M sibling-pairs). This is real — no cheating, no
copying. The positions flow through `m_bom_line.dx/dy/dz` → `PlacementCollectorVisitor`
→ `elements_rtree`.

But the **route architecture is at skeleton stage**. 258 edges for 8K MEP
elements = 3% coverage. The spec (DISC_VALIDATION_DB_SRS §10.4.12) describes
complete routing; the code delivers a proof-of-concept.

---

## 3. elementRef vs GUID — Code Path (empirical)

**Question:** Where stored when BOMs are factored?

### Resolution cascade in BOM walker

`PlacementCollectorVisitor.java:375-397`:

```java
// PRIORITY 1: MA GUID (IFC format only)
if (maGuids != null && qi < maGuids.length && maGuids[qi] != null
        && IFC_GUID.matcher(maGuids[qi]).matches()) {
    elementRef = maGuids[qi];
}
// PRIORITY 2: BOM line element_ref
if (elementRef == null) {
    elementRef = line.getElementRef();
    // PRIORITY 3: product_id + ordinal
    if (elementRef == null || elementRef.isEmpty()) {
        elementRef = (product != null ? product.getProductId() : productId)
            + ":" + (++ordinalCounter);
    }
}
```

### MA GUID loading

`PlacementCollectorVisitor.java:642-663`:
```sql
SELECT qi, guid FROM m_bom_line_ma WHERE bom_id = ? AND sequence = ? ORDER BY qi
```

Returns array indexed by expansion index (qi). One GUID per element instance
within a factored line.

### Factored BOM storage

```
m_bom_line (factored)              m_bom_line_ma (per-instance)
+-----------------------------+    +-----------------------------+
| bom_child_id: 42            |    | bom_child_id: 42            |
| child_product_id: BEAM_A    |    | qi: 0                       |
| qty: 15                     |    | guid: 09nghOD7H61f8AyUeT... | ← IFC GUID
| element_ref: BEAM_A         |    +-----------------------------+
+-----------------------------+    | qi: 1                       |
                                   | guid: 09nghOD7H61f8AyUeU... |
                                   +-----------------------------+
                                   ... (15 rows)
```

### GUID chain (extraction → BOM → output)

| Step | File:line | Source | Destination |
|------|-----------|--------|-------------|
| 1 | `ExtractionPopulator.java:154` | `elements_meta.guid` | `RawElement.guid()` |
| 2 | `ExtractionPopulator.java:259` | `RawElement.guid()` | `ExtractionRow.guid` |
| 3 | `ExtractionPopulator.java:107` | `ExtractionRow.guid` | `ExtractionElement.guid()` |
| 4 | `VerbFactorizer.java:299` | `elements.get(i).guid()` | `insertMaRow()` |
| 5 | `VerbFactorizer.java:317` | `guid` param | `m_bom_line_ma.guid` (SQL INSERT) |

No transformations. No COMPACT_MD substitution in this chain.

---

## 4. GEO (white-box) vs Proofs (black-box)

**Question:** Are different proofs good enough?

### Proofs = black-box

BIMEyes proofs check output properties without tracing the computation:
- P04 (`StoreyZBandProof`): Is element within storey Z-band?
- P05 (`DuplicatePositionProof`): Any two elements at same position?
- P06 (`OverlapProof`): Any same-class elements overlapping?
- P10 (`ShapeIdentityProof`): Planarity within threshold?

A building could pass all proofs with fabricated positions that happen to
satisfy constraints. Proofs detect symptoms, not causes.

### GEO = white-box

GEO traces the actual BOM walk. `PlacementCollectorVisitor.java`:
- Line 304: `ENTER` — logs tack chain at each BOM node entry
- Line 310: `CHAIN` — logs full ancestor path
- Line 318: `EXIT` — logs node completion
- Line 844: `SUMMARY` — compares compiled vs extraction positions

GEO can't be fooled because it compares the compiler's output positions
against the IFC extraction source directly. Drift > 1mm = the tack chain
lost spatial fidelity.

### The qualification

GEO proves tack chain placement correctness. It does NOT prove:
- Route computation (skeleton only, 3% coverage)
- Material quantities
- Assembly completeness
- Discipline assignment correctness

PATTERN proves storey assignment at extraction time — before the tack
chain starts. Together: PATTERN (extraction quality) + GEO (compilation
quality) = the full picture.

---

## 5. Non-Standard GUIDs — Root Cause (corrected)

**Previous claim (WRONG):** "COMPACT_MD is generated by the VerbFactorizer."

**Actual root cause:** The `COMPACT_MD` / `FRAME_MD` / `STR_MD` identifiers
are in the **extraction DB itself** (`elements_meta.guid`). The Python
extraction script generates them when the IFC file lacks proper GlobalId
on some elements.

### Evidence

```
Building  | IFC GUIDs (22-char) | Total | Source
----------|---------------------|-------|-------
TE        | 48,428 / 48,428     | 100%  | Revit 2022 federated IFC — all elements have GlobalId
SH        |     58 /      58    | 100%  | IfcOpenShell sample — proper IFC4
FK        |     82 /      82    | 100%  | KIT FZK-Haus — proper IFC2x3
IN        |    699 /     699    | 100%  | AC11 Institute — proper IFC2x3
MO        |    106 /   3,114    |  3.4% | Molio (Danish) — only 106 have GlobalId
JE        |     41 /     626    |  6.5% | Jesse — partial GlobalId
RA        |     96 /     442    | 21.7% | Revit ARC — partial GlobalId
RM        |    178 /   6,787    |  2.6% | Revit MEP — partial GlobalId
RS        |     90 /   4,133    |  2.2% | Revit STR — partial GlobalId
```

Verified: `sqlite3 DAGCompiler/lib/input/Molio_extracted.db
"SELECT COUNT(*) FROM elements_meta WHERE LENGTH(guid)=22"` → 106.

The extraction script (`RosettaStoneExtract.py`) generates `COMPACT_MD_*`
names as fallback when `IfcElement.GlobalId` is absent or non-standard in
the IFC source file. These identifiers go into `elements_meta.guid` at
extraction time — the Java pipeline passes them through unchanged.

### GEO coverage consequence

GEO's `IFC_GUID` regex (`^[0-9A-Za-z_$]{22}$`) rejects non-standard GUIDs.
For MO: only 8 elements passed GEO filtering (some 22-char names happen to
match). "Zero drift on 8/3114 elements" is statistically meaningless.

### Fix required

Either:
- A) Fix extraction script to preserve IFC GlobalId for all elements
- B) Relax GEO's IFC_GUID filter to accept any non-empty elementRef
  (match by string equality against extraction guid, not by format)

Option B is simpler and works for all buildings regardless of IFC source.

---

## 6. PATTERN Helping R2 — Storey Assignment Fix

### What PATTERN proves (empirical)

From `ClassificationYaml.java:66` (STOREY) and `DisciplineBomBuilder.java:163` (FLOOR):

```
[PATRN] STOREY  Container 'First Floor': minZ=-1.219m, 1112 elements
[PATRN] FLOOR   Container 'Unknown' (code=MISC): 726 elements, fMinZ=-1.000m
```

### Fleet-wide PATTERN data

| PFX | Unknown% | Unknown | Total | Root cause |
|-----|----------|---------|-------|-----------|
| MO  | 84%      | 2,630   | 3,114 | IFC elements lack rel_contained_in_space |
| JE  | 65%      | 404     | 626   | Same — partial spatial containment |
| RA  | 45%      | 200     | 442   | Revit export: partial containment |
| RM  | 31%      | 2,116   | 6,787 | MEP elements rarely assigned to IfcBuildingStorey |
| CA  | 28%      | 726     | 2,586 | Storey Z-bands overlap (FDN Z=-1.0m, L1 Z=-1.22m) |
| RS  | 100%     | 4,133   | 4,133 | No IfcBuildingStorey in IFC at all |

### R2 fix design

Current: `SpatialContainerConfig.discover()` (`ClassificationYaml.java:41-71`)
sorts containers by minZ and assigns elements by Z-band proximity.

Problem: When Z-bands overlap (CA) or elements lack `rel_contained_in_space`
(MO/JE), elements fall to "Unknown".

Fix in `discover()`:
1. Read `rel_contained_in_space` from extraction DB as PRIMARY source
2. Z-band assignment as FALLBACK only for elements not in any IfcSpace
3. PATTERN Unknown% becomes the quality gate: target < 10% for all buildings
   except RS (no storeys in IFC — genuinely has no spatial structure)

### Verification

After R2 fix, re-extract affected buildings and check:
```bash
grep "[PATRN] FLOOR.*Unknown" logs/pipeline_*_ifctobom_*.log
```

Target: Unknown% < 10% for MO, JE, RA, RM, CA.

---

## Fleet Summary — Qualified

### Proven by code + data

- **Tack chain works** (`PlacementCollectorVisitor.java:375-440`):
  BOM walk accumulates dx/dy/dz → `elements_rtree`. GEO verified for
  buildings with ≥90% GUID coverage (SH, FK, IN, DX, TE, CH, CP + 15 others).
- **8 disciplines compile** (`OrderLineProductCallout.java:43-134`):
  Callout reads ERP.db M_BOM recipes, inserts DISCIPLINE OrderLines,
  expands children. All 8 AD_Org values in TE output.db `c_orderline`.
- **Extraction complete:** 34/34 `*_BOM.db` produced. Verified by
  `m_bom_line` counts and QA validation in each ifctobom log.
- **PATTERN is honest** (`ClassificationYaml.java:66`, `DisciplineBomBuilder.java:163`):
  Logs every container assignment. Cannot be falsified.

### Qualified (not yet proven)

- **TE "zero drift"** is real for tack placement. But routing is 3% skeleton
  (258 edges / 8,056 MEP elements). TE passes GEO because tack chain is
  correct, NOT because routes are correct. Routes are untested.
- **5 buildings have < 10% GEO coverage** due to non-standard GUIDs in
  extraction DB (`elements_meta.guid`). "Zero drift" on these is meaningless.
  Root cause: Python extraction script, not Java pipeline.
- **P15/P16/P17 proofs not firing** — gated by `hasRelationalData` in
  `ProveStage`. TE has `system_edges=258` but proofs still gated.

### Next actions

1. **R2:** Fix `discover()` — `rel_contained_in_space` as primary source
2. **R1:** Data-driven P10 threshold from extraction stats
3. **R3:** RS auto-classify P06 as ADVISORY
4. **GEO GUID fix:** Relax IFC_GUID filter to string match (option B)
5. **Route audit:** Track edges/nodes vs MEP element count per building
