# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/home/red1/bim-compiler/venv/bin/python3
"""
IFC Export Script for BIM Compiler
Converts Java-generated WallSpec/PipeSpec to valid IFC files.

CONFIG-DRIVEN: Receives typology configuration from Java.
All naming, patterns (G4, G7), and rules come from config - not hardcoded.

Usage:
    python export_to_ifc.py --wall <json> --output <ifc> --config <config.json>
    python export_to_ifc.py --pipe <json> --output <ifc> --config <config.json>
    python export_to_ifc.py --reimport <ifc>

JSON format for wall:
{
    "start": {"x": 0.0, "y": 0.0, "z": 0.0},
    "end": {"x": 10.0, "y": 0.0, "z": 0.0},
    "thickness": 0.15,
    "height": 3.0,
    "openings": [
        {
            "offset": 5.0,       // distance from wall start to opening CENTER
            "width": 0.9,        // opening width
            "height": 2.1,       // opening height
            "sill_height": 0.0   // distance from floor to bottom of opening
        }
    ]
}

Config format (from IFCExportConfig.java):
{
    "typology": "Terminal",
    "project_name": "Terminal Building",
    "site_name": "Airport Site",
    "building_name": "Terminal 1",
    "storey_name": "Ground Floor",
    "patterns": {
        "G4_connection": {...},
        "G7_opening": {...}
    }
}
"""

import sys
import json
import argparse
import math

import ifcopenshell
import ifcopenshell.api
import ifcopenshell.geom


# Default config (fallback if none provided - matches Terminal typology)
DEFAULT_CONFIG = {
    "typology": "Default",
    "project_name": "BIMCompiler Export",
    "site_name": "Site",
    "building_name": "Building",
    "storey_name": "Ground Floor",
    "patterns": {
        "G4_connection": {
            "wall_junction_overlap": 0.133,
            "pipe_insertion_CW": 0.017,
            "pipe_insertion_FP": 0.014,
            "pipe_insertion_LPG": 0.018,
            "pipe_insertion_SP": 0.042
        },
        "G7_opening": {
            "max_width_ratio": 0.90,
            "max_height_ratio": 0.95,
            "min_edge_distance": 0.100
        }
    },
    "rules": {
        "tolerance": 0.005,
        "mep_structure_clearance": 0.050
    }
}


def normalize(v):
    """Normalize a 3D vector."""
    length = math.sqrt(v[0]**2 + v[1]**2 + v[2]**2)
    if length < 1e-10:
        return (0, 0, 0)
    return (v[0]/length, v[1]/length, v[2]/length)


def cross(a, b):
    """Cross product of two 3D vectors."""
    return (
        a[1]*b[2] - a[2]*b[1],
        a[2]*b[0] - a[0]*b[2],
        a[0]*b[1] - a[1]*b[0]
    )


def load_config(config_path):
    """Load config from JSON file, merge with defaults."""
    if config_path is None:
        return DEFAULT_CONFIG

    try:
        with open(config_path) as f:
            user_config = json.load(f)

        # Deep merge with defaults
        config = DEFAULT_CONFIG.copy()
        for key, value in user_config.items():
            if isinstance(value, dict) and key in config:
                config[key] = {**config.get(key, {}), **value}
            else:
                config[key] = value

        return config
    except Exception as e:
        print(f"WARNING: Could not load config {config_path}: {e}", file=sys.stderr)
        return DEFAULT_CONFIG


def create_ifc_model(config):
    """Create a minimal valid IFC model with project structure."""
    model = ifcopenshell.file(schema="IFC4")

    # Project - use config for naming
    project = ifcopenshell.api.run("root.create_entity", model,
                                    ifc_class="IfcProject",
                                    name=config.get("project_name", "Project"))

    # Units - use meters (not millimeters which is the default)
    ifcopenshell.api.run("unit.assign_unit", model, length={"is_metric": True, "raw": "METRE"})

    # Geometric context
    context = ifcopenshell.api.run("context.add_context", model,
                                    context_type="Model")
    body_context = ifcopenshell.api.run("context.add_context", model,
                                         context_type="Model",
                                         context_identifier="Body",
                                         target_view="MODEL_VIEW",
                                         parent=context)

    # Spatial structure - use config for naming
    site = ifcopenshell.api.run("root.create_entity", model,
                                 ifc_class="IfcSite",
                                 name=config.get("site_name", "Site"))
    building = ifcopenshell.api.run("root.create_entity", model,
                                     ifc_class="IfcBuilding",
                                     name=config.get("building_name", "Building"))
    storey = ifcopenshell.api.run("root.create_entity", model,
                                   ifc_class="IfcBuildingStorey",
                                   name=config.get("storey_name", "Level 1"))

    # Hierarchy
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=project, products=[site])
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=site, products=[building])
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=building, products=[storey])

    return model, body_context, storey


