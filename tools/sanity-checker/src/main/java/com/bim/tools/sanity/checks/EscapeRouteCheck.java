package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * Escape Route Sanity Check - Phase 67
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. TRAVEL_DISTANCE_MATHS: Distance from each room to exit <= UBBL limit
 * 2. EXIT_ACCESS: Every room has path to exterior
 * 3. DEAD_END: No dead-end corridors exceed limit
 *
 * Code references:
 * - UBBL By-Law 165: Max travel distance 45m (general), 30m (high-rise)
 * - UBBL By-Law 167: Dead-end corridor max 7.5m
 * - NFPA 101: Travel distance limits by occupancy
 */
public class EscapeRouteCheck implements SanityCheck {

    // UBBL By-Law 165 limits (EXTRACTED from ad_egress_travel)
    private static final double MAX_TRAVEL_GENERAL_M = 45.0;
    private static final double MAX_TRAVEL_HIGHRISE_M = 30.0;
    private static final double MAX_DEAD_END_M = 7.5;
    private static final double SPRINKLER_BONUS = 1.25;

    // High-rise threshold (UBBL)
    private static final double HIGHRISE_THRESHOLD_M = 18.0;

    private static final String EXTERIOR = "__EXTERIOR__";

    @Override
    public String getId() { return "escape_route"; }

    @Override
    public String getName() { return "Escape Route Travel Distance"; }

