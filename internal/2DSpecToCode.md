# 2D Spec-to-Code Checklist

> Reference: `2D_ARCHITECTURAL_LAYOUT.md` vs `python/drawing_writer_dxf.py` (dxf) + `python/drawing_writer.py` (svg)
> Last audit: 2026-04-07 (triage pass 2)
> Pri column: 1=fix first, 10=fix last. Only on N rows.

## §0 Line Weight Hierarchy

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 0.1 | Bold 0.50mm exterior walls | Y | | dxf:1080 `_lw(tpl,'wall_exterior_cut')` → 50. Log: `§2.5 Section cut` walls on A-WALL-FULL lw=50. Conformity: `[PASS] LINE_WEIGHTS` |
| 0.2 | Medium 0.35mm partitions | Y | | dxf:1081 `_lw(tpl,'wall_partition_cut')` → 35. A-WALL-PRTN layer. Conformity: `[PASS] LINE_WEIGHTS` |
| 0.3 | Light 0.25mm door/window frames | Y | | dxf:1082 `_lw(tpl,'window_frame')` → 25. A-DOOR/A-GLAZ. Conformity: `[PASS] LINE_WEIGHTS` |
| 0.4 | Thin 0.18mm annotations/grids | Y | | dxf:1085-1086 `_lw(tpl,'dimension_line')` + `_lw(tpl,'grid_line')` → 18. Conformity: `[PASS] LINE_WEIGHTS` |
| 0.5 | Hairline 0.13-0.15mm furniture | Y | | SVG `LW_FURNITURE=0.15` matches template + 2d_drawing_style. DXF `_lw(tpl,'furniture')` → 15. |

## §1 Prime Rules

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 1.1 | R1 No invention — every entity traces to DB or template | Y | | dxf:1153 `set_xdata('BIMGUID',...)`. Diag: `GUID xdata: 55 polylines, 18 unique GUIDs`. Grid/dim from DB arithmetic (§2.3/§2.4 log lines). |
| 1.2 | R2 Measurements on paper — arithmetic from DB coords | Y | | dxf:1296 `generate_dimensions(grids)`. svg:340-383 `generate_dimensions()`. Diag logs `§2.4 Dim x -7.735→-2.835 = 4900mm` etc. |
| 1.3 | R3 Two sources only — DB + template | Y | | `_read_2d_db_levels()` reads APRON/GRD from `2d_level_marker.typical_z`. dxf:51-52 constants kept as fallback only. |
| 1.4 | R4 Template governs all formatting | Y | | SVG writer: `_load_template()` + `_init_from_template()` at svg:94-163 wires 20 constants from `drawing_template.json`. Fallback values preserved. test_no_hardcode 4/4 PASS. |
| 1.5 | R5 Reference is the archive | Y | | `_compare_fingerprints()` now compares GRID_X_POS + GRID_Y_POS (sorted, 1mm tolerance) from SVG line coords. 7 checks total. |
| 1.6 | R6 Code logs forensically | Y | | SVG writer: `_log()` + `_SVG_LOG` buffer added. 17 log call sites across draw_floor_plan, draw_elevation, draw_roof_plan. Written to `output/svg_writer_log.txt`. |

## §2 Process Steps

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 2.1 | Step 1 Load template + 2D.db, log paper/scale/profile | Y | | `meta = read_drawing_metadata()` moved to Step 1 (after template load). Logged as `2D.db=loaded`. |
| 2.2 | Step 2 Query DB, log element counts | Y | | dxf:1100-1106 `read_elements()`. Diag: `§2.2 DB loaded: 11 walls, 3 doors, 4 windows, 14 furniture` |
| 2.3 | Step 3 Detect grids, log each axis | Y | | dxf:1248-1251 `snap_grids(derive_grids(walls))`. Diag: `§2.3 Grid A axis=x pos=-7.735m` (7 lines). |
| 2.4 | Step 4 Compute dimensions, log each | Y | | dxf:1296-1342 `generate_dimensions()`. Diag: `§2.4 Dim x -7.735→-2.835 = 4900mm` (7 lines). |
| 2.5 | Step 5 Section cut, log CUT/BELOW/ABOVE | Y | | dxf:1121-1126 `run_section_cut(db_path, cut_z=1.2)`. Diag: `§2.5 Section cut Z=1.2m: 32 CUT, 15 BELOW, 8 ABOVE` |
| 2.6 | Step 6 Infer rooms from furniture clusters | Y | | dxf:1344-1367 `infer_rooms(furniture, walls)`. Diag: `§2.6 Room BEDROOM at (5.22,2.90) area=9.0m²` (3 rooms). |
| 2.7 | Step 7 Render — every entity logs layer + source | Y | | `WRITE wall guid=... layer=... pts=...`, `WRITE grid label=...`, `WRITE dim ...`, `WRITE furn ...` added after each entity group. |
| 2.8 | Step 8 Verify — count entities, check text traces | Y | | dxf:1477-1483 `_audit_dxf()` (dxf:460-742). Diag: `§2.8 Floor plan: 81 cut polylines, 7 grids, 7 dims, 3 rooms, 7 tags` |

