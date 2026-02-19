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
                             Map<String, WallSegment> walls) {
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
            var ctx = new ResolutionContext(buildingType, rooms, walls);
            return computeAll(ctx, rules);
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

        return new PlacementAD.Placement(
            ctx.buildingType(), rule.storey, rule.ifcClass, rule.elementRef,
            placementId,
            minX, maxX, minY, maxY, minZ, maxZ,
            rule.orientation, rule.discipline,
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

    // ── Phase RM-5: Flat cache writer ──────────────────────────────

    /**
     * Write computed placements to ad_element_placement as cache.
     * Caller must have already validated via shadowValidate().
     * Deletes old rows for the building and inserts computed rows.
     * Returns count of rows written, or -1 on failure.
     */
    int writeFlatCache(String buildingType) {
        List<PlacementAD.Placement> computed = resolve(buildingType);
        if (computed.isEmpty()) return 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            conn.setAutoCommit(false);

            // Look up building_id FK from ad_building
            Integer buildingFk = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM ad_building WHERE building_type = ?")) {
                ps.setString(1, buildingType);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) buildingFk = rs.getInt(1);
            }

            // Delete old flat rows for this building
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ad_element_placement WHERE building_type = ? AND is_active = 1")) {
                ps.setString(1, buildingType);
                int deleted = ps.executeUpdate();
                System.out.printf("[RM-5] Deleted %d flat rows for %s%n", deleted, buildingType);
            }

            // Insert computed rows
            String insertSql = """
                INSERT INTO ad_element_placement
                (placement_id, building_type, storey, ifc_class, element_ref, ordinal,
                 min_x, max_x, min_y, max_y, min_z, max_z,
                 orientation, discipline, source, is_active, material_name, material_rgba, building_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPUTED:RELATIONAL', 1, ?, ?, ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (PlacementAD.Placement p : computed) {
                    ps.setInt(1, p.ordinal());      // placement_id = extractPlacementId
                    ps.setString(2, p.buildingType());
                    ps.setString(3, p.storey());
                    ps.setString(4, p.ifcClass());
                    ps.setString(5, p.elementRef());
                    ps.setInt(6, p.ordinal());      // ordinal = extractPlacementId
                    ps.setDouble(7, p.minX());
                    ps.setDouble(8, p.maxX());
                    ps.setDouble(9, p.minY());
                    ps.setDouble(10, p.maxY());
                    ps.setDouble(11, p.minZ());
                    ps.setDouble(12, p.maxZ());
                    ps.setString(13, p.orientation());
                    ps.setString(14, p.discipline());
                    ps.setString(15, p.materialName());
                    ps.setString(16, p.materialRgba());
                    if (buildingFk != null) ps.setInt(17, buildingFk);
                    else ps.setNull(17, java.sql.Types.INTEGER);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            System.out.printf("[RM-5] Flat cache written: %s → %d rows%n", buildingType, computed.size());
            return computed.size();
        } catch (SQLException e) {
            System.err.println("[RM-5] Cache write failed: " + e.getMessage());
            return -1;
        }
    }

    // ── Singleton ────────────────────────────────────────────────

    private static RelationalResolver instance;
    static synchronized RelationalResolver getInstance() {
        if (instance == null) instance = new RelationalResolver();
        return instance;
    }
}
