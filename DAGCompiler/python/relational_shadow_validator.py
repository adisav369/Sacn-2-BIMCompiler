#!/usr/bin/env python3
"""
Phase RM-2: Relational Shadow Validator
Computes element coordinates from relational rules and compares against
stored I_Element_Extraction (the oracle). Reports match/mismatch stats.

Usage:
    python3 relational_shadow_validator.py [--building Ifc4_SampleHouse|Ifc2x3_Duplex|all]
"""

import sqlite3
import argparse
import sys
from collections import defaultdict

DB_PATH = "library/BOM.db"
TOLERANCE_MM = 0.01  # 0.01mm tolerance for coordinate matching


def load_stored_placements(conn, building_type):
    """Load oracle placements from I_Element_Extraction."""
    sql = """
        SELECT placement_id, building_type, storey, ifc_class, element_ref,
               ordinal, min_x, max_x, min_y, max_y, min_z, max_z,
               orientation, discipline, material_name, material_rgba
        FROM I_Element_Extraction
        WHERE building_type = ? AND is_active = 1
        ORDER BY storey, ifc_class, ordinal
    """
    placements = {}
    for row in conn.execute(sql, (building_type,)):
        pid = row[0]
        placements[pid] = {
            'placement_id': pid,
            'building_type': row[1],
            'storey': row[2],
            'ifc_class': row[3],
            'element_ref': row[4],
            'ordinal': row[5],
            'min_x': row[6], 'max_x': row[7],
            'min_y': row[8], 'max_y': row[9],
            'min_z': row[10], 'max_z': row[11],
            'orientation': row[12],
            'discipline': row[13],
        }
    return placements


def load_grid(conn, building_type):
    """Load grid lines → {label: position_mm}."""
    x_lines = {}
    y_lines = {}
    for row in conn.execute(
            "SELECT axis, grid_label, position_mm FROM ad_building_grid WHERE building_type = ?",
            (building_type,)):
        if row[0] == 'X':
            x_lines[row[1]] = row[2]
        else:
            y_lines[row[1]] = row[2]
    return x_lines, y_lines


def load_rooms(conn, building_type, x_lines, y_lines):
    """Load room boundaries. Use exact coords if available, else resolve from grid labels."""
    rooms = {}
    for row in conn.execute(
            """SELECT room_name, storey, room_type,
                      grid_min_x, grid_max_x, grid_min_y, grid_max_y,
                      min_x_mm, max_x_mm, min_y_mm, max_y_mm
               FROM ad_room_boundary WHERE building_type = ?""",
            (building_type,)):
        name = row[0]
        # Prefer exact coords; fall back to grid-resolved
        rooms[name] = {
            'name': name,
            'storey': row[1],
            'type': row[2],
            'min_x_mm': row[7] if row[7] is not None else x_lines.get(row[3], 0),
            'max_x_mm': row[8] if row[8] is not None else x_lines.get(row[4], 0),
            'min_y_mm': row[9] if row[9] is not None else y_lines.get(row[5], 0),
            'max_y_mm': row[10] if row[10] is not None else y_lines.get(row[6], 0),
        }
    return rooms


def load_wall_faces(conn, building_type, rooms):
    """Load wall faces and compute wall segment geometry from room boundaries."""
    walls = {}
    for row in conn.execute(
            """SELECT room_name, storey, face, wall_type_id, is_exterior, adjacent_room
               FROM ad_wall_face WHERE building_type = ?""",
            (building_type,)):
        room_name, storey, face = row[0], row[1], row[2]
        room = rooms.get(room_name)
        if not room:
            continue

        # Compute wall segment from room boundary + face direction (in mm)
        if face == 'NORTH':
            x1, y1 = room['min_x_mm'], room['max_y_mm']
            x2, y2 = room['max_x_mm'], room['max_y_mm']
        elif face == 'SOUTH':
            x1, y1 = room['min_x_mm'], room['min_y_mm']
            x2, y2 = room['max_x_mm'], room['min_y_mm']
        elif face == 'EAST':
            x1, y1 = room['max_x_mm'], room['min_y_mm']
            x2, y2 = room['max_x_mm'], room['max_y_mm']
        else:  # WEST
            x1, y1 = room['min_x_mm'], room['min_y_mm']
            x2, y2 = room['min_x_mm'], room['max_y_mm']

        key = f"WALL_{room_name}_{face}"
        walls[key] = {
            'key': key,
            'room_name': room_name,
            'storey': storey,
            'face': face,
            'x1_mm': x1, 'y1_mm': y1,
            'x2_mm': x2, 'y2_mm': y2,
            'is_exterior': row[4],
        }
    return walls


