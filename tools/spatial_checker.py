#!/usr/bin/env python3
"""
Spatial Fidelity Checker — Compares compiled output DB against reference IFC DB.
Measures how well the compiler reproduces the spatial makeup of real buildings.

Usage: python3 tools/spatial_checker.py [output_db] [reference_db] [--discipline ARC|MEP|STR]

Defaults:
  output_db:    output/ifc2x3_duplex.db                    (compiled from DSL)
  reference_db: reference/rosetta/Ifc2x3_Duplex_extracted.db (ground truth from IFC)

Discipline filter:
  --discipline ARC  Only architecture elements (walls, doors, windows, furniture, slabs)
  --discipline MEP  Only MEP elements (pipes, fittings, terminals)
  --discipline STR  Only structural elements (beams, columns, members)
  (no flag)         All disciplines combined
"""

import sqlite3
import sys
import os
import math
from collections import defaultdict

TOLERANCE_MM = 5
NEAR_MM = 50

# ── Discipline classification ──────────────────────────────────────────
# Maps IFC classes to disciplines. Used by --discipline filter.
DISCIPLINE_FOR_CLASS = {
    # ARC — Architecture
    "IfcWall": "ARC", "IfcWallStandardCase": "ARC",
    "IfcPlate": "ARC",         # compiler writes walls as IfcPlate
    "IfcDoor": "ARC", "IfcWindow": "ARC",
    "IfcFurniture": "ARC", "IfcFurnishingElement": "ARC",
    "IfcSlab": "ARC", "IfcRoof": "ARC",
    "IfcStairFlight": "ARC", "IfcStair": "ARC",
    "IfcRailing": "ARC",
    "IfcSpace": "ARC",
    # MEP — Mechanical, Electrical, Plumbing
    "IfcFlowSegment": "MEP", "IfcFlowFitting": "MEP", "IfcFlowTerminal": "MEP",
    "IfcPipeSegment": "MEP", "IfcPipeFitting": "MEP",
    "IfcFireSuppressionTerminal": "MEP", "IfcAirTerminal": "MEP",
    "IfcOutlet": "MEP", "IfcSwitchingDevice": "MEP",
    "IfcLightFixture": "MEP", "IfcSanitaryTerminal": "MEP",
    "IfcFan": "MEP", "IfcAlarm": "MEP",
    # STR — Structural
    "IfcBeam": "STR", "IfcColumn": "STR", "IfcMember": "STR",
}

# Active discipline filter (None = all, set by CLI --discipline flag)
ACTIVE_DISCIPLINE = None

def class_allowed(ifc_class):
    """Check if an IFC class passes the active discipline filter."""
    if ACTIVE_DISCIPLINE is None:
        return True
    return DISCIPLINE_FOR_CLASS.get(ifc_class, "ARC") == ACTIVE_DISCIPLINE


def connect(db_path):
    if not os.path.exists(db_path):
        print(f"  ERROR: {db_path} not found")
        sys.exit(1)
    return sqlite3.connect(db_path)


def get_building_envelope(conn):
    """Get overall building extents from rtree (filtered by discipline)."""
    if ACTIVE_DISCIPLINE is not None:
        allowed = [c for c, d in DISCIPLINE_FOR_CLASS.items() if d == ACTIVE_DISCIPLINE]
        placeholders = ",".join("?" * len(allowed))
        row = conn.execute(f"""
            SELECT MIN(r.minX), MAX(r.maxX), MIN(r.minY), MAX(r.maxY), MIN(r.minZ), MAX(r.maxZ)
            FROM elements_rtree r
            JOIN elements_meta m ON m.id = r.id
            WHERE m.ifc_class IN ({placeholders})
        """, allowed).fetchone()
    else:
        row = conn.execute("""
            SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), MIN(minZ), MAX(maxZ)
            FROM elements_rtree
        """).fetchone()
    if row[0] is None:
        return {"minX": 0, "maxX": 0, "minY": 0, "maxY": 0, "minZ": 0, "maxZ": 0,
                "width": 0, "depth": 0, "height": 0}
    return {
        "minX": row[0], "maxX": row[1],
        "minY": row[2], "maxY": row[3],
        "minZ": row[4], "maxZ": row[5],
        "width": row[1] - row[0],
        "depth": row[3] - row[2],
        "height": row[5] - row[4],
    }


def get_storeys(conn):
    """Get storey names and count."""
    rows = conn.execute("""
        SELECT name FROM spatial_structure
        WHERE type = 'IfcBuildingStorey' OR guid LIKE 'STOREY%'
           OR ifc_class = 'IfcBuildingStorey'
    """).fetchall()
    if not rows:
        # Try alternate column names
        try:
            rows = conn.execute("""
                SELECT name FROM spatial_structure WHERE name IS NOT NULL
                AND (type = 'IfcBuildingStorey')
            """).fetchall()
        except:
            pass
    return [r[0] for r in rows] if rows else []


def get_storeys_v2(conn):
    """Get storeys from spatial_structure, handling both schemas."""
    cols = [r[1] for r in conn.execute("PRAGMA table_info(spatial_structure)").fetchall()]
    if "type" in cols:
        type_col = "type"
    elif "ifc_class" in cols:
        type_col = "ifc_class"
    else:
        type_col = None

    if type_col:
        rows = conn.execute(f"""
            SELECT name FROM spatial_structure
            WHERE {type_col} = 'IfcBuildingStorey' AND name IS NOT NULL
        """).fetchall()
    else:
        rows = conn.execute("SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL").fetchall()
    return [r[0] for r in rows]


