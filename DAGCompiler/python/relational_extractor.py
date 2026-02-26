#!/usr/bin/env python3
"""
Phase RM-1: Relational Extractor
Extracts grid lines, room boundaries, wall faces, and element rules
from ad_element_placement (flat coordinates) into the 5 relational tables.

Input:  BOM.db (ad_element_placement rows)
Output: populates ad_building_grid, ad_room_boundary, ad_wall_face,
        c_orderline, ad_element_dependency

Scope: SampleHouse + Duplex only (Terminal deferred).

Usage:
    python3 relational_extractor.py [--building Ifc4_SampleHouse|Ifc2x3_Duplex|all]
"""

import sqlite3
import argparse
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Optional

DB_PATH = "library/BOM.db"

# Tolerance for clustering wall positions into grid lines (mm converted to m)
GRID_CLUSTER_TOL = 0.05  # 50mm — walls within 50mm share a grid line

# Tolerance for element-to-wall proximity
WALL_PROXIMITY_TOL = 0.15  # 150mm — element within 150mm of wall plane


@dataclass
class Element:
    """A single element from ad_element_placement."""
    placement_id: int
    building_type: str
    storey: str
    ifc_class: str
    element_ref: str
    ordinal: int
    min_x: float
    max_x: float
    min_y: float
    max_y: float
    min_z: float
    max_z: float
    orientation: Optional[str]
    discipline: str
    material_name: Optional[str]
    material_rgba: Optional[str]

    @property
    def cx(self): return (self.min_x + self.max_x) / 2
    @property
    def cy(self): return (self.min_y + self.max_y) / 2
    @property
    def cz(self): return (self.min_z + self.max_z) / 2
    @property
    def width(self): return self.max_x - self.min_x
    @property
    def depth(self): return self.max_y - self.min_y
    @property
    def height(self): return self.max_z - self.min_z


@dataclass
class GridLine:
    axis: str        # 'X' or 'Y'
    label: str       # 'A', 'B', '1', '2' ...
    position: float  # metres


@dataclass
class RoomBoundary:
    room_name: str
    room_type: str
    storey: str
    min_x: float
    max_x: float
    min_y: float
    max_y: float
    grid_min_x: str = ""
    grid_max_x: str = ""
    grid_min_y: str = ""
    grid_max_y: str = ""


@dataclass
class WallFace:
    room_name: str
    storey: str
    face: str            # NORTH, SOUTH, EAST, WEST
    wall_type_id: str
    is_exterior: bool
    adjacent_room: Optional[str]
    # geometry for element hosting
    x1: float = 0
    y1: float = 0
    x2: float = 0
    y2: float = 0


def load_elements(conn, building_type):
    """Load all active elements for a building."""
    sql = """
        SELECT placement_id, building_type, storey, ifc_class, element_ref,
               ordinal, min_x, max_x, min_y, max_y, min_z, max_z,
               orientation, discipline, material_name, material_rgba
        FROM ad_element_placement
        WHERE building_type = ? AND is_active = 1
        ORDER BY storey, ifc_class, ordinal
    """
    elements = []
    for row in conn.execute(sql, (building_type,)):
        elements.append(Element(*row))
    return elements


def cluster_positions(values, tol):
    """Cluster nearby values into representative positions."""
    if not values:
        return []
    sorted_vals = sorted(set(values))
    clusters = []
    current = [sorted_vals[0]]
    for v in sorted_vals[1:]:
        if v - current[-1] <= tol:
            current.append(v)
        else:
            clusters.append(sum(current) / len(current))
            current = [v]
    clusters.append(sum(current) / len(current))
    return clusters


def find_nearest_grid(position, grid_lines, axis):
    """Find the nearest grid line label for a position."""
    best = None
    best_dist = float('inf')
    for gl in grid_lines:
        if gl.axis != axis:
            continue
        dist = abs(gl.position - position)
        if dist < best_dist:
            best_dist = dist
            best = gl.label
    return best


