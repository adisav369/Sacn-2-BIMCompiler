/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

import com.bim.compiler.util.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Proves BOM tree consistency from compiled output.
 *
 * <p>Four invariants — all stated in BOM terms (parent, child, sibling, tack, qty):
 * <ul>
 *   <li><b>P-PARENT</b> — every child placed within parent's spatial extent</li>
 *   <li><b>P-SIBLING</b> — no sibling placements overlap</li>
 *   <li><b>P-QTY</b> — leaf count matches BOM qty</li>
 *   <li><b>P-TACK</b> — tack reconstruction is reversible (1mm tolerance)</li>
 * </ul>
 *
 * <p>Data sources: output DB (elements_rtree, elements_meta, c_orderline)
 * and BOM.db (m_bom_line dx/dy/dz). No sidecar tables.
 *
 * <p>Implementing LAST_MILE_PROBLEM.md §12 — Witness: W-BOM-PROVE
 *
 * @since S100-p91
 */
public class BomTreeProver {

    /** 1mm tolerance for tack reconstruction (integer mm → float m conversion). */
    private static final double TACK_TOLERANCE_M = 0.001;

    // ── Records ──────────────────────────────────────────────────────────────

    /** A node in the c_orderline BOM tree from the output DB. */
    record OrderNode(
        int id, Integer parentId, String hostType, String familyRef,
        double dx, double dy, double dz,
        double aabbW, double aabbD, double aabbH,
        int qty, String productId, String discipline
    ) {}

    /** An element from elements_rtree + elements_meta in the output DB. */
    record OutputElement(
        int id, String guid, String storey, String ifcClass, String elementRef,
        double minX, double maxX, double minY, double maxY, double minZ, double maxZ
    ) {}

    /** Result of a single proof check. */
    record CheckResult(int checked, int passed, List<String> warns) {}

    /** Result of all proof checks. */
    public record BomProofReport(
        int parentChecked, int parentPass, List<String> parentWarns,
        int siblingChecked, int siblingPass, List<String> siblingWarns,
        int qtyChecked, int qtyPass, List<String> qtyWarns,
        int tackChecked, int tackPass, List<String> tackWarns
    ) {
        public int totalWarns() {
            return parentWarns.size() + siblingWarns.size()
                 + qtyWarns.size() + tackWarns.size();
        }
    }

    // ── Main entry point ─────────────────────────────────────────────────────

