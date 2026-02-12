#!/usr/bin/env python3
"""
Unified extraction tool for BIM Compiler.

Handles both Layer 1 (LOD400 → component_library.db) and
Layer 3 (Rosetta reference → reference/rosetta/*.db).

Sources: .ifc files (tessellates via ifcopenshell) or .db files (pre-tessellated).

Usage:
  # Layer 3: Extract Rosetta reference from IFC
  python3 tools/extract.py --to reference  source.ifc  -o reference/rosetta/out.db

  # Layer 3: Extract Rosetta reference from pre-tessellated DB (federation)
  python3 tools/extract.py --to reference  database/enhanced_federation_GI.db \
      -o reference/rosetta/terminal.db \
      --exclude IfcPlate,IfcOpeningElement

  # Layer 1: Extract LOD400 geometry to component library from DB
  python3 tools/extract.py --to library  database/enhanced_federation_GI.db

  # Layer 1: Extract LOD400 geometry to component library from IFC
  python3 tools/extract.py --to library  source.ifc --classes IfcFurniture,IfcDoor

  # Filter by discipline (DB sources only)
  python3 tools/extract.py --to reference  federation.db -o out.db --discipline ARC,STR
"""

import argparse
import hashlib
import os
import sqlite3
import struct
import sys

import numpy as np

# ---------------------------------------------------------------------------
# Geometry helpers
# ---------------------------------------------------------------------------

def geometry_hash(vertices_blob, faces_blob):
    """SHA256-based 16-char hash of centered geometry."""
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]


def parse_vertices_blob(blob):
    """Parse float32 vertex blob → numpy (N,3)."""
    n = len(blob) // 12
    verts = np.zeros((n, 3), dtype=np.float32)
    for i in range(n):
        x, y, z = struct.unpack('<fff', blob[i*12:(i+1)*12])
        verts[i] = [x, y, z]
    return verts


# ---------------------------------------------------------------------------
# IFC helpers (only imported when needed)
# ---------------------------------------------------------------------------

def get_storey_for_element(element):
    """Walk IFC containment to find storey name."""
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


# ---------------------------------------------------------------------------
# Reference schema (Layer 3: Rosetta)
# ---------------------------------------------------------------------------

REFERENCE_SCHEMA = """
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
"""

# Default IFC classes for reference extraction
REFERENCE_CLASSES = [
    "IfcFurnishingElement", "IfcFurniture",
    "IfcDoor", "IfcWindow",
    "IfcFlowTerminal", "IfcFlowSegment", "IfcFlowFitting",
    "IfcWall", "IfcWallStandardCase", "IfcSlab", "IfcColumn",
    "IfcStairFlight", "IfcRailing", "IfcRoof",
    "IfcPlate", "IfcMember", "IfcBeam",
    "IfcFireSuppressionTerminal", "IfcLightFixture",
    "IfcAirTerminal", "IfcPipeFitting", "IfcDuctFitting",
    "IfcPipeSegment", "IfcDuctSegment",
    "IfcValve", "IfcAlarm", "IfcElectricAppliance",
    "IfcSensor", "IfcController", "IfcFlowController",
    "IfcReinforcingBar", "IfcBuildingElementProxy",
    "IfcSanitaryTerminal", "IfcRampFlight",
    "IfcCovering",
]

# Discipline inference from IFC class
DISCIPLINE_MAP = {
    "IfcFlowTerminal": "MEP", "IfcFlowSegment": "MEP", "IfcFlowFitting": "MEP",
    "IfcPipeSegment": "MEP", "IfcPipeFitting": "MEP",
    "IfcDuctSegment": "MEP", "IfcDuctFitting": "MEP",
    "IfcValve": "MEP", "IfcFlowController": "MEP",
    "IfcSanitaryTerminal": "MEP",
    "IfcFireSuppressionTerminal": "FP", "IfcAlarm": "FP",
    "IfcSensor": "FP", "IfcController": "FP",
    "IfcLightFixture": "ELEC", "IfcElectricAppliance": "ELEC",
    "IfcAirTerminal": "ACMV",
    "IfcColumn": "STR", "IfcBeam": "STR", "IfcMember": "STR",
    "IfcReinforcingBar": "STR",
}


def infer_discipline(ifc_class):
    return DISCIPLINE_MAP.get(ifc_class, "ARC")