def extract_grid(elements, building_type):
    """Extract grid lines from wall and slab positions."""
    # Collect all significant X and Y boundary positions from walls
    x_positions = []
    y_positions = []

    wall_classes = {'IfcWall', 'IfcWallStandardCase', 'IfcPlate'}
    slab_classes = {'IfcSlab'}

    for e in elements:
        if e.ifc_class in wall_classes:
            # Thin dimension = wall thickness, long dimension = wall extent
            w = e.width
            d = e.depth
            if w < 0.6:  # thin in X → NS wall → X position matters
                x_positions.append(e.min_x)
                x_positions.append(e.max_x)
            if d < 0.6:  # thin in Y → EW wall → Y position matters
                y_positions.append(e.min_y)
                y_positions.append(e.max_y)
            # Also collect wall endpoints for long dimension
            if w >= 0.6:
                x_positions.append(e.min_x)
                x_positions.append(e.max_x)
            if d >= 0.6:
                y_positions.append(e.min_y)
                y_positions.append(e.max_y)

    # Cluster into grid lines
    x_clusters = cluster_positions(x_positions, GRID_CLUSTER_TOL)
    y_clusters = cluster_positions(y_positions, GRID_CLUSTER_TOL)

    # Label grid lines
    grid_lines = []
    for i, x in enumerate(x_clusters):
        label = chr(ord('A') + i) if i < 26 else f"X{i}"
        grid_lines.append(GridLine('X', label, x))
    for i, y in enumerate(y_clusters):
        label = str(i + 1)
        grid_lines.append(GridLine('Y', label, y))

    print(f"  [{building_type}] Grid: {len(x_clusters)} X-lines, {len(y_clusters)} Y-lines")
    for gl in grid_lines:
        print(f"    {gl.axis}-{gl.label}: {gl.position:.3f}m ({gl.position*1000:.0f}mm)")

    return grid_lines


def extract_rooms(elements, grid_lines, building_type):
    """
    Extract room boundaries from wall positions.
    Rooms are rectangular regions bounded by walls.
    """
    # Group walls by storey
    wall_classes = {'IfcWall', 'IfcWallStandardCase', 'IfcPlate'}
    walls_by_storey = defaultdict(list)
    for e in elements:
        if e.ifc_class in wall_classes:
            walls_by_storey[e.storey].append(e)

    rooms = []

    for storey, walls in walls_by_storey.items():
        if storey in ('Unknown', 'Roof', 'T/FDN'):
            continue

        # Collect wall-center X and Y positions for NS and EW walls
        ns_wall_x = []  # X positions of north-south walls
        ew_wall_y = []  # Y positions of east-west walls

        for w in walls:
            if w.width < w.depth:  # thin in X → NS wall
                ns_wall_x.append((w.min_x + w.max_x) / 2)
            else:  # thin in Y → EW wall
                ew_wall_y.append((w.min_y + w.max_y) / 2)

        # Cluster wall centers
        x_boundaries = sorted(set(cluster_positions(ns_wall_x, GRID_CLUSTER_TOL)))
        y_boundaries = sorted(set(cluster_positions(ew_wall_y, GRID_CLUSTER_TOL)))

        if len(x_boundaries) < 2 or len(y_boundaries) < 2:
            print(f"  [{storey}] Not enough wall boundaries for rooms (X:{len(x_boundaries)}, Y:{len(y_boundaries)})")
            continue

        # Generate room cells from adjacent boundary pairs
        room_idx = 0
        for xi in range(len(x_boundaries) - 1):
            for yi in range(len(y_boundaries) - 1):
                min_x = x_boundaries[xi]
                max_x = x_boundaries[xi + 1]
                min_y = y_boundaries[yi]
                max_y = y_boundaries[yi + 1]

                # Skip tiny cells (< 1m in any dimension — likely wall gaps)
                if max_x - min_x < 1.0 or max_y - min_y < 1.0:
                    continue

                room_idx += 1
                room_name = f"ROOM_{storey.replace(' ', '_')}_{room_idx}"

                # Determine room type from furniture/elements inside
                room_type = classify_room(elements, min_x, max_x, min_y, max_y, storey)

                rb = RoomBoundary(
                    room_name=room_name,
                    room_type=room_type,
                    storey=storey,
                    min_x=min_x, max_x=max_x,
                    min_y=min_y, max_y=max_y,
                )
                # Map to grid labels
                rb.grid_min_x = find_nearest_grid(min_x, grid_lines, 'X') or 'A'
                rb.grid_max_x = find_nearest_grid(max_x, grid_lines, 'X') or 'B'
                rb.grid_min_y = find_nearest_grid(min_y, grid_lines, 'Y') or '1'
                rb.grid_max_y = find_nearest_grid(max_y, grid_lines, 'Y') or '2'

                rooms.append(rb)

    print(f"  [{building_type}] Rooms: {len(rooms)} extracted")
    for r in rooms:
        print(f"    {r.room_name} ({r.room_type}): "
              f"X[{r.min_x:.2f}, {r.max_x:.2f}] Y[{r.min_y:.2f}, {r.max_y:.2f}] "
              f"grid [{r.grid_min_x}-{r.grid_max_x}, {r.grid_min_y}-{r.grid_max_y}]")

    return rooms


