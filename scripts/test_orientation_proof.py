# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S173: Orientation proof — pure math, no Blender, no IFC.

Proves the pipeline is spatially correct by verifying that:
  library_local_mesh × stored_rotation + stored_centre ≈ R-tree world bbox

Inputs: extracted.db (transforms + R-tree) + library.db (local meshes by hash)
Dependencies: numpy, sqlite3 (stdlib)

Usage:
    python3 scripts/test_orientation_proof.py \
        --db DAGCompiler/lib/input/Hospital_extracted.db \
        --library library/component_library.db
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
import argparse, math, sqlite3, sys, time
import numpy as np


def euler_to_matrix(a, b, c):
    """XYZ Euler angles → 3×3 rotation matrix (same as Blender's)."""
    return np.array([
        [math.cos(b)*math.cos(c),
         math.sin(a)*math.sin(b)*math.cos(c) - math.cos(a)*math.sin(c),
         math.cos(a)*math.sin(b)*math.cos(c) + math.sin(a)*math.sin(c)],
        [math.cos(b)*math.sin(c),
         math.sin(a)*math.sin(b)*math.sin(c) + math.cos(a)*math.cos(c),
         math.cos(a)*math.sin(b)*math.sin(c) - math.sin(a)*math.cos(c)],
        [-math.sin(b),
         math.sin(a)*math.cos(b),
         math.cos(a)*math.cos(b)]
    ])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True, help="Extracted DB (has R-tree)")
    parser.add_argument("--library", required=True, help="Component library DB")
    parser.add_argument("--max", type=int, default=200)
    parser.add_argument("--atol", type=float, default=0.05,
                        help="Tolerance in metres (default 5cm)")
    args = parser.parse_args()

    ext = sqlite3.connect(args.db)
    lib = sqlite3.connect(args.library)

    # ── Load library local meshes (hash → local bbox) ──
    t0 = time.time()
    lib_bbox = {}  # hash → (local_min, local_max)
    for ghash, vblob in lib.execute(
            "SELECT geometry_hash, vertices FROM component_geometries "
            "WHERE vertices IS NOT NULL"):
        v = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
        lib_bbox[ghash] = (v.min(axis=0), v.max(axis=0))
    print(f"Library: {len(lib_bbox):,} meshes loaded ({time.time()-t0:.2f}s)")

    # ── P1: LIBRARY_COVERAGE — every hash in extracted DB exists in library ──
    ext_hashes = set(r[0] for r in ext.execute(
        "SELECT DISTINCT geometry_hash FROM element_instances"))
    orphans = ext_hashes - set(lib_bbox.keys())
    p1_tag = "PASS" if len(orphans) == 0 else "FAIL"
    print(f"§PROOF LIBRARY_COVERAGE {p1_tag}  "
          f"{len(ext_hashes)-len(orphans)} covered, {len(orphans)} orphans")

    # ── P2: BBOX_RECONSTRUCT — R × local_bbox + centre ≈ R-tree bbox ──
    # Load transforms + R-tree entries joined
    rows = ext.execute("""
        SELECT et.guid,
               et.center_x, et.center_y, et.center_z,
               et.rotation_x, et.rotation_y, et.rotation_z,
               ei.geometry_hash,
               rt.minX, rt.maxX, rt.minY, rt.maxY, rt.minZ, rt.maxZ
        FROM element_transforms et
        JOIN element_instances ei ON ei.guid = et.guid
        JOIN elements_rtree rt ON rt.id = (
            SELECT rowid FROM element_transforms WHERE guid = et.guid
        )
        LIMIT ?
    """, (args.max,)).fetchall()

    if not rows:
        # R-tree might use a different ID scheme — try positional rowid
        rows = ext.execute("""
            SELECT et.guid,
                   et.center_x, et.center_y, et.center_z,
                   et.rotation_x, et.rotation_y, et.rotation_z,
                   ei.geometry_hash
            FROM element_transforms et
            JOIN element_instances ei ON ei.guid = et.guid
            LIMIT ?
        """, (args.max,)).fetchall()

        # Load R-tree separately — need to find the ID mapping
        rtree_map = {}
        for r in ext.execute("SELECT id, minX, maxX, minY, maxY, minZ, maxZ FROM elements_rtree"):
            rtree_map[r[0]] = r[1:]

        # Check how R-tree IDs map to elements
        # Try: R-tree id = rowid of I_Element_Extraction or element_transforms
        guid_to_rowid = {}
        try:
            for r in ext.execute("SELECT rowid, guid FROM I_Element_Extraction"):
                guid_to_rowid[r[1]] = r[0]
        except Exception:
            pass
        if not guid_to_rowid:
            for r in ext.execute("SELECT rowid, guid FROM element_transforms"):
                guid_to_rowid[r[1]] = r[0]

        # Rebuild rows with R-tree data
        full_rows = []
        for r in rows:
            guid = r[0]
            rid = guid_to_rowid.get(guid)
            if rid and rid in rtree_map:
                full_rows.append(r + rtree_map[rid])
        rows = full_rows

    p2_ok = p2_fail = 0
    p2_worst = 0.0
    p2_worst_guid = ""

    for row in rows:
        if len(row) < 14:
            continue
        guid = row[0]
        cx, cy, cz = row[1], row[2], row[3]
        rx, ry, rz = row[4], row[5], row[6]
        ghash = row[7]
        rt_minX, rt_maxX = row[8], row[9]
        rt_minY, rt_maxY = row[10], row[11]
        rt_minZ, rt_maxZ = row[12], row[13]

        if ghash not in lib_bbox:
            continue

        local_min, local_max = lib_bbox[ghash]
        R = euler_to_matrix(rx, ry, rz)
        centre = np.array([cx, cy, cz])

        # Transform all 8 local bbox corners to world space
        corners = np.array([
            [local_min[0], local_min[1], local_min[2]],
            [local_max[0], local_min[1], local_min[2]],
            [local_min[0], local_max[1], local_min[2]],
            [local_max[0], local_max[1], local_min[2]],
            [local_min[0], local_min[1], local_max[2]],
            [local_max[0], local_min[1], local_max[2]],
            [local_min[0], local_max[1], local_max[2]],
            [local_max[0], local_max[1], local_max[2]],
        ])
        world_corners = (R @ corners.T).T + centre
        recon_min = world_corners.min(axis=0)
        recon_max = world_corners.max(axis=0)

        rt_min = np.array([rt_minX, rt_minY, rt_minZ])
        rt_max = np.array([rt_maxX, rt_maxY, rt_maxZ])

        err_min = np.abs(recon_min - rt_min).max()
        err_max = np.abs(recon_max - rt_max).max()
        err = max(err_min, err_max)

        if err > p2_worst:
            p2_worst = err
            p2_worst_guid = guid

        if err > args.atol:
            p2_fail += 1
            if p2_fail <= 5:
                print(f"  FAIL BBOX_RECONSTRUCT {guid[:16]} err={err:.4f}m")
                print(f"    rtree  =([{rt_minX:.3f},{rt_maxX:.3f}],"
                      f"[{rt_minY:.3f},{rt_maxY:.3f}],"
                      f"[{rt_minZ:.3f},{rt_maxZ:.3f}])")
                print(f"    recon  =([{recon_min[0]:.3f},{recon_max[0]:.3f}],"
                      f"[{recon_min[1]:.3f},{recon_max[1]:.3f}],"
                      f"[{recon_min[2]:.3f},{recon_max[2]:.3f}])")
                print(f"    centre =({cx:.3f},{cy:.3f},{cz:.3f})")
                print(f"    local  =([{local_min[0]:.3f},{local_max[0]:.3f}],"
                      f"[{local_min[1]:.3f},{local_max[1]:.3f}],"
                      f"[{local_min[2]:.3f},{local_max[2]:.3f}])")
        else:
            p2_ok += 1

    p2_tag = "PASS" if p2_fail == 0 and p2_ok > 0 else "FAIL"
    print(f"§PROOF BBOX_RECONSTRUCT {p2_tag}  {p2_ok} ok, {p2_fail} fail  "
          f"worst={p2_worst:.4f}m ({p2_worst_guid[:16]})")

    # ── P3: EULER_SANITY — rotation angles within valid range ──
    bad_euler = ext.execute("""
        SELECT COUNT(*) FROM element_transforms
        WHERE ABS(rotation_x) > 6.3 OR ABS(rotation_y) > 6.3 OR ABS(rotation_z) > 6.3
    """).fetchone()[0]
    p3_tag = "PASS" if bad_euler == 0 else "FAIL"
    print(f"§PROOF EULER_SANITY {p3_tag}  {bad_euler} out-of-range")

    # ── Summary ──
    elapsed = time.time() - t0
    all_pass = p1_tag == "PASS" and p2_tag == "PASS" and p3_tag == "PASS"
    print(f"\n§PROOF ORIENTATION {'PASS' if all_pass else 'FAIL'}  "
          f"({len(rows)} elements, {elapsed:.1f}s)")
    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()
