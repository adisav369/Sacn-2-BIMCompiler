# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
validate_segmentation.py — Phase 2 validation harness, NOT part of the shipped pipeline.

Runs segment.py against a synthetic point cloud (see gen_synthetic_pointcloud.py) and scores
the result against the held-out ground truth: for each produced Segment, what fraction of its
points came from a single real source element ("purity"), and how many real elements got
split across multiple segments or merged into one. The segmentation code itself never sees
the ground truth — this script reads it only to grade the output afterward, the same
blind-then-score discipline the rest of this codebase's witness tests use.

Usage:
    python3 validate_segmentation.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply
"""

from __future__ import annotations

import argparse
import time
from collections import Counter
from pathlib import Path

import numpy as np

from pointcloud_io import load_pointcloud, save_ply
from normalize import normalize_pointcloud, save_tack_point
from segment import segment_pointcloud, merge_coplanar_fragments


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ply", required=True, help="point cloud file — .ply, .xyz/.txt, or .las/.laz")
    ap.add_argument("--no-normalize", action="store_true",
                     help="skip coordinate normalization — score against raw ingested "
                          "coordinates, for comparison")
    ap.add_argument("--no-merge", action="store_true",
                     help="skip the Phase 2.5 coplanar-fragment merge — score raw Phase 2 "
                          "output, for comparison")
    ap.add_argument("--save-labeled", action="store_true",
                     help="also write <ply>.segmented.ply with a per-point segment-id label, "
                          "for visual inspection in a point cloud viewer")
    args = ap.parse_args()

    ply_path = Path(args.ply)
    gt_path = ply_path.with_suffix(".groundtruth.npz")

    pc = load_pointcloud(ply_path)
    print(f"§VALIDATE loaded {len(pc)} points from {ply_path}")

    if not args.no_normalize:
        pc, tack_point = normalize_pointcloud(pc)
        sidecar = save_tack_point(ply_path, tack_point)
        print(f"§VALIDATE tack point saved -> {sidecar}")

    t0 = time.time()
    segments = segment_pointcloud(pc)
    elapsed = time.time() - t0
    n_before_merge = len(segments)
    print(f"\n§VALIDATE segmentation took {elapsed:.1f}s for {len(pc)} points "
          f"-> {n_before_merge} segments")

    if not args.no_merge:
        t1 = time.time()
        segments = merge_coplanar_fragments(segments)
        merge_elapsed = time.time() - t1
        print(f"§VALIDATE Phase 2.5 merge took {merge_elapsed:.1f}s: "
              f"{n_before_merge} -> {len(segments)} segments")

    if not gt_path.exists():
        print(f"§VALIDATE no ground truth at {gt_path} — skipping purity scoring "
              f"(segmentation output stands on its own above)")
        return

    gt = np.load(gt_path, allow_pickle=True)
    gt_guid = gt["guid"]
    gt_class = gt["ifc_class"]

    print(f"\n§VALIDATE ── purity per segment (blind output vs held-out ground truth) ──")
    print(f"{'seg':>4} {'type':7} {'orient':10} {'pts':>7} {'conf':>5}  dominant true source")
    guid_to_segments: dict[str, set[int]] = {}
    for seg in segments:
        true_guids = gt_guid[seg.point_indices]
        true_classes = gt_class[seg.point_indices]
        counts = Counter(true_guids)
        dominant_guid, dominant_count = counts.most_common(1)[0]
        purity = dominant_count / len(true_guids)
        dominant_class = gt_class[gt_guid == dominant_guid][0]
        n_true_sources = len(counts)
        flag = " [LOW-CONF]" if seg.low_confidence else ""
        mix = f" (mixed, {n_true_sources} true sources)" if n_true_sources > 1 and purity < 0.9 else ""
        print(f"{seg.id:>4} {seg.geometry_type:7} {str(seg.orientation):10} "
              f"{seg.point_count:>7} {seg.confidence:>5.2f}  "
              f"{dominant_class:20s} purity={purity:.2f}{mix}{flag}")
        for g in counts:
            guid_to_segments.setdefault(g, set()).add((seg.id, seg.geometry_type))

    # Coverage: which real elements never got assigned to ANY segment at all (fell into
    # RANSAC/DBSCAN noise) — an honest gap, exactly what this pipeline is supposed to surface.
    all_true_guids = set(gt_guid.tolist())
    covered = set(guid_to_segments.keys())
    uncovered = all_true_guids - covered
    print(f"\n§VALIDATE coverage: {len(covered)}/{len(all_true_guids)} real elements have "
          f"at least one point in some segment")
    if uncovered:
        for g in sorted(uncovered):
            cls = gt_class[gt_guid == g][0]
            n_pts = int((gt_guid == g).sum())
            print(f"  UNCOVERED guid={g} class={cls} ({n_pts} scan points never joined a segment)")

    split_count = sum(1 for segs in guid_to_segments.values() if len(segs) > 1)
    # Break down by geometry_type — the Phase 2.5 merge only targets PLANE fragmentation;
    # CLUSTER fragmentation (multi-part furniture) is deliberately untouched (needs a
    # predicted type first, Phase 3's job). Reporting only the combined number would hide
    # whatever the merge actually did, since furniture fragmentation dominates the count.
    plane_split = sum(1 for segs in guid_to_segments.values()
                       if len({sid for sid, gt in segs if gt == "plane"}) > 1)
    cluster_split = sum(1 for segs in guid_to_segments.values()
                         if len({sid for sid, gt in segs if gt == "cluster"}) > 1)
    print(f"\n§VALIDATE fragmentation (combined): {split_count}/{len(covered)} real elements "
          f"had their points split across >1 segment")
    print(f"§VALIDATE fragmentation (plane-only, what Phase 2.5 merge targets): "
          f"{plane_split}/{len(covered)} real elements still split across >1 plane segment")
    print(f"§VALIDATE fragmentation (cluster-only, untouched, Phase 3's job): "
          f"{cluster_split}/{len(covered)} real elements still split across >1 cluster segment")

    n_low = sum(1 for s in segments if s.low_confidence)
    print(f"\n§VALIDATE SUMMARY: {len(segments)} segments produced, {n_low} low-confidence, "
          f"{len(uncovered)} real elements with zero coverage")

    if args.save_labeled:
        labels = np.full(len(pc), -1, dtype=np.int64)
        for seg in segments:
            labels[seg.point_indices] = seg.id
        out = ply_path.with_suffix(".segmented.ply")
        save_ply(out, pc, labels=labels)
        print(f"\n§VALIDATE labeled point cloud -> {out}")


if __name__ == "__main__":
    main()
