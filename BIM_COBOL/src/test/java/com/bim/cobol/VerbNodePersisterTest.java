package com.bim.cobol;

import com.bim.ormsandbox.po.M_PP_Order_Node;
import com.bim.ormsandbox.po.M_PP_Order_NodeProduct;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for VerbNodePersister — verb results → PP_Order_Node round-trip.
 *
 * <p>Uses in-memory SQLite — no file dependency.
 */
@DisplayName("VerbNodePersister — Verb Result to PP_Order_Node")
class VerbNodePersisterTest {

    private Connection conn;

    @BeforeEach
    void setup() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE c_order (building_id TEXT PRIMARY KEY)");
            s.execute("INSERT INTO c_order VALUES ('TEST_BUILDING')");
            s.execute("""
                CREATE TABLE PP_Order_Node (
                    PP_Order_Node_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID        TEXT NOT NULL REFERENCES c_order(building_id),
                    SeqNo             INTEGER NOT NULL DEFAULT 10,
                    Name              TEXT NOT NULL,
                    Description       TEXT NOT NULL,
                    S_Resource_ID     INTEGER,
                    M_Product_ID      TEXT,
                    IsActive          INTEGER DEFAULT 1,
                    DocStatus         TEXT DEFAULT 'DR'
                        CHECK(DocStatus IN ('DR','IP','CO','VO')),
                    last_result       TEXT,
                    element_count     INTEGER DEFAULT 0,
                    Created           TEXT DEFAULT (datetime('now')),
                    Updated           TEXT DEFAULT (datetime('now'))
                )
            """);
            s.execute("""
                CREATE TABLE PP_Order_NodeProduct (
                    PP_Order_NodeProduct_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                    PP_Order_Node_ID         INTEGER NOT NULL
                        REFERENCES PP_Order_Node(PP_Order_Node_ID),
                    Name                     TEXT NOT NULL,
                    Value                    TEXT NOT NULL,
                    ValueType                TEXT DEFAULT 'TEXT'
                        CHECK(ValueType IN ('TEXT','REAL','INTEGER')),
                    UNIQUE(PP_Order_Node_ID, Name)
                )
            """);
        }
    }

    @AfterEach
    void teardown() throws SQLException {
        if (conn != null) conn.close();
    }

    @Test
    @DisplayName("W-COBOL-57: persist() writes PP_Order_Node rows from ScriptReport")
    void persistScriptReport() throws SQLException {
        // Simulate a 3-line script execution
        VerbResult<String> r1 = VerbResult.ok("CHECK BOM", "BOM valid: 5 lines", "ok");
        VerbResult<String> r2 = VerbResult.ok("TILE SURFACE", "ROOF: 15×294 = 4410 tiles", "ok");
        VerbResult<String> r3 = VerbResult.fail("CHECK CLASH", "2 clashes detected", "fail");

        ScriptRunner.ScriptReport report = new ScriptRunner.ScriptReport(
                List.of(r1, r2, r3), 2, 1, 3);

        List<String> lines = List.of(
                "CHECK BOM TB_LKTN",
                "TILE SURFACE ROOF_WEST WITH PLATE ORIGIN 92.49 -42.16 19.0 GRID 15 294 STEP 495 150",
                "CHECK CLASH TB_LKTN \"Ground Floor\"");

        VerbNodePersister persister = new VerbNodePersister(conn, "TEST_BUILDING");
        int count = persister.persist(lines, report);
        assertEquals(3, count, "3 verb lines → 3 PP_Order_Node rows");

        // Verify read-back
        List<M_PP_Order_Node> nodes = M_PP_Order_Node.getByOrder(conn, "TEST_BUILDING");
        assertEquals(3, nodes.size());

        // Sorted by SeqNo: 10, 20, 30
        assertEquals(10, nodes.get(0).getSeqNo());
        assertEquals("CHECK BOM", nodes.get(0).getName());
        assertEquals("CO", nodes.get(0).getDocStatus()); // pass → CO
        assertTrue(nodes.get(0).getLastResult().contains("CHECK BOM"));

        assertEquals(20, nodes.get(1).getSeqNo());
        assertEquals("TILE SURFACE", nodes.get(1).getName());
        assertEquals("CO", nodes.get(1).getDocStatus());

        assertEquals(30, nodes.get(2).getSeqNo());
        assertEquals("CHECK CLASH", nodes.get(2).getName());
        assertEquals("VO", nodes.get(2).getDocStatus()); // fail → VO (voided)
    }

    @Test
    @DisplayName("W-COBOL-58: persistOne() writes node + structured parameters")
    void persistOneWithParams() throws SQLException {
        VerbResult<String> result = VerbResult.ok("TILE SURFACE",
                "ROOF_WEST: 15×294 = 4410 tiles, step 495×150mm", "ok");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("SURFACE_NAME", "ROOF_DECK_Z19_WEST");
        params.put("ORIGIN_X", "92.49");
        params.put("ORIGIN_Y", "-42.16");
        params.put("ORIGIN_Z", "19.0");
        params.put("GRID_NX", "15");
        params.put("GRID_NY", "294");
        params.put("STEP_X_MM", "495");
        params.put("STEP_Y_MM", "150");

        VerbNodePersister persister = new VerbNodePersister(conn, "TEST_BUILDING");
        int nodeId = persister.persistOne(10,
                "TILE SURFACE ROOF_WEST WITH PLATE ORIGIN 92.49 -42.16 19.0 GRID 15 294 STEP 495 150",
                result, params);
        assertTrue(nodeId > 0);

        // Read back node
        M_PP_Order_Node node = M_PP_Order_Node.get(conn, nodeId);
        assertNotNull(node);
        assertEquals("TILE SURFACE", node.getName());
        assertEquals("CO", node.getDocStatus());

        // Read back params (sorted by Name)
        List<M_PP_Order_NodeProduct> nodeParams = M_PP_Order_NodeProduct.getByNode(conn, nodeId);
        assertEquals(8, nodeParams.size());

        // Sorted alphabetically: GRID_NX, GRID_NY, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, STEP_X_MM, STEP_Y_MM, SURFACE_NAME
        assertEquals("GRID_NX", nodeParams.get(0).getName());
        assertEquals("15", nodeParams.get(0).getValue());
        assertEquals("INTEGER", nodeParams.get(0).getValueType());
        assertEquals(15, nodeParams.get(0).getValueAsInt());

        assertEquals("ORIGIN_X", nodeParams.get(2).getName());
        assertEquals("92.49", nodeParams.get(2).getValue());
        assertEquals("REAL", nodeParams.get(2).getValueType());
        assertEquals(92.49, nodeParams.get(2).getValueAsDouble(), 0.001);

        assertEquals("SURFACE_NAME", nodeParams.get(7).getName());
        assertEquals("ROOF_DECK_Z19_WEST", nodeParams.get(7).getValue());
        assertEquals("TEXT", nodeParams.get(7).getValueType());
    }

    @Test
    @DisplayName("W-COBOL-59: inferType correctly classifies INTEGER, REAL, TEXT")
    void inferType() {
        assertEquals("INTEGER", VerbNodePersister.inferType("42"));
        assertEquals("INTEGER", VerbNodePersister.inferType("0"));
        assertEquals("INTEGER", VerbNodePersister.inferType("-7"));
        assertEquals("REAL", VerbNodePersister.inferType("3.14"));
        assertEquals("REAL", VerbNodePersister.inferType("92.49"));
        assertEquals("REAL", VerbNodePersister.inferType("-42.16"));
        assertEquals("TEXT", VerbNodePersister.inferType("ROOF_WEST"));
        assertEquals("TEXT", VerbNodePersister.inferType(""));
        assertEquals("TEXT", VerbNodePersister.inferType(null));
    }

    @Test
    @DisplayName("W-COBOL-60: live verb dispatch → persist → read-back round-trip")
    void liveVerbRoundTrip() throws SQLException {
        // Execute a real verb via the registry
        VerbRegistry registry = VerbRegistry.createDefault();
        VerbContext ctx = VerbContext.ofBom(null); // TILE SURFACE needs no DB

        String line = "TILE SURFACE test_roof WITH plate ORIGIN 0 0 0 GRID 3 4 STEP 500 150";
        VerbResult<?> result = registry.dispatch(ctx, line);
        assertTrue(result.pass(), "TILE SURFACE must pass: " + result.summary());

        // Persist with structured params
        Map<String, String> params = new LinkedHashMap<>();
        params.put("SURFACE_NAME", "test_roof");
        params.put("GRID_NX", "3");
        params.put("GRID_NY", "4");
        params.put("STEP_X_MM", "500");
        params.put("STEP_Y_MM", "150");

        VerbNodePersister persister = new VerbNodePersister(conn, "TEST_BUILDING");
        int nodeId = persister.persistOne(10, line, result, params);

        // Read back and verify
        M_PP_Order_Node node = M_PP_Order_Node.get(conn, nodeId);
        assertNotNull(node);
        assertEquals("TILE SURFACE", node.getName());
        assertEquals("CO", node.getDocStatus());
        assertTrue(node.getElementCount() > 0 || node.getLastResult().contains("12"),
                "TILE 3×4 = 12 elements should be captured");

        // Verify params
        List<M_PP_Order_NodeProduct> nodeParams = M_PP_Order_NodeProduct.getByNode(conn, nodeId);
        assertEquals(5, nodeParams.size());
        assertEquals("GRID_NX", nodeParams.get(0).getName());
        assertEquals(3, nodeParams.get(0).getValueAsInt());
    }
}
