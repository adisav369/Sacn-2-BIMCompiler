# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""Remove elements by IFC class from a reference/extracted DB.

Usage:
    python3 scripts/removeElementsByType.py DATABASE IFC_CLASS [IFC_CLASS ...]

Examples:
    # Remove opening elements (void placeholders)
    python3 scripts/removeElementsByType.py DAGCompiler/lib/input/merged_federation_extracted.db IfcOpeningElement

    # Remove multiple types
    python3 scripts/removeElementsByType.py some.db IfcOpeningElement IfcSpace

    # Dry run — report what would be removed without changing anything
    python3 scripts/removeElementsByType.py some.db IfcOpeningElement --dry-run
"""

import sqlite3
import sys
import os


def remove_elements_by_type(db_path, ifc_classes, dry_run=False):
    if not os.path.exists(db_path):
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Show before counts
    cur.execute("SELECT COUNT(*) FROM elements_meta")
    total_before = cur.fetchone()[0]

    for ifc_class in ifc_classes:
        cur.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE ifc_class = ?", (ifc_class,)
        )
        count = cur.fetchone()[0]

        if count == 0:
            print(f"  {ifc_class}: 0 elements found — skipping")
            continue

        if dry_run:
            print(f"  {ifc_class}: {count} elements would be removed (dry-run)")
            continue

        # Get IDs to remove from rtree
        cur.execute(
            "SELECT id FROM elements_meta WHERE ifc_class = ?", (ifc_class,)
        )
        ids = [row[0] for row in cur.fetchall()]

        # Delete from rtree first (virtual table, must delete by id)
        for eid in ids:
            cur.execute("DELETE FROM elements_rtree WHERE id = ?", (eid,))

        # Delete from main table
        cur.execute("DELETE FROM elements_meta WHERE ifc_class = ?", (ifc_class,))

        print(f"  {ifc_class}: {count} elements removed")

    if not dry_run:
        conn.commit()
        conn.execute("VACUUM")

    cur.execute("SELECT COUNT(*) FROM elements_meta")
    total_after = cur.fetchone()[0]

    conn.close()

    action = "would remain" if dry_run else "remaining"
    print(f"\nTotal: {total_before} → {total_after} ({action}), "
          f"delta: -{total_before - total_after}")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    db_path = sys.argv[1]
    dry_run = "--dry-run" in sys.argv
    ifc_classes = [a for a in sys.argv[2:] if a != "--dry-run"]

    print(f"Database: {db_path}")
    print(f"{'DRY RUN — ' if dry_run else ''}Removing: {', '.join(ifc_classes)}\n")

    remove_elements_by_type(db_path, ifc_classes, dry_run)


if __name__ == "__main__":
    main()
