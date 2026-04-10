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
    center_x REAL, center_y REAL, center_z REAL, transform_source TEXT,
    rotation_x REAL DEFAULT 0, rotation_y REAL DEFAULT 0, rotation_z REAL DEFAULT 0
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


def fix_unit_scale(tmp_db_path: Path, ifc_path: Path):
    """
    Apply unit scale to all element coordinates and vertex BLOBs.

    The geom.iterator with USE_WORLD_COORDS=False returns coordinates in
    the IFC file's native length unit. If the file uses mm (unit_scale=0.001),
    all coordinates and vertices need scaling to metres.

    This is dynamic — reads unit_scale from the IFC, applies if != 1.0.
    No-op for metre-unit files.
    """
    import ifcopenshell
    import ifcopenshell.util.unit

    ifc = ifcopenshell.open(str(ifc_path))
    unit_scale = ifcopenshell.util.unit.calculate_unit_scale(ifc, "LENGTHUNIT")
    if abs(unit_scale - 1.0) < 1e-9:
        return  # File is metre-unit, nothing to do

    conn = sqlite3.connect(str(tmp_db_path))

    print(f"  §UNIT_SCALE {ifc_path.name}: unit_scale={unit_scale} — scaling all coordinates to metres")

    # Scale ALL element_transforms coordinates
    conn.execute("""
        UPDATE element_transforms SET
            center_x = center_x * ?,
            center_y = center_y * ?,
            center_z = center_z * ?
    """, (unit_scale, unit_scale, unit_scale))

    # Scale ALL rtree entries
    conn.execute("""
        UPDATE elements_rtree SET
            minX = minX * ?, maxX = maxX * ?,
            minY = minY * ?, maxY = maxY * ?,
            minZ = minZ * ?, maxZ = maxZ * ?
    """, (unit_scale, unit_scale, unit_scale, unit_scale, unit_scale, unit_scale))

    # Scale vertex BLOBs in base_geometries
    import numpy as np
    scaled_hashes = set()
    for row in conn.execute(
            "SELECT DISTINCT ei.geometry_hash, bg.vertices, bg.vertex_count "
            "FROM element_instances ei "
            "JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash "
            "WHERE bg.vertices IS NOT NULL").fetchall():
        ghash, vblob, vcount = row
        if ghash in scaled_hashes or not vblob or vcount == 0:
            continue
        verts = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
        if np.abs(verts).max() > 1.0:  # has actual geometry
            verts_scaled = (verts * unit_scale).astype(np.float32)
            conn.execute(
                "UPDATE base_geometries SET vertices=? WHERE geometry_hash=?",
                (verts_scaled.tobytes(), ghash))
        scaled_hashes.add(ghash)

    conn.commit()
    n_meshes = len(scaled_hashes)
    conn.close()
    print(f"  §UNIT_SCALE scaled to metres ({n_meshes} meshes)")


def _read_geolocation(ifc_path: Path):
    """Read geolocation origin from an IFC file using the community's
    auto_xyz2enh (same logic as MergeProjects in IfcPatch).

    Returns dict with:
        enh: (easting, northing, height) — real-world coords of IFC origin
        grid_north: angle in degrees
        ifc: opened ifcopenshell.file (reused by caller)
    """
    import ifcopenshell
    import ifcopenshell.util.geolocation as geo
    try:
        ifc = ifcopenshell.open(str(ifc_path))
        enh = geo.auto_xyz2enh(ifc, 0, 0, 0, should_return_in_map_units=False)
        north = geo.get_grid_north(ifc)
        return {'enh': tuple(enh), 'grid_north': north, 'ifc': ifc}
    except Exception as e:
        print(f"  Warning: geolocation read failed for {ifc_path.name}: {e}")
        return {'enh': (0.0, 0.0, 0.0), 'grid_north': 0.0, 'ifc': None}


