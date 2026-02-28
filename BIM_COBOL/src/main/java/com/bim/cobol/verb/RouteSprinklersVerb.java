package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.geometry.ComplianceChecker;
import com.bim.cobol.geometry.ComplianceChecker.ComplianceResult;
import com.bim.cobol.geometry.ComplianceChecker.CoverageRule;
import com.bim.cobol.geometry.PipeRouter;
import com.bim.cobol.geometry.PipeRouter.PipeRoutingResult;
import com.bim.cobol.geometry.SprinklerGrid;
import com.bim.compiler.geometry.Point3D;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * ROUTE SPRINKLERS &lt;building_type&gt; &lt;storey&gt; &lt;room_name&gt; [SPACING &lt;mm&gt;] [HAZARD &lt;class&gt;]
 *
 * <p>Computes sprinkler head grid, pipe routing, and compliance proof for a room.
 * Read-only — no DB writes. Queries ad_room_boundary for room AABB and
 * ad_fp_coverage for hazard class rules.
 */
public class RouteSprinklersVerb implements Verb<RouteSprinklersVerb.SprinklerRoutingPayload> {

    /** Default head spacing (mm). */
    private static final double DEFAULT_SPACING_MM = 3000;
    /** Default ceiling height above storey z_offset (mm). */
    private static final double DEFAULT_CEILING_MM = 2700;

    @Override
    public String keyword() { return "ROUTE SPRINKLERS"; }

    @Override
    public VerbResult<SprinklerRoutingPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 3)
            return VerbResult.fail(keyword(),
                    "usage: ROUTE SPRINKLERS <building_type> <storey> <room_name>"
                            + " [SPACING <mm>] [HAZARD <class>]", null);

        Connection bomConn = ctx.bomConn();
        if (bomConn == null)
            return VerbResult.fail(keyword(), "bomConn required", null);

        // Parse args
        String buildingType = args[0];
        String storey = args[1];
        String roomName = args[2];
        double spacingMm = DEFAULT_SPACING_MM;
        String hazardClass = "LIGHT";
        for (int i = 3; i < args.length - 1; i++) {
            if ("SPACING".equals(args[i])) spacingMm = Double.parseDouble(args[i + 1]);
            if ("HAZARD".equals(args[i])) hazardClass = args[i + 1];
        }

        // 1. Load room boundary
        RoomBoundary room = loadRoomBoundary(bomConn, buildingType, storey, roomName);
        if (room == null)
            return VerbResult.fail(keyword(),
                    String.format("room '%s' not found in %s / %s",
                            roomName, buildingType, storey), null);

        // 2. Load coverage rule
        CoverageRule rule = loadCoverageRule(bomConn, hazardClass);
        if (rule == null)
            return VerbResult.fail(keyword(),
                    "hazard class '" + hazardClass + "' not found in ad_fp_coverage", null);

        // 3. Convert mm → m
        double minXM = room.minXMm / 1000.0;
        double maxXM = room.maxXMm / 1000.0;
        double minYM = room.minYMm / 1000.0;
        double maxYM = room.maxYMm / 1000.0;
        double ceilingZM = (room.zOffsetMm + DEFAULT_CEILING_MM) / 1000.0;
        double spacingM = spacingMm / 1000.0;

        // 4. Generate sprinkler head grid
        List<Point3D> heads = SprinklerGrid.generate(
                minXM, maxXM, minYM, maxYM, ceilingZM, spacingM);

        // 5. Route pipes — virtual riser at room min corner, ceiling height
        Point3D riserPos = new Point3D(minXM, minYM, ceilingZM);
        PipeRoutingResult routing = PipeRouter.route(heads, riserPos, ceilingZM);

        // 6. Compliance check
        List<ComplianceResult> compliance = ComplianceChecker.check(
                heads, minXM, maxXM, minYM, maxYM, rule);

        // 7. Build payload
        double roomAreaM2 = (maxXM - minXM) * (maxYM - minYM);
        SprinklerRoutingPayload payload = new SprinklerRoutingPayload(
                buildingType, storey, roomName, room.roomType,
                hazardClass, heads.size(), heads, routing, compliance, roomAreaM2);

        if (payload.compliancePass()) {
            return VerbResult.ok(keyword(),
                    String.format("%s: %d heads, %.1f m pipe, compliance PASS",
                            roomName, heads.size(), routing.totalLengthM()),
                    payload);
        } else {
            String fails = compliance.stream()
                    .filter(c -> !c.pass())
                    .map(ComplianceResult::detail)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("unknown");
            return VerbResult.fail(keyword(),
                    String.format("%s: %d heads, compliance FAIL — %s",
                            roomName, heads.size(), fails),
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

    private CoverageRule loadCoverageRule(Connection conn, String hazardClass)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT max_coverage_m2, max_spacing_m, min_spacing_m, wall_distance_m"
                        + " FROM ad_fp_coverage"
                        + " WHERE hazard_class = ? AND is_active = 1")) {
            ps.setString(1, hazardClass);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new CoverageRule(hazardClass,
                        rs.getDouble("max_coverage_m2"),
                        rs.getDouble("max_spacing_m"),
                        rs.getDouble("min_spacing_m"),
                        rs.getDouble("wall_distance_m"));
            }
        }
    }

    /** Room AABB from ad_room_boundary. */
    private record RoomBoundary(String roomType,
                                double minXMm, double maxXMm,
                                double minYMm, double maxYMm,
                                double zOffsetMm) {}

    /** Payload carrying sprinkler routing results. */
    public record SprinklerRoutingPayload(
            String buildingType,
            String storey,
            String roomName,
            String roomType,
            String hazardClass,
            int headCount,
            List<Point3D> headPositions,
            PipeRoutingResult routing,
            List<ComplianceResult> compliance,
            double roomAreaM2
    ) {
        /** True if all compliance checks pass. */
        public boolean compliancePass() {
            return compliance.stream().allMatch(ComplianceResult::pass);
        }
    }
}