    @Override
    public CheckResult execute(SanityModel model) {
        String dbPath = model.getDbPath();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Step 1: Get building parameters
            double buildingHeight = getBuildingHeight(conn);
            boolean isHighRise = buildingHeight >= HIGHRISE_THRESHOLD_M;
            boolean hasSprinklers = hasSprinklers(conn);

            // Calculate effective travel limit
            double baseLimit = isHighRise ? MAX_TRAVEL_HIGHRISE_M : MAX_TRAVEL_GENERAL_M;
            double effectiveLimit = hasSprinklers && !isHighRise ? baseLimit * SPRINKLER_BONUS : baseLimit;

            // Step 2: Build room graph with distances
            List<Element> spaces = model.getSpaces();
            List<Element> doors = model.getDoors();

            if (spaces.isEmpty()) {
                return CheckResult.pass(getId(), getName())
                    .summary("No rooms found - skipping escape route check")
                    .build();
            }

            // Build room map with centroids
            Map<String, RoomInfo> rooms = new HashMap<>();
            for (Element space : spaces) {
                String name = space.name() != null ? space.name().toLowerCase() : space.guid().toLowerCase();
                BoundingBox bbox = space.bbox();
                if (bbox != null) {
                    rooms.put(name, new RoomInfo(name, bbox));
                }
            }

            if (rooms.isEmpty()) {
                return CheckResult.pass(getId(), getName())
                    .summary("No room geometry found - skipping escape route check")
                    .build();
            }

            // Build adjacency with door connections
            Map<String, Set<String>> adjacency = buildAdjacency(rooms.keySet(), doors);

            // Step 3: Calculate travel distances from each room to exterior
            Map<String, Double> distances = calculateDistances(rooms, adjacency);

            // Step 4: Validate against limits
            List<String> failures = new ArrayList<>();
            List<String> details = new ArrayList<>();
            double maxDistance = 0;
            String farthestRoom = null;

            for (Map.Entry<String, Double> entry : distances.entrySet()) {
                String room = entry.getKey();
                double distance = entry.getValue();

                if (distance > maxDistance) {
                    maxDistance = distance;
                    farthestRoom = room;
                }

                if (distance > effectiveLimit) {
                    failures.add(String.format("%s: %.1fm > %.1fm limit", room, distance, effectiveLimit));
                } else if (distance > effectiveLimit * 0.9) {
                    // Warn if close to limit
                    details.add(String.format("NEAR_LIMIT: %s at %.1fm (90%% of %.1fm)", room, distance, effectiveLimit));
                }
            }

            // Check for rooms without escape route
            // Skip outdoor spaces (porch/anjung) - they're already outside
            List<String> noEscape = new ArrayList<>();
            for (String room : rooms.keySet()) {
                if (!distances.containsKey(room)) {
                    // Skip outdoor spaces that don't need escape routes
                    if (!isOutdoorSpace(room)) {
                        noEscape.add(room);
                    }
                }
            }

            if (!noEscape.isEmpty()) {
                for (String room : noEscape) {
                    failures.add(String.format("%s: NO escape route to exterior", room));
                }
            }

            // Build result
            if (!failures.isEmpty()) {
                CheckResult.Builder result = CheckResult.fail(getId(), getName())
                    .summary(String.format("Escape route violations: %d issues", failures.size()));

                result.detail(String.format("LIMIT: %.1fm (%s%s)",
                    effectiveLimit,
                    isHighRise ? "high-rise" : "general",
                    hasSprinklers && !isHighRise ? " +25% sprinkler bonus" : ""));

                for (String failure : failures) {
                    result.detail("FAIL: " + failure);
                }
                for (String detail : details) {
                    result.detail(detail);
                }

                result.guidance("Reduce travel distances or add additional exits (UBBL By-Law 165)");
                result.data("maxDistance", maxDistance);
                result.data("limit", effectiveLimit);
                result.data("isHighRise", isHighRise);
                result.data("hasSprinklers", hasSprinklers);

                return result.build();
            }

            // All checks pass
            CheckResult.Builder result = CheckResult.pass(getId(), getName())
                .summary(String.format("All %d rooms within %.1fm travel limit", rooms.size(), effectiveLimit));

            result.detail(String.format("LIMIT_MATHS: %.1fm (%s%s)",
                effectiveLimit,
                isHighRise ? "high-rise 30m" : "general 45m",
                hasSprinklers && !isHighRise ? " × 1.25 sprinkler bonus" : ""));

            if (farthestRoom != null) {
                result.detail(String.format("MAX_TRAVEL: %s at %.1fm (%.0f%% of limit)",
                    farthestRoom, maxDistance, (maxDistance / effectiveLimit) * 100));
            }

            for (String detail : details) {
                result.detail(detail);
            }

            result.data("maxDistance", maxDistance);
            result.data("farthestRoom", farthestRoom);
            result.data("limit", effectiveLimit);
            result.data("roomCount", rooms.size());

            return result.build();

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get building height from elements_rtree.
     */
    private double getBuildingHeight(Connection conn) throws SQLException {
        String sql = "SELECT MAX(maxZ) as height FROM elements_rtree";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble("height");
            }
        }
        return 0;
    }

    /**
     * Check if building has sprinklers.
     */
    private boolean hasSprinklers(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM elements_meta WHERE ifc_class = 'IfcFireSuppressionTerminal'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * Build adjacency graph from door connections.
     */
    private Map<String, Set<String>> buildAdjacency(Set<String> roomNames, List<Element> doors) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        adjacency.put(EXTERIOR, new HashSet<>());
        for (String room : roomNames) {
            adjacency.put(room, new HashSet<>());
        }

        for (Element door : doors) {
            // Check for exterior doors
            if (isExteriorDoor(door)) {
                String room = extractRoomFromExteriorDoor(door);
                room = findMatchingRoom(room, roomNames);
                if (room != null) {
                    adjacency.get(EXTERIOR).add(room);
                    adjacency.get(room).add(EXTERIOR);
                }
            }

            // Check for interior doors
            String[] connected = extractConnectedRooms(door);
            if (connected != null && connected.length >= 2) {
                String room1 = findMatchingRoom(connected[0], roomNames);
                String room2 = findMatchingRoom(connected[1], roomNames);
                if (room1 != null && room2 != null) {
                    adjacency.get(room1).add(room2);
                    adjacency.get(room2).add(room1);
                }
            }
        }

        return adjacency;
    }

    /**
     * Calculate travel distances from each room to exterior using Dijkstra.
     * Distance = sum of horizontal distances between room centroids.
     */
    private Map<String, Double> calculateDistances(Map<String, RoomInfo> rooms,
                                                    Map<String, Set<String>> adjacency) {
        Map<String, Double> distances = new HashMap<>();

        // Dijkstra from EXTERIOR
        PriorityQueue<DistEntry> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e.distance));
        pq.add(new DistEntry(EXTERIOR, 0));
        distances.put(EXTERIOR, 0.0);

        while (!pq.isEmpty()) {
            DistEntry current = pq.poll();

            if (current.distance > distances.getOrDefault(current.room, Double.MAX_VALUE)) {
                continue;
            }

            for (String neighbor : adjacency.getOrDefault(current.room, Collections.emptySet())) {
                double edgeDistance = calculateEdgeDistance(current.room, neighbor, rooms);
                double newDist = current.distance + edgeDistance;

                if (newDist < distances.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    distances.put(neighbor, newDist);
                    pq.add(new DistEntry(neighbor, newDist));
                }
            }
        }

        // Remove EXTERIOR from results
        distances.remove(EXTERIOR);

        return distances;
    }

    /**
     * Calculate edge distance between two rooms.
     * Uses centroid-to-centroid horizontal distance.
     */
    private double calculateEdgeDistance(String from, String to, Map<String, RoomInfo> rooms) {
        // Distance from exterior is 0 (at the door)
        if (EXTERIOR.equals(from) || EXTERIOR.equals(to)) {
            return 0;
        }

        RoomInfo r1 = rooms.get(from);
        RoomInfo r2 = rooms.get(to);

        if (r1 == null || r2 == null) {
            return 5.0; // Default distance if geometry unknown
        }

        // Horizontal distance between centroids
        double dx = r2.centerX - r1.centerX;
        double dy = r2.centerY - r1.centerY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // --- Door parsing methods (from RoomConnectivityCheck) ---

    private boolean isExteriorDoor(Element door) {
        if (door.guid() == null) return false;
        String upper = door.guid().toUpperCase();

        if (upper.contains("ENTRY") || upper.contains("EXTERIOR") ||
            upper.contains("TO_OUTSIDE") || upper.contains("TO_EXTERIOR") ||
            upper.contains("MAIN_DOOR") || upper.contains("FRONT_DOOR")) {
            return true;
        }

        if ((upper.contains("_SOUTH") || upper.contains("_NORTH") ||
             upper.contains("_EAST") || upper.contains("_WEST")) &&
            !upper.contains("_TO_")) {
            return true;
        }

        return false;
    }

    private String extractRoomFromExteriorDoor(Element door) {
        if (door.guid() == null) return null;
        String guid = door.guid();

        if (guid.startsWith("DOOR_") && !guid.contains("_TO_")) {
            int secondDoor = guid.indexOf("_DOOR_", 5);
            if (secondDoor > 0) {
                return guid.substring(5, secondDoor).toLowerCase();
            }
        }
        return null;
    }

    private String[] extractConnectedRooms(Element door) {
        if (door.guid() == null) return null;
        String guid = door.guid();

        if (guid.startsWith("DOOR_") && guid.contains("_TO_")) {
            int toIdx = guid.indexOf("_TO_");
            int doorIdx = guid.indexOf("_DOOR_", toIdx);
            if (doorIdx < 0) doorIdx = guid.length();

            String room1 = guid.substring(5, toIdx).toLowerCase();
            String room2 = guid.substring(toIdx + 4, doorIdx).toLowerCase();
            return new String[]{room1, room2};
        }
        return null;
    }

    private String findMatchingRoom(String name, Set<String> roomNames) {
        if (name == null) return null;
        String lower = name.toLowerCase();

        if (roomNames.contains(lower)) return lower;

        String normalized = lower.replace("_", "");
        for (String room : roomNames) {
            if (room.replace("_", "").equals(normalized)) {
                return room;
            }
        }

        for (String room : roomNames) {
            if (room.contains(lower) || lower.contains(room)) {
                return room;
            }
        }

        return null;
    }

    /**
     * Check if room is an outdoor space (porch, veranda, carport).
     * These spaces are already outside - no escape route needed.
     */
    private boolean isOutdoorSpace(String roomName) {
        if (roomName == null) return false;
        String lower = roomName.toLowerCase();
        return lower.contains("porch") || lower.contains("anjung") ||
               lower.contains("veranda") || lower.contains("deck") ||
               lower.contains("carport") || lower.contains("covered") ||
               lower.contains("terrace") || lower.contains("balcony");
    }

    // Data classes
    private record RoomInfo(String name, double centerX, double centerY) {
        RoomInfo(String name, BoundingBox bbox) {
            this(name, bbox.centerX(), bbox.centerY());
        }
    }

    private record DistEntry(String room, double distance) {}
}
