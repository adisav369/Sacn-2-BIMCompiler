# S148 — DX MEP Begin + Remaining Furniture Cleanup

**Prior work:** S147 (stair mirror fix, ElementIdentity contract, LINE verb, LOD rotation, logging hardening)
**Analysis:** `docs/DuplexAnalysis.md` §S145, `docs/BOMBasedCompilation.md` §4.3

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## S147 Learnings — Must Read Before Any DX Work

S147 found and fixed three layers of issues. The fixes established new
infrastructure that this session must use:

### 1. Black box logging is now trustworthy
- `SPATIAL-REPORT` (grep pipeline log): modal shift, outlier sym/asym diagnosis,
  missing breakdown by discipline. Always check this first.
- `BOM-SUMMARY` (grep IFCtoBOM log): BOM tree structure, children/instances per BOM.
- `LOD-ROTATE` (grep pipeline log): confirms B-side LOD meshes get rot=π.
- `rosetta_trace.sh <log> <output.db> [ref.db]` — cross-box correlation.

### 2. ElementIdentity contract (BBC.md §4.3)
- Every element carries `baseGuid` (22-char IFC) + `unitPrefix` (A_/B_/"").
- Output DB `element_ref` = base GUID (matches reference). `guid` = synthetic.
- SpatialDiff uses identity-based matching (not position-based fallback).
- W-GUID-1/2/3 witness claims PENDING — verify these hold after changes.

### 3. DX-specific findings
- **Fridge:** `IfcFlowTerminal` — 2 M_Refrigerator in IFC, correctly excluded
  from ARC BOM by discipline separation (§6.12.1). They belong in DISC path.
  SPATIAL-REPORT confirms: `missing_summary: disc_excluded=904 not_in_bom=0`.
- **Stair LOD rotation:** Fixed. B-side gets rot=π via `MeshBinder.bind()`.
  Confirmed by `LOD-ROTATE IfcStairFlight guid=MD_UNKNOWN_101_B rotZ=3.1416rad`.
- **Kitchen cabinets:** LINE/LINE_MULTI verb expansion implemented. 15 remaining
  outliers are GUID-to-position order mismatch within LINE_MULTI expansion
  (visual correct, identity swapped). Low priority — cosmetic.
- **B-side pantry (B104):** Zero furniture in IFC source. Not a compiler issue.

## Task 1 — Remaining Furniture GUID Order — DONE (S147)

15 kitchen base cabinet outliers fixed. GUID order now matches verb position order.

## Task 2 — Begin MEP Process (DX)

MEP is IFCtoERP (DISC path), not IFCtoBOM. Per S147 SPATIAL-REPORT:
`IfcFlowTerminal count=105 reason=DISC_EXCLUDED` — 105 terminals including
2 refrigerators, plus 358 fittings, 427 segments, 14 controllers.

DX MEP context:
- IFCtoERP writes directly to ERP.db, no intermediate DB
- YAML carries AD_Org (DISC) = MEP to set discipline during compile
- Half-unit concept does NOT apply to MEP — each side's routing walked
  independently from the IFC source (no mirror, no rotation)
- DX is IFC2x3 — element_name carries type info, no IfcPropertySet
- The SH/RM/TE MEP path is already proven (S104 series). DX should follow
  the same RouteWalker pattern but verify IFC2x3 class mapping

Steps:
1. Run `IFCtoERP` on DX extracted DB — check routing topology
2. Verify DX MEP classes map to the same discipline tags as RM/TE
3. Check SPATIAL-REPORT after: `disc_excluded` count should drop as
   MEP elements move from "missing" to compiled DISC output
4. Verify fridge appears in DISC output

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §4.3 (ElementIdentity contract)
3. `docs/BOMBasedCompilation.md` §6.12.1-§6.12.3 (discipline separation + MEP)
4. Pipeline log: `grep 'SPATIAL-REPORT\|BOM-SUMMARY\|LOD-ROTATE' logs/pipeline_DX*`
5. Run `./scripts/run_RosettaStones.sh classify_dx.yaml`

## Gate

- DX: 8/8 PASS (must not regress)
- SH: 8/8 PASS (no regression)
- LOD-ROTATE: 51 B-side elements must show rotZ=3.1416rad
