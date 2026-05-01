# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Analyze sprinkler geometry to determine origin and orientation.
Extract one sprinkler's vertices and determine attachment convention.
"""

import sqlite3
import struct
import numpy as np
from pathlib import Path

DB_PATH = Path("/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db")

def parse_vertices(blob: bytes) -> np.ndarray:
    """Parse vertex blob to numpy array of (x, y, z) coordinates."""
    vertex_count = len(blob) // 12  # 3 floats * 4 bytes
    vertices = np.zeros((vertex_count, 3), dtype=np.float32)
    for i in range(vertex_count):
        x, y, z = struct.unpack('<fff', blob[i*12:(i+1)*12])
        vertices[i] = [x, y, z]
    return vertices

def analyze_sprinkler(cursor, guid: str):
    """Analyze a single sprinkler's geometry."""

    # Get transform and bbox
    cursor.execute("""
        SELECT
            et.center_x, et.center_y, et.center_z,
            er.minX, er.maxX, er.minY, er.maxY, er.minZ, er.maxZ
        FROM elements_meta em
        JOIN element_transforms et ON em.guid = et.guid
        JOIN elements_rtree er ON em.id = er.id
        WHERE em.guid = ?
    """, (guid,))
    row = cursor.fetchone()
    center_x, center_y, center_z = row[0], row[1], row[2]
    minX, maxX, minY, maxY, minZ, maxZ = row[3], row[4], row[5], row[6], row[7], row[8]

    # Get geometry
    cursor.execute("""
        SELECT bg.vertices, bg.vertex_count, bg.face_count
        FROM elements_meta em
        JOIN element_instances ei ON em.guid = ei.guid
        JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
        WHERE em.guid = ?
    """, (guid,))
    row = cursor.fetchone()
    vertices = parse_vertices(row[0])
    vertex_count = row[1]
    face_count = row[2]

    print(f"Sprinkler GUID: {guid}")
    print(f"  Transform center: ({center_x:.3f}, {center_y:.3f}, {center_z:.3f})")
    print(f"  BBox: X[{minX:.3f}, {maxX:.3f}] Y[{minY:.3f}, {maxY:.3f}] Z[{minZ:.3f}, {maxZ:.3f}]")
    print(f"  BBox dimensions: {(maxX-minX)*1000:.0f}mm x {(maxY-minY)*1000:.0f}mm x {(maxZ-minZ)*1000:.0f}mm")
    print(f"  Geometry: {vertex_count} vertices, {face_count} faces")

    # Analyze vertex distribution
    print(f"\n  Vertex bounds:")
    print(f"    X: [{vertices[:,0].min():.3f}, {vertices[:,0].max():.3f}]")
    print(f"    Y: [{vertices[:,1].min():.3f}, {vertices[:,1].max():.3f}]")
    print(f"    Z: [{vertices[:,2].min():.3f}, {vertices[:,2].max():.3f}]")

    # Check how many vertices at top (maxZ) vs bottom (minZ)
    z_tolerance = 0.01  # 10mm
    verts_at_top = np.sum(np.abs(vertices[:,2] - vertices[:,2].max()) < z_tolerance)
    verts_at_bottom = np.sum(np.abs(vertices[:,2] - vertices[:,2].min()) < z_tolerance)

    print(f"\n  Vertex concentration:")
    print(f"    At top (Z≈{vertices[:,2].max():.3f}): {verts_at_top} vertices")
    print(f"    At bottom (Z≈{vertices[:,2].min():.3f}): {verts_at_bottom} vertices")

    # Centroid vs bbox center
    centroid = vertices.mean(axis=0)
    bbox_center = np.array([(minX+maxX)/2, (minY+maxY)/2, (minZ+maxZ)/2])
    print(f"\n  Centroid: ({centroid[0]:.3f}, {centroid[1]:.3f}, {centroid[2]:.3f})")
    print(f"  BBox center: ({bbox_center[0]:.3f}, {bbox_center[1]:.3f}, {bbox_center[2]:.3f})")
    print(f"  Offset (centroid - bbox_center): ({centroid[0]-bbox_center[0]:.3f}, {centroid[1]-bbox_center[1]:.3f}, {centroid[2]-bbox_center[2]:.3f})")

    # Attachment inference
    if verts_at_top > verts_at_bottom * 2:
        print(f"\n  ATTACHMENT INFERENCE: Top-heavy → Likely ceiling mount, origin at TOP")
    elif verts_at_bottom > verts_at_top * 2:
        print(f"\n  ATTACHMENT INFERENCE: Bottom-heavy → Likely floor mount, origin at BOTTOM")
    else:
        print(f"\n  ATTACHMENT INFERENCE: Balanced → Center origin or side mount")

    return vertices

def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Get 3 sample sprinklers at different Z levels
    cursor.execute("""
        SELECT em.guid, er.minZ
        FROM elements_meta em
        JOIN elements_rtree er ON em.id = er.id
        WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
        ORDER BY er.minZ
        LIMIT 1
    """)
    low_sprinkler = cursor.fetchone()[0]

    cursor.execute("""
        SELECT em.guid, er.minZ
        FROM elements_meta em
        JOIN elements_rtree er ON em.id = er.id
        WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
          AND er.minZ BETWEEN 14 AND 16
        LIMIT 1
    """)
    mid_sprinkler = cursor.fetchone()[0]

    cursor.execute("""
        SELECT em.guid, er.minZ
        FROM elements_meta em
        JOIN elements_rtree er ON em.id = er.id
        WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
        ORDER BY er.minZ DESC
        LIMIT 1
    """)
    high_sprinkler = cursor.fetchone()[0]

    print("=" * 70)
    print("SPRINKLER GEOMETRY ANALYSIS")
    print("=" * 70)

    for i, guid in enumerate([low_sprinkler, mid_sprinkler, high_sprinkler], 1):
        print(f"\n--- Sample {i} ---")
        analyze_sprinkler(cursor, guid)
        print()

    conn.close()

if __name__ == "__main__":
    main()
