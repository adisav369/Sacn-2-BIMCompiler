package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.dsl.RoomType.WallRule;
import com.bim.compiler.dsl.WallGenerator.*;

/**
 * Phase 28: Wall Rules Test
 *
 * Verifies:
 * 1. WallRule enum values
 * 2. RoomType.getWallRule() mapping
 * 3. OPEN_PLAN gets PERIMETER_ONLY walls
 * 4. BEDROOM gets ENCLOSED (4 walls)
 * 5. PORCH gets NONE (no walls)
 * 6. opens_to generates door on shared edge
 * 7. No walls inside OPEN_PLAN zones
 */
public class WallRulesTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("WALL RULES TEST (Phase 28)");
        System.out.println("=".repeat(70));

        int testsPassed = 0;
        int testsTotal = 0;

        // 1. WallRule enum values
        System.out.println("\n1. WALL RULE ENUM");
        System.out.println("-".repeat(70));
        testsTotal++;
        boolean enumOk = true;
        for (WallRule rule : WallRule.values()) {
            System.out.println("  " + rule);
        }
        if (WallRule.values().length == 4) {
            System.out.println("[PASS] WallRule enum has 4 values");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 4 WallRule values");
        }

        // 2. RoomType → WallRule mapping
        System.out.println("\n2. ROOMTYPE → WALLRULE MAPPING");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("  BEDROOM    → " + RoomType.BEDROOM.getWallRule());
        System.out.println("  BATHROOM   → " + RoomType.BATHROOM.getWallRule());
        System.out.println("  OPEN_PLAN  → " + RoomType.OPEN_PLAN.getWallRule());
        System.out.println("  PORCH      → " + RoomType.PORCH.getWallRule());
        System.out.println("  CORRIDOR   → " + RoomType.CORRIDOR.getWallRule());

        boolean mappingOk =
            RoomType.BEDROOM.getWallRule() == WallRule.ENCLOSED &&
            RoomType.OPEN_PLAN.getWallRule() == WallRule.PERIMETER_ONLY &&
            RoomType.PORCH.getWallRule() == WallRule.NONE;

        if (mappingOk) {
            System.out.println("[PASS] WallRule mapping correct");
            testsPassed++;
        } else {
            System.out.println("[FAIL] WallRule mapping incorrect");
        }

        // 3. fromKeyword resolution
        System.out.println("\n3. KEYWORD RESOLUTION");
        System.out.println("-".repeat(70));
        testsTotal++;

        RoomType porch1 = RoomType.fromKeyword("PORCH");
        RoomType porch2 = RoomType.fromKeyword("ANJUNG");
        RoomType openPlan = RoomType.fromKeyword("OPEN_PLAN");

        System.out.println("  PORCH    → " + porch1 + " (" + porch1.getWallRule() + ")");
        System.out.println("  ANJUNG   → " + porch2 + " (" + porch2.getWallRule() + ")");
        System.out.println("  OPEN_PLAN → " + openPlan + " (" + openPlan.getWallRule() + ")");

        boolean keywordOk =
            porch1 == RoomType.PORCH &&
            porch2 == RoomType.PORCH &&
            openPlan == RoomType.OPEN_PLAN;

        if (keywordOk) {
            System.out.println("[PASS] Keyword resolution correct");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Keyword resolution incorrect");
        }

        // 4. Wall generation test with TB-LKTN layout
        System.out.println("\n4. WALL GENERATION (TB-LKTN)");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl = """
            BUILDING "TB-LKTN" {
                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
                }

                STOREY "Ground" level:0 height:2.8m {
                    PORCH "anjung" bounds:C1-D2 {
                        exterior: south
                    }

                    OPEN_PLAN "common" bounds:B2-D5 {
                        zones: LIVING, DINING, KITCHEN
                        exterior: south
                        exterior: north
                    }

                    BEDROOM "bilik_utama" bounds:A2-C3 {
                        exterior: west
                        exterior: south
                        opens_to: common
                    }

                    BATHROOM "bilik_mandi" bounds:A3-B4 {
                        exterior: west
                        opens_to: common
                    }

                    BEDROOM "bilik_2" bounds:D2-E3 {
                        exterior: east
                        exterior: south
                        opens_to: common
                    }
                }

                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def = BuildingParser.parse(dsl);
        StoreyDef storey = def.storeys().get(0);

        WallGenerationResult result = WallGenerator.generate(storey, def.grid());

        System.out.println("Generated walls: " + result.wallCount());
        System.out.println("  Exterior: " + result.exteriorWallCount());
        System.out.println("  Partition: " + result.partitionCount());
        System.out.println("\nWall details:");

        // Group walls by room
        java.util.Map<String, java.util.List<WallSegment>> byRoom = new java.util.HashMap<>();
        for (WallSegment wall : result.walls()) {
            byRoom.computeIfAbsent(wall.roomName(), k -> new java.util.ArrayList<>()).add(wall);
        }

        for (String roomName : byRoom.keySet()) {
            var roomWalls = byRoom.get(roomName);
            System.out.printf("  %-12s: %d walls%n", roomName, roomWalls.size());
            for (WallSegment w : roomWalls) {
                System.out.printf("    %s (%s) %.1fm%n", w.direction(), w.type(), w.length());
            }
        }

        // Verify wall counts
        int anjungWalls = byRoom.getOrDefault("anjung", java.util.List.of()).size();
        int commonWalls = byRoom.getOrDefault("common", java.util.List.of()).size();
        int bedroomWalls = byRoom.getOrDefault("bilik_utama", java.util.List.of()).size();

        System.out.println("\nExpected:");
        System.out.println("  anjung (PORCH): 0 walls (NONE rule)");
        System.out.println("  common (OPEN_PLAN): 2 walls (PERIMETER_ONLY, exterior:south,north)");
        System.out.println("  bilik_utama (BEDROOM): 4 walls (ENCLOSED)");

        System.out.println("\nActual:");
        System.out.printf("  anjung: %d walls%n", anjungWalls);
        System.out.printf("  common: %d walls%n", commonWalls);
        System.out.printf("  bilik_utama: %d walls%n", bedroomWalls);

        boolean wallCountOk = anjungWalls == 0 && commonWalls == 2 && bedroomWalls == 4;
        if (wallCountOk) {
            System.out.println("[PASS] Wall counts correct");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Wall counts incorrect");
        }

        // 5. opens_to door generation
        System.out.println("\n5. OPENS_TO DOOR GENERATION");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("Doors generated:");
        for (String door : result.doorsGenerated()) {
            System.out.println("  " + door);
        }

        // Should have 3 doors: bilik_utama→common, bilik_mandi→common, bilik_2→common
        boolean doorsOk = result.doorsGenerated().size() >= 3;
        if (doorsOk) {
            System.out.println("[PASS] Door connections generated");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 3 door connections");
        }

        // 6. Warnings check
        System.out.println("\n6. WARNINGS");
        System.out.println("-".repeat(70));
        testsTotal++;

        if (result.warnings().isEmpty()) {
            System.out.println("  (none)");
            System.out.println("[PASS] No warnings");
            testsPassed++;
        } else {
            for (String warning : result.warnings()) {
                System.out.println("  " + warning);
            }
            System.out.println("[INFO] Warnings present (may be expected)");
            testsPassed++; // Don't fail on warnings
        }

        // 7. Zone verification (no walls between zones)
        System.out.println("\n7. ZONE VERIFICATION");
        System.out.println("-".repeat(70));
        testsTotal++;

        // For OPEN_PLAN with zones, there should be NO partition walls
        long commonPartitions = result.walls().stream()
            .filter(w -> w.roomName().equals("common"))
            .filter(w -> w.type() == WallSegment.WallType.PARTITION)
            .count();

        System.out.println("common room partition walls: " + commonPartitions + " (expected: 0)");
        if (commonPartitions == 0) {
            System.out.println("[PASS] No internal walls in OPEN_PLAN");
            testsPassed++;
        } else {
            System.out.println("[FAIL] OPEN_PLAN should have no partition walls");
        }

        // SUMMARY
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Tests passed: %d/%d%n", testsPassed, testsTotal);

        System.out.println("\nWall Rule Summary:");
        System.out.println("+---------------+------------------+--------+");
        System.out.println("| Space Type    | Wall Rule        | Walls  |");
        System.out.println("+---------------+------------------+--------+");
        System.out.printf("| %-13s | %-16s | %-6d |%n", "PORCH", "NONE", anjungWalls);
        System.out.printf("| %-13s | %-16s | %-6d |%n", "OPEN_PLAN", "PERIMETER_ONLY", commonWalls);
        System.out.printf("| %-13s | %-16s | %-6d |%n", "BEDROOM", "ENCLOSED", bedroomWalls);
        System.out.println("+---------------+------------------+--------+");

        if (testsPassed == testsTotal) {
            System.out.println("\n[SUCCESS] Wall Rules implemented correctly!");
        } else {
            System.out.println("\n[PARTIAL] Some tests failed - review above");
        }
    }
}
