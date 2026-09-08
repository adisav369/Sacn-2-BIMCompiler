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
  spatial_structure (one IfcBuilding + one or more IfcBuildingStorey rows — see "Storey" note
  below).

Per Phase 1 spec §5's decision (gate on C9 only for v1, mesh fidelity deferred): base_geometries
holds a coarse fitted box per element (from its own AABB), not a tessellation of the scanned
surface. The compiler doesn't consume this mesh to compile anyway — it instances pre-existing
library geometry matched by product_type + dimensions — so a box is schema-complete without
overclaiming mesh fidelity C8/C10 don't need for v1.

Storey: `write_reference_db()` is still the single-storey entry point every one-scan-per-
building caller uses (Sample House, B_ICU, Building C, and each of DeKH Building A's floors
individually) — one IfcBuildingStorey row, elevation = the detected floor height. This
pipeline still has no AUTO-DETECTION of multiple floors from one continuous scan (that's a
separate, larger, unscoped problem — see README). What it now has is `write_multistorey_
reference_db()`, for the case that's already real: a building scanned as SEPARATE per-floor
point clouds (each with its own independent segmentation, classification and normalization
tack point — see run_dekh_staged.py and normalize.combine_floors_to_shared_frame). That
function writes N real IfcBuildingStorey rows, each with its own real elevation and its own
correctly-tagged elements, reconciled into one shared coordinate frame first. StructuralBom
Builder groups by storey name; both a single named storey (the historical degenerate case) and
several are valid.

Confidence partition (Phase 6): the writer emits TWO full-schema DBs, not one — the primary
db_path holds the elements the pipeline stands behind, and a `<stem>_lowconf.db` companion
holds the ones it has already self-flagged `low_confidence`. This is a partition, not a
filter: nothing is dropped, and every element in either DB carries its own confidence
provenance in the additive `element_confidence` table (geometry + classification confidence,
the tier it landed in, and both support notes). It is additive to the spec schema — verified
that no consumer, Java or Python, enumerates this DB's tables or SELECTs * from it — so the
six spec tables downstream reads are untouched.

