#!/usr/bin/env python3
"""
Rosetta Dictionary Extractor — Step 1 of the Linguist's Method.

Reads a reference DB (extracted from IFC) and produces a spatial skeleton:
corner points, anchor positions, spatial relationships. X-ray shows bones,
not soft tissue — we extract WHERE things are, not mesh details.

Usage:
    python3 tools/rosetta_dictionary.py [db_path]
    python3 tools/rosetta_dictionary.py [db_path] --discipline ARC,STR
    python3 tools/rosetta_dictionary.py --both

Discipline filter (comma-separated): ARC, STR, FP, ACMV, CW, ELEC, SP, LPG
When set, only elements with matching discipline are included.

Output: structured text dictionary suitable for cross-reference comparison.
"""

import sqlite3
import sys
import math
import argparse
from collections import defaultdict


def mm(val):
    """Convert metres to mm, rounded to integer."""
    return round(val * 1000)


def connect(db_path):
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn


# =========================================================================
# 1. SPATIAL STRUCTURE — storeys, rooms, hierarchy
# =========================================================================

def extract_spatial_structure(conn):
    """Extract building → storey → space hierarchy."""
    rows = conn.execute("""
        SELECT guid, type, name, parent_guid, object_type, predefined_type
        FROM spatial_structure ORDER BY type, name
    """).fetchall()

    storeys = []
    spaces = []
    for r in rows:
        if r['type'] == 'IfcBuildingStorey':
            storeys.append({'guid': r['guid'], 'name': r['name']})
        elif r['type'] == 'IfcSpace':
            spaces.append({
                'guid': r['guid'], 'name': r['name'],
                'parent': r['parent_guid']
            })
    return storeys, spaces


# =========================================================================
# 2. ELEMENT EXTRACTION — bbox corners + classification
# =========================================================================

def extract_elements(conn, disciplines=None):
    """Extract every element with bbox corners and classification.
    If disciplines is set, only include elements with matching discipline."""
    if disciplines:
        placeholders = ','.join('?' for _ in disciplines)
        rows = conn.execute(f"""
            SELECT e.id, e.guid, e.ifc_class, e.element_name, e.element_type,
                   e.storey, e.discipline,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta e
            JOIN elements_rtree r ON e.id = r.id
            WHERE e.discipline IN ({placeholders})
            ORDER BY e.ifc_class, e.element_name
        """, disciplines).fetchall()
    else:
        rows = conn.execute("""
            SELECT e.id, e.guid, e.ifc_class, e.element_name, e.element_type,
                   e.storey, e.discipline,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta e
            JOIN elements_rtree r ON e.id = r.id
            ORDER BY e.ifc_class, e.element_name
        """).fetchall()

    elements = []
    for r in rows:
        dx = r['maxX'] - r['minX']
        dy = r['maxY'] - r['minY']
        dz = r['maxZ'] - r['minZ']
        dims = sorted([dx, dy, dz], reverse=True)

        elements.append({
            'id': r['id'],
            'guid': r['guid'],
            'ifc_class': r['ifc_class'],
            'name': r['element_name'] or '',
            'type': r['element_type'] or '',
            'storey': r['storey'] or '',
            'discipline': r['discipline'] or '',
            # Bbox corners (world coordinates)
            'min': (r['minX'], r['minY'], r['minZ']),
            'max': (r['maxX'], r['maxY'], r['maxZ']),
            'center': (
                (r['minX'] + r['maxX']) / 2,
                (r['minY'] + r['maxY']) / 2,
                (r['minZ'] + r['maxZ']) / 2
            ),
            # Dimensions in mm
            'dx_mm': mm(dx), 'dy_mm': mm(dy), 'dz_mm': mm(dz),
            'dims_mm': [mm(d) for d in dims],  # sorted L, W, H
            'thin_mm': mm(dims[2]),  # thinnest dimension
        })
    return elements


# =========================================================================
# 3. CONTAINMENT — which elements are in which rooms
# =========================================================================

def extract_containment(conn):
    """Extract element-to-space containment relationships."""
    rows = conn.execute("""
        SELECT r.element_guid, r.space_guid, s.name as space_name
        FROM rel_contained_in_space r
        LEFT JOIN spatial_structure s ON r.space_guid = s.guid
    """).fetchall()

    # element_guid -> space_name
    elem_to_space = {}
    space_contents = defaultdict(list)
    for r in rows:
        elem_to_space[r['element_guid']] = r['space_name']
        space_contents[r['space_name']].append(r['element_guid'])
    return elem_to_space, space_contents


