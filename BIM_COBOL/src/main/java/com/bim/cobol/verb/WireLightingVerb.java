package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.geometry.ConduitRouter;
import com.bim.cobol.geometry.ConduitRouter.ConduitRoutingResult;
import com.bim.cobol.geometry.SprinklerGrid;
import com.bim.compiler.geometry.Point3D;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * WIRE LIGHTING &lt;building_type&gt; &lt;storey&gt; &lt;room_name&gt; [GRID &lt;mm&gt;] [TYPE &lt;fixture&gt;]
 *
 * <p>Computes ceiling light fixture placement, conduit routing, and compliance
 * proofs for a room. Read-only — no DB writes. Queries ad_room_boundary for
 * room AABB, ad_space_type_mep for light_points, and ad_placement_rule for
 * edge offset and max spacing constraints.
 */
public class WireLightingVerb implements Verb<WireLightingVerb.LightingPayload> {

    /** Default ceiling height above storey z_offset (mm). */
    private static final double DEFAULT_CEILING_MM = 2700;
    /** Default fixture lumens (standard LED downlight). */
    private static final double DEFAULT_FIXTURE_LUMENS = 1400.0;
    /** Max fixtures per circuit (NEC standard). */
    private static final int MAX_FIXTURES_PER_CIRCUIT = 10;

    @Override
    public String keyword() { return "WIRE LIGHTING"; }

