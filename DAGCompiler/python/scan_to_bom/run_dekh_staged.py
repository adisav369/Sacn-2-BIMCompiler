# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
run_dekh_staged.py — Phase 6 full-scale DeKH run, split into checkpointed stages.

Four earlier attempts at running the full DeKH_B_ICU.laz (437M points) pipeline in one shot
died silently across session/environment boundaries in this environment (confirmed: even a
fully OS-detached process didn't survive — the whole execution environment appears to reset
between conversation turns, not just the tracked shell session). A single foreground call is
the only execution mode confirmed to survive here, and it's capped at ~10 minutes — likely not
enough for the whole pipeline at this scale in one call (segmentation alone measured 27.1s for
1.32M points on the real corner-crop smoke test; the full downsampled cloud is ~18x that).

So: three stages, each its own foreground call, each checkpointing its result to disk
(OUTSIDE the repo — DeKH is licensed third-party data, nothing derived from it may be
committed) so a stage that times out can be diagnosed (how far did it get — see segment.py's
own within-stage progress logging) and re-run without losing the stages before it.

    python3 run_dekh_staged.py --stage downsample --laz <path> --npy <path> \
        --checkpoint-dir <scratch dir outside the repo> --voxel-size 0.01
    python3 run_dekh_staged.py --stage segment --checkpoint-dir <same dir>
    python3 run_dekh_staged.py --stage classify --checkpoint-dir <same dir> \
        --gt-ifc-extracted <path> --pred-ifc-extracted <path>
