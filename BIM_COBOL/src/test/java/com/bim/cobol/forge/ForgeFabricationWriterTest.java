package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forge fabrication writer — verifies ad_forge_fabrication data path.
 * @Traces FORGE_SUITE_SRS.md §9 Part ⑤
 */
class ForgeFabricationWriterTest {

    private static Connection outputConn;
    private static Connection bomConn;

    @BeforeAll
    static void setUp() throws Exception {
        // Create output.db schema in memory
        outputConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = outputConn.createStatement()) {
            // Minimal W_Verb_Node for FK
            stmt.execute("""
                CREATE TABLE W_Verb_Node (
                    W_Verb_Node_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID      TEXT NOT NULL,
                    SeqNo           INTEGER NOT NULL DEFAULT 10,
                    Name            TEXT NOT NULL,
                    Description     TEXT NOT NULL,
                    S_Resource_ID   INTEGER,
                    M_Product_ID    TEXT,
                    IsActive        INTEGER DEFAULT 1,
                    DocStatus       TEXT DEFAULT 'DR',
                    last_result     TEXT,
                    element_count   INTEGER DEFAULT 0,
                    Created         TEXT DEFAULT (datetime('now')),
                    Updated         TEXT DEFAULT (datetime('now'))
                )
            """);

            // ad_forge_fabrication table — same DDL as BuildingWriter.initSchema()
            stmt.execute("""
                CREATE TABLE ad_forge_fabrication (
                    AD_Forge_Fabrication_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                    W_Verb_Node_ID           INTEGER REFERENCES W_Verb_Node(W_Verb_Node_ID),
                    Element_ID               TEXT NOT NULL,
                    PieceType                TEXT NOT NULL,
                    ParamName                TEXT NOT NULL,
                    ParamValue               REAL NOT NULL,
                    Unit                     TEXT
                )
            """);
        }

        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (outputConn != null) outputConn.close();
        if (bomConn != null) bomConn.close();
    }

    @BeforeEach
    void clearTable() throws Exception {
        try (Statement stmt = outputConn.createStatement()) {
            stmt.execute("DELETE FROM ad_forge_fabrication");
        }
    }

    /**
     * W-FORGE-FAB-1: SLOPE_CUT fabrication data writes to ad_forge_fabrication.
     */
    @Test
    void slopeCutFabricationWritesToDb() throws Exception {
        // Compute a SLOPE_CUT result
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbRegistry registry = VerbRegistry.createDefault();
        VerbResult<?> r = registry.dispatch(ctx, "FORGE SLOPE_CUT pitch:30 span:5200 width:90 depth:45");
        assertTrue(r.pass());
        ForgeResult fr = (ForgeResult) r.payload();

        // Write to output.db
        ForgeFabricationWriter writer = new ForgeFabricationWriter(outputConn);
        int rows = writer.write(null, fr);
        assertTrue(rows > 0, "Should write fabrication rows");

        // Verify rows in ad_forge_fabrication
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT PieceType, ParamName, ParamValue, Unit FROM ad_forge_fabrication ORDER BY ParamName")) {
            List<String> params = new ArrayList<>();
            while (rs.next()) {
                assertEquals("SLOPE_CUT", rs.getString("PieceType"));
                params.add(rs.getString("ParamName"));
            }
            // Must have standard dims + fabrication entries
            assertTrue(params.contains("length_mm"), "Should have length_mm");
            assertTrue(params.contains("width_mm"), "Should have width_mm");
            assertTrue(params.contains("depth_mm"), "Should have depth_mm");
            assertTrue(params.contains("cut_angle_top"), "Should have cut_angle_top");
            assertTrue(params.contains("cut_angle_bottom"), "Should have cut_angle_bottom");
        }
    }

    /**
     * W-FORGE-FAB-2: Failed forge result writes zero rows.
     */
    @Test
    void failedResultWritesNothing() throws Exception {
        ForgeResult fail = ForgeResult.fail("SLOPE_CUT", "pitch too steep");
        ForgeFabricationWriter writer = new ForgeFabricationWriter(outputConn);
        int rows = writer.write(null, fail);
        assertEquals(0, rows, "Failed result should write zero rows");
    }

    /**
     * W-FORGE-FAB-3: DOME_SECTION writes 72 records × N params each.
     */
    @Test
    void domeFabricationWritesMultipleRecords() throws Exception {
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbRegistry registry = VerbRegistry.createDefault();
        VerbResult<?> r = registry.dispatch(ctx,
                "FORGE DOME_SECTION radius:8000 rings:6 segments:12 base_z:15000");
        assertTrue(r.pass());
        ForgeResult fr = (ForgeResult) r.payload();
        assertEquals(72, fr.records().size());

        ForgeFabricationWriter writer = new ForgeFabricationWriter(outputConn);
        int rows = writer.write(null, fr);
        // 72 panels × (3 dims + fabrication entries) = many rows
        assertTrue(rows >= 72 * 3, "Should write at least 3 params per record (dims)");

        // Verify distinct Element_IDs
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(DISTINCT Element_ID) FROM ad_forge_fabrication")) {
            rs.next();
            assertEquals(72, rs.getInt(1), "72 distinct element IDs for 72 panels");
        }
    }

    /**
     * W-FORGE-FAB-4: Unit inference works correctly.
     */
    @Test
    void unitInference() {
        assertEquals("mm", ForgeFabricationWriter.inferUnit("length_mm"));
        assertEquals("mm", ForgeFabricationWriter.inferUnit("birdsmouth_depth"));
        assertEquals("deg", ForgeFabricationWriter.inferUnit("cut_angle_top"));
        assertEquals("ea", ForgeFabricationWriter.inferUnit("step_count"));
        assertNull(ForgeFabricationWriter.inferUnit("some_unknown_param"));
    }

    /**
     * W-FORGE-FAB-5: W_Verb_Node_ID FK is nullable (standalone forge calls).
     */
    @Test
    void nullVerbNodeIdAllowed() throws Exception {
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbRegistry registry = VerbRegistry.createDefault();
        VerbResult<?> r = registry.dispatch(ctx,
                "FORGE PIPE_BEND angle:90 radius:150 diameter:32");
        assertTrue(r.pass());
        ForgeResult fr = (ForgeResult) r.payload();

        ForgeFabricationWriter writer = new ForgeFabricationWriter(outputConn);
        int rows = writer.write(null, fr);
        assertTrue(rows > 0);

        // Verify W_Verb_Node_ID is NULL
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT W_Verb_Node_ID FROM ad_forge_fabrication LIMIT 1")) {
            rs.next();
            assertNull(rs.getObject("W_Verb_Node_ID"), "W_Verb_Node_ID should be NULL");
        }
    }
}
