# RESUME — disc_walker: area-scaled n_measured + envelope-bound placement (RouteWalker alignment)

```
# ⚠ DO NOT REMOVE
SCOPE: Fix the array-placer density explosion (SampleCastle residential PLB = 708k placements) by
aligning it with RouteWalker's doctrine: GENERATION IS BOUNDED BY MEASURED QUANTITY + REAL ARC
SUBSTRATE, never by bbox area. NON-INVENT: count = measured n_measured × measured area-ratio; position
= measured pitch inside the building's own ARC occupancy envelope. Read the log after every run.
ROOT CAUSE (measured, build/duplex_rules.db): the placer IGNORES rule_placement.n_measured (the real
per-storey count, e.g. PLB IfcFlowController n_measured=8) and tiles the storey bbox at the local cluster
pitch (~0.208m) → (W/sx)·(D/sy) = area/pitch² → 178k/storey. pitch is a WITHIN-CLUSTER spacing, not a
floor cadence. RouteWalker's rwPlaceFixtures already does it right: count = measured BOM qty per room,
pitch only arranges locally. We have no rooms (IfcSpace=0 in ALL extracted.db), so the per-storey
analogue is n_measured scaled by floor-area ratio.
```

## THE MEASUREMENT DOCTRINE (user 2026-06-28 — the load-bearing constraint)
A fidelity claim is only as good as having a GROUND TRUTH to land on. Split the walked output by construction:
- **LANDED (reconstructive, real→real):** routed segment endpoints ARE real extracted elements — land them
  EXACTLY (1e-6, guid-matched). Already proven: `witness_disc_route_nnchain.js` R2 (5315 Terminal segs,
  posDrift=0). A human may trust these; the machine confirms them.
- **GENERATED (fills an ABSENT discipline):** no ground truth exists → position is PLAUSIBLE, never landed.
  NEVER print rmse/cover as a fidelity verdict (that invites the vicious-waste human audit). The ONE
  confirmable thing about a generated set is its COUNT → make that EXACT.
- ⚠ `§DXM-RT` cover/rmse is a statistics-vs-source self-consistency check (2–3× tolerances), NOT a landing.
  W-WALKBACK-MEP is ⛔ BLOCKED (no exact end-to-end MEP landing). Don't dress either as exact.
- ⚠ ERP.db sets routed along the walk = GENERATED layer unless a real ERP ground truth is found to land on.
  TODO: confirm when the ERP side is touched; do not assume exact.

## THE FIX (count-by-measured-quantity, envelope-placed)
1. **Re-bake — stamp SOURCE storey footprint area** (measured off the source building, non-invent) into each
   `*_rules.db` so areal density travels with the rule. Touch `bake_duplex_rules.py` + `bake_terminal_rules.py`:
   write `rule_placement.src_storey_area_m2` (or a `rules_meta` `src_storey_area_*` row per storey scope).
   One number per storey scope, NOT a re-mine.
2. **disc_walker `place()` array branch** (`disc_walker.js:140-145`): replace `nx=round(W/sx); ny=round(D/sy)`
   with **count = round(n_measured × (target_storey_area / src_storey_area))**, then ARRANGE that many
   fixtures at the measured pitch INSIDE the ARC occupancy envelope (occupied cells from the building's own
   ARC element footprints — substrate already loads center+bbox; add `occupancy(storey)`). Fixtures fill
   occupied cells at pitch; if count < occupied capacity, stride; if count > capacity, the envelope is the
   ceiling (log `§DW-CAP`). repRules must carry n_measured + src_storey_area through.
3. **Secondary backstop only:** hard ceiling MAX_PER_STOREY logged as `§DW-CAP placed=N of M`, never silent.

## WITNESS (`build/witness_disc_walk_density.js`)
- **D-COUNT (EXACT, 0 tol):** SC PLB/ELEC walked count == Σ round(n_measured × area_ratio) per class/storey.
  Assert 708k → bounded measured count (e.g. controllers ~8×area_ratio, not 178k).
- **D-ENVELOPE:** every placed fixture falls inside the ARC occupancy envelope (no void fixtures).
- **D-CADENCE:** surviving local spacing still == measured pitch (we thinned by area/quantity, not by
  changing cadence) — G2 analogue.
- **D-LANDED:** routed endpoints keep posDrift=0 (reuse the R2 check) — landed layer unbroken.
- **D-LABEL:** placed set is reported `generated/plausible`, NOT rmse/cover. (assert the witness prints no
  fidelity verdict for placed positions.)
- **REGRESSION:** `witness_disc_walk_generalize.js` (§DWG PASS=49, terminal standard — fully occupiable, count
  UNCHANGED) + `witness_disc_walk_duplex_generalize.js` (§DXG PASS=12) stay green.

## DEPLOY (engine + DBs TOGETHER — never source-only = drift)
Re-bake both DBs → re-run ALL witnesses green → port `disc_walker.js` + both `*_rules.db` to
`~/bim-ootb/modeller/` (worktree, NOT shared tree — hook-blocked) → bim-ootb PR → verify live
(content_sha bump, §DW-PROV, a real SC walk count in the §-log) → update `docs/ModellerGuide.md` Table 4
note if SC counts change materially.

## ACCEPTANCE
- D-COUNT exact-green; SC residential PLB count is a measured quantity (~hundreds), not 708k.
- Terminal-standard generalize UNCHANGED (regression). Endpoints still land 1e-6.
- Placed positions labeled `generated`, never rmse-as-fidelity. Deployed both DBs + engine, verified live.
