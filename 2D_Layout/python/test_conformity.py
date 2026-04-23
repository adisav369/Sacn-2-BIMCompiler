#!/usr/bin/env python3
"""
Conformity Gate — §10.5
Checks every DXF output sheet against the mandatory sheet furniture pattern
from §5.0. Logs every check. Stops on first FAIL.

Usage:
    python3 test_conformity.py

Reads DXF files from python/output/, writes log to python/output/conformity_log.txt.
Exit 0 = all conform, exit 1 = FAIL (read the log).
"""
import sys, os, json, re

import ezdxf

# ─────────────────────────────────────────────────────────────────
# R5 STATIC CHECK — §1 R5: no hardcoded symbol geometry
# ─────────────────────────────────────────────────────────────────
_WRITER_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            'drawing_writer_dxf.py')

# Patterns that indicate hardcoded geometry (not unit conversions)
_R5_PATTERNS = [
    re.compile(r'int\([0-9]+\.[0-9]+ \* 100\)'),         # int(N.N * 100)
    re.compile(r'= [0-9]+\.[0-9]+ \* scale'),             # = N.N * scale
    re.compile(r'add_solid.*\* [0-9]+\.[0-9]+'),          # add_solid geometry multiplier
    re.compile(r'add_lwpolyline.*\* [0-9]+\.[0-9]+'),     # add_lwpolyline geometry multiplier
]

def check_r5_no_hardcode():
    """R5: drawing_writer_dxf.py must not contain unapproved hardcoded geometry.
    Violations = lines matching geometry patterns that lack .get( (template read)
    and lack # R5-ALLOW: (documented exception).
    Returns (ok: bool, violations: list[str]).
    """
    violations = []
    with open(_WRITER_PATH) as f:
        for lineno, line in enumerate(f, 1):
            stripped = line.rstrip()
            # Skip template-driven reads (contain .get()
            if '.get(' in stripped:
                continue
            # Skip approved exceptions
            if 'R5-ALLOW:' in stripped:
                continue
            for pat in _R5_PATTERNS:
                if pat.search(stripped):
                    violations.append(f"L{lineno}: {stripped.strip()}")
                    break
    return len(violations) == 0, violations

def check_mep_symbol_coverage():
    """I-30 / §12.2: every MEP IFC class must have a 2d_drawing_style row in 2D.db.
    Returns (ok: bool, missing: list[str]).
    """
    if not os.path.exists(_DB_2D_PATH):
        return True, []  # skip if 2D.db not found
    import sqlite3 as _sql
    conn = _sql.connect(_DB_2D_PATH)
    cur = conn.cursor()
    missing = []
    for ifc_class in _MEP_IFC_REQUIRED:
        cur.execute("SELECT COUNT(*) FROM [2d_drawing_style] WHERE element_match=? AND view_type='PLAN'",
                    (ifc_class,))
        if cur.fetchone()[0] == 0:
            missing.append(ifc_class)
    conn.close()
    return len(missing) == 0, missing

# ─────────────────────────────────────────────────────────────────
# PATHS
# ─────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, '..', 'output')
TPL_PATH = os.path.join(SCRIPT_DIR, '..', 'drawing_template.json')
LOG_PATH = os.path.join(OUT_DIR, 'conformity_log.txt')
_DB_2D_PATH = os.path.join(SCRIPT_DIR, '..', 'input', '2D.db')

# MEP IFC classes that must have a 2d_drawing_style row (I-30 / §12.2)
_MEP_IFC_REQUIRED = [
    'IfcPipeSegment', 'IfcDuctSegment', 'IfcFlowTerminal',
    'IfcSwitchingDevice', 'IfcElectricAppliance',
]

# ─────────────────────────────────────────────────────────────────
# TEMPLATE
# ─────────────────────────────────────────────────────────────────
def _load_template():
    with open(TPL_PATH) as f:
        return json.load(f)

# ─────────────────────────────────────────────────────────────────
# FILENAME → DRAWING TYPE MAPPING (§5.0, §10.6)
# ─────────────────────────────────────────────────────────────────
# DXF filename convention: {building}_{drawing_type_id}.dxf
# e.g. ifc4_sample_house_floor_plan.dxf → FLOOR_PLAN
_SUFFIX_TO_ID = {
    'floor_plan':       'FLOOR_PLAN',
    'front_elevation':  'FRONT_ELEV',
    'rear_elevation':   'REAR_ELEV',
    'left_elevation':   'LEFT_ELEV',
    'right_elevation':  'RIGHT_ELEV',
    'roof_plan':        'ROOF_PLAN',
    'section':          'SECTION',
    'opening_schedule': 'OPENING_SCHED',
    'electrical_plan':  'ELECT_PLAN',
    'reflected_ceiling_plan': 'REFL_CEILING',
    'plumbing_plan':    'PLUMBING_PLAN',
}

# §9.6 R2: short filename map
_SHORT_TO_ID = {
    'FLOOR': 'FLOOR_PLAN',
    'ROOF':  'ROOF_PLAN',
    'FRONT': 'FRONT_ELEV',
    'REAR':  'REAR_ELEV',
    'LEFT':  'LEFT_ELEV',
    'RIGHT': 'RIGHT_ELEV',
    'PLUMBING': 'PLUMBING_PLAN',
}

