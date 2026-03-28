package com.bim.backoffice.server;

import com.bim.backoffice.dao.*;
import com.bim.backoffice.federation.ColorSchemeEngine;
import com.bim.backoffice.federation.DimensionQuery;
import com.bim.backoffice.federation.WorkPackageSelector;
import com.bim.backoffice.report.*;
import com.bim.orm.BIMLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.Executors;

/**
 * Back-Office HTTP server — serves portfolio/reports/Kanban/BSC as JSON.
 *
 * <p>Separate from BonsaiBIMDesigner's TCP bridge (port 9876).
 * This is the ERP-style multi-project server that back-office clients
 * (web dashboard, CLI, Excel connector) query.
 *
 * <p>Endpoints:
 * <pre>
 *   POST /api/login              — create session (userId, displayName)
 *   GET  /api/sessions           — who's online
 *   GET  /api/portfolio          — all projects analysis table
 *   GET  /api/kanban             — Kanban board cards
 *   GET  /api/bsc                — Balanced scorecard (4 perspectives)
 *   GET  /api/cost?id=SH         — 5D cost breakdown for one project
 *   GET  /api/schedule?id=SH     — 4D schedule for one project
 *   GET  /api/carbon?id=SH       — 6D carbon footprint for one project
 *   GET  /api/maintenance?id=SH  — 7D maintenance schedule for one project
 *   GET  /api/health             — health check
 * </pre>
 *
 * <p>Session header: {@code X-Session-Token: <token from /api/login>}
 *
 * <p>No framework dependency — uses JDK {@code com.sun.net.httpserver}.
 *
 * // Implementing BACK_OFFICE_SRS.md §0, §3 — Witness: W-BO-SERVER-1
 */
public class BackOfficeServer implements AutoCloseable {

    private static final String TAG = "BackOfficeServer";
    private static final int DEFAULT_PORT = 9877;
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private final HttpServer server;
    private final String libraryDir;
    private final Connection compLibConn;
    private final SessionManager sessionMgr;
    private final com.bim.backoffice.federation.VisualizationManager vizManager =
            new com.bim.backoffice.federation.VisualizationManager();

    public BackOfficeServer(String libraryDir, int port) throws Exception {
        this.libraryDir = libraryDir;
        this.compLibConn = DriverManager.getConnection(
                "jdbc:sqlite:" + libraryDir + "/ERP.db");
        String secret = System.getenv("BIM_SESSION_SECRET");
        this.sessionMgr = (secret != null && !secret.isBlank())
                ? new SessionManager(secret)
                : new SessionManager();

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // Register endpoints
        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/portfolio", this::handlePortfolio);
        server.createContext("/api/kanban", this::handleKanban);
        server.createContext("/api/bsc", this::handleBSC);
        server.createContext("/api/cost", this::handleCost);
        server.createContext("/api/schedule", this::handleSchedule);
        server.createContext("/api/carbon", this::handleCarbon);
        server.createContext("/api/maintenance", this::handleMaintenance);
        server.createContext("/api/health", this::handleHealth);
        // Federation port — 4D-7D color + reports (prompt 77)
        server.createContext("/api/colorscheme", this::handleColorScheme);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/query", this::handleQuery);
        server.createContext("/api/workpackage", this::handleWorkPackage);
        server.createContext("/api/palette", this::handlePalette);
        server.createContext("/api/nlp", this::handleNlp);
        server.createContext("/api/labels", this::handleLabels);
        server.createContext("/api/visualization", this::handleVisualization);
    }

    public BackOfficeServer(String libraryDir) throws Exception {
        this(libraryDir, DEFAULT_PORT);
    }

