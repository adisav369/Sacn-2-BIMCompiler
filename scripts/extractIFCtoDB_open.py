#!/usr/bin/env python3
"""
Open-filter IFC extractor — copy of extractIFCtoDB.py with:
  1. Fine-grained discipline map (VENT/HVAC/HEAT/PLB/SAN, not generic "MEP")
  2. Built-in unit-scale fix (mm→m for IFC2x3 Swedish/German files)
  3. Minimal blacklist (reverse filter: block only 5 truly non-extractable types)
  4. Forensic logging — structured abstract per class/discipline/storey

NEVER modify the original DAGCompiler/python/extractIFCtoDB.py.
This is a standalone copy for merged multi-discipline IFC files.

Usage:
  python3 scripts/extractIFCtoDB_open.py \
      --ifc DAGCompiler/lib/input/IFC/LTU_AHouse_merged.ifc \
      -o DAGCompiler/lib/input/LTU_AHouse_extracted.db
"""

import argparse
import hashlib
import os
import sqlite3
import sys
import time

import numpy as np


# ---------------------------------------------------------------------------
# Schema — identical to extractIFCtoDB.py (same consumer contract)
# ---------------------------------------------------------------------------

REFERENCE_SCHEMA = """
CREATE TABLE IF NOT EXISTS project_metadata (
    key TEXT PRIMARY KEY, value TEXT
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
CREATE VIRTUAL TABLE IF NOT EXISTS elements_rtree USING rtree(
    id, minX, maxX, minY, maxY, minZ, maxZ
);
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
    center_x REAL, center_y REAL, center_z REAL,
    rotation_x REAL DEFAULT 0, rotation_y REAL DEFAULT 0, rotation_z REAL DEFAULT 0,
    transform_source TEXT
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


# ---------------------------------------------------------------------------
# Blacklist — minimal.  Only types that are NEVER 3D geometry.
# Everything else with a Representation is extracted (reverse filter).
# ---------------------------------------------------------------------------

SKIP_CLASSES = {
    "IfcProject",
    "IfcSite",
    "IfcBuilding",
    "IfcBuildingStorey",
    "IfcSpace",                # spatial container — extracted to spatial_structure instead
    "IfcZone",
    "IfcSpatialZone",
    "IfcDistributionPort",     # logical connection, no mesh
    "IfcGrid",
    "IfcGridAxis",
    "IfcAnnotation",
    "IfcVirtualElement",
    "IfcRelSpaceBoundary",
    "IfcExternalSpatialElement",
}


# ---------------------------------------------------------------------------
# Fine-grained discipline map — preserves MEP sub-discipline identity
# ---------------------------------------------------------------------------

DISCIPLINE_MAP = {
    # --- Architectural ---
    "IfcWall": "ARC", "IfcWallStandardCase": "ARC",
    "IfcDoor": "ARC", "IfcWindow": "ARC",
    "IfcSlab": "ARC", "IfcRoof": "ARC",
    "IfcStair": "ARC", "IfcStairFlight": "ARC",
    "IfcRamp": "ARC", "IfcRampFlight": "ARC",
    "IfcRailing": "ARC", "IfcCurtainWall": "ARC",
    "IfcCovering": "ARC", "IfcFurnishingElement": "ARC",
    "IfcBuildingElementProxy": "ARC",
    "IfcOpeningElement": "VOID",

    # --- Structural ---
    "IfcColumn": "STR", "IfcBeam": "STR", "IfcMember": "STR",
    "IfcPlate": "STR", "IfcReinforcingBar": "STR",
    "IfcFooting": "STR", "IfcPile": "STR",
    "IfcTendon": "STR", "IfcTendonAnchor": "STR",

    # --- Duct / Ventilation ---
    "IfcDuctSegment": "VENT", "IfcDuctFitting": "VENT",
    "IfcAirTerminal": "VENT", "IfcAirTerminalBox": "VENT",
    "IfcDamper": "VENT", "IfcFilter": "VENT",
    "IfcDuctSilencer": "VENT",
    "IfcFan": "VENT",

    # --- Pipe / Plumbing ---
    "IfcPipeSegment": "PLB", "IfcPipeFitting": "PLB",
    "IfcValve": "PLB",
    "IfcSanitaryTerminal": "SAN",

    # --- Heating ---
    "IfcBoiler": "HEAT", "IfcHeatExchanger": "HEAT",
    "IfcSpaceHeater": "HEAT",

    # --- Cooling / HVAC ---
    "IfcChiller": "HVAC", "IfcCoolingTower": "HVAC",
    "IfcCooledBeam": "HVAC", "IfcEvaporativeCooler": "HVAC",
    "IfcCondenser": "HVAC", "IfcCompressor": "HVAC",
    "IfcUnitaryEquipment": "HVAC",

    # --- Electrical ---
    "IfcLightFixture": "ELEC", "IfcElectricAppliance": "ELEC",
    "IfcCableSegment": "ELEC", "IfcCableFitting": "ELEC",
    "IfcCableCarrierSegment": "ELEC", "IfcCableCarrierFitting": "ELEC",
    "IfcDistributionControlElement": "ELEC",
    "IfcElectricDistributionBoard": "ELEC",
    "IfcElectricMotor": "ELEC", "IfcElectricGenerator": "ELEC",
    "IfcOutlet": "ELEC", "IfcJunctionBox": "ELEC",
    "IfcSwitchingDevice": "ELEC", "IfcProtectiveDevice": "ELEC",

    # --- Fire Protection ---
    "IfcFireSuppressionTerminal": "FP", "IfcAlarm": "FP",
    "IfcSensor": "FP", "IfcController": "FP",
    "IfcSprayTerminal": "FP",

    # --- Generic flow (IFC2x3 fallback — use element_type heuristics) ---
    "IfcFlowTerminal": "MEP",
    "IfcFlowSegment": "MEP",
    "IfcFlowFitting": "MEP",
    "IfcFlowController": "MEP",
    "IfcFlowStorageDevice": "MEP",
    "IfcFlowMovingDevice": "MEP",
    "IfcFlowTreatmentDevice": "MEP",
    "IfcEnergyConversionDevice": "MEP",

    # --- Pump / Mechanical ---
    "IfcPump": "MECH", "IfcMotorConnection": "MECH",

    # --- Infrastructure (IFC4x3) ---
    "IfcGeographicElement": "GIS",
    "IfcRoad": "ROAD", "IfcRoadPart": "ROAD",
    "IfcKerb": "ROAD", "IfcPavement": "ROAD",
    "IfcRailway": "RAIL", "IfcRailwayPart": "RAIL",
    "IfcRail": "RAIL", "IfcTrackElement": "RAIL",
    "IfcBridge": "BRIDGE", "IfcBridgePart": "BRIDGE",
    "IfcTunnel": "TUNNEL", "IfcTunnelPart": "TUNNEL",
    "IfcMarineFacility": "MARINE", "IfcMarinePart": "MARINE",
    "IfcAlignment": "INFRA", "IfcLinearElement": "INFRA",
    "IfcEarthworksCut": "INFRA", "IfcEarthworksFill": "INFRA",
    "IfcCivilElement": "INFRA", "IfcFacility": "INFRA",
    "IfcFacilityPart": "INFRA",
}

# Swedish/German element_type keywords → discipline (for generic IfcFlow* in IFC2x3)
_ELEMENT_TYPE_HINTS = [
    # Order matters — first match wins
    ("kanal",   "VENT"),   # Swedish: duct/channel
    ("spiro",   "VENT"),   # Spiro spiral duct
    ("duct",    "VENT"),
    ("air",     "VENT"),
    ("avlopp",  "SAN"),    # Swedish: drain
    ("sanit",   "SAN"),
    ("drain",   "SAN"),
    ("vatten",  "PLB"),    # Swedish: water
    ("plumb",   "PLB"),
    ("copper",  "PLB"),
    ("pex",     "PLB"),
    ("värme",   "HEAT"),   # Swedish: heat
    ("radi",    "HEAT"),   # radiator
    ("heat",    "HEAT"),
    ("kyla",    "HVAC"),   # Swedish: cooling
    ("cool",    "HVAC"),
    ("kyl",     "HVAC"),
    ("fläkt",   "VENT"),   # Swedish: fan
    ("lampa",   "ELEC"),   # Swedish: lamp
    ("light",   "ELEC"),
    ("elec",    "ELEC"),
    ("cable",   "ELEC"),
    ("brand",   "FP"),     # Swedish: fire
    ("fire",    "FP"),
    ("sprinkl", "FP"),
]


def get_pset_discipline(element):
    """Read Pset_BIMSource.Discipline stamped during IFC merge (if present).

    Returns discipline string (e.g. "VENT") or None if Pset not found.
    This is the highest-priority discipline source — set by the merger
    which knows the original source filename.
    """
    try:
        for rel in element.IsDefinedBy:
            if rel.is_a("IfcRelDefinesByProperties"):
                pset = rel.RelatingPropertyDefinition
                if pset.is_a("IfcPropertySet") and pset.Name == "Pset_BIMSource":
                    for prop in pset.HasProperties:
                        if prop.Name == "Discipline" and prop.NominalValue:
                            val = str(prop.NominalValue.wrappedValue).strip().upper()
                            if val:
                                return val
    except (AttributeError, TypeError):
        pass
    return None


def infer_discipline(ifc_class, element_type=None, material_name=None, pset_disc=None):
    """Infer discipline with 3-tier priority:
       1. Pset_BIMSource.Discipline (stamped during merge — knows source file)
       2. IFC class map (specific classes like IfcDuctSegment)
       3. element_type/material keyword heuristic (for generic IfcFlow*)
    """
    # Priority 1: merger-stamped Pset (authoritative for merged IFCs)
    if pset_disc:
        return pset_disc

    # Priority 2: specific IFC class
    disc = DISCIPLINE_MAP.get(ifc_class)
    if disc and disc != "MEP":
        return disc

    # Priority 3: element_type / material keyword heuristic
    hint_sources = [
        (element_type or "").lower(),
        (material_name or "").lower(),
    ]
    for text in hint_sources:
        if not text:
            continue
        for keyword, hint_disc in _ELEMENT_TYPE_HINTS:
            if keyword in text:
                return hint_disc

    return disc or "ARC"


# ---------------------------------------------------------------------------
# Geometry helpers (copied from extractIFCtoDB.py — pristine logic)
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
    """Fallback: derive a minimal 1x1x1 box from the element's placement origin."""
    import ifcopenshell.util.placement as ifcplace
    try:
        m = ifcplace.get_local_placement(elem.ObjectPlacement)
        cx, cy, cz = float(m[0][3]), float(m[1][3]), float(m[2][3])
    except Exception:
        cx = cy = cz = 0.0
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
           np.array([cx - half, cy - half, cz - half]), \
           np.array([cx + half, cy + half, cz + half])


