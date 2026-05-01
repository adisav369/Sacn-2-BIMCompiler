# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Temporal fix: correct discipline tags for IfcBuildingElementProxy elements
that defaulted to ARC because discipline was inferred from IFC class alone.

Reads source IFC files to identify which GUIDs belong to which discipline,
then UPDATEs elements_meta in the extracted DB.

Usage:
    python3 scripts/fix_proxy_discipline.py \
        --db DAGCompiler/lib/input/Hospital_PerDisc_extracted.db \
        --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
        --mapping "Hospital_IFC4_ELE.ifc=ELEC" \
                  "Hospital_IFC4_MECH.ifc=MECH" \
                  "Hospital_IFC4_PLB.ifc=MEP" \
                  "Hospital_IFC4_SPR.ifc=FP" \
                  "Hospital_IFC4_FIRE.ifc=FP" \
                  "Hospital_IFC4_STR.ifc=STR"
"""

import argparse
import sqlite3
import ifcopenshell
from pathlib import Path


def fix_disciplines(db_path, ifc_dir, mappings, dry_run=False):
    conn = sqlite3.connect(db_path)

    total_fixed = 0

    for mapping in mappings:
        filename, discipline = mapping.split("=", 1)
        ifc_path = Path(ifc_dir) / filename

        if not ifc_path.exists():
            print(f"  SKIP {filename} — file not found")
            continue

        print(f"  Reading {filename} → discipline={discipline} ...")
        ifc_file = ifcopenshell.open(str(ifc_path))

        # Collect ALL GUIDs from this file (not just proxies)
        # Any element whose discipline was mis-inferred will be corrected
        guids = [e.GlobalId for e in ifc_file.by_type("IfcProduct")
                 if e.GlobalId and e.Representation is not None]

        if not guids:
            print(f"    No elements found.")
            continue

        # Only fix elements currently tagged ARC (wrongly defaulted)
        # Don't overwrite correctly-inferred disciplines (MEP pipes etc.)
        placeholders = ",".join("?" * len(guids))
        rows = conn.execute(
            f"SELECT guid, ifc_class, discipline FROM elements_meta "
            f"WHERE guid IN ({placeholders}) AND discipline='ARC'",
            guids
        ).fetchall()

        if not rows:
            print(f"    No ARC-labelled elements found for this file.")
            continue

        fix_guids = [r[0] for r in rows]
        classes = set(r[1] for r in rows)
        print(f"    Fixing {len(fix_guids)} elements (classes: {classes}) → {discipline}")

        if not dry_run:
            fix_placeholders = ",".join("?" * len(fix_guids))
            conn.execute(
                f"UPDATE elements_meta SET discipline=? "
                f"WHERE guid IN ({fix_placeholders})",
                [discipline] + fix_guids
            )
            conn.commit()

        total_fixed += len(fix_guids)

    conn.close()

    print(f"\nTotal fixed: {total_fixed} elements")
    if dry_run:
        print("(dry run — no changes written)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True)
    parser.add_argument("--ifc-dir", required=True)
    parser.add_argument("--mapping", nargs="+", required=True,
                        help="filename=DISCIPLINE pairs e.g. Hospital_IFC4_ELE.ifc=ELEC")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    fix_disciplines(args.db, args.ifc_dir, args.mapping, args.dry_run)


if __name__ == "__main__":
    main()
