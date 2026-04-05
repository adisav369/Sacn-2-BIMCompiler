package com.bim.eyes.diff;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.shape.ShapeClassifier;

import java.sql.*;
import java.util.*;

/**
 * Per-element spatial diff between two output DBs.
 * // Implementing EYES_SRS.md §6 — moved from DAGCompiler
 */
public class SpatialDiff {

    public enum Band { EXACT, DRIFT, SHIFT, MISSING, EXTRA }

    public record ElementDelta(
        String ifcClass, int indexInClass,
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

        /**
         * Symmetric: min and max shifted by ~same amount on all axes (position error, size correct).
         * Asymmetric: min and max differ (AABB resized — wrong element paired, or real size error).
         * Threshold: 50mm tolerance for symmetry check.
         */
        public boolean isSymmetric() {
            double tol = EyesConstants.SPATIAL_DRIFT_MM;
            return Math.abs(deltaMinX_mm - deltaMaxX_mm) <= tol
                && Math.abs(deltaMinY_mm - deltaMaxY_mm) <= tol
                && Math.abs(deltaMinZ_mm - deltaMaxZ_mm) <= tol;
        }
    }

    public record DiffReport(
        List<ElementDelta> deltas, Map<String, int[]> classCounts,
        int exact, int drift, int shift, int missing, int extra
    ) {
        public boolean isClean() {
            return drift == 0 && shift == 0 && missing == 0 && extra == 0;
        }

        /**
         * Compute the modal (most common) shift vector across all SHIFT elements,
         * rounded to 1mm. Returns [dMinX, dMinY, dMinZ] in mm, or [0,0,0] if no shifts.
         */
        public double[] modalShift() {
            // Bucket shifts by rounded (dMinX, dMinY, dMinZ) — the LBD corner shift
            Map<String, int[]> freq = new HashMap<>();
            Map<String, double[]> values = new HashMap<>();
            for (ElementDelta d : deltas) {
                if (d.band == Band.SHIFT) {
                    String key = Math.round(d.deltaMinX_mm) + "," +
                                 Math.round(d.deltaMinY_mm) + "," +
                                 Math.round(d.deltaMinZ_mm);
                    freq.computeIfAbsent(key, k -> new int[]{0})[0]++;
                    values.putIfAbsent(key, new double[]{d.deltaMinX_mm, d.deltaMinY_mm, d.deltaMinZ_mm});
                }
            }
            if (freq.isEmpty()) return new double[]{0, 0, 0};
            String best = freq.entrySet().stream()
                .max(Map.Entry.comparingByValue((a, b) -> Integer.compare(a[0], b[0])))
                .map(Map.Entry::getKey).orElse("0,0,0");
            return values.getOrDefault(best, new double[]{0, 0, 0});
        }

        /**
         * Count of SHIFT elements whose shift deviates from the modal shift by more
         * than the given tolerance (mm). These are the real position errors, not
         * the expected global offset.
         */
        public int outlierCount(double toleranceMm) {
            double[] mode = modalShift();
            int count = 0;
            for (ElementDelta d : deltas) {
                if (d.band == Band.SHIFT && isOutlier(d, mode, toleranceMm)) count++;
            }
            return count;
        }

        /**
         * Return SHIFT elements that deviate from the modal shift beyond tolerance.
         */
        public List<ElementDelta> outliers(double toleranceMm) {
            double[] mode = modalShift();
            return deltas.stream()
                .filter(d -> d.band == Band.SHIFT && isOutlier(d, mode, toleranceMm))
                .toList();
        }

        /**
         * Per-class outlier counts: class → [total_shift, outlier_count].
         */
        public Map<String, int[]> outliersByClass(double toleranceMm) {
            double[] mode = modalShift();
            Map<String, int[]> result = new TreeMap<>();
            for (ElementDelta d : deltas) {
                if (d.band == Band.SHIFT) {
                    int[] counts = result.computeIfAbsent(d.ifcClass, k -> new int[]{0, 0});
                    counts[0]++;
                    if (isOutlier(d, mode, toleranceMm)) counts[1]++;
                }
            }
            return result;
        }

        private static boolean isOutlier(ElementDelta d, double[] mode, double toleranceMm) {
            return Math.abs(d.deltaMinX_mm - mode[0]) > toleranceMm
                || Math.abs(d.deltaMaxX_mm - mode[0]) > toleranceMm
                || Math.abs(d.deltaMinY_mm - mode[1]) > toleranceMm
                || Math.abs(d.deltaMaxY_mm - mode[1]) > toleranceMm
                || Math.abs(d.deltaMinZ_mm - mode[2]) > toleranceMm
                || Math.abs(d.deltaMaxZ_mm - mode[2]) > toleranceMm;
        }

        /**
         * Diagnosis report: classifies outliers as symmetric (position error) vs
         * asymmetric (size mismatch = wrong pairing), then emits a per-class
         * summary with actionable diagnosis.
         *
         * <p>Symmetric outlier → real compilation error (wrong offset in BOM walk).
         * Asymmetric outlier → likely measurement artifact (SpatialDiff mis-paired
         * elements of different sizes). Fix the pairing before investigating compilation.
         *
         * @return multi-line diagnosis string, or empty if no outliers
         */
        public String diagnosis(double toleranceMm) {
            List<ElementDelta> outs = outliers(toleranceMm);
            if (outs.isEmpty()) return "";

            int symmetric = 0, asymmetric = 0;
            // Per-class: [symmetric, asymmetric]
            Map<String, int[]> byClass = new TreeMap<>();
            for (ElementDelta d : outs) {
                int[] counts = byClass.computeIfAbsent(d.ifcClass, k -> new int[]{0, 0});
                if (d.isSymmetric()) { symmetric++; counts[0]++; }
                else { asymmetric++; counts[1]++; }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("DIAGNOSIS: %d outliers = %d symmetric (position error) + %d asymmetric (size mismatch)%n",
                outs.size(), symmetric, asymmetric));

            for (var e : byClass.entrySet()) {
                int sym = e.getValue()[0], asym = e.getValue()[1];
                int total = sym + asym;
                String hint;
                if (asym > sym) {
                    hint = "likely mis-paired by SpatialDiff (fix black box pairing first)";
                } else if (sym > 0 && asym == 0) {
                    hint = "real position error (trace through white box TACK LEAF)";
                } else {
                    hint = "mixed — fix pairing first, then re-evaluate";
                }
                sb.append(String.format("  %s: %d sym + %d asym → %s%n",
                    e.getKey(), sym, asym, hint));
            }

            return sb.toString();
        }

        /**
         * Format the diff report as a TSV string.
         * Columns: band, ifc_class, index, dMinX_mm, dMaxX_mm, dMinY_mm, dMaxY_mm, dMinZ_mm, dMaxZ_mm, max_mm
         */
        public String toTsv() {
            StringBuilder sb = new StringBuilder();
            sb.append("band\tifc_class\tindex\tdMinX_mm\tdMaxX_mm\tdMinY_mm\tdMaxY_mm\tdMinZ_mm\tdMaxZ_mm\tmax_mm\n");
            for (ElementDelta d : deltas) {
                sb.append(String.format("%s\t%s\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f%n",
                    d.band, d.ifcClass, d.indexInClass,
                    d.deltaMinX_mm, d.deltaMaxX_mm,
                    d.deltaMinY_mm, d.deltaMaxY_mm,
                    d.deltaMinZ_mm, d.deltaMaxZ_mm,
                    d.maxDelta()));
            }
            return sb.toString();
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("SpatialDiff: %d exact, %d drift, %d shift, %d missing, %d extra%n",
                exact, drift, shift, missing, extra));
            if (isClean()) {
                sb.append("All elements match within 1mm.\n");
                return sb.toString();
            }
            // Modal shift analysis — separate expected global offset from per-element errors
            double[] mode = modalShift();
            double outlierTol = EyesConstants.SPATIAL_DRIFT_MM;  // 50mm
            int outliers = outlierCount(outlierTol);
            sb.append(String.format("  Modal shift: dX=%.0f dY=%.0f dZ=%.0f mm (global offset)%n",
                mode[0], mode[1], mode[2]));
            if (outliers > 0) {
                sb.append(String.format("  OUTLIERS: %d elements deviate >%.0fmm from modal shift%n",
                    outliers, outlierTol));
                for (var e : outliersByClass(outlierTol).entrySet()) {
                    if (e.getValue()[1] > 0) {
                        sb.append(String.format("    %s: %d/%d shifted are outliers%n",
                            e.getKey(), e.getValue()[1], e.getValue()[0]));
                    }
                }
            }

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
     * Tries identity-based matching first, falls back to position-based.
     */
    public static DiffReport diff(String refDbPath, String outDbPath) {
        return diff(refDbPath, outDbPath, null);
    }

    /**
     * Compare two output DBs element-by-element, restricted to the given ifc_class filter.
     * If filter is null or empty, all classes are included.
     * // Implementing AUDIT_20260402.txt §3 — ARC/STC filter for TotalityContractTest
     *
     * @param ifcClassFilter set of ifc_class strings to include, or null for all
     */
    public static DiffReport diff(String refDbPath, String outDbPath, Set<String> ifcClassFilter) {
        Map<String, double[]> refById = loadElementsByIdentity(refDbPath, "guid", ifcClassFilter);
        Map<String, double[]> outById = loadElementsByIdentity(outDbPath, "element_ref", ifcClassFilter);

        if (!refById.isEmpty() && !outById.isEmpty()) {
            long overlap = refById.keySet().stream().filter(outById::containsKey).count();
            // S147 §4.3: threshold against the SMALLER set — output may be a compiled
            // subset of the full reference. 113/215 output = 53% is a strong signal;
            // 113/1119 ref = 10% would incorrectly reject identity matching.
            long smaller = Math.min(refById.size(), outById.size());
            if (overlap > smaller / 4) {
                return diffByIdentity(refById, outById, refDbPath, outDbPath);
            }
        }

        return diffByPosition(refDbPath, outDbPath, ifcClassFilter);
    }

    private static DiffReport diffByIdentity(
            Map<String, double[]> refById, Map<String, double[]> outById,
            String refDbPath, String outDbPath) {

        Map<String, String> refClassById = loadClassByIdentity(refDbPath, "guid");
        Map<String, String> outClassById = loadClassByIdentity(outDbPath, "element_ref");

        List<ElementDelta> deltas = new ArrayList<>();
        Map<String, int[]> classCounts = new TreeMap<>();
        int exact = 0, drift = 0, shift = 0, missing = 0, extra = 0;

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

                double dMinX = (o[0] - r[0]) * 1000, dMaxX = (o[1] - r[1]) * 1000;
                double dMinY = (o[2] - r[2]) * 1000, dMaxY = (o[3] - r[3]) * 1000;
                double dMinZ = (o[4] - r[4]) * 1000, dMaxZ = (o[5] - r[5]) * 1000;
                Band band = classifyForClass(cls, r, o);
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

        // Position-based fallback for unmatched
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

        Set<String> remainClasses = new TreeSet<>();
        remainClasses.addAll(unmatchedRef.keySet());
        remainClasses.addAll(unmatchedOut.keySet());

        for (String cls : remainClasses) {
            List<double[]> refList = new ArrayList<>(unmatchedRef.getOrDefault(cls, List.of()));
            List<double[]> outList = new ArrayList<>(unmatchedOut.getOrDefault(cls, List.of()));
            refList.sort(SpatialDiff::compareCoords);
            outList.sort(SpatialDiff::compareCoords);

            classCounts.computeIfAbsent(cls, k -> new int[]{0, 0});
            classCounts.get(cls)[0] += refList.size();
            classCounts.get(cls)[1] += outList.size();

            int paired = Math.min(refList.size(), outList.size());
            for (int i = 0; i < paired; i++) {
                double[] r = refList.get(i), o = outList.get(i);
                double dMinX = (o[0]-r[0])*1000, dMaxX = (o[1]-r[1])*1000;
                double dMinY = (o[2]-r[2])*1000, dMaxY = (o[3]-r[3])*1000;
                double dMinZ = (o[4]-r[4])*1000, dMaxZ = (o[5]-r[5])*1000;
                Band band = classifyForClass(cls, r, o);
                deltas.add(new ElementDelta(cls, i, dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, band));
                switch (band) { case EXACT -> exact++; case DRIFT -> drift++; case SHIFT -> shift++; default -> {} }
            }
            for (int i = paired; i < refList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.MISSING)); missing++;
            }
            for (int i = paired; i < outList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.EXTRA)); extra++;
            }
        }

        return new DiffReport(deltas, classCounts, exact, drift, shift, missing, extra);
    }

    private static DiffReport diffByPosition(String refDbPath, String outDbPath) {
        return diffByPosition(refDbPath, outDbPath, null);
    }

    private static DiffReport diffByPosition(String refDbPath, String outDbPath, Set<String> ifcClassFilter) {
        Map<String, List<double[]>> refElements = loadElements(refDbPath, ifcClassFilter);
        Map<String, List<double[]>> outElements = loadElements(outDbPath, ifcClassFilter);

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
                double[] r = refList.get(i), o = outList.get(i);
                double dMinX = (o[0]-r[0])*1000, dMaxX = (o[1]-r[1])*1000;
                double dMinY = (o[2]-r[2])*1000, dMaxY = (o[3]-r[3])*1000;
                double dMinZ = (o[4]-r[4])*1000, dMaxZ = (o[5]-r[5])*1000;
                Band band = classifyForClass(cls, r, o);
                deltas.add(new ElementDelta(cls, i, dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, band));
                switch (band) { case EXACT -> exact++; case DRIFT -> drift++; case SHIFT -> shift++; default -> {} }
            }
            for (int i = paired; i < refList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.MISSING)); missing++;
            }
            for (int i = paired; i < outList.size(); i++) {
                deltas.add(new ElementDelta(cls, i, 0, 0, 0, 0, 0, 0, Band.EXTRA)); extra++;
            }
        }

        return new DiffReport(deltas, classCounts, exact, drift, shift, missing, extra);
    }

    private static int compareCoords(double[] a, double[] b) {
        int cmp = Long.compare(Math.round(a[0] * 100), Math.round(b[0] * 100));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round(a[2] * 100), Math.round(b[2] * 100));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round(a[4] * 100), Math.round(b[4] * 100));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round((a[1]-a[0]) * 1000), Math.round((b[1]-b[0]) * 1000));
        if (cmp != 0) return cmp;
        cmp = Long.compare(Math.round((a[3]-a[2]) * 1000), Math.round((b[3]-b[2]) * 1000));
        if (cmp != 0) return cmp;
        return Long.compare(Math.round((a[5]-a[4]) * 1000), Math.round((b[5]-b[4]) * 1000));
    }

    private static Band classify(double... deltas_mm) {
        double max = 0;
        for (double d : deltas_mm) max = Math.max(max, Math.abs(d));
        if (max <= EyesConstants.SPATIAL_EXACT_MM) return Band.EXACT;
        if (max <= EyesConstants.SPATIAL_DRIFT_MM) return Band.DRIFT;
        return Band.SHIFT;
    }

    private static Band classifyForClass(String ifcClass, double[] ref, double[] out) {
        double dMinX = (out[0]-ref[0])*1000, dMaxX = (out[1]-ref[1])*1000;
        double dMinY = (out[2]-ref[2])*1000, dMaxY = (out[3]-ref[3])*1000;
        double dMinZ = (out[4]-ref[4])*1000, dMaxZ = (out[5]-ref[5])*1000;

        double wMm = (ref[1] - ref[0]) * 1000;
        double depthMm = (ref[3] - ref[2]) * 1000;
        double hMm = (ref[5] - ref[4]) * 1000;
        if (ShapeClassifier.isHostedOpening(wMm, depthMm, hMm)) {
            double dCx = ((out[0]+out[1])/2 - (ref[0]+ref[1])/2) * 1000;
            double dCy = ((out[2]+out[3])/2 - (ref[2]+ref[3])/2) * 1000;
            double dCz = ((out[4]+out[5])/2 - (ref[4]+ref[5])/2) * 1000;
            double centroidDist = Math.sqrt(dCx*dCx + dCy*dCy + dCz*dCz);
            if (centroidDist <= EyesConstants.SPATIAL_EXACT_MM) return Band.EXACT;
            if (centroidDist <= EyesConstants.SPATIAL_DRIFT_MM) return Band.DRIFT;
            return Band.SHIFT;
        }

        return classify(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ);
    }

    private static Map<String, double[]> loadElementsByIdentity(String dbPath, String idColumn) {
        return loadElementsByIdentity(dbPath, idColumn, null);
    }

    private static Map<String, double[]> loadElementsByIdentity(String dbPath, String idColumn, Set<String> ifcClassFilter) {
        if (!"guid".equals(idColumn) && !"element_ref".equals(idColumn)) return Collections.emptyMap();
        String filterClause = buildFilterClause(ifcClassFilter);
        String sql = String.format("""
            SELECT em.%s, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
            WHERE em.%s IS NOT NULL AND em.%s != ''%s
            """, idColumn, idColumn, idColumn, filterClause.isEmpty() ? "" : " AND " + filterClause);

        Map<String, double[]> result = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id == null || id.isEmpty()) continue;
                result.putIfAbsent(id, new double[]{
                    rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                    rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)});
            }
        } catch (SQLException e) { return Collections.emptyMap(); }
        return result;
    }

    /** Build a SQL WHERE clause fragment for ifc_class IN (...), or empty string if no filter. */
    private static String buildFilterClause(Set<String> ifcClassFilter) {
        if (ifcClassFilter == null || ifcClassFilter.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("em.ifc_class IN (");
        boolean first = true;
        for (String cls : ifcClassFilter) {
            if (!first) sb.append(',');
            sb.append('\'').append(cls).append('\'');
            first = false;
        }
        sb.append(')');
        return sb.toString();
    }

    private static Map<String, String> loadClassByIdentity(String dbPath, String idColumn) {
        if (!"guid".equals(idColumn) && !"element_ref".equals(idColumn)) return Collections.emptyMap();
        String sql = String.format("SELECT em.%s, em.ifc_class FROM elements_meta em WHERE em.%s IS NOT NULL AND em.%s != ''",
            idColumn, idColumn, idColumn);
        Map<String, String> result = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) { result.putIfAbsent(rs.getString(1), rs.getString(2)); }
        } catch (SQLException e) { return Collections.emptyMap(); }
        return result;
    }

    /**
     * Write diff report as TSV to a file.
     * // Implementing S60_ERP_ALIGNMENT.md §9 — Witness: W-S60-DIFF
     */
    public static void writeTsv(DiffReport report, String tsvPath) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(tsvPath))) {
            pw.print(report.toTsv());
        } catch (java.io.IOException e) {
            System.err.printf("[SpatialDiff] Failed to write TSV: %s — %s%n", tsvPath, e.getMessage());
        }
    }

    /**
     * CLI entry point: diff two DBs and write TSV.
     * Usage: SpatialDiff &lt;ref.db&gt; &lt;out.db&gt; &lt;output.tsv&gt;
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: SpatialDiff <ref.db> <out.db> <output.tsv>");
            System.exit(1);
        }
        DiffReport report = diff(args[0], args[1]);
        writeTsv(report, args[2]);
        System.out.printf("[SpatialDiff] %s: %d elements, %d exact, %d drift, %d shift, %d missing, %d extra%n",
                args[2], report.deltas().size(), report.exact(), report.drift(),
                report.shift(), report.missing(), report.extra());
    }

    private static Map<String, List<double[]>> loadElements(String dbPath) {
        return loadElements(dbPath, null);
    }

    private static Map<String, List<double[]>> loadElements(String dbPath, Set<String> ifcClassFilter) {
        String filterClause = buildFilterClause(ifcClassFilter);
        String whereClause = filterClause.isEmpty() ? "" : "WHERE " + filterClause + "\n";
        String sql = """
            SELECT em.ifc_class, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
            """ + whereClause + """
            ORDER BY em.ifc_class,
                     ROUND(r.minX * 100), ROUND(r.minY * 100), ROUND(r.minZ * 100),
                     ROUND((r.maxX - r.minX) * 1000), ROUND((r.maxY - r.minY) * 1000),
                     ROUND((r.maxZ - r.minZ) * 1000),
                     ROUND(r.maxX * 1000), ROUND(r.maxY * 1000), ROUND(r.maxZ * 1000)
            """;
        Map<String, List<double[]>> result = new TreeMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String cls = rs.getString(1);
                result.computeIfAbsent(cls, k -> new ArrayList<>()).add(new double[]{
                    rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                    rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)});
            }
        } catch (SQLException e) {
            throw new RuntimeException("SpatialDiff failed on " + dbPath + ": " + e.getMessage(), e);
        }
        return result;
    }
}
