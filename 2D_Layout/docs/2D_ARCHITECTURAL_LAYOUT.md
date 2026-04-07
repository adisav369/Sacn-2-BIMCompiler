# 2D Architectural Layout — Specification

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
(§4.3) to distinguish it from solid building outlines. The circles at each
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

Step 3: DETECT ALIGNMENT (§4)
        Cluster element face positions → grid axes
        Log each axis: "Grid {label} axis={x|y} pos={position}m source={n} elements at face"

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

### 3.1 Building DB (WHAT + WHERE)

From `elements_meta` + `elements_rtree` in the compiled output DB:
- Element positions (minX/maxX/minY/maxY/minZ/maxZ)
- Element types (ifc_class, element_name)
- Storey containment
- Spatial structure (rooms, storeys)
- Triangle meshes in `base_geometries` (for section cuts)

### 3.2 Template (HOW)

Two template files, user-editable:

**`drawing_template.json`** — layout and formatting values:
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

**`2D.db`** (SQLite, 17 tables with `2d_*` prefix) — label content:
- Room names (bilingual), annotation tags, finish codes, drawing symbols,
  title block field labels, level marker labels, dimension/grid/heading styles,
  sheet templates, drawing types, drawing profiles

### 3.3 Traceability

Every entity in the output must trace to one of:

| Entity | Source |
|--------|--------|
| Wall outline | DB: mesh section cut at storey elevation |
| Door/window opening | DB: element position in host wall |
| Furniture rectangle | DB: bounding box of below-cut element |
| Grid line position | DB: alignment axis from element face clustering (§4) |
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

Every wall that a contractor needs to locate gets a grid line. A single
bedroom partition wall needs a grid — the contractor must measure from
somewhere to build it. The question is not "how many elements share this
position" but "does the contractor need a measurement here?"

```
Step 1: Collect structural element positions
        → IfcColumn: centroid (center_x, center_y)
        → IfcWall (not curtain wall/IfcPlate): centreline
          - N-S wall → x = center_x
          - E-W wall → y = center_y
        → Exclude: furniture, slabs, roofs, annotations, openings

Step 2: Cluster within wall-thickness tolerance (0.20m)
        → Two wall faces 150mm apart = same grid at their midpoint
        → Column centroid within 0.20m of a wall face = same grid
           (column governs the position)

Step 3: Every cluster becomes a grid — no minimum count filter
        → A single partition wall = one grid line
        → A column = one grid line (highest priority position)
        → Building boundary walls always included

Step 4: Label from template (sorted by position)
        → Vertical axes (X positions): A, B, C, D...
          from template grid.vertical_axis_labels
        → Horizontal axes (Y positions): 1, 2, 3...
          from template grid.horizontal_axis_labels
```

Door and window positions are NOT gridded on the floor plan — they clutter
the main drawing. Openings are dimensioned on a dedicated **Opening Schedule**
page (§5.5) per TB-LKTN practice.

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

Each view = one cut plane + one bundle of elements.

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
| Roof outline | DB: roof slab bounding box |
| Ridge line | DB: maxZ line of roof slabs (dashed, style from template `line_styles.ridge_line`) |
| Slope arrows | DB: evenly spaced along each slope, direction from ridge toward eave, count = one per grid bay |
| Eave overhang | DB: roof edge - wall face (dimension value) |
| Labels | 2D.db: bilingual (RABUNG/RIDGE, CUCURAN/EAVE) |

### 5.4 Section

Vertical cut through building. **GAP — not yet implemented.**

### 5.5 Opening Schedule (JADUAL PINTU & TINGKAP)

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
- TAJUK LUKISAN value: set per drawing type (PELAN LANTAI, PANDANGAN HADAPAN, etc.)

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

### 9.1 Done (archive reference, do not modify)