Why partition rather than merge fragments away: measured on all three DeKH scenes, the
confident tier alone matches *every* ground-truth element the combined output matched — zero
recall cost — while cutting over-prediction from 22.5x to 2.1x (Building A), 56.8x to 9.8x
(B_ICU) and 76.8x to 10.0x (Building C). The low-confidence tier is 83-91% of raw element
count and is dominated by IfcBuildingElementProxy, i.e. surfaces the classifier declined to
name. Suppressing them from the primary BOM is honest about that; deleting them would throw
away real measurements, which is why the companion DB exists.

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
CREATE TABLE element_confidence (
    guid TEXT PRIMARY KEY,
    tier TEXT NOT NULL,
    geometry_confidence REAL,
    classification_confidence REAL,
    low_confidence INTEGER NOT NULL,
    geometry_type TEXT,
    support_note TEXT,
    classification_note TEXT,
    FOREIGN KEY (guid) REFERENCES elements_meta(guid)
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

# Companion DB for the low-confidence tier: <stem>_lowconf.db alongside the primary one.
LOWCONF_SUFFIX = "_lowconf"


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


def _write_one_db(storeys: list[dict], db_path: Path, tier: str, log) -> int:
    """Write exactly one full-schema reference DB from one or more real storeys.

    `storeys`: `[{"guid": str, "name": str, "elevation": float, "classified": [...]}, ...]` —
    each dict is one real `IfcBuildingStorey`. Single-storey callers (the historical
    behavior, still how every non-multi-storey building is written) pass a length-1 list;
    `write_multistorey_reference_db` is what actually has more than one.

    `tier` is recorded per-element in element_confidence so a DB is self-describing about
    which side of the partition it is — a consumer never has to infer it from the filename.
    """
    if db_path.exists():
        db_path.unlink()
    con = sqlite3.connect(db_path)
    con.executescript(SCHEMA_SQL)

    building_guid = "PC_BUILDING_1"
    all_classified = [cs for st in storeys for cs in st["classified"]]
    all_min = np.array([cs.segment.aabb_min for cs in all_classified]) if all_classified else np.zeros((1, 3))
    all_max = np.array([cs.segment.aabb_max for cs in all_classified]) if all_classified else np.zeros((1, 3))
    bldg_center = (all_min.min(axis=0) + all_max.max(axis=0)) / 2.0
    bldg_size = all_max.max(axis=0) - all_min.min(axis=0)

    con.execute(
        "INSERT INTO spatial_structure (guid, type, name, parent_guid, center_x, center_y, "
        "center_z, size_x, size_y, size_z) VALUES (?, 'IfcBuilding', 'Scanned Building', NULL, "
        "?, ?, ?, ?, ?, ?)",
        (building_guid, *bldg_center, *bldg_size))
    for st in storeys:
        con.execute(
            "INSERT INTO spatial_structure (guid, type, name, parent_guid, elevation) "
            "VALUES (?, 'IfcBuildingStorey', ?, ?, ?)",
            (st["guid"], st["name"], building_guid, st["elevation"]))
    log(f"§WRITE_DB[{tier}] spatial_structure: 1 IfcBuilding + {len(storeys)} IfcBuildingStorey "
        f"row(s) -> " + ", ".join(f"'{st['name']}' (elevation={st['elevation']:.3f}m, "
                                   f"{len(st['classified'])} elements)" for st in storeys))

    n_elements = 0
    n_geom_written = set()
    for st in storeys:
        for cs in st["classified"]:
            guid = _guid(cs.ifc_class, cs.segment.id)
            con.execute(
                "INSERT INTO elements_meta (id, guid, discipline, ifc_class, element_name, "
                "element_type, storey, material_name, material_rgba, is_anchor) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, 0)",
                (n_elements, guid, cs.discipline, cs.ifc_class,
                 f"{cs.ifc_class} ({cs.segment.geometry_type})", cs.ifc_class, st["name"]))
            con.execute(
                "INSERT INTO elements_rtree (id, minX, maxX, minY, maxY, minZ, maxZ) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                (n_elements, *_rtree_bounds(cs.segment.aabb_min, cs.segment.aabb_max)))
            con.execute(
                "INSERT INTO element_transforms (guid, center_x, center_y, center_z, rotation_x, "
                "rotation_y, rotation_z, bbox_x, bbox_y, bbox_z, transform_source) "
                "VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?)",
                (guid, *cs.center.tolist(), cs.rotation_z, *cs.bbox.tolist(), TRANSFORM_SOURCE))
            con.execute(
                "INSERT INTO element_confidence (guid, tier, geometry_confidence, "
                "classification_confidence, low_confidence, geometry_type, support_note, "
                "classification_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (guid, tier, float(cs.segment.confidence), float(cs.classification_confidence),
                 1 if cs.low_confidence else 0, cs.segment.geometry_type,
                 cs.segment.support_note, cs.classification_note))

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
    log(f"§WRITE_DB[{tier}] {n_elements} elements written across {len(storeys)} storey(s), "
        f"{len(n_geom_written)} distinct box geometries (deduplicated by rounded dimensions) "
        f"-> {db_path}")
    return n_elements


def lowconf_db_path(db_path: str | Path) -> Path:
    """Companion path for the low-confidence tier. Kept as a function, not an f-string at
    each call site, so a consumer can locate the second tier without hardcoding the suffix."""
    db_path = Path(db_path)
    return db_path.with_name(f"{db_path.stem}{LOWCONF_SUFFIX}{db_path.suffix}")


def write_reference_db(classified: list[ClassifiedSegment], db_path: str | Path,
                        floor_z: float | None, log=print, partition: bool = True) -> dict:
    """Write the reference DB, by default PARTITIONED by the pipeline's own confidence flag.

    Single-storey entry point — every caller today (Sample House, B_ICU, Building C, DeKH
    Building A's individual floors) has exactly one real storey per scan. Delegates to
    `write_multistorey_reference_db` with a length-1 storey list so the two writers can never
    drift apart; unchanged behavior and signature for every existing caller.

    Nothing is discarded: every element still reaches a full-schema DB, and every element
    carries its confidence provenance in element_confidence. The partition only decides which
    DB it lands in, so the primary one is the subset the pipeline actually stands behind.

    partition=False writes a single combined DB at db_path (still with element_confidence) —
    for validation and diagnostic callers that want to score the whole output at once.
    """
    floor = {"guid": "PC_STOREY_1", "name": "Level 1",
             "elevation": floor_z if floor_z is not None else 0.0, "classified": classified}
    return write_multistorey_reference_db([floor], db_path, log=log, partition=partition)


def write_multistorey_reference_db(floors: list[dict], db_path: str | Path, log=print,
                                    partition: bool = True) -> dict:
    """Write a reference DB with one or more REAL `IfcBuildingStorey` rows.

    `floors`: `[{"guid": str, "name": str, "elevation": float, "classified": [...]}, ...]` —
    one dict per real storey, already reconciled into ONE shared coordinate frame (see
    `normalize.combine_floors_to_shared_frame`, which also guarantees each floor's
    `Segment.id`s are disjoint so guids stay globally unique across storeys — this function
    does not check for collisions itself, it trusts the caller already resolved them). This
    function only writes; it never reconciles coordinate frames or renumbers ids itself, same
    separation of concerns as normalize.py/segment.py/classify.py's own division of labor.

    Confidence partition applies globally across all storeys (an element's tier depends on the
    pipeline's own confidence in IT, not which floor it came from), but each storey's own
    element set is filtered and written under its own name/elevation so `storey` stays
    per-element-correct in both the confident and low-confidence DB.

    partition=False writes a single combined DB at db_path (still with element_confidence).
    """
    db_path = Path(db_path)
    if not partition:
        n = _write_one_db(floors, db_path, "combined", log)
        return {"primary": db_path, "lowconf": None, "n_primary": n, "n_lowconf": 0}

    conf_floors = [{**f, "classified": [cs for cs in f["classified"] if not cs.low_confidence]}
                    for f in floors]
    low_floors = [{**f, "classified": [cs for cs in f["classified"] if cs.low_confidence]}
                   for f in floors]
    n_conf = _write_one_db(conf_floors, db_path, "confident", log)
    low_path = lowconf_db_path(db_path)
    n_low = _write_one_db(low_floors, low_path, "low_confidence", log)
    total = n_conf + n_low
    log(f"§WRITE_DB partition: {n_conf}/{total} confident "
        f"({n_conf/total:.1%}) -> {db_path.name}; {n_low}/{total} low-confidence "
        f"({n_low/total:.1%}) -> {low_path.name}. Measured on all three DeKH scenes: the "
        f"confident tier alone matches every ground-truth element the combined output "
        f"matched (zero recall cost) — see README 'Confidence-gated output partition'.")
    return {"primary": db_path, "lowconf": low_path, "n_primary": n_conf, "n_lowconf": n_low}
