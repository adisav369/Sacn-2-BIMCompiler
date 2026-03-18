"""
BIM Designer Operators
----------------------
Thin Blender operators that delegate all logic to the Java DesignerServer
via the TCP client.
"""

from __future__ import annotations

import bpy
from bpy.types import Operator

import json

from .client import DesignerClient
from . import design_bbox

# Module-level client instance, shared across operators.
_client: DesignerClient | None = None


def _get_props(context) -> "bpy.types.PropertyGroup":
    return context.scene.BIMDesignerProperties


# =============================================================================
# Connection
# =============================================================================

class BIM_OT_designer_connect(Operator):
    """Connect to the Java DesignerServer"""
    bl_idname = "bim.designer_connect"
    bl_label = "Connect"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is not None:
            try:
                _client.disconnect()
            except Exception:
                pass

        _client = DesignerClient(host=props.server_host, port=props.server_port)
        try:
            _client.connect()
            props.is_connected = True
            props.compile_status = "Connected"
            self.report({'INFO'}, f"Connected to {props.server_host}:{props.server_port}")
        except Exception as e:
            props.is_connected = False
            props.compile_status = f"Connection failed: {e}"
            self.report({'ERROR'}, str(e))
            _client = None
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_disconnect(Operator):
    """Disconnect from the DesignerServer"""
    bl_idname = "bim.designer_disconnect"
    bl_label = "Disconnect"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is not None:
            try:
                _client.disconnect()
            except Exception:
                pass
            _client = None

        props.is_connected = False
        props.compile_status = "Disconnected"
        self.report({'INFO'}, "Disconnected")
        return {'FINISHED'}


# =============================================================================
# Building operations
# =============================================================================

