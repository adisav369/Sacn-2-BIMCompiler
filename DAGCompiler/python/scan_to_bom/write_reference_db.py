# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
write_reference_db.py — Phase 4 of the Scan-to-BIM roadmap: reference-DB schema writer.

Serializes the final list[ClassifiedSegment] (after Phase 3.5's instance merge) into the exact
reference-DB schema defined in docs/ScanToBOM_ReferenceDB_Spec.md — the same schema
extractIFCtoDB.py produces from an IFC file, so everything downstream (ExtractionPopulator,
StructuralBomBuilder, ScopeBomBuilder, BomValidator, CompilationPipeline, BIM_COBOL's verbs,
BIMEyes' gates) consumes it unmodified. That contract was verified against real Java source
before Phase 1 was written, not assumed — see the spec doc for the grep-based table-by-table
consumer counts this schema was built to match.

Tables written (per the spec's §1 "required for v1"):
  elements_meta, elements_rtree, element_transforms, element_instances, base_geometries,
  spatial_structure (one IfcBuilding + one IfcBuildingStorey row — see "Storey" note below).

Per Phase 1 spec §5's decision (gate on C9 only for v1, mesh fidelity deferred): base_geometries
holds a coarse fitted box per element (from its own AABB), not a tessellation of the scanned
surface. The compiler doesn't consume this mesh to compile anyway — it instances pre-existing
library geometry matched by product_type + dimensions — so a box is schema-complete without
overclaiming mesh fidelity C8/C10 don't need for v1.

Storey: this pipeline has no multi-storey detection yet (explicitly deferred, see README) — one
IfcBuildingStorey row covers everything, elevation = the detected floor height. Honest about
current capability, not a guess at floor count. StructuralBomBuilder groups by storey name; a
single named storey is a valid degenerate case for a single-scan building.

guid: doesn't need to be a real IFC GUID (confirmed in Phase 1 spec — downstream code treats it
as an opaque key) — generated deterministically from ifc_class + segment id.
"""

from __future__ import annotations

import hashlib
import sqlite3
import struct
from pathlib import Path

import numpy as np

from classify import ClassifiedSegment

SCHEMA_SQL = """
CREATE TABLE elements_meta (
    id INTEGER PRIMARY KEY,
    guid TEXT UNIQUE NOT NULL,
    discipline TEXT NOT NULL,
    ifc_class TEXT NOT NULL,
    element_name TEXT,
    element_type TEXT,
    storey TEXT,
    material_name TEXT,
    material_rgba TEXT,
    is_anchor INTEGER DEFAULT 0
);
CREATE VIRTUAL TABLE elements_rtree USING rtree(
    id, minX, maxX, minY, maxY, minZ, maxZ
);
CREATE TABLE base_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB,
    faces BLOB,
    vertex_count INTEGER,
    face_count INTEGER
);
CREATE TABLE element_instances (
    guid TEXT PRIMARY KEY,
    geometry_hash TEXT,
    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
);
CREATE TABLE element_transforms (
    guid TEXT PRIMARY KEY,
    center_x REAL, center_y REAL, center_z REAL,
    rotation_x REAL DEFAULT 0, rotation_y REAL DEFAULT 0, rotation_z REAL DEFAULT 0,
    bbox_x REAL, bbox_y REAL, bbox_z REAL,
    transform_source TEXT
);
CREATE TABLE spatial_structure (
    guid TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    name TEXT,
    parent_guid TEXT,
    object_type TEXT,
    predefined_type TEXT,
    center_x REAL, center_y REAL, center_z REAL,
    size_x REAL, size_y REAL, size_z REAL,
    elevation REAL
);
"""

TRANSFORM_SOURCE = "pointcloud:v1"


def _guid(ifc_class: str, seg_id: int) -> str:
    return f"PC_{ifc_class}_{seg_id:05d}"


def _rtree_bounds(aabb_min: np.ndarray, aabb_max: np.ndarray) -> tuple[float, ...]:
    """SQLite's rtree module stores bounds as float32, not float64. A float64 min/max pair
    that's merely very close (not exactly equal) can have its order flip on that downcast,
    which the rtree module rejects outright (IntegrityError: minZ<=maxZ) — hit for real on
    this dataset's near-degenerate plane-segment AABBs. Pad every axis by a fixed epsilon
    well above float32 rounding error (~1e-7 relative) so min stays strictly below max after
    the downcast, on every axis, even a numerically flat one."""
    eps = 1e-4
    out = []
    for lo, hi in zip(aabb_min.tolist(), aabb_max.tolist()):
        lo, hi = min(lo, hi), max(lo, hi)
        out.extend([lo - eps, hi + eps])
    return tuple(out)


def _box_mesh(extent: np.ndarray) -> tuple[bytes, bytes, int, int]:
    """8 vertices + 12 triangles (2 per face) for a box of the given extent, centred at
    its own local origin (matches the local-origin-plus-translation convention verified
    against a real extracted element back in Phase 2's synthetic-cloud generator)."""
    hx, hy, hz = extent / 2.0
    verts = np.array([
        [-hx, -hy, -hz], [hx, -hy, -hz], [hx, hy, -hz], [-hx, hy, -hz],
        [-hx, -hy, hz], [hx, -hy, hz], [hx, hy, hz], [-hx, hy, hz],
    ], dtype="<f4")
    faces = np.array([
        [0, 1, 2], [0, 2, 3],  # bottom
        [4, 6, 5], [4, 7, 6],  # top
        [0, 4, 5], [0, 5, 1],  # front
        [1, 5, 6], [1, 6, 2],  # right
        [2, 6, 7], [2, 7, 3],  # back
        [3, 7, 4], [3, 4, 0],  # left
    ], dtype="<i4")
    return verts.tobytes(), faces.tobytes(), 8, 12


def _geometry_hash(extent: np.ndarray) -> str:
    """Deterministic hash of rounded box dimensions — identical-sized elements share a
    hash, matching this project's existing geometry-deduplication convention
    (component_library.db's geometry_hash keying)."""
    rounded = tuple(round(float(v), 3) for v in extent)  # mm-level rounding at metre scale
    return hashlib.sha256(struct.pack("<3f", *rounded)).hexdigest()[:16]


def write_reference_db(classified: list[ClassifiedSegment], db_path: str | Path,
                        floor_z: float | None, log=print) -> None:
    db_path = Path(db_path)
    if db_path.exists():
        db_path.unlink()
    con = sqlite3.connect(db_path)
    con.executescript(SCHEMA_SQL)

    building_guid = "PC_BUILDING_1"
    storey_guid = "PC_STOREY_1"
    all_center = np.array([cs.center for cs in classified]) if classified else np.zeros((1, 3))
    all_min = np.array([cs.segment.aabb_min for cs in classified]) if classified else np.zeros((1, 3))
    all_max = np.array([cs.segment.aabb_max for cs in classified]) if classified else np.zeros((1, 3))
    bldg_center = (all_min.min(axis=0) + all_max.max(axis=0)) / 2.0
    bldg_size = all_max.max(axis=0) - all_min.min(axis=0)

    con.execute(
        "INSERT INTO spatial_structure (guid, type, name, parent_guid, center_x, center_y, "
        "center_z, size_x, size_y, size_z) VALUES (?, 'IfcBuilding', 'Scanned Building', NULL, "
        "?, ?, ?, ?, ?, ?)",
        (building_guid, *bldg_center, *bldg_size))
    con.execute(
        "INSERT INTO spatial_structure (guid, type, name, parent_guid, elevation) "
        "VALUES (?, 'IfcBuildingStorey', 'Level 1', ?, ?)",
        (storey_guid, building_guid, floor_z if floor_z is not None else 0.0))
    log(f"§WRITE_DB spatial_structure: 1 IfcBuilding + 1 IfcBuildingStorey "
        f"(elevation={floor_z if floor_z is not None else 0.0:.3f}m — single-storey only, "
        f"no multi-storey detection yet)")

    n_elements = 0
    n_geom_written = set()
    for cs in classified:
        guid = _guid(cs.ifc_class, cs.segment.id)
        con.execute(
            "INSERT INTO elements_meta (id, guid, discipline, ifc_class, element_name, "
            "element_type, storey, material_name, material_rgba, is_anchor) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, 0)",
            (n_elements, guid, cs.discipline, cs.ifc_class,
             f"{cs.ifc_class} ({cs.segment.geometry_type})", cs.ifc_class, "Level 1"))
        con.execute(
            "INSERT INTO elements_rtree (id, minX, maxX, minY, maxY, minZ, maxZ) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (n_elements, *_rtree_bounds(cs.segment.aabb_min, cs.segment.aabb_max)))
        con.execute(
            "INSERT INTO element_transforms (guid, center_x, center_y, center_z, rotation_x, "
            "rotation_y, rotation_z, bbox_x, bbox_y, bbox_z, transform_source) "
            "VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?)",
            (guid, *cs.center.tolist(), cs.rotation_z, *cs.bbox.tolist(), TRANSFORM_SOURCE))

        geo_hash = _geometry_hash(cs.bbox)
        if geo_hash not in n_geom_written:
            verts, faces, vc, fc = _box_mesh(cs.bbox)
            con.execute(
                "INSERT INTO base_geometries (geometry_hash, vertices, faces, vertex_count, "
                "face_count) VALUES (?, ?, ?, ?, ?)", (geo_hash, verts, faces, vc, fc))
            n_geom_written.add(geo_hash)
        con.execute("INSERT INTO element_instances (guid, geometry_hash) VALUES (?, ?)",
                    (guid, geo_hash))
        n_elements += 1

    con.commit()
    con.close()
    log(f"§WRITE_DB {n_elements} elements written, {len(n_geom_written)} distinct box "
        f"geometries (deduplicated by rounded dimensions) -> {db_path}")