def get_rooms(conn):
    """Get room types from spatial structure."""
    cols = [r[1] for r in conn.execute("PRAGMA table_info(spatial_structure)").fetchall()]

    if "object_type" in cols:
        # Compiled DB has object_type for room function
        rows = conn.execute("""
            SELECT name, object_type FROM spatial_structure
            WHERE (type = 'IfcSpace' OR guid LIKE 'SPACE%')
            AND name IS NOT NULL
        """).fetchall()
        return [(r[0], r[1]) for r in rows]
    else:
        # Reference DB — room names encode function (e.g., A101)
        try:
            type_col = "type" if "type" in cols else "ifc_class"
            rows = conn.execute(f"""
                SELECT name FROM spatial_structure
                WHERE {type_col} = 'IfcSpace' AND name IS NOT NULL
            """).fetchall()
            return [(r[0], None) for r in rows]
        except:
            return []


def get_element_classes(conn):
    """Get element class distribution (filtered by discipline)."""
    rows = conn.execute("""
        SELECT ifc_class, COUNT(*) FROM elements_meta
        GROUP BY ifc_class ORDER BY COUNT(*) DESC
    """).fetchall()
    if ACTIVE_DISCIPLINE is not None:
        rows = [(c, n) for c, n in rows if class_allowed(c)]
    return dict(rows)


def get_element_dims(conn, ifc_class):
    """Get element dimensions for a given IFC class (respects discipline filter)."""
    if not class_allowed(ifc_class):
        return []
    rows = conn.execute("""
        SELECT m.element_name,
               r.maxX - r.minX AS dx,
               r.maxY - r.minY AS dy,
               r.maxZ - r.minZ AS dz
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.ifc_class = ?
    """, (ifc_class,)).fetchall()
    return rows


def classify_wall_thickness(thickness_mm):
    """Classify a wall by its thickness into grammar categories."""
    if thickness_mm < 80:
        return "FURRING"
    elif thickness_mm < 140:
        return "PARTITION"
    elif thickness_mm < 200:
        return "INTERIOR_THICK"
    elif thickness_mm < 350:
        return "PARTY/EXTERNAL"
    elif thickness_mm < 500:
        return "EXTERIOR_BRICK"
    else:
        return "HEAVY"


def compare_envelopes(out_env, ref_env):
    """Compare building envelopes."""
    print("\n" + "=" * 70)
    print("1. BUILDING ENVELOPE")
    print("=" * 70)

    print(f"\n  {'Dimension':<15} {'Output':>12} {'Reference':>12} {'Ratio':>8}")
    print(f"  {'-'*15} {'-'*12} {'-'*12} {'-'*8}")

    dims = [
        ("Width (X)", out_env["width"], ref_env["width"]),
        ("Depth (Y)", out_env["depth"], ref_env["depth"]),
        ("Height (Z)", out_env["height"], ref_env["height"]),
    ]

    for label, out_v, ref_v in dims:
        ratio = out_v / ref_v if ref_v > 0 else 0
        print(f"  {label:<15} {out_v:>10.2f}m {ref_v:>10.2f}m {ratio:>7.2f}x")

    out_fp = out_env["width"] * out_env["depth"]
    ref_fp = ref_env["width"] * ref_env["depth"]
    ratio = out_fp / ref_fp if ref_fp > 0 else 0
    print(f"  {'Footprint':<15} {out_fp:>9.1f}m\u00b2 {ref_fp:>9.1f}m\u00b2 {ratio:>7.2f}x")

    out_vol = out_fp * out_env["height"]
    ref_vol = ref_fp * ref_env["height"]
    ratio = out_vol / ref_vol if ref_vol > 0 else 0
    print(f"  {'Volume':<15} {out_vol:>9.1f}m\u00b3 {ref_vol:>9.1f}m\u00b3 {ratio:>7.2f}x")


def compare_spatial_structure(out_conn, ref_conn):
    """Compare storey/room structure."""
    print("\n" + "=" * 70)
    print("2. SPATIAL STRUCTURE")
    print("=" * 70)

    out_storeys = get_storeys_v2(out_conn)
    ref_storeys = get_storeys_v2(ref_conn)

    print(f"\n  Storeys: Output={len(out_storeys)}  Reference={len(ref_storeys)}")
    print(f"  Output:    {', '.join(out_storeys)}")
    print(f"  Reference: {', '.join(ref_storeys)}")

    out_rooms = get_rooms(out_conn)
    ref_rooms = get_rooms(ref_conn)

    print(f"\n  Rooms: Output={len(out_rooms)}  Reference={len(ref_rooms)}")

    # Count room types in output
    out_room_types = defaultdict(int)
    for name, rtype in out_rooms:
        if rtype:
            out_room_types[rtype] += 1
        else:
            out_room_types["UNKNOWN"] += 1

    # Reference rooms are coded (A101, B102, etc.) — we know the layout from docs
    ref_room_functions = {
        "LIVING": 4, "KITCHEN": 4, "BEDROOM": 4, "MASTER_BEDROOM": 0,
        "BATHROOM": 4, "DINING": 4, "STAIR": 2, "CORRIDOR": 2, "ROOF": 1,
    }

    print(f"\n  {'Room Type':<20} {'Output':>6} {'Reference':>9}")
    print(f"  {'-'*20} {'-'*6} {'-'*9}")

    all_types = set(out_room_types.keys()) | set(ref_room_functions.keys())
    match_count = 0
    total_types = 0
    for rt in sorted(all_types):
        out_c = out_room_types.get(rt, 0)
        ref_c = ref_room_functions.get(rt, 0)
        marker = " =" if out_c == ref_c else ""
        print(f"  {rt:<20} {out_c:>6} {ref_c:>9}{marker}")
        if out_c > 0 and ref_c > 0:
            match_count += min(out_c, ref_c)
        total_types += 1

    return len(out_rooms), len(ref_rooms)


