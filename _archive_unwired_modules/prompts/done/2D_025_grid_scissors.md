# 2D_025: Scissors-Driven Adaptive Grids + Save + Print

# !! DO NOT REMOVE — read the log after every run
# Scope: Grid lines reposition to structural alignments at section cuts,
#         save as static views, print as professional A3 sheets
# All geometry extracted from mesh, never invented
# Desktop only — no mobile for this phase
#
# !! LOCKED — DO NOT MODIFY existing 2D grid behavior:
#    - Bubble sizes, scaling curve, density filter — FROZEN
#    - Face-direction opacity thresholds — FROZEN
#    - Dim chain positioning and style — FROZEN
#    - Grid line overshoot, colours, view margins — FROZEN
#    - Panel layout, view button labels — FROZEN
#    - Mutual exclusion (Measure/2D) — FROZEN
#    - Long-press drag gate — FROZEN
#    These are working as intended. Do not tweak, refactor, or "improve"
#    unless the user explicitly requests a specific change.

## Scope (4 deliverables)

1. Scissors cut → grid lines reposition to detected structural alignments
2. Save current cut+grids as a named static view in the 2D panel
3. Photo button → A3 print preview with editable text fields, draggable
   positioning, colour contrast slider, auto-cleared labels, corporate details
4. Corporate details from `corporate.json` (SYSNOVA UK for now)

**NOT in scope:** mobile, RouteWalker, compliance, material schemes, combine
sections, Y-shaped rotated cuts, grid drag recompile. Those are future phases.

---

## Prerequisites: Current State

### Scissors (section_cut.js)
- `SectionCut.sectionCut(db, libDb, cutZ, storeyName, options)` — horizontal only
- Slices mesh triangles at Z=cutZ, returns per-element contour arrays
- The scissors button (`toggleSection()`) activates a horizontal clipping plane
- **Limitation:** horizontal plane only (normal = [0,0,1])
- **Limitation:** no event/callback when cut position changes

### Grids (grid_overlay.js + grid_dims.js)
- `GridDims.detectGrids(db)` clusters column centres into X and Y lines
- X/Y/Z-axis grid lines with bubbles, dim chains, density filter
- Face-direction opacity fades back-facing grids
- Mutual exclusion with Measure mode

### Print (print_sheet.js)
- A3 canvas (2480x1754 px at 150dpi), preview overlay with Save/Close
- Title block with building name, storey, date, volume, class counts
- Scale bar + north arrow
- B&W greyscale filter on viewport capture

---

## Deliverable 1: Scissors Cut → Grid Alignment

### Interaction Flow

```
1. User in free navigation (unlocked)
2. Grids active (2D icon highlighted)
3. User clicks scissors → horizontal cut plane appears
4. User positions the cut (existing drag/slider)
5. ON CUT CHANGE:
   a. detectGridsAtPlane(db, cutZ) finds columns/walls crossing the cut
   b. Grid lines reposition to detected alignments at cutZ
   c. Dim chains rebuild between new positions
   d. Contours render (wall outlines with filled thickness)
6. User moves cut up/down → grids follow (debounced 200ms)
7. Scissors off → original grids restore instantly
```

### New: detectGridsAtPlane(db, cutZ, planeNormal)

File: `grid_dims.js` — new function, append only.

```sql
-- Elements whose vertical extent crosses cutZ (horizontal cut)
SELECT m.guid, m.ifc_class, t.center_x, t.center_y
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class IN ('IfcColumn','IfcWall','IfcWallStandardCase')
  AND (t.center_z - t.bbox_z/2) <= cutZ
  AND (t.center_z + t.bbox_z/2) >= cutZ
```

Cluster `center_x` values → X-axis grid lines.
Cluster `center_y` values → Y-axis grid lines.
Same gap-based clustering as existing `detectGrids()`.

**Confidence:** only emit grid line if ≥3 elements align (within tolerance).
No structure found → grids disappear, status: "No structural alignment here".

### New: section_cut.js event hook

Append only — expose cut position changes:

```javascript
SectionCut.onCutChange = null; // callback slot
// Inside existing cut positioning logic, after cutZ changes:
if (SectionCut.onCutChange) SectionCut.onCutChange(cutZ);
```

