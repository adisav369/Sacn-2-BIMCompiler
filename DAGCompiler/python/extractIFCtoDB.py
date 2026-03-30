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
  python3 DAGCompiler/python/extract.py \
      --ifc DAGCompiler/lib/input/Ifc4_SampleHouse.ifc \
      -o DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db

  # Populate I_Element_Extraction material columns from enriched reference DB
  python3 DAGCompiler/python/extract.py \
      --populate-placement \
      --ref DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
      --library library/component_library.db \
      --building-type Ifc4_SampleHouse

  # Enrich existing DB with materials only (no geometry re-extraction)
  python3 DAGCompiler/python/extract.py \
      --enrich \
      --ifc DAGCompiler/lib/input/Ifc4_SampleHouse.ifc \
      --ref DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db

  # Dry-run (report only)
  python3 DAGCompiler/python/extract.py \
      --ifc DAGCompiler/lib/input/Ifc4_SampleHouse.ifc \
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
    "IfcCovering", "IfcOpeningElement",
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
# Geometry helpers
# ---------------------------------------------------------------------------

def geometry_hash(vertices_blob, faces_blob):
    """SHA256-based 16-char hash of centered geometry."""
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]


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

def extract_reference(ifc_path, output_path, classes=None, exclude=None, dry_run=False):
    """Full extraction from IFC to reference DB with geometry + materials."""
    import ifcopenshell
    import ifcopenshell.geom

    if classes is None:
        classes = REFERENCE_CLASSES
    if exclude is None:
        exclude = []

    print(f"Opening IFC: {ifc_path}")
    ifc_file = ifcopenshell.open(ifc_path)

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

    # Geometry settings
    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)
    settings.set(settings.WELD_VERTICES, True)

    existing_hashes = set()
    next_id = 1
    imported = failed = 0
    mat_found = rgba_found = 0

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
                        "(guid, center_x, center_y, center_z, transform_source) "
                        "VALUES (?,?,?,?,'ifc_extract')",
                        (guid, float(center[0]), float(center[1]), float(center[2])))
                    space = get_space_for_element(elem)
                    if space:
                        conn.execute(
                            "INSERT OR IGNORE INTO rel_contained_in_space VALUES (?,?)",
                            (guid, space.GlobalId))

                imported += 1
            except Exception:
                failed += 1

    if not dry_run:
        conn.commit()

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

    print(f"  Elements:    {imported} (failed: {failed})")
    print(f"  Materials:   {mat_found} names, {rgba_found} RGBA colours")
    print(f"  Surface styles: {len(styles)}, Material layers: {len(layers)}")

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
    parser.add_argument("--library", help="Component library DB path (for --populate-placement)")
    parser.add_argument("--building-type", help="Building type key (for --populate-placement)")
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
        extract_reference(args.ifc, args.output, exclude=exclude, dry_run=args.dry_run)
    else:
        print("ERROR: Must specify either:")
        print("  --ifc FILE -o OUTPUT       Full extraction (geometry + materials)")
        print("  --enrich --ifc FILE --ref DB   Enrich existing DB with materials")
        print("  --populate-placement --ref DB --library DB --building-type TYPE")
        sys.exit(1)


if __name__ == "__main__":
    main()
