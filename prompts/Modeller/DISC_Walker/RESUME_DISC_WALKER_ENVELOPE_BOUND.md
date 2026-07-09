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

**⚠ BORDER CONTROL — do this check FIRST, before any other step, every session that touches this card
(user directive, 2026-07-09 PM, written after real repeated confusion this same session — see below):**
1. **In-scope files, full stop: the 8 `<Building>_ARC.db` files + the shared `mesh.db`, all living under
   `modeller/` space only.** Nothing else is this task's business.
2. **Never touch anything under `viewer/`** — not `viewer/import_worker.js`, not
   `viewer/import_db_builder.js`, not `viewer/streaming.js`, nothing in that tree. The Modeller and Viewer
   are permanently separate surfaces (existing doctrine, `project_bonsai_kernel` memory) — this task doubles
   down on it: zero drift into Viewer code, even to "borrow" or "reference" its engine.
3. **Never touch already-working Modeller loader code either** (`real_geometry.js`, `bonsai_library.js`,
   `arc_editable.js`, `str_walker_outliner.js`'s fetch/render logic) unless a check PROVES it's broken —
   these are the proven, working substrate; USE them, don't "fix" them on spec.
4. **If a real code change is ever needed on any file this task doesn't own outright: COPY IT FIRST, edit
   only the copy, never the original** — no exceptions, no "just this once."
5. **Why this is written down, not assumed:** this exact session drifted THREE times before catching itself
   — attempted to edit `viewer/import_db_builder.js` in place (caught, copy made instead), then made the
   copy of the WRONG file (`extractIFC2DB.js`, a parallel Node reimplementation, not the real importer) after
   already being told to use the real one, then had to be corrected a second time before landing on the
   right file. All three were avoidable with a 30-second border-control check before the first edit attempt,
   not after. **Next session: run this checklist BEFORE opening any file, not as damage control after.**

> ▶▶ **RESUME HERE (2026-07-10 — supersedes the 07-09 PM pointer below as the entry point; that one's detail
> is still real and worth reading for depth, just not where to start). Written as a handoff to a FRESH
> session — you do not need to read this whole 700+-line file top to bottom, this block is self-contained.**
>
> **The mission, stated plainly:** this whole thread is the project's sharpest concrete case of the general
> problem — an AI-authored geometry engine will confidently apply a PLAUSIBLE-LOOKING mathematical convention
> (an Euler order, an axis mapping, a rotation formula) that is subtly wrong, and nothing about the code
> LOOKS wrong. The only defense that has actually worked here, twice now, is refusing to trust reasoning
> about geometry and instead computing against REAL measured data through the REAL renderer (real THREE.js,
> real extracted buildings) and diffing against a REAL baseline before believing a result. See
> [[feedback_verify_branch_conditions_before_applying_convention_fix]] (bim-compiler auto-memory) for the
> concrete near-miss this produced THIS session — a fix that looked right, wasn't, and was only caught by
> diffing regression output against `git stash`, not by re-reading the math. Carry that discipline forward
> literally, not as a platitude: any new geometry/rotation/placement work in this file gets the SAME
> treatment — real data, real renderer or an independently-coded oracle, baseline diff — before it's reported
> done.
>
> **State as of 2026-07-10 (all verified, not assumed — see item 3's ✅ block and item 5's ▶ STATUS block
> below for the full evidence trail):**
> - ✅ The 8-building embed (`EMBED_8_ARC_BUILDINGS_MESH_DB.md`) is DONE, committed, and PUSHED —
>   `feat/embed-8-arc-buildings` on `origin` (bim-ootb), not yet merged to main (no PR opened, by design —
>   open one when told to, not before). `mesh.db` (114MB) is now LFS-tracked (`.gitattributes`); it wasn't
>   before and would have hard-failed the push (>100MB) — check LFS coverage before adding any new large
>   binary to this repo, don't assume the existing patterns cover it.
> - ✅ Item 3 (rotation-convention bug in `_eulerMat3`) is FIXED, in BOTH bim-compiler (`build/disc_walker.js`,
>   commit `2a36bb313`) and bim-ootb (merged into `feat/embed-8-arc-buildings`, pushed). Proven against real
>   data + the real renderer twice over: node-side (`scripts/witness_rotation_convention.js`, 30/30) AND
>   real-browser (`modeller/tests/witness_residents_anchor_sweep.js`, 32/32 across all 8 buildings incl.
>   Terminal's 325 tilted elements, ≤1e-5m vs independently-computed ground truth).
> - ✅ Black-box STR+MEP walks run on BOTH Terminal and Duplex against the new consolidated data (item 5's
>   ▶ STATUS block has the numbers). **Named, concrete, carry-forward finding — not a vague caveat:**
>   `hostBind` has never actually selected one of the rotation-tilted hosts (IfcDoor/IfcWindow/IfcFurniture/
>   IfcBuildingElementProxy) on EITHER building tested, because neither `terminal_rules.db` nor
>   `duplex_rules.db`'s `rule_shim` table hosts any MEP class to those IFC classes today (only IfcWall/
>   IfcCovering). The fix's `occupancy()`-path is proven; its `hostBind`-path is not, and won't be until a
>   real, MEASURED window/door/furniture-hosted shim exists to mine — don't build one speculatively just to
>   close this gap, that would be inventing a rule, not fixing one.
> - ⛔ Still open, in priority order: (1) re-run Duplex's FP walk WITH the `dwBorrow('FP', terminal_rules)`
>   percept wired (the current REFUSE there is a script-scope gap in this session's quick test, not a
>   verified engine defect — don't report it as either fixed or broken without re-checking); (2) STR
>   per-storey containment scoping (item 2 below, ~20% outside own-storey footprint) — DEFERRED by explicit
>   user call, 80% is Pareto-acceptable for now, do not re-open as urgent; (3) whatever `WalkerDoctrine.md
>   §13`'s oracle-vacuousness finding (`witness_disc_density.js` D3/D4/D4b) implies for future mesh-to-mesh
>   comparison work — read that section before building one, it's a real, named trap, not boilerplate.
>
> ---
>
> ▶ **RESUME HERE (2026-07-09 PM — supersedes the pointer below; that one is still valid history/context,
> just not the entry point). Goal for the next session, stated by the user verbatim: "finish the DISC walk
> correctly on DX/TE extracting/refining further the present rules, resolving issues of still breaking from
> their extracted DB reference." DX=Duplex, TE=Terminal. Read `EMBED_8_ARC_BUILDINGS_MESH_DB.md`'s own new
> resume pointer FIRST — this session's mesh-consolidation work changed BOTH Duplex's and Terminal's
> underlying `component_geometries`/`element_transforms` data (true-dup + rotation-consolidation + orphan
> removal), so any disc_walker/str_walker test from here on must run against the NEW consolidated
> `Duplex_ARC.db`/`Terminal_ARC.db` + shared `mesh.db` (in `/tmp/wt-embed-8-arc`, or rebuilt via
> `embed8_scripts/finalize_all_8.js` if that worktree is gone), not the old single-file/split-pair originals.**
>
> **1. ✅ Real black-box STR walk RUN on Terminal (`embed8_scripts/str_walk_terminal_blackbox.js`) — first
> actual disc_walker test against the NEW consolidated substrate, no ground-truth peeking during the walk
> (user's explicit "RosettaStoneStrategy" black-box rule — never forget it, applies to every future walk
> test too, not just this one).** Real, substantial result: `dwWalk('STR', Terminal_ARC.db, 'Terminal',
> {geoDb: mesh.db})` against `terminal_rules.db`'s measured STR `rule_placement` rows (columns `ref_kind=
> 'grid'` 30/56/30/30 per storey, beams `ref_kind='storey'` 20/119/126/146, roof/canopy members `ref_kind=
> 'datum'` 284 — all measured, not invented) placed **1,700 STR elements** (330 IfcColumn, 685 IfcBeam, 685
> IfcMember) across the real 14 storeys (genuine names: "Aras 02", "GROUND FLOOR LEVEL", etc. — confirms it
> read the real ARC substrate). `§DW-CAP` backstop fired as designed (logs `placed=N of M, envelope is the
> ceiling` per storey/class — the documented THE-FIX behavior, not a bug).
>
> **2. ⛔ OPEN — STR per-storey containment gap, ~20%, root cause understood, NOT fixed.** Global-envelope
> check (all storeys combined): 162/1700 outside. The METHODOLOGICALLY CORRECT check — per-storey envelope
> (only compare a placement against its OWN storey's real ARC footprint, mirroring this file's own
> D-ENVELOPE witness pattern) — is actually WORSE: **335/1700 (~20%) outside their own storey's footprint**
> (e.g. a column at x=153 on "Aras 02" whose real footprint only spans x∈[101.1,150.1]). Root cause: the
> column/beam GRID is derived globally/aggregately then applied UNIFORMLY to every storey, but Terminal is a
> real stepped/tiered building (upper floors set back, wings that don't exist on every level) — a one-size
> grid inevitably overshoots on a storey smaller than whatever it was derived from. **This is NOT the
> rotation-convention bug (item 3 below), NOT corrupted data, NOT the already-fixed hostBind/occupancy
> true-midpoint bugs** — it's a new, distinct scoping gap in STR's array-density placement specifically.
> **User's call: 80% in-footprint clears the Pareto bar for now ("refinement later") — do NOT re-open this
> as urgent, but it IS the concrete target when "refining the present rules" is picked up**, per this
> session's closing instruction. Fix direction (not yet speced in detail): derive/scale the grid PER-STOREY
> from that storey's own real ARC footprint area-ratio (mirrors THE FIX section's own established
> `n_measured × area_ratio` pattern above, just needs a PER-STOREY area term instead of one global one), or
> clip placements to their own storey's occupancy envelope as a backstop (cheaper, less correct).
>
> **3. ✅ DONE 2026-07-09 PM (bim-compiler `2a36bb313`, bim-ootb worktree `/tmp/wt-terminal-geosplit-port` commit
> `144943b`, NOT pushed/merged) — rotation-convention mismatch in `_trueMidpoint()`/`_eulerMat3()`, FIXED.**
> ⚠ **Near-miss, recorded so it isn't repeated:** the first fix attempt applied the render's `Euler(rotX,
> rotZRad, -rotY)` remap UNCONDITIONALLY — but the render only takes that path `if (rx||ry)`
> (`bonsai_library.js:76`, mirrored by `arc_editable.js:211`); for the COMMON case (rx=ry=0, ordinary wall
> yaw — ALL of Duplex/SampleHouse/SampleCastle, most of Terminal), the render uses a SEPARATE plain-Z-axis-yaw
> path instead. The unconditional remap silently rotated every ordinary yawed wall about Y instead of Z —
> caught by `scripts/witness_true_midpoint.js`'s T5 regression (65→33 outside on real Duplex), confirmed a
> genuine regression (not a flake) via a `git stash` baseline diff before concluding anything. Fixed by
> branching on `rx||ry` (matching the render's own condition exactly) — rx=ry=0 now reduces to the ORIGINAL
> cardinal-Z formula, byte-identical to pre-fix behaviour. **Final proof:** `scripts/witness_rotation_convention.js`
> drives the REAL browser-side `three.core.min.js` via puppeteer against real `/tmp/wt-embed-8-arc/modeller/
> Terminal_ARC.db` elements (the ONLY Terminal DB with non-zero rotation — `~/bim-ootb/modeller/Terminal_meta.db`
> has ALL-ZERO rotations and can't exercise this at all) — old formula diverged up to 1.1569m, fix matches the
> real renderer to 0.00000000m, full 13-file existing regression suite 0-fail (30/30 total). Ported to bim-ootb
> and independently re-verified there too (17/17 real-data + both bim-ootb regression witnesses unchanged).
> Superseded text below kept for the original finding's detail:
> `_eulerMat3(rx,ry,rz)` (disc_walker.js's own world-bbox-midpoint reconstruction, used by `hostBind`'s
> SIDE-mount branch and `occupancy()`) computes a DIFFERENT rotation than the ACTUAL production renderer
> (`arc_editable.js`/`bonsai_library.js`'s `place()`, confirmed via direct source read at
> `modeller/arc_editable.js:135` — `_euler.set(el.rotX, el.rotZ, -el.rotY)`, i.e. THREE's Euler(rotX,
> rotZRad, -rotY) XYZ-order convention). **The two conventions happen to agree** for (a) a locally-symmetric
> mesh bbox under ANY rotation (trivial/degenerate — most real extracted elements are NOT locally symmetric,
> their local origin is the IFC placement ANCHOR not the centroid, confirmed by `real_geometry.js`'s own
> `recenter()`/`anchorOffset` comments), and (b) an ASYMMETRIC bbox under Z-ONLY cardinal rotation
> specifically (proven: this is why the existing `witness_true_midpoint.js` T1 passed — it only ever tested
> a Z-rotated wall). **They MATERIALLY DIVERGE (several metres on realistic geometry) for any real rotation_y
> or rotation_x case with an asymmetric bbox** — verified numerically both synthetically and against REAL
> Terminal data: **325 real Terminal elements carry non-zero rotation_y** (`IfcBuildingElementProxy` 242,
> `IfcDoor` 36, `IfcFurniture` 43, `IfcWindow` 4 — confirmed NOT the roof/canopy IfcPlate mass, which per user
> direction is out-of-scope as a "single signed action, Terminal-centric, unlikely-to-generalize" case, not
> a walker concern). **Scope: this is a disc_walker.js defect (MEP hostBind/occupancy), separate from item 2
> above (STR) — it was NOT exercised by the STR walk just run (STR array-density placement doesn't call
> hostBind/occupancy the same way), but WILL be exercised the moment a Terminal MEP walk runs `hostBind`
> against a window/door/furniture host with non-zero rotation_y.** Test methodology for whoever fixes this:
> reconstruct BOTH conventions' world-bbox-corner-midpoint for a realistic ASYMMETRIC local bbox (NOT a
> synthetic symmetric one — that degenerately agrees under any rotation and will falsely look fixed) at the
> real measured rotation_y values above; the render convention (`arc_editable.js`'s, confirmed correct via
> direct production-code read) is the one to converge `_eulerMat3` toward, not the reverse.
>
> **4. Cross-building STR "science" check (user requested, Pareto-satisfied, not exhaustive) —
> SampleCastle's REAL STR data** (`deploy/buildings/SampleCastle_extracted.db`, NOT the ARC-only resident —
> 23 real IfcColumn, 174 IfcBeam, 9 IfcMember, genuinely independent of Terminal's mined rules): columns hug
> walls tightly (median 0.03m, max 0.26m from nearest wall centreline — real load-path/space-efficiency
> convention, columns embedded in wall thickness), beams span more freely in-plan (max 3.12m from any wall —
> correct, beams connect supports across open space) but track slab elevation tightly (median 0.14m). This
> independently confirms (not just Terminal's own mined pattern) `terminal_rules.db`'s own `ref_kind='grid'`
> (columns) vs `ref_kind='storey'` (beams) distinction is measuring something real, not an artifact. Two
> buildings agreeing was judged sufficient (Pareto) — no further cross-building check requested or needed.
>
> **5. NEXT (in order, per user's "STR then MEP" sequencing + the closing ask):** (a) when picked back up,
> decide whether to fix item 2 (STR per-storey scoping) or item 3 (rotation-convention) first — item 3 has
> the larger blast radius (any MEP hostBind/occupancy call on a rotation_y host) so is likely higher-value;
> (b) run the SAME black-box STR walk methodology against **Duplex** (`Duplex_ARC.db`, the NEWLY-consolidated
> copy — Duplex's own rotation-consolidation touched 18 elements/15 rows, verify the walk still behaves
> correctly against that changed data, not assumed); (c) THEN start MEP (ELEC/PLB/ACMV/FP) black-box walks on
> both DX and TE, which is where item 3's rotation gap actually bites; (d) re-run the existing regression
> suite (§DWG/§DXG/W-DWWALK-HOSTBIND/etc., all listed earlier in this file) against the NEW consolidated data
> to confirm nothing already-proven silently broke from the mesh-consolidation changes themselves — this has
> NOT been done yet, existing witnesses still point at the OLD Terminal_meta.db/Terminal_geo.db pair (deleted
> from the worktree) and would need repointing at the new `Terminal_ARC.db`/`mesh.db` shared-file shape.
>
> ▶ **STATUS 2026-07-10 — item 5(a)/(d) DONE on Terminal, (b)/(c) DONE on Terminal only (Duplex not yet run):**
> (a) decided rotation-convention (item 3) first — DONE, fixed+ported+pushed (see item 3's own ✅ block above).
> (d) DONE: `feat/embed-8-arc-buildings` (bim-ootb, pushed) repoints the regression suite at the new
> `Terminal_ARC.db`+`mesh.db` — 3 witnesses were crashing outright on the removed old filenames (fixed), full
> 8-building anchor-sweep 32/32 PASS (every building's rendered AABB matches ground truth ≤1e-5m, incl.
> Terminal's 325 rotation-tilted elements — the first REAL-BROWSER confirmation of the item-3 fix, stronger
> than the original node-side-only proof). (c) PARTIAL: ran a black-box MEP walk (ELEC/PLB/ACMV/FP) on
> **Terminal only** — all 4 placed cleanly (2067/28/1831/1195), no crashes. **Concrete, narrower-than-expected
> finding, worth carrying forward:** `hostedOnTiltedHost=0` for every discipline — Terminal's live
> `rule_shim` table only hosts to `IfcWall`(SIDE)/`IfcCovering`(BOTTOM) today, so `hostBind` never actually
> selected one of the 325 rotation-tilted hosts (IfcDoor/IfcWindow/IfcFurniture/IfcBuildingElementProxy) in
> this walk — **the item-3 fix's `hostBind`-path coverage is still unproven**, only its `occupancy()`-path
> coverage is (occupancy reads every element on a storey regardless of class, so it did exercise the fix).
> **The fix isn't dormant, but this specific consumer of it is untested. Named, checkable trigger condition
> for whoever picks this up: it becomes testable the moment `rule_shim` grows a window/door/furniture-hosted
> MEP class** (none exist today — this is not an invitation to mine one speculatively, just the fact that
> would make it testable if one is ever measured). (b)/(c) on **Duplex** — DONE 2026-07-10, same black-box
> method, real `Duplex_ARC.db`+`mesh.db`: **STR REFUSE** (`duplex_rules.db` genuinely has zero STR
> `rule_placement` rows — honest, matches doctrine, not a bug); **FP REFUSE** (no measured FP rule either —
> Duplex was never called with the `dwBorrow('FP', terminal_rules)` percept in this quick script, so this
> REFUSE is a script-scope gap, not proof FP-borrow is broken on Duplex specifically — that mechanism is
> already proven elsewhere for SC, just not re-verified here for DX); **ELEC 115 placed (0 outside), PLB 38
> placed (1 outside), ACMV 10 placed (0 outside)** — all clean. Duplex has 18 tilted elements (3 IfcDoor/3
> IfcFurnishingElement/12 IfcWindow, matches the embed-8 rotation-consolidation count exactly) — **same
> `hostedOnTiltedHost=0` finding as Terminal**: `duplex_rules.db`'s `rule_shim` also only hosts to
> IfcWall/IfcCovering, so the item-3 fix's `hostBind`-path remains unexercised on BOTH buildings tested so
> far, generalizing the "named, checkable trigger condition" above across residential AND Terminal-scale.
> **NEXT (not yet done, whoever picks this up):** re-run FP on Duplex WITH the borrow percept wired (verify,
> don't assume); the STR-per-storey item (2) stays deferred; no other open item in this file.
>
> **6. ⚠ READ `WalkerDoctrine.md §13` BEFORE building any mesh-to-mesh or count-vs-oracle comparison work
> (added 2026-07-09 PM, user-requested foresight write-up).** Real instance found THIS session, not
> hypothetical: `witness_disc_density.js`'s count oracle (`Terminal_meta.db`) is 100% ARC — its D4/D4b checks
> are structurally vacuous for Terminal (always divide-by-zero/fail regardless of walker correctness), yet a
> "3 pre-existing fails, unchanged before/after" report read as if it were a validated result. §13's rule:
> verify an oracle's OWN reference data is real/non-trivial for the specific (building, discipline) pair
> BEFORE trusting its verdict — assert it, fail loud (`§ORACLE-VACUOUS`) if not, never let a vacuous
> reference silently produce a pass/fail that gets counted as meaningful. Also settles the "true RSS" scope
> question from the same discussion: mesh-to-mesh fidelity is only buildable/provable where a REAL mesh
> reference exists (SampleCastle's real STR, Duplex's real MEP via `W-WALKBACK-MEP`) — Terminal's generated
> STR/MEP has no such reference anywhere in this project's data and never will without a real re-extraction,
> so it stays in the GENERATED bucket (count-exact, envelope-plausible, never claimed mesh-verified) by
> design, not as an unfinished gap.
>
> ---
>
> ▶ **RESUME HERE (2026-07-09, supersedes the 2026-06-30 pointer below — that work is old/settled, see §BUG-A
> sections near the end of this file for full detail):**
> - **Bug B (render never applied hostBind's yaw) — SHIPPED, bim-ootb PR #717, OPEN NOT MERGED.** Confirm merge
>   status before treating it as closed.
> - **Bug A (fixtures outside the building envelope) — PARTIALLY FIXED, verified on the LIVE evidence DB, NOT
>   ported to bim-ootb yet.** `build/disc_walker.js`'s `hostBind()` SIDE-mount wall branch now resolves a TRUE
>   world-bbox midpoint from each wall's own real mesh (`component_geometries` OR `base_geometries` —
>   `_trueMidpoint()`), instead of trusting `element_transforms.center` (proven to be an unreliable raw IFC
>   placement-line origin, not a midpoint). Re-verified directly against `~/bim-ootb/modeller/Duplex_extracted.db`
>   (the actual 65/267-outside evidence DB, read-only): **65/267 → 0/242 outside, real per-placement shifts up
>   to 2.25m on SampleCastle** (not vacuous). 18/18 witnesses green (`scripts/witness_true_midpoint.js`,
>   `build/logs/witness_true_midpoint_2026-07-08T2201.log`) — 2 other witnesses found sharing the same
>   raw-center ground-truth bug were fixed along the way (`witness_elec_hostbind.js`, `witness_dwwalk_hostbind.js`).
> - **✅ item 1 (MEP-wide scope) DONE 2026-07-09 (W-OCC-TRUE-MIDPOINT 17/17, `scripts/witness_occ_true_midpoint.js`,
>   commit pending):** measured (not assumed) each site named in the open item, per-site:
>   - `routeChains`/`_loadXYZ(B)` (PLB/ACMV nn-pairing classes IfcFlowSegment/IfcFlowFitting) — true-midpoint
>     delta max **0.21m** on real evidence (Duplex/SampleCastle), an order of magnitude below the wall defect —
>     **NOT fixed**, same "don't overfit past the evidence" precedent already applied to hostBind's point-host
>     branch (no proven defect at this site = scope creep, not rigor).
>   - `occupancy()` (the fixture-placement footprint mask, reads EVERY element incl. walls) — true-midpoint
>     delta max **3.12m** (Duplex) / **1.03m** (SampleCastle), dominated by IfcWall* — a REAL, proven risk —
>     **FIXED** (`disc_walker.js` `_occElements`/`occupancy`, same `_trueMidpoint` recovery as hostBind, cached
>     per (bdb,storey) since occupancy() is called once per placement rule × storey). Ripples into 3 witnesses'
>     independent-oracle mirrors (which deliberately re-derive occupancy locally so the check grades the engine,
>     not itself) — all re-baselined in the same commit: `build/witness_disc_walk_generalize.js` G2's `occCells`,
>     `build/witness_disc_walk_density.js` D-ENVELOPE's `occCells`, `scripts/witness_hostbind_agnostic.js` H1's
>     hardcoded bound-count (36→37, count-preserved, 0 fabricated — one previously out-of-reach ELEC fixture
>     landed within reach of a real corrected wall). `scripts/witness_true_midpoint.js` T5 also updated: the
>     original 65/267 claim now needs a FROZEN pre-fix occupancy reproduction (`oldPlaceDensity`) since the live
>     engine's `DW.place()` no longer generates the old (buggy) floating positions — reveals a bonus finding:
>     the occupancy fix ALONE (old hostBind + new occupancy) already closes 65→0 outside on LIVE Duplex; hostBind's
>     own fix remains necessary for buildings without an ELEC density path (e.g. Terminal).
>   - `gate()` — **not actually a raw-DB reader** (operates on already-placed `p.x/y/z`, downstream of
>     place()/hostBind); the open item's framing was imprecise here. No fix applicable, nothing to scope.
>   Full regression: 11 existing DW witness files, 0 fail (§DWG 49, §DXG 12, density 43, hostbind-agnostic 6,
>   true-midpoint 21, shim-select 6, elec-hostbind 5, dwwalk-hostbind 6, walkback-mep 9, generalize-xbuild 7,
>   rule-connector 4). `scripts/witness_hostbind_rotation.js`'s 4 fails are PRE-EXISTING and UNRELATED (confirmed
>   via `git stash` baseline before touching this item) — CORRECTION (independent reviewer session, 2026-07-09):
>   NOT Bug B/PR #717 as first attributed here. `DW._hostAxis` (the cardinal-swap world-run-axis fix this witness
>   tests, R1-R4) does not exist in `disc_walker.js` at all — a separate same-day thread attempted it, found it
>   disproven, and correctly reverted it, leaving this witness failing against the reverted (absent) function.
>   Same topic (rotation/yaw), different mechanism/file than PR #717 (render-side, never applies hostBind's yaw).
> - **✅ item 2 (VENT_WINDOW_SHIM raw-artifact check) DONE 2026-07-09 (part of the same W-OCC-TRUE-MIDPOINT pass):**
>   confirmed — it WAS an artifact. SampleCastle is mesh-backed (unlike Terminal), so the check didn't need a
>   Terminal mesh recovery. All 7 grille-associated windows carry a consistent, tight (MAD=0) true-midpoint Z
>   defect: `true_z = raw_z − 0.0835m`. Re-mined `scripts/witness_hostbind_agnostic.js` H0 off `DW._trueMidpoint`
>   for both grille and window (previously raw `center_z` both sides): offset moves **0.415m → 0.498m** (median,
>   MAD still 0.000m — an exact rule, not looser). `hostBind`'s CENTER/TOP/BOTTOM apply branch (`disc_walker.js`
>   ~line 493-518) now ALSO uses the true host `tz` (was deliberately RAW before, per the old §BUG-A SCOPE NOTE —
>   that note is now superseded: it was scoped-out only because mining and apply sides disagreed; fixing BOTH
>   together keeps them self-consistent, H3 REPRODUCE still |Δz|=0.000m). The promoted `library/ERP.db
>   _shim_attributes` row (`VENT_WINDOW_SHIM | IfcWindow | TOP | −513mm`, the CENTER+415 equivalent) corrected to
>   **−429mm** (CENTER+498 equivalent, all 7 samples agree exactly) — gitignored local artifact, not a tracked
>   file, no commit needed for that row; disc-mapping into `rule_shim` is still deferred (product_value prefix
>   "VENT" doesn't match any real discipline code, so this percept was never live-wired — inert until that's done).
>   XY was NOT touched (no XY defect ever measured on point-hosts — same non-overfit scope discipline, now
>   explicit in the code comment). Regression: same 11-file suite, 0 fail.
> - **✅ item 1 (Terminal "data-recovery problem") FALSIFIED + FIXED 2026-07-09 (W-TERMINAL-GEOSPLIT 16/16,
>   `scripts/witness_terminal_geosplit.js`):** the "no mesh payload exists in any live/committed Terminal DB"
>   conclusion was reached by testing `deploy/buildings/Terminal_extracted.db` (this repo's own committed copy,
>   used by the SEPARATE RosettaStone/RSS compiler-gate system — genuinely mesh-free, confirmed exhaustively:
>   every table checked, cross-referenced against `library/component_library.db` and against
>   `~/bim-ootb/modeller/Terminal_geo.db` itself, 0 hash overlap with either — that RSS-side file really has no
>   mesh anywhere) — **but that's the wrong file for the walker.** The LIVE Modeller resident
>   (`~/bim-ootb/modeller/Terminal_meta.db` + `Terminal_geo.db`, confirmed the current source) splits geometry
>   across two files because sql.js can't JOIN two independently-opened DB handles — the exact reason
>   `real_geometry.js`'s `buildGeometryIndex(db, geoDb)` already exists, proven live for Terminal's OWN rendering
>   since 2026-07-02. `disc_walker.js` never got the same treatment. Fixed: `_trueMidpoint`/`_geomRow`/
>   `hostBind`/`occupancy`/`_occElements`/`place`/`dwWalk` all take a new OPTIONAL trailing `geoDb` param
>   (omitted everywhere else → byte-identical old behaviour, confirmed via full regression). With it: **333/333
>   Terminal walls resolve real mesh** (were 0/333 unverified before); true-midpoint delta reaches **12.69m**
>   (worse than Duplex's 3.12m or SampleCastle's 1.03m); end-to-end `hostBind` real per-placement shift reaches
>   **53.46m**, 124/751 placements moved >1m — not a vacuous flag-flip (envelope-outside-count alone doesn't
>   catch it on a building this size, 75m×57m bbox — same "measure the shift, not just containment" lesson
>   already on record in `witness_true_midpoint.js`). **Doctrine written:** `docs/internal/WalkerDoctrine.md §12`
>   — ARC needs no separate gate (same render method as the Viewer, verified); every walked discipline needs a
>   POST-WALK, INDEPENDENTLY-CODED oracle, never the engine (or a self-reporting Java RSS gate) grading itself.
>   ⚠ Scope discipline: this is disc_walker/Modeller-side ONLY — `RosettaStoneGateTest.java`/RSS was NOT touched
>   (a real self-report collusion shape WAS found there, `G1-COUNT`'s `readGenerativeCount()`, but per explicit
>   user direction it's noted as a known gap, not fixed by editing Java — the JS witness layer is the answer).
> - **✅ item 1 (port to bim-ootb) DONE 2026-07-09 PM, worktree only, NOT pushed/merged:** `/tmp/wt-terminal-geosplit-port`
>   (branch `feat/terminal-geosplit-port` off fresh `origin/main`, commit `8d161fa`) — surgical merge (not a wholesale
>   overwrite) of `_trueMidpoint`/`_eulerMat3`/`_geomRow`/occupancy-true-midpoint/hostBind-true-midpoint/`geoDb` onto
>   the CURRENT live `modeller/disc_walker.js`, preserving that file's own MEP-RosettaStone additions (bendFittings/
>   routePattern/IDB-timeout-guard) which don't exist in bim-compiler's copy at all. VENT_WINDOW_SHIM re-mine was
>   NOT ported — it's a DB-row correction (`library/component_library.db`, gitignored) that was never live-wired
>   anyway ("VENT" prefix doesn't match any real discipline code, per the original commit message), inert either side.
>   PROOF: real numbers reproduced exactly against `~/bim-ootb/modeller/Terminal_meta.db`+`Terminal_geo.db` (333/333
>   walls resolve, delta 12.6856m, hostBind shift 53.46m/124>1m — same as bim-compiler's own witness); existing
>   bim-ootb witnesses re-run before/after the port on the SAME file (`modeller/tests/witness_disc_density.js`:
>   5 PASS/3 FAIL both times, identical shape, the 3 fails are pre-existing/unrelated; `witness_route_pattern_bridge.js`:
>   10/10 PASS). ⚠ Bundled with this port is a genuine caveat, NOT closed by it: a SEPARATE rotation-convention
>   mismatch in this same `_trueMidpoint`/`_eulerMat3` machinery (item 3 above) still gives a WRONG midpoint for any
>   host with non-zero rotation_y — 325 real Terminal elements qualify. Do not report Terminal containment as fully
>   fixed on the strength of this port alone.
> - **Don't re-litigate:** the Start/End (BOM Tack-chain) reconstruction shortcut was tested rigorously across
>   all 3 mesh-backed buildings and falsified on 2 of them (SampleHouse 7.95m error, SampleCastle 7.85m error) —
>   it only matched Duplex (0.03m) because of that file's specific authoring history, not a general IFC rule.
>   Full trail in §START-END-THEORY-TESTED. Don't propose it again without new measured evidence.
>
> ---
>
> *(older, settled pointer below — kept for history, not the resume point)*
> ▶ **RESUME HERE (2026-06-30)** — ✅ SELECTION KEY DONE (W-SHIM-SELECT 6/6, see §SHIM-SELECT below). `rule_shim`
> now carries `fixture_ifc_class` MEASURED from the source building (5 terminal + 3 duplex per-fixture rows; every
> host is the nearest-NN, ambiguous→REFUSE→disc-level fallback). dwWalk groups floating placements by ifc_class and
> binds each with its own shim → ELEC ceiling-lights snap to IfcCovering instead of being mis-bound to walls (Terminal:
> 1872 lights→ceiling, 0→wall). All regressions green: §DWG 49 · §DXG 12 · W-DWWALK-HOSTBIND 6 · W-HOSTBIND-AGNOSTIC 6
> · W-ELEC-HOSTBIND 5 · W-WALKBACK-MEP 8 · disc_walk_shim 6. ⚠ IMPLEMENTATION NOTE: applied the projection STANDALONE
> (`python3 build/project_rule_shim.py <db> <class> <src>`) onto the COMMITTED `*_rules.db` — a FULL re-bake drifts
> `rule_avoidance` 10(global-p05)→47(per-storey) because the committed DBs were baked by a newer process than the
> current `bake_*.py`. Touch only rule_shim until that bake drift is reconciled.
> **✅ DEFAULT-ON host-bind DONE** (358dcbd2 — `{noHostBind:true}` restores raw floating; W-DWWALK-HOSTBIND reframed).
> **✅ PER-DISCIPLINE BORROW DONE** (5f013fe2 — `dwBorrow(disc, db)`, W-BORROW-FP 6/6: SampleCastle walks ELEC/ACMV/PLB
> from duplex_rules + BORROWS FP/sprinkler from terminal_rules, 151 sprinklers host-bound to real ceilings, gate stays
> residential; `_primFor` = LOD400 render seam). **✅ ANTI-DRIFT: `docs/WalkerDoctrine.md` is now the LOCKED core doc**
> (CLAUDE.md pointer; small→DX / Terminal=LOD400-ref+borrow-source / dwInit-default trap / §DWG=generalization-test).
> **✅ bim-ootb PORT DONE+LIVE 2026-06-30 (PR #576 MERGED, sw v20):** ported current `disc_walker.js` + both
> `*_rules.db` → `modeller/` (worktree off origin/main, NOT shared tree); brings DEFAULT-ON host-bind + §SHIM-SELECT
> live to the modeller for the first time. New engine helper `dwBorrowFile` (browser IDB-cached borrow-by-file) +
> modeller `_dwEnsureBorrow()` (residential primary borrows FP←terminal WITHOUT switching class; wired into roster +
> click-walk). `§LOD-SEAM` render: `_renderDiscWalk` geometry now keyed by `p.prim` through `window._dwPrimGeo` (the
> single LOD400 swap point; POC returns the MEASURED box for every kind — no fabricated shape, W-DW-PRIM). `§DW-RULES-BUST`:
> fixed a LATENT staleness bug (since #562) — `§DW_IDB` cached the MUTABLE rules DBs keyed by url with no version, so
> returning users kept old rules; now evicted once per `__dwRulesVer`. Live-verified: sw=v20, disc_walker has dwBorrowFile,
> modeller.html has _dwEnsureBorrow/_dwPrimGeo/__dwRulesVer, both live `*_rules.db` byte-identical to build (FP sprinkler
> shim live: FP/IfcFireSuppressionTerminal→IfcCovering/BOTTOM). SC sprinkler-walk now reachable in the modeller (151
> host-bound). bim-compiler `37c603e8` (dwBorrowFile + density witness generation-count fix → §DWD 43/0).
> **✅ roadmap #1 (de-ERP RENAME) DONE 2026-06-30** (bim-compiler `c70bfce1`): all 11 live code readers →
> `disc_patterns.db`, producer rebuild_erp.sh outputs it + back-compat ERP.db symlink, accounting-split is a no-op
> (ERP.db already pure geometry-pattern), full suite green, NO impact (byte-identical symlink). See roadmap #1 below.
> **✅ roadmap #3 Route→ASSEMBLE DONE 2026-06-30** (bim-compiler `f443ae4d`+`a46b4c25`): `assemble()` instantiates
> catalog parts at routed nodes (W-ASSEMBLE 10/10, Duplex-MEP oracle); **#3a** projected `rule_joint_piece` (no caller
> catalog); **#3b** `connectorFor`/`connectorEnrich` connector-face orient + clearance standoff (W-ASSEMBLE-CONNECT 6/6,
> live 151 SC sprinklers → SPRINKLER→FP_MAIN). See roadmap #3 below for the engine detail.
> **✅ roadmap #3c DONE+LIVE 2026-06-30** (bim-compiler `e74742ad`; bim-ootb PR #578 MERGED, sw **v21**, GH-Pages live):
> ported `assemble`/`connectorEnrich` into the modeller render. **NEW first-class `rule_connector`** (the "optional earlier"
> step, now done) — `build/project_rule_connector.py` projects `disc_patterns.ad_assembly_connector`(+manifest) → a
> `rule_connector` table per `*_rules.db` keyed (disc, ifc_class), DECISIVE-only (exactly one assembly w/ a SERVICE
> connector), face/Ø/connects_to verbatim, standoff = manifest clearance: **terminal = SPRINKLER + LIGHT (2 rows); duplex
> = 0** (generic IfcFlow* have no assembly mapping — honest, not a gap). Applied STANDALONE (DROP+CREATE rule_connector
> only = zero drift; also wired into both bake scripts). `disc_walker.connectorEnrich` now falls back to `_loadConnectors(disc)`
> when no `opts.connectors` (projected path keys by (part.disc, part.ifc_class); caller-passed path byte-identical) →
> **the modeller enriches with NO caller percept** (it never carries `disc_patterns.db`). **W-RULE-CONNECTOR 4/4**
> (`scripts/witness_rule_connector.js`: 151 live SC sprinklers enriched identically by projected vs caller path); full
> suite green. Render (modeller.html): `_renderDiscWalk` draws each enriched fixture's FIXTURE→SERVICE hookup as a
> pale-cyan edge along `connector.faceDir` (len = standoff else 0.3m stub) → live **SC FP walk = 151 SPRINKLER TOP→FP_MAIN
> edges** (§DW-CONNECT); `_renderDiscAssembly` instantiates projected catalog PARTS (`rule_joint_piece`) at routed nodes
> via the `_dwPrimGeo` LOD seam + connector edges when a network exists (SC FP has no duct topology → assemble honest-REFUSE,
> §DW-ASSEMBLE). `__dwAssembly` wired into `_clearDiscWalk`/`_redrawAllDiscWalks` (survives commit re-fold). sw v20→v21 +
> `__dwRulesVer` v20→v21 (rules DBs changed → §DW-RULES-BUST re-fetch). Live-verified: sw=v21, modeller has _renderDiscAssembly/
> §DW-CONNECT, disc_walker has _loadConnectors, live terminal_rules.db carries SPRINKLER+LIGHT rule_connector, duplex 0.
> **✅ roadmap #4 (render gate) DONE+LIVE 2026-06-30** (bim-ootb PR #579 MERGED, sw **v22**, GH-Pages live). The
> routeChains→tube render was ALREADY live (`_renderDiscChains`, §DW-TUBE); the genuine open piece was the `__dwPixelProbe`
> readPixels assertion (the §8E-3/`_seedStrWalk`/`swbCanopyOps` refs in the old #4 text are STALE — none exist). Added
> `window.__dwPixelProbe(disc)` (scene-graph census of dwRoot: fixture box InstancedMesh + §3c connector-edge LineSegments
> + tubes + assembled parts, plus a one-frame readPixels litPct) + a thin render-only witness seam
> `window.__dwRender={walk,assembly,redraw}`. ⚠ The production `discWalk()` path caches rules in `bim_ootb_cache`
> IndexedDB which HANGS under puppeteer+swiftshader (real browsers fine — verified live by curl); the witness drives the
> render via the IDB-FREE engine API (`dwOpen`/`dwBorrow` with plain-fetched DBs) + the seam (the render fns are
> byte-identical to discWalk's). **W-DW-PIXELPROBE 6/6** (`modeller/tests/witness_dw_pixelprobe.js`, puppeteer): FP walk
> on SC → 759 fixture instances (2 classes), 1 connector-edge LineSegments covering all 663 enriched sprinklers,
> §DW-CONNECT hookups==enriched (count is a node-witnessed VALUE — gate asserts log==render not a magic number), canvas
> non-blank, assemble honest-REFUSE (no network, 0 parts), no pageerror. sw v21→v22 (modeller.html changed; DBs unchanged
> → __dwRulesVer stays v21). Live-verified sw=v22 + probe/seam present.
> **✅ roadmap #5 (cross-building generalization) DONE 2026-06-30** (bim-compiler `a97978cf`, **W-GENERALIZE-XBUILD 7/7**,
> `scripts/witness_generalize_xbuild.js`). The doctrine-central upgrade from SELF-CONSISTENCY to HELD-OUT: route
> `duplex_rules` (mined from Duplex) onto **LTU_AHouse** — a HOUSE with a real 32k-fitting generic-IfcFlow* network
> NEVER used in mining — scored vs LTU's OWN geometric-touch oracle (the W-WALKBACK-MEP oracle). **Result: 32138 segs,
> precision 0.839 @0.15m (0.945 @0.30m), 0 fabricated, 0 over the Duplex-measured 3.298m bound** → the measured bound
> generalizes without widening. Self-consistency baseline (Duplex-on-Duplex) = 0.969; **gap 0.130 = the honest measured
> cost of generalization** (reported, not hidden). ⚠ SUBSTRATE FINDING (settles my earlier caveat): among the residential
> class SH/DX/SC there is NO held-out MEP target (SH ARC-only, DX is the mining source, SC rainwater-only) — LTU_AHouse
> (a house, same IfcFlow* taxonomy, rich real network) IS the genuine held-out residential-domain target. Bim-compiler
> witness, no deploy.
> **NEXT BITE:** the prioritized roadmap #1–#5 is now DRAINED. Remaining (thinner / substrate-gated) threads: (a)
> PLACEMENT-cadence generalization analogue — BLOCKED: held-out buildings lack grilles/named-fixtures for ground truth
> (the grille→window-top rule has no held-out target; same substrate wall as routing had before LTU); (b) deeper
> route-to-FACE face-AND-direction model for ACMV ducts (M7 already lifts it partially); (c) ELEC host-bind mining
> promotion (residential ELEC as ref_kind='host' in the bake, vs today's post-step). Pick per user direction.
> Full status: §NEXT + roadmap below.
>
> ▶ **(b) PICKED 2026-06-30 (user direction) — ✅ §FACE-SURFACE DONE (W-FACE-SURFACE, see §FACE-SURFACE spec below).**
> FINDING: the ACMV "ducts are genuinely harder" precision (0.269 centre / 0.332 face-by-line @0.15m) is SUBSTANTIALLY a
> **centre-to-line SCORING ARTIFACT** on bulky elements, not a real disconnection. A face/surface-aware touch — gap =
> centre-line gap − BOTH elements' MEASURED perpendicular half-extents (clamped ≥0) — shows the ducts genuinely connect:
> ACMV nearest-run touch **0.518→0.996**, while thin PLB is INVARIANT (TE 0.998→0.999, DX 0.978→0.980). Two independent
> falsifiers prove it is a real correction, not free leniency: (1) PLB-INVARIANCE (bulk-proportional — thin pipes don't
> move); (2) RANK-DISCRIMINATION (surface-touch nearest 0.996, 2nd 0.698 [a fitting is a junction — physically joins ≥2
> ducts], 5th 0.014, FARTHEST 0.000 → still rejects wrong pairs). Engine: `routeChains(disc,bdb,{toFace:true})` now
> attaches `gapSurface` (additive; `gap`/guids/pairing UNCHANGED → M7 + W-WALKBACK-MEP invariant). (c) is SUBSUMED by
> §SHIM-SELECT (default-on, rule_shim-driven host-bind = the "promote to mining" intent; ELEC wall/ceiling split mined;
> residential stays all-wall by substrate, not engine). (a) stays BLOCKED (no held-out fixtures).

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
1. ✅ **RENAME → `disc_patterns.db` DONE 2026-06-30** (de-"ERP", per §NAMING DIRECTIVE; bim-compiler `c70bfce1`).
   FINDING: `library/ERP.db` is ALREADY a pure geometry-pattern store — accounting tables (`C_Order`/`C_OrderLine`)
   are EMPTY and `fact_acct`/`AD_Window`/GL are ABSENT, so the "split accounting out" part is a **no-op**; the DB is
   git-IGNORED (local build artifact). REAL work = migrate readers. ALL 11 live code readers migrated ERP.db→disc_patterns.db
   (4 build witnesses + 7 `scripts/`; project_rule_shim already used it). Producer `scripts/rebuild_erp.sh` now builds
   `disc_patterns.db` (real) + a back-compat `ERP.db` symlink. NO deployed/runtime reader names ERP.db (verified). NO IMPACT:
   disc_patterns.db is byte-identical to ERP.db (symlink) so every witness reads the same bytes — full suite GREEN
   (incl. erp-equiv 14, erp-landed 4, reconcile 12, roof-bound 10). ⚠ erp_equivalence/erp_landed had a PRE-EXISTING
   §SHIM-SELECT drift (terminal_rules has `rule_shim`; the ERP.db TRM001 views don't → default-on host-bind diverged
   POSITIONS, counts always matched) — fixed by comparing the RULE-SOURCE walk with `{noHostBind:true}` (same pattern as
   §DWG/§DWD). Docs still say "ERP.db" (many legitimately about ERP integration, not the store) — left as-is; the accretion
   risk was in CODE, now drained. ⓘ Local tree keeps ERP.db real + disc_patterns symlink (resolves fine); a fresh
   `rebuild_erp.sh` produces the canonical layout (disc_patterns real + ERP.db symlink) — cosmetic, both resolve.
2. ✅ **SHIM is now a first-class PROJECTED rule** (`build/project_rule_shim.py` → `rule_shim` in each `*_rules.db`; `dwWalk`
   reads it; W-DWWALK-HOSTBIND 6/6 incl W5). **REMAINING = the SELECTION KEY `disc + ifc_class`** — `rule_shim.fixture_ifc_class`
   is NULL today, so a disc with >1 host (ELEC wall-outlet + ceiling-light; ACMV ceiling-diffuser + window-grille) picks by
   `priority` only → would mis-bind ceiling LightFixtures to walls. THIS is why host-bind is OPT-IN not default-on. Next bite:
   stamp `fixture_ifc_class` per shim (mine which fixture class mounts on which host) → `_shimForDisc` matches on it → safe
   default-on. SPLIT ELEC wall(outlet) vs ceiling(light); verify GENERATED residential ELEC/FP float drops with NO caller percept.
3. ✅ **Route→ASSEMBLE bridge DONE 2026-06-30** (bim-compiler `f443ae4d`, W-ASSEMBLE 7/7). `disc_walker.assemble(disc,
   bdb, {catalog})`: at each routed NODE (real element endpoint from routeChains), instantiate the matching catalog
   piece (`_import_joint_piece_types` by ifc_class) — POSE from the real node, TYPE+Ø from the MEASURED catalog,
   ORIENTATION from the incident run vector; no-catalog → REFUSE, no-part-for-class → honest SKIP. ORACLE=Duplex-MEP:
   661 parts, posDrift 0m (every part on a real element), Ø re-measured from catalog (FlowFitting 45mm/FlowSegment
   50mm, mismatch 0), 363 distinct run-derived orientations, 358 joints carry their catalog Ø pair — LANDED/real→real,
   the assembly analogue of W-WALKBACK-MEP.
   ✅ **(a) PROJECTED CATALOG DONE** (`a46b4c25`, W-ASSEMBLE 10/10): `build/project_rule_joint_piece.py` projects
   `disc_patterns._import_joint_piece_types` → a `rule_joint_piece` table per `*_rules.db` (keyed by (disc, ifc_class)
   from `rule_routing`, MEASURED median Ø/length from the matching source; duplex 3 rows, terminal 6, honest skips).
   `assemble` reads it via `_loadJointPieces` when no `opts.catalog` → no caller percept. Isolated/idempotent
   (rule_avoidance 4/10 unchanged — no bake drift). J1-J4: projected-source, traceable, non-invent, refuse-when-absent.
   ✅ **(b) CONNECTOR-FACE ORIENT + STANDOFF DONE** (`a46b4c25`, W-ASSEMBLE-CONNECT 6/6): `connectorFor()` reads the
   FIXTURE→SERVICE hookup verbatim from `ad_assembly_connector` (face/Ø/connects_to) + `ad_assembly_manifest` standoff;
   `connectorEnrich()` attaches it + stands the pose off along the face by the measured clearance; `_faceDir` is a frame
   convention (face name → local axis). Applied to the LIVE SC FP sprinkler walk: 151 sprinklers carry SPRINKLER→FP_MAIN
   (TOP, Ø25, flush 0m); unmapped fixtures untouched. `assemblyKey` is caller-passed (a projected `rule_connector` is
   the later first-class step).
   ✅ **(c) MODELLER RENDER + first-class rule_connector DONE+LIVE 2026-06-30** (bim-compiler `e74742ad`; bim-ootb #578,
   sw v21): `build/project_rule_connector.py` → `rule_connector` per `*_rules.db` (terminal SPRINKLER+LIGHT, duplex 0
   honest, DECISIVE-only, zero drift); `connectorEnrich` reads it via `_loadConnectors` with NO caller percept
   (W-RULE-CONNECTOR 4/4 — projected == caller path on 151 live SC sprinklers). Modeller `_renderDiscWalk` draws hookup
   edges (§DW-CONNECT) + `_renderDiscAssembly` parts-at-routed-nodes via the `_dwPrimGeo` seam (§DW-ASSEMBLE, honest-REFUSE
   on no-network). See top RESUME pointer for detail.
4. ✅ **RENDER GATE DONE+LIVE 2026-06-30** (bim-ootb #579, sw v22). routeChains→tube render was ALREADY live
   (`_renderDiscChains`, §DW-TUBE); the §8E-3/`_seedStrWalk`/`swbCanopyOps` refs above are STALE (none exist). The real
   open piece = the `__dwPixelProbe` readPixels assertion: added `window.__dwPixelProbe(disc)` (dwRoot scene-graph census
   + readPixels litPct) + render-only seam `window.__dwRender`. **W-DW-PIXELPROBE 6/6** (`modeller/tests/witness_dw_pixelprobe.js`,
   puppeteer) drives the render IDB-free (production discWalk's IndexedDB rules-cache hangs under swiftshader; real browsers
   fine). See top RESUME pointer.
5. ✅ **CROSS-BUILDING GENERALIZATION DONE 2026-06-30** (bim-compiler `a97978cf`, W-GENERALIZE-XBUILD 7/7). Mined
   `duplex_rules` → HELD-OUT **LTU_AHouse** (house, real 32k-fitting IfcFlow* network, never mined), scored vs LTU's own
   geometric-touch oracle: 32138 segs, precision **0.839 @0.15m** (vs self-consistency 0.969, gap 0.130), 0 fabricated,
   0 over the Duplex-measured bound. SUBSTRATE FINDING: no held-out MEP target inside SH/DX/SC (SH ARC-only, DX mined-from,
   SC rainwater-only) — LTU_AHouse is the genuine one; routing DOES have a held-out target after all. See top RESUME.
   Residual: route-to-FACE ACMV (M7 partial); PLACEMENT-cadence generalization is BLOCKED (no held-out grilles/fixtures).

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

## §3c — modeller ASSEMBLE render + first-class rule_connector (SPEC, 2026-06-30)
**Goal (roadmap #3c).** Port `assemble`/`connectorEnrich` into the modeller render: at routed NODES instantiate the
catalog PART mesh via the `_dwPrimGeo` LOD seam, and draw the FIXTURE→SERVICE connector HOOKUP EDGE. Deploy the new
`rule_joint_piece` (already projected) + `rule_connector` (NEW, below) DBs.

**Prereq — project `rule_connector` first-class (the "optional earlier" step, now REQUIRED).** The modeller carries
only `*_rules.db` (not `disc_patterns.db`), so `connectorEnrich` needs the connector hookup as a PROJECTED rule, mirroring
`rule_shim`/`rule_joint_piece`. `build/project_rule_connector.py`:
- KEY = `(disc, ifc_class)`. Resolve `ifc_class → assembly_id` via `disc_patterns.ad_element_mep`/`ad_element_mep_alias`
  (ifc_class→element_type), then keep ONLY where that element_type is an EXACT `assembly_id` in `ad_assembly_connector`
  AND the `(disc, ifc_class)` is one the rules DB actually walks (`rule_placement` ∪ `rule_routing` endpoints).
- DECISIVE-ONLY (non-invent): pick the SERVICE connector (`connects_to` non-null) per assembly; read face/connector_type/
  Ø/connects_to VERBATIM; standoff = matching `ad_assembly_manifest.clearance_m` (0 if none = flush). No mapping → NO row
  (honest skip; `connectorEnrich` leaves the fixture untouched). Expected: terminal_rules = FP/IfcFireSuppressionTerminal→
  SPRINKLER (TOP/SUPPLY_IN/Ø25→FP_MAIN, standoff 0) [+ ELEC/IfcLightFixture→LIGHT if it walks]; duplex_rules = 0 (generic
  IfcFlow* have no assembly mapping — correct, not a gap).
- Schema: `rule_connector(disc, ifc_class, assembly_id, face, connector_type, diameter_mm, connects_to, standoff_m, provenance)`.
  Idempotent + ISOLATED (DROP+CREATE rule_connector only — no bake drift). Wire into both bake scripts.

**Engine (`disc_walker.js`).** Add `_loadConnectors(disc)` (borrow-aware `_dbFor`, table-absent-safe → []). `connectorEnrich`:
if `opts.connectors` passed → existing caller path UNCHANGED (W-ASSEMBLE-CONNECT byte-identical). Else load `rule_connector`
for the disc, key by `ifc_class` → `{face,faceDir:_faceDir(face),dia_mm,connects_to,standoff_m}`, enrich each matching part/
placement (set `.connector`, `.posStood = pos + faceDir·standoff`); unmapped untouched, count preserved.

**Witness (`scripts/witness_rule_connector.js`, W-RULE-CONNECTOR):**
- RC0 PROJECTED — terminal_rules.rule_connector has FP/IfcFireSuppressionTerminal→SPRINKLER (TOP/Ø25/FP_MAIN), provenance
  `projected:ad_assembly_connector%`; duplex_rules has 0 rows (honest).
- RC1 TRACEABLE — each projected row's face/Ø/connects_to == the verbatim ad_assembly_connector row (no fabrication).
- RC2 SELF-CONTAINED == CALLER — `connectorEnrich(sprinklers)` with NO opts (projected path) gives the SAME enriched
  connector (face/dia/connects_to/standoff/posStood) as the caller-passed path on the live SC FP walk. (proves the port
  needs no caller percept.)
- RC3 NON-INVENT-SKIP — a disc/class with no projected row (e.g. duplex ELEC) → 0 enriched, untouched.
- REGRESSION: W-ASSEMBLE-CONNECT 6, W-ASSEMBLE 10, §DWG 49, §DXG 12 stay green.

**Render (modeller.html).** New `_renderDiscAssembly(disc, parts)`: group parts by ifc_class → `_dwPrimGeo(prim,bx,by,bz)`
box instance at each part.pos (oriented by part.dir); then connector edges = a short LineSegments from each enriched
fixture along `connector.faceDir` (length = standoff or a fixed stub) tinted by service. Called after the chain render in
the walk flow when `assemble` returns parts. §-log `§DW-ASSEMBLE disc parts=N joints=M connectors=K`.

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

## §FACE-SURFACE — route-to-FACE refined with measured cross-section (W-FACE-SURFACE, 2026-06-30)
**Thread (b), user-picked.** The ACMV duct-routing precision (centre 0.269 / M7 face-by-line 0.332 @0.15m) reads as
"ducts are genuinely harder than pipes." The PROBE (`scratchpad/probe_face.js` + `probe_discrim.js`) shows that is
SUBSTANTIALLY a SCORING ARTIFACT: the touch oracle measures node-CENTRE → run-LINE, which over-states the gap for BULKY
elements by ~(node half-section + run half-section). Subtracting both MEASURED perpendicular half-extents (the real
surfaces, non-invent) → ACMV nearest-run touch 0.518→0.996; thin PLB invariant (0.998→0.999). This is a CORRECTION of a
known bias, NOT goalpost-moving — proven by two falsifiers:
- **PLB-INVARIANCE** (bulk-proportional): thin pipes (half-section ~0.01m) barely move; bulky ducts (half-section
  ~0.14m) lift a lot. If it were free leniency, PLB would jump too. It doesn't.
- **RANK-DISCRIMINATION** (still rejects wrong pairs): surface-touch nearest 0.996 · 2nd 0.698 (a duct FITTING is a
  junction — physically joins ≥2 ducts, so 2nd touching is correct topology) · 5th 0.014 · FARTHEST 0.000.

**NON-INVENT boundary.** Perpendicular half-extent = mean of the TWO bbox half-extents perpendicular to the run's
dominant AABB axis — MEASURED, never a constant. Clamp surface gap ≥0 (overlap = touch). A zero-bbox element → no
subtraction → centre fallback (no fabrication). Pairing (which run is nearest, by centre-line) is UNCHANGED — only the
reported/scored GAP changes; so guids + the centre `gap` field + M7 precision are invariant (regression-safe).

**ENGINE (`disc_walker.js`, additive).**
1. `_segLine(s)` also returns `ax` (the dominant-axis index it already computes).
2. New `_perpHalf(bx,by,bz,ax)` = mean of the two perpendicular half-extents.
3. `_nnPassFace(nodes, runs, bound)` — `nodes` loaded WITH bbox (caller passes `_loadXYZB`); each pair carries
   `gapSurface = max(0, gap − _perpHalf(run,ax) − _perpHalf(node,ax))`.
4. `routeChains` toFace branch: `nodes = _loadXYZB(...)`; each face-mode seg gets `gapSurface` (centre `gap` unchanged).

**WITNESS (`scripts/witness_route_face_surface.js`, W-FACE-SURFACE).** Own surface-oracle (mirror of the centre touch
oracle, subtracting measured perp half-extents). Substrates: Terminal (ACMV DuctSegment↔DuctFitting + PLB
PipeFitting↔PipeSegment) + Duplex (PLB FlowFitting↔FlowSegment).
- **FS1 LIFT (ACMV)** — surface nearest-touch ≫ centre (≥0.95 vs ~0.52); surface-scored precision on the FACE-walked
  pairs ≫ centre-scored.
- **FS2 PLB-INVARIANT (falsifier)** — PLB surface-touch − centre-touch < 0.02 on BOTH Terminal & Duplex (thin → no lift).
- **FS3 DISCRIMINATION (falsifier)** — farthest-run surface-touch ≈0 (≤0.02) and 5th-nearest low (<0.1); nearest high.
- **FS4 NON-INVENT** — every subtracted half-extent == independently re-measured bbox/2 (0 tol); a zeroed-bbox element
  falls back to centre (gapSurface==gap), no fabrication.
- **FS5 ENGINE-CARRIES** — `routeChains('ACMV',TE,{toFace:true})` segs carry `gapSurface` == independently recomputed
  surface gap; the centre `gap` field == plain `{toFace:true}` today (pairing/guids byte-identical).
- **REGRESSION** — W-WALKBACK-MEP 8/8 (incl M7) unchanged; §DWG 49 / §DXG 12 / nnchain 6 green.

**ACCEPTANCE.** FS1–FS5 green + regression 0-FAIL. Report BOTH numbers (centre = pessimistic-for-bulky, surface =
faithful); the lift is the honest correction, the two falsifiers are why it is not faked. Bim-compiler witness; the
engine `gapSurface` field is additive and opt-in (live dwWalk unchanged) → no deploy required for the finding (a later
slice can render faithful gaps / feed assemble).

---

## 🚨 2026-07-09 — TWO NEW CONFIRMED BUGS (hostBind SIDE-mount + render rotation-drop), guide screenshots RETRACTED

**Context: this was found by accident, not by design.** A guide-screenshot session (bim-compiler
`prompts/RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md`) added a "Walk ALL Disciplines" section + recaptured
`seedtrunk-trunk.png` showing a real routed ELEC trunk. The user looked at the deployed screenshot and said
**"MEP seem to be outside the building"** — a plain visual read, not a witness failure, is what caught this.
That itself is the headline finding: **every existing disc-walker witness in this file (§DWG/§DXG/W-DWWALK-HOSTBIND/
W-HOSTBIND-AGNOSTIC/W-ELEC-HOSTBIND/W-SHIM-SELECT/W-WALKBACK-MEP — dozens of green runs, on Duplex, the walker's OWN
mining source) never once asserted "is this point inside the building." Self-consistency on the mining source is the
EASIEST case there is, and it still slipped through — same failure shape as the LOD400 box-fallback saga
([[project_modeller_lod400_real_geometry]], 32 witnesses, 0 flagged mesh-shape) and
[[feedback_test_real_user_path_not_seams]]: the suite asserts something real but NARROWER than the property that
actually matters. **User's framing, keep it verbatim for the next session:** *"thus that is the source of truth, no
longer need visual which clearly fails and ongoing blindness is expected"* — the fix's acceptance gate must be a
NUMERIC containment/rotation assertion, not eyeballing a render. See §NEXT below for the concrete sandbox to add it to.

### Bug A — `hostBind()` SIDE-mount wall "thickness" is world-AABB, rotation-unaware (`disc_walker.js:368-399`)
```js
var horiz = w.bx >= w.by_ ? 0 : 1;                       // dominant horizontal axis = host run
var hlen = (horiz === 0 ? w.bx : w.by_) / 2, thick = (horiz === 0 ? w.by_ : w.bx);
...
var faceOff = bl.thick / 2 + off;
var fx = bpt[0] + (perpx / pl) * faceOff, fy = bpt[1] + (perpy / pl) * faceOff;
```
Picks "thickness" by comparing the host wall's **world-space** `bx`/`by_` extents. For a wall that is NOT axis-aligned
with global X/Y, its world AABB does not equal its true local thickness (a rotated rectangle's AABB captures a
diagonal extent) — `thick` can come out many times too large, and the SIDE push (`centreline + thick/2 + offset`)
shoves the fixture correspondingly far outside the real wall face.

**Measured (not guessed), fresh probe against `~/bim-ootb` main, live Duplex, real `discWalk('ELEC', {building:'Duplex'})`:**
- Real ARC envelope (253 real elements): x∈[-0.24, 9.04], y∈[-22.18, 4.38].
- 65/267 (24%) of walked ELEC fixtures land outside that envelope (0.5m margin) via exactly the
  `prov:"shim:host-IfcWall-side"` path — e.g. x=-4.59 (4.35m past the west wall), x=10.57 (1.53m past the east wall),
  y=-24.455/-23.73/-22.995 (past the south end). 0 land above the roof or below ground (z is untouched by this bug —
  it's purely an XY/plan problem). `ifc_class` breakdown: `IfcFlowTerminal` 58, `IfcFlowController` 7 — ordinary
  outlets/valves, nothing exotic.
- **No containment gate exists anywhere in `hostBind()` to catch this.** The only guard is `best > reach` (refuse if
  no host wall within `reach_m`, default 6m) — there is nothing checking the COMPUTED result afterward. A general
  `_envelopeClash()` helper already exists in this same file (line ~693) but is wired ONLY into route clash-skipping
  (`routewalker`-style chain building), never into `hostBind`'s fixture output.
- **⚠ Checked before writing this up (don't re-derive): this is NOT the same gap as the `D-ENVELOPE` witness above.**
  `build/witness_disc_walk_density.js`'s `D-ENVELOPE` check IS built and DOES run — but it explicitly filters
  `placed.filter(p => p.prov === 'placed:array-density')` (the array/density-tiling branch this file's original
  "THE FIX" section targeted). `hostBind`'s SIDE-mount output carries `prov: 'shim:host-IfcWall-side'` — a
  structurally different tag, excluded from that filter entirely. D-ENVELOPE has never once looked at a host-bound
  fixture. So this is a genuinely uncovered adjacent gap, not a regression of something already checked — verified
  by reading `witness_disc_walk_density.js`'s actual filter, not assumed from this doc's prose.

**Java precedent (checked, per user direction — "reference old Java supposedly resolved... see where is the gap"):**
dispatched a research agent into `/home/red1/bim-compiler`'s Java sources. Findings (verify file:line yourself before
trusting — this is a summary, not a re-read):
- `DAGCompiler/src/main/java/com/bim/compiler/builder/WallSpec.java` (~32-91): the FORWARD/authoring path never has
  this bug — walls are stored as explicit `start`/`end` centerline points + a `thickness` enum, and
  `toBoundingBox()`'s perpendicular offset comes from the wall's own true direction vector (`perpX=-dir.y(),
  perpY=dir.x()`), never a world-AABB comparison.
- **BUT** `WallSpec.extractFrom(...)` (~100-153) — the REVERSE-extraction method (closest Java analogue to what
  `disc_walker.js` does, walking already-built/scanned geometry) — has the **identical trap**: `if (w > d) ... else
  ...` off the bbox width/depth. Java just never exercises this path on a rotated wall in practice (its walls are
  always authored with explicit centerlines). This raises confidence the root-cause hypothesis is right (a
  recognized failure mode of this exact pattern), not just a one-off guess.
- Java DOES have a containment gate this JS walker lacks: `BIMEyes/src/main/java/com/bim/eyes/proof/tier2/
  FixtureOnSurfaceProof.java` (proof id `P09_FIXTURE_ON_SURFACE`) checks every wall-hosted fixture's gap to its host
  wall's centerline against `EyesConstants.CONTAINMENT_TOLERANCE_M = 0.050` (50mm) and flags a violation — this is
  exactly the missing check. `OpeningPlacementValidator` (Java) does the analogous thing for openings-in-walls.
  `BIMEyes/.../proof/RelationalData.java`'s `WallFaceData` stores wall orientation as an explicit flag/centerline
  rather than inferring it from a bbox — the safest of the patterns found, worth copying the SHAPE of (not
  necessarily the Java code itself).

### Bug B — `_renderDiscWalk()` never applies the computed yaw (`modeller.html:3605-3611`, this is the "long boxes /
rotation not conveyed" the user spotted independently, unprompted, from the same screenshot)
```js
function _mesh(sub, mat) {
  if (!sub.length) return;
  var im = new THREE.InstancedMesh(geo, mat, sub.length);
  sub.forEach(function (p, i) { m.makeTranslation(p.x, p.y, p.z); im.setMatrixAt(i, m); });   // ← TRANSLATION ONLY
  ...
```
`disc_walker.js` computes and stores a real per-placement `yaw` (e.g. `bl.horiz === 0 ? 0 : Math.PI/2` in `hostBind`,
or a rule-derived value elsewhere) — but the renderer that draws every walked fixture ignores it completely. Every
walked fixture box renders axis-aligned in world space regardless of its intended orientation along its host
wall/run. **This is not a mid-animation artifact or a screenshot fluke** — `_renderDiscWalk` is the function called
on every fold (`_redrawAllDiscWalks`, called from the main oplog-fold path, not a one-off preview), so this is what
ships permanently, not just what's visible for a moment after walking.

The correct pattern already exists elsewhere in the SAME file, just not applied here — `_renderDiscAssembly()` (a
sibling function, ~3772-3808, for the "assemble catalog parts at routed nodes" feature) properly does
`q.setFromUnitVectors(up, dir); m.compose(pos, q, sc)`. Also, when a walked placement is later promoted to a real
committed feature, `rot: p.yaw` IS threaded through correctly (line ~3849) — so `p.yaw` is good data, it's just
dropped specifically in this one preview/fold render path.

**Dimension sanity check (rules out one theory):** probed `window.__dwWalks` box dims across ALL 4 disciplines after
a real `discWalkAll` on Duplex — every class's `bx/by/bz` is small and sane (0.03–0.15m, e.g. `IfcFlowTerminal`
0.070×0.070×0.114, `IfcFireSuppressionTerminal` 0.032×0.027×0.058) — **the "long pillar" look in the screenshot is
NOT an oversized-box data bug**, it reads that way because ~500 tiny unrotated boxes densely cluster/stack without
any orientation cue, not because any single box is actually large. Don't waste time chasing a scale bug — there
isn't one; it's Bug A (mis-placement) + Bug B (no rotation) compounding visually.

### SampleCastle "rooms seem empty of any DISC" (user observation, distinct from Bugs A/B — a coverage/integration
gap, not a placement-math bug)
Confirmed: `modeller/SampleCastle_extracted.db` (the modeller's own ARC-only copy) has **zero** `IfcSpace`, **zero**
`IfcSanitaryTerminal`/`IfcFlow*` rows — pure architecture (walls/slabs/windows/doors/railings/stairs/coverings) only,
as expected for an "ARC-only resident." Two PRE-EXISTING, already-documented facts explain why this isn't a new
finding, just newly-felt:
- `RESUME_MEP_SAMPLECASTLE.md` (2026-06-22, Viewer-side lane, different app/code path — `viewer/routewalker.js`) already
  recorded **SampleCastle has NO baked `building_room` sidecar** — the room-based MEP fixture-placement demo (bathroom/
  kitchen wet-recipe rooms) has been blocked on this since that date, never resolved.
- `disc_walker.js` (the MODELLER's own walker, this file's subject) has **zero** references to `IfcSpace`, `geomap`,
  or any room/space table anywhere — grepped clean. `hostBind()`/`place()` are pure "nearest wall of a matching class
  within `reach_m`" — there is no room-TYPE concept at all, so there is no way today to specifically target "put a
  sink in the restroom." This was never wired in, on any building, not just SampleCastle.
- **But the capability to detect rooms already EXISTS and is SHIPPED, just never connected**: [[project_ifc_bom_geomapping]]
  (memory; LANE CONCLUDED 2026-07-02) found `IfcRelSpaceBoundary` really exists in SampleCastle's own source IFC (1675
  relations) and `tools/rooms_from_topology.py`/`rooms_from_boundaries.py` already solve room-polygon recovery (13/21
  IoU≥0.5 on Duplex via topology alone, 21/21 via the sidecar rung). **Nobody has ever wired that room output into
  `disc_walker.js`'s placement decisions.** This is a real, distinct integration gap — worth a session of its own,
  separate from Bugs A/B, and should NOT be conflated with them (don't try to "fix rooms" as part of fixing the
  rotation/containment bugs — different code, different owners, different specs).

### What was done about it THIS session (guide-scope only — no disc_walker.js/modeller.html code touched)
Per this project's "fail hard, do not embed it" precedent (established 2026-07-01 for the SampleCastle tilt saga,
same card) and explicit user instruction ("beware of bandaid fix not based on well worked out specs"): **no code fix
was attempted.** Only the guide was touched:
- `bim-compiler` `65605fd9c` added a "Walk ALL Disciplines" section + recaptured `seedtrunk-trunk.png` (both showcased
  the bugs above, unknowingly, at capture time).
- `ef0cd7d6a` + `346d5356d` retracted both: removed the Walk-All-Disciplines subsection + image, reverted
  `seedtrunk-trunk.png`/caption to the prior honest "pre-route, 0 routed" state. Redeployed via
  `scripts/safe_gh_deploy.sh` (guard correctly caught both as shrinks/deletions; blessed by exact path via
  `ALLOW_SHRINK`, nothing else touched). Live-verified via direct URL fetch, not just deploy-exit-code.
- Grid-Stretch's hosted-fill sentence + the `gridstretch-before/after.png` chrome-consistency recapture (unrelated to
  Bugs A/B) were KEPT. Full detail: `prompts/RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md`'s 2026-07-09 section.
- The move-gizmo item (a THIRD, unrelated thread from that same guide card) was deferred to the in-progress
  SampleCastle demo-swap lane (`prompts/GUIDE_VISUAL_QUALITY.md`) — not touched here, no collision.

### §NEXT — for a fresh session, spec-first (do NOT bandaid — write the spec section before touching engine code)
1. **Reuse the existing NON-BROWSER sandbox, don't build new scaffolding.** `scripts/witness_hostbind_agnostic.js` +
   `scripts/witness_shim_select.js` (bim-compiler) already load `build/disc_walker.js` + the real `*_rules.db`/meta
   DBs directly in plain Node — no puppeteer, no screenshots, exactly the numeric/whitebox shape this needs (user:
   "no longer need visual which clearly fails"; "perhaps have a sandbox... that can work"). Add the containment +
   rotation assertions there (or a new sibling witness alongside them), not as a browser/visual check.
2. **Check Terminal too, not just Duplex** (user: "similar with Terminal") — `hostBind()`'s SIDE-mount branch is
   shared code across every discipline/building; a rotated wall in Terminal's real extraction would trigger the
   identical bug. Re-run whatever containment probe gets built against BOTH `duplex_rules`/Duplex substrate AND
   `terminal_rules`/Terminal substrate before calling it fixed.
3. **Bug A fix direction (spec it first):** replace the world-AABB `w.bx >= w.by_` comparison with a rotation-aware
   local thickness — either (a) if `element_transforms` carries a rotation column for walls, use it to get the wall's
   true local frame (mirrors Java's `WallSpec.direction()`/perpendicular-via-direction-vector pattern), or (b) derive
   the wall's run direction from its OWN long-axis via a rotated-rect fit (min-area-rect / PCA on its footprint),
   not its axis-aligned bbox. Add a `_envelopeClash()`-style containment assertion on `hostBind`'s OUTPUT (mirrors
   Java's `FixtureOnSurfaceProof`/P09 tolerance-gate design, adapted to JS) so this class of bug fails LOUD next time,
   not silently.
4. **Bug B fix direction (spec it first):** thread `p.yaw` into `_renderDiscWalk`'s `_mesh()` instance matrix —
   mirror the already-correct pattern in `_renderDiscAssembly` (`q.setFromUnitVectors`/quaternion) or the simpler
   `m.makeRotationZ(p.yaw)` composed before translation, whichever matches the yaw convention `hostBind`/`place`
   actually use (yaw is currently always 0 or π/2 — confirm nothing downstream assumes translation-only matrices
   before changing this, e.g. `_dwPrimGeo`/instance-pick code that reads `im.getMatrixAt`).
5. **Room-awareness (SampleCastle empty-rooms) is a SEPARATE follow-on, not part of 3/4 above** — spec it
   independently: wire `tools/rooms_from_topology.py`'s (or the sidecar rung's) room-polygon output into
   `disc_walker.js`'s host selection so a fixture can be room-TYPE-aware, not just nearest-wall-aware. Needs its own
   acceptance criteria (e.g. "every detected restroom-type room gets ≥1 sink candidate") — don't fold it into the
   Bug A/B fix's acceptance gate, they're independent claims.
6. Only AFTER 3/4 pass their own numeric witnesses (not before) should the guide screenshots be recaptured — and even
   then, re-apply the SAME "open the PNG, eyeball it, don't trust witness-green alone" discipline from
   `RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md`'s §ORIGINAL CARD "THE LESSON" section, since a passing containment/
   rotation witness proves those TWO properties, not "looks right" in general.

---

## §ROTATION-BOUND — Bug A/B fix, spec (2026-07-09, session continuing same day)

**Java precedent independently re-verified (not re-trusted from the prior summary — read file:line myself):**
- `DAGCompiler/src/main/java/com/bim/compiler/builder/WallSpec.java:63-91` (`toBoundingBox()`) — the FORWARD path is
  clean: `perpX=-dir.y(), perpY=dir.x()` off a real unit direction vector, never an axis-aligned bbox comparison.
- `WallSpec.java:100-134` (`extractFrom()`, the reverse-extraction analogue of what `disc_walker.js` does) — confirmed
  it has the IDENTICAL `w > d` bbox-width-vs-depth branch disc_walker.js has, with no rotation term anywhere in the
  method. Java just never exercises it on a rotated wall (its walls are always authored with explicit centerlines) —
  disc_walker.js DOES exercise it, on real rotated Terminal/Duplex walls (below), so the failure mode is live, not
  theoretical.
- `BIMEyes/src/main/java/com/bim/eyes/proof/tier2/FixtureOnSurfaceProof.java` + `EyesConstants.CONTAINMENT_TOLERANCE_M
  = 0.050` — confirmed real, exactly as the prior write-up said (gap-to-host-wall gate, 50mm tolerance).
- **Correction to the prior write-up:** `_envelopeClash()` does **not** exist at `disc_walker.js:~693` — that line is
  `connectorFor()` (the FIXTURE→SERVICE hookup resolver, roadmap #3b, unrelated). Grepped the whole file for
  "envelope"/"Envelope": no such helper exists anywhere in `disc_walker.js` today. There is no pre-existing
  containment check to wire in — the witness below builds one fresh (mirroring `FixtureOnSurfaceProof`'s gap/tolerance
  shape, not reusing nonexistent JS code).

**⚠ CORRECTION (same session, before any code was written) — the first pass below was wrong about WHAT `bbox_x/bbox_y`
ARE, caught by going back to the actual extractor source + a raw-IFC cross-check instead of trusting a plausible-sounding
reading of the DB numbers. Recorded here rather than silently rewritten, per this project's "no-invented-rules"
discipline — the wrong turn is part of the proof trail:**
- My first hypothesis (below, struck through in spirit not text) was "`bbox_x/bbox_y` are the wall's LOCAL/object-frame
  extents." **This is false.** Ground truth, read straight from the extractor
  (`DAGCompiler/python/extractIFCtoDB.py:1191-1220`): `rot3` = the element's full WORLD rotation matrix (from
  IfcOpenShell's own composed 4×4 transform — the whole IFC placement chain is ALREADY resolved by the time this code
  runs); `rot_z = atan2(rot3[1,0], rot3[0,0])` = the real WORLD Euler-Z angle. Separately, `world_corners = (rot3 @
  local_corners.T).T + center` and `bbox_x/y/z = maxXYZ - minXYZ` off THOSE — i.e. **`bbox_x/bbox_y` are the WORLD-space
  AABB extents of the ALREADY-ROTATED element**, not its pre-rotation local dimensions. (The already-established prior
  art `scripts/test_orientation_proof.py`'s **P2 BBOX_RECONSTRUCT** proof — `R(rotation_x/y/z) @ local_mesh_bbox_corners
  + centre ≈ R-tree world bbox`, already tested against real IFC — confirms this same convention independently; that
  script is the "old spec that had success before" this session's methodology should have started from, not reasoning
  about the DB columns cold.)
- **Independently re-verified against the raw IFC source itself** (not code, not a summary — the actual
  `internal/sources/Ifc2x3_Duplex_Architecture.ifc`), for wall guid `2O2Fr$t4X7Zf8NOew3FNqI`
  (`element_transforms`: `bbox_x=0.417, bbox_y=17.383, rotation_z=-1.5708, center=(8.5915,-0.417)`): traced
  `#3999 IFCWALLSTANDARDCASE → #3986 IFCLOCALPLACEMENT → #3985 IFCAXIS2PLACEMENT3D(location=#3984=(8.5915,-0.417,0),
  axis=(0,0,1), refDirection=(0,-1,0))` — the wall's LOCAL +X axis really does point along WORLD **−Y**, matching
  `rotation_z=-π/2` exactly (`cos(-90°),sin(-90°) = 0,-1`). The swept-solid profile
  (`#3992 IFCRECTANGLEPROFILEDEF` off axis `#3988 IFCPOLYLINE((0,0),(17.383,0))`) gives the TRUE local length=17.383,
  thickness=0.417 — rotating that local rectangle by −90° and taking the world AABB gives world bbox_x=0.417,
  bbox_y=17.383 — **exactly the stored DB row.** Both independent sources (extractor code + raw IFC) agree.
- **Why the fix is still simple (not the general AABB-inversion math this would otherwise require):** recovering local
  length/thickness from a world AABB + rotation in general needs solving `Wx=|Lx cosθ|+|Ly sinθ|`, `Wy=|Lx
  sinθ|+|Ly cosθ|` for `(Lx,Ly)` — a real inversion, and NOT something to invent machinery for on zero evidence it's
  ever needed. It isn't: **every measured `rotation_z` in every extracted-building DB available (Terminal 333 walls,
  Duplex 57 walls, both independently queried) is an EXACT cardinal multiple of π/2** (`{0, ±1.571, 3.142}`, no
  in-between value observed anywhere). At a cardinal angle the general system above degenerates exactly (no
  approximation): θ=0 or π → `Wx=Lx, Wy=Ly` (no swap); θ=±π/2 → `Wx=Ly, Wy=Lx` (EXACT swap). So **the correct, fully
  general reconstruction and "just swap bbox_x/bbox_y on an odd quarter-turn" are mathematically IDENTICAL for every
  real host in this project's substrate** — this is not a narrower hack standing in for the real thing, it's the exact
  same formula evaluated at the only angles that occur. This also lines up with the BOM tack convention's own
  vocabulary (`m_bom_line.rotation_rule ∈ {EW, NS, POINT}`, `docs/archive/BOMBasedCompilation.md` §4) — real building
  walls in this project's data are architecturally cardinal (East-West/North-South runs), not diagonal; the fix reads
  in the SAME discrete language the rest of the BOM system already uses, rather than introducing continuous
  trigonometry the codebase doesn't otherwise speak. A non-cardinal rotation (if one is ever measured in a future
  building) is explicitly REFUSED/logged (`§DW-NONCARDINAL`), never silently run through the cardinal-only swap.

