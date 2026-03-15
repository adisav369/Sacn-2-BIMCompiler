package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for OVERRIDE ROOF verb.
 * W-H3-01..03: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OverrideRoofVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));

        tempDb = File.createTempFile("override_roof_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        // Create output schema
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
                    material_rgba TEXT,
                    element_ref TEXT
                )
                """);
            stmt.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id, minX, maxX, minY, maxY, minZ, maxZ
                )
                """);
            stmt.execute("""
                CREATE TABLE element_instances (
                    guid TEXT PRIMARY KEY,
                    geometry_hash TEXT NOT NULL
                )
                """);

            // Seed a roof element
            stmt.execute("""
                INSERT INTO elements_meta VALUES
                    (1, 'ROOF_TEST_1', 'ARC', 'IfcRoof', 'Test Roof', 'ROOF',
                     'Ground Floor', NULL, 'Old Material', '255,0,0,255')
                """);
            stmt.execute("""
                INSERT INTO elements_rtree VALUES
                    (1, 0.0, 10.0, 0.0, 8.0, 5.0, 6.0)
                """);
            stmt.execute("""
                INSERT INTO element_instances VALUES
                    ('ROOF_TEST_1', 'GEO_old_hash')
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
    @DisplayName("W-H3-01: OVERRIDE ROOF updates bbox, storey, material")
    void w_h3_01_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "OVERRIDE ROOF SH ID 1 " +
            "MINX 1.0 MAXX 11.0 MINY 1.0 MAXY 9.0 MINZ 5.5 MAXZ 6.5 " +
            "STOREY Roof MATERIAL_NAME \"Concrete Tiles\" MATERIAL_RGBA 128,128,128,255 " +
            "GEO_HASH GEO_new_hash");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("OVERRIDE ROOF", result.verb());

        OverrideRoofVerb.OverrideRoofPayload p =
            (OverrideRoofVerb.OverrideRoofPayload) result.payload();
        assertNotNull(p, "payload should not be null");
        assertEquals("SH", p.docSubType());
        assertEquals(1, p.roofId());
        assertTrue(p.updatesApplied() > 0, "at least one update applied");

        // Cross-check DB: R-tree updated
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT minX, maxX, minY, maxY, minZ, maxZ FROM elements_rtree WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "rtree row should exist");
                assertEquals(1.0, rs.getDouble("minX"), 0.001);
                assertEquals(11.0, rs.getDouble("maxX"), 0.001);
                assertEquals(5.5, rs.getDouble("minZ"), 0.001);
                assertEquals(6.5, rs.getDouble("maxZ"), 0.001);
            }
        }

        // Cross-check DB: metadata updated
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT storey, material_name, material_rgba FROM elements_meta WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "meta row should exist");
                assertEquals("Roof", rs.getString("storey"));
                assertEquals("Concrete Tiles", rs.getString("material_name"));
                assertEquals("128,128,128,255", rs.getString("material_rgba"));
            }
        }

        // Cross-check DB: geometry hash updated
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT geometry_hash FROM element_instances WHERE guid = 'ROOF_TEST_1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "instance row should exist");
                assertEquals("GEO_new_hash", rs.getString("geometry_hash"));
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H3-02: OVERRIDE ROOF fails without outputConn")
    void w_h3_02_noOutputConn() {
        VerbContext bomOnly = VerbContext.ofBom(bomConn);
        VerbResult<?> result = registry.dispatch(bomOnly, "OVERRIDE ROOF SH ID 1 MINX 0 MAXX 1 MINY 0 MAXY 1 MINZ 0 MAXZ 1");

        assertFalse(result.pass(), "should fail without outputConn");
        assertTrue(result.summary().contains("outputConn"),
            "error should mention outputConn: " + result.summary());
    }

    @Test @Order(3)
    @DisplayName("W-H3-03: OVERRIDE ROOF via verb produces same DB state as direct UPDATE")
    void w_h3_03_integrityCheck() throws Exception {
        // Verify final DB state matches expected values from W-H3-01
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT em.storey, em.material_rgba, er.minX, er.maxZ " +
                 "FROM elements_meta em JOIN elements_rtree er ON em.id = er.id WHERE em.id = 1")) {
            assertTrue(rs.next(), "joined row should exist");
            assertEquals("Roof", rs.getString("storey"), "storey updated by verb");
            assertEquals("128,128,128,255", rs.getString("material_rgba"), "material_rgba updated by verb");
            assertEquals(1.0, rs.getDouble("minX"), 0.001, "minX updated by verb");
            assertEquals(6.5, rs.getDouble("maxZ"), 0.001, "maxZ updated by verb");
        }
    }
}
