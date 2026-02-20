package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase RM-2: Computes element coordinates from relational metadata.
 * Reads: ad_building_grid, ad_room_boundary, ad_wall_face, ad_element_rule
 * Produces: PlacementAD.Placement records identical to ad_element_placement.
 *
 * Shadow mode: compute placements, validate against stored oracle.
 * Does NOT replace the compiler's input — that's Phase RM-3.
 *
 * Pattern: stateless resolver, lazy singleton (same as PlacementAD).
 */
class RelationalResolver {

    private static final String DB_PATH = "library/component_library.db";

    // Internal records for intermediate computation
    record RoomExtent(String name, String storey, String type,
                      double minXmm, double maxXmm, double minYmm, double maxYmm) {}

    record WallSegment(String key, String roomName, String storey, String face,
                       double x1mm, double y1mm, double x2mm, double y2mm,
                       boolean exterior) {}

    record ResolutionContext(String buildingType,
                             Map<String, RoomExtent> rooms,
                             Map<String, WallSegment> walls,
                             Map<String, String> connPointsByProduct) {
        WallSegment wallOrThrow(String wallRef, String elementRef) {
            WallSegment wall = walls.get(wallRef);
            if (wall == null) throw new IllegalStateException(
                "Element " + elementRef + " references wall '" + wallRef
                + "' not found for " + buildingType);
            return wall;
        }
        RoomExtent roomOrThrow(String roomRef, String elementRef) {
            RoomExtent room = rooms.get(roomRef);
            if (room == null) throw new IllegalStateException(
                "Element " + elementRef + " references room '" + roomRef
                + "' not found for " + buildingType);
            return room;
        }
    }

    record ElementRule(String elementRef, String ifcClass, String storey, String discipline,
                       Double heightMm, String familyRef,
                       Double widthMm, Double heightExtentMm, Double depthMm,
                       String orientation, String materialName, String materialRgba,
                       PositionRule positionMode) {}