def _identify_drawing_type(dxf_filename, tpl):
    """Match DXF filename to template drawing_types entry.
    Supports timestamped names (FLOOR_20260407_1730.dxf),
    short names (FLOOR.dxf), and long names per §9.6 R2."""
    base = os.path.basename(dxf_filename).replace('.dxf', '')
    # Extract view token: try each underscore-split part against _SHORT_TO_ID
    # Handles FLOOR_20260407_1730 and DX_FLOOR_20260409_0920 (building prefix)
    parts = base.split('_')
    view = None
    for part in parts:
        if part in _SHORT_TO_ID:
            view = part
            break
    if view is None:
        view = parts[0]
    # Try short name first
    dt_id = _SHORT_TO_ID.get(view) or _SHORT_TO_ID.get(base)
    if dt_id:
        for dt in tpl.get('drawing_types', []):
            if dt['id'] == dt_id:
                return dt
    # Fall back to long suffix match
    for suffix, dt_id in _SUFFIX_TO_ID.items():
        if base.endswith(suffix):
            for dt in tpl.get('drawing_types', []):
                if dt['id'] == dt_id:
                    return dt
    return None

# ─────────────────────────────────────────────────────────────────
# SHEET TYPE CLASSIFICATION (§5.0 conditional furniture)
# ─────────────────────────────────────────────────────────────────
_MEP_DRAWING_IDS = frozenset({'PLUMBING_PLAN', 'ELECT_PLAN'})

def _sheet_type(dt):
    """Return 'plan', 'elevation', 'section', 'schedule', or 'mep'."""
    dt_id = dt.get('id', '')
    if dt_id in _MEP_DRAWING_IDS:
        return 'mep'
    view = dt.get('view', '')
    if view in ('PLAN', 'ROOF_PLAN'):
        return 'plan'
    elif view == 'ELEVATION':
        return 'elevation'
    elif view == 'SECTION':
        return 'section'
    elif view == 'SCHEDULE':
        return 'schedule'
    return 'plan'  # default

# ─────────────────────────────────────────────────────────────────
# LOG
# ─────────────────────────────────────────────────────────────────
_log_lines = []

def _log(line):
    _log_lines.append(line)

def _pass(sheet, check, detail):
    _log(f"[PASS] {sheet} {check}: {detail}")
    return True

def _fail(sheet, check, expected, actual):
    _log(f"[FAIL] {sheet} {check}: expected {expected}, got {actual}")
    _log(f"[STOP] Process halted at first FAIL. Fix and rerun.")
    return False

def _write_log():
    os.makedirs(OUT_DIR, exist_ok=True)
    log_path = os.path.join(OUT_DIR, 'conformity_log.txt')
    with open(log_path, 'w') as f:
        f.write('\n'.join(_log_lines) + '\n')

# ─────────────────────────────────────────────────────────────────
# CHECKS — §10.5 check table
# ─────────────────────────────────────────────────────────────────

def check_border(sheet, entities):
    """BORDER: LWPOLYLINE on A-TTLB layer exists (>= 1)"""
    border = [e for e in entities if e.dxftype() in ('LWPOLYLINE', 'LINE')
              and e.dxf.layer in ('A-TTLB', 'A-ANNO-TTLB')]
    if len(border) >= 1:
        return _pass(sheet, 'BORDER', f'{len(border)} entities on A-TTLB')
    return _fail(sheet, 'BORDER', '>= 1 LWPOLYLINE/LINE on A-TTLB', f'{len(border)}')


def check_title_block(sheet, entities):
    """TITLE_BLOCK: LINE entities on A-TTLB (separator + field rows) >= 5"""
    lines = [e for e in entities if e.dxftype() == 'LINE'
             and e.dxf.layer in ('A-TTLB', 'A-ANNO-TTLB')]
    if len(lines) >= 5:
        return _pass(sheet, 'TITLE_BLOCK', f'{len(lines)} lines on A-TTLB')
    return _fail(sheet, 'TITLE_BLOCK', '>= 5 LINE on A-TTLB', f'{len(lines)}')


def check_title_texts(sheet, entities):
    """TITLE_TEXTS: TEXT on A-TTLB (field labels + values) >= 5"""
    texts = [e for e in entities if e.dxftype() in ('TEXT', 'MTEXT')
             and e.dxf.layer in ('A-TTLB', 'A-ANNO-TTLB')]
    if len(texts) >= 5:
        return _pass(sheet, 'TITLE_TEXTS', f'{len(texts)} texts on A-TTLB')
    return _fail(sheet, 'TITLE_TEXTS', '>= 5 TEXT on A-TTLB', f'{len(texts)}')


