# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S186: Assemble city.blend from per-building baked .blend files.

Links the root collection from each baked/{building}_baked.blend into a single
city.blend. Building offsets come from site_context table in the sandbox DB.

Usage:
    blender --background --python scripts/assemble_city_blend.py -- \
        --baked-dir baked/ \
        --output baked/city.blend \
        --db scripts/sandbox_1M.db
"""
import sys, os, time

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--baked-dir", default="DAGCompiler/baked", help="Directory with *_baked.blend files")
parser.add_argument("--output", default="DAGCompiler/baked/city.blend", help="Output city .blend")
parser.add_argument("--db", default=None, help="Sandbox DB for building offsets (optional)")
args = parser.parse_args(argv)

import bpy
import sqlite3
from pathlib import Path
from mathutils import Vector

baked_dir = Path(args.baked_dir).resolve()
out_path = Path(args.output).resolve()

if not baked_dir.exists():
    raise FileNotFoundError(f"Baked directory not found: {baked_dir}")

# Find all baked .blend files
baked_files = sorted(baked_dir.glob("*_baked.blend"))
if not baked_files:
    raise FileNotFoundError(f"No *_baked.blend files in {baked_dir}")

print(f"[S186] Assembling city from {len(baked_files)} building files")

# Building offsets from sandbox DB (if multi-building with per-building positions)
building_offsets = {}
if args.db and Path(args.db).exists():
    try:
        conn = sqlite3.connect(args.db)
        # Check if there's per-building offset data
        # For now, all buildings share the same site_context offset
        # (already applied at bake time), so no additional offset needed.
        # Future: building_offsets table for multi-site federations.
        conn.close()
    except Exception as e:
        print(f"  [S186] offset query skipped: {e}")

# Fresh scene
bpy.ops.wm.read_factory_settings(use_empty=True)

t0 = time.time()
linked = 0

for bf in baked_files:
    building_name = bf.stem.replace("_baked", "")
    print(f"  Linking: {building_name} ({bf.name})")

    with bpy.data.libraries.load(str(bf), link=True) as (src, dst):
        # Link root collection (building name, not discipline sub-collections)
        disc_suffixes = {'ARC','STR','MEP','ELEC','FP','OTHER',
                         'PLB','HEAT','HVAC','VENT','SAN','ACMV'}
        root_cols = [c for c in src.collections
                     if not any(c.endswith(f'_{d}') for d in disc_suffixes)]
        if root_cols:
            dst.collections = [root_cols[0]]
        elif src.collections:
            dst.collections = [src.collections[0]]

    # Create an instance empty for the linked collection
    for col in dst.collections:
        if col is not None:
            # Add to scene
            bpy.context.scene.collection.children.link(col)
            linked += 1

            # Apply building offset if available
            offset = building_offsets.get(building_name)
            if offset:
                # Create empty at offset, instance the collection
                empty = bpy.data.objects.new(f"Offset_{building_name}", None)
                empty.instance_type = 'COLLECTION'
                empty.instance_collection = col
                empty.location = offset
                bpy.context.scene.collection.objects.link(empty)

# Save
out_path.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(out_path))
elapsed = time.time() - t0
print(f"\n[S186] §PROOF CITY_ASSEMBLED buildings={linked} "
      f"file={out_path.name} {elapsed:.1f}s")