# ---------------------------------------------------------------------------
# IFC spatial helpers (copied from extractIFCtoDB.py — pristine logic)
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
# IFC material extraction (copied from extractIFCtoDB.py — pristine logic)
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
    """Extract RGBA colour string from IFC element via style chain."""
    try:
        if not element.Representation:
            return None
        for rep in element.Representation.Representations:
            for item in rep.Items:
                colour = _colour_from_item(item)
                if colour:
                    return colour
                if item.is_a("IfcMappedItem"):
                    mapped_rep = item.MappingSource.MappedRepresentation
                    for sub_item in mapped_rep.Items:
                        colour = _colour_from_item(sub_item)
                        if colour:
                            return colour
    except (AttributeError, TypeError):
        pass
    return None


def _colour_from_item(item):
    try:
        if not hasattr(item, 'StyledByItem') or not item.StyledByItem:
            return None
        for styled in item.StyledByItem:
            if not styled.is_a("IfcStyledItem"):
                continue
            for style_select in styled.Styles:
                colour = _colour_from_style(style_select)
                if colour:
                    return colour
    except (AttributeError, TypeError):
        pass
    return None


def _colour_from_style(style_select):
    try:
        if style_select.is_a("IfcSurfaceStyle"):
            return _colour_from_surface(style_select)
        if style_select.is_a("IfcPresentationStyleAssignment"):
            for s in style_select.Styles:
                if s.is_a("IfcSurfaceStyle"):
                    c = _colour_from_surface(s)
                    if c:
                        return c
    except (AttributeError, TypeError):
        pass
    return None


