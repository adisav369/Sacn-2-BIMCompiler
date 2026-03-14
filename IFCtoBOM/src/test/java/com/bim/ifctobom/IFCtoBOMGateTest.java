package com.bim.ifctobom;

import com.bim.ifctobom.IFCtoBOMPipeline.PipelineResult;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Gate test: Java pipeline output must be internally consistent and
 * match Python-built BOM.db where structure is unchanged.
 *
 * <p>DISC-3 changed the BOM structure: 14 furnishing elements moved from
 * FLOOR STR BOMs into SET BOMs. Gate tests compare:
 * <ul>
 *   <li>G1-COUNT: total LEAF (STR + SET) = 55</li>
 *   <li>G2-STRUCTURE: BOM tree (bom_id, bom_type, group_by) for STR + SET</li>
 *   <li>G3-COORDS: unchanged floors (ROOF, CW) match Python exactly</li>
 *   <li>G4-AABB: building and floor AABB match Python (envelope unchanged)</li>
 *   <li>G5-HASH: integrity hash is self-consistent</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IFCtoBOMGateTest {

    static final Path YAML = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
    static final Path COMP_DB = Path.of("library/component_library.db");
    static final Path SCHEMA = Path.of("library/schema_snapshot_bom.sql");
    static final Path SH_BOM_DB = Path.of("library/SH_BOM.db");
    static final Path PYTHON_BOM_DB = Path.of(System.getProperty("bom.db"));

    static final double COORD_TOL = 1e-6;

    static PipelineResult result;

    static boolean hasPythonBom;

    @BeforeAll
    static void runPipeline() throws Exception {
        assertTrue(Files.exists(COMP_DB), "component_library.db must exist");
        hasPythonBom = Files.exists(PYTHON_BOM_DB);
        result = IFCtoBOMPipeline.run(YAML, SH_BOM_DB, COMP_DB, SCHEMA);
    }

    // ── G1: COUNT ────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void g1_totalLeafCount() {
        assertEquals(55, result.structuralLines() + result.setLines(),
                "Total LEAF (STR + SET) must be 55");
    }

    @Test
    @Order(2)
    void g1_structuralLines() {
        assertEquals(41, result.structuralLines(),
                "STR LEAF lines must be 41 (55 - 14 scope-assigned)");
    }

    @Test
    @Order(3)
    void g1_setLines() {
        assertEquals(14, result.setLines(),
                "SET LEAF lines must be 14 (furnishing elements)");
    }

    @Test
    @Order(4)
    void g1_perFloorCounts() throws Exception {
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB)) {
            // GF: 13 structural (27 - 14 furnishing)
            assertEquals(13, countLeafLines(java, "SH_GF_STR"),
                    "SH_GF_STR LEAF lines = 13 structural");
            // ROOF: 2 unchanged
            assertEquals(2, countLeafLines(java, "SH_ROOF_STR"),
                    "SH_ROOF_STR LEAF lines = 2");
            // CW: 26 unchanged
            assertEquals(26, countLeafLines(java, "SH_CW_STR"),
                    "SH_CW_STR LEAF lines = 26");
        }
    }

    // ── G2: STRUCTURE ────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void g2_bomHeadersMatch() throws Exception {
        assumeTrue(hasPythonBom, "BOM.db (Python-built) not available — skipping comparison");
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
             Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {

            // STR BOMs should match Python
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

    @Test
    @Order(6)
    void g2_setBomHeaders() throws Exception {
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB)) {
            for (String bomId : List.of("SH_LIVING_SET", "SH_DINING_SET",
                    "SH_BED_SET", "TOILET_BLOCK_FIXTURES")) {
                var bom = getBomHeader(java, bomId);
                assertNotNull(bom, bomId + " must exist");
                assertEquals("SET", bom.get("bom_type"), bomId + " bom_type");
                assertEquals("ROOM", bom.get("group_by"), bomId + " group_by");
            }
        }
    }

    // ── G3: COORDS ───────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void g3_unchangedFloorsMatchPython() throws Exception {
        assumeTrue(hasPythonBom, "BOM.db (Python-built) not available — skipping comparison");
        try (Connection java = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
             Connection python = DriverManager.getConnection("jdbc:sqlite:" + PYTHON_BOM_DB)) {

            // ROOF and CW are unchanged — coordinates must match Python exactly
            for (String floorId : List.of("SH_ROOF_STR", "SH_CW_STR")) {
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
    @Order(8)
    void g4_buildingAabbMatches() throws Exception {
        assumeTrue(hasPythonBom, "BOM.db (Python-built) not available — skipping comparison");
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
    @Order(9)
    void g4_floorAabbMatches() throws Exception {
        assumeTrue(hasPythonBom, "BOM.db (Python-built) not available — skipping comparison");
        // Floor AABB is computed from ALL elements (including scope-assigned),
        // so it should still match Python.
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
    @Order(10)
    void g5_integrityHashSelfConsistent() throws Exception {
        // Verify hash stored in ad_sysconfig matches recomputation
        assertNotNull(result.integrityHash());
        assertEquals(64, result.integrityHash().length(), "SHA-256 = 64 hex chars");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB)) {
            String recomputed = IntegrityHash.compute(conn);
            assertEquals(result.integrityHash(), recomputed,
                    "Stored hash must match recomputation");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int countLeafLines(Connection conn, String bomId) throws SQLException {
        var rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='" + bomId +
                "' AND component_type='LEAF'");
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
                "WHERE bom_id='" + bomId + "' AND component_type='LEAF' " +
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
}
