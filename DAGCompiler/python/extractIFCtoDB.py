#!/usr/bin/env python3
"""
DAGCompiler Reference DB Extractor — full extraction from IFC source.

Creates a complete reference DB from an IFC file in ONE pass:
  - Spatial structure (buildings, storeys, spaces)
  - Element geometry (tessellated meshes, bounding boxes, transforms)
  - Materials and colours (from IfcRelAssociatesMaterial + IfcSurfaceStyle)

Uses the unified reference_schema.sql (material columns from the start).
Replaces the need for separate extract.py + material_extractor.py steps.

Also supports populating I_Element_Extraction with material columns.

Usage:
  # Full extraction: IFC → new reference DB (geometry + materials)
  python3 DAGCompiler/python/extractIFCtoDB.py \
      --ifc DAGCompiler/lib/input/SampleHouse.ifc \
      -o DAGCompiler/lib/input/SampleHouse_extracted.db

  # S168: Full extraction with library mode (BLOBs → library, hashes → extracted)
  python3 DAGCompiler/python/extractIFCtoDB.py \
      --ifc DAGCompiler/lib/input/SampleHouse.ifc \
      -o DAGCompiler/lib/input/SampleHouse_extracted.db \
      --library library/component_library.db \
      --building-type Ifc4_SampleHouse

  # Populate I_Element_Extraction material columns from enriched reference DB
  python3 DAGCompiler/python/extract.py \
      --populate-placement \
      --ref DAGCompiler/lib/input/SampleHouse_extracted.db \
      --library library/component_library.db \
      --building-type SampleHouse

  # Enrich existing DB with materials only (no geometry re-extraction)
  python3 DAGCompiler/python/extract.py \
      --enrich \
      --ifc DAGCompiler/lib/input/SampleHouse.ifc \
      --ref DAGCompiler/lib/input/SampleHouse_extracted.db

  # Dry-run (report only)
  python3 DAGCompiler/python/extract.py \
      --ifc DAGCompiler/lib/input/SampleHouse.ifc \
      -o out.db --dry-run
"""

import argparse
import hashlib
import os
import sqlite3
import struct
import sys

import numpy as np


# ---------------------------------------------------------------------------
# Reference DB schema (unified — includes material columns)
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
    storey TEXT,
    material_name TEXT,
    material_rgba TEXT
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
    rotation_x REAL DEFAULT 0,
    rotation_y REAL DEFAULT 0,
    rotation_z REAL DEFAULT 0,
    transform_source TEXT
);
CREATE TABLE IF NOT EXISTS rel_contained_in_space (
    element_guid TEXT,
    space_guid TEXT,
    PRIMARY KEY (element_guid, space_guid)
);
CREATE TABLE IF NOT EXISTS rel_aggregates (
    parent_guid TEXT NOT NULL,
    child_guid TEXT NOT NULL,
    PRIMARY KEY (parent_guid, child_guid)
);
CREATE TABLE IF NOT EXISTS surface_styles (
    style_name TEXT PRIMARY KEY,
    surface_r REAL, surface_g REAL, surface_b REAL,
    transparency REAL DEFAULT 0.0,
    specular_r REAL, specular_g REAL, specular_b REAL,
    specular_ratio REAL,
    specular_exponent REAL,
    reflectance_method TEXT DEFAULT 'NOTDEFINED',
    side TEXT DEFAULT 'BOTH',
    source TEXT
);
CREATE TABLE IF NOT EXISTS material_layers (
    layer_set_name TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    material_name TEXT,
    thickness_m REAL,
    is_ventilated INTEGER DEFAULT 0,
    PRIMARY KEY (layer_set_name, sequence)
);
"""


# Default IFC classes for reference extraction
# Non-geometric IFC types — no 3D body, skip tessellation entirely.
# All other IfcProduct subclasses with a Representation are extracted.
NON_GEOMETRIC_CLASSES = {
    "IfcDistributionPort",    # logical connection points, no mesh
    "IfcGrid",                # reference grid lines, 2D only
    "IfcGridAxis",
    "IfcSpace",               # spatial container, no body
    "IfcZone",
    "IfcSpatialZone",
    "IfcAnnotation",          # 2D annotations
    "IfcVirtualElement",      # abstract boundary
    "IfcRelSpaceBoundary",
    "IfcBuildingStorey",      # spatial container
    "IfcBuilding",
    "IfcSite",
    "IfcProject",
    "IfcExternalSpatialElement",
}

# REFERENCE_CLASSES kept for backwards compatibility with callers that pass classes=
# The extraction loop now defaults to all IfcProduct — see extract_reference().
REFERENCE_CLASSES = None  # Deprecated: extraction now uses blacklist, not whitelist

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
    "IfcCableSegment": "ELEC", "IfcCableFitting": "ELEC",
    "IfcCableCarrierSegment": "ELEC", "IfcCableCarrierFitting": "ELEC",
    "IfcDistributionControlElement": "ELEC", "IfcElectricDistributionBoard": "ELEC",
    "IfcElectricMotor": "ELEC", "IfcElectricGenerator": "ELEC",
    "IfcAirTerminal": "ACMV",
    "IfcPump": "MECH", "IfcFan": "MECH", "IfcUnitaryEquipment": "MECH",
    "IfcHeatExchanger": "MECH", "IfcBoiler": "MECH", "IfcChiller": "MECH",
    "IfcCoolingTower": "MECH", "IfcCompressor": "MECH", "IfcMotorConnection": "MECH",
    "IfcSprayTerminal": "FP",
    "IfcColumn": "STR", "IfcBeam": "STR", "IfcMember": "STR",
    "IfcPlate": "STR", "IfcReinforcingBar": "STR",
    # IFC4X3 Infrastructure disciplines
    "IfcGeographicElement": "GIS",
    "IfcRoad": "ROAD", "IfcRoadPart": "ROAD", "IfcKerb": "ROAD", "IfcPavement": "ROAD",
    "IfcRailway": "RAIL", "IfcRailwayPart": "RAIL", "IfcRail": "RAIL", "IfcTrackElement": "RAIL",
    "IfcBridge": "BRIDGE", "IfcBridgePart": "BRIDGE",
    "IfcTunnel": "TUNNEL", "IfcTunnelPart": "TUNNEL",
    "IfcMarineFacility": "MARINE", "IfcMarinePart": "MARINE",
    "IfcAlignment": "INFRA", "IfcLinearElement": "INFRA", "IfcLinearPositioningElement": "INFRA",
    "IfcEarthworksCut": "INFRA", "IfcEarthworksFill": "INFRA", "IfcEarthworksObstacle": "INFRA",
    "IfcCivilElement": "INFRA", "IfcFacility": "INFRA", "IfcFacilityPart": "INFRA",
}


def infer_discipline(ifc_class):
    return DISCIPLINE_MAP.get(ifc_class, "ARC")


# ---------------------------------------------------------------------------
# Geometry helpers
# ---------------------------------------------------------------------------

def geometry_hash(vertices_blob, faces_blob):
    """SHA256-based 16-char hash of centered geometry."""
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]


def boolean_depth(item, depth=0, _visited=None):
    """Count max IfcBooleanResult chain depth in a representation item."""
    if _visited is None:
        _visited = set()
    eid = item.id()
    if eid in _visited:
        return depth
    _visited.add(eid)
    if item.is_a("IfcBooleanResult"):
        return max(
            boolean_depth(item.FirstOperand, depth + 1, _visited),
            boolean_depth(item.SecondOperand, depth + 1, _visited),
        )
    if item.is_a("IfcMappedItem"):
        try:
            for sub in item.MappingSource.MappedRepresentation.Items:
                d = boolean_depth(sub, depth, _visited)
                if d > depth:
                    depth = d
        except Exception:
            pass
    return depth


def element_boolean_depth(elem):
    """Return max boolean chain depth across all representations of an element."""
    try:
        if not elem.Representation:
            return 0
        max_d = 0
        for rep in elem.Representation.Representations:
            for item in rep.Items:
                d = boolean_depth(item)
                if d > max_d:
                    max_d = d
        return max_d
    except Exception:
        return 0


def bbox_from_placement(elem):
    """Fallback: derive a minimal 1×1×1 box from the element's placement origin."""
    import ifcopenshell.util.placement as ifcplace
    try:
        m = ifcplace.get_local_placement(elem.ObjectPlacement)
        cx, cy, cz = float(m[0][3]), float(m[1][3]), float(m[2][3])
    except Exception:
        cx = cy = cz = 0.0
    # Unit box centred on placement origin
    half = 0.5
    verts = np.array([
        [-half, -half, -half], [half, -half, -half],
        [half,  half, -half], [-half,  half, -half],
        [-half, -half,  half], [half, -half,  half],
        [half,  half,  half], [-half,  half,  half],
    ], dtype=np.float32)
    faces = np.array([
        [0,1,2],[0,2,3],[4,5,6],[4,6,7],
        [0,1,5],[0,5,4],[2,3,7],[2,7,6],
        [1,2,6],[1,6,5],[0,3,7],[0,7,4],
    ], dtype=np.int32)
    center = np.array([cx, cy, cz], dtype=np.float64)
    return verts.tobytes(), faces.tobytes(), center, \
           np.array([cx-half, cy-half, cz-half]), \
           np.array([cx+half, cy+half, cz+half])


