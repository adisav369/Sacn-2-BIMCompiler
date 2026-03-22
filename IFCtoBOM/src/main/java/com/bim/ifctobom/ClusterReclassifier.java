package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.ifctobom.VerbDetector.*;

import java.sql.*;
import java.util.*;

/**
 * Analyses CLUSTER verb groups for promotability to exact verbs (TILE/ROUTE/FRAME).
 *
 * <p>CLUSTER stores lossless per-instance offsets+dims — a replay, not a pattern.
 * Many CLUSTER groups are actually regular patterns (sprinkler grids, pipe routes,
 * structural bays) that failed initial detection criteria. This tool re-examines
 * each group and reports which can be promoted.
 *
 * <p>Usage: {@code ClusterReclassifier.analyse(bomDbPath)} — prints report.
 * {@code ClusterReclassifier.reclassify(bomDbPath)} — rewrites verb_refs in-place.
 *
 * @since S51 — CLUSTER reclassification for generative design readiness
 */
public class ClusterReclassifier {

    /** Analysis result for one CLUSTER group. */
    public record GroupAnalysis(
        String bomId, int sequence, String productId, String storey,
        int qty, String currentVerb,
        String promotedVerb,  // null if not promotable
        String pattern,       // TILE/ROUTE/FRAME/CLUSTER
        String detail         // human-readable description
    ) {
        public boolean isPromotable() { return promotedVerb != null; }
    }