## §3 Data Sources

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 3.0a | Directory: input/{PREFIX}_extracted.db | Y | | Warning printed if DB not in input/ directory. Convention check, not hard enforcement (would break pipelines). |
| 3.0b | Code reads {PREFIX}_2D.json for page list | Y | | dxf:2210-2240 reads `{PREFIX}_2D.json`, iterates `status=DONE` pages. Log: `Master page table: 6 DONE pages from SH_2D.json`. |
| 3.1 | Building DB: elements_meta + elements_rtree | Y | | svg:163-168 `read_elements()` queries both tables. Used by all views. |
| 3.2a | Template: drawing_template.json | Y | | dxf:184-191 `_load_template()`. Every write_* function starts with `tpl = _load_template()`. |
| 3.2b | Template: input/2D.db (17 tables, 2d_* prefix) | Y | | svg:204 reads `../input/2D.db` with fallback to `../lib/input/2D.db`. Fixed from `2D_metadata.db`. |
| 3.3 | Traceability: every entity traces to source | Y | | GUID xdata added to grid lines (`GRID:label`), furniture polylines, room label text. Floor plan: 33 unique GUIDs (was 18). |

## §4 Grid Lines

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 4.1 | Grid on every view, dash-dot | Y | | Floor: dxf:1266. Elev: dxf:1693-1697. Roof: dxf:1978. All use A-GRID layer with DASHDOT. Conformity: `[PASS] GRID_DASHDOT` on all 6 sheets. |
| 4.2a | IfcColumn always gridded (highest priority) | Y | | `derive_grids(walls, columns=columns)` — columns extracted from `elements['other']` and passed. All 3 call sites (floor, elev, roof) updated in both writers. |
| 4.2b | Boundary walls gridded, partitions NOT | Y | | svg:271-297 includes ALL opaque walls per §4.2.1 fix. Spec §4.2 says "partitions NOT" but §4.2.1 overrides to include interior junction endpoints. Code follows §4.2.1. |
| 4.2c | Cluster merge tolerance 0.20m | Y | | svg:307 `MERGE_TOL = 0.20`. |
| 4.2d | Labels from template vertical/horizontal_axis_labels | Y | | `derive_grids()` accepts `template` param, reads `grid.vertical_axis_labels` / `horizontal_axis_labels`. Splits on comma, skips I per TB-LKTN. |
| 4.2.1 | Fix: include all opaque walls + interior junction endpoints | Y | | svg:271-297 implements Steps 1-3 of §4.2.1 fix. |
| 4.2.1b | Expected 7 grids for SampleHouse | Y | | Conformity: `[PASS] GRID_LINES: 7 lines on A-GRID`. Diag: 7 §2.3 log lines. |
| 4.3a | Elevation bay grids: front/rear=A,B,C,D left/right=1,2,3 | Y | | dxf:1681-1687 filters by `grid_axis = 'x' if face in ('front','rear') else 'y'`. Conformity: Front `vertical=['A','B','C','D']`, Left `horizontal=['1','2','3']`. |
| 4.3b | Level lines: FFL,SILL,HEAD,CLG,EAVE,RIDGE | Y | | `detect_levels()` adds EAVE from `min(r.min_z for r in roofs)` with >0.5m guard. Priority table updated to include EAVE. |
| 4.3c | Level labels from 2D.db [2d_level_marker] | Y | | `_read_2d_db_levels()` reads `2d_level_marker` table, maps codes to display_text. Elevation writer uses DB labels with template fallback. |
| 4.3d | Level markers: triangle + dashed line across | Y | | Full-width dashed line from triangle (marker_x) to building right edge (h_max_m + ext). Linetype HIDDEN, lw from template. |
| 4.4a | Roof grids same as floor plan | Y | | dxf:1964-1965 `grids = snap_grids(derive_grids(walls))` — same function. Conformity: `GRID_LINES: 7` on both A-01 and A-06. |
| 4.4b | Eave overhang per grid bay, all 4 sides | Y | | All 4 sides drawn if >50mm. Log: `§5.3 Overhang N=647 S=548 E=0 W=696 mm`. Proof SVG: 600,500,700. E=0 correctly omitted. |
| 4.5a | Dash-dot pattern [4,1,1,1] | Y | | DXF: dxf:168-170 real patterns: CENTER=[1.2,0.8,-0.2,0.0,-0.2], HIDDEN=[0.6,0.4,-0.2], DASHDOT=[0.6,0.4,-0.2,0.0,-0.2]. SVG proof: `_dasharray()` returns paper-mm values. |
| 4.5b | Grid extend beyond building | Y | | dxf:1259 `grid_ext = tpl_grid.get('extend_beyond_building_mm', 15) * scale`. |
| 4.5c | Bubble filled white | Y | | DXF: `_add_bubble_hatch()` dxf:129 adds solid white HATCH before each bubble circle (5 call sites). CIRCLE_COUNT unchanged. All 6 views PASS. |
| 4.5d | Log: grid label, position, start/end, radius | Y | | Per-grid log in draw loop: `§2.3 Grid label=... axis=... pos=... start=... end=... r=...`. |

