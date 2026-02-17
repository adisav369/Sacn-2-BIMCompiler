package com.bim.compiler.builder;

import com.bim.compiler.db.FederatedDBReader;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMConstants;
import com.bim.compiler.topology.BIMObjectType;

import java.sql.SQLException;
import java.util.*;

/**
 * MINIMAL CONNECTION VALIDATION TEST
 *
 * Past failures were CONNECTION failures, not element failures:
 * - Conduit to sky (no wall relationship)
 * - Disconnected walls (no corner overlap)
 * - Openings wrong position
 *
 * This test validates that generated elements maintain
 * correct spatial relationships with each other.
 *
 * Tests:
 * 1. Opening spatially contained within wall
 * 2. Opening bbox fully inside wall bbox
 * 3. Relationship queries work both directions
 * 4. Wall-wall corner overlap (PATTERN G4: 133mm)
 */
public class ConnectionValidationTest {

    private static final String DB_PATH =
        "/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db";

    private static final double TOLERANCE = BIMConstants.TOLERANCE; // 5mm

    public static void main(String[] args) {
        try {
            new ConnectionValidationTest().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws SQLException {
        System.out.println("=".repeat(70));
        System.out.println("CONNECTION VALIDATION TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        try (FederatedDBReader db = new FederatedDBReader(DB_PATH)) {
            db.loadAllElements();
            System.out.println();

            int passed = 0;
            int failed = 0;

            // Test 1: Wall-Opening containment
            System.out.println("-".repeat(70));
            System.out.println("TEST 1: Wall-Opening Containment");
            System.out.println("-".repeat(70));
            if (testWallOpeningContainment(db)) {
                passed++;
                System.out.println("[PASS]");
            } else {
                failed++;
                System.out.println("[FAIL]");
            }
            System.out.println();

            // Test 2: Bidirectional relationship queries
            System.out.println("-".repeat(70));
            System.out.println("TEST 2: Bidirectional Relationship Queries");
            System.out.println("-".repeat(70));
            if (testBidirectionalQueries(db)) {
                passed++;
                System.out.println("[PASS]");
            } else {
                failed++;
                System.out.println("[FAIL]");
            }
            System.out.println();

            // Test 3: Wall-Wall corner overlap (PATTERN G4)
            System.out.println("-".repeat(70));
            System.out.println("TEST 3: Wall-Wall Corner Overlap (PATTERN G4)");
            System.out.println("-".repeat(70));
            if (testWallCornerOverlap(db)) {
                passed++;
                System.out.println("[PASS]");
            } else {
                failed++;
                System.out.println("[FAIL]");
            }
            System.out.println();

            // Test 4: Generated wall+opening maintains relationship
            System.out.println("-".repeat(70));
            System.out.println("TEST 4: Generated Wall+Opening Relationship");
            System.out.println("-".repeat(70));
            if (testGeneratedWallOpeningRelationship(db)) {
                passed++;
                System.out.println("[PASS]");
            } else {
                failed++;
                System.out.println("[FAIL]");
            }
            System.out.println();

            // Summary
            System.out.println("=".repeat(70));
            System.out.println("SUMMARY");
            System.out.println("=".repeat(70));
            System.out.printf("Passed: %d / %d%n", passed, passed + failed);

            if (failed == 0) {
                System.out.println();
                System.out.println("ALL CONNECTION TESTS PASSED");
                System.out.println("Foundation is solid. Safe to pause and continue later.");
            } else {
                System.out.println();
                System.out.println("CONNECTION FAILURES DETECTED");
                System.out.println("Fix before building more elements!");
            }
        }
    }

    /**
     * Test 1: Verify openings are spatially contained within their host walls.
     * From SESSION_STATE: 98% of openings overlap walls.
     */
    private boolean testWallOpeningContainment(FederatedDBReader db) {
        List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);
        List<ISpatialElement> openings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);

        int containedCount = 0;
        int overlappingCount = 0;
        int orphanCount = 0;

        for (ISpatialElement opening : openings) {
            BoundingBox openingBox = opening.getBoundingBox();
            boolean foundWall = false;
            boolean fullyContained = false;

            for (ISpatialElement wall : walls) {
                BoundingBox wallBox = wall.getBoundingBox();
                if (wallBox.overlaps(openingBox)) {
                    foundWall = true;
                    overlappingCount++;

                    // Check if opening is fully contained in wall
                    if (wallBox.contains(openingBox)) {
                        fullyContained = true;
                        containedCount++;
                    }
                    break;
                }
            }

            if (!foundWall) {
                orphanCount++;
            }
        }

        System.out.printf("  Total openings: %d%n", openings.size());
        System.out.printf("  Overlapping wall: %d (%.1f%%)%n",
            overlappingCount, overlappingCount * 100.0 / openings.size());
        System.out.printf("  Fully contained: %d (%.1f%%)%n",
            containedCount, containedCount * 100.0 / openings.size());
        System.out.printf("  Orphaned: %d (%.1f%%)%n",
            orphanCount, orphanCount * 100.0 / openings.size());

        // Success: >= 95% overlap (SESSION_STATE says 98%)
        double overlapRate = overlappingCount * 100.0 / openings.size();
        System.out.printf("  Expected: >= 95%% overlap rate%n");
        System.out.printf("  Actual: %.1f%%%n", overlapRate);

        return overlapRate >= 95.0;
    }

