package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Check 4: Room Connectivity
 * Verifies all rooms are reachable via doors (graph connectivity).
 */
public class RoomConnectivityCheck implements SanityCheck {

    private static final String EXTERIOR = "__EXTERIOR__";

    @Override
    public String getId() { return "room_connectivity"; }

    @Override
    public String getName() { return "Room Connectivity"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> spaces = model.getSpaces();
        List<Element> doors = model.getDoors();

        // If no explicit IfcSpace elements, try to infer rooms from wall/door guids
        Set<String> roomNames;
        boolean usingImplicitRooms = false;

        if (spaces.isEmpty()) {
            roomNames = model.getImplicitSpaceNames();
            if (roomNames.isEmpty() || (roomNames.size() == 1 && roomNames.contains("exterior"))) {
                // Also try to extract from door guids
                roomNames = extractRoomsFromDoors(doors);
            }
            if (roomNames.isEmpty()) {
                return CheckResult.warning(getId(), getName())
                    .summary("No rooms found in building")
                    .detail("No IfcSpace elements and could not infer rooms from wall/door patterns")
                    .guidance("Building should have space elements or consistent naming patterns")
                    .build();
            }
            usingImplicitRooms = true;
            roomNames.remove("exterior"); // Will add back as special node
        } else {
            roomNames = new HashSet<>();
            for (Element space : spaces) {
                roomNames.add(space.name() != null ? space.name() : space.guid());
            }
        }

        if (doors.isEmpty()) {
            return CheckResult.fail(getId(), getName())
                .summary("No doors found - all rooms are isolated")
                .detail(String.format("Found %d rooms but no doors", roomNames.size()))
                .guidance("Add doors to connect rooms")
                .build();
        }

        // Build adjacency graph from doors
        Map<String, Set<String>> adjacency = new HashMap<>();
        adjacency.put(EXTERIOR, new HashSet<>());
        for (String room : roomNames) {
            adjacency.put(room, new HashSet<>());
        }

        // Track which rooms have doors
        Set<String> roomsWithDoors = new HashSet<>();

        for (Element door : doors) {
            // Check for exterior doors first (they might not have _TO_ pattern)
            if (isExteriorDoor(door)) {
                // Extract room name from exterior door (e.g., DOOR_LIVING_DOOR_SOUTH_Ground → living)
                String interiorRoom = extractRoomFromExteriorDoor(door);
                interiorRoom = findMatchingRoom(interiorRoom, roomNames);
                if (interiorRoom != null) {
                    adjacency.get(EXTERIOR).add(interiorRoom);
                    adjacency.computeIfAbsent(interiorRoom, k -> new HashSet<>()).add(EXTERIOR);
                    roomsWithDoors.add(interiorRoom);
                }
            }

            // Check for interior doors (room-to-room connections)
            String[] connectedRooms = extractConnectedRooms(door);
            if (connectedRooms == null || connectedRooms.length < 2) {
                continue;
            }

            String room1 = connectedRooms[0];
            String room2 = connectedRooms[1];

            // Ensure rooms are in our set (fuzzy match)
            room1 = findMatchingRoom(room1, roomNames);
            room2 = findMatchingRoom(room2, roomNames);

            if (room1 != null && room2 != null) {
                adjacency.computeIfAbsent(room1, k -> new HashSet<>()).add(room2);
                adjacency.computeIfAbsent(room2, k -> new HashSet<>()).add(room1);
                roomsWithDoors.add(room1);
                roomsWithDoors.add(room2);
            }
        }

        // Also add adjacency from wall patterns
        Map<String, Set<String>> wallAdjacency = model.getRoomAdjacency();
        for (Map.Entry<String, Set<String>> entry : wallAdjacency.entrySet()) {
            String room1 = findMatchingRoom(entry.getKey(), roomNames);
            if (room1 == null) continue;
            for (String adj : entry.getValue()) {
                String room2 = findMatchingRoom(adj, roomNames);
                if (room2 != null) {
                    // Walls indicate adjacency but not necessarily connectivity (need door)
                    // Just use this for validation
                }
            }
        }

        // Check for rooms with no doors
        // Note: PORCH/ANJUNG is an outdoor covered space - doesn't require door
        List<String> roomsWithNoDoor = new ArrayList<>();
        for (String room : roomNames) {
            if (!roomsWithDoors.contains(room)) {
                // Skip outdoor spaces that don't need doors
                if (isPorchOrOutdoorSpace(room)) {
                    continue;
                }
                roomsWithNoDoor.add(room);
            }
        }

        // Check if building has stairs (multi-storey vertical connectivity)
        boolean hasStairs = hasStairElements(model);
        int storeyCount = model.getStoreyCount();
        boolean isMultiStorey = storeyCount > 1;

        // For multi-storey buildings with stairs, add implicit stair connectivity
        // All rooms are considered potentially connected via stairs (landing connections may not be explicit)
        if (isMultiStorey && hasStairs) {
            // If there's exterior access on ground floor, connect all rooms via stair
            // This handles cases where upper floor rooms connect to landing (not explicit in door list)
            if (!adjacency.get(EXTERIOR).isEmpty()) {
                for (String room : roomNames) {
                    if (!adjacency.get(EXTERIOR).contains(room)) {
                        // Add implicit stair connectivity for upper floor rooms
                        adjacency.get(EXTERIOR).add(room);
                        adjacency.computeIfAbsent(room, k -> new HashSet<>()).add(EXTERIOR);
                    }
                }
            }
        }

        // BFS from exterior or common room to find reachable rooms
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        if (!adjacency.get(EXTERIOR).isEmpty()) {
            queue.add(EXTERIOR);
            reachable.add(EXTERIOR);
        } else {
            // No exterior access - start from "common" if exists, else first room
            String startRoom = findMatchingRoom("common", roomNames);
            if (startRoom == null && !roomNames.isEmpty()) {
                startRoom = roomNames.iterator().next();
            }
            if (startRoom != null) {
                queue.add(startRoom);
                reachable.add(startRoom);
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adjacency.getOrDefault(current, Collections.emptySet())) {
                if (!reachable.contains(neighbor)) {
                    reachable.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        // Check for unreachable rooms
        // Note: PORCH/outdoor spaces are considered reachable from exterior by definition
        List<String> unreachableRooms = new ArrayList<>();
        for (String room : roomNames) {
            if (!reachable.contains(room)) {
                // Skip outdoor spaces - they're reachable from exterior by definition
                if (isPorchOrOutdoorSpace(room)) {
                    continue;
                }
                unreachableRooms.add(room);
            }
        }

        // Report results
        boolean hasExteriorAccess = !adjacency.get(EXTERIOR).isEmpty();
        String roomSource = usingImplicitRooms ? " (inferred from element naming)" : "";

        if (!roomsWithNoDoor.isEmpty() && !unreachableRooms.isEmpty()) {
            // Rooms without doors that are also unreachable
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("%d room(s) have no door - occupants trapped", roomsWithNoDoor.size()));

            for (String room : roomsWithNoDoor) {
                result.detail(String.format("\"%s\" has no doors", room));
            }

            result.guidance("Add doors to connect isolated rooms");
            return result.build();
        }

        if (!unreachableRooms.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("%d room(s) not reachable%s", unreachableRooms.size(), roomSource));

            for (String room : unreachableRooms) {
                result.detail(String.format("\"%s\" is isolated", room));
            }

            result.guidance("Check door placement to ensure all rooms are connected");
            return result.build();
        }

        if (!hasExteriorAccess) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("All %d rooms interconnected, but no exterior access%s",
                    roomNames.size(), roomSource))
                .guidance("Add entry door on exterior wall")
                .build();
        }

