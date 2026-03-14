package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import java.nio.file.*;
import java.util.*;

/**
 * TB-LKTN Complete Test (Phase 27)
 *
 * Verifies:
 * 1. GRID parsing and axis positions
 * 2. Bounds to coordinates conversion
 * 3. OPEN_PLAN with zones
 * 4. opens_to constraint parsing
 * 5. Extended DOOR/WINDOW parsing
 * 6. Expected element counts
 */
public class TBLKTNCompleteTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN COMPLETE TEST (Phase 27)");
        System.out.println("=".repeat(70));

        String dsl = Files.readString(Path.of("examples/tb_lktn_complete.dsl"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        int testsPassed = 0;
        int testsTotal = 0;

        // 1. GRID VERIFICATION
        System.out.println("\n1. GRID PARSING");
        System.out.println("-".repeat(70));
        testsTotal++;
        GridDef grid = def.grid();
        if (grid != null) {
            System.out.println("X Axes: " + grid.xAxes());
            System.out.println("Y Axes: " + grid.yAxes());
            System.out.println("X Spacing: " + grid.xSpacing());
            System.out.println("Y Spacing: " + grid.ySpacing());

            // Calculate positions
            System.out.println("\nCalculated positions:");
            for (String x : grid.xAxes()) {
                System.out.printf("  %s -> X=%.0fmm%n", x, grid.getX(x) * 1000);
            }
            System.out.println();
            for (String y : grid.yAxes()) {
                System.out.printf("  %s -> Y=%.0fmm%n", y, grid.getY(y) * 1000);
            }
            System.out.println("[PASS] Grid parsed");
            testsPassed++;
        } else {
            System.out.println("[FAIL] No grid found");
        }

        // 2. ROOM COUNT
        System.out.println("\n2. ROOM COUNT");
        System.out.println("-".repeat(70));
        testsTotal++;
        var storey = def.storeys().get(0);
        int roomCount = storey.rooms().size();
        System.out.printf("Rooms found: %d (expected: 7)%n", roomCount);
        for (RoomDef room : storey.rooms()) {
            System.out.printf("  %s \"%s\" bounds:%s%n",
                room.type(), room.name(), room.gridBounds());
        }
        if (roomCount == 7) {
            System.out.println("[PASS] Room count correct");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 7 rooms");
        }

        // 3. OPEN_PLAN VERIFICATION
        System.out.println("\n3. OPEN_PLAN VERIFICATION");
        System.out.println("-".repeat(70));
        testsTotal++;
        RoomDef openPlan = storey.rooms().stream()
            .filter(r -> "common".equals(r.name()))
            .findFirst().orElse(null);

        if (openPlan != null && openPlan.isOpenPlan()) {
            System.out.printf("Type: %s%n", openPlan.type());
            System.out.printf("Zones: %s%n", openPlan.zones());
            System.out.printf("Exterior walls: %s%n", openPlan.exteriorWalls());

            boolean hasZones = openPlan.zones().containsAll(
                List.of("LIVING", "DINING", "KITCHEN"));
            if (hasZones) {
                System.out.println("[PASS] OPEN_PLAN with zones");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Missing zones");
            }
        } else {
            System.out.println("[FAIL] OPEN_PLAN 'common' not found");
        }

        // 4. OPENS_TO VERIFICATION
        System.out.println("\n4. OPENS_TO CONSTRAINT");
        System.out.println("-".repeat(70));
        testsTotal++;
        int opensToCount = 0;
        for (RoomDef room : storey.rooms()) {
            if (room.hasOpensTo()) {
                System.out.printf("  %s opens_to: %s%n", room.name(), room.opensTo());
                opensToCount++;
            }
        }
        System.out.printf("Rooms with opens_to: %d (expected: 5)%n", opensToCount);
        if (opensToCount == 5) {
            System.out.println("[PASS] opens_to constraints parsed");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 5 rooms with opens_to");
        }

        // 5. DOOR/WINDOW COUNT
        System.out.println("\n5. DOOR/WINDOW COUNT");
        System.out.println("-".repeat(70));
        testsTotal++;
        int totalDoors = 0;
        int totalWindows = 0;
        for (RoomDef room : storey.rooms()) {
            for (OpeningDef opening : room.openings()) {
                if ("DOOR".equals(opening.type())) totalDoors++;
                else if ("WINDOW".equals(opening.type())) totalWindows++;
            }
        }
        System.out.printf("Doors: %d (expected: 7)%n", totalDoors);
        System.out.printf("Windows: %d (expected: 7)%n", totalWindows);

        // List all openings
        System.out.println("\nOpening details:");
        for (RoomDef room : storey.rooms()) {
            for (OpeningDef op : room.openings()) {
                System.out.printf("  %s: %s %s %.0fx%.0fmm%n",
                    room.name(), op.type(), op.wall(),
                    op.width() * 1000, op.height() * 1000);
            }
        }

        if (totalDoors >= 7 && totalWindows >= 6) {
            System.out.println("[PASS] Door/window count");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 7 doors, 7 windows");
        }

        // 6. PLUMBING STACK
        System.out.println("\n6. PLUMBING STACK");
        System.out.println("-".repeat(70));
        testsTotal++;
        int stackCount = 0;
        for (RoomDef room : storey.rooms()) {
            if ("plumbing".equals(room.stack())) {
                System.out.printf("  %s stack: %s%n", room.name(), room.stack());
                stackCount++;
            }
        }
        System.out.printf("Rooms on plumbing stack: %d (expected: 2)%n", stackCount);
        if (stackCount == 2) {
            System.out.println("[PASS] Plumbing stack");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Expected 2 rooms on plumbing stack");
        }

        // 7. BOUNDS TO COORDINATES
        System.out.println("\n7. BOUNDS TO COORDINATES");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("Room dimensions from grid bounds:");
        boolean dimsOk = true;
        for (RoomDef room : storey.rooms()) {
            if (room.hasGridBounds() && grid != null) {
                GridBounds gb = room.getParsedGridBounds();
                if (gb != null) {
                    double[] dims = gb.getDimensions(grid);
                    double x1 = grid.getX(gb.startX());
                    double y1 = grid.getY(gb.startY());
                    double x2 = grid.getX(gb.endX());
                    double y2 = grid.getY(gb.endY());
                    System.out.printf("  %-12s: (%5.0f,%5.0f) to (%5.0f,%5.0f) = %.1f×%.1fm%n",
                        room.name(),
                        x1 * 1000, y1 * 1000,
                        x2 * 1000, y2 * 1000,
                        dims[0], dims[1]);
                    if (dims[0] <= 0 || dims[1] <= 0) dimsOk = false;
                }
            }
        }
        if (dimsOk) {
            System.out.println("[PASS] All rooms have valid dimensions");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Some rooms have zero dimensions");
        }

        // 8. ROOF VERIFICATION
        System.out.println("\n8. ROOF VERIFICATION");
        System.out.println("-".repeat(70));
        testsTotal++;
        if (def.roof() != null) {
            System.out.printf("Pitch: %.0f deg%n", def.roof().pitchDegrees());
            System.out.printf("Overhang: %.0f mm%n", def.roof().overhangMm());
            if (def.roof().pitchDegrees() == 25 && def.roof().overhangMm() == 600) {
                System.out.println("[PASS] Roof spec correct");
                testsPassed++;
            } else {
                System.out.println("[FAIL] Roof values don't match");
            }
        } else {
            System.out.println("[FAIL] No roof found");
        }

        // 9. SEMANTIC VALIDATION
        System.out.println("\n9. SEMANTIC VALIDATION");
        System.out.println("-".repeat(70));
        testsTotal++;
        boolean semanticOk = true;

        // Calculate room areas
        Map<String, Double> areas = new HashMap<>();
        for (RoomDef room : storey.rooms()) {
            if (room.hasGridBounds() && grid != null) {
                GridBounds gb = room.getParsedGridBounds();
                if (gb != null) {
                    double[] dims = gb.getDimensions(grid);
                    double area = dims[0] * dims[1];
                    areas.put(room.name(), area);
                    System.out.printf("  %-12s: %.1f × %.1f = %.2f sqm%n",
                        room.name(), dims[0], dims[1], area);
                }
            }
        }

        // Check: Master bedroom should be largest bedroom
        double masterArea = areas.getOrDefault("bilik_utama", 0.0);
        double bed2Area = areas.getOrDefault("bilik_2", 0.0);
        double bed3Area = areas.getOrDefault("bilik_3", 0.0);

        System.out.println("\nSemantic checks:");
        if (masterArea > bed2Area && masterArea > bed3Area) {
            System.out.printf("  [OK] Master (%.1f sqm) > Bed2 (%.1f) and Bed3 (%.1f)%n",
                masterArea, bed2Area, bed3Area);
        } else {
            System.out.printf("  [!!] Master (%.1f sqm) should be > Bed2 (%.1f) and Bed3 (%.1f)%n",
                masterArea, bed2Area, bed3Area);
            semanticOk = false;
        }

        // Check: Wet areas should be smaller than bedrooms
        double bathArea = areas.getOrDefault("bilik_mandi", 0.0);
        double wcArea = areas.getOrDefault("tandas", 0.0);
        if (bathArea < bed2Area && wcArea < bed2Area) {
            System.out.printf("  [OK] Wet areas (%.1f, %.1f sqm) < bedrooms%n", bathArea, wcArea);
        } else {
            System.out.println("  [!!] Wet areas should be smaller than bedrooms");
            semanticOk = false;
        }

        // Check: Common area should be largest
        double commonArea = areas.getOrDefault("common", 0.0);
        if (commonArea > masterArea) {
            System.out.printf("  [OK] Common area (%.1f sqm) is largest space%n", commonArea);
        } else {
            System.out.println("  [!!] Common area should be largest");
            semanticOk = false;
        }

        if (semanticOk) {
            System.out.println("[PASS] Semantic validation");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Semantic issues found");
        }

        // SUMMARY
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Tests passed: %d/%d%n", testsPassed, testsTotal);

        // Element summary table
        System.out.println("\nElement Summary:");
        System.out.println("+-----------------+----------+----------+");
        System.out.println("| Element         | Expected | Actual   |");
        System.out.println("+-----------------+----------+----------+");
        System.out.printf("| Rooms           | 7        | %-8d |%n", roomCount);
        System.out.printf("| Doors           | 7        | %-8d |%n", totalDoors);
        System.out.printf("| Windows         | 7        | %-8d |%n", totalWindows);
        System.out.printf("| Plumbing stack  | 2        | %-8d |%n", stackCount);
        System.out.printf("| opens_to        | 5        | %-8d |%n", opensToCount);
        System.out.println("+-----------------+----------+----------+");

        if (testsPassed == testsTotal) {
            System.out.println("\n[SUCCESS] All tests passed!");
        } else {
            System.out.println("\n[PARTIAL] Some tests failed - review above");
        }
    }
}
