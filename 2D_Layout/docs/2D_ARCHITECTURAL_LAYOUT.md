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

**R5 — No hardcoded symbols.** The code must not contain hardcoded geometry
for any visual element — no inline triangle vertices, no magic radius values,
no ad-hoc polygon points. Every symbol (grid bubble, north arrow, dimension
tick, slope arrow, MEP icon, tag shape) must be defined in
`drawing_template.json` or `2D.db` and read at render time. If the template
lacks a key, add the key to the template first — do not hardcode a fallback
in code. The log must report `§VALUE` for every symbol parameter read from
template, so the coder can verify from logs alone what was drawn.

**R6 — Reference is the archive.** The SVG outputs in `archive/` are the proven
reference. Any new output (DXF or SVG) must match the archive in content and
layout. Tests enforce this.

**R7 — Code logs forensically.** Every derivation is logged. If a line appears
in the output, the log shows where it came from. No external checking needed.

**R8 — Reuse shared abstract code.** Thickness rendering (wall section-cut,
roof cross-section), hull projection, hatch fill, and other geometric
primitives must be shared helper functions. Coder must not write custom
geometry for each new request. If a new function duplicates >5 lines of an
existing function, refactor into a shared helper. This prevents drift between
views that render the same concept (e.g. wall thickness on floor plan vs
roof thickness on roof plan).

**R8 enforcement:** Before writing ANY new geometry function, coder must:
1. `grep` for existing functions that do the same thing
2. If found, call the existing function
3. If not found, write with a generic name (not feature-specific)
4. Log the search: `§R8 checked: grep '_hull' → N hits, reusing _mesh_hulls`

**R9 — Extract, don't invent.** When mesh data exists in `base_geometries`,
use `section_cut.py` or direct vertex extraction. Do not compute synthetic
geometry (offset polygons, interpolated curves) when the real geometry is
available. Computed geometry is only acceptable when no mesh exists.
Validation: every drawn boundary point must trace to an actual vertex in
`base_geometries.vertices` or to a `section_cut` intersection.

**R10 — Abstract gates, not per-feature tests.** Conformity checks must be
abstract principles (NO_INVENTION, NO_MEP_BLEED) not concrete feature names
(ROOF_NO_ENDCAP, ROOF_THICKNESS). When a new drawing feature needs
validation, extend the existing abstract check — don't add a new one.

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
| Roof outline (outer) | DB: XY convex hull of `base_geometries.vertices` for IfcRoof (see §5.3a). Thickness from mesh edge Z-span → `_inward_offset_hull` (§1 R8 shared). **Extract, validate, draw — never invent.** |
| Roof outline (inner) | DB: outer hull offset inward by measured slab thickness. Same `_inward_offset_hull` used by wall section-cut. Log thickness: `§DATA thickness=N.NNNm` |
| Ridge line | DB: maxZ line of roof slabs (dashed, style from template `line_styles.ridge_line`). Omit for flat roofs (§5.3b). |
| Slope arrows | DB: evenly spaced along each slope, direction from ridge toward eave, count = one per grid bay. Omit for flat roofs. |
| Eave overhang | DB: roof edge - wall face (dimension value). Omit for flat roofs (no overhang). |
| Labels | RIDGE, EAVE (from 2D.db `[2d_level_marker]` table). Flat roof: use BUMBUNG RATA / FLAT ROOF label only. |

#### §5.3a Roof Detection Rule

Code MUST detect the roof element using this priority order:

1. **`IfcRoof`** elements — direct match, always preferred (SampleHouse is this case)
2. **`IfcSlab`** where `element_name LIKE '%Roof%'` AND `maxZ` is within 0.5m of the
   building's overall `maxZ` — flat roof slab (Duplex is this case)
3. **Parapet walls** in a "Roof" storey — if no slab is found, use the bounding box of
   all walls in the storey named "Roof" as the roof outline

If `roofs = []` after step 1 AND step 2 produces candidates, promote them to `roofs`.
Log which rule fired: `§5.3a roof_detection: rule={1|2|3} n={count}`.

**Never skip roof plan silently** — if detection fails, log as WARN and draw the
building footprint bounding box as a fallback outline with annotation
"ROOF OUTLINE (FALLBACK — CHECK IFC)".

#### §5.3b Flat Roof vs Pitched Roof

Detect roof type by comparing ridge_z vs eave_z from the roof element(s):

```
pitched = (roof.maxZ - roof.minZ) > 0.5m
```

| Feature | Pitched roof (SH) | Flat roof (DX) |
|---------|------------------|----------------|
| Ridge line (dashed) | YES — at maxZ position | NO |
| Slope arrows | YES — from ridge to eave | NO |
| Eave overhang dim | YES — roof edge - wall face | NO |
| RIDGE/EAVE labels | YES | NO |
| Parapet outline | NO | YES — parapet walls in "Roof" storey |
| Skylights | if present | YES — IfcWindow in Roof storey |
| Roof drain symbols | NO | YES — IfcFlowFitting in Roof storey |
| Flat roof label | NO | YES — "BUMBUNG RATA / FLAT ROOF" at centroid |

**TB-LKTN convention for flat roofs:** The roof plan shows the parapet rectangle
as a bold outline (A-ROOF, lw=0.50mm), skylights as rectangles with cross marks
(A-GLAZ, lw=0.25mm), and drain symbols as circles with an X (A-PLMB, lw=0.18mm).
The building name is shown centered above the drawing title.

#### §5.3c Building Name Fallback

`spatial_structure WHERE type='IfcBuilding'` → `name` column.
If `name` is empty (DX case), derive from DB filename stem:
- `DX_extracted.db` → "DUPLEX"
- `SH_extracted.db` → "IFC4 SAMPLE HOUSE"
- `RM_extracted.db` → "RUMAH RAKYAT"
Fallback name is uppercase with underscores replaced by spaces.

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

### 7.1a Fitted Paper Height (sleek format)

**Principle:** Paper width is fixed at 420mm (A3 landscape). Paper height is **fitted to content** — short and wide, not wasted space.

**Reference:** Archive FLOOR plan SVG is 420×194mm (ratio 2.16:1). BestFloorPlanReference.png is ratio 1.95:1. Target range: **1.9–2.2:1**.

**Formula:**
```
paper_height = margin_top
             + annotation_top       (= tier_1_offset_mm + grid_extend_mm + bubble_radius_mm×2)
             + building_height_mm   (building Y-extent at scale, or building H for elevations)
             + annotation_bottom    (= grid_extend_mm + bubble_radius_mm×2)
             + margin_bottom
```

Clamp to `[fitted_min_height_mm, fitted_max_height_mm]` from template.

**Template keys** (in `paper`):
- `fitted: true` — enable fitted mode (default: true)
- `fitted_min_height_mm: 150` — never shorter than this
- `fitted_max_height_mm: 250` — never taller (prevents reverting to full A3)
- `height_mm: 297` — fallback when `fitted: false`

**Outcome:**
- Floor plan (SH, 8.7m deep): ~180–195mm ✓ matches archive
- Elevations (SH, 3.5m tall): ~120mm → clamped to 150mm minimum
- All sheets: sleek landscape, content fills the sheet

**Test:** After generation, log `§7.1a paper_height={h}mm fitted={fitted}`. Verify height ≤ 250mm and ratio width/height ≥ 1.9.

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

| Task | Spec section | Status |
|------|-------------|--------|
| Roof plan: DXF implementation | §5.3 | DONE (S162) |
| Roof plan: bay dims + overhang dims | §5.3, §6.1 | DONE (S162) |
| All 4 elevations: DXF | §5.2 | DONE (S161) |
| Elevation layout: building centering | §7.1.1 | DONE (S162 fill audit) |
| DX multi-storey floor plan (Level 2) | §5.1 | GAP — §18 I-03 |
| Dimension tier 3: opening widths | §6.1c | GAP — no schedule yet |
| Section view (A-07) | §5.4 | GAP |
| MEP electrical plan (E-01) | §17 | STUB (S163) |
| MEP plumbing plan (M-01) | §17 | STUB (S163) |
| Reflected ceiling plan (E-02) | §5.0 | GAP |

### 9.5 Open Issues

Current audit-verified open issues. **Authoritative list is §18.**
This section is a summary pointer only — do not duplicate details here.

| Priority | Issues |
|----------|--------|
| P1 — fix now | I-01 DX window tag overlaps, I-02 room label overlaps, I-03 DX Level 2 missing |
| P2 — next session | I-04 MEP segments, I-05 stale files, I-06 Malay labels, I-09 grid triage |
| P3 — backlog | I-07 JKR logo, I-08 fan symbol, I-10 PINDAAN NO row |

See §18 for full descriptions and spec cross-references.

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

**R2 — SVG is the proof.** `--all` implies `--proof` — SVG proof renders
are always generated alongside DXF. The proof SVG is read back from the
DXF and transformed to paper-scale for conformity inspection (entity
positions, layers, `stroke-dasharray` for dash-dot, `<rect>` for white
background). No PNG output — SVG is sufficient. For single-view runs
(e.g. `--floor-plan`), add `--proof` explicitly.

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
python3 drawing_writer_dxf.py <db> --all     # DXF + SVG proof (--proof implied)

# Output per view:
#   output/DXF/{PREFIX}_{VIEW}_{ts}.dxf       — professional deliverable
#   output/SVG/{PREFIX}_{VIEW}_{ts}.svg       — SVG proof for visual QA
#   output/DXF/log_{PREFIX}_{VIEW}_{ts}.txt   — per-view diagnostic log

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
| ROOF_NOT_INVENTED | Curved roof (vertex_count>20): not ALL A-ROOF polylines are axis-aligned rectangles. Passes if ≥1 non-rect polyline exists (inner hull proves mesh was used). §1 R1, R9. | roof plan only, conditional on db_path |
| NO_MEP_BLEED | Architectural floor plan has 0 entities on MEP layers | floor plan only |
| LANGUAGE | No Malay strings in A-TTLB or A-ANNO-TEXT layers | all sheets |

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
| `internal/SpecToCode.md` | **Spec-to-code checklist** — 134 items, 134/134 Y (100%) as of 2026-04-08 |

---

## 12. Scaling Roadmap — Beyond SampleHouse

SH is the Rosetta Stone. DX (Duplex) is the first scale test because it has
two storeys and MEP elements — the two capabilities absent from SH.

### 12.1 Multi-Storey (§3.0c)

**Spec requirement:** For each storey in the compiled DB, generate one FLOOR
plan sheet. Sheet numbering follows storey elevation order:

```
A-01-GF   Ground Floor Plan    storey_elevation ≈ 0.000
A-01-FF   First Floor Plan     storey_elevation ≈ 3.000
A-01-RF   Roof Floor Plan      storey_elevation ≈ 6.000
```

**Implementation:** `write_floor_plan_dxf()` already accepts `storey_filter`.
Add a storey iteration loop in `main()` that:
1. Queries distinct storeys from DB (sorted by elevation)
2. Derives a short code per storey (GF, FF, 2F, 3F…)
3. Calls `write_floor_plan_dxf()` once per storey
4. Adds each storey sheet to the `{PREFIX}_2D.json` page table

**`{PREFIX}_2D.json` storey-aware page row:**
```json
{
  "sheet": "A-01-GF",
  "title": "GROUND FLOOR PLAN",
  "file": "FLOOR_GF",
  "type": "plan",
  "storey": "Ground Floor",
  "storey_elevation_m": 0.0,
  "status": "DONE"
}
```

**Tests to add (§10.3 Multi-storey):**
- `test_storey_count` — output FLOOR sheet count = storey count in DB
- `test_storey_boundary` — no element from storey N appears in storey N+1 plan
- `test_storey_labels` — sheet title contains storey name, sheet number contains storey code
- `test_storey_grids_consistent` — same grid labels appear on all storeys (structural grid is constant)

### 12.2 MEP Discipline Sheets (§5.6)

TB-LKTN includes M-series (mechanical/plumbing) and E-series (electrical)
sheets alongside the A-series architectural sheets. DX has pipes and
electrical elements; this is the first real test.

**MEP sheet types:**

| Sheet | Title | IFC filter | AIA layers |
|-------|-------|-----------|-----------|
| M-01-GF | GROUND FL PLUMBING | IfcPipe, IfcPipeSegment, IfcPipeFitting, IfcFlowTerminal | A-MECH-PIPE, A-MECH-EQPM |
| M-02-GF | GROUND FL HVAC | IfcDuctSegment, IfcDuctFitting, IfcAirTerminal | A-HVAC-DUCT, A-HVAC-EQPM |
| E-01-GF | GROUND FL ELECTRICAL | IfcCableCarrierSegment, IfcSwitchingDevice, IfcElectricAppliance | A-ELEC-POWR, A-ELEC-LITE |

**Spec rule:** Each MEP sheet is a FLOOR plan pass with a discipline filter.
The wall section cut is SHOWN (thin, gray, not bold) as spatial context.
MEP elements render at their symbol from `2d_drawing_symbol`. Elements with
no symbol entry render as their bounding box centroid with a question mark tag.

