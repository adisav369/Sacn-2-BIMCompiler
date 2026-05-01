/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

import com.bim.compiler.BIMConstants;

import java.sql.*;
import java.util.*;

/**
 * Phase 29: Assembly-level geometry verification.
 *
 * Verifies piece-to-piece correctness:
 * 1. Door/window containment in walls
 * 2. Frame member alignment within assemblies
 * 3. Component positions are valid
 *
 * This catches issues that room-level validation misses.
 */
public class AssemblyGeometryValidator {

    private static final double TOLERANCE = BIMConstants.ASSEMBLY_TOLERANCE;
    private static final double PLANE_TOLERANCE = BIMConstants.PLANE_TOLERANCE;

    /**
     * Bounding box record.
     */
    public record BBox(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        public double width() { return maxX - minX; }
        public double depth() { return maxY - minY; }
        public double height() { return maxZ - minZ; }

        public boolean contains2D(BBox other) {
            return other.minX >= minX - TOLERANCE &&
                   other.maxX <= maxX + TOLERANCE &&
                   other.minY >= minY - TOLERANCE &&
                   other.maxY <= maxY + TOLERANCE;
        }

        public boolean intersects2D(BBox other) {
            return !(other.maxX < minX - TOLERANCE ||
                     other.minX > maxX + TOLERANCE ||
                     other.maxY < minY - TOLERANCE ||
                     other.minY > maxY + TOLERANCE);
        }
    }

    /**
     * Validation issue.
     */
    public record Issue(String type, String component, String expected, String actual, String severity) {
        public static Issue error(String type, String component, String expected, String actual) {
            return new Issue(type, component, expected, actual, "ERROR");
        }