def classify_room(elements, min_x, max_x, min_y, max_y, storey):
    """Classify room type based on contained elements."""
    contained = []
    for e in elements:
        if e.storey != storey:
            continue
        # Element center inside room bounds (with margin)
        margin = 0.1
        if (min_x - margin <= e.cx <= max_x + margin and
            min_y - margin <= e.cy <= max_y + margin):
            contained.append(e)

    # Check element refs for clues
    refs = ' '.join(e.element_ref.lower() for e in contained)
    if 'bed' in refs:
        return 'BEDROOM'
    if 'couch' in refs or 'sofa' in refs or 'chair_viper' in refs:
        return 'LIVING'
    if 'dining' in refs or 'table_dining' in refs:
        return 'DINING'
    if 'desk' in refs or 'piano' in refs:
        return 'STUDY'
    if 'kitchen' in refs or 'cabinet' in refs:
        return 'KITCHEN'
    if 'toilet' in refs or 'bath' in refs or 'wc' in refs:
        return 'BATHROOM'
    return 'ROOM'


def extract_wall_faces(rooms, elements, building_type):
    """Extract wall faces for each room boundary."""
    wall_classes = {'IfcWall', 'IfcWallStandardCase', 'IfcPlate'}
    walls = [e for e in elements if e.ifc_class in wall_classes]

    # Building envelope from all elements
    all_x = [e.min_x for e in elements] + [e.max_x for e in elements]
    all_y = [e.min_y for e in elements] + [e.max_y for e in elements]
    env_min_x = min(all_x)
    env_max_x = max(all_x)
    env_min_y = min(all_y)
    env_max_y = max(all_y)

    faces = []
    for room in rooms:
        for face_dir in ['NORTH', 'SOUTH', 'EAST', 'WEST']:
            # Determine face position
            if face_dir == 'NORTH':
                face_pos = room.max_y
                axis = 'Y'
            elif face_dir == 'SOUTH':
                face_pos = room.min_y
                axis = 'Y'
            elif face_dir == 'EAST':
                face_pos = room.max_x
                axis = 'X'
            else:  # WEST
                face_pos = room.min_x
                axis = 'X'

            # Is this face on the building envelope?
            is_ext = False
            if axis == 'X':
                is_ext = abs(face_pos - env_min_x) < 0.5 or abs(face_pos - env_max_x) < 0.5
            else:
                is_ext = abs(face_pos - env_min_y) < 0.5 or abs(face_pos - env_max_y) < 0.5

            # Find adjacent room
            adjacent = None
            if not is_ext:
                for other in rooms:
                    if other.room_name == room.room_name or other.storey != room.storey:
                        continue
                    if face_dir == 'NORTH' and abs(other.min_y - room.max_y) < 0.3:
                        adjacent = other.room_name
                        break
                    if face_dir == 'SOUTH' and abs(other.max_y - room.min_y) < 0.3:
                        adjacent = other.room_name
                        break
                    if face_dir == 'EAST' and abs(other.min_x - room.max_x) < 0.3:
                        adjacent = other.room_name
                        break
                    if face_dir == 'WEST' and abs(other.max_x - room.min_x) < 0.3:
                        adjacent = other.room_name
                        break

            # Find matching wall from element data
            wall_type_id = 'INTERIOR_PARTITION_92'  # default interior
            if is_ext:
                wall_type_id = 'EXTERIOR_BRICK'

            # Try to match a specific wall element at this face
            for w in walls:
                if axis == 'Y':
                    if w.depth < 0.6 and abs((w.min_y + w.max_y) / 2 - face_pos) < 0.3:
                        wall_type_id = infer_wall_type(w)
                        break
                else:
                    if w.width < 0.6 and abs((w.min_x + w.max_x) / 2 - face_pos) < 0.3:
                        wall_type_id = infer_wall_type(w)
                        break

            # Compute wall segment geometry
            if face_dir in ('NORTH', 'SOUTH'):
                x1, y1 = room.min_x, face_pos
                x2, y2 = room.max_x, face_pos
            else:
                x1, y1 = face_pos, room.min_y
                x2, y2 = face_pos, room.max_y

            faces.append(WallFace(
                room_name=room.room_name,
                storey=room.storey,
                face=face_dir,
                wall_type_id=wall_type_id,
                is_exterior=is_ext,
                adjacent_room=adjacent,
                x1=x1, y1=y1, x2=x2, y2=y2,
            ))

    print(f"  [{building_type}] Wall faces: {len(faces)}")
    ext_count = sum(1 for f in faces if f.is_exterior)
    int_count = len(faces) - ext_count
    print(f"    Exterior: {ext_count}, Interior: {int_count}")
    return faces