**`2d_drawing_style` rows needed** (add to 2D.db):
```sql
INSERT INTO 2d_drawing_style (ifc_class, view_type, stroke, fill, stroke_width, shape)
VALUES
  ('IfcPipeSegment',   'plan', '#0055AA', 'none', 0.25, 'line'),
  ('IfcDuctSegment',   'plan', '#006600', 'none', 0.35, 'rect'),
  ('IfcFlowTerminal',  'plan', '#0055AA', 'none', 0.18, 'symbol'),
  ('IfcSwitchingDevice','plan','#AA6600', 'none', 0.18, 'symbol'),
  ('IfcElectricAppliance','plan','#AA6600','none', 0.18, 'symbol');
```

**Tests to add (§10.4 Discipline separation):**
- `test_mep_discipline_separation` — M-01 contains zero IfcWall-cut entities at full weight; A-01 contains zero IfcPipe entities
- `test_mep_symbol_coverage` — every IFC class in MEP DB has a `2d_drawing_style` row; no question-mark placeholders
- `test_mep_wall_context` — M-01 wall outlines use gray stroke (A-MECH-WALL layer, lw=0.18), not bold black

### 12.3 Auto-Scale for Large Buildings (§7.1b)

SH at 1:100 fits A3. A hospital floor at 100m × 60m at 1:100 = 1000×600mm —
far exceeds A3. The template needs **auto_scale**:

```json
"paper": {
  "auto_scale": true,
  "auto_scale_target_fill": 0.60,
  "_note": "Scale chosen so building occupies 60% of content width"
}
```

**Algorithm:**
```
content_width = paper.width_mm - margins.left - margins.right - title_block_width_mm
building_width_mm = (bld_max_x - bld_min_x) / 1000  (metres → mm at 1:1)
raw_scale = building_width_mm / (content_width * target_fill)
scale = round up to nearest standard: 50, 100, 200, 250, 500, 1000
```

Standard scales (ISO 5455): 1:50, 1:100, 1:200, 1:250, 1:500, 1:1000.
Log: `§7.1b auto_scale: building=102.4m content=265mm → scale=1:500`.

**Test:** `test_auto_scale_fits` — building fill ≥ 40% and ≤ 80% of content area.

### 12.4 Irregular Grid Detection (§4.1b)

SH and DX have orthogonal grids (walls align to X/Y). Large buildings —
hospitals, airports, university campuses — often have:
- Wings at non-90° angles
- Rotated structural grids
- Multiple grid systems per building

**Spec for rotated grids:**
1. Compute dominant wall angle via PCA on all wall midpoint vectors
2. If dominant angle deviates > 5° from 0°/90°, rotate coordinate system
3. Derive grids in rotated space
4. Apply inverse rotation to grid line start/end points for DXF output
5. Grid labels remain A,B,C,D / 1,2,3

**Failure mode without this:** derive_grids() treats rotated walls as noise →
produces 0-2 grids instead of 6-8 → conformity gate fails on GRID_COUNT.

**Test:** `test_rotated_grid_30deg` — synthetic 30° rotated building produces
≥ 4 grids with correct label sequence.

### 12.5 Hospital Scale — Known Constraints

| Constraint | Severity | Resolution |
|-----------|----------|------------|
| Floor size > A3 at 1:100 | Medium | §7.1b auto_scale |
| Room label collision (100+ rooms) | Medium | Collision detection, font size reduction |
| Irregular / multi-wing footprint | High | Wing decomposition or zone tiling |
| Non-orthogonal structural grid | High | §4.1b rotated grid detection |
| Medical gas / specialist MEP symbols | Low | Add rows to `2d_drawing_symbol` |
| Section cut performance (100m floor) | Low | SQLite handles it; mesh math is O(elements) |

The architectural A-series (floor plans, elevations, roof plan) will produce
correct output for a regular hospital block with auto_scale + storey iteration.
The main blocker for complex hospital geometry is non-orthogonal grids.

---

## 13. Industry Comparison

### 13.1 What the Big Players Do

No major BIM platform produces fully automated 2D output. All require a
human to compose sheets before drawings are generated.

| Platform | Sheet Automation | Dimension Automation | IFC-native | Assessment |
|----------|-----------------|---------------------|-----------|------------|
| **Autodesk Revit** | Manual view placement on sheets | Manual or semi-auto | Import only | Industry standard; zero automation on sheet composition |
| **Graphisoft ArchiCAD** | Publisher batch-exports; views manually composed | Manual | Better IFC | Same manual composition problem |
| **Bentley OpenBuildings** | i-model publishing; views manually set up | Manual | Good | Same |
| **Vectorworks** | Best scripting support (Python/Marionette) | Partial | Good | Closest; but bespoke per project, not compiler-driven |
| **Speckle / Hypar** | Cloud parametric; geometry only | None | Good | No 2D production |
| **EvolveLAB (Revit)** | Dynamo sheet automation | None | No | Revit-dependent; not IFC-native |

**The gap none of them close:** IFC → fully automated, standards-compliant 2D
with zero human composition steps. In every platform listed, a human must
decide where views sit on paper, what scale to use, where annotations go.
This project computes all of that from DB coordinates.

### 13.2 The Compiler Paradigm — Why It's Different

Every existing BIM tool treats 2D drawings as **views of a model** that a
human composes. This project treats 2D as a **compiled artifact**: given an
IFC + a standard (TB-LKTN), the compiler deterministically produces the
drawing. No human decisions inside the loop.

This is analogous to the difference between a programmer editing assembly
and a compiler that takes high-level source. The model is the source. The
drawing is the binary.

**Consequences:**
- **Reproducible.** Same IFC + same template → byte-identical output. No
  "the architect moved this text box". This matters for regulatory submissions
  and audit trails.
- **Scalable.** 500 identical housing units → 500 drawing sets in one pipeline
  run. No per-unit manual work.
- **Rosetta Stone effect.** Once 35 reference buildings define the pattern,
  any NEW building that passes through the compiler inherits all proven drawing
  conventions automatically.
- **Standard-portable.** Swap `drawing_template.json` and `2D.db` for a
  different national standard (Singapore BCA, UK NBS, Australian NCC) and
  the same pipeline produces drawings to that standard.

### 13.3 Market Impact

**Emerging markets.** TB-LKTN (Malaysia JKR) compliant drawings are required
for government housing submissions. A Revit license costs USD 3,000–5,000/year
per seat. An open IFC → 2D compiler removes that barrier entirely for small
practices, contractors, and government agencies processing high volumes.

**Mass housing.** Government housing programmes (Malaysia PR1MA, Singapore HDB,
Indonesia FLPP) build tens of thousands of identical or near-identical units.
Manual drawing production per unit is the bottleneck. A compiler with
parametric variation (change room dimensions → recompile → new drawing set)
removes that bottleneck.

**Regulatory review at scale.** A planning authority reviewing 10,000 building
submissions per year cannot manually check each drawing. If drawings are
compiler-produced and machine-readable (DXF with GUID xdata), automated
compliance checking becomes possible — check wall thickness from DB, verify
room area from label, cross-reference grid positions.

**The IFC round-trip.** DXF output with GUID xdata means the 2D drawing can
be imported back into Revit/ArchiCAD and elements can be traced back to the
original IFC objects. This closes the round-trip that all major platforms
claim to support but none actually implement for automated 2D.

### 13.4 Showstoppers Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Non-orthogonal building geometry | High for complex projects | Broken grids | §4.1b rotated grid detection |
| Scale mismatch (large buildings) | Certain for hospitals | Won't fit paper | §7.1b auto_scale |
| MEP symbol gaps | Medium | Placeholder tags | Incremental `2d_drawing_symbol` additions |
| Room label collision | High for dense plans | Illegible labels | Collision detection pass |
| Multi-wing footprint | Medium for hospitals | Wrong bounding box | Wing decomposition |
| Performance | Low | Slow but correct | Not a blocker |

**No fundamental architecture showstopper.** The pipeline — IFC → SQLite →
section cut → grid detection → annotation → DXF → SVG — is sound at any
scale. The constraints above are engineering problems with known solutions,
not paradigm failures. The most complex building in the world has walls,
grids, rooms, and levels. The compiler handles all of them; it just needs
scale-appropriate parameters and extended symbol libraries.

---

## 14. Standards Enforced by Code

### 14.1 Language Rule (English Only)

**All text rendered in DXF/SVG output must be in English.**

This applies to:
- Room labels (draw from `2d_room_label.display_text` — keep English column)
- Title block field labels (`FLOOR PLAN`, `PROJECT`, `CLIENT`, `ARCHITECT` etc.)
- Level marker labels (`GRD. FLOOR LEVEL`, `BEAM/CEILING LEVEL`, `APRON LEVEL`)
- Schedule headers (`DOOR & WINDOW SCHEDULE`, `SYMBOLS`, `DESCRIPTIONS`, `QTY.`)
- Grid reference legend (`GRID REFERENCE`, `HORIZONTAL (X)`, `VERTICAL (Y)`, `TOTAL`)
- MEP legend (`ELECTRICAL LAYOUT`, `PLUMBING LAYOUT`)
- Drawing title (`FLOOR PLAN`, `FRONT ELEVATION`, `ROOF PLAN` etc.)
- Scale bar label (`scale 1:100` — text below drawing title, NOT in title block)

**What is NOT English in input but must be normalised:**
- `2d_title_block.field_name` values may be Malay (e.g. `TARIKH`, `ARKITEK`) — the
  code must map these to English display labels via a lookup in `_draw_sheet_layout`.
  Mapping table (hard-coded, not invented — mirrors TBKLTN English column):

  | DB field_name | Display label |
  |--------------|---------------|
  | PROJEK       | PROJECT       |
  | PEMILIK      | CLIENT        |
  | ARKITEK      | ARCHITECT     |
  | JENIS_BANGUNAN | BUILDING TYPE |
  | TAJUK_LUKISAN | DRAWING TITLE |
  | DILUKIS      | DRAWN BY      |
  | UKURAN       | SCALE         |
  | TARIBULAN    | DATE          |
  | DISEMAK      | CHECKED BY    |
  | NO_LUKISAN   | DRAWING NO.   |
  | NO_KEPINGAN  | SHEET NO.     |
  | PINDAAN_NO   | REVISION      |

  If a field_name is not in this table, use it as-is (already English).

**Test:** `test_conformity.py` must assert no Malay string appears in any DXF TEXT
entity on layers `A-TTLB` or `A-ANNO-TEXT`. Malay indicator: string contains any
of ('PROJEK','PEMILIK','ARKITEK','JENIS','TARIKH','TARIBULAN','DISEMAK','PINDAAN').

### 14.2 JKR Logo in Title Block

**File:** `input/jkr.png` (exists). Insert as raster image in the JKR header zone.

**Spec:**
```
Title block header zone (top of panel):
┌──────────────────────────────┐  ← top of panel
│ [jkr.png]  PUBLIC WORKS      │
│            DEPARTMENT MALAYSIA│  ← existing header text
├──────────────────────────────┤  ← thick line (0.50mm)
│ DUPLEX RESIDENTIAL           │  ← building type (§A)
│ Ifc4 Duplex                  │  ← building name
├──────────────────────────────┤
```

**DXF image insertion** (`ezdxf`):
```python
image_def = doc.add_image_def('jkr.png', size_in_pixel=(width_px, height_px))
msp.add_image(insert=(x, y), size_in_units=(logo_w, logo_h),
              image_def=image_def, dxfattribs={'layer': 'A-TTLB'})
```
- `logo_w` = `title_block.logo_width_mm * scale` (template key, default 15mm)
- `logo_h` = `title_block.logo_height_mm * scale` (template key, default 12mm)
- Placement: left side of JKR zone, vertically centred
- Header text shifts right by `logo_w + 2mm` gap

**Template keys to add** in `drawing_template.json → title_block`:
```json
"logo_file": "jkr.png",
"logo_width_mm": 15.0,
"logo_height_mm": 12.0
```

**Fallback:** if `jkr.png` not found at `input/jkr.png`, log warning and skip logo
(do not abort). Header text stays centred.

### 14.3 Fan Symbol (MEP)

**File:** `input/fan.png` (exists). Use as the ceiling fan symbol on MEP/Electrical pages.

**Classification:** element_name keywords `'fan'`, `'ceiling fan'` → discipline `ELECTRICAL`,
symbol type `'FAN'`.

**DXF symbol:** When discipline=ELECTRICAL and element is classified FAN:
- Insert `fan.png` as raster image centred at element centroid
- Size: `2d_drawing_symbol.symbol_size_mm` (default 5mm) × scale
- Fallback geometric: asterisk (6 lines at 30° intervals through centre, radius = sym_r)

**Legend entry:** `FAN` row in MEP legend panel with `fan.png` thumbnail or asterisk glyph.

---

## 15. Grid Dimension Triage

### 15.1 Problem

When bay spacing is small at the drawing scale, dimension text overlaps adjacent
text and becomes illegible. The right panel GRID REFERENCE legend lists ALL bays
even when most are already clearly legible in the drawing — wasting panel space.