    @Override
    public VerbResult<LightingPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 3)
            return VerbResult.fail(keyword(),
                    "usage: WIRE LIGHTING <building_type> <storey> <room_name>"
                            + " [GRID <mm>] [TYPE <fixture>]", null);

        Connection bomConn = ctx.bomConn();
        if (bomConn == null)
            return VerbResult.fail(keyword(), "bomConn required", null);

        // Parse args
        String buildingType = args[0];
        String storey = args[1];
        String roomName = args[2];
        double gridMm = 0; // 0 = derive from light_points
        String fixtureType = "LED_DOWNLIGHT";
        for (int i = 3; i < args.length - 1; i++) {
            if ("GRID".equals(args[i])) gridMm = Double.parseDouble(args[i + 1]);
            if ("TYPE".equals(args[i])) fixtureType = args[i + 1];
        }

        // 1. Load room boundary
        RoomBoundary room = loadRoomBoundary(bomConn, buildingType, storey, roomName);
        if (room == null)
            return VerbResult.fail(keyword(),
                    String.format("room '%s' not found in %s / %s",
                            roomName, buildingType, storey), null);

        // 2. Load light_points from ad_space_type_mep
        LightingRule rule = loadLightingRule(bomConn, room.roomType);
        if (rule == null)
            return VerbResult.fail(keyword(),
                    "room type '" + room.roomType + "' not found in ad_space_type_mep", null);

        // 3. Load placement constraints from ad_placement_rule
        PlacementConstraints constraints = loadPlacementConstraints(bomConn);

        // 4. Convert mm → m
        double minXM = room.minXMm / 1000.0;
        double maxXM = room.maxXMm / 1000.0;
        double minYM = room.minYMm / 1000.0;
        double maxYM = room.maxYMm / 1000.0;
        double ceilingZM = (room.zOffsetMm + DEFAULT_CEILING_MM) / 1000.0;
        double roomAreaM2 = (maxXM - minXM) * (maxYM - minYM);

        // 5. Derive spacing: if GRID arg given use it, else sqrt(area / light_points)
        double spacingM;
        if (gridMm > 0) {
            spacingM = gridMm / 1000.0;
        } else {
            spacingM = Math.sqrt(roomAreaM2 / rule.lightPoints);
        }

        // 6. Generate fixture grid (reuse SprinklerGrid)
        List<Point3D> fixtures = SprinklerGrid.generate(
                minXM, maxXM, minYM, maxYM, ceilingZM, spacingM);

        // 7. Route conduits — virtual panel at room min corner, ceiling height
        Point3D panelPos = new Point3D(minXM, minYM, ceilingZM);
        ConduitRoutingResult routing = ConduitRouter.route(fixtures, panelPos, ceilingZM);

        // 8. Compute lux (informational)
        double totalLumens = DEFAULT_FIXTURE_LUMENS * fixtures.size();
        double lux = roomAreaM2 > 0 ? totalLumens / roomAreaM2 : 0;

        // 9. Circuit count
        int circuitCount = (int) Math.ceil((double) fixtures.size() / MAX_FIXTURES_PER_CIRCUIT);

        // 10. Compliance checks
        List<LightingComplianceResult> compliance = new ArrayList<>();

        // EDGE_OFFSET: all fixtures >= edge_offset_m from walls
        double edgeOffset = constraints.edgeOffsetM;
        boolean edgePass = true;
        double worstEdge = Double.MAX_VALUE;
        for (Point3D f : fixtures) {
            double dMinX = f.x() - minXM;
            double dMaxX = maxXM - f.x();
            double dMinY = f.y() - minYM;
            double dMaxY = maxYM - f.y();
            double minDist = Math.min(Math.min(dMinX, dMaxX), Math.min(dMinY, dMaxY));
            if (minDist < worstEdge) worstEdge = minDist;
            if (minDist < edgeOffset - 0.001) edgePass = false;
        }
        compliance.add(new LightingComplianceResult("EDGE_OFFSET", edgePass,
                worstEdge == Double.MAX_VALUE ? 0 : worstEdge, edgeOffset,
                String.format("min edge distance %.3fm >= %.1fm required", worstEdge, edgeOffset)));

        // MAX_SPACING: distance between adjacent fixtures <= max_spacing_m
        double maxSpacingRule = constraints.maxSpacingM;
        double actualMaxSpacing = 0;
        for (int i = 0; i < fixtures.size(); i++) {
            for (int j = i + 1; j < fixtures.size(); j++) {
                double d = fixtures.get(i).distanceTo(fixtures.get(j));
                // Only check adjacent fixtures (same row or same column)
                double dx = Math.abs(fixtures.get(i).x() - fixtures.get(j).x());
                double dy = Math.abs(fixtures.get(i).y() - fixtures.get(j).y());
                if (dx < 0.001 || dy < 0.001) {
                    if (d > actualMaxSpacing) actualMaxSpacing = d;
                }
            }
        }
        boolean spacingPass = fixtures.size() <= 1 || actualMaxSpacing <= maxSpacingRule + 0.001;
        compliance.add(new LightingComplianceResult("MAX_SPACING", spacingPass,
                actualMaxSpacing, maxSpacingRule,
                String.format("max spacing %.3fm <= %.1fm required", actualMaxSpacing, maxSpacingRule)));

        // MIN_COUNT: fixture count >= light_points
        boolean countPass = fixtures.size() >= rule.lightPoints;
        compliance.add(new LightingComplianceResult("MIN_COUNT", countPass,
                fixtures.size(), rule.lightPoints,
                String.format("%d fixtures >= %d light_points required",
                        fixtures.size(), rule.lightPoints)));

        // 11. Build payload
        LightingPayload payload = new LightingPayload(
                buildingType, storey, roomName, room.roomType, fixtureType,
                fixtures.size(), fixtures, routing, compliance,
                roomAreaM2, lux, circuitCount);

        if (payload.compliancePass()) {
            return VerbResult.ok(keyword(),
                    String.format("%s: %d fixtures, %.1f lux, %.1f m conduit, compliance PASS",
                            roomName, fixtures.size(), lux, routing.totalLengthM()),
                    payload);
        } else {
            String fails = compliance.stream()
                    .filter(c -> !c.pass)
                    .map(LightingComplianceResult::detail)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("unknown");
            return VerbResult.fail(keyword(),
                    String.format("%s: %d fixtures, compliance FAIL — %s",
                            roomName, fixtures.size(), fails),
                    payload);
        }
    }

    // ── DB helpers (raw JDBC to BOM.db) ───────────────────────────

    private RoomBoundary loadRoomBoundary(Connection conn, String buildingType,
                                          String storey, String roomName)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT room_type, min_x_mm, max_x_mm, min_y_mm, max_y_mm, z_offset_mm"
                        + " FROM ad_room_boundary"
                        + " WHERE building_type = ? AND storey = ? AND room_name = ?"
                        + " AND is_active = 1"
                        + " AND min_x_mm IS NOT NULL")) {
            ps.setString(1, buildingType);
            ps.setString(2, storey);
            ps.setString(3, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RoomBoundary(
                        rs.getString("room_type"),
                        rs.getDouble("min_x_mm"), rs.getDouble("max_x_mm"),
                        rs.getDouble("min_y_mm"), rs.getDouble("max_y_mm"),
                        rs.getDouble("z_offset_mm"));
            }
        }
    }

    private LightingRule loadLightingRule(Connection conn, String roomType)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT light_points, circuit_type"
                        + " FROM ad_space_type_mep"
                        + " WHERE space_type_id = ?")) {
            ps.setString(1, roomType);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new LightingRule(
                        rs.getInt("light_points"),
                        rs.getString("circuit_type"));
            }
        }
    }

    private PlacementConstraints loadPlacementConstraints(Connection conn)
            throws SQLException {
        double edgeOffset = 0.3;  // default from NEC 210.70
        double maxSpacing = 4.6;  // default from NFPA 13

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rule_id, edge_offset_m, max_spacing_m FROM ad_placement_rule"
                        + " WHERE rule_id IN ('LIGHT_CEILING', 'LIGHT_CEILING_GRID')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ruleId = rs.getString("rule_id");
                    if ("LIGHT_CEILING".equals(ruleId)) {
                        double v = rs.getDouble("edge_offset_m");
                        if (!rs.wasNull()) edgeOffset = v;
                    }
                    if ("LIGHT_CEILING_GRID".equals(ruleId)) {
                        double v = rs.getDouble("max_spacing_m");
                        if (!rs.wasNull()) maxSpacing = v;
                    }
                }
            }
        }
        return new PlacementConstraints(edgeOffset, maxSpacing);
    }

    // ── Records ───────────────────────────────────────────────────

    private record RoomBoundary(String roomType,
                                double minXMm, double maxXMm,
                                double minYMm, double maxYMm,
                                double zOffsetMm) {}

    private record LightingRule(int lightPoints, String circuitType) {}

    private record PlacementConstraints(double edgeOffsetM, double maxSpacingM) {}

    /** Individual compliance check result. */
    public record LightingComplianceResult(String rule, boolean pass,
                                           double actual, double required,
                                           String detail) {}

    /** Payload carrying lighting design results. */
    public record LightingPayload(
            String buildingType,
            String storey,
            String roomName,
            String roomType,
            String fixtureType,
            int fixtureCount,
            List<Point3D> fixturePositions,
            ConduitRoutingResult routing,
            List<LightingComplianceResult> compliance,
            double roomAreaM2,
            double luxLevel,
            int circuitCount
    ) {
        /** True if all compliance checks pass. */
        public boolean compliancePass() {
            return compliance.stream().allMatch(LightingComplianceResult::pass);
        }
    }
}
