# 2D Architectural Layout — Specification

> **SACRED:** Archive SVGs at `/home/red1/bim-compiler/2D_Layout/archive/`
> are the pristine baseline reference. NEVER overwrite them. These are the
> earliest correct version — all improvement builds from here, never digresses.
> Do not disturb `drawing_writer.py` or `drawing_writer_dxf.py` rendering
> without visual verification against these archive SVGs.
>
> **HARD STOP:** Every DXF output must visually match the archive SVG for
> that view — panel position, grid positions, label placement, dimension
> layout. If it doesn't match, the code is wrong. Do not commit, do not
> continue. Compare proof SVG against archive SVG before declaring DONE.
> PNG output is no longer needed — SVG is sufficient.
>
> **TOP PRIORITY:** Gridlines and remaining pages (sections, elevations) are
> the active work. The floor/roof/side SVG rendering is locked.

## 0. What is a 2D Architectural Drawing

A 2D architectural drawing is a set of **coordinated orthographic projections**
of a building. Each view shows the same building from a different direction:

| View | What it shows | How it is made |
|------|--------------|----------------|
| **Floor plan** | Looking straight down. Walls, doors, windows, furniture. | Horizontal cut at 1.2m above floor level. Walls hit by the cut are drawn bold (section cut). Elements below the cut (furniture) are drawn light. |
| **Elevation** | Looking at one face of the building (front, rear, left, right). | Vertical projection onto a plane parallel to that face. Shows wall outlines, openings, roof silhouette, ground line. |
| **Roof plan** | Looking straight down at the roof only. | No section cut — just the roof outline, ridge line, slope arrows, eave overhang. |
| **Section** | Vertical cut through the building interior. | Like a floor plan rotated 90° — shows internal heights, floor slabs, roof structure. |

All views share the **same grid lines**. Grid A at position X in the floor
plan is the same Grid A at position X in every elevation. The grid is the
skeleton that ties the views together. A builder reads the grid label on the
floor plan, walks to the elevation drawing, finds the same label, and knows
the dimensions match.

**Grid lines are not walls.** A grid line marks where multiple building
elements align — wall faces, column centres, window rows. The grid position
is discovered from the building data (§4.2). The grid style is dash-dot
(§4.5) to distinguish it from solid building outlines. The circles at each
end (bubbles) carry the label. The dimensions between grids give the builder
the bay spacing.

**Line weight hierarchy** is what makes the drawing readable:
- **Bold (0.50mm)** — structure cut by the section plane (exterior walls, columns)
- **Medium (0.35mm)** — partitions cut by the section plane
- **Light (0.25mm)** — door/window frames, projected outlines
- **Thin (0.18mm)** — annotations: dimensions, grids, text, hatch
- **Hairline (0.13-0.15mm)** — furniture, hatch fill

A drawing without this hierarchy is a pile of lines. With it, the eye
immediately sees structure vs annotation.

**The sheet** is the paper the drawing lives on. It has a border, a title
block panel (right side, project metadata), a north arrow, a scale bar,
and the drawing title. Every sheet follows the same template so any
drawing from any project looks familiar to the reader.

## 1. Prime Rules

**R1 — No invention.** Every line, number, and label on a drawing must trace to
either the building database or the drawing template. If a value cannot be traced,
it must not exist.

**R2 — Measurements on paper.** A 2D drawing is arithmetic from DB coordinates,
rendered with line standards from the template. The LLM knows drafting conventions
from training. It queries the DB for numbers and the template for formatting.

**R3 — Two sources only.** Data comes from the compiled building DB (what + where).
Presentation comes from `drawing_template.json` and `2D.db` (how). Code invents
nothing — it connects these two sources.

**R4 — Template governs all formatting.** Line weights, font sizes, margins,
offsets, colours, label text, grid style — all from the template. The code reads
the template at render time. Users change the template to change the drawing.
The template values are calibrated to TB-LKTN (JKR Malaysian) professional
standard: bold cut walls (0.50mm), medium partitions (0.35mm), thin annotations
(0.18mm). The visual hierarchy — thick for structure, thin for annotation —
is what makes the drawing readable. Preserve this hierarchy.

**R5 — Reference is the archive.** The SVG outputs in `archive/` are the proven
reference. Any new output (DXF or SVG) must match the archive in content and
layout. Tests enforce this.

**R6 — Code logs forensically.** Every derivation is logged. If a line appears
in the output, the log shows where it came from. No external checking needed.

## 2. Process

The drawing writer follows this exact sequence. No step is skipped.

```
Step 1: LOAD TEMPLATE
        Read drawing_template.json → all layout values
        Read 2D.db → all label content
        Log: "Template loaded: paper={size}, scale={scale}, profile={profile}"

Step 2: QUERY DB
        Read elements_meta + elements_rtree → element positions
        Read spatial_structure → storey elevations, room names
        Log: "DB loaded: {n} walls, {n} doors, {n} windows, {n} furniture"

Step 3: DETECT STRUCTURAL BAYS (§4.2)
        Columns + boundary walls → grid axes
        Log each axis: "Grid {label} axis={x|y} pos={position}m source={element_type}"

Step 4: COMPUTE DIMENSIONS
        For each pair of consecutive grids: distance = grid[i+1].pos - grid[i].pos
        Overall = last.pos - first.pos
        Snap to nearest snap_module_mm from template
        Log each: "Dim {axis} {start}→{end} = {value}mm"

Step 5: SECTION CUT (floor plan only)
        Slice meshes at cut_height → 2D contours
        Log: "Section cut Z={z}m: {n} CUT, {n} BELOW, {n} ABOVE"

Step 6: INFER ROOMS
        Cluster furniture by type → room boundaries → centroids
        Look up room name from 2D.db [2d_room_label] table
        Compute area from furniture cluster bounding box (approximate)
        Log each: "Room {type} at ({cx},{cy}) area={area}m²"

Step 7: RENDER
        For each entity, read style from template, draw to output
        Every entity logs: layer, source (DB row or template field)

Step 8: VERIFY
        Count entities per layer
        Check all text traces to DB or template
        Log summary: "{n} entities, {n} layers, {n} texts"
```

The log is the forensic trail. If a line appears in the output, the log
must show where it came from.

## 3. Data Sources

### 3.0 Directory Layout

```
2D_Layout/
├── input/                          ← per-project inputs
│   ├── SH_extracted.db             ← SampleHouse compiled DB
│   ├── SH_2D.json                  ← SampleHouse output plan (master)
│   ├── DX_extracted.db             ← Duplex (when onboarded)
│   ├── DX_2D.json
│   └── 2D.db                       ← TB-LKTN label content (shared)
├── output/                         ← generated products
│   ├── DXF/                        ← DXF deliverables (2 per view max)
│   ├── SVG/                        ← SVG outputs (2 per view max)
│   └── dxf_diagnostic.txt
├── archive/                        ← sacred reference (never overwrite)
├── drawing_template.json           ← formatting (shared, all projects)
├── python/                         ← code
└── docs/                           ← specs
```

**Per-project input convention:**
1. Copy the compiled DB into `input/` with prefix: `SH_extracted.db`
2. Create `{PREFIX}_2D.json` in `input/` — the output plan for that project
3. Code reads the JSON to know what pages to generate and what to expect

**`{PREFIX}_2D.json` — output plan per project:**

