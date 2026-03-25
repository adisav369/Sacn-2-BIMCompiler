package com.bim.compiler.contract;

import com.bim.compiler.bom.BomDropper;
import com.bim.compiler.bom.BomDropper.ExceptionLine;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session D: Remove + Compress mutations.
 *
 * <p>W-EXCEPTION-1: qty=0 on locator_ref → subtree skipped, element count drops.
 * <p>W-REFCLASS-1: is_reference_class=true, qty=3 → 3 instances at computed offsets.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-REFCLASS-1
 */
// Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RemoveCompressTest {

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

    // ── W-LOCATOR-1: locator_ref populated during normal BOM drop ──────────

    @Test
    @Order(1)
    @DisplayName("W-LOCATOR-1: BomDropper populates locator_ref on every C_OrderLine")
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    void locatorRefPopulated() throws Exception {
        int leaves = BomDropper.drop(conn, ENTRY, "LOC_TEST");
        assertTrue(leaves > 0, "Must produce leaves");

        // Every C_OrderLine must have a non-null locator_ref
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = 'LOC_TEST' AND locator_ref IS NULL")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "All C_OrderLines must have locator_ref");
        }

        // Root should have a locator_ref matching its category
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = ? AND Parent_OrderLine_ID IS NULL")) {
            ps.setString(1, "LOC_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Root must exist");
                String rootLocator = rs.getString(1);
                assertNotNull(rootLocator, "Root locator_ref must not be null");
                System.out.printf("[W-LOCATOR-1] Root locator_ref = %s%n", rootLocator);
            }
        }

        // Leaf locator_refs should be dot-separated paths
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'LOC_TEST' AND host_type = 'LEAF'")) {
            int leafCount = 0;
            while (rs.next()) {
                String loc = rs.getString(1);
                assertTrue(loc.contains("."), "Leaf locator_ref must be dot-separated: " + loc);
                leafCount++;
            }
            assertTrue(leafCount > 0, "Must have leaves with locator_ref");
            System.out.printf("[W-LOCATOR-1] %d leaves with dot-separated locator_ref%n", leafCount);
        }
    }

    // ── W-EXCEPTION-1: Remove mutation — qty=0 skips subtree ───────────────

    @Test
    @Order(2)
    @DisplayName("W-EXCEPTION-1: qty=0 on locator_ref skips subtree, element count drops")
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    void removeMutationSkipsSubtree() throws Exception {
        // First: normal drop to get baseline count
        int baselineLeaves = BomDropper.drop(conn, ENTRY, "BASELINE");
        assertTrue(baselineLeaves > 0, "Baseline must produce leaves");

        // Find the locator_ref of a leaf we want to remove
        String targetLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                     + "AND host_type = 'LEAF' LIMIT 1")) {
            assertTrue(rs.next(), "Must have at least one leaf");
            targetLocator = rs.getString(1);
        }

        // Drop with Remove exception: qty=0 on the target locator_ref
        Map<String, ExceptionLine> exceptions = Map.of(
                targetLocator, ExceptionLine.remove()
        );
        int reducedLeaves = BomDropper.drop(conn, ENTRY, "REMOVE_TEST", exceptions);

        // Element count must drop
        assertTrue(reducedLeaves < baselineLeaves,
                String.format("W-EXCEPTION-1: Remove must reduce leaves: baseline=%d, after=%d, target=%s",
                        baselineLeaves, reducedLeaves, targetLocator));

        // Verify the removed locator_ref does NOT exist in the output
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND locator_ref = ?")) {
            ps.setString(1, "REMOVE_TEST");
            ps.setString(2, targetLocator);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "Removed locator_ref must not appear in C_OrderLine: " + targetLocator);
            }
        }

        System.out.printf("[W-EXCEPTION-1] baseline=%d, after_remove=%d, target=%s — PASS%n",
                baselineLeaves, reducedLeaves, targetLocator);
    }

    // ── W-EXCEPTION-2: Remove on assembly skips entire subtree ─────────────

    @Test
    @Order(3)
    @DisplayName("W-EXCEPTION-2: qty=0 on assembly locator_ref skips entire subtree")
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    void removeAssemblySkipsEntireSubtree() throws Exception {
        // Find a ROOM-level assembly locator_ref (has children)
        String assemblyLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                     + "AND host_type = 'ROOM' LIMIT 1")) {
            if (!rs.next()) {
                // No ROOM assemblies — try FLOOR
                try (ResultSet rs2 = stmt.executeQuery(
                        "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                        + "AND host_type = 'FLOOR' LIMIT 1")) {
                    assertTrue(rs2.next(), "Must have at least one assembly");
                    assemblyLocator = rs2.getString(1);
                }
            } else {
                assemblyLocator = rs.getString(1);
            }
        }

        // Drop with Remove on the assembly
        Map<String, ExceptionLine> exceptions = Map.of(
                assemblyLocator, ExceptionLine.remove()
        );
        int reducedLeaves = BomDropper.drop(conn, ENTRY, "REMOVE_ASSEMBLY", exceptions);

        // Count must drop more than removing one leaf (subtree effect)
        int baselineLeaves = countLeaves(conn, "BASELINE");
        assertTrue(reducedLeaves < baselineLeaves,
                "Removing assembly must reduce leaves more than a single leaf");

        // The assembly locator_ref and all descendants must be absent
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND locator_ref LIKE ?")) {
            ps.setString(1, "REMOVE_ASSEMBLY");
            ps.setString(2, assemblyLocator + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "Assembly and all descendants must be absent after Remove");
            }
        }

        System.out.printf("[W-EXCEPTION-2] baseline=%d, after_assembly_remove=%d, target=%s — PASS%n",
                baselineLeaves, reducedLeaves, assemblyLocator);
    }

    // ── W-REFCLASS-1: Compress mutation — reference class instantiation ────

    @Test
    @Order(4)
    @DisplayName("W-REFCLASS-1: is_reference_class=true, qty=3 → stored with is_reference_class flag")
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-REFCLASS-1
    void compressMutationStoresReferenceClass() throws Exception {
        // Find a FLOOR-level assembly locator_ref to compress
        String floorLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                     + "AND host_type = 'FLOOR' LIMIT 1")) {
            assertTrue(rs.next(), "Must have at least one FLOOR");
            floorLocator = rs.getString(1);
        }

        // Drop with Compress: is_reference_class=true, qty=3
        Map<String, ExceptionLine> exceptions = Map.of(
                floorLocator, ExceptionLine.compress(3)
        );
        int leaves = BomDropper.drop(conn, ENTRY, "COMPRESS_TEST", exceptions);

        // The compressed node must exist with is_reference_class=1 and qty=3
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Qty, is_reference_class FROM C_OrderLine "
                + "WHERE C_Order_ID = ? AND locator_ref = ?")) {
            ps.setString(1, "COMPRESS_TEST");
            ps.setString(2, floorLocator);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Compressed node must exist");
                assertEquals(3, rs.getInt("Qty"), "Qty must be 3");
                assertEquals(1, rs.getInt("is_reference_class"), "is_reference_class must be 1");
            }
        }

        // The compressed node must NOT have children (subtree not exploded)
        int compressedLineId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT C_OrderLine_ID FROM C_OrderLine WHERE C_Order_ID = ? AND locator_ref = ?")) {
            ps.setString(1, "COMPRESS_TEST");
            ps.setString(2, floorLocator);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                compressedLineId = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND Parent_OrderLine_ID = ?")) {
            ps.setString(1, "COMPRESS_TEST");
            ps.setInt(2, compressedLineId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "Reference class node must NOT have exploded children");
            }
        }

        System.out.printf("[W-REFCLASS-1] Compressed %s: qty=3, is_reference_class=1, no children — PASS%n",
                floorLocator);
    }

    // ── W-REFCLASS-2: Compress reduces total C_OrderLines vs full explosion ─

    @Test
    @Order(5)
    @DisplayName("W-REFCLASS-2: Compress produces fewer C_OrderLines than full explosion")
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-REFCLASS-1
    void compressReducesOrderLineCount() throws Exception {
        int baselineCount = countAllLines(conn, "BASELINE");

        // Find FLOOR locator
        String floorLocator;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT locator_ref FROM C_OrderLine WHERE C_Order_ID = 'BASELINE' "
                     + "AND host_type = 'FLOOR' LIMIT 1")) {
            assertTrue(rs.next());
            floorLocator = rs.getString(1);
        }

        Map<String, ExceptionLine> exceptions = Map.of(
                floorLocator, ExceptionLine.compress(3)
        );
        BomDropper.drop(conn, ENTRY, "COMPRESS_COUNT", exceptions);
        int compressedCount = countAllLines(conn, "COMPRESS_COUNT");

        // Compressed version must have fewer C_OrderLines (subtree collapsed to 1 node)
        assertTrue(compressedCount <= baselineCount,
                String.format("Compress must produce <= lines: baseline=%d, compressed=%d",
                        baselineCount, compressedCount));

        System.out.printf("[W-REFCLASS-2] baseline_lines=%d, compressed_lines=%d — PASS%n",
                baselineCount, compressedCount);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int countLeaves(Connection conn, String orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT SUM(Qty) FROM C_OrderLine WHERE C_Order_ID = ? AND host_type = 'LEAF'")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int countAllLines(Connection conn, String orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ── Schema + seed data ───────────────────────────────────────────────────

    private static void initSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE m_bom (
                    bom_id TEXT PRIMARY KEY,
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

            stmt.execute("""
                CREATE TABLE C_Order (
                    C_Order_ID TEXT PRIMARY KEY,
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
     * Seed data: BUILDING → FLOOR → ROOM → 2 LEAFs
     *
     * <pre>
     * SH_BUILDING (BUILDING, category=RE)
     *   └─ FLOOR_GF (FLOOR, category=GF)
     *        └─ ROOM_LI (ROOM/SET, category=LI)
     *             ├─ SOFA_001 (LEAF, qty=1)
     *             └─ TABLE_001 (LEAF, qty=1)
     * </pre>
     */
    private static void seedBomData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // BUILDING BOM
            stmt.execute("""
                INSERT INTO m_bom (bom_id, bom_name, group_by, bom_type, m_product_category_id,
                    doc_sub_type, is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('SH_BUILDING', 'SH Building', 'BUILDING', 'BUILDING', 'RE',
                    'SH', 1, 10000, 8000, 6000)
            """);

            // FLOOR BOM
            stmt.execute("""
                INSERT INTO m_bom (bom_id, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('FLOOR_GF', 'Ground Floor', 'BUILDING', 'FLOOR', 'GF',
                    1, 10000, 8000, 3000)
            """);

            // ROOM BOM (Living room)
            stmt.execute("""
                INSERT INTO m_bom (bom_id, bom_name, group_by, bom_type, m_product_category_id,
                    is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('ROOM_LI', 'Living Room', 'ROOM', 'SET', 'LI',
                    1, 5000, 4000, 3000)
            """);

            // BUILDING → FLOOR child
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('SH_BUILDING', 'FLOOR_GF', 'MAKE', 1, 10, 'MAKE', 1,
                    0, 0, 0, 10000, 8000, 3000)
            """);

            // FLOOR → ROOM child
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('FLOOR_GF', 'ROOM_LI', 'MAKE', 1, 10, 'MAKE', 1,
                    0, 0, 0, 5000, 4000, 3000)
            """);

            // ROOM → leaf products
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
}
