#!/usr/bin/env python3
"""
Phase 114: Extract Duplex IFC components into component_library.db.

Sources:
  - Duplex_Architecture.ifc: 61 furniture (beds, sofas, tables, cabinets)
  - Duplex_MEP.ifc: 75 flow terminals (sanitary fixtures, lights, appliances)

Follows import_ifc_furniture.py pattern:
  - Tessellate via ifcopenshell.geom.create_shape()
  - Center mesh at origin, store as float32/int32 BLOBs
  - INSERT OR IGNORE into component_geometries + component_definitions

Naming convention per docs/IFC_NAMING_CONVENTION.md:
  {Category}_{Variant}_{Room}_{Storey} → simplified to {Category}_{Variant}_{Size}
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

# Type IDs from component_types table
TYPE_FURNITURE = 9   # IfcFurniture / FURNITURE
TYPE_LIGHT = 2       # IfcLightFixture / LIGHT
TYPE_FIXTURE = 19    # IfcFlowTerminal / FIXTURE (sanitary)
TYPE_APPLIANCE = 12  # IfcElectricAppliance / APPLIANCE

# === Name mapping: IFC element/type name → canonical library name ===

# Architecture furniture (keyed by element Name prefix before first colon)
ARCH_NAME_MAP = {
    "M_Bed-Standard": ("Bed", TYPE_FURNITURE),
    "M_Sofa": ("Sofa", TYPE_FURNITURE),
    "M_Table-Coffee": ("Coffee_Table", TYPE_FURNITURE),
    "M_Base Cabinet-Double Door & 2 Drawer": ("Cabinet_Base_DoubleD", TYPE_FURNITURE),
    "M_Tall Cabinet-Single Door(2)": ("Cabinet_Tall_SingleD", TYPE_FURNITURE),
    "M_Upper Cabinet-Double Door-Wall": ("Cabinet_Upper_DoubleD", TYPE_FURNITURE),
    "M_Vanity Cabinet-Double Door Sink Unit": ("Cabinet_Vanity_DoubleD", TYPE_FURNITURE),
    "M_Counter Top": ("Counter_Top", TYPE_FURNITURE),
    "M_Counter Top w Sink Hole": ("Counter_Top_SinkHole", TYPE_FURNITURE),
}

# MEP flow terminals (keyed by element Name prefix before first colon)
MEP_NAME_MAP = {
    # Sanitary fixtures → FIXTURE (type 19)
    "M_Water Closet - Flush Tank": ("WC_FlushTank", TYPE_FIXTURE),
    "M_Lavatory - Oval": ("Lavatory_Oval", TYPE_FIXTURE),
    "M_Bath Tub": ("Bath_Tub", TYPE_FIXTURE),
    "M_Shower Stall - Rectangular": ("Shower_Rectangular", TYPE_FIXTURE),
    "M_Sink - Island - Single": ("Sink_Island_Single", TYPE_FIXTURE),

    # Light fixtures → LIGHT (type 2)
    "M_Pendant Light - Hemisphere": ("Light_Pendant_Hemisphere", TYPE_LIGHT),
    "M_Sconce Light - Sphere": ("Light_Sconce_Sphere", TYPE_LIGHT),
    "M_Lighting Switches": None,  # Skip switches - not geometry

    # Appliances → APPLIANCE (type 12)
    "M_Refrigerator": ("Appliance_Refrigerator", TYPE_APPLIANCE),
    "M_Range": ("Appliance_Range", TYPE_APPLIANCE),
    "M_Microwave": ("Appliance_Microwave", TYPE_APPLIANCE),

    # Electrical - skip (outlets, panels, phones → no meaningful geometry)
    "M_Duplex Receptacle": None,
    "M_Lighting and Appliance Panelboard - 208V MLO": None,
    "M_Fire Alarm Control Panel": None,
    "M_Telephone Terminal Board": None,
    "M_Telephone Outlet": None,
}

# Attachment face by category prefix
ATTACHMENT_MAP = {
    "Bed": "BOTTOM",
    "Sofa": "BOTTOM",
    "Coffee_Table": "BOTTOM",
    "Cabinet_Base": "BOTTOM",
    "Cabinet_Tall": "BOTTOM",
    "Cabinet_Upper": "SIDE",  # wall-mounted
    "Cabinet_Vanity": "BOTTOM",
    "Counter_Top": "BOTTOM",
    "WC": "BOTTOM",
    "Lavatory": "SIDE",  # wall-mounted typical
    "Bath": "BOTTOM",
    "Shower": "BOTTOM",
    "Sink": "BOTTOM",
    "Light_Pendant": "TOP",
    "Light_Sconce": "SIDE",
    "Appliance": "BOTTOM",
}


def geometry_hash(vertices_blob, faces_blob):
    """Compute 16-char hex hash matching existing library convention."""
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]


def tessellate_element(ifc_file, element, settings):
    """Tessellate an IFC element into centered triangle mesh."""
    try:
        shape = ifcopenshell.geom.create_shape(settings, element)
    except Exception:
        return None

    geo = shape.geometry
    verts = geo.verts
    faces = geo.faces

    if len(verts) < 9 or len(faces) < 3:
        return None

    v = np.array(verts, dtype=np.float64).reshape(-1, 3)
    f = np.array(faces, dtype=np.int32).reshape(-1, 3)

    # Center at origin
    center = (v.min(axis=0) + v.max(axis=0)) / 2.0
    v_centered = v - center

    local_min = v_centered.min(axis=0)
    local_max = v_centered.max(axis=0)

    v_f32 = v_centered.astype(np.float32)
    vertices_blob = v_f32.tobytes()
    faces_blob = f.tobytes()

    ghash = geometry_hash(vertices_blob, faces_blob)

    return {
        "vertices_blob": vertices_blob,
        "faces_blob": faces_blob,
        "geometry_hash": ghash,
        "vertex_count": len(v),
        "face_count": len(f),
        "local_min_x": float(local_min[0]),
        "local_max_x": float(local_max[0]),
        "local_min_y": float(local_min[1]),
        "local_max_y": float(local_max[1]),
        "local_min_z": float(local_min[2]),
        "local_max_z": float(local_max[2]),
    }


def get_element_prefix(element):
    """Get the family name prefix from element Name (before first colon)."""
    name = element.Name if hasattr(element, "Name") and element.Name else ""
    return name.split(":")[0]


def get_type_variant(element):
    """Get the type variant from IsDefinedBy (e.g. '1000mm', '1525 x 2007mm - Queen')."""
    try:
        for rel in element.IsDefinedBy:
            if rel.is_a("IfcRelDefinesByType"):
                return rel.RelatingType.Name
    except AttributeError:
        pass
    return None


def dims_mm(mesh):
    """Get width/depth/height in mm from mesh bounds."""
    w = (mesh["local_max_x"] - mesh["local_min_x"]) * 1000
    d = (mesh["local_max_y"] - mesh["local_min_y"]) * 1000
    h = (mesh["local_max_z"] - mesh["local_min_z"]) * 1000
    return w, d, h


def size_suffix_from_variant(canonical, variant, w_mm, d_mm, h_mm):
    """Derive a size suffix from the variant string or dimensions."""
    if canonical.startswith("Bed"):
        if "King" in (variant or ""):
            return "_King_1981x2032"
        elif "Queen" in (variant or ""):
            return "_Queen_1525x2007"
        else:
            return f"_{int(w_mm)}x{int(d_mm)}"

    if canonical.startswith("Coffee_Table"):
        return f"_{int(w_mm)}x{int(d_mm)}"

    if canonical.startswith("Sofa"):
        return f"_{int(w_mm)}"

    if canonical.startswith("Cabinet_") or canonical.startswith("Counter_"):
        # Use the numeric part from variant
        if variant:
            nums = "".join(c for c in variant.split()[0] if c.isdigit())
            if nums:
                return f"_{nums}"
        return f"_{int(w_mm)}"

    if canonical.startswith("WC"):
        return "_6Lpf"

    if canonical.startswith("Lavatory"):
        return f"_{int(w_mm)}x{int(d_mm)}"

    if canonical.startswith("Bath"):
        return f"_{int(w_mm)}x{int(d_mm)}"

    if canonical.startswith("Shower"):
        return f"_{int(w_mm)}x{int(d_mm)}"

    if canonical.startswith("Sink"):
        return f"_{int(w_mm)}x{int(d_mm)}"

    if canonical.startswith("Light_Pendant"):
        return "_150W"

    if canonical.startswith("Light_Sconce"):
        return "_100W"

    if canonical.startswith("Appliance"):
        return f"_{int(w_mm)}x{int(d_mm)}"

    return f"_{int(w_mm)}"


def get_attachment(canonical):
    """Determine attachment face from canonical name prefix."""
    for prefix, attach in ATTACHMENT_MAP.items():
        if canonical.startswith(prefix):
            return attach
    return "BOTTOM"


def insert_component(conn, type_id, full_name, mesh, attach, existing_hashes, seen_hashes):
    """Insert geometry + definition into library. Returns True if inserted."""
    ghash = mesh["geometry_hash"]

    if ghash in existing_hashes or ghash in seen_hashes:
        return False
    seen_hashes.add(ghash)

    w, d, h = dims_mm(mesh)

    # Determine orientation
    if attach == "SIDE":
        orientation = "WALL_MOUNT"
    elif attach == "TOP":
        orientation = "CEILING"
    elif h > max(w, d):
        orientation = "VERTICAL"
    else:
        orientation = "UPRIGHT"

    conn.execute(
        "INSERT OR IGNORE INTO component_geometries "
        "(geometry_hash, vertices, faces, vertex_count, face_count) "
        "VALUES (?, ?, ?, ?, ?)",
        (ghash, mesh["vertices_blob"], mesh["faces_blob"],
         mesh["vertex_count"], mesh["face_count"]),
    )

    conn.execute(
        "INSERT OR IGNORE INTO component_definitions "
        "(type_id, name, geometry_hash, "
        "local_min_x, local_max_x, local_min_y, local_max_y, "
        "local_min_z, local_max_z, "
        "attachment_face, up_axis, forward_axis, orientation, "
        "default_rotation, vertex_count, face_count, instance_count) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Z', 'Y', ?, 0, ?, ?, 1)",
        (type_id, full_name, ghash,
         mesh["local_min_x"], mesh["local_max_x"],
         mesh["local_min_y"], mesh["local_max_y"],
         mesh["local_min_z"], mesh["local_max_z"],
         attach, orientation,
         mesh["vertex_count"], mesh["face_count"]),
    )

    print(f"  + {full_name:40s}  {w:6.0f}x{d:6.0f}x{h:6.0f}mm  "
          f"{mesh['vertex_count']:5d}v/{mesh['face_count']:5d}f  {ghash}")
    return True


def process_architecture(ifc_path, conn, existing_hashes, seen_hashes):
    """Extract furniture from Architecture IFC."""
    print(f"\n{'='*60}")
    print(f"Architecture: {os.path.basename(ifc_path)}")
    print(f"{'='*60}")

    ifc_file = ifcopenshell.open(ifc_path)

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, False)
    settings.set(settings.WELD_VERTICES, True)

    furniture = ifc_file.by_type("IfcFurnishingElement")
    print(f"  Found {len(furniture)} furniture elements")

    imported = 0
    skipped = 0

    for elem in furniture:
        prefix = get_element_prefix(elem)
        variant = get_type_variant(elem)

        mapping = ARCH_NAME_MAP.get(prefix)
        if mapping is None:
            # Try fuzzy match
            for key, val in ARCH_NAME_MAP.items():
                if key.lower() in prefix.lower():
                    mapping = val
                    break
        if mapping is None:
            skipped += 1
            continue

        canonical, type_id = mapping

        mesh = tessellate_element(ifc_file, elem, settings)
        if mesh is None:
            skipped += 1
            continue

        w, d, h = dims_mm(mesh)
        suffix = size_suffix_from_variant(canonical, variant, w, d, h)
        full_name = f"{canonical}{suffix}"
        attach = get_attachment(canonical)

        if insert_component(conn, type_id, full_name, mesh, attach, existing_hashes, seen_hashes):
            imported += 1
        else:
            skipped += 1

    conn.commit()
    print(f"\n  Imported: {imported}, Skipped: {skipped}")
    return imported


def process_mep(ifc_path, conn, existing_hashes, seen_hashes):
    """Extract sanitary fixtures, lights, and appliances from MEP IFC."""
    print(f"\n{'='*60}")
    print(f"MEP: {os.path.basename(ifc_path)}")
    print(f"{'='*60}")

    ifc_file = ifcopenshell.open(ifc_path)

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, False)
    settings.set(settings.WELD_VERTICES, True)

    terminals = ifc_file.by_type("IfcFlowTerminal")
    print(f"  Found {len(terminals)} flow terminals")

    imported = 0
    skipped = 0

    for elem in terminals:
        prefix = get_element_prefix(elem)
        variant = get_type_variant(elem)

        mapping = MEP_NAME_MAP.get(prefix)
        if mapping is None:
            # Try fuzzy match
            for key, val in MEP_NAME_MAP.items():
                if key.lower() in prefix.lower():
                    mapping = val
                    break
        if mapping is None:
            skipped += 1
            continue

        # None means explicitly skip
        if mapping is None:
            skipped += 1
            continue

        canonical, type_id = mapping

        mesh = tessellate_element(ifc_file, elem, settings)
        if mesh is None:
            skipped += 1
            continue

        w, d, h = dims_mm(mesh)
        suffix = size_suffix_from_variant(canonical, variant, w, d, h)
        full_name = f"{canonical}{suffix}"
        attach = get_attachment(canonical)

        if insert_component(conn, type_id, full_name, mesh, attach, existing_hashes, seen_hashes):
            imported += 1
        else:
            skipped += 1

    conn.commit()
    print(f"\n  Imported: {imported}, Skipped: {skipped}")
    return imported


def main():
    arch_path = "archive/IFC_source_files/youshengCode_samples/Duplex_Architecture.ifc"
    mep_path = "archive/IFC_source_files/youshengCode_samples/Duplex_MEP.ifc"

    if not os.path.exists(arch_path):
        print(f"ERROR: {arch_path} not found")
        sys.exit(1)
    if not os.path.exists(mep_path):
        print(f"ERROR: {mep_path} not found")
        sys.exit(1)

    conn = sqlite3.connect(LIB_DB)

    # Load existing hashes
    cursor = conn.execute("SELECT geometry_hash FROM component_geometries")
    existing_hashes = {row[0] for row in cursor}
    seen_hashes = set()

    total = 0
    total += process_architecture(arch_path, conn, existing_hashes, seen_hashes)
    total += process_mep(mep_path, conn, existing_hashes, seen_hashes)

    # Summary
    print(f"\n{'='*60}")
    print(f"TOTAL IMPORTED: {total} unique component geometries")
    print(f"{'='*60}")

    # Show new duplex components
    cursor = conn.execute(
        "SELECT name, type_id FROM component_definitions "
        "WHERE name LIKE 'Bed_%' OR name LIKE 'WC_%' OR name LIKE 'Cabinet_%' "
        "OR name LIKE 'Light_Pendant_%' OR name LIKE 'Light_Sconce_%' "
        "OR name LIKE 'Lavatory_%' OR name LIKE 'Bath_%' OR name LIKE 'Shower_%' "
        "OR name LIKE 'Appliance_%' OR name LIKE 'Sink_Island_%' "
        "OR name LIKE 'Counter_Top_%' "
        "ORDER BY type_id, name")
    print("\nDuplex components in library:")
    for name, tid in cursor:
        print(f"  [{tid:2d}] {name}")

    conn.close()


if __name__ == "__main__":
    main()