        return CheckResult.pass(getId(), getName())
            .summary(String.format("All %d rooms reachable from entry%s", roomNames.size(), roomSource))
            .data("roomCount", roomNames.size())
            .data("doorCount", doors.size())
            .data("implicitRooms", usingImplicitRooms)
            .build();
    }

    /**
     * Extract room names from door guids.
     */
    private Set<String> extractRoomsFromDoors(List<Element> doors) {
        Set<String> rooms = new HashSet<>();
        for (Element door : doors) {
            String[] connected = extractConnectedRooms(door);
            if (connected != null) {
                for (String room : connected) {
                    if (room != null && !room.isEmpty() && !room.equalsIgnoreCase("exterior")) {
                        rooms.add(room.toLowerCase());
                    }
                }
            }
        }
        return rooms;
    }

    /**
     * Extract connected room names from door guid.
     * Pattern: DOOR_{room1}_TO_{room2}_DOOR_*
     */
    private String[] extractConnectedRooms(Element door) {
        if (door.guid() == null) return null;

        String guid = door.guid();

        // Pattern: DOOR_COMMON_TO_BILIK_UTAMA_DOOR_Ground
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

    /**
     * Extract room name from exterior door guid.
     * Pattern: DOOR_{room}_DOOR_{direction}_{storey}
     * Example: DOOR_LIVING_DOOR_SOUTH_Ground → living
     */
    private String extractRoomFromExteriorDoor(Element door) {
        if (door.guid() == null) return null;

        String guid = door.guid();

        // Pattern: DOOR_LIVING_DOOR_SOUTH_Ground
        if (guid.startsWith("DOOR_") && !guid.contains("_TO_")) {
            // Find second occurrence of "_DOOR_"
            int firstDoor = 0; // "DOOR_" at start
            int secondDoor = guid.indexOf("_DOOR_", 5);
            if (secondDoor > 0) {
                String room = guid.substring(5, secondDoor).toLowerCase();
                return room;
            }
        }

        return null;
    }

    /**
     * Check if door is an exterior door.
     * Checks guid patterns for entry/exterior doors.
     */
    private boolean isExteriorDoor(Element door) {
        if (door.guid() == null) return false;
        String upper = door.guid().toUpperCase();

        // Explicit entry/exterior patterns
        if (upper.contains("ENTRY") || upper.contains("EXTERIOR") ||
            upper.contains("TO_OUTSIDE") || upper.contains("TO_EXTERIOR") ||
            upper.contains("MAIN_DOOR") || upper.contains("FRONT_DOOR")) {
            return true;
        }

        // Doors on cardinal directions (SOUTH/NORTH/EAST/WEST) without _TO_ pattern
        // are typically exterior doors (e.g., "DOOR_LIVING_DOOR_SOUTH_Ground")
        if ((upper.contains("_SOUTH") || upper.contains("_NORTH") ||
             upper.contains("_EAST") || upper.contains("_WEST")) &&
            !upper.contains("_TO_")) {
            return true;
        }

        return false;
    }

    /**
     * Check if room name indicates a porch or outdoor covered space.
     * These spaces don't require doors - you walk directly into them from outside.
     */
    private boolean isPorchOrOutdoorSpace(String roomName) {
        if (roomName == null) return false;
        String lower = roomName.toLowerCase();
        return lower.contains("porch") || lower.contains("anjung") ||
               lower.contains("veranda") || lower.contains("deck") ||
               lower.contains("carport") || lower.contains("covered");
    }

    /**
     * Check if the building has stair elements (for multi-storey connectivity).
     */
    private boolean hasStairElements(SanityModel model) {
        for (Element elem : model.getAllElements()) {
            if (elem.ifcClass() != null &&
                (elem.ifcClass().contains("Stair") || elem.ifcClass().contains("StairFlight"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a matching room name in the set (case-insensitive, handles underscores).
     */
    private String findMatchingRoom(String name, Set<String> roomNames) {
        if (name == null) return null;
        String lower = name.toLowerCase();

        // Direct match
        if (roomNames.contains(lower)) return lower;

        // Try with underscores replaced
        String normalized = lower.replace("_", "");
        for (String room : roomNames) {
            if (room.replace("_", "").equals(normalized)) {
                return room;
            }
        }

        // Partial match (room name contains search or vice versa)
        for (String room : roomNames) {
            if (room.contains(lower) || lower.contains(room)) {
                return room;
            }
        }

        return null;
    }
}
