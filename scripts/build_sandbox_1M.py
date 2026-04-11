#!/usr/bin/env python3
"""
build_sandbox_1M.py
Merges all available extracted.db buildings into a single federation sandbox.
Tiles the precinct 4× to reach ~1M elements.

Output: scripts/sandbox_1M.db  (same schema as extracted.db — openable in Blender)

PRIME RULE: Reads only from existing extracted.db files. No invented geometry.
"""

import sqlite3
import time
import os
from pathlib import Path

REPO       = Path(__file__).parent.parent
INPUT_DIR  = REPO / "DAGCompiler" / "lib" / "input"
OUT_DB     = Path(__file__).parent / "sandbox_1M.db"
LOG_FILE   = Path(__file__).parent / "sandbox_1M_build.log"

TILE_GAP   = 20.0    # metres between tile origins
TILE_COLS  = 1       # single CBD strip (no mirroring)
TILE_ROWS  = 1

# ── CITY LAYOUT ──────────────────────────────────────────────
# CBD core: big buildings in the centre
CBD_BUILDINGS = [
    "HospitalGarage_extracted.db",     # 1,463  — left flank
    "Hospital_extracted.db",           # 63,917 — left hospital
    "LTU_AHouse_extracted.db",         # 4,785
    "Terminal_extracted.db",           # 48,428
    "Hospital_extracted.db",           # 63,917 — right hospital (2nd copy)
    "HospitalGarage_extracted.db",     # 1,463  — right flank
]

# Clinics/offices ring: medium buildings in CBD strip
CLINIC_BUILDINGS = [
    "Clinic_extracted.db",             # 16,480
    "Ifc4_Revit_extracted.db",         # 5,002
    "WBDG_Office_MEP_extracted.db",    # 5,678
    "HITOS_extracted.db",              # 1,000
    "HHS_Office_MEP_extracted.db",     # 3,964
    "HHS_Office_ARC_extracted.db",     # 2,745
    "Esplanades_extracted.db",         # 2,544
]
CLINIC_ROWS = 2  # 2 rows of clinics flanking CBD

# Suburbs: houses repeating in wide rows
SUBURB_BUILDINGS = [
    "Duplex_extracted.db",             # 1,169
    "SampleHouse_extracted.db",        # 65
    "AC90_Jasmin_extracted.db",        # 82
    "AC90_Niedriha_extracted.db",      # 136
    "AC9_HausGH_extracted.db",         # 256
    "Ifc4_FZKHaus_extracted.db",       # 98
    "Jesse_extracted.db",              # 676
    "VogelGesamt_extracted.db",        # 160
    "BimWhale_Basic_extracted.db",     # 153
    "Schependomlaan_extracted.db",     # 3,284
    "SampleCastle_extracted.db",       # 3,284
    "Molio_extracted.db",              # 2,791
    "BimWhale_Advanced_extracted.db",  # 2,176
    "SmileyWest_extracted.db",         # 531
    "HospitalAuckland_extracted.db",   # 442
    "BimWhale_Large_extracted.db",     # 174
    "BimWhale_Tall_extracted.db",      # 81
    "Ifc2x3_AC11Institute_extracted.db", # 986
]
SUBURB_ROWS = 45  # S175: 45 rows needed for ~1M with current building counts

BUILDINGS = CBD_BUILDINGS + CLINIC_BUILDINGS  # CBD + Clinics in main strip


def log(msg, f=None):
    line = f"[BUILD] {msg}"
    print(line)
    if f:
        f.write(line + "\n")
        f.flush()


