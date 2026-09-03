# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
smoke_test_dekh.py — Phase 6 smoke test, NOT part of the shipped pipeline.

Two full background runs of validate_real_world.py on the whole 437M-point DeKH_B_ICU.laz
died silently across session boundaries in this environment before completing (confirmed:
even a fully OS-detached process didn't survive — the whole execution environment appears to
reset between turns here, not just the tracked shell session). Before committing more wall-
clock time to another full run, this proves the pipeline runs clean, end to end, on real DeKH
data at all — a small spatial crop (one corner, a few hundred thousand points) run through
every real stage: ingestion -> normalize -> segment -> merge-coplanar -> classify ->
instance-merge -> write_reference_db.

Uses the REAL ingestion path: load_las_downsampled() on the whole file first (~50s, 1cm
voxels, 23.7M points for DeKH_B_ICU), THEN spatially crops the DOWNSAMPLED result to one
corner. This is deliberately not raw-point cropping (an earlier version of this script did
that, and its artificially high raw density triggered a real MemoryError in
_split_plane_into_components -- fixed since, see segment.py's grid-based rewrite -- but
raw-density cropping was never a faithful test of what the full run actually feeds
downstream anyway). Ingestion here is byte-for-byte the same call the full run makes;
only the crop-after-downsample step is smoke-test-only.

Usage:
    python3 smoke_test_dekh.py --laz "C:\\DeKH\\Buildings\\B\\DeKH_B_ICU.laz" \
        --out-dir <scratch dir OUTSIDE the repo>
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np


def _log(msg: str):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def downsample_then_crop_corner(laz_path: str, voxel_size_m: float, corner_size_m: float = 4.0):
    """The REAL ingestion path (load_las_downsampled — identical call the full run makes),
    then a spatial crop of the DOWNSAMPLED result to one corner. Crop-after-downsample, not
    before: an earlier version of this script cropped raw (non-downsampled) points, whose
    artificially high density triggered a real MemoryError in _split_plane_into_components —
    fixed since (segment.py's grid-based rewrite) — but that was never a faithful test of
    what the full run actually feeds downstream regardless."""
    import laspy
    from pointcloud_io import load_las_downsampled

    with laspy.open(laz_path) as f:
        mins = f.header.mins

    _log(f"downsampling full file (voxel={voxel_size_m}m) — this is the exact call the full "
         f"run makes, ~50s for DeKH_B_ICU's 437M points")
    pc, _kept_indices = load_las_downsampled(laz_path, voxel_size_m=voxel_size_m, log=_log)

    cx0, cy0 = mins[0], mins[1]
    cx1, cy1 = cx0 + corner_size_m, cy0 + corner_size_m
    mask = ((pc.xyz[:, 0] >= cx0) & (pc.xyz[:, 0] <= cx1) &
            (pc.xyz[:, 1] >= cy0) & (pc.xyz[:, 1] <= cy1))
    cropped = pc.subset(mask)
    _log(f"corner crop X=[{cx0:.2f},{cx1:.2f}] Y=[{cy0:.2f},{cy1:.2f}]: "
         f"{len(cropped)} of {len(pc)} downsampled points")
    return cropped.xyz, cropped.rgb


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--laz", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--voxel-size", type=float, default=0.01)
    ap.add_argument("--corner-size-m", type=float, default=4.0)
    args = ap.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    sys.path.insert(0, str(Path(__file__).parent))
    from pointcloud_io import PointCloud
    from normalize import normalize_pointcloud
    from segment import segment_pointcloud, merge_coplanar_fragments
    from classify import classify_segments
    from merge_instances import merge_instances
    from write_reference_db import write_reference_db

    t_start = time.time()

    _log("STAGE 1/6: real ingestion (load_las_downsampled) + corner crop")
    t0 = time.time()
    xyz, rgb = downsample_then_crop_corner(args.laz, args.voxel_size, args.corner_size_m)
    pc = PointCloud(xyz, rgb)
    _log(f"STAGE 1/6 DONE in {time.time()-t0:.1f}s -> {len(pc)} points")

    _log("STAGE 2/6: normalize")
    t0 = time.time()
    pc, tack_point = normalize_pointcloud(pc)
    _log(f"STAGE 2/6 DONE in {time.time()-t0:.1f}s -> tack_point={tack_point}")

    _log("STAGE 3/6: segment (RANSAC + DBSCAN)")
    t0 = time.time()
    segments = segment_pointcloud(pc)
    _log(f"STAGE 3/6 DONE in {time.time()-t0:.1f}s -> {len(segments)} raw segments")

    _log("STAGE 4/6: merge_coplanar_fragments")
    t0 = time.time()
    segments = merge_coplanar_fragments(segments)
    _log(f"STAGE 4/6 DONE in {time.time()-t0:.1f}s -> {len(segments)} segments after merge")

    _log("STAGE 5/6: classify + instance-merge")
    t0 = time.time()
    classified = classify_segments(segments, points=pc.xyz)
    merged = merge_instances(classified)
    _log(f"STAGE 5/6 DONE in {time.time()-t0:.1f}s -> {len(merged)} classified elements")

    _log("STAGE 6/6: write_reference_db")
    t0 = time.time()
    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None
    out_db = out_dir / "smoke_test_reference.db"
    write_reference_db(merged, out_db, floor_z)
    _log(f"STAGE 6/6 DONE in {time.time()-t0:.1f}s -> {out_db}")

    _log(f"SMOKE TEST PASSED — all 6 stages completed clean in {time.time()-t_start:.1f}s total")
    from collections import Counter
    _log(f"class breakdown: {dict(Counter(cs.ifc_class for cs in merged))}")


if __name__ == "__main__":
    main()
