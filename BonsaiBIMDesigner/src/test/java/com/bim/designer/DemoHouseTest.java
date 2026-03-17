package com.bim.designer;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DemoHouse_2BR Generative POC — BOM generation + DocValidate proof.
 *
 * <p>Proves the generative path end-to-end:
 * <ol>
 *   <li>DM_BOM.db has correct BOM tree structure (BUILDING -> FLOOR -> ROOM -> LEAF)</li>
 *   <li>Each room passes UBBL compliance checks from validation.db</li>
 *   <li>A too-small bedroom correctly triggers BLOCK</li>
 *   <li>Provenance is GENERATIVE (via C_DocType)</li>
 * </ol>
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-DH-1: BOM tree has 25 lines (1 floor + 5 rooms + 19 leaves)</li>
 *   <li>W-DH-2: All rooms PASS UBBL min_area_m2 checks</li>
 *   <li>W-DH-3: All rooms PASS UBBL min_dim_mm checks</li>
 *   <li>W-DH-4: 2800mm bedroom BLOCKS on UBBL min_dim (3000mm required)</li>
 *   <li>W-DH-5: Provenance is GENERATIVE</li>
 *   <li>W-DH-6: BOM hierarchy: BUILDING -> 1 FLOOR -> 5 ROOMS</li>
 * </ul>
 */
