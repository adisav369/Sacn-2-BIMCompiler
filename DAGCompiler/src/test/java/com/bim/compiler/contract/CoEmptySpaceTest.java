package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CO_EmptySpace witness tests — verify that the compilation pipeline populates
 * the co_empty_space (header) and co_empty_space_line (acceptance detail) tables.
 *
 * <h2>W-CO_EMPTY-1 — Header exists with CO status after proof</h2>
 * <p>After SH compilation, {@code co_empty_space} must have at least 1 row with
 * {@code doc_status = 'CO'} and {@code is_available = 0} (proven placement).
 *
 * <h2>W-CO_EMPTY-2 — Acceptance line references UNIT BOM</h2>
 * <p>After SH compilation, {@code co_empty_space_line} must have at least 1 row
 * whose {@code bom_id} starts with 'UNIT_SH' (the SH unit BOM).
 *
 * <h2>W-CO_EMPTY-3 — Quality gate invariant</h2>
 * <p>is_available = 0 implies doc_status = 'CO'. No shortcut to available=0
 * without completing the proof stage.
 *
 * <h2>W-CO_EMPTY-4 — Per-storey lines with storey names</h2>
 * <p>SH co_empty_space_line has level-1 children with storey column matching
 * spatial_structure names.
 */
@DisplayName("CO_EmptySpace — W-CO_EMPTY witnesses")
class CoEmptySpaceTest {

    private static final String SH_DB = "DAGCompiler/lib/output/ifc4_sample_house.db";

    @Test
    @DisplayName("W-CO_EMPTY-1: SH co_empty_space has CO status with is_available=0, AABB > 0")
    void sh_coEmptySpace_headerExists() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT co_emptyspace_id, aabb_width_mm, aabb_depth_mm, aabb_height_mm, " +
                 "is_available, doc_status " +
                 "FROM co_empty_space WHERE doc_status = 'CO' AND is_available = 0")) {
            assertTrue(rs.next(), "co_empty_space must have at least 1 proven (CO, is_available=0) row for SH");
            double w = rs.getDouble("aabb_width_mm");
            double d = rs.getDouble("aabb_depth_mm");
            double h = rs.getDouble("aabb_height_mm");
            assertTrue(w > 0, "AABB width must be > 0, got " + w);
            assertTrue(d > 0, "AABB depth must be > 0, got " + d);
            assertTrue(h > 0, "AABB height must be > 0, got " + h);
        }
    }

    @Test
    @DisplayName("W-CO_EMPTY-2: SH co_empty_space_line references UNIT_SH BOM (CO header)")
    void sh_coEmptySpaceLine_unitBomRef() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT l.bom_id FROM co_empty_space_line l " +
                 "JOIN co_empty_space h ON h.co_emptyspace_id = l.co_emptyspace_id " +
                 "WHERE h.doc_status = 'CO'")) {
            assertTrue(rs.next(), "co_empty_space_line must have at least 1 row for SH (CO header)");
            String bomId = rs.getString("bom_id");
            assertNotNull(bomId, "bom_id must not be null");
            assertTrue(bomId.startsWith("UNIT_SH"),
                "bom_id must reference SH UNIT BOM, got: " + bomId);
        }
    }

    @Test
    @DisplayName("W-CO_EMPTY-3: is_available=0 implies doc_status='CO' (quality gate invariant)")
    void sh_qualityGateInvariant() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT co_emptyspace_id, doc_status FROM co_empty_space " +
                 "WHERE is_available = 0 AND doc_status != 'CO'")) {
            assertFalse(rs.next(),
                "Quality gate violation: is_available=0 but doc_status != 'CO'. " +
                "Only ProveStage may set is_available=0 (via setComplete).");
        }
    }

    @Test
    @DisplayName("W-CO_EMPTY-4: SH per-storey lines exist with storey names")
    void sh_perStoreyLinesExist() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT l.bom_level, l.storey, l.bom_line_role, l.before_z_mm, l.next_z_mm " +
                 "FROM co_empty_space_line l " +
                 "JOIN co_empty_space h ON h.co_emptyspace_id = l.co_emptyspace_id " +
                 "WHERE h.doc_status = 'CO' AND l.bom_level = 1")) {
            int lineCount = 0;
            boolean hasStoreyName = false;
            while (rs.next()) {
                lineCount++;
                String storey = rs.getString("storey");
                if (storey != null && !storey.isBlank()) {
                    hasStoreyName = true;
                }
            }
            assertTrue(lineCount >= 2,
                "SH must have ≥2 level-1 children (GROUND_SLAB + GROUND_FLOOR + ROOF). Got: " + lineCount);
            assertTrue(hasStoreyName,
                "At least one level-1 line must have a non-null storey name (e.g. 'Ground Floor')");
        }
    }
}
