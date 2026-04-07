#!/usr/bin/env python3
"""
drawing_writer_dxf.py — DXF output for 2D architectural drawings.

Reads the compiled BIM database (output.db) and writes professional DXF files
using ezdxf. Coordinates are in model-space mm (no PAPER_FACTOR scaling).

Spec: 2D_ARCHITECTURAL_LAYOUT.md §14
Witnesses: W-2D-ROUNDTRIP (partial — DXF exists for overlay diff)

Usage:
    python3 drawing_writer_dxf.py lib/input/ifc4_sample_house.db --floor-plan
    python3 drawing_writer_dxf.py lib/input/ifc4_sample_house.db --elevation front
    python3 drawing_writer_dxf.py lib/input/ifc4_sample_house.db --all

Output: DXF R2010 files in python/output/  (open in FreeCAD, QCAD, AutoCAD, LibreCAD)
"""

import sys
import os
import math
import sqlite3
import argparse
import datetime
from typing import List, Tuple, Optional

import ezdxf
from ezdxf.enums import TextEntityAlignment

# Add parent dir so we can import section_cut and drawing_writer helpers
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from section_cut import section_cut as run_section_cut, parse_vertices_blob, parse_faces_blob
from drawing_writer import (
    read_elements, derive_grids, snap_grids, detect_levels, _convex_hull_2d,
    roof_silhouette, generate_dimensions, format_dim,
    DimString, SNAP_MODULE,
    infer_rooms, find_host_wall, _get_room_side_ew, _get_room_side_ns,
    read_drawing_metadata,
)

# ─────────────────────────────────────────────────────────────────
# CONSTANTS — only unit conversions and DB-sourced elevations here.
# All formatting (line weights, text heights, colors, styles) comes
# from drawing_template.json at runtime per spec §1 R4.
# ─────────────────────────────────────────────────────────────────

SCALE       = 100     # 1:100 drawing scale
MM          = 1000.0  # world metres × MM → model-space mm

# Level markers — DB-sourced reference elevations (metres)
APRON_Z = -0.100    # concrete apron
GRD_Z   = -0.250    # natural ground


def _hex_to_aci(hex_color: str) -> int:
    """Map template hex color to nearest DXF ACI (AutoCAD Color Index).
    Spec §8: all colors from template colors.*"""
    _MAP = {
        '#000000': 7,    # black (ACI 7 = black on dark bg, white on light)
        '#4488CC': 5,    # blue/cyan → ACI 5
        '#AAAAAA': 9,    # grey → ACI 9
        '#888888': 8,    # grey → ACI 8
        '#CCCCCC': 254,  # light grey → ACI 254
        '#F0F0F0': 254,  # very light grey
        '#FFFFFF': 7,    # white
    }
    return _MAP.get(hex_color, 7)


def _linestyle_to_dxf(style_name: str) -> str:
    """Map template line_styles name to DXF linetype name.
    Spec §4.3: grid line style from template grid.line_style."""
    _MAP = {
        'solid':    'CONTINUOUS',
        'dashed':   'HIDDEN',
        'dash_dot': 'DASHDOT',
        'dotted':   'DOT',
        'center':   'CENTER',
    }
    return _MAP.get(style_name, 'CONTINUOUS')


def _build_layers(tpl: dict) -> list:
    """Build AIA layer table from template colors and line styles.
    Spec §1 R4 + §8: layer colors from template, not hardcoded."""
    colors = tpl.get('colors', {})
    ls = tpl.get('line_styles', {})
    grid_style = _linestyle_to_dxf(ls.get('grid_line', 'dash_dot'))
    return [
        ('A-WALL-FULL', _hex_to_aci(colors.get('wall', '#000000')),       'CONTINUOUS'),
        ('A-WALL-PRTN', _hex_to_aci(colors.get('wall', '#000000')),       'CONTINUOUS'),
        ('A-WALL-PATT', _hex_to_aci(colors.get('column_fill', '#CCCCCC')),'CONTINUOUS'),
        ('A-GLAZ',      _hex_to_aci(colors.get('glass', '#4488CC')),      'CONTINUOUS'),
        ('A-DOOR',      _hex_to_aci(colors.get('wall', '#000000')),       'CONTINUOUS'),
        ('A-ROOF',      _hex_to_aci(colors.get('wall', '#000000')),       'CONTINUOUS'),
        ('A-GRID',      _hex_to_aci(colors.get('grid', '#888888')),       grid_style),
        ('A-ANNO-DIMS', _hex_to_aci(colors.get('dimension', '#000000')),  'CONTINUOUS'),
        ('A-ANNO-TEXT',  _hex_to_aci(colors.get('label', '#000000')),     'CONTINUOUS'),
        ('A-FURN',      _hex_to_aci(colors.get('furniture', '#AAAAAA')),  'CONTINUOUS'),
        ('A-ELEV-WALL', _hex_to_aci(colors.get('wall', '#000000')),      'CONTINUOUS'),
        ('A-ELEV-LEVL', _hex_to_aci(colors.get('dimension', '#000000')), 'CONTINUOUS'),
        ('A-TTLB',      _hex_to_aci(colors.get('wall', '#000000')),      'CONTINUOUS'),
    ]


def _hexagon_pts(cx: float, cy: float, r: float) -> list:
    """Return 6 vertices of a flat-top hexagon centered at (cx, cy) with radius r.
    Spec §7.3: annotation_tags.shape = 'hexagon'. Matches archive SVG hexagons."""
    pts = []
    for i in range(6):
        angle = math.radians(60 * i + 30)  # flat-top: offset by 30°
        pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    return pts


def _lw(tpl: dict, key: str) -> int:
    """Read line weight from template, return DXF lineweight integer.
    Spec §1 R4: all line weights from template line_weights.*"""
    return int(tpl.get('line_weights', {}).get(key, 0.18) * 100)


def _txt_h(tpl: dict, section: str, key: str, fallback: float = 2.5) -> float:
    """Read text height (paper mm) from template section.
    Spec §7.3: all annotation placement from template."""
    return tpl.get(section, {}).get(key, fallback)

# ─────────────────────────────────────────────────────────────────
# DOC SETUP
# ─────────────────────────────────────────────────────────────────

def _new_doc(tpl: dict, scale: int = SCALE) -> ezdxf.document.Drawing:
    """Create a new DXF R2010 document with template-driven layers and dimstyle.
    Spec §1 R4: template governs all formatting."""
    doc = ezdxf.new('R2010')

    # Units: mm
    doc.header['$INSUNITS'] = 4
    doc.header['$LTSCALE'] = scale / 100.0
    doc.header['$MEASUREMENT'] = 1  # metric

    # Load standard linetypes from template line_styles
    doc.linetypes.add('CENTER',  [0.0], description='Center __ . __ . __')
    doc.linetypes.add('HIDDEN',  [0.0], description='Hidden -- -- -- --')
    doc.linetypes.add('DASHDOT', [0.0], description='Dash dot __.__.__')

    # AIA layers — colors and linetypes from template (§8)
    for name, color, ltype in _build_layers(tpl):
        if ltype not in ('CONTINUOUS',):
            try:
                doc.layers.add(name=name, color=color, linetype=ltype)
            except Exception:
                doc.layers.add(name=name, color=color)
        else:
            doc.layers.add(name=name, color=color)

    # ARCH_JKR dimstyle — all values from template dimensions.* (§6.3)
    tpl_dims = tpl.get('dimensions', {})
    try:
        ds = doc.dimstyles.new('ARCH_JKR')
        ds.dxf.dimscale = scale
        ds.dxf.dimtxt   = tpl_dims.get('text_height_mm', 2.5)
        ds.dxf.dimblk   = 'ARCHTICK'    # 45° tick per template terminator=tick
        ds.dxf.dimexo   = tpl_dims.get('extension_gap_mm', 2.0)
        ds.dxf.dimexe   = tpl_dims.get('extension_overshoot_mm', 2.0)
        ds.dxf.dimdle   = 0.0
        ds.dxf.dimgap   = 1.0
    except Exception:
        pass

    return doc


def _mh(metres: float) -> float:
    """Convert metres to DXF model-space mm."""
    return metres * MM


# ─────────────────────────────────────────────────────────────────
# TEMPLATE LOADER
# ─────────────────────────────────────────────────────────────────

import json

def _load_template() -> dict:
    """Load drawing_template.json — user-editable drawing CSS."""
    tpl_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            '..', 'drawing_template.json')
    if os.path.exists(tpl_path):
        with open(tpl_path) as f:
            return json.load(f)
    return {}


def _build_schedule(elements: dict) -> list:
    """Build door & window schedule rows from elements.
    Groups openings by size, returns list of dicts for the schedule table.
    Spec §7.2: archive has JADUAL PINTU & TINGKAP in title block."""
    from collections import defaultdict
    doors = elements.get('doors', [])
    windows = elements.get('windows', [])

    # Group doors by size (width × height in mm, rounded)
    door_groups = defaultdict(list)
    for i, d in enumerate(doors, 1):
        w_mm = round(d.width_x * 1000)
        h_mm = round((d.max_z - d.min_z) * 1000) if hasattr(d, 'max_z') else round(d.width_y * 1000)
        key = (w_mm, h_mm)
        door_groups[key].append(f'D{i}')

    win_groups = defaultdict(list)
    for i, w in enumerate(windows, 1):
        w_mm = round(w.width_x * 1000)
        h_mm = round((w.max_z - w.min_z) * 1000) if hasattr(w, 'max_z') else round(w.width_y * 1000)
        key = (w_mm, h_mm)
        win_groups[key].append(f'W{i}')

    rows = []
    for (w_mm, h_mm), tags in sorted(door_groups.items(), key=lambda x: x[1][0]):
        tag_str = tags[0] if len(tags) == 1 else f'{tags[0]}-{tags[-1]}'
        desc = 'EXTDBL FLUSH' if w_mm > 1500 else 'INTSGL'
        rows.append({'tag': tag_str, 'size': f'{w_mm}\u00d7{h_mm}',
                     'desc': desc, 'qty': f'{len(tags)} No.'})
    for (w_mm, h_mm), tags in sorted(win_groups.items(), key=lambda x: x[1][0]):
        tag_str = tags[0] if len(tags) == 1 else f'{tags[0]}-{tags[-1]}'
        rows.append({'tag': tag_str, 'size': f'{w_mm}\u00d7{h_mm}',
                     'desc': 'SGL PLAIN', 'qty': f'{len(tags)} No.'})
    return rows


