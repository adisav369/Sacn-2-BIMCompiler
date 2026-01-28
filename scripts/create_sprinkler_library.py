#!/usr/bin/env python3
"""
Extract sprinkler components from Terminal DB and create component library.

Uses existing DB data (transforms, geometry) - no manual annotation needed.
Sprinkler orientation determined from element_type (pendant vs upright).
"""

import sqlite3
import struct
import numpy as np
from pathlib import Path

TERMINAL_DB = Path("/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db")
LIBRARY_DB = Path("/home/red1/bim-compiler/library/component_library.db")


def create_library_schema(cursor):
    """Create component library tables."""

    cursor.executescript("""
        -- Component type categories
        CREATE TABLE IF NOT EXISTS component_types (
            id INTEGER PRIMARY KEY,
            ifc_class TEXT NOT NULL,
            category TEXT NOT NULL,
            discipline TEXT NOT NULL,
            UNIQUE(ifc_class, category)
        );

        -- Component definitions with placement metadata
        CREATE TABLE IF NOT EXISTS component_definitions (
            id INTEGER PRIMARY KEY,
            type_id INTEGER REFERENCES component_types(id),
            name TEXT NOT NULL,
            geometry_hash TEXT NOT NULL,

            -- Local geometry bounds (in local coordinates)
            local_min_x REAL, local_max_x REAL,
            local_min_y REAL, local_max_y REAL,
            local_min_z REAL, local_max_z REAL,

            -- Attachment convention
            attachment_face TEXT NOT NULL,  -- TOP, BOTTOM, SIDE, CENTER
            up_axis TEXT DEFAULT 'Z',       -- Which local axis points up
            forward_axis TEXT DEFAULT 'Y',  -- Which local axis is forward

            -- Orientation
            orientation TEXT,               -- PENDANT, UPRIGHT, WALL_MOUNT
            default_rotation REAL DEFAULT 0,

            -- Geometry stats
            vertex_count INTEGER,
            face_count INTEGER,

            UNIQUE(name, geometry_hash)
        );

        -- Actual geometry blobs (copied from Terminal DB)
        CREATE TABLE IF NOT EXISTS component_geometries (
            geometry_hash TEXT PRIMARY KEY,
            vertices BLOB NOT NULL,
            faces BLOB NOT NULL,
            normals BLOB,
            vertex_count INTEGER NOT NULL,
            face_count INTEGER NOT NULL
        );

        -- Placement rules
        CREATE TABLE IF NOT EXISTS placement_rules (
            id INTEGER PRIMARY KEY,
            component_id INTEGER REFERENCES component_definitions(id),
            host_type TEXT,           -- CEILING, WALL, FLOOR
            offset_from_host REAL,    -- Distance from host surface
            grid_spacing REAL,        -- Standard spacing (e.g., 4.6m for sprinklers)
            clearance_radius REAL     -- Min distance from other objects
        );
    """)


def parse_vertices(blob: bytes) -> np.ndarray:
    """Parse vertex blob to numpy array."""
    vertex_count = len(blob) // 12
    vertices = np.zeros((vertex_count, 3), dtype=np.float32)
    for i in range(vertex_count):
        x, y, z = struct.unpack('<fff', blob[i*12:(i+1)*12])
        vertices[i] = [x, y, z]
    return vertices


