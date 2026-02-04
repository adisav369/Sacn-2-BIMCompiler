package com.bim.compiler.dsl;

import java.util.*;

/**
 * LayoutResolver - Second pass to clean up overlaps.
 *
 * After rough placement (PreCompiler), this pass:
 * 1. Merges shared walls between adjacent rooms
 * 2. Resolves item collisions (products overlapping)
 * 3. Snaps to grid for clean geometry
 *
 * Pattern: Rough cut → Resolve → Clean output
 */
public class LayoutResolver {

    private static final double WALL_THICKNESS = 0.15;
    private static final double SNAP_GRID = 0.05;  // 50mm grid
    private static final double OVERLAP_TOLERANCE = 0.01;  // 10mm

    // =========================================================================
    // Public Records
    // =========================================================================

    public record ResolvedLayout(
        List<ResolvedRoom> rooms,
        List<SharedWall> sharedWalls,
        List<String> resolutions    // What was changed
    ) {}

    public record ResolvedRoom(
        String name,
        String type,
        double x, double y,
        double width, double depth,
        List<AutoFitter.ProductPlacement> products,
        List<String> wallRefs       // References to shared walls
    ) {}

    public record SharedWall(
        String wallId,
        double x1, double y1,
        double x2, double y2,
        double thickness,
        List<String> rooms,         // Rooms sharing this wall
        List<Opening> openings
    ) {}

    public record Opening(
        String type,    // DOOR, WINDOW
        String productId,
        double position,  // Along wall (0-1)
        double width,
        double height,
        double sillHeight
    ) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolve overlaps in precompiled layout.
     */
    public static ResolvedLayout resolve(PreCompiler.GeneratedDSL input) {
        List<ResolvedRoom> resolvedRooms = new ArrayList<>();
        List<SharedWall> sharedWalls = new ArrayList<>();
        List<String> resolutions = new ArrayList<>();

        // Collect all rooms from all storeys
        List<RoomBox> allRooms = new ArrayList<>();
        for (var storey : input.storeys()) {
            for (var room : storey.rooms()) {
                allRooms.add(new RoomBox(
                    room.name(),
                    room.type(),
                    room.x(),
                    room.y(),
                    room.width(),
                    room.depth(),
                    room.products()
                ));
            }
        }

        // Pass 1: Find shared walls
        Map<String, SharedWall> wallMap = findSharedWalls(allRooms, resolutions);
        sharedWalls.addAll(wallMap.values());

        // Pass 2: Resolve product overlaps within each room
        for (RoomBox room : allRooms) {
            List<AutoFitter.ProductPlacement> resolved = resolveProductOverlaps(
                room.products, room.width, room.depth, resolutions
            );

            // Find which walls this room touches
            List<String> wallRefs = findWallRefs(room, wallMap);

            resolvedRooms.add(new ResolvedRoom(
                room.name,
                room.type,
                snap(room.x),
                snap(room.y),
                snap(room.width),
                snap(room.depth),
                resolved,
                wallRefs
            ));
        }

        // Pass 3: Place openings in shared walls
        placeOpeningsInWalls(resolvedRooms, sharedWalls, resolutions);

        return new ResolvedLayout(resolvedRooms, sharedWalls, resolutions);
    }

    /**
     * Quick resolve for a single storey of rooms.
     */
    public static ResolvedLayout resolveStorey(List<PreCompiler.GeneratedRoom> rooms) {
        List<RoomBox> boxes = rooms.stream()
            .map(r -> new RoomBox(r.name(), r.type(), r.x(), r.y(), r.width(), r.depth(), r.products()))
            .toList();

        List<String> resolutions = new ArrayList<>();
        Map<String, SharedWall> wallMap = findSharedWalls(boxes, resolutions);

        List<ResolvedRoom> resolved = new ArrayList<>();
        for (RoomBox room : boxes) {
            List<AutoFitter.ProductPlacement> products = resolveProductOverlaps(
                room.products, room.width, room.depth, resolutions
            );
            resolved.add(new ResolvedRoom(
                room.name, room.type,
                snap(room.x), snap(room.y),
                snap(room.width), snap(room.depth),
                products,
                findWallRefs(room, wallMap)
            ));
        }

        return new ResolvedLayout(resolved, new ArrayList<>(wallMap.values()), resolutions);
    }

    // =========================================================================
    // Wall Resolution
    // =========================================================================

    private record RoomBox(String name, String type, double x, double y,
                          double width, double depth, List<AutoFitter.ProductPlacement> products) {
        double x2() { return x + width; }
        double y2() { return y + depth; }
    }

