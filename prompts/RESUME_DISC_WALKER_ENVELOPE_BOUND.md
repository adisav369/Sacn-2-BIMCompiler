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
- ✅ **W-WALKBACK-MEP UNBLOCKED 2026-06-29** (`scripts/witness_walkback_mep.js`, 8/8) — the ⛔ was a SUBSTRATE gap, not
  an engine fault. `routeChains` on a REAL MEP-bearing extracted.db (Terminal/Duplex-MEP) emits the network (0→N:
  5317/358 segs); candidates+oracle share one frame by construction. Scored vs a NON-INVENT geometric touch oracle
  (point-to-3D-segment; the IFCs carry NO IfcRelConnectsPorts, so geometric touch IS the ground truth): precision
  (don't-fabricate, per-rule) PLB 0.896(TE)/0.969(DX). This is the LANDED class for routing — real→real. M7 opt-in
  route-to-FACE (`routeChains{toFace:true}`) partially lifts ACMV ducts (0.269→0.332).
- ✅ **ERP.db lineage CLARIFIED 2026-06-29** (see [[reference_erp_db_pattern_store]]): `library/ERP.db` (ex
  `disc_validation.db`) is the prior-art PATTERN store (joint-pieces/shims/mep-patterns/M_BOM/assembly-connectors), NOT
  the accounting ERP. The `*_rules.db` are its byte-identical projection (proven: `ad_routing_measured` == `rule_routing`).
  So routed/placed sets trace to ERP.db's MEASURED rows = the GENERATED layer's provenance, non-invent. Accounting
  (`build/erp/erp_rules.db`, GL) is a SEPARATE DB = out of scope (downstream/easy). Rename plan: de-"ERP" → `disc_patterns.db`,
  split by building-class NOT discipline, keep cross-disc tables (rule_avoidance/place_order) whole.
- ✅ **ELEC anti-float fix SPIKED 2026-06-29** (`scripts/witness_elec_hostbind.js`, W-ELEC-HOSTBIND 5/5) — the SH
  floating-outlets defect is REAL (density `ref_kind='storey'` scatters at footprint centres: 26/38 float, median 2.0m
  off wall) and host-bind fixes it: new opt-in `disc_walker.hostBind(placements,bdb,shim)` snaps to the nearest REAL
  wall via the ERP.db `_shim_attributes` percept (ELEC_WALL_SHIM|IfcWall|SIDE|1200mm) → 0/36 float, median 0.145m (on
  the face), 2 honest refusals, every point on a real wall guid. OPT-IN (live dwWalk byte-identical). **Promote to mining
  next** (see ROADMAP below).

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

---

## §PRIM — GENERATED-fixture representative primitives (W-DW-PRIM) — 2026-06-28
**Problem.** GENERATED disc-walk fixtures all render as a uniform 0.18³ marker cube in modeller
`_renderDiscWalk`. An absent discipline has no ground truth for fixture POSITION (correct — stays
plausible/density-placed). But the fixture's SIZE per ifc_class IS measurable from the source building the
rules were mined from. Replace the uniform cube with a per-class BOX sized to that class's MEASURED bbox.

**NON-INVENT boundary (load-bearing):**
- POSITION unchanged — still `placed:array-density`, no fidelity claim (the cube→box change moves/adds NO fixture).
- SIZE = MEASURED median bbox extents of REAL source elements of that ifc_class (Terminal_meta / Duplex_mep_meta).
  Never a hand-picked constant.
- SHAPE = a BOX of the measured extents — NOT a class-specific catalog mesh. We have NO landed geometry for an
  absent discipline; the box's DIMENSIONS carry the only real information (class footprint/height). A fabricated
  cylinder/grille mesh would be inventing geometry → forbidden.
- A class with no measured bbox → keep the 0.18 cube + honest `§DW-PRIM … no measured bbox` log.

**Data flow (mirrors `src_storey_area_m2` stamping):**
1. `build/stamp_src_bbox.py <rules.db> <meta.db>` → `ALTER TABLE rule_placement ADD COLUMN bbox_dx/dy/dz`,
   writes median bbox per ifc_class (idempotent). Run for terminal_rules+Terminal_meta and duplex_rules+Duplex_mep_meta.
2. `disc_walker.repRules` carries `bbox={dx,dy,dz}` (median over class rows); `place()` pushes `bx,by,bz` onto
   every placement object.
3. modeller `_renderDiscWalk` groups placements by ifc_class → one BoxGeometry(bx,by,bz) per class (fallback
   0.18³), preserving the normal/gated-orange/clash-red material split per class.

## WITNESS (`build/witness_disc_prim.js`, W-DW-PRIM)
- **P1 MEASURED:** every rule_placement class carries bbox_dx/dy/dz>0 == independently re-measured median bbox
  of that class's source elements (0 tol).
- **P2 NON-INVENT/falsifier:** a fake class ('IfcNope') → null bbox → engine fallback 0.18, no fabricated size.
- **P3 CARRIED:** disc_walker placements expose bx/by/bz == the stamped class bbox.
- **P4 REGRESSION:** placement COUNT + x/y/z identical to pre-PRIM walk (size-only change).
- **P5 RENDER:** replicate three.js box-instance transform → instance scaled to (bx,by,bz), centered at p.x/y/z.

## DEPLOY
Stamp both DBs → all disc-walker witnesses green → port `disc_walker.js`+both `*_rules.db`+modeller.html to
worktree off origin/main → sw bump → bim-ootb PR → live-verify (content_sha bump, §DW-PRIM box dims in §-log).

## ACCEPTANCE
- P1-P5 green; box dims == measured median, never a constant. Position/count UNCHANGED (P4).
- Absent-class size falls back honestly. Deployed engine+DBs+UI together; live §DW-PRIM shows real per-class dims.

## ⏸ RESUME STATUS (paused 2026-06-28, battery) — W-DW-PRIM, NOTHING IMPLEMENTED YET
Spec above is DONE. Investigation DONE — all facts below are verified, just implement top-to-bottom:
- **Engine source = canonical, deployed copies MATCH** (no drift): `build/disc_walker.js` == `origin/main:modeller/disc_walker.js`;
  `build/{terminal,duplex}_rules.db` content_sha == origin/main modeller copies (terminal b90fa163b29e, duplex 7551d63b7f57).
  ⚠ LOCAL `~/bim-ootb` is STALE (pre-#557, no duplex_rules.db) — DO NOT diff/deploy against it; use a `/tmp/wt-*` worktree off `origin/main`.
- **Meta DBs (source of measured bbox), have elements_meta(ifc_class)⋈element_transforms(bbox_x/y/z):**
  terminal → `~/bim-ootb/modeller/Terminal_meta.db` (NOT deploy/buildings/Terminal_meta.db = EMPTY; NOT library/archive = no bbox cols);
  duplex → `build/Duplex_mep_meta.db`.
- **Verified median bbox per rule class (what stamp must reproduce, 0-tol):**
  Terminal: IfcAirTerminal(.600,.600,.102) IfcAlarm(.155,.102,.118) IfcBeam(1.855,.500,.750) IfcColumn(.600,.750,8.000)
  IfcDuctFitting(.300,.300,.250) IfcDuctSegment(.579,.509,.337) IfcElectricAppliance(.087,.048,.087)
  IfcFireSuppressionTerminal(.032,.027,.058) IfcLightFixture(.644,.612,.096) IfcMember(.150,.100,2.440)
  IfcPipeSegment(.057,.033,.069) IfcPlate(.500,.150,.107) IfcValve(.080,.116,.122).
  Duplex: IfcFlowSegment(.033,.033,.033) IfcFlowFitting(.035,.035,.035) IfcFlowTerminal(.070,.070,.114) IfcFlowController(.132,.140,.125).
- **rule_placement schema** (terminal): disc,ifc_class,ref_kind,dx,dy,dz,spacing_x_m,spacing_y_m,z_band_lo,z_band_hi,
  storey_scope,n_measured,provenance,src_guids,src_storey_area_m2. ADD bbox_dx/dy/dz (guarded, like src_storey_area_m2).
- **Render target:** modeller.html `_renderDiscWalk` (origin/main lines ~1866-1895) — currently one global
  `BoxGeometry(0.18,0.18,0.18)` InstancedMesh (matN) + `_overlay(gated,matG)` orange + `_overlay(clashed,matC)` red.
  Restructure: group `placements` by ifc_class → per-class BoxGeometry(bx,by,bz) (fallback .18), keep the 3-material split.
  Tube render `_renderDiscChains` (LANDED) is UNCHANGED — this is the GENERATED/cube half only.
- **disc_walker carry-through:** `repRules` (build/disc_walker.js:96-124) add `bbox:{dx:_med(...bbox_dx),...}`;
  `place()` (171-220) push `bx:rp.bbox.dx,by:...,bz:...` onto EACH of the 4 push sites (array-density/array/shim/single).
- **Mirror script:** `build/stamp_terminal_src_area.py` (col-guard + idempotent + NON-INVENT docblock pattern). Write `build/stamp_src_bbox.py <rules.db> <meta.db>`.
- **TODO order:** (1) stamp_src_bbox.py + run on both DBs (2) disc_walker repRules+place carry bbox
  (3) witness_disc_prim.js W-DW-PRIM P1-P5 (4) rerun full disc-walker suite green (density43/nnchain6/erp-equiv14/erp-landed4/roof-bound10 + §DWG49/§DXG12)
  (5) worktree deploy engine+2 DBs+modeller.html, sw bump v7→v8, live §DW-PRIM verify (6) PROGRESS + MEMORY update.
- bim-compiler unpushed at pause: 0 commits (only this gitignored prompt card edited). Nothing to push.

## ⚠ OFFLINE TODO — add IDB caching to disc_walker `dwInit` (2026-06-28, separate from above)
**Viewer-side fixed (PR #561 sw v738):** 8 bare `fetch('../erp/ad_seed.db')` calls in time_machine/whatif/wh_walk/diff/schedule_author/navigate_find → routed through `APP.cachedFetch`.
**Modeller still needs:** `disc_walker.dwInit` line 59 (`var buf = await (await fetch(url)).arrayBuffer()`) hits the network EVERY open for `terminal_rules.db` and `duplex_rules.db`. Fix = wrap it with an IDB read-write using the shared `bim_ootb_cache / dbs` store (same DB that `modeller/kernel_ops.js` opens via `indexedDB.open('bim_ootb_cache')`). Pattern:
```
// try IDB hit → on miss: fetch + put to IDB → fallback bare fetch on IDB error
```
Key = the full `url` string (e.g. `'../modeller/terminal_rules.db'`). Log: `§DW_IDB_HIT` / `§DW_IDB_WRITE`. Do this fix ALONGSIDE the bbox/stamp work above (one PR). The modeller sw.js comment at line 8 already says "terminal_rules.db cached in IndexedDB" — make it true.

## 🗺️ ROADMAP — walker → assembly (from 2026-06-29; resume here next session)
The walk is **PATTERN × SUBSTRATE → network**, and the full chain is **ROUTE → INSTANTIATE → JOIN → SHIM**, every
step sourced from `library/ERP.db` (the prior-art pattern store — see [[reference_erp_db_pattern_store]]), NOT the leaf
`component_library.db` and NOT the accounting `erp_rules.db`. Where we are:

```
 ERP.db (ex disc_validation.db)
  ├─ ad_routing/placement_measured ─► *_rules.db projection ─► routeChains / place   ✅ ROUTE done (W-WALKBACK-MEP 8/8)
  ├─ ad_mep_pattern (METER→JUNCTION→FIXTURE abstract recipe)                          ◻ not consumed
  ├─ _import_joint_piece_types (parts + Ø + how-they-join, 7083)                      ◻ INSTANTIATE — not wired
  ├─ ad_assembly_connector (face / Ø / connects_to, 29)                              ◻ JOIN — not wired
  └─ _shim_attributes (anchor shims, 11)                                            ◧ SHIM — hostBind spike uses it (ELEC)
```

**DONE this session (engine proven, all opt-in / regression-clean):**
- ROUTE: `routeChains(disc,bdb[,{toFace}])` emits the real network on a MEP-bearing substrate; non-invent; landed/real→real.
- hostBind: anti-float for wall-mounted classes via the `_shim_attributes` percept (ELEC SH 26/38→0 float).

**NEXT — in priority order (each a bounded, witnessed slice):**
1. **Promote host-bind into the MINING pipeline** (close the SH ELEC defect for real, not a post-step). Re-bake so
   residential ELEC outlets/switches + FP alarms carry `ref_kind='host'` sourced from ERP.db `_shim_attributes`; SPLIT
   ELEC wall (ELEC_WALL_SHIM) vs ceiling (ELEC_CEILING_SHIM → lights on IfcCovering BOTTOM). Touch `bake_duplex_rules.py`
   /`bake_terminal_rules.py`; witness = the floating count must drop in the GENERATED `place()` output itself, no post-call.
2. **Rename ERP.db → `disc_patterns.db`** (de-"ERP", isolate accounting). Carve the geometry-pattern tables out of
   `library/ERP.db` into a clearly-named store; leave the accounting tables behind. Keep `*_rules.db` as the projection;
   split files by BUILDING-CLASS, discipline stays a column, cross-disc tables (rule_avoidance/place_order) stay whole.
   Witness: the projection re-bakes byte-identical from the renamed store (no number drift).
3. **Route→ASSEMBLE bridge** (turn routed boxes into real parts). At each routed node, instantiate the catalog part
   (`_import_joint_piece_types`/component_geometries), orient so its `ad_assembly_connector` face (WASTE_OUT Ø100→
   PLUMBING_STACK) meets the run, stand off by `ad_assembly_manifest`/`_shim_attributes` clearance. Witness on Duplex-MEP
   (real parts exist as oracle): assembled part poses land on the extracted fittings/terminals, non-invent.
4. **Wire routeChains into the modeller §8E-3 render** (bim-ootb) — overlay the nn-segments as edges into the laid ARC
   (mirror `_seedStrWalk`/`swbCanopyOps`) + a `__dwPixelProbe` readPixels assertion. Engine proven; render + deploy only.
5. **Deeper route-to-FACE / generalization** (lower pri): a face-AND-direction model for ACMV ducts; route duplex_rules
   onto a DIFFERENT residential building (true cross-building generalization, vs today's mined-then-applied self-consistency).

Scope guard: accounting (C_Order, GL) is downstream/easy = out of scope; geometry/assembly is the hard part.

## 🏰 SC (SampleCastle/Schependomlaan) = the next walker TARGET (user direction 2026-06-29)
**Why ideal:** clean ARC(3342)+STR(206) over 7 real storeys (fundering→dak) = "ARC/STR perfect to crawl"; MEP is
RUDIMENTARY (60 IfcFlowSegment, NO fittings/terminals) so MEP/ELEC are genuinely WALKED IN, not pre-built. Covered by
duplex_rules (building_class=Duplex,SampleHouse,SampleCastle; standard=residential) → **uses DX rules, no new file.**
Crawl probe (DX rules): places across all 7 storeys — ELEC 326 / PLB 101 / ACMV 14.

**The "vent_router" = SC's RICH ventilation, UNDER-EXTRACTED.** Source IFC has 402 `ventilatierooster` (vent grilles),
186 `VentilationProfileType`, 3752 `DUCT` refs — but extraction kept only 60 IfcFlowSegment, NO fittings/terminals. So
`routeChains` PLB/ACMV on SC = `no-endpoints` (can't route without fittings). This is the dependency.

**DECISION — SC_disc vs DX: LUMP INTO DX, do NOT make an SC_disc file.** Rationale = the building-class axis (SC is
residential = DX class; discipline stays a column). The SC vent cadence is mined as **ACMV rows into duplex_rules** with
provenance `measured:samplecastle/vent` + src_guids (traceable), thickening DX's currently-thin ACMV (2 placement rules,
0 routing). A per-building SC_disc file would fragment the wrong axis.

**PREREQUISITE (do FIRST): fix SC vent EXTRACTION** — recover `ventilatierooster`→IfcAirTerminal/FlowTerminal + the duct
network→segments/fittings into SampleCastle_extracted.db (the data is in the IFC, dropped at extract — a Path-B-style
recovery). THEN: (a) SC becomes a real ACMV walk-back ORACLE (as Duplex-MEP was for PLB — W-WALKBACK-MEP), (b) mine its
cadence into DX ACMV. ELEC on SC will float like SH → apply the host-bind fix (roadmap #1).
**Outliner DISC-tab story (the goal):** open SC (clean ARC/STR) → pick ACMV/ELEC/PLB from the DISC tab → DX rules
(context + spacing + clearance) drive the walk to fill fine elements into the laid space.