def infer_wall_type(wall_elem):
    """Infer wall_type_id from element_ref string."""
    ref = wall_elem.element_ref.lower()
    if 'exterior' in ref or 'brick' in ref:
        return 'EXTERIOR_BRICK'
    if 'party' in ref or 'cmu' in ref or 'dimising' in ref:
        return 'PARTY_CMU'
    if 'furring' in ref:
        thickness = wall_elem.width if wall_elem.width < wall_elem.depth else wall_elem.depth
        if thickness * 1000 > 100:
            return 'INTERIOR_FURRING_152'
        return 'INTERIOR_FURRING_38'
    if 'partition' in ref or 'partn' in ref:
        return 'INTERIOR_PARTITION_92'
    if 'plumbing' in ref:
        return 'INTERIOR_PLUMBING_152'
    if 'foundation' in ref:
        return 'FOUNDATION_417'
    if 'glazed' in ref or 'curtain' in ref:
        return 'EXTERIOR_UK_BRICK_290'  # placeholder for curtain wall
    return 'INTERIOR_PARTITION_92'


def extract_element_rules(elements, rooms, wall_faces, building_type):
    """Extract element placement rules from flat coordinates."""
    wall_classes = {'IfcWall', 'IfcWallStandardCase', 'IfcPlate', 'IfcMember'}
    slab_classes = {'IfcSlab'}
    roof_classes = {'IfcRoof'}
    opening_classes = {'IfcDoor', 'IfcWindow'}

    rules = []
    deps = []

    for e in elements:
        if e.storey in ('Unknown',):
            # Members/plates with Unknown storey — try to assign
            assigned_storey = assign_storey(e, elements)
            storey = assigned_storey or e.storey
        else:
            storey = e.storey

        # Unique element key (placement_id guarantees uniqueness)
        elem_key = f"{e.ifc_class}_{e.placement_id}"

        if e.ifc_class in wall_classes:
            # Walls are hosted on the building/room boundary
            host_room = find_containing_room(e, rooms)
            host_ref = host_room.room_name if host_room else 'BUILDING'
            host_type = 'ROOM' if host_room else 'BUILDING'

            rules.append({
                'building_type': building_type,
                'storey': storey,
                'element_ref': elem_key,
                'ifc_class': e.ifc_class,
                'discipline': e.discipline or 'ARC',
                'host_type': host_type,
                'host_ref': host_ref,
                'position_rule': 'BOUNDARY',
                'position_value': e.cx * 1000,   # center X in mm
                'position_value_2': e.cy * 1000,  # center Y in mm
                'height_mm': e.min_z * 1000,
                'family_ref': e.element_ref,
                'width_mm': e.width * 1000,
                'height_extent_mm': e.height * 1000,
                'depth_mm': e.depth * 1000,
                'orientation': e.orientation,
                'geometry_hash': None,
                'material_name': e.material_name,
                'material_rgba': e.material_rgba,
            })

        elif e.ifc_class in opening_classes:
            # Doors/windows are hosted on walls
            host_face = find_host_wall(e, wall_faces)
            if host_face:
                # Compute fractional position along wall
                wall_len = ((host_face.x2 - host_face.x1)**2 +
                            (host_face.y2 - host_face.y1)**2) ** 0.5

                # Check if element is wider than wall — if so, use ABSOLUTE
                elem_along_wall = max(e.width, e.depth) if host_face.face in ('NORTH', 'SOUTH') else max(e.width, e.depth)
                if wall_len > 0 and elem_along_wall > wall_len * 1.1:
                    # Element spans beyond this wall segment — use absolute center
                    rules.append(make_building_hosted_rule(e, elem_key, building_type, storey))
                else:
                    if wall_len > 0:
                        # Project element center onto wall line
                        dx = host_face.x2 - host_face.x1
                        dy = host_face.y2 - host_face.y1
                        t_raw = ((e.cx - host_face.x1) * dx + (e.cy - host_face.y1) * dy) / (wall_len * wall_len)
                        # If element center is far outside wall segment, use ABSOLUTE
                        if t_raw < -0.05 or t_raw > 1.05:
                            rules.append(make_building_hosted_rule(e, elem_key, building_type, storey))
                            continue
                        t = t_raw  # store raw fraction (may be slightly outside [0,1])
                    else:
                        t = 0.5

                    face_key = f"WALL_{host_face.room_name}_{host_face.face}"
                    sill_mm = e.min_z * 1000

                    # Compute perpendicular offset from wall plane
                    if host_face.face in ('NORTH', 'SOUTH'):
                        perp_offset_mm = (e.cy - host_face.y1) * 1000
                    else:
                        perp_offset_mm = (e.cx - host_face.x1) * 1000

                    rules.append({
                        'building_type': building_type,
                        'storey': storey,
                        'element_ref': elem_key,
                        'ifc_class': e.ifc_class,
                        'discipline': e.discipline or 'ARC',
                        'host_type': 'WALL',
                        'host_ref': face_key,
                        'position_rule': 'FRACTION',
                        'position_value': round(t, 8),
                        'position_value_2': round(perp_offset_mm, 3),
                        'height_mm': sill_mm,
                        'family_ref': e.element_ref,
                        'width_mm': e.width * 1000,        # actual X extent
                        'height_extent_mm': e.height * 1000,
                        'depth_mm': e.depth * 1000,        # actual Y extent
                        'orientation': 'ALONG_HOST',
                        'geometry_hash': None,
                        'material_name': e.material_name,
                        'material_rgba': e.material_rgba,
                    })

                    deps.append({
                        'building_type': building_type,
                        'element_ref': elem_key,
                        'parent_ref': face_key,
                        'relation': 'HOSTED_ON',
                        'cascade_rule': 'MOVE',
                    })
            else:
                # Fallback: opening not matched to any wall
                rules.append(make_building_hosted_rule(e, elem_key, building_type, storey))

        elif e.ifc_class in slab_classes:
            rules.append({
                'building_type': building_type,
                'storey': storey,
                'element_ref': elem_key,
                'ifc_class': e.ifc_class,
                'discipline': e.discipline or 'ARC',
                'host_type': 'BUILDING',
                'host_ref': 'BUILDING',
                'position_rule': 'ENVELOPE',
                'position_value': e.cx * 1000,
                'position_value_2': e.cy * 1000,
                'height_mm': e.min_z * 1000,
                'family_ref': e.element_ref,
                'width_mm': e.width * 1000,
                'height_extent_mm': e.height * 1000,
                'depth_mm': e.depth * 1000,
                'orientation': None,
                'geometry_hash': None,
                'material_name': e.material_name,
                'material_rgba': e.material_rgba,
            })

        elif e.ifc_class in roof_classes:
            rules.append({
                'building_type': building_type,
                'storey': storey,
                'element_ref': elem_key,
                'ifc_class': e.ifc_class,
                'discipline': e.discipline or 'ARC',
                'host_type': 'BUILDING',
                'host_ref': 'BUILDING',
                'position_rule': 'ENVELOPE',
                'position_value': e.cx * 1000,
                'position_value_2': e.cy * 1000,
                'height_mm': e.min_z * 1000,
                'family_ref': e.element_ref,
                'width_mm': e.width * 1000,
                'height_extent_mm': e.height * 1000,
                'depth_mm': e.depth * 1000,
                'orientation': None,
                'geometry_hash': None,
                'material_name': e.material_name,
                'material_rgba': e.material_rgba,
            })

        else:
            # Furniture, MEP, etc. — hosted in room
            host_room = find_containing_room(e, rooms)
            if host_room:
                # Position as fraction within room
                rx = (e.cx - host_room.min_x) / max(host_room.max_x - host_room.min_x, 0.01)
                ry = (e.cy - host_room.min_y) / max(host_room.max_y - host_room.min_y, 0.01)

                rules.append({
                    'building_type': building_type,
                    'storey': storey,
                    'element_ref': elem_key,
                    'ifc_class': e.ifc_class,
                    'discipline': e.discipline or 'ARC',
                    'host_type': 'ROOM',
                    'host_ref': host_room.room_name,
                    'position_rule': 'FRACTION',
                    'position_value': round(rx, 8),
                    'position_value_2': round(ry, 8),
                    'height_mm': e.min_z * 1000,
                    'family_ref': e.element_ref,
                    'width_mm': e.width * 1000,
                    'height_extent_mm': e.height * 1000,
                    'depth_mm': e.depth * 1000,
                    'orientation': e.orientation,
                    'geometry_hash': None,
                    'material_name': e.material_name,
                    'material_rgba': e.material_rgba,
                })

                deps.append({
                    'building_type': building_type,
                    'element_ref': elem_key,
                    'parent_ref': host_room.room_name,
                    'relation': 'CONTAINED_IN',
                    'cascade_rule': 'MOVE',
                })
            else:
                rules.append(make_building_hosted_rule(e, elem_key, building_type, storey))

    print(f"  [{building_type}] Element rules: {len(rules)}, Dependencies: {len(deps)}")

    # Stats by host type
    host_stats = defaultdict(int)
    for r in rules:
        host_stats[r['host_type']] += 1
    for ht, cnt in sorted(host_stats.items()):
        print(f"    {ht}: {cnt}")

    return rules, deps


