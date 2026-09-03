# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
r"""
validate_real_world.py — Phase 6: first real-world validation (DeKH dataset), NOT part of
the shipped pipeline.

Runs the full chain (ingest -> normalize -> segment -> merge-coplanar -> classify ->
instance-merge -> write_reference_db) on a REAL terrestrial LiDAR scan — not the synthetic
Sample House cloud every earlier phase was validated against — and scores it two ways,
neither of which the pipeline code itself ever reads:

  1. Against the scan's own per-point ground-truth label array (a real .npy, one numeric
     label per raw point, produced by the DeKH dataset's own human annotators per Kaufmann
     et al.'s ontology). We could NOT verify an authoritative numeric-ID-to-class-name
     mapping for this label space (checked: dataset README, the segmentation model's own
     config, the source paper in full, the external annotation-guideline repo — login-gated)
     so this is used as an UNNAMED grouping signal only, exactly like validate_segmentation.py
     already does for the synthetic ground truth: does each of our segments' points share one
     true label (purity), and how fragmented is each true group across our segments. No class
     names are inferred or invented anywhere in this script.

  2. Against the scan's real ground-truth IFC (extracted via extractIFCtoDB.py into the same
     reference-DB schema Phase 1 specifies). Real point clouds carry no per-point source-GUID
     the way the synthetic cloud's generator could stamp on — there IS no shared point-level
     correspondence to lean on here. So matching is spatial: for each ground-truth element,
     find the compatible-class predicted element with the largest AABB-overlap volume (an
     IoU-style match) — the same kind of metric this exact research area's own literature
     (BIMStruct3D, the pipeline this dataset was published alongside) reports for itself, not
     a methodology invented for this comparison.

Real-world scale note: a real terrestrial scan can be hundreds of millions of points (437M
for DeKH_B_ICU) -- ingestion uses pointcloud_io.load_las_downsampled() (chunked, voxel-grid,
index-preserving), never the full-resolution _load_las() path a synthetic-scale file uses.

Usage:
    python3 validate_real_world.py \
        --laz "C:\DeKH\Buildings\B\DeKH_B_ICU.laz" \
        --npy "C:\DeKH\Buildings\B\DeKH_B_ICU.npy" \
        --gt-ifc-extracted <path to DeKH_B_ICU.ifc already run through extractIFCtoDB.py> \
        --pred-ifc-extracted <same, for DeKH_B_ICU_pred_2025-06-03.ifc, optional> \
        --out-dir <scratch dir OUTSIDE the repo -- DeKH is licensed third-party data> \
        --voxel-size 0.01
"""

from __future__ import annotations

import argparse
import sqlite3
import time
from collections import Counter
from pathlib import Path

import numpy as np

from pointcloud_io import load_las_downsampled
from normalize import normalize_pointcloud
from segment import segment_pointcloud, merge_coplanar_fragments
from classify import classify_segments
from merge_instances import merge_instances
from write_reference_db import write_reference_db

EQUIVALENCE = {
    "IfcWall": {"IfcWall", "IfcWallStandardCase", "IfcCurtainWall"},
    "IfcSlab": {"IfcSlab", "IfcCovering"},
    "IfcRoof": {"IfcRoof", "IfcMember", "IfcPlate"},
    "IfcWindow": {"IfcWindow"},
    "IfcDoor": {"IfcDoor"},
    "IfcFurniture": {"IfcFurniture"},
    "IfcColumn": {"IfcColumn"},
}


def _aabb_overlap_volume(min1, max1, min2, max2) -> float:
    inter = np.maximum(0.0, np.minimum(max1, max2) - np.maximum(min1, min2))
    return float(np.prod(inter))


