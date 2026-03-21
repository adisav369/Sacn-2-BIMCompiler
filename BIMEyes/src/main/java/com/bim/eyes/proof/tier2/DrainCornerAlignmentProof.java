package com.bim.eyes.proof.tier2;

import com.bim.eyes.proof.ProofResult;
import java.sql.*;
import java.util.*;

/** P23: Drain pipe segments share corner points. */
public final class DrainCornerAlignmentProof {
    private DrainCornerAlignmentProof() {}

    public static List<ProofResult> prove(Connection conn) {
        List<ProofResult> results = new ArrayList<>();
        List<String> guids = new ArrayList<>();
        List<double[]> bboxes = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT em.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta em
                 JOIN elements_rtree r ON em.id = r.id
                 WHERE em.ifc_class = 'IfcFlowSegment'
                 ORDER BY r.minZ, r.minX, r.minY
                 """)) {
            while (rs.next()) {
                guids.add(rs.getString("guid"));
                bboxes.add(new double[]{
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                });
            }
        } catch (SQLException e) {
            results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                ProofResult.Status.SKIPPED, null, "DB read failed: " + e.getMessage(), 0));
            return results;
        }

        if (guids.size() < 2) {
            results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                ProofResult.Status.SKIPPED, null,
                guids.isEmpty() ? "no IfcFlowSegment elements" : "only 1 drain segment", 0));
            return results;
        }

        double cornerTol = 0.005;
        double zTol = 0.050;

        for (int i = 0; i < bboxes.size(); i++) {
            double[] a = bboxes.get(i);
            double aW = a[1] - a[0], aD = a[3] - a[2];
            boolean aHoriz = aW >= aD;
            double[][] aCorn = {{a[0], a[2]}, {a[1], a[2]}, {a[0], a[3]}, {a[1], a[3]}};

            double minGap = Double.MAX_VALUE;
            for (int j = 0; j < bboxes.size(); j++) {
                if (i == j) continue;
                double[] b = bboxes.get(j);
                if (Math.abs(a[4] - b[4]) > zTol) continue;
                double bW = b[1] - b[0], bD = b[3] - b[2];
                boolean bHoriz = bW >= bD;
                if (aHoriz == bHoriz) continue;
                double[][] bCorn = {{b[0], b[2]}, {b[1], b[2]}, {b[0], b[3]}, {b[1], b[3]}};
                for (double[] ca : aCorn) {
                    for (double[] cb : bCorn) {
                        double d = Math.hypot(ca[0] - cb[0], ca[1] - cb[1]);
                        if (d < minGap) minGap = d;
                    }
                }
            }

            if (minGap == Double.MAX_VALUE) {
                results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                    ProofResult.Status.SKIPPED, guids.get(i),
                    "no orthogonal neighbor at same Z", 0));
            } else if (minGap <= cornerTol) {
                results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                    ProofResult.Status.PROVEN, guids.get(i),
                    String.format("corner gap %.1fmm", minGap * 1000), minGap));
            } else {
                results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                    ProofResult.Status.VIOLATED, guids.get(i),
                    String.format("corner gap %.1fmm exceeds 5mm tolerance", minGap * 1000),
                    minGap));
            }
        }
        return results;
    }
}