```json
{
  "project": "SH",
  "building": "ifc4_sample_house",
  "db": "SH_extracted.db",
  "pages": [
    { "sheet": "A-01", "title": "FLOOR PLAN",             "file": "FLOOR",   "type": "plan",      "status": "DONE" },
    { "sheet": "A-02", "title": "FRONT ELEVATION",        "file": "FRONT",   "type": "elevation", "status": "DONE" },
    { "sheet": "A-03", "title": "REAR ELEVATION",         "file": "REAR",    "type": "elevation", "status": "DONE" },
    { "sheet": "A-04", "title": "LEFT ELEVATION",         "file": "LEFT",    "type": "elevation", "status": "DONE" },
    { "sheet": "A-05", "title": "RIGHT ELEVATION",        "file": "RIGHT",   "type": "elevation", "status": "DONE" },
    { "sheet": "A-06", "title": "ROOF PLAN",              "file": "ROOF",    "type": "plan",      "status": "DONE" },
    { "sheet": "A-07", "title": "SECTION",                "file": "SECTION", "type": "section",   "status": "GAP" },
    { "sheet": "A-08", "title": "OPENING SCHEDULE",       "file": "SCHEDULE","type": "schedule",  "status": "GAP" },
    { "sheet": "E-01", "title": "ELECTRICAL PLAN",        "file": "ELECTRICAL","type": "plan",     "status": "GAP" },
    { "sheet": "E-02", "title": "REFLECTED CEILING PLAN", "file": "CEILING", "type": "plan",      "status": "GAP" }
  ],
  "archive_check": ["FLOOR", "ROOF"],
  "total_pages": 10
}
```

Code reads this JSON. Tests validate against it. Analysis references it.
One file governs the whole cycle: generate → test → compare → log.
When a new project is onboarded, create its `{PREFIX}_2D.json`.

### 3.1 Building DB (WHAT + WHERE)

From `elements_meta` + `elements_rtree` in the compiled output DB
(copied to `input/{PREFIX}_extracted.db`):
- Element positions (minX/maxX/minY/maxY/minZ/maxZ)
- Element types (ifc_class, element_name)
- Storey containment
- Spatial structure (rooms, storeys)
- Triangle meshes in `base_geometries` (for section cuts)

### 3.2 Template (HOW)

Two template files, user-editable:

**`drawing_template.json`** — layout and formatting values (shared):
- `paper` — size, margins, title block width, border weight, scale
- `grid` — circle radius, extend distance, label style, line style
- `dimensions` — tier offsets, tick angle/size, text height, snap module
- `room_labels` — font heights, area format, language
- `annotation_tags` — tag size, prefix letters, shape
- `north_arrow` — size, placement, label
- `title_block` — header text, field rows, column ratios
- `line_weights` — per element type, cut vs projection
- `colors` — per element type
- `level_markers` — symbol style, font height

**`input/2D.db`** (SQLite, 20 tables with `2d_*` prefix) — the library:

Style tables (existing):
- `2d_drawing_style` (19) — stroke/fill per IFC class + view type
- `2d_drawing_symbol` (19) — plumbing/electrical fixture symbols
- `2d_dxf_layer` (23) — AIA layer names, colors, linetypes
- `2d_grid_style` (2), `2d_dimension_style` (3), `2d_heading_style` (10)
- `2d_dxf_dimstyle` (4), `2d_dxf_textstyle` (8)

Content tables (existing):
- `2d_room_label` (18), `2d_annotation_tag` (7), `2d_finish_type` (10)
- `2d_title_block` (11), `2d_level_marker` (9), `2d_drawing_type` (8)
- `2d_sheet_template` (4), `2d_drawing_profile` (3)

Construction tables (2D_010):
- `2d_drawing_part` (33) — WHAT: every atomic part with stroke/fill/dash/font
- `2d_part_placement` (35) — WHERE: zone, anchor, offset per view type
- `2d_page_composition` (103) — HOW: which parts on which page, draw order

**Library rule:** Code reads these tables at runtime. No inline visual
literals. `test_no_hardcode.py` enforces this (§10.2).

### 3.3 Traceability

Every entity in the output must trace to one of:

| Entity | Source |
|--------|--------|
| Wall outline | DB: mesh section cut at storey elevation |
| Door/window opening | DB: element position in host wall |
| Furniture rectangle | DB: bounding box of below-cut element |
| Grid line position | DB: structural bay axis from columns + boundary walls (§4.2) |
| Grid line style | Template: `grid.line_style` |
| Grid end circle | Template: `grid.bubble_radius_mm` |
| Grid label text | Template: `grid.vertical_axis_labels` / `grid.horizontal_axis_labels` |
| Dimension value | DB: `grid[i+1].position - grid[i].position` (arithmetic) |
| Dimension formatting | Template: `dimensions.*` |
| Room label text | 2D.db: `[2d_room_label]` table |
| Room area | DB: computed from wall-enclosed space |
| Door/window tag | DB: sequential count of elements per type |
| Tag formatting | Template: `annotation_tags.*` |
| Level marker text | 2D.db: `[2d_level_marker]` table |
| Level elevation | DB: `detect_levels()` from element Z ranges |
| Height dimension | DB: difference between consecutive level elevations |
| Sheet border | Template: `paper.margins`, `paper.border_weight_mm` |
| Title block panel | Template: `paper.title_block_width_mm`, `title_block.fields` |
| North arrow | Template: `north_arrow.*` |
| Line weight | Template: `line_weights.*` |
| Background colour | White (paper standard) |

## 4. Grid Lines

### 4.1 What Grid Lines Are

A grid line is a **reference axis** that the contractor uses to set out the
building on site. Every wall, column, and opening is located by its distance
from the nearest grid. The grid is the coordinate system of the building.

Grid lines appear on **every view** — same labels, same positions. Grid A
on the floor plan is Grid A on the front elevation. This is how the
contractor reads across drawings.

Grid lines are drawn **dash-dot** (not solid) so the eye distinguishes
reference geometry from physical building outlines.

### 4.2 Floor Plan Grids (Vertical: A,B,C... Horizontal: 1,2,3...)

Grid lines mark **structural bay spacing** — the primary coordinate system
of the building. They are NOT per-wall lines. A typical house has 3-6 grids
per direction. Partition walls are *dimensioned from* the nearest grid, not
gridded themselves.

What qualifies for a grid line:
- **Columns** — always gridded (centroid position, highest priority)
- **Load-bearing / boundary walls** — exterior walls and primary structural
  walls that define the building envelope and major internal divisions
- **Partition walls** — NOT gridded. They are dimensioned from grids.

```
Step 1: Collect structural element positions
        → IfcColumn: centroid (center_x, center_y) — always qualifies
        → IfcWall: only boundary / load-bearing walls
          Detection: wall face aligns with building bounding box
          within wall-thickness tolerance (0.20m)
          - N-S wall → x = center_x (centreline)
          - E-W wall → y = center_y (centreline)
        → Exclude: partition walls (interior, not at building boundary),
          curtain wall / IfcPlate, furniture, slabs, roofs, annotations,
          openings

Step 2: Cluster within wall-thickness tolerance (0.20m)
        → Two wall faces 150mm apart = same grid at their midpoint
        → Column centroid within 0.20m of a wall face = same grid
           (column governs the position)

Step 3: Every cluster becomes a grid
        → A column = one grid line (highest priority position)
        → A boundary wall = one grid line
        → No minimum count filter within the structural set

Step 4: Label from template (sorted by position)
        → Vertical axes (X positions): A, B, C, D...
          from template grid.vertical_axis_labels
        → Horizontal axes (Y positions): 1, 2, 3...
          from template grid.horizontal_axis_labels
```

Door and window positions are NOT gridded on the floor plan — they clutter
the main drawing. Openings are dimensioned on a dedicated **Opening Schedule**
page (§5.5) per TB-LKTN practice.

### 4.2.1 Grid Detection Forensics (2026-04-07)

**Problem:** Archive floor plan has 7 grids (A,B,C,D + 1,2,3). DXF writer
produces only 4 (A,B + 1,2). Diagnostic log confirms: `grids=4(A,B,1,2)`.

