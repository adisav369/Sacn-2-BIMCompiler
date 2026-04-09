#!/usr/bin/env python3
"""
DrawingWriter — Generate 2D architectural SVG drawings from compiled BIM database.

Reads the spatial DB produced by the BIM compiler and generates standard
architectural drawings: floor plans, elevations, roof plans.

Output: SVG viewable in any browser. Crisp vector at any zoom.

Usage:
    python3 tools/drawing_writer.py output/samplehouse.db --floor-plan
    python3 tools/drawing_writer.py output/samplehouse.db --elevation front
    python3 tools/drawing_writer.py output/samplehouse.db --all

Style conventions follow Malaysian JKR / TB-LKTN standard drawing practice.
Style constants are grouped for future migration to 2d_drawing_style metadata table.
"""

import sqlite3
import sys
import math
import os
import json
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import List, Tuple, Optional, Dict
from section_cut import section_cut as run_section_cut, parse_vertices_blob

# ─────────────────────────────────────────────────────────────────
# FORENSIC LOG  (spec R6)
# ─────────────────────────────────────────────────────────────────

_SVG_LOG: list = []


def _log(msg: str) -> None:
    """Append a diagnostic line to the session log buffer."""
    _SVG_LOG.append(msg)


# ─────────────────────────────────────────────────────────────────
# DRAWING STYLE CONSTANTS
# Future: these become rows in 2d_drawing_style metadata table.
# Profile: JKR_Malaysian (default)
# ─────────────────────────────────────────────────────────────────

SCALE = 100          # 1:100 scale — 1m world = 10mm paper
PAPER_FACTOR = 1000 / SCALE  # world_m * PAPER_FACTOR = paper_mm (= 10)

# Line weights (mm on paper) — ISO 128 / TB-LKTN JKR standard
# Wide  0.70: ground line, section-cut walls (strongest visual anchor)
# Medium 0.50: visible building outline, eave, roof silhouette
# Thin  0.35: interior partitions, doors, hidden edges
# Hair  0.18: dimensions, grids, hatching, centre lines
# Note: 0.15 is below ISO hairline — avoid (renders as 1px on screen, disappears in print)
LW_GROUND       = 0.70   # Ground line, apron edge — boldest
LW_WALL_EXT     = 0.50   # Exterior walls / building outline
LW_WALL_INT     = 0.35   # Interior partitions
LW_WALL_GLASS   = 0.25   # Curtain wall / glazing
LW_OPENING      = 0.35   # Door/window outlines
LW_DIMENSION    = 0.18   # Dimension lines, extension lines
LW_GRID         = 0.18   # Grid lines (dashed)
LW_FURNITURE    = 0.15   # Furniture outlines — per template + 2d_drawing_style

# Text sizes (mm on paper)
TXT_DIM         = 2.5    # Dimension values
TXT_ROOM        = 3.5    # Room labels
TXT_GRID        = 3.0    # Grid bubble labels
TXT_TITLE       = 5.0    # Drawing title

# Grid bubbles
GRID_CIRCLE_R   = 4.0    # mm radius
GRID_EXTEND     = 15.0   # mm extension beyond building

# Dimension offsets
DIM_GAP         = 2.0    # mm gap from object to extension line start
DIM_EXTEND      = 2.0    # mm extension past dimension line
DIM_OFFSET_1    = 10.0   # mm — first dimension row from building
DIM_OFFSET_2    = 18.0   # mm — second (overall) dimension row
DIM_TICK_SIZE   = 1.5    # mm — 45° tick mark half-length

# Colors
COL_WALL        = '#000000'
COL_GLASS       = '#4488CC'
COL_OPENING     = '#000000'
COL_DIM         = '#000000'
COL_GRID        = '#888888'
COL_FURNITURE   = '#AAAAAA'
COL_FURN_FILL   = '#F0F0F0'
COL_BACKGROUND  = '#FFFFFF'

# Drawing margins (space around building for grids/dimensions within content area)
MARGIN_LEFT     = 35.0
MARGIN_RIGHT    = 15.0
MARGIN_TOP      = 25.0
MARGIN_BOTTOM   = 25.0

# Dimension rounding — snap bay dimensions to nearest module for clean numbers
SNAP_MODULE     = 100    # mm — snap to nearest 100mm (standard housing module)


# ─────────────────────────────────────────────────────────────────
# TEMPLATE LOADER — overrides defaults above from drawing_template.json
# ─────────────────────────────────────────────────────────────────

def _load_template() -> dict:
    """Load drawing_template.json — user-editable drawing CSS.
    Mirrors the same loader used in drawing_writer_dxf.py."""
    tpl_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            '..', 'drawing_template.json')
    if os.path.exists(tpl_path):
        with open(tpl_path) as f:
            return json.load(f)
    return {}


def _init_from_template():
    """Wire module-level style constants from drawing_template.json.
    Keeps constant names intact (other modules import them by name).
    Fallbacks are the ISO 128 / JKR defaults already set above."""
    global LW_GROUND, LW_WALL_EXT, LW_WALL_INT, LW_WALL_GLASS
    global LW_OPENING, LW_DIMENSION, LW_GRID, LW_FURNITURE
    global TXT_DIM, TXT_ROOM, TXT_GRID, TXT_TITLE
    global GRID_CIRCLE_R, GRID_EXTEND
    global COL_WALL, COL_GLASS, COL_OPENING, COL_DIM
    global COL_GRID, COL_FURNITURE, COL_FURN_FILL, COL_BACKGROUND
    global MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM
    global SNAP_MODULE

    tpl = _load_template()
    if not tpl:
        return  # no template file — keep hardcoded defaults

    lw = tpl.get('line_weights', {})
    LW_GROUND     = lw.get('border',              LW_GROUND)
    LW_WALL_EXT   = lw.get('wall_exterior_cut',   LW_WALL_EXT)
    LW_WALL_INT   = lw.get('wall_partition_cut',  LW_WALL_INT)
    LW_WALL_GLASS = lw.get('glass_glazing',        LW_WALL_GLASS)
    LW_OPENING    = lw.get('window_frame',         LW_OPENING)
    LW_DIMENSION  = lw.get('dimension_line',       LW_DIMENSION)
    LW_GRID       = lw.get('grid_line',            LW_GRID)
    LW_FURNITURE  = lw.get('furniture',            LW_FURNITURE)

    col = tpl.get('colors', {})
    COL_WALL      = col.get('wall',           COL_WALL)
    COL_GLASS     = col.get('glass',          COL_GLASS)
    COL_OPENING   = col.get('wall',           COL_OPENING)   # openings share wall color
    COL_DIM       = col.get('dimension',      COL_DIM)
    COL_GRID      = col.get('grid',           COL_GRID)
    COL_FURNITURE = col.get('furniture',      COL_FURNITURE)
    COL_FURN_FILL = col.get('furniture_fill', COL_FURN_FILL)
    # COL_BACKGROUND has no template key — keep hardcoded white

    grid = tpl.get('grid', {})
    GRID_CIRCLE_R = grid.get('bubble_radius_mm',          GRID_CIRCLE_R)
    GRID_EXTEND   = grid.get('extend_beyond_building_mm', GRID_EXTEND)
    TXT_GRID      = grid.get('label_font_height_mm',      TXT_GRID)

    dims = tpl.get('dimensions', {})
    TXT_DIM      = dims.get('text_height_mm',        TXT_DIM)
    DIM_GAP_val  = dims.get('extension_gap_mm',      None)   # local only — not global
    SNAP_MODULE  = dims.get('snap_module_mm',         SNAP_MODULE)

    rl = tpl.get('room_labels', {})
    TXT_ROOM     = rl.get('name_font_height_mm', TXT_ROOM)

    paper = tpl.get('paper', {})
    margins = paper.get('margins', {})
    MARGIN_LEFT   = margins.get('left',   MARGIN_LEFT)
    MARGIN_RIGHT  = margins.get('right',  MARGIN_RIGHT)
    MARGIN_TOP    = margins.get('top',    MARGIN_TOP)
    MARGIN_BOTTOM = margins.get('bottom', MARGIN_BOTTOM)


_init_from_template()

# ─────────────────────────────────────────────────────────────────
# DATA CLASSES
# ─────────────────────────────────────────────────────────────────

@dataclass
class Element:
    """A building element with bounding box."""
    ifc_class: str
    name: str
    storey: str
    min_x: float
    max_x: float
    min_y: float
    max_y: float
    min_z: float
    max_z: float
    guid: str = ''

    @property
    def width_x(self): return abs(self.max_x - self.min_x)

    @property
    def width_y(self): return abs(self.max_y - self.min_y)

    @property
    def center_x(self): return (self.min_x + self.max_x) / 2

    @property
    def center_y(self): return (self.min_y + self.max_y) / 2

    @property
    def is_ns_wall(self):
        """Wall runs north-south (thin in X, long in Y)."""
        return self.width_x < self.width_y

    @property
    def is_ew_wall(self):
        """Wall runs east-west (long in X, thin in Y)."""
        return self.width_x >= self.width_y

    @property
    def is_exterior(self):
        return 'Ext' in self.name

    @property
    def is_glass(self):
        return 'Glazed' in self.name or 'Curtain' in self.name

    @property
    def is_partition(self):
        return 'Partn' in self.name

@dataclass
class GridLine:
    """A structural grid line."""
    label: str
    axis: str       # 'x' (vertical line) or 'y' (horizontal line)
    position: float  # world coordinate
    source_guids: list = field(default_factory=list)

@dataclass
class DimString:
    """A dimension annotation."""
    start: float     # world coord along axis
    end: float       # world coord along axis
    offset: float    # perpendicular offset (paper mm from building edge)
    axis: str        # 'x' (horizontal dim) or 'y' (vertical dim)
    text: str        # formatted dimension value
    from_label: str = ''  # §20: grid label at start
    to_label: str = ''    # §20: grid label at end

# ─────────────────────────────────────────────────────────────────
# DATABASE READER
# ─────────────────────────────────────────────────────────────────

def read_elements(db_path: str, storey_filter: str = None) -> Dict[str, List[Element]]:
    """Read building elements from compiled DB, grouped by category."""
    conn = sqlite3.connect(db_path)

    query = """
        SELECT m.ifc_class, m.element_name, m.storey,
               r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
               m.guid
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        ORDER BY m.ifc_class, m.element_name
    """
    rows = conn.execute(query).fetchall()
    conn.close()

    result = {'walls': [], 'doors': [], 'windows': [], 'furniture': [],
              'slabs': [], 'roofs': [], 'other': []}

    for row in rows:
        e = Element(*row)

        # Skip roof-level elements for floor plan
        if storey_filter and e.storey and 'Roof' in e.storey:
            if e.ifc_class not in ('IfcRoof',):
                continue

        if e.ifc_class in ('IfcWall', 'IfcPlate'):
            result['walls'].append(e)
        elif e.ifc_class == 'IfcDoor':
            result['doors'].append(e)
        elif e.ifc_class == 'IfcWindow':
            result['windows'].append(e)
        elif e.ifc_class in ('IfcFurnishingElement', 'IfcFurniture'):
            result['furniture'].append(e)
        elif e.ifc_class == 'IfcSlab':
            result['slabs'].append(e)
        elif e.ifc_class == 'IfcRoof':
            result['roofs'].append(e)
        else:
            result['other'].append(e)

    # §5.3a rule 2: if no IfcRoof, promote IfcSlab named '*Roof*' at building top-Z
    if not result['roofs'] and result['slabs']:
        all_max_z = max((e.max_z for e in result['slabs'] + result['other']
                         if hasattr(e, 'max_z')), default=0)
        roof_slabs = [e for e in result['slabs']
                      if 'Roof' in (e.name or '')
                      and e.max_z >= all_max_z - 0.5]
        if roof_slabs:
            result['roofs'] = roof_slabs
            result['slabs'] = [e for e in result['slabs'] if e not in roof_slabs]

    return result


def read_drawing_metadata() -> Optional[Dict]:
    """Load JKR drawing metadata from input/2D.db.
    §3.2b: path is input/2D.db (relative to 2D_Layout/), not python/2D_metadata.db."""
    meta_db = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           '..', 'input', '2D.db')
    if not os.path.exists(meta_db):
        # Fallback to lib/input/2D.db (git-tracked location)
        meta_db = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               '..', 'lib', 'input', '2D.db')
    if not os.path.exists(meta_db):
        print(f"  Warning: 2D.db not found, using defaults", file=sys.stderr)
        return None

    conn = sqlite3.connect(meta_db)
    conn.row_factory = sqlite3.Row
    meta = {}

    # Sheet template (A3)
    row = conn.execute(
        'SELECT * FROM [2d_sheet_template] '
        "WHERE profile_id='JKR_Malaysian' AND paper_size='A3'"
    ).fetchone()
    if row:
        meta['sheet'] = dict(row)

    # Title block fields
    rows = conn.execute(
        'SELECT * FROM [2d_title_block] '
        "WHERE profile_id='JKR_Malaysian' ORDER BY field_order"
    ).fetchall()
    meta['title_fields'] = [dict(r) for r in rows]

    # Room labels (English)
    rows = conn.execute(
        'SELECT * FROM [2d_room_label] '
        "WHERE profile_id='JKR_Malaysian' AND language='EN'"
    ).fetchall()
    meta['room_labels'] = {r['space_type']: dict(r) for r in rows}

    # Annotation tags
    rows = conn.execute(
        "SELECT * FROM [2d_annotation_tag] WHERE profile_id='JKR_Malaysian'"
    ).fetchall()
    meta['tags'] = {r['tag_type']: dict(r) for r in rows}

    conn.close()
    return meta

# ─────────────────────────────────────────────────────────────────
# GRID DERIVATION
# ─────────────────────────────────────────────────────────────────

