package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.RoomType;

import java.util.*;

/**
 * Phase 21A: Habitability validator.
 *
 * Validates:
 * 1. Every habitable room has natural light (window or exterior door)
 * 2. Every room has at least one door (egress)
 * 3. Bedrooms have emergency egress opening (code requirement)
 * 4. Minimum room dimensions (usable space)
 *
 * Based on IRC 2021 requirements:
 * - R303.1: Habitable rooms require natural light
 * - R310.1: Emergency egress required in sleeping rooms
 * - R304.1: Minimum room dimensions
 */
public class HabitabilityValidator implements BuildingValidator {

    // IRC 2021 minimums
    private static final double MIN_HABITABLE_AREA = 6.5;      // m² (70 sq ft)
    private static final double MIN_DIMENSION = 2.134;          // m (7 ft)
    private static final double MIN_CORRIDOR_WIDTH = 0.914;     // m (36 in)
    private static final double MIN_PRACTICAL_WIDTH = 1.5;      // m (practical minimum)

    // Room types that require natural light (habitable spaces)
    private static final Set<String> HABITABLE_TYPES = Set.of(
        "BEDROOM", "LIVING", "KITCHEN", "OFFICE", "DEPARTURE_LOUNGE", "GATE"
    );

    // Room types that require emergency egress
    private static final Set<String> SLEEPING_TYPES = Set.of(
        "BEDROOM"
    );

    // Room types exempt from strict requirements
    private static final Set<String> EXEMPT_TYPES = Set.of(
        "BATHROOM", "STORAGE", "CORRIDOR", "LOBBY", "GARAGE", "CONCOURSE"
    );

    @Override
    public String getName() {
        return "HabitabilityValidator";
    }

    @Override
    public boolean isRequired() {
        return true; // Habitability is critical for building permit
    }

    @Override
    public BuildingValidationResult validate(BuildingSpec building) {
        BuildingValidationResult result = new BuildingValidationResult(getName());

        for (StoreySpec storey : building.storeys()) {
            validateStorey(storey, building, result);
        }

        return result;
    }

    private void validateStorey(StoreySpec storey, BuildingSpec building, BuildingValidationResult result) {
        // Build lookup maps
        Map<String, List<DoorSpec>> doorsByRoom = groupDoorsByRoom(storey.doors());
        Map<String, List<WindowSpec>> windowsByRoom = groupWindowsByRoom(storey.windows());

        // Get building bounds for exterior detection
        double[] bounds = getBuildingBounds(storey);

        for (RoomSpec room : storey.rooms()) {
            // Check 1: Natural light for habitable rooms
            validateNaturalLight(room, doorsByRoom, windowsByRoom, bounds, result);

            // Check 2: Every room has egress (door)
            validateEgress(room, doorsByRoom, result);

            // Check 3: Emergency egress for bedrooms
            validateEmergencyEgress(room, windowsByRoom, doorsByRoom, bounds, result);

            // Check 4: Minimum dimensions
            validateMinimumDimensions(room, result);
        }
    }

    // =========================================================================
    // CHECK 1: Natural Light
    // =========================================================================

    private void validateNaturalLight(RoomSpec room, Map<String, List<DoorSpec>> doorsByRoom,
                                       Map<String, List<WindowSpec>> windowsByRoom,
                                       double[] bounds, BuildingValidationResult result) {
        if (!isHabitable(room.type())) {
            return; // Non-habitable rooms don't require natural light
        }

        List<WindowSpec> windows = windowsByRoom.getOrDefault(room.name(), List.of());
        List<DoorSpec> doors = doorsByRoom.getOrDefault(room.name(), List.of());

        // Check for exterior window
        boolean hasExteriorWindow = windows.stream()
            .anyMatch(w -> isOnExterior(w.x(), w.y(), w.wall(), bounds));

        // Check for exterior door (can provide light)
        boolean hasExteriorDoor = doors.stream()
            .anyMatch(d -> isOnExterior(d.x(), d.y(), d.wall(), bounds));

        if (!hasExteriorWindow && !hasExteriorDoor) {
            result.addCritical(
                room.name(),
                "NaturalLight",
                "Habitable room has no window or exterior door for natural light (IRC R303.1)"
            );
        }
    }

    // =========================================================================
    // CHECK 2: Egress (Every room has door)
    // =========================================================================

    private void validateEgress(RoomSpec room, Map<String, List<DoorSpec>> doorsByRoom,
                                 BuildingValidationResult result) {
        List<DoorSpec> doors = doorsByRoom.getOrDefault(room.name(), List.of());

        if (doors.isEmpty()) {
            result.addCritical(
                room.name(),
                "Egress",
                "Room has no door - occupants would be trapped"
            );
        }
    }

    // =========================================================================
    // CHECK 3: Emergency Egress (Bedrooms)
    // =========================================================================