    private static Map<String, SharedWall> findSharedWalls(List<RoomBox> rooms, List<String> resolutions) {
        Map<String, SharedWall> walls = new LinkedHashMap<>();
        int wallCount = 0;

        for (int i = 0; i < rooms.size(); i++) {
            RoomBox r1 = rooms.get(i);

            for (int j = i + 1; j < rooms.size(); j++) {
                RoomBox r2 = rooms.get(j);

                // Check for shared edge
                SharedEdge edge = findSharedEdge(r1, r2);
                if (edge != null) {
                    String wallId = "WALL_" + (++wallCount);
                    walls.put(wallId, new SharedWall(
                        wallId,
                        edge.x1, edge.y1,
                        edge.x2, edge.y2,
                        WALL_THICKNESS,
                        List.of(r1.name, r2.name),
                        new ArrayList<>()
                    ));
                    resolutions.add(String.format("Merged wall between %s and %s → %s",
                        r1.name, r2.name, wallId));
                }
            }

            // Add perimeter walls (not shared)
            // North wall
            String northId = "WALL_" + r1.name + "_N";
            if (!hasSharedWallAt(r1.x, r1.y2(), r1.x2(), r1.y2(), walls)) {
                walls.put(northId, new SharedWall(northId, r1.x, r1.y2(), r1.x2(), r1.y2(),
                    WALL_THICKNESS, List.of(r1.name), new ArrayList<>()));
            }
            // South wall
            String southId = "WALL_" + r1.name + "_S";
            if (!hasSharedWallAt(r1.x, r1.y, r1.x2(), r1.y, walls)) {
                walls.put(southId, new SharedWall(southId, r1.x, r1.y, r1.x2(), r1.y,
                    WALL_THICKNESS, List.of(r1.name), new ArrayList<>()));
            }
            // East wall
            String eastId = "WALL_" + r1.name + "_E";
            if (!hasSharedWallAt(r1.x2(), r1.y, r1.x2(), r1.y2(), walls)) {
                walls.put(eastId, new SharedWall(eastId, r1.x2(), r1.y, r1.x2(), r1.y2(),
                    WALL_THICKNESS, List.of(r1.name), new ArrayList<>()));
            }
            // West wall
            String westId = "WALL_" + r1.name + "_W";
            if (!hasSharedWallAt(r1.x, r1.y, r1.x, r1.y2(), walls)) {
                walls.put(westId, new SharedWall(westId, r1.x, r1.y, r1.x, r1.y2(),
                    WALL_THICKNESS, List.of(r1.name), new ArrayList<>()));
            }
        }

        return walls;
    }

    private record SharedEdge(double x1, double y1, double x2, double y2) {}

    private static SharedEdge findSharedEdge(RoomBox r1, RoomBox r2) {
        // Check if r1's east = r2's west
        if (Math.abs(r1.x2() - r2.x) < OVERLAP_TOLERANCE) {
            double y1 = Math.max(r1.y, r2.y);
            double y2 = Math.min(r1.y2(), r2.y2());
            if (y2 > y1) {
                return new SharedEdge(r1.x2(), y1, r1.x2(), y2);
            }
        }
        // Check if r1's west = r2's east
        if (Math.abs(r1.x - r2.x2()) < OVERLAP_TOLERANCE) {
            double y1 = Math.max(r1.y, r2.y);
            double y2 = Math.min(r1.y2(), r2.y2());
            if (y2 > y1) {
                return new SharedEdge(r1.x, y1, r1.x, y2);
            }
        }
        // Check if r1's north = r2's south
        if (Math.abs(r1.y2() - r2.y) < OVERLAP_TOLERANCE) {
            double x1 = Math.max(r1.x, r2.x);
            double x2 = Math.min(r1.x2(), r2.x2());
            if (x2 > x1) {
                return new SharedEdge(x1, r1.y2(), x2, r1.y2());
            }
        }
        // Check if r1's south = r2's north
        if (Math.abs(r1.y - r2.y2()) < OVERLAP_TOLERANCE) {
            double x1 = Math.max(r1.x, r2.x);
            double x2 = Math.min(r1.x2(), r2.x2());
            if (x2 > x1) {
                return new SharedEdge(x1, r1.y, x2, r1.y);
            }
        }
        return null;
    }

