#!/usr/bin/env python3
"""
Export sprinklers to IFC with instancing (IfcMappedItem).

Uses:
- Single geometry definition per sprinkler type
- Multiple IfcMappedItem placements
- Efficient file size (100 sprinklers = 1 geometry + 100 placements)
"""

import json
import sys
import math
import sqlite3
import struct
from pathlib import Path

import ifcopenshell
from ifcopenshell.api import run

LIBRARY_DB = Path(__file__).parent.parent / "library" / "component_library.db"


def create_ifc_file(project_name: str = "Sprinkler System"):
    """Create IFC file with project structure."""
    model = ifcopenshell.file(schema="IFC4")

    # Project setup
    project = run("root.create_entity", model, ifc_class="IfcProject", name=project_name)
    run("unit.assign_unit", model, length={"is_metric": True, "raw": "METRE"})

    # Context for geometry
    context = run("context.add_context", model, context_type="Model")
    body = run("context.add_context", model,
               context_type="Model",
               context_identifier="Body",
               target_view="MODEL_VIEW",
               parent=context)

    # Spatial structure
    site = run("root.create_entity", model, ifc_class="IfcSite", name="Site")
    building = run("root.create_entity", model, ifc_class="IfcBuilding", name="Building")
    storey = run("root.create_entity", model, ifc_class="IfcBuildingStorey", name="Level 1")

    run("aggregate.assign_object", model, relating_object=project, products=[site])
    run("aggregate.assign_object", model, relating_object=site, products=[building])
    run("aggregate.assign_object", model, relating_object=building, products=[storey])

    return model, body, storey


def get_sprinkler_geometry(geometry_hash: str):
    """Load sprinkler geometry from library."""
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

    # Parse vertices
    vertices = []
    for i in range(vertex_count):
        x, y, z = struct.unpack('<fff', vertices_blob[i*12:(i+1)*12])
        vertices.append((x, y, z))

    # Parse faces
    faces = []
    for i in range(face_count):
        v1, v2, v3 = struct.unpack('<III', faces_blob[i*12:(i+1)*12])
        faces.append((v1, v2, v3))

    return vertices, faces


def create_shape_representation(model, context, vertices, faces):
    """Create IfcShapeRepresentation from vertices and faces."""
    # Create coordinate list
    coord_list = model.create_entity(
        "IfcCartesianPointList3D",
        CoordList=vertices
    )

    # Create indexed face set
    face_indices = [[f[0]+1, f[1]+1, f[2]+1] for f in faces]  # IFC uses 1-based
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


def create_mapped_representation(model, context, shape_rep, position, rotation):
    """Create IfcMappedItem for instanced placement."""
    # Create RepresentationMap (defines the shape once)
    origin = model.create_entity("IfcCartesianPoint", Coordinates=[0.0, 0.0, 0.0])
    axis2 = model.create_entity("IfcAxis2Placement3D", Location=origin)
    rep_map = model.create_entity(
        "IfcRepresentationMap",
        MappingOrigin=axis2,
        MappedRepresentation=shape_rep
    )

    # Create placement transform
    cos_r = math.cos(rotation)
    sin_r = math.sin(rotation)

    location = model.create_entity("IfcCartesianPoint",
                                   Coordinates=[float(position[0]), float(position[1]), float(position[2])])

    # Rotation around Z axis
    if abs(rotation) > 0.001:
        axis_x = model.create_entity("IfcDirection", DirectionRatios=[cos_r, sin_r, 0.0])
        axis_z = model.create_entity("IfcDirection", DirectionRatios=[0.0, 0.0, 1.0])
        transform_origin = model.create_entity(
            "IfcAxis2Placement3D",
            Location=location,
            Axis=axis_z,
            RefDirection=axis_x
        )
    else:
        transform_origin = model.create_entity(
            "IfcAxis2Placement3D",
            Location=location
        )

    # Cartesian transform operator
    transform = model.create_entity(
        "IfcCartesianTransformationOperator3D",
        LocalOrigin=transform_origin.Location,
        Axis1=model.create_entity("IfcDirection", DirectionRatios=[cos_r, sin_r, 0.0]) if abs(rotation) > 0.001 else None,
        Axis2=model.create_entity("IfcDirection", DirectionRatios=[-sin_r, cos_r, 0.0]) if abs(rotation) > 0.001 else None,
        Axis3=model.create_entity("IfcDirection", DirectionRatios=[0.0, 0.0, 1.0]) if abs(rotation) > 0.001 else None
    )

    # Create mapped item
    mapped_item = model.create_entity(
        "IfcMappedItem",
        MappingSource=rep_map,
        MappingTarget=transform
    )

    # Create shape representation with mapped item
    mapped_shape = model.create_entity(
        "IfcShapeRepresentation",
        ContextOfItems=context,
        RepresentationIdentifier="Body",
        RepresentationType="MappedRepresentation",
        Items=[mapped_item]
    )

    return mapped_shape, rep_map


