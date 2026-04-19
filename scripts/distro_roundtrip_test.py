#!/usr/bin/env python3
"""
S189z: Distro round-trip test — fat → strip → reconstitute → verify.

Proves the lean distro cycle:
  1. STRIP:  fat baked .blend → lean .blend (empty meshes, geometry_hash preserved)
                              + trimmed component_library.db (only needed hashes)
  2. RECONSTITUTE: lean .blend + trimmed DB → fat .blend (meshes tessellated from BLOBs)
  3. VERIFY: reconstituted .blend has same object count, mesh vertex counts, collections

Usage:
    blender --background --factory-startup --python scripts/distro_roundtrip_test.py -- \
        --input DAGCompiler/baked/Hospital_baked.blend \
        --library-db library/component_library.db \
        --output-dir DAGCompiler/lib/input/DISTRO/test

See docs/PackageDistro.md for the lean distro architecture.
"""
import sys
import os
import time
import struct
import sqlite3

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True, help="Fat baked .blend file")
parser.add_argument("--library-db", required=True, help="Path to component_library.db")
parser.add_argument("--output-dir", default="DAGCompiler/lib/input/DISTRO/test")
args = parser.parse_args(argv)

import bpy
from pathlib import Path

input_path = Path(args.input).resolve()
lib_db_path = Path(args.library_db).resolve()
out_dir = Path(args.output_dir)
out_dir.mkdir(parents=True, exist_ok=True)

building = input_path.stem.replace('_baked', '').replace('_chunk', '_C')

def _ts():
    from datetime import datetime
    return datetime.now().strftime('%H:%M:%S.%f')[:-3]

def unpack_vertices(blob):
    if not blob:
        return []
    floats = struct.unpack(f'<{len(blob)//4}f', blob)
    return [(floats[i], floats[i+1], floats[i+2]) for i in range(0, len(floats), 3)]

def unpack_faces(blob):
    if not blob:
        return []
    ints = struct.unpack(f'<{len(blob)//4}I', blob)
    return [(ints[i], ints[i+1], ints[i+2]) for i in range(0, len(ints), 3)]


# ══════════════════════════════════════════════════════════════════════
# PHASE 1: Open fat .blend, snapshot original state
# ══════════════════════════════════════════════════════════════════════
print(f"\n{'='*70}")
print(f"DISTRO ROUND-TRIP TEST: {building}")
print(f"{'='*70}")
t0 = time.time()

print(f"\n[1] Opening fat .blend: {input_path.name}")
bpy.ops.wm.open_mainfile(filepath=str(input_path))

# Snapshot: object count, collections, per-mesh vertex counts
orig_objects = len([o for o in bpy.data.objects if o.type == 'MESH'])
orig_collections = sorted([c.name for c in bpy.data.collections])
orig_mesh_verts = {}  # mesh.name → vertex count
orig_hashes = {}      # mesh.name → geometry_hash (from mesh name = hash)
for mesh in bpy.data.meshes:
    orig_mesh_verts[mesh.name] = len(mesh.vertices)
    orig_hashes[mesh.name] = mesh.name  # mesh is named by geometry_hash

orig_unique_meshes = len(orig_mesh_verts)
orig_total_verts = sum(orig_mesh_verts.values())
print(f"  Objects: {orig_objects}, Unique meshes: {orig_unique_meshes}, "
      f"Total vertices: {orig_total_verts:,}, Collections: {len(orig_collections)}")


# ══════════════════════════════════════════════════════════════════════
# PHASE 2: STRIP — clear mesh geometry, keep structure + hash refs
# ══════════════════════════════════════════════════════════════════════
print(f"\n[2] STRIP: clearing mesh geometry...")
t_strip = time.time()

# Store geometry_hash as custom property before clearing
hashes_needed = set()
for mesh in bpy.data.meshes:
    mesh['geometry_hash'] = mesh.name
    hashes_needed.add(mesh.name)
    mesh.clear_geometry()

# Save lean .blend
lean_path = out_dir / f"{building}_lean.blend"
bpy.ops.wm.save_as_mainfile(filepath=str(lean_path.resolve()))
lean_mb = lean_path.stat().st_size / (1024 * 1024)
fat_mb = input_path.stat().st_size / (1024 * 1024)

print(f"  Lean: {lean_path.name} ({lean_mb:.1f} MB) — was {fat_mb:.1f} MB "
      f"(ratio: {lean_mb/fat_mb*100:.0f}%)")
print(f"  Hashes preserved: {len(hashes_needed)}")

# Create trimmed component_library.db with only needed hashes
trimmed_db_path = out_dir / f"{building}_library.db"
print(f"  Trimming library DB to {len(hashes_needed)} hashes...")
src_conn = sqlite3.connect(str(lib_db_path))
dst_conn = sqlite3.connect(str(trimmed_db_path))
dst_conn.execute("""CREATE TABLE IF NOT EXISTS component_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB,
    faces BLOB
)""")
dst_conn.execute("BEGIN")
copied = 0
for ci in range(0, len(hashes_needed), 999):
    chunk = list(hashes_needed)[ci:ci+999]
    ph = ','.join('?' * len(chunk))
    rows = src_conn.execute(f"""
        SELECT geometry_hash, vertices, faces
        FROM component_geometries WHERE geometry_hash IN ({ph})
    """, chunk).fetchall()
    dst_conn.executemany(
        "INSERT OR IGNORE INTO component_geometries VALUES (?,?,?)", rows)
    copied += len(rows)
