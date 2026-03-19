#!/usr/bin/env python3
"""F002: Fix IfcRampFlight LOD — triangular prism (6 vertices) → box (8 vertices).

G5-PROVENANCE requires vertex_count >= 8 (minimum for a box).
The IfcRampFlight geometry_hash '22cf7694a40d1ecf' had only 6 vertices
(triangular prism). This script rebuilds it as a proper 8-vertex box
with the same bounding dimensions.

Implementing TerminalAnalysis.md §CTFL Review Status F2 — Witness: G5-PROVENANCE
"""
import sqlite3
import struct
import sys

DB_PATH = "library/component_library.db"
HASH = "22cf7694a40d1ecf"

def main():
    conn = sqlite3.connect(DB_PATH)
    row = conn.execute(
        "SELECT vertex_count FROM component_geometries WHERE geometry_hash = ?",
        (HASH,)
    ).fetchone()

    if row is None:
        print(f"[F002] geometry_hash {HASH} not found — skipping")
        return

    if row[0] >= 8:
        print(f"[F002] already fixed (vertex_count={row[0]}) — skipping")
        return

    # Original bounding box from the 6-vertex prism:
    # X: [-0.499, 0.499], Y: [-0.526, 0.526], Z: [-0.1782, 0.1782]
    box = [
        ( 0.4990,  0.5260,  0.1782),
        ( 0.4990,  0.5260, -0.1782),
        ( 0.4990, -0.5260,  0.1782),
        ( 0.4990, -0.5260, -0.1782),
        (-0.4990,  0.5260,  0.1782),
        (-0.4990,  0.5260, -0.1782),
        (-0.4990, -0.5260,  0.1782),
        (-0.4990, -0.5260, -0.1782),
    ]

    verts_blob = b"".join(struct.pack("<fff", *v) for v in box)

    # 12 triangles (2 per face, 6 faces)
    faces = [
        (0,1,2), (1,3,2),   # +X
        (4,6,5), (5,6,7),   # -X
        (0,4,1), (1,4,5),   # +Y
        (2,3,6), (3,7,6),   # -Y
        (0,2,4), (2,6,4),   # +Z
        (1,5,3), (3,5,7),   # -Z
    ]
    faces_blob = b"".join(struct.pack("<III", *f) for f in faces)

    face_normals = [
        ( 1, 0, 0), ( 1, 0, 0),
        (-1, 0, 0), (-1, 0, 0),
        ( 0, 1, 0), ( 0, 1, 0),
        ( 0,-1, 0), ( 0,-1, 0),
        ( 0, 0, 1), ( 0, 0, 1),
        ( 0, 0,-1), ( 0, 0,-1),
    ]
    normals_blob = b"".join(
        struct.pack("<fff", float(n[0]), float(n[1]), float(n[2]))
        for n in face_normals
    )

    conn.execute(
        """UPDATE component_geometries
           SET vertices = ?, faces = ?, normals = ?,
               vertex_count = 8, face_count = 12
           WHERE geometry_hash = ?""",
        (verts_blob, faces_blob, normals_blob, HASH),
    )
    conn.commit()

    # Verify
    vc = conn.execute(
        "SELECT vertex_count FROM component_geometries WHERE geometry_hash = ?",
        (HASH,)
    ).fetchone()[0]
    conn.close()

    print(f"[F002] IfcRampFlight LOD fixed: 6→{vc} vertices, 8→12 faces")

if __name__ == "__main__":
    main()