### 15.2 Triage Function

```python
def _triage_grid_dims(grids, scale, crowding_threshold_mm=15.0):
    """
    Classify each bay dimension as INLINE or PANEL.

    Returns:
        inline_dims: list of GridBay — show dim text in drawing AND keep in drawing
        panel_dims:  list of GridBay — suppress inline text, list ONLY in panel

    Rules:
        bay_paper_mm = (grid[i+1].pos - grid[i].pos) * 1000 / scale
        if bay_paper_mm >= crowding_threshold_mm → INLINE (legible in drawing)
        if bay_paper_mm <  crowding_threshold_mm → PANEL  (too small to label)
        Overall tier-1 dim is always INLINE.

    Log (mandatory):
        §TRIAGE {axis}: {label1}-{label2} = {dist_mm}mm ({paper_mm:.1f}mm@1:{scale}) → {INLINE|PANEL}
    """
```

**GridBay** namedtuple: `(label1, label2, dist_m, paper_mm, tier)`

### 15.3 Panel Legend Rule

The GRID REFERENCE panel shows **only panel_dims** (crowded bays).
If `panel_dims` is empty → show one line: `ALL DIMS SHOWN IN DRAWING`.

**Drawing behaviour:**
- `inline_dims` → dim line + tick + text rendered in drawing (normal)
- `panel_dims` → dim line + tick rendered, but text SUPPRESSED in drawing
  (the panel legend is the only label for these bays)

**Tier-1 overall dim** is always shown inline in the drawing regardless of width.

### 15.4 Log Output

Every bay must be logged:
```
§TRIAGE X: A-B = 4900mm (49.0mm@1:100) → INLINE
§TRIAGE X: B-C =  200mm ( 2.0mm@1:100) → PANEL
§TRIAGE X: OVERALL = 5100mm (51.0mm@1:100) → INLINE (forced)
§TRIAGE Y: 1-2 = 3000mm (30.0mm@1:100) → INLINE
§TRIAGE PANEL: 1 bay listed (X: B-C)
```

### 15.5 Conformity Audit

`layout_audit.py` adds check **G02**:
- Count PANEL bays from triage log; verify panel legend row count matches
- PASS: panel count == triage PANEL count
- FAIL: mismatch (panel shows bays not in drawing or vice versa)

---

## 16. Log Standard

### 16.1 Principle

**The log is the only debugging tool.** No manual DB queries. No print-and-check.
A coder reading the log file must understand EXACTLY what happened, with which
values, in which order, without opening any source file.

### 16.2 Per-View Log Files

In addition to the consolidated `dxf_diagnostic.txt`, each view writes its own
log: `output/log_{PREFIX}_{VIEW}_{ts}.txt`

Examples:
```
output/log_SH_FLOOR_20260408_2116.txt
output/log_DX_FRONT_20260408_2116.txt
output/log_DX_PLUMBING_20260408_2116.txt
```

The consolidated `dxf_diagnostic.txt` is the concatenation of all view logs
for the session, plus a session header.

### 16.3 Mandatory Log Events

Every function in `drawing_writer_dxf.py` must emit these events:

| Event | Format | When |
|-------|--------|------|
| ENTRY | `§ENTRY {func}({params})` | On function entry with all param values |
| QUERY | `§QUERY {sql_summary}: {n} rows` | After every DB query |
| SAMPLE | `  sample[0]: {row}` | First row of every query result (if any) |
| TRIAGE | `§TRIAGE {axis}: {label1}-{label2}={dist}mm ({paper}mm@{scale}) → {INLINE\|PANEL}` | Per bay in triage |
| RENDER | `§RENDER {entity_type} layer={layer} src={source} at ({x:.1f},{y:.1f})` | Per entity rendered |
| SKIP | `§SKIP {entity_type} reason={reason}` | When an entity is not drawn |
| VALUE | `§VALUE {name}={value} (from {source})` | Every computed constant |
| AUDIT | `§AUDIT {check}: {expected} actual={actual} → {PASS\|FAIL}` | Per conformity check |
| EXIT  | `§EXIT {func}: {summary}` | On function return with count/outcome |

### 16.4 VALUE Events (mandatory)

Every constant that governs output geometry must be logged as a VALUE event:

```
§VALUE scale=100 (from template)
§VALUE paper_w=420.0mm (from template.paper.width_mm)
§VALUE tb_width=75.0mm (from template.paper.title_block_width_mm)
§VALUE grid_ext=20.0mm (from template.grid.extend_beyond_building_mm)
§VALUE bubble_r=4.0mm (from template.grid.bubble_radius_mm)
§VALUE tier1_offset=12.0mm (from template.dimensions.tier_1_offset_mm)
§VALUE tier2_offset=6.0mm (from template.dimensions.tier_2_offset_mm)
§VALUE cut_z=1.2m (hardcoded per §5.1a)
§VALUE crowding_threshold=15.0mm (from template.grid.crowding_threshold_mm)
```

### 16.5 RENDER Events

Every entity written to DXF must produce a RENDER log line:
```
§RENDER LWPOLYLINE layer=A-WALL-FULL lw=50 src=guid:1A2B3C pts=4
§RENDER CIRCLE layer=A-GRID src=grid:A pos=(−7.735m,−) r=4.0mm
§RENDER TEXT layer=A-ANNO-DIMS src=dim:A-B val="4900" at (12.3,−45.6)mm
§RENDER HATCH layer=A-WALL-PATT src=guid:1A2B3C area=2.1m²
§RENDER SKIP HATCH reason=zero_area guid=1A2B3C
```

### 16.6 Test Log Format

`test_conformity.py` must write `output/conformity_log.txt` with this format:

```
=== CONFORMITY REPORT {ts} ===
File: {dxf_path}
View: {view_type}

[PASS] BORDER       : border entities=28 on A-TTLB
[PASS] TITLE_BLOCK  : field text entities=12
[PASS] TAJUK        : found "FLOOR PLAN" on A-TTLB
[PASS] NO_LUKISAN   : found "A-01" on A-TTLB
[PASS] GRID_LINES   : 7 lines on A-GRID (expected >=3)
[PASS] GRID_DASHDOT : A-GRID linetype=DASHDOT
[PASS] GRID_LABELS  : vertical=['A','B','C','D'] horizontal=['1','2','3']
[PASS] LINE_WEIGHTS : wall_exterior=50, partition=35, window=25, dim=18
[PASS] WHITE_BG     : 1 bg entity on layer 0
[PASS] NORTH_ARROW  : found INSERT on A-ANNO (plan views only)
[PASS] SCALE_BAR    : 6 segments, label "5m" found
[PASS] PROOF_EXISTS : SVG at output/SVG/FLOOR_{ts}.svg
[PASS] PROOF_WHITE_BG: <rect fill="#FFFFFF"/> found
[PASS] PROOF_DASHDOT: stroke-dasharray found in SVG
[PASS] LANGUAGE     : 0 Malay strings in A-TTLB/A-ANNO-TEXT layers
[PASS] ARROWHEADS   : 0 ARROW entities found outside A-ROOF (slope arrows on A-ROOF are permitted)
[PASS] TRIAGE_PANEL : panel legend=1 bay matches triage PANEL count=1

VALUES LOGGED:
  paper=420.0x278.5mm scale=100 grids_x=2 grids_y=2
  walls=32 doors=3 windows=4 furniture=15
  inline_bays_x=1 panel_bays_x=1 inline_bays_y=2 panel_bays_y=0
  mep_terminals=10 mep_segments=0

SCORE: 17/17 PASS
```

Every check must log: check name, expected value, actual value, PASS/FAIL.
Scores are counts not percentages.

---

## 17. MEP Triage (Extended)

### 17.1 Data Available (DX DB)

```sql
-- Terminals by discipline keyword
SELECT element_name, COUNT(*) FROM elements_meta
WHERE ifc_class='IfcFlowTerminal' GROUP BY element_name;

-- Segments (pipe/duct/wire runs)
SELECT ifc_class, element_name, COUNT(*) FROM elements_meta
WHERE ifc_class IN ('IfcFlowSegment','IfcFlowFitting','IfcFlowController')
GROUP BY ifc_class, element_name;
```

DX has: IfcFlowTerminal=105, IfcFlowSegment=427, IfcFlowFitting=358, IfcFlowController=14.

### 17.2 Terminal Classification

```
ELECTRICAL terminals (element_name keyword match):
  'light', 'fan', 'switch', 'outlet', 'telephone', 'power',
  'socket', 'panel', 'meter', 'luminaire', 'lamp', 'sconce', 'pendant'

PLUMBING terminals:
  'water closet', 'sink', 'shower', 'basin', 'bath',
  'drain', 'trap', 'toilet', 'bidet', 'urinal', 'lavatory'

MEP (unclassified): everything else
```

### 17.3 Segment Rendering

IfcFlowSegment (pipe/duct/wire runs):

```python
# For each segment: draw thin line between bbox long-axis endpoints
# Segment is a pipe/duct: major axis = longest bbox dimension
# If maxX-minX > maxY-minY: horizontal run → draw line (minX,midY)→(maxX,midY)
# Else: vertical run → draw (midX,minY)→(midX,maxY)
# Layer: A-MEP-ELEC (electrical) or A-MEP-PLMB (plumbing) lw=0.13mm
```

Log: `§RENDER SEGMENT layer=A-MEP-PLMB src=guid:{g} ({x1},{y1})→({x2},{y2})`

### 17.4 Fitting Rendering

IfcFlowFitting (junctions/elbows): draw small filled circle at centroid.
- Radius = `sym_r * 0.4` (smaller than terminal symbols)

IfcFlowController (valves/switches): draw filled square at centroid.
- Size = `sym_r * 0.8 × sym_r * 0.8`

### 17.5 Discipline Pages

| DB content | Page | Title |
|-----------|------|-------|
| ELECTRICAL terminals found | E-01 | ELECTRICAL PLAN |
| PLUMBING terminals found | M-01 | PLUMBING LAYOUT |
| Both found | Two pages | E-01 + M-01 |
| Neither | M-01 | MEP LAYOUT (general) |

`{PREFIX}_2D.json` must list both pages when both disciplines are present.
Status=STUB until segment rendering is implemented; status=DONE when both
terminals AND segments are rendered and conformity passes.

### 17.6 MEP Symbol Legend in Panel

The right panel on MEP sheets replaces the door/window schedule with:

```
┌─────────────────────────────────┐
│  SYMBOLS  │ DESCRIPTIONS  │ QTY │
├─────────────────────────────────┤
│  [sym]    │ Ceiling Light  │  9  │
│  [sym]    │ Ceiling Fan    │  5  │
│  [sym]    │ Switch 1-gang  │  4  │
│  [sym]    │ Power Outlet   │  8  │
│  ─────────│ PLUMBING ─────│     │
│  [sym]    │ Water Closet   │  4  │
│  [sym]    │ Shower         │  2  │
│  [sym]    │ Basin          │  2  │
└─────────────────────────────────┘
```

QTY is count from DB query. Symbols match what is drawn.
Log: `§MEP LEGEND: {n} rows ({n_elec} electrical, {n_plumb} plumbing)`

---

### 17.7 Abstract Element Footprint Rule (symbol rendering)

**Principle:** Every element already has geometry. Use it. Do not map names to
hardcoded shapes. Do not maintain a symbol library of circles and crosses.

For any IfcFlowTerminal, IfcFurniture, or fixture in plan view:

```
symbol = project element bounding box onto the plan plane
         → LWPOLYLINE: (minX,minY)→(maxX,minY)→(maxX,maxY)→(minX,maxY)→close
         layer: per discipline (A-MEP-ELEC / A-MEP-PLMB / A-FURN)
         lineweight: terminal_line_weight_mm from template
```

No keyword-to-symbol mapping. No symbol_code lookup. No geometry invention.
The IFC already contains the element's real footprint — that IS the symbol.

**Why this generalises:** Any building, any fixture type, any country standard.
A Malaysian bath tub, a Japanese toilet, a European shower unit — all render
correctly because the geometry comes from the IFC, not from a lookup table.

**Sized correctly:** `elements_rtree` gives minX/maxX/minY/maxY in world
metres. Project to plan-space mm via `_mh()`. The outline is the element.

**Legend:** The element_name (trimmed at first `:`) is the description.
QTY is count of identical element_name prefixes per discipline.

```python
# Mandatory log per terminal:
§SYMBOL_FOOTPRINT guid={guid} element='{name}' bbox=({w:.0f}×{h:.0f}mm)
                  layer={layer} at ({cx:.0f},{cy:.0f})
```

**Fallback (no rtree data):** single circle, radius = `terminal_symbol_radius_mm`,
log `§SYMBOL_FALLBACK guid={guid} reason=no_rtree`.

**Test (§10.x):** For each rendered MEP terminal:
- Verify an LWPOLYLINE exists on the MEP layer within ±1mm of element centroid
- Verify LWPOLYLINE width = `maxX-minX` and height = `maxY-minY` (±5%)
- Zero circles on MEP layer = pass (circles indicate fallback, not footprint)
- Log line `§SYMBOL_FOOTPRINT` must appear once per terminal

