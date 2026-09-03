# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
segment.py — Phase 2 of the Scan-to-BIM roadmap: point-cloud segmentation.

  raw point cloud -> plane segmentation (floor/ceiling/wall) -> object clustering
  (everything left over) -> confidence-scored Segment list.

This stage produces NO semantic labels (no "IfcWall", no product type) — that's Phase 3.
It only answers "what are the distinct physical surfaces/objects in this scan, and how
sure are we of each one." Every segment gets a confidence score; nothing below the
threshold is silently dropped or force-classified — it comes out flagged low_confidence,
matching this project's prime rule (extract or compile only, never invent). A human or a
later, better algorithm can revisit low-confidence segments; Phase 3 must not treat them
as equivalent to confident ones.

Algorithm, in order:
  1. Iterative RANSAC plane extraction — repeatedly find the best-supported plane among
     remaining points; stop when no plane clears MIN_PLANE_INLIERS.
  2. Each accepted plane is split into spatially-connected components (two physically
     separate walls can share the same orientation/offset — e.g. two segments of the same
     straight wall line broken by a doorway gap — and must not be merged into one segment).
  3. Each plane component is classified by its normal: near-horizontal normal -> 'horizontal'
     (floor/ceiling candidate, disambiguated by relative height once all horizontals are
     found); near-vertical normal -> 'vertical' (wall candidate); otherwise 'oblique'
     (roofs, sloped surfaces — flagged low-confidence by default since Phase 2 has no
     dedicated handling for them yet).
  4. Whatever points are left after plane extraction get DBSCAN-clustered in 3D — each
     cluster is an object candidate (furniture, fixtures, fittings).
  5. Confidence: planes score by inlier ratio at acceptance; clusters score by point count
     against a minimum-for-full-confidence threshold. Both are logged white-box, per this
     codebase's own convention (see MEPDevicePlacer.java's GEO logging) — every segment's
     confidence is traceable to the measurement that produced it, not a hidden heuristic.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field

import numpy as np
from scipy import ndimage
from sklearn.cluster import DBSCAN

from pointcloud_io import PointCloud

# ── Tunables — every one logged when it changes a decision, none hidden ──────────────

RANSAC_DIST_THRESHOLD_M = 0.02      # a point counts as a plane inlier within 2cm of it
RANSAC_ITERATIONS = 400             # per plane search
MIN_PLANE_INLIERS = 300             # below this, not worth calling a plane at all
MIN_PLANE_CONFIDENT_INLIERS = 1500  # at/above this inlier count, confidence = 1.0
MAX_PLANES = 40                     # hard stop — a building has a bounded number of surfaces
HORIZONTAL_NORMAL_TOL_DEG = 20.0    # normal within this of +-Z -> 'horizontal'
VERTICAL_NORMAL_TOL_DEG = 20.0      # normal within this of the XY plane -> 'vertical'
PLANE_COMPONENT_EPS_M = 0.30        # connectivity radius for splitting one plane into physical
                                     # pieces — points within this of each other (via the grid
                                     # dilation below) count as one connected surface
PLANE_COMPONENT_MIN_SAMPLES = 20
PLANE_COMPONENT_GRID_CELL_M = 0.01  # grid-based connected-components cell size (Phase 6 fix —
                                     # was DBSCAN on raw (u,v) points, which is O(density^2)
                                     # memory in the eps-radius neighbor query: fine at the
                                     # synthetic test's ~400 pts/m^2 but a real terrestrial scan
                                     # (DeKH_B_ICU, even after 1cm-voxel downsampling) is dense
                                     # enough on a real wall/floor plane to blow available RAM
                                     # (confirmed: MemoryError on a 4.5M-point corner crop).
                                     # "same connected physical surface" is fundamentally a
                                     # spatial-adjacency question, not a general clustering one,
                                     # so it's answered on a 2D occupancy grid instead: memory is
                                     # bounded by grid CELL COUNT (a plane's spatial extent /
                                     # cell size), never by point density or count. 10x finer
                                     # than PLANE_COMPONENT_EPS_M so the eps-dilation below (in
                                     # whole cells) approximates the real distance closely.
MIN_COMPONENT_POINTS = 150          # a split-off piece below this isn't a real architectural
                                     # surface (furniture-top / roof-facet fragment) — its points
                                     # go back into the remaining pool, not out as a segment