# ---------------------------------------------------------------------------
# IFC spatial helpers
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
# IFC Material extraction
# ---------------------------------------------------------------------------

def get_material_for_element(element):
    """Extract material name from IFC element via IfcRelAssociatesMaterial."""
    try:
        for rel in element.HasAssociations:
            if rel.is_a("IfcRelAssociatesMaterial"):
                mat = rel.RelatingMaterial
                if mat.is_a("IfcMaterial"):
                    return mat.Name
                elif mat.is_a("IfcMaterialLayerSetUsage"):
                    ls = mat.ForLayerSet
                    if ls and ls.LayerSetName:
                        return ls.LayerSetName
                    if ls and ls.MaterialLayers:
                        for layer in ls.MaterialLayers:
                            if layer.Material:
                                return layer.Material.Name
                elif mat.is_a("IfcMaterialLayerSet"):
                    if mat.LayerSetName:
                        return mat.LayerSetName
                    if mat.MaterialLayers:
                        for layer in mat.MaterialLayers:
                            if layer.Material:
                                return layer.Material.Name
                elif mat.is_a("IfcMaterialList"):
                    if mat.Materials:
                        return mat.Materials[0].Name
                elif mat.is_a("IfcMaterialConstituentSet"):
                    if hasattr(mat, 'Name') and mat.Name:
                        return mat.Name
                    if mat.MaterialConstituents:
                        for c in mat.MaterialConstituents:
                            if c.Material:
                                return c.Material.Name
                elif mat.is_a("IfcMaterialProfileSetUsage"):
                    ps = mat.ForProfileSet
                    if ps and hasattr(ps, 'Name') and ps.Name:
                        return ps.Name
                    if ps and ps.MaterialProfiles:
                        for mp in ps.MaterialProfiles:
                            if mp.Material:
                                return mp.Material.Name
    except (AttributeError, TypeError):
        pass
    return None


def get_colour_for_element(element):
    """Extract RGBA colour from IFC element via representation style chain.

    Walks: IfcProduct.Representation -> IfcRepresentationItem
           -> IfcStyledItem -> IfcPresentationStyleAssignment/IfcSurfaceStyle
           -> IfcSurfaceStyleRendering -> IfcColourRgb + Transparency
    """
    try:
        if not element.Representation:
            return None
        for rep in element.Representation.Representations:
            for item in rep.Items:
                colour = _extract_colour_from_item(item)
                if colour:
                    return colour
                # Check mapped items
                if item.is_a("IfcMappedItem"):
                    mapped_rep = item.MappingSource.MappedRepresentation
                    for sub_item in mapped_rep.Items:
                        colour = _extract_colour_from_item(sub_item)
                        if colour:
                            return colour
    except (AttributeError, TypeError):
        pass
    return None


def _extract_colour_from_item(item):
    try:
        if not hasattr(item, 'StyledByItem') or not item.StyledByItem:
            return None
        for styled in item.StyledByItem:
            if not styled.is_a("IfcStyledItem"):
                continue
            for style_select in styled.Styles:
                colour = _extract_colour_from_style(style_select)
                if colour:
                    return colour
    except (AttributeError, TypeError):
        pass
    return None


def _extract_colour_from_style(style_select):
    try:
        if style_select.is_a("IfcSurfaceStyle"):
            return _extract_colour_from_surface_style(style_select)
        if style_select.is_a("IfcPresentationStyleAssignment"):
            for s in style_select.Styles:
                if s.is_a("IfcSurfaceStyle"):
                    colour = _extract_colour_from_surface_style(s)
                    if colour:
                        return colour
    except (AttributeError, TypeError):
        pass
    return None


def _extract_colour_from_surface_style(surface_style):
    try:
        for ss in surface_style.Styles:
            if ss.is_a("IfcSurfaceStyleRendering") or ss.is_a("IfcSurfaceStyleShading"):
                colour = ss.SurfaceColour
                if colour and colour.is_a("IfcColourRgb"):
                    r = float(colour.Red)
                    g = float(colour.Green)
                    b = float(colour.Blue)
                    transparency = 0.0
                    if hasattr(ss, 'Transparency') and ss.Transparency is not None:
                        transparency = float(ss.Transparency)
                    alpha = 1.0 - transparency
                    return f"{r:.3f},{g:.3f},{b:.3f},{alpha:.3f}"
    except (AttributeError, TypeError):
        pass
    return None


# ---------------------------------------------------------------------------
# Rich surface style + material layer extraction
# ---------------------------------------------------------------------------

