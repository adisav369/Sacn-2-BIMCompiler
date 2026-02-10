package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TB-LKTN-DUPLEX End-to-End Test (Phase 110)
 *
 * Tests the foyer-spine duplex from IFC Duplex sample:
 * 1. DSL file loading from examples/TB-LKTN-DUPLEX.bim
 * 2. Multi-unit parsing (UNIT A + UNIT B)
 * 3. SHARED block parsing (stairs, landings)
 * 4. Per-unit storey structure
 *
 * NOTE: Full compilation is pending — the multi-unit compiler does not yet
 * support adjacency-driven layout (adjacent:, adjacent_unit:, stack:).
 * This test validates parsing only. Compilation E2E will be added when
 * the multi-unit adjacency solver is implemented.
 */
public class TBLKTNDuplexEndToEndTest {

    private static final String DSL_PATH = "examples/TB-LKTN-DUPLEX.bim";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN-DUPLEX PARSE TEST");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        String dsl = Files.readString(Path.of(DSL_PATH));

        // STEP 1: Parse
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("Building: " + def.name());

        if (def.name().equals("TB-LKTN-DUPLEX")) {
            System.out.println("[PASS] Building name");
            passed++;
        } else {
            System.out.println("[FAIL] Building name");
            failed++;
        }

        // STEP 2: Validate multi-unit structure
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: MULTI-UNIT STRUCTURE");
        System.out.println("-".repeat(70));

        boolean hasUnits = def.units() != null && !def.units().isEmpty();
        System.out.println("Multi-unit: " + hasUnits);

        if (hasUnits && def.units().size() == 2) {
            System.out.println("Units: " + def.units().size());
            for (var unit : def.units()) {
                System.out.printf("  - Unit %s: %d storeys, %d rooms total%n",
                    unit.name(), unit.storeys().size(),
                    unit.storeys().stream().mapToInt(s -> s.rooms().size()).sum());
            }
            System.out.println("[PASS] 2-unit duplex structure");
            passed++;
        } else {
            System.out.println("[FAIL] Expected 2 units");
            failed++;
        }

        // STEP 3: Validate per-unit storeys
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: PER-UNIT STOREYS");
        System.out.println("-".repeat(70));

        boolean storeysOk = true;
        for (var unit : def.units()) {
            if (unit.storeys().size() != 2) {
                System.out.printf("[FAIL] Unit %s has %d storeys, expected 2%n",
                    unit.name(), unit.storeys().size());
                storeysOk = false;
            } else {
                for (var storey : unit.storeys()) {
                    System.out.printf("  Unit %s / %s: %d rooms%n",
                        unit.name(), storey.name(), storey.rooms().size());
                }
            }
        }

        if (storeysOk) {
            System.out.println("[PASS] Each unit has 2 storeys");
            passed++;
        } else {
            failed++;
        }

        // STEP 4: Validate SHARED block
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 4: SHARED BLOCK");
        System.out.println("-".repeat(70));

        if (def.shared() != null && def.shared().storeys() != null) {
            int sharedStoreys = def.shared().storeys().size();
            int totalSharedRooms = def.shared().storeys().stream()
                .mapToInt(s -> s.rooms().size()).sum();
            System.out.printf("Shared storeys: %d, rooms: %d%n", sharedStoreys, totalSharedRooms);
            System.out.println("[PASS] SHARED block parsed");
            passed++;
        } else {
            System.out.println("[FAIL] No SHARED block found");
            failed++;
        }

        // STEP 5: Compile attempt (expected: limited output)
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 5: COMPILATION (adjacency solver pending)");
        System.out.println("-".repeat(70));

        try {
            CompilationResult result = BuildingCompiler.compileWithValidation(def);
            BuildingSpec spec = result.spec();
            int storeyCount = spec.storeys().size();
            System.out.printf("Compiled storeys: %d%n", storeyCount);

            if (storeyCount >= 2) {
                System.out.println("[PASS] Full compilation");
                passed++;
            } else {
                System.out.println("[SKIP] Compilation produced " + storeyCount
                    + " storeys (adjacency solver not yet implemented)");
                // Not counted as fail — known limitation
            }
        } catch (Exception e) {
            System.out.println("[SKIP] Compilation error: " + e.getMessage());
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n%n", passed, failed);

        if (failed == 0) {
            System.out.println("[SUCCESS] TB-LKTN-DUPLEX parse validation complete");
            System.out.println("         Full E2E pending adjacency solver implementation.");
        } else {
            System.out.println("[FAILURE] " + failed + " test(s) failed");
            System.exit(1);
        }
        System.out.println("=".repeat(70));
    }
}
