package com.bim.compiler.validation;

import java.sql.*;
import java.util.*;

/**
 * Per-element spatial diff between two output DBs.
 *
 * <p>When G3-DIGEST fails, SpatialDigest reports only per-class counts — not which
 * element moved or by how much. This class provides the diagnostic detail:
 * per-element coordinate deltas with tolerance classification.
 *
 * <p><b>Algorithm:</b> Query both DBs with the same sort order as SpatialDigest
 * (ifc_class, ROUND(minX*1000), ROUND(minY*1000), ROUND(minZ*1000)). Match elements
 * by positional sort order within each ifc_class. Compute per-axis delta in mm.
 *
 * <p><b>Tolerance bands:</b>
 * <ul>
 *   <li>EXACT: all axes ≤ 1mm</li>
 *   <li>DRIFT: any axis &gt; 1mm and ≤ 50mm</li>
 *   <li>SHIFT: any axis &gt; 50mm</li>
 *   <li>MISSING/EXTRA: count mismatch per class</li>
 * </ul>
 *
 * @see SpatialDigest — the aggregate hash that this complements
 */
public class SpatialDiff {

    public enum Band { EXACT, DRIFT, SHIFT, MISSING, EXTRA }

    public record ElementDelta(
        String ifcClass,
        int indexInClass,
        double deltaMinX_mm, double deltaMaxX_mm,
        double deltaMinY_mm, double deltaMaxY_mm,
        double deltaMinZ_mm, double deltaMaxZ_mm,
        Band band
    ) {
        public double maxDelta() {
            return Math.max(Math.abs(deltaMinX_mm),
                   Math.max(Math.abs(deltaMaxX_mm),
                   Math.max(Math.abs(deltaMinY_mm),
                   Math.max(Math.abs(deltaMaxY_mm),
                   Math.max(Math.abs(deltaMinZ_mm),
                            Math.abs(deltaMaxZ_mm))))));
        }
    }

