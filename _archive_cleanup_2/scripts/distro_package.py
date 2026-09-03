# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S188: Package a fat baked .blend into a lean distribution bundle.

Reads a self-contained (fat) baked .blend, strips appended meshes back to
library links, produces a trimmed per-building library, manifest, and
optional signature.

The reverse operation (fatten) resolves all library links back into
appended meshes — used by recipients on first open or as a standalone
repack step.

See docs/PackageDistro.md for the full spec.

Usage — strip (fat → lean):
    blender --background --factory-startup \
        --python scripts/distro_package.py -- \
        --mode strip \
        --input DAGCompiler/baked/Hospital_baked.blend \
        --library library/library.blend \
        --output-dir distro/

Usage — fatten (lean → fat):
    blender --background --factory-startup \
        --python scripts/distro_package.py -- \
        --mode fatten \
        --input distro/Hospital_distro.blend \
        --library distro/Hospital_library.blend \
        --output-dir DAGCompiler/baked/

Output (strip):
    distro/{Building}_distro.blend       — lean, linked to trimmed library
    distro/{Building}_library.blend      — trimmed, only this building's meshes
    distro/{Building}_manifest.json      — element counts, hash list, checksums

Output (fatten):
    DAGCompiler/baked/{Building}_baked.blend — fat, self-contained, no library chain
"""
import sys
import os
import time
import json
import hashlib

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse

parser = argparse.ArgumentParser(description="Package or unpackage baked .blend files")
parser.add_argument("--mode", required=True, choices=["strip", "fatten"],
                    help="strip = fat→lean (for distro), fatten = lean→fat (for work)")
parser.add_argument("--input", required=True, help="Input .blend file")
parser.add_argument("--library", required=True,
                    help="strip: full library.blend; fatten: trimmed *_library.blend")
parser.add_argument("--output-dir", default="distro", help="Output directory")
args = parser.parse_args(argv)

import bpy
from pathlib import Path

input_path = Path(args.input).resolve()
lib_path = Path(args.library).resolve()
out_dir = Path(args.output_dir)
out_dir.mkdir(parents=True, exist_ok=True)

if not input_path.exists():
    raise FileNotFoundError(f"Input not found: {input_path}")
if not lib_path.exists():
    raise FileNotFoundError(f"Library not found: {lib_path}")


def get_building_name(blend_path):
    """Derive building name from filename: Hospital_baked.blend → Hospital."""
    stem = blend_path.stem
    for suffix in ('_baked', '_distro', '_library'):
        stem = stem.replace(suffix, '')
    return stem


def sha256_file(path):
    """SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(65536), b''):
            h.update(chunk)
    return h.hexdigest()


# ── STRIP: fat → lean ────────────────────────────────────────────────────────