def derive_grids(walls: List[Element], columns: Optional[List[Element]] = None,
                 template: Optional[Dict] = None) -> List[GridLine]:
    """Derive structural bay grid lines from boundary walls and columns.

    §4.2: Grids mark structural bay spacing — columns + boundary walls only.
    Partition walls are NOT gridded; they are dimensioned from grids.
    §4.2a: IfcColumn always gridded (highest priority).
    §4.2d: Labels from template vertical/horizontal_axis_labels (skip I).

    Convention: Letters (A, B, C...) for vertical grids (X-axis),
                Numbers (1, 2, 3...) for horizontal grids (Y-axis).
    This follows TB-LKTN (JKR Malaysian) practice.
    """
    # §20.2: entries are (position, guid) tuples for source_guids tracking
    x_entries = []
    y_entries = []

    # §4.2: compute building bounding box from all walls (including glass)
    # to detect boundary positions
    all_walls = [w for w in walls]
    if not all_walls:
        return []
    bld_min_x = min(w.min_x for w in all_walls)
    bld_max_x = max(w.max_x for w in all_walls)
    bld_min_y = min(w.min_y for w in all_walls)
    bld_max_y = max(w.max_y for w in all_walls)

    # §4.2a: IfcColumn always gridded — columns define grid intersections
    if columns:
        for c in columns:
            x_entries.append((c.center_x, c.guid))
            y_entries.append((c.center_y, c.guid))

    # §4.2.1 Step 1: exterior wall centrelines only — partition walls are NOT gridded
    # (spec §4.2: "Grids mark structural bay spacing — columns + boundary walls only")
    for w in walls:
        if w.is_glass or not w.is_exterior:
            continue
        if w.is_ns_wall:
            x_entries.append((w.center_x, w.guid))
        if w.is_ew_wall:
            y_entries.append((w.center_y, w.guid))

    # §4.2.1 Step 2: wall endpoints of exterior walls as bay boundaries
    # Only endpoints far from bbox edges (interior junctions like glass-masonry)
    BOUNDARY_TOL = 0.50
    for w in walls:
        if w.is_glass or not w.is_exterior:
            continue
        if w.is_ew_wall:
            if abs(w.min_x - bld_min_x) > BOUNDARY_TOL:
                x_entries.append((w.min_x, w.guid))
            if abs(w.max_x - bld_max_x) > BOUNDARY_TOL:
                x_entries.append((w.max_x, w.guid))
        if w.is_ns_wall:
            if abs(w.min_y - bld_min_y) > BOUNDARY_TOL:
                y_entries.append((w.min_y, w.guid))
            if abs(w.max_y - bld_max_y) > BOUNDARY_TOL:
                y_entries.append((w.max_y, w.guid))

    # §4.2.1 Step 3: building bounding box edges are structural boundaries
    # even if formed by glass/curtain wall — contractor needs these
    x_entries.append((bld_min_x, ''))
    x_entries.append((bld_max_x, ''))
    y_entries.append((bld_min_y, ''))
    y_entries.append((bld_max_y, ''))

    # §4.2 Step 2: cluster within wall-thickness tolerance (0.20m)
    MERGE_TOL = 0.20

    def merge_entries(entries):
        """Merge (pos, guid) tuples within MERGE_TOL, accumulating guids."""
        if not entries:
            return []
        entries = sorted(entries, key=lambda e: e[0])
        # merged: list of (position, [guids])
        merged = [(entries[0][0], [entries[0][1]])]
        for pos, guid in entries[1:]:
            if abs(pos - merged[-1][0]) < MERGE_TOL:
                avg = (merged[-1][0] + pos) / 2
                merged[-1] = (avg, merged[-1][1] + [guid])
            else:
                merged.append((pos, [guid]))
        return merged

    x_merged = merge_entries(x_entries)
    y_merged = merge_entries(y_entries)

    # §4.2e: prune bays narrower than min_structural_bay_m — wall-thickness artifacts
    min_bay = (template or {}).get('grid', {}).get('min_structural_bay_m', 1.0)

    def prune_small_bays(merged):
        """Keep first + last; skip interior positions creating bays < min_bay.
        Each element is (position, [guids])."""
        if len(merged) <= 2:
            return merged
        kept = [merged[0]]
        for pos, guids in merged[1:-1]:
            if pos - kept[-1][0] >= min_bay:
                kept.append((pos, guids))
        last_pos, last_guids = merged[-1]
        if last_pos - kept[-1][0] < min_bay and len(kept) > 1:
            # last building edge too close to previous — replace last kept with edge
            kept[-1] = (last_pos, kept[-1][1] + last_guids)
        else:
            kept.append((last_pos, last_guids))
        return kept

    x_merged = prune_small_bays(x_merged)
    y_merged = prune_small_bays(y_merged)

    # §4.2d: labels from template axis_labels (skip I per TB-LKTN)
    tpl_grid = (template or {}).get('grid', {})
    v_labels = tpl_grid.get('vertical_axis_labels', 'A,B,C,D,E,F,G,H,J,K,L,M,N').split(',')
    h_labels = tpl_grid.get('horizontal_axis_labels', '1,2,3,4,5,6,7,8,9,10,11,12').split(',')

    grids = []
    for i, (x, guids) in enumerate(x_merged):
        label = v_labels[i] if i < len(v_labels) else chr(ord('A') + i)
        clean_guids = [g for g in guids if g]
        grids.append(GridLine(label, 'x', x, source_guids=clean_guids))

    for i, (y, guids) in enumerate(y_merged):
        label = h_labels[i] if i < len(h_labels) else str(i + 1)
        clean_guids = [g for g in guids if g]
        grids.append(GridLine(label, 'y', y, source_guids=clean_guids))

    return grids

# ─────────────────────────────────────────────────────────────────
# DIMENSION GENERATION
# ─────────────────────────────────────────────────────────────────

def generate_dimensions(grids: List[GridLine]) -> List[DimString]:
    """Generate dimension strings from grid lines.

    Bay dimensions (between adjacent grids) on first row.
    Overall dimension on second row.
    """
    dims = []

    x_grids = sorted([g for g in grids if g.axis == 'x'], key=lambda g: g.position)
    y_grids = sorted([g for g in grids if g.axis == 'y'], key=lambda g: g.position)

    # §6.1 I-23: suppress tier-2 bay dims when only 1 bay — tier-2 would equal tier-1
    # X-axis bay dimensions (horizontal, shown above building)
    if len(x_grids) > 2:
        for i in range(len(x_grids) - 1):
            dist = x_grids[i + 1].position - x_grids[i].position
            dims.append(DimString(
                x_grids[i].position, x_grids[i + 1].position,
                DIM_OFFSET_1, 'x', format_dim(dist),
                from_label=x_grids[i].label, to_label=x_grids[i + 1].label
            ))

    # X-axis overall dimension
    if len(x_grids) >= 2:
        dist = x_grids[-1].position - x_grids[0].position
        dims.append(DimString(
            x_grids[0].position, x_grids[-1].position,
            DIM_OFFSET_2, 'x', format_dim(dist),
            from_label=x_grids[0].label, to_label=x_grids[-1].label
        ))

    # §6.1 I-23: suppress tier-2 bay dims when only 1 bay — tier-2 would equal tier-1
    # Y-axis bay dimensions (vertical, shown left of building)
    if len(y_grids) > 2:
        for i in range(len(y_grids) - 1):
            dist = y_grids[i + 1].position - y_grids[i].position
            dims.append(DimString(
                y_grids[i].position, y_grids[i + 1].position,
                DIM_OFFSET_1, 'y', format_dim(dist),
                from_label=y_grids[i].label, to_label=y_grids[i + 1].label
            ))

    # Y-axis overall dimension
    if len(y_grids) >= 2:
        dist = y_grids[-1].position - y_grids[0].position
        dims.append(DimString(
            y_grids[0].position, y_grids[-1].position,
            DIM_OFFSET_2, 'y', format_dim(dist),
            from_label=y_grids[0].label, to_label=y_grids[-1].label
        ))

    return dims


def format_dim(meters: float) -> str:
    """Format dimension value. Use mm for values, matching TB-LKTN convention."""
    mm = meters * 1000
    if abs(mm - round(mm)) < 1:
        return f"{int(round(mm))}"
    return f"{mm:.0f}"


def snap_grids(grids: List[GridLine]) -> List[GridLine]:
    """Snap grid positions so bay dimensions round to clean architectural numbers.

    Keeps first grid as anchor on each axis.  Snaps each bay distance to
    nearest SNAP_MODULE mm, then rebuilds subsequent positions so bays
    and overall dimension are internally consistent.
    """
    result: List[GridLine] = []

    for axis in ('x', 'y'):
        axis_grids = sorted(
            [g for g in grids if g.axis == axis],
            key=lambda g: g.position
        )
        if len(axis_grids) < 2:
            result.extend(axis_grids)
            continue

        # Anchor at first grid position
        snapped = [axis_grids[0]]
        for i in range(1, len(axis_grids)):
            raw_bay_mm = (axis_grids[i].position
                          - axis_grids[i - 1].position) * 1000
            snapped_bay_mm = round(raw_bay_mm / SNAP_MODULE) * SNAP_MODULE
            # Guard: never collapse a bay to zero
            if snapped_bay_mm < SNAP_MODULE:
                snapped_bay_mm = SNAP_MODULE
            new_pos = snapped[-1].position + snapped_bay_mm / 1000
            snapped.append(GridLine(axis_grids[i].label, axis, new_pos,
                                    source_guids=axis_grids[i].source_guids))

        result.extend(snapped)

    return result


def _convex_hull_2d(points):
    """Andrew's monotone chain convex hull. Returns CCW-ordered hull points.

    Works on any iterable of (x, y) tuples. Pure Python, no dependencies.
    Portable to Java as-is (the same algorithm).
    """
    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    pts = sorted(set(points))
    if len(pts) <= 2:
        return pts

    lower = []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)

    upper = []
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)

    return lower[:-1] + upper[:-1]


def roof_silhouette(db_path, face):
    """Extract roof profile from mesh vertices via convex hull projection.

    Loads the actual triangle mesh from base_geometries, projects all vertices
    onto the elevation plane, and computes the 2D convex hull — giving the
    true roof silhouette including curves, hips, and overhangs.

    Returns list of (h, z) points forming the convex hull outline.
    h = horizontal position in the view, z = vertical (height).
    """
    conn = sqlite3.connect(db_path)
    rows = conn.execute("""
        SELECT bg.vertices, bg.vertex_count
        FROM elements_meta m
        JOIN element_instances ei ON m.guid = ei.guid
        JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
        WHERE m.ifc_class = 'IfcRoof'
    """).fetchall()
    conn.close()

    all_pts = []
    for vert_blob, vertex_count in rows:
        if vert_blob is None:
            continue
        verts = parse_vertices_blob(vert_blob, vertex_count)
        if face in ('front', 'rear'):
            h_vals = verts[:, 0]   # X axis horizontal
            if face == 'rear':
                h_vals = -h_vals   # mirror: rear viewer sees X reversed
        else:
            h_vals = verts[:, 1]   # Y axis horizontal
            if face == 'right':
                h_vals = -h_vals   # mirror: right viewer sees Y reversed
        z_vals = verts[:, 2]
        for h, z in zip(h_vals, z_vals):
            all_pts.append((float(h), float(z)))

    if len(all_pts) < 3:
        return []

    return _convex_hull_2d(all_pts)


def detect_levels(elements: Dict[str, List['Element']]) -> list:
    """Detect building height levels from elements for elevation annotation.

    Returns sorted list of (label, z_value) tuples with snapped Z values.
    Follows TB-LKTN convention: every meaningful horizontal datum is marked —
    GRD. FLOOR, WINDOW SILL, WINDOW HEAD / DOOR HEAD, CEILING, RIDGE.
    """
    from collections import Counter

    def snap(z): return round(z * 1000 / SNAP_MODULE) * SNAP_MODULE / 1000

    levels = [('FFL', 0.0)]  # Finished Floor Level always at 0

    walls   = elements['walls']
    roofs   = elements.get('roofs', [])
    windows = elements.get('windows', [])
    doors   = elements.get('doors', [])

    # Ceiling level: mode of interior partition tops
    partition_tops = [w.max_z for w in walls
                      if not w.is_exterior and not w.is_glass]
    if partition_tops:
        rounded = [snap(z) for z in partition_tops]
        ceiling_z = Counter(rounded).most_common(1)[0][0]
        levels.append(('CLG', ceiling_z))

    # Window sill: mode of window bottom Z (most common sill height)
    sill_zs = [snap(w.min_z) for w in windows if w.max_z - w.min_z > 0.1]
    if sill_zs:
        sill_z = Counter(sill_zs).most_common(1)[0][0]
        if sill_z > 0.05:   # ignore sills at floor level (full-height glazing)
            levels.append(('SILL', sill_z))

    # Window / door head: mode of top Z across all openings
    # In TB-LKTN this is typically a single datum (all heads at same height)
    head_zs = [snap(e.max_z) for e in windows + doors if e.max_z > 0.1]
    if head_zs:
        head_z = Counter(head_zs).most_common(1)[0][0]
        levels.append(('HEAD', head_z))

    # §4.3b: Eave level — lowest roof Z (top of wall / bottom of roof overhang)
    if roofs:
        eave_z = snap(min(r.min_z for r in roofs))
        if eave_z > 0.5:  # ignore ground-touching roofs
            levels.append(('EAVE', eave_z))

    # Roof ridge level: highest roof point
    if roofs:
        ridge_z = snap(max(r.max_z for r in roofs))
        levels.append(('RIDGE', ridge_z))

    # Deduplicate levels that are within 100mm of each other
    # (e.g. door head == ceiling — keep the more informative label)
    PRIORITY = {'CLG': 0, 'RIDGE': 1, 'EAVE': 2, 'HEAD': 3, 'SILL': 4, 'FFL': 5}
    merged = {}
    for lbl, z in sorted(levels, key=lambda lv: PRIORITY.get(lv[0], 9)):
        already = [k for k, v in merged.items() if abs(v - z) < 0.10]
        if not already:
            merged[lbl] = z

    return sorted(merged.items(), key=lambda lv: lv[1])


# ─────────────────────────────────────────────────────────────────
# SVG GENERATION
# ─────────────────────────────────────────────────────────────────