# ---------------------------------------------------------------------------
# Library schema (Layer 1: LOD400)
# ---------------------------------------------------------------------------

LIBRARY_SCHEMA = """
    CREATE TABLE IF NOT EXISTS component_types (
        id INTEGER PRIMARY KEY,
        ifc_class TEXT NOT NULL,
        category TEXT NOT NULL,
        discipline TEXT NOT NULL,
        UNIQUE(ifc_class, category)
    );
    CREATE TABLE IF NOT EXISTS component_definitions (
        id INTEGER PRIMARY KEY,
        type_id INTEGER REFERENCES component_types(id),
        name TEXT NOT NULL,
        geometry_hash TEXT NOT NULL,
        local_min_x REAL, local_max_x REAL,
        local_min_y REAL, local_max_y REAL,
        local_min_z REAL, local_max_z REAL,
        attachment_face TEXT NOT NULL,
        up_axis TEXT DEFAULT 'Z',
        forward_axis TEXT DEFAULT 'Y',
        orientation TEXT,
        default_rotation REAL DEFAULT 0,
        vertex_count INTEGER,
        face_count INTEGER,
        instance_count INTEGER DEFAULT 1,
        UNIQUE(name, geometry_hash)
    );
    CREATE TABLE IF NOT EXISTS component_geometries (
        geometry_hash TEXT PRIMARY KEY,
        vertices BLOB NOT NULL,
        faces BLOB NOT NULL,
        normals BLOB,
        vertex_count INTEGER NOT NULL,
        face_count INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS placement_rules (
        id INTEGER PRIMARY KEY,
        component_id INTEGER REFERENCES component_definitions(id),
        host_type TEXT,
        offset_from_host REAL,
        grid_spacing REAL,
        clearance_radius REAL
    );
"""

CATEGORY_MAP = {
    "IfcFireSuppressionTerminal": "SPRINKLER", "IfcLightFixture": "LIGHT",
    "IfcAirTerminal": "DIFFUSER", "IfcPipeFitting": "PIPE_FITTING",
    "IfcDuctFitting": "DUCT_FITTING", "IfcColumn": "COLUMN",
    "IfcBeam": "BEAM", "IfcMember": "MEMBER", "IfcFurniture": "FURNITURE",
    "IfcFurnishingElement": "FURNITURE", "IfcValve": "VALVE",
    "IfcAlarm": "ALARM", "IfcElectricAppliance": "APPLIANCE",
    "IfcSensor": "SENSOR", "IfcController": "CONTROLLER",
    "IfcDoor": "DOOR", "IfcWindow": "WINDOW", "IfcStairFlight": "STAIR",
    "IfcRailing": "RAILING", "IfcFlowTerminal": "FIXTURE",
    "IfcPipeSegment": "PIPE_SEGMENT", "IfcDuctSegment": "DUCT_SEGMENT",
    "IfcBuildingElementProxy": "PROXY",
}

ATTACHMENT_MAP = {
    "IfcFireSuppressionTerminal": "TOP", "IfcLightFixture": "TOP",
    "IfcAirTerminal": "TOP", "IfcColumn": "BOTTOM", "IfcBeam": "ENDS",
    "IfcMember": "ENDS", "IfcFurniture": "BOTTOM", "IfcFurnishingElement": "BOTTOM",
    "IfcDoor": "BOTTOM", "IfcWindow": "SIDE", "IfcStairFlight": "BOTTOM",
    "IfcRailing": "BOTTOM", "IfcAlarm": "TOP",
}


def detect_orientation(vertices, ifc_class):
    """Detect orientation from geometry shape."""
    x_span = vertices[:, 0].max() - vertices[:, 0].min()
    y_span = vertices[:, 1].max() - vertices[:, 1].min()
    z_span = vertices[:, 2].max() - vertices[:, 2].min()
    if ifc_class == "IfcColumn":
        return "VERTICAL"
    if ifc_class == "IfcBeam":
        return "HORIZONTAL"
    if z_span > max(x_span, y_span) * 1.5:
        return "VERTICAL"
    if z_span < min(x_span, y_span) * 0.5:
        return "HORIZONTAL"
    return "MIXED"


# ---------------------------------------------------------------------------
# Source: IFC file (tessellate via ifcopenshell)
# ---------------------------------------------------------------------------