**Root cause, sharpened (verified against real rotation_z values, not inferred):**
`hostBind()`'s SIDE-mount branch (`build/disc_walker.js:359-364`) builds each host wall's centerline by comparing raw
`w.bx`/`w.by_` (whichever is bigger = "the run axis") and then writing that magnitude straight into a WORLD-space `a`/`b`
pair (`a[horiz] -= hlen`) — i.e. it silently assumes the wall's WORLD AABB extents already tell you which WORLD axis is
the run vs the thickness. That is only true when the wall's rotation is 0/π; at ±π/2 the world AABB's `bx`/`by_` are
the wall's TRUE length/thickness **with the world axis assignment inverted** (proven above) — so the code doesn't just
mis-size the push, for a ±π/2 wall it silently transposes which axis (X or Y) the centerline even runs along. Confirmed
by direct inspection of two independent real building DBs:
- `deploy/buildings/Terminal_extracted.db` (**in this repo**, already used by the existing regression witnesses —
  `witness_hostbind_agnostic.js` et al.) has walls at `rotation_z ∈ {0, ±1.571(π/2), 3.142(π)}` — the bug substrate has
  been sitting in the committed test fixtures the whole time; nobody added a containment assertion to notice.
- `~/bim-ootb/modeller/Duplex_extracted.db` (the live/deployed copy, read-only reference — never edit that shared tree
  per this project's worktree hook) also has real `rotation_z ∈ {0, ±π/2, π}` on 57 walls — this is the exact DB the
  2026-07-09 "65/267 ELEC fixtures land outside envelope" measurement above was taken against.
- By contrast, **this repo's own committed** `deploy/buildings/{Duplex,SampleHouse,SampleCastle}_extracted.db` and
  `build/Duplex_mep_extracted.db` all show `rotation_z=0` for every wall — a DIFFERENT (older/flattened) extraction
  than what SHIPS. This is WHY the dozens of green regression witnesses in this file never caught Bug A: their own
  Duplex/SH/SC fixtures never contain a rotated wall, so the bug is invisible on them by construction, even though
  Terminal — right there in the same `deploy/buildings/` folder — already had the triggering data.
- **What does NOT need fixing:** the face-push math AFTER the centerline (`perpx = p.x - bpt[0], perpy = p.y -
  bpt[1]` at `disc_walker.js:378`) already derives its push direction at RUNTIME from the vector toward the floating
  point — it is orientation-agnostic already. Only the `lines` construction (`disc_walker.js:359-364`, the a/b
  centerline + `thick` + implicit direction) and the two `yaw` sites derived from it (`:384`, `:408/410`) are wrong.

**Fix — ONE shared helper, cardinal-swap on the WORLD-AABB extents (additive, byte-identical at `rotation_z∈{0,π}` —
regression-safe by construction; matches the exact-inversion math proven above, not an approximation):**
1. Add `t.rotation_z rot` to the `hosts` SELECT (one column, shared by both the SIDE and TOP/BOTTOM/CENTER branches).
2. New shared helper (used by BOTH branches — one function, not per-branch duplicated logic):
   ```js
   var CARDINAL_TOL = 0.01;                                  // rad (~0.6°) — measured data is exact multiples of π/2
   // Cardinal-snap the host's measured world rotation_z to a quarter-turn count. Every real wall measured in this
   // project (Terminal 333, Duplex 57) is EXACTLY 0/±π/2/π — this is the exact AABB-inversion solution at those
   // angles (Wx=Ly,Wy=Lx at odd quarter-turns; Wx=Lx,Wy=Ly at even), not a narrowed approximation of it. A
   // non-cardinal rotation_z (none measured yet) is honestly refused, never run through the swap.
   function _hostAxis(w) {
     var rot = w.rot || 0, q = rot / (Math.PI / 2), k = Math.round(q);
     if (Math.abs(q - k) > CARDINAL_TOL / (Math.PI / 2)) return null;      // non-cardinal → caller falls back/refuses
     var odd = (((k % 2) + 2) % 2) === 1;                                  // odd quarter-turn ⇒ world X/Y axes swapped
     var lenIsX = odd ? (w.by_ >= w.bx) : (w.bx >= w.by_);                 // WORLD-AABB axis that carries the run
     return { lenIsX: lenIsX, yaw: lenIsX ? 0 : Math.PI / 2, nonCardinal: false };
   }
   ```
3. SIDE branch `lines` construction (`disc_walker.js:359-364`) — replace `var horiz = w.bx >= w.by_ ? 0 : 1;` with:
   ```js
   var hostAxis = _hostAxis(w);
   if (!hostAxis) { console.log(TAG + ' §DW-NONCARDINAL host=' + w.g + ' rot=' + w.rot + ' (unmeasured case, falling back to raw bbox axis)'); }
   var horiz = hostAxis ? (hostAxis.lenIsX ? 0 : 1) : (w.bx >= w.by_ ? 0 : 1);   // fallback = today's behaviour, never a crash
   ```
   Everything downstream (`hlen`, `thick`, `a`/`b` construction) is UNCHANGED — only which WORLD AABB field (`bx` vs
   `by_`) is treated as "the run axis" changes, and only when the wall is cardinally rotated by an ODD quarter-turn.
   At `rot=0` or `π` (even quarter-turns, incl. the `k=0` today-default): `hostAxis.lenIsX === (w.bx>=w.by_)` — IDENTICAL
   to today, byte-for-byte, no swap. At `rot=±π/2` (the actually-wrong case): `lenIsX` flips — this is the fix.
4. `yaw: bl.horiz === 0 ? 0 : Math.PI / 2` (line 384) — no change needed; `bl.horiz` already carries the corrected
   axis once step 3 lands, so the existing yaw expression is automatically right.
5. TOP/BOTTOM/CENTER branch (`:408`) — `var horiz = bh.bx >= bh.by_ ? 0 : 1;` gets the SAME `_hostAxis(bh)` swap
   applied (position is unaffected there either way — nearest-XY binding, not a projected line — only yaw was
   naively axis-guessed; this makes yaw correct for cardinally-rotated hosts in this branch too).
6. `thick`/`hlen` magnitudes are UNCHANGED by this fix in either branch — swapping which WORLD-AABB field is "length"
   vs "thickness" doesn't invent a new number, it picks between two ALREADY-MEASURED extents; the fix is which axis
   label attaches to which existing number, never a new value.

**Witness plan (extends the existing non-browser sandbox pattern, per §NEXT item 1 — no new scaffolding, no puppeteer
— AND mirrors `scripts/test_orientation_proof.py`'s already-proven **P2 BBOX_RECONSTRUCT** methodology: reconstruct
from measured spatial fields, compare against an independent ground truth, never eyeball it):**
New `scripts/witness_hostbind_rotation.js`, same shape as `witness_hostbind_agnostic.js`/`witness_shim_select.js`
(plain Node, `sql.js`, loads `build/disc_walker.js` + real DBs directly):
- **R0 SUBSTRATE** — confirm (log, don't assert-fail) which walls in each target DB carry non-zero `rotation_z`, so
  the run visibly exercises the bug path, not just axis-aligned walls; log the cardinal-quarter-turn histogram
  (expect 100% within `CARDINAL_TOL` of a multiple of π/2 on both Terminal and Duplex — the substrate claim, checked
  every run, not assumed once).
- **R1 CONTAINMENT (the acceptance gate user asked for — numeric, not visual)** — compute the building's real ARC
  envelope (min/max x/y over ALL real elements + a fixed margin, mirroring the "253 real elements, x∈[-0.24,9.04]"
  measurement already taken) on BOTH substrates: Terminal (`deploy/buildings/Terminal_extracted.db`, already in this
  repo) and Duplex (read `~/bim-ootb/modeller/Duplex_extracted.db` directly, read-only — same pattern this file
  already uses for `~/bim-ootb/modeller/Terminal_meta.db` elsewhere). Assert: after the fix, 0 SIDE-mount-bound
  placements fall outside the envelope+margin (today: 65/267 on live Duplex — reproduce that count with the OLD
  algorithm inline as the falsifier, then show 0 with the NEW one).
- **R2 YAW-SANITY** — `yaw` stays a member of `{0, π/2}` (the cardinal-only substrate never needs a 3rd value), but
  now correctly reflects the host's TRUE world run-axis (via `_hostAxis`, independently re-derived per host — not the
  naive `bx>=by` guess); for every ±π/2-rotated host, assert `yaw` DIFFERS from what the pre-fix naive guess would
  have produced (falsifies "the fix is a no-op").
- **R3 REGRESSION, BYTE-IDENTICAL ON AXIS-ALIGNED SUBSTRATE** — re-run `witness_hostbind_agnostic.js` (6/6),
  `witness_shim_select.js` (S0-S5), `witness_elec_hostbind.js` (5/5), §DWG (49), §DXG (12), `witness_walkback_mep.js`
  (8/8) unchanged 0-FAIL against the COMMITTED (rotation_z=0) fixtures — proves the fix is additive, not a rewrite.
- **R4 NON-INVENT, RAW-IFC CROSS-CHECK** — for the specific probed wall (`2O2Fr$t4X7Zf8NOew3FNqI`, Duplex), assert the
  fixed `horiz`/`thick` reproduce the values independently derived straight from
  `internal/sources/Ifc2x3_Duplex_Architecture.ifc`'s own placement+profile (length=17.383 along world Y, thickness
  =0.417 along world X — see the raw-IFC trace above) — not just "the code changed," an actual source-grounded number
  match. `thick`/`hlen` magnitudes are re-confirmed == the wall's own `bbox_x`/`bbox_y` (whichever the corrected axis
  picks) — the fix changes axis LABEL only, never fabricates a size.

