# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
smoke_test_from_checkpoint.py — Phase 6 smoke test, NOT part of the shipped pipeline.

Same intent as smoke_test_dekh.py (prove every real stage — normalize -> segment ->
merge-coplanar -> classify -> instance-merge -> write_reference_db — runs clean on a small
spatial crop before committing wall-clock to a full run) but sources the crop from an
ALREADY-WRITTEN stage1 downsample checkpoint (run_dekh_staged.py --stage downsample) instead
of re-running load_las_downsampled() on the raw file a second time. Buildings A/C's scans are
large enough (500M-620M raw points) that re-downsampling just for a smoke test would cost real
minutes for no new information — the stage1 checkpoint IS the real ingestion path's output,
byte-for-byte what the full run's stage 2 (segment) actually consumes.

Usage:
    python3 smoke_test_from_checkpoint.py --checkpoint-dir <dir with stage1_pointcloud.npz>
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np


def _log(msg: str):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint-dir", required=True,
                     help="dir with stage1_pointcloud.npz (run_dekh_staged.py --stage downsample)")
    ap.add_argument("--corner-size-m", type=float, default=4.0)
    args = ap.parse_args()

    ckpt_dir = Path(args.checkpoint_dir)
    in_pc = ckpt_dir / "stage1_pointcloud.npz"
    if not in_pc.exists():
        raise SystemExit(f"missing {in_pc} — run --stage downsample first")

    sys.path.insert(0, str(Path(__file__).parent))
    from pointcloud_io import PointCloud
    from normalize import normalize_pointcloud
    from segment import segment_pointcloud, merge_coplanar_fragments
    from classify import classify_segments
    from merge_instances import merge_instances
    from write_reference_db import write_reference_db

    t_start = time.time()

    _log(f"STAGE 1/6: loading stage1 checkpoint {in_pc} (real downsampled ingestion output)")
    data = np.load(in_pc)
    xyz_full = data["xyz"]
    rgb_full = data["rgb"] if bool(data["has_rgb"]) else None
    _log(f"STAGE 1/6 loaded {len(xyz_full)} downsampled points")

    xmin, ymin = xyz_full[:, 0].min(), xyz_full[:, 1].min()
    cx0, cy0 = xmin, ymin
    cx1, cy1 = cx0 + args.corner_size_m, cy0 + args.corner_size_m
    mask = ((xyz_full[:, 0] >= cx0) & (xyz_full[:, 0] <= cx1) &
            (xyz_full[:, 1] >= cy0) & (xyz_full[:, 1] <= cy1))
    xyz = xyz_full[mask]
    rgb = rgb_full[mask] if rgb_full is not None else None
    _log(f"STAGE 1/6 corner crop X=[{cx0:.2f},{cx1:.2f}] Y=[{cy0:.2f},{cy1:.2f}]: "
         f"{len(xyz)} of {len(xyz_full)} points")
    pc = PointCloud(xyz, rgb)

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
    out_db = ckpt_dir / "smoke_test_reference.db"
    write_reference_db(merged, out_db, floor_z)
    _log(f"STAGE 6/6 DONE in {time.time()-t0:.1f}s -> {out_db}")

    _log(f"SMOKE TEST PASSED — all 6 stages completed clean in {time.time()-t_start:.1f}s total")
    from collections import Counter
    _log(f"class breakdown: {dict(Counter(cs.ifc_class for cs in merged))}")


if __name__ == "__main__":
    main()
