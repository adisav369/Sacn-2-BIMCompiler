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

    private HttpServer httpServer;
    private volatile boolean running;

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
        httpServer.createContext("/", this::handleStatic);
        httpServer.start();
        running = true;
        LOG.info("WebUIServer listening on http://localhost:" + httpPort);

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
                case "loadBuildingDetail" -> loadBuildingDetail(
                        obj.has("dbFile") ? obj.get("dbFile").getAsString() : null);
                default -> designer.dispatch(jsonLine);
            };

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