"""

from __future__ import annotations

import argparse
import pickle
import time
from pathlib import Path

import numpy as np


def _log(msg: str):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def stage_downsample(args):
    from pointcloud_io import load_las_downsampled

    ckpt_dir = Path(args.checkpoint_dir)
    ckpt_dir.mkdir(parents=True, exist_ok=True)

    _log(f"STAGE 1/3 (downsample): {args.laz}")
    t0 = time.time()
    pc, kept_indices = load_las_downsampled(args.laz, voxel_size_m=args.voxel_size, log=_log)
    _log(f"STAGE 1/3 downsample done in {time.time()-t0:.1f}s -> {len(pc)} points")

    out_pc = ckpt_dir / "stage1_pointcloud.npz"
    np.savez(out_pc, xyz=pc.xyz, rgb=pc.rgb if pc.rgb is not None else np.zeros((0, 3)),
              has_rgb=pc.rgb is not None, kept_indices=kept_indices)
    _log(f"STAGE 1/3 checkpoint written -> {out_pc}")

    if args.npy:
        t0 = time.time()
        full_labels = np.load(args.npy, mmap_mode="r")
        labels_ds = np.asarray(full_labels[kept_indices])
        assert len(labels_ds) == len(pc), "label slice length must match downsampled point count"
        out_labels = ckpt_dir / "stage1_labels.npy"
        np.save(out_labels, labels_ds)
        _log(f"STAGE 1/3 aligned labels ({len(labels_ds)}, {len(np.unique(labels_ds))} "
              f"distinct values) written in {time.time()-t0:.1f}s -> {out_labels}")

    _log("STAGE 1/3 (downsample) COMPLETE")


def stage_segment(args):
    from pointcloud_io import PointCloud
    from normalize import normalize_pointcloud
    from segment import segment_pointcloud, merge_coplanar_fragments

    ckpt_dir = Path(args.checkpoint_dir)
    in_pc = ckpt_dir / "stage1_pointcloud.npz"
    if not in_pc.exists():
        raise SystemExit(f"missing {in_pc} — run --stage downsample first")

    _log(f"STAGE 2/3 (segment): loading checkpoint {in_pc}")
    data = np.load(in_pc)
    xyz = data["xyz"]
    rgb = data["rgb"] if bool(data["has_rgb"]) else None
    pc = PointCloud(xyz, rgb)
    _log(f"STAGE 2/3 loaded {len(pc)} points")

    t0 = time.time()
    pc, tack_point = normalize_pointcloud(pc, log=_log)
    _log(f"STAGE 2/3 normalize done in {time.time()-t0:.1f}s")

    t0 = time.time()
    segments = segment_pointcloud(pc, log=_log)
    _log(f"STAGE 2/3 segment_pointcloud done in {time.time()-t0:.1f}s -> {len(segments)} raw segments")

    t0 = time.time()
    segments = merge_coplanar_fragments(segments, log=_log)
    _log(f"STAGE 2/3 merge_coplanar_fragments done in {time.time()-t0:.1f}s -> {len(segments)} segments")

    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None

    out_path = ckpt_dir / "stage2_segments.pkl"
    with open(out_path, "wb") as f:
        pickle.dump({"segments": segments, "xyz": pc.xyz, "floor_z": floor_z,
                     "tack_point": tack_point}, f, protocol=pickle.HIGHEST_PROTOCOL)
    _log(f"STAGE 2/3 checkpoint written -> {out_path}")
    _log("STAGE 2/3 (segment) COMPLETE")


def stage_classify(args):
    from classify import classify_segments
    from merge_instances import merge_instances
    from write_reference_db import write_reference_db
    from validate_real_world import _extract_gt_elements, _report_spatial_match

    ckpt_dir = Path(args.checkpoint_dir)
    in_seg = ckpt_dir / "stage2_segments.pkl"
    if not in_seg.exists():
        raise SystemExit(f"missing {in_seg} — run --stage segment first")

    _log(f"STAGE 3/3 (classify): loading checkpoint {in_seg}")
    with open(in_seg, "rb") as f:
        data = pickle.load(f)
    segments, xyz, floor_z = data["segments"], data["xyz"], data["floor_z"]
    tack_point = data["tack_point"]
    _log(f"STAGE 3/3 loaded {len(segments)} segments")

    t0 = time.time()
    classified = classify_segments(segments, points=xyz, log=_log)
    _log(f"STAGE 3/3 classify done in {time.time()-t0:.1f}s")

    t0 = time.time()
    merged = merge_instances(classified, log=_log)
    _log(f"STAGE 3/3 instance-merge done in {time.time()-t0:.1f}s -> {len(merged)} elements")

    ref_db_path = ckpt_dir / "stage3_reference.db"
    write_reference_db(merged, ref_db_path, floor_z, log=_log)
    _log(f"STAGE 3/3 reference DB written -> {ref_db_path}")

    report_lines = []
    def _r(msg=""):
        report_lines.append(msg)
        print(msg)

    _r(f"\n=== DeKH_B_ICU Phase 6 full-run report ===")
    from collections import Counter
    n_low = sum(1 for cs in merged if cs.low_confidence)
    n_proxy = sum(1 for cs in merged if cs.ifc_class == "IfcBuildingElementProxy")
    _r(f"elements: {len(merged)}, low-confidence: {n_low}, deferred to Proxy: {n_proxy}")
    _r(f"by class: {dict(Counter(cs.ifc_class for cs in merged))}")

    # Score 1: unnamed grouping signal against the .npy labels, if checkpointed
    labels_path = ckpt_dir / "stage1_labels.npy"
    if labels_path.exists():
        labels_ds = np.load(labels_path)
        purity_violations = 0
        label_to_segs: dict = {}
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
        _r(f"\n--- segmentation purity vs .npy (unnamed grouping signal) ---")
        _r(f"segments: {len(segments)}")
        _r(f"purity violations (mixed >1 true label, purity<0.95): {purity_violations}/{len(segments)}")
        _r(f"distinct true labels spanning >1 segment: {frag_count}/{len(label_to_segs)}")

    # Score 2: spatial IoU-style match against real ground-truth IFC. The GT (and baseline)
    # DBs were extracted with --skip-normalize, i.e. in the IFC's own raw coordinate frame;
    # our own segments went through normalize_pointcloud() (normalized = raw - tack_point),
    # so comparing AABBs directly would compare two different origins and (correctly)
    # find near-zero overlap regardless of how good the predictions actually are. Un-shift
    # back to the raw frame with the same tack_point normalize_pointcloud() used.
    _r(f"\n(un-shifting predicted AABBs by the normalization tack_point {tack_point.tolist()} "
       f"before matching against the raw-frame ground-truth IFC)")
    pred_elements = [dict(guid=f"seg_{cs.segment.id}", ifc_class=cs.ifc_class,
                           aabb_min=cs.segment.aabb_min + tack_point,
                           aabb_max=cs.segment.aabb_max + tack_point)
                      for cs in merged]
    if args.gt_ifc_extracted:
        gt_elements = _extract_gt_elements(args.gt_ifc_extracted)
        _report_spatial_match(pred_elements, gt_elements, "our prediction")
        if args.pred_ifc_extracted:
            pred_baseline = [dict(guid=g["guid"], ifc_class=g["ifc_class"],
                                   aabb_min=g["aabb_min"], aabb_max=g["aabb_max"])
                              for g in _extract_gt_elements(args.pred_ifc_extracted)]
            _report_spatial_match(pred_baseline, gt_elements,
                                   "BIMStruct3D published baseline (sanity-check reference only)")

    report_path = ckpt_dir / "stage3_report.txt"
    report_path.write_text("\n".join(report_lines), encoding="utf-8")
    _log(f"STAGE 3/3 report written -> {report_path}")
    _log("STAGE 3/3 (classify) COMPLETE — FULL RUN DONE")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stage", required=True, choices=["downsample", "segment", "classify"])
    ap.add_argument("--checkpoint-dir", required=True,
                     help="scratch directory OUTSIDE the repo -- shared across all 3 stages")
    ap.add_argument("--laz", help="required for --stage downsample")
    ap.add_argument("--npy", default=None, help="optional for --stage downsample")
    ap.add_argument("--voxel-size", type=float, default=0.01)
    ap.add_argument("--gt-ifc-extracted", default=None, help="for --stage classify")
    ap.add_argument("--pred-ifc-extracted", default=None, help="for --stage classify")
    args = ap.parse_args()

    import sys
    sys.path.insert(0, str(Path(__file__).parent))

    if args.stage == "downsample":
        if not args.laz:
            raise SystemExit("--laz is required for --stage downsample")
        stage_downsample(args)
    elif args.stage == "segment":
        stage_segment(args)
    elif args.stage == "classify":
        stage_classify(args)


if __name__ == "__main__":
    main()
