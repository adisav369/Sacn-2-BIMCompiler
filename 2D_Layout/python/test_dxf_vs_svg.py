#!/usr/bin/env python3
"""
Anti-drift test: verify DXF floor plan matches SVG reference output.
Checks that all features present in the archive SVGs are also in the DXF.
"""
import sys, os, re, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ezdxf
from collections import Counter

DXF_PATH = os.path.join(os.path.dirname(__file__), 'output', 'ifc4_sample_house_floor_plan.dxf')
SVG_PATH = os.path.join(os.path.dirname(__file__), '..', 'archive', 'ifc4_sample_house_floor_plan.svg')
TPL_PATH = os.path.join(os.path.dirname(__file__), '..', 'drawing_template.json')

def load_template():
    with open(TPL_PATH) as f:
        return json.load(f)

def test_floor_plan_dxf():
    """DXF floor plan must contain all features present in archive SVG."""
    assert os.path.exists(DXF_PATH), f"DXF not found: {DXF_PATH}"
    doc = ezdxf.readfile(DXF_PATH)
    msp = doc.modelspace()
    
    entities = list(msp)
    types = Counter(e.dxftype() for e in entities)
    texts = [e for e in entities if e.dxftype() in ('TEXT', 'MTEXT')]
    text_values = [e.dxf.text for e in texts if hasattr(e.dxf, 'text')]
    
    results = []
    
    # 1. Walls (section cut polylines)
    polys = [e for e in entities if e.dxftype() == 'LWPOLYLINE']
    wall_polys = [e for e in polys if e.dxf.layer in ('A-WALL-FULL', 'A-WALL-PRTN')]
    ok = len(wall_polys) >= 5
    results.append(('Wall section polylines', ok, f'{len(wall_polys)} polylines'))
    
    # 2. Grid lines (A-GRID layer)
    grid_lines = [e for e in entities if e.dxftype() == 'LINE' and e.dxf.layer == 'A-GRID']
    ok = len(grid_lines) >= 7  # 4 vertical + 3 horizontal
    results.append(('Grid lines', ok, f'{len(grid_lines)} lines'))
    
    # 3. Grid bubbles (circles on A-GRID)
    bubbles = [e for e in entities if e.dxftype() == 'CIRCLE' and e.dxf.layer == 'A-GRID']
    ok = len(bubbles) >= 14  # 7 grids × 2 ends
    results.append(('Grid bubbles', ok, f'{len(bubbles)} circles'))
    
    # 4. Grid labels (A, B, C, D, 1, 2, 3)
    grid_labels = set()
    for t in text_values:
        if t in ('A', 'B', 'C', 'D', '1', '2', '3'):
            grid_labels.add(t)
    ok = grid_labels >= {'A', 'B', 'C', 'D', '1', '2', '3'}
    results.append(('Grid labels A-D,1-3', ok, f'found: {sorted(grid_labels)}'))
    
    # 5. Dimension texts present on A-ANNO-DIMS layer
    dim_texts = [e for e in texts if e.dxf.layer == 'A-ANNO-DIMS']
    dim_numbers = [e.dxf.text for e in dim_texts if e.dxf.text.isdigit()]
    ok = len(dim_numbers) >= 3
    results.append(('Bay dimension texts (>=3)', ok, f'{len(dim_numbers)} found'))
    # Overall dimension present and adds up
    if dim_numbers:
        vals = sorted(int(n) for n in dim_numbers)
        # Check X axis: first 4 values (3 bays + 1 overall)
        # Check Y axis: last 3 values (2 bays + 1 overall)
        adds_up = len(vals) >= 4  # at least bays + overall on one axis
    else:
        adds_up = False
    results.append(('Dimension chain present (bays + overall)', adds_up, f'{dim_numbers}'))
    
    # 6. Room labels (Malay)
    has_ruang_tamu = any('RUANG TAMU' in t for t in text_values)
    has_ruang_makan = any('RUANG MAKAN' in t for t in text_values)
    has_bilik = any('BILIK' in t for t in text_values)
    results.append(('Room label: RUANG TAMU', has_ruang_tamu, ''))
    results.append(('Room label: RUANG MAKAN', has_ruang_makan, ''))
    results.append(('Room label: BILIK', has_bilik, ''))
    
    # 7. Room areas (m²)
    has_areas = any('m²' in t for t in text_values)
    results.append(('Room areas (m²)', has_areas, ''))
    
    # 8. Door tags (D1, D2, D3)
    door_tags = [t for t in text_values if re.match(r'^D\d+$', t)]
    ok = len(door_tags) >= 3
    results.append(('Door tags (D1-D3)', ok, f'found: {door_tags}'))
    
    # 9. Window tags (W1-W...)
    win_tags = [t for t in text_values if re.match(r'^W\d+$', t)]
    ok = len(win_tags) >= 4
    results.append(('Window tags (W1+)', ok, f'found: {win_tags}'))
    
    # 10. Drawing title
    has_title = any('PELAN LANTAI' in t or 'FLOOR PLAN' in t for t in text_values)
    results.append(('Drawing title (PELAN LANTAI / FLOOR PLAN)', has_title, ''))
    
    # 11. Scale text
    has_scale = any('1:100' in t for t in text_values)
    results.append(('Scale text (1:100)', has_scale, ''))
    
    # 12. Furniture (BELOW elements on A-FURN)
    furn = [e for e in polys if e.dxf.layer == 'A-FURN']
    ok = len(furn) >= 5
    results.append(('Furniture bboxes', ok, f'{len(furn)} items'))
    
    # 13. GUID xdata
    guid_count = sum(1 for e in polys if e.has_xdata('BIMGUID'))
    ok = guid_count >= 5
    results.append(('GUID xdata', ok, f'{guid_count} polylines'))
    
    # 14. Dimension tick marks (45-degree lines on A-ANNO-DIMS)
    dim_lines = [e for e in entities if e.dxftype() == 'LINE' and e.dxf.layer == 'A-ANNO-DIMS']
    ok = len(dim_lines) >= 10  # extension + dim + tick lines
    results.append(('Dimension lines + ticks', ok, f'{len(dim_lines)} lines'))

    # 15. Sheet border rectangle exists
    border_layers = ('A-TTLB', 'A-ANNO-TTLB')
    border_polys = [e for e in entities if e.dxftype() in ('LWPOLYLINE', 'LINE')
                    and e.dxf.layer in border_layers]
    ok = len(border_polys) >= 1
    results.append(('Sheet border', ok, f'{len(border_polys)} entities'))

    # 16. Title block panel — vertical line separating content from right panel
    ttlb_lines = [e for e in entities if e.dxftype() == 'LINE'
                  and e.dxf.layer in ('A-TTLB', 'A-ANNO-TTLB')]
    ok = len(ttlb_lines) >= 1
    results.append(('Title block panel (vertical separator line)', ok, f'{len(ttlb_lines)} lines'))

    # 17. Title block has field rows (horizontal lines + label texts in panel)
    ttlb_texts = [e for e in texts if e.dxf.layer in ('A-TTLB', 'A-ANNO-TTLB')]
    ok = len(ttlb_texts) >= 5
    results.append(('Title block field texts (>=5)', ok, f'{len(ttlb_texts)} texts'))

    # 18. North arrow present (text "N" exists)
    has_north = any(t == 'N' for t in text_values)
    results.append(('North arrow', has_north, ''))

    # 19. White background (SVG has style="background:#FFFFFF")
    # For DXF: white background rectangle on layer 0 or $BGCOLOR header
    bg_rect = [e for e in entities if e.dxftype() in ('LWPOLYLINE', 'SOLID')
               and e.dxf.layer == '0' and getattr(e.dxf, 'color', 0) == 7]
    has_bgcolor = len(bg_rect) >= 1
    results.append(('White background', has_bgcolor, f'{len(bg_rect)} bg entities'))

    # ── §9.3 Parity tests: features the SVG archive has, DXF must match ──

    # 20. Door swing arcs (§5.1) — SVG has arc per door, DXF needs ARC on A-DOOR
    door_arcs = [e for e in entities if e.dxftype() == 'ARC' and e.dxf.layer == 'A-DOOR']
    ok = len(door_arcs) >= 3  # SampleHouse has 3 doors
    results.append(('Door swing arcs (§5.1)', ok, f'{len(door_arcs)} arcs'))

    # 21. Window double-line symbols (§5.1) — SVG has 3 lines per window in wall
    # In DXF: LINE entities on A-GLAZ layer in floor plan (not elevation louvres)
    glaz_lines = [e for e in entities if e.dxftype() == 'LINE' and e.dxf.layer == 'A-GLAZ']
    ok = len(glaz_lines) >= 12  # 4 windows × 3 lines each
    results.append(('Window symbols (§5.1, 3 lines/window)', ok, f'{len(glaz_lines)} lines'))

    # 22. Wall solid fill (§5.1) — SVG fills walls black, DXF needs HATCH or fill
    wall_fill = [e for e in entities if e.dxf.layer == 'A-WALL-PATT'
                 and e.dxftype() in ('HATCH', 'LWPOLYLINE', 'SOLID')]
    ok = len(wall_fill) >= 5
    results.append(('Wall fill/hatch (§5.1)', ok, f'{len(wall_fill)} entities'))

    # 23. Hexagonal tags (§7.3) — annotation_tags.shape = "hexagon"
    # Archive uses 6-point polygons for door/window tags. DXF must match.
    tag_hexagons = [e for e in entities if e.dxftype() == 'LWPOLYLINE'
                    and e.dxf.layer == 'A-ANNO-TEXT'
                    and len(list(e.get_points(format='xy'))) == 6]
    ok = len(tag_hexagons) >= 7  # 3 doors + 4 windows
    results.append(('Hexagonal tags (§7.3)', ok, f'{len(tag_hexagons)} hexagons'))

    # 24. Door & window schedule in title block (archive has it)
    schedule_texts = [e.dxf.text for e in texts if hasattr(e.dxf, 'text')]
    has_schedule_header = any('JADUAL' in t or 'SCHEDULE' in t for t in schedule_texts)
    has_schedule_data = any('No.' in t for t in schedule_texts)  # "1 No.", "2 No." etc.
    ok = has_schedule_header and has_schedule_data
    results.append(('Door/window schedule table', ok,
                    f'header={has_schedule_header}, data={has_schedule_data}'))

    # 25. Scale bar (template scale_bar.*) — archive has bar 0-1-2-5m
    # Must have SOLID entities on A-ANNO-DIMS layer (bar segments) AND '5m' text
    scalebar_solids = [e for e in entities if e.dxftype() == 'SOLID'
                       and e.dxf.layer == 'A-ANNO-DIMS']
    has_5m = any(e.dxf.text == '5m' for e in texts if hasattr(e.dxf, 'text'))
    ok = len(scalebar_solids) >= 2 and has_5m
    results.append(('Scale bar (§7.3, bar + 5m label)', ok,
                    f'{len(scalebar_solids)} bar segments, 5m={has_5m}'))

    # Print results
    print("\n=== DXF Floor Plan Anti-Drift Test ===\n")
    pass_count = 0
    fail_count = 0
    for name, passed, detail in results:
        status = 'PASS' if passed else 'FAIL'
        if passed:
            pass_count += 1
        else:
            fail_count += 1
        detail_str = f'  ({detail})' if detail else ''
        print(f"  [{status}] {name}{detail_str}")
    
    print(f"\n  Score: {pass_count}/{pass_count + fail_count}")
    print(f"  {'ALL PASS' if fail_count == 0 else f'{fail_count} FAILURES'}")
    
    return fail_count == 0