def load_orderlines(conn, building_type):
    """Load element rules."""
    rules = []
    for row in conn.execute(
            """SELECT element_ref, ifc_class, storey, discipline,
                      host_type, host_ref, position_rule,
                      position_value, position_value_2, height_mm,
                      family_ref, width_mm, height_extent_mm, depth_mm,
                      orientation, material_name, material_rgba
               FROM c_orderline WHERE building_type = ?""",
            (building_type,)):
        rules.append({
            'element_ref': row[0],
            'ifc_class': row[1],
            'storey': row[2],
            'discipline': row[3],
            'host_type': row[4],
            'host_ref': row[5],
            'position_rule': row[6],
            'position_value': row[7],
            'position_value_2': row[8],
            'height_mm': row[9],
            'family_ref': row[10],
            'width_mm': row[11],
            'height_extent_mm': row[12],
            'depth_mm': row[13],
            'orientation': row[14],
            'material_name': row[15],
            'material_rgba': row[16],
        })
    return rules


def compute_placement(rule, rooms, walls):
    """Compute absolute bbox (in metres) from a relational rule."""
    pos_rule = rule['position_rule']
    host_type = rule['host_type']
    width_m = rule['width_mm'] / 1000
    height_m = rule['height_extent_mm'] / 1000
    depth_m = rule['depth_mm'] / 1000
    min_z = rule['height_mm'] / 1000
    max_z = min_z + height_m

    if pos_rule == 'ABSOLUTE':
        # Center X and Y stored in position_value / position_value_2 (mm)
        cx = rule['position_value'] / 1000
        cy = rule['position_value_2'] / 1000
        min_x = cx - width_m / 2
        max_x = cx + width_m / 2
        min_y = cy - depth_m / 2
        max_y = cy + depth_m / 2
        return min_x, max_x, min_y, max_y, min_z, max_z

    elif pos_rule == 'ENVELOPE':
        # Slabs/roofs: center stored, dimensions known
        cx = rule['position_value'] / 1000
        cy = rule['position_value_2'] / 1000
        min_x = cx - width_m / 2
        max_x = cx + width_m / 2
        min_y = cy - depth_m / 2
        max_y = cy + depth_m / 2
        return min_x, max_x, min_y, max_y, min_z, max_z

    elif pos_rule == 'BOUNDARY' and host_type in ('ROOM', 'BUILDING'):
        # Walls on room boundaries: center stored
        cx = rule['position_value'] / 1000
        cy = rule['position_value_2'] / 1000
        min_x = cx - width_m / 2
        max_x = cx + width_m / 2
        min_y = cy - depth_m / 2
        max_y = cy + depth_m / 2
        return min_x, max_x, min_y, max_y, min_z, max_z

    elif pos_rule == 'FRACTION' and host_type == 'WALL':
        wall = walls.get(rule['host_ref'])
        if not wall:
            return None
        t = rule['position_value']
        # Point along wall
        wx = wall['x1_mm'] + (wall['x2_mm'] - wall['x1_mm']) * t
        wy = wall['y1_mm'] + (wall['y2_mm'] - wall['y1_mm']) * t
        # Add perpendicular offset
        perp_mm = rule['position_value_2'] or 0
        face = wall['face']
        if face in ('NORTH', 'SOUTH'):
            wy = wall['y1_mm'] + perp_mm
        else:
            wx = wall['x1_mm'] + perp_mm

        cx = wx / 1000
        cy = wy / 1000
        # width_mm = X extent, depth_mm = Y extent (always)
        min_x = cx - width_m / 2
        max_x = cx + width_m / 2
        min_y = cy - depth_m / 2
        max_y = cy + depth_m / 2
        return min_x, max_x, min_y, max_y, min_z, max_z

    elif pos_rule == 'FRACTION' and host_type == 'ROOM':
        room = rooms.get(rule['host_ref'])
        if not room:
            return None
        rx = rule['position_value']
        ry = rule['position_value_2'] if rule['position_value_2'] is not None else 0.5
        room_w = room['max_x_mm'] - room['min_x_mm']
        room_d = room['max_y_mm'] - room['min_y_mm']
        cx = (room['min_x_mm'] + rx * room_w) / 1000
        cy = (room['min_y_mm'] + ry * room_d) / 1000
        min_x = cx - width_m / 2
        max_x = cx + width_m / 2
        min_y = cy - depth_m / 2
        max_y = cy + depth_m / 2
        return min_x, max_x, min_y, max_y, min_z, max_z

    return None  # Unhandled position rule


def extract_placement_id(element_ref):
    """Extract placement_id from element_ref format: IfcClass_ID."""
    parts = element_ref.rsplit('_', 1)
    if len(parts) == 2:
        try:
            return int(parts[1])
        except ValueError:
            pass
    return None


