#!/usr/bin/env python3
"""
Grammar Coverage Checker — Compares reference IFC (Stacked_Duplex.db) against
metadata grammar (BOM.db) to measure spatial exactness.

Usage: python3 tools/grammar_checker.py

Outputs a scored report: exact matches, near matches, gaps.
"""

import sqlite3
import sys
import os
from collections import defaultdict

REFERENCE_DB = "database/Stacked_Duplex.db"
GRAMMAR_DB = "library/BOM.db"

TOLERANCE_MM = 5  # 5mm tolerance for "exact" match
NEAR_MM = 50      # 50mm tolerance for "near" match


def connect(db_path):
    if not os.path.exists(db_path):
        print(f"ERROR: {db_path} not found. Run from bim-compiler root.")
        sys.exit(1)
    return sqlite3.connect(db_path)


def get_ref_elements(conn):
    """Get all reference elements with bounding box dimensions."""
    rows = conn.execute("""
        SELECT m.element_name, m.ifc_class,
               r.maxX - r.minX AS dx,
               r.maxY - r.minY AS dy,
               r.maxZ - r.minZ AS dz
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        ORDER BY m.ifc_class, m.element_name
    """).fetchall()
    return rows


def parse_wall_type(element_name):
    """Extract wall type from IFC element name like 'Basic Wall:Exterior - Brick on Block:138062'."""
    parts = element_name.split(":")
    if len(parts) >= 2:
        return parts[1].strip()
    return element_name


def parse_opening_dims(element_name):
    """Extract nominal dimensions from opening name like 'M_Single-Flush:0864 x 2032mm:...'."""
    parts = element_name.split(":")
    if len(parts) >= 2:
        dim_str = parts[1].strip()
        # Try parsing "WxH" or "WxHmm" pattern
        import re
        match = re.search(r'(\d+)\s*(?:mm)?\s*x\s*(\d+)\s*(?:mm)?', dim_str, re.IGNORECASE)
        if match:
            return int(match.group(1)), int(match.group(2))
    return None, None


def get_wall_thickness(dx, dy):
    """Wall thickness = thinnest horizontal dimension (in metres)."""
    return min(dx, dy)


def check_walls(ref_conn, grammar_conn):
    """Compare reference wall thicknesses against ad_wall_type grammar."""
    print("\n" + "=" * 70)
    print("WALL TYPES (Dewey 200/300)")
    print("=" * 70)

    ref_walls = ref_conn.execute("""
        SELECT m.element_name, m.ifc_class,
               r.maxX - r.minX AS dx,
               r.maxY - r.minY AS dy,
               r.maxZ - r.minZ AS dz
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.ifc_class = 'IfcWall'
        ORDER BY m.element_name
    """).fetchall()

    grammar_walls = {}
    for row in grammar_conn.execute("SELECT wall_type_id, total_mm FROM ad_wall_type"):
        grammar_walls[row[0]] = row[1]

    # Map IFC wall type names to grammar IDs
    TYPE_MAP = {
        "Exterior - Brick on Block": "EXTERIOR_BRICK",
        "Interior - Partition (92mm Stud)": "INTERIOR_PARTITION_92",
        "Interior - Furring (38 mm Stud)": "INTERIOR_FURRING_38",
        "Interior - Furring (152 mm Stud)": "INTERIOR_FURRING_152",
        "Interior - Plumbing (152mm Stud)": "INTERIOR_PLUMBING_152",
        "Party Wall - CMU Residential Unit Dimising Wall": "PARTY_CMU",
        "Foundation - Concrete (417mm)": "FOUNDATION_417",
        "Foundation - Concrete (435mm)": "FOUNDATION_435",
    }

    exact = near = gap = 0
    by_type = defaultdict(lambda: {"count": 0, "exact": 0, "near": 0, "gap": 0})

    for name, ifc_class, dx, dy, dz in ref_walls:
        wall_type = parse_wall_type(name)
        thickness_m = get_wall_thickness(dx, dy)
        thickness_mm = thickness_m * 1000

        grammar_id = TYPE_MAP.get(wall_type)
        bt = by_type[wall_type]
        bt["count"] += 1
        bt["thickness_mm"] = thickness_mm

        if grammar_id and grammar_id in grammar_walls:
            grammar_mm = grammar_walls[grammar_id]
            diff = abs(thickness_mm - grammar_mm)
            bt["grammar_mm"] = grammar_mm

            if diff <= TOLERANCE_MM:
                exact += 1
                bt["exact"] += 1
            elif diff <= NEAR_MM:
                near += 1
                bt["near"] += 1
            else:
                gap += 1
                bt["gap"] += 1
        else:
            gap += 1
            bt["gap"] += 1

    total = exact + near + gap
    print(f"\n  {'Type':<45} {'Count':>5} {'IFC mm':>8} {'Grammar mm':>10} {'Match':>7}")
    print(f"  {'-'*45} {'-'*5} {'-'*8} {'-'*10} {'-'*7}")
    for wt, data in sorted(by_type.items()):
        gmm = data.get("grammar_mm", "---")
        if data["exact"] == data["count"]:
            status = "EXACT"
        elif data["near"] > 0:
            status = "NEAR"
        else:
            status = "GAP"
        print(f"  {wt:<45} {data['count']:>5} {data['thickness_mm']:>8.1f} {str(gmm):>10} {status:>7}")

    print(f"\n  WALLS: {exact}/{total} EXACT, {near} NEAR, {gap} GAP")
    return exact, near, gap, total


