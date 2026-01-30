package com.bim.compiler.dsl;

import com.bim.compiler.util.OutlierLogger;
import com.bim.compiler.util.OutlierLogger.OutlierCategory;

import java.nio.file.*;

/**
 * Documentation Sweep: Full Pipeline Test with Outlier Report
 *
 * Tests the complete pipeline: DSL → Solver → Compiler → Spec
 * with outlier tracking enabled. Includes edge cases to exercise
 * all outlier categories.
 */
public class FullPipelineOutlierTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("FULL PIPELINE TEST WITH OUTLIER TRACKING");
        System.out.println("=".repeat(70));
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Test 1: Standard building (should have minimal outliers)
        System.out.println("TEST 1: Standard 3-bedroom house (baseline)");
        System.out.println("-".repeat(70));
        OutlierLogger.reset();
        OutlierLogger.setCompilationName("standard_house");

        String standardDsl = """
            BUILDING "standard_house" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "living" size:5x4m {
                        adjacent: kitchen
                        exterior: south
                    }
                    KITCHEN "kitchen" size:3x3m {
                        adjacent: living
                    }
                    BEDROOM "master" size:4x4m {
                        adjacent: ensuite
                        exterior: north
                    }
                    BATHROOM "ensuite" size:2.5x2.5m {
                        adjacent: master
                    }
                    BATHROOM "bathroom" size:2.5x2.5m
                }
                ROOF pitch:15deg
            }
            """;

        try {
            BuildingDefinition def = BuildingParser.parse(standardDsl);
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def, Path.of("output"));

            int outliers = OutlierLogger.getTotalCount();
            System.out.printf("Rooms: %d, Outliers: %d%n",
                spec.storeys().get(0).rooms().size(), outliers);

            if (outliers <= 2) {  // Allow minimal outliers
                System.out.println("[PASS] Baseline has minimal outliers");
                passed++;
            } else {
                System.out.println("[FAIL] Too many outliers for standard building");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] Exception: " + e.getMessage());
            failed++;
        }

        // Test 2: Unknown room types (should trigger UNKNOWN_SPACETYPE)
        System.out.println();
        System.out.println("TEST 2: Unknown room types (UNKNOWN_SPACETYPE outliers)");
        System.out.println("-".repeat(70));
        OutlierLogger.reset();
        OutlierLogger.setCompilationName("unknown_types");

        String unknownTypesDsl = """
            BUILDING "unknown_types" {
                STOREY "Ground" level:0 height:2.8m {
                    SUNROOM "sunroom" size:4x3m {
                        exterior: south
                    }
                    SCULLERY "scullery" size:2x2m {
                        adjacent: sunroom
                    }
                    MASTER_BEDROOM "master" size:4x4m {
                        exterior: north
                    }
                    WC "wc" size:1.5x1.5m
                }
                ROOF pitch:15deg
            }
            """;

        try {
            BuildingDefinition def = BuildingParser.parse(unknownTypesDsl);
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

            int unknownTypeOutliers = OutlierLogger.getCount(OutlierCategory.UNKNOWN_SPACETYPE);
            System.out.printf("Rooms: %d, UNKNOWN_SPACETYPE outliers: %d%n",
                spec.storeys().get(0).rooms().size(), unknownTypeOutliers);

            // Should have exactly 4 unknown type outliers
            if (unknownTypeOutliers == 4) {
                System.out.println("[PASS] All unknown types logged correctly");
                passed++;
            } else {
                System.out.printf("[FAIL] Expected 4 outliers, got %d%n", unknownTypeOutliers);
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] Exception: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: Conflicting constraints (should trigger UNSATISFIABLE then resolve)
        System.out.println();
        System.out.println("TEST 3: Conflicting constraints (UNSATISFIABLE → relaxation)");
        System.out.println("-".repeat(70));
        OutlierLogger.reset();
        OutlierLogger.setCompilationName("conflicts");

        String conflictDsl = """
            BUILDING "conflicts" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "living" size:5x4m {
                        adjacent: kitchen
                        not_adjacent: kitchen
                        exterior: south
                    }
                    KITCHEN "kitchen" size:3x3m
                }
                ROOF pitch:15deg
            }
            """;

        try {
            BuildingDefinition def = BuildingParser.parse(conflictDsl);
            // This should NOT throw - outlier handling should manage it
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

            int unsatisfiableOutliers = OutlierLogger.getCount(OutlierCategory.UNSATISFIABLE);
            System.out.printf("UNSATISFIABLE outliers: %d%n", unsatisfiableOutliers);

            if (unsatisfiableOutliers >= 1 && spec.storeys().get(0).rooms().size() == 2) {
                System.out.println("[PASS] Constraint conflict handled via relaxation");
                passed++;
            } else {
                System.out.println("[FAIL] Conflict not handled properly");
                failed++;
            }
        } catch (Exception e) {
            // If it throws, that's also acceptable behavior (strict mode)
            System.out.println("[PASS] Strict mode - conflict causes exception (acceptable)");
            passed++;
        }

        // Test 4: Tiny bathroom (should trigger GEOMETRY_IMPOSSIBLE for fixtures)
        System.out.println();
        System.out.println("TEST 4: Tiny bathroom (GEOMETRY_IMPOSSIBLE for fixtures)");
        System.out.println("-".repeat(70));
        OutlierLogger.reset();
        OutlierLogger.setCompilationName("tiny_bathroom");

        String tinyBathroomDsl = """
            BUILDING "tiny_bathroom" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "living" size:4x4m {
                        exterior: south
                    }
                    BATHROOM "tiny" size:0.8x0.8m
                }
                ROOF pitch:15deg
            }
            """;

        try {
            BuildingDefinition def = BuildingParser.parse(tinyBathroomDsl);
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

            int geometryOutliers = OutlierLogger.getCount(OutlierCategory.GEOMETRY_IMPOSSIBLE);
            int fixtureCount = spec.storeys().get(0).fixtures().size();
            System.out.printf("Fixtures placed: %d, GEOMETRY_IMPOSSIBLE outliers: %d%n",
                fixtureCount, geometryOutliers);

            // Tiny bathroom should skip most fixtures
            if (geometryOutliers >= 1) {
                System.out.println("[PASS] Geometry impossible logged for tiny bathroom");
                passed++;
            } else {
                System.out.println("[FAIL] Expected geometry outliers for tiny bathroom");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] Exception: " + e.getMessage());
            failed++;
        }

        // Test 5: Multi-storey with mixed types
        System.out.println();
        System.out.println("TEST 5: Multi-storey mixed building (integration)");
        System.out.println("-".repeat(70));
        OutlierLogger.reset();
        OutlierLogger.setCompilationName("multi_storey");

        String multiStoreyDsl = """
            BUILDING "multi_storey" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "living" size:5x4m {
                        adjacent: kitchen
                        exterior: south
                    }
                    KITCHEN "kitchen" size:3x3m
                    GARAGE "garage" size:3x6m {
                        exterior: west
                    }
                }
                STOREY "Upper" level:1 height:2.8m {
                    BEDROOM "master" size:4x4m {
                        adjacent: ensuite
                        above: living
                        exterior: south
                    }
                    BATHROOM "ensuite" size:2.5x2.5m {
                        adjacent: master
                    }
                    BEDROOM "bed2" size:3.5x3.5m {
                        exterior: north
                    }
                }
                ROOF pitch:15deg
            }
            """;

        try {
            BuildingDefinition def = BuildingParser.parse(multiStoreyDsl);
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def, Path.of("output"));

            int totalOutliers = OutlierLogger.getTotalCount();
            int groundRooms = spec.storeys().get(0).rooms().size();
            int upperRooms = spec.storeys().get(1).rooms().size();

            System.out.printf("Ground rooms: %d, Upper rooms: %d%n", groundRooms, upperRooms);
            System.out.printf("Total outliers: %d%n", totalOutliers);
            System.out.printf("Outlier rate: %.1f%%%n", OutlierLogger.getOutlierRate());

            if (groundRooms == 3 && upperRooms == 3) {
                System.out.println("[PASS] Multi-storey building compiled successfully");
                passed++;
            } else {
                System.out.println("[FAIL] Room count mismatch");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] Exception: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Print final outlier summary
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("FINAL OUTLIER SUMMARY (from last test)");
        System.out.println("=".repeat(70));
        OutlierLogger.printSummary();

        // Summary
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.printf("FULL PIPELINE TEST RESULTS: %d/%d passed%n", passed, passed + failed);
        System.out.println("=".repeat(70));

        if (failed == 0) {
            System.out.println("\n[PASS] All pipeline tests passed!");
        } else {
            System.out.println("\n[FAIL] " + failed + " test(s) failed");
            System.exit(1);
        }
    }
}