    public void start() {
        server.start();
        BIMLogger.info(TAG, "BackOfficeServer listening on port {}",
                server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
        sessionMgr.closeAll();
        try { compLibConn.close(); } catch (Exception e) { BIMLogger.warn(TAG, "Connection close: {}", e.getMessage()); }
    }

    public SessionManager getSessionManager() {
        return sessionMgr;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    // ── Auth helper ──────────────────────────────────────────────────

    /** Returns the session for the token in X-Session-Token, or sends 401 and returns null. */
    private SessionManager.Session requireSession(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Session-Token");
        if (token == null || token.isBlank()) {
            sendJson(ex, 401, java.util.Map.of("error", "Authentication required"));
            return null;
        }
        SessionManager.Session session = sessionMgr.getSession(token);
        if (session == null) {
            sendJson(ex, 401, java.util.Map.of("error", "Authentication required"));
            return null;
        }
        return session;
    }

    // ── Endpoints ───────────────────────────────────────────────────

    private void handleLogin(HttpExchange ex) throws IOException {
        try {
            String body = new String(ex.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            var json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            String userId = json.has("userId") ? json.get("userId").getAsString() : "anonymous";
            String displayName = json.has("displayName")
                    ? json.get("displayName").getAsString() : userId;

            String token = sessionMgr.createSession(userId, displayName);

            sendJson(ex, 200, java.util.Map.of(
                    "token", token,
                    "userId", userId,
                    "displayName", displayName));
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleSessions(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        try {
            var active = sessionMgr.activeSessions();
            var list = active.stream()
                    .map(s -> java.util.Map.of(
                            "userId", s.userId(),
                            "displayName", s.displayName(),
                            "activeBuilding", s.activeBuildingId() != null ? s.activeBuildingId() : "",
                            "lastAccess", s.lastAccess().toString()))
                    .toList();
            sendJson(ex, 200, java.util.Map.of(
                    "activeSessions", list,
                    "count", active.size()));
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handlePortfolio(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        try {
            PortfolioDAO dao = new PortfolioDAO();
            PortfolioDAO.PortfolioSummary summary =
                    dao.analysePortfolio(libraryDir, compLibConn);
            sendJson(ex, 200, summary);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleKanban(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        try {
            PortfolioDAO dao = new PortfolioDAO();
            var cards = dao.kanbanBoard(libraryDir, compLibConn);
            sendJson(ex, 200, cards);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleBSC(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        try {
            PortfolioDAO dao = new PortfolioDAO();
            var bsc = dao.balancedScorecard(libraryDir, compLibConn);
            sendJson(ex, 200, bsc);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleCost(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        try (Connection bomConn = openBom(buildingId)) {
            CostDAO dao = new CostDAO();
            var summary = dao.costBreakdown(bomConn, compLibConn, buildingId);
            sendJson(ex, 200, summary);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleSchedule(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        String startDate = queryParam(ex, "start");
        if (startDate == null) startDate = "2026-04-01";
        try (Connection bomConn = openBom(buildingId)) {
            ScheduleDAO dao = new ScheduleDAO();
            var summary = dao.constructionSchedule(bomConn, compLibConn,
                    buildingId, startDate);
            sendJson(ex, 200, summary);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleCarbon(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        try (Connection bomConn = openBom(buildingId)) {
            SustainabilityDAO dao = new SustainabilityDAO();
            var summary = dao.carbonFootprint(bomConn, compLibConn, buildingId);
            sendJson(ex, 200, summary);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleMaintenance(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        try (Connection bomConn = openBom(buildingId)) {
            FacilityMgmtDAO dao = new FacilityMgmtDAO();
            var summary = dao.maintenanceSchedule(bomConn, compLibConn, buildingId);
            sendJson(ex, 200, summary);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        sendJson(ex, 200, java.util.Map.of(
                "status", "UP",
                "service", "BIMBackOffice",
                "port", getPort(),
                "libraryDir", libraryDir));
    }

    // ── Federation endpoints (prompt 77) ───────────────────────────

    /** GET /api/colorscheme?id=SH&scheme=discipline|4D-phase|5D-cost|6D-carbon */
    private void handleColorScheme(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        String scheme = queryParam(ex, "scheme");
        if (scheme == null) scheme = "discipline";
        String startDate = queryParam(ex, "start");
        if (startDate == null) startDate = "2026-04-01";
        try (Connection bomConn = openBom(buildingId)) {
            ColorSchemeEngine engine = new ColorSchemeEngine();
            ColorSchemeEngine.ColorMap result = switch (scheme) {
                case "4D-phase" -> engine.colorByPhase(bomConn, compLibConn, buildingId, startDate);
                case "5D-cost"  -> engine.colorByCost(bomConn, compLibConn, buildingId);
                case "6D-carbon" -> engine.colorByCarbon(bomConn, compLibConn, buildingId);
                default -> engine.colorByDiscipline(bomConn, buildingId);
            };
            sendJson(ex, 200, result);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    /** GET /api/report?id=SH&type=bom|cost|schedule|compliance */
    private void handleReport(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        String type = queryParam(ex, "type");
        if (type == null) type = "bom";
        String startDate = queryParam(ex, "start");
        if (startDate == null) startDate = "2026-04-01";
        try (Connection bomConn = openBom(buildingId)) {
            Object result = switch (type) {
                case "cost" -> new CostSummaryReport().generate(bomConn, compLibConn, buildingId);
                case "schedule" -> new ScheduleReport().generate(bomConn, compLibConn, buildingId, startDate);
                case "compliance" -> new ComplianceReport().generate(compLibConn, bomConn, buildingId);
                default -> new BomScheduleReport().generate(bomConn, compLibConn, buildingId);
            };
            sendJson(ex, 200, result);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    /** GET /api/query?id=SH&dim=discipline&filter=ARC */
    private void handleQuery(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        String dim = queryParam(ex, "dim");
        if (dim == null) dim = "discipline";
        String filter = queryParam(ex, "filter");
        if (filter == null) { sendError(ex, 400, "Missing ?filter="); return; }
        try (Connection bomConn = openBom(buildingId)) {
            DimensionQuery dq = new DimensionQuery();
            DimensionQuery.QueryResult result = switch (dim) {
                case "phase" -> dq.byPhase(bomConn, compLibConn, filter);
                case "floor" -> dq.byFloor(bomConn, filter);
                case "cost-above" -> dq.byCostAbove(bomConn, compLibConn, buildingId, Double.parseDouble(filter));
                case "carbon-above" -> dq.byCarbonAbove(bomConn, compLibConn, buildingId, Double.parseDouble(filter));
                default -> dq.byDiscipline(bomConn, filter);
            };
            sendJson(ex, 200, result);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    /** GET /api/workpackage?id=SH&bom=FLOOR_GF */
    private void handleWorkPackage(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        String bomId = queryParam(ex, "bom");
        if (bomId == null) { sendError(ex, 400, "Missing ?bom="); return; }
        try (Connection bomConn = openBom(buildingId)) {
            WorkPackageSelector sel = new WorkPackageSelector();
            var result = sel.selectPackage(bomConn, compLibConn, bomId);
            sendJson(ex, 200, result);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    /** GET /api/palette?type=discipline|phase — returns color legend. */
    private void handlePalette(HttpExchange ex) throws IOException {
        String type = queryParam(ex, "type");
        if (type == null) type = "discipline";
        var colors = switch (type) {
            case "phase" -> ColorSchemeEngine.getPhaseColors();
            default -> ColorSchemeEngine.getDisciplineColors();
        };
        sendJson(ex, 200, colors);
    }

    /** GET /api/nlp?id=SH&q=how+many+beams — NLP query against BOM. */
    private void handleNlp(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String buildingId = queryParam(ex, "id");
        if (buildingId == null) { sendError(ex, 400, "Missing ?id="); return; }
        String q = queryParam(ex, "q");
        if (q == null || q.isBlank()) { sendError(ex, 400, "Missing ?q="); return; }
        try (Connection bomConn = openBom(buildingId)) {
            var executor = new com.bim.backoffice.federation.NlpQueryExecutor();
            var result = executor.query(bomConn, java.net.URLDecoder.decode(q, java.nio.charset.StandardCharsets.UTF_8));
            sendJson(ex, 200, result);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    /** GET /api/labels?type=ifc|discipline — IFC/discipline label maps. */
    private void handleLabels(HttpExchange ex) throws IOException {
        String type = queryParam(ex, "type");
        if ("discipline".equals(type)) {
            sendJson(ex, 200, com.bim.backoffice.federation.IfcLabelMapper.allDisciplineLabels());
        } else {
            sendJson(ex, 200, com.bim.backoffice.federation.IfcLabelMapper.allIfcLabels());
        }
    }

    /** POST /api/visualization?mode=BBOXES|SEMANTICS|MATERIALS — switch viz mode. */
    private void handleVisualization(HttpExchange ex) throws IOException {
        if (requireSession(ex) == null) return;
        String mode = queryParam(ex, "mode");
        if (mode == null) {
            // GET — return current state
            sendJson(ex, 200, vizManager.state());
            return;
        }
        try {
            var vizMode = com.bim.backoffice.federation.VisualizationMode.valueOf(mode.toUpperCase());
            var result = vizManager.switchMode(vizMode);
            sendJson(ex, 200, result);
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, "Invalid mode. Use BBOXES, SEMANTICS, or MATERIALS");
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    // ── CORS + WAN helpers ──────────────────────────────────────────

    private void addCorsHeaders(HttpExchange ex) {
        var headers = ex.getResponseHeaders();
        String corsOrigin = System.getenv("BIM_CORS_ORIGIN");
        headers.set("Access-Control-Allow-Origin", (corsOrigin != null && !corsOrigin.isBlank()) ? corsOrigin : "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Session-Token");
        headers.set("Access-Control-Max-Age", "86400");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Connection openBom(String buildingId) throws Exception {
        Path safePath = Paths.get(libraryDir).resolve(buildingId.toUpperCase() + "_BOM.db").normalize();
        if (!safePath.startsWith(Paths.get(libraryDir).normalize())) {
            throw new SecurityException("Invalid building ID");
        }
        String dbPath = safePath.toString();
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private String queryParam(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    private void sendJson(HttpExchange ex, int code, Object body) throws IOException {
        // Handle CORS preflight (OPTIONS) for WAN/browser access
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            addCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            return;
        }
        byte[] bytes = GSON.toJson(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        addCorsHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, int code, String msg) throws IOException {
        sendJson(ex, code, java.util.Map.of("error", msg));
    }

    private void sendError(HttpExchange ex, Exception e) throws IOException {
        BIMLogger.warn(TAG, "Request failed: {}", e.getMessage());
        sendJson(ex, 500, java.util.Map.of("error", "Internal server error"));
    }

    // ── Main ────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String libDir = args.length > 0 ? args[0] : "library";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        try (BackOfficeServer server = new BackOfficeServer(libDir, port)) {
            server.start();
            System.out.println("BackOfficeServer running on http://localhost:" + port);
            System.out.println("Endpoints:");
            System.out.println("  GET /api/portfolio");
            System.out.println("  GET /api/kanban");
            System.out.println("  GET /api/bsc");
            System.out.println("  GET /api/cost?id=SH");
            System.out.println("  GET /api/schedule?id=SH&start=2026-04-01");
            System.out.println("  GET /api/carbon?id=SH");
            System.out.println("  GET /api/maintenance?id=SH");
            System.out.println("  GET /api/health");
            System.out.println("  ── Federation (prompt 77) ──");
            System.out.println("  GET /api/colorscheme?id=SH&scheme=discipline|4D-phase|5D-cost|6D-carbon");
            System.out.println("  GET /api/report?id=SH&type=bom|cost|schedule|compliance");
            System.out.println("  GET /api/query?id=SH&dim=discipline&filter=ARC");
            System.out.println("  GET /api/workpackage?id=SH&bom=FLOOR_GF");
            System.out.println("  GET /api/palette?type=discipline|phase");
            System.out.println("  GET /api/nlp?id=SH&q=how+many+beams");
            System.out.println("  GET /api/labels?type=ifc|discipline");
            System.out.println("  GET /api/visualization?mode=BBOXES|SEMANTICS|MATERIALS");
            System.out.println("Press Ctrl+C to stop.");

            // Block until interrupted
            Thread.currentThread().join();
        }
    }
}