**Bug B fix (separate, render-side, lives in `~/bim-ootb/modeller/modeller.html` — needs a `/tmp/wt-*` worktree,
never the shared tree):** thread `p.yaw` into `_renderDiscWalk`'s `_mesh()` instance matrix. The sibling function
`_renderDiscAssembly` (same file, ~3772-3808) already does this correctly via `q.setFromUnitVectors(up, dir);
m.compose(pos, q, sc)` — mirror that pattern (or the simpler `m.makeRotationZ(p.yaw)` composed before translation) in
`_mesh()`. Confirm nothing reading `im.getMatrixAt` downstream assumes a translation-only matrix before changing this.
This is where Bug A's now-correct (non-0/π/2) `yaw` values actually become visible — Bug A alone fixes POSITION, Bug B
is required for the box's ORIENTATION to render correctly.

**Sequencing:** Bug A fix + R0-R4 witness in bim-compiler FIRST (self-contained, no deploy). Only after R1-R4 green,
port the engine fix to `~/bim-ootb/modeller/disc_walker.js` (worktree) alongside the Bug B render fix (one PR, per
this project's "engine+render together, never source-only" deploy discipline) — sw bump, live-verify both the
containment (no fixture visibly outside envelope) and the rotation (boxes visibly aligned along their host wall).

---

## 🛑 §ROTATION-BOUND — THE CARDINAL-SWAP FIX ABOVE IS WRONG, DISPROVEN BY ITS OWN WITNESS (same session,
before any deploy — reverted, not shipped)

