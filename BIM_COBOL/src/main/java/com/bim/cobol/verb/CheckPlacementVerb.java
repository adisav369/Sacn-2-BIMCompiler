package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CHECK PLACEMENT [db_path] [TIER &lt;1|2|ALL&gt;]
 *
 * <p>Validates element placement geometry in an extracted or compiled DB.
 * Runs per-element arithmetic proofs (Tier 1) and optional pairwise
 * overlap checks (Tier 2).
 *
 * <p>When no db_path is given, falls back to {@code ctx.outputConn()}.
 * This allows ScriptRunner to manage the connection lifecycle.
 *
 * <p>Read-only verb — never writes to the target DB.
 */
public class CheckPlacementVerb implements Verb<CheckPlacementVerb.PlacementCheckPayload> {

    // ── Proof constants (from PlacementProver) ─────────────────────
    private static final double COORD_MIN = -100.0;
    private static final double COORD_MAX = 1000.0;
    private static final double MIN_DIMENSION = 0.001; // 1mm
    private static final double CENTROID_TOLERANCE = 0.001; // 1mm
    private static final double OVERLAP_VOLUME_THRESHOLD = 0.00001; // (10mm)^3

    @Override
    public String keyword() { return "CHECK PLACEMENT"; }

    @Override
    public VerbResult<PlacementCheckPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Determine connection source: explicit path or outputConn fallback
        String dbPath;
        Connection targetConn;
        boolean selfManaged;
        int tierArgStart;

        if (args.length == 0 || "TIER".equals(args[0])) {
            // No path — use outputConn
            targetConn = ctx.outputConn();
            if (targetConn == null)
                return VerbResult.fail(keyword(),
                        "no db_path and no outputConn — provide either", null);
            dbPath = "(outputConn)";
            selfManaged = false;
            tierArgStart = 0;
        } else {
            dbPath = args[0];
            targetConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            selfManaged = true;
            tierArgStart = 1;
        }

        String tier = "ALL";
        for (int i = tierArgStart; i < args.length - 1; i++) {
            if ("TIER".equals(args[i])) tier = args[i + 1];
        }

