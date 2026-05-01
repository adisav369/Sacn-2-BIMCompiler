/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation.building;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.SpaceTypeRegistry;
import com.bim.compiler.dsl.SpaceTypeRegistry.SpaceTypeConfig;
import com.bim.compiler.util.OutlierLogger;

import java.util.*;

/**
 * Phase 21A: Habitability validator.
 * Phase 29: Now uses SpaceTypeRegistry for configuration-driven validation.
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
 *
 * Validation rules now loaded from config/spacetypes.yaml
 */
public class HabitabilityValidator implements BuildingValidator {

    // Fallback minimums (from BIMConstants) - used if config not available
    private static final double FALLBACK_MIN_AREA = BIMConstants.IRC_MIN_HABITABLE_AREA;
    private static final double FALLBACK_MIN_DIMENSION = BIMConstants.IRC_MIN_DIMENSION;
    private static final double MIN_CORRIDOR_WIDTH = BIMConstants.IRC_MIN_CORRIDOR_WIDTH;
    private static final double MIN_PRACTICAL_WIDTH = BIMConstants.PRACTICAL_MIN_ROOM_WIDTH;

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

        // Phase 50: Pass rooms list for adjacency-based exterior detection
        List<RoomSpec> allRooms = storey.rooms();

        for (RoomSpec room : allRooms) {
            // Check 1: Natural light for habitable rooms
            validateNaturalLight(room, doorsByRoom, windowsByRoom, allRooms, result);

            // Check 2: Every room has egress (door)
            validateEgress(room, doorsByRoom, result);

            // Check 3: Emergency egress for bedrooms
            validateEmergencyEgress(room, windowsByRoom, doorsByRoom, allRooms, result);

            // Check 4: Minimum dimensions
            validateMinimumDimensions(room, result);
        }
    }

    // =========================================================================
    // CHECK 1: Natural Light
    // =========================================================================

    private void validateNaturalLight(RoomSpec room, Map<String, List<DoorSpec>> doorsByRoom,
                                       Map<String, List<WindowSpec>> windowsByRoom,
                                       List<RoomSpec> allRooms, BuildingValidationResult result) {
        // Phase 25: Use logging version to track unknown types
        if (!isHabitableWithLogging(room.type(), room.name())) {
            return; // Non-habitable rooms don't require natural light
        }

        List<WindowSpec> windows = windowsByRoom.getOrDefault(room.name(), List.of());
        List<DoorSpec> doors = doorsByRoom.getOrDefault(room.name(), List.of());

        // Phase 47A.3: For multi-unit buildings, relax exterior check
        // Joint solve may place rooms away from building boundary but still on unit exterior
        boolean isMultiUnit = room.unitId() != null;

        // Check for exterior window
        // Phase 50: Use adjacency-based check - wall is exterior if no room shares that edge
        boolean hasExteriorWindow;
        if (isMultiUnit) {
            // Multi-unit: just check if room has any window (exterior placement not verified)
            hasExteriorWindow = !windows.isEmpty();
        } else {
            // Single unit: check if window is on exterior wall (no adjacent room)
            hasExteriorWindow = windows.stream()
                .anyMatch(w -> isOnExterior(room, w.wall(), allRooms));
        }

        // Check for exterior door (can provide light)
        boolean hasExteriorDoor;
        if (isMultiUnit) {
            // Multi-unit: just check if room has any door that could provide light
            hasExteriorDoor = doors.stream()
                .anyMatch(d -> "south".equalsIgnoreCase(d.wall()) || "west".equalsIgnoreCase(d.wall()) ||
                              "north".equalsIgnoreCase(d.wall()) || "east".equalsIgnoreCase(d.wall()));
        } else {
            hasExteriorDoor = doors.stream()
                .anyMatch(d -> isOnExterior(room, d.wall(), allRooms));
        }

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
        String roomType = room.type().toUpperCase();

        // Phase 63: Skip door check for spaces that don't require traditional doors
        // 1. EXTERIOR category (PORCH, VERANDAH, CAR_PORCH)
        // 2. CIRCULATION category (CORRIDOR, LOBBY) - open to adjacent spaces
        // 3. Utility/infrastructure rooms - access panels, not occupant doors
        SpaceTypeConfig config = SpaceTypeRegistry.get(roomType);
        String category = config.category();

        if ("EXTERIOR".equals(category) || "CIRCULATION".equals(category)) {
            return; // Exterior/circulation spaces don't require doors
        }

        // Utility rooms that fall back to GENERIC but are infrastructure
        // These have access panels/hatches, not occupant doors
        if (isUtilityRoom(room.name())) {
            return;
        }

        List<DoorSpec> doors = doorsByRoom.getOrDefault(room.name(), List.of());

        if (doors.isEmpty()) {
            result.addCritical(
                room.name(),
                "Egress",
                "Room has no door - occupants would be trapped"
            );
        }
    }

    /**
     * Phase 63: Check if room is a utility/infrastructure room.
     * These rooms have access panels or hatches, not occupant doors.
     *
     * Since utility room types (TNB_ROOM, PUMP_ROOM, etc.) resolve to GENERIC,
     * we check room name patterns instead.
     */
    private boolean isUtilityRoom(String roomName) {
        String lower = roomName.toLowerCase();
        // Utility room name patterns
        return lower.contains("tnb") ||           // Electrical (Tenaga Nasional)
               lower.contains("pump") ||          // Mechanical pump
               lower.contains("genset") ||        // Generator
               lower.contains("tank") ||          // Water storage (not fish tank!)
               lower.contains("machine") ||       // Lift machine room
               lower.startsWith("lift_mr") ||     // Lift machine room variant
               lower.contains("riser") ||         // MEP riser shaft
               lower.contains("shaft");           // Vertical shaft
    }

    // =========================================================================
    // CHECK 3: Emergency Egress (Bedrooms)
    // =========================================================================

    private void validateEmergencyEgress(RoomSpec room, Map<String, List<WindowSpec>> windowsByRoom,
                                          Map<String, List<DoorSpec>> doorsByRoom,
                                          List<RoomSpec> allRooms, BuildingValidationResult result) {
        // Phase 25: Use logging version to track unknown types
        if (!isSleepingRoomWithLogging(room.type(), room.name())) {
            return; // Only sleeping rooms require emergency egress
        }

        List<WindowSpec> windows = windowsByRoom.getOrDefault(room.name(), List.of());
        List<DoorSpec> doors = doorsByRoom.getOrDefault(room.name(), List.of());

        // Emergency egress requires exterior opening
        // Phase 50: Use adjacency-based check
        boolean hasEgressWindow = windows.stream()
            .anyMatch(w -> isOnExterior(room, w.wall(), allRooms) && isEgressSize(w));

        boolean hasExteriorDoor = doors.stream()
            .anyMatch(d -> isOnExterior(room, d.wall(), allRooms));

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

        // Phase 29: Get validation rules from SpaceTypeRegistry
        SpaceTypeConfig config = SpaceTypeRegistry.get(room.type());
        double minArea = config.validation().minArea();
        double minDimension = config.validation().minDimension();

        // Use fallback if config has zeros
        if (minArea == 0 && config.isHabitable()) {
            minArea = FALLBACK_MIN_AREA;
        }
        if (minDimension == 0 && config.isHabitable()) {
            minDimension = FALLBACK_MIN_DIMENSION;
        }

        // Check minimum practical width
        if (minDim < MIN_PRACTICAL_WIDTH) {
            result.addWarning(
                room.name(),
                "MinDimension",
                "Room is too narrow (%.2fm < %.2fm) - may not fit furniture",
                minDim, MIN_PRACTICAL_WIDTH
            );
        }

        // Check configured minimums for habitable rooms
        if (config.isHabitable()) {
            if (minDimension > 0 && minDim < minDimension) {
                result.addWarning(
                    room.name(),
                    "MinDimension",
                    "Habitable room dimension (%.2fm) below minimum (%.2fm)",
                    minDim, minDimension
                );
            }

            if (minArea > 0 && area < minArea) {
                result.addWarning(
                    room.name(),
                    "MinArea",
                    "Habitable room area (%.1fm²) below minimum (%.1fm²)",
                    area, minArea
                );
            }
        }

        // Check corridor width
        if ("CORRIDOR".equalsIgnoreCase(room.type()) && minDim < MIN_CORRIDOR_WIDTH) {
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

    /**
     * Phase 50: Adjacency-based exterior detection.
     * A wall is exterior if no other room shares that edge.
     * Works correctly for multi-block layouts (school with teaching + assembly blocks).
     */
    private boolean isOnExterior(RoomSpec room, String wall, List<RoomSpec> allRooms) {
        double tolerance = 0.1; // 100mm tolerance for adjacency check
        double wallPosition = getWallPosition(room, wall);
        String opposite = oppositeDirection(wall);

        // Check if any other room has its opposite edge at this position
        return allRooms.stream()
            .filter(r -> !r.name().equals(room.name()))
            .noneMatch(r -> Math.abs(getWallPosition(r, opposite) - wallPosition) < tolerance
                         && edgesOverlap(room, r, wall));
    }

    private double getWallPosition(RoomSpec room, String wall) {
        return switch (wall.toLowerCase()) {
            case "south" -> room.minY();
            case "north" -> room.maxY();
            case "west" -> room.minX();
            case "east" -> room.maxX();
            default -> 0;
        };
    }

    private String oppositeDirection(String direction) {
        return switch (direction.toLowerCase()) {
            case "south" -> "north";
            case "north" -> "south";
            case "east" -> "west";
            case "west" -> "east";
            default -> direction;
        };
    }

    /**
     * Check if two rooms' edges overlap in the perpendicular dimension.
     * E.g., for south/north walls, check X overlap.
     */
    private boolean edgesOverlap(RoomSpec r1, RoomSpec r2, String wall) {
        return switch (wall.toLowerCase()) {
            case "south", "north" ->
                // Check X overlap
                r1.minX() < r2.maxX() && r1.maxX() > r2.minX();
            case "east", "west" ->
                // Check Y overlap
                r1.minY() < r2.maxY() && r1.maxY() > r2.minY();
            default -> false;
        };
    }

    // Legacy method for backward compatibility (delegates to adjacency check)
    @SuppressWarnings("unused")
    private boolean isOnExteriorLegacy(double x, double y, String wall, double[] bounds) {
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
        return isHabitableWithLogging(roomType, null);
    }

    /**
     * Phase 29: Check if room type is habitable using SpaceTypeRegistry.
     */
    private boolean isHabitableWithLogging(String roomType, String spaceName) {
        SpaceTypeConfig config = SpaceTypeRegistry.get(roomType);

        // Log if using GENERIC fallback
        if ("GENERIC".equals(config.name()) && spaceName != null) {
            OutlierLogger.logValidationUnknown(roomType, spaceName);
        }

        return config.isHabitable();
    }

    private boolean isSleepingRoom(String roomType) {
        return isSleepingRoomWithLogging(roomType, null);
    }

    /**
     * Phase 29: Check if room is sleeping room using SpaceTypeRegistry.
     */
    private boolean isSleepingRoomWithLogging(String roomType, String spaceName) {
        SpaceTypeConfig config = SpaceTypeRegistry.get(roomType);

        // Log if using GENERIC fallback
        if ("GENERIC".equals(config.name()) && spaceName != null) {
            // Check if name suggests sleeping
            String upper = roomType.toUpperCase();
            if (upper.contains("BED") || upper.contains("SLEEP") || upper.contains("GUEST")) {
                OutlierLogger.log(OutlierLogger.OutlierCategory.VALIDATION_UNKNOWN,
                    roomType, "Space: " + spaceName,
                    "Treating as sleeping room based on name",
                    "Add " + roomType + " to config/spacetypes.yaml");
                return true;
            }
        }

        return config.isSleepingRoom();
    }

    /**
     * Phase 29: Check if room type is known in SpaceTypeRegistry.
     */
    public boolean isKnownType(String roomType) {
        return SpaceTypeRegistry.exists(roomType);
    }
}
