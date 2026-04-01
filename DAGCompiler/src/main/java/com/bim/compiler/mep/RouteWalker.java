package com.bim.compiler.mep;

// Implementing DISC_VALIDATION_DB_SRS.md §6.12.3 §3 — Witness: W-PATTERN-CW, W-PATTERN-SP

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * RouteWalker — Pattern applier for unclassified MEP buildings (§6.12.3).
 *
 * <p>NOT a routing engine. No A*, no pathfinding, no graph search.
 * Reads anchor pairs from ad_mep_anchor, applies topology steps from ad_mep_pattern,
 * emits c_orderline rows for CW and SP disciplines.
 *
 * @Traces DISC_VALIDATION_DB_SRS.md §6.12.3 §3
 * @Witnesses W-PATTERN-CW, W-PATTERN-SP
 */
public class RouteWalker {

    /** Result record: line counts and clash-skipped pairs. */
    public record WalkResult(int cwLines, int spLines, int clashSkipped) {}

    /**
     * Walk patterns for CW and SP disciplines against anchor set for the given building.
     *
     * @param compileDb    connection to the compile DB (c_orderline for ARC envelope + write target)
     * @param erpDb        connection to ERP.db (ad_mep_anchor + ad_mep_pattern)
     * @param buildingType source_building name (e.g. "Revit_MEP")
     * @param orderId      C_Order_ID to use for emitted rows
     */
    public static WalkResult walk(Connection compileDb, Connection erpDb,
                                  String buildingType, String orderId) throws SQLException {
        // A1. Pattern selection
        List<PatternStep> cwSteps = loadPatternSteps(erpDb, "CW", buildingType);
        List<PatternStep> spSteps = loadPatternSteps(erpDb, "SP", buildingType);

        if (cwSteps.isEmpty()) BIMLogger.warn("ROUTEWALKER", "[RouteWalker] No CW pattern found for {}", buildingType);
        if (spSteps.isEmpty()) BIMLogger.warn("ROUTEWALKER", "[RouteWalker] No SP pattern found for {}", buildingType);

        // A2. Anchor loading
        List<Anchor> allAnchors = loadAnchors(erpDb, buildingType);
        if (allAnchors.isEmpty()) {
            BIMLogger.warn("ROUTEWALKER", "[RouteWalker] No anchors found for building={}", buildingType);
            return new WalkResult(0, 0, 0);
        }

        // A4. Load ARC envelope for clash checking
        List<ArcBox> arcEnvelope = loadArcEnvelope(compileDb, orderId);

        // Determine starting line number
        int maxLine = getMaxLine(compileDb, orderId);

        // A3. Pattern application per discipline, per storey
        int cwLines = 0, spLines = 0, clashSkipped = 0;
        int lineCounter = maxLine + 10;

        // CW pass
        if (!cwSteps.isEmpty()) {
            ApplyResult cwResult = applyPattern(
                    compileDb, erpDb, "CW", cwSteps, allAnchors, arcEnvelope, orderId, lineCounter, buildingType);
            cwLines = cwResult.emitted;
            clashSkipped += cwResult.clashSkipped;
            lineCounter += cwLines * 10 + 10;
        }

        // SP pass
        if (!spSteps.isEmpty()) {
            ApplyResult spResult = applyPattern(
                    compileDb, erpDb, "SP", spSteps, allAnchors, arcEnvelope, orderId, lineCounter, buildingType);
            spLines = spResult.emitted;
            clashSkipped += spResult.clashSkipped;
        }

        BIMLogger.info("ROUTEWALKER",
                "[RouteWalker] {}: CW={} lines, SP={} lines, clashSkipped={}, METER_anchors_used={}, FIXTURE_anchors_used={}",
                buildingType, cwLines, spLines, clashSkipped,
                allAnchors.stream().filter(a -> "METER".equals(a.anchorType)).count(),
                allAnchors.stream().filter(a -> "FIXTURE".equals(a.anchorType)).count());

        if (cwLines == 0) BIMLogger.warn("ROUTEWALKER",
                "[RouteWalker] WARNING: CW=0 — check METER/FIXTURE anchor counts for {}", buildingType);
        if (spLines == 0) BIMLogger.warn("ROUTEWALKER",
                "[RouteWalker] WARNING: SP=0 — check FIXTURE/GENERIC anchor counts for {}", buildingType);

        return new WalkResult(cwLines, spLines, clashSkipped);
    }

