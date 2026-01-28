#!/usr/bin/env python3
"""
Extract ALL LOD400 component types from Terminal DB to library.

Handles:
- MEP Terminals (sprinklers, lights, diffusers)
- MEP Fittings (pipe, duct)
- Structural (columns, beams, members)
- Furniture
- Controls (sensors, alarms, controllers)
"""

import sqlite3
import struct
import numpy as np
from pathlib import Path
from collections import defaultdict

TERMINAL_DB = Path("/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db")
LIBRARY_DB = Path("/home/red1/bim-compiler/library/component_library.db")

# Component type configurations
COMPONENT_CONFIGS = {
    'IfcFireSuppressionTerminal': {
        'category': 'SPRINKLER',
        'discipline': 'FP',
        'attachment_heuristic': 'from_element_type',  # pendant=TOP, upright=BOTTOM
        'grid_spacing': 4.6,  # NFPA 13
    },
    'IfcLightFixture': {
        'category': 'LIGHT',
        'discipline': 'ELEC',
        'attachment_heuristic': 'ceiling',  # Always ceiling mounted
        'grid_spacing': 3.0,
    },
    'IfcAirTerminal': {
        'category': 'DIFFUSER',
        'discipline': 'ACMV',
        'attachment_heuristic': 'ceiling',
        'grid_spacing': 4.0,
    },
    'IfcPipeFitting': {
        'category': 'PIPE_FITTING',
        'discipline': 'from_element',  # Varies by pipe discipline
        'attachment_heuristic': 'center',  # Inline connections
    },
    'IfcDuctFitting': {
        'category': 'DUCT_FITTING',
        'discipline': 'ACMV',
        'attachment_heuristic': 'center',
    },
    'IfcColumn': {
        'category': 'COLUMN',
        'discipline': 'STR',
        'attachment_heuristic': 'bottom',  # Base at floor
    },
    'IfcBeam': {
        'category': 'BEAM',
        'discipline': 'STR',
        'attachment_heuristic': 'ends',  # Connect at ends
    },
    'IfcMember': {
        'category': 'MEMBER',
        'discipline': 'STR',
        'attachment_heuristic': 'ends',
    },
    'IfcFurniture': {
        'category': 'FURNITURE',
        'discipline': 'ARC',
        'attachment_heuristic': 'bottom',  # Floor standing
    },
    'IfcValve': {
        'category': 'VALVE',
        'discipline': 'from_element',
        'attachment_heuristic': 'center',
    },
    'IfcAlarm': {
        'category': 'ALARM',
        'discipline': 'FP',
        'attachment_heuristic': 'ceiling_or_wall',
    },
    'IfcElectricAppliance': {
        'category': 'APPLIANCE',
        'discipline': 'ELEC',
        'attachment_heuristic': 'varies',
    },
    'IfcSensor': {
        'category': 'SENSOR',
        'discipline': 'from_element',
        'attachment_heuristic': 'varies',
    },
    'IfcController': {
        'category': 'CONTROLLER',
        'discipline': 'ELEC',
        'attachment_heuristic': 'wall',
    },
    # === NEW: Priority extraction for complete library ===
    'IfcDoor': {
        'category': 'DOOR',
        'discipline': 'ARC',
        'attachment_heuristic': 'bottom',  # Floor-mounted in wall opening
    },
    'IfcWindow': {
        'category': 'WINDOW',
        'discipline': 'ARC',
        'attachment_heuristic': 'side',  # Wall-mounted in opening
    },
    'IfcStairFlight': {
        'category': 'STAIR',
        'discipline': 'ARC',
        'attachment_heuristic': 'bottom',  # Base at floor
    },
    'IfcRailing': {
        'category': 'RAILING',
        'discipline': 'ARC',
        'attachment_heuristic': 'bottom',  # Mounted on stair/floor
    },
    'IfcFlowTerminal': {
        'category': 'FIXTURE',
        'discipline': 'from_element',  # SP for plumbing, CW for water
        'attachment_heuristic': 'varies',  # Sinks=wall, toilets=floor
    },
}


def parse_vertices(blob: bytes) -> np.ndarray:
    """Parse vertex blob to numpy array."""
    vertex_count = len(blob) // 12
    vertices = np.zeros((vertex_count, 3), dtype=np.float32)
    for i in range(vertex_count):
        x, y, z = struct.unpack('<fff', blob[i*12:(i+1)*12])
        vertices[i] = [x, y, z]
    return vertices


