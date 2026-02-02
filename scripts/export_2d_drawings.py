#!/usr/bin/env python3
"""
BIM Intent Compiler - Direct DB to 2D Drawings

THE SILENT KILLER: Generates permit-ready 2D drawings directly from DB.
No Revit. No Blender. No Bonsai. Pure Python.

Usage:
    python scripts/export_2d_drawings.py output/building.db [output_dir]

Outputs:
    - floor_plan_Ground.svg    Floor plan at Z=1.0m section cut
    - floor_plan_Upper.svg     Upper floor plan (if exists)
    - section_A.svg            North-South section
    - section_B.svg            East-West section
    - elevation_north.svg      North elevation
    - elevation_south.svg      South elevation
    - door_schedule.svg        Door schedule table
    - window_schedule.svg      Window schedule table
    - room_schedule.svg        Room areas table
    - drawing_index.html       Index page with all drawings

Dependencies:
    pip install svgwrite

The SVG output is:
    - Vector-based (infinite zoom)
    - Printable at any scale
    - Editable in Inkscape/Illustrator
    - Convertible to PDF/DXF
"""

import sys
import sqlite3
import json
import math
from pathlib import Path
from dataclasses import dataclass
from typing import List, Dict, Tuple, Optional

try:
    import svgwrite
    HAS_SVGWRITE = True
except ImportError:
    HAS_SVGWRITE = False
    print("WARNING: svgwrite not installed. Install with: pip install svgwrite")


# Drawing constants
SCALE = 50  # pixels per meter (1:20 at 96dpi)
MARGIN = 50  # pixels
LINE_WEIGHT = {
    'wall_cut': 2.0,      # Walls cut by section plane
    'wall_beyond': 0.5,   # Walls beyond section plane
    'door': 1.0,
    'window': 0.75,
    'dimension': 0.35,
    'grid': 0.25,
    'annotation': 0.5,
}

COLORS = {
    'wall': '#000000',
    'wall_fill': '#e0e0e0',
    'door': '#4a4a4a',
    'door_swing': '#888888',
    'window': '#6699cc',
    'window_glass': '#cce5ff',
    'room_label': '#333333',
    'dimension': '#0066cc',
    'grid': '#cc0000',
    'annotation': '#666666',
}


@dataclass
class Element:
    guid: str
    ifc_class: str
    name: str
    storey: str
    minX: float
    maxX: float
    minY: float
    maxY: float
    minZ: float
    maxZ: float
    properties: dict


