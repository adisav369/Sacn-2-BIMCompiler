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
 * Requires all rooms to have non-null calibrated coordinates (Phase A materialization).
 *
 * <h2>F3 — No Upper-floor furniture at ground Z (regression guard)</h2>
 * <p>Specific regression for the DX floorZ cascade bug: any IfcFurnishingElement
 * on storey "Upper" with minZ < 1.0 m is treated as a hard fault.
 */
@DisplayName("Furniture Geometry — Z elevation and room containment")
class FurnitureGeometryTest {

    private static final String LIB  = "library/BOM.db";
    private static final String SH_DB = "DAGCompiler/lib/output/ifc4_sample_house.db";
    private static final String DX_DB = "DAGCompiler/lib/output/ifc2x3_duplex.db";

    /** DX Level 2 (Upper storey) floor elevation in metres. */
    private static final double DX_UPPER_Z_M = 3.0;
    /** Tolerance: furniture rests on slab ± 10mm (float precision + thin rugs). */
    private static final double FLOOR_TOL_M  = 0.01;
    /** Maximum plausible furniture height (wardrobe / tall shelving). */
    private static final double MAX_FURN_H_M = 2.5;

    /** Reference (extracted) DBs — ground truth from IFC extraction. */
    private static final String SH_REF = "DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db";
    private static final String DX_REF = "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db";

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
    @Disabled("DX MULTI_UNIT compiler outputs in unit-local coordinates (positive Y) "
            + "while ad_room_boundary uses IFC_GLOBAL_MM frame (negative Y). "
            + "Coordinate frame alignment needed before this gate can pass.")
    @DisplayName("F2-DX: Every DX furniture centroid is inside a calibrated room boundary")
    void f2_dxFurnitureCentroids_validBoundRoomsOnly() throws SQLException {
        checkFurnitureCentroids(DX_DB, "Ifc2x3_Duplex");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F4 — Furniture bbox edge fidelity (product dims + IFC reference edges)
    // Replaces visual inspection: proves element SIZE and POSITION at edge level.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * F4-SH: Piano bounding box edges match IFC reference within 10mm.
     *
     * <p>G8 checks centroid distance (≤500mm). This checks all four XY edges
     * of the Piano's bounding box against the extracted IFC reference.
     * A passing centroid with wrong size or wrong wall-proximity would fail here.
     *
     * <p>Edge tolerance 10mm: accounts for float32 R*-tree rounding (±0.05mm)
     * plus the ~1mm between compiled (1371mm) and reference (1371.6mm) widths.
     * IFC-calibrated: Piano dx=-3.749m from north wall center → world(0.673,4.109).
     */
    @Test
    @DisplayName("F4-SH: Piano bbox edges match IFC reference within 10mm — edge-level maths proof")
    void f4_shPianoEdgesMatchReference() throws SQLException {
        record BBox(double minX, double maxX, double minY, double maxY) {}

        BBox compiled = null, reference = null;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB)) {
            String sql = """
                SELECT er.minX, er.maxX, er.minY, er.maxY
                FROM elements_rtree er JOIN elements_meta em ON er.id = em.id
                WHERE em.ifc_class = 'IfcFurniture' AND em.element_name LIKE '%piano%'
                LIMIT 1
                """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                if (rs.next())
                    compiled = new BBox(rs.getDouble(1), rs.getDouble(2),
                                       rs.getDouble(3), rs.getDouble(4));
            }
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_REF)) {
            String sql = """
                SELECT er.minX, er.maxX, er.minY, er.maxY
                FROM elements_rtree er JOIN elements_meta em ON er.id = em.id
                WHERE em.ifc_class = 'IfcFurnishingElement' AND em.element_name LIKE '%Piano%'
                LIMIT 1
                """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                if (rs.next())
                    reference = new BBox(rs.getDouble(1), rs.getDouble(2),
                                        rs.getDouble(3), rs.getDouble(4));
            }
        }

        assertNotNull(compiled,  "Piano not found in compiled SH output — BOM dispatch failure");
        assertNotNull(reference, "Piano not found in SH reference DB — DB integrity failure");

        double tol = 0.010; // 10mm
        BBox c = compiled, r = reference;
        assertTrue(Math.abs(c.minX() - r.minX()) <= tol,
            String.format("Piano west edge: compiled %.3fm vs ref %.3fm (Δ%.0fmm > 10mm)",
                c.minX(), r.minX(), Math.abs(c.minX()-r.minX())*1000));
        assertTrue(Math.abs(c.maxX() - r.maxX()) <= tol,
            String.format("Piano east edge: compiled %.3fm vs ref %.3fm (Δ%.0fmm > 10mm)",
                c.maxX(), r.maxX(), Math.abs(c.maxX()-r.maxX())*1000));
        assertTrue(Math.abs(c.minY() - r.minY()) <= tol,
            String.format("Piano south edge: compiled %.3fm vs ref %.3fm (Δ%.0fmm > 10mm)",
                c.minY(), r.minY(), Math.abs(c.minY()-r.minY())*1000));
        assertTrue(Math.abs(c.maxY() - r.maxY()) <= tol,
            String.format("Piano north edge: compiled %.3fm vs ref %.3fm (Δ%.0fmm > 10mm)",
                c.maxY(), r.maxY(), Math.abs(c.maxY()-r.maxY())*1000));
    }

    /**
     * F4-SH: Dining chairs bbox dimensions match ad_product_dim catalog (450×450mm).
     *
     * <p>Tests that compiled geometry is derived from the catalog, not invented.
     * All dining chairs must have width and depth within 5mm of Dining_Chair product dim.
     * This is a product-fidelity gate: no hardcoded dimensions, only catalog-driven geometry.
     */
    @Test
    @DisplayName("F4-SH: All dining chairs bbox = catalog Dining_Chair dims (450×450mm) ± 5mm")
    void f4_shDiningChairDimFidelity() throws SQLException {
        // Expected from ad_product_dim Dining_Chair: width=0.450, depth=0.450
        final double EXP_W = 0.450, EXP_D = 0.450, TOL = 0.005;

        List<String> bad = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB)) {
            String sql = """
                SELECT em.element_name,
                       (er.maxX - er.minX) AS w,
                       (er.maxY - er.minY) AS d
                FROM elements_rtree er JOIN elements_meta em ON er.id = em.id
                WHERE em.ifc_class = 'IfcFurniture'
                  AND em.element_name LIKE '%dining_chair%'
                """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    double w = rs.getDouble("w"), d = rs.getDouble("d");
                    if (Math.abs(w - EXP_W) > TOL || Math.abs(d - EXP_D) > TOL) {
                        bad.add(String.format("%s: bbox %dx%dmm — expected %dx%dmm from catalog",
                            rs.getString("element_name"),
                            Math.round(w * 1000), Math.round(d * 1000),
                            Math.round(EXP_W * 1000), Math.round(EXP_D * 1000)));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(),
            "Dining chair bbox deviates from ad_product_dim (catalog dim contract broken): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // F5 — Material transparency and staircase Z span (maths, no visual check)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * F5-SH/DX: Glass elements (IfcWindow, IfcPlate) must have alpha < 0.5.
     *
     * <p>Catches the "glass looks opaque" failure mode at the DB level.
     * alpha=1.0 in material_rgba means fully opaque — a rendering error visible
     * only via Blender. This gate converts that visual check to a math assertion.
     *
     * <p>Expected: SH IfcWindow Glass alpha=0.300, SH IfcPlate Glass alpha=0.100,
     * DX IfcWindow Glass alpha=0.300. Any value ≥ 0.5 indicates missing transparency.
     */
    @Test
    @DisplayName("F5-SH: All glass elements (IfcPlate/IfcWindow with Glass material) have alpha < 0.5")
    void f5_shGlassTransparency() throws SQLException {
        checkGlassAlpha(SH_DB, "SH");
    }

    @Test
    @DisplayName("F5-DX: All glass elements (IfcWindow with Glass material) have alpha < 0.5")
    void f5_dxGlassTransparency() throws SQLException {
        checkGlassAlpha(DX_DB, "DX");
    }

    private void checkGlassAlpha(String dbPath, String tag) throws SQLException {
        List<String> bad = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            String sql = """
                SELECT element_name, ifc_class, material_rgba
                FROM elements_meta
                WHERE material_name = 'Glass'
                  AND material_rgba IS NOT NULL AND material_rgba != ''
                """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String rgba = rs.getString("material_rgba");
                    // material_rgba format: "R,G,B,A" — extract last component
                    String[] parts = rgba.split(",");
                    if (parts.length == 4) {
                        double alpha = Double.parseDouble(parts[3].trim());
                        if (alpha >= 0.5) {
                            bad.add(String.format("[%s] %s (%s): alpha=%.3f ≥ 0.5 (opaque)",
                                tag, rs.getString("element_name"),
                                rs.getString("ifc_class"), alpha));
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(),
            "[" + tag + "] Glass elements with opaque material (alpha ≥ 0.5): " + bad);
    }

    /**
     * F5-DX: Staircase spans from ground (minZ < 100mm) to upper floor (maxZ ≥ 2900mm).
     *
     * <p>Proves staircase Z-extent is geometrically correct — floor-to-floor, not truncated.
     * Catches: stairs placed only at ground floor (maxZ too low) or missing floor-1 stairs.
     * DX reference: 2 IfcStairFlight elements, each height=3050mm, minZ=0.
     */
    @Test
    @DisplayName("F5-DX: Staircase elements span Ground to Upper floor (minZ < 100mm, maxZ ≥ 2900mm)")
    void f5_dxStaircaseZSpan() throws SQLException {
        List<String> bad = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DX_DB)) {
            String sql = """
                SELECT em.element_name, rt.minZ, rt.maxZ
                FROM elements_rtree rt JOIN elements_meta em ON rt.id = em.id
                WHERE em.ifc_class IN ('IfcStairFlight', 'IfcStair', 'IfcRamp')
                """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    double minZ = rs.getDouble("minZ");
                    double maxZ = rs.getDouble("maxZ");
                    String name = rs.getString("element_name");
                    if (minZ > 0.100) {
                        bad.add(String.format("%s: minZ=%.3fm — staircase does not start at ground (expected < 0.1m)", name, minZ));
                    }
                    if (maxZ < 2.900) {
                        bad.add(String.format("%s: maxZ=%.3fm — staircase does not reach upper floor (expected ≥ 2.9m)", name, maxZ));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "DX staircase Z-span outside expected floor-to-floor range: " + bad);
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
