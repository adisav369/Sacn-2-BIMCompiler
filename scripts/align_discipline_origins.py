#!/usr/bin/env python3
"""
Align Discipline Origins — Post-DB Site Offset Reconciliation
=============================================================

When multiple discipline IFCs are extracted separately into one DB
(via extract_merge_disciplines.py), each discipline may have a different
IfcSite.ObjectPlacement. This causes disciplines to appear at different
XY/Z positions in the viewport.

This script reads the site placement from each source IFC, computes the
offset relative to a reference discipline (default: first ARC file found),
and updates element_transforms + elements_rtree in the extracted DB.

Usage:
    python3 scripts/align_discipline_origins.py \\
        --db DAGCompiler/lib/input/Terminal_extracted.db \\
        --ifc-dir DAGCompiler/lib/input/IFC \\
        --pattern "SJTII-*.ifc"

    # With explicit reference discipline:
    python3 scripts/align_discipline_origins.py \\
        --db DAGCompiler/lib/input/Terminal_extracted.db \\
        --ifc-dir DAGCompiler/lib/input/IFC \\
        --pattern "SJTII-*.ifc" \\
        --ref SJTII-STR-S-TER1-00-R0-Clean

    # Dry run (show offsets without modifying DB):
    python3 scripts/align_discipline_origins.py \\
        --db DAGCompiler/lib/input/Terminal_extracted.db \\
        --ifc-dir DAGCompiler/lib/input/IFC \\
        --pattern "SJTII-*.ifc" \\
        --dry-run

Requires: ifcopenshell (pip install ifcopenshell)
"""

import argparse
import glob
import os
import sqlite3
import sys
from pathlib import Path


def read_site_offset(ifc_path):
    """Read IfcSite.ObjectPlacement XYZ offset from an IFC file.

    Returns (offset_x, offset_y, offset_z) in metres.
    Returns (0, 0, 0) if no site placement found.
    """
    import ifcopenshell
    import ifcopenshell.util.placement as ifcplace
    import ifcopenshell.util.unit as ifcunit

    ifc = ifcopenshell.open(str(ifc_path))
    unit_scale = ifcunit.calculate_unit_scale(ifc, 'LENGTHUNIT')

    sites = ifc.by_type('IfcSite')
    if not sites or not sites[0].ObjectPlacement:
        return (0.0, 0.0, 0.0)

    m = ifcplace.get_local_placement(sites[0].ObjectPlacement)
    ox = float(m[0][3]) * unit_scale
    oy = float(m[1][3]) * unit_scale
    oz = float(m[2][3]) * unit_scale
    return (ox, oy, oz)


def get_discipline_for_ifc(ifc_stem, disc_map=None):
    """Infer discipline from IFC filename stem.

    Uses disc_map overrides if provided, otherwise guesses from filename.
    """
    if disc_map and ifc_stem in disc_map:
        return disc_map[ifc_stem]

    stem_upper = ifc_stem.upper()
    if 'ARC' in stem_upper:
        return 'ARC'
    if 'STR' in stem_upper:
        return 'STR'
    if any(k in stem_upper for k in ['MEP', 'MECH', 'ACMV', 'HVAC', 'PLB', 'ELE', 'FP', 'SP', 'CW', 'LPG', 'FIRE', 'SPR']):
        return 'MEP'
    return 'Unknown'