**What happened:** implemented the `_hostAxis()` cardinal-swap fix exactly as spec'd above, wrote
`scripts/witness_hostbind_rotation.js` to prove it (R0-R4), and ran it against real data (Terminal in-repo +
live Duplex). **The witness FAILED the fix**: NEW put MORE placements outside the envelope than OLD on both
substrates (Terminal: OLD 0/2890 outside → NEW 27/2881; Duplex: OLD 0/267 → NEW 11/242), and R4's raw-IFC
cross-check also failed (`_hostAxis` returned `lenIsX=true` for the probe wall; the ground truth traced from the
raw IFC placement says `lenIsX=false`). **Per this project's own "tests expose issues" rule, a fix a witness
rejects does not ship — reverted `build/disc_walker.js` to its pre-session state (`git diff` clean, confirmed).**
This is recorded rather than quietly dropped, because the wrong turn IS the finding:

**Why the fix was wrong — the axis-comparison was never the bug.** Re-deriving by hand (not trusting the earlier
derivation): at an EXACT cardinal rotation, comparing raw WORLD-AABB `bbox_x` vs `bbox_y` and picking the bigger
one as "the run axis" is **already correct, with no swap needed** — a 90° rotation just relabels which world axis
carries the (unchanged) local length value wholesale; it doesn't create axis ambiguity. Verified directly: probe
wall `2O2Fr$t4X7Zf8NOew3FNqI` (`rotation_z=-π/2`, `bbox_x=0.417, bbox_y=17.383`) has its TRUE length (17.383, per
the raw IFC profile) on WORLD Y — and the OLD naive `bx>=by_?0:1` already picks `horiz=1` (Y) for it. **No bug
there.** The `_hostAxis` swap I built flipped a pick that was already right, which is why the witness got WORSE.

