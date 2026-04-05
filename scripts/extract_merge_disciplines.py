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
import shutil
from pathlib import Path


def fix_bbox_units(tmp_db_path: Path, ifc_path: Path):
    """Fix element_transforms and elements_rtree rows that bbox_from_placement
    wrote in file-native units (e.g. mm) instead of metres.

    create_shape(USE_WORLD_COORDS) already returns metres.
    get_local_placement() used by bbox_from_placement returns raw file units.
    Detect outliers (|coord| >> expected building scale) and rescale them.
    Also subtract the site placement origin so the model sits near Z=0.
    """
    try:
        import ifcopenshell
        import ifcopenshell.util.placement as ifcplace
        import ifcopenshell.util.unit as ifcunit
    except ImportError:
        return  # ifcopenshell not available — skip silently

    try:
        ifc = ifcopenshell.open(str(ifc_path))
    except Exception:
        return

    unit_scale = ifcunit.calculate_unit_scale(ifc, 'LENGTHUNIT')  # e.g. 0.001 for mm

    # Site origin in metres
    ox = oy = oz = 0.0
    try:
        sites = ifc.by_type('IfcSite')
        if sites and sites[0].ObjectPlacement:
            m = ifcplace.get_local_placement(sites[0].ObjectPlacement)
            ox = float(m[0][3]) * unit_scale
            oy = float(m[1][3]) * unit_scale
            oz = float(m[2][3]) * unit_scale
    except Exception:
        pass

    conn = sqlite3.connect(str(tmp_db_path))

    # --- 1. Fix bbox_from_placement outliers (stored in native units, not metres) ---
    # Threshold: any coord whose magnitude exceeds 1/unit_scale * 10 is almost
    # certainly in native units (e.g. >10 000 for a mm file with unit_scale=0.001).
    if unit_scale < 1.0:
        threshold = (1.0 / unit_scale) * 10  # e.g. 10 000 for mm files
        conn.execute(
            "UPDATE element_transforms "
            "SET center_x = center_x * ?, center_y = center_y * ?, center_z = center_z * ? "
            "WHERE abs(center_x) > ? OR abs(center_y) > ? OR abs(center_z) > ?",
            (unit_scale, unit_scale, unit_scale, threshold, threshold, threshold)
        )
        conn.execute(
            "UPDATE elements_rtree "
            "SET minX=minX*?, maxX=maxX*?, minY=minY*?, maxY=maxY*?, minZ=minZ*?, maxZ=maxZ*? "
            "WHERE abs(minX) > ? OR abs(maxX) > ? OR abs(minY) > ? "
            "   OR abs(maxY) > ? OR abs(minZ) > ? OR abs(maxZ) > ?",
            (unit_scale,)*6 + (threshold,)*6
        )

    # --- 2. Subtract site origin so building sits near (0,0,0) ---
    if abs(ox) > 1.0 or abs(oy) > 1.0 or abs(oz) > 1.0:
        conn.execute(
            "UPDATE element_transforms "
            "SET center_x=center_x-?, center_y=center_y-?, center_z=center_z-?",
            (ox, oy, oz)
        )
        conn.execute(
            "UPDATE elements_rtree "
            "SET minX=minX-?, maxX=maxX-?, minY=minY-?, maxY=maxY-?, minZ=minZ-?, maxZ=maxZ-?",
            (ox, ox, oy, oy, oz, oz)
        )

    conn.commit()
    conn.close()

    if unit_scale < 1.0 or abs(oz) > 1.0:
        print(f"  → coord fix applied: unit_scale={unit_scale}, site_origin=({ox:.2f},{oy:.2f},{oz:.2f})m")


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
    args = parser.parse_args()

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

        fix_bbox_units(tmp_db, ifc)
        merge_db(tmp_db, dst, disc)
        tmp_db.unlink()

    total = dst.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    by_disc = dst.execute(
        "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC"
    ).fetchall()

    # Store building center as camera target — loader uses this to face the building
    try:
        row = dst.execute(
            "SELECT AVG(center_x), AVG(center_y), AVG(center_z) FROM element_transforms"
        ).fetchone()
        if row and row[0] is not None:
            cx, cy, cz = row
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_x', ?)", (str(cx),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_y', ?)", (str(cy),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_z', ?)", (str(cz),))
            dst.commit()
            print(f"  Camera target: ({cx:.2f}, {cy:.2f}, {cz:.2f})m")
    except Exception as e:
        print(f"  Camera target write skipped: {e}")

    dst.close()

    size_mb = output.stat().st_size / (1024 * 1024)
    print(f"\n✓ Done: {output.name} — {size_mb:.1f} MB, {total} elements")
    for d, c in by_disc:
        print(f"  {d}: {c}")


if __name__ == "__main__":
    main()
