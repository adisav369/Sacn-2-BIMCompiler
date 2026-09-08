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

import dataclasses
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


def combine_floors_to_shared_frame(floors: list[dict], log=print) -> list[dict]:
    """Reconcile multiple independently-normalized floors into ONE shared coordinate frame,
    so they can be written as real storeys in a single reference DB instead of each floor
    silently claiming its own coordinate origin.

    This is the real, measured situation for DeKH Building A (see README's "Buildings A and C"
    section): it has no single continuous multi-floor scan, only two SEPARATE floor scans
    (1st_floor 507M pts, 2nd_floor 621M pts). Each floor already goes through its own
    `normalize_pointcloud()` call with its own bbox-center tack point (there is no shared raw
    scan to anchor both floors to a common origin at ingestion time) — until now those two
    predictions were only ever un-shifted back to the raw/world frame and combined for
    SCORING (run_dekh_staged.py's stage_classify), never written into one reference DB as real
    storeys.

    `floors`: `[{"classified": list[ClassifiedSegment], "tack_point": np.ndarray(3,),
    "floor_z": float | None, "name": str}, ...]` — one dict per floor, in FLOOR ORDER (bottom
    to top; only used for logging, elevation is what actually orders storeys downstream).
    `floor_z` is that floor's own detected floor elevation, in ITS OWN normalized frame (same
    value classify.py already computes from `orientation == "floor"` segments).

    Shared frame: centered on the bbox-center of the UNION of every floor's RAW (pre-normalize)
    extent — reconstructed by adding each floor's own tack_point back to its own segments'
    normalized AABBs. This is the exact same bbox-center rule `compute_tack_point()` already
    uses for one floor, just applied to the union of floors instead of one alone — not a new,
    invented convention.

    Each floor's `Segment.id`s are also renumbered into a disjoint range (floor 2's ids start
    right after floor 1's highest id) so that `write_reference_db._guid()` — which keys purely
    off `(ifc_class, segment.id)` — can't collide across floors sharing the same per-floor
    numbering (both floors' own segmentation runs start counting from 0 independently).

    Returns `[{"classified": [...], "elevation": float, "name": str}, ...]` — `classified`
    holds NEW ClassifiedSegment/Segment objects (nothing in `floors` is mutated), shifted into
    the shared frame and with renumbered ids; `elevation` is `floor_z` re-expressed in that
    shared frame — what the caller should write as this storey's real elevation.
    """
    if len(floors) == 1:
        f = floors[0]
        elevation = float(f["floor_z"]) if f["floor_z"] is not None else 0.0
        return [{"classified": f["classified"], "elevation": elevation, "name": f["name"]}]

    raw_mins, raw_maxs = [], []
    for f in floors:
        cls = f["classified"]
        if not cls:
            continue
        mins = np.array([cs.segment.aabb_min for cs in cls]).min(axis=0) + f["tack_point"]
        maxs = np.array([cs.segment.aabb_max for cs in cls]).max(axis=0) + f["tack_point"]
        raw_mins.append(mins)
        raw_maxs.append(maxs)
    if not raw_mins:
        raise ValueError("combine_floors_to_shared_frame: every floor has zero classified "
                          "elements — nothing to reconcile a shared frame from")
    shared_tack_point = (np.min(raw_mins, axis=0) + np.max(raw_maxs, axis=0)) / 2.0
    log(f"§COMBINE_FLOORS shared tack point (bbox-center of {len(floors)} floors' raw union): "
        f"({shared_tack_point[0]:.3f}, {shared_tack_point[1]:.3f}, {shared_tack_point[2]:.3f})")

    out = []
    next_id_base = 0
    for f in floors:
        offset = f["tack_point"] - shared_tack_point  # this floor's normalized frame -> shared frame
        shifted = []
        max_id_seen = -1
        for cs in f["classified"]:
            seg = cs.segment
            max_id_seen = max(max_id_seen, seg.id)
            new_seg = dataclasses.replace(
                seg, id=seg.id + next_id_base,
                aabb_min=seg.aabb_min + offset, aabb_max=seg.aabb_max + offset)
            new_cs = dataclasses.replace(cs, segment=new_seg, center=cs.center + offset)
            shifted.append(new_cs)
        elevation = float(f["floor_z"] + offset[2]) if f["floor_z"] is not None else float(offset[2])
        log(f"§COMBINE_FLOORS '{f['name']}': offset=({offset[0]:.3f}, {offset[1]:.3f}, "
            f"{offset[2]:.3f})m from its own tack point, ids [{next_id_base}, "
            f"{next_id_base + max_id_seen}], {len(shifted)} elements, elevation -> "
            f"{elevation:.3f}m in shared frame")
        out.append({"classified": shifted, "elevation": elevation, "name": f["name"]})
        next_id_base += max_id_seen + 1

    return out


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
