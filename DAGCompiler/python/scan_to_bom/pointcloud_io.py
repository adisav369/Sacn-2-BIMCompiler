# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
pointcloud_io.py — point cloud ingestion (Phase 2 step 1 of the Scan-to-BIM roadmap).

Reads raw point cloud files into a plain numpy array: Nx3 (XYZ) or Nx6 (XYZ+RGB uint8).
No labels, no classification — this is the literal raw scan, exactly as it comes off the
scanner. Supports the two formats real scan exports and this project's own synthetic test
data actually use:

  .xyz / .txt   plain ASCII "x y z" or "x y z r g b" per line (space or comma separated)
  .ply          ASCII or binary_little_endian PLY, vertex x/y/z (+ optional red/green/blue)

LAS/LAZ (real LiDAR-specific formats) are NOT supported yet — would need the `laspy`
dependency, which isn't installed in this environment. Documented gap, not a silent one:
load_pointcloud() raises NotImplementedError with a clear message rather than guessing.
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
