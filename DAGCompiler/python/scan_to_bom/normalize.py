# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
normalize.py — coordinate normalization, a distinct pre-processing step between ingestion
(pointcloud_io.py) and segmentation (segment.py).

Deliberately NOT folded into pointcloud_io.py — see the reasoning recorded in
DAGCompiler/python/scan_to_bom/README.md's "LAS/LAZ ingestion" section. Short version:
ingestion's job is reading a file as-is; deciding the project's coordinate system is a
different concern, and different formats need this differently (a synthetic PLY sampled from
already building-local IFC geometry needs none of this; a real LAS/LAZ delivered in a survey
CRS needs all of it). Folding it into ingestion would risk silently re-normalizing already-
correct data a second time.

Mirrors extractIFCtoDB.py's own convention exactly — that script's extraction log prints
"USE_WORLD_COORDS=False, tack point = IFC origin": one computed anchor, everything else
relative. This module computes the point-cloud equivalent of that tack point (IFC authoring
tools place a project near a small local origin for free; raw scans are captured directly in
real-world/survey coordinates and don't get that for free — this module is what does the
equivalent work).

Satisfies two hard, verified requirements downstream, not just a style preference:
  - BomValidator.java:49 — WORLD_COORD_THRESHOLD_M = 500, an enforced QA gate. Real survey-CRS
    coordinates (UTM eastings routinely 100,000s-900,000s) fail this immediately unnormalized.
  - BomValidator.java:240 — only the BUILDING-type BOM row may carry non-zero origin_x/y/z;
    every other row must be exactly 0. Confirms the architecture's rule: exactly one absolute
    anchor per building, everything else a small relative offset from it.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from pointcloud_io import PointCloud

WORLD_COORD_THRESHOLD_M = 500.0  # mirrors BomValidator.java's WORLD_COORD_THRESHOLD_M exactly


def compute_tack_point(pc: PointCloud) -> np.ndarray:
    """Bounding-box center of the raw cloud, per axis. Simple, deterministic, and needs no
    semantic knowledge (doesn't need to know which surface is the floor, unlike a
    floor-plane-based origin would) — and matches how this pipeline's building-local data
    actually looks in practice (a real Sample House wall's real center is (-7.73, 4.55, 0.0)
    — centered near origin with both signs, not shifted into an all-positive octant the way
    a bbox-MIN-based origin would produce)."""
    return (pc.xyz.min(axis=0) + pc.xyz.max(axis=0)) / 2.0


def normalize_pointcloud(pc: PointCloud, tack_point: np.ndarray | None = None,
                          log=print) -> tuple[PointCloud, np.ndarray]:
    """Shift `pc` to building-local coordinates. Pass an explicit `tack_point` to align
    multiple point clouds (different disciplines' scans of the same site, say) to a shared
    reference rather than each self-centering independently — a real need for site
    federation that a per-file heuristic alone couldn't support.

    Returns (normalized_cloud, tack_point_used). The caller is responsible for persisting
    tack_point_used (see save_tack_point()) if the transform needs to be reversible later —
    e.g. relating the compiled model back to real-world survey/GPS coordinates.
    """
    if tack_point is None:
        tack_point = compute_tack_point(pc)
        method = "bbox-center (computed)"
    else:
        tack_point = np.asarray(tack_point, dtype=np.float64)
        method = "explicit override"

    raw_max_abs = float(np.abs(pc.xyz).max()) if len(pc) else 0.0
    normalized_xyz = pc.xyz - tack_point
    normalized_max_abs = float(np.abs(normalized_xyz).max()) if len(pc) else 0.0

    log(f"§NORMALIZE tack point ({method}): "
        f"({tack_point[0]:.3f}, {tack_point[1]:.3f}, {tack_point[2]:.3f})")
    gate_ok = normalized_max_abs < WORLD_COORD_THRESHOLD_M
    log(f"§NORMALIZE coordinate magnitude: raw max|xyz|={raw_max_abs:.1f}m -> "
        f"normalized max|xyz|={normalized_max_abs:.1f}m "
        f"(BomValidator WORLD_COORD_THRESHOLD_M={WORLD_COORD_THRESHOLD_M:.0f}m gate: "
        f"{'OK' if gate_ok else 'STILL EXCEEDS — building larger than the gate, or bad tack point'})")

    return PointCloud(normalized_xyz, pc.rgb), tack_point


def save_tack_point(pointcloud_path: str | Path, tack_point: np.ndarray,
                     method: str = "bbox-center") -> Path:
    """Persist the tack point as a JSON sidecar next to the source point cloud file, so the
    transform back to the file's original coordinate system (e.g. survey CRS) isn't silently
    lost. Mirrors the .meta.json sidecar pattern already used by gen_synthetic_pointcloud.py."""
    path = Path(pointcloud_path)
    sidecar = path.with_suffix(path.suffix + ".tackpoint.json")
    sidecar.write_text(json.dumps({
        "source_file": str(path),
        "tack_point_xyz": [float(v) for v in tack_point],
        "method": method,
        "note": "normalized = raw - tack_point_xyz. Add tack_point_xyz back to recover the "
                "file's original coordinates.",
    }, indent=2))
    return sidecar