def _draw_sheet_layout(doc, msp, tpl, bld_min_x, bld_max_x, bld_min_y, bld_max_y,
                       drawing_title='', drawing_no='', scale=SCALE, schedule_rows=None,
                       view_type='plan'):
    """Draw sheet border, white background, title block panel, north arrow.

    All values from drawing_template.json — no hardcoded layout.

    Rules (from ISO 128 + JKR Malaysian practice):
      R1: White background — paper is white, all drawing on top
      R2: Sheet border — rectangle at margin boundaries, bold line (0.7mm)
      R3: Content area — left of title block, building centered within
      R4: Title block — right-side panel, vertical separator, field rows
          Fields: label column (30%) | value column (70%), bottom-up order
          Header text centered near top of panel
      R5: North arrow — triangle + "N" label, top-right of content area
      R6: All layout dimensions from drawing_template.json, user-editable
    """
    paper = tpl.get('paper', {})
    pw = paper.get('width_mm', 420) * scale   # paper mm → model-space mm
    ph = paper.get('height_mm', 297) * scale
    ml = paper.get('margins', {}).get('left', 25) * scale
    mt = paper.get('margins', {}).get('top', 10) * scale
    mr = paper.get('margins', {}).get('right', 10) * scale
    mb = paper.get('margins', {}).get('bottom', 10) * scale
    tb_w = paper.get('title_block_width_mm', 120) * scale
    bw = int(paper.get('border_weight_mm', 0.7) * 100)  # mm → DXF lineweight

    # Center building in content area (left of title block)
    content_w = pw - ml - mr - tb_w
    content_h = ph - mt - mb
    bld_w = bld_max_x - bld_min_x
    bld_h = bld_max_y - bld_min_y

    # Sheet origin: position so building is centered in content area
    sheet_x = bld_min_x - ml - max(0, (content_w - bld_w) / 2)
    sheet_y = bld_min_y - mb - max(0, (content_h - bld_h) / 2)

    sx0 = sheet_x
    sy0 = sheet_y
    sx1 = sheet_x + pw
    sy1 = sheet_y + ph

    # White background rectangle (layer 0, color 7=white)
    bg_pts = [(sx0, sy0), (sx1, sy0), (sx1, sy1), (sx0, sy1)]
    bg = msp.add_lwpolyline(bg_pts, close=True,
                            dxfattribs={'layer': '0', 'color': 7, 'lineweight': -1})

    # Sheet border
    bx0 = sx0 + ml
    by0 = sy0 + mb
    bx1 = sx1 - mr
    by1 = sy1 - mt
    border_pts = [(bx0, by0), (bx1, by0), (bx1, by1), (bx0, by1)]
    msp.add_lwpolyline(border_pts, close=True,
                       dxfattribs={'layer': 'A-TTLB', 'lineweight': bw})

    # Title block panel — vertical line separating content from panel
    tb_left = bx1 - tb_w
    msp.add_line((tb_left, by0), (tb_left, by1),
                 dxfattribs={'layer': 'A-TTLB', 'lineweight': bw})

    # Line weight for title block internal lines — from template
    lw_tb = _lw(tpl, 'dimension_line')  # thin separator lines

    # Title block fields from template
    title_block = tpl.get('title_block', {})
    fields = title_block.get('fields', [])
    label_ratio = title_block.get('label_column_ratio', 0.30)
    lbl_h = title_block.get('font_height_label_mm', 2.0) * scale
    val_h = title_block.get('font_height_value_mm', 3.0) * scale
    n_fields = len(fields)
    # Row height: divide available panel height equally among fields
    panel_h = by1 - by0
    header_zone = val_h * 4  # space for header text at top
    row_h = (panel_h - header_zone) / max(n_fields, 1)

    # Header text
    header = title_block.get('header', '')
    if header:
        hx = tb_left + tb_w / 2
        hy = by1 - header_zone / 2
        msp.add_text(header,
                     dxfattribs={'layer': 'A-TTLB', 'height': lbl_h}
                     ).set_placement((hx, hy), align=TextEntityAlignment.MIDDLE_CENTER)

    # Door & window schedule (between header and field rows)
    if schedule_rows:
        sch_top = by1 - header_zone  # below header zone
        sch_row_h = lbl_h * 3.5      # row height for schedule
        sch_hdr_h = lbl_h * 2.5      # schedule title height
        sch_small = lbl_h * 0.9      # small text for descriptions
        # Schedule title
        sch_y = sch_top
        msp.add_line((tb_left, sch_y), (bx1, sch_y),
                     dxfattribs={'layer': 'A-TTLB', 'lineweight': lw_tb})
        msp.add_text('JADUAL PINTU & TINGKAP',
                     dxfattribs={'layer': 'A-TTLB', 'height': val_h * 0.85}
                     ).set_placement((hx, sch_y - sch_hdr_h * 0.35),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        msp.add_text('DOOR & WINDOW SCHEDULE',
                     dxfattribs={'layer': 'A-TTLB', 'height': sch_small,
                                 'color': 8}
                     ).set_placement((hx, sch_y - sch_hdr_h * 0.8),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        sch_y -= sch_hdr_h
        msp.add_line((tb_left, sch_y), (bx1, sch_y),
                     dxfattribs={'layer': 'A-TTLB', 'lineweight': lw_tb})
        # Column positions (TAG | SIZE | DESCRIPTION | QTY)
        col_x = [tb_left + 4 * scale,
                 tb_left + tb_w * 0.18,
                 tb_left + tb_w * 0.43,
                 tb_left + tb_w * 0.82]
        # Column headers
        for label, cx in zip(['TAG', 'SIZE', 'DESCRIPTION', 'QTY'], col_x):
            msp.add_text(label,
                         dxfattribs={'layer': 'A-TTLB', 'height': sch_small}
                         ).set_placement((cx, sch_y - sch_row_h * 0.5),
                                         align=TextEntityAlignment.LEFT)
        sch_y -= sch_row_h
        msp.add_line((tb_left, sch_y), (bx1, sch_y),
                     dxfattribs={'layer': 'A-TTLB', 'lineweight': lw_tb,
                                 'color': 8})
        # Data rows
        for row in schedule_rows:
            vals = [row['tag'], row['size'], row['desc'], row['qty']]
            heights = [lbl_h, lbl_h, sch_small, lbl_h]
            for val, cx, th in zip(vals, col_x, heights):
                msp.add_text(val,
                             dxfattribs={'layer': 'A-TTLB', 'height': th}
                             ).set_placement((cx, sch_y - sch_row_h * 0.5),
                                             align=TextEntityAlignment.LEFT)
            sch_y -= sch_row_h
        msp.add_line((tb_left, sch_y), (bx1, sch_y),
                     dxfattribs={'layer': 'A-TTLB', 'lineweight': lw_tb,
                                 'color': 8})

    # Field rows from bottom up
    y_cursor = by0
    div_x = tb_left + tb_w * label_ratio
    for field in reversed(fields):
        msp.add_line((tb_left, y_cursor), (bx1, y_cursor),
                     dxfattribs={'layer': 'A-TTLB', 'lineweight': lw_tb})
        msp.add_line((div_x, y_cursor), (div_x, y_cursor + row_h),
                     dxfattribs={'layer': 'A-TTLB', 'lineweight': lw_tb})
        label_x = tb_left + tb_w * label_ratio / 2
        label_y = y_cursor + row_h / 2
        msp.add_text(field.get('label', ''),
                     dxfattribs={'layer': 'A-TTLB', 'height': lbl_h}
                     ).set_placement((label_x, label_y),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        value = field.get('default', '')
        if field.get('key') == 'TAJUK_LUKISAN' and drawing_title:
            value = drawing_title
        if field.get('key') == 'NO_LUKISAN' and drawing_no:
            value = drawing_no
        if value:
            value_x = div_x + (tb_w * (1 - label_ratio)) / 2
            msp.add_text(value,
                         dxfattribs={'layer': 'A-TTLB', 'height': val_h}
                         ).set_placement((value_x, label_y),
                                         align=TextEntityAlignment.MIDDLE_CENTER)
        y_cursor += row_h

    # North arrow — §5.0b: plans only, not elevations
    if view_type in ('plan', 'roof_plan'):
        na = tpl.get('north_arrow', {})
        na_size = na.get('size_mm', 8) * scale
        na_font = na.get('font_height_mm', 3.0) * scale
        # Placement: top-right of content area, offset by arrow size from edges
        na_x = tb_left - na_size * 2
        na_y = by1 - na_size * 3
        tri_pts = [(na_x, na_y + na_size),
                   (na_x - na_size / 3, na_y),
                   (na_x + na_size / 3, na_y)]
        msp.add_lwpolyline(tri_pts, close=True,
                           dxfattribs={'layer': 'A-ANNO-TEXT', 'lineweight': lw_tb})
        msp.add_text(na.get('label', 'N'),
                     dxfattribs={'layer': 'A-ANNO-TEXT', 'height': na_font}
                     ).set_placement((na_x, na_y + na_size + na_font),
                                     align=TextEntityAlignment.MIDDLE_CENTER)

    # Scale text — §5.0 universal sheet furniture
    scale_str = tpl.get('paper', {}).get('scale', '1:100')
    scale_txt_h = tpl.get('title_block', {}).get('font_height_value_mm', 3.0) * scale
    scale_color = _hex_to_aci(tpl.get('colors', {}).get('scale_text', '#888888'))
    scale_x = (bx0 + tb_left) / 2
    scale_y = by0 + scale_txt_h * 3
    msp.add_text(f'scale {scale_str}',
                 dxfattribs={'layer': 'A-ANNO-TEXT',
                             'height': scale_txt_h, 'color': scale_color}
                 ).set_placement((scale_x, scale_y),
                                 align=TextEntityAlignment.MIDDLE_CENTER)

    # Scale bar — §5.0 conditional (not schedule)
    tpl_sb = tpl.get('scale_bar', {})
    sb_divs = tpl_sb.get('divisions', [0, 1, 2, 5])
    sb_unit = tpl_sb.get('unit', 'm')
    sb_bar_h = tpl_sb.get('bar_height_mm', 2.0) * scale
    sb_font_h = tpl_sb.get('font_height_mm', 2.0) * scale
    sb_y = scale_y - scale_txt_h * 2
    sb_scale_factor = 1000.0 / scale  # 1 metre in model-space mm
    total_extent = (sb_divs[-1] - sb_divs[0]) * sb_scale_factor
    sb_x0 = scale_x - total_extent / 2
    for i in range(len(sb_divs) - 1):
        x_start = sb_x0 + (sb_divs[i] - sb_divs[0]) * sb_scale_factor
        x_end = sb_x0 + (sb_divs[i + 1] - sb_divs[0]) * sb_scale_factor
        fill_color = 7 if i % 2 == 0 else 0
        msp.add_solid(
            [(x_start, sb_y), (x_end, sb_y),
             (x_start, sb_y + sb_bar_h), (x_end, sb_y + sb_bar_h)],
            dxfattribs={'layer': 'A-ANNO-DIMS', 'color': fill_color})
    for d in sb_divs:
        lx = sb_x0 + (d - sb_divs[0]) * sb_scale_factor
        label = f'{d}{sb_unit}' if d == sb_divs[-1] else str(d)
        msp.add_text(label,
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'height': sb_font_h}
                     ).set_placement((lx, sb_y - sb_font_h * 0.8),
                                     align=TextEntityAlignment.MIDDLE_CENTER)


# ─────────────────────────────────────────────────────────────────
# DIAGNOSTIC LOG
# ─────────────────────────────────────────────────────────────────

_LOG_LINES: List[str] = []

def _log(msg: str):
    """Append a diagnostic line (printed + buffered for log file)."""
    print(msg)
    _LOG_LINES.append(msg)


def _audit_dxf(doc, out_dxf: str, view_type: str):
    """Audit a generated DXF and log key metrics for hands-free verification.

    Checks:
      - Entity counts by type
      - Model-space extents (bounding box)
      - Text heights vs model extent (visibility ratio)
      - GUID xdata count
      - Annotation sanity: text_height / model_width should be 1-5%
    """
    msp = doc.modelspace()
    counts = {}
    min_x = min_y = float('inf')
    max_x = max_y = float('-inf')
    text_entries = []          # (text_str, x, y, height)
    guid_count = 0
    guid_set = set()
    # Track geometry-only extent (LWPOLYLINE on wall/elev layers)
    geo_min_x = geo_min_y = float('inf')
    geo_max_x = geo_max_y = float('-inf')

    for e in msp:
        t = e.dxftype()
        counts[t] = counts.get(t, 0) + 1
        if t == 'LWPOLYLINE':
            layer = e.dxf.layer
            for pt in e.get_points(format='xy'):
                min_x = min(min_x, pt[0])
                max_x = max(max_x, pt[0])
                min_y = min(min_y, pt[1])
                max_y = max(max_y, pt[1])
                # Building geometry layers only (not grid/anno)
                if layer.startswith('A-WALL') or layer in (
                        'A-DOOR', 'A-GLAZ', 'A-FURN',
                        'A-ELEV-WALL', 'A-ROOF'):
                    geo_min_x = min(geo_min_x, pt[0])
                    geo_max_x = max(geo_max_x, pt[0])
                    geo_min_y = min(geo_min_y, pt[1])
                    geo_max_y = max(geo_max_y, pt[1])
            try:
                xd = e.get_xdata('BIMGUID')
                if xd:
                    guid_count += 1
                    for tag in xd:
                        if tag[0] == 1000:
                            guid_set.add(tag[1])
            except Exception:
                pass
        elif t == 'LINE':
            min_x = min(min_x, e.dxf.start.x, e.dxf.end.x)
            max_x = max(max_x, e.dxf.start.x, e.dxf.end.x)
            min_y = min(min_y, e.dxf.start.y, e.dxf.end.y)
            max_y = max(max_y, e.dxf.start.y, e.dxf.end.y)
        elif t == 'TEXT':
            txt = e.dxf.text
            h = e.dxf.height
            ix, iy = e.dxf.insert.x, e.dxf.insert.y
            text_entries.append((txt, ix, iy, h))

    width_mm = max_x - min_x if max_x > min_x else 1
    height_mm = max_y - min_y if max_y > min_y else 1
    geo_w = geo_max_x - geo_min_x if geo_max_x > geo_min_x else 0
    geo_h = geo_max_y - geo_min_y if geo_max_y > geo_min_y else 0

    _log(f"  AUDIT [{view_type}] {os.path.basename(out_dxf)}")
    _log(f"    Entities: {dict(sorted(counts.items()))}")
    _log(f"    Total extent: {width_mm:.0f} x {height_mm:.0f} mm  "
         f"({width_mm/1000:.1f} x {height_mm/1000:.1f} m)")
    _log(f"    Building extent: {geo_w:.0f} x {geo_h:.0f} mm  "
         f"({geo_w/1000:.1f} x {geo_h/1000:.1f} m)")
    if width_mm > 0 and height_mm > 0 and geo_w > 0:
        fill_pct = (geo_w * geo_h) / (width_mm * height_mm) * 100
        _log(f"    Building fill: {fill_pct:.0f}% of drawing area")

    text_heights = [h for _, _, _, h in text_entries]
    if text_entries:
        avg_h = sum(text_heights) / len(text_heights)
        ratio = avg_h / width_mm * 100
        _log(f"    Text: {len(text_entries)} entities, "
             f"avg height {avg_h:.0f}mm, "
             f"visibility ratio {ratio:.1f}% of width")
        # Log actual text content for traceability
        for txt, tx, ty, th in text_entries[:8]:  # first 8 only
            _log(f"      \"{txt}\" at ({tx:.0f},{ty:.0f}) h={th:.0f}")
        if len(text_entries) > 8:
            _log(f"      ... and {len(text_entries) - 8} more")
        if ratio < 0.5:
            _log(f"    !! FAIL: text invisible (ratio {ratio:.2f}% < 0.5%)")
        elif ratio > 10:
            _log(f"    !! FAIL: text oversized (ratio {ratio:.1f}% > 10%)")
        else:
            _log(f"    OK: text proportional")
    else:
        _log(f"    Text: NONE")

    # Check DIMENSION entities for text overlap (doubled text bug)
    dim_count = counts.get('DIMENSION', 0)
    if dim_count > 0:
        _log(f"    Dimensions: {dim_count} entities")

    if guid_count > 0:
        _log(f"    GUID xdata: {guid_count} polylines, "
             f"{len(guid_set)} unique GUIDs")

    # ── Verdict ──
    has_geometry = counts.get('LWPOLYLINE', 0) + counts.get('LINE', 0) > 0
    text_ok = True
    detail_parts = []

    if not has_geometry:
        detail_parts.append("NO GEOMETRY")
        text_ok = False

    total_entities = sum(counts.values())
    detail_parts.append(f"{total_entities} entities")
    detail_parts.append(f"bldg {geo_w/1000:.1f}x{geo_h/1000:.1f}m")

    if text_entries:
        avg_h = sum(text_heights) / len(text_heights)
        ratio = avg_h / width_mm * 100
        if ratio < 0.5:
            detail_parts.append(f"TEXT INVISIBLE ({ratio:.2f}%)")
            text_ok = False
        elif ratio > 10:
            detail_parts.append(f"TEXT OVERSIZED ({ratio:.1f}%)")
            text_ok = False
        else:
            detail_parts.append(f"text {ratio:.1f}%")
    else:
        detail_parts.append("no text")

    if guid_count > 0:
        detail_parts.append(f"{len(guid_set)} GUIDs")

    # ── Composition checks (visual issues not caught by entity counts) ──
    composition_warns = []
    is_plan = 'FLOOR' in view_type or 'PLAN' in view_type
    is_elev = 'ELEV' in view_type

    if width_mm > 0 and height_mm > 0 and geo_w > 0:
        fill_ratio = (geo_w * geo_h) / (width_mm * height_mm)
        if fill_ratio < 0.20:
            composition_warns.append(
                f"Building occupies {fill_ratio*100:.0f}% of drawing — "
                f"annotations dominate, building too small")

    if is_elev:
        # Check for excessive empty space below ground line
        grd_y = 0  # GRD. LEVEL at y=0 in model space
        below_grd = abs(min_y - grd_y) if min_y < grd_y else 0
        total_v = height_mm
        if total_v > 0 and below_grd / total_v > 0.25:
            composition_warns.append(
                f"Empty space below ground: {below_grd:.0f}mm = "
                f"{below_grd/total_v*100:.0f}% of drawing height")

        # Check if building is less than 40% of vertical extent
        if geo_h > 0 and geo_h / total_v < 0.40:
            composition_warns.append(
                f"Building height {geo_h:.0f}mm = "
                f"{geo_h/total_v*100:.0f}% of drawing height — "
                f"grid+dim overhead squeezes building")

    if is_plan and geo_h > geo_w * 2.5:
        composition_warns.append(
            f"Building aspect {geo_w/1000:.1f}x{geo_h/1000:.1f}m — "
            f"multi-storey overlap? Consider per-storey plans")

    if composition_warns:
        _log(f"    ── Composition Warnings ──")
        for w in composition_warns:
            _log(f"      !! {w}")

    ok = has_geometry and text_ok
    _VERDICTS.append((view_type, ok, ", ".join(detail_parts)))

    # ── TBLKLTN completeness check (post-process) ──
    # Reads 2D_metadata.db as the reference spec for a professional drawing.
    # Compares DXF actual counts against metadata-defined expectations.
    # Like Rosetta Stone: expected vs actual, quantitative, per feature.
    _log(f"    ── TBLKLTN Completeness (vs 2D.db) ──")
    is_plan = 'FLOOR' in view_type or 'PLAN' in view_type
    is_elev = 'ELEV' in view_type
    text_strs = [e[0] for e in text_entries]

    # Read metadata reference counts
    meta = _read_metadata_ref()

    features = []  # (name, have: bool, actual, expected_desc)
    if is_plan:
        n_poly = counts.get('LWPOLYLINE', 0)
        n_grid = counts.get('LINE', 0)
        n_circ = counts.get('CIRCLE', 0)
        n_furn = sum(1 for e in msp if e.dxftype() == 'LWPOLYLINE'
                     and e.dxf.layer == 'A-FURN')
        n_arcs = sum(1 for e in msp if e.dxftype() == 'ARC'
                     and e.dxf.layer == 'A-DOOR')
        n_wall_fill = sum(1 for e in msp if e.dxf.layer == 'A-WALL-PATT'
                          and e.dxftype() in ('HATCH', 'LWPOLYLINE', 'SOLID'))
        n_ttlb_texts = sum(1 for e in msp if e.dxftype() == 'TEXT'
                           and e.dxf.layer == 'A-TTLB')
        n_scalebar = sum(1 for e in msp if e.dxftype() == 'SOLID'
                         and e.dxf.layer == 'A-ANNO-DIMS')
        has_north = any(t == 'N' for t in text_strs)
        dim_texts = [t for t in text_strs if t.isdigit() and len(t) >= 3]
        tag_texts = [t for t in text_strs
                     if t.startswith(('D', 'W')) and any(c.isdigit() for c in t)]
        grid_labels_found = [t for t in text_strs if len(t) == 1 and (t.isalpha() or t.isdigit())]
        features = [
            ("Wall section polylines",   n_poly > 5,          str(n_poly),     ">5 from section cut"),
            ("Grid lines",               n_grid >= 2,         str(n_grid),     f"≥2 per {meta['grid_axes']} axes"),
            ("Grid bubbles",             n_circ >= 2,         str(n_circ),     f"2× per grid line, r={meta['bubble_r']}mm"),
            ("Grid labels",              len(grid_labels_found) >= 4,
                                                              f"{len(grid_labels_found)} ({','.join(sorted(set(grid_labels_found)))})",
                                                              f"A-{meta['grid_alpha_max']}, 1-{meta['grid_num_max']}"),
            ("GUID xdata",               guid_count >= 5,     str(guid_count), f"≥5 ARC elements"),
            ("Furniture bboxes",          n_furn > 0,          str(n_furn),     "1 per BELOW element"),
            ("Bay dimensions",            len(dim_texts) >= 3,
                                                              str(len(dim_texts)), "1 per grid bay"),
            ("Room labels",               any(t in meta['room_types'] for t in text_strs)
                                          or any('RUANG' in t or 'BILIK' in t for t in text_strs),
                                                              str(sum(1 for t in text_strs if 'RUANG' in t or 'BILIK' in t)),
                                                              f"need: {', '.join(meta['room_types'][:5])}"),
            ("Room areas (m²)",           any('m²' in t or 'm2' in t for t in text_strs),
                                                              str(sum(1 for t in text_strs if 'm²' in t)),
                                                              "per room from spatial data"),
            ("Door swing arcs",           n_arcs >= 3,         str(n_arcs),
                                                              f"{meta['door_symbols']} symbols in 2d_drawing_symbol"),
            ("Wall fill / hatch",         n_wall_fill >= 5,    str(n_wall_fill),
                                                              f"{meta['wall_styles']} styles in 2d_drawing_style"),
            ("Door/window tags",          len(tag_texts) >= 3,
                                                              str(len(tag_texts)), f"tags: {meta['tag_types']}"),
            ("North arrow",               has_north,           "yes" if has_north else "no",
                                                              "NORTH_ARROW in 2d_drawing_symbol"),
            ("Drawing title",             any('PLAN' in t.upper() or 'PELAN' in t.upper() for t in text_strs),
                                                              "yes",           f"'{meta['plan_title']}'"),
            ("Title block",               n_ttlb_texts >= 5,   str(n_ttlb_texts),
                                                              f"{meta['title_fields']} fields in 2d_title_block"),
            ("Scale bar",                 n_scalebar >= 2,     str(n_scalebar),
                                                              "standard bar 0-1-2-5m"),
        ]
    elif is_elev:
        face_key = view_type.split()[-1].lower() if len(view_type.split()) > 1 else 'front'
        elev_title = meta.get(f'elev_{face_key}_title', f'{face_key.upper()} ELEVATION')
        n_solid = counts.get('SOLID', 0)
        n_dim = counts.get('DIMENSION', 0)
        level_labels = [t for t in text_strs if 'LEVEL' in t or 'GRD' in t.split()[0:1]]
        grid_labels_found = [t for t in text_strs if len(t) == 1 and (t.isalpha() or t.isdigit())]
        has_louvre = any(e.dxf.layer == 'A-GLAZ' for e in msp if e.dxftype() == 'LINE')
        has_roof = any(e.dxf.layer == 'A-ROOF' for e in msp if e.dxftype() == 'LWPOLYLINE')
        has_grd_line = any(e.dxf.layer == 'A-ELEV-LEVL' for e in msp if e.dxftype() == 'LINE')
        features = [
            ("Element outlines",          counts.get('LWPOLYLINE', 0) >= 3,
                                                              str(counts.get('LWPOLYLINE', 0)), "walls+doors+windows"),
            ("Grid lines",                counts.get('LINE', 0) >= 3,
                                                              str(counts.get('LINE', 0)), "1 per grid on face"),
            ("Grid bubbles",              counts.get('CIRCLE', 0) >= 2,
                                                              str(counts.get('CIRCLE', 0)), "1 per grid"),
            ("Grid labels",               len(grid_labels_found) >= 2,
                                                              f"{len(grid_labels_found)} ({','.join(sorted(set(grid_labels_found)))})",
                                                              "matching grid axis"),
            ("Level markers (▶)",         n_solid >= 3,        str(n_solid),    f"≥{meta['level_count']} from ad_level_marker"),
            ("Level labels",              len(level_labels) >= 3,
                                                              f"{len(level_labels)} ({', '.join(level_labels[:3])}...)",
                                                              f"{meta['level_count']} from ad_level_marker"),
            ("Bay dimensions",            n_dim > 0,           str(n_dim),      "1 per grid bay"),
            ("Ground line",               has_grd_line,        "yes" if has_grd_line else "no", "A-ELEV-LEVL layer"),
            ("Window louvres",            has_louvre,           "yes" if has_louvre else "no",  "A-GLAZ layer lines"),
            ("Roof silhouette",           has_roof,            "yes" if has_roof else "no",    "A-ROOF layer polyline"),
            ("Drawing title",             any(face_key.upper() in t.upper() for t in text_strs),
                                                              "0",             f"'{elev_title}'"),
            ("Title block",               False,               "0",             f"{meta['title_fields']} fields"),
        ]

    have = sum(1 for _, h, _, _ in features if h)
    for name, present, actual, expected in features:
        tag = "HAVE" if present else "MISS"
        _log(f"      [{tag}] {name}: actual={actual}, ref={expected}")
    pct = have * 100 // len(features) if features else 0
    _log(f"    Score: {have}/{len(features)} features ({pct}%)")
    if _VERDICTS and _VERDICTS[-1][0] == view_type:
        v, o, d = _VERDICTS[-1]
        _VERDICTS[-1] = (v, o, d + f", completeness {have}/{len(features)} ({pct}%)")


def _read_metadata_ref() -> dict:
    """Read 2D.db (2d_* tables) and return reference counts for TBLKLTN comparison.
    This is the spec's truth — what a professional drawing must have."""
    meta_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             '..', 'lib', 'input', '2D.db')
    ref = {
        'grid_axes': 2, 'bubble_r': 4.0,
        'grid_alpha_max': 'D', 'grid_num_max': '3',
        'room_types': [], 'door_symbols': 0, 'wall_styles': 0,
        'tag_types': '', 'plan_title': 'FLOOR PLAN',
        'title_fields': 0, 'level_count': 0,
    }
    if not os.path.exists(meta_path):
        _log(f"    (2D.db not found at {meta_path})")
        return ref
    try:
        conn = sqlite3.connect(meta_path)
        cur = conn.cursor()
        # Room labels
        cur.execute("SELECT DISTINCT label_text FROM [2d_room_label] WHERE language='EN'")
        ref['room_types'] = [r[0] for r in cur.fetchall()]
        # Door/window symbols
        cur.execute("SELECT COUNT(*) FROM [2d_drawing_symbol] WHERE view_type='PLAN' "
                    "AND (symbol_code LIKE 'DOOR%' OR symbol_code LIKE 'WINDOW%')")
        ref['door_symbols'] = cur.fetchone()[0]
        # Wall styles
        cur.execute("SELECT COUNT(*) FROM [2d_drawing_style] WHERE view_type='PLAN' "
                    "AND element_match LIKE '%Wall%'")
        ref['wall_styles'] = cur.fetchone()[0]
        # Annotation tags
        cur.execute("SELECT tag_prefix FROM [2d_annotation_tag]")
        ref['tag_types'] = ', '.join(r[0] for r in cur.fetchall())
        # Drawing titles
        cur.execute("SELECT drawing_title FROM [2d_drawing_type] WHERE drawing_code='FLOOR_PLAN'")
        row = cur.fetchone()
        if row: ref['plan_title'] = row[0]
        for face in ('front', 'rear', 'left', 'right'):
            cur.execute("SELECT drawing_title FROM [2d_drawing_type] WHERE drawing_code=?",
                        (f'{face.upper()}_ELEV',))
            row = cur.fetchone()
            if row: ref[f'elev_{face}_title'] = row[0]
        # Title block
        cur.execute("SELECT COUNT(*) FROM [2d_title_block]")
        ref['title_fields'] = cur.fetchone()[0]
        # Level markers
        cur.execute("SELECT COUNT(*) FROM [2d_level_marker]")
        ref['level_count'] = cur.fetchone()[0]
        # Grid style
        cur.execute("SELECT bubble_radius FROM [2d_grid_style] LIMIT 1")
        row = cur.fetchone()
        if row: ref['bubble_r'] = row[0]
        conn.close()
    except Exception as e:
        _log(f"    (2D.db read failed: {e})")
    return ref


def _read_2d_db_levels() -> dict:
    """Read level markers and reference elevations from 2D.db.
    Returns dict with 'level_labels' mapping (code→display_text),
    'level_elevations' mapping (code→z), and 'roof_labels' dict."""
    result = {
        'level_labels': {},     # e.g. 'FFL' → 'GRD. FLOOR LEVEL'
        'level_elevations': {}, # e.g. 'GRD' → -0.15
        'roof_labels': {'ridge': 'RABUNG / RIDGE', 'eave': 'CUCURAN / EAVE'},
    }
    for try_path in [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'input', '2D.db'),
        os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'lib', 'input', '2D.db'),
    ]:
        if os.path.exists(try_path):
            try:
                conn = sqlite3.connect(try_path)
                cur = conn.cursor()
                cur.execute("SELECT level_code, display_text, typical_z FROM [2d_level_marker]")
                for code, text, z in cur.fetchall():
                    # Map DB codes to detect_levels() codes
                    key = code.replace('_LEVEL', '').replace('_FLOOR', '')
                    if 'GRD_FLOOR' in code or 'GRD_FLOOR' in code:
                        key = 'FFL'
                    elif 'GRD' in code:
                        key = 'GRD'
                    elif 'APRON' in code:
                        key = 'APRON'
                    elif 'BEAM' in code or 'CEILING' in code:
                        key = 'CLG'
                    elif 'ROOF' in code or 'RIDGE' in code:
                        key = 'RIDGE'
                    elif 'SILL' in code:
                        key = 'SILL'
                    elif 'HEAD' in code:
                        key = 'HEAD'
                    elif 'EAVE' in code:
                        key = 'EAVE'
                    elif 'FIRST' in code:
                        key = 'CLG'
                    result['level_labels'][key] = text
                    result['level_elevations'][key] = z
                # Read roof labels from 2d_drawing_part if available
                try:
                    cur.execute("SELECT part_code, description FROM [2d_drawing_part] "
                                "WHERE part_code IN ('RIDGE_LINE','ROOF_OUTLINE')")
                    for pc, desc in cur.fetchall():
                        if 'RIDGE' in pc:
                            result['roof_labels']['ridge'] = result['level_labels'].get('RIDGE', 'RABUNG / RIDGE')
                        # eave stays as default
                except Exception:
                    pass
                conn.close()
                _log(f"§3.2 2D.db: {len(result['level_labels'])} level markers loaded")
            except Exception as e:
                _log(f"§3.2 2D.db read failed: {e}")
            break
    return result


