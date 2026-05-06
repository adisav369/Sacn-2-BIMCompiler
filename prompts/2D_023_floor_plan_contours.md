# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Integrate section_cut.js contours into grid overlay floor plan mode
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: SPEC — not yet implemented

# 2D_023 — Floor Plan Contours in 3D Overlay

## Context — What Exists

### Grid Overlay (grid_overlay.js) — S247, working
- 2D button toggles grid lines as Three.js scene objects in the 3D viewer
- Grid detection via `GridDims.detectGrids()` — columns first, walls fallback
- Measurement panel (draggable), on-scene dimension chains (tier 1 + tier 3)
- Elevation presets: GF, L1, F, R, L, S, Roof with ortho camera lock
- GF/L1 applies a simple THREE.Plane clipping cut ~1m above slab
- Theme-aware (dark/light), furniture skipped from clip

### Section Cut Engine (section_cut.js) — proven, working
- `SectionCut.sectionCut(db, libDb, cutZ, storeyName)` → 2D contours
- `SectionCut.sliceMesh(vertices, faces, cutZ)` → line segments
- `SectionCut.chainSegments(segments, tolerance)` → closed contours
- Produces **real mesh cross-sections** — wall thickness, door openings, window profiles
- Door arcs are quarter-circle contours from IfcDoor mesh intersection
- Window/glass appears as thinner contours
- Already proven: `2D_Layout/output/SVG/SH_FLOOR_*.svg` shows correct results

### What the First Grid Overlay Attempt Got Right
- The earliest version (before elevation presets were added) was clean:
  - Simple camera presets that used building envelope directly from DB
  - No recalculation of frustum ratios or aspect adjustments
  - Elevation views (F/R/L/S) were proportionally correct — no axis elongation
  - **The 'S' (side) view showed correct proportions** before adjustments were added
- Subsequent iterations introduced skew on side elevations by recalculating halfW/halfH with margin and aspect corrections

## What Went Wrong Along the Way
1. **Elevation aspect correction drift** — computing `halfW = halfH * aspect` distorted the building proportions. The first attempt didn't do this and looked right.
2. **Forced theme toggle** — toggling light/dark on every view lock/unlock caused state confusion when user had already toggled manually.
3. **Floor plan via clipping plane** — shows a flat 3D cut but not architectural contours (no wall thickness, no door arcs, no window profiles). The proper approach is section_cut.js contours rendered as 2D lines.
4. **Inventing wall thickness** — early discussions considered computing T1/T2 labels for wall types. Wrong approach. The mesh cross-section IS the thickness. Extract, don't compute.

## The Task

Integrate `SectionCut.sectionCut()` output into the grid overlay GF/L1 floor plan mode, rendering 2D contours as Three.js line objects on the floor plane.

### §1 — What to Extract (Not Invent)

**All floor plan geometry comes from `section_cut.js` mesh slicing:**

| Element | How it appears | Source |
|---------|---------------|--------|
| Wall cross-section | Two parallel lines showing real thickness | Mesh contour at cut plane |
| Door opening | Gap in wall contour | IfcDoor mesh removes wall at opening |
| Door arc (swing) | Quarter-circle polyline | IfcDoor mesh contour = arc shape |
| Window | Thinner lines in wall gap | IfcWindow mesh contour (glass profile) |
| Column | Small rectangle/circle section | IfcColumn mesh contour |
| Furniture | Full outline (NOT cut) | Projected from above, not sliced |

**Nothing is invented.** Wall thickness is the distance between inner and outer contour lines — extracted from geometry, not from a parameter table.

### §2 — Rendering Contours as Three.js Lines

For each element returned by `sectionCut()`:
1. Element has `.contours` = array of polylines `[[[x0,y0],[x1,y1],...], ...]`
2. Convert each contour to Three.js coordinates via `A.ifc2three(x, y, cutZ)`
3. Create `THREE.Line` from the points, at the groundY level
4. Style by IFC class:
   - `IfcWall` → thick dark line (stroke-width equivalent)
   - `IfcDoor` → medium line + arc contour
   - `IfcWindow` → thin blue/grey line
   - `IfcColumn` → thick dark line (same as wall)
5. Add all contour lines to `gridGroup` so they toggle with grid mode

### §3 — Wall Thickness Legend

Since wall thickness comes from the contour, measure it per wall:
- For each IfcWall contour set, compute the width between parallel segments
- Group by thickness (e.g. T1=100mm, T2=200mm, T3=250mm)
- Show in the Grid Dimensions panel below the dimension rows:
  ```
  Wall Types
  T1  100 mm  (partitions)
  T2  200 mm  (external)
  ```
- On the floor plan, label at least one instance of each type

### §4 — Separate Concerns

The rendering should be **data-driven from a template**, not hardcoded:

```json
{
  "floor_plan": {
    "cut_offset_m": 1.0,
    "styles": {
      "IfcWall": { "color": "#000000", "weight": 2.0 },
      "IfcDoor": { "color": "#000000", "weight": 1.0, "arc": true },
      "IfcWindow": { "color": "#4488CC", "weight": 0.5 },
      "IfcColumn": { "color": "#000000", "weight": 2.0 },
      "IfcFurnishingElement": { "color": "#888888", "weight": 0.5, "clip": false }
    }
  }
}
```

This avoids hardcoding class-specific logic in the renderer. The template can be extended for elevation views later. Store as `grid_styles.json` or embed as a const in `grid_overlay.js`.

### §5 — What NOT to Do

- Do NOT recompute camera frustum aspect ratios — the first attempt was correct
- Do NOT force theme toggle on elevation views — only floor plan needs light bg
- Do NOT invent wall dimensions — extract from contour geometry
- Do NOT recalculate building proportions — use `envCache` directly
- Do NOT modify `section_cut.js` — it's proven and tested
- Do NOT touch elevation views (F/R/L/S/Roof) in this task — floor plan only

### §6 — Implementation Order

1. Call `SectionCut.sectionCut(A.db, A.libDb, cutZ, storey)` in GF/L1 mode
2. Convert contours to Three.js line objects, styled by class
3. Add to `gridGroup` with `userData.isFloorContour = true`
4. Show wall type legend in panel
5. Test on SampleHouse (proven reference) and Duplex

### §7 — Files

| File | Action |
|------|--------|
| `grid_overlay.js` | Modify — add contour rendering in floor plan mode |
| `section_cut.js` | Read only — call its API |
| `grid_dims.js` | Unchanged |
| `index.html` | May need `<script src="section_cut.js">` if not already loaded |

### §8 — Fix Elevation Skew

The side elevation ('S') view is currently skewed — height axis elongated. The fix is to revert the frustum calculation to the first working version:
- Use building dimensions directly: `halfW = bldDepth/2`, `halfH = bldHeight/2`
- Do NOT apply margin multiplier or aspect ratio correction to individual axes
- The ortho camera's aspect is handled by Three.js OrbitControls naturally
- If the view doesn't fill the screen, that's OK — architectural drawings have margins

### §9 — References

- SH floor plan SVG proof: `2D_Layout/output/SVG/SH_FLOOR_20260428_0636.svg`
- TBKLTN House reference PDF: `2D_Layout/input/TBKLTN_House.pdf`
- Section cut engine: `deploy/dev/section_cut.js`
- Grid overlay: `deploy/dev/grid_overlay.js`
- 2D Layout spec: `docs/2D_LAYOUT.md`
- Grid overlay spec: `prompts/2D_022_grid_overlay_mode.md`
