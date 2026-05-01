# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S173: Standalone proof that library.blend meshes are correct.

P1 BAKE_INTEGRITY: library.blend mesh vertex count == library.db vertex count (per hash)
P2 LINK_SPEED:     linking from .blend vs from_pydata() from .db — timing comparison
P3 ORIENTATION:    pick N elements from extracted DB, reconstruct world vertex from
                   (library_mesh_local_vert × stored_rotation + stored_centre)
                   vs IFC iterator world vertex. This proves facing is correct.

Usage:
    blender --background --python scripts/test_library_blend.py -- \
        --blend library/library.blend \
        --library library/component_library.db \
        --db DAGCompiler/lib/input/Hospital_extracted.db \
        --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
        --pattern "Hospital_IFC4_ARC.ifc"
"""
import sys, os, time, math

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--blend", required=True)
parser.add_argument("--library", required=True)
parser.add_argument("--db", required=True)
parser.add_argument("--ifc-dir", required=True)
parser.add_argument("--pattern", default="*.ifc")
parser.add_argument("--max", type=int, default=50)
args = parser.parse_args(argv)

import bpy
import sqlite3
import numpy as np
from pathlib import Path

# ── P1: BAKE_INTEGRITY ──
print("=" * 60)
print("P1: BAKE_INTEGRITY — vertex counts match between .blend and .db")
print("=" * 60)

bpy.ops.wm.read_factory_settings(use_empty=True)

lib_db = sqlite3.connect(args.library)
db_hashes = {}
for r in lib_db.execute("SELECT geometry_hash, vertex_count FROM component_geometries WHERE vertices IS NOT NULL"):
    db_hashes[r[0]] = r[1]

# Link all objects from library.blend
blend_path = os.path.abspath(args.blend)
with bpy.data.libraries.load(blend_path, link=True) as (data_from, data_to):
    data_to.objects = list(data_from.objects)

link_time_start = time.time()
linked_count = 0
for obj in data_to.objects:
    if obj is not None:
        bpy.context.scene.collection.objects.link(obj)
        linked_count += 1
link_elapsed = time.time() - link_time_start

p1_ok = p1_fail = 0
for obj in bpy.data.objects:
    ghash = obj.name
    if ghash not in db_hashes:
        continue
    blend_vcount = len(obj.data.vertices)
    db_vcount = db_hashes[ghash]
    if blend_vcount == db_vcount:
        p1_ok += 1
    else:
        p1_fail += 1
        if p1_fail <= 3:
            print(f"  FAIL BAKE_INTEGRITY {ghash[:12]} blend={blend_vcount} db={db_vcount}")

tag1 = "PASS" if p1_fail == 0 and p1_ok > 0 else "FAIL"
print(f"§PROOF BAKE_INTEGRITY {tag1}  {p1_ok} ok, {p1_fail} fail")

# ── P2: LINK_SPEED ──
print(f"\n{'=' * 60}")
print(f"P2: LINK_SPEED — {linked_count:,} objects linked in {link_elapsed:.3f}s")
print(f"{'=' * 60}")
print(f"§PROOF LINK_SPEED {link_elapsed:.3f}s for {linked_count:,} objects ({linked_count/max(link_elapsed,0.001):.0f}/s)")

# ── P3: ORIENTATION — the real proof ──
print(f"\n{'=' * 60}")
print(f"P3: ORIENTATION — world vertex reconstruction from stored transform")
print(f"{'=' * 60}")

# Build a lookup of linked mesh verts by hash
blend_meshes = {}
for obj in bpy.data.objects:
    ghash = obj.name
    if ghash in db_hashes:
        verts = np.array([v.co[:] for v in obj.data.vertices], dtype=np.float64)
        blend_meshes[ghash] = verts

ext_db = sqlite3.connect(args.db)
stored = {}
for r in ext_db.execute("""
    SELECT et.guid, et.center_x, et.center_y, et.center_z,
           et.rotation_x, et.rotation_y, et.rotation_z, ei.geometry_hash
    FROM element_transforms et
    JOIN element_instances ei ON ei.guid = et.guid
