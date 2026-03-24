package com.bim.designer;

import com.bim.backoffice.dao.ChangelogDAO;
import com.bim.designer.dao.CalloutEngine;
import com.bim.designer.dao.DiffVerbService;
import com.bim.designer.dao.DiffVerbService.DiffParams;
import com.bim.designer.dao.DiffVerbService.DiffResult;
import com.bim.ormsandbox.po.M_PP_Order_Node;
import com.bim.ormsandbox.po.M_PP_Order_NodeProduct;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffVerb + Callout integration tests.
 *
 * <p>Proves the §9 reactive spatial editing path:
 * record DIFF → PP_Order_Node + PP_Order_NodeProduct → bim_changelog MOVE →
 * CalloutEngine fires AD_Rule chain → room AABB recalc triggered.
 *
 * <p>All tests use in-memory SQLite — no filesystem dependency.
 *
 * // Implementing ProjectOrderBlueprint.md §9 — Witness: W-DIFF-1
 */
@DisplayName("DiffVerb — §9 Reactive Spatial Editing")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiffVerbTest {

    private static Connection conn;
    private static DiffVerbService diffService;

    private static final String BUILDING_ID = "TestHouse";

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        initTestSchema(conn);
        diffService = new DiffVerbService(conn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // ── W-DIFF-1: Record a DIFF verb and verify PP_Order_Node ─────────────

    /**
     * W-DIFF-1: Move fireplace 300mm left → PP_Order_Node with Name='DIFF',
     * PP_Order_NodeProduct rows with delta_dx=-300, and bim_changelog MOVE entry.
     */
    @Test
    @Order(1)
    void w_diff_1_recordDiffCreatesNodeAndParams() throws SQLException {
        DiffParams params = new DiffParams(
                BUILDING_ID, "L1.Living.FP_Mantel",
                -300, 0, 0,       // delta: 300mm left
                2400, 1500, 0,    // old position
                "viewport_drag"
        );

        DiffResult result = diffService.recordDiff(params);

        // Verify PP_Order_Node created
        assertTrue(result.nodeId() > 0, "PP_Order_Node_ID should be positive");
        assertEquals("L1.Living.FP_Mantel", result.locatorRef());

        // Load and verify PP_Order_Node
        M_PP_Order_Node node = M_PP_Order_Node.get(conn, result.nodeId());
        assertNotNull(node, "PP_Order_Node should exist");
        assertEquals(BUILDING_ID, node.getC_Order_ID());
        assertEquals("DIFF", node.getName());
        assertEquals("CO", node.getDocStatus());
        assertTrue(node.getDescription().contains("L1.Living.FP_Mantel"),
                "Description should contain locator_ref");
        assertTrue(node.getDescription().contains("-300"),
                "Description should contain delta");
    }

    /**
     * W-DIFF-2: PP_Order_NodeProduct rows carry structured delta parameters.
     */
    @Test
    @Order(2)
    void w_diff_2_nodeProductParamsRecorded() throws SQLException {
        // Node from W-DIFF-1
        List<M_PP_Order_Node> nodes = M_PP_Order_Node.getByOrder(conn, BUILDING_ID);
        assertFalse(nodes.isEmpty(), "Should have at least one node");

        int nodeId = nodes.get(0).getPP_Order_Node_ID();
        List<M_PP_Order_NodeProduct> params = M_PP_Order_NodeProduct.getByNode(conn, nodeId);

        // Should have 8 params: locator_ref, delta_dx/dy/dz, old_x/y/z, source
        assertEquals(8, params.size(), "Should have 8 structured parameters");

        // Verify key params
        assertParamValue(params, "delta_dx", "-300.0");
        assertParamValue(params, "delta_dy", "0.0");
        assertParamValue(params, "delta_dz", "0.0");
        assertParamValue(params, "old_x", "2400.0");
        assertParamValue(params, "locator_ref", "L1.Living.FP_Mantel");
        assertParamValue(params, "source", "viewport_drag");
    }

    /**
     * W-DIFF-3: bim_changelog records MOVE action with old/new position.
     */
    @Test
    @Order(3)
    void w_diff_3_changelogMoveRecorded() throws SQLException {
        ChangelogDAO changelog = new ChangelogDAO(conn);
        List<ChangelogDAO.ChangeEntry> entries = changelog.getChangelog(BUILDING_ID, 10);

        assertFalse(entries.isEmpty(), "Changelog should have entries");

        ChangelogDAO.ChangeEntry move = entries.stream()
                .filter(e -> "MOVE".equals(e.action()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No MOVE entry in changelog"));

        assertEquals("C_OrderLine", move.entityType());
        assertEquals("L1.Living.FP_Mantel", move.entityId());
        assertEquals("position", move.fieldName());
        assertEquals("2400,1500,0", move.oldValue());
        assertEquals("2100,1500,0", move.newValue());  // 2400 - 300 = 2100
    }

    /**
     * W-DIFF-4: CalloutEngine fires ROOM_AABB_RECALC for dx change.
     */
    @Test
    @Order(4)
    void w_diff_4_calloutFiresAabbRecalc() throws SQLException {
        // Re-record a diff to get fresh callout results
        DiffParams params = new DiffParams(
                BUILDING_ID, "L1.Kitchen.Wall_N",
                500, 0, 0,       // move wall 500mm right
                3600, 4200, 0,
                "api_call"
        );

        DiffResult result = diffService.recordDiff(params);

        // Callout should have fired for dx
        assertTrue(result.calloutsFired() > 0,
                "At least one callout should fire for dx change");

        // Verify ROLLUP_AABB callout result
        boolean aabbFired = result.calloutResults().stream()
                .anyMatch(r -> r.fired() && "ROLLUP_AABB".equals(r.expression()));
        assertTrue(aabbFired, "ROLLUP_AABB callout should fire on dx change");
    }

    /**
     * W-DIFF-5: No callout fires when delta is zero on all axes.
     */
    @Test
    @Order(5)
    void w_diff_5_noCalloutOnZeroDelta() throws SQLException {
        DiffParams params = new DiffParams(
                BUILDING_ID, "L1.Living.Table",
                0, 0, 0,         // no movement
                1000, 1000, 0,
                "api_call"
        );

        DiffResult result = diffService.recordDiff(params);

        assertEquals(0, result.calloutsFired(),
                "No callouts should fire when delta is zero");
        assertTrue(result.calloutResults().isEmpty(),
                "Callout results should be empty for zero delta");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void assertParamValue(List<M_PP_Order_NodeProduct> params,
                                  String name, String expectedValue) {
        M_PP_Order_NodeProduct param = params.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing param: " + name));
        assertEquals(expectedValue, param.getValue(),
                "Param '" + name + "' value mismatch");
    }

    /**
     * Create minimal schema for DiffVerb testing: C_Order, C_OrderLine,
     * PP_Order_Node, PP_Order_NodeProduct, bim_changelog, AD_Rule.
     */
    private static void initTestSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // C_Order (minimal)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS C_Order (
                    C_Order_ID  TEXT PRIMARY KEY,
                    C_DocType_ID TEXT NOT NULL,
                    Name TEXT NOT NULL,
                    DocStatus TEXT NOT NULL DEFAULT 'DR',
                    aabb_width_mm REAL NOT NULL DEFAULT 0,
                    aabb_depth_mm REAL NOT NULL DEFAULT 0,
                    aabb_height_mm REAL NOT NULL DEFAULT 0,
                    IsActive INTEGER NOT NULL DEFAULT 1,
                    created TEXT NOT NULL DEFAULT (datetime('now')),
                    updated TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // C_OrderLine (minimal)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS C_OrderLine (
                    C_OrderLine_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
                    Line INTEGER NOT NULL DEFAULT 10,
                    family_ref TEXT NOT NULL,
                    host_type TEXT NOT NULL DEFAULT 'ROOM',
                    dx REAL NOT NULL DEFAULT 0,
                    dy REAL NOT NULL DEFAULT 0,
                    dz REAL NOT NULL DEFAULT 0,
                    locator_ref TEXT,
                    IsActive INTEGER NOT NULL DEFAULT 1,
                    created TEXT NOT NULL DEFAULT (datetime('now')),
                    updated TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // PP_Order_Node
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS PP_Order_Node (
                    PP_Order_Node_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
                    C_OrderLine_ID INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),
                    Name TEXT NOT NULL,
                    SeqNo INTEGER NOT NULL,
                    verb_ref TEXT,
                    co_emptyspaceline_id INTEGER,
                    DocStatus TEXT NOT NULL DEFAULT 'DR'
                        CHECK(DocStatus IN ('DR','IP','CO','VO')),
                    Duration_minutes INTEGER,
                    Description TEXT,
                    IsActive INTEGER NOT NULL DEFAULT 1,
                    created TEXT NOT NULL DEFAULT (datetime('now')),
                    updated TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // PP_Order_NodeProduct
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS PP_Order_NodeProduct (
                    PP_Order_NodeProduct_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    PP_Order_Node_ID INTEGER NOT NULL REFERENCES PP_Order_Node(PP_Order_Node_ID),
                    Name TEXT NOT NULL,
                    Value TEXT NOT NULL,
                    ValueType TEXT DEFAULT 'NUM'
                        CHECK(ValueType IN ('NUM','TEXT','BOOL')),
                    UNIQUE(PP_Order_Node_ID, Name)
                )
                """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ppnode_order ON PP_Order_Node(C_Order_ID)");
        }

        // bim_changelog via ChangelogDAO
        new ChangelogDAO(conn).initSchema();

        // AD_Rule (W007 migration inline)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS AD_Rule (
                    ad_rule_id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    event_type TEXT NOT NULL DEFAULT 'FIELD_CHANGE'
                        CHECK(event_type IN ('FIELD_CHANGE')),
                    source_table TEXT NOT NULL,
                    source_column TEXT NOT NULL,
                    rule_type TEXT NOT NULL
                        CHECK(rule_type IN ('POSITIONAL','DIMENSIONAL','CONSTRAINT','REROUTE')),
                    target_locator TEXT,
                    expression TEXT,
                    depends_on INTEGER REFERENCES AD_Rule(ad_rule_id),
                    seq_no INTEGER NOT NULL DEFAULT 10,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    pack_id TEXT
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ad_rule_source ON AD_Rule(source_table, source_column, is_active)");

            // Seed: Room AABB recalc rules
            stmt.execute("""
                INSERT OR IGNORE INTO AD_Rule (ad_rule_id, name, description,
                    event_type, source_table, source_column, rule_type,
                    target_locator, expression, seq_no)
                VALUES
                    (1, 'ROOM_AABB_RECALC_DX', 'Recalculate room AABB when child dx changes',
                     'FIELD_CHANGE', 'C_OrderLine', 'dx', 'DIMENSIONAL', NULL, 'ROLLUP_AABB', 10),
                    (2, 'ROOM_AABB_RECALC_DY', 'Recalculate room AABB when child dy changes',
                     'FIELD_CHANGE', 'C_OrderLine', 'dy', 'DIMENSIONAL', NULL, 'ROLLUP_AABB', 10),
                    (3, 'ROOM_AABB_RECALC_DZ', 'Recalculate room AABB when child dz changes',
                     'FIELD_CHANGE', 'C_OrderLine', 'dz', 'DIMENSIONAL', NULL, 'ROLLUP_AABB', 10)
                """);
        }

        // Insert test building
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT INTO C_Order (C_Order_ID, C_DocType_ID, Name, DocStatus,
                    aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('TestHouse', 'RE_SH', 'Test House', 'DR', 9000, 7000, 3000)
                """);
        }
    }
}