def validate_opening(opening, wall_length, wall_height, config):
    """Validate opening against G7 constraints from config."""
    patterns = config.get("patterns", {}).get("G7_opening", {})
    max_width_ratio = patterns.get("max_width_ratio", 0.90)
    max_height_ratio = patterns.get("max_height_ratio", 0.95)
    min_edge = patterns.get("min_edge_distance", 0.100)

    width = opening.get("width", 0)
    height = opening.get("height", 0)
    offset = opening.get("offset", 0)

    # Check size ratios
    if width / wall_length > max_width_ratio:
        return False, f"Opening width ratio {width/wall_length:.2f} exceeds max {max_width_ratio}"
    if height / wall_height > max_height_ratio:
        return False, f"Opening height ratio {height/wall_height:.2f} exceeds max {max_height_ratio}"

    # Check edge distances
    if offset < min_edge:
        return False, f"Opening start {offset:.3f}m < min edge {min_edge:.3f}m"
    end_distance = wall_length - (offset + width)
    if end_distance < min_edge:
        return False, f"Opening end distance {end_distance:.3f}m < min edge {min_edge:.3f}m"

    return True, "OK"


def export_wall(spec, output_path, config):
    """Export a wall specification to IFC."""
    model, body_context, storey = create_ifc_model(config)
    typology = config.get("typology", "Default")

    # Parse coordinates
    start = (spec["start"]["x"], spec["start"]["y"], spec["start"]["z"])
    end = (spec["end"]["x"], spec["end"]["y"], spec["end"]["z"])
    thickness = spec["thickness"]
    height = spec["height"]
    openings = spec.get("openings", [])

    # Calculate wall length and direction
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    length = math.sqrt(dx*dx + dy*dy)

    if length < 0.001:
        print("ERROR: Wall has zero length", file=sys.stderr)
        return False

    # Direction angle for placement
    angle = math.atan2(dy, dx)

    # Validate openings against config patterns
    for i, opening in enumerate(openings):
        valid, msg = validate_opening(opening, length, height, config)
        if not valid:
            print(f"WARNING: Opening {i} validation: {msg}", file=sys.stderr)

    # Create wall entity with typology-aware naming
    wall_name = f"{typology} Wall" if typology != "Default" else "Generated Wall"
    wall = ifcopenshell.api.run("root.create_entity", model,
                                 ifc_class="IfcWall",
                                 name=wall_name)

    # Add wall geometry using SweptSolid
    representation = ifcopenshell.api.run("geometry.add_wall_representation", model,
                                           context=body_context,
                                           length=length,
                                           height=height,
                                           thickness=thickness)

    ifcopenshell.api.run("geometry.assign_representation", model,
                          product=wall, representation=representation)

    # Place wall at start point with rotation
    cos_a = math.cos(angle)
    sin_a = math.sin(angle)

    # Local Y direction in world coords is (-sin_a, cos_a)
    # Offset by -thickness/2 in local Y to center
    half_t = thickness / 2
    offset_x = -(-sin_a) * half_t
    offset_y = -(cos_a) * half_t

    matrix = [
        [cos_a, -sin_a, 0, start[0] + offset_x],
        [sin_a, cos_a, 0, start[1] + offset_y],
        [0, 0, 1, start[2]],
        [0, 0, 0, 1]
    ]

    ifcopenshell.api.run("geometry.edit_object_placement", model,
                          product=wall, matrix=matrix)

    # Create openings (if any)
    for i, opening_spec in enumerate(openings):
        create_wall_opening(model, body_context, wall, opening_spec, i,
                           length, thickness, angle, start, config)

    # Assign to storey
    ifcopenshell.api.run("spatial.assign_container", model,
                          relating_structure=storey, products=[wall])

    # Write file
    model.write(output_path)
    print(f"SUCCESS: Exported wall to {output_path}")
    print(f"  Typology: {typology}")
    print(f"  Length: {length:.3f}m, Height: {height:.3f}m, Thickness: {thickness:.3f}m")
    print(f"  Openings: {len(openings)}")

    return True


