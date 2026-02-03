package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Consolidation Phase: Integration Test
 *
 * Tests full feature integration:
 * - Multi-storey building (2 floors)
 * - Horizontal constraints (adjacent, exterior)
 * - Vertical constraints (above, stack)
 * - MEP systems (sprinklers, lights)
 * - Auto-generated walls, doors, windows
 *
 * This test validates that all Phase 15-17 features work together.
 */
public class IntegratedTownhouseTest {

    private static final double TOLERANCE = 0.005;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("CONSOLIDATION: INTEGRATED TOWNHOUSE TEST");
        System.out.println("=".repeat(70));

        String dsl = Files.readString(Path.of("examples/integrated_townhouse.bim"));
        System.out.println("\nDSL:");
        System.out.println(dsl);

        int passed = 0;
        int failed = 0;

        // Parse and compile
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 1: PARSE + COMPILE");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingSpec spec = BuildingCompiler.compile(def);

        System.out.println("Building: " + spec.name());
        System.out.println("Storeys: " + spec.storeys().size());

        boolean test1Pass = spec.storeys().size() == 2;
        System.out.println(test1Pass ? "[PASS] 2 storeys compiled" : "[FAIL] Storey count wrong");
        if (test1Pass) passed++; else failed++;

        // Test 2: Verify vertical constraint (above:)
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 2: VERTICAL CONSTRAINT (above:)");
        System.out.println("-".repeat(70));

        RoomSpec living = findRoom(spec, "living");
        RoomSpec master = findRoom(spec, "master");
        RoomSpec kitchen = findRoom(spec, "kitchen");
        RoomSpec child = findRoom(spec, "child");

        double masterXDelta = Math.abs(master.minX() - living.minX());
        double masterYDelta = Math.abs(master.minY() - living.minY());
        double childXDelta = Math.abs(child.minX() - kitchen.minX());
        double childYDelta = Math.abs(child.minY() - kitchen.minY());

        System.out.printf("master vs living: X delta=%.4fm, Y delta=%.4fm%n", masterXDelta, masterYDelta);
        System.out.printf("child vs kitchen: X delta=%.4fm, Y delta=%.4fm%n", childXDelta, childYDelta);

        boolean test2Pass = masterXDelta < TOLERANCE && masterYDelta < TOLERANCE &&
                            childXDelta < TOLERANCE && childYDelta < TOLERANCE;
        System.out.println(test2Pass ? "[PASS] above: constraints satisfied" : "[FAIL] above: alignment error");
        if (test2Pass) passed++; else failed++;

        // Test 3: Verify stack constraint
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 3: STACK CONSTRAINT (plumbing)");
        System.out.println("-".repeat(70));

        RoomSpec bathG = findRoom(spec, "bath_g");
        RoomSpec bathU = findRoom(spec, "bath_u");

        double stackXDelta = Math.abs(bathU.minX() - bathG.minX());
        double stackYDelta = Math.abs(bathU.minY() - bathG.minY());

        System.out.printf("bath_u vs bath_g: X delta=%.4fm, Y delta=%.4fm%n", stackXDelta, stackYDelta);

        boolean test3Pass = stackXDelta < TOLERANCE && stackYDelta < TOLERANCE;
        System.out.println(test3Pass ? "[PASS] stack: constraint satisfied (plumbing core)" : "[FAIL] stack alignment error");
        if (test3Pass) passed++; else failed++;

        // Test 4: Verify MEP generation
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 4: MEP SYSTEMS (sprinklers + lights)");
        System.out.println("-".repeat(70));

        int totalSprinklers = 0;
        int totalLights = 0;

        for (StoreySpec storey : spec.storeys()) {
            int sprinklers = storey.sprinklers().size();
            int lights = storey.lights().size();
            totalSprinklers += sprinklers;
            totalLights += lights;
            System.out.printf("  %s: %d sprinklers, %d lights%n", storey.name(), sprinklers, lights);
        }

        System.out.printf("\nTotal: %d sprinklers, %d lights%n", totalSprinklers, totalLights);

        // Living has sprinklers (5x4m at 4.6m grid = 2x1 = 2)
        // Living, kitchen, master, child have lights
        boolean test4Pass = totalSprinklers >= 1 && totalLights >= 4;
        System.out.println(test4Pass ? "[PASS] MEP systems generated" : "[FAIL] MEP generation incomplete");
        if (test4Pass) passed++; else failed++;

        // Test 5: Verify wall generation
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 5: WALL GENERATION");
        System.out.println("-".repeat(70));

        int groundWalls = spec.storeys().get(0).walls().size();
        int upperWalls = spec.storeys().get(1).walls().size();

        System.out.printf("Ground storey: %d walls%n", groundWalls);
        System.out.printf("Upper storey: %d walls%n", upperWalls);

        // Expect perimeter (4) + interior walls
        boolean test5Pass = groundWalls >= 4 && upperWalls >= 4;
        System.out.println(test5Pass ? "[PASS] Walls generated (perimeter + interior)" : "[FAIL] Wall count insufficient");
        if (test5Pass) passed++; else failed++;

        // Test 6: Verify auto-generated doors/windows
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 6: AUTO-GENERATED OPENINGS");
        System.out.println("-".repeat(70));

        int totalDoors = 0;
        int totalWindows = 0;

        for (StoreySpec storey : spec.storeys()) {
            totalDoors += storey.doors().size();
            totalWindows += storey.windows().size();
        }

        System.out.printf("Total doors: %d%n", totalDoors);
        System.out.printf("Total windows: %d%n", totalWindows);

        // Adjacent rooms get doors, exterior rooms get windows
        boolean test6Pass = totalDoors >= 2 && totalWindows >= 2;
        System.out.println(test6Pass ? "[PASS] Openings auto-generated" : "[FAIL] Opening generation incomplete");
        if (test6Pass) passed++; else failed++;

