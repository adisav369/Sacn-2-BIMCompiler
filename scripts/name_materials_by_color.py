# BIM OOTB — Frictionless BIM.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
name_materials_by_color.py — give a SPLIT _meta.db a Material lens when the source
extraction carried NO material names, only colours (material_rgba "r,g,b,a" floats).

The ONLY real material signal in such a DB is the per-element colour. This labels each
distinct colour with its nearest named colour ('≈ Grey', '≈ Tan', …) — DETERMINISTIC and
EXTRACTED from the real rgba, never invented. The '≈' marks it colour-derived (same caveat
as compiled rooms). Only fills rows where material_name is NULL; geometry untouched.

Usage:
  name_materials_by_color.py <_meta.db>            # DRY
  name_materials_by_color.py <_meta.db> --write
"""
import sqlite3, sys

# Nearest-colour palette (name → r,g,b in 0..1). Basic, deterministic buckets.
PALETTE = {
    "White": (0.96, 0.96, 0.94), "Off-White": (0.92, 0.90, 0.85),
    "Light Grey": (0.71, 0.71, 0.71), "Grey": (0.50, 0.50, 0.50),
    "Dark Grey": (0.34, 0.30, 0.30), "Black": (0.05, 0.05, 0.05),
    "Tan": (0.82, 0.62, 0.37), "Beige": (0.85, 0.78, 0.62),
    "Brown": (0.46, 0.28, 0.20), "Red": (0.75, 0.20, 0.20),
    "Orange": (0.90, 0.55, 0.20), "Yellow": (0.90, 0.85, 0.30),
    "Olive": (0.50, 0.50, 0.20), "Green": (0.10, 0.60, 0.30),
    "Teal": (0.30, 0.65, 0.70), "Cyan": (0.46, 0.75, 0.81),
    "Blue": (0.30, 0.45, 0.70), "Navy": (0.15, 0.20, 0.45),
    "Purple": (0.45, 0.30, 0.55), "Pink": (0.80, 0.55, 0.60),
    "Slate": (0.44, 0.50, 0.56),
}


def nearest(r, g, b):
    best, bd = None, 9.0
    for name, (pr, pg, pb) in PALETTE.items():
        d = (r - pr) ** 2 + (g - pg) ** 2 + (b - pb) ** 2
        if d < bd:
            bd, best = d, name
    return best


def main():
    if len(sys.argv) < 2:
        print(__doc__); return
    db = sys.argv[1]; write = "--write" in sys.argv
    con = sqlite3.connect(db); c = con.cursor()
    rgbas = [r[0] for r in c.execute(
        "SELECT DISTINCT material_rgba FROM elements_meta WHERE material_rgba IS NOT NULL").fetchall()]
    mapping = {}
    for rgba in rgbas:
        try:
            parts = [float(x) for x in rgba.split(",")]
            mapping[rgba] = "≈ " + nearest(parts[0], parts[1], parts[2])
        except Exception:
            continue
    names = sorted(set(mapping.values()))
    print(f"distinct rgba: {len(rgbas)} → {len(names)} colour-materials: {', '.join(names)}")
    if not write:
        print("(dry run — pass --write)"); return
    updated = 0
    for rgba, name in mapping.items():
        cur = c.execute(
            "UPDATE elements_meta SET material_name=? WHERE material_rgba=? AND (material_name IS NULL OR material_name='')",
            (name, rgba))
        updated += cur.rowcount
    con.commit()
    distinct = c.execute("SELECT COUNT(DISTINCT material_name) FROM elements_meta WHERE material_name IS NOT NULL").fetchone()[0]
    print(f"WROTE material_name on {updated} rows → {distinct} distinct materials")


if __name__ == "__main__":
    main()
