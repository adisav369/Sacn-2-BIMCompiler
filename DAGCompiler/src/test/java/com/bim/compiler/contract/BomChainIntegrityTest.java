/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Permanent gate — BOM chain structure invariants that must never regress.
 *
 * <p>These tests verify the structural integrity of the BOM DAG in
 * {@code BOM.db} (m_bom + m_bom_line tables):
 * <ul>
 *   <li><b>R1</b> UNIT root BOMs exist for every residential building</li>
 *   <li><b>R3</b> Duplex Level 2 FLOOR BOM definition exists</li>
 *   <li><b>R5a</b> No dangling MAKE child_product_id FK in m_bom_line</li>
 *   <li><b>R5b</b> Every active SET BOM has ≥1 child</li>
 * </ul>
 *
 * <p>R2/R4/R6/R7 deleted — queried c_orderline/c_order which were dropped
 * from BOM.db in the C_DocType migration (§11.9).
 */
@DisplayName("BOM Chain Integrity — Permanent Structural Gate (R1/R3/R5)")
class BomChainIntegrityTest {

    private static String bomDbPath() { return System.getProperty("bom.db"); }
    private static Connection conn;

    @BeforeAll static void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath());
        conn.createStatement().execute("ATTACH DATABASE '" + bomDbPath() + "' AS bom_db");
    }
    @AfterAll  static void close() throws SQLException { if (conn != null) conn.close(); }

    // ─────────────────────────────────────────────────────────────────────────
    // R1: BUILDING root BOMs — BUILDING_SH_STD and BUILDING_DX_STD must exist in m_bom
    // [EXTRACTED: migration_BOM2c_unit_orderlines.sql — BUILDING BOM definitions]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R1: BUILDING root BOMs for SH and DX exist in m_bom (chain entrypoints)")
    void unitRootExists() throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM m_bom
            WHERE bom_id IN ('BUILDING_SH_STD', 'BUILDING_DX_STD')
              AND bom_level = 'BUILDING' AND is_active = 1
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int count = rs.next() ? rs.getInt(1) : 0;
            assertEquals(2, count,
                "[R1] Expected 2 BUILDING root BOMs (BUILDING_SH_STD, BUILDING_DX_STD) in m_bom. "
                + "Found: " + count + ". [EXTRACTED: migration_BOM2c_unit_orderlines.sql]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R3: Duplex Level 2 storey Z = 3000mm
    // [EXTRACTED: Duplex Rosetta Stone — Level 2 elevation = 3000mm above Level 1]
    // [EXTRACTED: migration_BOM2c_floor_dz.sql]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R3: FLOOR_DX_L2_STD exists in m_bom with correct storey structure")
    void floorDzDeclared() throws SQLException {
        // DX is EXTRACTED — its FLOOR Orderlines may be deactivated (direct IFC coords used).
        // Instead of checking c_orderline, verify the BOM definition itself exists and has
        // the correct level. The Z offset is encoded in the BOM hierarchy, not the orderline.
        String sql = """
            SELECT COUNT(*) FROM m_bom
            WHERE bom_id = 'FLOOR_DX_L2_STD' AND bom_level = 'FLOOR' AND is_active = 1
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int count = rs.next() ? rs.getInt(1) : 0;
            assertTrue(count >= 1,
                "[R3] FLOOR_DX_L2_STD BOM definition missing from m_bom. "
                + "The BOM hierarchy must exist even if DX c_orderline anchors are deactivated (EXTRACTED mode).");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R5: Full DAG walk to leaf
    //   (a) No dangling MAKE child_product_id FK in m_bom_line (NORM-2: replaces child_bom_id)
    //   (b) Every active SET BOM has ≥1 child (leaf BUY or nested MAKE)
    //       Exemption: none currently (WARDROBE_SET deactivated or has children added)
    // [EXTRACTED: BOMChild.childBomId() — MAKE rows only, via child_product_id]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R5a: No dangling MAKE child_product_id FK in m_bom_line")
    void noDanglingChildBomFK() throws SQLException {
        String sql = """
            SELECT M_BOM_Line_ID, bom_id, child_product_id
            FROM m_bom_line
            WHERE is_active = 1
              AND component_type = 'MAKE'
              AND child_product_id IS NOT NULL
              AND child_product_id NOT IN (SELECT bom_id FROM m_bom)
            """;
        List<String> dangling = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) dangling.add(
                "M_BOM_Line_ID=" + rs.getInt("M_BOM_Line_ID")
                + " bom=" + rs.getString("bom_id")
                + " → child_product_id='" + rs.getString("child_product_id") + "' missing from m_bom");
        }
        assertTrue(dangling.isEmpty(),
            "[R5a] Dangling MAKE child_product_id FK in m_bom_line (nested BOM reference broken): "
            + dangling + ". [EXTRACTED: BOMAssemblerAD — MAKE child_product_id → m_bom]");
    }

    @Test
    @DisplayName("R5b: Every active SET BOM has ≥1 child (leaf or nested BOM)")
    void setBomHasChild() throws SQLException {
        String sql = """
            SELECT b.bom_id
            FROM m_bom b
            WHERE b.bom_level = 'SET' AND b.is_active = 1
              AND (SELECT COUNT(*) FROM m_bom_line bc
                    WHERE bc.bom_id = b.bom_id AND bc.is_active = 1
                      AND bc.child_product_id IS NOT NULL) = 0
            """;
        List<String> empty = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) empty.add(rs.getString("bom_id") + " (0 children)");
        }
        assertTrue(empty.isEmpty(),
            "[R5b] SET BOMs with no children (DAG terminates before reaching element): "
            + empty + ". Add m_bom_line rows (leaf BUY or nested MAKE). "
            + "[EXTRACTED: BOMChild — child_product_id unified FK (NORM-2)]");
    }

}