CLUSTER_EPS_M = 0.08                # DBSCAN eps for leftover-point object clustering — tested
                                     # against 0.15: wider eps increased fragmentation (213->318
                                     # segments) AND introduced cross-contamination between
                                     # distinct real elements (purity dropped below 1.0 for the
                                     # first time) by bridging gaps between adjacent-but-different
                                     # objects, not just within one object's disconnected parts.
                                     # 0.08 measured better on both axes — keep it.
CLUSTER_MIN_SAMPLES = 30
MIN_CLUSTER_CONFIDENT_POINTS = 200  # cluster point count for confidence = 1.0


@dataclass
class Segment:
    id: int
    geometry_type: str              # 'plane' | 'cluster'
    orientation: str | None         # 'horizontal' | 'vertical' | 'oblique' | None (clusters)
    point_indices: np.ndarray       # indices into the source PointCloud
    aabb_min: np.ndarray
    aabb_max: np.ndarray
    normal: np.ndarray | None       # unit normal, planes only
    confidence: float               # 0..1
    low_confidence: bool
    support_note: str               # human-readable: what measurement set the confidence

    @property
    def point_count(self) -> int:
        return len(self.point_indices)

    @property
    def centroid(self) -> np.ndarray:
        return (self.aabb_min + self.aabb_max) / 2.0


def _fit_plane_ransac(xyz: np.ndarray, rng: np.random.Generator, log=None,
                       progress_every: int = 100) -> tuple[np.ndarray, float, np.ndarray] | None:
    """One RANSAC search over `xyz` (all remaining, unassigned points). Returns
    (normal, offset, inlier_mask) for the best plane found, or None if nothing clears
    MIN_PLANE_INLIERS.

    `log` is optional and, when given, prints coarse within-search progress every
    `progress_every` iterations — at real-world point counts (millions, not the synthetic
    test's 670K) a single plane search can itself take real time, and losing a run mid-search
    with no trace of how far it got (Phase 6's checkpointing work) is worse than a little log
    noise. None by default so this stays silent for the already-fast synthetic-scale path.
    """
    n = len(xyz)
    if n < 3:
        return None
    best_inliers = None
    best_count = 0
    best_normal = None
    best_offset = 0.0
    for it in range(RANSAC_ITERATIONS):
        if log is not None and it > 0 and it % progress_every == 0:
            log(f"§SEGMENT   RANSAC iter {it}/{RANSAC_ITERATIONS} "
                f"({n} candidate points, best so far: {best_count} inliers)")
        idx = rng.choice(n, size=3, replace=False)
        p0, p1, p2 = xyz[idx]
        v1, v2 = p1 - p0, p2 - p0
        normal = np.cross(v1, v2)
        norm = np.linalg.norm(normal)
        if norm < 1e-9:
            continue  # degenerate (collinear) sample
        normal = normal / norm
        offset = -np.dot(normal, p0)
        dist = np.abs(xyz @ normal + offset)
        inliers = dist < RANSAC_DIST_THRESHOLD_M
        count = int(inliers.sum())
        if count > best_count:
            best_count = count
            best_inliers = inliers
            best_normal = normal
            best_offset = offset
    if best_count < MIN_PLANE_INLIERS:
        return None
    # Refit on the inlier set for a stabler normal (least-squares plane through centroid).
    pts = xyz[best_inliers]
    centroid = pts.mean(axis=0)
    centered = pts - centroid
    # Eigendecompose the 3x3 covariance matrix rather than SVD the full (N,3) point set —
    # equivalent result (smallest-eigenvalue eigenvector = plane normal), but O(1) in point
    # count instead of blowing up memory/LAPACK on hundreds of thousands of inlier points.
    cov = centered.T @ centered
    eigvals, eigvecs = np.linalg.eigh(cov)
    refined_normal = eigvecs[:, 0]  # eigh returns ascending eigenvalues — index 0 is smallest
    refined_offset = -np.dot(refined_normal, centroid)
    dist = np.abs(xyz @ refined_normal + refined_offset)
    inliers = dist < RANSAC_DIST_THRESHOLD_M
    return refined_normal, refined_offset, inliers


def _classify_orientation(normal: np.ndarray) -> str:
    angle_to_z = np.degrees(np.arccos(np.clip(abs(normal[2]), 0, 1)))
    if angle_to_z <= HORIZONTAL_NORMAL_TOL_DEG:
        return "horizontal"
    if angle_to_z >= 90 - VERTICAL_NORMAL_TOL_DEG:
        return "vertical"
    return "oblique"


