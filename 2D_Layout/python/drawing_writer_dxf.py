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
        ds.dxf.dimscale = scale          # annotative scale
        ds.dxf.dimtxt   = TXT_DIM * scale / 100   # model-space text height
        ds.dxf.dimblk   = 'ARCHTICK'    # 45° tick, not arrow
        ds.dxf.dimexo   = 1.5 * scale / 100  # ext line offset
        ds.dxf.dimexe   = 2.5 * scale / 100  # ext line extension
        ds.dxf.dimdle   = 0.0
        ds.dxf.dimgap   = 1.0 * scale / 100  # text gap
    except Exception:
        pass  # dimstyle fields vary by ezdxf version

    return doc


def _mh(metres: float) -> float:
    """Convert metres to DXF model-space mm."""
    return metres * MM


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
    bubble_r  = BUBBLE_R_MM * scale / 100  # model-space radius

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
                                         'height': TXT_GRID * scale / 100}
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
                                         'height': TXT_GRID * scale / 100}
                             ).set_placement((cx, pos),
                                             align=TextEntityAlignment.MIDDLE_CENTER)

    doc.saveas(out_dxf)
    print(f"Floor plan DXF: {cut_count} cut polylines, {len(grids)} grid lines")
    print(f"  → {out_dxf}")


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
    txt_h    = TXT_DIM * scale / 100       # model-space text height

    # Minimum label spacing in model-space mm
    min_gap = TXT_DIM * 3 * scale / 100
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
    bubble_r   = BUBBLE_R_MM * scale / 100

    for g in face_grids:
        gx = _mh(g.position)
        msp.add_line((gx, _mh(GRD_Z - 0.2)), (gx, grid_above),
                     dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
        msp.add_circle((gx, grid_above + bubble_r + 300 * scale / 100),
                       bubble_r,
                       dxfattribs={'layer': 'A-GRID', 'lineweight': LW_HAIR})
        msp.add_text(g.label,
                     dxfattribs={'layer': 'A-ANNO-TEXT', 'height': TXT_GRID * scale / 100}
                     ).set_placement(
                         (gx, grid_above + bubble_r + 300 * scale / 100),
                         align=TextEntityAlignment.MIDDLE_CENTER)

    # ── Bay dimensions ──
    if len(face_grids) >= 2:
        dim_y = grid_above + bubble_r * 2 + 600 * scale / 100
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
                    override={'dimpost': f'{int(snapped)}<>'},
                )
                dim.render()
            except Exception:
                # Fallback: plain line + text
                mid_x = (xa + xb) / 2
                msp.add_line((xa, dim_y), (xb, dim_y),
                             dxfattribs={'layer': 'A-ANNO-DIMS', 'lineweight': LW_HAIR})
                msp.add_text(str(int(snapped)),
                             dxfattribs={'layer': 'A-ANNO-TEXT',
                                         'height': TXT_DIM * scale / 100}
                             ).set_placement((mid_x, dim_y + 200 * scale / 100),
                                             align=TextEntityAlignment.MIDDLE_CENTER)

    doc.saveas(out_dxf)
    print(f"Elevation DXF ({face}): {len(face_elems)} elements, "
          f"{len(face_grids)} grids")
    print(f"  → {out_dxf}")


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


if __name__ == '__main__':
    main()