def detect_attachment_face(vertices: np.ndarray, ifc_class: str, element_type: str) -> str:
    """Detect attachment face using heuristics."""
    config = COMPONENT_CONFIGS.get(ifc_class, {})
    heuristic = config.get('attachment_heuristic', 'center')

    if heuristic == 'from_element_type':
        # Sprinkler-specific: check element_type
        if element_type and 'pendent' in element_type.lower():
            return 'TOP'
        elif element_type and 'upright' in element_type.lower():
            return 'BOTTOM'
        return 'TOP'  # Default for sprinklers

    elif heuristic == 'ceiling':
        return 'TOP'

    elif heuristic == 'bottom':
        return 'BOTTOM'

    elif heuristic == 'wall':
        return 'SIDE'

    elif heuristic == 'ceiling_or_wall':
        # Analyze geometry: if flat in Z, likely ceiling; if tall, likely wall
        z_span = vertices[:, 2].max() - vertices[:, 2].min()
        xy_span = max(vertices[:, 0].max() - vertices[:, 0].min(),
                      vertices[:, 1].max() - vertices[:, 1].min())
        return 'TOP' if z_span < xy_span else 'SIDE'

    elif heuristic == 'ends':
        # For beams/members: attachment at both ends
        return 'ENDS'

    elif heuristic == 'center':
        return 'CENTER'

    else:
        return 'CENTER'


def detect_orientation(vertices: np.ndarray, ifc_class: str, element_type: str) -> str:
    """Detect orientation from geometry and element type."""
    config = COMPONENT_CONFIGS.get(ifc_class, {})

    # Sprinklers have explicit pendant/upright
    if ifc_class == 'IfcFireSuppressionTerminal':
        if element_type and 'pendent' in element_type.lower():
            return 'PENDANT'
        elif element_type and 'upright' in element_type.lower():
            return 'UPRIGHT'

    # Columns are vertical
    if ifc_class == 'IfcColumn':
        return 'VERTICAL'

    # Beams are typically horizontal
    if ifc_class == 'IfcBeam':
        return 'HORIZONTAL'

    # For other types, analyze geometry
    x_span = vertices[:, 0].max() - vertices[:, 0].min()
    y_span = vertices[:, 1].max() - vertices[:, 1].min()
    z_span = vertices[:, 2].max() - vertices[:, 2].min()

    if z_span > max(x_span, y_span) * 1.5:
        return 'VERTICAL'
    elif z_span < min(x_span, y_span) * 0.5:
        return 'HORIZONTAL'
    else:
        return 'MIXED'


def detect_discipline(ifc_class: str, element_discipline: str) -> str:
    """Detect discipline from config or element data."""
    config = COMPONENT_CONFIGS.get(ifc_class, {})
    disc = config.get('discipline', 'UNKNOWN')

    if disc == 'from_element':
        return element_discipline or 'UNKNOWN'
    return disc


def create_library_schema(cursor):
    """Create/update library schema."""
    cursor.executescript("""
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
            UNIQUE(geometry_hash)
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
    """)


