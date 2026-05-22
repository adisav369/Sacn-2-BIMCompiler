# Refactoring doc_canvas.js — Separation of Concerns

**Status:** Spec — not yet implemented
**Trigger:** S270 bug cascade (BUG-1, BUG-4, phantom deltas, grid flood) all caused by interleaved concerns sharing mutable state in a 2230-line monolithic IIFE.
**Reviewed by:** DeepSeek (2026-05-23), accepted with modifications.

---

## 1. Problem Statement

`doc_canvas.js` does 7 jobs in one file:

| Concern | ~Lines | Mutable state it owns |
|---|---|---|
| Grid state | 200 | `_xPositions`, `_zPositions`, `_xLabels`, `_zLabels`, `_gridOriginals`, `_gridOrigByLabel` |
| Grid rendering | 200 | `_gridGroup`, line/bubble/dim meshes |
| Grid interaction | 200 | `_selected`, `_dragStart`, `_dragging`, `_origPos`, `_raycaster` |
| Rosetta Stone | 150 | `_calibrationMode`, `_calibrations`, `_rosettaGroup` |
| Phase/BOM | 250 | `_phases`, `_phaseIndex`, `_activeDisc`, `_shownCount` |
| Recomposition | 300 | `_kinEngine`, `_kinEngineDirty`, `_lastAppliedDeltas`, `_guidToSlot`, `_guidToInstance` |
| Scene management | 200 | `_group`, `_envGroup`, `_hiddenMeshes`, `_batchedState`, `_instancedState` |

Every S270 bug occurred at a **seam** between two concerns sharing state:
- BUG-1: grid state ↔ recomposition (incremental vs absolute delta)
- BUG-4: recomposition ↔ scene (IFC↔Three.js coordinate swizzle)
- Grid flood: phase/BOM ↔ grid state (auto-add pollutes grid)
- Phantom deltas: grid state ↔ recomposition (index-based vs label-based originals)

---

## 2. Target Architecture

```
doc_canvas.js (orchestrator, ~800 lines)
  ├── grid_state.js      — positions, labels, originals, deltas
  ├── grid_recompose.js   — engine bridge, bbox swizzle, command dispatch
  └── grid_interaction.js — pointer events, drag, select, status
      └── grid_kinematics.js (already extracted — pure math engine)
```

**Dependency rule:** Each module exposes a JSDoc-documented API. No module reads another module's internal state. `doc_canvas.js` is the only file that wires them together.

**Facade rule:** `window.DocCanvas` public API is unchanged. Existing callers (scene.js, index.html) see no difference. Test-only `_` prefixed methods remain on `DocCanvas` but delegate to the new modules.

---

## 3. Module Contracts

### 3.1 `grid_state.js` — GridState

Owns all grid position/label/original tracking. Single source of truth for "where are the grid lines and where were they."

```js
/**
 * @typedef {Object} GridDelta
 * @property {string} label  — grid label (e.g. 'A', 'B', '1', '2')
 * @property {string} axis   — 'x' or 'z'
 * @property {number} absDelta — currentPos - originalPos
 * @property {number} currentPos
 * @property {number} originalPos
 * @property {number} index  — current array index (for rendering, not for delta math)
 */

GridState.init(xPositions, zPositions, xLabels, zLabels)
GridState.addLine(axis, position, label?) → {label, index} | null
GridState.removeLine(axis, label) → boolean
GridState.snapshotOriginals()  // called once at activate, records label→pos
GridState.getDeltas() → GridDelta[]  // label-keyed, immune to re-sort
GridState.getLines() → [{id, axis, pos}]  // original positions for engine
GridState.getPositions() → {x: number[], z: number[]}
GridState.getLabels() → {x: string[], z: string[]}
GridState.setPosition(axis, index, newPos)  // drag updates
GridState.getOriginal(label) → number | undefined
GridState.reset()  // clear all state (deactivate)
```

**Invariants:**
- Originals are always label-keyed. No index-based lookup.
- `addLine()` registers the new line's original position immediately.
- `getDeltas()` returns only lines where `|absDelta| > 0.01`.
- Dedup: `addLine()` rejects positions within `minGap` of existing lines.
- Cap: max 15 lines per axis.

**Timing contract:**
- `snapshotOriginals()` is called exactly once, at `activate()` time.
- `addLine()` can be called any time after (auto-grid, Rosetta, user click).
- New lines added after snapshot get their insertion position as original.

### 3.2 `grid_recompose.js` — GridRecompose

Bridge between GridState and GridKinematicEngine. Owns element data collection, engine lifecycle, command dispatch, and delta accumulation.

