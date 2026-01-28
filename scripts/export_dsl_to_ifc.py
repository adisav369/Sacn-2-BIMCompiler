#!/home/red1/bim-compiler/venv/bin/python3
"""
DSL-to-IFC Export Script
Exports compiled DSL storey (walls + spaces + sprinklers) to valid IFC file.

Usage:
    python export_dsl_to_ifc.py --storey <json> --output <ifc> --config <config.json>

Storey JSON format:
{
    "storey_name": "Ground",
    "storey_height": 2.8,
    "walls": [...],
    "spaces": [...],
    "sprinkler_grids": [...]
}
"""

import sys
import json
import argparse
import math
import struct
import sqlite3
from pathlib import Path

import ifcopenshell
import ifcopenshell.api
import ifcopenshell.geom

# Component library path
LIBRARY_DB = Path(__file__).parent.parent / "library" / "component_library.db"


def load_config(config_path):
    """Load config from JSON file."""
    if config_path is None:
        return {"typology": "Default", "project_name": "BIM Compiler"}

    try:
        with open(config_path) as f:
            return json.load(f)
    except Exception as e:
        print(f"WARNING: Could not load config: {e}", file=sys.stderr)
        return {"typology": "Default", "project_name": "BIM Compiler"}


def create_ifc_model(config, storey_name):
    """Create IFC model with project structure."""
    model = ifcopenshell.file(schema="IFC4")

    # Project
    project = ifcopenshell.api.run("root.create_entity", model,
                                    ifc_class="IfcProject",
                                    name=config.get("project_name", "BIM Compiler"))

    # Units - meters
    ifcopenshell.api.run("unit.assign_unit", model,
                          length={"is_metric": True, "raw": "METRE"})

    # Geometric context
    context = ifcopenshell.api.run("context.add_context", model,
                                    context_type="Model")
    body_context = ifcopenshell.api.run("context.add_context", model,
                                         context_type="Model",
                                         context_identifier="Body",
                                         target_view="MODEL_VIEW",
                                         parent=context)

    # Spatial structure
    site = ifcopenshell.api.run("root.create_entity", model,
                                 ifc_class="IfcSite",
                                 name=config.get("site_name", "Site"))
    building = ifcopenshell.api.run("root.create_entity", model,
                                     ifc_class="IfcBuilding",
                                     name=config.get("building_name", "Building"))
    storey = ifcopenshell.api.run("root.create_entity", model,
                                   ifc_class="IfcBuildingStorey",
                                   name=storey_name)

    # Hierarchy
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=project, products=[site])
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=site, products=[building])
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=building, products=[storey])

    return model, body_context, storey


def create_wall(model, body_context, storey, wall_spec, index):
    """Create a wall with openings."""
    start = (wall_spec["start"]["x"], wall_spec["start"]["y"], wall_spec["start"]["z"])
    end = (wall_spec["end"]["x"], wall_spec["end"]["y"], wall_spec["end"]["z"])
    thickness = wall_spec["thickness"]
    height = wall_spec["height"]

    # Calculate length and direction
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    length = math.sqrt(dx*dx + dy*dy)

    if length < 0.001:
        return None

    angle = math.atan2(dy, dx)

    # Create wall entity
    wall = ifcopenshell.api.run("root.create_entity", model,
                                 ifc_class="IfcWall",
                                 name=f"Wall_{index}")

    # Add geometry
    representation = ifcopenshell.api.run("geometry.add_wall_representation", model,
                                           context=body_context,
                                           length=length,
                                           height=height,
                                           thickness=thickness)

    ifcopenshell.api.run("geometry.assign_representation", model,
                          product=wall, representation=representation)

    # Place wall
    cos_a = math.cos(angle)
    sin_a = math.sin(angle)
    half_t = thickness / 2
    offset_x = sin_a * half_t
    offset_y = -cos_a * half_t

    matrix = [
        [cos_a, -sin_a, 0, start[0] + offset_x],
        [sin_a, cos_a, 0, start[1] + offset_y],
        [0, 0, 1, start[2]],
        [0, 0, 0, 1]
    ]

    ifcopenshell.api.run("geometry.edit_object_placement", model,
                          product=wall, matrix=matrix)

    # Create openings
    for i, opening_spec in enumerate(wall_spec.get("openings", [])):
        create_opening(model, body_context, wall, opening_spec, i,
                      length, thickness, angle, start)

    # Assign to storey
    ifcopenshell.api.run("spatial.assign_container", model,
                          relating_structure=storey, products=[wall])

    return wall


