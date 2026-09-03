# 2D_030: Grid UX Troubleshooting — Browser-First Fix Session

# ⚠ DO NOT REMOVE — Read the log after every run. Scope: deploy/dev/ ONLY.

## Status: PLANNING (2026-05-09) — fix session, NO new features

## Prime Directive

**BROWSER FIRST. F12 FIRST. LOG FIRST.** Every fix must be verified in the browser.
Do NOT code without loading the viewer. Do NOT declare fixed without F12 evidence.

Load SimpleCastle. Open F12. Fix what's broken. Move to next building. Repeat.

---

## Issues — in priority order

### P1: Grid lines not aligned with walls (Y-axis / sideways)
**Symptom:** X-axis grid lines (1,2,3...) align OK after snap fix. Y-axis (A,B,C...) still off.
**Debug:**
1. Load SimpleCastle, enter 2D GF view
2. F12: check `§GD_WALL_WEIGHT axis=Y` — are Y-running walls voting center_y?
3. Check `§GD_OPP_CLUSTER axis=Y clusters=N` — how many Y clusters?
4. Compare cluster positions to visible wall centerlines
5. If few Y votes: wall orientation threshold 1.5× may be too strict for short walls
**Files:** `deploy/dev/grid_dims.js` lines 330-350 (orientation detection)
**Verify:** grid line sits ON the wall, not between walls

### P2: GF floor plan — roof contours, missing door arcs
**Symptom:** SimpleCastle GF still shows roof elements. Door arcs missing.
**Debug:**
1. Enter GF view, F12: check `§SC_BAND_FILTER` — how many excluded?
2. Check `§SC_CLASSES withContour=[...]` — is IfcRoof in the list? (should NOT be)
3. Check `§DOOR_ARC_STOREY doors=N` — N should be > 0
4. If doors=0: check `§DOOR_ARC_CLASSES` — what IFC classes are present at this cutZ?
**Files:** `deploy/dev/section_cut.js` lines 520-548 (band filter), `deploy/dev/grid_overlay.js` renderContoursForView
**Verify:** no roof shapes visible in GF plan. Door arcs visible at each door.

### P3: Opening dimension lines (jamb ticks + connecting line)
**Symptom:** Windows have jamb ticks but the connecting dimension line may not be visible.
**Debug:**
1. Enter GF view, zoom to a window
2. F12: check `§DOOR_ARC_WINDOW` logs — are windows being processed?
3. Look for the thin blue connecting line between the two jamb ticks
**Files:** `deploy/dev/grid_door_arcs.js` lines 370-430
**Verify:** two short ticks + one connecting line per window opening

### P4: Saved section restore — smart save composite not rendering
**Symptom:** Clicking a saved SmartCut card jumps to GF, shows nothing of the saved cuts.
**Debug:**
1. Save a SmartCut with 2+ dwell points
2. Unlock (🔓), then click the saved card
3. F12: check `§SAVE_SECTION restore dwells=` — is dwells null or populated?
4. Check `§SAVE_SECTION composite layer=` — are layers being rendered?
5. If dwells=null: the DB load path didn't parse detected_grids column
**Files:** `deploy/dev/grid_overlay.js` restoreSection function, loadSavedSections
**Verify:** composite contours visible from multiple Z levels

### P5: Old saved cuts stuck — ✕ delete not working
**Symptom:** Old localStorage saved cuts have ✕ but clicking does nothing.
**Debug:**
1. Click ✕ next to an old dotted-border saved cut
2. F12: check for `§SAVE_CUT deleted name=` or `§SAVE_CUT delete key=`
3. If no log: pointerup not firing — check event propagation
4. If log appears but card stays: wrong localStorage key
5. Run in console: `Object.keys(localStorage).filter(k => k.includes('section'))` — what keys exist?
**Files:** `deploy/dev/grid_overlay.js` saved-cut-del handler
**Fix if stuck:** user can run `localStorage.clear()` in console as nuclear option

### P6: Panel close ✕ — verify it works
**Symptom:** Panel has ✕ close button (added this session). Verify it dismisses the panel.
**Debug:** click ✕ in panel header. Panel should disappear.
**Files:** `deploy/dev/grid_overlay.js` grid-panel-close handler