def _extract_gt_elements(db_path: str):
    con = sqlite3.connect(db_path)
    rows = con.execute(
        "SELECT em.guid, em.ifc_class, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ "
        "FROM elements_meta em JOIN elements_rtree r ON r.id = em.id").fetchall()
    con.close()
    out = []
    for guid, ifc_class, minx, maxx, miny, maxy, minz, maxz in rows:
        out.append(dict(guid=guid, ifc_class=ifc_class,
                         aabb_min=np.array([minx, miny, minz]),
                         aabb_max=np.array([maxx, maxy, maxz])))
    return out


def _spatial_match(pred_elements, gt_elements):
    """For each GT element, find the compatible-class predicted element with the largest
    AABB-overlap volume. Returns (matched, unmatched_gt, unmatched_pred) — matched is a list
    of (gt, pred, overlap_vol, rel_err[3])."""
    matched, unmatched_gt = [], []
    used_pred = set()
    for gt in gt_elements:
        gt_equiv = None
        for pred_class, equiv in EQUIVALENCE.items():
            if gt["ifc_class"] in equiv:
                gt_equiv = pred_class
                break
        if gt_equiv is None:
            continue  # GT class has no equivalence mapping (e.g. IfcOpeningElement) — skip
        best, best_vol = None, 0.0
        for i, pred in enumerate(pred_elements):
            if pred["ifc_class"] != gt_equiv:
                continue
            vol = _aabb_overlap_volume(gt["aabb_min"], gt["aabb_max"],
                                        pred["aabb_min"], pred["aabb_max"])
            if vol > best_vol:
                best, best_vol, best_i = pred, vol, i
        if best is not None:
            gt_bbox = gt["aabb_max"] - gt["aabb_min"]
            pred_bbox = best["aabb_max"] - best["aabb_min"]
            rel_err = np.abs(pred_bbox - gt_bbox) / np.maximum(gt_bbox, 1e-6)
            matched.append((gt, best, best_vol, rel_err))
            used_pred.add(best_i)
        else:
            unmatched_gt.append(gt)
    unmatched_pred = [p for i, p in enumerate(pred_elements) if i not in used_pred]
    return matched, unmatched_gt, unmatched_pred