def create_output(path):
    if path.exists():
        path.unlink()
    conn = sqlite3.connect(str(path))
    conn.executescript("""
        CREATE TABLE elements_meta (
            id INTEGER PRIMARY KEY,
            guid TEXT UNIQUE NOT NULL,
            discipline TEXT NOT NULL,
            ifc_class TEXT NOT NULL,
            element_name TEXT,
            element_type TEXT,
            storey TEXT,
            material_name TEXT,
            material_rgba TEXT,
            building TEXT
        );
        CREATE VIRTUAL TABLE elements_rtree USING rtree(
            id, minX, maxX, minY, maxY, minZ, maxZ
        );
        CREATE TABLE site_context (
            id INTEGER PRIMARY KEY,
            site_offset_x REAL DEFAULT 0,
            site_offset_y REAL DEFAULT 0,
            site_offset_z REAL DEFAULT 0
        );
        CREATE TABLE base_geometries (
            geometry_hash TEXT PRIMARY KEY
        );
        CREATE TABLE element_instances (
            guid TEXT PRIMARY KEY,
            geometry_hash TEXT,
            FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
        );
        CREATE TABLE element_transforms (
            guid TEXT PRIMARY KEY,
            center_x REAL, center_y REAL, center_z REAL,
            transform_source TEXT,
            rotation_x REAL DEFAULT 0,
            rotation_y REAL DEFAULT 0,
            rotation_z REAL DEFAULT 0
        );
    """)
    conn.execute("INSERT INTO site_context VALUES (1, 0, 0, 0)")
    conn.commit()
    return conn


def load_building(db_path):
    """Read all elements from one extracted.db."""
    conn = sqlite3.connect(str(db_path))

    meta = conn.execute("""
        SELECT guid, discipline, ifc_class, element_name, element_type,
               storey, material_name, material_rgba
        FROM elements_meta
    """).fetchall()

    rtree = conn.execute("""
        SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
    """).fetchall()

    # element_instances: guid → geometry_hash
    try:
        instances = conn.execute("SELECT guid, geometry_hash FROM element_instances").fetchall()
    except Exception:
        instances = []

    # base_geometries blobs
    try:
        geoms = conn.execute("SELECT geometry_hash, vertices, faces, vertex_count, face_count FROM base_geometries").fetchall()
    except Exception:
        geoms = []

    # element_transforms: original centre + rotation (local-space anchor for GI)
    transforms = {}
    try:
        # Check if rotation columns exist
        cols = [r[1] for r in conn.execute("PRAGMA table_info(element_transforms)").fetchall()]
        has_rot = 'rotation_x' in cols
        if has_rot:
            for row in conn.execute("SELECT guid, center_x, center_y, center_z, transform_source, rotation_x, rotation_y, rotation_z FROM element_transforms"):
                transforms[row[0]] = row[1:]  # (cx, cy, cz, source, rx, ry, rz)
        else:
            for row in conn.execute("SELECT guid, center_x, center_y, center_z, transform_source FROM element_transforms"):
                transforms[row[0]] = row[1:] + (0.0, 0.0, 0.0)  # (cx, cy, cz, source, 0, 0, 0)
    except Exception:
        pass

    bbox = conn.execute("SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), MIN(minZ) FROM elements_rtree").fetchone()
    conn.close()

    return meta, rtree, bbox, instances, geoms, transforms


def place_buildings(logf):
    """
    Arrange buildings side by side in a strip.
    Returns list of (label, meta, rtree, offset_x, offset_y).
    """
    placed = []
    cursor_x = 0.0
    GAP = 5.0    # 5m alley between buildings — packed tight

    all_geoms = {}   # geometry_hash → blob row (deduplicated across all buildings)

    for fname in BUILDINGS:
        db_path = INPUT_DIR / fname
        if not db_path.exists():
            log(f"SKIP {fname} — not found", logf)
            continue

        meta, rtree, bbox, instances, geoms, transforms = load_building(db_path)
        if not meta or not bbox or bbox[0] is None:
            log(f"SKIP {fname} — empty", logf)
            continue

        min_x, max_x, min_y, max_y, min_z = bbox
        width = max_x - min_x

        offset_x = cursor_x - min_x
        offset_y = -min_y

        label = fname.replace("_extracted.db", "").replace("_Extracted.db", "")
        # Deduplicate label if same building appears multiple times
        existing_labels = [p[0] for p in placed]
        if label in existing_labels:
            suffix = sum(1 for l in existing_labels if l.startswith(label)) + 1
            label = f"{label}_{suffix}"
        placed.append((label, meta, rtree, offset_x, offset_y, 0.0, instances, transforms))

        # Collect unique geometry blobs
        for g in geoms:
            if g[0] not in all_geoms:
                all_geoms[g[0]] = g

        log(f"  {label}: {len(meta):>6} elements  {len(geoms):>5} geom blobs  footprint {width:.0f}m wide  at x={cursor_x:.0f}m", logf)
        cursor_x += width + GAP

    return placed, cursor_x, all_geoms


