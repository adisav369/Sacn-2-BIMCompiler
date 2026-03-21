"""
TCP client for the Java DesignerServer.

Protocol: newline-delimited JSON (ndjson) over TCP, default port 9876.
No HTTP framework dependency.

Usage:
    client = DesignerClient()
    client.connect()
    result = client.compile("Ifc4_SampleHouse", "library/_SH_compile.db")
    client.disconnect()

Async notifications from the server (COMPILE_COMPLETE after auto-recompile)
are received on a background thread and dispatched to Blender via
bpy.app.timers for thread safety.
"""

import json
import socket
import threading
from typing import Callable, Optional

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 9876


class DesignerClient:
    """TCP client for the BIM Designer Java server.

    Threading contract: ``start_listener()`` and ``_send()`` must NOT be
    used concurrently on the same socket.  The listener owns the read side
    once started — synchronous ``_send()``/``_recv_line()`` calls will race
    on ``recv()`` and corrupt the stream.  Use the listener for push
    notifications only *after* the synchronous setup phase is complete,
    or route all reads through the listener with a response queue.
    """

    TIMEOUT_SECONDS = 10.0  # socket connect + recv timeout

    def __init__(self, host: str = DEFAULT_HOST, port: int = DEFAULT_PORT):
        self.host = host
        self.port = port
        self._sock: Optional[socket.socket] = None
        self._reader_thread: Optional[threading.Thread] = None
        self._on_status: Optional[Callable] = None
        self._running = False

    def connect(self) -> None:
        """Connect to the DesignerServer."""
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.settimeout(self.TIMEOUT_SECONDS)
        self._sock.connect((self.host, self.port))
        self._running = True

    def disconnect(self) -> None:
        """Disconnect from the server."""
        self._running = False
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def compile(self, building_id: str, bom_db_path: str,
                library_path: str = None, output_dir: str = None) -> dict:
        """Request a full compilation. Returns the response dict."""
        request = {
            "action": "compile",
            "buildingId": building_id,
            "bomDbPath": bom_db_path,
        }
        if library_path:
            request["libraryPath"] = library_path
        if output_dir:
            request["outputDir"] = output_dir
        return self._send(request)

    def execute_verb(self, building_id: str, verb_line: str) -> dict:
        """Execute a BIM COBOL verb. Returns the response dict."""
        return self._send({
            "action": "verb",
            "buildingId": building_id,
            "verbLine": verb_line,
        })

    def list_buildings(self) -> dict:
        """List available building types."""
        return self._send({"action": "listBuildings"})

    def list_categories(self, doc_sub_type: str) -> dict:
        """List BOM categories for a building type."""
        return self._send({
            "action": "listCategories",
            "docSubType": doc_sub_type,
        })

    def create_new(self, building_name: str, building_type: str,
                   jurisdiction: str, site_width_mm: float, site_depth_mm: float,
                   num_bedrooms: int, num_bathrooms: int, storeys: int = 1) -> dict:
        """Create a new building layout. Returns bboxes for design-mode rendering."""
        return self._send({
            "action": "createNew",
            "buildingName": building_name,
            "buildingType": building_type,
            "jurisdiction": jurisdiction,
            "siteWidthMm": site_width_mm,
            "siteDepthMm": site_depth_mm,
            "numBedrooms": num_bedrooms,
            "numBathrooms": num_bathrooms,
            "storeys": storeys,
        })

    def snap(self, bboxes: list, jurisdiction: str, grid_mm: int = 250,
             fix_rule: str = None, fix_bom_id: str = None) -> dict:
        """Snap bboxes to grid + validate. Returns adjusted bboxes.

        If fix_rule and fix_bom_id are set, click-to-fix is applied:
        the target bbox dimension is forced to meet the rule's minimum
        before normal snap processing.
        """
        req = {
            "action": "snap",
            "bboxes": bboxes,
            "jurisdiction": jurisdiction,
            "gridMm": grid_mm,
        }
        if fix_rule:
            req["fixRule"] = fix_rule
        if fix_bom_id:
            req["fixBomId"] = fix_bom_id
        return self._send(req)

    def set_jurisdiction(self, jurisdiction: str, bboxes: list) -> dict:
        """Switch jurisdiction and re-validate all bboxes."""
        return self._send({
            "action": "setJurisdiction",
            "jurisdiction": jurisdiction,
            "bboxes": bboxes,
        })

    def save(self, building_id: str, bboxes: list, variant_label: str = "") -> dict:
        """Save current design to work_output.db."""
        return self._send({
            "action": "save",
            "buildingId": building_id,
            "bboxes": bboxes,
            "variantLabel": variant_label,
        })

    def recall(self, building_id: str, variant_id: str) -> dict:
        """Recall a previous design variant."""
        return self._send({
            "action": "recall",
            "buildingId": building_id,
            "variantId": variant_id,
        })

    def list_variants(self, building_id: str) -> dict:
        """List saved variants for a building."""
        return self._send({
            "action": "listVariants",
            "buildingId": building_id,
        })

    def browse_items(self, search: str = None, category: str = None,
                     building_type: str = None,
                     container_width_mm: float = 0, container_depth_mm: float = 0,
                     container_height_mm: float = 0,
                     offset: int = 0, limit: int = 20) -> dict:
        """Browse product catalog with search + container fit check (§17.18)."""
        req = {"action": "browseItems", "offset": offset, "limit": limit}
        if search:
            req["search"] = search
        if category:
            req["category"] = category
        if building_type:
            req["buildingType"] = building_type
        if container_width_mm > 0:
            req["containerWidthMm"] = container_width_mm
        if container_depth_mm > 0:
            req["containerDepthMm"] = container_depth_mm
        if container_height_mm > 0:
            req["containerHeightMm"] = container_height_mm
        return self._send(req)

    def find_similar(self, product_id: str, limit: int = 10,
                     container_width_mm: float = 0, container_depth_mm: float = 0,
                     container_height_mm: float = 0) -> dict:
        """Find products similar to a given product (§25.3 — JEPA embedding similarity)."""
        req = {
            "action": "findSimilar",
            "productId": product_id,
            "limit": limit,
        }
        if container_width_mm > 0:
            req["containerWidthMm"] = container_width_mm
        if container_depth_mm > 0:
            req["containerDepthMm"] = container_depth_mm
        if container_height_mm > 0:
            req["containerHeightMm"] = container_height_mm
        return self._send(req)

    def promote(self, building_id: str, owner: str, compliance_ref: str,
                provenance: str, bboxes: list) -> dict:
        """Promote design to BOM — governance gate."""
        return self._send({
            "action": "promote",
            "buildingId": building_id,
            "owner": owner,
            "complianceRef": compliance_ref,
            "provenance": provenance,
            "bboxes": bboxes,
        })

    def place_item(self, building_id: str, room_bom_id: str, product_id: str,
                   offset_x: float = 0, offset_y: float = 0, offset_z: float = 0,
                   current_bboxes: list = None) -> dict:
        """Place a product item into a room at a given offset (§15)."""
        req = {
            "action": "placeItem",
            "buildingId": building_id,
            "roomBomId": room_bom_id,
            "productId": product_id,
            "offsetXMm": offset_x,
            "offsetYMm": offset_y,
            "offsetZMm": offset_z,
        }
        if current_bboxes:
            req["currentBboxes"] = current_bboxes
        return self._send(req)

    def click_to_place(self, building_id: str,
                       click_x: float, click_y: float, click_z: float,
                       discipline: str = "ARC",
                       current_bboxes: list = None) -> dict:
        """Interactive click-to-place: resolve click → room → place seed (§18.8)."""
        req = {
            "action": "clickToPlace",
            "buildingId": building_id,
            "clickXMm": click_x,
            "clickYMm": click_y,
            "clickZMm": click_z,
            "discipline": discipline,
        }
        if current_bboxes:
            req["currentBboxes"] = current_bboxes
        return self._send(req)

    def add_room(self, building_id: str, category: str, storey: str = "GF") -> dict:
        """Add a room of the given category to the building layout (§16)."""
        return self._send({
            "action": "addRoom",
            "buildingId": building_id,
            "category": category,
            "storey": storey,
        })

    def remove_room(self, building_id: str, room_bom_id: str) -> dict:
        """Remove a room from the building layout (§16)."""
        return self._send({
            "action": "removeRoom",
            "buildingId": building_id,
            "roomBomId": room_bom_id,
        })

    def add_storey(self, building_id: str) -> dict:
        """Add a new storey to the building (§16)."""
        return self._send({
            "action": "addStorey",
            "buildingId": building_id,
        })

    # ── FL-2: Flywheel Advisory Panel (§27) ─────────────────────────

    def list_advisories(self, building_id: str) -> dict:
        """List flywheel advisories for a building (§27).

        Returns: { success, advisories: [{layer, severity, message, ...}],
                   infoCount, warningCount, suggestionCount, error }
        """
        return self._send({
            "action": "listAdvisories",
            "buildingId": building_id,
        })

    def suggest_dimensions(self, ifc_class: str) -> dict:
        """Suggest typical dimensions for an IFC class from mined data (FL-3 prep).

        Returns: { success, ifcClass, typicalMinW, typicalMaxW,
                   typicalMinD, typicalMaxD, typicalMinH, typicalMaxH,
                   observationCount, nearestProducts: [{productId, name, ...}], error }
        """
        return self._send({
            "action": "suggestDimensions",
            "ifcClass": ifc_class,
        })

    def get_element_metadata(self, bom_id: str, building_id: str = "") -> dict:
        """Get metadata for a BOM element — properties popup (§26.6).

        Returns: { bomId, name, category, dimensions: {w,d,h},
                   material, constructionSystem, compliance: [{rule,status}],
                   productId, costUnit, currency }
        """
        req = {
            "action": "getElementMetadata",
            "bomId": bom_id,
        }
        if building_id:
            req["buildingId"] = building_id
        return self._send(req)

    def cost_of_change(self, chain_guids: list, original_positions: list,
                       proposed_positions: list) -> dict:
        """Query cost impact of a proposed move — side-effect-free (§26.12.3).

        Returns: { materialDelta, fittingsDelta, labourHrs, costDelta, currency,
                   compliance: [{rule,status,detail}],
                   newClashes: [{guidA,guidB,discipline}] }
        """
        return self._send({
            "action": "costOfChange",
            "chainGuids": chain_guids,
            "originalPositions": original_positions,
            "proposedPositions": proposed_positions,
        })

    def move_chain(self, chain_guids: list, original_positions: list,
                   proposed_positions: list) -> dict:
        """Commit a chain move — writes to DB, may spawn R_Request (§26.12/§26.13).

        Returns: { success, updatedPositions, costDelta, changeRequest? }
        """
        return self._send({
            "action": "moveChain",
            "chainGuids": chain_guids,
            "originalPositions": original_positions,
            "proposedPositions": proposed_positions,
        })

    def get_chain(self, guid: str) -> dict:
        """Get all connected elements for chain highlight (§26.12.1).

        Returns: { chainGuids: [...], systemId: str }
        """
        return self._send({
            "action": "getChain",
            "guid": guid,
        })

    # ── G-9 ORDER View + BOM Outliner (§17.11, §17.19) ──────────────

    def list_order_lines(self, building_id: str) -> dict:
        """List all C_OrderLine rows for ORDER View tabular display (§17.11).

        Returns: { success: bool, lines: [...], error: str|null }
        """
        return self._send({
            "action": "listOrderLines",
            "buildingId": building_id,
        })

    def update_order_line(self, order_line_id: int, building_id: str,
                          field: str, value: str) -> dict:
        """Update a single field on a C_OrderLine — inline table edit (§17.11).

        Only whitelisted fields: aabb_width_mm, aabb_depth_mm, aabb_height_mm,
        dx, dy, dz, Qty.

        Returns: { success: bool, updated: {...}, error: str|null }
        """
        return self._send({
            "action": "updateOrderLine",
            "orderLineId": order_line_id,
            "buildingId": building_id,
            "field": field,
            "value": value,
        })

    def get_bom_tree(self, building_id: str) -> dict:
        """Get hierarchical BOM tree for BOM Outliner (§17.19).

        Returns: { success: bool, roots: [{..., children: [...]}], error: str|null }
        """
        return self._send({
            "action": "getBomTree",
            "buildingId": building_id,
        })

    def _send(self, request: dict) -> dict:
        """Send a request and read the response (synchronous)."""
        if not self._sock:
            raise ConnectionError("Not connected to DesignerServer")
        line = json.dumps(request) + "\n"
        self._sock.sendall(line.encode("utf-8"))
        return self._recv_line()

    def _recv_line(self) -> dict:
        """Read one newline-delimited JSON response."""
        buf = b""
        while True:
            chunk = self._sock.recv(4096)
            if not chunk:
                raise ConnectionError("Server closed connection")
            buf += chunk
            if b"\n" in buf:
                line, _ = buf.split(b"\n", 1)
                return json.loads(line.decode("utf-8"))

    def start_listener(self, on_status: Callable) -> None:
        """Start a background thread to receive async status messages.

        Args:
            on_status: Callback receiving a dict. In Blender, this should
                       queue work via bpy.app.timers for thread safety.
        """
        self._on_status = on_status
        self._reader_thread = threading.Thread(
            target=self._listen_loop, daemon=True, name="designer-listener"
        )
        self._reader_thread.start()

    def _listen_loop(self) -> None:
        """Background loop reading async push messages."""
        buf = b""
        while self._running and self._sock:
            try:
                chunk = self._sock.recv(4096)
                if not chunk:
                    break
                buf += chunk
                while b"\n" in buf:
                    line, buf = buf.split(b"\n", 1)
                    msg = json.loads(line.decode("utf-8"))
                    if self._on_status and msg.get("type"):
                        self._on_status(msg)
            except OSError:
                break
