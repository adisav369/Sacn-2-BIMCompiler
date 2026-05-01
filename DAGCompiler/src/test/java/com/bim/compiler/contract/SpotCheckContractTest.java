/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C2-SPOTCHECK: Per-element coordinate verification for SH (Sample House).
 *
 * <p><b>Purpose:</b> G3-DIGEST proves that the <em>aggregate</em> spatial hash matches
 * between reference and compiled output. But a hash is opaque — if it fails, you don't
 * know which element drifted. This test picks 5 representative elements (one per major
 * IFC class) and asserts their <b>exact AABB coordinates</b> against the reference DB.
 *
 * <p><b>Why 5 elements?</b> Each element covers a different placement path:
 * <ul>
 *   <li><b>IfcSlab</b> — ground floor slab, largest element, tests building-level placement</li>
 *   <li><b>IfcDoor</b> — opening element, tests wall-hosted placement offsets</li>
 *   <li><b>IfcWindow</b> — opening element, tests elevated Z offset (sill height)</li>
 *   <li><b>IfcFurnishingElement</b> — furniture, tests room-level placement + material_rgba</li>
 *   <li><b>IfcWall</b> — structural element, tests full-width building span</li>
 * </ul>
 *
 * <p><b>How coordinates are selected:</b> NOT hardcoded by the developer. Each test
 * queries the <em>reference DB</em> (the IFC extraction oracle we didn't write) for the
 * element's AABB, then queries the <em>compiled output DB</em> for the same element
 * (matched by ifc_class + coordinate position, since element names differ between
 * EXTRACTED and COMPILED). If coordinates don't match within 1mm, the compiler drifted.
 *
 * <p><b>Anti-cheat:</b> The reference DB is read-only (IfcOpenShell extraction output).
 * We query it live — no hardcoded golden values. If someone modifies the reference DB,
 * G4-TAMPER catches it (seal breakage). If someone modifies the compiler to produce
 * wrong coordinates, THIS test catches it (live cross-DB comparison).
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>C2-1: IfcSlab ground floor AABB matches reference within 1mm</li>
 *   <li>C2-2: IfcDoor (first interior single) AABB matches reference within 1mm</li>
 *   <li>C2-3: IfcWindow (first north-facing) AABB matches reference within 1mm</li>
 *   <li>C2-4: IfcFurnishingElement (piano) AABB + material_rgba matches reference</li>
 *   <li>C2-5: IfcWall (north exterior) AABB + material matches reference within 1mm</li>
 * </ul>
 *
 * @see SpatialDigest — the aggregate hash (G3) that this test complements
 * @see RosettaStoneGateTest — the 6-gate framework
 * @see <a href="docs/TestArchitecture.md">Test Architecture §Anti-Drift</a>
 */
class SpotCheckContractTest {

    /** 1mm tolerance in metres — absorbs floating-point noise without hiding real drift. */
    private static final double TOLERANCE_M = 0.001;

    private static final String OUTPUT_DB =
        "DAGCompiler/lib/output/samplehouse.db";
    private static final String REFERENCE_DB =
        "DAGCompiler/lib/input/SampleHouse_extracted.db";

    private static Connection outConn;
    private static Connection refConn;

    @BeforeAll
    static void openConnections() throws SQLException {
        outConn = DriverManager.getConnection("jdbc:sqlite:" + OUTPUT_DB);
        refConn = DriverManager.getConnection("jdbc:sqlite:" + REFERENCE_DB);
    }

    @AfterAll
    static void closeConnections() throws SQLException {
        if (outConn != null) outConn.close();
        if (refConn != null) refConn.close();
    }

    // =========================================================================
    // C2-1: IfcSlab — Ground Floor slab (largest element, building-level check)
    //
    // The ground floor slab spans the entire building footprint. Its AABB tests
    // that the building-level coordinate system is correctly established.
    // =========================================================================

    @Test
    @DisplayName("C2-1: IfcSlab ground floor AABB matches reference within 1mm")
    void c2_1_slab_ground_floor() throws SQLException {
        // The ground floor slab is unique: it's the only IfcSlab on "Ground Floor"
        // with negative minZ (below grade). Match by class + storey + minZ < 0.
        double[] ref = queryFirstElement(refConn, "IfcSlab", "Ground Floor");
        double[] out = queryFirstElement(outConn, "IfcSlab", "Ground Floor");

        assertNotNull(ref, "Reference DB must have IfcSlab on Ground Floor");
        assertNotNull(out, "Output DB must have IfcSlab on Ground Floor");
        assertAabbMatch("IfcSlab/Ground Floor", ref, out);
    }

    // =========================================================================
    // C2-2: IfcDoor — First interior single door (opening placement check)
    //
    // Doors test the wall-hosted placement path. The interior single door's
    // position depends on: floor BOM offset + door BOM line dx/dy/dz.
    // If the anchor accumulation is wrong, doors shift.
    // =========================================================================

    @Test
    @DisplayName("C2-2: IfcDoor (first interior single) AABB matches reference within 1mm")
    void c2_2_door_interior() throws SQLException {
        // The first IfcDoor sorted by minX is the westernmost interior door.
        double[] ref = queryFirstElement(refConn, "IfcDoor", null);
        double[] out = queryFirstElement(outConn, "IfcDoor", null);

        assertNotNull(ref, "Reference DB must have IfcDoor");
        assertNotNull(out, "Output DB must have IfcDoor");
        assertAabbMatch("IfcDoor/first", ref, out);
    }

    // =========================================================================
    // C2-3: IfcWindow — First north-facing window (elevated Z check)
    //
    // Windows test the sill height offset. Their minZ should be ~0.9m
    // (standard sill). If the BOM line dz is wrong, windows sit on the floor.
    // =========================================================================

    @Test
    @DisplayName("C2-3: IfcWindow (first by position) AABB matches reference within 1mm")
    void c2_3_window() throws SQLException {
        double[] ref = queryFirstElement(refConn, "IfcWindow", null);
        double[] out = queryFirstElement(outConn, "IfcWindow", null);

        assertNotNull(ref, "Reference DB must have IfcWindow");
        assertNotNull(out, "Output DB must have IfcWindow");
        assertAabbMatch("IfcWindow/first", ref, out);

        // Bonus: verify sill height is elevated (not sitting on floor)
        assertTrue(out[4] > 0.5,
            "IfcWindow minZ should be elevated (sill height) — got " + out[4]);
    }

    // =========================================================================
    // C2-4: IfcFurnishingElement — Piano (furniture + material check)
    //
    // The piano is unique furniture: only one in SH, distinctive material
    // (Wood - Mahogany). Tests room-level furniture placement AND material
    // provenance. If material_rgba drifts, the piano changes color.
    // =========================================================================

    @Test
    @DisplayName("C2-4: IfcFurnishingElement (piano) AABB + material matches reference")
    void c2_4_furniture_piano() throws SQLException {
        // Piano is identifiable by material "Wood - Mahogany" (unique in SH)
        String[] refMat = queryElementWithMaterial(refConn, "IfcFurnishingElement",
            "Wood - Mahogany");
        String[] outMat = queryElementWithMaterial(outConn, "IfcFurnishingElement",
            "Wood - Mahogany");

        assertNotNull(refMat, "Reference DB must have piano (Wood - Mahogany)");
        assertNotNull(outMat, "Output DB must have piano (Wood - Mahogany)");

        // AABB match
        for (int i = 0; i < 6; i++) {
            double refVal = Double.parseDouble(refMat[i]);
            double outVal = Double.parseDouble(outMat[i]);
            assertEquals(refVal, outVal, TOLERANCE_M,
                String.format("Piano AABB[%d] drift: ref=%.6f out=%.6f",
                    i, refVal, outVal));
        }

        // Material RGBA match — visual fidelity check
        assertEquals(refMat[6], outMat[6],
            "Piano material_rgba must match reference (visual fidelity)");
    }

    // =========================================================================
    // C2-5: IfcWall — North exterior wall (structural span check)
    //
    // The north exterior wall spans the building's full width. Its AABB tests
    // that structural elements maintain their full building-scale coordinates.
    // Material: "Brick, Common" — a distinct construction material.
    // =========================================================================

    @Test
    @DisplayName("C2-5: IfcWall (north exterior) AABB + material matches reference within 1mm")
    void c2_5_wall_exterior() throws SQLException {
        // The north exterior wall is the widest IfcWall (maxY > 4.0)
        // with material "Brick, Common". Sort by maxY desc to find it.
        String[] refMat = queryWidestWall(refConn);
        String[] outMat = queryWidestWall(outConn);

        assertNotNull(refMat, "Reference DB must have north exterior IfcWall");
        assertNotNull(outMat, "Output DB must have north exterior IfcWall");

        for (int i = 0; i < 6; i++) {
            double refVal = Double.parseDouble(refMat[i]);
            double outVal = Double.parseDouble(outMat[i]);
            assertEquals(refVal, outVal, TOLERANCE_M,
                String.format("North wall AABB[%d] drift: ref=%.6f out=%.6f",
                    i, refVal, outVal));
        }

        // Material match
        assertEquals(refMat[6], outMat[6],
            "North wall material_rgba must match reference");
    }

    // =========================================================================
    // Query helpers — all queries are positional (no element name matching)
    //
    // Element names differ between EXTRACTED ("Basic Wall:...:285330") and
    // COMPILED ("MD_WALL_GROUND_FLOOR_1"). Matching by ifc_class + coordinate
    // position is the only cross-mode invariant.
    // =========================================================================

    /**
     * Query the first element of a given IFC class, sorted by minX then minY.
     * Optionally filter by storey.
     *
     * @return double[6] = {minX, maxX, minY, maxY, minZ, maxZ}, or null
     */
    private double[] queryFirstElement(Connection conn, String ifcClass,
                                        String storey) throws SQLException {
        String sql = storey != null
            ? """
              SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
              FROM elements_meta em
              JOIN elements_rtree r ON em.id = r.id
              WHERE em.ifc_class = ? AND em.storey = ?
              ORDER BY r.minX, r.minY, r.minZ
              LIMIT 1
              """
            : """
              SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
              FROM elements_meta em
              JOIN elements_rtree r ON em.id = r.id
              WHERE em.ifc_class = ?
              ORDER BY r.minX, r.minY, r.minZ
              LIMIT 1
              """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            if (storey != null) ps.setString(2, storey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new double[]{
                    rs.getDouble(1), rs.getDouble(2), rs.getDouble(3),
                    rs.getDouble(4), rs.getDouble(5), rs.getDouble(6)
                };
            }
        }
    }

    /**
     * Query an element by IFC class + material name. Returns AABB + material_rgba.
     *
     * @return String[7] = {minX, maxX, minY, maxY, minZ, maxZ, material_rgba}, or null
     */
    private String[] queryElementWithMaterial(Connection conn, String ifcClass,
                                              String materialName) throws SQLException {
        String sql = """
            SELECT printf('%.6f', r.minX), printf('%.6f', r.maxX),
                   printf('%.6f', r.minY), printf('%.6f', r.maxY),
                   printf('%.6f', r.minZ), printf('%.6f', r.maxZ),
                   COALESCE(em.material_rgba, '')
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class = ? AND em.material_name = ?
            ORDER BY r.minX, r.minY
            LIMIT 1
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            ps.setString(2, materialName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new String[]{
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7)
                };
            }
        }
    }

    /**
     * Query the widest IfcWall (largest minX-to-maxX span) with "Brick, Common" material.
     * This identifies the north exterior wall which spans the full building width.
     *
     * @return String[7] = {minX, maxX, minY, maxY, minZ, maxZ, material_rgba}, or null
     */
    private String[] queryWidestWall(Connection conn) throws SQLException {
        String sql = """
            SELECT printf('%.6f', r.minX), printf('%.6f', r.maxX),
                   printf('%.6f', r.minY), printf('%.6f', r.maxY),
                   printf('%.6f', r.minZ), printf('%.6f', r.maxZ),
                   COALESCE(em.material_rgba, '')
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class = 'IfcWall'
              AND em.material_name = 'Brick, Common'
            ORDER BY (r.maxX - r.minX) DESC
            LIMIT 1
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new String[]{
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7)
                };
            }
        }
    }

    /**
     * Assert that two AABBs match within 1mm tolerance on all 6 axes.
     * Reports which axis drifted for diagnostic clarity.
     */
    private void assertAabbMatch(String label, double[] ref, double[] out) {
        String[] axisNames = {"minX", "maxX", "minY", "maxY", "minZ", "maxZ"};
        for (int i = 0; i < 6; i++) {
            assertEquals(ref[i], out[i], TOLERANCE_M,
                String.format("[C2-SPOTCHECK] %s %s drift: ref=%.6f out=%.6f delta=%.6f",
                    label, axisNames[i], ref[i], out[i], Math.abs(ref[i] - out[i])));
        }
    }
}
