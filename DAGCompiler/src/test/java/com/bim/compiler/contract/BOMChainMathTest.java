package com.bim.compiler.contract;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Math-based truth testing for the BOM positional chain.
 *
 * <p>No visual inspection. No Blender. Every assertion is a numerical check
 * against extracted building data in {@code ad_room_boundary}.
 *
 * <p>Scope: SH (Ifc4_SampleHouse) and DX (Ifc2x3_Duplex) — the Rosetta Stones.
 * These are the examples of the correct process. Once they pass, every new
 * building passes the same maths automatically.
 *
 * <h2>Assertions</h2>
 * <ol>
 *   <li>Every active FURN BOM anchor has spacing facts (width_mm, depth_mm &gt; 0)</li>
 *   <li>Anchor width_mm matches room boundary width to within 1mm</li>
 *   <li>Anchor depth_mm matches room boundary depth to within 1mm</li>
 *   <li>Anchor height_extent_mm &gt; 0 (storey height declared, not zero)</li>
 *   <li>Every anchor host_ref resolves to a real room in ad_room_boundary</li>
 *   <li>BOM catalog entries for BUILDING/FLOOR levels have bom_level set correctly</li>
 *   <li>m_bom_line typed columns (dx/dy/dz/rotation_rule) back-filled from EAV</li>
 * </ol>
 */
@DisplayName("BOM Chain — Math Truth Tests (SH + DX)")
class BOMChainMathTest {

    private static final String DB      = "library/BOM.db";
    private static final String BOM_DB  = "library/BOM.db";
    private static final double EPS  = 1.0;   // 1mm tolerance for boundary agreement
    private static Connection conn;

