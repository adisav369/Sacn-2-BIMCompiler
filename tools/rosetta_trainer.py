#!/usr/bin/env python3
"""
Rosetta Trainer — Automated epoch analysis for BIM Compiler convergence.

This tool implements the "training loop" from TheRosettaStoneStrategy.txt:
  1. Run spatial_checker on all Rosetta pairs
  2. Identify MISS elements (reference items not matched by output)
  3. Query library for available components that could match
  4. Generate actionable migration suggestions
  5. Report gap analysis with estimated score impact

Usage:
  python3 tools/rosetta_trainer.py              # Analyze all 3 pairs
  python3 tools/rosetta_trainer.py --pair 1     # SampleHouse only
  python3 tools/rosetta_trainer.py --pair 2     # Duplex only
  python3 tools/rosetta_trainer.py --suggest    # Generate migration SQL suggestions

The Rosetta Stone Strategy = LLM training model:
  Reference IFCs   = training corpus
  AD tables/catalog = weight matrix
  Compiler resolvers = inference engine
  X-ray score       = loss function
  This tool         = training loop automation
"""

import sqlite3
import sys
import os
from collections import defaultdict
from pathlib import Path

# Rosetta Stone pairs
PAIRS = [
    {
        "name": "SampleHouse",
        "tradition": "UK_Residential",
        "output": "output/samplehouse.db",
        "reference": "reference/rosetta/SampleHouse_extracted.db",
    },
    {
        "name": "Duplex",
        "tradition": "US_Residential",
        "output": "output/duplex.db",
        "reference": "reference/rosetta/Duplex_extracted.db",
    },
    {
        "name": "Terminal",
        "tradition": "Malaysian_Institutional",
        "output": "output/terminal.db",
        "reference": "reference/rosetta/Terminal_extracted.db",
    },
]

LIBRARY_PATH = "library/component_library.db"

# IFC class → category mapping (same as spatial_checker.py)
CATEGORY_MAP = {
    "IfcWall": "WALL", "IfcPlate": "WALL", "IfcCurtainWall": "WALL",
    "IfcDoor": "DOOR", "IfcWindow": "WINDOW",
    "IfcSlab": "SLAB", "IfcRoof": "ROOF",
    "IfcFurnishingElement": "FURNITURE", "IfcFurniture": "FURNITURE",
    "IfcColumn": "STRUCTURE", "IfcBeam": "STRUCTURE", "IfcMember": "STRUCTURE",
    "IfcStair": "STAIR", "IfcStairFlight": "STAIR",
    "IfcRailing": "RAILING",
    "IfcPipeSegment": "PIPE", "IfcPipeFitting": "FITTING",
    "IfcFlowTerminal": "TERMINAL", "IfcSanitaryTerminal": "TERMINAL",
}


def get_elements(db_path, discipline=None):
    """Extract elements with bounding box dimensions from a DB."""
    if not os.path.exists(db_path):
        return []

    conn = sqlite3.connect(db_path)
    # Detect table names
    tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()]
    meta_table = "elements_meta" if "elements_meta" in tables else "elements" if "elements" in tables else None
    if not meta_table:
        conn.close()
        return []

    # Check for discipline column
    cols = [r[1] for r in conn.execute(f"PRAGMA table_info({meta_table})").fetchall()]
    has_discipline = "discipline" in cols
    name_col = "element_name" if "element_name" in cols else "name" if "name" in cols else None

    sql = f"""
        SELECT m.ifc_class, {name_col or "'?'"} as ename,
               r.maxX - r.minX as dx, r.maxY - r.minY as dy, r.maxZ - r.minZ as dz
        FROM {meta_table} m
        JOIN elements_rtree r ON m.id = r.id
    """
    if discipline and has_discipline:
        sql += f" WHERE m.discipline = '{discipline}'"

    elements = []
    for row in conn.execute(sql).fetchall():
        cls, name, dx, dy, dz = row
        cat = CATEGORY_MAP.get(cls, cls)
        dims = sorted([abs(dx), abs(dy), abs(dz)], reverse=True)
        sig_10mm = tuple(round(d * 100) * 10 for d in dims)  # 10mm buckets
        elements.append({
            "class": cls, "category": cat, "name": name or "?",
            "dims": dims, "sig": sig_10mm,
            "raw": (dx, dy, dz),
        })
    conn.close()
    return elements