    private static boolean hasSharedWallAt(double x1, double y1, double x2, double y2,
                                           Map<String, SharedWall> walls) {
        for (SharedWall wall : walls.values()) {
            if (wall.rooms().size() > 1) {  // Only check shared walls
                if (edgesOverlap(x1, y1, x2, y2, wall.x1(), wall.y1(), wall.x2(), wall.y2())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean edgesOverlap(double ax1, double ay1, double ax2, double ay2,
                                        double bx1, double by1, double bx2, double by2) {
        // Check if edges are collinear and overlapping
        boolean sameX = Math.abs(ax1 - ax2) < OVERLAP_TOLERANCE &&
                       Math.abs(bx1 - bx2) < OVERLAP_TOLERANCE &&
                       Math.abs(ax1 - bx1) < OVERLAP_TOLERANCE;
        boolean sameY = Math.abs(ay1 - ay2) < OVERLAP_TOLERANCE &&
                       Math.abs(by1 - by2) < OVERLAP_TOLERANCE &&
                       Math.abs(ay1 - by1) < OVERLAP_TOLERANCE;

        if (sameX) {
            return Math.max(ay1, ay2) > Math.min(by1, by2) &&
                   Math.max(by1, by2) > Math.min(ay1, ay2);
        }
        if (sameY) {
            return Math.max(ax1, ax2) > Math.min(bx1, bx2) &&
                   Math.max(bx1, bx2) > Math.min(ax1, ax2);
        }
        return false;
    }

    private static List<String> findWallRefs(RoomBox room, Map<String, SharedWall> walls) {
        List<String> refs = new ArrayList<>();
        for (var entry : walls.entrySet()) {
            if (entry.getValue().rooms().contains(room.name)) {
                refs.add(entry.getKey());
            }
        }
        return refs;
    }

    // =========================================================================
    // Product Overlap Resolution
    // =========================================================================

    private static List<AutoFitter.ProductPlacement> resolveProductOverlaps(
            List<AutoFitter.ProductPlacement> products, double roomWidth, double roomDepth,
            List<String> resolutions) {

        List<AutoFitter.ProductPlacement> resolved = new ArrayList<>();
        Set<String> placed = new HashSet<>();

        for (AutoFitter.ProductPlacement p : products) {
            // Skip duplicates
            String key = p.productId() + "_" + p.hostFace();
            if (placed.contains(key)) {
                resolutions.add("Removed duplicate: " + p.productId());
                continue;
            }

            // Check for collision with already placed items
            AutoFitter.ProductPlacement adjusted = adjustForCollisions(p, resolved, roomWidth, roomDepth);
            if (adjusted != null) {
                resolved.add(adjusted);
                placed.add(key);
                if (!adjusted.equals(p)) {
                    resolutions.add(String.format("Moved %s from (%.2f,%.2f) to (%.2f,%.2f)",
                        p.productId(), p.x(), p.y(), adjusted.x(), adjusted.y()));
                }
            }
        }

        return resolved;
    }

    private static AutoFitter.ProductPlacement adjustForCollisions(
            AutoFitter.ProductPlacement p,
            List<AutoFitter.ProductPlacement> existing,
            double roomWidth, double roomDepth) {

        AutoFitter.ProductDim dim = AutoFitter.getProductDim(p.productId());
        double pw = dim != null ? dim.width() : 0.3;
        double pd = dim != null ? dim.depth() : 0.3;

        double x = p.x();
        double y = p.y();

        // Check each existing product for collision
        for (AutoFitter.ProductPlacement e : existing) {
            AutoFitter.ProductDim eDim = AutoFitter.getProductDim(e.productId());
            double ew = eDim != null ? eDim.width() : 0.3;
            double ed = eDim != null ? eDim.depth() : 0.3;

            // Simple box collision
            if (boxesOverlap(x, y, pw, pd, e.x(), e.y(), ew, ed)) {
                // Try to nudge
                x = e.x() + ew + 0.1;  // Move right
                if (x + pw > roomWidth) {
                    x = e.x() - pw - 0.1;  // Move left
                }
                if (x < 0) {
                    y = e.y() + ed + 0.1;  // Move up
                    x = p.x();
                }
            }
        }

        // Clamp to room bounds
        x = Math.max(0, Math.min(x, roomWidth - pw));
        y = Math.max(0, Math.min(y, roomDepth - pd));

        return new AutoFitter.ProductPlacement(
            p.productId(), p.productType(),
            snap(x), snap(y), p.z(),
            p.hostFace(), p.rotation(), p.note()
        );
    }

    private static boolean boxesOverlap(double x1, double y1, double w1, double d1,
                                        double x2, double y2, double w2, double d2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + d2 && y1 + d1 > y2;
    }

    // =========================================================================
    // Opening Placement
    // =========================================================================

    private static void placeOpeningsInWalls(List<ResolvedRoom> rooms,
            List<SharedWall> walls, List<String> resolutions) {

        for (ResolvedRoom room : rooms) {
            for (AutoFitter.ProductPlacement p : room.products()) {
                if ("DOOR".equals(p.productType()) || "WINDOW".equals(p.productType())) {
                    // Find which wall this opening belongs to
                    String wallId = findWallForOpening(room, p, walls);
                    if (wallId != null) {
                        SharedWall wall = walls.stream()
                            .filter(w -> w.wallId().equals(wallId))
                            .findFirst()
                            .orElse(null);

                        if (wall != null) {
                            // Calculate position along wall (0-1)
                            double wallLen = Math.hypot(wall.x2() - wall.x1(), wall.y2() - wall.y1());
                            double pos = 0.5;  // Default center

                            AutoFitter.ProductDim dim = AutoFitter.getProductDim(p.productId());
                            double width = dim != null ? dim.width() : 0.9;
                            double height = dim != null ? dim.height() : 2.1;
                            double sill = "WINDOW".equals(p.productType()) ? 0.9 : 0;

                            // Add to wall's openings (mutable list)
                            ((ArrayList<Opening>) wall.openings()).add(new Opening(
                                p.productType(), p.productId(), pos, width, height, sill
                            ));
                        }
                    }
                }
            }
        }
    }

    private static String findWallForOpening(ResolvedRoom room, AutoFitter.ProductPlacement p,
                                             List<SharedWall> walls) {
        String face = p.hostFace();
        for (String wallRef : room.wallRefs()) {
            SharedWall wall = walls.stream()
                .filter(w -> w.wallId().equals(wallRef))
                .findFirst()
                .orElse(null);

            if (wall == null) continue;

            // Match wall direction to face
            boolean isHorizontal = Math.abs(wall.y1() - wall.y2()) < OVERLAP_TOLERANCE;
            boolean isVertical = Math.abs(wall.x1() - wall.x2()) < OVERLAP_TOLERANCE;

            if (("SOUTH".equals(face) || "NORTH".equals(face)) && isHorizontal) {
                return wallRef;
            }
            if (("EAST".equals(face) || "WEST".equals(face)) && isVertical) {
                return wallRef;
            }
        }
        return null;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static double snap(double v) {
        return Math.round(v / SNAP_GRID) * SNAP_GRID;
    }

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== LayoutResolver Test ===\n");

        // Create overlapping rooms
        var rooms = List.of(
            new PreCompiler.GeneratedRoom("living", "LIVING", 0, 0, 4, 4,
                List.of(
                    new AutoFitter.ProductPlacement("DOOR_D1", "DOOR", 2, 0, 0, "SOUTH", 0, ""),
                    new AutoFitter.ProductPlacement("ELEC_LIGHT", "ELECTRICAL", 2, 2, 2.7, "CEILING", 0, "")
                )),
            new PreCompiler.GeneratedRoom("kitchen", "KITCHEN", 4, 0, 3, 3,
                List.of(
                    new AutoFitter.ProductPlacement("DOOR_D1", "DOOR", 4, 1.5, 0, "WEST", 0, ""),
                    new AutoFitter.ProductPlacement("FIXTURE_SINK", "FIXTURE", 5.5, 0.5, 0.85, "SOUTH", 0, "")
                )),
            new PreCompiler.GeneratedRoom("bedroom", "BEDROOM", 0, 4, 4, 3,
                List.of(
                    new AutoFitter.ProductPlacement("DOOR_D1", "DOOR", 4, 5, 0, "EAST", 0, ""),
                    new AutoFitter.ProductPlacement("WINDOW_W1", "WINDOW", 2, 7, 0.9, "NORTH", 0, "")
                ))
        );

        ResolvedLayout resolved = resolveStorey(rooms);

        System.out.println("Shared Walls:");
        for (SharedWall wall : resolved.sharedWalls()) {
            System.out.printf("  %s: (%.1f,%.1f)-(%.1f,%.1f) rooms=%s%n",
                wall.wallId(), wall.x1(), wall.y1(), wall.x2(), wall.y2(), wall.rooms());
        }

        System.out.println("\nResolutions:");
        for (String r : resolved.resolutions()) {
            System.out.println("  - " + r);
        }

        System.out.println("\nResolved Rooms:");
        for (ResolvedRoom room : resolved.rooms()) {
            System.out.printf("  %s: (%.1f,%.1f) %.1fx%.1fm, walls=%s%n",
                room.name(), room.x(), room.y(), room.width(), room.depth(), room.wallRefs());
        }

        AutoFitter.close();
    }
}
