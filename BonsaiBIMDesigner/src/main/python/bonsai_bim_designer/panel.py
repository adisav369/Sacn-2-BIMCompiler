"""
BIM Designer Panel
------------------
Blender UI panel registered under BIM_PT_tabs (Federation Suite parent).

Sections:
  A.1  Connection
  A.2  Building Selector  (Real Mode only)
  A.3  Create New + Design Mode toggle + section chooser
  A.4  Verb Console       (Real Mode only)
"""

from __future__ import annotations

import json

import bpy
from bpy.types import Panel

from . import design_bbox

# Panel-level bbox cache — avoids json.loads() on every panel redraw.
_bbox_cache_raw: str = ""
_bbox_cache_parsed: list = []


def _get_cached_bboxes(context) -> list:
    """Return parsed bboxes, re-parsing only when the scene property changes."""
    global _bbox_cache_raw, _bbox_cache_parsed
    raw = context.scene.get("_design_bboxes", "")
    if raw != _bbox_cache_raw:
        _bbox_cache_raw = raw
        _bbox_cache_parsed = json.loads(raw) if raw else []
    return _bbox_cache_parsed


class BIM_PT_bim_designer(Panel):
    """A. BIM Designer — compiler-driven building design"""
    bl_label = "A. BIM Designer"
    bl_idname = "BIM_PT_bim_designer"
    bl_space_type = "PROPERTIES"
    bl_region_type = "WINDOW"
    bl_context = "scene"
    bl_parent_id = "BIM_PT_tabs"
    bl_order = 15

    def draw(self, context):
        layout = self.layout
        props = context.scene.BIMDesignerProperties
        in_design = props.design_mode

        # ── A.1 Connection ──────────────────────────────────────────────
        box = layout.box()
        box.label(text="A.1  Connection", icon='URL')

        row = box.row(align=True)
        row.prop(props, "server_host", text="Host")
        row.prop(props, "server_port", text="Port")

        row = box.row(align=True)
        if props.is_connected:
            row.operator("bim.designer_disconnect", icon='CANCEL')
            row.label(text="Connected", icon='CHECKMARK')
        else:
            row.operator("bim.designer_connect", icon='PLAY')
            row.label(text="Disconnected", icon='ERROR')

        if props.compile_status:
            box.label(text=props.compile_status, icon='INFO')

        # ── A.2 Building Selector (Real Mode only) ─────────────────────
        if not in_design:
            box = layout.box()
            box.label(text="A.2  Building Selector", icon='HOME')

            col = box.column(align=True)
            col.prop(props, "building_id")
            col.prop(props, "bom_db_path")
            col.prop(props, "output_db_path")

            row = box.row(align=True)
            row.operator("bim.designer_list_buildings", icon='LINENUMBERS_ON')
            row.operator("bim.designer_compile", icon='FILE_REFRESH')
            row.enabled = props.is_connected

            if props.last_element_count > 0:
                box.label(text=f"Elements: {props.last_element_count}", icon='MESH_DATA')

        # ── A.3 Create New + Design Mode ──────────────────────────────
        box = layout.box()

        # Mode toggle — always visible
        row = box.row(align=True)
        if in_design:
            row.operator("bim.designer_toggle_mode", text="REAL",
                         icon='SHADING_SOLID', depress=False)
            row.label(text="DESIGN MODE", icon='OUTLINER_OB_LIGHT')
        else:
            row.label(text="REAL MODE", icon='SHADING_RENDERED')
            row.operator("bim.designer_toggle_mode", text="DESIGN",
                         icon='OUTLINER_OB_LIGHT', depress=False)
            row.enabled = props.is_connected or "_design_bboxes" in context.scene

        if in_design:
            # Phase indicator (§26.8)
            if design_bbox.is_phase2():
                box.label(text="Phase 2 — BOUNDS + Focus", icon='MESH_CUBE')
            else:
                box.label(text="Phase 1 — Wireframe BBox", icon='MESH_GRID')

            # ── Section Chooser (Design Mode) ──────────────────────────
            self._draw_section_chooser(context, box, props)
        else:
            # ── Create New form (Real Mode) ────────────────────────────
            box.label(text="A.3  Create New", icon='ADD')

            col = box.column(align=True)
            col.prop(props, "building_name")
            col.prop(props, "building_type")
            col.prop(props, "jurisdiction")

            row = box.row(align=True)
            row.prop(props, "site_width_mm")
            row.prop(props, "site_depth_mm")

            row = box.row(align=True)
            row.prop(props, "num_bedrooms")
            row.prop(props, "num_bathrooms")
            row.prop(props, "storeys")

            row = box.row()
            row.operator("bim.designer_create_new", icon='PLAY')
            row.enabled = props.is_connected

        # ── A.4 Verb Console (Real Mode only) ──────────────────────────
        if not in_design:
            box = layout.box()
            box.label(text="A.4  Verb Console", icon='CONSOLE')

            col = box.column(align=True)
            col.prop(props, "verb_line", text="")

            row = box.row()
            row.operator("bim.designer_execute_verb", icon='PLAY')
            row.enabled = props.is_connected

            if props.verb_result:
                box.label(text=props.verb_result, icon='INFO')

    def _draw_section_chooser(self, context, box, props):
        """Draw the clickable BOM tree section chooser in Design Mode."""
        bboxes = _get_cached_bboxes(context)
        if not bboxes:
            box.label(text="No design data — generate a building first", icon='ERROR')
            return

        # Group by storey
        building = None
        floors = {}  # storey → floor bbox
        rooms = {}   # storey → [room bboxes]

        for bb in bboxes:
            bt = bb.get("bomType", "")
            if bt == "BUILDING":
                building = bb
            elif bt == "FLOOR":
                storey = bb.get("storey", "?")
                floors[storey] = bb
            elif bt == "ROOM":
                storey = bb.get("storey", "?")
                if storey not in rooms:
                    rooms[storey] = []
                rooms[storey].append(bb)

        # Building header
        if building:
            row = box.row()
            w_m = building["maxX"] / 1000
            d_m = building["maxY"] / 1000
            row.label(text=f"{building['name']} ({w_m:.0f} x {d_m:.0f} m)",
                      icon='HOME')

        # Per-storey sections
        for storey in sorted(floors.keys()):
            floor_bb = floors[storey]
            sub = box.box()

            # Floor header — clickable
            op = sub.operator("bim.designer_focus_section",
                              text=f"{floor_bb['name']}",
                              icon='TRIA_DOWN' if props.active_section == floor_bb['bomId'] else 'TRIA_RIGHT')
            op.section_id = floor_bb["bomId"]

            # Room cards
            if storey in rooms:
                grid = sub.grid_flow(columns=3, even_columns=True, align=True)
                for room in rooms[storey]:
                    cat = room.get("category", "DEFAULT")
                    bom_id = room["bomId"]
                    is_active = props.active_section == bom_id

                    col = grid.column(align=True)
                    op = col.operator(
                        "bim.designer_focus_section",
                        text=f"{cat}",
                        icon='RADIOBUT_ON' if is_active else 'RADIOBUT_OFF',
                        depress=is_active)
                    op.section_id = bom_id

                    w_m = (room["maxX"] - room["minX"]) / 1000
                    d_m = (room["maxY"] - room["minY"]) / 1000
                    col.label(text=f"{w_m:.1f} x {d_m:.1f} m")

                    # Remove button (small X)
                    rm_op = col.operator("bim.designer_remove_room",
                                         text="", icon='X')
                    rm_op.room_bom_id = bom_id

        # ── Dimension Sliders (§17) — when a ROOM is focused ──────────
        if props.active_section:
            for bb in bboxes:
                if (bb.get("bomId") == props.active_section
                        and bb.get("bomType") == "ROOM"):
                    # Sync slider values from current bbox dims
                    cur_w = bb["maxX"] - bb["minX"]
                    cur_d = bb["maxY"] - bb["minY"]
                    # Only sync if slider hasn't been touched yet
                    # (avoid overwriting user edits mid-drag)
                    slider_box = box.box()
                    slider_box.label(
                        text=f"Resize: {bb.get('category', '')}",
                        icon='FULLSCREEN_ENTER')
                    col = slider_box.column(align=True)
                    col.prop(props, "room_width_mm", slider=True)
                    col.prop(props, "room_depth_mm", slider=True)
                    col.operator("bim.designer_update_room_dims",
                                 icon='CHECKMARK', text="Apply")
                    col.enabled = True
                    break

            # ── Peek Metadata button (§26.6) ────────────────────────
            peek_row = box.row(align=True)
            peek_op = peek_row.operator("bim.designer_peek_metadata",
                                         text="Peek Properties",
                                         icon='INFO')
            peek_op.bom_id = props.active_section

        # ── Layout editing — Add Room / Add Storey (§16) ──────────────
        edit_row = box.row(align=True)
        edit_row.label(text="Layout:", icon='MESH_GRID')
        for cat_label, cat_val in [("Bed", "BEDROOM"), ("Bath", "BATHROOM"),
                                   ("Living", "LIVING"), ("Kitchen", "KITCHEN")]:
            op = edit_row.operator("bim.designer_add_room",
                                   text=f"+{cat_label}")
            op.category = cat_val
        edit_row.enabled = props.is_connected

        storey_row = box.row()
        storey_row.operator("bim.designer_add_storey", icon='SORT_ASC')
        storey_row.enabled = props.is_connected

        # Element count
        if props.last_element_count > 0:
            box.label(text=f"~{props.last_element_count} elements", icon='MESH_DATA')

        # ── Status Strip — per-rule compliance (UX-F-18/19) ────────────
        self._draw_status_strip(context, box, props)

        # ── BOM Chooser (§17.18) ─────────────────────────────────────────
        self._draw_bom_chooser(context, box, props)

        # ── Action buttons ─────────────────────────────────────────────
        box.separator()
        row = box.row(align=True)
        row.operator("bim.designer_snap", icon='SNAP_ON')
        row.operator("bim.designer_save", icon='FILE_TICK')
        row.enabled = props.is_connected

        # ── Compile Bridge (§22) — turns wireframe bboxes into 3D ────
        compile_row = box.row(align=True)
        compile_row.operator("bim.designer_compile", text="Compile to 3D",
                             icon='MESH_CUBE')
        compile_row.enabled = props.is_connected
        if props.last_element_count > 0 and props.compile_status:
            status = props.compile_status
            if "elements" in status.lower():
                box.label(text=status, icon='CHECKMARK')

        row = box.row()
        row.operator("bim.designer_promote", icon='EXPORT')
        row.enabled = props.is_connected


    def _draw_bom_chooser(self, context, box, props):
        """Draw the BOM Chooser — search-first product browser (§17.18).

        Shows search bar, category sidebar, results with fit status,
        and pagination controls.
        """
        chooser = box.box()
        chooser.label(text="BOM Chooser", icon='ASSET_MANAGER')

        # Search bar + browse button
        row = chooser.row(align=True)
        row.prop(props, "browse_search", text="", icon='VIEWZOOM')
        op = row.operator("bim.designer_browse_items", text="", icon='PLAY')
        op.search = ""
        op.category = ""
        row.enabled = props.is_connected

        # Category tabs from last browse result
        cat_json = context.scene.get("_browse_categories", "")
        if cat_json:
            categories = json.loads(cat_json)
            if categories:
                row = chooser.row(align=True)
                for cat in categories:
                    cat_name = cat.get("name", "?")
                    cnt = cat.get("count", 0)
                    fits = cat.get("fitsCount", 0)
                    op = row.operator("bim.designer_browse_items",
                                      text=f"{cat_name} ({fits}/{cnt})")
                    op.search = ""
                    op.category = cat_name

        # Results
        items_json = context.scene.get("_browse_items", "")
        if items_json:
            items = json.loads(items_json)
            if items:
                for item in items:
                    row = chooser.row(align=True)

                    # Fit status icon
                    fit = item.get("fitStatus", "FITS")
                    if fit == "FITS":
                        icon = 'CHECKMARK'
                    elif fit == "TIGHT":
                        icon = 'ERROR'
                    else:
                        icon = 'CANCEL'

                    # Product name + dimensions
                    w = item.get("widthMm", 0)
                    d = item.get("depthMm", 0)
                    h = item.get("heightMm", 0)
                    name = item.get("productId", "?")
                    row.label(text=f"{name}", icon=icon)
                    row.label(text=f"{w:.0f}x{d:.0f}x{h:.0f}")
                    # Show similarity score if present (from findSimilar)
                    sim = item.get("similarity", 0)
                    if sim > 0:
                        row.label(text=f"{sim:.0%}")
                    row.label(text=fit)

                    # Place button — places item into focused room
                    place_op = row.operator("bim.designer_place_item",
                                            text="", icon='IMPORT')
                    place_op.product_id = name

                    # Find Similar button (§25.3)
                    sim_op = row.operator("bim.designer_find_similar",
                                          text="", icon='LINKED')
                    sim_op.product_id = name

                # Pagination
                if props.browse_total > 20:
                    row = chooser.row(align=True)
                    row.operator("bim.designer_browse_prev", icon='TRIA_LEFT')
                    page = (props.browse_offset // 20) + 1
                    total_pages = (props.browse_total + 19) // 20
                    row.label(text=f"Page {page}/{total_pages}")
                    row.operator("bim.designer_browse_next", icon='TRIA_RIGHT')
            else:
                chooser.label(text="No results", icon='INFO')

    def _draw_status_strip(self, context, box, props):
        """Draw the compliance status strip — UX-F-18/19 (ambient compliance).

        Shows per-rule validation verdicts from the last snap() call.
        Red/yellow/green indicators with delta display on failure.
        """
        adj_json = context.scene.get("_snap_adjustments", "")
        if not adj_json:
            return

        adjustments = json.loads(adj_json)
        if not adjustments:
            # All clear — show green summary
            strip = box.box()
            row = strip.row()
            row.label(text=f"{props.jurisdiction} — All checks passed",
                      icon='CHECKMARK')
            return

        strip = box.box()
        row = strip.row()
        row.label(text=f"{props.jurisdiction} Compliance", icon='ERROR')

        # Jurisdiction switch buttons
        jur_row = strip.row(align=True)
        jur_row.label(text="Switch:", icon='WORLD')
        for jur_code in ("MY", "US", "UK", "AU", "SG"):
            op = jur_row.operator("bim.designer_set_jurisdiction",
                                   text=jur_code,
                                   depress=(props.jurisdiction == jur_code))
            op.jurisdiction = jur_code
        jur_row.enabled = props.is_connected

        # Group adjustments by bomId for the focused section
        focused = props.active_section
        for adj in adjustments:
            bom_id = adj.get("bomId", "")
            rule = adj.get("rule", "")
            field = adj.get("field", "")
            actual = adj.get("from", 0)
            required = adj.get("to", 0)

            # Show all if no focus, or filter to focused section
            if focused and bom_id != focused:
                continue

            # Delta display (UX-F-19)
            if rule == "GRID_SNAP":
                icon = 'PREFERENCES'
                text = f"{bom_id} {field}: {actual:.0f} -> {required:.0f}mm (grid)"
            else:
                # Validation failure — red indicator
                delta = required - actual
                if actual < required:
                    icon = 'CANCEL'
                    text = f"{bom_id}: {actual:.0f}mm < {required:.0f}mm (need +{delta:.0f}mm)"
                else:
                    icon = 'CHECKMARK'
                    text = f"{bom_id}: {actual:.0f}mm >= {required:.0f}mm"

            row = strip.row(align=True)
            row.label(text=text, icon=icon)

            # Click-to-fix button for BLOCK violations (not GRID_SNAP)
            if rule != "GRID_SNAP" and actual < required:
                fix_op = row.operator("bim.designer_auto_fix",
                                       text="Fix", icon='TOOL_SETTINGS')
                fix_op.fix_rule = rule
                fix_op.fix_bom_id = bom_id


# =============================================================================
# Registration
# =============================================================================

_classes = (
    BIM_PT_bim_designer,
)


def register():
    for cls in _classes:
        bpy.utils.register_class(cls)


def unregister():
    for cls in reversed(_classes):
        bpy.utils.unregister_class(cls)