def create_opening(model, body_context, wall, opening_spec, index,
                   wall_length, wall_thickness, wall_angle, wall_start):
    """Create an opening in a wall."""
    offset = opening_spec.get("offset", 0)
    width = opening_spec.get("width", 0.9)
    height = opening_spec.get("height", 2.1)
    sill_height = opening_spec.get("sill_height", 0)
    opening_type = opening_spec.get("type", "IFC_DOOR")

    # Create opening element
    opening = ifcopenshell.api.run("root.create_entity", model,
                                    ifc_class="IfcOpeningElement",
                                    name=f"Opening_{index}")

    # Opening geometry
    cut_depth = wall_thickness * 1.2

    profile = model.create_entity("IfcRectangleProfileDef",
                                   ProfileType="AREA",
                                   XDim=width,
                                   YDim=cut_depth)

    extrusion_dir = model.create_entity("IfcDirection",
                                         DirectionRatios=(0.0, 0.0, 1.0))

    profile_pos = model.create_entity("IfcAxis2Placement3D",
                                       Location=model.create_entity("IfcCartesianPoint",
                                                                     Coordinates=(0.0, 0.0, 0.0)))

    solid = model.create_entity("IfcExtrudedAreaSolid",
                                 SweptArea=profile,
                                 Position=profile_pos,
                                 ExtrudedDirection=extrusion_dir,
                                 Depth=height)

    shape_rep = model.create_entity("IfcShapeRepresentation",
                                     ContextOfItems=body_context,
                                     RepresentationIdentifier="Body",
                                     RepresentationType="SweptSolid",
                                     Items=[solid])

    product_shape = model.create_entity("IfcProductDefinitionShape",
                                         Representations=[shape_rep])

    opening.Representation = product_shape

    # Position opening
    cos_a = math.cos(wall_angle)
    sin_a = math.sin(wall_angle)

    pos_along = offset
    pos_x = wall_start[0] + pos_along * cos_a
    pos_y = wall_start[1] + pos_along * sin_a
    pos_z = wall_start[2] + sill_height

    matrix = [
        [cos_a, -sin_a, 0, pos_x],
        [sin_a, cos_a, 0, pos_y],
        [0, 0, 1, pos_z],
        [0, 0, 0, 1]
    ]

    ifcopenshell.api.run("geometry.edit_object_placement", model,
                          product=opening, matrix=matrix)

    # Create void relationship
    ifcopenshell.api.run("feature.add_feature", model,
                          feature=opening, element=wall)

    return opening


def create_space(model, body_context, storey, space_spec):
    """Create an IfcSpace for a room."""
    name = space_spec["name"]
    room_type = space_spec["type"]
    min_x = space_spec["min_x"]
    max_x = space_spec["max_x"]
    min_y = space_spec["min_y"]
    max_y = space_spec["max_y"]
    height = space_spec["height"]

    width = max_x - min_x
    depth = max_y - min_y

    # Create space entity
    space = ifcopenshell.api.run("root.create_entity", model,
                                  ifc_class="IfcSpace",
                                  name=name)

    # Set space type
    space.LongName = f"{room_type} - {name}"
    space.Description = f"OmniClass: {space_spec.get('omniclass', '')}"

    # Create space geometry (simple box)
    profile = model.create_entity("IfcRectangleProfileDef",
                                   ProfileType="AREA",
                                   XDim=width,
                                   YDim=depth)

    extrusion_dir = model.create_entity("IfcDirection",
                                         DirectionRatios=(0.0, 0.0, 1.0))

    profile_pos = model.create_entity("IfcAxis2Placement3D",
                                       Location=model.create_entity("IfcCartesianPoint",
                                                                     Coordinates=(0.0, 0.0, 0.0)))

    solid = model.create_entity("IfcExtrudedAreaSolid",
                                 SweptArea=profile,
                                 Position=profile_pos,
                                 ExtrudedDirection=extrusion_dir,
                                 Depth=height)

    shape_rep = model.create_entity("IfcShapeRepresentation",
                                     ContextOfItems=body_context,
                                     RepresentationIdentifier="Body",
                                     RepresentationType="SweptSolid",
                                     Items=[solid])

    product_shape = model.create_entity("IfcProductDefinitionShape",
                                         Representations=[shape_rep])

    space.Representation = product_shape

    # Position space
    center_x = (min_x + max_x) / 2
    center_y = (min_y + max_y) / 2

    matrix = [
        [1, 0, 0, center_x],
        [0, 1, 0, center_y],
        [0, 0, 1, 0],
        [0, 0, 0, 1]
    ]

    ifcopenshell.api.run("geometry.edit_object_placement", model,
                          product=space, matrix=matrix)

    # Assign to storey (spaces use aggregate, not contain)
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=storey, products=[space])

    return space


