package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 93: Data-driven furniture resolver using ad_bom / ad_bom_child / ad_bom_child_param.
 *
 * Loads ROOM_FURNITURE BOM tree from component_library.db, resolves per-room:
 * - Scores walls for opening avoidance (work zone against fewest-opening wall)
 * - Guest seat on end wall, back to wall
 * - Big rooms (≥80m², dims≥3m): 2 mirrored zones
 *
 * Federation-extracted offsets: officer chair in L-curve (dy=+0.36),
 * visitor chairs along desk arm (dx=+0.95, +1.76, dy=+0.17).
 */
public class FurnitureBOMResolver {

    private static final String LIB_PATH = "library/component_library.db";
    private static final double BIG_ROOM_AREA = 80.0;
    private static final double BIG_ROOM_MIN_DIM = 3.0;
    private static final double WALL_OFFSET = 0.5;

    // BOM tree loaded from library
    private final Map<String, BOMNode> bomTree = new HashMap<>();

    public record BOMNode(String bomId, List<BOMChild> children) {}

    public record BOMChild(int id, String role, String childBomId, String namePattern,
                           double xOffset, double yOffset, double zOffset, double rotation,
                           String zone, String wallRule, double wallOffset,
                           boolean backToWall) {}

    public record PlacedFurniture(String role, double x, double y, double z,
                                  double rotation, String namePattern) {}

    public FurnitureBOMResolver() {
        loadBOMTree();
    }

    private void loadBOMTree() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Phase 109: Load ALL active furniture BOMs (not just ROOM_FURNITURE)
            String sql = """
                SELECT bc.bom_child_id, bc.bom_id, bc.role, bc.child_bom_id,
                       bc.child_name_pattern, bc.sequence
                FROM ad_bom_child bc
                JOIN ad_bom b ON bc.bom_id = b.bom_id
                WHERE b.is_active = 1
                  AND bc.is_active = 1
                  AND b.group_by = 'ROOM'
                ORDER BY bc.bom_id, bc.sequence
                """;

