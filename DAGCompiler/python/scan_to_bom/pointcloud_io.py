# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
pointcloud_io.py — point cloud ingestion (Phase 2 step 1 of the Scan-to-BIM roadmap).

Reads raw point cloud files into a plain numpy array: Nx3 (XYZ) or Nx6 (XYZ+RGB uint8).
No labels, no classification — this is the literal raw scan, exactly as it comes off the
scanner. Supports:

  .xyz / .txt   plain ASCII "x y z" or "x y z r g b" per line (space or comma separated)
  .ply          ASCII or binary_little_endian PLY, vertex x/y/z (+ optional red/green/blue)
  .las / .laz   via `load_pointcloud()` -> `_load_las()`, full-resolution, in one shot.
                Fine at synthetic/small-scan scale; a real high-resolution terrestrial scan
                (hundreds of millions of points) will not fit in memory this way — use
                `load_las_downsampled()` instead for those (see its own docstring: chunked,
                voxel-grid downsampled, index-preserving so an external per-point label
                array stays aligned).
"""

from __future__ import annotations

import struct
from pathlib import Path

import numpy as np


class PointCloud:
    """A raw point cloud: Nx3 XYZ (metres), optional Nx3 RGB (uint8, 0-255)."""

    def __init__(self, xyz: np.ndarray, rgb: np.ndarray | None = None):
        assert xyz.ndim == 2 and xyz.shape[1] == 3, f"xyz must be Nx3, got {xyz.shape}"
        if rgb is not None:
            assert rgb.shape == xyz.shape, f"rgb shape {rgb.shape} must match xyz {xyz.shape}"
        self.xyz = xyz.astype(np.float64)
        self.rgb = rgb.astype(np.uint8) if rgb is not None else None

    def __len__(self) -> int:
        return self.xyz.shape[0]

    def subset(self, mask_or_indices) -> "PointCloud":
        xyz = self.xyz[mask_or_indices]
        rgb = self.rgb[mask_or_indices] if self.rgb is not None else None
        return PointCloud(xyz, rgb)


def load_pointcloud(path: str | Path) -> PointCloud:
    path = Path(path)
    ext = path.suffix.lower()
    if ext in (".xyz", ".txt", ".csv"):
        return _load_xyz(path)
    if ext == ".ply":
        return _load_ply(path)
    if ext in (".las", ".laz"):
        return _load_las(path)
    raise ValueError(f"Unrecognised point cloud format: {ext} ({path})")


def _load_xyz(path: Path) -> PointCloud:
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.replace(",", " ").split()
            rows.append([float(p) for p in parts])
    arr = np.array(rows, dtype=np.float64)
    if arr.shape[1] >= 6:
        return PointCloud(arr[:, :3], arr[:, 3:6].astype(np.uint8))
    return PointCloud(arr[:, :3])


def _load_ply(path: Path) -> PointCloud:
    with open(path, "rb") as f:
        header_lines = []
        while True:
            line = f.readline().decode("ascii").strip()
            header_lines.append(line)
            if line == "end_header":
                break

        fmt = None
        vertex_count = 0
        properties = []  # (name, type)
        in_vertex_element = False
        for line in header_lines:
            if line.startswith("format"):
                fmt = line.split()[1]
            elif line.startswith("element vertex"):
                vertex_count = int(line.split()[-1])
                in_vertex_element = True
            elif line.startswith("element") and not line.startswith("element vertex"):
                in_vertex_element = False
            elif line.startswith("property") and in_vertex_element:
                parts = line.split()
                properties.append((parts[-1], parts[-2]))  # (name, type)

        prop_names = [p[0] for p in properties]
        xyz_idx = [prop_names.index(a) for a in ("x", "y", "z")]
        has_rgb = all(c in prop_names for c in ("red", "green", "blue"))
        rgb_idx = [prop_names.index(c) for c in ("red", "green", "blue")] if has_rgb else None

        if fmt == "ascii":
            xyz = np.zeros((vertex_count, 3), dtype=np.float64)
            rgb = np.zeros((vertex_count, 3), dtype=np.uint8) if has_rgb else None
            for i in range(vertex_count):
                vals = f.readline().decode("ascii").split()
                xyz[i] = [float(vals[j]) for j in xyz_idx]
                if has_rgb:
                    rgb[i] = [int(float(vals[j])) for j in rgb_idx]
            return PointCloud(xyz, rgb)

        if fmt == "binary_little_endian":
            type_size = {"float": 4, "float32": 4, "double": 8, "float64": 8,
                         "uchar": 1, "uint8": 1, "int": 4, "int32": 4, "short": 2}
            type_fmt = {"float": "f", "float32": "f", "double": "d", "float64": "d",
                        "uchar": "B", "uint8": "B", "int": "i", "int32": "i", "short": "h"}
            row_fmt = "<" + "".join(type_fmt[t] for _, t in properties)
            row_size = sum(type_size[t] for _, t in properties)
            xyz = np.zeros((vertex_count, 3), dtype=np.float64)
            rgb = np.zeros((vertex_count, 3), dtype=np.uint8) if has_rgb else None
            for i in range(vertex_count):
                row = struct.unpack(row_fmt, f.read(row_size))
                xyz[i] = [row[j] for j in xyz_idx]
                if has_rgb:
                    rgb[i] = [row[j] for j in rgb_idx]
            return PointCloud(xyz, rgb)

        raise ValueError(f"Unsupported PLY format: {fmt} (only ascii / binary_little_endian)")


def _load_las(path: Path) -> PointCloud:
    """Read LAS or LAZ (auto-detected from the file's own header, not the extension) via
    laspy. Needs the 'laspy' package plus a LAZ decompression backend ('lazrs' or 'laszip')
    for compressed files — both confirmed installable with native wheels for this
    environment's Python version; see DAGCompiler/python/scan_to_bom/README.md.

    Deliberately does NOT touch coordinate magnitude here — a real LAS/LAZ file is very
    likely delivered in a projected survey CRS (large UTM-scale numbers), and ingestion's
    job is reading the file as-is, not deciding the project's coordinate system. That
    normalization is a separate, explicit step (see README's "Coordinate normalization"
    section) — this function returns exactly what the file says, scale/offset already
    resolved by laspy.

    Deliberately does NOT read the LAS 'classification' byte (ground/building/vegetation
    etc., ASPRS standard) even where present — that's a real, usable extract-don't-invent
    signal for a later phase (see README), out of scope for this raw-ingestion step.
    """
    import laspy

    las = laspy.read(str(path))
    xyz = np.column_stack([las.x, las.y, las.z]).astype(np.float64)

    rgb = None
    dim_names = set(las.point_format.dimension_names)
    if {"red", "green", "blue"}.issubset(dim_names):
        # LAS mandates 16-bit RGB fields; the overwhelmingly common convention (most
        # scanners/processing software) is an 8-bit value widened by x257 (0..255 -> 0..65535
        # so 0xFF*257=0xFFFF exactly). >>8 recovers that original 8-bit value. If a real
        # DeKH file uses genuine 16-bit color depth this would lose precision — harmless for
        # segmentation (RGB isn't used by segment.py), worth revisiting if a later phase
        # needs real color fidelity.
        rgb = np.column_stack([
            np.asarray(las.red) >> 8,
            np.asarray(las.green) >> 8,
            np.asarray(las.blue) >> 8,
        ]).astype(np.uint8)

    return PointCloud(xyz, rgb)


def load_las_downsampled(path: str | Path, voxel_size_m: float, chunk_size: int = 5_000_000,
                          log=print) -> tuple[PointCloud, np.ndarray]:
    """Chunked, voxel-grid-downsampled LAS/LAZ ingestion for real-world scans too large for
    `_load_las()`'s one-shot read — a real terrestrial scan can be hundreds of millions of
    points (437M for DeKH_B_ICU, first real-world file this pipeline has processed); a naive
    full load needs tens of GB of RAM and makes RANSAC/DBSCAN intractable regardless.

    Keeps exactly ONE REAL measured point per voxel cell — the first one encountered in the
    file's own point order, never an invented average — so every kept point is still a real
    scan measurement, consistent with this project's extract-or-compile-only rule. Reads and
    downsamples chunk-by-chunk so peak memory is bounded by the DOWNSAMPLED point count
    (typically a small fraction of the raw count for a real high-density scan), not the raw
    one.

    Returns `(PointCloud, kept_indices)`. `kept_indices` are positions into the ORIGINAL,
    file-order point sequence — so a same-length external array (e.g. a per-point ground-
    truth `.npy`, one label per raw point) can be sliced with these exact indices
    (`labels[kept_indices]`) and stay correctly aligned with the downsampled cloud, point for
    point, with no re-derivation needed.

    `voxel_size_m` is a real, load-bearing parameter, not a tuning knob to pick blindly: pick
    it well below every geometric tolerance segment.py/classify.py already tune at (2-8cm),
    so downsampling only removes literally-redundant same-spot resampling, never real spatial
    detail those algorithms rely on.
    """
    import laspy

    xyz_chunks: list[np.ndarray] = []
    rgb_chunks: list[np.ndarray] = []
    kept_idx_chunks: list[np.ndarray] = []
    seen_voxels: set[int] = set()
    n_raw = 0

    # Voxel-key packing: three per-axis cell indices -> one int64. real building-scale scans
    # span tens of metres, i.e. a few thousand cells per axis even at 1cm voxels — the
    # generous +/-1,000,000-cell, 2,000,000-wide-per-axis packing below has enormous headroom
    # over that and stays collision-free.
    AXIS_OFFSET = 1_000_000
    AXIS_WIDTH = 2_000_000

    with laspy.open(str(path)) as f:
        has_rgb = {"red", "green", "blue"}.issubset(set(f.header.point_format.dimension_names))
        offset = 0
        for chunk in f.chunk_iterator(chunk_size):
            x = np.asarray(chunk.x, dtype=np.float64)
            y = np.asarray(chunk.y, dtype=np.float64)
            z = np.asarray(chunk.z, dtype=np.float64)
            n = len(x)

            vx = np.floor(x / voxel_size_m).astype(np.int64)
            vy = np.floor(y / voxel_size_m).astype(np.int64)
            vz = np.floor(z / voxel_size_m).astype(np.int64)
            key = ((vx + AXIS_OFFSET) * AXIS_WIDTH + (vy + AXIS_OFFSET)) * AXIS_WIDTH + (vz + AXIS_OFFSET)

            # First-occurrence-per-voxel WITHIN this chunk, fully vectorized (np.unique's
            # return_index is the first occurrence in the *original*, unsorted order).
            _, first_local_idx = np.unique(key, return_index=True)
            first_local_idx.sort()
            cand_keys = key[first_local_idx]

            # Of those chunk-local candidates, keep only voxels not already claimed by an
            # earlier chunk's real point — the actual cross-chunk dedup. Only iterates the
            # chunk's own unique-voxel count, not its raw point count.
            cand_keys_list = cand_keys.tolist()
            new_mask = np.fromiter((k not in seen_voxels for k in cand_keys_list),
                                    dtype=bool, count=len(cand_keys_list))
            new_local_idx = first_local_idx[new_mask]
            seen_voxels.update(k for k, keep in zip(cand_keys_list, new_mask) if keep)

            xyz_chunks.append(np.column_stack([x[new_local_idx], y[new_local_idx], z[new_local_idx]]))
            kept_idx_chunks.append(new_local_idx + offset)
            if has_rgb:
                r = np.asarray(chunk.red)[new_local_idx] >> 8
                g = np.asarray(chunk.green)[new_local_idx] >> 8
                b = np.asarray(chunk.blue)[new_local_idx] >> 8
                rgb_chunks.append(np.column_stack([r, g, b]).astype(np.uint8))

            n_raw += n
            offset += n
            log(f"§LAS_DOWNSAMPLE chunk: {n} raw -> {len(new_local_idx)} new voxels "
                f"({len(seen_voxels)} kept total, {n_raw} raw points processed so far)")

    xyz = np.concatenate(xyz_chunks, axis=0) if xyz_chunks else np.zeros((0, 3))
    kept_indices = (np.concatenate(kept_idx_chunks, axis=0) if kept_idx_chunks
                     else np.zeros((0,), dtype=np.int64))
    rgb = np.concatenate(rgb_chunks, axis=0) if rgb_chunks else None

    retained_pct = (len(kept_indices) / n_raw) if n_raw else 0.0
    log(f"§LAS_DOWNSAMPLE {path}: {n_raw} raw points -> {len(kept_indices)} kept "
        f"(voxel={voxel_size_m}m, {retained_pct:.1%} retained)")
    return PointCloud(xyz, rgb), kept_indices


def save_ply(path: str | Path, pc: PointCloud, labels: np.ndarray | None = None) -> None:
    """Write a point cloud to ASCII PLY. `labels` (optional int array) adds a
    per-point 'label' property — used by tooling/debugging, never by the real
    ingestion path (a real scan has no labels)."""
    path = Path(path)
    n = len(pc)
    with open(path, "w", encoding="ascii") as f:
        f.write("ply\nformat ascii 1.0\n")
        f.write(f"element vertex {n}\n")
        f.write("property float x\nproperty float y\nproperty float z\n")
        if pc.rgb is not None:
            f.write("property uchar red\nproperty uchar green\nproperty uchar blue\n")
        if labels is not None:
            f.write("property int label\n")
        f.write("end_header\n")
        for i in range(n):
            row = [f"{pc.xyz[i, 0]:.6f}", f"{pc.xyz[i, 1]:.6f}", f"{pc.xyz[i, 2]:.6f}"]
            if pc.rgb is not None:
                row += [str(int(pc.rgb[i, 0])), str(int(pc.rgb[i, 1])), str(int(pc.rgb[i, 2]))]
            if labels is not None:
                row += [str(int(labels[i]))]
            f.write(" ".join(row) + "\n")