def _plane_confidence(inlier_count: int) -> float:
    return float(min(1.0, inlier_count / MIN_PLANE_CONFIDENT_INLIERS))


def _cluster_confidence(point_count: int) -> float:
    return float(min(1.0, point_count / MIN_CLUSTER_CONFIDENT_POINTS))


def _split_plane_into_components(xyz: np.ndarray, plane_indices: np.ndarray,
                                  normal: np.ndarray) -> list[np.ndarray]:
    """A single RANSAC plane can span multiple physically separate surfaces (two wall
    segments broken by a gap, opposite walls that happen to be parallel and coplanar in a
    symmetric room). Project onto the plane's local 2D frame and find connected components
    there — connectivity within the plane, not 3D proximity across it, is what defines "one
    wall."

    Grid-based (rasterize (u,v) into an occupancy grid, dilate by the eps radius, flood-fill
    label the connected cells — scipy.ndimage.label), not DBSCAN: this is fundamentally a
    spatial-adjacency question ("is every point reachable from every other through a chain of
    nearby points"), which a grid answers with memory bounded by the plane's spatial extent
    (cell count), never by point density. DBSCAN's eps-radius neighbor query is
    O(density^2)-ish in memory — fine at the synthetic test's ~400 pts/m^2, confirmed to
    MemoryError on a real terrestrial scan's actual density (DeKH_B_ICU smoke test, Phase 6).
    """
    pts = xyz[plane_indices]
    # Build an orthonormal in-plane basis (u, v) from the normal.
    arbitrary = np.array([1.0, 0.0, 0.0]) if abs(normal[0]) < 0.9 else np.array([0.0, 1.0, 0.0])
    u = np.cross(normal, arbitrary)
    u = u / np.linalg.norm(u)
    v = np.cross(normal, u)
    uv = np.column_stack([pts @ u, pts @ v])

    cell = PLANE_COMPONENT_GRID_CELL_M
    uv_min = uv.min(axis=0)
    grid_idx = np.floor((uv - uv_min) / cell).astype(np.int64)
    grid_shape = (int(grid_idx[:, 0].max()) + 1, int(grid_idx[:, 1].max()) + 1)
    occupied = np.zeros(grid_shape, dtype=bool)
    occupied[grid_idx[:, 0], grid_idx[:, 1]] = True

    # Dilate by HALF the eps radius (in whole cells): two separate blobs merge once their
    # own dilation halos touch, i.e. once their real edge-to-edge gap is <= 2*(this radius)
    # -- so half of PLANE_COMPONENT_EPS_M makes the merge threshold the real eps, matching
    # DBSCAN's "any two points within eps are directly linked" rather than doubling it.
    # (Caught by direct A/B measurement against the pre-change DBSCAN version, not assumed:
    # dilating by the full eps merged real elements DBSCAN kept separate -- plane-purity
    # fragmentation on the synthetic ground truth got measurably worse, 55/58 -> 57/58 real
    # elements split across >1 segment, before this fix.)
    dilation_cells = max(1, round(PLANE_COMPONENT_EPS_M / (2 * cell)))
    dilated = ndimage.binary_dilation(occupied, iterations=dilation_cells)
    labeled, _ = ndimage.label(dilated, structure=np.ones((3, 3)))  # 8-connectivity
    point_labels = labeled[grid_idx[:, 0], grid_idx[:, 1]]

    components = []
    for lbl in np.unique(point_labels):
        if lbl == 0:
            continue  # unlabeled background — cannot occur for an originally-occupied cell,
                       # kept only as a defensive no-op
        comp_idx = plane_indices[point_labels == lbl]
        if len(comp_idx) < PLANE_COMPONENT_MIN_SAMPLES:
            continue  # too few points — same noise-suppression intent as DBSCAN's min_samples
        components.append(comp_idx)
    return components


