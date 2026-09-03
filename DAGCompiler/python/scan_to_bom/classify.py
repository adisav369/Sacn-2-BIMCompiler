# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
classify.py — Phase 3 of the Scan-to-BIM roadmap: semantic classification.

Assigns an IFC-class-shaped label + discipline + placement to each Phase 2.5 Segment, using
only measurable geometry (orientation, size, position relative to the detected floor) — no
material/RGB, no ML model, nothing beyond what a segment's own shape defensibly supports.

Per docs/ScanToBOM_ReferenceDB_Spec.md §4, this vocabulary (IfcWall, IfcSlab, ...) was chosen
because ProductRegistrar.deriveProductType() and extractIFCtoDB.py's DISCIPLINE_MAP already key
off these exact strings — confirmed by reading both directly (not assumed): deriveProductType()
has a dedicated case for every class this module can emit, and none of them appear in
DISCIPLINE_MAP, so they all correctly fall through to its documented "ARC" default — the same
default path real IFC-extracted ARC elements already take. Nothing downstream needs a new
mapping for this module's output to be consumed.

Two independent confidence axes, not conflated: Segment.confidence (from Phase 2 — "how much
real point support does this geometry have") and ClassifiedSegment.classification_confidence
("how sure is this specific label, given the geometry"). A segment can have excellent point
support and an uncertain label (a small, clean, well-measured vertical plane that could
defensibly be a door or a window), or sparse support and a confident label (a tiny but
unambiguously floor-level horizontal patch). Both matter; neither substitutes for the other.

IfcBuildingElementProxy — a real, standard IFC4 class meaning "a genuine building element,
specific type not determined" — is the deliberate fallback for anything that doesn't clear a
specific class's evidence bar. Never a guessed specific class dressed up as a real one.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from segment import Segment, aabb_gap

# ── Discipline map — verbatim copy of extractIFCtoDB.py's DISCIPLINE_MAP, kept in sync
# deliberately rather than imported, since extractIFCtoDB.py is the IFC-side extractor and
# this module has no reason to depend on it. None of THIS module's output classes appear
# here (all fall through to the "ARC" default) — kept in full anyway so anything Phase 3
# adds later (MEP classification, per the roadmap's explicit "defer" on that) is correct for
# free rather than needing a second, drifted copy of the map. ─────────────────────────────
DISCIPLINE_MAP = {
    "IfcFlowTerminal": "MEP", "IfcFlowSegment": "MEP", "IfcFlowFitting": "MEP",
    "IfcPipeSegment": "MEP", "IfcPipeFitting": "MEP",
    "IfcDuctSegment": "MEP", "IfcDuctFitting": "MEP",
    "IfcValve": "MEP", "IfcFlowController": "MEP",
    "IfcSanitaryTerminal": "MEP",
    "IfcFireSuppressionTerminal": "FP", "IfcAlarm": "FP",
    "IfcSensor": "FP", "IfcController": "FP",
    "IfcLightFixture": "ELEC", "IfcElectricAppliance": "ELEC",
    "IfcCableSegment": "ELEC", "IfcCableFitting": "ELEC",
    "IfcCableCarrierSegment": "ELEC", "IfcCableCarrierFitting": "ELEC",
    "IfcDistributionControlElement": "ELEC", "IfcElectricDistributionBoard": "ELEC",
    "IfcElectricMotor": "ELEC", "IfcElectricGenerator": "ELEC",
    "IfcAirTerminal": "ACMV",
    "IfcPump": "MECH", "IfcFan": "MECH", "IfcUnitaryEquipment": "MECH",
    "IfcHeatExchanger": "MECH", "IfcBoiler": "MECH", "IfcChiller": "MECH",
    "IfcCoolingTower": "MECH", "IfcCompressor": "MECH", "IfcMotorConnection": "MECH",
    "IfcSprayTerminal": "FP",
    "IfcColumn": "STR", "IfcBeam": "STR", "IfcMember": "STR",
    "IfcPlate": "STR", "IfcReinforcingBar": "STR",
}


def infer_discipline(ifc_class: str) -> str:
    return DISCIPLINE_MAP.get(ifc_class, "ARC")


# ── Tunables — geometric thresholds only, every one logged when it decides a label ───────
WALL_MIN_AREA_M2 = 2.0
WALL_MIN_HEIGHT_M = 1.5
WALL_FLOOR_TOL_M = 0.5           # a wall candidate's z_min must reach within this of the floor
DOOR_FLOOR_TOL_M = 0.15          # a vertical plane's z_min within this of the floor -> door
FURNITURE_MIN_HEIGHT_M = 0.1
FURNITURE_MAX_HEIGHT_M = 2.2
FURNITURE_MIN_VOLUME_M3 = 0.0003  # was 0.01 — checked against ground truth and that
                                   # excluded 54/62 true furniture clusters (recall 12.9%):
                                   # after Phase 2 carves off furniture tops (as plane
                                   # segments) and absorbs floor-touching bases (into the
                                   # floor plane's own RANSAC inliers), what survives as a
                                   # cluster is often just a thin leg/frame member — real
                                   # observed volumes as low as 0.0006m3. DBSCAN's own
                                   # CLUSTER_MIN_SAMPLES=30 already keeps pure noise specks
                                   # out; this threshold's job was never noise-filtering.
FURNITURE_MAX_VOLUME_M3 = 8.0
FURNITURE_MIN_FLATNESS_RATIO = 0.08  # smallest/largest AABB extent must clear this — a
                                      # near-flat cluster (a wall/window fragment that missed
                                      # plane detection, not a solid 3D object) fails it
FURNITURE_MAX_HEIGHT_ABOVE_FLOOR_M = 0.3  # NOTE: checked as a hard reject and reverted —
                                      # true furniture (0.54-1.26m above floor in this
                                      # dataset, since Phase 2 strips furniture tops/bases)
                                      # genuinely overlaps the fragment population (0.49m+).
                                      # Kept only as a soft confidence-adjuster below, not a
                                      # filter — see _classify_cluster().
FURNITURE_MAX_WALL_DIST_M = 0.05  # a genuine furniture piece sits in open room space; wall/
                                   # window/door/plate residue (fragments that broke off
                                   # their own plane during Phase 2's component-split) sits
                                   # AT that parent plane's footprint. Checked against ground
                                   # truth: EVERY such fragment measured 0.00m from its
                                   # nearest wall plane's AABB at every percentile (10-90th);
                                   # true furniture ranged 0.07-0.95m, median 0.35m. A clean,
                                   # real separation — unlike volume or elevation, which
                                   # (both checked) genuinely overlap in this dataset.


@dataclass
class ClassifiedSegment:
    segment: Segment
    ifc_class: str
    discipline: str
    classification_confidence: float
    low_confidence: bool
    classification_note: str
    center: np.ndarray       # element_transforms.center_x/y/z
    rotation_z: float        # element_transforms.rotation_z
    bbox: np.ndarray         # element_transforms.bbox_x/y/z (AABB extents)


def _plane_yaw(normal: np.ndarray) -> float:
    """Facing direction of a vertical plane, from its normal — meaningful for wall/window/
    door (which way they face); not computed for anything else (see module docstring on
    rotation_x/y=0 convention — same reasoning: no defensible orientation without more
    shape information than a flat/blob AABB provides)."""
    return float(np.arctan2(normal[1], normal[0]))


def classify_segments(segments: list[Segment], log=print) -> list[ClassifiedSegment]:
    # Floor level, if any floor plane was found — needed for the door/window heuristic.
    # None of this is guessed: it's the actual measured height of whichever plane
    # segment_pointcloud() already determined was the floor by relative elevation.
    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None
    if floor_z is not None:
        log(f"§CLASSIFY floor level detected at z={floor_z:.3f}m "
            f"(from {len(floor_segments)} floor segment(s)) — used for door/window "
            f"disambiguation and furniture floor-proximity check")
    else:
        log(f"§CLASSIFY no floor segment found — door/window disambiguation and furniture "
            f"floor-proximity check both unavailable, will default to IfcBuildingElementProxy "
            f"rather than guess")

    # Vertical (wall-orientation) planes, for the furniture-vs-wall-debris wall-distance
    # check — computed once here rather than per-cluster.
    wall_planes = [s for s in segments if s.orientation == "vertical"]

    out: list[ClassifiedSegment] = []
    for seg in segments:
        extent = seg.aabb_max - seg.aabb_min
        center = seg.centroid

        if seg.geometry_type == "plane":
            ifc_class, conf, note, rot_z = _classify_plane(seg, extent, floor_z)
        else:
            ifc_class, conf, note = _classify_cluster(seg, extent, floor_z, wall_planes)
            rot_z = 0.0

        discipline = infer_discipline(ifc_class)
        low_conf = conf < 0.5 or seg.low_confidence
        cs = ClassifiedSegment(
            segment=seg, ifc_class=ifc_class, discipline=discipline,
            classification_confidence=conf, low_confidence=low_conf,
            classification_note=note, center=center, rotation_z=rot_z, bbox=extent,
        )
        out.append(cs)
        log(f"§CLASSIFY seg#{seg.id:<4} {seg.geometry_type:7} -> {ifc_class:24s} "
            f"disc={discipline:4s} conf={conf:.2f}{' [LOW]' if low_conf else ''}  {note}")

    n_low = sum(1 for c in out if c.low_confidence)
    n_proxy = sum(1 for c in out if c.ifc_class == "IfcBuildingElementProxy")
    log(f"§CLASSIFY EXIT: {len(out)} classified, {n_low} low-confidence, "
        f"{n_proxy} deferred to IfcBuildingElementProxy")
    return out


def _classify_plane(seg: Segment, extent: np.ndarray, floor_z: float | None
                     ) -> tuple[str, float, str, float]:
    if seg.orientation == "floor":
        return "IfcSlab", 0.9, "floor-oriented plane", 0.0
    if seg.orientation == "oblique":
        return "IfcRoof", 0.85, "sloped (oblique) plane", 0.0
    if seg.orientation == "ceiling":
        # Genuinely ambiguous without more signal: could be a structural slab, a suspended
        # covering, or (per Phase 2's own validated finding) several of each coincidentally
        # coplanar. IfcSlab is the majority real case in this dataset's ground truth, but
        # this is a real assumption, not a measurement — flagged accordingly, not silently
        # presented as certain.
        return "IfcSlab", 0.55, "ceiling-oriented plane (majority-case default; could be " \
                                 "IfcCovering — not distinguishable from geometry alone)", 0.0

    # orientation == 'vertical'
    area = max(extent[0], extent[1]) * extent[2]   # in-plane footprint width x height
    rot_z = _plane_yaw(seg.normal)
    z_min = seg.aabb_min[2]
    touches_floor = floor_z is not None and z_min <= floor_z + WALL_FLOOR_TOL_M
    if area >= WALL_MIN_AREA_M2 and extent[2] >= WALL_MIN_HEIGHT_M:
        if touches_floor or floor_z is None:
            return "IfcWall", 0.9, f"large vertical plane, area={area:.1f}m2 height={extent[2]:.1f}m", rot_z
        # Large and tall, but doesn't start near floor level — a true architectural wall
        # normally does. More likely a large structural plate/member up in a roof
        # structure (a real, measured confusion this project ran into — see README) than
        # a wall. Not confident enough to call it IfcWall; not evidence enough for
        # anything more specific either.
        return "IfcBuildingElementProxy", 0.3, \
            f"large vertical plane but z_min={z_min:.2f}m doesn't reach floor " \
            f"(floor={floor_z:.2f}m) — large-and-tall alone isn't wall evidence without " \
            f"touching the floor", rot_z

    if floor_z is None:
        return "IfcBuildingElementProxy", 0.3, \
            "small vertical plane, no floor reference available to disambiguate door/window", rot_z

    z_min = seg.aabb_min[2]
    if abs(z_min - floor_z) <= DOOR_FLOOR_TOL_M:
        return "IfcDoor", 0.6, f"small vertical plane reaching floor level " \
                                f"(z_min={z_min:.2f}m, floor={floor_z:.2f}m)", rot_z
    return "IfcWindow", 0.6, f"small vertical plane elevated above floor " \
                              f"(z_min={z_min:.2f}m, floor={floor_z:.2f}m)", rot_z


def _classify_cluster(seg: Segment, extent: np.ndarray, floor_z: float | None,
                       wall_planes: list[Segment]) -> tuple[str, float, str]:
    volume = float(np.prod(extent))
    height = extent[2]
    flatness = float(extent.min() / extent.max()) if extent.max() > 0 else 0.0

    if flatness < FURNITURE_MIN_FLATNESS_RATIO:
        # Near-flat (thin in one axis relative to the others) — a stray planar fragment
        # that missed RANSAC plane detection, not a solid 3D object. Purely a shape check.
        return "IfcBuildingElementProxy", 0.3, \
            f"cluster too flat for a solid furniture piece " \
            f"(flatness={flatness:.3f} < {FURNITURE_MIN_FLATNESS_RATIO}, likely a planar " \
            f"fragment that missed RANSAC plane detection, vol={volume:.3f}m3)"

    if not (FURNITURE_MIN_HEIGHT_M <= height <= FURNITURE_MAX_HEIGHT_M
            and FURNITURE_MIN_VOLUME_M3 <= volume <= FURNITURE_MAX_VOLUME_M3):
        return "IfcBuildingElementProxy", 0.3, \
            f"cluster outside furniture-scale envelope (vol={volume:.3f}m3 height={height:.2f}m)"

    # Wall-distance filter — see FURNITURE_MAX_WALL_DIST_M's comment for the ground-truth
    # evidence behind this: a clean, real separator, unlike volume or elevation (both tried,
    # both genuinely overlap in this dataset — see the reverted-heuristic comments in git
    # history / README). Wall/window/door/plate residue sits AT its parent plane (dist~0m);
    # real furniture doesn't. This is a hard reject, not a confidence adjustment, because the
    # ground-truth check found no overlap to hedge against.
    if wall_planes:
        wall_dist = min(aabb_gap(seg.aabb_min, seg.aabb_max, w.aabb_min, w.aabb_max)
                         for w in wall_planes)
        if wall_dist <= FURNITURE_MAX_WALL_DIST_M:
            return "IfcBuildingElementProxy", 0.3, \
                f"furniture-scale cluster but touching a wall plane " \
                f"(dist={wall_dist:.3f}m <= {FURNITURE_MAX_WALL_DIST_M}m) — more likely " \
                f"debris from that wall/window/door than freestanding furniture " \
                f"(vol={volume:.3f}m3 height={height:.2f}m)"
    else:
        wall_dist = None

    # Elevation-above-floor genuinely overlaps between furniture and non-furniture in this
    # dataset (checked, not assumed — see FURNITURE_MAX_HEIGHT_ABOVE_FLOOR_M's comment), so
    # it only softens confidence here, never rejects outright.
    conf = 0.6
    note = f"furniture-scale cluster, clear of walls " \
           f"(wall_dist={wall_dist:.2f}m, vol={volume:.3f}m3 height={height:.2f}m)" \
        if wall_dist is not None else \
           f"furniture-scale cluster, no wall planes to check against " \
           f"(vol={volume:.3f}m3 height={height:.2f}m)"
    if floor_z is not None:
        height_above_floor = seg.aabb_min[2] - floor_z
        note += f", {height_above_floor:.2f}m above floor"
        if height_above_floor > FURNITURE_MAX_HEIGHT_ABOVE_FLOOR_M:
            conf = 0.45  # still plausible but further from where most true furniture in
                          # this dataset actually sits — lowered, not zeroed
    return "IfcFurniture", conf, note
