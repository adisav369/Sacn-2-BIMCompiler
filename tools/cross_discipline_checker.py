#!/usr/bin/env python3
"""
Cross-Discipline Overlap Checker — extracts grammar from bbox overlaps.

Given a multi-discipline reference DB (extracted from IFC), analyses spatial
overlaps between discipline pairs to derive cross-discipline grammar rules:
  - PIPE-IN-WALL: pipe.diameter < wall.thickness
  - DUCT-SLAB: ceiling void depth
  - COLUMN-WALL: structural-envelope integration
  - BEAM-SLAB: drop beam vs flush pattern
  - SPRINKLER-CEILING: standard drop distance
  - STOREY MAPPING: Z-overlap to map dual naming conventions

Usage:
    python3 tools/cross_discipline_checker.py [db_path]
    python3 tools/cross_discipline_checker.py [db_path] --storey "Aras 01"

Output: structured analysis report for cross-discipline grammar extraction.
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

import sqlite3
import argparse
from collections import defaultdict


def mm(val):
    """Convert metres to mm, rounded to integer."""
    return round(val * 1000)


def connect(db_path):
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn


# =========================================================================
# 1. PIPE-IN-WALL — pipe segments overlapping wall bboxes
# =========================================================================

def analyse_pipe_in_wall(conn, storey_filter=None):
    """Find pipes running through walls. Grammar: pipe.diameter < wall.thickness."""
    where = "AND a.storey = ?" if storey_filter else ""
    params = [storey_filter] if storey_filter else []

    rows = conn.execute(f"""
        SELECT
            a.element_type as wall_type,
            b.discipline as pipe_disc,
            ROUND(MIN(ra.maxX-ra.minX, ra.maxY-ra.minY)*1000) as wall_thin_mm,
            ROUND(MIN(rb.maxX-rb.minX, rb.maxY-rb.minY, rb.maxZ-rb.minZ)*1000) as pipe_thin_mm,
            COUNT(*) as cnt
        FROM elements_meta a JOIN elements_rtree ra ON a.id=ra.id
        JOIN elements_meta b ON b.id!=a.id JOIN elements_rtree rb ON b.id=rb.id
        WHERE a.discipline='ARC' AND a.ifc_class IN ('IfcWall','IfcWallStandardCase')
          AND b.ifc_class='IfcPipeSegment'
          AND ra.minX<rb.maxX AND ra.maxX>rb.minX
          AND ra.minY<rb.maxY AND ra.maxY>rb.minY
          AND ra.minZ<rb.maxZ AND ra.maxZ>rb.minZ
          {where}
        GROUP BY wall_type, pipe_disc, wall_thin_mm, pipe_thin_mm
        ORDER BY cnt DESC
    """, params).fetchall()

    return rows


# =========================================================================
# 2. DUCT-SLAB — ACMV ducts relative to slab soffit
# =========================================================================

def analyse_duct_slab(conn):
    """Find ACMV ducts overlapping or near slabs. Derives ceiling void depth."""
    rows = conn.execute("""
        SELECT
            a.storey,
            a.element_type as slab_type,
            ROUND(MIN(ra.minZ)*1000) as slab_bottom_mm,
            ROUND(MAX(ra.maxZ)*1000) as slab_top_mm,
            b.ifc_class as duct_class,
            ROUND(AVG(rb.minZ)*1000) as avg_duct_bottom_mm,
            ROUND(AVG(rb.maxZ)*1000) as avg_duct_top_mm,
            ROUND(AVG(rb.maxZ-rb.minZ)*1000) as avg_duct_height_mm,
            COUNT(DISTINCT b.id) as duct_count
        FROM elements_meta a JOIN elements_rtree ra ON a.id=ra.id
        JOIN elements_meta b ON b.id!=a.id JOIN elements_rtree rb ON b.id=rb.id
        WHERE a.discipline='ARC' AND a.ifc_class='IfcSlab'
          AND b.discipline='ACMV'
          AND ra.minX<rb.maxX AND ra.maxX>rb.minX
          AND ra.minY<rb.maxY AND ra.maxY>rb.minY
          AND ABS(ra.minZ - rb.maxZ) < 2.0
        GROUP BY a.storey, a.element_type, b.ifc_class
        ORDER BY a.storey
    """).fetchall()

    return rows


# =========================================================================
# 3. COLUMN-WALL — STR columns at ARC wall junctions
# =========================================================================

def analyse_column_wall(conn, storey_filter=None):
    """Find structural columns overlapping walls. Grammar: columns at wall intersections."""
    where = "AND a.storey = ?" if storey_filter else ""
    params = [storey_filter] if storey_filter else []

    rows = conn.execute(f"""
        SELECT
            ROUND((ra.minX+ra.maxX)/2*1000) as col_x_mm,
            ROUND((ra.minY+ra.maxY)/2*1000) as col_y_mm,
            ROUND((ra.maxX-ra.minX)*1000) as col_w_mm,
            ROUND((ra.maxY-ra.minY)*1000) as col_d_mm,
            COUNT(DISTINCT b.id) as wall_count
        FROM elements_meta a JOIN elements_rtree ra ON a.id=ra.id
        JOIN elements_meta b ON b.id!=a.id JOIN elements_rtree rb ON b.id=rb.id
        WHERE a.discipline='STR' AND a.ifc_class='IfcColumn'
          AND b.discipline='ARC' AND b.ifc_class IN ('IfcWall','IfcWallStandardCase')
          AND ra.minX<rb.maxX AND ra.maxX>rb.minX
          AND ra.minY<rb.maxY AND ra.maxY>rb.minY
          AND ra.minZ<rb.maxZ AND ra.maxZ>rb.minZ
          {where}
        GROUP BY col_x_mm, col_y_mm, col_w_mm, col_d_mm
        ORDER BY wall_count DESC, col_x_mm
    """, params).fetchall()

    return rows


# =========================================================================
# 4. BEAM-SLAB — STR beams relative to slabs
# =========================================================================

def analyse_beam_slab(conn):
    """Find beams overlapping slabs. Grammar: beam.depth ∝ span."""
    rows = conn.execute("""
        SELECT
            b.element_type as beam_type,
            ROUND(MAX(rb.maxX-rb.minX, rb.maxY-rb.minY)*1000) as span_mm,
            ROUND(MIN(rb.maxX-rb.minX, rb.maxY-rb.minY)*1000) as width_mm,
            ROUND((rb.maxZ-rb.minZ)*1000) as depth_mm,
            COUNT(*) as cnt
        FROM elements_meta b JOIN elements_rtree rb ON b.id=rb.id
        WHERE b.discipline='STR' AND b.ifc_class='IfcBeam'
        GROUP BY b.element_type, span_mm, width_mm, depth_mm
        ORDER BY cnt DESC
        LIMIT 30
    """).fetchall()

    return rows


# =========================================================================
# 5. SPRINKLER-CEILING — FP terminals relative to slab soffit
# =========================================================================

def analyse_sprinkler_ceiling(conn):
    """Find FP terminals and their Z offset from nearest slab. Grammar: standard drop distance."""
    rows = conn.execute("""
        SELECT
            b.storey,
            b.ifc_class,
            ROUND(AVG(rb.minZ)*1000) as avg_bottom_mm,
            ROUND(AVG(rb.maxZ)*1000) as avg_top_mm,
            ROUND(AVG(rb.maxZ-rb.minZ)*1000) as avg_height_mm,
            COUNT(*) as cnt
        FROM elements_meta b JOIN elements_rtree rb ON b.id=rb.id
        WHERE b.discipline='FP'
          AND b.ifc_class IN ('IfcFireSuppressionTerminal','IfcAlarm')
        GROUP BY b.storey, b.ifc_class
        ORDER BY b.storey, cnt DESC
    """).fetchall()

    return rows


# =========================================================================
# 6. STOREY MAPPING — Z-overlap for dual naming conventions
# =========================================================================

def analyse_storey_mapping(conn):
    """Map storeys by Z-overlap to identify dual naming (Malay ↔ English)."""
    rows = conn.execute("""
        SELECT storey,
            ROUND(MIN(r.minZ)*1000) as z_min_mm,
            ROUND(MAX(r.maxZ)*1000) as z_max_mm,
            COUNT(*) as elem_count
        FROM elements_meta e JOIN elements_rtree r ON e.id=r.id
        WHERE storey IS NOT NULL AND storey != ''
        GROUP BY storey ORDER BY z_min_mm
    """).fetchall()

    return rows


# =========================================================================
# 7. WALL FINISH → WET ROOM indicator
# =========================================================================

def analyse_wall_finish(conn):
    """Analyse wall finish types. Grammar: Ceramic finish → wet room."""
    rows = conn.execute("""
        SELECT element_type,
            ROUND(MIN(ra.maxX-ra.minX, ra.maxY-ra.minY)*1000) as thick_mm,
            COUNT(*) as cnt
        FROM elements_meta a JOIN elements_rtree ra ON a.id=ra.id
        WHERE a.discipline='ARC' AND a.ifc_class='IfcWall'
        GROUP BY element_type, thick_mm ORDER BY cnt DESC
    """).fetchall()

    return rows


# =========================================================================
# 8. COLUMN GRID REGULARITY
# =========================================================================

def analyse_column_grid(conn):
    """Extract column grid positions and compute spacing regularity."""
    rows = conn.execute("""
        SELECT
            ROUND((ra.minX+ra.maxX)/2, 1) as cx,
            ROUND((ra.minY+ra.maxY)/2, 1) as cy,
            ROUND((ra.maxX-ra.minX)*1000) as w_mm,
            ROUND((ra.maxY-ra.minY)*1000) as d_mm
        FROM elements_meta a JOIN elements_rtree ra ON a.id=ra.id
        WHERE a.discipline='STR' AND a.ifc_class='IfcColumn'
        GROUP BY cx, cy
        ORDER BY cx, cy
    """).fetchall()

    return rows


# =========================================================================
# DISCIPLINE OVERLAP MATRIX
# =========================================================================

def analyse_overlap_matrix(conn):
    """Count overlapping element pairs between all discipline pairs."""
    rows = conn.execute("""
        SELECT a.discipline as disc_a, b.discipline as disc_b, COUNT(*) as overlap_pairs
        FROM elements_meta a JOIN elements_rtree ra ON a.id=ra.id
        JOIN elements_meta b ON b.id>a.id JOIN elements_rtree rb ON b.id=rb.id
        WHERE ra.minX<rb.maxX AND ra.maxX>rb.minX
          AND ra.minY<rb.maxY AND ra.maxY>rb.minY
          AND ra.minZ<rb.maxZ AND ra.maxZ>rb.minZ
        GROUP BY disc_a, disc_b
        ORDER BY overlap_pairs DESC
    """).fetchall()

    return rows


# =========================================================================
# MAIN REPORT
# =========================================================================

def generate_report(db_path, storey_filter=None):
    conn = connect(db_path)
    db_name = db_path.split('/')[-1].replace('_extracted.db', '').replace('.db', '')

    storey_label = f" [storey: {storey_filter}]" if storey_filter else ""
    print(f"{'='*80}")
    print(f"  CROSS-DISCIPLINE OVERLAP ANALYSIS: {db_name}{storey_label}")
    print(f"  Source: {db_path}")
    print(f"{'='*80}")

    # --- Discipline census ---
    disc_counts = conn.execute(
        "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC"
    ).fetchall()
    print(f"\n  Discipline census:")
    for r in disc_counts:
        print(f"    {r[0]:<6s} {r[1]:>6d} elements")

    # === 1. PIPE-IN-WALL ===
    print(f"\n{'='*80}")
    print(f"  1. PIPE-IN-WALL — pipe.diameter vs wall.thickness")
    print(f"{'='*80}")

    piw = analyse_pipe_in_wall(conn, storey_filter)
    if piw:
        total_overlaps = sum(r['cnt'] for r in piw)
        violations = sum(r['cnt'] for r in piw if r['pipe_thin_mm'] > r['wall_thin_mm'])
        print(f"\n  Total pipe-wall overlaps: {total_overlaps}")
        print(f"  Violations (pipe > wall): {violations}")
        print(f"\n  {'Wall Thick':>10s} {'Pipe Dia':>8s} {'Disc':>5s} {'Count':>6s} {'Fits?':>6s}")
        print(f"  {'-'*10} {'-'*8} {'-'*5} {'-'*6} {'-'*6}")
        for r in piw[:25]:
            fits = "YES" if r['pipe_thin_mm'] <= r['wall_thin_mm'] else "NO"
            print(f"  {r['wall_thin_mm']:>9.0f}mm {r['pipe_thin_mm']:>7.0f}mm {r['pipe_disc']:>5s} {r['cnt']:>6d} {fits:>6s}")
    else:
        print("\n  No pipe-wall overlaps found.")

    # === 2. DUCT-SLAB ===
    print(f"\n{'='*80}")
    print(f"  2. DUCT-SLAB — ceiling void depth (slab soffit to duct top)")
    print(f"{'='*80}")

    ds = analyse_duct_slab(conn)
    if ds:
        print(f"\n  {'Storey':<25s} {'Slab Bot':>9s} {'Duct Top':>9s} {'Void':>6s} {'Ducts':>5s}")
        print(f"  {'-'*24} {'-'*9} {'-'*9} {'-'*6} {'-'*5}")
        for r in ds:
            void = r['slab_bottom_mm'] - r['avg_duct_top_mm']
            storey_name = r['storey'] or '(no storey)'
            print(f"  {storey_name:<25s} {r['slab_bottom_mm']:>8.0f}mm {r['avg_duct_top_mm']:>8.0f}mm {void:>5.0f}mm {r['duct_count']:>5d}")
    else:
        print("\n  No duct-slab overlaps found (ducts may not be near slabs).")

    # === 3. COLUMN-WALL ===
    print(f"\n{'='*80}")
    print(f"  3. COLUMN-WALL — columns at wall junctions")
    print(f"{'='*80}")

    cw = analyse_column_wall(conn, storey_filter)
    if cw:
        at_junction = sum(1 for r in cw if r['wall_count'] >= 2)
        at_single = sum(1 for r in cw if r['wall_count'] == 1)
        no_wall = sum(1 for r in cw if r['wall_count'] == 0)
        print(f"\n  Columns at wall junction (≥2 walls): {at_junction}")
        print(f"  Columns at single wall: {at_single}")
        print(f"  Total column positions: {len(cw)}")
        print(f"\n  {'Col X':>10s} {'Col Y':>10s} {'Size':>10s} {'Walls':>5s}")
        print(f"  {'-'*10} {'-'*10} {'-'*10} {'-'*5}")
        for r in cw[:20]:
            print(f"  {r['col_x_mm']:>9.0f}mm {r['col_y_mm']:>9.0f}mm"
                  f" {r['col_w_mm']:>4.0f}x{r['col_d_mm']:>4.0f}mm {r['wall_count']:>5d}")
    else:
        print("\n  No column-wall overlaps found.")

    # === 4. BEAM-SLAB ===
    print(f"\n{'='*80}")
    print(f"  4. BEAM INVENTORY — span vs depth (Grammar: depth ∝ span)")
    print(f"{'='*80}")

    bs = analyse_beam_slab(conn)
    if bs:
        print(f"\n  {'Beam Type':<55s} {'Span':>7s} {'Width':>6s} {'Depth':>6s} {'Cnt':>4s} {'D/S':>6s}")
        print(f"  {'-'*54} {'-'*7} {'-'*6} {'-'*6} {'-'*4} {'-'*6}")
        for r in bs:
            ratio = r['depth_mm'] / r['span_mm'] if r['span_mm'] > 0 else 0
            name = (r['beam_type'] or '')[:54]
            print(f"  {name:<55s} {r['span_mm']:>6.0f}mm {r['width_mm']:>5.0f}mm {r['depth_mm']:>5.0f}mm {r['cnt']:>4d} {ratio:>5.2f}")
    else:
        print("\n  No beams found.")

    # === 5. SPRINKLER-CEILING ===
    print(f"\n{'='*80}")
    print(f"  5. SPRINKLER-CEILING — FP terminal Z positions per storey")
    print(f"{'='*80}")

    sc = analyse_sprinkler_ceiling(conn)
    if sc:
        print(f"\n  {'Storey':<25s} {'Class':<30s} {'Bottom':>8s} {'Top':>8s} {'Height':>7s} {'Count':>5s}")
        print(f"  {'-'*24} {'-'*29} {'-'*8} {'-'*8} {'-'*7} {'-'*5}")
        for r in sc:
            print(f"  {r['storey']:<25s} {r['ifc_class']:<30s} {r['avg_bottom_mm']:>7.0f}mm {r['avg_top_mm']:>7.0f}mm"
                  f" {r['avg_height_mm']:>6.0f}mm {r['cnt']:>5d}")
    else:
        print("\n  No FP terminals found.")

    # === 6. STOREY MAPPING ===
    print(f"\n{'='*80}")
    print(f"  6. STOREY MAPPING — Z-overlap for dual naming (Malay ↔ English)")
    print(f"{'='*80}")

    sm = analyse_storey_mapping(conn)
    if sm:
        print(f"\n  {'Storey':<45s} {'Z min':>9s} {'Z max':>9s} {'Elements':>8s}")
        print(f"  {'-'*44} {'-'*9} {'-'*9} {'-'*8}")
        for r in sm:
            storey_name = r['storey'] or '(no storey)'
            print(f"  {storey_name:<45s} {r['z_min_mm']:>8.0f}mm {r['z_max_mm']:>8.0f}mm {r['elem_count']:>8d}")

        # Derive overlaps
        print(f"\n  Z-overlap mapping (storeys sharing Z range):")
        storey_list = [(r['storey'], r['z_min_mm'], r['z_max_mm'], r['elem_count']) for r in sm]
        for i, (name_a, z_min_a, z_max_a, cnt_a) in enumerate(storey_list):
            for name_b, z_min_b, z_max_b, cnt_b in storey_list[i+1:]:
                overlap = min(z_max_a, z_max_b) - max(z_min_a, z_min_b)
                if overlap > 1000:  # >1m overlap
                    print(f"    {name_a:<35s} ↔ {name_b:<35s} overlap: {overlap:.0f}mm")

    # === 7. WALL FINISH ===
    print(f"\n{'='*80}")
    print(f"  7. WALL FINISH TYPES — Ceramic = wet room indicator")
    print(f"{'='*80}")

    wf = analyse_wall_finish(conn)
    if wf:
        ceramic_count = sum(r['cnt'] for r in wf if 'Ceramic' in (r['element_type'] or ''))
        paint_count = sum(r['cnt'] for r in wf if 'Paint' in (r['element_type'] or '') and 'Ceramic' not in (r['element_type'] or ''))
        plaster_count = sum(r['cnt'] for r in wf if 'Plaster' in (r['element_type'] or '') and 'Ceramic' not in (r['element_type'] or ''))
        total = sum(r['cnt'] for r in wf)
        print(f"\n  Total walls: {total}")
        print(f"  Ceramic finish: {ceramic_count} ({100*ceramic_count//max(total,1)}%)")
        print(f"  Paint finish: {paint_count}")
        print(f"  Plaster finish: {plaster_count}")
        print(f"\n  {'Wall Type':<65s} {'Thick':>6s} {'Count':>5s}")
        print(f"  {'-'*64} {'-'*6} {'-'*5}")
        for r in wf:
            name = (r['element_type'] or '')[:64]
            print(f"  {name:<65s} {r['thick_mm']:>5.0f}mm {r['cnt']:>5d}")

    # === 8. COLUMN GRID ===
    print(f"\n{'='*80}")
    print(f"  8. COLUMN GRID — spacing regularity")
    print(f"{'='*80}")

    cg = analyse_column_grid(conn)
    if cg:
        # Extract unique X and Y positions
        x_positions = sorted(set(r['cx'] for r in cg))
        y_positions = sorted(set(r['cy'] for r in cg))

        x_spacings = [mm(x_positions[i+1] - x_positions[i]) for i in range(len(x_positions)-1)]
        y_spacings = [mm(y_positions[i+1] - y_positions[i]) for i in range(len(y_positions)-1)]

        print(f"\n  Unique X positions: {len(x_positions)}")
        print(f"  Unique Y positions: {len(y_positions)}")
        print(f"  Column size: {cg[0]['w_mm']:.0f}x{cg[0]['d_mm']:.0f}mm")

        if x_spacings:
            print(f"\n  X-axis spacings (mm): {x_spacings[:15]}")
            # Find most common spacing
            sp_counts = defaultdict(int)
            for s in x_spacings:
                sp_counts[s] += 1
            top_x = sorted(sp_counts.items(), key=lambda x: -x[1])[:3]
            print(f"  Most common X spacings: {[(s, c) for s, c in top_x]}")

        if y_spacings:
            print(f"\n  Y-axis spacings (mm): {y_spacings[:15]}")
            sp_counts = defaultdict(int)
            for s in y_spacings:
                sp_counts[s] += 1
            top_y = sorted(sp_counts.items(), key=lambda x: -x[1])[:3]
            print(f"  Most common Y spacings: {[(s, c) for s, c in top_y]}")

    # === OVERLAP MATRIX ===
    print(f"\n{'='*80}")
    print(f"  9. DISCIPLINE OVERLAP MATRIX")
    print(f"{'='*80}")

    om = analyse_overlap_matrix(conn)
    if om:
        print(f"\n  {'Disc A':<8s} {'Disc B':<8s} {'Overlapping Pairs':>18s}")
        print(f"  {'-'*7} {'-'*7} {'-'*18}")
        for r in om:
            print(f"  {r['disc_a']:<8s} {r['disc_b']:<8s} {r['overlap_pairs']:>18d}")

    conn.close()
    print(f"\n{'='*80}")
    print(f"  END OF CROSS-DISCIPLINE ANALYSIS: {db_name}")
    print(f"{'='*80}")


# =========================================================================
# ENTRY POINT
# =========================================================================

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Cross-Discipline Overlap Checker — bbox overlap analysis')
    parser.add_argument('db_path', help='Path to multi-discipline extracted reference DB')
    parser.add_argument('--storey', type=str, default=None,
                        help='Filter pipe-in-wall and column-wall analyses to a specific storey')
    args = parser.parse_args()

    generate_report(args.db_path, args.storey)