def segment_pointcloud(pc: PointCloud, seed: int = 0, log=print) -> list[Segment]:
    xyz = pc.xyz
    remaining_mask = np.ones(len(xyz), dtype=bool)
    rng = np.random.default_rng(seed)
    segments: list[Segment] = []
    seg_id = 0
    leftover_chunks: list[np.ndarray] = []  # rejected small plane fragments -> clustering stage
    n_rejected_fragments = 0
    n_rejected_points = 0

    log(f"§SEGMENT start: {len(xyz)} points")

    # ── Stage 1+2+3: iterative plane extraction ──────────────────────────────────────
    plane_count = 0
    while plane_count < MAX_PLANES:
        remaining_idx = np.nonzero(remaining_mask)[0]
        if len(remaining_idx) < MIN_PLANE_INLIERS:
            break
        log(f"§SEGMENT plane search #{plane_count + 1}/{MAX_PLANES} starting: "
            f"{len(remaining_idx)} candidate points")
        t_plane = time.time()
        result = _fit_plane_ransac(xyz[remaining_idx], rng, log=log)
        log(f"§SEGMENT   RANSAC search done in {time.time() - t_plane:.1f}s")
        if result is None:
            log(f"§SEGMENT plane search: no plane clears {MIN_PLANE_INLIERS} inliers "
                f"among {len(remaining_idx)} remaining points — stopping plane extraction")
            break
        normal, offset, local_inlier_mask = result
        plane_global_idx = remaining_idx[local_inlier_mask]
        orientation = _classify_orientation(normal)

        # Always consume the full inlier set from the plane-search pool — whether or not
        # every piece clears MIN_COMPONENT_POINTS below — so RANSAC always makes forward
        # progress and can't re-discover the same fragment forever.
        remaining_mask[plane_global_idx] = False

        log(f"§SEGMENT   plane found ({orientation}, {len(plane_global_idx)} inliers) — "
            f"splitting into connected components")
        t_split = time.time()
        components = _split_plane_into_components(xyz, plane_global_idx, normal)
        log(f"§SEGMENT   component split done in {time.time() - t_split:.1f}s -> "
            f"{len(components)} components")
        if not components:
            log(f"§SEGMENT plane found ({orientation}, {len(plane_global_idx)} pts) but "
                f"split into zero connected components — releasing to clustering pool")
            leftover_chunks.append(plane_global_idx)
            continue

        accepted_any = False
        for comp_idx in components:
            if len(comp_idx) < MIN_COMPONENT_POINTS:
                # Furniture-top / roof-facet-scale fragment, not a real architectural
                # surface at this size — don't report it as a plane segment, but don't
                # lose the points either: they may still form a real object cluster later.
                leftover_chunks.append(comp_idx)
                n_rejected_fragments += 1
                n_rejected_points += len(comp_idx)
                continue
            accepted_any = True
            aabb_min = xyz[comp_idx].min(axis=0)
            aabb_max = xyz[comp_idx].max(axis=0)
            conf = _plane_confidence(len(comp_idx))
            seg = Segment(
                id=seg_id, geometry_type="plane", orientation=orientation,
                point_indices=comp_idx, aabb_min=aabb_min, aabb_max=aabb_max,
                normal=normal, confidence=conf, low_confidence=conf < 0.5,
                support_note=f"RANSAC plane, {len(comp_idx)} inliers "
                             f"(threshold {MIN_PLANE_CONFIDENT_INLIERS} for full confidence)",
            )
            segments.append(seg)
            log(f"§SEGMENT plane #{seg_id} {orientation:10s} pts={len(comp_idx):6d} "
                f"conf={conf:.2f}{' [LOW]' if seg.low_confidence else ''} "
                f"normal=({normal[0]:+.2f},{normal[1]:+.2f},{normal[2]:+.2f}) "
                f"aabb=[{aabb_min[0]:.2f},{aabb_max[0]:.2f}]x[{aabb_min[1]:.2f},{aabb_max[1]:.2f}]"
                f"x[{aabb_min[2]:.2f},{aabb_max[2]:.2f}]")
            seg_id += 1
        if accepted_any:
            plane_count += 1
        # else: this whole plane search only produced sub-threshold fragments — don't
        # count it against MAX_PLANES, but do keep looping (points already consumed above).

    # Disambiguate floor vs ceiling among 'horizontal' planes by relative height —
    # the lowest confident horizontal plane is the floor, everything else horizontal
    # above it is a ceiling/soffit candidate. Purely a relabel; doesn't touch confidence.
    horizontals = [s for s in segments if s.orientation == "horizontal"]
    if horizontals:
        floor_seg = min(horizontals, key=lambda s: s.centroid[2])
        for s in horizontals:
            s.orientation = "floor" if s is floor_seg else "ceiling"

    leftover_idx = np.concatenate(leftover_chunks) if leftover_chunks else np.array([], dtype=int)
    log(f"§SEGMENT planes done: {len(segments)} plane segments accepted, "
        f"{n_rejected_fragments} sub-{MIN_COMPONENT_POINTS}pt fragments ({n_rejected_points} pts) "
        f"released to clustering, {int(remaining_mask.sum())} points never touched by a plane search")

    # ── Stage 4: cluster whatever is left — never-planar points plus rejected fragments ──
    remaining_idx = np.concatenate([np.nonzero(remaining_mask)[0], leftover_idx]).astype(int)
    remaining_idx = np.unique(remaining_idx)
    if len(remaining_idx) >= CLUSTER_MIN_SAMPLES:
        log(f"§SEGMENT residual DBSCAN clustering starting: {len(remaining_idx)} points "
            f"(eps={CLUSTER_EPS_M}m) — no per-iteration progress available (single sklearn "
            f"call); this line plus the 'done' line below bound how long it ran if a run "
            f"dies mid-call")
        t_cluster = time.time()
        labels = DBSCAN(eps=CLUSTER_EPS_M, min_samples=CLUSTER_MIN_SAMPLES).fit_predict(
            xyz[remaining_idx])
        log(f"§SEGMENT residual DBSCAN clustering done in {time.time() - t_cluster:.1f}s")
        n_noise = int((labels == -1).sum())
        for lbl in sorted(set(labels)):
            if lbl == -1:
                continue
            comp_idx = remaining_idx[labels == lbl]
            aabb_min = xyz[comp_idx].min(axis=0)
            aabb_max = xyz[comp_idx].max(axis=0)
            conf = _cluster_confidence(len(comp_idx))
            seg = Segment(
                id=seg_id, geometry_type="cluster", orientation=None,
                point_indices=comp_idx, aabb_min=aabb_min, aabb_max=aabb_max,
                normal=None, confidence=conf, low_confidence=conf < 0.5,
                support_note=f"DBSCAN cluster, {len(comp_idx)} points "
                             f"(threshold {MIN_CLUSTER_CONFIDENT_POINTS} for full confidence)",
            )
            segments.append(seg)
            log(f"§SEGMENT cluster #{seg_id} pts={len(comp_idx):6d} conf={conf:.2f}"
                f"{' [LOW]' if seg.low_confidence else ''} "
                f"aabb=[{aabb_min[0]:.2f},{aabb_max[0]:.2f}]x[{aabb_min[1]:.2f},{aabb_max[1]:.2f}]"
                f"x[{aabb_min[2]:.2f},{aabb_max[2]:.2f}]")
            seg_id += 1
        log(f"§SEGMENT clustering done: {n_noise} points never joined a cluster "
            f"(isolated/sparse — not reported as a segment; genuinely no shape support)")
    else:
        log(f"§SEGMENT clustering skipped: only {len(remaining_idx)} points remain, "
            f"below CLUSTER_MIN_SAMPLES={CLUSTER_MIN_SAMPLES}")

    n_low = sum(1 for s in segments if s.low_confidence)
    log(f"§SEGMENT EXIT: {len(segments)} segments total, {n_low} flagged low_confidence")
    return segments


