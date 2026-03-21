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
    """Compile the active building via the DesignerServer.

    In Real Mode: uses building_id + bom_db_path from A.2 selector.
    In Design Mode: uses building_name + output_db_path from design state.
    """
    bl_idname = "bim.designer_compile"
    bl_label = "Compile"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        # Auto-detect building ID and BOM DB path from context
        building_id = props.building_id
        bom_db_path = props.bom_db_path

        # In Design Mode, fall back to create_new building name
        if not building_id and props.building_name:
            building_id = props.building_name
        if not bom_db_path and props.output_db_path:
            bom_db_path = props.output_db_path

        if not building_id:
            self.report({'ERROR'}, "No building ID — set in A.2 or Create New first")
            return {'CANCELLED'}

        if not bom_db_path:
            self.report({'ERROR'}, "No BOM DB path — set in A.2 or save first")
            return {'CANCELLED'}

        try:
            result = _client.compile(
                building_id=building_id,
                bom_db_path=bom_db_path,
            )
            success = result.get("success", False)
            count = result.get("elementCount", 0)
            elapsed = result.get("compileTimeMs", 0)
            props.last_element_count = count

            if success:
                props.compile_status = f"Compiled — {count} elements in {elapsed}ms"
                self.report({'INFO'}, props.compile_status)
            else:
                error = result.get("error", "unknown error")
                props.compile_status = f"Compile failed: {error}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
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
    """Toggle between Design Mode (draft bboxes) and Real Mode (Federation view).

    Implements WF-BB §26.2:
      Phase 1 — No full geometry: GPU overlay bboxes (existing behaviour).
      Phase 2 — Full geometry: per-object BOUNDS, focused item SOLID + bbox.
    """
    bl_idname = "bim.designer_toggle_mode"
    bl_label = "Toggle Design/Real"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        props = _get_props(context)

        if props.design_mode:
            # Switch to Real Mode — restore everything
            if design_bbox.is_phase2():
                design_bbox.exit_phase2()
            else:
                design_bbox.disable()
            props.design_mode = False
            props.active_section = ""
            props.compile_status = "Real Mode"
            self.report({'INFO'}, "Real Mode — Federation view restored")
        else:
            # Detect Phase 1 vs Phase 2 (§26.8)
            if design_bbox.has_full_geometry():
                # Phase 2 — full geometry exists, use per-object BOUNDS
                if design_bbox.enter_phase2():
                    props.design_mode = True
                    props.compile_status = "Design Mode (BOUNDS)"
                    self.report({'INFO'},
                                "Design Mode — select item for bbox focus")
                    return {'FINISHED'}
                else:
                    props.compile_status = "No Designer objects found"
                    self.report({'WARNING'}, props.compile_status)
                    return {'CANCELLED'}
            else:
                # Phase 1 — no full geometry, use GPU overlay bboxes
                bbox_json = context.scene.get("_design_bboxes", "")
                if bbox_json:
                    bboxes = json.loads(bbox_json)
                    if design_bbox.enable(bboxes):
                        props.design_mode = True
                        props.compile_status = "Design Mode"
                        self.report({'INFO'},
                                    "Design Mode — draft bboxes active")
                        return {'FINISHED'}

                props.compile_status = (
                    "No design data — generate a building first")
                self.report({'WARNING'}, props.compile_status)
                return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_focus_section(Operator):
    """Focus a section in Design Mode — vivid color for that bbox.

    Phase 1: GPU overlay focus (existing).
    Phase 2: Per-object SOLID + bbox overlay + orientation markers (§26.4).
    """
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
            if design_bbox.is_phase2():
                design_bbox.unfocus_phase2()
            else:
                design_bbox.focus_section(None)
            props.active_section = ""
        else:
            if design_bbox.is_phase2():
                # Phase 2: find matching Blender object and focus it
                obj = self._find_object_for_section(self.section_id)
                if obj:
                    design_bbox.focus_phase2(obj, self.section_id)
                else:
                    # Fallback: object not found, may be a new bbox-only item
                    design_bbox.focus_section(self.section_id)
            else:
                design_bbox.focus_section(self.section_id)
            props.active_section = self.section_id

        return {'FINISHED'}

    def _find_object_for_section(self, section_id: str):
        """Find the Blender object matching a bomId/section_id.

        Checks object custom properties and name for a match.
        """
        for obj in bpy.data.objects:
            if obj.type != 'MESH':
                continue
            # Check custom property first (set by db_loader or compiler)
            if obj.get("bomId") == section_id:
                return obj
            if obj.get("guid") == section_id:
                return obj
            # Fallback: name match
            if obj.name == section_id:
                return obj
        return None


