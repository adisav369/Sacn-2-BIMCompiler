# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Phase 109: Import furniture geometry from IFC sample files into component_library.db.

Reads IFC files, tessellates furniture geometry via ifcopenshell.geom,
centers meshes at origin, and inserts into component_geometries + component_definitions.

Usage:
    python3 scripts/import_ifc_furniture.py [ifc_file ...]

If no files given, processes the 3 known furniture-rich IFC sample files.
"""

import sys
import os
import struct
import hashlib
import sqlite3
import numpy as np

import ifcopenshell
import ifcopenshell.geom

LIB_DB = "library/component_library.db"
FURNITURE_TYPE_ID = 9  # IfcFurniture in component_types

# IFC furniture classes to extract
FURNITURE_CLASSES = {
    "IfcFurnishingElement",
    "IfcFurniture",
}

# Map IFC type names to our canonical names
NAME_MAP = {
    # Beds
    "M_Bed-Standard": "Bed",
    "Furniture_Bed_1": "Bed",
    # Sofas
    "M_Sofa": "Sofa",
    "Furniture_Couch_Viper": "Sofa",
    "Sofa - Ottoman": "Ottoman",
    # Tables
    "M_Table-Coffee": "Coffee_Table",
    "Furniture_Table_Coffee_1": "Coffee_Table",
    "Table-Dining 01 (M)": "Dining_Table",
    "Furniture_Table_Dining_w-Chairs_Rectangular": "Dining_Table_With_Chairs",
    "Side Table 2 (2)": "Side_Table",
    # Chairs
    "Dining Chair (3)": "Dining_Chair",
    "Chair - Dining": "Dining_Chair",
    "Bar Chair": "Bar_Chair",
    "Seating - Artemis - Lounge chair": "Lounge_Chair",
    "Furniture_Chair_Viper": "Lounge_Chair",
    # Desks
    "Furniture_Desk": "Desk",
    # Cabinets
    "M_Base Cabinet-Double Door & 2 Drawer": "Base_Cabinet",
    "M_Tall Cabinet-Single Door(2)": "Tall_Cabinet",
    "M_Upper Cabinet-Double Door-Wall": "Upper_Cabinet",
    "M_Vanity Cabinet-Double Door Sink Unit": "Vanity_Cabinet",
    "Cabinet 1": "Cabinet",
    # Kitchen
    "M_Counter Top": "Counter_Top",
    "M_Counter Top w Sink Hole": "Counter_Top_Sink",
    "4500_Kitchen Island": "Kitchen_Island",
    "4500_Kitchen Island_DW": "Kitchen_Island_DW",
    # Other
    "M_TV - Flat Screen": "TV_Flat_Screen",
    "Furniture_Piano": "Piano",
    "wardrobe1": "Wardrobe",
    "wardrobe2": "Wardrobe_Large",
    "hood enclosure": "Hood_Enclosure",
    "Seat - Single with Island": "Island_Seat",
}

# Attachment face by category
ATTACHMENT = {
    "Bed": "BOTTOM",
    "Sofa": "BOTTOM",
    "Ottoman": "BOTTOM",
    "Coffee_Table": "BOTTOM",
    "Dining_Table": "BOTTOM",
    "Dining_Table_With_Chairs": "BOTTOM",
    "Side_Table": "BOTTOM",
    "Dining_Chair": "BOTTOM",
    "Bar_Chair": "BOTTOM",
    "Lounge_Chair": "BOTTOM",
    "Desk": "BOTTOM",
    "Base_Cabinet": "BOTTOM",
    "Tall_Cabinet": "BOTTOM",
    "Upper_Cabinet": "SIDE",  # wall-mounted
    "Vanity_Cabinet": "BOTTOM",
    "Cabinet": "BOTTOM",
    "Counter_Top": "BOTTOM",
    "Counter_Top_Sink": "BOTTOM",
    "Kitchen_Island": "BOTTOM",
    "Kitchen_Island_DW": "BOTTOM",
    "TV_Flat_Screen": "SIDE",  # wall-mounted
    "Piano": "BOTTOM",
    "Wardrobe": "BOTTOM",
    "Wardrobe_Large": "BOTTOM",
    "Hood_Enclosure": "SIDE",
    "Island_Seat": "BOTTOM",
}


def geometry_hash(vertices_blob, faces_blob):
    """Compute 16-char hex hash matching existing library convention."""
    h = hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]
    return h


def tessellate_element(ifc_file, element, settings):
    """Tessellate an IFC element into centered triangle mesh."""
    try:
        shape = ifcopenshell.geom.create_shape(settings, element)
    except Exception as e:
        return None

    geo = shape.geometry
    verts = geo.verts  # flat list: x0,y0,z0, x1,y1,z1, ...
    faces = geo.faces  # flat list: v0,v1,v2, v0,v1,v2, ...

    if len(verts) < 9 or len(faces) < 3:
        return None

    # Reshape to arrays
    v = np.array(verts, dtype=np.float64).reshape(-1, 3)
    f = np.array(faces, dtype=np.int32).reshape(-1, 3)

    vertex_count = len(v)
    face_count = len(f)

    # Center at origin
    center = (v.min(axis=0) + v.max(axis=0)) / 2.0
    v_centered = v - center

    # Compute local bounds
    local_min = v_centered.min(axis=0)
    local_max = v_centered.max(axis=0)

    # Convert to blobs (float32 for vertices, int32 for faces)
    v_f32 = v_centered.astype(np.float32)
    vertices_blob = v_f32.tobytes()
    faces_blob = f.tobytes()

    ghash = geometry_hash(vertices_blob, faces_blob)

    return {
        "vertices_blob": vertices_blob,
        "faces_blob": faces_blob,
        "geometry_hash": ghash,
        "vertex_count": vertex_count,
        "face_count": face_count,
        "local_min_x": float(local_min[0]),
        "local_max_x": float(local_max[0]),
        "local_min_y": float(local_min[1]),
        "local_max_y": float(local_max[1]),
        "local_min_z": float(local_min[2]),
        "local_max_z": float(local_max[2]),
        "center": center.tolist(),
    }


def get_element_type_name(element):
    """Extract the type name from an IFC element."""
    # Try IsTypedBy (IFC4) or IsDefinedBy (IFC2x3)
    try:
        for rel in element.IsTypedBy:
            return rel.RelatingType.Name
    except AttributeError:
        pass

    try:
        for rel in element.IsDefinedBy:
            if rel.is_a("IfcRelDefinesByType"):
                return rel.RelatingType.Name
    except AttributeError:
        pass

    return element.Name


def get_dimensions_mm(mesh_data):
    """Get width/depth/height in mm from mesh bounds."""
    w = (mesh_data["local_max_x"] - mesh_data["local_min_x"]) * 1000
    d = (mesh_data["local_max_y"] - mesh_data["local_min_y"]) * 1000
    h = (mesh_data["local_max_z"] - mesh_data["local_min_z"]) * 1000
    return w, d, h


def classify_name(type_name, element_name):
    """Map IFC type/element name to canonical name."""
    # Try exact match on type name first
    if type_name in NAME_MAP:
        return NAME_MAP[type_name]

    # Try element name
    if element_name and element_name in NAME_MAP:
        return NAME_MAP[element_name]

    # Fuzzy match: check if any key is a substring
    for key, canonical in NAME_MAP.items():
        if key.lower() in (type_name or "").lower():
            return canonical
        if element_name and key.lower() in element_name.lower():
            return canonical

    # Fallback: clean up the name
    name = type_name or element_name or "Unknown"
    # Remove Revit family suffixes
    for suffix in [" (2)", " (3)", " (M)", " (1)"]:
        name = name.replace(suffix, "")
    return name.replace(" ", "_").replace("-", "_")


def process_ifc_file(ifc_path, conn):
    """Process one IFC file and insert furniture into library."""
    print(f"\n{'='*60}")
    print(f"Processing: {os.path.basename(ifc_path)}")
    print(f"{'='*60}")

    ifc_file = ifcopenshell.open(ifc_path)
    schema = ifc_file.schema

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, False)  # local coords
    settings.set(settings.WELD_VERTICES, True)

    # Collect all furniture elements
    furniture = []
    for cls in FURNITURE_CLASSES:
        try:
            furniture.extend(ifc_file.by_type(cls))
        except RuntimeError:
            pass

    print(f"  Found {len(furniture)} furniture elements (schema: {schema})")

    imported = 0
    skipped_dup = 0
    skipped_fail = 0
    seen_hashes = set()

    # Check existing hashes in DB
    cursor = conn.execute("SELECT geometry_hash FROM component_geometries")
    existing_hashes = {row[0] for row in cursor}

    for elem in furniture:
        type_name = get_element_type_name(elem)
        elem_name = elem.Name if hasattr(elem, "Name") else None
        canonical = classify_name(type_name, elem_name)

        mesh = tessellate_element(ifc_file, elem, settings)
        if mesh is None:
            skipped_fail += 1
            continue

        ghash = mesh["geometry_hash"]

        # Skip if already in DB or already seen this run
        if ghash in existing_hashes or ghash in seen_hashes:
            skipped_dup += 1
            continue
        seen_hashes.add(ghash)

        w, d, h = get_dimensions_mm(mesh)

        # Determine size variant suffix
        size_suffix = ""
        if canonical == "Bed":
            if w > 1700:
                size_suffix = "_King"
            elif w > 1400:
                size_suffix = "_Queen"
            else:
                size_suffix = "_Single"
        elif canonical == "Coffee_Table":
            if w > 800:
                size_suffix = "_Large"
            else:
                size_suffix = "_Small"

        full_name = f"{canonical}{size_suffix}"
        attach = ATTACHMENT.get(canonical, "BOTTOM")

        # Determine orientation
        if attach == "SIDE":
            orientation = "WALL_MOUNT"
        elif h > max(w, d):
            orientation = "VERTICAL"
        else:
            orientation = "UPRIGHT"

        # Insert geometry
        conn.execute(
            "INSERT OR IGNORE INTO component_geometries "
            "(geometry_hash, vertices, faces, vertex_count, face_count) "
            "VALUES (?, ?, ?, ?, ?)",
            (ghash, mesh["vertices_blob"], mesh["faces_blob"],
             mesh["vertex_count"], mesh["face_count"]),
        )

        # Insert definition
        conn.execute(
            "INSERT OR IGNORE INTO component_definitions "
            "(type_id, name, geometry_hash, "
            "local_min_x, local_max_x, local_min_y, local_max_y, "
            "local_min_z, local_max_z, "
            "attachment_face, up_axis, forward_axis, orientation, "
            "default_rotation, vertex_count, face_count, instance_count) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Z', 'Y', ?, 0, ?, ?, 1)",
            (FURNITURE_TYPE_ID, full_name, ghash,
             mesh["local_min_x"], mesh["local_max_x"],
             mesh["local_min_y"], mesh["local_max_y"],
             mesh["local_min_z"], mesh["local_max_z"],
             attach, orientation,
             mesh["vertex_count"], mesh["face_count"]),
        )

        print(f"  + {full_name:30s}  {w:6.0f}x{d:6.0f}x{h:6.0f}mm  "
              f"{mesh['vertex_count']:5d}v/{mesh['face_count']:5d}f  {ghash}")
        imported += 1

    conn.commit()
    print(f"\n  Imported: {imported}, Dup-skipped: {skipped_dup}, "
          f"Tessellation-failed: {skipped_fail}")
    return imported


def main():
    ifc_files = sys.argv[1:] if len(sys.argv) > 1 else [
        "/tmp/ifc-samples/IfcSampleFiles-main/Duplex_Architecture.ifc",
        "/tmp/ifc-samples/IfcSampleFiles-main/Ifc4_Revit_ARC.ifc",
        "/tmp/ifc-samples/IfcSampleFiles-main/SampleHouse.ifc",
    ]

    conn = sqlite3.connect(LIB_DB)
    total = 0

    for path in ifc_files:
        if not os.path.exists(path):
            print(f"SKIP: {path} not found")
            continue
        total += process_ifc_file(path, conn)

    # Summary
    print(f"\n{'='*60}")
    print(f"TOTAL IMPORTED: {total} unique furniture geometries")

    # Show what we have now
    cursor = conn.execute(
        "SELECT name, count(*) FROM component_definitions "
        "WHERE type_id = ? GROUP BY name ORDER BY name", (FURNITURE_TYPE_ID,))
    print(f"\nFurniture in library (type_id={FURNITURE_TYPE_ID}):")
    for name, cnt in cursor:
        print(f"  {name}: {cnt} variant(s)")

    conn.close()


if __name__ == "__main__":
    main()
