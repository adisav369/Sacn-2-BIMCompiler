package com.bim.ifctobom;

import com.bim.ifctobom.IFCtoBOMPipeline.PipelineResult;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.List;

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
        assertEquals(41, result.structuralLines(),
                "SH must have exactly 41 structural element lines (55 - 14 scope-assigned)");
    }

    @Test
    @Order(13)
    void g1_setLineCount() {
        assertEquals(14, result.setLines(),
                "SH must have exactly 14 SET BOM element lines");
    }

    @Test
    @Order(14)
    void g1_totalLeafCount() {
        assertEquals(55, result.structuralLines() + result.setLines(),
                "Total LEAF = structural + SET must be 55");
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
        assertTrue(ids.contains("SH_CW_STR"), "Must have Cold Water STR");
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

    // ── SET BOMs ───────────────────────────────────────────────────────────

    @Test
    @Order(15)
    void setBomCount() {
        assertEquals(4, result.setBomIds().size(),
                "SH must have 4 SET BOMs (LIVING, DINING, BED, BATHROOM)");
    }

    @Test
    @Order(16)
    void setBomChildCounts() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            // LIVING = 5 (Piano, Sofa, Coffee Table, 2x Armchair)
            assertEquals(5, countLines(conn, "SH_LIVING_SET"),
                    "SH_LIVING_SET must have 5 children");
            // DINING = 7 (Dining Table + 6 Chairs)
            assertEquals(7, countLines(conn, "SH_DINING_SET"),
                    "SH_DINING_SET must have 7 children");
            // BED = 2 (Queen Bed + Desk)
            assertEquals(2, countLines(conn, "SH_BED_SET"),
                    "SH_BED_SET must have 2 children");
            // BATHROOM = 0 (no sanitary in SH)
            assertEquals(0, countLines(conn, "TOILET_BLOCK_FIXTURES"),
                    "TOILET_BLOCK_FIXTURES must have 0 children");
        }
    }

    @Test
    @Order(17)
    void setBomHeadersExist() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            for (String bomId : List.of("SH_LIVING_SET", "SH_DINING_SET",
                    "SH_BED_SET", "TOILET_BLOCK_FIXTURES")) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT bom_type, group_by FROM m_bom WHERE bom_id='" + bomId + "'");
                assertTrue(rs.next(), bomId + " must exist as m_bom row");
                assertEquals("SET", rs.getString(1), bomId + " bom_type must be SET");
                assertEquals("ROOM", rs.getString(2), bomId + " group_by must be ROOM");
            }
        }
    }

    private static int countLines(Connection conn, String bomId) throws SQLException {
        var rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='" + bomId + "'");
        rs.next();
        return rs.getInt(1);
    }

    // ── G3: COORDS ───────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void g3_centroidOffsetsNonNegative() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT bom_id, child_product_id, dx, dy, dz " +
                    "FROM m_bom_line WHERE component_type='LEAF' AND entity_type='D'");
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
            assertTrue(count >= 55, "Must check at least 55 LEAF lines");
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
                // LEAF products have real dimensions in metres (typically 0.01 - 20)
                assertTrue(w > 0 && w < 100, "Width must be reasonable metres: " + w);
                assertTrue(d > 0 && d < 100, "Depth must be reasonable metres: " + d);
                assertTrue(h > 0 && h < 100, "Height must be reasonable metres: " + h);
            }
        }
    }

    // ── CP-4 §4a: Shape archetype + scale band witnesses ──────────────────

    @Test
    @Order(20)
    @DisplayName("W-ARCHETYPE-BOM: Every LEAF row has non-null shape_archetype")
    void wArchetypeBom_allLeafsClassified() throws Exception {
        // Implementing BBC.md §2.2.1 — Witness: W-ARCHETYPE-BOM
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            // Count LEAF rows with dimensions > 0 that lack archetype
            var rs = conn.createStatement().executeQuery("""
                    SELECT COUNT(*) FROM m_bom_line
                    WHERE component_type = 'LEAF'
                      AND MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) > 0
                      AND shape_archetype IS NULL
                    """);
            rs.next();
            int missing = rs.getInt(1);
            assertEquals(0, missing,
                    "Every LEAF row with dimensions must have shape_archetype — " + missing + " missing");

            // Count total classified
            rs = conn.createStatement().executeQuery("""
                    SELECT COUNT(*) FROM m_bom_line
                    WHERE component_type = 'LEAF' AND shape_archetype IS NOT NULL
                    """);
            rs.next();
            int classified = rs.getInt(1);
            assertTrue(classified >= 40,
                    "SH must have at least 40 classified LEAF rows, got " + classified);
        }
    }

    @Test
    @Order(21)
    @DisplayName("W-ARCHETYPE-BOM: Every LEAF row has non-null scale_band")
    void wArchetypeBom_allLeafsScaleBanded() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery("""
                    SELECT COUNT(*) FROM m_bom_line
                    WHERE component_type = 'LEAF'
                      AND MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) > 0
                      AND scale_band IS NULL
                    """);
            rs.next();
            int missing = rs.getInt(1);
            assertEquals(0, missing,
                    "Every LEAF row with dimensions must have scale_band — " + missing + " missing");
        }
    }

    @Test
    @Order(22)
    @DisplayName("W-ARCHETYPE-DIST: SH archetype distribution is well-formed")
    void wArchetypeDist_shDistribution() throws Exception {
        // SH has walls (PLANAR), columns (ELONGATED), furniture (COMPACT)
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery("""
                    SELECT shape_archetype, COUNT(*) as cnt
                    FROM m_bom_line
                    WHERE component_type = 'LEAF' AND shape_archetype IS NOT NULL
                    GROUP BY shape_archetype
                    ORDER BY cnt DESC
                    """);
            int total = 0;
            boolean hasPlanar = false;
            System.out.println("\n  [CP-4] SH archetype distribution:");
            while (rs.next()) {
                String arch = rs.getString(1);
                int cnt = rs.getInt(2);
                System.out.printf("    %-12s %d%n", arch, cnt);
                total += cnt;
                if ("PLANAR".equals(arch)) hasPlanar = true;
            }
            assertTrue(hasPlanar, "SH must have PLANAR elements (walls, slabs)");
            assertTrue(total >= 40, "Must classify all LEAF elements, got " + total);
        }
    }

    @Test
    @Order(23)
    @DisplayName("W-ARCHETYPE-COMPUTE: Classification matches expected for known shapes")
    void wArchetypeCompute_knownShapes() {
        // Wall: 200mm × 5000mm × 3000mm → PLANAR
        assertEquals("PLANAR", VerbFactorizer.classifyArchetype(200, 5000, 3000));
        assertEquals("ARCHITECTURAL", VerbFactorizer.classifyScaleBand(200, 5000, 3000));

        // Column: 300mm × 300mm × 3000mm → ELONGATED
        assertEquals("ELONGATED", VerbFactorizer.classifyArchetype(300, 300, 3000));

        // Furniture: 800mm × 600mm × 400mm → COMPACT
        assertEquals("COMPACT", VerbFactorizer.classifyArchetype(800, 600, 400));
        assertEquals("FURNITURE", VerbFactorizer.classifyScaleBand(800, 600, 400));

        // Beam: 200mm × 100mm × 6000mm → ELONGATED
        assertEquals("ELONGATED", VerbFactorizer.classifyArchetype(200, 100, 6000));

        // Tiny fitting: 20mm × 20mm × 30mm → COMPACT + TINY
        assertEquals("COMPACT", VerbFactorizer.classifyArchetype(20, 20, 30));
        assertEquals("TINY", VerbFactorizer.classifyScaleBand(20, 20, 30));

        // Degenerate: 0 × 0 × 0 → MIXED
        assertEquals("MIXED", VerbFactorizer.classifyArchetype(0, 0, 0));
    }
}