def extract_surface_styles(ifc_file, source_tag="EXTRACTED"):
    """Extract all IfcSurfaceStyle → surface_styles rows.

    Returns list of dicts ready for DB insertion.
    One row per unique style name (instanced, not per-element).
    """
    rows = []
    seen = set()
    for style in ifc_file.by_type('IfcSurfaceStyle'):
        name = style.Name
        if not name or name in seen:
            continue
        for ss in style.Styles:
            if ss.is_a('IfcSurfaceStyleRendering'):
                sc = ss.SurfaceColour
                if not sc or not sc.is_a('IfcColourRgb'):
                    continue
                row = {
                    'style_name': name,
                    'surface_r': float(sc.Red),
                    'surface_g': float(sc.Green),
                    'surface_b': float(sc.Blue),
                    'transparency': float(ss.Transparency) if ss.Transparency is not None else 0.0,
                    'reflectance_method': ss.ReflectanceMethod if ss.ReflectanceMethod else 'NOTDEFINED',
                    'side': style.Side if style.Side else 'BOTH',
                    'source': source_tag,
                    'specular_r': None, 'specular_g': None, 'specular_b': None,
                    'specular_ratio': None, 'specular_exponent': None,
                }
                # Specular colour: either IfcNormalisedRatioMeasure (ratio) or IfcColourRgb
                spec = ss.SpecularColour
                if spec is not None:
                    if hasattr(spec, 'wrappedValue'):
                        row['specular_ratio'] = float(spec.wrappedValue)
                    elif hasattr(spec, 'Red'):
                        row['specular_r'] = float(spec.Red)
                        row['specular_g'] = float(spec.Green)
                        row['specular_b'] = float(spec.Blue)
                # Specular highlight (exponent)
                highlight = ss.SpecularHighlight
                if highlight is not None:
                    if hasattr(highlight, 'wrappedValue'):
                        row['specular_exponent'] = float(highlight.wrappedValue)
                rows.append(row)
                seen.add(name)
                break  # one rendering per style name
            elif ss.is_a('IfcSurfaceStyleShading') and name not in seen:
                # Shading-only style (no specular data)
                sc = ss.SurfaceColour
                if not sc or not sc.is_a('IfcColourRgb'):
                    continue
                row = {
                    'style_name': name,
                    'surface_r': float(sc.Red),
                    'surface_g': float(sc.Green),
                    'surface_b': float(sc.Blue),
                    'transparency': float(ss.Transparency) if hasattr(ss, 'Transparency') and ss.Transparency is not None else 0.0,
                    'reflectance_method': 'NOTDEFINED',
                    'side': style.Side if style.Side else 'BOTH',
                    'source': source_tag,
                    'specular_r': None, 'specular_g': None, 'specular_b': None,
                    'specular_ratio': None, 'specular_exponent': None,
                }
                rows.append(row)
                seen.add(name)
                break
    return rows


def extract_material_layers(ifc_file):
    """Extract all IfcMaterialLayerSet → material_layers rows.

    Returns list of dicts ready for DB insertion.
    """
    rows = []
    seen = set()
    for layer_set in ifc_file.by_type('IfcMaterialLayerSet'):
        name = layer_set.LayerSetName
        if not name or name in seen:
            continue
        seen.add(name)
        for seq, layer in enumerate(layer_set.MaterialLayers):
            mat_name = layer.Material.Name if layer.Material else None
            thickness = float(layer.LayerThickness) if layer.LayerThickness is not None else None
            is_vent = 1 if (hasattr(layer, 'IsVentilated') and layer.IsVentilated) else 0
            rows.append({
                'layer_set_name': name,
                'sequence': seq,
                'material_name': mat_name,
                'thickness_m': thickness,
                'is_ventilated': is_vent,
            })
    return rows


def write_surface_styles(conn, styles):
    """Write surface_styles rows to DB."""
    for s in styles:
        conn.execute(
            "INSERT OR REPLACE INTO surface_styles "
            "(style_name, surface_r, surface_g, surface_b, transparency, "
            "specular_r, specular_g, specular_b, specular_ratio, specular_exponent, "
            "reflectance_method, side, source) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (s['style_name'], s['surface_r'], s['surface_g'], s['surface_b'],
             s['transparency'], s['specular_r'], s['specular_g'], s['specular_b'],
             s['specular_ratio'], s['specular_exponent'],
             s['reflectance_method'], s['side'], s['source']))
    conn.commit()


def write_material_layers(conn, layers):
    """Write material_layers rows to DB."""
    for l in layers:
        conn.execute(
            "INSERT OR REPLACE INTO material_layers "
            "(layer_set_name, sequence, material_name, thickness_m, is_ventilated) "
            "VALUES (?,?,?,?,?)",
            (l['layer_set_name'], l['sequence'], l['material_name'],
             l['thickness_m'], l['is_ventilated']))
    conn.commit()


# ---------------------------------------------------------------------------
# Full extraction: IFC -> reference DB (geometry + materials in one pass)
# ---------------------------------------------------------------------------

def _infer_product_type(ifc_class):
    """Map IFC class to product_type for M_Product."""
    TYPE_MAP = {
        "IfcDoor": "DOOR", "IfcWindow": "WINDOW",
        "IfcWall": "WALL", "IfcWallStandardCase": "WALL",
        "IfcSlab": "SLAB", "IfcColumn": "COLUMN", "IfcBeam": "BEAM",
        "IfcRoof": "ROOF", "IfcStair": "STAIR", "IfcStairFlight": "STAIR",
        "IfcRailing": "RAILING", "IfcCovering": "ELEMENT",
        "IfcFurnishingElement": "ELEMENT", "IfcFurniture": "ELEMENT",
        "IfcBuildingElementProxy": "ELEMENT",
    }
    return TYPE_MAP.get(ifc_class, "ELEMENT")


def _product_id_from_dims(ifc_class, width, depth, height):
    """Derive M_Product.product_id from class + dimensions (mm)."""
    ptype = _infer_product_type(ifc_class)
    short = ptype
    if ifc_class.startswith("IfcDoor"):
        short = "DOOR_INT" if width < 1.0 else "DOOR_EXT"
    w_mm = int(round(width * 1000))
    d_mm = int(round(depth * 1000))
    h_mm = int(round(height * 1000))
    return f"{short}_{w_mm}x{d_mm}x{h_mm}"


