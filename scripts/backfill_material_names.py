# BIM OOTB — Frictionless BIM.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
backfill_material_names.py — populate elements_meta.material_name in a SPLIT _meta.db
that has material_rgba but NULL material_name, by bridging to a source _extracted.db
on the shared material_rgba value.

WHY: a building served as split (_meta.db + _geo.db) loses the Material lens when the
_meta.db carries colours (material_rgba) but no names. The matching _extracted.db (a
different extraction — different guids AND different geometry_hash, so neither bridges)
DOES carry material_name. The ONE key common to both is material_rgba.

DETERMINISTIC / EXTRACT-ONLY: name is taken from the source DB, never invented. The
rgba→name map uses the DOMINANT name per colour (highest element count; ties broken
alphabetically) because a colour can map to >1 named material across extractions.
This is APPROXIMATE at the colour granularity (materials sharing one RGBA merge) —
the same '≈' caveat as compiled rooms. Geometry/instances are never touched.

Usage:
  backfill_material_names.py --meta <_meta.db> --source <_extracted.db>          # DRY
  backfill_material_names.py --meta <_meta.db> --source <_extracted.db> --write
"""
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
import argparse, sqlite3, sys


def build_rgba_map(src):
    # dominant material_name per material_rgba (by count, tie-break name ASC)
    rows = src.execute(
        "SELECT material_rgba, material_name, COUNT(*) n FROM elements_meta "
        "WHERE material_name IS NOT NULL AND material_name!='' AND material_rgba IS NOT NULL "
        "GROUP BY material_rgba, material_name").fetchall()
    best = {}  # rgba -> (count, name)
    for rgba, name, n in rows:
        cur = best.get(rgba)
        if cur is None or n > cur[0] or (n == cur[0] and name < cur[1]):
            best[rgba] = (n, name)
    return {rgba: nm for rgba, (cnt, nm) in best.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--meta", required=True)
    ap.add_argument("--source", required=True)
    ap.add_argument("--write", action="store_true")
    a = ap.parse_args()

    meta = sqlite3.connect(a.meta)
    src = sqlite3.connect(a.source)
    rgba_map = build_rgba_map(src)
    print(f"source rgba→name entries: {len(rgba_map)} (distinct names: {len(set(rgba_map.values()))})")

    # which of the meta's colours can we name?
    meta_rgbas = [r[0] for r in meta.execute(
        "SELECT DISTINCT material_rgba FROM elements_meta WHERE material_rgba IS NOT NULL").fetchall()]
    namable = [r for r in meta_rgbas if r in rgba_map]
    print(f"meta distinct rgba: {len(meta_rgbas)}  namable from source: {len(namable)}")

    # how many element rows would get a name
    rows_total = meta.execute(
        "SELECT COUNT(*) FROM elements_meta WHERE material_rgba IS NOT NULL").fetchone()[0]
    print(f"meta rows with rgba: {rows_total}  → will name: {len([1 for (rb,) in meta.execute('SELECT material_rgba FROM elements_meta WHERE material_rgba IS NOT NULL') if rb in rgba_map])}")

    if not a.write:
        print("(dry run — pass --write to populate material_name)")
        return

    updated = 0
    for rgba, name in rgba_map.items():
        cur = meta.execute(
            "UPDATE elements_meta SET material_name=? WHERE material_rgba=? AND (material_name IS NULL OR material_name='')",
            (name, rgba))
        updated += cur.rowcount
    meta.commit()
    distinct = meta.execute(
        "SELECT COUNT(DISTINCT material_name) FROM elements_meta WHERE material_name IS NOT NULL").fetchone()[0]
    print(f"WROTE material_name on {updated} rows  → {distinct} distinct materials now in _meta")


if __name__ == "__main__":
    main()
