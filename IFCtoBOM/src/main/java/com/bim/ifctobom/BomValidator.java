package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Post-generation QA validator for BOM databases.
 *
 * <p>Validates per-building {@code *_BOM.db} files only (SH_BOM.db, DX_BOM.db,
 * TE_BOM.db). Never references the temporary monolithic {@code BOM.db}.
 *
 * <p>Runs after pipeline commit and prints a structured report that lets
 * developers immediately see whether the output is sound:
 * <ul>
 *   <li>BOM count and type breakdown</li>
 *   <li>Normalization: no duplicate bom_ids, no orphan lines</li>
 *   <li>Redundancy: duplicate lines at same position</li>
 *   <li>Relative offsets: no world-coordinate hardcodes</li>
 *   <li>AABB envelope: BUILDING vs children containment</li>
 *   <li>Tack I/O: BUILDING assembly children reference valid BOMs</li>
 *   <li>Element ref population on leaf lines</li>
 *   <li>Product normalization and factorization ratio</li>
 *   <li>Per-floor line distribution</li>
 * </ul>
 *
 * <p>Assembly-ref lines (parent→child BOM) are identified structurally:
 * {@code child_product_id IN (SELECT bom_id FROM m_bom)}.
 * Leaf lines are everything else: {@code component_type = 'LEAF'}.
 *
 * <p>Each check prints PASS/WARN/FAIL. Any FAIL means the BOM needs attention
 * before attempting compilation.
 */
public class BomValidator {

    private static final double WORLD_COORD_THRESHOLD_M = 500; // metres

    /** SQL fragment: lines whose child_product_id is itself a BOM (assembly refs). */
    private static final String IS_ASSEMBLY_REF =
            "child_product_id IN (SELECT bom_id FROM m_bom)";

    /** SQL fragment: leaf lines (component_type = 'LEAF'). */
    private static final String IS_LEAF = "component_type = 'LEAF'";

    /**
     * Validate and print QA report for a BOM database.
     *
     * @param bomConn read-only connection to the output BOM DB (already committed)
     * @return number of FAIL checks (0 = clean)
     */
    public static int validateAndReport(Connection bomConn) throws SQLException {
        System.out.println();
        System.out.println("=== BOM QA Validation ===");

        int fails = 0;

        fails += checkBomCounts(bomConn);
        fails += checkNormalization(bomConn);
        fails += checkDuplicateLines(bomConn);
        fails += checkRelativeOffsets(bomConn);
        fails += checkAabbEnvelope(bomConn);
        fails += checkTackIO(bomConn);
        fails += checkElementRefs(bomConn);
        fails += checkProductNormalization(bomConn);
        printFloorDistribution(bomConn);

        System.out.println();
        if (fails == 0) {
            System.out.println("[QA] All checks PASSED");
        } else {
            System.out.printf("[QA] %d check(s) FAILED — review before compilation%n", fails);
        }
        return fails;
    }

