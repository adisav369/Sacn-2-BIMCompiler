# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
validate_instance_merge.py — Phase 3.5 validation harness, NOT part of the shipped pipeline.

Runs the full chain (ingest -> normalize -> segment -> merge -> classify -> instance-merge)
blind, then scores against the held-out ground truth on the two axes that matter: did instance
merging reduce fragmentation of real furniture elements (the actual goal), and did it introduce
any cross-contamination between different real objects (the failure the CLUSTER_EPS_M widening
experiment in Phase 2 already showed is easy to hit by being too generous with proximity)?

Usage:
    python3 validate_instance_merge.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply
"""

from __future__ import annotations
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------

import argparse
from collections import Counter
from pathlib import Path

import numpy as np

from pointcloud_io import load_pointcloud
from normalize import normalize_pointcloud
from segment import segment_pointcloud, merge_coplanar_fragments
from classify import classify_segments
from merge_instances import merge_instances


def _report(classified, gt_guid, gt_class, label: str):
    print(f"\n--- {label} ---")
    cluster_cs = [c for c in classified if c.segment.geometry_type == "cluster"]
    n_seg = len(cluster_cs)

    guid_to_segs: dict[str, set[int]] = {}
    purity_violations = 0
    for cs in cluster_cs:
        true_g = gt_guid[cs.segment.point_indices]
        counts = Counter(true_g.tolist())
        dominant, dom_count = counts.most_common(1)[0]
        purity = dom_count / len(true_g)
        if len(counts) > 1 and purity < 0.95:
            purity_violations += 1
            print(f"  seg#{cs.segment.id} PURITY DROP: {cs.ifc_class} purity={purity:.2f} "
                  f"mixed {len(counts)} true sources -> {counts.most_common(3)}")
        for g in counts:
            guid_to_segs.setdefault(g, set()).add(cs.segment.id)

    frag_count = sum(1 for segs in guid_to_segs.values() if len(segs) > 1)
    n_covered = len(guid_to_segs)
    print(f"  cluster segments: {n_seg}")
    print(f"  purity violations (mixed >1 true source, purity<0.95): {purity_violations}")
    print(f"  real elements still fragmented across >1 cluster segment: "
          f"{frag_count}/{n_covered}")
    return n_seg, purity_violations, frag_count


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ply", required=True)
    args = ap.parse_args()

    ply_path = Path(args.ply)
    gt_path = ply_path.with_suffix(".groundtruth.npz")
    if not gt_path.exists():
        print(f"§VALIDATE no ground truth at {gt_path} — cannot score")
        return

    gt = np.load(gt_path, allow_pickle=True)
    gt_guid, gt_class = gt["guid"], gt["ifc_class"]

    pc = load_pointcloud(ply_path)
    pc, _ = normalize_pointcloud(pc, log=lambda *a, **k: None)
    segments = segment_pointcloud(pc, log=lambda *a, **k: None)
    segments = merge_coplanar_fragments(segments, log=lambda *a, **k: None)
    classified = classify_segments(segments, points=pc.xyz, log=lambda *a, **k: None)

    n_before, viol_before, frag_before = _report(classified, gt_guid, gt_class,
                                                   "BEFORE instance merge (Phase 3 output)")

    merged = merge_instances(classified)

    n_after, viol_after, frag_after = _report(merged, gt_guid, gt_class,
                                                "AFTER Phase 3.5 instance merge")

    print(f"\n§VALIDATE instance-merge SUMMARY:")
    print(f"  cluster segments: {n_before} -> {n_after}")
    print(f"  purity violations: {viol_before} -> {viol_after} "
          f"({'REGRESSION' if viol_after > viol_before else 'OK'})")
    print(f"  fragmented real elements: {frag_before} -> {frag_after} "
          f"({'IMPROVED' if frag_after < frag_before else 'NO CHANGE' if frag_after == frag_before else 'REGRESSION'})")


if __name__ == "__main__":
    main()
