# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S173: Simulate GN Instance on Points placement.

Tests what Blender GN does: place mesh at (cx,cy,cz), rotate by (rx,ry,rz).
GN rotates around mesh origin (0,0,0). If mesh verts are offset from origin,
rotation swings them → spikes.

Compares resulting world bbox against stored rtree bbox.
Runs on any extracted DB — no Blender needed.

Usage:
  python3 scripts/test_gn_placement.py DAGCompiler/lib/input/Hospital_extracted.db
  python3 scripts/test_gn_placement.py DAGCompiler/lib/input/Clinic_extracted.db
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
import sqlite3, numpy as np, math, sys, os

def euler_to_matrix(rx, ry, rz):
    a, b, c = rx, ry, rz
    return np.array([
        [math.cos(b)*math.cos(c), math.sin(a)*math.sin(b)*math.cos(c)-math.cos(a)*math.sin(c), math.cos(a)*math.sin(b)*math.cos(c)+math.sin(a)*math.sin(c)],
        [math.cos(b)*math.sin(c), math.sin(a)*math.sin(b)*math.sin(c)+math.cos(a)*math.cos(c), math.cos(a)*math.sin(b)*math.sin(c)-math.sin(a)*math.cos(c)],
        [-math.sin(b),             math.sin(a)*math.cos(b),                                      math.cos(a)*math.cos(b)]
    ])

def test_db(db_path):
    db = sqlite3.connect(db_path)
    name = os.path.basename(db_path)

    # Check mesh source
    has_blobs = False
    try:
        row = db.execute("SELECT 1 FROM base_geometries WHERE vertices IS NOT NULL LIMIT 1").fetchone()
        has_blobs = row is not None
    except: pass

    # Try library if no blobs
    lib_conn = None
    if not has_blobs:
        for p in [os.path.dirname(db_path)]:
            search = p
            for _ in range(5):
                candidate = os.path.join(search, 'library', 'component_library.db')
                if os.path.exists(candidate):
                    lib_conn = sqlite3.connect(candidate)
                    break
                search = os.path.dirname(search)
            if lib_conn:
                break

    mesh_src = "base_geometries" if has_blobs else ("library" if lib_conn else "NONE")

    # Get rotated elements
    samples = db.execute("""
        SELECT et.guid, et.center_x, et.center_y, et.center_z,
               et.rotation_x, et.rotation_y, et.rotation_z,
               ei.geometry_hash,
               r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM element_transforms et
        JOIN element_instances ei ON ei.guid = et.guid
        JOIN elements_meta em ON em.guid = et.guid
        JOIN elements_rtree r ON r.id = em.id
        WHERE et.rotation_x != 0 OR et.rotation_y != 0 OR et.rotation_z != 0
        ORDER BY RANDOM() LIMIT 20
    """).fetchall()

    if not samples:
        print(f"{name}: no rotated elements")
        return

    pivot_ok = pivot_fail = 0
    pivot_worst = 0.0
    gn_ok = gn_fail = 0
    gn_worst = 0.0
    checked_hashes = set()

    for guid, cx, cy, cz, rx, ry, rz, ghash, x0, x1, y0, y1, z0, z1 in samples:
        # Fetch mesh
        vblob = None
        if has_blobs:
            row = db.execute("SELECT vertices FROM base_geometries WHERE geometry_hash=?", (ghash,)).fetchone()
            if row: vblob = row[0]
        elif lib_conn:
            row = lib_conn.execute("SELECT vertices FROM component_geometries WHERE geometry_hash=?", (ghash,)).fetchone()
            if row: vblob = row[0]
        if not vblob:
            continue

        v = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)

        # PIVOT: mesh centroid distance from origin
        if ghash not in checked_hashes:
            centroid = v.mean(axis=0)
            cdist = float(np.linalg.norm(centroid))
            if cdist > pivot_worst: pivot_worst = cdist
            if cdist > 1.0:
                pivot_fail += 1
                if pivot_fail <= 2:
                    print(f"  PIVOT {ghash[:12]} centroid=({centroid[0]:.1f},{centroid[1]:.1f},{centroid[2]:.1f}) offset={cdist:.1f}m")
            else:
                pivot_ok += 1
            checked_hashes.add(ghash)

        # GN_SIM: what Blender GN does — R @ mesh + location
        R = euler_to_matrix(rx, ry, rz)
        gn_world = (R @ v.astype(np.float64).T).T + np.array([cx, cy, cz])
        gn_min = gn_world.min(axis=0)
        gn_max = gn_world.max(axis=0)
        gn_err = max(abs(gn_min[0]-x0), abs(gn_max[0]-x1),
                     abs(gn_min[1]-y0), abs(gn_max[1]-y1),
                     abs(gn_min[2]-z0), abs(gn_max[2]-z1))
        if gn_err > gn_worst: gn_worst = gn_err
        if gn_err > 0.1:
            gn_fail += 1
            if gn_fail <= 2:
                print(f"  GN_SIM {guid[:16]} err={gn_err:.1f}m rot=({rx:.3f},{ry:.3f},{rz:.3f})")
        else:
            gn_ok += 1

    if lib_conn: lib_conn.close()
    db.close()

    ptag = "PASS" if pivot_fail == 0 else "FAIL"
    gtag = "PASS" if gn_fail == 0 else "FAIL"
    print(f"\n{name}  mesh_src={mesh_src}")
    print(f"  §PROOF PIVOT    {ptag}  {pivot_ok} ok, {pivot_fail} fail  worst={pivot_worst:.1f}m")
    print(f"  §PROOF GN_SIM   {gtag}  {gn_ok} ok, {gn_fail} fail  worst={gn_worst:.4f}m")
    verdict = "PASS" if pivot_fail == 0 and gn_fail == 0 else "FAIL"
    print(f"  §PROOF VIEWPORT {verdict}")

if __name__ == "__main__":
    for db_path in sys.argv[1:]:
        test_db(db_path)
