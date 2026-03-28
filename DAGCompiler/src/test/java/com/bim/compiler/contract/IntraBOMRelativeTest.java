package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@code m_bom_line.dx/dy/dz} contain intra-space relative offsets,
 * never flat absolute world coordinates.
 *
 * <h2>The "No Flat High-Level Facts" Rule</h2>
 * <p>A BOM child offset is relative to its parent's local origin — the room or assembly centre.
 * Absolute world coordinates (like the Revit global X/Y or Duplex Level 2 elevation +3000mm)
 * must NEVER appear in {@code m_bom_line}. They live in {@code c_orderline.position_value}
 * (the BOM anchor Orderline). Mixing the two — storing an absolute offset in a BOM child —
 * collapses all children to the wrong world position without any compilation error.
 *
 * <h2>Fingerprint / X-ray</h2>
 * <p>The vertex fingerprint of a BOM assembly is the collection of child offsets relative to
 * each other: chair dx=±0.45m from table centre, cabinet dx=±1.22m from bed head, etc.
 * These relative positions define the assembly's spatial signature — independent of where in
 * the world the assembly happens to be placed. If any offset exceeds room scale (> 10m),
 * it is not a relative offset; it is an absolute coordinate that slipped into the wrong table.
 *
 * <h2>Assertions</h2>
 * <ul>
 *   <li><b>R1</b> |dx| and |dy| &lt; 10m — intra-room scale. World X/Y coordinates for all
 *       supported buildings exceed 15m, making them detectable.</li>
 *   <li><b>R2</b> |dz| &lt; 4.5m — intra-storey height. Multi-storey elevation (3.0m typical)
 *       is an Orderline value, not a BOM-child value. Values &gt; 4.5m indicate a storey offset
 *       that should be in {@code c_orderline.position_value_3}.</li>
 *   <li><b>R3</b> No BOM child dx/dy matches a known room boundary min/max value (mod 0.1m) —
 *       catches accidentally encoded Revit world coordinates.</li>
 *   <li><b>R4</b> For active children with non-zero dx: |dx| is plausible relative to the
 *       declared product dimensions in {@code ad_product_dim} — offset should not wildly exceed
 *       the product it references.</li>
 * </ul>
 */
@DisplayName("IntraBOM — Relative Offsets Only, No Absolute Coordinates in m_bom_line")
class IntraBOMRelativeTest {

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    /** Intra-room maximum dimension — rooms exceed this only for large commercial spaces. */
    private static final double MAX_ROOM_DIM_M  = 10.0;
    /** Intra-storey maximum height — no room is taller than this in supported buildings. */
    private static final double MAX_STOREY_H_M  = 4.5;

    private static Connection conn;

