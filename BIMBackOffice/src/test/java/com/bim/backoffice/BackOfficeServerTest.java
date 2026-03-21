package com.bim.backoffice;

import com.bim.backoffice.server.BackOfficeServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackOfficeServer integration test — starts HTTP server, hits endpoints.
 *
 * // Implementing BACK_OFFICE_SRS.md §0 — Witness: W-BO-SERVER-1..6
 */
@DisplayName("BackOfficeServer — HTTP API")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackOfficeServerTest {

    private static BackOfficeServer server;
    private static HttpClient client;
    private static String baseUrl;
    private static String authToken;

    @BeforeAll
    static void setUp() throws Exception {
        server = new BackOfficeServer("library", 0);  // port 0 = random free port
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
        // Obtain auth token for authenticated endpoints
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"userId\":\"test\",\"displayName\":\"Test User\"}"))
                .build();
        HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        authToken = JsonParser.parseString(loginResp.body()).getAsJsonObject()
                .get("token").getAsString();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.close();
    }

    private JsonObject get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-Session-Token", authToken)
                .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "GET " + path + " must return 200");
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    @Test @Order(1)
    @DisplayName("W-BO-SERVER-1: /api/health returns UP")
    void healthCheck() throws Exception {
        JsonObject json = get("/api/health");
        assertEquals("UP", json.get("status").getAsString());
        assertEquals("BIMBackOffice", json.get("service").getAsString());
    }

    @Test @Order(2)
    @DisplayName("W-BO-SERVER-2: /api/portfolio returns projects with cost data")
    void portfolio() throws Exception {
        JsonObject json = get("/api/portfolio");
        assertTrue(json.get("totalProjects").getAsInt() >= 6,
                "Must find ≥6 projects");
        assertTrue(json.get("totalCostRm").getAsDouble() > 0,
                "Total cost must be > 0");
        assertTrue(json.getAsJsonArray("projects").size() >= 6,
                "Projects array must have ≥6 entries");
    }

    @Test @Order(3)
    @DisplayName("W-BO-SERVER-3: /api/kanban returns cards with status columns")
    void kanban() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/kanban"))
                .header("X-Session-Token", authToken)
                .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        var arr = JsonParser.parseString(resp.body()).getAsJsonArray();
        assertTrue(arr.size() >= 6, "Kanban must have ≥6 cards");

        // Each card must have status
        for (var el : arr) {
            String status = el.getAsJsonObject().get("status").getAsString();
            assertTrue(java.util.List.of("Backlog", "In Progress", "Review", "Complete")
                    .contains(status), "Invalid status: " + status);
        }
    }

    @Test @Order(4)
    @DisplayName("W-BO-SERVER-4: /api/bsc returns 4 perspectives")
    void balancedScorecard() throws Exception {
        JsonObject json = get("/api/bsc");
        assertTrue(json.getAsJsonArray("financial").size() >= 3);
        assertTrue(json.getAsJsonArray("client").size() >= 3);
        assertTrue(json.getAsJsonArray("process").size() >= 3);
        assertTrue(json.getAsJsonArray("learning").size() >= 3);
    }

    @Test @Order(5)
    @DisplayName("W-BO-SERVER-5: /api/cost?id=SH returns 3-component cost")
    void costPerProject() throws Exception {
        JsonObject json = get("/api/cost?id=SH");
        assertTrue(json.get("grandTotal").getAsDouble() > 0, "SH must have cost > 0");
        assertTrue(json.get("materialTotal").getAsDouble() > 0);
        assertTrue(json.get("laborTotal").getAsDouble() > 0);
        assertFalse(json.getAsJsonArray("lines").isEmpty(), "Must have cost lines");
    }

    @Test @Order(6)
    @DisplayName("W-BO-SERVER-6: /api/schedule?id=SH returns Gantt tasks")
    void schedulePerProject() throws Exception {
        JsonObject json = get("/api/schedule?id=SH");
        assertTrue(json.get("totalTasks").getAsInt() > 0, "SH must have schedule tasks");
        assertTrue(json.get("totalDays").getAsInt() > 0, "Duration must be > 0");
        assertNotNull(json.get("projectStartDate").getAsString());
        assertNotNull(json.get("projectFinishDate").getAsString());
    }

    // ── Session management ──────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("W-BO-SESSION-1: POST /api/login creates session with token")
    void loginCreatesSession() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"userId\":\"alice\",\"displayName\":\"Alice Tan\"}"))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertNotNull(json.get("token").getAsString(), "Must return session token");
        assertEquals("alice", json.get("userId").getAsString());
        assertEquals("Alice Tan", json.get("displayName").getAsString());
    }

    @Test @Order(11)
    @DisplayName("W-BO-SESSION-2: /api/sessions shows active users")
    void sessionsShowsActiveUsers() throws Exception {
        // Create two sessions
        for (String user : new String[]{"bob", "carol"}) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/login"))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"userId\":\"" + user + "\"}"))
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        }

        JsonObject json = get("/api/sessions");
        assertTrue(json.get("count").getAsInt() >= 2,
                "Must have ≥2 active sessions");
    }

    @Test @Order(12)
    @DisplayName("W-BO-SESSION-3: SessionManager detects concurrent editing")
    void concurrentEditDetection() {
        var mgr = server.getSessionManager();
        String t1 = mgr.createSession("user1", "User 1");
        String t2 = mgr.createSession("user2", "User 2");

        mgr.setActiveBuilding(t1, "SH");
        mgr.setActiveBuilding(t2, "SH");

        // Extract sessionId from signed token for exclusion
        String t1SessionId = t1.substring(0, t1.lastIndexOf('.'));

        // user1 should see user2 editing SH
        var others = mgr.whoIsEditing("SH", t1SessionId);
        assertEquals(1, others.size(), "Must detect 1 other user editing SH");
        assertEquals("user2", others.get(0).userId());
    }

    @Test @Order(13)
    @DisplayName("W-BO-SESSION-4: per-DB write lock serializes writers")
    void writeLocksPerDatabase() {
        var mgr = server.getSessionManager();
        var lock1 = mgr.getWriteLock("SH_BOM.db");
        var lock2 = mgr.getWriteLock("SH_BOM.db");
        var lock3 = mgr.getWriteLock("TE_BOM.db");

        assertSame(lock1, lock2, "Same DB file must return same lock");
        assertNotSame(lock1, lock3, "Different DB files must return different locks");
    }

    // ── WAN security (BO-2b) ─────────────────────────────────────────

    @Test @Order(20)
    @DisplayName("W-BO-WAN-1: HMAC-signed token accepted by getSession()")
    void signedTokenAccepted() {
        var mgr = server.getSessionManager();
        String token = mgr.createSession("wan_user", "WAN User");
        // Token must contain a dot (sessionId.signature)
        assertTrue(token.contains("."), "Token must be signed (contain '.')");
        var session = mgr.getSession(token);
        assertNotNull(session, "Signed token must resolve to session");
        assertEquals("wan_user", session.userId());
    }

    @Test @Order(21)
    @DisplayName("W-BO-WAN-2: forged token (wrong signature) rejected")
    void forgedTokenRejected() {
        var mgr = server.getSessionManager();
        String token = mgr.createSession("legit_user", "Legit");
        // Tamper with the signature
        String forged = token.substring(0, token.lastIndexOf('.')) + ".FORGED_SIG";
        var session = mgr.getSession(forged);
        assertNull(session, "Forged token must be rejected");
    }

    @Test @Order(22)
    @DisplayName("W-BO-WAN-3: unsigned UUID token accepted (backward compat)")
    void unsignedTokenBackwardCompat() {
        var mgr = server.getSessionManager();
        // Directly create a session with raw sessionId (simulates legacy token)
        String rawToken = mgr.createSession("legacy_user", "Legacy");
        // Extract the sessionId part (before the dot)
        String sessionId = rawToken.substring(0, rawToken.lastIndexOf('.'));
        // verifyToken should accept a plain UUID (no dot) as backward compat
        var session = mgr.getSession(sessionId);
        assertNotNull(session, "Unsigned UUID must be accepted for backward compat");
    }

    @Test @Order(23)
    @DisplayName("W-BO-WAN-4: unauthenticated request returns 401")
    void unauthenticatedReturns401() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/portfolio"))
                .GET().build();  // no X-Session-Token header
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode(), "Must return 401 without token");
    }

    @Test @Order(24)
    @DisplayName("W-BO-WAN-5: CORS preflight (OPTIONS) returns 204")
    void corsPreflight() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/health"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // OPTIONS should return 200 or 204 with CORS headers
        assertTrue(resp.statusCode() <= 204,
                "OPTIONS must return 200 or 204, got " + resp.statusCode());
        String allowOrigin = resp.headers().firstValue("Access-Control-Allow-Origin").orElse("");
        assertEquals("*", allowOrigin, "Must have CORS Allow-Origin header");
    }
}
