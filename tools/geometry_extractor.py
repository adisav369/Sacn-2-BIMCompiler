#!/usr/bin/env python3
"""
Geometry Extractor — Phase DE-3

Reads Rosetta Stone reference DBs and extracts element geometry (mesh blobs)
into the component_library.db for exact-fidelity emission.

Modes:
  (default)    Type-level: deduped by geometry_hash, maps (element_ref, ifc_class) → hash
  --instance   Instance-level: per-instance mapping (building_type, ifc_class, storey, ordinal) → hash
               Ordinals match ad_element_placement sort order: (ifc_class, storey, minX, minY, minZ)

Creates/populates:
  - ad_geometry_map: maps element/instance → geometry_hash
  - component_geometries: vertex/face/normal blobs
  - component_definitions: local bounds + metadata
  - component_types: type entries (if missing)

Usage:
  python3 tools/geometry_extractor.py [--ifc-class CLASS] [--dry-run] [--stone NAME]
  python3 tools/geometry_extractor.py --instance [--ifc-class CLASS] [--dry-run] [--stone NAME]

Example:
  python3 tools/geometry_extractor.py --instance
  python3 tools/geometry_extractor.py --ifc-class IfcRoof --dry-run
"""

import argparse
import hashlib
import sqlite3
import struct
import sys
from collections import defaultdict
from pathlib import Path

# Project root (one level up from tools/)
PROJECT_ROOT = Path(__file__).resolve().parent.parent
LIBRARY_DB = PROJECT_ROOT / "library" / "component_library.db"

# Rosetta Stone reference DBs and their building types
ROSETTA_STONES = [
    {
        "ref_db": PROJECT_ROOT / "reference" / "rosetta" / "Ifc4_SampleHouse_extracted.db",
        "building_type": "Ifc4_SampleHouse",
    },
    {
        "ref_db": PROJECT_ROOT / "reference" / "rosetta" / "Ifc2x3_Duplex_extracted.db",
        "building_type": "Ifc2x3_Duplex",
    },
    {
        "ref_db": PROJECT_ROOT / "reference" / "rosetta" / "Terminal_Extracted.db",
        "building_type": "SJTII_Terminal",
    },
]

# IFC class → (category, discipline) for component_types
IFC_CLASS_MAP = {
    "IfcRoof": ("ROOF", "ARC"),
    "IfcCurtainWall": ("CURTAIN_WALL", "ARC"),
    "IfcCovering": ("COVERING", "ARC"),
    "IfcRailing": ("RAILING", "ARC"),
    "IfcStairFlight": ("STAIR", "ARC"),
    "IfcFurnishingElement": ("FURNITURE", "ARC"),
    "IfcDoor": ("DOOR", "ARC"),
    "IfcWindow": ("WINDOW", "ARC"),
    "IfcWall": ("WALL", "ARC"),
    "IfcWallStandardCase": ("WALL", "ARC"),
    "IfcSlab": ("SLAB", "ARC"),
    "IfcColumn": ("COLUMN", "STR"),
    "IfcMember": ("MEMBER", "STR"),
    "IfcPlate": ("PLATE", "ARC"),
    "IfcFlowTerminal": ("FLOW_TERMINAL", "MEP"),
    "IfcFlowSegment": ("FLOW_SEGMENT", "MEP"),
    "IfcFlowFitting": ("FLOW_FITTING", "MEP"),
    "IfcSanitaryTerminal": ("SANITARY", "MEP"),
    "IfcLightFixture": ("LIGHT", "ELEC"),
    "IfcFireSuppressionTerminal": ("FIRE_SUPPRESSION", "FP"),
    "IfcAlarm": ("ALARM", "FP"),
    "IfcOpeningElement": ("OPENING", "ARC"),
    "IfcReinforcingBar": ("REBAR", "STR"),
    "IfcSensor": ("SENSOR", "FP"),
}


def _f32(val):
    """Round float64 to nearest float32 (R*Tree exact representation)."""
    return struct.unpack('f', struct.pack('f', val))[0]


