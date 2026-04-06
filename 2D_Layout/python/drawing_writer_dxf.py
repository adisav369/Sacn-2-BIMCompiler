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
    roof_silhouette,
    SNAP_MODULE,
)

# ─────────────────────────────────────────────────────────────────
# DRAWING CONSTANTS  (§14 — model-space mm, ISO 128 line weights)
# ─────────────────────────────────────────────────────────────────

SCALE       = 100     # 1:100 drawing scale
MM          = 1000.0  # world metres × MM → model-space mm

# ISO 128 line weights in mm (× 100 for DXF lineweight integer)
LW_GROUND   = 70    # 0.70 mm — ground line, boldest
LW_WALL_EXT = 50    # 0.50 mm — section-cut exterior walls
LW_WALL_INT = 35    # 0.35 mm — partitions, doors
LW_OPENING  = 35    # 0.35 mm — window/door outlines
LW_GLASS    = 25    # 0.25 mm — glazing / curtain wall
LW_HAIR     = 18    # 0.18 mm — ISO hairline: dims, grid, hatch (minimum)

# Text heights in mm on paper; DXF model-space height = paper_h × SCALE
TXT_DIM     = 2.5
TXT_ROOM    = 3.5
TXT_GRID    = 3.0
TXT_TITLE   = 5.0

# Grid bubble
BUBBLE_R_MM = 4.0   # paper mm radius

# Level markers
APRON_Z = -0.100    # concrete apron (m)
GRD_Z   = -0.250    # natural ground (m)

# AIA layer table: (name, ACI colour, linetype)
AIA_LAYERS = [
    ('A-WALL-FULL', 7,   'CONTINUOUS'),   # exterior cut wall outline
    ('A-WALL-PRTN', 7,   'CONTINUOUS'),   # partition cut wall outline
    ('A-WALL-PATT', 254, 'CONTINUOUS'),   # wall fill / hatch
    ('A-GLAZ',      5,   'CONTINUOUS'),   # glazing / curtain wall
    ('A-DOOR',      3,   'CONTINUOUS'),   # doors
    ('A-ROOF',      7,   'CONTINUOUS'),   # roof outline
    ('A-GRID',      8,   'CENTER'),       # reference grid lines
    ('A-ANNO-DIMS', 7,   'CONTINUOUS'),   # dimension strings
    ('A-ANNO-TEXT', 7,   'CONTINUOUS'),   # labels and room names
    ('A-FURN',      9,   'CONTINUOUS'),   # furniture / below-cut elements
    ('A-ELEV-WALL', 7,   'CONTINUOUS'),   # elevation wall outlines
    ('A-ELEV-LEVL', 2,   'CONTINUOUS'),   # level marker lines
]

# ─────────────────────────────────────────────────────────────────
# DOC SETUP
# ─────────────────────────────────────────────────────────────────

def _new_doc(scale: int = SCALE) -> ezdxf.document.Drawing:
    """Create a new DXF R2010 document with AIA layers and ARCH_JKR dimstyle."""
    doc = ezdxf.new('R2010')

    # Units: mm
    doc.header['$INSUNITS'] = 4
    # Linetype scale: at 1:100, LTSCALE=1; at 1:50 use 0.5, etc.
    doc.header['$LTSCALE'] = scale / 100.0
    doc.header['$MEASUREMENT'] = 1  # metric

    # Load standard linetypes (CENTER, HIDDEN, DASHED)
    doc.linetypes.add('CENTER',  [0.0],
        description='Center __ . __ . __')
    doc.linetypes.add('HIDDEN',  [0.0],
        description='Hidden -- -- -- --')

    # AIA layers
    for name, color, ltype in AIA_LAYERS:
        if ltype not in ('CONTINUOUS',):
            try:
                doc.layers.add(name=name, color=color, linetype=ltype)
            except Exception:
                doc.layers.add(name=name, color=color)
        else:
            doc.layers.add(name=name, color=color)

    # ARCH_JKR dimstyle: tick marks (JKR convention), model-space text
    try:
        ds = doc.dimstyles.new('ARCH_JKR')
        ds.dxf.dimscale = scale          # annotative scale (multiplies all dim geometry)
        ds.dxf.dimtxt   = TXT_DIM       # paper-mm text height (dimscale applies)
        ds.dxf.dimblk   = 'ARCHTICK'    # 45° tick, not arrow
        ds.dxf.dimexo   = 1.5           # ext line offset (paper mm)
        ds.dxf.dimexe   = 2.5           # ext line extension (paper mm)
        ds.dxf.dimdle   = 0.0
        ds.dxf.dimgap   = 1.0           # text gap (paper mm)
    except Exception:
        pass  # dimstyle fields vary by ezdxf version

    return doc