def extract_from_ifc_to_reference(ifc_path, conn, classes, exclude):
    """Tessellate IFC → reference schema."""
    import ifcopenshell
    import ifcopenshell.geom

    ifc_file = ifcopenshell.open(ifc_path)

    # Spatial structure
    print("  Extracting spatial structure...")
    for b in ifc_file.by_type("IfcBuilding"):
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure (guid, type, name) VALUES (?, 'IfcBuilding', ?)",
            (b.GlobalId, b.Name))
    buildings = ifc_file.by_type("IfcBuilding")
    for s in ifc_file.by_type("IfcBuildingStorey"):
        parent = buildings[0].GlobalId if buildings else None
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
            "VALUES (?, 'IfcBuildingStorey', ?, ?)", (s.GlobalId, s.Name, parent))
    for sp in ifc_file.by_type("IfcSpace"):
        parent_guid = None
        try:
            for rel in sp.Decomposes:
                if rel.RelatingObject.is_a("IfcBuildingStorey"):
                    parent_guid = rel.RelatingObject.GlobalId
                    break
        except (AttributeError, TypeError):
            pass
        obj_type = getattr(sp, "ObjectType", None)
        predef = getattr(sp, "PredefinedType", None)
        conn.execute(
            "INSERT OR IGNORE INTO spatial_structure "
            "(guid, type, name, parent_guid, object_type, predefined_type) "
            "VALUES (?, 'IfcSpace', ?, ?, ?, ?)",
            (sp.GlobalId, sp.Name or sp.LongName, parent_guid, obj_type, predef))
    conn.commit()

    # Elements
    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)
    settings.set(settings.WELD_VERTICES, True)

    existing = {r[0] for r in conn.execute("SELECT geometry_hash FROM base_geometries")}
    next_id = (conn.execute("SELECT COALESCE(MAX(id),0) FROM elements_meta").fetchone()[0]) + 1
    imported = failed = 0

    active_classes = [c for c in classes if c not in exclude]
    for cls in active_classes:
        try:
            elements = ifc_file.by_type(cls)
        except RuntimeError:
            continue
        for elem in elements:
            try:
                shape = ifcopenshell.geom.create_shape(settings, elem)
                geo = shape.geometry
                verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)
                if len(verts) < 3 or len(faces) < 1:
                    raise ValueError("degenerate")

                minXYZ = verts.min(axis=0)
                maxXYZ = verts.max(axis=0)
                center = (minXYZ + maxXYZ) / 2.0
                v_centered = (verts - center).astype(np.float32)
                vblob = v_centered.tobytes()
                fblob = faces.astype(np.int32).tobytes()
                ghash = geometry_hash(vblob, fblob)

                if ghash not in existing:
                    conn.execute(
                        "INSERT OR IGNORE INTO base_geometries "
                        "(geometry_hash, vertices, faces, vertex_count, face_count) "
                        "VALUES (?,?,?,?,?)", (ghash, vblob, fblob, len(v_centered), len(faces)))
                    existing.add(ghash)

                eid = next_id; next_id += 1
                guid = elem.GlobalId
                name = getattr(elem, "Name", None)
                storey = get_storey_for_element(elem)
                discipline = infer_discipline(cls)
                elem_type = None
                try:
                    for rel in elem.IsDefinedBy:
                        if rel.is_a("IfcRelDefinesByType"):
                            elem_type = rel.RelatingType.Name; break
                except (AttributeError, TypeError):
                    pass

                conn.execute(
                    "INSERT OR IGNORE INTO elements_meta (id,guid,discipline,ifc_class,element_name,element_type,storey) "
                    "VALUES (?,?,?,?,?,?,?)", (eid, guid, discipline, cls, name, elem_type, storey))
                conn.execute(
                    "INSERT OR IGNORE INTO elements_rtree (id,minX,maxX,minY,maxY,minZ,maxZ) "
                    "VALUES (?,?,?,?,?,?,?)",
                    (eid, float(minXYZ[0]), float(maxXYZ[0]), float(minXYZ[1]), float(maxXYZ[1]),
                     float(minXYZ[2]), float(maxXYZ[2])))
                conn.execute("INSERT OR IGNORE INTO element_instances (guid,geometry_hash) VALUES (?,?)", (guid, ghash))
                conn.execute(
                    "INSERT OR IGNORE INTO element_transforms (guid,center_x,center_y,center_z,transform_source) "
                    "VALUES (?,?,?,?,'ifc_extract')", (guid, float(center[0]), float(center[1]), float(center[2])))
                space = get_space_for_element(elem)
                if space:
                    conn.execute("INSERT OR IGNORE INTO rel_contained_in_space VALUES (?,?)", (guid, space.GlobalId))
                imported += 1
            except Exception:
                failed += 1
    conn.commit()
    print(f"    Imported: {imported}, Failed: {failed}")
    return imported


