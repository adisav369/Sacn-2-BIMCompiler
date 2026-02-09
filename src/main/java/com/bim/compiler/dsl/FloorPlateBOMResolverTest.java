package com.bim.compiler.dsl;

import com.bim.compiler.dsl.FloorPlateBOMResolver.*;
import java.util.*;

/**
 * Phase 95A: Validates FloorPlateBOMResolver produces zone bounds
 * identical to the manual DSL grid bounds in CONDO-MID.bim.
 */
public class FloorPlateBOMResolverTest {

    public static void main(String[] args) {
        System.out.println("=== FloorPlateBOMResolver Test ===\n");

        // Build CONDO-MID grid: axes A-F / 1-6, spacing 10,2,6,2,10 / 8.5×5
        GridInfo grid = new GridInfo(
            List.of("A", "B", "C", "D", "E", "F"),
            List.of(0.0, 10.0, 12.0, 18.0, 20.0, 30.0),
            List.of("1", "2", "3", "4", "5", "6"),
            List.of(0.0, 8.5, 17.0, 25.5, 34.0, 42.5)
        );

        FloorPlateBOMResolver resolver = new FloorPlateBOMResolver();
        List<ZoneBounds> zones = resolver.resolveFloorPlate("TYPICAL_CONDO_FLOOR", grid);

        System.out.println("Resolved zones:");
        for (ZoneBounds z : zones) {
            System.out.println("  " + z);
        }
        System.out.println();

        // Expected zones from manual DSL (Phase 95B: added roomName)
        record Expected(String role, String spaceType, String gridLabel,
                         double minX, double minY, double maxX, double maxY,
                         String roomName) {}

        List<Expected> expected = List.of(
            new Expected("STAIR_A",      "STAIR_ENCLOSURE", "C1-D2", 12, 0,    18, 8.5,  "stair_A_typ"),
            new Expected("STAIR_B",      "STAIR_ENCLOSURE", "C4-D5", 12, 25.5, 18, 34,   "stair_B_typ"),
            new Expected("LIFT_LOBBY",   "LIFT_LOBBY",      "C2-D4", 12, 8.5,  18, 25.5, "lift_lobby_typ"),
            new Expected("CORRIDOR",     "CORRIDOR",        "B1-C6", 10, 0,    12, 42.5, "corridor"),
            new Expected("WEST_UNITS_1", "UNIT",            "A1-B3",  0, 0,    10, 17,   "unit_W1"),
            new Expected("WEST_UNITS_2", "UNIT",            "A3-B6",  0, 17,   10, 42.5, "unit_W2"),
            new Expected("EAST_UNITS_1", "UNIT",            "D1-F3", 18, 0,    30, 17,   "unit_E1"),
            new Expected("EAST_UNITS_2", "UNIT",            "D3-F6", 18, 17,   30, 42.5, "unit_E2"),
            new Expected("TOILET",       "TOILET_BLOCK",    "D3-E4", 18, 17,   20, 25.5, "toilet")
        );

        int passed = 0, failed = 0;

        for (Expected exp : expected) {
            ZoneBounds actual = findZone(zones, exp.role);
            if (actual == null) {
                System.out.printf("FAIL: %s — not found in resolver output%n", exp.role);
                failed++;
                continue;
            }

            List<String> errors = new ArrayList<>();
            if (!exp.gridLabel.equals(actual.gridLabel()))
                errors.add("gridLabel: expected " + exp.gridLabel + ", got " + actual.gridLabel());
            if (Math.abs(exp.minX - actual.minX()) > 0.01)
                errors.add("minX: expected " + exp.minX + ", got " + actual.minX());
            if (Math.abs(exp.minY - actual.minY()) > 0.01)
                errors.add("minY: expected " + exp.minY + ", got " + actual.minY());
            if (Math.abs(exp.maxX - actual.maxX()) > 0.01)
                errors.add("maxX: expected " + exp.maxX + ", got " + actual.maxX());
            if (Math.abs(exp.maxY - actual.maxY()) > 0.01)
                errors.add("maxY: expected " + exp.maxY + ", got " + actual.maxY());
            if (!exp.spaceType.equals(actual.spaceType()))
                errors.add("spaceType: expected " + exp.spaceType + ", got " + actual.spaceType());
            // Phase 95B: Room name check
            if (exp.roomName != null && !exp.roomName.equals(actual.roomName()))
                errors.add("roomName: expected " + exp.roomName + ", got " + actual.roomName());

            if (errors.isEmpty()) {
                System.out.printf("PASS: %-16s %s  room=%-16s (%.1f,%.1f)-(%.1f,%.1f)%n",
                    exp.role, exp.gridLabel, exp.roomName, exp.minX, exp.minY, exp.maxX, exp.maxY);
                passed++;
            } else {
                System.out.printf("FAIL: %s — %s%n", exp.role, String.join("; ", errors));
                failed++;
            }
        }

        System.out.printf("%n=== Zone Results: %d/%d passed ===%n", passed, expected.size());

        // Phase 95B: Test resolveRoomBoundsMap
        System.out.println("\n--- resolveRoomBoundsMap ---");
        Map<String, double[]> roomMap = resolver.resolveRoomBoundsMap("TYPICAL_CONDO_FLOOR", grid);
        Map<String, double[]> expectedMap = Map.of(
            "corridor",       new double[]{10, 0, 12, 42.5},
            "stair_A_typ",    new double[]{12, 0, 18, 8.5},
            "stair_B_typ",    new double[]{12, 25.5, 18, 34},
            "lift_lobby_typ", new double[]{12, 8.5, 18, 25.5},
            "unit_W1",        new double[]{0, 0, 10, 17},
            "unit_W2",        new double[]{0, 17, 10, 42.5},
            "unit_E1",        new double[]{18, 0, 30, 17},
            "unit_E2",        new double[]{18, 17, 30, 42.5},
            "toilet",         new double[]{18, 17, 20, 25.5}
        );
        int mapPassed = 0;
        for (var entry : expectedMap.entrySet()) {
            double[] actual = roomMap.get(entry.getKey());
            double[] exp2 = entry.getValue();
            if (actual != null &&
                Math.abs(actual[0] - exp2[0]) < 0.01 && Math.abs(actual[1] - exp2[1]) < 0.01 &&
                Math.abs(actual[2] - exp2[2]) < 0.01 && Math.abs(actual[3] - exp2[3]) < 0.01) {
                System.out.printf("PASS: %-16s (%.1f,%.1f)-(%.1f,%.1f)%n",
                    entry.getKey(), actual[0], actual[1], actual[2], actual[3]);
                mapPassed++;
            } else {
                System.out.printf("FAIL: %-16s expected (%.1f,%.1f)-(%.1f,%.1f), got %s%n",
                    entry.getKey(), exp2[0], exp2[1], exp2[2], exp2[3],
                    actual != null ? String.format("(%.1f,%.1f)-(%.1f,%.1f)", actual[0], actual[1], actual[2], actual[3]) : "null");
                failed++;
            }
        }
        passed += mapPassed;
        int total = expected.size() + expectedMap.size();

        // Phase 95B: Test gridInfoFromGridDef
        System.out.println("\n--- gridInfoFromGridDef ---");
        BuildingDefinition.GridDef gridDef = new BuildingDefinition.GridDef(
            List.of("A", "B", "C", "D", "E", "F"),
            List.of("1", "2", "3", "4", "5", "6"),
            List.of(10.0, 2.0, 6.0, 2.0, 10.0),
            List.of(8.5, 8.5, 8.5, 8.5, 8.5),
            false
        );
        GridInfo converted = FloorPlateBOMResolver.gridInfoFromGridDef(gridDef);
        boolean gridMatch = converted.xPositions().equals(grid.xPositions())
                         && converted.yPositions().equals(grid.yPositions());
        if (gridMatch) {
            System.out.println("PASS: gridInfoFromGridDef positions match");
            passed++;
        } else {
            System.out.printf("FAIL: gridInfoFromGridDef — x=%s, y=%s%n",
                converted.xPositions(), converted.yPositions());
            failed++;
        }
        total++;

        System.out.printf("%n=== Results: %d/%d passed ===%n", passed, total);

        if (failed > 0) {
            System.out.println("VERDICT: FAIL");
            System.exit(1);
        } else {
            System.out.println("VERDICT: PASS");
        }
    }

    private static ZoneBounds findZone(List<ZoneBounds> zones, String role) {
        for (ZoneBounds z : zones) {
            if (z.role().equals(role)) return z;
        }
        return null;
    }
}
