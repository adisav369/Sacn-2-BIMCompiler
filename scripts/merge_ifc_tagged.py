# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Merge multiple discipline IFC files into one, stamping each element with
Pset_BIMSource.Discipline so downstream extractors know the source discipline.

Usage:
    python3 scripts/merge_ifc_tagged.py \
        --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
        --pattern "LTU_AHouse_*.ifc" \
        --output DAGCompiler/lib/input/IFC/LTU_AHouse_merged.ifc \
        --disc-map \
            LTU_AHouse_AIR=VENT  LTU_AHouse_DUCT=VENT \
            LTU_AHouse_COOL=HVAC LTU_AHouse_HEAT=HEAT \
            LTU_AHouse_PLB=PLB   LTU_AHouse_SAN=SAN \
            LTU_AHouse_ARC=ARC   LTU_AHouse_STR=STR \
            LTU_AHouse_VOID=VOID

Each IfcProduct in the merged IFC gets:
    Pset_BIMSource {
        Discipline: "VENT"     (from --disc-map or filename stem)
        SourceFile: "LTU_AHouse_AIR.ifc"
    }

This allows extractIFCtoDB_open.py to read sub-disciplines from a merged IFC
without needing access to the original source files.
"""
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
import sys
import time
from pathlib import Path

import ifcopenshell
import ifcopenshell.api


def stamp_discipline(ifc_file, discipline, source_filename):
    """Add Pset_BIMSource with Discipline + SourceFile to every IfcProduct."""
    stamped = 0
    for product in ifc_file.by_type("IfcProduct"):
        try:
            pset = ifcopenshell.api.run("pset.add_pset",
                                         ifc_file,
                                         product=product,
                                         name="Pset_BIMSource")
            ifcopenshell.api.run("pset.edit_pset",
                                  ifc_file,
                                  pset=pset,
                                  properties={
                                      "Discipline": discipline,
                                      "SourceFile": source_filename,
                                  })
            stamped += 1
        except Exception as e:
            print(f"    WARN: stamp failed for {product.GlobalId}: {e}")
    return stamped


def merge_into(target, source):
    """Copy all entities from source into target IFC file.

    Uses ifcopenshell.api.project.append_asset for each IfcProduct,
    which handles remapping of internal IDs and relationships.
    """
    products = source.by_type("IfcProduct")
    copied = 0
    for product in products:
        try:
            ifcopenshell.api.run("project.append_asset",
                                  target,
                                  library=source,
                                  element=product)
            copied += 1
        except Exception as e:
            # Some elements may fail (proxy, orphaned refs) — log and continue
            if copied == 0 or copied % 1000 == 0:
                print(f"    WARN: copy failed ({type(e).__name__}): {e}")
    return copied


def main():
    parser = argparse.ArgumentParser(
        description="Merge discipline IFCs into one, stamping Pset_BIMSource.Discipline")
    parser.add_argument("--ifc-dir", required=True, help="Directory with source IFC files")
    parser.add_argument("--pattern", required=True, help="Glob pattern for source files")
    parser.add_argument("--output", required=True, help="Output merged IFC path")
    parser.add_argument("--disc-map", nargs="*", metavar="STEM=DISC",
                        help="Map filename stem to discipline. E.g. LTU_AHouse_PLB=PLB")
    args = parser.parse_args()

    # Build stem→discipline map
    disc_map = {}
    if args.disc_map:
        for pair in args.disc_map:
            stem, disc = pair.split("=", 1)
            disc_map[stem.strip()] = disc.strip().upper()

    ifc_files = sorted(Path(args.ifc_dir).glob(args.pattern))
    if not ifc_files:
        print(f"No files matching {args.pattern} in {args.ifc_dir}")
        sys.exit(1)

    print(f"Merging {len(ifc_files)} IFC files → {args.output}")
    if disc_map:
        print(f"  Disc overrides: {disc_map}")

    t0 = time.time()

    # Use the first (largest / ARC) file as the base
    base_file = ifc_files[0]
    print(f"\n[BASE] {base_file.name} ({base_file.stat().st_size / 1024 / 1024:.1f} MB)")
    target = ifcopenshell.open(str(base_file))

    # Stamp discipline on the base file's products
    base_disc = disc_map.get(base_file.stem, base_file.stem.split("_")[-1].upper())
    stamped = stamp_discipline(target, base_disc, base_file.name)
    print(f"  → stamped {stamped} products with Discipline={base_disc}")

    # Merge remaining files
    total_copied = 0
    for ifc_path in ifc_files[1:]:
        disc = disc_map.get(ifc_path.stem, ifc_path.stem.split("_")[-1].upper())
        size_mb = ifc_path.stat().st_size / 1024 / 1024
        print(f"\n[{disc}] {ifc_path.name} ({size_mb:.1f} MB)")

        source = ifcopenshell.open(str(ifc_path))

        # Stamp BEFORE merging so the Pset travels with the elements
        stamped = stamp_discipline(source, disc, ifc_path.name)
        print(f"  → stamped {stamped} products")

        copied = merge_into(target, source)
        total_copied += copied
        print(f"  → copied {copied} products into target")

    # Write merged output
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    target.write(str(output))

    elapsed = time.time() - t0
    size_mb = output.stat().st_size / 1024 / 1024

    # Verification summary
    merged = ifcopenshell.open(str(output))
    total_products = len(merged.by_type("IfcProduct"))

    print(f"\n{'=' * 60}")
    print(f"  MERGE SUMMARY")
    print(f"{'=' * 60}")
    print(f"  Output:   {output} ({size_mb:.1f} MB)")
    print(f"  Products: {total_products}")
    print(f"  Time:     {elapsed:.1f}s")

    # Check discipline coverage via Pset
    disc_counts = {}
    for p in merged.by_type("IfcProduct"):
        try:
            for rel in p.IsDefinedBy:
                if rel.is_a("IfcRelDefinesByProperties"):
                    pset = rel.RelatingPropertyDefinition
                    if pset.is_a("IfcPropertySet") and pset.Name == "Pset_BIMSource":
                        for prop in pset.HasProperties:
                            if prop.Name == "Discipline":
                                d = str(prop.NominalValue.wrappedValue)
                                disc_counts[d] = disc_counts.get(d, 0) + 1
        except (AttributeError, TypeError):
            pass

    if disc_counts:
        print(f"\n  By Pset_BIMSource.Discipline:")
        for d, c in sorted(disc_counts.items(), key=lambda x: -x[1]):
            print(f"    {d:10s} {c:6d}")
        untagged = total_products - sum(disc_counts.values())
        if untagged > 0:
            print(f"    {'UNTAGGED':10s} {untagged:6d}")
    else:
        print(f"  WARNING: No Pset_BIMSource found — stamping may have failed")

    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
