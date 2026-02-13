#!/usr/bin/env python3
"""
Stone-to-Library Extractor — Signature Coverage Matrix tool.

Reads a reference DB, extracts unique element signatures, diffs against
the component library, and either reports coverage (--audit-only) or
generates missing INSERT SQL.

Usage:
  python3 tools/library_gap_filler.py \
    --reference reference/rosetta/SJTII_Terminal_extracted.db \
    --library library/component_library.db \
    [--output missing_entries.sql] \
    [--audit-only] \
    [--profile Malaysian_Institutional] \
    [--discipline ARC]
"""

import argparse
import sqlite3
import sys
from collections import defaultdict
from datetime import date


def extract_slab_signatures(ref_conn, discipline):
    """Extract unique slab thicknesses from reference."""
    where = "AND e.discipline = ?" if discipline else ""
    params = (discipline,) if discipline else ()
    cur = ref_conn.execute(f"""
        SELECT ROUND((r.maxZ - r.minZ) * 1000) as thickness_mm, COUNT(*)
        FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
        WHERE e.ifc_class = 'IfcSlab' {where}
        GROUP BY thickness_mm ORDER BY thickness_mm
    """, params)
    return [(int(row[0]), row[1]) for row in cur.fetchall()]


def extract_wall_signatures(ref_conn, discipline):
    """Extract unique wall thicknesses (thin dimension) from reference."""
    where = "AND e.discipline = ?" if discipline else ""
    params = (discipline,) if discipline else ()
    cur = ref_conn.execute(f"""
        SELECT ROUND(MIN(r.maxX - r.minX, r.maxY - r.minY) * 1000) as thickness_mm, COUNT(*)
        FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
        WHERE (e.ifc_class = 'IfcWall' OR e.ifc_class = 'IfcPlate'
               OR e.ifc_class = 'IfcWallStandardCase') {where}
        GROUP BY thickness_mm ORDER BY thickness_mm
    """, params)
    return [(int(row[0]), row[1]) for row in cur.fetchall()]


def extract_opening_signatures(ref_conn, ifc_class, discipline):
    """Extract unique opening (width, height, depth) from reference."""
    where = "AND e.discipline = ?" if discipline else ""
    params = (ifc_class, discipline) if discipline else (ifc_class,)
    cur = ref_conn.execute(f"""
        SELECT
            ROUND(MAX(r.maxX - r.minX, r.maxY - r.minY) * 1000) as width_mm,
            ROUND((r.maxZ - r.minZ) * 1000) as height_mm,
            ROUND(MIN(r.maxX - r.minX, r.maxY - r.minY) * 1000) as depth_mm,
            COUNT(*)
        FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
        WHERE e.ifc_class = ? {where}
        GROUP BY width_mm, height_mm, depth_mm
        ORDER BY COUNT(*) DESC
    """, params)
    return [(int(row[0]), int(row[1]), int(row[2]), row[3]) for row in cur.fetchall()]


def extract_covering_signatures(ref_conn, discipline):
    """Extract unique covering thicknesses from reference."""
    where = "AND e.discipline = ?" if discipline else ""
    params = (discipline,) if discipline else ()
    cur = ref_conn.execute(f"""
        SELECT ROUND((r.maxZ - r.minZ) * 1000) as thickness_mm, COUNT(*)
        FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
        WHERE e.ifc_class = 'IfcCovering' {where}
        GROUP BY thickness_mm ORDER BY thickness_mm
    """, params)
    return [(int(row[0]), row[1]) for row in cur.fetchall()]


def extract_railing_signatures(ref_conn, discipline):
    """Extract unique railing heights from reference."""
    where = "AND e.discipline = ?" if discipline else ""
    params = (discipline,) if discipline else ()
    cur = ref_conn.execute(f"""
        SELECT ROUND((r.maxZ - r.minZ) * 1000) as height_mm, COUNT(*)
        FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
        WHERE e.ifc_class = 'IfcRailing' {where}
        GROUP BY height_mm ORDER BY height_mm
    """, params)
    return [(int(row[0]), row[1]) for row in cur.fetchall()]


def get_library_slab_thicknesses(lib_conn):
    """Get existing slab thicknesses from ad_floor_type."""
    cur = lib_conn.execute(
        "SELECT ROUND(slab_thickness * 1000) FROM ad_floor_type WHERE is_active = 1")
    return {int(row[0]) for row in cur.fetchall()}


def get_library_wall_thicknesses(lib_conn):
    """Get existing wall thicknesses from ad_wall_type."""
    cur = lib_conn.execute(
        "SELECT total_mm FROM ad_wall_type WHERE is_active = 1")
    return {int(row[0]) for row in cur.fetchall()}


def get_library_opening_families(lib_conn, opening_type):
    """Get existing opening families as (width, height, depth) tuples."""
    cur = lib_conn.execute(
        "SELECT default_width_mm, default_height_mm, depth_mm FROM ad_opening_family "
        "WHERE opening_type = ? AND is_active = 1", (opening_type,))
    return {(int(row[0]), int(row[1]), int(row[2])) for row in cur.fetchall()}


