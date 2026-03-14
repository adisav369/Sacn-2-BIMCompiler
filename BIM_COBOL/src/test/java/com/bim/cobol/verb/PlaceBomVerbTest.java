package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.verb.PlaceBomVerb.PlaceBomPayload;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for PLACE BOM verb — the first emitting verb.
 *
 * <p>Each test creates an isolated output.db (temp file), runs PLACE BOM SH,
 * and verifies elements were written correctly. No collision with the
 * production pipeline.
 */
class PlaceBomVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;

    /** Shared result — run once, verify thrice. */
    private static VerbResult<PlaceBomPayload> result;

    @BeforeAll
    static void setUp() throws Exception {
        // BOM.db — read-only dictionary
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));

        // Create isolated output.db in temp file
        tempDb = File.createTempFile("place_bom_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        // Create output schema (same as BuildingWriter.initSchema)
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
            stmt.execute("""
                CREATE TABLE base_geometries (
                    geometry_hash TEXT PRIMARY KEY,
                    vertices BLOB NOT NULL,
                    faces BLOB NOT NULL,
                    vertex_count INTEGER NOT NULL,
                    face_count INTEGER NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE element_transforms (
                    guid TEXT PRIMARY KEY,
                    center_x REAL NOT NULL,
                    center_y REAL NOT NULL,
                    center_z REAL NOT NULL,
                    transform_source TEXT DEFAULT 'compiled',
                    FOREIGN KEY (guid) REFERENCES elements_meta(guid)
                )
            """);
            stmt.execute("""
                CREATE TABLE element_instances (
                    guid TEXT PRIMARY KEY,
                    geometry_hash TEXT NOT NULL,
                    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
                )
            """);
        }

        // Execute PLACE BOM SH
        PlaceBomVerb verb = new PlaceBomVerb();
        VerbContext ctx = VerbContext.withOutput(bomConn, null, outputConn);
        result = verb.execute(ctx, "SH");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (outputConn != null) outputConn.close();
        if (bomConn != null) bomConn.close();
        if (tempDb != null) tempDb.delete();
    }

    /**
     * W-COBOL-64: PLACE BOM SH element count = 55.
     * The SH building has exactly 55 BOM-derived elements.
     */
    @Test
    void w_cobol_64_elementCount() throws SQLException {
        assertTrue(result.pass(), "PLACE BOM SH should pass: " + result.summary());
        assertEquals("PLACE BOM", result.verb());

        PlaceBomPayload p = result.payload();
        assertNotNull(p, "payload");
        assertEquals(55, p.placed(),
                "SH element count — expected 55 elements placed, got " + p.placed());
        assertEquals(0, p.errors(), "No placement errors expected");

        // Cross-check with actual DB row count
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            assertTrue(rs.next());
            assertEquals(55, rs.getInt(1), "elements_meta row count");
        }
    }

    /**
     * W-COBOL-65: PLACE BOM SH GUID uniqueness — no duplicate GUIDs.
     */
    @Test
    void w_cobol_65_guidUniqueness() throws SQLException {
        assertTrue(result.pass(), result.summary());

        Set<String> guids = new HashSet<>();
        int total = 0;
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT guid FROM elements_meta")) {
            while (rs.next()) {
                guids.add(rs.getString("guid"));
                total++;
            }
        }
        assertEquals(total, guids.size(),
                "All GUIDs must be unique — found " + (total - guids.size()) + " duplicates");
        assertTrue(total > 0, "At least one element must exist");
    }

    /**
     * W-COBOL-66: PLACE BOM SH all elements have geometry (vertex_count > 8).
     * Every element must have LOD library geometry, not just a bounding box.
     *
     * <p>Known exception: 1 structural slab (STR_MD_SLAB_GROUND_FLOOR_1) has
     * vertex_count=8 because the library mesh IS a box — same as production path.
     */
    @Test
    void w_cobol_66_allElementsHaveGeometry() throws SQLException {
        assertTrue(result.pass(), result.summary());

        // Known structural elements whose library mesh is a box (8 vertices).
        // Same behavior in production emitGlobalPlacementElements path.
        Set<String> knownBoxGuids = Set.of("STR_MD_SLAB_GROUND_FLOOR_1");

        String sql = """
            SELECT m.guid, bg.vertex_count
            FROM elements_meta m
            JOIN element_instances ei ON m.guid = ei.guid
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
        """;

        int checked = 0;
        int unexpectedBox = 0;
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                checked++;
                int vc = rs.getInt("vertex_count");
                String guid = rs.getString("guid");
                if (vc <= 8 && !knownBoxGuids.contains(guid)) {
                    unexpectedBox++;
                    System.err.printf("[W-COBOL-66] Unexpected box geometry: %s vertex_count=%d%n",
                            guid, vc);
                }
            }
        }
        assertEquals(55, checked, "All 55 elements should have geometry records");
        assertEquals(0, unexpectedBox,
                unexpectedBox + " elements have unexpected box-only geometry (vertex_count <= 8)");
    }
}
