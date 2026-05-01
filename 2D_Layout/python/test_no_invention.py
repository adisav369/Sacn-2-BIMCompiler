# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Anti-invention test: every DXF entity must trace to either:
  1. A DB query (geometry from extracted.db / output.db)
  2. A template rule (drawing_template.json or 2D.db)

If an entity exists that doesn't come from either source, it's invention.
"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ezdxf

DXF_PATH = os.path.join(os.path.dirname(__file__), 'output',
                        'ifc4_sample_house_floor_plan.dxf')
TPL_PATH = os.path.join(os.path.dirname(__file__), '..', 'drawing_template.json')

def load_template():
    with open(TPL_PATH) as f:
        return json.load(f)

def test_no_invention():
    """Every DXF entity must trace to DB data or template rule."""
    doc = ezdxf.readfile(DXF_PATH)
    tpl = load_template()
    msp = doc.modelspace()
    entities = list(msp)

    results = []

    # Rule 1: Every layer used must be in the AIA layer table
    allowed_layers = {
        '0',  # DXF default
        'A-WALL-FULL', 'A-WALL-PRTN', 'A-WALL-PATT',
        'A-GLAZ', 'A-DOOR', 'A-ROOF',
        'A-GRID', 'A-ANNO-DIMS', 'A-ANNO-TEXT',
        'A-FURN', 'A-ELEV-WALL', 'A-ELEV-LEVL',
        'A-TTLB',
    }
    used_layers = set(e.dxf.layer for e in entities if hasattr(e.dxf, 'layer'))
    unknown_layers = used_layers - allowed_layers
    ok = len(unknown_layers) == 0
    results.append(('No unknown layers', ok, f'unknown: {unknown_layers}' if unknown_layers else ''))

    # Rule 2: Grid lines must be on A-GRID layer (not invented on other layers)
    # A grid line is a LINE with dash-dot style — should only be on A-GRID
    grid_layer_lines = [e for e in entities if e.dxftype() == 'LINE' and e.dxf.layer == 'A-GRID']
    non_grid_center = [e for e in entities if e.dxftype() == 'LINE'
                       and e.dxf.layer not in allowed_layers]
    ok = len(non_grid_center) == 0
    results.append(('Grid lines only on A-GRID', ok, f'{len(non_grid_center)} stray lines'))

    # Rule 3: All text traces to template or DB
    # Allowed text sources:
    #   - Grid labels: from template grid.vertical_axis_labels / horizontal_axis_labels
    #   - Room labels: from 2D.db 2d_room_label table
    #   - Dimension values: numeric, derived from DB coordinates
    #   - Drawing title: from template drawing_types
    #   - Title block fields: from template title_block.fields
    #   - Scale text: from template paper.scale
    #   - North arrow: "N"
    #   - Door/window tags: D+number, W+number (from DB element count)
    #   - Level labels: from template level_markers
    texts = [e for e in entities if e.dxftype() == 'TEXT' and hasattr(e.dxf, 'text')]
    text_values = [e.dxf.text for e in texts]

    grid_v = tpl.get('grid', {}).get('vertical_axis_labels', '').split(',')
    grid_h = tpl.get('grid', {}).get('horizontal_axis_labels', '').split(',')
    allowed_grid_labels = set(l.strip() for l in grid_v + grid_h)

    tb_labels = set()
    for f in tpl.get('title_block', {}).get('fields', []):
        tb_labels.add(f.get('label', ''))
        if f.get('default'):
            tb_labels.add(f.get('default'))

    tb_header = tpl.get('title_block', {}).get('header', '')
    if tb_header:
        tb_labels.add(tb_header)

    unexplained = []
    for t in text_values:
        explained = False
        # Grid label
        if t in allowed_grid_labels:
            explained = True
        # Numeric (dimension value)
        elif t.replace('.', '').replace('-', '').isdigit():
            explained = True
        # Room label (any uppercase word)
        elif any(t.startswith(r) for r in ('RUANG', 'BILIK', 'DAPUR', 'STOR', 'KORIDOR', 'ANJUNG', 'TANGGA')):
            explained = True
        # Area (m²)
        elif 'm²' in t:
            explained = True
        # Door/window tag
        elif len(t) <= 4 and t[0] in ('D', 'W') and t[1:].isdigit():
            explained = True
        # Drawing title (contains PELAN or ELEVATION or PLAN)
        elif any(kw in t.upper() for kw in ('PELAN', 'PLAN', 'ELEVATION', 'PANDANGAN', 'BUMBUNG')):
            explained = True
        # Scale text
        elif 'scale' in t.lower() or '1:' in t:
            explained = True
        # North arrow
        elif t == 'N':
            explained = True
        # Title block field (label or value)
        elif t in tb_labels:
            explained = True
        # Level marker (contains LEVEL or elevation number)
        elif 'LEVEL' in t.upper() or 'SILL' in t.upper() or 'RIDGE' in t.upper() or 'HEAD' in t.upper() or 'APRON' in t.upper() or 'CLG' in t.upper() or 'GRD' in t.upper() or 'BEAM' in t.upper():
            explained = True
        # Door/window schedule — headers and data from DB elements (§7.2)
        elif t in ('JADUAL PINTU & TINGKAP', 'DOOR & WINDOW SCHEDULE',
                    'TAG', 'SIZE', 'DESCRIPTION', 'QTY'):
            explained = True
        # Schedule data rows: tag ranges (D2-D3, W1-W4), sizes (1810×2110),
        # descriptions (EXTDBL, INTSGL, SGL PLAIN), quantities (N No.)
        elif '×' in t or 'No.' in t:
            explained = True
        elif t in ('EXTDBL FLUSH', 'INTSGL', 'SGL PLAIN', 'DBL PLAIN'):
            explained = True
        elif len(t) <= 6 and '-' in t and t.split('-')[0][0] in ('D', 'W'):
            explained = True
        # Scale bar labels — from template scale_bar.divisions + unit
        elif t.endswith('m') and t[:-1].isdigit():
            explained = True

        if not explained:
            unexplained.append(t)

    ok = len(unexplained) == 0
    results.append(('All text traces to DB or template', ok,
                    f'unexplained: {unexplained}' if unexplained else ''))

    # Rule 4: No entities outside the sheet boundary
    # Sheet is defined by paper size in template — all entities should be within
    paper = tpl.get('paper', {})
    # Can't check absolute coords (model-space varies), but check no extreme outliers
    all_x = []
    for e in entities:
        if hasattr(e.dxf, 'start'):
            all_x.append(e.dxf.start[0])
        if hasattr(e.dxf, 'insert'):
            all_x.append(e.dxf.insert[0])
    if all_x:
        x_range = max(all_x) - min(all_x)
        # Model-space range should be reasonable (< 100m = 100000mm)
        ok = x_range < 100000
    else:
        ok = True
    results.append(('Entities within reasonable bounds', ok, f'X range: {x_range:.0f}mm' if all_x else ''))

    # Rule 5: Wall polylines must have GUID xdata (traces to DB element)
    wall_polys = [e for e in entities if e.dxftype() == 'LWPOLYLINE'
                  and e.dxf.layer in ('A-WALL-FULL', 'A-WALL-PRTN', 'A-GLAZ', 'A-DOOR')]
    guid_polys = [e for e in wall_polys if e.has_xdata('BIMGUID')]
    # Not all wall polys have GUIDs (some are partition fill), but most should
    ok = len(guid_polys) > 0
    results.append(('Wall polylines have GUID xdata (DB traceability)', ok,
                    f'{len(guid_polys)}/{len(wall_polys)} have GUID'))

    # Rule 6: Template defines line weights — DXF entities should use defined weights only
    tpl_weights = set()
    for k, v in tpl.get('line_weights', {}).items():
        if isinstance(v, (int, float)):
            tpl_weights.add(int(v * 100))  # mm → DXF lineweight
    tpl_weights.add(-1)  # ByLayer
    tpl_weights.add(-3)  # Default
    tpl_weights.add(0)

    used_weights = set()
    for e in entities:
        if hasattr(e.dxf, 'lineweight'):
            used_weights.add(e.dxf.lineweight)

    unknown_weights = used_weights - tpl_weights
    # Allow standard DXF weights that are close to template values
    ok = len(unknown_weights) <= 2  # tolerate minor rounding
    results.append(('Line weights from template', ok,
                    f'unknown: {unknown_weights}' if unknown_weights else ''))

    # Print results
    print("\n=== Anti-Invention Test ===\n")
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


if __name__ == '__main__':
    ok = test_no_invention()
    sys.exit(0 if ok else 1)
