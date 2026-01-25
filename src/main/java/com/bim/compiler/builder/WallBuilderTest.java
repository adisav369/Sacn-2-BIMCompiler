package com.bim.compiler.builder;

import com.bim.compiler.db.FederatedDBReader;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMConstants;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.validation.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Round-trip test for WallBuilder.
 *
 * Test flow:
 * 1. Pick existing wall with opening
 * 2. Extract spec
 * 3. Generate new wall
 * 4. Validate generated wall
 * 5. Compare geometry to original
 */
public class WallBuilderTest {

    private static final String DB_PATH =
        "/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db";

    private static final double TOLERANCE = BIMConstants.TOLERANCE; // 5mm

    public static void main(String[] args) {
        try {
            new WallBuilderTest().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws SQLException {
        System.out.println("=".repeat(70));
        System.out.println("WALL BUILDER ROUND-TRIP TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        try (FederatedDBReader db = new FederatedDBReader(DB_PATH)) {
            db.loadAllElements();
            System.out.println();

            // Get all walls
            List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);
            System.out.printf("Total walls in model: %d%n", walls.size());

            // Find walls with openings
            List<ISpatialElement> openings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);
            System.out.printf("Total openings in model: %d%n", openings.size());

            Map<ISpatialElement, List<ISpatialElement>> wallsWithOpenings = new LinkedHashMap<>();

            for (ISpatialElement wall : walls) {
                List<ISpatialElement> wallOpenings = new ArrayList<>();
                for (ISpatialElement opening : openings) {
                    if (wall.getBoundingBox().overlaps(opening.getBoundingBox())) {
                        wallOpenings.add(opening);
                    }
                }
                if (!wallOpenings.isEmpty()) {
                    wallsWithOpenings.put(wall, wallOpenings);
                }
            }

            System.out.printf("Walls with openings: %d%n", wallsWithOpenings.size());
            System.out.println();

            // Test validators
            WallThicknessValidator thicknessValidator = new WallThicknessValidator();
            OpeningPlacementValidator openingValidator = new OpeningPlacementValidator();
            WallBuilder builder = new WallBuilder();

            int passed = 0;
            int failed = 0;
            int tested = 0;

            // Test first 20 walls with openings
            int maxTests = 20;

            System.out.println("-".repeat(70));
            System.out.println("ROUND-TRIP TESTS");
            System.out.println("-".repeat(70));

            for (Map.Entry<ISpatialElement, List<ISpatialElement>> entry :
                    wallsWithOpenings.entrySet()) {

                if (tested >= maxTests) break;
                tested++;

                ISpatialElement originalWall = entry.getKey();
                List<ISpatialElement> originalOpenings = entry.getValue();

                // 1. Extract spec
                WallSpec spec = WallSpec.extractFrom(originalWall, originalOpenings);

                // 2. Generate new wall
                builder.clear();
                ISpatialElement generatedWall = builder.build(spec);
                List<ISpatialElement> generatedElements = builder.getGeneratedElements();

                // 3. Validate generated wall
                List<ISpatialElement> allGenerated = new ArrayList<>(generatedElements);
                ValidationResult thicknessResult = thicknessValidator.validate(
                    generatedWall, allGenerated);

                // Validate openings
                ValidationResult openingResult = new ValidationResult();
                for (ISpatialElement genElement : generatedElements) {
                    if (genElement.getType() == BIMObjectType.IFC_OPENING_ELEMENT) {
                        openingResult.merge(openingValidator.validate(genElement, allGenerated));
                    }
                }

                // 4. Compare geometry
                boolean bboxMatch = WallBuilder.bboxMatches(
                    originalWall.getBoundingBox(),
                    generatedWall.getBoundingBox(),
                    TOLERANCE
                );

                // Check opening count
                long genOpeningCount = generatedElements.stream()
                    .filter(e -> e.getType() == BIMObjectType.IFC_OPENING_ELEMENT)
                    .count();
                boolean openingCountMatch = genOpeningCount == originalOpenings.size();

                // Determine pass/fail
                boolean testPassed = !thicknessResult.hasErrors()
                    && !openingResult.hasErrors()
                    && bboxMatch
                    && openingCountMatch;

                if (testPassed) {
                    passed++;
                    System.out.printf("[PASS] Wall %s: bbox=%.3fm x %.3fm x %.3fm, openings=%d%n",
                        truncateGuid(originalWall.getGuid()),
                        spec.length(),
                        spec.thickness().getMeters(),
                        spec.height(),
                        originalOpenings.size());
                } else {
                    failed++;
                    System.out.printf("[FAIL] Wall %s:%n", truncateGuid(originalWall.getGuid()));
                    if (thicknessResult.hasErrors()) {
                        System.out.println("  - Thickness validation failed");
                    }
                    if (openingResult.hasErrors()) {
                        System.out.println("  - Opening placement failed");
                    }
                    if (!bboxMatch) {
                        System.out.println("  - BBox mismatch");
                        printBboxDiff(originalWall.getBoundingBox(), generatedWall.getBoundingBox());
                    }
                    if (!openingCountMatch) {
                        System.out.printf("  - Opening count: expected %d, got %d%n",
                            originalOpenings.size(), genOpeningCount);
                    }
                }
            }

            // Summary
            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("SUMMARY");
            System.out.println("=".repeat(70));
            System.out.printf("Tested: %d walls%n", tested);
            System.out.printf("Passed: %d (%.1f%%)%n", passed, passed * 100.0 / tested);
            System.out.printf("Failed: %d (%.1f%%)%n", failed, failed * 100.0 / tested);
            System.out.println();

            if (passed == tested) {
                System.out.println("✓ ALL ROUND-TRIP TESTS PASSED");
            } else {
                System.out.println("✗ SOME TESTS FAILED - review above");
            }

            // Test a specific wall with multiple openings
            testSpecificWall(db, builder);
        }
    }