    /**
     * Resolve all elements for a building into Placement records.
     * Returns list matching ad_element_placement format.
     */
    List<PlacementAD.Placement> resolve(String buildingType) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            Map<String, RoomExtent> rooms = loadRooms(conn, buildingType);
            Map<String, WallSegment> walls = loadWalls(conn, buildingType, rooms);
            List<ElementRule> rules = loadRules(conn, buildingType);
            Map<String, String> connPoints = loadConnPoints(conn);
            var ctx = new ResolutionContext(buildingType, rooms, walls, connPoints);
            return computeAll(ctx, rules);
        } catch (SQLException e) {
            System.err.println("[RelationalResolver] Failed: " + e.getMessage());
            return List.of();
        }
    }

    // ── Load methods ──────────────────────────────────────────────

    private Map<String, RoomExtent> loadRooms(Connection conn, String buildingType) throws SQLException {
        Map<String, RoomExtent> rooms = new HashMap<>();
        String sql = """
            SELECT room_name, storey, room_type,
                   min_x_mm, max_x_mm, min_y_mm, max_y_mm,
                   grid_min_x, grid_max_x, grid_min_y, grid_max_y
            FROM ad_room_boundary WHERE building_type = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            ResultSet rs = ps.executeQuery();
            // Also load grid for fallback
            Map<String, Double> xGrid = new HashMap<>(), yGrid = new HashMap<>();
            try (Statement gs = conn.createStatement();
                 ResultSet grs = gs.executeQuery(
                     "SELECT axis, grid_label, position_mm FROM ad_building_grid WHERE building_type = '" + buildingType + "'")) {
                while (grs.next()) {
                    if ("X".equals(grs.getString(1))) xGrid.put(grs.getString(2), grs.getDouble(3));
                    else yGrid.put(grs.getString(2), grs.getDouble(3));
                }
            }

            while (rs.next()) {
                String name = rs.getString("room_name");
                // Prefer exact coords; fall back to grid-resolved
                double minX = rs.getObject("min_x_mm") != null ? rs.getDouble("min_x_mm")
                    : xGrid.getOrDefault(rs.getString("grid_min_x"), 0.0);
                double maxX = rs.getObject("max_x_mm") != null ? rs.getDouble("max_x_mm")
                    : xGrid.getOrDefault(rs.getString("grid_max_x"), 0.0);
                double minY = rs.getObject("min_y_mm") != null ? rs.getDouble("min_y_mm")
                    : yGrid.getOrDefault(rs.getString("grid_min_y"), 0.0);
                double maxY = rs.getObject("max_y_mm") != null ? rs.getDouble("max_y_mm")
                    : yGrid.getOrDefault(rs.getString("grid_max_y"), 0.0);

                rooms.put(name, new RoomExtent(name, rs.getString("storey"),
                    rs.getString("room_type"), minX, maxX, minY, maxY));
            }
        }
        return rooms;
    }

    private Map<String, WallSegment> loadWalls(Connection conn, String buildingType,
                                                Map<String, RoomExtent> rooms) throws SQLException {
        Map<String, WallSegment> walls = new HashMap<>();
        String sql = """
            SELECT room_name, storey, face, wall_type_id, is_exterior
            FROM ad_wall_face WHERE building_type = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String roomName = rs.getString("room_name");
                String face = rs.getString("face");
                RoomExtent room = rooms.get(roomName);
                if (room == null) continue;

                double x1, y1, x2, y2;
                switch (face) {
                    case "NORTH" -> { x1 = room.minXmm; y1 = room.maxYmm; x2 = room.maxXmm; y2 = room.maxYmm; }
                    case "SOUTH" -> { x1 = room.minXmm; y1 = room.minYmm; x2 = room.maxXmm; y2 = room.minYmm; }
                    case "EAST"  -> { x1 = room.maxXmm; y1 = room.minYmm; x2 = room.maxXmm; y2 = room.maxYmm; }
                    default      -> { x1 = room.minXmm; y1 = room.minYmm; x2 = room.minXmm; y2 = room.maxYmm; } // WEST
                }

                String key = "WALL_" + roomName + "_" + face;
                walls.put(key, new WallSegment(key, roomName, rs.getString("storey"), face,
                    x1, y1, x2, y2, rs.getInt("is_exterior") == 1));
            }
        }
        return walls;
    }

    private List<ElementRule> loadRules(Connection conn, String buildingType) throws SQLException {
        List<ElementRule> rules = new ArrayList<>();
        String sql = """
            SELECT element_ref, ifc_class, storey, discipline,
                   host_type, host_ref, position_rule,
                   position_value, position_value_2, height_mm,
                   family_ref, width_mm, height_extent_mm, depth_mm,
                   orientation, material_name, material_rgba
            FROM ad_element_rule WHERE building_type = ? AND is_active = 1
            ORDER BY id
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String hostType = rs.getString(5);
                String hostRef = rs.getString(6);
                String posRule = rs.getString(7);
                Double posVal = getDoubleOrNull(rs, 8);
                Double posVal2 = getDoubleOrNull(rs, 9);
                rules.add(new ElementRule(
                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    getDoubleOrNull(rs, 10), rs.getString(11),
                    getDoubleOrNull(rs, 12), getDoubleOrNull(rs, 13), getDoubleOrNull(rs, 14),
                    rs.getString(15), rs.getString(16), rs.getString(17),
                    PositionRule.from(posRule, hostType, hostRef, posVal, posVal2)
                ));
            }
        }
        return rules;
    }

    /** Phase RM-11 Step 3: Load conn_points JSON keyed by product_id. */
    private Map<String, String> loadConnPoints(Connection conn) throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT product_id, conn_points FROM ad_product_dim " +
                 "WHERE conn_points IS NOT NULL AND is_active = 1")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        }
        return map;
    }

    /**
     * Phase RM-11 Step 3: Derive concrete orientation from conn_points.
     *
     * <p>For ROOM-hosted fixture/furniture elements with NS/EW or null orientation:
     * reads the BACK or WALL conn_point face of the product and infers which host
     * wall the element is placed against (from position fraction), then returns
     * the rotation radians string so the element faces INTO the room.
     *
     * <p>Convention: north wall → element faces south → π (Math.PI).
     *                south wall → element faces north → 0.
     *                east  wall → element faces west  → π/2.
     *                west  wall → element faces east  → -π/2.
     *
     * @return concrete radians string (e.g. "3.141592653589793") or original
     *         orientation if conn_points doesn't apply
     */
    private String resolveOrientation(ElementRule rule, ResolutionContext ctx,
                                       PositionRule pos) {
        if (rule.familyRef == null) return rule.orientation;
        // Only apply to ROOM-hosted fixture/furniture
        if (!(pos instanceof PositionRule.RoomFraction rf)) return rule.orientation;
        String orient = rule.orientation;
        if (orient != null && !orient.equals("NS") && !orient.equals("EW"))
            return orient;  // already concrete (e.g. radians or semantic)

        String connJson = ctx.connPointsByProduct().get(rule.familyRef);
        if (connJson == null) return orient;

        // Look for BACK or WALL face type — these require the element to be
        // placed against a wall (back/wall face touches the wall surface).
        boolean hasWallConnection = connJson.contains("\"BACK\"")
            || connJson.contains("\"WALL\"");
        if (!hasWallConnection) return orient;

        // Infer host wall from position fraction:
        //   EW orientation → element back is against EAST or WEST wall → use X fraction
        //   NS orientation (or null) → element back is against NORTH or SOUTH → use Y fraction
        String hostWall;
        if ("EW".equals(orient)) {
            hostWall = rf.fractionX() >= 0.5 ? "east" : "west";
        } else {
            // NS or null
            hostWall = rf.fractionY() >= 0.5 ? "north" : "south";
        }

        // rotationFacingInto: north→π, south→0, east→π/2, west→-π/2
        double rotation = switch (hostWall) {
            case "north" -> Math.PI;
            case "south" -> 0.0;
            case "east"  -> Math.PI / 2.0;
            case "west"  -> -Math.PI / 2.0;
            default      -> 0.0;
        };
        return String.valueOf(rotation);
    }

    private static Double getDoubleOrNull(ResultSet rs, int col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    // ── Computation ──────────────────────────────────────────────

    private List<PlacementAD.Placement> computeAll(ResolutionContext ctx, List<ElementRule> rules) {
        List<PlacementAD.Placement> result = new ArrayList<>();
        for (ElementRule rule : rules) {
            PlacementAD.Placement p = computeOne(ctx, rule);
            if (p != null) result.add(p);
        }
        return result;
    }

    private PlacementAD.Placement computeOne(ResolutionContext ctx, ElementRule rule) {
        double widthM  = nn(rule.widthMm) / 1000.0;
        double heightM = nn(rule.heightExtentMm) / 1000.0;
        double depthM  = nn(rule.depthMm) / 1000.0;
        double minZ    = nn(rule.heightMm) / 1000.0;
        double maxZ    = minZ + heightM;

        double minX, maxX, minY, maxY;

        PositionRule pos = rule.positionMode;

        if (pos instanceof PositionRule.DirectCoordinate dc) {
            double cx = dc.cxMm() / 1000.0;
            double cy = dc.cyMm() / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if (pos instanceof PositionRule.WallFraction wf) {
            WallSegment wall = ctx.wallOrThrow(wf.wallRef(), rule.elementRef);

            double t = wf.fraction();
            double wx = wall.x1mm + (wall.x2mm - wall.x1mm) * t;
            double wy = wall.y1mm + (wall.y2mm - wall.y1mm) * t;

            double perpMm = wf.perpOffsetMm();
            if ("NORTH".equals(wall.face) || "SOUTH".equals(wall.face)) {
                wy = wall.y1mm + perpMm;
            } else {
                wx = wall.x1mm + perpMm;
            }

            double cx = wx / 1000.0;
            double cy = wy / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if (pos instanceof PositionRule.RoomFraction rf) {
            RoomExtent room = ctx.roomOrThrow(rf.roomRef(), rule.elementRef);

            double rx = rf.fractionX();
            double ry = rf.fractionY();
            double roomW = room.maxXmm - room.minXmm;
            double roomD = room.maxYmm - room.minYmm;
            double cx = (room.minXmm + rx * roomW) / 1000.0;
            double cy = (room.minYmm + ry * roomD) / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else {
            throw new AssertionError("Unreachable: " + pos.getClass());
        }

        int placementId = extractPlacementId(rule.elementRef);

        // Phase RM-11 Step 3: derive concrete facing rotation from conn_points
        String orientation = resolveOrientation(rule, ctx, pos);

        return new PlacementAD.Placement(
            ctx.buildingType(), rule.storey, rule.ifcClass, rule.elementRef,
            placementId,
            minX, maxX, minY, maxY, minZ, maxZ,
            orientation, rule.discipline,
            rule.materialName, rule.materialRgba,
            rule.familyRef
        );
    }

    private static double nn(Double v) { return v != null ? v : 0.0; }

    private static int extractPlacementId(String elementRef) {
        int idx = elementRef.lastIndexOf('_');
        if (idx >= 0) {
            try { return Integer.parseInt(elementRef.substring(idx + 1)); }
            catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    // ── Singleton ────────────────────────────────────────────────

    private static RelationalResolver instance;
    static synchronized RelationalResolver getInstance() {
        if (instance == null) instance = new RelationalResolver();
        return instance;
    }
}