def compute_local_bounds(vertices_blob, vertex_count):
    """Compute local bounding box from vertex blob (float32 or float64 XYZ triples)."""
    if not vertices_blob or vertex_count == 0:
        return None
    n_coords = vertex_count * 3
    # Auto-detect float32 vs float64 from blob size
    if len(vertices_blob) == n_coords * 4:
        fmt = f"<{n_coords}f"
    elif len(vertices_blob) == n_coords * 8:
        fmt = f"<{n_coords}d"
    else:
        print(f"  WARNING: vertex blob size {len(vertices_blob)} unexpected for {vertex_count} verts", file=sys.stderr)
        return None
    coords = struct.unpack(fmt, vertices_blob)
    xs = coords[0::3]
    ys = coords[1::3]
    zs = coords[2::3]
    return (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs))


def ensure_schema(lib_conn):
    """Ensure ad_geometry_map table exists with DE-3 schema (building_type, storey, ordinal)."""
    # Check if table has ordinal column (DE-3 schema)
    try:
        lib_conn.execute("SELECT ordinal FROM ad_geometry_map LIMIT 0")
    except sqlite3.OperationalError:
        # Table missing or old schema — apply DE-3 migration
        print("  Applying DE-3 schema migration...", file=sys.stderr)
        migration = PROJECT_ROOT / "migration" / "migration_phase_DE3_instance_geometry.sql"
        if migration.exists():
            lib_conn.executescript(migration.read_text())
        else:
            # Fallback: create fresh
            lib_conn.execute("""
                CREATE TABLE IF NOT EXISTS ad_geometry_map (
                    id INTEGER PRIMARY KEY,
                    building_type TEXT,
                    element_ref TEXT NOT NULL,
                    ifc_class TEXT NOT NULL,
                    storey TEXT,
                    ordinal INTEGER,
                    geometry_hash TEXT NOT NULL REFERENCES component_geometries(geometry_hash),
                    source TEXT
                )
            """)
            lib_conn.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_geom_instance
                    ON ad_geometry_map(building_type, ifc_class, storey, ordinal)
                    WHERE ordinal IS NOT NULL
            """)
            lib_conn.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_geom_type
                    ON ad_geometry_map(element_ref, ifc_class)
                    WHERE ordinal IS NULL
            """)
            lib_conn.commit()


def ensure_component_type(lib_conn, ifc_class):
    """Ensure component_types has an entry for ifc_class. Returns type_id."""
    row = lib_conn.execute(
        "SELECT id FROM component_types WHERE ifc_class = ?", (ifc_class,)
    ).fetchone()
    if row:
        return row[0]

    if ifc_class not in IFC_CLASS_MAP:
        print(f"  WARNING: Unknown IFC class {ifc_class}, using UNKNOWN/ARC", file=sys.stderr)
        category, discipline = "UNKNOWN", "ARC"
    else:
        category, discipline = IFC_CLASS_MAP[ifc_class]

    cur = lib_conn.execute(
        "INSERT INTO component_types (ifc_class, category, discipline) VALUES (?, ?, ?)",
        (ifc_class, category, discipline)
    )
    lib_conn.commit()
    return cur.lastrowid


def extract_geometries(ref_db, building_type, ifc_class_filter=None):
    """Extract unique geometries from a reference DB (type-level, deduped by hash).

    Returns list of dicts with keys:
        element_ref, ifc_class, geometry_hash, vertices, faces, normals,
        vertex_count, face_count, local_bounds
    """
    if not ref_db.exists():
        print(f"  SKIP: {ref_db} not found", file=sys.stderr)
        return []

    conn = sqlite3.connect(str(ref_db))
    query = """
        SELECT em.element_name, em.ifc_class, ei.geometry_hash,
               bg.vertices, bg.faces, bg.vertex_count, bg.face_count
        FROM elements_meta em
        JOIN element_instances ei ON em.guid = ei.guid
        JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
    """
    params = []
    if ifc_class_filter:
        query += " WHERE em.ifc_class = ?"
        params.append(ifc_class_filter)

    results = []
    seen_hashes = set()
    for row in conn.execute(query, params):
        element_name, ifc_class, geo_hash, vertices, faces, vertex_count, face_count = row
        if geo_hash in seen_hashes:
            continue
        seen_hashes.add(geo_hash)

        # Strip instance number suffix from element_name for element_ref
        # e.g. "Basic Roof:Roof_Flat-4Felt...:286419" → "Basic Roof:Roof_Flat-4Felt..."
        parts = element_name.rsplit(":", 1) if element_name else [element_name]
        if len(parts) == 2 and parts[1].isdigit():
            element_ref = parts[0]
        else:
            element_ref = element_name

        local_bounds = compute_local_bounds(vertices, vertex_count)

        results.append({
            "element_ref": element_ref,
            "ifc_class": ifc_class,
            "geometry_hash": geo_hash,
            "vertices": vertices,
            "faces": faces,
            "normals": None,  # Reference DBs don't store normals
            "vertex_count": vertex_count,
            "face_count": face_count,
            "local_bounds": local_bounds,
            "source": building_type,
        })

    conn.close()
    return results


