package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Window Area Ratio Sanity Check - Phase 71
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. WINDOW_RATIO_MATHS: Window area >= 10% of floor area (habitable rooms)
 * 2. VENTILATION_MATHS: Openable area >= 5% of floor area
 *
 * Code references:
 * - UBBL By-Law 39: Natural lighting (min 10% window-to-floor ratio)
 * - UBBL By-Law 40: Natural ventilation (min 5% openable area)
 * - MS 1525: Natural lighting requirements
 */
public class WindowAreaCheck implements SanityCheck {

    // UBBL By-Law 39: Natural lighting
    private static final double MIN_WINDOW_RATIO = 0.10; // 10%
    // UBBL By-Law 40: Natural ventilation (typically 50% of window is openable)
    private static final double MIN_VENTILATION_RATIO = 0.05; // 5%
    private static final double ASSUMED_OPENABLE_FRACTION = 0.50; // 50% of window area

    // Rooms that require natural light (habitable spaces)
    private static final Set<String> HABITABLE_TYPES = Set.of(
        "BEDROOM", "LIVING", "KITCHEN", "OFFICE", "CLASSROOM", "ROOM"
    );

    // Rooms exempt from natural light requirement
    private static final Set<String> EXEMPT_TYPES = Set.of(
        "BATHROOM", "TOILET", "STORAGE", "CORRIDOR", "STAIR", "LOBBY", "PORCH"
    );

    @Override
    public String getId() { return "window_area"; }

    @Override
    public String getName() { return "Window-to-Floor Area Ratio"; }

    @Override
    public CheckResult execute(SanityModel model) {
        List<Element> spaces = model.getSpaces();
        List<Element> windows = model.getWindows();

        if (spaces.isEmpty()) {
            return CheckResult.warning(getId(), getName())
                .summary("No rooms found - cannot calculate window ratios")
                .build();
        }

        if (windows.isEmpty()) {
            // Check if any habitable rooms exist
            boolean hasHabitable = false;
            for (Element space : spaces) {
                if (isHabitableRoom(space, model)) {
                    hasHabitable = true;
                    break;
                }
            }

            if (hasHabitable) {
                return CheckResult.fail(getId(), getName())
                    .summary("No windows found but building has habitable rooms")
                    .guidance("Add windows to habitable rooms (UBBL By-Law 39)")
                    .build();
            }

            return CheckResult.pass(getId(), getName())
                .summary("No windows required (no habitable rooms)")
                .build();
        }

        // Map windows to rooms by GUID pattern
        Map<String, List<Element>> roomWindows = mapWindowsToRooms(windows, spaces);

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> details = new ArrayList<>();

        int habitableRooms = 0;
        int passRooms = 0;
        int failRooms = 0;

        for (Element space : spaces) {
            String roomName = getRoomName(space);
            String spaceType = model.inferSpaceType(space);
            boolean isHabitable = isHabitableRoom(space, model);

            if (!isHabitable) {
                // Exempt rooms don't need window ratio check
                continue;
            }

            habitableRooms++;

            // Calculate floor area
            double floorArea = getFloorArea(space);
            if (floorArea <= 0) {
                warnings.add(String.format("%s: Cannot calculate floor area", roomName));
                continue;
            }

            // Calculate total window area for this room
            List<Element> roomWindowList = roomWindows.getOrDefault(roomName.toLowerCase(), Collections.emptyList());
            double totalWindowArea = 0;
            int windowCount = 0;

            for (Element window : roomWindowList) {
                double windowArea = getWindowArea(window);
                if (windowArea > 0) {
                    totalWindowArea += windowArea;
                    windowCount++;
                }
            }

            // Calculate ratios
            double windowRatio = floorArea > 0 ? totalWindowArea / floorArea : 0;
            double ventilationArea = totalWindowArea * ASSUMED_OPENABLE_FRACTION;
            double ventilationRatio = floorArea > 0 ? ventilationArea / floorArea : 0;

            // Check window ratio
            if (windowRatio < MIN_WINDOW_RATIO) {
                failRooms++;
                failures.add(String.format("%s [%s]: Window ratio %.1f%% < %.0f%% (%.1fm² windows / %.1fm² floor, %d windows)",
                    roomName, spaceType, windowRatio * 100, MIN_WINDOW_RATIO * 100,
                    totalWindowArea, floorArea, windowCount));
            } else {
                passRooms++;
                details.add(String.format("OK: %s [%s] %.1f%% (%.1fm² / %.1fm², %d windows)",
                    roomName, spaceType, windowRatio * 100,
                    totalWindowArea, floorArea, windowCount));
            }

            // Check ventilation ratio (warning only)
            if (ventilationRatio < MIN_VENTILATION_RATIO && windowRatio >= MIN_WINDOW_RATIO) {
                warnings.add(String.format("%s: Ventilation %.1f%% < %.0f%% (assuming %.0f%% openable)",
                    roomName, ventilationRatio * 100, MIN_VENTILATION_RATIO * 100,
                    ASSUMED_OPENABLE_FRACTION * 100));
            }
        }

        // Build result
        if (!failures.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Window ratio violations: %d/%d habitable rooms below %.0f%%",
                    failRooms, habitableRooms, MIN_WINDOW_RATIO * 100));

            result.detail(String.format("REQUIREMENT: Window area >= %.0f%% of floor area (UBBL By-Law 39)",
                MIN_WINDOW_RATIO * 100));