# ─────────────────────────────────────────────────────────────────
# DXF→PROOF RENDERER (§10.0 R6, R7)
# Reads DXF entities back and renders to paper-scale SVG/PNG.
# Bypasses ezdxf rendering — writes SVG directly from entity data.
# ─────────────────────────────────────────────────────────────────

# ACI color index → hex
_ACI_HEX = {
    0: '#000000', 1: '#FF0000', 2: '#FFFF00', 3: '#00FF00',
    4: '#00FFFF', 5: '#0000FF', 6: '#FF00FF', 7: '#000000',
    8: '#888888', 9: '#AAAAAA', 254: '#CCCCCC',
}


def _render_proof(dxf_path: str, svg_path: str, png_path: str = None):
    """Read DXF, transform to paper-scale, write SVG. §9.6 R1: SVG only."""
    doc = ezdxf.readfile(dxf_path)
    msp = doc.modelspace()
    tpl = _load_template()
    sc = int(tpl.get('paper', {}).get('scale', '1:100').split(':')[1])

    # Collect all geometry-layer entity extents (model-space mm)
    _SKIP_LAYERS = {'0'}  # skip background rectangle
    gx0 = gy0 = float('inf')
    gx1 = gy1 = float('-inf')
    for e in msp:
        if e.dxf.layer in _SKIP_LAYERS:
            continue
        if e.dxftype() == 'LWPOLYLINE':
            for pt in e.get_points(format='xy'):
                gx0, gy0 = min(gx0, pt[0]), min(gy0, pt[1])
                gx1, gy1 = max(gx1, pt[0]), max(gy1, pt[1])
        elif e.dxftype() == 'LINE':
            for v in (e.dxf.start, e.dxf.end):
                gx0, gy0 = min(gx0, v.x), min(gy0, v.y)
                gx1, gy1 = max(gx1, v.x), max(gy1, v.y)
        elif e.dxftype() in ('CIRCLE', 'ARC'):
            r = e.dxf.radius
            gx0 = min(gx0, e.dxf.center.x - r)
            gy0 = min(gy0, e.dxf.center.y - r)
            gx1 = max(gx1, e.dxf.center.x + r)
            gy1 = max(gy1, e.dxf.center.y + r)
        elif e.dxftype() == 'TEXT':
            gx0 = min(gx0, e.dxf.insert.x)
            gy0 = min(gy0, e.dxf.insert.y)
            gx1 = max(gx1, e.dxf.insert.x + e.dxf.height * len(e.dxf.text) * 0.6)
            gy1 = max(gy1, e.dxf.insert.y + e.dxf.height)

    # Paper-space transform: model mm → paper mm
    # Pad 3% and add margins
    pad = max(gx1 - gx0, gy1 - gy0) * 0.03
    gx0 -= pad; gy0 -= pad; gx1 += pad; gy1 += pad
    pw = (gx1 - gx0) / sc
    ph = (gy1 - gy0) / sc
    ox = -gx0 / sc  # offset so gx0 maps to x=0
    oy = gy1 / sc    # offset so gy1 maps to y=0 (Y flip)

    def px(mx): return mx / sc + ox
    def py(my): return -my / sc + oy

    # Layer → color/weight/linetype mapping from DXF layers
    layer_colors = {}
    layer_weights = {}
    layer_linetypes = {}
    for layer in doc.layers:
        aci = layer.color
        layer_colors[layer.dxf.name] = _ACI_HEX.get(aci, '#000000')
        # DXF lineweight is in 1/100 mm; -1 = default
        lw = getattr(layer.dxf, 'lineweight', -1)
        layer_weights[layer.dxf.name] = max(lw, 18) / 100.0  # mm
        layer_linetypes[layer.dxf.name] = getattr(layer.dxf, 'linetype', 'CONTINUOUS')

    # §4.5: DASHDOT → stroke-dasharray [4,1,1,1] scaled to paper mm
    def _dasharray(layer_name):
        lt = layer_linetypes.get(layer_name, 'CONTINUOUS')
        if lt == 'DASHDOT':
            s = 1.0 / sc  # scale dash pattern to paper coordinates
            return f' stroke-dasharray="{4*s:.3f},{1*s:.3f},{1*s:.3f},{1*s:.3f}"'
        if lt == 'HIDDEN':
            s = 1.0 / sc
            return f' stroke-dasharray="{6*s:.3f},{2*s:.3f}"'
        return ''

    svg_lines = []
    svg_lines.append(f'<?xml version="1.0" encoding="UTF-8"?>')
    svg_lines.append(f'<svg xmlns="http://www.w3.org/2000/svg" '
                     f'viewBox="0.00 0.00 {pw:.2f} {ph:.2f}" '
                     f'width="{int(pw*3)}" height="{int(ph*3)}" '
                     f'style="background:#FFFFFF">')
    svg_lines.append(f'<defs><style>text {{ font-family: "Arial", "Helvetica", sans-serif; }}</style></defs>')
    # §10.0 R5: explicit white background rect (CSS background unreliable in cairosvg)
    svg_lines.append(f'<rect width="100%" height="100%" fill="#FFFFFF"/>')

    # Render each entity
    for e in msp:
        layer = e.dxf.layer
        if layer in _SKIP_LAYERS:
            continue
        color = layer_colors.get(layer, '#000000')
        weight = layer_weights.get(layer, 0.18)
        # Override color from entity if set
        ec = getattr(e.dxf, 'color', 256)
        if ec != 256 and ec in _ACI_HEX:
            color = _ACI_HEX[ec]
        # Override lineweight from entity if set
        elw = getattr(e.dxf, 'lineweight', -1)
        if elw > 0:
            weight = elw / 100.0

        if e.dxftype() == 'LWPOLYLINE':
            pts = list(e.get_points(format='xy'))
            if len(pts) < 2:
                continue
            points_str = ' '.join(f'{px(p[0]):.3f},{py(p[1]):.3f}' for p in pts)
            is_closed = e.close
            fill = 'none'
            # Wall fill layer: solid black fill
            if layer == 'A-WALL-PATT':
                fill = '#000000'
                color = '#000000'
            elif layer == 'A-FURN':
                fill = '#F0F0F0'
            svg_lines.append(
                f'<polygon points="{points_str}" stroke="{color}" '
                f'stroke-width="{weight:.3f}" fill="{fill}" '
                f'stroke-linejoin="round"/>')

        elif e.dxftype() == 'LINE':
            da = _dasharray(layer)
            svg_lines.append(
                f'<line x1="{px(e.dxf.start.x):.3f}" y1="{py(e.dxf.start.y):.3f}" '
                f'x2="{px(e.dxf.end.x):.3f}" y2="{py(e.dxf.end.y):.3f}" '
                f'stroke="{color}" stroke-width="{weight:.3f}"{da}/>')

        elif e.dxftype() == 'CIRCLE':
            svg_lines.append(
                f'<circle cx="{px(e.dxf.center.x):.3f}" cy="{py(e.dxf.center.y):.3f}" '
                f'r="{e.dxf.radius/sc:.3f}" stroke="{color}" '
                f'stroke-width="{weight:.3f}" fill="#FFFFFF"/>')

        elif e.dxftype() == 'ARC':
            cx, cy = px(e.dxf.center.x), py(e.dxf.center.y)
            r = e.dxf.radius / sc
            sa = math.radians(e.dxf.start_angle)
            ea = math.radians(e.dxf.end_angle)
            # SVG arc: Y is flipped, so negate angles
            x1 = cx + r * math.cos(sa)
            y1 = cy - r * math.sin(sa)
            x2 = cx + r * math.cos(ea)
            y2 = cy - r * math.sin(ea)
            # Determine sweep
            da = (e.dxf.end_angle - e.dxf.start_angle) % 360
            large = 1 if da > 180 else 0
            svg_lines.append(
                f'<path d="M {x1:.3f},{y1:.3f} A {r:.3f},{r:.3f} 0 {large},0 '
                f'{x2:.3f},{y2:.3f}" stroke="{color}" '
                f'stroke-width="{weight:.3f}" fill="none"/>')

        elif e.dxftype() == 'TEXT':
            txt = e.dxf.text
            h = e.dxf.height / sc
            ix = px(e.dxf.insert.x)
            iy = py(e.dxf.insert.y)
            rot = getattr(e.dxf, 'rotation', 0)
            anchor = 'middle'
            # Check alignment
            align = e.get_align_enum() if hasattr(e, 'get_align_enum') else None
            if align and 'CENTER' in str(align):
                anchor = 'middle'
                try:
                    ap = e.dxf.align_point
                    ix, iy = px(ap.x), py(ap.y)
                except Exception:
                    pass
            transform = ''
            if rot:
                transform = f' transform="rotate({-rot},{ix:.3f},{iy:.3f})"'
            svg_lines.append(
                f'<text x="{ix:.3f}" y="{iy:.3f}" font-size="{h:.2f}" '
                f'fill="{color}" text-anchor="{anchor}" '
                f'dominant-baseline="central"{transform}>'
                f'{txt.replace("&","&amp;").replace("<","&lt;")}</text>')

        elif e.dxftype() == 'SOLID':
            # Filled quadrilateral (scale bar segments, level markers)
            pts = []
            for attr in ('vtx0', 'vtx1', 'vtx2', 'vtx3'):
                v = getattr(e.dxf, attr, None)
                if v:
                    pts.append((px(v.x), py(v.y)))
            if len(pts) >= 3:
                # SOLID vertex order: 0,1,3,2 (not 0,1,2,3)
                if len(pts) == 4:
                    pts = [pts[0], pts[1], pts[3], pts[2]]
                points_str = ' '.join(f'{p[0]:.3f},{p[1]:.3f}' for p in pts)
                fill_c = _ACI_HEX.get(ec, '#000000') if ec != 256 else color
                svg_lines.append(
                    f'<polygon points="{points_str}" fill="{fill_c}" '
                    f'stroke="{fill_c}" stroke-width="0.1"/>')

        elif e.dxftype() == 'DIMENSION':
            # Dimension entities are complex; skip for now (bay dims
            # rendered as LINE+TEXT entities in floor plan)
            pass

    svg_lines.append('</svg>')
    svg_str = '\n'.join(svg_lines)

    with open(svg_path, 'w') as f:
        f.write(svg_str)

    # §9.6 R1: SVG proof only, no PNG


