# HO_001 — Hospital Pipeline Onboarding

**Spec:** `docs/HospitalAnalysis.md`, `docs/DISC_VALIDATE_SRS.md §Phase 5`, `docs/BOMBasedCompilation.md`
**Prereq:** S136 DONE — `Hospital_extracted.db` (62,291 elements, 7 disciplines, LFS)
**Extraction:** COMPLETE. Do NOT re-extract. Use `--skip-extract` throughout.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

Extract or compile only. The Hospital IFC structure is the authority.
Pre-flight citation required before every code change.

## Read First

1. `PROGRESS.md` §Current State
2. `docs/HospitalAnalysis.md` — full building profile, discipline breakdown, known gaps
3. `docs/DISC_VALIDATE_SRS.md §Phase 5` — HO_ BOM tree structure + HO_ vs TE_ vocabulary
4. `scripts/onboard_ifc.sh` — pipeline steps 3–8 (skip step 2)
5. `IFCtoBOM/src/main/resources/classify_sh.yaml` — reference for YAML format

## Context

Hospital_extracted.db is already built (S136):
- 62,291 elements across 7 discipline IFCs (ARC/STR/MECH/PLB/ELE/SPR/FIRE)
- GUIDs prefixed by discipline (ARC_xxx, MECH_xxx, SPR_xxx, ...)
- 20 unique storey names (Level 1–Level 7A + Ceiling/TOS variants)
- 0 IfcSpace — no room containment (DECLARE ROOMS is HO_002, not this session)
- 9,527 assembly relationships, 506 door/window fills

Known pipeline challenges to DOCUMENT (not fix) in this session:
- "Unknown" storey for 9,427 MEP elements — Z-band fallback expected
- No IfcSpace → room-level BOM impossible → floor-level only (expected)
- FIRE discipline is 6 elements (proxy) — will likely be ZERO compiled output

## Task 1 — classify_hosp.yaml

Run the onboarding scaffold:

```bash
./scripts/onboard_ifc.sh \
  --prefix HOSP \
  --type Hospital \
  --name "Hospital — Large Healthcare Complex" \
  --base CO \
  --ifc DAGCompiler/lib/input/IFC/UNMERGED/Hospital_IFC4_ARC.ifc \
  --skip-extract
```

This generates `IFCtoBOM/src/main/resources/classify_hosp.yaml` and `dsl_hosp.bim`.

Then **edit `classify_hosp.yaml`** to add the discipline block per
`docs/DISC_VALIDATE_SRS.md §5.1`:

```yaml
disciplines:
  MECH:
    ifc_classes: [IfcFlowSegment, IfcFlowFitting, IfcValve, IfcBuildingElementProxy]
    ad_org_id: MECH
    guid_prefix: MECH
  SPR:
    ifc_classes: [IfcFlowSegment, IfcFlowFitting, IfcFlowTerminal, IfcValve]
    ad_org_id: SPR
    guid_prefix: SPR
  PLB:
    ifc_classes: [IfcFlowSegment, IfcFlowFitting, IfcBuildingElementProxy]
    ad_org_id: PLB
    guid_prefix: PLB
  ELE:
    ifc_classes: [IfcFlowTerminal, IfcBuildingElementProxy, IfcFlowController]
    ad_org_id: ELE
    guid_prefix: ELE
```

The `guid_prefix` field tells the discipline builder which GUID-prefixed elements belong
to each discipline (since Hospital used `--guid-prefix DISC` during extraction).

## Task 2 — Run Pipeline

```bash
./scripts/run_RosettaStones.sh classify_hosp.yaml
```

Capture full output. The pipeline will attempt G1–G6. Expected outcomes:

| Gate | Expected | Notes |
|------|----------|-------|
| G1-COUNT | Partial PASS | ARC/STR elements counted; MEP in DISC BOM |
| G2-VOLUME | PASS or WARN | Hospital is large — verify AABB bounds match |
| G3-DIGEST | Likely FAIL | Hospital not in GATE_SCOPE seal yet |
| G4-TAMPER | PASS | |
| G5-PROVENANCE | May WARN | New IFC classes not in component_library.db |
| G6-ISOLATION | PASS | |

Document every FAIL with the actual error message. Do not fix G3 in this session —
the seal update is a separate gate operation.

## Task 3 — Report Findings Without Fixing

After pipeline run, answer these in the DONE appendix:

1. G1 element count — what compiled vs what was extracted
2. G5 new IFC classes not in library (expected — hospital has clinical equipment proxies)
3. MECH/SPR/PLB/ELE counts in the discipline BOM vs extracted counts
4. Any new IFC classes that need seeding in `ad_ifc_class_map`
5. "Unknown" storey elements — did Z-band fallback assign them?
6. FIRE discipline — did it produce any compiled output?

## Gate (this session)

- `classify_hosp.yaml` committed and pipeline runs without crash
- G1: at least ARC/STR element count > 0
- G4: PASS (tamper check)
- SH 8/8 PASS (regression — Hospital must not break existing gates)
- DONE appendix complete with findings

## What NOT to Do

- Do NOT re-extract (DB is done)
- Do NOT fix G3 (seal update is a separate operation)
- Do NOT add missing library products (document, don't seed)
- Do NOT implement DECLARE ROOMS (that is HO_002)
- Do NOT modify existing migration files

## Commit

```bash
git add IFCtoBOM/src/main/resources/classify_hosp.yaml \
        IFCtoBOM/src/main/resources/dsl_hosp.bim \
        PROGRESS.md
git commit -m "[HO_001] Hospital pipeline onboard: classify_hosp.yaml + gate diagnostic"
```

## Sequence Map

```
HO_001 (this)  — Pipeline gate: classify_hosp.yaml, G1/G4, findings report
HO_002         — DECLARE ROOMS: DeclareRoomsService infers IfcSpace from wall topology
HO_003         — FINISH_WALLS: after rooms declared, wall completion per HTM standard
HO_004         — HO_ product categories: seed ad_ifc_class_map with HO_MECH/HO_SPR/HO_PLB
HO_005         — Route/Walker: W-HOSP-FP-1..5 + W-HOSP-MECH-1 + W-HOSP-PLB-1 witnesses
```

## When Done

Prepend `# DONE — [commit_hash]` to this file's first line.

Append findings below `---` including:
- G1 count (compiled vs extracted)
- G5 new classes list
- Discipline BOM counts (MECH/SPR/PLB/ELE)
- Any crash or schema error messages verbatim
- What the next session (HO_002) needs to know

---
