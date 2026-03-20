"""
Design Mode BBox Renderer — WF-BB Protocol (§26)
-------------------------------------------------
GPU batch wireframe renderer for design-mode bounding boxes.

Two phases (§26.2):
  Phase 1 — No full geometry: all bboxes drawn as GPU overlay (initial load).
  Phase 2 — Full geometry exists: per-object BOUNDS display, focused item
            gets SOLID + vivid bbox overlay + orientation markers.

Follows Federation's bbox_visualization.py pattern:
  load bboxes → group by category → build GPU batches → draw handler

Visual states (BIM_Designer.md §17.2):
  - GREY:  muted context (unfocused existing data)
  - VIVID: category-colored wireframe (active draft section)
  - SOLID: higher alpha + thicker line (just committed)

This module creates NO Blender objects. Everything is GPU overlay only.
"""

from __future__ import annotations

import hashlib
import json
import time

import bpy
import gpu
from gpu_extras.batch import batch_for_shader
from mathutils import Vector
from typing import Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Category colors — vivid, deliberately different from Federation's
# 8-discipline palette so user always knows draft vs committed.
# ---------------------------------------------------------------------------
DESIGN_COLORS: Dict[str, Tuple[float, float, float, float]] = {
    'BUILDING': (0.5, 0.5, 0.5, 0.3),   # Ghost outline — site envelope
    'FLOOR':    (0.3, 0.5, 0.9, 0.6),   # Blue — storey slab
    'LIVING':   (0.2, 0.8, 0.3, 0.8),   # Green
    'KITCHEN':  (0.9, 0.8, 0.2, 0.8),   # Yellow
    'BEDROOM':  (0.6, 0.3, 0.8, 0.8),   # Purple
    'BATHROOM': (0.2, 0.7, 0.8, 0.8),   # Cyan
    'CORRIDOR': (0.7, 0.7, 0.5, 0.6),   # Khaki
    'BALCONY':  (0.4, 0.9, 0.6, 0.7),   # Mint
    'DEFAULT':  (0.7, 0.7, 0.7, 0.5),   # Fallback grey
}

GREY_OVERRIDE  = (0.4, 0.4, 0.4, 0.2)
SOLID_ALPHA_BOOST = 0.15
SOLID_LINE_WIDTH  = 2.0
DEFAULT_LINE_WIDTH = 1.0

# ---------------------------------------------------------------------------
# Module state
# ---------------------------------------------------------------------------
_category_batches: Dict[str, object] = {}   # category → GPU batch
_bbox_metadata: Dict[str, dict] = {}        # bomId → full bbox dict
_draw_handler = None
_is_enabled = False
_focused_section: Optional[str] = None      # bomId or None (canvas state)
_committed_sections: set = set()            # bomIds that were just committed
_commit_time: float = 0.0                   # monotonic time of last commit (for fade)
_sync_timer_running = False                 # lazy sync timer state
_last_scene_hash: str = ""                  # for dirty detection


# ---------------------------------------------------------------------------
# BBox edge geometry (same algorithm as Federation's bbox_visualization.py)
# ---------------------------------------------------------------------------
def _create_bbox_edges(min_x, min_y, min_z, max_x, max_y, max_z) -> List[Vector]:
    """Create 12 edges of a bbox as 24 vertices (line segments)."""
    # Convert mm to metres for Blender coordinate system
    scale = 0.001
    mn_x, mn_y, mn_z = min_x * scale, min_y * scale, min_z * scale
    mx_x, mx_y, mx_z = max_x * scale, max_y * scale, max_z * scale

    corners = [
        Vector((mn_x, mn_y, mn_z)),  # 0
        Vector((mx_x, mn_y, mn_z)),  # 1
        Vector((mx_x, mx_y, mn_z)),  # 2
        Vector((mn_x, mx_y, mn_z)),  # 3
        Vector((mn_x, mn_y, mx_z)),  # 4
        Vector((mx_x, mn_y, mx_z)),  # 5
        Vector((mx_x, mx_y, mx_z)),  # 6
        Vector((mn_x, mx_y, mx_z)),  # 7
    ]

    edges = [
        (0,1),(1,2),(2,3),(3,0),  # bottom
        (4,5),(5,6),(6,7),(7,4),  # top
        (0,4),(1,5),(2,6),(3,7),  # vertical
    ]

    vertices = []
    for a, b in edges:
        vertices.append(corners[a])
        vertices.append(corners[b])
    return vertices


