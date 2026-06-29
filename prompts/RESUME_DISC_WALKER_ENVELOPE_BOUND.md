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

> ▶ **RESUME HERE (2026-06-30)** — ✅ SELECTION KEY DONE (W-SHIM-SELECT 6/6, see §SHIM-SELECT below). `rule_shim`
> now carries `fixture_ifc_class` MEASURED from the source building (5 terminal + 3 duplex per-fixture rows; every
> host is the nearest-NN, ambiguous→REFUSE→disc-level fallback). dwWalk groups floating placements by ifc_class and
> binds each with its own shim → ELEC ceiling-lights snap to IfcCovering instead of being mis-bound to walls (Terminal:
> 1872 lights→ceiling, 0→wall). All regressions green: §DWG 49 · §DXG 12 · W-DWWALK-HOSTBIND 6 · W-HOSTBIND-AGNOSTIC 6
> · W-ELEC-HOSTBIND 5 · W-WALKBACK-MEP 8 · disc_walk_shim 6. ⚠ IMPLEMENTATION NOTE: applied the projection STANDALONE
> (`python3 build/project_rule_shim.py <db> <class> <src>`) onto the COMMITTED `*_rules.db` — a FULL re-bake drifts
> `rule_avoidance` 10(global-p05)→47(per-storey) because the committed DBs were baked by a newer process than the
> current `bake_*.py`. Touch only rule_shim until that bake drift is reconciled.
> **NEXT BITE (in order):** (a) DEFAULT-ON host-bind is now safe — flip `dwWalk` to host-bind by default (the key
> removes the mis-bind risk); (b) roadmap #1 full ERP→`disc_patterns` physical rename; (c) port `disc_walker.js` +
> both `*_rules.db` → `~/bim-ootb/modeller/` + live SC grille-walk deploy. Full status: §NEXT below + the SHIM diagram.

## 🚫 §NAMING DIRECTIVE — DE-ERP (BINDING; user-confirmed 2026-06-29/30 — READ FIRST)
**The prior-art pattern store is `disc_patterns.db`. "ERP.db" is a MISLEADING LEGACY NAME — do NOT use it for pattern
work going forward.** `library/ERP.db` is the renamed `disc_validation.db` holding GEOMETRY percepts; the "ERP" label
wrongly connotes accounting. Where older blocks below still say "ERP.db", read it as **"disc_patterns.db (currently still
physically `library/ERP.db` until the rename slice lands)"** — and do NOT introduce NEW code/specs that name or read
"ERP.db" for patterns; write `disc_patterns.db`.
- **IN `disc_patterns.db` (geometry percepts):** `_shim_attributes` (anchor/mount/offset), `_import_joint_piece_types`
  (parts+Ø+joins), `ad_assembly_connector`/`ad_assembly_manifest` (join+clearance), `ad_mep_pattern`,
  `ad_routing_measured`/`ad_placement_measured`, `M_BOM`/`M_BOM_Line`.
- **OUT (NOT the pattern store, out of scope):** accounting — `C_Order`/`C_OrderLine`, `AD_*` business, GL. The GL store
  `build/erp/erp_rules.db` is a DIFFERENT file and stays separate. Geometry is the hard part; accounting is downstream.
- **PROJECTION contract (unchanged):** the walker reads the lean per-BUILDING-CLASS `build/*_rules.db`
  (`terminal_rules`/`duplex_rules`) = byte-identical projections of `disc_patterns.db`. **Discipline = a `WHERE` column,
  never a file.** Cross-disc tables (`rule_avoidance` = disc-PAIRS, `rule_place_order`) stay WHOLE — never split by disc.
