package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for COMPLETE BUILDING verb.
 * W-H2-13..15: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompleteBuildingVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));

        tempDb = File.createTempFile("complete_building_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        // Create c_order schema + seed an IP building
        try (Statement stmt = outputConn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS c_order (
                    C_Order_ID TEXT PRIMARY KEY,
                    Name TEXT,
                    DSLContent TEXT,
                    OutputDbPath TEXT,
                    ReferenceDbPath TEXT,
                    IsActive INTEGER DEFAULT 1,
                    SeqNo INTEGER DEFAULT 0,
                    ExpectedElements INTEGER DEFAULT 0,
                    Provenance TEXT,
                    Description TEXT,
                    GeometryFailThreshold INTEGER DEFAULT 0,
                    DocStatus TEXT DEFAULT 'DR',
                    AabbWidthMm REAL,
                    AabbDepthMm REAL,
                    AabbHeightMm REAL,
                    CompiledAt TEXT,
                    CompilerVersion TEXT,
                    C_DocType_ID TEXT,
                    SpatialDigest TEXT,
                    EmptySpaceChecksum TEXT
                )
                """);
            stmt.executeUpdate("""
                INSERT INTO c_order (C_Order_ID, Name, DocStatus)
                VALUES ('TestComplete_SH', 'Test House', 'IP')
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
    @DisplayName("W-H2-13: COMPLETE BUILDING promotes DocStatus IP->CO with digest")
    void w_h2_13_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "COMPLETE BUILDING TestComplete_SH " +
            "DIGEST abc123def456 ELEMENTS 55 CHECKSUM chk789");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("COMPLETE BUILDING", result.verb());

        CompleteBuildingVerb.CompleteBuildingPayload p =
            (CompleteBuildingVerb.CompleteBuildingPayload) result.payload();
        assertNotNull(p);
        assertEquals("TestComplete_SH", p.buildingId());
        assertEquals("CO", p.docStatus());
        assertTrue(p.updated());

        // Cross-check DB
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT DocStatus, SpatialDigest, ExpectedElements, EmptySpaceChecksum " +
                "FROM c_order WHERE C_Order_ID = ?")) {
            ps.setString(1, "TestComplete_SH");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "c_order row should exist");
                assertEquals("CO", rs.getString("DocStatus"));
                assertEquals("abc123def456", rs.getString("SpatialDigest"));
                assertEquals(55, rs.getInt("ExpectedElements"));
                assertEquals("chk789", rs.getString("EmptySpaceChecksum"));
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H2-14: COMPLETE BUILDING fails for nonexistent building")
    void w_h2_14_nonexistent() {
        VerbResult<?> result = registry.dispatch(ctx,
            "COMPLETE BUILDING NONEXISTENT_999 DIGEST x ELEMENTS 0 CHECKSUM y");

        assertFalse(result.pass(), "should fail for nonexistent building");
        assertTrue(result.summary().contains("no c_order"),
            "should mention no c_order: " + result.summary());
    }

    @Test @Order(3)
    @DisplayName("W-H2-15: COMPLETE BUILDING fails without outputConn")
    void w_h2_15_noOutputConn() {
        VerbContext bomOnly = VerbContext.ofBom(bomConn);
        VerbResult<?> result = registry.dispatch(bomOnly,
            "COMPLETE BUILDING TestComplete_SH DIGEST x ELEMENTS 0 CHECKSUM y");

        assertFalse(result.pass(), "should fail without outputConn");
        assertTrue(result.summary().contains("outputConn"),
            "error should mention outputConn: " + result.summary());
    }

    @Test @Order(99)
    void cleanup() throws Exception {
        try (Statement stmt = outputConn.createStatement()) {
            stmt.executeUpdate("DELETE FROM c_order WHERE C_Order_ID LIKE 'TestComplete%'");
        }
    }
}
