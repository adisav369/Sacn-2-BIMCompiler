# ⚠ DO NOT REMOVE — S272 BOM Engine Phase 3: doc_canvas Integration
# Scope: Wire bom_engine into doc_canvas.js — L0→L1→L3 flow, DISC/Next, instance-aware diff
# Read the log after every run.

## Activity Category
BOM/geometry — read feedback files: architecture, card-first, logs only, no deploy without proof

## What Was Done (S272 Phase 1+2)
Phase 1: Pure math engine — 4 files, 247 tests, ALL PASS.
- `bom_strategies.js`: 8 placement strategies (UNIFORM, PACKED, CENTERED, etc.)
- `bom_constraints.js`: fitCheck, overlapCheck, bufferCheck, mandatoryCheck, computePhantom
- `bom_diff.js`: diff(current[], target[]) → KEEP/MOVE/SCALE/ADD/REMOVE commands
- `bom_node.js`: BOMNode + recompose() Template Method (5 steps: FIT→RESERVE→FILL→CASCADE→VALIDATE+PHANTOM)
- Bug found + fixed: `_stepFill` now subtracts reserved space from mandatory children

Phase 2: Data layer — 3 files + 2 migrations, 89 tests, ALL PASS.
- `bom_tree.js`: materializeLevel() (DB→BOMNode), getAffectedBranch() (attach map bridge)
- `bom_grid.js`: GridLineManager (level-scoped grids, shared keys, editable, clamping)
- `W022_bom_engine_columns.sql`: 10 new m_bom_line columns
- `W022b_sh_bom_seed.sql`: SH rules seeded (walls=FIXED, windows=UNIFORM, floor=SPAN)

Advanced whitebox: 37 tests — property-based (fast-check, 2100 random inputs), metamorphic (5 relations), golden master, state recording (_trace).

**Total: 373 tests, ALL PASS. Real SH_BOM.db tested end-to-end.**

## What To Do Now — Phase 3 Only

Wire the BOM engine into `doc_canvas.js`. The viewer already has:
- L0: `grid_kinematics.js` — attach map, dragGrid() → TRANSLATE/SCALE commands
- BatchedMesh/InstancedMesh scene with `_guidToSlot` / `_guidToInstance` lookups
- `recomposeAfterGridDrag()` applying L0 commands to meshes
- BOM.db loaded via `bom_walker.js` with phase/Gantt stepping

The integration adds L1 (BOM recompose) after L0 and before rendering.

### Read First
1. `docs/BOM_ENGINE_SPEC.md` §4, §9, §15 — Template Method, L0 bridge, stack integration
2. `deploy/dev/doc_canvas.js` — current recomposeAfterGridDrag (lines 2070-2160)
3. `deploy/dev/grid_kinematics.js` — L0 engine, dragGrid() return format
4. `deploy/dev/bom_engine/bom_node.js` — recompose() API, _trace
5. `deploy/dev/bom_engine/bom_tree.js` — materializeLevel(), getAffectedBranch()
6. `deploy/dev/bom_engine/bom_diff.js` — diff() → command array

### Build Order (Steps 3a-3d)

**Step 3a: L0→L1→L3 Flow in recomposeAfterGridDrag** (~80 lines changed)
- After L0 kinematics commands execute, fire L1:
  1. `getAffectedBranch(bomNodes, attachMap, gridId)` → parent BOMNodes
  2. For each affected parent: `parent.recompose(parent.hostAABB)` → target state
  3. `diff(currentState, targetState)` → BOM commands (ADD/REMOVE/MOVE/SCALE)
  4. Apply BOM commands to scene (Step 3c)
- L0 runs every frame (fast). L1 runs debounced (16ms).
- L3 validation: log conflicts, no blocking.
- §-tagged log: `§BOM_RECOMPOSE parent=SH_GF_STR reserved=5 filled=10 phantom.w=2400`

**Step 3b: DISC/Next Controller**
- Existing `_phaseIndex` and `_activeDisc` stay.
- On "Next" press: `materializeLevel(db, currentBomId, parentAABB)` → new BOMNodes
- New BOMNodes get their own GridLineManager grids: `gridMgr.addGridsForLevel(nodes, level)`
- On "Prev": `gridMgr.removeGridsForLevel(level)`, dematerialize
- On DISC switch: reset depth to 0, clear all level grids
- §-tagged log: `§BOM_NEXT level=1 children=8 grids=3`

**Step 3c: Instance-Aware Diff Execution** (~60 lines)
- Map diff commands to Three.js operations:
  - `MOVE` → `InstancedMesh.setMatrixAt(idx, newMatrix)` or BatchedMesh `setMatrixAt`
  - `SCALE` → same path, matrix includes scale
  - `ADD` → clone from sibling: `InstancedMesh.count++`, set new matrix
  - `REMOVE` → zero-scale matrix (hide), retain for undo
- Use existing `_guidToInstance` / `_guidToSlot` lookups
- No new rendering code — just matrix updates

**Step 3d: kernel_ops Integration** (~20 lines)
- Log `BOM_RECOMPOSE` op to kernel_ops after diff execution
- Payload: `{ parentBomId, commands: [...] }`
- Undo = replay in reverse (REMOVE↔ADD, MOVE reversed)
- Same pattern as RouteWalker's `ELEMENT_PLACE` logging

### Code Conventions
- Same IIFE pattern as all deploy/dev/ files
- `var` not `let/const`, prototype methods
- §-tagged console.log for every significant state change
- No new files — changes go into existing `doc_canvas.js`
- BOM engine modules loaded via `<script>` tags (same as grid_kinematics.js)

### Gate
- L0 + L1 both run on SH grid drag (§-logs prove it)
- Next/Prev materializes/dematerializes BOM levels (§-logs prove it)
- DISC switch resets depth (§-log proves it)
- Diff commands applied to meshes (§-logs prove MOVE/ADD/REMOVE counts)
- kernel_ops log entry created on BOM_RECOMPOSE
- Existing 373 tests still pass
- No new unit tests needed — this is wiring, verified by §-logs

### Do NOT
- Touch bom_strategies.js, bom_constraints.js, bom_diff.js, bom_node.js, bom_tree.js, bom_grid.js
- Touch grid_kinematics.js or grid_state.js
- Create new rendering abstractions
- Deploy anything
- Add Playwright tests (§-logs first)
- Move to Phase 4 without user confirmation