### Grid Repositioning (grid_overlay.js)

When scissors active AND grids active:
- Hook `SectionCut.onCutChange`
- Create `gridGroup_scissors` (separate THREE.Group)
- Hide original `gridGroup`
- Rebuild grid lines at cut elevation (not ground level)
- On scissors off: dispose `gridGroup_scissors`, restore `gridGroup`

### Performance
- Debounce 200ms on cut drag
- Skip if cut moved < 0.1m
- bbox pre-filter on SQL (not full triangle scan for detection)
- Triangle intersection only for contour generation (existing code)
- Target: < 100ms per cut position

### Files Modified
| File | Change | Risk |
|------|--------|------|
| grid_dims.js | Add `detectGridsAtPlane()` | Low — new function |
| section_cut.js | Add `onCutChange` callback | Low — 3 lines, append |
| grid_overlay.js | Scissors listener, adaptive grid group | Medium — state |

### Witness Claims
- W-SCISSORS-1: Horizontal cut → grids reposition to detected XY alignments
- W-SCISSORS-2: Move cut → grids follow within 200ms
- W-SCISSORS-3: Scissors off → original grids restored, no leaked state
- W-SCISSORS-4: No grid line shown with < 3 aligned elements
- W-SCISSORS-5: Static views (GF, Front, etc.) unaffected

---

## Deliverable 2: Save Cut as Static View

### Interaction Flow

```
1. Scissors active, adaptive grids visible at a cut
2. User clicks "Save View" button (new, in grid panel)
3. Prompt: section name (default: "Section @Z={cutZ}m")
4. Current state saved:
   - Cut position + normal
   - Detected grid positions
   - Contour data (cached)
5. New button appears in grid panel alongside GF, L1, Front...
6. Clicking it restores that exact view
```

### Storage

Saved in the building's SQLite DB:

```sql
CREATE TABLE IF NOT EXISTS saved_sections (
  id INTEGER PRIMARY KEY,
  name TEXT,
  cut_value REAL,
  plane_normal TEXT,       -- JSON: [0,0,1]
  crop_bbox TEXT,          -- JSON: {minX,maxX,minY,maxY} or null
  detected_grids TEXT,     -- JSON: {xLines, yLines}
  timestamp TEXT
);
```

Contours are NOT cached — regenerated on load (they depend on mesh data
which may change). Grid positions ARE cached (lightweight).

### Panel Integration

```
[GF] [L1] [Front] [Back] [Left] [Right] [Roof] [🔓]
[Section @3.2m] [Section @6.8m]  [Save ✚]
```

- "Save ✚" button appears only when scissors is active
- Saved section buttons styled differently (dashed border?)
- Long-press on saved section → rename / delete options

### Files Modified
| File | Change | Risk |
|------|--------|------|
| grid_overlay.js | Save/load logic, panel buttons | Medium |
| grid_overlay.js | DB table creation on first save | Low |

### Witness Claims
- W-SAVE-1: Save section → appears in panel
- W-SAVE-2: Click saved section → restores cut + grids + contours
- W-SAVE-3: Saved sections persist across page reloads (in DB)
- W-SAVE-4: Delete saved section → removed from panel and DB

---

## Deliverable 3: Professional A3 Print Sheet

### What Changes from Current print_sheet.js

Current: auto-renders viewport + title block, downloads PNG.
New: interactive preview with editable fields and controls.

### Print Preview Panel UX

```
┌──────────────────────────────────────────────────────┐
│  [Save PDF]  [Save PNG]  [Close]                     │
│  ┌─────────────────────────┐  ┌────────────────────┐ │
│  │                         │  │ Text Fields:       │ │
│  │   A3 SHEET PREVIEW      │  │ Title: [________]  │ │
│  │   (live-updating)       │  │ Subtitle: [_____]  │ │
│  │                         │  │ Notes: [________]  │ │
│  │   Building plan with    │  │ Drawn by: [______] │ │
│  │   grids, dims, contours │  │ Checked: [_______] │ │
│  │                         │  │ Scale: [auto]      │ │
│  │                         │  │                    │ │
│  │   ┌──────────────────┐  │  │ Contrast: ──●──── │ │
│  │   │ SYSNOVA UK       │  │  │ (slider 0-100%)   │ │
│  │   │ Title Block      │  │  │                    │ │
│  │   └──────────────────┘  │  │ Paper: [A3 ▼]     │ │
│  └─────────────────────────┘  └────────────────────┘ │
│  ↕ Drag panel to reposition                          │
└──────────────────────────────────────────────────────┘
```

