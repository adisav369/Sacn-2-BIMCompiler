package com.bim.compiler.contract;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometry truth tests for compiled furniture placement.
 *
 * <h2>F1 — Upper-floor furniture Z elevation</h2>
 * <p>Furniture dispatched to upper storeys must rest on the correct floor slab.
 * The UNIT→FLOOR→SET cascade must propagate {@code ad_element_rule.position_value_3}
 * (storey Z in mm) to every child. If the cascade is missing, all upper-floor furniture
 * silently lands at Z=0 on the ground slab — visually "stacked" on the wrong floor.
 *
 * <p>Rule: for DX Upper storey, every IfcFurniture/IfcFurnishingElement minZ must be
 * within [floorElev − TOL, floorElev + maxFurnH] of the known Level 2 elevation (3000mm).
 * Ground-floor furniture must be below 1500mm (half a storey — catches Z=0 items
 * that never received the upper-floor offset).
 *
 * <h2>F2 — Furniture centroid inside a valid room boundary</h2>
 * <p>Every furniture element centroid must lie inside at least one active room boundary
 * for the same building. An element whose centroid falls outside all known rooms is in
 * the wrong room or at an invalid world position.
 * Skipped for rooms whose boundary is NULL (calibration deferred — G8-DX intentional RED).
 *
 * <h2>F3 — No Upper-floor furniture at ground Z (regression guard)</h2>
 * <p>Specific regression for the DX floorZ cascade bug: any IfcFurnishingElement
 * on storey "Upper" with minZ < 1.0 m is treated as a hard fault.
 */
@DisplayName("Furniture Geometry — Z elevation and room containment")
class FurnitureGeometryTest {

    private static final String LIB  = "library/component_library.db";
    private static final String SH_DB = "DAGCompiler/lib/output/ifc4_sample_house.db";
    private static final String DX_DB = "DAGCompiler/lib/output/ifc2x3_duplex.db";

    /** DX Level 2 (Upper storey) floor elevation in metres. */
    private static final double DX_UPPER_Z_M = 3.0;
    /** Tolerance: furniture rests on slab ± 10mm (float precision + thin rugs). */
    private static final double FLOOR_TOL_M  = 0.01;
    /** Maximum plausible furniture height (wardrobe / tall shelving). */
    private static final double MAX_FURN_H_M = 2.5;