# ---------------------------------------------------------------------------
# Batch building
# ---------------------------------------------------------------------------
def _build_batches(bboxes: List[dict]) -> None:
    """Group bboxes by category and build one GPU batch per category."""
    global _category_batches, _bbox_metadata

    _category_batches.clear()
    _bbox_metadata.clear()

    # Group by color key (category for ROOMs, bomType for BUILDING/FLOOR)
    groups: Dict[str, List[Vector]] = {}
    for bb in bboxes:
        bom_id = bb.get("bomId", "")
        _bbox_metadata[bom_id] = bb

        color_key = bb.get("category") or bb.get("bomType", "DEFAULT")
        verts = _create_bbox_edges(
            bb["minX"], bb["minY"], bb["minZ"],
            bb["maxX"], bb["maxY"], bb["maxZ"])

        if color_key not in groups:
            groups[color_key] = []
        groups[color_key].extend(verts)

    shader = gpu.shader.from_builtin('UNIFORM_COLOR')
    for key, verts in groups.items():
        coords = [(v.x, v.y, v.z) for v in verts]
        _category_batches[key] = batch_for_shader(shader, 'LINES', {"pos": coords})


# ---------------------------------------------------------------------------
# Draw callback
# ---------------------------------------------------------------------------
def _draw_design_bboxes():
    """Viewport draw handler — called every frame in Design Mode."""
    if not _is_enabled or not _category_batches:
        return

    gpu.state.blend_set('ALPHA')
    shader = gpu.shader.from_builtin('UNIFORM_COLOR')
    shader.bind()

    for key, batch in _category_batches.items():
        # Determine visual state
        if _focused_section:
            # Focus state: only the focused section's category is vivid
            bboxes_in_key = [
                bid for bid, meta in _bbox_metadata.items()
                if (meta.get("category") or meta.get("bomType", "DEFAULT")) == key
            ]
            is_focused = any(bid == _focused_section for bid in bboxes_in_key)
            # Also highlight parent chain
            if not is_focused and _focused_section in _bbox_metadata:
                focused_parent = _bbox_metadata[_focused_section].get("parentBomId")
                is_focused = any(bid == focused_parent for bid in bboxes_in_key)

            if is_focused:
                color = DESIGN_COLORS.get(key, DESIGN_COLORS['DEFAULT'])
            else:
                color = GREY_OVERRIDE
        else:
            # Canvas state: all categories vivid
            color = DESIGN_COLORS.get(key, DESIGN_COLORS['DEFAULT'])

        # Committed boost with animated fade (pulse for 2 seconds after commit)
        is_committed = any(
            bid in _committed_sections
            for bid, meta in _bbox_metadata.items()
            if (meta.get("category") or meta.get("bomType", "DEFAULT")) == key
        )
        if is_committed:
            elapsed = time.monotonic() - _commit_time
            fade = max(0.0, min(1.0, 1.0 - (elapsed / 2.0)))  # 2s fade
            boost = SOLID_ALPHA_BOOST * (0.5 + 0.5 * fade)     # settles at half-boost
            color = (color[0], color[1], color[2],
                     min(1.0, color[3] + boost))
            gpu.state.line_width_set(SOLID_LINE_WIDTH if fade > 0.1 else DEFAULT_LINE_WIDTH)
        else:
            gpu.state.line_width_set(DEFAULT_LINE_WIDTH)

        shader.uniform_float("color", color)
        batch.draw(shader)

    gpu.state.blend_set('NONE')
    gpu.state.line_width_set(1.0)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------
def enable(bboxes: List[dict]) -> bool:
    """Enter Design Mode: build batches and register draw handler.

    Args:
        bboxes: list of bbox dicts from server response (DesignBBox format)

    Returns:
        True if successfully enabled.
    """
    global _draw_handler, _is_enabled, _focused_section, _committed_sections

    disable()  # clean up any previous state

    if not bboxes:
        return False

    _build_batches(bboxes)
    _focused_section = None
    _committed_sections = set()

    _draw_handler = bpy.types.SpaceView3D.draw_handler_add(
        _draw_design_bboxes, (), 'WINDOW', 'POST_VIEW')
    _is_enabled = True

    # Start lazy sync timer for ORDER View ↔ BBox sync + commit animation
    _start_sync_timer()

    _tag_redraw()
    return True


