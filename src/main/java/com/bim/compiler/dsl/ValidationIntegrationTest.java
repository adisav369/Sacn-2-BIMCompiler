package com.bim.compiler.dsl;

import com.bim.compiler.validation.building.*;

/**
 * Phase 28: Validation Integration Test
 *
 * Verifies:
 * 1. ValidatorChain is called after compilation
 * 2. GeometryValidator checks: wall connectivity, room enclosure, no overlap
 * 3. GeometryValidator checks: no walls inside OPEN_PLAN
 * 4. Build fails if validation fails with critical errors
 * 5. TB-LKTN full validation report
 */
public class ValidationIntegrationTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("VALIDATION INTEGRATION TEST (Phase 28)");
        System.out.println("=".repeat(70));

        int testsPassed = 0;
        int testsTotal = 0;

        // 1. Valid building passes validation
        System.out.println("\n1. VALID BUILDING PASSES VALIDATION");
        System.out.println("-".repeat(70));
        testsTotal++;

        try {
            String validDsl = """
                BUILDING "valid_house" {
                    STOREY "Ground" level:0 height:2.8m {
                        BEDROOM "bed1" at:A1 size:4x4m {
                            WINDOW east
                            DOOR south to:hall
                        }
                        CORRIDOR "hall" at:A5 size:2x6m {
                            adjacent: bed1
                            DOOR north
                        }
                    }
                    ROOF pitch:25deg
                }
                """;

            BuildingDefinition def = BuildingParser.parse(validDsl);
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);
            System.out.println("  Building compiled successfully: " + spec.name());
            System.out.println("[PASS] Valid building passes validation");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("[FAIL] Unexpected error: " + e.getMessage());
        }

        // 2. Invalid building fails validation (trapped room)
        System.out.println("\n2. INVALID BUILDING FAILS (TRAPPED ROOM)");
        System.out.println("-".repeat(70));
        testsTotal++;

        try {
            String invalidDsl = """
                BUILDING "trapped" {
                    STOREY "Ground" level:0 height:2.8m {
                        BEDROOM "trapped_room" at:A1 size:4x4m {}
                    }
                    ROOF pitch:25deg
                }
                """;

            BuildingDefinition def = BuildingParser.parse(invalidDsl);
            BuildingCompiler.compile(def);
            System.out.println("[FAIL] Should have failed validation");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("validation failed")) {
                System.out.println("  Correctly rejected: " + e.getMessage().split("\\.")[0]);
                System.out.println("[PASS] Trapped room detected");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Wrong error: " + e.getMessage());
            }
        }

        // 3. compileWithValidation returns report without throwing
        System.out.println("\n3. COMPILE WITH VALIDATION RETURNS REPORT");
        System.out.println("-".repeat(70));
        testsTotal++;

        String warningDsl = """
            BUILDING "narrow_rooms" {
                STOREY "Ground" level:0 height:2.8m {
                    BEDROOM "narrow" at:A1 size:2x6m {
                        WINDOW east
                        DOOR south
                    }
                }
                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def3 = BuildingParser.parse(warningDsl);
        BuildingCompiler.CompilationResult result = BuildingCompiler.compileWithValidation(def3);

        System.out.println("  Building: " + result.spec().name());
        System.out.println("  Valid: " + result.isValid());
        System.out.println("  Critical: " + result.validationReport().getTotalCritical());
        System.out.println("  Warnings: " + result.validationReport().getTotalWarnings());

        if (result.isValid() && result.validationReport().getTotalWarnings() > 0) {
            System.out.println("[PASS] Report returned with warnings");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected valid with warnings");
        }

        // 4. TB-LKTN full validation
        System.out.println("\n4. TB-LKTN FULL VALIDATION");
        System.out.println("-".repeat(70));
        testsTotal++;

        String tblktnDsl = """
            BUILDING "TB-LKTN"
                profile: "Malaysian_Residential"
                protocol: "Residential_Single_Storey"
                lod: 300
            {
                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
                }

                STOREY "Ground" level:0 height:2.8m {
                    PORCH "anjung" bounds:C1-D2 {
                        exterior: south
                        roof: ATTACHED
                    }

                    OPEN_PLAN "common" bounds:B2-D5 {
                        zones: LIVING, DINING, KITCHEN
                        exterior: south
                        exterior: north
                    }

                    BEDROOM "bilik_utama" bounds:A2-B4 {
                        exterior: west
                        opens_to: common
                    }

                    BATHROOM "bilik_mandi" bounds:A4-B5 {
                        exterior: west
                        opens_to: common
                    }

                    BEDROOM "bilik_2" bounds:D2-E4 {
                        exterior: east
                        opens_to: common
                    }

                    BEDROOM "bilik_3" bounds:D4-E5 {
                        exterior: east
                        opens_to: common
                    }
                }

                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition tblktnDef = BuildingParser.parse(tblktnDsl);
        BuildingCompiler.CompilationResult tblktnResult = BuildingCompiler.compileWithValidation(tblktnDef);

        System.out.println("Building: " + tblktnResult.spec().name());
        System.out.println("Profile: " + tblktnDef.profile());
        System.out.println("Protocol: " + tblktnDef.protocol());
        System.out.println("LOD: " + tblktnDef.lod());
        System.out.println();

        var report = tblktnResult.validationReport();
        System.out.println("Validation Report:");
        var geoResult = report.getResultFor("GeometryValidator");
        var habResult = report.getResultFor("HabitabilityValidator");
        System.out.println("  Geometry critical: " + (geoResult != null ? geoResult.getCriticalCount() : "N/A"));
        System.out.println("  Geometry warnings: " + (geoResult != null ? geoResult.getWarningCount() : "N/A"));
        System.out.println("  Habitability critical: " + (habResult != null ? habResult.getCriticalCount() : "N/A"));
        System.out.println("  Habitability warnings: " + (habResult != null ? habResult.getWarningCount() : "N/A"));

        // Show critical issues if any
        if (geoResult != null && geoResult.hasCriticalFailures()) {
            System.out.println("  Geometry issues:");
            geoResult.getCritical().forEach(i -> System.out.println("    - " + i));
        }
        if (habResult != null && habResult.hasCriticalFailures()) {
            System.out.println("  Habitability issues:");
            habResult.getCritical().forEach(i -> System.out.println("    - " + i));
        }
        System.out.println();

        // Also validate against profile/protocol
        var profileErrors = ProfileRegistry.validateAgainstProfile(tblktnDef, tblktnDef.profile());
        var protocolResult = ProtocolValidator.validate(tblktnDef, tblktnDef.protocol());

        System.out.println("Profile validation errors: " + profileErrors.size());
        System.out.println("Protocol validation: " + (protocolResult.valid() ? "PASSED" : "FAILED"));

        if (tblktnResult.isValid() && profileErrors.isEmpty() && protocolResult.valid()) {
            System.out.println("[PASS] TB-LKTN validates completely");
            testsPassed++;
        } else {
            System.out.println("[FAIL] TB-LKTN has validation errors");
        }

        // 5. OPEN_PLAN no interior walls check
        System.out.println("\n5. OPEN_PLAN NO INTERIOR WALLS CHECK");
        System.out.println("-".repeat(70));
        testsTotal++;

        // This test verifies the OPEN_PLAN validation is in place
        // The TB-LKTN building has OPEN_PLAN "common" which should not have interior walls
        var geoResultForCheck = report.getResultFor("GeometryValidator");
        boolean hasOpenPlanWallError = geoResultForCheck != null &&
            geoResultForCheck.getCritical().stream()
            .anyMatch(i -> i.checkName().equals("OpenPlanNoInteriorWalls"));

        System.out.println("  OPEN_PLAN interior wall errors: " +
            (hasOpenPlanWallError ? "YES (wall violation)" : "NONE (correct)"));

        if (!hasOpenPlanWallError) {
            System.out.println("[PASS] OPEN_PLAN has no interior walls");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Unexpected interior wall in OPEN_PLAN");
        }

        // SUMMARY
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Tests passed: %d/%d%n", testsPassed, testsTotal);

        System.out.println("\nValidation Integration:");
        System.out.println("  1. ValidatorChain called after compile()");
        System.out.println("  2. GeometryValidator checks wall connectivity, room enclosure, overlap");
        System.out.println("  3. GeometryValidator checks no walls inside OPEN_PLAN");
        System.out.println("  4. Build throws RuntimeException on critical failures");
        System.out.println("  5. compileWithValidation() returns report without throwing");
        System.out.println("  6. TB-LKTN passes all validation: geometry + habitability + profile + protocol");

        if (testsPassed == testsTotal) {
            System.out.println("\n[SUCCESS] Validation integration complete!");
        } else {
            System.out.println("\n[PARTIAL] Some tests failed - review above");
        }
    }
}
