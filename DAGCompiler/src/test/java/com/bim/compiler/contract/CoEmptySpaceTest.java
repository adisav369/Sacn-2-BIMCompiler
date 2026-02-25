package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CO_EmptySpace witness tests — verify that the compilation pipeline populates
 * the co_empty_space (header) and co_empty_space_line (acceptance detail) tables.
 *
 * <h2>W-CO_EMPTY-1 — Header exists with valid AABB</h2>
 * <p>After SH compilation, {@code co_empty_space} must have at least 1 row with
 * {@code is_available = 1} and all AABB dimensions &gt; 0.
 *
 * <h2>W-CO_EMPTY-2 — Acceptance line references UNIT BOM</h2>
 * <p>After SH compilation, {@code co_empty_space_line} must have at least 1 row
 * whose {@code bom_id} starts with 'UNIT_SH' (the SH unit BOM).
 */
@DisplayName("CO_EmptySpace — W-CO_EMPTY witnesses")
class CoEmptySpaceTest {

    private static final String SH_DB = "DAGCompiler/lib/output/ifc4_sample_house.db";

    @Test
    @DisplayName("W-CO_EMPTY-1: SH co_empty_space has 1 row with is_available=1, AABB > 0")
    void sh_coEmptySpace_headerExists() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT co_emptyspace_id, aabb_width_mm, aabb_depth_mm, aabb_height_mm, is_available " +
                 "FROM co_empty_space WHERE is_available = 1")) {
            assertTrue(rs.next(), "co_empty_space must have at least 1 available row for SH");
            double w = rs.getDouble("aabb_width_mm");
            double d = rs.getDouble("aabb_depth_mm");
            double h = rs.getDouble("aabb_height_mm");
            assertTrue(w > 0, "AABB width must be > 0, got " + w);
            assertTrue(d > 0, "AABB depth must be > 0, got " + d);
            assertTrue(h > 0, "AABB height must be > 0, got " + h);
        }
    }

    @Test
    @DisplayName("W-CO_EMPTY-2: SH co_empty_space_line references UNIT_SH BOM")
    void sh_coEmptySpaceLine_unitBomRef() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT l.bom_id FROM co_empty_space_line l " +
                 "JOIN co_empty_space h ON h.co_emptyspace_id = l.co_emptyspace_id " +
                 "WHERE h.is_available = 1")) {
            assertTrue(rs.next(), "co_empty_space_line must have at least 1 row for SH");
            String bomId = rs.getString("bom_id");
            assertNotNull(bomId, "bom_id must not be null");
            assertTrue(bomId.startsWith("UNIT_SH"),
                "bom_id must reference SH UNIT BOM, got: " + bomId);
        }
    }
}
