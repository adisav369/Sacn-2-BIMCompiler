package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for BUILD SPATIAL STRUCTURE verb.
 * W-H3-07..09: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BuildSpatialStructureVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));

        tempDb = File.createTempFile("spatial_structure_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        outputConn.setAutoCommit(false);

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
                CREATE TABLE spatial_structure (
                    guid TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    name TEXT,
                    parent_guid TEXT,
                    object_type TEXT,
                    predefined_type TEXT
                )
                """);
            stmt.execute("""
                CREATE TABLE rel_contained_in_space (
                    element_guid TEXT PRIMARY KEY,
                    space_guid TEXT NOT NULL,
                    FOREIGN KEY (space_guid) REFERENCES spatial_structure(guid)
                )
                """);
            stmt.execute("""
                CREATE TABLE co_empty_space (
                    co_emptyspace_id INTEGER PRIMARY KEY,
                    c_order_id TEXT,
                    bom_id TEXT,
                    before_x_mm REAL, before_y_mm REAL, before_z_mm REAL,
                    next_x_mm REAL, next_y_mm REAL, next_z_mm REAL,
                    doc_status TEXT DEFAULT 'DR',
                    created TEXT DEFAULT (datetime('now')),
                    updated TEXT DEFAULT (datetime('now'))
                )
                """);
            stmt.execute("""
                CREATE TABLE co_empty_space_line (
                    line_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    co_emptyspace_id INTEGER NOT NULL,
                    bom_line_seq INTEGER NOT NULL,
                    bom_id TEXT NOT NULL,
                    bom_line_role TEXT,
                    bom_level INTEGER DEFAULT 0,
                    before_x_mm REAL, before_y_mm REAL, before_z_mm REAL,
                    next_x_mm REAL, next_y_mm REAL, next_z_mm REAL,
                    orientation_rad REAL DEFAULT 0,
                    capacity_mm REAL, filled_mm REAL DEFAULT 0, remaining_mm REAL,
                    storey TEXT, room_name TEXT, locator_ref TEXT,
                    c_orderline_id INTEGER,
                    doc_status TEXT DEFAULT 'DR',
                    created TEXT DEFAULT (datetime('now')),
                    updated TEXT DEFAULT (datetime('now'))
                )
                """);

            // Seed IfcBuilding
            stmt.execute("""
                INSERT INTO spatial_structure VALUES
                    ('BUILDING_SH', 'IfcBuilding', 'Sample House', NULL, NULL, NULL)
                """);

            // Seed elements with storeys
            stmt.execute("""
                INSERT INTO elements_meta VALUES
                    (1, 'WALL_1', 'ARC', 'IfcWall', 'Wall 1', 'WALL', 'Ground Floor', NULL, NULL, NULL)
                """);
            stmt.execute("INSERT INTO elements_rtree VALUES (1, 0.0, 5.0, 0.0, 0.2, 0.0, 2.7)");

            stmt.execute("""
                INSERT INTO elements_meta VALUES
                    (2, 'FURN_1', 'ARC', 'IfcFurniture', 'Sofa', 'FURNITURE', 'Ground Floor', NULL, NULL, NULL)
                """);
            stmt.execute("INSERT INTO elements_rtree VALUES (2, 1.0, 3.0, 1.0, 2.0, 0.0, 0.8)");

            // Seed L2 co_empty_space_line (room) — coords in mm
            stmt.execute("INSERT INTO co_empty_space VALUES (1, 'SH', 'BUILDING_SH_STD', 0, 0, 0, 12000, 8000, 6000, 'CO', datetime('now'), datetime('now'))");
            stmt.execute("""
                INSERT INTO co_empty_space_line
                    (co_emptyspace_id, bom_line_seq, bom_id, bom_line_role, bom_level,
                     before_x_mm, before_y_mm, before_z_mm, next_x_mm, next_y_mm, next_z_mm,
                     storey, room_name)
                VALUES
                    (1, 1, 'FLOOR_SH_GF_STD', 'LI', 2,
                     0, 0, 0, 6000, 4000, 2700,
                     'Ground Floor', 'Living Room')
                """);
        }
        outputConn.commit();

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
    @DisplayName("W-H3-07: BUILD SPATIAL STRUCTURE adds storeys, spaces, containment")
    void w_h3_07_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx, "BUILD SPATIAL STRUCTURE SH");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("BUILD SPATIAL STRUCTURE", result.verb());

        BuildSpatialStructureVerb.SpatialStructurePayload p =
            (BuildSpatialStructureVerb.SpatialStructurePayload) result.payload();
        assertNotNull(p, "payload should not be null");
        assertEquals("SH", p.docSubType());
        assertTrue(p.storeysAdded() >= 1, "at least 1 storey added (Ground Floor)");
        assertTrue(p.spacesEmitted() >= 1, "at least 1 space emitted (Living Room)");
        assertTrue(p.storeyContained() >= 1, "at least 1 storey containment");

        // Verify IfcBuildingStorey created
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcBuildingStorey'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1, "at least 1 storey in spatial_structure");
            }
        }

        // Verify IfcSpace created
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcSpace'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1, "at least 1 space in spatial_structure");
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H3-08: BUILD SPATIAL STRUCTURE fails without outputConn")
    void w_h3_08_noOutputConn() {
        VerbContext bomOnly = VerbContext.ofBom(bomConn);
        VerbResult<?> result = registry.dispatch(bomOnly, "BUILD SPATIAL STRUCTURE SH");

        assertFalse(result.pass(), "should fail without outputConn");
        assertTrue(result.summary().contains("outputConn"),
            "error should mention outputConn: " + result.summary());
    }

    @Test @Order(3)
    @DisplayName("W-H3-09: BUILD SPATIAL STRUCTURE containment links elements to spaces")
    void w_h3_09_integrityCheck() throws Exception {
        // After W-H3-07 ran, containment should link elements to storeys/spaces
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM rel_contained_in_space")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 1, "at least 1 containment relationship");
        }

        // FURN_1 centroid (2.0, 1.5, 0.4) is inside Living Room AABB (0-6m, 0-4m, 0-2.7m)
        // It should be contained in the Living Room space
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT space_guid FROM rel_contained_in_space WHERE element_guid = 'FURN_1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "FURN_1 should have space containment");
                String spaceGuid = rs.getString("space_guid");
                // Should be in either the storey or the room space
                assertTrue(spaceGuid.startsWith("SPACE_") || spaceGuid.startsWith("STOREY_"),
                    "space_guid should be a space or storey: " + spaceGuid);
            }
        }
    }
}
