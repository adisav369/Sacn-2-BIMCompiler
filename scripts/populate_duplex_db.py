#!/usr/bin/env python3
"""
Phase 114: Populate Stacked_Duplex.db from Duplex IFC source files.

Parses both Architecture and MEP IFC files into the existing schema:
  - spatial_structure: storeys and spaces with room codes
  - elements_meta: all elements with IFC class, name, storey
  - elements_rtree: bounding boxes for spatial queries
  - base_geometries: tessellated geometry BLOBs

Makes Stacked_Duplex.db a queryable reference for Duplex validation witnesses.
"""

import os
import sys
import hashlib
import sqlite3
import numpy as np

import ifcopenshell
import ifcopenshell.geom

DB_PATH = "database/Stacked_Duplex.db"
ARCH_PATH = "archive/IFC_source_files/youshengCode_samples/Ifc2x3_Duplex_Architecture.ifc"
MEP_PATH = "archive/IFC_source_files/youshengCode_samples/Ifc2x3_Duplex_MEP.ifc"


def geometry_hash(vertices_blob, faces_blob):
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]


def get_storey_for_element(element):
    """Walk containment to find storey name."""
    try:
        for rel in element.ContainedInStructure:
            container = rel.RelatingStructure
            if container.is_a("IfcBuildingStorey"):
                return container.Name
            # Walk up
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


def populate_spatial_structure(ifc_file, conn):
    """Extract spatial hierarchy: Site > Building > Storeys > Spaces."""
    print("  Extracting spatial structure...")

    # Clear existing
    conn.execute("DELETE FROM spatial_structure WHERE guid != 'ROOT'")

    storeys = ifc_file.by_type("IfcBuildingStorey")
    spaces = ifc_file.by_type("IfcSpace")

    # Building
    buildings = ifc_file.by_type("IfcBuilding")
    if buildings:
        b = buildings[0]
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
            "VALUES (?, 'IfcBuilding', ?, NULL)",
            (b.GlobalId, b.Name))

    # Storeys
    for s in storeys:
        parent = buildings[0].GlobalId if buildings else None
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
            "VALUES (?, 'IfcBuildingStorey', ?, ?)",
            (s.GlobalId, s.Name, parent))

    # Spaces
    for sp in spaces:
        # Find parent storey
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


def populate_elements(ifc_file, conn, source_label, settings):
    """Extract elements with metadata, bounding boxes, and geometry."""
    print(f"  Extracting elements from {source_label}...")

    # Get max existing ID
    row = conn.execute("SELECT COALESCE(MAX(id), 0) FROM elements_meta").fetchone()
    next_id = row[0] + 1

    # Existing geometry hashes
    existing = {r[0] for r in conn.execute("SELECT geometry_hash FROM base_geometries")}

    # Define classes to extract
    classes_to_extract = [
        "IfcFurnishingElement", "IfcFurniture",
        "IfcDoor", "IfcWindow",
        "IfcFlowTerminal", "IfcFlowSegment", "IfcFlowFitting",
        "IfcWall", "IfcWallStandardCase", "IfcSlab", "IfcColumn",
        "IfcStairFlight", "IfcRailing",
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

            # Determine discipline
            if cls in ("IfcFlowTerminal", "IfcFlowSegment", "IfcFlowFitting"):
                discipline = "MEP"
            elif cls in ("IfcColumn", "IfcBeam", "IfcMember"):
                discipline = "STR"
            else:
                discipline = "ARC"

            # Get type name
            elem_type = None
            try:
                for rel in elem.IsDefinedBy:
                    if rel.is_a("IfcRelDefinesByType"):
                        elem_type = rel.RelatingType.Name
                        break
            except (AttributeError, TypeError):
                pass

            # Tessellate
            try:
                shape = ifcopenshell.geom.create_shape(settings, elem)
                geo = shape.geometry
                verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)

                if len(verts) < 3 or len(faces) < 1:
                    raise ValueError("degenerate mesh")

                # World-space bounds for rtree
                minX, minY, minZ = verts.min(axis=0)
                maxX, maxY, maxZ = verts.max(axis=0)

                # Center at origin for base geometry
                center = (verts.min(axis=0) + verts.max(axis=0)) / 2.0
                v_centered = (verts - center).astype(np.float32)
                f_i32 = faces.astype(np.int32)

                vblob = v_centered.tobytes()
                fblob = f_i32.tobytes()
                ghash = geometry_hash(vblob, fblob)

                # Insert base geometry if new
                if ghash not in existing:
                    conn.execute(
                        "INSERT OR IGNORE INTO base_geometries "
                        "(geometry_hash, vertices, faces, vertex_count, face_count) "
                        "VALUES (?, ?, ?, ?, ?)",
                        (ghash, vblob, fblob, len(v_centered), len(f_i32)))
                    existing.add(ghash)

                # Insert element
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

                # Spatial containment
                space = get_space_for_element(elem)
                if space:
                    conn.execute(
                        "INSERT OR IGNORE INTO rel_contained_in_space "
                        "(element_guid, space_guid) VALUES (?, ?)",
                        (guid, space.GlobalId))

                imported += 1

            except Exception:
                failed += 1
                continue

    conn.commit()
    print(f"    Imported: {imported}, Failed: {failed}")
    return imported


def main():
    if not os.path.exists(ARCH_PATH):
        print(f"ERROR: {ARCH_PATH} not found")
        sys.exit(1)
    if not os.path.exists(MEP_PATH):
        print(f"ERROR: {MEP_PATH} not found")
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)

    # Architecture
    print(f"\n{'='*60}")
    print("Processing Architecture IFC")
    print(f"{'='*60}")

    arch_ifc = ifcopenshell.open(ARCH_PATH)
    populate_spatial_structure(arch_ifc, conn)

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)
    settings.set(settings.WELD_VERTICES, True)

    arch_count = populate_elements(arch_ifc, conn, "Architecture", settings)

    # MEP
    print(f"\n{'='*60}")
    print("Processing MEP IFC")
    print(f"{'='*60}")

    mep_ifc = ifcopenshell.open(MEP_PATH)
    mep_count = populate_elements(mep_ifc, conn, "MEP", settings)

    # Summary
    print(f"\n{'='*60}")
    print("Stacked_Duplex.db Summary")
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

    # Element counts by class
    print("\n  By IFC class:")
    for cls, cnt in conn.execute(
            "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC"):
        print(f"    {cls}: {cnt}")

    conn.close()


if __name__ == "__main__":
    main()