### P7: Panel dimension values — verify they update on drag
**Symptom:** Bay widths in panel show large numbers that "don't change."
**Debug:**
1. Drag a grid line in 3D
2. Watch the panel bay widths — do they update?
3. The values are in mm (e.g. 3600). A 50mm drag = 3600→3650 — subtle change.
**Files:** `deploy/dev/grid_drag.js` rebuildAnnotations → rebuildPanel

### P8: Wall outline visibility on large buildings
**Symptom:** Walls become hairlines on terminal/hospital-scale buildings.
**Debug:**
1. Load a large building (if available), enter GF view
2. Zoom out to see full building
3. Are wall contours visible as solid black shapes?
4. F12: check `minOutlineW` computation — is the ribbon being added?
**Files:** `deploy/dev/grid_contours.js` buildRibbon + minOutlineW

---

## Session Isolation

**Category:** Browser JS — zero Java, zero live/ edits.
**Prefix:** `S2D30-`
**Files OWNED:** same as 2D_028 + 2D_029 (grid_dims, section_cut, grid_overlay, grid_drag, grid_door_arcs, grid_contours, grid_scissors, kernel_ops)

## Test Protocol

For each fix:
1. Add `§` log tag if missing
2. Load building in dev viewer
3. Open F12, filter console for the relevant `§` tag
4. Verify the values make sense
5. Visual check in the viewport
6. Add wiring test in spec 32 if missing
7. `node deploy/dev/tests/audit_specs.js` must exit 0

## Buildings to Test

| Building | What to verify |
|----------|---------------|
| SimpleCastle | GF arcs, roof exclusion, grid alignment, saved section restore |
| Duplex | Basic grid detection, door arcs |
| HITOS (if available) | Multi-storey, large building wall visibility |

## Context from Previous Session (2026-05-09)

### What was built and deployed
- `snapGrids` no longer moves line positions — display-only bay rounding
- Band filter unconditionally excludes IfcRoof from floor plans
- `detectStoreys` called once not twice in sectionCut
- `clearStoreyBandVisibility` on 2D off — corruption fix
- `restoreSection` no longer toggles scissors on/off — GF trap fix
- Panel ✕ close button added
- Color-coded buttons: green=default, yellow=saved, orange=latest
- Scissors bookmarks on slider rail (🔖 add, × delete, ticks on rail, tap to jump)
- Smart save dwell tracker (flash on capture, red outline markers, FIFO, proximity dedup)
- kernel_ops v4: compact, sessionStart, GRID_DETECT audit
- Undo/redo buttons (↩↪) — skip audit ops, only undo GRID_MOVE
- Grid drag UX: red ghost (origin) + blue proposed, status hints
- Opening connecting dimension lines (jamb ticks + line)
- Adaptive wall ribbon outline for large buildings
- 34 tests in spec 32

### Architecture decisions
- **localStorage vs kernel_ops**: bookmarks = localStorage (survives reload). kernel_ops = in-session undo chain (lives in runtime DB memory only, lost on reload unless DB exported).
- **Save button removed from 2D panel** — bookmarks (🔖) on scissors slider is the only save. Smart save dwell badge shows count but no Save button.
- **Saved section delete**: zombie bug fixed — `deleteSavedSection` now clears localStorage backup before `loadSavedSections` to prevent re-import loop.
- **Old saved cuts delete**: inline onclick + global `deleteOldSavedCut()` — bypasses _makeDraggable pointer capture.
- **Scissors off on all views**: not just floor plans with clip — scissors conflicts with all ortho views.

### Key bugs found and fixed
- `snapGrids` was moving grid line positions (cumulative 200mm+ drift on large buildings)
- `detectStoreys` called twice per sectionCut (duplicate SQL)
- Band filter class exclude was unconditional → then conditional → back to unconditional for IfcRoof
- `restoreSection` toggled scissors on then off → triggered `onOff()` → rebuilt panel with GF grids → GF trap
- `clearStoreyBandVisibility` not called on 2D exit → meshes stayed hidden → corruption
- `deleteSavedSection` → `loadSavedSections` → DB empty → localStorage fallback re-imported deleted card → zombie
- Undo was undoing GRID_DETECT (audit) instead of GRID_MOVE (user action)
- Double GRID_DETECT commits from grid_dims.js + grid_scissors.js

### What still needs browser verification (P1-P8 in this prompt)
All code changes deployed to ootb-dev but NOT browser-verified on SimpleCastle.
Next session MUST open browser first, check F12 logs, fix what doesn't match.
