# ⚠ DO NOT REMOVE — SCOPE & DISCIPLINE
**Scope:** Make the seeded ARC building (GEOM_INSERT box proxies) actually **grid-STRETCHABLE**, then **gate** the
stretch — so "open a building and stretch it, conformity-checked" is real. The vision's PRIMARY handle (3D grid,
stretch≠scale). **Read the log after every run.** Honour until ✅ DONE. **Repo:** code = bim-ootb worktree;
spec = this file. **CORE-FOLD CHANGE** (foldChainToScene/foldInsert) → regression-witness move/cascade/gate/insert.
**NON-INVENT:** geometry only; mirrors the worker's translate/scale exactly.

## WHY (finding 2026-06-29)
Grid stretch folds WORKER-side (`bonsai_kernel_worker.js` GEOM_GRID_MOVE → `solids.get(featureId)`, B-rep only).
Seeded ARC walls are GEOM_INSERT **box proxies** folded HOST-side (`foldInsert`), which honored only
GEOM_MOVE/GEOM_ROTATE/GEOM_SCALE — **not GEOM_GRID_MOVE**. So dragging a gridline did NOTHING to the real building.
`gridmove.elementData()` already includes the inserts (classifies them, emits commands) — the FOLD just dropped
them. User chose: make ARC stretchable + gate it.

## §STRETCH-1 SPEC — foldInsert honors GEOM_GRID_MOVE (world-axis), then gate
### The math (MIRROR the worker exactly — bonsai_kernel_worker.js GEOM_GRID_MOVE)
A grid command `{featureId, action, axis:'x'|'y'|'z', delta?, newScale?, translateDelta?}` applies to the
element's WORLD positions:
- **TRANSLATE**: `coord[axis] += delta`.
- **SCALE** (non-uniform, anchored at the stationary world MIN edge): `f=newScale`, `min=min(coord[axis])`,
  `tx = min·(1−f) + (translateDelta||0)` → `coord[axis] = f·coord[axis] + tx`. (min edge → min+translateDelta;
  width → f·width.)

### foldInsert(op, mv, gridCmds)  — additive 3rd param (back-compat: callers passing (op,mv) unaffected)
After `place()` gives WORLD `positions` (line 381), apply `gridCmds` IN OP ORDER, each recomputing the axis min
from the CURRENT positions (self-consistent, matches the worker scaling the already-transformed solid). The local
GEOM_SCALE path (mv.fx, gizmo scale) is UNTOUCHED — grid is a separate WORLD-axis step after place().

### foldChainToScene — build the gridBy map
Iterate ops in order; for each GEOM_GRID_MOVE, push each `command` to `gridBy[command.featureId]` (ordered list).
Pass `gridBy.get(op.id)` as foldInsert's 3rd arg. The worker still gets kernelOps (incl GRID_MOVE) and applies to
its B-rep solids only (skips inserts: `solids.get` miss) → NO double-application. (insertOps folded host-side only.)

### Gate the stretch (commitGridMove, modeller.html ~1020)
Snapshot `_gateBoxes()` BEFORE `gridmove.commit`; after it resolves (scene re-folded), run
`_runGate(before, res.commands.map(c=>c.featureId))` → RED (clash / door-out — a stretched wall whose door now
falls outside) + ORANGE (clearance). Reuse §GATE-1 verbatim. `window.__lastGate` test hook. NO cascade-into-stretch
ride this slice (door stays; gate flags door-out — honest, the "openings can't divorce" ride is a separate slice).

## §STRETCH-1 WITNESS (claim FIRST)
**W-GRID-INSERT** (node, the box math — PURE): given a box proxy + a GEOM_GRID_MOVE command,
- TRANSLATE: world box shifts by delta on axis; extent unchanged; other axes unchanged.
- SCALE far-edge (translateDelta=0): min edge FIXED, max edge moves, width = f·w (matches worker formula exactly).
- SCALE near-edge (translateDelta=delta): the worker's left-edge stretch — verify against the worker tx formula.
- compose: a GEOM_MOVE then a grid SCALE = move-then-scale (min from moved positions); order respected.
- non-invent: gridCmds=[] ⇒ positions byte-identical to no-3rd-arg (back-compat).
**W-GRID-INSERT-FOLD** (node, via foldChainToScene-shape): a chain [INSERT, GRID_MOVE(cmd for it)] folds the insert
stretched (AABB == expected); a chain with GRID_MOVE whose command targets a DIFFERENT featureId leaves it untouched.
**Regression** (node + headless): W-ARC-EDITABLE, W-SDG-CASCADE-MODELLER, W-SDG-GATE, foldinsert-regression,
insert/move smokes — ALL still green (core-fold change must not perturb move/cascade/gate/catalog-insert).
**§STRETCH-GATE-SMOKE** (headless): open SampleHouse → drag a gridline → a seeded wall's AABB CHANGES (it stretches/
translates) → if it clashes/door-out, §GATE red logged + toast; a clean stretch → no flag.

## STATUS — §STRETCH-1 ✅ DONE+WITNESSED 2026-06-29 (bim-ootb PR #TBD, sw v18→v19)
- [x] `foldInsert(op, mv, gridCmds)` — 3rd arg; world TRANSLATE/SCALE after place(), mirrors the worker exactly
      (min recomputed from current positions per scale). Local GEOM_SCALE (gizmo) path untouched.
- [x] `foldChainToScene` builds `gridBy` (featureId → ordered commands from GEOM_GRID_MOVE ops), passes to
      foldInsert. Worker still folds B-rep solids only (skips inserts) → NO double-apply.
- [x] gate wired into `commitGridMove` (snapshot before, `_runGate(before, commands.featureIds)` after). Hook
      window.__gridStretch for the smoke.
- [x] **W-GRID-INSERT 6/6** (node: TRANSLATE; SCALE far min-fixed width=f·w; SCALE near min+td; compose move+scale
      op-order; back-compat gridCmds=[] byte-identical).
- [x] **§STRETCH-GATE-SMOKE 5/5** (headless: gridline drag recomposes a SEEDED wall → its geometry CHANGES (was a
      no-op) → gate runs → large stretch drives a wall into the building → §GATE red clash logged).
- [x] REGRESSION all green: W-ARC-EDITABLE 8/8, W-SDG-CASCADE 7/7, W-SDG-GATE 6/6, foldinsert-regression 5/5
      (node) + cascade/gate/arc-seed smokes 6/6/8 (headless). Core-fold change perturbed nothing.
- [x] sw v19.
- ⚠ Smoke gotcha fixed: commands carry `featureId` (numeric, = seeded fid 1..39) → test seeded membership with
      `__arcGuidByFid[featureId]` (fid→guid), NOT `__arcFidByGuid` (guid→fid).

## NEXT
- cascade-into-stretch ride (openings host-constrained "can't divorce" — TRANSLATE'd host rides its door; SCALE
  host keeps door at relative position) · one-click revert-RED · ORANGE backprop · enterprise fold.
