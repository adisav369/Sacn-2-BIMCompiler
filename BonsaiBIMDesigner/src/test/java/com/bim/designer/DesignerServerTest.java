package com.bim.designer;

import com.bim.designer.api.*;
import com.bim.designer.dao.DesignerDAO;
import com.bim.designer.dao.StubDataSeeder;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test: StubDataSeeder → DesignerDAO → DesignerAPIImpl → DesignerServer → TCP.
 *
 * <p>Proves the full protocol stack works with POC data. No real pipeline,
 * no real BOM.db, no filesystem dependencies. Pure in-memory.
 *
 * <p>Test structure follows the project convention:
 * <ul>
 *   <li>W-DS-* = witness claims for the Designer Server</li>
 *   <li>Each test is a self-contained proof of one capability</li>
 * </ul>
 */
@DisplayName("DesignerServer — End-to-End POC")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesignerServerTest {

    private static Connection conn;
    private static DesignerAPIImpl api;
    private static DesignerServer server;
    private static final int TEST_PORT = 19876;  // Avoid collision with default 9876

    @BeforeAll
    static void setUp() throws Exception {
        // In-memory SQLite — no filesystem dependency
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(conn);

        api = new DesignerAPIImpl(conn);
        server = new DesignerServer(api, TEST_PORT);
        server.startAsync();
        Thread.sleep(200);  // Let server bind
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (server != null) server.close();
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── DAO layer proofs ────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-DS-1: DAO lists 3 Rosetta Stone buildings from stub data")
    void w_ds_1_dao_lists_buildings() throws Exception {
        DesignerDAO dao = new DesignerDAO(conn);
        var types = dao.listBuildingTypes();
        assertEquals(3, types.size(), "Expected SH, DX, TE");
        assertEquals("Ifc4_SampleHouse", types.get(0).projectName());
        assertEquals("Duplex_A_01", types.get(1).projectName());
        assertEquals("Terminal_KLIA", types.get(2).projectName());
    }

    @Test
    @Order(2)
    @DisplayName("W-DS-2: DAO retrieves SH AABB dimensions")
    void w_ds_2_dao_sh_aabb() throws Exception {
        DesignerDAO dao = new DesignerDAO(conn);
        var sh = dao.getBuildingType("Ifc4_SampleHouse");
        assertNotNull(sh);
        assertEquals(10000, sh.aabbWidthMm());
        assertEquals(6000, sh.aabbDepthMm());
        assertEquals(6000, sh.aabbHeightMm());
        assertEquals(55, sh.expectedElements());
    }

    @Test
    @Order(3)
    @DisplayName("W-DS-3: DAO lists SH categories (LIVING, KITCHEN, BEDROOM, BATHROOM)")
    void w_ds_3_dao_sh_categories() throws Exception {
        DesignerDAO dao = new DesignerDAO(conn);
        var cats = dao.listCategories("SH");
        assertEquals(4, cats.size(), "4 room categories");
        var names = cats.stream().map(DesignerDAO.CategoryRow::categoryName).toList();
        assertTrue(names.contains("LIVING"));
        assertTrue(names.contains("KITCHEN"));
        assertTrue(names.contains("BEDROOM"));
        assertTrue(names.contains("BATHROOM"));
    }

    @Test
    @Order(4)
    @DisplayName("W-DS-4: DAO walks BOM tree — BUILDING → FLOOR → ROOM → LEAF")
    void w_ds_4_dao_bom_tree_walk() throws Exception {
        DesignerDAO dao = new DesignerDAO(conn);

        // BUILDING level
        var building = dao.getBomHeader("BUILDING_SH");
        assertNotNull(building);
        assertEquals("BUILDING", building.bomType());

        // BUILDING → 2 FLOOR children
        var floors = dao.getBomLines("BUILDING_SH");
        assertEquals(2, floors.size(), "GF + FF");
        assertTrue(floors.stream().allMatch(l -> "MAKE".equals(l.componentType())));

        // GF floor → 3 ROOM children
        var rooms = dao.getBomLines("FLOOR_SH_GF");
        assertEquals(3, rooms.size(), "LI + KT + BT");

        // Living room → 3 LEAF products
        var leafs = dao.getBomLines("ROOM_SH_LI");
        assertEquals(3, leafs.size(), "WALL + SLAB + WINDOW");
        assertTrue(leafs.stream().allMatch(l -> "BUY".equals(l.componentType())));

        // Verify tack convention: dx/dy/dz >= 0
        for (var line : leafs) {
            assertTrue(line.dx() >= 0, "dx >= 0 (tack convention)");
            assertTrue(line.dy() >= 0, "dy >= 0 (tack convention)");
            assertTrue(line.dz() >= 0, "dz >= 0 (tack convention)");
        }
    }

    // ── API layer proofs ────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-DS-10: API lists building types from C_DocType")
    void w_ds_10_api_list_buildings() {
        List<DesignerAPI.BuildingTypeInfo> types = api.listBuildingTypes();
        assertEquals(3, types.size());
        assertEquals("SH", types.get(0).docSubType());
        assertEquals("DX", types.get(1).docSubType());
        assertEquals("TE", types.get(2).docSubType());
    }

    @Test
    @Order(11)
    @DisplayName("W-DS-11: API compile returns structured response (stub)")
    void w_ds_11_api_compile() {
        CompileRequest req = new CompileRequest(
                "Ifc4_SampleHouse",
                "library/_SH_compile.db",
                null, null);
        CompileResponse resp = api.compile(req);
        assertTrue(resp.success());
        assertEquals(55, resp.elementCount());
        assertTrue(resp.compileTimeMs() >= 0);
        assertNotNull(resp.outputDbPath());
        assertNotNull(resp.spatialDigest());
    }

    @Test
    @Order(12)
    @DisplayName("W-DS-12: API compile rejects unknown building")
    void w_ds_12_api_compile_unknown() {
        CompileRequest req = new CompileRequest(
                "NonExistent_Building",
                "library/_XX_compile.db",
                null, null);
        CompileResponse resp = api.compile(req);
        assertFalse(resp.success());
        assertTrue(resp.error().contains("Unknown building"));
    }

    @Test
    @Order(13)
    @DisplayName("W-DS-13: API verb dispatch returns structured response (stub)")
    void w_ds_13_api_verb() {
        var resp = api.executeVerb("Ifc4_SampleHouse", "CHECK BOM BUILDING_SH");
        assertTrue(resp.success());
        assertEquals("CHECK BOM", resp.verb());
    }

    @Test
    @Order(14)
    @DisplayName("W-DS-14: API categories for TE returns empty (no room BOMs seeded)")
    void w_ds_14_api_categories_te() {
        var cats = api.listCategories("TE");
        assertTrue(cats.isEmpty(), "TE has no room-level BOMs in stub data");
    }

    // ── TCP protocol layer proofs ───────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-DS-20: TCP compile request → JSON response")
    void w_ds_20_tcp_compile() throws Exception {
        String response = tcpRequest(
                "{\"action\":\"compile\",\"buildingId\":\"Ifc4_SampleHouse\","
                        + "\"bomDbPath\":\"library/_SH_compile.db\"}");
        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("\"elementCount\":55"));
    }

    @Test
    @Order(21)
    @DisplayName("W-DS-21: TCP listBuildings → 3 buildings")
    void w_ds_21_tcp_list_buildings() throws Exception {
        String response = tcpRequest("{\"action\":\"listBuildings\"}");
        // BuildingTypeInfo serializes name (human), docSubType, docTypeId
        assertTrue(response.contains("Sample House"), "SH name in response");
        assertTrue(response.contains("Duplex A Unit 01"), "DX name in response");
        assertTrue(response.contains("Airport Terminal"), "TE name in response");
        assertTrue(response.contains("\"SH\""), "SH docSubType");
        assertTrue(response.contains("\"DX\""), "DX docSubType");
        assertTrue(response.contains("\"TE\""), "TE docSubType");
    }

    @Test
    @Order(22)
    @DisplayName("W-DS-22: TCP verb dispatch → structured response")
    void w_ds_22_tcp_verb() throws Exception {
        String response = tcpRequest(
                "{\"action\":\"verb\",\"buildingId\":\"Ifc4_SampleHouse\","
                        + "\"verbLine\":\"SNAP TO GRID 10000 6000 1000\"}");
        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("SNAP TO GRID"));
    }

    @Test
    @Order(23)
    @DisplayName("W-DS-23: TCP unknown action → error response")
    void w_ds_23_tcp_unknown_action() throws Exception {
        String response = tcpRequest("{\"action\":\"destroyEverything\"}");
        assertTrue(response.contains("Unknown action"));
    }

    @Test
    @Order(24)
    @DisplayName("W-DS-24: TCP listCategories for SH → 4 room types")
    void w_ds_24_tcp_list_categories() throws Exception {
        String response = tcpRequest(
                "{\"action\":\"listCategories\",\"docSubType\":\"SH\"}");
        assertTrue(response.contains("LIVING"));
        assertTrue(response.contains("KITCHEN"));
        assertTrue(response.contains("BEDROOM"));
        assertTrue(response.contains("BATHROOM"));
    }

    // ── Helper ──────────────────────────────────────────────────────

    private String tcpRequest(String json) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(json);
            return in.readLine();
        }
    }
}
