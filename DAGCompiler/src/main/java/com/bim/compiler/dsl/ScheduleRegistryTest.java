package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import java.nio.file.*;

/**
 * Phase 28A: SCHEDULE Registry Test
 *
 * Verifies:
 * 1. SCHEDULE doors {} parsing
 * 2. SCHEDULE windows {} parsing
 * 3. Type code resolution (D1 → 900x2100)
 * 4. Door/window with type:XX resolution
 */
public class ScheduleRegistryTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("SCHEDULE REGISTRY TEST (Phase 28A)");
        System.out.println("=".repeat(70));

        // Test DSL with schedules
        String dsl = """
            BUILDING "TB-LKTN" {

                SCHEDULE doors {
                    D1: 900x2100 "Metal frame solid timber"
                    D2: 750x2100 "Flush door gloss paint"
                    D3: 900x2100 "Flush door gloss paint"
                }

                SCHEDULE windows {
                    W1: 1800x1000 "Aluminium 3 panel"
                    W2: 1200x1000 "Aluminium 2 panel"
                    W3: 600x500 "Aluminium top hung"
                }

                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
                }

                STOREY "Ground" level:0 height:2.8m {
                    BEDROOM "bilik_utama" bounds:A2-C3 {
                        exterior: west
                        DOOR type:D2
                        WINDOW type:W1 wall:west
                    }

                    BATHROOM "bilik_mandi" bounds:A3-B4 {
                        exterior: west
                        DOOR type:D3
                        WINDOW type:W3 wall:west
                    }
                }

                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def = BuildingParser.parse(dsl);

        int testsPassed = 0;
        int testsTotal = 0;

        // 1. SCHEDULE doors parsing
        System.out.println("\n1. SCHEDULE doors PARSING");
        System.out.println("-".repeat(70));
        testsTotal++;
        if (def.doorSchedule() != null) {
            System.out.println("Door schedule found: " + def.doorSchedule().entries().size() + " entries");
            for (ScheduleEntryDef entry : def.doorSchedule().entries()) {
                System.out.printf("  %s: %.0fx%.0fmm \"%s\"%n",
                    entry.typeCode(), entry.widthMm(), entry.heightMm(),
                    entry.description() != null ? entry.description() : "");
            }
            if (def.doorSchedule().entries().size() == 3) {
                System.out.println("[PASS] Door schedule parsed");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Expected 3 door entries");
            }
        } else {
            System.out.println("[FAIL] No door schedule found");
        }

        // 2. SCHEDULE windows parsing
        System.out.println("\n2. SCHEDULE windows PARSING");
        System.out.println("-".repeat(70));
        testsTotal++;
        if (def.windowSchedule() != null) {
            System.out.println("Window schedule found: " + def.windowSchedule().entries().size() + " entries");
            for (ScheduleEntryDef entry : def.windowSchedule().entries()) {
                System.out.printf("  %s: %.0fx%.0fmm \"%s\"%n",
                    entry.typeCode(), entry.widthMm(), entry.heightMm(),
                    entry.description() != null ? entry.description() : "");
            }
            if (def.windowSchedule().entries().size() == 3) {
                System.out.println("[PASS] Window schedule parsed");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Expected 3 window entries");
            }
        } else {
            System.out.println("[FAIL] No window schedule found");
        }

        // 3. Type code resolution
        System.out.println("\n3. TYPE CODE RESOLUTION");
        System.out.println("-".repeat(70));
        testsTotal++;

        double[] d1 = def.resolveDoorType("D1");
        double[] d2 = def.resolveDoorType("D2");
        double[] w1 = def.resolveWindowType("W1");
        double[] w3 = def.resolveWindowType("W3");

        System.out.printf("D1 → %.0fx%.0fmm (expected 900x2100)%n",
            d1 != null ? d1[0] * 1000 : 0, d1 != null ? d1[1] * 1000 : 0);
        System.out.printf("D2 → %.0fx%.0fmm (expected 750x2100)%n",
            d2 != null ? d2[0] * 1000 : 0, d2 != null ? d2[1] * 1000 : 0);
        System.out.printf("W1 → %.0fx%.0fmm (expected 1800x1000)%n",
            w1 != null ? w1[0] * 1000 : 0, w1 != null ? w1[1] * 1000 : 0);
        System.out.printf("W3 → %.0fx%.0fmm (expected 600x500)%n",
            w3 != null ? w3[0] * 1000 : 0, w3 != null ? w3[1] * 1000 : 0);

        boolean resolveOk = d1 != null && d1[0] == 0.9 && d1[1] == 2.1 &&
                           d2 != null && d2[0] == 0.75 && d2[1] == 2.1 &&
                           w1 != null && w1[0] == 1.8 && w1[1] == 1.0 &&
                           w3 != null && w3[0] == 0.6 && w3[1] == 0.5;
        if (resolveOk) {
            System.out.println("[PASS] Type code resolution");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Type code resolution mismatch");
        }

        // 4. Door/window with typeCode storage
        System.out.println("\n4. OPENING typeCode STORAGE");
        System.out.println("-".repeat(70));
        testsTotal++;

        var storey = def.storeys().get(0);
        int openingsWithTypeCode = 0;
        System.out.println("Openings parsed:");
        for (RoomDef room : storey.rooms()) {
            for (OpeningDef opening : room.openings()) {
                String tc = opening.hasTypeCode() ? opening.typeCode() : "(none)";
                System.out.printf("  %s: %s type:%s wall:%s %.0fx%.0fmm%n",
                    room.name(), opening.type(), tc, opening.wall(),
                    opening.width() * 1000, opening.height() * 1000);
                if (opening.hasTypeCode()) {
                    openingsWithTypeCode++;
                }
            }
        }
        System.out.printf("Openings with typeCode: %d (expected: 4)%n", openingsWithTypeCode);
        if (openingsWithTypeCode >= 4) {
            System.out.println("[PASS] typeCode stored in openings");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 4 openings with typeCode");
        }

        // 5. Schedule-based resolution
        System.out.println("\n5. SCHEDULE-BASED DIMENSION RESOLUTION");
        System.out.println("-".repeat(70));
        testsTotal++;

        boolean allResolved = true;
        System.out.println("Resolved dimensions:");
        for (RoomDef room : storey.rooms()) {
            for (OpeningDef opening : room.openings()) {
                if (opening.hasTypeCode()) {
                    double[] resolved = opening.resolveFromSchedule(def);
                    System.out.printf("  %s %s type:%s → %.0fx%.0fmm%n",
                        room.name(), opening.type(), opening.typeCode(),
                        resolved[0] * 1000, resolved[1] * 1000);
                    if (resolved[0] <= 0 || resolved[1] <= 0) {
                        allResolved = false;
                    }
                }
            }
        }
        if (allResolved) {
            System.out.println("[PASS] Schedule resolution");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Some openings couldn't be resolved");
        }

        // SUMMARY
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Tests passed: %d/%d%n", testsPassed, testsTotal);

        if (testsPassed == testsTotal) {
            System.out.println("\n[SUCCESS] SCHEDULE Registry implemented!");
        } else {
            System.out.println("\n[PARTIAL] Some tests failed - review above");
        }
    }
}