**Root cause:** `derive_grids()` in `drawing_writer.py:248` filters with
`is_exterior` (`'Ext' in name`). SampleHouse has 5 IfcWall (3 Ext, 2 Partn)
+ 6 IfcPlate (Glazed). Only 3 Ext walls contribute centerlines, which merge
with bounding box edges → 2 X grids + 2 Y grids = 4 total.

**Archive grid positions (from archive SVG, verified):**

| Label | Axis | Model position | Source element |
|-------|------|----------------|----------------|
| A | x | -7.735m | Building bbox min_x (glass panel left edge) |
| B | x | -2.835m | Glass-masonry junction (IfcPlate ends, IfcWall starts) |
| C | x |  1.620m | Partition wall center_x (major internal division) |
| D | x |  6.270m | Exterior wall center_x (right boundary) |
| 1 | y | -1.320m | Building bbox min_y (bottom boundary, merged) |
| 2 | y |  0.900m | Partition wall center_y (major internal division) |
| 3 | y |  4.555m | Building bbox max_y (top boundary, merged) |

**Fix spec — `derive_grids()` must produce these 7 grids:**

```
Step 1: Collect ALL opaque wall centerlines (remove is_exterior filter)
        → Include IfcWall regardless of Ext/Partn naming
        → Exclude IfcPlate/glass panels (is_glass check stays)
        → NS walls → x_positions.append(center_x)
        → EW walls → y_positions.append(center_y)

Step 2: Add wall endpoints of exterior walls as structural bay boundaries
        → For each EW exterior wall: x_positions.append(min_x), x_positions.append(max_x)
        → For each NS exterior wall: y_positions.append(min_y), y_positions.append(max_y)
        → This captures glass-masonry junctions where Ext wall starts/ends

Step 3: Add building bounding box edges (keep existing logic)
        → x_positions.append(bld_min_x), x_positions.append(bld_max_x)
        → y_positions.append(bld_min_y), y_positions.append(bld_max_y)

Step 4: Merge within 0.20m tolerance (keep existing logic)

Step 5: Label sorted positions (keep existing logic)
        → X: A, B, C, D...
        → Y: 1, 2, 3...
```

**Expected result for SampleHouse:**
- X positions: -7.735, -2.835, 1.620, 6.270 → 4 grids (A,B,C,D)
- Y positions: -1.320, 0.900, 4.555 → 3 grids (1,2,3)
- Total: 7 grids — matches archive

Partition walls are dimensioned as offsets from the nearest grid on the
bay dimension tier (tier 3).

### 4.3 Elevation Grids

Elevations carry **two sets** of grid lines:

**Bay grids (vertical lines)** — same as floor plan grids for the face
being viewed. Front/rear elevation shows A, B, C, D. Left/right shows
1, 2, 3. These run top-to-bottom with bubbles at top.

**Level lines (horizontal)** — mark the vertical positions the contractor
needs. Detected from element Z ranges in the DB:

```
Level detection (from building elements):
  → FFL (Finished Floor Level): storey elevation (0.000m)
  → SILL: bottom of lowest window (window.minZ)
  → HEAD: top of tallest door/window (opening.maxZ)
  → CLG/UPPER FLOOR: ceiling or next storey elevation
  → EAVE: bottom of roof (roof.minZ) — where roof meets wall
  → RIDGE: top of roof (roof.maxZ) — highest point of building

Level labels from 2D.db [2d_level_marker] table.
Level markers: triangle symbol at left edge, dashed line across.
```

The contractor reads: RIDGE at +3.500, EAVE at +2.300, HEAD at +2.100,
SILL at +0.900, FFL at +0.000, GRD at -0.250. Every height between
these levels is a dimension.

### 4.4 Roof Plan Grids

Same bay grids as floor plan (A, B, C, D and 1, 2, 3). Plus:
- **Eave overhang dimension**: roof edge to wall face, measured at each
  grid bay. The contractor needs this to set the fascia board position.
- **Ridge position**: dashed line along the roof peak.

### 4.5 Rendering

Each grid axis becomes a line on the drawing:
- Line at the axis position, extending `grid.extend_beyond_building_mm`
  beyond the building outline in both directions
- **Style: dash-dot** from `grid.line_style` — pattern `[4, 1, 1, 1]`
  (4mm dash, 1mm gap, 1mm dot, 1mm gap). This is mandatory — solid
  lines are building outlines, dash-dot lines are reference grids.
- Circle (bubble) at each end, radius from `grid.bubble_radius_mm`
- Label inside each circle from template axis labels
- Fill bubble white so label is readable over crossing lines

Log each: "Grid {label} at {position}m → line from {start} to {end},
circle r={radius}mm"

## 5. Views

### 5.0 TB-LKTN Drawing Set (RUMAH RAKYAT)

A complete TB-LKTN architectural submission is a **set of sheets**. Each
sheet has one view, one title, one sheet number. The set is defined here.
Every sheet follows the same sheet furniture pattern proven in the archive
floor plan and roof plan (§9.1).

| Sheet | Sheet No. | Drawing Title | Template `drawing_types.id` | Type | Status |
|-------|-----------|---------------|-----------------------------|-----------|----|
| 1 | A-01 | FLOOR PLAN | FLOOR_PLAN | plan | DONE (archive) |
| 2 | A-02 | FRONT ELEVATION | FRONT_ELEV | elevation | DONE (DXF) |
| 3 | A-03 | REAR ELEVATION | REAR_ELEV | elevation | DONE (DXF) |
| 4 | A-04 | LEFT ELEVATION | LEFT_ELEV | elevation | DONE (DXF) |
| 5 | A-05 | RIGHT ELEVATION | RIGHT_ELEV | elevation | DONE (DXF) |
| 6 | A-06 | ROOF PLAN | ROOF_PLAN | plan | DONE (archive SVG), DXF GAP |
| 7 | A-07 | SECTION | SECTION | section | GAP |
| 8 | A-08 | OPENING SCHEDULE | OPENING_SCHED | schedule | GAP |
| 9 | E-01 | ELECTRICAL PLAN | ELECT_PLAN | plan | GAP |
| 10 | E-02 | REFLECTED CEILING PLAN | REFL_CEILING | plan | GAP |

**Sheet furniture — universal (every sheet):**
- Sheet border (§7.1) — `paper.margins.*`, `paper.border_weight_mm`
- Title block panel (§7.2) — right side, field rows from template
- Drawing title — from table above, centered below content
- Sheet number — from table above
- Scale text — from template `paper.scale`
- White background — paper standard
- Line weight hierarchy — per §0 (bold structure, thin annotation)

**Sheet furniture — conditional by type:**

| Furniture | plan | elevation | section | schedule |
|-----------|------|-----------|---------|----------|
| North arrow | YES | no | no | no |
| Scale bar | YES | YES | YES | no |
| Grid lines (dash-dot §4.5) | YES | YES (bay grids) | YES | no |
| Grid bubbles + labels | YES | YES | YES | no |
| Level markers | no | YES | YES | no |
| Table structure | no | no | no | YES |

**DXF filename convention:**
`{building}_{drawing_type_id}.dxf` — e.g. `ifc4_sample_house_floor_plan.dxf`.
The `drawing_type_id` suffix maps to the template `drawing_types[].id` in
lowercase with underscores. The conformity gate uses this to look up the
expected sheet number and drawing title.

The archive floor plan (A-01) and roof plan (A-06) are the proven
conformity reference. All other sheets must match their sheet furniture
pattern. See §10.5 for the conformity gate that enforces this.

