package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 46A Test: Verify multi-unit DSL parsing.
 */
public class MultiUnitParserTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 46A: Multi-Unit Parser Test ===\n");

        // Test 1: Parse Duplex-Pilot.bim
        testDuplexPilot();

        // Test 2: Backward compatibility - single-unit building
        testBackwardCompatibility();

        System.out.println("\n=== All Phase 46A Tests Passed ===");
    }

    private static void testDuplexPilot() throws Exception {
        System.out.println("Test 1: Parsing Duplex-Pilot.bim");

        String dsl = Files.readString(Path.of("examples/Duplex-Pilot.bim"));
        BuildingDefinition building = BuildingParser.parse(dsl);

        // Verify building type
        assert building.buildingType() == BuildingType.MULTI_UNIT :
            "Expected MULTI_UNIT, got " + building.buildingType();
        System.out.println("  - Building type: " + building.buildingType() + " ✓");

        // Verify building name
        assert "Duplex-Pilot".equals(building.name()) :
            "Expected Duplex-Pilot, got " + building.name();
        System.out.println("  - Building name: " + building.name() + " ✓");

        // Verify units
        assert building.units().size() == 2 :
            "Expected 2 units, got " + building.units().size();
        System.out.println("  - Unit count: " + building.units().size() + " ✓");

        // Verify Unit A
        UnitDefinition unitA = building.getUnit("A");
        assert unitA != null : "Unit A not found";
        assert unitA.type() == UnitType.RESIDENTIAL :
            "Expected RESIDENTIAL, got " + unitA.type();
        assert unitA.entry() == EntryType.DIRECT :
            "Expected DIRECT entry, got " + unitA.entry();
        System.out.println("  - Unit A: " + unitA.type() + ", entry=" + unitA.entry() + " ✓");

        // Verify Unit A rooms
        assert unitA.getAllRooms().size() == 4 :
            "Expected 4 rooms in Unit A, got " + unitA.getAllRooms().size();
        System.out.println("  - Unit A rooms: " + unitA.getAllRooms().size() + " ✓");

        // Verify Unit A meters
        assert unitA.meters().size() == 2 :
            "Expected 2 meters in Unit A, got " + unitA.meters().size();
        assert unitA.getElectricalMeter() != null : "No electrical meter in Unit A";
        assert unitA.getWaterMeter() != null : "No water meter in Unit A";
        System.out.println("  - Unit A meters: electrical at " +
            unitA.getElectricalMeter().locationRoom() + ", water at " +
            unitA.getWaterMeter().locationRoom() + " ✓");

        // Verify Unit B
        UnitDefinition unitB = building.getUnit("B");
        assert unitB != null : "Unit B not found";
        assert unitB.getAllRooms().size() == 4 :
            "Expected 4 rooms in Unit B, got " + unitB.getAllRooms().size();
        System.out.println("  - Unit B rooms: " + unitB.getAllRooms().size() + " ✓");

        // Verify no shared spaces (duplex has direct entry)
        assert building.shared().isEmpty() :
            "Expected no shared spaces in side-by-side duplex";
        System.out.println("  - Shared spaces: none (as expected) ✓");

        // Verify roof
        assert building.roof() != null : "No roof definition";
        assert building.roof().pitchDegrees() == 15.0 :
            "Expected 15deg pitch, got " + building.roof().pitchDegrees();
        System.out.println("  - Roof: " + building.roof().pitchDegrees() + "deg ✓");

        // Verify isMultiUnit()
        assert building.isMultiUnit() : "isMultiUnit() should return true";
        System.out.println("  - isMultiUnit(): true ✓");

        // Verify findUnitContainingRoom()
        String containingUnit = building.findUnitContainingRoom("living_a");
        assert "A".equals(containingUnit) :
            "Expected living_a in Unit A, got " + containingUnit;
        System.out.println("  - findUnitContainingRoom(living_a): " + containingUnit + " ✓");

        System.out.println("  Test 1 PASSED\n");
    }

    private static void testBackwardCompatibility() throws Exception {
        System.out.println("Test 2: Backward compatibility (single-unit)");

        // Parse existing TB-LKTN-2S.bim (single-unit)
        String dsl = Files.readString(Path.of("examples/TB-LKTN-2S.bim"));
        BuildingDefinition building = BuildingParser.parse(dsl);

        // Should be detected as single-unit
        assert building.buildingType() == BuildingType.SINGLE_UNIT :
            "Expected SINGLE_UNIT, got " + building.buildingType();
        System.out.println("  - Building type: " + building.buildingType() + " ✓");

        // Should have storeys directly
        assert !building.storeys().isEmpty() :
            "Expected storeys in single-unit building";
        System.out.println("  - Storey count: " + building.storeys().size() + " ✓");

        // Should have no units
        assert building.units().isEmpty() :
            "Expected no units in single-unit building";
        System.out.println("  - Unit count: 0 ✓");

        // isMultiUnit() should return false
        assert !building.isMultiUnit() :
            "isMultiUnit() should return false for single-unit";
        System.out.println("  - isMultiUnit(): false ✓");

        System.out.println("  Test 2 PASSED\n");
    }
}
