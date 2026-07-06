# DONE
# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: 2D Drawing Engine — Phase A hardening: R5 hardcodes, triangle levels, section markers, finish codes, elevation tags, roof profile
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: DONE (2026-04-19)

## Context

TB-LKTN parity gap analysis: `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §24.
Reference: `2D_Layout/input/TBKLTN_House.pdf` (8 sheets, WD-1/01, RUMAH RAKYAT).

### Already working (verified 2026-04-18)
- Floor plan: walls (section cut), doors (swing arcs ✓), windows (double-line ✓),
  room labels + area, D/W tags, grid bubbles both ends, dimension chains, north arrow,
  scale bar, title block + field rows, door/window schedule panel, grid reference panel
- Elevations: wall/door/window outlines, grid bubbles, dimensions, level marker text,
  roof silhouette (envelope for curved, hull for flat), ground/FFL lines
- Roof plan: mesh hull outline
- MEP: plumbing stub

### Still missing — this session's scope
Focus on what TB-LKTN has that we don't. Every fix must be **abstract** — no
building-specific code. The SH archive reference is the "Best Reference Sample"
and its door arcs, bubbles, lettering are correct. Extend that quality to all views.

## Task 1 — Eliminate R5 hardcoded values (I-43)

Six values are hardcoded in `drawing_writer_dxf.py`. Move each to `drawing_template.json`.

| Line | Current hardcode | Template key to add |
|------|-----------------|---------------------|
| ~405 | `_lw_sym = int(0.25 * 100)` | `blocks.stroke_mm: 0.25` |
| ~700 | `lw_tb_med = int(0.35 * 100)` | `title_block.section_break_mm: 0.35` |
| ~701 | `lw_tb_bold = int(0.50 * 100)` | `title_block.outer_frame_mm: 0.50` |
| ~879 | `tb_w * 0.18` | `title_block.internal_ratio: 0.18` |
| ~1240 | `below_grd / total_v > 0.25` | `elevation.ground_threshold_ratio: 0.25` |
| ~1718 | `layer_weights.get(layer, 0.18)` | `line_weights.fallback: 0.18` |

**Steps:**
1. Add keys to `drawing_template.json` under appropriate sections
2. Replace each hardcode with `tpl.get(...)` read
3. Log each read: `§VALUE key=blocks.stroke_mm value=0.25 src=template`
4. Run SH + DX — output must be IDENTICAL (values unchanged, source changed)

## Task 2 — Triangle level markers on elevations (I-44)

TB-LKTN pages 4-5: level markers use ▽ (inverted triangle) for floor levels and
△ (upright triangle) for ground datum. Our engine draws text-only labels.

**Template keys to add in `level_markers` section:**
```json
"floor_symbol": "triangle_down",
"ground_symbol": "triangle_up",
"symbol_size_mm": 3.0,
"symbol_line_weight_mm": 0.18
```

**Implementation in `write_elevation_dxf()` level marker block:**
- Before the label text, draw a SOLID triangle (3 vertices) at the marker position
- ▽ (down) for levels at or above FFL: FFL, SILL, HEAD, CLG, BEAM/CEILING, RIDGE, EAVE
- △ (up) for levels below FFL: GRD, APRON
- Triangle immediately left of the label text, on layer A-ANNO-LEVL
- **Abstract:** decision is `z >= 0.0` → triangle_down, else → triangle_up (no hardcoded level names)

**Log:** `§LEVEL_SYMBOL code=FFL z=0.000 symbol=triangle_down size=3.0mm src=template`

## Task 3 — Section cut markers on floor plan (I-46)

TB-LKTN page 1: section cut lines A-A and B-B across the building with circled
letters and directional arrows.

**Implementation in `write_floor_plan_dxf()` after dimensions:**
- SECTION_ARROW block already defined (line ~417) but never placed
- Section A-A: horizontal cut at building Y midpoint, extends 1m beyond building
- Section B-B: vertical cut at building X midpoint, extends 1m beyond building
- Line: dashed (`HIDDEN` linetype), layer A-ANNO-SECT
- Each end: circled label (same style as grid bubbles) + SECTION_ARROW INSERT rotated to viewing direction
- **Template keys:** `section_markers.line_weight_mm: 0.18`, `section_markers.label_size_mm: 3.0`,
  `section_markers.extend_beyond_m: 1.0`
- **Abstract:** number of sections from template list, not hardcoded A-A/B-B

**Log:** `§SECTION_MARKER id=A-A axis=y pos={y}m extent=({x0},{x1})m src=template`

## Task 4 — Door/window tags on elevations (I-53 partial)

TB-LKTN pages 4-5: elevation views show D1, W1, CP tags on the building face.
Our floor plan has these tags but elevations do not.

**Implementation in `write_elevation_dxf()` after wall/door/window rendering:**
- For each door/window element visible on this face:
  - Place tag label (D{n}, W{n}) at element centroid in elevation coordinates
  - Use same tag style as floor plan (hexagon for windows, circle for doors)
  - Tag blocks (DOOR_TAG, WINDOW_TAG) already defined — just INSERT them
- **Abstract:** same `_draw_tag_shape()` helper already used on floor plan

**Log:** `§ELEV_TAG face={face} tag={label} at=({h},{v})mm type={door|window}`

## Task 5 — Finish code callouts per room (I-47)

TB-LKTN page 1: each room has finish codes below name (e.g., CT | V1).

**Implementation in `write_floor_plan_dxf()` room label block:**
- Read `2d_finish_type` table from 2D.db
- For each room, look up floor/wall finish code by room type
- Render between room name and area: `CT | V1` centered
- **Template key:** `room_labels.show_finish_codes: true`
- **Abstract:** if 2d_finish_type has no row for a room type, skip (don't invent)

**Log:** `§FINISH_CODE room={type} floor={code} wall={code} src=2d_finish_type`

## Task 6 — Roof profile eave/fascia line on elevation (I-48)

TB-LKTN pages 4-5: elevation shows fascia board line at eave level, distinct from
the roof ridge line. Our engine draws the roof silhouette as a single polyline.

**Implementation in `write_elevation_dxf()` after roof silhouette:**
- Extract eave Z from roof mesh: min Z of roof vertices (= top of wall / beam level)
- Draw horizontal fascia line at eave Z, extending to roof overhang
- Roof overhang distance from template: `roof.eave_overhang_mm: 700`
- The gable peak is already in the envelope — verify rendering for DX (gable) and SH (barrel vault)
- **Abstract:** works from mesh Z data, not hardcoded heights

**Log:** `§ROOF_PROFILE face={face} eave_z={z}m fascia_extent=({h0},{h1})mm overhang={mm}mm`

## Pre-flight

1. Read `2D_Layout/OPEN_ISSUES.txt`
2. Read `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §24
3. Run SH + DX, read logs — confirm door swings, bubbles, tags already work
4. Open TB-LKTN PDF for visual comparison

## Run after every task

```bash
cd 2D_Layout/python
python3 drawing_writer_dxf.py --all --proof ../input/SH_extracted.db
python3 drawing_writer_dxf.py --all --proof ../input/DX_extracted.db
```

Read the log. Verify §PROOF lines. Compare SVG against TB-LKTN PDF.

## Principle: Abstract, not custom

The SH archive door arcs work for ANY building because they derive from element
geometry (bbox, host wall, furniture clustering). Every new feature must follow
this pattern:
- **Input:** element geometry from DB + style from template
- **Decision:** spatial logic (Z threshold, wall orientation, room side)
- **Output:** DXF entity on the correct layer with template-sourced style
- **Never:** building name checks, hardcoded positions, magic coordinates

## What NOT to Do

- Do NOT re-implement door swing arcs (I-45) — already done, 3 arcs on SH, 14 on DX
- Do NOT change wall/grid/dimension rendering that matches the archive
- Do NOT start Phase B (missing pages) — this session is Phase A only
- Do NOT touch `component_library.db`
- Do NOT invent finish codes — extract from 2D.db or skip

## When Done

- Update OPEN_ISSUES.txt: close completed I-## with §PROOF log lines
- Prepend `# DONE` to this file
- Update PROGRESS.md