def extract_from_ifc_to_library(ifc_path, lib_conn, classes, exclude):
    """Tessellate IFC → library schema."""
    import ifcopenshell
    import ifcopenshell.geom

    ifc_file = ifcopenshell.open(ifc_path)
    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, False)  # local coords for library
    settings.set(settings.WELD_VERTICES, True)

    active_classes = [c for c in classes if c not in exclude]
    imported = 0

    for cls in active_classes:
        category = CATEGORY_MAP.get(cls, cls.replace("Ifc", "").upper())
        discipline = infer_discipline(cls)
        lib_conn.execute(
            "INSERT OR IGNORE INTO component_types (ifc_class, category, discipline) VALUES (?,?,?)",
            (cls, category, discipline))
        type_id = lib_conn.execute(
            "SELECT id FROM component_types WHERE ifc_class=? AND category=?", (cls, category)).fetchone()[0]

        try:
            elements = ifc_file.by_type(cls)
        except RuntimeError:
            continue
        for elem in elements:
            try:
                shape = ifcopenshell.geom.create_shape(settings, elem)
                geo = shape.geometry
                verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)
                if len(verts) < 3 or len(faces) < 1:
                    continue
                center = (verts.min(axis=0) + verts.max(axis=0)) / 2.0
                v_centered = (verts - center).astype(np.float32)
                vblob = v_centered.tobytes()
                fblob = faces.astype(np.int32).tobytes()
                ghash = geometry_hash(vblob, fblob)

                local_min = v_centered.min(axis=0)
                local_max = v_centered.max(axis=0)
                name = getattr(elem, "Name", None) or f"{cls}_{ghash[:8]}"
                if ':' in name:
                    name = name.split(':')[0]
                attachment = ATTACHMENT_MAP.get(cls, "CENTER")
                orientation = detect_orientation(v_centered, cls)

                lib_conn.execute(
                    "INSERT OR IGNORE INTO component_geometries "
                    "(geometry_hash, vertices, faces, normals, vertex_count, face_count) "
                    "VALUES (?,?,?,NULL,?,?)", (ghash, vblob, fblob, len(v_centered), len(faces)))
                lib_conn.execute(
                    "INSERT OR IGNORE INTO component_definitions "
                    "(type_id, name, geometry_hash, "
                    "local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z, "
                    "attachment_face, orientation, vertex_count, face_count, instance_count) "
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1)",
                    (type_id, name, ghash,
                     float(local_min[0]), float(local_max[0]),
                     float(local_min[1]), float(local_max[1]),
                     float(local_min[2]), float(local_max[2]),
                     attachment, orientation, len(v_centered), len(faces)))
                imported += 1
            except Exception:
                continue
    lib_conn.commit()
    print(f"    Imported: {imported} unique components")
    return imported


# ---------------------------------------------------------------------------
# Source: pre-tessellated DB (federation or any extracted DB)
# ---------------------------------------------------------------------------

