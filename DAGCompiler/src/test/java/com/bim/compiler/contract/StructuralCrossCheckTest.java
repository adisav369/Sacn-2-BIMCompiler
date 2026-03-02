package com.bim.compiler.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * X1 — Structural Cross-Check Gate.
 *
 * <p>Non-circular comparison of compiled output against extracted IFC reference DBs.
 * This test is hardcoded to SH and DX — the only two buildings with extracted reference DBs.
 * It does NOT use BuildingRegistry, geometry_fail_threshold, or any pipeline bypass mechanism.
 *
 * <p>For each IFC class tested, a per-class spatial digest is computed independently from
 * both the reference and compiled DB. Formula: "COUNT={n}" header + sorted bbox lines
 * ({minX_mm}|{maxX_mm}|{minY_mm}|{maxY_mm}|{minZ_mm}|{maxZ_mm}), SHA256.
 * Coordinates are rounded to 1mm — same formula as {@link com.bim.compiler.validation.SpatialDigest}.
 *
 * <p>Two test categories:
 * <ul>
 *   <li><b>MATCH</b> — classes where compiled output reproduces the reference exactly.
 *       Must always be GREEN. A failure here is a regression — find and fix it.</li>
 *   <li><b>GAP</b> — classes with known bugs as of 2026-02-28. Honest RED.
 *       Remain RED until the underlying bug is fixed. When fixed, the test turns GREEN
 *       automatically — no test file changes required.</li>
 * </ul>
 *
 * <p>No {@code @Disabled}, no threshold tolerance, no {@code isGenerative()} bypass,
 * no dependence on BuildingRegistry flags. The only way to defeat this test is to
 * delete it — which is visible in git history.
 */
@DisplayName("X1 — Structural Cross-Check Gate: compiled vs extracted IFC reference")
class StructuralCrossCheckTest {

    // Hardcoded paths — not delegated to BuildingRegistry (bypass-proof).
    // Compiled DBs are written by BuildingRegistryTest during the same mvn test run.
    private static final String COMPILED_SH  = "DAGCompiler/lib/output/ifc4_sample_house.db";
    private static final String COMPILED_DX  = "DAGCompiler/lib/output/ifc2x3_duplex.db";
    private static final String REFERENCE_SH = "DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db";
    private static final String REFERENCE_DX = "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db";

    // -----------------------------------------------------------------------
    // SH — MATCH (structural classes, position + count verified 2026-02-28)
    // -----------------------------------------------------------------------

    /**
     * X1-SH: Five structural classes must reproduce the reference exactly.
     *
     * <p>Verified against Ifc4_SampleHouse_extracted.db on 2026-02-28:
     * IfcWall=5, IfcSlab=2, IfcRoof=1, IfcMember=20, IfcPlate=6.
     * Both element count and bounding-box positions are identical.
     *
     * <p>If this test fails: a geometry regression was introduced in the compilation
     * pipeline. Do NOT add thresholds or skip annotations — fix the pipeline.
     */
    @Test
    @DisplayName("X1-SH: Structural classes match reference (Wall/Slab/Roof/Member/Plate)")
    void sh_structural_match() {
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcWall");
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcSlab");
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcRoof");
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcMember");
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcPlate");
    }

    // -----------------------------------------------------------------------
    // SH — GAP (honest RED until fixed)
    // -----------------------------------------------------------------------

    /**
     * X1-SH-GAP: Door and Window positions are wrong — known relational resolver bug.
     *
     * <p>Root cause: RelationalResolver places doors and windows using room boundary
     * logic rather than IFC world coordinates. The element COUNT is correct (3 doors,
     * N windows) but all positions differ from the reference extracted DB.
     *
     * <p>Repair: fix RelationalResolver.loadDoors() / loadWindows() to derive positions
     * from IFC world coordinates stored in the extracted DB, not the room boundary grid.
     *
     * <p>When fixed, this test turns GREEN automatically — no test changes required.
     * Until fixed, this test is an honest RED: the compiler output is wrong for doors/windows.
     */
    @Test
    @DisplayName("X1-SH-GAP: Door/Window positions wrong — relational resolver bug [KNOWN RED]")
    void sh_door_window_gap() {
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcDoor");
        assertClassDigest(REFERENCE_SH, COMPILED_SH, "IfcWindow");
    }

    // -----------------------------------------------------------------------
    // DX — MATCH (10 classes, count verified 2026-02-28)
    // -----------------------------------------------------------------------

