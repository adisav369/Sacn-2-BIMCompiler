/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layer 4 — Data Integrity Guards (against data fraud).
 *
 * <p>Cross-checks BOM.db (our dictionary) against reference extracted DBs
 * (the independent oracle extracted from IFC files by IfcOpenShell).
 * The oracle is the one database we didn't write — it's ground truth.
 *
 * <p>R17: Queries migrated from I_Element_Extraction (component_library.db)
 * to reference DBs (elements_meta + elements_rtree). Source truth is the
 * extracted DB, not a copy in the product catalog.
 *
 * <p>D-1 through D-5 rules detect:
 * <ul>
 *   <li>D-1: Orphan products — BOM references to non-existent M_Product rows</li>
 *   <li>D-2: Unit consistency — M_Product dimensions in meters, no unit confusion</li>
 *   <li>D-3: Count match — extraction element count vs C_DocType.ExpectedElements</li>
 *   <li>D-4: Product existence — BUY products must trace to M_Product catalog</li>
 *   <li>D-5: AABB vs extraction envelope — building dimensions not invented</li>
 * </ul>
 *
 * @see <a href="docs/TestArchitecture.md">TestArchitecture.md Layer 4</a>
 */
@DisplayName("Data Integrity — cross-database guards against data fraud")
public class DataIntegrityTest {

    private static String bomDbPath() { return System.getProperty("bom.db"); }
    private static final String COMP_DB = "library/component_library.db";

    /** AABB tolerance in mm — sub-millimetre rounding from REAL storage. */
    private static final double AABB_TOLERANCE_MM = 1.0;

    /**
     * IFC class products are legitimate structural references in template BOMs.
     * They map via ifc_class column, not M_Product_ID. Exclude from D-4.
     */
    private static final Set<String> IFC_CLASS_PRODUCTS = Set.of(
        "IfcAirTerminal", "IfcBeam", "IfcBuildingElementProxy", "IfcColumn",
        "IfcDiscreteAccessory", "IfcDoor", "IfcFan", "IfcFireSuppressionTerminal",
        "IfcFlowTerminal", "IfcFurniture", "IfcLightFixture", "IfcMember",
        "IfcOutlet", "IfcPipeFitting", "IfcPipeSegment", "IfcPlate",
        "IfcRailing", "IfcRoof", "IfcSanitaryTerminal", "IfcSlab",
        "IfcStair", "IfcStairFlight", "IfcSwitchingDevice", "IfcWall",
        "IfcWindow"
    );

    private static Connection conn;