    /** BOM count and type breakdown. */
    private static int checkBomCounts(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM m_bom");
            int total = rs.next() ? rs.getInt(1) : 0;

            rs = stmt.executeQuery(
                    "SELECT bom_type, COUNT(*) FROM m_bom GROUP BY bom_type ORDER BY bom_type");
            StringBuilder types = new StringBuilder();
            int buildingCount = 0;
            while (rs.next()) {
                if (types.length() > 0) types.append(", ");
                types.append(rs.getString(1)).append("=").append(rs.getInt(2));
                if ("BUILDING".equals(rs.getString(1))) buildingCount = rs.getInt(2);
            }
            report("BOM count", total + " (" + types + ")",
                    buildingCount == 1 ? "PASS" : "FAIL");
            if (buildingCount != 1) fails++;

            rs = stmt.executeQuery("SELECT COUNT(*) FROM m_bom_line");
            int lines = rs.next() ? rs.getInt(1) : 0;
            report("BOM lines", String.valueOf(lines), lines > 0 ? "PASS" : "FAIL");
            if (lines == 0) fails++;

            // DocBaseType / DocSubType from BUILDING BOM
            rs = stmt.executeQuery(
                    "SELECT doc_base_type, doc_sub_type FROM m_bom WHERE bom_type = 'BUILDING'");
            if (rs.next()) {
                String dbt = rs.getString(1);
                String dst = rs.getString(2);
                report("DocType (DBT/DST)",
                        (dbt != null ? dbt : "-") + "_" + (dst != null ? dst : "-"),
                        (dbt != null && dst != null) ? "PASS" : "FAIL");
                if (dbt == null || dst == null) fails++;
            }

            // BOM categories
            rs = stmt.executeQuery(
                    "SELECT bom_category, COUNT(*) FROM m_bom " +
                    "WHERE bom_category IS NOT NULL GROUP BY bom_category ORDER BY bom_category");
            StringBuilder cats = new StringBuilder();
            while (rs.next()) {
                if (cats.length() > 0) cats.append(", ");
                cats.append(rs.getString(1)).append("=").append(rs.getInt(2));
            }
            report("BOM categories",
                    cats.length() > 0 ? cats.toString() : "none",
                    cats.length() > 0 ? "PASS" : "WARN");

            // M_Product count
            rs = stmt.executeQuery("SELECT COUNT(*) FROM M_Product");
            int products = rs.next() ? rs.getInt(1) : 0;
            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM M_Product WHERE extracted_from != 'BOM_ASSEMBLY'");
            int catalogProducts = rs.next() ? rs.getInt(1) : 0;
            report("M_Product",
                    products + " total (" + catalogProducts + " catalog, " +
                    (products - catalogProducts) + " assembly stubs)",
                    catalogProducts > 0 ? "PASS" : "WARN");
        }
        return fails;
    }

    /** No duplicate bom_ids, no orphan lines. */
    private static int checkNormalization(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM (SELECT bom_id FROM m_bom GROUP BY bom_id HAVING COUNT(*) > 1)");
            int dupBoms = rs.next() ? rs.getInt(1) : 0;
            report("Duplicate bom_ids", String.valueOf(dupBoms),
                    dupBoms == 0 ? "PASS" : "FAIL");
            if (dupBoms > 0) fails++;

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id NOT IN (SELECT bom_id FROM m_bom)");
            int orphans = rs.next() ? rs.getInt(1) : 0;
            report("Orphan lines", String.valueOf(orphans),
                    orphans == 0 ? "PASS" : "FAIL");
            if (orphans > 0) fails++;
        }
        return fails;
    }

    /** Duplicate lines: same bom_id + child_product_id + dx/dy/dz. */
    private static int checkDuplicateLines(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("""
                    SELECT COUNT(*) FROM (
                      SELECT bom_id, child_product_id, dx, dy, dz
                      FROM m_bom_line
                      GROUP BY bom_id, child_product_id, dx, dy, dz
                      HAVING COUNT(*) > 1
                    )""");
            int dups = rs.next() ? rs.getInt(1) : 0;
            report("Duplicate positions", String.valueOf(dups),
                    dups == 0 ? "PASS" : "WARN");
        }
        return 0;
    }

    /** All offsets relative: no dx/dy/dz exceeding world threshold. */
    private static int checkRelativeOffsets(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(String.format(
                    "SELECT COUNT(*) FROM m_bom_line " +
                    "WHERE ABS(dx) > %f OR ABS(dy) > %f OR ABS(dz) > %f",
                    WORLD_COORD_THRESHOLD_M, WORLD_COORD_THRESHOLD_M, WORLD_COORD_THRESHOLD_M));
            int worldCoords = rs.next() ? rs.getInt(1) : 0;
            report("World-coord offsets (>" + (int) WORLD_COORD_THRESHOLD_M + "m)",
                    String.valueOf(worldCoords),
                    worldCoords == 0 ? "PASS" : "FAIL");
            if (worldCoords > 0) fails++;

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom WHERE origin_x != 0 OR origin_y != 0 OR origin_z != 0");
            int nonZeroOrigins = rs.next() ? rs.getInt(1) : 0;
            report("Non-zero BOM origins", String.valueOf(nonZeroOrigins),
                    nonZeroOrigins == 0 ? "PASS" : "WARN");
        }
        return fails;
    }

    /** BUILDING AABB must contain all floor AABBs (width/depth). Height may overlap. */
    private static int checkAabbEnvelope(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm FROM m_bom WHERE bom_type = 'BUILDING'");
            if (!rs.next()) return 0;
            double bldgW = rs.getDouble(1);
            double bldgD = rs.getDouble(2);
            double bldgH = rs.getDouble(3);

            rs = stmt.executeQuery(
                    "SELECT MAX(aabb_width_mm), MAX(aabb_depth_mm), SUM(aabb_height_mm) " +
                    "FROM m_bom WHERE bom_type = 'FLOOR'");
            if (!rs.next()) return 0;
            double floorMaxW = rs.getDouble(1);
            double floorMaxD = rs.getDouble(2);
            double floorSumH = rs.getDouble(3);

            boolean wOk = floorMaxW <= bldgW * 1.01;
            boolean dOk = floorMaxD <= bldgD * 1.01;
            report("AABB W containment",
                    String.format("floor max %.0f <= building %.0f", floorMaxW, bldgW),
                    wOk ? "PASS" : "FAIL");
            if (!wOk) fails++;

            report("AABB D containment",
                    String.format("floor max %.0f <= building %.0f", floorMaxD, bldgD),
                    dOk ? "PASS" : "FAIL");
            if (!dOk) fails++;

            report("AABB H envelope",
                    String.format("building %.0f, floor sum %.0f (overlap=%.0f%%)",
                            bldgH, floorSumH,
                            floorSumH > 0 ? ((floorSumH - bldgH) / bldgH * 100) : 0),
                    bldgH <= floorSumH ? "PASS" : "WARN");
        }
        return fails;
    }

    /**
     * Tack I/O: assembly-ref lines (child_product_id resolves to a BOM)
     * from BUILDING must all point to valid BOMs.
     */
    private static int checkTackIO(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            // Assembly-ref lines from BUILDING pointing to non-existent BOMs
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line l " +
                    "JOIN m_bom b ON l.bom_id = b.bom_id " +
                    "WHERE b.bom_type = 'BUILDING' " +
                    "AND l." + IS_ASSEMBLY_REF);
            int validRefs = rs.next() ? rs.getInt(1) : 0;

            // All BUILDING children that reference another BOM by name
            // but that BOM doesn't exist (dangling)
            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line l " +
                    "JOIN m_bom b ON l.bom_id = b.bom_id " +
                    "WHERE b.bom_type = 'BUILDING' " +
                    "AND l.child_product_id IS NOT NULL " +
                    "AND l.child_product_id NOT IN (SELECT bom_id FROM m_bom) " +
                    "AND l.child_product_id NOT IN (SELECT product_id FROM M_Product)");
            int dangling = rs.next() ? rs.getInt(1) : 0;

            report("Tack: assembly refs valid",
                    dangling == 0 ? validRefs + " refs, all valid" : dangling + " dangling",
                    dangling == 0 ? "PASS" : "FAIL");
            if (dangling > 0) fails++;

            report("Tack: BUILDING children",
                    validRefs + " assembly refs",
                    validRefs > 0 ? "PASS" : "WARN");
        }
        return fails;
    }

    /** LEAF lines should have element_ref populated. */
    private static int checkElementRefs(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF);
            int leafLines = rs.next() ? rs.getInt(1) : 0;

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line " +
                    "WHERE " + IS_LEAF + " AND (element_ref IS NULL OR element_ref = '')");
            int missing = rs.next() ? rs.getInt(1) : 0;

            int populated = leafLines - missing;
            report("Element refs on LEAF lines",
                    populated + "/" + leafLines,
                    missing == 0 ? "PASS" : "WARN");
        }
        return 0;
    }

    /**
     * Product normalization: LEAF lines should reference M_Product types,
     * and the ratio of unique products to total lines indicates factorization.
     * A proper BOM has reusable product types with qty — not 1:1 instance lines.
     */
    private static int checkProductNormalization(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF);
            int leafLines = rs.next() ? rs.getInt(1) : 0;
            if (leafLines == 0) return 0;

            // LEAF lines with product reference
            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line " +
                    "WHERE " + IS_LEAF + " " +
                    "AND child_product_id IS NOT NULL AND child_product_id <> ''");
            int linked = rs.next() ? rs.getInt(1) : 0;
            int unlinked = leafLines - linked;

            report("Product-linked LEAF lines",
                    linked + "/" + leafLines + " (" + (unlinked > 0 ? unlinked + " unlinked" : "all linked") + ")",
                    unlinked == 0 ? "PASS" : "WARN");

            // Unique products referenced (factorization ratio)
            rs = stmt.executeQuery(
                    "SELECT COUNT(DISTINCT child_product_id) FROM m_bom_line " +
                    "WHERE " + IS_LEAF + " " +
                    "AND child_product_id IS NOT NULL AND child_product_id <> ''");
            int uniqueProducts = rs.next() ? rs.getInt(1) : 0;

            // M_Product catalog count (excluding assembly stubs)
            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM M_Product WHERE extracted_from <> 'BOM_ASSEMBLY'");
            int catalogProducts = rs.next() ? rs.getInt(1) : 0;

            if (uniqueProducts > 0) {
                double ratio = (double) leafLines / uniqueProducts;
                report("Factorization ratio",
                        String.format("%d lines / %d products = %.1fx", leafLines, uniqueProducts, ratio),
                        ratio >= 2.0 ? "PASS" : "INFO");
            } else {
                report("M_Product catalog (non-assembly)",
                        catalogProducts + " products (" + leafLines + " lines unfactorized)",
                        catalogProducts > 0 ? "WARN" : "WARN");
            }
        }
        return fails;
    }

    /** Per-floor line distribution (informational). */
    private static void printFloorDistribution(Connection conn) throws SQLException {
        System.out.println("  Floor distribution:");
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT b.bom_id, b.bom_category, " +
                    "  (SELECT COUNT(*) FROM m_bom_line l WHERE l.bom_id = b.bom_id AND l." + IS_LEAF + ") AS leaf_lines, " +
                    "  b.aabb_width_mm, b.aabb_depth_mm, b.aabb_height_mm " +
                    "FROM m_bom b WHERE b.bom_type = 'FLOOR' ORDER BY b.seq_no");
            while (rs.next()) {
                System.out.printf("    %-16s [%s]  %6d lines  AABB %6.0f x %6.0f x %6.0f mm%n",
                        rs.getString(1),
                        rs.getString(2) != null ? rs.getString(2) : "--",
                        rs.getInt(3),
                        rs.getDouble(4), rs.getDouble(5), rs.getDouble(6));
            }
        }
    }

    private static void report(String check, String value, String status) {
        String marker = switch (status) {
            case "PASS" -> "PASS";
            case "WARN" -> "WARN";
            case "FAIL" -> "FAIL";
            case "INFO" -> "INFO";
            default -> "????";
        };
        System.out.printf("  [%s] %-40s %s%n", marker, check, value);
    }
}
