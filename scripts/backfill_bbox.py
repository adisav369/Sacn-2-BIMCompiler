#!/usr/bin/env python3
"""Backfill bbox_x/y/z in deploy/buildings/*_extracted.db from sandbox_1M R-tree.

The R-tree (elements_rtree) stores minX/maxX/minY/maxY/minZ/maxZ per element —
this IS extracted data from the IFC extraction pipeline, not computed from vertices.

Source: deploy/sandbox_1M_extracted.db (elements_rtree JOIN elements_meta)
Target: deploy/buildings/*_extracted.db (element_transforms gets bbox columns)
"""
import sqlite3, os, glob

BUILDINGS_DIR = "deploy/buildings"
SANDBOX = "deploy/sandbox_1M_extracted.db"


def load_bbox_lookup(sandbox_path):
    """Load all GUIDs with bbox from sandbox R-tree."""
    conn = sqlite3.connect(sandbox_path)
    rows = conn.execute("""
        SELECT t.guid,
               (r.minX + r.maxX) / 2.0, (r.minY + r.maxY) / 2.0, (r.minZ + r.maxZ) / 2.0,
               r.maxX - r.minX, r.maxY - r.minY, r.maxZ - r.minZ
        FROM element_transforms t
        JOIN elements_meta m ON t.guid = m.guid
        JOIN elements_rtree r ON m.id = r.id
    """).fetchall()
    conn.close()
    # guid → (cx, cy, cz, bx, by, bz)
    return {r[0]: r[1:] for r in rows}


def backfill_one(ext_path, lookup):
    """Add bbox columns and populate from lookup."""
    name = os.path.basename(ext_path)

    ext = sqlite3.connect(ext_path)
    ec = ext.cursor()

    # Check if table exists
    has_table = ec.execute("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='element_transforms'").fetchone()[0]
    has_meta = ec.execute("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='elements_meta'").fetchone()[0]
    if not has_table:
        if not has_meta:
            print(f"  SKIP {name} (no elements_meta or element_transforms — incomplete DB)")
            ext.close()
            return 0
        # Create table and populate from elements_meta GUIDs
        ec.execute("""CREATE TABLE element_transforms (
            guid TEXT PRIMARY KEY,
            center_x REAL, center_y REAL, center_z REAL,
            rotation_x REAL, rotation_y REAL, rotation_z REAL,
            bbox_x REAL, bbox_y REAL, bbox_z REAL)""")
        guids = [r[0] for r in ec.execute("SELECT guid FROM elements_meta").fetchall()]
        updated = 0
        for guid in guids:
            if guid in lookup:
                cx, cy, cz, bx, by, bz = lookup[guid]
                ec.execute("INSERT INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)",
                           (guid, cx, cy, cz, 0, 0, 0, bx, by, bz))
                updated += 1
            else:
                ec.execute("INSERT INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)",
                           (guid, 0, 0, 0, 0, 0, 0, None, None, None))
        ext.commit()
        ext.close()
        print(f"  DONE {name}: CREATED table, {updated}/{len(guids)} elements with bbox")
        return updated

    # Check current columns
    cols = [r[1] for r in ec.execute("PRAGMA table_info(element_transforms)").fetchall()]

    if 'bbox_x' in cols:
        has_data = ec.execute("SELECT count(*) FROM element_transforms WHERE bbox_x IS NOT NULL").fetchone()[0]
        if has_data > 0:
            print(f"  SKIP {name} (bbox already populated: {has_data} rows)")
            ext.close()
            return 0

    # Rebuild table with correct schema
    # Get existing data
    existing = ec.execute("SELECT * FROM element_transforms").fetchall()
    col_names = [r[1] for r in ec.execute("PRAGMA table_info(element_transforms)").fetchall()]

    # Map old columns to values
    guid_idx = col_names.index('guid')
    cx_idx = col_names.index('center_x') if 'center_x' in col_names else None
    cy_idx = col_names.index('center_y') if 'center_y' in col_names else None
    cz_idx = col_names.index('center_z') if 'center_z' in col_names else None
    rx_idx = col_names.index('rotation_x') if 'rotation_x' in col_names else None
    ry_idx = col_names.index('rotation_y') if 'rotation_y' in col_names else None
    rz_idx = col_names.index('rotation_z') if 'rotation_z' in col_names else None

    # Drop and recreate with new schema
    ec.execute("DROP TABLE element_transforms")
    ec.execute("""CREATE TABLE element_transforms (
        guid TEXT PRIMARY KEY,
        center_x REAL, center_y REAL, center_z REAL,
        rotation_x REAL, rotation_y REAL, rotation_z REAL,
        bbox_x REAL, bbox_y REAL, bbox_z REAL)""")

    updated = 0
    for row in existing:
        guid = row[guid_idx]
        # Get bbox from lookup (R-tree extracted data)
        if guid in lookup:
            cx, cy, cz, bx, by, bz = lookup[guid]
            rx = row[rx_idx] if rx_idx is not None else 0
            ry = row[ry_idx] if ry_idx is not None else 0
            rz = row[rz_idx] if rz_idx is not None else 0
            ec.execute("INSERT INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)",
                       (guid, cx, cy, cz, rx, ry, rz, bx, by, bz))
            updated += 1
        else:
            # Keep old center, no bbox
            cx = row[cx_idx] if cx_idx is not None else 0
            cy = row[cy_idx] if cy_idx is not None else 0
            cz = row[cz_idx] if cz_idx is not None else 0
            rx = row[rx_idx] if rx_idx is not None else 0
            ry = row[ry_idx] if ry_idx is not None else 0
            rz = row[rz_idx] if rz_idx is not None else 0
            ec.execute("INSERT INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)",
                       (guid, cx, cy, cz, rx, ry, rz, None, None, None))

    ext.commit()
    ext.close()
    total = len(existing)
    print(f"  DONE {name}: {updated}/{total} elements with bbox")
    return updated


def main():
    print(f"Loading bbox lookup from {SANDBOX}...")
    lookup = load_bbox_lookup(SANDBOX)
    print(f"  {len(lookup)} GUIDs with R-tree bbox\n")

    total_updated = 0
    ext_files = sorted(glob.glob(os.path.join(BUILDINGS_DIR, "*_extracted.db")))
    print(f"Processing {len(ext_files)} extracted DBs:\n")

    for ext_path in ext_files:
        name = os.path.basename(ext_path).replace("_extracted.db", "")
        print(f"[{name}]")
        count = backfill_one(ext_path, lookup)
        total_updated += count

    print(f"\n{'='*50}")
    print(f"TOTAL: {total_updated} elements updated with bbox across {len(ext_files)} buildings")


if __name__ == "__main__":
    main()
