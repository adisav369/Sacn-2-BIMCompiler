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

    @BeforeAll
    static void setUp() throws Exception {
        server = new BackOfficeServer("library", 0);  // port 0 = random free port
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.close();
    }

    private JsonObject get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
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

        // user1 should see user2 editing SH
        var others = mgr.whoIsEditing("SH", t1);
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
}