### Features

**Editable text fields** (right panel):
- Title (default: building name)
- Subtitle (default: view name — "Ground Floor", "Section @3.2m")
- Notes (free text — user adds construction notes)
- Drawn by / Checked by (default from corporate.json)
- Scale (auto-computed, display only)

**Colour contrast slider:**
- 0% = current viewport colours (full colour)
- 50% = desaturated with boosted contrast
- 100% = full B&W high contrast (current default)
- Live preview updates as slider moves
- User finds the best combo for their printer

**Auto-positioned labels:**
- Grid bubbles: auto-offset to avoid overlapping dimension text
- Dimension numbers: placed in clear space (not on top of contour lines)
- Grid lines: extend to sheet margin with bubble at edge
- Algorithm: for each label, check collision with existing labels,
  shift perpendicular to line until clear

**A3 media fitting:**
- Viewport auto-scales to fit building + grids + dims within printable area
- Margins: 10mm each side (standard A3 print margin)
- Title block: fixed height at bottom (25mm)
- Corporate box: fixed position bottom-right within title block

**Draggable panel:**
- The entire print preview panel is draggable (reuse `_makeDraggable`)
- User can position it to see the 3D view underneath while adjusting

### Corporate Details (corporate.json)

```json
{
  "company": "SYSNOVA UK",
  "address": "71-75 Shelton Street, Covent Garden, London WC2H 9JQ",
  "phone": "+44 20 7946 0958",
  "email": "bim@sysnova.co.uk",
  "website": "www.sysnova.co.uk",
  "logo_text": "SYSNOVA",
  "registration": "Company No. 12345678"
}
```

Loaded once on first print. Falls back to "BIM OOTB" if file not found.
Rendered in the title block bottom-right cell.

### Title Block Layout (on the A3 sheet)

```
┌─────────────────────────────────────────────────────────────┐
│                    VIEWPORT (plan/section)                   │
│                                                             │
│         Grid lines, bubbles, dimensions, contours           │
│                                                             │
│                    Scale bar (bottom-left)                   │
│                              North arrow (top-right)        │
├──────────────────────────────────┬──────────────────────────┤
│ Title: Hospital Ward Block       │ SYSNOVA UK               │
│ Subtitle: Ground Floor Plan      │ 71-75 Shelton Street     │
│ Notes: For construction          │ London WC2H 9JQ          │
│ Date: 2026-05-07                 │ +44 20 7946 0958         │
│ Drawn: R.D. Oon  Checked: ___   │ www.sysnova.co.uk        │
│ Scale: 1:100                     │ BIM OOTB                 │
├──────────────────────────────────┴──────────────────────────┤
│ Vol: 2450 m³  Floor: 820 m²  Elements: 1,247               │
│ Wall: 245  Door: 42  Window: 38  Column: 16  Slab: 8       │
└─────────────────────────────────────────────────────────────┘
```

### Files Modified
| File | Change | Risk |
|------|--------|------|
| print_sheet.js | Major rewrite — interactive preview | Medium |
| corporate.json | New file — company details | None |
| index.html | Load corporate.json (optional, onerror safe) | Low |

### Witness Claims
- W-PRINT-1: Preview panel shows live A3 sheet with all fields editable
- W-PRINT-2: Contrast slider updates preview in real-time
- W-PRINT-3: Labels auto-positioned — no overlapping text
- W-PRINT-4: Corporate details from corporate.json rendered in title block
- W-PRINT-5: Save PNG downloads correct A3 resolution (2480x1754)
- W-PRINT-6: Panel is draggable
- W-PRINT-7: Grid lines extend to sheet margin with bubbles at edge

---

## Deliverable 4: corporate.json (SYSNOVA UK)

Create `deploy/dev/corporate.json` with SYSNOVA UK details.
Loaded by print_sheet.js with `fetch('corporate.json')`.
Falls back gracefully if missing.

