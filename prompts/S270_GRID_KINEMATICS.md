# ⚠ DO NOT REMOVE — S270 Grid Kinematics Engine
# Scope: Pure-math grid recomposition engine. Read the log after every run.

## Activity Category
geometry/BOM — read feedback files: deployment, pipeline, tack point, geometry extraction

## What's DONE (commits 87a096b7 → 7ae0add9 on branch `full`)

### Engine — `deploy/dev/grid_kinematics.js` (672 lines)
- GridKinematicEngine class, IIFE dual-export (Node + browser)
- 3-question attachment model: WHAT attached, HOW may it move, WHAT cascades
- 8 relation types: ATTACH, SPAN, EDGE_RIGHT, EDGE_LEFT, ROOF_EAVE, ROOF_FLAT, ROOF_LIFT, INTERIOR
- 4 command types: TRANSLATE, SCALE, ROOF_VERTICES, ROOF_LIFT
- Cascade: WALL_HEIGHT_SCALE (walls grow when roof lifts via Y-axis grid)
- Bay-proportional interior repositioning
- 98/98 tests in `deploy/dev/tests/test_grid_kinematics.js`

### Caller — `doc_canvas.js` refactored
- Thin caller: `_rebuildEngine` → `engine.dragGrid()` → `_applyCommand()`
- `_collectElementData(A)` reads Three.js → plain objects for engine
- `_collectGridLines()` includes CEIL Y-axis grid
- `_applyCommand()` dispatches TRANSLATE/SCALE/ROOF_VERTICES/ROOF_LIFT
- BatchedMesh `instanceMatrix` guard (r160 has no `.instanceMatrix`)
- `_findRootBom` guarded with try/catch
- Ceiling grid auto-placement at eave Y on Phase 3 (IfcRoof)
- Grid/Rosetta status messages in status bar
- `sw.js` precache includes `grid_kinematics.js`, CACHE_VERSION v438

### Tests passing
- `test_grid_kinematics.js`: 98/98
- `test_doc_canvas.js`: 66/66
- `test_s268_recompose.js`: 63/63
- `test_grid_modules.js`: 114/114
- `whitebox_regression.js`: 34/36 (2 pre-existing)

## KNOWN BUGS — Fix in next session

### BUG-1: Incremental delta double-counting — FIXED ✓
**Was:** Absolute delta from `_computeGridDeltas()` fed directly to `_translateMesh()` which adds
incrementally → second drag double-counted.
**Fix:** Added `_lastAppliedDeltas` map in `doc_canvas.js`. `recomposeAfterGridDrag` now computes
`incrementalDelta = absoluteDelta - _lastAppliedDeltas[gridId]` before calling engine.
Reset on engine rebuild and deactivate. Log shows `absDelta` + `workedDelta` for tracing.
**Tests:** T52 (two drags), T53 (pullback), T54 (rebuild resets). All 57/57 pass.

### BUG-2: Grid should validate edge alignment before allowing transform
**Symptom:** Grid line not at building edge still allows drag and transforms.
**Expected:** Grid must be near actual element edges to have meaningful attachments.
If attach map is empty for a grid line, status should say "no attached elements —
place grid at wall/column first" and optionally block the drag.
**Fix:** In `_deselectGrid` after drag, if engine returned 0 commands, warn user.
Or: at grid select time, show attach count (already done via `_getGridAttachInfo`).

### BUG-3: Next button not aware of post-drag state
**Symptom:** After dragging grid and transforming walls, pressing Next materializes
new phase elements at their ORIGINAL positions (from DB), not accounting for grid moves.
**Expected:** New elements should appear at positions consistent with the moved grid.
**Fix:** On `_materializePhase`, if engine exists, run the new elements through
`attachGridToElements` and apply any pending deltas. This is a Stage 2 concern
(UBBL cascade adjusts newly-revealed elements) but a basic version could snap new
elements to their bay-proportional positions immediately.
**Scope:** Triage — could be deferred to UBBL Stage 2.

### BUG-4: SCALE commands not firing — FIXED ✓
**Was:** `_collectElementData` passed IFC bbox (Z-up: X,Y=depth,Z=height) to engine
without coordinate swizzle. Engine used bboxZ (IFC height) as Three.js Z-axis extent
and bboxY (IFC depth) as Three.js Y-axis extent — inverted. SPAN/EDGE could never
form correctly because half-extents were on wrong axes.
**Fix:** Swizzle in `_collectElementData`: `bboxY→bboxZ` (IFC depth→Three Z),
`bboxZ→bboxY` (IFC height→Three Y). Added `§COLLECT_ELEMENTS` diag log showing
bbox match count + sample values, and per-relation-type counts in `§RECOMPOSE_ENGINE`.
**Tests:** T55 (swizzle), T56 (SPAN forms), T57 (EDGE forms), T58 (SCALE fires, scaleZ=1.1667).
All 61/61 pass.