# =========================================================================
# 4. WALL SKELETON — direction, start/end, thickness
# =========================================================================

def classify_wall(elem):
    """Classify a wall element: direction, thickness, endpoints."""
    dx, dy, dz = elem['dx_mm'], elem['dy_mm'], elem['dz_mm']
    minx, miny, minz = elem['min']
    maxx, maxy, maxz = elem['max']

    # Walls are tall (dz >> thin dim) and long in one plan direction
    # Thin dimension = wall thickness
    if dx < dy:
        # Thin along X → wall runs N-S (along Y axis)
        direction = 'NS'
        thickness_mm = dx
        start = (mm(minx), mm(miny))
        end = (mm(minx), mm(maxy))
        length_mm = dy
    else:
        # Thin along Y → wall runs E-W (along X axis)
        direction = 'EW'
        thickness_mm = dy
        start = (mm(minx), mm(miny))
        end = (mm(maxx), mm(miny))
        length_mm = dx

    return {
        'direction': direction,
        'thickness_mm': thickness_mm,
        'length_mm': mm(length_mm),
        'height_mm': dz,
        'start': start,
        'end': end,
        'z_range': (mm(minz), mm(maxz)),
    }


# =========================================================================
# 5. OPENING PLACEMENT — which wall, position along wall
# =========================================================================

def classify_opening(elem, walls):
    """Determine which wall an opening sits on and its position."""
    cx, cy, cz = elem['center']
    best_wall = None
    best_dist = float('inf')

    for w in walls:
        wmin = w['elem']['min']
        wmax = w['elem']['max']
        # Check if opening center is within wall's plan extent (with tolerance)
        tol = 0.3  # 300mm tolerance for containment
        in_x = (wmin[0] - tol) <= cx <= (wmax[0] + tol)
        in_y = (wmin[1] - tol) <= cy <= (wmax[1] + tol)
        in_z = (wmin[2] - tol) <= cz <= (wmax[2] + tol)

        if in_x and in_y and in_z:
            # Distance from opening center to wall center
            wcx = (wmin[0] + wmax[0]) / 2
            wcy = (wmin[1] + wmax[1]) / 2
            dist = math.sqrt((cx - wcx)**2 + (cy - wcy)**2)
            if dist < best_dist:
                best_dist = dist
                best_wall = w

    if best_wall is None:
        return {'wall': None, 'position_along_wall_mm': 0}

    w_info = best_wall['info']
    if w_info['direction'] == 'EW':
        # Wall runs along X, position = X offset from wall start
        pos = mm(cx - best_wall['elem']['min'][0])
    else:
        # Wall runs along Y, position = Y offset from wall start
        pos = mm(cy - best_wall['elem']['min'][1])

    return {
        'wall': best_wall['elem']['name'],
        'wall_direction': w_info['direction'],
        'wall_thickness_mm': w_info['thickness_mm'],
        'position_along_wall_mm': pos,
        'height_above_floor_mm': mm(cz - best_wall['elem']['min'][2]),
    }


# =========================================================================
# 6. ADJACENCY — which rooms share edges
# =========================================================================

def compute_room_extents(conn, spaces, elements, elem_to_space):
    """Compute bounding extent of each room from its contained elements."""
    # Build guid -> element lookup
    guid_to_elem = {e['guid']: e for e in elements}

    room_extents = {}
    for space_name, elem_guids in defaultdict(list).items():
        pass  # unused, computed below

    # Get elements per space from containment
    _, space_contents = extract_containment(conn)
    for space_name, guids in space_contents.items():
        if not guids:
            continue
        min_x = min_y = min_z = float('inf')
        max_x = max_y = max_z = float('-inf')
        for g in guids:
            if g in guid_to_elem:
                e = guid_to_elem[g]
                min_x = min(min_x, e['min'][0])
                min_y = min(min_y, e['min'][1])
                min_z = min(min_z, e['min'][2])
                max_x = max(max_x, e['max'][0])
                max_y = max(max_y, e['max'][1])
                max_z = max(max_z, e['max'][2])
        if min_x < float('inf'):
            room_extents[space_name] = {
                'min': (min_x, min_y, min_z),
                'max': (max_x, max_y, max_z),
                'width_mm': mm(max_x - min_x),
                'depth_mm': mm(max_y - min_y),
                'height_mm': mm(max_z - min_z),
            }
    return room_extents


# =========================================================================
# 7. EXISTING DICTIONARY — leverage element_dictionary if present
# =========================================================================

