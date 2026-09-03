# BIM Intent Compiler — Scan-to-BIM Pipeline
# Copyright (c) 2025-2026
# SPDX-License-Identifier: MIT
"""
run_scan_to_bom.py — production entry point, Phases 2 through 4.

Runs the full chain (ingest -> normalize -> segment -> merge-coplanar -> classify ->
instance-merge -> write_reference_db) on a real point cloud and writes the result to
the exact path convention the existing, UNMODIFIED Java BOM pipeline expects:
`DAGCompiler/lib/input/<building_type>_extracted.db` (see `ExtractionPopulator.populate()`
and `IFCtoBOMPipeline.run()`, both of which hardcode this path from the classification
YAML's `building_type` field — never a CLI argument).

That convention is what makes Phase 5 (BOM assembly) possible without touching a single
line of Java: point the classification YAML's `building_type` at a name distinct from any
real IFC-extracted building sharing this `lib/input/` directory, write this pipeline's
output there, and the existing `--populate` / `--classify` CLI flow treats it exactly like
an IFC extraction. Nothing downstream needs to know the source was a point cloud.

Usage:
    python3 run_scan_to_bom.py --ply <path.ply> --building-type SampleHousePC \
        --lib-input ../../lib/input
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from pointcloud_io import load_pointcloud
from normalize import normalize_pointcloud, save_tack_point
from segment import segment_pointcloud, merge_coplanar_fragments
from classify import classify_segments
from merge_instances import merge_instances
from write_reference_db import write_reference_db


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ply", required=True)
    ap.add_argument("--building-type", required=True,
                     help="matches the classification YAML's building_type field — the "
                          "output is written to <lib-input>/<building-type>_extracted.db, "
                          "the exact convention ExtractionPopulator/IFCtoBOMPipeline read")
    ap.add_argument("--lib-input", default="../../lib/input",
                     help="DAGCompiler/lib/input, or wherever this checkout's convention "
                          "path root lives")
    args = ap.parse_args()

    ply_path = Path(args.ply)
    pc = load_pointcloud(ply_path)
    print(f"§RUN loaded {len(pc.xyz)} points from {ply_path}")

    pc, tack_point = normalize_pointcloud(pc)
    save_tack_point(ply_path.with_suffix(".tackpoint.json"), tack_point, "bbox_center")

    segments = segment_pointcloud(pc)
    segments = merge_coplanar_fragments(segments)
    floor_segments = [s for s in segments if s.orientation == "floor"]
    floor_z = float(np.mean([s.centroid[2] for s in floor_segments])) if floor_segments else None

    classified = classify_segments(segments, points=pc.xyz)
    merged = merge_instances(classified)

    out_path = Path(args.lib_input) / f"{args.building_type}_extracted.db"
    write_reference_db(merged, out_path, floor_z)
    print(f"§RUN wrote {out_path} — ready for "
          f"'mvn exec:java -pl IFCtoBOM -Dexec.mainClass=com.bim.ifctobom.IFCtoBOMMain "
          f"-Dexec.args=\"--populate --classify <yaml for {args.building_type}>\"'")


if __name__ == "__main__":
    main()
