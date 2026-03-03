package com.bim.ormsandbox;

import com.bim.ormsandbox.po.M_PP_Order_Node;
import com.bim.ormsandbox.po.M_PP_Order_NodeProduct;
import com.bim.ormsandbox.po.X_PP_Order_Node;
import com.bim.ormsandbox.po.X_PP_Order_NodeProduct;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for PP_Order_Node + PP_Order_NodeProduct PO classes.
 *
 * <p>Uses in-memory SQLite — no BOM.db dependency.
 */
@DisplayName("PP_Order_Node — Production Operations PO")
class PP_Order_NodeTest {

    private Connection conn;

    @BeforeEach
    void setup() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE c_order (building_id TEXT PRIMARY KEY)
            """);
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
    @DisplayName("W-PP-1: Insert and read PP_Order_Node via PO")
    void insertAndReadNode() throws SQLException {
        M_PP_Order_Node node = new M_PP_Order_Node(conn);
        node.setC_Order_ID("TEST_BUILDING");
        node.setSeqNo(10);
        node.setName("TILE SURFACE");
        node.setDescription("ROOF_WEST WITH PLATE ORIGIN 92.49 -42.16 19.0 GRID 15 294 STEP 495 150");
        node.setDocStatus("DR");
        node.setElementCount(4410);
        assertTrue(node.save(), "save must succeed");

        int id = node.getPP_Order_Node_ID();
        assertTrue(id > 0, "auto-increment PK must be assigned");

        M_PP_Order_Node loaded = M_PP_Order_Node.get(conn, id);
        assertNotNull(loaded);
        assertEquals("TEST_BUILDING", loaded.getC_Order_ID());
        assertEquals(10, loaded.getSeqNo());
        assertEquals("TILE SURFACE", loaded.getName());
        assertEquals("DR", loaded.getDocStatus());
        assertEquals(4410, loaded.getElementCount());
    }

    @Test
    @DisplayName("W-PP-2: getByOrder returns nodes sorted by SeqNo")
    void getByOrderSorted() throws SQLException {
        // Insert in reverse order
        insertNode("TEST_BUILDING", 30, "ROUTE SPRINKLERS", "Departure Hall SPACING 3000");
        insertNode("TEST_BUILDING", 10, "TILE SURFACE", "ROOF_WEST WITH PLATE ORIGIN 0 0 0 GRID 3 4 STEP 500 150");
        insertNode("TEST_BUILDING", 20, "ARRAY", "SLAB_001 WITH REBAR LENGTH 6000 SPACING 150 COVER 40");

        List<M_PP_Order_Node> nodes = M_PP_Order_Node.getByOrder(conn, "TEST_BUILDING");
        assertEquals(3, nodes.size());
        assertEquals(10, nodes.get(0).getSeqNo());
        assertEquals(20, nodes.get(1).getSeqNo());
        assertEquals(30, nodes.get(2).getSeqNo());
        assertEquals("TILE SURFACE", nodes.get(0).getName());
        assertEquals("ARRAY", nodes.get(1).getName());
        assertEquals("ROUTE SPRINKLERS", nodes.get(2).getName());
    }

    @Test
    @DisplayName("W-PP-3: Insert and read PP_Order_NodeProduct parameters")
    void insertAndReadParams() throws SQLException {
        int nodeId = insertNode("TEST_BUILDING", 10, "TILE SURFACE", "ROOF WITH PLATE ORIGIN 0 0 0 GRID 3 4 STEP 500 150");

        insertParam(nodeId, "SURFACE_NAME", "ROOF_DECK_Z19_WEST", "TEXT");
        insertParam(nodeId, "ORIGIN_X", "92.49", "REAL");
        insertParam(nodeId, "GRID_NX", "15", "INTEGER");

        List<M_PP_Order_NodeProduct> params = M_PP_Order_NodeProduct.getByNode(conn, nodeId);
        assertEquals(3, params.size());
        // Sorted by Name
        assertEquals("GRID_NX", params.get(0).getName());
        assertEquals("15", params.get(0).getValue());
        assertEquals("INTEGER", params.get(0).getValueType());
        assertEquals(15, params.get(0).getValueAsInt());

        assertEquals("ORIGIN_X", params.get(1).getName());
        assertEquals(92.49, params.get(1).getValueAsDouble(), 0.001);

        assertEquals("SURFACE_NAME", params.get(2).getName());
        assertEquals("ROOF_DECK_Z19_WEST", params.get(2).getValue());
    }

    @Test
    @DisplayName("W-PP-4: DocStatus CHECK constraint enforced")
    void docStatusConstraint() throws SQLException {
        M_PP_Order_Node node = new M_PP_Order_Node(conn);
        node.setC_Order_ID("TEST_BUILDING");
        node.setSeqNo(10);
        node.setName("CHECK BOM");
        node.setDescription("test");
        node.setDocStatus("INVALID");
        assertFalse(node.save(), "save with invalid DocStatus should fail");
    }

    @Test
    @DisplayName("W-PP-5: UNIQUE(PP_Order_Node_ID, Name) enforced on NodeProduct")
    void uniqueParamName() throws SQLException {
        int nodeId = insertNode("TEST_BUILDING", 10, "ARRAY", "test");
        insertParam(nodeId, "SPACING_MM", "150", "REAL");

        // Duplicate name → should fail
        M_PP_Order_NodeProduct dup = new M_PP_Order_NodeProduct(conn);
        dup.setPP_Order_Node_ID(nodeId);
        dup.setName("SPACING_MM");
        dup.setValue("200");
        assertFalse(dup.save(), "duplicate param name should fail");
    }

    // ── Helpers ──

    private int insertNode(String orderId, int seqNo, String name, String desc) throws SQLException {
        M_PP_Order_Node node = new M_PP_Order_Node(conn);
        node.setC_Order_ID(orderId);
        node.setSeqNo(seqNo);
        node.setName(name);
        node.setDescription(desc);
        node.save();
        return node.getPP_Order_Node_ID();
    }

    private void insertParam(int nodeId, String name, String value, String type) throws SQLException {
        M_PP_Order_NodeProduct p = new M_PP_Order_NodeProduct(conn);
        p.setPP_Order_Node_ID(nodeId);
        p.setName(name);
        p.setValue(value);
        p.setValueType(type);
        assertTrue(p.save(), "param insert must succeed: " + name);
    }
}