def _compute_alignment(ref_geo, other_geo):
    """Compute XYZ correction to align other discipline to reference.

    Uses the same logic as MergeProjects: compare real-world origins
    (auto_xyz2enh) and compute the XYZ shift needed in local coords.

    Returns (dx, dy, dz) to ADD to other's element coordinates.
    """
    import numpy as np
    import ifcopenshell.util.geolocation as geo

    ref_enh = np.array(ref_geo['enh'])
    other_enh = np.array(other_geo['enh'])

    if np.allclose(ref_enh, other_enh, atol=0.01) and \
       np.isclose(ref_geo['grid_north'], other_geo['grid_north'], atol=0.01):
        return (0.0, 0.0, 0.0)  # Already aligned

    # Convert reference origin into other's local coordinate system
    other_ifc = other_geo['ifc']
    if other_ifc is None:
        return (0.0, 0.0, 0.0)

    try:
        # Where does the reference origin land in other's local coords?
        x, y, z = geo.auto_enh2xyz(other_ifc, *ref_enh, is_specified_in_map_units=False)
        # The correction is: element should be at (element_local + correction)
        # where correction = (x, y, z) because that's where (0,0,0) of ref maps to in other
        return (float(x), float(y), float(z))
    except Exception as e:
        # Fallback: simple subtraction
        dx = ref_enh[0] - other_enh[0]
        dy = ref_enh[1] - other_enh[1]
        dz = ref_enh[2] - other_enh[2]
        print(f"  Warning: auto_enh2xyz failed ({e}), using simple offset ({dx:.1f}, {dy:.1f}, {dz:.1f})")
        return (dx, dy, dz)


def merge_db(src_path: Path, dst: sqlite3.Connection, disc_label: str,
             correction=(0.0, 0.0, 0.0)):
    """Merge src DB into dst. Applies coordinate correction (dx, dy, dz)
    to align this discipline's elements to the reference coordinate system."""
    src = sqlite3.connect(src_path)
    src.row_factory = sqlite3.Row
    dx, dy, dz = correction
    has_offset = abs(dx) > 0.01 or abs(dy) > 0.01 or abs(dz) > 0.01

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

    # element_transforms — apply site offset correction + copy rotation
    # Check if source has rotation columns
    src_cols = {r[1] for r in src.execute("PRAGMA table_info(element_transforms)").fetchall()}
    has_rot = 'rotation_x' in src_cols

    for row in src.execute("SELECT * FROM element_transforms"):
        cx = (row["center_x"] or 0.0) + dx
        cy = (row["center_y"] or 0.0) + dy
        cz = (row["center_z"] or 0.0) + dz
        rx = float(row["rotation_x"] or 0.0) if has_rot else 0.0
        ry = float(row["rotation_y"] or 0.0) if has_rot else 0.0
        rz = float(row["rotation_z"] or 0.0) if has_rot else 0.0
        dst.execute(
            "INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?,?,?,?)",
            (row["guid"], cx, cy, cz, row["transform_source"], rx, ry, rz)
        )

    # elements_rtree — apply same offset, use meta id from destination
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
                    (meta[0], bbox[0]+dx, bbox[1]+dx, bbox[2]+dy, bbox[3]+dy, bbox[4]+dz, bbox[5]+dz)
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