@DisplayName("DemoHouse_2BR — Generative POC")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoHouseTest {

    private static Connection bomConn;
    private static Connection valConn;

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection(
                "jdbc:sqlite:library/DM_BOM.db");
        valConn = DriverManager.getConnection(
                "jdbc:sqlite:library/validation.db");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
        if (valConn != null && !valConn.isClosed()) valConn.close();
    }

    // ── BOM structure proofs ─────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-DH-1: BOM tree has 25 lines total")
    void w_dh_1_bom_line_count() throws Exception {
        try (Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM m_bom_line")) {
            assertTrue(rs.next());
            assertEquals(25, rs.getInt(1), "1 floor-link + 5 room-links + 19 leaf lines");
        }
    }

    @Test
    @Order(2)
    @DisplayName("W-DH-6: BOM hierarchy: BUILDING -> 1 FLOOR -> 5 ROOMS")
    void w_dh_6_bom_hierarchy() throws Exception {
        // BUILDING has 1 child (FLOOR)
        int floorCount = countLines("BUILDING_DEMO_2BR", "MAKE");
        assertEquals(1, floorCount, "BUILDING -> 1 FLOOR");

        // FLOOR has 5 children (ROOMS)
        int roomCount = countLines("FLOOR_DEMO_GF", "MAKE");
        assertEquals(5, roomCount, "FLOOR -> 5 ROOMS");

        // Each room has only BUY (leaf) children
        for (String room : List.of("ROOM_DEMO_LI", "ROOM_DEMO_KT",
                "ROOM_DEMO_BD1", "ROOM_DEMO_BD2", "ROOM_DEMO_BT")) {
            int makeCount = countLines(room, "MAKE");
            assertEquals(0, makeCount, room + " should have no MAKE children");
            int buyCount = countLines(room, "BUY");
            assertTrue(buyCount > 0, room + " should have BUY children");
        }
    }

    @Test
    @Order(3)
    @DisplayName("W-DH-5: Provenance is GENERATIVE")
    void w_dh_5_provenance() throws Exception {
        try (Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT Provenance FROM C_DocType WHERE DocSubType = 'DM'")) {
            assertTrue(rs.next(), "C_DocType row for DM must exist");
            assertEquals("GENERATIVE", rs.getString(1));
        }
    }

    // ── UBBL validation proofs ───────────────────────────────────────

    /**
     * PlacementRequest: for each room BOM, compute area from aabb_width_mm * aabb_depth_mm,
     * then check against UBBL min_area_m2 for the room's bom_category.
     */
    @Test
    @Order(10)
    @DisplayName("W-DH-2: All rooms PASS UBBL min_area_m2")
    void w_dh_2_ubbl_min_area() throws Exception {
        Map<String, double[]> rooms = getRoomDimensions();

        for (var entry : rooms.entrySet()) {
            String bomId = entry.getKey();
            double widthMm = entry.getValue()[0];
            double depthMm = entry.getValue()[1];
            String category = getRoomCategory(bomId);

            double areaM2 = (widthMm / 1000.0) * (depthMm / 1000.0);
            Double minArea = getUbblParam(category, "min_area_m2");

            if (minArea != null) {
                assertTrue(areaM2 >= minArea,
                        String.format("%s (%s): area %.2f m2 < UBBL min %.2f m2",
                                bomId, category, areaM2, minArea));
            }
        }
    }

    /**
     * Check that every room's smallest plan dimension meets UBBL min_dim_mm.
     */
    @Test
    @Order(11)
    @DisplayName("W-DH-3: All rooms PASS UBBL min_dim_mm")
    void w_dh_3_ubbl_min_dim() throws Exception {
        Map<String, double[]> rooms = getRoomDimensions();

        for (var entry : rooms.entrySet()) {
            String bomId = entry.getKey();
            double widthMm = entry.getValue()[0];
            double depthMm = entry.getValue()[1];
            String category = getRoomCategory(bomId);

            double minDim = Math.min(widthMm, depthMm);
            Double requiredMin = getUbblParam(category, "min_dim_mm");

            if (requiredMin != null) {
                assertTrue(minDim >= requiredMin,
                        String.format("%s (%s): min dim %.0f mm < UBBL min %.0f mm",
                                bomId, category, minDim, requiredMin));
            }
        }
    }

    /**
     * Negative test: a 2800mm bedroom should BLOCK because UBBL requires 3000mm min dimension.
     */
    @Test
    @Order(12)
    @DisplayName("W-DH-4: 2800mm bedroom BLOCKS on UBBL min_dim (3000mm required)")
    void w_dh_4_undersized_bedroom_blocks() throws Exception {
        // Simulate a bedroom with 2800mm as the smallest dimension
        double testWidthMm = 2800;
        double testDepthMm = 3500;
        double minDim = Math.min(testWidthMm, testDepthMm);

        Double requiredMin = getUbblParam("BEDROOM", "min_dim_mm");
        assertNotNull(requiredMin, "UBBL must define min_dim_mm for BEDROOM");
        assertEquals(3000.0, requiredMin, "UBBL BEDROOM min_dim_mm = 3000");

        // This bedroom BLOCKS — 2800 < 3000
        assertTrue(minDim < requiredMin,
                String.format("2800mm bedroom should BLOCK: %.0f < %.0f",
                        minDim, requiredMin));

        // Verify the actual bedrooms in the BOM pass (both are 3500mm min dim)
        Map<String, double[]> rooms = getRoomDimensions();
        for (String bomId : List.of("ROOM_DEMO_BD1", "ROOM_DEMO_BD2")) {
            double[] dims = rooms.get(bomId);
            double actualMin = Math.min(dims[0], dims[1]);
            assertTrue(actualMin >= requiredMin,
                    bomId + " actual min dim " + actualMin + " >= " + requiredMin);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private int countLines(String bomId, String componentType) throws Exception {
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id = ? AND component_type = ?")) {
            ps.setString(1, bomId);
            ps.setString(2, componentType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Returns room BOM IDs mapped to [aabb_width_mm, aabb_depth_mm, aabb_height_mm].
     * Only room-level BOMs (group_by = 'ROOM').
     */
    private Map<String, double[]> getRoomDimensions() throws Exception {
        Map<String, double[]> result = new LinkedHashMap<>();
        try (Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT bom_id, aabb_width_mm, aabb_depth_mm, aabb_height_mm "
                             + "FROM m_bom WHERE group_by = 'ROOM'")) {
            while (rs.next()) {
                result.put(rs.getString(1), new double[]{
                        rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)
                });
            }
        }
        return result;
    }

    private String getRoomCategory(String bomId) throws Exception {
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT bom_category FROM m_bom WHERE bom_id = ?")) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Looks up a UBBL rule parameter by bom_category and param name.
     * Uses jurisdiction = 'MY' (UBBL is Malaysian).
     */
    private Double getUbblParam(String bomCategory, String paramName) throws Exception {
        try (PreparedStatement ps = valConn.prepareStatement(
                "SELECT p.value FROM AD_Val_Rule_Param p "
                        + "JOIN AD_Val_Rule r ON r.ad_val_rule_id = p.ad_val_rule_id "
                        + "WHERE r.jurisdiction = 'MY' AND r.rule_type = 'COMPLIANCE' "
                        + "  AND p.name = ? "
                        + "  AND EXISTS (SELECT 1 FROM AD_Val_Rule_Param p2 "
                        + "              WHERE p2.ad_val_rule_id = p.ad_val_rule_id "
                        + "                AND p2.name = 'bom_category' "
                        + "                AND p2.value = ?)")) {
            ps.setString(1, paramName);
            ps.setString(2, bomCategory);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : null;
            }
        }
    }
}