def get_library_components():
    """Load all library component definitions with dimensions."""
    if not os.path.exists(LIBRARY_PATH):
        return {}

    conn = sqlite3.connect(LIBRARY_PATH)
    comps = defaultdict(list)
    for row in conn.execute("""
        SELECT cd.name, ct.ifc_class,
               (cd.local_max_x - cd.local_min_x) as dx,
               (cd.local_max_y - cd.local_min_y) as dy,
               (cd.local_max_z - cd.local_min_z) as dz
        FROM component_definitions cd
        JOIN component_types ct ON cd.type_id = ct.id
    """).fetchall():
        name, cls, dx, dy, dz = row
        cat = CATEGORY_MAP.get(cls, cls)
        dims = sorted([abs(dx), abs(dy), abs(dz)], reverse=True)
        sig = tuple(round(d * 100) * 10 for d in dims)
        comps[cat].append({"name": name, "class": cls, "dims": dims, "sig": sig})
    conn.close()
    return comps


def near_match(sig1, sig2):
    """Check if two signatures are a near-match (thin ±5mm, long ±10%)."""
    if sig1[0] == 0 or sig2[0] == 0:
        return False
    # Thin dimension (smallest)
    if abs(sig1[2] - sig2[2]) > 50:  # 50mm = 5 * 10mm bucket
        return False
    # Long dimensions (±10%)
    for i in range(2):
        if sig1[i] == 0 and sig2[i] == 0:
            continue
        ratio = sig1[i] / max(sig2[i], 1)
        if ratio < 0.9 or ratio > 1.1:
            return False
    return True


def analyze_pair(pair, library_comps, discipline="ARC"):
    """Analyze a single Rosetta pair and identify gaps."""
    print(f"\n{'='*70}")
    print(f"  {pair['name']} ({pair['tradition']}) — Discipline: {discipline}")
    print(f"{'='*70}")

    # Filter for ARC discipline
    disc = discipline if discipline != "ALL" else None
    out_elems = get_elements(pair["output"], disc)
    ref_elems = get_elements(pair["reference"], disc)

    if not ref_elems:
        print(f"  No reference elements found (discipline={discipline})")
        return {}

    # Build signature multisets
    out_sigs = defaultdict(int)
    for e in out_elems:
        out_sigs[e["sig"]] += 1

    ref_sigs = defaultdict(list)
    for e in ref_elems:
        ref_sigs[e["sig"]].append(e)

    # Match analysis
    matched = 0
    near_matched = 0
    misses = []
    extras = []

    out_used = defaultdict(int)
    for sig, ref_list in sorted(ref_sigs.items()):
        available = out_sigs.get(sig, 0) - out_used.get(sig, 0)
        for e in ref_list:
            if available > 0:
                matched += 1
                out_used[sig] += 1
                available -= 1
            else:
                # Try near-match
                found_near = False
                for osig, ocount in out_sigs.items():
                    remaining = ocount - out_used.get(osig, 0)
                    if remaining > 0 and near_match(sig, osig) and e["category"] == CATEGORY_MAP.get(
                        next((oe["class"] for oe in out_elems if oe["sig"] == osig), ""), ""):
                        near_matched += 1
                        out_used[osig] += 1
                        found_near = True
                        break
                if not found_near:
                    misses.append(e)

    total = len(ref_elems)

    print(f"\n  Elements: Output={len(out_elems)}, Reference={len(ref_elems)}")
    print(f"  Exact match: {matched}/{total} ({matched*100//max(total,1)}%)")
    print(f"  Near match:  {matched+near_matched}/{total} ({(matched+near_matched)*100//max(total,1)}%)")
    print(f"  Misses:      {len(misses)}")

    # Group misses by category
    miss_by_cat = defaultdict(list)
    for m in misses:
        miss_by_cat[m["category"]].append(m)

    print(f"\n  --- MISSES BY CATEGORY ---")
    print(f"  {'Category':<15} {'Count':>5} {'Top Missing Signatures'}")
    print(f"  {'-'*15} {'-'*5} {'-'*40}")

    suggestions = []
    for cat in sorted(miss_by_cat.keys()):
        items = miss_by_cat[cat]
        sig_counts = defaultdict(lambda: {"count": 0, "names": []})
        for item in items:
            sig_counts[item["sig"]]["count"] += 1
            sig_counts[item["sig"]]["names"].append(item["name"][:40])

        print(f"  {cat:<15} {len(items):>5}")
        for sig, info in sorted(sig_counts.items(), key=lambda x: -x[1]["count"])[:5]:
            l, w, h = sig
            name_sample = info["names"][0] if info["names"] else "?"

            # Check if library has a matching component
            lib_match = None
            if cat in library_comps:
                for comp in library_comps[cat]:
                    if comp["sig"] == sig:
                        lib_match = comp["name"]
                        break
                    elif near_match(sig, comp["sig"]):
                        lib_match = f"~{comp['name']} ({comp['sig'][0]}x{comp['sig'][1]}x{comp['sig'][2]})"
                        break

            status = "LIBRARY HAS" if lib_match and not lib_match.startswith("~") else \
                     "NEAR IN LIB" if lib_match else "NO LIB MATCH"

            print(f"    {l:>5}x{w:>4}x{h:>4} x{info['count']:>2}  {status:<12} {name_sample[:35]}")

            if lib_match:
                suggestions.append({
                    "pair": pair["name"],
                    "category": cat,
                    "sig": sig,
                    "count": info["count"],
                    "ref_name": name_sample,
                    "lib_component": lib_match,
                })

    return {
        "pair": pair["name"],
        "total": total,
        "exact": matched,
        "near": matched + near_matched,
        "misses": len(misses),
        "suggestions": suggestions,
    }


