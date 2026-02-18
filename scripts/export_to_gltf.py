#!/usr/bin/env python3
"""
BIM Intent Compiler - DB to glTF Exporter

Converts the federated SQLite database to glTF 2.0 format for web viewing.
Uses pygltflib for glTF generation.

Usage:
    python scripts/export_to_gltf.py output/building.db [output/building.glb]

Dependencies:
    pip install pygltflib numpy

The exported glTF includes:
- All building geometry (walls, floors, roofs, doors, windows)
- MEP elements (electrical, plumbing, structural)
- Element metadata as extras (for viewer inspection)
- Discipline-based material colors
"""

import sys
import sqlite3
import struct
import json
from pathlib import Path

try:
    import numpy as np
    from pygltflib import (
        GLTF2, Scene, Node, Mesh, Primitive, Buffer, BufferView,
        Accessor, Material, Asset, FLOAT, UNSIGNED_INT, ARRAY_BUFFER,
        ELEMENT_ARRAY_BUFFER, TRIANGLES
    )
    HAS_PYGLTF = True
except ImportError:
    HAS_PYGLTF = False
    print("WARNING: pygltflib not installed. Install with: pip install pygltflib numpy")


# Material colors by discipline
DISCIPLINE_COLORS = {
    'ARCH': [0.9, 0.9, 0.85, 1.0],      # Off-white for walls
    'STRUCT': [0.6, 0.6, 0.65, 1.0],    # Concrete gray
    'ELEC': [1.0, 0.8, 0.0, 1.0],       # Yellow for electrical
    'PLUMB': [0.2, 0.5, 0.8, 1.0],      # Blue for plumbing
    'HVAC': [0.3, 0.7, 0.3, 1.0],       # Green for HVAC
    'UNKNOWN': [0.7, 0.7, 0.7, 1.0],    # Gray default
}

# Map IFC class to discipline (for filtering)
IFC_TO_DISCIPLINE = {
    'IfcWall': 'ARCH', 'IfcWallStandardCase': 'ARCH',
    'IfcDoor': 'ARCH', 'IfcWindow': 'ARCH',
    'IfcSlab': 'ARCH', 'IfcRoof': 'ARCH',
    'IfcSpace': 'ARCH', 'IfcStair': 'ARCH', 'IfcStairFlight': 'ARCH',
    'IfcColumn': 'STRUCT', 'IfcBeam': 'STRUCT', 'IfcMember': 'STRUCT',
    'IfcLightFixture': 'ELEC', 'IfcOutlet': 'ELEC', 'IfcSwitchingDevice': 'ELEC',
    'IfcPipeSegment': 'PLUMB', 'IfcSanitaryTerminal': 'PLUMB',
    'IfcFlowTerminal': 'PLUMB',
    'IfcFireSuppressionTerminal': 'PLUMB',  # Phase 89: Sprinklers
    'IfcFan': 'HVAC',                       # Phase 89: Exhaust fans
    'IfcAirTerminal': 'HVAC',               # Phase 89: Supply/return diffusers
    'IfcFurniture': 'ARCH',                 # Phase 89: Furniture (explicit)
    'IfcPlate': 'ARCH',                     # Walls (stored as IfcPlate in BIM)
    'IfcFurnishingElement': 'ARCH',         # Furnishing elements
    'IfcMember': 'STRUCT',                  # Structural members
}

def get_discipline(element: dict) -> str:
    """Get discipline from IFC class."""
    ifc_class = element.get('ifc_class', '')
    return IFC_TO_DISCIPLINE.get(ifc_class, 'ARCH')