_VERDICTS: List[Tuple[str, bool, str]] = []  # (view, pass, detail)

def _write_log(out_dir: str):
    """Flush buffered log lines + summary to a diagnostic text file."""
    if not _LOG_LINES:
        return
    log_path = os.path.join(out_dir, 'dxf_diagnostic.txt')
    stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    # Build summary
    summary = []
    summary.append("")
    summary.append("=" * 60)
    summary.append("SUMMARY — read this section only")
    summary.append("=" * 60)
    passes = sum(1 for _, ok, _ in _VERDICTS if ok)
    fails  = sum(1 for _, ok, _ in _VERDICTS if not ok)
    for view, ok, detail in _VERDICTS:
        tag = "PASS" if ok else "FAIL"
        summary.append(f"  [{tag}] {view}: {detail}")
    summary.append("")
    if fails == 0:
        summary.append(f"RESULT: ALL {passes} VIEWS PASS — no visual inspection needed")
    else:
        summary.append(f"RESULT: {fails} FAIL / {passes} PASS — inspect FAIL views")
    summary.append("")

    with open(log_path, 'w') as f:
        f.write(f"DXF Diagnostic — {stamp}\n")
        f.write("=" * 60 + "\n\n")
        for line in _LOG_LINES:
            f.write(line + "\n")
        for line in summary:
            f.write(line + "\n")

    # Print summary to console too
    for line in summary:
        print(line)
    print(f"  Diagnostic log → {log_path}")


# ─────────────────────────────────────────────────────────────────
# FLOOR PLAN
# ─────────────────────────────────────────────────────────────────

