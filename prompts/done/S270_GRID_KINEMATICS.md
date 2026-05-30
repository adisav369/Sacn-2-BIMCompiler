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

## What's Done — S270c

### Priority 1: Y-axis drag UI — DONE ✓
- `grid_state.js`: split `_ceilingY` into original + `_ceilingYCurrent`, `getDeltas()` includes CEIL delta
- `doc_canvas.js`: click ceiling disc → select CEIL grid (cyan highlight), click again → drag up/down
- Vertical plane projection (camera-facing) for Y-axis pointer tracking
- Ceiling disc moves live during drag, position updates in real-time
- `pointerup` commits CEIL GRID_MOVE kernel_op → `recomposeAfterGridDrag()` → engine produces
  ROOF_LIFT + WALL_HEIGHT_SCALE cascade commands
- Escape cancels drag, restores original ceiling position
- Attachment guard: blocks CEIL drag if no ROOF_LIFT attachments
- Status messages throughout: select, drag, commit
- CACHE_VERSION v443

### Tests (S270c)
- T71: getDeltas includes CEIL after setCeilingYCurrent
- T72: getLines uses original CEIL pos after drag
- T73: reset clears ceiling state
- T74: CEIL delta below threshold excluded
- T75: engine ROOF_LIFT + WALL_HEIGHT_SCALE cascade from CEIL drag
- T76: getCeilingYCurrent falls back to original when current not set
- All suites: 79 + 98 + 63 + 114 = 354 pass. Whitebox 34/36 (2 pre-existing).

## What's Next — S270d (next session)

### Priority 1: BOM wiring (B1-B5) — see RED_PILL.md §11.6
- B1: Add `<script>` tags for 6 `bom_engine/*.js` + `disc_rules.json` to `index.html`
- B2: Wall→Window cascade — wire `materializeLevel()` so `_bomNodes` is populated
- B3: ROUTE strategy → `RouteWalker.walk()` (~10 lines)
- BUG-3 closes after B1+B2 are wired (materializeLevel reads CURRENT AABBs)

### Priority 2: BUG-2 (triage)
- Warning on empty attach map — already partially done (drag blocked with status message)

### Priority 3: UBBL Validator (Stage 2)
Compliance engine, clearance rules, code checks. Needs triage session first.

## Main Doc
`docs/RED_PILL.md` is the single source of truth for the grid-based model.
Read §10 (Grid CUD + Attachment + Cascade) and §11 (Implementation Status) before starting.

## Session Startup
1. Read this prompt — note DONE items above
2. Read `docs/RED_PILL.md` §10 (grid CUD, attachment, cascade) and §11 (status, issues)
3. Read `deploy/dev/doc_canvas.js` — interaction code (lines ~1370-1660)
4. Read `deploy/dev/grid_recompose.js` — engine bridge
5. Read `deploy/dev/grid_kinematics.js` — the engine (§ROOF_LIFT, §WALL_HEIGHT_SCALE)

## Module Load Order (index.html)
`grid_state.js` → `grid_kinematics.js` → `grid_recompose.js` → `doc_canvas.js`

## Out of Scope
- Tile recount / FRAME coord replacement at runtime (Stage 2)
- MEP rerouting — NOT needed per §11.6 insight: DISC switch → MEP reads current positions
- IFC export, GPU throttle
- GridInteraction extraction (deferred, ~190 lines, low priority)

## Watchdog — S270c DONE appendix

| Claim | §-log evidence |
|---|---|
| getDeltas includes CEIL | T71: `§CEIL_DELTAS CEIL delta=1.500` |
| getLines uses original pos | T72: `§CEIL_LINES CEIL original pos=6.500` |
| reset clears ceiling state | T73: `§CEIL_RESET` |
| threshold excludes tiny delta | T74: `§CEIL_THRESHOLD CEIL delta 0.005 excluded` |
| ROOF_LIFT + WALL_HEIGHT_SCALE cascade | T75: `§CEIL_CASCADE ROOF_LIFT=1 WALL_SCALE=2 totalCmds=3` |
| getCeilingYCurrent fallback | T76: `§CEIL_FALLBACK` |
| All suites green | 79 + 98 + 63 + 114 = 354 pass, whitebox 34/36 |
| Commit | `96989ac0` on branch `full` |
