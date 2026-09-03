# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
"""
Micro-benchmark: new() only vs new() + link()
Run: blender --background --python scripts/stress_micro_benchmark.py
"""
import bpy
import time
import resource

TARGET = 50_000

def ram_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024

def run(label, fn):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    m = bpy.data.meshes.new("cube")
    m.from_pydata([(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)],[],[(0,1,2,3),(4,5,6,7),(0,1,5,4),(2,3,7,6),(0,3,7,4),(1,2,6,5)])
    t0 = time.perf_counter()
    fn(m)
    t = time.perf_counter() - t0
    print(f"{label}: {t:.2f}s — {TARGET/t:.0f} obj/s — RAM {ram_mb():.0f} MB")

def test_new_only(m):
    for i in range(TARGET):
        obj = bpy.data.objects.new(f"O{i}", m)
        obj['guid'] = f"G{i}"
        obj.location = (i * 0.01, 0, 0)

def test_new_plus_link(m):
    coll = bpy.data.collections.new("Test")
    for i in range(TARGET):
        obj = bpy.data.objects.new(f"O{i}", m)
        obj['guid'] = f"G{i}"
        obj.location = (i * 0.01, 0, 0)
        coll.objects.link(obj)

run("A: new() only    ", test_new_only)
run("B: new() + link()", test_new_plus_link)