    @BeforeAll
    static void openConnection() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath());
        conn.createStatement().execute(
            "ATTACH DATABASE '" + COMP_DB + "' AS lod");
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (conn != null) conn.close();
    }

    // =========================================================================
    // D-1: Orphan products — m_bom_line.child_product_id → M_Product FK
    // =========================================================================

    @Test
    @DisplayName("D-1: No orphan products — every BUY child_product_id exists in M_Product")
    void d1_noOrphanProducts() throws SQLException {
        List<String> orphans = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT DISTINCT bl.child_product_id " +
                 "FROM m_bom_line bl " +
                 "LEFT JOIN M_Product p ON bl.child_product_id = p.product_id " +
                 "WHERE bl.component_type = 'BUY' " +
                 "  AND bl.is_active = 1 " +
                 "  AND p.product_id IS NULL")) {
            while (rs.next()) {
                orphans.add(rs.getString(1));
            }
        }
        assertTrue(orphans.isEmpty(),
            "D-1 FAIL: " + orphans.size() + " BUY products not in M_Product: " + orphans);
    }

    // =========================================================================
    // D-1b: Orphan products — ALL component types, not just BUY
    // =========================================================================

    @Test
    @DisplayName("D-1b: No orphan products — every child_product_id resolves (any component_type)")
    void d1b_noOrphanProductsAnyType() throws SQLException {
        List<String> orphans = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT bl.child_product_id, bl.component_type, bl.bom_id " +
                 "FROM m_bom_line bl " +
                 "LEFT JOIN M_Product p ON bl.child_product_id = p.product_id " +
                 "WHERE bl.child_product_id IS NOT NULL " +
                 "  AND bl.is_active = 1 " +
                 "  AND p.product_id IS NULL")) {
            while (rs.next()) {
                orphans.add(rs.getString(1) + " (" + rs.getString(2) +
                    " in " + rs.getString(3) + ")");
            }
        }
        assertTrue(orphans.isEmpty(),
            "D-1b FAIL: " + orphans.size() + " child_product_id refs not in M_Product: " + orphans);
    }

    // =========================================================================
    // D-2: Unit consistency — M_Product dimensions in meters, no unit confusion
    // =========================================================================

    @Test
    @DisplayName("D-2: M_Product dimensions are in meters (no mm/m unit confusion)")
    void d2_unitConsistency() throws SQLException {
        // M_Product stores dimensions in meters. If someone puts 800 (mm)
        // instead of 0.8 (m), the product would be 800m wide. Guard: all
        // active M_Product dimensions must be < 100m (reasonable BIM max).
        // Also check: no zero or negative dimensions for active products.
        List<String> violations = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT product_id, width, depth, height FROM M_Product " +
                 "WHERE is_active = 1")) {
            while (rs.next()) {
                String id = rs.getString(1);
                double w = rs.getDouble(2);
                double d = rs.getDouble(3);
                double h = rs.getDouble(4);

                if (w > 100 || d > 100 || h > 100) {
                    violations.add(id + ": likely mm not meters"
                        + " (w=" + w + " d=" + d + " h=" + h + ")");
                }
                if (w <= 0 || d <= 0 || h <= 0) {
                    violations.add(id + ": zero/negative dim"
                        + " (w=" + w + " d=" + d + " h=" + h + ")");
                }
            }
        }
        assertTrue(violations.isEmpty(),
            "D-2 FAIL: " + violations.size() + " unit violations: " + violations);
    }

    // =========================================================================
    // D-3: Count match — extraction COUNT(*) vs C_DocType.ExpectedElements
    // =========================================================================

    @Test
    @DisplayName("D-3: Extraction element counts match C_DocType.ExpectedElements")
    void d3_countMatch() throws SQLException {
        // R17: Query reference DBs directly (source truth), not I_Element_Extraction copy.
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT d.C_DocType_ID, d.ProjectName, d.ExpectedElements, " +
                 "       d.ReferenceDbPath " +
                 "FROM C_DocType d " +
                 "WHERE d.Provenance = 'EXTRACTED' " +
                 "  AND d.ExpectedElements > 0 " +
                 "  AND d.IsActive = 1")) {
            while (rs.next()) {
                String docTypeId = rs.getString(1);
                String projectName = rs.getString(2);
                int expected = rs.getInt(3);
                String refDbPath = rs.getString(4);

                // Count elements in reference DB (the real oracle).
                // Convention: reference DBs exclude REB discipline.
                assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(refDbPath)),
                    "D-3: Reference DB not found: " + refDbPath);

                int actual;
                try (Connection refConn = DriverManager.getConnection(
                         "jdbc:sqlite:" + refDbPath);
                     Statement refStmt = refConn.createStatement();
                     ResultSet rs2 = refStmt.executeQuery(
                         "SELECT COUNT(*) FROM elements_meta")) {
                    rs2.next();
                    actual = rs2.getInt(1);
                }
                assertEquals(expected, actual,
                    "D-3 FAIL: " + docTypeId + " (" + projectName + "): " +
                    "ExpectedElements=" + expected + " but reference DB has " + actual);
            }
        }
    }

    // =========================================================================
    // D-4: Product existence — BUY products must trace to extraction oracle
    // =========================================================================

    @Test
    @DisplayName("D-4: BUY products in BUILDING BOMs exist in M_Product catalog")
    void d4_productExistence() throws SQLException {
        // R17: Check against M_Product in component_library.db (product catalog),
        // not I_Element_Extraction. Every extraction-sourced product has an M_Product row
        // with extracted_from set by ExtractionPopulator.
        List<String> missing = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT DISTINCT bl.child_product_id " +
                 "FROM m_bom_line bl " +
                 "WHERE bl.component_type = 'BUY' " +
                 "  AND bl.is_active = 1 " +
                 "  AND bl.child_product_id NOT IN " +
                 "      (SELECT bom_id FROM m_bom) " +    // exclude sub-BOM assemblies
                 "  AND bl.child_product_id NOT IN " +
                 "      (SELECT product_id FROM lod.M_Product " +
                 "       WHERE product_id IS NOT NULL)")) {
            while (rs.next()) {
                String productId = rs.getString(1);
                if (!IFC_CLASS_PRODUCTS.contains(productId)) {
                    missing.add(productId);
                }
            }
        }

        // Known aliases: BOM names that differ from extraction M_Product_ID.
        // These are naming variants, not fabricated products.
        // Each is verified manually to have a real IFC source.
        Set<String> knownAliases = Set.of(
            "Base_Cabinet",               // extraction: FURN_KITCHEN_BASE
            "Coffee_Table",               // extraction: FURN_COFFEE_TABLE
            "Counter_Top",                // extraction: COUNTER_TOP_600
            "DOOR_D2",                    // MY-specific door variant
            "Dining_Table_With_Chairs",   // extraction: FURN_DINING_TABLE
            "ELEC_LIGHT",                 // extraction: LIGHT_PENDANT_150W
            "FIXTURE_SINK",               // extraction: LAVATORY_OVAL_535
            "FURN_WARDROBE",              // wardrobe (MY template)
            "Piano",                      // extraction: FURN_PIANO
            "Sofa",                       // extraction: FURN_SOFA
            "Sofa_Loveseat",              // extraction: SOFA_1830
            "Tall_Cabinet",               // extraction: TALL_CABINET_800
            "Upper_Cabinet",              // extraction: UPPER_CABINET_1000
            "Vanity_Cabinet",             // extraction: VANITY_CABINET_450
            "WINDOW_W1",                  // SH window variant
            "WINDOW_W2"                   // MY window variant
        );

        // Remove known aliases — what's left is truly unknown.
        List<String> unknown = new ArrayList<>();
        for (String p : missing) {
            if (!knownAliases.contains(p)) {
                unknown.add(p);
            }
        }

        assertTrue(unknown.isEmpty(),
            "D-4 FAIL: " + unknown.size() + " BUY products not in extraction " +
            "and not in known aliases: " + unknown);

        // Also assert known alias count hasn't grown (catch new aliases).
        assertEquals(knownAliases.size(), missing.size(),
            "D-4 WARNING: alias count changed — expected " + knownAliases.size() +
            " known aliases, found " + missing.size() + ": " + missing);
    }

    // =========================================================================
    // D-5: AABB vs extraction envelope
    // =========================================================================

    @Test
    @DisplayName("D-5: BUILDING BOM AABB matches extraction envelope (within 1mm)")
    void d5_aabbVsExtractionEnvelope() throws SQLException {
        // R17: Query reference DBs directly for envelope, not I_Element_Extraction.
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT b.bom_id, b.aabb_width_mm, b.aabb_depth_mm, b.aabb_height_mm, " +
                 "       d.ProjectName, d.ReferenceDbPath " +
                 "FROM m_bom b " +
                 "JOIN C_DocType d ON b.doc_sub_type = d.doc_sub_type " +
                 "WHERE b.bom_type = 'BUILDING' " +
                 "  AND d.Provenance = 'EXTRACTED' " +
                 "  AND d.ExpectedElements > 0 " +
                 "  AND b.aabb_width_mm > 0")) {
            while (rs.next()) {
                String bomId = rs.getString(1);
                double bomW = rs.getDouble(2);
                double bomD = rs.getDouble(3);
                double bomH = rs.getDouble(4);
                String projectName = rs.getString(5);
                String refDbPath = rs.getString(6);

                assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(refDbPath)),
                    "D-5: Reference DB not found: " + refDbPath);

                // Extraction envelope in mm (source data is meters).
                // R17: elements_meta JOIN elements_rtree in reference DB.
                double extW, extD, extH;
                try (Connection refConn = DriverManager.getConnection(
                         "jdbc:sqlite:" + refDbPath);
                     Statement refStmt = refConn.createStatement();
                     ResultSet rs2 = refStmt.executeQuery(
                         "SELECT " +
                         "  ROUND((MAX(er.maxX) - MIN(er.minX)) * 1000, 1), " +
                         "  ROUND((MAX(er.maxY) - MIN(er.minY)) * 1000, 1), " +
                         "  ROUND((MAX(er.maxZ) - MIN(er.minZ)) * 1000, 1)  " +
                         "FROM elements_meta em " +
                         "JOIN elements_rtree er ON em.id = er.id")) {
                    assertTrue(rs2.next(),
                        "D-5: No extraction data for " + projectName);
                    extW = rs2.getDouble(1);
                    extD = rs2.getDouble(2);
                    extH = rs2.getDouble(3);
                }

                assertEquals(extW, bomW, AABB_TOLERANCE_MM,
                    "D-5 FAIL: " + bomId + " width: bom=" + bomW + " ext=" + extW);
                assertEquals(extD, bomD, AABB_TOLERANCE_MM,
                    "D-5 FAIL: " + bomId + " depth: bom=" + bomD + " ext=" + extD);
                assertEquals(extH, bomH, AABB_TOLERANCE_MM,
                    "D-5 FAIL: " + bomId + " height: bom=" + bomH + " ext=" + extH);
            }
        }
    }
}