class BIM_OT_designer_peek_metadata(Operator):
    """Show metadata popup for the focused element (§26.6).

    Double-click-hold on a focused bbox shows properties + orientation markers.
    Queries the backend via getElementMetadata verb.
    """
    bl_idname = "bim.designer_peek_metadata"
    bl_label = "Peek Metadata"
    bl_options = {'REGISTER'}

    bom_id: bpy.props.StringProperty(name="BOM ID")

    def execute(self, context):
        global _client
        props = _get_props(context)

        if not props.design_mode:
            self.report({'WARNING'}, "Not in Design Mode")
            return {'CANCELLED'}

        # Toggle peek
        if design_bbox.is_peek_active():
            design_bbox.exit_peek()
            return {'FINISHED'}

        # Try to get metadata from backend
        metadata = None
        if _client and props.is_connected and self.bom_id:
            try:
                result = _client.get_element_metadata(
                    self.bom_id,
                    building_id=props.building_name or props.building_id or "")
                if result.get("success") or result.get("bomId"):
                    metadata = result
            except Exception as e:
                print(f"Peek metadata error: {e}")

        # Fallback: build metadata from local bbox data
        if not metadata:
            local = design_bbox.get_metadata().get(self.bom_id)
            if local:
                metadata = {
                    "bomId": self.bom_id,
                    "name": local.get("name", self.bom_id),
                    "category": local.get("category", local.get("bomType", "?")),
                    "dimensions": {
                        "w": local.get("maxX", 0) - local.get("minX", 0),
                        "d": local.get("maxY", 0) - local.get("minY", 0),
                        "h": local.get("maxZ", 0) - local.get("minZ", 0),
                    },
                }
            else:
                metadata = {"bomId": self.bom_id, "name": self.bom_id}

        design_bbox.enter_peek(metadata)
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
                # Store adjustments for status strip display (UX-F-18/19)
                context.scene["_snap_adjustments"] = json.dumps(adjustments)
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
                output_path = result.get("outputDbPath", "")
                design_bbox.mark_committed(design_bbox.get_section_ids())
                props.compile_status = f"Saved: {variant_id}"
                props.output_db_path = output_path
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


class BIM_OT_designer_browse_items(Operator):
    """Browse product catalog — search-first with container fit check (§17.18)"""
    bl_idname = "bim.designer_browse_items"
    bl_label = "Browse Items"
    bl_options = {'REGISTER'}

    search: bpy.props.StringProperty(name="Search", default="")
    category: bpy.props.StringProperty(name="Category", default="")

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        # Get container dims from the focused room
        cont_w, cont_d, cont_h = 0, 0, 0
        bbox_json = context.scene.get("_design_bboxes", "")
        if bbox_json and props.active_section:
            for bb in json.loads(bbox_json):
                if bb.get("bomId") == props.active_section:
                    cont_w = bb["maxX"] - bb["minX"]
                    cont_d = bb["maxY"] - bb["minY"]
                    cont_h = bb["maxZ"] - bb["minZ"]
                    break

        try:
            result = _client.browse_items(
                search=self.search or props.browse_search or None,
                category=self.category or None,
                building_type=props.building_type or None,
                container_width_mm=cont_w,
                container_depth_mm=cont_d,
                container_height_mm=cont_h,
                offset=props.browse_offset,
                limit=20,
            )

            if result.get("success"):
                context.scene["_browse_items"] = json.dumps(result.get("items", []))
                context.scene["_browse_categories"] = json.dumps(result.get("categories", []))
                props.browse_total = result.get("totalCount", 0)
                props.compile_status = f"Browse: {len(result.get('items', []))} of {props.browse_total}"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Browse failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Browse error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_browse_next(Operator):
    """Next page of browse results"""
    bl_idname = "bim.designer_browse_next"
    bl_label = "Next Page"
    bl_options = {'REGISTER'}

    def execute(self, context):
        props = _get_props(context)
        props.browse_offset = min(props.browse_offset + 20, max(0, props.browse_total - 1))
        bpy.ops.bim.designer_browse_items(
            search=props.browse_search, category="")
        return {'FINISHED'}