            for (String f : failures) {
                result.detail("FAIL: " + f);
            }
            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }

            result.guidance("Add more windows or increase window size to meet natural lighting requirement");
            result.data("habitableRooms", habitableRooms);
            result.data("failRooms", failRooms);
            result.data("totalWindows", windows.size());

            return result.build();
        }

        if (!warnings.isEmpty()) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("All %d habitable rooms meet window ratio, %d ventilation warnings",
                    habitableRooms, warnings.size()));

            result.detail(String.format("REQUIREMENT: Window >= %.0f%%, Ventilation >= %.0f%%",
                MIN_WINDOW_RATIO * 100, MIN_VENTILATION_RATIO * 100));

            for (String w : warnings) {
                result.detail(w);
            }

            return result.build();
        }

        // All pass
        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary(String.format("All %d habitable rooms meet %.0f%% window ratio",
                habitableRooms, MIN_WINDOW_RATIO * 100));

        result.detail(String.format("REQUIREMENT: Window area >= %.0f%% of floor area (UBBL By-Law 39)",
            MIN_WINDOW_RATIO * 100));

        for (String d : details) {
            result.detail(d);
        }

        result.data("habitableRooms", habitableRooms);
        result.data("totalWindows", windows.size());
        result.data("passRooms", passRooms);

        return result.build();
    }

    /**
     * Map windows to rooms by GUID pattern.
     * Window GUID: WINDOW_{ROOM}_WINDOW_{DIRECTION}_{STOREY}
     */
    private Map<String, List<Element>> mapWindowsToRooms(List<Element> windows, List<Element> spaces) {
        Map<String, List<Element>> result = new HashMap<>();

        // Build room name lookup
        Set<String> roomNames = new HashSet<>();
        for (Element space : spaces) {
            String name = getRoomName(space).toLowerCase();
            roomNames.add(name);
        }

        for (Element window : windows) {
            String roomName = extractRoomFromWindow(window);
            if (roomName != null) {
                // Try to match to known room
                String matched = findMatchingRoom(roomName, roomNames);
                if (matched != null) {
                    result.computeIfAbsent(matched, k -> new ArrayList<>()).add(window);
                }
            }
        }

        return result;
    }

    /**
     * Extract room name from window GUID.
     * Pattern: WINDOW_{ROOM}_WINDOW_{DIRECTION}_{STOREY}
     */
    private String extractRoomFromWindow(Element window) {
        if (window.guid() == null) return null;
        String guid = window.guid();

        if (guid.startsWith("WINDOW_")) {
            int secondWindow = guid.indexOf("_WINDOW_", 7);
            if (secondWindow > 0) {
                return guid.substring(7, secondWindow).toLowerCase();
            }
        }
        return null;
    }

    /**
     * Find matching room name with fuzzy matching.
     */
    private String findMatchingRoom(String name, Set<String> roomNames) {
        if (name == null) return null;
        String lower = name.toLowerCase();

        if (roomNames.contains(lower)) return lower;

        // Try without underscores
        String normalized = lower.replace("_", "");
        for (String room : roomNames) {
            if (room.replace("_", "").equals(normalized)) {
                return room;
            }
        }

        // Try partial match
        for (String room : roomNames) {
            if (room.contains(lower) || lower.contains(room)) {
                return room;
            }
        }

        return null;
    }

    /**
     * Check if room is habitable (requires natural light).
     */
    private boolean isHabitableRoom(Element space, SanityModel model) {
        String spaceType = model.inferSpaceType(space);

        // Exempt types don't need natural light
        if (EXEMPT_TYPES.contains(spaceType)) {
            return false;
        }

        // Check room name for exempt patterns
        String name = space.name() != null ? space.name().toLowerCase() : "";
        if (name.contains("toilet") || name.contains("tandas") ||
            name.contains("stor") || name.contains("utility") ||
            name.contains("corridor") || name.contains("koridor") ||
            name.contains("lobby") || name.contains("lobi") ||
            name.contains("porch") || name.contains("anjung")) {
            return false;
        }

        // Habitable types require natural light
        if (HABITABLE_TYPES.contains(spaceType)) {
            return true;
        }

        // Default: treat unknown rooms as habitable (conservative)
        return true;
    }

    /**
     * Get floor area from space bounding box.
     */
    private double getFloorArea(Element space) {
        if (space.bbox() == null) return 0;
        return space.bbox().areaXY();
    }

    /**
     * Get window area (width × height).
     */
    private double getWindowArea(Element window) {
        if (window.bbox() == null) return 0;

        BoundingBox bbox = window.bbox();
        // Window dimensions: width is larger horizontal, depth is frame thickness
        double dim1 = bbox.width();
        double dim2 = bbox.depth();
        double height = bbox.height();

        // Width is the larger horizontal dimension
        double width = Math.max(dim1, dim2);

        return width * height;
    }

    /**
     * Get readable room name.
     */
    private String getRoomName(Element space) {
        if (space.name() != null && !space.name().isEmpty()) {
            return space.name();
        }
        // Extract from GUID: SPACE_GROUND_CLASS_1 -> CLASS_1
        String guid = space.guid();
        if (guid != null && guid.startsWith("SPACE_")) {
            int lastUnderscore = guid.lastIndexOf('_');
            if (lastUnderscore > 6) {
                // Find storey marker
                String afterSpace = guid.substring(6);
                int storeyEnd = afterSpace.indexOf('_');
                if (storeyEnd > 0) {
                    return afterSpace.substring(storeyEnd + 1);
                }
            }
        }
        return guid != null ? guid : "unknown";
    }
}