Each view = one cut plane + one bundle of elements. View sections below
list **content only** — sheet furniture (border, title block, etc.) is
per §5.0 universal/conditional tables and is not repeated per view.

### 5.1 Floor Plan

| Property | Source |
|----------|--------|
| Cut height | DB: storey_elevation + 1.2m (standard cut plane height, drafting convention) |
| Cut elements (solid, heavy) | DB: IfcWall, IfcDoor, IfcWindow, IfcColumn crossing cut plane |
| Below elements (light) | DB: IfcFurnishingElement, IfcStair below cut plane |
| Omitted | IfcSlab, IfcRoof, IfcBeam above, MEP above ceiling |
| Door swing arcs | DB: hinge at wall end, swing direction from furniture clustering (room side detection) |
| Window symbols | DB: double parallel lines + glass center line in wall opening |
| Wall rendering | Cut walls: solid filled polygons. Curtain wall/glass: outline stroke only |
| Line weights | Template: `line_weights.*` per element type |

### 5.2 Elevation

| Property | Source |
|----------|--------|
| Face selection | DB: elements within 1.0m of building face (front=minY, rear=maxY, left=minX, right=maxX) |
| Level markers | DB: `detect_levels()` → FFL, SILL, HEAD, CLG, RIDGE |
| Level label text | 2D.db: `[2d_level_marker]` |
| Height dimensions | DB: differences between consecutive levels |
| Bay dimensions | DB: grid-to-grid distances (same as floor plan) |
| Roof silhouette | DB: convex hull of roof elements projected onto face |
| Window louvre lines | DB: horizontal lines inside window rectangle, evenly spaced by window height |

### 5.3 Roof Plan

| Property | Source |
|----------|--------|
| Bay grids | Same as floor plan (§4.4) — dash-dot, bubbles, labels |
| Roof outline | DB: roof slab bounding box |
| Ridge line | DB: maxZ line of roof slabs (dashed, style from template `line_styles.ridge_line`) |
| Slope arrows | DB: evenly spaced along each slope, direction from ridge toward eave, count = one per grid bay |
| Eave overhang | DB: roof edge - wall face (dimension value) |
| Labels | RIDGE, EAVE (from 2D.db `[2d_level_marker]` table) |

### 5.4 Section

Vertical cut through building. **GAP — not yet implemented.**

### 5.5 Opening Schedule

TB-LKTN dedicates a **separate sheet** to door and window details. This keeps
the floor plan clean — openings are tagged (D1, W1) on the plan but their
dimensions, materials, and quantities are read from the schedule page.

The title block on the floor plan sheet includes a summary schedule table
(TAG, SIZE, DESCRIPTION, QTY). The dedicated sheet expands this with:

| Column | Source |
|--------|--------|
| TAG | Sequential tag from floor plan (D1, D2, W1, W2...) |
| SIZE | Width × height in mm from DB element extents |
| DESCRIPTION | Door/window type from element_name or classification |
| MATERIAL | From DB attributes or default (TIMBER, ALUMINIUM) |
| HARDWARE | Hinges, locks — from template defaults |
| QTY | Count of identical openings (grouped by size) |
| REMARKS | Special notes |

This is why door/window positions are NOT gridded on the floor plan —
the schedule page carries their dimensions separately.

## 6. Dimensions

All dimensions are arithmetic from DB coordinates. No invented numbers.

### 6.1 Tiers

| Tier | What | Source | Offset |
|------|------|--------|--------|
| 1 (outermost) | Overall extent | `MAX(pos) - MIN(pos)` from grid | Template: `dimensions.tier_1_offset_mm` |
| 2 (middle) | Bay spacing | `grid[i+1].position - grid[i].position` | Template: `dimensions.tier_2_offset_mm` |
| 3 (innermost) | Opening widths | Door/window extents along wall | Template: `dimensions.tier_3_offset_mm` |

Tier 3 is **GAP** — not yet implemented.

Dimension chains sit beyond the grid end circles. Tier 2 (bay) is the first
row outside the circles, tier 1 (overall) is the next row beyond that. Offsets
measured from the building outline edge, per template `dimensions.tier_*_offset_mm`.

### 6.2 Height Dimensions (Elevation)

| Dimension | Source |
|-----------|--------|
| FFL to SILL | DB: `window.minZ - storey.elevation` |
| SILL to HEAD | DB: `window.maxZ - window.minZ` |
| HEAD to CLG | DB: `ceiling.Z - window.maxZ` |
| CLG to RIDGE | DB: `ridge.Z - ceiling.Z` |
| Overall | DB: `ridge.Z - FFL` |

### 6.3 Formatting

All from template `dimensions.*`:
- Tick marks: `tick_angle_deg`, `tick_half_length_mm`
- Text: `text_height_mm`, centered on dimension line
- Extension lines: `extension_gap_mm`, `extension_overshoot_mm`
- Rounding: `snap_module_mm`
- Units: mm, no suffix

## 7. Drawing Composition

### 7.1 Sheet Layout

All values from `drawing_template.json` → `paper.*`:

```
┌─────────────────────────────────────────────────────┐
│  margin (paper.margins.*)                            │
│  ┌───────────────────────────────┬──────────────┐   │
│  │                               │ TITLE BLOCK  │   │
│  │     CONTENT AREA              │ (paper.      │   │
│  │     (building centered)       │  title_block │   │
│  │                               │  _width_mm)  │   │
│  │  grid lines + dimensions      │              │   │
│  │  room labels + tags           │ field rows   │   │
│  │                               │ from template│   │
│  │  ↑N  DRAWING TITLE            │              │   │
│  │      scale text               │              │   │
│  └───────────────────────────────┴──────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 7.2 Title Block Panel

Right side of sheet, width from `paper.title_block_width_mm`.

```
┌──────────────────┐
│  JABATAN KERJA   │  ← title_block.header (centered, near top)
│  RAYA MALAYSIA   │
│                  │
├─────────┬────────┤  ← field rows, bottom-up from title_block.fields[]
│ PROJEK  │ value  │    label column: title_block.label_column_ratio (30%)
├─────────┼────────┤    value column: remaining 70%
│ PEMILIK │ value  │
├─────────┼────────┤
│ ARKITEK │ value  │
├─────────┼────────┤
│ JENIS   │ RUMAH  │  ← default values from fields[].default
│ BANGUNAN│ RAKYAT │
├─────────┼────────┤
│ TAJUK   │ PELAN  │  ← overridden per drawing (floor plan, elevation, etc.)
│ LUKISAN │ LANTAI │
├─────────┼────────┤
│ NO.     │ A-01   │
│ LUKISAN │        │
├─────────┼────────┤
│ PINDAAN │        │
│ NO      │        │
└─────────┴────────┘
```

- Vertical separator line between content area and panel: `paper.border_weight_mm`
- Horizontal separators between rows: `line_weights.dimension_line`
- Vertical divider between label and value columns: `line_weights.dimension_line`
- Label text: `title_block.font_height_label_mm`
- Value text: `title_block.font_height_value_mm`
- Field list and order: `title_block.fields[]` (read from template)
- TAJUK LUKISAN value: set per drawing type from §5.0 table (FLOOR PLAN, FRONT ELEVATION, etc.)

### 7.3 Annotation Placement

Every placement value traces to a template field:

| Annotation | Template source |
|------------|-----------------|
| Grid line extent | `grid.extend_beyond_building_mm` |
| Grid end circle | `grid.bubble_radius_mm` |
| Grid label font | `grid.label_font_height_mm` |
| Grid label values | `grid.vertical_axis_labels`, `grid.horizontal_axis_labels` |
| Dim tier offsets | `dimensions.tier_1/2/3_offset_mm` |
| Dim tick style | `dimensions.tick_angle_deg`, `dimensions.tick_half_length_mm` |
| Dim text height | `dimensions.text_height_mm` |
| Extension lines | `dimensions.extension_gap_mm`, `dimensions.extension_overshoot_mm` |
| Room label font | `room_labels.name_font_height_mm` |
| Room area format | `room_labels.area_format` |
| Tag offset | `annotation_tags.size_mm` |
| North arrow | `north_arrow.size_mm`, `north_arrow.placement` |
| Title block | `title_block.fields[]`, `title_block.header` |
| Border weight | `paper.border_weight_mm` |

## 8. Colours

All colours from template `colors.*`. White background is paper standard.

| Element | Template field | Default |
|---------|---------------|---------|
| Walls | `colors.wall` | #000000 |
| Glass | `colors.glass` | #4488CC |
| Furniture | `colors.furniture` | #AAAAAA |
| Dimensions | `colors.dimension` | #000000 |
| Grid lines | `colors.grid` | #888888 |
| Labels | `colors.label` | #000000 |
| Scale text | `colors.scale_text` | #888888 |
| Background | White (paper) | #FFFFFF |

## 9. Status

### 9.1 Done (archive reference — conformity template, do not modify)

The template and drawing style follow TB-LKTN (JKR Malaysian standard for
RUMAH RAKYAT housing). SampleHouse SVG outputs in `archive/` match this
standard. They are the proven reference:
- Floor plan: walls, grids, dimensions, room labels, tags, title block, north arrow
- Roof plan: ridge, slope arrows, eave labels, overhang dimension
- 4 elevations: level markers, height dimensions, bay dimensions, roof silhouette

**Conformity template.** The archive floor plan (A-01) and roof plan (A-06)
are proven to TB-LKTN standard. They define the conformity pattern:
- Sheet border with correct margins
- Title block panel with all field rows populated
- Grid lines rendered dash-dot with bubbles at both ends
- Dimension chains with tick terminators, tiered offsets
- Drawing title centered below content
- North arrow (plans only)
- Scale bar and scale text
- White background

All new views and DXF ports MUST follow this pattern. The conformity gate
(§10.5) stops the process when any sheet deviates. This is not advisory —
it is a hard gate.

### 9.2 Done (2D_008)

| Task | Spec section | Status |
|------|-------------|--------|
| DXF writer: replace hardcoded values with template reads | §1 R4 | DONE — 11 violations fixed, `test_no_hardcode.py` ALL PASS |
| DXF writer: forensic logging per §2 Process | §1 R6 | DONE — diagnostic log in `output/dxf_diagnostic.txt` |
| DXF writer: sheet border + title block panel | §7.1, §7.2 | DONE — `_draw_sheet_layout()`, 22/22 floor plan checks pass |
| DXF writer: north arrow | §7.3 | DONE — triangle + "N" label from template |
| DXF writer: white background | §8 | DONE — layer 0 rectangle, color 7 |
| DXF writer: `--svg` flag for dual output | §1 R5 | DONE — SVG produced alongside DXF |

### 9.3 Remaining — Floor Plan Parity (SVG writer has, DXF writer missing)

These features exist in the SVG reference output. The DXF writer must match.
Tests are written first (will FAIL), then code implements until they PASS.

| Feature | Spec section | SVG writer reference | Test assertion |
|---------|-------------|---------------------|----------------|
| Door swing arcs | §5.1 | `drawing_writer.py:1234-1286` — leaf line + quarter-arc, direction from `_get_room_side_ew/ns` | ARC entities on A-DOOR layer, count >= door count |
| Window double-line symbol | §5.1 | `drawing_writer.py:1222-1232` — two parallel lines + glass center in wall opening | LINE entities on A-GLAZ layer in floor plan, >= 3 per window |
| Wall solid fill | §5.1 | `drawing_writer.py:1184` — `fill=COL_WALL` on wall polygons | HATCH or filled LWPOLYLINE on A-WALL-PATT layer |

### 9.4 Remaining — Other Views and Features

| Task | Spec section |
|------|-------------|
| DXF writer: roof plan | §5.3 (SVG writer has `draw_roof_plan`, DXF not yet ported) |
| Roof plan: bay dimensions + all 4 overhang dimensions | §5.3, §6.1 |
| Elevation layout: building centering | §7.1.1 (building fills 2-4%, see forensics) |
| Dimension tier 3: opening widths | §6.1 |
| Section view | §5.4 |
| TB-LKTN additional pages: electrical plan, reflected ceiling plan | 2D.db `[2d_drawing_type]` defines 8 types, only 3 implemented |

### 9.5 What Is Still Wrong (2026-04-08)

**a. Proof renderer dash patterns — FIXED (2D_010c, partial).**
~~Grid dash-dot `1/scale` bug~~ — dasharray function rewritten to use
paper-mm values directly from `2d_drawing_part.dasharray`. Per-entity
linetype check added (not just layer). **Remaining:** proof renderer
still needs full library wiring (read `2d_drawing_part` for all styles).

**b. Floor plan deviates from BestFloorPlanReference.png.**
Archive floor plan (Grade A) has crisp filled walls, clean grid bubbles,
proper dash-dot, professional title block. Current proof SVG renders
section-cut polygons (correct geometry but messy vertices) instead of
the archive's clean rectangles. The proof renderer needs to match the
archive's visual quality — the reference image is in `archive/BestFloorPlanReference.png`.

**c. Elevations still broken.**
- Level marker arrowheads render as degenerate triangles (duplicate vertex)
- Measurements don't align — dimension positions not verified against any reference
- REAR shows same problems as FRONT; LEFT/RIGHT have no openings
- Archive elevations are Grade D/F (not usable as reference) — must rebuild
  from TB-LKTN standard using `2d_drawing_part` + `2d_part_placement` tables

**d. Archive comparison gap.**
No automated positional comparison between proof SVG and archive.
Conformity gate checks DXF structure (layers, weights) but not visual
correspondence. Gate can PASS while drawing looks nothing like reference.

**e. Previously fixed (2D_010a-c).**
- ~~Roof plan not in --all~~ — FIXED, SH_2D.json drives page generation
- ~~Verbose filenames~~ — FIXED, single-term names
- ~~PNG proofs~~ — FIXED, SVG only
- ~~No output rotation~~ — FIXED, 2-gen pruning with timestamps
- ~~No visible change log~~ — FIXED, YES/NO in diagnostic

### 9.6 Output Management Spec

**R1 — SVG only.** No PNG.

**R2 — Single-term filenames.** Same name in both folders. No suffixes,
no decoration.

**R3 — Output directory is `2D_Layout/output/`.** Two folders: `DXF/`
and `SVG/`. Each folder keeps exactly 2 generations per view. On each
run, oldest is removed if more than 2 exist. Timestamp in filename.

```
output/
├── DXF/
│   ├── FLOOR_20260407_1730.dxf    ← current
│   ├── FLOOR_20260407_1528.dxf    ← previous (max 2 per view)
│   ├── ROOF_20260407_1730.dxf
│   └── ...
├── SVG/
│   ├── FLOOR_20260407_1730.svg
│   ├── FLOOR_20260407_1528.svg
│   ├── ROOF_20260407_1730.svg
│   └── ...
└── dxf_diagnostic.txt
```

**Master page table is `input/{PREFIX}_2D.json`.** This is the single
source of truth. Code reads it to know what to generate. Tests read it
to know what to validate. Analysis reads it to know what to compare.
See §3.0 for the JSON schema. The table below is the SampleHouse instance:

| # | Sheet | Title | Filename | Type | Status |
|---|-------|-------|----------|------|--------|
| 1 | A-01 | FLOOR PLAN | FLOOR | plan | DONE |
| 2 | A-02 | FRONT ELEVATION | FRONT | elevation | DONE |
| 3 | A-03 | REAR ELEVATION | REAR | elevation | DONE |
| 4 | A-04 | LEFT ELEVATION | LEFT | elevation | DONE |
| 5 | A-05 | RIGHT ELEVATION | RIGHT | elevation | DONE |
| 6 | A-06 | ROOF PLAN | ROOF | plan | DONE |
| 7 | A-07 | SECTION | SECTION | section | GAP |
| 8 | A-08 | OPENING SCHEDULE | SCHEDULE | schedule | GAP |
| 9 | E-01 | ELECTRICAL PLAN | ELECTRICAL | plan | GAP |
| 10 | E-02 | REFLECTED CEILING PLAN | CEILING | plan | GAP |

**Total pages: 10.** `--all` generates all non-GAP pages (currently 6).
When a GAP page is implemented, update its status in the JSON and it
joins `--all` automatically.

**R4 — Always generate floor + roof.** Every run MUST produce FLOOR and
ROOF. They are the archive regression reference — never drop them.

**R5 — Archive regression check (FLOOR + ROOF only).** After generating
SVGs, compare against archive:

```
For each of {FLOOR, ROOF}:
  archive = archive/{building}_{view}.svg
  current = SVG/{VIEW}_{timestamp}.svg

  Compare:
    1. Grid label set
    2. Dimension values
    3. Entity counts: lines, circles, texts, polygons
    4. Room labels (floor only)

  Log:
    ARCHIVE CHECK [{VIEW}]: {score}/{total} — Better: YES/NO