    /**
     * Analyse all CLUSTER groups in a BOM database.
     * Returns analysis for each group — promotable or not.
     */
    public static List<GroupAnalysis> analyse(String bomDbPath) throws SQLException {
        List<GroupAnalysis> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath)) {
            // Read all CLUSTER verb lines
            List<ClusterLine> lines = readClusterLines(conn);

            System.out.printf("%n  ── CLUSTER Reclassification Analysis (%d groups) ──%n%n", lines.size());
            System.out.printf("  %-6s %-50s %-15s %5s  %-8s  %s%n",
                "Seq", "Product", "Storey", "Qty", "Promote", "Detail");
            System.out.println("  " + "─".repeat(120));

            int promotable = 0, totalInstances = 0, promotedInstances = 0;

            for (ClusterLine cl : lines) {
                totalInstances += cl.qty;

                // Parse CLUSTER offsets back into synthetic elements
                double[][] offsets = parseClusterOffsets(cl.verbRef);
                if (offsets == null || offsets.length == 0) {
                    results.add(new GroupAnalysis(cl.bomId, cl.seq, cl.productId,
                        cl.storey, cl.qty, cl.verbRef, null, "CLUSTER", "parse error"));
                    continue;
                }

                // Build synthetic ExtractionElements from offsets
                // Origin = (0,0,0) since offsets are already relative
                List<ExtractionElement> synthElements = buildSyntheticElements(offsets, cl.productId, cl.storey);

                // Run detection cascade (TILE → ROUTE → FRAME)
                // Use origin (0,0,0) since CLUSTER offsets are group-relative
                GroupAnalysis analysis = tryPromote(synthElements, cl, offsets);
                results.add(analysis);

                if (analysis.isPromotable()) {
                    promotable++;
                    promotedInstances += cl.qty;
                }

                String shortProduct = cl.productId.length() > 48
                    ? cl.productId.substring(0, 48) + ".." : cl.productId;
                System.out.printf("  %-6d %-50s %-15s %5d  %-8s  %s%n",
                    cl.seq, shortProduct, cl.storey, cl.qty,
                    analysis.pattern, analysis.detail);
            }

            System.out.printf("%n  SUMMARY: %d/%d groups promotable (%d/%d instances, %.1f%%)%n",
                promotable, lines.size(), promotedInstances, totalInstances,
                totalInstances > 0 ? 100.0 * promotedInstances / totalInstances : 0);
        }

        return results;
    }

    /**
     * Reclassify promotable CLUSTER groups in-place.
     * Rewrites verb_ref for groups that can be promoted to TILE/ROUTE/FRAME.
     */
    public static int reclassify(String bomDbPath) throws SQLException {
        List<GroupAnalysis> analyses = analyse(bomDbPath);
        int promoted = 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE m_bom_line SET verb_ref = ? WHERE bom_id = ? AND sequence = ?")) {
                for (GroupAnalysis a : analyses) {
                    if (a.isPromotable()) {
                        ps.setString(1, a.promotedVerb);
                        ps.setString(2, a.bomId);
                        ps.setInt(3, a.sequence);
                        ps.addBatch();
                        promoted++;
                    }
                }
                if (promoted > 0) ps.executeBatch();
            }
        }

        System.out.printf("%n  RECLASSIFIED: %d CLUSTER groups promoted to exact verbs%n", promoted);
        return promoted;
    }

    // ── Internal helpers ──────────────────────────────────────────────

    private record ClusterLine(String bomId, int seq, String productId,
                               String storey, int qty, String verbRef) {}

    private static List<ClusterLine> readClusterLines(Connection conn) throws SQLException {
        List<ClusterLine> lines = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT bom_id, sequence, child_product_id, storey, qty, verb_ref
                 FROM m_bom_line
                 WHERE verb_ref LIKE 'CLUSTER:%'
                 ORDER BY qty DESC
                 """)) {
            while (rs.next()) {
                lines.add(new ClusterLine(
                    rs.getString(1), rs.getInt(2), rs.getString(3),
                    rs.getString(4), rs.getInt(5), rs.getString(6)));
            }
        }
        return lines;
    }

    /** Parse CLUSTER:dx,dy,dz,w,d,h;dx,dy,dz,w,d,h;... into offset array. */
    static double[][] parseClusterOffsets(String verbRef) {
        String data = verbRef.substring(8); // skip "CLUSTER:"
        String[] entries = data.split(";");
        double[][] offsets = new double[entries.length][6];
        for (int i = 0; i < entries.length; i++) {
            String[] vals = entries[i].split(",");
            if (vals.length < 6) return null;
            for (int j = 0; j < 6; j++) {
                offsets[i][j] = Double.parseDouble(vals[j]);
            }
        }
        return offsets;
    }

    /** Build synthetic ExtractionElements from parsed CLUSTER offsets.
     *  Uses UNIFORM nominal dimensions (median W/D/H) so that pattern detection
     *  sees only positions, not dimension variance. Per-instance dimensions are
     *  attribute set values (M_AttributeSetInstance), not part of the spatial pattern.
     *  Like ERP: a T-shirt has sizes S/M/L/XL — same product, same shelf position. */
    private static List<ExtractionElement> buildSyntheticElements(
            double[][] offsets, String productId, String storey) {
        // Compute median W/D/H — the "nominal" product size
        double[] ws = new double[offsets.length];
        double[] ds = new double[offsets.length];
        double[] hs = new double[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            ws[i] = offsets[i][3]; ds[i] = offsets[i][4]; hs[i] = offsets[i][5];
        }
        Arrays.sort(ws); Arrays.sort(ds); Arrays.sort(hs);
        double nomW = ws[ws.length / 2];
        double nomD = ds[ds.length / 2];
        double nomH = hs[hs.length / 2];

        List<ExtractionElement> elements = new ArrayList<>(offsets.length);
        for (int i = 0; i < offsets.length; i++) {
            double dx = offsets[i][0], dy = offsets[i][1], dz = offsets[i][2];
            // Use nominal dims for all elements — position pattern only
            elements.add(new ExtractionElement(
                storey, "IfcBuildingElementProxy", "SYNTH:" + i, i,
                dx, dx + nomW,    // minX, maxX (nominal)
                dy, dy + nomD,    // minY, maxY (nominal)
                dz, dz + nomH,    // minZ, maxZ (nominal)
                null, null, null, null,
                productId, null, null
            ));
        }
        return elements;
    }

    /** Try promoting a CLUSTER group to TILE, ROUTE, or FRAME.
     *  First tries the whole group. If Z-spread blocks it, sub-groups by Z-band
     *  (like manufacturing BOM workstations) and tries each Z-band independently. */
    private static GroupAnalysis tryPromote(List<ExtractionElement> elements,
                                           ClusterLine cl, double[][] offsets) {
        // Try whole group first
        GroupAnalysis whole = tryDetect(elements, cl, offsets);
        if (whole.isPromotable()) return whole;

        // Sub-group by Z-band (50mm tolerance) — MEP elements at different heights
        // form separate placement patterns (ceiling sprinklers vs floor pipes)
        Map<Long, List<ExtractionElement>> byZBand = new TreeMap<>();
        for (ExtractionElement e : elements) {
            long zBand = Math.round(e.minZ() * 20); // 50mm bands
            byZBand.computeIfAbsent(zBand, k -> new ArrayList<>()).add(e);
        }

        if (byZBand.size() > 1 && byZBand.size() < elements.size() / 2) {
            int promotedInBands = 0;
            int totalInBands = 0;
            List<String> bandDetails = new ArrayList<>();

            for (var entry : byZBand.entrySet()) {
                List<ExtractionElement> band = entry.getValue();
                if (band.size() < 4) continue;  // too small for pattern
                totalInBands += band.size();

                double[][] bandOffsets = new double[band.size()][6];
                double bandMinX = band.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                double bandMinY = band.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                double bandMinZ = band.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);

                for (int i = 0; i < band.size(); i++) {
                    ExtractionElement e = band.get(i);
                    bandOffsets[i] = new double[]{
                        e.minX() - bandMinX, e.minY() - bandMinY, e.minZ() - bandMinZ,
                        e.maxX() - e.minX(), e.maxY() - e.minY(), e.maxZ() - e.minZ()
                    };
                }

                GroupAnalysis bandResult = tryDetect(band, cl, bandOffsets);
                if (bandResult.isPromotable()) {
                    promotedInBands += band.size();
                    bandDetails.add(String.format("Z=%.1fm:%s(%d)",
                        entry.getKey() / 20.0, bandResult.pattern, band.size()));
                }
            }

            if (promotedInBands > 0) {
                String detail = String.format("%d/%d in %d Z-bands: %s",
                    promotedInBands, elements.size(), byZBand.size(),
                    String.join(", ", bandDetails.subList(0, Math.min(5, bandDetails.size()))));
                // Report as partially promotable (can't rewrite verb_ref for partial group yet)
                return new GroupAnalysis(cl.bomId, cl.seq, cl.productId, cl.storey,
                    cl.qty, cl.verbRef, null, "PARTIAL", detail);
            }
        }

        // Not promotable — diagnose
        String reason = diagnoseWhyNotPromotable(offsets);
        return new GroupAnalysis(cl.bomId, cl.seq, cl.productId, cl.storey,
            cl.qty, cl.verbRef, null, "CLUSTER", reason);
    }

    /** Try detecting TILE/ROUTE/FRAME on a group of elements. */
    private static GroupAnalysis tryDetect(List<ExtractionElement> elements,
                                          ClusterLine cl, double[][] offsets) {
        double floorMinX = 0, floorMinY = 0, floorMinZ = 0;

        TileResult tile = VerbDetector.detectTile(elements, floorMinX, floorMinY, floorMinZ);
        if (tile != null && tile.instanceCount() == elements.size()) {
            return new GroupAnalysis(cl.bomId, cl.seq, cl.productId, cl.storey,
                cl.qty, cl.verbRef, tile.verbRef(), "TILE",
                String.format("grid %dx%d step %.1fx%.1fmm",
                    tile.nx(), tile.ny(), tile.stepX() * 1000, tile.stepY() * 1000));
        }

        RouteResult route = VerbDetector.detectRoute(elements, floorMinX, floorMinY, floorMinZ);
        if (route != null && route.instanceCount() == elements.size()) {
            return new GroupAnalysis(cl.bomId, cl.seq, cl.productId, cl.storey,
                cl.qty, cl.verbRef, route.verbRef(), "ROUTE",
                String.format("%d legs, %d instances",
                    route.legs().size(), route.instanceCount()));
        }

        FrameResult frame = VerbDetector.detectFrame(elements, floorMinX, floorMinY, floorMinZ);
        if (frame != null && frame.instanceCount() == elements.size()) {
            return new GroupAnalysis(cl.bomId, cl.seq, cl.productId, cl.storey,
                cl.qty, cl.verbRef, frame.verbRef(), "FRAME",
                String.format("%dx%d grid", frame.xLines().length, frame.yLines().length));
        }

        return new GroupAnalysis(cl.bomId, cl.seq, cl.productId, cl.storey,
            cl.qty, cl.verbRef, null, "CLUSTER", "no pattern");
    }

    /** Diagnose why a CLUSTER group couldn't be promoted. */
    private static String diagnoseWhyNotPromotable(double[][] offsets) {
        if (offsets.length < 4) return "too few (<4)";

        // Check Z uniformity (positions only — dims are ASI)
        double z0 = offsets[0][2];
        boolean zUniform = true;
        for (double[] o : offsets) {
            if (Math.abs(o[2] - z0) > 0.005) { zUniform = false; break; }
        }

        // Count unique X and Y positions
        Set<Long> uniqueX = new TreeSet<>(), uniqueY = new TreeSet<>();
        for (double[] o : offsets) {
            uniqueX.add(Math.round(o[0] * 1000)); // 1mm quantization
            uniqueY.add(Math.round(o[1] * 1000));
        }

        int nX = uniqueX.size(), nY = uniqueY.size();
        boolean isGrid = nX * nY == offsets.length;

        // Count dimension variants (ASI sizes)
        Set<String> dimVariants = new TreeSet<>();
        for (double[] o : offsets) {
            dimVariants.add(String.format("%.0f×%.0f×%.0f",
                o[3] * 1000, o[4] * 1000, o[5] * 1000));
        }
        String asiNote = dimVariants.size() > 1
            ? String.format(" [%d ASI sizes]", dimVariants.size()) : "";

        if (!zUniform) return String.format("Z-spread (nX=%d nY=%d)%s", nX, nY, asiNote);
        if (isGrid && nX >= 2 && nY >= 2)
            return String.format("grid %dx%d step non-uniform%s", nX, nY, asiNote);
        if (nX == 1 || nY == 1)
            return String.format("1D %s (n=%d) step non-uniform%s",
                nX == 1 ? "Y" : "X", Math.max(nX, nY), asiNote);

        return String.format("irregular (nX=%d nY=%d, %d el)%s", nX, nY, offsets.length, asiNote);
    }

    // ── CLI entry point ──────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ClusterReclassifier <bom.db> [--reclassify]");
            System.exit(1);
        }
        String bomDb = args[0];
        boolean doReclassify = args.length > 1 && "--reclassify".equals(args[1]);

        if (doReclassify) {
            reclassify(bomDb);
        } else {
            analyse(bomDb);
        }
    }
}
