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
import datetime as _dt
import sqlite3
import struct

# S173: Log level — FINE enables detailed proofs, INFO is summary only.
LOG_LEVEL = os.environ.get("BIM_LOG_LEVEL", "FINE").upper()
FINE = LOG_LEVEL == "FINE"
# §LODHELL-FIX-1: number of §PROOF checks that FAILED in the last extract_reference() call. main() turns a
# non-zero value into a non-zero exit status so a red gate actually stops a pipeline.
LAST_PROOF_FAIL = 0
import sys
import time

import numpy as np


# ---------------------------------------------------------------------------
# S171: Extract world placement matrix from IFC ObjectPlacement chain
# (no tessellation — pure IFC data traversal)
# ---------------------------------------------------------------------------

def _placement_matrix(elem):
    """Return 4x4 world transform matrix from IfcLocalPlacement chain."""
    mat = np.eye(4, dtype=np.float64)
    placement = getattr(elem, 'ObjectPlacement', None)
    placements = []
    while placement and placement.is_a('IfcLocalPlacement'):
        placements.append(placement)
        placement = placement.PlacementRelTo
    # Apply from root to leaf (outermost first)
    for p in reversed(placements):
        rp = p.RelativePlacement
        if rp is None:
            continue
        local = np.eye(4, dtype=np.float64)
        loc = rp.Location
        if loc:
            coords = list(loc.Coordinates)
            if len(coords) == 2:
                coords.append(0.0)
            local[0, 3] = coords[0]
            local[1, 3] = coords[1]
            local[2, 3] = coords[2]
        axis = getattr(rp, 'Axis', None)
        ref_dir = getattr(rp, 'RefDirection', None)
        if axis and axis.DirectionRatios:
            z = np.array(list(axis.DirectionRatios)[:3], dtype=np.float64)
            z = z / max(np.linalg.norm(z), 1e-12)
        else:
            z = np.array([0, 0, 1], dtype=np.float64)
        if ref_dir and ref_dir.DirectionRatios:
            x = np.array(list(ref_dir.DirectionRatios)[:3], dtype=np.float64)
            x = x / max(np.linalg.norm(x), 1e-12)
        else:
            x = np.array([1, 0, 0], dtype=np.float64)
        y = np.cross(z, x)
        y = y / max(np.linalg.norm(y), 1e-12)
        x = np.cross(y, z)
        x = x / max(np.linalg.norm(x), 1e-12)
        local[:3, 0] = x
        local[:3, 1] = y
        local[:3, 2] = z
        mat = mat @ local
    return mat


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
    predefined_type TEXT,
    -- IfcSpace footprint AABB (room qualification: habitable area = size_x*size_y, height = size_z).
    -- Spaces are non-geometric in the iterator (no body mesh) but DO carry a Representation solid;
    -- we tessellate it once here for the AABB only. NULL for Building/Storey (no own footprint).
    center_x REAL, center_y REAL, center_z REAL,
    size_x REAL, size_y REAL, size_z REAL
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
    material_rgba TEXT,
    -- §KUL001: the Viewer's centres query (viewer/streaming.js) is
    --   SELECT m.building, COUNT(*), AVG(t.center_*) ... GROUP BY m.building
    -- Without this column it throws `no such column: m.building`, §CENTRES_RESULT rows=0,
    -- §BOOTSTRAP centres=0, and A.startStreaming() takes a SILENT early return: the DB loads,
    -- logs its full size, and renders NOTHING. Reads like a size/perf bug and is neither.
    -- The browser importer has always written it (viewer/import_db_builder.js:38,48); this
    -- extractor did not, which is why CLI-built DBs opened blank.
    building TEXT
);
CREATE TABLE IF NOT EXISTS project_metadata (key TEXT PRIMARY KEY, value TEXT);
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
    -- AABB full extent (maxK-minK), same source as elements_rtree — the bbox substrate the STR walker +
    -- cross-edge derivation read (previously backfilled post-hoc by scripts/backfill_bbox.py; now native).
    bbox_x REAL,
    bbox_y REAL,
    bbox_z REAL,
    transform_source TEXT
);
CREATE TABLE IF NOT EXISTS port_elements (
    port_guid TEXT PRIMARY KEY,
    element_guid TEXT NOT NULL,
    flow_direction TEXT,
    local_x REAL,
    local_y REAL,
    local_z REAL
);
CREATE TABLE IF NOT EXISTS port_connections (
    port_a_guid TEXT NOT NULL,
    port_b_guid TEXT NOT NULL,
    PRIMARY KEY (port_a_guid, port_b_guid)
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
-- Path B (§PATHB / SPATIAL_DEPENDENCY_GRAPH.md): the void/fill chain, recovered verbatim from
-- IfcRelVoidsElement (host→opening) composed with IfcRelFillsElement (opening→filling). One row per
-- opening that voids a host; filling_guid NULL = open void (no door/window). provenance is always
-- 'ifc:recovered' — NON-INVENT, we copy authored relations and derive nothing here.
CREATE TABLE IF NOT EXISTS rel_fills_host (
    opening_guid TEXT PRIMARY KEY,
    host_guid TEXT,
    filling_guid TEXT,
    host_class TEXT,
    filling_class TEXT,
    provenance TEXT DEFAULT 'ifc:recovered'
);
-- abuts edge (SPATIAL_DEPENDENCY_GRAPH.md): the first DERIVED measured edge. Two elements ABUT if they
-- share a face — measured from AABBs: faces within tol on ONE axis (the touch axis) AND a real overlap on
-- the other two. DERIVED, never recovered/guessed: provenance carries the measure ('derived:face-touch').
-- One row per unordered pair (a_guid < b_guid). gap_mm = |signed gap| at the touching face; contact_m2 =
-- the shared-face contact patch (the two non-touch overlaps multiplied) — a measured fold quantity.
CREATE TABLE IF NOT EXISTS rel_adjacency (
    a_guid TEXT,
    b_guid TEXT,
    touch_axis TEXT,
    gap_mm REAL,
    contact_m2 REAL,
    provenance TEXT DEFAULT 'derived:face-touch',
    PRIMARY KEY (a_guid, b_guid, touch_axis)
);
-- anchored-to edge (SPATIAL_DEPENDENCY_GRAPH.md): a DERIVED edge from element → DATUM plane. Datums are
-- NOT recovered (a bridge has no IfcGrid, no storeys) — they EMERGE by measure: a datum on an axis is a
-- coordinate where ≥min_support element FACES align within tol (a gridline, a storey plane, a pier station).
-- NON-INVENT: zero class names, no template grid; the cadence of the real geometry defines the datums.
CREATE TABLE IF NOT EXISTS datum_plane (
    datum_id INTEGER PRIMARY KEY,
    axis TEXT,
    coord REAL,
    support_count INTEGER,
    provenance TEXT DEFAULT 'derived:cadence'
);
CREATE TABLE IF NOT EXISTS rel_anchored (
    element_guid TEXT,
    datum_id INTEGER,
    axis TEXT,
    offset_mm REAL,
    provenance TEXT DEFAULT 'derived:cadence-snap',
    PRIMARY KEY (element_guid, datum_id)
);
-- spans edge (SPATIAL_DEPENDENCY_GRAPH.md): a DERIVED edge — an element SPANS two datums when its bbox
-- reaches from near one datum to near another on an axis (a girder between piers, a slab across gridlines).
-- The fold rule: the element stretches between the two datums with its cross-section sizes HELD. DERIVED
-- from datum_plane geometry, NON-INVENT, grep-clean. One row per (element, axis); datum_lo < datum_hi.
CREATE TABLE IF NOT EXISTS rel_spans (
    element_guid TEXT,
    axis TEXT,
    datum_lo_id INTEGER,
    datum_hi_id INTEGER,
    span_m REAL,
    provenance TEXT DEFAULT 'derived:bbox-spans-datums',
    PRIMARY KEY (element_guid, axis)
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
    """ILLEGAL FALLBACK — parametric box generation is a no-invent violation.

    If the tessellation engine cannot produce geometry for an element, that
    element must be SKIPPED, not replaced with a synthetic 1×1×1 box.
    A fake box in the library corrupts downstream consumers (viewer, BOM,
    spatial queries) and violates the EXTRACT-OR-COMPILE-ONLY prime rule.
    """
    guid = getattr(elem, 'GlobalId', '?')
    cls = elem.is_a() if hasattr(elem, 'is_a') else type(elem).__name__
    raise RuntimeError(
        f"§ILLEGAL_PARAMETRIC_FALLBACK: {cls} guid={guid} — "
        f"tessellation failed and bbox_from_placement is banned. "
        f"Add to NON_GEOMETRIC_CLASSES if this type has no mesh, "
        f"or fix the IFC source."
    )


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


def is_void_consumed(elem, settings):
    """§LODHELL-FIX-1 (RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LODHELL-FIX) — Witness: W-LODHELL-CLASSIFY.

    An element can tessellate to NOTHING for two completely different reasons, and the extractor used to
    report both as `§ILLEGAL_PARAMETRIC_FALLBACK` ("fix your IFC"):

      (a) VOID-CONSUMED — the element has a perfectly good body, and its OWN authored IfcRelVoidsElement
          openings subtract 100% of it. Measured on SampleCastle 2026-07-27: `kozijn` window-frame walls
          are a 1.210 x 0.114 x 1.850 m strip whose opening is 1.210 x 0.342 x 1.850 m — same width and
          height, thicker. The empty result is geometrically CORRECT; the window that fills the void is
          what carries the visible geometry (74/74 fillings verified present). Not a failure.
      (b) a real defect — no body, or an empty body with nothing voiding it.

    Classified from authored data only: does a `Body` representation ITEM tessellate on its own (bypassing
    the openings), and does the element declare any opening? No thresholds, no proximity, nothing inferred.

    Returns True only for (a).
    """
    import ifcopenshell.geom          # module-level import is deferred in this file (see caller at :1047)
    try:
        if not getattr(elem, 'HasOpenings', None):
            return False
        rep = getattr(elem, 'Representation', None)
        if not rep:
            return False
        for sub in rep.Representations:
            if sub.RepresentationIdentifier != 'Body':
                continue
            for item in sub.Items:
                try:
                    shp = ifcopenshell.geom.create_shape(settings, item)
                    geo = shp.geometry if hasattr(shp, 'geometry') else shp
                    if len(geo.verts) >= 9:      # >= 3 vertices
                        return True
                except Exception:
                    continue
    except (AttributeError, TypeError):
        return False
    return False


def extract_rel_fills_host(ifc_file, conn):
    """§PATHB (SPATIAL_DEPENDENCY_GRAPH.md Phase 1): recover the void/fill chain.

    The `hosted-by` edge, RECOVERED from authored relations — never proximity-guessed:
      IfcRelVoidsElement: RelatingBuildingElement (host) → RelatedOpeningElement (opening)
      IfcRelFillsElement: RelatingOpeningElement (opening) → RelatedBuildingElement (filling)
    Compose on the shared opening GUID → filling → opening → host. One row per opening that
    voids a host; filling_guid NULL = open void. NON-INVENT: copies authored relations, derives
    nothing (the relative host-ride transform is derivable later from element_transforms).

    Returns (rows_written, rows_with_filling).
    """
    rows = 0
    filled = 0
    try:
        # opening_guid → host element (from Voids)
        host_of_opening = {}
        for rel in ifc_file.by_type("IfcRelVoidsElement"):
            try:
                host = rel.RelatingBuildingElement
                opening = rel.RelatedOpeningElement
                if not opening or not hasattr(opening, 'GlobalId'):
                    continue
                host_of_opening[opening.GlobalId] = host
            except (AttributeError, TypeError):
                pass
        # opening_guid → filling element (from Fills)
        filling_of_opening = {}
        for rel in ifc_file.by_type("IfcRelFillsElement"):
            try:
                opening = rel.RelatingOpeningElement
                filling = rel.RelatedBuildingElement
                if not opening or not hasattr(opening, 'GlobalId'):
                    continue
                filling_of_opening[opening.GlobalId] = filling
            except (AttributeError, TypeError):
                pass
        # One row per opening that voids a host (filling optional)
        for opening_guid, host in host_of_opening.items():
            if not host or not hasattr(host, 'GlobalId'):
                continue
            filling = filling_of_opening.get(opening_guid)
            filling_guid = filling.GlobalId if (filling and hasattr(filling, 'GlobalId')) else None
            filling_class = filling.is_a() if filling else None
            conn.execute(
                "INSERT OR IGNORE INTO rel_fills_host "
                "(opening_guid, host_guid, filling_guid, host_class, filling_class, provenance) "
                "VALUES (?, ?, ?, ?, ?, 'ifc:recovered')",
                (opening_guid, host.GlobalId, filling_guid, host.is_a(), filling_class))
            rows += 1
            if filling_guid:
                filled += 1
        conn.commit()
    except RuntimeError:
        pass  # IFC schema may not have these relation types
    return rows, filled


def _face_touch(a, b, tol, min_overlap):
    """Is the AABB pair (a,b) a FACE-TOUCH? Returns (touch_axis, gap_m, contact_m2) or None.

    Pure geometry — references NO IFC class. a,b are (minX,maxX,minY,maxY,minZ,maxZ).
    Face-touch on axis k ⇔ the opposing faces on k are within `tol` (small gap or small
    interpenetration) AND the boxes genuinely overlap on the OTHER two axes (≥ min_overlap each).
    The touch axis is the one with the smallest |overlap| (closest to a back-to-back face).
    Corner/edge grazes (two axes near-zero) and deep clashes (no axis near-zero) are excluded.
    """
    # signed overlap per axis: >0 interpenetrate, =0 touch, <0 gap
    ov = []
    for k in range(3):
        lo = max(a[2 * k], b[2 * k])
        hi = min(a[2 * k + 1], b[2 * k + 1])
        ov.append(hi - lo)
    # pick the touch axis = axis whose |overlap| is smallest (the back-to-back face)
    axis = min(range(3), key=lambda k: abs(ov[k]))
    if abs(ov[axis]) > tol:
        return None                       # faces not within tol on the closest axis → not adjacent
    others = [k for k in range(3) if k != axis]
    if ov[others[0]] < min_overlap or ov[others[1]] < min_overlap:
        return None                       # no real shared face (corner/edge graze) → not a face-touch
    contact = ov[others[0]] * ov[others[1]]
    return "XYZ"[axis], abs(ov[axis]), contact


def derive_adjacency(conn, tol=0.03, min_overlap=0.02):
    """§ABUTS (SPATIAL_DEPENDENCY_GRAPH.md): derive the `abuts` edge from MEASURED face-touch.

    Reads the pristine AABBs (elements_meta ⋈ elements_rtree), writes one rel_adjacency row per
    unordered face-touching pair. DERIVED, NON-INVENT: every edge is a measured face contact, stamped
    provenance='derived:face-touch'; NO proximity radius, NO class whitelist (grep-clean of class names).
    Uses the rtree to fetch only spatially-near candidates (scales past O(n²) on large buildings).

    Returns rows written.
    """
    rows = 0
    try:
        elems = conn.execute("""
            SELECT m.guid, r.id, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
        """).fetchall()
        box = {}    # id → (guid, aabb tuple)
        for guid, rid, x0, x1, y0, y1, z0, z1 in elems:
            box[rid] = (guid, (x0, x1, y0, y1, z0, z1))
        seen = set()
        for rid, (guid_a, a) in box.items():
            # rtree candidates: any element whose AABB comes within `tol` of a's expanded AABB
            cand = conn.execute("""
                SELECT id FROM elements_rtree
                WHERE maxX >= ? AND minX <= ? AND maxY >= ? AND minY <= ? AND maxZ >= ? AND minZ <= ?
            """, (a[0] - tol, a[1] + tol, a[2] - tol, a[3] + tol, a[4] - tol, a[5] + tol)).fetchall()
            for (rid_b,) in cand:
                if rid_b == rid or rid_b not in box:
                    continue
                guid_b, b = box[rid_b]
                if guid_a == guid_b:
                    continue
                lo, hi = (guid_a, guid_b) if guid_a < guid_b else (guid_b, guid_a)
                if (lo, hi) in seen:
                    continue
                ft = _face_touch(a, b, tol, min_overlap)
                if ft is None:
                    continue
                touch_axis, gap_m, contact = ft
                seen.add((lo, hi))
                conn.execute(
                    "INSERT OR IGNORE INTO rel_adjacency "
                    "(a_guid, b_guid, touch_axis, gap_mm, contact_m2, provenance) "
                    "VALUES (?, ?, ?, ?, ?, 'derived:face-touch')",
                    (lo, hi, touch_axis, round(gap_m * 1000, 3), round(contact, 6)))
                rows += 1
        conn.commit()
    except sqlite3.OperationalError:
        pass  # elements_rtree absent (non-geometric ref DB)
    return rows


def derive_datums_and_anchors(conn, tol=0.05, min_support=3):
    """§ANCHORED (SPATIAL_DEPENDENCY_GRAPH.md): derive datum planes + the `anchored-to` edge by MEASURE.

    A datum on an axis is a coordinate where the FACES (min & max box faces) of ≥min_support DISTINCT
    elements align within `tol` — i.e. a gridline, a storey plane, or a bridge pier station, EMERGENT from
    the real cadence, never a recovered IfcGrid (the bridge has none) and never a template. Each supporting
    element gets a rel_anchored edge to the datum (closest face → signed offset). NON-INVENT, grep-clean of
    class names: pure geometry. Greedy tol-bounded 1-D clustering caps each datum's spread at `tol` (no chaining
    into a smeared pseudo-plane).

    Returns (n_datums, n_anchors).
    """
    rows = conn.execute("""
        SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
    """).fetchall()
    if not rows:
        return 0, 0
    box = {g: (x0, x1, y0, y1, z0, z1) for g, x0, x1, y0, y1, z0, z1 in rows}
    n_datums = 0
    n_anchors = 0
    datum_id = 0
    for ax in range(3):
        # candidate faces: both the min and the max box face of every element on this axis
        cand = []
        for g, b in box.items():
            cand.append((b[2 * ax], g))
            cand.append((b[2 * ax + 1], g))
        cand.sort()
        # greedy clustering bounded to spread ≤ tol (prevents chaining across a gradient)
        i = 0
        while i < len(cand):
            start = cand[i][0]
            j = i
            while j < len(cand) and cand[j][0] - start <= tol:
                j += 1
            group = cand[i:j]
            i = j
            guids = {g for _, g in group}
            if len(guids) < min_support:
                continue
            coord = sum(c for c, _ in group) / len(group)
            datum_id += 1
            conn.execute(
                "INSERT INTO datum_plane (datum_id, axis, coord, support_count, provenance) "
                "VALUES (?, ?, ?, ?, 'derived:cadence')",
                (datum_id, "XYZ"[ax], round(coord, 6), len(guids)))
            n_datums += 1
            for g in guids:
                b = box[g]
                faces = (b[2 * ax], b[2 * ax + 1])
                off = min((f - coord for f in faces), key=abs)   # closest face → signed offset
                conn.execute(
                    "INSERT OR IGNORE INTO rel_anchored (element_guid, datum_id, axis, offset_mm, provenance) "
                    "VALUES (?, ?, ?, ?, 'derived:cadence-snap')",
                    (g, datum_id, "XYZ"[ax], round(off * 1000, 3)))
                n_anchors += 1
    conn.commit()
    return n_datums, n_anchors


def derive_spans(conn, tol=0.05):
    """§SPANS (SPATIAL_DEPENDENCY_GRAPH.md): derive the `spans` edge — element stretches between two datums.

    An element SPANS on an axis when its min face is near one datum AND its max face is near a DIFFERENT
    datum (both within tol) — its bbox reaches across the datum interval (a girder between piers, a slab
    across gridlines). Reuses datum_plane (must be populated first by derive_datums_and_anchors). NON-INVENT,
    grep-clean: pure geometry. span_m = the HELD extent; the fold rule stretches it between the two datums.

    Returns rows written.
    """
    datums = {0: [], 1: [], 2: []}
    for did, ax, co in conn.execute("SELECT datum_id, axis, coord FROM datum_plane"):
        datums["XYZ".index(ax)].append((did, co))
    if not any(datums.values()):
        return 0
    rows = conn.execute("""
        SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
    """).fetchall()
    n = 0
    for guid, *b in rows:
        for ax in range(3):
            lo_face, hi_face = b[2 * ax], b[2 * ax + 1]
            lo = min(datums[ax], key=lambda d: abs(d[1] - lo_face), default=None)
            hi = min(datums[ax], key=lambda d: abs(d[1] - hi_face), default=None)
            if lo is None or hi is None:
                continue
            if abs(lo[1] - lo_face) > tol or abs(hi[1] - hi_face) > tol or lo[0] == hi[0]:
                continue
            d_lo, d_hi = (lo, hi) if lo[1] <= hi[1] else (hi, lo)
            conn.execute(
                "INSERT OR IGNORE INTO rel_spans (element_guid, axis, datum_lo_id, datum_hi_id, span_m, provenance) "
                "VALUES (?, ?, ?, ?, ?, 'derived:bbox-spans-datums')",
                (guid, "XYZ"[ax], d_lo[0], d_hi[0], round(hi_face - lo_face, 6)))
            n += 1
    conn.commit()
    return n


def extract_reference(ifc_path, output_path, classes=None, exclude=None,
                      dry_run=False, library_path=None, building_type=None,
                      skip_normalize=False):
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

    # S173: geom.iterator() returns metres natively (applies unit_scale
    # internally), regardless of the IFC file's native length unit.
    # No manual scaling needed. unit_scale is read for logging only.
    import ifcopenshell.util.unit as _ifcunit
    unit_scale = _ifcunit.calculate_unit_scale(ifc_file, "LENGTHUNIT")
    print(f"  §UNIT_SCALE {unit_scale} (iterator returns metres — no manual scaling)")

    # S168: Derive building_type from IFC filename if not provided
    if building_type is None:
        building_type = os.path.splitext(os.path.basename(ifc_path))[0]
        # Strip common suffixes
        for suffix in ("_extracted", "_merged", "_federated"):
            building_type = building_type.replace(suffix, "")

    print(f"  §BUILDING   {building_type}")
    # §KUL001: the label the Viewer groups by. The two existing residents use the T0_<name> form
    # (LTU_AHouse -> T0_LTU_AHouse x125,698; Terminal -> T0_Terminal x48,428), so match it rather
    # than invent a third convention. Written into elements_meta.building on every row below, and
    # mirrored into project_metadata, so a CLI-extracted DB opens in the Viewer with no post-process.
    _building_label = building_type if building_type.startswith("T0_") else "T0_" + building_type
    print(f"  §BUILDING_LABEL {_building_label}  (elements_meta.building, Viewer centres key)")
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
        # IfcSpace footprint AABB — tessellate the space solid ONCE (world coords) for its bounding box.
        # Enables habitable-AABB room qualification + per-room door proximity (SPATIAL_DEPENDENCY_GRAPH
        # room/storey design). World-coord AABB is normalized later alongside element_transforms.
        space_settings = ifcopenshell.geom.settings()
        space_settings.set(space_settings.USE_WORLD_COORDS, True)
        n_space_geom = 0
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
            cx = cy = cz = sx = sy = sz = None
            try:
                shp = ifcopenshell.geom.create_shape(space_settings, sp)
                v = shp.geometry.verts            # flat [x,y,z, x,y,z, ...]
                if v:
                    xs, ys, zs = v[0::3], v[1::3], v[2::3]
                    minx, maxx = min(xs), max(xs)
                    miny, maxy = min(ys), max(ys)
                    minz, maxz = min(zs), max(zs)
                    cx, cy, cz = (minx + maxx) / 2, (miny + maxy) / 2, (minz + maxz) / 2
                    sx, sy, sz = maxx - minx, maxy - miny, maxz - minz
                    n_space_geom += 1
            except Exception:
                pass                              # space without a usable Representation → AABB stays NULL
            conn.execute(
                "INSERT OR IGNORE INTO spatial_structure "
                "(guid, type, name, parent_guid, object_type, predefined_type, "
                "center_x, center_y, center_z, size_x, size_y, size_z) "
                "VALUES (?, 'IfcSpace', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (sp.GlobalId, sp.Name or sp.LongName, parent_guid, obj_type, predef,
                 cx, cy, cz, sx, sy, sz))
        if n_space_geom:
            print(f"  §SPACE-AABB: {n_space_geom} IfcSpace footprints tessellated (habitable area/height)")
        conn.commit()

    # Geometry settings — S169: LOCAL coords for canonical mesh deduplication.
    # World placement comes from shape.transformation matrix.
    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, False)
    settings.set(settings.WELD_VERTICES, True)

    # §LODHELL-FIX-2 (2026-07-27) — the "Tier 2: no boolean operations" fallback that used to be declared
    # here (`settings_no_bool` with DISABLE_BOOLEAN_RESULT, plus BOOL_DEPTH_THRESHOLD = 3) is DELETED, not
    # disabled. It was unreferenced dead code, and it must never be revived, for two measured reasons:
    #   1. It does not work. On ifcopenshell 0.8.4.post1, DISABLE_BOOLEAN_RESULT=True (setting readback
    #      confirmed True) still returns verts=0 faces=0 at PRODUCT level for the SampleCastle `kozijn`
    #      walls — the exact elements it was supposed to rescue.
    #   2. It would be WRONG even if it worked. An element whose body is fully removed by its own authored
    #      IfcRelVoidsElement opening is CORRECT as empty (see is_void_consumed()). Re-tessellating it
    #      without booleans resurrects an uncut wall the author deliberately voided and presents it as real
    #      geometry — inventing content, and a direct violation of the no-fake-LOD doctrine
    #      (docs/internal/WalkerDoctrine.md §11).
    # Full evidence: prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LODHELL-ROOTCAUSE FINDING 3.

    existing_hashes = set()
    next_id = 1
    imported = failed = simplified = bbox_fallback = 0
    # §LODHELL-FIX-1: empty-tessellation elements whose OWN authored opening consumed the whole body.
    # Expected, correct output — tracked and reported separately, never counted as a failure.
    void_consumed = 0
    void_guids = []
    mat_found = rgba_found = 0
    lib_geo_new = lib_igm_new = lib_prod_new = 0  # S168 counters
    ordinal_counter = {}  # S168: (ifc_class, storey) → next ordinal

    # ── S172: Geometry iterator (replaces per-element create_shape) ──────
    # The iterator has built-in C++ dedup, instancing, and caching.
    # It processes all elements in one pass, returning geometry + transform.
    import math

    t_iter_start = time.time()
    exclude_list = list(skip_classes)
    iterator = ifcopenshell.geom.iterator(settings, ifc_file, exclude=exclude_list)

    if not iterator.initialize():
        print("  §ITER WARNING: iterator.initialize() returned False — falling back")
        iterator = None

    # Build GUID→element lookup for metadata extraction
    guid_to_elem = {}
    for elem in ifc_file.by_type("IfcProduct"):
        guid_to_elem[elem.GlobalId] = elem

    # S173: Running stats for debug summary
    _cx_min = _cy_min = _cz_min = float('inf')
    _cx_max = _cy_max = _cz_max = float('-inf')
    _vmax_global = 0.0
    _rot_ok = _rot_fail = 0

    if iterator:
        print(f"  §ITER using geometry iterator (v{ifcopenshell.version}, built-in dedup)")
        while True:
            shape = iterator.get()
            elem = guid_to_elem.get(shape.guid)
            if elem is None:
                if not iterator.next():
                    break
                continue

            cls = shape.type  # IFC class
            try:
                geo = shape.geometry
                verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)

                rot_x = rot_y = rot_z = 0.0

                if len(verts) < 3 or len(faces) < 1:
                    # §LODHELL-FIX-1: an empty tessellation is NOT automatically a defect — classify it
                    # against the element's own authored openings before crying wolf (is_void_consumed()).
                    if is_void_consumed(elem, settings):
                        void_consumed += 1
                        void_guids.append((shape.guid, cls, getattr(elem, 'Name', None)))
                        if not iterator.next():
                            break
                        continue
                    # S185: parametric fallback is illegal — abort extraction
                    raise RuntimeError(
                        f"§ILLEGAL_PARAMETRIC_FALLBACK {cls} guid={shape.guid} — "
                        f"verts={len(verts)} faces={len(faces)}. "
                        f"Tessellation produced no geometry. "
                        f"Add to NON_GEOMETRIC_CLASSES or fix IFC source.")
                else:
                    # S173: iterator returns metres natively — no scaling
                    vblob = verts.astype(np.float32).tobytes()
                    fblob = faces.astype(np.int32).tobytes()

                    # Transform from iterator (4x4 column-major in v0.8)
                    mat_flat = list(shape.transformation.matrix)
                    mat4 = np.array(mat_flat, dtype=np.float64).reshape(4, 4).T
                    rot3 = mat4[:3, :3]
                    center = mat4[:3, 3]

                    # World-space bbox (from original verts + original centre)
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

                    # Euler rotation
                    sy = math.sqrt(rot3[0, 0]**2 + rot3[1, 0]**2)
                    if sy > 1e-6:
                        rot_x = math.atan2(rot3[2, 1], rot3[2, 2])
                        rot_y = math.atan2(-rot3[2, 0], sy)
                        rot_z = math.atan2(rot3[1, 0], rot3[0, 0])
                    else:
                        rot_x = math.atan2(-rot3[1, 2], rot3[1, 1])
                        rot_y = math.atan2(-rot3[2, 0], sy)
                        rot_z = 0.0

                    # S173: ROTATION TRUTH — at the event, compare Euler→matrix vs original rot3
                    if FINE:
                        a, b, c = rot_x, rot_y, rot_z
                        R_recon = np.array([
                            [math.cos(b)*math.cos(c), math.sin(a)*math.sin(b)*math.cos(c)-math.cos(a)*math.sin(c), math.cos(a)*math.sin(b)*math.cos(c)+math.sin(a)*math.sin(c)],
                            [math.cos(b)*math.sin(c), math.sin(a)*math.sin(b)*math.sin(c)+math.cos(a)*math.cos(c), math.cos(a)*math.sin(b)*math.sin(c)-math.sin(a)*math.cos(c)],
                            [-math.sin(b),             math.sin(a)*math.cos(b),                                      math.cos(a)*math.cos(b)]
                        ])
                        rot_err = np.abs(R_recon - rot3).max()
                        if rot_err > 1e-6:
                            _rot_fail += 1
                            if _rot_fail <= 3:
                                print(f"    §ROT_FAIL {shape.guid[:16]} err={rot_err:.6f} "
                                      f"euler=({rot_x:.4f},{rot_y:.4f},{rot_z:.4f})")
                        else:
                            _rot_ok += 1

                # S173: track running stats + log first 3 elements for diagnostics
                _vmax_local = np.abs(verts).max() if len(verts) > 0 else 0.0
                if _vmax_local > _vmax_global:
                    _vmax_global = _vmax_local
                _cx_min = min(_cx_min, float(center[0]))
                _cx_max = max(_cx_max, float(center[0]))
                _cy_min = min(_cy_min, float(center[1]))
                _cy_max = max(_cy_max, float(center[1]))
                _cz_min = min(_cz_min, float(center[2]))
                _cz_max = max(_cz_max, float(center[2]))

                if FINE and imported < 3:
                    print(f"    §SAMPLE[{imported}] {cls} "
                          f"centre=({float(center[0]):.2f},{float(center[1]):.2f},{float(center[2]):.2f})m "
                          f"vmax={_vmax_local:.3f}m "
                          f"bbox=[{float(minXYZ[0]):.2f},{float(maxXYZ[0]):.2f}]x"
                          f"[{float(minXYZ[1]):.2f},{float(maxXYZ[1]):.2f}]x"
                          f"[{float(minXYZ[2]):.2f},{float(maxXYZ[2]):.2f}]")

                ghash = geometry_hash(vblob, fblob)

                guid = shape.guid
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

                material_name = get_material_for_element(elem)
                material_rgba = get_colour_for_element(elem)
                if material_name:
                    mat_found += 1
                if material_rgba:
                    rgba_found += 1

                # S173: progress every 5000 elements (FINE only)
                if FINE and imported > 0 and imported % 5000 == 0:
                    elapsed = time.time() - t_iter_start
                    print(f"    §PROGRESS {imported:,} elements  "
                          f"{elapsed:.0f}s  {imported/max(elapsed,0.01):.0f} elem/s  "
                          f"fail={failed}")

                if not dry_run:
                    # Vertex/face counts — derive from BLOB size (always correct)
                    v_count = len(vblob) // 12   # float32 * 3 = 12 bytes/vertex
                    f_count = len(fblob) // 12   # int32 * 3  = 12 bytes/face

                    if ghash not in existing_hashes:
                        # S173: spot-check vertex scale for first 5 new hashes (FINE only)
                        if FINE and len(existing_hashes) < 5 and v_count > 3:
                            _sv = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
                            _vmax = np.abs(_sv).max()
                            print(f"    §MESH_SCALE hash={ghash[:12]} "
                                  f"vc={v_count} vmax={_vmax:.3f}m "
                                  f"centre=({float(center[0]):.2f},{float(center[1]):.2f},{float(center[2]):.2f})m "
                                  f"unit_scale={unit_scale}")

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
                        "storey, material_name, material_rgba, building) "
                        "VALUES (?,?,?,?,?,?,?,?,?,?)",
                        (eid, guid, discipline, cls, name, elem_type, storey,
                         material_name, material_rgba, _building_label))
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
                        "rotation_x, rotation_y, rotation_z, bbox_x, bbox_y, bbox_z, transform_source) "
                        "VALUES (?,?,?,?,?,?,?,?,?,?,'ifc_extract')",
                        (guid, float(center[0]), float(center[1]), float(center[2]),
                         float(rot_x), float(rot_y), float(rot_z),
                         float(maxXYZ[0]) - float(minXYZ[0]),
                         float(maxXYZ[1]) - float(minXYZ[1]),
                         float(maxXYZ[2]) - float(minXYZ[2])))
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
                if imported % 1000 == 0 and not dry_run:
                    conn.commit()
                    if lib_conn:
                        lib_conn.commit()

            except Exception as exc:
                failed += 1
                # §LODHELL-FIX-1: print EVERY real failure. The old `if failed <= 5` cap hid 60 of 65
                # events on SampleCastle — a genuine geometry loss would have been invisible among them.
                print(f"  §FAIL {cls} {getattr(elem, 'GlobalId', '?')}: {exc}")

            if not iterator.next():
                break

        t_iter_elapsed = time.time() - t_iter_start
        print(f"  §ITER done: {imported:,} elements in {t_iter_elapsed:.1f}s "
              f"({imported/max(t_iter_elapsed,0.01):.0f} elem/s)")
        # §LODHELL-FIX-1: expected output, so a summary + sample rather than one line per element.
        if void_consumed:
            print(f"  §VOID-CONSUMED {void_consumed} elements fully removed by their own authored "
                  f"IfcRelVoidsElement opening (correct — the filling element carries the geometry)")
            for _g, _c, _n in void_guids[:5]:
                print(f"    §VOID-CONSUMED {_c} {_g} name={_n}")
            if void_consumed > 5:
                print(f"    … {void_consumed - 5} more (full list is the rel_fills_host host set)")
        # S173: Pre-normalize coordinate summary (FINE only)
        if FINE and imported > 0:
            cx_span = _cx_max - _cx_min
            cy_span = _cy_max - _cy_min
            cz_span = _cz_max - _cz_min
            print(f"  §PRE_NORM centre X=[{_cx_min:.2f},{_cx_max:.2f}] "
                  f"Y=[{_cy_min:.2f},{_cy_max:.2f}] "
                  f"Z=[{_cz_min:.2f},{_cz_max:.2f}]")
            print(f"  §PRE_NORM span=({cx_span:.1f}, {cy_span:.1f}, {cz_span:.1f})m  "
                  f"vmax_global={_vmax_global:.3f}m")

    if not dry_run:
        conn.commit()
        if lib_conn:
            lib_conn.commit()

    # S169: Normalize building origin — subtract centroid so building is near (0,0,0)
    # Fixes georeferenced IFC files (UTM/national grid) that place elements at 100K+ metres
    # S173: SKIP when called from merge script — merge does its own post-merge normalization.
    # Per-discipline normalization destroys inter-discipline alignment.
    if skip_normalize:
        print(f"  §NORMALIZE skip (--skip-normalize: merge script handles post-merge normalization)")
    elif not dry_run and imported > 0:
        row = conn.execute("""
            SELECT AVG(center_x), AVG(center_y), MIN(center_z)
            FROM element_transforms
        """).fetchone()
        if row and row[0] is not None:
            ox, oy, oz = row[0], row[1], row[2]
            # Only normalize if significantly far from origin (> 100m)
            if abs(ox) > 100 or abs(oy) > 100 or abs(oz) > 100:
                print(f"  §NORMALIZE origin far from (0,0,0): centroid=({ox:.1f}, {oy:.1f}, {oz:.1f})")
                print(f"  §NORMALIZE subtracting offset to re-center building at origin")
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
                # IfcSpace footprint AABB shares the element frame → apply the same offset (size invariant)
                conn.execute("""
                    UPDATE spatial_structure
                    SET center_x = center_x - ?, center_y = center_y - ?, center_z = center_z - ?
                    WHERE center_x IS NOT NULL
                """, (ox, oy, oz))
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
                # Verify
                vrow = conn.execute("""
                    SELECT AVG(center_x), AVG(center_y), MIN(center_z)
                    FROM element_transforms
                """).fetchone()
                print(f"  §NORMALIZE offset=({ox:.1f}, {oy:.1f}, {oz:.1f}) stored in site_normalization")
                print(f"  §NORMALIZE after: centroid=({vrow[0]:.1f}, {vrow[1]:.1f}, {vrow[2]:.1f}) — should be near (0,0,0)")
            else:
                print(f"  §NORMALIZE skip — centroid=({ox:.1f}, {oy:.1f}, {oz:.1f}) already near origin")

        # §KUL001: project_metadata — the Viewer reads true_north_angle from it and logged
        # `no such table: project_metadata` on every CLI-extracted DB. Same shape the browser
        # importer writes (viewer/import_db_builder.js:28).
        conn.execute("CREATE TABLE IF NOT EXISTS project_metadata (key TEXT PRIMARY KEY, value TEXT)")
        for _k, _v in (("project_name", building_type),
                       ("building_name", _building_label),
                       ("source_file", os.path.basename(ifc_path)),
                       ("import_date", _dt.datetime.now(_dt.timezone.utc)
                                          .strftime("%Y-%m-%dT%H:%M:%S.000Z")),
                       ("true_north_angle", "0")):
            conn.execute("INSERT OR REPLACE INTO project_metadata (key,value) VALUES (?,?)", (_k, _v))
        conn.commit()
        _bn = conn.execute("SELECT COUNT(*) FROM elements_meta WHERE building IS NOT NULL").fetchone()[0]
        _bt = conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
        print(f"  §BUILDING_COL {_bn}/{_bt} rows carry building={_building_label}; "
              f"project_metadata rows={conn.execute('SELECT COUNT(*) FROM project_metadata').fetchone()[0]}")

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

    # ── §PATHB: recover the void/fill chain into rel_fills_host ─────────────
    if not dry_run:
        fills_rows, fills_filled = extract_rel_fills_host(ifc_file, conn)
        if fills_rows > 0:
            print(f"  §PATHB rel_fills_host: {fills_rows} host edges recovered "
                  f"({fills_filled} with a door/window filling, "
                  f"{fills_rows - fills_filled} open voids)")

    # ── §ABUTS: derive the face-touch adjacency edge from measured AABBs ────
    if not dry_run:
        adj_rows = derive_adjacency(conn)
        if adj_rows > 0:
            print(f"  §ABUTS rel_adjacency: {adj_rows} face-touch edges derived "
                  f"(measured shared-face contact, provenance=derived:face-touch)")

    # ── §ANCHORED: derive datum planes + the anchored-to edge from face cadence ──
    if not dry_run:
        n_datums, n_anchors = derive_datums_and_anchors(conn)
        if n_datums > 0:
            print(f"  §ANCHORED datum_plane: {n_datums} datums emerged from cadence, "
                  f"{n_anchors} anchored-to edges (provenance=derived:cadence)")

    # ── §SPANS: derive the spans edge (element bbox straddles two datums) ────
    if not dry_run:
        span_rows = derive_spans(conn)
        if span_rows > 0:
            print(f"  §SPANS rel_spans: {span_rows} span edges derived "
                  f"(element stretches between two datums, provenance=derived:bbox-spans-datums)")

    # Extract rich surface styles + material layers
    source_tag = f"EXTRACTED:{os.path.basename(ifc_path)}"
    styles = extract_surface_styles(ifc_file, source_tag)
    layers = extract_material_layers(ifc_file)
    if not dry_run:
        write_surface_styles(conn, styles)
        write_material_layers(conn, layers)

    # ── S173: PROOF BLOCK — self-checking summary ──────────────────────────
    # Each check prints PASS/FAIL with evidence. Read this block only.
    print(f"\n  {'─'*60}")
    print(f"  §PROOF {os.path.basename(ifc_path)}  elements={imported}  "
          f"failed={failed}  void_consumed={void_consumed}  bbox_fallback={bbox_fallback}")
    print(f"  {'─'*60}")

    _proof_pass = 0
    _proof_fail = 0

    def _check(name, ok, evidence):
        nonlocal _proof_pass, _proof_fail
        tag = "PASS" if ok else "FAIL"
        if ok:
            _proof_pass += 1
        else:
            _proof_fail += 1
        print(f"    {tag:4s}  {name:20s}  {evidence}")

    if not dry_run and imported > 0:
        # P1: SCALE — coordinates in metres (span 1-500m for real buildings)
        cr = conn.execute("""
            SELECT MIN(center_x), MAX(center_x),
                   MIN(center_y), MAX(center_y),
                   MIN(center_z), MAX(center_z)
            FROM element_transforms
        """).fetchone()
        span_x = abs(cr[1]-cr[0])
        span_y = abs(cr[3]-cr[2])
        span_z = abs(cr[5]-cr[4])
        span = max(span_x, span_y, span_z)
        _check("SCALE",
               0.5 < span < 500,
               f"span=({span_x:.1f},{span_y:.1f},{span_z:.1f})m  "
               f"X=[{cr[0]:.1f},{cr[1]:.1f}] Y=[{cr[2]:.1f},{cr[3]:.1f}] Z=[{cr[4]:.1f},{cr[5]:.1f}]")

        # P2: MESH_SCALE — vertex BLOBs in metres (vmax < 200m)
        _check("MESH_SCALE",
               0.001 < _vmax_global < 500,
               f"vmax_global={_vmax_global:.3f}m  unit_scale={unit_scale}")

        # P3: DEDUP — geometry deduplication working
        unique_hashes = conn.execute(
            "SELECT COUNT(DISTINCT geometry_hash) FROM element_instances").fetchone()[0]
        total_instances = conn.execute(
            "SELECT COUNT(*) FROM element_instances").fetchone()[0]
        reuse = total_instances / max(unique_hashes, 1)
        _check("DEDUP",
               unique_hashes > 0,
               f"{unique_hashes} hashes / {total_instances} instances  reuse={reuse:.1f}x")

        # P4: ROT_TRUTH — Euler→matrix matches original IFC matrix (checked at event)
        _check("ROT_TRUTH",
               _rot_fail == 0 and _rot_ok > 0,
               f"{_rot_ok} ok, {_rot_fail} fail  "
               f"(Euler decompose→reconstruct vs IFC iterator matrix)")

        # P5: FAIL_RATE — extraction failures below 1%.
        # §LODHELL-FIX-1: `failed` now excludes VOID-CONSUMED elements (see is_void_consumed) — those are a
        # correct authored outcome, and counting them kept this check permanently red on SampleCastle
        # (65/3648 = 1.78%), which is exactly how a REAL loss would have gone unnoticed.
        fail_pct = (failed / max(imported + failed, 1)) * 100
        _check("FAIL_RATE",
               fail_pct < 1.0,
               f"{failed}/{imported+failed} ({fail_pct:.2f}%)")

        # P9: VOID_CONSUMED — a fully-voided host is only correct if the thing that voided it is FILLED by
        # an element that DOES have geometry. A consumed host whose filling is also missing is a genuine
        # hole in the model — this is the check that would catch a real loss hiding behind the classifier.
        # (Open voids — filling_guid NULL, an authored hole with no door/window — are legitimate.)
        try:
            orphan = conn.execute("""
                SELECT COUNT(*) FROM rel_fills_host r
                WHERE r.filling_guid IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM element_instances i WHERE i.guid = r.filling_guid)
            """).fetchone()[0]
            total_fills = conn.execute(
                "SELECT COUNT(*) FROM rel_fills_host WHERE filling_guid IS NOT NULL").fetchone()[0]
        except sqlite3.OperationalError:
            orphan, total_fills = 0, 0
        _check("VOID_CONSUMED",
               orphan == 0,
               f"{void_consumed} hosts consumed by own opening; "
               f"{total_fills - orphan}/{total_fills} fillings have geometry, {orphan} orphaned")

        # P6: MATERIALS — some materials found
        _check("MATERIALS",
               mat_found > 0 or rgba_found > 0,
               f"{mat_found} names, {rgba_found} rgba, {len(styles)} styles, {len(layers)} layers")

    # P7: LIBRARY — writes succeeded (when using library mode)
    if lib_conn and not dry_run:
        total_geo = lib_conn.execute(
            "SELECT COUNT(*) FROM component_geometries").fetchone()[0]
        total_igm = lib_conn.execute(
            "SELECT COUNT(*) FROM I_Geometry_Map").fetchone()[0]
        _check("LIBRARY",
               lib_geo_new > 0 or total_geo > 0,
               f"+{lib_geo_new} new hashes, +{lib_igm_new} mappings  "
               f"totals: {total_geo} geo, {total_igm} map")

        # P8: LIBRARY_SCALE — spot-check vertex scale in library
        sample = lib_conn.execute("""
            SELECT vertices, vertex_count FROM component_geometries
            WHERE vertices IS NOT NULL AND vertex_count > 3
            ORDER BY RANDOM() LIMIT 5
        """).fetchall()
        lib_scale_ok = True
        worst_vmax = 0.0
        for vblob, vc in sample:
            if vblob:
                sv = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
                vm = np.abs(sv).max()
                if vm > worst_vmax:
                    worst_vmax = vm
                if vm > 500:
                    lib_scale_ok = False
        _check("LIBRARY_SCALE",
               lib_scale_ok,
               f"spot-check {len(sample)} meshes  worst_vmax={worst_vmax:.3f}m")

        # NOTE: RECONSTRUCT + IFC_TRUTH proofs run at LOAD TIME (stress_blender_test.py)
        # not here — extraction has the IFC so it can't fail here.

        lib_conn.close()

    print(f"  {'─'*60}")
    print(f"  §PROOF RESULT: {_proof_pass} PASS, {_proof_fail} FAIL")
    print(f"  {'─'*60}")
    # §LODHELL-FIX-1: a red §PROOF must FAIL THE RUN. Before this, the block printed FAIL and the process
    # still exited 0 — verified 2026-07-27 on SampleCastle (P5 FAIL, exit 0), which is why a permanently
    # red gate was tolerated for weeks. main() reads this and sets the exit status.
    global LAST_PROOF_FAIL
    LAST_PROOF_FAIL = _proof_fail

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
    parser.add_argument("--skip-normalize", action="store_true",
                        help="Skip centroid normalization (merge script does its own)")
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
                          building_type=args.building_type,
                          skip_normalize=args.skip_normalize)
        # §LODHELL-FIX-1: red §PROOF ⇒ non-zero exit. Exit code is not evidence on its own (Log Mandate),
        # but a gate that can never fail the run is not a gate at all.
        if LAST_PROOF_FAIL > 0:
            print(f"  §PROOF gate FAILED ({LAST_PROOF_FAIL} check(s)) — read the log above")
            sys.exit(1)
    else:
        print("ERROR: Must specify either:")
        print("  --ifc FILE -o OUTPUT       Full extraction (geometry + materials)")
        print("  --enrich --ifc FILE --ref DB   Enrich existing DB with materials")
        print("  --populate-placement --ref DB --library DB --building-type TYPE")
        sys.exit(1)


if __name__ == "__main__":
    main()