def extract_instance_geometries(ref_db, building_type, ifc_class_filter=None):
    """Extract per-instance geometries from a reference DB.

    Unlike extract_geometries() which dedupes by hash, this returns one entry
    per element instance, sorted by (ifc_class, storey, minX, minY, minZ) to
    match the ad_element_placement ordinal assignment.

    Returns list of dicts with keys:
        building_type, element_ref, ifc_class, storey, ordinal,
        geometry_hash, vertices, faces, normals, vertex_count, face_count, local_bounds
    """
    if not ref_db.exists():
        print(f"  SKIP: {ref_db} not found", file=sys.stderr)
        return []

    conn = sqlite3.connect(str(ref_db))
    query = """
        SELECT em.element_name, em.ifc_class, em.storey,
               ei.geometry_hash,
               bg.vertices, bg.faces, bg.vertex_count, bg.face_count,
               r.minX, r.minY, r.minZ
        FROM elements_meta em
        JOIN element_instances ei ON em.guid = ei.guid
        JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
        JOIN elements_rtree r ON em.id = r.id
    """
    params = []
    if ifc_class_filter:
        query += " WHERE em.ifc_class = ?"
        params.append(ifc_class_filter)

    query += " ORDER BY em.ifc_class, em.storey, r.minX, r.minY, r.minZ"

    rows = conn.execute(query, params).fetchall()
    conn.close()

    # Group by (ifc_class, storey) and assign ordinals — same as placement_extractor.py
    groups = defaultdict(list)
    for row in rows:
        element_name, ifc_class, storey, geo_hash, vertices, faces, vertex_count, face_count, minX, minY, minZ = row
        # Match placement_extractor.py: NULL storey → "Unknown"
        storey = storey or "Unknown"
        key = (ifc_class, storey)
        groups[key].append({
            "element_name": element_name,
            "ifc_class": ifc_class,
            "storey": storey,
            "geometry_hash": geo_hash,
            "vertices": vertices,
            "faces": faces,
            "vertex_count": vertex_count,
            "face_count": face_count,
        })

    results = []
    for (ifc_class, storey), elems in sorted(groups.items(), key=lambda kv: (kv[0][0], kv[0][1] or "")):
        for ordinal, elem in enumerate(elems, 1):
            # Strip instance number suffix from element_name for element_ref
            element_name = elem["element_name"]
            parts = element_name.rsplit(":", 1) if element_name else [element_name]
            if len(parts) == 2 and parts[1].isdigit():
                element_ref = parts[0]
            else:
                element_ref = element_name

            local_bounds = compute_local_bounds(elem["vertices"], elem["vertex_count"])

            results.append({
                "building_type": building_type,
                "element_ref": element_ref,
                "ifc_class": ifc_class,
                "storey": storey,
                "ordinal": ordinal,
                "geometry_hash": elem["geometry_hash"],
                "vertices": elem["vertices"],
                "faces": elem["faces"],
                "normals": None,
                "vertex_count": elem["vertex_count"],
                "face_count": elem["face_count"],
                "local_bounds": local_bounds,
                "source": building_type,
            })

    return results