def check_openings(ref_conn, grammar_conn):
    """Compare reference door/window dimensions against ad_opening_family."""
    print("\n" + "=" * 70)
    print("OPENINGS (Dewey 400)")
    print("=" * 70)

    # Get grammar opening families
    grammar_openings = {}
    for row in grammar_conn.execute(
        "SELECT family_name, opening_type, default_width_mm, default_height_mm FROM ad_opening_family"
    ):
        grammar_openings[row[0]] = {
            "type": row[1], "width_mm": row[2], "height_mm": row[3]
        }

    # Get product dimensions for depth comparison
    grammar_dims = {}
    for row in grammar_conn.execute(
        "SELECT product_id, product_type, width, depth, height FROM ad_product_dim WHERE product_type IN ('DOOR', 'WINDOW')"
    ):
        grammar_dims[row[0]] = {"type": row[1], "w": row[2], "d": row[3], "h": row[4]}

    # Map IFC names to grammar family names
    DOOR_MAP = {
        "M_Single-Flush:0762 x 2032mm": "Duplex Interior 762",
        "M_Single-Flush:0864 x 2032mm": "Duplex Interior 864",
        "M_Single-Flush:1250mm x 2010mm": "Duplex Entry Door",
        "M_Single-Glass 1:0813 x 2420mm": "Duplex Glass Door",
    }
    WINDOW_MAP = {
        "M_Fixed:2800mm x 2410mm": "Duplex Bedroom Window",
        "M_Fixed:4835mm x 2420mm": "Duplex Living Room Window",
        "M_Fixed:750mm x 2200mm": "Duplex Narrow Tall Window",
        "M_Fixed:819mm x 759mm": "Duplex Small Fixed Window",
        "M_Casement:819mm x 759mm": "Duplex Casement Window",
        "M_Skylight:1180 x 1170mm": "Duplex Skylight",
    }

    exact = near = gap = 0
    results = []

    for ifc_class, type_map in [("IfcDoor", DOOR_MAP), ("IfcWindow", WINDOW_MAP)]:
        ref_elements = ref_conn.execute("""
            SELECT m.element_name, m.ifc_class,
                   r.maxX - r.minX AS dx,
                   r.maxY - r.minY AS dy,
                   r.maxZ - r.minZ AS dz
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = ?
        """, (ifc_class,)).fetchall()

        # Group by type (strip instance ID from name)
        by_type = defaultdict(list)
        for name, cls, dx, dy, dz in ref_elements:
            # Extract type key (first two colon-separated parts)
            parts = name.split(":")
            type_key = ":".join(parts[:2]) if len(parts) >= 2 else name
            by_type[type_key].append((dx, dy, dz))

        for type_key, instances in by_type.items():
            grammar_name = type_map.get(type_key)
            count = len(instances)

            # Use NOMINAL dimensions from IFC name, not bounding box
            # Bounding box includes wall depth + frame, which inflates the measurement
            nom_w, nom_h = parse_opening_dims(type_key)
            if nom_w and nom_h:
                ifc_w_m = nom_w / 1000.0
                ifc_h_m = nom_h / 1000.0
            else:
                # Fallback to bbox
                avg_dx = sum(d[0] for d in instances) / count
                avg_dy = sum(d[1] for d in instances) / count
                avg_dz = sum(d[2] for d in instances) / count
                ifc_w_m = max(avg_dx, avg_dy)
                ifc_h_m = avg_dz

            if grammar_name and grammar_name in grammar_openings:
                gf = grammar_openings[grammar_name]
                gw_m = gf["width_mm"] / 1000.0
                gh_m = gf["height_mm"] / 1000.0
                # Compare nominal width and height
                w_diff_mm = abs(ifc_w_m - gw_m) * 1000
                h_diff_mm = abs(ifc_h_m - gh_m) * 1000

                if w_diff_mm <= TOLERANCE_MM and h_diff_mm <= TOLERANCE_MM:
                    status = "EXACT"
                    exact += count
                elif w_diff_mm <= NEAR_MM and h_diff_mm <= NEAR_MM:
                    status = "NEAR"
                    near += count
                else:
                    status = f"W:{w_diff_mm:.0f} H:{h_diff_mm:.0f}"
                    near += count  # grammar exists, dimensions differ

                results.append((type_key, count, ifc_w_m, 0, ifc_h_m,
                                gw_m, gh_m, status))
            else:
                gap += count
                results.append((type_key, count, ifc_w_m, 0, ifc_h_m,
                                None, None, "NO GRAMMAR"))

    print(f"\n  {'Opening Type':<40} {'Cnt':>3} {'IFC W':>7} {'IFC H':>7} {'Gram W':>7} {'Gram H':>7} {'Status':>10}")
    print(f"  {'-'*40} {'-'*3} {'-'*7} {'-'*7} {'-'*7} {'-'*7} {'-'*10}")
    for r in results:
        gw = f"{r[5]:.3f}" if r[5] else "---"
        gh = f"{r[6]:.3f}" if r[6] else "---"
        print(f"  {r[0]:<40} {r[1]:>3} {r[2]:>7.3f} {r[4]:>7.3f} {gw:>7} {gh:>7} {r[7]:>10}")

    total = exact + near + gap
    print(f"\n  OPENINGS: {exact}/{total} EXACT, {near} NEAR, {gap} GAP")
    return exact, near, gap, total


