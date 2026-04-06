#!/usr/bin/env python3
"""
Extract IFC disciplines separately then merge into one extracted.db.

Usage — multiple disciplines (primary use case):
    python3 scripts/extract_merge_disciplines.py \
        --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
        --pattern "Hospital_IFC4_*.ifc" \
        --output DAGCompiler/lib/input/Hospital_extracted.db

Usage — single file (works too, glob matches one file):
    python3 scripts/extract_merge_disciplines.py \
        --ifc-dir DAGCompiler/lib/input/IFC \
        --pattern "Clinic_Federated.ifc" \
        --output DAGCompiler/lib/input/Clinic_extracted.db

    Note: for a single file, calling extractIFCtoDB.py directly is simpler:
        python3 DAGCompiler/python/extractIFCtoDB.py \
            --ifc DAGCompiler/lib/input/IFC/Clinic_Federated.ifc \
            -o DAGCompiler/lib/input/Clinic_extracted.db

Why per-discipline:
    Extracting a large merged IFC (200MB+) causes ifcopenshell geometry
    iterator to OOM on the tessellation loop. Extracting each discipline
    file separately caps RAM to one file at a time.

    Also avoids needing a merged IFC at all — the BIM compiler pipeline
    only needs the _extracted.db, not the merged IFC file.
"""

import argparse
import sqlite3
import subprocess
import sys
import tempfile
from pathlib import Path


SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS project_metadata (
    key TEXT PRIMARY KEY,
    value TEXT
);
CREATE TABLE IF NOT EXISTS spatial_structure (
    guid TEXT PRIMARY KEY, type TEXT NOT NULL, name TEXT,
    parent_guid TEXT, object_type TEXT, predefined_type TEXT
);
CREATE TABLE IF NOT EXISTS elements_meta (
    id INTEGER PRIMARY KEY, guid TEXT UNIQUE NOT NULL,
    discipline TEXT NOT NULL, ifc_class TEXT NOT NULL,
    element_name TEXT, element_type TEXT, storey TEXT,
    material_name TEXT, material_rgba TEXT
);
CREATE VIRTUAL TABLE IF NOT EXISTS elements_rtree
    USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ);