def post_normalise_site_origin(dst: sqlite3.Connection, first_ifc: Path):
    """
    Subtract IfcSite placement origin from all element coordinates so the building
    sits near ground level rather than at a real-world datum (e.g. 165m elevation).

    Updates element_transforms and elements_rtree together.
    Camera target is computed by the caller AFTER this function, so it self-corrects.
    No-op when |site_oz| <= 1.0m (site at or near origin).

    Logic source: scripts/topup_extracted_db.py lines 49-147.
    Witness: W-SITE-Z-1 in scripts/verify_extraction.sh.
    """
    try:
        import ifcopenshell
        import ifcopenshell.util.placement as ifcplace
        import ifcopenshell.util.unit as ifcunit
    except ImportError:
        print("  post_normalise_site_origin: ifcopenshell not available — skipping")
        return

    try:
        ifc = ifcopenshell.open(str(first_ifc))
        unit_scale = ifcunit.calculate_unit_scale(ifc, 'LENGTHUNIT')
        sites = ifc.by_type('IfcSite')
        if not sites or not sites[0].ObjectPlacement:
            return
        m = ifcplace.get_local_placement(sites[0].ObjectPlacement)
        site_oz = float(m[2][3]) * unit_scale
    except Exception as e:
        print(f"  post_normalise_site_origin: could not read site placement ({e}) — skipping")
        return

    if abs(site_oz) <= 1.0:
        return

    print(f"  Site origin Z={site_oz:.3f}m — subtracting from all element coordinates")
    dst.execute("UPDATE element_transforms SET center_z = center_z - ?", (site_oz,))
    dst.execute("""
        UPDATE elements_rtree SET
            minZ = minZ - ?,
            maxZ = maxZ - ?
    """, (site_oz, site_oz))
    dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('site_offset_z', ?)",
                (str(round(site_oz, 4)),))
    dst.commit()
    print(f"  → Z normalised. site_offset_z={site_oz:.3f}m stored in project_metadata")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ifc-dir", required=True)
    parser.add_argument("--pattern", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--extractor", default="DAGCompiler/python/extractIFCtoDB.py")
    parser.add_argument("--library", help="Component library DB path (passed to extractor as --library)")
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

    import time as _time
    t0 = _time.time()

    print(f"Extracting {len(ifc_files)} discipline files → {output.name}")
    if disc_map:
        print(f"  Disc overrides: {disc_map}")

    # ── S172: Parallel extraction — launch all disciplines concurrently ──
    # Each discipline IFC is extracted to its own temp DB in parallel.
    # Then merge sequentially (fast, just DB row copies).
    jobs = []  # (ifc, tmp_db, process)
    for ifc in ifc_files:
        tmp_db = Path(tempfile.mktemp(suffix=f"_{ifc.stem}.db"))
        cmd = [sys.executable, args.extractor, "--ifc", str(ifc), "-o", str(tmp_db)]
        if args.library:
            cmd.extend(["--library", args.library])
        log_file = open(str(tmp_db) + ".log", 'w')
        proc = subprocess.Popen(cmd, stdout=log_file, stderr=subprocess.STDOUT)
        jobs.append((ifc, tmp_db, proc, log_file))
        size_mb = ifc.stat().st_size / 1024 / 1024
        print(f"  [PID {proc.pid}] {ifc.stem} ({size_mb:.1f} MB) → {tmp_db.name}")

    print(f"\n  {len(jobs)} extractions running in parallel...")

    # Wait for all to finish, report as they complete
    completed = set()
    while len(completed) < len(jobs):
        for i, (ifc, tmp_db, proc, log_file) in enumerate(jobs):
            if i in completed:
                continue
            ret = proc.poll()
            if ret is not None:
                log_file.close()
                elapsed = _time.time() - t0
                if ret == 0 and tmp_db.exists() and tmp_db.stat().st_size > 0:
                    # Read element count from log
                    log_path = str(tmp_db) + ".log"
                    elems = "?"
                    try:
                        with open(log_path) as lf:
                            for line in lf:
                                if "§EXTRACT Elements:" in line:
                                    elems = line.strip()
                                    break
                    except:
                        pass
                    print(f"  ✓ [{int(elapsed)}s] {ifc.stem} done — {elems}")
                else:
                    print(f"  ✗ [{int(elapsed)}s] {ifc.stem} FAILED (exit={ret})")
                completed.add(i)
        if len(completed) < len(jobs):
            _time.sleep(1)

    extract_time = _time.time() - t0
    print(f"\n  All {len(jobs)} extractions done in {extract_time:.0f}s")

    # ── Geolocation alignment (same logic as IfcPatch MergeProjects) ──
    # Read geolocation from each IFC, compute corrections relative to reference
    print(f"\n  §ALIGN Reading geolocation from {len(jobs)} discipline IFCs...")
    geo_data = {}  # ifc_stem -> geo dict
    for ifc, tmp_db, proc, log_file in jobs:
        if tmp_db.exists() and tmp_db.stat().st_size > 0:
            geo = _read_geolocation(ifc)
            geo_data[ifc.stem] = geo
            enh = geo['enh']
            print(f"    {ifc.stem:45s} enh=({enh[0]:>10.2f}, {enh[1]:>10.2f}, {enh[2]:>10.2f})  north={geo['grid_north']:.1f}")

    # Pick reference: first file (consistent with MergeProjects behavior)
    ref_stem = list(geo_data.keys())[0] if geo_data else None
    ref_geo = geo_data.get(ref_stem)
    if ref_geo:
        print(f"  §ALIGN Reference: {ref_stem}")

    # Compute corrections
    corrections = {}  # ifc_stem -> (dx, dy, dz)
    for stem, geo in geo_data.items():
        if stem == ref_stem:
            corrections[stem] = (0.0, 0.0, 0.0)
        else:
            corr = _compute_alignment(ref_geo, geo)
            corrections[stem] = corr
            if abs(corr[0]) > 0.01 or abs(corr[1]) > 0.01 or abs(corr[2]) > 0.01:
                print(f"    {stem:45s} correction=({corr[0]:>10.2f}, {corr[1]:>10.2f}, {corr[2]:>10.2f})")

    # ── Sequential merge (fast — just DB row copies) ──
    merge_t0 = _time.time()
    for ifc, tmp_db, proc, log_file in jobs:
        disc = ifc.stem
        if not tmp_db.exists() or tmp_db.stat().st_size == 0:
            continue

        fix_unit_scale(tmp_db, ifc)

        # Apply discipline override
        override = disc_map.get(ifc.stem)
        if override:
            conn = sqlite3.connect(str(tmp_db))
            conn.execute("UPDATE elements_meta SET discipline=?", (override,))
            conn.commit()
            conn.close()
            print(f"  [{disc}] → discipline overridden to {override}")

        corr = corrections.get(ifc.stem, (0.0, 0.0, 0.0))
        merge_db(tmp_db, dst, disc, correction=corr)
        tmp_db.unlink()
        log_path = Path(str(tmp_db) + ".log")
        if log_path.exists():
            log_path.unlink()

    merge_time = _time.time() - merge_t0
    print(f"  Merge phase: {merge_time:.1f}s")

    total = dst.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    by_disc = dst.execute(
        "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC"
    ).fetchall()

    # Post-action 1: Legacy Z-only normalization from IfcSite placement
    post_normalise_site_origin(dst, ifc_files[0])

    # Post-action 2 (S172): Full XYZ re-normalization of merged DB.
    # Each discipline was normalized to its own centroid during extraction.
    # After merge, re-center ALL elements to a single shared origin.
    row = dst.execute("""
        SELECT AVG(center_x), AVG(center_y), MIN(center_z)
        FROM element_transforms
    """).fetchone()
    if row and row[0] is not None:
        ox, oy, oz = row[0], row[1], row[2]
        if abs(ox) > 100 or abs(oy) > 100 or abs(oz) > 100:
            print(f"  §NORMALIZE merged centroid far from origin: ({ox:.1f}, {oy:.1f}, {oz:.1f})")
            dst.execute("""
                UPDATE element_transforms
                SET center_x = center_x - ?, center_y = center_y - ?, center_z = center_z - ?
            """, (ox, oy, oz))
            dst.execute("""
                UPDATE elements_rtree
                SET minX = minX - ?, maxX = maxX - ?,
                    minY = minY - ?, maxY = maxY - ?,
                    minZ = minZ - ?, maxZ = maxZ - ?
            """, (ox, ox, oy, oy, oz, oz))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('merge_offset_x', ?)", (str(round(ox,4)),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('merge_offset_y', ?)", (str(round(oy,4)),))
            dst.execute("INSERT OR REPLACE INTO project_metadata VALUES ('merge_offset_z', ?)", (str(round(oz,4)),))
            dst.commit()
            vrow = dst.execute("SELECT AVG(center_x), AVG(center_y), MIN(center_z) FROM element_transforms").fetchone()
            print(f"  §NORMALIZE after: centroid=({vrow[0]:.1f}, {vrow[1]:.1f}, {vrow[2]:.1f})")
        else:
            print(f"  §NORMALIZE merged centroid near origin: ({ox:.1f}, {oy:.1f}, {oz:.1f}) — skip")

    # Store building center + view distance as camera target for the loader.
    # Must run AFTER post_normalise_site_origin so camera tracks corrected coords.
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