def get_sprinkler_geometry(geometry_hash: str):
    """Load sprinkler geometry from library."""
    if not LIBRARY_DB.exists():
        return None, None

    conn = sqlite3.connect(LIBRARY_DB)
    cursor = conn.cursor()

    cursor.execute("""
        SELECT vertices, faces, vertex_count, face_count
        FROM component_geometries
        WHERE geometry_hash = ?
    """, (geometry_hash,))

    row = cursor.fetchone()
    conn.close()

    if not row:
        return None, None

    vertices_blob, faces_blob, vertex_count, face_count = row

    # Parse vertices (3 floats per vertex)
    vertices = []
    for i in range(vertex_count):
        x, y, z = struct.unpack('<fff', vertices_blob[i*12:(i+1)*12])
        vertices.append((x, y, z))

    # Parse faces (3 ints per face)
    faces = []
    for i in range(face_count):
        v1, v2, v3 = struct.unpack('<III', faces_blob[i*12:(i+1)*12])
        faces.append((v1, v2, v3))

    return vertices, faces


def get_default_sprinkler_geometry():
    """Get default pendant sprinkler geometry from library."""
    if not LIBRARY_DB.exists():
        return None, None

    conn = sqlite3.connect(LIBRARY_DB)
    cursor = conn.cursor()

    # Get first pendant sprinkler
    cursor.execute("""
        SELECT cd.geometry_hash
        FROM component_definitions cd
        JOIN component_types ct ON cd.type_id = ct.id
        WHERE ct.category = 'SPRINKLER' AND cd.orientation = 'PENDANT'
        LIMIT 1
    """)

    row = cursor.fetchone()
    conn.close()

    if not row:
        return None, None

    return get_sprinkler_geometry(row[0])


def create_sprinkler_shape(model, context, vertices, faces):
    """Create IfcShapeRepresentation from tessellated geometry."""
    # Create coordinate list
    coord_list = model.create_entity(
        "IfcCartesianPointList3D",
        CoordList=vertices
    )

    # Create indexed face set (IFC uses 1-based indices)
    face_indices = [[f[0]+1, f[1]+1, f[2]+1] for f in faces]
    face_set = model.create_entity(
        "IfcTriangulatedFaceSet",
        Coordinates=coord_list,
        CoordIndex=face_indices
    )

    # Create shape representation
    shape = model.create_entity(
        "IfcShapeRepresentation",
        ContextOfItems=context,
        RepresentationIdentifier="Body",
        RepresentationType="Tessellation",
        Items=[face_set]
    )

    return shape


