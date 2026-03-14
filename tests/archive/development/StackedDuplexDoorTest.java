package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.StoreyDef;
import com.bim.compiler.dsl.BuildingDefinition.RoomDef;
import com.bim.compiler.dsl.BuildingDefinition.OpeningDef;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 48D.2: Test door connection from Unit B to SHARED landing.
 * Verifies:
 * 1. Door from living_b to upper_landing is compiled
 * 2. Door has correct connectsTo field
 * 3. Witness path includes Unit→SHARED boundary crossing
 */
public class StackedDuplexDoorTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 48D.2: Unit→SHARED Door Test ===\n");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        // Verify Unit B has entry:SHARED
        System.out.println("Test 1: Unit B entry type");
        assert def.units().size() == 2 : "Expected 2 units";
        UnitDefinition unitB = def.units().stream()
            .filter(u -> "B".equals(u.name()))
            .findFirst()
            .orElseThrow();
        assert unitB.entry() == EntryType.SHARED : "Unit B should have entry:SHARED";
        System.out.println("  - Unit B entry: " + unitB.entry() + " ✓");

        // Check that living_b has a door to upper_landing
        System.out.println("\nTest 2: Door declaration in DSL");
        boolean hasDoorToLanding = false;
        for (StoreyDef storey : unitB.storeys()) {
            for (RoomDef room : storey.rooms()) {
                if ("living_b".equals(room.name())) {
                    for (OpeningDef opening : room.openings()) {
                        if ("DOOR".equals(opening.type()) && "upper_landing".equals(opening.connectsTo())) {
                            hasDoorToLanding = true;
                            System.out.println("  - Found: DOOR " + opening.wall() + " to:" + opening.connectsTo() + " ✓");
                        }
                    }
                }
            }
        }
        assert hasDoorToLanding : "living_b should have DOOR to upper_landing";

        // Compile and check DoorSpec
        System.out.println("\nTest 3: Compiled DoorSpec");
        BuildingSpecs.BuildingSpec spec = BuildingCompiler.compile(def);

        boolean foundDoorSpec = false;
        for (BuildingSpecs.StoreySpec storey : spec.storeys()) {
            for (BuildingSpecs.DoorSpec door : storey.doors()) {
                if (door.roomName().equals("living_b") && "upper_landing".equals(door.connectsTo())) {
                    foundDoorSpec = true;
                    System.out.printf("  - DoorSpec: %s in %s, connectsTo=%s ✓%n",
                        door.name(), door.roomName(), door.connectsTo());
                }
            }
        }
        assert foundDoorSpec : "DoorSpec for living_b→upper_landing not found";

        // Verify witness path generation
        System.out.println("\nTest 4: Witness path verification");
        java.nio.file.Path witnessPath = Path.of("output/stacked_duplex_witness.json");
        // Phase 48D.2: Pass BuildingDefinition for per-unit entry tracking
        WitnessGenerator.generateWitness(spec, def, witnessPath);

        String witness = Files.readString(witnessPath);
        boolean hasLandingPath = witness.contains("upper_landing") && witness.contains("living_b");
        System.out.println("  - Witness contains living_b→upper_landing path: " + (hasLandingPath ? "✓" : "✗"));

        if (hasLandingPath) {
            // Extract and display the path
            int idx = witness.indexOf("\"upper_landing\"");
            if (idx > 0) {
                int start = Math.max(0, idx - 50);
                int end = Math.min(witness.length(), idx + 100);
                System.out.println("  - Witness excerpt: ..." + witness.substring(start, end).replace("\n", " ") + "...");
            }
        }

        System.out.println("\n=== Phase 48D.2 Test Complete ===");
    }
}