def export_sprinklers(sprinklers: list, output_path: str):
    """
    Export sprinklers to IFC file.

    sprinklers: list of dicts with keys:
        - type: "PENDANT" or "UPRIGHT"
        - position: [x, y, z]
        - rotation: float (radians)
        - geometry_hash: str
    """
    model, context, storey = create_ifc_file()

    # Group by geometry hash for instancing
    by_hash = {}
    for s in sprinklers:
        h = s["geometry_hash"]
        if h not in by_hash:
            by_hash[h] = []
        by_hash[h].append(s)

    print(f"Exporting {len(sprinklers)} sprinklers ({len(by_hash)} unique geometries)")

    sprinkler_elements = []

    for geom_hash, instances in by_hash.items():
        # Load geometry once per unique hash
        vertices, faces = get_sprinkler_geometry(geom_hash)
        if vertices is None:
            print(f"  Warning: geometry {geom_hash} not found")
            continue

        # Create base shape representation
        base_shape = create_shape_representation(model, context, vertices, faces)

        # Create instances
        for i, s in enumerate(instances):
            # Create sprinkler element
            sprinkler = run("root.create_entity", model,
                           ifc_class="IfcFlowTerminal",
                           name=f"Sprinkler_{s['type']}_{i+1}",
                           predefined_type="USERDEFINED")

            # Create local placement
            origin = model.create_entity("IfcCartesianPoint",
                                        Coordinates=[float(x) for x in s["position"]])
            placement = model.create_entity("IfcAxis2Placement3D", Location=origin)
            local_placement = model.create_entity("IfcLocalPlacement",
                                                  RelativePlacement=placement)
            sprinkler.ObjectPlacement = local_placement

            # For first instance, use direct shape; for others use mapped
            if len(instances) == 1:
                # Direct representation
                prod_shape = model.create_entity(
                    "IfcProductDefinitionShape",
                    Representations=[base_shape]
                )
            else:
                # Use instancing
                mapped_shape, _ = create_mapped_representation(
                    model, context, base_shape,
                    (0, 0, 0),  # Local origin - placement handles position
                    s.get("rotation", 0.0)
                )
                prod_shape = model.create_entity(
                    "IfcProductDefinitionShape",
                    Representations=[mapped_shape]
                )

            sprinkler.Representation = prod_shape
            sprinkler_elements.append(sprinkler)

    # Assign to storey
    run("spatial.assign_container", model,
        relating_structure=storey,
        products=sprinkler_elements)

    # Write file
    model.write(output_path)
    print(f"Written: {output_path}")
    print(f"File size: {Path(output_path).stat().st_size / 1024:.1f} KB")


def main():
    if len(sys.argv) < 3:
        print("Usage: python export_sprinklers_to_ifc.py <input.json> <output.ifc>")
        print()
        print("Input JSON format:")
        print('  [{"type": "PENDANT", "position": [x, y, z], "rotation": 0.0, "geometry_hash": "..."}]')
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2]

    with open(input_path) as f:
        sprinklers = json.load(f)

    export_sprinklers(sprinklers, output_path)


if __name__ == "__main__":
    main()
