"""
BIM Designer Operators
----------------------
Thin Blender operators that delegate all logic to the Java DesignerServer
via the TCP client.
"""

from __future__ import annotations

import bpy
from bpy.types import Operator

from .client import DesignerClient

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
    """Create a new generative building via the DesignerServer"""
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
            result = _client._send({
                "action": "createBuilding",
                "buildingName": props.building_name,
                "buildingType": props.building_type,
                "jurisdiction": props.jurisdiction,
                "siteWidthMm": props.site_width_mm,
                "siteDepthMm": props.site_depth_mm,
                "numBedrooms": props.num_bedrooms,
                "numBathrooms": props.num_bathrooms,
            })
            status = result.get("status", "unknown")
            props.compile_status = f"Create: {status}"
            self.report({'INFO'}, props.compile_status)
        except Exception as e:
            props.compile_status = f"Create error: {e}"
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
    BIM_OT_designer_execute_verb,
)


def register():
    for cls in _classes:
        bpy.utils.register_class(cls)


def unregister():
    for cls in reversed(_classes):
        bpy.utils.unregister_class(cls)