def disable() -> None:
    """Exit Design Mode: remove draw handler, clear batches."""
    global _draw_handler, _is_enabled, _focused_section, _committed_sections
    global _category_batches, _bbox_metadata

    if _draw_handler is not None:
        bpy.types.SpaceView3D.draw_handler_remove(_draw_handler, 'WINDOW')
        _draw_handler = None

    _is_enabled = False
    _focused_section = None
    _committed_sections = set()
    _category_batches.clear()
    _bbox_metadata.clear()

    _stop_sync_timer()
    _tag_redraw()


def focus_section(bom_id: Optional[str]) -> None:
    """Focus a section — vivid color for that bbox, grey for rest.

    Args:
        bom_id: the bomId to focus, or None to return to canvas state.
    """
    global _focused_section
    _focused_section = bom_id

    for area in bpy.context.screen.areas:
        if area.type == 'VIEW_3D':
            area.tag_redraw()


def mark_committed(bom_ids: List[str]) -> None:
    """Mark sections as just-committed — solid/opaque visual treatment with fade."""
    global _committed_sections, _commit_time
    _committed_sections.update(bom_ids)
    _commit_time = time.monotonic()

    for area in bpy.context.screen.areas:
        if area.type == 'VIEW_3D':
            area.tag_redraw()


def is_enabled() -> bool:
    """Whether Design Mode bbox rendering is active."""
    return _is_enabled


def get_metadata() -> Dict[str, dict]:
    """Return the bbox metadata dict (bomId → full bbox data)."""
    return dict(_bbox_metadata)


def get_section_ids() -> List[str]:
    """Return all bomIds in the current layout."""
    return list(_bbox_metadata.keys())


# ---------------------------------------------------------------------------
# Lazy sync timer — detects scene data changes and rebuilds GPU batches.
# Runs at 0.2s intervals via bpy.app.timers. Lightweight: just a hash
# comparison, no redraw unless dirty. Also drives commit fade animation.
# ---------------------------------------------------------------------------
def _sync_timer() -> Optional[float]:
    """Background timer callback. Returns interval or None to stop."""
    global _last_scene_hash, _sync_timer_running

    if not _is_enabled:
        _sync_timer_running = False
        return None  # Stop timer

    # Check if scene bbox data changed (ORDER View or undo could modify it)
    try:
        scene = bpy.context.scene
        if scene is None:
            return 0.2
        raw = scene.get("_design_bboxes", "")
        current_hash = hashlib.md5(raw.encode("utf-8")).hexdigest() if raw else ""
        if current_hash != _last_scene_hash:
            _last_scene_hash = current_hash
            if raw:
                bboxes = json.loads(raw)
                _build_batches(bboxes)
                _tag_redraw()
    except Exception:
        pass  # Scene not available (e.g., Blender shutting down)

    # Drive commit fade animation — keep redrawing for 2s after commit
    if _committed_sections and (time.monotonic() - _commit_time) < 2.5:
        _tag_redraw()

    return 0.2  # Continue at 200ms intervals


def _start_sync_timer() -> None:
    """Start the lazy sync timer if not already running."""
    global _sync_timer_running, _last_scene_hash
    if not _sync_timer_running:
        _sync_timer_running = True
        _last_scene_hash = ""
        bpy.app.timers.register(_sync_timer, first_interval=0.2)


def _stop_sync_timer() -> None:
    """Stop the sync timer."""
    global _sync_timer_running
    _sync_timer_running = False
    # Timer stops itself when _is_enabled is False


def _tag_redraw() -> None:
    """Force all 3D viewports to redraw."""
    try:
        for area in bpy.context.screen.areas:
            if area.type == 'VIEW_3D':
                area.tag_redraw()
    except Exception:
        pass


# ===========================================================================
# Phase 2 — Per-Object BOUNDS Mode (§26.4)
# ===========================================================================
# When full geometry exists in the scene, Design Mode uses Blender's native
# per-object display_type='BOUNDS' for unselected items and SOLID + vivid
# bbox overlay for the focused item.

_LOADED_TAG = "bim_designer_loaded"  # matches db_loader.py tag
_phase2_active = False
_phase2_draw_handler = None
_phase2_focused_batch = None        # GPU batch for focused item's bbox
_phase2_focused_color = (0.2, 0.8, 0.3, 0.9)  # vivid green default
_phase2_orientation_batch = None    # GPU batch for orientation arrows
_phase2_metadata: Optional[dict] = None  # cached metadata for peek popup
_phase2_peek_handler = None         # 2D overlay for metadata popup
_phase2_peek_active = False


