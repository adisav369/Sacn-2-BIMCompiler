# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Phase 113: Copy water tank geometry BLOBs from federation DB to component library.

SQL cannot do cross-database blob copy, so this script handles it.
Run BEFORE migration_113_federation_equipment.sql (which creates definitions referencing these hashes).

Usage:
    python3 scripts/migrate_tank_geometry.py

Idempotent: INSERT OR IGNORE on geometry_hash primary key.
"""

import sqlite3
import os
import sys

FEDERATION_DB = "database/enhanced_federation_GI.db"
LIBRARY_DB = "library/component_library.db"

# Geometry hashes for the 3 FRP water tanks
TANK_HASHES = [
    "91f7a4dc26d9690d",  # 5x6 tank
    "40a57b3321618ef3",  # 4x4 tank
    "191c806d5f90f5f0",  # 2x4 tank
]

def main():
    # Resolve paths relative to project root
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    fed_path = os.path.join(project_root, FEDERATION_DB)
    lib_path = os.path.join(project_root, LIBRARY_DB)

    if not os.path.exists(fed_path):
        print(f"ERROR: Federation DB not found: {fed_path}")
        sys.exit(1)
    if not os.path.exists(lib_path):
        print(f"ERROR: Library DB not found: {lib_path}")
        sys.exit(1)

    fed_conn = sqlite3.connect(fed_path)
    lib_conn = sqlite3.connect(lib_path)

    copied = 0
    skipped = 0

    for ghash in TANK_HASHES:
        # Check if already exists in library
        existing = lib_conn.execute(
            "SELECT 1 FROM component_geometries WHERE geometry_hash = ?", (ghash,)
        ).fetchone()
        if existing:
            print(f"  SKIP {ghash} (already in library)")
            skipped += 1
            continue

        # Read from federation
        row = fed_conn.execute(
            "SELECT geometry_hash, vertices, faces, normals, vertex_count, face_count "
            "FROM base_geometries WHERE geometry_hash = ?", (ghash,)
        ).fetchone()
        if not row:
            print(f"  ERROR: geometry_hash {ghash} not found in federation DB")
            continue

        geometry_hash, vertices, faces, normals, vertex_count, face_count = row

        # Insert into library
        lib_conn.execute(
            "INSERT OR IGNORE INTO component_geometries "
            "(geometry_hash, vertices, faces, normals, vertex_count, face_count) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (geometry_hash, vertices, faces, normals, vertex_count, face_count)
        )
        print(f"  COPY {ghash} ({vertex_count} verts, {face_count} faces)")
        copied += 1

    lib_conn.commit()
    fed_conn.close()
    lib_conn.close()

    print(f"\nDone: {copied} copied, {skipped} skipped")

if __name__ == "__main__":
    main()