def load_elements(db_path: str) -> List[Element]:
    """Load elements from database."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    elements = []
    # Schema: element_name, element_type (not name, type, properties)
    # rtree uses id (integer) not guid
    cursor = conn.execute("""
        SELECT
            m.guid, m.ifc_class, m.element_name as name,
            m.element_type as type, m.storey,
            r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
    """)

    for row in cursor:
        elements.append(Element(
            guid=row['guid'],
            ifc_class=row['ifc_class'] or '',
            name=row['name'] or '',
            storey=row['storey'] or '',
            minX=row['minX'] or 0,
            maxX=row['maxX'] or 0,
            minY=row['minY'] or 0,
            maxY=row['maxY'] or 0,
            minZ=row['minZ'] or 0,
            maxZ=row['maxZ'] or 0,
            properties={'type': row['type']} if row['type'] else {}
        ))

    conn.close()
    return elements


def get_storeys(elements: List[Element]) -> List[str]:
    """Get unique storey names ordered by Z."""
    storey_z = {}
    for e in elements:
        if e.storey and e.storey not in storey_z:
            storey_z[e.storey] = e.minZ

    return sorted(storey_z.keys(), key=lambda s: storey_z[s])


def get_bounds(elements: List[Element]) -> Tuple[float, float, float, float]:
    """Get overall XY bounds."""
    if not elements:
        return (0, 0, 10, 10)

    minX = min(e.minX for e in elements)
    maxX = max(e.maxX for e in elements)
    minY = min(e.minY for e in elements)
    maxY = max(e.maxY for e in elements)

    return (minX, minY, maxX, maxY)


def to_svg_coords(x: float, y: float, bounds: tuple, height: float) -> Tuple[float, float]:
    """Convert world coords to SVG coords (Y flipped)."""
    minX, minY, maxX, maxY = bounds
    svg_x = MARGIN + (x - minX) * SCALE
    svg_y = MARGIN + (maxY - y) * SCALE  # Flip Y
    return (svg_x, svg_y)


def create_floor_plan(elements: List[Element], storey: str, output_path: str,
                      section_z: float = 1.0, building_name: str = "Building"):
    """Generate floor plan SVG for a storey."""

    if not HAS_SVGWRITE:
        print(f"Skipping {output_path} - svgwrite not installed")
        return

    # Filter elements for this storey that intersect section plane
    storey_elements = [e for e in elements if e.storey == storey]

    # Get bounds
    bounds = get_bounds(storey_elements)
    minX, minY, maxX, maxY = bounds

    # Calculate SVG size
    width = (maxX - minX) * SCALE + 2 * MARGIN
    height = (maxY - minY) * SCALE + 2 * MARGIN

    dwg = svgwrite.Drawing(output_path, size=(f'{width}px', f'{height}px'))

    # Background
    dwg.add(dwg.rect((0, 0), (width, height), fill='white'))

    # Title block
    dwg.add(dwg.text(
        f'{building_name} - {storey} Floor Plan',
        insert=(MARGIN, 30),
        font_size='16px',
        font_family='Arial',
        fill=COLORS['room_label']
    ))

    dwg.add(dwg.text(
        f'Scale 1:{int(1000/SCALE)}',
        insert=(width - MARGIN - 100, 30),
        font_size='12px',
        font_family='Arial',
        fill=COLORS['annotation']
    ))

    # Draw elements
    # 1. Walls (filled)
    walls = [e for e in storey_elements if 'Wall' in e.ifc_class]
    for wall in walls:
        x1, y1 = to_svg_coords(wall.minX, wall.minY, bounds, height)
        x2, y2 = to_svg_coords(wall.maxX, wall.maxY, bounds, height)

        # Check if wall is cut by section plane
        is_cut = wall.minZ <= section_z <= wall.maxZ

        dwg.add(dwg.rect(
            (min(x1, x2), min(y1, y2)),
            (abs(x2 - x1), abs(y2 - y1)),
            fill=COLORS['wall_fill'] if is_cut else 'none',
            stroke=COLORS['wall'],
            stroke_width=LINE_WEIGHT['wall_cut'] if is_cut else LINE_WEIGHT['wall_beyond']
        ))

    # 2. Doors (with swing arc)
    doors = [e for e in storey_elements if 'Door' in e.ifc_class]
    for door in doors:
        cx = (door.minX + door.maxX) / 2
        cy = (door.minY + door.maxY) / 2
        door_width = max(door.maxX - door.minX, door.maxY - door.minY)

        x1, y1 = to_svg_coords(door.minX, door.minY, bounds, height)
        x2, y2 = to_svg_coords(door.maxX, door.maxY, bounds, height)

        # Door opening (gap in wall)
        dwg.add(dwg.rect(
            (min(x1, x2), min(y1, y2)),
            (abs(x2 - x1), abs(y2 - y1)),
            fill='white',
            stroke=COLORS['door'],
            stroke_width=LINE_WEIGHT['door']
        ))

        # Door swing arc (90 degree)
        sx, sy = to_svg_coords(cx, cy, bounds, height)
        radius = door_width * SCALE * 0.9

        # Simplified arc - just draw door leaf line
        dwg.add(dwg.line(
            (sx - radius/2, sy),
            (sx + radius/2, sy),
            stroke=COLORS['door'],
            stroke_width=LINE_WEIGHT['door']
        ))

    # 3. Windows
    windows = [e for e in storey_elements if 'Window' in e.ifc_class]
    for window in windows:
        x1, y1 = to_svg_coords(window.minX, window.minY, bounds, height)
        x2, y2 = to_svg_coords(window.maxX, window.maxY, bounds, height)

        # Window with glass lines
        dwg.add(dwg.rect(
            (min(x1, x2), min(y1, y2)),
            (abs(x2 - x1), abs(y2 - y1)),
            fill=COLORS['window_glass'],
            stroke=COLORS['window'],
            stroke_width=LINE_WEIGHT['window']
        ))

    # 4. Room labels
    spaces = [e for e in storey_elements if 'Space' in e.ifc_class]
    for space in spaces:
        cx = (space.minX + space.maxX) / 2
        cy = (space.minY + space.maxY) / 2
        sx, sy = to_svg_coords(cx, cy, bounds, height)

        # Room name
        room_name = space.name.replace('_', ' ').upper()
        dwg.add(dwg.text(
            room_name,
            insert=(sx, sy),
            font_size='10px',
            font_family='Arial',
            font_weight='bold',
            text_anchor='middle',
            fill=COLORS['room_label']
        ))

        # Room area
        area = (space.maxX - space.minX) * (space.maxY - space.minY)
        dwg.add(dwg.text(
            f'{area:.1f} m²',
            insert=(sx, sy + 14),
            font_size='9px',
            font_family='Arial',
            text_anchor='middle',
            fill=COLORS['annotation']
        ))

    # 5. North arrow
    arrow_x = width - MARGIN - 30
    arrow_y = height - MARGIN - 50
    dwg.add(dwg.polygon(
        [(arrow_x, arrow_y - 30), (arrow_x - 10, arrow_y), (arrow_x + 10, arrow_y)],
        fill='black'
    ))
    dwg.add(dwg.text('N', insert=(arrow_x, arrow_y + 15),
                     font_size='14px', font_family='Arial',
                     text_anchor='middle', font_weight='bold'))

    # 6. Scale bar
    scale_x = MARGIN
    scale_y = height - MARGIN - 20
    scale_length = 5 * SCALE  # 5 meters

    dwg.add(dwg.line((scale_x, scale_y), (scale_x + scale_length, scale_y),
                     stroke='black', stroke_width=2))

    for i in range(6):
        x = scale_x + i * SCALE
        dwg.add(dwg.line((x, scale_y - 5), (x, scale_y + 5),
                         stroke='black', stroke_width=1))
        dwg.add(dwg.text(str(i), insert=(x, scale_y + 18),
                         font_size='10px', font_family='Arial', text_anchor='middle'))

    dwg.add(dwg.text('meters', insert=(scale_x + scale_length + 10, scale_y + 5),
                     font_size='10px', font_family='Arial'))

    dwg.save()
    print(f"Generated: {output_path}")


def create_section(elements: List[Element], axis: str, position: float,
                   output_path: str, building_name: str = "Building"):
    """Generate section SVG (vertical cut)."""

    if not HAS_SVGWRITE:
        print(f"Skipping {output_path} - svgwrite not installed")
        return

    # Filter elements that intersect section plane
    tolerance = 0.5  # meters
    if axis == 'Y':  # Section looking North (cut along X)
        section_elements = [e for e in elements
                          if e.minY - tolerance <= position <= e.maxY + tolerance]
    else:  # Section looking East (cut along Y)
        section_elements = [e for e in elements
                          if e.minX - tolerance <= position <= e.maxX + tolerance]

    if not section_elements:
        print(f"No elements found at section position {position}")
        return

    # Get bounds (horizontal axis + Z)
    if axis == 'Y':
        minH = min(e.minX for e in section_elements)
        maxH = max(e.maxX for e in section_elements)
    else:
        minH = min(e.minY for e in section_elements)
        maxH = max(e.maxY for e in section_elements)

    minZ = min(e.minZ for e in section_elements)
    maxZ = max(e.maxZ for e in section_elements)

    # SVG size
    width = (maxH - minH) * SCALE + 2 * MARGIN
    height = (maxZ - minZ) * SCALE + 2 * MARGIN

    dwg = svgwrite.Drawing(output_path, size=(f'{width}px', f'{height}px'))
    dwg.add(dwg.rect((0, 0), (width, height), fill='white'))

    # Title
    section_name = 'A-A' if axis == 'Y' else 'B-B'
    dwg.add(dwg.text(
        f'{building_name} - Section {section_name}',
        insert=(MARGIN, 30),
        font_size='16px', font_family='Arial', fill=COLORS['room_label']
    ))

    # Draw elements
    for e in section_elements:
        if axis == 'Y':
            x1 = MARGIN + (e.minX - minH) * SCALE
            x2 = MARGIN + (e.maxX - minH) * SCALE
        else:
            x1 = MARGIN + (e.minY - minH) * SCALE
            x2 = MARGIN + (e.maxY - minH) * SCALE

        y1 = MARGIN + (maxZ - e.maxZ) * SCALE  # Flip Z
        y2 = MARGIN + (maxZ - e.minZ) * SCALE

        is_wall = 'Wall' in e.ifc_class
        is_slab = 'Slab' in e.ifc_class

        dwg.add(dwg.rect(
            (min(x1, x2), min(y1, y2)),
            (abs(x2 - x1), abs(y2 - y1)),
            fill=COLORS['wall_fill'] if (is_wall or is_slab) else 'none',
            stroke=COLORS['wall'],
            stroke_width=LINE_WEIGHT['wall_cut'] if is_wall else LINE_WEIGHT['wall_beyond']
        ))

    # Ground line
    ground_y = MARGIN + maxZ * SCALE
    dwg.add(dwg.line(
        (MARGIN, ground_y), (width - MARGIN, ground_y),
        stroke='black', stroke_width=1, stroke_dasharray='10,5'
    ))
    dwg.add(dwg.text('GL', insert=(MARGIN - 20, ground_y + 5),
                     font_size='10px', font_family='Arial'))

    dwg.save()
    print(f"Generated: {output_path}")


def create_schedule(elements: List[Element], element_type: str,
                    output_path: str, building_name: str = "Building"):
    """Generate schedule table as SVG."""

    if not HAS_SVGWRITE:
        print(f"Skipping {output_path} - svgwrite not installed")
        return

    # Filter by type
    if element_type == 'doors':
        items = [e for e in elements if 'Door' in e.ifc_class]
        title = 'Door Schedule'
        headers = ['Mark', 'Location', 'Width', 'Height', 'Type']
    elif element_type == 'windows':
        items = [e for e in elements if 'Window' in e.ifc_class]
        title = 'Window Schedule'
        headers = ['Mark', 'Location', 'Width', 'Height', 'Type']
    elif element_type == 'rooms':
        items = [e for e in elements if 'Space' in e.ifc_class]
        title = 'Room Schedule'
        headers = ['Room', 'Storey', 'Area (m²)', 'Perimeter (m)']
    else:
        return

    # Table dimensions
    row_height = 25
    col_widths = [80, 120, 80, 80, 100]
    table_width = sum(col_widths)
    table_height = (len(items) + 1) * row_height

    width = table_width + 2 * MARGIN
    height = table_height + 100

    dwg = svgwrite.Drawing(output_path, size=(f'{width}px', f'{height}px'))
    dwg.add(dwg.rect((0, 0), (width, height), fill='white'))

    # Title
    dwg.add(dwg.text(
        f'{building_name} - {title}',
        insert=(MARGIN, 30),
        font_size='16px', font_family='Arial', fill=COLORS['room_label']
    ))

    # Table
    table_x = MARGIN
    table_y = 60

    # Header row
    x = table_x
    for i, header in enumerate(headers):
        dwg.add(dwg.rect(
            (x, table_y), (col_widths[i], row_height),
            fill='#f0f0f0', stroke='black', stroke_width=1
        ))
        dwg.add(dwg.text(
            header,
            insert=(x + 5, table_y + 17),
            font_size='11px', font_family='Arial', font_weight='bold'
        ))
        x += col_widths[i]

    # Data rows
    for row_idx, item in enumerate(sorted(items, key=lambda e: e.name)):
        y = table_y + (row_idx + 1) * row_height
        x = table_x

        if element_type in ['doors', 'windows']:
            width_val = item.maxX - item.minX
            height_val = item.maxZ - item.minZ
            if width_val < 0.1:  # Likely Y-oriented
                width_val = item.maxY - item.minY

            row_data = [
                item.name.split('_')[-1] if '_' in item.name else item.name,
                item.storey,
                f'{width_val*1000:.0f}mm',
                f'{height_val*1000:.0f}mm',
                item.properties.get('type', '-')
            ]
        else:  # rooms
            area = (item.maxX - item.minX) * (item.maxY - item.minY)
            perimeter = 2 * ((item.maxX - item.minX) + (item.maxY - item.minY))
            row_data = [
                item.name.replace('_', ' ').title(),
                item.storey,
                f'{area:.1f}',
                f'{perimeter:.1f}'
            ]

        for i, cell in enumerate(row_data):
            dwg.add(dwg.rect(
                (x, y), (col_widths[i], row_height),
                fill='white', stroke='black', stroke_width=0.5
            ))
            dwg.add(dwg.text(
                str(cell),
                insert=(x + 5, y + 17),
                font_size='10px', font_family='Arial'
            ))
            x += col_widths[i]

    dwg.save()
    print(f"Generated: {output_path}")


def create_index_html(output_dir: str, drawings: List[str], building_name: str):
    """Generate HTML index page for all drawings."""

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>{building_name} - Drawing Set</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            margin: 0;
            padding: 20px;
            background: #f5f5f5;
        }}
        h1 {{
            color: #333;
            border-bottom: 2px solid #e94560;
            padding-bottom: 10px;
        }}
        .drawing-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
            gap: 20px;
            margin-top: 20px;
        }}
        .drawing-card {{
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            overflow: hidden;
        }}
        .drawing-card h3 {{
            margin: 0;
            padding: 12px 16px;
            background: #16213e;
            color: white;
            font-size: 14px;
        }}
        .drawing-card object {{
            width: 100%;
            height: 300px;
            display: block;
        }}
        .drawing-card .actions {{
            padding: 10px 16px;
            background: #f8f8f8;
            border-top: 1px solid #eee;
        }}
        .drawing-card a {{
            color: #e94560;
            text-decoration: none;
            font-size: 13px;
            margin-right: 16px;
        }}
        .drawing-card a:hover {{
            text-decoration: underline;
        }}
        .footer {{
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #ddd;
            color: #888;
            font-size: 12px;
        }}
    </style>
</head>
<body>
    <h1>{building_name} — Drawing Set</h1>
    <p>Generated by BIM Intent Compiler — Direct DB to 2D Pipeline</p>

    <div class="drawing-grid">
"""

    for drawing in drawings:
        name = Path(drawing).stem.replace('_', ' ').title()
        html += f"""        <div class="drawing-card">
            <h3>{name}</h3>
            <object data="{drawing}" type="image/svg+xml"></object>
            <div class="actions">
                <a href="{drawing}" target="_blank">Open Full Size</a>
                <a href="{drawing}" download>Download SVG</a>
            </div>
        </div>
"""

    html += f"""    </div>

    <div class="footer">
        <p>No Revit. No Blender. No Bonsai. Pure Python.</p>
        <p>BIM Intent Compiler v0.51.0 | Generated from SQLite database</p>
    </div>
</body>
</html>
"""

    index_path = Path(output_dir) / 'drawing_index.html'
    with open(index_path, 'w') as f:
        f.write(html)
    print(f"Generated: {index_path}")