# ── Phase 2.5: geometric reunification of coplanar fragments ────────────────────────
#
# segment_pointcloud()'s component-split step (necessarily) breaks one continuous
# physical surface into several Segments whenever an occluder — a doorway gap, furniture
# against a wall — interrupts point coverage. This pass reunites fragments that are
# pieces of the SAME real surface, using only geometry already computed in Phase 2 (shared
# plane equation + spatial proximity) — no semantic label needed, so it runs before Phase 3
# and never touches cluster segments (disconnected multi-part objects like chair legs need
# a predicted type first; see README).
#
# Merge criterion, all three required: (1) same orientation class (never merges floor with
# ceiling even though both come from the same 'horizontal' RANSAC family — they're
# genuinely different planes offset in Z), (2) near-identical plane equation (normal within
# NORMAL_ANGLE_TOL_DEG, offset along that normal within OFFSET_TOL_M — this is what rules
# out merging two genuinely different parallel walls on opposite sides of a room: they
# share an orientation but not an offset), (3) AABBs within MAX_GAP_M of each other — bridges
# a doorway-scale gap, not the width of a room.

NORMAL_ANGLE_TOL_DEG = 8.0
OFFSET_TOL_M = 0.05
MAX_GAP_M = 2.0


def aabb_gap(min1: np.ndarray, max1: np.ndarray, min2: np.ndarray, max2: np.ndarray) -> float:
    """Minimum distance between two AABBs — 0 if they overlap or touch."""
    d = np.maximum(np.maximum(min1 - max2, min2 - max1), 0)
    return float(np.linalg.norm(d))


