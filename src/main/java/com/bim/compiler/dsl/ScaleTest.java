package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Scale test for Space Solver - 10 rooms with complex constraints.
 */
public class ScaleTest {
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("SCALE TEST: 10 ROOMS WITH CONSTRAINTS");
        System.out.println("=".repeat(60));

        try {
            String dsl = Files.readString(Path.of("examples/house_10rooms.bim"));

            System.out.println("\nParsing...");
            long parseStart = System.currentTimeMillis();
            var def = BuildingParser.parse(dsl);
            long parseTime = System.currentTimeMillis() - parseStart;
            System.out.println("Parsed in " + parseTime + "ms");
            System.out.println("Rooms: " + def.storeys().get(0).rooms().size());

            // Count constraints
            int totalConstraints = 0;
            for (var room : def.storeys().get(0).rooms()) {
                totalConstraints += room.adjacentTo().size();
                totalConstraints += room.notAdjacentTo().size();
                if (room.exteriorWall() != null) totalConstraints++;
                System.out.printf("  %s: adj=%d notAdj=%d ext=%s%n",
                    room.name(), room.adjacentTo().size(),
                    room.notAdjacentTo().size(), room.exteriorWall());
            }
            System.out.println("Total constraints: " + totalConstraints);

            System.out.println("\nCompiling (includes solver)...");
            long compileStart = System.currentTimeMillis();
            var spec = BuildingCompiler.compile(def);
            long compileTime = System.currentTimeMillis() - compileStart;

            System.out.println("\n" + "=".repeat(60));
            System.out.println("RESULT: SUCCESS");
            System.out.println("=".repeat(60));
            System.out.println("Rooms compiled: " + spec.storeys().get(0).rooms().size());
            System.out.println("Compile time: " + compileTime + "ms");

            // Show room positions
            System.out.println("\nRoom positions:");
            for (var room : spec.storeys().get(0).rooms()) {
                System.out.printf("  %s: (%.0f,%.0f) to (%.0f,%.0f)%n",
                    room.name(), room.minX(), room.minY(), room.maxX(), room.maxY());
            }

        } catch (Exception e) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("RESULT: FAILED");
            System.out.println("=".repeat(60));
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
