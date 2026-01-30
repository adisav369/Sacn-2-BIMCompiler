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
        Element bestEntry = null;
        double bestWidth = 0;
        for (Element door : exteriorDoors) {
            double width = Math.min(door.width(), door.depth()); // Smaller dimension is likely width
            if (width > bestWidth) {
                bestWidth = width;
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
        String dimensions = String.format("%.0f×%.0fmm",
            Math.min(bestEntry.width(), bestEntry.depth()) * 1000,
            bestEntry.height() * 1000);

        return CheckResult.pass(getId(), getName())
            .summary(String.format("Main entry %s (%s) on perimeter", doorId, dimensions))
            .data("doorId", doorId)
            .data("dimensions", new double[]{bestEntry.width(), bestEntry.height()})
            .data("exteriorDoorCount", exteriorDoors.size())
            .build();
    }

    /**
     * Check if a door is an exterior/entry door.
     * Checks guid patterns and geometric position.
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
        }

        // Fall back to geometric check
        return model.isDoorOnPerimeter(door);
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