def do_strip():
    """Strip appended meshes from fat .blend, produce lean distro + trimmed library."""
    t0 = time.time()
    building = get_building_name(input_path)
    print(f"\n{'='*60}")
    print(f"[S188] STRIP: {building} (fat → lean)")
    print(f"{'='*60}")

    # ── Step 1: Build trimmed library ──
    # Open a clean scene, append only the meshes used by this building,
    # save as the trimmed per-building library.
    print(f"  Step 1: Building trimmed library...")
    bpy.ops.wm.read_factory_settings(use_empty=True)

    # Load the fat .blend to discover which mesh names it uses
    mesh_names = set()
    with bpy.data.libraries.load(str(input_path), link=True) as (src, _dst):
        mesh_names = set(src.meshes)

    # Clear — we only wanted the name list
    bpy.ops.wm.read_factory_settings(use_empty=True)

    # Append those meshes from the full library (link=False → local copies)
    t_lib = time.time()
    with bpy.data.libraries.load(str(lib_path), link=False) as (src, dst):
        available = set(src.meshes)
        dst.meshes = [m for m in mesh_names if m in available]
    appended = sum(1 for m in dst.meshes if m is not None)
    print(f"  Appended {appended}/{len(mesh_names)} meshes from full library "
          f"in {time.time()-t_lib:.1f}s")

    # Save trimmed library
    lib_out = out_dir / f"{building}_library.blend"
    bpy.ops.wm.save_as_mainfile(filepath=str(lib_out.resolve()))
    lib_mb = lib_out.stat().st_size / (1024 * 1024)
    print(f"  Trimmed library: {lib_out.name} ({lib_mb:.1f} MB, {appended} meshes)")

    # ── Step 2: Build lean .blend ──
    # Re-open the fat .blend, replace appended meshes with links to trimmed library.
    print(f"\n  Step 2: Building lean .blend...")
    bpy.ops.wm.open_mainfile(filepath=str(input_path))

    # Collect mesh names before relinking
    obj_mesh_map = {}  # obj.name → mesh.name
    for obj in bpy.data.objects:
        if obj.type == 'MESH' and obj.data:
            obj_mesh_map[obj.name] = obj.data.name

    unique_meshes = set(obj_mesh_map.values())
    print(f"  Objects: {len(obj_mesh_map)}, Unique meshes: {len(unique_meshes)}")

    # Remove all local mesh data (will be replaced by links)
    for mesh in list(bpy.data.meshes):
        if mesh.name in unique_meshes:
            mesh.user_clear()
            bpy.data.meshes.remove(mesh)

    # Link meshes back from trimmed library (link=True → references)
    t_relink = time.time()
    with bpy.data.libraries.load(str(lib_out.resolve()), link=True) as (src, dst):
        available = set(src.meshes)
        dst.meshes = [m for m in unique_meshes if m in available]
    linked = sum(1 for m in dst.meshes if m is not None)
    print(f"  Relinked {linked} meshes from trimmed library in {time.time()-t_relink:.1f}s")

    # Reassign linked meshes to objects
    reassigned = 0
    for obj_name, mesh_name in obj_mesh_map.items():
        obj = bpy.data.objects.get(obj_name)
        mesh = bpy.data.meshes.get(mesh_name)
        if obj and mesh:
            obj.data = mesh
            reassigned += 1

    print(f"  Reassigned {reassigned}/{len(obj_mesh_map)} objects")

    # Save lean .blend
    distro_out = out_dir / f"{building}_distro.blend"
    bpy.ops.wm.save_as_mainfile(filepath=str(distro_out.resolve()))
    distro_mb = distro_out.stat().st_size / (1024 * 1024)
    print(f"  Lean .blend: {distro_out.name} ({distro_mb:.1f} MB)")

    # ── Step 3: Manifest ──
    print(f"\n  Step 3: Writing manifest...")
    # Count elements per discipline by scanning collections
    disc_counts = {}
    total_objects = 0
    for col in bpy.data.collections:
        parts = col.name.rsplit('_', 1)
        if len(parts) == 2:
            disc = parts[1]
            count = len([o for o in col.objects if o.type == 'MESH'])
            if count > 0:
                disc_counts[disc] = disc_counts.get(disc, 0) + count
                total_objects += count

    manifest = {
        "building": building,
        "format": "lean",
        "source_fat": input_path.name,
        "distro_blend": distro_out.name,
        "library_blend": lib_out.name,
        "total_objects": total_objects,
        "unique_meshes": len(unique_meshes),
        "discipline_counts": disc_counts,
        "mesh_hashes": sorted(unique_meshes),
        "checksums": {
            distro_out.name: sha256_file(distro_out),
            lib_out.name: sha256_file(lib_out),
        },
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }

    manifest_out = out_dir / f"{building}_manifest.json"
    with open(manifest_out, 'w') as f:
        json.dump(manifest, f, indent=2)
    print(f"  Manifest: {manifest_out.name}")

    # ── Summary ──
    elapsed = time.time() - t0
    fat_mb = input_path.stat().st_size / (1024 * 1024)
    print(f"\n  [S188] §PROOF STRIP bld={building} "
          f"fat={fat_mb:.1f}MB lean={distro_mb:.1f}MB lib={lib_mb:.1f}MB "
          f"objects={total_objects} meshes={len(unique_meshes)} "
          f"ratio={distro_mb/max(fat_mb,0.1)*100:.0f}% {elapsed:.1f}s")

    return distro_out, lib_out, manifest_out