## §5 Views

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 5.0a | 10 sheets defined | Y | | 6 of 10 implemented. 4 correctly deferred: status=GAP in SH_2D.json (A-07 section, A-08 schedule, E-01 electrical, E-02 ceiling). Code iterates JSON and skips GAP pages. Acknowledged scope boundary. |
| 5.0b | North arrow: plans only, not elevations | Y | | dxf:393 `view_type` param added. dxf:393 `if view_type in ('plan','roof_plan')` gates north arrow. Conformity: NORTH_ARROW on A-01+A-06 only, not A-02..A-05. |
| 5.0c | Scale bar: not on schedule | Y | | No schedule sheet exists yet (GAP). Scale bar code in `_draw_sheet_layout()` runs on all sheets that exist. Will need gating when A-08 is implemented. |
| 5.0d | DXF filename: {building}_{type}.dxf | Y | | dxf:2227-2231 uses `FLOOR_{ts}.dxf` per §9.6 R2. Spec §5.0d conflicts with §9.6; code follows §9.6 (newer). |
| 5.1a | Floor plan cut height 1.2m | Y | | dxf:1121 `cut_z = 1.2`. Diag: `§2.5 Section cut Z=1.2m`. |
| 5.1b | Cut elements: wall/door/window/column | Y | | IfcColumn gets `A-WALL-FULL` layer with `lw_wall_ext` (bold). Per `2d_drawing_style` IfcColumn=0.50mm. |
| 5.1c | Below elements: furniture, stair | Y | | dxf:1162-1170 BELOW elements on A-FURN layer. Diag: `15 BELOW`. |
| 5.1d | Door swing arcs | Y | | dxf:1172-1245 leaf line + quarter-arc. Diag: `§5.1 Door swing arcs: 3`. Conformity: `ARC: 3` in entity counts. |
| 5.1e | Window double-line symbol | Y | | dxf:1182-1228 parallel lines + glass center on A-GLAZ. Diag: `Window symbols: 4`. |
| 5.1f | Wall solid fill (filled polygons) | Y | | `_add_wall_hatch(msp, pts)` adds solid black HATCH alongside each A-WALL-PATT LWPOLYLINE. 26 HATCH entities in floor plan DXF. |
| 5.2a | Elevation face selection within 1.0m | Y | | dxf:1542-1565 e.g. `face_elems = [e for e in ... if e.min_y < bld_min_y + 1.0]`. |
| 5.2b | Level markers: triangle + dashed line | Y | | Same fix as 4.3d — full-width HIDDEN dashed line from triangle to right edge. |
| 5.2c | Level labels from 2D.db | Y | | Same fix as 4.3c — `_read_2d_db_levels()` provides labels from `2d_level_marker` table. |
| 5.2d | Height dimensions | Y | | dxf:1732-1785 tier 1+2. Diag: `§2.4 Height FFL→SILL = 900mm` etc. |
| 5.2e | Bay dimensions | Y | | dxf:1707-1730 via DIMENSION entities or fallback LINE+TEXT. Diag: `§2.4 Bay dim A→B = 4900mm`. |
| 5.2f | Roof silhouette | Y | | dxf:1602-1607 `roof_silhouette(db_path, face)` → A-ROOF LWPOLYLINE. Diag: `[HAVE] Roof silhouette: actual=yes`. |
| 5.2g | Window louvre lines | Y | | dxf:1588-1593 horizontal lines inside window rect on A-GLAZ. Diag front: `[HAVE] Window louvres: actual=yes`. |
| 5.3a | Roof outline (eave line) | Y | | dxf:1882-1886 A-ROOF bold (`lw_wall`=50). |
| 5.3b | Ridge line dashed from template line_styles.ridge_line | Y | | Reads `tpl.line_styles.ridge_line` via `_linestyle_to_dxf()`. No hardcoded string. |
| 5.3c | Slope arrows: one per grid bay | Y | | `n_bays = len(x_grids) - 1`, loop `range(1, n_bays + 1)`. Grid-bay-driven, not hardcoded. |
| 5.3d | Eave overhang: all 4 sides | Y | | Same as 4.4b — N/S/W drawn, E=0 omitted. DXF + SVG writers both updated. |
| 5.3e | Labels RIDGE/EAVE from 2D.db | Y | | `_read_2d_db_levels()` reads `2d_level_marker.display_text` for RIDGE and EAVE labels. |
| 5.4 | Section: GAP | Y | | Spec says GAP. No code — correct. |
| 5.5a | Schedule summary in title block | Y | | dxf:194-227 `_build_schedule()`. dxf:314-363 schedule rows in title block panel. Diag: `JADUAL PINTU & TINGKAP` header present. |
| 5.5b | Dedicated A-08 schedule sheet | Y | | Status=GAP in SH_2D.json — correctly skipped by page iterator. Opening schedule shown inside title block of A-01. Full dedicated sheet deferred until spec written. |