def main():
    parser = argparse.ArgumentParser(
        description="Align discipline origins in an extracted DB using IFC site placements")
    parser.add_argument("--db", required=True, help="Path to extracted DB")
    parser.add_argument("--ifc-dir", required=True, help="Directory containing discipline IFC files")
    parser.add_argument("--pattern", required=True, help="Glob pattern for discipline IFCs (e.g. 'SJTII-*.ifc')")
    parser.add_argument("--ref", help="Reference discipline IFC stem (default: first ARC file, or first file)")
    parser.add_argument("--dry-run", action="store_true", help="Show offsets without modifying DB")
    parser.add_argument("--disc-map", nargs="*", metavar="STEM=DISC",
                        help="Override discipline by filename stem")
    args = parser.parse_args()

    # Parse disc-map
    disc_map = {}
    if args.disc_map:
        for pair in args.disc_map:
            if '=' in pair:
                k, v = pair.split('=', 1)
                disc_map[k] = v

    # Find IFC files
    ifc_dir = Path(args.ifc_dir)
    ifc_files = sorted(ifc_dir.glob(args.pattern))
    if not ifc_files:
        print(f"§ERROR No IFC files match {args.pattern} in {ifc_dir}")
        sys.exit(1)

    # Read site offsets from each discipline IFC
    print(f"§ALIGN Reading site offsets from {len(ifc_files)} IFC files...")
    offsets = {}  # stem -> (ox, oy, oz)
    for ifc in ifc_files:
        offset = read_site_offset(ifc)
        offsets[ifc.stem] = offset
        disc = get_discipline_for_ifc(ifc.stem, disc_map)
        print(f"  {ifc.name:45s} site=({offset[0]:>10.3f}, {offset[1]:>10.3f}, {offset[2]:>10.3f})  [{disc}]")

    # Choose reference discipline
    ref_stem = args.ref
    if not ref_stem:
        # Default: first ARC file, or first file
        for stem in offsets:
            if 'ARC' in stem.upper():
                ref_stem = stem
                break
        if not ref_stem:
            ref_stem = list(offsets.keys())[0]

    if ref_stem not in offsets:
        print(f"§ERROR Reference '{ref_stem}' not found in IFC files")
        sys.exit(1)

    ref_offset = offsets[ref_stem]
    print(f"\n§ALIGN Reference: {ref_stem}")
    print(f"  ref offset = ({ref_offset[0]:.3f}, {ref_offset[1]:.3f}, {ref_offset[2]:.3f})")

    # Compute deltas: subtract each discipline's own site offset,
    # then add the reference offset. This aligns all to the reference frame.
    # With USE_WORLD_COORDS=False, the iterator bakes the site offset INTO
    # the transformation matrix. So we must UNDO each discipline's offset.
    print(f"\n§ALIGN Corrections needed (subtract own offset, add ref offset):")
    corrections = {}
    for stem, offset in offsets.items():
        # Remove own site offset, apply reference site offset
        dx = ref_offset[0] - offset[0]
        dy = ref_offset[1] - offset[1]
        dz = ref_offset[2] - offset[2]
        disc = get_discipline_for_ifc(stem, disc_map)
        if abs(dx) > 0.01 or abs(dy) > 0.01 or abs(dz) > 0.01:
            corrections[stem] = (dx, dy, dz, disc)
            print(f"  {stem:45s} Δ=({dx:>10.3f}, {dy:>10.3f}, {dz:>10.3f})  [{disc}]")
        else:
            print(f"  {stem:45s} (aligned — no correction)")

    if not corrections:
        print("\n§ALIGN All disciplines already aligned. Nothing to do.")
        return

    if args.dry_run:
        print("\n§ALIGN Dry run — no DB changes made.")
        return

    # Apply corrections to DB
    db_path = args.db
    if not os.path.exists(db_path):
        print(f"§ERROR DB not found: {db_path}")
        sys.exit(1)

    conn = sqlite3.connect(db_path)

    # We need to map IFC stems to the discipline labels used in the DB.
    # The extraction stores discipline in elements_meta.discipline.
    # Multiple IFC stems may map to the same discipline (e.g. ACMV, CW, FP → MEP).
    # We need element-level mapping, not discipline-level.
    #
    # Strategy: for each IFC stem that needs correction, find elements
    # that came from that source. But our DB doesn't store source IFC stem.
    #
    # Fallback: if all IFCs in a discipline share the same offset, apply
    # per-discipline. If offsets differ within a discipline, we can't
    # distinguish — warn the user.

    # Group corrections by discipline
    disc_corrections = {}  # discipline -> list of (stem, dx, dy, dz)
    for stem, (dx, dy, dz, disc) in corrections.items():
        disc_corrections.setdefault(disc, []).append((stem, dx, dy, dz))

    applied = 0
    for disc, corr_list in disc_corrections.items():
        # Check if all stems in this discipline have the same offset
        offsets_in_disc = set((dx, dy, dz) for _, dx, dy, dz in corr_list)
        if len(offsets_in_disc) > 1:
            print(f"\n  §WARN {disc}: multiple different offsets within discipline!")
            for stem, dx, dy, dz in corr_list:
                print(f"    {stem}: Δ=({dx:.3f}, {dy:.3f}, {dz:.3f})")
            print(f"    Using average offset for all {disc} elements")
            dx = sum(d[1] for d in corr_list) / len(corr_list)
            dy = sum(d[2] for d in corr_list) / len(corr_list)
            dz = sum(d[3] for d in corr_list) / len(corr_list)
        else:
            dx, dy, dz = corr_list[0][1], corr_list[0][2], corr_list[0][3]

        # Count affected elements
        count = conn.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE discipline=?", (disc,)
        ).fetchone()[0]

        if count == 0:
            continue

        # Update element_transforms for this discipline
        conn.execute("""
            UPDATE element_transforms SET
                center_x = center_x + ?,
                center_y = center_y + ?,
                center_z = center_z + ?
            WHERE guid IN (SELECT guid FROM elements_meta WHERE discipline=?)
        """, (dx, dy, dz, disc))

        # Update elements_rtree for this discipline
        conn.execute("""
            UPDATE elements_rtree SET
                minX = minX + ?, maxX = maxX + ?,
                minY = minY + ?, maxY = maxY + ?,
                minZ = minZ + ?, maxZ = maxZ + ?
            WHERE id IN (SELECT id FROM elements_meta WHERE discipline=?)
        """, (dx, dx, dy, dy, dz, dz, disc))

        applied += count
        print(f"\n  §ALIGN {disc}: shifted {count} elements by ({dx:.3f}, {dy:.3f}, {dz:.3f})")

    conn.commit()

    # Verify
    row = conn.execute("""
        SELECT discipline, AVG(et.center_x), AVG(et.center_y), AVG(et.center_z)
        FROM element_transforms et JOIN elements_meta em ON et.guid = em.guid
        GROUP BY discipline
    """).fetchall()
    print(f"\n§ALIGN After correction — per-discipline centroids:")
    for r in row:
        print(f"  {r[0]:6s}: ({r[1]:.1f}, {r[2]:.1f}, {r[3]:.1f})")

    conn.close()
    print(f"\n§ALIGN Done. {applied} elements corrected in {db_path}")


if __name__ == "__main__":
    main()
