/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation.building;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.dsl.BuildingSpecs.*;

import java.util.*;

/**
 * Phase 63: Orphaned element validator.
 *
 * Detects elements placed outside room boundaries with no logical containment:
 * 1. Elements assigned to non-existent rooms
 * 2. Elements positioned outside their assigned room's bounds
 * 3. Clusters of orphaned elements (placement bugs often create multiple strays)
 *
 * This catches placement bugs where fixtures, lights, or MEP elements
 * end up floating in space with no containing room.
 */
public class OrphanedElementValidator implements BuildingValidator {

    private static final double TOLERANCE = BIMConstants.TOLERANCE;
    // Margin outside room bounds before flagging (allow minor overhangs)
    private static final double MARGIN = 0.1; // 100mm

    @Override
    public String getName() {
        return "OrphanedElementValidator";
    }

    @Override
    public boolean isRequired() {
        return false; // Advisory - doesn't block build, but warns
    }

    @Override
    public BuildingValidationResult validate(BuildingSpec building) {
        BuildingValidationResult result = new BuildingValidationResult(getName());

        for (StoreySpec storey : building.storeys()) {
            validateStorey(storey, result);
        }

        return result;
    }

    private void validateStorey(StoreySpec storey, BuildingValidationResult result) {
        // Build room lookup by name
        Map<String, RoomSpec> roomsByName = new HashMap<>();
        for (RoomSpec room : storey.rooms()) {
            roomsByName.put(room.name(), room);
        }

        // Calculate storey bounds (union of all rooms + slab)
        double[] storeyBounds = calculateStoreyBounds(storey);

        // Track orphaned elements for cluster detection
        List<OrphanedElement> orphans = new ArrayList<>();

        // Check fixtures
        for (FixtureSpec f : storey.fixtures()) {
            OrphanResult r = checkElement(f.id(), f.roomName(), f.x(), f.y(),
                                          "fixture:" + f.fixtureType(), roomsByName, storeyBounds);
            if (r != null) orphans.add(r.orphan);
        }

        // Check lights
        for (LightSpec l : storey.lights()) {
            OrphanResult r = checkElement(l.id(), l.roomName(), l.x(), l.y(),
                                          "light:" + l.fixtureType(), roomsByName, storeyBounds);
            if (r != null) orphans.add(r.orphan);
        }

        // Check sprinklers
        for (SprinklerSpec s : storey.sprinklers()) {
            OrphanResult r = checkElement(s.id(), s.roomName(), s.x(), s.y(),
                                          "sprinkler:" + s.type(), roomsByName, storeyBounds);
            if (r != null) orphans.add(r.orphan);
        }

        // Check electrical elements
        for (ElectricalSpec e : storey.electricals()) {
            OrphanResult r = checkElement(e.id(), e.roomName(), e.x(), e.y(),
                                          "electrical:" + e.elementType(), roomsByName, storeyBounds);
            if (r != null) orphans.add(r.orphan);
        }

        // Check diffusers
        for (DiffuserSpec d : storey.diffusers()) {
            OrphanResult r = checkElement(d.id(), d.roomName(), d.x(), d.y(),
                                          "diffuser:" + d.diffuserType(), roomsByName, storeyBounds);
            if (r != null) orphans.add(r.orphan);
        }

        // Report orphaned elements
        reportOrphans(storey.name(), orphans, result);
    }

    /**
     * Check if an element is orphaned (outside room bounds or no room).
     * Diagnoses root cause based on offset pattern.
     */
    private OrphanResult checkElement(String id, String roomName, double x, double y,
                                       String elementType, Map<String, RoomSpec> roomsByName,
                                       double[] storeyBounds) {
        // Check 1: Room exists
        if (roomName == null || roomName.isEmpty()) {
            return new OrphanResult(new OrphanedElement(id, elementType, x, y, "NO_ROOM",
                                                         "Element has no assigned room"));
        }

        RoomSpec room = roomsByName.get(roomName);
        if (room == null) {
            return new OrphanResult(new OrphanedElement(id, elementType, x, y, "INVALID_ROOM",
                                                         "Room '" + roomName + "' does not exist"));
        }

        // Check 2: Position inside room (with margin)
        if (!isInsideRoom(x, y, room, MARGIN)) {
            double distOutside = distanceOutsideRoom(x, y, room);
            String cause = diagnoseRootCause(x, y, room, distOutside);
            return new OrphanResult(new OrphanedElement(id, elementType, x, y, "OUTSIDE_ROOM",
                String.format("%.2fm outside '%s' - %s", distOutside, roomName, cause)));
        }

        // Check 3: Far outside storey bounds (major bug)
        if (!isInsideBounds(x, y, storeyBounds, 5.0)) { // 5m tolerance for storey
            return new OrphanResult(new OrphanedElement(id, elementType, x, y, "OUTSIDE_STOREY",
                                                         "Element far outside storey - coordinate system bug"));
        }

        return null; // Element is properly contained
    }