def validate_building(conn, building_type):
    """Run shadow validation for one building."""
    print(f"\n{'='*60}")
    print(f"Shadow Validation: {building_type}")
    print(f"{'='*60}")

    # Load oracle
    stored = load_stored_placements(conn, building_type)
    print(f"  Oracle placements: {len(stored)}")

    # Load relational data
    x_lines, y_lines = load_grid(conn, building_type)
    rooms = load_rooms(conn, building_type, x_lines, y_lines)
    walls = load_wall_faces(conn, building_type, rooms)
    rules = load_orderlines(conn, building_type)
    print(f"  Rules: {len(rules)}, Rooms: {len(rooms)}, Walls: {len(walls)}")

    # Compute and compare
    match_count = 0
    mismatch_count = 0
    unresolved_count = 0
    no_oracle_count = 0
    max_error = 0
    errors_by_type = defaultdict(list)
    tolerance_m = TOLERANCE_MM / 1000

    for rule in rules:
        pid = extract_placement_id(rule['element_ref'])
        if pid is None or pid not in stored:
            no_oracle_count += 1
            continue

        oracle = stored[pid]
        computed = compute_placement(rule, rooms, walls)

        if computed is None:
            unresolved_count += 1
            continue

        c_min_x, c_max_x, c_min_y, c_max_y, c_min_z, c_max_z = computed

        # Compare all 6 coordinates
        diffs = [
            abs(c_min_x - oracle['min_x']),
            abs(c_max_x - oracle['max_x']),
            abs(c_min_y - oracle['min_y']),
            abs(c_max_y - oracle['max_y']),
            abs(c_min_z - oracle['min_z']),
            abs(c_max_z - oracle['max_z']),
        ]
        max_diff = max(diffs)
        max_error = max(max_error, max_diff)

        if max_diff <= tolerance_m:
            match_count += 1
        else:
            mismatch_count += 1
            key = f"{rule['host_type']}/{rule['position_rule']}"
            if len(errors_by_type[key]) < 3:  # Show first 3 per type
                errors_by_type[key].append({
                    'ref': rule['element_ref'],
                    'ifc': rule['ifc_class'],
                    'max_diff_mm': max_diff * 1000,
                    'diffs_mm': [d * 1000 for d in diffs],
                    'computed': [c_min_x, c_max_x, c_min_y, c_max_y, c_min_z, c_max_z],
                    'oracle': [oracle['min_x'], oracle['max_x'],
                               oracle['min_y'], oracle['max_y'],
                               oracle['min_z'], oracle['max_z']],
                })

    total = match_count + mismatch_count + unresolved_count
    print(f"\n  RESULTS:")
    print(f"    Matched (within {TOLERANCE_MM}mm): {match_count}/{total} ({match_count/max(total,1)*100:.1f}%)")
    print(f"    Mismatched: {mismatch_count}")
    print(f"    Unresolved (no computation): {unresolved_count}")
    print(f"    No oracle match: {no_oracle_count}")
    print(f"    Max error: {max_error*1000:.3f}mm")

    if errors_by_type:
        print(f"\n  MISMATCHES BY TYPE:")
        for key, errors in sorted(errors_by_type.items()):
            total_of_type = sum(1 for r in rules
                               if f"{r['host_type']}/{r['position_rule']}" == key)
            print(f"    {key} ({len(errors)} of {total_of_type} shown):")
            for e in errors:
                print(f"      {e['ref']} ({e['ifc']}): max_diff={e['max_diff_mm']:.3f}mm")
                print(f"        computed: [{', '.join(f'{v:.6f}' for v in e['computed'])}]")
                print(f"        oracle:   [{', '.join(f'{v:.6f}' for v in e['oracle'])}]")
                print(f"        diffs_mm: [{', '.join(f'{d:.3f}' for d in e['diffs_mm'])}]")

    return match_count, mismatch_count, unresolved_count, max_error


def main():
    parser = argparse.ArgumentParser(description="Phase RM-2: Shadow Validator")
    parser.add_argument('--building', default='all',
                        help='Building: Ifc4_SampleHouse, Ifc2x3_Duplex, or all')
    parser.add_argument('--tolerance', type=float, default=0.01,
                        help='Tolerance in mm (default: 0.01)')
    args = parser.parse_args()

    global TOLERANCE_MM
    TOLERANCE_MM = args.tolerance

    conn = sqlite3.connect(DB_PATH)

    buildings = []
    if args.building == 'all':
        buildings = ['Ifc4_SampleHouse', 'Ifc2x3_Duplex']
    else:
        buildings = [args.building]

    total_match = 0
    total_mismatch = 0
    total_unresolved = 0
    overall_max_error = 0

    for bt in buildings:
        m, mm, u, me = validate_building(conn, bt)
        total_match += m
        total_mismatch += mm
        total_unresolved += u
        overall_max_error = max(overall_max_error, me)

    print(f"\n{'='*60}")
    print(f"OVERALL SUMMARY")
    print(f"{'='*60}")
    total = total_match + total_mismatch + total_unresolved
    print(f"  Matched: {total_match}/{total} ({total_match/max(total,1)*100:.1f}%)")
    print(f"  Mismatched: {total_mismatch}")
    print(f"  Unresolved: {total_unresolved}")
    print(f"  Max error: {overall_max_error*1000:.3f}mm")

    if total_mismatch == 0 and total_unresolved == 0:
        print(f"\n  *** ALL ELEMENTS MATCH WITHIN {TOLERANCE_MM}mm ***")

    conn.close()


if __name__ == '__main__':
    main()
