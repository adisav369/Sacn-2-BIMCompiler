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

    record ElementRule(String elementRef, String ifcClass, String storey, String discipline,
                       String hostType, String hostRef, String positionRule,
                       Double positionValue, Double positionValue2, Double heightMm,
                       String familyRef, Double widthMm, Double heightExtentMm, Double depthMm,
                       String orientation, String materialName, String materialRgba) {}

    /**
     * Resolve all elements for a building into Placement records.
     * Returns list matching ad_element_placement format.
     */
    List<PlacementAD.Placement> resolve(String buildingType) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            Map<String, RoomExtent> rooms = loadRooms(conn, buildingType);
            Map<String, WallSegment> walls = loadWalls(conn, buildingType, rooms);
            List<ElementRule> rules = loadRules(conn, buildingType);
            return computeAll(buildingType, rules, rooms, walls);
        } catch (SQLException e) {
            System.err.println("[RelationalResolver] Failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Shadow validation: compare computed placements against stored oracle.
     * Loads oracle directly from ad_element_placement (by placement_id).
     * Returns number of mismatches (0 = perfect).
     */
    int shadowValidate(String buildingType) {
        List<PlacementAD.Placement> computed = resolve(buildingType);
        if (computed.isEmpty()) {
            System.err.printf("[RelationalResolver] No rules for %s — skipping validation%n", buildingType);
            return -1;
        }

        // Load oracle indexed by placement_id
        Map<Integer, double[]> oracle = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            String sql = """
                SELECT placement_id, min_x, max_x, min_y, max_y, min_z, max_z
                FROM ad_element_placement
                WHERE building_type = ? AND is_active = 1
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, buildingType);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    oracle.put(rs.getInt(1), new double[]{
                        rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("[RelationalResolver] Oracle load failed: " + e.getMessage());
            return -1;
        }

        int matched = 0, mismatched = 0;
        double maxError = 0;
        double toleranceM = 0.00001; // 0.01mm

        for (PlacementAD.Placement c : computed) {
            int pid = c.ordinal(); // ordinal field holds placement_id from extraction
            double[] o = oracle.get(pid);
            if (o == null) continue;

            double err = Math.max(
                Math.max(Math.abs(c.minX() - o[0]), Math.abs(c.maxX() - o[1])),
                Math.max(
                    Math.max(Math.abs(c.minY() - o[2]), Math.abs(c.maxY() - o[3])),
                    Math.max(Math.abs(c.minZ() - o[4]), Math.abs(c.maxZ() - o[5]))
                )
            );
            maxError = Math.max(maxError, err);
            if (err <= toleranceM) {
                matched++;
            } else {
                mismatched++;
                if (mismatched <= 3) {
                    System.err.printf("  MISMATCH pid=%d %s: err=%.3fmm%n",
                        pid, c.ifcClass(), err * 1000);
                }
            }
        }

        int total = matched + mismatched;
        System.out.printf("[RelationalResolver] %s: %d/%d matched (%.1f%%), max_err=%.3fmm%n",
            buildingType, matched, total,
            matched * 100.0 / Math.max(total, 1), maxError * 1000);

        return mismatched;
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
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rules.add(new ElementRule(
                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getString(7),
                    getDoubleOrNull(rs, 8), getDoubleOrNull(rs, 9), getDoubleOrNull(rs, 10),
                    rs.getString(11),
                    getDoubleOrNull(rs, 12), getDoubleOrNull(rs, 13), getDoubleOrNull(rs, 14),
                    rs.getString(15), rs.getString(16), rs.getString(17)
                ));
            }
        }
        return rules;
    }

    private static Double getDoubleOrNull(ResultSet rs, int col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    // ── Computation ──────────────────────────────────────────────

    private List<PlacementAD.Placement> computeAll(String buildingType, List<ElementRule> rules,
                                                    Map<String, RoomExtent> rooms,
                                                    Map<String, WallSegment> walls) {
        List<PlacementAD.Placement> result = new ArrayList<>();
        // Track ordinals per (storey, ifcClass) for Placement record
        Map<String, Integer> ordinalCounters = new HashMap<>();

        for (ElementRule rule : rules) {
            PlacementAD.Placement p = computeOne(buildingType, rule, rooms, walls);
            if (p != null) result.add(p);
        }
        return result;
    }

    private PlacementAD.Placement computeOne(String buildingType, ElementRule rule,
                                              Map<String, RoomExtent> rooms,
                                              Map<String, WallSegment> walls) {
        double widthM  = nn(rule.widthMm) / 1000.0;
        double heightM = nn(rule.heightExtentMm) / 1000.0;
        double depthM  = nn(rule.depthMm) / 1000.0;
        double minZ    = nn(rule.heightMm) / 1000.0;
        double maxZ    = minZ + heightM;

        double minX, maxX, minY, maxY;

        String posRule = rule.positionRule;

        if ("ABSOLUTE".equals(posRule) || "ENVELOPE".equals(posRule) || "BOUNDARY".equals(posRule)) {
            // Center stored directly in position_value / position_value_2 (mm)
            double cx = nn(rule.positionValue) / 1000.0;
            double cy = nn(rule.positionValue2) / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if ("FRACTION".equals(posRule) && "WALL".equals(rule.hostType)) {
            WallSegment wall = walls.get(rule.hostRef);
            if (wall == null) return null;

            double t = nn(rule.positionValue);
            double wx = wall.x1mm + (wall.x2mm - wall.x1mm) * t;
            double wy = wall.y1mm + (wall.y2mm - wall.y1mm) * t;

            // Perpendicular offset
            double perpMm = nn(rule.positionValue2);
            if ("NORTH".equals(wall.face) || "SOUTH".equals(wall.face)) {
                wy = wall.y1mm + perpMm;
            } else {
                wx = wall.x1mm + perpMm;
            }

            double cx = wx / 1000.0;
            double cy = wy / 1000.0;
            // width_mm = X extent, depth_mm = Y extent (always)
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if ("FRACTION".equals(posRule) && "ROOM".equals(rule.hostType)) {
            RoomExtent room = rooms.get(rule.hostRef);
            if (room == null) return null;

            double rx = nn(rule.positionValue);
            double ry = rule.positionValue2 != null ? rule.positionValue2 : 0.5;
            double roomW = room.maxXmm - room.minXmm;
            double roomD = room.maxYmm - room.minYmm;
            double cx = (room.minXmm + rx * roomW) / 1000.0;
            double cy = (room.minYmm + ry * roomD) / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else {
            // Unhandled position rule
            return null;
        }

        // Extract placement_id from element_ref (format: IfcClass_ID)
        int placementId = extractPlacementId(rule.elementRef);

        return new PlacementAD.Placement(
            buildingType, rule.storey, rule.ifcClass, rule.familyRef,
            placementId,
            minX, maxX, minY, maxY, minZ, maxZ,
            rule.orientation, rule.discipline,
            rule.materialName, rule.materialRgba
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
