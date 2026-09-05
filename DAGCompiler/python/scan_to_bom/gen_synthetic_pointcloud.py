# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
gen_synthetic_pointcloud.py — validation fixture generator, NOT part of the shipped
scan-to-BOM pipeline.

Per docs/ScanToBOM_ReferenceDB_Spec.md §6: validate the segmentation/classification pipeline
against a synthetic point cloud sampled from a Rosetta Stone building's own already-known-correct
extracted geometry, BEFORE dealing with real scan noise/occlusion. This script does that sampling.

Reads DAGCompiler/lib/input/SampleHouse_extracted.db (real, IFC-extracted geometry — the exact
same reference DB the current IFC pipeline produces and trusts), tessellates each element's real
mesh, applies its real placement (center + rotation_z; rotation_x/y are 0 for this building per
inspection), and samples points uniformly (by triangle area) across every triangle. Writes:

  - a .ply point cloud (XYZ only — what a real scan would give you)
  - a sidecar .npy ground-truth array (per-point source guid + ifc_class), used ONLY by
    validate_segmentation.py to score the blind segmentation output. The segmentation code
    itself never sees this file.

Usage:
    python3 gen_synthetic_pointcloud.py --db ../../lib/input/SampleHouse_extracted.db \
        --out ../../lib/input/pointcloud/samplehouse_synthetic.ply --density 400