def make_building_hosted_rule(e, elem_key, building_type, storey):
    """Fallback rule for elements not matched to a room or wall."""
    return {
        'building_type': building_type,
        'storey': storey,
        'element_ref': elem_key,
        'ifc_class': e.ifc_class,
        'discipline': e.discipline or 'ARC',
        'host_type': 'BUILDING',
        'host_ref': 'BUILDING',
        'position_rule': 'ABSOLUTE',
        'position_value': e.cx * 1000,      # center X in mm
        'position_value_2': e.cy * 1000,    # center Y in mm
        'height_mm': e.min_z * 1000,
        'family_ref': e.element_ref,
        'width_mm': e.width * 1000,
        'height_extent_mm': e.height * 1000,
        'depth_mm': e.depth * 1000,
        'orientation': e.orientation,
        'geometry_hash': None,
        'material_name': e.material_name,
        'material_rgba': e.material_rgba,
    }


def find_containing_room(element, rooms):
    """Find which room contains this element's center."""
    best = None
    best_area = float('inf')
    for room in rooms:
        if room.storey != element.storey:
            continue
        margin = 0.2  # 200mm tolerance
        if (room.min_x - margin <= element.cx <= room.max_x + margin and
            room.min_y - margin <= element.cy <= room.max_y + margin):
            area = (room.max_x - room.min_x) * (room.max_y - room.min_y)
            if area < best_area:
                best = room
                best_area = area
    return best


