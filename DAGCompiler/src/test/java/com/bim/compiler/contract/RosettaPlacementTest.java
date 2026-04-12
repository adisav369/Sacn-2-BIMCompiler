package com.bim.compiler.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * G8 — Rosetta Placement Gate (Tier 2 from TheRosettaStoneStrategy.txt).
 *
 * <p>Tier 1 (X5/X6) proves non-bunching — elements are distinct and non-overlapping.
 * Tier 2 proves absolute positional correctness: each compiled element is within
 * THRESHOLD_MM of the nearest reference element of the same furnishing class in the
 * extracted reference DB.
 *
 * <p>The extracted reference DBs already carry correct world positions — extraction IS the
 * ground truth. The compiled output must reproduce them. Failures ARE the bug list.
 *
 * <p>Matching strategy: nearest-neighbour by 3D centroid distance across all reference
 * IfcFurnishingElement entries. Name formats differ between reference ("M_Bed-Standard:...")
 * and compiled ("Furniture:bed_2031x1981x500mm") so matching is purely positional.
 *
 * <p>THRESHOLD: 500 mm. If the nearest reference element is farther than 500 mm the
 * compiled element is in the wrong room or at the wrong position. Those failures identify
 * calibration bugs in ad_room_boundary.
 */
@DisplayName("G8 — Rosetta Placement Gate: compiled vs reference centroid distance")
class RosettaPlacementTest {

    private static final double THRESHOLD_MM = 500.0;   // 0.5 m
    private static final double THRESHOLD_M  = THRESHOLD_MM / 1000.0;

    // Compiled output DBs (written by BuildingRegistryTest / EdgeVertexTest during mvn test)
    private static final String COMPILED_SH  = "DAGCompiler/lib/output/samplehouse.db";
    private static final String COMPILED_DX  = "DAGCompiler/lib/output/duplex.db";

    // Reference extracted DBs — immutable ground truth
    private static final String REFERENCE_SH = "DAGCompiler/lib/input/SampleHouse_extracted.db";
    private static final String REFERENCE_DX = "DAGCompiler/lib/input/Duplex_extracted.db";

    // -----------------------------------------------------------------------
    // Record types
    // -----------------------------------------------------------------------

    private record Centroid(String name, double cx, double cy, double cz) {}

    // -----------------------------------------------------------------------
    // SH gate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("G8-SH: SampleHouse compiled furniture within 500mm of reference")
    void g8_sampleHouse() {
        runGate("SH", COMPILED_SH, REFERENCE_SH);
    }

    // -----------------------------------------------------------------------
    // DX gate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("G8-DX: Duplex compiled furniture within 500mm of reference")
    void g8_duplex() {
        runGate("DX", COMPILED_DX, REFERENCE_DX);
    }

    // -----------------------------------------------------------------------
    // Core comparison
    // -----------------------------------------------------------------------

    private void runGate(String tag, String compiledPath, String referencePath) {
        List<Centroid> compiled;
        List<Centroid> reference;

        try {
            compiled  = loadCentroids(compiledPath,  "IfcFurniture", "IfcFurnishingElement");
            reference = loadCentroids(referencePath, "IfcFurnishingElement");
        } catch (SQLException e) {
            fail("[G8-" + tag + "] DB load failed: " + e.getMessage());
            return;
        }

        if (compiled.isEmpty()) {
            fail("[G8-" + tag + "] No compiled furniture found in " + compiledPath);
            return;
        }
        if (reference.isEmpty()) {
            fail("[G8-" + tag + "] No reference furniture found in " + referencePath);
            return;
        }

        List<String> failures = new ArrayList<>();
        List<String> report   = new ArrayList<>();

        for (Centroid c : compiled) {
            double bestDist = Double.MAX_VALUE;
            Centroid bestRef = null;

            for (Centroid r : reference) {
                double d = dist3(c, r);
                if (d < bestDist) {
                    bestDist = d;
                    bestRef  = r;
                }
            }

            double distMm = bestDist * 1000.0;
            String line = String.format("[G8-%s] compiled='%s' (%.3f,%.3f,%.3f) → nearest='%s' (%.3f,%.3f,%.3f) dist=%.0fmm",
                tag, c.name(), c.cx(), c.cy(), c.cz(),
                bestRef != null ? bestRef.name() : "?",
                bestRef != null ? bestRef.cx() : 0,
                bestRef != null ? bestRef.cy() : 0,
                bestRef != null ? bestRef.cz() : 0,
                distMm);

            report.add(line);
            System.out.println(line);

            if (bestDist > THRESHOLD_M) {
                failures.add(line);
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[G8-%s] %d/%d compiled elements exceed %.0fmm threshold:%n",
                tag, failures.size(), compiled.size(), THRESHOLD_MM));
            for (String f : failures) sb.append("  ").append(f).append('\n');
            fail(sb.toString());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static double dist3(Centroid a, Centroid b) {
        double dx = a.cx() - b.cx();
        double dy = a.cy() - b.cy();
        double dz = a.cz() - b.cz();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<Centroid> loadCentroids(String dbPath, String... classes)
            throws SQLException {
        String placeholders = String.join(",", Collections.nCopies(classes.length, "?"));
        String sql = String.format("""
            SELECT em.element_name,
                   (er.minX + er.maxX) / 2.0 AS cx,
                   (er.minY + er.maxY) / 2.0 AS cy,
                   (er.minZ + er.maxZ) / 2.0 AS cz
            FROM elements_rtree er
            JOIN elements_meta  em ON er.id = em.id
            WHERE em.ifc_class IN (%s)
            ORDER BY em.element_name
            """, placeholders);

        List<Centroid> result = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < classes.length; i++) {
                ps.setString(i + 1, classes[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Centroid(
                        rs.getString("element_name"),
                        rs.getDouble("cx"),
                        rs.getDouble("cy"),
                        rs.getDouble("cz")));
                }
            }
        }
        return result;
    }
}