def compare_element_classes(out_conn, ref_conn):
    """Compare element class distributions."""
    print("\n" + "=" * 70)
    print("3. ELEMENT CLASS DISTRIBUTION")
    print("=" * 70)

    out_classes = get_element_classes(out_conn)
    ref_classes = get_element_classes(ref_conn)

    # Map equivalent classes between compiled and reference
    CLASS_MAP = {
        # compiled → reference equivalent
        "IfcMember": "IfcWall",      # studs represent walls
        "IfcPlate": "IfcWall",       # cladding represents walls
        "IfcPipeSegment": "IfcFlowSegment",
        "IfcPipeFitting": "IfcFlowFitting",
        "IfcFireSuppressionTerminal": "IfcFlowTerminal",
        "IfcAirTerminal": "IfcFlowTerminal",
        "IfcOutlet": "IfcFlowTerminal",
        "IfcSwitchingDevice": "IfcFlowTerminal",
        "IfcLightFixture": "IfcFlowTerminal",
        "IfcSanitaryTerminal": "IfcFlowTerminal",
        "IfcFan": "IfcFlowTerminal",
        "IfcFurniture": "IfcFurnishingElement",
    }

    # Aggregate to comparable categories
    out_agg = defaultdict(int)
    for cls, cnt in out_classes.items():
        mapped = CLASS_MAP.get(cls, cls)
        out_agg[mapped] += cnt

    ref_agg = defaultdict(int)
    for cls, cnt in ref_classes.items():
        ref_agg[cls] += cnt

    all_cats = sorted(set(out_agg.keys()) | set(ref_agg.keys()))

    out_total = sum(out_classes.values())
    ref_total = sum(ref_classes.values())

    print(f"\n  Total elements: Output={out_total}  Reference={ref_total}")
    print(f"\n  {'Category':<25} {'Output':>6} {'Out%':>5} {'Reference':>9} {'Ref%':>5} {'Ratio':>7}")
    print(f"  {'-'*25} {'-'*6} {'-'*5} {'-'*9} {'-'*5} {'-'*7}")

    for cat in all_cats:
        oc = out_agg.get(cat, 0)
        rc = ref_agg.get(cat, 0)
        op = oc * 100 / out_total if out_total else 0
        rp = rc * 100 / ref_total if ref_total else 0
        ratio = f"{oc/rc:.2f}x" if rc > 0 else ("---" if oc == 0 else "NEW")
        print(f"  {cat:<25} {oc:>6} {op:>4.1f}% {rc:>9} {rp:>4.1f}% {ratio:>7}")

    return out_total, ref_total


def compare_wall_thicknesses(out_conn, ref_conn):
    """Compare wall thickness distributions."""
    print("\n" + "=" * 70)
    print("4. WALL THICKNESS COMPARISON")
    print("=" * 70)

    # Reference: IfcWall elements
    ref_walls = get_element_dims(ref_conn, "IfcWall")
    ref_thick = defaultdict(int)
    for name, dx, dy, dz in ref_walls:
        t_mm = min(dx, dy) * 1000
        bucket = round(t_mm)
        ref_thick[bucket] += 1

    # Output: wall thickness from IfcPlate (cladding) — the thin dimension
    out_walls = get_element_dims(out_conn, "IfcPlate")
    out_thick = defaultdict(int)
    for name, dx, dy, dz in out_walls:
        t_mm = min(dx, dy) * 1000
        # Skip very thin items (cladding sheets < 10mm aren't wall thickness)
        if t_mm > 10:
            bucket = round(t_mm)
            out_thick[bucket] += 1

    # Also check IfcMember studs
    out_studs = get_element_dims(out_conn, "IfcMember")
    stud_thick = defaultdict(int)
    for name, dx, dy, dz in out_studs:
        t_mm = min(dx, dy) * 1000
        if t_mm > 10:
            stud_thick[round(t_mm)] += 1

    all_buckets = sorted(set(ref_thick.keys()) | set(out_thick.keys()) | set(stud_thick.keys()))

    print(f"\n  {'Thickness (mm)':<18} {'Ref Walls':>9} {'Out Plates':>10} {'Out Studs':>10} {'Category':>18}")
    print(f"  {'-'*18} {'-'*9} {'-'*10} {'-'*10} {'-'*18}")

    exact = total = 0
    for b in all_buckets:
        rc = ref_thick.get(b, 0)
        oc = out_thick.get(b, 0)
        sc = stud_thick.get(b, 0)
        cat = classify_wall_thickness(b)
        marker = " <--MATCH" if rc > 0 and (oc > 0 or sc > 0) else ""
        print(f"  {b:>14}mm {rc:>9} {oc:>10} {sc:>10} {cat:>18}{marker}")
        if rc > 0:
            total += rc
            if oc > 0 or sc > 0:
                exact += rc

    print(f"\n  Wall thickness match: {exact}/{total} ref types have compiled equivalents")
    return exact, total