This replaces the `_draw_mep_symbol()` / `_lookup_symbol_code()` approach
committed in [2D_018] — that was a stepping stone, this is the spec.

---

## 18. Open Issues (as of 2026-04-09)

Tracked against TBKLTN WD-1/01 reference and `layout_audit.txt`.

**Issue-tracking rule:** When user reports a problem, coder must:
1. Check §18.1 — is it already listed?
2. If NO → add immediately with severity and spec ref, confirm: "Added as I-{N}"
3. If YES → update the existing row
4. Every spec change must have a matching `internal/2DSpecToCode.md` row in
   the same session

**Agent scope rule:** Multi-agent prompts must include:
- "ONLY modify the specific code blocks described below"
- "Do NOT delete, rename, or refactor any function not named in this prompt"
- "Run `git diff --stat` before finishing — if >20 lines changed outside
  the target functions, STOP and explain"

### 18.1 Active

**Single source of truth: `2D_Layout/OPEN_ISSUES.txt`**
Do not duplicate issues here. Add to that file, not this section.

### 18.2 Done (reference only — one-line pointers)

| # | Done in | Summary |
|---|---------|---------|
| I-04 | 2D_012 | MEP segments rendered on DX M-01 |
| I-05 | 2D_015 | Stale files removed, 2-version retention |
| I-06 | 2D_012 | English field labels, LANGUAGE conformity |
| I-09 | 2D_012 | Grid triage panel |
| I-15 | 2D_015 | Curved roof mesh hull + offset thickness |
| I-16 | 2D_015 | R8 reuse rule added to §1 |
| I-17 | 2D_016 | SpecToCode re-audit: line refs updated (3722), R8/R9/R10 + ROOF_NOT_INVENTED/NO_MEP_BLEED/LANGUAGE rows added. FLAG: `_inward_offset_hull` inner hull synthetic (R9 low-priority). |
| I-11 | 2D_017 | MEP bleed: `_BELOW_SKIP` had all 4 IfcFlow* classes. DX_FLOOR confirmed 0 MEP entities. Conformity grouping bug fixed — NO_MEP_BLEED now verifiable. |
| I-12 | 2D_017 | MEP template gap: `mep` section added with all keys. Remaining hardcodes `r*0.25` → `inner_dot_radius_factor=0.25` and `'○'`/`'●'` → `legend_char` per symbol now template-driven. |
| I-14 | 2D_017 | Hardcoded symbols: `write_mep_plan_dxf` + `_draw_mep_symbol` added to `SCAN_FUNCTIONS`. `test_no_hardcode.py` → ALL PASS. |

### 18.3 Deferred (spec written, not started)

| # | Issue | Spec section |
|---|-------|--------------|
| I-20 | Roof surface hatching (tile/slab pattern) | §5.3 extension |
| I-21 | APRON level marker (4th level between GRD and FFL) | §4.3b |
| I-23 | Reflected ceiling plan (A-07) | status=GAP |


---

## 19. Professional Drawing Component Standards

> **Purpose:** This section defines every drawing component to professional
> Malaysian JKR standard, as this system cannot visually inspect its own output.
> For each component: exact geometry, template keys, DB source, mandatory log
> lines, and audit check. A coder reading the log must be able to confirm the
> drawing is correct WITHOUT opening the DXF file.

---

### 19.1 Sheet Composition

**What it must look like (professional standard):**
A3 landscape sheet. 10mm border all sides (25mm left for binding).
Title block panel: right side, 75mm wide, full sheet height.
Drawing area: everything left of title block separator line.
Drawing centred in the drawing area with visible margin on all four sides.

**Template keys:**
```json
"paper": {
  "width_mm": 420, "height_mm": 297,
  "margin_left_mm": 25, "margin_right_mm": 10,
  "margin_top_mm": 10, "margin_bottom_mm": 10,
  "title_block_width_mm": 75,
  "border_weight_mm": 0.50
}
```

**Log proof (mandatory):**
```
§VALUE paper=420.0x297.0mm margins=25/10/10/10mm title_block=75.0mm (template)
§VALUE drawing_area_w=305.0mm drawing_area_h=277.0mm (paper - margins - title_block)
§VALUE building_w={W}mm building_h={H}mm fill_x={pct}% fill_y={pct}% (DB extents vs drawing area)
§AUDIT FILL: fill_x={pct}% in [10%,70%] → PASS/FAIL
§AUDIT ASPECT: ratio={r} in [1.2,4.0] → PASS/FAIL
```

Fill target: building occupies 30–65% of drawing area width and 25–65% of height.
If fill_x < 10%: building is too small → scale is too small (auto_scale flag).
If fill_x > 70%: building is too large → margins squeezed.

**Audit check P02:** `layout_audit.py` verifies fill 10%–70%. Currently checks
"within limit" without logging the exact percentages — must log both values.

---

### 19.2 Title Block Panel

**What it must look like:**

```
┌─────────────────────────────────┐  ← top of sheet (margin_top)
│  [jkr.png 15x12mm]              │
│  PUBLIC WORKS DEPARTMENT MALAYSIA│  ← 3.5mm bold, centred
│  ─────────────────────────────  │  ← lw 0.50mm
│                                 │
│  DUPLEX RESIDENTIAL             │  ← 5.0mm bold (building type §A)
│  Ifc4 Duplex                    │  ← 3.0mm normal (building name)
│  ─────────────────────────────  │  ← lw 0.35mm
│                                 │
│  PROJECT     │                  │  ← 2.0mm label / 3.0mm value
│  ────────────────────────────── │
│  CLIENT      │                  │
│  ────────────────────────────── │
│  ARCHITECT   │                  │
│  ────────────────────────────── │
│       [architect stamp area]    │  ← reserved zone, 25mm tall
│  ────────────────────────────── │
│  BUILDING TYPE │ DUPLEX RESID.  │
│  ────────────────────────────── │
│  DRAWING TITLE │ FLOOR PLAN     │
│  ────────────────────────────── │
│  DRAWN BY    │                  │
│  SCALE       │ 1:100            │
│  DATE        │                  │
│  CHECKED BY  │                  │
│  ────────────────────────────── │
│  DRAWING NO. │ SHEET NO.        │
│              │ A-01             │
│  ────────────────────────────── │
│  REVISION    │                  │  ← PINDAAN NO
└─────────────────────────────────┘  ← bottom of sheet (margin_bottom)
```

**Field label mapping (English — §14.1):**
All labels display in English regardless of `field_name` in DB.
`SCALE` field value = `f"1:{scale}"` (integer, from template).
`DATE` field value = from `2d_title_block.default_value WHERE field_name='TARIBULAN'`.
`DRAWING NO.` + `SHEET NO.` share one row, divided at 50/50 of panel width.

**Architect stamp zone:** 25mm tall reserved rectangle (no text, just border lines).
Template key: `title_block.stamp_height_mm: 25.0`. Log: `§VALUE stamp_zone=25.0mm`.

**Revision row:** Single row at bottom. `REVISION` label + empty value cell.
If revisions exist in DB: list `Rev.A`, `Rev.B` etc.

**Log proof:**
```
§RENDER TITLEBLOCK fields=12 logo=YES stamp_zone=25mm lang=EN
§VALUE field[0] label="PROJECT" value="" y={y:.1f}mm
§VALUE field[1] label="CLIENT" value="" y={y:.1f}mm
...
§VALUE field[11] label="REVISION" value="" y={y:.1f}mm
§AUDIT LANGUAGE: 0 Malay strings in A-TTLB → PASS
§AUDIT TITLEBLOCK: 12 field rows drawn → PASS
```

**Critical:** `SCALE` value must be drawn as `1:100` not just `100`.
`DRAWING TITLE` value must exactly match `{PREFIX}_2D.json pages[].title`.

---

### 19.3 Grid Lines and Bubbles

**What it must look like:**

```
       (A)  ←─ bubble: circle r=4mm, white fill, label centred
        │         bubble sits OUTERMOST (beyond all dim lines)
    ─ ─ ┼ ─ ─    dash-dot line, colour #888888, lw=0.18mm
        │         line extends 20mm beyond building each side
 ───────┼─────── building boundary
        │
 ─ ─ ─ ┼ ─ ─ ─
        │
       (A)  ←─ bubble at other end
```

**Positioning rule (absolute):**
Grid line runs from `bld_edge - grid_ext` to `bld_edge + grid_ext + bubble_r*2`.
Bubble centre at `bld_edge + grid_ext + bubble_r`.
Dimension lines (tiers) sit BETWEEN building edge and bubble.
Tier-1 (outer) dim at `bld_edge + tier1_offset_mm`.
Tier-2 (inner) dim at `bld_edge + tier2_offset_mm`.
Constraint: `tier2_offset < tier1_offset < grid_ext`.

Current values (template): `tier1=12mm, tier2=6mm, grid_ext=20mm`. ✓

**Label skip rules:** Skip `I` (looks like 1), skip `O` (looks like 0).
Template provides full sequence; code uses it as-is from `vertical_axis_labels`.

**Dash-dot pattern:** 4mm dash, 1mm gap, 1mm dot, 1mm gap — at paper scale.
In model space: multiply all values by `scale`. DXF linetype DASHDOT.

**Log proof:**
```
§GRID X: 4 axes detected (A,B,C,D) from {n} structural elements
§RENDER CIRCLE layer=A-GRID src=grid:A pos=(-7735,-) r=400 (model units)
§RENDER HATCH layer=A-GRID fill=white src=grid:A (bubble white fill)
§RENDER LINE layer=A-GRID src=grid:A from=(-7735,-14200) to=(-7735,5755) linetype=DASHDOT
§RENDER TEXT layer=A-GRID src=grid:A label="A" at (-7735,5155) h=300
§AUDIT GRID_LINES: 7 lines on A-GRID (expected >=3) → PASS
§AUDIT GRID_DASHDOT: A-GRID linetype=DASHDOT → PASS
§AUDIT GRID_LABELS: vertical=['A','B','C','D'] horizontal=['1','2','3'] → PASS
```

---

### 19.4 Dimension Chains (Three Tiers)

**What it must look like:**

```
(A)─────────────────────────────────────────────────────────(B)
         │◄─────────── 9900 ─────────────────────►│       ← Tier 1: overall
         │◄── 3100 ──►│◄──── 3700 ────►│◄── 3100 ►│       ← Tier 2: bay spacing
         │◄─1300─►│                   │                    ← Tier 3: opening sub-dims
                  ↑                   ↑
           45° tick                 45° tick
```

**Tier definitions:**
- **Tier 1 (outermost):** overall building dimension. Single span, always INLINE.
- **Tier 2 (middle):** grid-to-grid bay spacing. Multiple spans. INLINE if bay ≥ 15mm paper; PANEL if < 15mm.
- **Tier 3 (innermost, adjacent to building):** sub-bay dimensions — door/window openings within a bay, partition wall offsets. **Currently GAP.**

**Terminator geometry (NO arrowheads):**
```
Tick = diagonal line at 45°, crossing the dim line.
half_length = dimensions.tick_half_length_mm (default 1.5mm) × scale
tick_dx = half_length × cos(45°) = half_length × 0.7071
tick_dy = half_length × sin(45°) = half_length × 0.7071
tick line: (dim_x - tick_dx, dim_y - tick_dy) → (dim_x + tick_dx, dim_y + tick_dy)
```

**Extension line geometry:**
```
extension_gap_mm = 2.0mm × scale   (gap between building face and ext line start)
extension_overshoot_mm = 2.0mm × scale (ext line extends beyond dim line)
ext_line: from (grid_x, bld_edge + extension_gap) to (grid_x, dim_y + extension_overshoot)
```

**Text placement:**
```
text centred on dim line: x = (grid1.pos + grid2.pos) / 2
text y = dim_y + dim_txt_h × 0.4  (just above the dim line)
if bay_paper_mm < crowding_threshold: suppress text (§15 triage)
if text would overlap neighbour: offset outward with leader line (Tier 3)
```

**Log proof (per dimension):**
```
§DIM X tier=1 A-D: from=-7735 to=6270 dist=14005mm paper=140.1mm@1:100 → INLINE text="14005"
§DIM X tier=2 A-B: from=-7735 to=-2835 dist=4900mm paper=49.0mm@1:100 → INLINE text="4900"
§DIM X tier=2 B-C: from=-2835 to=1620 dist=4455mm paper=44.6mm@1:100 → INLINE text="4455"
§RENDER LINE layer=A-ANNO-DIMS src=dim:A-B y={dim_y:.1f} from=-7735 to=-2835 lw=18
§RENDER LINE layer=A-ANNO-DIMS src=dim:A-B extline_left from=-7735,{gap} to=-7735,{overshoot}
§RENDER LINE layer=A-ANNO-DIMS src=dim:A-B tick_left from=(-7735-{dx},{dim_y}-{dy}) to=(-7735+{dx},{dim_y}+{dy})
§RENDER TEXT layer=A-ANNO-DIMS src=dim:A-B val="4900" at (-5285,{txt_y:.1f})
§AUDIT ARROWHEADS: 0 ARROW entities outside A-ROOF layer → PASS  (slope arrows on A-ROOF exempted)
```

