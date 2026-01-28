package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 21A: Geometry validator.
 *
 * Validates:
 * 1. Wall connectivity (corners meet within tolerance)
 * 2. Room enclosure (every room has 4 walls)
 * 3. No room overlap (rooms don't intersect)
 *
 * Refactored from GeometricProofTest into proper validator.
 */
public class GeometryValidator implements BuildingValidator {

    private static final double TOLERANCE = 0.005; // 5mm

    @Override
    public String getName() {
        return "GeometryValidator";
    }

    @Override
    public boolean isRequired() {
        return true; // Geometry errors are critical
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
        // Check 1: Wall connectivity
        validateWallConnectivity(storey, result);

        // Check 2: Room enclosure
        validateRoomEnclosure(storey, result);

        // Check 3: No room overlap
        validateNoRoomOverlap(storey, result);
    }

    // =========================================================================
    // CHECK 1: Wall Connectivity
    // =========================================================================

    private void validateWallConnectivity(StoreySpec storey, BuildingValidationResult result) {
        List<WallEndpoint> endpoints = new ArrayList<>();

        for (WallAssemblySpec wall : storey.walls()) {
            var clad = wall.cladding();
            double x1, y1, x2, y2;

            // Determine wall orientation
            if (Math.abs(clad.maxX() - clad.minX()) > Math.abs(clad.maxY() - clad.minY())) {
                // Horizontal wall
                x1 = clad.minX(); y1 = clad.minY();
                x2 = clad.maxX(); y2 = clad.minY();
            } else {
                // Vertical wall
                x1 = clad.minX(); y1 = clad.minY();
                x2 = clad.minX(); y2 = clad.maxY();
            }

            endpoints.add(new WallEndpoint(wall.assemblyName(), x1, y1));
            endpoints.add(new WallEndpoint(wall.assemblyName(), x2, y2));
        }

        // Cluster endpoints to find corners
        List<Corner> corners = clusterEndpoints(endpoints);

        // Check each corner has at least 2 walls meeting
        for (Corner corner : corners) {
            if (corner.walls.size() < 2) {
                // Isolated endpoint - might be at building edge (acceptable)
                continue;
            }

            double maxGap = corner.maxGap;
            if (maxGap > TOLERANCE * 1000) {
                result.addCritical(
                    String.format("Corner(%.1f,%.1f)", corner.x, corner.y),
                    "WallConnectivity",
                    "Wall gap of %.1fmm exceeds tolerance (%.1fmm)",
                    maxGap, TOLERANCE * 1000
                );
            }
        }
    }

    private List<Corner> clusterEndpoints(List<WallEndpoint> endpoints) {
        List<Corner> corners = new ArrayList<>();

        for (WallEndpoint ep : endpoints) {
            boolean found = false;
            for (Corner corner : corners) {
                double dist = distance(ep.x, ep.y, corner.x, corner.y);
                if (dist < TOLERANCE * 10) { // Within 50mm cluster radius
                    corner.walls.add(ep.wallName);
                    if (dist > 0.0001) {
                        corner.maxGap = Math.max(corner.maxGap, dist * 1000);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                Corner newCorner = new Corner(ep.x, ep.y);
                newCorner.walls.add(ep.wallName);
                corners.add(newCorner);
            }
        }

        return corners;
    }

    // =========================================================================
    // CHECK 2: Room Enclosure
    // =========================================================================

    private void validateRoomEnclosure(StoreySpec storey, BuildingValidationResult result) {
        for (RoomSpec room : storey.rooms()) {
            boolean northWall = hasWallOnEdge(storey.walls(), room.minX(), room.maxY(), room.maxX(), room.maxY());
            boolean southWall = hasWallOnEdge(storey.walls(), room.minX(), room.minY(), room.maxX(), room.minY());
            boolean eastWall = hasWallOnEdge(storey.walls(), room.maxX(), room.minY(), room.maxX(), room.maxY());
            boolean westWall = hasWallOnEdge(storey.walls(), room.minX(), room.minY(), room.minX(), room.maxY());

            if (!northWall || !southWall || !eastWall || !westWall) {
                StringBuilder missing = new StringBuilder();
                if (!northWall) missing.append("north ");
                if (!southWall) missing.append("south ");
                if (!eastWall) missing.append("east ");
                if (!westWall) missing.append("west ");

                result.addCritical(
                    room.name(),
                    "RoomEnclosure",
                    "Room is not fully enclosed, missing walls: %s",
                    missing.toString().trim()
                );
            }
        }
    }

    private boolean hasWallOnEdge(List<WallAssemblySpec> walls, double x1, double y1, double x2, double y2) {
        for (WallAssemblySpec wall : walls) {
            var clad = wall.cladding();
            boolean isHorizontal = Math.abs(y1 - y2) < TOLERANCE;

            if (isHorizontal) {
                // Check horizontal edge
                boolean sameY = Math.abs(clad.minY() - y1) < TOLERANCE || Math.abs(clad.maxY() - y1) < TOLERANCE;
                boolean overlapsX = !(clad.maxX() < x1 - TOLERANCE || clad.minX() > x2 + TOLERANCE);
                if (sameY && overlapsX) return true;
            } else {
                // Check vertical edge
                boolean sameX = Math.abs(clad.minX() - x1) < TOLERANCE || Math.abs(clad.maxX() - x1) < TOLERANCE;
                boolean overlapsY = !(clad.maxY() < y1 - TOLERANCE || clad.minY() > y2 + TOLERANCE);
                if (sameX && overlapsY) return true;
            }
        }
        return false;
    }

    // =========================================================================
    // CHECK 3: No Room Overlap
    // =========================================================================

    private void validateNoRoomOverlap(StoreySpec storey, BuildingValidationResult result) {
        List<RoomSpec> rooms = storey.rooms();

        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                RoomSpec r1 = rooms.get(i);
                RoomSpec r2 = rooms.get(j);

                double overlapX = Math.max(0, Math.min(r1.maxX(), r2.maxX()) - Math.max(r1.minX(), r2.minX()));
                double overlapY = Math.max(0, Math.min(r1.maxY(), r2.maxY()) - Math.max(r1.minY(), r2.minY()));
                double overlap = overlapX * overlapY;

                // Allow tiny overlap from floating point (tolerance squared)
                if (overlap > TOLERANCE * TOLERANCE) {
                    result.addCritical(
                        r1.name() + " / " + r2.name(),
                        "NoOverlap",
                        "Rooms overlap by %.3f m²",
                        overlap
                    );
                }
            }
        }
    }

    // =========================================================================
    // Helper types
    // =========================================================================

    private record WallEndpoint(String wallName, double x, double y) {}

    private static class Corner {
        final double x, y;
        final List<String> walls = new ArrayList<>();
        double maxGap = 0;

        Corner(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
