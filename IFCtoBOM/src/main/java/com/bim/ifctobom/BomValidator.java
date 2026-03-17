package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Pre-commit QA validator for BOM databases — the last gate before data reaches disk.
 *
 * <p>See {@code docs/YAMLGuide.md} §"Drift Prevention" for the full guard table
 * and what is NOT validated (honest gaps documented as ASSUMPTION remarks).
 *
 * <h3>LESSON LEARNED (2026-03-15): WARN on broken data = silent compilation failure</h3>
 * <p>NULL child_product_id on LEAF lines was previously WARN. BOMWalker silently
 * skipped those lines → 0 placements → all downstream gates saw empty output and
 * couldn't distinguish "no data" from "broken data". Now FAIL — blocks commit.
 *
 * <p>Validates per-building {@code {PREFIX}_BOM.db} files only (SH_BOM.db, DX_BOM.db,
 * TE_BOM.db). No monolithic BOM.db exists.
 *
 * <p>Runs BEFORE commit (was post-commit). Any FAIL causes pipeline rollback.
 * Report shows:
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
 *
 * <p>ASSUMPTION: Expects exactly 1 BUILDING BOM with FLOOR children.
 * Infrastructure IFCs would produce FACILITY/SEGMENT BOMs instead — checkBomCounts
 * and checkAabbEnvelope would FAIL or no-op. See {@code docs/InfrastructureAnalysis.md}.
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
     * @param bomConn            read-only connection to the output BOM DB (already committed)
     * @param extractionCount    total active elements from I_Element_Extraction (-1 to skip reconciliation)
     * @param compositionPaired  half-unit LEAF lines from CompositionBomBuilder (each represents
     *                           2 physical elements: A-side + B-side mirror). 0 for simple buildings.
     * @return number of FAIL checks (0 = clean)
     */
    public static int validateAndReport(Connection bomConn,
                                        int extractionCount, int compositionPaired) throws SQLException {
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
        if (extractionCount >= 0) {
            fails += checkExtractionReconciliation(bomConn, extractionCount, compositionPaired);
        }
        printFloorDistribution(bomConn);
        printComplianceReport(bomConn);

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
            // BOM header count
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
            // FACTORIZE-v1: also report total instances (SUM(qty))
            rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(qty), COUNT(*)) FROM m_bom_line");
            int totalInstances = rs.next() ? rs.getInt(1) : lines;
            String lineInfo = (totalInstances != lines)
                    ? lines + " lines (" + totalInstances + " instances)"
                    : String.valueOf(lines);
            report("BOM lines", lineInfo, lines > 0 ? "PASS" : "FAIL");
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

    /**
     * LEAF line element_ref check — every LEAF must be traceable.
     *
     * <p>Extraction-sourced LEAF lines (storey IS NOT NULL) MUST have element_ref.
     * Missing element_ref on an extraction line means the element can't be traced
     * back to its IFC source — FAIL. Fix: rebuild component_library.db or add
     * the element from the IFC extraction DB where similar objects are instanced.
     *
     * <p>Template/static LEAF lines (storey IS NULL) don't have element_ref by
     * design — they're BOM-to-BOM links (e.g. SH_LIVING_SET), not IFC elements.
     * These are accounted for by {@link #checkTackIO} and {@link #checkProductNormalization}.
     */
    private static int checkElementRefs(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            // Extraction-sourced LEAF lines MUST have element_ref
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF
                    + " AND storey IS NOT NULL AND (element_ref IS NULL OR element_ref = '')");
            int extractionMissing = rs.next() ? rs.getInt(1) : 0;

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF
                    + " AND element_ref IS NOT NULL AND element_ref != ''");
            int populated = rs.next() ? rs.getInt(1) : 0;

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF);
            int total = rs.next() ? rs.getInt(1) : 0;

            int templateLines = total - populated;
            if (extractionMissing > 0) {
                report("Element refs on LEAF lines",
                        populated + "/" + total + " (" + extractionMissing
                        + " extraction lines MISSING element_ref)",
                        "FAIL");
                System.err.printf("  [FAIL] %d extraction LEAF lines have no element_ref. "
                        + "Rebuild component_library.db or add elements from IFC extraction DB "
                        + "where similar objects are instanced.%n", extractionMissing);
                fails++;
            } else {
                String info = populated + "/" + total;
                if (templateLines > 0) {
                    info += " (" + templateLines + " template/static — OK)";
                }
                report("Element refs on LEAF lines", info, "PASS");
            }
        }
        return fails;
    }

    /**
     * Product normalization: LEAF lines MUST reference M_Product types.
     * NULL child_product_id on a LEAF line means BOMWalker will skip it
     * at compile time → silent 0-placement output. This is a FAIL, not
     * a warning — the pipeline should never commit data with this defect.
     *
     * <p>GUARD: This check catches the failure mode where I_Element_Extraction
     * has NULL M_Product_ID (e.g. migration not applied after re-extraction).
     * If this FAILs, apply the relevant migration_P0x migration to
     * component_library.db and re-run IFCtoBOM.
     *
     * <p>TODO [FACTORIZE-v1 2026-03-17] This method already computes and reports
     * the factorization ratio (lines/unique products). After factorization, the
     * ratio should approach 1.0 (one line per product). Consider promoting the
     * ratio to WARN if > 10× (would have caught TE's 51× bloat early).
     */
    private static int checkProductNormalization(Connection conn) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF);
            int leafLines = rs.next() ? rs.getInt(1) : 0;
            if (leafLines == 0) return 0;

            // FACTORIZE-v1: total instances = SUM(qty) across all LEAF lines
            rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(qty), COUNT(*)) FROM m_bom_line WHERE " + IS_LEAF);
            int leafInstances = rs.next() ? rs.getInt(1) : leafLines;

            // LEAF lines with product reference — FAIL if any are unlinked
            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line " +
                    "WHERE " + IS_LEAF + " " +
                    "AND child_product_id IS NOT NULL AND child_product_id <> ''");
            int linked = rs.next() ? rs.getInt(1) : 0;
            int unlinked = leafLines - linked;

            // ASSUMPTION: Every LEAF line must have a non-NULL child_product_id.
            // NULL means BOMWalker.walkChildren() will skip the line (silent 0 output).
            // This was previously WARN — changed to FAIL because silent skip at
            // compile time is undetectable without this gate.
            report("Product-linked LEAF lines",
                    linked + "/" + leafLines + " (" + (unlinked > 0 ? unlinked + " UNLINKED" : "all linked") + ")",
                    unlinked == 0 ? "PASS" : "FAIL");
            if (unlinked > 0) fails++;

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
                // FACTORIZE-v1: report both line ratio and instance ratio
                double lineRatio = (double) leafLines / uniqueProducts;
                double instanceRatio = (double) leafInstances / uniqueProducts;
                String ratioInfo = (leafInstances != leafLines)
                        ? String.format("%d lines (%d instances) / %d products = %.1fx lines, %.1fx reuse",
                                leafLines, leafInstances, uniqueProducts, lineRatio, instanceRatio)
                        : String.format("%d lines / %d products = %.1fx", leafLines, uniqueProducts, lineRatio);
                report("Factorization ratio", ratioInfo,
                        lineRatio <= 10.0 ? "PASS" : "WARN");
            } else {
                report("M_Product catalog (non-assembly)",
                        catalogProducts + " products (" + leafLines + " lines unfactorized)",
                        catalogProducts > 0 ? "WARN" : "WARN");
            }
        }
        return fails;
    }

    /**
     * Extraction reconciliation — the "accountant check".
     * Every active element in I_Element_Extraction must be accounted for.
     * Counts extraction-sourced LEAF lines (element_ref IS NOT NULL) and
     * adjusts for composition: each half-unit LEAF line represents 2
     * physical elements (A-side + mirrored B-side).
     *
     * <p>Formula: extractionLeafs + compositionPaired = extractionCount
     * <ul>
     *   <li>SH: 55 + 0 = 55 (no composition)</li>
     *   <li>DX: 614 + 485 = 1099 (MIRRORED_PAIR)</li>
     * </ul>
     *
     * <p>GUARD: Catches unmapped storey silent drops, scope box boundary
     * losses, and composition pairing imbalances. At Terminal scale (48K
     * elements), even 1% loss = 480 elements silently missing from output.
     *
     * <p>TODO [FACTORIZE-v1 2026-03-17] When m_bom_line gets a {@code qty}
     * column, this check must use {@code SUM(qty)} instead of {@code COUNT(*)}.
     * Currently works because qty is implicitly 1 per row (unfactored).
     * After factorization: 943 lines with SUM(qty)=48,428 must still
     * reconcile to extractionCount. The SQL change:
     * {@code SELECT COALESCE(SUM(qty),0) FROM m_bom_line WHERE component_type='LEAF' AND element_ref IS NOT NULL}
     */
    private static int checkExtractionReconciliation(Connection conn,
                                                     int expectedCount,
                                                     int compositionPaired) throws SQLException {
        int fails = 0;
        try (Statement stmt = conn.createStatement()) {
            // Count extraction-sourced LEAF instances (SUM(qty), not COUNT(*)).
            // Template refs from FloorRoomBomBuilder and static_children
            // don't have element_ref — they're BOM-to-BOM links, not elements.
            // FACTORIZE-v1: SUM(qty) handles both unfactored (qty=1 per row)
            // and factored (qty=N per type line) BOMs correctly.
            ResultSet rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(qty), COUNT(*)) FROM m_bom_line WHERE " + IS_LEAF
                    + " AND element_ref IS NOT NULL AND element_ref != ''");
            int leafCount = rs.next() ? rs.getInt(1) : 0;

            // In MIRRORED_PAIR buildings, each half-unit LEAF represents 2
            // physical elements (A + B side). The B-side elements don't get
            // separate LEAF lines — they're accounted for by the composition.
            int accountedFor = leafCount + compositionPaired;
            int delta = accountedFor - expectedCount;
            String detail;
            if (compositionPaired > 0) {
                detail = String.format(
                        "%d LEAFs + %d paired = %d vs %d extracted (delta=%+d)",
                        leafCount, compositionPaired, accountedFor, expectedCount, delta);
            } else {
                detail = String.format(
                        "%d extraction LEAFs vs %d extracted (delta=%+d)",
                        leafCount, expectedCount, delta);
            }
            if (delta == 0) {
                report("Extraction reconciliation", detail, "PASS");
            } else {
                report("Extraction reconciliation", detail, "FAIL");
                fails++;
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

    /**
     * Verb pattern compliance report — compression metrics and AD_Val_Rule diagnostics.
     * Informational only (no FAIL/PASS), printed after the 9 QA checks.
     */
    private static void printComplianceReport(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Recipe lines vs instances
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF);
            int recipeLines = rs.next() ? rs.getInt(1) : 0;
            if (recipeLines == 0) return;

            rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(qty), COUNT(*)) FROM m_bom_line WHERE " + IS_LEAF);
            int totalInstances = rs.next() ? rs.getInt(1) : recipeLines;

            // Verb coverage
            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM m_bom_line WHERE " + IS_LEAF
                    + " AND verb_ref IS NOT NULL");
            int verbLines = rs.next() ? rs.getInt(1) : 0;

            rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(qty), 0) FROM m_bom_line WHERE " + IS_LEAF
                    + " AND verb_ref IS NOT NULL");
            int verbInstances = rs.next() ? rs.getInt(1) : 0;

            int flatLines = recipeLines - verbLines;
            int flatInstances = totalInstances - verbInstances;

            // Only print compliance section if factorization occurred
            if (verbLines == 0 && totalInstances == recipeLines) return;

            System.out.println();
            System.out.println("  ── Verb Pattern Compliance ──");
            double ratio = totalInstances > 0 ? (double) totalInstances / recipeLines : 1.0;
            System.out.printf("    Recipe lines:     %,d%n", recipeLines);
            System.out.printf("    Total instances:  %,d%n", totalInstances);
            System.out.printf("    Compression:      %.0f:1 (%,d → %,d)%n",
                    ratio, totalInstances, recipeLines);

            if (verbLines > 0) {
                System.out.printf("    Verb lines:       %d (%,d instances, %.1f%% coverage)%n",
                        verbLines, verbInstances,
                        100.0 * verbInstances / totalInstances);
                System.out.printf("    Flat lines:       %d (%,d instances)%n",
                        flatLines, flatInstances);
            }

            // Per-verb breakdown
            rs = stmt.executeQuery(
                    "SELECT SUBSTR(verb_ref, 1, INSTR(verb_ref, ':') - 1) AS verb, "
                    + "COUNT(*) AS lines, COALESCE(SUM(qty), COUNT(*)) AS instances "
                    + "FROM m_bom_line WHERE " + IS_LEAF
                    + " AND verb_ref IS NOT NULL "
                    + "GROUP BY verb ORDER BY instances DESC");
            boolean hasVerbs = false;
            while (rs.next()) {
                if (!hasVerbs) {
                    System.out.println("    Per-verb:");
                    hasVerbs = true;
                }
                System.out.printf("      %-8s %4d lines → %,7d instances%n",
                        rs.getString(1), rs.getInt(2), rs.getInt(3));
            }

            // Per-discipline breakdown
            rs = stmt.executeQuery(
                    "SELECT b.bom_category, "
                    + "COUNT(*) AS lines, COALESCE(SUM(l.qty), COUNT(*)) AS instances "
                    + "FROM m_bom_line l JOIN m_bom b ON l.bom_id = b.bom_id "
                    + "WHERE l." + IS_LEAF + " "
                    + "GROUP BY b.bom_category ORDER BY instances DESC");
            boolean hasDisc = false;
            while (rs.next()) {
                if (!hasDisc) {
                    System.out.println("    Per-discipline:");
                    hasDisc = true;
                }
                String cat = rs.getString(1);
                System.out.printf("      %-8s %4d lines → %,7d instances%n",
                        cat != null ? cat : "--", rs.getInt(2), rs.getInt(3));
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