def get_library_covering_thicknesses(lib_conn):
    """Get existing covering thicknesses from ad_covering_type."""
    try:
        cur = lib_conn.execute(
            "SELECT thickness_mm FROM ad_covering_type WHERE is_active = 1")
        return {int(row[0]) for row in cur.fetchall()}
    except sqlite3.OperationalError:
        return set()


def get_library_railing_heights(lib_conn):
    """Get existing railing heights from ad_railing_type."""
    try:
        cur = lib_conn.execute(
            "SELECT height_mm FROM ad_railing_type WHERE is_active = 1")
        return {int(row[0]) for row in cur.fetchall()}
    except sqlite3.OperationalError:
        return set()


def generate_slab_sql(missing, profile, provenance):
    """Generate INSERT SQL for missing slab thicknesses."""
    lines = []
    for thickness_mm, count in missing:
        tid = f"FINISH_{thickness_mm}" if thickness_mm < 100 else f"STRUCTURAL_{thickness_mm}"
        name = f"Finish Floor {thickness_mm}mm" if thickness_mm < 100 else f"Structural Slab {thickness_mm}mm"
        cat = "FINISH" if thickness_mm < 100 else "STRUCTURAL"
        prof = f"'{profile}'" if profile else "NULL"
        lines.append(
            f"INSERT OR IGNORE INTO ad_floor_type "
            f"(floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile) "
            f"VALUES ('{tid}', '{name}', '{cat}', 4.0, {thickness_mm/1000:.3f}, 1, {prof});"
            f"  -- {count} in ref, {provenance}")
    return lines


def generate_wall_sql(missing, profile, provenance):
    """Generate INSERT SQL for missing wall thicknesses."""
    lines = []
    for thickness_mm, count in missing:
        tid = f"WALL_{thickness_mm}"
        name = f"Wall {thickness_mm}mm"
        prof = f"'{profile}'" if profile else "NULL"
        lines.append(
            f"INSERT OR IGNORE INTO ad_wall_type "
            f"(wall_type_id, wall_name, total_mm, is_active) "
            f"VALUES ('{tid}', '{name}', {thickness_mm}, 1);"
            f"  -- {count} in ref, {provenance}")
    return lines


def generate_opening_sql(missing, opening_type, profile, provenance):
    """Generate INSERT SQL for missing opening families."""
    lines = []
    prefix = "MY_" if profile and "Malaysian" in profile else "US_" if profile and "US" in profile else ""
    ifc = "IfcDoor" if opening_type == "DOOR" else "IfcWindow"
    for w, h, d, count in missing:
        tid = f"{prefix}{opening_type}_{w}x{h}"
        name = f"{prefix}{opening_type.title()} {w}x{h}mm"
        lines.append(
            f"INSERT OR IGNORE INTO ad_opening_family "
            f"(family_id, family_name, opening_type, ifc_class, "
            f"default_width_mm, default_height_mm, depth_mm, is_active) "
            f"VALUES ('{tid}', '{name}', '{opening_type}', '{ifc}', {w}, {h}, {d}, 1);"
            f"  -- {count} in ref, {provenance}")
    return lines


def opening_match(ref_sig, lib_set, tolerance_mm=15):
    """Check if a reference opening signature has a near-match in library."""
    w, h, d = ref_sig[0], ref_sig[1], ref_sig[2]
    for lw, lh, ld in lib_set:
        if (abs(w - lw) <= tolerance_mm and
            abs(h - lh) <= tolerance_mm and
            abs(d - ld) <= tolerance_mm):
            return True
    return False


