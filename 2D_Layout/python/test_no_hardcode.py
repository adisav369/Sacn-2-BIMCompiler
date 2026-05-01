# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Test: no hardcoded visual values in drawing code.

RULE: Every visual property (color, weight, dasharray, font size, label text)
must come from 2D.db tables or drawing_template.json — never from inline literals.

This test scans drawing_writer_dxf.py and _render_proof() for violations:
1. Hex colors (#RRGGBB) that aren't in a .get() fallback or _ACI_HEX table
2. Dash patterns (stroke-dasharray or dasharray literals) not from DB
3. Font sizes / line weights as bare floats not read via _lw()/_txt_h()/tpl.get()
4. Hardcoded label strings (bilingual text, level names) not from DB
5. Hardcoded numeric constants (>= 3 digits) not in ALLOWED set

The test reads the DB and template to know what values ARE allowed as fallbacks.
"""
import re
import sys
import os
import json
import sqlite3


def _load_allowed_from_template():
    """Extract all numeric/string values from drawing_template.json."""
    tpl_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            '..', 'drawing_template.json')
    allowed_colors = set()
    allowed_numbers = set()
    allowed_strings = set()
    if os.path.exists(tpl_path):
        with open(tpl_path) as f:
            tpl = json.load(f)
        _extract_values(tpl, allowed_colors, allowed_numbers, allowed_strings)
    return allowed_colors, allowed_numbers, allowed_strings


def _extract_values(obj, colors, numbers, strings, depth=0):
    """Recursively extract values from a JSON object."""
    if depth > 10:
        return
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k.startswith('_'):
                continue
            _extract_values(v, colors, numbers, strings, depth + 1)
    elif isinstance(obj, list):
        for v in obj:
            _extract_values(v, colors, numbers, strings, depth + 1)
    elif isinstance(obj, str):
        if re.match(r'^#[0-9A-Fa-f]{6}$', obj):
            colors.add(obj.upper())
        else:
            strings.add(obj)
    elif isinstance(obj, (int, float)):
        numbers.add(obj)


def _load_allowed_from_db():
    """Extract visual values from 2D.db drawing_part table."""
    allowed_colors = set()
    allowed_dasharrays = set()
    allowed_numbers = set()
    allowed_strings = set()
    for try_path in [
        os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     '..', 'input', '2D.db'),
        os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     '..', 'lib', 'input', '2D.db'),
    ]:
        if os.path.exists(try_path):
            conn = sqlite3.connect(try_path)
            cur = conn.cursor()
            # Drawing parts — all visual properties
            try:
                cur.execute("SELECT stroke_color, stroke_weight, fill_color, "
                            "dasharray, font_size, size_mm FROM [2d_drawing_part]")
                for row in cur.fetchall():
                    for v in row:
                        if v is None:
                            continue
                        if isinstance(v, str) and re.match(r'^#[0-9A-Fa-f]{6}$', v):
                            allowed_colors.add(v.upper())
                        elif isinstance(v, str) and ',' in v:
                            allowed_dasharrays.add(v)
                        elif isinstance(v, (int, float)):
                            allowed_numbers.add(v)
            except Exception:
                pass
            # Drawing styles
            try:
                cur.execute("SELECT stroke_color, stroke_weight, fill_color "
                            "FROM [2d_drawing_style]")
                for row in cur.fetchall():
                    for v in row:
                        if v is None:
                            continue
                        if isinstance(v, str) and re.match(r'^#[0-9A-Fa-f]{6}$', v):
                            allowed_colors.add(v.upper())
                        elif isinstance(v, (int, float)):
                            allowed_numbers.add(v)
            except Exception:
                pass
            # Level marker labels
            try:
                cur.execute("SELECT display_text FROM [2d_level_marker]")
                for (txt,) in cur.fetchall():
                    allowed_strings.add(txt)
            except Exception:
                pass
            conn.close()
            break
    return allowed_colors, allowed_dasharrays, allowed_numbers, allowed_strings


# Lines that are allowed to have visual literals (lookup tables, constants block)
EXEMPT_PATTERNS = [
    r'_ACI_HEX',           # ACI color lookup table
    r'_MAP\s*=',            # mapping dictionaries
    r'^\s*#',               # comments
    r'_log\(',              # log lines
    r'print\(',             # print lines
    r'"""',                 # docstrings
    r"'''",                 # docstrings
    r'\.get\(',             # template .get() fallback — allowed
    r'_lw\(',               # reads from template
    r'_txt_h\(',            # reads from template
    r'_hex_to_aci\(',       # color conversion
    r'_linestyle_to_dxf\(', # linetype conversion
    r'_dasharray\(',        # dasharray function
    r'_read_2d_db_levels',  # DB reader
    r'_read_metadata_ref',  # DB reader
    r'read_drawing_metadata', # DB reader
    r'APRON_Z\s*=',         # fallback constant (DB overrides at runtime)
    r'GRD_Z\s*=',           # fallback constant (DB overrides at runtime)
    r'SCALE\s*=',           # unit constant
    r'MM\s*=',              # unit constant
    r'description=',        # ezdxf linetype description strings
]