def find_host_wall(element, wall_faces):
    """Find which wall face hosts this opening element."""
    best = None
    best_dist = float('inf')

    for wf in wall_faces:
        if wf.storey != element.storey:
            continue
        # Check proximity of element to wall plane
        if wf.face in ('NORTH', 'SOUTH'):
            dist = abs(element.cy - wf.y1)
            # Element must be within wall's X extent
            overlap = min(element.max_x, wf.x2) - max(element.min_x, wf.x1)
            if overlap < 0:
                continue
        else:  # EAST, WEST
            dist = abs(element.cx - wf.x1)
            overlap = min(element.max_y, wf.y2) - max(element.min_y, wf.y1)
            if overlap < 0:
                continue

        if dist < WALL_PROXIMITY_TOL and dist < best_dist:
            best_dist = dist
            best = wf

    return best


def assign_storey(element, all_elements):
    """Assign storey to elements with 'Unknown' storey based on Z position."""
    # Find storey Z ranges from known elements
    storey_z = defaultdict(list)
    for e in all_elements:
        if e.storey not in ('Unknown', 'Roof', 'T/FDN'):
            storey_z[e.storey].append(e.min_z)
            storey_z[e.storey].append(e.max_z)

    if not storey_z:
        return None

    # Find best matching storey by Z overlap
    for storey, z_vals in storey_z.items():
        min_z = min(z_vals)
        max_z = max(z_vals)
        if min_z - 0.5 <= element.cz <= max_z + 0.5:
            return storey
    return None


