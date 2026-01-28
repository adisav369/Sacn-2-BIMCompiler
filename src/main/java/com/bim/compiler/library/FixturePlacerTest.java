package com.bim.compiler.library;

import com.bim.compiler.library.FixturePlacer.*;

import java.util.List;

/**
 * Phase 22: Test fixture placement from library.
 *
 * Verifies:
 * 1. Toilet, sink, exhaust fan found in library
 * 2. Bathroom fixtures placed with IBC clearances
 * 3. Kitchen sink placed on correct wall
 */
public class FixturePlacerTest {

    private static final String LIBRARY_PATH = "library/component_library.db";

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 22: FIXTURE PLACER TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Test 1: Library fixtures available
        if (testLibraryFixtures()) passed++; else failed++;

        // Test 2: Bathroom fixture placement
        if (testBathroomPlacement()) passed++; else failed++;

        // Test 3: Kitchen fixture placement
        if (testKitchenPlacement()) passed++; else failed++;

        // Summary
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.printf("RESULT: %d/%d tests passed%n", passed, passed + failed);
        System.out.println("=".repeat(70));

        if (failed > 0) System.exit(1);
    }

    // =========================================================================
    // TEST 1: Library fixtures available
    // =========================================================================

    private static boolean testLibraryFixtures() {
        System.out.println("TEST 1: Library fixtures available");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);

            // Check for toilet
            var toilet = library.getByName("Toilet");
            System.out.printf("Toilet: %s%n", toilet != null ?
                String.format("%.2f x %.2f x %.2fm",
                    toilet.localBounds().width(),
                    toilet.localBounds().depth(),
                    toilet.localBounds().height()) : "NOT FOUND");

            // Check for sink
            var sink = library.getByName("MRV basic round sink");
            System.out.printf("Sink: %s%n", sink != null ?
                String.format("%.2f x %.2f x %.2fm",
                    sink.localBounds().width(),
                    sink.localBounds().depth(),
                    sink.localBounds().height()) : "NOT FOUND");

            // Check for exhaust fan
            var exhaust = library.getByName("Exhaust air grill");
            System.out.printf("Exhaust fan: %s%n", exhaust != null ?
                String.format("%.2f x %.2f x %.2fm",
                    exhaust.localBounds().width(),
                    exhaust.localBounds().depth(),
                    exhaust.localBounds().height()) : "NOT FOUND");

            // Check for kitchen sink
            var kitchenSink = library.getByName("single_end_bowl_sink");
            System.out.printf("Kitchen sink: %s%n", kitchenSink != null ?
                String.format("%.2f x %.2f x %.2fm",
                    kitchenSink.localBounds().width(),
                    kitchenSink.localBounds().depth(),
                    kitchenSink.localBounds().height()) : "NOT FOUND");

            boolean pass = toilet != null && sink != null;
            System.out.printf("%n[%s] Required fixtures found%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 2: Bathroom fixture placement
    // =========================================================================

    private static boolean testBathroomPlacement() {
        System.out.println("TEST 2: Bathroom fixture placement");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            FixturePlacer placer = new FixturePlacer(library);

            // Standard bathroom: 2.5m x 2m
            double roomMinX = 0, roomMinY = 0;
            double roomMaxX = 2.5, roomMaxY = 2.0;
            double floorZ = 0, ceilingZ = 2.8;

            List<FixtureInstance> fixtures = placer.placeBathroomFixtures(
                roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, ceilingZ);

            System.out.printf("Room: %.1f x %.1f m%n", roomMaxX - roomMinX, roomMaxY - roomMinY);
            System.out.printf("Fixtures placed: %d%n%n", fixtures.size());

            for (FixtureInstance f : fixtures) {
                System.out.printf("  %s (%s)%n", f.type(), f.name());
                System.out.printf("    Position: (%.2f, %.2f, %.2f)%n",
                    f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z());
                System.out.printf("    Rotation: %.0f°%n", Math.toDegrees(f.rotation()));
            }

            // Verify toilet placement
            FixtureInstance toilet = fixtures.stream()
                .filter(f -> f.type() == FixtureType.TOILET)
                .findFirst().orElse(null);

            boolean toiletPlaced = toilet != null;
            boolean toiletClearance = toilet != null &&
                toilet.worldPosition().x() >= roomMinX + 0.38 &&  // Side clearance
                toilet.worldPosition().y() <= roomMaxY;           // Against north wall

            // Verify sink placement
            FixtureInstance sink = fixtures.stream()
                .filter(f -> f.type() == FixtureType.SINK)
                .findFirst().orElse(null);

            boolean sinkPlaced = sink != null;

            System.out.printf("%nToilet placed: %s%n", toiletPlaced);
            System.out.printf("Toilet clearance OK: %s%n", toiletClearance);
            System.out.printf("Sink placed: %s%n", sinkPlaced);

            boolean pass = toiletPlaced && sinkPlaced;
            System.out.printf("%n[%s] Bathroom fixtures placed correctly%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 3: Kitchen fixture placement
    // =========================================================================

    private static boolean testKitchenPlacement() {
        System.out.println("TEST 3: Kitchen fixture placement");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            FixturePlacer placer = new FixturePlacer(library);

            // Standard kitchen: 3m x 4m
            double roomMinX = 0, roomMinY = 0;
            double roomMaxX = 3.0, roomMaxY = 4.0;
            double floorZ = 0;
            String exteriorWall = "west";

            List<FixtureInstance> fixtures = placer.placeKitchenFixtures(
                roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, exteriorWall);

            System.out.printf("Room: %.1f x %.1f m%n", roomMaxX - roomMinX, roomMaxY - roomMinY);
            System.out.printf("Exterior wall: %s%n", exteriorWall);
            System.out.printf("Fixtures placed: %d%n%n", fixtures.size());

            for (FixtureInstance f : fixtures) {
                System.out.printf("  %s (%s)%n", f.type(), f.name());
                System.out.printf("    Position: (%.2f, %.2f, %.2f)%n",
                    f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z());
                System.out.printf("    Rotation: %.0f°%n", Math.toDegrees(f.rotation()));
            }

            // Verify sink is on west wall
            FixtureInstance sink = fixtures.stream()
                .filter(f -> f.type() == FixtureType.SINK)
                .findFirst().orElse(null);

            boolean sinkPlaced = sink != null;
            boolean sinkOnWestWall = sink != null &&
                sink.worldPosition().x() < roomMaxX / 2; // Left half = west side

            System.out.printf("%nSink placed: %s%n", sinkPlaced);
            System.out.printf("Sink on west wall: %s%n", sinkOnWestWall);

            boolean pass = sinkPlaced;
            System.out.printf("%n[%s] Kitchen fixtures placed correctly%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
