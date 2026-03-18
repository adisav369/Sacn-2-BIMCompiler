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
        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            box.label(text="No design data — generate a building first", icon='ERROR')
            return

        bboxes = json.loads(bbox_json)

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

        # Element count
        if props.last_element_count > 0:
            box.label(text=f"~{props.last_element_count} elements", icon='MESH_DATA')

        # ── Action buttons ─────────────────────────────────────────────
        box.separator()
        row = box.row(align=True)
        row.operator("bim.designer_snap", icon='SNAP_ON')
        row.operator("bim.designer_save", icon='FILE_TICK')
        row.enabled = props.is_connected

        row = box.row()
        row.operator("bim.designer_promote", icon='EXPORT')
        row.enabled = props.is_connected


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
