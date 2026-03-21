package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Analyses CLUSTER verb groups to discover if validation rules (M1-M17)
 * describe the actual spatial patterns. Computes nearest-neighbour distances,
 * step regularity, and grid/route/frame fit metrics.
 *
 * <p>The validation rules ARE the pattern — this tool confirms it from data.
 *
 * @since S51b — Rule-driven pattern discovery
 */
public class ClusterPatternAnalyser {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ClusterPatternAnalyser <bom.db>");
            System.exit(1);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            List<ClusterGroup> groups = readClusterGroups(conn);

            System.out.printf("%n  ── Pattern Analysis: Validation Rules vs Data (%d groups) ──%n%n", groups.size());

            for (ClusterGroup g : groups) {
                if (g.qty < 10) continue;  // skip tiny groups

                double[][] offsets = ClusterReclassifier.parseClusterOffsets(g.verbRef);
                if (offsets == null) continue;

                analyseGroup(g, offsets);
            }
        }
    }

    private static void analyseGroup(ClusterGroup g, double[][] offsets) {
        // Compute NN distances (XY only — ignore Z for MEP)
        double[] nnDists = new double[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            double minDist = Double.MAX_VALUE;
            for (int j = 0; j < offsets.length; j++) {
                if (i == j) continue;
                double dx = offsets[i][0] - offsets[j][0];
                double dy = offsets[i][1] - offsets[j][1];
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < minDist) minDist = dist;
            }
            nnDists[i] = minDist;
        }
        Arrays.sort(nnDists);

        // Compute unique X, Y, Z positions (1mm quantization)
        Set<Long> uxSet = new TreeSet<>(), uySet = new TreeSet<>(), uzSet = new TreeSet<>();
        for (double[] o : offsets) {
            uxSet.add(Math.round(o[0] * 1000));
            uySet.add(Math.round(o[1] * 1000));
            uzSet.add(Math.round(o[2] * 1000));
        }

        int nX = uxSet.size(), nY = uySet.size(), nZ = uzSet.size();
        boolean isGrid = nX * nY == offsets.length && nZ == 1;

        // Step regularity: compute X and Y step deltas
        double[] xSteps = computeSteps(uxSet);
        double[] ySteps = computeSteps(uySet);
        double xStepCV = coefficientOfVariation(xSteps);
        double yStepCV = coefficientOfVariation(ySteps);

        // Dimension variance (ASI)
        Set<String> dimVariants = new TreeSet<>();
        for (double[] o : offsets) {
            dimVariants.add(String.format("%.0f×%.0f×%.0f",
                o[3] * 1000, o[4] * 1000, o[5] * 1000));
        }

        // Report
        String shortProduct = g.productId.length() > 45
            ? g.productId.substring(0, 45) + ".." : g.productId;
        System.out.printf("  %-47s %s (qty=%d)%n", shortProduct, g.storey, g.qty);
        System.out.printf("    NN: min=%.2fm  median=%.2fm  max=%.2fm%n",
            nnDists[0], nnDists[offsets.length / 2], nnDists[offsets.length - 1]);
        System.out.printf("    Grid: nX=%d nY=%d nZ=%d %s%n",
            nX, nY, nZ, isGrid ? "COMPLETE" : "sparse");

        if (xSteps.length > 0) {
            System.out.printf("    X-step: median=%.0fmm CV=%.1f%% (%d unique)%n",
                median(xSteps), xStepCV * 100, xSteps.length);
        }
        if (ySteps.length > 0) {
            System.out.printf("    Y-step: median=%.0fmm CV=%.1f%% (%d unique)%n",
                median(ySteps), yStepCV * 100, ySteps.length);
        }

        System.out.printf("    ASI: %d size variants%n", dimVariants.size());

        // Pattern verdict
        String verdict;
        if (isGrid && xStepCV < 0.05 && yStepCV < 0.05)
            verdict = "TILE (uniform grid)";
        else if (isGrid && (xStepCV < 0.20 || yStepCV < 0.20))
            verdict = "FRAME (irregular grid)";
        else if ((nX == 1 || nY == 1) && nZ == 1)
            verdict = "ROUTE (1D chain)";
        else if (nZ == 1 && nnDists[offsets.length / 2] < 5.0)
            verdict = "ZONE (clustered, rule-governed)";
        else
            verdict = "MIXED (multi-Z or irregular)";

        System.out.printf("    → %s%n%n", verdict);
    }

    private static double[] computeSteps(Set<Long> positions) {
        if (positions.size() < 2) return new double[0];
        Long[] sorted = positions.toArray(new Long[0]);
        double[] steps = new double[sorted.length - 1];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = sorted[i + 1] - sorted[i];  // already in mm
        }
        return steps;
    }

    private static double coefficientOfVariation(double[] values) {
        if (values.length < 2) return 0;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        if (mean == 0) return 0;
        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        variance /= values.length;
        return Math.sqrt(variance) / mean;
    }

    private static double median(double[] arr) {
        double[] sorted = arr.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    record ClusterGroup(String bomId, int seq, String productId,
                        String storey, int qty, String verbRef) {}

    private static List<ClusterGroup> readClusterGroups(Connection conn) throws SQLException {
        List<ClusterGroup> groups = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT bom_id, sequence, child_product_id, storey, qty, verb_ref
                 FROM m_bom_line
                 WHERE verb_ref LIKE 'CLUSTER:%'
                 ORDER BY qty DESC
                 """)) {
            while (rs.next()) {
                groups.add(new ClusterGroup(
                    rs.getString(1), rs.getInt(2), rs.getString(3),
                    rs.getString(4), rs.getInt(5), rs.getString(6)));
            }
        }
        return groups;
    }
}