dst_conn.execute("COMMIT")
dst_conn.close()
src_conn.close()
trimmed_mb = trimmed_db_path.stat().st_size / (1024 * 1024)
print(f"  Trimmed DB: {trimmed_db_path.name} ({trimmed_mb:.1f} MB, {copied} hashes)")

strip_s = time.time() - t_strip
print(f"  §STRIP done in {strip_s:.1f}s")


# ══════════════════════════════════════════════════════════════════════
# PHASE 3: RECONSTITUTE — open lean, tessellate from trimmed DB
# ══════════════════════════════════════════════════════════════════════
print(f"\n[3] RECONSTITUTE: tessellating from trimmed DB...")
t_recon = time.time()

# Re-open the lean .blend (meshes are empty)
bpy.ops.wm.open_mainfile(filepath=str(lean_path.resolve()))

# Verify meshes are empty
empty_count = sum(1 for m in bpy.data.meshes if len(m.vertices) == 0)
print(f"  Empty meshes: {empty_count}/{len(bpy.data.meshes)}")

# Tessellate from trimmed DB
lib_conn = sqlite3.connect(str(trimmed_db_path))
filled = 0
miss = 0
for mesh in bpy.data.meshes:
    ghash = mesh.get('geometry_hash', mesh.name)
    row = lib_conn.execute(
        "SELECT vertices, faces FROM component_geometries WHERE geometry_hash=?",
        (ghash,)).fetchone()
    if row and row[0]:
        verts = unpack_vertices(row[0])
        faces = unpack_faces(row[1])
        mesh.from_pydata(verts, [], faces)
        mesh.update()
        filled += 1
    else:
        miss += 1
lib_conn.close()
print(f"  Filled: {filled}, Miss: {miss}")

# Save reconstituted fat .blend
recon_path = out_dir / f"{building}_reconstituted.blend"
bpy.ops.wm.save_as_mainfile(filepath=str(recon_path.resolve()))
recon_mb = recon_path.stat().st_size / (1024 * 1024)

recon_s = time.time() - t_recon
print(f"  Reconstituted: {recon_path.name} ({recon_mb:.1f} MB) in {recon_s:.1f}s")


# ══════════════════════════════════════════════════════════════════════
# PHASE 4: VERIFY — compare reconstituted vs original
# ══════════════════════════════════════════════════════════════════════
print(f"\n[4] VERIFY: comparing reconstituted vs original...")

recon_objects = len([o for o in bpy.data.objects if o.type == 'MESH'])
recon_collections = sorted([c.name for c in bpy.data.collections])
recon_mesh_verts = {}
for mesh in bpy.data.meshes:
    recon_mesh_verts[mesh.name] = len(mesh.vertices)
recon_unique_meshes = len(recon_mesh_verts)
recon_total_verts = sum(recon_mesh_verts.values())

# Checks
checks = []
def check(name, expected, actual):
    ok = expected == actual
    status = "PASS" if ok else "FAIL"
    checks.append((name, ok))
    print(f"  {status}  {name}: expected={expected}, actual={actual}")

check("object_count", orig_objects, recon_objects)
check("unique_meshes", orig_unique_meshes, recon_unique_meshes)
check("total_vertices", orig_total_verts, recon_total_verts)
check("collection_count", len(orig_collections), len(recon_collections))
check("collection_names", orig_collections, recon_collections)

# Per-mesh vertex count match
vert_mismatches = 0
for mname, vcount in orig_mesh_verts.items():
    if recon_mesh_verts.get(mname, -1) != vcount:
        vert_mismatches += 1
        if vert_mismatches <= 3:
            print(f"    MISMATCH mesh={mname} orig={vcount} recon={recon_mesh_verts.get(mname, 'MISSING')}")
check("mesh_vertex_match", 0, vert_mismatches)

# File size within 10% (materials/custom props may differ slightly)
size_ratio = recon_mb / max(fat_mb, 0.1)
size_ok = 0.8 <= size_ratio <= 1.2
checks.append(("file_size_ratio", size_ok))
print(f"  {'PASS' if size_ok else 'FAIL'}  file_size_ratio: "
      f"fat={fat_mb:.1f}MB recon={recon_mb:.1f}MB ratio={size_ratio:.2f}")

# ── Summary ──
elapsed = time.time() - t0
n_pass = sum(1 for _, ok in checks if ok)
n_fail = sum(1 for _, ok in checks if not ok)
print(f"\n{'='*70}")
print(f"§PROOF DISTRO_ROUNDTRIP bld={building} "
      f"pass={n_pass} fail={n_fail} "
      f"fat={fat_mb:.1f}MB lean={lean_mb:.1f}MB trimDB={trimmed_mb:.1f}MB "
      f"recon={recon_mb:.1f}MB elapsed={elapsed:.1f}s")
print(f"  Distro bundle: {lean_mb:.1f}MB .blend + {trimmed_mb:.1f}MB DB "
      f"= {lean_mb+trimmed_mb:.1f}MB total (vs {fat_mb:.1f}MB fat)")
print(f"{'='*70}")

if n_fail:
    print(f"\n  *** {n_fail} CHECKS FAILED ***")
    sys.exit(1)
else:
    print(f"\n  ALL {n_pass} CHECKS PASSED")