def _infer_attachment_face(ifc_class):
    """Derive attachment_face from IFC class — how the element tacks to its host."""
    FACE_MAP = {
        "IfcDoor": "SIDE", "IfcWindow": "SIDE",           # tack to wall face
        "IfcWall": "FLOOR", "IfcWallStandardCase": "FLOOR",
        "IfcSlab": "FLOOR", "IfcRoof": "FLOOR",
        "IfcColumn": "FLOOR", "IfcBeam": "FLOOR",
        "IfcStair": "FLOOR", "IfcStairFlight": "FLOOR",
        "IfcRailing": "FLOOR",
        "IfcFurnishingElement": "FLOOR", "IfcFurniture": "FLOOR",
        "IfcCovering": "TOP",                              # ceiling covering
        "IfcFlowTerminal": "TOP", "IfcAirTerminal": "TOP", # sprinkler, diffuser
        "IfcLightFixture": "TOP",                          # pendant light
        "IfcSanitaryTerminal": "FLOOR",
        "IfcPipeSegment": "CENTER", "IfcPipeFitting": "CENTER",
        "IfcDuctSegment": "CENTER", "IfcDuctFitting": "CENTER",
    }
    return FACE_MAP.get(ifc_class, "CENTER")


def _open_library(library_path):
    """Open component_library.db and ensure target tables exist."""
    lib = sqlite3.connect(library_path, timeout=30)
    lib.execute("PRAGMA journal_mode=WAL")  # allow concurrent reads
    lib.execute("PRAGMA busy_timeout=30000")  # wait up to 30s for lock
    # component_geometries — must already exist (sacred DB, append-only)
    # I_Geometry_Map, M_Product — must already exist
    # Verify tables exist
    tables = {r[0] for r in lib.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}
    for t in ("component_geometries", "I_Geometry_Map", "M_Product"):
        if t not in tables:
            raise RuntimeError(
                f"component_library.db missing table '{t}' — "
                f"run schema migration first")
    return lib