def apply_to_library(lib_conn, geometries, dry_run=False):
    """Insert extracted geometries into component_library.db (type-level mapping)."""
    stats = {"geometries": 0, "definitions": 0, "mappings": 0, "skipped": 0}

    for geo in geometries:
        geo_hash = geo["geometry_hash"]
        element_ref = geo["element_ref"]
        ifc_class = geo["ifc_class"]

        # Check if geometry already exists
        existing = lib_conn.execute(
            "SELECT geometry_hash FROM component_geometries WHERE geometry_hash = ?",
            (geo_hash,)
        ).fetchone()

        if existing:
            print(f"  EXISTS: geometry {geo_hash} ({geo['vertex_count']}v/{geo['face_count']}f)")
        else:
            print(f"  NEW: geometry {geo_hash} ({geo['vertex_count']}v/{geo['face_count']}f)")
            if not dry_run:
                lib_conn.execute(
                    "INSERT INTO component_geometries (geometry_hash, vertices, faces, normals, vertex_count, face_count) "
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    (geo_hash, geo["vertices"], geo["faces"], geo["normals"],
                     geo["vertex_count"], geo["face_count"])
                )
            stats["geometries"] += 1

        # Ensure component_type exists
        type_id = ensure_component_type(lib_conn, ifc_class) if not dry_run else 0

        # Check if component_definition exists
        existing_def = lib_conn.execute(
            "SELECT id FROM component_definitions WHERE geometry_hash = ?",
            (geo_hash,)
        ).fetchone()

        if existing_def:
            print(f"  EXISTS: definition for {geo_hash}")
        else:
            bounds = geo["local_bounds"]
            name = f"{ifc_class}_{geo['source']}_{geo_hash[:8]}"
            if bounds and not dry_run:
                lib_conn.execute(
                    """INSERT INTO component_definitions
                    (type_id, name, geometry_hash,
                     local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
                     attachment_face, up_axis, forward_axis,
                     vertex_count, face_count, instance_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CENTER', 'Z', 'Y', ?, ?, 1)""",
                    (type_id, name, geo_hash,
                     bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
                     geo["vertex_count"], geo["face_count"])
                )
                stats["definitions"] += 1
            elif not bounds:
                print(f"  SKIP: no local bounds for {geo_hash}", file=sys.stderr)
                stats["skipped"] += 1

        # Map element_ref → geometry_hash (type-level: ordinal=NULL)
        existing_map = lib_conn.execute(
            "SELECT id FROM ad_geometry_map WHERE element_ref = ? AND ifc_class = ? AND ordinal IS NULL",
            (element_ref, ifc_class)
        ).fetchone()

        if existing_map:
            print(f"  EXISTS: mapping {element_ref} → {geo_hash}")
        else:
            print(f"  MAP: {element_ref} [{ifc_class}] → {geo_hash} (source: {geo['source']})")
            if not dry_run:
                lib_conn.execute(
                    "INSERT INTO ad_geometry_map (element_ref, ifc_class, geometry_hash, source) "
                    "VALUES (?, ?, ?, ?)",
                    (element_ref, ifc_class, geo_hash, geo["source"])
                )
            stats["mappings"] += 1

    if not dry_run:
        lib_conn.commit()

    return stats


