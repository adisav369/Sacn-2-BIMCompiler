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
    """TCP client for the BIM Designer Java server."""

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