    private void validateEmergencyEgress(RoomSpec room, Map<String, List<WindowSpec>> windowsByRoom,
                                          Map<String, List<DoorSpec>> doorsByRoom,
                                          double[] bounds, BuildingValidationResult result) {
        if (!isSleepingRoom(room.type())) {
            return; // Only sleeping rooms require emergency egress
        }

        List<WindowSpec> windows = windowsByRoom.getOrDefault(room.name(), List.of());
        List<DoorSpec> doors = doorsByRoom.getOrDefault(room.name(), List.of());

        // Emergency egress requires exterior opening
        boolean hasEgressWindow = windows.stream()
            .anyMatch(w -> isOnExterior(w.x(), w.y(), w.wall(), bounds) && isEgressSize(w));

        boolean hasExteriorDoor = doors.stream()
            .anyMatch(d -> isOnExterior(d.x(), d.y(), d.wall(), bounds));

        if (!hasEgressWindow && !hasExteriorDoor) {
            result.addWarning(
                room.name(),
                "EmergencyEgress",
                "Bedroom lacks emergency egress window or exterior door (IRC R310.1)"
            );
        }
    }

    /**
     * IRC R310.1 egress window requirements:
     * - Min opening area: 5.7 sq ft (0.53 m²)
     * - Min opening height: 24 in (610mm)
     * - Min opening width: 20 in (508mm)
     */
    private boolean isEgressSize(WindowSpec window) {
        double area = window.width() * window.height();
        return area >= 0.53 && window.height() >= 0.61 && window.width() >= 0.508;
    }

    // =========================================================================
    // CHECK 4: Minimum Dimensions
    // =========================================================================

    private void validateMinimumDimensions(RoomSpec room, BuildingValidationResult result) {
        double width = room.maxX() - room.minX();
        double depth = room.maxY() - room.minY();
        double minDim = Math.min(width, depth);
        double area = width * depth;

        // Check minimum practical width
        if (minDim < MIN_PRACTICAL_WIDTH) {
            result.addWarning(
                room.name(),
                "MinDimension",
                "Room is too narrow (%.2fm < %.2fm) - may not fit furniture",
                minDim, MIN_PRACTICAL_WIDTH
            );
        }

        // Check IRC minimums for habitable rooms
        if (isHabitable(room.type())) {
            if (minDim < MIN_DIMENSION) {
                result.addWarning(
                    room.name(),
                    "MinDimension",
                    "Habitable room dimension (%.2fm) below IRC minimum (%.2fm)",
                    minDim, MIN_DIMENSION
                );
            }

            if (area < MIN_HABITABLE_AREA) {
                result.addWarning(
                    room.name(),
                    "MinArea",
                    "Habitable room area (%.1fm²) below IRC minimum (%.1fm²)",
                    area, MIN_HABITABLE_AREA
                );
            }
        }

        // Check corridor width
        if ("CORRIDOR".equals(room.type()) && minDim < MIN_CORRIDOR_WIDTH) {
            result.addWarning(
                room.name(),
                "CorridorWidth",
                "Corridor width (%.2fm) below IRC minimum (%.2fm)",
                minDim, MIN_CORRIDOR_WIDTH
            );
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Map<String, List<DoorSpec>> groupDoorsByRoom(List<DoorSpec> doors) {
        Map<String, List<DoorSpec>> map = new HashMap<>();
        for (DoorSpec door : doors) {
            // Door belongs to its room
            map.computeIfAbsent(door.roomName(), k -> new ArrayList<>()).add(door);

            // Also add to connected room if it's an interior door
            String connectsTo = extractConnectsTo(door.name());
            if (connectsTo != null) {
                map.computeIfAbsent(connectsTo, k -> new ArrayList<>()).add(door);
            }
        }
        return map;
    }

    private String extractConnectsTo(String doorName) {
        // Door names like "living_to_kitchen_door" or "master_to_ensuite_door"
        if (doorName.contains("_to_")) {
            String[] parts = doorName.split("_to_");
            if (parts.length == 2) {
                String targetRoom = parts[1];
                // Strip "_door" suffix if present
                if (targetRoom.endsWith("_door")) {
                    targetRoom = targetRoom.substring(0, targetRoom.length() - 5);
                }
                return targetRoom;
            }
        }
        return null;
    }

    private Map<String, List<WindowSpec>> groupWindowsByRoom(List<WindowSpec> windows) {
        Map<String, List<WindowSpec>> map = new HashMap<>();
        for (WindowSpec window : windows) {
            map.computeIfAbsent(window.roomName(), k -> new ArrayList<>()).add(window);
        }
        return map;
    }

    private double[] getBuildingBounds(StoreySpec storey) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (RoomSpec room : storey.rooms()) {
            minX = Math.min(minX, room.minX());
            maxX = Math.max(maxX, room.maxX());
            minY = Math.min(minY, room.minY());
            maxY = Math.max(maxY, room.maxY());
        }

        return new double[]{minX, minY, maxX, maxY};
    }

    private boolean isOnExterior(double x, double y, String wall, double[] bounds) {
        double tolerance = 0.005; // 5mm
        return switch (wall.toLowerCase()) {
            case "south" -> Math.abs(y - bounds[1]) < tolerance;
            case "north" -> Math.abs(y - bounds[3]) < tolerance;
            case "west" -> Math.abs(x - bounds[0]) < tolerance;
            case "east" -> Math.abs(x - bounds[2]) < tolerance;
            default -> false;
        };
    }

    private boolean isHabitable(String roomType) {
        return HABITABLE_TYPES.contains(roomType.toUpperCase());
    }

    private boolean isSleepingRoom(String roomType) {
        return SLEEPING_TYPES.contains(roomType.toUpperCase());
    }
}