# IFC class colors (more specific)
IFC_COLORS = {
    'IfcWallStandardCase': [0.95, 0.95, 0.9, 1.0],
    'IfcSlab': [0.8, 0.8, 0.75, 1.0],
    'IfcRoof': [0.6, 0.3, 0.2, 1.0],     # Brown
    'IfcDoor': [0.5, 0.35, 0.2, 1.0],    # Wood brown
    'IfcWindow': [0.7, 0.85, 0.95, 0.5], # Light blue, transparent
    'IfcSpace': [0.5, 0.7, 0.9, 0.2],    # Light blue, very transparent
    'IfcColumn': [0.5, 0.5, 0.55, 1.0],
    'IfcBeam': [0.55, 0.55, 0.6, 1.0],
    'IfcStair': [0.85, 0.85, 0.8, 1.0],
    'IfcFlowTerminal': [0.9, 0.9, 0.2, 1.0],  # Yellow
    'IfcSanitaryTerminal': [0.95, 0.95, 0.95, 1.0],  # White
    'IfcFireSuppressionTerminal': [0.9, 0.2, 0.2, 1.0],  # Phase 89: Red (fire protection)
    'IfcFurniture': [0.6, 0.4, 0.25, 1.0],               # Phase 89: Wood brown
    'IfcFan': [0.3, 0.7, 0.3, 1.0],                       # Phase 89: Green (HVAC)
    'IfcAirTerminal': [0.4, 0.8, 0.4, 1.0],               # Phase 89: Light green (HVAC)
    'IfcPlate': [0.92, 0.90, 0.85, 1.0],                  # Walls (Teablock/plaster)
    'IfcMember': [0.55, 0.55, 0.6, 1.0],                  # Structural members
    'IfcFurnishingElement': [0.6, 0.4, 0.25, 1.0],        # Furnishings (wood brown)
}


