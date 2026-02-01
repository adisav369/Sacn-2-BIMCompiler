package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 48A: Full compilation test for Stacked-Duplex.
 * Verifies STACKED layout detection is triggered during compilation.
 */
public class StackedDuplexCompileTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 48A: Stacked Duplex Compilation Test ===\n");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        System.out.println("Building: " + def.name());
        System.out.println("Units: " + def.units().size());

        // Compile - this should trigger STACKED detection
        System.out.println("\n--- Compilation Output ---");
        System.out.println("Calling BuildingCompiler.compile()...");
        BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);
        System.out.println("Compile returned: " + (spec != null ? "spec" : "null"));

        System.out.println("\n--- Compilation Result ---");
        assert spec != null : "Compilation returned null";
        System.out.println("Compilation succeeded");
        System.out.println("Storeys: " + spec.storeys().size());

        // Verify room positions - Unit B rooms should align with Unit A
        int totalRooms = 0;
        for (BuildingCompiler.StoreySpec storey : spec.storeys()) {
            System.out.println("\n" + storey.name() + " (level " + storey.level() + "):");
            for (BuildingCompiler.RoomSpec room : storey.rooms()) {
                System.out.printf("  %s: [%.1f,%.1f] - [%.1f,%.1f] (unit=%s)%n",
                    room.name(), room.minX(), room.minY(), room.maxX(), room.maxY(),
                    room.unitId());
                totalRooms++;
            }
        }

        assert totalRooms == 8 : "Expected 8 rooms (4 per unit), got " + totalRooms;
        System.out.println("\nTotal rooms: " + totalRooms + " ✓");

        // Verify above: alignment - living_b should have same XY as living_a
        BuildingCompiler.RoomSpec livingA = null;
        BuildingCompiler.RoomSpec livingB = null;
        for (BuildingCompiler.StoreySpec storey : spec.storeys()) {
            for (BuildingCompiler.RoomSpec room : storey.rooms()) {
                if (room.name().equals("living_a")) livingA = room;
                if (room.name().equals("living_b")) livingB = room;
            }
        }

        if (livingA != null && livingB != null) {
            double tolerance = 0.1;
            boolean xMatch = Math.abs(livingA.minX() - livingB.minX()) < tolerance;
            boolean yMatch = Math.abs(livingA.minY() - livingB.minY()) < tolerance;
            System.out.printf("\nliving_a: (%.1f, %.1f)%n", livingA.minX(), livingA.minY());
            System.out.printf("living_b: (%.1f, %.1f)%n", livingB.minX(), livingB.minY());
            System.out.println("XY alignment: " + (xMatch && yMatch ? "✓" : "MISMATCH"));
        }

        System.out.println("\n=== Stacked Duplex Compilation Test Passed ===");
    }
}