```js
/**
 * @typedef {Object} MeshLookup
 * @property {function(string): Object|null} getPosition — guid → {x,y,z,scaleX,scaleY,scaleZ}
 * @property {function(string,string,number): void} translate — guid, axis, delta
 * @property {function(Object): void} scale — cmd → apply scale
 * @property {function(Object): void} roofVertices — cmd → apply roof vertices
 * @property {function(Object): void} roofLift — cmd → apply roof lift
 */

GridRecompose.rebuild(meshLookup, db, shownGuids, gridLines)
GridRecompose.applyDrag(gridLabel, incrementalDelta) → {translated, scaled, roofOps}
GridRecompose.getAttachInfo(label) → string  // status message
GridRecompose.getRelationCounts() → {ATTACH, SPAN, EDGE_RIGHT, EDGE_LEFT, ...}
GridRecompose.resetDeltas()  // clear _lastAppliedDeltas
GridRecompose.getLastAppliedDelta(label) → number
GridRecompose.isDirty() → boolean
GridRecompose.markDirty()
```

**Invariants:**
- The bbox swizzle (IFC Z-up → Three.js Y-up) happens in `rebuild()`, nowhere else.
- `meshLookup` is a snapshot interface, not a live scene reference. Caller builds it from `_guidToSlot`/`_guidToInstance` at rebuild time.
- `applyDrag()` receives **incremental** delta (already computed by caller from GridState). Engine is pure.
- `_lastAppliedDeltas` lives here, not in GridState.

**Timing contract:**
- `rebuild()` is called when engine is dirty (new phase, new elements).
- `applyDrag()` is called per grid line per drag event.
- `resetDeltas()` on rebuild and deactivate.

### 3.3 `grid_interaction.js` — GridInteraction

Pointer events, drag mechanics, grid selection, status feedback.

```js
GridInteraction.init(canvas, camera, onDrag, onSelect, onDeselect)
GridInteraction.setGridState(gridState)  // reference to GridState for hit testing
GridInteraction.getSelected() → {axis, label, index} | null
GridInteraction.setStatus(msg)  // callback to update status bar
GridInteraction.dispose()  // remove event listeners
```

**Invariants:**
- Never touches meshes or the engine directly.
- Calls `onDrag(label, delta)` callback — doc_canvas.js handles recompose.
- Handles drag cancellation (pointerup outside canvas, Escape key).
- Status messages via callback, not direct DOM manipulation.

---

## 4. Extraction Order

### Step 1: `grid_state.js`
Foundation — everything else depends on grid positions/labels. Extract all `_xPositions`, `_zPositions`, `_xLabels`, `_zLabels`, `_gridOriginals`, `_gridOrigByLabel`, `_addGridPosition`, `_computeGridDeltas`, `_snapshotGridOriginals`, `_resortLabels`, `_nextXLabel` into this module.

**Test:** T59-T62 (label-based deltas) should work against `GridState` directly.

### Step 2: `grid_recompose.js`
Extract `_kinEngine`, `_kinEngineDirty`, `_lastAppliedDeltas`, `_collectElementData`, `_collectGridLines`, `_rebuildEngine`, `_applyCommand`, `_translateMesh`, `_scaleMeshFromCommand`, `_applyRoofVertices`, `_applyRoofLift`, `recomposeAfterGridDrag`, `_getMeshPosition`, `_getShownGuids`.

**Test:** T52-T58 (BUG-1, BUG-4) should work against `GridRecompose` with mock meshLookup.

### Step 3: `grid_interaction.js`
Extract `_selected`, `_dragStart`, `_dragging`, `_origPos`, `_raycaster`, `_pointer`, `_initInteraction`, pointer handlers, `_highlightGrid`, `_deselectGrid`, `_getGridAttachInfo`.

**Test:** Interaction tests (drag, select, cancel) against `GridInteraction` with mock callbacks.

### Step 4: Thin `doc_canvas.js`
What remains: `activate`, `deactivate`, `toggleGrid`, `nextPhase`, `prevPhase`, envelope, HUD, Rosetta Stone, timeline, phase loading, GRID_STRATEGY table, `_ifcToThree`, scene mesh management.

---

## 5. Migration Safety

- Each step: extract → add `<script>` tag → facade delegates → run all 341 tests → commit.
- `window.DocCanvas` API is unchanged throughout. No external caller breaks.
- `grid_kinematics.js` is untouched — already clean.
- `sw.js` precache list updated after each new file.

---

## 6. What This Does NOT Change

- `grid_kinematics.js` — already a standalone pure-math module (98 tests).
- `bom_extract.js` — already standalone.
- `kernel_ops.js` — already standalone.
- GRID_STRATEGY table — stays in `doc_canvas.js` as data (not a config file).
- Three.js rendering approach — BatchedMesh/InstancedMesh paths unchanged.
- The `_ifcToThree` coordinate transform — stays in `doc_canvas.js` as the single source of truth for coordinate conversion.