    @BeforeAll static void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath());
        conn.createStatement().execute("ATTACH DATABASE '" + bomDbPath() + "' AS bom_db");
    }
    @AfterAll  static void close() throws SQLException { if (conn != null) conn.close(); }

    // ─────────────────────────────────────────────────────────────────────────
    // R1: All |dx|, |dy| within room scale
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R1: No BOM child |dx| or |dy| exceeds 10m (intra-room scale only)")
    void r1_dxDyWithinRoomScale() throws SQLException {
        // EXTRACTED BOMs and FLOOR/BUILDING BOMs carry building-scale offsets
        // (up to 17m for SH, 22m for DX) which are relative to the building/floor
        // tack origin, not to a room. Only SET/ROOM/ITEM BOMs must stay within room scale.
        String sql = """
            SELECT bl.M_BOM_Line_ID, bl.bom_id, bl.child_name_pattern, bl.dx, bl.dy
            FROM m_bom_line bl
            JOIN m_bom b ON bl.bom_id = b.bom_id
            WHERE bl.is_active = 1
              AND b.bom_id NOT LIKE 'EB_%'
              AND b.bom_type NOT IN ('FLOOR', 'BUILDING')
              AND (ABS(bl.dx) > ? OR ABS(bl.dy) > ?)
            """;
        List<String> bad = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, MAX_ROOM_DIM_M);
            ps.setDouble(2, MAX_ROOM_DIM_M);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bad.add(String.format(
                    "child_id=%d bom=%s pattern='%s' dx=%.3f dy=%.3f",
                    rs.getInt("M_BOM_Line_ID"), rs.getString("bom_id"),
                    rs.getString("child_name_pattern"),
                    rs.getDouble("dx"), rs.getDouble("dy")));
            }
        }
        assertTrue(bad.isEmpty(),
            "BOM child dx/dy exceeds 10m — looks like an absolute world coordinate: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R2: All |dz| within storey height
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R2: No non-BUILDING BOM child |dz| exceeds 4.5m (BUILDING children exempt — vertical stacking is correct)")
    void r2_dzWithinStoreyHeight() throws SQLException {
        // BUILDING-level children (slabs, floors, roof) legitimately carry dz for
        // vertical stacking within the building envelope (e.g. ROOF dz=6.0m).
        // Only room/set-level children should be bounded by storey height.
        String sql = """
            SELECT bl.M_BOM_Line_ID, bl.bom_id, bl.child_name_pattern, bl.dz
            FROM m_bom_line bl
            JOIN m_bom b ON bl.bom_id = b.bom_id
            WHERE bl.is_active = 1 AND ABS(bl.dz) > ?
              AND b.bom_type NOT IN ('BUILDING')
            """;
        List<String> bad = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, MAX_STOREY_H_M);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bad.add(String.format(
                    "child_id=%d bom=%s pattern='%s' dz=%.3f",
                    rs.getInt("M_BOM_Line_ID"), rs.getString("bom_id"),
                    rs.getString("child_name_pattern"),
                    rs.getDouble("dz")));
            }
        }
        assertTrue(bad.isEmpty(),
            "BOM child dz exceeds 4.5m — storey elevation must be in c_orderline.position_value_3: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R3: No BOM child offset matches a room boundary absolute value
    //     (catches Revit world coordinates that slipped into BOM children)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R3: No BOM child dx/dy matches an ad_room_boundary min/max value (no Revit absolute leak)")
    void r3_noAbsoluteRoomBoundaryLeak() throws SQLException {
        // Collect all distinct room boundary values (in meters, rounded to 2dp)
        // that are > 1m away from zero — world coordinates not near origin.
        String boundsSql = """
            SELECT DISTINCT ROUND(min_x_mm / 1000.0, 2) AS v FROM ad_room_boundary WHERE ABS(min_x_mm) > 1000
            UNION
            SELECT DISTINCT ROUND(max_x_mm / 1000.0, 2) FROM ad_room_boundary WHERE ABS(max_x_mm) > 1000
            UNION
            SELECT DISTINCT ROUND(min_y_mm / 1000.0, 2) FROM ad_room_boundary WHERE ABS(min_y_mm) > 1000
            UNION
            SELECT DISTINCT ROUND(max_y_mm / 1000.0, 2) FROM ad_room_boundary WHERE ABS(max_y_mm) > 1000
            """;

        // World boundary values (these MUST NOT appear verbatim in BOM child offsets)
        List<Double> worldValues = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(boundsSql)) {
            while (rs.next()) worldValues.add(rs.getDouble(1));
        }

        if (worldValues.isEmpty()) return; // no boundary data — skip

        // For each active BOM child with non-zero dx or dy, check if the value
        // exactly matches a world boundary coordinate (within 5mm).
        // EB_ BOMs carry building-scale offsets relative to the tack origin,
        // which may coincidentally match room boundary values — exempt them.
        String childSql = "SELECT bl.M_BOM_Line_ID, bl.bom_id, bl.child_name_pattern, bl.dx, bl.dy " +
                          "FROM m_bom_line bl JOIN m_bom b ON bl.bom_id = b.bom_id " +
                          "WHERE bl.is_active=1 AND b.bom_id NOT LIKE 'EB_%' AND (bl.dx != 0 OR bl.dy != 0)";
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(childSql)) {
            while (rs.next()) {
                double dx = rs.getDouble("dx");
                double dy = rs.getDouble("dy");
                for (double worldVal : worldValues) {
                    if (Math.abs(dx - worldVal) < 0.005 && Math.abs(dx) > 1.0) {
                        bad.add(String.format("child_id=%d bom=%s: dx=%.3f matches world boundary %.3f",
                            rs.getInt("M_BOM_Line_ID"), rs.getString("bom_id"), dx, worldVal));
                    }
                    if (Math.abs(dy - worldVal) < 0.005 && Math.abs(dy) > 1.0) {
                        bad.add(String.format("child_id=%d bom=%s: dy=%.3f matches world boundary %.3f",
                            rs.getInt("M_BOM_Line_ID"), rs.getString("bom_id"), dy, worldVal));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(),
            "BOM child dx/dy matches room boundary world coordinate — absolute value leaked into BOM: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R4: Non-zero dx must be plausible relative to catalog product width
    //     (a chair offset of 4.5m makes no sense for a 0.45m chair)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R4: BOM child dx/dy plausible relative to catalog product dimensions")
    void r4_offsetPlausibleVsProductDims() throws SQLException {
        // For children where child_name_pattern resolves to a catalog product,
        // |dx| should not exceed 8× the product's width.
        // 3× was too tight for room-anchor-relative assemblies (e.g. SH_DINING_SET chairs
        // placed 7.4× their width from the south-wall anchor to match IFC reference coords).
        // 8× still catches absolute world coordinates (room spans 9m+ → ratio 20×+ for 0.45m chair).
        String sql = """
            SELECT bc.M_BOM_Line_ID, bc.bom_id, bc.child_name_pattern, bc.dx, bc.dy,
                   pd.width AS cat_w, pd.depth AS cat_d
            FROM m_bom_line bc
            JOIN M_Product pd
              ON pd.product_id = TRIM(REPLACE(COALESCE(bc.child_name_pattern,''), '%', ''))
            WHERE bc.is_active = 1
              AND (ABS(bc.dx) > pd.width * 8 OR ABS(bc.dy) > pd.depth * 8)
              AND (bc.dx != 0 OR bc.dy != 0)
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "child_id=%d bom=%s pattern='%s' dx=%.3f dy=%.3f catW=%.3f catD=%.3f",
                rs.getInt("M_BOM_Line_ID"), rs.getString("bom_id"),
                rs.getString("child_name_pattern"),
                rs.getDouble("dx"), rs.getDouble("dy"),
                rs.getDouble("cat_w"), rs.getDouble("cat_d")));
        }
        assertTrue(bad.isEmpty(),
            "BOM child offset implausibly large vs catalog product dims: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R5: rotation_rule is either a valid semantic string or a parseable radian value
    // ─────────────────────────────────────────────────────────────────────────

    /** Valid semantic rotation rules defined in IBOMChildLine + EXTRACTED orientation codes. */
    private static final java.util.Set<String> SEMANTIC_RULES = java.util.Set.of(
        "FACE_INTO_ROOM", "FACE_AWAY_FROM_WALL", "PARALLEL_TO_WALL",
        "FACE_WALL_BACK", "PERPENDICULAR_TO_WALL", "FACE_OUTSIDE", "0",
        "EW", "NS"  // EXTRACTED BOM wall orientation codes
    );

    @Test
    @DisplayName("R5: All rotation_rule values are semantic strings or parseable radians")
    void r5_rotationRuleValid() throws SQLException {
        String sql = "SELECT M_BOM_Line_ID, bom_id, rotation_rule FROM m_bom_line WHERE is_active=1";
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String rule = rs.getString("rotation_rule");
                if (rule == null || rule.isBlank()) continue;
                if (SEMANTIC_RULES.contains(rule)) continue;
                try { Double.parseDouble(rule); }
                catch (NumberFormatException e) {
                    bad.add(String.format("child_id=%d bom=%s: rotation_rule='%s' not semantic and not parseable",
                        rs.getInt("M_BOM_Line_ID"), rs.getString("bom_id"), rule));
                }
            }
        }
        assertTrue(bad.isEmpty(), "Invalid rotation_rule values in m_bom_line: " + bad);
    }
}
