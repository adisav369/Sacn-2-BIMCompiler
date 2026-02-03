package com.bim.compiler.dsl;

import java.io.*;
import java.nio.file.*;

/**
 * Phase 20: Intent Resolver Test Suite
 *
 * Tests the NL → DSL → IFC pipeline with various intents.
 */
public class IntentResolverTest {

    private static final String[] TEST_INTENTS = {
        "2 bedroom apartment",
        "3 bedroom house with ensuite",
        "open plan 2 bedroom flat",
        "large 4 bedroom townhouse"
    };

    private static final int[] EXPECTED_ROOMS = {5, 7, 5, 8};

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 20: INTENT RESOLVER TEST SUITE");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        for (int i = 0; i < TEST_INTENTS.length; i++) {
            String intent = TEST_INTENTS[i];
            int expectedRooms = EXPECTED_ROOMS[i];

            System.out.println("\n" + "-".repeat(70));
            System.out.printf("TEST %d: \"%s\"%n", i + 1, intent);
            System.out.println("-".repeat(70));

            try {
                Path outputDir = Path.of("output/intent_test_" + (i + 1));
                Files.createDirectories(outputDir);

                // Step 1: Run Python resolver
                ProcessBuilder pb = new ProcessBuilder(
                    "bash", "-c",
                    "source venv/bin/activate && python scripts/intent_resolver.py \"" +
                    intent + "\" " + outputDir + "/spec.json"
                );
                pb.inheritIO();
                Process p = pb.start();
                int exitCode = p.waitFor();

                if (exitCode != 0) {
                    System.out.println("[FAIL] Python resolver failed");
                    failed++;
                    continue;
                }

                // Step 2: Read spec and verify room count
                String specJson = Files.readString(outputDir.resolve("spec.json"));
                int roomCount = countRooms(specJson);
                System.out.printf("Rooms in spec: %d (expected %d)%n", roomCount, expectedRooms);

                // Step 3: Compile
                IntentCompiler compiler = new IntentCompiler();
                compiler.compile(outputDir.resolve("spec.json"), outputDir);

                // Step 4: Check DB
                Path dbPath = outputDir.resolve("building.db");
                boolean dbExists = Files.exists(dbPath);
                long dbSize = dbExists ? Files.size(dbPath) : 0;

                System.out.printf("DB created: %s (%d bytes)%n", dbExists, dbSize);

                // Verify
                boolean testPassed = roomCount >= expectedRooms - 1 &&  // Allow some variance
                                    roomCount <= expectedRooms + 1 &&
                                    dbExists && dbSize > 0;

                if (testPassed) {
                    System.out.println("[PASS]");
                    passed++;
                } else {
                    System.out.println("[FAIL]");
                    failed++;
                }

            } catch (Exception e) {
                System.out.println("[FAIL] Exception: " + e.getMessage());
                failed++;
            }
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("INTENT RESOLVER TEST SUMMARY");
        System.out.println("=".repeat(70));

        for (int i = 0; i < TEST_INTENTS.length; i++) {
            System.out.printf("%d. \"%s\"%n", i + 1, TEST_INTENTS[i]);
        }

        System.out.println();
        System.out.printf("PASSED: %d / %d%n", passed, TEST_INTENTS.length);

        if (passed == TEST_INTENTS.length) {
            System.out.println("\nOVERALL: PASS - Intent resolver working!");
        } else {
            System.out.println("\nOVERALL: FAIL - " + failed + " test(s) failed");
        }
        System.out.println("=".repeat(70));
    }

    private static int countRooms(String json) {
        // Simple count of "type" occurrences in rooms
        int count = 0;
        int idx = 0;
        while ((idx = json.indexOf("\"type\":", idx)) != -1) {
            count++;
            idx++;
        }
        // Subtract 1 for building_type
        return Math.max(0, count - 1);
    }
}