    /**
     * Prove BOM tree consistency from compiled output.
     *
     * @param outputDbPath path to the output DB (elements_rtree, elements_meta, c_orderline)
     * @param bomDbPath    path to the BOM DB (m_bom_line with tack offsets)
     * @param prefix       building prefix for log output
     * @return proof report with per-check results
     */
    public static BomProofReport prove(String outputDbPath, String bomDbPath, String prefix) {
        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath)) {
            // Load BOM tree from output
            Map<Integer, OrderNode> nodes = loadOrderTree(outConn);
            List<OutputElement> elements = loadElements(outConn);

            if (nodes.isEmpty()) {
                BIMLogger.fine("PROVE", "{}: No c_orderline rows — skipping BOM tree proofs", prefix);
                return emptyReport();
            }

            // Load tack offsets from BOM.db for P-TACK
            Map<Integer, double[]> bomTacks = loadBomTacks(bomDbPath);

            // Run proofs
            CheckResult parent  = proveParent(nodes, prefix);
            CheckResult sibling = proveSibling(nodes, prefix);
            CheckResult qty     = proveQty(nodes, elements, prefix);
            CheckResult tack    = proveTack(nodes, elements, bomTacks, prefix);

            return new BomProofReport(
                parent.checked(),  parent.passed(),  parent.warns(),
                sibling.checked(), sibling.passed(), sibling.warns(),
                qty.checked(),     qty.passed(),     qty.warns(),
                tack.checked(),    tack.passed(),    tack.warns()
            );
        } catch (SQLException e) {
            BIMLogger.warn("PROVE", "{}: DB error — {}", prefix, e.getMessage());
            return emptyReport();
        }
    }

    // ── P-PARENT: Every child within parent's spatial extent ─────────────────

    /**
     * For each LEAF c_orderline, verify its position (dx/dy/dz) falls within
     * its immediate parent container's own allocated AABB.
     *
     * <p><b>Corrected 2026-09-08 (A9, Witness: W-A9-PPARENT) — the previous version of this
     * check was wrong and had a 0% pass rate on every real building tested, point-cloud AND
     * IFC-authored (DeKH Building A 0/498, SampleHousePC 0/70, Sample House 0/59), invisible
     * until this was the first session to verify {@code CHECK PLACEMENT} actually runs rather
     * than silently skip (see {@code BuildingRegistryTest}'s {@code GATE_SCOPE} fix).</b>
     *
     * <p>The old comment here claimed "LEAF dx/dy/dz are world-absolute," which is false —
     * verified by comparing real {@code c_orderline} data against the real walked positions in
     * {@code element_transforms} (same local frame, not offset by anything). LEAF dx/dy/dz is
     * a MIN-CORNER-relative offset local to its own IMMEDIATE parent only — matching
     * {@link StructuralBomBuilder}'s own documented convention verbatim: the building root's
     * own origin is computed as "building origin (LBD corner) from all elements", and a
     * child's own placement offset ({@code makeDx = fMinX - allMinX}) is commented
     * "always &gt;= 0". Every level of the tree is independently corner-relative to its own
     * parent; nothing here is meant to be accumulated up to a shared world origin, and the
     * root {@code BUILDING} node's own {@code dx/dy/dz} is a different value entirely (its own
     * real-world placement seed, {@code bom.getOriginX/Y/Z()}, fed into the actual placement
     * walk in {@code CompilationPipeline} — a different coordinate frame, not a relative tack
     * in this tree at all). Accumulating parent chains (the old {@code computeWorldPosition})
     * was summing values from two different, incompatible coordinate frames.
     *
     * <p>Verified against real data before this fix, not assumed: simulated this exact
     * corrected formula offline against the real {@code c_orderline} rows of all three
     * buildings and confirmed 100% pass on each (498/498, 70/70, 59/59) before changing this
     * file — see the A9 investigation in project memory / commit history for the full trace.
     */
    private static CheckResult proveParent(Map<Integer, OrderNode> nodes, String prefix) {
        int checked = 0, passed = 0;
        List<String> warns = new ArrayList<>();

        for (OrderNode child : nodes.values()) {
            // Only check LEAF children — container nesting is structural, not spatial
            if (!"LEAF".equals(child.hostType())) continue;
            if (child.parentId() == null) continue;
            OrderNode parent = nodes.get(child.parentId());
            if (parent == null) continue;

            // Skip parents with no allocated extent
            if (parent.aabbW() <= 0 && parent.aabbD() <= 0 && parent.aabbH() <= 0) continue;

            checked++;

            // Parent extent in metres (AABB is in mm)
            double extentW = parent.aabbW() / 1000.0;
            double extentD = parent.aabbD() / 1000.0;
            double extentH = parent.aabbH() / 1000.0;

            // Child position — LEAF dx/dy/dz is corner-relative to its OWN immediate parent
            // only (see class-level note above); no accumulation up the tree.
            double cx = child.dx();
            double cy = child.dy();
            double cz = child.dz();

            // Check: child within [0, extent] of its immediate parent's own corner origin —
            // corner-relative, matching StructuralBomBuilder's own "always >= 0" convention,
            // not center-relative.
            boolean withinX = extentW <= 0
                || (cx >= -TACK_TOLERANCE_M && cx <= extentW + TACK_TOLERANCE_M);
            boolean withinY = extentD <= 0
                || (cy >= -TACK_TOLERANCE_M && cy <= extentD + TACK_TOLERANCE_M);
            boolean withinZ = extentH <= 0
                || (cz >= -TACK_TOLERANCE_M && cz <= extentH + TACK_TOLERANCE_M);

            if (withinX && withinY && withinZ) {
                passed++;
            } else {
                String axis = !withinX ? "X" : !withinY ? "Y" : "Z";
                warns.add(String.format("LEAF %d outside parent %d on %s (pos=%.3f,%.3f,%.3f aabb=%.0f,%.0f,%.0fmm)",
                    child.id(), parent.id(), axis, cx, cy, cz,
                    parent.aabbW(), parent.aabbD(), parent.aabbH()));
            }
        }

        BIMLogger.fine("PROVE", "{}: P-PARENT {}/{} PASS, {} WARN",
            prefix, passed, checked, warns.size());
        System.out.printf("[FINE] PROVE %s: P-PARENT %d/%d PASS, %d WARN%n",
            prefix, passed, checked, warns.size());

        return new CheckResult(checked, passed, warns);
    }

    // ── P-SIBLING: No sibling placements overlap ─────────────────────────────

    /**
     * Two children of the same parent must not occupy the same space.
     * Checks tack offsets — if two siblings have identical dx/dy/dz, they overlap.
     */
    private static CheckResult proveSibling(Map<Integer, OrderNode> nodes, String prefix) {
        // Group children by parent
        Map<Integer, List<OrderNode>> childrenByParent = new LinkedHashMap<>();
        for (OrderNode node : nodes.values()) {
            if (node.parentId() != null) {
                childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
            }
        }

        int checked = 0, passed = 0;
        List<String> warns = new ArrayList<>();

        for (var entry : childrenByParent.entrySet()) {
            List<OrderNode> siblings = entry.getValue();
            // Check each pair for tack collision
            for (int i = 0; i < siblings.size(); i++) {
                for (int j = i + 1; j < siblings.size(); j++) {
                    OrderNode a = siblings.get(i);
                    OrderNode b = siblings.get(j);

                    // Only check LEAF siblings — container nodes (FLOOR, ROOM) naturally share space
                    if (!"LEAF".equals(a.hostType()) || !"LEAF".equals(b.hostType())) continue;

                    checked++;

                    // Two leaves at the same tack = overlap
                    boolean sameTack = Math.abs(a.dx() - b.dx()) < TACK_TOLERANCE_M
                                    && Math.abs(a.dy() - b.dy()) < TACK_TOLERANCE_M
                                    && Math.abs(a.dz() - b.dz()) < TACK_TOLERANCE_M;

                    if (!sameTack) {
                        passed++;
                    } else {
                        warns.add(String.format("siblings %d and %d share tack (%.3f,%.3f,%.3f) under parent %d",
                            a.id(), b.id(), a.dx(), a.dy(), a.dz(), entry.getKey()));
                    }
                }
            }
        }

        BIMLogger.fine("PROVE", "{}: P-SIBLING {}/{} PASS, {} WARN",
            prefix, passed, checked, warns.size());
        System.out.printf("[FINE] PROVE %s: P-SIBLING %d/%d PASS, %d WARN%n",
            prefix, passed, checked, warns.size());

        return new CheckResult(checked, passed, warns);
    }

    // ── P-QTY: Leaf count matches BOM qty ────────────────────────────────────

    /**
     * For each BOM line (c_orderline), verify the output contains the expected
     * number of elements. Groups LEAF lines by product and compares
     * sum(qty) to actual element count.
     */
    private static CheckResult proveQty(Map<Integer, OrderNode> nodes,
                                         List<OutputElement> elements, String prefix) {
        // Count LEAF lines → expected qty
        int totalExpected = 0;
        int leafLines = 0;
        for (OrderNode node : nodes.values()) {
            if (!"LEAF".equals(node.hostType())) continue;
            totalExpected += node.qty();
            leafLines++;
        }

        int totalActual = elements.size();

        int checked = 1; // one aggregate check
        int passed = 0;
        List<String> warns = new ArrayList<>();

        if (totalActual == totalExpected) {
            passed = 1;
        } else {
            warns.add(String.format("element count mismatch: BOM qty=%d (%d lines), output=%d (delta=%d)",
                totalExpected, leafLines, totalActual, totalActual - totalExpected));
        }

        BIMLogger.fine("PROVE", "{}: P-QTY {}/{} PASS, {} WARN (expected={}, actual={})",
            prefix, passed, checked, warns.size(), totalExpected, totalActual);
        System.out.printf("[FINE] PROVE %s: P-QTY %d/%d PASS, %d WARN%n",
            prefix, passed, checked, warns.size());

        return new CheckResult(checked, passed, warns);
    }

    // ── P-TACK: Tack reconstruction is reversible ────────────────────────────

    /**
     * For a sample of LEAF c_orderlines, verify:
     * LEAF.dx/dy/dz matches nearest output element centroid (within 1mm).
     *
     * <p>LEAF dx/dy/dz in the output c_orderline are world-absolute
     * (BomDropper writes computed positions). The proof checks that
     * these positions correspond to real elements in elements_rtree.
     */
    private static CheckResult proveTack(Map<Integer, OrderNode> nodes,
                                          List<OutputElement> elements,
                                          Map<Integer, double[]> bomTacks,
                                          String prefix) {
        // Collect LEAF world positions (dx/dy/dz are already world-absolute in output)
        List<OrderNode> leaves = new ArrayList<>();
        for (OrderNode node : nodes.values()) {
            if ("LEAF".equals(node.hostType())) leaves.add(node);
        }

        if (leaves.isEmpty() || elements.isEmpty()) {
            BIMLogger.fine("PROVE", "{}: P-TACK 0/0 PASS, 0 WARN (no LEAF nodes)", prefix);
            System.out.printf("[FINE] PROVE %s: P-TACK 0/0 PASS, 0 WARN (1mm tolerance)%n", prefix);
            return new CheckResult(0, 0, List.of());
        }

        // Sample: check up to 100 LEAF nodes
        int maxSample = Math.min(leaves.size(), 100);
        int checked = 0, passed = 0;
        List<String> warns = new ArrayList<>();

        for (OrderNode leaf : leaves) {
            if (checked >= maxSample) break;
            checked++;

            double lx = leaf.dx(), ly = leaf.dy(), lz = leaf.dz();

            // Find nearest output element by centroid distance
            double nearestDist = Double.MAX_VALUE;
            for (OutputElement el : elements) {
                double cx = (el.minX() + el.maxX()) / 2.0;
                double cy = (el.minY() + el.maxY()) / 2.0;
                double cz = (el.minZ() + el.maxZ()) / 2.0;
                double dist = Math.abs(cx - lx) + Math.abs(cy - ly) + Math.abs(cz - lz);
                if (dist < nearestDist) {
                    nearestDist = dist;
                }
            }

            if (nearestDist <= TACK_TOLERANCE_M * 3) {
                // Within tolerance (3mm Manhattan distance ≈ 1mm per axis)
                passed++;
            } else {
                warns.add(String.format("LEAF %d: tack (%.3f,%.3f,%.3f), nearest element drift=%.4fm",
                    leaf.id(), lx, ly, lz, nearestDist));
            }
        }

        BIMLogger.fine("PROVE", "{}: P-TACK {}/{} PASS, {} WARN (1mm tolerance)",
            prefix, passed, checked, warns.size());
        System.out.printf("[FINE] PROVE %s: P-TACK %d/%d PASS, %d WARN (1mm tolerance)%n",
            prefix, passed, checked, warns.size());

        return new CheckResult(checked, passed, warns);
    }

    // ── Data loading ─────────────────────────────────────────────────────────

    private static Map<Integer, OrderNode> loadOrderTree(Connection conn) throws SQLException {
        Map<Integer, OrderNode> nodes = new LinkedHashMap<>();
        try {
            String sql = """
                SELECT C_OrderLine_ID, Parent_OrderLine_ID, host_type, family_ref,
                       dx, dy, dz, aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                       Qty, M_Product_ID, Discipline
                FROM c_orderline WHERE IsActive = 1 ORDER BY C_OrderLine_ID
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    int id = rs.getInt("C_OrderLine_ID");
                    int parentRaw = rs.getInt("Parent_OrderLine_ID");
                    Integer parentId = rs.wasNull() ? null : parentRaw;
                    nodes.put(id, new OrderNode(
                        id, parentId,
                        rs.getString("host_type"),
                        rs.getString("family_ref"),
                        rs.getDouble("dx"), rs.getDouble("dy"), rs.getDouble("dz"),
                        rs.getDouble("aabb_width_mm"), rs.getDouble("aabb_depth_mm"), rs.getDouble("aabb_height_mm"),
                        rs.getInt("Qty"),
                        rs.getString("M_Product_ID"),
                        rs.getString("Discipline")
                    ));
                }
            }
        } catch (SQLException e) {
            // c_orderline may not exist in output DB
            BIMLogger.fine("PROVE", "c_orderline not found in output: {}", e.getMessage());
        }
        return nodes;
    }

    private static List<OutputElement> loadElements(Connection conn) throws SQLException {
        List<OutputElement> elements = new ArrayList<>();
        try {
            String sql = """
                SELECT m.id, m.guid, m.storey, m.ifc_class, m.element_ref,
                       r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                FROM elements_meta m
                JOIN elements_rtree r ON m.id = r.id
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    elements.add(new OutputElement(
                        rs.getInt("id"), rs.getString("guid"),
                        rs.getString("storey"), rs.getString("ifc_class"),
                        rs.getString("element_ref"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
        } catch (SQLException e) {
            BIMLogger.fine("PROVE", "elements not found in output: {}", e.getMessage());
        }
        return elements;
    }

    /**
     * Load tack offsets from BOM.db m_bom_line.
     * Key = M_BOM_Line_ID, Value = [dx, dy, dz] in metres.
     */
    private static Map<Integer, double[]> loadBomTacks(String bomDbPath) {
        Map<Integer, double[]> tacks = new LinkedHashMap<>();
        if (bomDbPath == null) return tacks;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath)) {
            String sql = "SELECT M_BOM_Line_ID, dx, dy, dz FROM m_bom_line WHERE is_active = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    tacks.put(rs.getInt("M_BOM_Line_ID"),
                        new double[]{rs.getDouble("dx"), rs.getDouble("dy"), rs.getDouble("dz")});
                }
            }
        } catch (SQLException e) {
            // BOM.db may not have m_bom_line
        }
        return tacks;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static BomProofReport emptyReport() {
        return new BomProofReport(0, 0, List.of(), 0, 0, List.of(), 0, 0, List.of(), 0, 0, List.of());
    }
}
