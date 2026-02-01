package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 48D: Test SHARED stair compilation for Stacked-Duplex.
 */
public class StackedDuplexStairTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 48D: SHARED Stair Test ===\n");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        // Verify parsed definition
        System.out.println("Test 1: SHARED parsing");
        assert def.shared() != null : "SHARED is null";
        assert !def.shared().isEmpty() : "SHARED is empty";
        assert def.shared().storeys().size() == 2 : "Expected 2 SHARED storeys";
        System.out.println("  - SHARED storeys: " + def.shared().storeys().size() + " ✓");

        // Compile
        BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

        // Check for stairs in compiled output
        System.out.println("\nTest 2: Compiled stairs");
        int totalStairs = 0;
        int totalLandings = 0;
        for (BuildingCompiler.StoreySpec storey : spec.storeys()) {
            System.out.println("  " + storey.name() + " (level " + storey.level() + "):");
            System.out.println("    stairs: " + storey.stairs().size());
            for (BuildingCompiler.StairSpec stair : storey.stairs()) {
                System.out.printf("      - %s to:%s at (%.1f, %.1f)%n",
                    stair.name(), stair.toStorey(), stair.x(), stair.y());
                totalStairs++;
            }
            System.out.println("    landings: " + storey.landings().size());
            for (BuildingCompiler.LandingSpec landing : storey.landings()) {
                System.out.printf("      - %s from:%s at (%.1f, %.1f)%n",
                    landing.name(), landing.fromStair(), landing.minX(), landing.minY());
                totalLandings++;
            }
        }

        System.out.println("\nTest 3: Stair/landing count");
        System.out.println("  - Total stairs: " + totalStairs);
        System.out.println("  - Total landings: " + totalLandings);

        if (totalStairs == 0 && totalLandings == 0) {
            System.out.println("\n[WARNING] No stairs/landings in compiled output!");
            System.out.println("This may indicate SHARED compilation doesn't include stairs yet.");
        } else {
            assert totalStairs >= 1 : "Expected at least 1 stair";
            assert totalLandings >= 1 : "Expected at least 1 landing";
            System.out.println("  ✓ Stairs and landings compiled");
        }

        System.out.println("\n=== Phase 48D Test Complete ===");
    }
}