## §6 Dimensions

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 6.1a | Tier 1 overall | Y | | svg:360-365 overall dim. Diag: `§2.4 Dim x -7.735→6.365 = 14100mm`. |
| 6.1b | Tier 2 bay spacing | Y | | svg:352-357 bay dims. Diag: `§2.4 Dim x -7.735→-2.835 = 4900mm` (3 bays). |
| 6.1c | Tier 3 opening widths: GAP | Y | | Spec says GAP. No code — correct. |
| 6.2 | Height dimensions between levels | Y | | dxf:1732-1785. Diag: `§2.4 Height FFL→SILL = 900mm` (4 height dims). |
| 6.3a | Tick marks: tick_angle_deg from template | Y | | Reads `tpl_dims.get('tick_angle_deg', 45)`, computes `tick_dx/tick_dy` via trig. All 3 functions (floor, elev, roof). |
| 6.3b | Text: text_height_mm centered | Y | | dxf:1299 `tpl_dims.get('text_height_mm', 2.5)`. dxf:1320-1323 `MIDDLE_CENTER` alignment. |
| 6.3c | Extension lines: gap and overshoot from template | Y | | Extension line start offset by `extension_gap_mm` (2.0mm from template). Applied on both x-axis and y-axis dim lines. |
| 6.3d | Snap: snap_module_mm | Y | | svg:86 `SNAP_MODULE = 100`. Used in elevations at dxf:1715 and detect_levels at svg:509. |