def extract_sprinklers(terminal_cursor, library_cursor):
    """Extract sprinkler components from Terminal DB."""

    # Create component type for sprinklers
    library_cursor.execute("""
        INSERT OR IGNORE INTO component_types (ifc_class, category, discipline)
        VALUES ('IfcFireSuppressionTerminal', 'SPRINKLER', 'FP')
    """)
    type_id = library_cursor.lastrowid or library_cursor.execute(
        "SELECT id FROM component_types WHERE category='SPRINKLER'"
    ).fetchone()[0]

    # Get unique sprinkler geometries with their types
    terminal_cursor.execute("""
        SELECT DISTINCT
            em.element_type,
            ei.geometry_hash
        FROM elements_meta em
        JOIN element_instances ei ON em.guid = ei.guid
        WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
          AND em.element_type NOT LIKE '%Hose%'
    """)

    sprinkler_types = terminal_cursor.fetchall()
    print(f"Found {len(sprinkler_types)} unique sprinkler geometry/type combinations")

    extracted = 0
    for element_type, geometry_hash in sprinkler_types:
        # Get geometry
        terminal_cursor.execute("""
            SELECT vertices, faces, normals, vertex_count, face_count
            FROM base_geometries
            WHERE geometry_hash = ?
        """, (geometry_hash,))
        geom = terminal_cursor.fetchone()
        if not geom:
            continue

        vertices_blob, faces_blob, normals_blob, vertex_count, face_count = geom

        # Parse vertices to get local bounds
        vertices = parse_vertices(vertices_blob)
        local_min = vertices.min(axis=0)
        local_max = vertices.max(axis=0)

        # Determine orientation from element_type
        if 'pendent' in element_type.lower():
            orientation = 'PENDANT'
            attachment_face = 'TOP'  # Attaches at top, hangs down
        elif 'upright' in element_type.lower():
            orientation = 'UPRIGHT'
            attachment_face = 'BOTTOM'  # Attaches at bottom, sprays up
        else:
            orientation = 'UNKNOWN'
            attachment_face = 'CENTER'

        # Derive name from element_type
        name = element_type.split(':')[0] if ':' in element_type else element_type

        # Insert geometry
        library_cursor.execute("""
            INSERT OR IGNORE INTO component_geometries
            (geometry_hash, vertices, faces, normals, vertex_count, face_count)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (geometry_hash, vertices_blob, faces_blob, normals_blob, vertex_count, face_count))

        # Insert component definition
        library_cursor.execute("""
            INSERT OR IGNORE INTO component_definitions
            (type_id, name, geometry_hash,
             local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
             attachment_face, orientation, vertex_count, face_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (type_id, name, geometry_hash,
              float(local_min[0]), float(local_max[0]),
              float(local_min[1]), float(local_max[1]),
              float(local_min[2]), float(local_max[2]),
              attachment_face, orientation, vertex_count, face_count))

        extracted += 1

    # Add placement rules for sprinklers
    library_cursor.execute("""
        INSERT OR IGNORE INTO placement_rules
        (component_id, host_type, offset_from_host, grid_spacing, clearance_radius)
        SELECT id, 'CEILING', 0.0, 4.6, 2.3
        FROM component_definitions
        WHERE name LIKE '%pendent%'
    """)

    library_cursor.execute("""
        INSERT OR IGNORE INTO placement_rules
        (component_id, host_type, offset_from_host, grid_spacing, clearance_radius)
        SELECT id, 'FLOOR', 0.0, 4.6, 2.3
        FROM component_definitions
        WHERE name LIKE '%upright%'
    """)

    return extracted


def main():
    print("=" * 70)
    print("CREATING SPRINKLER COMPONENT LIBRARY")
    print("=" * 70)

    # Connect to databases
    terminal_conn = sqlite3.connect(TERMINAL_DB)
    terminal_cursor = terminal_conn.cursor()

    LIBRARY_DB.parent.mkdir(parents=True, exist_ok=True)
    library_conn = sqlite3.connect(LIBRARY_DB)
    library_cursor = library_conn.cursor()

    # Create schema
    print("\nCreating library schema...")
    create_library_schema(library_cursor)
    library_conn.commit()

    # Extract sprinklers
    print("\nExtracting sprinklers from Terminal DB...")
    count = extract_sprinklers(terminal_cursor, library_cursor)
    library_conn.commit()

    print(f"\nExtracted {count} sprinkler definitions")

    # Verify
    print("\n" + "=" * 70)
    print("LIBRARY SUMMARY")
    print("=" * 70)

    library_cursor.execute("""
        SELECT
            ct.category,
            cd.orientation,
            cd.attachment_face,
            COUNT(*) as count,
            AVG(cd.vertex_count) as avg_verts
        FROM component_definitions cd
        JOIN component_types ct ON cd.type_id = ct.id
        GROUP BY ct.category, cd.orientation, cd.attachment_face
    """)

    print(f"\n{'Category':<12} {'Orientation':<10} {'Attach':<8} {'Count':<6} {'Avg Verts'}")
    print("-" * 50)
    for row in library_cursor.fetchall():
        print(f"{row[0]:<12} {row[1]:<10} {row[2]:<8} {row[3]:<6} {row[4]:.0f}")

    # Check library file size
    library_conn.close()
    terminal_conn.close()

    size_kb = LIBRARY_DB.stat().st_size / 1024
    print(f"\nLibrary file: {LIBRARY_DB}")
    print(f"Size: {size_kb:.1f} KB")


if __name__ == "__main__":
    main()
