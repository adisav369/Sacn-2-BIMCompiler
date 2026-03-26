package com.bim.designer.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.bim.designer.protocol.JsonProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web UI server — two-port architecture for reliability.
 *
 * <p>Port 9878: JDK HttpServer — serves static HTML/JS/CSS (proven in BackOfficeServer).
 * <p>Port 9879: Raw ServerSocket — WebSocket only (no HTTP mixing).
 *
 * <p>Browser loads page from :9878, JS connects WebSocket to :9879.
 *
 * // Implementing BIM_Designer_UserGuide.md §13 — Witness: W-WS-2
 */
public class WebUIServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WebUIServer.class.getName());
    static final int DEFAULT_PORT = 9878;
    static final int DEFAULT_WS_PORT = 9879;

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".js", "application/javascript; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".json", "application/json; charset=utf-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".ico", "image/x-icon")
    );

    private final DesignerServer designer;
    private final Path webRoot;
    private final Path libDir;
    private final int httpPort;

    private static final long IDLE_TIMEOUT_MS = 4 * 60 * 60 * 1000L; // 4 hours

    private HttpServer httpServer;
    private volatile boolean running;
    private volatile long lastActivityMs = System.currentTimeMillis();

    // ── Bidirectional sync state (Bonsai ↔ Web UI) ──────────
    private volatile Map<String, String> currentSelection = Map.of();
    private final Set<OutputStream> sseClients = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentLinkedQueue<Map<String, Object>> pendingCommands =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    public WebUIServer(DesignerServer designer, Path webRoot, Path libDir, int httpPort) {
        this.designer = designer;
        this.webRoot = webRoot;
        this.libDir = libDir;
        this.httpPort = httpPort;
    }

    public WebUIServer(DesignerServer designer, Path webRoot, int httpPort) {
        this(designer, webRoot,
                webRoot.getParent() != null ? webRoot.getParent().resolve("library") : Path.of("library"),
                httpPort);
    }

    public WebUIServer(DesignerServer designer, Path webRoot) {
        this(designer, webRoot, DEFAULT_PORT);
    }

    /** Start HTTP server. Blocks until stop(). */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(8));
        httpServer.createContext("/api", this::handleApi);
        httpServer.createContext("/events", this::handleSSE);
        httpServer.createContext("/", this::handleStatic);
        httpServer.start();
        running = true;
        lastActivityMs = System.currentTimeMillis();
        LOG.info("WebUIServer listening on http://localhost:" + httpPort);

        // Idle watchdog — shuts down server after IDLE_TIMEOUT_MS of no API activity
        Thread watchdog = new Thread(() -> {
            while (running) {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { break; }
                long idle = System.currentTimeMillis() - lastActivityMs;
                if (idle > IDLE_TIMEOUT_MS) {
                    LOG.info("WebUIServer idle for " + (idle / 60_000) + " min — shutting down");
                    stop();
                }
            }
        }, "webui-idle-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        // Block until stop()
        try {
            synchronized (this) { while (running) wait(); }
        } catch (InterruptedException ignored) {}
    }

    /** Start on a background daemon thread. Returns immediately. */
    public void startAsync() {
        Thread t = new Thread(() -> {
            try { start(); } catch (IOException e) {
                LOG.log(Level.SEVERE, "WebUIServer start failed", e);
            }
        }, "webui-server");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        if (httpServer != null) httpServer.stop(0);
        synchronized (this) { notifyAll(); }
    }

    @Override public void close() { stop(); }

    public int getPort() { return httpPort; }

    // ── API handler (POST /api — JSON action dispatch) ────────

    private void handleApi(HttpExchange exchange) throws IOException {
        // CORS preflight
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        addCorsHeaders(exchange);
        lastActivityMs = System.currentTimeMillis();

        // Read request body
        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (body.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Empty request body\"}");
            return;
        }

        // Dispatch through localDispatch (handles scanLibrary + DesignerServer actions)
        String response = localDispatch(body);
        sendJson(exchange, 200, response);
    }

    private void addCorsHeaders(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // ── HTTP static file handler (JDK HttpServer) ───────────

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        // Resolve file — prevent directory traversal
        Path resolved = webRoot.resolve(path.substring(1)).normalize();
        if (!resolved.startsWith(webRoot) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
            byte[] body = "Not Found".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            return;
        }

        String fileName = resolved.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot) : "";
        String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

        byte[] body = Files.readAllBytes(resolved);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
    }

    // ── Local dispatch (scanLibrary + fallthrough to DesignerServer) ─

    String localDispatch(String jsonLine) {
        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
            String action = obj.has("action") ? obj.get("action").getAsString() : "";
            String callId = obj.has("_callId") ? obj.get("_callId").toString() : null;

            String response = switch (action) {
                case "scanLibrary" -> scanLibrary();
                case "scanBomProducts" -> scanBomProducts();
                case "loadBuildingDetail" -> loadBuildingDetail(
                        obj.has("dbFile") ? obj.get("dbFile").getAsString() : null);
                // ── BOM Drop with explicit DB path ──────────────────
                case "bomDrop" -> handleBomDrop(obj);
                // ── Bidirectional sync (Bonsai ↔ Web UI) ────────────
                case "selectionChanged" -> handleSelectionChanged(obj);
                case "applyScheme" -> handleApplyScheme(obj);
                case "pollCommands" -> handlePollCommands();
                case "getSelection" -> JsonProtocol.toJson(currentSelection);
                case "launchBonsai" -> handleLaunchBonsai();
                default -> designer.dispatch(jsonLine);
            };

            // ── Post-dispatch: detect compile completion → push to Bonsai ──
            if (("compile".equals(action) || "completeIt".equals(action)) && response != null) {
                afterCompile(action, response);
            }

            if (callId != null && response != null) {
                response = injectCallId(response, callId);
            }
            return response;
        } catch (Exception e) {
            return JsonProtocol.toJson(
                    com.bim.designer.protocol.StatusMessage.error(e.getMessage()));
        }
    }

    private static String injectCallId(String json, String callId) {
        if (json.startsWith("{")) return "{\"_callId\":" + callId + "," + json.substring(1);
        if (json.startsWith("[")) return "{\"_callId\":" + callId + ",\"data\":" + json + "}";
        return json;
    }

    private String scanLibrary() {
        List<Map<String, Object>> buildings = new ArrayList<>();
        try {
            File[] dbFiles = libDir.toFile().listFiles((dir, name) -> name.endsWith("_BOM.db"));
            if (dbFiles == null) dbFiles = new File[0];
            Arrays.sort(dbFiles);

            for (File dbFile : dbFiles) {
                String code = dbFile.getName().replace("_BOM.db", "");
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("code", code);
                entry.put("dbFile", dbFile.getAbsolutePath());
                entry.put("dbName", dbFile.getName());

                try (Connection conn = DriverManager.getConnection(
                        "jdbc:sqlite:" + dbFile.getAbsolutePath());
                     Statement stmt = conn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT ProjectName, Name, ExpectedElements FROM C_DocType WHERE IsActive=1 LIMIT 1")) {
                        if (rs.next()) {
                            entry.put("buildingId", rs.getString("ProjectName"));
                            entry.put("name", rs.getString("Name"));
                            entry.put("elementCount", rs.getInt("ExpectedElements"));
                        } else {
                            entry.put("buildingId", code);
                            entry.put("name", code);
                            entry.put("elementCount", 0);
                        }
                    }
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) AS cnt FROM m_bom WHERE is_active=1")) {
                        if (rs.next()) entry.put("bomCount", rs.getInt("cnt"));
                    } catch (Exception ignored) {}
                } catch (Exception e) {
                    entry.put("buildingId", code);
                    entry.put("name", code);
                    entry.put("elementCount", 0);
                }
                buildings.add(entry);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "scanLibrary failed", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildings", buildings);
        result.put("libraryDir", libDir.toString());
        result.put("count", buildings.size());
        return JsonProtocol.toJson(result);
    }

    private String loadBuildingDetail(String dbFilePath) {
        if (dbFilePath == null || dbFilePath.isBlank())
            return JsonProtocol.toJson(com.bim.designer.protocol.StatusMessage.error("Missing dbFile"));
        Path resolved = Path.of(dbFilePath).normalize();
        if (!resolved.startsWith(libDir.toAbsolutePath().normalize()))
            return JsonProtocol.toJson(com.bim.designer.protocol.StatusMessage.error("Access denied"));

        Map<String, Object> detail = new LinkedHashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
             Statement stmt = conn.createStatement()) {
            detail.put("dbFile", dbFilePath);
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT ProjectName, Name, DocSubType, ExpectedElements FROM C_DocType WHERE IsActive=1 LIMIT 1")) {
                if (rs.next()) {
                    detail.put("buildingId", rs.getString("ProjectName"));
                    detail.put("name", rs.getString("Name"));
                    detail.put("docSubType", rs.getString("DocSubType"));
                    detail.put("elementCount", rs.getInt("ExpectedElements"));
                }
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT bom_type, COUNT(*) AS cnt FROM m_bom WHERE is_active=1 GROUP BY bom_type")) {
                Map<String, Integer> bomStats = new LinkedHashMap<>();
                while (rs.next()) bomStats.put(rs.getString("bom_type"), rs.getInt("cnt"));
                detail.put("bomStats", bomStats);
            } catch (Exception ignored) {}
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM m_bom_line")) {
                if (rs.next()) detail.put("bomLineCount", rs.getInt("cnt"));
            } catch (Exception ignored) {}
            detail.put("success", true);
        } catch (Exception e) {
            detail.put("success", false);
            detail.put("error", e.getMessage());
        }
        return JsonProtocol.toJson(detail);
    }

    // ── SSE (Server-Sent Events) — push to browser ─────────────────────
    // Implementing BIM_Designer_SRS.md §29.5 — Bonsai ↔ Web UI sync

    private void handleSSE(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0); // chunked

        OutputStream os = exchange.getResponseBody();
        sseClients.add(os);
        LOG.info("SSE client connected (" + sseClients.size() + " total)");

        // Send initial selection state
        if (!currentSelection.isEmpty()) {
            sseWrite(os, "selectionChanged", JsonProtocol.toJson(currentSelection));
        }

        // Keep alive — block this thread until client disconnects
        try {
            while (running) {
                Thread.sleep(15000);
                sseWrite(os, "ping", "{\"ts\":" + System.currentTimeMillis() + "}");
            }
        } catch (InterruptedException | IOException ignored) {
        } finally {
            sseClients.remove(os);
            LOG.info("SSE client disconnected (" + sseClients.size() + " remaining)");
        }
    }

    private void sseWrite(OutputStream os, String event, String data) throws IOException {
        String msg = "event: " + event + "\ndata: " + data + "\n\n";
        synchronized (os) {
            os.write(msg.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    private void sseBroadcast(String event, String data) {
        for (OutputStream os : sseClients) {
            try {
                sseWrite(os, event, data);
            } catch (IOException e) {
                sseClients.remove(os);
            }
        }
    }

    // ── Bonsai ↔ Web UI sync actions ──────────────────────────────────

    /**
     * Bonsai sends this when viewport selection changes.
     * Stores current selection and SSE-broadcasts to all browser clients.
     */
    private String handleSelectionChanged(JsonObject obj) {
        Map<String, String> sel = new LinkedHashMap<>();
        for (String key : new String[]{"objectName", "ifcClass", "guid", "discipline",
                                        "material", "productId", "hostType"}) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                sel.put(key, obj.get(key).getAsString());
            }
        }
        currentSelection = Map.copyOf(sel);
        String json = JsonProtocol.toJson(sel);
        sseBroadcast("selectionChanged", json);
        LOG.fine("Selection changed: " + json);
        return "{\"success\":true,\"event\":\"selectionChanged\"}";
    }

    /**
     * Web UI sends this to apply a color scheme to the selected object(s) in Bonsai.
     * Queues a command that Bonsai picks up via pollCommands.
     */
    private String handleApplyScheme(JsonObject obj) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("command", "applyScheme");
        if (obj.has("schemeName")) cmd.put("schemeName", obj.get("schemeName").getAsString());
        if (obj.has("color")) cmd.put("color", obj.get("color").toString());
        if (obj.has("guid")) cmd.put("guid", obj.get("guid").getAsString());
        if (obj.has("objectName")) cmd.put("objectName", obj.get("objectName").getAsString());
        if (obj.has("filterDiscipline")) cmd.put("filterDiscipline", obj.get("filterDiscipline").getAsString());
        if (obj.has("filterIfcType")) cmd.put("filterIfcType", obj.get("filterIfcType").getAsString());
        pendingCommands.add(cmd);
        // Also broadcast to any other browser tabs
        sseBroadcast("schemeApplied", JsonProtocol.toJson(cmd));
        return "{\"success\":true,\"queued\":true}";
    }

    /**
     * Bonsai polls this to get pending commands (color changes, etc.).
     * Returns and clears the queue.
     */
    private String handlePollCommands() {
        List<Map<String, Object>> commands = new ArrayList<>();
        Map<String, Object> cmd;
        while ((cmd = pendingCommands.poll()) != null) {
            commands.add(cmd);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commands", commands);
        result.put("count", commands.size());
        return JsonProtocol.toJson(result);
    }

    // ── BOM Drop with explicit BOM DB path ──────────────────────────────
    // WebUI sends bomDbPath from scanBomProducts result → open that DB for the drop

    private String handleBomDrop(JsonObject obj) {
        String productId = obj.has("buildingProductId") ? obj.get("buildingProductId").getAsString() : "";
        String bomDbPath = obj.has("bomDbPath") ? obj.get("bomDbPath").getAsString() : "";

        if (productId.isBlank()) {
            return "{\"success\":false,\"error\":\"Missing buildingProductId\"}";
        }

        // If bomDbPath provided, open that specific BOM DB and do the drop
        if (!bomDbPath.isBlank()) {
            java.nio.file.Path resolved = java.nio.file.Path.of(bomDbPath).normalize();
            if (!java.nio.file.Files.exists(resolved)) {
                return "{\"success\":false,\"error\":\"BOM DB not found: " + bomDbPath + "\"}";
            }
            try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + resolved)) {
                DesignerAPIImpl tempApi = new DesignerAPIImpl(bomConn);
                var resp = tempApi.bomDrop(productId);
                return com.bim.designer.protocol.JsonProtocol.toJson(resp);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "bomDrop with DB path failed", e);
                return "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
            }
        }

        // Fallback: dispatch through DesignerServer (uses constructor bomConn)
        return designer.dispatch("{\"action\":\"bomDrop\",\"buildingProductId\":\"" + productId + "\"}");
    }

    // ── Launch Bonsai from Web UI ───────────────────────────────────────

    private String handleLaunchBonsai() {
        try {
            // Launch via user's shell to inherit PATH and environment
            // ~/bin/bonsai wraps blender with addon config
            String home = System.getProperty("user.home");
            ProcessBuilder pb = new ProcessBuilder("bash", "-lc", home + "/bin/bonsai");
            pb.redirectErrorStream(true);
            pb.start(); // fire and forget — don't wait
            return "{\"success\":true,\"message\":\"Bonsai launched\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    // ── Post-compile: notify browser only ───────────────────────────────
    // loadOutput must come from user action (Show in Bonsai button or Bonsai
    // Full Load panel), never auto-queued by the server. This prevents
    // accidental Bonsai viewport loads during backend testing.

    private void afterCompile(String action, String response) {
        try {
            JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
            boolean success = resp.has("success") && resp.get("success").getAsBoolean();
            if (!success) return;

            String outputDbPath = resp.has("outputDbPath") ? resp.get("outputDbPath").getAsString() : null;
            int elementCount = resp.has("elementCount") ? resp.get("elementCount").getAsInt() : 0;

            // SSE broadcast compileComplete to browser (notification only, no loadOutput)
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "compileComplete");
            event.put("outputDbPath", outputDbPath);
            event.put("elementCount", elementCount);
            sseBroadcast("compileComplete", JsonProtocol.toJson(event));
        } catch (Exception e) {
            LOG.log(Level.FINE, "afterCompile parse error (non-fatal)", e);
        }
    }

    // ── scanBomProducts — BUILDING-level BOM headers from each *_BOM.db ──
    // Implementing BIM_Designer_SRS.md §29.5 — new wire action for 1D Order Configurator

    private String scanBomProducts() {
        List<Map<String, Object>> products = new ArrayList<>();
        try {
            File[] dbFiles = libDir.toFile().listFiles((dir, name) -> name.endsWith("_BOM.db"));
            if (dbFiles == null) dbFiles = new File[0];
            Arrays.sort(dbFiles);

            for (File dbFile : dbFiles) {
                String code = dbFile.getName().replace("_BOM.db", "");
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:sqlite:" + dbFile.getAbsolutePath());
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT Value AS bom_id, bom_name, m_product_category_id, " +
                             "aabb_width_mm, aabb_depth_mm, aabb_height_mm, " +
                             "(SELECT COUNT(*) FROM m_bom_line bl WHERE bl.M_BOM_ID = b.M_BOM_ID) AS element_count " +
                             "FROM m_bom b WHERE b.bom_type = 'BUILDING' AND b.is_active = 1")) {
                    while (rs.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("bomId", rs.getString("bom_id"));
                        entry.put("name", rs.getString("bom_name"));
                        entry.put("category", rs.getString("m_product_category_id"));
                        entry.put("source", code);
                        entry.put("dbFile", dbFile.getAbsolutePath());
                        entry.put("elementCount", rs.getInt("element_count"));
                        entry.put("widthMm", rs.getDouble("aabb_width_mm"));
                        entry.put("depthMm", rs.getDouble("aabb_depth_mm"));
                        entry.put("heightMm", rs.getDouble("aabb_height_mm"));
                        products.add(entry);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "scanBomProducts skip " + code, e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "scanBomProducts failed", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", products);
        result.put("count", products.size());
        return JsonProtocol.toJson(result);
    }

    // ── Main ────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String libDir = args.length > 0 ? args[0] : "library";
        int httpPort = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        Path webRoot = Path.of(args.length > 2 ? args[2] : "webui");

        Path libPath = Path.of(libDir);
        Path dbFile = libPath.resolve("component_library.db");
        if (!Files.exists(dbFile)) {
            System.err.println("ERROR: Database not found: " + dbFile);
            System.exit(1);
        }
        if (!Files.isDirectory(webRoot)) {
            System.err.println("ERROR: Web root not found: " + webRoot);
            System.exit(1);
        }

        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        DesignerAPIImpl api = new DesignerAPIImpl(conn);
        DesignerServer designer = new DesignerServer(api, 0);

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        BIM Designer — Web UI             ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  URL:      http://localhost:" + httpPort + "          ║");
        System.out.println("║  API:      POST /api (JSON)              ║");
        System.out.println("║  Library:  " + padRight(libDir, 29) + "║");
        System.out.println("║  Bonsai:   optional (viewport only)      ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  Press Ctrl+C to stop                    ║");
        System.out.println("╚══════════════════════════════════════════╝");

        try (WebUIServer server = new WebUIServer(designer, webRoot, libPath, httpPort)) {
            server.start();
        }
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}