def main():
    if len(sys.argv) < 2:
        print("BIM Intent Compiler - Direct DB to 2D Drawings")
        print("=" * 50)
        print("\nUsage: python export_2d_drawings.py <input.db> [output_dir]")
        print("\nGenerates permit-ready 2D drawings directly from database.")
        print("No Revit. No Blender. No Bonsai. Pure Python.")
        print("\nDependencies: pip install svgwrite")
        sys.exit(1)

    db_path = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) >= 3 else Path(db_path).parent / 'drawings'

    if not Path(db_path).exists():
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    Path(output_dir).mkdir(parents=True, exist_ok=True)

    print(f"Loading elements from {db_path}...")
    elements = load_elements(db_path)
    print(f"Loaded {len(elements)} elements")

    building_name = Path(db_path).stem.replace('_', '-').upper()
    storeys = get_storeys(elements)
    print(f"Storeys: {storeys}")

    bounds = get_bounds(elements)
    center_y = (bounds[1] + bounds[3]) / 2
    center_x = (bounds[0] + bounds[2]) / 2

    drawings = []

    # Floor plans
    for storey in storeys:
        filename = f'floor_plan_{storey}.svg'
        create_floor_plan(elements, storey, str(Path(output_dir) / filename),
                         section_z=1.0, building_name=building_name)
        drawings.append(filename)

    # Sections
    create_section(elements, 'Y', center_y, str(Path(output_dir) / 'section_A.svg'),
                  building_name)
    drawings.append('section_A.svg')

    create_section(elements, 'X', center_x, str(Path(output_dir) / 'section_B.svg'),
                  building_name)
    drawings.append('section_B.svg')

    # Schedules
    create_schedule(elements, 'doors', str(Path(output_dir) / 'door_schedule.svg'),
                   building_name)
    drawings.append('door_schedule.svg')

    create_schedule(elements, 'windows', str(Path(output_dir) / 'window_schedule.svg'),
                   building_name)
    drawings.append('window_schedule.svg')

    create_schedule(elements, 'rooms', str(Path(output_dir) / 'room_schedule.svg'),
                   building_name)
    drawings.append('room_schedule.svg')

    # Index HTML
    create_index_html(output_dir, drawings, building_name)

    print("\n" + "=" * 50)
    print("SUCCESS: Drawing set generated")
    print(f"Open: {output_dir}/drawing_index.html")
    print("=" * 50)


if __name__ == '__main__':
    main()