def _colour_from_surface(surface_style):
    try:
        for ss in surface_style.Styles:
            if ss.is_a("IfcSurfaceStyleRendering") or ss.is_a("IfcSurfaceStyleShading"):
                colour = ss.SurfaceColour
                if colour and colour.is_a("IfcColourRgb"):
                    r, g, b = float(colour.Red), float(colour.Green), float(colour.Blue)
                    transparency = 0.0
                    if hasattr(ss, 'Transparency') and ss.Transparency is not None:
                        transparency = float(ss.Transparency)
                    return f"{r:.3f},{g:.3f},{b:.3f},{1.0 - transparency:.3f}"
    except (AttributeError, TypeError):
        pass
    return None


# ---------------------------------------------------------------------------
# Surface style + material layer extraction
# ---------------------------------------------------------------------------

def extract_surface_styles(ifc_file, source_tag="EXTRACTED"):
    """Extract all IfcSurfaceStyle rows."""
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
                    'surface_r': float(sc.Red), 'surface_g': float(sc.Green),
                    'surface_b': float(sc.Blue),
                    'transparency': float(ss.Transparency) if ss.Transparency is not None else 0.0,
                    'reflectance_method': ss.ReflectanceMethod or 'NOTDEFINED',
                    'side': style.Side or 'BOTH', 'source': source_tag,
                    'specular_r': None, 'specular_g': None, 'specular_b': None,
                    'specular_ratio': None, 'specular_exponent': None,
                }
                spec = ss.SpecularColour
                if spec is not None:
                    if hasattr(spec, 'wrappedValue'):
                        row['specular_ratio'] = float(spec.wrappedValue)
                    elif hasattr(spec, 'Red'):
                        row['specular_r'] = float(spec.Red)
                        row['specular_g'] = float(spec.Green)
                        row['specular_b'] = float(spec.Blue)
                highlight = ss.SpecularHighlight
                if highlight is not None and hasattr(highlight, 'wrappedValue'):
                    row['specular_exponent'] = float(highlight.wrappedValue)
                rows.append(row)
                seen.add(name)
                break
            elif ss.is_a('IfcSurfaceStyleShading') and name not in seen:
                sc = ss.SurfaceColour
                if not sc or not sc.is_a('IfcColourRgb'):
                    continue
                rows.append({
                    'style_name': name,
                    'surface_r': float(sc.Red), 'surface_g': float(sc.Green),
                    'surface_b': float(sc.Blue),
                    'transparency': float(ss.Transparency) if hasattr(ss, 'Transparency') and ss.Transparency is not None else 0.0,
                    'reflectance_method': 'NOTDEFINED', 'side': style.Side or 'BOTH',
                    'source': source_tag,
                    'specular_r': None, 'specular_g': None, 'specular_b': None,
                    'specular_ratio': None, 'specular_exponent': None,
                })
                seen.add(name)
                break
    return rows


