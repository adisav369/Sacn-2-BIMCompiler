# 2D_031: Card-First View Model — Persistent View Cards

# ⚠ DO NOT REMOVE — Read the log after every run. Scope: deploy/dev/ ONLY.

## Status: CARD LOGIC DONE — outstanding 2D UX debt below (2026-05-10)

## Concept

A **View Card** is a frozen, named, persistent snapshot of a 2D view state.
It is the first-class object the user works with — not the raw building DB.

**Card = the view contract:**
- Which storey, which cutZ
- Which IFC classes are hidden (roof, covering, foundation)
- Camera position + zoom level
- Which contours were rendered

**User workflow:**
1. Enter 2D mode → auto-generates GF card + L1 card
2. User clicks a card → exact view restored (cutZ, hidden classes, camera)
3. User clicks an element in card view → IFC data popup (only from visible set)
4. User toggles sunglasses → white background. Hides panels (−). Screenshots.
5. Cards persist in DB — survive reload, sharable via DB export.

**Why card-first:**
- No IFC version needed — same DB, different lens
- Roof hidden in GF card ≠ roof deleted from building
- Picking is scoped to card's visible set (no roof click-through in GF)
- User curates cards per building — GF, L1, canteen zoom, mechanical room
- Print = card + sunglasses reverse + panel hide

---

## Architecture

### Schema Change — ONE new column

```sql
-- Existing table (grid_overlay.js line 696):
CREATE TABLE IF NOT EXISTS saved_sections (
  id INTEGER PRIMARY KEY,
  name TEXT,
  cut_value REAL,
  plane_normal TEXT,
  crop_bbox TEXT,
  detected_grids TEXT,   -- currently stores dwell JSON, reuse
  timestamp TEXT
);

-- ADD:
ALTER TABLE saved_sections ADD COLUMN view_state TEXT;
-- view_state = JSON:
-- {
--   "hidden_classes": ["IfcRoof", "IfcCovering"],
--   "camera": { "x": 0, "y": 50, "z": 0, "zoom": 1.2 },
--   "storey": "Ground Floor",
--   "mode": "floor"
-- }
```

No new tables. One column. Backward-compatible (NULL = legacy card, use defaults).

### Files to Edit

| File | Change |
|------|--------|
| `grid_views.js` | Already refactored (v293). `classifyMesh()` takes hide set as param instead of hardcoded `HIDE_IN_FLOOR`. |
| `grid_overlay.js` | `saveSectionToDb()` — capture current view_state JSON. `restoreSection()` — read view_state, pass hidden_classes to `applyFloorClip`. `loadSavedSections()` — parse view_state column. |
| `grid_overlay.js` | Auto-create GF + L1 cards on first grid mode entry if no cards exist. |
| `section_cut.js` | No change — band filter + class exclude already works. |
| `grid_contours.js` | No change — white/black fill is correct. |

### Function Responsibilities (atomic, single-purpose)

**grid_views.js** (already clean):
- `classifyMesh(ifcClass, retainSet)` → change to `classifyMesh(ifcClass, retainSet, hideSet)` — hideSet comes from card
- `applyFloorClip(A, env, viewMode, cutZ, hideSet)` — hideSet param instead of hardcoded HIDE_IN_FLOOR
- `clearFloorClip(A)` — unchanged, restores everything

**grid_overlay.js** — new/modified functions:
- `captureViewState()` → returns `{ hidden_classes, camera, storey, mode }` JSON
- `applyViewState(state)` → sets cutZ, passes hideSet to applyFloorClip, positions camera
- `saveSectionToDb(name)` → calls captureViewState(), stores in view_state column
- `restoreSection(sec)` → reads sec.view_state, calls applyViewState()
- `autoCreateCards()` → on first entry, if no saved_sections: create "GF" + "L1" cards

### Pick Scoping (click → IFC data)

When a card is active, element picking should skip hidden classes:
- `picking.js` or `grid_overlay.js` click handler checks `obj.visible === true`
- Already works — Three.js raycaster skips `visible=false` meshes
- No code change needed if `applyFloorClip` hides roof meshes via `visible=false`

---

## Implementation Steps

### P1: Schema + Save (capture view state on save)
1. `ensureSavedSectionsTable` — add `view_state TEXT` column (ALTER TABLE IF NOT EXISTS pattern)
2. `captureViewState()` — reads current `_activeView`, `HIDE_IN_FLOOR` set, camera pos/zoom
3. `saveSectionToDb(name)` — includes view_state JSON in INSERT
4. Test: save a card, check DB has view_state column with valid JSON