def create_wall_opening(model, body_context, wall, opening_spec, index,
                        wall_length, wall_thickness, wall_angle, wall_start, config):
    """Create an opening in a wall using IfcOpeningElement."""
    offset = opening_spec.get("offset", 0)
    width = opening_spec.get("width", 0.9)
    height = opening_spec.get("height", 2.1)
    sill_height = opening_spec.get("sill_height", 0)

    # Create opening element
    opening = ifcopenshell.api.run("root.create_entity", model,
                                    ifc_class="IfcOpeningElement",
                                    name=f"Opening_{index}")

    # Opening geometry - box that cuts through wall
    # Make it slightly larger than wall thickness to ensure full cut
    cut_depth = wall_thickness * 1.2

    # Create rectangular profile for opening
    profile = model.create_entity("IfcRectangleProfileDef",
                                   ProfileType="AREA",
                                   XDim=width,
                                   YDim=cut_depth)

    # Extrusion direction (up)
    extrusion_dir = model.create_entity("IfcDirection",
                                         DirectionRatios=(0.0, 0.0, 1.0))

    # Profile position
    profile_pos = model.create_entity("IfcAxis2Placement3D",
                                       Location=model.create_entity("IfcCartesianPoint",
                                                                     Coordinates=(0.0, 0.0, 0.0)))

    # Create extruded solid
    solid = model.create_entity("IfcExtrudedAreaSolid",
                                 SweptArea=profile,
                                 Position=profile_pos,
                                 ExtrudedDirection=extrusion_dir,
                                 Depth=height)

    # Shape representation
    shape_rep = model.create_entity("IfcShapeRepresentation",
                                     ContextOfItems=body_context,
                                     RepresentationIdentifier="Body",
                                     RepresentationType="SweptSolid",
                                     Items=[solid])

    product_shape = model.create_entity("IfcProductDefinitionShape",
                                         Representations=[shape_rep])

    opening.Representation = product_shape

    # Position opening relative to wall
    # Opening position is along wall direction + perpendicular offset to center in wall
    cos_a = math.cos(wall_angle)
    sin_a = math.sin(wall_angle)

    # Position along wall: offset is distance to opening CENTER (matches Java OpeningSpec)
    pos_along = offset
    pos_x = wall_start[0] + pos_along * cos_a
    pos_y = wall_start[1] + pos_along * sin_a
    pos_z = wall_start[2] + sill_height

    # Rotation: opening local X is along wall, local Y perpendicular
    matrix = [
        [cos_a, -sin_a, 0, pos_x],
        [sin_a, cos_a, 0, pos_y],
        [0, 0, 1, pos_z],
        [0, 0, 0, 1]
    ]

    ifcopenshell.api.run("geometry.edit_object_placement", model,
                          product=opening, matrix=matrix)

    # Create void relationship (opening cuts wall)
    # Uses feature.add_feature API (ifcopenshell 0.8+)
    ifcopenshell.api.run("feature.add_feature", model,
                          feature=opening, element=wall)

    return opening