    /**
     * Diagnose root cause based on offset pattern.
     */
    private String diagnoseRootCause(double x, double y, RoomSpec room, double distOutside) {
        // Calculate offset from room center
        double roomCenterX = (room.minX() + room.maxX()) / 2;
        double roomCenterY = (room.minY() + room.maxY()) / 2;
        double offsetX = x - roomCenterX;
        double offsetY = y - roomCenterY;

        // Pattern 1: Large systematic offset (>5m) = unit-local vs building-global
        if (distOutside > 5.0) {
            return String.format("COORD_MISMATCH (offset %.1f,%.1f) - check unit vs building coords", offsetX, offsetY);
        }

        // Pattern 2: Small offset near corner (<1m) = column avoidance accumulation
        boolean nearCornerX = Math.abs(x - room.minX()) < 1.0 || Math.abs(x - room.maxX()) < 1.0;
        boolean nearCornerY = Math.abs(y - room.minY()) < 1.0 || Math.abs(y - room.maxY()) < 1.0;
        if (distOutside < 1.0 && nearCornerX && nearCornerY) {
            return "COLUMN_AVOIDANCE - accumulated offset from multiple column zones";
        }

        // Pattern 3: Element at exact room edge = room resized after placement
        double edgeTolerance = 0.05;
        boolean atEdgeX = Math.abs(x - room.minX()) < edgeTolerance || Math.abs(x - room.maxX()) < edgeTolerance;
        boolean atEdgeY = Math.abs(y - room.minY()) < edgeTolerance || Math.abs(y - room.maxY()) < edgeTolerance;
        if (atEdgeX || atEdgeY) {
            return "ROOM_RESIZED - element at old boundary, room bounds changed";
        }

        // Default: generic offset
        return String.format("offset (%.2f,%.2f) from room center", offsetX, offsetY);
    }

    private boolean isInsideRoom(double x, double y, RoomSpec room, double margin) {
        return x >= room.minX() - margin && x <= room.maxX() + margin &&
               y >= room.minY() - margin && y <= room.maxY() + margin;
    }

    private boolean isInsideBounds(double x, double y, double[] bounds, double margin) {
        return x >= bounds[0] - margin && x <= bounds[2] + margin &&
               y >= bounds[1] - margin && y <= bounds[3] + margin;
    }

    private double distanceOutsideRoom(double x, double y, RoomSpec room) {
        double dx = 0, dy = 0;
        if (x < room.minX()) dx = room.minX() - x;
        if (x > room.maxX()) dx = x - room.maxX();
        if (y < room.minY()) dy = room.minY() - y;
        if (y > room.maxY()) dy = y - room.maxY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double[] calculateStoreyBounds(StoreySpec storey) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (RoomSpec room : storey.rooms()) {
            minX = Math.min(minX, room.minX());
            minY = Math.min(minY, room.minY());
            maxX = Math.max(maxX, room.maxX());
            maxY = Math.max(maxY, room.maxY());
        }

        // Include slab bounds if no rooms
        if (storey.slab() != null && storey.rooms().isEmpty()) {
            SlabSpec slab = storey.slab();
            minX = slab.minX();
            minY = slab.minY();
            maxX = slab.maxX();
            maxY = slab.maxY();
        }

        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * Report orphaned elements, grouping clusters.
     */
    private void reportOrphans(String storeyName, List<OrphanedElement> orphans,
                                BuildingValidationResult result) {
        if (orphans.isEmpty()) return;

        // Group by reason
        Map<String, List<OrphanedElement>> byReason = new HashMap<>();
        for (OrphanedElement o : orphans) {
            byReason.computeIfAbsent(o.reason, k -> new ArrayList<>()).add(o);
        }

        // Report clusters (>3 orphans of same type suggests systematic bug)
        for (Map.Entry<String, List<OrphanedElement>> entry : byReason.entrySet()) {
            List<OrphanedElement> group = entry.getValue();

            if (group.size() >= 3) {
                // Cluster of orphans - likely placement bug
                result.addCritical(
                    storeyName,
                    "OrphanCluster",
                    "%d orphaned elements (%s): %s",
                    group.size(), entry.getKey(), summarizeOrphans(group)
                );
            } else {
                // Individual orphans
                for (OrphanedElement o : group) {
                    result.addWarning(
                        storeyName + "/" + o.id,
                        "OrphanedElement",
                        "%s at (%.1f,%.1f) - %s",
                        o.elementType, o.x, o.y, o.message
                    );
                }
            }
        }
    }

    private String summarizeOrphans(List<OrphanedElement> orphans) {
        // Group by element type
        Map<String, Integer> typeCounts = new HashMap<>();
        for (OrphanedElement o : orphans) {
            String type = o.elementType.split(":")[0]; // Just "fixture", "light", etc.
            typeCounts.merge(type, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : typeCounts.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append("× ").append(e.getKey());
        }
        return sb.toString();
    }

    // =========================================================================
    // Helper types
    // =========================================================================

    private record OrphanedElement(
        String id,
        String elementType,
        double x, double y,
        String reason,
        String message
    ) {}

    private record OrphanResult(OrphanedElement orphan) {}
}
