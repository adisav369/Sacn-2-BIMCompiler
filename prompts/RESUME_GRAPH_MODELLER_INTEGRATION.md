# RESUME — Spatial Dependency Graph → Modeller integration (the USEFUL diff)

```
# ⚠ DO NOT REMOVE
SCOPE: Land the Spatial Dependency Graph (typed cross-edges over the BOM trunk) into the MODELLER as authoring
value the present modeller LACKS. The graph frame is correct (chosen after "this is hard, rethink" — it is BOM
drawn with its real edges = MRP-with-geometry, NOT a pivot away from BOM). The forward FOLD already ships in the
modeller — DO NOT rebuild it. Value = the EDGES + the BACKPROP exceptions, layered on the existing op-log.
GUARDRAIL (read first, every session): prompts/MODELLER_DIRECT_MANIPULATION.md + prompts/CONSTRUCTION_GRID_BOM_DUAL_MODEL.md
+ prompts/SPATIAL_DEPENDENCY_GRAPH.md. Before any code: (1) does it already ship in deploy/dev? (2) edge or fold?
(build edges, not fold) (3) MODELLER track (viewer/modeller.html), not viewer/erp. [[feedback_graph_edges_not_fold_modeller_track]]
WITNESS-FIRST, spec-first, oracle = pristine extracted.db / raw extraction (never cooked output.db). Read the §-log.
```

## 🔒 §VISION-LOCK — the product, in five sentences (READ FIRST, every session; do NOT drift from this)
**THIS IS THE NORTH STAR. If a session's work does not serve one of these five, it is drift.** (Hardened 2026-06-25 c.)
1. **OPEN = a whole ARC building, and you EDIT it.** `extracted.db` opens as a *complete* building (SH / DX / SC, or
   complex like Terminal) — never a fragment. **ARC is the SOLE edited substrate**; the import RosettaStone
   (`output.db == extracted.db`, Java-proven) is the birth-certificate, retired on first edit.
2. **The 3D GRID is the primary edit-handle.** In the BOMTree you select the building's grid system, locate an axis,
   and **drag-remodel its dimensions** — *stretch ≠ scale*: walls stretch (thickness held), roof re-derives at held
   pitch, slabs re-fold on the datum. Engine already ships: `GridKinematicEngine` / `GEOM_GRID_MOVE`.
3. **CONFORMITY handlers fire alongside the drag** so the remodel stays coherent: openings ride their host and can't
   divorce; **door-crush / UBBL min-dim / clash = RED** (hard stop); soft realigns (`abuts`/`spans`/`anchored`/opening-
   move) = **ORANGE** (accept/ignore, signable). Measured, never guessed.