            Map<Integer, BOMChild> childById = new LinkedHashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    int childId = rs.getInt("bom_child_id");
                    childById.put(childId, new BOMChild(
                        childId, rs.getString("role"),
                        rs.getString("child_bom_id"),
                        rs.getString("child_name_pattern"),
                        0, 0, 0, 0, null, null, WALL_OFFSET, false
                    ));
                    bomTree.computeIfAbsent(rs.getString("bom_id"),
                        k -> new BOMNode(k, new ArrayList<>()));
                }
            }

            // Load params and rebuild with actual values
            for (var entry : childById.entrySet()) {
                int childId = entry.getKey();
                BOMChild base = entry.getValue();

                Map<String, String> params = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT param_key, param_value FROM ad_bom_child_param WHERE bom_child_id = ?")) {
                    ps.setInt(1, childId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            params.put(rs.getString("param_key"), rs.getString("param_value"));
                        }
                    }
                }

                // Phase 109: Support both old (x_offset) and new (dx) param keys
                // Also support name_pattern as param (overrides child_name_pattern column)
                String nameOverride = params.get("name_pattern");
                String effectiveName = nameOverride != null ? nameOverride : base.namePattern();

                // Resolve wall_rule from opposite_wall param
                String wallRule = params.get("wall_rule");
                if (wallRule == null && "true".equalsIgnoreCase(params.get("opposite_wall"))) {
                    wallRule = "OPPOSITE_WORK";
                }

                BOMChild enriched = new BOMChild(
                    base.id(), base.role(), base.childBomId(), effectiveName,
                    parseDouble(params, "dx", parseDouble(params, "x_offset", 0)),
                    parseDouble(params, "dy", parseDouble(params, "y_offset", 0)),
                    parseDouble(params, "dz", parseDouble(params, "z_offset", 0)),
                    parseDouble(params, "rotation", 0),
                    params.get("zone"),
                    wallRule,
                    parseDouble(params, "wall_offset", WALL_OFFSET),
                    "true".equalsIgnoreCase(params.get("back_to_wall"))
                );

                String parentBom = findParentBom(childId, conn);
                if (parentBom != null) {
                    BOMNode node = bomTree.get(parentBom);
                    if (node != null) node.children().add(enriched);
                }
            }

            System.out.printf("[FURNITURE-BOM] Loaded %d BOM nodes%n", bomTree.size());

        } catch (SQLException e) {
            System.err.println("[FURNITURE-BOM] Failed to load: " + e.getMessage());
        }
    }

    private String findParentBom(int childId, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT bom_id FROM ad_bom_child WHERE bom_child_id = ?")) {
            ps.setInt(1, childId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("bom_id");
            }
        }
        return null;
    }

    private static double parseDouble(Map<String, String> params, String key, double def) {
        String val = params.get(key);
        if (val == null) return def;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Resolve furniture placement for a room using the default ROOM_FURNITURE BOM.
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings) {
        return resolveForRoom(roomMinX, roomMinY, roomMaxX, roomMaxY,
            floorZ, roomName, roomType, openings, "ROOM_FURNITURE");
    }

    /**
     * Phase 109: Resolve furniture placement for a room using a specific BOM ID.
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings, String bomId) {

        double roomW = roomMaxX - roomMinX;
        double roomD = roomMaxY - roomMinY;
        double area = roomW * roomD;

        if (area < 6.0) return List.of();

        BOMNode roomFurniture = bomTree.get(bomId);
        if (roomFurniture == null || roomFurniture.children().isEmpty()) {
            return List.of();
        }

        int setCount = (area >= BIG_ROOM_AREA && roomW >= BIG_ROOM_MIN_DIM
                        && roomD >= BIG_ROOM_MIN_DIM) ? 2 : 1;

        List<PlacedFurniture> result = new ArrayList<>();

        for (int setIdx = 0; setIdx < setCount; setIdx++) {
            double zoneMinX, zoneMaxX, zoneMinY, zoneMaxY;
            if (setCount == 1) {
                zoneMinX = roomMinX; zoneMaxX = roomMaxX;
                zoneMinY = roomMinY; zoneMaxY = roomMaxY;
            } else if (roomD >= roomW) {
                double mid = (roomMinY + roomMaxY) / 2;
                zoneMinX = roomMinX; zoneMaxX = roomMaxX;
                zoneMinY = (setIdx == 0) ? roomMinY : mid;
                zoneMaxY = (setIdx == 0) ? mid : roomMaxY;
            } else {
                double mid = (roomMinX + roomMaxX) / 2;
                zoneMinX = (setIdx == 0) ? roomMinX : mid;
                zoneMaxX = (setIdx == 0) ? mid : roomMaxX;
                zoneMinY = roomMinY; zoneMaxY = roomMaxY;
            }

            boolean mirrored = (setIdx == 1);

            String workWall = selectWorkWall(
                zoneMaxX - zoneMinX, zoneMaxY - zoneMinY, openings);

            // Phase 109: Check if this BOM has children with dx/dy offsets
            // (residential sets like BED_SET). If so, place all relative to primary anchor.
            boolean hasOffsets = roomFurniture.children().stream()
                .anyMatch(c -> c.xOffset() != 0 || c.yOffset() != 0);

            if (hasOffsets) {
                // Residential-style: single anchor from primary child, offsets for others
                BOMChild primary = roomFurniture.children().get(0);
                String wall = resolveWall(primary.wallRule(), workWall);
                if (mirrored) wall = oppositeWall(wall);

                // For back_to_wall items, anchor at wall with offset
                double[] anchor = computeZoneAnchor(
                    wall, primary.wallOffset(),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);
                double wallRotation = wallToRotation(wall);

                result.addAll(expandBOMNode(
                    roomFurniture, anchor[0], anchor[1], anchor[2],
                    wallRotation,
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
            } else {
                // Office-style: each zone child gets its own anchor
                for (BOMChild zoneChild : roomFurniture.children()) {
                    String wall = resolveWall(zoneChild.wallRule(), workWall);

                    // For mirrored zone, flip to opposite wall
                    if (mirrored) {
                        wall = oppositeWall(wall);
                    }

                    double[] anchor = computeZoneAnchor(
                        wall, zoneChild.wallOffset(),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);
                    double wallRotation = wallToRotation(wall);

                    if (zoneChild.childBomId() != null) {
                        BOMNode subNode = bomTree.get(zoneChild.childBomId());
                        if (subNode != null) {
                            result.addAll(expandBOMNode(
                                subNode, anchor[0], anchor[1], anchor[2],
                                wallRotation,
                                zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                        }
                    } else {
                        double leafRot;
                        if (zoneChild.backToWall()) {
                            leafRot = wallToRotation(wall);
                        } else {
                            leafRot = wallRotation;
                        }
                        result.add(new PlacedFurniture(
                            zoneChild.role(),
                            anchor[0], anchor[1], anchor[2],
                            leafRot,
                            zoneChild.namePattern()));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Recursively expand a BOM node, applying offsets relative to anchor.
     * The parentRotation determines the orientation of the set (wall-facing).
     */
    private List<PlacedFurniture> expandBOMNode(
            BOMNode node, double anchorX, double anchorY, double anchorZ,
            double parentRotation,
            double zoneMinX, double zoneMinY, double zoneMaxX, double zoneMaxY) {

        List<PlacedFurniture> result = new ArrayList<>();
        double cos = Math.cos(parentRotation);
        double sin = Math.sin(parentRotation);

        for (BOMChild child : node.children()) {
            // Rotate offsets by parent rotation
            double rx = anchorX + child.xOffset() * cos - child.yOffset() * sin;
            double ry = anchorY + child.xOffset() * sin + child.yOffset() * cos;
            double rz = anchorZ + child.zOffset();
            double cr = parentRotation + child.rotation();

            // Bounds check — skip if outside room (with small tolerance)
            double tol = 0.1;
            if (rx < zoneMinX - tol || rx > zoneMaxX + tol
                || ry < zoneMinY - tol || ry > zoneMaxY + tol) {
                continue;
            }

            if (child.childBomId() != null) {
                BOMNode subNode = bomTree.get(child.childBomId());
                if (subNode != null) {
                    result.addAll(expandBOMNode(subNode, rx, ry, rz, cr,
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                }
            } else {
                result.add(new PlacedFurniture(
                    child.role(), rx, ry, rz, cr, child.namePattern()));
            }
        }

        return result;
    }

    /**
     * Convert wall name to rotation angle.
     * Convention: rotation=0 means furniture faces +Y (north).
     * "back to wall" means the furniture's back is against the named wall.
     *
     * south wall → back to south, face north → rotation = 0
     * north wall → back to north, face south → rotation = π
     * west wall  → back to west, face east   → rotation = -π/2
     * east wall  → back to east, face west   → rotation = π/2
     */
    private double wallToRotation(String wall) {
        return switch (wall) {
            case "south" -> 0;
            case "north" -> Math.PI;
            case "west"  -> -Math.PI / 2;
            case "east"  -> Math.PI / 2;
            default      -> 0;
        };
    }

    /**
     * Score walls by opening count and length. Pick wall with fewest openings;
     * break ties by longest wall.
     */
    String selectWorkWall(double roomW, double roomD, List<OpeningInfo> openings) {
        Map<String, Integer> openingCount = new HashMap<>();
        for (String w : List.of("north", "south", "east", "west")) {
            openingCount.put(w, 0);
        }
        if (openings != null) {
            for (OpeningInfo o : openings) {
                if (o.wall() != null) {
                    openingCount.merge(o.wall().toLowerCase(), 1, Integer::sum);
                }
            }
        }

        String bestWall = "north";
        double bestScore = Double.NEGATIVE_INFINITY;

        for (var entry : openingCount.entrySet()) {
            String wall = entry.getKey();
            int count = entry.getValue();
            double length = (wall.equals("north") || wall.equals("south")) ? roomW : roomD;
            double score = -10.0 * count + length;
            if (score > bestScore) {
                bestScore = score;
                bestWall = wall;
            }
        }

        return bestWall;
    }

    private String resolveWall(String wallRule, String workWall) {
        if (wallRule == null || wallRule.equals("NO_OPENINGS")) {
            return workWall;
        }
        if (wallRule.equals("OPPOSITE_WORK")) {
            return oppositeWall(workWall);
        }
        if (wallRule.equals("END_WALL")) {
            return (workWall.equals("north") || workWall.equals("south")) ? "east" : "south";
        }
        return workWall;
    }

    private String oppositeWall(String wall) {
        return switch (wall) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> "south";
        };
    }

    /**
     * Compute anchor position against a wall, inset by wallOffset.
     */
    private double[] computeZoneAnchor(
            String wall, double wallOffset,
            double minX, double minY, double maxX, double maxY, double floorZ) {
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;

        return switch (wall) {
            case "north" -> new double[]{cx, maxY - wallOffset, floorZ};
            case "south" -> new double[]{cx, minY + wallOffset, floorZ};
            case "east"  -> new double[]{maxX - wallOffset, cy, floorZ};
            case "west"  -> new double[]{minX + wallOffset, cy, floorZ};
            default      -> new double[]{cx, maxY - wallOffset, floorZ};
        };
    }

    /**
     * Lightweight opening info — decoupled from BuildingCompiler.OpeningSpec.
     */
    public record OpeningInfo(String type, String wall, double width) {
        public OpeningInfo(String type, String wall) {
            this(type, wall, 0);
        }
    }
}