def generate_suggestions(all_results):
    """Generate actionable migration suggestions."""
    print(f"\n{'='*70}")
    print(f"  MIGRATION SUGGESTIONS (sorted by impact)")
    print(f"{'='*70}")

    all_suggestions = []
    for result in all_results:
        all_suggestions.extend(result.get("suggestions", []))

    # Sort by count (highest impact first)
    all_suggestions.sort(key=lambda x: -x["count"])

    if not all_suggestions:
        print("  No actionable suggestions — all misses lack library components.")
        return

    for i, s in enumerate(all_suggestions[:20], 1):
        l, w, h = s["sig"]
        lib = s["lib_component"]
        if lib.startswith("~"):
            action = "NEAR-MATCH (may need BOM param update)"
        else:
            action = "EXACT MATCH (add to BOM or fix name_pattern)"

        print(f"\n  {i}. [{s['pair']}] {s['category']} {l}x{w}x{h}mm x{s['count']}")
        print(f"     Ref: {s['ref_name'][:50]}")
        print(f"     Lib: {lib}")
        print(f"     Action: {action}")
        print(f"     Impact: +{s['count']} matches → +{s['count']}/{all_results[0]['total'] if all_results else '?'} X-ray")


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Rosetta Trainer — automated convergence analysis")
    parser.add_argument("--pair", type=int, help="Analyze specific pair (1=SampleHouse, 2=Duplex, 3=Terminal)")
    parser.add_argument("--discipline", default="ARC", help="Discipline filter (default: ARC)")
    parser.add_argument("--suggest", action="store_true", help="Generate migration suggestions")
    args = parser.parse_args()

    print("=" * 70)
    print("  ROSETTA TRAINER — Automated Convergence Analysis")
    print("  Strategy: TheRosettaStoneStrategy.txt")
    print("  Target: 100%% X-ray fidelity on ALL stones")
    print("=" * 70)

    # Load library components
    library_comps = get_library_components()
    lib_count = sum(len(v) for v in library_comps.values())
    print(f"\n  Library: {lib_count} components across {len(library_comps)} categories")

    # Analyze pairs
    pairs = [PAIRS[args.pair - 1]] if args.pair else PAIRS
    results = []
    for pair in pairs:
        if not os.path.exists(pair["output"]) or not os.path.exists(pair["reference"]):
            print(f"\n  SKIP {pair['name']}: missing output or reference DB")
            continue
        result = analyze_pair(pair, library_comps, args.discipline)
        results.append(result)

    # Summary
    print(f"\n{'='*70}")
    print(f"  SCORE SUMMARY ({args.discipline} discipline)")
    print(f"{'='*70}")
    print(f"  {'Pair':<15} {'Exact':>7} {'Near':>7} {'Total':>7} {'Score':>7} {'Misses':>7}")
    print(f"  {'-'*15} {'-'*7} {'-'*7} {'-'*7} {'-'*7} {'-'*7}")
    for r in results:
        score = r["near"] * 100 // max(r["total"], 1)
        print(f"  {r['pair']:<15} {r['exact']:>7} {r['near']:>7} {r['total']:>7} {score:>6}% {r['misses']:>7}")

    target_gap = sum(r["misses"] for r in results)
    target_total = sum(r["total"] for r in results)
    print(f"\n  Total gap to 100%%: {target_gap} misses across {target_total} reference elements")

    # Suggestions
    if args.suggest or True:  # Always show suggestions
        generate_suggestions(results)


if __name__ == "__main__":
    main()
