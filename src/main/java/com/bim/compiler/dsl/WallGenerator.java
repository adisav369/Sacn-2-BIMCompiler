package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.dsl.RoomType.WallRule;
import java.util.*;

/**
 * Phase 28: Wall Generator
 *
 * Generates walls based on SpaceType wall rules:
 * - ENCLOSED: 4 walls (private rooms)
 * - PERIMETER_ONLY: Exterior walls only (OPEN_PLAN)
 * - NONE: No walls (PORCH)
 * - AS_REQUIRED: Context-dependent
 *
 * Also handles:
 * - opens_to: Door placement on shared edges
 * - ZONE: No walls between zones within OPEN_PLAN
 */
public class WallGenerator {

    /**
     * Represents a wall segment.
     */
    public record WallSegment(
        String roomName,        // Room this wall belongs to
        String direction,       // north, south, east, west
        double startX, double startY,
        double endX, double endY,
        double height,
        WallType type,
        String opensTo          // If this wall has a door to another room
    ) {
        public enum WallType {
            EXTERIOR,    // External building wall
            PARTITION,   // Internal wall between rooms
            NONE         // No wall (virtual boundary)
        }

        public double length() {
            return Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2));
        }
    }

    /**
     * Result of wall generation.
     */
    public record WallGenerationResult(
        List<WallSegment> walls,
        List<String> doorsGenerated,    // "room1 → room2" connections
        List<String> warnings
    ) {
        public int wallCount() { return walls.size(); }
        public int exteriorWallCount() {
            return (int) walls.stream()
                .filter(w -> w.type() == WallSegment.WallType.EXTERIOR)
                .count();
        }
        public int partitionCount() {
            return (int) walls.stream()
                .filter(w -> w.type() == WallSegment.WallType.PARTITION)
                .count();
        }
    }

    /**
     * Generate walls for a building storey.
     */
    public static WallGenerationResult generate(StoreyDef storey, GridDef grid) {
        List<WallSegment> walls = new ArrayList<>();
        List<String> doors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Build room position map
        Map<String, RoomBounds> roomBounds = new HashMap<>();
        for (RoomDef room : storey.rooms()) {
            RoomBounds bounds = calculateBounds(room, grid);
            if (bounds != null) {
                roomBounds.put(room.name(), bounds);
            }
        }

        // Generate walls for each room
        for (RoomDef room : storey.rooms()) {
            RoomType roomType = RoomType.fromKeyword(room.type());
            WallRule wallRule = roomType.getWallRule();
            RoomBounds bounds = roomBounds.get(room.name());

            if (bounds == null) {
                warnings.add("No bounds for room: " + room.name());
                continue;
            }

            // Get exterior wall directions
            Set<String> exteriorDirs = new HashSet<>(room.getAllExteriorWalls());

            // Generate walls based on rule
            switch (wallRule) {
                case ENCLOSED -> {
                    // All 4 walls
                    walls.addAll(generateEnclosedWalls(room, bounds, exteriorDirs, storey.height()));
                }
                case PERIMETER_ONLY -> {
                    // Only exterior walls
                    walls.addAll(generatePerimeterWalls(room, bounds, exteriorDirs, storey.height()));
                }
                case NONE -> {
                    // No walls - just record virtual boundaries
                    // (Could add posts/columns here in future)
                }
                case AS_REQUIRED -> {
                    // Context-dependent - for now treat as enclosed
                    walls.addAll(generateEnclosedWalls(room, bounds, exteriorDirs, storey.height()));
                }
            }

            // Handle opens_to constraint (door generation)
            if (room.hasOpensTo()) {
                String targetRoom = room.opensTo();
                RoomBounds targetBounds = roomBounds.get(targetRoom);
                if (targetBounds != null) {
                    String sharedEdge = findSharedEdge(bounds, targetBounds);
                    if (sharedEdge != null) {
                        doors.add(room.name() + " → " + targetRoom + " (" + sharedEdge + ")");
                    } else {
                        warnings.add(room.name() + " opens_to " + targetRoom + " but no shared edge");
                    }
                } else {
                    warnings.add(room.name() + " opens_to unknown room: " + targetRoom);
                }
            }
        }

        return new WallGenerationResult(walls, doors, warnings);
    }

    /**
     * Calculate room bounds from grid.
     */
    private static RoomBounds calculateBounds(RoomDef room, GridDef grid) {
        if (!room.hasGridBounds() || grid == null) {
            // Fall back to explicit dimensions
            if (room.width() > 0 && room.depth() > 0) {
                return new RoomBounds(0, 0, room.width(), room.depth());
            }
            return null;
        }

        GridBounds gb = room.getParsedGridBounds();
        if (gb == null) return null;

        double x1 = grid.getX(gb.startX());
        double y1 = grid.getY(gb.startY());
        double x2 = grid.getX(gb.endX());
        double y2 = grid.getY(gb.endY());

        return new RoomBounds(
            Math.min(x1, x2), Math.min(y1, y2),
            Math.max(x1, x2), Math.max(y1, y2)
        );
    }

    /**
     * Room bounds helper.
     */
    private record RoomBounds(double minX, double minY, double maxX, double maxY) {
        public double width() { return maxX - minX; }
        public double height() { return maxY - minY; }
    }

    /**
     * Generate all 4 walls for enclosed rooms.
     */
    private static List<WallSegment> generateEnclosedWalls(
            RoomDef room, RoomBounds bounds, Set<String> exteriorDirs, double height) {

        List<WallSegment> walls = new ArrayList<>();

        // South wall (Y = minY)
        walls.add(new WallSegment(
            room.name(), "south",
            bounds.minX(), bounds.minY(), bounds.maxX(), bounds.minY(),
            height,
            exteriorDirs.contains("south") ? WallSegment.WallType.EXTERIOR : WallSegment.WallType.PARTITION,
            null
        ));

        // North wall (Y = maxY)
        walls.add(new WallSegment(
            room.name(), "north",
            bounds.minX(), bounds.maxY(), bounds.maxX(), bounds.maxY(),
            height,
            exteriorDirs.contains("north") ? WallSegment.WallType.EXTERIOR : WallSegment.WallType.PARTITION,
            null
        ));

        // West wall (X = minX)
        walls.add(new WallSegment(
            room.name(), "west",
            bounds.minX(), bounds.minY(), bounds.minX(), bounds.maxY(),
            height,
            exteriorDirs.contains("west") ? WallSegment.WallType.EXTERIOR : WallSegment.WallType.PARTITION,
            null
        ));

        // East wall (X = maxX)
        walls.add(new WallSegment(
            room.name(), "east",
            bounds.maxX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
            height,
            exteriorDirs.contains("east") ? WallSegment.WallType.EXTERIOR : WallSegment.WallType.PARTITION,
            null
        ));

        return walls;
    }

    /**
     * Generate only exterior walls (for OPEN_PLAN).
     */
    private static List<WallSegment> generatePerimeterWalls(
            RoomDef room, RoomBounds bounds, Set<String> exteriorDirs, double height) {

        List<WallSegment> walls = new ArrayList<>();

        // Only generate walls where exterior: is declared
        if (exteriorDirs.contains("south")) {
            walls.add(new WallSegment(
                room.name(), "south",
                bounds.minX(), bounds.minY(), bounds.maxX(), bounds.minY(),
                height, WallSegment.WallType.EXTERIOR, null
            ));
        }

        if (exteriorDirs.contains("north")) {
            walls.add(new WallSegment(
                room.name(), "north",
                bounds.minX(), bounds.maxY(), bounds.maxX(), bounds.maxY(),
                height, WallSegment.WallType.EXTERIOR, null
            ));
        }

        if (exteriorDirs.contains("west")) {
            walls.add(new WallSegment(
                room.name(), "west",
                bounds.minX(), bounds.minY(), bounds.minX(), bounds.maxY(),
                height, WallSegment.WallType.EXTERIOR, null
            ));
        }

        if (exteriorDirs.contains("east")) {
            walls.add(new WallSegment(
                room.name(), "east",
                bounds.maxX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
                height, WallSegment.WallType.EXTERIOR, null
            ));
        }

        return walls;
    }

    /**
     * Find shared edge between two rooms.
     * Returns direction from room1's perspective, or null if no shared edge.
     *
     * Handles both:
     * 1. Adjacent rooms (edges touch exactly)
     * 2. Overlapping bounds where one room's edge is inside the other
     *    (common in open-plan layouts where rooms are carved from larger spaces)
     */
    private static String findSharedEdge(RoomBounds r1, RoomBounds r2) {
        double tolerance = 0.01; // 10mm tolerance

        // Check X overlap (rooms share horizontal space)
        boolean xOverlap = r1.minX() < r2.maxX() && r1.maxX() > r2.minX();
        // Check Y overlap (rooms share vertical space)
        boolean yOverlap = r1.minY() < r2.maxY() && r1.maxY() > r2.minY();

        // Case 1: Adjacent rooms (edges touch exactly)
        // r2 is to the south of r1 (r1's south wall = r2's north wall)
        if (Math.abs(r1.minY() - r2.maxY()) < tolerance && xOverlap) {
            return "south";
        }
        // r2 is to the north of r1
        if (Math.abs(r1.maxY() - r2.minY()) < tolerance && xOverlap) {
            return "north";
        }
        // r2 is to the west of r1
        if (Math.abs(r1.minX() - r2.maxX()) < tolerance && yOverlap) {
            return "west";
        }
        // r2 is to the east of r1
        if (Math.abs(r1.maxX() - r2.minX()) < tolerance && yOverlap) {
            return "east";
        }

        // Case 2: Overlapping bounds (one room carved from larger space)
        // This handles open-plan layouts where private rooms are "inside" the common area
        if (xOverlap && yOverlap) {
            // r1's north edge is inside r2's Y range (door on north)
            if (r1.maxY() > r2.minY() + tolerance && r1.maxY() < r2.maxY() - tolerance) {
                // Check if there's X overlap at the north edge
                if (r1.minX() < r2.maxX() && r1.maxX() > r2.minX()) {
                    return "north";
                }
            }
            // r1's south edge is inside r2's Y range (door on south)
            if (r1.minY() > r2.minY() + tolerance && r1.minY() < r2.maxY() - tolerance) {
                if (r1.minX() < r2.maxX() && r1.maxX() > r2.minX()) {
                    return "south";
                }
            }
            // r1's east edge is inside r2's X range (door on east)
            if (r1.maxX() > r2.minX() + tolerance && r1.maxX() < r2.maxX() - tolerance) {
                if (r1.minY() < r2.maxY() && r1.maxY() > r2.minY()) {
                    return "east";
                }
            }
            // r1's west edge is inside r2's X range (door on west)
            if (r1.minX() > r2.minX() + tolerance && r1.minX() < r2.maxX() - tolerance) {
                if (r1.minY() < r2.maxY() && r1.maxY() > r2.minY()) {
                    return "west";
                }
            }
        }

        return null;
    }
}