CREATE TABLE IF NOT EXISTS base_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB, faces BLOB,
    vertex_count INTEGER, face_count INTEGER
);
CREATE TABLE IF NOT EXISTS element_instances (
    guid TEXT PRIMARY KEY, geometry_hash TEXT,
    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
);
CREATE TABLE IF NOT EXISTS element_transforms (
    guid TEXT PRIMARY KEY,
    center_x REAL, center_y REAL, center_z REAL, transform_source TEXT
);
CREATE TABLE IF NOT EXISTS rel_contained_in_space (
    element_guid TEXT, space_guid TEXT,
    PRIMARY KEY (element_guid, space_guid)
);
CREATE TABLE IF NOT EXISTS rel_aggregates (
    parent_guid TEXT NOT NULL, child_guid TEXT NOT NULL,
    PRIMARY KEY (parent_guid, child_guid)
);
CREATE TABLE IF NOT EXISTS surface_styles (
    style_name TEXT PRIMARY KEY,
    surface_r REAL, surface_g REAL, surface_b REAL,
    transparency REAL DEFAULT 0.0,
    specular_r REAL, specular_g REAL, specular_b REAL,
    specular_ratio REAL, specular_exponent REAL,
    reflectance_method TEXT DEFAULT 'NOTDEFINED',
    side TEXT DEFAULT 'BOTH', source TEXT
);
CREATE TABLE IF NOT EXISTS material_layers (
    layer_set_name TEXT NOT NULL, sequence INTEGER NOT NULL,
    material_name TEXT, thickness_m REAL, is_ventilated INTEGER DEFAULT 0,
    PRIMARY KEY (layer_set_name, sequence)
);
"""


def fix_mm_outliers(tmp_db_path: Path, ifc_path: Path):
    """
    ifcopenshell USE_WORLD_COORDS=True normally returns metres.  However,
    some IFC2x3 files (e.g. LTU_AHouse_STR) contain elements whose placement
    coordinates come through in native units (mm) despite the flag.

    This function detects whether the tmp_db has coordinates in mm range
    (any |center| > 500) and, if the IFC's LENGTHUNIT is mm (scale=0.001),
    scales ONLY those outlier rows to metres.
    """
    import ifcopenshell
    import ifcopenshell.util.unit

    conn = sqlite3.connect(str(tmp_db_path))

    # Check if any coordinates are in mm range
    row = conn.execute(
        "SELECT MAX(ABS(center_x)), MAX(ABS(center_y)), MAX(ABS(center_z)) "
        "FROM element_transforms"
    ).fetchone()
    if not row or row[0] is None:
        conn.close()
        return
    max_abs = max(row[0], row[1], row[2])
    if max_abs <= 500.0:
        conn.close()
        return  # All in metres, nothing to do

    # Confirm the IFC is actually mm-unit
    ifc = ifcopenshell.open(str(ifc_path))
    unit_scale = ifcopenshell.util.unit.calculate_unit_scale(ifc, "LENGTHUNIT")
    if abs(unit_scale - 1.0) < 1e-9:
        conn.close()
        return  # File is metre-unit, large coords are genuine

    print(f"  → mm outliers detected (max_abs={max_abs:.0f}, unit_scale={unit_scale})")

    # Scale only elements whose center exceeds 500 in any axis
    # These are the ones ifcopenshell didn't convert
    outliers = conn.execute(
        "SELECT guid, center_x, center_y, center_z FROM element_transforms "
        "WHERE ABS(center_x) > 500 OR ABS(center_y) > 500 OR ABS(center_z) > 500"
    ).fetchall()

    for guid, cx, cy, cz in outliers:
        conn.execute(
            "UPDATE element_transforms SET center_x=?, center_y=?, center_z=? WHERE guid=?",
            (cx * unit_scale, cy * unit_scale, cz * unit_scale, guid))

    # Scale matching rtree entries
    for guid, cx, cy, cz in outliers:
        meta = conn.execute("SELECT id FROM elements_meta WHERE guid=?", (guid,)).fetchone()
        if meta:
            conn.execute(
                "UPDATE elements_rtree SET "
                "minX=minX*?, maxX=maxX*?, minY=minY*?, maxY=maxY*?, minZ=minZ*?, maxZ=maxZ*? "
                "WHERE id=?",
                (unit_scale, unit_scale, unit_scale, unit_scale, unit_scale, unit_scale, meta[0]))

    # Scale matching vertex blobs — track already-scaled hashes to avoid
    # double-scaling shared geometry (deduplication means multiple elements
    # can reference the same geometry_hash)
    import numpy as np
    scaled_hashes = set()
    for guid, cx, cy, cz in outliers:
        row = conn.execute(
            "SELECT ei.geometry_hash, bg.vertices, bg.vertex_count "
            "FROM element_instances ei "
            "JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash "
            "WHERE ei.guid=?", (guid,)).fetchone()
        if row and row[1] and row[2] > 0:
            ghash, vblob, vcount = row
            if ghash in scaled_hashes:
                continue  # Already scaled via another element sharing this geometry
            verts = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
            if np.abs(verts).max() > 500:
                verts_scaled = (verts * unit_scale).astype(np.float32)
                conn.execute(
                    "UPDATE base_geometries SET vertices=? WHERE geometry_hash=?",
                    (verts_scaled.tobytes(), ghash))
            scaled_hashes.add(ghash)

    conn.commit()
    conn.close()
    print(f"  → fixed {len(outliers)} mm-scale elements to metres")


def merge_db(src_path: Path, dst: sqlite3.Connection, disc_label: str):
    src = sqlite3.connect(src_path)
    src.row_factory = sqlite3.Row

    # base_geometries — deduplicate by hash
    for row in src.execute("SELECT * FROM base_geometries"):
        dst.execute(
            "INSERT OR IGNORE INTO base_geometries VALUES (?,?,?,?,?)",
            (row["geometry_hash"], row["vertices"], row["faces"],
             row["vertex_count"], row["face_count"])
        )

    # elements_meta — skip id (autoincrement), use INSERT OR IGNORE for dups
    for row in src.execute("SELECT * FROM elements_meta"):
        try:
            dst.execute(
                "INSERT OR IGNORE INTO elements_meta "
                "(guid, discipline, ifc_class, element_name, element_type, "
                "storey, material_name, material_rgba) VALUES (?,?,?,?,?,?,?,?)",
                (row["guid"], row["discipline"], row["ifc_class"],
                 row["element_name"], row["element_type"], row["storey"],
                 row["material_name"], row["material_rgba"])
            )
        except Exception:
            pass

    # element_instances
    for row in src.execute("SELECT * FROM element_instances"):
        dst.execute(
            "INSERT OR IGNORE INTO element_instances VALUES (?,?)",
            (row["guid"], row["geometry_hash"])
        )

    # element_transforms
    for row in src.execute("SELECT * FROM element_transforms"):
        dst.execute(
            "INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?)",
            (row["guid"], row["center_x"], row["center_y"],
             row["center_z"], row["transform_source"])
        )

    # elements_rtree — use meta id from destination
    for row in src.execute("SELECT * FROM element_transforms"):
        meta = dst.execute(
            "SELECT id FROM elements_meta WHERE guid=?", (row["guid"],)
        ).fetchone()
        if not meta:
            continue
        bbox = src.execute(
            "SELECT minX,maxX,minY,maxY,minZ,maxZ FROM elements_rtree "
            "WHERE id=(SELECT id FROM elements_meta WHERE guid=?)", (row["guid"],)
        ).fetchone()
        if bbox:
            try:
                dst.execute(
                    "INSERT OR IGNORE INTO elements_rtree VALUES (?,?,?,?,?,?,?)",
                    (meta[0], bbox[0], bbox[1], bbox[2], bbox[3], bbox[4], bbox[5])
                )
            except Exception:
                pass

    # spatial_structure
    for row in src.execute("SELECT * FROM spatial_structure"):
        dst.execute(
            "INSERT OR IGNORE INTO spatial_structure VALUES (?,?,?,?,?,?)",
            (row["guid"], row["type"], row["name"], row["parent_guid"],
             row["object_type"], row["predefined_type"])
        )

    # rel_contained_in_space
    for row in src.execute("SELECT * FROM rel_contained_in_space"):
        dst.execute(
            "INSERT OR IGNORE INTO rel_contained_in_space VALUES (?,?)",
            (row["element_guid"], row["space_guid"])
        )

    # rel_aggregates
    for row in src.execute("SELECT * FROM rel_aggregates"):
        dst.execute(
            "INSERT OR IGNORE INTO rel_aggregates VALUES (?,?)",
            (row["parent_guid"], row["child_guid"])
        )

    # surface_styles
    for row in src.execute("SELECT * FROM surface_styles"):
        dst.execute("INSERT OR IGNORE INTO surface_styles VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            tuple(row))

    # material_layers
    for row in src.execute("SELECT * FROM material_layers"):
        dst.execute("INSERT OR IGNORE INTO material_layers VALUES (?,?,?,?,?)",
            tuple(row))

    dst.commit()
    src.close()

    count = dst.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    print(f"  → merged {disc_label}: running total {count} elements")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ifc-dir", required=True)
    parser.add_argument("--pattern", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--extractor", default="DAGCompiler/python/extractIFCtoDB.py")
    parser.add_argument(
        "--disc-map", nargs="*", metavar="STEM=DISC",
        help="Override discipline by source filename stem. "
             "E.g. --disc-map LTU_AHouse_PLB=PLB LTU_AHouse_SAN=SAN LTU_AHouse_HEAT=HEAT"
    )
    args = parser.parse_args()

    # Build stem→discipline override dict from --disc-map pairs
    disc_map = {}
    if args.disc_map:
        for pair in args.disc_map:
            stem, disc = pair.split("=", 1)
            disc_map[stem.strip()] = disc.strip().upper()

    ifc_files = sorted(Path(args.ifc_dir).glob(args.pattern))
    if not ifc_files:
        print(f"No files matching {args.pattern} in {args.ifc_dir}")
        sys.exit(1)

    output = Path(args.output)
    if output.exists():
        output.unlink()

    dst = sqlite3.connect(output)
    dst.executescript(SCHEMA_SQL)
    dst.commit()

    print(f"Extracting {len(ifc_files)} discipline files → {output.name}")
    if disc_map:
        print(f"  Disc overrides: {disc_map}")

    for ifc in ifc_files:
        disc = ifc.stem  # e.g. Hospital_IFC4_ARC
        tmp_db = Path(tempfile.mktemp(suffix=".db"))
        print(f"\n[{disc}] Extracting {ifc.name} ({ifc.stat().st_size/1024/1024:.1f}MB)...")

        result = subprocess.run(
            [sys.executable, args.extractor, "--ifc", str(ifc), "-o", str(tmp_db)],
            capture_output=False
        )

        if result.returncode != 0 or not tmp_db.exists():
            print(f"  ✗ FAILED — skipping")
            continue

        # Fix elements where ifcopenshell returned mm despite USE_WORLD_COORDS
        fix_mm_outliers(tmp_db, ifc)

        # Apply discipline override: UPDATE elements_meta before merging
        override = disc_map.get(ifc.stem)
        if override:
            conn = sqlite3.connect(str(tmp_db))
            conn.execute("UPDATE elements_meta SET discipline=?", (override,))
            conn.commit()
            conn.close()
            print(f"  → discipline overridden to {override}")

        merge_db(tmp_db, dst, disc)
        tmp_db.unlink()

    total = dst.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    by_disc = dst.execute(
        "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC"
    ).fetchall()

    # Store building center + view distance as camera target for the loader
    try:
        row = dst.execute("""
            SELECT (MIN(center_x)+MAX(center_x))/2.0,
                   (MIN(center_y)+MAX(center_y))/2.0,
                   (MIN(center_z)+MAX(center_z))/2.0,
                   SQRT(POWER(MAX(center_x)-MIN(center_x),2)
                      + POWER(MAX(center_y)-MIN(center_y),2)
                      + POWER(MAX(center_z)-MIN(center_z),2)) * 0.8
            FROM element_transforms
        """).fetchone()
        if row and row[0] is not None:
            cx, cy, cz, vd = row
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_x', ?)", (str(round(cx,2)),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_y', ?)", (str(round(cy,2)),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_z', ?)", (str(round(cz,2)),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_distance',  ?)", (str(round(vd,1)),))
            dst.commit()
            print(f"  Camera target: ({cx:.2f}, {cy:.2f}, {cz:.2f})m  dist={vd:.1f}m")
    except Exception as e:
        print(f"  Camera target write skipped: {e}")

    dst.close()

    size_mb = output.stat().st_size / (1024 * 1024)
    print(f"\n✓ Done: {output.name} — {size_mb:.1f} MB, {total} elements")
    for d, c in by_disc:
        print(f"  {d}: {c}")


if __name__ == "__main__":
    main()
