# S149b — MEP Route Geometry Fixes (from Sandbox Findings)

**Prior work:** S149 (C_BPartner model, system BOM children, DV039 rule tables, MepRouteGeometryTest sandbox)
**Findings source:** MepRouteGeometryTest GEO forensic output — not assumptions

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Triage Style

**Narrow focus.** Work one finding at a time. Do not jump ahead.
Each finding: read the spec, run the sandbox, read the GEO log, fix, re-run.
No analysis-driven fixes — only GEO-proven fixes.
After each fix, await review before proceeding to the next.

## S149 GEO Forensic Findings

Four findings from MepRouteGeometryTest, all exposed by running the Walker
against real/synthetic mini BOMs and reading GeoProofRecord output.

### Finding 1: c_uom_id=MM qty guard (BLOCKING)

**GEO evidence:** S5 test — `FLOW_SEGMENT_100MM` with `c_uom_id=MM, qty=2345`
produced 2346 GeoProofRecords (2345 instances + shim). InterimWorkshop IS wired
at PlacementCollectorVisitor:443 (`line.isLengthBased()`) and recomputes
half-extents. But `expandVerb()` at line 468 still reads `qty=2345` and expands
2345 instances.

**Fix:** When `isLengthBased() && qty_type=VARIABLE`, set qty=1 before
`expandVerb()`. The workshop recomputes dimensions; the piece is one instance
at the specified length. Guard: `qty_type=FIXED` pieces keep their qty (a tee
fitting with qty=2 means 2 fittings, not a 2mm fitting).

**Spec:** §6.12.2 §6 — "c_uom_id is the signal. qty_type=VARIABLE allows
runtime adjustment."

### Finding 2: LMP MEP exemption (MEDIUM)

**GEO evidence:** S1 test — `FLOW_SEGMENT_35MM_577911` at LBD=(-0.849, 0, 0).
LMP check (outputLBD >= parentAnchor) reports FAIL because X is negative.
MEP pipes route both directions from shim — negative offsets are valid.

**Fix:** Exempt MEP pieces from LMP containment check. Detection: the BOM
has `bom_type=MEP_RECIPE`, or the product's M_Product_Category maps to a
discipline (AD_Org_ID > 0). The exemption is on LMP only — envelope check
still applies.

**Spec:** §6.12.2 §1 — "A tack point is just a point... What the point
physically represents depends on the product."

### Finding 3: Re-extract DX recipes with cumulative offsets (DONE in S149)

**GEO evidence:** S1 synthetic test — seq=40 at X=1.0 (not X=2.0). Sibling
offsets are parent-relative by §3.2 design. buildMepBomRecipes was writing
piece-to-piece deltas (curr.cx - prev.cx).

**Fix applied in S149:** Changed extraction to `curr.cx - first.cx` (cumulative
from first piece = shim position). Rotation calc preserved using piece-to-piece
delta (`curr.cx - prev.cx` for direction change detection only).

**Verify:** Re-run `./scripts/run_RosettaStones.sh classify_dx.yaml` — recipes
will be re-extracted with cumulative offsets. Then run MepRouteGeometryTest S1
with real mini BOM and verify offsets are cumulative.

### Finding 4: §8d route direction → piece orientation (SPEC GAP)

**GEO evidence:** S2 test — `rotation_rule=PI/2` on a flat sibling had no
effect on output position. The Walker only applies rotation in sub-assembly
context (onSubAssembly push/pop). Flat siblings are §5's "flat sibling pattern".

**Not a code bug.** This is by design: §8b says tee branching uses nested
sub-BOMs, not rotation on flat siblings. Direction changes happen at fittings
(tees, elbows) which ARE sub-assemblies. The rotation_rule on a flat sibling
is for the piece's own facing, not for the tack chain.

**However:** Three spec gaps remain (written into DISC_VALIDATION_DB_SRS.md §8d):
1. RouteWalker must set rotation_rule on generated lines
2. Walk direction per discipline needs runtime storage
3. Piece forward_axis alignment with route direction

These are not blocking for DX (extracted path has all data). Blocking for
generated path (§6.12.3 RouteWalker) only.

## Task List

Work one at a time. Run sandbox after each. Await review.

1. **Fix c_uom_id=MM qty guard** — PlacementCollectorVisitor, guard qty before expandVerb
2. **LMP MEP exemption** — PlacementCollectorVisitor, skip LMP for MEP pieces
3. **Re-extract DX recipes** — run pipeline, verify cumulative offsets in ERP.db
4. **Black-box test** — walk real mini BOM, compare output positions against IFC reference positions from Duplex_extracted.db

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.2 §5-§6 (flat sibling, InterimWorkshop)
3. `docs/DISC_VALIDATION_DB_SRS.md` §8d (route direction triage)
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` lines 437-468
5. `DAGCompiler/src/test/java/com/bim/compiler/contract/MepRouteGeometryTest.java` (sandbox)
6. Run `MepRouteGeometryTest` — read GEO output before changing any code

## Gate

- MepRouteGeometryTest: 5/5 PASS
- DX: 7/9 PASS (no regression)
- SH: 7/9 PASS (no regression)
- `c_uom_id=MM` with `qty=2345` produces 1 GeoProofRecord (not 2345)
- LMP: 0 FAIL on MEP pieces (exempted)
- Real mini BOM output positions match IFC reference within 0.005mm