# Functions to scan (drawing and rendering code)
SCAN_FUNCTIONS = [
    'def write_floor_plan_dxf',
    'def write_elevation_dxf',
    'def write_roof_plan_dxf',
    'def _render_proof',
    'def _draw_sheet_layout',
    'def write_mep_plan_dxf',
    'def _draw_mep_symbol',
]


def test_no_hardcoded_colors():
    """Test: no inline hex color strings that aren't from DB/template."""
    tpl_colors, _, _ = _load_allowed_from_template()
    db_colors, _, _, _ = _load_allowed_from_db()
    allowed = tpl_colors | db_colors | {'#FFFFFF', '#000000'}  # B&W always OK

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        'drawing_writer_dxf.py')
    with open(path) as f:
        lines = f.readlines()

    violations = []
    in_func = False
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if any(stripped.startswith(fn) for fn in SCAN_FUNCTIONS):
            in_func = True
        elif stripped.startswith('def ') and in_func:
            in_func = False
        if not in_func:
            continue
        if any(re.search(pat, stripped) for pat in EXEMPT_PATTERNS):
            continue
        # Find hex colors
        for m in re.finditer(r"'(#[0-9A-Fa-f]{6})'|\"(#[0-9A-Fa-f]{6})\"", stripped):
            color = (m.group(1) or m.group(2)).upper()
            if color not in allowed:
                violations.append((i, color, stripped[:80]))

    return violations


def test_no_hardcoded_dasharray():
    """Test: no inline dasharray strings — must come from 2d_drawing_part.dasharray."""
    _, db_dasharrays, _, _ = _load_allowed_from_db()

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        'drawing_writer_dxf.py')
    with open(path) as f:
        lines = f.readlines()

    violations = []
    in_func = False
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if any(stripped.startswith(fn) for fn in SCAN_FUNCTIONS):
            in_func = True
        elif stripped.startswith('def ') and in_func:
            in_func = False
        if not in_func:
            continue
        if any(re.search(pat, stripped) for pat in EXEMPT_PATTERNS):
            continue
        # Find dasharray patterns (e.g. "4,1,1,1" or "6,2")
        for m in re.finditer(r'"(\d+(?:,\d+)+)"', stripped):
            da = m.group(1)
            if da not in db_dasharrays:
                violations.append((i, da, stripped[:80]))

    return violations


def test_no_hardcoded_labels():
    """Test: no inline bilingual label strings — must come from 2D.db."""
    _, _, _, db_strings = _load_allowed_from_db()
    # Known Malay/bilingual patterns that should come from DB
    LABEL_PATTERNS = [
        r"'[A-Z]+\s*/\s*[A-Z]+'",    # "RABUNG / RIDGE" pattern
        r"'CUCURAN",
        r"'RABUNG",
        r"'PELAN",
        r"'LUKISAN",
        r"'JADUAL",
    ]

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        'drawing_writer_dxf.py')
    with open(path) as f:
        lines = f.readlines()

    violations = []
    in_func = False
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if any(stripped.startswith(fn) for fn in SCAN_FUNCTIONS):
            in_func = True
        elif stripped.startswith('def ') and in_func:
            in_func = False
        if not in_func:
            continue
        if any(re.search(pat, stripped) for pat in EXEMPT_PATTERNS):
            continue
        for pat in LABEL_PATTERNS:
            if re.search(pat, stripped):
                violations.append((i, pat, stripped[:80]))

    return violations


