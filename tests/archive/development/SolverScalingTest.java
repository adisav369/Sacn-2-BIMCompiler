package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 19: Solver Scaling Test
 *
 * Tests solver performance with larger room counts:
 * - Test A: 10 rooms single storey
 * - Test B: 8 rooms across 2 storeys with vertical constraints
 *
 * Target: <5 seconds solve time
 */
public class SolverScalingTest {

    private static final long TIMEOUT_MS = 5000; // 5 second limit

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 19: SOLVER SCALING TEST");
        System.out.println("=".repeat(70));

        boolean testAPassed = false;
        boolean testBPassed = false;

        // Test A: 10-room single storey
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST A: 10-ROOM SINGLE STOREY");
        System.out.println("-".repeat(70));

        try {
            String dslA = Files.readString(Path.of("examples/scale_test_10.bim"));
            System.out.println("DSL loaded: scale_test_10.bim");

            long startA = System.currentTimeMillis();
            BuildingDefinition defA = BuildingParser.parse(dslA);
            BuildingSpec specA = BuildingCompiler.compile(defA);
            long timeA = System.currentTimeMillis() - startA;

            int roomCount = specA.storeys().get(0).rooms().size();
            int wallCount = specA.storeys().get(0).walls().size();

            System.out.printf("\nResults:%n");
            System.out.printf("  Rooms: %d%n", roomCount);
            System.out.printf("  Walls: %d%n", wallCount);
            System.out.printf("  Total time: %dms%n", timeA);
            System.out.printf("  Under 5s limit: %s%n", timeA < TIMEOUT_MS ? "YES" : "NO");

            testAPassed = roomCount == 10 && timeA < TIMEOUT_MS;
            System.out.println(testAPassed ? "[PASS] 10-room test succeeded" :
                                            "[FAIL] 10-room test failed");

        } catch (Exception e) {
            System.out.println("[FAIL] Exception: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Cannot satisfy")) {
                System.out.println("  Constraint failure - solver could not find solution");
            }
        }

        // Test B: 8-room vertical
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST B: 8-ROOM VERTICAL (2 STOREYS)");
        System.out.println("-".repeat(70));

        try {
            String dslB = Files.readString(Path.of("examples/scale_test_vertical.bim"));
            System.out.println("DSL loaded: scale_test_vertical.bim");

            long startB = System.currentTimeMillis();
            BuildingDefinition defB = BuildingParser.parse(dslB);
            BuildingSpec specB = BuildingCompiler.compile(defB);
            long timeB = System.currentTimeMillis() - startB;

            int groundRooms = specB.storeys().get(0).rooms().size();
            int upperRooms = specB.storeys().get(1).rooms().size();
            int totalRooms = groundRooms + upperRooms;

            // Verify vertical constraints
            RoomSpec living = null, master = null;
            RoomSpec kitchen = null, bed2 = null;
            RoomSpec bathG = null, bathU = null;

            for (RoomSpec r : specB.storeys().get(0).rooms()) {
                if (r.name().equals("living")) living = r;
                if (r.name().equals("kitchen")) kitchen = r;
                if (r.name().equals("bath_g")) bathG = r;
            }
            for (RoomSpec r : specB.storeys().get(1).rooms()) {
                if (r.name().equals("master")) master = r;
                if (r.name().equals("bed2")) bed2 = r;
                if (r.name().equals("bath_u")) bathU = r;
            }

            boolean verticalOK = true;
            if (living != null && master != null) {
                double dx = Math.abs(master.minX() - living.minX());
                double dy = Math.abs(master.minY() - living.minY());
                System.out.printf("  master above living: delta=(%.4f, %.4f)%n", dx, dy);
                verticalOK &= dx < 0.01 && dy < 0.01;
            }
            if (kitchen != null && bed2 != null) {
                double dx = Math.abs(bed2.minX() - kitchen.minX());
                double dy = Math.abs(bed2.minY() - kitchen.minY());
                System.out.printf("  bed2 above kitchen: delta=(%.4f, %.4f)%n", dx, dy);
                verticalOK &= dx < 0.01 && dy < 0.01;
            }
            if (bathG != null && bathU != null) {
                double dx = Math.abs(bathU.minX() - bathG.minX());
                double dy = Math.abs(bathU.minY() - bathG.minY());
                System.out.printf("  bath_u stack bath_g: delta=(%.4f, %.4f)%n", dx, dy);
                verticalOK &= dx < 0.01 && dy < 0.01;
            }

            System.out.printf("\nResults:%n");
            System.out.printf("  Ground rooms: %d%n", groundRooms);
            System.out.printf("  Upper rooms: %d%n", upperRooms);
            System.out.printf("  Total rooms: %d%n", totalRooms);
            System.out.printf("  Vertical constraints: %s%n", verticalOK ? "SATISFIED" : "FAILED");
            System.out.printf("  Total time: %dms%n", timeB);
            System.out.printf("  Under 5s limit: %s%n", timeB < TIMEOUT_MS ? "YES" : "NO");

            testBPassed = totalRooms == 8 && verticalOK && timeB < TIMEOUT_MS;
            System.out.println(testBPassed ? "[PASS] 8-room vertical test succeeded" :
                                            "[FAIL] 8-room vertical test failed");

        } catch (Exception e) {
            System.out.println("[FAIL] Exception: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Cannot satisfy")) {
                System.out.println("  Constraint failure - solver could not find solution");
            }
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SOLVER SCALING SUMMARY");
        System.out.println("=".repeat(70));

        System.out.println("Test A (10-room flat): " + (testAPassed ? "PASS" : "FAIL"));
        System.out.println("Test B (8-room vertical): " + (testBPassed ? "PASS" : "FAIL"));

        if (testAPassed && testBPassed) {
            System.out.println("\nOVERALL: PASS - Solver scales to 10 rooms");
        } else {
            System.out.println("\nOVERALL: FAIL - Solver scaling issues detected");
            System.out.println("\nRecommendations:");
            if (!testAPassed) {
                System.out.println("  - Simplify 10-room constraints");
                System.out.println("  - Add solver heuristics");
                System.out.println("  - Consider room grouping");
            }
        }
        System.out.println("=".repeat(70));
    }
}
