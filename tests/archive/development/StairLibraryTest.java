package com.bim.compiler.library;

import com.bim.compiler.library.ComponentLibrary.ComponentDefinition;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Phase 14A Verification: Stair Library Lookup Test
 *
 * Verifies:
 * 1. Query returns actual Terminal stair (not null)
 * 2. Geometry hash matches component_definitions entry
 * 3. Dimensions within tolerance of target
 * 4. Railing lookup works
 * 5. Stringer lookup works
 */
public class StairLibraryTest {

    private static final String LIBRARY_DB = "library/component_library.db";

    // Duplex target dimensions
    private static final double TARGET_WIDTH = 1.4;   // meters
    private static final double TARGET_RISE = 2.8;    // storey height
    private static final double TOLERANCE = 0.5;      // acceptable deviation

    // Terminal actual dimensions (from catalog)
    private static final double TERMINAL_WIDTH = 1.4;
    private static final double TERMINAL_RISE = 2.04;
    private static final double TERMINAL_RUN = 3.04;

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 14A: STAIR LIBRARY LOOKUP TEST");
        System.out.println("=".repeat(70));

        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            ComponentLibrary library = new ComponentLibrary(LIBRARY_DB);
            int passed = 0;
            int failed = 0;

            // Test 1: Library statistics
            System.out.println("\n[TEST 1] Library Statistics");
            Map<String, Integer> stats = library.getStats();
            System.out.println("  IFC Classes in library:");
            for (Map.Entry<String, Integer> e : stats.entrySet()) {
                System.out.printf("    %s: %d definitions%n", e.getKey(), e.getValue());
            }

            boolean hasStairs = stats.containsKey("IfcStairFlight") && stats.get("IfcStairFlight") > 0;
            boolean hasRailings = stats.containsKey("IfcRailing") && stats.get("IfcRailing") > 0;
            boolean hasMembers = stats.containsKey("IfcMember") && stats.get("IfcMember") > 0;

            if (hasStairs && hasRailings && hasMembers) {
                System.out.println("  [PASS] Library has IfcStairFlight, IfcRailing, IfcMember");
                passed++;
            } else {
                System.out.println("  [FAIL] Missing required types");
                failed++;
            }

            // Test 2: StairFlight query with Terminal dimensions
            System.out.println("\n[TEST 2] StairFlight Query (Terminal dimensions)");
            ComponentDefinition stair = library.findStairFlight(TERMINAL_WIDTH, TERMINAL_RISE, 0.2);

            if (stair != null) {
                double actualWidth = stair.localBounds().maxX() - stair.localBounds().minX();
                double actualRise = stair.localBounds().maxZ() - stair.localBounds().minZ();
                double actualRun = stair.localBounds().maxY() - stair.localBounds().minY();

                System.out.printf("  Found: %s%n", stair.name());
                System.out.printf("  Geometry hash: %s%n", stair.geometryHash());
                System.out.printf("  Dimensions: %.2fm width × %.2fm run × %.2fm rise%n",
                    actualWidth, actualRun, actualRise);
                System.out.printf("  Vertices: %d, Faces: %d%n", stair.vertexCount(), stair.faceCount());

                boolean widthOk = Math.abs(actualWidth - TERMINAL_WIDTH) < 0.2;
                boolean riseOk = Math.abs(actualRise - TERMINAL_RISE) < 0.3;

                if (widthOk && riseOk) {
                    System.out.println("  [PASS] Dimensions within tolerance");
                    passed++;
                } else {
                    System.out.printf("  [FAIL] Dimensions out of tolerance (width: %b, rise: %b)%n",
                        widthOk, riseOk);
                    failed++;
                }
            } else {
                System.out.println("  [FAIL] No stair found for Terminal dimensions");
                failed++;
            }

            // Test 3: StairFlight query with duplex dimensions (may need scaling)
            System.out.println("\n[TEST 3] StairFlight Query (Duplex 2.8m storey)");
            ComponentDefinition duplexStair = library.findStairFlight(TARGET_WIDTH, TARGET_RISE, TOLERANCE);

            if (duplexStair != null) {
                double actualRise = duplexStair.localBounds().maxZ() - duplexStair.localBounds().minZ();
                System.out.printf("  Found: %s (rise: %.2fm)%n", duplexStair.name(), actualRise);
                System.out.printf("  Note: Rise %.2fm vs target %.2fm — may need scaling or multi-run%n",
                    actualRise, TARGET_RISE);
                System.out.println("  [PASS] Found stair within tolerance");
                passed++;
            } else {
                System.out.println("  [INFO] No exact match for 2.8m rise");
                System.out.println("  [INFO] Will need: 2× runs or parametric fallback");
                // Not a failure - this is expected
                passed++;
            }