4. **EVERY non-ARC discipline is a WALKER that FILLS the ARC space.** **STR walks to STRENGTHEN** the ARC
   (columns/beams/reinforcement); **MEP/CW/SP walk to SERVICE** it; **4D/5D/ERP walk to ENTERPRISE-ify** it.
   `RouteWalker` (freshly ported from the WORKING Java — `routewalker.js`, already loaded in `modeller.html`) is the
   prototype walker. **Remodel the ARC → every walker RE-WALKS into the changed space** (signed op, non-invent).
   ⚠ This SUPERSEDES the older "ARC/STR transform *together*, only MEP walks" seam — **STR is now a walker, not a
   transform-partner of ARC.** (User-confirmed 2026-06-25 c. Spine §227/§295/§312 patched to match.)
   **STR-walks PROVEN on Terminal data + spec'd (2026-06-26): `prompts/STR_ROUTEWALKING_SPEC.md`.** Empirical: 158 STR
   columns sit on a 16×11 emergent grid (skeleton = f(grid), deterministic, RS-clean); 32,203/33,324 space-frame plates
   share one unit-bbox (0.5×0.2) = `instanced-by n` tessellation (system = generative, oracle'd not bit-exact). STR is
   MORE walkable than MEP (its anchors = grid/surface, DERIVED from ARC+SDG; no hand-seed). Handler set MIRRORS MEP RW's
   5 stages with SPATIAL (grid-anchor/span/tessellate/fit-clash) + REGULATORY (load-path/span-depth/code-min, each rule
   CITED) handlers + RED/ORANGE signals. **MEMBERSHIP FORK RESOLVED → editable BOM = ARC ONLY; STR joins the walker/
   routed side; extracted STR = ORACLE** (the space-frame can't be both dropped-verbatim AND walk-reproduced).
5. **ONE PANEL — the Outliner = Find's faceted lens ON STEROIDS.** Find's panel is a *placeholder stub* we superimpose
   onto; each navigate-facet is reborn as a **handle** or a **walker** over the one ARC substrate (navigate → drive/
   generate). ⚠ REUSE Find's *pattern* (`_treeMode`/`_treeNode`/axis-pills) inside the MODELLER's `bonsai_outliner.js`;
   `navigate_find.js`/`panels.js` are VIEWER-track — never import or deploy them. `routewalker.js` is ALREADY in the
   modeller.

### 🔧 §VISION-LOCK-1 — OPEN GAPS under sentence 1 (NEW SESSION: read these FIRST, in order)
Sentence 1 ("OPEN a whole ARC building and EDIT it") is NOT truly met yet. Two gaps. **Begin the session by listing
these two, saying why each is not done, then discussing how to resume + fill them — before any new code.** The spine
is **ARC (render exact) → walk STR → walk other DISCs**; both gaps are at the very first step (render ARC exact).

> **▶ SESSION HANDOFF 2026-06-29 — GAP 1 + GAP 2 CLOSED + STR-into-ARC done. Full protocol + proofs =
> `prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md §8` (§8A exactness/tolerance table · §8B two-stage ablation + before/
> after interim records · §8C two log layers · §8D GREEN report · §8E TE suite progress). Code on bim-ootb branch
> `lane/arc-mesh-readpixels` (2 commits, PUSHED), NOT yet merged/PR'd.**
> - **GAP 1 (real ARC mesh) ✅** — `arc_editable` emits LOD-300 hash-ops rendering real `component_geometries`
>   (instanced; box = logged geo-absent fallback); `bonsai_library.registerExtractedComponent`. **Proof A** (node,
>   all 35,552 TE ARC): vertices LOCAL + extent==bbox **0.0000**; fixed a latent box-proxy displacement on asymmetric
>   elements. **Proof B** (node, all 35,552): modeller `place()`==Viewer to **1.78e-15m** (z=cz+local_zmin cancels ground-seat).
> - **GAP 2 (readPixels) ✅** — **W-ARC-MESH-READPIXELS 6/6**: A/B-isolated readPixels (hide group, diff) caught a
>   false-positive the naive whole-frame nonBg=100% would have passed. The probe = `window.__arcPixelProbe(isolateFids)`.

> **▶ RE-VERIFICATION 2026-07-04 (watchdog re-run, not carried-forward trust) — same conclusions, ONE escalated risk.**
> All witnesses below were actually re-executed fresh in a throwaway worktree off `lane/arc-mesh-readpixels`, not
> assumed from this doc's prose.
> - **GAP 2 "EYES" (readPixels) — CONFIRMED-CURRENT.** `witness_arc_mesh_readpixels.js` reran **6/6**, numbers match
>   exactly (M5 arcPainted 9.8%, M3 totalTris=305,713 vs 18,684 baseline). This is the capability the user asked
>   about directly — "Claude deriving good EYEs" = proving the ARC actually rasterizes via maths, not a screenshot.
>   It works, and it's real. **But it lives only on the branch below — not in production**, and its recorded numbers
>   were measured against the branch's OWN (now-obsolete, see below) mesh path — the harness is portable, the numbers
>   are not.
> - **§8E TE suite — CONFIRMED-CURRENT.** `witness_str_into_arc.js` 11/11, `witness_str_canopy.js` 8/8,
>   `witness_disc_density.js` 8/8, `witness_disc_clash.js` 10/10, `witness_green_report.js` 7/7 — all reran identical
>   to the figures below.
> - **🚨 "434 files diverged" — RE-CHECKED 2026-07-04 (second pass) and this framing is WRONG, not just stale.** That
>   stat (`git diff --stat main...branch`) is a full-tree artifact of main's OWN churn (62 commits in the 5 days since
>   the branch's last commit), not the branch's actual footprint. A real `git merge --no-commit` dry-run shows the
>   branch's unique contribution is **7 commits / 17 files / +1313 −32 lines**, and only **3 files conflict**
>   (`arc_editable.js`, `modeller.html`, `str_walker_outliner.js`) — the rest (`disc_walker.js`, `str_walker_bridge.js`,
>   `bonsai_library.js`, 11 new test/fixture files) auto-merge clean. This is a bounded cherry-pick, not a
>   multi-day reconciliation.
> - **GAP 1 is ALREADY CLOSED on `main` — independently, via a DIFFERENT and MORE capable mechanism this doc never
>   updated to reflect.** `modeller/real_geometry.js` (bim-ootb PR #598, #613 — see
>   [[project_modeller_lod400_real_geometry]]) already renders real per-element meshes for all 5 residents including
>   Terminal's split geo-db (`Terminal_geo.db`), with LOD300 catalog matching, a no-id-column schema fix, and
>   hardfail-no-silent-box — strictly more capable than the branch's 2026-06-29 `arc_editable.js#buildSeedOps`
>   rewrite. **Merging the branch's version of that one file would be a REGRESSION, not a gain — it must be
>   discarded, not merged.** `str_walker_outliner.js` on main already wires the equivalent
>   (`Bonsai.library.registerRealGeometry`, line ~308) — only the branch's `_seedStrWalk(O, key)` call (trigger the
>   STR-skeleton render below) is genuinely missing, not the mesh registration around it.
> - **What's genuinely still missing from main (confirmed by direct grep, not present anywhere):**
>   `swbRenderOps`/`swbCanopyOps` in `str_walker_bridge.js` (STR skeleton + space-frame canopy render-into-ARC —
>   §8E-1b/§8E-2a's actual payload), the `_seedStrWalk` wiring that calls them on open, and the
>   `__arcPixelProbe`/`__dwPixelProbe` readPixels harness in `modeller.html` (pure-additive ~85 lines). These + the
>   5 §8E witness files + fixture scripts are the real, non-redundant, still-unshipped value — roughly HALF of what
>   the branch contains, not all of it.
> - This changes the standing decision from "merge now vs. accept as permanently-unmerged research" to: **cherry-pick
>   the ~5 genuinely-new files onto fresh main, drop the obsolete `arc_editable.js` rewrite, re-derive the readPixels
>   numbers against the current mesh path.** Estimated a bounded job (part of a session), not a multi-day effort.

> **▶ RESOLVED 2026-07-04, same day, parallel session — bim-ootb PR #638 MERGED.** The cherry-pick above was executed
> (not by this thread — a concurrent session did it, self-reported via memory `project_arc_meshreadpixels_branch_
> unmerged.md`) and **independently RE-VERIFIED here** (fresh checkout of `main`, fresh fixture builds from LFS
> Terminal_meta.db/Terminal_geo.db, all 5 witnesses actually re-run headless — not trusting the PR body):
> - `swbRenderOps`/`swbCanopyOps` + `_seedStrWalk` wiring + `__arcPixelProbe`/`__dwPixelProbe` are ON MAIN now
>   (commit `19721ff`, #638). `arc_editable.js`/`bonsai_library.js` confirmed untouched (still at #613) — the
>   obsolete mesh rewrite was correctly discarded, not merged.
> - **Re-run fresh, independently, this session:** W-STR-INTO-ARC **11/11**, W-STR-CANOPY **8/8**,
>   W-DW-CLASH-TE **10/10**, W-GREEN-REPORT-TE **7/7** — exact match to the branch's original 2026-06-29 numbers
>   (girder section 0.500×0.750m 0-tol, canopy count err 1.38%, clash 2878→3 = −99.9%, all readPixels checks pass).
> - **W-DW-DENSITY-TE now 7/8, confirmed** — D3 (ARC occupancy envelope, was ≥99%) measures PLB 28/28=100%,
>   ELEC 1874/1988=94.3%, FP 1028/1117=92.0%, ACMV 1592/1680=94.8%. `disc_walker.js` is untouched by this port
>   (confirmed via `arc_editable.js`/`bonsai_library.js` byte-diff + `git log` — the branch's one disc_walker fix
>   was already independently on main via PR #576), so this is a real drift in main's own placement behaviour over
>   the 5 days since the branch's last measurement, not a regression from this port. **NEW OPEN ITEM, not urgent/
>   blocking:** find what shifted (candidate: something in the 62 intervening main commits touches disc placement
>   density) and decide if 92-95% is acceptable or needs a fix.
> - **§8E TE suite headline is now accurate as a claim about `main`, not just a branch:** GAP 2 readPixels proof +
>   STR-into-ARC + canopy + clash + green-report all ship in production. GAP 1 (real mesh) was already production
>   via the separate `real_geometry.js` lane. The only genuinely still-open items from this whole spine are:
>   §8E-3 (routeChains MEP render into modeller, engine proven, render wiring not started) and W-SDG-BACKPROP
>   (pegging/MRP exceptions, zero hits anywhere). `lane/arc-mesh-readpixels` is now safe to delete (historical,
>   fully superseded).
> - **GAP 1 "Proof A/B" headline numbers (0.0000 extent, 1.78e-15m) — COULD NOT independently reproduce.** The
>   underlying mechanism (`witness_arc_mesh_readpixels.js`) reran clean and live, but the specific all-35,552-element
>   proof script that produced those two numbers is not committed anywhere in either repo — only exists as commit-
>   message prose (`baafada4e6`). Not disproven, just not an artifact anyone can re-run today.
> - **§8E-3 (routeChains MEP render into the modeller) — STILL OPEN, unchanged.** Engine side reconfirmed live
>   (`witness_walkback_mep.js` 8/8, same Terminal 5317-seg / Duplex 358-seg numbers). Modeller-render wiring: zero
>   commits since 2026-06-29 across all ~150 branches touch this. Exactly where the doc left it.
> - **`hosted-by` ride — DRIFTED (further than this doc's own §NEXT list shows).** `§STRETCH-RIDE` (bim-ootb PR
>   #604, MERGED 2026-07-02) already ships ride-on-stretch + a door-out RED (door centre leaving its host
>   footprint). Still missing: a width/crush-specific RED (shrunk span < real measured door width) — only
>   position-leaving is checked today, not "door still fits."
> - **Backprop / `W-SDG-BACKPROP` — STILL OPEN, reconfirmed independently.** Zero hits anywhere in either repo,
>   any branch, any commit message — not just carried forward from an earlier grep.
> - **STR Stage-1 ✅** — gate-assumptions proven (D clash data-gated by EMPTY `rule_avoidance`; E grid 158→18×10; FRAME
>   STR shares ARC site frame). `W-WALKBACK-STR` reproduced 5/5. **W-STR-INTO-ARC 6/6**: 158 cols walk onto grid INSIDE
>   the laid ARC, residual RMS 0.094m, same frame, readPixels-isolated.
> - **§8E-1b GIRDER RENDER ✅ DONE 2026-06-29** (commit b5302cc, PUSHED) — **W-STR-INTO-ARC now 11/11**: lifted the STR
>   render into reusable bridge verb `swbRenderOps` + production `_seedStrWalk` (overlays ARC); girders render as a
>   single-level bay lattice, cross-section = MEASURED IfcBeam median 0.500×0.750m (non-invent), length = derived span.
>   G1 108 girders / G2 108/108 on-grid / G3 0-tol section / G4 readPixels 7281px / G5 span-band. 8 over-span girders
>   honestly RED. Fixture rebuilt with STR IfcBeam.
> - **§8E-2a SPACE-FRAME CANOPY ✅ DONE 2026-06-29** (commit 01c9312, PUSHED) — **W-STR-CANOPY 8/8**: `swbCanopyOps`
>   renders the 33K-plate roof as a BOUNDED generative tessellation into the laid ARC; count proven NUMERICALLY
>   (predictedN 33784 vs extractedN 33324 = 1.38%), unit==measured modal 0.5×0.1×0.11m, NN cadence within band, §DW-CAP
>   placed=2374 of 33232, empty→0 falsifier. Fixture `Terminal_plates_proof.db` (IfcPlate transforms only).
> - **§8E-2b MEP DENSITY ✅ DONE 2026-06-29** (bim-ootb 6879595 + bim-compiler cb49ca8f) — **W-DW-DENSITY-TE 8/8**: the 4
>   MEP discs walk + render into the laid ARC (`__dwPixelProbe` readPixels), 100% envelope, ACMV +7% / FP +13% tight,
>   PLB routed→honest no-network refusal, no fidelity field. FINDING: ELEC 2.4× over (density-transfer drift). Engine
>   fix: single-placement envelope-bound (build §DWD/§DWG/§DXG green).
> - **§8E-4 STAGE-2 CLASH ✅ DONE 2026-06-29** (bim-ootb 6b9b8b6) — **W-DW-CLASH-TE 10/10**. `rule_avoidance` was NOT
>   empty (spec text stale): already mined (10 pairs, p05), gate already iterate-yields + flags-RED → this was the Δ
>   proof. Δ-TABLE: clashes 170→3 (−98.2%), 164 yields, count Δ=0, xy-drift=0 (envelope preserved), no below-grade, 3
>   flagged RED, top-priority never yields, residual 0.06% ≤ real-TE p05 tail 2.17%. Position GENERATED → judged by clash
>   RATE, not per-element NN.
> - **§8E-5 GREEN REPORT ✅ DONE 2026-06-29** (bim-ootb b7553ef) — **W-GREEN-REPORT-TE 7/7**. One harness runs the whole
>   TE walk → emits `modeller/tests/TE_GREEN_REPORT.md` (per-layer bars + §8D criteria + gaps). **Red-on-theirs: 994 real
>   cross-disc pairs <0.10m, 57 <0.05m** in the as-built Terminal (green-on-ours residual 0.06% = auditing, not bragging).
>   Confidence ECE 0.034 cited. **§8E TE SUITE COMPLETE** (§8E-0/1/1b/2a/2b/4/5).
> - **§8E-3 ROUTED MEP NETWORK ✅ UNBLOCKED 2026-06-29** (bim-compiler `scripts/witness_walkback_mep.js`, **W-WALKBACK-MEP
>   8/8**) — the ⛔ was a SUBSTRATE gap, not an engine fault: `disc_walker.routeChains` reads endpoints DIRECTLY from a
>   real MEP-bearing extracted.db, so candidates+oracle share ONE frame by construction (the §5 local↔site split cannot
>   arise) and the endpoint classes are present. §8E emitted 0 only because the modeller passed the LAID ARC-ONLY fixture
>   as bdb. **0→N proven:** Terminal (terminal_rules) 5317 segs (PLB pipes+ACMV ducts), Duplex MEP `build/Duplex_mep_
>   extracted.db` (duplex_rules) 358 segs (PLB), ARC-only SampleHouse 0. NON-INVENT (5675 segs, 0 fabricated, every gap ≤
>   measured bound). Walk-back vs a GEOMETRIC touch oracle (fitting↔pipe-run point-to-3D-segment; IFCs carry NO
>   IfcRelConnectsPorts so geometric touch IS the ground truth): precision (per-rule) PLB 0.896(TE)/0.969(DX) @0.15m,
>   recall ~0.40 = junction coverage. FINDING: ACMV ducts looser (0.269) — nn-to-CENTRE vs face; **M7 opt-in route-to-FACE**
>   (`routeChains(...,{toFace:true})`, default OFF → live `dwWalk` byte-identical) lifts 0.269→0.332 (partial).
> - **NEXT (pick up here):** wire `routeChains` MEP network into the modeller §8E-3 render (overlay nn-segments as edges
>   into the laid ARC, mirror of `_seedStrWalk`/`swbCanopyOps`) + a `__dwPixelProbe` readPixels assertion; the ENGINE is
>   proven, only the modeller render + deploy remain. Other open threads: route-to-FACE deeper tuning (M7 gives only a
>   PARTIAL ACMV lift — ducts stay hard; a face-AND-direction model might do better); full-SCALE 261MB verbatim plate
>   range-stream (DEFERRED); yaw-by-vertex owed to walkers (TE axis-aligned
>   so moot here — needs a rotated building); merge/PR `lane/arc-mesh-readpixels` into bim-ootb main. Fixtures reproducible
>   via `modeller/tests/build_*_fixture.sh`.

**GAP 1 — Render the REAL ARC mesh, streamed the Viewer's way (not box proxies, not SH-only).**
- *What's wanted:* the Modeller opens a building and shows its REAL extracted ARC — exactly the originals — by reusing
  the Viewer's stream: **bbox first (meta.db) → real meshes follow (`*_geo.db` via LFS media-endpoint RANGE)**,
  ARC-filtered. meta.db is the Viewer's domain too; reuse `viewer/` httpvfs/DLOD, don't reinvent.
- *Why NOT done:* §ARC-1 (PR #571) took a shortcut — **in-memory box proxies from `element_transforms`**, and only
  **SampleHouse** was ever rendered/compared. **No building-mesh streaming exists** — `Terminal_geo.db` (261MB, LFS)
  sits unused; the only real-mesh path is catalog inserts. DX/SC/TE ARC: never rendered or compared. So "exact ARC"
  is **box-not-mesh for all, and unproven for DX/SC/TE.**
- *How to resume:* re-point `arc_editable.js` at the Viewer ARC stream (bbox→mesh, ARC-only); keep the box ONLY as a
  geo-absent fallback (logged). Then **W-ARC-ROSETTA per building (SH/DX/SC/TE):** rendered ARC == extracted ORIGINAL
  by maths — count, centre, extent, and **yaw by VERTEX/CORNER (not world-AABB — the AABB hides rotation; the long-open
  rotate-vertex gap)**, real mesh vertices == `*_geo`. Simple set exact FIRST, then TE (range-stream + rtree/sweep
  prune for scale). Any building not proven = ⛔ with its one blocker, never silently passed.

**GAP 2 — Prove "it is visible" by MATHS (readPixels), not by eye.**
- *What's wanted:* a §-log that proves the rendered ARC actually rasterizes on canvas — no eyeball.
- *Why NOT done:* current witnesses prove GEOMETRY (position/extent via the scene mesh `geometry.boundingBox`,
  conformity via the gate, mesh-in-scene + selectable) but **never the pixels**. The only visibility check all session
  was ONE screenshot — a visual check, which by the no-eyeball rule does not count. [[feedback_whitebox_no_handwave_geometry]]
- *How to resume:* add a headless `gl.readPixels` (or projected screen-rect colour) assertion — the edited ARC occupies
  its expected screen pixels with non-background colour, + `visible===true / opacity>0 / in-frustum` flags. Retrofit it
  across the shipped ARC / cascade / gate / stretch witnesses so "visible" is a §-log line everywhere, not an assumption.

*(Note: §ARC-1/§SDG-CASCADE/§GATE-1/§STRETCH-1 shipped real EDITING machinery on the SH box-proxy substrate — useful,
but they ride on GAP 1. Close GAP 1 + GAP 2 first; the editing already works once the substrate is the real streamed
mesh on all buildings. THEN proceed down the spine: walk STR, then the other DISCs.)*

| Find facet (navigate, today) | …ON STEROIDS in the modeller (drive / generate) |
|---|---|
| **Disc** (filter by discipline) | **Walker switcher** — the tab BEGINS with **ARC = the SOLE EDITABLE tab**; every other tab is a WALKER (read-only, regenerated), listed in walk/dependency order: **ARC → STR** (strengthen) **→ MEP/CW/SP** (service) **→ FP/ELEC/ACMV**. Edit ARC → walkers re-walk down the chain. |
| **Storey / Floor** (filter by level) | **Z-datum handle + typical-storey ×n** — raise Z / set n → storeys replicate, Z-runners (stairs/risers) auto-insert |
| **Phase / Task** (filter by 4D) | **4D walker** — sequence folds from the authored schedule (`time_machine.js`); 5D cost folds with it (enterprise dim) |
| **Rooms** (filter by space) | Spatial handle (IfcSpace) — weak today |
| **— (missing 4th facet)** | **Grids / SEMI-GRID** — derived candidate grid (cluster column centroids + wall centerlines) = the **dimension-remodel driver** = the PRIMARY handle |

**HOW IT ALL FALLS IN (consolidation 2026-06-26 — editable BOM = ARC ONLY, decreed):**
- **The Disc tab = ARC editable + walked followers** (order above). STR dropped from the editor; extracted STR kept as
  the walker's ORACLE only. Light editable surface, heavy generated (Terminal: 2,222 ARC editable vs 34K STR walked).
- **The Grids facet is a SHARED DATUM, not a discipline** — seeded ONCE from the whole extracted building (ARC walls +
  the STR-oracle columns → emergent `datum_plane`), then it is the editable dimension-driver; ARC binds re-fold and all
  walkers re-walk onto it. (Chicken-and-egg resolved: oracle seeds the grid once; thereafter grid → ARC + walkers.)
- **One re-walk chain on every ARC edit (the dependency/clash bus):** ARC substrate → STR walks (clash vs ARC) →
  services walk (clash vs ARC+STR) → 4D/5D re-roll. One signed op chain; deterministic replay. = `STR_ROUTEWALKING_SPEC.md` §3.
- **RosettaStone after the split:** editor RS = ARC reconstruction; STR skeleton = f(grid) deterministic (checkable);
  STR systems = generative (oracle'd, never bit-exact). Each walker output is signed + non-invent (cited rule / measured edge).

**WALKER SAFETY/MEASUREMENT LAYER (spec'd 2026-06-26, for a new session):** `prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md`
— a universal guard pass every walker passes through (containment · surface · orientation · clash · source · priority ·
refuse-to-place), a RosettaStone walk-back harness (Terminal = the multi-discipline oracle: STR/MEP/FP/ELEC/ACMV) that
post-checks each discipline walks back element-for-element, and a CALIBRATED confidence marker (earned on the
RosettaStone, never asserted). Answers "does the walk generalise?" — oracle to CALIBRATE, guards to GENERALISE.

**INVARIANTS (every slice, no exceptions):** signed ops into `kernel_ops` · NON-INVENT (re-derive against MEASURED
edges / raw extraction, never a guess) · ABSTRACT, never per-class (the door proved it — *delete* custom treatment,
don't add it) · oracle = pristine `extracted.db` / raw extraction, NEVER cooked `output.db` · witness-first, read the §-log.

## ⭐ SESSION UPDATE 2026-06-25 b — DESIGN SETTLED + PRODUCT INVERSION + FIRST SLICE SHIPPED
Read `prompts/SPATIAL_DEPENDENCY_GRAPH.md` §INVERT-TWIN-EDITING → §OUTLINER-COHERENCE → §BUILD-DECOMPOSITION →
§DIMENSION-HANDLERS (the full settled design) and memory `project_modeller_rosettastone_mission`. The long design
dialogue CONVERGED:
- **PRODUCT INVERSION (the headline):** don't author from scratch — **OPEN a ready-made ARC twin and EDIT it.** Open
  `extracted.db` = **ARC ONLY** (the sole edited substrate); **STR/MEP/4D/5D/ERP FOLLOW — crawl RouteWalk against the
  ARC and AUTO-COMPLETE** (open a bare ARC → it completes itself = speed + completion + reuse + signed fold). The twin
  becomes EDITABLE + GENERATIVE, not a read-only mirror. Beats the from-scratch authoring race we can't win.
- **THE ONE MODEL:** verbatim geometry (`extracted.db`, RS-trivial) + a GUID-pointer metadata overlay (edges/facets) +
  one signed op-log; the **Outliner = a crossing-facet lens** (Find's facet design MOVED into the modeller Outliner via
  `addCategory` over a shared core); the **ARC facet IS the BOM editor** (grid + graph/compose + individual change);
  edits & follows are signed ops; RosettaStone (`output.db==extracted.db`, Java-proven) = the import birth-certificate,
  retired on first edit. The Java→JS port shrinks: don't re-thread geometry, only port the edges + the fold.
- **SHIPPED — bim-ootb PR #530** (branch `lane/outliner-bom-editor`, worktree `/tmp/wt-bomtree`): the ARC BOM editor in
  the modeller Outliner — 📂 Open icon loads any `extracted.db` → deep editable BOM Tree (building→storey→disc→class→
  element); re-parent by drag = signed `BOM_REPARENT`, geometry untouched; Outliner extended ADDITIVELY (`tree()`/
  `onReparent`), flag-gated `?bomtree`. **W-BOMTREE-OUTLINER 8/8**, reuses `bom_tree.js` **W-BOM-TREE-EDIT 15/15** +
  `factorizeInstancedZ` **W-TYPICAL-N 26/26** (committed on bim-compiler lane/benchmark-clash-resolution).
- **NEXT:** (1) headless browser smoke for `?bomtree` (load modeller, open db, assert tree renders + drag signs a
  BOM_REPARENT) → take it off the flag; (2) **scope editable nodes to ARC + present STR/MEP as follower-views** (the
  ARC-only principle); (3) land the opened ARC geometry into the modeller scene (selection-highlight needs it) +
  RouteWalk auto-completion demo; (4) axis handler (part 2) + align handler (part 3).

## VERDICT (2026-06-25): FORWARD, not square one
- **Graph frame = RIGHT** (user-endorsed pivot). **Edge substrate = REAL & committed** (prior): Path B `hosted-by`
  + `abuts` + `anchored-to` + `spans` + emergent datums, W-SDG-GRAPH on house AND bridge, zero invented, in
  `DAGCompiler/python/extractIFCtoDB.py`.
- **Wasted detour (this session only):** rebuilt the forward FOLD as `deploy/dev/sdg_fold.js` + wired to the VIEWER
  (`sdg_fold_ui.js`, `bake_sdg_edges.py`, `smoke_sdg_fold_live.js`, `witness_sdg_fold_ui.js`, viewer index.html
  edits + baked DB). REDUNDANT with shipped `GEOM_GRID_MOVE`/`GridKinematicEngine`, wrong track. Ignore/retire these.
- **On-track WIP:** `deploy/dev/bom_extract.js` `factorizeTypicalStoreys` (`instanced-by n`) — keep, finish (below).

## WHAT THE PRESENT MODELLER ALREADY SHIPS — do NOT rebuild
| Capability | Where |
|---|---|
| Move a placed object (signed) | `GEOM_MOVE` (W-BONSAI-MOVE 12/12, PR #423) |
| Drag a gridline → walls TRANSLATE / edge walls STRETCH / roof cascade (signed, undo) | `bonsai_gridmove.js` + `grid_kinematics.js` `GridKinematicEngine` → `GEOM_GRID_MOVE` |
| Gridlines derived from column cadence | `grid_dims.detectGrids` |
| Z-level planes / gridline drag / per-cut reposition | `grid_overlay.buildZGrids`, `grid_drag`, `grid_scissors` |
| Storey→discipline→class BOM tree, envelope, cadence, storeyHeights | `bom_extract.js` |
| Every edit is a signed op | `kernel_ops.js` op-log |

## THE USEFUL DIFF — what the GRAPH adds that the present modeller LACKS (ranked)
1. **`hosted-by` ride + door-fit RED.** GridKinematicEngine classifies WALLS vs gridlines; **openings are not modelled**
   — move/stretch a wall and its door/window does not ride it, and nothing flags a door that no longer fits. The
   recovered `rel_fills_host` edge is exactly "opening rides its host wall (1–2 DOF); host moved → door no longer fits
   → RED." This is the door-facing fix generalised. **Biggest concrete gap.**
2. **Backprop EXCEPTIONS (ORANGE/RED) — the "you may need to change" engine.** The modeller does forward cascade only;
   it raises NO dependency exceptions. The graph's backward signals (pegging = MRP exception) are net-new editing
   semantics: ORANGE "your opening may move / neighbour gap", RED "door-crush / UBBL min-dim / clash". **The headline
   differentiator** — no competitor folds model+exception from one signed log.
3. **Editing WITHOUT an authored grid + on non-grid constructs.** The modeller's cascade needs gridlines (columns or
   hand-drawn). The graph's datums EMERGE from face cadence (no IfcGrid) and `abuts`/`spans`/`anchored-to` are
   construct-agnostic (proven on a bridge: grid=0, storey=0). So editing generalises to bridges / shopfloors / loose IFC.
4. **`instanced-by n` + Z-span layer.** `bom_extract` is a FLAT storey tree (no n-factorisation, no Z-runner layer).
   The graph adds TYPICAL_STOREY × n (a storey = a product repeated n times) and Z-runners (risers/stairs/facade) whose
   extent = f(n) → collapse n→1 ⇒ they auto-vanish. (`CONSTRUCTION_GRID_BOM_DUAL_MODEL §SHELL-N-ZSPAN`.)
5. **`abuts` element↔element realign.** Grid cascade is grid-centric; the graph models direct neighbour adjacency
   (face-touch) → "neighbour pulled away → gap → ORANGE realign."
6. **Graph as an INDEPENDENT ORACLE.** Recovered edges (source IFC relations) let the model be CHECKED against an
   independent source — defeats the tree-only self-collusion (why `witness_drop_vs_java` was deprecated).

## STANDING DISCUSSIONS THAT MUST ANCHOR THE WORK (user-confirmed intact 2026-06-25)
- **ABSTRACT, never custom (the prime constraint).** Editing = transforming CAPTURED placements through a HANDLE;
  we **never build per-class verbs/recipes**. The door proved it: it was fixed by **deleting** custom treatment
  (`hasFront`/`DIRECTIONAL_ROLE_TOKENS`/`_inheritHostRotation`), not adding it → captured-yaw, class-agnostic,
  grep-clean, bridge-general. **There must be NO vicious circle of hand-correcting "hopeless doors"** — if a fix is
  per-class, it is wrong. Measure-don't-whitelist. [[RESUME_GIGO_FACING_TEST]] · spine §DOCTRINE.
- **The editing TRUNK = Find's facets promoted to edit-HANDLES** (`prompts/BOM_AS_OVERLAY_IDEA.md`, ADOPTED). The
  abstract overlay provides four handle sources — **Grids · Storey · Rooms · DISC** — and three already exist as the
  **Find panel's facets** (Storey/Rooms/Discipline): the overlay is "promote Find's faceting from *navigate* to
  *edit-handle*," NOT a new store. ⚠ **The Find panel lives in `viewer.html` — do NOT let that drift you into the
  viewer track.** REUSE its STRUCTURE inside the MODELLER as edit-handles; don't build in or deploy to the viewer.
- **SEMI-GRID = the derived/candidate grid handle** (user's term for the missing 4th facet). Non-invent: cluster
  ARC/STR column centroids + wall centerlines per axis → candidate gridlines; each line OWNS elements whose anchor
  snaps within tol. A *partial/implied* grid (not a hard authored IfcGrid) — same emergent-datum doctrine as the
  graph's `datum_plane`. This is the handle that makes "restretch a bay → translate the bound set by Δ" work, abstractly.

## NEXT (modeller track; signed ops into kernel_ops; oracle = raw extraction)
1. **Finish `instanced-by n`** — `factorizeTypicalStoreys` (done, in `bom_extract.js`) + **W-TYPICAL-N** (round-trip:
   `expand(TYPICAL×n)` == real per-class counts within tol; RED on force-merge) + **Z-span classifier**
   (W-ZSPAN-ABSTRACT, grep-clean of class names). Cheapest — all data already in `bom_extract`.
2. **`hosted-by` ride in the modeller** — wire the recovered `rel_fills_host` so an opening rides its host on
   `GEOM_GRID_MOVE`/`GEOM_MOVE`; add the door-fit **RED** constraint (real door width). High value, builds on Path B.
3. **Backprop exceptions (ORANGE/RED)** over the edges — thresholded, clamped, user-gated hop-by-hop; each accept = one
   signed op. The differentiator. (`SPATIAL_DEPENDENCY_GRAPH.md` Phase 3, W-SDG-BACKPROP.)

## RETIRE / IGNORE (this session's redundant viewer-track detour)
`deploy/dev/sdg_fold.js`, `sdg_fold_ui.js`, `scripts/bake_sdg_edges.py`, `scripts/smoke_sdg_fold_live.js`,
`scripts/witness_sdg_fold_ui.js`, `scripts/witness_sdg_forward.js`, the `deploy/dev/index.html` SDG edits + baked
`deploy/dev/buildings/SampleHouse_extracted.db` rows. Committed on `lane/benchmark-clash-resolution` (additive,
harmless, never deployed). The fold they implement is `GEOM_GRID_MOVE`. Keep only as reference; do not extend.
```
