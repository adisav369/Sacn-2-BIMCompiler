# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Merge LTU A-House discipline IFC files into a single federated model.

Uses ifcpatch MergeProjects recipe — the official IfcOpenShell merge approach.
Merges incrementally: after each discipline merge, writes to disk and reopens
to avoid memory pressure / segfaults on large models.

ARC file serves as the base (spatial structure).

Input:  DAGCompiler/lib/input/IFC/UNMERGED/LTU_AHouse_*.ifc
Output: DAGCompiler/lib/input/IFC/LTU_AHouse_merged.ifc
"""

import gc
import os
import sys
import time
import logging
import shutil
import ifcopenshell
import ifcpatch.recipes.MergeProjects

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNMERGED = os.path.join(BASE_DIR, "DAGCompiler/lib/input/IFC/UNMERGED")
OUT_PATH = os.path.join(BASE_DIR, "DAGCompiler/lib/input/IFC/LTU_AHouse_merged.ifc")
TMP_PATH = OUT_PATH + ".tmp"

# ARC is the base; remaining files are merged in order
DISC_FILES = [
    ("ARC",  "LTU_AHouse_ARC.ifc"),
    ("STR",  "LTU_AHouse_STR.ifc"),
    ("VOID", "LTU_AHouse_VOID.ifc"),
    ("AIR",  "LTU_AHouse_AIR.ifc"),
    ("DUCT", "LTU_AHouse_DUCT.ifc"),
    ("COOL", "LTU_AHouse_COOL.ifc"),
    ("HEAT", "LTU_AHouse_HEAT.ifc"),
    ("PLB",  "LTU_AHouse_PLB.ifc"),
    ("SAN",  "LTU_AHouse_SAN.ifc"),
]

logger = logging.getLogger("ifcpatch")


def count_products(model):
    """Return dict of IFC class -> count for IfcProduct subtypes."""
    counts = {}
    for p in model.by_type("IfcProduct"):
        cls = p.is_a()
        counts[cls] = counts.get(cls, 0) + 1
    return counts


def main():
    # Verify all source files exist
    for disc, fname in DISC_FILES:
        path = os.path.join(UNMERGED, fname)
        if not os.path.exists(path):
            print(f"ERROR: {path} not found")
            sys.exit(1)

    t_start = time.time()

    # Start with a copy of ARC
    arc_path = os.path.join(UNMERGED, DISC_FILES[0][1])
    print(f"Copying ARC base to working file...")
    shutil.copy2(arc_path, TMP_PATH)

    # Merge each discipline one at a time, writing after each
    for i, (disc, fname) in enumerate(DISC_FILES[1:], 1):
        donor_path = os.path.join(UNMERGED, fname)
        print(f"\n[{i}/8] Merging {disc}: {fname}")
        t1 = time.time()

        # Open current state
        base = ifcopenshell.open(TMP_PATH)
        products_before = len(base.by_type("IfcProduct"))

        # Open donor
        donor = ifcopenshell.open(donor_path)
        donor_products = len(donor.by_type("IfcProduct"))
        print(f"  Base products: {products_before}, Donor products: {donor_products}")

        # Merge
        patcher = ifcpatch.recipes.MergeProjects.Patcher(base, logger, [donor])
        patcher.patch()

        products_after = len(base.by_type("IfcProduct"))
        net = products_after - products_before
        print(f"  Net added: +{net} products (total: {products_after})")

        # Write back to disk
        print(f"  Writing intermediate file...")
        base.write(TMP_PATH)
        size_mb = os.path.getsize(TMP_PATH) / (1024*1024)
        print(f"  Size: {size_mb:.1f} MB")

        # Free memory
        del base
        del donor
        del patcher
        gc.collect()

        print(f"  Elapsed: {time.time()-t1:.0f}s")

    # Rename to final
    shutil.move(TMP_PATH, OUT_PATH)
    size_mb = os.path.getsize(OUT_PATH) / (1024*1024)
    print(f"\nFinal file: {OUT_PATH} ({size_mb:.1f} MB)")

    # Verification
    print(f"\n{'='*60}")
    print(f"VERIFICATION")
    print(f"{'='*60}")

    merged = ifcopenshell.open(OUT_PATH)

    print(f"\nElement breakdown in merged file:")
    counts = count_products(merged)
    total = 0
    for cls, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {cls:<35} {n:>6}")
        total += n
    print(f"  {'TOTAL':<35} {total:>6}")

    total_products = len(merged.by_type("IfcProduct"))
    print(f"\n  Total IfcProduct (incl subtypes): {total_products}")
    print(f"  IfcBuildingStorey: {len(merged.by_type('IfcBuildingStorey'))}")
    print(f"  IfcProject: {len(merged.by_type('IfcProject'))}")

    total_time = time.time() - t_start
    print(f"\nTotal elapsed: {total_time:.0f}s")
    print("Done.")


if __name__ == "__main__":
    main()