        public static Issue warning(String type, String component, String expected, String actual) {
            return new Issue(type, component, expected, actual, "WARNING");
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s\n  Expected: %s\n  Actual:   %s",
                severity, type, component, expected, actual);
        }
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        int totalChecks,
        int passed,
        int failed,
        List<Issue> issues
    ) {
        public boolean isValid() { return failed == 0; }

        public void print() {
            System.out.println("\n=== Assembly Geometry Validation ===");
            System.out.printf("Checks: %d total, %d passed, %d failed%n", totalChecks, passed, failed);

            if (!issues.isEmpty()) {
                System.out.println("\nIssues:");
                for (Issue issue : issues) {
                    System.out.println("  " + issue);
                }
            }

            System.out.println("\nVerdict: " + (isValid() ? "[PASS]" : "[FAIL]"));
        }
    }

    private final Connection conn;
    private final List<Issue> issues = new ArrayList<>();
    private int totalChecks = 0;
    private int passed = 0;
    private int failed = 0;

    public AssemblyGeometryValidator(Connection conn) {
        this.conn = conn;
    }

    /**
     * Run all validations.
     */
    public ValidationResult validate() throws SQLException {
        issues.clear();
        totalChecks = 0;
        passed = 0;
        failed = 0;

        validateDoorWallRelationship();
        validateWindowWallRelationship();
        validateWallFrameAlignment();
        validateAssemblyComponentPositions();

        return new ValidationResult(totalChecks, passed, failed, new ArrayList<>(issues));
    }

    /**
     * 1. Validate doors are positioned correctly relative to walls.
     *
     * A door should:
     * - Be positioned ON a wall (share wall plane)
     * - Have width along the wall length direction
     * - Not extend beyond the wall bounds
     */
    private void validateDoorWallRelationship() throws SQLException {
        String query = """
            SELECT
                m.guid as door_guid,
                m.element_name,
                r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcDoor'
            """;

        // Get all wall bboxes
        Map<String, BBox> wallBBoxes = getWallBoundingBoxes();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                totalChecks++;
                String doorGuid = rs.getString("door_guid");
                String doorName = rs.getString("element_name");

                BBox doorBBox = new BBox(
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                );

                // Find the wall this door should be in
                String wallName = findContainingWall(doorBBox, wallBBoxes);

                if (wallName == null) {
                    // Door not contained in any wall - check if it's ON a wall plane
                    String nearestWall = findNearestWall(doorBBox, wallBBoxes);
                    if (nearestWall != null) {
                        BBox wallBBox = wallBBoxes.get(nearestWall);
                        // Check if door is on wall plane (perpendicular)
                        boolean onWallPlane = isOnWallPlane(doorBBox, wallBBox);
                        if (onWallPlane) {
                            // Check if door width fits within wall length
                            boolean fitsInWall = doorFitsInWall(doorBBox, wallBBox);
                            if (fitsInWall) {
                                passed++;
                            } else {
                                failed++;
                                issues.add(Issue.error(
                                    "DOOR_EXCEEDS_WALL",
                                    doorGuid,
                                    String.format("Door width (%.2fm) should fit in wall length",
                                        Math.max(doorBBox.width(), doorBBox.depth())),
                                    String.format("Door at %.2f-%.2f exceeds wall at %.2f-%.2f",
                                        doorBBox.minX, doorBBox.maxX,
                                        wallBBox.minX, wallBBox.maxX)
                                ));
                            }
                        } else {
                            failed++;
                            issues.add(Issue.error(
                                "DOOR_NOT_ON_WALL_PLANE",
                                doorGuid,
                                String.format("Door should be on wall plane (nearest: %s)", nearestWall),
                                String.format("Door bbox: X[%.2f-%.2f] Y[%.2f-%.2f]",
                                    doorBBox.minX, doorBBox.maxX, doorBBox.minY, doorBBox.maxY)
                            ));
                        }
                    } else {
                        failed++;
                        issues.add(Issue.error(
                            "DOOR_NO_WALL",
                            doorGuid,
                            "Door should be associated with a wall",
                            String.format("Door at X[%.2f-%.2f] Y[%.2f-%.2f] not near any wall",
                                doorBBox.minX, doorBBox.maxX, doorBBox.minY, doorBBox.maxY)
                        ));
                    }
                } else {
                    passed++;
                }
            }
        }
    }

    /**
     * Check if door is positioned on wall plane (perpendicular to wall thickness).
     *
     * Uses PLANE_TOLERANCE (50mm) to account for:
     * - Grid line positioning vs wall center positioning
     * - Minor offsets in interior wall placement
     */
    private boolean isOnWallPlane(BBox door, BBox wall) {
        // Wall is thin in one direction (thickness ~0.15m)
        boolean wallThinInX = wall.width() < BIMConstants.WALL_THIN_THRESHOLD;
        boolean wallThinInY = wall.depth() < BIMConstants.WALL_THIN_THRESHOLD;

        if (wallThinInX) {
            // Wall runs along Y axis, door should overlap wall X position
            double wallCenterX = (wall.minX + wall.maxX) / 2;
            return door.minX <= wallCenterX + PLANE_TOLERANCE && door.maxX >= wallCenterX - PLANE_TOLERANCE;
        } else if (wallThinInY) {
            // Wall runs along X axis, door should overlap wall Y position
            double wallCenterY = (wall.minY + wall.maxY) / 2;
            return door.minY <= wallCenterY + PLANE_TOLERANCE && door.maxY >= wallCenterY - PLANE_TOLERANCE;
        }

        return false;
    }

    /**
     * Check if door width fits within wall length.
     */
    private boolean doorFitsInWall(BBox door, BBox wall) {
        boolean wallThinInX = wall.width() < 0.3;

        if (wallThinInX) {
            // Wall runs along Y, door width should fit in wall Y range
            return door.minY >= wall.minY - TOLERANCE && door.maxY <= wall.maxY + TOLERANCE;
        } else {
            // Wall runs along X, door width should fit in wall X range
            return door.minX >= wall.minX - TOLERANCE && door.maxX <= wall.maxX + TOLERANCE;
        }
    }

    /**
     * 2. Validate windows are positioned correctly.
     */
    private void validateWindowWallRelationship() throws SQLException {
        String query = """
            SELECT
                m.guid, m.element_name,
                r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcWindow'
            """;

        Map<String, BBox> wallBBoxes = getWallBoundingBoxes();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                totalChecks++;
                String windowGuid = rs.getString("guid");

                BBox windowBBox = new BBox(
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                );

                // Similar logic to doors
                String nearestWall = findNearestWall(windowBBox, wallBBoxes);
                if (nearestWall != null) {
                    BBox wallBBox = wallBBoxes.get(nearestWall);
                    if (isOnWallPlane(windowBBox, wallBBox)) {
                        passed++;
                    } else {
                        failed++;
                        issues.add(Issue.warning(
                            "WINDOW_OFFSET_FROM_WALL",
                            windowGuid,
                            "Window should be on wall plane",
                            String.format("Window offset from nearest wall %s", nearestWall)
                        ));
                    }
                } else {
                    failed++;
                    issues.add(Issue.error(
                        "WINDOW_NO_WALL",
                        windowGuid,
                        "Window should be on a wall",
                        "No wall found near window position"
                    ));
                }
            }
        }
    }

    /**
     * 3. Validate wall frame members align correctly.
     *
     * Note: In wall framing, there are different member types:
     * - Studs: Full wall height (vertical members)
     * - Rails/Plates: Thin horizontal members (top/bottom plates, ~150mm)
     *
     * Heights will legitimately vary between these types.
     */
    private void validateWallFrameAlignment() throws SQLException {
        String query = """
            SELECT
                a.name as wall_name,
                a.assembly_guid,
                a.total_height
            FROM element_assemblies a
            WHERE a.assembly_type = 'WALL_PANEL'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                totalChecks++;
                String wallName = rs.getString("wall_name");
                String assemblyGuid = rs.getString("assembly_guid");
                double expectedHeight = rs.getDouble("total_height");

                // Get frame members with their names to distinguish studs from rails
                List<FrameMember> frameMembers = getFrameMembers(assemblyGuid);

                if (frameMembers.isEmpty()) {
                    failed++;
                    issues.add(Issue.error(
                        "WALL_NO_FRAMES",
                        wallName,
                        "Wall should have frame members",
                        "No frame members found"
                    ));
                    continue;
                }

                // Separate studs from rails (plates) based on name
                List<FrameMember> studs = frameMembers.stream()
                    .filter(m -> m.name.contains("STUD")).toList();
                List<FrameMember> rails = frameMembers.stream()
                    .filter(m -> m.name.contains("RAIL") || m.name.contains("PLATE")).toList();

                // Validate studs have consistent height (should be wall height)
                if (!studs.isEmpty()) {
                    double minStudHeight = studs.stream().mapToDouble(m -> m.bbox.height()).min().orElse(0);
                    double maxStudHeight = studs.stream().mapToDouble(m -> m.bbox.height()).max().orElse(0);

                    if (Math.abs(maxStudHeight - expectedHeight) > BIMConstants.STUD_HEIGHT_TOLERANCE) {
                        failed++;
                        issues.add(Issue.warning(
                            "STUD_HEIGHT_MISMATCH",
                            wallName,
                            String.format("Studs should be near wall height (%.2fm)", expectedHeight),
                            String.format("Stud heights: %.2fm to %.2fm", minStudHeight, maxStudHeight)
                        ));
                    } else {
                        passed++;
                    }
                } else {
                    passed++; // No studs to validate
                }
            }
        }
    }

    /**
     * Frame member record with name for type identification.
     */
    private record FrameMember(String name, BBox bbox) {}

    /**
     * 4. Validate assembly component positions are valid.
     */
    private void validateAssemblyComponentPositions() throws SQLException {
        String query = """
            SELECT
                a.name as assembly_name,
                c.component_guid,
                c.role,
                c.local_x, c.local_y, c.local_z
            FROM element_assemblies a
            JOIN assembly_components c USING(assembly_guid)
            WHERE c.optional = 0
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                totalChecks++;
                String assemblyName = rs.getString("assembly_name");
                String componentGuid = rs.getString("component_guid");
                String role = rs.getString("role");
                double localX = rs.getDouble("local_x");
                double localY = rs.getDouble("local_y");
                double localZ = rs.getDouble("local_z");

                // Check for NaN or unreasonable values
                if (Double.isNaN(localX) || Double.isNaN(localY) || Double.isNaN(localZ)) {
                    failed++;
                    issues.add(Issue.error(
                        "COMPONENT_INVALID_POSITION",
                        componentGuid,
                        "Position should be valid numbers",
                        String.format("Position contains NaN: (%.2f, %.2f, %.2f)", localX, localY, localZ)
                    ));
                } else if (Math.abs(localX) > BIMConstants.EXTREME_POSITION_THRESHOLD ||
                           Math.abs(localY) > BIMConstants.EXTREME_POSITION_THRESHOLD ||
                           Math.abs(localZ) > BIMConstants.EXTREME_POSITION_THRESHOLD) {
                    failed++;
                    issues.add(Issue.warning(
                        "COMPONENT_EXTREME_POSITION",
                        componentGuid,
                        "Position should be within reasonable bounds",
                        String.format("Position (%.2f, %.2f, %.2f) seems extreme", localX, localY, localZ)
                    ));
                } else {
                    passed++;
                }
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Map<String, BBox> getWallBoundingBoxes() throws SQLException {
        Map<String, BBox> result = new HashMap<>();

        String query = """
            SELECT
                a.name as wall_name,
                r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM element_assemblies a
            JOIN assembly_components c USING(assembly_guid)
            JOIN elements_meta m ON c.component_guid = m.guid
            JOIN elements_rtree r ON m.id = r.id
            WHERE a.assembly_type = 'WALL_PANEL'
              AND c.role = 'CLADDING'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                result.put(rs.getString("wall_name"), new BBox(
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                ));
            }
        }

        return result;
    }

    private String findContainingWall(BBox target, Map<String, BBox> walls) {
        return walls.entrySet().stream()
            .filter(e -> e.getValue().contains2D(target))
            .map(java.util.Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private String findNearestWall(BBox target, Map<String, BBox> walls) {
        String nearest = null;
        double minDist = Double.MAX_VALUE;

        double targetCenterX = (target.minX + target.maxX) / 2;
        double targetCenterY = (target.minY + target.maxY) / 2;

        for (var entry : walls.entrySet()) {
            BBox wall = entry.getValue();
            double wallCenterX = (wall.minX + wall.maxX) / 2;
            double wallCenterY = (wall.minY + wall.maxY) / 2;

            // For thin walls, check distance to wall plane
            boolean wallThinInX = wall.width() < 0.3;
            boolean wallThinInY = wall.depth() < 0.3;

            double dist;
            if (wallThinInX) {
                // Wall runs along Y, check X distance
                dist = Math.abs(targetCenterX - wallCenterX);
                // Also check if target Y overlaps wall Y range
                if (target.maxY < wall.minY || target.minY > wall.maxY) {
                    continue; // No Y overlap
                }
            } else if (wallThinInY) {
                // Wall runs along X, check Y distance
                dist = Math.abs(targetCenterY - wallCenterY);
                // Also check if target X overlaps wall X range
                if (target.maxX < wall.minX || target.minX > wall.maxX) {
                    continue; // No X overlap
                }
            } else {
                continue; // Not a thin wall
            }

            if (dist < minDist && dist < BIMConstants.WALL_PROXIMITY_DISTANCE) {
                minDist = dist;
                nearest = entry.getKey();
            }
        }

        return nearest;
    }

    private List<BBox> getFrameMemberBBoxes(String assemblyGuid) throws SQLException {
        return getFrameMembers(assemblyGuid).stream().map(m -> m.bbox).toList();
    }

    private List<FrameMember> getFrameMembers(String assemblyGuid) throws SQLException {
        List<FrameMember> result = new ArrayList<>();

        String query = """
            SELECT c.component_guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM assembly_components c
            JOIN elements_meta m ON c.component_guid = m.guid
            JOIN elements_rtree r ON m.id = r.id
            WHERE c.assembly_guid = ?
              AND c.role = 'FRAME'
            """;

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, assemblyGuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FrameMember(
                        rs.getString("component_guid"),
                        new BBox(
                            rs.getDouble("minX"), rs.getDouble("maxX"),
                            rs.getDouble("minY"), rs.getDouble("maxY"),
                            rs.getDouble("minZ"), rs.getDouble("maxZ")
                        )
                    ));
                }
            }
        }

        return result;
    }

    /**
     * Main method for standalone testing.
     */
    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "output/tb_lktn.db";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            AssemblyGeometryValidator validator = new AssemblyGeometryValidator(conn);
            ValidationResult result = validator.validate();
            result.print();

            System.exit(result.isValid() ? 0 : 1);
        }
    }
}