**The REAL bug, found by going one level deeper (numeric reconstruction, not reasoning) — verified three
independent ways for the same wall:**
1. **Raw IFC placement** (`internal/sources/Ifc2x3_Duplex_Architecture.ifc`): `IFCLOCALPLACEMENT`'s `Location` =
   `(8.5915, -0.417, 0)` — this is the wall's polyline-axis **START point** (`IFCPOLYLINE((0,0),(17.383,0))` runs
   FROM the local origin), not its centre.
2. **`elements_rtree`** (available in `~/bim-ootb/modeller/Duplex_extracted.db` only): real world bbox
   `x∈[8.383,8.800], y∈[-17.800,-0.417]` → true midpoint `(8.5915, -9.1085)`.
3. **`element_transforms.center_x/center_y`** for the SAME guid: `(8.5915, -0.417)` — matches the bbox midpoint in
   X (thickness axis) but is **8.69m off** the true midpoint in Y (the length axis) — it equals the Y-MAX corner,
   not the centre.
   Extractor source confirms WHY: `DAGCompiler/python/extractIFCtoDB.py:1372` inserts `center = mat4[:3,3]` — the
   raw IFC placement TRANSLATION (wherever the authoring tool put the element's local origin) — never
   `(minXYZ+maxXYZ)/2`. `bbox_x/y/z` (line 1374-1376) IS computed as `maxXYZ-minXYZ`, so the WIDTH is trustworthy;
   the **CENTRE POINT is not** — it's centred in thickness (Revit places a wall's location LINE on its centreline)
   but sits at an arbitrary point ALONG the length (wherever that wall's location-line segment starts/joins), not
   the midpoint. **Checked across 14 more real Duplex walls (not just the one probe) via `elements_rtree`:** every
   single one has ~0.00 offset from bbox-mid on the thickness axis and a NONZERO offset (0.73m to 8.69m) on the
   length axis — this is systematic, not a one-off.
   ⚠ **`deploy/buildings/Duplex_extracted.db`** (this repo's own committed fixture, rotation_z=0) stores a
   DIFFERENT value for the same guid's centre (`center_y=-9.9175`, much closer to the true bbox-mid `-9.1085`,
   though not exact) — meaning the COMMITTED regression fixtures were produced by a **different/older extraction
   computation** than the current `extractIFCtoDB.py`/live pipeline, not just "same pipeline, flattened rotation"
   as the (also now suspect) earlier note in this file assumed. This means the existing regression suite's
   Duplex/SH/SC fixtures are doubly unrepresentative of live data — both the rotation AND the centre-point
   convention differ from what ships.

**Consequence — `hostBind()`'s SIDE branch has a bigger, different defect than originally scoped:**
`a = [w.x, w.y]; a[horiz] -= hlen; b[horiz] += hlen;` assumes `(w.x, w.y)` IS the wall's midpoint. It is NOT, for
any wall whose placement-line origin isn't centred (shown to be the common case, not the exception). This
displaces the reconstructed centreline by up to `hlen` (up to the wall's own half-length — multiple metres) along
the wall's run — independent of, and much larger than, any rotation/axis question. Rotation still matters (it
determines which world coordinate carries this same error), but it is not where the fix belongs.

**Fix direction for a fresh attempt (NOT implemented — this needs a decision, see the question below):**
- The ONLY reliable source of a true bbox midpoint across the DBs checked is `elements_rtree.minX/maxX/minY/maxY`
  — but it is **only present in `~/bim-ootb/modeller/Duplex_extracted.db`** among everything probed; ABSENT from
  `deploy/buildings/{Terminal,Duplex,SampleHouse,SampleCastle}_extracted.db` (this repo's committed fixtures),
  `~/bim-ootb/buildings/Terminal_extracted.db`, and `~/bim-ootb/modeller/SampleCastle_extracted.db`. `hostBind`
  cannot uniformly rely on a table that doesn't exist in most of its real substrates.
- Options, none yet chosen: (a) re-extract every building through the CURRENT `extractIFCtoDB.py` so `elements_rtree`
  ships everywhere hostBind runs (biggest, most correct, most work — touches the extraction pipeline, not just the
  walker); (b) accept `center` as an approximation and bound the resulting error (log it, never silently trust it)
  rather than trying to reconstruct a true midpoint from data that doesn't carry it; (c) find another already-
  extracted column this session hasn't checked yet that DOES carry a true centroid uniformly (not yet searched).
  **This is a scope decision for the user, not something to keep guessing at solo** — the fix now touches the
  extraction pipeline's data contract, not just `disc_walker.js`.
- Bug B (yaw not rendered) is UNCHANGED/still real and still separate — it wasn't touched by any of the above.

**⚠ For the next session: do not re-attempt the `_hostAxis` cardinal-swap patch above — it is DISPROVEN, not
just unfinished.** `scripts/witness_hostbind_rotation.js` still exists in the repo (uncommitted at pause) and is a
working, reusable numeric harness (it correctly caught the bad fix) — reuse its `envelope()`/`outsideCount()`/
raw-IFC-cross-check machinery for whatever the real fix turns out to be; only its `R1`/`R4` PASS conditions were
built assuming the wrong theory and need new expected values once the real fix direction is chosen.

**⚠ CORRECTION to R1's own envelope (found immediately after, same session, before moving on) — the witness's
`envelope()` helper computed the "ground truth" building box from `center_x/y ± bbox/2` — **the exact same
unreliable assumption `hostBind` makes**, so it was self-contaminated: it reported an inflated box
(`x∈[-4.90,13.70], y∈[-27.20,9.64]`) wider than the real building, making the OLD (pre-fix) algorithm falsely look
clean (0/267 outside). Recomputing the SAME check off `elements_rtree.minX/maxX/minY/maxY` (the reliable,
independently-populated source, available on this one Duplex copy) gives `x∈[-0.24,9.04], y∈[-22.18,4.38]` —
**exactly matching the original 2026-07-09 claim, verbatim.** Re-ran the REAL (reverted, currently-shipping)
`DW.hostBind` against THIS envelope: **65/267 outside, breakdown `IfcFlowTerminal 58, IfcFlowController 7` —
an EXACT reproduction of the original finding, both the count and the per-class split.** So: the original 65/267
claim is CONFIRMED real and reproducible (it was never in doubt from the axis-swap misfire) — what was wrong was
only my witness's OWN envelope math, which inherited the identical "trust center as if it were a midpoint" flaw
this whole investigation is about. Lesson for the fix, sharpened further: **any validation of `hostBind`'s output
must also avoid `center±bbox/2` for its ground truth** — on Terminal (no `elements_rtree`, no `component_geometries`,
no library match) there is currently **no reliable way to even MEASURE whether a fixture is contained**, not just
no reliable way to fix its position. The scope-decision question above (re-extract vs. bound-as-approximation vs.
defer) applies equally to "how do we even grade this," not just "how do we fix it."

**Follow-up check (same session, cheap/no-risk — asked the user to pick a fix direction, got no response in 60s,
so did the low-cost option only and stopped rather than guess on the bigger one):** searched for ANY existing
column/table that already carries a true centroid, across all 7 real building DBs checked, before considering
touching the extraction pipeline:
- `deploy/buildings/{Duplex,SampleHouse,SampleCastle}_extracted.db` (+ their bim-ootb equivalents) all carry
  `component_geometries` (real per-element mesh vertex BLOBs, keyed by `geometry_hash` via `element_instances`) —
  for these three, a true world bbox/centroid CAN be recomputed locally, no re-extraction needed, mirroring
  `scripts/test_orientation_proof.py`'s exact method (`R(rotation) @ local_verts.bbox + center → world bbox`).
- `deploy/buildings/Terminal_extracted.db` (+ its bim-ootb copy) has **NEITHER** `elements_rtree` **NOR**
  `component_geometries` — and none of its 7150 distinct `geometry_hash` values (checked a 200-sample) exist in
  the shared `library/component_library.db` either. **Terminal — the building EVERY existing disc-walker
  regression witness in this file is built on — has NO locally-recoverable true centroid anywhere.** Only a
  re-extraction (or accepting `center` as an uncorrected approximation) would fix Terminal specifically.
- **✅ RESOLVED 2026-07-09 (user: "take over, advise, spec, implement")** — decision made (see §BUG-A-TRUE-MIDPOINT
  below): NOT a re-extract-everything-or-nothing choice. Used the already-committed `component_geometries` mesh
  data (100% element coverage measured on Duplex/SampleHouse/SampleCastle, needing no re-extraction) to recover a
  TRUE midpoint wherever it exists, and made the fallback EXPLICITLY self-reporting (`verified:false`, logged)
  wherever it doesn't (Terminal). Re-extracting Terminal specifically remains a named, scoped follow-on, not an
  open question.
  (don't loop on a blocked item — next session: ask again first, don't restart the investigation).

## ✅ Bug B SHIPPED 2026-07-09 (bim-ootb PR #717, branch `fix/disc-walker-yaw-render`) — Bug A still ⛔ BLOCKED above
`_renderDiscWalk`'s `_mesh()` now applies `p.yaw` (`makeRotationZ(p.yaw||0)` + `setPosition`, mirroring
`_renderDiscAssembly`'s already-correct pattern). Verified in a real headless-Chrome render (puppeteer): a live
ELEC/Duplex `hostBind` walk's rendered `InstancedMesh` matrices decompose to the exact expected yaw+position
(float-precision match) across all 4 fixture classes; placements with no `yaw` render identically to before.
`witness_dw_pixelprobe.js` shows the same pre-existing 3/3 fail as an unmodified `origin/main` baseline (checked
side-by-side) — not a regression from this change. **Not merged yet** — PR open, needs the human merge decision.

**⚠ HANDOFF NOTE (user, 2026-07-09): a separate "review session" is taking over Bug A's blocked scope decision
next, not this session.** When a fresh bim-compiler session resumes this card, its job is to **REVIEW that review
session's work — verify its claims and re-check the actual diff/witness output itself, not take a "done" report
at face value** — same discipline this session used on its OWN first (disproven) fix and on the external review
summary's one inaccurate number (§ROTATION-BOUND above, both caught by re-running the numbers, not by trusting
the narrative). Do not just continue building on top of whatever the review session produces without that check.

---

## ✅ §BUG-A-TRUE-MIDPOINT — spec + fix (2026-07-09, user directed "take over, spec it, implement")

**User's own AskUserQuestion on the 3-way fix choice (re-extract / bound-as-approximation / defer) got no
reply twice** (once from the prior session, once from me). User then said explicitly: take over, advise, spec,
implement. Re-verified every load-bearing claim myself before writing code, per the HANDOFF NOTE's own
discipline — did not build on the prior session's numbers without re-deriving them:

- Confirmed `deploy/buildings/{Duplex,SampleHouse,SampleCastle}_extracted.db` each carry `component_geometries`
  (`geometry_hash TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT`) + `element_instances
  (guid, geometry_hash)` **in the extracted DB itself** — `Terminal_extracted.db` has `element_instances` but
  **no** `component_geometries` table at all. Confirmed by direct `.schema`/`.tables` query, not assumed.
- Confirmed 100% wall coverage (every wall guid resolves to a `component_geometries` row) on all three: Duplex
  57/57, SampleHouse 5/5, SampleCastle 879/879.
- **Independently re-ran the P2 BBOX_RECONSTRUCT reconstruction** (`scripts/test_orientation_proof.py`'s proven
  method: `R(rotation) @ local_mesh_vertex_bbox_corners + centre = world bbox`) in a standalone Python check
  against probe wall `2O2Fr$t4X7Zf8NOew3FNqI` in **this repo's own** `Duplex_extracted.db`: reconstructed
  midpoint `(8.5915, -9.1085)` — **exact match** to the prior session's `elements_rtree`-derived claim, while the
  stored `center_x/y` gives `(8.5915, -9.9175)` — confirmed **0.81m off**, real defect, reproduced independently,
  in a DB that doesn't even have `elements_rtree` (proving the fix doesn't need that table, `component_geometries`
  alone suffices). This repo's committed Duplex fixture has `rotation_z=0` for every wall (a flattened/older
  extraction, per the prior session's note) — so this reproduction is INDEPENDENT of the rotation question
  entirely; the centre-offset bug and the rotation question are orthogonal, confirmed on two different DBs.