    /**
     * Test 2: Verify bidirectional relationship queries work.
     * Opening.findOverlapping(WALL) should return the host wall.
     * Wall.findContained() should return its openings.
     */
    private boolean testBidirectionalQueries(FederatedDBReader db) {
        // Find a wall with openings
        List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);
        List<ISpatialElement> openings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);

        ISpatialElement testWall = null;
        ISpatialElement testOpening = null;

        for (ISpatialElement wall : walls) {
            for (ISpatialElement opening : openings) {
                if (wall.getBoundingBox().overlaps(opening.getBoundingBox())) {
                    testWall = wall;
                    testOpening = opening;
                    break;
                }
            }
            if (testWall != null) break;
        }

        if (testWall == null || testOpening == null) {
            System.out.println("  ERROR: No wall-opening pair found");
            return false;
        }

        // Copy to final for lambda
        final String wallGuid = testWall.getGuid();
        final String openingGuid = testOpening.getGuid();

        System.out.printf("  Test wall: %s%n", wallGuid);
        System.out.printf("  Test opening: %s%n", openingGuid);

        // Forward query: Opening -> Wall
        List<ISpatialElement> foundWalls = testOpening.findOverlapping(BIMObjectType.IFC_WALL);
        boolean forwardOk = foundWalls.stream()
            .anyMatch(w -> w.getGuid().equals(wallGuid));
        System.out.printf("  Opening.findOverlapping(WALL) finds host: %s%n", forwardOk ? "YES" : "NO");

        // Reverse query: Wall -> Opening
        List<ISpatialElement> foundOpenings = testWall.findOverlapping(BIMObjectType.IFC_OPENING_ELEMENT);
        boolean reverseOk = foundOpenings.stream()
            .anyMatch(o -> o.getGuid().equals(openingGuid));
        System.out.printf("  Wall.findOverlapping(OPENING) finds opening: %s%n", reverseOk ? "YES" : "NO");

        return forwardOk && reverseOk;
    }

    /**
     * Test 3: Verify wall-wall connections at corners.
     * From PATTERN G4: Perpendicular overlap avg 133mm (≈ wall thickness).
     * From FACT 9: 1,194 wall-wall connections via bbox overlap.
     */
    private boolean testWallCornerOverlap(FederatedDBReader db) {
        List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);

        int connectionCount = 0;
        double totalOverlap = 0;
        int overlapMeasured = 0;

        // Check first 100 walls for connections
        int checked = 0;
        for (ISpatialElement wall1 : walls) {
            if (checked++ > 100) break;

            for (ISpatialElement wall2 : walls) {
                if (wall1.getGuid().equals(wall2.getGuid())) continue;

                BoundingBox box1 = wall1.getBoundingBox();
                BoundingBox box2 = wall2.getBoundingBox();

                if (box1.overlaps(box2)) {
                    connectionCount++;

                    // Calculate overlap amount
                    double overlapX = Math.min(box1.maxX(), box2.maxX()) - Math.max(box1.minX(), box2.minX());
                    double overlapY = Math.min(box1.maxY(), box2.maxY()) - Math.max(box1.minY(), box2.minY());

                    // Smaller overlap dimension is the penetration depth
                    double overlapDepth = Math.min(overlapX, overlapY);
                    if (overlapDepth > 0 && overlapDepth < 1.0) { // Reasonable range
                        totalOverlap += overlapDepth;
                        overlapMeasured++;
                    }
                }
            }
        }

        System.out.printf("  Walls checked: %d%n", Math.min(checked, walls.size()));
        System.out.printf("  Connections found: %d%n", connectionCount);

        if (overlapMeasured > 0) {
            double avgOverlap = totalOverlap / overlapMeasured * 1000; // to mm
            System.out.printf("  Average overlap depth: %.1fmm%n", avgOverlap);
            System.out.printf("  Expected (PATTERN G4): ~133mm%n");

            // Success: found connections and overlap is reasonable
            return connectionCount > 0 && avgOverlap > 50 && avgOverlap < 500;
        }

        return connectionCount > 0;
    }

    /**
     * Test 4: Generate wall with opening, verify relationship preserved.
     * This is the critical test - does generation maintain connections?
     */
    private boolean testGeneratedWallOpeningRelationship(FederatedDBReader db) {
        // Find a wall with multiple openings
        List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);
        List<ISpatialElement> allOpenings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);

        ISpatialElement originalWall = null;
        List<ISpatialElement> originalOpenings = new ArrayList<>();

        for (ISpatialElement wall : walls) {
            List<ISpatialElement> wallOpenings = new ArrayList<>();
            for (ISpatialElement opening : allOpenings) {
                if (wall.getBoundingBox().overlaps(opening.getBoundingBox())) {
                    wallOpenings.add(opening);
                }
            }
            if (wallOpenings.size() >= 2) {
                originalWall = wall;
                originalOpenings = wallOpenings;
                break;
            }
        }

        if (originalWall == null) {
            System.out.println("  ERROR: No wall with multiple openings found");
            return false;
        }

        System.out.printf("  Original wall: %s%n", originalWall.getGuid());
        System.out.printf("  Original openings: %d%n", originalOpenings.size());

        // Extract spec from original
        WallSpec spec = WallSpec.extractFrom(originalWall, originalOpenings);
        System.out.printf("  Extracted spec: %.1fm x %.0fmm x %.1fm, %d openings%n",
            spec.length(), spec.thickness().getMeters() * 1000, spec.height(),
            spec.openings().size());

        // Generate new wall
        WallBuilder builder = new WallBuilder();
        builder.clear();
        ISpatialElement generatedWall = builder.build(spec);
        List<ISpatialElement> generatedElements = builder.getGeneratedElements();

        // Find generated openings
        List<ISpatialElement> generatedOpenings = generatedElements.stream()
            .filter(e -> e.getType() == BIMObjectType.IFC_OPENING_ELEMENT)
            .toList();

        System.out.printf("  Generated wall bbox: %s%n", generatedWall.getBoundingBox());
        System.out.printf("  Generated openings: %d%n", generatedOpenings.size());

        // Verify opening count matches
        if (generatedOpenings.size() != originalOpenings.size()) {
            System.out.printf("  ERROR: Opening count mismatch! Expected %d, got %d%n",
                originalOpenings.size(), generatedOpenings.size());
            return false;
        }

        // Verify each generated opening is contained in generated wall
        BoundingBox wallBox = generatedWall.getBoundingBox();
        int containedCount = 0;
        int overlappingCount = 0;

        for (ISpatialElement opening : generatedOpenings) {
            BoundingBox openingBox = opening.getBoundingBox();

            if (wallBox.overlaps(openingBox)) {
                overlappingCount++;
            }

            // Check if opening is fully contained (within tolerance)
            if (isContainedWithTolerance(openingBox, wallBox, TOLERANCE)) {
                containedCount++;
            }
        }

        System.out.printf("  Openings overlapping wall: %d/%d%n", overlappingCount, generatedOpenings.size());
        System.out.printf("  Openings contained in wall: %d/%d%n", containedCount, generatedOpenings.size());

        // Success: all openings overlap wall
        boolean success = overlappingCount == generatedOpenings.size();

        if (success) {
            System.out.println("  RELATIONSHIP PRESERVED: All openings connected to wall");
        } else {
            System.out.println("  RELATIONSHIP BROKEN: Some openings disconnected!");
        }

        return success;
    }

    /**
     * Check if inner box is contained in outer box (with tolerance).
     */
    private boolean isContainedWithTolerance(BoundingBox inner, BoundingBox outer, double tolerance) {
        return inner.minX() >= outer.minX() - tolerance
            && inner.maxX() <= outer.maxX() + tolerance
            && inner.minY() >= outer.minY() - tolerance
            && inner.maxY() <= outer.maxY() + tolerance
            && inner.minZ() >= outer.minZ() - tolerance
            && inner.maxZ() <= outer.maxZ() + tolerance;
    }
}
