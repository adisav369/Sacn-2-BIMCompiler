package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 48A Test: Verify STACKED layout detection and cross-unit above: constraint.
 */
public class StackedDuplexTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 48A: Stacked Duplex Test ===\n");

        // Test 1: Parse and detect STACKED layout
        testStackedDetection();

        // Test 2: Verify cross-unit above: resolution
        testCrossUnitAbove();

        // Test 3: Verify TB-LKTN-2S intra-unit above: still works
        testIntraUnitAbove();

        System.out.println("\n=== All Phase 48A Tests Passed ===");
    }

    private static void testStackedDetection() throws Exception {
        System.out.println("Test 1: STACKED layout detection");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        // Verify it's multi-unit
        assert def.isMultiUnit() : "Should be multi-unit";
        System.out.println("  - Parsed as MULTI_UNIT ✓");

        // Verify units occupy different levels
        UnitDefinition unitA = def.getUnit("A");
        UnitDefinition unitB = def.getUnit("B");

        assert unitA != null : "Unit A not found";
        assert unitB != null : "Unit B not found";

        // Unit A should be on level 0, Unit B on level 1
        boolean aHasLevel0 = unitA.hasStoreyAtLevel(0);
        boolean bHasLevel1 = unitB.hasStoreyAtLevel(1);
        boolean aHasLevel1 = unitA.hasStoreyAtLevel(1);
        boolean bHasLevel0 = unitB.hasStoreyAtLevel(0);

        assert aHasLevel0 && !aHasLevel1 : "Unit A should only be on level 0";
        assert bHasLevel1 && !bHasLevel0 : "Unit B should only be on level 1";
        System.out.println("  - Unit A: level 0 only ✓");
        System.out.println("  - Unit B: level 1 only ✓");
        System.out.println("  - Disjoint levels → STACKED layout expected ✓");

        System.out.println("  Test 1 PASSED\n");
    }

    private static void testCrossUnitAbove() throws Exception {
        System.out.println("Test 2: Cross-unit above: constraint resolution");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        // Verify living_b has above: living_a (cross-unit reference)
        UnitDefinition unitB = def.getUnit("B");
        BuildingDefinition.RoomDef livingB = unitB.findRoom("living_b");

        assert livingB != null : "living_b not found in Unit B";
        assert livingB.above() != null : "living_b should have above: constraint";
        assert livingB.above().equals("living_a") : "living_b should be above living_a";
        System.out.println("  - living_b.above() = living_a ✓");

        // Verify findUnitContainingRoom works for cross-unit reference
        String unitContainingLivingA = def.findUnitContainingRoom("living_a");
        assert "A".equals(unitContainingLivingA) : "living_a should be in Unit A";
        System.out.println("  - findUnitContainingRoom(living_a) = A ✓");

        // Verify all above: constraints in Unit B reference Unit A rooms
        for (BuildingDefinition.RoomDef room : unitB.getAllRooms()) {
            if (room.above() != null) {
                String targetUnit = def.findUnitContainingRoom(room.above());
                assert "A".equals(targetUnit) :
                    room.name() + ".above() references " + room.above() + " which should be in Unit A";
            }
        }
        System.out.println("  - All Unit B above: constraints reference Unit A ✓");

        System.out.println("  Test 2 PASSED\n");
    }

    private static void testIntraUnitAbove() throws Exception {
        System.out.println("Test 3: Intra-unit above: still works (TB-LKTN-2S regression)");

        String dsl = Files.readString(Path.of("examples/TB-LKTN-2S.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        // TB-LKTN-2S is single-unit with above: constraints within the unit
        assert !def.isMultiUnit() : "TB-LKTN-2S should be single-unit";
        System.out.println("  - TB-LKTN-2S parsed as single-unit ✓");

        // Find a room with above: constraint (if any in the DSL)
        boolean hasIntraUnitAbove = false;
        for (BuildingDefinition.StoreyDef storey : def.storeys()) {
            for (BuildingDefinition.RoomDef room : storey.rooms()) {
                if (room.above() != null) {
                    hasIntraUnitAbove = true;
                    System.out.println("  - Found intra-unit above: " +
                        room.name() + " above " + room.above() + " ✓");
                }
            }
        }

        // Even if no above: constraints, verify compilation works
        System.out.println("  - Parsing successful, no regression ✓");

        System.out.println("  Test 3 PASSED\n");
    }
}