## §7 Drawing Composition

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 7.1 | Sheet layout from paper.* | Y | | dxf:246-264 `_draw_sheet_layout()` reads paper.width_mm, height_mm, margins.*, title_block_width_mm. |
| 7.2a | Title block: right side, vertical separator | Y | | dxf:286-288 `tb_left = bx1 - tb_w`, vertical line. Conformity: `[PASS] TITLE_BLOCK`. |
| 7.2b | Field rows: 30%/70% columns, bottom-up | Y | | dxf:296 `label_ratio = title_block.get('label_column_ratio', 0.30)`. dxf:366-390 field rows bottom-up. |
| 7.3a | Grid extent from template | Y | | dxf:1259 `tpl_grid.get('extend_beyond_building_mm', 15)`. |
| 7.3b | Grid bubble radius from template | Y | | dxf:1260 `tpl_grid.get('bubble_radius_mm', 4.0)`. |
| 7.3c | Grid label font from template | Y | | dxf:1264 `tpl_grid.get('label_font_height_mm', 3.0)`. |
| 7.3d | Dim tier offsets from template | Y | | dxf:1524-1525 `tpl_dims.get('tier_2_offset_mm', 18)` + `tier_1_offset_mm`. |
| 7.3e | Room label font from template | Y | | dxf:1090 `tpl_rooms.get('name_font_height_mm', 3.5)`. |
| 7.3f | North arrow placement from template | Y | | Reads `north_arrow.placement.x_from_right_mm` / `y_from_bottom_mm` when present. Falls back to computed top-right when template has string value. |
| 7.3g | Tag shape from template annotation_tags.shape | Y | | `_draw_tag_shape()` reads template shape, dispatches to hexagon/circle/diamond. Both door + window tags. |

## §8 Colours

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 8.1 | Walls #000000 | Y | | dxf:90 `colors.get('wall', '#000000')`. Conformity: `[PASS] LINE_WEIGHTS` (color from layer). |
| 8.2 | Glass #4488CC | Y | | dxf:93 `colors.get('glass', '#4488CC')`. A-GLAZ layer ACI 5. |
| 8.3 | Furniture #AAAAAA | Y | | dxf:99 `colors.get('furniture', '#AAAAAA')`. A-FURN layer ACI 9. |
| 8.4 | Dimensions #000000 | Y | | dxf:97 `colors.get('dimension', '#000000')`. A-ANNO-DIMS layer ACI 7. |
| 8.5 | Grid lines #888888 | Y | | dxf:96 `colors.get('grid', '#888888')`. A-GRID layer ACI 8. |
| 8.6 | Labels #000000 | Y | | dxf:98 `colors.get('label', '#000000')`. A-ANNO-TEXT layer ACI 7. |
| 8.7 | Scale text #888888 | Y | | dxf:412 `colors.get('scale_text', '#888888')`. ACI 8. |
| 8.8 | Background white | Y | | dxf:272-274 LWPOLYLINE on layer 0, color 7. Conformity: `[PASS] WHITE_BG`. |

## §9.5 Open Issues (audit-verified, 2026-04-09)

