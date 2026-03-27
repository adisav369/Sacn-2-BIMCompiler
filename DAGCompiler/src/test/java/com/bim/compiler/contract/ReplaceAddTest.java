package com.bim.compiler.contract;

import com.bim.compiler.bom.BomDropper;
import com.bim.compiler.bom.BomDropper.ExceptionLine;
import com.bim.compiler.bom.BomDropper.MutationType;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session p74: Replace + Add mutations.
 *
 * <p>W-REPLACE-1: Replace at locator_ref swaps product subtree during explosion.
 * <p>W-REPLACE-2: Replace rejected when category mismatch (same shelf constraint).
 * <p>W-ADD-1: Add creates discipline C_OrderLine as sibling of root BUILDING line.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1, W-ADD-1
 *
 * <p>BOM tree for tests:
 * <pre>
 * SH_BUILDING (BUILDING, category=RE, doc_sub_type=SH)
 *   └─ FLOOR_GF (FLOOR, category=GF)
 *        └─ ROOM_LI (ROOM, category=LI)  ← 2 leaves (SOFA_001, TABLE_001)
 *
 * ROOM_KI (ROOM, category=LI)            ← replacement: 3 leaves (SINK_001, STOVE_001, FRIDGE_001)
 * ROOM_WRONG (ROOM, category=XX)         ← wrong category: Replace must be rejected
 * </pre>
 */
// Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1, W-ADD-1
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReplaceAddTest {

    private static Connection conn;
    private static final BuildingEntry ENTRY = new BuildingEntry(
            "RE_SH", "TestHouse", "Test House",
            "RE", "SH",
            null, null, null,
            true, 10, 0,
            "GENERATIVE", "test", 0,
            10000, 8000, 6000
    );

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        initSchema(conn);
        seedBomData(conn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // ── W-REPLACE-1: Replace on assembly swaps subtree ──────────────────────

    @Test
    @Order(1)
    @DisplayName("W-REPLACE-1: Replace at locator_ref swaps assembly subtree, leaf count changes")
    // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
    void replaceSwapsSubtree() throws Exception {
        // Baseline: normal drop → 2 leaves (SOFA_001, TABLE_001)
        int baselineLeaves = BomDropper.drop(conn, ENTRY, "BASELINE");
        assertEquals(2, baselineLeaves, "Baseline must have 2 leaves");

        // Find the ROOM_LI locator_ref
        String roomLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                     + "AND host_type = 'ROOM' LIMIT 1")) {
            assertTrue(rs.next(), "Must have a ROOM assembly");
            roomLocator = rs.getString(1);
        }

        // Replace ROOM_LI → ROOM_KI (same category=LI, but 3 leaves instead of 2)
        Map<String, ExceptionLine> exceptions = new HashMap<>();
        exceptions.put(roomLocator, ExceptionLine.replace("ROOM_KI"));
        int replacedLeaves = BomDropper.drop(conn, ENTRY, "REPLACE_TEST", exceptions);

        // Must now have 3 leaves (SINK_001, STOVE_001, FRIDGE_001)
        assertEquals(3, replacedLeaves,
                String.format("W-REPLACE-1: Replace must change leaf count: baseline=2, after=%d, target=%s",
                        replacedLeaves, roomLocator));

        // Verify the new leaves are from ROOM_KI, not ROOM_LI
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT family_ref FROM C_OrderLine WHERE C_Order_ID = 'REPLACE_TEST' "
                     + "AND host_type = 'LEAF' ORDER BY family_ref")) {
            assertTrue(rs.next());
            assertEquals("FRIDGE_001", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("SINK_001", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("STOVE_001", rs.getString(1));
            assertFalse(rs.next(), "Must have exactly 3 leaves");
        }

        System.out.printf("[W-REPLACE-1] baseline=2, after_replace=3, target=%s — PASS%n", roomLocator);
    }

    // ── W-REPLACE-2: Replace rejected on category mismatch ──────────────────

    @Test
    @Order(2)
    @DisplayName("W-REPLACE-2: Replace rejected when category mismatch (same shelf constraint)")
    // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
    void replaceRejectedOnCategoryMismatch() throws Exception {
        // Find the ROOM_LI locator_ref
        String roomLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                     + "AND host_type = 'ROOM' LIMIT 1")) {
            assertTrue(rs.next());
            roomLocator = rs.getString(1);
        }

        // Try to Replace ROOM_LI → ROOM_WRONG (category=XX ≠ LI → must be rejected)
        Map<String, ExceptionLine> exceptions = new HashMap<>();
        exceptions.put(roomLocator, ExceptionLine.replace("ROOM_WRONG"));
        int leaves = BomDropper.drop(conn, ENTRY, "REJECT_TEST", exceptions);

        // Must still have 2 leaves (original ROOM_LI, swap rejected)
        assertEquals(2, leaves,
                "W-REPLACE-2: Category mismatch must reject Replace — leaves must be unchanged");

        // Verify the leaves are still from ROOM_LI
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT family_ref FROM C_OrderLine WHERE C_Order_ID = 'REJECT_TEST' "
                     + "AND host_type = 'LEAF' ORDER BY family_ref")) {
            assertTrue(rs.next());
            assertEquals("SOFA_001", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("TABLE_001", rs.getString(1));
        }

        System.out.printf("[W-REPLACE-2] Category mismatch → original preserved — PASS%n");
    }

    // ── W-ADD-1: Add creates discipline C_OrderLine ─────────────────────────

    @Test
    @Order(3)
    @DisplayName("W-ADD-1: Add mutation creates discipline C_OrderLine as sibling of root")
    // Implementing ProjectOrderBlueprint.md §1.1 + DISC_VALIDATION_DB_SRS.md §10.4.6 — Witness: W-ADD-1
    void addCreatesDisciplineLine() throws Exception {
        // Drop with Add exception for FP discipline
        Map<String, ExceptionLine> exceptions = new HashMap<>();
        exceptions.put("FP", ExceptionLine.add("FP_SYSTEM"));
        int leaves = BomDropper.drop(conn, ENTRY, "ADD_TEST", exceptions);

        // Structural leaves unchanged (Add doesn't affect the structural explosion)
        assertEquals(2, leaves, "Add mutation must not change structural leaf count");

        // The FP_SYSTEM line must exist as a sibling of the root (Parent_OrderLine_ID IS NULL)
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT family_ref, host_type, locator_ref, M_Product_ID "
                     + "FROM C_OrderLine WHERE C_Order_ID = 'ADD_TEST' "
                     + "AND host_type = 'DISCIPLINE'")) {
            assertTrue(rs.next(), "Add line must exist");
            assertEquals("FP_SYSTEM", rs.getString("family_ref"));
            assertEquals("DISCIPLINE", rs.getString("host_type"));
            assertEquals("FP", rs.getString("locator_ref"));
            assertEquals("FP_SYSTEM", rs.getString("M_Product_ID"));
            assertFalse(rs.next(), "Only one Add line expected");
        }

        // Count top-level lines (Parent_OrderLine_ID IS NULL)
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = 'ADD_TEST' "
                     + "AND Parent_OrderLine_ID IS NULL")) {
            rs.next();
            assertEquals(2, rs.getInt(1),
                    "Must have 2 top-level lines: BUILDING root + FP_SYSTEM discipline");
        }

        System.out.printf("[W-ADD-1] FP_SYSTEM added as DISCIPLINE sibling of root — PASS%n");
    }

    // ── W-ADD-2: Multiple Add mutations ─────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-ADD-2: Multiple Add mutations create multiple discipline lines")
    // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-ADD-1
    void multipleAddMutations() throws Exception {
        Map<String, ExceptionLine> exceptions = new HashMap<>();
        exceptions.put("FP", ExceptionLine.add("FP_SYSTEM"));
        exceptions.put("ELEC", ExceptionLine.add("ELEC_SYSTEM"));
        exceptions.put("ACMV", ExceptionLine.add("ACMV_SYSTEM"));
        int leaves = BomDropper.drop(conn, ENTRY, "MULTI_ADD", exceptions);

        assertEquals(2, leaves, "Structural leaves unchanged with 3 Add mutations");

        // Count DISCIPLINE lines
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = 'MULTI_ADD' "
                     + "AND host_type = 'DISCIPLINE'")) {
            rs.next();
            assertEquals(3, rs.getInt(1), "Must have 3 discipline lines");
        }

        System.out.printf("[W-ADD-2] 3 discipline lines added — PASS%n");
    }

    // ── W-ALGEBRA-1: Replace + Add combined ─────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("W-ALGEBRA-1: Replace + Add combined — all four mutations on one order")
    // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1, W-ADD-1
    void allFourMutationsCombined() throws Exception {
        // First find the room and a leaf locator from a baseline drop
        BomDropper.drop(conn, ENTRY, "BASE_FOR_COMBO");
        String roomLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASE_FOR_COMBO' "
                     + "AND host_type = 'ROOM' LIMIT 1")) {
            assertTrue(rs.next());
            roomLocator = rs.getString(1);
        }

        // All four mutations:
        // Replace: swap ROOM_LI → ROOM_KI (2 leaves → 3 leaves)
        // Add: FP_SYSTEM discipline
        // (Remove and Compress already tested in RemoveCompressTest)
        Map<String, ExceptionLine> exceptions = new HashMap<>();
        exceptions.put(roomLocator, ExceptionLine.replace("ROOM_KI"));
        exceptions.put("FP", ExceptionLine.add("FP_SYSTEM"));
        int leaves = BomDropper.drop(conn, ENTRY, "COMBO_TEST", exceptions);

        // 3 leaves from ROOM_KI (Replace), Add doesn't affect leaf count
        assertEquals(3, leaves, "Replace → 3 leaves, Add → 0 structural change");

        // Verify both: replacement leaves + discipline line
        int disciplineCount;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = 'COMBO_TEST' "
                     + "AND host_type = 'DISCIPLINE'")) {
            rs.next();
            disciplineCount = rs.getInt(1);
        }
        assertEquals(1, disciplineCount, "Must have 1 discipline line");

        System.out.printf("[W-ALGEBRA-1] Replace+Add combo: 3 leaves + 1 discipline — PASS%n");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void initSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE m_bom (
                    bom_id TEXT PRIMARY KEY,
                    Value TEXT,
                    bom_name TEXT NOT NULL,
                    description TEXT,
                    target_ifc_class TEXT DEFAULT 'IfcElementAssembly',
                    group_by TEXT NOT NULL,
                    is_active INTEGER DEFAULT 1,
                    bom_level TEXT DEFAULT 'SET',
                    bom_type TEXT NOT NULL DEFAULT 'SET',
                    m_product_category_id TEXT DEFAULT NULL,
                    doc_sub_type TEXT DEFAULT NULL,
                    seq_no INTEGER DEFAULT 10,
                    origin_x REAL DEFAULT 0.0,
                    origin_y REAL DEFAULT 0.0,
                    origin_z REAL DEFAULT 0.0,
                    entity_type TEXT DEFAULT 'D',
                    aabb_width_mm INTEGER DEFAULT 0,
                    aabb_depth_mm INTEGER DEFAULT 0,
                    aabb_height_mm INTEGER DEFAULT 0
                )
            """);

            stmt.execute("""
                CREATE TABLE m_bom_line (
                    bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id TEXT NOT NULL,
                    child_product_id TEXT,
                    child_element_type TEXT,
                    child_name_pattern TEXT,
                    role TEXT NOT NULL DEFAULT 'BUY',
                    qty_type TEXT DEFAULT 'VARIABLE',
                    qty INTEGER NOT NULL DEFAULT 1,
                    sequence INTEGER DEFAULT 100,
                    component_type TEXT DEFAULT 'BUY',
                    is_active INTEGER DEFAULT 1,
                    dx REAL DEFAULT 0,
                    dy REAL DEFAULT 0,
                    dz REAL DEFAULT 0,
                    allocated_width_mm REAL DEFAULT 0,
                    allocated_depth_mm REAL DEFAULT 0,
                    allocated_height_mm REAL DEFAULT 0,
                    entity_type TEXT DEFAULT 'D'
                )
            """);

            // Tier 2: C_Order_ID is INTEGER PK, Value holds text key
            stmt.execute("""
                CREATE TABLE C_Order (
                    C_Order_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    Value TEXT,
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

            stmt.execute("""
                CREATE TABLE C_OrderLine (
                    C_OrderLine_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
                    Parent_OrderLine_ID INTEGER,
                    Line INTEGER NOT NULL DEFAULT 10,
                    family_ref TEXT NOT NULL,
                    host_type TEXT NOT NULL,
                    m_product_category_id TEXT,
                    bom_child_id INTEGER,
                    dx REAL NOT NULL DEFAULT 0,
                    dy REAL NOT NULL DEFAULT 0,
                    dz REAL NOT NULL DEFAULT 0,
                    aabb_width_mm REAL,
                    aabb_depth_mm REAL,
                    aabb_height_mm REAL,
                    M_Product_ID TEXT,
                    Discipline TEXT DEFAULT 'ARC',
                    AD_Org_ID INTEGER DEFAULT 0,
                    Qty INTEGER NOT NULL DEFAULT 1,
                    locator_ref TEXT,
                    is_reference_class INTEGER NOT NULL DEFAULT 0,
                    IsActive INTEGER NOT NULL DEFAULT 1,
                    created TEXT NOT NULL DEFAULT (datetime('now')),
                    updated TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);
        }
    }

    /**
     * Seed data: BUILDING → FLOOR → ROOM → 2 LEAFs + replacement room with 3 LEAFs.
     *
     * <pre>
     * SH_BUILDING (BUILDING, category=RE, doc_sub_type=SH)
     *   └─ FLOOR_GF (FLOOR, category=GF)
     *        └─ ROOM_LI (ROOM, category=LI) — 2 leaves
     *             ├─ SOFA_001 (LEAF, qty=1)
     *             └─ TABLE_001 (LEAF, qty=1)
     *
     * ROOM_KI (ROOM, category=LI) — replacement with 3 leaves (same category)
     *   ├─ SINK_001 (LEAF, qty=1)
     *   ├─ STOVE_001 (LEAF, qty=1)
     *   └─ FRIDGE_001 (LEAF, qty=1)
     *
     * ROOM_WRONG (ROOM, category=XX) — wrong category for rejection test
     *   └─ DESK_001 (LEAF, qty=1)
     * </pre>
     */
    private static void seedBomData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // BUILDING BOM
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    doc_sub_type, is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('SH_BUILDING', 'SH_BUILDING', 'SH Building', 'BUILDING', 'BUILDING', 'RE',
                    'SH', 1, 10000, 8000, 6000)
            """);

            // FLOOR BOM
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('FLOOR_GF', 'FLOOR_GF', 'Ground Floor', 'BUILDING', 'FLOOR', 'GF',
                    1, 10000, 8000, 3000)
            """);

            // ROOM_LI BOM (Living room — original)
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('ROOM_LI', 'ROOM_LI', 'Living Room', 'ROOM', 'SET', 'LI',
                    1, 5000, 4000, 3000)
            """);

            // ROOM_KI BOM (Kitchen — replacement, same category=LI)
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('ROOM_KI', 'ROOM_KI', 'Kitchen', 'ROOM', 'SET', 'LI',
                    1, 4000, 3500, 3000)
            """);

            // ROOM_WRONG BOM (wrong category for rejection test)
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('ROOM_WRONG', 'ROOM_WRONG', 'Wrong Category Room', 'ROOM', 'SET', 'XX',
                    1, 3000, 3000, 3000)
            """);

            // BUILDING → FLOOR
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('SH_BUILDING', 'FLOOR_GF', 'MAKE', 1, 10, 'MAKE', 1,
                    0, 0, 0, 10000, 8000, 3000)
            """);

            // FLOOR → ROOM_LI
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('FLOOR_GF', 'ROOM_LI', 'MAKE', 1, 10, 'MAKE', 1,
                    0, 0, 0, 5000, 4000, 3000)
            """);

            // ROOM_LI → 2 leaf products
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('ROOM_LI', 'SOFA_001', 'BUY', 1, 10, 'BUY', 1,
                    0, 0, 0, 2000, 1000, 900)
            """);
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('ROOM_LI', 'TABLE_001', 'BUY', 1, 20, 'BUY', 1,
                    2000, 0, 0, 1200, 800, 750)
            """);

            // ROOM_KI → 3 leaf products (replacement room)
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('ROOM_KI', 'SINK_001', 'BUY', 1, 10, 'BUY', 1,
                    0, 0, 0, 1000, 600, 900)
            """);
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('ROOM_KI', 'STOVE_001', 'BUY', 1, 20, 'BUY', 1,
                    1000, 0, 0, 600, 600, 900)
            """);
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('ROOM_KI', 'FRIDGE_001', 'BUY', 1, 30, 'BUY', 1,
                    1600, 0, 0, 700, 700, 1800)
            """);

            // ROOM_WRONG → 1 leaf
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('ROOM_WRONG', 'DESK_001', 'BUY', 1, 10, 'BUY', 1,
                    0, 0, 0, 1200, 600, 750)
            """);
        }
    }
}