# ── FATTEN: lean → fat ───────────────────────────────────────────────────────

def do_fatten():
    """Resolve library links in a lean .blend, produce self-contained fat .blend."""
    t0 = time.time()
    building = get_building_name(input_path)
    print(f"\n{'='*60}")
    print(f"[S188] FATTEN: {building} (lean → fat)")
    print(f"{'='*60}")

    # Open the lean .blend
    bpy.ops.wm.open_mainfile(filepath=str(input_path))

    # Collect all linked meshes (they reference the trimmed library)
    linked_meshes = set()
    for mesh in bpy.data.meshes:
        if mesh.library:
            linked_meshes.add(mesh.name)

    print(f"  Found {len(linked_meshes)} linked meshes to localise")

    if not linked_meshes:
        print(f"  Nothing to fatten — file may already be self-contained")
        return

    # Record object → mesh assignments before localising
    obj_mesh_map = {}
    for obj in bpy.data.objects:
        if obj.type == 'MESH' and obj.data and obj.data.name in linked_meshes:
            obj_mesh_map[obj.name] = obj.data.name

    # Append (not link) from the library to get local copies
    t_append = time.time()
    with bpy.data.libraries.load(str(lib_path), link=False) as (src, dst):
        available = set(src.meshes)
        dst.meshes = [m for m in linked_meshes if m in available]
    appended = sum(1 for m in dst.meshes if m is not None)
    print(f"  Appended {appended} meshes in {time.time()-t_append:.1f}s")

    # Blender may create ".001" suffixed names for the appended copies
    # since the linked versions still exist. Find and reassign.
    reassigned = 0
    for obj_name, orig_mesh_name in obj_mesh_map.items():
        obj = bpy.data.objects.get(obj_name)
        if not obj:
            continue
        # Prefer the local (non-library) copy
        local_mesh = None
        for mesh in bpy.data.meshes:
            if mesh.name.startswith(orig_mesh_name) and not mesh.library:
                local_mesh = mesh
                break
        if local_mesh:
            obj.data = local_mesh
            reassigned += 1

    # Clean up orphaned linked meshes (they have 0 users now)
    for mesh in list(bpy.data.meshes):
        if mesh.library and mesh.users == 0:
            bpy.data.meshes.remove(mesh)

    # Rename ".001" suffixed meshes back to originals where possible
    for mesh in bpy.data.meshes:
        if not mesh.library and '.' in mesh.name:
            base = mesh.name.rsplit('.', 1)[0]
            if not bpy.data.meshes.get(base):
                mesh.name = base

    print(f"  Reassigned {reassigned}/{len(obj_mesh_map)} objects to local meshes")

    # Save fat .blend
    fat_out = out_dir / f"{building}_baked.blend"
    bpy.ops.wm.save_as_mainfile(filepath=str(fat_out.resolve()))
    fat_mb = fat_out.stat().st_size / (1024 * 1024)
    lean_mb = input_path.stat().st_size / (1024 * 1024)
    elapsed = time.time() - t0

    print(f"\n  [S188] §PROOF FATTEN bld={building} "
          f"lean={lean_mb:.1f}MB fat={fat_mb:.1f}MB "
          f"meshes={appended} reassigned={reassigned} {elapsed:.1f}s")


# ── Main ──────────────────────────────────────────────────────────────────────

if args.mode == "strip":
    do_strip()
elif args.mode == "fatten":
    do_fatten()