class SVGBuilder:
    """Builds an SVG document with architectural drawing conventions."""

    def __init__(self):
        self.layers: Dict[str, List[str]] = {
            'border': [],
            'grid': [],
            'wall_fill': [],
            'wall_stroke': [],
            'opening': [],
            'furniture': [],
            'dimension': [],
            'room_label': [],
            'title_block': [],
            'label': [],
        }
        self.view_min_x = 0
        self.view_min_y = 0
        self.view_width = 100
        self.view_height = 100
        self._defs: List[str] = []

    def add_def(self, content: str):
        """Add a raw SVG element into <defs> (patterns, markers, etc.)."""
        self._defs.append(content)

    def set_viewbox(self, min_x, min_y, width, height):
        self.view_min_x = min_x
        self.view_min_y = min_y
        self.view_width = width
        self.view_height = height

    def _add(self, layer: str, svg: str):
        self.layers[layer].append(svg)

    # ── Primitives ──

    def rect(self, layer, x, y, w, h, stroke='#000', stroke_width=0.3,
             fill='none', opacity=1.0):
        self._add(layer,
            f'<rect x="{x:.3f}" y="{y:.3f}" width="{w:.3f}" height="{h:.3f}" '
            f'stroke="{stroke}" stroke-width="{stroke_width:.3f}" '
            f'fill="{fill}" opacity="{opacity}"/>')

    def line(self, layer, x1, y1, x2, y2, stroke='#000', stroke_width=0.3,
             dash=None):
        extra = f' stroke-dasharray="{dash}"' if dash else ''
        self._add(layer,
            f'<line x1="{x1:.3f}" y1="{y1:.3f}" x2="{x2:.3f}" y2="{y2:.3f}" '
            f'stroke="{stroke}" stroke-width="{stroke_width:.3f}"{extra}/>')

    def circle(self, layer, cx, cy, r, stroke='#000', stroke_width=0.3,
               fill='none'):
        self._add(layer,
            f'<circle cx="{cx:.3f}" cy="{cy:.3f}" r="{r:.3f}" '
            f'stroke="{stroke}" stroke-width="{stroke_width:.3f}" fill="{fill}"/>')

    def arc(self, layer, cx, cy, r, start_deg, end_deg, stroke='#000',
            stroke_width=0.25, cw=True):
        """Draw an arc (for door swings). cw=True for clockwise in SVG coords."""
        s_rad = math.radians(start_deg)
        e_rad = math.radians(end_deg)
        x1 = cx + r * math.cos(s_rad)
        y1 = cy + r * math.sin(s_rad)
        x2 = cx + r * math.cos(e_rad)
        y2 = cy + r * math.sin(e_rad)
        if cw:
            angle = (end_deg - start_deg) % 360
        else:
            angle = (start_deg - end_deg) % 360
        large = 1 if angle > 180 else 0
        sweep_flag = 1 if cw else 0
        self._add(layer,
            f'<path d="M {x1:.3f},{y1:.3f} A {r:.3f},{r:.3f} 0 {large},{sweep_flag} '
            f'{x2:.3f},{y2:.3f}" stroke="{stroke}" stroke-width="{stroke_width:.3f}" '
            f'fill="none"/>')

    def text(self, layer, x, y, content, size=2.5, anchor='middle',
             color='#000', rotate=0, font='sans-serif'):
        transform = f' transform="rotate({rotate},{x:.3f},{y:.3f})"' if rotate else ''
        # Escape XML special characters in content
        safe = str(content).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
        self._add(layer,
            f'<text x="{x:.3f}" y="{y:.3f}" font-family="{font}" '
            f'font-size="{size:.2f}" fill="{color}" text-anchor="{anchor}" '
            f'dominant-baseline="central"{transform}>{safe}</text>')

    def tick_mark(self, layer, x, y, axis, stroke='#000', stroke_width=0.18):
        """Draw a 45-degree tick mark (TB-LKTN dimension style)."""
        d = DIM_TICK_SIZE
        if axis == 'x':
            self.line(layer, x - d, y + d, x + d, y - d, stroke, stroke_width)
        else:
            self.line(layer, x - d, y - d, x + d, y + d, stroke, stroke_width)

    def rect_fill(self, layer, x, y, w, h, stroke, stroke_width, fill_url, opacity=1.0):
        """Rect with a fill referencing a <defs> pattern/gradient by URL."""
        self._add(layer,
            f'<rect x="{x:.3f}" y="{y:.3f}" width="{w:.3f}" height="{h:.3f}" '
            f'stroke="{stroke}" stroke-width="{stroke_width:.3f}" '
            f'fill="{fill_url}" opacity="{opacity}"/>')

    def polygon(self, layer, points, stroke, stroke_width, fill='none', opacity=1.0):
        """Draw a closed polygon from a list of (x,y) tuples."""
        pts_str = " ".join(f"{x:.3f},{y:.3f}" for x, y in points)
        self._add(layer,
            f'<polygon points="{pts_str}" stroke="{stroke}" '
            f'stroke-width="{stroke_width:.3f}" fill="{fill}" '
            f'opacity="{opacity}" stroke-linejoin="round"/>')

    def hexagon(self, layer, cx, cy, r, stroke='#000', stroke_width=0.18,
                fill='#FFFFFF'):
        """Draw a regular hexagon centered at (cx,cy) with circumradius r."""
        pts = []
        for i in range(6):
            angle = math.radians(60 * i - 90)  # Start at top
            pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
        self.polygon(layer, pts, stroke, stroke_width, fill)

    # ── Output ──

    def to_string(self) -> str:
        # Convert viewbox to integers for clean SVG
        vb = f"{self.view_min_x:.2f} {self.view_min_y:.2f} {self.view_width:.2f} {self.view_height:.2f}"
        # Paper size: compute from viewbox (mm → reasonable pixel size for browser)
        px_w = self.view_width * 3  # ~3 pixels per paper mm for screen
        px_h = self.view_height * 3

        lines = [
            f'<?xml version="1.0" encoding="UTF-8"?>',
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}" '
            f'width="{px_w:.0f}" height="{px_h:.0f}" '
            f'style="background:{COL_BACKGROUND}">',
            f'  <defs>',
            f'    <style>text {{ font-family: "Arial", "Helvetica", sans-serif; }}</style>',
        ] + [f'    {d}' for d in self._defs] + [
            f'  </defs>',
        ]

        layer_order = ['border', 'grid', 'furniture', 'wall_fill', 'wall_stroke',
                       'opening', 'dimension', 'room_label', 'title_block', 'label']
        for layer_name in layer_order:
            items = self.layers.get(layer_name, [])
            if items:
                lines.append(f'  <g id="{layer_name}">')
                for item in items:
                    lines.append(f'    {item}')
                lines.append(f'  </g>')

        lines.append('</svg>')
        return '\n'.join(lines)

# ─────────────────────────────────────────────────────────────────
# SHEET / TITLE BLOCK / ANNOTATION HELPERS
# ─────────────────────────────────────────────────────────────────

DEFAULT_SHEET = {
    'width_mm': 420, 'height_mm': 297,
    'margin_left': 25, 'margin_top': 10, 'margin_right': 10, 'margin_bottom': 10,
    'title_block_width': 120, 'border_weight': 0.7,
}


def _get_sheet(meta):
    """Extract sheet dict from metadata, with fallback to defaults."""
    if meta and 'sheet' in meta:
        return meta['sheet']
    return DEFAULT_SHEET


def draw_sheet_border(svg: SVGBuilder, sheet: dict):
    """Draw sheet border rectangle at margin boundaries."""
    ml = sheet['margin_left']
    mt = sheet['margin_top']
    w = sheet['width_mm'] - ml - sheet['margin_right']
    h = sheet['height_mm'] - mt - sheet['margin_bottom']
    svg.rect('border', ml, mt, w, h, COL_WALL, sheet['border_weight'])


def draw_title_block(svg: SVGBuilder, sheet: dict, title_fields: list,
                     drawing_title: str = '', drawing_no: str = ''):
    """Draw JKR title block strip on right side of sheet."""
    tb_left = sheet['width_mm'] - sheet['margin_right'] - sheet['title_block_width']
    tb_top = sheet['margin_top']
    tb_width = sheet['title_block_width']
    tb_bottom = sheet['height_mm'] - sheet['margin_bottom']
    tb_height = tb_bottom - tb_top
    bw = sheet['border_weight']

    # Vertical boundary line (left edge of title block)
    svg.line('title_block', tb_left, tb_top, tb_left, tb_bottom, COL_WALL, bw)

    # Draw field rows from bottom up
    total_fields_h = sum(f['row_height'] for f in title_fields)
    label_col_w = tb_width * 0.30  # 30% for label column

    y_cursor = tb_bottom
    for tf in reversed(title_fields):
        rh = tf['row_height']
        y_cursor -= rh

        # Horizontal separator
        svg.line('title_block', tb_left, y_cursor, tb_left + tb_width, y_cursor,
                 COL_WALL, LW_DIMENSION)
        # Vertical divider between label and value columns
        div_x = tb_left + label_col_w
        svg.line('title_block', div_x, y_cursor, div_x, y_cursor + rh,
                 COL_WALL, LW_DIMENSION)

        # Label text (left column, centered)
        label_x = tb_left + label_col_w / 2
        label_y = y_cursor + rh / 2
        svg.text('title_block', label_x, label_y, tf['label_text'],
                 tf['label_size'], color=COL_DIM)

        # Value text (right column, centered)
        value_x = div_x + (tb_width - label_col_w) / 2
        value = tf.get('default_value') or ''

        # Override specific fields with drawing-level values
        if tf['field_name'] == 'TAJUK_LUKISAN' and drawing_title:
            value = drawing_title
        elif tf['field_name'] == 'NO_LUKISAN' and drawing_no:
            value = drawing_no
        elif tf['field_name'] == 'HARIBULAN':
            value = '2026-02'

        if value:
            svg.text('title_block', value_x, label_y, value,
                     tf['value_size'], color=COL_WALL)

    # Organization header — pinned near top of title block
    logo_cx = tb_left + tb_width / 2
    logo_cy = tb_top + 10  # fixed 10mm from top
    svg.text('title_block', logo_cx, logo_cy, 'JABATAN KERJA RAYA',
             3.0, color=COL_GRID)
    svg.text('title_block', logo_cx, logo_cy + 5, 'MALAYSIA',
             2.5, color=COL_GRID)


def draw_north_arrow(svg: SVGBuilder, x: float, y: float, size: float = 8):
    """Draw a north arrow at (x, y). Arrow points up, size in mm."""
    half = size / 2
    qtr = size * 0.25
    tip = (x, y - half)
    bl = (x - qtr, y + half)
    br = (x + qtr, y + half)
    mid_bot = (x, y + half)

    # Filled right half (dark)
    svg.polygon('label', [tip, mid_bot, br], COL_WALL, 0.18, fill=COL_WALL)
    # Outline left half (light)
    svg.polygon('label', [tip, bl, mid_bot], COL_WALL, 0.18, fill=COL_BACKGROUND)
    # "N" label above
    svg.text('label', x, y - half - 2.5, 'N', 3.0)


def draw_scale_bar(svg: SVGBuilder, x: float, y: float, scale: int = 100):
    """Draw graphic scale bar. At 1:100, 10mm paper = 1m real."""
    # Bar segments: 0–1m (10mm), 1–2m (10mm), 2–5m (30mm)
    segments = [(10, True), (10, False), (30, True)]
    bar_h = 2.0

    cx = x
    for seg_w, filled in segments:
        fill = COL_WALL if filled else COL_BACKGROUND
        svg.rect('label', cx, y, seg_w, bar_h, COL_WALL, 0.18, fill)
        cx += seg_w

    # Labels below bar
    label_y = y + bar_h + 2.5
    for lx, txt in [(x, '0'), (x + 10, '1'), (x + 20, '2'), (x + 50, '5m')]:
        svg.text('label', lx, label_y, txt, 2.0)


def _parse_opening_name(name: str) -> Tuple[str, str]:
    """Parse element name into description and size.

    'Doors_ExtDbl_Flush:1810x2110mm' → ('EXT DBL FLUSH', '1810×2110')
    """
    if ':' in name:
        type_part, size_part = name.split(':', 1)
        type_part = (type_part
                     .replace('Doors_', '').replace('Windows_', '')
                     .replace('_', ' ').upper())
        size_part = size_part.replace('mm', '').replace('x', '\u00d7').strip()
        return type_part, size_part
    return name, ''


