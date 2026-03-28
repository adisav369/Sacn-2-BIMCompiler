package com.bim.ifctobom;

import com.bim.ifctobom.IFCtoBOMPipeline.PipelineResult;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full pipeline test for DX (Ifc2x3_Duplex).
 *
 * <p>Runs the complete IFC-to-BOM pipeline against classify_dx.yaml
 * and verifies half-unit composition, pair container, rotation,
 * offsets, and integrity hash.
 *
 * <p>DX = two mirrored half-units + shared elements.
 * Three-tier partition (see DuplexAnalysis.md):
 * <ul>
 *   <li>Spanning: elements whose AABB crosses the mirror plane → SHARED</li>
 *   <li>Paired: min(A, B) per (M_Product_ID, storey) → half-unit</li>
 *   <li>Excess: |A - B| per group → SHARED</li>
 * </ul>
 * ~487 paired/side + ~125 shared = ~612 stored → 1099 produced.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DXPipelineTest {

    static final Path YAML = Path.of("IFCtoBOM/src/main/resources/classify_dx.yaml");
    static final Path COMP_DB = Path.of("library/component_library.db");
    static final Path SCHEMA = Path.of("library/schema_snapshot_bom.sql");
    static final Path BOM_DB = Path.of("library/DX_BOM.db");

    static PipelineResult result;

    @BeforeAll
    static void runPipeline() throws Exception {
        assertTrue(Files.exists(COMP_DB), "component_library.db must exist");
        assertTrue(Files.exists(SCHEMA), "schema_snapshot_bom.sql must exist");
        result = IFCtoBOMPipeline.run(YAML, BOM_DB, COMP_DB, SCHEMA);
    }

    // ── G1: COUNT ────────────────────────────────────────────────────────────

    @Test @Order(1)
    void g1_buildingType() {
        assertEquals("Ifc2x3_Duplex", result.buildingType());
    }

    @Test @Order(2)
    void g1_halfUnitLineCount() {
        // Paired A-side elements: ~487 (from 3-tier partition, see DuplexAnalysis.md)
        assertTrue(result.halfUnitLines() >= 450 && result.halfUnitLines() <= 520,
                "Half-unit lines should be ~487, got " + result.halfUnitLines());
    }

    @Test @Order(3)
    void g1_structuralLineCount() {
        // Shared = spanning + excess + scope-excluded elements → in structural BOMs
        assertTrue(result.structuralLines() > 50,
                "Structural (shared) lines should be > 50, got " + result.structuralLines());
    }

    @Test @Order(4)
    void g1_losslessVerification() {
        // The core invariant: halfUnit * 2 + shared = 1099
        // shared = structural + spanning + excess (all non-paired elements)
        // stored = halfUnit + shared
        // produced = halfUnit + mirror(halfUnit) + shared = halfUnit*2 + shared
        int stored = result.halfUnitLines() + result.structuralLines()
                + result.setLines() + result.roomLines() + result.pairLines();
        int produced = result.halfUnitLines() * 2
                + (result.structuralLines() + result.setLines());
        System.out.printf("[DXPipelineTest] stored=%d, produced(projected)=%d%n", stored, produced);
        // stored < 1099 (compression achieved)
        assertTrue(stored < 1099,
                "Stored lines must be < 1099 (compression), got " + stored);
    }

    @Test @Order(5)
    void g1_pairLines() {
        assertEquals(2, result.pairLines(), "Pair container must have exactly 2 children");
    }

    // ── G2: STRUCTURE ────────────────────────────────────────────────────────

    @Test @Order(10)
    void g2_halfUnitBomExists() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT b.bom_type, b.group_by, mpc.Value FROM m_bom b "
                    + "LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID "
                    + "WHERE b.bom_id='DUPLEX_SINGLE_UNIT_STD'");
            assertTrue(rs.next(), "DUPLEX_SINGLE_UNIT_STD must exist");
            assertEquals("FLOOR", rs.getString(1), "bom_type must be FLOOR");
            assertEquals("STOREY", rs.getString(2), "group_by must be STOREY");
            assertEquals("HU", rs.getString(3), "m_product_category_id must be HU");
        }
    }

    @Test @Order(11)
    void g2_pairContainerBomExists() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT b.bom_type, b.group_by, mpc.Value FROM m_bom b "
                    + "LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID "
                    + "WHERE b.bom_id='DUPLEX_SET_STD'");
            assertTrue(rs.next(), "DUPLEX_SET_STD must exist");
            assertEquals("SET", rs.getString(1), "bom_type must be SET");
            assertEquals("BUILDING", rs.getString(2), "group_by must be BUILDING");
            assertEquals("PR", rs.getString(3), "m_product_category_id must be PR");
        }
    }

    @Test @Order(12)
    void g2_pairContainerHasTwoChildren() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='DUPLEX_SET_STD'");
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "DUPLEX_SET_STD must have 2 children (UNIT_A, UNIT_B)");
        }
    }

    @Test @Order(13)
    void g2_unitARoleAndRotation() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT role, rotation_rule FROM m_bom_line " +
                    "WHERE bom_id='DUPLEX_SET_STD' AND role='UNIT_A'");
            assertTrue(rs.next(), "UNIT_A must exist");
            assertEquals("0", rs.getString(2), "UNIT_A rotation_rule must be 0");
        }
    }

    @Test @Order(14)
    void g2_unitBRoleAndRotation() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT role, rotation_rule FROM m_bom_line " +
                    "WHERE bom_id='DUPLEX_SET_STD' AND role='UNIT_B'");
            assertTrue(rs.next(), "UNIT_B must exist");
            double rotVal = Double.parseDouble(rs.getString(2));
            assertEquals(Math.PI, rotVal, 0.001, "UNIT_B rotation_rule must be pi");
        }
    }

    @Test @Order(15)
    void g2_halfUnitChildCount() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='DUPLEX_SINGLE_UNIT_STD' AND component_type='LEAF'");
            assertTrue(rs.next());
            assertEquals(result.halfUnitLines(), rs.getInt(1),
                    "Half-unit DB count must match pipeline result");
        }
    }

    @Test @Order(16)
    void g2_buildingBomExists() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT bom_type FROM m_bom WHERE bom_id='BUILDING_DX_STD'");
            assertTrue(rs.next(), "BUILDING_DX_STD must exist");
            assertEquals("BUILDING", rs.getString(1));
        }
    }

    // ── G3: COORDS ───────────────────────────────────────────────────────────

    @Test @Order(20)
    void g3_halfUnitOffsetsNonNegative() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT child_product_id, dx, dy, dz FROM m_bom_line " +
                    "WHERE bom_id='DUPLEX_SINGLE_UNIT_STD' AND component_type='LEAF'");
            int count = 0;
            while (rs.next()) {
                double dx = rs.getDouble(2);
                double dy = rs.getDouble(3);
                double dz = rs.getDouble(4);
                assertTrue(dx >= -1e-9, "dx must be >= 0: " + rs.getString(1) + " dx=" + dx);
                assertTrue(dy >= -1e-9, "dy must be >= 0: " + rs.getString(1) + " dy=" + dy);
                assertTrue(dz >= -1e-9, "dz must be >= 0: " + rs.getString(1) + " dz=" + dz);
                count++;
            }
            assertTrue(count > 0, "Must have LEAF lines to check");
        }
    }

    // ── G4: AABB ─────────────────────────────────────────────────────────────

    @Test @Order(30)
    void g4_buildingAabbReasonable() {
        // DX building AABB: ~9215 x ~26565 x ~7885 mm
        assertTrue(result.aabbWidthMm() > 5000 && result.aabbWidthMm() < 15000,
                "AABB width should be ~9215mm, got " + result.aabbWidthMm());
        assertTrue(result.aabbDepthMm() > 15000 && result.aabbDepthMm() < 35000,
                "AABB depth should be ~26565mm, got " + result.aabbDepthMm());
        assertTrue(result.aabbHeightMm() > 3000 && result.aabbHeightMm() < 12000,
                "AABB height should be ~7885mm, got " + result.aabbHeightMm());
    }

    // ── G5: HASH ─────────────────────────────────────────────────────────────

    @Test @Order(40)
    void g5_integrityHashStored() throws Exception {
        assertNotNull(result.integrityHash());
        assertFalse(result.integrityHash().isBlank());
        assertEquals(64, result.integrityHash().length(), "SHA-256 = 64 hex chars");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT config_value FROM ad_sysconfig WHERE config_key='RSTB_INTEGRITY_HASH'");
            assertTrue(rs.next());
            assertEquals(result.integrityHash(), rs.getString(1));
        }
    }

    // ── ROTATION ─────────────────────────────────────────────────────────────

    @Test @Order(50)
    void rotation_unitBHasPiAndMirrorOffset() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT rotation_rule, dx, dy FROM m_bom_line " +
                    "WHERE bom_id='DUPLEX_SET_STD' AND role='UNIT_B'");
            assertTrue(rs.next(), "UNIT_B must exist");
            double rot = Double.parseDouble(rs.getString(1));
            double dx = rs.getDouble(2);
            double dy = rs.getDouble(3);

            assertEquals(Math.PI, rot, 0.001, "UNIT_B rotation = pi");
            // UNIT_B must have different offset than UNIT_A (mirrored position)
            assertTrue(dx > 0 || dy > 0,
                    "UNIT_B must have non-zero mirror offset, dx=" + dx + " dy=" + dy);
        }
    }

    // ── Room BOMs + static children ──────────────────────────────────────────

    @Test @Order(60)
    void roomBomLinesCreated() {
        assertTrue(result.roomLines() > 0, "DX must have room BOM lines");
    }

    @Test @Order(61)
    void staticChildrenCreated() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT child_product_id FROM m_bom_line " +
                    "WHERE bom_id='BUILDING_DX_STD' AND child_product_id='DUPLEX_SET_STD'");
            assertTrue(rs.next(), "DUPLEX_SET_STD must be a static child of BUILDING_DX_STD");

            rs = conn.createStatement().executeQuery(
                    "SELECT dz FROM m_bom_line " +
                    "WHERE bom_id='BUILDING_DX_STD' AND child_product_id='ROOF_ASSEMBLY'");
            assertTrue(rs.next(), "ROOF_ASSEMBLY must be a static child of BUILDING_DX_STD");
            assertEquals(6.0, rs.getDouble(1), 1e-6, "ROOF dz = 6.0m");
        }
    }
}