def write_tile(out_conn, placed, tile_x_offset, tile_y_offset, tile_label, start_id, logf):
    """Write one tile of the precinct into out_conn."""
    meta_rows      = []
    rtree_rows     = []
    instance_rows  = []
    transform_rows = []
    current_id = start_id

    for (label, meta, rtree, bld_off_x, bld_off_y, bld_off_z, instances, transforms) in placed:
        guid_prefix = f"{tile_label}_{label}"
        rtree_by_guid    = {r[0]: r[1:] for r in rtree}
        instance_by_guid = {i[0]: i[1] for i in instances}

        for row in meta:
            guid_orig = row[0]
            guid_new  = f"{guid_prefix}_{guid_orig}"[:200]

            # building = tile + label (e.g. "T0_LTU_AHouse")
            building_name = f"{tile_label}_{label}"
            meta_rows.append((
                current_id, guid_new,
                row[1], row[2], row[3], row[4], row[5], row[6], row[7],
                building_name,
            ))

            if guid_orig in rtree_by_guid:
                rx = rtree_by_guid[guid_orig]
                wx0 = rx[0] + bld_off_x + tile_x_offset
                wx1 = rx[1] + bld_off_x + tile_x_offset
                wy0 = rx[2] + bld_off_y + tile_y_offset
                wy1 = rx[3] + bld_off_y + tile_y_offset
                wz0 = rx[4] + bld_off_z
                wz1 = rx[5] + bld_off_z
                rtree_rows.append((current_id, wx0, wx1, wy0, wy1, wz0, wz1))

                # element_transforms — use SOURCE centre + tile offset
                # (not recomputed from rtree, which misaligns GI local-space meshes)
                src = transforms.get(guid_orig)
                if src:
                    cx = src[0] + bld_off_x + tile_x_offset
                    cy = src[1] + bld_off_y + tile_y_offset
                    cz = src[2] + bld_off_z
                    tsrc = src[3]
                    rot_x, rot_y, rot_z = src[4], src[5], src[6]
                else:
                    # Fallback: bbox centre from rtree (legacy DBs without transforms)
                    cx = (wx0 + wx1) / 2
                    cy = (wy0 + wy1) / 2
                    cz = (wz0 + wz1) / 2
                    tsrc = 'bbox_centre'
                    rot_x = rot_y = rot_z = 0.0
                transform_rows.append((guid_new, cx, cy, cz, tsrc,
                                       rot_x, rot_y, rot_z))

            # element_instances — geometry_hash link
            if guid_orig in instance_by_guid:
                instance_rows.append((guid_new, instance_by_guid[guid_orig]))

            current_id += 1

    out_conn.execute("BEGIN")
    out_conn.executemany("INSERT INTO elements_meta VALUES (?,?,?,?,?,?,?,?,?,?)", meta_rows)
    out_conn.executemany("INSERT INTO elements_rtree VALUES (?,?,?,?,?,?,?)", rtree_rows)
    out_conn.executemany("INSERT OR IGNORE INTO element_instances VALUES (?,?)", instance_rows)
    out_conn.executemany("INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?,?,?,?)", transform_rows)
    out_conn.execute("COMMIT")

    log(f"  Tile {tile_label}: {len(meta_rows):,} elements  {len(instance_rows):,} instances  {len(transform_rows):,} transforms", logf)
    return current_id