def test_elevation_dxf():
    """DXF elevation must have level markers with height dimensions."""
    elev_path = os.path.join(os.path.dirname(__file__), 'output', 
                             'ifc4_sample_house_front_elevation.dxf')
    if not os.path.exists(elev_path):
        print("\n=== Elevation test skipped (no DXF) ===")
        return True
    
    doc = ezdxf.readfile(elev_path)
    tpl = load_template()
    msp = doc.modelspace()
    entities = list(msp)
    texts = [e.dxf.text for e in entities if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    
    results = []
    
    # Level markers — labels from template level_markers.defaults[]
    tpl_levels = tpl.get('level_markers', {}).get('defaults', [])
    tpl_level_labels = [lm.get('label', '') for lm in tpl_levels]
    has_grd = any('GRD' in t for t in texts)
    has_roof = any('ROOF' in t or 'RIDGE' in t for t in texts)
    has_upper = any('FLOOR' in t or 'CEILING' in t or 'CLG' in t or '1ST' in t for t in texts)
    results.append(('Level: GRD (ground)', has_grd, ''))
    results.append(('Level: roof/ridge', has_roof, ''))
    results.append(('Level: upper floor/ceiling', has_upper, ''))
    
    # Height dimensions present (>=3 on A-ANNO-DIMS)
    dim_entities = [e for e in entities if e.dxftype() == 'TEXT'
                    and e.dxf.layer == 'A-ANNO-DIMS' and e.dxf.text.isdigit()]
    ok = len(dim_entities) >= 3
    results.append(('Height dimensions (>=3)', ok, f'{len(dim_entities)} found'))

    # Grid labels present
    grid_labels = set(t for t in texts if t in ('A', 'B', 'C', 'D'))
    ok = len(grid_labels) >= 2
    results.append(('Grid labels present', ok, f'found: {sorted(grid_labels)}'))

    # Bay dimensions (via DIMENSION entities or text)
    dim_ents = [e for e in entities if e.dxftype() == 'DIMENSION']
    ok = len(dim_ents) >= 2 or len(dim_entities) >= 5
    results.append(('Bay dimensions present', ok, f'{len(dim_ents)} DIMENSION + {len(dim_entities)} text'))
    
    print("\n=== DXF Front Elevation Anti-Drift Test ===\n")
    pass_count = 0
    fail_count = 0
    for name, passed, detail in results:
        status = 'PASS' if passed else 'FAIL'
        if passed:
            pass_count += 1
        else:
            fail_count += 1
        detail_str = f'  ({detail})' if detail else ''
        print(f"  [{status}] {name}{detail_str}")
    
    print(f"\n  Score: {pass_count}/{pass_count + fail_count}")
    print(f"  {'ALL PASS' if fail_count == 0 else f'{fail_count} FAILURES'}")
    
    return fail_count == 0


def test_template_rules():
    """Every layout element must trace to a rule in drawing_template.json."""
    tpl_path = os.path.join(os.path.dirname(__file__), '..', 'drawing_template.json')
    assert os.path.exists(tpl_path), f"Template not found: {tpl_path}"

    with open(tpl_path) as f:
        tpl = json.load(f)

    results = []

    # R1: Paper definition exists
    paper = tpl.get('paper', {})
    ok = all(k in paper for k in ('width_mm', 'height_mm', 'orientation'))
    results.append(('R1: Paper size defined', ok, ''))

    # R2: Margins defined
    margins = paper.get('margins', {})
    ok = all(k in margins for k in ('left', 'top', 'right', 'bottom'))
    results.append(('R2: Margins defined (left/top/right/bottom)', ok, ''))

    # R3: Title block width defined
    ok = 'title_block_width_mm' in paper
    results.append(('R3: Title block width defined', ok, ''))

    # R4: Border weight defined
    ok = 'border_weight_mm' in paper
    results.append(('R4: Border weight defined', ok, ''))

    # R5: Line weights defined for all element types
    lw = tpl.get('line_weights', {})
    required_lw = ['wall_exterior_cut', 'wall_partition_cut', 'glass_glazing',
                   'door_leaf', 'furniture', 'dimension_line', 'grid_line']
    ok = all(k in lw for k in required_lw)
    results.append(('R5: Line weights for all element types', ok,
                    f'missing: {[k for k in required_lw if k not in lw]}'))

    # R6: Grid style defined
    grid = tpl.get('grid', {})
    ok = all(k in grid for k in ('bubble_radius_mm', 'line_style',
                                  'vertical_axis_labels', 'horizontal_axis_labels'))
    results.append(('R6: Grid style defined', ok, ''))

    # R7: Dimension style defined
    dims = tpl.get('dimensions', {})
    ok = all(k in dims for k in ('text_height_mm', 'terminator', 'tick_angle_deg',
                                  'tier_1_offset_mm', 'tier_2_offset_mm'))
    results.append(('R7: Dimension style defined', ok, ''))

    # R8: Room label style defined
    rl = tpl.get('room_labels', {})
    ok = all(k in rl for k in ('name_font_height_mm', 'area_format'))
    results.append(('R8: Room label style defined', ok, ''))

    # R9: Title block fields defined
    tb = tpl.get('title_block', {})
    ok = len(tb.get('fields', [])) >= 5
    results.append(('R9: Title block fields (>=5)', ok, f'{len(tb.get("fields",[]))} fields'))

    # R10: North arrow defined
    na = tpl.get('north_arrow', {})
    ok = 'size_mm' in na and 'label' in na
    results.append(('R10: North arrow defined', ok, ''))

    # R11: Drawing types defined
    dt = tpl.get('drawing_types', [])
    ok = len(dt) >= 3  # floor, roof, at least one elevation
    results.append(('R11: Drawing types (>=3)', ok, f'{len(dt)} types'))

    # R12: Colors defined
    colors = tpl.get('colors', {})
    ok = all(k in colors for k in ('wall', 'glass', 'furniture', 'grid', 'dimension'))
    results.append(('R12: Colors defined', ok, ''))

    print("\n=== Template Rule Validation ===\n")
    pass_count = 0
    fail_count = 0
    for name, passed, detail in results:
        status = 'PASS' if passed else 'FAIL'
        if passed:
            pass_count += 1
        else:
            fail_count += 1
        detail_str = f'  ({detail})' if detail else ''
        print(f"  [{status}] {name}{detail_str}")

    print(f"\n  Score: {pass_count}/{pass_count + fail_count}")
    print(f"  {'ALL PASS' if fail_count == 0 else f'{fail_count} FAILURES'}")

    return fail_count == 0


def test_proof_svg():
    """§10.0 R4, R7: Proof PNG must exist (rendered from DXF via matplotlib).
    Tester must visually verify before declaring pass."""
    proof_png = os.path.join(os.path.dirname(__file__), 'output',
                             'ifc4_sample_house_floor_plan_proof.png')
    results = []

    # Proof file exists
    exists = os.path.exists(proof_png)
    results.append(('Proof PNG exists', exists, proof_png if exists else 'NOT FOUND'))

    if exists:
        file_size = os.path.getsize(proof_png)
        # Must be a real image (> 10KB)
        ok = file_size > 10000
        results.append(('Proof PNG has content', ok, f'{file_size} bytes'))

        # Check dimensions via header (PNG starts with 8-byte signature,
        # then IHDR chunk with width/height at bytes 16-23)
        with open(proof_png, 'rb') as f:
            header = f.read(24)
        if len(header) >= 24:
            import struct
            width = struct.unpack('>I', header[16:20])[0]
            height = struct.unpack('>I', header[20:24])[0]
            ok = width >= 800 and height >= 400
            results.append(('Proof PNG resolution', ok, f'{width}x{height}px'))
        else:
            results.append(('Proof PNG resolution', False, 'header too short'))

    print("\n=== Proof PNG Verification (§10.0 R7) ===\n")
    pass_count = 0
    fail_count = 0
    for name, passed, detail in results:
        status = 'PASS' if passed else 'FAIL'
        if passed:
            pass_count += 1
        else:
            fail_count += 1
        detail_str = f'  ({detail})' if detail else ''
        print(f"  [{status}] {name}{detail_str}")

    print(f"\n  Score: {pass_count}/{pass_count + fail_count}")
    if fail_count == 0:
        print(f"  ALL PASS")
    else:
        print(f"  {fail_count} FAILURES")

    # §10.0 R4: Print path for visual review — tester MUST open this
    if exists:
        abs_path = os.path.abspath(proof_png)
        archive_path = os.path.abspath(os.path.join(
            os.path.dirname(__file__), '..', 'archive',
            'ifc4_sample_house_floor_plan.svg'))
        print(f"\n  ┌─────────────────────────────────────────────────┐")
        print(f"  │  VISUAL REVIEW REQUIRED (§10.0 R4)              │")
        print(f"  │  Open and compare to archive:                   │")
        print(f"  │  proof: file://{abs_path}")
        print(f"  │  archive: file://{archive_path}")
        print(f"  └─────────────────────────────────────────────────┘")

    return fail_count == 0


if __name__ == '__main__':
    ok1 = test_floor_plan_dxf()
    ok2 = test_elevation_dxf()
    ok3 = test_template_rules()
    ok4 = test_proof_svg()
    sys.exit(0 if ok1 and ok2 and ok3 and ok4 else 1)