def extract_component_type(terminal_cursor, library_cursor, ifc_class: str) -> int:
    """Extract all components of a given type."""
    config = COMPONENT_CONFIGS.get(ifc_class, {})
    category = config.get('category', ifc_class.replace('Ifc', '').upper())

    # Get or create component type
    library_cursor.execute("""
        INSERT OR IGNORE INTO component_types (ifc_class, category, discipline)
        VALUES (?, ?, ?)
    """, (ifc_class, category, config.get('discipline', 'UNKNOWN')))

    library_cursor.execute(
        "SELECT id FROM component_types WHERE ifc_class=? AND category=?",
        (ifc_class, category)
    )
    type_id = library_cursor.fetchone()[0]

    # Get unique geometries with instance counts
    terminal_cursor.execute("""
        SELECT
            ei.geometry_hash,
            COUNT(*) as instance_count,
            MIN(em.element_type) as element_type,
            MIN(em.discipline) as discipline,
            MIN(em.element_name) as element_name
        FROM elements_meta em
        JOIN element_instances ei ON em.guid = ei.guid
        WHERE em.ifc_class = ?
        GROUP BY ei.geometry_hash
        ORDER BY instance_count DESC
    """, (ifc_class,))

    geometries = terminal_cursor.fetchall()
    extracted = 0

    for geom_hash, instance_count, element_type, discipline, element_name in geometries:
        # Get geometry data
        terminal_cursor.execute("""
            SELECT vertices, faces, normals, vertex_count, face_count
            FROM base_geometries
            WHERE geometry_hash = ?
        """, (geom_hash,))
        geom = terminal_cursor.fetchone()
        if not geom:
            continue

        vertices_blob, faces_blob, normals_blob, vertex_count, face_count = geom

        # Parse vertices
        vertices = parse_vertices(vertices_blob)
        local_min = vertices.min(axis=0)
        local_max = vertices.max(axis=0)

        # Detect attachment and orientation
        attachment = detect_attachment_face(vertices, ifc_class, element_type)
        orientation = detect_orientation(vertices, ifc_class, element_type)
        disc = detect_discipline(ifc_class, discipline)

        # Generate name
        name = element_type or element_name or f"{ifc_class}_{geom_hash[:8]}"
        if ':' in name:
            name = name.split(':')[0]

        # Insert geometry
        library_cursor.execute("""
            INSERT OR IGNORE INTO component_geometries
            (geometry_hash, vertices, faces, normals, vertex_count, face_count)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (geom_hash, vertices_blob, faces_blob, normals_blob, vertex_count, face_count))

        # Insert component definition
        library_cursor.execute("""
            INSERT OR REPLACE INTO component_definitions
            (type_id, name, geometry_hash,
             local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
             attachment_face, orientation, vertex_count, face_count, instance_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (type_id, name, geom_hash,
              float(local_min[0]), float(local_max[0]),
              float(local_min[1]), float(local_max[1]),
              float(local_min[2]), float(local_max[2]),
              attachment, orientation, vertex_count, face_count, instance_count))

        extracted += 1

    # Add placement rules
    grid_spacing = config.get('grid_spacing')
    if grid_spacing:
        library_cursor.execute("""
            INSERT OR IGNORE INTO placement_rules
            (component_id, host_type, offset_from_host, grid_spacing, clearance_radius)
            SELECT cd.id, ?, 0.0, ?, ?
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = ?
        """, (
            'CEILING' if config.get('attachment_heuristic') == 'ceiling' else 'FLOOR',
            grid_spacing,
            grid_spacing / 2,
            ifc_class
        ))

    return extracted


def main():
    print("=" * 70)
    print("EXTRACTING ALL COMPONENT TYPES TO LIBRARY")
    print("=" * 70)

    terminal_conn = sqlite3.connect(TERMINAL_DB)
    terminal_cursor = terminal_conn.cursor()

    library_conn = sqlite3.connect(LIBRARY_DB)
    library_cursor = library_conn.cursor()

    print("\nCreating/updating library schema...")
    create_library_schema(library_cursor)
    library_conn.commit()

    print("\nExtracting components...")
    totals = {}

    for ifc_class in COMPONENT_CONFIGS.keys():
        count = extract_component_type(terminal_cursor, library_cursor, ifc_class)
        totals[ifc_class] = count
        print(f"  {ifc_class}: {count} unique geometries")
        library_conn.commit()

    # Summary
    print("\n" + "=" * 70)
    print("EXTRACTION SUMMARY")
    print("=" * 70)

    library_cursor.execute("""
        SELECT
            ct.ifc_class,
            ct.category,
            COUNT(*) as components,
            SUM(cd.instance_count) as total_instances,
            SUM(cd.vertex_count * cd.instance_count) as total_vertices
        FROM component_definitions cd
        JOIN component_types ct ON cd.type_id = ct.id
        GROUP BY ct.ifc_class, ct.category
        ORDER BY total_instances DESC
    """)

    print(f"\n{'IFC Class':<30} {'Category':<15} {'Unique':<8} {'Instances':<10} {'Total Verts'}")
    print("-" * 80)
    total_components = 0
    total_instances = 0
    for row in library_cursor.fetchall():
        print(f"{row[0]:<30} {row[1]:<15} {row[2]:<8} {row[3]:<10} {row[4]:,}")
        total_components += row[2]
        total_instances += row[3]

    print("-" * 80)
    print(f"{'TOTAL':<30} {'':<15} {total_components:<8} {total_instances:<10}")

    # File size
    library_conn.close()
    terminal_conn.close()

    size_mb = LIBRARY_DB.stat().st_size / (1024 * 1024)
    print(f"\nLibrary file: {LIBRARY_DB}")
    print(f"Size: {size_mb:.1f} MB")


if __name__ == "__main__":
    main()
