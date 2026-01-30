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
    public CheckResult execute(SanityModel model) {
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
            String[] connectedRooms = extractConnectedRooms(door);

            if (connectedRooms == null || connectedRooms.length < 2) {
                // Can't determine connectivity from this door
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

            // Check for exterior doors
            if (isExteriorDoor(door)) {
                String interiorRoom = room1;
                if (interiorRoom != null) {
                    adjacency.get(EXTERIOR).add(interiorRoom);
                    adjacency.get(interiorRoom).add(EXTERIOR);
                }
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
        List<String> roomsWithNoDoor = new ArrayList<>();
        for (String room : roomNames) {
            if (!roomsWithDoors.contains(room)) {
                roomsWithNoDoor.add(room);
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
        List<String> unreachableRooms = new ArrayList<>();
        for (String room : roomNames) {
            if (!reachable.contains(room)) {
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
     * Check if door is an exterior door.
     */
    private boolean isExteriorDoor(Element door) {
        if (door.guid() == null) return false;
        String upper = door.guid().toUpperCase();
        return upper.contains("ENTRY") || upper.contains("EXTERIOR") ||
               upper.contains("TO_OUTSIDE") || upper.contains("MAIN_DOOR");
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