## What's Next — S270b (next session)

### Priority 1: Fix BUG-1 (incremental delta) — DONE ✓
Fixed: `_lastAppliedDeltas` map, incremental delta computation, T52-T54 tests.

### Priority 2: Fix BUG-4 (all TRANSLATE, no SCALE) — DONE ✓
Fixed: IFC→Three.js bbox coordinate swizzle in `_collectElementData`. T55-T58 tests.

### Priority 3: Refactor doc_canvas.js → 3 modules — DONE ✓ (Steps 1-2)
Spec: `docs/REFACTOR_DOC_CANVAS.md`. Reviewed by DeepSeek 2026-05-23.
**Step 1 (GridState wiring):** `grid_state.js` (already existed, 335 lines) wired into
doc_canvas.js. All `_xPositions`, `_zPositions`, `_xLabels`, `_zLabels`, `_gridOriginals`,
`_gridOrigByLabel`, `_ceilingGridY` vars removed from doc_canvas. Duplicate functions
(`_snapshotGridOriginals`, `_computeGridDeltas`, `_collectGridLines`, `_resortLabels`,
`_nextXLabel`, `_addGridPosition`, `_removeGridPosition`) replaced with GridState delegates.
Added `getPosition()`, `getLabel()`, `getCount()` accessors to GridState.
**Step 2 (GridRecompose extraction):** Created `grid_recompose.js` (682 lines).
Extracted engine lifecycle, command dispatch, mesh transforms, BOM recompose, delta tracking.
`_kinEngine`, `_kinEngineDirty`, `_lastAppliedDeltas`, all BOM state moved to GridRecompose.
doc_canvas.js shrank from 2862 → 2088 lines.
**Step 3 (GridInteraction):** Deferred — interaction code is ~190 lines, low priority.
All 4 test suites pass: 73 + 98 + 63 + 114 = 348 tests. Whitebox 34/36 (2 pre-existing).
Script tags and sw.js precache updated. CACHE_VERSION v442.

### Priority 4: Y-axis drag UI
The ceiling grid auto-places at eave Y (translucent disc visible). But the current
drag UI only handles X/Z axis lines. Need to wire Y-axis grid interaction:
- Click on ceiling disc → select CEIL grid
- Drag up/down → engine produces ROOF_LIFT + WALL_HEIGHT_SCALE cascade
- Status shows "Ceiling grid selected (1 ROOF_LIFT, 2 cascades)"

### Priority 5: BUG-2 and BUG-3 (triage)
- BUG-2: Warning on empty attach map — small UX fix
- BUG-3: Phase-aware recompose — may defer to UBBL Stage 2

## Repo Migration Note (2026-05-23)
Repo migrating from `red1oon/ootb-dev` (sandbox/) to `red1oon/bim-ootb` (viewer/).
Files move: `sandbox/grid_kinematics.js` → `viewer/grid_kinematics.js`, etc.
**Critical:** `grid_kinematics.js` must load before `doc_canvas.js` in HTML.
Check paths after migration before starting work.

## DB Schema Note
OCI `bim-ootb` bucket DBs were repaired on 2026-05-23. Five buildings (SC, HITOS, SH,
Duplex, Terminal) had been overwritten with old-schema copies (no `bbox_x`). Merged
correct DBs from `deploy/buildings/` + BOM from `*_BOM.db` and re-uploaded.
All 5 now have both `bbox_x` columns AND `m_bom` tables.

## Session Startup
1. Read this prompt — note DONE section and KNOWN BUGS
2. Read `docs/NEW_FROM_REFERENCE.md` §17.10.2 (roof), §17.10.3 (engine)
3. Read `deploy/dev/doc_canvas.js` — the `recomposeAfterGridDrag` function
4. Read `deploy/dev/grid_kinematics.js` — the engine
5. Check repo paths (migration may have moved files to `viewer/`)
6. Fix BUG-1 first — it's blocking all other drag testing

## Out of Scope
- UBBL Validator (Stage 2) — separate session after drag bugs fixed
- Tile recount / FRAME coord replacement at runtime (Stage 2)
- MEP rerouting (Stage 2)
- IFC export, save/recall, GPU throttle
