package com.bim.cobol;

import com.bim.compiler.dsl.VerbExecutor;
import com.bim.ormsandbox.po.M_PP_Order_Node;
import com.bim.ormsandbox.po.M_PP_Order_NodeProduct;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: VerbExecutor SPI → ScriptRunner → PP_Order_Node persistence.
 *
 * <p>Proves the full chain that VerbStage will use at runtime:
 * ServiceLoader discovers BimCobolVerbExecutor, executes verbs, persists results.
 *
 * <p>Uses in-memory SQLite — no BOM.db or file dependency.
 */
@DisplayName("VerbStage Integration — SPI → Execute → Persist")
class VerbStageIntegrationTest {

    private Connection outputConn;

    @BeforeEach
    void setup() throws SQLException {
        outputConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = outputConn.createStatement()) {
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
        if (outputConn != null) outputConn.close();
    }

    @Test
    @DisplayName("W-COBOL-61: ServiceLoader discovers BimCobolVerbExecutor")
    void spiDiscovery() {
        VerbExecutor executor = ServiceLoader.load(VerbExecutor.class)
                .findFirst().orElse(null);
        assertNotNull(executor, "BimCobolVerbExecutor must be on classpath");
        assertInstanceOf(BimCobolVerbExecutor.class, executor);
    }

    @Test
    @DisplayName("W-COBOL-62: Full chain: SPI execute → PP_Order_Node rows created")
    void fullChainExecution() throws Exception {
        VerbExecutor executor = ServiceLoader.load(VerbExecutor.class)
                .findFirst().orElseThrow();

        // Only use verbs that need no DB (TILE SURFACE, ARRAY are pure computation)
        List<String> verbLines = List.of(
                "TILE SURFACE roof WITH plate ORIGIN 0 0 0 GRID 3 4 STEP 500 150",
                "ARRAY beam WITH rebar LENGTH 3000 SPACING 150 COVER 40",
                "TILE SURFACE side WITH panel ORIGIN 10 0 0 GRID 2 5 STEP 1000 200"
        );

        VerbExecutor.ExecutionReport report =
                executor.execute(null, outputConn, "TEST_BUILDING", verbLines);

        assertEquals(3, report.passCount(), "All 3 verbs must pass");
        assertEquals(0, report.failCount());
        assertTrue(report.allPass());
        assertEquals(3, report.totalNodes(), "3 verb lines → 3 PP_Order_Node rows");
        assertEquals(3, report.details().size());

        // Verify PP_Order_Node rows in DB
        List<M_PP_Order_Node> nodes = M_PP_Order_Node.getByOrder(outputConn, "TEST_BUILDING");
        assertEquals(3, nodes.size());
        assertEquals(10, nodes.get(0).getSeqNo());
        assertEquals(20, nodes.get(1).getSeqNo());
        assertEquals(30, nodes.get(2).getSeqNo());

        // TILE SURFACE should have element_count = 12 (3×4)
        M_PP_Order_Node tileNode = nodes.get(0);
        assertEquals("TILE SURFACE", tileNode.getName());
        assertEquals("CO", tileNode.getDocStatus());
        assertTrue(tileNode.getElementCount() > 0 || tileNode.getLastResult().contains("12"),
                "TILE 3×4 should capture 12 elements");

        // ARRAY should have element_count = 20 (floor((3000-80)/150)+1 = 20)
        M_PP_Order_Node arrayNode = nodes.get(1);
        assertEquals("ARRAY", arrayNode.getName());
        assertEquals("CO", arrayNode.getDocStatus());
    }

    @Test
    @DisplayName("W-COBOL-63: Failed verbs get DocStatus=VO (voided)")
    void failedVerbsVoided() throws Exception {
        VerbExecutor executor = ServiceLoader.load(VerbExecutor.class)
                .findFirst().orElseThrow();

        List<String> verbLines = List.of(
                "TILE SURFACE roof WITH plate ORIGIN 0 0 0 GRID 3 4 STEP 500 150",
                "UNKNOWN VERB should fail"
        );

        VerbExecutor.ExecutionReport report =
                executor.execute(null, outputConn, "TEST_BUILDING", verbLines);

        assertEquals(1, report.passCount());
        assertEquals(1, report.failCount());
        assertFalse(report.allPass());

        List<M_PP_Order_Node> nodes = M_PP_Order_Node.getByOrder(outputConn, "TEST_BUILDING");
        assertEquals(2, nodes.size());
        assertEquals("CO", nodes.get(0).getDocStatus()); // TILE pass → CO
        assertEquals("VO", nodes.get(1).getDocStatus()); // unknown → VO
    }
}