def extract_reference(ifc_path, output_path, classes=None, exclude=None,
                      dry_run=False, library_path=None, building_type=None):
    """Full extraction from IFC to reference DB with geometry + materials.

    Extracts ALL IfcProduct subclasses that have a Representation, except
    known non-geometric types (NON_GEOMETRIC_CLASSES blacklist).
    The `classes` parameter is kept for backwards compatibility but ignored
    when None — the blacklist approach is always used by default.

    S168: When library_path is set, mesh BLOBs go to component_library.db
    and _extracted.db/base_geometries stores hashes only (NULL BLOBs).
    """
    import ifcopenshell
    import ifcopenshell.geom

    if exclude is None:
        exclude = []
    # Merge caller-supplied exclude with the global blacklist
    skip_classes = NON_GEOMETRIC_CLASSES | set(exclude)

    print(f"\n{'='*70}")
    print(f"§EXTRACT START")
    print(f"{'='*70}")
    print(f"  §IFC        {ifc_path}")
    ifc_file = ifcopenshell.open(ifc_path)
    ifc_size_mb = os.path.getsize(ifc_path) / (1024 * 1024)
    print(f"  §IFC        {ifc_size_mb:.1f} MB, schema {ifc_file.schema}")

    # S168: Derive building_type from IFC filename if not provided
    if building_type is None:
        building_type = os.path.splitext(os.path.basename(ifc_path))[0]
        # Strip common suffixes
        for suffix in ("_extracted", "_merged", "_federated"):
            building_type = building_type.replace(suffix, "")

    print(f"  §BUILDING   {building_type}")
    print(f"  §OUTPUT     {output_path}")
    print(f"  §COORDS     LOCAL (USE_WORLD_COORDS=False, tack point = IFC origin)")

    # S168: Open component library for mesh BLOB storage
    lib_conn = None
    if library_path and not dry_run:
        lib_conn = _open_library(library_path)
        print(f"  §LIBRARY    {library_path} (WAL mode, BLOBs here, hashes in output)")
    else:
        print(f"  §LIBRARY    none (legacy mode — BLOBs in output DB)")

    if dry_run:
        print("  [DRY RUN] Scanning elements...")
    else:
        # Create fresh DB with unified schema
        if os.path.exists(output_path):
            os.remove(output_path)
        conn = sqlite3.connect(output_path)
        conn.executescript(REFERENCE_SCHEMA)

    # Spatial structure
    print("  Extracting spatial structure...")
    if not dry_run:
        buildings = ifc_file.by_type("IfcBuilding")
        for b in buildings:
            conn.execute(
                "INSERT OR IGNORE INTO spatial_structure (guid, type, name) "
                "VALUES (?, 'IfcBuilding', ?)", (b.GlobalId, b.Name))
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

    # Geometry settings — S169: LOCAL coords for canonical mesh deduplication.
    # World placement comes from shape.transformation matrix.
    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, False)
    settings.set(settings.WELD_VERTICES, True)

    # Tier 2: no boolean operations — fast fallback for complex CSG elements
    settings_no_bool = ifcopenshell.geom.settings()
    settings_no_bool.set(settings_no_bool.USE_WORLD_COORDS, False)
    settings_no_bool.set(settings_no_bool.WELD_VERTICES, True)
    settings_no_bool.set(settings_no_bool.DISABLE_BOOLEAN_RESULT, True)

    # Boolean depth threshold — elements above this skip full tessellation
    BOOL_DEPTH_THRESHOLD = 3

    existing_hashes = set()
    next_id = 1
    imported = failed = simplified = bbox_fallback = 0
    mat_found = rgba_found = 0
    lib_geo_new = lib_igm_new = lib_prod_new = 0  # S168 counters
    ordinal_counter = {}  # S168: (ifc_class, storey) → next ordinal

    # Collect all unique concrete IFC classes present in the file,
    # excluding known non-geometric types. This replaces the old whitelist.
    all_classes = set()
    for elem in ifc_file.by_type("IfcProduct"):
        c = elem.is_a()
        if c not in skip_classes and elem.Representation is not None:
            all_classes.add(c)

    for cls in sorted(all_classes):
        try:
            elements = [e for e in ifc_file.by_type(cls)
                        if e.is_a() == cls and e.Representation is not None]
        except RuntimeError:
            continue
        for elem in elements:
            try:
                # --- Tier selection: pre-scan boolean depth ---
                use_bbox = False
                depth = element_boolean_depth(elem)
                if depth > BOOL_DEPTH_THRESHOLD:
                    # Tier 2: skip full CSG, tessellate base shape only
                    try:
                        shape = ifcopenshell.geom.create_shape(settings_no_bool, elem)
                        simplified += 1
                    except Exception:
                        use_bbox = True
                else:
                    # Tier 1: full tessellation
                    try:
                        shape = ifcopenshell.geom.create_shape(settings, elem)
                    except Exception:
                        # Tier 2 on failure
                        try:
                            shape = ifcopenshell.geom.create_shape(settings_no_bool, elem)
                            simplified += 1
                        except Exception:
                            use_bbox = True

                # S169: rotation from transform matrix (0 for bbox fallback)
                rot_x = rot_y = rot_z = 0.0

                if use_bbox:
                    # Tier 3: placement bbox
                    vblob, fblob, center, minXYZ, maxXYZ = bbox_from_placement(elem)
                    bbox_fallback += 1
                else:
                    geo = shape.geometry
                    verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                    faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)
                    if len(verts) < 3 or len(faces) < 1:
                        vblob, fblob, center, minXYZ, maxXYZ = bbox_from_placement(elem)
                        bbox_fallback += 1
                    else:
                        # S169: LOCAL coords — origin = IFC tack point (BOM granted)
                        # DO NOT re-center. Local (0,0,0) = placement origin
                        # (door hinge, pipe connection, attachment face)
                        vblob = verts.astype(np.float32).tobytes()
                        fblob = faces.astype(np.int32).tobytes()

                        # World transform from shape placement matrix
                        mat_flat = list(shape.transformation.matrix)
                        mat4 = np.array(mat_flat, dtype=np.float64).reshape(4, 4).T
                        rot3 = mat4[:3, :3]
                        # Tack point world position = matrix translation directly
                        center = mat4[:3, 3]

                        # World-space bbox for R-tree (transform local bbox corners)
                        local_min = verts.min(axis=0)
                        local_max = verts.max(axis=0)
                        corners = np.array([
                            [local_min[0], local_min[1], local_min[2]],
                            [local_max[0], local_min[1], local_min[2]],
                            [local_min[0], local_max[1], local_min[2]],
                            [local_max[0], local_max[1], local_min[2]],
                            [local_min[0], local_min[1], local_max[2]],
                            [local_max[0], local_min[1], local_max[2]],
                            [local_min[0], local_max[1], local_max[2]],
                            [local_max[0], local_max[1], local_max[2]],
                        ])
                        world_corners = (rot3 @ corners.T).T + mat4[:3, 3]
                        minXYZ = world_corners.min(axis=0)
                        maxXYZ = world_corners.max(axis=0)

                        # Decompose rotation to Euler XYZ (radians)
                        import math
                        # From rotation matrix to Euler XYZ
                        sy = math.sqrt(rot3[0, 0]**2 + rot3[1, 0]**2)
                        if sy > 1e-6:
                            rot_x = math.atan2(rot3[2, 1], rot3[2, 2])
                            rot_y = math.atan2(-rot3[2, 0], sy)
                            rot_z = math.atan2(rot3[1, 0], rot3[0, 0])
                        else:
                            rot_x = math.atan2(-rot3[1, 2], rot3[1, 1])
                            rot_y = math.atan2(-rot3[2, 0], sy)
                            rot_z = 0.0

                ghash = geometry_hash(vblob, fblob)

                guid = elem.GlobalId
                name = getattr(elem, "Name", None)
                storey = get_storey_for_element(elem)
                discipline = infer_discipline(cls)
                elem_type = None
                try:
                    for rel in elem.IsDefinedBy:
                        if rel.is_a("IfcRelDefinesByType"):
                            elem_type = rel.RelatingType.Name
                            break
                except (AttributeError, TypeError):
                    pass

                # Material + colour in SAME pass
                material_name = get_material_for_element(elem)
                material_rgba = get_colour_for_element(elem)
                if material_name:
                    mat_found += 1
                if material_rgba:
                    rgba_found += 1

                if not dry_run:
                    # Vertex/face counts — derive from BLOB size (always correct)
                    v_count = len(vblob) // 12   # float32 * 3 = 12 bytes/vertex
                    f_count = len(fblob) // 12   # int32 * 3  = 12 bytes/face

                    if ghash not in existing_hashes:
                        if lib_conn:
                            # S168: BLOBs → component_library.db
                            rc = lib_conn.execute(
                                "INSERT OR IGNORE INTO component_geometries "
                                "(geometry_hash, vertices, faces, normals, "
                                "vertex_count, face_count) "
                                "VALUES (?,?,?,NULL,?,?)",
                                (ghash, vblob, fblob, v_count, f_count))
                            if rc.rowcount > 0:
                                lib_geo_new += 1
                                # S169: component_definitions — UFB + local bounds
                                local_min = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3).min(axis=0)
                                local_max = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3).max(axis=0)
                                attach = _infer_attachment_face(cls)
                                lib_conn.execute(
                                    "INSERT OR IGNORE INTO component_definitions "
                                    "(name, geometry_hash, "
                                    "local_min_x, local_max_x, "
                                    "local_min_y, local_max_y, "
                                    "local_min_z, local_max_z, "
                                    "attachment_face, up_axis, forward_axis, "
                                    "vertex_count, face_count, instance_count) "
                                    "VALUES (?,?,?,?,?,?,?,?,?,'Z','Y',?,?,1)",
                                    (elem_type or name or cls,
                                     ghash,
                                     float(local_min[0]), float(local_max[0]),
                                     float(local_min[1]), float(local_max[1]),
                                     float(local_min[2]), float(local_max[2]),
                                     attach, v_count, f_count))
                            # Hash-only in _extracted.db (NULL BLOBs)
                            conn.execute(
                                "INSERT OR IGNORE INTO base_geometries "
                                "(geometry_hash, vertices, faces, "
                                "vertex_count, face_count) "
                                "VALUES (?,NULL,NULL,?,?)",
                                (ghash, v_count, f_count))
                        else:
                            # Legacy mode: BLOBs in _extracted.db
                            conn.execute(
                                "INSERT OR IGNORE INTO base_geometries "
                                "(geometry_hash, vertices, faces, "
                                "vertex_count, face_count) "
                                "VALUES (?,?,?,?,?)",
                                (ghash, vblob, fblob, v_count, f_count))
                        existing_hashes.add(ghash)

                    eid = next_id
                    next_id += 1
                    conn.execute(
                        "INSERT OR IGNORE INTO elements_meta "
                        "(id, guid, discipline, ifc_class, element_name, element_type, "
                        "storey, material_name, material_rgba) "
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                        (eid, guid, discipline, cls, name, elem_type, storey,
                         material_name, material_rgba))
                    conn.execute(
                        "INSERT OR IGNORE INTO elements_rtree "
                        "(id, minX, maxX, minY, maxY, minZ, maxZ) "
                        "VALUES (?,?,?,?,?,?,?)",
                        (eid, float(minXYZ[0]), float(maxXYZ[0]),
                         float(minXYZ[1]), float(maxXYZ[1]),
                         float(minXYZ[2]), float(maxXYZ[2])))
                    conn.execute(
                        "INSERT OR IGNORE INTO element_instances (guid, geometry_hash) "
                        "VALUES (?,?)", (guid, ghash))
                    conn.execute(
                        "INSERT OR IGNORE INTO element_transforms "
                        "(guid, center_x, center_y, center_z, "
                        "rotation_x, rotation_y, rotation_z, transform_source) "
                        "VALUES (?,?,?,?,?,?,?,'ifc_extract')",
                        (guid, float(center[0]), float(center[1]), float(center[2]),
                         float(rot_x), float(rot_y), float(rot_z)))
                    space = get_space_for_element(elem)
                    if space:
                        conn.execute(
                            "INSERT OR IGNORE INTO rel_contained_in_space VALUES (?,?)",
                            (guid, space.GlobalId))

                    # S168: Write I_Geometry_Map + M_Product to library
                    if lib_conn:
                        storey_norm = storey or "Unknown"
                        key = (cls, storey_norm)
                        ordinal = ordinal_counter.get(key, 1)
                        ordinal_counter[key] = ordinal + 1

                        element_ref = f"{elem_type or name or cls}"
                        rc = lib_conn.execute(
                            "INSERT OR IGNORE INTO I_Geometry_Map "
                            "(building_type, element_ref, ifc_class, storey, "
                            "ordinal, geometry_hash, source, provenance) "
                            "VALUES (?,?,?,?,?,?,?,?)",
                            (building_type, element_ref, cls, storey_norm,
                             ordinal, ghash, building_type, "EXTRACTED"))
                        if rc.rowcount > 0:
                            lib_igm_new += 1

                        # M_Product from bbox dimensions
                        width = float(maxXYZ[0] - minXYZ[0])
                        depth = float(maxXYZ[1] - minXYZ[1])
                        height = float(maxXYZ[2] - minXYZ[2])
                        prod_id = _product_id_from_dims(cls, width, depth, height)
                        prod_type = _infer_product_type(cls)
                        rc = lib_conn.execute(
                            "INSERT OR IGNORE INTO M_Product "
                            "(product_id, product_type, width, depth, height, "
                            "ifc_class, extracted_from, building_type, "
                            "source_element_ref) "
                            "VALUES (?,?,?,?,?,?,?,?,?)",
                            (prod_id, prod_type, width, depth, height,
                             cls, "IFC_EXTRACTION", building_type, element_ref))
                        if rc.rowcount > 0:
                            lib_prod_new += 1

                imported += 1

                # S170: batch-commit every 1000 elements to release write lock
                # Enables concurrent extractions against same component_library.db
                if imported % 1000 == 0 and not dry_run:
                    conn.commit()
                    if lib_conn:
                        lib_conn.commit()

            except Exception as exc:
                failed += 1
                if failed <= 5:
                    print(f"  §FAIL {cls} {getattr(elem, 'GlobalId', '?')}: {exc}")

    if not dry_run:
        conn.commit()
        if lib_conn:
            lib_conn.commit()

    # S169: Normalize building origin — subtract centroid so building is near (0,0,0)
    # Fixes georeferenced IFC files (UTM/national grid) that place elements at 100K+ metres
    if not dry_run and imported > 0:
        row = conn.execute("""
            SELECT AVG(center_x), AVG(center_y), MIN(center_z)
            FROM element_transforms
        """).fetchone()
        if row and row[0] is not None:
            ox, oy, oz = row[0], row[1], row[2]
            # Only normalize if significantly far from origin (> 100m)
            if abs(ox) > 100 or abs(oy) > 100 or abs(oz) > 100:
                print(f"  §NORMALIZE origin far from (0,0,0): centroid=({ox:.1f}, {oy:.1f}, {oz:.1f})")
                print(f"  §NORMALIZE subtracting to re-center building")
                conn.execute("""
                    UPDATE element_transforms
                    SET center_x = center_x - ?,
                        center_y = center_y - ?,
                        center_z = center_z - ?
                """, (ox, oy, oz))
                conn.execute("""
                    UPDATE elements_rtree
                    SET minX = minX - ?, maxX = maxX - ?,
                        minY = minY - ?, maxY = maxY - ?,
                        minZ = minZ - ?, maxZ = maxZ - ?
                """, (ox, ox, oy, oy, oz, oz))
                # Store offset for IFC round-trip export
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS site_normalization (
                        key TEXT PRIMARY KEY, value REAL
                    )
                """)
                conn.execute("INSERT OR REPLACE INTO site_normalization VALUES ('offset_x', ?)", (ox,))
                conn.execute("INSERT OR REPLACE INTO site_normalization VALUES ('offset_y', ?)", (oy,))
                conn.execute("INSERT OR REPLACE INTO site_normalization VALUES ('offset_z', ?)", (oz,))
                conn.commit()
                print(f"  §NORMALIZE offset stored in site_normalization for IFC export round-trip")

    # Extract IfcRelAggregates — parent-child decomposition
    agg_count = 0
    if not dry_run:
        try:
            for rel in ifc_file.by_type("IfcRelAggregates"):
                try:
                    parent = rel.RelatingObject
                    if not parent or not hasattr(parent, 'GlobalId'):
                        continue
                    for child in rel.RelatedObjects:
                        if not child or not hasattr(child, 'GlobalId'):
                            continue
                        conn.execute(
                            "INSERT OR IGNORE INTO rel_aggregates (parent_guid, child_guid) "
                            "VALUES (?, ?)", (parent.GlobalId, child.GlobalId))
                        agg_count += 1
                except (AttributeError, TypeError):
                    pass
            conn.commit()
        except RuntimeError:
            pass  # IFC schema may not have these types
    if agg_count > 0:
        print(f"  IfcRelAggregates: {agg_count} parent→child decomposition mappings")

    # Extract rich surface styles + material layers
    source_tag = f"EXTRACTED:{os.path.basename(ifc_path)}"
    styles = extract_surface_styles(ifc_file, source_tag)
    layers = extract_material_layers(ifc_file)
    if not dry_run:
        write_surface_styles(conn, styles)
        write_material_layers(conn, layers)

    print(f"\n  §EXTRACT Elements: {imported} (failed: {failed}, simplified: {simplified}, bbox_fallback: {bbox_fallback})")
    print(f"  §EXTRACT Materials: {mat_found} names, {rgba_found} RGBA colours")
    print(f"  §EXTRACT Surface styles: {len(styles)}, Material layers: {len(layers)}")

    # Geometry deduplication stats
    if not dry_run:
        unique_hashes = conn.execute(
            "SELECT COUNT(DISTINCT geometry_hash) FROM element_instances").fetchone()[0]
        total_instances = conn.execute(
            "SELECT COUNT(*) FROM element_instances").fetchone()[0]
        reuse_ratio = total_instances / max(unique_hashes, 1)
        print(f"  §DEDUP {unique_hashes} unique hashes / {total_instances} instances (reuse ratio: {reuse_ratio:.1f}x)")

    # Rotation stats
    if not dry_run:
        rot_stats = conn.execute("""
            SELECT COUNT(*) as total,
                   SUM(CASE WHEN rotation_x != 0 OR rotation_y != 0 OR rotation_z != 0 THEN 1 ELSE 0 END) as rotated
            FROM element_transforms
        """).fetchone()
        if rot_stats:
            print(f"  §ROTATION {rot_stats[1]}/{rot_stats[0]} elements have non-zero rotation")

    # Tack point verification — origin should be near element base (Z≈0)
    if not dry_run and unique_hashes > 0:
        origins = conn.execute("""
            SELECT bg.geometry_hash,
                   MIN(CAST(SUBSTR(HEX(bg.vertices), 1, 8) AS REAL)) as sample
            FROM base_geometries bg LIMIT 1
        """).fetchone()
        # Check via actual vertex data in library
        print(f"  §TACK_POINT origin=IFC placement (Z-up, Y-forward) — no bbox centering")

    # S168: Library summary
    if lib_conn and not dry_run:
        print(f"\n  §LIBRARY writes ({library_path}):")
        print(f"    §LIBRARY component_geometries: {lib_geo_new} new hashes")
        print(f"    §LIBRARY component_definitions: {lib_geo_new} new defs (UFB: Z-up, Y-fwd)")
        print(f"    §LIBRARY I_Geometry_Map:       {lib_igm_new} new instance mappings")
        print(f"    §LIBRARY M_Product:            {lib_prod_new} new product defs")
        total_geo = lib_conn.execute(
            "SELECT COUNT(*) FROM component_geometries").fetchone()[0]
        total_defs = lib_conn.execute(
            "SELECT COUNT(*) FROM component_definitions").fetchone()[0]
        total_igm = lib_conn.execute(
            "SELECT COUNT(*) FROM I_Geometry_Map").fetchone()[0]
        total_prod = lib_conn.execute(
            "SELECT COUNT(*) FROM M_Product").fetchone()[0]
        print(f"    §LIBRARY totals: {total_geo} geometries, {total_defs} defs, "
              f"{total_igm} I_Geometry_Map, {total_prod} M_Product")

        # Attachment face distribution
        face_dist = lib_conn.execute("""
            SELECT attachment_face, COUNT(*) FROM component_definitions
            GROUP BY attachment_face ORDER BY COUNT(*) DESC
        """).fetchall()
        if face_dist:
            print(f"    §LIBRARY attachment_face: {', '.join(f'{f}={c}' for f, c in face_dist)}")

        lib_conn.close()

    # Summary by class
    if not dry_run:
        rows = conn.execute(
            "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC"
        ).fetchall()
        print(f"\n  By IFC class:")
        for cls, cnt in rows:
            print(f"    {cls:40s} {cnt}")

        # Summary by discipline
        rows = conn.execute(
            "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC"
        ).fetchall()
        print(f"\n  By discipline:")
        for disc, cnt in rows:
            print(f"    {disc:15s} {cnt}")

        # Material summary
        rows = conn.execute(
            "SELECT material_name, COUNT(*), material_rgba FROM elements_meta "
            "WHERE material_name IS NOT NULL GROUP BY material_name ORDER BY COUNT(*) DESC"
        ).fetchall()
        if rows:
            print(f"\n  Materials ({len(rows)} distinct):")
            for mat, cnt, rgba in rows[:15]:
                rgba_str = f"  rgba={rgba}" if rgba else ""
                print(f"    {mat:45s} {cnt:4d}{rgba_str}")
            if len(rows) > 15:
                print(f"    ... and {len(rows) - 15} more")

        sz = os.path.getsize(output_path) / (1024 * 1024)
        conn.close()
        print(f"\n  Written to: {output_path} ({sz:.1f} MB)")

    return imported


# ---------------------------------------------------------------------------
# Enrich existing DB with materials only (no geometry re-extraction)
# ---------------------------------------------------------------------------

def enrich_reference_db(ifc_path, ref_db_path, dry_run=False):
    """Add materials from IFC to existing reference DB (ALTER TABLE if needed)."""
    import ifcopenshell

    print(f"Opening IFC: {ifc_path}")
    ifc_file = ifcopenshell.open(ifc_path)

    conn = sqlite3.connect(ref_db_path)

    # Ensure material columns exist
    cols = {row[1] for row in conn.execute("PRAGMA table_info(elements_meta)")}
    if "material_name" not in cols:
        if not dry_run:
            conn.execute("ALTER TABLE elements_meta ADD COLUMN material_name TEXT")
            print("  Added material_name column")
    if "material_rgba" not in cols:
        if not dry_run:
            conn.execute("ALTER TABLE elements_meta ADD COLUMN material_rgba TEXT")
            print("  Added material_rgba column")
        conn.commit()

    # Build GUID -> IFC element lookup
    guid_to_ifc = {}
    for elem in ifc_file:
        if hasattr(elem, 'GlobalId'):
            guid_to_ifc[elem.GlobalId] = elem

    rows = conn.execute("SELECT id, guid FROM elements_meta").fetchall()
    print(f"  Reference DB has {len(rows)} elements")

    matched = mat_found = rgba_found = 0
    updates = []

    for eid, guid in rows:
        ifc_elem = guid_to_ifc.get(guid)
        if not ifc_elem:
            continue
        matched += 1
        material_name = get_material_for_element(ifc_elem)
        rgba = get_colour_for_element(ifc_elem)
        if material_name:
            mat_found += 1
        if rgba:
            rgba_found += 1
        if material_name or rgba:
            updates.append((material_name, rgba, guid))

    print(f"  GUID matched: {matched}/{len(rows)}")
    print(f"  Material names: {mat_found}, RGBA colours: {rgba_found}")

    if dry_run:
        for mat, rgba, guid in updates[:10]:
            print(f"    {guid}: material={mat}, rgba={rgba}")
    else:
        for mat, rgba, guid in updates:
            conn.execute(
                "UPDATE elements_meta SET material_name = ?, material_rgba = ? "
                "WHERE guid = ?", (mat, rgba, guid))
        conn.commit()
        print(f"  Wrote {len(updates)} material updates.")

    conn.close()
    return len(updates)


# ---------------------------------------------------------------------------
# Populate I_Element_Extraction with material data
# ---------------------------------------------------------------------------

def populate_placement_materials(ref_db_path, library_db_path, building_type, dry_run=False):
    """Copy material_name/material_rgba from reference DB to I_Element_Extraction."""
    ref_conn = sqlite3.connect(ref_db_path)
    lib_conn = sqlite3.connect(library_db_path)

    # Ensure placement table has material columns
    cols = {row[1] for row in lib_conn.execute("PRAGMA table_info(I_Element_Extraction)")}
    if "material_name" not in cols and not dry_run:
        lib_conn.execute("ALTER TABLE I_Element_Extraction ADD COLUMN material_name TEXT")
        print("  Added material_name column to I_Element_Extraction")
    if "material_rgba" not in cols and not dry_run:
        lib_conn.execute("ALTER TABLE I_Element_Extraction ADD COLUMN material_rgba TEXT")
        print("  Added material_rgba column to I_Element_Extraction")

    # Check reference DB has material columns
    ref_cols = {row[1] for row in ref_conn.execute("PRAGMA table_info(elements_meta)")}
    if "material_name" not in ref_cols:
        print("  ERROR: Reference DB has no material_name column. Run extraction first.")
        ref_conn.close()
        lib_conn.close()
        return 0

    def normalize_storey(s):
        if not s or s.strip() == "":
            return "Unknown"
        return s.strip()

    # Get reference elements ordered for ordinal assignment (same as placement_extractor)
    ref_elements = ref_conn.execute("""
        SELECT m.guid, m.ifc_class, m.storey, m.material_name, m.material_rgba,
               r.minX, r.minY, r.minZ
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        ORDER BY m.ifc_class, m.storey, r.minX, r.minY, r.minZ
    """).fetchall()

    # Assign 1-based ordinals (matching placement_extractor convention)
    ordinal_counter = {}
    ref_lookup = {}
    for guid, ifc_class, storey, mat_name, mat_rgba, minX, minY, minZ in ref_elements:
        storey_norm = normalize_storey(storey)
        key = (ifc_class, storey_norm)
        ordinal = ordinal_counter.get(key, 1)
        ordinal_counter[key] = ordinal + 1
        ref_lookup[(ifc_class, storey_norm, ordinal)] = (mat_name, mat_rgba)

    # Read placement entries
    placements = lib_conn.execute("""
        SELECT rowid, ifc_class, storey, ordinal
        FROM I_Element_Extraction
        WHERE building_type = ? AND is_active = 1
    """, (building_type,)).fetchall()

    updates = []
    for rowid, ifc_class, storey, ordinal in placements:
        storey_norm = normalize_storey(storey)
        mat = ref_lookup.get((ifc_class, storey_norm, ordinal))
        if mat and (mat[0] or mat[1]):
            updates.append((mat[0], mat[1], rowid))

    print(f"  Placements for {building_type}: {len(placements)}")
    print(f"  Material matches: {len(updates)}")

    if dry_run:
        for mat_name, mat_rgba, rowid in updates[:10]:
            print(f"    rowid={rowid}: material={mat_name}, rgba={mat_rgba}")
    else:
        for mat_name, mat_rgba, rowid in updates:
            lib_conn.execute(
                "UPDATE I_Element_Extraction SET material_name = ?, material_rgba = ? "
                "WHERE rowid = ?", (mat_name, mat_rgba, rowid))
        lib_conn.commit()
        print(f"  Wrote {len(updates)} material updates to I_Element_Extraction.")

    ref_conn.close()
    lib_conn.close()
    return len(updates)


# ---------------------------------------------------------------------------
# Enrich existing DB with surface_styles + material_layers only
# ---------------------------------------------------------------------------

def enrich_styles_only(ifc_path, ref_db_path, dry_run=False):
    """Add surface_styles + material_layers to existing reference DB."""
    import ifcopenshell

    print(f"Opening IFC: {ifc_path}")
    ifc_file = ifcopenshell.open(ifc_path)

    conn = sqlite3.connect(ref_db_path)

    # Ensure tables exist
    conn.execute("""CREATE TABLE IF NOT EXISTS surface_styles (
        style_name TEXT PRIMARY KEY,
        surface_r REAL, surface_g REAL, surface_b REAL,
        transparency REAL DEFAULT 0.0,
        specular_r REAL, specular_g REAL, specular_b REAL,
        specular_ratio REAL, specular_exponent REAL,
        reflectance_method TEXT DEFAULT 'NOTDEFINED',
        side TEXT DEFAULT 'BOTH', source TEXT)""")
    conn.execute("""CREATE TABLE IF NOT EXISTS material_layers (
        layer_set_name TEXT NOT NULL, sequence INTEGER NOT NULL,
        material_name TEXT, thickness_m REAL, is_ventilated INTEGER DEFAULT 0,
        PRIMARY KEY (layer_set_name, sequence))""")
    conn.commit()

    source_tag = f"EXTRACTED:{os.path.basename(ifc_path)}"
    styles = extract_surface_styles(ifc_file, source_tag)
    layers = extract_material_layers(ifc_file)

    print(f"  Surface styles: {len(styles)}")
    for s in styles[:10]:
        spec_info = ""
        if s['specular_exponent'] is not None:
            spec_info = f" exp={s['specular_exponent']}"
        elif s['specular_ratio'] is not None:
            spec_info = f" ratio={s['specular_ratio']}"
        print(f"    {s['style_name']:40s} t={s['transparency']:.2f} {s['reflectance_method']}{spec_info}")
    if len(styles) > 10:
        print(f"    ... and {len(styles) - 10} more")

    print(f"  Material layers: {len(layers)}")
    for l in layers:
        vent = " [VENTILATED]" if l['is_ventilated'] else ""
        thick = f" {l['thickness_m']*1000:.0f}mm" if l['thickness_m'] else ""
        print(f"    {l['layer_set_name']:45s} #{l['sequence']} {l['material_name']}{thick}{vent}")

    if not dry_run:
        write_surface_styles(conn, styles)
        write_material_layers(conn, layers)
        print(f"  Written to: {ref_db_path}")

    conn.close()
    return len(styles), len(layers)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="DAGCompiler Reference DB Extractor — IFC to reference DB with materials")
    parser.add_argument("--ifc", help="IFC source file path")
    parser.add_argument("-o", "--output", help="Output reference DB path (for full extraction)")
    parser.add_argument("--ref", help="Existing reference DB path (for --enrich or --populate-placement)")
    parser.add_argument("--enrich", action="store_true",
                        help="Enrich existing DB with materials only (no geometry)")
    parser.add_argument("--populate-placement", action="store_true",
                        help="Copy materials from reference DB to I_Element_Extraction")
    parser.add_argument("--library", help="Component library DB path (S168: mesh BLOBs go here during extraction)")
    parser.add_argument("--building-type", help="Building type key (auto-derived from IFC filename if omitted)")
    parser.add_argument("--styles-only", action="store_true",
                        help="Enrich existing DB with surface_styles + material_layers only")
    parser.add_argument("--exclude", help="Comma-separated IFC classes to exclude")
    parser.add_argument("--dry-run", action="store_true", help="Report only, no writes")
    args = parser.parse_args()

    exclude = args.exclude.split(",") if args.exclude else []

    if args.styles_only:
        if not args.ifc or not args.ref:
            print("ERROR: --styles-only requires --ifc and --ref")
            sys.exit(1)
        enrich_styles_only(args.ifc, args.ref, args.dry_run)
    elif args.populate_placement:
        if not args.ref or not args.library or not args.building_type:
            print("ERROR: --populate-placement requires --ref, --library, and --building-type")
            sys.exit(1)
        populate_placement_materials(args.ref, args.library, args.building_type, args.dry_run)
    elif args.enrich:
        if not args.ifc or not args.ref:
            print("ERROR: --enrich requires --ifc and --ref")
            sys.exit(1)
        enrich_reference_db(args.ifc, args.ref, args.dry_run)
    elif args.ifc and args.output:
        extract_reference(args.ifc, args.output, exclude=exclude,
                          dry_run=args.dry_run,
                          library_path=args.library,
                          building_type=args.building_type)
    else:
        print("ERROR: Must specify either:")
        print("  --ifc FILE -o OUTPUT       Full extraction (geometry + materials)")
        print("  --enrich --ifc FILE --ref DB   Enrich existing DB with materials")
        print("  --populate-placement --ref DB --library DB --building-type TYPE")
        sys.exit(1)


if __name__ == "__main__":
    main()
