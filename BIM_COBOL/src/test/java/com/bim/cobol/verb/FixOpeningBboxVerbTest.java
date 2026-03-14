package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for FIX OPENING BBOX verb.
 * W-H3-04..06: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FixOpeningBboxVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));

        tempDb = File.createTempFile("fix_opening_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        try (Statement stmt = outputConn.createStatement()) {
            stmt.execute("""
                CREATE TABLE elements_meta (
                    id INTEGER PRIMARY KEY,
                    guid TEXT UNIQUE NOT NULL,
                    discipline TEXT NOT NULL,
                    ifc_class TEXT NOT NULL,
                    element_name TEXT,
                    element_type TEXT,
                    storey TEXT,
                    fire_rating_hr REAL,
                    material_name TEXT,
                    material_rgba TEXT
                )
                """);
            stmt.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id, minX, maxX, minY, maxY, minZ, maxZ
                )
                """);

            // Seed a door and a window
            stmt.execute("""
                INSERT INTO elements_meta VALUES
                    (1, 'DOOR_MD_DOOR_1_GROUND_FLOOR', 'ARC', 'IfcDoor', 'Front Door', 'DOOR',
                     'Ground Floor', NULL, NULL, NULL)
                """);
            stmt.execute("INSERT INTO elements_rtree VALUES (1, 0.0, 1.0, 0.0, 0.2, 0.0, 2.1)");

            stmt.execute("""
                INSERT INTO elements_meta VALUES
                    (2, 'WINDOW_MD_WIN_1_GROUND_FLOOR', 'ARC', 'IfcWindow', 'Living Window', 'WINDOW',
                     'Ground Floor', NULL, NULL, NULL)
                """);
            stmt.execute("INSERT INTO elements_rtree VALUES (2, 2.0, 3.0, 0.0, 0.2, 0.9, 2.1)");
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
    @DisplayName("W-H3-04: FIX OPENING BBOX updates door bounding box")
    void w_h3_04_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "FIX OPENING BBOX SH GUID DOOR_MD_DOOR_1_GROUND_FLOOR " +
            "MINX 0.5 MAXX 1.4 MINY 0.0 MAXY 0.15 MINZ 0.0 MAXZ 2.2");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("FIX OPENING BBOX", result.verb());

        FixOpeningBboxVerb.FixOpeningPayload p =
            (FixOpeningBboxVerb.FixOpeningPayload) result.payload();
        assertNotNull(p, "payload should not be null");
        assertEquals("SH", p.docSubType());
        assertEquals("DOOR_MD_DOOR_1_GROUND_FLOOR", p.guid());
        assertEquals(1, p.elementId());

        // Cross-check DB: R-tree updated
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT minX, maxX, maxZ FROM elements_rtree WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "rtree row should exist");
                assertEquals(0.5, rs.getDouble("minX"), 0.001);
                assertEquals(1.4, rs.getDouble("maxX"), 0.001);
                assertEquals(2.2, rs.getDouble("maxZ"), 0.001);
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H3-05: FIX OPENING BBOX fails for unknown GUID")
    void w_h3_05_unknownGuid() {
        VerbResult<?> result = registry.dispatch(ctx,
            "FIX OPENING BBOX SH GUID NONEXISTENT_DOOR " +
            "MINX 0 MAXX 1 MINY 0 MAXY 1 MINZ 0 MAXZ 1");

        assertFalse(result.pass(), "should fail for unknown GUID");
        assertTrue(result.summary().contains("no element found"),
            "error should mention missing element: " + result.summary());
    }

    @Test @Order(3)
    @DisplayName("W-H3-06: FIX OPENING BBOX window coords match verb input exactly")
    void w_h3_06_integrityCheck() throws Exception {
        // Fix window bbox
        VerbResult<?> result = registry.dispatch(ctx,
            "FIX OPENING BBOX SH GUID WINDOW_MD_WIN_1_GROUND_FLOOR " +
            "MINX 2.5 MAXX 3.5 MINY 0.0 MAXY 0.15 MINZ 0.85 MAXZ 2.15");
        assertTrue(result.pass(), "window fix should pass: " + result.summary());

        // Verify all 6 coords match input exactly
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT minX, maxX, minY, maxY, minZ, maxZ FROM elements_rtree WHERE id = 2")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "rtree row should exist");
                assertEquals(2.5,  rs.getDouble("minX"), 0.001, "minX");
                assertEquals(3.5,  rs.getDouble("maxX"), 0.001, "maxX");
                assertEquals(0.0,  rs.getDouble("minY"), 0.001, "minY");
                assertEquals(0.15, rs.getDouble("maxY"), 0.001, "maxY");
                assertEquals(0.85, rs.getDouble("minZ"), 0.001, "minZ");
                assertEquals(2.15, rs.getDouble("maxZ"), 0.001, "maxZ");
            }
        }
    }
}
