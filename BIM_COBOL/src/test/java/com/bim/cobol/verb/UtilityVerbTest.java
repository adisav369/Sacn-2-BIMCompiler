package com.bim.cobol.verb;

import com.bim.cobol.*;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for §18.4 SET LINE PROPERTY + §18.5 Utility Verbs + VerbLogger.
 *
 * <p>W-SY-17..28: SET LINE PROPERTY, EXTRACT AABB, SNAP TO GRID,
 * VALIDATE AABB, VerbLogger.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UtilityVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File("library/BOM.db");
        assertTrue(source.exists(), "library/BOM.db must exist");
        tempDb = File.createTempFile("utility_verb_test_", ".db");
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

    // ── W-SY-17..18: SET LINE PROPERTY ──────────────────────────────────

    /**
     * W-SY-17: SET LINE PROPERTY updates a string property.
     */
    @Test
    @Order(1)
    void w_sy_17_setLinePropertyString() throws SQLException {
        // Create a BOM + line to test on
        registry.dispatch(ctx, "CREATE BOM SY_PROP_TEST TYPE SET CATEGORY KT DOC_SUB_TYPE SY");
        registry.dispatch(ctx, "ADD LINE TO SY_PROP_TEST CHILD TestItem ROLE ITEM_1 SEQ 10");

        VerbResult<?> result = registry.dispatch(ctx,
            "SET LINE PROPERTY ON SY_PROP_TEST LINE ITEM_1 LOCATOR_REF NORTH_WALL");

        assertTrue(result.pass(), "SET LINE PROPERTY should pass: " + result.summary());

        MBOMLine line = SetTackVerb.findLine(conn, "SY_PROP_TEST", "ITEM_1");
        assertNotNull(line);
        assertEquals("NORTH_WALL", line.getLocatorRef());
    }

    /**
     * W-SY-18: SET LINE PROPERTY updates an integer property.
     */
    @Test
    @Order(2)
    void w_sy_18_setLinePropertyInt() throws SQLException {
        VerbResult<?> result = registry.dispatch(ctx,
            "SET LINE PROPERTY ON SY_PROP_TEST LINE ITEM_1 FIT_PRIORITY 5");

        assertTrue(result.pass(), "SET LINE PROPERTY should pass: " + result.summary());

        MBOMLine line = SetTackVerb.findLine(conn, "SY_PROP_TEST", "ITEM_1");
        assertNotNull(line);
        assertEquals(5, line.getFitPriority());
    }

    /**
     * W-SY-19: SET LINE PROPERTY rejects unknown property.
     */
    @Test
    @Order(3)
    void w_sy_19_setLinePropertyUnknown() {
        VerbResult<?> result = registry.dispatch(ctx,
            "SET LINE PROPERTY ON SY_PROP_TEST LINE ITEM_1 MAGIC_FIELD foo");

        assertFalse(result.pass(), "unknown property should fail");
    }

    // ── W-SY-20..21: EXTRACT AABB ───────────────────────────────────────

    /**
     * W-SY-20: EXTRACT AABB FROM POINTS computes correct AABB in mm.
     */
    @Test
    @Order(4)
    void w_sy_20_extractAabbFromPoints() {
        VerbResult<?> result = registry.dispatch(ctx,
            "EXTRACT AABB FROM POINTS (0,0,0) (12.0,10.0,6.0)");

        assertTrue(result.pass(), "EXTRACT AABB should pass: " + result.summary());
        assertEquals("EXTRACT AABB", result.verb());

        ExtractAabbVerb.AabbPayload p = (ExtractAabbVerb.AabbPayload) result.payload();
        assertEquals(12000, p.widthMm());
        assertEquals(10000, p.depthMm());
        assertEquals(6000, p.heightMm());
    }

    /**
     * W-SY-21: EXTRACT AABB FROM BOM reads child space from existing BOM.
     */
    @Test
    @Order(5)
    void w_sy_21_extractAabbFromBom() {
        // EB_SH exists in BOM.db — it has known children
        VerbResult<?> result = registry.dispatch(ctx,
            "EXTRACT AABB FROM BOM EB_SH");

        assertTrue(result.pass(), "EXTRACT AABB FROM BOM should pass: " + result.summary());

        ExtractAabbVerb.AabbPayload p = (ExtractAabbVerb.AabbPayload) result.payload();
        assertTrue(p.widthMm() > 0, "width should be > 0");
    }

    // ── W-SY-22..23: SNAP TO GRID ──────────────────────────────────────

    /**
     * W-SY-22: SNAP TO GRID AABB snaps up to grid.
     */
    @Test
    @Order(6)
    void w_sy_22_snapToGridAabb() {
        VerbResult<?> result = registry.dispatch(ctx,
            "SNAP TO GRID AABB 11500 9700 SPACING 1000");

        assertTrue(result.pass(), "SNAP TO GRID should pass: " + result.summary());

        SnapToGridVerb.GridSnapPayload p = (SnapToGridVerb.GridSnapPayload) result.payload();
        assertEquals(12000, p.snappedWidthMm(), "11500 → 12000");
        assertEquals(10000, p.snappedDepthMm(), "9700 → 10000");
        assertEquals(1000, p.gridSpacingMm());
    }

    /**
     * W-SY-23: SNAP TO GRID on-grid values unchanged.
     */
    @Test
    @Order(7)
    void w_sy_23_snapToGridNoChange() {
        VerbResult<?> result = registry.dispatch(ctx,
            "SNAP TO GRID AABB 12000 10000 SPACING 1000");

        assertTrue(result.pass(), "SNAP should pass: " + result.summary());

        SnapToGridVerb.GridSnapPayload p = (SnapToGridVerb.GridSnapPayload) result.payload();
        assertEquals(12000, p.snappedWidthMm(), "already on grid");
        assertEquals(10000, p.snappedDepthMm(), "already on grid");
        assertTrue(p.adjustments().isEmpty(), "no adjustments needed");
    }

    // ── W-SY-24..25: VALIDATE AABB ─────────────────────────────────────

    /**
     * W-SY-24: VALIDATE AABB with viable dimensions → pass with candidates.
     */
    @Test
    @Order(8)
    void w_sy_24_validateAabbOk() {
        // LI (living) is a known category with BOMs in catalog
        VerbResult<?> result = registry.dispatch(ctx,
            "VALIDATE AABB 8000 5000 2800 FOR LI");

        assertTrue(result.pass(), "VALIDATE AABB should pass for LI: " + result.summary());

        ValidateAabbVerb.ValidationPayload p = (ValidateAabbVerb.ValidationPayload) result.payload();
        assertTrue(p.valid());
        assertTrue(p.candidateCount() > 0, "should find at least 1 candidate");
        assertNotNull(p.bestFitBomId());
    }

    /**
     * W-SY-25: VALIDATE AABB with unknown category → fail.
     */
    @Test
    @Order(9)
    void w_sy_25_validateAabbUnknownCategory() {
        VerbResult<?> result = registry.dispatch(ctx,
            "VALIDATE AABB 5000 4000 2800 FOR ZZ");

        assertFalse(result.pass(), "unknown category should fail");
    }

    // ── W-SY-26: VerbLogger ─────────────────────────────────────────────

    /**
     * W-SY-26: VerbLogger compact mode produces one-line output.
     */
    @Test
    @Order(10)
    void w_sy_26_verbLoggerCompact() {
        List<String> lines = new ArrayList<>();
        VerbLogger log = VerbLogger.compact(lines::add);

        VerbResult<?> result = registry.dispatch(ctx,
            "EXTRACT AABB FROM POINTS (0,0,0) (5.0,4.0,3.0)");
        log.log(result);

        assertEquals(1, lines.size(), "compact = 1 line");
        assertTrue(lines.get(0).startsWith("[OK]"));
        assertTrue(lines.get(0).contains("EXTRACT AABB"));
    }

    /**
     * W-SY-27: VerbLogger detail mode shows payload fields.
     */
    @Test
    @Order(11)
    void w_sy_27_verbLoggerDetail() {
        List<String> lines = new ArrayList<>();
        VerbLogger log = VerbLogger.detail(lines::add);

        VerbResult<?> result = registry.dispatch(ctx,
            "EXTRACT AABB FROM POINTS (0,0,0) (5.0,4.0,3.0)");
        log.log(result);

        assertTrue(lines.size() > 1, "detail mode should have header + fields");
        assertTrue(lines.get(0).contains("[OK]"));
        // Should contain payload field names
        boolean hasWidth = lines.stream().anyMatch(l -> l.contains("widthMm"));
        assertTrue(hasWidth, "detail should show widthMm field");
    }

    /**
     * W-SY-28: VerbLogger JSON mode produces valid JSON.
     */
    @Test
    @Order(12)
    void w_sy_28_verbLoggerJson() {
        List<String> lines = new ArrayList<>();
        VerbLogger log = VerbLogger.json(lines::add);

        VerbResult<?> result = registry.dispatch(ctx,
            "SNAP TO GRID AABB 11500 9700 SPACING 500");
        log.log(result);

        assertEquals(1, lines.size(), "JSON = 1 output");
        assertTrue(lines.get(0).contains("\"pass\""), "should be JSON");
        assertTrue(lines.get(0).contains("\"verb\""), "should have verb field");
    }

    // ── W-SY-29: Registry count ─────────────────────────────────────────

    /**
     * W-SY-29: VerbRegistry has 34 verbs (30 prior + 4 new).
     */
    @Test
    @Order(13)
    void w_sy_29_registryCount() {
        assertEquals(53, registry.size(),
            "41 prior + 11 L2/L3/L4 + HELLO WORLD = 53");
    }

    // Cleanup
    @Test
    @Order(99)
    void cleanup() {
        registry.dispatch(ctx, "DELETE BOM SY_PROP_TEST");
    }
}