```

**R6 — Visible change detection.** Compare current SVG against previous
generation in same folder:

```
Summary log line:
  VISIBLE CHANGE: YES (FLOOR: +3 grids, +6 dims) / NO (identical to prev)
```

## 10. Tests

### 10.0 Test Protocol — DXF Proof

**R1 — DXF is the deliverable.** The DXF file is what the contractor opens
in CAD software. It is the single source of truth.

**R2 — SVG is the proof.** After generating the DXF, the `--proof` flag
reads the DXF back and transforms entities to a paper-scale SVG. This
proof SVG is inspectable for conformity checks (entity positions, layers,
`stroke-dasharray` for dash-dot, `<rect>` for white background). No PNG
output — SVG is sufficient.

**R3 — The archive SVG is the reference, not the proof.**
`drawing_writer.py` produced the archive SVGs (frozen, do not regenerate).
They are the reference for what the DXF output must match visually —
panel position, grid positions, label placement, dimension layout.

**R4 — Structural verification before pass.** Automated tests check entity
counts and types. But they cannot judge visual layout quality. The coder
MUST do three-way verification before declaring DONE:

1. **Read the log** — `conformity_log.txt` and `dxf_diagnostic.txt`
2. **Read the output** — proof SVG or DXF file (both are text, use Read tool)
3. **Read the spec** — §5.0 (sheet furniture), §4.2 (grids), §7.1 (layout)

Cross-reference all three: does the log say PASS? Does the output file
contain entities at the positions the spec requires? Do those positions
match the archive SVG? If any of the three disagree, the code is wrong.

The coder does this themselves — do not ask the user to look. If SVG-blind,
read the DXF directly and compare entity coordinates against archive.

**R5 — White background, black strokes.** The proof SVG must have an
explicit `<rect fill="#FFFFFF"/>` for white background. All building
outlines in black. Grid lines in grey. Glass in blue.

**R6 — Proof must show all content.** The proof renderer reads DXF
entity coordinates, divides by the drawing scale (1:100 → ÷100), and
renders at paper-scale mm — the same coordinate system as the archive
SVGs. This ensures walls, text, dimensions, and grids are all visible
and proportional.

```
python3 drawing_writer_dxf.py <db> --all --proof

# Outputs per view in lib/output/latest/:
#   *_floor_plan.dxf            — professional deliverable (CAD software)
#   *_floor_plan_proof.svg      — SVG proof for visual review
#   *_front_elevation.dxf       — elevation deliverable
#   *_front_elevation_proof.svg — elevation proof
#   ... (all views generated together)

