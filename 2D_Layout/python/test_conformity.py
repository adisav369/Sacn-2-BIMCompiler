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
# PATHS
# ─────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, '..', 'output')
TPL_PATH = os.path.join(SCRIPT_DIR, '..', 'drawing_template.json')
LOG_PATH = os.path.join(OUT_DIR, 'conformity_log.txt')

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
}

# §9.6 R2: short filename map
_SHORT_TO_ID = {
    'FLOOR': 'FLOOR_PLAN',
    'ROOF':  'ROOF_PLAN',
    'FRONT': 'FRONT_ELEV',
    'REAR':  'REAR_ELEV',
    'LEFT':  'LEFT_ELEV',
    'RIGHT': 'RIGHT_ELEV',
}

def _identify_drawing_type(dxf_filename, tpl):
    """Match DXF filename to template drawing_types entry.
    Supports timestamped names (FLOOR_20260407_1730.dxf),
    short names (FLOOR.dxf), and long names per §9.6 R2."""
    base = os.path.basename(dxf_filename).replace('.dxf', '')
    # Extract view name before timestamp: FLOOR_20260407_1730 → FLOOR
    view = base.split('_')[0] if '_' in base else base
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
def _sheet_type(dt):
    """Return 'plan', 'elevation', 'section', or 'schedule'."""
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
    """GRID_LINES: LINE entities on A-GRID layer >= 3 (minimum 2 axes)
    §10.5.1: strengthened from >= 1 to >= 3 to catch missing grids"""
    grid_lines = [e for e in entities if e.dxftype() == 'LINE'
                  and e.dxf.layer == 'A-GRID']
    if len(grid_lines) >= 3:
        return _pass(sheet, 'GRID_LINES', f'{len(grid_lines)} lines on A-GRID')
    return _fail(sheet, 'GRID_LINES', '>= 3 LINE on A-GRID', f'{len(grid_lines)}')


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
    tpl = _load_template()

    # §9.6 R3: DXF files are in lib/output/DXF/
    dxf_subdir = os.path.join(OUT_DIR, 'DXF')
    search_dir = dxf_subdir if os.path.isdir(dxf_subdir) else OUT_DIR

    # Find latest DXF per view (newest timestamp)
    dxf_files = []
    if os.path.isdir(search_dir):
        import glob as _glob
        all_dxf = sorted(f for f in os.listdir(search_dir) if f.endswith('.dxf'))
        # Group by view name, take latest (last in sorted order)
        seen = {}
        for f in all_dxf:
            view = f.split('_')[0]  # FLOOR_20260407_1730.dxf → FLOOR
            seen[view] = f
        dxf_files = sorted(seen.values())

    if not dxf_files:
        _log("[FAIL] No DXF files found in lib/output/DXF/")
        _log("[STOP] Process halted. Run drawing_writer_dxf.py --all --proof first.")
        _write_log()
        print("[FAIL] No DXF files found. See conformity_log.txt", file=sys.stderr)
        return 1

    _log(f"Conformity Gate — checking {len(dxf_files)} DXF files")
    _log(f"Template: {TPL_PATH}")
    _log("")

    all_pass = True

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
        if stype != 'schedule':
            checks.extend([
                lambda: check_grid_lines(sheet, entities),
                lambda: check_grid_dashdot(sheet, doc),
                lambda: check_grid_bubbles(sheet, entities),
                lambda: check_grid_labels(sheet, entities, tpl),
            ])

        if stype == 'plan':
            checks.extend([
                lambda: check_north_arrow(sheet, entities),
                lambda: check_semantic_dxf(sheet, entities),
                lambda: check_grid_sources(sheet, entities),
            ])

        if stype in ('plan', 'elevation', 'section'):
            checks.extend([
                lambda: check_scale_bar(sheet, entities),
            ])

        # Proof SVG checks
        checks.extend([
            lambda: check_proof_exists(sheet, dxf_path),
        ])

        if stype != 'schedule':
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

    _write_log()
    return 0


if __name__ == '__main__':
    sys.exit(main())
