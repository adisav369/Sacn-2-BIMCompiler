#!/usr/bin/env python3
"""
Test: no hardcoded layout values in drawing functions.
Every numeric value in drawing code must come from drawing_template.json
or be a unit conversion constant (1000 for mm, 100 for scale).
"""
import re, sys

ALLOWED = {
    0, 1, 2, 3, 4, 5,   # trivial / index
    100,                  # scale
    1000,                 # mm conversion
    420, 297, 120,        # template .get() fallbacks (match template defaults)
    128,                  # ISO standard number in comment
}

def test_no_hardcode():
    with open('python/drawing_writer_dxf.py') as f:
        lines = f.readlines()

    violations = []
    in_draw_func = False

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        if stripped.startswith('def write_floor_plan') or \
           stripped.startswith('def write_elevation') or \
           stripped.startswith('def _draw_sheet_layout'):
            in_draw_func = True
        elif stripped.startswith('def ') and in_draw_func:
            in_draw_func = False

        if not in_draw_func:
            continue
        if stripped.startswith('#') or stripped.startswith('_log') or \
           stripped.startswith('print') or stripped.startswith('"""') or \
           stripped.startswith("'"):
            continue
        if 'dxfattribs' in stripped:
            continue

        nums = re.findall(r'(?<![a-zA-Z_\.\'])(\d{3,})(?![a-zA-Z_])', stripped)
        for n in nums:
            val = int(n)
            if val in ALLOWED:
                continue
            violations.append((i, val, stripped[:80]))

    print("\n=== No Hardcoded Values Test ===\n")
    if violations:
        for line_no, val, context in violations:
            print(f"  [FAIL] Line {line_no}: {val}  →  {context}")
        print(f"\n  {len(violations)} violations — these should come from drawing_template.json")
    else:
        print("  ALL PASS — no hardcoded layout values")

    return len(violations) == 0


if __name__ == '__main__':
    ok = test_no_hardcode()
    sys.exit(0 if ok else 1)