    private void testSpecificWall(FederatedDBReader db, WallBuilder builder) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("DETAILED TEST: Wall with multiple openings");
        System.out.println("=".repeat(70));

        // Find a wall with multiple openings
        List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);
        List<ISpatialElement> openings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);

        ISpatialElement testWall = null;
        List<ISpatialElement> testOpenings = null;

        for (ISpatialElement wall : walls) {
            List<ISpatialElement> wallOpenings = new ArrayList<>();
            for (ISpatialElement o : openings) {
                if (wall.getBoundingBox().overlaps(o.getBoundingBox())) {
                    wallOpenings.add(o);
                }
            }
            if (wallOpenings.size() >= 2) {
                testWall = wall;
                testOpenings = wallOpenings;
                break;
            }
        }

        if (testWall == null) {
            System.out.println("No wall with multiple openings found");
            return;
        }

        System.out.printf("Wall GUID: %s%n", testWall.getGuid());
        System.out.printf("Openings: %d%n", testOpenings.size());
        System.out.println();

        // Extract and display spec
        WallSpec spec = WallSpec.extractFrom(testWall, testOpenings);

        System.out.println("Extracted WallSpec:");
        System.out.printf("  Start: %s%n", spec.start());
        System.out.printf("  End: %s%n", spec.end());
        System.out.printf("  Length: %.3fm%n", spec.length());
        System.out.printf("  Thickness: %s (%.0fmm)%n", spec.thickness(), spec.thickness().getMeters() * 1000);
        System.out.printf("  Height: %.3fm%n", spec.height());
        System.out.println("  Openings:");

        for (int i = 0; i < spec.openings().size(); i++) {
            OpeningSpec o = spec.openings().get(i);
            System.out.printf("    [%d] offset=%.3fm, floor=%.3fm, %.3fm x %.3fm%n",
                i, o.offsetAlongWall(), o.offsetFromFloor(), o.width(), o.height());
        }

        // Build and compare
        System.out.println();
        System.out.println("Building wall from spec...");
        builder.clear();
        ISpatialElement generated = builder.build(spec);

        System.out.println();
        System.out.println("Original BBox: " + testWall.getBoundingBox());
        System.out.println("Generated BBox: " + generated.getBoundingBox());

        boolean match = WallBuilder.bboxMatches(
            testWall.getBoundingBox(), generated.getBoundingBox(), TOLERANCE);
        System.out.printf("BBox match (within %.0fmm): %s%n", TOLERANCE * 1000, match ? "YES" : "NO");

        if (!match) {
            printBboxDiff(testWall.getBoundingBox(), generated.getBoundingBox());
        }
    }

    private void printBboxDiff(BoundingBox orig, BoundingBox gen) {
        System.out.printf("    minX: %.4f vs %.4f (diff: %.4f)%n",
            orig.minX(), gen.minX(), Math.abs(orig.minX() - gen.minX()));
        System.out.printf("    maxX: %.4f vs %.4f (diff: %.4f)%n",
            orig.maxX(), gen.maxX(), Math.abs(orig.maxX() - gen.maxX()));
        System.out.printf("    minY: %.4f vs %.4f (diff: %.4f)%n",
            orig.minY(), gen.minY(), Math.abs(orig.minY() - gen.minY()));
        System.out.printf("    maxY: %.4f vs %.4f (diff: %.4f)%n",
            orig.maxY(), gen.maxY(), Math.abs(orig.maxY() - gen.maxY()));
        System.out.printf("    minZ: %.4f vs %.4f (diff: %.4f)%n",
            orig.minZ(), gen.minZ(), Math.abs(orig.minZ() - gen.minZ()));
        System.out.printf("    maxZ: %.4f vs %.4f (diff: %.4f)%n",
            orig.maxZ(), gen.maxZ(), Math.abs(orig.maxZ() - gen.maxZ()));
    }

    private String truncateGuid(String guid) {
        return guid.length() > 12 ? guid.substring(0, 12) + "..." : guid;
    }
}