def _mh(metres: float) -> float:
    """Convert metres to DXF model-space mm."""
    return metres * MM


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

    if fill_pct < 10 if (width_mm > 0 and height_mm > 0 and geo_w > 0) else False:
        detail_parts.append(f"COMPOSITION WARN: building {fill_pct:.0f}% of area")

    ok = has_geometry and text_ok
    _VERDICTS.append((view_type, ok, ", ".join(detail_parts)))

    # ── TBLKLTN completeness check (post-process) ──
    # Reads 2D_metadata.db as the reference spec for a professional drawing.
    # Compares DXF actual counts against metadata-defined expectations.
    # Like Rosetta Stone: expected vs actual, quantitative, per feature.
    _log(f"    ── TBLKLTN Completeness (vs 2D_metadata.db) ──")
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
            ("Bay dimensions",            counts.get('DIMENSION', 0) > 0,
                                                              str(counts.get('DIMENSION', 0)), "1 per grid bay"),
            ("Room labels",               any(t in meta['room_types'] for t in text_strs),
                                                              "0",             f"need: {', '.join(meta['room_types'][:5])}"),
            ("Room areas (m²)",           any('m²' in t or 'm2' in t for t in text_strs),
                                                              "0",             "per room from spatial data"),
            ("Door swing arcs",           False,               "0",             f"{meta['door_symbols']} symbols in ad_drawing_symbol"),
            ("Wall fill / hatch",         False,               "0",             f"{meta['wall_styles']} styles in ad_drawing_style"),
            ("Door/window tags",          any(t.startswith(('D','W')) and any(c.isdigit() for c in t) for t in text_strs),
                                                              "0",             f"tags: {meta['tag_types']}"),
            ("North arrow",               False,               "0",             "NORTH_ARROW in ad_drawing_symbol"),
            ("Drawing title",             any('PLAN' in t.upper() or 'PELAN' in t.upper() for t in text_strs),
                                                              "0",             f"'{meta['plan_title']}'"),
            ("Title block",               False,               "0",             f"{meta['title_fields']} fields in ad_title_block"),
            ("Scale bar",                 False,               "0",             "standard bar 0-1-2-5m"),
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
    """Read 2D_metadata.db and return reference counts for TBLKLTN comparison."""
    meta_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             '..', 'lib', 'input', '2D_metadata.db')
    ref = {
        'grid_axes': 2, 'bubble_r': 4.0,
        'grid_alpha_max': 'D', 'grid_num_max': '3',
        'room_types': [], 'door_symbols': 0, 'wall_styles': 0,
        'tag_types': '', 'plan_title': 'FLOOR PLAN',
        'title_fields': 0, 'level_count': 0,
    }
    if not os.path.exists(meta_path):
        return ref
    try:
        conn = sqlite3.connect(meta_path)
        cur = conn.cursor()
        # Room labels
        cur.execute("SELECT DISTINCT label_text FROM ad_room_label WHERE language='EN'")
        ref['room_types'] = [r[0] for r in cur.fetchall()]
        # Door/window symbols
        cur.execute("SELECT COUNT(*) FROM ad_drawing_symbol WHERE view_type='PLAN' "
                    "AND symbol_code LIKE 'DOOR%' OR symbol_code LIKE 'WINDOW%'")
        ref['door_symbols'] = cur.fetchone()[0]
        # Wall styles
        cur.execute("SELECT COUNT(*) FROM ad_drawing_style WHERE view_type='PLAN' "
                    "AND element_match LIKE '%Wall%'")
        ref['wall_styles'] = cur.fetchone()[0]
        # Annotation tags
        cur.execute("SELECT tag_prefix FROM ad_annotation_tag")
        ref['tag_types'] = ', '.join(r[0] for r in cur.fetchall())
        # Drawing titles
        cur.execute("SELECT drawing_title FROM ad_drawing_type WHERE drawing_code='FLOOR_PLAN'")
        row = cur.fetchone()
        if row: ref['plan_title'] = row[0]
        for face in ('front', 'rear', 'left', 'right'):
            cur.execute("SELECT drawing_title FROM ad_drawing_type WHERE drawing_code=?",
                        (f'{face.upper()}_ELEV',))
            row = cur.fetchone()
            if row: ref[f'elev_{face}_title'] = row[0]
        # Title block
        cur.execute("SELECT COUNT(*) FROM ad_title_block")
        ref['title_fields'] = cur.fetchone()[0]
        # Level markers
        cur.execute("SELECT COUNT(*) FROM ad_level_marker")
        ref['level_count'] = cur.fetchone()[0]
        # Grid style
        cur.execute("SELECT bubble_radius FROM ad_grid_style LIMIT 1")
        row = cur.fetchone()
        if row: ref['bubble_r'] = row[0]
        conn.close()
    except Exception as e:
        _log(f"    (metadata read failed: {e})")
    return ref


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
    Implements 2D_ARCHITECTURAL_LAYOUT.md §14.5
    """
    elements = read_elements(db_path)
    walls    = elements['walls']
    doors    = elements['doors']
    windows  = elements['windows']
    furniture = elements['furniture']

    if not walls:
        print("No walls — skipping floor plan DXF", file=sys.stderr)
        return

    doc = _new_doc(scale)
    doc.appids.new('BIMGUID')
    msp = doc.modelspace()

    # ARC ifc_classes eligible for GUID xdata (roundtrip scope)
    _ARC_CLASSES = {'IfcWall', 'IfcWallStandardCase', 'IfcSlab',
                    'IfcDoor', 'IfcWindow', 'IfcPlate'}

    # ── Section cut at Z=1.0m ──
    cut_results = run_section_cut(db_path, cut_z=1.0)

    cut_count = 0
    for es in cut_results:
        if es.category != 'CUT' or not es.contours:
            continue
        if es.ifc_class in ('IfcWall', 'IfcWallStandardCase'):
            layer = ('A-WALL-FULL'
                     if 'Ext' in (es.element_name or '')
                     else 'A-WALL-PRTN')
            lw = LW_WALL_EXT if 'Ext' in (es.element_name or '') else LW_WALL_INT
        elif es.ifc_class in ('IfcPlate',):
            layer, lw = 'A-GLAZ', LW_GLASS
        elif es.ifc_class == 'IfcDoor':
            layer, lw = 'A-DOOR', LW_OPENING
        elif es.ifc_class == 'IfcWindow':
            layer, lw = 'A-GLAZ', LW_OPENING
        else:
            layer, lw = 'A-WALL-PRTN', LW_WALL_INT

        for contour in es.contours:
            pts = [(_mh(p[0]), _mh(p[1])) for p in contour.points]
            if len(pts) >= 2:
                pl = msp.add_lwpolyline(pts, close=True,
                                        dxfattribs={'layer': layer, 'lineweight': lw})
                if es.ifc_class in _ARC_CLASSES and es.guid:
                    pl.set_xdata('BIMGUID', [(1000, es.guid)])
                cut_count += 1

    # ── BELOW elements: bounding box on A-FURN ──
    for es in cut_results:
        if es.category != 'BELOW':
            continue
        bx0, by0, bx1, by1 = es.bbox_2d
        pts = [(_mh(bx0), _mh(by0)), (_mh(bx1), _mh(by0)),
               (_mh(bx1), _mh(by1)), (_mh(bx0), _mh(by1))]
        msp.add_lwpolyline(pts, close=True,
                           dxfattribs={'layer': 'A-FURN', 'lineweight': LW_HAIR})

    # ── Grid lines + bubbles ──
    grids = snap_grids(derive_grids(walls))
    all_elems = walls + doors + windows + furniture
    bld_min_x = min(e.min_x for e in all_elems) * MM
    bld_max_x = max(e.max_x for e in all_elems) * MM
    bld_min_y = min(e.min_y for e in all_elems) * MM
    bld_max_y = max(e.max_y for e in all_elems) * MM

    grid_ext  = 1500   # mm extension beyond building
    bubble_r  = BUBBLE_R_MM * scale  # model-space radius (4mm paper × 100 = 400mm)

    for g in grids:
        pos = g.position * MM
        if g.axis == 'x':
            # Vertical grid line
            msp.add_line((pos, bld_min_y - grid_ext),
                         (pos, bld_max_y + grid_ext + bubble_r * 2 + 300),
                         dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
            # Bubbles top and bottom
            for cy in (bld_max_y + grid_ext + bubble_r,
                       bld_min_y - grid_ext - bubble_r):
                msp.add_circle((pos, cy), bubble_r,
                               dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
                msp.add_text(g.label,
                             dxfattribs={'layer': 'A-ANNO-TEXT',
                                         'height': TXT_GRID * scale}
                             ).set_placement((pos, cy),
                                             align=TextEntityAlignment.MIDDLE_CENTER)
        else:
            # Horizontal grid line
            msp.add_line((bld_min_x - grid_ext, pos),
                         (bld_max_x + grid_ext + bubble_r * 2 + 300, pos),
                         dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
            for cx in (bld_max_x + grid_ext + bubble_r,
                       bld_min_x - grid_ext - bubble_r):
                msp.add_circle((cx, pos), bubble_r,
                               dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
                msp.add_text(g.label,
                             dxfattribs={'layer': 'A-ANNO-TEXT',
                                         'height': TXT_GRID * scale}
                             ).set_placement((cx, pos),
                                             align=TextEntityAlignment.MIDDLE_CENTER)

    doc.saveas(out_dxf)
    _log(f"Floor plan DXF: {cut_count} cut polylines, {len(grids)} grid lines")
    _log(f"  → {out_dxf}")
    _audit_dxf(doc, out_dxf, "FLOOR PLAN")


# ─────────────────────────────────────────────────────────────────
# ELEVATION
# ─────────────────────────────────────────────────────────────────

def write_elevation_dxf(db_path: str, face: str, out_dxf: str,
                        scale: int = SCALE):
    """Generate elevation DXF — model-space mm, AIA layers, mirrored for rear/right.

    Implements 2D_ARCHITECTURAL_LAYOUT.md §14.5, mirror rule from §14.7.
    """
    elements = read_elements(db_path)
    walls    = elements['walls']
    doors    = elements['doors']
    windows  = elements['windows']
    roofs    = elements['roofs']
    slabs    = elements['slabs']

    if not walls:
        return

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

    doc = _new_doc(scale)
    msp = doc.modelspace()

    # ── Wall/door/window outlines ──
    for e in face_elems:
        hh = h_of(e)
        vv = v_of(e)
        # Rectangle: h0, h1, v0, v1 in model-space mm
        h0, h1 = _mh(hh[0]), _mh(hh[1])
        v0, v1 = _mh(vv[0]), _mh(vv[1])
        pts = [(h0, v0), (h1, v0), (h1, v1), (h0, v1)]

        if e.ifc_class in ('IfcWall', 'IfcPlate'):
            if 'Glazed' in (e.name or '') or 'Curtain' in (e.name or ''):
                layer, lw = 'A-GLAZ', LW_GLASS
            else:
                layer, lw = 'A-ELEV-WALL', LW_WALL_EXT
        elif e.ifc_class == 'IfcWindow':
            layer, lw = 'A-GLAZ', LW_OPENING
            # Louvre lines: horizontal lines inside window at 150mm spacing
            win_h = _mh(vv[1] - vv[0])
            spacing = 150.0  # mm model-space
            n = max(1, int(win_h / spacing) - 1)
            for li in range(1, n + 1):
                lv = _mh(vv[0]) + li * (win_h / (n + 1))
                msp.add_line((h0, lv), (h1, lv),
                             dxfattribs={'layer': 'A-GLAZ', 'lineweight': LW_HAIR})
        elif e.ifc_class == 'IfcDoor':
            layer, lw = 'A-DOOR', LW_OPENING
        else:
            layer, lw = 'A-ELEV-WALL', LW_WALL_INT

        msp.add_lwpolyline(pts, close=True,
                           dxfattribs={'layer': layer, 'lineweight': lw})

    # ── Roof silhouette ──
    hull = roof_silhouette(db_path, face)  # already applies h_sign internally
    if hull:
        pts = [(_mh(h), _mh(z)) for h, z in hull]
        msp.add_lwpolyline(pts, close=True,
                           dxfattribs={'layer': 'A-ROOF', 'lineweight': LW_WALL_EXT})

    # ── Ground + apron lines ──
    all_vis = face_elems + roofs + slabs
    if not all_vis:
        doc.saveas(out_dxf)
        return

    h_min_m = min(h_of(e)[0] for e in all_vis)
    h_max_m = max(h_of(e)[1] for e in all_vis)
    ext = 0.5  # m extension each side

    # GRD. LEVEL line (boldest)
    msp.add_line((_mh(h_min_m - ext), _mh(GRD_Z)),
                 (_mh(h_max_m + ext), _mh(GRD_Z)),
                 dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': LW_GROUND})

    # FFL line (medium)
    msp.add_line((_mh(h_min_m - 0.3), 0.0),
                 (_mh(h_max_m + 0.3), 0.0),
                 dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': LW_WALL_INT})

    # ── Level markers (text + triangle on left side) ──
    levels = detect_levels(elements)
    levels = sorted(levels + [('APRON', APRON_Z), ('GRD', GRD_Z)],
                    key=lambda lv: lv[1])

    LEVEL_LABELS = {
        'FFL':   'GRD. FLOOR LEVEL',
        'CLG':   'BEAM/CEILING LEVEL',
        'RIDGE': 'RIDGE LEVEL',
        'APRON': 'APRON LEVEL',
        'GRD':   'GRD. LEVEL',
    }

    marker_x = _mh(h_min_m - ext - 0.5)  # left of ground line
    txt_h    = TXT_DIM * scale       # model-space text height

    # Minimum label spacing in model-space mm
    min_gap = TXT_DIM * 3 * scale
    label_ys = []
    for lbl, lz in levels:
        ly = _mh(lz)
        if label_ys and ly - label_ys[-1] < min_gap:
            ly = label_ys[-1] + min_gap
        label_ys.append(ly)

    for (lbl, lz), label_ly in zip(levels, label_ys):
        true_ly = _mh(lz)
        # Horizontal tick at true level
        msp.add_line((marker_x - _mh(1.8), true_ly),
                     (marker_x, true_ly),
                     dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': LW_WALL_INT})
        # Filled triangle pointer (SOLID entity — takes list of 3 points)
        tri = [(marker_x, true_ly),
               (marker_x - _mh(0.3), true_ly - _mh(0.18)),
               (marker_x - _mh(0.3), true_ly + _mh(0.18))]
        msp.add_solid(tri, dxfattribs={'layer': 'A-ELEV-LEVL'})
        # Leader from label to true position if offset
        if abs(label_ly - true_ly) > _mh(0.05):
            msp.add_line((marker_x - _mh(1.6), true_ly),
                         (marker_x - _mh(1.6), label_ly),
                         dxfattribs={'layer': 'A-ELEV-LEVL', 'lineweight': LW_HAIR})
        # Label name
        sign = '+' if lz >= 0 else ''
        label_str = f"{LEVEL_LABELS.get(lbl, lbl)}  {sign}{lz:.3f}"
        msp.add_text(label_str,
                     dxfattribs={'layer': 'A-ANNO-TEXT', 'height': txt_h}
                     ).set_placement((marker_x - _mh(2.0), label_ly),
                                     align=TextEntityAlignment.MIDDLE_RIGHT)

    # ── Grid lines on elevation ──
    grid_axis = 'x' if face in ('front', 'rear') else 'y'
    elev_grids = snap_grids(derive_grids(walls))
    face_grids_raw = [g for g in elev_grids if g.axis == grid_axis]
    face_grids = sorted(
        [type('G', (), {'label': g.label, 'position': g.position * h_sign})()
         for g in face_grids_raw],
        key=lambda g: g.position)

    v_max_m = max(v_of(e)[1] for e in all_vis)
    grid_above = _mh(v_max_m + 2.0)  # extend 2m above building top
    bubble_r   = BUBBLE_R_MM * scale

    for g in face_grids:
        gx = _mh(g.position)
        msp.add_line((gx, _mh(GRD_Z - 0.2)), (gx, grid_above),
                     dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
        msp.add_circle((gx, grid_above + bubble_r + 300),
                       bubble_r,
                       dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
        msp.add_text(g.label,
                     dxfattribs={'layer': 'A-ANNO-TEXT', 'height': TXT_GRID * scale}
                     ).set_placement(
                         (gx, grid_above + bubble_r + 300),
                         align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Bay dimensions ──
    if len(face_grids) >= 2:
        dim_y = grid_above + bubble_r * 2 + 600
        for i in range(len(face_grids) - 1):
            xa = _mh(face_grids[i].position)
            xb = _mh(face_grids[i + 1].position)
            bay_mm = abs(face_grids[i + 1].position - face_grids[i].position) * 1000
            snapped = round(bay_mm / SNAP_MODULE) * SNAP_MODULE
            # Simple dimension line (linear)
            try:
                dim = msp.add_linear_dim(
                    base=(0, dim_y),
                    p1=(xa, 0), p2=(xb, 0),
                    dimstyle='ARCH_JKR',
                    text=str(int(snapped)),
                )
                dim.render()
            except Exception:
                # Fallback: plain line + text
                mid_x = (xa + xb) / 2
                msp.add_line((xa, dim_y), (xb, dim_y),
                             dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': LW_HAIR})
                msp.add_text(str(int(snapped)),
                             dxfattribs={'layer': 'A-ANNO-TEXT',
                                         'height': TXT_DIM * scale}
                             ).set_placement((mid_x, dim_y + 200),
                                             align=TextEntityAlignment.MIDDLE_CENTER)

    doc.saveas(out_dxf)
    _log(f"Elevation DXF ({face}): {len(face_elems)} elements, "
         f"{len(face_grids)} grids")
    _log(f"  → {out_dxf}")
    _audit_dxf(doc, out_dxf, f"ELEVATION {face.upper()}")


# ─────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────

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
                        help='Generate all drawings (floor plan + 4 elevations)')
    parser.add_argument('--scale', type=int, default=100,
                        help='Drawing scale denominator (default 100 = 1:100)')
    args = parser.parse_args()

    if not os.path.exists(args.db_path):
        print(f"Database not found: {args.db_path}", file=sys.stderr)
        sys.exit(1)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir    = os.path.join(script_dir, 'output')
    os.makedirs(out_dir, exist_ok=True)
    stem = os.path.splitext(os.path.basename(args.db_path))[0]

    do_plan = args.floor_plan or args.all
    do_elev = []
    if args.all:
        do_elev = ['front', 'rear', 'left', 'right']
    elif args.elevation:
        do_elev = [args.elevation]
    if not do_plan and not do_elev:
        do_plan = True   # default: floor plan

    _LOG_LINES.clear()
    _VERDICTS.clear()
    _log(f"DXF generation: {stem} (scale 1:{args.scale})")
    _log(f"  Source: {args.db_path}")

    if do_plan:
        write_floor_plan_dxf(
            args.db_path,
            os.path.join(out_dir, f'{stem}_floor_plan.dxf'),
            scale=args.scale)

    for face in do_elev:
        write_elevation_dxf(
            args.db_path, face,
            os.path.join(out_dir, f'{stem}_{face}_elevation.dxf'),
            scale=args.scale)

    _write_log(out_dir)


if __name__ == '__main__':
    main()