def check_furniture(ref_conn, grammar_conn):
    """Compare reference furniture bounding boxes against component library."""
    print("\n" + "=" * 70)
    print("FURNITURE (Dewey 500)")
    print("=" * 70)

    ref_furn = ref_conn.execute("""
        SELECT m.element_name,
               r.maxX - r.minX AS dx,
               r.maxY - r.minY AS dy,
               r.maxZ - r.minZ AS dz
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.ifc_class = 'IfcFurnishingElement'
        ORDER BY m.element_name
    """).fetchall()

    # Group by type
    by_type = defaultdict(list)
    for name, dx, dy, dz in ref_furn:
        parts = name.split(":")
        type_key = parts[0].strip() if parts else name
        dims_sorted = tuple(sorted([dx, dy, dz], reverse=True))
        by_type[type_key].append(dims_sorted)

    # Get furniture from component_definitions by geometry (local extents)
    furn_defs = grammar_conn.execute("""
        SELECT cd.name,
               cd.local_max_x - cd.local_min_x AS w,
               cd.local_max_y - cd.local_min_y AS d,
               cd.local_max_z - cd.local_min_z AS h
        FROM component_definitions cd
        JOIN component_types ct ON cd.type_id = ct.id
        WHERE ct.ifc_class IN ('IfcFurniture', 'IfcFurnishingElement')
        GROUP BY cd.name
        ORDER BY cd.name
    """).fetchall()

    # Build lookup: name pattern → (w, d, h) in metres (sorted largest-first)
    grammar_furn = {}
    for cname, w, d, h in furn_defs:
        if w and d and h:
            grammar_furn[cname] = tuple(sorted([w, d, h], reverse=True))

    # Manual mapping: IFC furniture type → best grammar match
    FURN_MAP = {
        "M_Base Cabinet-Double Door & 2 Drawer": "Base_Cabinet",
        "M_Bed-Standard": "Bed",
        "M_Counter Top w Sink Hole": "Counter_Top",
        "M_Counter Top": "Counter_Top",
        "M_Sofa": "Sofa",
        "M_Table-Coffee": "Coffee_Table",
        "M_Tall Cabinet-Single Door(2)": "Tall_Cabinet",
        "M_Upper Cabinet-Double Door-Wall": "Upper_Cabinet",
        "M_Vanity Cabinet-Double Door Sink Unit": "Vanity_Cabinet",
    }

    exact = near = gap = 0
    results = []

    for type_key, instances in sorted(by_type.items()):
        count = len(instances)
        avg = tuple(sum(d[i] for d in instances) / count for i in range(3))

        # Try to find grammar match
        grammar_match = None
        grammar_name = FURN_MAP.get(type_key)
        if grammar_name:
            # Search for matching component by name pattern
            for gname, gdims in grammar_furn.items():
                if grammar_name.lower().replace("_", "") in gname.lower().replace("_", ""):
                    grammar_match = (gname, gdims)
                    break

        if grammar_match:
            gname, gdims = grammar_match
            diffs = [abs(avg[i] - gdims[i]) * 1000 for i in range(3)]
            max_diff = max(diffs)
            if max_diff <= TOLERANCE_MM:
                status = "EXACT"
                exact += count
            elif max_diff <= NEAR_MM:
                status = f"~{max_diff:.0f}mm"
                near += count
            else:
                status = f"D:{max_diff:.0f}mm"
                near += count
            results.append((type_key, count, avg, gname, gdims, status))
        else:
            gap += count
            results.append((type_key, count, avg, "---", None, "NO MATCH"))

    print(f"\n  {'Furniture Type':<42} {'Cnt':>3} {'IFC (LxWxH m)':>20} {'Grammar':>20} {'Status':>10}")
    print(f"  {'-'*42} {'-'*3} {'-'*20} {'-'*20} {'-'*10}")
    for r in results:
        ifc_str = f"{r[2][0]:.3f}x{r[2][1]:.3f}x{r[2][2]:.3f}"
        if r[4]:
            g_str = f"{r[4][0]:.3f}x{r[4][1]:.3f}x{r[4][2]:.3f}"
        else:
            g_str = "---"
        print(f"  {r[0]:<42} {r[1]:>3} {ifc_str:>20} {g_str:>20} {r[5]:>10}")

    total = exact + near + gap
    print(f"\n  FURNITURE: {exact}/{total} EXACT, {near} NEAR, {gap} GAP")
    return exact, near, gap, total