def extract_from_db_to_reference(src_path, conn, classes, exclude, disciplines):
    """Copy filtered elements from pre-tessellated DB → reference schema."""
    src = sqlite3.connect(src_path)

    # Build class filter
    active_classes = [c for c in classes if c not in exclude]
    placeholders = ",".join("?" * len(active_classes))

    # Build discipline filter
    disc_filter = ""
    disc_params = []
    if disciplines:
        disc_placeholders = ",".join("?" * len(disciplines))
        disc_filter = f" AND em.discipline IN ({disc_placeholders})"
        disc_params = list(disciplines)

    # Copy spatial structure — handle both reference schema (type/name/parent_guid)
    # and federation schema (building/storey/space per element guid)
    print("  Copying spatial structure...")
    src_cols = [r[1] for r in src.execute("PRAGMA table_info(spatial_structure)")]
    storey_map = {}

    if "type" in src_cols:
        # Reference-style: hierarchical (type, name, parent_guid)
        try:
            for row in src.execute("SELECT guid, type, name, parent_guid, object_type, predefined_type FROM spatial_structure"):
                conn.execute(
                    "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid, object_type, predefined_type) "
                    "VALUES (?,?,?,?,?,?)", row)
        except sqlite3.OperationalError:
            for row in src.execute("SELECT guid, type, name, parent_guid FROM spatial_structure"):
                conn.execute(
                    "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
                    "VALUES (?,?,?,?)", row)
        # Build storey map from hierarchy
        try:
            for guid, storey in src.execute(
                    "SELECT ss_elem.guid, ss_storey.name "
                    "FROM spatial_structure ss_elem "
                    "JOIN spatial_structure ss_storey ON ss_elem.parent_guid = ss_storey.guid "
                    "WHERE ss_storey.type = 'IfcBuildingStorey'"):
                storey_map[guid] = storey
        except sqlite3.OperationalError:
            pass
    elif "storey" in src_cols:
        # Federation-style: flat (guid → building, storey, space)
        # Reconstruct hierarchy: unique buildings + storeys → spatial_structure
        buildings = set()
        storeys = set()
        for row in src.execute("SELECT DISTINCT building FROM spatial_structure WHERE building IS NOT NULL"):
            buildings.add(row[0])
        for row in src.execute("SELECT DISTINCT storey FROM spatial_structure WHERE storey IS NOT NULL AND storey != ''"):
            storeys.add(row[0])
        bld_guid = "BUILDING_0"
        for b in buildings:
            conn.execute(
                "INSERT OR IGNORE INTO spatial_structure (guid, type, name) VALUES (?, 'IfcBuilding', ?)",
                (bld_guid, b))
        for s in sorted(storeys):
            s_guid = f"STOREY_{s.replace(' ', '_')}"
            conn.execute(
                "INSERT OR IGNORE INTO spatial_structure (guid, type, name, parent_guid) "
                "VALUES (?, 'IfcBuildingStorey', ?, ?)", (s_guid, s, bld_guid))
        # Build storey map from flat table
        for guid, storey in src.execute("SELECT guid, storey FROM spatial_structure WHERE storey IS NOT NULL AND storey != ''"):
            storey_map[guid] = storey
    conn.commit()

    # Copy elements with filters
    print("  Copying elements...")
    query = f"""
        SELECT em.id, em.guid, em.discipline, em.ifc_class,
               em.element_name, em.element_type, em.storey
        FROM elements_meta em
        WHERE em.ifc_class IN ({placeholders})
        {disc_filter}
        ORDER BY em.id
    """
    params = active_classes + disc_params

    existing_geo = set()
    next_id = 1
    imported = 0

    for _, guid, discipline, ifc_class, name, elem_type, storey in src.execute(query, params):
        # Get bounding box
        bbox = src.execute(
            "SELECT minX, maxX, minY, maxY, minZ, maxZ FROM elements_rtree WHERE id=?", (_,)).fetchone()
        if not bbox:
            continue

        # Get geometry hash
        gi = src.execute("SELECT geometry_hash FROM element_instances WHERE guid=?", (guid,)).fetchone()
        ghash = gi[0] if gi else None

        # Resolve storey from spatial_structure if NULL
        if not storey or storey == "":
            storey = storey_map.get(guid)

        eid = next_id; next_id += 1
        conn.execute(
            "INSERT OR IGNORE INTO elements_meta (id,guid,discipline,ifc_class,element_name,element_type,storey) "
            "VALUES (?,?,?,?,?,?,?)", (eid, guid, discipline, ifc_class, name, elem_type, storey))
        conn.execute(
            "INSERT OR IGNORE INTO elements_rtree (id,minX,maxX,minY,maxY,minZ,maxZ) "
            "VALUES (?,?,?,?,?,?,?)", (eid,) + bbox)

        if ghash:
            conn.execute("INSERT OR IGNORE INTO element_instances (guid,geometry_hash) VALUES (?,?)", (guid, ghash))
            if ghash not in existing_geo:
                geo = src.execute(
                    "SELECT vertices, faces, vertex_count, face_count FROM base_geometries WHERE geometry_hash=?",
                    (ghash,)).fetchone()
                if geo:
                    conn.execute(
                        "INSERT OR IGNORE INTO base_geometries (geometry_hash, vertices, faces, vertex_count, face_count) "
                        "VALUES (?,?,?,?,?)", (ghash,) + geo)
                existing_geo.add(ghash)

        # Transforms
        tx = src.execute(
            "SELECT center_x, center_y, center_z FROM element_transforms WHERE guid=?", (guid,)).fetchone()
        if tx:
            conn.execute(
                "INSERT OR IGNORE INTO element_transforms (guid,center_x,center_y,center_z,transform_source) "
                "VALUES (?,?,?,?,'db_copy')", (guid,) + tx)

        imported += 1

    conn.commit()
    src.close()
    print(f"    Imported: {imported} elements, {len(existing_geo)} unique geometries")
    return imported