The template and drawing style follow TB-LKTN (JKR Malaysian standard for
RUMAH RAKYAT housing). SampleHouse SVG outputs in `archive/` match this
standard. They are the proven reference:
- Floor plan: walls, grids, dimensions, room labels, tags, title block, north arrow
- Roof plan: ridge, slope arrows, eave labels, overhang dimension
- 4 elevations: level markers, height dimensions, bay dimensions, roof silhouette

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
| Alignment detection: generalized face clustering | §4.2 (current code is wall-only) |
| Dimension tier 3: opening widths | §6.1 |
| Section view | §5.4 |
| TB-LKTN additional pages: electrical plan, reflected ceiling plan | 2D.db `[2d_drawing_type]` defines 8 types, only 3 implemented |

## 10. Tests

### 10.0 Test Protocol — DXF Proof

**R1 — DXF is the deliverable.** The DXF file is what the contractor opens
in CAD software. It is the single source of truth.

**R2 — PNG is the proof.** After generating the DXF, the `--proof` flag
reads the DXF back, transforms entities to paper-scale coordinates, and
renders a PNG image. The PNG proves the DXF content is correct — what you
see in the PNG is exactly what is in the DXF. No SVG intermediate step.

**R3 — The SVG writer is the reference, not the proof.**
`drawing_writer.py` produced the archive SVGs (frozen, do not regenerate).
It is the reference for what features the DXF must contain. But the SVG
writer must NOT be used as proof output — that tests the SVG writer, not
the DXF writer.

**R4 — Visual verification before pass.** Automated tests check entity
counts and types. But they cannot judge visual quality. The tester MUST
open the proof PNG and compare against the archive before declaring pass.

**R5 — White background, black strokes.** The proof PNG must render on
white background (paper convention). All building outlines in black.
Grid lines in grey. Glass in blue. No transparent backgrounds.

**R6 — Proof must show all content.** The proof renderer reads DXF
entity coordinates, divides by the drawing scale (1:100 → ÷100), and
renders at paper-scale mm — the same coordinate system as the archive
SVGs. This ensures walls, text, dimensions, and grids are all visible
and proportional. The building should fill ~60-80% of the proof image.

```
python3 drawing_writer_dxf.py <db> --all --proof

# Outputs per view in python/output/:
#   *_floor_plan.dxf        — professional deliverable (CAD software)
#   *_floor_plan_proof.png  — PNG proof for visual review
#   *_front_elevation.dxf   — elevation deliverable
#   *_front_elevation_proof.png — elevation proof
#   ... (all views generated together)

# The --all flag generates floor plan + 4 elevations + proofs
# in a single run. No separate commands needed.
```

Output in `python/output/`:
- `*.dxf` — professional deliverables (one per view)
- `*_proof.png` — PNG proofs for visual review (one per view)
- `archive/*.svg` — frozen reference (do not regenerate, do not touch)

### 10.1 `test_no_invention.py`

Checks that every DXF entity traces to DB or template:
- No unknown layers
- All text traces to template field, DB value, or dimension arithmetic
- Wall polylines have GUID xdata (DB traceability)
- Line weights match template values

### 10.2 `test_no_hardcode.py`

Scans drawing writer code for hardcoded numeric literals (>= 100) in drawing
functions. Any value not from the template is a violation.

### 10.3 `test_dxf_vs_svg.py`

Checks DXF output has all features present in the archive SVG reference:
- Wall sections, grid lines, circles, labels
- Dimensions present and consistent
- Room labels, area values
- Door/window tags
- Sheet border, title block panel
- North arrow, white background

### 10.4 TBLKLTN Completeness Audit

The DXF writer runs a post-render audit against `2D.db` (the TB-LKTN reference
database). For each view it logs `[HAVE]` or `[MISS]` for every professional
drawing feature, with actual counts vs expected counts from the database.

Output: `python/output/dxf_diagnostic.txt` — the forensic trail.

Features checked (floor plan): wall sections, grid lines, grid bubbles, grid
labels, GUID xdata, furniture, bay dimensions, room labels, room areas, door
swing arcs, wall fill/hatch, door/window tags, north arrow, drawing title,
title block fields, scale bar.

Features checked (elevation): element outlines, grid lines, grid bubbles, grid
labels, level markers, level labels, bay dimensions, ground line, window
louvres, roof silhouette, drawing title, title block.

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
| `archive/` | Reference SVG outputs (proven, do not modify) |