def has_full_geometry() -> bool:
    """Detect Phase 2: are there mesh objects from db_loader in the scene?"""
    try:
        return any(
            obj.type == 'MESH' and obj.get(_LOADED_TAG)
            for obj in bpy.data.objects
        )
    except Exception:
        return False


def _get_designer_objects() -> list:
    """Get all objects loaded by db_loader."""
    return [obj for obj in bpy.data.objects
            if obj.type == 'MESH' and obj.get(_LOADED_TAG)]


# -- Phase 2: Enter/Exit ---------------------------------------------------

def enter_phase2() -> bool:
    """Enter Phase 2 Design Mode — set all loaded objects to BOUNDS.

    Returns True if Phase 2 was activated.
    """
    global _phase2_active, _phase2_draw_handler

    objects = _get_designer_objects()
    if not objects:
        return False

    # Store original display_type and switch to BOUNDS
    for obj in objects:
        obj["_wf_original_display"] = obj.display_type
        obj.display_type = 'BOUNDS'
        obj.display_bounds_type = 'BOX'
        obj.color = (0.4, 0.4, 0.4, 0.3)

    # Set viewport shading to use object color so BOUNDS grey is visible
    for area in bpy.context.screen.areas:
        if area.type == 'VIEW_3D':
            space = area.spaces[0]
            if hasattr(space.shading, 'color_type'):
                space.shading.color_type = 'OBJECT'

    # Register Phase 2 draw handler (for focused item bbox + orientation)
    if _phase2_draw_handler is None:
        _phase2_draw_handler = bpy.types.SpaceView3D.draw_handler_add(
            _draw_phase2_overlay, (), 'WINDOW', 'POST_VIEW')

    _phase2_active = True
    _tag_redraw()
    return True


def exit_phase2() -> None:
    """Exit Phase 2 Design Mode — restore all objects to original display."""
    global _phase2_active, _phase2_draw_handler, _phase2_focused_batch
    global _phase2_orientation_batch, _phase2_metadata

    objects = _get_designer_objects()
    for obj in objects:
        original = obj.get("_wf_original_display", 'SOLID')
        obj.display_type = original
        obj.show_in_front = False
        if "_wf_original_display" in obj:
            del obj["_wf_original_display"]

    # Remove Phase 2 draw handler
    if _phase2_draw_handler is not None:
        bpy.types.SpaceView3D.draw_handler_remove(_phase2_draw_handler, 'WINDOW')
        _phase2_draw_handler = None

    # Remove peek popup handler
    exit_peek()

    _phase2_focused_batch = None
    _phase2_orientation_batch = None
    _phase2_metadata = None
    _phase2_active = False
    _tag_redraw()


def is_phase2() -> bool:
    """Whether Phase 2 (per-object BOUNDS) mode is active."""
    return _phase2_active


# -- Phase 2: Focus --------------------------------------------------------

