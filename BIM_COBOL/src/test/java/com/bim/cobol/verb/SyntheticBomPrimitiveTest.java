package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for §18.4 Level 0 — BOM Primitive Verbs.
 *
 * <p>Exercises the full CREATE→ADD→SET→REMOVE→DELETE lifecycle
 * on an isolated temp BOM.db copy. Each test is a witness claim.
 *
 * <p>All primitives write to BOM.db (the dictionary). The tests use a
 * temp copy so production BOM.db is never mutated.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SyntheticBomPrimitiveTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        // Copy BOM.db to temp file — primitives mutate it
        File source = new File("library/BOM.db");
        assertTrue(source.exists(), "library/BOM.db must exist");
        tempDb = File.createTempFile("synthetic_bom_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        ctx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
        if (tempDb != null) tempDb.delete();
    }

    // ── W-SY-1: CREATE BOM ──────────────────────────────────────────────

    /**
     * W-SY-1: CREATE BOM creates a new m_bom row with correct fields.
     */
    @Test
    @Order(1)
    void w_sy_1_createBom() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "CREATE BOM SY_TEST_KIT TYPE SET CATEGORY KT DOC_SUB_TYPE SY");

        assertTrue(result.pass(), "CREATE BOM should pass: " + result.summary());
        assertEquals("CREATE BOM", result.verb());

        // Verify in DB
        MBOM bom = MBOM.get(conn, "SY_TEST_KIT");
        assertNotNull(bom, "SY_TEST_KIT should exist in DB");
        assertEquals("SET", bom.getBomType());
        assertEquals("KT", bom.getBomCategory());
        assertEquals("SY", bom.getDocSubType());
        assertEquals("ROOM", bom.getGroupBy());
        assertTrue(bom.isActive());
    }

    /**
     * W-SY-2: CREATE BOM rejects duplicate bom_id.
     */
    @Test
    @Order(2)
    void w_sy_2_createBomDuplicate() {
        VerbResult<?> result = registry.dispatch(ctx,
            "CREATE BOM SY_TEST_KIT TYPE SET CATEGORY KT");

        assertFalse(result.pass(), "duplicate CREATE should fail");
        assertTrue(result.summary().contains("already exists"));
    }

    /**
     * W-SY-3: CREATE BOM rejects non-SY_ prefix.
     */
    @Test
    @Order(3)
    void w_sy_3_createBomNoSyPrefix() {
        VerbResult<?> result = registry.dispatch(ctx,
            "CREATE BOM ROGUE_KIT TYPE SET CATEGORY KT");

        assertFalse(result.pass(), "non-SY_ prefix should fail");
        assertTrue(result.summary().contains("SY_ prefix"));
    }

    // ── W-SY-4..6: ADD LINE ─────────────────────────────────────────────

    /**
     * W-SY-4: ADD LINE creates a m_bom_line row.
     */
    @Test
    @Order(4)
    void w_sy_4_addLine() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "ADD LINE TO SY_TEST_KIT CHILD Base_Cabinet ROLE BASE_1 SEQ 10 DX 0.0");

        assertTrue(result.pass(), "ADD LINE should pass: " + result.summary());
        assertEquals("ADD LINE", result.verb());

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_TEST_KIT");
        assertEquals(1, children.size());
        assertEquals("Base_Cabinet", children.get(0).getChildProductId());
        assertEquals("BASE_1", children.get(0).getRole());
        assertEquals(10, children.get(0).getSequence());
        assertEquals("BUY", children.get(0).getComponentType(),
            "Base_Cabinet is not a known m_bom → should default to BUY");
    }

    /**
     * W-SY-5: ADD LINE second child with offset.
     */
    @Test
    @Order(5)
    void w_sy_5_addLineWithOffset() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "ADD LINE TO SY_TEST_KIT CHILD Upper_Cabinet ROLE UPPER_1 SEQ 20 DX 0.6 DZ 1.2");

        assertTrue(result.pass(), "ADD LINE should pass: " + result.summary());

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_TEST_KIT");
        assertEquals(2, children.size());

        MBOMLine second = children.get(1); // seq=20
        assertEquals(0.6, second.getDx(), 0.0001);
        assertEquals(1.2, second.getDz(), 0.0001);
    }

    /**
     * W-SY-6: ADD LINE with explicit COMPONENT_TYPE override.
     */
    @Test
    @Order(6)
    void w_sy_6_addLineExplicitComponentType() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "ADD LINE TO SY_TEST_KIT CHILD Spacer ROLE BUFFER SEQ 30 COMPONENT_TYPE PHANTOM");

        assertTrue(result.pass(), "ADD LINE should pass: " + result.summary());

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_TEST_KIT");
        assertEquals(3, children.size());
        MBOMLine third = children.get(2);
        assertEquals("PHANTOM", third.getComponentType());
    }

    // ── W-SY-7..8: SET TACK ─────────────────────────────────────────────

    /**
     * W-SY-7: SET TACK updates dx/dy/dz on a line matched by role.
     */
    @Test
    @Order(7)
    void w_sy_7_setTack() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "SET TACK ON SY_TEST_KIT LINE BASE_1 DX 0.5 DY 0.3");

        assertTrue(result.pass(), "SET TACK should pass: " + result.summary());

        MBOMLine line = SetTackVerb.findLine(conn, "SY_TEST_KIT", "BASE_1");
        assertNotNull(line);
        assertEquals(0.5, line.getDx(), 0.0001);
        assertEquals(0.3, line.getDy(), 0.0001);
        assertEquals(0.0, line.getDz(), 0.0001, "DZ was not specified — should remain 0");
    }

    /**
     * W-SY-8: SET TACK fails for unknown line.
     */
    @Test
    @Order(8)
    void w_sy_8_setTackUnknownLine() {
        VerbResult<?> result = registry.dispatch(ctx,
            "SET TACK ON SY_TEST_KIT LINE NONEXISTENT DX 1.0");

        assertFalse(result.pass(), "unknown line should fail");
    }

    // ── W-SY-9: SET ROTATION ────────────────────────────────────────────

    /**
     * W-SY-9: SET ROTATION updates rotation_rule.
     */
    @Test
    @Order(9)
    void w_sy_9_setRotation() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "SET ROTATION ON SY_TEST_KIT LINE UPPER_1 TO 3.14159");

        assertTrue(result.pass(), "SET ROTATION should pass: " + result.summary());

        MBOMLine line = SetTackVerb.findLine(conn, "SY_TEST_KIT", "UPPER_1");
        assertNotNull(line);
        assertEquals("3.14159", line.getRotationRule());
    }

    // ── W-SY-10: SET DIMENSIONS ─────────────────────────────────────────

    /**
     * W-SY-10: SET DIMENSIONS updates allocated AABB.
     */
    @Test
    @Order(10)
    void w_sy_10_setDimensions() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "SET DIMENSIONS ON SY_TEST_KIT LINE BASE_1 WIDTH 600 DEPTH 500 HEIGHT 800");

        assertTrue(result.pass(), "SET DIMENSIONS should pass: " + result.summary());

        MBOMLine line = SetTackVerb.findLine(conn, "SY_TEST_KIT", "BASE_1");
        assertNotNull(line);
        assertEquals(600, line.getAllocatedWidthMm());
        assertEquals(500, line.getAllocatedDepthMm());
        assertEquals(800, line.getAllocatedHeightMm());
    }

    // ── W-SY-11: REMOVE LINE ────────────────────────────────────────────

    /**
     * W-SY-11: REMOVE LINE by role deletes the line.
     */
    @Test
    @Order(11)
    void w_sy_11_removeLine() throws SQLException {
        // Before: 3 lines (BASE_1, UPPER_1, BUFFER)
        assertEquals(3, MBOMLine.getByBom(conn, "SY_TEST_KIT").size());

        VerbResult<?> result = registry.dispatch(ctx,
            "REMOVE LINE FROM SY_TEST_KIT ROLE BUFFER");

        assertTrue(result.pass(), "REMOVE LINE should pass: " + result.summary());

        // After: 2 lines
        assertEquals(2, MBOMLine.getByBom(conn, "SY_TEST_KIT").size());
    }

    /**
     * W-SY-12: REMOVE LINE by child_product_id.
     */
    @Test
    @Order(12)
    void w_sy_12_removeLineByChild() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "REMOVE LINE FROM SY_TEST_KIT CHILD Upper_Cabinet");

        assertTrue(result.pass(), "REMOVE LINE by CHILD should pass: " + result.summary());
        assertEquals(1, MBOMLine.getByBom(conn, "SY_TEST_KIT").size());
    }

    // ── W-SY-13..14: DELETE BOM ─────────────────────────────────────────

    /**
     * W-SY-13: DELETE BOM removes header + all remaining lines.
     */
    @Test
    @Order(13)
    void w_sy_13_deleteBom() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "DELETE BOM SY_TEST_KIT");

        assertTrue(result.pass(), "DELETE BOM should pass: " + result.summary());

        assertNull(MBOM.get(conn, "SY_TEST_KIT"), "BOM should be deleted");
        assertEquals(0, MBOMLine.getByBom(conn, "SY_TEST_KIT").size(),
            "all lines should be deleted");
    }

    /**
     * W-SY-14: DELETE BOM rejects non-SY_ prefix (safety guard).
     */
    @Test
    @Order(14)
    void w_sy_14_deleteBomNonSy() {
        VerbResult<?> result = registry.dispatch(ctx,
            "DELETE BOM EB_SH");

        assertFalse(result.pass(), "non-SY_ delete should fail");
        assertTrue(result.summary().contains("SY_ prefix"));
    }

    // ── W-SY-15: Registry count ─────────────────────────────────────────

    /**
     * W-SY-15: VerbRegistry has 30 verbs (23 existing + 7 P0 primitives).
     */
    @Test
    @Order(15)
    void w_sy_15_registryCount() {
        assertEquals(30, registry.size(),
            "23 existing + 7 P0 primitives = 30 total verbs");
    }

    // ── W-SY-16: Full lifecycle via dispatch ────────────────────────────

    /**
     * W-SY-16: Full CREATE→ADD→SET TACK→SET ROTATION→SET DIMENSIONS→REMOVE→DELETE lifecycle.
     */
    @Test
    @Order(16)
    void w_sy_16_fullLifecycle() throws SQLException {
        // CREATE
        assertTrue(registry.dispatch(ctx,
            "CREATE BOM SY_DUPLEX_TEST TYPE SET CATEGORY UN DOC_SUB_TYPE SY").pass());

        // ADD two children (mirror pair)
        assertTrue(registry.dispatch(ctx,
            "ADD LINE TO SY_DUPLEX_TEST CHILD HalfUnit ROLE UNIT_A SEQ 10 ROTATION 0").pass());
        assertTrue(registry.dispatch(ctx,
            "ADD LINE TO SY_DUPLEX_TEST CHILD HalfUnit ROLE UNIT_B SEQ 20 ROTATION 3.14159").pass());

        // Verify 2 lines
        assertEquals(2, MBOMLine.getByBom(conn, "SY_DUPLEX_TEST").size());

        // SET TACK on UNIT_B — offset by 8m in X
        assertTrue(registry.dispatch(ctx,
            "SET TACK ON SY_DUPLEX_TEST LINE UNIT_B DX 8.0").pass());

        // SET DIMENSIONS on UNIT_A
        assertTrue(registry.dispatch(ctx,
            "SET DIMENSIONS ON SY_DUPLEX_TEST LINE UNIT_A WIDTH 8000 DEPTH 10000 HEIGHT 6000").pass());

        // Verify combined state
        MBOMLine unitA = SetTackVerb.findLine(conn, "SY_DUPLEX_TEST", "UNIT_A");
        MBOMLine unitB = SetTackVerb.findLine(conn, "SY_DUPLEX_TEST", "UNIT_B");
        assertNotNull(unitA);
        assertNotNull(unitB);
        assertEquals("0", unitA.getRotationRule());
        assertEquals("3.14159", unitB.getRotationRule());
        assertEquals(8.0, unitB.getDx(), 0.0001);
        assertEquals(8000, unitA.getAllocatedWidthMm());

        // REMOVE one line
        assertTrue(registry.dispatch(ctx,
            "REMOVE LINE FROM SY_DUPLEX_TEST ROLE UNIT_B").pass());
        assertEquals(1, MBOMLine.getByBom(conn, "SY_DUPLEX_TEST").size());

        // DELETE
        assertTrue(registry.dispatch(ctx,
            "DELETE BOM SY_DUPLEX_TEST").pass());
        assertNull(MBOM.get(conn, "SY_DUPLEX_TEST"));
    }
}
