package com.bim.ifctobom;

import com.bim.ifctobom.IFCtoBOMPipeline.PipelineResult;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full pipeline test for SH (Ifc4_SampleHouse).
 *
 * <p>Runs the complete IFC-to-BOM pipeline against classify_sh.yaml
 * and verifies structure, counts, and coordinates.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SHPipelineTest {

    static final Path YAML = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
    static final Path COMP_DB = Path.of("library/component_library.db");
    static final Path SCHEMA = Path.of("library/schema_snapshot_bom.sql");
    static final Path BOM_DB = Path.of("library/SH_BOM.db");

    static PipelineResult result;

    @BeforeAll
    static void runPipeline() throws Exception {
        assertTrue(Files.exists(COMP_DB), "component_library.db must exist");
        assertTrue(Files.exists(SCHEMA), "schema_snapshot_bom.sql must exist");
        result = IFCtoBOMPipeline.run(YAML, BOM_DB, COMP_DB, SCHEMA);
    }

    @AfterAll
    static void cleanup() throws Exception {
        // Leave SH_BOM.db for inspection; delete on next run
    }

    // ── G1: COUNT ────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void g1_structuralLineCount() {
        assertEquals(55, result.structuralLines(),
                "SH must have exactly 55 structural element lines");
    }

    @Test
    @Order(2)
    void g1_buildingType() {
        assertEquals("Ifc4_SampleHouse", result.buildingType());
    }

    // ── G2: STRUCTURE ────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void g2_floorBomIds() {
        var ids = result.floorBomIds();
        assertTrue(ids.contains("SH_GF_STR"), "Must have Ground Floor STR");
        assertTrue(ids.contains("SH_ROOF_STR"), "Must have Roof STR");
        assertTrue(ids.contains("SH_CW_STR"), "Must have Curtain Wall STR");
        assertEquals(3, ids.size(), "SH has exactly 3 floor STR BOMs");
    }

    @Test
    @Order(4)
    void g2_bomTree() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            // BUILDING BOM must exist
            var rs = conn.createStatement().executeQuery(
                    "SELECT bom_type, group_by FROM m_bom WHERE bom_id='BUILDING_SH_STD'");
            assertTrue(rs.next(), "BUILDING_SH_STD must exist");
            assertEquals("BUILDING", rs.getString(1));

            // Floor STR BOMs must exist
            rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM m_bom WHERE bom_id LIKE 'SH_%_STR' AND bom_type='FLOOR'");
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));

            // MAKE children linking floors to building
            rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='BUILDING_SH_STD' AND component_type='MAKE'");
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 3, "At least 3 MAKE children (floors) + static");

            // Room floor BOM
            rs = conn.createStatement().executeQuery(
                    "SELECT bom_type FROM m_bom WHERE bom_id='FLOOR_SH_GF_STD'");
            assertTrue(rs.next(), "FLOOR_SH_GF_STD must exist");
            assertEquals("FLOOR", rs.getString(1));
        }
    }

    // ── G3: COORDS ───────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void g3_centroidOffsetsNonNegative() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT bom_id, child_product_id, dx, dy, dz " +
                    "FROM m_bom_line WHERE component_type='BUY' AND entity_type='D'");
            int count = 0;
            while (rs.next()) {
                double dx = rs.getDouble(3);
                double dy = rs.getDouble(4);
                double dz = rs.getDouble(5);
                assertTrue(dx >= -1e-9, "dx must be >= 0: " + rs.getString(1) + "/" + rs.getString(2));
                assertTrue(dy >= -1e-9, "dy must be >= 0: " + rs.getString(1) + "/" + rs.getString(2));
                assertTrue(dz >= -1e-9, "dz must be >= 0: " + rs.getString(1) + "/" + rs.getString(2));
                count++;
            }
            assertTrue(count >= 55, "Must check at least 55 BUY lines");
        }
    }

    // ── G4: AABB ─────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    void g4_buildingAabb() {
        // SH building AABB ≈ 16868 x 8668 x 3945 mm
        assertEquals(16868, Math.round(result.aabbWidthMm()), 10);
        assertEquals(8668, Math.round(result.aabbDepthMm()), 10);
        assertEquals(3945, Math.round(result.aabbHeightMm()), 10);
    }

    @Test
    @Order(7)
    void g4_floorAabbStored() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm " +
                    "FROM m_bom WHERE bom_id='SH_GF_STR'");
            assertTrue(rs.next(), "SH_GF_STR must exist");
            assertTrue(rs.getDouble(1) > 0, "GF width must be > 0");
            assertTrue(rs.getDouble(2) > 0, "GF depth must be > 0");
            assertTrue(rs.getDouble(3) > 0, "GF height must be > 0");
        }
    }

    // ── G5: HASH ─────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void g5_integrityHashStored() throws Exception {
        assertNotNull(result.integrityHash());
        assertFalse(result.integrityHash().isBlank());
        assertEquals(64, result.integrityHash().length(), "SHA-256 = 64 hex chars");

        // Verify stored in ad_sysconfig
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT config_value FROM ad_sysconfig WHERE config_key='RSTB_INTEGRITY_HASH'");
            assertTrue(rs.next());
            assertEquals(result.integrityHash(), rs.getString(1));
        }
    }

    // ── Room BOMs ────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    void roomSpaceChildren() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='FLOOR_SH_GF_STD'");
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1), "4 space children in GF room BOM");
        }
    }

    @Test
    @Order(10)
    void staticChildrenCreated() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT child_product_id, dz FROM m_bom_line " +
                    "WHERE bom_id='BUILDING_SH_STD' AND child_product_id='ROOF_ASSEMBLY'");
            assertTrue(rs.next(), "ROOF_ASSEMBLY static child must exist");
            assertEquals(3.0, rs.getDouble(2), 1e-6, "ROOF dz = 3.0m");
        }
    }

    // ── Products ─────────────────────────────────────────────────────────────

    @Test
    @Order(11)
    void productsRegistered() {
        assertTrue(result.productsRegistered() > 0, "Must register at least some products");
    }

    @Test
    @Order(12)
    void productDimensionsInMetres() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT width, depth, height FROM M_Product " +
                    "WHERE product_id NOT IN (SELECT bom_id FROM m_bom) " +
                    "AND is_active=1 LIMIT 1");
            if (rs.next()) {
                double w = rs.getDouble(1);
                double d = rs.getDouble(2);
                double h = rs.getDouble(3);
                // BUY products have real dimensions in metres (typically 0.01 - 20)
                assertTrue(w > 0 && w < 100, "Width must be reasonable metres: " + w);
                assertTrue(d > 0 && d < 100, "Depth must be reasonable metres: " + d);
                assertTrue(h > 0 && h < 100, "Height must be reasonable metres: " + h);
            }
        }
    }
}
