# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Interactive grid overlay on 3D viewer — replaces static 2D dimension pipeline
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: SPEC — not yet implemented

# 2D_022 — Grid Overlay Mode

## Motivation

The previous 2D approach generated static DXF drawings with pre-computed dimension
tiers (tier 1/2/3 offsets), a triage panel for crowded bays, and an info panel on
the right showing grid references. That pipeline was:

- **Cumbersome** — multiple tabs, hard to get right, over-engineered formatting
- **Redundant** — the model already knows wall/column alignments
- **Static** — user can't adjust or verify; just receives a drawing

**New direction:** impose architectural grid lines directly onto the 3D viewer.
The grid IS the dimension system. Measurements are a natural byproduct of grid
positions. The user adjusts the grid in-place — no tab switch, no info panel.

## What This Replaces

- DXF dimension generation pipeline (§4-§7 of 2D_ARCHITECTURAL_LAYOUT.md)
- Triage panel for crowded bay text (§15)
- Grid reference info panel (§6.3)
- The separate 2D tab workflow for reading bay spacing

**What remains:** The 3D section-cut engine (section_cut.js) is still valid for
generating floor plan geometry. Grid overlay complements it, not replaces it.

## Concept

When the user presses the 2D icon, the viewer enters **Grid Mode**:

1. Grid lines appear overlaid on the model, auto-detected from geometry
2. Each grid line has a **bubble** (circle with label) at both ends
3. Lines snap to alignable positions — not freeflow
4. A small **measurements panel** shows grid-to-grid distances
5. User confirms, adjusts, or adds/removes grids
6. Grids persist in the DB for reload

## Spec

### §1 — Grid Mode Entry

- **Trigger:** user clicks the 2D toolbar icon (existing button, repurposed)
- **On enter:**
  - **Camera stays where it is** — no auto-transition. User pans/rotates freely in 3D.
    Grid lines are scene objects, they move with the model naturally.
  - Auto-detection runs (§2) to propose initial grid positions
  - Grid lines render as 3D scene geometry (§3)
  - Measurements panel opens (§5)
- **On exit:** press 2D icon again or Esc — grid geometry removed from scene
  (but grid data persists in DB for next activation)
- **Log:** `§GRID_MODE state=enter|exit`

### §2 — Auto-Detection of Grid Positions

Grid positions are inferred from the model, not invented.

**Priority sources (in order):**

1. **IfcGrid** — if the IFC file contained grid data, use it directly
   (most IFC exports from Revit/ArchiCAD include IfcGrid entities)
2. **Saved grids** — if grids were previously confirmed and saved to DB, reload them
3. **Wall/column alignment analysis** — scan structural elements:
   - Cluster X-coordinates of wall start/end points → vertical grid candidates
   - Cluster Y-coordinates of wall start/end points → horizontal grid candidates
   - Cluster column center positions → refine candidates
   - Merge clusters within snap tolerance (§4)
   - Rank by number of aligned elements (more alignments = stronger candidate)

**Output:** ordered list of grid lines per axis, each with:
- `axis`: 'X' or 'Y'
- `position`: world coordinate (metres)
- `label`: auto-assigned (§3)
- `source`: 'ifc_grid' | 'saved' | 'detected'
- `confidence`: number of aligned elements (for detected grids)

**Log:** `§GRID_DETECT source={ifc_grid|saved|detected} xCount={N} yCount={N}`
**Log per line:** `§GRID_LINE axis={X|Y} pos={metres} label={A|1} confidence={N} source={src}`

### §3 — Grid Rendering

Grid lines are **3D scene objects** — thin black lines that live in the scene
and move with the model during pan/rotate/zoom. They read as "annotation" not
"geometry" — always visible, never occluding.

**Geometry per grid line (e.g. grid "A" at X=5.0):**

```
Floor line:  thin black line on the ground plane (Y=0), running the full
             depth of the building in the perpendicular axis direction.
             This is the primary reference line.

Side extensions:  the line extends past the building bounding box on both
                  sides (overshoot ~2m or 10% of building extent, whichever
                  is larger). The bubble sits at the end of the extension.
                  This mimics how architectural grid bubbles appear at the
                  edges of a drawing — outside the building footprint.

Vertical rise (optional):  a faint vertical line from ground to roof height
                           at the building edge, so grids are visible in
                           elevation views too. Same thin black style.
```

