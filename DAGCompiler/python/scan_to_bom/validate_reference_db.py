# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
validate_reference_db.py — Phase 4 validation harness, NOT part of the shipped pipeline.

Runs the full chain blind (ingest -> normalize -> segment -> merge-coplanar -> classify ->
instance-merge -> write_reference_db), then scores the WRITTEN SQLITE DATABASE itself — not
just the in-memory ClassifiedSegment list — against two independent things neither
write_reference_db.py nor classify.py ever see:

  1. Per-point ground truth (guid/ifc_class) — same file every earlier phase's validator used,
     for classification accuracy at the row level, exactly as validate_classification.py scores
     it (kept in sync intentionally rather than imported, so a Phase 3 regression can't hide
     behind a Phase 4 scoring change).

  2. The REAL element_transforms rows in SampleHouse_extracted.db — the actual IFC-extracted
     dimensions gen_synthetic_pointcloud.py sampled the point cloud from in the first place.
     This is the C9-style dimensional-fidelity check the Phase 1 spec's decision #5 gates v1
     on: for each written element whose dominant true source is unambiguous, compare its
     written bbox_x/y/z against that real guid's real bbox_x/y/z from the source DB. Neither
     file is read by write_reference_db.py or classify.py, so this is a genuine blind check,
     not a self-consistency check against numbers the writer itself produced.

Also checks basic schema/FK integrity: every table's row count, every element_instances.guid
has a base_geometries row, every elements_rtree row's bounds match its element_transforms
bbox+center, spatial_structure has exactly one Building and one Storey.

Usage:
    python3 validate_reference_db.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply \
        --source-db ../../lib/input/SampleHouse_extracted.db \
        --out ../../lib/input/pointcloud/samplehouse_reference.db