        try {
            List<PlacementData> elements = loadPlacements(targetConn);
            if (elements.isEmpty())
                return VerbResult.fail(keyword(), "no elements found in " + dbPath, null);

            List<ProofResult> results = new ArrayList<>();
            boolean runTier1 = "ALL".equals(tier) || "1".equals(tier);
            boolean runTier2 = "ALL".equals(tier) || "2".equals(tier);

            if (runTier1) {
                for (PlacementData e : elements) {
                    provePositiveExtent(e, results);
                    proveFiniteCoords(e, results);
                    proveMinDimension(e, results);
                    proveStoreyZBand(e, elements, results);
                }
            }

            if (runTier2) {
                provePairwise(elements, results);
            }

            int proven = 0, violated = 0;
            for (ProofResult r : results) {
                if (r.pass) proven++; else violated++;
            }

            PlacementCheckPayload payload = new PlacementCheckPayload(
                    dbPath, elements.size(), tier, proven, violated, results);

            if (violated == 0)
                return VerbResult.ok(keyword(),
                        String.format("%d elements, %d proofs — all pass", elements.size(), proven),
                        payload);
            else
                return VerbResult.fail(keyword(),
                        String.format("%d elements, %d pass, %d violated",
                                elements.size(), proven, violated),
                        payload);
        } catch (SQLException e) {
            return VerbResult.fail(keyword(), "DB error: " + e.getMessage(), null);
        } finally {
            if (selfManaged) targetConn.close();
        }
    }

    // ── Data loading ───────────────────────────────────────────────

    private List<PlacementData> loadPlacements(Connection conn) throws SQLException {
        List<PlacementData> result = new ArrayList<>();
        String sql = "SELECT m.guid, m.ifc_class, m.storey,"
                + " r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ"
                + " FROM elements_meta m"
                + " JOIN elements_rtree r ON m.id = r.id";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new PlacementData(
                        rs.getString("guid"),
                        rs.getString("ifc_class"),
                        rs.getString("storey"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")));
            }
        }
        return result;
    }

    // ── Tier 1: per-element proofs ─────────────────────────────────

    /** P01: all extents must be positive. */
    private void provePositiveExtent(PlacementData e, List<ProofResult> out) {
        double dx = e.maxX - e.minX;
        double dy = e.maxY - e.minY;
        double dz = e.maxZ - e.minZ;
        if (dx > 0 && dy > 0 && dz > 0) {
            out.add(new ProofResult("P01", true, e.guid, null));
        } else {
            out.add(new ProofResult("P01", false, e.guid,
                    String.format("non-positive extent: dx=%.4f dy=%.4f dz=%.4f", dx, dy, dz)));
        }
    }

    /** P02: no NaN/Inf, coords within sane range. */
    private void proveFiniteCoords(PlacementData e, List<ProofResult> out) {
        double[] vals = {e.minX, e.maxX, e.minY, e.maxY, e.minZ, e.maxZ};
        for (double v : vals) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                out.add(new ProofResult("P02", false, e.guid, "NaN or Infinite coordinate"));
                return;
            }
            if (v < COORD_MIN || v > COORD_MAX) {
                out.add(new ProofResult("P02", false, e.guid,
                        String.format("coord %.4f outside [%.0f, %.0f]", v, COORD_MIN, COORD_MAX)));
                return;
            }
        }
        out.add(new ProofResult("P02", true, e.guid, null));
    }

    /** P03: smallest axis must be >= 1mm (no degenerate elements). */
    private void proveMinDimension(PlacementData e, List<ProofResult> out) {
        double dx = e.maxX - e.minX;
        double dy = e.maxY - e.minY;
        double dz = e.maxZ - e.minZ;
        double minDim = Math.min(dx, Math.min(dy, dz));
        if (minDim >= MIN_DIMENSION) {
            out.add(new ProofResult("P03", true, e.guid, null));
        } else {
            out.add(new ProofResult("P03", false, e.guid,
                    String.format("min dimension %.6f < %.3f", minDim, MIN_DIMENSION)));
        }
    }

    /** P04: element Z should be within its storey band. */
    private void proveStoreyZBand(PlacementData e, List<PlacementData> all,
                                   List<ProofResult> out) {
        if (e.storey == null || "Unknown".equals(e.storey)) {
            out.add(new ProofResult("P04", true, e.guid, "no storey — skip"));
            return;
        }
        // Compute storey Z range from all elements on that storey
        double storeyMinZ = Double.MAX_VALUE, storeyMaxZ = -Double.MAX_VALUE;
        int count = 0;
        for (PlacementData o : all) {
            if (e.storey.equals(o.storey)) {
                storeyMinZ = Math.min(storeyMinZ, o.minZ);
                storeyMaxZ = Math.max(storeyMaxZ, o.maxZ);
                count++;
            }
        }
        if (count < 2) {
            out.add(new ProofResult("P04", true, e.guid, "singleton storey — skip"));
            return;
        }
        // Element should be within storey band (±0.5m tolerance)
        double tolerance = 0.5;
        if (e.minZ >= storeyMinZ - tolerance && e.maxZ <= storeyMaxZ + tolerance) {
            out.add(new ProofResult("P04", true, e.guid, null));
        } else {
            out.add(new ProofResult("P04", false, e.guid,
                    String.format("Z [%.2f,%.2f] outside storey band [%.2f,%.2f]",
                            e.minZ, e.maxZ, storeyMinZ - tolerance, storeyMaxZ + tolerance)));
        }
    }

    // ── Tier 2: pairwise proofs ────────────────────────────────────

    private void provePairwise(List<PlacementData> elements, List<ProofResult> out) {
        // Group by ifc_class+storey for same-class overlap check
        Map<String, List<PlacementData>> groups = new HashMap<>();
        for (PlacementData e : elements) {
            String key = e.ifcClass + "|" + (e.storey != null ? e.storey : "");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        for (List<PlacementData> group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                PlacementData a = group.get(i);
                for (int j = i + 1; j < group.size(); j++) {
                    PlacementData b = group.get(j);

                    // P05: no duplicate position (centroid within 1mm)
                    double cx_a = (a.minX + a.maxX) / 2, cy_a = (a.minY + a.maxY) / 2,
                           cz_a = (a.minZ + a.maxZ) / 2;
                    double cx_b = (b.minX + b.maxX) / 2, cy_b = (b.minY + b.maxY) / 2,
                           cz_b = (b.minZ + b.maxZ) / 2;
                    double dist = Math.sqrt(Math.pow(cx_a - cx_b, 2)
                            + Math.pow(cy_a - cy_b, 2)
                            + Math.pow(cz_a - cz_b, 2));
                    if (dist < CENTROID_TOLERANCE) {
                        out.add(new ProofResult("P05", false, a.guid,
                                String.format("duplicate position with %s (dist=%.4f)",
                                        b.guid, dist)));
                    }

                    // P06: no same-class overlap exceeding volume threshold
                    // Exempt IfcMember and IfcBuildingElementProxy
                    if ("IfcMember".equals(a.ifcClass) || "IfcBuildingElementProxy".equals(a.ifcClass))
                        continue;
                    double overlapX = Math.max(0, Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX));
                    double overlapY = Math.max(0, Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY));
                    double overlapZ = Math.max(0, Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ));
                    double vol = overlapX * overlapY * overlapZ;
                    if (vol > OVERLAP_VOLUME_THRESHOLD) {
                        out.add(new ProofResult("P06", false, a.guid,
                                String.format("overlap with %s: vol=%.6f m^3", b.guid, vol)));
                    }
                }
            }
        }
    }

    // ── Records ────────────────────────────────────────────────────

    /** Element bbox data loaded from DB. */
    record PlacementData(String guid, String ifcClass, String storey,
                         double minX, double maxX, double minY, double maxY,
                         double minZ, double maxZ) {}

    /** Single proof result. */
    public record ProofResult(String proofId, boolean pass, String element, String evidence) {}

    /** Verb payload with per-proof results. */
    public record PlacementCheckPayload(
            String dbPath,
            int elementCount,
            String tier,
            int proven,
            int violated,
            List<ProofResult> results
    ) {}
}