**Visual style:**

- **Color:** black (`#000000`) — reads as annotation, distinct from model geometry
- **Line weight:** thin (1px, constant screen-space via `THREE.Line` with `linewidth`)
- **Opacity:** fully opaque — thin black doesn't compete visually, no need for transparency
- **Material:** `THREE.LineBasicMaterial` — see-through by nature (just lines, no surfaces)
- **Bubble:** circle sprite (diameter ~18px screen-space) at both ends of the extension,
  black outline with white fill, label text centered (CSS2DRenderer or sprite)
- **Selected grid:** line thickens to 2px, bubble fill turns light yellow
- **Dashed option:** `THREE.LineDashedMaterial` for the floor line if user prefers
  (solid by default — thin black solid is cleaner than dashed at most zoom levels)

**Label convention:**

- **X-axis grids (vertical lines):** letters — A, B, C, D, ... Z, AA, AB, ...
- **Y-axis grids (horizontal lines):** numbers — 1, 2, 3, 4, ...
- Auto-assigned left-to-right (letters) and bottom-to-top (numbers)
- Labels auto-reassign when grids are added/removed/reordered

**Scene integration:** grid lines are added to a dedicated `THREE.Group` ("gridGroup")
which is a child of the scene. No separate render pass needed — lines don't z-fight
because they're `THREE.Line` objects (no depth-fill). The group is toggled
visible/invisible on mode enter/exit.

### §4 — Snap Behaviour

Grids snap to **alignable positions** — they are not freeflow draggable.

**Snap targets (in priority order):**

1. Wall face planes (exterior face of outer walls = building envelope)
2. Wall centreline planes (structural grid convention)
3. Column centre points
4. Opening edges (door/window jambs — for sub-bay grids)
5. Existing grid positions on the perpendicular axis (intersection alignment)

**Snap tolerance:** configurable, default 200mm world units.
When dragging, the grid line jumps between valid snap positions.
Visual feedback: snap target highlights briefly when grid locks onto it.

**Behaviour:**
- Grid line only rests at a snap position — releasing between snaps reverts to nearest
- Hold Shift to override snap (allow arbitrary position for unusual geometries)
- Snap positions are pre-computed on grid mode entry and cached

**Log:** `§GRID_SNAP label={A} from={old_pos} to={new_pos} target={wall_face|centreline|column|opening}`

### §5 — Measurements Card (Floating Glass)

A transparent floating card in the 3D scene — same UI pattern as the bbox info
cards that follow the model around. Not a fixed side panel.

**Visual style:**
- **Background:** semi-transparent blue-tinted glass (`rgba(20, 60, 120, 0.55)`)
  with `backdrop-filter: blur(8px)` — frosted glass effect
- **Text:** black (`#000`), clean sans-serif, high contrast against the blue glass
- **Border:** subtle light border (`rgba(255,255,255,0.2)`) for edge definition
- **Border-radius:** rounded corners (8px)
- **Position:** `CSS2DObject` attached to a scene anchor point near the building edge,
  so it pans/rotates with the model. Default anchor: top-right of building bbox.
- **Draggable:** user can reposition the card if it obscures something

**Content:**

```
 A-B   6.000 m
 B-C   4.500 m
 C-D   6.000 m
 A-D  16.500 m  total

 1-2   5.000 m
 2-3   3.600 m
 1-3   8.600 m  total
```

**Rules:**
- Adjacent spans shown as `AB = 6.000 m` (compact label pairs, no arrows)
- Total span shown at bottom of each axis group
- Distances update live as user drags a grid line
- Units: metres (from model), displayed to 3 decimal places
- Card auto-sizes to content; scrollable only if > ~12 rows
- Separator line between X-axis and Y-axis groups

**No between-grid labels on the model** — the glass card is the single source
of dimension text. Keeps the 3D view clean; all numbers in one place.

### §6 — User Interactions

| Action | Effect |
|--------|--------|
| **Click** grid line | Select it (highlight, show position in panel) |
| **Drag** grid line | Move to next snap position; measurements update live |
| **Double-click** empty space | Create new grid line at nearest snap position |
| **Delete / Backspace** on selected grid | Remove grid line; labels re-sequence |
| **Right-click** grid line | Context menu: rename label, delete, show aligned elements |
| **Scroll wheel** | Zoom (as normal, grids scale appropriately) |
| **Esc** | Exit grid mode |
| **Shift+drag** | Override snap — place grid at arbitrary position |

