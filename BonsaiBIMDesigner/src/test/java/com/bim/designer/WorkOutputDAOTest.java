package com.bim.designer;

import com.bim.designer.api.*;
import com.bim.designer.dao.WorkOutputDAO;
import com.bim.designer.validation.PlacementValidator;
import com.bim.designer.validation.PlacementValidatorImpl;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkOutputDAO + save/recall/listVariants integration tests.
 *
 * <p>Proves the full G-4 persistence path:
 * createNew → save → listVariants → recall → round-trip fidelity.
 *
 * <p>All tests use in-memory SQLite — no filesystem dependency.
 *
 * // Implementing G4_SRS §2 — Witness: W-WO-DAO-1..6
 */
@DisplayName("WorkOutputDAO — G-4 Persistence")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkOutputDAOTest {

    private static Connection woConn;
    private static WorkOutputDAO dao;

    // Shared test data — a simple 3-room layout
    private static final String BUILDING_ID = "TestHouse";
    private static List<DesignBBox> testBboxes;
    private static String savedVariantId;

    @BeforeAll
    static void setUp() throws Exception {
        woConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        dao = new WorkOutputDAO(woConn);
        dao.initSchema();

        // Create test bboxes (matches RoomLayoutGenerator output shape)
        testBboxes = List.of(
                new DesignBBox("BLDG_01", "Test House", "BUILDING", null,
                        "IfcBuilding", null, null,
                        0, 0, 0, 9000, 7000, 3000),
                new DesignBBox("FLOOR_GF", "Ground Floor", "FLOOR", null,
                        "IfcBuildingStorey", "GF", "BLDG_01",
                        0, 0, 0, 9000, 7000, 3000),
                new DesignBBox("ROOM_LI_01", "Living Room", "ROOM", "LIVING",
                        "IfcSpace", "GF", "FLOOR_GF",
                        0, 0, 0, 5400, 4200, 3000),
                new DesignBBox("ROOM_KT_01", "Kitchen", "ROOM", "KITCHEN",
                        "IfcSpace", "GF", "FLOOR_GF",
                        5400, 0, 0, 9000, 4200, 3000),
                new DesignBBox("ROOM_BR_01", "Bedroom 1", "ROOM", "BEDROOM",
                        "IfcSpace", "GF", "FLOOR_GF",
                        0, 4200, 0, 4500, 7000, 3000)
        );
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (woConn != null && !woConn.isClosed()) woConn.close();
    }

    // ── Schema ────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-WO-DAO-1: Schema init creates all required tables")
    void w_wo_dao_1_schema_init() throws Exception {
        // Verify core tables exist by querying sqlite_master
        var tables = new java.util.ArrayList<String>();
        try (var rs = woConn.getMetaData().getTables(null, null, "%", null)) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        assertTrue(tables.contains("C_Order"), "C_Order table exists");
        assertTrue(tables.contains("C_OrderLine"), "C_OrderLine table exists");
        assertTrue(tables.contains("W_Variant"), "W_Variant table exists");
        assertTrue(tables.contains("W_BuildingConfig"), "W_BuildingConfig table exists");
        assertTrue(tables.contains("AD_SysConfig"), "AD_SysConfig table exists");

        // Verify schema version
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT Value FROM AD_SysConfig WHERE Name = 'SCHEMA_VERSION'")) {
            assertTrue(rs.next());
            assertEquals("W001", rs.getString(1));
        }
    }

    // ── Master Order ──────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("W-WO-DAO-2: ensureMasterOrder creates master C_Order (idempotent)")
    void w_wo_dao_2_master_order() throws Exception {
        String id1 = dao.ensureMasterOrder(BUILDING_ID, "GENERATIVE", 9000, 7000, 3000);
        assertEquals(BUILDING_ID, id1);

        // Second call is idempotent
        String id2 = dao.ensureMasterOrder(BUILDING_ID, "GENERATIVE", 9000, 7000, 3000);
        assertEquals(id1, id2);

        // Verify master has DocStatus IP, no parent
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT DocStatus, Parent_Order_ID FROM C_Order WHERE C_Order_ID = '"
                             + BUILDING_ID + "'")) {
            assertTrue(rs.next());
            assertEquals("IP", rs.getString(1));
            assertNull(rs.getString(2));
        }
    }

    // ── Save ──────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("W-WO-DAO-3: Save creates sub-order + OrderLines + W_Variant")
    void w_wo_dao_3_save() throws Exception {
        savedVariantId = dao.save(BUILDING_ID, testBboxes, "initial");
        assertNotNull(savedVariantId);

        // Verify sub-order exists with DocStatus CO and parent = master
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT DocStatus, Parent_Order_ID FROM C_Order WHERE C_Order_ID = '"
                             + savedVariantId + "'")) {
            assertTrue(rs.next());
            assertEquals("CO", rs.getString("DocStatus"), "Sub-order frozen on save");
            assertEquals(BUILDING_ID, rs.getString("Parent_Order_ID"));
        }

        // Verify OrderLine count
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = '"
                             + savedVariantId + "'")) {
            assertEquals(testBboxes.size(), rs.getInt(1),
                    "One C_OrderLine per bbox");
        }

        // Verify W_Variant
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT label, is_active, orderline_count FROM W_Variant WHERE C_Order_ID = '"
                             + savedVariantId + "'")) {
            assertTrue(rs.next());
            assertEquals("initial", rs.getString("label"));
            assertEquals(1, rs.getInt("is_active"), "Latest variant is active");
            assertEquals(testBboxes.size(), rs.getInt("orderline_count"));
        }
    }

    @Test
    @Order(4)
    @DisplayName("W-WO-DAO-4: Second save auto-labels and deactivates previous")
    void w_wo_dao_4_second_save() throws Exception {
        String secondId = dao.save(BUILDING_ID, testBboxes, null);
        assertNotNull(secondId);
        assertNotEquals(savedVariantId, secondId);

        // Previous variant deactivated
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT is_active FROM W_Variant WHERE C_Order_ID = '"
                             + savedVariantId + "'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("is_active"), "Previous variant deactivated");
        }

        // New variant is active with auto-label "v2"
        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT label, is_active FROM W_Variant WHERE C_Order_ID = '"
                             + secondId + "'")) {
            assertTrue(rs.next());
            assertEquals("v2", rs.getString("label"), "Auto-label v2");
            assertEquals(1, rs.getInt("is_active"));
        }
    }

    // ── List Variants ─────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("W-WO-DAO-5: listVariants returns all saved variants")
    void w_wo_dao_5_list_variants() throws Exception {
        var variants = dao.listVariants(BUILDING_ID);
        assertEquals(2, variants.size(), "2 variants saved");

        // Both labels present (order may vary within same-second saves)
        var labels = variants.stream().map(DesignerAPI.VariantInfo::label).toList();
        assertTrue(labels.contains("initial"), "Has 'initial' variant");
        assertTrue(labels.contains("v2"), "Has 'v2' variant");

        // Each variant has correct orderline count
        for (var v : variants) {
            assertEquals(testBboxes.size(), v.orderLineCount());
            assertEquals("UNCHECKED", v.complianceStatus());
        }
    }

    // ── Recall ────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("W-WO-DAO-6: Recall round-trips bbox data (tack + dims)")
    void w_wo_dao_6_recall_roundtrip() throws Exception {
        List<DesignBBox> recalled = dao.recall(savedVariantId);
        assertEquals(testBboxes.size(), recalled.size(),
                "Same number of bboxes after recall");

        // Verify each bbox's geometry round-trips through tack (mm→m→mm)
        for (int i = 0; i < testBboxes.size(); i++) {
            DesignBBox orig = testBboxes.get(i);
            DesignBBox recv = recalled.get(i);

            assertEquals(orig.bomId(), recv.bomId(), "bomId preserved");
            assertEquals(orig.bomType(), recv.bomType(), "bomType preserved");
            assertEquals(orig.category(), recv.category(), "category preserved");

            // Geometry: allow tiny floating-point tolerance from mm→m→mm round-trip
            double tol = 0.01;
            assertEquals(orig.minX(), recv.minX(), tol, "minX round-trip");
            assertEquals(orig.minY(), recv.minY(), tol, "minY round-trip");
            assertEquals(orig.minZ(), recv.minZ(), tol, "minZ round-trip");
            assertEquals(orig.maxX() - orig.minX(), recv.maxX() - recv.minX(), tol,
                    "width round-trip");
            assertEquals(orig.maxY() - orig.minY(), recv.maxY() - recv.minY(), tol,
                    "depth round-trip");
            assertEquals(orig.maxZ() - orig.minZ(), recv.maxZ() - recv.minZ(), tol,
                    "height round-trip");
        }
    }

    // ── Snap (validation) ─────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-SNAP-1: Snap returns grid-snapped bboxes with adjustments")
    void w_snap_1_grid_snap() {
        // Build API with no validator active (grid snap only)
        Connection fakeBom;
        try {
            fakeBom = DriverManager.getConnection("jdbc:sqlite::memory:");
            com.bim.designer.dao.StubDataSeeder.seed(fakeBom);
        } catch (Exception e) {
            fail("Failed to create test connection: " + e.getMessage());
            return;
        }

        var api = new DesignerAPIImpl(fakeBom);

        // Room 3100mm wide → should snap to 3000 or 3250 on 250mm grid
        var bbox = new DesignBBox("ROOM_TEST", "Test", "ROOM", "BEDROOM",
                "IfcSpace", "GF", "FLOOR_GF",
                0, 0, 0, 3100, 3100, 3000);

        var resp = api.snap(List.of(bbox), "MY", 250);
        assertTrue(resp.success());
        assertEquals(1, resp.bboxes().size());

        // Width 3100 → snaps to 3000 (nearest 250 multiple)
        DesignBBox snapped = resp.bboxes().get(0);
        double snappedWidth = snapped.maxX() - snapped.minX();
        assertEquals(0, snappedWidth % 250, 0.01,
                "Width snapped to grid: " + snappedWidth);

        // Should have grid-snap adjustments logged
        assertFalse(resp.adjustments().isEmpty(), "Grid snap produced adjustments");
    }
}