"""

from __future__ import annotations

import argparse
import sqlite3
from collections import Counter
from pathlib import Path

import numpy as np

from pointcloud_io import load_pointcloud
from normalize import normalize_pointcloud
from segment import segment_pointcloud, merge_coplanar_fragments
from classify import classify_segments
from merge_instances import merge_instances
from write_reference_db import write_reference_db

EQUIVALENCE = {
    "IfcWall": {"IfcWall", "IfcWallStandardCase", "IfcCurtainWall"},
    "IfcSlab": {"IfcSlab", "IfcCovering"},
    "IfcRoof": {"IfcRoof", "IfcMember", "IfcPlate"},
    "IfcWindow": {"IfcWindow"},
    "IfcDoor": {"IfcDoor"},
    "IfcFurniture": {"IfcFurniture"},
}

# A written bbox axis within this fraction of the real element's own axis counts as matching.
# Loose on purpose: RANSAC/DBSCAN segment boundaries are measured from noisy sampled points,
# not the mesh itself, and a coarse box fit (Phase 1 spec decision #5) isn't trying to be exact
# — this checks "is the written dimension in the right ballpark of reality," not tessellation
# fidelity, which the spec explicitly defers past v1.
BBOX_REL_TOL = 0.35


def _schema_integrity_checks(db_path: Path) -> list[str]:
    problems = []
    con = sqlite3.connect(db_path)
    cur = con.cursor()

    n_meta = cur.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    n_rtree = cur.execute("SELECT COUNT(*) FROM elements_rtree").fetchone()[0]
    n_xform = cur.execute("SELECT COUNT(*) FROM element_transforms").fetchone()[0]
    n_inst = cur.execute("SELECT COUNT(*) FROM element_instances").fetchone()[0]
    if not (n_meta == n_rtree == n_xform == n_inst):
        problems.append(f"row-count mismatch across per-element tables: "
                         f"elements_meta={n_meta} elements_rtree={n_rtree} "
                         f"element_transforms={n_xform} element_instances={n_inst}")

    orphan_geom = cur.execute(
        "SELECT COUNT(*) FROM element_instances ei "
        "LEFT JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash "
        "WHERE bg.geometry_hash IS NULL").fetchone()[0]
    if orphan_geom:
        problems.append(f"{orphan_geom} element_instances rows reference a missing "
                         f"base_geometries.geometry_hash")

    orphan_xform = cur.execute(
        "SELECT COUNT(*) FROM elements_meta em "
        "LEFT JOIN element_transforms et ON et.guid = em.guid "
        "WHERE et.guid IS NULL").fetchone()[0]
    if orphan_xform:
        problems.append(f"{orphan_xform} elements_meta rows have no element_transforms row")

    n_bldg = cur.execute(
        "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcBuilding'").fetchone()[0]
    n_storey = cur.execute(
        "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcBuildingStorey'").fetchone()[0]
    if n_bldg != 1 or n_storey != 1:
        problems.append(f"expected exactly 1 IfcBuilding + 1 IfcBuildingStorey, "
                         f"found {n_bldg} + {n_storey}")

    rows = cur.execute(
        "SELECT r.id, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ, "
        "t.center_x, t.center_y, t.center_z, t.bbox_x, t.bbox_y, t.bbox_z "
        "FROM elements_rtree r JOIN elements_meta m ON m.id = r.id "
        "JOIN element_transforms t ON t.guid = m.guid").fetchall()
    n_rtree_mismatch = 0
    for (rid, minx, maxx, miny, maxy, minz, maxz,
         cx, cy, cz, bx, by, bz) in rows:
        exp_min = (cx - bx / 2, cy - by / 2, cz - bz / 2)
        exp_max = (cx + bx / 2, cy + by / 2, cz + bz / 2)
        got_min, got_max = (minx, miny, minz), (maxx, maxy, maxz)
        if any(abs(a - b) > 1e-3 for a, b in zip(exp_min, got_min)) or \
           any(abs(a - b) > 1e-3 for a, b in zip(exp_max, got_max)):
            n_rtree_mismatch += 1
    if n_rtree_mismatch:
        problems.append(f"{n_rtree_mismatch} elements_rtree rows don't reconstruct from their "
                         f"own element_transforms center+bbox")

    con.close()
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ply", required=True)
    ap.add_argument("--source-db", required=True,
                     help="the real IFC-extracted DB the point cloud was sampled from "
                          "(ground truth for the C9-style dimension check)")
    ap.add_argument("--out", required=True, help="path to write the reference DB to")
    args = ap.parse_args()

    ply_path = Path(args.ply)
    gt_path = ply_path.with_suffix(".groundtruth.npz")
    if not gt_path.exists():
        print(f"§VALIDATE no ground truth at {gt_path} — cannot score")
        return

    # ── Blind run: pipeline never touches gt_path or --source-db up to this point ──────────
    pc = load_pointcloud(ply_path)
    pc, tack_point = normalize_pointcloud(pc, log=lambda *a, **k: None)
    segments = segment_pointcloud(pc, log=lambda *a, **k: None)
    segments = merge_coplanar_fragments(segments, log=lambda *a, **k: None)
    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None
    classified = classify_segments(segments, log=lambda *a, **k: None)
    merged = merge_instances(classified, log=lambda *a, **k: None)

    out_path = Path(args.out)
    write_reference_db(merged, out_path, floor_z, log=lambda *a, **k: None)
    print(f"§VALIDATE wrote reference DB blind -> {out_path} ({len(merged)} elements)")

    # ── Score 1: schema/FK integrity (self-consistency, not ground-truth, but still real) ──
    problems = _schema_integrity_checks(out_path)
    print(f"\n§VALIDATE ── schema integrity ──")
    if problems:
        for p in problems:
            print(f"  FAIL: {p}")
    else:
        print(f"  OK — row counts consistent, no orphan FKs, rtree reconstructs from "
              f"transforms, exactly 1 Building + 1 Storey")

    # ── Score 2: classification accuracy at the written-row level (per-point ground truth) ─
    gt = np.load(gt_path, allow_pickle=True)
    gt_guid, gt_class = gt["guid"], gt["ifc_class"]

    n_correct = n_wrong = n_deferred = n_confident_wrong = 0
    for cs in merged:
        true_classes = gt_class[cs.segment.point_indices]
        if len(true_classes) == 0:
            continue
        dominant_true = Counter(true_classes.tolist()).most_common(1)[0][0]
        if cs.ifc_class == "IfcBuildingElementProxy":
            n_deferred += 1
        elif dominant_true in EQUIVALENCE.get(cs.ifc_class, set()):
            n_correct += 1
        else:
            n_wrong += 1
            if cs.classification_confidence >= 0.7:
                n_confident_wrong += 1
    total = n_correct + n_wrong + n_deferred
    print(f"\n§VALIDATE ── written-row classification accuracy (per-point ground truth) ──")
    print(f"  {n_correct}/{total} correct, {n_wrong}/{total} wrong, "
          f"{n_deferred}/{total} deferred; confident-wrong={n_confident_wrong}")

    # ── Score 3: C9-style dimensional fidelity vs the REAL source DB (independent file, ─────
    # never read by write_reference_db.py or classify.py) ──────────────────────────────────
    src = sqlite3.connect(args.source_db)
    src_rows = src.execute(
        "SELECT em.guid, et.bbox_x, et.bbox_y, et.bbox_z, et.rotation_z "
        "FROM elements_meta em JOIN element_transforms et ON et.guid = em.guid").fetchall()
    src.close()
    real_bbox = {guid: (np.array([bx, by, bz]), rz or 0.0) for guid, bx, by, bz, rz in src_rows}

    # Real bbox_x/y/z is in the element's LOCAL (pre-rotation) frame; a written segment's
    # bbox is a world-axis-aligned AABB extent measured off scanned points. For an unrotated
    # element the two frames coincide, but a real limit shows up even then: the axis with the
    # SMALLEST real extent on a thin/planar element (a slab's material thickness, a wall's
    # depth) is usually a face a single-sided scan never sees the far side of — its measured
    # extent is just the scan's noise band on one surface, not the element's true thickness.
    # That's a genuine scan-physics limit (nothing to invent a thickness from), not a Phase 4
    # defect, so it's scored and reported separately from the two larger "footprint" axes,
    # which a single-sided scan CAN measure directly and which rotation only permutes, not
    # inflates, for axis-aligned (rotation_z == 0) real elements.
    n_dim_checked = n_dim_footprint_ok = n_dim_unresolved = n_rotated_skipped = 0
    thin_axis_rel_errs = []
    dim_failures = []
    for cs in merged:
        true_classes = gt_class[cs.segment.point_indices]
        true_guids = gt_guid[cs.segment.point_indices]
        if len(true_classes) == 0:
            continue
        counts = Counter(true_guids.tolist())
        dominant_guid, dom_count = counts.most_common(1)[0]
        purity = dom_count / len(true_guids)
        if purity < 0.95 or dominant_guid not in real_bbox:
            n_dim_unresolved += 1
            continue
        real_bx, rot_z = real_bbox[dominant_guid]
        if abs(rot_z) > 1e-6:
            # World-frame AABB extent isn't comparable to a local-frame bbox once rotated
            # (a rotated footprint's world AABB is inflated vs its local extents) — skip
            # rather than score a mismatch that's a frame artifact, not a real error.
            n_rotated_skipped += 1
            continue
        n_dim_checked += 1
        written_bx = np.abs(cs.bbox)
        rel_err = np.abs(written_bx - real_bx) / np.maximum(real_bx, 1e-6)
        thin_axis = int(np.argmin(real_bx))
        footprint_axes = [i for i in range(3) if i != thin_axis]
        thin_axis_rel_errs.append(rel_err[thin_axis])
        if all(rel_err[i] <= BBOX_REL_TOL for i in footprint_axes):
            n_dim_footprint_ok += 1
        else:
            dim_failures.append((cs.segment.id, cs.ifc_class, dominant_guid,
                                  written_bx.tolist(), real_bx.tolist(), rel_err.tolist()))

    print(f"\n§VALIDATE ── C9-style dimensional fidelity vs real source DB "
          f"({args.source_db}) ──")
    print(f"  {n_dim_checked} written elements resolvable to one real, axis-aligned source "
          f"guid (purity>=0.95, rotation_z==0); {n_dim_unresolved} not resolvable "
          f"(mixed/fragmented source, skipped rather than scored against an ambiguous truth), "
          f"{n_rotated_skipped} skipped (rotated real element — world-AABB not comparable to "
          f"local-frame bbox)")
    if n_dim_checked:
        print(f"  FOOTPRINT axes (the 2 larger real dimensions): "
              f"{n_dim_footprint_ok}/{n_dim_checked} within {BBOX_REL_TOL:.0%} on both")
        thin = np.array(thin_axis_rel_errs)
        print(f"  THIN axis (the element's smallest real dimension — typically material "
              f"thickness/depth, not directly observable from a single-sided scan): "
              f"median rel_err={np.median(thin):.2f}, {np.mean(thin <= BBOX_REL_TOL):.0%} "
              f"within {BBOX_REL_TOL:.0%} — reported separately, NOT counted as a Phase 4 "
              f"defect (see note above)")
    if dim_failures:
        print(f"  footprint failures (>{BBOX_REL_TOL:.0%} off on a large axis — the ones "
              f"that matter):")
    for seg_id, ifc_class, guid, wb, rb, err in dim_failures[:15]:
        print(f"    seg#{seg_id} {ifc_class} vs {guid}: written={[round(v,2) for v in wb]} "
              f"real={[round(v,2) for v in rb]} rel_err={[round(v,2) for v in err]}")

    print(f"\n§VALIDATE Phase 4 SUMMARY: schema_integrity={'OK' if not problems else 'FAIL'}  "
          f"classification {n_correct}/{total} correct  "
          f"footprint_dimensional_fidelity "
          f"{n_dim_footprint_ok}/{n_dim_checked if n_dim_checked else 1} within tol")


if __name__ == "__main__":
    main()
