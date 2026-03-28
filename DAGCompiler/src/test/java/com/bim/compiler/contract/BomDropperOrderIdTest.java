package com.bim.compiler.contract;

import com.bim.compiler.bom.BomDropper;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;

import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-PROJ-ID-1: Two orders of the same DocType coexist in compile DB.
 *
 * <p>Proves R-PROJ-3 fix: parameterized orderId prevents C_Order_ID collision
 * when multiple buildings share the same C_DocType (e.g., 180 houses all type RE_SH).
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session 0 — Witness: W-PROJ-ID-1
 */
// Implementing ProjectOrderBlueprint.md §14.3 Session 0 — Witness: W-PROJ-ID-1
public class BomDropperOrderIdTest {

    @Test
    void twoOrdersSameDocTypeBothSurvive() throws Exception {
        // In-memory SQLite DB with required schema + minimal BOM data
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            initSchema(conn);
            seedBomData(conn);

            // Same DocType entry — both are RE_SH
            BuildingEntry entry = new BuildingEntry(
                "RE_SH", "TestHouse", "Test House",
                "RE", "SH",
                null, null, null,
                true, 10, 0,
                "GENERATIVE", "test", 0,
                10000, 8000, 6000
            );

            // Drop order 1 with explicit ID
            int leaves1 = BomDropper.drop(conn, entry, "RE_SH_001");
            assertTrue(leaves1 > 0, "Order 1 must produce leaves");

            // Drop order 2 with different explicit ID
            int leaves2 = BomDropper.drop(conn, entry, "RE_SH_002");
            assertTrue(leaves2 > 0, "Order 2 must produce leaves");

            // Both orders must survive
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM C_Order")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "Both C_Orders must coexist");
            }

            // Both order line sets must exist
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ?")) {
                ps.setString(1, "RE_SH_001");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertTrue(rs.getInt(1) > 0, "Order 1 must have C_OrderLines");
                }
                ps.setString(1, "RE_SH_002");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertTrue(rs.getInt(1) > 0, "Order 2 must have C_OrderLines");
                }
            }

            // Default path backward compat: drop(conn, entry) uses docTypeId
            int leaves3 = BomDropper.drop(conn, entry);
            assertTrue(leaves3 > 0, "Default path must still work");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ?")) {
                ps.setString(1, "RE_SH");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertTrue(rs.getInt(1) > 0, "Default orderId=docTypeId must produce lines");
                }
            }
        }
    }

    private void initSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // m_bom
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

            // m_bom_line
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
                    IsActive INTEGER NOT NULL DEFAULT 1,
                    created TEXT NOT NULL DEFAULT (datetime('now')),
                    updated TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);

            // C_OrderLine (from S60_schema.sql)
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

    private void seedBomData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // BUILDING BOM for RE/SH
            stmt.execute("""
                INSERT INTO m_bom (bom_id, Value, bom_name, group_by, bom_type, m_product_category_id,
                    doc_sub_type, is_active, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES ('SH_BUILDING', 'SH_BUILDING', 'SH Building', 'BUILDING', 'BUILDING', 'RE',
                    'SH', 1, 10000, 8000, 6000)
            """);

            // Two leaf products as children
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('SH_BUILDING', 'WALL_001', 'BUY', 2, 10, 'BUY', 1,
                    0, 0, 0, 1000, 200, 3000)
            """);
            stmt.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence,
                    component_type, is_active, dx, dy, dz,
                    allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES ('SH_BUILDING', 'SLAB_001', 'BUY', 1, 20, 'BUY', 1,
                    0, 0, 0, 10000, 8000, 200)
            """);
        }
    }
}