- **SHIM — half wired, the PROJECTION half remains (answers "does shim flow like routing?"):** `dwWalk` ALREADY applies
  host-bind on the live walk via a **caller-passed** percept (`dwWalk(disc, bdb, name, {shims})` → `place→hostBind`,
  W-DWWALK-HOSTBIND 5/5, commit e79ce00c; SH ELEC float 26/38→2-honest-refusals; byte-identical without `{shims}`). What
  is NOT yet done = making it a **first-class PROJECTED rule** so the caller need not pass percepts: add a `rule_shim`
  table to each `*_rules.db`, projected from `disc_patterns.db._shim_attributes`, and have `dwWalk` read it directly.
  **⚠ rule_shim selection KEY = `disc + ifc_class`, NOT a product_value prefix** — a discipline has MULTIPLE shims (ELEC
  wall-outlet + ceiling-light; ACMV ceiling-diffuser + window-grille), so the projection must map each (disc, ifc_class)
  to its host/mount/offset. The interim caller-passed path keys on the percept name; the projected path must not.
- **SEQUENCING:** do the RENAME + `rule_shim` projection BEFORE accreting more "ERP.db" references — every new
  shim/join/assembly read must source from `disc_patterns.db` (or its `*_rules.db` projection), not the legacy name.

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
step sourced from **`disc_patterns.db`** (the prior-art pattern store, currently still physically `library/ERP.db` — see
§NAMING DIRECTIVE + [[reference_erp_db_pattern_store]]), NOT the leaf `component_library.db` and NOT the accounting
`erp_rules.db`. Where we are:

```
 disc_patterns.db   (currently library/ERP.db, ex disc_validation.db)
  ├─ ad_routing/placement_measured ─► *_rules.db projection ─► routeChains / place   ✅ ROUTE done (W-WALKBACK-MEP 8/8)
  ├─ ad_mep_pattern (METER→JUNCTION→FIXTURE abstract recipe)                          ◻ not consumed
  ├─ _import_joint_piece_types (parts + Ø + how-they-join, 7083)                      ◻ INSTANTIATE — not wired
  ├─ ad_assembly_connector (face / Ø / connects_to, 29)                              ◻ JOIN — not wired
  └─ _shim_attributes (anchor shims, 12 incl VENT_WINDOW)            ✅ SHIM PROJECTED — host-agnostic hostBind + rule_shim
                                                                       projected into *_rules.db + dwWalk READS it (no caller)
                                                                       (W-HOSTBIND-AGNOSTIC 6/6 + W-DWWALK-HOSTBIND 6/6 incl W5);
                                                                       remaining = disc+ifc_class SELECTION KEY (then safe default-on)
```

**DONE so far (engine proven, all opt-in / regression-clean):**
- ROUTE: `routeChains(disc,bdb[,{toFace}])` emits the real network on a MEP-bearing substrate; non-invent; landed/real→real.
- hostBind (host-AGNOSTIC, W-HOSTBIND-AGNOSTIC 6/6): wall/window-top/ceiling via the `_shim_attributes` percept (ELEC SH
  26/38→0; 7 SC grilles reproduced at window-top). Percept-driven, caller passes `shim`.
- dwWalk APPLIES host-bind on the live walk (W-DWWALK-HOSTBIND 6/6, `dwWalk(disc,bdb,name,{hostBind:true})`): placements
  = (already-hosted) + bound ∪ refused, count preserved, byte-identical without the flag. Only FLOATING (`placed:*`)
  placements are rescued; already-hosted (`shim:host-*`) ones are left untouched.
