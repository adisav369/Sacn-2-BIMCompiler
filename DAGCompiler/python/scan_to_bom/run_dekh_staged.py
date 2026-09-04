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

--stage segment is itself resumable one level deeper (Phase 6, interleaved per-orientation
search): a real 23.7M-point cloud needs more than 10 minutes of RANSAC search rounds even
before component-splitting/clustering, so it checkpoints after every round it doesn't finish
in time and picks back up from there — just re-run the identical `--stage segment` command
until it logs "STAGE 2/3 (segment) COMPLETE" instead of "round checkpoint written".
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
    """Resumable: plane extraction can run well beyond this environment's single ~10-minute
    foreground-call limit on a real multi-million-point scan (confirmed — see
    _plane_extraction_round's docstring in segment.py), and now runs until a real
    diminishing-returns signal fires (EARLY_STOP_WINDOW/EARLY_STOP_YIELD_FRAC in segment.py —
    Phase 6 fix #2: a fixed round count was confirmed to cut off real construction-site-scale
    buildings while they still had substantial real surfaces left; see README's "round-budget
    exhaustion" finding), not a fixed round count — MAX_PLANES is now a generous safety
    ceiling, not the primary control. Each invocation runs rounds until either plane
    extraction genuinely finishes (proceeds straight to floor/ceiling + residual clustering +
    the final stage2_segments.pkl checkpoint) or a wall-clock round-time-budget is hit (writes
    a ROUND checkpoint and returns — re-run the exact same command to resume from the next
    round; the diminishing-returns yield history persists across resumed calls too).
    `xyz`/`tack_point` are NOT stored in the round checkpoint — they're cheaply and
    deterministically reloaded from stage1 + normalize_pointcloud() on every invocation
    (normalize measured at 1.4s on the full 23.7M-point cloud) rather than duplicating
    ~570MB of point data into every round checkpoint write.
    """
    from pointcloud_io import PointCloud
    from normalize import normalize_pointcloud
    from segment import (merge_coplanar_fragments, _plane_extraction_round,
                          _finish_segmentation, MAX_PLANES, MIN_PLANE_INLIERS,
                          EARLY_STOP_WINDOW, EARLY_STOP_YIELD_FRAC)

    ckpt_dir = Path(args.checkpoint_dir)
    in_pc = ckpt_dir / "stage1_pointcloud.npz"
    if not in_pc.exists():
        raise SystemExit(f"missing {in_pc} — run --stage downsample first")

    _log(f"STAGE 2/3 (segment): loading checkpoint {in_pc}")
    data = np.load(in_pc)
    raw_xyz = data["xyz"]
    rgb = data["rgb"] if bool(data["has_rgb"]) else None
    pc = PointCloud(raw_xyz, rgb)
    _log(f"STAGE 2/3 loaded {len(pc)} points")

    t0 = time.time()
    pc, tack_point = normalize_pointcloud(pc, log=_log)
    _log(f"STAGE 2/3 normalize done in {time.time()-t0:.1f}s")
    xyz = pc.xyz

    round_ckpt_path = ckpt_dir / "stage2_round_checkpoint.pkl"
    if round_ckpt_path.exists():
        _log(f"STAGE 2/3 resuming from round checkpoint {round_ckpt_path}")
        with open(round_ckpt_path, "rb") as f:
            state = pickle.load(f)
        remaining_mask = state["remaining_mask"]
        rng = np.random.default_rng()
        rng.bit_generator.state = state["rng_state"]
        segments = state["segments"]
        seg_id = state["seg_id"]
        leftover_chunks = state["leftover_chunks"]
        reject_stats = state["reject_stats"]
        plane_count = state["plane_count"]
        round_num = state["round_num"]
        yield_history = state["yield_history"]
        best_round_yield = state["best_round_yield"]
        _log(f"STAGE 2/3 resumed at round {round_num} ({plane_count} rounds accepted "
             f"something so far), {len(segments)} plane segments so far, "
             f"{int(remaining_mask.sum())} points not yet touched by a plane search")
    else:
        remaining_mask = np.ones(len(xyz), dtype=bool)
        rng = np.random.default_rng(0)
        segments = []
        seg_id = 0
        leftover_chunks = []
        reject_stats = {"fragments": 0, "points": 0}
        plane_count = 0
        round_num = 0
        yield_history = []
        best_round_yield = 0

    t_budget_start = time.time()
    stopped_for_time_budget = False
    stopped_for_diminishing_returns = False
    while round_num < MAX_PLANES:
        if time.time() - t_budget_start > args.round_time_budget_s:
            stopped_for_time_budget = True
            break
        round_num += 1
        seg_id, found_any, accepted_any, round_yield = _plane_extraction_round(
            xyz, remaining_mask, rng, segments, seg_id, leftover_chunks, reject_stats,
            _log, round_num)
        if not found_any:
            _log(f"STAGE 2/3 plane search: no plane clears {MIN_PLANE_INLIERS} inliers "
                 f"among remaining points — plane extraction complete")
            break
        if accepted_any:
            plane_count += 1
        yield_history.append(round_yield)
        best_round_yield = max(best_round_yield, round_yield)
        if len(yield_history) >= EARLY_STOP_WINDOW:
            recent_mean = sum(yield_history[-EARLY_STOP_WINDOW:]) / EARLY_STOP_WINDOW
            if best_round_yield > 0 and recent_mean < EARLY_STOP_YIELD_FRAC * best_round_yield:
                _log(f"STAGE 2/3 diminishing returns: last {EARLY_STOP_WINDOW} rounds "
                     f"averaged {recent_mean:.0f} accepted points/round, under "
                     f"{EARLY_STOP_YIELD_FRAC:.1%} of this run's best single round "
                     f"({best_round_yield} points) — plane extraction complete (real "
                     f"surfaces exhausted for this scene, not an arbitrary round cap)")
                stopped_for_diminishing_returns = True
                break

    if stopped_for_time_budget:
        with open(round_ckpt_path, "wb") as f:
            pickle.dump({"remaining_mask": remaining_mask, "rng_state": rng.bit_generator.state,
                         "segments": segments, "seg_id": seg_id, "leftover_chunks": leftover_chunks,
                         "reject_stats": reject_stats, "plane_count": plane_count,
                         "round_num": round_num, "yield_history": yield_history,
                         "best_round_yield": best_round_yield},
                        f, protocol=pickle.HIGHEST_PROTOCOL)
        _log(f"STAGE 2/3 round time budget ({args.round_time_budget_s}s) reached at round "
             f"{round_num} ({len(segments)} plane segments so far) — round "
             f"checkpoint written -> {round_ckpt_path} — re-run the identical command to continue")
        return

    stop_reason = "diminishing returns" if stopped_for_diminishing_returns else \
        (f"round {round_num}/{MAX_PLANES} safety ceiling" if round_num >= MAX_PLANES else
         "no plane clears the inlier floor")
    _log(f"STAGE 2/3 plane extraction complete at round {round_num} ({stop_reason}) "
         f"({len(segments)} plane segments) — finishing (floor/ceiling relabel + "
         f"residual clustering)")
    t0 = time.time()
    segments = _finish_segmentation(xyz, remaining_mask, segments, seg_id, leftover_chunks,
                                     reject_stats["fragments"], reject_stats["points"], _log)
    _log(f"STAGE 2/3 finish done in {time.time()-t0:.1f}s -> {len(segments)} segments")

    t0 = time.time()
    segments = merge_coplanar_fragments(segments, log=_log)
    _log(f"STAGE 2/3 merge_coplanar_fragments done in {time.time()-t0:.1f}s -> {len(segments)} segments")

    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None

    out_path = ckpt_dir / "stage2_segments.pkl"
    with open(out_path, "wb") as f:
        pickle.dump({"segments": segments, "xyz": xyz, "floor_z": floor_z,
                     "tack_point": tack_point}, f, protocol=pickle.HIGHEST_PROTOCOL)
    _log(f"STAGE 2/3 checkpoint written -> {out_path}")
    if round_ckpt_path.exists():
        round_ckpt_path.unlink()
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
    ap.add_argument("--round-time-budget-s", type=float, default=480.0,
                     help="--stage segment only: wall-clock budget per invocation for plane-"
                          "extraction rounds, leaving headroom under this environment's ~10-"
                          "minute single-foreground-call limit for npz load/normalize/pickle "
                          "overhead. Re-run the identical command to resume from a round "
                          "checkpoint if this is hit before all MAX_PLANES rounds complete.")
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
