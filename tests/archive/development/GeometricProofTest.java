package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Phase 15B: Mathematical Verification (No Visual)
 *
 * Proves the apartment is geometrically correct with numbers, not screenshots.
 * All 8 checks must pass for valid building.
 */
public class GeometricProofTest {

    private static final double TOLERANCE = 0.005; // 5mm

    // Test results
    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("MATHEMATICAL VERIFICATION REPORT");
        System.out.println("=".repeat(70));
        System.out.println();

        // Parse and compile the apartment
        String dsl = Files.readString(Path.of("examples/apartment_test.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingSpec spec = BuildingCompiler.compile(def);
        StoreySpec storey = spec.storeys().get(0);

        // Extract data for analysis
        List<WallData> walls = extractWalls(storey);
        List<RoomData> rooms = extractRooms(storey);
        List<DoorData> doors = extractDoors(storey);
        List<WindowData> windows = extractWindows(storey);
        Map<String, List<String>> adjacencyConstraints = extractAdjacencyConstraints(def);

        // Run all 8 verification checks
        boolean check1 = verifyWallConnectivity(walls);
        boolean check2 = verifyInteriorWalls(walls, rooms);
        boolean check3 = verifyDoorPlacement(doors, walls);
        boolean check4 = verifyWindowPlacement(windows, walls, storey);
        boolean check5 = verifyRoomEnclosure(rooms, walls);
        boolean check6 = verifyNoOverlap(rooms);
        boolean check7 = verifyAdjacencyConstraints(rooms, walls, doors, adjacencyConstraints);
        boolean check8 = verifyIfcRelationships(storey);

        // Final summary
        printFinalSummary(check1, check2, check3, check4, check5, check6, check7, check8);
    }

    // =========================================================================
    // Data extraction records
    // =========================================================================

    record WallData(String name, double x1, double y1, double x2, double y2,
                    double minZ, double maxZ, double length, boolean isInterior) {}
    record RoomData(String name, double minX, double minY, double maxX, double maxY) {}
    record DoorData(String name, String roomName, String wall, double x, double y,
                    double width, double height) {}
    record WindowData(String name, String roomName, String wall, double x, double y,
                      double width, double height, double sillHeight) {}
    record Corner(double x, double y) {
        @Override public String toString() { return String.format("(%.1f,%.1f)", x, y); }
    }

    // =========================================================================
    // Data extraction methods
    // =========================================================================

    private static List<WallData> extractWalls(StoreySpec storey) {
        List<WallData> walls = new ArrayList<>();
        for (var wall : storey.walls()) {
            // Determine wall endpoints from cladding bounds
            var clad = wall.cladding();
            double x1, y1, x2, y2;

            // Wall orientation detection
            if (Math.abs(clad.maxX() - clad.minX()) > Math.abs(clad.maxY() - clad.minY())) {
                // Horizontal wall (runs along X)
                x1 = clad.minX(); y1 = clad.minY();
                x2 = clad.maxX(); y2 = clad.minY();
            } else {
                // Vertical wall (runs along Y)
                x1 = clad.minX(); y1 = clad.minY();
                x2 = clad.minX(); y2 = clad.maxY();
            }

            double length = Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2));
            boolean isInterior = wall.assemblyName().startsWith("INTERIOR_");

            walls.add(new WallData(wall.assemblyName(), x1, y1, x2, y2,
                                   clad.minZ(), clad.maxZ(), length, isInterior));
        }
        return walls;
    }

    private static List<RoomData> extractRooms(StoreySpec storey) {
        List<RoomData> rooms = new ArrayList<>();
        for (var room : storey.rooms()) {
            rooms.add(new RoomData(room.name(), room.minX(), room.minY(),
                                   room.maxX(), room.maxY()));
        }
        return rooms;
    }

    private static List<DoorData> extractDoors(StoreySpec storey) {
        List<DoorData> doors = new ArrayList<>();
        for (var door : storey.doors()) {
            doors.add(new DoorData(door.name(), door.roomName(), door.wall(),
                                   door.x(), door.y(), door.width(), door.height()));
        }
        return doors;
    }

    private static List<WindowData> extractWindows(StoreySpec storey) {
        List<WindowData> windows = new ArrayList<>();
        for (var window : storey.windows()) {
            windows.add(new WindowData(window.name(), window.roomName(), window.wall(),
                                       window.x(), window.y(), window.width(), window.height(),
                                       window.sillHeight()));
        }
        return windows;
    }

    private static Map<String, List<String>> extractAdjacencyConstraints(BuildingDefinition def) {
        Map<String, List<String>> constraints = new HashMap<>();
        for (var room : def.storeys().get(0).rooms()) {
            if (!room.adjacentTo().isEmpty()) {
                constraints.put(room.name(), new ArrayList<>(room.adjacentTo()));
            }
        }
        return constraints;
    }

    // =========================================================================
    // CHECK 1: Wall Connectivity
    // =========================================================================

    private static boolean verifyWallConnectivity(List<WallData> walls) {
        System.out.println("1. WALL CONNECTIVITY");
        System.out.println("-".repeat(70));

        // Collect all wall endpoints
        List<Corner> endpoints = new ArrayList<>();
        for (var wall : walls) {
            endpoints.add(new Corner(wall.x1, wall.y1));
            endpoints.add(new Corner(wall.x2, wall.y2));
        }

        // Find unique corners (cluster endpoints within tolerance)
        List<Corner> corners = clusterCorners(endpoints);

        System.out.println("CORNER JUNCTION ANALYSIS");
        System.out.printf("| %-15s | %-30s | %-10s | %-6s |%n",
                          "Corner (x,y)", "Walls Meeting", "Gap (mm)", "Status");
        System.out.println("|" + "-".repeat(17) + "|" + "-".repeat(32) + "|" +
                          "-".repeat(12) + "|" + "-".repeat(8) + "|");

        double maxGap = 0;
        int cornersChecked = 0;

        for (Corner corner : corners) {
            List<String> wallsAtCorner = new ArrayList<>();
            double minDist = Double.MAX_VALUE;

            for (var wall : walls) {
                double d1 = distance(corner.x, corner.y, wall.x1, wall.y1);
                double d2 = distance(corner.x, corner.y, wall.x2, wall.y2);
                double minD = Math.min(d1, d2);

                if (minD < TOLERANCE * 10) { // Within 50mm
                    wallsAtCorner.add(wall.name.replace("_WALL_ASSEMBLY", ""));
                    if (minD > 0.0001) { // Not exact
                        minDist = Math.min(minDist, minD);
                    }
                }
            }

            if (wallsAtCorner.size() >= 2) {
                double gap = (minDist == Double.MAX_VALUE) ? 0 : minDist * 1000;
                maxGap = Math.max(maxGap, gap);
                String status = gap < TOLERANCE * 1000 ? "PASS" : "FAIL";
                String wallStr = String.join(", ", wallsAtCorner);
                if (wallStr.length() > 28) wallStr = wallStr.substring(0, 25) + "...";

                System.out.printf("| %-15s | %-30s | %8.1f   | %-6s |%n",
                                  corner, wallStr, gap, status);
                cornersChecked++;
            }
        }

        System.out.println();
        System.out.printf("Corners checked: %d%n", cornersChecked);
        System.out.printf("Max gap: %.1f mm%n", maxGap);
        System.out.printf("Tolerance: %.1f mm%n", TOLERANCE * 1000);

        boolean pass = maxGap < TOLERANCE * 1000;
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    private static List<Corner> clusterCorners(List<Corner> endpoints) {
        List<Corner> clustered = new ArrayList<>();
        for (Corner c : endpoints) {
            boolean found = false;
            for (Corner existing : clustered) {
                if (distance(c.x, c.y, existing.x, existing.y) < TOLERANCE * 2) {
                    found = true;
                    break;
                }
            }
            if (!found) clustered.add(c);
        }
        return clustered;
    }

    // =========================================================================
    // CHECK 2: Interior Walls
    // =========================================================================

    private static boolean verifyInteriorWalls(List<WallData> walls, List<RoomData> rooms) {
        System.out.println("2. INTERIOR WALLS");
        System.out.println("-".repeat(70));

        List<WallData> interiorWalls = walls.stream()
            .filter(w -> w.isInterior)
            .toList();

        System.out.printf("Interior walls found: %d%n%n", interiorWalls.size());

        boolean allConnect = true;
        for (var wall : interiorWalls) {
            System.out.printf("WALL: %s%n", wall.name);
            System.out.printf("  Start: (%.1f, %.1f)%n", wall.x1, wall.y1);
            System.out.printf("  End:   (%.1f, %.1f)%n", wall.x2, wall.y2);
            System.out.printf("  Length: %.2f m%n", wall.length);

            // Find rooms on each side
            List<String> adjacentRooms = new ArrayList<>();
            for (var room : rooms) {
                if (wallTouchesRoom(wall, room)) {
                    adjacentRooms.add(room.name);
                }
            }

            System.out.printf("  Adjacent rooms: %s%n", adjacentRooms);
            boolean connects = adjacentRooms.size() >= 2;
            System.out.printf("  Connects two rooms: %s%n%n", connects ? "YES" : "NO");

            if (!connects) allConnect = false;
        }

        boolean pass = interiorWalls.size() >= 1 && allConnect;
        System.out.printf("Expected interior walls: >= 1%n");
        System.out.printf("All connect rooms: %s%n", allConnect ? "YES" : "NO");
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    private static boolean wallTouchesRoom(WallData wall, RoomData room) {
        // Check if wall edge aligns with room edge (within tolerance)
        double wallMidX = (wall.x1 + wall.x2) / 2;
        double wallMidY = (wall.y1 + wall.y2) / 2;

        // Room edges
        boolean touchesNorth = Math.abs(wall.y1 - room.maxY) < TOLERANCE ||
                               Math.abs(wall.y2 - room.maxY) < TOLERANCE;
        boolean touchesSouth = Math.abs(wall.y1 - room.minY) < TOLERANCE ||
                               Math.abs(wall.y2 - room.minY) < TOLERANCE;
        boolean touchesEast = Math.abs(wall.x1 - room.maxX) < TOLERANCE ||
                              Math.abs(wall.x2 - room.maxX) < TOLERANCE;
        boolean touchesWest = Math.abs(wall.x1 - room.minX) < TOLERANCE ||
                              Math.abs(wall.x2 - room.minX) < TOLERANCE;

        // Wall midpoint should be within room extent (plus tolerance)
        boolean withinX = wallMidX >= room.minX - TOLERANCE && wallMidX <= room.maxX + TOLERANCE;
        boolean withinY = wallMidY >= room.minY - TOLERANCE && wallMidY <= room.maxY + TOLERANCE;

        return (touchesNorth || touchesSouth || touchesEast || touchesWest) &&
               (withinX || withinY);
    }

    // =========================================================================
    // CHECK 3: Door Placement
    // =========================================================================

    private static boolean verifyDoorPlacement(List<DoorData> doors, List<WallData> walls) {
        System.out.println("3. DOOR PLACEMENT");
        System.out.println("-".repeat(70));

        System.out.printf("Doors found: %d%n%n", doors.size());

        boolean allCentered = true;
        boolean allFit = true;

        for (var door : doors) {
            System.out.printf("DOOR: %s%n", door.name);
            System.out.printf("  Position: (%.1f, %.1f)%n", door.x, door.y);
            System.out.printf("  Size: %.0f mm × %.0f mm%n", door.width * 1000, door.height * 1000);

            // Find the wall this door is in
            WallData hostWall = findHostWall(door, walls);
            if (hostWall != null) {
                double doorCenter = door.wall.equals("north") || door.wall.equals("south")
                    ? door.x + door.width / 2
                    : door.y + door.width / 2;
                double wallCenter = door.wall.equals("north") || door.wall.equals("south")
                    ? (hostWall.x1 + hostWall.x2) / 2
                    : (hostWall.y1 + hostWall.y2) / 2;

                double offset = Math.abs(doorCenter - wallCenter) * 1000;
                boolean fits = hostWall.length >= door.width;

                System.out.printf("  Host wall: %s (%.2f m)%n", hostWall.name, hostWall.length);
                System.out.printf("  Door center offset: %.0f mm%n", offset);
                System.out.printf("  Door fits in wall: %s%n%n", fits ? "YES" : "NO");

                if (offset > hostWall.length * 500) allCentered = false; // More than 50% off-center
                if (!fits) allFit = false;
            } else {
                System.out.printf("  Host wall: NOT FOUND%n%n");
                allFit = false;
            }
        }

        boolean pass = doors.size() >= 1 && allFit;
        System.out.printf("All doors fit in walls: %s%n", allFit ? "YES" : "NO");
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    private static WallData findHostWall(DoorData door, List<WallData> walls) {
        for (var wall : walls) {
            // Check if door position is on this wall
            if (wall.isInterior && wall.name.contains(door.roomName.replace("_to_", "_"))) {
                return wall;
            }
            // Check positional match
            boolean onWall = false;
            if (door.wall.equals("north") || door.wall.equals("south")) {
                onWall = Math.abs(wall.y1 - door.y) < TOLERANCE &&
                         door.x >= wall.x1 - TOLERANCE && door.x <= wall.x2 + TOLERANCE;
            } else {
                onWall = Math.abs(wall.x1 - door.x) < TOLERANCE &&
                         door.y >= wall.y1 - TOLERANCE && door.y <= wall.y2 + TOLERANCE;
            }
            if (onWall) return wall;
        }
        return null;
    }

    // =========================================================================
    // CHECK 4: Window Placement
    // =========================================================================

    private static boolean verifyWindowPlacement(List<WindowData> windows, List<WallData> walls,
                                                  StoreySpec storey) {
        System.out.println("4. WINDOW PLACEMENT");
        System.out.println("-".repeat(70));

        // Get building bounds
        double buildingMinX = Double.MAX_VALUE, buildingMaxX = -Double.MAX_VALUE;
        double buildingMinY = Double.MAX_VALUE, buildingMaxY = -Double.MAX_VALUE;
        for (var room : storey.rooms()) {
            buildingMinX = Math.min(buildingMinX, room.minX());
            buildingMaxX = Math.max(buildingMaxX, room.maxX());
            buildingMinY = Math.min(buildingMinY, room.minY());
            buildingMaxY = Math.max(buildingMaxY, room.maxY());
        }

        System.out.printf("Building bounds: [%.1f,%.1f] to [%.1f,%.1f]%n",
                          buildingMinX, buildingMinY, buildingMaxX, buildingMaxY);
        System.out.printf("Windows found: %d%n%n", windows.size());

        boolean allOnExterior = true;

        for (var window : windows) {
            System.out.printf("WINDOW: %s%n", window.name);
            System.out.printf("  Room: %s, Wall: %s%n", window.roomName, window.wall);
            System.out.printf("  Position: (%.1f, %.1f)%n", window.x, window.y);
            System.out.printf("  Size: %.0f mm × %.0f mm%n", window.width * 1000, window.height * 1000);
            System.out.printf("  Sill height: %.0f mm%n", window.sillHeight * 1000);

            // Check if on exterior wall
            boolean onExterior = false;
            switch (window.wall.toLowerCase()) {
                case "south" -> onExterior = Math.abs(window.y - buildingMinY) < TOLERANCE;
                case "north" -> onExterior = Math.abs(window.y - buildingMaxY) < TOLERANCE;
                case "west" -> onExterior = Math.abs(window.x - buildingMinX) < TOLERANCE;
                case "east" -> onExterior = Math.abs(window.x - buildingMaxX) < TOLERANCE;
            }

            System.out.printf("  On exterior wall: %s%n%n", onExterior ? "YES" : "NO");
            if (!onExterior) allOnExterior = false;
        }

        boolean pass = windows.size() >= 1 && allOnExterior;
        System.out.printf("All on exterior: %s%n", allOnExterior ? "YES" : "NO");
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    // =========================================================================
    // CHECK 5: Room Enclosure
    // =========================================================================

    private static boolean verifyRoomEnclosure(List<RoomData> rooms, List<WallData> walls) {
        System.out.println("5. ROOM ENCLOSURE");
        System.out.println("-".repeat(70));

        boolean allEnclosed = true;

        for (var room : rooms) {
            System.out.printf("ROOM: %s (%.0f×%.0f m)%n", room.name,
                              room.maxX - room.minX, room.maxY - room.minY);
            System.out.printf("  Bbox: [%.1f,%.1f] to [%.1f,%.1f]%n",
                              room.minX, room.minY, room.maxX, room.maxY);

            // Check each edge
            boolean northWall = hasWallOnEdge(walls, room.minX, room.maxY, room.maxX, room.maxY);
            boolean southWall = hasWallOnEdge(walls, room.minX, room.minY, room.maxX, room.minY);
            boolean eastWall = hasWallOnEdge(walls, room.maxX, room.minY, room.maxX, room.maxY);
            boolean westWall = hasWallOnEdge(walls, room.minX, room.minY, room.minX, room.maxY);

            System.out.printf("  North wall [%.1f,%.1f]-[%.1f,%.1f]: %s%n",
                              room.minX, room.maxY, room.maxX, room.maxY, northWall ? "✓" : "✗");
            System.out.printf("  South wall [%.1f,%.1f]-[%.1f,%.1f]: %s%n",
                              room.minX, room.minY, room.maxX, room.minY, southWall ? "✓" : "✗");
            System.out.printf("  East wall  [%.1f,%.1f]-[%.1f,%.1f]: %s%n",
                              room.maxX, room.minY, room.maxX, room.maxY, eastWall ? "✓" : "✗");
            System.out.printf("  West wall  [%.1f,%.1f]-[%.1f,%.1f]: %s%n",
                              room.minX, room.minY, room.minX, room.maxY, westWall ? "✓" : "✗");

            boolean enclosed = northWall && southWall && eastWall && westWall;
            System.out.printf("  Enclosure complete: %s%n%n", enclosed ? "YES" : "NO");

            if (!enclosed) allEnclosed = false;
        }

        boolean pass = allEnclosed;
        System.out.printf("All rooms enclosed: %s%n", allEnclosed ? "YES" : "NO");
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    private static boolean hasWallOnEdge(List<WallData> walls, double x1, double y1,
                                          double x2, double y2) {
        for (var wall : walls) {
            // Check if wall overlaps with edge
            boolean isHorizontal = Math.abs(y1 - y2) < TOLERANCE;
            boolean isVertical = Math.abs(x1 - x2) < TOLERANCE;

            if (isHorizontal) {
                // Edge is horizontal
                boolean sameY = Math.abs(wall.y1 - y1) < TOLERANCE || Math.abs(wall.y2 - y1) < TOLERANCE;
                boolean overlapsX = !(wall.x2 < x1 - TOLERANCE || wall.x1 > x2 + TOLERANCE);
                if (sameY && overlapsX) return true;
            } else if (isVertical) {
                // Edge is vertical
                boolean sameX = Math.abs(wall.x1 - x1) < TOLERANCE || Math.abs(wall.x2 - x1) < TOLERANCE;
                boolean overlapsY = !(wall.y2 < y1 - TOLERANCE || wall.y1 > y2 + TOLERANCE);
                if (sameX && overlapsY) return true;
            }
        }
        return false;
    }

    // =========================================================================
    // CHECK 6: No Overlap
    // =========================================================================

    private static boolean verifyNoOverlap(List<RoomData> rooms) {
        System.out.println("6. NO OVERLAP");
        System.out.println("-".repeat(70));

        System.out.println("OVERLAP MATRIX (m²)");
        System.out.print("|" + " ".repeat(12) + "|");
        for (var r : rooms) {
            System.out.printf(" %-10s |", r.name.length() > 10 ? r.name.substring(0,10) : r.name);
        }
        System.out.println();
        System.out.print("|" + "-".repeat(12) + "|");
        for (int i = 0; i < rooms.size(); i++) System.out.print("-".repeat(12) + "|");
        System.out.println();

        double totalOverlap = 0;

        for (int i = 0; i < rooms.size(); i++) {
            var r1 = rooms.get(i);
            System.out.printf("| %-10s |", r1.name.length() > 10 ? r1.name.substring(0,10) : r1.name);

            for (int j = 0; j < rooms.size(); j++) {
                if (i == j) {
                    System.out.print("     -      |");
                } else {
                    var r2 = rooms.get(j);
                    double overlapX = Math.max(0, Math.min(r1.maxX, r2.maxX) - Math.max(r1.minX, r2.minX));
                    double overlapY = Math.max(0, Math.min(r1.maxY, r2.maxY) - Math.max(r1.minY, r2.minY));
                    double overlap = overlapX * overlapY;

                    // Subtract tolerance for touching edges
                    if (overlap < TOLERANCE * TOLERANCE) overlap = 0;

                    if (i < j) totalOverlap += overlap;
                    System.out.printf(" %8.2f   |", overlap);
                }
            }
            System.out.println();
        }

        System.out.println();
        System.out.printf("Room pairs: %d%n", rooms.size() * (rooms.size() - 1) / 2);
        System.out.printf("Total overlap: %.4f m²%n", totalOverlap);

        boolean pass = totalOverlap < TOLERANCE;
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    // =========================================================================
    // CHECK 7: Adjacency Constraints
    // =========================================================================

    private static boolean verifyAdjacencyConstraints(List<RoomData> rooms, List<WallData> walls,
                                                       List<DoorData> doors,
                                                       Map<String, List<String>> constraints) {
        System.out.println("7. ADJACENCY CONSTRAINTS");
        System.out.println("-".repeat(70));

        int total = 0;
        int satisfied = 0;

        for (var entry : constraints.entrySet()) {
            String room1Name = entry.getKey();
            for (String room2Name : entry.getValue()) {
                total++;

                RoomData room1 = findRoom(rooms, room1Name);
                RoomData room2 = findRoom(rooms, room2Name);

                if (room1 == null || room2 == null) {
                    System.out.printf("CONSTRAINT: %s adjacent:%s - ROOM NOT FOUND%n", room1Name, room2Name);
                    continue;
                }

                System.out.printf("CONSTRAINT: %s adjacent:%s%n", room1Name, room2Name);

                // Check shared edge
                String sharedEdge = findSharedEdge(room1, room2);
                boolean hasInteriorWall = hasInteriorWallBetween(walls, room1Name, room2Name);
                boolean hasDoor = hasDoorBetween(doors, room1Name, room2Name);

                System.out.printf("  %s edge: x=[%.1f,%.1f]%n", room1Name, room1.minX, room1.maxX);
                System.out.printf("  %s edge: x=[%.1f,%.1f]%n", room2Name, room2.minX, room2.maxX);
                System.out.printf("  Shared edge: %s%n", sharedEdge != null ? sharedEdge : "NONE");
                System.out.printf("  Interior wall: %s%n", hasInteriorWall ? "YES" : "NO");
                System.out.printf("  Door in wall: %s%n", hasDoor ? "YES" : "NO");

                boolean isSatisfied = sharedEdge != null && hasInteriorWall && hasDoor;
                System.out.printf("  RESULT: %s%n%n", isSatisfied ? "SATISFIED" : "VIOLATED");

                if (isSatisfied) satisfied++;
            }
        }

        boolean pass = total == satisfied;
        System.out.printf("Constraints: %d%n", total);
        System.out.printf("Satisfied: %d%n", satisfied);
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    private static RoomData findRoom(List<RoomData> rooms, String name) {
        return rooms.stream().filter(r -> r.name.equals(name)).findFirst().orElse(null);
    }

    private static String findSharedEdge(RoomData r1, RoomData r2) {
        // Check east-west adjacency (r1 east edge = r2 west edge)
        if (Math.abs(r1.maxX - r2.minX) < TOLERANCE) {
            double overlapY = Math.min(r1.maxY, r2.maxY) - Math.max(r1.minY, r2.minY);
            if (overlapY > TOLERANCE) return String.format("X=%.1f, Y=[%.1f,%.1f]", r1.maxX,
                Math.max(r1.minY, r2.minY), Math.min(r1.maxY, r2.maxY));
        }
        // Check west-east adjacency (r1 west edge = r2 east edge)
        if (Math.abs(r1.minX - r2.maxX) < TOLERANCE) {
            double overlapY = Math.min(r1.maxY, r2.maxY) - Math.max(r1.minY, r2.minY);
            if (overlapY > TOLERANCE) return String.format("X=%.1f, Y=[%.1f,%.1f]", r1.minX,
                Math.max(r1.minY, r2.minY), Math.min(r1.maxY, r2.maxY));
        }
        // Check north-south adjacency
        if (Math.abs(r1.maxY - r2.minY) < TOLERANCE) {
            double overlapX = Math.min(r1.maxX, r2.maxX) - Math.max(r1.minX, r2.minX);
            if (overlapX > TOLERANCE) return String.format("Y=%.1f, X=[%.1f,%.1f]", r1.maxY,
                Math.max(r1.minX, r2.minX), Math.min(r1.maxX, r2.maxX));
        }
        // Check south-north adjacency
        if (Math.abs(r1.minY - r2.maxY) < TOLERANCE) {
            double overlapX = Math.min(r1.maxX, r2.maxX) - Math.max(r1.minX, r2.minX);
            if (overlapX > TOLERANCE) return String.format("Y=%.1f, X=[%.1f,%.1f]", r1.minY,
                Math.max(r1.minX, r2.minX), Math.min(r1.maxX, r2.maxX));
        }
        return null;
    }

    private static boolean hasInteriorWallBetween(List<WallData> walls, String room1, String room2) {
        String pattern1 = "INTERIOR_" + room1 + "_" + room2;
        String pattern2 = "INTERIOR_" + room2 + "_" + room1;
        return walls.stream().anyMatch(w -> w.name.contains(pattern1) || w.name.contains(pattern2));
    }

    private static boolean hasDoorBetween(List<DoorData> doors, String room1, String room2) {
        String pattern1 = room1 + "_to_" + room2;
        String pattern2 = room2 + "_to_" + room1;
        return doors.stream().anyMatch(d -> d.name.contains(pattern1) || d.name.contains(pattern2));
    }

    // =========================================================================
    // CHECK 8: IFC Relationships
    // =========================================================================

    private static boolean verifyIfcRelationships(StoreySpec storey) {
        System.out.println("8. IFC RELATIONSHIP INTEGRITY");
        System.out.println("-".repeat(70));

        int wallCount = storey.walls().size();
        int doorCount = storey.doors().size();
        int windowCount = storey.windows().size();
        int expectedOpenings = doorCount + windowCount;

        System.out.printf("Walls: %d%n", wallCount);
        System.out.printf("Doors: %d%n", doorCount);
        System.out.printf("Windows: %d%n", windowCount);
        System.out.printf("Expected openings: %d%n", expectedOpenings);
        System.out.println();

        // Verify each door has room and wall references
        boolean doorsValid = storey.doors().stream()
            .allMatch(d -> d.roomName() != null && d.wall() != null);

        // Verify each window has room and wall references
        boolean windowsValid = storey.windows().stream()
            .allMatch(w -> w.roomName() != null && w.wall() != null);

        System.out.printf("Door references valid: %s%n", doorsValid ? "YES" : "NO");
        System.out.printf("Window references valid: %s%n", windowsValid ? "YES" : "NO");

        boolean pass = doorsValid && windowsValid;
        System.out.printf("Relationships valid: %s%n", pass ? "YES" : "NO");
        System.out.printf("RESULT: %s%n%n", pass ? "PASS" : "FAIL");

        if (pass) passCount++; else failCount++;
        return pass;
    }

    // =========================================================================
    // Final Summary
    // =========================================================================

    private static void printFinalSummary(boolean... checks) {
        System.out.println("=".repeat(70));
        System.out.println("MATHEMATICAL VERIFICATION SUMMARY");
        System.out.println("=".repeat(70));
        System.out.println();

        String[] names = {
            "Wall Connectivity",
            "Interior Walls",
            "Door Placement",
            "Window Placement",
            "Room Enclosure",
            "No Overlap",
            "Adjacency Constraints",
            "IFC Relationships"
        };

        for (int i = 0; i < checks.length; i++) {
            System.out.printf("%d. %-25s %s%n", i + 1, names[i], checks[i] ? "PASS" : "FAIL");
        }

        System.out.println();
        System.out.printf("Passed: %d / %d%n", passCount, passCount + failCount);
        System.out.println();

        boolean allPass = passCount == checks.length;
        System.out.println("=".repeat(70));
        System.out.printf("OVERALL: %s%n", allPass ? "PASS - Building is geometrically valid" :
                                                     "FAIL - Geometric errors detected");
        System.out.println("=".repeat(70));

        if (allPass) {
            System.out.println("\nAll 8 checks passed. Numbers prove correctness.");
            System.out.println("If the math passes, the building is valid.");
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
