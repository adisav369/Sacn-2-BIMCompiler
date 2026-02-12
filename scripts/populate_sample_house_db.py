#!/usr/bin/env python3
"""
Phase 118C: Populate Ifc4_SampleHouse_extracted.db from Ifc4_SampleHouse.ifc.

Extracts spatial structure and elements with bounding boxes into
reference/rosetta/Ifc4_SampleHouse_extracted.db — the ground truth
Rosetta Stone for single-house compiler validation.

Uses same extraction pipeline as populate_duplex_db.py.
"""

import os
import sys
import hashlib
import sqlite3
import numpy as np

import ifcopenshell
import ifcopenshell.geom

# Rosetta dictionary — maps IFC names to compiler categories
sys.path.insert(0, os.path.dirname(__file__))
from rosetta_dictionary import create_dictionary_schema, populate_dictionary, print_dictionary

DB_PATH = "reference/rosetta/Ifc4_SampleHouse_extracted.db"
IFC_PATH = "reference/residential/Ifc4_SampleHouse.ifc"


def geometry_hash(vertices_blob, faces_blob):
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]


def get_storey_for_element(element):
    """Walk containment to find storey name."""
    try:
        for rel in element.ContainedInStructure:
            container = rel.RelatingStructure
            if container.is_a("IfcBuildingStorey"):
                return container.Name
            if hasattr(container, "Decomposes"):
                for dec in container.Decomposes:
                    if dec.RelatingObject.is_a("IfcBuildingStorey"):
                        return dec.RelatingObject.Name
    except (AttributeError, TypeError):
        pass
    return "Unknown"


def get_space_for_element(element):
    """Find the IfcSpace containing this element (if any)."""
    try:
        for rel in element.ContainedInStructure:
            container = rel.RelatingStructure
            if container.is_a("IfcSpace"):
                return container
    except (AttributeError, TypeError):
        pass
    return None


