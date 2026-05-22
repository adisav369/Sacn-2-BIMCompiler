# ⚠ DO NOT REMOVE — S270 Grid Kinematics Engine
# Scope: Extract pure-math engine from doc_canvas.js, add roof vertex recomposition. Read the log after every run.

## Activity Category
geometry/BOM — read feedback files: deployment, pipeline, tack point, geometry extraction

## Goal
Implement `grid_kinematics.js` as a standalone pure-math module that owns ALL grid-drag recomposition.
Then refactor `doc_canvas.js` to be a thin caller. Spec: `docs/NEW_FROM_REFERENCE.md` §17.10.2–§17.10.4.

## Context — What Already Exists

### S267 (done): BOM Walker + Verb Expansion
- `verb_expand.js`: 7 verb expanders (TILE/ROUTE/FRAME/CLUSTER/SPRAY/LINE/LINE_MULTI)
- `bom_walker.js`: BOM tree traversal via sql.js
- `doc_canvas.js`: phases from BOM tree walk, grid envelope from BOM AABB

### S268+S269 (done): Attach-Map Recompose + Bay-Proportional
- `doc_canvas.js` has `_buildAttachMap`, `_applyAxisDeltas`, `_applyBayProportional`
- 5 relation types: ATTACH, SPAN, EDGE_RIGHT, EDGE_LEFT + INTERIOR (bay-proportional)
- 63 tests in `test_s268_recompose.js` (Node.js, pure logic, duplicated from doc_canvas.js)
- Edge-attach walls: direction-aware (grid moves away → stretch, into → translate)
- **Problem: algorithm is duplicated** between doc_canvas.js (browser IIFE) and test file

### Sandbox verification
- `/tmp/ootb-dev/sandbox/sandbox_recompose.js` — proven on SC_BOM.db (2990 entries)
- `/tmp/ootb-dev/sandbox/sandbox_grid_attach.js` — realistic user-placed grid lines

## What This Session Must Deliver

### 1. `deploy/dev/grid_kinematics.js` — The Engine

```
Class: GridKinematicEngine

Constructor(elementData, gridLines)
  elementData: [{guid, x, z, bboxX, bboxZ, ifcClass, vertices?, scaleX?, scaleZ?}]
  gridLines:   [{id, axis:'x'|'z', pos}]

Methods:
  attachGridToElements()  → builds internal attachMap (called once)
  dragGrid(gridId, delta) → [{guid, action, axis, delta, ...}] (called per drag)
  getAttachMap()          → returns attachMap for test inspection
```

#### Relations (from S268, proven)
- ATTACH: centerline within 0.5m → TRANSLATE
- EDGE_RIGHT: right edge within 0.1m → +delta=stretch, -delta=translate
- EDGE_LEFT: left edge within 0.1m → +delta=translate, -delta=stretch
- SPAN: grid inside body → SCALE (near/far edge)
- INTERIOR: not attached, inside bay → proportional TRANSLATE

#### Roof vertex computation (NEW, from §17.10.2)
For IfcRoof elements with `vertices` (Float32Array):
- Classify: yRange < 0.05 → flat, else sloped
- Flat: edge vertices translate by delta (same as SCALE)
- Sloped: `t = (vertex.y - yMin) / yRange`. Eave verts (t≈0) get full delta. Ridge (t≈1) gets zero.
- Returns `{ action: 'ROOF_VERTICES', vertexDeltas: Float32Array }` — caller applies to BufferGeometry

#### Commands returned by dragGrid()
```javascript
{ guid, action: 'TRANSLATE', axis: 'x'|'z', delta: number }
{ guid, action: 'SCALE', axis, newScale, translateDelta }
{ guid, action: 'EDGE_STRETCH', axis, delta, edge: 'right'|'left' }
{ guid, action: 'ROOF_VERTICES', axis, delta, vertexDeltas: Float32Array }
{ guid, action: 'ROOF_LIFT', deltaY: number }
```