---

### 19.5 Floor Plan Section Cut

**What it must look like:**

```
Wall at cut:          ████████████████  ← bold (0.50mm) solid filled black polygon
Partition at cut:     ▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ← medium (0.35mm) solid filled grey
Door at cut:          [ opening ]─ ─ ─  ← opening gap + swing arc + leaf line
Window at cut:        │═══════│         ← double line (frame) + centre glass line
Furniture (below):    ┌──────┐          ← thin (0.18mm) rectangle
Room label:           BEDROOM           ← 3.5mm, centred in room
Room area:            12.3 m²           ← 2.5mm, below room name
Tag (door):           ①               ← circle with number, A-ANNO-TEXT
Tag (window):         W1              ← hexagon with code, A-ANNO-TEXT
```

**Door symbol (professional standard):**
```
1. Door leaf: line from hinge point to open position
   Length = door_width (from DB: element maxX - minX or maxY - minY)
   Hinge at wall end (determined by which wall face door is in)
2. Swing arc: quarter-circle from closed to open
   Centre = hinge point, radius = door_width
   Arc spans 90° from closed direction to open direction
   Room side: arc opens toward the larger room (DB: spatial containment or centroid heuristic)
3. Layer: A-DOOR, lw = 0.25mm
4. Opening gap: remove wall segment within door width
```

**Window symbol (professional standard):**
```
1. Wall opening gap (same as door)
2. Two frame lines: parallel to wall face, at wall outer and inner surfaces
   Offset = wall_thickness × 0.1 (10% of wall thickness = frame reveal)
3. Glass centre line: single line at wall centre, full opening width
   Layer: A-GLAZ, lw = 0.25mm
4. Louvre lines (elevation only): horizontal lines at 3mm spacing inside window rect
```

**Wall fill (professional standard):**
```
Exterior walls: HATCH pattern SOLID on A-WALL-PATT, fill=black
Interior walls: HATCH pattern SOLID on A-WALL-PRTN, fill=50% grey (ACI colour 9)
  - Both require LWPOLYLINE on main layer + HATCH on fill layer
  - HATCH uses same polyline as boundary
```

**Log proof:**
```
§SECTION cut_z=1.2m: 32 CUT, 15 BELOW, 8 ABOVE (DB: 55 elements total)
§RENDER LWPOLYLINE layer=A-WALL-FULL src=guid:1A2B3C pts=4 lw=50 (exterior cut)
§RENDER HATCH layer=A-WALL-PATT src=guid:1A2B3C pattern=SOLID fill=black
§RENDER ARC layer=A-DOOR src=guid:D001 hinge=(-5.2,-1.3) r=0.9m span=90° side=ROOM
§RENDER LINE layer=A-GLAZ src=guid:W001 glass_centre at y=-0.95 x=(-3.2 to -1.8)
§RENDER TEXT layer=A-ANNO-TEXT src=2d_room_label:BEDROOM val="BEDROOM" at (-4.1,-0.5) h=350
§RENDER TEXT layer=A-ANNO-TEXT src=computed val="12.3 m²" at (-4.1,-1.2) h=250
§AUDIT DOORS: 3 arcs on A-DOOR → PASS (matches door count=3)
§AUDIT WINDOWS: 12 lines on A-GLAZ → PASS (4 windows × 3 lines each)
```

---

### 19.6 Elevation View

**What it must look like:**

```
     (A)       (B)       (C)       (D)
      │         │         │         │        ← vertical grid lines, dash-dot
──────┤         │         │         ├──      ← BEAM/CEILING LEVEL ▽ +2.300
      │  ┌────┐ │ ┌──────┐│ ┌────┐  │        ← wall openings (windows/doors)
      │  │////│ │ │//////││ │////│  │        ← window glass hatching (horizontal)
──────┤  └────┘ │ └──────┘│ └────┘  ├──      ← GRD. FLOOR LEVEL ▽ +0.000
══════╪═════════╪═════════╪═════════╪══      ← thick ground line (0.50mm)
 ─────┼─────────┼─────────┼─────────┼──      ← APRON LEVEL (150mm below FFL)
──────┴─────────┴─────────┴─────────┴──      ← GRD. LEVEL ▽ -0.150
```

**Level markers (professional standard):**
```
Symbol: equilateral triangle pointing DOWN toward the level line
Triangle dimensions: base = 4mm × scale, height = 3mm × scale
Tip at: (marker_x, level_z × 1000) — the exact elevation
Dashed line: full width of drawing area at level_z × 1000
  from (bld_min_x - 5mm × scale) to (bld_max_x + 5mm × scale)
  layer=A-GRID, linetype=HIDDEN, lw=0.18mm
Label: "GRD. FLOOR LEVEL  +0.000" — right of triangle, 2.5mm text
  offset from marker_x: template.level_markers.text_right_offset_m × 1000

Four mandatory levels for single-storey (from DB):
  GRD. LEVEL:         -0.150m (150mm below FFL — apron drain level)
  APRON LEVEL:        -0.000m (apron / path finish level, JKR convention 150mm)
  GRD. FLOOR LEVEL:   +0.000m (FFl, storey_elevation from spatial_structure)
  BEAM/CEILING LEVEL: detect from IfcBeam maxZ or storey height (typically +2.3 to +3.0m)
```

**Height dimension (professional standard):**
```
Height dim between consecutive levels:
  dim line: vertical, at x = marker_x - 10mm × scale (left of building)
  two extension lines: horizontal at each level, from bld_min_x to dim_x
  tick marks: 45° at each end
  text: "3000" centered on dim line, rotated 90° or horizontal beside it
  (Malaysian practice: horizontal text beside dim line, not rotated)
```

**Log proof:**
```
§LEVELS detected: GRD=-150mm APRON=0mm FFL=0mm CLG=2300mm (from DB)
§RENDER TRIANGLE layer=A-GRID src=level:FFL tip=(-7735,0) base_w=4000 h=3000
§RENDER LINE layer=A-GRID src=level:FFL dashed full_width from=-8235 to=6770 y=0
§RENDER TEXT layer=A-ANNO-TEXT src=2d_level_marker:FFL val="GRD. FLOOR LEVEL  +0.000" at (-6935,200)
§DIM HEIGHT FFL→CLG: 0→2300mm = 2300mm
§RENDER LINE layer=A-ANNO-DIMS src=dim:FFL-CLG vertical x=-8735 from=0 to=2300
§RENDER TEXT layer=A-ANNO-DIMS src=dim:FFL-CLG val="2300" at (-9235,1150) h=250
§AUDIT LEVELS: 4 triangle markers on A-GRID → PASS
§AUDIT LEVEL_LABELS: "GRD. FLOOR LEVEL", "BEAM/CEILING LEVEL" found → PASS
```

---

### 19.7 Roof Plan

**What it must look like:**

```
        (A)      (B)      (C)      (D)
    700↓  ╔══════════════════════╗ ↓700     ← eave overhang dim (all sides)
         ║╲                    /║
         ║ ╲                  / ║
         ║  ╲                /  ║            ← ridge line (dashed)
         ║   ╲              /   ║
         ║    ╲────────────/    ║
         ║     slope arrows ↓  ║            ← one per bay
    700→ ╚══════════════════════╝ ←700
```

**Ridge line:** dashed, linetype HIDDEN or DASHED.
Detected as the line connecting the highest Z points of roof elements.
For gable: one horizontal ridge line at mid-span.
For hip: four ridge lines meeting at centre point.

**Slope arrows (professional standard):**
```
One arrow per bay. Positioned at bay centre.
Arrow: thin line (0.18mm) pointing DOWNSLOPE (toward eave)
Length: 3mm × scale
Arrowhead: small filled triangle at the downslope end (exception to no-arrowhead rule:
  slope arrows ARE arrowheads — they indicate direction of water run-off)
Label: "1:X" slope ratio beside arrow (if slope ratio available from DB; else omit)
```

**Eave overhang dim (professional standard):**
```
For each side with overhang > 50mm:
  dim line parallel to building face, outside eave line
  value = eave_edge_to_wall_face_mm (not eave_to_grid)
  tick terminators (standard dim style)
  value logged: §DIM OVERHANG N=700mm S=700mm E=0mm W=700mm
```

**Log proof:**
```
§RIDGE detected: type=gable ridge_y=0.0m (mid-span) at z=4500mm
§RENDER LINE layer=A-ROOF src=ridge dashed from=(-7735,0) to=(6270,0) y=0
§RENDER ARROW layer=A-ROOF src=slope:bay_AB at (-5285,-500) dir=S len=3000
§RENDER TEXT layer=A-ANNO-DIMS src=dim:overhang-N val="700" at (0,{y})
§AUDIT RIDGE: 1 dashed line on A-ROOF → PASS
§AUDIT SLOPE_ARROWS: 3 arrows (= n_bays=3) on A-ROOF → PASS
§AUDIT OVERHANG_DIMS: 3 sides (N,S,W; E=0→omitted) → PASS
```

---

### 19.8 MEP Electrical Plan

**What it must look like (from TBKLTN E-01):**

```
  Symbols (paper size: symbol circle ≈ 5mm dia):

  Ceiling light:   ⊕  circle + cross (4 lines at 90°)
  Ceiling fan:     ⊛  circle + asterisk (fan.png or 4 arcs at 45°)
  Switch 1-gang:   ─⌒  line + arc (open semicircle)
  Switch 2-gang:   ─⌒⌒ line + double arc
  Switch 3-gang:   ─⌒⌒⌒
  Power outlet:    ⊐─  D-shape + line (socket symbol)
  Dist. board:     ▼   filled triangle (large, 8mm paper)
  Elec. meter:     ⓜ   circle with M
  Wall light:      ◑   half-circle at wall face

  Wiring:          ─────────────  solid thin line (0.13mm) connecting fixtures to DB
```

**Symbol construction (DXF geometry):**
```python
r = 2.5 * scale  # 2.5mm paper radius

CEILING_LIGHT: circle(r) + line(-r,0)(+r,0) + line(0,-r)(0,+r)
CEILING_FAN:   circle(r) + [if fan.png: insert image else: 4 lines at 45° intervals]
SWITCH_1:      line(-r,0)(0,0) + arc centre=(0,0) r=r from=0° to=180° (open top)
POWER_OUTLET:  arc centre=(0,0) r=r from=-90° to=90° + line(r,0)(r+r*0.5,0)
DIST_BOARD:    filled triangle: pts=[(0,+r*1.5),(-r,−r),(+r,−r)] layer=A-ELEC-POWR
WALL_LIGHT:    arc at wall face, half-circle into room
```

**Wiring logic:**
```
For each room: collect all electrical terminals in that room.
Connect each terminal to the nearest switch in same room with a thin line.
Connect each switch to the distribution board (triangle symbol).
Route: straight lines (no bend routing in first implementation).
Layer: A-ELEC-POWR, lw = 0.13mm.
```

**Circuit grouping (future):** Identify circuits by DB element grouping or spatial proximity.

**Log proof:**
```
§MEP ELECTRICAL: 9 ceiling lights, 5 fans, 4 switches, 8 outlets, 1 dist_board
§RENDER SYMBOL layer=A-MEP-ELEC type=CEILING_LIGHT src=guid:EL001 at (cx,cy) r=250
§RENDER SYMBOL layer=A-MEP-ELEC type=CEILING_FAN src=fan.png at (cx,cy) size=500x500
§RENDER LINE layer=A-ELEC-POWR src=wiring dist=3.5m from=(cx1,cy1) to=(cx2,cy2)
§MEP WIRING: 14 run lines drawn (9 lights + 5 fans → nearest switch → dist_board)
§AUDIT MEP_ELEC: 9 CEILING_LIGHT + 5 FAN + 4 SWITCH + 8 OUTLET + 1 DIST_BOARD → PASS
§MEP LEGEND: 5 types, 27 elements total
```

---

### 19.9 MEP Plumbing Plan

**What it must look like (from TBKLTN page 1 legend):**

```
  Fixtures (paper size: symbol ≈ 5mm):

  WC (Water Closet):    □─  rectangle + connector line
  Basin:                ○   oval
  Sink:                 [─]  rectangle with drain
  Shower:               ╳   X in circle
  Bath:                 [──]  elongated rectangle
  Floor trap:           ⊟   square with X
  Tap:                  ─╮  line + arc

  Pipe runs:
    Soil pipe:          ─ ─ ─  dashed (lw 0.35mm)
    Waste pipe:         ─────  solid (lw 0.25mm)
```

**WC symbol (professional standard):**
```
Rectangle: 500mm × 350mm (typical WC footprint), centred at element centroid
Bowl: ellipse inside rectangle (rear half)
Tank: smaller rectangle at rear (wall side)
All at lw = 0.18mm, layer = A-MEP-PLMB
Orientation: aligned with element bounding box long axis
```

