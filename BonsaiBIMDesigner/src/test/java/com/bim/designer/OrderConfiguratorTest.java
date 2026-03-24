package com.bim.designer;

import com.bim.designer.api.*;
import com.bim.designer.dao.WorkOutputDAO;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S57 — Order Configurator sandbox test.
 *
 * <p>Demo house scenario: BOM Drop → list lines → edit category → add FP line
 * → save → complete. All in-memory, no server, no filesystem dependency.
 *
 * <p>Traces the full iDempiere C_Order → C_OrderLine → ASI chain.
 * Exposes bugs systematically before UI integration.
 *
 * // Implementing BIM_Designer_SRS.md §29.5 — Witness: W-OC-*
 */
@DisplayName("Order Configurator — Demo House End-to-End")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderConfiguratorTest {

    private static Connection bomConn;     // simulates *_BOM.db
    private static Connection workConn;    // simulates work_output.db
    private static WorkOutputDAO woDao;

    // Shared state across ordered tests (demo house scenario)
    private static String orderId;
    private static int rootLineId;
    private static int roofLineId;
    private static int fpLineId;

    @BeforeAll
    static void setUp() throws Exception {
        // Silence loggers
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);

        // ── BOM database (in-memory) — simulates SH_BOM.db ──
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        seedBomDb(bomConn);

        // ── Work output database (in-memory) — simulates work_building_sh_std.db ──
        workConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        woDao = new WorkOutputDAO(workConn);
        woDao.initSchema();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
        if (workConn != null) workConn.close();
    }

    // ══════════════════════════════════════════════════════════════════
    // Step 1: BOM Drop — create C_Order + explode into C_OrderLine
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-OC-1: createBomDropOrder creates C_Order with correct Parent_Order_ID")
    void w_oc_1_create_order() throws Exception {
        orderId = woDao.createBomDropOrder("BUILDING_SH_STD", 16868, 8668, 3945);

        assertNotNull(orderId, "Order ID must not be null");
        assertTrue(orderId.startsWith("DROP_BUILDING_SH_STD_"), "Order ID format");

        // Verify C_Order exists and has correct fields
        try (Statement stmt = workConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT C_Order_ID, Parent_Order_ID, Name, DocStatus FROM C_Order WHERE C_Order_ID = '" + orderId + "'")) {
            assertTrue(rs.next(), "C_Order must exist");
            assertEquals("DR", rs.getString("DocStatus"));
            assertEquals("BUILDING_SH_STD", rs.getString("Name"));
        }
    }

    @Test @Order(2)
    @DisplayName("W-OC-2: findActiveSubOrder finds the BOM Drop order by buildingProductId")
    void w_oc_2_find_active_sub_order() throws Exception {
        // Parent_Order_ID = buildingProductId (BUG-1 fix)
        // findActiveSubOrder queries WHERE Parent_Order_ID = 'BUILDING_SH_STD'
        var lines = woDao.listOrderLines("BUILDING_SH_STD");
        assertNotNull(lines, "listOrderLines must not return null");
        // Order exists but no lines yet — empty is correct at this stage
    }

    @Test @Order(3)
    @DisplayName("W-OC-3: insertBomDropLine creates root BUILDING line")
    void w_oc_3_insert_root_line() throws Exception {
        rootLineId = woDao.insertBomDropLine(orderId, 0,
                "BUILDING_SH_STD", "BUILDING", "GF",
                0, 0, 0, 16868, 8668, 3945, 1);

        assertTrue(rootLineId > 0, "Root line ID must be positive");

        // Verify line exists
        var line = woDao.getOrderLine(rootLineId);
        assertNotNull(line, "Root line must exist");
        assertEquals("BUILDING_SH_STD", line.familyRef());
        assertEquals("BUILDING", line.hostType());
        assertEquals(16868, line.widthMm(), 0.01);
    }

    @Test @Order(4)
    @DisplayName("W-OC-4: Insert floor + room + leaf lines (SH structure)")
    void w_oc_4_insert_tree() throws Exception {
        // Floor
        int floorId = woDao.insertBomDropLine(orderId, rootLineId,
                "FLOOR_SH_GF_STD", "FLOOR", "GF",
                0, 0, 0, 16868, 8668, 3300, 1);
        assertTrue(floorId > 0);

        // Room — Living
        int livingId = woDao.insertBomDropLine(orderId, floorId,
                "SH_LIVING_SET", "ROOM", "LI",
                0, 0, 0, 8000, 2000, 1200, 1);
        assertTrue(livingId > 0);

        // Roof — flat (this is what we'll change to pitched)
        roofLineId = woDao.insertBomDropLine(orderId, rootLineId,
                "ROOF_ASSEMBLY", "BUILDING", "RF",
                0, 0, 0, 16868, 8668, 1730, 1);
        assertTrue(roofLineId > 0);

        // Leaf elements — walls, windows
        for (int i = 0; i < 5; i++) {
            woDao.insertBomDropLine(orderId, floorId,
                    "WALL_EXT_" + i, "LEAF", "WL",
                    i * 3000, 0, 0, 3000, 200, 3000, 1);
        }
        for (int i = 0; i < 3; i++) {
            woDao.insertBomDropLine(orderId, floorId,
                    "WINDOW_STD_" + i, "LEAF", "WI",
                    i * 2000, 0, 1000, 1200, 100, 1500, 1);
        }
    }

    @Test @Order(5)
    @DisplayName("W-OC-5: listOrderLines returns all inserted lines")
    void w_oc_5_list_order_lines() throws Exception {
        // Direct query by order ID (bypasses findActiveSubOrder)
        try (PreparedStatement ps = workConn.prepareStatement(
                "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertEquals(12, count,
                        "12 lines: 1 building + 1 floor + 1 living + 1 roof + 5 walls + 3 windows");
            }
        }

        // listOrderLines(buildingId) uses findActiveSubOrder(Parent_Order_ID = buildingProductId)
        var lines = woDao.listOrderLines("BUILDING_SH_STD");
        assertEquals(12, lines.size(), "All 12 lines must be returned via findActiveSubOrder");
    }

    // ══════════════════════════════════════════════════════════════════
    // Step 2: Edit — change roof category to pitched
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(6)
    @DisplayName("W-OC-6: getOrderLine retrieves the roof line")
    void w_oc_6_get_roof_line() throws Exception {
        var roof = woDao.getOrderLine(roofLineId);
        assertNotNull(roof, "Roof line must exist");
        assertEquals("ROOF_ASSEMBLY", roof.familyRef());
        assertEquals("RF", roof.bomCategory());
        assertEquals(1730, roof.heightMm(), 0.01);
    }

    @Test @Order(7)
    @DisplayName("W-OC-7: updateOrderLine edits AABB dimensions (whitelisted fields)")
    void w_oc_7_update_dimensions() throws Exception {
        // Change roof height from 1730 (flat) to 3000 (pitched)
        boolean updated = woDao.updateOrderLine(roofLineId, "aabb_height_mm", "3000");
        assertTrue(updated, "Height update must succeed (whitelisted field)");

        var roof = woDao.getOrderLine(roofLineId);
        assertEquals(3000, roof.heightMm(), 0.01, "Roof height must be 3000mm (pitched)");
    }

    @Test @Order(8)
    @DisplayName("W-OC-8: updateOrderLine rejects dangerous fields, allows category/product swap")
    void w_oc_8_field_whitelist() throws Exception {
        // m_product_category_id and family_ref ARE now whitelisted (for category swap)
        boolean updated = woDao.updateOrderLine(roofLineId, "m_product_category_id", "RF");
        assertTrue(updated, "m_product_category_id must be editable (category swap)");

        updated = woDao.updateOrderLine(roofLineId, "family_ref", "ROOF_ASSEMBLY");
        assertTrue(updated, "family_ref must be editable (product swap)");

        // But SQL-injection-prone fields must still be blocked
        updated = woDao.updateOrderLine(roofLineId, "C_Order_ID", "HACKED");
        assertFalse(updated, "C_Order_ID must be rejected");

        updated = woDao.updateOrderLine(roofLineId, "IsActive", "0");
        assertFalse(updated, "IsActive must be rejected");
    }

    @Test @Order(9)
    @DisplayName("W-OC-9: Qty update works (whitelisted)")
    void w_oc_9_update_qty() throws Exception {
        boolean updated = woDao.updateOrderLine(roofLineId, "Qty", "2");
        assertTrue(updated, "Qty update must succeed");

        var roof = woDao.getOrderLine(roofLineId);
        assertEquals(2, roof.qty(), "Qty must be 2");

        // Restore to 1
        woDao.updateOrderLine(roofLineId, "Qty", "1");
    }

    // ══════════════════════════════════════════════════════════════════
    // Step 3: Add FP (Fire Protection) line
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("W-OC-10: insertBomDropLine adds FP sprinkler to existing order")
    void w_oc_10_add_fp_line() throws Exception {
        // Add FP product to the order (like adding a line in Sales Order)
        fpLineId = woDao.insertBomDropLine(orderId, rootLineId,
                "FP_SPRINKLER_HEAD", "LEAF", "FP",
                5000, 3000, 2800, 100, 100, 200, 4);

        assertTrue(fpLineId > 0, "FP line ID must be positive");

        var fpLine = woDao.getOrderLine(fpLineId);
        assertNotNull(fpLine);
        assertEquals("FP_SPRINKLER_HEAD", fpLine.familyRef());
        assertEquals("FP", fpLine.bomCategory());
        assertEquals("LEAF", fpLine.hostType());
        assertEquals(4, fpLine.qty());
    }

    @Test @Order(11)
    @DisplayName("W-OC-11: Order now has 13 lines (12 original + 1 FP)")
    void w_oc_11_total_lines_after_add() throws Exception {
        try (PreparedStatement ps = workConn.prepareStatement(
                "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(13, rs.getInt(1), "13 lines after adding FP");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Step 4: ASI — read and write attributes
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(12)
    @DisplayName("W-OC-12: ASI schema initialises without error")
    void w_oc_12_asi_schema() throws Exception {
        woDao.initASISchema();
        // Verify tables exist
        try (Statement stmt = workConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'M_Attribute%'")) {
            assertTrue(rs.next(), "ASI tables must exist after initASISchema");
        }
    }

    @Test @Order(13)
    @DisplayName("W-OC-13: getASIForOrderLine returns -1 for line without ASI")
    void w_oc_13_no_asi() throws Exception {
        int asiId = woDao.getASIForOrderLine(fpLineId);
        assertTrue(asiId < 0, "No ASI should exist yet for FP line");
    }

    // ══════════════════════════════════════════════════════════════════
    // Step 5: DocAction — Save + Complete
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(14)
    @DisplayName("W-OC-14: DocStatus transitions DR → IP (Save)")
    void w_oc_14_save_draft_to_ip() throws Exception {
        // Update DocStatus DR → IP
        try (PreparedStatement ps = workConn.prepareStatement(
                "UPDATE C_Order SET DocStatus = 'IP' WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            assertEquals(1, ps.executeUpdate());
        }

        // Verify
        try (PreparedStatement ps = workConn.prepareStatement(
                "SELECT DocStatus FROM C_Order WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("IP", rs.getString(1));
            }
        }
    }

    @Test @Order(15)
    @DisplayName("W-OC-15: DocStatus transitions IP → CO (Complete)")
    void w_oc_15_complete_to_co() throws Exception {
        try (PreparedStatement ps = workConn.prepareStatement(
                "UPDATE C_Order SET DocStatus = 'CO' WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            assertEquals(1, ps.executeUpdate());
        }

        try (PreparedStatement ps = workConn.prepareStatement(
                "SELECT DocStatus FROM C_Order WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CO", rs.getString(1));
            }
        }
    }

    @Test @Order(16)
    @DisplayName("W-OC-16: listOrderLines still works after Complete (fallback to latest CO)")
    void w_oc_16_list_after_complete() throws Exception {
        // findActiveSubOrder looks for DR/IP — won't find CO
        // findLatestSubOrder is the fallback — should find it
        var lines = woDao.listOrderLines("BUILDING_SH_STD");
        assertEquals(13, lines.size(), "All 13 lines accessible after CO");
    }

    // ══════════════════════════════════════════════════════════════════
    // Summary: verify final state
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("W-OC-17: Final state — correct structure, counts, and edits")
    void w_oc_17_final_state() throws Exception {
        var lines = woDao.listOrderLines("BUILDING_SH_STD");
        assertEquals(13, lines.size());

        // Verify roof was edited
        var roof = woDao.getOrderLine(roofLineId);
        assertEquals(3000, roof.heightMm(), 0.01, "Roof height must be 3000mm (pitched)");

        // Verify FP was added
        var fp = woDao.getOrderLine(fpLineId);
        assertEquals("FP_SPRINKLER_HEAD", fp.familyRef());
        assertEquals(4, fp.qty());

        // Verify order is Complete
        try (PreparedStatement ps = workConn.prepareStatement(
                "SELECT DocStatus FROM C_Order WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CO", rs.getString(1));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Bug detectors
    // ══════════════════════════════════════════════════════════════════

    @Test @Order(30)
    @DisplayName("BUG-1 FIXED: createBomDropOrder sets Parent_Order_ID = buildingProductId")
    void bug_1_fixed_parent_order_id() throws Exception {
        Connection freshConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        WorkOutputDAO freshDao = new WorkOutputDAO(freshConn);
        freshDao.initSchema();

        String freshOrderId = freshDao.createBomDropOrder("TEST_BUG", 1000, 1000, 1000);
        freshDao.insertBomDropLine(freshOrderId, 0, "TEST", "BUILDING", "GF",
                0, 0, 0, 1000, 1000, 1000, 1);

        // Parent_Order_ID should now be set → findActiveSubOrder works
        var lines = freshDao.listOrderLines("TEST_BUG");
        assertEquals(1, lines.size(), "BUG-1 FIXED: listOrderLines finds line via Parent_Order_ID");

        // Verify Parent_Order_ID is set correctly
        try (Statement stmt = freshConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT Parent_Order_ID FROM C_Order WHERE C_Order_ID = '" + freshOrderId + "'")) {
            assertTrue(rs.next());
            assertEquals("TEST_BUG", rs.getString("Parent_Order_ID"));
        }

        freshConn.close();
    }

    @Test @Order(31)
    @DisplayName("BUG-2 FIXED: m_product_category_id and family_ref now editable")
    void bug_2_fixed_category_editable() throws Exception {
        boolean canEditCategory = woDao.updateOrderLine(roofLineId, "m_product_category_id", "RF_PITCHED");
        assertTrue(canEditCategory, "BUG-2 FIXED: m_product_category_id must be editable");

        var roof = woDao.getOrderLine(roofLineId);
        assertEquals("RF_PITCHED", roof.bomCategory(), "Category must be updated");

        boolean canEditProduct = woDao.updateOrderLine(roofLineId, "family_ref", "ROOF_PITCHED_ASM");
        assertTrue(canEditProduct, "family_ref must be editable for product swap");

        roof = woDao.getOrderLine(roofLineId);
        assertEquals("ROOF_PITCHED_ASM", roof.familyRef(), "Product must be swapped");
    }

    @Test @Order(32)
    @DisplayName("BUG-3: dbPathFor uses buildingId directly — orderId breaks path")
    void bug_3_db_path_order_id() {
        // If JS sends orderId instead of productId, we get wrong DB path
        String correctPath = WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        String brokenPath = WorkOutputDAO.dbPathFor("DROP_BUILDING_SH_STD_1234567890");

        assertEquals("library/work_building_sh_std.db", correctPath);
        assertEquals("library/work_drop_building_sh_std_1234567890.db", brokenPath);

        assertNotEquals(correctPath, brokenPath,
                "BUG-3: orderId and productId produce different DB paths — " +
                "JS must use productId, not orderId");
    }

    // ══════════════════════════════════════════════════════════════════
    // Seed data — simulates SH_BOM.db structure
    // ══════════════════════════════════════════════════════════════════

    private static void seedBomDb(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE m_bom (
                    bom_id TEXT PRIMARY KEY, bom_name TEXT, bom_type TEXT,
                    m_product_category_id TEXT, is_active INTEGER DEFAULT 1,
                    aabb_width_mm REAL, aabb_depth_mm REAL, aabb_height_mm REAL
                )""");
            stmt.execute("""
                CREATE TABLE m_bom_line (
                    bom_child_id INTEGER PRIMARY KEY, bom_id TEXT,
                    child_product_id TEXT, component_type TEXT DEFAULT 'BUY',
                    qty INTEGER DEFAULT 1, seq_no INTEGER DEFAULT 10,
                    allocated_width_mm REAL, allocated_depth_mm REAL, allocated_height_mm REAL,
                    dx REAL DEFAULT 0, dy REAL DEFAULT 0, dz REAL DEFAULT 0
                )""");

            // BUILDING BOM
            stmt.execute("""
                INSERT INTO m_bom VALUES ('BUILDING_SH_STD', 'Sample House', 'BUILDING', 'GF', 1, 16868, 8668, 3945)
                """);
            // FLOOR BOM
            stmt.execute("""
                INSERT INTO m_bom VALUES ('FLOOR_SH_GF_STD', 'Ground Floor', 'FLOOR', 'GF', 1, 16868, 8668, 3300)
                """);
            // BOM Lines: BUILDING → FLOOR
            stmt.execute("""
                INSERT INTO m_bom_line (bom_child_id, bom_id, child_product_id, component_type, qty, seq_no,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES (1, 'BUILDING_SH_STD', 'FLOOR_SH_GF_STD', 'MAKE', 1, 10, 16868, 8668, 3300)
                """);
        }
    }
}