- ✅ **SHIM PROJECTED (the §SHIM first-class flow, 2026-06-30)**: `build/project_rule_shim.py` projects
  `disc_patterns._shim_attributes` → a `rule_shim` table in each `*_rules.db` (disc, fixture_ifc_class[null], host_ifc_class,
  mount, offset_m, height_m, same_storey, **priority**, building_class, provenance); idempotent, isolated (the 5 mined
  tables untouched — no drift), wired into both bake scripts. `dwWalk` reads it directly (W5 PROJECTION-SOURCE: `{hostBind:true}`
  with NO caller shims binds 36==caller-path on SH ELEC). `disc_patterns.db` name landed via SYMLINK→ERP.db (minimal; full
  physical carve-out = #1 still pending). OPT-IN (default OFF, regressions invariant) — see selection-key below.

**NEXT — in priority order (do the naming/projection FIRST so nothing new accretes onto "ERP.db"):**
1. **RENAME → `disc_patterns.db`** (de-"ERP", per §NAMING DIRECTIVE). ◧ PARTIAL: the NAME landed via a SYMLINK
   (`library/disc_patterns.db`→`ERP.db`) so new code (project_rule_shim, witnesses) reads it; the FULL physical carve-out
   (split geometry-pattern tables out of `library/ERP.db`, leave accounting behind, migrate the ~20 legacy `ERP.db` readers)
   is still TODO. Witness target: every `*_rules.db` re-bakes byte-identical from the renamed store (no number drift).
2. ✅ **SHIM is now a first-class PROJECTED rule** (`build/project_rule_shim.py` → `rule_shim` in each `*_rules.db`; `dwWalk`
   reads it; W-DWWALK-HOSTBIND 6/6 incl W5). **REMAINING = the SELECTION KEY `disc + ifc_class`** — `rule_shim.fixture_ifc_class`
   is NULL today, so a disc with >1 host (ELEC wall-outlet + ceiling-light; ACMV ceiling-diffuser + window-grille) picks by
   `priority` only → would mis-bind ceiling LightFixtures to walls. THIS is why host-bind is OPT-IN not default-on. Next bite:
   stamp `fixture_ifc_class` per shim (mine which fixture class mounts on which host) → `_shimForDisc` matches on it → safe
   default-on. SPLIT ELEC wall(outlet) vs ceiling(light); verify GENERATED residential ELEC/FP float drops with NO caller percept.
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

**⚠ SOURCE-AUDIT CORRECTION (2026-06-29) — the vent-extraction premise above is REFUTED by `internal/sources/Ifc2x3_SampleCastle.ifc`. Every count was a naive-substring artifact; do NOT do "vent extraction" — there is nothing dropped to recover, and synthesizing a duct network would be INVENT (forbidden).**
- **"402 ventilatierooster grilles"** → really **13** placed grilles. The 402 = 372 `IFCPROPERTYSINGLEVALUE` *drawing-style* strings (Surface/Arcering/Arceringpen/Achtergrondpen ×93 = hatching/pen names) + ~30 product-name labels (`IFC_ventilatierooster_DucoFIT_50_ZR` / `_Ducomax_Corto_20`). The grille name is a *Building Material/Profile/Fill* property, not an element.
- **"3752 DUCT refs"** → **zero**. `grep DUCT` matched `IFCPRO`**`DUCT`**`DEFINITIONSHAPE` (3752 of them). No duct references exist.
- **"60 IfcFlowSegment ducts under-extracted"** → all 60 **ARE** extracted (geometry present) and they are **`hwa afvoer`** = hemelwaterafvoer = **rainwater drainage downpipes (drainage/PLB), NOT ventilation.**
- **"NO fittings/terminals kept"** → the **13 grilles ARE kept** as `IfcDistributionElement` named `vent. rooster`, discipline=MEP, **all 13 with geometry** (on 2 storeys: 4 begane grond + 9 tweede verdieping — NOT all 7).
- **routeChains `no-endpoints` root cause** → source has **zero** `IfcPort`/`IfcDistributionPort`/`IfcRelNests`. The 635 `IfcRelConnectsPathElements` are wall joins. There is **no duct↔grille network topology in the model at all** — nothing was dropped at extract; the connectivity does not exist. `routeChains` cannot walk a network that isn't there, and fabricating one is forbidden.
- **NET:** SC's "rich ventilation" is **13 disconnected, fully-extracted grilles + a separate rainwater-drainage run** — not an under-extraction. SC is NOT an ACMV walk-back oracle (no network to learn from). What IS real & open: (1) ELEC host-bind float (roadmap #1, applies as on SH); (2) treating the 13 grilles as standalone ACMV *placement* anchors (PLACE-only, no ROUTE) if a placement-density walk is wanted; (3) ARC/STR remains the clean crawl substrate as stated. DECISION FOR USER: drop the vent-router sub-task (recommended), or pivot SC to ELEC-host-bind / grille-placement-only.

## ➕ ADDENDUM — SC DIRECTION RESOLVED (2026-06-30, prior-session review of the audit above)
The SOURCE-AUDIT above is CORRECT — independently re-verified against `internal/sources/Ifc2x3_SampleCastle.ifc`:
`IFCPRODUCTDEFINITIONSHAPE`=3752 (the "DUCT" artifact), real `IFCDUCT*`=0, 372/402 ventilatierooster=`IFCPROPERTYSINGLEVALUE`,
grilles `vent. rooster`=4(begane grond)+9(tweede)=13 all extracted-with-geometry, all 60 `IfcFlowSegment`='hwa afvoer'
(rainwater/PLB), and `IfcPort`/`IfcDistributionPort`/`IfcRelNests`/`IfcRelConnectsPorts`=0. **Accept it. No vent extraction;
no fabricated ducts.**

**BUT the 3-option fork is slightly mis-framed. RESOLUTION = Option 1 as the spine + fold Option 2 in; NOT Option 3.**
Reason: the audit's "SC is not an ACMV walk-back oracle" is true *for ROUTING* — but the 13 grilles ARE a real
**host-bound PLACEMENT oracle**. Measured: 2nd-floor grilles sit at z=8.22m = the window-top line (windows top ≈8.25m);
ground-floor at 2.5m (above window heads); widths VARY with the opening (0.83 vs 0.063m); product types encode size
(`DucoFIT_50`, `Ducomax_Corto_20`). So a grille is a STANDALONE device **governed by its host (window-top) + sized to the
opening** — NOT a joined-network part. The earlier route→JOIN path (`ad_assembly_connector` face/Ø/connects_to-a-stack)
does NOT apply to it (no port, no network — confirmed).

**MEP relationship taxonomy (the refinement this surfaced — use it to route future MEP work):**
1. **Networked** (route→join→shim at PORTS): supply/waste plumbing, ducted HVAC. Needs ports/connectors. (Duplex-MEP = the
   class-1 oracle, W-WALKBACK-MEP.)
2. **Host-bound standalone** (host-bind + size, **NO join**): vent grilles (`host=IfcWindow, mount=TOP`), ELEC outlets
   (`host=IfcWall, mount=SIDE`), alarms (`host=IfcCovering, mount=BOTTOM`). Governed by host + sizing/spaciousness rule
   (`_shim_attributes` for where, `ad_space_type_mep_bom` for count/size). SC's 13 grilles = the class-2 oracle.
3. **Run without recorded joins** (segments, no ports): SC's 60 `hwa afvoer` rainwater downpipes — reconstructed by
   PROXIMITY (nn route), not by recorded connectors.

**THE SLICE (do this, not the refuted vent-extraction):** generalize `disc_walker.hostBind` from walls-only to
**host-type-agnostic** — drive `host_ifc_class` + `mount` from the shim percept (it currently hard-targets `%Wall%`).
Then ONE witness proves it on TWO real host types:
- (a) **ELEC outlets → wall** (`host=IfcWall, mount=SIDE`) = the genuinely-open SH float fix (W-ELEC-HOSTBIND already
  spiked this for walls; promote + generalize).
- (b) **the 13 SC grilles → window-top** (`host=IfcWindow, mount=TOP`) = walk-BACK against the real grilles: does the
  host-bind rule reproduce their window-top z + opening-width? PLACE-only, no route, no fabricated ducts.
- ⚠ HONESTY: (b) is SELF-CONSISTENCY (mine the window-top rule from the 13, reproduce the 13) — same status as the routing
  rules (mined-then-applied-to-same). Report as such; cross-building generalization (apply to Duplex/SH openings) is a
  LATER step.

**NET for SC:** drop the vent-EXTRACTION task (refuted); KEEP SC as the clean ARC/STR crawl substrate; do the
host-agnostic `hostBind` slice witnessed on ELEC-wall + the 13 grille-window-top. SC is useful for class-2 (host-bound)
and class-3 (proximity run), just not class-1 (networked). DROP the "DX-ACMV-thickening from a vent network" sub-goal —
there is no network; a grille placement-density rule (count/size by room) is the only ACMV thing SC can teach, and it's
class-2 not class-1.

## ✅ THE SLICE DONE (W-HOSTBIND-AGNOSTIC 6/6, 2026-06-30, bim-compiler `build/disc_walker.js`)
`disc_walker.hostBind` is now **host-type-agnostic + mount-aware**: it drives `host_ifc_class` + `mount` from the shim
percept instead of hard-targeting `%Wall%`. `host_ifc_class` matches as a substring (so `IfcWall` still picks up
`IfcWallStandardCase` — wall path byte-for-byte unchanged). Mount faces: **SIDE** (wall centreline→face push, the
original), **TOP/BOTTOM** (nearest host by XY → top/bottom face ± offset), and `same_storey` constrains host selection
to the placement's own storey (REQUIRED for vertically STACKED hosts like windows). Witness `scripts/witness_hostbind_agnostic.js`
proves BOTH host types in one run:
- **(A) H1 WALL-REGRESSION** — ELEC→`IfcWall`/SIDE through the generalized path reproduces the anti-float fix UNCHANGED
  (SH: 36 bound, float 26→0, median 0.145m = wall half-thickness, 0 fabricated). W-ELEC-HOSTBIND 5/5 + §DWG 49 + §DXG 12
  + W-WALKBACK-MEP 8/8 all still green.
- **(B) H0/H2/H3 GRILLE→WINDOW** — the grille→window rule is **MINED** from the 13 real `vent. rooster`
  `IfcDistributionElement`: **7/13** are window-co-located on their OWN storey (median plan snap **0.014m**), each sitting
  a **measured 0.415m above its same-storey window centre — MAD=0.000m** (an EXACT rule, not a fit). Applied back
  (grilles' real XY+storey known, Z stripped → hostBind recomputes Z), it **reproduces** all 7: XY resid 0.014m, **|Δz|=0.000m**.
  The other **6 are honestly REFUSED** (H5) — not window-co-located on their storey (4 ground-floor + 2 wall-nearer), never
  forced. H4: every bound grille carries a REAL window guid. ⚠ HONESTY: this is SELF-CONSISTENCY (mined-then-applied-to-same),
  the same status as the routing rules — NOT cross-building generalization (apply-to-DX/SH openings = a LATER step).
- **Percept promoted:** `VENT_WINDOW_SHIM | IfcWindow | TOP | −513mm` added to ERP.db `_shim_attributes` (TOP−513 ==
  window-centre+415, the measured value; CENTER isn't in the table's CHECK so stored as the equivalent TOP offset).
- **NEXT (open):** (1) generalize the grille rule cross-building (apply to SH/DX window openings — real generalization,
  not self-consistency); (2) the 6 refused grilles' true host (ground-floor wall/ceiling — a 2nd host rule); (3) port
  `disc_walker.js` to `~/bim-ootb/modeller/` + a live SC grille-walk in the §-log (deploy). ELEC host-bind mining promotion
  (W-ELEC-HOSTBIND "promote to mining") still stands as its own bite.

## §SHIM-SELECT — the fixture_ifc_class SELECTION KEY (W-SHIM-SELECT, 2026-06-30)
**Problem (the open hole that blocked DEFAULT-ON host-bind).** `rule_shim` was projected at DISC level only
(`fixture_ifc_class=NULL`); a disc with >1 host (ELEC = wall-outlet + ceiling-light; FP = wall-alarm + ceiling-
sprinkler) was disambiguated by a coarse `priority` (SIDE/wall first). That MIS-BINDS ceiling fixtures to walls.
The selection key = stamp each `rule_shim` row with the fixture's own `ifc_class`, MEASURED from the source
building, so `dwWalk` picks the shim by `fixture_ifc_class == placement.ifc_class`.

**MINING (non-invent, `project_rule_shim.py` gets a `source_db` param).** For each `(disc, fixture_ifc_class)`
the walker actually PLACES (∈ `rule_placement`) and whose disc has ≥1 shim: measure every fixture instance's
point-to-bbox-surface distance to the nearest host of each CANDIDATE host class (the disc's own shim hosts), in
the source building it was mined from (Terminal → `Terminal_extracted.db`; Duplex → `Duplex_mep_meta.db`, disc
resolved via `mep_subdisc`). Winner = host with smallest median. STAMP the per-fixture row ONLY when the winner
is DECISIVE: `median(winner) ≤ MOUNT_TOL (0.5m)` AND (single candidate OR `median(winner) ≤ ½·median(runner-up)`).
Else REFUSE (no per-fixture row — the disc-level row stays as fallback). The per-fixture row copies mount/offset/
height from the matching `_shim_attributes` percept; provenance = `measured:fixture-host-nn:<src>@<median>m`.
- MEASURED (Terminal): IfcLightFixture→IfcCovering(0.040m) · IfcElectricAppliance→IfcWall(0.000m) ·
  IfcAirTerminal→IfcCovering(0.114m) · IfcFireSuppressionTerminal→IfcCovering(0.313m) · IfcAlarm→IfcWall(0.031m).
  DISTINCT hosts within ELEC and within FP = the mis-bind resolved at the DATA level.
- MEASURED (Duplex, generic flow-classes): ELEC IfcFlow*→IfcWall (all ≤0.24m; ceiling >4.8m) → SampleHouse ELEC
  walk stays all-wall = the existing W-DWWALK-HOSTBIND W5 path UNCHANGED. ACMV refused (no host within 0.5m).

**READ PATH (`disc_walker.js`).** New `_shimForFixture(shims, disc, ifcClass)`: exact `fixture_ifc_class` match
first (lowest priority wins), else fall back to disc-level `_shimForDisc`. `dwWalk` host-bind block now GROUPS the
floating placements by `ifc_class` and binds each group with its own shim → lights snap to ceilings, appliances to
walls IN ONE WALK. Caller-passed `opts.shims` (raw `_shim_attributes` rows, no fixture_ifc_class) → no exact match
→ disc-level fallback = byte-identical to today (interim path preserved). hbInfo aggregates across groups
(total bound/refused + per-class breakdown); aggregate `host`='MIXED' when groups differ.

**WITNESS (`scripts/witness_shim_select.js`, W-SHIM-SELECT):**
- S0 MINED-DISTINCT — terminal_rules.rule_shim carries `ELEC/IfcLightFixture→IfcCovering` AND
  `ELEC/IfcElectricAppliance→IfcWall` (distinct hosts), each `provenance LIKE measured:fixture-host-nn%`.
- S1 NON-INVENT-ORACLE — re-measure each stamped row's fixture→host NN independently from the source DB; the
  stamped host == the independently-measured nearest, median ≤ MOUNT_TOL. (no fabrication)
- S2 SELECTION — `_shimForFixture` picks IfcCovering/BOTTOM for IfcLightFixture and IfcWall/SIDE for
  IfcElectricAppliance — different shims for the same disc.
- S3 LIVE-GROUPED — `dwWalk('ELEC', Terminal, {hostBind:true})` binds IfcLightFixture to IfcCovering (BOTTOM) and
  IfcElectricAppliance to IfcWall (SIDE) in one walk; both mounts present; count preserved.
- S4 FALLBACK — a class with no per-fixture row falls back to the disc-level shim (no crash, count preserved).
- S5 REGRESSION — duplex ELEC all→wall (SampleHouse) unchanged → W-DWWALK-HOSTBIND green.

**ACCEPTANCE.** S0–S5 green; existing §DWG 49 / §DXG 12 / W-DWWALK-HOSTBIND 6 / W-HOSTBIND-AGNOSTIC 6 /
W-ELEC-HOSTBIND 5 / W-WALKBACK-MEP 8 stay 0-FAIL after re-bake. Then DEFAULT-ON host-bind is safe (follow-up).