def compare_opening_sizes(out_conn, ref_conn):
    """Compare door/window dimensions."""
    print("\n" + "=" * 70)
    print("5. OPENING SIZE COMPARISON")
    print("=" * 70)

    exact = near = gap = total = 0

    for label, ref_class, out_class in [("DOORS", "IfcDoor", "IfcDoor"),
                                         ("WINDOWS", "IfcWindow", "IfcWindow")]:
        print(f"\n  --- {label} ---")

        ref_items = get_element_dims(ref_conn, ref_class)
        out_items = get_element_dims(out_conn, out_class)

        # Group by size bucket (round to 10mm)
        def size_key(dx, dy, dz):
            w = max(dx, dy)
            h = dz
            return (round(w * 100) * 10, round(h * 100) * 10)  # mm, rounded to 10mm

        ref_sizes = defaultdict(int)
        for name, dx, dy, dz in ref_items:
            k = size_key(dx, dy, dz)
            ref_sizes[k] += 1

        out_sizes = defaultdict(int)
        for name, dx, dy, dz in out_items:
            k = size_key(dx, dy, dz)
            out_sizes[k] += 1

        all_sizes = sorted(set(ref_sizes.keys()) | set(out_sizes.keys()))

        print(f"  {'Size (WxH mm)':<25} {'Reference':>9} {'Output':>9} {'Match':>8}")
        print(f"  {'-'*25} {'-'*9} {'-'*9} {'-'*8}")

        for sz in all_sizes:
            rc = ref_sizes.get(sz, 0)
            oc = out_sizes.get(sz, 0)
            total += rc
            if rc > 0 and oc > 0:
                matched = min(rc, oc)
                exact += matched
                status = "EXACT" if rc == oc else f"{matched}/{rc}"
            elif rc > 0:
                status = "MISS"
                gap += rc
            else:
                status = "EXTRA"
            print(f"  {sz[0]:>10} x {sz[1]:<10} {rc:>9} {oc:>9} {status:>8}")

    print(f"\n  Opening size match: {exact}/{total} reference openings matched ({exact*100//total if total else 0}%)")
    return exact, total


def compare_furniture_dims(out_conn, ref_conn):
    """Compare furniture bounding box dimensions."""
    print("\n" + "=" * 70)
    print("6. FURNITURE DIMENSION COMPARISON")
    print("=" * 70)

    ref_items = get_element_dims(ref_conn, "IfcFurnishingElement")
    out_items = get_element_dims(out_conn, "IfcFurniture")

    # Group by dimension signature (sorted dims, rounded to 50mm)
    def dim_sig(dx, dy, dz):
        dims = sorted([dx, dy, dz], reverse=True)
        return tuple(round(d * 20) * 50 for d in dims)  # round to 50mm

    ref_sigs = defaultdict(lambda: {"count": 0, "names": set()})
    for name, dx, dy, dz in ref_items:
        sig = dim_sig(dx, dy, dz)
        ref_sigs[sig]["count"] += 1
        short = name.split(":")[0] if ":" in name else name
        ref_sigs[sig]["names"].add(short)

    out_sigs = defaultdict(lambda: {"count": 0, "names": set()})
    for name, dx, dy, dz in out_items:
        sig = dim_sig(dx, dy, dz)
        out_sigs[sig]["count"] += 1
        out_sigs[sig]["names"].add(name)

    all_sigs = sorted(set(ref_sigs.keys()) | set(out_sigs.keys()))

    print(f"\n  Reference: {len(ref_items)} items, {len(ref_sigs)} size groups")
    print(f"  Output:    {len(out_items)} items, {len(out_sigs)} size groups")

    print(f"\n  {'Dimensions (mm LxWxH)':<25} {'Ref':>4} {'Out':>4} {'Match':>6} {'Ref Names':>30}")
    print(f"  {'-'*25} {'-'*4} {'-'*4} {'-'*6} {'-'*30}")

    exact = total = 0
    for sig in all_sigs:
        rc = ref_sigs[sig]["count"] if sig in ref_sigs else 0
        oc = out_sigs[sig]["count"] if sig in out_sigs else 0
        rn = ", ".join(sorted(ref_sigs[sig]["names"])) if sig in ref_sigs else ""
        on = ", ".join(sorted(out_sigs[sig]["names"])) if sig in out_sigs else ""

        if rc > 0:
            total += rc
            if oc > 0:
                exact += min(rc, oc)
                status = "YES"
            else:
                status = "MISS"
        else:
            status = "EXTRA"

        sig_str = f"{sig[0]:>5}x{sig[1]:>4}x{sig[2]:<4}"
        names = rn[:30] if rn else on[:30]
        print(f"  {sig_str:<25} {rc:>4} {oc:>4} {status:>6} {names:>30}")

    pct = exact * 100 // total if total else 0
    print(f"\n  Furniture match: {exact}/{total} reference items matched by size ({pct}%)")
    return exact, total