class BIM_OT_designer_list_buildings(Operator):
    """List available building types from the server"""
    bl_idname = "bim.designer_list_buildings"
    bl_label = "List Buildings"
    bl_options = {'REGISTER'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        try:
            result = _client.list_buildings()
            buildings = result.get("buildings", [])
            props.compile_status = f"Buildings: {', '.join(buildings)}"
            self.report({'INFO'}, props.compile_status)
        except Exception as e:
            props.compile_status = f"Error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_compile(Operator):
    """Compile the active building via the DesignerServer"""
    bl_idname = "bim.designer_compile"
    bl_label = "Compile"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.building_id:
            self.report({'ERROR'}, "No building ID set")
            return {'CANCELLED'}

        if not props.bom_db_path:
            self.report({'ERROR'}, "No BOM DB path set")
            return {'CANCELLED'}

        try:
            result = _client.compile(
                building_id=props.building_id,
                bom_db_path=props.bom_db_path,
            )
            status = result.get("status", "unknown")
            count = result.get("elementCount", 0)
            props.last_element_count = count
            props.compile_status = f"Compile {status} — {count} elements"
            self.report({'INFO'}, props.compile_status)
        except Exception as e:
            props.compile_status = f"Compile error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_create_new(Operator):
    """Generate a new building layout — returns design-mode bboxes"""
    bl_idname = "bim.designer_create_new"
    bl_label = "Generate Building"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.building_name:
            self.report({'ERROR'}, "Building name is required")
            return {'CANCELLED'}

        try:
            result = _client.create_new(
                building_name=props.building_name,
                building_type=props.building_type,
                jurisdiction=props.jurisdiction,
                site_width_mm=props.site_width_mm,
                site_depth_mm=props.site_depth_mm,
                num_bedrooms=props.num_bedrooms,
                num_bathrooms=props.num_bathrooms,
                storeys=props.storeys,
            )

            if not result.get("success", False):
                error = result.get("error", "Unknown error")
                props.compile_status = f"Create failed: {error}"
                self.report({'ERROR'}, error)
                return {'CANCELLED'}

            bboxes = result.get("bboxes", [])
            count = result.get("elementCount", 0)
            props.last_element_count = count

            # Store bbox data in scene for undo tracking
            context.scene["_design_bboxes"] = json.dumps(bboxes)

            # Enable design mode with bboxes
            if design_bbox.enable(bboxes):
                props.design_mode = True
                props.active_section = ""
                props.compile_status = f"Design: {len(bboxes)} bboxes, ~{count} elements"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = "Create: no bboxes returned"
                self.report({'WARNING'}, props.compile_status)

        except Exception as e:
            props.compile_status = f"Create error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


# =============================================================================
# Design Mode operators
# =============================================================================

class BIM_OT_designer_toggle_mode(Operator):
    """Toggle between Design Mode (draft bboxes) and Real Mode (Federation view)"""
    bl_idname = "bim.designer_toggle_mode"
    bl_label = "Toggle Design/Real"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        props = _get_props(context)

        if props.design_mode:
            # Switch to Real Mode
            design_bbox.disable()
            props.design_mode = False
            props.active_section = ""
            props.compile_status = "Real Mode"
            self.report({'INFO'}, "Real Mode — Federation view restored")
        else:
            # Switch to Design Mode — restore bboxes from scene data
            bbox_json = context.scene.get("_design_bboxes", "")
            if bbox_json:
                bboxes = json.loads(bbox_json)
                if design_bbox.enable(bboxes):
                    props.design_mode = True
                    props.compile_status = "Design Mode"
                    self.report({'INFO'}, "Design Mode — draft bboxes active")
                    return {'FINISHED'}

            props.compile_status = "No design data — generate a building first"
            self.report({'WARNING'}, props.compile_status)
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_focus_section(Operator):
    """Focus a section in Design Mode — vivid color for that bbox"""
    bl_idname = "bim.designer_focus_section"
    bl_label = "Focus Section"
    bl_options = {'REGISTER', 'UNDO'}

    section_id: bpy.props.StringProperty(name="Section ID")

    def execute(self, context):
        props = _get_props(context)

        if not props.design_mode:
            self.report({'WARNING'}, "Not in Design Mode")
            return {'CANCELLED'}

        # Toggle: clicking the already-focused section unfocuses it
        if props.active_section == self.section_id:
            design_bbox.focus_section(None)
            props.active_section = ""
        else:
            design_bbox.focus_section(self.section_id)
            props.active_section = self.section_id

        return {'FINISHED'}


class BIM_OT_designer_snap(Operator):
    """Snap bboxes to grid + validate against jurisdiction rules"""
    bl_idname = "bim.designer_snap"
    bl_label = "Snap"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.design_mode:
            self.report({'WARNING'}, "Not in Design Mode")
            return {'CANCELLED'}

        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            self.report({'WARNING'}, "No design data")
            return {'CANCELLED'}

        try:
            bboxes = json.loads(bbox_json)
            result = _client.snap(bboxes, props.jurisdiction)

            if result.get("success"):
                adjusted = result.get("bboxes", bboxes)
                adjustments = result.get("adjustments", [])
                context.scene["_design_bboxes"] = json.dumps(adjusted)
                design_bbox.enable(adjusted)
                props.compile_status = f"Snap: {len(adjustments)} adjustments"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Snap failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Snap error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_save(Operator):
    """Save current design to work_output.db (OrderLine + ASI)"""
    bl_idname = "bim.designer_save"
    bl_label = "Save Design"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            self.report({'WARNING'}, "No design data to save")
            return {'CANCELLED'}

        try:
            bboxes = json.loads(bbox_json)
            result = _client.save(
                building_id=props.building_name or "untitled",
                bboxes=bboxes,
                variant_label=f"v{props.last_element_count}",
            )

            if result.get("success"):
                variant_id = result.get("variantId", "?")
                design_bbox.mark_committed(design_bbox.get_section_ids())
                props.compile_status = f"Saved: {variant_id}"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Save failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Save error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_promote(Operator):
    """Promote design to BOM — governance gate (requires confirmation)"""
    bl_idname = "bim.designer_promote"
    bl_label = "Promote to BOM"
    bl_options = {'REGISTER', 'UNDO'}

    def invoke(self, context, event):
        # Show confirmation dialog before executing
        return context.window_manager.invoke_confirm(self, event)

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            self.report({'WARNING'}, "No design data to promote")
            return {'CANCELLED'}

        try:
            bboxes = json.loads(bbox_json)
            result = _client.promote(
                building_id=props.building_name or "untitled",
                owner="designer",  # TODO: from user preferences
                compliance_ref=props.jurisdiction,
                provenance="GENERATIVE",
                bboxes=bboxes,
            )

            if result.get("success"):
                dangles = result.get("dangles", [])
                created = result.get("bomEntriesCreated", 0)
                if dangles:
                    props.compile_status = f"Promoted with {len(dangles)} dangles"
                    self.report({'WARNING'}, props.compile_status)
                else:
                    props.compile_status = f"Promoted: {created} BOM entries created"
                    self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Promote failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Promote error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_execute_verb(Operator):
    """Execute a BIM COBOL verb on the active building"""
    bl_idname = "bim.designer_execute_verb"
    bl_label = "Execute Verb"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.building_id:
            self.report({'ERROR'}, "No building ID set")
            return {'CANCELLED'}

        if not props.verb_line:
            self.report({'ERROR'}, "No verb entered")
            return {'CANCELLED'}

        try:
            result = _client.execute_verb(
                building_id=props.building_id,
                verb_line=props.verb_line,
            )
            status = result.get("status", "unknown")
            props.verb_result = f"{status}: {result.get('message', '')}"
            props.compile_status = f"Verb {status}"
            self.report({'INFO'}, props.verb_result)
        except Exception as e:
            props.verb_result = f"Error: {e}"
            props.compile_status = f"Verb error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


# =============================================================================
# Registration
# =============================================================================

_classes = (
    BIM_OT_designer_connect,
    BIM_OT_designer_disconnect,
    BIM_OT_designer_list_buildings,
    BIM_OT_designer_compile,
    BIM_OT_designer_create_new,
    BIM_OT_designer_toggle_mode,
    BIM_OT_designer_focus_section,
    BIM_OT_designer_snap,
    BIM_OT_designer_save,
    BIM_OT_designer_promote,
    BIM_OT_designer_execute_verb,
)


def register():
    for cls in _classes:
        bpy.utils.register_class(cls)


def unregister():
    for cls in reversed(_classes):
        bpy.utils.unregister_class(cls)