def main():
    parser = argparse.ArgumentParser(description="Stone-to-Library gap filler")
    parser.add_argument("--reference", required=True, help="Reference extracted DB")
    parser.add_argument("--library", required=True, help="Component library DB")
    parser.add_argument("--output", help="Output SQL file (omit for stdout)")
    parser.add_argument("--audit-only", action="store_true", help="Report coverage only")
    parser.add_argument("--profile", help="Profile for generated entries")
    parser.add_argument("--discipline", help="Filter reference by discipline (e.g. ARC)")
    args = parser.parse_args()

    ref_conn = sqlite3.connect(args.reference)
    lib_conn = sqlite3.connect(args.library)
    provenance = f"[EXTRACTED: {args.reference.split('/')[-1]}]"
    disc = args.discipline

    # --- SLABS ---
    ref_slabs = extract_slab_signatures(ref_conn, disc)
    lib_slabs = get_library_slab_thicknesses(lib_conn)
    missing_slabs = [(t, c) for t, c in ref_slabs if t not in lib_slabs and t > 5]

    # --- WALLS ---
    ref_walls = extract_wall_signatures(ref_conn, disc)
    lib_walls = get_library_wall_thicknesses(lib_conn)
    missing_walls = [(t, c) for t, c in ref_walls if t not in lib_walls and 30 < t < 1000]

    # --- DOORS ---
    ref_doors = extract_opening_signatures(ref_conn, "IfcDoor", disc)
    lib_doors = get_library_opening_families(lib_conn, "DOOR")
    missing_doors = [(w, h, d, c) for w, h, d, c in ref_doors
                     if not opening_match((w, h, d), lib_doors)]

    # --- WINDOWS ---
    ref_windows = extract_opening_signatures(ref_conn, "IfcWindow", disc)
    lib_windows = get_library_opening_families(lib_conn, "WINDOW")
    missing_windows = [(w, h, d, c) for w, h, d, c in ref_windows
                       if not opening_match((w, h, d), lib_windows)]

    # --- COVERINGS ---
    ref_coverings = extract_covering_signatures(ref_conn, disc)
    lib_coverings = get_library_covering_thicknesses(lib_conn)
    missing_coverings = [(t, c) for t, c in ref_coverings if t not in lib_coverings]

    # --- RAILINGS ---
    ref_railings = extract_railing_signatures(ref_conn, disc)
    lib_railings = get_library_railing_heights(lib_conn)
    missing_railings = [(h, c) for h, c in ref_railings if h not in lib_railings]

    # Coverage report
    total_ref = (len(ref_slabs) + len(ref_walls) + len(ref_doors) +
                 len(ref_windows) + len(ref_coverings) + len(ref_railings))
    total_missing = (len(missing_slabs) + len(missing_walls) + len(missing_doors) +
                     len(missing_windows) + len(missing_coverings) + len(missing_railings))
    total_covered = total_ref - total_missing

    print(f"\n{'='*60}")
    print(f"  LIBRARY COVERAGE: {args.reference.split('/')[-1]}")
    if disc:
        print(f"  Discipline filter: {disc}")
    print(f"{'='*60}\n")

    def pct(covered, total):
        return f"{covered}/{total} ({100*covered//total if total else 0}%)"

    cats = [
        ("SLAB", len(ref_slabs), len(missing_slabs)),
        ("WALL", len(ref_walls), len(missing_walls)),
        ("DOOR", len(ref_doors), len(missing_doors)),
        ("WINDOW", len(ref_windows), len(missing_windows)),
        ("COVERING", len(ref_coverings), len(missing_coverings)),
        ("RAILING", len(ref_railings), len(missing_railings)),
    ]

    for name, ref_n, miss_n in cats:
        covered = ref_n - miss_n
        status = "OK" if miss_n == 0 else f"MISSING {miss_n}"
        print(f"  {name:12s}  {pct(covered, ref_n):>16s}  {status}")

    print(f"\n  {'OVERALL':12s}  {pct(total_covered, total_ref):>16s}")
    if total_missing > 0:
        print(f"\n  {total_missing} missing signatures.")
        if args.audit_only:
            print(f"  Run without --audit-only to generate SQL.\n")

    # Missing detail
    if missing_slabs:
        print(f"\n  Missing slab thicknesses: {[t for t,c in missing_slabs]}")
    if missing_walls:
        print(f"  Missing wall thicknesses: {[t for t,c in missing_walls]}")
    if missing_doors:
        print(f"  Missing door families:    {[(w,h,d) for w,h,d,c in missing_doors[:8]]}")
    if missing_windows:
        print(f"  Missing window families:  {[(w,h,d) for w,h,d,c in missing_windows[:8]]}")

    print()

    if args.audit_only:
        ref_conn.close()
        lib_conn.close()
        return

    # Generate SQL
    sql_lines = [
        f"-- Generated by library_gap_filler.py",
        f"-- Reference: {args.reference}",
        f"-- Date: {date.today()}",
        f"-- Profile: {args.profile or 'generic'}",
        f"-- Discipline: {disc or 'ALL'}",
        f""
    ]

    if missing_slabs:
        sql_lines.append(f"-- SLABS: {len(missing_slabs)} missing thicknesses")
        sql_lines.extend(generate_slab_sql(missing_slabs, args.profile, provenance))
        sql_lines.append("")

    if missing_walls:
        sql_lines.append(f"-- WALLS: {len(missing_walls)} missing thicknesses")
        sql_lines.extend(generate_wall_sql(missing_walls, args.profile, provenance))
        sql_lines.append("")

    if missing_doors:
        sql_lines.append(f"-- DOORS: {len(missing_doors)} missing families")
        sql_lines.extend(generate_opening_sql(missing_doors, "DOOR", args.profile, provenance))
        sql_lines.append("")

    if missing_windows:
        sql_lines.append(f"-- WINDOWS: {len(missing_windows)} missing families")
        sql_lines.extend(generate_opening_sql(missing_windows, "WINDOW", args.profile, provenance))
        sql_lines.append("")

    if missing_coverings:
        sql_lines.append(f"-- COVERINGS: {len(missing_coverings)} missing thicknesses")
        sql_lines.append("")

    if missing_railings:
        sql_lines.append(f"-- RAILINGS: {len(missing_railings)} missing heights")
        sql_lines.append("")

    sql_text = "\n".join(sql_lines)

    if args.output:
        with open(args.output, "w") as f:
            f.write(sql_text)
        print(f"  SQL written to {args.output}")
    else:
        print(sql_text)

    ref_conn.close()
    lib_conn.close()


if __name__ == "__main__":
    main()