    // -------------------------------------------------------------------------
    // A1. Pattern loading
    // -------------------------------------------------------------------------

    private record PatternStep(String patternId, String discipline, String buildingType,
                               int sequence, String fromNodeType, String toNodeType,
                               String directionAxis, String pieceType, String offsetRule,
                               double gradient) {}

    private static List<PatternStep> loadPatternSteps(Connection erpDb, String discipline,
                                                       String buildingType) throws SQLException {
        // Try exact building_type match first, then fallback to TERMINAL patterns
        String sql = """
            SELECT pattern_id, discipline, building_type, sequence, from_node_type, to_node_type,
                   direction_axis, piece_type, offset_rule, COALESCE(gradient, 0)
            FROM ad_mep_pattern
            WHERE discipline = ?
            ORDER BY
                CASE WHEN building_type = ? THEN 0 ELSE 1 END,
                sequence
            """;
        List<PatternStep> steps = new ArrayList<>();
        try (PreparedStatement ps = erpDb.prepareStatement(sql)) {
            ps.setString(1, discipline);
            ps.setString(2, buildingType);
            ResultSet rs = ps.executeQuery();
            String selectedPatternId = null;
            while (rs.next()) {
                String pid = rs.getString(1);
                if (selectedPatternId == null) selectedPatternId = pid;
                // Only load steps from the first (best) pattern
                if (!pid.equals(selectedPatternId)) break;
                steps.add(new PatternStep(pid, rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getDouble(10)));
            }
        }
        if (!steps.isEmpty()) {
            BIMLogger.info("ROUTEWALKER", "[RouteWalker] {} pattern selected: {} ({} steps)",
                    discipline, steps.get(0).patternId(), steps.size());
        }
        return steps;
    }

    // -------------------------------------------------------------------------
    // A2. Anchor loading + node_type mapping
    // -------------------------------------------------------------------------

    private record Anchor(String anchorId, String anchorType, String nodeType,
                          double x, double y, double z, String storey) {}

    private static String toNodeType(String anchorType, String discipline) {
        return switch (anchorType) {
            case "METER"   -> "SP".equals(discipline) ? "STACK" : "METER";
            case "FIXTURE" -> "FIXTURE";
            case "VALVE"   -> "VALVE";
            case "GENERIC" -> "JUNCTION";
            default        -> "JUNCTION";
        };
    }

