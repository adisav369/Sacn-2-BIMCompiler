package com.bim.ifctobom;

import com.bim.ifctobom.IFCtoBOMPipeline.PipelineResult;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate test: Java pipeline output must match Python-built BOM.db for all
 * SH-related rows.
 *
 * <p>Compares SH_BOM.db (Java) against BOM.db (Python) for:
 * <ul>
 *   <li>G1-COUNT: same number of structural lines</li>
 *   <li>G2-STRUCTURE: same BOM tree (bom_id, bom_type, group_by)</li>
 *   <li>G3-COORDS: dx/dy/dz within 1e-6 tolerance</li>
 *   <li>G4-AABB: building and floor AABB match</li>
 *   <li>G5-HASH: integrity hash matches (SH-only subset)</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IFCtoBOMGateTest {

    static final Path YAML = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
    static final Path COMP_DB = Path.of("library/component_library.db");
    static final Path SCHEMA = Path.of("library/schema_snapshot_bom.sql");
    static final Path SH_BOM_DB = Path.of("library/SH_BOM.db");
    static final Path PYTHON_BOM_DB = Path.of("library/BOM.db");

    static final double COORD_TOL = 1e-6;

    static PipelineResult result;

    @BeforeAll
    static void runPipeline() throws Exception {
        assertTrue(Files.exists(COMP_DB), "component_library.db must exist");
        assertTrue(Files.exists(PYTHON_BOM_DB), "BOM.db (Python-built) must exist");
        result = IFCtoBOMPipeline.run(YAML, SH_BOM_DB, COMP_DB, SCHEMA);
    }

    // ── G1: COUNT ────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void g1_structuralLineCountMatches() throws Exception {
        int pythonCount = countSHStructuralLines(PYTHON_BOM_DB);
        assertEquals(pythonCount, result.structuralLines(),
                "Java structural line count must match Python");
    }

    @Test
    @Order(2)
    void g1_perFloorCounts() throws Exception {
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
             Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {

            for (String floorId : List.of("SH_GF_STR", "SH_ROOF_STR", "SH_CW_STR")) {
                int jCount = countLines(java, floorId);
                int pCount = countLines(python, floorId);
                assertEquals(pCount, jCount,
                        floorId + " line count mismatch: Python=" + pCount + " Java=" + jCount);
            }
        }
    }

    // ── G2: STRUCTURE ────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void g2_bomHeadersMatch() throws Exception {
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
             Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {

            for (String bomId : List.of("BUILDING_SH_STD", "SH_GF_STR", "SH_ROOF_STR", "SH_CW_STR")) {
                var jBom = getBomHeader(java, bomId);
                var pBom = getBomHeader(python, bomId);
                assertNotNull(jBom, "Java must have " + bomId);
                assertNotNull(pBom, "Python must have " + bomId);
                assertEquals(pBom.get("bom_type"), jBom.get("bom_type"),
                        bomId + " bom_type mismatch");
                assertEquals(pBom.get("group_by"), jBom.get("group_by"),
                        bomId + " group_by mismatch");
            }
        }
    }

    // ── G3: COORDS ───────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void g3_coordinatesMatch() throws Exception {
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
             Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {

            for (String floorId : List.of("SH_GF_STR", "SH_ROOF_STR", "SH_CW_STR")) {
                List<double[]> jCoords = getLineCoords(java, floorId);
                List<double[]> pCoords = getLineCoords(python, floorId);

                assertEquals(pCoords.size(), jCoords.size(),
                        floorId + " coordinate row count mismatch");

                for (int i = 0; i < pCoords.size(); i++) {
                    double[] j = jCoords.get(i);
                    double[] p = pCoords.get(i);
                    assertEquals(p[0], j[0], COORD_TOL,
                            floorId + " line " + i + " dx mismatch");
                    assertEquals(p[1], j[1], COORD_TOL,
                            floorId + " line " + i + " dy mismatch");
                    assertEquals(p[2], j[2], COORD_TOL,
                            floorId + " line " + i + " dz mismatch");
                }
            }
        }
    }

    // ── G4: AABB ─────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void g4_buildingAabbMatches() throws Exception {
        try (Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {
            var rs = python.createStatement().executeQuery(
                    "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm " +
                    "FROM m_bom WHERE bom_id='BUILDING_SH_STD'");
            assertTrue(rs.next());
            assertEquals(rs.getDouble(1), result.aabbWidthMm(), COORD_TOL, "AABB width");
            assertEquals(rs.getDouble(2), result.aabbDepthMm(), COORD_TOL, "AABB depth");
            assertEquals(rs.getDouble(3), result.aabbHeightMm(), COORD_TOL, "AABB height");
        }
    }

    @Test
    @Order(6)
    void g4_floorAabbMatches() throws Exception {
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
             Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {

            for (String floorId : List.of("SH_GF_STR", "SH_ROOF_STR", "SH_CW_STR")) {
                double[] jAabb = getAabb(java, floorId);
                double[] pAabb = getAabb(python, floorId);
                assertNotNull(jAabb, "Java " + floorId + " must have AABB");
                assertNotNull(pAabb, "Python " + floorId + " must have AABB");
                assertEquals(pAabb[0], jAabb[0], COORD_TOL, floorId + " AABB width");
                assertEquals(pAabb[1], jAabb[1], COORD_TOL, floorId + " AABB depth");
                assertEquals(pAabb[2], jAabb[2], COORD_TOL, floorId + " AABB height");
            }
        }
    }

    // ── G5: HASH ─────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void g5_integrityHashMatchesSHSubset() throws Exception {
        // The SH_BOM.db hash covers only SH lines (no DX).
        // Compute the same SH-only hash from Python's BOM.db for comparison.
        String pythonSHHash;
        try (Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {
            pythonSHHash = computeSHOnlyHash(python);
        }

        assertEquals(pythonSHHash, result.integrityHash(),
                "Java SH hash must match Python SH-only hash");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int countSHStructuralLines(Path db) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line l " +
                    "JOIN m_bom b ON l.bom_id = b.bom_id " +
                    "WHERE b.bom_type='FLOOR' AND b.group_by='STOREY' " +
                    "AND b.bom_id LIKE 'SH_%_STR' AND l.component_type='BUY'");
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int countLines(Connection conn, String bomId) throws SQLException {
        var rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='" + bomId + "'");
        rs.next();
        return rs.getInt(1);
    }

    private static Map<String, String> getBomHeader(Connection conn, String bomId)
            throws SQLException {
        var rs = conn.createStatement().executeQuery(
                "SELECT bom_type, group_by, bom_category FROM m_bom WHERE bom_id='" + bomId + "'");
        if (!rs.next()) return null;
        Map<String, String> map = new HashMap<>();
        map.put("bom_type", rs.getString(1));
        map.put("group_by", rs.getString(2));
        map.put("bom_category", rs.getString(3));
        return map;
    }

    private static List<double[]> getLineCoords(Connection conn, String bomId) throws SQLException {
        var rs = conn.createStatement().executeQuery(
                "SELECT dx, dy, dz FROM m_bom_line " +
                "WHERE bom_id='" + bomId + "' AND component_type='BUY' " +
                "ORDER BY child_product_id, dx, dy, dz");
        List<double[]> coords = new ArrayList<>();
        while (rs.next()) {
            coords.add(new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)});
        }
        return coords;
    }

    private static double[] getAabb(Connection conn, String bomId) throws SQLException {
        var rs = conn.createStatement().executeQuery(
                "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm " +
                "FROM m_bom WHERE bom_id='" + bomId + "'");
        if (!rs.next()) return null;
        return new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)};
    }

    /**
     * Compute SH-only integrity hash from BOM.db (same algorithm as
     * IntegrityHash but filtered to SH_ BOMs only).
     */
    private static String computeSHOnlyHash(Connection conn) throws Exception {
        // The IntegrityHash uses LIKE '%_STR' which matches both SH and DX.
        // In SH_BOM.db there's only SH data, so the hash naturally covers SH only.
        // To get the same result from BOM.db, we filter to SH_ prefix.
        var rs = conn.createStatement().executeQuery(
                "SELECT l.bom_id, l.child_product_id, " +
                "round(l.dx, 6), round(l.dy, 6), round(l.dz, 6) " +
                "FROM m_bom_line l " +
                "JOIN m_bom b ON l.bom_id = b.bom_id " +
                "WHERE b.bom_type = 'FLOOR' AND l.component_type = 'BUY' " +
                "AND b.group_by = 'STOREY' AND b.bom_id LIKE 'SH_%_STR' " +
                "ORDER BY l.bom_id, l.child_product_id, " +
                "round(l.dx,6), round(l.dy,6), round(l.dz,6)");

        List<String> fingerprints = new ArrayList<>();
        while (rs.next()) {
            fingerprints.add(String.format("%s,%s,%s,%s,%s",
                    rs.getString(1), rs.getString(2),
                    rs.getString(3), rs.getString(4), rs.getString(5)));
        }

        String joined = String.join("|", fingerprints);
        var md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