def extract_material_layers(ifc_file):
    """Extract all IfcMaterialLayerSet rows."""
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
                'layer_set_name': name, 'sequence': seq,
                'material_name': mat_name, 'thickness_m': thickness,
                'is_ventilated': is_vent,
            })
    return rows


# ---------------------------------------------------------------------------
# Unit scale detection + geometry correction (Hell_GeometryScale fix)
# ---------------------------------------------------------------------------

def detect_unit_scale(ifc_file):
    """Return the length unit scale factor (1.0 = metres, 0.001 = millimetres)."""
    import ifcopenshell.util.unit
    return ifcopenshell.util.unit.calculate_unit_scale(ifc_file, "LENGTHUNIT")


# ---------------------------------------------------------------------------
# Forensic logger — structured extraction report
# ---------------------------------------------------------------------------

class ForensicLog:
    """Accumulates extraction stats and prints a structured forensic abstract."""

    def __init__(self, ifc_path):
        self.ifc_path = ifc_path
        self.t0 = time.time()
        self.imported = 0
        self.failed = 0
        self.simplified = 0
        self.bbox_fallback = 0
        self.mat_found = 0
        self.rgba_found = 0
        self.heuristic_upgrades = 0  # MEP→specific discipline via heuristic
        self.pset_tagged = 0         # discipline from Pset_BIMSource (merger-stamped)
        self.by_class = {}    # ifc_class → count
        self.by_disc = {}     # discipline → count
        self.by_storey = {}   # storey → count
        self.by_tier = {"T1": 0, "T2": 0, "T3": 0}
        self.unit_scale = 1.0
        self.vertex_range = None  # (min_xyz, max_xyz) after scale

    def record(self, ifc_class, discipline, storey, tier):
        self.imported += 1
        self.by_class[ifc_class] = self.by_class.get(ifc_class, 0) + 1
        self.by_disc[discipline] = self.by_disc.get(discipline, 0) + 1
        self.by_storey[storey] = self.by_storey.get(storey, 0) + 1
        self.by_tier[tier] = self.by_tier.get(tier, 0) + 1

    def print_abstract(self, output_path, total_db_elements):
        elapsed = time.time() - self.t0
        sz = os.path.getsize(output_path) / (1024 * 1024) if os.path.exists(output_path) else 0

        print("\n" + "=" * 72)
        print("  EXTRACTION FORENSIC ABSTRACT")
        print("=" * 72)
        print(f"  Source:    {self.ifc_path}")
        print(f"  Output:    {output_path} ({sz:.1f} MB)")
        print(f"  Time:      {elapsed:.1f}s")
        print(f"  Unit:      scale={self.unit_scale} ({'metres' if abs(self.unit_scale - 1.0) < 1e-9 else 'scaled to metres'})")
        print(f"  Elements:  {self.imported} OK, {self.failed} failed")
        print(f"  Tiers:     T1={self.by_tier['T1']}  T2={self.by_tier['T2']}  T3={self.by_tier['T3']}")
        print(f"  Materials: {self.mat_found} names, {self.rgba_found} RGBA")
        if self.pset_tagged:
            print(f"  Pset_BIMSource: {self.pset_tagged} elements tagged by merger")
        if self.heuristic_upgrades:
            print(f"  Heuristic: {self.heuristic_upgrades} elements upgraded from generic MEP")

        print(f"\n  By discipline ({len(self.by_disc)}):")
        for disc, cnt in sorted(self.by_disc.items(), key=lambda x: -x[1]):
            print(f"    {disc:10s} {cnt:6d}")

        print(f"\n  By IFC class ({len(self.by_class)}):")
        for cls, cnt in sorted(self.by_class.items(), key=lambda x: -x[1]):
            print(f"    {cls:40s} {cnt:6d}")

        print(f"\n  By storey ({len(self.by_storey)}):")
        for st, cnt in sorted(self.by_storey.items(), key=lambda x: -x[1]):
            print(f"    {st:30s} {cnt:6d}")

        if self.vertex_range:
            lo, hi = self.vertex_range
            print(f"\n  World extent (after scale):")
            print(f"    X: {lo[0]:.3f} → {hi[0]:.3f} m")
            print(f"    Y: {lo[1]:.3f} → {hi[1]:.3f} m")
            print(f"    Z: {lo[2]:.3f} → {hi[2]:.3f} m")

        print("=" * 72)