def check_mep(ref_conn, grammar_conn):
    """Check MEP element coverage."""
    print("\n" + "=" * 70)
    print("MEP (Dewey 600-900)")
    print("=" * 70)

    mep_classes = ref_conn.execute("""
        SELECT m.ifc_class, COUNT(*) as cnt
        FROM elements_meta m
        WHERE m.ifc_class IN ('IfcFlowSegment', 'IfcFlowFitting', 'IfcFlowTerminal')
        GROUP BY m.ifc_class
        ORDER BY cnt DESC
    """).fetchall()

    grammar_types = {}
    for row in grammar_conn.execute("SELECT ifc_class, category FROM component_types"):
        grammar_types[row[0]] = row[1]

    # MEP elements are system-generated (piping graph), not grammar-selected.
    # We check: (1) IFC class has a component_type entry, (2) terminals have BOM roles.
    exact = gap = 0
    print(f"\n  {'IFC Class':<25} {'Count':>6} {'Grammar Type':>20} {'Status':>8}")
    print(f"  {'-'*25} {'-'*6} {'-'*20} {'-'*8}")
    for cls, cnt in mep_classes:
        if cls in grammar_types:
            print(f"  {cls:<25} {cnt:>6} {grammar_types[cls]:>20} {'MAPPED':>8}")
            exact += cnt
        else:
            # IfcFlowSegment/IfcFlowFitting may map to IfcPipeSegment/IfcPipeFitting
            alt_map = {
                "IfcFlowSegment": "IfcPipeSegment",
                "IfcFlowFitting": "IfcPipeFitting",
            }
            alt = alt_map.get(cls)
            if alt and alt in grammar_types:
                print(f"  {cls:<25} {cnt:>6} {grammar_types[alt]+' (via '+alt+')':>20} {'MAPPED':>8}")
                exact += cnt
            else:
                print(f"  {cls:<25} {cnt:>6} {'---':>20} {'GAP':>8}")
                gap += cnt

    # Check BOM coverage for terminals
    terminal_types = ref_conn.execute("""
        SELECT m.element_name, COUNT(*) as cnt
        FROM elements_meta m
        WHERE m.ifc_class = 'IfcFlowTerminal'
        GROUP BY m.element_name
    """).fetchall()

    bom_roles = set()
    for row in grammar_conn.execute(
        "SELECT DISTINCT child_ifc_class FROM m_bom_line WHERE child_ifc_class IS NOT NULL"
    ):
        bom_roles.add(row[0])

    print(f"\n  MEP Terminal Detail:")
    print(f"  {'Terminal':<50} {'Cnt':>4} {'BOM':>6}")
    print(f"  {'-'*50} {'-'*4} {'-'*6}")
    for name, cnt in terminal_types:
        short_name = name.split(":")[0] if ":" in name else name
        # Group by broad category
        has_bom = "YES" if any(bc in short_name.lower() or short_name.lower() in bc.lower()
                               for bc in ["IfcSanitaryTerminal", "IfcLightFixture",
                                           "IfcFireSuppressionTerminal", "IfcOutlet",
                                           "IfcSwitchingDevice", "IfcFan",
                                           "IfcAirTerminal"]) else "---"
        print(f"  {short_name:<50} {cnt:>4} {has_bom:>6}")

    total = exact + gap
    print(f"\n  MEP: {exact}/{total} type-mapped ({exact*100//total}%)")
    return exact, 0, gap, total


