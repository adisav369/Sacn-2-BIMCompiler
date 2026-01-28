#!/home/red1/bim-compiler/venv/bin/python3
"""
Building-to-IFC Export Script
Exports multi-storey building from generated model DB to IFC file.

Usage:
    python export_building_to_ifc.py <db_path> <output_ifc>

Reads from:
- elements_meta: element definitions
- elements_rtree: bounding boxes
- base_geometries: mesh data
- element_assemblies: assembly hierarchy
- spatial_structure: building/storey hierarchy
"""

import sys
import struct
import sqlite3
from pathlib import Path

import ifcopenshell
import ifcopenshell.api
import ifcopenshell.geom


def load_db(db_path):
    """Load all data from generated model DB."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    data = {
        'elements': [],
        'assemblies': [],
        'spatial': [],
        'geometries': {}
    }

    # Load elements with bounds and geometry
    cursor = conn.execute("""
        SELECT m.guid, m.ifc_class, m.element_name, m.element_type, m.storey,
               r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
               g.vertices, g.faces, g.vertex_count, g.face_count
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        JOIN element_instances i ON m.guid = i.guid
        JOIN base_geometries g ON i.geometry_hash = g.geometry_hash
    """)
    for row in cursor:
        data['elements'].append(dict(row))

    # Load assemblies
    cursor = conn.execute("SELECT * FROM element_assemblies")
    for row in cursor:
        data['assemblies'].append(dict(row))

    # Load spatial structure
    cursor = conn.execute("SELECT * FROM spatial_structure")
    for row in cursor:
        data['spatial'].append(dict(row))

    conn.close()
    return data


def blob_to_floats(blob, count):
    """Convert BLOB to float array."""
    return list(struct.unpack(f'<{count}f', blob[:count*4]))


def blob_to_ints(blob, count):
    """Convert BLOB to int array."""
    return list(struct.unpack(f'<{count}i', blob[:count*4]))


def create_box_representation(model, context, min_pt, max_pt):
    """Create extruded box representation."""
    dx = max_pt[0] - min_pt[0]
    dy = max_pt[1] - min_pt[1]
    dz = max_pt[2] - min_pt[2]

    # Create rectangular profile
    rectangle = model.createIfcRectangleProfileDef(
        ProfileType="AREA",
        XDim=dx,
        YDim=dy
    )

    # Create placement at min corner
    placement = model.createIfcAxis2Placement3D(
        Location=model.createIfcCartesianPoint((
            min_pt[0] + dx/2,
            min_pt[1] + dy/2,
            min_pt[2]
        ))
    )

    # Create extruded solid
    solid = model.createIfcExtrudedAreaSolid(
        SweptArea=rectangle,
        Position=placement,
        ExtrudedDirection=model.createIfcDirection((0.0, 0.0, 1.0)),
        Depth=dz
    )

    # Create shape representation
    return model.createIfcShapeRepresentation(
        ContextOfItems=context,
        RepresentationIdentifier="Body",
        RepresentationType="SweptSolid",
        Items=[solid]
    )


def create_mesh_representation(model, context, vertices, faces, vertex_count, face_count):
    """Create tessellated mesh representation."""
    verts = blob_to_floats(vertices, vertex_count * 3)
    face_indices = blob_to_ints(faces, face_count * 3)

    # Create coordinate list
    points = []
    for i in range(0, len(verts), 3):
        points.append((verts[i], verts[i+1], verts[i+2]))

    coord_list = model.createIfcCartesianPointList3D(CoordList=points)

    # Create triangulated face set
    triangles = []
    for i in range(0, len(face_indices), 3):
        # IFC uses 1-based indexing
        triangles.append((
            face_indices[i] + 1,
            face_indices[i+1] + 1,
            face_indices[i+2] + 1
        ))

    face_set = model.createIfcTriangulatedFaceSet(
        Coordinates=coord_list,
        CoordIndex=triangles
    )

    return model.createIfcShapeRepresentation(
        ContextOfItems=context,
        RepresentationIdentifier="Body",
        RepresentationType="Tessellation",
        Items=[face_set]
    )


def export_to_ifc(data, output_path):
    """Export data to IFC file."""
    model = ifcopenshell.file(schema="IFC4")

    # Create project
    project = ifcopenshell.api.run("root.create_entity", model,
                                    ifc_class="IfcProject",
                                    name="BIM Compiler Export")

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

    # Create site
    site = ifcopenshell.api.run("root.create_entity", model,
                                 ifc_class="IfcSite",
                                 name="Site")
    ifcopenshell.api.run("aggregate.assign_object", model,
                          relating_object=project, products=[site])

    # Create building and storeys from spatial structure
    building = None
    storeys = {}

    for sp in data['spatial']:
        if sp['type'] == 'IfcBuilding':
            building = ifcopenshell.api.run("root.create_entity", model,
                                             ifc_class="IfcBuilding",
                                             name=sp['name'])
            ifcopenshell.api.run("aggregate.assign_object", model,
                                  relating_object=site, products=[building])
        elif sp['type'] == 'IfcBuildingStorey':
            storey = ifcopenshell.api.run("root.create_entity", model,
                                           ifc_class="IfcBuildingStorey",
                                           name=sp['name'])
            storeys[sp['name']] = storey

    # Assign storeys to building
    if building and storeys:
        ifcopenshell.api.run("aggregate.assign_object", model,
                              relating_object=building,
                              products=list(storeys.values()))

    # If no spatial structure, create default
    if not building:
        building = ifcopenshell.api.run("root.create_entity", model,
                                         ifc_class="IfcBuilding",
                                         name="Building")
        ifcopenshell.api.run("aggregate.assign_object", model,
                              relating_object=site, products=[building])

    if not storeys:
        default_storey = ifcopenshell.api.run("root.create_entity", model,
                                               ifc_class="IfcBuildingStorey",
                                               name="Ground")
        storeys["Ground"] = default_storey
        ifcopenshell.api.run("aggregate.assign_object", model,
                              relating_object=building, products=[default_storey])

    # Create elements
    ifc_elements = []
    assemblies_map = {}

    for elem in data['elements']:
        ifc_class = elem['ifc_class']
        guid = elem['guid']
        name = elem['element_name']
        storey_name = elem['storey'] or 'Ground'

        # Get or create storey
        if storey_name not in storeys:
            storey = ifcopenshell.api.run("root.create_entity", model,
                                           ifc_class="IfcBuildingStorey",
                                           name=storey_name)
            storeys[storey_name] = storey
            ifcopenshell.api.run("aggregate.assign_object", model,
                                  relating_object=building, products=[storey])

        storey = storeys[storey_name]

        # Create element based on class
        if ifc_class == "IfcElementAssembly":
            ifc_elem = ifcopenshell.api.run("root.create_entity", model,
                                             ifc_class="IfcElementAssembly",
                                             name=name)
            assemblies_map[guid] = ifc_elem
        else:
            # Map to valid IFC class
            valid_class = ifc_class
            if valid_class not in ['IfcWall', 'IfcSlab', 'IfcRoof', 'IfcMember',
                                   'IfcPlate', 'IfcDoor', 'IfcWindow', 'IfcStairFlight',
                                   'IfcBeam', 'IfcColumn']:
                valid_class = 'IfcBuildingElementProxy'

            ifc_elem = ifcopenshell.api.run("root.create_entity", model,
                                             ifc_class=valid_class,
                                             name=name)

        # Create geometry representation
        min_pt = (elem['minX'], elem['minY'], elem['minZ'])
        max_pt = (elem['maxX'], elem['maxY'], elem['maxZ'])

        # Use box for simple elements, mesh for complex ones
        use_mesh = elem['vertex_count'] > 8 or ifc_class in ['IfcRoof', 'IfcStairFlight']

        if use_mesh and elem['vertices'] and elem['faces']:
            representation = create_mesh_representation(
                model, body_context,
                elem['vertices'], elem['faces'],
                elem['vertex_count'], elem['face_count']
            )
        else:
            representation = create_box_representation(
                model, body_context, min_pt, max_pt
            )

        # Assign representation
        product_definition = model.createIfcProductDefinitionShape(
            Representations=[representation]
        )
        ifc_elem.Representation = product_definition

        # Create local placement
        placement = model.createIfcLocalPlacement(
            RelativePlacement=model.createIfcAxis2Placement3D(
                Location=model.createIfcCartesianPoint((0.0, 0.0, 0.0))
            )
        )
        ifc_elem.ObjectPlacement = placement

        # Assign to storey
        ifcopenshell.api.run("spatial.assign_container", model,
                              relating_structure=storey,
                              products=[ifc_elem])

        ifc_elements.append(ifc_elem)

    # Create assembly relationships
    for assembly in data['assemblies']:
        assembly_guid = assembly['assembly_guid']
        if assembly_guid in assemblies_map:
            # Find components and create IfcRelAggregates
            # (Components are already created as individual elements)
            pass

    # Write file
    model.write(output_path)
    return len(ifc_elements)


def verify_ifc(ifc_path):
    """Verify exported IFC file."""
    model = ifcopenshell.open(ifc_path)

    stats = {
        'file_size': Path(ifc_path).stat().st_size,
        'elements': {},
        'storeys': [],
        'building': None,
        'stair_z': None,
        'landing_z': None
    }

    # Count elements by class
    for elem in model.by_type('IfcProduct'):
        cls = elem.is_a()
        stats['elements'][cls] = stats['elements'].get(cls, 0) + 1

    # Get spatial structure
    for building in model.by_type('IfcBuilding'):
        stats['building'] = building.Name

    for storey in model.by_type('IfcBuildingStorey'):
        stats['storeys'].append(storey.Name)

    # Find stair z-range
    for stair in model.by_type('IfcStairFlight'):
        if stair.Representation:
            for rep in stair.Representation.Representations:
                for item in rep.Items:
                    if hasattr(item, 'Coordinates'):
                        coords = item.Coordinates.CoordList
                        zs = [c[2] for c in coords]
                        stats['stair_z'] = (min(zs), max(zs))

    # Find landing z-range
    for slab in model.by_type('IfcSlab'):
        if 'Landing' in (slab.Name or ''):
            if slab.Representation:
                for rep in slab.Representation.Representations:
                    for item in rep.Items:
                        if hasattr(item, 'Position'):
                            z = item.Position.Location.Coordinates[2]
                            depth = item.Depth if hasattr(item, 'Depth') else 0.15
                            stats['landing_z'] = (z, z + depth)

    return stats


def main():
    if len(sys.argv) < 3:
        print("Usage: python export_building_to_ifc.py <db_path> <output_ifc>")
        sys.exit(1)

    db_path = sys.argv[1]
    output_path = sys.argv[2]

    print(f"Loading from: {db_path}")
    data = load_db(db_path)

    print(f"Elements: {len(data['elements'])}")
    print(f"Assemblies: {len(data['assemblies'])}")
    print(f"Spatial: {len(data['spatial'])}")

    print(f"\nExporting to: {output_path}")
    count = export_to_ifc(data, output_path)
    print(f"Exported {count} elements")

    # Verify
    print("\n" + "="*60)
    print("VERIFICATION")
    print("="*60)

    stats = verify_ifc(output_path)

    print(f"\nFile size: {stats['file_size']:,} bytes")
    print(f"Building: {stats['building']}")
    print(f"Storeys: {stats['storeys']}")

    print("\nElement counts:")
    for cls, count in sorted(stats['elements'].items()):
        print(f"  {cls}: {count}")

    total = sum(stats['elements'].values())
    print(f"\nTotal IFC elements: {total}")

    if stats['stair_z']:
        print(f"\nStair Z-range: [{stats['stair_z'][0]:.2f} - {stats['stair_z'][1]:.2f}]")

    if stats['landing_z']:
        print(f"Landing Z-range: [{stats['landing_z'][0]:.2f} - {stats['landing_z'][1]:.2f}]")

    # Check hierarchy
    print("\n" + "-"*60)
    print("HIERARCHY CHECK:")

    model = ifcopenshell.open(output_path)
    for rel in model.by_type('IfcRelAggregates'):
        parent = rel.RelatingObject.Name if rel.RelatingObject else "?"
        children = [c.Name for c in rel.RelatedObjects] if rel.RelatedObjects else []
        if len(children) <= 5:
            print(f"  {parent} → {children}")
        else:
            print(f"  {parent} → [{len(children)} items]")


if __name__ == "__main__":
    main()