**Pipe run symbol:**
```
IfcFlowSegment: draw along long axis of bbox
  Horizontal (maxX-minX > maxY-minY): line (minX,midY)→(maxX,midY)
  Vertical: line (midX,minY)→(midX,maxY)
  Soil (from toilet): DASHED, lw=35
  Waste (from sink/basin): solid, lw=25
  Classification: soil = element_name contains 'soil' or connected to WC
```

**Log proof:**
```
§MEP PLUMBING: 4 WC, 2 sink, 2 shower, 2 basin, 1 bath (DB terminals)
§MEP SEGMENTS: 427 IfcFlowSegment → {n} horizontal + {m} vertical drawn
§RENDER SYMBOL layer=A-MEP-PLMB type=WC src=guid:P001 at (cx,cy) box=500x350 orient=NS
§RENDER LINE layer=A-MEP-PLMB src=guid:S001 type=soil from=(x1,y1) to=(x2,y2) lw=35 DASHED
§AUDIT MEP_PLMB: 4 WC + 2 SINK + 2 SHOWER + 2 BASIN → PASS
§AUDIT MEP_SEGMENTS: {n} lines on A-MEP-PLMB → PASS (expected > 0)
```

---

### 19.10 North Arrow

**What it must look like:**

```
      ▲
     /│\
    / │ \        ← right half filled black, left half white
   /  │  \
  /   │   \
 /    N    \
└───────────┘   (no base — just the arrow head)
```

**Professional geometry:**
```
Arrow is a composite symbol: split diamond (left=white, right=black)
Centre at placement point (top-right of drawing area, inside border)
Size: north_arrow.size_mm × scale (default 10mm)

Black (right) half: polygon [(0,0),(size*0.3,0),(0,size),(0,0)]
White (left) half:  polygon [(0,0),(-size*0.3,0),(0,size),(0,0)]
"N" label: centred at (0, -size*0.4), h = size*0.4

Layer: A-ANNO (inserted as block in DXF: INSERT entity referencing BLOCK "NORTH_ARROW")
```

**Log proof:**
```
§RENDER NORTH_ARROW at ({x:.1f},{y:.1f}) size={sz}mm layer=A-ANNO
§AUDIT NORTH_ARROW: 1 INSERT on A-ANNO (plan views only) → PASS
```

---

### 19.11 Scale Bar

**What it must look like:**

```
├──┼──┼──┼──┼──┤   ← alternating filled/empty segments
0  1  2  3  4  5m
   scale 1:100
```

**Professional geometry:**
```
Total length: 5m at current scale → 5m / scale × 1000 = 50mm paper at 1:100
Segments: 5 × 10mm paper each (alternating filled/empty)
Height: 2mm paper
Alternating fill: SOLID hatch, segments 0,2,4 = black; 1,3 = white
Tick marks: vertical line at each segment boundary, height = 3mm
Labels: "0", "1", "2", "3", "4", "5m" below each tick mark (2.0mm text)
  Note: "5m" appended with unit; others are bare numbers
Text baseline: 1mm below tick mark bottom
"scale 1:{scale}" centred below bar (2.5mm, ACI 8 grey)
Placement: bottom-left of drawing area, 10mm from left border, 5mm from bottom border
```

**Current bug pattern (seen in 1201 stale files):**
Scale bar labels "0","1","2","5m" overlap → caused by `sb_scale_factor = 1000/scale`
instead of `MM`. Fix already applied in 2116 generation. Log must confirm separation:

**Log proof:**
```
§SCALE_BAR: total=50.0mm paper segments=5 each=10.0mm at x0={x:.1f} y0={y:.1f}
§RENDER HATCH layer=A-ANNO src=scale_bar seg=0 fill=black x=({x0},{x0+10})
§RENDER HATCH layer=A-ANNO src=scale_bar seg=1 fill=white x=({x1},{x1+10})
§RENDER TEXT layer=A-ANNO-DIMS src=scale_bar val="0" at ({x0},{y_lbl:.1f}) sep={sep:.1f}mm
§RENDER TEXT layer=A-ANNO-DIMS src=scale_bar val="1" at ({x1},{y_lbl:.1f}) sep={sep:.1f}mm
§RENDER TEXT layer=A-ANNO-DIMS src=scale_bar val="5m" at ({x5},{y_lbl:.1f}) sep={sep:.1f}mm
§AUDIT SCALE_BAR: 5 segments, label "5m" found, min_sep={sep:.1f}mm (expected >5mm) → PASS
```

`min_sep` is minimum gap between adjacent label bboxes. Must be > 5mm paper. If < 0: overlap.

---

### 19.12 Room Labels

**What it must look like:**

```
┌─────────────────────┐
│                     │
│      BEDROOM        │  ← room name: 3.5mm, centred in room
│       12.3 m²       │  ← area: 2.5mm, below name, greyed
│                     │
└─────────────────────┘
```

**Room detection from DB:**
```
1. `infer_rooms()` clusters furniture by type → room centroid
2. Look up display_text from `2d_room_label WHERE room_type LIKE '%{type}%'`
3. Area = convex hull of furniture cluster bounding boxes (approximate)
4. If two rooms overlap by > 50% of smaller room → merge, use primary room label
5. If same label at two centroids within 2m (duplex mirror) → keep only first storey's
```

**Collision detection (professional requirement):**
```
After all labels placed: compute text bboxes
For any overlapping pair:
  Option A: offset one label by half room height (vertical push)
  Option B: if room too small for label, place leader line to margin
  Option C: suppress if area < min_room_area_m2 (template key)
Min room area threshold: room_labels.min_room_area_m2 (default 1.5)
```

**Log proof:**
```
§ROOM BEDROOM: centroid=(-4.1,-0.5) area=12.3m² src=2d_room_label:BEDROOM
§ROOM LIVING ROOM: centroid=(-1.2,-0.5) area=24.1m² src=2d_room_label:LIVING_AREA
§ROOM COLLISION: BEDROOM↔LIVING ROOM overlap=2.1mm → shift BEDROOM by +3.5mm
§RENDER TEXT layer=A-ANNO-TEXT src=room:BEDROOM val="BEDROOM" at (-4.1,0.8) h=350
§RENDER TEXT layer=A-ANNO-TEXT src=room:BEDROOM val="12.3 m²" at (-4.1,0.0) h=250
§AUDIT ROOMS: 3 room labels placed, 0 collisions after adjustment → PASS
```

---

### 19.13 Door and Window Tags

**What it must look like (TB-LKTN):**

```
Door tag:   circle with bold number    D1  D2  D3...
Window tag: hexagon with W code        W1  W2  W3...
Placed adjacent to element, outside room (toward nearest wall)
Connected with short leader line when space is tight
```

**Tag numbering:**
```
Doors: D1, D2... (sequential per floor plan, left-to-right top-to-bottom)
Windows: W1, W2... (same order)
Multi-storey: suffix storey indicator or use separate sequence per storey
  (to avoid W1 Level1 ↔ W15 Level2 overlap — §F1 fix)
Storey prefix option: Level 1 = W1..W14, Level 2 = W1..W14 (suppress Level 2 on A-01)
```

**Tag geometry:**
```
Tag shape from template annotation_tags.shape:
  circle: centre + bold number text, r = annotation_tags.size_mm × scale / 2
  hexagon: 6-sided polygon, inscribed circle r = size_mm × scale / 2

Leader line: from tag centre to element centroid
  Length: clipped to fit between tag edge and element face
  Only drawn if distance > tag_size × 2

Layer: A-ANNO-TEXT, lw = 0.18mm
```

**Log proof:**
```
§TAG D1: door guid:D001 at (x,y) tag_centre=({tx},{ty}) storey=Level 1
§TAG W1: window guid:W001 at (x,y) shape=hexagon storey=Level 1
§TAG SUPPRESS: W15 storey=Level 2 suppressed (current sheet=Level 1, §F1 fix)
§RENDER TEXT layer=A-ANNO-TEXT src=guid:D001 val="D1" at ({tx},{ty}) shape=circle r=200
§AUDIT TAGS: 3 D-tags, 4 W-tags, 0 duplicates on current storey → PASS
```

---

### 19.14 Log File Structure

Every `write_*` function produces a per-view log. Structure:

```
=== LOG: {PREFIX}_{VIEW}_{ts}.txt ===
Generated: {datetime}
Function:  write_floor_plan_dxf
DB:        input/SH_extracted.db

--- §ENTRY ---
§ENTRY write_floor_plan_dxf(db_path='input/SH_extracted.db', out_dxf='output/DXF/SH_FLOOR_xxx.dxf', scale=100)

--- §VALUE (layout constants) ---
§VALUE paper_w=420.0mm (template.paper.width_mm)
§VALUE paper_h=297.0mm (template.paper.height_mm)
§VALUE margin_l=25.0mm margin_r=10.0mm (template.paper.margins)
§VALUE tb_width=75.0mm (template.paper.title_block_width_mm)
§VALUE drawing_area_w=305.0mm drawing_area_h=277.0mm (computed)
§VALUE scale=100 (template or arg)
§VALUE grid_ext=20.0mm bubble_r=4.0mm (template.grid)
§VALUE tier1=12.0mm tier2=6.0mm ext_gap=2.0mm (template.dimensions)
§VALUE crowding_threshold=15.0mm (template.grid.crowding_threshold_mm)
§VALUE cut_z=1.2m (drafting convention §5.1a)

--- §QUERY (DB reads) ---
§QUERY elements_meta+elements_rtree: 55 rows (11 walls, 3 doors, 4 windows, 14 furniture, 23 other)
  sample[0]: IfcWall guid=1A2B3C element_name='Basic Wall:Generic 200mm' bbox=(-7.735,-1.320,-7.535,4.555)
§QUERY 2d_level_marker: 9 rows loaded
  sample[0]: code=FFL display_text='GRD. FLOOR LEVEL' typical_z=0.0

--- §SECTION ---
§SECTION cut_z=1.2m: 32 CUT, 15 BELOW, 8 ABOVE

--- §GRID ---
§GRID X: 4 axes (A,B,C,D) from 11 structural elements
§GRID Y: 3 axes (1,2,3)
§TRIAGE X: A-B=4900mm (49.0mm@1:100) → INLINE
§TRIAGE X: B-C=4455mm (44.6mm@1:100) → INLINE
...
§TRIAGE PANEL: 0 bays listed → ALL DIMS SHOWN IN DRAWING

--- §RENDER (entities written) ---
§RENDER LWPOLYLINE layer=A-WALL-FULL src=guid:1A2B3C pts=4 lw=50
§RENDER HATCH layer=A-WALL-PATT src=guid:1A2B3C fill=solid
§RENDER ARC layer=A-DOOR src=guid:D001 hinge=(-5.2,-1.3) r=900 span=90°
...
(one line per entity — truncated in this spec for brevity)

--- §AUDIT ---
§AUDIT BORDER: 28 entities on A-TTLB → PASS
§AUDIT GRID_LINES: 7 on A-GRID → PASS
§AUDIT LANGUAGE: 0 Malay strings → PASS
§AUDIT ARROWHEADS: 0 ARROW entities outside A-ROOF layer → PASS  (slope arrows on A-ROOF exempted)
§AUDIT TRIAGE_PANEL: 0 bays listed = triage PANEL count 0 → PASS
...

--- §EXIT ---
§EXIT write_floor_plan_dxf: 247 entities written to SH_FLOOR_xxx.dxf
  walls=32 grids=7 dims=7 rooms=3 tags=7 title_block=38 border=4 north=3 scalebar=12
=== END ===
```

**Rule:** if a check is FAIL, the log MUST include the exact values that failed.
`§AUDIT FILL: fill_x=8% < 10% → FAIL (building too small for scale — try scale=50)`
A coder reads one line and knows exactly what to change.


---

## 20. Reverse Path: DXF as Semantic Document

### 20.1 Design Principle

> **The DXF is not just a drawing. It is a semantic document.**

Every entity in the DXF carries xdata that maps it back to:
1. Its IFC source element (GUID)
2. Its intent parameter in the Unified Formula `B = f(Ω, Φ, Ψ, Λ, J)`
3. Its current value in model coordinates

This mirrors the XML principle: every element carries its own attributes.
You can pick up any DXF file, parse its xdata, and reconstruct the full
relationship graph — no sidecar files, no separate database.

```
Traditional DXF:    geometry only — lines, arcs, text
Semantic DXF:       geometry + intent — lines with BIMGUID + BIMSRC attributes
```

This enables the closed loop: a grid moved in the DXF → parse xdata → know
exactly which OrderLine parameters to update → recompile → new DXF.

The round-trip is consistent with Rosetta Stone Truth (§docs/unified_mathematical_formulation.txt):
geometry is always `f(intent)`. The DXF edit does NOT store new geometry as
source — it is translated into an intent change (Ω update), which then
recompiles to produce new geometry.

```
2D DXF edit
    ↓
xdata.parse() → {source_guid, intent_param, old_value, new_value}
    ↓
Ω_new = Ω.update(source_guid, intent_param, new_value)
    ↓
C(Ω_new, Φ, Ψ, Λ, J) → output.db  [geometry still ephemeral, f(intent)]
    ↓
new DXF [geometry regenerated from updated intent]
```