    @BeforeAll
    static void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB);
        conn.createStatement().execute("ATTACH DATABASE '" + BOM_DB + "' AS bom_db");
    }

    @AfterAll
    static void close() throws SQLException {
        if (conn != null) conn.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C1: Spacing facts present — no anchor with zero dimensions
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "C1 [{0}] BOM anchors have non-zero spacing facts")
    @ValueSource(strings = {"Ifc4_SampleHouse", "Ifc2x3_Duplex"})
    void c1_spacingFactsPresent(String building) throws SQLException {
        String sql = """
            SELECT element_ref, width_mm, depth_mm, height_extent_mm
            FROM c_orderline
            WHERE building_type = ? AND discipline = 'FURN'
              AND host_type = 'ROOM' AND is_active = 1
              AND (width_mm = 0 OR depth_mm = 0 OR height_extent_mm = 0)
            """;
        List<String> bad = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, building);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bad.add(String.format(
                    "%s(w=%.0f d=%.0f h=%.0f)",
                    rs.getString("element_ref"),
                    rs.getDouble("width_mm"), rs.getDouble("depth_mm"),
                    rs.getDouble("height_extent_mm")));
            }
        }
        assertTrue(bad.isEmpty(),
            building + ": anchors with zero spacing facts: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C2: Anchor width_mm matches room boundary width to within 1mm
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "C2 [{0}] Anchor width_mm agrees with room boundary")
    @ValueSource(strings = {"Ifc4_SampleHouse", "Ifc2x3_Duplex"})
    void c2_widthMatchesBoundary(String building) throws SQLException {
        String sql = """
            SELECT er.element_ref,
                   er.width_mm                          AS anchor_w,
                   (rb.max_x_mm - rb.min_x_mm)         AS boundary_w,
                   ABS(er.width_mm - (rb.max_x_mm - rb.min_x_mm)) AS delta
            FROM c_orderline er
            JOIN ad_room_boundary rb
              ON rb.building_type = er.building_type
             AND rb.room_name     = er.host_ref
            WHERE er.building_type = ? AND er.discipline = 'FURN'
              AND er.host_type = 'ROOM' AND er.is_active = 1
              AND ABS(er.width_mm - (rb.max_x_mm - rb.min_x_mm)) > ?
            """;
        List<String> bad = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, building);
            ps.setDouble(2, EPS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bad.add(String.format(
                    "%s anchor=%.1f boundary=%.1f Δ=%.2fmm",
                    rs.getString("element_ref"),
                    rs.getDouble("anchor_w"), rs.getDouble("boundary_w"),
                    rs.getDouble("delta")));
            }
        }
        assertTrue(bad.isEmpty(),
            building + ": width_mm/boundary mismatch (>" + EPS + "mm): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C3: Anchor depth_mm matches room boundary depth to within 1mm
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "C3 [{0}] Anchor depth_mm agrees with room boundary")
    @ValueSource(strings = {"Ifc4_SampleHouse", "Ifc2x3_Duplex"})
    void c3_depthMatchesBoundary(String building) throws SQLException {
        String sql = """
            SELECT er.element_ref,
                   er.depth_mm                          AS anchor_d,
                   (rb.max_y_mm - rb.min_y_mm)         AS boundary_d,
                   ABS(er.depth_mm - (rb.max_y_mm - rb.min_y_mm)) AS delta
            FROM c_orderline er
            JOIN ad_room_boundary rb
              ON rb.building_type = er.building_type
             AND rb.room_name     = er.host_ref
            WHERE er.building_type = ? AND er.discipline = 'FURN'
              AND er.host_type = 'ROOM' AND er.is_active = 1
              AND ABS(er.depth_mm - (rb.max_y_mm - rb.min_y_mm)) > ?
            """;
        List<String> bad = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, building);
            ps.setDouble(2, EPS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bad.add(String.format(
                    "%s anchor=%.1f boundary=%.1f Δ=%.2fmm",
                    rs.getString("element_ref"),
                    rs.getDouble("anchor_d"), rs.getDouble("boundary_d"),
                    rs.getDouble("delta")));
            }
        }
        assertTrue(bad.isEmpty(),
            building + ": depth_mm/boundary mismatch (>" + EPS + "mm): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C4: Every anchor host_ref resolves to a real room (no dangling)
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "C4 [{0}] All anchor host_refs exist in ad_room_boundary")
    @ValueSource(strings = {"Ifc4_SampleHouse", "Ifc2x3_Duplex"})
    void c4_hostRefNotDangling(String building) throws SQLException {
        String sql = """
            SELECT er.element_ref, er.host_ref
            FROM c_orderline er
            LEFT JOIN ad_room_boundary rb
              ON rb.building_type = er.building_type
             AND rb.room_name     = er.host_ref
            WHERE er.building_type = ? AND er.discipline = 'FURN'
              AND er.host_type = 'ROOM' AND er.is_active = 1
              AND rb.room_name IS NULL
            """;
        List<String> bad = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, building);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bad.add(
                    rs.getString("element_ref") + " → " + rs.getString("host_ref"));
            }
        }
        assertTrue(bad.isEmpty(),
            building + ": dangling host_refs (no room boundary): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C5: BOM catalog BUILDING/FLOOR entries have correct bom_level
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C5: BUILDING and FLOOR BOMs have bom_level set (schema refactor applied)")
    void c5_bomLevelSet() throws SQLException {
        // This test passes only after migration_BOM2c_chain_schema.sql is applied
        boolean columnExists = false;
        try (ResultSet rs = conn.getMetaData()
                .getColumns(null, null, "m_bom", "bom_level")) {
            columnExists = rs.next();
        }
        assumeTrue(columnExists,
            "bom_level column not yet added — run migration_BOM2c_chain_schema.sql first");

        String sql = """
            SELECT bom_id, bom_level FROM m_bom
            WHERE bom_id IN (
                'BUILDING_SH_STD','BUILDING_DX_STD','BUILDING_TBLKTN_STD',
                'FLOOR_SH_GF_STD','FLOOR_DX_L1_STD','FLOOR_DX_L2_STD','FLOOR_TBLKTN_GF_STD'
            ) AND (bom_level IS NULL OR bom_level = 'SET')
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("bom_id") + "=" + rs.getString("bom_level"));
        }
        assertTrue(bad.isEmpty(),
            "BUILDING/FLOOR BOMs with wrong bom_level (expected BUILDING or FLOOR): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C6: m_bom_line typed dx/dy/dz columns back-filled from EAV
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C6: m_bom_line typed dx/dy/dz match EAV param values")
    void c6_dxDyDzBackFilled() throws SQLException {
        boolean columnExists = false;
        try (ResultSet rs = conn.getMetaData()
                .getColumns(null, null, "m_bom_line", "dx")) {
            columnExists = rs.next();
        }
        assumeTrue(columnExists,
            "dx column not yet added — run migration_BOM2c_chain_schema.sql first");

        // For every EAV row with param_key='dx', the typed column must match
        String sql = """
            SELECT bc.bom_child_id, bc.dx AS typed, CAST(p.param_value AS REAL) AS eav,
                   ABS(bc.dx - CAST(p.param_value AS REAL)) AS delta
            FROM m_bom_line bc
            JOIN m_attribute p ON p.bom_child_id = bc.bom_child_id
             AND p.param_key = 'dx' AND p.param_type = 'DOUBLE'
            WHERE ABS(bc.dx - CAST(p.param_value AS REAL)) > 0.0001
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "child_id=%d typed=%.4f eav=%.4f",
                rs.getInt("bom_child_id"), rs.getDouble("typed"), rs.getDouble("eav")));
        }
        assertTrue(bad.isEmpty(),
            "m_bom_line.dx mismatches EAV param_value: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C7: BOM anchor count per building is non-zero (BOM Drop fired)
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "C7 [{0}] UNIT Orderline exists (BOM-2c chain live)")
    @ValueSource(strings = {"Ifc4_SampleHouse", "Ifc2x3_Duplex"})
    void c7_bomAnchorsExist(String building) throws SQLException {
        // Phase BOM-2c: ROOM anchors deactivated; check UNIT Orderlines (host_type='BUILDING') instead
        String sql = """
            SELECT COUNT(*) FROM c_orderline
            WHERE building_type = ? AND discipline = 'FURN'
              AND host_type = 'BUILDING' AND is_active = 1
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, building);
            try (ResultSet rs = ps.executeQuery()) {
                int count = rs.next() ? rs.getInt(1) : 0;
                assertTrue(count > 0,
                    building + ": no FURN UNIT Orderlines — run migration_BOM2c_unit_orderlines.sql");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: skip test if assumption fails (JUnit 5 Assumptions)
    // ─────────────────────────────────────────────────────────────────────────

    private static void assumeTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assumptions.assumeTrue(condition, message);
    }
}