"""

from __future__ import annotations
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

import argparse
import json
import sqlite3
import struct
from pathlib import Path

import numpy as np

from pointcloud_io import PointCloud, save_ply


def blob_to_f32(blob: bytes, count: int) -> np.ndarray:
    return np.frombuffer(blob[: count * 4], dtype="<f4").astype(np.float64)


def blob_to_i32(blob: bytes, count: int) -> np.ndarray:
    return np.frombuffer(blob[: count * 4], dtype="<i4")


def rotation_z_matrix(theta: float) -> np.ndarray:
    c, s = np.cos(theta), np.sin(theta)
    return np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])


def sample_points_on_mesh(verts: np.ndarray, faces: np.ndarray, n_points: int,
                           rng: np.random.Generator) -> np.ndarray:
    """Sample n_points uniformly by surface area across the mesh's triangles."""
    tris = verts[faces]  # (F, 3, 3)
    a, b, c = tris[:, 0], tris[:, 1], tris[:, 2]
    areas = 0.5 * np.linalg.norm(np.cross(b - a, c - a), axis=1)
    total = areas.sum()
    if total <= 0:
        return np.zeros((0, 3))
    probs = areas / total
    tri_idx = rng.choice(len(faces), size=n_points, p=probs)
    u = rng.random(n_points)
    v = rng.random(n_points)
    flip = u + v > 1
    u[flip] = 1 - u[flip]
    v[flip] = 1 - v[flip]
    p0, p1, p2 = a[tri_idx], b[tri_idx], c[tri_idx]
    pts = p0 + (u[:, None] * (p1 - p0)) + (v[:, None] * (p2 - p0))
    return pts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True, help="SampleHouse_extracted.db (or any *_extracted.db)")
    ap.add_argument("--library", default="../../../library/component_library.db",
                     help="component_library.db — mesh BLOBs live here when the reference DB "
                          "was extracted with --library (base_geometries in --db is then a thin "
                          "pointer/metadata table only, vertices/faces NULL there)")
    ap.add_argument("--out", required=True, help="output .ply path")
    ap.add_argument("--density", type=float, default=400.0,
                     help="target points per m^2 of triangle surface area (default 400 — "
                          "roughly a 5cm point spacing, typical handheld-LiDAR density)")
    ap.add_argument("--noise-mm", type=float, default=3.0,
                     help="Gaussian noise stddev in mm added along each point's local normal, "
                          "simulating real scanner measurement error (default 3mm)")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--exclude-class", action="append", default=["IfcOpeningElement"],
                     help="ifc_class values to skip (openings have no real surface to scan); "
                          "repeatable")
    args = ap.parse_args()

    rng = np.random.default_rng(args.seed)
    con = sqlite3.connect(args.db)
    cur = con.cursor()
    cur.execute("ATTACH DATABASE ? AS lib", (args.library,))

    cur.execute("""
        SELECT em.guid, em.ifc_class, em.element_name, ei.geometry_hash,
               et.center_x, et.center_y, et.center_z, et.rotation_z
        FROM elements_meta em
        JOIN element_instances ei ON ei.guid = em.guid
        JOIN element_transforms et ON et.guid = em.guid
        WHERE em.is_anchor = 0
    """)
    rows = cur.fetchall()

    all_pts = []
    all_guids = []
    all_classes = []
    skipped_no_geom = 0
    skipped_excluded = 0

    for guid, ifc_class, name, geom_hash, cx, cy, cz, rot_z in rows:
        if ifc_class in args.exclude_class:
            skipped_excluded += 1
            continue
        cur.execute("SELECT vertices, faces, vertex_count, face_count FROM base_geometries "
                    "WHERE geometry_hash = ?", (geom_hash,))
        geo = cur.fetchone()
        if geo is None or geo[0] is None:
            # BLOBs may have been redirected to the shared library (extractIFCtoDB.py --library).
            cur.execute("SELECT vertices, faces, vertex_count, face_count FROM lib.component_geometries "
                        "WHERE geometry_hash = ?", (geom_hash,))
            geo = cur.fetchone()
        if geo is None or geo[0] is None or geo[2] == 0 or geo[3] == 0:
            skipped_no_geom += 1
            continue
        vblob, fblob, vcount, fcount = geo
        verts_local = blob_to_f32(vblob, vcount * 3).reshape(-1, 3)
        faces = blob_to_i32(fblob, fcount * 3).reshape(-1, 3)

        # Rigid transform: world = R(rotation_z) @ local + center. Verified against a real
        # element (IfcWall, rotation_z=0): local vertex span exactly matches the extraction
        # log's reported world AABB once center_x/y/z is added — center_x/y/z is the local
        # origin's world position, not a centroid offset; local (0,0,0) need not be the mesh's
        # geometric center.
        R = rotation_z_matrix(rot_z or 0.0)
        verts_world = verts_local @ R.T + np.array([cx, cy, cz])

        tri_areas = 0.5 * np.linalg.norm(
            np.cross(verts_world[faces[:, 1]] - verts_world[faces[:, 0]],
                      verts_world[faces[:, 2]] - verts_world[faces[:, 0]]), axis=1)
        surface_area = tri_areas.sum()
        n_pts = max(1, int(surface_area * args.density))

        pts = sample_points_on_mesh(verts_world, faces, n_pts, rng)
        if args.noise_mm > 0 and len(pts) > 0:
            pts = pts + rng.normal(0, args.noise_mm / 1000.0, size=pts.shape)

        all_pts.append(pts)
        all_guids.extend([guid] * len(pts))
        all_classes.extend([ifc_class] * len(pts))

    con.close()

    xyz = np.vstack(all_pts) if all_pts else np.zeros((0, 3))
    pc = PointCloud(xyz)

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    save_ply(out_path, pc)

    gt_path = out_path.with_suffix(".groundtruth.npz")
    np.savez(gt_path, guid=np.array(all_guids), ifc_class=np.array(all_classes))

    meta_path = out_path.with_suffix(".meta.json")
    meta_path.write_text(json.dumps({
        "source_db": str(args.db),
        "n_points": int(len(xyz)),
        "n_source_elements": len(rows) - skipped_no_geom - skipped_excluded,
        "skipped_no_geometry": skipped_no_geom,
        "skipped_excluded_class": skipped_excluded,
        "density_pts_per_m2": args.density,
        "noise_mm": args.noise_mm,
        "seed": args.seed,
    }, indent=2))

    print(f"§SYNTH_PC wrote {len(xyz)} points from {len(rows) - skipped_no_geom - skipped_excluded} "
          f"elements -> {out_path}")
    print(f"§SYNTH_PC skipped: {skipped_no_geom} no-geometry, {skipped_excluded} excluded-class")
    print(f"§SYNTH_PC ground truth -> {gt_path} (NOT read by the segmentation pipeline)")


if __name__ == "__main__":
    main()