def write_results(conn, building_type, grid_lines, rooms, wall_faces, rules, deps):
    """Write extracted data to relational tables."""
    # Clear existing data for this building
    for table in ['ad_building_grid', 'ad_room_boundary', 'ad_wall_face',
                  'c_orderline', 'ad_element_dependency']:
        conn.execute(f"DELETE FROM {table} WHERE building_type = ?", (building_type,))

    # Grid lines
    for gl in grid_lines:
        conn.execute(
            "INSERT INTO ad_building_grid (building_type, axis, grid_label, position_mm) VALUES (?,?,?,?)",
            (building_type, gl.axis, gl.label, gl.position * 1000))

    # Room boundaries (include exact coords for lossless round-trip)
    for r in rooms:
        conn.execute(
            """INSERT INTO ad_room_boundary
               (building_type, storey, room_name, room_type,
                grid_min_x, grid_max_x, grid_min_y, grid_max_y,
                min_x_mm, max_x_mm, min_y_mm, max_y_mm, z_offset_mm)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (building_type, r.storey, r.room_name, r.room_type,
             r.grid_min_x, r.grid_max_x, r.grid_min_y, r.grid_max_y,
             r.min_x * 1000, r.max_x * 1000, r.min_y * 1000, r.max_y * 1000, 0))

    # Wall faces
    for wf in wall_faces:
        conn.execute(
            """INSERT INTO ad_wall_face
               (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room)
               VALUES (?,?,?,?,?,?,?)""",
            (building_type, wf.storey, wf.room_name, wf.face,
             wf.wall_type_id, 1 if wf.is_exterior else 0, wf.adjacent_room))

    # Element rules
    for r in rules:
        conn.execute(
            """INSERT INTO c_orderline
               (building_type, storey, element_ref, ifc_class, discipline,
                host_type, host_ref, position_rule, position_value, position_value_2, height_mm,
                family_ref, width_mm, height_extent_mm, depth_mm,
                orientation, geometry_hash, material_name, material_rgba)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (r['building_type'], r['storey'], r['element_ref'], r['ifc_class'],
             r['discipline'], r['host_type'], r['host_ref'],
             r['position_rule'], r['position_value'], r.get('position_value_2'),
             r['height_mm'],
             r['family_ref'], r['width_mm'], r['height_extent_mm'], r['depth_mm'],
             r['orientation'], r['geometry_hash'],
             r['material_name'], r['material_rgba']))

    # Dependencies
    for d in deps:
        conn.execute(
            """INSERT INTO ad_element_dependency
               (building_type, element_ref, parent_ref, relation, cascade_rule)
               VALUES (?,?,?,?,?)""",
            (d['building_type'], d['element_ref'], d['parent_ref'],
             d['relation'], d['cascade_rule']))

    conn.commit()
    print(f"  [{building_type}] Written: {len(grid_lines)} grid lines, {len(rooms)} rooms, "
          f"{len(wall_faces)} wall faces, {len(rules)} element rules, {len(deps)} dependencies")


def extract_building(conn, building_type):
    """Full extraction pipeline for one building."""
    print(f"\n{'='*60}")
    print(f"Extracting: {building_type}")
    print(f"{'='*60}")

    elements = load_elements(conn, building_type)
    print(f"  Loaded {len(elements)} elements")

    if not elements:
        print(f"  No elements found — skipping")
        return

    # Step 1: Grid
    grid_lines = extract_grid(elements, building_type)

    # Step 2: Rooms
    rooms = extract_rooms(elements, grid_lines, building_type)

    # Step 3: Wall faces
    wall_faces = extract_wall_faces(rooms, elements, building_type)

    # Step 4: Element rules + dependencies
    rules, deps = extract_element_rules(elements, rooms, wall_faces, building_type)

    # Write
    write_results(conn, building_type, grid_lines, rooms, wall_faces, rules, deps)


def main():
    parser = argparse.ArgumentParser(description="Phase RM-1: Relational Extractor")
    parser.add_argument('--building', default='all',
                        help='Building to extract: Ifc4_SampleHouse, Ifc2x3_Duplex, or all')
    args = parser.parse_args()

    conn = sqlite3.connect(DB_PATH)

    buildings = []
    if args.building == 'all':
        buildings = ['Ifc4_SampleHouse', 'Ifc2x3_Duplex']
    else:
        buildings = [args.building]

    for bt in buildings:
        extract_building(conn, bt)

    # Summary
    print(f"\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")
    for table in ['ad_building_grid', 'ad_room_boundary', 'ad_wall_face',
                  'c_orderline', 'ad_element_dependency']:
        count = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        print(f"  {table}: {count} rows")

    conn.close()
    print("\nDone.")


if __name__ == '__main__':
    main()