def draw_opening_legend(svg: SVGBuilder, sheet: dict,
                        doors: List[Element], windows: List[Element],
                        walls: List[Element]):
    """Draw door/window schedule in the title block's upper area."""
    tb_left = sheet['width_mm'] - sheet['margin_right'] - sheet['title_block_width']
    tb_top = sheet['margin_top']
    tb_width = sheet['title_block_width']
    total_fields_h = 109  # sum of 2d_title_block" row heights
    tb_bottom = sheet['height_mm'] - sheet['margin_bottom']

    # Legend area: between JKR header and field rows
    legend_top = tb_top + 18  # below JKR header text
    legend_bottom = tb_bottom - total_fields_h - 5
    if legend_bottom - legend_top < 30:
        return  # not enough space

    pad = 4  # mm padding from title block edges
    lx = tb_left + pad
    row_h = 5.5  # mm per schedule row

    # ── Build schedule items: (tag, size, description) ──
    items = []
    seen_types = {}  # group identical types

    for i, d in enumerate(doors, 1):
        desc, size = _parse_opening_name(d.name)
        tag = f'D{i}'
        key = (d.name, 'D')
        if key not in seen_types:
            seen_types[key] = {'tag_first': tag, 'tag_last': tag,
                               'desc': desc, 'size': size, 'count': 1}
        else:
            seen_types[key]['tag_last'] = tag
            seen_types[key]['count'] += 1

    for i, w in enumerate(windows, 1):
        desc, size = _parse_opening_name(w.name)
        tag = f'W{i}'
        key = (w.name, 'W')
        if key not in seen_types:
            seen_types[key] = {'tag_first': tag, 'tag_last': tag,
                               'desc': desc, 'size': size, 'count': 1}
        else:
            seen_types[key]['tag_last'] = tag
            seen_types[key]['count'] += 1

    for info in seen_types.values():
        if info['count'] == 1:
            tag_str = info['tag_first']
        else:
            tag_str = f"{info['tag_first']}-{info['tag_last']}"
        items.append((tag_str, info['size'], info['desc'],
                      f"{info['count']} No."))

    if not items:
        return

    # ── Draw header ──
    cy = legend_top
    svg.line('title_block', tb_left, cy, tb_left + tb_width, cy,
             COL_WALL, LW_DIMENSION)
    svg.text('title_block', tb_left + tb_width / 2, cy + row_h / 2,
             'JADUAL PINTU & TINGKAP', 2.5, color=COL_WALL)
    cy += row_h
    svg.text('title_block', tb_left + tb_width / 2, cy + row_h * 0.35,
             'DOOR & WINDOW SCHEDULE', 1.8, color=COL_GRID)
    cy += row_h * 0.8

    # Column header
    svg.line('title_block', tb_left, cy, tb_left + tb_width, cy,
             COL_WALL, LW_DIMENSION)
    hdr_y = cy + row_h / 2
    col_tag = lx
    col_size = lx + 18
    col_desc = lx + 48
    col_qty = lx + 95
    svg.text('title_block', col_tag, hdr_y, 'TAG', 1.8,
             anchor='start', color=COL_DIM)
    svg.text('title_block', col_size, hdr_y, 'SIZE', 1.8,
             anchor='start', color=COL_DIM)
    svg.text('title_block', col_desc, hdr_y, 'DESCRIPTION', 1.8,
             anchor='start', color=COL_DIM)
    svg.text('title_block', col_qty, hdr_y, 'QTY', 1.8,
             anchor='start', color=COL_DIM)
    cy += row_h

    # Separator
    svg.line('title_block', tb_left, cy, tb_left + tb_width, cy,
             COL_GRID, LW_DIMENSION)

    # ── Draw rows ──
    for tag_str, size, desc, qty in items:
        if cy + row_h > legend_bottom:
            break
        ry = cy + row_h / 2
        svg.text('title_block', col_tag, ry, tag_str, 2.0,
                 anchor='start', color=COL_WALL)
        svg.text('title_block', col_size, ry, size, 2.0,
                 anchor='start', color=COL_WALL)
        svg.text('title_block', col_desc, ry, desc, 1.8,
                 anchor='start', color=COL_DIM)
        svg.text('title_block', col_qty, ry, qty, 2.0,
                 anchor='start', color=COL_DIM)
        cy += row_h

    # Bottom separator
    svg.line('title_block', tb_left, cy, tb_left + tb_width, cy,
             COL_GRID, LW_DIMENSION)


def infer_rooms(furniture: List[Element], walls: List[Element]) -> list:
    """Infer room locations from furniture keywords.

    Returns list of (room_type, centroid_x, centroid_y, area_m2).
    Graceful no-op if no furniture matches.
    """
    keyword_map = {
        'bed': 'BEDROOM',
        'sofa': 'LIVING',
        'lounge': 'LIVING',
        'coffee_table': 'LIVING',
        'piano': 'LIVING',
        'dining': 'DINING',
        'workstation': 'BEDROOM',
    }

    room_items: Dict[str, List[Tuple[float, float]]] = {}
    for f in furniture:
        name_lower = f.name.lower()
        for keyword, room_type in keyword_map.items():
            if keyword in name_lower:
                room_items.setdefault(room_type, []).append(
                    (f.center_x, f.center_y))
                break

    rooms = []
    for room_type, positions in room_items.items():
        cx = sum(p[0] for p in positions) / len(positions)
        cy = sum(p[1] for p in positions) / len(positions)

        # Estimate area from furniture spread with padding
        if len(positions) > 1:
            pad = 1.0  # 1m padding around furniture cluster
            mn_x = min(p[0] for p in positions) - pad
            mx_x = max(p[0] for p in positions) + pad
            mn_y = min(p[1] for p in positions) - pad
            mx_y = max(p[1] for p in positions) + pad
            area = (mx_x - mn_x) * (mx_y - mn_y)
        else:
            area = 9.0  # Default ~3x3m

        rooms.append((room_type, cx, cy, area))

    return rooms


def _get_room_side_ew(host: Element, furniture: List[Element]) -> str:
    """For E-W wall, determine if room is north or south based on furniture."""
    north = sum(1 for f in furniture if f.center_y > host.center_y)
    south = sum(1 for f in furniture if f.center_y < host.center_y)
    return 'south' if south >= north else 'north'


def _get_room_side_ns(host: Element, furniture: List[Element]) -> str:
    """For N-S wall, determine if room is east or west based on furniture."""
    east = sum(1 for f in furniture if f.center_x > host.center_x)
    west = sum(1 for f in furniture if f.center_x < host.center_x)
    return 'east' if east >= west else 'west'


# ─────────────────────────────────────────────────────────────────
# FLOOR PLAN DRAWING
# ─────────────────────────────────────────────────────────────────

def world_to_paper(x_world, y_world, origin_x, origin_y):
    """Convert world meters to paper mm at SCALE, with Y-flip for SVG."""
    px = (x_world - origin_x) * PAPER_FACTOR
    py = -(y_world - origin_y) * PAPER_FACTOR  # Y-flip: north = up in world, up in SVG
    return px, py


def find_host_wall(opening: Element, walls: List[Element]) -> Optional[Element]:
    """Find which wall hosts a door or window by overlap."""
    best = None
    best_overlap = 0
    ox1, ox2 = opening.min_x, opening.max_x
    oy1, oy2 = opening.min_y, opening.max_y

    for w in walls:
        # Check XY overlap
        overlap_x = max(0, min(ox2, w.max_x) - max(ox1, w.min_x))
        overlap_y = max(0, min(oy2, w.max_y) - max(oy1, w.min_y))
        overlap = overlap_x * overlap_y if (overlap_x > 0 and overlap_y > 0) else 0

        # For thin walls, check proximity instead
        if overlap == 0:
            # Check if opening is within wall extents along major axis
            if w.is_ew_wall:
                if ox1 >= w.min_x - 0.1 and ox2 <= w.max_x + 0.1:
                    dist = abs(opening.center_y - w.center_y)
                    if dist < 0.5:
                        overlap = 1.0 / (dist + 0.01)
            elif w.is_ns_wall:
                if oy1 >= w.min_y - 0.1 and oy2 <= w.max_y + 0.1:
                    dist = abs(opening.center_x - w.center_x)
                    if dist < 0.5:
                        overlap = 1.0 / (dist + 0.01)

        if overlap > best_overlap:
            best_overlap = overlap
            best = w

    return best


