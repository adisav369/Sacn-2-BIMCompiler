package com.bim.compiler.placement;

import com.bim.compiler.dsl.BuildingCompiler.RoomSpec;
import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Phase 63: Validates element placement before construction (fail-fast).
 *
 * Solves all orphan causes by catching invalid placements at creation time,
 * not during post-hoc validation.
 *
 * Usage:
 *   PlacementValidator validator = new PlacementValidator(roomsByName);
 *
 *   // Validate before adding - throws if invalid
 *   validator.requireInRoom("toilet_1", "bathroom", x, y);
 *   fixtures.add(new FixtureSpec(...));
 *
 *   // Or use soft validation with logging
 *   if (validator.isValidPlacement("light_1", "bedroom", x, y)) {
 *       lights.add(new LightSpec(...));
 *   }
 */
public class PlacementValidator {

    private final Map<String, RoomSpec> roomsByName;
    private final double margin;  // Allowed overhang beyond room bounds
    private final List<PlacementViolation> violations = new ArrayList<>();

    public PlacementValidator(Map<String, RoomSpec> roomsByName) {
        this(roomsByName, 0.1);  // Default 100mm margin
    }

    public PlacementValidator(Map<String, RoomSpec> roomsByName, double margin) {
        this.roomsByName = roomsByName;
        this.margin = margin;
    }

    /**
     * Validate placement - throws PlacementException if invalid.
     */
    public void requireInRoom(String elementId, String roomName, double x, double y) {
        ValidationResult result = validate(elementId, roomName, x, y);
        if (!result.valid) {
            throw new PlacementException(result.message);
        }
    }

    /**
     * Validate placement - throws PlacementException if invalid.
     */
    public void requireInRoom(String elementId, String roomName, Point3D position) {
        requireInRoom(elementId, roomName, position.x(), position.y());
    }

    /**
     * Soft validation - returns true if valid, logs violation if not.
     */
    public boolean isValidPlacement(String elementId, String roomName, double x, double y) {
        ValidationResult result = validate(elementId, roomName, x, y);
        if (!result.valid) {
            violations.add(new PlacementViolation(elementId, roomName, x, y, result.message));
        }
        return result.valid;
    }

    /**
     * Validate and auto-clamp to room bounds if slightly outside.
     * Returns clamped position, or throws if way outside.
     */
    public Point3D validateAndClamp(String elementId, String roomName, double x, double y, double z,
                                     double maxClampDistance) {
        RoomSpec room = roomsByName.get(roomName);
        if (room == null) {
            throw new PlacementException("Room '%s' not found for element '%s'", roomName, elementId);
        }

        double clampedX = clamp(x, room.minX(), room.maxX());
        double clampedY = clamp(y, room.minY(), room.maxY());

        double dx = Math.abs(clampedX - x);
        double dy = Math.abs(clampedY - y);
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance > maxClampDistance) {
            throw new PlacementException(
                "Element '%s' at (%.2f,%.2f) is %.2fm outside room '%s' - exceeds clamp limit %.2fm",
                elementId, x, y, distance, roomName, maxClampDistance);
        }

        if (distance > 0.001) {
            System.out.printf("[PlacementValidator] Clamped '%s' by %.3fm to fit room '%s'%n",
                              elementId, distance, roomName);
        }

        return new Point3D(clampedX, clampedY, z);
    }

    /**
     * Get all violations logged during soft validation.
     */
    public List<PlacementViolation> getViolations() {
        return List.copyOf(violations);
    }

    /**
     * Check if any violations were logged.
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    /**
     * Clear logged violations.
     */
    public void clearViolations() {
        violations.clear();
    }

    // =========================================================================
    // Private implementation
    // =========================================================================

    private ValidationResult validate(String elementId, String roomName, double x, double y) {
        // Check 1: Room exists
        if (roomName == null || roomName.isEmpty()) {
            return ValidationResult.fail("Element '%s' has no assigned room", elementId);
        }

        RoomSpec room = roomsByName.get(roomName);
        if (room == null) {
            return ValidationResult.fail("Element '%s' assigned to non-existent room '%s'",
                                          elementId, roomName);
        }

        // Check 2: Position inside room (with margin)
        if (x < room.minX() - margin || x > room.maxX() + margin ||
            y < room.minY() - margin || y > room.maxY() + margin) {

            double distX = Math.max(0, Math.max(room.minX() - x, x - room.maxX()));
            double distY = Math.max(0, Math.max(room.minY() - y, y - room.maxY()));
            double dist = Math.sqrt(distX * distX + distY * distY);

            return ValidationResult.fail(
                "Element '%s' at (%.2f,%.2f) is %.2fm outside room '%s' bounds [%.2f-%.2f, %.2f-%.2f]",
                elementId, x, y, dist, roomName,
                room.minX(), room.maxX(), room.minY(), room.maxY());
        }

        return ValidationResult.ok();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // =========================================================================
    // Helper types
    // =========================================================================

    private record ValidationResult(boolean valid, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult fail(String format, Object... args) {
            return new ValidationResult(false, String.format(format, args));
        }
    }

    public record PlacementViolation(
        String elementId,
        String roomName,
        double x, double y,
        String message
    ) {}
}
