package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for VOID EMPTY_SPACE FOR BUILDING verb.
 * W-H2+1..3: happy path, no rows, missing arg.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoidEmptySpaceVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");

        tempDb = File.createTempFile("void_empty_space_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        // Create wm_empty_storage_line schema + seed test data
        try (Statement stmt = outputConn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wm_empty_storage_line (
                    line_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    building_type TEXT NOT NULL,
                    storey        TEXT NOT NULL,
                    room_name     TEXT NOT NULL,
                    locator_ref   TEXT NOT NULL,
                    capacity_mm   REAL NOT NULL,
                    filled_mm     REAL NOT NULL DEFAULT 0,
                    remaining_mm  REAL NOT NULL,
                    next_anchor_x_mm REAL NOT NULL DEFAULT 0,
                    next_anchor_y_mm REAL NOT NULL DEFAULT 0,
                    next_anchor_z_mm REAL NOT NULL DEFAULT 0,
                    doc_status    TEXT NOT NULL DEFAULT 'DR',
                    created       TEXT NOT NULL DEFAULT (datetime('now')),
                    updated       TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);
            // Seed: 2 CO lines + 1 already-VO line for TEST_HOUSE
            stmt.executeUpdate("""
                INSERT INTO wm_empty_storage_line
                    (building_type, storey, room_name, locator_ref, capacity_mm, remaining_mm, doc_status)
                VALUES ('TEST_HOUSE', 'Ground', 'LIVING', 'NORTH_WALL', 6500.0, 1300.0, 'CO')
                """);
            stmt.executeUpdate("""
                INSERT INTO wm_empty_storage_line
                    (building_type, storey, room_name, locator_ref, capacity_mm, remaining_mm, doc_status)
                VALUES ('TEST_HOUSE', 'Ground', 'KITCHEN', 'SOUTH_WALL', 4000.0, 800.0, 'CO')
                """);
            stmt.executeUpdate("""
                INSERT INTO wm_empty_storage_line
                    (building_type, storey, room_name, locator_ref, capacity_mm, remaining_mm, doc_status)
                VALUES ('TEST_HOUSE', 'Ground', 'BATH', 'EAST_WALL', 2000.0, 0.0, 'VO')
                """);
        }

        ctx = VerbContext.withOutput(bomConn, null, outputConn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (outputConn != null) outputConn.close();
        if (bomConn != null) bomConn.close();
        if (tempDb != null) tempDb.delete();
    }

    @Test @Order(1)
    @DisplayName("W-H2+1: VOID EMPTY_SPACE voids CO lines, skips already-VO")
    void w_h2plus1_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "VOID EMPTY_SPACE FOR BUILDING TEST_HOUSE");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("VOID EMPTY_SPACE FOR BUILDING", result.verb());

        VoidEmptySpaceVerb.VoidEmptySpacePayload p =
            (VoidEmptySpaceVerb.VoidEmptySpacePayload) result.payload();
        assertNotNull(p);
        assertEquals("TEST_HOUSE", p.buildingType());
        assertEquals(2, p.voidedCount(), "2 CO lines should be voided");

        // Cross-check: all 3 lines now VO
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT COUNT(*) FROM wm_empty_storage_line WHERE building_type = ? AND doc_status = 'VO'")) {
            ps.setString(1, "TEST_HOUSE");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "all 3 lines should be VO");
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H2+2: VOID EMPTY_SPACE on empty table returns 0 voided")
    void w_h2plus2_noRows() {
        VerbResult<?> result = registry.dispatch(ctx,
            "VOID EMPTY_SPACE FOR BUILDING NONEXISTENT_BUILDING");

        assertTrue(result.pass(), "should pass with 0 voided: " + result.summary());

        VoidEmptySpaceVerb.VoidEmptySpacePayload p =
            (VoidEmptySpaceVerb.VoidEmptySpacePayload) result.payload();
        assertEquals(0, p.voidedCount(), "no rows to void");
    }

    @Test @Order(3)
    @DisplayName("W-H2+3: VOID EMPTY_SPACE without args fails")
    void w_h2plus3_missingArg() {
        VerbResult<?> result = registry.dispatch(ctx,
            "VOID EMPTY_SPACE FOR BUILDING");

        assertFalse(result.pass(), "should fail without buildingType arg");
        assertTrue(result.summary().contains("usage"),
            "error should mention usage: " + result.summary());
    }

    @Test @Order(99)
    void cleanup() throws Exception {
        try (Statement stmt = outputConn.createStatement()) {
            stmt.executeUpdate("DELETE FROM wm_empty_storage_line WHERE building_type = 'TEST_HOUSE'");
        }
    }
}