def draw_floor_plan(elements: Dict[str, List[Element]], db_path: str,
                    meta: Optional[Dict]) -> str:
    """Generate floor plan SVG on A3 sheet with JKR title block and annotations."""
    _SVG_LOG.clear()
    _log(f"SVG writer: {db_path} scale=1:{SCALE}")

    walls = elements['walls']
    doors = elements['doors']
    windows = elements['windows']
    furniture = elements['furniture']

    _log(f"  elements: {len(walls)} walls, {len(furniture)} furniture, "
         f"{len(doors)+len(windows)} openings "
         f"({len(doors)} doors, {len(windows)} windows)")

    if not walls:
        print("No walls found in database.", file=sys.stderr)
        return ""

    # ── Compute extents ──
    all_elems = walls + doors + windows + furniture
    bld_min_x = min(e.min_x for e in all_elems)
    bld_max_x = max(e.max_x for e in all_elems)
    bld_min_y = min(e.min_y for e in all_elems)
    bld_max_y = max(e.max_y for e in all_elems)

    # Origin for coordinate transform (bottom-left of building)
    origin_x = bld_min_x
    origin_y = bld_max_y  # Top of building = Y origin (because SVG Y-flip)

    # Paper extents
    bld_w_paper = (bld_max_x - bld_min_x) * PAPER_FACTOR
    bld_h_paper = (bld_max_y - bld_min_y) * PAPER_FACTOR

    svg = SVGBuilder()

    # ── Sheet setup with tight-fit height ──
    sheet = dict(_get_sheet(meta))  # copy to avoid mutating original
    envelope_w = MARGIN_LEFT + bld_w_paper + MARGIN_RIGHT
    envelope_h = MARGIN_TOP + bld_h_paper + MARGIN_BOTTOM

    # Shrink sheet height to remove empty vertical space
    left_h = envelope_h + 20       # envelope + title/scale below
    right_h = 109 + 65             # title block fields + header/legend overhead
    tight_h = max(left_h, right_h) + sheet['margin_top'] + sheet['margin_bottom']
    sheet['height_mm'] = min(tight_h, sheet['height_mm'])

    sw = sheet['width_mm']
    sh = sheet['height_mm']
    svg.set_viewbox(0, 0, sw, sh)

    # Content area (left of title block)
    content_left = sheet['margin_left']
    content_top = sheet['margin_top']
    content_right = sw - sheet['margin_right'] - sheet['title_block_width']
    content_bottom = sh - sheet['margin_bottom']
    content_w = content_right - content_left
    content_h = content_bottom - content_top

    # Center envelope in content area
    envelope_x = content_left + max(0, (content_w - envelope_w) / 2)
    envelope_y = content_top + max(0, (content_h - envelope_h) / 2)

    # Building origin offset on sheet (where world_to_paper (0,0) maps)
    bld_ox = envelope_x + MARGIN_LEFT
    bld_oy = envelope_y + MARGIN_TOP

    def to_sheet(x_world, y_world):
        """Convert world coords to A3 sheet coords."""
        px, py = world_to_paper(x_world, y_world, origin_x, origin_y)
        return px + bld_ox, py + bld_oy

    # ── Sheet border ──
    draw_sheet_border(svg, sheet)

    # ── Title block ──
    if meta and 'title_fields' in meta:
        draw_title_block(svg, sheet, meta['title_fields'],
                         drawing_title='PELAN LANTAI / FLOOR PLAN',
                         drawing_no='A-01')

    # ── North arrow (top-right of content area) ──
    draw_north_arrow(svg, content_right - 10, content_top + 15)

    # ── Derive grids (snap to clean numbers) ──
    # §4.2a: columns always gridded. §4.2d: labels from template (skip I)
    columns = [e for e in elements.get('other', []) if e.ifc_class == 'IfcColumn']
    grids = snap_grids(derive_grids(walls, columns=columns))
    dims = generate_dimensions(grids)
    _log(f"  grids: {len(grids)} ({', '.join(g.label for g in grids)})")

    # ── Draw grid lines (dash-dot per 2d_grid_style) ──
    GRID_DASH = "4,1,1,1"  # dash-dot pattern per TB-LKTN

    for g in grids:
        if g.axis == 'x':
            px, _ = to_sheet(g.position, 0)
            y_top = bld_oy - MARGIN_TOP + 5
            y_bot = bld_oy + bld_h_paper + MARGIN_BOTTOM - 5
            svg.line('grid', px, y_top, px, y_bot,
                     COL_GRID, LW_GRID, dash=GRID_DASH)
            # Grid bubble at top
            bubble_y = bld_oy - MARGIN_TOP + GRID_CIRCLE_R + 3
            svg.circle('grid', px, bubble_y, GRID_CIRCLE_R,
                        COL_WALL, LW_WALL_INT, COL_BACKGROUND)
            svg.text('grid', px, bubble_y, g.label,
                     TXT_GRID, color=COL_WALL)
            # Grid bubble at bottom
            bubble_y_bot = bld_oy + bld_h_paper + MARGIN_BOTTOM - GRID_CIRCLE_R - 3
            svg.circle('grid', px, bubble_y_bot, GRID_CIRCLE_R,
                        COL_WALL, LW_WALL_INT, COL_BACKGROUND)
            svg.text('grid', px, bubble_y_bot, g.label,
                     TXT_GRID, color=COL_WALL)
        else:
            _, py = to_sheet(0, g.position)
            x_left = bld_ox - MARGIN_LEFT + 5
            x_right = bld_ox + bld_w_paper + MARGIN_RIGHT - 5
            svg.line('grid', x_left, py, x_right, py,
                     COL_GRID, LW_GRID, dash=GRID_DASH)
            # Grid bubble at left
            bubble_x = bld_ox - MARGIN_LEFT + GRID_CIRCLE_R + 3
            svg.circle('grid', bubble_x, py, GRID_CIRCLE_R,
                        COL_WALL, LW_WALL_INT, COL_BACKGROUND)
            svg.text('grid', bubble_x, py, g.label,
                     TXT_GRID, color=COL_WALL)
            # Grid bubble at right
            bubble_x_rt = bld_ox + bld_w_paper + MARGIN_RIGHT - GRID_CIRCLE_R - 3
            svg.circle('grid', bubble_x_rt, py, GRID_CIRCLE_R,
                        COL_WALL, LW_WALL_INT, COL_BACKGROUND)
            svg.text('grid', bubble_x_rt, py, g.label,
                     TXT_GRID, color=COL_WALL)

    # ── Draw furniture (light, behind walls) ──
    for f in furniture:
        sx, sy = to_sheet(f.min_x, f.max_y)
        w = f.width_x * PAPER_FACTOR
        h = f.width_y * PAPER_FACTOR
        svg.rect('furniture', sx, sy, w, h,
                 COL_FURNITURE, LW_FURNITURE, COL_FURN_FILL)
    _log(f"  drew furniture: {len(furniture)} entities")

    # ── Draw walls from mesh section contours ──
    section_elements = run_section_cut(db_path, cut_z=1.0)
    wall_contour_count = 0

    for se in section_elements:
        if se.category != 'CUT' or not se.contours:
            continue

        if se.ifc_class.startswith('IfcWall'):
            # Solid walls: filled black polygons
            lw = LW_WALL_EXT if 'Ext' in se.element_name else LW_WALL_INT
            for contour in se.contours:
                paper_pts = [to_sheet(x, y) for x, y in contour.points]
                svg.polygon('wall_fill', paper_pts, COL_WALL, lw,
                            fill=COL_WALL)
                wall_contour_count += 1

        elif se.ifc_class == 'IfcPlate':
            # Curtain wall glass: thin blue strokes
            for contour in se.contours:
                paper_pts = [to_sheet(x, y) for x, y in contour.points]
                svg.polygon('wall_stroke', paper_pts, COL_GLASS,
                            LW_WALL_GLASS)
                wall_contour_count += 1

        elif se.ifc_class == 'IfcMember':
            # Structural members: thin gray strokes
            for contour in se.contours:
                paper_pts = [to_sheet(x, y) for x, y in contour.points]
                svg.polygon('wall_stroke', paper_pts, COL_GRID,
                            LW_WALL_GLASS)
                wall_contour_count += 1

    print(f"  Section cut contours drawn: {wall_contour_count}")
    _log(f"  drew walls: {wall_contour_count} section-cut contours")

    # ── Draw openings (doors + windows) ──
    for opening in doors + windows:
        host = find_host_wall(opening, walls)
        if not host:
            continue

        # Opening position in sheet coords
        ox1, oy1 = to_sheet(opening.min_x, opening.max_y)
        ow = opening.width_x * PAPER_FACTOR
        oh = opening.width_y * PAPER_FACTOR

        if host.is_ew_wall or (host.width_y < host.width_x):
            # Opening in E-W wall
            wall_x1, wall_y1 = to_sheet(opening.min_x, host.max_y)
            wall_h = host.width_y * PAPER_FACTOR

            if opening.ifc_class == 'IfcWindow':
                # Window: two parallel lines + glass center line
                mid_y = wall_y1 + wall_h / 2
                svg.line('opening', ox1, mid_y - wall_h * 0.3,
                         ox1 + ow, mid_y - wall_h * 0.3,
                         COL_OPENING, LW_OPENING)
                svg.line('opening', ox1, mid_y + wall_h * 0.3,
                         ox1 + ow, mid_y + wall_h * 0.3,
                         COL_OPENING, LW_OPENING)
                svg.line('opening', ox1, mid_y, ox1 + ow, mid_y,
                         COL_GLASS, LW_WALL_GLASS)
            else:
                # Door: leaf line + arc with furniture-based swing direction
                door_w_paper = opening.width_x * PAPER_FACTOR
                wall_center_y = wall_y1 + wall_h / 2
                hinge_x = ox1
                hinge_y = wall_center_y

                room_side = _get_room_side_ew(host, furniture)
                svg.line('opening', hinge_x, hinge_y,
                         hinge_x + door_w_paper, hinge_y,
                         COL_OPENING, LW_OPENING)
                if room_side == 'south':
                    # Swing south (down in SVG): CW from 0° to 90°
                    svg.arc('opening', hinge_x, hinge_y, door_w_paper,
                            0, 90, COL_OPENING, LW_OPENING, cw=True)
                else:
                    # Swing north (up in SVG): CCW from 0° to 270°
                    svg.arc('opening', hinge_x, hinge_y, door_w_paper,
                            0, 270, COL_OPENING, LW_OPENING, cw=False)

        else:
            # Opening in N-S wall
            wall_x1, wall_y1 = to_sheet(host.min_x, opening.max_y)
            wall_w = host.width_x * PAPER_FACTOR

            if opening.ifc_class == 'IfcWindow':
                mid_x = wall_x1 + wall_w / 2
                svg.line('opening', mid_x - wall_w * 0.3, oy1,
                         mid_x - wall_w * 0.3, oy1 + oh,
                         COL_OPENING, LW_OPENING)
                svg.line('opening', mid_x + wall_w * 0.3, oy1,
                         mid_x + wall_w * 0.3, oy1 + oh,
                         COL_OPENING, LW_OPENING)
                svg.line('opening', mid_x, oy1, mid_x, oy1 + oh,
                         COL_GLASS, LW_WALL_GLASS)
            else:
                door_h_paper = opening.width_y * PAPER_FACTOR
                wall_center_x = wall_x1 + wall_w / 2
                hinge_y = oy1

                room_side = _get_room_side_ns(host, furniture)
                svg.line('opening', wall_center_x, hinge_y,
                         wall_center_x, hinge_y + door_h_paper,
                         COL_OPENING, LW_OPENING)
                if room_side == 'east':
                    # Swing east (right in SVG): CCW from 90° to 0°
                    svg.arc('opening', wall_center_x, hinge_y,
                            door_h_paper, 90, 0,
                            COL_OPENING, LW_OPENING, cw=False)
                else:
                    # Swing west (left in SVG): CW from 90° to 180°
                    svg.arc('opening', wall_center_x, hinge_y,
                            door_h_paper, 90, 180,
                            COL_OPENING, LW_OPENING, cw=True)

    # ── Door / Window annotation tags ──
    tag_meta = meta.get('tags', {}) if meta else {}
    door_tag = tag_meta.get('DOOR', {
        'tag_prefix': 'D', 'size_mm': 4.0,
        'text_size': 2.0, 'stroke_weight': 0.18})
    window_tag = tag_meta.get('WINDOW', {
        'tag_prefix': 'W', 'size_mm': 4.0,
        'text_size': 2.0, 'stroke_weight': 0.18})

    d_num = 0
    for opening in doors:
        host = find_host_wall(opening, walls)
        if not host:
            continue
        d_num += 1
        tag_label = f"{door_tag['tag_prefix']}{d_num}"
        tcx, tcy = to_sheet(opening.center_x, opening.center_y)
        tag_r = door_tag['size_mm'] / 2

        # Position tag outside wall (opposite side from window tags)
        if host.is_ew_wall or host.width_y < host.width_x:
            tcy -= host.width_y * PAPER_FACTOR / 2 + tag_r + 2
        else:
            tcx += host.width_x * PAPER_FACTOR / 2 + tag_r + 2

        svg.hexagon('label', tcx, tcy, tag_r,
                     COL_WALL, door_tag['stroke_weight'], COL_BACKGROUND)
        svg.text('label', tcx, tcy, tag_label, door_tag['text_size'])

    w_num = 0
    for opening in windows:
        host = find_host_wall(opening, walls)
        if not host:
            continue
        w_num += 1
        tag_label = f"{window_tag['tag_prefix']}{w_num}"
        tcx, tcy = to_sheet(opening.center_x, opening.center_y)
        tag_r = window_tag['size_mm'] / 2

        if host.is_ew_wall or host.width_y < host.width_x:
            tcy += host.width_y * PAPER_FACTOR / 2 + tag_r + 2
        else:
            tcx -= host.width_x * PAPER_FACTOR / 2 + tag_r + 2

        svg.hexagon('label', tcx, tcy, tag_r,
                     COL_WALL, window_tag['stroke_weight'], COL_BACKGROUND)
        svg.text('label', tcx, tcy, tag_label, window_tag['text_size'])
    _log(f"  drew openings: {d_num} door tags, {w_num} window tags")

    # ── Draw dimensions ──
    for dim in dims:
        if dim.axis == 'x':
            # Horizontal dimension above building
            x1_paper, _ = to_sheet(dim.start, 0)
            x2_paper, _ = to_sheet(dim.end, 0)
            dy = bld_oy - dim.offset

            # Dimension line
            svg.line('dimension', x1_paper, dy, x2_paper, dy,
                     COL_DIM, LW_DIMENSION)
            # Extension lines
            svg.line('dimension', x1_paper, bld_oy - DIM_GAP,
                     x1_paper, dy - DIM_EXTEND,
                     COL_DIM, LW_DIMENSION)
            svg.line('dimension', x2_paper, bld_oy - DIM_GAP,
                     x2_paper, dy - DIM_EXTEND,
                     COL_DIM, LW_DIMENSION)
            # Tick marks
            svg.tick_mark('dimension', x1_paper, dy, 'x', COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', x2_paper, dy, 'x', COL_DIM, LW_DIMENSION)
            # Text
            mid_x = (x1_paper + x2_paper) / 2
            svg.text('dimension', mid_x, dy - TXT_DIM * 0.7, dim.text, TXT_DIM)

        else:
            # Vertical dimension left of building
            _, y1_paper = to_sheet(0, dim.start)
            _, y2_paper = to_sheet(0, dim.end)
            dx = bld_ox - dim.offset

            svg.line('dimension', dx, y1_paper, dx, y2_paper,
                     COL_DIM, LW_DIMENSION)
            svg.line('dimension', bld_ox - DIM_GAP, y1_paper,
                     dx - DIM_EXTEND, y1_paper,
                     COL_DIM, LW_DIMENSION)
            svg.line('dimension', bld_ox - DIM_GAP, y2_paper,
                     dx - DIM_EXTEND, y2_paper,
                     COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', dx, y1_paper, 'y', COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', dx, y2_paper, 'y', COL_DIM, LW_DIMENSION)
            mid_y = (y1_paper + y2_paper) / 2
            svg.text('dimension', dx - TXT_DIM * 0.7, mid_y, dim.text, TXT_DIM,
                     rotate=-90)

    _log(f"  drew dims: {len(dims)} dimension strings")

    # ── Room labels ──
    room_labels = meta.get('room_labels', {}) if meta else {}
    rooms = infer_rooms(furniture, walls)
    for room_type, cx, cy, area in rooms:
        sx, sy = to_sheet(cx, cy)
        label_info = room_labels.get(room_type, {})
        label_text = label_info.get('label_text', room_type)
        svg.text('room_label', sx, sy - 2, label_text, TXT_ROOM, color=COL_DIM)
        if label_info.get('show_area', True):
            svg.text('room_label', sx, sy + 2.5, f'{area:.1f} m\u00b2',
                     TXT_DIM, color=COL_GRID)

    # ── Drawing title + scale bar (below bottom grid bubbles) ──
    title_x = bld_ox + bld_w_paper / 2
    title_y = bld_oy + bld_h_paper + MARGIN_BOTTOM + 2
    svg.text('label', title_x, title_y,
             '1.  PELAN LANTAI / FLOOR PLAN', TXT_TITLE)

    # Scale bar centered below title
    draw_scale_bar(svg, title_x - 25, title_y + TXT_TITLE + 3)

    # ── Door / Window schedule legend in title block ──
    draw_opening_legend(svg, sheet, doors, windows, walls)

    # ── Summary ──
    print(f"Floor plan: {len(walls)} walls, {len(doors)} doors, "
          f"{len(windows)} windows, {len(furniture)} furniture")
    print(f"  Building: {(bld_max_x-bld_min_x)*1000:.0f} x "
          f"{(bld_max_y-bld_min_y)*1000:.0f} mm")
    print(f"  Grids: {len([g for g in grids if g.axis=='x'])} vertical (A-...), "
          f"{len([g for g in grids if g.axis=='y'])} horizontal (1-...)")
    print(f"  Dimensions: {len(dims)} strings")
    print(f"  Annotations: {d_num} door tags, {w_num} window tags, "
          f"{len(rooms)} room labels")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(script_dir, 'output')
    svg_path = os.path.join(out_dir,
                            os.path.splitext(os.path.basename(db_path))[0]
                            + '_floor_plan.svg')
    _log(f"  SVG written: {svg_path}")

    return svg.to_string()

# ─────────────────────────────────────────────────────────────────
# ELEVATION DRAWING
# ─────────────────────────────────────────────────────────────────

def draw_elevation(elements: Dict[str, List[Element]], face: str,
                   db_path: str, meta: Optional[Dict]) -> str:
    """Generate elevation SVG on A3 sheet with border and title block.

    face: 'front' (south/-Y), 'rear' (north/+Y),
          'left' (west/-X), 'right' (east/+X)
    """
    _log(f"SVG writer elevation ({face}): {db_path} scale=1:{SCALE}")
    walls = elements['walls']
    doors = elements['doors']
    windows = elements['windows']
    roofs = elements['roofs']
    slabs = elements['slabs']

    _log(f"  elements: {len(walls)} walls, {len(doors)+len(windows)} openings, "
         f"{len(roofs)} roofs, {len(slabs)} slabs")

    if not walls:
        return ""

    # Determine view axis and horizontal coordinate.
    # h_sign = -1 for rear/right: viewer is on the far side so the building
    # is seen left-right REVERSED — the mirror of the front/left elevation.
    # Without this, front and rear both show the same tilt direction.
    v_key = lambda e: (e.min_z, e.max_z)
    if face == 'front':
        h_key  = lambda e: (e.min_x, e.max_x)
        h_sign = 1
        grid_axis = 'x'
        bld_min_y = min(e.min_y for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.min_y < bld_min_y + 1.0]
    elif face == 'rear':
        # Looking south from north: X is reversed — larger X is on the left
        h_key  = lambda e: (-e.max_x, -e.min_x)
        h_sign = -1
        grid_axis = 'x'
        bld_max_y = max(e.max_y for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.max_y > bld_max_y - 1.0]
    elif face == 'left':
        h_key  = lambda e: (e.min_y, e.max_y)
        h_sign = 1
        grid_axis = 'y'
        bld_min_x = min(e.min_x for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.min_x < bld_min_x + 1.0]
    else:  # right
        # Looking west from east: Y is reversed
        h_key  = lambda e: (-e.max_y, -e.min_y)
        h_sign = -1
        grid_axis = 'y'
        bld_max_x = max(e.max_x for e in walls)
        face_elems = [e for e in walls + doors + windows
                      if e.max_x > bld_max_x - 1.0]

    svg = SVGBuilder()

    # Compute extents of the elevation view
    all_vis = face_elems + roofs + slabs
    if not all_vis:
        return ""

    h_min = min(h_key(e)[0] for e in all_vis)
    h_max = max(h_key(e)[1] for e in all_vis)
    v_min = min(v_key(e)[0] for e in all_vis)
    v_max = max(v_key(e)[1] for e in all_vis)

    # Extend downward for apron/ground below FFL
    APRON_Z   = -0.100   # concrete apron level (100mm below FFL)
    GRD_Z     = -0.250   # natural ground (150mm below apron)
    v_min = min(v_min, GRD_Z)

    elev_w = (h_max - h_min) * PAPER_FACTOR
    elev_h = (v_max - v_min) * PAPER_FACTOR

    # ── Sheet setup with tight-fit height ──
    sheet = dict(_get_sheet(meta))  # copy to avoid mutating original
    envelope_w = MARGIN_LEFT + elev_w + MARGIN_RIGHT
    envelope_h = MARGIN_TOP + elev_h + MARGIN_BOTTOM

    # Shrink sheet height to remove empty vertical space
    left_h = envelope_h + 20       # envelope + title below
    right_h = 109 + 25             # title block fields + header overhead
    tight_h = max(left_h, right_h) + sheet['margin_top'] + sheet['margin_bottom']
    sheet['height_mm'] = min(tight_h, sheet['height_mm'])

    sw = sheet['width_mm']
    sh = sheet['height_mm']
    svg.set_viewbox(0, 0, sw, sh)

    # Content area (left of title block)
    content_left = sheet['margin_left']
    content_top = sheet['margin_top']
    content_right = sw - sheet['margin_right'] - sheet['title_block_width']
    content_bottom = sh - sheet['margin_bottom']
    content_w = content_right - content_left
    content_h = content_bottom - content_top

    # Center elevation in content area
    envelope_x = content_left + max(0, (content_w - envelope_w) / 2)
    envelope_y = content_top + max(0, (content_h - envelope_h) / 2)
    elev_ox = envelope_x + MARGIN_LEFT
    elev_oy = envelope_y + MARGIN_TOP

    def to_elev(h_val, v_val):
        """Convert elevation coords to sheet coords."""
        px = (h_val - h_min) * PAPER_FACTOR + elev_ox
        py = (v_max - v_val) * PAPER_FACTOR + elev_oy  # Z-up → SVG Y-down
        return px, py

    # ── Sheet border + title block ──
    draw_sheet_border(svg, sheet)

    face_titles = {
        'front': 'PANDANGAN HADAPAN / FRONT ELEVATION',
        'rear': 'PANDANGAN BELAKANG / REAR ELEVATION',
        'left': 'PANDANGAN KIRI / LEFT ELEVATION',
        'right': 'PANDANGAN KANAN / RIGHT ELEVATION',
    }
    face_idx = {'front': 2, 'rear': 3, 'left': 4, 'right': 5}

    if meta and 'title_fields' in meta:
        draw_title_block(svg, sheet, meta['title_fields'],
                         drawing_title=face_titles.get(face, face.upper()),
                         drawing_no=f'A-0{face_idx.get(face, 2)}')

    # ── Roof hatch pattern (vertical lines = metal roofing sheets) ──
    hatch_spacing = 1.0   # 1mm paper = 100mm at 1:100 — dense, reads as material
    svg.add_def(
        f'<pattern id="roof_hatch" x="0" y="0" '
        f'width="{hatch_spacing:.1f}" height="{hatch_spacing:.1f}" '
        f'patternUnits="userSpaceOnUse">'
        f'<line x1="{hatch_spacing/2:.1f}" y1="0" '
        f'x2="{hatch_spacing/2:.1f}" y2="{hatch_spacing:.1f}" '
        f'stroke="#000000" stroke-width="0.25"/>'
        f'</pattern>'
    )

    # ── Grid lines on elevation ──
    elev_grids = snap_grids(derive_grids(walls))
    _log(f"  grids: {len(elev_grids)} ({', '.join(g.label for g in elev_grids)})")
    # Apply h_sign: grid positions in world coords, flip for rear/right views
    face_grids_raw = [g for g in elev_grids if g.axis == grid_axis]
    face_grids = sorted(
        [type('G', (), {'label': g.label, 'axis': g.axis,
                        'position': g.position * h_sign})()
         for g in face_grids_raw],
        key=lambda g: g.position)

    # Extend h_min/h_max to include grid positions
    if face_grids:
        h_min = min(h_min, face_grids[0].position)
        h_max = max(h_max, face_grids[-1].position)

    GRID_ABOVE  = 20.0    # mm above building top to extend grid line
    BUBBLE_R    = GRID_CIRCLE_R
    BUBBLE_TOP  = 5.0     # mm gap above building top before bubble centre

    for g in face_grids:
        gx_paper, gy_bldg_top = to_elev(g.position, v_max)
        gy_line_top = gy_bldg_top - GRID_ABOVE
        gy_grd      = to_elev(g.position, GRD_Z)[1]
        # Vertical grid line: from below bubble to ground
        svg.line('grid', gx_paper, gy_line_top + BUBBLE_R * 2 + BUBBLE_TOP,
                 gx_paper, gy_grd,
                 COL_GRID, LW_GRID, dash="4,1,1,1")
        # Bubble (circle + label)
        bubble_cy = gy_line_top + BUBBLE_R + BUBBLE_TOP
        svg.circle('grid', gx_paper, bubble_cy, BUBBLE_R,
                   COL_WALL, LW_WALL_INT, COL_BACKGROUND)
        svg.text('grid', gx_paper, bubble_cy, g.label, TXT_GRID, color=COL_WALL)

    # ── Bay dimensions above grid bubbles ──
    if len(face_grids) >= 2:
        _, first_top = to_elev(face_grids[0].position, v_max)
        dim_y     = first_top - GRID_ABOVE + BUBBLE_TOP - BUBBLE_R * 2 - 4.0
        overall_y = dim_y - 8.0

        for i in range(len(face_grids) - 1):
            xa, _ = to_elev(face_grids[i].position, 0)
            xb, _ = to_elev(face_grids[i + 1].position, 0)
            bay_mm  = abs(face_grids[i + 1].position - face_grids[i].position) * 1000
            snapped = round(bay_mm / SNAP_MODULE) * SNAP_MODULE
            mid_x   = (xa + xb) / 2
            svg.line('dimension', xa, dim_y, xb, dim_y, COL_DIM, LW_DIMENSION)
            svg.line('dimension', xa, dim_y - 1.5, xa, dim_y + 1.5, COL_DIM, LW_DIMENSION)
            svg.line('dimension', xb, dim_y - 1.5, xb, dim_y + 1.5, COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', xa, dim_y, 'x', COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', xb, dim_y, 'x', COL_DIM, LW_DIMENSION)
            svg.text('dimension', mid_x, dim_y - 2.5, f'{int(snapped)}', TXT_DIM)

        x_first, _ = to_elev(face_grids[0].position, 0)
        x_last,  _ = to_elev(face_grids[-1].position, 0)
        total_mm      = abs(face_grids[-1].position - face_grids[0].position) * 1000
        snapped_total = round(total_mm / SNAP_MODULE) * SNAP_MODULE
        svg.line('dimension', x_first, overall_y, x_last, overall_y, COL_DIM, LW_DIMENSION)
        svg.line('dimension', x_first, overall_y - 1.5, x_first, overall_y + 1.5, COL_DIM, LW_DIMENSION)
        svg.line('dimension', x_last,  overall_y - 1.5, x_last,  overall_y + 1.5, COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', x_first, overall_y, 'x', COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', x_last,  overall_y, 'x', COL_DIM, LW_DIMENSION)
        svg.text('dimension', (x_first + x_last) / 2, overall_y - 2.5,
                 f'{int(snapped_total)}', TXT_DIM)

    # ── GRD. LEVEL bold ground line (ISO 128: widest line = visual base) ──
    gx1, gy_grd = to_elev(h_min - 0.5, GRD_Z)
    gx2, _      = to_elev(h_max + 0.5, GRD_Z)
    svg.line('wall_stroke', gx1, gy_grd, gx2, gy_grd, COL_WALL, LW_GROUND)

    # ── Concrete apron step (GRD_Z to APRON_Z, slightly wider than walls) ──
    ax1, ay_top = to_elev(h_min - 0.2, APRON_Z)
    ax2, ay_bot = to_elev(h_max + 0.2, GRD_Z)
    apron_w = ax2 - ax1
    apron_h = ay_bot - ay_top
    svg.rect('wall_fill', ax1, ay_top, apron_w, apron_h, COL_WALL, LW_WALL_INT, '#E0E0E0')

    # ── GRD. FLOOR LEVEL line (FFL) ──
    fx1, fy_ffl = to_elev(h_min - 0.3, 0.0)
    fx2, _      = to_elev(h_max + 0.3, 0.0)
    svg.line('wall_stroke', fx1, fy_ffl, fx2, fy_ffl, COL_WALL, 0.35)

    # ── Building facade silhouette — solid grey base before individual elements ──
    # Draw the full facade bounding box first; element rects punch openings on top.
    sil_x, sil_y_top = to_elev(h_min, v_max)
    _, sil_y_bot = to_elev(h_min, 0.0)   # FFL = Z 0.0
    sil_w = (h_max - h_min) * PAPER_FACTOR
    sil_h = sil_y_bot - sil_y_top
    svg.rect('wall_fill', sil_x, sil_y_top, sil_w, sil_h, 'none', 0, '#D0D0D0')

    # ── Draw walls in elevation ──
    for e in face_elems:
        hh = h_key(e)
        vv = v_key(e)
        x1, y1 = to_elev(hh[0], vv[1])
        w = (hh[1] - hh[0]) * PAPER_FACTOR
        h = (vv[1] - vv[0]) * PAPER_FACTOR

        if e.ifc_class in ('IfcWall', 'IfcPlate'):
            if e.is_glass:
                svg.rect('wall_fill', x1, y1, max(w, 0.5), max(h, 0.5),
                         COL_GLASS, LW_WALL_GLASS, COL_GLASS, 0.2)
            else:
                svg.rect('wall_fill', x1, y1, max(w, 0.5), max(h, 0.5),
                         COL_WALL, LW_WALL_EXT, '#C8C8C8')
        elif e.ifc_class == 'IfcWindow':
            svg.rect('opening', x1, y1, w, h,
                     COL_OPENING, LW_OPENING, '#FFFFFF', 1.0)
            # Louvre lines (horizontal stripes, 150mm real spacing = 1.5mm paper at 1:100)
            louvre_spacing = 1.5   # mm paper
            n_louvres = max(1, int(h / louvre_spacing) - 1)
            for li in range(1, n_louvres + 1):
                ly = y1 + li * (h / (n_louvres + 1))
                svg.line('opening', x1, ly, x1 + w, ly, COL_WALL, 0.13)
        elif e.ifc_class == 'IfcDoor':
            svg.rect('opening', x1, y1, w, h,
                     COL_OPENING, LW_OPENING, '#E8E8E8')

    _log(f"  drew walls: {len(face_elems)} elevation elements")

    # ── Draw roof outline — mesh-based silhouette via convex hull ──
    roof_hull = roof_silhouette(db_path, face)
    if roof_hull:
        hull_pts = [to_elev(h, z) for h, z in roof_hull]
        # Fill with hatch pattern, then draw bold outline
        pts_str = " ".join(f"{px:.3f},{py:.3f}" for px, py in hull_pts)
        svg._add('wall_fill',
            f'<polygon points="{pts_str}" stroke="none" '
            f'fill="url(#roof_hatch)" opacity="1.0"/>')
        svg.polygon('wall_stroke', hull_pts, COL_WALL, LW_WALL_EXT, 'none')

        # Eave line — bold
        eave_z_hull = min(z for _, z in roof_hull)
        eave_pts = sorted([(h, z) for h, z in roof_hull
                           if abs(z - eave_z_hull) < 0.05])
        if len(eave_pts) >= 2:
            ex1, ey1 = to_elev(eave_pts[0][0], eave_pts[0][1])
            ex2, ey2 = to_elev(eave_pts[-1][0], eave_pts[-1][1])
            svg.line('wall_stroke', ex1, ey1, ex2, ey2, COL_WALL, LW_WALL_EXT)

    # ── Height levels: markers and vertical dimensions ──
    # APRON and GRD are drawn as geometry (apron rect, bold ground line) but
    # not added as label markers — they sit ~100–250mm below FFL which is
    # only 1–2.5mm paper at 1:100, causing label crowding against the margin.
    levels = detect_levels(elements)
    levels = sorted(levels, key=lambda lv: lv[1])

    LEVEL_LABELS = {
        'FFL':   'GRD. FLOOR LEVEL',
        'SILL':  'WINDOW SILL LEVEL',
        'HEAD':  'WINDOW / DOOR HEAD',
        'CLG':   'BEAM / CEILING LEVEL',
        'RIDGE': 'RIDGE LEVEL',
    }
    # Dashed lines: SILL and HEAD are shorter (span only the opening zone)
    # FFL, CLG, RIDGE span the full building width — TB-LKTN convention
    FULL_WIDTH = {'FFL', 'CLG', 'RIDGE'}

    # Horizontal dashed lines at each level
    for lbl, lz in levels:
        _, ly = to_elev(h_min, lz)
        if lbl in FULL_WIDTH:
            lx_left  = elev_ox - 5
            lx_right = elev_ox + elev_w + 5
        else:
            # Span only to the DIM column on the right so they read as datum
            lx_left  = elev_ox
            lx_right = elev_ox + elev_w + DIM_OFFSET_1
        svg.line('grid', lx_left, ly, lx_right, ly,
                 COL_GRID, LW_GRID, dash="4,1,1,1")

    # Level markers (left side) — enforce minimum label spacing so closely
    # spaced levels (APRON/GRD only 1-2mm apart at 1:100) don't overlap.
    MIN_LABEL_GAP = TXT_DIM * 3.0   # 3 text-heights between label rows
    marker_x = elev_ox - 5

    # Build list of (paper_y, lbl, lz) sorted top-to-bottom
    level_paper_y = []
    for lbl, lz in levels:
        _, ly = to_elev(h_min, lz)
        level_paper_y.append([ly, lbl, lz])
    level_paper_y.sort(key=lambda r: r[0])   # top = smaller y

    # Push labels apart (downward) when they would collide
    label_y = [r[0] for r in level_paper_y]
    for i in range(1, len(label_y)):
        if label_y[i] - label_y[i - 1] < MIN_LABEL_GAP:
            label_y[i] = label_y[i - 1] + MIN_LABEL_GAP

    for (ly, lbl, lz), label_ly in zip(level_paper_y, label_y):
        # Horizontal tick at TRUE level position
        svg.line('dimension', marker_x - 18, ly, marker_x, ly,
                 COL_DIM, 0.35)
        # Filled triangle pointer (bold, ISO 128 level marker style)
        svg.polygon('dimension',
                    [(marker_x, ly), (marker_x - 3.0, ly - 1.8),
                     (marker_x - 3.0, ly + 1.8)],
                    COL_WALL, 0.35, fill=COL_WALL)
        # Leader line from label_ly to true ly if offset
        if abs(label_ly - ly) > 0.5:
            svg.line('dimension', marker_x - 16, ly,
                     marker_x - 16, label_ly, COL_DIM, 0.18)
        # Level name (above label_ly)
        svg.text('label', marker_x - 21, label_ly - TXT_DIM * 0.7,
                 LEVEL_LABELS.get(lbl, lbl), TXT_DIM, anchor='end')
        # Elevation value (below label_ly)
        sign = '+' if lz >= 0 else ''
        svg.text('label', marker_x - 21, label_ly + TXT_DIM * 0.7,
                 f'{sign}{lz:.3f}', TXT_DIM, anchor='end')

    # Vertical height dimensions (right side)
    if len(levels) >= 2:
        dim_x = elev_ox + elev_w + DIM_OFFSET_1

        # Bay dimensions between adjacent levels
        for i in range(len(levels) - 1):
            _, y_top = to_elev(h_min, levels[i + 1][1])
            _, y_bot = to_elev(h_min, levels[i][1])
            dist_mm = (levels[i + 1][1] - levels[i][1]) * 1000
            snapped = round(dist_mm / SNAP_MODULE) * SNAP_MODULE

            # Vertical dimension line
            svg.line('dimension', dim_x, y_top, dim_x, y_bot,
                     COL_DIM, LW_DIMENSION)
            # Extension lines
            svg.line('dimension', elev_ox + elev_w + DIM_GAP, y_top,
                     dim_x + DIM_EXTEND, y_top, COL_DIM, LW_DIMENSION)
            svg.line('dimension', elev_ox + elev_w + DIM_GAP, y_bot,
                     dim_x + DIM_EXTEND, y_bot, COL_DIM, LW_DIMENSION)
            # Tick marks
            svg.tick_mark('dimension', dim_x, y_top, 'y', COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', dim_x, y_bot, 'y', COL_DIM, LW_DIMENSION)
            # Text
            mid_y = (y_top + y_bot) / 2
            svg.text('dimension', dim_x + TXT_DIM * 0.7, mid_y,
                     f'{int(snapped)}', TXT_DIM, rotate=90)

        # Overall height dimension (second row)
        if len(levels) >= 3:
            dim_x2 = elev_ox + elev_w + DIM_OFFSET_2
            _, y_top = to_elev(h_min, levels[-1][1])
            _, y_bot = to_elev(h_min, levels[0][1])
            total_mm = (levels[-1][1] - levels[0][1]) * 1000
            snapped_total = round(total_mm / SNAP_MODULE) * SNAP_MODULE

            svg.line('dimension', dim_x2, y_top, dim_x2, y_bot,
                     COL_DIM, LW_DIMENSION)
            svg.line('dimension', elev_ox + elev_w + DIM_GAP, y_top,
                     dim_x2 + DIM_EXTEND, y_top, COL_DIM, LW_DIMENSION)
            svg.line('dimension', elev_ox + elev_w + DIM_GAP, y_bot,
                     dim_x2 + DIM_EXTEND, y_bot, COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', dim_x2, y_top, 'y', COL_DIM, LW_DIMENSION)
            svg.tick_mark('dimension', dim_x2, y_bot, 'y', COL_DIM, LW_DIMENSION)
            mid_y = (y_top + y_bot) / 2
            svg.text('dimension', dim_x2 + TXT_DIM * 0.7, mid_y,
                     f'{int(snapped_total)}', TXT_DIM, rotate=90)

    # Note: ceiling overlap line belongs on SECTION drawings, not elevations.
    # Elevations show exterior face only. Ceiling level is already indicated
    # by the level marker (triangle + "CEILING LEVEL +2.300") per convention.

    # Title
    title_x = elev_ox + elev_w / 2
    title_y = elev_oy + elev_h + MARGIN_BOTTOM - 8
    svg.text('label', title_x, title_y,
             face_titles.get(face, face.upper()), TXT_TITLE)
    svg.text('label', title_x, title_y + TXT_TITLE + 2,
             f'scale 1:{SCALE}', TXT_DIM, color=COL_GRID)

    print(f"Elevation ({face}): {len(face_elems)} elements, "
          f"{len(roofs)} roof, {len(slabs)} slab")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(script_dir, 'output')
    svg_path = os.path.join(out_dir,
                            os.path.splitext(os.path.basename(db_path))[0]
                            + f'_{face}_elevation.svg')
    _log(f"  SVG written: {svg_path}")

    return svg.to_string()

# ─────────────────────────────────────────────────────────────────
# PAIRED ELEVATION SHEET (two elevations stacked on one A3)
# ─────────────────────────────────────────────────────────────────

def combine_elevation_svgs(svg1_str: str, svg2_str: str,
                           face_a: str, face_b: str,
                           gap_mm: float = 15.0) -> str:
    """Stack two elevation SVGs vertically on one sheet.

    Each SVG viewBox is in mm. The top elevation (face_a) sits above,
    the bottom elevation (face_b) sits below, separated by gap_mm.
    Preserves all defs (patterns, styles) from both.
    """
    ET.register_namespace('', 'http://www.w3.org/2000/svg')

    def parse_viewbox(svg_str):
        """Return (min_x, min_y, width, height) from SVG viewBox attribute."""
        start = svg_str.find('viewBox="') + 9
        end   = svg_str.find('"', start)
        parts = svg_str[start:end].split()
        return float(parts[0]), float(parts[1]), float(parts[2]), float(parts[3])

    def extract_body(svg_str):
        """Extract inner SVG content: defs items and layer groups."""
        # Strip XML declaration and outer <svg> wrapper — keep <defs> + <g> elements
        root = ET.fromstring(svg_str.encode())
        ns = {'svg': 'http://www.w3.org/2000/svg'}
        defs_content = []
        groups = []
        for child in root:
            tag = child.tag.split('}')[-1]
            if tag == 'defs':
                for d in child:
                    defs_content.append(ET.tostring(d, encoding='unicode'))
            elif tag == 'g':
                groups.append(ET.tostring(child, encoding='unicode'))
        return defs_content, groups

    vb1 = parse_viewbox(svg1_str)
    vb2 = parse_viewbox(svg2_str)

    w = max(vb1[2], vb2[2])
    h = vb1[3] + gap_mm + vb2[3]

    defs1, groups1 = extract_body(svg1_str)
    defs2, groups2 = extract_body(svg2_str)

    # Deduplicate defs by id
    all_defs = {}
    for d in defs1 + defs2:
        key = d[:60]  # rough dedup key
        all_defs[key] = d

    px_w = int(w * 3)
    px_h = int(h * 3)

    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.2f} {h:.2f}" '
        f'width="{px_w}" height="{px_h}" style="background:#FFFFFF">',
        '  <defs>',
        '    <style>text { font-family: "Arial", "Helvetica", sans-serif; }</style>',
    ]
    for d in all_defs.values():
        lines.append(f'    {d}')
    lines.append('  </defs>')

    # face_a (top drawing)
    lines.append(f'  <g id="elev_{face_a}" transform="translate(0,0)">')
    lines.extend(f'    {g}' for g in groups1)
    lines.append('  </g>')

    # face_b (bottom drawing) — shifted down
    y_shift = vb1[3] + gap_mm
    lines.append(f'  <g id="elev_{face_b}" transform="translate(0,{y_shift:.2f})">')
    lines.extend(f'    {g}' for g in groups2)
    lines.append('  </g>')

    lines.append('</svg>')
    return '\n'.join(lines)


# ─────────────────────────────────────────────────────────────────
# ROOF PLAN DRAWING
# ─────────────────────────────────────────────────────────────────

def draw_roof_plan(elements: Dict[str, List[Element]], db_path: str,
                   meta: Optional[Dict]) -> str:
    """Generate roof plan SVG — top-down view of roof with ridge, slope arrows."""
    _log(f"SVG writer roof plan: {db_path} scale=1:{SCALE}")

    walls = elements['walls']
    roofs = elements['roofs']

    _log(f"  elements: {len(walls)} walls, {len(roofs)} roofs")

    if not roofs:
        print("No roof elements found.", file=sys.stderr)
        return ""

    # ── Roof extent (eave outline) ──
    roof_min_x = min(r.min_x for r in roofs)
    roof_max_x = max(r.max_x for r in roofs)
    roof_min_y = min(r.min_y for r in roofs)
    roof_max_y = max(r.max_y for r in roofs)
    roof_max_z = max(r.max_z for r in roofs)

    # ── Find ridge line via section cut near peak ──
    ridge_y = (roof_min_y + roof_max_y) / 2  # default: center
    try:
        ridge_cut = run_section_cut(db_path, cut_z=roof_max_z - 0.1)
        for se in ridge_cut:
            if se.ifc_class == 'IfcRoof' and se.contours:
                all_pts = [p for c in se.contours for p in c.points]
                if all_pts:
                    ridge_y = sum(p[1] for p in all_pts) / len(all_pts)
                break
    except Exception:
        pass

    # ── Building footprint from walls ──
    bld_min_x = min(w.min_x for w in walls)
    bld_max_x = max(w.max_x for w in walls)
    bld_min_y = min(w.min_y for w in walls)
    bld_max_y = max(w.max_y for w in walls)

    # Use roof extent as drawing extent (roof overhangs beyond walls)
    origin_x = roof_min_x
    origin_y = roof_max_y  # top in world = top in SVG (Y-flip)

    plan_w = (roof_max_x - roof_min_x) * PAPER_FACTOR
    plan_h = (roof_max_y - roof_min_y) * PAPER_FACTOR

    svg = SVGBuilder()

    # ── Tight-fit sheet ──
    sheet = dict(_get_sheet(meta))
    envelope_w = MARGIN_LEFT + plan_w + MARGIN_RIGHT
    envelope_h = MARGIN_TOP + plan_h + MARGIN_BOTTOM

    left_h = envelope_h + 20
    right_h = 109 + 25
    tight_h = max(left_h, right_h) + sheet['margin_top'] + sheet['margin_bottom']
    sheet['height_mm'] = min(tight_h, sheet['height_mm'])

    sw = sheet['width_mm']
    sh = sheet['height_mm']
    svg.set_viewbox(0, 0, sw, sh)

    content_left = sheet['margin_left']
    content_top = sheet['margin_top']
    content_right = sw - sheet['margin_right'] - sheet['title_block_width']
    content_bottom = sh - sheet['margin_bottom']
    content_w = content_right - content_left
    content_h = content_bottom - content_top

    envelope_x = content_left + max(0, (content_w - envelope_w) / 2)
    envelope_y = content_top + max(0, (content_h - envelope_h) / 2)
    plan_ox = envelope_x + MARGIN_LEFT
    plan_oy = envelope_y + MARGIN_TOP

    def to_sheet(x_world, y_world):
        px = (x_world - origin_x) * PAPER_FACTOR + plan_ox
        py = -(y_world - origin_y) * PAPER_FACTOR + plan_oy
        return px, py

    # ── Sheet border + title block ──
    draw_sheet_border(svg, sheet)
    if meta and 'title_fields' in meta:
        draw_title_block(svg, sheet, meta['title_fields'],
                         drawing_title='PELAN BUMBUNG / ROOF PLAN',
                         drawing_no='A-06')

    # ── Building footprint (dashed, light) ──
    bfx1, bfy1 = to_sheet(bld_min_x, bld_max_y)
    bfx2, bfy2 = to_sheet(bld_max_x, bld_min_y)
    bfw = bfx2 - bfx1
    bfh = bfy2 - bfy1
    svg.rect('grid', bfx1, bfy1, bfw, bfh, COL_GRID, LW_GRID)

    # ── Roof outline (bold, solid — eave line) ──
    rx1, ry1 = to_sheet(roof_min_x, roof_max_y)
    rx2, ry2 = to_sheet(roof_max_x, roof_min_y)
    rw = rx2 - rx1
    rh = ry2 - ry1
    svg.rect('wall_stroke', rx1, ry1, rw, rh, COL_WALL, LW_WALL_EXT)

    # ── Ridge line (dashed, medium weight) ──
    rdg_x1, rdg_y = to_sheet(roof_min_x, ridge_y)
    rdg_x2, _ = to_sheet(roof_max_x, ridge_y)
    svg.line('wall_stroke', rdg_x1, rdg_y, rdg_x2, rdg_y,
             COL_WALL, LW_WALL_INT, dash="6,2")
    # Label
    rdg_mid_x = (rdg_x1 + rdg_x2) / 2
    svg.text('label', rdg_mid_x, rdg_y - 3, 'RABUNG / RIDGE',
             TXT_DIM, color=COL_DIM)

    # ── Eave labels ──
    eave_lx = (rx1 + rx2) / 2
    svg.text('label', eave_lx, ry1 - 2, 'CUCURAN / EAVE', TXT_DIM, color=COL_DIM)
    svg.text('label', eave_lx, ry2 + 3.5, 'CUCURAN / EAVE', TXT_DIM, color=COL_DIM)

    # ── Slope direction arrows ──
    # North slope: ridge → north eave (increasing Y in world = up in SVG)
    # South slope: ridge → south eave (decreasing Y in world = down in SVG)
    arrow_spacing = plan_w / 5
    arrow_len_n = (roof_max_y - ridge_y) * PAPER_FACTOR * 0.4
    arrow_len_s = (ridge_y - roof_min_y) * PAPER_FACTOR * 0.4
    head_size = 1.5

    for i in range(1, 5):
        ax = rx1 + arrow_spacing * i

        # North slope arrow (from ridge toward top eave)
        n_start_y = rdg_y - 5
        n_end_y = n_start_y - arrow_len_n
        svg.line('dimension', ax, n_start_y, ax, n_end_y, COL_DIM, LW_DIMENSION)
        svg.polygon('dimension',
                    [(ax, n_end_y), (ax - head_size, n_end_y + head_size * 1.5),
                     (ax + head_size, n_end_y + head_size * 1.5)],
                    COL_DIM, LW_DIMENSION, fill=COL_DIM)

        # South slope arrow (from ridge toward bottom eave)
        s_start_y = rdg_y + 5
        s_end_y = s_start_y + arrow_len_s
        svg.line('dimension', ax, s_start_y, ax, s_end_y, COL_DIM, LW_DIMENSION)
        svg.polygon('dimension',
                    [(ax, s_end_y), (ax - head_size, s_end_y - head_size * 1.5),
                     (ax + head_size, s_end_y - head_size * 1.5)],
                    COL_DIM, LW_DIMENSION, fill=COL_DIM)

    # ── Overhang dimensions (distance from wall to eave) ──
    overhang_n = (roof_max_y - bld_max_y) * 1000  # mm
    overhang_s = (bld_min_y - roof_min_y) * 1000
    overhang_e = (roof_max_x - bld_max_x) * 1000
    overhang_w = (bld_min_x - roof_min_x) * 1000

    # North overhang dimension (right side, vertical)
    if overhang_n > 50:
        _, oy_wall = to_sheet(0, bld_max_y)
        _, oy_eave = to_sheet(0, roof_max_y)
        ox_dim = rx2 + DIM_OFFSET_1
        svg.line('dimension', ox_dim, oy_wall, ox_dim, oy_eave,
                 COL_DIM, LW_DIMENSION)
        svg.line('dimension', rx2 + DIM_GAP, oy_wall,
                 ox_dim + DIM_EXTEND, oy_wall, COL_DIM, LW_DIMENSION)
        svg.line('dimension', rx2 + DIM_GAP, oy_eave,
                 ox_dim + DIM_EXTEND, oy_eave, COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_dim, oy_wall, 'y', COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_dim, oy_eave, 'y', COL_DIM, LW_DIMENSION)
        mid_ov = (oy_wall + oy_eave) / 2
        svg.text('dimension', ox_dim + TXT_DIM * 0.7, mid_ov,
                 f'{int(round(overhang_n / SNAP_MODULE) * SNAP_MODULE)}',
                 TXT_DIM, rotate=90)

    # South overhang dimension (right side, vertical)
    if overhang_s > 50:
        _, oy_wall = to_sheet(0, bld_min_y)
        _, oy_eave = to_sheet(0, roof_min_y)
        ox_dim = rx2 + DIM_OFFSET_1
        svg.line('dimension', ox_dim, oy_wall, ox_dim, oy_eave,
                 COL_DIM, LW_DIMENSION)
        svg.line('dimension', rx2 + DIM_GAP, oy_wall,
                 ox_dim + DIM_EXTEND, oy_wall, COL_DIM, LW_DIMENSION)
        svg.line('dimension', rx2 + DIM_GAP, oy_eave,
                 ox_dim + DIM_EXTEND, oy_eave, COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_dim, oy_wall, 'y', COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_dim, oy_eave, 'y', COL_DIM, LW_DIMENSION)
        mid_ov = (oy_wall + oy_eave) / 2
        svg.text('dimension', ox_dim + TXT_DIM * 0.7, mid_ov,
                 f'{int(round(overhang_s / SNAP_MODULE) * SNAP_MODULE)}',
                 TXT_DIM, rotate=90)

    # East overhang dimension (top side, horizontal)
    if overhang_e > 50:
        ox_wall, _ = to_sheet(bld_max_x, 0)
        ox_eave, _ = to_sheet(roof_max_x, 0)
        oy_dim = ry1 - DIM_OFFSET_1
        svg.line('dimension', ox_wall, oy_dim, ox_eave, oy_dim,
                 COL_DIM, LW_DIMENSION)
        svg.line('dimension', ox_wall, ry1 - DIM_GAP,
                 ox_wall, oy_dim - DIM_EXTEND, COL_DIM, LW_DIMENSION)
        svg.line('dimension', ox_eave, ry1 - DIM_GAP,
                 ox_eave, oy_dim - DIM_EXTEND, COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_wall, oy_dim, 'x', COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_eave, oy_dim, 'x', COL_DIM, LW_DIMENSION)
        mid_ov = (ox_wall + ox_eave) / 2
        svg.text('dimension', mid_ov, oy_dim - TXT_DIM * 0.7,
                 f'{int(round(overhang_e / SNAP_MODULE) * SNAP_MODULE)}',
                 TXT_DIM)

    # West overhang dimension (top side, horizontal)
    if overhang_w > 50:
        ox_eave, _ = to_sheet(roof_min_x, 0)
        ox_wall, _ = to_sheet(bld_min_x, 0)
        oy_dim = ry1 - DIM_OFFSET_1
        svg.line('dimension', ox_eave, oy_dim, ox_wall, oy_dim,
                 COL_DIM, LW_DIMENSION)
        svg.line('dimension', ox_eave, ry1 - DIM_GAP,
                 ox_eave, oy_dim - DIM_EXTEND, COL_DIM, LW_DIMENSION)
        svg.line('dimension', ox_wall, ry1 - DIM_GAP,
                 ox_wall, oy_dim - DIM_EXTEND, COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_eave, oy_dim, 'x', COL_DIM, LW_DIMENSION)
        svg.tick_mark('dimension', ox_wall, oy_dim, 'x', COL_DIM, LW_DIMENSION)
        mid_ov = (ox_eave + ox_wall) / 2
        svg.text('dimension', mid_ov, oy_dim - TXT_DIM * 0.7,
                 f'{int(round(overhang_w / SNAP_MODULE) * SNAP_MODULE)}',
                 TXT_DIM)

    # ── Grids ──
    grids = snap_grids(derive_grids(walls))
    _log(f"  grids: {len(grids)} ({', '.join(g.label for g in grids)})")
    GRID_DASH = "4,1,1,1"

    for g in grids:
        if g.axis == 'x':
            px, _ = to_sheet(g.position, 0)
            y_top = plan_oy - MARGIN_TOP + 5
            y_bot = plan_oy + plan_h + MARGIN_BOTTOM - 5
            svg.line('grid', px, y_top, px, y_bot,
                     COL_GRID, LW_GRID, dash=GRID_DASH)
            bubble_y = plan_oy - MARGIN_TOP + GRID_CIRCLE_R + 3
            svg.circle('grid', px, bubble_y, GRID_CIRCLE_R,
                        COL_GRID, LW_GRID, COL_BACKGROUND)
            svg.text('grid', px, bubble_y, g.label, TXT_GRID, color=COL_GRID)
            bubble_y_bot = plan_oy + plan_h + MARGIN_BOTTOM - GRID_CIRCLE_R - 3
            svg.circle('grid', px, bubble_y_bot, GRID_CIRCLE_R,
                        COL_GRID, LW_GRID, COL_BACKGROUND)
            svg.text('grid', px, bubble_y_bot, g.label, TXT_GRID, color=COL_GRID)
        else:
            _, py = to_sheet(0, g.position)
            x_left = plan_ox - MARGIN_LEFT + 5
            x_right = plan_ox + plan_w + MARGIN_RIGHT - 5
            svg.line('grid', x_left, py, x_right, py,
                     COL_GRID, LW_GRID, dash=GRID_DASH)
            bubble_x = plan_ox - MARGIN_LEFT + GRID_CIRCLE_R + 3
            svg.circle('grid', bubble_x, py, GRID_CIRCLE_R,
                        COL_GRID, LW_GRID, COL_BACKGROUND)
            svg.text('grid', bubble_x, py, g.label, TXT_GRID, color=COL_GRID)
            bubble_x_rt = plan_ox + plan_w + MARGIN_RIGHT - GRID_CIRCLE_R - 3
            svg.circle('grid', bubble_x_rt, py, GRID_CIRCLE_R,
                        COL_GRID, LW_GRID, COL_BACKGROUND)
            svg.text('grid', bubble_x_rt, py, g.label, TXT_GRID, color=COL_GRID)

    # ── Title ──
    title_x = plan_ox + plan_w / 2
    title_y = plan_oy + plan_h + MARGIN_BOTTOM + 2
    svg.text('label', title_x, title_y,
             'PELAN BUMBUNG / ROOF PLAN', TXT_TITLE)
    svg.text('label', title_x, title_y + TXT_TITLE + 2,
             f'scale 1:{SCALE}', TXT_DIM, color=COL_GRID)

    print(f"Roof plan: {len(roofs)} roof element(s), "
          f"ridge Y={ridge_y:.3f}m, "
          f"overhang N={overhang_n:.0f} S={overhang_s:.0f} "
          f"E={overhang_e:.0f} W={overhang_w:.0f} mm")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(script_dir, 'output')
    svg_path = os.path.join(out_dir,
                            os.path.splitext(os.path.basename(db_path))[0]
                            + '_roof_plan.svg')
    _log(f"  SVG written: {svg_path}")

    return svg.to_string()


# ─────────────────────────────────────────────────────────────────
# LOG FLUSH
# ─────────────────────────────────────────────────────────────────

def _write_svg_log(out_dir: str) -> None:
    """Write buffered _SVG_LOG lines to output/svg_writer_log.txt."""
    if not _SVG_LOG:
        return
    import datetime
    os.makedirs(out_dir, exist_ok=True)
    log_path = os.path.join(out_dir, 'svg_writer_log.txt')
    stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    with open(log_path, 'w') as f:
        f.write(f"SVG Writer Log — {stamp}\n")
        f.write("=" * 60 + "\n\n")
        for line in _SVG_LOG:
            f.write(line + "\n")
    print(f"  Log: {log_path}")


# ─────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 drawing_writer.py <output.db> [options]")
        print()
        print("Options:")
        print("  --floor-plan          Generate floor plan SVG")
        print("  --roof-plan           Generate roof plan SVG")
        print("  --elevation FACE      Generate elevation SVG (front|rear|left|right)")
        print("  --all                 Generate all SVG drawings")
        print("  --dxf                 Also generate DXF files alongside SVGs")
        print()
        print("Examples:")
        print("  python3 drawing_writer.py samplehouse.db --all")
        print("  python3 drawing_writer.py samplehouse.db --all --dxf")
        print("  python3 drawing_writer.py samplehouse.db --elevation front --dxf")
        sys.exit(1)

    db_path = sys.argv[1]
    if not os.path.exists(db_path):
        print(f"Database not found: {db_path}", file=sys.stderr)
        sys.exit(1)

    # Parse options
    args = sys.argv[2:]
    do_plan = '--floor-plan' in args or '--all' in args or not args
    do_roof = '--roof-plan' in args or '--all' in args
    do_dxf  = '--dxf' in args
    do_elev = []
    if '--all' in args:
        do_elev = ['front', 'rear', 'left', 'right']
    elif '--elevation' in args:
        idx = args.index('--elevation')
        if idx + 1 < len(args):
            do_elev = [args[idx + 1]]

    # Read elements and metadata
    elements = read_elements(db_path, storey_filter='ground')
    meta = read_drawing_metadata()

    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(script_dir, 'output')
    os.makedirs(out_dir, exist_ok=True)
    db_stem = os.path.splitext(os.path.basename(db_path))[0]

    # Lazy-import DXF writer only when --dxf requested
    dxf_writer = None
    if do_dxf:
        try:
            import drawing_writer_dxf as _dxf
            dxf_writer = _dxf
        except ImportError:
            print("Warning: ezdxf not installed — skipping DXF output. "
                  "Install with: pip3 install ezdxf", file=sys.stderr)

    if do_plan:
        svg_content = draw_floor_plan(elements, db_path, meta)
        if svg_content:
            out_path = os.path.join(out_dir, f'{db_stem}_floor_plan.svg')
            with open(out_path, 'w') as f:
                f.write(svg_content)
            print(f"  → {out_path}")
        if dxf_writer:
            dxf_writer.write_floor_plan_dxf(
                db_path, os.path.join(out_dir, f'{db_stem}_floor_plan.dxf'))

    if do_roof:
        svg_content = draw_roof_plan(elements, db_path, meta)
        if svg_content:
            out_path = os.path.join(out_dir, f'{db_stem}_roof_plan.svg')
            with open(out_path, 'w') as f:
                f.write(svg_content)
            print(f"  → {out_path}")

    elev_svgs = {}
    for face in do_elev:
        svg_content = draw_elevation(elements, face, db_path, meta)
        if svg_content:
            elev_svgs[face] = svg_content
            out_path = os.path.join(out_dir, f'{db_stem}_{face}_elevation.svg')
            with open(out_path, 'w') as f:
                f.write(svg_content)
            print(f"  → {out_path}")
        if dxf_writer:
            dxf_writer.write_elevation_dxf(
                db_path, face,
                os.path.join(out_dir, f'{db_stem}_{face}_elevation.dxf'))

    # Paired sheets: front+rear on one sheet, left+right on another
    for face_a, face_b, sheet_name in [
        ('front', 'rear',  'frontrear_elevations'),
        ('left',  'right', 'leftright_elevations'),
    ]:
        if face_a in elev_svgs and face_b in elev_svgs:
            paired = combine_elevation_svgs(
                elev_svgs[face_a], elev_svgs[face_b], face_a, face_b)
            out_path = os.path.join(out_dir, f'{db_stem}_{sheet_name}.svg')
            with open(out_path, 'w') as f:
                f.write(paired)
            print(f"  → {out_path}  [paired sheet]")

    _write_svg_log(out_dir)


if __name__ == '__main__':
    main()