def export_pipe(spec, output_path, config):
    """Export a pipe specification to IFC."""
    model, body_context, storey = create_ifc_model(config)
    typology = config.get("typology", "Default")

    # Parse coordinates
    start = (spec["start"]["x"], spec["start"]["y"], spec["start"]["z"])
    end = (spec["end"]["x"], spec["end"]["y"], spec["end"]["z"])
    diameter = spec["diameter"]
    discipline = spec.get("discipline", "FP")

    # Calculate pipe length and direction
    direction = (end[0] - start[0], end[1] - start[1], end[2] - start[2])
    length = math.sqrt(direction[0]**2 + direction[1]**2 + direction[2]**2)

    if length < 0.001:
        print("ERROR: Pipe has zero length", file=sys.stderr)
        return False

    direction = normalize(direction)

    # Create pipe entity with typology-aware naming
    pipe_name = f"{typology} Pipe ({discipline})" if typology != "Default" else f"Generated Pipe ({discipline})"
    pipe = ifcopenshell.api.run("root.create_entity", model,
                                 ifc_class="IfcPipeSegment",
                                 name=pipe_name)

    # Create circular profile for pipe
    radius = diameter / 2

    profile = model.create_entity("IfcCircleProfileDef",
                                   ProfileType="AREA",
                                   Radius=radius)

    # Find a perpendicular vector for local X axis
    if abs(direction[2]) < 0.9:
        perp = cross(direction, (0, 0, 1))
    else:
        perp = cross(direction, (1, 0, 0))
    perp = normalize(perp)

    # Local Y is cross of Z (direction) and X (perp)
    local_y = cross(direction, perp)

    # Create the extrusion direction
    extrusion_direction = model.create_entity("IfcDirection",
                                               DirectionRatios=(0.0, 0.0, 1.0))

    # Position for the profile
    profile_position = model.create_entity("IfcAxis2Placement3D",
                                            Location=model.create_entity("IfcCartesianPoint",
                                                                          Coordinates=(0.0, 0.0, 0.0)))

    # Create the extruded solid
    solid = model.create_entity("IfcExtrudedAreaSolid",
                                 SweptArea=profile,
                                 Position=profile_position,
                                 ExtrudedDirection=extrusion_direction,
                                 Depth=length)

    # Create shape representation
    shape_rep = model.create_entity("IfcShapeRepresentation",
                                     ContextOfItems=body_context,
                                     RepresentationIdentifier="Body",
                                     RepresentationType="SweptSolid",
                                     Items=[solid])

    # Create product definition shape
    product_shape = model.create_entity("IfcProductDefinitionShape",
                                         Representations=[shape_rep])

    pipe.Representation = product_shape

    # Place pipe
    z_axis = direction
    x_axis = perp
    y_axis = local_y

    matrix = [
        [x_axis[0], y_axis[0], z_axis[0], start[0]],
        [x_axis[1], y_axis[1], z_axis[1], start[1]],
        [x_axis[2], y_axis[2], z_axis[2], start[2]],
        [0, 0, 0, 1]
    ]

    ifcopenshell.api.run("geometry.edit_object_placement", model,
                          product=pipe, matrix=matrix)

    # Assign to storey
    ifcopenshell.api.run("spatial.assign_container", model,
                          relating_structure=storey, products=[pipe])

    # Write file
    model.write(output_path)
    print(f"SUCCESS: Exported pipe to {output_path}")
    print(f"  Typology: {typology}")
    print(f"  Length: {length:.3f}m, Diameter: {diameter*1000:.1f}mm, Discipline: {discipline}")

    return True


def reimport_and_extract_bbox(ifc_path):
    """
    Reimport an IFC file and extract bounding boxes.
    Returns JSON with element bboxes for validation.
    """
    model = ifcopenshell.open(ifc_path)

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)

    results = []

    for element in model.by_type("IfcProduct"):
        if element.is_a("IfcSpatialStructureElement"):
            continue
        try:
            shape = ifcopenshell.geom.create_shape(settings, element)
            verts = shape.geometry.verts

            if len(verts) == 0:
                continue

            xs = verts[0::3]
            ys = verts[1::3]
            zs = verts[2::3]

            bbox = {
                "guid": element.GlobalId,
                "type": element.is_a(),
                "name": element.Name or "",
                "minX": min(xs),
                "maxX": max(xs),
                "minY": min(ys),
                "maxY": max(ys),
                "minZ": min(zs),
                "maxZ": max(zs),
                "vertex_count": len(verts) // 3
            }
            results.append(bbox)

        except Exception as e:
            print(f"WARNING: Could not extract geometry for {element.GlobalId}: {e}",
                  file=sys.stderr)

    print(json.dumps(results, indent=2))
    return results


def main():
    parser = argparse.ArgumentParser(description="BIM Compiler IFC Export (Config-Driven)")
    parser.add_argument("--wall", help="JSON file with wall specification")
    parser.add_argument("--pipe", help="JSON file with pipe specification")
    parser.add_argument("--reimport", help="IFC file to reimport and extract bbox")
    parser.add_argument("--output", "-o", help="Output IFC file path")
    parser.add_argument("--config", "-c", help="Config JSON from Java (typology, patterns)")

    args = parser.parse_args()

    # Load config (from file or use defaults)
    config = load_config(args.config)

    if args.wall:
        if not args.output:
            print("ERROR: --output required for --wall", file=sys.stderr)
            sys.exit(1)
        with open(args.wall) as f:
            spec = json.load(f)
        success = export_wall(spec, args.output, config)
        sys.exit(0 if success else 1)

    elif args.pipe:
        if not args.output:
            print("ERROR: --output required for --pipe", file=sys.stderr)
            sys.exit(1)
        with open(args.pipe) as f:
            spec = json.load(f)
        success = export_pipe(spec, args.output, config)
        sys.exit(0 if success else 1)

    elif args.reimport:
        reimport_and_extract_bbox(args.reimport)

    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