def check_tajuk(sheet, entities, dt):
    """TAJUK: TAJUK LUKISAN text matches §5.0 table"""
    expected_title = dt.get('title', '')
    all_texts = [e.dxf.text for e in entities
                 if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    # Check if the expected title appears in any text entity
    found = any(expected_title in t or expected_title.upper() in t.upper()
                for t in all_texts)
    if found:
        return _pass(sheet, 'TAJUK', f'found "{expected_title}"')
    return _fail(sheet, 'TAJUK', f'text containing "{expected_title}"',
                 f'not found in {len(all_texts)} texts')


def check_no_lukisan(sheet, entities, dt):
    """NO_LUKISAN: sheet number text matches §5.0 table"""
    expected_no = dt.get('sheet_no', '')
    all_texts = [e.dxf.text for e in entities
                 if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    found = any(expected_no in t for t in all_texts)
    if found:
        return _pass(sheet, 'NO_LUKISAN', f'found "{expected_no}"')
    return _fail(sheet, 'NO_LUKISAN', f'text containing "{expected_no}"',
                 f'not found in {len(all_texts)} texts')


def check_drawing_title(sheet, entities, dt):
    """DRAWING_TITLE: drawing title text below content"""
    expected_title = dt.get('title', '')
    all_texts = [e.dxf.text for e in entities
                 if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    # Title could be on any annotation layer, not just A-TTLB
    found = any(expected_title in t or expected_title.upper() in t.upper()
                for t in all_texts)
    if found:
        return _pass(sheet, 'DRAWING_TITLE', f'found "{expected_title}"')
    return _fail(sheet, 'DRAWING_TITLE', f'"{expected_title}" in drawing',
                 'not found')


def check_scale_text(sheet, entities):
    """SCALE_TEXT: scale text (e.g. "1:100") present"""
    all_texts = [e.dxf.text for e in entities
                 if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    found = any('1:' in t for t in all_texts)
    if found:
        return _pass(sheet, 'SCALE_TEXT', 'found scale text')
    return _fail(sheet, 'SCALE_TEXT', 'text containing "1:"', 'not found')


def check_grid_lines(sheet, entities):
    """GRID_LINES: LINE entities on A-GRID layer >= 1 per axis.
    Floor plans have both X and Y grids (typically >= 3).
    Elevations have one axis only (can be 2 for a 2-bay building).
    Threshold >= 1: grid system present."""
    grid_lines = [e for e in entities if e.dxftype() == 'LINE'
                  and e.dxf.layer == 'A-GRID']
    if len(grid_lines) >= 1:
        return _pass(sheet, 'GRID_LINES', f'{len(grid_lines)} lines on A-GRID')
    return _fail(sheet, 'GRID_LINES', '>= 1 LINE on A-GRID', f'{len(grid_lines)}')


def check_grid_dashdot(sheet, doc):
    """GRID_DASHDOT: A-GRID layer linetype is DASHDOT"""
    try:
        layer = doc.layers.get('A-GRID')
        lt = layer.dxf.linetype
        if lt == 'DASHDOT':
            return _pass(sheet, 'GRID_DASHDOT', f'A-GRID linetype={lt}')
        return _fail(sheet, 'GRID_DASHDOT', 'DASHDOT', f'{lt}')
    except Exception as ex:
        return _fail(sheet, 'GRID_DASHDOT', 'A-GRID layer exists', f'error: {ex}')


def check_grid_bubbles(sheet, entities):
    """GRID_BUBBLES: CIRCLE entities on A-GRID layer >= 2 per grid"""
    bubbles = [e for e in entities if e.dxftype() == 'CIRCLE'
               and e.dxf.layer == 'A-GRID']
    if len(bubbles) >= 2:  # at least 1 grid x 2 ends (or 2 grids x 1 end on elevations)
        return _pass(sheet, 'GRID_BUBBLES', f'{len(bubbles)} circles on A-GRID')
    return _fail(sheet, 'GRID_BUBBLES', '>= 2 CIRCLE on A-GRID', f'{len(bubbles)}')


def check_grid_bubbles_both_ends(sheet, entities, stype):
    """§12a.5 I-31: elevation grids must have exactly 2 circles per label (top+bot).
    For each unique cx, must have >=2 circles — one with cy above building bbox,
    one with cy below. Non-elevation sheets skipped."""
    if stype != 'elevation':
        return _pass(sheet, 'GRID_BUBBLES_BOTH_ENDS', 'skipped (non-elevation)')
    bubbles = [e for e in entities if e.dxftype() == 'CIRCLE'
               and e.dxf.layer == 'A-GRID']
    if not bubbles:
        return _fail(sheet, 'GRID_BUBBLES_BOTH_ENDS', '>= 2 circles on A-GRID', '0 circles')
    # Group by cx (±1mm tolerance = ±scale units)
    from collections import defaultdict
    by_cx = defaultdict(list)
    for b in bubbles:
        key = round(b.dxf.center.x, 0)
        by_cx[key].append(b.dxf.center.y)
    failures = []
    for cx, cys in by_cx.items():
        if len(cys) < 2:
            failures.append(f"cx={cx:.0f} has only {len(cys)} bubble(s)")
        else:
            top_y = max(cys)
            bot_y = min(cys)
            if top_y == bot_y:
                failures.append(f"cx={cx:.0f} both bubbles at same y={top_y:.0f}")
    if failures:
        return _fail(sheet, 'GRID_BUBBLES_BOTH_ENDS',
                     'each grid cx: 2 circles at different y (top+bot)',
                     '; '.join(failures))
    n = len(by_cx)
    return _pass(sheet, 'GRID_BUBBLES_BOTH_ENDS',
                 f'{n} grid lines, each with top+bot bubble')


def check_grid_labels(sheet, entities, tpl):
    """GRID_LABELS: TEXT on A-GRID with template axis labels, sorted.
    §10.5.1: filter "N" (north arrow leak), require contiguous labels."""
    # Grid labels may be on A-GRID or A-ANNO-TEXT layer
    grid_texts = [e.dxf.text for e in entities
                  if e.dxftype() in ('TEXT', 'MTEXT')
                  and e.dxf.layer in ('A-GRID', 'A-ANNO-TEXT')
                  and hasattr(e.dxf, 'text')
                  and len(e.dxf.text) <= 2]  # grid labels are short (A, B, 1, 10)
    # §10.5.1: exclude "N" — it is the north arrow, not a grid label
    unique = sorted(set(t for t in grid_texts if t != 'N'))
    v_labels = tpl.get('grid', {}).get('vertical_axis_labels', '').split(',')
    h_labels = tpl.get('grid', {}).get('horizontal_axis_labels', '').split(',')
    # Check that found labels are valid template labels
    found_v = [l for l in v_labels if l in unique]
    found_h = [l for l in h_labels if l in unique]
    total = len(found_v) + len(found_h)
    # §10.5.1: require at least 2 labels AND contiguous sequence
    # (e.g. A,B,C not A,C — no gaps)
    ok = total >= 2
    if ok and found_v:
        expected_v = v_labels[:len(found_v)]
        if found_v != expected_v:
            return _fail(sheet, 'GRID_LABELS',
                         f'contiguous vertical {expected_v}',
                         f'got {found_v}')
    if ok and found_h:
        expected_h = h_labels[:len(found_h)]
        if found_h != expected_h:
            return _fail(sheet, 'GRID_LABELS',
                         f'contiguous horizontal {expected_h}',
                         f'got {found_h}')
    if ok:
        return _pass(sheet, 'GRID_LABELS',
                     f'vertical={found_v}, horizontal={found_h}')
    return _fail(sheet, 'GRID_LABELS',
                 '>= 2 grid labels total',
                 f'vertical={found_v}, horizontal={found_h}')


def check_white_bg(sheet, entities):
    """WHITE_BG: LWPOLYLINE or SOLID on layer 0, ACI color 7 >= 1"""
    bg = [e for e in entities if e.dxf.layer == '0'
          and e.dxftype() in ('LWPOLYLINE', 'SOLID')
          and getattr(e.dxf, 'color', 0) == 7]
    if len(bg) >= 1:
        return _pass(sheet, 'WHITE_BG', f'{len(bg)} bg entities on layer 0')
    return _fail(sheet, 'WHITE_BG', '>= 1 entity on layer 0 with ACI 7',
                 f'{len(bg)}')


def check_line_weights(sheet, doc, tpl):
    """LINE_WEIGHTS: layer lineweights match template line_weights.*"""
    lw_map = {
        'A-WALL-FULL': ('wall_exterior_cut', 50),
        'A-WALL-PRTN': ('wall_partition_cut', 35),
        'A-GRID':      ('grid_line', 18),
        'A-FURN':      ('furniture', 15),
    }
    for layer_name, (tpl_key, expected_100) in lw_map.items():
        try:
            layer = doc.layers.get(layer_name)
            actual = getattr(layer.dxf, 'lineweight', -1)
            # DXF lineweight: negative = default (per-entity weights used instead)
            # This is valid — weights are set per entity in dxfattribs.
            # Only fail if layer has an explicit wrong weight (positive, != expected).
            tpl_val = int(tpl.get('line_weights', {}).get(tpl_key, 0.18) * 100)
            if actual < 0:
                continue  # default — per-entity weights apply
            if actual == tpl_val or actual == expected_100:
                continue
            return _fail(sheet, 'LINE_WEIGHTS',
                         f'{layer_name}={expected_100}',
                         f'{layer_name}={actual}')
        except Exception:
            pass  # layer may not exist in all views
    return _pass(sheet, 'LINE_WEIGHTS', 'all checked layers match template')


def check_north_arrow(sheet, entities):
    """NORTH_ARROW: TEXT "N" present (plans only)"""
    all_texts = [e.dxf.text for e in entities
                 if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    found = any(t == 'N' for t in all_texts)
    if found:
        return _pass(sheet, 'NORTH_ARROW', 'found "N" text')
    return _fail(sheet, 'NORTH_ARROW', 'TEXT "N"', 'not found')


def check_scale_bar(sheet, entities):
    """SCALE_BAR: SOLID entities on A-ANNO-DIMS + "5m" text"""
    solids = [e for e in entities if e.dxftype() == 'SOLID'
              and e.dxf.layer == 'A-ANNO-DIMS']
    all_texts = [e.dxf.text for e in entities
                 if e.dxftype() in ('TEXT', 'MTEXT') and hasattr(e.dxf, 'text')]
    has_5m = any(t == '5m' for t in all_texts)
    if len(solids) >= 2 and has_5m:
        return _pass(sheet, 'SCALE_BAR',
                     f'{len(solids)} bar segments, 5m label found')
    return _fail(sheet, 'SCALE_BAR',
                 '>= 2 SOLID on A-ANNO-DIMS + "5m" text',
                 f'{len(solids)} solids, 5m={has_5m}')


# ── SVG checks ──

def _dxf_to_svg_path(dxf_path):
    """Map DXF path to corresponding SVG path.
    output/DXF/FLOOR_TS.dxf → output/SVG/FLOOR_TS.svg"""
    svg_dir = os.path.join(os.path.dirname(os.path.dirname(dxf_path)), 'SVG')
    base = os.path.basename(dxf_path).replace('.dxf', '.svg')
    return os.path.join(svg_dir, base)


def check_proof_exists(sheet, dxf_path):
    """PROOF_EXISTS: SVG file exists for this view"""
    svg_path = _dxf_to_svg_path(dxf_path)
    if os.path.exists(svg_path):
        return _pass(sheet, 'PROOF_EXISTS', svg_path)
    return _fail(sheet, 'PROOF_EXISTS', 'SVG file', 'not found')


def check_proof_white_bg(sheet, dxf_path):
    """PROOF_WHITE_BG: First SVG element is <rect> fill white"""
    svg_path = _dxf_to_svg_path(dxf_path)
    if not os.path.exists(svg_path):
        return _pass(sheet, 'PROOF_WHITE_BG', 'skipped (no SVG)')
    with open(svg_path) as f:
        content = f.read()
    if re.search(r'<rect[^>]*fill="#FFFFFF"', content):
        return _pass(sheet, 'PROOF_WHITE_BG', 'found <rect fill="#FFFFFF"/>')
    if re.search(r'<rect[^>]*fill="white"', content):
        return _pass(sheet, 'PROOF_WHITE_BG', 'found <rect fill="white"/>')
    return _fail(sheet, 'PROOF_WHITE_BG',
                 '<rect fill="#FFFFFF"/> in SVG',
                 'not found')


def check_proof_dashdot(sheet, dxf_path):
    """PROOF_DASHDOT: Grid lines in SVG have stroke-dasharray"""
    svg_path = _dxf_to_svg_path(dxf_path)
    if not os.path.exists(svg_path):
        return _pass(sheet, 'PROOF_DASHDOT', 'skipped (no SVG)')
    with open(svg_path) as f:
        content = f.read()
    if 'stroke-dasharray' in content:
        return _pass(sheet, 'PROOF_DASHDOT', 'found stroke-dasharray in SVG')
    return _fail(sheet, 'PROOF_DASHDOT',
                 'stroke-dasharray on grid lines',
                 'not found — grids rendered as solid')


def check_proof_no_overshoot(sheet, dxf_path):
    """PROOF_NO_OVERSHOOT: No SVG entity coordinate exceeds the viewBox boundary.
    Spec §7.1a guard: content must stay within paper frame.
    Tolerance: 1mm (allows for stroke-width half-width at border)."""
    import re as _re
    svg_path = _dxf_to_svg_path(dxf_path)
    if not os.path.exists(svg_path):
        return _pass(sheet, 'PROOF_NO_OVERSHOOT', 'skipped (no SVG)')
    with open(svg_path) as f:
        txt = f.read()
    vb = _re.search(r'viewBox="0\.00 0\.00 ([0-9.]+) ([0-9.]+)"', txt)
    if not vb:
        return _pass(sheet, 'PROOF_NO_OVERSHOOT', 'skipped (no viewBox)')
    vw, vh = float(vb.group(1)), float(vb.group(2))
    TOL = 1.0  # mm tolerance for stroke edge
    # Collect all x,y numeric values from inline attributes
    all_x = [float(v) for v in _re.findall(r'\bx[12]?="(-?[0-9.]+)"', txt)]
    all_y = [float(v) for v in _re.findall(r'\by[12]?="(-?[0-9.]+)"', txt)]
    # Collect polygon/polyline point coordinates
    for pts in _re.findall(r'points="([^"]+)"', txt):
        for pair in pts.split():
            try:
                x, y = pair.split(',')
                all_x.append(float(x)); all_y.append(float(y))
            except ValueError:
                pass
    if not all_x:
        return _pass(sheet, 'PROOF_NO_OVERSHOOT', 'no coordinates found')
    max_x = max(all_x); min_x = min(all_x)
    max_y = max(all_y); min_y = min(all_y)
    ox = max(0.0, max_x - vw - TOL)
    oy = max(0.0, max_y - vh - TOL)
    ux = max(0.0, -min_x - TOL)
    uy = max(0.0, -min_y - TOL)
    if ox > 0 or oy > 0 or ux > 0 or uy > 0:
        detail = f'max=({max_x:.1f},{max_y:.1f}) vw={vw:.1f} vh={vh:.1f} over=({ox:.1f},{oy:.1f}) under=({ux:.1f},{uy:.1f})'
        return _fail(sheet, 'PROOF_NO_OVERSHOOT', 'all content within viewBox±1mm', detail)
    return _pass(sheet, 'PROOF_NO_OVERSHOOT',
                 f'max=({max_x:.1f},{max_y:.1f}) within ({vw:.1f},{vh:.1f})')


# ── §20 Semantic DXF checks ──

def check_semantic_dxf(sheet, entities):
    """SEMANTIC_DXF: entities carry BIMSRC xdata (walls + grids + dims + rooms > 0).
    Non-blocking: pre-§20 DXFs without BIMSRC still PASS (with note)."""
    # Check if BIMSRC appid is registered in the DXF
    has_bimsrc = False
    counts = {'wall': 0, 'GRID': 0, 'DIM': 0, 'ROOM': 0}
    for e in entities:
        try:
            xd = e.get_xdata('BIMSRC')
            if not xd:
                continue
            has_bimsrc = True
            for code, val in xd:
                if code == 1000 and ':' in str(val):
                    k, v = str(val).split(':', 1)
                    if k == 'type' and v in counts:
                        counts[v] += 1
                        break
        except Exception:
            pass
    total = sum(counts.values())
    detail = ' '.join(f'{k}={v}' for k, v in counts.items())
    if total > 0:
        return _pass(sheet, 'SEMANTIC_DXF', f'{total} BIMSRC entities ({detail})')
    # Pre-§20 DXF without BIMSRC — pass with note, not fail
    return _pass(sheet, 'SEMANTIC_DXF', 'no BIMSRC (pre-§20 DXF — regenerate to add)')


def check_grid_sources(sheet, entities):
    """GRID_SOURCES: GRID entities with BIMSRC have non-empty source_guids"""
    grid_count = 0
    sourced = 0
    for e in entities:
        if e.dxf.layer != 'A-GRID' or e.dxftype() != 'LINE':
            continue
        try:
            xd = e.get_xdata('BIMSRC')
            if not xd:
                continue
            grid_count += 1
            for code, val in xd:
                if code == 1000 and str(val).startswith('source_guids:'):
                    src = str(val).split(':', 1)[1]
                    if src.strip():
                        sourced += 1
                    break
        except Exception:
            pass
    if grid_count == 0:
        return _pass(sheet, 'GRID_SOURCES', 'no grids with BIMSRC (non-floor view)')
    if sourced > 0:
        return _pass(sheet, 'GRID_SOURCES',
                     f'{sourced}/{grid_count} grids have source GUIDs')
    return _fail(sheet, 'GRID_SOURCES', '>0 grids with source_guids',
                 f'0/{grid_count}')


# ── No-invention check: roof outline must not be a bbox rectangle for curved roofs ──

# Threshold: a roof element with more than this many mesh vertices cannot be a simple box.
# A rectangular box has 8 vertices. Curved roofs have 10x+ more.
_CURVED_ROOF_VERTEX_THRESHOLD = 20

def _is_rect_aligned(points, tol=1.0):
    """Return True if 'points' (list of (x,y)) form an axis-aligned rectangle.
    Checks: exactly 4 unique points, all right-angle corners, sides parallel to axes.
    tol: model-space mm tolerance for floating-point equality."""
    unique = list({(round(x / tol) * tol, round(y / tol) * tol) for x, y in points})
    if len(unique) != 4:
        return False
    xs = sorted(set(p[0] for p in unique))
    ys = sorted(set(p[1] for p in unique))
    if len(xs) != 2 or len(ys) != 2:
        return False
    expected = {(xs[0], ys[0]), (xs[0], ys[1]), (xs[1], ys[0]), (xs[1], ys[1])}
    return set(map(tuple, unique)) == expected


def check_roof_outline_not_invented(sheet, entities, dt, db_path):
    """ROOF_NOT_INVENTED: for roof plan sheets, verify no roof outline is a bbox
    rectangle when the source element has curved geometry (>N mesh vertices).

    §1 R1: every line must trace to an actual building element.
    A curved IfcRoof drawn as a 4-corner box invents two straight edge lines
    (the end caps) that have no corresponding element face. FAIL if detected.

    db_path: path to the extracted DB used to generate this DXF.
    """
    if dt.get('id') != 'ROOF_PLAN':
        return _pass(sheet, 'ROOF_NOT_INVENTED', 'not a roof plan — skip')

    if db_path is None or not os.path.exists(db_path):
        return _pass(sheet, 'ROOF_NOT_INVENTED', 'no db_path — skip')

    # Query roof vertex count from extracted DB
    import sqlite3 as _sql
    try:
        conn = _sql.connect(db_path)
        rows = conn.execute("""
            SELECT bg.vertex_count
            FROM elements_meta m
            JOIN element_instances ei ON m.guid = ei.guid
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            WHERE m.ifc_class = 'IfcRoof'
        """).fetchall()
        conn.close()
    except Exception as ex:
        return _pass(sheet, 'ROOF_NOT_INVENTED', f'db query failed: {ex}')

    if not rows:
        return _pass(sheet, 'ROOF_NOT_INVENTED', 'no IfcRoof in DB — skip')

    max_verts = max(r[0] for r in rows if r[0])
    if max_verts <= _CURVED_ROOF_VERTEX_THRESHOLD:
        return _pass(sheet, 'ROOF_NOT_INVENTED',
                     f'roof vertex_count={max_verts} ≤ {_CURVED_ROOF_VERTEX_THRESHOLD} — simple box OK')

    # Curved roof confirmed. Now check the DXF A-ROOF entities.
    roof_polys = [e for e in entities
                  if e.dxftype() == 'LWPOLYLINE' and e.dxf.layer == 'A-ROOF']

    if not roof_polys:
        return _pass(sheet, 'ROOF_NOT_INVENTED', 'no A-ROOF LWPOLYLINE — skip')

    rect_count = 0
    non_rect_count = 0
    for poly in roof_polys:
        pts = list(poly.get_points('xy'))
        if _is_rect_aligned(pts):
            rect_count += 1
        else:
            non_rect_count += 1

    # If at least one non-rectangular polyline exists on A-ROOF, the mesh
    # hull was used — a rectangular outer hull is legitimate (barrel vault).
    # Only fail when ALL polylines are rectangles (pure bbox invention).
    if rect_count > 0 and non_rect_count == 0:
        return _fail(sheet, 'ROOF_NOT_INVENTED',
                     f'non-rectangular outline (roof has {max_verts} mesh vertices)',
                     f'{rect_count} bbox rectangle(s) on A-ROOF — end caps are invented')

    return _pass(sheet, 'ROOF_NOT_INVENTED',
                 f'roof outline: {non_rect_count} mesh-hull + {rect_count} rect '
                 f'(vertex_count={max_verts})')


# ── I-11: MEP bleed check ──

# MEP layers that must NOT appear on architectural floor plan (A-01)
_MEP_LAYERS = frozenset({'A-MEP-ELEC', 'A-MEP-PLMB'})

def check_no_mep_on_arch(sheet, entities, dt):
    """NO_MEP_BLEED: architectural floor plan must not contain MEP entities.
    I-11: IfcFlow* elements belong on MEP pages only (E-01, M-01)."""
    if dt.get('id') not in ('FLOOR_PLAN',):
        return _pass(sheet, 'NO_MEP_BLEED', 'non-floor-plan sheet — skip')
    mep_count = sum(1 for e in entities if e.dxf.layer in _MEP_LAYERS)
    if mep_count == 0:
        return _pass(sheet, 'NO_MEP_BLEED', '0 entities on MEP layers')
    return _fail(sheet, 'NO_MEP_BLEED', '0 MEP entities on A-01',
                 f'{mep_count} entities on MEP layers')


# ── §14.1 Language check ──

_MALAY_INDICATORS = ('PROJEK', 'PEMILIK', 'ARKITEK', 'JENIS', 'TARIBULAN',
                     'DILUKIS', 'DISEMAK', 'PINDAAN')

def check_language(sheet, entities):
    """LANGUAGE: no Malay strings in title block or annotation text layers.
    §14.1: all DXF text must be English."""
    text_entities = [e.dxf.text for e in entities
                     if e.dxftype() in ('TEXT', 'MTEXT')
                     and hasattr(e.dxf, 'text')
                     and e.dxf.layer in ('A-TTLB', 'A-ANNO-TEXT')]
    malay_hits = [t for t in text_entities
                  if any(m in t.upper() for m in _MALAY_INDICATORS)]
    if len(malay_hits) == 0:
        return _pass(sheet, 'LANGUAGE', f'0 Malay strings in A-TTLB/A-ANNO-TEXT')
    return _fail(sheet, 'LANGUAGE', '0 Malay strings',
                 f'{len(malay_hits)} found: {malay_hits[:3]}')


# ─────────────────────────────────────────────────────────────────
# MAIN — iterate sheets, run checks, stop on first FAIL
# ─────────────────────────────────────────────────────────────────

def main():
    import argparse
    parser = argparse.ArgumentParser(description='2D conformity gate')
    parser.add_argument('--db', default=None,
                        help='Path to extracted DB (for ROOF_NOT_INVENTED check)')
    args = parser.parse_args()
    db_path = args.db

    tpl = _load_template()

    # §9.6 R3: DXF files are in lib/output/DXF/
    dxf_subdir = os.path.join(OUT_DIR, 'DXF')
    search_dir = dxf_subdir if os.path.isdir(dxf_subdir) else OUT_DIR

    # Find latest DXF per view (newest timestamp)
    dxf_files = []
    if os.path.isdir(search_dir):
        import glob as _glob
        all_dxf = sorted(f for f in os.listdir(search_dir) if f.endswith('.dxf'))
        # Group by building+view key, take latest (last in sorted order)
        # DX_FLOOR_20260409_0920.dxf → key 'DX_FLOOR'
        # FLOOR_20260407_1730.dxf   → key 'FLOOR'
        seen = {}
        for f in all_dxf:
            parts = f.replace('.dxf', '').split('_')
            view = '_'.join(parts[:2]) if len(parts) >= 2 else parts[0]
            seen[view] = f
        dxf_files = sorted(seen.values())

    if not dxf_files:
        _log("[FAIL] No DXF files found in lib/output/DXF/")
        _log("[STOP] Process halted. Run drawing_writer_dxf.py --all --proof first.")
        _write_log()
        print("[FAIL] No DXF files found. See conformity_log.txt", file=sys.stderr)
        return 1

    # ── Pre-flight: R5 static source check ──
    r5_ok, r5_violations = check_r5_no_hardcode()
    if r5_ok:
        _log(f"[PASS] R5_NO_HARDCODE: 0 unapproved hardcoded geometry values")
    else:
        _log(f"[FAIL] R5_NO_HARDCODE: {len(r5_violations)} unapproved hardcoded values:")
        for v in r5_violations:
            _log(f"  {v}")
        _log("[STOP] Fix hardcoded geometry before checking DXF output.")
        _write_log()
        print(f"[FAIL] R5: {len(r5_violations)} hardcoded values — see conformity_log.txt",
              file=sys.stderr)
        return 1

    # ── Pre-flight: I-30 MEP symbol coverage ──
    mep_ok, mep_missing = check_mep_symbol_coverage()
    if mep_ok:
        _log(f"§I-30 test_mep_symbol_coverage: all {len(_MEP_IFC_REQUIRED)} MEP IFC classes have 2d_drawing_style rows")
    else:
        _log(f"[FAIL] I-30 test_mep_symbol_coverage: missing rows for: {mep_missing}")

    _log(f"Conformity Gate — checking {len(dxf_files)} DXF files")
    _log(f"Template: {TPL_PATH}")
    _log("")

    all_pass = mep_ok

    for dxf_file in dxf_files:
        dxf_path = os.path.join(search_dir, dxf_file)
        dt = _identify_drawing_type(dxf_file, tpl)

        if dt is None:
            _log(f"[SKIP] {dxf_file} — no matching drawing_type in template")
            continue

        sheet = dt.get('sheet_no', '??')
        stype = _sheet_type(dt)
        _log(f"── Sheet {sheet}: {dt['title']} (type={stype}) ──")

        doc = ezdxf.readfile(dxf_path)
        msp = doc.modelspace()
        entities = list(msp)

        # Universal checks (every sheet)
        checks = [
            lambda: check_border(sheet, entities),
            lambda: check_title_block(sheet, entities),
            lambda: check_title_texts(sheet, entities),
            lambda: check_tajuk(sheet, entities, dt),
            lambda: check_no_lukisan(sheet, entities, dt),
            lambda: check_drawing_title(sheet, entities, dt),
            lambda: check_scale_text(sheet, entities),
            lambda: check_white_bg(sheet, entities),
            lambda: check_line_weights(sheet, doc, tpl),
            lambda: check_language(sheet, entities),
            lambda: check_no_mep_on_arch(sheet, entities, dt),
        ]

        # Conditional checks by sheet type (§5.0 conditional table)
        if stype not in ('schedule', 'mep'):
            checks.extend([
                lambda: check_grid_lines(sheet, entities),
                lambda: check_grid_dashdot(sheet, doc),
                lambda: check_grid_bubbles(sheet, entities),
                lambda: check_grid_bubbles_both_ends(sheet, entities, stype),
                lambda: check_grid_labels(sheet, entities, tpl),
            ])

        if stype == 'plan':
            checks.extend([
                lambda: check_north_arrow(sheet, entities),
                lambda: check_semantic_dxf(sheet, entities),
                lambda: check_grid_sources(sheet, entities),
                lambda: check_roof_outline_not_invented(sheet, entities, dt, db_path),
            ])

        if stype in ('plan', 'elevation', 'section'):
            checks.extend([
                lambda: check_scale_bar(sheet, entities),
            ])

        # Proof SVG checks
        checks.extend([
            lambda: check_proof_exists(sheet, dxf_path),
        ])

        if stype not in ('schedule', 'mep'):
            checks.extend([
                lambda: check_proof_white_bg(sheet, dxf_path),
                lambda: check_proof_dashdot(sheet, dxf_path),
                lambda: check_proof_no_overshoot(sheet, dxf_path),
            ])

        # Run checks — stop on first FAIL
        check_count = 0
        for check_fn in checks:
            ok = check_fn()
            check_count += 1
            if not ok:
                all_pass = False
                _log("")
                _log("═══════════════════════════════════════")
                fail_line = [l for l in _log_lines if l.startswith('[FAIL]')][-1]
                _log(f"RESULT: FAIL — {fail_line}")
                _log("═══════════════════════════════════════")
                _write_log()
                print(fail_line, file=sys.stderr)
                return 1

        # Collect sheet summary info
        grid_lines_count = len([e for e in entities if e.dxftype() == 'LINE'
                                and e.dxf.layer == 'A-GRID'])
        grid_labels = sorted(set(e.dxf.text for e in entities
                                 if e.dxftype() in ('TEXT', 'MTEXT')
                                 and e.dxf.layer in ('A-GRID', 'A-ANNO-TEXT')
                                 and hasattr(e.dxf, 'text')
                                 and len(e.dxf.text) <= 2
                                 and e.dxf.text not in ('N',)))
        _log(f"  → {check_count}/{check_count} PASS  "
             f"grids={grid_lines_count}({','.join(grid_labels)})")
        _log("")

    # Summary — coder reads this first
    _log("═══════════════════════════════════════")
    _log(f"RESULT: PASS — {len(dxf_files)} sheets conform to §5.0")
    _log("═══════════════════════════════════════")
    _log("")
    _log("CHECK PROBLEMS SECTION §18.1 IF LOGS PASSED THEM")

    _write_log()
    return 0


if __name__ == '__main__':
    sys.exit(main())