def create_sprinkler_grid(model, body_context, storey, grid_spec, base_shape):
    """
    Create sprinklers on a grid within a room area.

    Grid formula (from Java SprinklerPlacer):
        First head at (spacing/2, spacing/2) from room corner
        n = floor((dim - spacing/2) / spacing) + 1
    """
    min_x = grid_spec["min_x"]
    max_x = grid_spec["max_x"]
    min_y = grid_spec["min_y"]
    max_y = grid_spec["max_y"]
    spacing = grid_spec["spacing"]
    attachment_z = grid_spec["attachment_z"]
    room_name = grid_spec.get("room_name", "Room")

    width = max_x - min_x
    depth = max_y - min_y

    # Calculate grid positions
    half_spacing = spacing / 2

    # Number of heads in each direction
    n_x = int((width - half_spacing) / spacing) + 1
    n_y = int((depth - half_spacing) / spacing) + 1

    # Ensure at least 1 sprinkler
    n_x = max(1, n_x)
    n_y = max(1, n_y)

    sprinklers = []

    for i in range(n_x):
        for j in range(n_y):
            # Position from room corner (offset by half spacing)
            x = min_x + half_spacing + i * spacing
            y = min_y + half_spacing + j * spacing

            # Create sprinkler element
            sprinkler = ifcopenshell.api.run("root.create_entity", model,
                                              ifc_class="IfcFireSuppressionTerminal",
                                              name=f"Sprinkler_{room_name}_{i}_{j}")

            # Create placement
            origin = model.create_entity("IfcCartesianPoint",
                                         Coordinates=[float(x), float(y), float(attachment_z)])
            placement = model.create_entity("IfcAxis2Placement3D", Location=origin)
            local_placement = model.create_entity("IfcLocalPlacement",
                                                  RelativePlacement=placement)
            sprinkler.ObjectPlacement = local_placement

            # Assign geometry (reference shared shape)
            prod_shape = model.create_entity(
                "IfcProductDefinitionShape",
                Representations=[base_shape]
            )
            sprinkler.Representation = prod_shape

            sprinklers.append(sprinkler)

    return sprinklers


def create_assembly(model, body_context, storey, assembly_spec, component_elements):
    """
    Create an IfcElementAssembly that groups components.

    assembly_spec: dict with keys:
        - assembly_guid: unique identifier
        - assembly_type: WALL_PANEL, ROOF_ASSEMBLY, etc.
        - name: display name
        - components: list of component specs with guid, role, sequence

    component_elements: dict mapping component_guid to IFC element
    """
    assembly_type = assembly_spec.get("assembly_type", "USERDEFINED")
    name = assembly_spec.get("name", "Assembly")

    # Create assembly element
    assembly = ifcopenshell.api.run("root.create_entity", model,
                                     ifc_class="IfcElementAssembly",
                                     name=name,
                                     predefined_type="USERDEFINED")

    # Set assembly type as description
    assembly.Description = assembly_type

    # Get component IFC elements
    components = []
    for comp_spec in assembly_spec.get("components", []):
        comp_guid = comp_spec.get("component_guid")
        if comp_guid in component_elements:
            components.append(component_elements[comp_guid])

    # Create aggregation relationship (assembly → components)
    if components:
        ifcopenshell.api.run("aggregate.assign_object", model,
                              relating_object=assembly,
                              products=components)

    # Assign assembly to storey
    ifcopenshell.api.run("spatial.assign_container", model,
                          relating_structure=storey,
                          products=[assembly])

    return assembly