def main():
    with open(LOG_FILE, "w") as logf:
        t_start = time.perf_counter()
        log("=== Building sandbox_1M.db from real extracted buildings ===", logf)

        # Load all buildings
        log("Loading buildings...", logf)
        placed, precinct_width, all_geoms = place_buildings(logf)

        total_unique = sum(len(p[1]) for p in placed)
        log(f"Precinct: {len(placed)} buildings, {total_unique:,} unique elements, {len(all_geoms):,} unique geom hashes, {precinct_width:.0f}m wide", logf)

        # Create output DB
        out_conn = create_output(OUT_DB)

        # Write geometry hashes only (BLOBs live in component_library.db)
        log(f"\nWriting {len(all_geoms):,} geometry hashes (BLOBs in component_library.db)...", logf)
        t_geom = time.perf_counter()
        out_conn.execute("BEGIN")
        out_conn.executemany(
            "INSERT OR IGNORE INTO base_geometries VALUES (?)",
            [(g[0],) for g in all_geoms.values()]
        )
        out_conn.execute("COMMIT")
        log(f"Geometry hashes written in {time.perf_counter()-t_geom:.1f}s", logf)

        # Write CBD tiles (main precinct)
        current_id = 1
        tile_idx = 0
        tile_spacing_x = precinct_width + TILE_GAP
        tile_spacing_y = 200.0 + TILE_GAP   # precinct depth + gap

        log(f"\nWriting {TILE_COLS * TILE_ROWS} CBD tiles...", logf)
        for row in range(TILE_ROWS):
            for col in range(TILE_COLS):
                tile_label = f"T{tile_idx}"
                tx = col * tile_spacing_x
                ty = row * tile_spacing_y
                log(f"Tile {tile_label} at ({tx:.0f}m, {ty:.0f}m)", logf)
                current_id = write_tile(out_conn, placed, tx, ty, tile_label, current_id, logf)
                tile_idx += 1

        # Write suburb rows (small houses, wide rows behind CBD)
        suburb_placed, suburb_width, suburb_geoms = [], 0.0, {}
        suburb_cursor_x = 0.0
        SUBURB_GAP = 8.0  # spacing between houses

        for fname in SUBURB_BUILDINGS:
            db_path = INPUT_DIR / fname
            if not db_path.exists():
                continue
            meta, rtree, bbox, instances, geoms, transforms = load_building(db_path)
            if not meta or not bbox or bbox[0] is None:
                continue
            min_x, max_x, min_y, max_y, min_z = bbox
            width = max_x - min_x
            offset_x = suburb_cursor_x - min_x
            offset_y = -min_y
            label = fname.replace("_extracted.db", "")
            suburb_placed.append((label, meta, rtree, offset_x, offset_y, 0.0, instances, transforms))
            for g in geoms:
                if g[0] not in all_geoms:
                    all_geoms[g[0]] = g
                    out_conn.execute("INSERT OR IGNORE INTO base_geometries VALUES (?)", (g[0],))
            suburb_cursor_x += width + SUBURB_GAP

        out_conn.commit()
        suburb_width = suburb_cursor_x
        if suburb_placed:
            TARGET = 1_050_000  # slightly over 1M to guarantee we hit it
            elements_so_far = current_id - 1
            suburb_per_tile = sum(len(p[1]) for p in suburb_placed)
            tiles_needed = max(1, (TARGET - elements_so_far) // suburb_per_tile + 1)
            # Spread tiles into a grid: 1 col deep, N rows
            suburb_cols = 1
            suburb_rows = tiles_needed
            log(f"\nWriting {suburb_rows} suburb rows ({len(suburb_placed)} houses × {suburb_cols} col, {suburb_per_tile:,}/row)...", logf)
            log(f"  CBD elements: {elements_so_far:,}, target: {TARGET:,}, suburb tiles: {tiles_needed}", logf)

            suburb_y_start = TILE_ROWS * tile_spacing_y  # behind CBD
            for srow in range(suburb_rows):
                for scol in range(suburb_cols):
                    tile_label = f"S{srow}_{scol}"
                    tx = scol * (suburb_width + SUBURB_GAP)
                    ty = suburb_y_start + srow * 50.0  # 50m between suburb rows
                    current_id = write_tile(out_conn, suburb_placed, tx, ty, tile_label, current_id, logf)
                    tile_idx += 1

        # Final count
        final = out_conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
        db_mb = OUT_DB.stat().st_size / (1024 * 1024)
        elapsed = time.perf_counter() - t_start

        log(f"\n=== DONE ===", logf)
        log(f"Total elements : {final:,}", logf)
        log(f"DB size        : {db_mb:.1f} MB", logf)
        log(f"Build time     : {elapsed:.1f}s", logf)
        log(f"Output         : {OUT_DB}", logf)

        out_conn.close()


if __name__ == "__main__":
    main()