def load_elements(db_path: str) -> list:
    """Load all elements with geometry from the database."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    elements = []

    # Get elements with geometry
    # Schema: elements_meta has element_name, element_type (not name, type)
    # rtree uses id (integer) not guid
    cursor = conn.execute("""
        SELECT
            m.guid,
            m.ifc_class,
            m.element_name as name,
            m.element_type as type,
            m.storey,
            m.discipline,
            m.material_rgba,
            g.vertices,
            g.faces,
            r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m
        JOIN element_instances i ON m.guid = i.guid
        JOIN base_geometries g ON i.geometry_hash = g.geometry_hash
        LEFT JOIN elements_rtree r ON m.id = r.id
        WHERE g.vertices IS NOT NULL
    """)

    for row in cursor:
        # Decode vertex and face data
        vertices_blob = row['vertices']
        faces_blob = row['faces']

        if vertices_blob and faces_blob:
            # Vertices: float32 array [x1,y1,z1, x2,y2,z2, ...]
            vertices = np.frombuffer(vertices_blob, dtype=np.float32).reshape(-1, 3)
            # Faces: int32 array [i1,i2,i3, i4,i5,i6, ...]
            faces = np.frombuffer(faces_blob, dtype=np.int32).reshape(-1, 3)

            elements.append({
                'guid': row['guid'],
                'ifc_class': row['ifc_class'],
                'name': row['name'],
                'type': row['type'],
                'storey': row['storey'],
                'discipline': row['discipline'] or 'UNKNOWN',
                'material_rgba': row['material_rgba'],
                'vertices': vertices,
                'faces': faces,
                'bounds': {
                    'min': [row['minX'], row['minY'], row['minZ']],
                    'max': [row['maxX'], row['maxY'], row['maxZ']]
                } if row['minX'] is not None else None
            })

    conn.close()
    return elements


def parse_rgba(rgba_str: str) -> list:
    """Parse material_rgba string, auto-detecting 0-255 int vs 0.0-1.0 float format.
    Returns [r, g, b, a] in 0.0-1.0 range, or None if unparseable.
    SampleHouse (IFC-extracted): '0.969,0.969,0.969,1.000' (floats)
    TB-LKTN (generated):         '220,210,200,255' (ints)
    """
    if not rgba_str:
        return None
    try:
        parts = [float(x.strip()) for x in rgba_str.split(',')]
        if len(parts) < 3:
            return None
        # Auto-detect: if any RGB value > 1.0, it's 0-255 format
        max_rgb = max(parts[0], parts[1], parts[2])
        if max_rgb > 1.0:
            r, g, b = parts[0]/255.0, parts[1]/255.0, parts[2]/255.0
            a = parts[3]/255.0 if len(parts) >= 4 else 1.0
        else:
            r, g, b = parts[0], parts[1], parts[2]
            a = parts[3] if len(parts) >= 4 else 1.0
        # Alpha=0.0 is likely a default/error — make fully opaque
        # Values 0 < a < 1 are intentional transparency (e.g. glass alpha=0.1)
        if a == 0.0:
            a = 1.0
        return [r, g, b, a]
    except (ValueError, TypeError):
        return None


def get_material_color(element: dict) -> list:
    """Get material color: DB material_rgba > IFC class > discipline fallback.

    Alpha heuristic for composite elements: IFC windows/curtain walls are
    multi-material (frame=opaque, glass=transparent) but we only store one
    material_rgba per element. Override alpha by ifc_class to get visually
    correct transparency. Replace with surface_styles lookup when available.
    """
    # Priority 1: material_rgba from DB
    color = parse_rgba(element.get('material_rgba'))
    if color:
        # HEURISTIC: override alpha for composite elements
        # These have multi-material sub-elements (frame+glass) but we only
        # store one material_rgba. Force reasonable alpha by ifc_class.
        ifc_class = element.get('ifc_class', '')
        if ifc_class == 'IfcWindow':
            color[3] = 0.3   # Glass with visible frame outline
        elif ifc_class == 'IfcCurtainWall':
            color[3] = 0.2   # Mostly transparent
        elif ifc_class == 'IfcDoor' and 'glass' in (element.get('name') or '').lower():
            color[3] = 0.5   # Glass door
        return color

    # Priority 2: IFC class color
    ifc_class = element.get('ifc_class', '')
    if ifc_class in IFC_COLORS:
        return IFC_COLORS[ifc_class]

    # Priority 3: discipline fallback
    discipline = element.get('discipline', 'UNKNOWN')
    return DISCIPLINE_COLORS.get(discipline, DISCIPLINE_COLORS['UNKNOWN'])


def export_gltf(elements: list, output_path: str, building_name: str = "Building"):
    """Export elements to glTF 2.0 binary format with discipline grouping."""

    if not HAS_PYGLTF:
        print("ERROR: pygltflib required for glTF export")
        print("Install with: pip install pygltflib numpy")
        return False

    # Create discipline groups
    disciplines = ['ARCH', 'STRUCT', 'ELEC', 'PLUMB']

    # Node structure:
    # 0: Root (building_name) -> children [1,2,3,4]
    # 1: ARCH group
    # 2: STRUCT group
    # 3: ELEC group
    # 4: PLUMB group
    # 5+: individual elements

    gltf = GLTF2(
        asset=Asset(version="2.0", generator="BIM Intent Compiler"),
        scenes=[Scene(nodes=[0])],  # Root node
        scene=0,
        nodes=[
            Node(name=building_name, children=[1, 2, 3, 4]),  # Root
            Node(name="ARCH", children=[]),   # Architecture group
            Node(name="STRUCT", children=[]), # Structural group
            Node(name="ELEC", children=[]),   # Electrical group
            Node(name="PLUMB", children=[]),  # Plumbing group
        ],
        meshes=[],
        materials=[],
        accessors=[],
        bufferViews=[],
        buffers=[]
    )

    discipline_node_idx = {'ARCH': 1, 'STRUCT': 2, 'ELEC': 3, 'PLUMB': 4}

    # Collect all binary data
    binary_data = bytearray()

    # Track materials to avoid duplicates
    material_cache = {}

    # Process each element
    for i, element in enumerate(elements):
        vertices = element['vertices']
        faces = element['faces']

        if len(vertices) == 0 or len(faces) == 0:
            continue

        # Calculate normals (simple face normals repeated per vertex)
        normals = np.zeros_like(vertices)
        for face in faces:
            v0, v1, v2 = vertices[face[0]], vertices[face[1]], vertices[face[2]]
            normal = np.cross(v1 - v0, v2 - v0)
            norm = np.linalg.norm(normal)
            if norm > 0:
                normal = normal / norm
            for idx in face:
                normals[idx] = normal

        # Get or create material
        color = tuple(get_material_color(element))
        if color not in material_cache:
            mat_idx = len(gltf.materials)
            material_cache[color] = mat_idx
            gltf.materials.append(Material(
                pbrMetallicRoughness={
                    "baseColorFactor": list(color),
                    "metallicFactor": 0.0,
                    "roughnessFactor": 0.8
                },
                doubleSided=True,
                alphaMode="BLEND" if color[3] < 1.0 else "OPAQUE"
            ))
        material_idx = material_cache[color]

        # --- Vertex buffer ---
        vertex_data = vertices.astype(np.float32).tobytes()
        vertex_offset = len(binary_data)
        binary_data.extend(vertex_data)
        # Pad to 4-byte alignment
        while len(binary_data) % 4 != 0:
            binary_data.append(0)

        vertex_buffer_view_idx = len(gltf.bufferViews)
        gltf.bufferViews.append(BufferView(
            buffer=0,
            byteOffset=vertex_offset,
            byteLength=len(vertex_data),
            target=ARRAY_BUFFER
        ))

        vertex_accessor_idx = len(gltf.accessors)
        gltf.accessors.append(Accessor(
            bufferView=vertex_buffer_view_idx,
            byteOffset=0,
            componentType=FLOAT,
            count=len(vertices),
            type="VEC3",
            min=vertices.min(axis=0).tolist(),
            max=vertices.max(axis=0).tolist()
        ))

        # --- Normal buffer ---
        normal_data = normals.astype(np.float32).tobytes()
        normal_offset = len(binary_data)
        binary_data.extend(normal_data)
        while len(binary_data) % 4 != 0:
            binary_data.append(0)

        normal_buffer_view_idx = len(gltf.bufferViews)
        gltf.bufferViews.append(BufferView(
            buffer=0,
            byteOffset=normal_offset,
            byteLength=len(normal_data),
            target=ARRAY_BUFFER
        ))

        normal_accessor_idx = len(gltf.accessors)
        gltf.accessors.append(Accessor(
            bufferView=normal_buffer_view_idx,
            byteOffset=0,
            componentType=FLOAT,
            count=len(normals),
            type="VEC3"
        ))

        # --- Index buffer ---
        index_data = faces.astype(np.uint32).tobytes()
        index_offset = len(binary_data)
        binary_data.extend(index_data)
        while len(binary_data) % 4 != 0:
            binary_data.append(0)

        index_buffer_view_idx = len(gltf.bufferViews)
        gltf.bufferViews.append(BufferView(
            buffer=0,
            byteOffset=index_offset,
            byteLength=len(index_data),
            target=ELEMENT_ARRAY_BUFFER
        ))

        index_accessor_idx = len(gltf.accessors)
        gltf.accessors.append(Accessor(
            bufferView=index_buffer_view_idx,
            byteOffset=0,
            componentType=UNSIGNED_INT,
            count=faces.size,
            type="SCALAR"
        ))

        # --- Create mesh ---
        mesh_idx = len(gltf.meshes)
        gltf.meshes.append(Mesh(
            name=element['name'],
            primitives=[Primitive(
                attributes={
                    "POSITION": vertex_accessor_idx,
                    "NORMAL": normal_accessor_idx
                },
                indices=index_accessor_idx,
                material=material_idx,
                mode=TRIANGLES
            )]
        ))

        # --- Create node ---
        node_idx = len(gltf.nodes)
        disc = get_discipline(element)
        gltf.nodes.append(Node(
            name=element['guid'],
            mesh=mesh_idx,
            extras={
                'guid': element['guid'],
                'ifc_class': element['ifc_class'],
                'name': element['name'],
                'type': element['type'],
                'storey': element['storey'],
                'discipline': disc
            }
        ))

        # Add to discipline group node
        disc_node = discipline_node_idx.get(disc, 1)  # Default to ARCH
        gltf.nodes[disc_node].children.append(node_idx)

    # Create buffer
    gltf.buffers.append(Buffer(byteLength=len(binary_data)))

    # Set binary blob
    gltf.set_binary_blob(bytes(binary_data))

    # Save
    gltf.save(output_path)
    return True


def export_simple_obj(elements: list, output_path: str):
    """Fallback: export to OBJ format (no dependencies required)."""

    obj_path = output_path.replace('.glb', '.obj').replace('.gltf', '.obj')
    mtl_path = obj_path.replace('.obj', '.mtl')

    with open(mtl_path, 'w') as mtl:
        mtl.write("# BIM Intent Compiler Materials\n\n")
        for discipline, color in DISCIPLINE_COLORS.items():
            mtl.write(f"newmtl {discipline}\n")
            mtl.write(f"Kd {color[0]} {color[1]} {color[2]}\n")
            mtl.write(f"d {color[3]}\n\n")

    vertex_offset = 1  # OBJ indices are 1-based

    with open(obj_path, 'w') as obj:
        obj.write("# BIM Intent Compiler Export\n")
        obj.write(f"mtllib {Path(mtl_path).name}\n\n")

        for element in elements:
            vertices = element['vertices']
            faces = element['faces']
            discipline = element.get('discipline', 'UNKNOWN')

            obj.write(f"# {element['guid']} - {element['name']}\n")
            obj.write(f"o {element['guid']}\n")
            obj.write(f"usemtl {discipline}\n")

            for v in vertices:
                obj.write(f"v {v[0]} {v[1]} {v[2]}\n")

            for f in faces:
                obj.write(f"f {f[0]+vertex_offset} {f[1]+vertex_offset} {f[2]+vertex_offset}\n")

            vertex_offset += len(vertices)
            obj.write("\n")

    print(f"OBJ exported: {obj_path}")
    print(f"MTL exported: {mtl_path}")
    return True


def main():
    if len(sys.argv) < 2:
        print("Usage: python export_to_gltf.py <input.db> [output.glb]")
        print("\nExports BIM database to glTF 2.0 binary format for web viewing.")
        sys.exit(1)

    db_path = sys.argv[1]

    if len(sys.argv) >= 3:
        output_path = sys.argv[2]
    else:
        output_path = db_path.replace('.db', '.glb')

    if not Path(db_path).exists():
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    print(f"Loading elements from {db_path}...")
    elements = load_elements(db_path)
    print(f"Loaded {len(elements)} elements with geometry")

    # Group by discipline for stats
    by_discipline = {}
    for e in elements:
        d = e.get('discipline', 'UNKNOWN')
        by_discipline[d] = by_discipline.get(d, 0) + 1

    print("\nElements by discipline:")
    for d, count in sorted(by_discipline.items()):
        print(f"  {d}: {count}")

    # Try glTF export, fall back to OBJ
    if HAS_PYGLTF:
        print(f"\nExporting to glTF: {output_path}")
        building_name = Path(db_path).stem.replace('_', '-').upper()
        if export_gltf(elements, output_path, building_name):
            print(f"SUCCESS: {output_path}")
            print(f"\nView with:")
            print(f"  1. Open viewer/index.html in browser")
            print(f"  2. Drag and drop {output_path}")
            print(f"  3. Or use: npx serve . and open http://localhost:3000/viewer/?model={output_path}")
    else:
        print("\nFalling back to OBJ export (install pygltflib for glTF)...")
        export_simple_obj(elements, output_path)


if __name__ == '__main__':
    main()
