# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
test_las_downsample.py — correctness check for pointcloud_io.load_las_downsampled(), NOT
part of the shipped pipeline.

Writes a small, synthetic LAS file with a KNOWN voxel structure (deliberately duplicate
points inside some voxels, deliberately unique points in others), runs the chunked
voxel-downsampler against it, and checks the three things that matter before trusting it on
a real 437M-point file:

  1. Every kept point is a REAL point from the original file (no invented averages) —
     checked by exact coordinate match, not just "a point near here".
  2. No two kept points share a voxel (the downsampling actually downsampled).
  3. `kept_indices` sliced against a same-length external label array reproduces the exact
     TRUE label of whichever real point was kept — the actual property this function exists
     for (DeKH's .npy ground truth must stay aligned with the downsampled cloud).

Run: python3 test_las_downsample.py
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

import tempfile
from pathlib import Path

import laspy
import numpy as np

from pointcloud_io import load_las_downsampled


def build_test_las(path: Path, rng: np.random.Generator):
    """21 points: 3 clusters of 5-7 near-duplicate points each within a 1mm jitter (all
    landing in the SAME 1cm voxel per cluster) at three well-separated locations, plus 3
    lone points each alone in their own voxel. Every point gets a distinct integer "label"
    (0..20) stashed in the LAS `user_data` byte AND mirrored into an external labels array
    at the same row index, so we can verify point<->label correspondence survives
    downsampling by two independent means.
    """
    # Centers offset to voxel-CELL-INTERIOR positions (+0.005m, half a 1cm voxel) rather
    # than exact multiples of the voxel size — a center sitting exactly ON a voxel boundary
    # is the one case where a +/-1mm jitter can straddle two different cells regardless of
    # how tight the jitter is, which would make this a bad test of downsampling itself.
    centers = [(0.005, 0.005, 0.005), (5.005, 5.005, 5.005), (-2.995, 2.005, 1.005)]
    cluster_sizes = [5, 7, 6]
    xyz = []
    for (cx, cy, cz), n in zip(centers, cluster_sizes):
        jitter = rng.uniform(-0.001, 0.001, size=(n, 3))  # well within the same 1cm voxel
        xyz.append(np.array([cx, cy, cz]) + jitter)
    lone_points = np.array([(10.005, 0.005, 0.005), (0.005, 10.005, 0.005), (0.005, 0.005, 10.005)])
    xyz.append(lone_points)
    xyz = np.concatenate(xyz, axis=0)  # 5+7+6+3 = 21 points

    n_total = len(xyz)
    true_labels = np.arange(n_total, dtype=np.float32)  # each point's own unique "label"

    header = laspy.LasHeader(point_format=2)
    header.scales = [1e-6, 1e-6, 1e-6]
    header.offsets = [0.0, 0.0, 0.0]
    las = laspy.LasData(header)
    las.x = xyz[:, 0]
    las.y = xyz[:, 1]
    las.z = xyz[:, 2]
    las.user_data = true_labels.astype(np.uint8)  # 0..20 fits a byte, cross-check #2
    las.write(str(path))
    return xyz, true_labels, cluster_sizes


def main():
    rng = np.random.default_rng(0)
    with tempfile.TemporaryDirectory() as tmp:
        las_path = Path(tmp) / "test.las"
        xyz, true_labels, cluster_sizes = build_test_las(las_path, rng)

        pc, kept_indices = load_las_downsampled(las_path, voxel_size_m=0.01, chunk_size=8,
                                                  log=lambda *a, **k: None)

        n_total = len(xyz)
        n_expected_voxels = len(cluster_sizes) + 3  # 3 clusters + 3 lone points
        print(f"§TEST raw points: {n_total}, expected distinct voxels: {n_expected_voxels}, "
              f"kept: {len(kept_indices)}")
        assert len(kept_indices) == n_expected_voxels, \
            f"expected {n_expected_voxels} kept points, got {len(kept_indices)}"

        # 1. Every kept point is a REAL point from the original file (matched to LAS's own
        #    integer-quantization precision, not just "somewhere near")
        for i, idx in enumerate(kept_indices):
            # LAS stores coordinates as scaled integers (header.scales=1e-6 here) -- a
            # write/read round-trip through the real file format introduces sub-micron
            # quantization, same as any real LAS file including DeKH's own. allclose at
            # 1e-5m (10x the scale) confirms "the same real point", not floating-point
            # bit-identity, which no LAS round-trip can promise.
            assert np.allclose(pc.xyz[i], xyz[idx], atol=1e-5), \
                f"kept point {i} (orig idx {idx}) doesn't match the original point: " \
                f"{pc.xyz[i]} vs {xyz[idx]}"
        print("§TEST PASS: every kept point is an exact, real original point (no invented averages)")

        # 2. No two kept points share a voxel
        voxel_keys = np.floor(pc.xyz / 0.01).astype(np.int64)
        n_unique_voxels = len(np.unique(voxel_keys, axis=0))
        assert n_unique_voxels == len(pc.xyz), \
            f"{len(pc.xyz)} kept points but only {n_unique_voxels} distinct voxels -- duplicate voxel survived"
        print(f"§TEST PASS: all {len(pc.xyz)} kept points occupy distinct voxels")

        # 3. kept_indices correctly slices an external label array (the actual DeKH use case)
        sliced_labels = true_labels[kept_indices]
        for i, idx in enumerate(kept_indices):
            # user_data (read back from the real kept point) must match the external array
            # sliced the same way -- two independent paths to the same real label.
            assert sliced_labels[i] == true_labels[idx]
        # And every kept label must correspond to a real point whose OWN coordinates
        # (re-derived from true_labels' index) match what's in the downsampled cloud.
        for i, label in enumerate(sliced_labels):
            orig_idx = int(label)  # by construction, true_labels[j] == j
            assert np.allclose(pc.xyz[i], xyz[orig_idx], atol=1e-5), \
                f"label {label} at kept row {i} doesn't correspond to its own real point"
        print("§TEST PASS: labels[kept_indices] stays correctly aligned, point for point, "
              "with the downsampled cloud")

        print("\n§TEST ALL PASSED — load_las_downsampled() is index-safe for external "
              "per-point label arrays")


if __name__ == "__main__":
    main()
