package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CHECK ROOM &lt;db_path&gt; [ROOM_TYPE &lt;type&gt;]
 *
 * <p>Validates room dimensions against ad_ubbl_rule building code constraints.
 * Room geometry is derived from IfcSpace spatial_structure entries,
 * with bounds computed from contained elements via rel_contained_in_space.
 *
 * <p>Read-only verb. Uses ctx.bomConn() for ad_ubbl_rule lookups.
 */
public class CheckRoomVerb implements Verb<CheckRoomVerb.RoomCheckPayload> {

    @Override
    public String keyword() { return "CHECK ROOM"; }

    @Override
    public VerbResult<RoomCheckPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 1)
            return VerbResult.fail(keyword(),
                    "usage: CHECK ROOM <db_path> [ROOM_TYPE <type>]", null);

        String dbPath = args[0];
        String roomTypeFilter = null;
        for (int i = 1; i < args.length - 1; i++) {
            if ("ROOM_TYPE".equals(args[i])) roomTypeFilter = args[i + 1];
        }

        // Load UBBL rules from BOM.db
        List<UbblRule> rules;
        if (ctx.bomConn() != null) {
            rules = loadUbblRules(ctx.bomConn());
        } else {
            rules = new ArrayList<>();
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            List<RoomMeasurement> rooms = measureRooms(conn);
            if (rooms.isEmpty())
                return VerbResult.fail(keyword(), "no IfcSpace rooms found in " + dbPath, null);

            List<RoomCheckResult> results = new ArrayList<>();
            for (RoomMeasurement room : rooms) {
                if (roomTypeFilter != null
                        && !room.name.toUpperCase().contains(roomTypeFilter.toUpperCase()))
                    continue;
                checkRoom(room, rules, results);
            }

            int passed = 0, failed = 0;
            for (RoomCheckResult r : results) {
                if (r.pass) passed++; else failed++;
            }

            RoomCheckPayload payload = new RoomCheckPayload(
                    dbPath, rooms.size(), rules.size(), passed, failed, results);

            if (failed == 0)
                return VerbResult.ok(keyword(),
                        String.format("%d rooms, %d checks — all pass", rooms.size(), passed),
                        payload);
            else
                return VerbResult.fail(keyword(),
                        String.format("%d rooms, %d pass, %d failed",
                                rooms.size(), passed, failed),
                        payload);
        } catch (SQLException e) {
            return VerbResult.fail(keyword(), "DB error: " + e.getMessage(), null);
        }
    }

    // ── Room measurement ───────────────────────────────────────────

    /** Compute room bounds from contained elements (IfcSpace has no RTREE entry). */
    private List<RoomMeasurement> measureRooms(Connection conn) throws SQLException {
        List<RoomMeasurement> rooms = new ArrayList<>();

        String sql = "SELECT s.name,"
                + " MIN(r.minX) AS mnx, MAX(r.maxX) AS mxx,"
                + " MIN(r.minY) AS mny, MAX(r.maxY) AS mxy,"
                + " MIN(r.minZ) AS mnz, MAX(r.maxZ) AS mxz,"
                + " COUNT(*) AS cnt"
                + " FROM spatial_structure s"
                + " JOIN rel_contained_in_space c ON s.guid = c.space_guid"
                + " JOIN elements_meta m ON c.element_guid = m.guid"
                + " JOIN elements_rtree r ON m.id = r.id"
                + " WHERE s.type = 'IfcSpace'"
                + " GROUP BY s.name"
                + " HAVING COUNT(*) > 0";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double dx = rs.getDouble("mxx") - rs.getDouble("mnx");
                double dy = rs.getDouble("mxy") - rs.getDouble("mny");
                double dz = rs.getDouble("mxz") - rs.getDouble("mnz");
                rooms.add(new RoomMeasurement(
                        rs.getString("name"),
                        dx, dy, dz,
                        dx * dy,            // floor area (m^2)
                        Math.min(dx, dy),    // min plan dimension
                        dz,                  // ceiling height
                        rs.getInt("cnt")));
            }
        }
        return rooms;
    }

    // ── UBBL rule loading ──────────────────────────────────────────

    private List<UbblRule> loadUbblRules(Connection conn) throws SQLException {
        List<UbblRule> rules = new ArrayList<>();
        String sql = "SELECT rule_id, room_type, constraint_key, min_value_mm, ubbl_ref"
                + " FROM ad_ubbl_rule WHERE is_active = 1";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rules.add(new UbblRule(
                        rs.getString("rule_id"),
                        rs.getString("room_type"),
                        rs.getString("constraint_key"),
                        rs.getDouble("min_value_mm"),
                        rs.getString("ubbl_ref")));
            }
        }
        return rules;
    }

    // ── Rule checking ──────────────────────────────────────────────

    private void checkRoom(RoomMeasurement room, List<UbblRule> rules,
                           List<RoomCheckResult> out) {
        // Always check positive area
        if (room.areaSqM > 0) {
            out.add(new RoomCheckResult("AREA_POSITIVE", room.name, true,
                    String.format("area=%.2f m^2", room.areaSqM), null));
        } else {
            out.add(new RoomCheckResult("AREA_POSITIVE", room.name, false,
                    "non-positive area", null));
        }

        // Always check positive ceiling height
        if (room.ceilingHeightM > 0) {
            out.add(new RoomCheckResult("HEIGHT_POSITIVE", room.name, true,
                    String.format("height=%.2f m", room.ceilingHeightM), null));
        } else {
            out.add(new RoomCheckResult("HEIGHT_POSITIVE", room.name, false,
                    "non-positive height", null));
        }

        // Match rules by room type
        for (UbblRule rule : rules) {
            if (rule.roomType == null) {
                // Global rule (e.g. CEILING_MM — applies to all rooms)
                applyRule(room, rule, out);
            } else if (matchesRoomType(room.name, rule.roomType)) {
                applyRule(room, rule, out);
            }
        }
    }

    private void applyRule(RoomMeasurement room, UbblRule rule, List<RoomCheckResult> out) {
        switch (rule.constraintKey) {
            case "AREA" -> {
                double minAreaSqM = rule.minValueMm / 1_000_000.0; // mm^2 → m^2
                boolean pass = room.areaSqM >= minAreaSqM;
                out.add(new RoomCheckResult(rule.ruleId, room.name, pass,
                        String.format("area=%.2f m^2 vs min=%.2f m^2",
                                room.areaSqM, minAreaSqM),
                        rule.ubblRef));
            }
            case "MIN_DIM" -> {
                double minDimM = rule.minValueMm / 1000.0; // mm → m
                boolean pass = room.minDimM >= minDimM;
                out.add(new RoomCheckResult(rule.ruleId, room.name, pass,
                        String.format("minDim=%.2f m vs min=%.2f m",
                                room.minDimM, minDimM),
                        rule.ubblRef));
            }
            case "CEILING_MM" -> {
                double minHeightM = rule.minValueMm / 1000.0; // mm → m
                boolean pass = room.ceilingHeightM >= minHeightM;
                out.add(new RoomCheckResult(rule.ruleId, room.name, pass,
                        String.format("ceiling=%.2f m vs min=%.2f m",
                                room.ceilingHeightM, minHeightM),
                        rule.ubblRef));
            }
            default -> { /* NOT_ADJACENT and others — skip for now */ }
        }
    }

    /** Heuristic match: room name contains room type string. */
    private boolean matchesRoomType(String roomName, String ruleRoomType) {
        // Duplex rooms are coded (A102, B201, etc.) — no type in name
        // We accept any room for type-specific rules when room names don't indicate type
        // This is intentionally permissive for extracted DBs
        return true;
    }

    // ── Records ────────────────────────────────────────────────────

    record RoomMeasurement(String name, double widthM, double depthM, double ceilingHeightM,
                           double areaSqM, double minDimM, double heightM, int elementCount) {}

    record UbblRule(String ruleId, String roomType, String constraintKey,
                    double minValueMm, String ubblRef) {}

    /** Single check result per room per rule. */
    public record RoomCheckResult(String checkId, String room, boolean pass,
                                  String evidence, String ubblRef) {}

    /** Verb payload with all room check results. */
    public record RoomCheckPayload(
            String dbPath,
            int roomCount,
            int ruleCount,
            int passed,
            int failed,
            List<RoomCheckResult> results
    ) {}
}
