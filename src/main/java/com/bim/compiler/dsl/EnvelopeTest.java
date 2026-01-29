package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;

/**
 * Phase 28: ENVELOPE Completion Test
 *
 * Verifies:
 * 1. FOUNDATION parsing (type, depth, thickness)
 * 2. DRAINAGE parsing (perimeter drain, downpipes)
 * 3. ROOF-DRAIN correlation validation
 * 4. Complete envelope chain
 */
public class EnvelopeTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("ENVELOPE COMPLETION TEST (Phase 28)");
        System.out.println("=".repeat(70));

        int testsPassed = 0;
        int testsTotal = 0;

        // 1. FOUNDATION parsing
        System.out.println("\n1. FOUNDATION PARSING");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl1 = """
            BUILDING "test" {
                ENVELOPE {
                    FOUNDATION type:slab depth:300mm thickness:150mm
                    DRAINAGE {
                        PERIMETER_DRAIN offset:600mm connects:municipal_drain
                    }
                }
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def1 = BuildingParser.parse(dsl1);
        if (def1.envelope() != null && def1.envelope().foundation() != null) {
            FoundationDef f = def1.envelope().foundation();
            System.out.println("Foundation found:");
            System.out.printf("  Type: %s%n", f.type());
            System.out.printf("  Depth: %.0fmm%n", f.depthMm());
            System.out.printf("  Thickness: %.0fmm%n", f.slabThicknessMm());
            System.out.printf("  Material: %s%n", f.material());

            boolean ok = f.type() == FoundationDef.FoundationType.SLAB_ON_GRADE &&
                        f.depthMm() == 300 && f.slabThicknessMm() == 150;
            if (ok) {
                System.out.println("[PASS] Foundation parsing");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Foundation values incorrect");
            }
        } else {
            System.out.println("[FAIL] No foundation found");
        }

        // 2. Simplified FOUNDATION syntax
        System.out.println("\n2. SIMPLIFIED FOUNDATION SYNTAX");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl2 = """
            BUILDING "test" {
                ENVELOPE {
                    FOUNDATION slab:150mm
                }
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def2 = BuildingParser.parse(dsl2);
        if (def2.envelope() != null && def2.envelope().foundation() != null) {
            FoundationDef f = def2.envelope().foundation();
            System.out.printf("  Type: %s%n", f.type());
            System.out.printf("  Thickness: %.0fmm%n", f.slabThicknessMm());

            if (f.type() == FoundationDef.FoundationType.SLAB_ON_GRADE && f.slabThicknessMm() == 150) {
                System.out.println("[PASS] Simplified syntax");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Simplified syntax incorrect");
            }
        } else {
            System.out.println("[FAIL] No foundation found");
        }

        // 3. ROOF-DRAIN correlation (matching)
        System.out.println("\n3. ROOF-DRAIN CORRELATION (MATCHING)");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl3 = """
            BUILDING "test" {
                ENVELOPE {
                    DRAINAGE {
                        PERIMETER_DRAIN offset:600mm
                    }
                }
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def3 = BuildingParser.parse(dsl3);
        if (def3.envelope() != null) {
            System.out.println("  Roof overhang: " + def3.roof().overhangMm() + "mm");
            System.out.println("  Drain offset: " + def3.envelope().drainage().perimeterDrainOffsetMm() + "mm");
            System.out.println("  " + def3.envelope().getCorrelationStatus());

            if (def3.envelope().validateRoofDrainCorrelation()) {
                System.out.println("[PASS] Roof-drain correlation validates");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Correlation should pass");
            }
        } else {
            System.out.println("[FAIL] No envelope");
        }

        // 4. ROOF-DRAIN correlation (mismatched)
        System.out.println("\n4. ROOF-DRAIN CORRELATION (MISMATCHED)");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl4 = """
            BUILDING "test" {
                ENVELOPE {
                    DRAINAGE {
                        PERIMETER_DRAIN offset:900mm
                    }
                }
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def4 = BuildingParser.parse(dsl4);
        if (def4.envelope() != null) {
            System.out.println("  Roof overhang: " + def4.roof().overhangMm() + "mm");
            System.out.println("  Drain offset: " + def4.envelope().drainage().perimeterDrainOffsetMm() + "mm");
            System.out.println("  " + def4.envelope().getCorrelationStatus());

            if (!def4.envelope().validateRoofDrainCorrelation()) {
                System.out.println("[PASS] Mismatch correctly detected");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Should detect mismatch");
            }
        } else {
            System.out.println("[FAIL] No envelope");
        }

        // 5. DOWNPIPE locations
        System.out.println("\n5. DOWNPIPE LOCATIONS");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl5 = """
            BUILDING "test" {
                ENVELOPE {
                    DRAINAGE {
                        PERIMETER_DRAIN offset:600mm connects:municipal
                        DOWNPIPE at:A2, A5, E2, E5
                    }
                }
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def5 = BuildingParser.parse(dsl5);
        if (def5.envelope() != null && def5.envelope().drainage() != null) {
            DrainageDef d = def5.envelope().drainage();
            System.out.println("  Connects to: " + d.connectsTo());
            System.out.println("  Downpipes at: " + d.downpipeLocations());

            if (d.downpipeLocations().size() == 4) {
                System.out.println("[PASS] Downpipe locations parsed");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Expected 4 downpipe locations");
            }
        } else {
            System.out.println("[FAIL] No drainage");
        }

        // 6. Foundation types
        System.out.println("\n6. FOUNDATION TYPES");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("Available foundation types:");
        for (FoundationDef.FoundationType type : FoundationDef.FoundationType.values()) {
            System.out.println("  " + type);
        }

        String dsl6 = """
            BUILDING "test" {
                ENVELOPE {
                    FOUNDATION type:strip depth:450mm
                }
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def6 = BuildingParser.parse(dsl6);
        if (def6.envelope() != null && def6.envelope().foundation() != null) {
            FoundationDef f = def6.envelope().foundation();
            System.out.printf("\nParsed: type=%s depth=%.0fmm%n", f.type(), f.depthMm());

            if (f.type() == FoundationDef.FoundationType.STRIP_FOOTING && f.depthMm() == 450) {
                System.out.println("[PASS] Strip footing parsed");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Strip footing incorrect");
            }
        } else {
            System.out.println("[FAIL] No foundation");
        }

        // 7. Complete TB-LKTN envelope
        System.out.println("\n7. TB-LKTN ENVELOPE");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl7 = """
            BUILDING "TB-LKTN" {
                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
                }

                ENVELOPE {
                    FOUNDATION type:slab depth:300mm thickness:150mm
                    DRAINAGE {
                        PERIMETER_DRAIN offset:600mm connects:municipal_drain
                        DOWNPIPE at:A2, A5, E2, E5
                    }
                }

                STOREY "Ground" level:0 height:2.8m {
                    BEDROOM "bilik" bounds:A2-B3 {}
                }

                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def7 = BuildingParser.parse(dsl7);
        boolean envComplete = def7.envelope() != null &&
                             def7.envelope().foundation() != null &&
                             def7.envelope().drainage() != null &&
                             def7.envelope().validateRoofDrainCorrelation();

        if (envComplete) {
            System.out.println("TB-LKTN Envelope:");
            System.out.printf("  Foundation: %s (%.0fmm slab)%n",
                def7.envelope().foundation().type(),
                def7.envelope().foundation().slabThicknessMm());
            System.out.printf("  Drainage: offset=%.0fmm, %d downpipes%n",
                def7.envelope().drainage().perimeterDrainOffsetMm(),
                def7.envelope().drainage().downpipeLocations().size());
            System.out.printf("  Roof: pitch=%.0f°, overhang=%.0fmm%n",
                def7.roof().pitchDegrees(),
                def7.roof().overhangMm());
            System.out.println("  " + def7.envelope().getCorrelationStatus());
            System.out.println("[PASS] TB-LKTN envelope complete");
            testsPassed++;
        } else {
            System.out.println("[FAIL] TB-LKTN envelope incomplete");
        }

        // SUMMARY
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Tests passed: %d/%d%n", testsPassed, testsTotal);

        System.out.println("\nEnvelope Chain:");
        System.out.println("  FOUNDATION → building footprint/structural base");
        System.out.println("       ↓");
        System.out.println("  WALLS → room enclosures");
        System.out.println("       ↓");
        System.out.println("  ROOF (overhang) → water shed line");
        System.out.println("       ↓");
        System.out.println("  GUTTER → collects water");
        System.out.println("       ↓");
        System.out.println("  DOWNPIPE → vertical drainage");
        System.out.println("       ↓");
        System.out.println("  PERIMETER_DRAIN (offset=overhang) → to municipal");

        if (testsPassed == testsTotal) {
            System.out.println("\n[SUCCESS] ENVELOPE implementation complete!");
        } else {
            System.out.println("\n[PARTIAL] Some tests failed - review above");
        }
    }
}
