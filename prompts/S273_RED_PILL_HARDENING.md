# ⚠ DO NOT REMOVE — S273 Red Pill Hardening: UI Features + SC Validation
# Scope: Complete unfinished Red Pill UI, seed SC BOM rules, validate on SampleCastle
# Read the log after every run.

## Activity Category
BOM/geometry + pipeline/debug — read feedback files: architecture, card-first, logs only, deploy discipline

## Context — Where We Are

S272 Phase 1-4 DONE: BOM recomposition engine, 438 tests. But the **Red Pill UI features are incomplete**. The engine exists but the user-facing wiring has gaps. SampleCastle (3,621 elements, 7 BOMs, 348 BOM lines, MEP discipline) is the validation target.

## Feature Audit — What's Missing

### F1: Save Design — STUBBED
**File:** `panels.js:895-900`
**Current:** `console.log('§DOC_SAVE materialize')` — placeholder only.
**What it should do:**
1. Serialize current grid state (`_xPositions`, `_zPositions`, labels) + `_phaseIndex` + `_activeDisc`
2. Export `kernel_ops` table as the design's edit history
3. Save to IndexedDB under a user-named key (e.g., "SC_Design_v1")
4. Optional: download as `.json` file
**§-log:** `§DOC_SAVE key=SC_Design_v1 ops=12 grids=6`

### F2: Open Design — STUBBED
**File:** `panels.js:888-893`
**Current:** `console.log('§DOC_OPEN list saved designs')` — placeholder only.
**What it should do:**
1. List saved designs from IndexedDB (show name + date + op count)
2. On select: restore grid positions, labels, phase index, active disc
3. Replay `kernel_ops` to restore element positions (recompose)
4. Mark `_kinEngineDirty = true` so next drag uses restored state
**§-log:** `§DOC_OPEN key=SC_Design_v1 ops=12 grids=6 restored=true`

### F3: Timeline Scrub Accuracy — IMPLEMENTED, BUG
**File:** `doc_canvas.js:1805-1854` (`_scrubToPhase`)
**Bug:** Scrubbing backward resets grid to envelope-only (A/B/1/2), losing all user-placed grid lines. Should preserve user grids across scrub.
**Fix:** Before scrub, snapshot user-placed grid lines (those not from auto-grid). After replay, re-add them.
**§-log:** `§DOC_SCRUB preserved=4 userGrids`

### F4: Rosetta Stone — CUD Grid Lines, Not Just Create
**File:** `doc_canvas.js:1184-1219` (`handleRosettaDrag`)
**Current:** Only Creates grid lines. No Update (reposition) or Delete from Rosetta.
**What it should do:**
- **Create:** ✓ Done — drag from Rosetta template to canvas places new grid line
- **Update:** Drag an existing grid line (already works via select-then-drag)
- **Delete:** Drag grid line back toward Rosetta zone (beyond envelope) → remove it
  - Alternative: long-press on grid bubble → delete confirmation
**§-log:** `§DOC_ROSETTA_DELETE axis=X label=C`

### F5: Grid Lines Only Move When Attached — NOT IMPLEMENTED
**File:** `doc_canvas.js:1518-1650` (drag handler)
**Current:** Any grid line can be dragged regardless of whether it has attachments. Moving a grid line with zero attachments is meaningless — it shifts nothing.
**What it should do:**
1. On drag start: check `_kinEngine.getAttachMap()[gridLabel]`
2. If no attachments → block drag, show status: "Grid C has no attached elements — place elements first"
3. If attachments exist → allow drag, show status: "Grid C: 5 walls, 2 slabs"
4. `_getGridAttachInfo()` already exists (line 1708-1721) but is display-only — wire it as a guard
**§-log:** `§DOC_GRID_DRAG_BLOCKED label=C reason=no_attachments`

### F6: SC BOM Rule Seed — BOM Lines Lack Engine Columns
**File:** New migration: `W023_sc_bom_seed.sql`
**Current:** SC's 348 m_bom_line rows all have `layout_strategy='LINEAR'`, no `mandatory`, `creates_grid`, `fill_axis`, or `allocated_*_mm` values. The Phase 2 columns exist (W022 migration added them) but are empty/default.
**What it should do:**
1. Classify SC elements by IFC class → assign strategies:
   - IfcWall/IfcWallStandardCase → `SPAN`, `mandatory=1`, `creates_grid=1`
   - IfcColumn → `FIXED`, `mandatory=1`, `creates_grid=1`
   - IfcWindow → `UNIFORM`, `fill_axis='x'`
   - IfcDoor → `FIXED`, `mandatory=1`
   - IfcSlab → `SPAN`, `mandatory=1`
   - IfcCovering → `UNIFORM`
   - IfcFlowSegment → `ROUTE` (MEP discipline)
   - IfcBeam → `SPAN`, `mandatory=1`
   - IfcRailing → `LINEAR`
2. Set `allocated_width_mm`, `allocated_depth_mm`, `allocated_height_mm` from element_transforms bbox
3. Set `element_ref` from elements_meta GUID
4. Set `storey` from IfcBuildingStorey association
**Gate:** `materializeLevel('SC_GF_STR')` returns children with strategies, `recompose()` positions them, rules fire.

## Build Order

| Step | What | File(s) | Gate |
|------|------|---------|------|
| 1 | F6: SC BOM seed migration | `W023_sc_bom_seed.sql`, apply to `SampleCastle_extracted.db` | materialize returns typed children |
| 2 | F5: Grid attachment guard | `doc_canvas.js` drag handler | §-log proves blocked+allowed |
| 3 | F4: Rosetta delete (drag beyond envelope) | `doc_canvas.js` handleRosettaDrag | §-log proves delete |
| 4 | F3: Timeline scrub grid preservation | `doc_canvas.js` _scrubToPhase | §-log proves user grids survive |
| 5 | F1+F2: Save/Open to IndexedDB | `doc_canvas.js` + `panels.js` | §-log proves save+restore cycle |
| 6 | Integration test on SC | Full cycle: load SC → Next through phases → place grids → drag → recompose → save → open → verify | §-logs prove all |

## SampleCastle Profile

- **Elements:** 3,621 (652 walls, 282 std walls, 279 slabs, 259 windows, 205 doors, 174 beams, 90 railings, 60 MEP pipes, 23 columns)
- **Storeys:** 5 floors + foundation + roof
- **BOMs:** 7 (BUILDING + 6 floors), 348 BOM lines
- **Disciplines:** ARC (walls/windows/doors/coverings), STR (columns/beams/slabs), MEP (pipes/distribution)
- **Why SC:** Complex multi-storey, multi-discipline, more elements than SH (65), has MEP, Dutch naming (tests i18n resilience)

## Code Conventions
- Same IIFE pattern, `var` not `let/const`, prototype methods
- §-tagged console.log for every significant state change
- No new files except the SQL migration
- Changes go into existing `doc_canvas.js`, `panels.js`
- No deploy — local validation only

## Do NOT
- Touch bom_engine/ files (Phase 1-4 locked, 438 tests)
- Touch grid_kinematics.js or grid_state.js
- Deploy anything
- Move to Terminal without user confirmation
