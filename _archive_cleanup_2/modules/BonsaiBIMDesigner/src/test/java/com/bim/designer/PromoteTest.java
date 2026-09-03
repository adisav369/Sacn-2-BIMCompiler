package com.bim.designer;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;
import com.bim.designer.dao.StubDataSeeder;
import com.bim.designer.dao.WorkOutputDAO;
import com.bim.designer.validation.PlacementValidatorImpl;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Promote governance gate — G-10.
 *
 * <p>Proves the full promote path: approve → promote → m_bom + m_bom_line
 * in BOM.db, with DocStatus transitions and dangle detection.
 *
 * // Implementing BIM_Designer.md §17.10.4 — Witness: W-PROMOTE-1..6
 */
@DisplayName("Promote — G-10 Governance Gate")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PromoteTest {

    private static Connection bomConn;
    private static DesignerAPIImpl api;

    private static final String BUILDING_ID = "PromoteTestHouse";

    // Test bboxes — BUILDING > FLOOR > 2 ROOMs
    private static final List<DesignBBox> TEST_BBOXES = List.of(
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
                    5400, 0, 0, 9000, 4200, 3000)
    );

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(bomConn);

        // Use unactivated validator — isActive()=false so approve always passes
        api = new DesignerAPIImpl(bomConn, new PlacementValidatorImpl());

        // Create work_output.db and save a design so getCurrentBboxes works
        WorkOutputDAO woDao = new WorkOutputDAO(
                getOrCreateWorkOutputConn(BUILDING_ID));
        woDao.initSchema();
        woDao.ensureMasterOrder(BUILDING_ID, "GENERATIVE", 9000, 7000, 3000);
        woDao.save(BUILDING_ID, TEST_BBOXES, "promote-test");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
    }

    /**
     * Get or wire the work_output.db connection into DesignerAPIImpl's internal map
     * by triggering getWorkOutputDAO via save() or by direct injection.
     */
    private static Connection getOrCreateWorkOutputConn(String buildingId) throws Exception {
        // Use in-memory for test — need to wire it into the API's workOutputConns map
        Connection woConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        // Inject via reflection since workOutputConns is package-private
        var field = DesignerAPIImpl.class.getDeclaredField("workOutputConns");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<String, Connection>) field.get(api);
        map.put(buildingId, woConn);
        return woConn;
    }

    // ── W-PROMOTE-2: Must approve first ──────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-PROMOTE-2: Promote blocked if master C_Order is not AP")
    void w_promote_2_blocked_without_approve() {
        // Master C_Order starts as IP — promote should fail
        PromoteRequest req = new PromoteRequest(
                BUILDING_ID, "test-user", null, "GENERATIVE", TEST_BBOXES);
        PromoteResponse resp = api.promote(req);

        assertFalse(resp.success(), "Promote must fail without prior approve");
        assertNotNull(resp.error());
        assertTrue(resp.error().contains("Approve design first"),
                "Error should say 'Approve design first', got: " + resp.error());
    }

    // ── W-PROMOTE-1: Full promote after approve ─────────────────────

    @Test
    @Order(2)
    @DisplayName("W-PROMOTE-1: Promote after approve creates m_bom + m_bom_line rows")
    void w_promote_1_creates_bom_rows() throws Exception {
        // First approve (sets DocStatus AP)
        ApproveResponse approveResp = api.approve(BUILDING_ID);
        assertTrue(approveResp.success(), "Approve should pass: " + approveResp.error());
        assertEquals("AP", approveResp.status());

        // Now promote
        PromoteRequest req = new PromoteRequest(
                BUILDING_ID, "test-user", null, "GENERATIVE", TEST_BBOXES);
        PromoteResponse resp = api.promote(req);

        assertTrue(resp.success(), "Promote should succeed: " + resp.error());
        assertEquals(BUILDING_ID, resp.buildingId());
        assertTrue(resp.bomEntriesCreated() > 0, "Should create BOM entries");
        assertTrue(resp.dangles().isEmpty(), "No dangles expected");

        // Verify m_bom rows exist in BOM.db
        try (var stmt = bomConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM m_bom WHERE bom_id LIKE '%_GEN'")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 4, "Should have ≥4 m_bom GEN rows (BLDG+FLOOR+2ROOM)");
        }

        // Verify m_bom_line rows exist
        try (var stmt = bomConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM m_bom_line WHERE entity_type = 'U'")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 3, "Should have ≥3 m_bom_line rows (FLOOR+2ROOM children)");
        }
    }

    // ── W-PROMOTE-3: entity_type='U', provenance='GENERATIVE' ───────

    @Test
    @Order(3)
    @DisplayName("W-PROMOTE-3: Promoted m_bom entries have entity_type='U', provenance='GENERATIVE'")
    void w_promote_3_entity_type_and_provenance() throws Exception {
        // Check entity_type on promoted m_bom rows
        try (var stmt = bomConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT entity_type, description FROM m_bom WHERE bom_id LIKE '%_GEN'")) {
            int count = 0;
            while (rs.next()) {
                assertEquals("U", rs.getString("entity_type"),
                        "Promoted BOM must have entity_type='U' (User)");
                assertEquals("GENERATIVE", rs.getString("description"),
                        "Promoted BOM provenance should be GENERATIVE");
                count++;
            }
            assertTrue(count > 0, "Should have promoted m_bom rows");
        }
    }

    // ── W-PROMOTE-4: DocStatus AP → CO ──────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-PROMOTE-4: Promote transitions master C_Order AP → CO (frozen)")
    void w_promote_4_docstatus_frozen() throws Exception {
        // After promote (ran in test 2), master should be CO
        var field = DesignerAPIImpl.class.getDeclaredField("workOutputConns");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<String, Connection>) field.get(api);
        Connection woConn = map.get(BUILDING_ID);

        try (var stmt = woConn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT DocStatus FROM C_Order WHERE C_Order_ID = '"
                             + BUILDING_ID + "' AND Parent_Order_ID IS NULL")) {
            assertTrue(rs.next(), "Master C_Order should exist");
            assertEquals("CO", rs.getString("DocStatus"),
                    "After promote, master DocStatus should be CO (frozen)");
        }
    }

    // ── W-PROMOTE-5: Correct AABB dims and parent-relative offsets ──

    @Test
    @Order(5)
    @DisplayName("W-PROMOTE-5: Promoted m_bom_line has correct AABB dims and parent-relative offsets")
    void w_promote_5_dims_and_offsets() throws Exception {
        // recall() returns name=bomId, so child_product_id = bomId (e.g. "ROOM_LI_01")
        // Check ROOM_LI_01 line under FLOOR_GF_GEN
        try (var stmt = bomConn.createStatement();
             var rs = stmt.executeQuery("""
                     SELECT allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                            dx, dy, dz
                     FROM m_bom_line
                     WHERE bom_id = 'FLOOR_GF_GEN' AND child_product_id = 'ROOM_LI_01'
                     """)) {
            assertTrue(rs.next(), "ROOM_LI_01 line should exist under FLOOR_GF_GEN");

            // Living Room: 5400×4200×3000 mm
            assertEquals(5400, rs.getInt("allocated_width_mm"), "width");
            assertEquals(4200, rs.getInt("allocated_depth_mm"), "depth");
            assertEquals(3000, rs.getInt("allocated_height_mm"), "height");

            // Parent-relative offset: ROOM starts at (0,0,0), FLOOR starts at (0,0,0) → delta = 0
            assertEquals(0.0, rs.getDouble("dx"), 0.001, "dx relative to parent");
            assertEquals(0.0, rs.getDouble("dy"), 0.001, "dy relative to parent");
            assertEquals(0.0, rs.getDouble("dz"), 0.001, "dz relative to parent");
        }

        // Check Kitchen — offset from FLOOR parent
        try (var stmt = bomConn.createStatement();
             var rs = stmt.executeQuery("""
                     SELECT allocated_width_mm, dx
                     FROM m_bom_line
                     WHERE bom_id = 'FLOOR_GF_GEN' AND child_product_id = 'ROOM_KT_01'
                     """)) {
            assertTrue(rs.next(), "ROOM_KT_01 line should exist under FLOOR_GF_GEN");
            assertEquals(3600, rs.getInt("allocated_width_mm"), "Kitchen width (9000-5400)");
            assertEquals(5.4, rs.getDouble("dx"), 0.001,
                    "Kitchen dx = (5400 - 0) / 1000 = 5.4m from parent");
        }
    }

    // ── W-PROMOTE-6: Dangles returned, not silently skipped ─────────

    @Test
    @Order(6)
    @DisplayName("W-PROMOTE-6: Promote with dangles returns dangle list (does not silently skip)")
    void w_promote_6_dangles_reported() throws Exception {
        // Set up a fresh building with an ITEM bbox referencing a non-existent product
        String dangleBldg = "DangleTestHouse";
        Connection woConn2 = DriverManager.getConnection("jdbc:sqlite::memory:");
        var field = DesignerAPIImpl.class.getDeclaredField("workOutputConns");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<String, Connection>) field.get(api);
        map.put(dangleBldg, woConn2);

        WorkOutputDAO woDao2 = new WorkOutputDAO(woConn2);
        woDao2.initSchema();
        woDao2.ensureMasterOrder(dangleBldg, "GENERATIVE", 9000, 7000, 3000);

        // Include an ITEM bbox with a product name that doesn't exist
        List<DesignBBox> bboxesWithDangle = List.of(
                new DesignBBox("BLDG_D1", "Dangle House", "BUILDING", null,
                        "IfcBuilding", null, null,
                        0, 0, 0, 9000, 7000, 3000),
                new DesignBBox("ITEM_GHOST", "NonExistentProduct", "ITEM", "FURNITURE",
                        null, "GF", "BLDG_D1",
                        1000, 1000, 0, 2000, 2000, 1000)
        );
        woDao2.save(dangleBldg, bboxesWithDangle, "dangle-test");

        // Approve (no-op validator, so this passes)
        woDao2.setMasterDocStatus(dangleBldg, "AP");

        // Promote — should fail with dangles
        PromoteRequest req = new PromoteRequest(
                dangleBldg, "test-user", null, "GENERATIVE", bboxesWithDangle);
        PromoteResponse resp = api.promote(req);

        assertFalse(resp.success(), "Promote should fail when dangles exist");
        assertFalse(resp.dangles().isEmpty(), "Dangles list must not be empty");
        assertTrue(resp.dangles().get(0).contains("ITEM_GHOST"),
                "Dangle should reference ITEM_GHOST, got: " + resp.dangles().get(0));

        woConn2.close();
    }
}
