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
- `test_doc_canvas.js`: 54/54
- `test_s268_recompose.js`: 63/63
- `test_grid_modules.js`: 114/114
- `whitebox_regression.js`: 34/36 (2 pre-existing)

## KNOWN BUGS — Fix in next session

### BUG-1: Incremental delta double-counting (CRITICAL)
**Symptom:** Drag grid -6m, drag again -7m → elements move -13m instead of -7m total.
Pullback (+3m) still moves further negative.
**Root cause:** `recomposeAfterGridDrag()` computes **absolute delta from original**
(`currentPos - originalPos = -8.0`) but `_translateMesh()` **adds incrementally** to
the current mesh position. Second drag applies -8.0 on top of already-moved mesh.
**Fix:** Track `_lastAppliedDeltas` per grid line. Pass only the **incremental** change
`(newAbsoluteDelta - lastAppliedDelta)` to the engine. Reset on engine rebuild.
**Log evidence:**
```
§RECOMPOSE_GRID id=1 delta=-4.401 commands=1085   ← drag 1: correct
§RECOMPOSE_GRID id=1 delta=-11.344 commands=1085  ← drag 2: should be -6.9 incremental
§RECOMPOSE_GRID id=1 delta=-8.033 commands=1085   ← pullback: should be +3.3 incremental
```
**Test to add:** Drag +3, then drag +5 total → mesh at +5 not +8.

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

### BUG-4: SCALE commands not firing (all TRANSLATE)
**Symptom:** Log shows `translated=1085 scaled=0` — every element translates, none scale.
**Expected:** Walls spanning a grid line should SCALE (stretch), not translate.
**Root cause:** `_collectElementData` reads `bbox_x/bbox_y/bbox_z` from DB, but the
SC DB now has 3504 elements while `_getShownGuids()` returns phase GUIDs from BOM walk.
Need to verify that bbox lookup matches the shown GUIDs. Possibly the bbox query
returns rows keyed by a different GUID format.
**Fix:** Add §-tagged log in `_collectElementData` showing element count, bbox match
count, and sample bbox values. Then trace why no SPAN/EDGE relations form.

## What's Next — S270b (next session)

### Priority 1: Fix BUG-1 (incremental delta)
This is a one-variable fix: track `_lastAppliedDeltas[gridId]` in `doc_canvas.js`.
Compute `incrementalDelta = absoluteDelta - lastApplied`. Pass incremental to engine.
Add test: drag +3, drag +5 total, verify mesh at +5.

### Priority 2: Fix BUG-4 (all TRANSLATE, no SCALE)
Debug with §-tagged logs. If bbox data is missing or zero for all elements,
SPAN/EDGE can never trigger. The engine classification is correct (98/98 tests)
so the issue is in `_collectElementData`.

### Priority 3: Y-axis drag UI
The ceiling grid auto-places at eave Y (translucent disc visible). But the current
drag UI only handles X/Z axis lines. Need to wire Y-axis grid interaction:
- Click on ceiling disc → select CEIL grid
- Drag up/down → engine produces ROOF_LIFT + WALL_HEIGHT_SCALE cascade
- Status shows "Ceiling grid selected (1 ROOF_LIFT, 2 cascades)"

### Priority 4: BUG-2 and BUG-3 (triage)
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