def compare_slab_roof(out_conn, ref_conn):
    """Compare slab and roof dimensions."""
    print("\n" + "=" * 70)
    print("7. SLAB & ROOF COMPARISON")
    print("=" * 70)

    exact = total = 0

    for label, ref_class, out_class in [
        ("SLABS", "IfcSlab", "IfcSlab"),
        ("ROOFS", "IfcRoof", "IfcRoof"),
    ]:
        ref_items = get_element_dims(ref_conn, ref_class)
        out_items = get_element_dims(out_conn, out_class)
        if not ref_items and not out_items:
            continue

        print(f"\n  --- {label} ---")

        def dim_sig(dx, dy, dz):
            dims = sorted([dx, dy, dz], reverse=True)
            return tuple(round(d * 100) * 10 for d in dims)  # round to 10mm

        ref_sigs = defaultdict(lambda: {"count": 0, "names": []})
        for name, dx, dy, dz in ref_items:
            sig = dim_sig(dx, dy, dz)
            ref_sigs[sig]["count"] += 1
            short = name.split(":")[0] if ":" in name else (name or "?")
            ref_sigs[sig]["names"].append(short)

        out_sigs = defaultdict(lambda: {"count": 0, "names": []})
        for name, dx, dy, dz in out_items:
            sig = dim_sig(dx, dy, dz)
            out_sigs[sig]["count"] += 1
            out_sigs[sig]["names"].append(name or "?")

        all_sigs = sorted(set(ref_sigs.keys()) | set(out_sigs.keys()))

        print(f"  Reference: {len(ref_items)} items  Output: {len(out_items)} items")
        print(f"  {'Dimensions (mm LxWxH)':<28} {'Ref':>4} {'Out':>4} {'Match':>6} {'Names':>30}")
        print(f"  {'-'*28} {'-'*4} {'-'*4} {'-'*6} {'-'*30}")

        for sig in all_sigs:
            rc = ref_sigs[sig]["count"] if sig in ref_sigs else 0
            oc = out_sigs[sig]["count"] if sig in out_sigs else 0
            rn = ", ".join(ref_sigs[sig]["names"][:2]) if sig in ref_sigs else ""
            on = ", ".join(out_sigs[sig]["names"][:2]) if sig in out_sigs else ""

            if rc > 0:
                total += rc
                if oc > 0:
                    exact += min(rc, oc)
                    status = "YES"
                else:
                    status = "MISS"
            else:
                status = "EXTRA"

            sig_str = f"{sig[0]:>6}x{sig[1]:>5}x{sig[2]:<5}"
            names = rn[:30] if rn else on[:30]
            print(f"  {sig_str:<28} {rc:>4} {oc:>4} {status:>6} {names:>30}")

    pct = exact * 100 // total if total else 0
    print(f"\n  Slab/Roof match: {exact}/{total} reference items matched ({pct}%)")
    return exact, total


def compare_storey_heights(out_conn, ref_conn):
    """Compare storey-to-storey heights."""
    print("\n" + "=" * 70)
    print("8. STOREY HEIGHT ANALYSIS")
    print("=" * 70)

    for label, conn in [("Reference", ref_conn), ("Output", out_conn)]:
        storeys = get_storeys_v2(conn)
        print(f"\n  {label}:")
        # Get Z range of elements per storey
        for st in storeys:
            row = conn.execute("""
                SELECT MIN(r.minZ), MAX(r.maxZ), COUNT(*)
                FROM elements_meta m
                JOIN elements_rtree r ON m.id = r.id
                WHERE m.storey = ?
            """, (st,)).fetchone()
            if row and row[2] > 0:
                h = row[1] - row[0]
                print(f"    {st:<20} Z: {row[0]:>6.2f} to {row[1]:>6.2f}  height={h:.2f}m  ({row[2]} elements)")
            else:
                print(f"    {st:<20} (no elements matched)")


