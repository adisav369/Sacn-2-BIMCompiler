package com.bim.compiler.contract;

import com.bim.compiler.bom.BomDropper;
import com.bim.compiler.bom.BomDropper.ExceptionLine;
import com.bim.compiler.bom.InheritanceResolver;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session E: Order inheritance — composable variant overlays.
 *
 * <p>W-INHERIT-1: DX_SOLAR_PREMIUM = 3 lines on top of DX_SOLAR on top of DX_BASE.
 * Three-deep chain resolves correctly; deepest order wins at each locator_ref.
 *
 * <p>W-INHERIT-CONFLICT-1: Cycle detection throws IllegalStateException.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
 */
// Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderInheritanceTest {

    private static Connection conn;
    private static final BuildingEntry ENTRY = new BuildingEntry(
            "RE_DX", "TestDuplex", "Test Duplex",
            "RE", "DX",
            null, null, null,
            true, 10, 0,
            "GENERATIVE", "test", 0,
            12000, 10000, 8000,
            null, null
    );

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        initSchema(conn);
        seedBomData(conn);
        seedInheritanceChain(conn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // ── W-INHERIT-CHAIN-1: Chain walking resolves root-first ─────────────────

    @Test
    @Order(1)
    @DisplayName("W-INHERIT-CHAIN-1: resolveChain returns root-first order [DX_BASE, DX_SOLAR, DX_SOLAR_PREMIUM]")
    // Implementing ProjectOrderBlueprint.md §6.1 — Witness: W-INHERIT-1
    void chainWalkReturnsRootFirst() throws Exception {
        List<String> chain = InheritanceResolver.resolveChain(conn, "DX_SOLAR_PREMIUM");
        assertEquals(List.of("DX_BASE", "DX_SOLAR", "DX_SOLAR_PREMIUM"), chain,
                "Chain must be root-first: DX_BASE → DX_SOLAR → DX_SOLAR_PREMIUM");
        System.out.printf("[W-INHERIT-CHAIN-1] chain=%s — PASS%n", chain);
    }

    // ── W-INHERIT-CHAIN-2: Base order has chain of length 1 ──────────────────

    @Test
    @Order(2)
    @DisplayName("W-INHERIT-CHAIN-2: Base order (no parent) returns single-element chain")
    // Implementing ProjectOrderBlueprint.md §6.1 — Witness: W-INHERIT-1
    void baseOrderChainIsSingleton() throws Exception {
        List<String> chain = InheritanceResolver.resolveChain(conn, "DX_BASE");
        assertEquals(List.of("DX_BASE"), chain, "Base order chain must be [DX_BASE]");
        System.out.printf("[W-INHERIT-CHAIN-2] base chain=%s — PASS%n", chain);
    }

    // ── W-INHERIT-1: Three-deep chain compiles with inherited exceptions ─────

    @Test
    @Order(3)
    @DisplayName("W-INHERIT-1: DX_SOLAR_PREMIUM inherits exceptions from DX_SOLAR and DX_BASE")
    // Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
    void threeDeepChainCompilesCorrectly() throws Exception {
        // First: drop the base order normally
        int baseLeaves = BomDropper.drop(conn, ENTRY, "DX_BASE");
        assertTrue(baseLeaves > 0, "Base order must produce leaves");

        // Now resolve exceptions from the 3-deep chain
        Map<String, ExceptionLine> exceptions =
                InheritanceResolver.resolveExceptions(conn, "DX_SOLAR_PREMIUM");

        // Chain has 3 orders, so there must be inherited exceptions
        assertFalse(exceptions.isEmpty(),
                "3-deep chain must produce inherited exceptions");

        System.out.printf("[W-INHERIT-1] Resolved %d exceptions from 3-deep chain%n", exceptions.size());

        // Verify that the resolved map contains exception from DX_SOLAR (depth 1)
        // and DX_SOLAR_PREMIUM (depth 2), with depth 2 winning on conflicts
        // DX_SOLAR added: REMOVE on RE.GF.LI.TABLE_001 (solar panel replaces table slot)
        // DX_SOLAR_PREMIUM added: REMOVE on RE.GF.LI.SOFA_001 (premium upgrade removes sofa)
        assertTrue(exceptions.containsKey("RE.GF.LI.TABLE_001"),
                "Must inherit DX_SOLAR's Remove on TABLE_001");
        assertTrue(exceptions.containsKey("RE.GF.LI.SOFA_001"),
                "Must inherit DX_SOLAR_PREMIUM's Remove on SOFA_001");

        // Verify Remove semantics (qty=0)
        assertEquals(0, exceptions.get("RE.GF.LI.TABLE_001").qty(),
                "TABLE_001 exception must be qty=0 (Remove)");
        assertEquals(0, exceptions.get("RE.GF.LI.SOFA_001").qty(),
                "SOFA_001 exception must be qty=0 (Remove)");

        // Drop with inherited exceptions — element count must drop
        int inheritedLeaves = BomDropper.drop(conn, ENTRY, "DX_INHERIT_TEST", exceptions);
        assertTrue(inheritedLeaves < baseLeaves,
                String.format("Inherited exceptions must reduce leaves: base=%d, inherited=%d",
                        baseLeaves, inheritedLeaves));

        System.out.printf("[W-INHERIT-1] base=%d, inherited=%d, exceptions=%d — PASS%n",
                baseLeaves, inheritedLeaves, exceptions.size());
    }

    // ── W-INHERIT-DEPTH-1: Last descendant wins at same locator_ref ──────────

    @Test
    @Order(4)
    @DisplayName("W-INHERIT-DEPTH-1: Deepest order wins when same locator_ref appears in multiple chain levels")
    // Implementing ProjectOrderBlueprint.md §6.2 — Witness: W-INHERIT-1
    void lastDescendantWins() throws Exception {
        // Seed a conflicting exception: DX_SOLAR (depth 1) and DX_SOLAR_PREMIUM (depth 2)
        // both target the same locator_ref with different actions.
        // We use the DX_SOLAR_CONFLICT and DX_PREMIUM_OVERRIDE orders for this test.
        seedConflictChain(conn);

        List<String> chain = InheritanceResolver.resolveChain(conn, "DX_CONFLICT_CHILD");
        assertEquals(3, chain.size(), "Conflict chain must be 3 deep");

        Map<String, ExceptionLine> exceptions =
                InheritanceResolver.collectExceptions(conn, chain);

        // DX_CONFLICT_PARENT sets RE.GF.LI.SOFA_001 to qty=0 (Remove)
        // DX_CONFLICT_CHILD sets RE.GF.LI.SOFA_001 to qty=3, is_reference_class=true (Compress)
        // Depth 2 (child) must win
        ExceptionLine resolved = exceptions.get("RE.GF.LI.SOFA_001");
        assertNotNull(resolved, "Conflict locator_ref must be in resolved set");
        assertEquals(3, resolved.qty(), "Deepest order (qty=3 Compress) must win over shallower (qty=0 Remove)");
        assertTrue(resolved.isReferenceClass(), "Deepest order's is_reference_class must prevail");

        System.out.printf("[W-INHERIT-DEPTH-1] Conflict at RE.GF.LI.SOFA_001: depth-2 wins (qty=%d, refClass=%s) — PASS%n",
                resolved.qty(), resolved.isReferenceClass());
    }

    // ── W-INHERIT-CONFLICT-1: Cycle detection ────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("W-INHERIT-CONFLICT-1: Cycle in Ref_Order_ID chain throws IllegalStateException")
    // Implementing ProjectOrderBlueprint.md §6.3 — Witness: W-INHERIT-CONFLICT-1
    void cycleDetectionThrows() throws Exception {
        // Create a cycle: A → B → A
        seedCyclicOrders(conn);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> InheritanceResolver.resolveChain(conn, "CYCLE_B"),
                "Cycle must throw IllegalStateException");

        assertTrue(ex.getMessage().contains("Cycle"),
                "Exception message must mention 'Cycle': " + ex.getMessage());

        System.out.printf("[W-INHERIT-CONFLICT-1] Cycle detected: %s — PASS%n", ex.getMessage());
    }

    // ── W-INHERIT-COMPAT-1: dropWithInheritance convenience method ───────────

    @Test
    @Order(6)
    @DisplayName("W-INHERIT-COMPAT-1: dropWithInheritance produces same result as manual chain resolve + drop")
    // Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
    void dropWithInheritanceMatchesManual() throws Exception {
        // Manual: resolve chain, then drop with exceptions
        Map<String, ExceptionLine> manualExceptions =
                InheritanceResolver.resolveExceptions(conn, "DX_SOLAR_PREMIUM");
        int manualLeaves = BomDropper.drop(conn, ENTRY, "MANUAL_TEST", manualExceptions);

        // Convenience: dropWithInheritance
        int autoLeaves = BomDropper.dropWithInheritance(conn, ENTRY, "DX_SOLAR_PREMIUM");

        assertEquals(manualLeaves, autoLeaves,
                "dropWithInheritance must produce same leaf count as manual resolve+drop");

        System.out.printf("[W-INHERIT-COMPAT-1] manual=%d, auto=%d — PASS%n", manualLeaves, autoLeaves);
    }

    // ── Schema + seed data ───────────────────────────────────────────────────

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
                    M_BOM_Line_ID INTEGER PRIMARY KEY AUTOINCREMENT,
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
                    Ref_Order_ID TEXT,
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
                    M_BOM_Line_ID INTEGER,
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
     * Seed data: same BUILDING → FLOOR → ROOM → 2 LEAFs as RemoveCompressTest.
     *
     * <pre>
     * DX_BUILDING (BUILDING, category=RE)
     *   └─ FLOOR_GF (FLOOR, category=GF)
     *        └─ ROOM_LI (ROOM/SET, category=LI)
     *             ├─ SOFA_001 (LEAF, qty=1)
     *             └─ TABLE_001 (LEAF, qty=1)
     * </pre>
     */
    private static void seedBomData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    doc_sub_type, is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('DX_BUILDING', 'DX_BUILDING', 'DX Building', 'BUILDING', 'BUILDING', 'RE',
                    'DX', 1, 12000, 10000, 8000)
            """);

            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('FLOOR_GF', 'FLOOR_GF', 'Ground Floor', 'BUILDING', 'FLOOR', 'GF',
                    1, 12000, 10000, 3000)
            """);

            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('ROOM_LI', 'ROOM_LI', 'Living Room', 'ROOM', 'SET', 'LI',
                    1, 5000, 4000, 3000)
            """);

            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('DX_BUILDING', 'FLOOR_GF', 'MAKE', 1, 10, 'MAKE', 1,
                    0, 0, 0, 12000, 10000, 3000)
            """);

            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('FLOOR_GF', 'ROOM_LI', 'MAKE', 1, 10, 'MAKE', 1,
                    0, 0, 0, 5000, 4000, 3000)
            """);

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
        }
    }

    /**
     * Seed the 3-deep inheritance chain for W-INHERIT-1:
     *
     * <pre>
     * DX_BASE (base order, Ref_Order_ID = NULL)
     *   └─ DX_SOLAR (parent = DX_BASE)
     *        └─ DX_SOLAR_PREMIUM (parent = DX_SOLAR)
     * </pre>
     *
     * DX_SOLAR carries: Remove on RE.GF.LI.TABLE_001 (solar panel replaces table)
     * DX_SOLAR_PREMIUM carries: Remove on RE.GF.LI.SOFA_001 (premium upgrade)
     */
    private static void seedInheritanceChain(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // DX_BASE — base order (no parent)
            stmt.execute("""
                INSERT INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('DX_BASE', 'RE_DX', 'DX Base', NULL)
            """);

            // DX_SOLAR — child of DX_BASE
            stmt.execute("""
                INSERT INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('DX_SOLAR', 'RE_DX', 'DX Solar', 'DX_BASE')
            """);
            // DX_SOLAR exception: Remove TABLE_001
            stmt.execute("""
                INSERT INTO C_OrderLine (C_Order_ID, Line, family_ref, host_type,
                    locator_ref, Qty, is_reference_class)
                VALUES ('DX_SOLAR', 10, 'SOLAR_PANEL_400W', 'LEAF',
                    'RE.GF.LI.TABLE_001', 0, 0)
            """);

            // DX_SOLAR_PREMIUM — child of DX_SOLAR (grandchild of DX_BASE)
            stmt.execute("""
                INSERT INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('DX_SOLAR_PREMIUM', 'RE_DX', 'DX Solar Premium', 'DX_SOLAR')
            """);
            // DX_SOLAR_PREMIUM exception: Remove SOFA_001
            stmt.execute("""
                INSERT INTO C_OrderLine (C_Order_ID, Line, family_ref, host_type,
                    locator_ref, Qty, is_reference_class)
                VALUES ('DX_SOLAR_PREMIUM', 10, 'PREMIUM_SOFA', 'LEAF',
                    'RE.GF.LI.SOFA_001', 0, 0)
            """);
        }
    }

    /**
     * Seed a conflict chain for W-INHERIT-DEPTH-1:
     * DX_CONFLICT_BASE → DX_CONFLICT_PARENT → DX_CONFLICT_CHILD
     * Both PARENT and CHILD target RE.GF.LI.SOFA_001 with different actions.
     */
    private static void seedConflictChain(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT OR IGNORE INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('DX_CONFLICT_BASE', 'RE_DX', 'Conflict Base', NULL)
            """);
            stmt.execute("""
                INSERT OR IGNORE INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('DX_CONFLICT_PARENT', 'RE_DX', 'Conflict Parent', 'DX_CONFLICT_BASE')
            """);
            // Parent: Remove SOFA_001 (qty=0)
            stmt.execute("""
                INSERT INTO C_OrderLine (C_Order_ID, Line, family_ref, host_type,
                    locator_ref, Qty, is_reference_class)
                VALUES ('DX_CONFLICT_PARENT', 10, 'SOFA_REMOVAL', 'LEAF',
                    'RE.GF.LI.SOFA_001', 0, 0)
            """);

            stmt.execute("""
                INSERT OR IGNORE INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('DX_CONFLICT_CHILD', 'RE_DX', 'Conflict Child', 'DX_CONFLICT_PARENT')
            """);
            // Child: Compress SOFA_001 (qty=3, is_reference_class=1) — overrides parent's Remove
            stmt.execute("""
                INSERT INTO C_OrderLine (C_Order_ID, Line, family_ref, host_type,
                    locator_ref, Qty, is_reference_class)
                VALUES ('DX_CONFLICT_CHILD', 10, 'SOFA_COMPRESSED', 'LEAF',
                    'RE.GF.LI.SOFA_001', 3, 1)
            """);
        }
    }

    /**
     * Seed cyclic orders: CYCLE_A → CYCLE_B → CYCLE_A (cycle).
     */
    private static void seedCyclicOrders(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT OR IGNORE INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('CYCLE_A', 'RE_DX', 'Cycle A', 'CYCLE_B')
            """);
            stmt.execute("""
                INSERT OR IGNORE INTO C_Order (Value, C_DocType_ID, Name, Ref_Order_ID)
                VALUES ('CYCLE_B', 'RE_DX', 'Cycle B', 'CYCLE_A')
            """);
        }
    }
}
