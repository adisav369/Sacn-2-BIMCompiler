# BONSAI AUTHORING KERNEL — Research → Build Card

```
# ⚠ DO NOT REMOVE
SCOPE: Stand up a browser BIM geometry-AUTHORING module ("Bonsai part") by EMBEDDING a
reused B-rep kernel (occt-wasm) as a pure function `ops → B-rep → mesh`, with OUR signed
op-log (kernel_ops.js) as the parametric feature tree and geometry as a FOLD over it.
We do NOT write a geometry kernel. We do NOT lift AGPL app code (Chili3D / ifc5cad).
DOCTRINE: op-log = git-for-data, now over GEOMETRY as well as ERP records (one substrate).
LOG MANDATE: after ANY spike/witness run, read the output log before conclusions.
STATUS: RESEARCH DONE + ITEM 1 ✅ (W-KERNEL-FOLD) + ITEM 1b ✅ (W-KERNEL-SIGNED) — 2026-06-18.
Geometry folds over the REAL shipped signed op-log (kernel_ops.commitGroup), replay byte-identical,
tamper caught. + ITEM 1c ✅ (W-KERNEL-RENDER, occt→three.js draws) + SKETCH FRONT ✅ (W-SKETCH-SOLVE,
planegcs constraint solve → occt profile). WHOLE SPINE GREEN: sketch→solver→kernel→signed-log→render,
all in-browser on the MIT-shippable stack. Prior-art stamp LIVE: docs/ModellerKernelFold.md.
+ BOTH EDGE PROBES ✅ 2026-06-18: W-KERNEL-MANIFOLD (Manifold robust-BOP fallback, valid closed
2-manifold on coincident-face cut) + W-KERNEL-WEBIFC (real wall → 1 op-row → IFC4 write → re-import,
IfcExtrudedAreaSolid family round-trips exact). 6 witnesses GREEN.
+ ITEM 2 LEG 1 ✅ (W-BONSAI-VIEWER): occt kernel in a module Web Worker folds a kernel_ops op-row into
the PRODUCTION three.js scene on an ALTERNATIVE viewer (viewer/modeller.html, branch feat/bonsai-kernel-viewer
on bim-ootb) — present viewer.html untouched (user decree 2026-06-18).
+ ITEM 2 LEG 2 ✅ (W-BONSAI-SKETCH): interactive top-down planegcs sketch on the modeller — click points →
solver cleans the quad (auto H/V) → GEOM_EXTRUDE_POLY solid in scene.
+ ITEM 2 LEG 3 ✅ (W-BONSAI-RECIPE): op-log = feature tree; GEOM_CUT cuts a referenced parent solid;
pick-select + history-scrubber replay (commit wall → pick → cut child → scrub back/fwd deterministic).
+ ITEM 2 LEG 3b ✅ (W-BONSAI-SIGNED): geometry rides the SHIPPED SIGNED kernel_ops chain in-viewer — features
commit as signed op-groups (verifyChain ok), deterministic replay, tamper caught. ONE signed op-log backs ERP+geometry.
+ ITEM 3(c) ✅ (W-BONSAI-IFC): the authored signed op-log EXPORTS to standards IFC4 (IfcWall arbitrary-profile extrude +
IfcOpeningElement + IfcRelVoidsElement) via shipped web-ifc; re-import round-trips profile+depth. author→sign→export done.
11 witnesses GREEN. WHOLE in-viewer pipeline proven: sketch→solve→sign→fold→render→export. Next = Item 3: void-sketch / picking silhouette / regen cache / DEPLOY (slim occt wasm first).
+ DEPTH TRACK LEG 1 ✅ 2026-06-18 (W-BONSAI-SWEEP): occt SWEEP shoulder (kernel.pipe — ALREADY exported in
lib/kernel/index.js, ZERO binding work) wired as ONE worker op-type GEOM_SWEEP (rectangle profile swept along a
polyline spine). Rides the signed op-log like any feature: tris=16, bbox spans the L-path, verify ok, signed=1,
scrub deterministic, tamper caught. Confirms the "shoulders" (fillet/chamfer/sweep/loft/draft/shell all present
in the binding) are reachable with worker-only wiring via the GEOM_CUT recipe. Probe fan-out done this session
(4 read-only probes: occt-ops · planegcs ~60 constraints · grid_kinematics.dragGrid pure fn · op-log add-op seam).
+ RESPONSIVENESS ✅ 2026-06-18 (W-BONSAI-FASTAUTHOR; user: "not that instant when clicking a door, extrude etc").
ROOT CAUSE: every commit/scrub re-folded EVERY feature through occt (foldChainToScene clear+re-eval-all = O(N)),
and every pick rebuilt the whole Outliner (DB query + DOM). FIX (cheap, correct): (1) LEAF adds (extrude/sweep/
poly) render via O(1) optimistic single-op author+append (commit returns appended=true) — deterministic fold ⇒
optimistic mesh == chain-fold mesh, GEOM_CUT still takes the authoritative full fold, scrub reconciles;
author() now tags featureId so the appended mesh stays pick-selectable. (2) Outliner.setActive restyles the
active row in place (no rebuild) on pick. No regression (SIGNED/RECIPE/OUTLINER/GRID GREEN). The PROPER fix =
op_hash-keyed incremental regen cache (the signed op_hash is a free perfectly-invalidating key) = the §4#1
"real core" / §9e card, Onshape-patent-adjacent (§6) — NOT built; needs a deliberate go.
14 in-viewer witnesses GREEN. Branch feat/bonsai-kernel-viewer PUSHED (worktree /tmp/wt-bonsai).
```

## §DOCTRINE — modeller is PERMANENTLY SEPARATE from the Viewer (user 2026-06-18)
The Bonsai modeller and the production Viewer are kept **permanently separate surfaces** — NOT merged.
The modeller's Outliner is an **AUTHORING tree only**: it carries the feature tree (Walls/Openings/Routes…)
and deliberately **does NOT carry 4D/5D info** (schedule/cost/etc.). 4D/5D is the **Viewer's** job after a
**handoff** of the authored model. This separation of concern is deliberate so the modeller can mature to
seriously take on the authoring challenge to its best, without being pulled toward viewer responsibilities.
(Supersedes the earlier "modeller may eventually deprecate/cannibalize the viewer" musing — they stay distinct.)

## §OOTB — the end-state roadmap (user-confirmed 2026-06-18)
Reaching "Bonsai OOTB" is NOT just kernel depth (more ops / regen cache) — that's the expert sketch-every-wall
path. OOTB = **3 authoring modes folded onto the ONE signed op-log**, all landing in outliner/history/IFC:
1. **Sketch-from-scratch** (DONE — planegcs→extrude→cut).
2. ✅ **DONE/LIVE 2026-06-18 — `W-BONSAI-INSERT` PASS (PR #377, GH Pages).** Insert library component @ LOD (THE bulk of
   real authoring — assemble, don't draw). `bonsai_library.js` = catalog + host-side fold (a baked mesh, NOT a B-rep →
   filtered out of the occt worker in `foldChainToScene`); 3 REAL components extracted NON-INVENT from
   `library/component_library.db` (Column/Beam/Door — bbox + real mesh blobs, base64; full 23888-row db via httpvfs =
   follow-on). Each insert = ONE signed `GEOM_INSERT` op-row (placement+component ref+lod). LOD = a RENDER override over the
   IMMUTABLE signed row: LOD-200 = bbox box proxy (12 tris) → LOD-300 = real mesh (52 tris) = "same row refined"
   (op_hash/chain-tip unchanged). modeller.html: Insert btn + component picker + click-place + LOD toggle + Components
   Outliner category (addCategory seam). Witness: assemble Door→signed=1 verify=true→LOD200(12)→LOD300(52, len+tip
   unchanged)→scrub 0/52 deterministic→tamper caught→drew 8.2%. No regression (RECIPE/SIGNED/ROUTE/GRIDMOVE PASS).