def spatial_xray(out_conn, ref_conn):
    """Spatial X-ray: compare dimension signature fingerprints.

    Every element gets a signature: (ifc_category, dim_bucket_L, dim_bucket_W, dim_bucket_H).
    We compare the multiset of signatures between output and reference.
    This ignores names, positions, orientations — purely spatial bone structure.
    """
    print("\n" + "=" * 70)
    print("9. SPATIAL X-RAY — Dimension Signature Fingerprint")
    print("=" * 70)

    # Map IFC classes to broad categories for cross-schema comparison
    # Key insight: compiler stores walls as IfcPlate (cladding) + IfcMember (studs)
    # Reference IFC uses IfcWall. Both must map to WALL for spatial matching.
    CATEGORY_MAP = {
        # Compiled wall representation → WALL (same as reference IfcWall)
        "IfcPlate": "WALL",        # cladding panels = wall surfaces
        "IfcMember": "FRAME",      # studs = internal structural members
        # Reference wall representation
        "IfcWall": "WALL",
        # Structural
        "IfcBeam": "BEAM", "IfcColumn": "COLUMN",
        "IfcSlab": "SLAB", "IfcRoof": "ROOF",
        # Openings
        "IfcDoor": "DOOR", "IfcWindow": "WINDOW",
        # Furniture (both schemas)
        "IfcFurniture": "FURNITURE", "IfcFurnishingElement": "FURNITURE",
        # Circulation
        "IfcStairFlight": "STAIR", "IfcStair": "STAIR",
        "IfcRailing": "RAILING",
        # MEP — reference classes
        "IfcFlowSegment": "PIPE", "IfcFlowFitting": "FITTING",
        "IfcFlowTerminal": "TERMINAL",
        # MEP — compiled classes
        "IfcPipeSegment": "PIPE", "IfcPipeFitting": "FITTING",
        "IfcFireSuppressionTerminal": "TERMINAL", "IfcAirTerminal": "TERMINAL",
        "IfcOutlet": "TERMINAL", "IfcSwitchingDevice": "TERMINAL",
        "IfcLightFixture": "TERMINAL", "IfcSanitaryTerminal": "TERMINAL",
        "IfcFan": "TERMINAL", "IfcAlarm": "TERMINAL",
    }

    def get_raw_elements(conn):
        """Get raw element dimensions with category (filtered by discipline)."""
        rows = conn.execute("""
            SELECT m.ifc_class,
                   r.maxX - r.minX AS dx,
                   r.maxY - r.minY AS dy,
                   r.maxZ - r.minZ AS dz
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
        """).fetchall()

        elements = []
        for cls, dx, dy, dz in rows:
            if not class_allowed(cls):
                continue
            cat = CATEGORY_MAP.get(cls, cls)
            dims = sorted([dx, dy, dz], reverse=True)
            # Exact signature: 10mm buckets
            exact_sig = (cat,
                         round(dims[0] * 100) * 10,
                         round(dims[1] * 100) * 10,
                         round(dims[2] * 100) * 10)
            elements.append((cat, dims, exact_sig))
        return elements

    out_elems = get_raw_elements(out_conn)
    ref_elems = get_raw_elements(ref_conn)

    # Build exact signature counts
    out_sigs = defaultdict(int)
    for cat, dims, sig in out_elems:
        out_sigs[sig] += 1

    ref_sigs = defaultdict(int)
    for cat, dims, sig in ref_elems:
        ref_sigs[sig] += 1

    # Near-match: for each ref element, find best output match within tolerance
    # Tolerance: same category + thin dim within 5mm + long dims within 10%
    def dims_near(ref_dims, out_dims, tol_pct=0.10, tol_thin_mm=5):
        """Check if two sorted dim triples are near-matches."""
        # Thin dim (smallest) must be within tol_thin_mm
        if abs(ref_dims[2] - out_dims[2]) > tol_thin_mm / 1000.0:
            return False
        # Other dims within tol_pct
        for r, o in zip(ref_dims[:2], out_dims[:2]):
            if r == 0:
                continue
            if abs(r - o) / max(r, 0.001) > tol_pct:
                return False
        return True

    # Group output elements by category for faster lookup
    out_by_cat = defaultdict(list)
    for cat, dims, sig in out_elems:
        out_by_cat[cat].append(dims)

    # Count near-matches per category
    by_cat = defaultdict(lambda: {"out": 0, "ref": 0, "exact": 0, "near": 0,
                                   "sigs_out": 0, "sigs_ref": 0, "sigs_shared": 0})

    # Exact match counting
    all_sigs = set(out_sigs.keys()) | set(ref_sigs.keys())
    for sig in all_sigs:
        cat = sig[0]
        oc = out_sigs.get(sig, 0)
        rc = ref_sigs.get(sig, 0)
        by_cat[cat]["out"] += oc
        by_cat[cat]["ref"] += rc
        by_cat[cat]["exact"] += min(oc, rc)
        if oc > 0: by_cat[cat]["sigs_out"] += 1
        if rc > 0: by_cat[cat]["sigs_ref"] += 1
        if oc > 0 and rc > 0: by_cat[cat]["sigs_shared"] += 1

    # Near-match counting (greedy: each ref element matched at most once)
    for cat in set(c for c, _, _ in ref_elems):
        ref_cat = [d for c, d, _ in ref_elems if c == cat]
        out_cat = list(out_by_cat.get(cat, []))
        used = [False] * len(out_cat)
        near_count = 0
        for rd in ref_cat:
            for j, od in enumerate(out_cat):
                if used[j]:
                    continue
                if dims_near(rd, od):
                    near_count += 1
                    used[j] = True
                    break
        by_cat[cat]["near"] = near_count

    print(f"\n  Output:    {len(out_elems)} elements, {len(out_sigs)} unique signatures")
    print(f"  Reference: {len(ref_elems)} elements, {len(ref_sigs)} unique signatures")
    shared_sigs = len(set(out_sigs.keys()) & set(ref_sigs.keys()))
    print(f"  Shared signatures: {shared_sigs}")

    print(f"\n  {'Category':<12} {'Out':>5} {'Ref':>5} {'Exact':>6} {'Near':>6} {'Out Sg':>6} {'Ref Sg':>6} {'Shared':>6}")
    print(f"  {'-'*12} {'-'*5} {'-'*5} {'-'*6} {'-'*6} {'-'*6} {'-'*6} {'-'*6}")

    total_exact = total_near = total_ref = 0
    for cat in sorted(by_cat.keys()):
        d = by_cat[cat]
        print(f"  {cat:<12} {d['out']:>5} {d['ref']:>5} {d['exact']:>6} {d['near']:>6} {d['sigs_out']:>6} {d['sigs_ref']:>6} {d['sigs_shared']:>6}")
        total_exact += d["exact"]
        total_near += d["near"]
        total_ref += d["ref"]

    # Top shared signatures (highest count)
    shared = {s: (out_sigs.get(s, 0), ref_sigs.get(s, 0))
              for s in set(out_sigs.keys()) & set(ref_sigs.keys())}
    if shared:
        print(f"\n  Top shared signatures (same spatial bone structure):")
        print(f"  {'Category':<12} {'L mm':>6} {'W mm':>6} {'H mm':>6} {'Out':>5} {'Ref':>5}")
        print(f"  {'-'*12} {'-'*6} {'-'*6} {'-'*6} {'-'*5} {'-'*5}")
        for sig, (oc, rc) in sorted(shared.items(), key=lambda x: min(x[1]), reverse=True)[:15]:
            print(f"  {sig[0]:<12} {sig[1]:>6} {sig[2]:>6} {sig[3]:>6} {oc:>5} {rc:>5}")

    exact_pct = total_exact * 100 // total_ref if total_ref > 0 else 0
    near_pct = total_near * 100 // total_ref if total_ref > 0 else 0
    print(f"\n  X-ray exact: {total_exact}/{total_ref} ({exact_pct}%)  near: {total_near}/{total_ref} ({near_pct}%)")
    return total_near, total_ref