#### Design Invariants (from DeepSeek review, non-negotiable)
1. Eave moves with grid. Ridge fixed in space. Linear interpolation by height ratio.
2. Engine is stateless re kernel_ops. Parent replays log on load.
3. Only attach-map elements get commands. Others ignored (except INTERIOR bay-proportional).
4. `dragGrid()` is pure: `(positions, delta) → commands`. Never mutates external state.
5. O(K) per drag where K = attached count. Pre-indexed by grid ID.

### 2. `deploy/dev/tests/test_grid_kinematics.js` — Test Harness

Port all 63 tests from `test_s268_recompose.js` to use the engine class. Add:
- Sloped roof: 942-vertex SampleHouse roof, drag eave +2m, verify ridge Y unchanged
- Flat roof: all vertices at same Y, drag edge, verify horizontal stretch
- Mixed: flat center + sloped edges (synthetic), verify both handled
- Adjacent walls share grid → no gap, no overlap (already proven, re-verify via engine)
- Engine returns commands, never mutates input data (purity check)

### 3. Refactor `doc_canvas.js` — Thin Caller

Replace the inline `_buildAttachMap`, `_attachToAxis`, `_applyAxisDeltas`,
`_scaleMesh`, `_applyBayProportional`, `_bayProportionalDelta`, `_getMeshPosition`
with:

```javascript
// On first drag after phase change:
var elementData = _collectElementData(A);
var gridLines = _collectGridLines();
_kinEngine = new GridKinematicEngine(elementData, gridLines);
_kinEngine.attachGridToElements();

// On each drag:
var commands = _kinEngine.dragGrid(movedGridId, delta);
_applyCommands(A, commands);
```

`_collectElementData` reads `_guidToSlot`, `_guidToInstance`, and `A.db` to build
the element array. `_applyCommands` translates/scales meshes and updates vertex
buffers. These are the ONLY Two functions that touch Three.js.

### 4. Ceiling Grid Auto-Placement

When Phase 3 (Finishes) reveals IfcRoof elements:
- Scan roof mesh vertices, find `eaveY = min(vertex.y)` in Three.js coords
- Auto-place a grid line at that Y on the "floor" axis
- This gives the user a handle to lift the ceiling

## Verification
- All existing tests pass (149/149 from S267+S268+S269)
- New engine tests pass (target: ~80 tests including roof)
- `doc_canvas.js` shrinks by ~200 lines (algorithm extracted)
- `node -c deploy/dev/grid_kinematics.js` — syntax OK
- `node -c deploy/dev/doc_canvas.js` — syntax OK
- §-tagged logs prove roof vertex recomposition:
  `§ROOF_CLASSIFY guid=xxx type=PITCHED verts=942 eave=241 ridge=413`
  `§ROOF_RECOMPOSE guid=xxx axis=x delta=+2.0 eave_moved=38`

## Session Startup
1. Read this prompt
2. Read `docs/NEW_FROM_REFERENCE.md` §17.10.2 (roof), §17.10.3 (engine), §17.10.4 (UBBL)
3. Read `deploy/dev/doc_canvas.js` — the recompose section (lines ~1580-1980)
4. Read `deploy/dev/tests/test_s268_recompose.js` — the algorithm to extract
5. Read PROGRESS.md §Current State

## Out of Scope
- UBBL Validator (Stage 2) — separate session after engine is stable
- Tile recount / FRAME coord replacement at runtime (Stage 2)
- MEP rerouting (Stage 2)
- IFC export, save/recall, GPU throttle
- Deploy to OCI (engine is internal, not a deployed file change)

## Sequence: Safe Migration Path
1. Create `grid_kinematics.js` with pure algorithm (NEW file, no risk)
2. Write `test_grid_kinematics.js`, port S268/S269 tests + add roof tests (NEW file)
3. Add roof vertex computation to engine
4. Refactor `doc_canvas.js` to call engine ← the only risky step, full test suite as safety net
5. Delete duplicated algorithm from doc_canvas.js
6. Verify all 149+ existing tests still pass