def extract_element_dictionary(conn):
    """Load the manually-classified element dictionary."""
    try:
        rows = conn.execute("""
            SELECT ref_ifc_class, ref_type_name, compiler_category,
                   compiler_subtype, width_mm, height_mm, depth_mm,
                   instance_count, notes
            FROM element_dictionary ORDER BY compiler_category, ref_ifc_class
        """).fetchall()
        return [dict(r) for r in rows]
    except Exception:
        return []


# =========================================================================
# MAIN REPORT
# =========================================================================

def generate_dictionary(db_path, disciplines=None):
    conn = connect(db_path)
    db_name = db_path.split('/')[-1].replace('_extracted.db', '').replace('.db', '')

    disc_label = f" [{','.join(disciplines)}]" if disciplines else ""
    print(f"{'='*72}")
    print(f"  ROSETTA DICTIONARY: {db_name}{disc_label}")
    print(f"  Source: {db_path}")
    print(f"{'='*72}")

    # --- Structure ---
    storeys, spaces = extract_spatial_structure(conn)
    print(f"\n{'='*72}")
    print(f"  1. SPATIAL STRUCTURE")
    print(f"{'='*72}")
    print(f"\n  Storeys: {len(storeys)}")
    for s in storeys:
        print(f"    {s['name']}")
    print(f"\n  Spaces/Rooms: {len(spaces)}")
    for s in spaces:
        parent_name = ''
        for st in storeys:
            if st['guid'] == s['parent']:
                parent_name = st['name']
        print(f"    {s['name']:20s}  (storey: {parent_name})")

    # --- Elements ---
    elements = extract_elements(conn, disciplines)
    elem_to_space, space_contents = extract_containment(conn)
    guid_to_elem = {e['guid']: e for e in elements}

    print(f"\n{'='*72}")
    print(f"  2. ELEMENT CENSUS")
    print(f"{'='*72}")
    print(f"\n  Total elements: {len(elements)}")

    by_class = defaultdict(list)
    for e in elements:
        by_class[e['ifc_class']].append(e)

    for cls in sorted(by_class.keys()):
        items = by_class[cls]
        print(f"\n  {cls} ({len(items)} elements)")

    # --- Wall Skeleton ---
    print(f"\n{'='*72}")
    print(f"  3. WALL SKELETON — Start/End Points + Thickness")
    print(f"{'='*72}")

    wall_classes = ['IfcWall', 'IfcWallStandardCase']
    walls_data = []
    for cls in wall_classes:
        for e in by_class.get(cls, []):
            info = classify_wall(e)
            room = elem_to_space.get(e['guid'], '?')
            walls_data.append({'elem': e, 'info': info, 'room': room})

    walls_data.sort(key=lambda w: (w['info']['direction'], w['info']['start']))

    print(f"\n  {'Name':<45s} {'Dir':>3s} {'Thick':>6s} {'Length':>7s} {'Start':>16s} {'End':>16s} {'Room':<12s}")
    print(f"  {'-'*44} {'-'*3} {'-'*6} {'-'*7} {'-'*16} {'-'*16} {'-'*12}")
    for w in walls_data:
        info = w['info']
        name = w['elem']['name'][:44]
        print(f"  {name:<45s} {info['direction']:>3s} {info['thickness_mm']:>5d}mm"
              f" {info['length_mm']:>6d}mm"
              f" ({info['start'][0]:>6d},{info['start'][1]:>6d})"
              f" ({info['end'][0]:>6d},{info['end'][1]:>6d})"
              f" {w['room']:<12s}")

    # --- Opening Placement ---
    print(f"\n{'='*72}")
    print(f"  4. OPENING PLACEMENT — Wall + Position")
    print(f"{'='*72}")

    opening_classes = ['IfcDoor', 'IfcWindow']
    for cls in opening_classes:
        items = by_class.get(cls, [])
        if not items:
            continue
        print(f"\n  {cls} ({len(items)} elements)")
        print(f"  {'Name':<40s} {'W mm':>5s} {'H mm':>5s} {'Thin':>5s} {'Center X':>9s} {'Center Y':>9s} {'Z base':>7s} {'Room':<12s}")
        print(f"  {'-'*39} {'-'*5} {'-'*5} {'-'*5} {'-'*9} {'-'*9} {'-'*7} {'-'*12}")
        for e in items:
            room = elem_to_space.get(e['guid'], '?')
            dims = e['dims_mm']
            cx, cy, cz = e['center']
            z_base = e['min'][2]
            # Width = longest plan dim, Height = Z-extent
            plan_dims = sorted([e['dx_mm'], e['dy_mm']], reverse=True)
            name = e['name'][:39]
            print(f"  {name:<40s} {plan_dims[0]:>5d} {e['dz_mm']:>5d} {e['thin_mm']:>5d}"
                  f" {mm(cx):>9d} {mm(cy):>9d} {mm(z_base):>7d} {room:<12s}")

    # --- Furniture Anchors ---
    print(f"\n{'='*72}")
    print(f"  5. FURNITURE ANCHORS — Room + Position")
    print(f"{'='*72}")

    furn_classes = ['IfcFurnishingElement', 'IfcFurniture']
    for cls in furn_classes:
        items = by_class.get(cls, [])
        if not items:
            continue
        print(f"\n  {cls} ({len(items)} elements)")
        print(f"  {'Name':<40s} {'L mm':>5s} {'W mm':>5s} {'H mm':>5s} {'Center X':>9s} {'Center Y':>9s} {'Room':<12s}")
        print(f"  {'-'*39} {'-'*5} {'-'*5} {'-'*5} {'-'*9} {'-'*9} {'-'*12}")
        for e in items:
            room = elem_to_space.get(e['guid'], '?')
            dims = e['dims_mm']
            cx, cy, _ = e['center']
            name = e['name'][:39]
            print(f"  {name:<40s} {dims[0]:>5d} {dims[1]:>5d} {dims[2]:>5d}"
                  f" {mm(cx):>9d} {mm(cy):>9d} {room:<12s}")

    # --- Slabs & Roofs ---
    print(f"\n{'='*72}")
    print(f"  6. SLAB & ROOF SKELETON")
    print(f"{'='*72}")

    for cls in ['IfcSlab', 'IfcRoof']:
        items = by_class.get(cls, [])
        if not items:
            continue
        print(f"\n  {cls} ({len(items)} elements)")
        print(f"  {'Name':<40s} {'X range':>18s} {'Y range':>18s} {'Z range':>14s} {'Thick':>6s}")
        print(f"  {'-'*39} {'-'*18} {'-'*18} {'-'*14} {'-'*6}")
        for e in items:
            name = e['name'][:39]
            thick = min(e['dx_mm'], e['dy_mm'], e['dz_mm'])
            print(f"  {name:<40s}"
                  f" {mm(e['min'][0]):>8d}→{mm(e['max'][0]):>7d}"
                  f" {mm(e['min'][1]):>8d}→{mm(e['max'][1]):>7d}"
                  f" {mm(e['min'][2]):>6d}→{mm(e['max'][2]):>5d}"
                  f" {thick:>5d}mm")

    # --- Structural Frame ---
    print(f"\n{'='*72}")
    print(f"  7. STRUCTURAL FRAME (Beams, Columns, Members)")
    print(f"{'='*72}")

    for cls in ['IfcBeam', 'IfcColumn', 'IfcMember']:
        items = by_class.get(cls, [])
        if not items:
            continue
        # Group by name pattern
        by_name = defaultdict(list)
        for e in items:
            by_name[e['name']].append(e)
        print(f"\n  {cls} ({len(items)} elements, {len(by_name)} types)")
        for name, group in sorted(by_name.items()):
            dims = group[0]['dims_mm']
            print(f"    {name[:50]:<52s} {dims[0]:>5d}x{dims[1]:>4d}x{dims[2]:>4d}mm  x{len(group)}")

    # --- MEP Summary ---
    mep_classes = ['IfcFlowSegment', 'IfcFlowFitting', 'IfcFlowTerminal',
                   'IfcSanitaryTerminal', 'IfcRailing', 'IfcStairFlight']
    mep_items = []
    for cls in mep_classes:
        mep_items.extend(by_class.get(cls, []))
    if mep_items:
        print(f"\n{'='*72}")
        print(f"  8. MEP & OTHER ({len(mep_items)} elements)")
        print(f"{'='*72}")
        mep_by_class = defaultdict(int)
        for e in mep_items:
            mep_by_class[e['ifc_class']] += 1
        for cls, cnt in sorted(mep_by_class.items()):
            print(f"    {cls:<30s} {cnt:>5d}")

    # --- Room Contents Summary ---
    print(f"\n{'='*72}")
    print(f"  9. ROOM CONTENTS (elements per space)")
    print(f"{'='*72}")

    for space_name in sorted(space_contents.keys(), key=lambda x: x or ""):
        guids = space_contents[space_name]
        elems_in_room = [guid_to_elem[g] for g in guids if g in guid_to_elem]
        if not elems_in_room:
            continue
        class_counts = defaultdict(int)
        for e in elems_in_room:
            class_counts[e['ifc_class']] += 1
        print(f"\n  {space_name}:")
        for cls, cnt in sorted(class_counts.items()):
            print(f"    {cls:<30s} {cnt:>3d}")

    # --- Existing Dictionary ---
    dict_entries = extract_element_dictionary(conn)
    if dict_entries:
        print(f"\n{'='*72}")
        print(f"  10. ELEMENT DICTIONARY (pre-classified)")
        print(f"{'='*72}")
        print(f"\n  {'Category':<12s} {'IFC Class':<25s} {'Subtype':<25s} {'W':>5s} {'H':>5s} {'D':>5s} {'#':>3s}")
        print(f"  {'-'*11} {'-'*24} {'-'*24} {'-'*5} {'-'*5} {'-'*5} {'-'*3}")
        for d in dict_entries:
            w = str(d['width_mm'] or '')
            h = str(d['height_mm'] or '')
            dp = str(d['depth_mm'] or '')
            print(f"  {d['compiler_category']:<12s} {d['ref_ifc_class']:<25s}"
                  f" {(d['compiler_subtype'] or ''):<25s}"
                  f" {w:>5s} {h:>5s} {dp:>5s} {d['instance_count']:>3d}")

    # --- Corner Point Summary ---
    print(f"\n{'='*72}")
    print(f"  11. SPATIAL CORNERS — Building skeleton key points")
    print(f"{'='*72}")

    # Extract building envelope from all elements
    all_min_x = min(e['min'][0] for e in elements)
    all_max_x = max(e['max'][0] for e in elements)
    all_min_y = min(e['min'][1] for e in elements)
    all_max_y = max(e['max'][1] for e in elements)
    all_min_z = min(e['min'][2] for e in elements)
    all_max_z = max(e['max'][2] for e in elements)

    print(f"\n  Building envelope:")
    print(f"    X: {mm(all_min_x):>8d} → {mm(all_max_x):>8d}  ({mm(all_max_x - all_min_x)}mm)")
    print(f"    Y: {mm(all_min_y):>8d} → {mm(all_max_y):>8d}  ({mm(all_max_y - all_min_y)}mm)")
    print(f"    Z: {mm(all_min_z):>8d} → {mm(all_max_z):>8d}  ({mm(all_max_z - all_min_z)}mm)")

    # Wall corner intersections
    print(f"\n  Wall endpoints (unique plan coordinates):")
    wall_points = set()
    for w in walls_data:
        wall_points.add(w['info']['start'])
        wall_points.add(w['info']['end'])
    for pt in sorted(wall_points):
        print(f"    ({pt[0]:>8d}, {pt[1]:>8d})")

    # Storey Z levels
    print(f"\n  Storey Z levels:")
    storey_z = defaultdict(lambda: {'min': float('inf'), 'max': float('-inf')})
    for e in elements:
        if e['storey']:
            storey_z[e['storey']]['min'] = min(storey_z[e['storey']]['min'], e['min'][2])
            storey_z[e['storey']]['max'] = max(storey_z[e['storey']]['max'], e['max'][2])
    for sname in sorted(storey_z.keys()):
        sz = storey_z[sname]
        print(f"    {sname:<20s}  Z: {mm(sz['min']):>8d} → {mm(sz['max']):>8d}"
              f"  (height: {mm(sz['max'] - sz['min'])}mm)")

    conn.close()
    print(f"\n{'='*72}")
    print(f"  END OF DICTIONARY: {db_name}")
    print(f"{'='*72}")


# =========================================================================
# ENTRY POINT
# =========================================================================

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Rosetta Dictionary Extractor — spatial skeleton from reference DB')
    parser.add_argument('db_path', nargs='?', help='Path to extracted reference DB')
    parser.add_argument('--both', action='store_true',
                        help='Run both SampleHouse and Duplex reference DBs')
    parser.add_argument('--discipline', type=str, default=None,
                        help='Comma-separated discipline filter (ARC,STR,FP,ACMV,CW,ELEC,SP,LPG)')
    args = parser.parse_args()

    disciplines = [d.strip() for d in args.discipline.split(',')] if args.discipline else None

    if args.both:
        refs = [
            'reference/rosetta/Ifc4_SampleHouse_extracted.db',
            'reference/rosetta/Ifc2x3_Duplex_extracted.db',
        ]
        for ref in refs:
            generate_dictionary(ref, disciplines)
            print("\n\n")
    elif args.db_path:
        generate_dictionary(args.db_path, disciplines)
    else:
        parser.print_help()
        sys.exit(1)
