package com.bim.compiler.library;

import com.bim.compiler.coordinate.LocalCoord;
import com.bim.compiler.coordinate.StoreyCoord;
import com.bim.compiler.coordinate.WorldCoord;

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
                           boolean backToWall, String productRef) {}

    public record PlacedFurniture(String role, double x, double y, double z,
                                  double rotation, String namePattern, String productRef) {}

    public FurnitureBOMResolver() {
        loadBOMTree();
    }

    private void loadBOMTree() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Phase 109: Load ALL active furniture BOMs (not just ROOM_FURNITURE)
            String sql = """
                SELECT bc.bom_child_id, bc.bom_id, bc.role, bc.child_bom_id,
                       bc.child_name_pattern, bc.sequence, bc.product_ref
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
                        0, 0, 0, 0, null, null, WALL_OFFSET, false,
                        rs.getString("product_ref")
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
                    parseDouble(params, "rotation_rule", 0),
                    params.get("zone"),
                    wallRule,
                    parseDouble(params, "wall_offset", WALL_OFFSET),
                    "true".equalsIgnoreCase(params.get("back_to_wall")),
                    base.productRef()
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

        // Phase 122C: Lower threshold from 6.0 to 2.0 — bathrooms need vanity cabinets
        if (area < 2.0) return List.of();

        BOMNode roomFurniture = bomTree.get(bomId);
        if (roomFurniture == null || roomFurniture.children().isEmpty()) {
            return List.of();
        }

        // Phase 122F: Check for CENTER grid placement (canteen-style area-based replication)
        BOMChild primaryChild = roomFurniture.children().get(0);
        boolean hasOffsets = roomFurniture.children().stream()
            .anyMatch(c -> c.xOffset() != 0 || c.yOffset() != 0);
        boolean isCenterGrid = hasOffsets && "CENTER".equals(primaryChild.wallRule()) && area >= 20.0;

        if (isCenterGrid) {
            // Area-based grid: ~13m² per table set (matches Terminal reference: 60 tables in ~780m²)
            double areaPerSet = 13.0;
            int gridTotal = Math.max(1, (int)(area / areaPerSet));
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(gridTotal * roomW / roomD)));
            int rows = Math.max(1, (int) Math.ceil((double) gridTotal / cols));

            double spacingX = roomW / (cols + 1);
            double spacingY = roomD / (rows + 1);

            List<PlacedFurniture> result = new ArrayList<>();
            int placed = 0;
            for (int r = 0; r < rows && placed < gridTotal; r++) {
                for (int c = 0; c < cols && placed < gridTotal; c++) {
                    double anchorX = roomMinX + spacingX * (c + 1);
                    double anchorY = roomMinY + spacingY * (r + 1);
                    result.addAll(expandBOMNode(
                        roomFurniture,
                        new StoreyCoord(anchorX, anchorY, floorZ, 0.0),
                        roomMinX, roomMinY, roomMaxX, roomMaxY));
                    placed++;
                }
            }
            System.out.printf("[FURNITURE] %s: CENTER grid %dx%d = %d sets (%.0fm² / %.0f = %d target)%n",
                roomName, cols, rows, placed, area, areaPerSet, gridTotal);
            return result;
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

            if (hasOffsets) {
                // Residential-style: single anchor from primary child, offsets for others.
                // The primary child's wall_rule controls the BOM anchor (consumed here).
                // It must NOT be re-applied inside expandBOMNode — strip it from the node
                // passed to expansion so each child is positioned relative to the anchor as-is.
                BOMChild primary = roomFurniture.children().get(0);
                String wall = resolveWall(primary.wallRule(), workWall);
                if (mirrored) wall = oppositeWall(wall);

                // For back_to_wall items, anchor at wall with offset
                StoreyCoord anchor = computeZoneAnchor(
                    wall, primary.wallOffset(),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);

                // Strip wall_rule from primary child: it was consumed above for anchor
                // determination. Re-applying it in expandBOMNode would double-negate the
                // primary child's position (e.g. OPPOSITE_WORK primary → BOM anchor at N wall,
                // then expandBOMNode re-applies OPPOSITE_WORK → child lands at S wall).
                BOMChild primaryStripped = new BOMChild(
                    primary.id(), primary.role(), primary.childBomId(),
                    primary.namePattern(), primary.xOffset(), primary.yOffset(), primary.zOffset(),
                    primary.rotation(), primary.zone(), null,
                    primary.wallOffset(), primary.backToWall(), primary.productRef());
                List<BOMChild> expandChildren = new ArrayList<>(roomFurniture.children());
                expandChildren.set(0, primaryStripped);
                BOMNode expandNode = new BOMNode(roomFurniture.bomId(), expandChildren);

                result.addAll(expandBOMNode(
                    expandNode, anchor,
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
            } else {
                // Office-style: each zone child gets its own anchor
                for (BOMChild zoneChild : roomFurniture.children()) {
                    String wall = resolveWall(zoneChild.wallRule(), workWall);

                    // For mirrored zone, flip to opposite wall
                    if (mirrored) {
                        wall = oppositeWall(wall);
                    }

                    StoreyCoord anchor = computeZoneAnchor(
                        wall, zoneChild.wallOffset(),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);

                    if (zoneChild.childBomId() != null) {
                        BOMNode subNode = bomTree.get(zoneChild.childBomId());
                        if (subNode != null) {
                            result.addAll(expandBOMNode(
                                subNode, anchor,
                                zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                        }
                    } else {
                        // Leaf in office-style path: position IS the anchor (no child offset)
                        // anchor.rotation() == wallToRotation(wall) — both back-to-wall and facing cases
                        result.add(new PlacedFurniture(
                            zoneChild.role(),
                            anchor.x(), anchor.y(), anchor.z(),
                            anchor.rotation(),
                            zoneChild.namePattern(), zoneChild.productRef()));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Recursively expand a BOM node, accumulating offsets relative to a typed anchor.
     *
     * <p>Each child's {@link LocalCoord} (dx, dy, dz, rotation from ad_bom_child_param)
     * is accumulated into a {@link WorldCoord} via {@link LocalCoord#toWorld(StoreyCoord)}.
     * For nested sub-BOMs, the child's world position becomes the new anchor via
     * {@link StoreyCoord#fromWorld(WorldCoord)}.
     *
     * <p>Children with dx=0/dy=0 are placed directly at the anchor — two such children
     * produce the same world position (bunching). Fix: ensure distinct dx/dy in
     * ad_bom_child_param for siblings that must be at different positions.
     *
     * @param anchor {@link StoreyCoord} carrying both position (x,y,z) and wall orientation
     *               (rotation) — eliminates the separate parentRotation parameter
     */
    private List<PlacedFurniture> expandBOMNode(
            BOMNode node, StoreyCoord anchor,
            double zoneMinX, double zoneMinY, double zoneMaxX, double zoneMaxY) {

        List<PlacedFurniture> result = new ArrayList<>();
        double tol = 0.5;

        for (BOMChild child : node.children()) {
            // If child declares OPPOSITE_WORK, re-anchor to the wall opposite to the primary anchor.
            StoreyCoord childAnchor = anchor;
            if ("OPPOSITE_WORK".equals(child.wallRule())) {
                String oppWall = oppositeWall(rotationToWall(anchor.rotation()));
                childAnchor = computeZoneAnchor(
                    oppWall, child.wallOffset(),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, anchor.z());
            }
            LocalCoord offset = new LocalCoord(
                child.xOffset(), child.yOffset(), child.zOffset(), child.rotation());
            WorldCoord childWorld = offset.toWorld(childAnchor);

            // [TRANSLATE] witness — one line per child: anchor + offset → world
            System.out.printf("[TRANSLATE] %s: anchor=(%.3f,%.3f,%.3f,rot=%.3f) + offset=(%.3f,%.3f,%.3f,rot=%.3f) = world(%.3f,%.3f,%.3f)%n",
                child.namePattern() != null ? child.namePattern() : child.role(),
                childAnchor.x(), childAnchor.y(), childAnchor.z(), childAnchor.rotation(),
                child.xOffset(), child.yOffset(), child.zOffset(), child.rotation(),
                childWorld.x(), childWorld.y(), childWorld.z());

            // Bounds check — skip if outside room (BOM children may extend slightly past room edge)
            if (childWorld.x() < zoneMinX - tol || childWorld.x() > zoneMaxX + tol
                || childWorld.y() < zoneMinY - tol || childWorld.y() > zoneMaxY + tol) {
                continue;
            }

            if (child.childBomId() != null) {
                BOMNode subNode = bomTree.get(child.childBomId());
                if (subNode != null) {
                    result.addAll(expandBOMNode(subNode,
                        StoreyCoord.fromWorld(childWorld),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                }
            } else {
                result.add(new PlacedFurniture(
                    child.role(),
                    childWorld.x(), childWorld.y(), childWorld.z(), childWorld.rotation(),
                    child.namePattern(), child.productRef()));
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

    /** Reverse of wallToRotation — maps rotation back to the wall name. */
    private String rotationToWall(double rotation) {
        if (Math.abs(rotation) < 0.01) return "south";
        if (Math.abs(rotation - Math.PI) < 0.01) return "north";
        if (Math.abs(rotation + Math.PI / 2) < 0.01) return "west";
        if (Math.abs(rotation - Math.PI / 2) < 0.01) return "east";
        return "south";
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
        if (wallRule.equals("CENTER")) {
            return "center";
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
     * Compute wall anchor as a typed {@link StoreyCoord}.
     *
     * <p>The returned anchor carries both position (x, y, z) and the wall-facing
     * orientation (rotation), so callers no longer need a separate {@code wallRotation}
     * variable. The rotation is consumed by {@link LocalCoord#toWorld(StoreyCoord)} when
     * children are accumulated.
     */
    private StoreyCoord computeZoneAnchor(
            String wall, double wallOffset,
            double minX, double minY, double maxX, double maxY, double floorZ) {
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;

        return switch (wall) {
            case "north"  -> new StoreyCoord(cx,               maxY - wallOffset, floorZ, wallToRotation("north"));
            case "south"  -> new StoreyCoord(cx,               minY + wallOffset, floorZ, wallToRotation("south"));
            case "east"   -> new StoreyCoord(maxX - wallOffset, cy,               floorZ, wallToRotation("east"));
            case "west"   -> new StoreyCoord(minX + wallOffset, cy,               floorZ, wallToRotation("west"));
            case "center" -> new StoreyCoord(cx,               cy,               floorZ, 0.0);
            default       -> new StoreyCoord(cx,               maxY - wallOffset, floorZ, wallToRotation("north"));
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
