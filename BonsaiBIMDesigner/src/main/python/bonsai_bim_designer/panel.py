"""
BIM Designer Panel
------------------
Blender UI panel registered under BIM_PT_tabs (Federation Suite parent).

Sections:
  A.1  Connection
  A.2  Building Selector
  A.3  Create New
  A.4  Verb Console
"""

from __future__ import annotations

import bpy
from bpy.types import Panel


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

        # ── A.2 Building Selector ───────────────────────────────────────
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

        # ── A.3 Create New ──────────────────────────────────────────────
        box = layout.box()
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

        row = box.row()
        row.operator("bim.designer_create_new", icon='PLAY')
        row.enabled = props.is_connected

        # ── A.4 Verb Console ────────────────────────────────────────────
        box = layout.box()
        box.label(text="A.4  Verb Console", icon='CONSOLE')

        col = box.column(align=True)
        col.prop(props, "verb_line", text="")

        row = box.row()
        row.operator("bim.designer_execute_verb", icon='PLAY')
        row.enabled = props.is_connected

        if props.verb_result:
            box.label(text=props.verb_result, icon='INFO')


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