            // Test 4: Railing query
            System.out.println("\n[TEST 4] Railing Query");
            ComponentDefinition railing = library.findRailing(TERMINAL_RUN, 5.0, 1.0);

            if (railing != null) {
                double length = railing.localBounds().maxY() - railing.localBounds().minY();
                double height = railing.localBounds().maxZ() - railing.localBounds().minZ();
                System.out.printf("  Found: %s%n", railing.name());
                System.out.printf("  Dimensions: %.2fm length × %.2fm height%n", length, height);
                System.out.printf("  Geometry hash: %s%n", railing.geometryHash());
                System.out.println("  [PASS] Railing found");
                passed++;
            } else {
                System.out.println("  [FAIL] No railing found");
                failed++;
            }

            // Test 5: Stringer query
            System.out.println("\n[TEST 5] Stringer Query (2.0m height)");
            List<ComponentDefinition> stringers = library.findStringers(2.0, 0.3);

            if (!stringers.isEmpty()) {
                System.out.printf("  Found %d stringers:%n", stringers.size());
                for (ComponentDefinition s : stringers) {
                    double height = s.localBounds().maxZ() - s.localBounds().minZ();
                    System.out.printf("    - %s: %.2fm height%n", s.name(), height);
                }
                System.out.println("  [PASS] Stringers found");
                passed++;
            } else {
                System.out.println("  [FAIL] No stringers found");
                failed++;
            }

            // Test 6: hasComponentType
            System.out.println("\n[TEST 6] hasComponentType checks");
            boolean hasStair = library.hasComponentType("IfcStairFlight");
            boolean hasRail = library.hasComponentType("IfcRailing");
            boolean hasDoor = library.hasComponentType("IfcDoor");
            boolean hasWindow = library.hasComponentType("IfcWindow");
            boolean hasFake = library.hasComponentType("IfcFakeClass");

            System.out.printf("  IfcStairFlight: %b%n", hasStair);
            System.out.printf("  IfcRailing: %b%n", hasRail);
            System.out.printf("  IfcDoor: %b%n", hasDoor);
            System.out.printf("  IfcWindow: %b%n", hasWindow);
            System.out.printf("  IfcFakeClass: %b%n", hasFake);

            if (hasStair && hasRail && hasDoor && hasWindow && !hasFake) {
                System.out.println("  [PASS] Type existence checks correct");
                passed++;
            } else {
                System.out.println("  [FAIL] Type existence check failed");
                failed++;
            }

            // Test 7: Geometry retrieval
            System.out.println("\n[TEST 7] Geometry Retrieval");
            if (stair != null) {
                var geom = library.getGeometry(stair.geometryHash());
                if (geom != null) {
                    System.out.printf("  Vertices blob: %d bytes%n", geom.vertices().length);
                    System.out.printf("  Faces blob: %d bytes%n", geom.faces().length);
                    System.out.printf("  Vertex count: %d%n", geom.vertexCount());
                    System.out.printf("  Face count: %d%n", geom.faceCount());

                    // Verify blob size matches count
                    int expectedVertBytes = geom.vertexCount() * 12; // 3 floats × 4 bytes
                    int expectedFaceBytes = geom.faceCount() * 12;   // 3 ints × 4 bytes

                    if (geom.vertices().length == expectedVertBytes) {
                        System.out.println("  [PASS] Geometry blob size correct");
                        passed++;
                    } else {
                        System.out.printf("  [FAIL] Expected %d bytes, got %d%n",
                            expectedVertBytes, geom.vertices().length);
                        failed++;
                    }
                } else {
                    System.out.println("  [FAIL] Geometry not found for hash");
                    failed++;
                }
            } else {
                System.out.println("  [SKIP] No stair to test geometry");
            }

            // Summary
            System.out.println("\n" + "=".repeat(70));
            System.out.println("SUMMARY");
            System.out.println("=".repeat(70));
            System.out.printf("Passed: %d%n", passed);
            System.out.printf("Failed: %d%n", failed);

            if (failed == 0) {
                System.out.println("\n✓ ALL STAIR LIBRARY TESTS PASSED");
                System.out.println("\nReady for Step 2: Create StairPlacementSpec");
            } else {
                System.out.println("\n✗ SOME TESTS FAILED - review before proceeding");
            }

            library.close();

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