**Touch/mobile:**
- Long-press grid line = select
- Drag selected = move to snap
- Double-tap empty space = create
- Long-press selected = context menu (rename/delete)

**Log:** `§GRID_EDIT action={create|move|delete|rename} label={A} pos={metres}`

### §7 — Grid Persistence

Grids are saved to the building's SQLite DB so they survive reload.

**Table: `grids`**

```sql
CREATE TABLE IF NOT EXISTS grids (
  id        INTEGER PRIMARY KEY,
  axis      TEXT NOT NULL CHECK(axis IN ('X','Y')),
  label     TEXT NOT NULL,
  position  REAL NOT NULL,  -- world coordinate in metres
  source    TEXT NOT NULL DEFAULT 'detected',
  confirmed INTEGER NOT NULL DEFAULT 0,  -- 1 = user confirmed/edited
  created   TEXT NOT NULL DEFAULT (datetime('now')),
  modified  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_grids_axis_label ON grids(axis, label);
```

**Lifecycle:**
- Auto-detected grids saved with `confirmed=0`
- Any user edit (drag, create, rename) sets `confirmed=1`
- On reload, `confirmed=1` grids take priority over re-detection
- User can "reset to auto" via a button (deletes all, re-runs §2)

**Log:** `§GRID_SAVE count={N} confirmed={N}`
**Log:** `§GRID_LOAD count={N} confirmed={N} source=db`

### §8 — What the 2D Icon Now Does

**Before (2D_021):** switched to a separate 2D canvas tab showing a static DXF rendering
with dimension tiers, info panel, layer controls.

**After (2D_022):** toggles grid lines as 3D scene objects on the existing viewer.
The 3D model stays visible. Grids are part of the scene — they pan/rotate/zoom
with the model. No camera change, no separate view. User stays in spatial context.

The section-cut floor plan (section_cut.js) remains available as a separate feature
if the user wants a pure 2D plan view. But dimensioning is handled by the grid overlay.

### §9 — Implementation Phases

**Phase A — Core grid overlay (MVP)**
- Grid mode toggle (§1)
- Auto-detection from wall alignments (§2, source=detected only)
- Grid rendering with bubbles (§3)
- Measurements panel (§5)
- Click to select, drag to snap, double-click to create, Delete to remove (§6)
- Grid persistence (§7)

**Phase B — Polish**
- IfcGrid import (§2, source=ifc_grid)
- Shift+drag snap override
- Right-click context menu (rename, show aligned elements)
- Mobile/touch interactions
- Optional ortho top-down shortcut button (not auto — user-triggered)

**Phase C — Export**
- Export grid dimensions as CSV/table
- Optionally stamp grid lines onto section-cut floor plan output

### §10 — Diagnostic Log Tags

| Tag | When | Content |
|-----|------|---------|
| `§GRID_MODE` | Mode enter/exit | `state=enter\|exit` |
| `§GRID_DETECT` | Auto-detection complete | `source={src} xCount={N} yCount={N}` |
| `§GRID_LINE` | Each detected/loaded line | `axis={X\|Y} pos={m} label={L} confidence={N}` |
| `§GRID_SNAP` | User snaps a grid | `label={L} from={old} to={new} target={type}` |
| `§GRID_EDIT` | Any user edit | `action={create\|move\|delete\|rename} label={L} pos={m}` |
| `§GRID_SAVE` | Persist to DB | `count={N} confirmed={N}` |
| `§GRID_LOAD` | Load from DB | `count={N} confirmed={N} source=db` |

### §11 — Files

| Purpose | Path |
|---------|------|
| Grid overlay JS | `deploy/dev/grid_overlay.js` (new) |
| Measurements panel CSS | inline in index.html or `deploy/dev/grid_overlay.css` (new) |
| DB schema addition | `grids` table in building DB |
| Section cut (unchanged) | `deploy/dev/section_cut.js` |
| 3D viewer (modified) | `deploy/dev/index.html` — add grid mode toggle |

### §12 — Non-Goals

- This is NOT a full CAD grid system. No angular/radial grids (maybe later).
- No parametric dimension chains. The grid IS the dimension system.
- No DXF dimension generation. Grids replace that pipeline.
- No printed-scale formatting (tier offsets, tick angles). Screen-native only.