def apply_instance_to_library(lib_conn, instances, dry_run=False):
    """Insert per-instance geometry mappings into component_library.db."""
    stats = {"geometries": 0, "definitions": 0, "mappings": 0, "skipped": 0}

    for inst in instances:
        geo_hash = inst["geometry_hash"]
        ifc_class = inst["ifc_class"]
        building_type = inst["building_type"]
        storey = inst["storey"]
        ordinal = inst["ordinal"]
        element_ref = inst["element_ref"]

        # Ensure geometry blob exists (deduplicated by hash)
        existing = lib_conn.execute(
            "SELECT geometry_hash FROM component_geometries WHERE geometry_hash = ?",
            (geo_hash,)
        ).fetchone()

        if not existing:
            if not dry_run:
                lib_conn.execute(
                    "INSERT INTO component_geometries (geometry_hash, vertices, faces, normals, vertex_count, face_count) "
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    (geo_hash, inst["vertices"], inst["faces"], inst["normals"],
                     inst["vertex_count"], inst["face_count"])
                )
            stats["geometries"] += 1

        # Ensure component_type exists
        if not dry_run:
            ensure_component_type(lib_conn, ifc_class)

        # Ensure component_definition exists (deduplicated by hash)
        existing_def = lib_conn.execute(
            "SELECT id FROM component_definitions WHERE geometry_hash = ?",
            (geo_hash,)
        ).fetchone()

        if not existing_def:
            bounds = inst["local_bounds"]
            name = f"{ifc_class}_{building_type}_{geo_hash[:8]}"
            if bounds and not dry_run:
                type_id = ensure_component_type(lib_conn, ifc_class)
                lib_conn.execute(
                    """INSERT INTO component_definitions
                    (type_id, name, geometry_hash,
                     local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
                     attachment_face, up_axis, forward_axis,
                     vertex_count, face_count, instance_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CENTER', 'Z', 'Y', ?, ?, 1)""",
                    (type_id, name, geo_hash,
                     bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
                     inst["vertex_count"], inst["face_count"])
                )
                stats["definitions"] += 1
            elif not bounds:
                stats["skipped"] += 1

        # Instance-level mapping: (building_type, ifc_class, storey, ordinal) → geometry_hash
        existing_map = lib_conn.execute(
            "SELECT id FROM ad_geometry_map WHERE building_type = ? AND ifc_class = ? AND storey = ? AND ordinal = ?",
            (building_type, ifc_class, storey, ordinal)
        ).fetchone()

        if existing_map:
            # Update if hash changed
            lib_conn.execute(
                "UPDATE ad_geometry_map SET geometry_hash = ?, element_ref = ?, source = ? WHERE building_type = ? AND ifc_class = ? AND storey = ? AND ordinal = ?",
                (geo_hash, element_ref, building_type, building_type, ifc_class, storey, ordinal)
            )
        else:
            if not dry_run:
                lib_conn.execute(
                    "INSERT INTO ad_geometry_map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (building_type, element_ref, ifc_class, storey, ordinal, geo_hash, building_type)
                )
            stats["mappings"] += 1

    if not dry_run:
        lib_conn.commit()

    return stats


def main():
    parser = argparse.ArgumentParser(description="Extract geometry from Rosetta Stone reference DBs")
    parser.add_argument("--ifc-class", help="Filter to specific IFC class (e.g. IfcRoof)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be done without modifying DB")
    parser.add_argument("--stone", help="Process only this stone (e.g. Ifc4_SampleHouse)")
    parser.add_argument("--instance", action="store_true",
                        help="Per-instance mode: map (building_type, ifc_class, storey, ordinal) → geometry_hash")
    args = parser.parse_args()

    if not LIBRARY_DB.exists():
        print(f"ERROR: Library DB not found at {LIBRARY_DB}", file=sys.stderr)
        sys.exit(1)

    lib_conn = sqlite3.connect(str(LIBRARY_DB))
    ensure_schema(lib_conn)

    total_stats = {"geometries": 0, "definitions": 0, "mappings": 0, "skipped": 0}

    stones = ROSETTA_STONES
    if args.stone:
        stones = [s for s in stones if s["building_type"] == args.stone]
        if not stones:
            print(f"ERROR: Unknown stone '{args.stone}'", file=sys.stderr)
            sys.exit(1)

    mode_label = "INSTANCE" if args.instance else "TYPE-LEVEL"

    for stone in stones:
        ref_db = stone["ref_db"]
        building_type = stone["building_type"]
        print(f"\n=== {building_type} ({ref_db.name}) [{mode_label}] ===")

        if args.instance:
            instances = extract_instance_geometries(ref_db, building_type, args.ifc_class)
            if not instances:
                print("  No instances found")
                continue
            # Count unique hashes
            unique_hashes = len(set(i["geometry_hash"] for i in instances))
            print(f"  Found {len(instances)} instances ({unique_hashes} unique geometries)")
            stats = apply_instance_to_library(lib_conn, instances, args.dry_run)
        else:
            geometries = extract_geometries(ref_db, building_type, args.ifc_class)
            if not geometries:
                print("  No geometries found")
                continue
            print(f"  Found {len(geometries)} unique geometries")
            stats = apply_to_library(lib_conn, geometries, args.dry_run)

        for k in total_stats:
            total_stats[k] += stats[k]

    lib_conn.close()

    print(f"\n=== SUMMARY {'(DRY RUN) ' if args.dry_run else ''}[{mode_label}] ===")
    print(f"  New geometries:  {total_stats['geometries']}")
    print(f"  New definitions: {total_stats['definitions']}")
    print(f"  New mappings:    {total_stats['mappings']}")
    if total_stats["skipped"]:
        print(f"  Skipped:         {total_stats['skipped']}")


if __name__ == "__main__":
    main()
