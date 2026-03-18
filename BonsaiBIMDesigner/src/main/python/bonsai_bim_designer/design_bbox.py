"""
Design Mode BBox Renderer
-------------------------
GPU batch wireframe renderer for uncommitted design-mode bounding boxes.

Follows the same pattern as Federation's bbox_visualization.py:
  load bboxes → group by category → build GPU batches → draw handler

Visual states (BIM_Designer.md §17.2):
  - GREY:  muted context (unfocused existing data)
  - VIVID: category-colored wireframe (active draft section)
  - SOLID: higher alpha + thicker line (just committed)

This module creates NO Blender objects. Everything is GPU overlay only.
"""

from __future__ import annotations

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
        current_hash = scene.get("_design_bboxes", "")
        if current_hash != _last_scene_hash:
            _last_scene_hash = current_hash
            if current_hash:
                bboxes = json.loads(current_hash)
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