---

## Implementation Order

1. **corporate.json** — create file (5 min)
2. **detectGridsAtPlane + onCutChange hook** — the detection engine (Step 1)
3. **Grid repositioning on scissors** — wire it up (Step 2)
4. **Save section to DB + panel** — save/load/delete (Step 3)
5. **Print sheet rewrite** — preview panel with fields + slider + auto-labels (Step 4)

Steps 1-3 are one session. Step 4 (print) is a separate session due to
the auto-label-positioning algorithm complexity.

## Files Summary

| File | Deliverable | Change |
|------|-------------|--------|
| grid_dims.js | 1 | `detectGridsAtPlane()` — new function |
| grid_scissors.js | 1 | Scissors listener, adaptive grids, 3-axis support |
| grid_overlay.js | 1,2 | State accessor, GridScissors wiring |
| tools.js | 1 | `onSectionSliderChange/onSectionOff` callbacks, `localClippingEnabled` fix |
| section_cut.js | 1 | `lookupGeometry` fallback for BLOB-only DBs |
| grid_door_arcs.js | 1 | `extractLeafAxis` — real closed-polygon contours |
| print_sheet.js | 3 | Major rewrite — interactive preview |
| corporate.json | 4 | New file — SYSNOVA UK details |
| index.html | 1,3,4 | grid_scissors.js tag, version bumps |

## DONE — Deliverable 1: Scissors Cut → Grid Alignment (2026-05-07)

### Commits
- `07ba3781` feat: scissors-driven adaptive grids (2D_025 D1) — 3-axis cut detection
- `78e1d5c5` fix: relabel grids after filtering + detect beams/members at cut plane
- `ad9a1432` fix: geometry lookup fallback + door arc contour unwrapping
- `c93f58aa` fix: door arcs from real geometry — leaf axis extraction

### What was built
- `grid_dims.js`: `detectGridsAtPlane(db, cutZ)` — Z-range filtered detection,
  `filterStructural`/`thinGrids` hoisted to module scope, sequential relabelling
- `grid_scissors.js`: new IIFE module (init pattern like GridDrag), all 3 axes,
  debounced slider listener, dispose/restore lifecycle, lineMeshes registration
- `tools.js`: `onSectionSliderChange`/`onSectionOff` callbacks (2 lines),
  `renderer.localClippingEnabled` asserted on scissors ON/OFF (pre-existing bug fix)
- `section_cut.js`: `lookupGeometry` falls back to BLOB-only query when
  `vertex_count`/`face_count` columns missing — infers from byte lengths
- `grid_door_arcs.js`: `extractLeafAxis(pts)` — finds door leaf panel from closed
  polygon bbox (long axis = width, short axis = thickness). Replaces line-segment
  assumption. `contourPoints()` unwraps `{points, isOuter}` objects.
- Tests: T98-T104 (scissors), T11/T11b/T58 updated, T15/T56/T66/T67 fixed.
  114/114 pass.

### Architecture decisions
- Hook in `tools.js` (slider callback), not `section_cut.js` (pure computation)
- Separate `grid_scissors.js` module, not inline in grid_overlay.js
- Contour extraction from real mesh triangles — wall thickness 290mm/95mm verified

### Remaining (D1 fine-tuning, future session)
- Grid lines pointing to no structure (confidence threshold, ≥3 aligned elements)
- Missed grid chances at wall endpoints / partial-height elements
- Stair step symbol extraction (grid_stairs.js — dedicated module)
- Roof element removal from floor plan contours
- Opening-to-opening dimensioning (A-C, D-E cross-wire lines)

## Open Questions (scoped to this phase)

1. Save PDF: use browser `window.print()` with CSS `@media print`, or
   generate PDF client-side (jsPDF)? → Recommend PNG first, PDF later
2. Contrast slider: CSS filter on canvas, or re-render with adjusted palette?
   → Recommend CSS filter (live, no re-render cost)
3. Auto-label positioning: greedy placement or constraint solver?
   → Recommend greedy (simpler, fast, good enough for grid labels)
4. Should saved sections include contour cache or regenerate on load?
   → Regenerate (contours depend on mesh data, cache would be stale if DB changes)