        // Test 7: Z-continuity
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 7: Z-CONTINUITY");
        System.out.println("-".repeat(70));

        StoreySpec ground = spec.storeys().get(0);
        StoreySpec upper = spec.storeys().get(1);

        double groundTop = ground.baseZ() + ground.height();
        double upperBase = upper.baseZ();
        double zGap = Math.abs(upperBase - groundTop);

        System.out.printf("Ground top: %.3fm, Upper base: %.3fm, Gap: %.4fm%n",
            groundTop, upperBase, zGap);

        boolean test7Pass = zGap < TOLERANCE;
        System.out.println(test7Pass ? "[PASS] Z-continuity verified" : "[FAIL] Z gap detected");
        if (test7Pass) passed++; else failed++;

        // Test 8: Room count per storey
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 8: ROOM COUNT");
        System.out.println("-".repeat(70));

        int groundRooms = ground.rooms().size();
        int upperRooms = upper.rooms().size();

        System.out.printf("Ground: %d rooms (expected 3: living, kitchen, bath_g)%n", groundRooms);
        System.out.printf("Upper: %d rooms (expected 3: master, child, bath_u)%n", upperRooms);

        boolean test8Pass = groundRooms == 3 && upperRooms == 3;
        System.out.println(test8Pass ? "[PASS] Room count correct" : "[FAIL] Room count mismatch");
        if (test8Pass) passed++; else failed++;

        // Test 9: STAIR/LANDING integration
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 9: STAIR/LANDING INTEGRATION");
        System.out.println("-".repeat(70));

        int stairCount = ground.stairs().size();
        int landingCount = upper.landings().size();

        System.out.printf("Stairs on Ground: %d (expected 1)%n", stairCount);
        System.out.printf("Landings on Upper: %d (expected 1)%n", landingCount);

        boolean stairOK = stairCount == 1;
        boolean landingOK = landingCount == 1;

        if (stairOK && ground.stairs().get(0) != null) {
            var stair = ground.stairs().get(0);
            System.out.printf("Stair '%s': Z=[%.2f, %.2f], %d risers%n",
                stair.name(), stair.z(), stair.z() + stair.rise(), stair.numRisers());

            // Verify stair reaches upper storey
            double stairTop = stair.z() + stair.rise();
            boolean stairReaches = Math.abs(stairTop - upper.baseZ()) < 0.01;
            System.out.printf("Stair reaches Upper (%.2fm): %s%n", upper.baseZ(),
                stairReaches ? "YES" : "NO");
            stairOK &= stairReaches;
        }

        if (landingOK && upper.landings().get(0) != null) {
            var landing = upper.landings().get(0);
            System.out.printf("Landing '%s': Z=[%.2f, %.2f]%n",
                landing.name(), landing.minZ(), landing.maxZ());

            // Verify landing at floor level
            boolean landingAtFloor = Math.abs(landing.maxZ() - upper.baseZ()) < 0.01;
            System.out.printf("Landing at Upper floor (%.2fm): %s%n", upper.baseZ(),
                landingAtFloor ? "YES" : "NO");
            landingOK &= landingAtFloor;
        }

        boolean test9Pass = stairOK && landingOK;
        System.out.println(test9Pass ? "[PASS] Stair/Landing integrated" : "[FAIL] Stair/Landing issue");
        if (test9Pass) passed++; else failed++;

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("INTEGRATION TEST SUMMARY");
        System.out.println("=".repeat(70));

        System.out.println("1. Parse + Compile:        " + (test1Pass ? "PASS" : "FAIL"));
        System.out.println("2. Vertical (above:):      " + (test2Pass ? "PASS" : "FAIL"));
        System.out.println("3. Stack (plumbing):       " + (test3Pass ? "PASS" : "FAIL"));
        System.out.println("4. MEP Systems:            " + (test4Pass ? "PASS" : "FAIL"));
        System.out.println("5. Wall Generation:        " + (test5Pass ? "PASS" : "FAIL"));
        System.out.println("6. Auto Openings:          " + (test6Pass ? "PASS" : "FAIL"));
        System.out.println("7. Z-Continuity:           " + (test7Pass ? "PASS" : "FAIL"));
        System.out.println("8. Room Count:             " + (test8Pass ? "PASS" : "FAIL"));
        System.out.println("9. Stair/Landing:          " + (test9Pass ? "PASS" : "FAIL"));

        System.out.println();
        System.out.printf("PASSED: %d / %d%n", passed, passed + failed);

        System.out.println("\n" + "=".repeat(70));
        if (passed == 9) {
            System.out.println("OVERALL: PASS - Full integration verified!");
            System.out.println("=".repeat(70));
            System.out.println("\nAll features working together:");
            System.out.println("  ✓ Multi-storey building");
            System.out.println("  ✓ Horizontal constraints (adjacent, exterior)");
            System.out.println("  ✓ Vertical constraints (above, stack)");
            System.out.println("  ✓ MEP systems (sprinklers, lights)");
            System.out.println("  ✓ Auto-generated walls, doors, windows");
            System.out.println("  ✓ Z-continuity between storeys");
            System.out.println("  ✓ Stair + Landing connecting storeys");
        } else {
            System.out.println("OVERALL: FAIL - " + failed + " test(s) failed");
            System.out.println("=".repeat(70));
        }
    }

    private static RoomSpec findRoom(BuildingSpec spec, String name) {
        for (StoreySpec storey : spec.storeys()) {
            for (RoomSpec room : storey.rooms()) {
                if (room.name().equals(name)) {
                    return room;
                }
            }
        }
        throw new RuntimeException("Room not found: " + name);
    }
}
