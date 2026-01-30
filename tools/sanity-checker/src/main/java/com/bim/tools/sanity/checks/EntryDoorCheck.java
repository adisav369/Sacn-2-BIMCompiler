package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Check 2: Entry Door
 * Verifies at least one door provides entry from outside.
 */
public class EntryDoorCheck implements SanityCheck {
    private static final double MIN_ENTRY_WIDTH = 0.8; // 800mm minimum accessible entry

    @Override
    public String getId() { return "entry_door"; }

    @Override
    public String getName() { return "Entry Door"; }

    @Override
    public CheckResult execute(SanityModel model) {
        List<Element> doors = model.getDoors();

        if (doors.isEmpty()) {
            return CheckResult.fail(getId(), getName())
                .summary("No doors found in building")
                .guidance("Add at least one entry door on exterior wall")
                .build();
        }

        List<Element> exteriorDoors = new ArrayList<>();
        List<String> internalDoorDescriptions = new ArrayList<>();

        for (Element door : doors) {
            // Check if door is an entry/exterior door by guid pattern or position
            if (isExteriorDoor(door, model)) {
                exteriorDoors.add(door);
            } else {
                // Extract room names from door guid if available
                String roomConnection = extractRoomConnection(door);
                internalDoorDescriptions.add(String.format("%s: %s (internal)",
                    door.guid(), roomConnection));
            }
        }

        if (exteriorDoors.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary("No entry door found")
                .detail(String.format("Found %d doors, but none touch building perimeter", doors.size()));

            for (String desc : internalDoorDescriptions) {
                result.detail(desc);
            }

            result.guidance("Add door on exterior wall (south or front-facing recommended for entry)");
            return result.build();
        }

        // Check if at least one exterior door meets minimum width
        // Door leaf width is the LARGER of width/depth (the smaller is door thickness)
        Element bestEntry = null;
        double bestWidth = 0;
        for (Element door : exteriorDoors) {
            double leafWidth = Math.max(door.width(), door.depth()); // Larger dimension is door leaf
            if (leafWidth > bestWidth) {
                bestWidth = leafWidth;
                bestEntry = door;
            }
        }

        if (bestWidth < MIN_ENTRY_WIDTH) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("Entry door too narrow (%.0fmm < %.0fmm minimum)",
                    bestWidth * 1000, MIN_ENTRY_WIDTH * 1000))
                .detail(String.format("Found %d exterior door(s), widest is %.0fmm",
                    exteriorDoors.size(), bestWidth * 1000))
                .guidance("Entry door should be at least 800mm wide for accessibility")
                .data("doorCount", exteriorDoors.size())
                .data("maxWidth", bestWidth)
                .build();
        }

        String doorId = bestEntry.name() != null ? bestEntry.name() : bestEntry.guid();
        double leafWidth = Math.max(bestEntry.width(), bestEntry.depth());
        String dimensions = String.format("%.0f×%.0fmm", leafWidth * 1000, bestEntry.height() * 1000);

        return CheckResult.pass(getId(), getName())
            .summary(String.format("Main entry %s (%s) on perimeter", doorId, dimensions))
            .data("doorId", doorId)
            .data("dimensions", new double[]{bestEntry.width(), bestEntry.height()})
            .data("exteriorDoorCount", exteriorDoors.size())
            .build();
    }

    /**
     * Check if a door is an exterior/entry door.
     * Checks guid patterns, porch adjacency, and geometric position.
     */
    private boolean isExteriorDoor(Element door, SanityModel model) {
        // Check guid patterns for exterior/entry doors
        if (door.guid() != null) {
            String upper = door.guid().toUpperCase();
            if (upper.contains("ENTRY") || upper.contains("EXTERIOR") ||
                upper.contains("MAIN_DOOR") || upper.contains("FRONT_DOOR")) {
                return true;
            }
            // Doors with "TO_EXTERIOR" or "TO_OUTSIDE" patterns
            if (upper.contains("TO_EXTERIOR") || upper.contains("TO_OUTSIDE")) {
                return true;
            }
            // Doors on south/north walls of common area (typical entry positions)
            // that are not between two interior rooms
            if ((upper.contains("_SOUTH") || upper.contains("_NORTH")) &&
                !upper.contains("_TO_")) {
                return true;
            }
        }

        // Check if door connects to a porch (outdoor covered space)
        // A door from interior to porch IS an entry door since porch is outdoor
        String[] rooms = extractConnectedRoomsFromGuid(door);
        if (rooms != null) {
            for (String room : rooms) {
                if (isPorchOrOutdoorSpace(room)) {
                    return true;
                }
            }
        }

        // Fall back to geometric check
        return model.isDoorOnPerimeter(door);
    }

    /**
     * Extract room names from door guid patterns.
     */
    private String[] extractConnectedRoomsFromGuid(Element door) {
        if (door.guid() == null) return null;
        String guid = door.guid();

        // Pattern: DOOR_{room1}_TO_{room2}_...
        if (guid.contains("_TO_")) {
            int start = guid.indexOf("DOOR_") + 5;
            int toIdx = guid.indexOf("_TO_");
            int end = guid.indexOf("_DOOR_", toIdx);
            if (end < 0) end = guid.indexOf("_Ground");
            if (end < 0) end = guid.length();

            String room1 = guid.substring(start, toIdx);
            String room2 = guid.substring(toIdx + 4, end);
            return new String[]{room1.toLowerCase(), room2.toLowerCase()};
        }

        return null;
    }

    /**
     * Check if room name indicates a porch or outdoor covered space.
     */
    private boolean isPorchOrOutdoorSpace(String roomName) {
        if (roomName == null) return false;
        String lower = roomName.toLowerCase();
        return lower.contains("porch") || lower.contains("anjung") ||
               lower.contains("veranda") || lower.contains("deck") ||
               lower.contains("carport") || lower.contains("covered");
    }

    /**
     * Extract room connection description from door guid.
     * Pattern: DOOR_{room1}_TO_{room2}_DOOR_*
     */
    private String extractRoomConnection(Element door) {
        if (door.guid() == null) {
            return "unknown rooms";
        }

        String guid = door.guid();
        // Pattern: DOOR_COMMON_TO_BILIK_UTAMA_DOOR_Ground
        if (guid.startsWith("DOOR_") && guid.contains("_TO_")) {
            int toIdx = guid.indexOf("_TO_");
            int doorIdx = guid.indexOf("_DOOR_", toIdx);
            if (doorIdx < 0) doorIdx = guid.length();

            String room1 = guid.substring(5, toIdx).toLowerCase();
            String room2 = guid.substring(toIdx + 4, doorIdx).toLowerCase();

            return String.format("between \"%s\" and \"%s\"", room1, room2);
        }

        return "connecting internal spaces";
    }
}
