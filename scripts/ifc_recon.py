# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
BIM Compiler — IFC Reconnaissance (fast, no extraction)

Reads IFC files directly with ifcopenshell to report:
  - Element count, storey structure, IFC classes
  - What's NEW vs existing component library
  - Validation rule mining potential
  - Coverage check against ad_ifc_class_map

Usage:
    ./scripts/ifc_recon.py DAGCompiler/lib/input/IFC/Clinic_Architectural_IFC2x3.ifc
    ./scripts/ifc_recon.py DAGCompiler/lib/input/IFC/*.ifc
    ./scripts/ifc_recon.py --dir DAGCompiler/lib/input/IFC/

No extracted DB created. Read-only reconnaissance.
"""

import sys, os, sqlite3, argparse
from pathlib import Path
from collections import Counter, defaultdict

try:
    import ifcopenshell
except ImportError:
    print("ERROR: ifcopenshell not installed. pip install ifcopenshell", file=sys.stderr)
    sys.exit(1)

# ── Colors ───────────────────────────────────────────────────

B  = "\033[1m"
R  = "\033[0m"
G  = "\033[32m"
Y  = "\033[33m"
RD = "\033[31m"
D  = "\033[90m"

def c(color, text):
    return f"{color}{text}{R}"

# ── Load existing library state ──────────────────────────────

def load_library_state(project_dir):
    comp_db = project_dir / "library" / "component_library.db"
    disc_db = project_dir / "library" / "ERP.db"

    existing_classes = set()
    registered_classes = set()
    product_count = 0

    if comp_db.exists():
        try:
            conn = sqlite3.connect(str(comp_db))
            existing_classes = {r[0] for r in conn.execute(
                "SELECT DISTINCT ifc_class FROM I_Geometry_Map")}
            product_count = conn.execute("SELECT COUNT(*) FROM M_Product").fetchone()[0]
            conn.close()
        except Exception:
            pass

    if disc_db.exists():
        try:
            conn = sqlite3.connect(str(disc_db))
            registered_classes = {r[0] for r in conn.execute(
                "SELECT ifc_class FROM ad_ifc_class_map WHERE is_active=1")}
            conn.close()
        except Exception:
            pass

    return existing_classes, registered_classes, product_count

# ── Analyze one IFC file ─────────────────────────────────────

def analyze_ifc(path, existing_classes, registered_classes):
    f = ifcopenshell.open(str(path))
    schema = f.schema

    # Elements (skip IfcOpeningElement, IfcSpace — metadata only)
    elements = [e for e in f.by_type("IfcElement")
                if not e.is_a("IfcOpeningElement")]

    # Class distribution
    class_counts = Counter(e.is_a() for e in elements)

    # Storey structure
    storey_counts = Counter()
    storey_map = {}
    for rel in f.by_type("IfcRelContainedInSpatialStructure"):
        storey_name = rel.RelatingStructure.Name or "Unknown"
        for e in rel.RelatedElements:
            storey_map[e.id()] = storey_name
    for e in elements:
        storey_counts[storey_map.get(e.id(), "Unknown")] += 1

    # Material names
    materials = Counter()
    for rel in f.by_type("IfcRelAssociatesMaterial"):
        mat_name = None
        mat = rel.RelatingMaterial
        if hasattr(mat, "Name") and mat.Name:
            mat_name = mat.Name
        elif hasattr(mat, "ForLayerSet"):
            mat_name = mat.ForLayerSet.LayerSetName if mat.ForLayerSet else None
        if mat_name:
            for e in rel.RelatedObjects:
                if e in elements:
                    materials[mat_name] += 1

    # Geometry sharing (rough: count by element type + material combos)
    type_combos = Counter()
    for e in elements:
        etype = getattr(e, "ObjectType", None) or e.is_a()
        type_combos[etype] += 1

    shared_elements = sum(c for c in type_combos.values() if c > 1)
    total = len(elements)

    return {
        "schema": schema,
        "total": total,
        "class_counts": class_counts,
        "storey_counts": storey_counts,
        "materials": materials,
        "type_combos": type_combos,
        "shared_elements": shared_elements,
    }

# ── Print report for one file ────────────────────────────────

def print_report(path, info, existing_classes, registered_classes):
    fname = Path(path).name
    total = info["total"]
    schema = info["schema"]

    print(f"\n{c(B, '══ ' + fname)}")
    print(f"    Schema:   {schema}")
    print(f"    Elements: {c(B, total)}")

    # Storeys
    storey_str = ", ".join(f"{s} ({n})" for s, n in
                           sorted(info["storey_counts"].items(),
                                  key=lambda x: -x[1]))
    print(f"    Storeys:  {storey_str}")

    # File size
    size_mb = Path(path).stat().st_size / (1024 * 1024)
    print(f"    Size:     {size_mb:.1f} MB")
    print()

    # IFC Class Coverage
    print(f"    {c(B, 'IFC Class Coverage')}")
    new_classes = []
    unregistered = []
    for cls, cnt in sorted(info["class_counts"].items(), key=lambda x: -x[1]):
        in_registry = cls in registered_classes
        in_library = cls in existing_classes

        if not in_registry:
            print(f"      {c(RD, '✗')} {cls:<30s} {cnt:>4d}  {c(RD, 'NOT REGISTERED')}")
            unregistered.append(cls)
        elif not in_library:
            print(f"      {c(G, '✓')} {cls:<30s} {cnt:>4d}  {c(G, 'NEW to library')}")
            new_classes.append(cls)
        else:
            print(f"      {c(G, '✓')} {cls:<30s} {cnt:>4d}")

    print()
    if unregistered:
        print(f"    {c(RD, f'⚠ {len(unregistered)} class(es) NOT REGISTERED — will be skipped!')}")
        print(f"    {c(D, 'Register in ad_ifc_class_map before onboarding')}")
    if new_classes:
        print(f"    {c(G, f'{len(new_classes)} NEW class(es) would enrich the component library:')}")
        for cls in new_classes:
            print(f"      + {cls} ({info['class_counts'][cls]} elements)")
    if not unregistered and not new_classes:
        print(f"    All classes already in library (no new types)")
    print()

    # Factorization potential
    print(f"    {c(B, 'Factorization Potential')}")
    shared = info["shared_elements"]
    pct = (shared / total * 100) if total > 0 else 0
    print(f"      Shared types:  {shared}/{total} elements ({pct:.0f}%) share a type")
    distinct_types = len(info["type_combos"])
    if total > 0 and distinct_types > 0:
        ratio = total / distinct_types
        print(f"      Type diversity: {distinct_types} distinct → {ratio:.1f}x potential reuse")
    print()

    # Validation rule potential
    print(f"    {c(B, 'Validation Rule Potential')}")
    minable = [(cls, cnt) for cls, cnt in info["class_counts"].items() if cnt >= 3]
    minable_elem = sum(cnt for _, cnt in minable)
    print(f"      Minable groups: {c(B, len(minable))}  (classes with ≥3 instances)")
    print(f"      Covered:        {minable_elem}/{total} elements")
    for cls, cnt in sorted(minable, key=lambda x: -x[1])[:8]:
        print(f"        {cls:<30s} {cnt:>4d}")
    if len(minable) > 8:
        print(f"        {c(D, f'... and {len(minable) - 8} more')}")
    print()

    # Materials preview
    if info["materials"]:
        print(f"    {c(B, 'Materials')} ({len(info['materials'])} distinct)")
        for mat, cnt in info["materials"].most_common(6):
            print(f"      {mat:<40s} {cnt:>4d}")
        if len(info["materials"]) > 6:
            extra = len(info["materials"]) - 6
            print(f"      {c(D, f'... and {extra} more')}")
        print()

    return {
        "name": fname,
        "total": total,
        "classes": len(info["class_counts"]),
        "new_classes": len(new_classes),
        "unregistered": len(unregistered),
        "storeys": len(info["storey_counts"]),
        "shared_pct": pct,
        "minable": len(minable),
    }

# ── Main ─────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="IFC Reconnaissance — fast, no extraction")
    parser.add_argument("files", nargs="*", help="IFC files to analyze")
    parser.add_argument("--dir", help="Directory of IFC files")
    args = parser.parse_args()

    files = list(args.files) if args.files else []
    if args.dir:
        d = Path(args.dir)
        files.extend(str(p) for p in sorted(d.glob("*.ifc")))

    if not files:
        parser.print_help()
        return

    # Find project root
    script_dir = Path(__file__).resolve().parent
    project_dir = script_dir.parent

    existing_classes, registered_classes, product_count = load_library_state(project_dir)

    print(f"\n{c(B, '╔══════════════════════════════════════════════════════════════════════╗')}")
    print(f"{c(B, '║              IFC RECONNAISSANCE (fast, no extraction)                ║')}")
    print(f"{c(B, '╠══════════════════════════════════════════════════════════════════════╣')}")
    print(f"{c(B, '║')}  Library: {c(B, product_count)} products, "
          f"{c(B, len(existing_classes))} IFC classes known"
          f"                          {c(B, '║')}")
    print(f"{c(B, '║')}  Registered: {c(B, len(registered_classes))} classes in ad_ifc_class_map"
          f"                       {c(B, '║')}")
    print(f"{c(B, '╚══════════════════════════════════════════════════════════════════════╝')}")

    summaries = []
    for fpath in files:
        if not os.path.exists(fpath):
            print(f"\n  {c(RD, 'Not found:')} {fpath}")
            continue
        try:
            info = analyze_ifc(fpath, existing_classes, registered_classes)
            s = print_report(fpath, info, existing_classes, registered_classes)
            summaries.append(s)
        except Exception as e:
            print(f"\n  {c(RD, 'Error:')} {fpath}: {e}")

    # Summary table
    if len(summaries) > 1:
        print(f"{c(B, '╔═════════════════════════════════════════════════════════════════════════════════╗')}")
        print(f"{c(B, '║  SUMMARY                                                                      ║')}")
        print(f"{c(B, '╠═════════════════════════════════════════════════════════════════════════════════╣')}")
        print(f"{c(B, '║')}  {'File':<40s} {'Elem':>6s}  {'Cls':>3s}  {'New':>3s}  {'St':>3s}  {'Shr%':>4s}  {'Rules':>5s}")
        print(f"{c(B, '║')}  {'─'*40} {'─'*6}  {'─'*3}  {'─'*3}  {'─'*3}  {'─'*4}  {'─'*5}")
        for s in summaries:
            flag = ""
            if s["unregistered"] > 0:
                unreg = s["unregistered"]
                flag = f"  {c(RD, f'⚠{unreg}')}"
            elif s["new_classes"] > 0:
                nnew = s["new_classes"]
                flag = f"  {c(G, f'+{nnew}')}"
            print(f"{c(B, '║')}  {s['name']:<40s} {s['total']:>6d}  {s['classes']:>3d}  "
                  f"{s['new_classes']:>3d}  {s['storeys']:>3d}  {s['shared_pct']:>3.0f}%  "
                  f"{s['minable']:>5d}{flag}")
        total_elem = sum(s["total"] for s in summaries)
        total_new = sum(s["new_classes"] for s in summaries)
        print(f"{c(B, '╠═════════════════════════════════════════════════════════════════════════════════╣')}")
        print(f"{c(B, '║')}  TOTAL: {c(B, len(summaries))} files, "
              f"{c(B, total_elem)} elements, "
              f"{c(G, f'{total_new} new classes')}")
        print(f"{c(B, '╚═════════════════════════════════════════════════════════════════════════════════╝')}")
    print()

if __name__ == "__main__":
    main()
