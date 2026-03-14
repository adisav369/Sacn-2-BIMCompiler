package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for REGISTER BUILDING verb.
 * W-H2-10..12: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegisterBuildingVerbTest {

    private static Connection bomConn;
    private static Connection outputConn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        // BOM.db — read-only dictionary
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));

        // Create isolated output.db in temp file
        tempDb = File.createTempFile("register_building_test_", ".db");
        tempDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        // Create c_order schema
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
    @DisplayName("W-H2-10: REGISTER BUILDING creates c_order with DocStatus=IP")
    void w_h2_10_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "REGISTER BUILDING TestBuilding_SH DOC_SUB_TYPE SH " +
            "NAME \"Test House\" DSL \"building TestBuilding_SH {}\" " +
            "OUTPUT_PATH output/test.db PROVENANCE EXTRACTED " +
            "SEQ 10 EXPECTED 55 GEO_THRESHOLD 3 " +
            "AABB_W 12000 AABB_D 8000 AABB_H 6000");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("REGISTER BUILDING", result.verb());

        RegisterBuildingVerb.RegisterBuildingPayload p =
            (RegisterBuildingVerb.RegisterBuildingPayload) result.payload();
        assertNotNull(p);
        assertEquals("TestBuilding_SH", p.buildingId());
        assertEquals("IP", p.docStatus());

        // Cross-check DB: row exists with DocStatus=IP
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT DocStatus, Name, Provenance, CompilerVersion FROM c_order WHERE C_Order_ID = ?")) {
            ps.setString(1, "TestBuilding_SH");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "c_order row should exist");
                assertEquals("IP", rs.getString("DocStatus"));
                assertEquals("Test House", rs.getString("Name"));
                assertEquals("EXTRACTED", rs.getString("Provenance"));
                assertEquals("BIM-Compiler-1.0", rs.getString("CompilerVersion"));
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H2-11: REGISTER BUILDING fails without outputConn")
    void w_h2_11_noOutputConn() {
        VerbContext bomOnly = VerbContext.ofBom(bomConn);
        VerbResult<?> result = registry.dispatch(bomOnly,
            "REGISTER BUILDING TestBuilding_SH DOC_SUB_TYPE SH NAME \"Test\"");

        assertFalse(result.pass(), "should fail without outputConn");
        assertTrue(result.summary().contains("outputConn"),
            "error should mention outputConn: " + result.summary());
    }

    @Test @Order(3)
    @DisplayName("W-H2-12: REGISTER BUILDING fails without DOC_SUB_TYPE")
    void w_h2_12_missingDocSubType() {
        VerbResult<?> result = registry.dispatch(ctx,
            "REGISTER BUILDING TestBuilding_SH NAME \"Test\"");

        assertFalse(result.pass(), "should fail without DOC_SUB_TYPE");
        assertTrue(result.summary().contains("DOC_SUB_TYPE"),
            "error should mention DOC_SUB_TYPE: " + result.summary());
    }

    @Test @Order(99)
    void cleanup() throws Exception {
        try (Statement stmt = outputConn.createStatement()) {
            stmt.executeUpdate("DELETE FROM c_order WHERE C_Order_ID LIKE 'TestBuilding%'");
        }
    }
}
