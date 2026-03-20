package com.bim.backoffice.server;

import com.bim.backoffice.dao.*;
import com.bim.orm.BIMLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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

    public BackOfficeServer(String libraryDir, int port) throws Exception {
        this.libraryDir = libraryDir;
        this.compLibConn = DriverManager.getConnection(
                "jdbc:sqlite:" + libraryDir + "/component_library.db");
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
        try { compLibConn.close(); } catch (Exception ignored) {}
    }

    public SessionManager getSessionManager() {
        return sessionMgr;
    }

    public int getPort() {
        return server.getAddress().getPort();
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
        try {
            PortfolioDAO dao = new PortfolioDAO();
            var cards = dao.kanbanBoard(libraryDir, compLibConn);
            sendJson(ex, 200, cards);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleBSC(HttpExchange ex) throws IOException {
        try {
            PortfolioDAO dao = new PortfolioDAO();
            var bsc = dao.balancedScorecard(libraryDir, compLibConn);
            sendJson(ex, 200, bsc);
        } catch (Exception e) {
            sendError(ex, e);
        }
    }

    private void handleCost(HttpExchange ex) throws IOException {
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

    // ── CORS + WAN helpers ──────────────────────────────────────────

    private void addCorsHeaders(HttpExchange ex) {
        var headers = ex.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Session-Token");
        headers.set("Access-Control-Max-Age", "86400");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Connection openBom(String buildingId) throws Exception {
        String dbPath = libraryDir + "/" + buildingId.toUpperCase() + "_BOM.db";
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
        sendJson(ex, 500, java.util.Map.of("error", e.getMessage()));
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
            System.out.println("Press Ctrl+C to stop.");

            // Block until interrupted
            Thread.currentThread().join();
        }
    }
}