### P2: Restore (apply view state on card click)
1. `loadSavedSections()` — parse view_state from SELECT
2. `restoreSection(sec)` — extract hidden_classes from view_state, pass to `applyFloorClip`
3. `classifyMesh` + `applyFloorClip` — accept hideSet param (override default HIDE_IN_FLOOR)
4. Test: save GF card → unlock → click card → exact same view restored with roof hidden

### P3: Auto-create default cards
1. On first `toggleGridOverlay` when `savedSections.length === 0`:
   - Auto-save "GF" card with floor mode + cutZ + default hide set
   - Auto-save "L1" card if building has > 1 significant storey
2. Cards appear in panel immediately — user sees them as starting point
3. Test: load building with no saved_sections → GF + L1 cards appear

### P4: Camera persistence in card
1. `captureViewState()` stores ortho camera zoom + pan offset
2. `applyViewState()` restores camera position after lockView
3. Test: zoom into a room → save card → unlock → click card → same zoom

---

## What NOT to do

- Do NOT invent footprint-area calculators or opening-average cutZ — floorZ + 1.2m is proven
- Do NOT hide ALL meshes — clip planes + roof hide is the correct approach
- Do NOT add ribbon/artificial thickness — white/black true geometry only
- Do NOT change section_cut.js band filter — it works with the 1.5m clamp
- Do NOT add dwell tracking or smart save — removed per user request

## Context from 2026-05-09/10 Session

### What works (DO NOT BREAK)
- Undo/redo grid drag
- Grid line detection (clusterVotes + snap-to-structural)
- Door arcs on SampleHouse (3 doors, 4 windows)
- Cost panel with variance (Δ Qty, Δ Vol) and ✕ close button
- Panel toggle −/+ button (hides all UI chrome)
- Band filter with 1.5m minimum clamp
- White/black contour fill (true reverse for print)
- `grid_views.js` refactored — atomic single-responsibility functions

### What's deployed (SW v293 — verify all uploaded)
- `grid_views.js` — refactored, `classifyMesh` + `HIDE_IN_FLOOR` for IfcRoof
- `grid_contours.js` — white fill on dark, black fill on light, no ribbon
- `grid_overlay.js` — door-count storey ranking, floorZ+1.2 cutZ
- `section_cut.js` — band filter original + 1.5m clamp
- `cost_panel.js` — variance + ✕ close button
- `tools.js` — Save Cut button gated by isIn2DView
- `panels.js` — toggleAllPanels hides all UI chrome
- `index.html` — `.swipe-hidden` global, panel-toggle-btn always visible

### What was done this session (2026-05-10)
- Card system: `view_state` column, `captureViewState`, `queryStoreyGuids`, one-pass `restoreSection`
- Card = one SQL (storey GUIDs from IndexedDB) → one scene pass (hide/fade/retain/clip) → contours
- `FADE_IN_FLOOR`: IfcSlab/IfcPlate → opacity 0.08 (not solid, not hidden)
- `autoCreateCards()`: GF + L1 cards on first grid entry
- `clearCardView()`: restores all meshes on exit
- `lockView(cameraOnly)`: card skips applyFloorClip, owns visibility directly
- Save button restored: always available when scissors ON (was wrongly gated by isIn2DView)
- `grid_scissors.js` restored: dwell/snap code was accidentally removed, put back
- Fleet test: 42 tests across all deployed buildings, all pass in 3.7s

### Outstanding 2D UX debt (from 2D_022–2D_030, never fully resolved)
1. **Grid line alignment** — center-vote snap-to-structural deployed but not verified on all buildings. Check `§GD_WALL_WEIGHT` logs. See `2D_024_editable_grid_lines.md`.
2. **Grid drag highlight** — no hover effect on draggable lines, user can't tell which are draggable. See `2D_024 §Click grid line`.
3. **IFC popup on element click** — clicking furniture/doors in card view should show IFC info. Raycaster hits visible meshes (card sets correct visible set), but verify info card popup works.
4. **Cost panel + variance** — cost_panel.js has Δ Qty/Δ Vol columns, verify they show on card restore.
5. **Terminal walls** — 3 walls on GF vs 420 slabs. Slab fade helps but contours may still be missing for curtain walls.
6. **DX door arcs** — `§DOOR_ARC_SKIP reason=no_leaf`, geometry BLOBs lack door panels.
7. **HITOS GF verification** — confirm wall visibility with floorZ+1.2 after band clamp fix.
8. **Grid lines per card storey** — grid detection runs once at entry, not per-card. Future: re-detect at card's cutZ.

