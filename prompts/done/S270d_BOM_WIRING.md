# ⚠ DO NOT REMOVE — S270d BOM Wiring: Script Tags + First Visible Cascade
# Scope: Wire BOM engine into browser. B1+B2 from RED_PILL.md §11.6.
# Read the log after every run.

## Activity Category
BOM/geometry — read feedback files: architecture, logs only, no deploy without proof, test before deploy

## What's Done
- S272: BOM engine complete — 438 tests. 6 files in `deploy/dev/bom_engine/`, `disc_rules.json`.
- S270c: Y-axis ceiling drag, grid_recompose.js extraction, 354 grid tests.
- S273: Red Pill UI hardening, save/open, grid guards.
- `grid_recompose.js` already has `_fireBomRecompose()`, `materializeBomLevel()`, `_applyBomDiffCommand()` — all guarded with `if (typeof BomTree === 'undefined') return`.
- BOM engine modules use IIFE pattern: `(function(exports){ ... })(window.BomStrategies = {})` — ready for `<script>` loading.

## What To Do — B1 + B2 Only

### B1: Add `<script>` tags to index.html

Add BOM engine scripts to `deploy/dev/index.html`. They must load BEFORE `grid_recompose.js`:

```
grid_state.js
bom_engine/bom_strategies.js    ← NEW
bom_engine/bom_constraints.js   ← NEW
bom_engine/bom_diff.js          ← NEW
bom_engine/bom_node.js          ← NEW (depends on strategies + constraints)
bom_engine/bom_tree.js          ← NEW (depends on bom_node)
bom_engine/bom_grid.js          ← NEW
bom_engine/bom_rules.js         ← NEW
grid_kinematics.js
grid_recompose.js               ← already references window.BomTree etc.
doc_canvas.js
```

Also load `rules/disc_rules.json` — fetch it in `grid_recompose.js` `setDiscRules()` or inline a `<script>` that assigns `window._bomDiscRulesJson`.

### B2: Verify Wall→Window cascade fires

After B1, the existing wiring should activate:
1. User enters Red Pill → presses Next → `materializeBomLevel()` populates `_bomNodes` from BOM.db
2. User drags grid → `recomposeAfterGridDrag()` → L0 kinematics → L1 `_fireBomRecompose()`
3. `_fireBomRecompose()` → `getAffectedBranch()` → `parent.recompose()` → `diff()` → `_applyBomDiffCommand()`

**Prove with §-logs:**
- `§BOM_NEXT level=1 children=N grids=M` — materializeLevel fired
- `§BOM_RECOMPOSE parent=SH_GF_STR reserved=X filled=Y phantom.w=Z` — L1 fired after grid drag
- `§BOM_L1_DONE moves=A adds=B removes=C scales=D` — diff commands applied

If logs don't appear, trace the chain:
- Is `_bomNodes` populated? Check `_bomNodes.length` after Next.
- Is `_kinEngine` built? Check `§RECOMPOSE_ENGINE` log.
- Does attach map have entries matching `_elementRef` on BOMNodes? That's the bridge.

### Read First
1. `docs/RED_PILL.md` §11.6 — B1-B5 task table with what-exists/what's-missing
2. `deploy/dev/grid_recompose.js` — `materializeBomLevel()` (line ~583), `_fireBomRecompose()` (line ~420)
3. `deploy/dev/index.html` — current script order
4. `deploy/dev/bom_engine/bom_tree.js` — `materializeLevel()` API
5. `deploy/dev/bom_engine/bom_node.js` — `recompose()` → `_trace` for debugging

### Gate
- BOM engine loads in browser without console errors
- `§BOM_NEXT` log appears on Next press (proves materializeLevel ran)
- `§BOM_RECOMPOSE` log appears after grid drag (proves L1 ran)
- All 438 BOM engine tests still pass (`node deploy/dev/tests/test_bom_*.js`)
- All 354 grid tests still pass
- SW cache version bumped

### Do NOT
- Modify any `bom_engine/*.js` file — the engine is proven, don't touch it
- Modify `grid_kinematics.js` or `grid_state.js`
- Deploy to production (dev only)
- Add Playwright tests — §-logs first
- Move to B3 (RouteWalker wiring) without user confirmation
- Edit RED_PILL.md — watchdog session handles doc updates

### After B1+B2 Proven
If §-logs show the cascade working, BUG-3 (phase-aware recompose) is solved — `materializeLevel()` reads CURRENT AABBs. Report to watchdog session for RED_PILL.md update.

Next: B3 (ROUTE→RouteWalker, ~10 lines) only after user confirms B1+B2 visually.