### 20.2 Two Application IDs

```
BIMGUID  (existing) — primary entity identifier
           Wall/door/window: actual IFC GUID (e.g. '1A2B3C...')
           Grid line:        synthetic 'GRID:A'
           Room label:       synthetic 'ROOM:BEDROOM'
           Dim entity:       synthetic 'DIM:X:A-B:tier2'
           Scale bar:        synthetic 'SCALEBAR'
           North arrow:      synthetic 'NORTHARROW'

BIMSRC   (new) — full semantic payload for reverse mapping
           Key=value strings (group code 1000)
           Floats for coordinates (group code 1040)
           Integers for counts (group code 1070)
```

Register both app IDs at document creation:
```python
doc.appids.new('BIMGUID')   # existing
doc.appids.new('BIMSRC')    # new — §20
```

### 20.3 xdata Schema by Entity Type

#### 20.3.1 Wall / Column / Door / Window (IFC elements)

```
BIMGUID: [(1000, '{ifc_guid}')]

BIMSRC:  [(1000, 'ifc_class:{IfcWall}'),
          (1000, 'element_name:{Basic Wall:Generic 200mm}'),
          (1000, 'storey:{Level 1}'),
          (1040, minX), (1040, minY), (1040, maxX), (1040, maxY),   ← model coords, metres
          (1000, 'intent_param:tack_x'),
          (1040, tack_x_value),
          (1000, 'intent_param:tack_y'),
          (1040, tack_y_value),
          (1000, 'intent_param:width'),
          (1040, width_value)]
```

Log: `§XDATA guid:{g} BIMSRC written: ifc_class={cls} tack=({x:.3f},{y:.3f})`

#### 20.3.2 Grid Line + Bubble

This is the most critical entity for the closed loop. The grid label `GRID:A`
is synthetic — it does NOT trace to a single IFC element. The `BIMSRC` payload
must record WHICH IFC elements (walls, columns) contributed to this axis.

```
BIMGUID: [(1000, 'GRID:{label}')]   ← e.g. 'GRID:A'

BIMSRC:  [(1000, 'type:GRID'),
          (1000, 'label:{A}'),
          (1000, 'axis:{x}'),                         ← 'x' or 'y'
          (1040, position_m),                         ← axis position in metres
          (1000, 'source_guids:{g1},{g2},{g3}'),      ← comma-separated IFC GUIDs
          (1000, 'intent_param:position_{x}'),        ← which param a grid edit changes
          (1070, tier),                               ← 1=structural, 2=derived
          (1000, 'crowding:{INLINE|PANEL}')]          ← from §15 triage result
```

**`source_guids`** — the walls and columns whose centreline defines this axis.
Populated by extending `GridAxis` namedtuple in `derive_grids()`:
```python
GridAxis = namedtuple('GridAxis', ['label','axis','position','source_guids'])
```
`derive_grids()` already collects the contributing elements — it just discards
them after returning. Keep them.

Log: `§XDATA GRID:A BIMSRC written: axis=x pos=-7.735m src=[guid1,guid2] crowding=INLINE`

#### 20.3.3 Dimension Entity (per span)

```
BIMGUID: [(1000, 'DIM:{axis}:{from}-{to}:tier{n}')]

BIMSRC:  [(1000, 'type:DIM'),
          (1000, 'axis:{x}'),
          (1000, 'from_grid:{A}'),
          (1000, 'to_grid:{B}'),
          (1070, tier),                 ← 1=overall, 2=bay, 3=opening
          (1040, dist_m),              ← distance in metres
          (1070, dist_mm),             ← rounded mm value (what is printed)
          (1000, 'layout:{INLINE|PANEL}')]
```

Log: `§XDATA DIM:X:A-B:tier2 BIMSRC written: dist=4.900m (4900mm) INLINE`

#### 20.3.4 Room Label

```
BIMGUID: [(1000, 'ROOM:{room_type}')]

BIMSRC:  [(1000, 'type:ROOM'),
          (1000, 'room_type:{BEDROOM}'),
          (1040, centroid_x),
          (1040, centroid_y),
          (1040, area_m2),
          (1000, 'source_guids:{f1},{f2},{f3}'),     ← furniture GUIDs defining room
          (1000, 'storey:{Level 1}'),
          (1000, 'label_src:2d_room_label:{pk}')]    ← DB row that provided display text
```

#### 20.3.5 Door / Window Tag

```
BIMGUID: [(1000, '{tag_code}')]     ← e.g. 'D1', 'W3'

BIMSRC:  [(1000, 'type:TAG'),
          (1000, 'tag_code:{D1}'),
          (1000, 'element_guid:{ifc_guid}'),
          (1000, 'element_class:{IfcDoor}'),
          (1000, 'storey:{Level 1}'),
          (1000, 'shape:{circle|hexagon}')]
```

#### 20.3.6 MEP Terminal

```
BIMGUID: [(1000, '{ifc_guid}')]

BIMSRC:  [(1000, 'type:MEP_TERMINAL'),
          (1000, 'ifc_class:IfcFlowTerminal'),
          (1000, 'element_name:{Ceiling Light:Standard}'),
          (1000, 'discipline:{ELECTRICAL}'),
          (1000, 'symbol_type:{CEILING_LIGHT}'),
          (1040, centroid_x),
          (1040, centroid_y),
          (1000, 'storey:{Level 1}')]
```

#### 20.3.7 MEP Segment (pipe / wire run)

```
BIMGUID: [(1000, '{ifc_guid}')]

BIMSRC:  [(1000, 'type:MEP_SEGMENT'),
          (1000, 'ifc_class:IfcFlowSegment'),
          (1000, 'element_name:{Soil Pipe:100mm}'),
          (1000, 'discipline:{PLUMBING}'),
          (1000, 'pipe_type:{SOIL|WASTE|COLD|HOT}'),
          (1000, 'orientation:{H|V}'),               ← horizontal or vertical
          (1040, x1), (1040, y1),                    ← drawn start in metres
          (1040, x2), (1040, y2)]                    ← drawn end in metres
```

#### 20.3.8 Level Marker

```
BIMGUID: [(1000, 'LEVEL:{code}')]   ← e.g. 'LEVEL:FFL'

BIMSRC:  [(1000, 'type:LEVEL'),
          (1000, 'code:{FFL}'),
          (1000, 'display_text:{GRD. FLOOR LEVEL}'),
          (1040, elevation_m),                       ← metres above datum
          (1000, 'src:{2d_level_marker|detect_levels}')]
```

### 20.4 Parsing xdata (Reverse Direction)

A reverse-path parser reads the DXF and reconstructs the intent map:

```python
def parse_semantic_dxf(dxf_path: str) -> dict:
    """
    Read DXF xdata and return entity intent map.
    Returns: {handle: {bimguid, type, intent_params: {param: value}, source_guids: [...]}}
    """
    doc = ezdxf.readfile(dxf_path)
    result = {}
    for entity in doc.modelspace():
        bimguid = _read_xdata_str(entity, 'BIMGUID')
        bimsrc  = _read_xdata_dict(entity, 'BIMSRC')
        if bimguid or bimsrc:
            result[entity.dxf.handle] = {
                'bimguid': bimguid,
                'type':    bimsrc.get('type'),
                'source_guids': bimsrc.get('source_guids','').split(','),
                'intent_param': bimsrc.get('intent_param'),
                'position_m': (bimsrc.get('pos_x'), bimsrc.get('pos_y')),
            }
    return result
```

**A grid edit becomes:**
```python
# User moves Grid A from x=-7.735m to x=-7.900m in DXF
delta_m = -7.900 - (-7.735)   # = -0.165m

entity_map = parse_semantic_dxf('SH_FLOOR_xxx.dxf')
grid_a = [e for e in entity_map.values() if e['bimguid'] == 'GRID:A'][0]
source_guids = grid_a['source_guids']   # ['guid_wall1', 'guid_column1']
param = grid_a['intent_param']          # 'position_x'

# Update intent in DB:
for guid in source_guids:
    db.execute(f"UPDATE elements_rtree SET minX=minX+{delta_m}, maxX=maxX+{delta_m}
                 WHERE id=(SELECT id FROM elements_meta WHERE guid=?)", [guid])
# Then recompile:
# C(Ω_updated, Φ, Ψ, Λ, J) → new output.db → new DXF
```

### 20.5 Implementation in Drawing Writer

#### New app ID registration (once per document)
```python
# In _new_doc():
doc.appids.new('BIMGUID')
doc.appids.new('BIMSRC')   # §20 — add alongside existing BIMGUID
```

#### Helper to write BIMSRC
```python
def _set_bimsrc(entity, **kwargs):
    """
    Write BIMSRC xdata. kwargs become key=value string pairs (1000).
    Float values use group code 1040. Int values use 1070.
    """
    groups = []
    for k, v in kwargs.items():
        if isinstance(v, float):
            groups.append((1000, k))
            groups.append((1040, v))
        elif isinstance(v, int):
            groups.append((1000, k))
            groups.append((1070, v))
        else:
            groups.append((1000, f'{k}:{v}'))
    entity.set_xdata('BIMSRC', groups)
```

#### GridAxis extension
```python
# In derive_grids() — keep source_guids instead of discarding:
GridAxis = namedtuple('GridAxis', ['label', 'axis', 'position', 'source_guids'])
# source_guids: list of IFC GUIDs of walls/columns that contributed this axis
```

### 20.6 Conformity Check

`test_conformity.py` adds check **SEMANTIC_DXF**:

```
[PASS] SEMANTIC_DXF : BIMSRC xdata present on {n} entities
                      Walls={n} Grids={n} Dims={n} Rooms={n} MEP={n}
[PASS] GRID_SOURCES : all GRID:* entities have non-empty source_guids
[FAIL] GRID_SOURCES : GRID:A has empty source_guids → reverse path broken
```

Minimum coverage: every wall, every grid line, every dim entity, every room
label must carry BIMSRC. Scale bar and north arrow may omit BIMSRC (they
have no IFC source and no intent parameter to edit).

### 20.7 Log Events

```
§XDATA BIMSRC wall guid=1A2B3C: ifc_class=IfcWall tack=(-7.735,-1.320) width=0.200m
§XDATA BIMSRC GRID:A: axis=x pos=-7.735m src=[guid1,guid2] crowding=INLINE intent=position_x
§XDATA BIMSRC DIM:X:A-B:tier2: dist=4900mm layout=INLINE
§XDATA BIMSRC ROOM:BEDROOM: centroid=(-4.1,-0.5) area=12.3m² src=[f1,f2,f3]
§XDATA BIMSRC LEVEL:FFL: elev=0.000m src=2d_level_marker
§XDATA BIMSRC MEP guid=E001: type=MEP_TERMINAL discipline=ELECTRICAL symbol=CEILING_LIGHT
§XDATA SUMMARY: {n_wall} walls, {n_grid} grids, {n_dim} dims, {n_room} rooms, {n_mep} MEP entities tagged
```

### 20.8 What This Does NOT Change

- **DXF geometry is unchanged** — xdata is invisible to CAD viewers; drawing looks identical
- **DXF file size** — xdata adds ~50–200 bytes per entity; negligible for typical drawings
- **Existing BIMGUID** — backward compatible; BIMSRC is additive
- **Pipeline** — no new files, no new steps; xdata written during the existing render loop
- **Unified Formula** — geometry remains `f(intent)`; the 2D edit pathway modifies Ω, not stored geometry

### 20.9 Future: BIM Designer Integration

When the BIM Designer (Bonsai/BlenderBIM) gains a DXF import plugin:
1. User opens DXF in viewer (LibreCAD, QCAD, or BIM Designer viewport)
2. User drags grid line
3. Plugin calls `parse_semantic_dxf()` → gets intent delta
4. Plugin calls `update_intent(source_guids, param, new_value)` → updates `_BOM.db`
5. BIM Designer triggers recompile → new `output.db` → new DXF generated
6. DXF reloads in viewer

This closes the loop from 2D paper space back into the 3D BIM model without
storing any coordinates as source — consistent with Rosetta Stone Truth.


### 20.10 Test Specification: 2D → BIM Round-Trip Protocol

#### Test W-2D-BIM-1: BIMSRC Coverage

**Claim:** Every wall, grid, dim, room, and MEP entity in a generated DXF
carries BIMSRC xdata with non-empty type field.

```
Setup:    Generate SH_FLOOR_{ts}.dxf
Action:   parse_semantic_dxf(dxf_path) → entity_map
Assert:
  walls  = [e for e in entity_map if e['type']=='wall']   → count >= 11
  grids  = [e for e in entity_map if e['type']=='GRID']   → count == 7 (SH)
  dims   = [e for e in entity_map if e['type']=='DIM']    → count >= 7
  rooms  = [e for e in entity_map if e['type']=='ROOM']   → count >= 2
  all([e['type'] is not None for e in entity_map.values()]) → True
Log:  [PASS] SEMANTIC_DXF: walls=11 grids=7 dims=7 rooms=3 all-typed → PASS
```

#### Test W-2D-BIM-2: Grid Source GUID Traceability