def export_storey(storey_spec, output_path, config):
    """Export complete storey to IFC."""
    storey_name = storey_spec.get("storey_name", "Ground Floor")
    storey_height = storey_spec.get("storey_height", 2.8)
    walls = storey_spec.get("walls", [])
    spaces = storey_spec.get("spaces", [])
    sprinkler_grids = storey_spec.get("sprinkler_grids", [])

    model, body_context, storey = create_ifc_model(config, storey_name)

    # Create walls
    wall_elements = []
    for i, wall_spec in enumerate(walls):
        wall = create_wall(model, body_context, storey, wall_spec, i)
        if wall:
            wall_elements.append(wall)

    # Create spaces
    space_elements = []
    for space_spec in spaces:
        space = create_space(model, body_context, storey, space_spec)
        space_elements.append(space)

    # Create sprinklers
    sprinkler_elements = []
    if sprinkler_grids:
        # Load sprinkler geometry once (shared by all)
        vertices, faces = get_default_sprinkler_geometry()
        if vertices and faces:
            base_shape = create_sprinkler_shape(model, body_context, vertices, faces)

            for grid_spec in sprinkler_grids:
                sprinklers = create_sprinkler_grid(model, body_context, storey, grid_spec, base_shape)
                sprinkler_elements.extend(sprinklers)

            # Assign all sprinklers to storey
            if sprinkler_elements:
                ifcopenshell.api.run("spatial.assign_container", model,
                                      relating_structure=storey,
                                      products=sprinkler_elements)
        else:
            print("WARNING: Could not load sprinkler geometry from library")

    # Create assemblies (if any)
    assemblies = storey_spec.get("assemblies", [])
    assembly_elements = []
    component_elements = {}  # Map guid → IFC element

    # First pass: create all component elements
    for assembly_spec in assemblies:
        for comp_spec in assembly_spec.get("components", []):
            comp_guid = comp_spec.get("component_guid")
            ifc_class = comp_spec.get("ifc_class", "IfcBuildingElementProxy")
            name = comp_spec.get("name", "Component")
            bbox = comp_spec.get("bbox", {})

            # Create component element
            comp = ifcopenshell.api.run("root.create_entity", model,
                                         ifc_class=ifc_class,
                                         name=name)

            # Simple box geometry for component
            if bbox:
                width = bbox.get("width", 1.0)
                depth = bbox.get("depth", 0.1)
                height = bbox.get("height", 2.4)

                profile = model.create_entity("IfcRectangleProfileDef",
                                               ProfileType="AREA",
                                               XDim=width,
                                               YDim=depth)
                extrusion_dir = model.create_entity("IfcDirection",
                                                     DirectionRatios=(0.0, 0.0, 1.0))
                profile_pos = model.create_entity("IfcAxis2Placement3D",
                                                   Location=model.create_entity("IfcCartesianPoint",
                                                                                 Coordinates=(0.0, 0.0, 0.0)))
                solid = model.create_entity("IfcExtrudedAreaSolid",
                                             SweptArea=profile,
                                             Position=profile_pos,
                                             ExtrudedDirection=extrusion_dir,
                                             Depth=height)
                shape_rep = model.create_entity("IfcShapeRepresentation",
                                                 ContextOfItems=body_context,
                                                 RepresentationIdentifier="Body",
                                                 RepresentationType="SweptSolid",
                                                 Items=[solid])
                prod_shape = model.create_entity("IfcProductDefinitionShape",
                                                  Representations=[shape_rep])
                comp.Representation = prod_shape

                # Position component
                x = bbox.get("x", 0)
                y = bbox.get("y", 0)
                z = bbox.get("z", 0)
                matrix = [
                    [1, 0, 0, x],
                    [0, 1, 0, y],
                    [0, 0, 1, z],
                    [0, 0, 0, 1]
                ]
                ifcopenshell.api.run("geometry.edit_object_placement", model,
                                      product=comp, matrix=matrix)

            component_elements[comp_guid] = comp

    # Second pass: create assemblies and link components
    for assembly_spec in assemblies:
        assembly = create_assembly(model, body_context, storey, assembly_spec, component_elements)
        assembly_elements.append(assembly)

    # Write file
    model.write(output_path)

    print(f"SUCCESS: Exported to {output_path}")
    print(f"  Storey: {storey_name}")
    print(f"  Walls: {len(wall_elements)}")
    print(f"  Spaces: {len(space_elements)}")
    print(f"  Openings: {sum(len(w.get('openings', [])) for w in walls)}")
    print(f"  Sprinklers: {len(sprinkler_elements)}")
    print(f"  Assemblies: {len(assembly_elements)}")

    return True


def main():
    parser = argparse.ArgumentParser(description="DSL-to-IFC Export")
    parser.add_argument("--storey", help="JSON file with storey specification")
    parser.add_argument("--output", "-o", help="Output IFC file path")
    parser.add_argument("--config", "-c", help="Config JSON from Java")

    args = parser.parse_args()

    if not args.storey or not args.output:
        parser.print_help()
        sys.exit(1)

    config = load_config(args.config)

    with open(args.storey) as f:
        storey_spec = json.load(f)

    success = export_storey(storey_spec, args.output, config)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