def extract_from_db_to_library(src_path, lib_conn, classes, exclude, disciplines):
    """Copy filtered components from pre-tessellated DB → library schema."""
    src = sqlite3.connect(src_path)

    active_classes = [c for c in classes if c not in exclude]
    placeholders = ",".join("?" * len(active_classes))

    disc_filter = ""
    disc_params = []
    if disciplines:
        disc_placeholders = ",".join("?" * len(disciplines))
        disc_filter = f" AND em.discipline IN ({disc_placeholders})"
        disc_params = list(disciplines)

    query = f"""
        SELECT ei.geometry_hash, COUNT(*) as instance_count,
               MIN(em.element_type) as element_type,
               MIN(em.discipline) as discipline,
               MIN(em.element_name) as element_name,
               MIN(em.ifc_class) as ifc_class
        FROM elements_meta em
        JOIN element_instances ei ON em.guid = ei.guid
        WHERE em.ifc_class IN ({placeholders})
        {disc_filter}
        GROUP BY ei.geometry_hash
        ORDER BY instance_count DESC
    """
    params = active_classes + disc_params

    imported = 0
    for ghash, inst_count, elem_type, discipline, elem_name, ifc_class in src.execute(query, params):
        category = CATEGORY_MAP.get(ifc_class, ifc_class.replace("Ifc", "").upper())
        disc = infer_discipline(ifc_class)
        lib_conn.execute(
            "INSERT OR IGNORE INTO component_types (ifc_class, category, discipline) VALUES (?,?,?)",
            (ifc_class, category, disc))
        type_id = lib_conn.execute(
            "SELECT id FROM component_types WHERE ifc_class=? AND category=?", (ifc_class, category)).fetchone()[0]

        geo = src.execute(
            "SELECT vertices, faces, vertex_count, face_count FROM base_geometries WHERE geometry_hash=?",
            (ghash,)).fetchone()
        if not geo:
            continue
        vblob, fblob, vc, fc = geo
        normals = src.execute(
            "SELECT normals FROM base_geometries WHERE geometry_hash=?", (ghash,)).fetchone()
        normals_blob = normals[0] if normals and normals[0] else None

        vertices = parse_vertices_blob(vblob)
        local_min = vertices.min(axis=0)
        local_max = vertices.max(axis=0)

        name = elem_type or elem_name or f"{ifc_class}_{ghash[:8]}"
        if ':' in name:
            name = name.split(':')[0]
        attachment = ATTACHMENT_MAP.get(ifc_class, "CENTER")
        orientation = detect_orientation(vertices, ifc_class)

        lib_conn.execute(
            "INSERT OR IGNORE INTO component_geometries "
            "(geometry_hash, vertices, faces, normals, vertex_count, face_count) "
            "VALUES (?,?,?,?,?,?)", (ghash, vblob, fblob, normals_blob, vc, fc))
        lib_conn.execute(
            "INSERT OR REPLACE INTO component_definitions "
            "(type_id, name, geometry_hash, "
            "local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z, "
            "attachment_face, orientation, vertex_count, face_count, instance_count) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (type_id, name, ghash,
             float(local_min[0]), float(local_max[0]),
             float(local_min[1]), float(local_max[1]),
             float(local_min[2]), float(local_max[2]),
             attachment, orientation, vc, fc, inst_count))
        imported += 1

    lib_conn.commit()
    src.close()
    print(f"    Imported: {imported} unique components")
    return imported


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