def dictionary_breakdown(out_conn, ref_conn):
    """Use element_dictionary (if present) to report per-discipline X-ray scores.

    Discipline mapping: WALL/DOOR/WINDOW/FURNITURE/SLAB/ROOF/STAIR/RAILING/PANEL → ARC
                       PIPE/FITTING/TERMINAL/DUCT/CONDUIT → MEP
                       BEAM/COLUMN/MEMBER/FRAME → STR
    """
    # Check if dictionary table exists in reference DB
    tables = [r[0] for r in ref_conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'").fetchall()]
    if "element_dictionary" not in tables:
        return (0, 0), (0, 0), (0, 0)

    print("\n" + "=" * 70)
    print("10. DICTIONARY — Per-Discipline & Per-Type Breakdown")
    print("=" * 70)

    DISCIPLINE_MAP = {
        "WALL": "ARC", "DOOR": "ARC", "WINDOW": "ARC",
        "FURNITURE": "ARC", "SLAB": "ARC", "ROOF": "ARC",
        "STAIR": "ARC", "RAILING": "ARC", "PANEL": "ARC",
        "PIPE": "MEP", "FITTING": "MEP", "TERMINAL": "MEP",
        "DUCT": "MEP", "CONDUIT": "MEP",
        "BEAM": "STR", "COLUMN": "STR", "MEMBER": "STR", "FRAME": "STR",
    }

    # Load dictionary entries
    dict_rows = ref_conn.execute("""
        SELECT compiler_category, compiler_subtype, instance_count
        FROM element_dictionary
    """).fetchall()

    # The X-ray CATEGORY_MAP (same as in spatial_xray)
    CATEGORY_MAP = {
        "IfcPlate": "WALL", "IfcMember": "FRAME",
        "IfcWall": "WALL", "IfcWallStandardCase": "WALL",
        "IfcBeam": "BEAM", "IfcColumn": "COLUMN",
        "IfcSlab": "SLAB", "IfcRoof": "ROOF",
        "IfcDoor": "DOOR", "IfcWindow": "WINDOW",
        "IfcFurniture": "FURNITURE", "IfcFurnishingElement": "FURNITURE",
        "IfcStairFlight": "STAIR", "IfcStair": "STAIR",
        "IfcRailing": "RAILING",
        "IfcFlowSegment": "PIPE", "IfcFlowFitting": "FITTING",
        "IfcFlowTerminal": "TERMINAL",
        "IfcPipeSegment": "PIPE", "IfcPipeFitting": "FITTING",
        "IfcFireSuppressionTerminal": "TERMINAL", "IfcAirTerminal": "TERMINAL",
        "IfcOutlet": "TERMINAL", "IfcSwitchingDevice": "TERMINAL",
        "IfcLightFixture": "TERMINAL", "IfcSanitaryTerminal": "TERMINAL",
        "IfcFan": "TERMINAL", "IfcAlarm": "TERMINAL",
    }

    def get_dim_elems(conn):
        """Get (category, sorted_dims) for each element (filtered by discipline)."""
        rows = conn.execute("""
            SELECT m.ifc_class, r.maxX - r.minX, r.maxY - r.minY, r.maxZ - r.minZ
            FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
        """).fetchall()
        result = []
        for cls, dx, dy, dz in rows:
            if not class_allowed(cls):
                continue
            cat = CATEGORY_MAP.get(cls, cls)
            dims = sorted([dx, dy, dz], reverse=True)
            result.append((cat, dims))
        return result

    out_elems = get_dim_elems(out_conn)
    ref_elems = get_dim_elems(ref_conn)

    def dims_near(r, o, tol_pct=0.10, tol_thin_mm=5):
        if abs(r[2] - o[2]) > tol_thin_mm / 1000.0:
            return False
        for rv, ov in zip(r[:2], o[:2]):
            if rv == 0: continue
            if abs(rv - ov) / max(rv, 0.001) > tol_pct:
                return False
        return True

    # Group by category
    out_by_cat = defaultdict(list)
    for cat, dims in out_elems:
        out_by_cat[cat].append(dims)

    ref_by_cat = defaultdict(list)
    for cat, dims in ref_elems:
        ref_by_cat[cat].append(dims)

    # Near-match per category
    cat_near = {}
    for cat in ref_by_cat:
        ref_list = ref_by_cat[cat]
        out_list = list(out_by_cat.get(cat, []))
        used = [False] * len(out_list)
        matched = 0
        for rd in ref_list:
            for j, od in enumerate(out_list):
                if used[j]: continue
                if dims_near(rd, od):
                    matched += 1
                    used[j] = True
                    break
        cat_near[cat] = matched

    # Aggregate by discipline
    disc_totals = {"ARC": [0, 0], "MEP": [0, 0], "STR": [0, 0]}
    cat_totals = {}
    for cat in sorted(set(list(ref_by_cat.keys()) + list(out_by_cat.keys()))):
        ref_n = len(ref_by_cat.get(cat, []))
        near_n = cat_near.get(cat, 0)
        disc = DISCIPLINE_MAP.get(cat, "ARC")
        disc_totals[disc][0] += near_n
        disc_totals[disc][1] += ref_n
        cat_totals[cat] = (near_n, ref_n, disc)

    # Print per-type breakdown
    print(f"\n  {'Category':<14} {'Disc':<5} {'Near':>5} {'/ Ref':>6} {'Score':>6}")
    print(f"  {'-'*14} {'-'*5} {'-'*5} {'-'*6} {'-'*6}")
    for cat in sorted(cat_totals.keys()):
        near_n, ref_n, disc = cat_totals[cat]
        if ref_n == 0 and near_n == 0:
            continue
        pct = near_n * 100 // ref_n if ref_n > 0 else 0
        marker = " *" if ref_n > 0 and near_n == 0 else ""
        print(f"  {cat:<14} {disc:<5} {near_n:>5} {ref_n:>6} {pct:>5}%{marker}")

    print(f"\n  Per-discipline totals:")
    for disc in ["ARC", "MEP", "STR"]:
        m, t = disc_totals[disc]
        pct = m * 100 // t if t > 0 else 0
        print(f"    {disc:<5} {m:>5}/{t:<5} ({pct}%)")

    return tuple(disc_totals["ARC"]), tuple(disc_totals["MEP"]), tuple(disc_totals["STR"])


def main():
    global ACTIVE_DISCIPLINE

    # Parse arguments: positional [output_db] [reference_db] and --discipline flag
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    output_db = args[0] if len(args) > 0 else "output/ifc2x3_duplex.db"
    reference_db = args[1] if len(args) > 1 else "reference/rosetta/Ifc2x3_Duplex_extracted.db"

    # Parse --discipline flag
    for i, a in enumerate(sys.argv[1:], 1):
        if a == "--discipline" and i + 1 < len(sys.argv):
            disc = sys.argv[i + 1].upper()
            if disc in ("ARC", "MEP", "STR"):
                ACTIVE_DISCIPLINE = disc
            else:
                print(f"  ERROR: Unknown discipline '{disc}'. Use ARC, MEP, or STR.")
                return 1

    disc_label = f"  Discipline: {ACTIVE_DISCIPLINE}" if ACTIVE_DISCIPLINE else ""

    print("=" * 70)
    print("  SPATIAL FIDELITY CHECKER")
    print(f"  Output:    {output_db}")
    print(f"  Reference: {reference_db}")
    if disc_label:
        print(disc_label)
    print("=" * 70)

    out_conn = connect(output_db)
    ref_conn = connect(reference_db)

    # 1. Building envelope
    out_env = get_building_envelope(out_conn)
    ref_env = get_building_envelope(ref_conn)
    compare_envelopes(out_env, ref_env)

    # 2. Spatial structure (always shown — storeys/rooms are universal)
    compare_spatial_structure(out_conn, ref_conn)

    # 3. Element class distribution
    out_total, ref_total = compare_element_classes(out_conn, ref_conn)

    # 4-7. Category-specific sections — only for ARC or unfiltered
    wall_match = wall_total = 0
    opening_match = opening_total = 0
    furn_match = furn_total = 0
    slab_match = slab_total = 0

    if ACTIVE_DISCIPLINE in (None, "ARC"):
        wall_match, wall_total = compare_wall_thicknesses(out_conn, ref_conn)
        opening_match, opening_total = compare_opening_sizes(out_conn, ref_conn)
        furn_match, furn_total = compare_furniture_dims(out_conn, ref_conn)
        slab_match, slab_total = compare_slab_roof(out_conn, ref_conn)

    # 8. Storey heights
    compare_storey_heights(out_conn, ref_conn)

    # 9. Spatial X-ray — dimension signature fingerprint
    xray_match, xray_total = spatial_xray(out_conn, ref_conn)

    # 10. Dictionary-powered per-discipline breakdown (skip if already filtered)
    if ACTIVE_DISCIPLINE is None:
        dict_arc, dict_mep, dict_str = dictionary_breakdown(out_conn, ref_conn)
    else:
        dict_arc = dict_mep = dict_str = (0, 0)

    # Summary
    print("\n" + "=" * 70)
    disc_suffix = f" ({ACTIVE_DISCIPLINE} ONLY)" if ACTIVE_DISCIPLINE else ""
    print(f"  SPATIAL FIDELITY SUMMARY{disc_suffix}")
    print("=" * 70)

    checks = [
        ("X-ray (near-match)", xray_match, xray_total),
    ]
    if ACTIVE_DISCIPLINE in (None, "ARC"):
        checks = [
            ("Wall thickness types", wall_match, wall_total),
            ("Opening sizes", opening_match, opening_total),
            ("Furniture sizes", furn_match, furn_total),
            ("Slab/Roof sizes", slab_match, slab_total),
        ] + checks

    grand_match = grand_total = 0
    print(f"\n  {'Check':<25} {'Match':>6} {'Total':>6} {'Score':>7}")
    print(f"  {'-'*25} {'-'*6} {'-'*6} {'-'*7}")
    for name, match, total in checks:
        pct = match * 100 // total if total > 0 else 0
        print(f"  {name:<25} {match:>6} {total:>6} {pct:>6}%")
        grand_match += match
        grand_total += total

    overall = grand_match * 100 // grand_total if grand_total > 0 else 0
    print(f"  {'-'*25} {'-'*6} {'-'*6} {'-'*7}")
    print(f"  {'OVERALL':<25} {grand_match:>6} {grand_total:>6} {overall:>6}%")

    # Discipline breakdown from dictionary (only in unfiltered mode)
    if ACTIVE_DISCIPLINE is None and (dict_arc[1] > 0 or dict_mep[1] > 0):
        print(f"\n  By discipline (X-ray near-match):")
        for label, (m, t) in [("ARC", dict_arc), ("MEP", dict_mep), ("STR", dict_str)]:
            pct = m * 100 // t if t > 0 else 0
            print(f"    {label:<5} {m:>5}/{t:<5} ({pct}%)")

    print(f"\n  Near-match = same category + thin dim \u00b15mm + long dims \u00b110%")
    print(f"  Exact match = same category + all dims within 10mm bucket")

    out_conn.close()
    ref_conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
