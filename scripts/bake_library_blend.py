# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S173: Bake component_library.db → library.blend

One-time conversion: each unique geometry_hash becomes a Blender Mesh datablock
named by its hash. After baking, Blender loads via link (instant) instead of
from_pydata() per element (slow).

Usage:
    blender --background --python scripts/bake_library_blend.py -- \
        --library library/component_library.db \
        --output library/library.blend
"""
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
import sys, os, struct, time

# Parse args after '--'
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--library", required=True, help="Path to component_library.db")
parser.add_argument("--output", required=True, help="Output .blend path")
args = parser.parse_args(argv)

import bpy
import sqlite3
import numpy as np

def unpack_vertices(blob):
    """BLOB → list of (x,y,z) tuples."""
    arr = np.frombuffer(blob, dtype=np.float32).reshape(-1, 3)
    return [tuple(v) for v in arr]

def unpack_faces(blob):
    """BLOB → list of (i,j,k) tuples."""
    arr = np.frombuffer(blob, dtype=np.int32).reshape(-1, 3)
    return [tuple(f) for f in arr]

# Clean default scene
bpy.ops.wm.read_factory_settings(use_empty=True)

lib = sqlite3.connect(args.library)
cursor = lib.execute("""
    SELECT geometry_hash, vertices, faces, vertex_count, face_count
    FROM component_geometries
    WHERE vertices IS NOT NULL AND faces IS NOT NULL
""")

t0 = time.time()
baked = 0
skipped = 0

for row in cursor:
    ghash, vblob, fblob, v_count, f_count = row
    if not vblob or not fblob:
        skipped += 1
        continue

    try:
        verts = unpack_vertices(vblob)
        faces = unpack_faces(fblob)
    except Exception as e:
        print(f"  SKIP {ghash[:12]}: {e}")
        skipped += 1
        continue

    # Create mesh datablock named by hash
    mesh = bpy.data.meshes.new(name=ghash)
    mesh.from_pydata(verts, [], faces)
    mesh.update()

    # Add placeholder material slot — required for object-level material
    # override when mesh is linked (read-only) at load time
    mesh.materials.append(None)

    # Create object so it's linkable (meshes without objects can't be linked easily)
    obj = bpy.data.objects.new(name=ghash, object_data=mesh)
    # Add to scene collection so it persists on save
    bpy.context.scene.collection.objects.link(obj)

    baked += 1
    if baked % 2000 == 0:
        elapsed = time.time() - t0
        print(f"  {baked:,} meshes baked ({elapsed:.1f}s, {baked/elapsed:.0f}/s)")

elapsed = time.time() - t0
print(f"\n§PROOF BAKE {baked:,} meshes baked, {skipped} skipped, {elapsed:.1f}s")

# Save
bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args.output))
file_size = os.path.getsize(args.output)
print(f"§PROOF BAKE_FILE {args.output} {file_size/1024/1024:.1f}MB")
print(f"§PROOF BAKE PASS")