- Confirmed `bbox_x/y/z` (world-AABB extents) are NOT the suspect field — only the centre point is; the fix
  therefore only touches which (x,y) a host's centreline is built around, never its length/thickness magnitudes.

**Decision (mine, since neither AskUserQuestion got an answer — a synthesis of the 3 options, grounded in what's
provably true today, not a guess):** implement a **tiered, self-reporting midpoint resolver**, not a binary
re-extract-everything-or-nothing choice:
1. Where real per-element mesh geometry already exists in the target DB (`component_geometries` via
   `element_instances` — Duplex/SampleHouse/SampleCastle today), reconstruct the TRUE world-bbox midpoint from
   it. This is **extraction, not invention** — every number comes from the element's own already-extracted mesh
   vertices; no re-extraction pass is needed for these three.
2. Where it doesn't exist (Terminal today), fall back to the raw `center_x/y` **but mark the result
   `verified:false`** and log it once per host (`§DW-UNVERIFIED-MIDPOINT`) — never silently trust an unverified
   number as if it were ground truth, matching the existing `§DW-NONCARDINAL` refuse-and-log precedent.
3. Full re-extraction of Terminal (option (a)) is NOT done here — it touches the shared extraction pipeline
   contract for a building with no committed mesh source in this repo's reach; left as a follow-on, now with a
   named, scoped shape (add `component_geometries`/`element_instances` coverage — or preserve `elements_rtree` —
   for Terminal specifically) rather than an open-ended "re-extract everything."

