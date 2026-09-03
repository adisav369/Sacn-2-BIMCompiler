# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
validate_classification.py — Phase 3 validation harness, NOT part of the shipped pipeline.

Runs the full chain (ingest -> normalize -> segment -> merge -> classify) blind, then scores
predicted ifc_class against the held-out ground truth used by validate_segmentation.py — same
blind-then-score discipline, extended to the label axis. classify.py never sees this file's
contents.

A predicted class is scored "correct" against an equivalence set, not exact string match —
e.g. predicting IfcWall for a true IfcWallStandardCase is correct (both are real walls; the
distinction is an IFC authoring detail classify.py's geometry-only approach has no way to see).
IfcBuildingElementProxy predictions are scored separately as "deferred," never as wrong — that
class exists precisely so an uncertain case is honest, not silently penalized against a
guess it explicitly declined to make. The dangerous case this script specifically flags is
CONFIDENT-AND-WRONG: a high classification_confidence paired with an incorrect label, since
that's a strictly worse failure mode than a flagged low-confidence one.

Usage:
    python3 validate_classification.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply
"""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

import numpy as np

from pointcloud_io import load_pointcloud
from normalize import normalize_pointcloud
from segment import segment_pointcloud, merge_coplanar_fragments
from classify import classify_segments

# predicted class -> set of true classes that count as a correct prediction
EQUIVALENCE = {
    "IfcWall": {"IfcWall", "IfcWallStandardCase", "IfcCurtainWall"},
    "IfcSlab": {"IfcSlab", "IfcCovering"},
    "IfcRoof": {"IfcRoof", "IfcMember", "IfcPlate"},
    "IfcWindow": {"IfcWindow"},
    "IfcDoor": {"IfcDoor"},
    "IfcFurniture": {"IfcFurniture"},
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ply", required=True)
    args = ap.parse_args()

    ply_path = Path(args.ply)
    gt_path = ply_path.with_suffix(".groundtruth.npz")

    pc = load_pointcloud(ply_path)
    pc, _ = normalize_pointcloud(pc, log=lambda *a, **k: None)
    segments = segment_pointcloud(pc, log=lambda *a, **k: None)
    segments = merge_coplanar_fragments(segments, log=lambda *a, **k: None)
    classified = classify_segments(segments)

    if not gt_path.exists():
        print(f"§VALIDATE no ground truth at {gt_path} — cannot score classification")
        return

    gt = np.load(gt_path, allow_pickle=True)
    gt_guid, gt_class = gt["guid"], gt["ifc_class"]

    print(f"\n§VALIDATE ── classification vs held-out ground truth ──")
    print(f"{'seg':>4} {'type':7} {'predicted':24} {'conf':>5}  {'true (dominant)':20} result")

    n_correct = n_wrong = n_deferred = n_confident_wrong = 0
    confusion: Counter[tuple[str, str]] = Counter()

    for cs in classified:
        true_classes = gt_class[cs.segment.point_indices]
        if len(true_classes) == 0:
            continue
        dominant_true = Counter(true_classes.tolist()).most_common(1)[0][0]

        if cs.ifc_class == "IfcBuildingElementProxy":
            result = "DEFERRED"
            n_deferred += 1
        elif dominant_true in EQUIVALENCE.get(cs.ifc_class, set()):
            result = "correct"
            n_correct += 1
        else:
            result = "WRONG"
            n_wrong += 1
            confusion[(cs.ifc_class, dominant_true)] += 1
            if cs.classification_confidence >= 0.7:
                result = "WRONG [CONFIDENT-WRONG]"
                n_confident_wrong += 1

        print(f"{cs.segment.id:>4} {cs.segment.geometry_type:7} {cs.ifc_class:24} "
              f"{cs.classification_confidence:>5.2f}  {dominant_true:20} {result}")

    total_scored = n_correct + n_wrong + n_deferred
    print(f"\n§VALIDATE classification SUMMARY: {n_correct}/{total_scored} correct, "
          f"{n_wrong}/{total_scored} wrong, {n_deferred}/{total_scored} deferred to "
          f"IfcBuildingElementProxy")
    print(f"§VALIDATE confident-wrong (classification_confidence>=0.7 AND wrong): "
          f"{n_confident_wrong} — the failure mode that actually matters")

    if confusion:
        print(f"\n§VALIDATE wrong-prediction breakdown (predicted -> true, count):")
        for (pred, true), n in confusion.most_common():
            print(f"  {pred} -> {true}: {n}")


if __name__ == "__main__":
    main()