    public record DiffReport(
        List<ElementDelta> deltas,
        Map<String, int[]> classCounts,  // [refCount, outCount]
        int exact, int drift, int shift, int missing, int extra
    ) {
        public boolean isClean() {
            return drift == 0 && shift == 0 && missing == 0 && extra == 0;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("SpatialDiff: %d exact, %d drift, %d shift, %d missing, %d extra%n",
                exact, drift, shift, missing, extra));
            if (isClean()) {
                sb.append("All elements match within 1mm.\n");
                return sb.toString();
            }
            // Show non-EXACT deltas, sorted by max delta descending
            List<ElementDelta> issues = deltas.stream()
                .filter(d -> d.band != Band.EXACT)
                .sorted((a, b) -> Double.compare(b.maxDelta(), a.maxDelta()))
                .toList();
            int shown = 0;
            for (ElementDelta d : issues) {
                if (shown >= 20) {
                    sb.append(String.format("  ... and %d more%n", issues.size() - 20));
                    break;
                }
                sb.append(String.format("  %s [%s] #%d: dX=(%.1f,%.1f) dY=(%.1f,%.1f) dZ=(%.1f,%.1f) max=%.1fmm%n",
                    d.band, d.ifcClass, d.indexInClass,
                    d.deltaMinX_mm, d.deltaMaxX_mm,
                    d.deltaMinY_mm, d.deltaMaxY_mm,
                    d.deltaMinZ_mm, d.deltaMaxZ_mm,
                    d.maxDelta()));
                shown++;
            }
            // Class count mismatches
            for (var e : classCounts.entrySet()) {
                int rc = e.getValue()[0], oc = e.getValue()[1];
                if (rc != oc) {
                    sb.append(String.format("  CLASS %s: ref=%d out=%d (delta=%d)%n",
                        e.getKey(), rc, oc, oc - rc));
                }
            }
            return sb.toString();
        }
    }

    /**
     * Compare two output DBs element-by-element.
     * CP-1: attempts identity-based matching (guid↔element_ref) first.
     * Falls back to position-based matching for DBs without identity data.
     *
     * @param refDbPath  reference DB path
     * @param outDbPath  output DB path
     * @return DiffReport with per-element deltas
     */
    public static DiffReport diff(String refDbPath, String outDbPath) {
        // CP-1: Try identity-based matching first (guid↔element_ref)
        Map<String, double[]> refById = loadElementsByIdentity(refDbPath, "guid");
        Map<String, double[]> outById = loadElementsByIdentity(outDbPath, "element_ref");

        // Only use identity matching if IDs actually overlap between ref and output.
        // SH/DX have guid in ref but name-based element_ref in output → no overlap → fallback.
        if (!refById.isEmpty() && !outById.isEmpty()) {
            long overlap = refById.keySet().stream().filter(outById::containsKey).count();
            if (overlap > refById.size() / 4) {  // >25% overlap = identity data is aligned
                return diffByIdentity(refById, outById, refDbPath, outDbPath);
            }
        }

        // Fallback: position-based matching (SH/DX, old DBs without guids)
        return diffByPosition(refDbPath, outDbPath);
    }

    /**
     * CP-1: Hybrid diff — identity matching first, position fallback for remainder.
     * Elements matched by guid↔element_ref are compared directly.
     * Unmatched elements (TILE/ROUTE/FRAME verbs without guids) fall back to
     * position-based matching within each ifc_class.
     */
    private static DiffReport diffByIdentity(
            Map<String, double[]> refById, Map<String, double[]> outById,
            String refDbPath, String outDbPath) {

        Map<String, String> refClassById = loadClassByIdentity(refDbPath, "guid");
        Map<String, String> outClassById = loadClassByIdentity(outDbPath, "element_ref");

        List<ElementDelta> deltas = new ArrayList<>();
        Map<String, int[]> classCounts = new TreeMap<>();
        int exact = 0, drift = 0, shift = 0, missing = 0, extra = 0;

        // Phase 1: identity-matched elements
        Set<String> matchedRefIds = new HashSet<>();
        Set<String> matchedOutIds = new HashSet<>();

        for (String id : refById.keySet()) {
            double[] o = outById.get(id);
            if (o != null) {
                double[] r = refById.get(id);
                String cls = refClassById.getOrDefault(id, "Unknown");
                classCounts.computeIfAbsent(cls, k -> new int[]{0, 0});
                classCounts.get(cls)[0]++;
                classCounts.get(cls)[1]++;

                double dMinX = (o[0] - r[0]) * 1000;
                double dMaxX = (o[1] - r[1]) * 1000;
                double dMinY = (o[2] - r[2]) * 1000;
                double dMaxY = (o[3] - r[3]) * 1000;
                double dMinZ = (o[4] - r[4]) * 1000;
                double dMaxZ = (o[5] - r[5]) * 1000;
                Band band = classify(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ);
                deltas.add(new ElementDelta(cls, 0, dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, band));
                switch (band) {
                    case EXACT -> exact++;
                    case DRIFT -> drift++;
                    case SHIFT -> shift++;
                    default -> {}
                }
                matchedRefIds.add(id);
                matchedOutIds.add(id);
            }
        }

        // Phase 2: position-based fallback for unmatched elements
        // Group unmatched ref elements by class+coords (position sort order)
        Map<String, List<double[]>> unmatchedRef = new TreeMap<>();
        for (var e : refById.entrySet()) {
            if (!matchedRefIds.contains(e.getKey())) {
                String cls = refClassById.getOrDefault(e.getKey(), "Unknown");
                unmatchedRef.computeIfAbsent(cls, k -> new ArrayList<>()).add(e.getValue());
            }
        }
        Map<String, List<double[]>> unmatchedOut = new TreeMap<>();
        for (var e : outById.entrySet()) {
            if (!matchedOutIds.contains(e.getKey())) {
                String cls = outClassById.getOrDefault(e.getKey(), "Unknown");
                unmatchedOut.computeIfAbsent(cls, k -> new ArrayList<>()).add(e.getValue());
            }
        }

        // Sort unmatched by position within each class, then pair
        Set<String> remainClasses = new TreeSet<>();
        remainClasses.addAll(unmatchedRef.keySet());
        remainClasses.addAll(unmatchedOut.keySet());

        for (String cls : remainClasses) {
            List<double[]> refList = unmatchedRef.getOrDefault(cls, List.of());
            List<double[]> outList = unmatchedOut.getOrDefault(cls, List.of());

            // Sort by position (same order as loadElements)
            refList.sort(SpatialDiff::compareCoords);
            outList.sort(SpatialDiff::compareCoords);

            classCounts.computeIfAbsent(cls, k -> new int[]{0, 0});
            classCounts.get(cls)[0] += refList.size();
            classCounts.get(cls)[1] += outList.size();

            int paired = Math.min(refList.size(), outList.size());
            for (int i = 0; i < paired; i++) {
                double[] r = refList.get(i);
                double[] o = outList.get(i);
                double dMinX = (o[0] - r[0]) * 1000;
                double dMaxX = (o[1] - r[1]) * 1000;
                double dMinY = (o[2] - r[2]) * 1000;
                double dMaxY = (o[3] - r[3]) * 1000;
                double dMinZ = (o[4] - r[4]) * 1000;
                double dMaxZ = (o[5] - r[5]) * 1000;
                Band band = classify(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ);
                deltas.add(new ElementDelta(cls, i, dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, band));
                switch (band) {
                    case EXACT -> exact++;
                    case DRIFT -> drift++;
                    case SHIFT -> shift++;
                    default -> {}
                }
            }
            for (int i = paired; i < refList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.MISSING));
                missing++;
            }
            for (int i = paired; i < outList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.EXTRA));
                extra++;
            }
        }

        return new DiffReport(deltas, classCounts, exact, drift, shift, missing, extra);
    }

    /** Sort comparator for position-based fallback (10mm bins + dimension tiebreakers). */
    private static int compareCoords(double[] a, double[] b) {
        // 10mm position bins
        int cmp = Long.compare(Math.round(a[0] * 100), Math.round(b[0] * 100));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round(a[2] * 100), Math.round(b[2] * 100));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round(a[4] * 100), Math.round(b[4] * 100));
        if (cmp != 0) return cmp;
        // Dimension tiebreakers at 1mm
        cmp = Long.compare(Math.round((a[1]-a[0]) * 1000), Math.round((b[1]-b[0]) * 1000));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round((a[3]-a[2]) * 1000), Math.round((b[3]-b[2]) * 1000));
        if (cmp != 0) return cmp;
        return Long.compare(Math.round((a[5]-a[4]) * 1000), Math.round((b[5]-b[4]) * 1000));
    }

    /** Original position-based diff (backward compat for SH/DX). */
    private static DiffReport diffByPosition(String refDbPath, String outDbPath) {
        Map<String, List<double[]>> refElements = loadElements(refDbPath);
        Map<String, List<double[]>> outElements = loadElements(outDbPath);

        Set<String> allClasses = new TreeSet<>();
        allClasses.addAll(refElements.keySet());
        allClasses.addAll(outElements.keySet());

        List<ElementDelta> deltas = new ArrayList<>();
        Map<String, int[]> classCounts = new TreeMap<>();
        int exact = 0, drift = 0, shift = 0, missing = 0, extra = 0;

        for (String cls : allClasses) {
            List<double[]> refList = refElements.getOrDefault(cls, List.of());
            List<double[]> outList = outElements.getOrDefault(cls, List.of());
            classCounts.put(cls, new int[]{ refList.size(), outList.size() });

            int paired = Math.min(refList.size(), outList.size());
            for (int i = 0; i < paired; i++) {
                double[] r = refList.get(i);
                double[] o = outList.get(i);
                double dMinX = (o[0] - r[0]) * 1000;
                double dMaxX = (o[1] - r[1]) * 1000;
                double dMinY = (o[2] - r[2]) * 1000;
                double dMaxY = (o[3] - r[3]) * 1000;
                double dMinZ = (o[4] - r[4]) * 1000;
                double dMaxZ = (o[5] - r[5]) * 1000;

                Band band = classify(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ);
                deltas.add(new ElementDelta(cls, i, dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, band));
                switch (band) {
                    case EXACT -> exact++;
                    case DRIFT -> drift++;
                    case SHIFT -> shift++;
                    default -> {}
                }
            }
            // Missing elements (in ref but not in output)
            for (int i = paired; i < refList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.MISSING));
                missing++;
            }
            // Extra elements (in output but not in ref)
            for (int i = paired; i < outList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.EXTRA));
                extra++;
            }
        }

        return new DiffReport(deltas, classCounts, exact, drift, shift, missing, extra);
    }

    private static Band classify(double... deltas_mm) {
        double max = 0;
        for (double d : deltas_mm) max = Math.max(max, Math.abs(d));
        if (max <= 1.0) return Band.EXACT;
        if (max <= 50.0) return Band.DRIFT;
        return Band.SHIFT;
    }

    /**
     * Load elements from a DB, grouped by ifc_class, sorted in SpatialDigest order.
     * Returns map of ifc_class → list of [minX, maxX, minY, maxY, minZ, maxZ].
     *
     * <p>Sort uses 10mm position bins (ROUND(x*100)) to absorb floating-point
     * jitter from BOM coordinate accumulation (up to ~5μm). Dimension tiebreakers
     * at 1mm precision (ROUND(W*1000)) disambiguate elements within the same bin.
     * This prevents non-deterministic pairing for floor slab layers at the same
     * XY corner, or walls whose reconstructed position jitters by a few μm.
     */
    /**
     * CP-1: Load elements keyed by identity column (guid or element_ref).
     * Returns empty map if the identity column doesn't exist or has no data.
     */
    private static Map<String, double[]> loadElementsByIdentity(String dbPath, String idColumn) {
        // Sanitise column name (guid or element_ref only)
        if (!"guid".equals(idColumn) && !"element_ref".equals(idColumn)) {
            return Collections.emptyMap(); // guard: only allowed identity columns
        }
        String sql = String.format("""
            SELECT em.%s, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.%s IS NOT NULL AND em.%s != ''
            """, idColumn, idColumn, idColumn);

        Map<String, double[]> result = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id == null || id.isEmpty()) continue;
                double[] coords = {
                    rs.getDouble(2), rs.getDouble(3),
                    rs.getDouble(4), rs.getDouble(5),
                    rs.getDouble(6), rs.getDouble(7)
                };
                result.putIfAbsent(id, coords);
            }
        } catch (SQLException e) {
            // Column may not exist (old DB schema) — return empty → fallback to position
            return Collections.emptyMap();
        }
        return result;
    }

    /** CP-1: Load ifc_class keyed by identity column. */
    private static Map<String, String> loadClassByIdentity(String dbPath, String idColumn) {
        if (!"guid".equals(idColumn) && !"element_ref".equals(idColumn)) {
            return Collections.emptyMap(); // guard: only allowed identity columns
        }
        String sql = String.format("""
            SELECT em.%s, em.ifc_class
            FROM elements_meta em
            WHERE em.%s IS NOT NULL AND em.%s != ''
            """, idColumn, idColumn, idColumn);

        Map<String, String> result = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.putIfAbsent(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            // Column may not exist (old DB schema) — empty triggers position fallback
            return Collections.emptyMap();
        }
        return result;
    }

    private static Map<String, List<double[]>> loadElements(String dbPath) {
        String sql = """
            SELECT em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            ORDER BY em.ifc_class,
                     ROUND(r.minX * 100), ROUND(r.minY * 100), ROUND(r.minZ * 100),
                     ROUND((r.maxX - r.minX) * 1000), ROUND((r.maxY - r.minY) * 1000),
                     ROUND((r.maxZ - r.minZ) * 1000),
                     ROUND(r.maxX * 1000), ROUND(r.maxY * 1000), ROUND(r.maxZ * 1000)
            """;

        Map<String, List<double[]>> result = new TreeMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String cls = rs.getString(1);
                double[] coords = {
                    rs.getDouble(2), rs.getDouble(3),
                    rs.getDouble(4), rs.getDouble(5),
                    rs.getDouble(6), rs.getDouble(7)
                };
                result.computeIfAbsent(cls, k -> new ArrayList<>()).add(coords);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SpatialDiff failed on " + dbPath + ": " + e.getMessage(), e);
        }
        return result;
    }
}