def print_summary(conn, target):
    """Print extraction summary."""
    meta_count = conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]

    if target == "reference":
        geo_count = conn.execute("SELECT COUNT(*) FROM base_geometries").fetchone()[0]
        storey_count = conn.execute(
            "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcBuildingStorey'").fetchone()[0]
        space_count = conn.execute(
            "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcSpace'").fetchone()[0]
        print(f"\n  Elements:    {meta_count}")
        print(f"  Geometries:  {geo_count}")
        print(f"  Storeys:     {storey_count}")
        print(f"  Spaces:      {space_count}")
    else:
        comp_count = conn.execute("SELECT COUNT(*) FROM component_definitions").fetchone()[0]
        type_count = conn.execute("SELECT COUNT(*) FROM component_types").fetchone()[0]
        print(f"\n  Component defs: {comp_count}")
        print(f"  Type categories: {type_count}")

    print("\n  By IFC class:")
    table = "elements_meta" if target == "reference" else \
        "component_definitions cd JOIN component_types ct ON cd.type_id = ct.id"
    col = "ifc_class" if target == "reference" else "ct.ifc_class"
    for cls, cnt in conn.execute(
            f"SELECT {col}, COUNT(*) FROM {table} GROUP BY {col} ORDER BY COUNT(*) DESC"):
        print(f"    {cls:<35} {cnt:>6}")

    if target == "reference":
        # Discipline breakdown
        print("\n  By discipline:")
        for disc, cnt in conn.execute(
                "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC"):
            print(f"    {disc:<10} {cnt:>6}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Unified BIM extraction tool (Layer 1: LOD400 library, Layer 3: Rosetta reference)")
    parser.add_argument("source", help="Source .ifc file or .db file")
    parser.add_argument("--to", required=True, choices=["reference", "library"],
                        help="Target: 'reference' (Rosetta spatial) or 'library' (LOD400 component)")
    parser.add_argument("-o", "--output", help="Output path (required for --to reference)")
    parser.add_argument("--classes", help="Comma-separated IFC classes to include (default: all)")
    parser.add_argument("--exclude", default="", help="Comma-separated IFC classes to exclude")
    parser.add_argument("--discipline", help="Comma-separated disciplines to include (DB sources only)")
    args = parser.parse_args()

    source = args.source
    target = args.to
    is_ifc = source.lower().endswith(".ifc")
    is_db = source.lower().endswith(".db")

    if not is_ifc and not is_db:
        print("ERROR: source must be .ifc or .db file")
        sys.exit(1)
    if not os.path.exists(source):
        print(f"ERROR: {source} not found")
        sys.exit(1)

    # Parse filters
    classes = args.classes.split(",") if args.classes else REFERENCE_CLASSES
    exclude = set(args.exclude.split(",")) if args.exclude else set()
    disciplines = args.discipline.split(",") if args.discipline else None

    # Resolve output path
    if target == "reference":
        if not args.output:
            print("ERROR: --to reference requires -o <output.db>")
            sys.exit(1)
        out_path = args.output
        os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
        if os.path.exists(out_path):
            os.remove(out_path)
        conn = sqlite3.connect(out_path)
        conn.executescript(REFERENCE_SCHEMA)
        conn.commit()
    else:
        out_path = args.output or "library/component_library.db"
        conn = sqlite3.connect(out_path)
        conn.executescript(LIBRARY_SCHEMA)
        conn.commit()

    # Banner
    src_name = os.path.basename(source)
    print(f"\n{'='*60}")
    print(f"  EXTRACT: {src_name}")
    print(f"  Target:  {target} → {out_path}")
    if exclude:
        print(f"  Exclude: {', '.join(exclude)}")
    if disciplines:
        print(f"  Disciplines: {', '.join(disciplines)}")
    print(f"{'='*60}")

    # Dispatch
    if is_ifc and target == "reference":
        extract_from_ifc_to_reference(source, conn, classes, exclude)
    elif is_ifc and target == "library":
        extract_from_ifc_to_library(source, conn, classes, exclude)
    elif is_db and target == "reference":
        extract_from_db_to_reference(source, conn, classes, exclude, disciplines)
    elif is_db and target == "library":
        extract_from_db_to_library(source, conn, classes, exclude, disciplines)

    print_summary(conn, target)
    conn.close()

    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f"\n  Written to: {out_path} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    main()