class BIM_OT_designer_browse_prev(Operator):
    """Previous page of browse results"""
    bl_idname = "bim.designer_browse_prev"
    bl_label = "Prev Page"
    bl_options = {'REGISTER'}

    def execute(self, context):
        props = _get_props(context)
        props.browse_offset = max(0, props.browse_offset - 20)
        bpy.ops.bim.designer_browse_items(
            search=props.browse_search, category="")
        return {'FINISHED'}


class BIM_OT_designer_place_item(Operator):
    """Place a product item into the focused room at an offset (§15)"""
    bl_idname = "bim.designer_place_item"
    bl_label = "Place Item"
    bl_options = {'REGISTER', 'UNDO'}

    product_id: bpy.props.StringProperty(name="Product ID")

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.active_section:
            self.report({'ERROR'}, "No room focused — click a room first")
            return {'CANCELLED'}

        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            self.report({'ERROR'}, "No design data")
            return {'CANCELLED'}

        try:
            bboxes = json.loads(bbox_json)
            result = _client.place_item(
                building_id=props.building_name or "untitled",
                room_bom_id=props.active_section,
                product_id=self.product_id,
                offset_x=0, offset_y=0, offset_z=0,
                current_bboxes=bboxes,
            )

            if result.get("success"):
                new_bbox = result.get("bbox")
                if new_bbox:
                    bboxes.append(new_bbox)
                    context.scene["_design_bboxes"] = json.dumps(bboxes)
                    design_bbox.enable(bboxes)
                props.compile_status = f"Placed: {self.product_id}"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Place failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Place error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_click_to_place(Operator):
    """Click-to-Place: click viewport to place discipline item in resolved room (§18.8)"""
    bl_idname = "bim.designer_click_to_place"
    bl_label = "Click to Place"
    bl_options = {'REGISTER', 'UNDO'}

    def invoke(self, context, event):
        """Start modal — wait for user click in 3D viewport."""
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.design_mode:
            self.report({'ERROR'}, "Enter Design Mode first")
            return {'CANCELLED'}

        context.window.cursor_set('CROSSHAIR')
        context.window_manager.modal_handler_add(self)
        self.report({'INFO'}, "Click in viewport to place item...")
        return {'RUNNING_MODAL'}

    def modal(self, context, event):
        if event.type == 'LEFTMOUSE' and event.value == 'PRESS':
            context.window.cursor_set('DEFAULT')
            return self._do_place(context, event)
        elif event.type in {'RIGHTMOUSE', 'ESC'}:
            context.window.cursor_set('DEFAULT')
            self.report({'INFO'}, "Click-to-Place cancelled")
            return {'CANCELLED'}

        return {'PASS_THROUGH'}

    def _do_place(self, context, event):
        """Ray-cast from mouse into viewport, compute world position, send to server."""
        import bpy_extras.view3d_utils as v3d
        from mathutils import Vector

        props = _get_props(context)
        region = context.region
        rv3d = context.space_data.region_3d

        # Ray from mouse position through viewport
        coord = (event.mouse_region_x, event.mouse_region_y)
        ray_origin = v3d.region_2d_to_origin_3d(region, rv3d, coord)
        ray_direction = v3d.region_2d_to_vector_3d(region, rv3d, coord)

        # Intersect with the ground plane (Z = 0) or focused storey plane
        # Default: intersect with Z=0 plane
        floor_z = 0.0
        bbox_json = context.scene.get("_design_bboxes", "")
        if bbox_json and props.active_section:
            for bb in json.loads(bbox_json):
                if bb.get("bomId") == props.active_section:
                    floor_z = bb.get("minZ", 0)
                    break

        # Plane intersection: solve ray_origin + t * ray_direction = (x, y, floor_z)
        if abs(ray_direction.z) < 1e-6:
            self.report({'WARNING'}, "Cannot ray-cast at this angle")
            return {'CANCELLED'}

        t = (floor_z - ray_origin.z) / ray_direction.z
        if t < 0:
            self.report({'WARNING'}, "Click behind camera")
            return {'CANCELLED'}

        hit = ray_origin + t * ray_direction
        # Blender uses metres; server expects mm
        click_x = hit.x * 1000.0
        click_y = hit.y * 1000.0
        click_z = hit.z * 1000.0

        # Get current bboxes and discipline
        if not bbox_json:
            self.report({'ERROR'}, "No design data")
            return {'CANCELLED'}

        bboxes = json.loads(bbox_json)
        discipline = props.click_discipline if hasattr(props, 'click_discipline') else "ARC"

        try:
            result = _client.click_to_place(
                building_id=props.building_name or "untitled",
                click_x=click_x,
                click_y=click_y,
                click_z=click_z,
                discipline=discipline,
                current_bboxes=bboxes,
            )

            if result.get("success"):
                placed = result.get("placedItems", [])
                room_id = result.get("resolvedRoomBomId", "?")
                mep_reqs = result.get("mepRequirements", [])
                if placed:
                    for item in placed:
                        bboxes.append(item)
                    context.scene["_design_bboxes"] = json.dumps(bboxes)
                    design_bbox.enable(bboxes)

                # Build coverage summary
                if mep_reqs:
                    total_needed = sum(r.get("qtyNormal", 0) for r in mep_reqs)
                    total_placed = sum(r.get("qtyPlaced", 0) for r in mep_reqs)
                    coverage_pct = int(100 * total_placed / max(total_needed, 1))
                    # Store MEP coverage for panel display
                    context.scene["_mep_coverage"] = json.dumps(mep_reqs)
                    props.compile_status = (
                        f"{room_id}: {len(placed)} items, "
                        f"coverage {total_placed}/{total_needed} ({coverage_pct}%)"
                    )
                elif placed:
                    props.compile_status = f"Placed {len(placed)} item(s) in {room_id}"
                else:
                    props.compile_status = f"Room {room_id}: no fitting products"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Click-to-Place: {result.get('error', '')}"
                self.report({'WARNING'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Click-to-Place error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_find_similar(Operator):
    """Find products similar to a selected item — JEPA embedding similarity (§25.3)"""
    bl_idname = "bim.designer_find_similar"
    bl_label = "Find Similar"
    bl_options = {'REGISTER'}

    product_id: bpy.props.StringProperty(name="Product ID")

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        # Get container dims from the focused room
        cont_w, cont_d, cont_h = 0, 0, 0
        bbox_json = context.scene.get("_design_bboxes", "")
        if bbox_json and props.active_section:
            for bb in json.loads(bbox_json):
                if bb.get("bomId") == props.active_section:
                    cont_w = bb["maxX"] - bb["minX"]
                    cont_d = bb["maxY"] - bb["minY"]
                    cont_h = bb["maxZ"] - bb["minZ"]
                    break

        try:
            result = _client.find_similar(
                product_id=self.product_id,
                limit=10,
                container_width_mm=cont_w,
                container_depth_mm=cont_d,
                container_height_mm=cont_h,
            )

            if result.get("success"):
                context.scene["_browse_items"] = json.dumps(result.get("items", []))
                # Clear categories — similarity results aren't category-filtered
                context.scene["_browse_categories"] = json.dumps([])
                item_count = len(result.get("items", []))
                props.browse_total = item_count
                props.browse_offset = 0
                props.compile_status = f"Similar to {self.product_id}: {item_count} items"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Find similar failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Find similar error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_add_room(Operator):
    """Add a room of the given category to the building layout (§16)"""
    bl_idname = "bim.designer_add_room"
    bl_label = "Add Room"
    bl_options = {'REGISTER', 'UNDO'}

    category: bpy.props.EnumProperty(
        name="Category",
        items=[
            ('BEDROOM', "Bedroom", "Add a bedroom"),
            ('BATHROOM', "Bathroom", "Add a bathroom"),
            ('LIVING', "Living", "Add a living room"),
            ('KITCHEN', "Kitchen", "Add a kitchen"),
        ],
        default='BEDROOM',
    )

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        try:
            result = _client.add_room(
                building_id=props.building_name or "untitled",
                category=self.category,
                storey="GF",
            )

            if result.get("success"):
                bboxes = result.get("bboxes", [])
                context.scene["_design_bboxes"] = json.dumps(bboxes)
                design_bbox.enable(bboxes)
                room_count = result.get("roomCount", 0)
                props.compile_status = f"Added {self.category} — {room_count} rooms"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Add room failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Add room error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_remove_room(Operator):
    """Remove a room from the building layout (§16)"""
    bl_idname = "bim.designer_remove_room"
    bl_label = "Remove Room"
    bl_options = {'REGISTER', 'UNDO'}

    room_bom_id: bpy.props.StringProperty(name="Room BOM ID")

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        try:
            result = _client.remove_room(
                building_id=props.building_name or "untitled",
                room_bom_id=self.room_bom_id,
            )

            if result.get("success"):
                bboxes = result.get("bboxes", [])
                context.scene["_design_bboxes"] = json.dumps(bboxes)
                design_bbox.enable(bboxes)
                room_count = result.get("roomCount", 0)
                props.compile_status = f"Removed room — {room_count} rooms"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Remove room failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Remove room error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_add_storey(Operator):
    """Add a new storey to the building (§16)"""
    bl_idname = "bim.designer_add_storey"
    bl_label = "Add Storey"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        try:
            result = _client.add_storey(
                building_id=props.building_name or "untitled",
            )

            if result.get("success"):
                bboxes = result.get("bboxes", [])
                context.scene["_design_bboxes"] = json.dumps(bboxes)
                design_bbox.enable(bboxes)
                room_count = result.get("roomCount", 0)
                props.compile_status = f"Added storey — {room_count} rooms"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Add storey failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Add storey error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_auto_fix(Operator):
    """Auto-fix a failed compliance rule by expanding room to minimum (§17)"""
    bl_idname = "bim.designer_auto_fix"
    bl_label = "Auto-Fix"
    bl_options = {'REGISTER', 'UNDO'}

    fix_rule: bpy.props.StringProperty(name="Fix Rule")
    fix_bom_id: bpy.props.StringProperty(name="Fix BOM ID")

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
            result = _client.snap(
                bboxes, props.jurisdiction,
                fix_rule=self.fix_rule, fix_bom_id=self.fix_bom_id,
            )

            if result.get("success"):
                adjusted = result.get("bboxes", bboxes)
                adjustments = result.get("adjustments", [])
                context.scene["_design_bboxes"] = json.dumps(adjusted)
                context.scene["_snap_adjustments"] = json.dumps(adjustments)
                design_bbox.enable(adjusted)
                props.compile_status = f"Fixed {self.fix_bom_id}: {len(adjustments)} adjustments"
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Auto-fix failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Auto-fix error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_set_jurisdiction(Operator):
    """Switch jurisdiction and re-validate all bboxes (§17)"""
    bl_idname = "bim.designer_set_jurisdiction"
    bl_label = "Set Jurisdiction"
    bl_options = {'REGISTER', 'UNDO'}

    jurisdiction: bpy.props.StringProperty(name="Jurisdiction")

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            self.report({'WARNING'}, "No design data")
            return {'CANCELLED'}

        try:
            bboxes = json.loads(bbox_json)
            result = _client.set_jurisdiction(self.jurisdiction, bboxes)

            if result.get("success"):
                verdicts = result.get("verdicts", [])
                rule_count = result.get("ruleCount", 0)
                # Store verdicts for status strip display
                context.scene["_jurisdiction_verdicts"] = json.dumps(verdicts)
                props.jurisdiction = self.jurisdiction
                props.compile_status = (
                    f"Jurisdiction: {self.jurisdiction} "
                    f"({rule_count} rules, {len(verdicts)} verdicts)"
                )
                self.report({'INFO'}, props.compile_status)
            else:
                props.compile_status = f"Set jurisdiction failed: {result.get('error', '')}"
                self.report({'ERROR'}, props.compile_status)
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Set jurisdiction error: {e}"
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_update_room_dims(Operator):
    """Update focused room dimensions from slider values (§17)"""
    bl_idname = "bim.designer_update_room_dims"
    bl_label = "Apply Room Dimensions"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        props = _get_props(context)

        if not props.design_mode or not props.active_section:
            self.report({'WARNING'}, "No room focused")
            return {'CANCELLED'}

        bbox_json = context.scene.get("_design_bboxes", "")
        if not bbox_json:
            self.report({'WARNING'}, "No design data")
            return {'CANCELLED'}

        try:
            bboxes = json.loads(bbox_json)
            updated = False
            for bb in bboxes:
                if bb.get("bomId") == props.active_section and bb.get("bomType") == "ROOM":
                    bb["maxX"] = bb["minX"] + props.room_width_mm
                    bb["maxY"] = bb["minY"] + props.room_depth_mm
                    updated = True
                    break

            if updated:
                context.scene["_design_bboxes"] = json.dumps(bboxes)
                design_bbox.enable(bboxes)
                props.compile_status = (
                    f"Room {props.active_section}: "
                    f"{props.room_width_mm:.0f} x {props.room_depth_mm:.0f} mm"
                )
                self.report({'INFO'}, props.compile_status)
            else:
                self.report({'WARNING'}, "Room not found in bboxes")
                return {'CANCELLED'}
        except Exception as e:
            props.compile_status = f"Update dims error: {e}"
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
# G-9 ORDER View + BOM Outliner (§17.11, §17.19)
# =============================================================================

class BIM_OT_designer_list_order_lines(Operator):
    """Fetch C_OrderLine data for ORDER View tabular display"""
    bl_idname = "bim.designer_list_order_lines"
    bl_label = "Refresh ORDER View"
    bl_options = {'REGISTER'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.building_id:
            self.report({'ERROR'}, "No building ID set")
            return {'CANCELLED'}

        try:
            result = _client.list_order_lines(props.building_id)
            if result.get("success"):
                lines = result.get("lines", [])
                context.scene["_order_lines"] = json.dumps(lines)
                props.compile_status = f"ORDER View: {len(lines)} lines"
                self.report({'INFO'}, f"Loaded {len(lines)} order lines")
            else:
                error = result.get("error", "Unknown error")
                props.compile_status = f"ORDER View error: {error}"
                self.report({'WARNING'}, error)
        except Exception as e:
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_update_order_line(Operator):
    """Update a single field on a C_OrderLine (inline ORDER View edit)"""
    bl_idname = "bim.designer_update_order_line"
    bl_label = "Update Order Line"
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.building_id or not props.edit_order_line_id:
            self.report({'ERROR'}, "No building or order line selected")
            return {'CANCELLED'}

        if not props.edit_field or not props.edit_value:
            self.report({'ERROR'}, "No field or value specified")
            return {'CANCELLED'}

        try:
            result = _client.update_order_line(
                props.edit_order_line_id,
                props.building_id,
                props.edit_field,
                props.edit_value,
            )
            if result.get("success"):
                # Refresh ORDER View data
                bpy.ops.bim.designer_list_order_lines()
                props.compile_status = f"Updated line {props.edit_order_line_id}"
                self.report({'INFO'}, f"Updated {props.edit_field} = {props.edit_value}")
            else:
                error = result.get("error", "Update failed")
                props.compile_status = f"Update error: {error}"
                self.report({'WARNING'}, error)
        except Exception as e:
            self.report({'ERROR'}, str(e))
            return {'CANCELLED'}

        return {'FINISHED'}


class BIM_OT_designer_get_bom_tree(Operator):
    """Fetch hierarchical BOM tree for BOM Outliner display"""
    bl_idname = "bim.designer_get_bom_tree"
    bl_label = "Refresh BOM Outliner"
    bl_options = {'REGISTER'}

    def execute(self, context):
        global _client
        props = _get_props(context)

        if _client is None or not props.is_connected:
            self.report({'ERROR'}, "Not connected")
            return {'CANCELLED'}

        if not props.building_id:
            self.report({'ERROR'}, "No building ID set")
            return {'CANCELLED'}

        try:
            result = _client.get_bom_tree(props.building_id)
            if result.get("success"):
                roots = result.get("roots", [])
                context.scene["_bom_tree"] = json.dumps(roots)
                # Count total nodes recursively
                def count_nodes(nodes):
                    n = len(nodes)
                    for node in nodes:
                        n += count_nodes(node.get("children", []))
                    return n
                total = count_nodes(roots)
                props.compile_status = f"BOM Outliner: {total} nodes"
                self.report({'INFO'}, f"Loaded BOM tree ({total} nodes)")
            else:
                error = result.get("error", "Unknown error")
                props.compile_status = f"BOM tree error: {error}"
                self.report({'WARNING'}, error)
        except Exception as e:
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
    BIM_OT_designer_browse_items,
    BIM_OT_designer_browse_next,
    BIM_OT_designer_browse_prev,
    BIM_OT_designer_place_item,
    BIM_OT_designer_click_to_place,
    BIM_OT_designer_find_similar,
    BIM_OT_designer_add_room,
    BIM_OT_designer_remove_room,
    BIM_OT_designer_add_storey,
    BIM_OT_designer_auto_fix,
    BIM_OT_designer_set_jurisdiction,
    BIM_OT_designer_update_room_dims,
    BIM_OT_designer_peek_metadata,
    BIM_OT_designer_execute_verb,
    BIM_OT_designer_list_order_lines,
    BIM_OT_designer_update_order_line,
    BIM_OT_designer_get_bom_tree,
)


def register():
    for cls in _classes:
        bpy.utils.register_class(cls)


def unregister():
    for cls in reversed(_classes):
        bpy.utils.unregister_class(cls)