# The --all flag generates floor plan + 4 elevations + proofs
# in a single run. No separate commands needed.
```

Output in `lib/output/latest/`:
- `*.dxf` — professional deliverables (one per view)
- `*_proof.svg` — SVG proofs for visual review (one per view)
- `archive/*.svg` — frozen reference (do not regenerate, do not touch)

### 10.1 `test_no_invention.py`

Checks that every DXF entity traces to DB or template. No unknown origins.

| Check | What it verifies | Expected | Fail means |
|-------|-----------------|----------|------------|
| LAYERS | Every layer in DXF is a known AIA layer | no unknown layers | code invented a layer |
| TEXT_TRACE | Every TEXT entity traces to template field, DB value, or dimension arithmetic | all traced | code invented a label |
| GUID_XDATA | Wall polylines have BIMGUID xdata | >= 5 polylines with xdata | wall not traced to DB element |
| LINE_WEIGHT | Layer lineweight matches template `line_weights.*` | exact match (1/100 mm) | hardcoded weight or wrong template read |

### 10.2 `test_no_hardcode.py` — Library Compliance Gate

**Rule:** Every visual property in drawing code must trace to `2D.db` or
`drawing_template.json`. No inline color, line weight, dasharray, font size,
or label string. This is the 2D equivalent of DAGCompiler's "no parametric"
rule — the library IS the spec.

Scans all drawing functions (`write_floor_plan_dxf`, `write_elevation_dxf`,
`write_roof_plan_dxf`, `_render_proof`, `_draw_sheet_layout`) for violations.

| Gate | What it catches | Source of truth | Fail means |
|------|----------------|-----------------|------------|
| COLORS | Hex `#RRGGBB` not in DB/template | `2d_drawing_part.stroke_color`, `2d_drawing_style.stroke_color`, template `colors.*` | Hardcoded color — add to `2d_drawing_part` or template |
| DASHARRAY | Dash pattern like `"4,1,1,1"` not in DB | `2d_drawing_part.dasharray` | Hardcoded pattern — add to `2d_drawing_part` |
| LABELS | Bilingual or display strings not from DB | `2d_level_marker.display_text`, `2d_room_label` | Hardcoded label — add to `2D.db` table |
| NUMBERS | Integer >= 100 not in template/DB | template numeric values, `2d_drawing_part` numeric columns | Hardcoded constant — read from template `.get()` |

**Exempt patterns** (not violations):
- Template `.get()` fallback values — these ARE library reads
- `_lw()`, `_txt_h()`, `_hex_to_aci()` calls — library accessor functions
- `_ACI_HEX` lookup table — DXF standard mapping
- Comments, docstrings, log lines
- Unit constants: `SCALE=100`, `MM=1000`

**How to fix a violation:** Add the value to `2D.db` (preferred) or
`drawing_template.json`, then read it at runtime via `SELECT` or `.get()`.

### 10.3 `test_dxf_vs_svg.py`

Checks DXF output has all features present in the archive SVG reference.

**Floor plan checks:**

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| WALL_SECTIONS | LWPOLYLINE on A-WALL-FULL/A-WALL-PRTN | >= 5 |
| GRID_LINES | LINE on A-GRID | >= 7 (4 vertical + 3 horizontal for SampleHouse) |
| GRID_BUBBLES | CIRCLE on A-GRID | >= 14 (7 grids x 2 ends) |
| GRID_LABELS | TEXT A,B,C,D,1,2,3 present | all 7 present |
| GRID_DASHDOT | A-GRID layer linetype is DASHDOT in DXF layer table | linetype name, not solid |
| GRID_COUNT | Grid count = structural bays (boundary walls + columns) | not total wall count |
| GRID_SORTED | Labels sorted by position (A < B < C, 1 < 2 < 3) | ascending order |
| DIM_TEXTS | TEXT on A-ANNO-DIMS with digit values | >= 3 |
| DIM_CHAIN | Bay dims + overall present, overall = sum of bays | arithmetic check |
| ROOM_LABELS | RUANG TAMU, RUANG MAKAN, BILIK present | all found |
| ROOM_AREAS | Text containing "m²" | >= 1 |
| DOOR_TAGS | D1, D2, D3 text | >= 3 |
| WINDOW_TAGS | W1+ text | >= 4 |
| DOOR_ARCS | ARC on A-DOOR | >= 3 (§5.1) |
| WINDOW_SYMBOLS | LINE on A-GLAZ | >= 12 (4 windows x 3 lines, §5.1) |
| WALL_FILL | LWPOLYLINE/HATCH on A-WALL-PATT | >= 5 (§5.1) |
| HEX_TAGS | 6-point LWPOLYLINE on A-ANNO-TEXT | >= 7 (§7.3) |
| SCHEDULE_TABLE | JADUAL header + "No." data in title block | present |
| SCALE_BAR | SOLID on A-ANNO-DIMS + "5m" text | present |
| DRAWING_TITLE | FLOOR PLAN text | present |
| SCALE_TEXT | "1:100" text | present |
| BORDER | LWPOLYLINE/LINE on A-TTLB | >= 1 |
| TITLE_BLOCK | LINE + TEXT on A-TTLB | >= 5 lines, >= 5 texts |
| NORTH_ARROW | Text "N" | present |
| WHITE_BG | LWPOLYLINE/SOLID on layer 0, ACI 7 | >= 1 |
| DIM_TICKS | LINE on A-ANNO-DIMS | >= 10 |
| GUID_COUNT | Polylines with BIMGUID xdata | >= 5 |

**Elevation checks:**

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| LEVEL_GRD | "GRD" in text | present |
| LEVEL_ROOF | "ROOF" or "RIDGE" in text | present |
| LEVEL_UPPER | "FLOOR" or "CEILING" or "CLG" or "1ST" in text | present |
| HEIGHT_DIMS | TEXT on A-ANNO-DIMS with digit values | >= 3 |
| GRID_LABELS | A,B,C,D present | >= 2 |
| BAY_DIMS | DIMENSION entities or digit texts | >= 2 |

**Proof SVG checks:**

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| PROOF_EXISTS | Proof SVG file exists | `*_proof.svg` present |
| PROOF_WHITE_BG | `<rect fill="#FFFFFF"/>` in proof SVG | explicit rect, not CSS background |
| PROOF_DASHDOT | Grid lines in proof SVG have `stroke-dasharray` | `[4,1,1,1]` pattern from template |
| PROOF_STRUCTURE | Coder reads proof SVG and compares positions against archive SVG | layout matches archive (§10.0 R4) |

### 10.4 TB-LKTN Completeness Audit (soft — informational)

The DXF writer runs a post-render audit against `2D.db` (the TB-LKTN reference
database). For each view it logs `[HAVE]` or `[MISS]` for every professional
drawing feature, with actual counts vs expected counts from the database.

This is **informational only** — it does not stop the process. Use it to
see what features are present vs missing. The hard gate is §10.5.

Output: `lib/output/latest/dxf_diagnostic.txt` — the forensic trail.

Features checked (floor plan): wall sections, grid lines, grid bubbles, grid
labels, GUID xdata, furniture, bay dimensions, room labels, room areas, door
swing arcs, wall fill/hatch, door/window tags, north arrow, drawing title,
title block fields, scale bar.

Features checked (elevation): element outlines, grid lines, grid bubbles, grid
labels, level markers, level labels, bay dimensions, ground line, window
louvres, roof silhouette, drawing title, title block.

### 10.5 Conformity Gate — `test_conformity.py`

**Purpose:** Every output sheet must match the sheet furniture pattern
proven in the archive floor plan and roof plan (§9.1). This test is a
hard gate — process stops on first infringement.

**How it works (white-box):**

The conformity gate reads each generated DXF file and checks it against
the mandatory sheet furniture list from §5.0. Every check logs a line to
the diagnostic file. On the first FAIL, the process **stops immediately**
with exit code 1. The coder reads the log, fixes the infringement, reruns.
No guessing — diagnosis is from the log alone.

```
Diagnostic output: lib/output/latest/conformity_log.txt

Log format per check:
  [PASS] {sheet} {check_name}: {detail}
  [FAIL] {sheet} {check_name}: expected {expected}, got {actual}
  [STOP] Process halted at first FAIL. Fix and rerun.

Last line of log — the coder reads ONLY this:
  ═══════════════════════════════════════
  RESULT: PASS — 5 sheets conform to §5.0
  ═══════════════════════════════════════

  or on failure:
  ═══════════════════════════════════════
  RESULT: FAIL — [FAIL] A-02 SCALE_TEXT: expected "1:", got not found
  ═══════════════════════════════════════

PASS or FAIL with main reason. No further reading needed unless FAIL.
On FAIL, read the detail lines above the summary to diagnose.

The coder's debugging process:
  1. Run: python3 test_conformity.py
  2. Read: lib/output/latest/conformity_log.txt
  3. Find the [FAIL] line — it tells you exactly what is wrong
  4. Fix the code (the DXF writer, not the test)
  5. Rerun from step 1
  6. Repeat until all [PASS], no [FAIL]
  7. Analysis is from the log ONLY — do not invent explanations
```

**Checks per sheet (every DXF output file):**

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| BORDER | LWPOLYLINE on A-TTLB layer exists | >= 1 |
| TITLE_BLOCK | LINE entities on A-TTLB (separator + field rows) | >= 5 |
| TITLE_TEXTS | TEXT on A-TTLB (field labels + values) | >= 5 |
| TAJUK | TAJUK LUKISAN text matches §5.0 table for this sheet | exact match |
| NO_LUKISAN | NO. LUKISAN text matches §5.0 table for this sheet | exact match |
| DRAWING_TITLE | Drawing title text below content (from §5.0 table) | present |
| SCALE_TEXT | Scale text (e.g. "1:100") present | present |
| GRID_LINES | LINE entities on A-GRID layer | >= 1 per axis (not schedule) |
| GRID_DASHDOT | A-GRID layer linetype is DASHDOT | linetype name (not schedule) |
| GRID_BUBBLES | CIRCLE entities on A-GRID layer | >= 2 per grid (not schedule) |
| GRID_LABELS | TEXT on A-GRID with template axis labels | sorted, matching (not schedule) |
| WHITE_BG | LWPOLYLINE or SOLID on layer 0, ACI color 7 | >= 1 |
| LINE_WEIGHTS | Layer lineweights match template `line_weights.*` | A-WALL-FULL=50, A-WALL-PRTN=35, A-GRID=18, A-FURN=15 |
| NORTH_ARROW | TEXT "N" present (plans only: A-01, A-06, E-01, E-02) | conditional |
| SCALE_BAR | SOLID entities on A-ANNO-DIMS + "5m" text | conditional (not schedule) |

**Proof SVG checks (for `--proof` output):**

| Check | What it verifies | Expected |
|-------|-----------------|----------|
| PROOF_EXISTS | Proof SVG file exists for this view | `*_proof.svg` present |
| PROOF_WHITE_BG | `<rect fill="#FFFFFF"/>` in proof SVG | explicit rect |
| PROOF_DASHDOT | Grid lines in proof SVG have `stroke-dasharray` | pattern present |

### 10.5.1 Conformity Gate Forensics (2026-04-07)

**Problem:** Gate reports "PASS — 5 sheets conform" but archive only proves
floor plan (A-01) and roof plan (A-06). The gate checks minimums (≥1 grid
line, ≥2 labels) rather than correct counts. It cannot catch:
- Wrong grid count (4 vs expected 7)
- Wrong grid labels (A,B,N vs expected A,B,C,D)
- "N" north arrow text leaking into grid label set
- Building too small on elevation sheets (2-4% fill)

**Fix spec — add these checks to `test_conformity.py`:**

| Check | What it validates | Expected |
|-------|-------------------|----------|
| GRID_COUNT | Total grid lines = archive count | 7 for SampleHouse floor plan |
| GRID_LABEL_SET | Labels exactly match archive set | {A,B,C,D,1,2,3} for floor plan |
| GRID_NO_LEAK | "N" text on A-GRID layer excluded from labels | "N" is north arrow, not grid |

The archive grid reference per building is the source of truth. Grid labels
are read from the archive SVG (text elements inside `<circle>` groups with
`stroke-dasharray`) and stored as the expected set.

For SampleHouse:
- Floor plan: vertical={A,B,C,D}, horizontal={1,2,3}, total=7
- Front/rear elevation: bay grids={A,B,C,D}, total=4
- Left/right elevation: bay grids={1,2,3}, total=3
- Roof plan: same as floor plan, total=7

### 7.1.1 Elevation Layout Forensics (2026-04-07)

**Problem:** Diagnostic reports "Building occupies 2-4% of drawing area"
for elevations, "45% empty space below ground." Building is tiny on sheet.

**Analysis:** At 1:100, a 14.1m × 3.5m elevation = 141mm × 35mm on A3
(420×297mm). This IS the correct proportion — the building is short and
wide. The 2-4% fill is the true area ratio. The visual issue is
**vertical centering**: ground line should sit low on the sheet, with
building and annotation above. Currently too much dead space below.

**Fix spec (deferred — informational, not blocking):**
- Vertical centering should anchor ground level at ~30% from bottom
- Level markers + height dims extend upward into remaining space
- This is a positioning improvement, not a conformity failure

**Stop-on-fail behaviour:**

The test iterates checks in order. On the first FAIL it:
1. Writes `[FAIL]` line to log with expected vs actual
2. Writes `[STOP] Process halted at first FAIL. Fix and rerun.`
3. Prints the FAIL line to stderr
4. Exits with code 1

This forces the coder to fix issues one at a time, in order, from the
log output. No batch failures to sort through. No guessing. Read the log,
fix the code, rerun.

**When to run:**

After every DXF generation (`drawing_writer_dxf.py --all --proof`). The
conformity gate is the final check before declaring a view DONE.

```
python3 drawing_writer_dxf.py <db> --all --proof
python3 test_conformity.py
# If exit 0: all sheets conform
# If exit 1: read lib/output/latest/conformity_log.txt, fix, rerun
```

### 10.6 Debugging Process

**White-box testing: the log is the complete picture.**

The conformity gate and DXF writer logs are designed so the coder never
needs to open a DXF file, grep source code, or interrogate the system.
Every log line echoes the **exact string written** into the DXF — layer
name, entity type, text content, linetype, lineweight, position. The
coder reads the log and sees what was written vs what was expected, all
in one place.

**What the logs must contain (white-box requirement):**

The DXF writer (`dxf_diagnostic.txt`) logs every entity it writes:
```
  WRITE layer=A-GRID type=LINE start=(x,y) end=(x,y) linetype=DASHDOT weight=18
  WRITE layer=A-GRID type=CIRCLE center=(x,y) r=400 fill=#FFFFFF
  WRITE layer=A-GRID type=TEXT insert=(x,y) text="A" height=350
  WRITE layer=A-WALL-FULL type=LWPOLYLINE points=[(x,y)...] weight=50 guid=abc123
  WRITE layer=A-TTLB type=TEXT insert=(x,y) text="FLOOR PLAN"
```

The conformity gate (`conformity_log.txt`) reads the DXF back and logs
what it finds vs what it expects:
```
  [PASS] A-01 GRID_DASHDOT: layer A-GRID linetype=DASHDOT (expected DASHDOT)
  [FAIL] A-01 LINE_WEIGHTS: layer A-GRID weight=0 (expected 18)
  [STOP] Process halted at first FAIL. Fix and rerun.
```

Between these two logs, the coder can trace: what was written (diagnostic)
→ what was read back (conformity) → where they diverge (the bug).

**Debugging protocol:**
```
  1. Run the pipeline: drawing_writer_dxf.py <db> --all --proof
  2. Run the gate:     test_conformity.py
  3. Read the log:     lib/output/latest/conformity_log.txt
  4. Find [FAIL]:      the log tells you WHAT failed and WHERE
  5. Read DXF diag:    lib/output/latest/dxf_diagnostic.txt
     — find the WRITE line for the entity that caused the fail
     — the exact string written is in the log, no need to open DXF
  5.1 Verify layout: read lib/output/latest/*_proof.svg (Read tool)
     — compare entity positions and structure against archive SVG
       at /home/red1/bim-compiler/2D_Layout/archive/
     — if SVG-blind, read the DXF file directly (text format)
     — check: grid positions, panel position, label placement,
       dimension layout match the archive
     — if discrepancy found: output findings and recommendations,
       then STOP and await instructions to triage together
     — the coder does this themselves — do not ask the user to look
     — do NOT silently continue past a layout discrepancy
  5.2 If the log lacks detail to diagnose a failure:
     — the log itself is deficient — fix the logging FIRST
     — add FINE-level log lines in drawing_writer_dxf.py that
       echo the exact values written (layer, type, text, weight,
       linetype, position)
     — rerun pipeline from step 1 to regenerate the log
     — THEN diagnose from the improved log
     — each round the log must get better, never worse
     — the goal: any future failure is diagnosable from the log
       alone, without opening files or guessing
  6. Fix the code:     edit drawing_writer_dxf.py
  7. Rerun from step 1
  8. DO NOT:
     - Open the DXF file to inspect it manually
     - Guess what went wrong without reading the log
     - Invent explanations not supported by log output
     - Skip the gate and declare DONE
     - Fix multiple issues at once (one FAIL, one fix, one rerun)
```

The log is the forensic trail. If the log says PASS, it passes. If the
log says FAIL, fix exactly what the log says is wrong. Analysis that is
not grounded in log output is invention and violates §1 R1.

## 11. Key Files

| File | Purpose |
|------|---------|
| `drawing_template.json` | User-editable layout template (all formatting values) |
| `lib/input/2D.db` | Template DB (17 tables, `2d_*` prefix, label content) |
| `python/drawing_writer.py` | SVG writer (reference implementation) |
| `python/drawing_writer_dxf.py` | DXF writer (port from SVG) |
| `python/section_cut.py` | Mesh slicer for floor plan section cuts |
| `python/test_no_invention.py` | Anti-invention test |
| `python/test_no_hardcode.py` | No hardcoded values test |
| `python/test_dxf_vs_svg.py` | DXF vs archive reference test |
| `python/test_conformity.py` | Conformity gate — stops on first infringement (§10.5) |
| `output/conformity_log.txt` | Conformity gate diagnostic log |
| `output/dxf_diagnostic.txt` | DXF writer forensic log |
| `archive/` | Reference SVG outputs (proven conformity template, do not modify) |
| `input/SH_2D.json` | SampleHouse output plan (master page table, 10 pages) |
| `input/SH_extracted.db` | SampleHouse compiled DB (copied from DAGCompiler) |
| `internal/SpecToCode.md` | **Spec-to-code checklist** — 133 items, Y/N compliance per spec line |
