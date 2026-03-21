package com.bim.eyes.compare;

import java.sql.*;
import java.util.*;

/**
 * BOM-traced Rosetta Stone proof: match elements by identity (element_name),
 * then compare shape AND position.
 * // Implementing EYES_SRS.md §5.2 — Witness: W-BOM-TRACED
 */
public final class BomTracedComparator {

    private BomTracedComparator() {}

    /** Per-element BOM-traced comparison result. */
    public record BomTracedElement(
        String elementRef, String ifcClass,
        double extCx, double extCy, double extCz,
        double cmpCx, double cmpCy, double cmpCz,
        double shapeDelta, double posDist,
        boolean shapeOK, boolean posOK
    ) {}

    /** Summary of BOM-traced proof. */
    public record BomTracedResult(
        int extractedCount, int compiledCount, int paired,
        int shapeOK, int posOK, int bothOK,
        List<BomTracedElement> failures
    ) {
        public double shapeRate() { return paired > 0 ? 100.0 * shapeOK / paired : 0; }
        public double posRate()   { return paired > 0 ? 100.0 * posOK / paired : 0; }
        public double bothRate()  { return paired > 0 ? 100.0 * bothOK / paired : 0; }
    }

    /**
     * BOM-traced proof: match by element_name, compare shape AND position.
     */
    public static BomTracedResult proveBomTraced(
            String extractedDbPath, String outputDbPath, double epsilon, double posTolerance) {

        Map<String, List<double[]>> extByName = new LinkedHashMap<>();
        int extCount = 0;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + extractedDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT em.element_name, em.ifc_class,
                        r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
                 """)) {
            while (rs.next()) {
                extCount++;
                String rawName = rs.getString("element_name");
                String baseName = stripIfcSuffix(rawName);
                double minX = rs.getDouble("minX"), maxX = rs.getDouble("maxX");
                double minY = rs.getDouble("minY"), maxY = rs.getDouble("maxY");
                double minZ = rs.getDouble("minZ"), maxZ = rs.getDouble("maxZ");
                double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
                double w = (maxX - minX) * 1000, d = (maxY - minY) * 1000, h = (maxZ - minZ) * 1000;
                double[] sorted = {w, d, h}; Arrays.sort(sorted);
                double L = sorted[2];
                double plan = L > 0.01 ? sorted[0] / L : 0;
                double elon = L > 0.01 ? sorted[1] / L : 0;
                double sqr  = sorted[1] > 0.01 ? sorted[0] / sorted[1] : 0;
                extByName.computeIfAbsent(baseName, k -> new ArrayList<>())
                    .add(new double[]{cx, cy, cz, plan, elon, sqr});
            }
        } catch (SQLException ex) {
            throw new RuntimeException("BomTraced: cannot read " + extractedDbPath, ex);
        }

        int cmpCount = 0, paired = 0, shapeOK = 0, posOK = 0, bothOK = 0;
        List<BomTracedElement> failures = new ArrayList<>();
        Map<String, Integer> consumedOrdinal = new HashMap<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT em.element_name, em.ifc_class,
                        r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
                 """)) {
            while (rs.next()) {
                cmpCount++;
                String cmpName = rs.getString("element_name");
                String ifcClass = rs.getString("ifc_class");
                double minX = rs.getDouble("minX"), maxX = rs.getDouble("maxX");
                double minY = rs.getDouble("minY"), maxY = rs.getDouble("maxY");
                double minZ = rs.getDouble("minZ"), maxZ = rs.getDouble("maxZ");
                double cmpCx = (minX + maxX) / 2, cmpCy = (minY + maxY) / 2, cmpCz = (minZ + maxZ) / 2;
                double w = (maxX - minX) * 1000, d = (maxY - minY) * 1000, h = (maxZ - minZ) * 1000;
                double[] sorted = {w, d, h}; Arrays.sort(sorted);
                double L = sorted[2];
                double cPlan = L > 0.01 ? sorted[0] / L : 0;
                double cElon = L > 0.01 ? sorted[1] / L : 0;
                double cSqr  = sorted[1] > 0.01 ? sorted[0] / sorted[1] : 0;

                List<double[]> extList = extByName.get(cmpName);
                if (extList == null) continue;

                int ordinal = consumedOrdinal.getOrDefault(cmpName, 0);
                if (ordinal >= extList.size()) continue;

                double[] best = null;
                double bestDist = Double.MAX_VALUE;
                int bestIdx = ordinal;
                for (int i = ordinal; i < extList.size(); i++) {
                    double[] ext = extList.get(i);
                    double dd = Math.sqrt((ext[0]-cmpCx)*(ext[0]-cmpCx)
                        + (ext[1]-cmpCy)*(ext[1]-cmpCy) + (ext[2]-cmpCz)*(ext[2]-cmpCz));
                    if (dd < bestDist) { bestDist = dd; best = ext; bestIdx = i; }
                }
                if (best == null) continue;
                if (bestIdx != ordinal) {
                    double[] tmp = extList.get(ordinal);
                    extList.set(ordinal, best);
                    extList.set(bestIdx, tmp);
                }
                consumedOrdinal.put(cmpName, ordinal + 1);

                paired++;
                double dp = Math.abs(best[3] - cPlan);
                double de = Math.abs(best[4] - cElon);
                double ds = Math.abs(best[5] - cSqr);
                double shapeDelta = Math.max(dp, Math.max(de, ds));
                boolean sOK = shapeDelta <= epsilon;
                boolean pOK = bestDist <= posTolerance;

                if (sOK) shapeOK++;
                if (pOK) posOK++;
                if (sOK && pOK) bothOK++;

                if (!sOK || !pOK) {
                    failures.add(new BomTracedElement(cmpName, ifcClass,
                        best[0], best[1], best[2], cmpCx, cmpCy, cmpCz,
                        shapeDelta, bestDist, sOK, pOK));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("BomTraced: cannot read " + outputDbPath, ex);
        }

        return new BomTracedResult(extCount, cmpCount, paired, shapeOK, posOK, bothOK, failures);
    }

    /** Strip IFC entity ID suffix from element_name (e.g., "Wall:Type:286349" → "Wall:Type"). */
    private static String stripIfcSuffix(String name) {
        if (name == null) return "";
        String stripped = name;
        while (stripped.length() > 0) {
            int lastColon = stripped.lastIndexOf(':');
            if (lastColon < 0) break;
            String suffix = stripped.substring(lastColon + 1);
            if (suffix.matches("\\d+")) {
                stripped = stripped.substring(0, lastColon);
            } else {
                break;
            }
        }
        return stripped;
    }
}