**Implementation — `build/disc_walker.js`:**
- New `_eulerMat3(rx,ry,rz)` + `_trueMidpoint(bdb, guid, w)` helpers (mirrors `test_orientation_proof.py`'s exact
  P2 method: full 3-axis rotation, not a cardinal-only shortcut — Terminal alone has non-zero `rotation_x/y`
  on 37,602 elements, confirmed by direct query, so the general form is used even though it degenerates to a
  2D case on every building the fix actually applies to today). Wrapped in `try/catch` per query — DBs missing
  `element_instances`/`component_geometries` (Terminal) fall back cleanly, never throw.
- `hostBind()`'s `hosts` query gains `rotation_x/y/z`; each host is decorated ONCE (not per-placement) with
  `tx/ty/tz` (the resolved midpoint) + `midVerified`. SIDE branch's centreline and TOP/BOTTOM/CENTER branch's
  XY-nearest-host + placement position both read `tx/ty/tz` instead of the raw `x/y/z`. `bbox_x/y/z` (already
  trustworthy) are untouched. Bound placements carry `midVerified` through so a witness (or the Modeller UI)
  can distinguish a verified containment result from an unverified one — never conflate the two as "0 outside."
- `hostCount`/`bound` results gain `unverifiedHosts` (count) so a caller sees at a glance whether its containment
  read is trustworthy.