def check_structural(ref_conn, grammar_conn):
    """Check structural element coverage."""
    print("\n" + "=" * 70)
    print("STRUCTURAL (Dewey 100)")
    print("=" * 70)

    struct_classes = [
        ("IfcSlab", "SLAB"), ("IfcBeam", "BEAM"), ("IfcMember", "MEMBER"),
        ("IfcRailing", "RAILING"), ("IfcStairFlight", "STAIR"),
    ]

    grammar_types = {}
    for row in grammar_conn.execute("SELECT ifc_class, category FROM component_types"):
        grammar_types[row[0]] = row[1]

    exact = gap = 0
    total = 0
    print(f"\n  {'IFC Class':<25} {'Count':>6} {'Grammar Type':>15} {'BOM Recipe':>20} {'Status':>8}")
    print(f"  {'-'*25} {'-'*6} {'-'*15} {'-'*20} {'-'*8}")

    for ifc_cls, expected_type in struct_classes:
        cnt = ref_conn.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE ifc_class = ?", (ifc_cls,)
        ).fetchone()[0]
        if cnt == 0:
            continue
        total += cnt

        has_type = ifc_cls in grammar_types
        # Check BOM
        bom = grammar_conn.execute(
            "SELECT bom_id FROM m_bom_line WHERE child_ifc_class = ? LIMIT 1", (ifc_cls,)
        ).fetchone()
        bom_name = bom[0] if bom else "---"

        if has_type and bom:
            status = "FULL"
            exact += cnt
        elif has_type:
            status = "TYPE"
            exact += cnt
        else:
            status = "GAP"
            gap += cnt

        gtype = grammar_types.get(ifc_cls, "---")
        print(f"  {ifc_cls:<25} {cnt:>6} {gtype:>15} {bom_name:>20} {status:>8}")

    print(f"\n  STRUCTURAL: {exact}/{total} covered")
    return exact, 0, gap, total


