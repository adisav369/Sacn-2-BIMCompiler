# ⚠ DO NOT REMOVE — S272 BOM Engine Phase 1: Pure Math
# Scope: Build bom_strategies.js, bom_constraints.js, bom_diff.js, bom_node.js
# Read the log after every run.

## Activity Category
BOM/geometry — read feedback files: architecture, card-first, logs only

## What Was Done (S271)
Spec-only design session. BOM_ENGINE_SPEC.md v5 written, watchdog-reviewed, DeepSeek-reviewed.
- 19 sections, 7 abstract interfaces, 4-phase roadmap
- Java DAGCompiler contracts mapped (IBOMChildLine, BOMVisitor, IHostable, IRepeatable, IRoutable)
- Existing stack confirmed: kernel_ops (undo log), Three.js (BatchedMesh/InstancedMesh), SQLite (sql.js) all stay as-is
- Override promotion mechanics specified (§12.5)
- DeepSeek review log in §20

## What To Do Now — Phase 1 Only

Build the pure math engine. 4 files, ~115 tests, NO DOM, NO Three.js, NO SQL. All runnable with `node`.

### Read First
1. `docs/BOM_ENGINE_SPEC.md` — the complete spec (especially §3-§4 strategies, §4 recompose steps)
2. `deploy/dev/grid_kinematics.js` — L0 engine pattern to follow (IIFE, pure math, no side effects)
3. `DAGCompiler/src/main/java/com/bim/compiler/contract/IBOMChildLine.java` — the Java interface

### Build Order (Steps 1a-1d)

**Step 1a: `deploy/dev/bom_engine/bom_strategies.js`** (~130 lines, ~35 tests)
- 8 pure functions: UNIFORM, PACKED, CENTERED, REPEAT, FIXED, SPAN, ROUTE, LINEAR
- Each: `(params) → {positions[], count}` — no state, no side effects
- LINEAR is alias for UNIFORM
- ROUTE returns `{segments[]}` (stub — delegates to RouteWalker in Phase 3)
- Test file: `deploy/dev/tests/test_bom_strategies.js`
- Edge cases: 0 available space, 1 child, count > max_count, count < min_count, childSize > available

**Step 1b: `deploy/dev/bom_engine/bom_constraints.js`** (~100 lines, ~25 tests)
- `fitCheck(nodeAABB, hostAABB)` → ok | conflict
- `overlapCheck(siblings[])` → ok | [{a, b, overlap_mm}]
- `bufferCheck(siblings[], bufferMm)` → ok | [{a, b, deficit_mm}]
- `mandatoryCheck(children[])` → ok | {missing: []}
- `computePhantom(hostInner, childrenAllocated)` → {w, d, h}
- Test file: `deploy/dev/tests/test_bom_constraints.js`

**Step 1c: `deploy/dev/bom_engine/bom_diff.js`** (~80 lines, ~15 tests)
- `diff(currentState[], targetState[])` → Command[]
- Commands: KEEP, MOVE, SCALE, ADD, REMOVE
- Sort: REMOVE first, then MOVE/SCALE, then ADD
- Test file: `deploy/dev/tests/test_bom_diff.js`

**Step 1d: `deploy/dev/bom_engine/bom_node.js`** (~200 lines, ~40 tests)
- BOMNode class with recompose(hostAABB) Template Method
- 5 steps: FIT → RESERVE → FILL → CASCADE → VALIDATE+PHANTOM
- Depends on bom_strategies.js and bom_constraints.js
- Test file: `deploy/dev/tests/test_bom_node.js`
- Key tests: single parent with UNIFORM children, 2-level cascade, mandatory survives shrink, override excluded from FILL, PHANTOM computation

### Code Conventions
- IIFE pattern: `(function(exports) { ... })(typeof module !== 'undefined' ? module.exports : (window.BomStrategies = {}));`
- This allows both `node` testing and browser `<script>` loading
- Follow grid_kinematics.js style: `var` not `let/const`, prototype methods, JSDoc
- No ES6 modules — plain script tags, same as rest of deploy/dev/

### Gate
All ~115 tests pass with `node deploy/dev/tests/test_bom_*.js`. No external dependencies. Pure functions proven.

### Do NOT
- Touch doc_canvas.js, grid_kinematics.js, grid_state.js, or any existing file
- Create any Three.js or DOM dependencies
- Query any database
- Deploy anything
- Move to Phase 2 without user confirmation