    private static List<Anchor> loadAnchors(Connection erpDb, String buildingType) throws SQLException {
        String sql = "SELECT anchor_id, anchor_type, x_m, y_m, z_m, storey FROM ad_mep_anchor WHERE source_building = ?";
        List<Anchor> anchors = new ArrayList<>();
        try (PreparedStatement ps = erpDb.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String anchorType = rs.getString(2);
                // node_type depends on discipline — we map per-discipline at application time
                anchors.add(new Anchor(rs.getString(1), anchorType, anchorType,
                        rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), rs.getString(6)));
            }
        }
        return anchors;
    }

    // -------------------------------------------------------------------------
    // A3. Pattern application
    // -------------------------------------------------------------------------

    private record ApplyResult(int emitted, int clashSkipped) {}

    private static ApplyResult applyPattern(Connection compileDb, Connection erpDb,
                                             String discipline, List<PatternStep> steps,
                                             List<Anchor> allAnchors, List<ArcBox> arcEnvelope,
                                             String orderId, int startLine,
                                             String buildingType) throws SQLException {
        // Group anchors by storey
        Map<String, List<Anchor>> byStorey = new LinkedHashMap<>();
        for (Anchor a : allAnchors) {
            byStorey.computeIfAbsent(a.storey() == null ? "Unknown" : a.storey(),
                    k -> new ArrayList<>()).add(a);
        }

        int emitted = 0;
        int clashSkipped = 0;
        int lineNum = startLine;

        for (Map.Entry<String, List<Anchor>> storeyEntry : byStorey.entrySet()) {
            String storey = storeyEntry.getKey();
            List<Anchor> storeyAnchors = storeyEntry.getValue();

            // used set: track to_anchors already paired
            Set<String> usedAnchorIds = new HashSet<>();

            for (PatternStep step : steps) {
                String fromNodeNeeded = step.fromNodeType();
                String toNodeNeeded   = step.toNodeType();

                // Collect from/to anchors using discipline-specific node_type mapping
                List<Anchor> fromCandidates = new ArrayList<>();
                List<Anchor> toCandidates   = new ArrayList<>();
                for (Anchor a : storeyAnchors) {
                    String nt = toNodeType(a.anchorType(), discipline);
                    if (nt.equals(fromNodeNeeded)) fromCandidates.add(a);
                    if (nt.equals(toNodeNeeded) && !usedAnchorIds.contains(a.anchorId())) toCandidates.add(a);
                }

                if (fromCandidates.isEmpty() || toCandidates.isEmpty()) continue;

                // Pair each from_anchor with nearest unmatched to_anchor (XY distance)
                for (Anchor from : fromCandidates) {
                    Anchor nearest = null;
                    double minDist = Double.MAX_VALUE;
                    for (Anchor to : toCandidates) {
                        if (usedAnchorIds.contains(to.anchorId())) continue;
                        double dx = to.x() - from.x();
                        double dy = to.y() - from.y();
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist < minDist) {
                            minDist = dist;
                            nearest = to;
                        }
                    }
                    if (nearest == null) continue;

                    // A3.4 Sanity guard: skip distance=0 or >50m
                    if (minDist == 0.0 || minDist > 50.0) continue;

                    // Compute dx, dy, dz
                    double dx = nearest.x() - from.x();
                    double dy = nearest.y() - from.y();
                    double dz = nearest.z() - from.z();

                    // A3.5 GRADIENT steps: enforce slope
                    if ("GRADIENT".equals(step.offsetRule()) && step.gradient() > 0) {
                        double horiz = Math.sqrt(dx * dx + dy * dy);
                        dz = step.gradient() * horiz;
                    }

                    // Pipe AABB: 50mm nominal diameter × 1.5 cross-section
                    double pipeWidth  = 50.0 * 1.5;
                    double pipeDepth  = 50.0 * 1.5;
                    double pipeLength = Math.sqrt(dx * dx + dy * dy + dz * dz) * 1000.0;

                    // A4. ARC envelope clash check
                    double midX = (from.x() + nearest.x()) / 2.0 * 1000.0;
                    double midY = (from.y() + nearest.y()) / 2.0 * 1000.0;
                    double midZ = (from.z() + nearest.z()) / 2.0 * 1000.0;

                    if (clashesWithArc(midX, midY, midZ, pipeWidth, pipeDepth, pipeLength, arcEnvelope)) {
                        BIMLogger.warn("ROUTEWALKER",
                                "[RouteWalker] WARNING: Clash skipped: from={} to={} (storey={})",
                                from.anchorId(), nearest.anchorId(), storey);
                        clashSkipped++;
                        continue;
                    }

                    // A5. Resolve M_Product_ID from _import_joint_piece_types
                    String mProductId = resolvePieceType(erpDb, step.pieceType(), buildingType);

                    // A5. Emit c_orderline row
                    emitOrderLine(compileDb, orderId, lineNum, dx, dy, dz,
                            pipeWidth, pipeDepth, pipeLength, mProductId,
                            discipline, discipline.equals("CW") ? 6 : 7);
                    lineNum += 10;
                    emitted++;

                    usedAnchorIds.add(nearest.anchorId());
                }
            }
        }
        return new ApplyResult(emitted, clashSkipped);
    }

    // -------------------------------------------------------------------------
    // A4. ARC envelope clash check
    // -------------------------------------------------------------------------

    private record ArcBox(double cx, double cy, double cz, double w, double d, double h) {}

    private static List<ArcBox> loadArcEnvelope(Connection compileDb, String orderId) throws SQLException {
        String sql = """
            SELECT dx, dy, dz, aabb_width_mm, aabb_depth_mm, aabb_height_mm
            FROM c_orderline WHERE Discipline = 'ARC' AND IsActive = 1
            """;
        List<ArcBox> boxes = new ArrayList<>();
        double cx = 0, cy = 0, cz = 0;
        try (Statement st = compileDb.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                double dx = rs.getDouble(1) * 1000.0;
                double dy = rs.getDouble(2) * 1000.0;
                double dz = rs.getDouble(3) * 1000.0;
                cx += dx; cy += dy; cz += dz;
                double w = rs.getDouble(4);
                double d = rs.getDouble(5);
                double h = rs.getDouble(6);
                if (w > 0 && d > 0 && h > 0) {
                    boxes.add(new ArcBox(cx, cy, cz, w, d, h));
                }
            }
        }
        return boxes;
    }

    private static boolean clashesWithArc(double px, double py, double pz,
                                           double pw, double pd, double ph,
                                           List<ArcBox> arcs) {
        // Tolerance: -10mm (pipes may touch walls but not penetrate)
        double tol = -10.0;
        for (ArcBox arc : arcs) {
            if (aabbOverlap(px, pw, arc.cx(), arc.w(), tol)
                    && aabbOverlap(py, pd, arc.cy(), arc.d(), tol)
                    && aabbOverlap(pz, ph, arc.cz(), arc.h(), tol)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aabbOverlap(double c1, double s1, double c2, double s2, double tol) {
        return Math.abs(c1 - c2) < (s1 / 2.0 + s2 / 2.0 + tol);
    }

    // -------------------------------------------------------------------------
    // A5. Emit c_orderline
    // -------------------------------------------------------------------------

    private static String resolvePieceType(Connection erpDb, String pieceType,
                                            String buildingType) throws SQLException {
        // Try source_building match first, then any
        String sql = """
            SELECT M_Product_ID FROM _import_joint_piece_types
            WHERE piece_type = ?
            ORDER BY CASE WHEN source_building = ? THEN 0 ELSE 1 END
            LIMIT 1
            """;
        try (PreparedStatement ps = erpDb.prepareStatement(sql)) {
            ps.setString(1, pieceType);
            ps.setString(2, buildingType);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            // Table may not have M_Product_ID column — use piece_type as placeholder
        }
        return pieceType;
    }

    private static void emitOrderLine(Connection compileDb, String orderId, int line,
                                       double dx, double dy, double dz,
                                       double widthMm, double depthMm, double heightMm,
                                       String mProductId, String discipline,
                                       int adOrgId) throws SQLException {
        String sql = """
            INSERT INTO c_orderline
            (C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type,
             M_Product_ID, Discipline, AD_Org_ID, Qty, dx, dy, dz,
             aabb_width_mm, aabb_depth_mm, aabb_height_mm, IsActive)
            VALUES (?, NULL, ?, 'MEP_ROUTE', 'GENERATED',
                    ?, ?, ?, 1, ?, ?, ?,
                    ?, ?, ?, 1)
            """;
        try (PreparedStatement ps = compileDb.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setInt(2, line);
            ps.setString(3, mProductId);
            ps.setString(4, discipline);
            ps.setInt(5, adOrgId);
            ps.setDouble(6, dx);
            ps.setDouble(7, dy);
            ps.setDouble(8, dz);
            ps.setDouble(9, widthMm);
            ps.setDouble(10, depthMm);
            ps.setDouble(11, heightMm);
            ps.executeUpdate();
        }
    }

    private static int getMaxLine(Connection compileDb, String orderId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(Line), 0) FROM c_orderline WHERE C_Order_ID = ?";
        try (PreparedStatement ps = compileDb.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