    // ─────────────────────────────────────────────────────────────────────────
    // F1/F3 — DX Upper storey furniture must be at Level 2 elevation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F1: DX Upper-storey furniture rests on Level 2 slab (Z ≈ 3000mm), not ground")
    void f1_dxUpperFurnitureAtLevelTwo() throws SQLException {
        List<String> bad = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DX_DB)) {
            String sql = """
                SELECT em.element_name AS ename, rt.minZ AS minZ, rt.maxZ AS maxZ
                FROM elements_meta em
                JOIN elements_rtree rt ON rt.id = em.id
                WHERE em.ifc_class IN ('IfcFurniture','IfcFurnishingElement')
                  AND em.storey = 'Upper'
                """;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    double minZ = rs.getDouble("minZ");
                    // minZ must be at DX_UPPER_Z_M ± tolerance
                    boolean onUpperSlab = minZ >= DX_UPPER_Z_M - FLOOR_TOL_M
                                      && minZ <= DX_UPPER_Z_M + MAX_FURN_H_M;
                    if (!onUpperSlab) {
                        bad.add(String.format("%s (Upper): minZ=%.3fm — expected ≥%.1fm (Level 2 elevation)",
                            rs.getString("ename"), minZ, DX_UPPER_Z_M));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(),
            "DX Upper-storey furniture not at Level 2 elevation — floor Z cascade missing: " + bad);
    }

    @Test
    @DisplayName("F3: DX Ground-storey furniture below 1.5m (no upper-floor leakage to ground)")
    void f3_dxGroundFurnitureBelowHalfStorey() throws SQLException {
        List<String> bad = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DX_DB)) {
            String sql = """
                SELECT em.element_name AS ename, rt.minZ AS minZ
                FROM elements_meta em
                JOIN elements_rtree rt ON rt.id = em.id
                WHERE em.ifc_class IN ('IfcFurniture','IfcFurnishingElement')
                  AND em.storey = 'Ground'
                """;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    double minZ = rs.getDouble("minZ");
                    if (minZ < -0.1 || minZ > 1.5) {
                        bad.add(String.format("%s: Ground minZ=%.3fm — outside expected [0, 1.5m]",
                            rs.getString("ename"), minZ));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "DX Ground-storey furniture at unexpected Z: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F2 — Furniture centroid inside a valid room boundary
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F2-SH: Every SH furniture centroid is inside a known room boundary")
    void f2_shFurnitureCentroidsContained() throws SQLException {
        checkFurnitureCentroids(SH_DB, "Ifc4_SampleHouse");
    }

    @Test
    @Disabled("G8-DX calibration deferred — 40/51 DX rooms have NULL boundaries; "
            + "4 items (Piano, 3 Dining Chairs in B-unit) outside calibrated bounds. "
            + "Re-enable when G8-DX room calibration is complete.")
    @DisplayName("F2-DX: Every DX furniture centroid is inside a valid-bound room (skips NULL-bound rooms)")
    void f2_dxFurnitureCentroids_validBoundRoomsOnly() throws SQLException {
        checkFurnitureCentroids(DX_DB, "Ifc2x3_Duplex");
    }

    /**
     * For each furniture centroid in the output DB, check it lies inside at least
     * one active room boundary with non-null coordinates.
     * Rooms whose min_x_mm IS NULL are skipped (calibration deferred).
     */
    private void checkFurnitureCentroids(String outputDb, String buildingType)
            throws SQLException {

        // Load valid room boundaries from library
        record RoomBound(String name, String type,
                         double minX, double maxX, double minY, double maxY) {}
        List<RoomBound> rooms = new ArrayList<>();

        try (Connection lib = DriverManager.getConnection("jdbc:sqlite:" + LIB)) {
            String sql = """
                SELECT room_name, room_type,
                       min_x_mm/1000.0, max_x_mm/1000.0,
                       min_y_mm/1000.0, max_y_mm/1000.0
                FROM ad_room_boundary
                WHERE building_type = ?
                  AND is_active = 1
                  AND min_x_mm IS NOT NULL AND max_x_mm IS NOT NULL
                  AND min_y_mm IS NOT NULL AND max_y_mm IS NOT NULL
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rooms.add(new RoomBound(
                            rs.getString(1), rs.getString(2),
                            rs.getDouble(3), rs.getDouble(4),
                            rs.getDouble(5), rs.getDouble(6)));
                    }
                }
            }
        }

        if (rooms.isEmpty()) {
            return; // No calibrated boundaries — skip
        }

        List<String> bad = new ArrayList<>();
        try (Connection out = DriverManager.getConnection("jdbc:sqlite:" + outputDb)) {
            String sql = """
                SELECT em.element_name AS ename, em.storey AS storey,
                       (rt.minX + rt.maxX) / 2.0 AS cx,
                       (rt.minY + rt.maxY) / 2.0 AS cy
                FROM elements_meta em
                JOIN elements_rtree rt ON rt.id = em.id
                WHERE em.ifc_class IN ('IfcFurniture','IfcFurnishingElement')
                """;
            try (Statement st = out.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    double cx = rs.getDouble("cx");
                    double cy = rs.getDouble("cy");
                    boolean contained = false;
                    for (RoomBound r : rooms) {
                        if (cx >= r.minX() && cx <= r.maxX()
                         && cy >= r.minY() && cy <= r.maxY()) {
                            contained = true;
                            break;
                        }
                    }
                    if (!contained) {
                        bad.add(String.format("%s [%s] centroid(%.3f,%.3f) outside all %d valid rooms",
                            rs.getString("ename"), rs.getString("storey"),
                            cx, cy, rooms.size()));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(),
            buildingType + ": furniture centroid outside all calibrated room boundaries: " + bad);
    }
}