def create_schema(conn):
    """Create the same schema as Stacked_Duplex.db."""
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS spatial_structure (
            guid TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            name TEXT,
            parent_guid TEXT,
            object_type TEXT,
            predefined_type TEXT
        );

        CREATE TABLE IF NOT EXISTS elements_meta (
            id INTEGER PRIMARY KEY,
            guid TEXT UNIQUE NOT NULL,
            discipline TEXT NOT NULL,
            ifc_class TEXT NOT NULL,
            element_name TEXT,
            element_type TEXT,
            storey TEXT
        );

        CREATE VIRTUAL TABLE IF NOT EXISTS elements_rtree USING rtree(
            id, minX, maxX, minY, maxY, minZ, maxZ
        );

        CREATE TABLE IF NOT EXISTS base_geometries (
            geometry_hash TEXT PRIMARY KEY,
            vertices BLOB,
            faces BLOB,
            vertex_count INTEGER,
            face_count INTEGER
        );

        CREATE TABLE IF NOT EXISTS element_instances (
            guid TEXT PRIMARY KEY,
            geometry_hash TEXT,
            FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
        );

        CREATE TABLE IF NOT EXISTS element_transforms (
            guid TEXT PRIMARY KEY,
            center_x REAL,
            center_y REAL,
            center_z REAL,
            transform_source TEXT
        );

        CREATE TABLE IF NOT EXISTS rel_contained_in_space (
            element_guid TEXT,
            space_guid TEXT,
            PRIMARY KEY (element_guid, space_guid)
        );
    """)
    conn.commit()


def populate_spatial_structure(ifc_file, conn):
    """Extract spatial hierarchy: Site > Building > Storeys > Spaces."""
    print("  Extracting spatial structure...")

    storeys = ifc_file.by_type("IfcBuildingStorey")
    spaces = ifc_file.by_type("IfcSpace")

    buildings = ifc_file.by_type("IfcBuilding")
    if buildings:
        b = buildings[0]
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
            "VALUES (?, 'IfcBuilding', ?, NULL)",
            (b.GlobalId, b.Name))

    for s in storeys:
        parent = buildings[0].GlobalId if buildings else None
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
            "VALUES (?, 'IfcBuildingStorey', ?, ?)",
            (s.GlobalId, s.Name, parent))

    for sp in spaces:
        parent_guid = None
        try:
            for rel in sp.Decomposes:
                parent = rel.RelatingObject
                if parent.is_a("IfcBuildingStorey"):
                    parent_guid = parent.GlobalId
                    break
        except (AttributeError, TypeError):
            pass

        obj_type = sp.ObjectType if hasattr(sp, "ObjectType") else None
        predef = None
        if hasattr(sp, "PredefinedType"):
            predef = sp.PredefinedType

        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure "
            "(guid, type, name, parent_guid, object_type, predefined_type) "
            "VALUES (?, 'IfcSpace', ?, ?, ?, ?)",
            (sp.GlobalId, sp.Name or sp.LongName, parent_guid, obj_type, predef))

    conn.commit()
    print(f"    Storeys: {len(storeys)}, Spaces: {len(spaces)}")
    return len(storeys), len(spaces)


def populate_elements(ifc_file, conn, settings):
    """Extract elements with metadata, bounding boxes, and geometry."""
    print("  Extracting elements...")

    row = conn.execute("SELECT COALESCE(MAX(id), 0) FROM elements_meta").fetchone()
    next_id = row[0] + 1

    existing = {r[0] for r in conn.execute("SELECT geometry_hash FROM base_geometries")}

    classes_to_extract = [
        "IfcFurnishingElement", "IfcFurniture",
        "IfcDoor", "IfcWindow",
        "IfcFlowTerminal", "IfcFlowSegment", "IfcFlowFitting",
        "IfcWall", "IfcWallStandardCase", "IfcSlab", "IfcColumn",
        "IfcStairFlight", "IfcRailing", "IfcRoof",
        "IfcPlate", "IfcMember", "IfcBeam",
    ]

    imported = 0
    failed = 0

    for cls in classes_to_extract:
        try:
            elements = ifc_file.by_type(cls)
        except RuntimeError:
            continue

        for elem in elements:
            guid = elem.GlobalId
            name = elem.Name if hasattr(elem, "Name") else None
            storey = get_storey_for_element(elem)

            if cls in ("IfcFlowTerminal", "IfcFlowSegment", "IfcFlowFitting"):
                discipline = "MEP"
            elif cls in ("IfcColumn", "IfcBeam", "IfcMember"):
                discipline = "STR"
            else:
                discipline = "ARC"

            elem_type = None
            try:
                for rel in elem.IsDefinedBy:
                    if rel.is_a("IfcRelDefinesByType"):
                        elem_type = rel.RelatingType.Name
                        break
            except (AttributeError, TypeError):
                pass

            try:
                shape = ifcopenshell.geom.create_shape(settings, elem)
                geo = shape.geometry
                verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)

                if len(verts) < 3 or len(faces) < 1:
                    raise ValueError("degenerate mesh")

                minX, minY, minZ = verts.min(axis=0)
                maxX, maxY, maxZ = verts.max(axis=0)

                center = (verts.min(axis=0) + verts.max(axis=0)) / 2.0
                v_centered = (verts - center).astype(np.float32)
                f_i32 = faces.astype(np.int32)

                vblob = v_centered.tobytes()
                fblob = f_i32.tobytes()
                ghash = geometry_hash(vblob, fblob)

                if ghash not in existing:
                    conn.execute(
                        "INSERT OR IGNORE INTO base_geometries "
                        "(geometry_hash, vertices, faces, vertex_count, face_count) "
                        "VALUES (?, ?, ?, ?, ?)",
                        (ghash, vblob, fblob, len(v_centered), len(f_i32)))
                    existing.add(ghash)

                eid = next_id
                next_id += 1

                conn.execute(
                    "INSERT OR IGNORE INTO elements_meta "
                    "(id, guid, discipline, ifc_class, element_name, element_type, storey) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (eid, guid, discipline, cls, name, elem_type, storey))

                conn.execute(
                    "INSERT OR IGNORE INTO elements_rtree "
                    "(id, minX, maxX, minY, maxY, minZ, maxZ) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (eid, float(minX), float(maxX), float(minY), float(maxY),
                     float(minZ), float(maxZ)))

                conn.execute(
                    "INSERT OR IGNORE INTO element_instances (guid, geometry_hash) "
                    "VALUES (?, ?)", (guid, ghash))

                conn.execute(
                    "INSERT OR IGNORE INTO element_transforms "
                    "(guid, center_x, center_y, center_z, transform_source) "
                    "VALUES (?, ?, ?, ?, 'ifc_extract')",
                    (guid, float(center[0]), float(center[1]), float(center[2])))

                space = get_space_for_element(elem)
                if space:
                    conn.execute(
                        "INSERT OR IGNORE INTO rel_contained_in_space "
                        "(element_guid, space_guid) VALUES (?, ?)",
                        (guid, space.GlobalId))

                imported += 1

            except Exception as e:
                failed += 1
                continue

    conn.commit()
    print(f"    Imported: {imported}, Failed: {failed}")
    return imported


def main():
    if not os.path.exists(IFC_PATH):
        print(f"ERROR: {IFC_PATH} not found")
        sys.exit(1)

    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)

    # Remove old DB if exists
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)

    print(f"\n{'='*60}")
    print("Extracting Ifc4_SampleHouse.ifc → Rosetta Stone DB")
    print(f"{'='*60}")

    create_schema(conn)
    create_dictionary_schema(conn)

    ifc_file = ifcopenshell.open(IFC_PATH)
    populate_spatial_structure(ifc_file, conn)

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)
    settings.set(settings.WELD_VERTICES, True)

    count = populate_elements(ifc_file, conn, settings)

    # Summary
    print(f"\n{'='*60}")
    print("Ifc4_SampleHouse_extracted.db Summary")
    print(f"{'='*60}")

    meta_count = conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    geo_count = conn.execute("SELECT COUNT(*) FROM base_geometries").fetchone()[0]
    space_count = conn.execute(
        "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcSpace'").fetchone()[0]
    storey_count = conn.execute(
        "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcBuildingStorey'").fetchone()[0]

    print(f"  Elements: {meta_count}")
    print(f"  Unique geometries: {geo_count}")
    print(f"  Storeys: {storey_count}")
    print(f"  Spaces: {space_count}")

    print("\n  By IFC class:")
    for cls, cnt in conn.execute(
            "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC"):
        print(f"    {cls}: {cnt}")

    # Populate Rosetta dictionary
    print("\n  Building element dictionary...")
    entries = populate_dictionary(conn)
    print_dictionary(conn)

    conn.close()
    print(f"\n  Written to: {DB_PATH}")


if __name__ == "__main__":
    main()