**Witness (`scripts/witness_true_midpoint.js`, new, plain Node/sql.js, same shape as
`witness_hostbind_rotation.js`):**
- **T1 RECONSTRUCT-MATCH** — probe wall `2O2Fr$t4X7Zf8NOew3FNqI` (Duplex): `_trueMidpoint` reproduces
  `(8.5915, -9.1085)` to float precision, `verified:true`.
- **T2 CONTAINMENT** — real ARC envelope (all real elements, +0.5m margin) on Duplex; run the FIXED `hostBind`
  SIDE-mount ELEC walk; assert placements-outside drops from the known 65/267 (24%) baseline. Also runs on
  SampleHouse/SampleCastle (regression — expect ~0 outside throughout, mesh-verified).
- **T3 TERMINAL-HONESTY** — Terminal has no mesh source; assert every host comes back `midVerified:false` and
  the walk result carries `unverifiedHosts === hostCount` (never silently reports a clean containment number
  for a building that can't actually be measured).
- **T4 REGRESSION** — `witness_hostbind_agnostic.js`, `witness_shim_select.js`, `witness_elec_hostbind.js`,
  §DWG, §DXG, `witness_walkback_mep.js` all stay green (additive fix — the true midpoint EQUALS the raw centre
  whenever a wall's placement-line origin already happens to be centred, so byte-identical wherever that holds).

**Sequencing (per this file's own established rule, "Bug A fix + witness in bim-compiler FIRST, self-contained,
no deploy — only port to `~/bim-ootb/modeller/disc_walker.js` after green"):** implemented + witnessed here only;
NOT ported to the live/shared tree in this pass — that is a separate follow-on PR once this is reviewed.

**✅ IMPLEMENTED + WITNESSED 2026-07-09 — `scripts/witness_true_midpoint.js`, 15/15 PASS, 0 FAIL**
(`build/logs/witness_true_midpoint_2026-07-08T2126.log`):
- T1 RECONSTRUCT-MATCH + T1 DEFECT-CONFIRMED — probe wall reconstruction exact, 0.809m defect independently
  reproduced (matches the prior session's number, and needed NEITHER `elements_rtree` NOR the live `~/bim-ootb`
  tree — `component_geometries` alone, already committed in this repo, is sufficient).
- T2 CONTAINMENT — **Duplex went from 1/116 outside → 0/116 outside** (this repo's own committed fixture, despite
  having `rotation_z=0` on every wall — confirms the centre-defect is orthogonal to rotation, as diagnosed).
  SampleHouse and SampleCastle were already 0 outside on both OLD/NEW (no triggering wall in either), so the fix
  is neutral there — not a false "everything was already fine," just that Duplex happens to hold the one
  measured case. All three report `unverifiedHosts=0` / every placement `midVerified:true`.
- T3 TERMINAL-HONESTY — Terminal correctly reports `unverifiedHosts===hostCount` (333/333) — the honest "cannot
  measure this" signal now flows all the way to the caller instead of silently defaulting to "0 outside."
- T4 REGRESSION — the full existing suite re-run and byte-identical on pass-counts: §DWG PASS=49/FAIL=0, §DXG
  PASS=12/FAIL=0, W-ELEC-HOSTBIND 5/5, W-SHIM-SELECT 6/6, W-WALKBACK-MEP 8/8, W-HOSTBIND-AGNOSTIC now 6/6 (see
  below — required a companion test fix, not a code regression).

**⚠ Found + fixed one real "verify the checker" case during T4** (same discipline as the prior session's own
self-correction on its R1 witness): `scripts/witness_hostbind_agnostic.js`'s `distToWalls()` helper built its OWN
ground-truth wall line from the RAW `center_x/y` — the exact defect under test. On SampleHouse's one non-centred
wall (raw→true delta 0.879m along its 14m run), the FIXED `hostBind` correctly moved 2 fixtures ~0.88m closer to
the TRUE wall face, which the test's own STALE reference line then flagged as "now far from the wall" (afterFloat
2→ where it was 0 before). Verified this was the test, not the code, by reconstructing the SH wall's true midpoint
independently (found the 0.879m delta) before touching anything. Fix: `distToWalls()` now also calls
`DW._trueMidpoint()` for its reference line — H1 back to exactly its pre-existing form (36 bound, float→0,
median=0.145m=wall half-thickness).

**⚠ Deliberately scoped the fix DOWN after finding a second regression, per RosettaStone Rules 2+7 ("score is
arbiter... if a stone drops, the fix is overfit, revert"):** the TOP/BOTTOM/CENTER branch (nearest-XY host
binding, e.g. SC's grille→window CENTER mount) was initially given the SAME `tx/ty/tz` correction as the SIDE
branch. This regressed `witness_hostbind_agnostic.js`'s H3 (SC grille reproduction: max |Δz| 0.05m→0.084m) —
because the MINED `VENT_WINDOW_SHIM` offset (0.415m, MAD=0.000m) was measured against the window's RAW
`center_z`, not its true midpoint; correcting only the apply side introduced a fresh mismatch on a host TYPE
that was never shown to have this defect in the first place (windows are point-like insertions, not long runs
with an off-centre placement-line origin — the proven defect is specific to wall SIDE-mount hosts). **Reverted
the TOP/BOTTOM/CENTER branch to raw `x/y/z`** — the fix now touches ONLY the SIDE-mount wall centreline, where
it's proven. This is the correct scope, not a partial fix: applying a proven-elsewhere correction to an
unproven case made a working, tightly-mined witness worse, so it was pulled back out.

**Net (as first written, 2026-07-09 morning):** Bug A fixed for the proven case (wall SIDE-mount centreline, all
3 mesh-backed buildings) with 0 regressions. **This was WRONG in one load-bearing way — corrected below, same
day, by an independent reviewer session before this was closed out.**

---

## 🛑 §BUG-A CORRECTION — the fix quietly tested the wrong database (2026-07-09, same day, reviewer-caught)

**Same mistake shape as the earlier R1-witness self-contamination, moved up one level:** the T2 "containment
fixed" claim above was tested against `deploy/buildings/Duplex_extracted.db` (1/116 outside pre-fix) — an
older/flattened extraction that happens to be byte-different from `~/bim-ootb/modeller/Duplex_extracted.db`,
**the actual DB the original 65/267 defect was measured against.** A reviewer session re-ran the fix against the
live DB directly and found it **completely unchanged: still 65/267 outside, unverifiedHosts=57/57** — the fix
had not touched the evidence it was supposedly fixing. Root cause: `_trueMidpoint()` only queried
`component_geometries`; the live DB names the identical-shaped table `base_geometries` instead (confirmed by
direct `.tables` query: live Duplex has 0 `component_geometries` rows, 170/170 populated `base_geometries`
rows, plus a populated `elements_rtree`). This repo's own committed `deploy/buildings/*` copies happen to use
`component_geometries`, which is exactly why the fix "worked" there and nowhere it actually mattered.

**Separately, the same investigation surfaced this while checking a DIFFERENT question** — the user asked why I'd
called Terminal "no mesh source" at all, since IFC→BOM extraction demonstrably produced full Terminal mesh data
once (`/home/red1/Projects/bim-compiler/backup/Terminal_Extracted_FULL_MESH.db`, 22,899 populated vertex blobs —
real, checked directly). Tracing this down: `base_geometries` (hash keys, no payload) exists in this repo's
`library/archive/Terminal_extracted.db` and `DAGCompiler/lib/input/Terminal_extracted.db`, but ALL rows have
NULL `vertices` in both — the geometry references survived, the mesh payload didn't, in whatever archival step
produced these copies. None of the LIVE Terminal DBs (`~/bim-ootb/buildings/`, `~/bim-ootb/viewer/buildings/`,
`~/bim-ootb/modeller/`) carry a `base_geometries` table at all. So "Terminal has no mesh source" is accurate
for every DB that currently ships or is live — but wrong as a claim about the pipeline's capability, and the
user's Modeller-is-ARC-only-by-design point is a separate, correct, valid point (a deliberate test-scoping
choice for RosettaStone reconstruction, not evidence about geometry availability elsewhere).

**Fix applied:** `_trueMidpoint()` now tries `component_geometries` THEN `base_geometries` (same schema, same
join via `element_instances.geometry_hash`) — mirroring the exact fallback list `scripts/measure_narrowphase.js`
already carries for the same reason, not an invented convention.

**Re-verified directly against the live DB (read-only, never edited, per this project's worktree-hook rule) —
new T5 in `scripts/witness_true_midpoint.js`:**
```
LIVE Duplex (~/bim-ootb/modeller/Duplex_extracted.db): envelope n=253 verified=253
  OLD 65/267 outside, NEW 0/242 outside, unverifiedHosts=0/57
```
The ORIGINAL 65/267 claim reproduces exactly on the real evidence DB (not a proxy), and the fixed `hostBind`
now puts 0 outside it, with every host mesh-verified (`base_geometries` resolves cleanly). Full suite re-run:
**17/17 PASS, 0 FAIL** (`build/logs/witness_true_midpoint_2026-07-08T2141.log`) — T1-T4 unchanged from before,
T5 new and green.

**What is NOT fixed, and should NOT be reported as done (reviewer's explicit call, correct):**
- **MEP-wide scope is untouched.** `_loadXYZ`/`_loadXYZB` (used by `routeChains`) and `occupancy`/`gate` all read
  `element_transforms.center_x/y/z` directly, with no `_trueMidpoint` correction — the reviewer measured this
  same defect on MEP run elements at up to 4.5m off. This fix only touches `hostBind()`'s SIDE-mount wall branch.
  Extending `_trueMidpoint` to those call sites is a real, separate, not-yet-scoped follow-on.
- **Terminal containment remains genuinely unmeasurable** — confirmed again, now with the fuller picture of why
  (mesh payload was dropped somewhere between extraction and what's committed/live, not never produced).

**Status: real, verified, partial progress — wall SIDE-mount hostBind containment is now actually fixed on the
building the defect was measured on. Not "Bug A done." MEP-wide + Terminal-mesh-recovery remain open, named
scope for a follow-on, per WORK-TO-ZERO (mark open items explicitly rather than let a partial fix read as closed).

---

## §BUG-A — second reviewer pass, 6 findings, all checked (2026-07-09, same day)

A reviewer session gave a mixed verdict: real abstraction (per-instance mesh reconstruction, not a per-convention
shortcut — see §START-END-THEORY-TESTED below for why that distinction is the whole ballgame), but flagged 6 gaps.
Checked all 6 directly rather than accept the framing:

1. **MEP-wide blast radius (routeChains/`_loadXYZ(B)`/occupancy/gate all read raw `center_x/y/z` uncorrected)**
   — CONFIRMED, real, unaddressed. Now sharper than "a bounded error": the Start/End-formula test below proved
   asymmetry isn't predictable even within one building's own wall set, so this is unbounded per-element risk,
   not an estimable one. Still out of scope for this pass.
2. **Terminal has no remaining cheap route** — CONFIRMED (see §START-END-THEORY-TESTED: the one candidate
   shortcut is now falsified). Terminal containment is a data-recovery problem (recover/regenerate the mesh
   payload), not a walker-code problem, full stop.
3. **Mined percepts (VENT_WINDOW_SHIM, 0.415m) may be measuring an artifact of raw-center, not true offset** —
   PLAUSIBLE, not verified either way this session. Flagged as a real risk for the NEXT session that attempts
   cross-building generalization of that constant — check against a mesh-recovered true center first.
4. **"0 outside before/after" on SH/SC might mean the fix was never exercised** — CHECKED DIRECTLY, PARTIALLY
   WRONG: added a per-placement OLD-vs-NEW shift measurement to `witness_true_midpoint.js`. Real, substantial,
   non-vacuous shifts occurred: **Duplex 0.29m, SampleHouse 0.82m, SampleCastle 2.25m** (all on real ELEC
   placements bound to real walls). The fix WAS exercised and DID change real output on all three — the
   reviewer's deeper point stands, though: the building-envelope containment metric is too coarse to have
   revealed this on its own; the shift number is now logged every run specifically so "0 outside" is never
   mistaken for "nothing moved."
5. **Other witnesses may share `witness_hostbind_agnostic.js`'s raw-center-ground-truth contamination** —
   CONFIRMED, found 2 more, fixed both: `scripts/witness_elec_hostbind.js` and `scripts/witness_dwwalk_hostbind.js`
   carried the IDENTICAL `distToWalls()` oracle built from raw `w.x,w.y`. Patched both to call
   `DW._trueMidpoint()` the same way; both re-verified GREEN after the fix (W-ELEC-HOSTBIND 5/5,
   W-DWWALK-HOSTBIND 6/6 — medians unchanged at 0.145m, confirming the contamination was silent, not
   previously masking a failure). `witness_hostbind_rotation.js` carries the same pattern too but belongs to
   the already-disproven/reverted rotation fix — not part of the live regression set, left as-is.
6. **Nothing shipped — bim-ootb still runs the original bug** — CONFIRMED, true, unchanged from the original
   sequencing decision (bim-compiler first, self-contained, port only after review). Named plainly, not implied.

**Full suite after all of the above: 18/18 PASS, 0 FAIL** (`build/logs/witness_true_midpoint_2026-07-08T2201.log`).

---

## §START-END-THEORY-TESTED — the BOM Tack (Start/End point) convention does NOT generalize to extraction (2026-07-09)

User asked whether the BOM's own Start/End-point (Tack-chain, dx/dy/dz cascading) formation theory — the thing
that already solves connection/length/orientation for GENERATIVE compilation — could replace the mesh-dependent
`_trueMidpoint()` fix, since it would need no `component_geometries`/`base_geometries` at all (unblocking
Terminal, which has neither). Hypothesis: `true_midpoint = center + (length/2)·direction(rotation_z)`, i.e.
treat `element_transforms.center` AS the wall's polyline START point (which a raw-IFC trace already showed it
literally is, for one Duplex wall) and derive the far end from the already-reliable `bbox_x/y` length + rotation.

**Tested against mesh ground truth on all 3 mesh-backed buildings (not just the one that inspired it) — per this
project's own three-stone-regression rule:**
```
Duplex:        57 walls, max formula-vs-mesh error 0.03m   (near-exact)
SampleHouse:    5 walls, max formula-vs-mesh error 7.95m   (FALSIFIED)
SampleCastle: 879 walls, max formula-vs-mesh error 7.85m   (FALSIFIED)
```
Root cause: inspected SampleHouse's own LOCAL mesh bboxes directly — some walls have their placement origin
sitting at the local-frame midpoint already (symmetric local bbox), others have it well off either end
(asymmetric, e.g. `[-7.95, 6.19]`) — inconsistent even within one building's own wall set. Duplex's 100% match
was specific to that file's authoring/export history (drawn once, start-to-end, never re-trimmed), not a
general IFC placement rule. Also attempted an independent raw-IFC cross-check on Terminal itself
(`/home/red1/Downloads/TerminalMerged.ifc`, via `ifcopenshell.geom.create_shape` with world coords) — blocked by
a ~547m coordinate-frame offset between the raw IFC and `Terminal_extracted.db` (a site-normalization/
`global_offset` transform whose exact parameters aren't available in this session) — could not complete that
check, noted honestly rather than guessed around.

**Conclusion: the theory is right for the FORWARD (generative BOM compile) direction — where the compiler sets
and guarantees the Start/End convention — and does NOT transfer to the REVERSE (extraction/walk) direction,
where a third-party authoring tool's placement-origin convention is unverifiable per-element.** This is exactly
why the mesh-based `_trueMidpoint()` (reads each element's OWN real geometry, assumes no convention) survived
this same three-stone test while the formula-based shortcut didn't — the robustness comes specifically from
refusing to generalize a placement convention across instances, per this project's core non-invent discipline.
No code change from this finding (the existing mesh-based fix stands, unmodified) — recorded so nobody re-tries
this shortcut later without re-deriving why it failed.