"""):
    stored[r[0]] = r[1:]

def euler_to_matrix(a, b, c):
    return np.array([
        [math.cos(b)*math.cos(c), math.sin(a)*math.sin(b)*math.cos(c)-math.cos(a)*math.sin(c), math.cos(a)*math.sin(b)*math.cos(c)+math.sin(a)*math.sin(c)],
        [math.cos(b)*math.sin(c), math.sin(a)*math.sin(b)*math.sin(c)+math.cos(a)*math.cos(c), math.cos(a)*math.sin(b)*math.sin(c)-math.sin(a)*math.cos(c)],
        [-math.sin(b),             math.sin(a)*math.cos(b),                                      math.cos(a)*math.cos(b)]
    ])

# Iterate IFC with USE_WORLD_COORDS=True for ground truth
import ifcopenshell, ifcopenshell.geom

ifc_dir = Path(args.ifc_dir)
ifc_files = sorted(ifc_dir.glob(args.pattern))

p3_ok = p3_fail = 0
p3_worst = 0.0
checked = 0

for ifc_path in ifc_files:
    if checked >= args.max:
        break
    ifc = ifcopenshell.open(str(ifc_path))

    # World-coords iterator (ground truth)
    settings_w = ifcopenshell.geom.settings()
    settings_w.set(settings_w.USE_WORLD_COORDS, True)
    settings_w.set(settings_w.WELD_VERTICES, True)
    it_w = ifcopenshell.geom.iterator(settings_w, ifc)
    if not it_w.initialize():
        continue

    while checked < args.max:
        shape = it_w.get()
        guid = shape.guid
        if guid not in stored:
            if not it_w.next():
                break
            continue

        cx, cy, cz, rx, ry, rz, ghash = stored[guid]
        if ghash not in blend_meshes:
            if not it_w.next():
                break
            continue

        world_verts = np.array(shape.geometry.verts, dtype=np.float64).reshape(-1, 3)
        lib_verts = blend_meshes[ghash]

        if len(world_verts) < 3 or len(lib_verts) < 3:
            if not it_w.next():
                break
            continue

        R = euler_to_matrix(rx, ry, rz)
        centre = np.array([cx, cy, cz])

        # Reconstruct world position of vertex 0 from library mesh + stored transform
        v_lib_local = lib_verts[0]
        v_world_truth = world_verts[0]
        v_reconstructed = R @ v_lib_local + centre

        err = np.linalg.norm(v_reconstructed - v_world_truth)
        if err > p3_worst:
            p3_worst = err

        if err > 0.01:  # 1cm tolerance
            p3_fail += 1
            if p3_fail <= 5:
                print(f"  FAIL ORIENTATION {guid[:16]} {shape.type} err={err:.4f}m")
                print(f"    truth=({v_world_truth[0]:.3f},{v_world_truth[1]:.3f},{v_world_truth[2]:.3f})")
                print(f"    recon=({v_reconstructed[0]:.3f},{v_reconstructed[1]:.3f},{v_reconstructed[2]:.3f})")
                print(f"    centre=({cx:.3f},{cy:.3f},{cz:.3f}) rot=({rx:.4f},{ry:.4f},{rz:.4f})")
        else:
            p3_ok += 1

        checked += 1
        if not it_w.next():
            break

tag3 = "PASS" if p3_fail == 0 and p3_ok > 0 else "FAIL"
print(f"§PROOF ORIENTATION {tag3}  {p3_ok} ok, {p3_fail} fail  worst_err={p3_worst:.4f}m")

# ── SUMMARY ──
all_pass = (p1_fail == 0 and p1_ok > 0) and (p3_fail == 0 and p3_ok > 0)
print(f"\n§PROOF LIBRARY_BLEND {'PASS' if all_pass else 'FAIL'}")
sys.exit(0 if all_pass else 1)