def focus_phase2(obj: "bpy.types.Object", bom_id: Optional[str] = None) -> None:
    """Focus an object in Phase 2 — SOLID + show_in_front + bbox overlay.

    Args:
        obj: The Blender object to focus.
        bom_id: Optional bomId for metadata lookup.
    """
    global _phase2_focused_batch, _phase2_orientation_batch, _phase2_focused_color

    # Unfocus previous — set all back to BOUNDS
    for o in _get_designer_objects():
        if o.display_type != 'BOUNDS':
            o.display_type = 'BOUNDS'
            o.show_in_front = False
            o.color = (0.4, 0.4, 0.4, 0.3)

    # Promote focused object to SOLID
    obj.display_type = 'SOLID'
    obj.show_in_front = True

    # Build bbox overlay for the focused object from its bounding box
    bb = obj.bound_box  # 8 corners in local space
    if bb:
        # Get world-space min/max from bound_box
        world_corners = [obj.matrix_world @ Vector(corner) for corner in bb]
        min_x = min(c.x for c in world_corners)
        max_x = max(c.x for c in world_corners)
        min_y = min(c.y for c in world_corners)
        max_y = max(c.y for c in world_corners)
        min_z = min(c.z for c in world_corners)
        max_z = max(c.z for c in world_corners)

        # Build bbox edges (already in metres, no scale needed)
        corners = [
            Vector((min_x, min_y, min_z)),
            Vector((max_x, min_y, min_z)),
            Vector((max_x, max_y, min_z)),
            Vector((min_x, max_y, min_z)),
            Vector((min_x, min_y, max_z)),
            Vector((max_x, min_y, max_z)),
            Vector((max_x, max_y, max_z)),
            Vector((min_x, max_y, max_z)),
        ]
        edges = [
            (0,1),(1,2),(2,3),(3,0),
            (4,5),(5,6),(6,7),(7,4),
            (0,4),(1,5),(2,6),(3,7),
        ]
        verts = []
        for a, b in edges:
            verts.append((corners[a].x, corners[a].y, corners[a].z))
            verts.append((corners[b].x, corners[b].y, corners[b].z))

        shader = gpu.shader.from_builtin('UNIFORM_COLOR')
        _phase2_focused_batch = batch_for_shader(shader, 'LINES', {"pos": verts})

        # Determine color from object name or bomId
        color_key = 'DEFAULT'
        if bom_id and bom_id in _bbox_metadata:
            color_key = (_bbox_metadata[bom_id].get("category")
                         or _bbox_metadata[bom_id].get("bomType", "DEFAULT"))
        _phase2_focused_color = DESIGN_COLORS.get(color_key, DESIGN_COLORS['DEFAULT'])
        # Boost alpha for Phase 2 (needs to stand out against BOUNDS objects)
        _phase2_focused_color = (
            _phase2_focused_color[0], _phase2_focused_color[1],
            _phase2_focused_color[2], min(1.0, _phase2_focused_color[3] + 0.2))

        # Build orientation markers — RGB axes at bbox centre
        cx = (min_x + max_x) / 2
        cy = (min_y + max_y) / 2
        cz = (min_z + max_z) / 2
        ax = (max_x - min_x) * 0.3  # 30% of extent
        ay = (max_y - min_y) * 0.3
        az = (max_z - min_z) * 0.3

        # 3 axis arrows: Red=+X, Green=+Y, Blue=+Z
        # Pre-build GPU batches so the draw callback doesn't allocate per frame.
        axis_shader = gpu.shader.from_builtin('UNIFORM_COLOR')
        axis_pairs = [
            ([(cx, cy, cz), (cx + ax, cy, cz)], (1.0, 0.2, 0.2, 1.0)),  # Red +X
            ([(cx, cy, cz), (cx, cy + ay, cz)], (0.2, 1.0, 0.2, 1.0)),  # Green +Y
            ([(cx, cy, cz), (cx, cy, cz + az)], (0.2, 0.2, 1.0, 1.0)),  # Blue +Z
        ]
        _phase2_orientation_batch = [
            (batch_for_shader(axis_shader, 'LINES', {"pos": verts}), color)
            for verts, color in axis_pairs
        ]

    _tag_redraw()


def unfocus_phase2() -> None:
    """Unfocus all objects in Phase 2 — all back to BOUNDS."""
    global _phase2_focused_batch, _phase2_orientation_batch, _phase2_metadata

    for o in _get_designer_objects():
        o.display_type = 'BOUNDS'
        o.show_in_front = False
        o.color = (0.4, 0.4, 0.4, 0.3)

    _phase2_focused_batch = None
    _phase2_orientation_batch = None
    _phase2_metadata = None
    exit_peek()
    _tag_redraw()


# -- Phase 2: Draw callback ------------------------------------------------

def _draw_phase2_overlay():
    """Viewport draw handler for Phase 2 — focused item bbox + orientation."""
    if not _phase2_active:
        return

    shader = gpu.shader.from_builtin('UNIFORM_COLOR')

    # Draw focused item bbox
    if _phase2_focused_batch is not None:
        gpu.state.blend_set('ALPHA')
        gpu.state.line_width_set(2.5)  # thick for visibility
        shader.bind()
        shader.uniform_float("color", _phase2_focused_color)
        _phase2_focused_batch.draw(shader)

        # Draw orientation axes (pre-built batches — no per-frame allocation)
        if _phase2_orientation_batch:
            gpu.state.line_width_set(3.0)
            for axis_batch, axis_color in _phase2_orientation_batch:
                shader.uniform_float("color", axis_color)
                axis_batch.draw(shader)

        gpu.state.blend_set('NONE')
        gpu.state.line_width_set(1.0)


# -- Phase 2: Metadata Peek Popup (§26.6) ----------------------------------

def is_peek_active() -> bool:
    """Whether the metadata peek popup is currently showing."""
    return _phase2_peek_active