def _report_spatial_match(pred_elements, gt_elements, label):
    matched, unmatched_gt, unmatched_pred = _spatial_match(pred_elements, gt_elements)
    n_gt_scoreable = sum(1 for gt in gt_elements if any(gt["ifc_class"] in e for e in EQUIVALENCE.values()))
    print(f"\n§VALIDATE ── spatial IoU-style match: {label} vs ground truth ──")
    print(f"  GT elements: {len(gt_elements)} total, {n_gt_scoreable} in a scoreable class")
    print(f"  matched (any spatial overlap, compatible class): {len(matched)}/{n_gt_scoreable} "
          f"({len(matched)/n_gt_scoreable:.1%})" if n_gt_scoreable else "  no scoreable GT elements")
    print(f"  unmatched predicted elements (no GT overlap): {len(unmatched_pred)}")
    if matched:
        vols = np.array([m[2] for m in matched])
        errs = np.array([m[3] for m in matched])
        print(f"  overlap volume: median={np.median(vols):.3f}m3, "
              f"{np.mean(vols > 0):.0%} with any positive overlap")
        print(f"  bbox rel_err (matched pairs): median per-axis = "
              f"{np.median(errs, axis=0).round(2).tolist()}")
    return matched, unmatched_gt, unmatched_pred


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--laz", required=True)
    ap.add_argument("--npy", required=True)
    ap.add_argument("--gt-ifc-extracted", required=True,
                     help="ground-truth IFC already run through extractIFCtoDB.py")
    ap.add_argument("--pred-ifc-extracted", default=None,
                     help="optional: published baseline prediction, same schema, for a "
                          "sanity-check reference point only")
    ap.add_argument("--out-dir", required=True,
                     help="scratch directory OUTSIDE the repo -- DeKH is licensed "
                          "third-party data, nothing derived from it may be committed")
    ap.add_argument("--voxel-size", type=float, default=0.01)
    args = ap.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    t0 = time.time()
    pc, kept_indices = load_las_downsampled(args.laz, voxel_size_m=args.voxel_size)
    print(f"§VALIDATE downsample took {time.time()-t0:.1f}s -> {len(pc)} points kept")

    t0 = time.time()
    full_labels = np.load(args.npy, mmap_mode="r")
    labels_ds = np.asarray(full_labels[kept_indices])
    assert len(labels_ds) == len(pc), "label slice length must match downsampled point count"
    print(f"§VALIDATE .npy slice took {time.time()-t0:.1f}s -> {len(labels_ds)} aligned labels, "
          f"{len(np.unique(labels_ds))} distinct label values present")

    pc, tack_point = normalize_pointcloud(pc)

    t0 = time.time()
    segments = segment_pointcloud(pc)
    segments = merge_coplanar_fragments(segments)
    print(f"§VALIDATE segmentation took {time.time()-t0:.1f}s -> {len(segments)} segments")

    # ── Score 1: Phase 2-style purity/coverage against the unnamed .npy grouping signal ──
    print(f"\n§VALIDATE ── segmentation purity vs .npy (unnamed grouping signal) ──")
    n_seg = len(segments)
    purity_violations = 0
    label_to_segs: dict[float, set[int]] = {}
    for seg in segments:
        true_labels = labels_ds[seg.point_indices]
        if len(true_labels) == 0:
            continue
        counts = Counter(true_labels.tolist())
        dominant, dom_count = counts.most_common(1)[0]
        purity = dom_count / len(true_labels)
        if len(counts) > 1 and purity < 0.95:
            purity_violations += 1
        for lbl in counts:
            label_to_segs.setdefault(lbl, set()).add(seg.id)
    frag_count = sum(1 for segs in label_to_segs.values() if len(segs) > 1)
    print(f"  segments: {n_seg}")
    print(f"  purity violations (mixed >1 true label, purity<0.95): {purity_violations}/{n_seg}")
    print(f"  distinct true labels spanning >1 segment: {frag_count}/{len(label_to_segs)}")

    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None

    t0 = time.time()
    classified = classify_segments(segments, points=pc.xyz)
    merged = merge_instances(classified)
    print(f"§VALIDATE classification+merge took {time.time()-t0:.1f}s -> {len(merged)} elements")

    ref_db_path = out_dir / "DeKH_reference.db"
    write_reference_db(merged, ref_db_path, floor_z)
    print(f"§VALIDATE reference DB written -> {ref_db_path}")

    pred_elements = [dict(guid=f"seg_{cs.segment.id}", ifc_class=cs.ifc_class,
                           aabb_min=cs.segment.aabb_min, aabb_max=cs.segment.aabb_max)
                      for cs in merged]
    n_low = sum(1 for cs in merged if cs.low_confidence)
    n_proxy = sum(1 for cs in merged if cs.ifc_class == "IfcBuildingElementProxy")
    print(f"\n§VALIDATE classification: {len(merged)} elements, {n_low} low-confidence, "
          f"{n_proxy} deferred to IfcBuildingElementProxy")
    print(f"  by class: {dict(Counter(cs.ifc_class for cs in merged))}")

    gt_elements = _extract_gt_elements(args.gt_ifc_extracted)
    _report_spatial_match(pred_elements, gt_elements, "our prediction")

    if args.pred_ifc_extracted:
        pred_baseline = [dict(guid=g["guid"], ifc_class=g["ifc_class"],
                               aabb_min=g["aabb_min"], aabb_max=g["aabb_max"])
                          for g in _extract_gt_elements(args.pred_ifc_extracted)]
        # Baseline's own IFC classes need no EQUIVALENCE translation on the "pred" side of
        # the match (only the GT side does) -- reuse the same matcher directly.
        _report_spatial_match(pred_baseline, gt_elements, "BIMStruct3D published baseline "
                                                            "(sanity-check reference only)")


if __name__ == "__main__":
    main()