def _plane_offset(normal: np.ndarray, point_on_plane: np.ndarray) -> float:
    """c such that normal . x + c = 0 for points on the plane."""
    return -float(np.dot(normal, point_on_plane))


def merge_coplanar_fragments(segments: list[Segment], log=print) -> list[Segment]:
    plane_idx = [i for i, s in enumerate(segments) if s.geometry_type == "plane"]
    other_segments = [s for s in segments if s.geometry_type != "plane"]
    planes = [segments[i] for i in plane_idx]

    parent = list(range(len(planes)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    def union(i: int, j: int) -> None:
        ri, rj = find(i), find(j)
        if ri != rj:
            parent[ri] = rj

    cos_tol = np.cos(np.radians(NORMAL_ANGLE_TOL_DEG))
    n_pairs_checked = 0
    n_pairs_merged = 0
    for i in range(len(planes)):
        for j in range(i + 1, len(planes)):
            a, b = planes[i], planes[j]
            if a.orientation != b.orientation:
                continue
            n_pairs_checked += 1
            cos_angle = abs(float(np.dot(a.normal, b.normal)))
            if cos_angle < cos_tol:
                continue
            # Align b's normal sign to a's before comparing offsets — a plane's normal
            # direction is arbitrary (SVD sign is not deterministic), but the plane itself
            # (and its offset) is the same regardless of which way the normal points.
            sign = 1.0 if np.dot(a.normal, b.normal) >= 0 else -1.0
            off_a = _plane_offset(a.normal, a.centroid)
            off_b = _plane_offset(sign * b.normal, b.centroid)
            if abs(off_a - off_b) > OFFSET_TOL_M:
                continue
            gap = aabb_gap(a.aabb_min, a.aabb_max, b.aabb_min, b.aabb_max)
            if gap > MAX_GAP_M:
                continue
            union(i, j)
            n_pairs_merged += 1

    groups: dict[int, list[int]] = {}
    for i in range(len(planes)):
        groups.setdefault(find(i), []).append(i)

    merged_segments: list[Segment] = []
    next_id = (max((s.id for s in segments), default=-1)) + 1
    n_groups_merged = 0
    for members_idx in groups.values():
        if len(members_idx) == 1:
            merged_segments.append(planes[members_idx[0]])
            continue
        members = [planes[k] for k in members_idx]
        pt_idx = np.concatenate([m.point_indices for m in members])
        aabb_min = np.min(np.stack([m.aabb_min for m in members]), axis=0)
        aabb_max = np.max(np.stack([m.aabb_max for m in members]), axis=0)
        total_pts = sum(m.point_count for m in members)
        ref_normal = members[0].normal
        normal_acc = np.zeros(3)
        for m in members:
            sign = 1.0 if np.dot(m.normal, ref_normal) >= 0 else -1.0
            normal_acc += sign * m.normal * m.point_count
        normal = normal_acc / np.linalg.norm(normal_acc)
        conf = _plane_confidence(total_pts)
        source_ids = [m.id for m in members]
        seg = Segment(
            id=next_id, geometry_type="plane", orientation=members[0].orientation,
            point_indices=pt_idx, aabb_min=aabb_min, aabb_max=aabb_max,
            normal=normal, confidence=conf, low_confidence=conf < 0.5,
            support_note=f"Phase 2.5 merge of {len(members)} coplanar fragments "
                         f"(source segment ids {source_ids}), {total_pts} total inliers "
                         f"(threshold {MIN_PLANE_CONFIDENT_INLIERS} for full confidence)",
        )
        log(f"§MERGE plane #{next_id} <- {source_ids} "
            f"({'+'.join(str(m.point_count) for m in members)} pts) "
            f"total={total_pts} conf={conf:.2f}{' [LOW]' if seg.low_confidence else ''}")
        merged_segments.append(seg)
        next_id += 1
        n_groups_merged += 1

    log(f"§MERGE checked {n_pairs_checked} same-orientation plane pairs, "
        f"{n_pairs_merged} pairwise unions, {n_groups_merged} groups actually merged "
        f"(size>1) -> {len(planes)} plane segments in, {len(merged_segments)} out")

    all_segments = merged_segments + other_segments
    for new_id, s in enumerate(sorted(all_segments, key=lambda s: s.id)):
        s.id = new_id
    return all_segments