def enter_peek(metadata: dict) -> None:
    """Show metadata popup overlay for the focused item.

    Args:
        metadata: dict from getElementMetadata verb response.
    """
    global _phase2_metadata, _phase2_peek_handler, _phase2_peek_active

    _phase2_metadata = metadata
    _phase2_peek_active = True

    if _phase2_peek_handler is None:
        _phase2_peek_handler = bpy.types.SpaceView3D.draw_handler_add(
            _draw_peek_popup, (), 'WINDOW', 'POST_PIXEL')

    _tag_redraw()


def exit_peek() -> None:
    """Dismiss the metadata popup."""
    global _phase2_peek_handler, _phase2_peek_active, _phase2_metadata

    if _phase2_peek_handler is not None:
        bpy.types.SpaceView3D.draw_handler_remove(_phase2_peek_handler, 'WINDOW')
        _phase2_peek_handler = None

    _phase2_peek_active = False
    _phase2_metadata = None
    _tag_redraw()


def _draw_peek_popup():
    """2D screen-space overlay for metadata properties popup."""
    if not _phase2_peek_active or not _phase2_metadata:
        return

    import blf

    meta = _phase2_metadata
    font_id = 0
    line_height = 20

    # Build lines to display
    lines = []
    lines.append(f"  {meta.get('name', meta.get('bomId', '?'))}")
    lines.append(f"  Category: {meta.get('category', '—')}")

    dims = meta.get('dimensions', {})
    if dims:
        lines.append(f"  Size: {dims.get('w', 0):.0f} x {dims.get('d', 0):.0f} x {dims.get('h', 0):.0f} mm")

    if meta.get('material'):
        lines.append(f"  Material: {meta['material']}")
    if meta.get('productId'):
        lines.append(f"  Product: {meta['productId']}")
    if meta.get('constructionSystem'):
        lines.append(f"  System: {meta['constructionSystem']}")

    # Cost
    cost = meta.get('costUnit')
    currency = meta.get('currency', '')
    if cost:
        lines.append(f"  Cost: {currency} {cost:.2f}")

    # Compliance status
    compliance = meta.get('compliance', [])
    for c in compliance:
        status_icon = "OK" if c.get('status') == 'PASS' else "FAIL"
        lines.append(f"  [{status_icon}] {c.get('rule', '?')}")

    # Position — top-left of viewport, offset from edge
    region = bpy.context.region
    x_start = 20
    y_start = region.height - 60

    # Background panel
    panel_width = 280
    panel_height = len(lines) * line_height + 30
    shader = gpu.shader.from_builtin('UNIFORM_COLOR')
    panel_verts = [
        (x_start - 5, y_start - panel_height),
        (x_start + panel_width, y_start - panel_height),
        (x_start + panel_width, y_start + 10),
        (x_start - 5, y_start + 10),
    ]
    panel_batch = batch_for_shader(shader, 'TRI_FAN', {"pos": panel_verts})
    gpu.state.blend_set('ALPHA')
    shader.bind()
    shader.uniform_float("color", (0.15, 0.15, 0.15, 0.9))
    panel_batch.draw(shader)

    # Title bar
    title_verts = [
        (x_start - 5, y_start - 5),
        (x_start + panel_width, y_start - 5),
        (x_start + panel_width, y_start + 10),
        (x_start - 5, y_start + 10),
    ]
    title_batch = batch_for_shader(shader, 'TRI_FAN', {"pos": title_verts})
    shader.uniform_float("color", (0.3, 0.5, 0.9, 0.9))
    title_batch.draw(shader)

    # Text
    blf.size(font_id, 13)
    blf.color(font_id, 1.0, 1.0, 1.0, 1.0)
    blf.position(font_id, x_start + 5, y_start, 0)
    blf.draw(font_id, "Element Properties")

    y_pos = y_start - 25
    blf.size(font_id, 12)
    for line_text in lines:
        blf.position(font_id, x_start, y_pos, 0)
        # Color compliance lines
        if "[FAIL]" in line_text:
            blf.color(font_id, 1.0, 0.3, 0.3, 1.0)
        elif "[OK]" in line_text:
            blf.color(font_id, 0.3, 1.0, 0.3, 1.0)
        else:
            blf.color(font_id, 0.9, 0.9, 0.9, 1.0)
        blf.draw(font_id, line_text)
        y_pos -= line_height

    gpu.state.blend_set('NONE')