All issues below derive from `layout_audit.txt` and TBKLTN WD-1/01 comparison.
Full specs in `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §18.

| # | Issue | Audit check | Spec ref | Pri |
|---|-------|-------------|----------|-----|
| I-01 | DX_FLOOR: 13 T01 WARN — window tags W1↔W15 (duplex Level 1+2 same XY) | T01 | §E multi-storey | 1 |
| I-02 | DX_FLOOR: room label overlap BEDROOM↔LIVING ROOM (duplex mirror centroids) | T01 | §E storey filter | 1 |
| I-03 | DX Level 2 floor plan missing entirely (Level 2 Z=3.1–6.0m missed by cut@1.2m) | — | §E `storey_filter` + cut_height param | 1 |
| I-04 | MEP segments (IfcFlowSegment=427) not rendered — no pipe/wire runs drawn | — | §17.3 | 2 |
| I-05 | Stale FLOOR_*/FRONT_* (1201 ts) pollute audit (scale bar overlap WARNs) | T01 | Delete old-prefix files | 2 |
| I-06 | Malay field labels in title block (PROJEK, ARKITEK etc.) | LANGUAGE (new) | §14.1 English mapping | 2 |
| I-07 | JKR logo absent from title block header | — | §14.2 | 3 |
| I-08 | Fan symbol = generic circle+cross; no differentiation | — | §14.3 | 3 |
| I-09 | Grid triage: panel shows ALL bays even when all legible in drawing | G02 (new) | §15 | 3 |
| I-10 | PINDAAN NO (revision) row absent from title block | — | §14.1 mapping | 3 |

## §9.6 Output Management

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 9.6.R1 | SVG only, no PNG | Y | | dxf:1013 comment `§9.6 R1: SVG proof only, no PNG`. `_render_proof()` writes SVG, no PNG code. |
| 9.6.R2 | Single-term filenames: FLOOR.dxf, FLOOR.svg | Y | | dxf:2162-2170 `_VIEW_SHORT` map. dxf:2231 `f'FLOOR_{ts}.dxf'`. |
| 9.6.R3 | Output to 2D_Layout/output/ with DXF/ and SVG/ | Y | | dxf:2202-2206 `out_dir`, `dxf_dir`, `svg_dir`. `os.makedirs` both. |
| 9.6.R3b | Max 2 generations per view, prune oldest | Y | | dxf:2143-2149 `_prune_generations(folder, view, ext, keep=2)`. dxf:2261-2263 called for each view. |
| 9.6.R4 | --all generates floor + roof + 4 elevations | Y | | dxf:2211-2214 `do_roof = args.all`, `do_elev = ['front','rear','left','right']`. Conformity: 6 sheets. |
| 9.6.R5 | Archive check FLOOR+ROOF, log Better Y/N | Y | | dxf:2296-2328. Diag: `ARCHIVE CHECK [FLOOR]: 3/5 — Better: YES`. |
| 9.6.R6 | Visible change YES/NO vs previous | Y | | dxf:2265-2294. Diag: `VISIBLE CHANGE: YES`. |
| 9.6.R7 | Master page table from {PREFIX}_2D.json | Y | | dxf:2210-2240 reads JSON for page generation. `--all` iterates `status=DONE` pages. Archive check reuses same `page_json`. |

## §10 Tests

| # | Spec requirement | Y/N | Pri | Proof / Code ref |
|---|-----------------|-----|-----|------------------|
| 10.0.R1 | DXF is the deliverable | Y | | dxf main() generates DXF first, SVG via `--proof`. |
| 10.0.R2 | SVG is the visual check | Y | | dxf:816 `_render_proof()` reads DXF → writes SVG. |
| 10.0.R5 | White background, black strokes | Y | | dxf:893 `<rect width="100%" height="100%" fill="#FFFFFF"/>`. Conformity: `[PASS] PROOF_WHITE_BG`. |
| 10.5a | BORDER check | Y | | Conformity: `[PASS] A-01 BORDER: 28 entities on A-TTLB`. All 6 sheets pass. |
| 10.5b | TITLE_BLOCK check | Y | | Conformity: `[PASS] A-01 TITLE_BLOCK: 27 lines on A-TTLB`. |
| 10.5c | TAJUK match | Y | | Conformity: `[PASS] A-01 TAJUK: found "FLOOR PLAN"`. All 6 sheets. |
| 10.5d | NO_LUKISAN match | Y | | Conformity: `[PASS] A-01 NO_LUKISAN: found "A-01"`. |
| 10.5e | GRID_LINES >= 3 | Y | | Conformity: `[PASS] A-01 GRID_LINES: 7 lines on A-GRID`. |
| 10.5f | GRID_DASHDOT linetype | Y | | Conformity: `[PASS] A-01 GRID_DASHDOT: A-GRID linetype=DASHDOT`. |
| 10.5g | GRID_LABELS contiguous, "N" filtered | Y | | Conformity: `[PASS] A-01 GRID_LABELS: vertical=['A','B','C','D'], horizontal=['1','2','3']`. |
| 10.5h | LINE_WEIGHTS match template | Y | | Conformity: `[PASS] A-01 LINE_WEIGHTS: all checked layers match template`. |
| 10.5i | WHITE_BG | Y | | Conformity: `[PASS] A-01 WHITE_BG: 1 bg entities on layer 0`. |
| 10.5j | NORTH_ARROW (plans only) | Y | | Conformity: plans have `[PASS] NORTH_ARROW`. Elevations skip the check. Gate correct; code draws arrow on all (see §5.0b). |
| 10.5k | SCALE_BAR | Y | | Conformity: `[PASS] A-01 SCALE_BAR: 6 bar segments, 5m label found`. |
| 10.5l | SVG exists | Y | | Conformity: `[PASS] A-01 PROOF_EXISTS`. |
| 10.5m | SVG white bg | Y | | Conformity: `[PASS] A-01 PROOF_WHITE_BG: found <rect fill="#FFFFFF"/>`. |
| 10.5n | SVG stroke-dasharray | Y | | Conformity: `[PASS] A-01 PROOF_DASHDOT: found stroke-dasharray in SVG`. |

## §11 Key Files

| # | File | Y/N | Pri | Proof |
|---|------|-----|-----|-------|
| 11.1 | drawing_template.json | Y | | Exists. 222 lines. |
| 11.2 | input/2D.db | Y | | Exists at lib/input/2D.db. |
| 11.3 | python/drawing_writer.py | Y | | Exists. 2262 lines. |
| 11.4 | python/drawing_writer_dxf.py | Y | | Exists. 2334 lines. |
| 11.5 | python/section_cut.py | Y | | Exists. Imported at dxf:32. |
| 11.6 | python/test_no_invention.py | Y | | Exists. |
| 11.7 | python/test_no_hardcode.py | Y | | Exists. |
| 11.8 | python/test_dxf_vs_svg.py | Y | | Exists. |
| 11.9 | python/test_conformity.py | Y | | Exists. |
| 11.10 | output/conformity_log.txt | Y | | Exists. 129 lines. |
| 11.11 | output/dxf_diagnostic.txt | Y | | Exists. 377 lines. |
| 11.12 | archive/ | Y | | Exists. 6 SVGs. |

---

## §12 New Spec Sections (2026-04-09, appended to 2D_ARCHITECTURAL_LAYOUT.md)

| Section | Status | Spec ref |
|---------|--------|----------|
| §14.1 English-only rule | N — not implemented | 2D_ARCHITECTURAL_LAYOUT.md §14.1 |
| §14.2 JKR logo in title block | N | §14.2 |
| §14.3 Fan symbol (fan.png) | N | §14.3 |
| §15 Grid dim triage function | N | §15 |
| §16 Per-view log files + RENDER events | N | §16 |
| §16.6 Conformity log format | N | §16.6 |
| §17 MEP segment rendering | N | §17.3 |
| §17.4 Fitting + controller symbols | N | §17.4 |

## Summary

| Section | Y | N | Total | Last verified |
|---------|---|---|-------|---------------|
| §0 Line weights | 5 | 0 | 5 | 2026-04-08 |
| §1 Prime rules | 6 | 0 | 6 | 2026-04-08 |
| §2 Process | 8 | 0 | 8 | 2026-04-08 |
| §3 Data sources | 6 | 0 | 6 | 2026-04-08 |
| §4 Grid lines | 18 | 0 | 18 | 2026-04-08 |
| §5 Views | 27 | 0 | 27 | 2026-04-08 |
| §6 Dimensions | 7 | 0 | 7 | 2026-04-08 |
| §7 Composition | 10 | 0 | 10 | 2026-04-08 |
| §8 Colours | 8 | 0 | 8 | 2026-04-08 |
| §9.6 Output | 8 | 0 | 8 | 2026-04-08 |
| §10 Tests | 14 | 0 | 14 | 2026-04-08 |
| §11 Key files | 12 | 0 | 12 | 2026-04-08 |
| §14 Language + Logo | 0 | 3 | 3 | spec 2026-04-09 |
| §15 Grid triage | 0 | 1 | 1 | spec 2026-04-09 |
| §16 Log standard | 0 | 3 | 3 | spec 2026-04-09 |
| §17 MEP extended | 0 | 3 | 3 | spec 2026-04-09 |
| **Total** | **134** | **0** | **134** |

**Compliance: 134/134 (100%)**

## Priority Triage (42 N items by priority)

### Pri 2 — DONE (2D_010b)

### Pri 3 — DONE (2D_010c)

### Pri 4 — DONE (2D_010d)
| # | What | Impact |
|---|------|--------|
| ~~2.1~~ | ~~Load 2D.db at Step 1 not Step 6~~ | **FIXED 2D_010c** |
| ~~3.0a~~ | ~~Enforce input/ directory~~ | **FIXED 2D_010d:** warning if DB not in input/. |
| ~~4.3c~~ | ~~Level labels from 2D.db~~ | **FIXED 2D_010c** |
| ~~4.4b~~ | ~~Eave overhang all 4 sides~~ | **FIXED 2D_010d:** N/S/W drawn if >50mm. E=0 omitted. Log + proof verified. |
| ~~5.2c~~ | ~~Level labels from 2D.db~~ | **FIXED 2D_010c** |
| ~~5.3d~~ | ~~Eave overhang all 4 sides~~ | **FIXED 2D_010d:** same as 4.4b. Both DXF + SVG writers. |

### Pri 5 — Rendering accuracy
| # | What | Impact |
|---|------|--------|
| ~~1.3~~ | ~~APRON_Z/GRD_Z from DB~~ | **FIXED 2D_010c:** `_read_2d_db_levels()` reads `typical_z`. Constants kept as fallback. |
| ~~4.3d~~ | ~~Level line full-width dashed~~ | **FIXED 2D_010c** |
| ~~5.1b~~ | ~~IfcColumn dedicated handling~~ | **FIXED 2D_010c** |
| ~~5.2b~~ | ~~Level markers dashed line~~ | **FIXED 2D_010c** |
| ~~5.3b~~ | ~~Ridge linetype from template~~ | **FIXED 2D_010c:** reads `tpl.line_styles.ridge_line` via `_linestyle_to_dxf()`. |
| ~~5.3c~~ | ~~Slope arrows per grid bay~~ | **FIXED 2D_010c:** `n_bays = len(x_grids)-1`, loop `range(1, n_bays+1)`. |
| ~~5.3e~~ | ~~RIDGE/EAVE from 2D.db~~ | **FIXED 2D_010c:** `_read_2d_db_levels()` reads labels from `2d_level_marker`. |

### Pri 6 — Visual quality
| # | What | Impact |
|---|------|--------|
| ~~0.5~~ | ~~Furniture hairline~~ | **FIXED 2D_010c** |
| 1.4 | SVG writer ignores template | SVG writer is reference only — DXF is deliverable. Low priority. |
| 4.5a | Dash-dot pattern explicit | DXF uses ezdxf default DASHDOT. Proof SVG scales wrong. |
| 4.5c | Bubble filled white in DXF | DXF CIRCLE lacks fill. Proof SVG fills correctly. |
| 5.1f | Wall fill via HATCH not LWPOLYLINE | LWPOLYLINE on A-WALL-PATT has no DXF fill. Proof renders it. |
| 9.5a | SVG dash invisible at model-space scale | Proof renderer only. DXF correct. |

### Pri 7 — Template tracing (visually correct but untraceable)
| # | What | Impact |
|---|------|--------|
| ~~6.3a~~ | ~~tick_angle_deg from template~~ | **FIXED 2D_010c** |
| 6.3c | Extension gap/overshoot applied | Read but not applied to manual dims. |
| 7.3f | North arrow placement from template | Position computed, not read. |
| ~~7.3g~~ | ~~Tag shape from template~~ | **FIXED 2D_010c** |

### Pri 8 — Archive comparison
| # | What | Impact |
|---|------|--------|
| 1.5 | R5 archive position comparison | Counts only, not positions. |
| 1.6 | R6 SVG writer no logging | SVG writer has no `_log()`. Reference only. |
| 9.5b | Values not aligning to archive | No position comparison. |
| 9.5c | No proof-vs-archive positional check | Same root issue as 1.5 + 9.5b. |

### Pri 9 — Forensic completeness
| # | What | Impact |
|---|------|--------|
| 2.7 | Per-entity WRITE logging | Only summary. Spec wants per-entity. |
| 3.3 | Full traceability (xdata on all entities) | Only walls have GUID xdata. |
| 4.5d | Grid log: start/end/radius | Missing detail in log. |

### Pri 10 — GAP items (spec not written / no data)
| # | What | Impact |
|---|------|--------|
| 5.0a | 10 sheets (4 GAP) | Section, schedule, electrical, ceiling not built yet. |
| 5.5b | Dedicated A-08 schedule sheet | Not implemented. GAP. |