def write_floor_plan_dxf(db_path: str, out_dxf: str, scale: int = SCALE):
    """Generate floor plan DXF from compiled output.db.

    Coordinates in model-space mm (no PAPER_FACTOR).
    Spec: 2D_ARCHITECTURAL_LAYOUT.md §5.1, §2 Process
    """
    # ── §2 Step 1: LOAD TEMPLATE ──
    tpl = _load_template()
    tpl_paper = tpl.get('paper', {})
    tpl_grid  = tpl.get('grid', {})
    tpl_dims  = tpl.get('dimensions', {})
    tpl_tags  = tpl.get('annotation_tags', {})
    tpl_rooms = tpl.get('room_labels', {})
    tpl_na    = tpl.get('north_arrow', {})
    _log(f"§2.1 Template loaded: paper={tpl_paper.get('size','?')}, "
         f"scale={tpl_paper.get('scale','?')}, "
         f"line_weights={len(tpl.get('line_weights',{}))} entries")

    # Line weights from template §1 R4
    lw_wall_ext = _lw(tpl, 'wall_exterior_cut')   # 0.50mm → 50
    lw_wall_int = _lw(tpl, 'wall_partition_cut')   # 0.35mm → 35
    lw_opening  = _lw(tpl, 'window_frame')         # 0.25mm → 25
    lw_glass    = _lw(tpl, 'glass_glazing')        # 0.18mm → 18
    lw_furn     = _lw(tpl, 'furniture')             # 0.15mm → 15
    lw_dim      = _lw(tpl, 'dimension_line')        # 0.18mm → 18
    lw_grid     = _lw(tpl, 'grid_line')             # 0.18mm → 18

    # Text heights from template §7.3 (paper mm)
    txt_dim   = tpl_dims.get('text_height_mm', 2.5)
    txt_room  = tpl_rooms.get('name_font_height_mm', 3.5)
    txt_area  = tpl_rooms.get('area_font_height_mm', 2.5)
    txt_tag   = tpl_tags.get('font_height_mm', 2.0)
    txt_title = tpl.get('title_block', {}).get('font_height_value_mm', 3.0) * 1.5

    # Colors from template §8
    tpl_colors = tpl.get('colors', {})
    scale_color = _hex_to_aci(tpl_colors.get('scale_text', tpl_colors.get('grid', '')))

    # ── §2 Step 2: QUERY DB ──
    elements = read_elements(db_path)
    walls    = elements['walls']
    doors    = elements['doors']
    windows  = elements['windows']
    furniture = elements['furniture']
    columns  = [e for e in elements.get('other', []) if e.ifc_class == 'IfcColumn']
    _log(f"§2.2 DB loaded: {len(walls)} walls, {len(doors)} doors, "
         f"{len(windows)} windows, {len(furniture)} furniture, {len(columns)} columns")

    if not walls:
        print("No walls — skipping floor plan DXF", file=sys.stderr)
        return

    doc = _new_doc(tpl, scale)
    doc.appids.new('BIMGUID')
    msp = doc.modelspace()

    # ARC ifc_classes eligible for GUID xdata (roundtrip scope)
    _ARC_CLASSES = {'IfcWall', 'IfcWallStandardCase', 'IfcSlab',
                    'IfcDoor', 'IfcWindow', 'IfcPlate'}

    # ── §2 Step 5: SECTION CUT ──
    cut_z = 1.2  # spec §5.1: storey_elevation + 1.2m (ground = 0.0)
    cut_results = run_section_cut(db_path, cut_z=cut_z)
    n_cut   = sum(1 for es in cut_results if es.category == 'CUT')
    n_below = sum(1 for es in cut_results if es.category == 'BELOW')
    n_above = sum(1 for es in cut_results if es.category == 'ABOVE')
    _log(f"§2.5 Section cut Z={cut_z}m: {n_cut} CUT, {n_below} BELOW, {n_above} ABOVE")

    cut_count = 0
    lw_hatch = _lw(tpl, 'hatch_line')
    for es in cut_results:
        if es.category != 'CUT' or not es.contours:
            continue
        if es.ifc_class in ('IfcWall', 'IfcWallStandardCase'):
            layer = ('A-WALL-FULL'
                     if 'Ext' in (es.element_name or '')
                     else 'A-WALL-PRTN')
            lw = lw_wall_ext if 'Ext' in (es.element_name or '') else lw_wall_int
        elif es.ifc_class in ('IfcPlate',):
            layer, lw = 'A-GLAZ', lw_glass
        elif es.ifc_class == 'IfcDoor':
            layer, lw = 'A-DOOR', lw_opening
        elif es.ifc_class == 'IfcWindow':
            layer, lw = 'A-GLAZ', lw_opening
        else:
            layer, lw = 'A-WALL-PRTN', lw_wall_int

        for contour in es.contours:
            pts = [(_mh(p[0]), _mh(p[1])) for p in contour.points]
            if len(pts) >= 2:
                # §5.1: Wall outline (stroke)
                pl = msp.add_lwpolyline(pts, close=True,
                                        dxfattribs={'layer': layer, 'lineweight': lw})
                if es.ifc_class in _ARC_CLASSES and es.guid:
                    pl.set_xdata('BIMGUID', [(1000, es.guid)])
                # §5.1: Wall solid fill on A-WALL-PATT (SVG ref: fill=COL_WALL)
                if es.ifc_class in ('IfcWall', 'IfcWallStandardCase'):
                    msp.add_lwpolyline(pts, close=True,
                                       dxfattribs={'layer': 'A-WALL-PATT',
                                                   'lineweight': lw_hatch})
                cut_count += 1

    # ── BELOW elements: bounding box on A-FURN ──
    for es in cut_results:
        if es.category != 'BELOW':
            continue
        bx0, by0, bx1, by1 = es.bbox_2d
        pts = [(_mh(bx0), _mh(by0)), (_mh(bx1), _mh(by0)),
               (_mh(bx1), _mh(by1)), (_mh(bx0), _mh(by1))]
        msp.add_lwpolyline(pts, close=True,
                           dxfattribs={'layer': 'A-FURN', 'lineweight': lw_furn})

    # ── §5.1: Door swing arcs + window symbols (ported from SVG writer) ──
    arc_count = 0
    win_sym_count = 0
    for opening in doors + windows:
        host = find_host_wall(opening, walls)
        if not host:
            continue

        if host.is_ew_wall or host.width_y < host.width_x:
            # Opening in E-W wall
            if opening.ifc_class == 'IfcWindow':
                # §5.1: double parallel lines + glass center line in wall opening
                ox = _mh(opening.min_x)
                ow = _mh(opening.width_x)
                wall_cy = _mh(host.center_y)
                wall_hw = _mh(host.width_y) * 0.3
                msp.add_line((ox, wall_cy - wall_hw), (ox + ow, wall_cy - wall_hw),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_opening})
                msp.add_line((ox, wall_cy + wall_hw), (ox + ow, wall_cy + wall_hw),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_opening})
                msp.add_line((ox, wall_cy), (ox + ow, wall_cy),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_glass})
                win_sym_count += 1
            elif opening.ifc_class == 'IfcDoor':
                # §5.1: door leaf line + quarter-arc swing
                door_w = _mh(opening.width_x)
                hinge_x = _mh(opening.min_x)
                hinge_y = _mh(host.center_y)
                # Leaf line (horizontal from hinge)
                msp.add_line((hinge_x, hinge_y), (hinge_x + door_w, hinge_y),
                             dxfattribs={'layer': 'A-DOOR', 'lineweight': lw_opening})
                # Arc: swing direction from furniture clustering
                room_side = _get_room_side_ew(host, furniture)
                if room_side == 'south':
                    # Swing south: arc from 0 to -90 (CW)
                    msp.add_arc((hinge_x, hinge_y), door_w,
                                start_angle=-90, end_angle=0,
                                dxfattribs={'layer': 'A-DOOR', 'lineweight': lw_opening})
                else:
                    # Swing north: arc from 0 to 90 (CCW)
                    msp.add_arc((hinge_x, hinge_y), door_w,
                                start_angle=0, end_angle=90,
                                dxfattribs={'layer': 'A-DOOR', 'lineweight': lw_opening})
                arc_count += 1
        else:
            # Opening in N-S wall
            if opening.ifc_class == 'IfcWindow':
                oy = _mh(opening.min_y)
                oh = _mh(opening.width_y)
                wall_cx = _mh(host.center_x)
                wall_hw = _mh(host.width_x) * 0.3
                msp.add_line((wall_cx - wall_hw, oy), (wall_cx - wall_hw, oy + oh),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_opening})
                msp.add_line((wall_cx + wall_hw, oy), (wall_cx + wall_hw, oy + oh),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_opening})
                msp.add_line((wall_cx, oy), (wall_cx, oy + oh),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_glass})
                win_sym_count += 1
            elif opening.ifc_class == 'IfcDoor':
                door_h = _mh(opening.width_y)
                hinge_x = _mh(host.center_x)
                hinge_y = _mh(opening.min_y)
                msp.add_line((hinge_x, hinge_y), (hinge_x, hinge_y + door_h),
                             dxfattribs={'layer': 'A-DOOR', 'lineweight': lw_opening})
                room_side = _get_room_side_ns(host, furniture)
                if room_side == 'east':
                    msp.add_arc((hinge_x, hinge_y), door_h,
                                start_angle=0, end_angle=90,
                                dxfattribs={'layer': 'A-DOOR', 'lineweight': lw_opening})
                else:
                    msp.add_arc((hinge_x, hinge_y), door_h,
                                start_angle=90, end_angle=90 + 90,
                                dxfattribs={'layer': 'A-DOOR', 'lineweight': lw_opening})
                arc_count += 1
    _log(f"§5.1 Door swing arcs: {arc_count}, Window symbols: {win_sym_count}")

    # ── §2 Step 3: DETECT ALIGNMENT (grid lines) ──
    # §4.2a: columns + walls. §4.2d: labels from template (skip I)
    grids = snap_grids(derive_grids(walls, columns=columns, template=tpl))
    for g in grids:
        _log(f"§2.3 Grid {g.label} axis={g.axis} pos={g.position:.3f}m")

    all_elems = walls + doors + windows + furniture
    bld_min_x = min(e.min_x for e in all_elems) * MM
    bld_max_x = max(e.max_x for e in all_elems) * MM
    bld_min_y = min(e.min_y for e in all_elems) * MM
    bld_max_y = max(e.max_y for e in all_elems) * MM

    grid_ext   = tpl_grid.get('extend_beyond_building_mm', 15) * scale
    bubble_r   = tpl_grid.get('bubble_radius_mm', 4.0) * scale
    bubble_lw  = int(tpl_grid.get('bubble_stroke_mm', 0.25) * 100)
    grid_gap   = tpl_dims.get('extension_gap_mm', 2.0) * scale
    tag_size   = tpl_tags.get('size_mm', 4.0) * scale
    txt_grid_h = tpl_grid.get('label_font_height_mm', 3.0) * scale

    for g in grids:
        pos = g.position * MM
        if g.axis == 'x':
            msp.add_line((pos, bld_min_y - grid_ext),
                         (pos, bld_max_y + grid_ext + bubble_r * 2 + grid_gap),
                         dxfattribs={'layer': 'A-GRID', 'lineweight': lw_grid})
            for cy in (bld_max_y + grid_ext + bubble_r,
                       bld_min_y - grid_ext - bubble_r):
                msp.add_circle((pos, cy), bubble_r,
                               dxfattribs={'layer': 'A-GRID', 'lineweight': bubble_lw})
                msp.add_text(g.label,
                             dxfattribs={'layer': 'A-ANNO-TEXT',
                                         'height': txt_grid_h}
                             ).set_placement((pos, cy),
                                             align=TextEntityAlignment.MIDDLE_CENTER)
        else:
            msp.add_line((bld_min_x - grid_ext, pos),
                         (bld_max_x + grid_ext + bubble_r * 2 + grid_gap, pos),
                         dxfattribs={'layer': 'A-GRID', 'lineweight': lw_grid})
            for cx in (bld_max_x + grid_ext + bubble_r,
                       bld_min_x - grid_ext - bubble_r):
                msp.add_circle((cx, pos), bubble_r,
                               dxfattribs={'layer': 'A-GRID', 'lineweight': bubble_lw})
                msp.add_text(g.label,
                             dxfattribs={'layer': 'A-ANNO-TEXT',
                                         'height': txt_grid_h}
                             ).set_placement((cx, pos),
                                             align=TextEntityAlignment.MIDDLE_CENTER)

    # ── §2 Step 4: COMPUTE DIMENSIONS ──
    dims = generate_dimensions(grids)
    dim_count = 0
    tick_len = tpl_dims.get('tick_half_length_mm', 1.5) * scale
    txt_h    = tpl_dims.get('text_height_mm', 2.5) * scale

    for d in dims:
        s = d.start * MM
        e = d.end * MM
        off = d.offset * scale
        _log(f"§2.4 Dim {d.axis} {d.start:.3f}→{d.end:.3f} = {d.text}mm")

        if d.axis == 'x':
            dim_y = bld_max_y + grid_ext + bubble_r * 2 + grid_gap + off
            msp.add_line((s, dim_y), (e, dim_y),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((s, bld_max_y + grid_ext), (s, dim_y),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((e, bld_max_y + grid_ext), (e, dim_y),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            for tx in (s, e):
                msp.add_line((tx - tick_len, dim_y - tick_len),
                             (tx + tick_len, dim_y + tick_len),
                             dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            mid_x = (s + e) / 2
            msp.add_text(d.text,
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'height': txt_h}
                         ).set_placement((mid_x, dim_y + txt_h * 0.3),
                                         align=TextEntityAlignment.MIDDLE_CENTER)
        else:
            dim_x = bld_min_x - grid_ext - bubble_r * 2 - grid_gap - off
            msp.add_line((dim_x, s), (dim_x, e),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((bld_min_x - grid_ext, s), (dim_x, s),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((bld_min_x - grid_ext, e), (dim_x, e),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            for ty in (s, e):
                msp.add_line((dim_x - tick_len, ty - tick_len),
                             (dim_x + tick_len, ty + tick_len),
                             dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            mid_y = (s + e) / 2
            msp.add_text(d.text,
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'height': txt_h,
                                     'rotation': 90}
                         ).set_placement((dim_x - txt_h * 0.3, mid_y),
                                         align=TextEntityAlignment.MIDDLE_CENTER)
        dim_count += 1

    # ── §2 Step 6: INFER ROOMS ──
    meta = read_drawing_metadata()
    room_labels_meta = meta.get('room_labels', {}) if meta else {}
    rooms = infer_rooms(furniture, walls)
    room_count = 0
    room_h = txt_room * scale
    area_h = txt_area * scale
    for room_type, cx, cy, area in rooms:
        rx = _mh(cx)
        ry = _mh(cy)
        label_info = room_labels_meta.get(room_type, {})
        label_text = label_info.get('label_text', room_type)
        _log(f"§2.6 Room {room_type} at ({cx:.2f},{cy:.2f}) area={area:.1f}m²")
        msp.add_text(label_text,
                     dxfattribs={'layer': 'A-ANNO-TEXT',
                                 'height': room_h}
                     ).set_placement((rx, ry + room_h * 0.5),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        msp.add_text(f'{area:.1f} m\u00b2',
                     dxfattribs={'layer': 'A-ANNO-TEXT',
                                 'height': area_h}
                     ).set_placement((rx, ry - area_h * 0.8),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        room_count += 1

    # ── Door / window tags — §7.3 annotation_tags.* ──
    tag_count = 0
    tag_r = tag_size / 2
    tag_lw = int(tpl_tags.get('stroke_mm', 0.18) * 100)
    tag_txt_h = txt_tag * scale

    d_num = 0
    for opening in elements['doors']:
        host = find_host_wall(opening, walls)
        if not host:
            continue
        d_num += 1
        tag_label = f'D{d_num}'
        tcx, tcy = _mh(opening.center_x), _mh(opening.center_y)

        if host.is_ew_wall or host.width_y < host.width_x:
            tcy -= _mh(host.width_y / 2) + tag_r + grid_gap
        else:
            tcx += _mh(host.width_x / 2) + tag_r + grid_gap

        # §7.3: hexagonal tag shape per template annotation_tags.shape
        hex_pts = _hexagon_pts(tcx, tcy, tag_r)
        msp.add_lwpolyline(hex_pts, close=True,
                           dxfattribs={'layer': 'A-ANNO-TEXT', 'lineweight': tag_lw})
        msp.add_text(tag_label,
                     dxfattribs={'layer': 'A-ANNO-TEXT',
                                 'height': tag_txt_h}
                     ).set_placement((tcx, tcy),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        tag_count += 1

    w_num = 0
    for opening in elements['windows']:
        host = find_host_wall(opening, walls)
        if not host:
            continue
        w_num += 1
        tag_label = f'W{w_num}'
        tcx, tcy = _mh(opening.center_x), _mh(opening.center_y)

        if host.is_ew_wall or host.width_y < host.width_x:
            tcy += _mh(host.width_y / 2) + tag_r + grid_gap
        else:
            tcx -= _mh(host.width_x / 2) + tag_r + grid_gap

        # §7.3: hexagonal tag shape per template annotation_tags.shape
        hex_pts = _hexagon_pts(tcx, tcy, tag_r)
        msp.add_lwpolyline(hex_pts, close=True,
                           dxfattribs={'layer': 'A-ANNO-TEXT', 'lineweight': tag_lw})
        msp.add_text(tag_label,
                     dxfattribs={'layer': 'A-ANNO-TEXT',
                                 'height': tag_txt_h}
                     ).set_placement((tcx, tcy),
                                     align=TextEntityAlignment.MIDDLE_CENTER)
        tag_count += 1

    # ── §2 Step 7: RENDER sheet layout ──
    schedule = _build_schedule(elements)
    _draw_sheet_layout(doc, msp, tpl, bld_min_x, bld_max_x, bld_min_y, bld_max_y,
                       drawing_title='FLOOR PLAN', drawing_no='A-01',
                       scale=scale, schedule_rows=schedule)

    # ── Drawing title (below building) ──
    title_h = txt_title * scale
    title_x = (bld_min_x + bld_max_x) / 2
    title_y = bld_min_y - grid_ext - bubble_r * 2 - tpl_dims.get('tier_1_offset_mm', 26) * scale
    msp.add_text('1.  FLOOR PLAN',
                 dxfattribs={'layer': 'A-ANNO-TEXT',
                             'height': title_h}
                 ).set_placement((title_x, title_y),
                                 align=TextEntityAlignment.MIDDLE_CENTER)
    msp.add_text(f'scale {tpl_paper.get("scale", "1:100")}',
                 dxfattribs={'layer': 'A-ANNO-TEXT',
                             'height': txt_h, 'color': scale_color}
                 ).set_placement((title_x, title_y - title_h * 1.5),
                                 align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Scale bar (template scale_bar.*) ──
    tpl_sb = tpl.get('scale_bar', {})
    sb_divs = tpl_sb.get('divisions', [0, 1, 2, 5])
    sb_unit = tpl_sb.get('unit', 'm')
    sb_bar_h = tpl_sb.get('bar_height_mm', 2.0) * scale
    sb_font_h = tpl_sb.get('font_height_mm', 2.0) * scale
    sb_lw = _lw(tpl, 'dimension_line')
    # Bar positioned below scale text, centered on title_x
    sb_y = title_y - title_h * 1.5 - sb_font_h * 2
    # Each division = real metres × scale factor (1m at 1:100 = 10mm paper × scale)
    sb_scale_factor = MM / SCALE  # 1 metre → 10mm paper → model-space mm
    total_extent = (sb_divs[-1] - sb_divs[0]) * sb_scale_factor
    sb_x0 = title_x - total_extent / 2  # center the bar
    for i in range(len(sb_divs) - 1):
        x_start = sb_x0 + (sb_divs[i] - sb_divs[0]) * sb_scale_factor
        x_end = sb_x0 + (sb_divs[i + 1] - sb_divs[0]) * sb_scale_factor
        fill_color = 7 if i % 2 == 0 else 0  # alternate black(7)/white(0)... ACI 7=black on white bg
        # Use SOLID for filled bar segments (ACI 0=BYBLOCK, 7=white/black)
        msp.add_solid(
            [(x_start, sb_y), (x_end, sb_y),
             (x_start, sb_y + sb_bar_h), (x_end, sb_y + sb_bar_h)],
            dxfattribs={'layer': 'A-ANNO-DIMS', 'color': fill_color})
    # Labels below each division tick
    for d in sb_divs:
        lx = sb_x0 + (d - sb_divs[0]) * sb_scale_factor
        label = f'{d}{sb_unit}' if d == sb_divs[-1] else str(d)
        msp.add_text(label,
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'height': sb_font_h}
                     ).set_placement((lx, sb_y - sb_font_h * 0.8),
                                     align=TextEntityAlignment.MIDDLE_CENTER)

    # ── §2 Step 8: VERIFY ──
    _log(f"§2.8 Floor plan: {cut_count} cut polylines, {len(grids)} grids, "
         f"{dim_count} dims, {room_count} rooms, {tag_count} tags")
    doc.saveas(out_dxf)
    _log(f"  → {out_dxf}")
    _log(f"  → {out_dxf}")
    _audit_dxf(doc, out_dxf, "FLOOR PLAN")


# ─────────────────────────────────────────────────────────────────
# ELEVATION
# ─────────────────────────────────────────────────────────────────

def write_elevation_dxf(db_path: str, face: str, out_dxf: str,
                        scale: int = SCALE):
    """Generate elevation DXF — spec §5.2, §6.2.
    All formatting from drawing_template.json. All data from DB."""

    # ── §2 Step 1: LOAD TEMPLATE ──
    tpl = _load_template()
    tpl_grid = tpl.get('grid', {})
    tpl_dims = tpl.get('dimensions', {})
    tpl_lm   = tpl.get('level_markers', {})
    _log(f"§2.1 Elevation {face}: template loaded")

    # Line weights from template §1 R4
    lw_wall_ext = _lw(tpl, 'wall_exterior_cut')
    lw_wall_int = _lw(tpl, 'wall_partition_cut')
    lw_opening  = _lw(tpl, 'window_frame')
    lw_glass    = _lw(tpl, 'glass_glazing')
    lw_dim      = _lw(tpl, 'dimension_line')
    lw_grid     = _lw(tpl, 'grid_line')
    lw_border   = _lw(tpl, 'border')

    # Text heights from template (paper mm)
    txt_dim   = tpl_dims.get('text_height_mm', 2.5)
    txt_grid  = tpl_grid.get('label_font_height_mm', 3.0)
    txt_level = tpl_lm.get('font_height_mm', 2.5)

    # Grid geometry from template
    bubble_r   = tpl_grid.get('bubble_radius_mm', 4.0) * scale
    bubble_lw  = int(tpl_grid.get('bubble_stroke_mm', 0.25) * 100)
    grid_gap   = tpl_dims.get('extension_gap_mm', 2.0) * scale
    tick_len   = tpl_dims.get('tick_half_length_mm', 1.5) * scale

    # Dimension tier offsets from template §6.1
    # Tier 2 = bay (inner, closer to building), Tier 1 = overall (outer)
    tier_2_off = tpl_dims.get('tier_2_offset_mm', 18) * scale
    tier_1_off = tpl_dims.get('tier_1_offset_mm', 26) * scale

    # ── §2 Step 2: QUERY DB ──
    elements = read_elements(db_path)
    walls    = elements['walls']
    doors    = elements['doors']
    windows  = elements['windows']
    roofs    = elements['roofs']
    slabs    = elements['slabs']

    if not walls:
        return

    _log(f"§2.2 DB loaded: {len(walls)} walls, {len(doors)} doors, "
         f"{len(windows)} windows, {len(roofs)} roofs")

    # Mirror sign: rear and right viewers see the horizontal axis reversed
    if face == 'front':
        h_of  = lambda e: (e.min_x, e.max_x)
        h_sign = 1
        bld_min_y = min(e.min_y for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.min_y < bld_min_y + 1.0]
    elif face == 'rear':
        h_of  = lambda e: (-e.max_x, -e.min_x)
        h_sign = -1
        bld_max_y = max(e.max_y for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.max_y > bld_max_y - 1.0]
    elif face == 'left':
        h_of  = lambda e: (e.min_y, e.max_y)
        h_sign = 1
        bld_min_x = min(e.min_x for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.min_x < bld_min_x + 1.0]
    else:  # right
        h_of  = lambda e: (-e.max_y, -e.min_y)
        h_sign = -1
        bld_max_x = max(e.max_x for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.max_x > bld_max_x - 1.0]

    v_of = lambda e: (e.min_z, e.max_z)

    doc = _new_doc(tpl, scale)
    msp = doc.modelspace()

    # ── §2 Step 7: RENDER — wall/door/window outlines ──
    for e in face_elems:
        hh = h_of(e)
        vv = v_of(e)
        h0, h1 = _mh(hh[0]), _mh(hh[1])
        v0, v1 = _mh(vv[0]), _mh(vv[1])
        pts = [(h0, v0), (h1, v0), (h1, v1), (h0, v1)]

        if e.ifc_class in ('IfcWall', 'IfcPlate'):
            if 'Glazed' in (e.name or '') or 'Curtain' in (e.name or ''):
                layer, lw = 'A-GLAZ', lw_glass
            else:
                layer, lw = 'A-ELEV-WALL', lw_wall_ext
        elif e.ifc_class == 'IfcWindow':
            layer, lw = 'A-GLAZ', lw_opening
            # §5.2: louvre lines evenly spaced by window height
            win_h = v1 - v0
            n_louvres = max(1, int(win_h / (txt_dim * scale)) - 1)
            for li in range(1, n_louvres + 1):
                lv = v0 + li * (win_h / (n_louvres + 1))
                msp.add_line((h0, lv), (h1, lv),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': lw_dim})
        elif e.ifc_class == 'IfcDoor':
            layer, lw = 'A-DOOR', lw_opening
        else:
            layer, lw = 'A-ELEV-WALL', lw_wall_int

        msp.add_lwpolyline(pts, close=True,
                           dxfattribs={'layer': layer, 'lineweight': lw})

    # ── Roof silhouette — §5.2: convex hull of roof projected onto face ──
    hull = roof_silhouette(db_path, face)
    if hull:
        pts = [(_mh(h), _mh(z)) for h, z in hull]
        msp.add_lwpolyline(pts, close=True,
                           dxfattribs={'layer': 'A-ROOF', 'lineweight': lw_wall_ext})

    # ── Ground + FFL lines ──
    all_vis = face_elems + roofs + slabs
    if not all_vis:
        doc.saveas(out_dxf)
        return

    h_min_m = min(h_of(e)[0] for e in all_vis)
    h_max_m = max(h_of(e)[1] for e in all_vis)
    ext = 0.5  # m extension each side

    # ── Level data from 2D.db (§3.2b, §4.3c, §5.2c) ──
    db_levels = _read_2d_db_levels()
    level_labels = db_levels['level_labels']
    apron_z = db_levels['level_elevations'].get('APRON', APRON_Z)
    grd_z   = db_levels['level_elevations'].get('GRD', GRD_Z)

    # GRD. LEVEL line — boldest (border weight)
    msp.add_line((_mh(h_min_m - ext), _mh(grd_z)),
                 (_mh(h_max_m + ext), _mh(grd_z)),
                 dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': lw_border})
    # FFL line — medium
    msp.add_line((_mh(h_min_m - 0.3), 0.0),
                 (_mh(h_max_m + 0.3), 0.0),
                 dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': lw_wall_int})

    # ── Level markers — §5.2, §3.2: text + triangle on left side ──
    levels = detect_levels(elements)
    levels = sorted(levels + [('APRON', apron_z), ('GRD', grd_z)],
                    key=lambda lv: lv[1])

    marker_x = _mh(h_min_m - ext - 0.5)
    txt_h = txt_level * scale
    min_gap = txt_level * 3 * scale
    label_ys = []
    for lbl, lz in levels:
        ly = _mh(lz)
        if label_ys and ly - label_ys[-1] < min_gap:
            ly = label_ys[-1] + min_gap
        label_ys.append(ly)

    for (lbl, lz), label_ly in zip(levels, label_ys):
        true_ly = _mh(lz)
        msp.add_line((marker_x - _mh(1.8), true_ly),
                     (marker_x, true_ly),
                     dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': lw_wall_int})
        tri = [(marker_x, true_ly),
               (marker_x - _mh(0.3), true_ly - _mh(0.18)),
               (marker_x - _mh(0.3), true_ly + _mh(0.18))]
        msp.add_solid(tri, dxfattribs={'layer': 'A-ELEV-LEVL'})
        if abs(label_ly - true_ly) > _mh(0.05):
            msp.add_line((marker_x - _mh(1.6), true_ly),
                         (marker_x - _mh(1.6), label_ly),
                         dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': lw_dim})
        sign = '+' if lz >= 0 else ''
        label_str = f"{level_labels.get(lbl, lbl)}  {sign}{lz:.3f}"
        _log(f"§2.7 Level {lbl} at {lz:.3f}m → {label_str}")
        msp.add_text(label_str,
                     dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_h}
                     ).set_placement((marker_x - _mh(2.0), label_ly),
                                     align=TextEntityAlignment.MIDDLE_RIGHT)

    # ── §2 Step 3: Grid lines on elevation ──
    grid_axis = 'x' if face in ('front', 'rear') else 'y'
    columns = [e for e in elements.get('other', []) if e.ifc_class == 'IfcColumn']
    elev_grids = snap_grids(derive_grids(walls, columns=columns, template=tpl))
    face_grids_raw = [g for g in elev_grids if g.axis == grid_axis]
    face_grids = sorted(
        [type('G', (), {'label': g.label, 'position': g.position * h_sign})()
         for g in face_grids_raw],
        key=lambda g: g.position)

    v_max_m = max(v_of(e)[1] for e in all_vis)
    grid_above = _mh(v_max_m + 2.0)
    txt_grid_h = txt_grid * scale

    for g in face_grids:
        gx = _mh(g.position)
        _log(f"§2.3 Grid {g.label} at {g.position:.3f}m")
        msp.add_line((gx, _mh(grd_z - 0.2)), (gx, grid_above),
                     dxfattribs={'layer': 'A-GRID', 'lineweight': lw_grid})
        msp.add_circle((gx, grid_above + bubble_r + grid_gap),
                       bubble_r,
                       dxfattribs={'layer': 'A-GRID', 'lineweight': bubble_lw})
        msp.add_text(g.label,
                     dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_grid_h}
                     ).set_placement(
                         (gx, grid_above + bubble_r + grid_gap),
                         align=TextEntityAlignment.MIDDLE_CENTER)

    # ── §6.1 Bay dimensions — tier 2 (inner) above grid bubbles ──
    dim_txt_h = txt_dim * scale
    if len(face_grids) >= 2:
        dim_y = grid_above + bubble_r * 2 + tier_2_off
        for i in range(len(face_grids) - 1):
            xa = _mh(face_grids[i].position)
            xb = _mh(face_grids[i + 1].position)
            bay_mm = abs(face_grids[i + 1].position - face_grids[i].position) * 1000
            snapped = round(bay_mm / SNAP_MODULE) * SNAP_MODULE
            _log(f"§2.4 Bay dim {face_grids[i].label}→{face_grids[i+1].label} = {int(snapped)}mm")
            try:
                dim = msp.add_linear_dim(
                    base=(0, dim_y), p1=(xa, 0), p2=(xb, 0),
                    dimstyle='ARCH_JKR', text=str(int(snapped)))
                dim.render()
            except Exception:
                mid_x = (xa + xb) / 2
                msp.add_line((xa, dim_y), (xb, dim_y),
                             dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
                msp.add_text(str(int(snapped)),
                             dxfattribs={'layer': 'A-ANNO-DIMS',
                                         'height': dim_txt_h}
                             ).set_placement((mid_x, dim_y + grid_gap),
                                             align=TextEntityAlignment.MIDDLE_CENTER)

    # ── §6.2 Height dimension chain (right side) ──
    height_levels = [(lbl, lz) for lbl, lz in levels if lbl not in ('APRON', 'GRD')]
    height_levels = sorted(height_levels, key=lambda x: x[1])

    if len(height_levels) >= 2:
        h_dim_x_base = _mh(h_max_m + ext + 0.5)

        # Tier 2: individual height diffs (inner)
        h_dim_x = h_dim_x_base + tier_2_off
        for i in range(len(height_levels) - 1):
            z0 = _mh(height_levels[i][1])
            z1 = _mh(height_levels[i + 1][1])
            diff_mm = abs(height_levels[i + 1][1] - height_levels[i][1]) * 1000
            snapped = round(diff_mm / SNAP_MODULE) * SNAP_MODULE
            _log(f"§2.4 Height {height_levels[i][0]}→{height_levels[i+1][0]} = {int(snapped)}mm")
            msp.add_line((h_dim_x, z0), (h_dim_x, z1),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((_mh(h_max_m + ext), z0), (h_dim_x, z0),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((_mh(h_max_m + ext), z1), (h_dim_x, z1),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            for tz in (z0, z1):
                msp.add_line((h_dim_x - tick_len, tz - tick_len),
                             (h_dim_x + tick_len, tz + tick_len),
                             dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            mid_z = (z0 + z1) / 2
            msp.add_text(str(int(snapped)),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'height': dim_txt_h,
                                     'rotation': 90}
                         ).set_placement((h_dim_x + dim_txt_h * 0.3, mid_z),
                                         align=TextEntityAlignment.MIDDLE_CENTER)

        # Tier 1: overall height (outer)
        h_dim_x2 = h_dim_x_base + tier_1_off
        z_bot = _mh(height_levels[0][1])
        z_top = _mh(height_levels[-1][1])
        total_mm = abs(height_levels[-1][1] - height_levels[0][1]) * 1000
        total_snapped = round(total_mm / SNAP_MODULE) * SNAP_MODULE
        msp.add_line((h_dim_x2, z_bot), (h_dim_x2, z_top),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        msp.add_line((h_dim_x, z_bot), (h_dim_x2, z_bot),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        msp.add_line((h_dim_x, z_top), (h_dim_x2, z_top),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        for tz in (z_bot, z_top):
            msp.add_line((h_dim_x2 - tick_len, tz - tick_len),
                         (h_dim_x2 + tick_len, tz + tick_len),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        mid_z = (z_bot + z_top) / 2
        msp.add_text(str(int(total_snapped)),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'height': dim_txt_h,
                                 'rotation': 90}
                     ).set_placement((h_dim_x2 + dim_txt_h * 0.3, mid_z),
                                     align=TextEntityAlignment.MIDDLE_CENTER)

    # ── §5.0: Sheet furniture (border, title block, north arrow) ──
    _face_dt = {
        'front': ('FRONT ELEVATION', 'A-02'),
        'rear':  ('REAR ELEVATION',  'A-03'),
        'left':  ('LEFT ELEVATION',  'A-04'),
        'right': ('RIGHT ELEVATION', 'A-05'),
    }
    dt_title, dt_no = _face_dt.get(face, ('ELEVATION', ''))
    # Bounding box in model-space mm for sheet layout
    bld_h0 = _mh(h_min_m - ext)
    bld_h1 = _mh(h_max_m + ext)
    bld_v0 = _mh(min(lz for _, lz in levels)) if levels else 0
    bld_v1 = _mh(max(lz for _, lz in levels)) if levels else _mh(3.5)
    _draw_sheet_layout(doc, msp, tpl, bld_h0, bld_h1, bld_v0, bld_v1,
                       drawing_title=dt_title, drawing_no=dt_no, scale=scale,
                       view_type='elevation')

    # ── §2 Step 8: VERIFY ──
    _log(f"§2.8 Elevation {face}: {len(face_elems)} elements, "
         f"{len(face_grids)} grids, {len(height_levels)} height dims")
    doc.saveas(out_dxf)
    _log(f"  → {out_dxf}")
    _audit_dxf(doc, out_dxf, f"ELEVATION {face.upper()}")


# ─────────────────────────────────────────────────────────────────
# ROOF PLAN — §5.3, §9.6 R4
# ─────────────────────────────────────────────────────────────────

def write_roof_plan_dxf(db_path: str, out_dxf: str, scale: int = SCALE):
    """Generate roof plan DXF — top-down view of roof with ridge, slope arrows,
    overhang dimensions, and grids. Ported from drawing_writer.py draw_roof_plan.

    §5.3: Bay grids same as floor plan. Ridge line dashed. Slope arrows
    from ridge toward eave. Eave overhang dimensions.
    """
    from section_cut import section_cut as run_section_cut
    tpl = _load_template()
    elements = read_elements(db_path, storey_filter=None)
    walls = elements['walls']
    roofs = elements['roofs']

    if not roofs:
        _log("§5.3 Roof plan: no roof elements found, skipping")
        return

    # ── Template reads ──
    tpl_grid = tpl.get('grid', {})
    tpl_dims = tpl.get('dimensions', {})
    tpl_lw   = tpl.get('line_weights', {})
    lw_wall  = int(tpl_lw.get('wall_exterior_cut', 0.50) * 100)
    lw_part  = int(tpl_lw.get('wall_partition_cut', 0.35) * 100)
    lw_grid  = int(tpl_lw.get('grid_line', 0.18) * 100)
    lw_dim   = int(tpl_lw.get('dimension_line', 0.18) * 100)
    lw_furn  = int(tpl_lw.get('furniture', 0.15) * 100)

    _log(f"§2.1 Roof plan: template loaded")

    # ── Roof extent ──
    roof_min_x = min(r.min_x for r in roofs)
    roof_max_x = max(r.max_x for r in roofs)
    roof_min_y = min(r.min_y for r in roofs)
    roof_max_y = max(r.max_y for r in roofs)
    roof_max_z = max(r.max_z for r in roofs)

    # ── Ridge line via section cut near peak ──
    ridge_y = (roof_min_y + roof_max_y) / 2
    try:
        ridge_cut = run_section_cut(db_path, cut_z=roof_max_z - 0.1)
        for se in ridge_cut:
            if se.ifc_class == 'IfcRoof' and se.contours:
                all_pts = [p for c in se.contours for p in c.points]
                if all_pts:
                    ridge_y = sum(p[1] for p in all_pts) / len(all_pts)
                break
    except Exception:
        pass

    _log(f"§5.3 Ridge Y={ridge_y:.3f}m, roof Z={roof_max_z:.3f}m")

    # ── Building footprint from walls ──
    bld_min_x = min(w.min_x for w in walls)
    bld_max_x = max(w.max_x for w in walls)
    bld_min_y = min(w.min_y for w in walls)
    bld_max_y = max(w.max_y for w in walls)

    # ── Create DXF ──
    doc = _new_doc(tpl, scale)
    msp = doc.modelspace()

    # ── Building footprint (dashed, grid layer) ──
    bf_pts = [(_mh(bld_min_x), _mh(bld_min_y)), (_mh(bld_max_x), _mh(bld_min_y)),
              (_mh(bld_max_x), _mh(bld_max_y)), (_mh(bld_min_x), _mh(bld_max_y))]
    msp.add_lwpolyline(bf_pts, close=True,
                       dxfattribs={'layer': 'A-GRID', 'lineweight': lw_grid})

    # ── Roof outline (bold, eave line) ──
    ro_pts = [(_mh(roof_min_x), _mh(roof_min_y)), (_mh(roof_max_x), _mh(roof_min_y)),
              (_mh(roof_max_x), _mh(roof_max_y)), (_mh(roof_min_x), _mh(roof_max_y))]
    msp.add_lwpolyline(ro_pts, close=True,
                       dxfattribs={'layer': 'A-ROOF', 'lineweight': lw_wall})

    # ── Ridge line (dashed) ──
    msp.add_line((_mh(roof_min_x), _mh(ridge_y)),
                 (_mh(roof_max_x), _mh(ridge_y)),
                 dxfattribs={'layer': 'A-ROOF', 'lineweight': lw_part,
                             'linetype': _linestyle_to_dxf(tpl.get('line_styles', {}).get('ridge_line', 'dashed'))})
    # §5.3e: Ridge/Eave labels from 2D.db
    db_levels = _read_2d_db_levels()
    ridge_label = db_levels['level_labels'].get('RIDGE', 'RABUNG / RIDGE')
    eave_label  = db_levels['level_labels'].get('EAVE', 'CUCURAN / EAVE')
    txt_h = tpl_dims.get('text_height_mm', 2.5) * scale
    rdg_mid_x = _mh((roof_min_x + roof_max_x) / 2)
    msp.add_text(ridge_label,
                 dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_h}
                 ).set_placement(
                     (rdg_mid_x, _mh(ridge_y) + txt_h * 1.5),
                     align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Eave labels ──
    eave_mid_x = _mh((roof_min_x + roof_max_x) / 2)
    msp.add_text(eave_label,
                 dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_h}
                 ).set_placement(
                     (eave_mid_x, _mh(roof_max_y) + txt_h * 2),
                     align=TextEntityAlignment.MIDDLE_CENTER)
    msp.add_text(eave_label,
                 dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_h}
                 ).set_placement(
                     (eave_mid_x, _mh(roof_min_y) - txt_h * 2),
                     align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Slope arrows (§5.3c: one per grid bay, ridge toward eave) ──
    x_grids = sorted([g for g in grids if g.axis == 'x'], key=lambda g: g.position)
    n_bays = max(len(x_grids) - 1, 1)
    roof_w_mm = _mh(roof_max_x - roof_min_x)
    arrow_spacing = roof_w_mm / (n_bays + 1)
    arrow_head = 1.5 * scale  # mm in model space
    for i in range(1, n_bays + 1):
        ax = _mh(roof_min_x) + arrow_spacing * i
        # North slope (ridge → top eave)
        n_start = _mh(ridge_y) + 5 * scale
        n_end = _mh(ridge_y) + (_mh(roof_max_y) - _mh(ridge_y)) * 0.6
        msp.add_line((ax, n_start), (ax, n_end),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        # Arrowhead (triangle)
        msp.add_solid([(ax, n_end), (ax - arrow_head, n_end - arrow_head * 1.5),
                       (ax + arrow_head, n_end - arrow_head * 1.5)],
                      dxfattribs={'layer': 'A-ANNO-DIMS'})
        # South slope (ridge → bottom eave)
        s_start = _mh(ridge_y) - 5 * scale
        s_end = _mh(ridge_y) - (_mh(ridge_y) - _mh(roof_min_y)) * 0.6
        msp.add_line((ax, s_start), (ax, s_end),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        msp.add_solid([(ax, s_end), (ax - arrow_head, s_end + arrow_head * 1.5),
                       (ax + arrow_head, s_end + arrow_head * 1.5)],
                      dxfattribs={'layer': 'A-ANNO-DIMS'})

    # ── Overhang dimensions ──
    overhang_n = (roof_max_y - bld_max_y) * 1000
    overhang_s = (bld_min_y - roof_min_y) * 1000
    overhang_e = (roof_max_x - bld_max_x) * 1000
    overhang_w = (bld_min_x - roof_min_x) * 1000
    _log(f"§5.3 Overhang N={overhang_n:.0f} S={overhang_s:.0f} "
         f"E={overhang_e:.0f} W={overhang_w:.0f} mm")

    snap = tpl_dims.get('snap_module_mm', 100)
    dim_off = tpl_dims.get('tier_2_offset_mm', 18) * scale

    # North overhang (right side)
    if overhang_n > 50:
        dim_x = _mh(roof_max_x) + dim_off
        y_wall = _mh(bld_max_y)
        y_eave = _mh(roof_max_y)
        msp.add_line((dim_x, y_wall), (dim_x, y_eave),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
        val = int(round(overhang_n / snap) * snap)
        msp.add_text(str(val),
                     dxfattribs={'layer': 'A-ANNO-DIMS', 'height': txt_h, 'rotation': 90}
                     ).set_placement(
                         (dim_x + txt_h, (y_wall + y_eave) / 2),
                         align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Grids (§4.4: same bay grids as floor plan) ──
    columns = [e for e in elements.get('other', []) if e.ifc_class == 'IfcColumn']
    grids = snap_grids(derive_grids(walls, columns=columns, template=tpl))
    grid_ext = tpl_grid.get('extend_beyond_building_mm', 15) * scale
    bubble_r = tpl_grid.get('bubble_radius_mm', 4.0) * scale
    bubble_lw = int(tpl_grid.get('bubble_stroke_mm', 0.25) * 100)
    txt_grid_h = tpl_grid.get('label_font_height_mm', 3.0) * scale
    grid_gap = tpl_dims.get('extension_gap_mm', 2.0) * scale

    # Use roof extent for grid line boundaries
    r_min_x = _mh(roof_min_x)
    r_max_x = _mh(roof_max_x)
    r_min_y = _mh(roof_min_y)
    r_max_y = _mh(roof_max_y)

    for g in grids:
        pos = g.position * MM
        if g.axis == 'x':
            msp.add_line((pos, r_min_y - grid_ext),
                         (pos, r_max_y + grid_ext + bubble_r * 2 + grid_gap),
                         dxfattribs={'layer': 'A-GRID', 'lineweight': lw_grid})
            for cy in (r_max_y + grid_ext + bubble_r,
                       r_min_y - grid_ext - bubble_r):
                msp.add_circle((pos, cy), bubble_r,
                               dxfattribs={'layer': 'A-GRID', 'lineweight': bubble_lw})
                msp.add_text(g.label,
                             dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_grid_h}
                             ).set_placement((pos, cy),
                                             align=TextEntityAlignment.MIDDLE_CENTER)
        else:
            msp.add_line((r_min_x - grid_ext, pos),
                         (r_max_x + grid_ext + bubble_r * 2 + grid_gap, pos),
                         dxfattribs={'layer': 'A-GRID', 'lineweight': lw_grid})
            for cx in (r_max_x + grid_ext + bubble_r,
                       r_min_x - grid_ext - bubble_r):
                msp.add_circle((cx, pos), bubble_r,
                               dxfattribs={'layer': 'A-GRID', 'lineweight': bubble_lw})
                msp.add_text(g.label,
                             dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_grid_h}
                             ).set_placement((cx, pos),
                                             align=TextEntityAlignment.MIDDLE_CENTER)
    _log(f"§2.3 Roof grids: {len(grids)}")

    # ── Bay dimensions ──
    dims = generate_dimensions(grids)
    tick_len = tpl_dims.get('tick_half_length_mm', 1.5) * scale
    for d in dims:
        s = d.start * MM
        e = d.end * MM
        off = d.offset * scale
        if d.axis == 'x':
            dim_y = r_max_y + grid_ext + bubble_r * 2 + grid_gap + off
            msp.add_line((s, dim_y), (e, dim_y),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((s, dim_y - tick_len), (s, dim_y + tick_len),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((e, dim_y - tick_len), (e, dim_y + tick_len),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_text(d.text,
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'height': txt_h}
                         ).set_placement(((s + e) / 2, dim_y + txt_h),
                                         align=TextEntityAlignment.MIDDLE_CENTER)
        else:
            dim_x = r_min_x - grid_ext - bubble_r * 2 - grid_gap - off
            msp.add_line((dim_x, s), (dim_x, e),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((dim_x - tick_len, s), (dim_x + tick_len, s),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_line((dim_x - tick_len, e), (dim_x + tick_len, e),
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': lw_dim})
            msp.add_text(d.text,
                         dxfattribs={'layer': 'A-ANNO-DIMS', 'height': txt_h,
                                     'rotation': 90}
                         ).set_placement((dim_x - txt_h, (s + e) / 2),
                                         align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Sheet layout ──
    _draw_sheet_layout(doc, msp, tpl, r_min_x, r_max_x, r_min_y, r_max_y,
                       drawing_title='ROOF PLAN', drawing_no='A-06', scale=scale)

    # North arrow
    na = tpl.get('north_arrow', {})
    na_size = na.get('size_mm', 8) * scale

    _log(f"§2.8 Roof plan: {len(roofs)} roof(s), {len(grids)} grids, "
         f"ridge Y={ridge_y:.3f}m")
    doc.saveas(out_dxf)
    _log(f"  → {out_dxf}")
    _audit_dxf(doc, out_dxf, "ROOF PLAN")


# ─────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────

def _svg_fingerprint(svg_path: str) -> dict:
    """Extract a white-box fingerprint from an SVG for comparison.
    §9.6 R5/R6: entity counts, text content, grid labels."""
    import re
    if not os.path.exists(svg_path):
        return {}
    with open(svg_path) as f:
        content = f.read()
    fp = {}
    fp['lines'] = len(re.findall(r'<line ', content))
    fp['rects'] = len(re.findall(r'<rect ', content))
    fp['circles'] = len(re.findall(r'<circle', content))
    fp['texts'] = len(re.findall(r'<text ', content))
    fp['polygons'] = len(re.findall(r'<polygon', content))
    fp['paths'] = len(re.findall(r'<path', content))
    fp['dasharrays'] = len(re.findall(r'stroke-dasharray', content))
    # Extract all text content
    text_vals = re.findall(r'>([^<]+)</text>', content)
    fp['text_set'] = sorted(set(text_vals))
    # Grid labels: single-char texts A-Z or 1-9
    fp['grid_labels'] = sorted(set(t for t in text_vals
                                    if len(t) <= 2 and t != 'N'
                                    and (t.isalpha() or t.isdigit())))
    # Dimension values: pure digit texts
    fp['dim_values'] = sorted(set(t for t in text_vals if t.isdigit()))
    return fp


def _compare_fingerprints(current: dict, reference: dict) -> tuple:
    """Compare two SVG fingerprints. Returns (score, total, details).
    §9.6 R5: archive regression check."""
    if not current or not reference:
        return 0, 0, ['no data to compare']
    checks = 0
    matched = 0
    details = []

    # Grid labels
    checks += 1
    if current.get('grid_labels') == reference.get('grid_labels'):
        matched += 1
        details.append(f'  GRID_LABELS: MATCH {current["grid_labels"]}')
    else:
        details.append(f'  GRID_LABELS: DIFFER current={current.get("grid_labels")} '
                       f'archive={reference.get("grid_labels")}')

    # Dimension values
    checks += 1
    if current.get('dim_values') == reference.get('dim_values'):
        matched += 1
        details.append(f'  DIM_VALUES: MATCH {current["dim_values"]}')
    else:
        details.append(f'  DIM_VALUES: DIFFER current={current.get("dim_values")} '
                       f'archive={reference.get("dim_values")}')

    # Entity counts — dasharray count (grid rendering)
    checks += 1
    if current.get('dasharrays', 0) == reference.get('dasharrays', 0):
        matched += 1
        details.append(f'  DASHDOT_COUNT: MATCH {current["dasharrays"]}')
    else:
        details.append(f'  DASHDOT_COUNT: DIFFER current={current.get("dasharrays")} '
                       f'archive={reference.get("dasharrays")}')

    # Circle count (grid bubbles)
    checks += 1
    if current.get('circles', 0) == reference.get('circles', 0):
        matched += 1
        details.append(f'  CIRCLE_COUNT: MATCH {current["circles"]}')
    else:
        details.append(f'  CIRCLE_COUNT: DIFFER current={current.get("circles")} '
                       f'archive={reference.get("circles")}')

    # Text count
    checks += 1
    if current.get('texts', 0) == reference.get('texts', 0):
        matched += 1
        details.append(f'  TEXT_COUNT: MATCH {current["texts"]}')
    else:
        details.append(f'  TEXT_COUNT: DIFFER current={current.get("texts")} '
                       f'archive={reference.get("texts")}')

    return matched, checks, details


def _prune_generations(folder: str, view: str, ext: str, keep: int = 2):
    """§9.6 R3: keep only 2 generations per view. Remove oldest."""
    import glob as _glob
    pattern = os.path.join(folder, f'{view}_*.{ext}')
    files = sorted(_glob.glob(pattern))  # oldest first (timestamp sort)
    while len(files) > keep:
        os.remove(files.pop(0))


def _prev_file(folder: str, view: str, ext: str) -> str:
    """Find previous generation file (second-newest) for a view."""
    import glob as _glob
    pattern = os.path.join(folder, f'{view}_*.{ext}')
    files = sorted(_glob.glob(pattern))
    if len(files) >= 2:
        return files[-2]
    return ''


# §9.6 R2: short view names
_VIEW_SHORT = {
    'floor_plan': 'FLOOR',
    'roof_plan': 'ROOF',
    'front': 'FRONT',
    'rear': 'REAR',
    'left': 'LEFT',
    'right': 'RIGHT',
}

# §9.6 R5: archive filename map (archive uses long names)
_ARCHIVE_MAP = {
    'FLOOR': 'floor_plan',
    'ROOF': 'roof_plan',
}


def main():
    parser = argparse.ArgumentParser(
        description='Generate professional DXF architectural drawings from BIM database.\n'
                    'Output: DXF R2010, model-space mm, AIA layers, ISO 128 line weights.')
    parser.add_argument('db_path', help='Path to compiled .db file (output.db)')
    parser.add_argument('--floor-plan', action='store_true',
                        help='Generate floor plan DXF')
    parser.add_argument('--elevation', metavar='FACE',
                        help='Generate elevation DXF: front|rear|left|right')
    parser.add_argument('--all', action='store_true',
                        help='Generate all drawings (floor + roof + 4 elevations)')
    parser.add_argument('--proof', action='store_true',
                        help='Convert DXF→SVG (spec §10.0)')
    parser.add_argument('--scale', type=int, default=100,
                        help='Drawing scale denominator (default 100 = 1:100)')
    args = parser.parse_args()

    if not os.path.exists(args.db_path):
        print(f"Database not found: {args.db_path}", file=sys.stderr)
        sys.exit(1)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    base_dir   = os.path.normpath(os.path.join(script_dir, '..'))
    out_dir    = os.path.join(base_dir, 'output')
    dxf_dir    = os.path.join(out_dir, 'DXF')
    svg_dir    = os.path.join(out_dir, 'SVG')
    os.makedirs(dxf_dir, exist_ok=True)
    os.makedirs(svg_dir, exist_ok=True)
    stem = os.path.splitext(os.path.basename(args.db_path))[0]
    ts = datetime.datetime.now().strftime('%Y%m%d_%H%M')

    # §3.0b / §9.6 R7: Read master page table from {PREFIX}_2D.json
    input_dir = os.path.join(base_dir, 'input')
    page_json = None
    if os.path.isdir(input_dir):
        for jf in sorted(os.listdir(input_dir)):
            if jf.endswith('_2D.json'):
                jf_path = os.path.join(input_dir, jf)
                with open(jf_path) as _jf:
                    _plan = json.load(_jf)
                    if _plan.get('db', '').replace('.db', '').replace('_extracted', '') in stem:
                        page_json = _plan
                        break

    # Build page list from JSON (--all) or CLI args (single view)
    _FILE_TO_FACE = {'FRONT': 'front', 'REAR': 'rear', 'LEFT': 'left', 'RIGHT': 'right'}
    if args.all and page_json:
        # §3.0b: iterate pages by status=DONE from master table
        pages_todo = [p for p in page_json['pages'] if p['status'] == 'DONE']
    elif args.all:
        # Fallback if no JSON found: legacy hardcoded list
        pages_todo = [
            {'file': 'FLOOR', 'type': 'plan',      'sheet': 'A-01'},
            {'file': 'FRONT', 'type': 'elevation',  'sheet': 'A-02'},
            {'file': 'REAR',  'type': 'elevation',  'sheet': 'A-03'},
            {'file': 'LEFT',  'type': 'elevation',  'sheet': 'A-04'},
            {'file': 'RIGHT', 'type': 'elevation',  'sheet': 'A-05'},
            {'file': 'ROOF',  'type': 'plan',        'sheet': 'A-06'},
        ]
    else:
        pages_todo = []
        if args.floor_plan:
            pages_todo.append({'file': 'FLOOR', 'type': 'plan', 'sheet': 'A-01'})
        if args.elevation:
            face = args.elevation
            short = _VIEW_SHORT.get(face, face.upper())
            pages_todo.append({'file': short, 'type': 'elevation', 'sheet': ''})
    if not pages_todo:
        pages_todo = [{'file': 'FLOOR', 'type': 'plan', 'sheet': 'A-01'}]  # default

    _LOG_LINES.clear()
    _VERDICTS.clear()
    _log(f"DXF generation: {stem} (scale 1:{args.scale})")
    _log(f"  Source: {args.db_path}")
    _log(f"  Output: {out_dir}/DXF/ and {out_dir}/SVG/")
    _log(f"  Timestamp: {ts}")
    if page_json:
        _log(f"  Master page table: {len(pages_todo)} DONE pages from {page_json.get('project','?')}_2D.json")
    else:
        _log(f"  Master page table: not found, using CLI args")

    # §9.6 R2: single-term filenames with timestamp
    generated = {}  # short_name → (dxf_path, svg_path)

    for page in pages_todo:
        short = page['file']
        ptype = page.get('type', 'plan')
        dxf_path = os.path.join(dxf_dir, f'{short}_{ts}.dxf')

        if short == 'FLOOR':
            write_floor_plan_dxf(args.db_path, dxf_path, scale=args.scale)
        elif short == 'ROOF':
            write_roof_plan_dxf(args.db_path, dxf_path, scale=args.scale)
        elif ptype == 'elevation':
            face = _FILE_TO_FACE.get(short, short.lower())
            write_elevation_dxf(args.db_path, face, dxf_path, scale=args.scale)
        else:
            _log(f"  SKIP {short}: no generator for type={ptype}")
            continue
        generated[short] = dxf_path

    # §9.6 R1: DXF→SVG (no PNG)
    if args.proof:
        for short, dxf_path in generated.items():
            if not os.path.exists(dxf_path):
                continue
            svg_path = os.path.join(svg_dir, f'{short}_{ts}.svg')
            try:
                _render_proof(dxf_path, svg_path)
                _log(f"DXF→SVG → {svg_path}")
            except Exception as e:
                _log(f"DXF→SVG failed for {short}: {e}")
                import traceback
                traceback.print_exc()

    # §9.6 R3: prune to 2 generations per view
    for short in generated:
        _prune_generations(dxf_dir, short, 'dxf', keep=2)
        _prune_generations(svg_dir, short, 'svg', keep=2)

    # §9.6 R6: Visible change detection (vs previous generation)
    _log("")
    _log("── VISIBLE CHANGE DETECTION ──")
    any_change = False
    change_details = []
    for short in generated:
        cur_svg = os.path.join(svg_dir, f'{short}_{ts}.svg')
        prev_svg = _prev_file(svg_dir, short, 'svg')
        cur_fp = _svg_fingerprint(cur_svg)
        prev_fp = _svg_fingerprint(prev_svg) if prev_svg else {}
        if not prev_fp:
            change_details.append(f'  {short}: no previous — first run')
            any_change = True
        else:
            diffs = []
            for key in ('lines', 'circles', 'texts', 'polygons', 'dasharrays'):
                c = cur_fp.get(key, 0)
                p = prev_fp.get(key, 0)
                if c != p:
                    diffs.append(f'{key}:{p}→{c}')
            if cur_fp.get('grid_labels') != prev_fp.get('grid_labels'):
                diffs.append(f'grids:{prev_fp.get("grid_labels")}→{cur_fp.get("grid_labels")}')
            if diffs:
                any_change = True
                change_details.append(f'  {short}: CHANGED — {", ".join(diffs)}')
            else:
                change_details.append(f'  {short}: identical to prev')
    for d in change_details:
        _log(d)
    _log(f"VISIBLE CHANGE: {'YES' if any_change else 'NO'}")

    # §9.6 R5: Archive regression check (FLOOR + ROOF only)
    _log("")
    _log("── ARCHIVE REGRESSION CHECK ──")
    archive_dir = os.path.join(base_dir, 'archive')
    archive_stem = page_json.get('building', stem) if page_json else stem
    for short in ('FLOOR', 'ROOF'):
        cur_svg = os.path.join(svg_dir, f'{short}_{ts}.svg')
        archive_suffix = _ARCHIVE_MAP.get(short, short.lower())
        archive_svg = os.path.join(archive_dir, f'{archive_stem}_{archive_suffix}.svg')
        if not os.path.exists(archive_svg):
            _log(f"  {short}: archive not found at {archive_svg}")
            continue
        cur_fp = _svg_fingerprint(cur_svg)
        arc_fp = _svg_fingerprint(archive_svg)
        matched, total, details = _compare_fingerprints(cur_fp, arc_fp)
        for d in details:
            _log(d)
        prev_svg = _prev_file(svg_dir, short, 'svg')
        prev_fp = _svg_fingerprint(prev_svg) if prev_svg else {}
        prev_matched = 0
        if prev_fp:
            prev_matched, _, _ = _compare_fingerprints(prev_fp, arc_fp)
        better = matched >= prev_matched if prev_fp else True
        _log(f"  ARCHIVE CHECK [{short}]: {matched}/{total} — Better: {'YES' if better else 'NO'}")

    _write_log(out_dir)


if __name__ == '__main__':
    main()