**Claim:** Every GRID entity's `source_guids` resolves to real elements in the
source DB.

```
Setup:    Generate SH_FLOOR_{ts}.dxf; have SH_extracted.db
Action:   for each GRID entity in parse_semantic_dxf():
            source_guids = entity['source_guids']
            for guid in source_guids:
              rows = db.execute("SELECT id FROM elements_meta WHERE guid=?", [guid])
              assert len(rows) > 0, f"GRID:{label} source guid {guid} not in DB"
Assert:   0 broken source GUID references
Log:  [PASS] GRID_SOURCES: 7 grids, all source_guids resolve in DB
```

#### Test W-2D-BIM-3: Intent Round-Trip (Grid Edit → Recompile → Verify)

**Claim:** Moving a grid 100mm in 2D and propagating to DB produces a
recompiled drawing where that grid is at the new position.

```
Setup:    Generate SH_FLOOR_A.dxf; record Grid B position = -2.835m
Action:
  1. Simulate edit: new_pos = -2.835 + 0.100 = -2.735m
  2. entity_map = parse_semantic_dxf('SH_FLOOR_A.dxf')
  3. grid_b = [e for e in entity_map if e['bimguid']=='GRID:B'][0]
  4. delta = 0.100
  5. for guid in grid_b['source_guids']:
       db.execute("UPDATE elements_rtree SET minX=minX+?, maxX=maxX+? WHERE ...", [delta,delta])
  6. Generate SH_FLOOR_B.dxf from updated DB
  7. entity_map_B = parse_semantic_dxf('SH_FLOOR_B.dxf')
  8. grid_b_new = [e for e in entity_map_B if e['bimguid']=='GRID:B'][0]
Assert:
  abs(grid_b_new['position_m'] - (-2.735)) < 0.001    ← grid moved
  dim_AB_new = [d for d in entity_map_B if d['bimguid']=='DIM:X:A-B:tier2'][0]
  abs(dim_AB_new['dist_m'] - 4.800) < 0.001            ← dim updated (was 4.900)
Log:  [PASS] ROUND_TRIP: Grid B moved 100mm, dim A-B updated from 4900 to 4800mm
```

#### Test W-2D-BIM-4: Non-Destructive Verify (Geometry = f(Intent))

**Claim:** If we restore the original intent (undo the grid move), the DXF
produced is identical to the original.

```
Setup:    DXF_A (original), DXF_B (after grid edit)
Action:
  1. Restore DB: reverse the delta (minX -= 0.100 for same GUIDs)
  2. Generate DXF_C from restored DB
  3. Compare parse_semantic_dxf(DXF_A) vs parse_semantic_dxf(DXF_C)
Assert:
  for each 'GRID' entity: |posC - posA| < 0.001
  for each 'DIM' entity: |distC - distA| < 0.001
Log:  [PASS] NON_DESTRUCTIVE: DXF_C matches DXF_A after undo (geometry = f(intent))
```

**These 4 tests constitute the 2D→BIM integration gate.** They run after
`test_conformity.py` (structural) and `layout_audit.py` (visual). All 4
must pass before the reverse path is declared DONE.

Test file: `python/test_2d_bim_roundtrip.py`


## 21. IFC 2D Annotation Extraction (§21)

> **Status:** SPEC ONLY — not implemented. Findings from DeepSeek analysis +
> HITOS evidence (2,920 IfcAnnotation rows in extracted DB).

### 21.1 Problem

The DXF writer derives all 2D content (grids, dims, rooms) from 3D spatial
data. Professional IFC files already contain the architect's 2D annotations
— grid positions, dimension strings, room labels, hatching — as
`IfcAnnotation` entities with embedded geometry. Currently `extractIFCtoDB.py`
classifies `IfcAnnotation` as `NON_GEOMETRIC_CLASSES` — metadata stored,
geometry skipped.

### 21.2 IFC Schema Chain

```
IfcProject
  └─ IfcGeometricRepresentationContext (3D "Model")
      └─ IfcGeometricRepresentationSubContext
           ContextIdentifier = "Annotation"
           TargetView = PLAN_VIEW | ELEVATION_VIEW | SECTION_VIEW
           TargetScale = 1:100

IfcAnnotation
  └─ Representation → IfcShapeRepresentation
       RepresentationIdentifier = "Annotation"
       Items: IfcGeometricCurveSet | IfcTextLiteral | IfcAnnotationFillArea
  └─ ContainedInStructure → IfcBuildingStorey
  └─ IfcPresentationLayerAssignment (CAD layer name from authoring tool)
```

### 21.3 Extraction Schema (proposed)

```sql
CREATE TABLE annotation_meta (
    id           INTEGER PRIMARY KEY,
    guid         TEXT NOT NULL,
    ifc_class    TEXT NOT NULL,   -- 'IfcAnnotation'
    parent_guid  TEXT,            -- element this annotates (via IfcRelAssociates)
    target_view  TEXT,            -- PLAN_VIEW, ELEVATION_VIEW, SECTION_VIEW
    layer_name   TEXT,            -- from IfcPresentationLayerAssignment
    storey       TEXT,
    ann_type     TEXT             -- 'GRID'|'DIM'|'ROOM_LABEL'|'HATCH'|'TEXT'|'OTHER'
);

CREATE TABLE annotation_geometry (
    id              INTEGER PRIMARY KEY,
    annotation_id   INTEGER REFERENCES annotation_meta(id),
    geom_type       TEXT,    -- 'POLYLINE'|'CIRCLE'|'ARC'|'TEXT'
    points_json     TEXT,    -- [[x,y],[x,y],...] for curves
    text_content    TEXT,    -- for IfcTextLiteral
    insertion_x     REAL,
    insertion_y     REAL,
    rotation        REAL,
    scale           REAL
);
```

### 21.4 Reconciliation Priority

When both derived and annotated data exist for the same entity type:

1. **Grids:** Annotated grid positions are authoritative (architect placed
   them intentionally). Match by nearest position within 0.20m tolerance.
   If matched: use annotated position, keep derived `source_guids` for
   traceability. If unmatched: keep derived grid.
2. **Dimensions:** Annotated dims are authoritative for text values.
   Verify against derived dim for consistency (warn if > 1mm delta).
3. **Room labels:** Annotated labels override `infer_rooms()` output.
4. **Hatching:** Annotated fill areas used directly; no derived equivalent.

```
if has_annotations(db, target_view):
    grids = reconcile_grids(annotated_grids, derived_grids, tol=0.20)
    dims  = annotated_dims  # authoritative
    rooms = annotated_rooms # authoritative
else:
    grids = derive_grids(walls, columns)  # fallback
    dims  = generate_dimensions(grids)
    rooms = infer_rooms(furniture, walls)
```

### 21.5 BIMSRC Extension

BIMSRC xdata gains `source` field to distinguish provenance:
- `source:derived` — computed from 3D spatial data (current)
- `source:annotated` — extracted from IFC IfcAnnotation entity

### 21.6 Evidence

| Building | DB | IfcAnnotation rows | Notes |
|----------|----|--------------------|-------|
| HITOS | HITOS_extracted.db (5.9MB) | 2,920 | Only DB with annotation data found |
| Hospital | Hospital_extracted.db (124MB) | 0 | Geometry-only export |
| SampleHouse | SH_extracted.db (672KB) | 0 | Minimal IFC4 model |
| Duplex | DX_extracted.db (6.2MB) | 0 | Architecture discipline only |

### 21.7 Triage Query

To check any new IFC building for annotation content:
```sql
SELECT count(*) FROM elements_meta WHERE ifc_class = 'IfcAnnotation';
-- If > 0: extraction candidate for §21 pipeline
```


## 22. Interactive Browser Editor (§22)

> **Status:** SPEC ONLY — deferred. Depends on §20 (BIMSRC xdata, DONE)
> and BonsaiBIMDesigner REST API (exists).

### 22.1 Three Output Tiers

| Tier | Format | Purpose | Interactivity |
|------|--------|---------|---------------|
| 1 | DXF | Fabrication deliverable (CNC, laser, print) | None |
| 2 | SVG | Visual proof / verification | View only |
| 3 | **Browser** | Live editing + recompile | Full |

### 22.2 Architecture

```
Browser (JS)                    Server (Java)
┌──────────────────┐           ┌──────────────────┐
│ DXF → SVG/Canvas │           │ DesignerServer   │
│ BIMSRC overlay   │──POST────▶│ CompileRequest   │
│ Drag grid/dim    │  deltas   │ recompile        │
│ Room label edit  │◀──DXF─────│ return new DXF   │
└──────────────────┘           └──────────────────┘
```

**Frontend:** Parse DXF client-side (JS `dxf-parser` or server-side
conversion to SVG). Overlay draggable handles on BIMSRC-tagged entities.
Each handle knows its `type` (GRID/DIM/ROOM), `source_guids`, and
current position from xdata.

**Edit cycle:**
1. User drags grid line A from x=-7.735 to x=-7.500
2. JS collects delta: `{type: 'GRID', label: 'A', axis: 'x', new_pos: -7.500}`
3. POST to `/api/compile` with delta payload
4. Server updates DB positions, recompiles via pipeline
5. Returns fresh DXF → browser re-renders

**Existing infrastructure:**
- `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerServer.java` — REST server
- `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/CompileRequest.java` — compile endpoint
- `BonsaiBIMDesigner/src/main/python/bonsai_bim_designer/client.py` — Python client
- BIMSRC xdata (§20) — entity↔DB mapping already live

### 22.3 Why Browser Wins

- **No install:** works on tablet, phone, client laptop
- **Demo impact:** investor drags a wall, sees dims update live
- **Collaboration:** share URL, not a DXF file
- **Round-trip proof:** the BIMSRC chain (§20) is the same whether
  the edit comes from a browser drag or a Bonsai plugin

### 22.4 Scope Boundary

This section does NOT cover:
- 3D viewport in browser (that's a separate BIMEyes concern)
- Structural analysis feedback (load-bearing wall constraints)
- Multi-user concurrent editing

It covers: 2D floor plan view, grid/dim/room editing, single-user,
compile-on-demand. The simplest useful thing.


## 23. Bonsai Asset Extraction (§23)

> **Status:** SPEC ONLY. Source: Bonsai drawing module at
> `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/drawing/`

### 23.1 Rationale

Bonsai's drawing module contains professional-grade assets (hatch patterns,
tag symbols, annotation type classifications) developed by the IfcOpenShell
community. Extracting these into our DXF writer avoids reinventing geometry
that architects already expect to see.

### 23.2 Hatch Patterns

**Source:** `bim/data/assets/patterns.svg` (71KB, 30+ patterns)

| Pattern | Use case | Priority |
|---------|----------|----------|
| concrete | Wall/slab section cuts | P1 |
| brick | Brick wall sections | P1 |
| insulation | Cavity wall fill | P2 |
| wood | Timber sections | P2 |
| earth | Site/foundation sections | P3 |

**Conversion:** SVG `<pattern>` → ezdxf custom HATCH pattern definition.
Each SVG pattern contains `<line>` or `<path>` elements defining the
repeat tile. Convert to ezdxf format:
`[angle, base_point, offset, dash_length_items]`.

**Integration:** In wall rendering loop, select pattern by `material_name`
from `ElementSection`. Fallback to solid black when material unknown.

### 23.3 Tag Symbols

**Source:** `bim/data/assets/symbols.svg` (18+ symbols)

| Symbol | Our use | Replaces |
|--------|---------|----------|
| door-tag | Door annotation tag | `_draw_tag_shape()` hexagon |
| window-tag | Window annotation tag | `_draw_tag_shape()` diamond |
| section-arrow | Section cut marker | future §5.4 |
| elevation-tag | Elevation reference | future elevation callout |

**Conversion:** SVG `<symbol>` → DXF BLOCK definition in `_new_doc()`.
Insert via `msp.add_blockref()` instead of procedural drawing.

### 23.4 Annotation Type Classification

**Source:** `bim/module/drawing/decoration.py` objecttype class attributes (20 types).
Constant: `drawing_writer_dxf.py` `_BONSAI_ANN_TYPE_MAP`.

Bonsai defines 20 annotation types. Mapping to our §21 `ann_type` column:

| Bonsai type | Our ann_type | Notes |
|-------------|-------------|-------|
| DIMENSION, ANGLE, RADIUS, DIAMETER | DIM | All dimension variants |
| TEXT, TEXT_LEADER | TEXT | Labels and callouts |
| PLAN_LEVEL, SECTION_LEVEL | TEXT | Level markers |
| GRID | GRID | Grid lines |
| FILL_AREA, BATTING | HATCH | Section fill patterns / insulation |
| STAIR_ARROW, BREAKLINE | OTHER | View-specific annotations |
| SYMBOL | OTHER | Generic symbols |
| FALL | OTHER | Slope indicators |
| REVISION_CLOUD | OTHER | QA markup |
| HIDDEN_LINE | OTHER | Hidden edges |
| ELEVATION, SECTION | OTHER | Reference markers |
| MISC, NOTDEFINED | OTHER | Catch-all |