3. **RouteWalker → MEP sweep** (a route is a path; an MEP run = profile swept along it = occt sweep/loft, already in the kernel,
   unexposed). Existing RouteWalker + spatial-picking = the MEP input device; each run = one op-row.
Plus: the **common Viewer PillBuilder rail ON the modeller** (pull the deferred chrome-convergence forward — it hosts the
toolbar extras, the Sound toggle, and the Find→ERP bridge); **authoring audio feedback** (DONE — Sound toggle, carries
Morpheus red-pill audio in); and the **ERP join** (Outliner → Project→Order over the same signed log = the payoff).
Eng plumbing underneath: **unify the +Wall/+Opening quick buttons through the signed op-log** (today they single-op author
into the scene only → bypass outliner/history/IFC, pick gives featureId=undefined) · incremental-regen cache · real
`picking.js focusElement` silhouette · slim occt wasm. SHIPPED 2026-06-18: modeller LIVE on GH Pages + Morpheus wiring (PR #370/#371/#372).

## ✅ DEPTH TRACK — ALL 4 SUGGESTED LEGS DONE 2026-06-18 + DEPLOYED/LIVE (PR #376, GH Pages)
**DEPLOYED 2026-06-18 — PR #376 squash-merged → main (`d888281`) → deploy-pages.yml minify+deploy SUCCESS → LIVE.**
Re-verified GREEN before merge (4 depth witnesses + SIGNED/RECIPE/SKETCH/IFC regression, all PASS, logs `/tmp/bonsai_*.log`).
Live smoke: https://red1oon.github.io/bim-ootb/viewer/modeller.html → 200; Route/Fillet/Chamfer/Constrain/Move-Grid +
GEOM_SWEEP/GEOM_FILLET/GEOM_GRID_MOVE all present in served page; `bonsai_gridmove.js` 200. The 4 legs are now reachable+usable.
Built leg-by-leg, witness-first, on the mapped shoulders. **15 in-viewer witnesses GREEN** (4 new + 11 prior, no regression).
- **Leg 1 ✅ W-BONSAI-ROUTE** — GEOM_SWEEP wired to a Route toolbar button + interactive ground-plane path-pick (was
  engine-only `?sweep=demo`). Worker GEOM_SWEEP generalized to orient the profile PERPENDICULAR to the spine start
  tangent, centred on path[0] (T=+Z reproduces the old XY profile → `?sweep=demo` byte-identical). Outliner Routes category.
- **Leg 2 ✅ W-BONSAI-FILLET** — occt fillet/chamfer via the new EDGE-PICK input device. Worker refactor foldChain→buildSolids;
  GEOM_FILLET (round|chamfer kind) is a parent-mutating op; `listEdges` worker msg reports edge midpoints in canonical
  getSubShapes order (stable across re-fold); host `queryEdges` + edge-marker pick mode + Apply. Outliner Fillets category.
- **Leg 3 ✅ W-BONSAI-CONSTRAIN** — richer planegcs (was only H/V, 2 of ~60): each ring edge → line primitive, then a
  Constrain cycle button (Axis→Rect→Square) applies parallel + perpendicular_ll + equal_length; p2p_symmetric_ppp proven.
  Noisy quad → rect (0° corners) → square (unit side ratio).
- **Leg 4 ✅ W-BONSAI-GRIDMOVE** — the §S270 parametric-grid payoff: `bonsai_gridmove.js` adapts authored solids→engine
  elementData + authoring grid→gridLines, runs the PURE `GridKinematicEngine.dragGrid`, commits ONE signed GEOM_GRID_MOVE.
  Move-Grid mode drags a gridline (controls off during grab) → ATTACH walls TRANSLATE, EDGE/SPAN walls STRETCH (non-uniform
  via `generalTransform`/gp_GTrsf — plain `transform`/gp_Trsf collapses an axis stretch to a uniform det^(1/3) scale!).
  Outliner Grid Moves category; oplog leaf-detection now a positive whitelist (CUT/FILLET/GRID_MOVE take the authoritative re-fold).
- **REGEN CACHE — CORRECTION, this line was stale:** actually DONE/LIVE same day (PR #382, `W-BONSAI-REGEN`, see §4#1
  below — op_hash-keyed `shapeCache`, verified live in `bonsai_kernel_worker.js` as of 2026-07-07). This line
  originally said "still not built, decision pending" — that was written before §4#1 landed later the same day and
  was never updated. Don't re-flag the regen cache as an open decision; it's shipped.
- NOT YET DEPLOYED: these legs are on the branch, not merged to bim-ootb main → not live on GH Pages (see [[feedback_modeller_deploy_branch]]).

## ▶▶ NEXT SESSION = CONNECT SCENE → `prompts/CONNECT_SCENE_SPEC.md` (user idea + agreed take, 2026-06-18)
Shared CROSS-SURFACE CONTEXT (Modeller ⇄ Viewer ⇄ ERP) — NOT a merge; share selection / timeline / identity over
the ONE signed op-log via a thin broker (`connect_scene.js`), generalizing the SHIPPED `bim:*` postMessage selection
bus + reusing `kernel_ops` (shared) + `universal_history.js`. Build phases P0 broker+toggle → P1 selection → P2
timeline (killer demo: scrub before a feature → its geometry AND ERP record both vanish) → P3 commit, each
witness-first. Honors the permanent-separation doctrine (context only, never chrome). SPEC ready, nothing built yet.

## ⚙ OPERABILITY TRACK — "fix the basic core modelling issues" (user 2026-06-18)
Code-grounded audit (4 Explore agents) found ~30 distinct operability gaps (6 blockers): the modeller was a
proven ENGINE with a THIN COCKPIT. Fixing leg-by-leg, witness-first, deployed.
- **v1 LIVE (PR #378)**: **W-BONSAI-PLACE** — placement correctness: GROUND-SEAT (door was half-buried at z=0,
  local z −1.05..1.05 → now sits 0..2.1; FIXED a real bug: boxArrays mis-mapped bbox axes since #377), rotate
  (R/button 90°), elevation (numeric), live ghost preview. **W-BONSAI-EDIT** — delete-one (+cascade children),
  undo/redo (LIFO) via the `undone` flag (NOT in signed payload → verifyChain stays valid). Toolbar Undo/Redo/
  Delete + Ctrl+Z/Y/Del.
- **v2 LIVE (PR #379)**: **W-BONSAI-PERSIST** — op-log autosaves to localStorage, restores+re-folds on boot
  (byte-faithful, same chain tip); fixes "reload loses everything". **W-BONSAI-UX** — Escape cancels any mode,
  Backspace undoes last sketch/route point, history timeline LABELS each step ("2/2 · Door" + hover list).
- **v3 LIVE (PR #380)**: **W-BONSAI-GMPREVIEW** — Move-Grid preview: hover highlights the grabbable line; during
  drag the affected walls tint live (blue=translate, orange=stretch) via pure computeCommands, nothing commits till
  release, tints clear (closes user's "move grid" pain). **W-BONSAI-NUMDIM** — typed wall height / sweep profile /
  fillet radius via contextual inputs (was hardcoded 3m/0.3m/0.1m).
- **v4 LIVE (PR #381)**: **W-BONSAI-CAMERA** — Fit (key F, frames selection or all at sane distance) + View cycle
  Iso/Top (camera.up stays Y so the sketch plan view isn't degenerate → Front omitted by design).
- **BASIC CORE = DONE.** User's 3 named pains all ✅ (door, timeline, move-grid). 8 operability legs across 4 deploys.
- **REMAINING (v5, ADVANCED not basic-core)**: vertex/edge snapping to existing geometry, real 23888-row catalog
  (httpvfs range-load), branch/redo history tree, minors (mode cursor, component search/filter). Bigger (L each),
  diminishing returns — pick up only on demand.
- **⭐ DIRECT-MANIPULATION LANE (user 2026-06-19, "be competitive against other modellers") → NEW CARD
  `prompts/MODELLER_DIRECT_MANIPULATION.md`.** The missing manipulation half of v5: there is a SELECT but no
  MANIPULATE — axis-drag MOVE gizmo + snap (L1, the #1 verb), lasso/marquee MULTI-select (L2), snap-to-geometry
  (L3, subsumes the vertex/edge-snap noted above), rotate/scale handles (L4). Code-grounded current-state audit +
  witness-first claims (W-BONSAI-MOVE/-MULTISELECT/-SNAPGEOM) in that card. THIS is the UI-competitive spine.
  ⭐ START WITH §0 SURVEY: research the whole UI-facing area FIRST (inward code audit × named-competitor benchmark
  × be-the-user walk → ranked punch-list), exactly as the engine got this operability audit. Not build-first.

## SPEC — W-BONSAI-INSERT (§OOTB Item 2: Insert library component @ LOD) — 2026-06-18
**Issue proved:** a REAL library component can be ASSEMBLED into the model (not drawn) as ONE signed op-row
(`GEOM_INSERT`), rendered at a chosen LOD, landing in the Outliner; LOD-200→LOD-300 = the SAME signed row refined
(render fidelity over an immutable signed feature — op_hash/chain-tip unchanged), geometry still a deterministic
fold over the signed op-log. This is the OOTB "bulk of authoring" mode (Revit-family insert/replace/history-free).
**NON-INVENT data:** 3 real components extracted from `library/component_library.db` (Column/Beam/Door — bbox + real
mesh blobs, base64) embedded as `bonsai_library.js` fixture catalog. Blob format (verified): vertices=Float32 (3/vtx),
faces=Uint32 (3/tri), normals nullable — direct THREE.BufferGeometry shape. (Full 23888-row db via httpvfs range-load
= production follow-on; witness uses a real fixture, the lane's standard method à la W-KERNEL-WEBIFC.)
**Fold (HOST-side, NO occt):** GEOM_INSERT is a baked-mesh placement, not a B-rep → `foldChainToScene` filters
INSERT ops out of the worker batch and builds their meshes via `Bonsai.library.foldInsert` (LOD-200 = bbox box proxy
=12 tris; LOD-300 = decoded real mesh) transformed by placement{x,y,z,rotDeg-about-Z}. It is NOT in the oplog LEAF
whitelist → naturally takes the authoritative `_foldUpto` re-fold (no worker call). LOD = a render override
(`Bonsai.library.setLod(fid,'300')` + re-fold) so the signed row is untouched ("same row refined").
**Files:** `bonsai_library.js` (catalog+provider) · `bonsai_kernel.js` (fold branch) · `bonsai_outliner.js`
(unchanged; Components category registered in modeller.html via addCategory seam) · `modeller.html` (Insert toolbar
btn + component picker + click-place + LOD toggle + `?insert=demo`) · `viewer/tests/bonsai_insert_live.js`.
**Witness asserts:** insert real Door @ LOD-200 → signed=1 verify=true tris=12 (box) → Components outliner row →
setLod 300 + re-fold → tris=52 (real mesh) AND length unchanged AND tip unchanged (same row refined) → scrub
0/1 deterministic → tamper committed param → verify fails. Drew pixels.

## ▶▶ NEXT-SESSION DIRECTIVE (user 2026-06-18) — BUILD ON THE SHOULDERS (calculate-ahead with agents)
The spine is proven and the Morpheus plate is LIVE (bim-ootb PR #373, https://red1oon.github.io/bim-ootb/index2.html).
NEXT SESSION = **seriously build the Bonsai authoring modeller on the shoulders it already stands on** — the DEPTH
track. User redirected here, AHEAD of the slim-occt/deploy track (§8) which now follows the depth legs. The shoulders,
currently UNEXPOSED in the modeller:
- **occt** (in the shipped kernel): fillet · chamfer · sweep/pipe · loft/thruSections · draft · shell/makeThickSolid
- **planegcs** (in the shipped solver): tangent · parallel · perpendicular · symmetry · equal · angle (we use only H/V)
- **our grid subsystem**: `grid_drag` + `grid_kinematics` — drag a gridline → attached geometry RECOMPOSES; relations ATTACH/SPAN/EDGE.

**✅ PROBE FAN-OUT DONE 2026-06-18 — DO NOT RE-PROBE. Build directly from this mapped inventory** (4 read-only
Explore agents, NON-INVENT, file:line-anchored):
- **occt shoulders — ALL already exported in `viewer/lib/kernel/index.js`, ZERO binding work, worker-only wiring:**
  `fillet(solid,edges,radius)` :262 · `chamfer(solid,edges,distance)` :267 · `chamferDistAngle` :272 ·
  `shell(solid,facesToRemove,thickness,tolerance)` :286 · `offset` :298 · `draft(shape,face,angleRad,dir)` :301 ·
  `pipe(profile,spine)` :307 (USED by GEOM_SWEEP) · `loft(wires,isSolid,ruled)` :320 · `sweep(wire,spine,mode)` :330 ·
  `revolve(shape,axis,angleRad)` :257 · `thicken` :1075 · `filletVariable` :1096. Mesh = `kernel.tessellate(shape)` :632.
  Vec3 = `{x,y,z}` object (NOT array). EDGE-LIST ops (fillet/chamfer/shell) need a way to pick edges/faces — that's
  the only non-trivial input plumbing; everything else takes explicit point/param arrays like GEOM_SWEEP.
- **planegcs — ~60 constraints in `viewer/lib/planegcs/planegcs_dist/constraint_param_index.js`, we wire only 2:**
  add any via `gcs.push_primitive({type,id,...})` (gcs_wrapper.js), solve `GcsWrapper.solve()` :95, reset `clear_data()` :37,
  MAIN-thread. Names: `parallel` :91 · `perpendicular_ll` :97 · `tangent_lc/le/la/cc/aa/ca` :333+ · `p2p_distance` :36 ·
  `p2p_angle` :51 · `equal_length` :393 · `p2p_symmetric_ppl/ppp` :435 · `circle_radius` :369 · `p2p_coincident` :193.
- **grid — `GridKinematicEngine.dragGrid(gridId,delta)` grid_kinematics.js:409 is a PURE fn** → returns recompose cmds
  `[{guid,action:TRANSLATE|SCALE|ROOF_*,...}]`; relations ATTACH/SPAN/EDGE_LEFT/EDGE_RIGHT (:175-197). `grid_state.js`
  standalone label-keyed. `grid_recompose.applyDrag(A)` :323 is VIEWER-COUPLED glue (queries element_transforms + mutates
  THREE instance matrices) → modeller needs an adapter feeding Bonsai's authored elements as `elementData`, commit one
  `GEOM_GRID_MOVE` op per drag-release.
- **add-a-new-GEOM_* recipe (from GEOM_CUT/GEOM_SWEEP):** ONLY required touch = a case in `bonsai_kernel_worker.js`
  `applyFeature()` (leaf, returns a shape) or `foldChain()` (if it mutates a referenced parent in place). `oplog.commit()`
  auto-signs via `KernelOps.commitGroup` + auto-folds; `foldChain` picks the op up for scrub automatically. IFC export
  (`bonsai_ifc.js build()` switch) is OPTIONAL. Witness = puppeteer-Chromium `?hook=demo` + `waitForFunction` + readPixels.
  Reuse `viewer/tests/bonsai_sweep_live.js` / `bonsai_fastauthor_live.js` as templates.

**METHOD:** build leg-by-leg, witness-first (each capability = ONE signed `GEOM_*` op = deterministic fold, scrub +
tamper-evident, landing in outliner + IFC where sensible). SUGGESTED ORDER: (1) wire GEOM_SWEEP to a toolbar button +
interactive path-pick (it's engine-only today, `?sweep=demo`); (2) GEOM_FILLET/CHAMFER (needs edge-pick UI = the new bit);
(3) richer planegcs constraints on the sketch (parallel/perp/equal/symmetry); (4) GEOM_GRID_MOVE (gridline-drag-recompose
via the dragGrid pure fn + a modeller adapter). DECISION PENDING (user go): the op_hash-keyed incremental regen cache (§4#1
real-core, patent-adjacent §6) — the proper fix to ALL fold latency, beyond this session's O(1) optimistic-append.

**Grounded pointers (verified 2026-06-18):**
- occt JS bindings = `/tmp/wt-bonsai/viewer/lib/kernel/{occt-wasm,index,types,raw-types}.js` (wasm present in the worktree)
- planegcs = `/tmp/wt-bonsai/viewer/lib/planegcs/` (index.js, planegcs_dist, sketch)
- grid = `~/bim-ootb/viewer/{grid_kinematics,grid_drag,grid_recompose,grid_assembler,grid_state}.js`
- op-log seam = `/tmp/wt-bonsai/viewer/{bonsai_kernel,bonsai_kernel_worker,bonsai_oplog,bonsai_outliner,bonsai_ifc}.js`
- worktree `/tmp/wt-bonsai`, branch `feat/bonsai-kernel-viewer`; spikes `~/bim-compiler/build/kernel/poc_kernel_*.js`

## ▶ RESUME — NEXT SESSION (read this first)
**Where we are (2026-06-18):** the whole authoring spine + both edge probes are proven leg-by-leg, in-browser,
on the MIT-shippable stack. 6 witnesses GREEN (re-run any: `node build/kernel/poc_kernel_<fold|signed|render|sketch|manifold|webifc>.js`):
- W-KERNEL-FOLD (extrude+boolean = deterministic fold over an op-row)
- W-KERNEL-SIGNED (geometry rides the SHIPPED signed op-log `kernel_ops.commitGroup`; tamper-evident)
- W-KERNEL-RENDER (occt mesh → three.js → draws pixels)
- W-SKETCH-SOLVE (planegcs constraint solver → occt profile)
- W-KERNEL-MANIFOLD (Manifold robust-BOP fallback: valid closed 2-manifold, status=NoError, genus=0,
  Euler-closed, exact volume on a coincident/flush-face subtraction — `manifold-3d@3.5.1`, Apache-2.0,
  import `/node_modules/manifold-3d/manifold.js`. Manifold Vec3 = ARRAY `[x,y,z]` (occt = `{x,y,z}` object).
  HONEST CAVEAT: on the test input OCCT `cut` ALSO survived — the witness proves Manifold's guaranteed-clean
  fallback, NOT OCCT failing; a genuinely OCCT-mangling input is a stronger follow-on if ever needed.)
- W-KERNEL-WEBIFC (real wall → 1 op-row → IFC4 write → re-import, round-trips exact. Source = REAL wall
  `reference/residential/Ifc4_WallElementedCase.ifc` "Drywall" panel: `IFCRECTANGLEPROFILEDEF(96.,48.)`
  + `IFCEXTRUDEDAREASOLID(...,0.5)` +Z. `web-ifc@0.0.77`, plain `--no-sandbox` (no WebGL/COOP needed → GH-Pages-safe).
  Schema enums live under per-schema obj: `WebIFC.IFC4.IfcProfileTypeEnum.AREA`, NOT top-level. Round-tripped:
  ExtrudedAreaSolid/RectangleProfileDef/Axis2Placement/Direction/CartesianPoint/Wall/ShapeRepresentation.
  Dropped by-design: Psets, Materials, OwnerHistory, SpatialContainment, Styling.)
Prior-art stamp LIVE + verified 200: https://red1oon.github.io/BIMCompiler/ModellerKernelFold/
occt-wasm + @salusoft89/planegcs + manifold-3d + web-ifc in repo devDeps (package.json gitignored —
re-`npm i -D occt-wasm @salusoft89/planegcs manifold-3d web-ifc` on a fresh clone).

**Do next (in order):**
1. ✅ DONE — Edge probes (W-KERNEL-MANIFOLD + W-KERNEL-WEBIFC, see above).
2. **Item 2 leg 1 ✅ DONE 2026-06-18 — `W-BONSAI-VIEWER` PASS.** DECISION (user 2026-06-18): the modeller is built
   on an **ALTERNATIVE viewer** (`viewer/modeller.html`), NOT impacting the present `viewer.html`. occt-wasm kernel
   hosted in a **module Web Worker** (`viewer/bonsai_kernel_worker.js`) folds a kernel_ops op-row → mesh; host
   `viewer/bonsai_kernel.js` (`window.Bonsai`, lazy worker spawn so the 22MB wasm loads only on first author())
   builds a THREE.BufferGeometry into `A.scene` on the PRODUCTION three.js stack. Witness `viewer/tests/bonsai_kernel_live.js`
   (puppeteer/SwiftShader): wall `tris=12 bbox=[0,0,0,4,0.2,3]`, opening cut `tris=32`, 2 meshes in scene, `litPixels=7.7%`.
   **Branch `feat/bonsai-kernel-viewer` on bim-ootb (worktree `/tmp/wt-bonsai`), PUSHED.** occt dist vendored to
   `viewer/lib/kernel/` (JS committed; `occt-wasm.wasm` gitignored = slim-build follow-up per §1). `OcctKernel.init()`
   auto-locates the co-located wasm; THREE shim = overlay the STANDARD `three.module.min.js` (webgpu bundle ships
   only WebGPURenderer) + copy the frozen ESM namespace into a mutable object (same as loader.js §S276).
3. **Item 2 leg 2 ✅ DONE 2026-06-18 — `W-BONSAI-SKETCH` PASS.** Interactive top-down 2D sketch on the modeller:
   click points on the XY plane → the planegcs constraint solver (LGPL, vendored to `viewer/lib/planegcs/`,
   `planegcs.wasm` 500K COMMITTED) cleans the hand-drawn quad via auto horizontal/vertical constraints (`horizontal_pp`/
   `vertical_pp`, p0 fixed anchor) → the SOLVED profile drives a real occt solid via a new `GEOM_EXTRUDE_POLY` worker op
   (`makeLineEdge→makeWire→makeFace→extrude`, same path as W-SKETCH-SOLVE). Files: `viewer/bonsai_sketch.js`
   (`window.Bonsai.sketch`, planegcs runs MAIN-thread, re-solve via `clear_data()`), `modeller.html` ✎ Sketch mode
   (raycast clicks to ground, marker+line preview, ⬆ Extrude) + `?sketch=demo` hook, `viewer/tests/bonsai_sketch_live.js`.
   Witness: noisy `[[0,0],[4,.15],[3.9,3],[.1,2.9]]` → solver status=0 → clean `[[0,0],[4,0],[4,3],[0,3]]` (4/4 axis edges,
   snapped), solid tris=12 bbox=[0,0,0,4,3,3] in scene, litPixels=20.1%; leg 1 still PASS (no regression). 8 witnesses GREEN.
4. **Item 2 leg 3 ✅ DONE 2026-06-18 — `W-BONSAI-RECIPE` PASS.** The modeller's op-log IS the feature tree;
   geometry = deterministic fold over it. Worker `foldChain(ops)` folds an ordered op-log → live solids; new
   `GEOM_CUT` op modifies its REFERENCED parent solid in place (child-of-wall, opening-on-a-picked-wall). Host:
   `bonsai_kernel.js foldChainToScene` (clear+re-fold = replay primitive), `bonsai_oplog.js` (`window.Bonsai.oplog`:
   append/commit/`scrubTo(k)`), `bonsai_sketch.js commitExtrude` (sketched wall = feature). `modeller.html`: pick-select +
   emissive highlight (`Bonsai.select`), ✂ Cut (window void from selected wall bbox → GEOM_CUT child), history slider
   (`scrubTo` on input), `?recipe=demo` hook; witness `viewer/tests/bonsai_recipe_live.js`. Witness: commit wall (tris=12)
   → pick → cut child (tris=32) → scrub back (12, hole gone) → scrub fwd (32, hole back) = DETERMINISTIC replay; leg1/leg2
   no regression. **9 kernel witnesses GREEN.** Single-op `{op}` worker path kept (leg1/2 back-compat). CAVEAT: pick uses
   a local raycast+emissive highlight; full `picking.js focusElement` silhouette reuse needs the viewer's picking infra.
5. **Item 2 leg 3b ✅ DONE 2026-06-18 — `W-BONSAI-SIGNED` PASS.** Geometry now rides the SHIPPED SIGNED op-log
   in-viewer (closes the doctrine loop proven headless by W-KERNEL-SIGNED). `bonsai_oplog.js` backs the feature tree
   with `window.KernelOps` (shipped `viewer/kernel_ops.js`, v8 W-CHAIN/W-SIGN/W-OPGROUP) on a sql.js db: each `commit()`
   folds the feature into the `kernel_ops` hash chain as a signed op-group (`commitGroup` + edge signer over `op_hash`),
   `verifyChain` ok, scene = deterministic fold of the VERIFIED log; `parent` ref rides in op `parameters`; scrub folds
   the GEOM prefix. `modeller.html` loads `lib/sql-wasm.js`+`kernel_ops.js`, `?signed=demo` hook; witness
   `viewer/tests/bonsai_signed_live.js`: wall+cut signed (signed=2), verify ok, scrub 12→32→12→32 deterministic, tamper a
   committed param → verify fails 'group torn'; legs 1/2/3 no regression. **10 kernel witnesses GREEN.** ONE signed op-log
   now backs BOTH ERP records and BIM geometry, fully in-browser.
6. **Item 3(c) ✅ DONE 2026-06-18 — `W-BONSAI-IFC` PASS.** Author → sign → EXPORT complete. `bonsai_ifc.js` turns the
   op-log GEOM features into a standards IFC4 model via the shipped web-ifc (`lib/web-ifc-api-iife.js`): GEOM_EXTRUDE_POLY →
   IfcWall + IfcArbitraryClosedProfileDef (solved sketch polygon) extruded; GEOM_CUT → IfcOpeningElement (void prism) +
   IfcRelVoidsElement to parent wall. ⤓ IFC button downloads `.ifc`; `?ifc=demo` hook; witness `viewer/tests/bonsai_ifc_live.js`:
   build (walls=1 openings=1 rels=1, 1727 bytes) → re-import counts match + wall profile polygon + extrude depth round-trip
   exact (depth read tied to the arbitrary-closed-profile solid, not the rect void). Honest scope: geometry + wall/opening +
   voids; Psets/materials/owner-history/spatial-containment/styling dropped. **11 kernel witnesses GREEN.**
7. **Item 3 — 2D GRID CORRELATION ✅ DONE 2026-06-18 — `W-BONSAI-GRID` PASS** (user's original 2D-grid-overlay idea).
   Brought the shipped column-grid concept (`grid_dims`/`grid_overlay`) into the modeller as an AUTHORING substrate:
   in the VIEWER the grid is DETECTED from a model, in the MODELLER it's the INPUT the design correlates to. `bonsai_grid.js`
   (`window.Bonsai.grid`): `define(xs/ys + A/B/C×1/2/3 labels)`, render gridlines+label sprites on the XY sketch plane,
   `snap(x,y)` pulls clicks onto gridlines, `refAt()` reports the grid ref. ▦ Grid toggle; sketch pointerdown snaps through it.
   Witness `viewer/tests/bonsai_grid_live.js`: noisy clicks → exact grid coords `[[0,0],[4,0],[4,3],[0,3]]` with refs A-1/B-1/B-2/A-2,
   wall lands on grid (signed). **12 kernel witnesses GREEN.** ADVANCED-TOOLING NOTE (the "shoulders"): occt has fillet/chamfer/
   sweep/loft/draft/shell (we use extrude+cut only); planegcs has tangent/parallel/perp/symmetry/equal/angle (we use H/V only);
   OUR grid subsystem has `grid_drag`+`grid_kinematics` (drag a gridline → attached geometry RECOMPOSES, relations ATTACH/SPAN/EDGE).
8. **Bonsai-faithful Outliner ✅ DONE 2026-06-18 — `W-BONSAI-OUTLINER` PASS** (thin scope, deliberate — see COST CALL below).
   Blender-style left panel (`bonsai_outliner.js` = `window.Bonsai.outliner`) driven by the SIGNED op-log: `Bonsai Model` >
   category groups (Walls/Openings) > nodes with grid refs (`Wall A-1·B-1`, `Opening ⮡ Wall 1`); Find box filters across
   categories; click row → select + mirror to 3D pick; footer shows the signed chain tip. CATEGORY-DRIVEN BY REGISTRATION
   (`addCategory` seam) so Room/Phase + ERP Project→Order categories slot in later (user direction) WITHOUT forking the
   viewer's Find. Auto-refresh via `bonsai:oplog` event. Screenshot: `~/Pictures/Screenshots/bonsai_modeller_outliner.png`.
   **13 kernel witnesses GREEN.** ⚖ COST CALL (user asked to weigh, agreed): banked ONLY the thin Outliner; did NOT fork the
   viewer's Find/PillBuilder/grid (would = double-maintenance + drift on an undeployed artifact). Full Bonsai chrome (PillBuilder
   rail) + Find-retrain (Room/Phase→ERP) + viewer-deprecation = a deliberate SHARED-MODULE program for LATER, driven by real
   usage after deploy. NEXT TRACK = slim occt build → DEPLOY (make it reachable) before more chrome.
9. **Item 3 remaining (later)** — (a) **gridline-drag recompose** via `grid_kinematics` (drag grid A → walls on it move — the
   advanced parametric-grid payoff); (b) richer constraints/ops (planegcs tangent/symmetry, occt fillet/sweep); (c) sketch the
   opening void interactively; (d) `picking.js focusElement` silhouette; (e) incremental-regen cache (§6); (f) DEPLOY (slim occt
   wasm first per §1; planegcs 500K + web-ifc already in viewer). Pipeline sketch→snap→solve→sign→fold→render→export proven in-viewer.
**Reuse the harness pattern** in `build/kernel/poc_kernel_*.js` (puppeteer-Chromium; Node18 can't host the
kernel — V8 too old for WASM tail-calls). occt Vec3 = `{x,y,z}` not array. WebGL needs `--use-angle=swiftshader`.

## ✅ ITEM 1 — SPIKE (GO/NO-GO): DONE 2026-06-18 — W-KERNEL-FOLD PASS
Foundation stands. Files: `build/kernel/spike.html` (in-page fold) + `build/kernel/poc_kernel_fold.js`
(puppeteer-Chromium harness) → log `build/kernel/poc_kernel_fold.log`. Run: `node build/kernel/poc_kernel_fold.js`.
- occt-wasm added to repo devDeps; **wasm 22MB raw** (slim custom build = follow-up for load budget).
- **EDGE FOUND:** occt-wasm needs WASM tail-calls → **Node 18 (V8 10.2) CANNOT host it**; harness must
  drive **puppeteer Chromium 147** (already a repo dep, matches `_live` witness pattern).
- **EDGE FOUND:** occt-wasm is **single-thread, 0 SharedArrayBuffer** → **no COOP/COEP** → will run on GH Pages.
- **EDGE FOUND:** kernel `Vec3 = {x,y,z}` (interface, NOT array) — passing arrays silently degenerates a box.
- §-witness (logged):
  - `init ok loadMs=105` (tail-calls run in Chromium)
  - `live tris=12 bbox=[0,0,0,4,0.2,3] cs=1782029157` — extrude = IfcExtrudedAreaSolid
  - `replay … cs=1782029157 identical=Y` — **replay from serialized op-row is byte-identical = fold proven**
  - `opening tris=32 cs=3687298821 deterministic=Y changedVsWall=Y` — boolean cut (IfcRelVoidsElement) folds too
- NOTE: this spike serializes the feature in the kernel_ops ROW SHAPE (op_type + JSON params) but does
  NOT yet route through the SIGNED chain. That is Item 1b below.

## 5b. Build legs (post-spike, ordered)
- ✅ **Item 1b DONE 2026-06-18 — `W-KERNEL-SIGNED` PASS** (`build/kernel/spike_signed.html` +
  `poc_kernel_signed.js` → `.log`). Geometry rides the SAME shipped signed op-log as ERP records:
  - bim-ootb checked first — occt authoring kernel + geometry op-log fold do NOT exist there (not a redo);
    `kernel_ops` (commitGroup/verifyChain) IS shipped = reused, geometry-fold is the new layer.
  - `commit committed=Y gid=geom-grp-1 ops=2` via the SHIPPED `kernel_ops.commitGroup`; `signed-rows=2/2`
    (W-SIGN edge signer); `verify ok=Y len=2`.
  - `replay[0] GEOM_EXTRUDE cs=1782029157 identical=Y` + `replay[1] GEOM_OPENING cs=3687298821 identical=Y`
    — replay FROM the signed log is byte-identical = fold over the signed log.
  - `tamper detected=Y brokeAt=1 why=group torn` — mutating a committed param breaks the chain (tamper-evident).
  - This is the central claim of `docs/ModellerKernelFold.md`, now proven on the real engine.
- ✅ **Item 1c DONE 2026-06-18 — `W-KERNEL-RENDER` PASS** (`build/kernel/spike_render.html` +
  `poc_kernel_render.js` → `.log`). occt mesh (the boolean opening) → three.js `BufferGeometry`
  (`positionCount=48 indexCount=96`), `geoBbox=[0,0,0,4,0.2,3]` matches the kernel, and the solid
  REALLY DRAWS: `litPixels=3740` (5.7% of frame) via SwiftShader WebGL. EDGE: headless WebGL needs
  `--use-angle=swiftshader --enable-unsafe-swiftshader`. Full chain proven end-to-end:
  op-row → signed op-log → fold → occt B-rep → tessellate → three.js → pixels. (Pick-highlight reuse
  via `picking.js focusElement` = integration detail, deferred to Item 2 in-viewer wiring.)
- ✅ **planegcs sketch→profile DONE 2026-06-18 — `W-SKETCH-SOLVE` PASS** (`build/kernel/spike_sketch.html`
  + `poc_kernel_sketch.js` → `.log`). FreeCAD constraint solver (planegcs, LGPL) runs in-browser:
  `solve status=0`, snaps p1 (3,1)→(3.838,1.127) to satisfy `|p0,p1|=4` (`solvedDist=4.0000`); the solved
  dimension drives a real occt solid via the general `makeLineEdge→makeWire→makeFace→extrude` path
  (`tris=12 xExtent=4.0000==solvedW`). The whole spine (sketch→solver→kernel→signed-log→render) is green.
- ✅ **Both edge probes DONE 2026-06-18** (files in `build/kernel/`, re-run `node build/kernel/poc_kernel_<manifold|webifc>.js`):
  - **`W-KERNEL-MANIFOLD` PASS** (`spike_manifold.html` + `poc_kernel_manifold.js` → `.log`). Manifold
    (`manifold-3d@3.5.1`, Apache-2.0) subtracts a flush-/coincident-face void and returns a watertight closed
    mesh: `status=NoError numTri=28 numVert=16 genus=0`, `vol=2.0000` (exact = 4·0.2·3−1·0.2·2), Euler `V−F/2=2`.
    = the robust BOP fallback §4#2 asked for. CAVEAT (honest): OCCT `cut` survived the SAME input here
    (`tris=28`, correct bbox) — so this proves Manifold's *guaranteed*-clean path, not OCCT breaking.
  - **`W-KERNEL-WEBIFC` PASS** (`spike_webifc.html` + `poc_kernel_webifc.js` → `.log`). A REAL wall
    (`reference/residential/Ifc4_WallElementedCase.ifc` Drywall panel, profile 96×48, extrude 0.5 +Z) is
    re-authored as ONE op-row, written to IFC4 via web-ifc (`CreateModel`/`CreateIfcEntity`/`WriteLine`/`SaveModel`,
    1085 bytes), re-imported (`OpenModel`/`GetLineIDsWithType`), and the `IfcExtrudedAreaSolid` reads back
    `XDim=96 YDim=48 depth=0.5 dir=[0,0,1]` — all four `match=Y`. Swept-solid family round-trips exact;
    Psets/Materials/OwnerHistory/Styling dropped by-design (scoped to what the Bonsai spine authors).
  Then Item 2 = in-VIEWER sketch UI wiring.

## 0. The question this answers: "new app or not?"
- **Reused as-is (zero rewrite):** geometry math — occt-wasm, Manifold, planegcs, web-ifc.
- **Reused from OURS:** op-log (`build/erp/kernel_ops.js`), SQLite, viewer render/picking/highlight
  (`picking.js focusElement`), history bar, CRUD overlay fold pattern.
- **NEW code (the "new app" surface):** op-log-as-feature-tree · fold→geometry replay ·
  worker marshalling · dirty-regen cache · IFC-type↔op mapping · authoring UI.
- **Verdict:** a new authoring FACE fused onto the existing viewer/ERP surface, sharing ONE op-log.
  Novelty is the ASSEMBLY (signed op-log where competitors keep a volatile undo stack), NOT geometry.

## 1. The stack — resolved, licenses sorted
| Need | Tool | License | Notes |
|---|---|---|---|
| B-rep kernel (extrude/boolean/fillet/sweep/loft) | **occt-wasm** (andymai) | wrapper MIT/Apache · wasm **LGPL-2.1** | ~170 ops, **4.5MB brotli**, OCCT V8.0.0, arena memory |
| Robust boolean fallback | Manifold (elalish) | Apache-2.0 | when OCCT BOP fails on openings |
| 2D sketch constraints | planegcs (Salusoft89) | LGPL-2.1 | profile parametrics |
| IFC read / round-trip | web-ifc (ThatOpen) | MPL-2.0 | import |
| Render | three.js | MIT | already ours |
| **Feature tree + persistence** | **our kernel_ops + SQLite** | MIT | the differentiator |

- Whole module is **MIT-shippable**. LGPL wasm stays a SEPARATE replaceable `.wasm`
  (occt-wasm is built for this — dynamic `.wasm` load satisfies LGPL). **Do NOT fuse the
  kernel wasm into the minified bundle** (breaks LGPL replaceability + our minify habit).
- **AGPL apps (Chili3D, ifc5cad) = STUDY ONLY, never copy code.**

## 2. The two historical killers — now solved
1. **Memory.** Raw opencascade.js = manual `.delete()` on every object, no GC, documented leak hell,
   fatal for a replay-fold. occt-wasm = **arena of u32 handles + Symbol.dispose/`using`** → scope an
   arena per evaluation, auto-free. Biggest de-risk.
2. **Threading.** Kernel in a **Web Worker** (Comlink RPC), own WASM instance + arena; UI stays
   reactive during regen. Standard (Replicad does the same).

## 3. The MVP spine (covers most of a building)
`IfcExtrudedAreaSolid` IS BIM geometry — a 2D profile swept by depth, holes→holes. Spine:

> **planegcs sketch → closed profile → occt-wasm.extrude → cut for openings → tessellate → three.js**

The RECIPE (profile params, depth, cut refs) = **one `kernel_ops` row per feature.**
Geometry is the fold; the op-log is the truth. Authors walls / slabs / columns / beams / openings.

## 4. Hard parts (ranked, honest)
1. ✅ **DONE/LIVE 2026-06-18 (PR #382, W-BONSAI-REGEN) — Incremental regen, the real core.** Built after the
   operability track, on user go. The signed **op_hash is the free perfect cache key**: append-only chain ⇒
   op_hash = SHA(prev_hash|op) encodes the whole prefix ⇒ a committed row's folded geometry is IMMUTABLE once
   computed. Worker caches occt shape + tessellated mesh per op_hash across folds (GRID_MOVE per op_hash:featureId);
   a re-fold rebuilds ONLY new ops. Cached shapes never released mid-fold (cache owns them; only local intermediates
   freed); foldChain returns CLONES (host transfers/detaches buffers). Witness: cold 3-feat fold=3 rebuilds/6ms,
   re-fold=0 rebuilds/1ms, add cut=1 rebuild, deterministic, clear drops cache. Built FREELY (single-lineage,
   content-addressed = decades-old dependency-graph prior art; patent-sensitive surface = cloud branch&merge, §6).
   ⚠ (historical) noted as Onshape branch/merge patent territory — resolved §6: regen ≠ branch/merge, build freely.
2. **Boolean robustness.** OCCT BOP drops/mangles shapes on coincident/misaligned inputs
   (FreeCAD #17705). Openings = many cuts = high exposure. Mitigate: fuzzy-tolerance BOP, then
   Manifold mesh fallback.
3. **IFC write coverage + browser gap.** Map fold → IFC entities (start extruded-area-solid family).
   occt-wasm = **Chrome 114+/Safari 17.2+, NO Firefox** (WASM tail calls) — product decision:
   accept it / no-tail-call build / fall back to bigger opencascade.js for reach.

## 5. Scope, tiered
- **Item 1 — SPIKE (days) [GO/NO-GO]:** occt-wasm in a worker → hardcode rectangle profile →
  extrude → mesh → three.js → store as ONE `kernel_ops` row → replay it.
  - **Witness claim (write FIRST):** `W-KERNEL-FOLD` — a feature stored as a kernel_ops row,
    deleted from the scene, and REGENERATED identically by replaying the op through the kernel
    (vertex-count + bbox match). Proves geometry = fold over op-log.
- **Item 2 — MVP (weeks):** planegcs sketch tool → extrude → opening-cut → IFC class tag →
  history scrubber replays the recipe. Walls/slabs/openings author-able.
- **Item 3 — REAL (months):** incremental-regen cache · Manifold robust booleans · broader IFC
  types · multi-user via distributed op-log · fillet/sweep/loft.

## 6. Flags (not code decisions — surface, don't resolve)
- **PATENT:** Onshape/PTC hold patents on cloud parametric CAD w/ branch & merge. Our
  multiverse-over-feature-op-log brushes this. Get counsel BEFORE leaning on it commercially.
  - **✅ RESOLVED re the REGEN CACHE — NOT a blocker (2026-06-18, engineering framing not legal advice).**
    Separate the two: (1) the **incremental regen cache** (dirty-feature + downstream invalidation, op_hash-keyed)
    is **decades-old prior art** (Pro/E, SolidWorks, CATIA dependency-graph regen since the late '80s/'90s; op_hash =
    content-addressing à la git/Merkle) → **build it freely, single-lineage, no branch/merge claim attached.** (2) The
    patent-sensitive surface is the narrower **branch & merge of model history in a CLOUD/server collaborative setting**
    — not "incremental regen." **Workaround = mostly framing, not avoidance:** build the cache as plain single-lineage
    regen; keep our branch/merge defended by **event-sourcing + git-for-data prior art** (predates & is independent of
    Onshape) and by our **client-side** fold (claimed Onshape territory is server-side); honest novelty scoped already in
    `docs/ModellerKernelFold.md` (delta = one signed op-log for BOTH ERP + geometry, fully client-side). Trigger is
    **commercialization**, not building — get counsel before leaning on cloud branch-&-merge; the parked copyleft/dual-
    license moat is the business lever. **So the regen cache is buildable later with no patent exposure — do NOT treat it as blocked.**
- **LICENSE STRATEGY (user, parked):** deliberately copyleft/dual-license moat so big players must
  ENGAGE rather than quietly fork. Strategy call, not a build blocker.

## 7. Sources
- occt-wasm: https://github.com/andymai/occt-wasm
- opencascade.js file size: https://ocjs.org/docs/getting-started/file-size
- opencascade.js memory lifetime: https://github.com/donalffons/opencascade.js/discussions/186
- Replicad as a library: https://replicad.xyz/docs/use-as-a-library/
- OCCT boolean robustness (FreeCAD #17705): https://github.com/FreeCAD/FreeCAD/issues/17705
- IfcExtrudedAreaSolid: https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcExtrudedAreaSolid.htm
- Event-sourced CAD architecture: https://novedge.com/blogs/design-news/deterministic-event-sourced-architecture-for-real-time-collaborative-cad
- Chili3D (AGPL, study only): https://github.com/xiangechen/chili3d
- ifc5cad (AGPL, study only): https://github.com/louistrue/ifc5cad

## §GAP-TO-COMPETITIVE — remaining authoring feature gap + ETA, spec-first (2026-07-07)

**§5's "Item 3 — REAL (months)" list is now stale** — it names `fillet/sweep/loft` as still-pending; all
three shipped since (depth track above), plus `GEOM_ARRAY`/`GEOM_LOFT` (`prompts/BONSAI_ARRAY_PATTERN_SPEC.md`)
and MEP fitting placement (`prompts/Modeller/DISC_Walker/RESUME_MODELLER_WALK_SUBSTRATE.md` M5) landed
today. This section replaces that stale list with the real remaining gap, tiered by genuine cost — not
by re-probing, by reading this file's OWN already-mapped inventory (§"NEXT-SESSION DIRECTIVE" above).

### Tier 1 — cheap occt shoulders, ZERO binding work (same shape as `GEOM_LOFT`, ~20-30 min each measured today)
Already exported in `lib/kernel/index.js`, confirmed unwired:
- `revolve(shape,axis,angleRad)` :257 — **highest value of this tier**: axisymmetric solids (round columns,
  tanks/vessels, turned parts) have no authoring path today at all.
- `shell`/`makeThickSolid` :286 — hollow a solid to a wall thickness (cast-then-hollow modeling).
- `draft(shape,face,angleRad,dir)` :301 — draft angle (lower AEC value, real for precast/formwork).
- `offset` :298, `filletVariable` :1096, `chamferDistAngle` :272 — variants of ops already wired.
Each is a worker-only `applyFeature()` case + LEAF-set entry + Outliner category + witness — the EXACT
recipe `GEOM_LOFT` just proved end-to-end. **Measured today, not estimated:** `GEOM_ARRAY` (genuinely new
worker math + formula parser + IFC research) = ~25 min one session; `GEOM_LOFT` (existing binding, this
tier's shape) = ~25 min. All ~6 Tier 1 ops are plausibly a **single day** if dispatched as parallel
sessions (the pattern used today), not a bottleneck.

**✅ DONE 2026-07-07 — all 6 shipped in one dispatched session, PR #691 (`feat/bonsai-tier1-shoulders`,
`0cc3875`), auto-merge armed, `W-BONSAI-TIER1` witness 20/20 checks PASS.** `GEOM_REVOLVE` (LEAF — fresh
solid from profile+axis+angle, same class as `GEOM_SWEEP`/`GEOM_LOFT`); `GEOM_SHELL`/`GEOM_OFFSET`/
`GEOM_FILLET_VARIABLE`/`GEOM_CHAMFER_DIST_ANGLE`/`GEOM_DRAFT` all non-LEAF (mutate a referenced parent
solid, same class as `GEOM_CUT`/`GEOM_FILLET`). Real determinism proof: revolve 208 tris @2π vs 60 tris
@π/2; `filletVariable` 44 tris vs 40 for an equal constant-radius fillet on the same edge.
**Harder-than-"free-shoulder" in practice, honestly reported by the build session, not hidden:**
`GEOM_DRAFT` needed direct probing to discover `direction` must equal the picked face's own outward
normal (an unrelated direction is silently a no-op, no exception — undocumented in the binding itself);
`GEOM_CHAMFER_DIST_ANGLE`'s angle parameter didn't produce a triangle-count-visible difference on the
test case used (topology-invariant there), so its angle-sensitivity wasn't independently proven the way
revolve/filletVariable's was. IFC mapping intentionally not built (optional per scope): `GEOM_REVOLVE` →
`IfcRevolvedAreaSolid` is a real, named target; shell/offset/fillet-variants have no dedicated IFC entity
beyond the base solid's boundary rep. **Net: Tier 1's "days not weeks" estimate held** — all 6 landed in
one session, confirming the pattern generalizes past just `GEOM_LOFT`.

### Tier 2 — real engineering, not free shoulders (weeks-scale, per this doc's own §5 tiering)
- **planegcs constraint richness**: ~60 constraints exist in `planegcs_dist/constraint_param_index.js`,
  only ~5 wired (`parallel`, `perpendicular_ll`, `equal_length`, `p2p_symmetric_ppp`, H/V). This is the
  actual gap between "constraint-solving on fixed hand-drawn geometry" (current state) and Grasshopper/
  Dynamo-class "geometry as a function of parameters" — `tangent`/`p2p_distance`/`p2p_angle`/
  `circle_radius`/`p2p_coincident` unlock drag-one-dimension-everything-updates behavior the sketch layer
  doesn't have yet. `GEOM_ARRAY`'s formula evaluator is a DIFFERENT, narrower thing (per-instance
  numeric variation) — it does not substitute for real sketch-level parametrics.
- **Boolean robustness** (§4#2) — OCCT BOP mangles coincident/misaligned cuts; every opening is a cut.
  Real risk at scale, mitigation (fuzzy-tolerance BOP, Manifold fallback) not built.
- **Direct-manipulation UI** (`prompts/MODELLER_DIRECT_MANIPULATION.md`, flagged "the UI-competitive
  spine") — axis-drag MOVE gizmo+snap, marquee multi-select, snap-to-geometry, rotate/scale handles.
  Separate track from kernel ops entirely; own spec, not started per that card.
- **MEP fitting library depth** — today's real mini-BOM RosettaStone (`DISC_ROSETTASTONE_MEP_MINISET.md`)
  proves the mechanism on exactly ONE real run (2 joint pieces: a tee + a transition) from one building.
  Genuine production coverage needs many more real mined runs, not a wiring task.

**✅ FIRST INCREMENT DONE 2026-07-07 — `p2p_distance` wired, real UX-verified (bim-ootb
`feat/bonsai-sketch-p2p-distance`):** the first of the `tangent`/`p2p_distance`/`p2p_angle`/`circle_radius`/
`p2p_coincident` family above — `p2p_distance` pins an edge's endpoint distance to a user-typed value,
`bonsai_sketch.js`'s `dims{}` map + `_buildConstraints()`. A real, previously-user-editable dimension:
after a rect/square 4-point sketch, typed `#dim-w`/`#dim-h` toolbar fields (Enter commits) re-solve the
WHOLE quad from one changed number — the actual drag-one-dimension-everything-updates behavior, not the
narrower `GEOM_ARRAY` formula evaluator.

**Acceptance bar met per this doc's own directive — real interaction, not headless-witness-alone:** driven
via real Playwright mouse+keyboard (click→type→Enter, not calling `solve()` from a harness) in the running
Modeller, THEN proven via hand-derived exact numeric invariants (not a screenshot, not a movement
threshold) — `witness_e2e_sketch_dims.js` (bim-ootb `modeller/tests/`), 10/10: pinned edge length exact,
the fixed anchor point bit-identical before/after, perpendicularity/parallelism proven via normalized
dot/cross products computed independently in the test (≈1e-16, not assumed), the rectangle-equal-sides
consequence verified, and the authored solid's bbox matching a hand-computed bbox of the same solved
points (isolates "did Extrude carry the profile faithfully" from "is the profile axis-aligned" — it need
not be, since rect mode pins angles+one length, not orientation). Same rigor as M5's 90°-to-135.0000°-yaw
proof (`WalkerDoctrine.md §7`) — recomputed independently, not eyeballed. First pass of this witness had
leaned on screenshots + a movement-threshold check; corrected same-day per a sharpened directive before
being called done.

**Real bug found+fixed live while proving this, bigger than the feature itself:** every toolbar `dim-*`
numeric field (rotate/scale/move/depth/profile/radius — not just the new W/H) rendered at the DOM flow
origin (0,0) — invisible under the Outliner panel and unfocusable by a genuine mouse click (`#bar`'s
`pointer-events:none` is inherited by `<input>`, only `<button>` got the override back; confirmed on the
pre-existing `#dim-depth`, not just the new fields). Fixed with a dedicated `#dim-row` fixed-position
container reusing the SAME "clear the Outliner" `left:252px` convention already used by `#hist`/`#stat` —
a shared-gate fix for the whole family, not a point-fix for just the 2 new fields.

**Not yet done (disclosed, not silently deferred):** `tangent`/`p2p_angle`/`circle_radius`/`p2p_coincident`
remain unwired; height (`#dim-h`) is shown/editable but not yet independently proven the same way (only
width was driven through the full real-interaction + invariant-proof cycle this pass).

### Tier 3 — structural, cross-cutting (this doc's own §5 already calls these "months")
IFC write coverage beyond `GEOM_EXTRUDE_POLY` parents (array/loft/insert exports are currently scoped
narrower than their engine ops), multi-user distributed op-log, broader IFC entity coverage.

### ETA, honestly — not one number
**Tier 1 (kernel op-count parity on cheap shoulders): days, not weeks** — the measured 20-30 min/op
pattern from today generalizes directly, this doc already flagged these as zero-binding-work in June.
**Tier 2/3 (the actual Grasshopper/Dynamo-class differentiator — true sketch parametrics, robust
booleans, UI-competitive manipulation): this doc's own §5 already scoped these as MONTHS before today's
session, and nothing found today changes that** — each is a real, separately-scoped engineering effort
with its own design risk (planegcs richness could surface solver-convergence issues; boolean robustness
is an open OCCT-upstream issue, not just wiring). **"Full challenge to the BIM Modeller world" is not a
single ETA** — kernel breadth closes fast, true parametric depth + UI competitiveness does not, and this
file already said so before today; today's work didn't shorten that, it just closed a different, cheaper
part of the gap (kernel op count) while sharpening evidence for why the harder part stays hard (e.g. the
same-day proof that computed-not-extracted geometry is unverifiable, `WalkerDoctrine.md §7`).
