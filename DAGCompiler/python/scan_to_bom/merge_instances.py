# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
merge_instances.py — Phase 3.5: cluster-fragment instance merging.

Now that Phase 3 assigns each segment a predicted ifc_class, this reunites disconnected
cluster fragments of ONE real object (a chair's 4 legs + seat, each its own DBSCAN cluster
from Phase 2 since they're not point-connected) into one logical instance — the piece
explicitly deferred from Phase 2.5's geometry-only merge, which correctly refused to guess
"these belong together" without a predicted type.

Only merges within the SAME predicted ifc_class (never cross-class — the same safety
discipline as Phase 2.5's same-orientation requirement), and only CLUSTER-geometry segments —
PLANE segments already went through their own reunification in Phase 2.5; running this pass
over them too would conflate two different kinds of fragmentation.

Proximity-based (AABB gap, reusing segment.py's aabb_gap — the same tested helper Phase 2.5
uses), operating at the SEGMENT level on already-confirmed connected components. Deliberately
NOT a wider DBSCAN radius on raw points — Phase 2's own tuning experiment already measured
that widening CLUSTER_EPS_M increases cross-contamination between different real objects
(purity dropped below 1.0 for the first time at eps=0.15). Merging confirmed-distinct
components by their own AABB gap, after they already have a type, is a different and safer
operation than re-clustering raw points blind.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from classify import ClassifiedSegment, infer_discipline
from segment import Segment, aabb_gap, _cluster_confidence, MIN_CLUSTER_CONFIDENT_POINTS

INSTANCE_MAX_GAP_M = 0.5  # see README "Instance merging" for the tuning story behind this

# Classes eligible for instance merging — "same predicted type" is only a meaningful merge
# signal when the type itself carries real information. Checked against ground truth: merging
# same-type IfcBuildingElementProxy fragments produced every single cross-contamination case
# in the first validation run (13/13 purity violations, up to 5 different real elements fused
# into one "instance") — Proxy means "unknown," not "the same unknown thing as that other
# fragment over there," so proximity + matching Proxy label is not evidence they're one
# object. IfcFurniture showed zero cross-contamination across the same run — a predicted
# type that's actually semantically specific IS real evidence. Excluding Proxy from merging
# entirely rather than tuning the gap threshold down for it — this isn't a gap-distance
# problem, it's that the class itself carries no grouping signal.
MERGEABLE_CLASSES = {"IfcFurniture"}


def merge_instances(classified: list[ClassifiedSegment], log=print) -> list[ClassifiedSegment]:
    cluster_cs = [c for c in classified
                  if c.segment.geometry_type == "cluster" and c.ifc_class in MERGEABLE_CLASSES]
    other_cs = [c for c in classified
                if c.segment.geometry_type != "cluster" or c.ifc_class not in MERGEABLE_CLASSES]

    parent = list(range(len(cluster_cs)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    def union(i: int, j: int) -> None:
        ri, rj = find(i), find(j)
        if ri != rj:
            parent[ri] = rj

    n_pairs_checked = 0
    n_pairs_merged = 0
    for i in range(len(cluster_cs)):
        for j in range(i + 1, len(cluster_cs)):
            a, b = cluster_cs[i], cluster_cs[j]
            if a.ifc_class != b.ifc_class:
                continue
            n_pairs_checked += 1
            gap = aabb_gap(a.segment.aabb_min, a.segment.aabb_max,
                            b.segment.aabb_min, b.segment.aabb_max)
            if gap > INSTANCE_MAX_GAP_M:
                continue
            union(i, j)
            n_pairs_merged += 1

    groups: dict[int, list[int]] = {}
    for i in range(len(cluster_cs)):
        groups.setdefault(find(i), []).append(i)

    merged_out: list[ClassifiedSegment] = []
    next_seg_id = (max((c.segment.id for c in classified), default=-1)) + 1
    n_groups_merged = 0
    for members_idx in groups.values():
        if len(members_idx) == 1:
            merged_out.append(cluster_cs[members_idx[0]])
            continue
        members = [cluster_cs[k] for k in members_idx]
        pt_idx = np.concatenate([m.segment.point_indices for m in members])
        aabb_min = np.min(np.stack([m.segment.aabb_min for m in members]), axis=0)
        aabb_max = np.max(np.stack([m.segment.aabb_max for m in members]), axis=0)
        total_pts = int(sum(m.segment.point_count for m in members))
        source_ids = [m.segment.id for m in members]
        geo_conf = _cluster_confidence(total_pts)
        # Classification confidence is conservative on merge, not optimistic: the merged
        # instance is only as certain as its least-certain constituent piece, never hidden
        # by averaging it away against more-confident pieces.
        class_conf = min(m.classification_confidence for m in members)
        ifc_class = members[0].ifc_class

        new_seg = Segment(
            id=next_seg_id, geometry_type="cluster", orientation=None,
            point_indices=pt_idx, aabb_min=aabb_min, aabb_max=aabb_max, normal=None,
            confidence=geo_conf, low_confidence=geo_conf < 0.5,
            support_note=f"Phase 3.5 instance merge of {len(members)} same-type fragments "
                         f"(source segment ids {source_ids}), {total_pts} total points "
                         f"(threshold {MIN_CLUSTER_CONFIDENT_POINTS} for full confidence)",
        )
        cs = ClassifiedSegment(
            segment=new_seg, ifc_class=ifc_class, discipline=infer_discipline(ifc_class),
            classification_confidence=class_conf,
            low_confidence=(geo_conf < 0.5) or (class_conf < 0.5) or any(m.low_confidence for m in members),
            classification_note=f"merged instance of {len(members)} {ifc_class} fragments "
                                 f"(source segment ids {source_ids})",
            center=(aabb_min + aabb_max) / 2.0, rotation_z=0.0, bbox=aabb_max - aabb_min,
        )
        log(f"§INSTANCE_MERGE seg#{next_seg_id} <- {source_ids} ({ifc_class}, "
            f"{'+'.join(str(m.segment.point_count) for m in members)} pts) "
            f"class_conf={class_conf:.2f}{' [LOW]' if cs.low_confidence else ''}")
        merged_out.append(cs)
        next_seg_id += 1
        n_groups_merged += 1

    log(f"§INSTANCE_MERGE checked {n_pairs_checked} same-type cluster pairs, "
        f"{n_pairs_merged} pairwise unions, {n_groups_merged} groups actually merged (size>1) "
        f"-> {len(cluster_cs)} cluster segments in, {len(merged_out)} out")

    return other_cs + merged_out