    /**
     * X1-DX: All 13 IFC classes must match the reference element count.
     *
     * <p>Verified against Ifc2x3_Duplex_extracted.db on 2026-03-03 (count comparison).
     * All classes present in the reference DB are checked.
     *
     * <p>Count comparison is used (not full digest) because positional fidelity for DX
     * elements has not yet been verified. Upgrade to assertClassDigest once
     * positions are confirmed identical to the reference.
     *
     * <p>If this test fails: elements were dropped or duplicated. Do NOT add tolerance
     * — find the pipeline step that lost or invented elements.
     */
    @Test
    @DisplayName("X1-DX: Structural classes match reference count (13 classes)")
    void dx_structural_match() {
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcBeam");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcDoor");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcFlowController");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcFlowFitting");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcFlowSegment");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcFlowTerminal");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcFurnishingElement");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcMember");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcRailing");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcSlab");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcStairFlight");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcWall");
        assertClassCount(REFERENCE_DX, COMPILED_DX, "IfcWindow");
    }

    // -----------------------------------------------------------------------
    // DX — GAP (honest RED until fixed)
    // -----------------------------------------------------------------------

    /**
     * X1-DX-GAP: Door and furnishing positions wrong — relational resolver bug.
     *
     * <p>MEP/furnishing COUNT gaps are now fixed (2026-03-03):
     * FlowController=14, FlowFitting=358, FurnishingElement=61 — all match reference.
     *
     * <p>Remaining gap: positional fidelity for Doors and FurnishingElements.
     * Same root cause as X1-SH-GAP — RelationalResolver places from room boundaries
     * rather than IFC world coordinates.
     *
     * <p>When positions match, this test turns GREEN automatically.
     */
    @Test
    @DisplayName("X1-DX-GAP: Door/Furnishing positions wrong — relational resolver [KNOWN RED]")
    void dx_position_gap() {
        assertClassDigest(REFERENCE_DX, COMPILED_DX, "IfcDoor");
        assertClassDigest(REFERENCE_DX, COMPILED_DX, "IfcFurnishingElement");
    }

    // -----------------------------------------------------------------------
    // Helpers — full digest (count + position)
    // -----------------------------------------------------------------------

    /**
     * Assert that the per-class spatial digest of refPath and compiledPath match.
     *
     * <p>Digest formula: "COUNT={n}" + sorted bbox lines, SHA256.
     * Catches both element count differences AND position shifts.
     * No tolerance, no threshold, no escape hatch.
     */
    private static void assertClassDigest(String refPath, String compiledPath, String ifcClass) {
        String refDigest      = classDigest(refPath,      ifcClass);
        String compiledDigest = classDigest(compiledPath, ifcClass);
        assertEquals(refDigest, compiledDigest,
            String.format("X1 digest mismatch for class %s%n"
                + "  ref      [%s]: %s%n"
                + "  compiled [%s]: %s%n"
                + "  Count or position difference. Check elements_rtree in both DBs.",
                ifcClass,
                refPath,      refDigest.substring(0, 16),
                compiledPath, compiledDigest.substring(0, 16)));
    }

    /**
     * Compute per-class spatial digest (count + sorted bboxes, SHA256).
     *
     * <p>An absent class produces digest of "COUNT=0" — distinct from any non-empty class.
     */
    private static String classDigest(String dbPath, String ifcClass) {
        String sql = """
            SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class = ?
            ORDER BY r.minX, r.minY, r.minZ
            """;

        List<String> coords = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    coords.add(String.format("%d|%d|%d|%d|%d|%d",
                        Math.round(rs.getDouble(1) * 1000),
                        Math.round(rs.getDouble(2) * 1000),
                        Math.round(rs.getDouble(3) * 1000),
                        Math.round(rs.getDouble(4) * 1000),
                        Math.round(rs.getDouble(5) * 1000),
                        Math.round(rs.getDouble(6) * 1000)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "classDigest failed: db=" + dbPath + " class=" + ifcClass, e);
        }

        List<String> lines = new ArrayList<>();
        lines.add("COUNT=" + coords.size());
        lines.addAll(coords);
        return sha256(String.join("\n", lines));
    }

    // -----------------------------------------------------------------------
    // Helpers — count only
    // -----------------------------------------------------------------------

    /**
     * Assert that the element count for ifcClass matches between reference and compiled.
     *
     * <p>Catches missing/extra elements. Does not verify positions.
     * Use assertClassDigest when positional fidelity is also required.
     */
    private static void assertClassCount(String refPath, String compiledPath, String ifcClass) {
        int refCount      = classCount(refPath,      ifcClass);
        int compiledCount = classCount(compiledPath, ifcClass);
        assertEquals(refCount, compiledCount,
            String.format("X1 count mismatch for class %s: ref=%d compiled=%d%n"
                + "  ref      db: %s%n"
                + "  compiled db: %s",
                ifcClass, refCount, compiledCount, refPath, compiledPath));
    }

    private static int classCount(String dbPath, String ifcClass) {
        String sql = "SELECT COUNT(*) FROM elements_meta WHERE ifc_class = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "classCount failed: db=" + dbPath + " class=" + ifcClass, e);
        }
    }

    // -----------------------------------------------------------------------
    // Crypto
    // -----------------------------------------------------------------------

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