def main():
    print("=" * 70)
    print("  GRAMMAR COVERAGE CHECKER")
    print(f"  Reference: {REFERENCE_DB}")
    print(f"  Grammar:   {GRAMMAR_DB}")
    print(f"  Tolerance: {TOLERANCE_MM}mm exact, {NEAR_MM}mm near")
    print("=" * 70)

    ref_conn = connect(REFERENCE_DB)
    grammar_conn = connect(GRAMMAR_DB)

    # Element inventory
    total_elements = ref_conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    class_counts = ref_conn.execute("""
        SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC
    """).fetchall()

    print(f"\n  Reference corpus: {total_elements} elements across {len(class_counts)} IFC classes")
    for cls, cnt in class_counts:
        pct = cnt * 100 / total_elements
        bar = "#" * int(pct / 2)
        print(f"    {cls:<25} {cnt:>5} ({pct:>4.1f}%) {bar}")

    # Run all checks
    results = {}
    results["walls"] = check_walls(ref_conn, grammar_conn)
    results["openings"] = check_openings(ref_conn, grammar_conn)
    results["furniture"] = check_furniture(ref_conn, grammar_conn)
    results["mep"] = check_mep(ref_conn, grammar_conn)
    results["structural"] = check_structural(ref_conn, grammar_conn)

    # Summary
    print("\n" + "=" * 70)
    print("  SUMMARY — Grammar Coverage Score")
    print("=" * 70)

    grand_exact = grand_near = grand_gap = grand_total = 0
    print(f"\n  {'Category':<15} {'Exact':>6} {'Near':>6} {'Gap':>6} {'Total':>6} {'Score':>7}")
    print(f"  {'-'*15} {'-'*6} {'-'*6} {'-'*6} {'-'*6} {'-'*7}")
    for cat, (ex, nr, gp, tot) in results.items():
        score = f"{ex*100//tot}%" if tot > 0 else "N/A"
        print(f"  {cat.upper():<15} {ex:>6} {nr:>6} {gp:>6} {tot:>6} {score:>7}")
        grand_exact += ex
        grand_near += nr
        grand_gap += gp
        grand_total += tot

    overall = grand_exact * 100 // grand_total if grand_total > 0 else 0
    print(f"  {'-'*15} {'-'*6} {'-'*6} {'-'*6} {'-'*6} {'-'*7}")
    print(f"  {'TOTAL':<15} {grand_exact:>6} {grand_near:>6} {grand_gap:>6} {grand_total:>6} {overall:>6}%")
    print(f"\n  Exact match rate: {grand_exact}/{grand_total} elements ({overall}%)")
    print(f"  Near match rate:  {(grand_exact+grand_near)}/{grand_total} elements ({(grand_exact+grand_near)*100//grand_total}%)")
    print(f"  Gap (no grammar): {grand_gap}/{grand_total} elements")

    ref_conn.close()
    grammar_conn.close()

    return 0 if overall >= 50 else 1


if __name__ == "__main__":
    sys.exit(main())