### Bugs found and fixed by CTFL analysis (11 total)
| Bug | Technique | Fix |
|-----|-----------|-----|
| IfcCovering | Equivalence | Wall tiles wrongly hidden — removed from HIDE_IN_FLOOR |
| A | Boundary: no-guid | Ground plane/InstancedMesh visible in card → hide first |
| C | Boundary: falsy zero | opacity=0 never saved → `== null` check |
| F | Data flow | captureViewState parsed status bar (never matched) → query DB |
| G | Data flow | localStorage INSERT missing view_state column |
| J | State transition | Stale clip planes leaked across card→card switches |
| K | State transition | Faded opacity stuck at 0.08 across card switches |
| N | State completeness | clipShadows never reset on hide/fade/retain paths |
| O | State completeness | needsUpdate not set on hide/retain paths |
| U | UX flow | Deleting active card left meshes in card state |
| W | Scope analysis | Contour overlay meshes got clip planes from card pass |

### Test suite — `deploy/dev/tests/specs/`
- `35-card-first-views.spec.js` — 7 logic tests: classifyMesh, restoreSection architecture, cleanup, auto-create, view_state, slab fade
- `36-card-first-browser.spec.js` — 18 tests: classifyMesh (14 classes + custom hideSet), restoreSection (11 checks), clearCardView (5 checks), autoCreateCards, view_state round-trip, lockView cameraOnly, Save button not gated, SampleHouse composition (hide+fade+retain+clip=total), fleet DB (30 buildings), door-count ranking (improved 19), classifyMesh on all 49 IFC classes, BUG K (unfade before reset), state completeness (6 paths × 4 properties), BUG U (delete clears card), grid alignment, stale lines, section_cut SLICE_CLASSES, BUG W (contour skip)
- **Fleet test: 30 buildings in 5 seconds, every composition verified**
- 41 specs / 417 tests / 1035 expects — all pass

### Critical: Deployed vs Local MISMATCH (found 2026-05-10 end of session)
Another session modified grid_overlay.js, tools.js, grid_scissors.js AFTER our deploy.
Deployed ootb-dev has STALE code:
- `tools.js` deployed has old Save button on scissors (removed by S251 session)
- `grid_overlay.js` deployed missing Save ✚ in grid panel (added by S251 session)
- `grid_scissors.js` deployed missing BUG-1 dwell fix (added by S251 session)
**FIRST ACTION next session: redeploy ALL `deploy/dev/*.js` to ootb-dev, then verify with curl.**

### Verification script: `deploy/dev/tests/card_verify.js`
Run: `node deploy/dev/tests/card_verify.js` — 3 seconds, checks:
- HIDE_IN_FLOOR (IfcRoof ✓, NOT IfcCovering ✓), FADE_IN_FLOOR (IfcSlab ✓)
- Card: queryStoreyGuids, cameraOnly, own clipPlane, skip isContour, hide !guid
- Card: no band, no applyFloorClip, unfade before reset, stale clip clear
- Save button location, section_cut band filter, FILL_CLASSES, door arcs
- Grid detection: clustering, snap, wall weight
- Fleet: 30 buildings GF composition from real DB
**If any check ✗ — the code is wrong, fix before deploying.**

### Test mandate — what the test suite MUST verify (not done yet)
The Playwright tests are TOO SLOW and test string presence, not behavior.
`card_verify.js` tests CODE TRUTH. Extend it to cover:
1. **Deployed match** — curl each JS file, grep for key functions, compare to local
2. **GF storey correctness** — door-count ranking across fleet (30 buildings)
3. **Card composition math** — hide+fade+retain+clip = total for every building
4. **Section cut contour output** — SLICE_CLASSES covers walls/doors/windows
5. **Door arc generation** — extractLeafAxis, generateArcs present
6. **Grid alignment** — wall X/Y positions clustered, snap to structural
7. **Save button** — in grid panel (grid-save-section-btn), NOT in tools.js scissors
8. **State completeness** — every mesh path sets visible+clip+clipShadows+needsUpdate
9. **Contour skip** — isContour meshes not processed by card pass
10. **Opacity restore** — unfade previous card before new card, clearCardView restores all

### Next session
1. Redeploy ALL `deploy/dev/*.js` to ootb-dev — curl verify each
2. Run `node deploy/dev/tests/card_verify.js` — fix any ✗
3. Extend card_verify.js with deployed-vs-local check
4. Then `prompts/2D_024_editable_grid_lines.md` — grid drag highlight, hover, alignment