def test_no_hardcoded_numbers():
    """Test: no large numeric constants (>=100) that aren't template fallbacks."""
    _, tpl_numbers, _ = _load_allowed_from_template()
    _, _, db_numbers, _ = _load_allowed_from_db()
    ALLOWED = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
               30, 45, 60, 90, 128, 180, 360,  # angles + ISO 128
               100,                        # scale
               256,                        # ACI ByLayer
               1000,                       # mm conversion
               } | {int(n) for n in tpl_numbers if isinstance(n, (int, float))} \
                 | {int(n) for n in db_numbers if isinstance(n, (int, float))}

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        'drawing_writer_dxf.py')
    with open(path) as f:
        lines = f.readlines()

    violations = []
    in_func = False
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if any(stripped.startswith(fn) for fn in SCAN_FUNCTIONS):
            in_func = True
        elif stripped.startswith('def ') and in_func:
            in_func = False
        if not in_func:
            continue
        if any(re.search(pat, stripped) for pat in EXEMPT_PATTERNS):
            continue
        if 'dxfattribs' in stripped:
            continue
        # Skip docstring continuation lines (inside multi-line """)
        if not stripped.startswith(("'", '"', 'def ', 'if ', 'for ', 'return',
                                    'msp.', 'doc.', 'svg_', 'level', 'grid',
                                    'dim', 'tick', 'bubble', 'tag', 'room')):
            if not any(c in stripped for c in ('=', '(', ')', '[')):
                continue  # prose line inside docstring
        nums = re.findall(r'(?<![a-zA-Z_\.\'])(\d{3,})(?![a-zA-Z_])', stripped)
        for n in nums:
            val = int(n)
            if val in ALLOWED:
                continue
            violations.append((i, val, stripped[:80]))

    return violations


def main():
    print("\n=== Library Compliance Test ===")
    print("Rule: every visual value must trace to 2D.db or drawing_template.json\n")

    all_ok = True

    # 1. Colors
    v = test_no_hardcoded_colors()
    if v:
        print(f"  COLORS — {len(v)} violations:")
        for line, val, ctx in v[:10]:
            print(f"    [FAIL] L{line}: {val}  →  {ctx}")
        all_ok = False
    else:
        print("  COLORS — PASS (all from DB/template)")

    # 2. Dasharrays
    v = test_no_hardcoded_dasharray()
    if v:
        print(f"  DASHARRAY — {len(v)} violations:")
        for line, val, ctx in v[:10]:
            print(f"    [FAIL] L{line}: {val}  →  {ctx}")
        all_ok = False
    else:
        print("  DASHARRAY — PASS (all from 2d_drawing_part)")

    # 3. Labels
    v = test_no_hardcoded_labels()
    if v:
        print(f"  LABELS — {len(v)} violations:")
        for line, val, ctx in v[:10]:
            print(f"    [FAIL] L{line}: {val}  →  {ctx}")
        all_ok = False
    else:
        print("  LABELS — PASS (all from 2D.db)")

    # 4. Numbers
    v = test_no_hardcoded_numbers()
    if v:
        print(f"  NUMBERS — {len(v)} violations:")
        for line, val, ctx in v[:10]:
            print(f"    [FAIL] L{line}: {val}  →  {ctx}")
        all_ok = False
    else:
        print("  NUMBERS — PASS (all from DB/template)")

    print()
    if all_ok:
        print("RESULT: ALL PASS — code is library-compliant")
    else:
        print("RESULT: FAIL — fix violations above before proceeding")

    return all_ok


if __name__ == '__main__':
    ok = main()
    sys.exit(0 if ok else 1)