# ---------------------------------------------------------------------------
# Main extraction
# ---------------------------------------------------------------------------

def extract(ifc_path, output_path, exclude=None):
    """Full extraction from merged IFC to reference DB.

    Open filter: extracts ALL IfcProduct subclasses with Representation,
    except SKIP_CLASSES.  Fine-grained discipline via DISCIPLINE_MAP +
    element_type/material heuristics.  Built-in unit scale correction.
    """
    import ifcopenshell
    import ifcopenshell.geom

    log = ForensicLog(ifc_path)

    skip_classes = SKIP_CLASSES | set(exclude or [])

    print(f"Opening IFC: {ifc_path}")
    ifc_file = ifcopenshell.open(ifc_path)

    # Detect unit scale
    unit_scale = detect_unit_scale(ifc_file)
    log.unit_scale = unit_scale
    if abs(unit_scale - 1.0) > 1e-9:
        print(f"  Unit scale: {unit_scale} — will convert geometry to metres")
    else:
        print(f"  Unit scale: 1.0 (metres, no conversion needed)")

    # Create fresh DB
    if os.path.exists(output_path):
        os.remove(output_path)
    conn = sqlite3.connect(output_path)
    conn.executescript(REFERENCE_SCHEMA)

    # --- Spatial structure ---
    print("  Extracting spatial structure...")
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

    # --- Geometry settings ---
    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)
    settings.set(settings.WELD_VERTICES, True)

    settings_no_bool = ifcopenshell.geom.settings()
    settings_no_bool.set(settings_no_bool.USE_WORLD_COORDS, True)
    settings_no_bool.set(settings_no_bool.WELD_VERTICES, True)
    settings_no_bool.set(settings_no_bool.DISABLE_BOOLEAN_RESULT, True)

    BOOL_DEPTH_THRESHOLD = 3

    existing_hashes = set()
    next_id = 1

    # Track world extent for forensic log
    world_min = np.array([1e30, 1e30, 1e30])
    world_max = np.array([-1e30, -1e30, -1e30])

    # --- Collect extractable classes ---
    all_classes = set()
    for elem in ifc_file.by_type("IfcProduct"):
        c = elem.is_a()
        if c not in skip_classes and elem.Representation is not None:
            all_classes.add(c)

    print(f"  Extracting {len(all_classes)} IFC classes...")

    for cls in sorted(all_classes):
        try:
            elements = [e for e in ifc_file.by_type(cls)
                        if e.is_a() == cls and e.Representation is not None]
        except RuntimeError:
            continue

        for elem in elements:
            try:
                # --- Tier selection ---
                use_bbox = False
                tier = "T1"
                depth = element_boolean_depth(elem)
                if depth > BOOL_DEPTH_THRESHOLD:
                    try:
                        shape = ifcopenshell.geom.create_shape(settings_no_bool, elem)
                        log.simplified += 1
                        tier = "T2"
                    except Exception:
                        use_bbox = True
                else:
                    try:
                        shape = ifcopenshell.geom.create_shape(settings, elem)
                    except Exception:
                        try:
                            shape = ifcopenshell.geom.create_shape(settings_no_bool, elem)
                            log.simplified += 1
                            tier = "T2"
                        except Exception:
                            use_bbox = True

                if use_bbox:
                    vblob, fblob, center, minXYZ, maxXYZ = bbox_from_placement(elem)
                    log.bbox_fallback += 1
                    tier = "T3"
                    v_centered = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
                    faces = np.frombuffer(fblob, dtype=np.int32).reshape(-1, 3)
                else:
                    geo = shape.geometry
                    verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                    faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)
                    if len(verts) < 3 or len(faces) < 1:
                        vblob, fblob, center, minXYZ, maxXYZ = bbox_from_placement(elem)
                        log.bbox_fallback += 1
                        tier = "T3"
                        v_centered = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
                        faces = np.frombuffer(fblob, dtype=np.int32).reshape(-1, 3)
                    else:
                        minXYZ = verts.min(axis=0)
                        maxXYZ = verts.max(axis=0)
                        center = (minXYZ + maxXYZ) / 2.0
                        v_centered = (verts - center).astype(np.float32)
                        fblob = faces.astype(np.int32).tobytes()

                # --- Unit scale correction (Hell_GeometryScale fix) ---
                if abs(unit_scale - 1.0) > 1e-9:
                    v_centered = (v_centered * unit_scale).astype(np.float32)
                    center = center * unit_scale
                    minXYZ = minXYZ * unit_scale
                    maxXYZ = maxXYZ * unit_scale

                vblob = v_centered.tobytes()

                # Track world extent
                world_min = np.minimum(world_min, minXYZ)
                world_max = np.maximum(world_max, maxXYZ)

                ghash = geometry_hash(vblob, fblob)

                guid = elem.GlobalId
                name = getattr(elem, "Name", None)
                storey = get_storey_for_element(elem)

                # Element type (for discipline heuristic)
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
                    log.mat_found += 1
                if material_rgba:
                    log.rgba_found += 1

                # Fine-grained discipline: Pset (from merge) → class → heuristic
                pset_disc = get_pset_discipline(elem)
                discipline = infer_discipline(cls, elem_type, material_name, pset_disc)
                if pset_disc:
                    log.pset_tagged += 1
                elif DISCIPLINE_MAP.get(cls) == "MEP" and discipline != "MEP":
                    log.heuristic_upgrades += 1

                # --- DB writes ---
                if ghash not in existing_hashes:
                    conn.execute(
                        "INSERT OR IGNORE INTO base_geometries "
                        "(geometry_hash, vertices, faces, vertex_count, face_count) "
                        "VALUES (?,?,?,?,?)",
                        (ghash, vblob, fblob, len(v_centered), len(faces)))
                    existing_hashes.add(ghash)

                eid = next_id
                next_id += 1
                conn.execute(
                    "INSERT OR IGNORE INTO elements_meta "
                    "(id, guid, discipline, ifc_class, element_name, element_type, "
                    "storey, material_name, material_rgba) VALUES (?,?,?,?,?,?,?,?,?)",
                    (eid, guid, discipline, cls, name, elem_type, storey,
                     material_name, material_rgba))
                conn.execute(
                    "INSERT OR IGNORE INTO elements_rtree "
                    "(id, minX, maxX, minY, maxY, minZ, maxZ) VALUES (?,?,?,?,?,?,?)",
                    (eid, float(minXYZ[0]), float(maxXYZ[0]),
                     float(minXYZ[1]), float(maxXYZ[1]),
                     float(minXYZ[2]), float(maxXYZ[2])))
                conn.execute(
                    "INSERT OR IGNORE INTO element_instances (guid, geometry_hash) "
                    "VALUES (?,?)", (guid, ghash))
                conn.execute(
                    "INSERT OR IGNORE INTO element_transforms "
                    "(guid, center_x, center_y, center_z, transform_source) "
                    "VALUES (?,?,?,?,'ifc_extract')",
                    (guid, float(center[0]), float(center[1]), float(center[2])))
                space = get_space_for_element(elem)
                if space:
                    conn.execute(
                        "INSERT OR IGNORE INTO rel_contained_in_space VALUES (?,?)",
                        (guid, space.GlobalId))

                log.record(cls, discipline, storey, tier)

            except Exception:
                log.failed += 1

    conn.commit()

    # --- IfcRelAggregates ---
    agg_count = 0
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
                        "INSERT OR IGNORE INTO rel_aggregates VALUES (?,?)",
                        (parent.GlobalId, child.GlobalId))
                    agg_count += 1
            except (AttributeError, TypeError):
                pass
        conn.commit()
    except RuntimeError:
        pass

    # --- Surface styles + material layers ---
    source_tag = f"EXTRACTED:{os.path.basename(ifc_path)}"
    styles = extract_surface_styles(ifc_file, source_tag)
    layers = extract_material_layers(ifc_file)
    for s in styles:
        conn.execute(
            "INSERT OR REPLACE INTO surface_styles "
            "(style_name, surface_r, surface_g, surface_b, transparency, "
            "specular_r, specular_g, specular_b, specular_ratio, specular_exponent, "
            "reflectance_method, side, source) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (s['style_name'], s['surface_r'], s['surface_g'], s['surface_b'],
             s['transparency'], s['specular_r'], s['specular_g'], s['specular_b'],
             s['specular_ratio'], s['specular_exponent'],
             s['reflectance_method'], s['side'], s['source']))
    for l in layers:
        conn.execute(
            "INSERT OR REPLACE INTO material_layers VALUES (?,?,?,?,?)",
            (l['layer_set_name'], l['sequence'], l['material_name'],
             l['thickness_m'], l['is_ventilated']))
    conn.commit()

    # --- project_metadata: camera target + extraction stats ---
    try:
        row = conn.execute("""
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
            conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_x', ?)", (str(round(cx, 2)),))
            conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_y', ?)", (str(round(cy, 2)),))
            conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_center_z', ?)", (str(round(cz, 2)),))
            conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('view_distance',  ?)", (str(round(vd, 1)),))
    except Exception:
        pass

    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('unit_scale', ?)", (str(unit_scale),))
    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('extractor', 'extractIFCtoDB_open')")
    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('source_file', ?)", (os.path.basename(ifc_path),))
    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('element_count', ?)", (str(log.imported),))
    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('surface_styles', ?)", (str(len(styles)),))
    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('material_layers', ?)", (str(len(layers)),))
    conn.execute("INSERT OR REPLACE INTO project_metadata VALUES ('aggregates', ?)", (str(agg_count),))
    conn.commit()

    # BlendMeshResolver — canonicalise geometry hashes before close
    # Spec: internal/BlendMeshResolver.md
    try:
        from blend_mesh_resolver import apply_mesh_redirects
        apply_mesh_redirects(conn,
                             caller="extractIFCtoDB_open",
                             db_name=os.path.basename(output_path))
    except Exception as e:
        print(f"§RESOLVER WARN  resolver failed (non-fatal): {e}")

    conn.close()

    # Forensic abstract
    if world_min[0] < 1e29:
        log.vertex_range = (world_min, world_max)
    log.print_abstract(output_path, log.imported)

    return log.imported


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Open-filter IFC extractor — fine-grained discipline + unit scale fix")
    parser.add_argument("--ifc", required=True, help="IFC source file path")
    parser.add_argument("-o", "--output", required=True, help="Output reference DB path")
    parser.add_argument("--exclude", help="Comma-separated IFC classes to additionally exclude")
    args = parser.parse_args()

    exclude = args.exclude.split(",") if args.exclude else []
    extract(args.ifc, args.output, exclude=exclude)


if __name__ == "__main__":
    main()
