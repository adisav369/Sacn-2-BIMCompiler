package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.SprinklerPlacer.SprinklerInstance;
import com.bim.compiler.library.SprinklerPlacer.SprinklerType;

import java.util.List;

/**
 * Test SprinklerPlacer functionality.
 *
 * Verifies:
 * 1. Pendant sprinkler attaches at ceiling, hangs down
 * 2. Upright sprinkler attaches at floor, points up
 * 3. Grid placement produces correct count and spacing
 * 4. Vertex transformation works correctly
 */
public class SprinklerPlacerTest {

    private static final String LIBRARY_PATH = "library/component_library.db";

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("SPRINKLER PLACER TEST");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            SprinklerPlacer placer = new SprinklerPlacer(library);

            // Test 1: Pendant sprinkler at ceiling
            System.out.println("\nTest 1: Pendant sprinkler at ceiling Z=3.0m");
            Point3D ceiling = new Point3D(5.0, 5.0, 3.0);
            SprinklerInstance pendant = placer.placePendant(ceiling, 0.0);

            System.out.println("  Ceiling point: " + ceiling);
            System.out.println("  World center: " + pendant.worldCenter());
            System.out.println("  World bounds: " + pendant.getWorldBounds());
            System.out.println("  Type: " + pendant.type());

            // Pendant should have maxZ at or below ceiling
            boolean test1 = pendant.getWorldBounds().maxZ() <= ceiling.z() + 0.001;
            System.out.println("  Attaches below ceiling: " + (test1 ? "PASS" : "FAIL"));
            if (test1) passed++; else failed++;

            // Test 2: Upright sprinkler at floor
            System.out.println("\nTest 2: Upright sprinkler at floor Z=0.0m");
            Point3D floor = new Point3D(5.0, 5.0, 0.0);
            SprinklerInstance upright = placer.placeUpright(floor, 0.0);

            System.out.println("  Floor point: " + floor);
            System.out.println("  World center: " + upright.worldCenter());
            System.out.println("  World bounds: " + upright.getWorldBounds());
            System.out.println("  Type: " + upright.type());

            // Upright should have minZ at or above floor
            boolean test2 = upright.getWorldBounds().minZ() >= floor.z() - 0.001;
            System.out.println("  Attaches above floor: " + (test2 ? "PASS" : "FAIL"));
            if (test2) passed++; else failed++;

            // Test 3: Grid placement (10m x 10m area, 4.6m spacing)
            System.out.println("\nTest 3: Grid placement 10m x 10m, spacing 4.6m");
            BoundingBox area = new BoundingBox(
                0, 10,  // minX, maxX
                0, 10,  // minY, maxY
                0, 3    // minZ, maxZ
            );
            List<SprinklerInstance> grid = placer.placeGrid(area, 3.0, 4.6);

            // Expected: 2x2 = 4 sprinklers (starts at 2.3m from edge, then +4.6m)
            System.out.println("  Area: 10m x 10m");
            System.out.println("  Spacing: 4.6m (NFPA 13)");
            System.out.println("  Sprinklers placed: " + grid.size());

            int expectedCount = 4; // (10/4.6) floored = 2 per axis
            boolean test3 = grid.size() == expectedCount;
            System.out.println("  Expected count (" + expectedCount + "): " + (test3 ? "PASS" : "FAIL"));
            if (test3) passed++; else failed++;

            // Print grid positions
            System.out.println("  Positions:");
            for (SprinklerInstance s : grid) {
                System.out.printf("    (%.2f, %.2f, %.2f)%n",
                    s.worldCenter().x(), s.worldCenter().y(), s.worldCenter().z());
            }

            // Test 4: Vertex transformation
            System.out.println("\nTest 4: Vertex transformation");
            float[] vertices = placer.getTransformedVertices(pendant);
            System.out.println("  Vertex count: " + (vertices.length / 3));

            // Calculate bounds from transformed vertices
            float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
            float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
            for (int i = 0; i < vertices.length; i += 3) {
                minX = Math.min(minX, vertices[i]);
                maxX = Math.max(maxX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                maxY = Math.max(maxY, vertices[i + 1]);
                minZ = Math.min(minZ, vertices[i + 2]);
                maxZ = Math.max(maxZ, vertices[i + 2]);
            }

            System.out.printf("  Vertex bounds: X[%.3f, %.3f] Y[%.3f, %.3f] Z[%.3f, %.3f]%n",
                minX, maxX, minY, maxY, minZ, maxZ);

            BoundingBox expected = pendant.getWorldBounds();
            boolean test4 = Math.abs(minX - expected.minX()) < 0.01 &&
                           Math.abs(maxX - expected.maxX()) < 0.01 &&
                           Math.abs(minZ - expected.minZ()) < 0.01;
            System.out.println("  Bounds match: " + (test4 ? "PASS" : "FAIL"));
            if (test4) passed++; else failed++;

            // Test 5: Rotation
            System.out.println("\nTest 5: Rotation (90 degrees)");
            SprinklerInstance rotated = placer.placePendant(ceiling, Math.PI / 2);
            float[] rotatedVerts = placer.getTransformedVertices(rotated);

            // First few vertices should be different due to rotation
            boolean test5 = vertices.length == rotatedVerts.length &&
                           (Math.abs(vertices[0] - rotatedVerts[0]) > 0.001 ||
                            Math.abs(vertices[1] - rotatedVerts[1]) > 0.001);
            System.out.println("  Vertices rotated: " + (test5 ? "PASS" : "FAIL"));
            if (test5) passed++; else failed++;

            library.close();

        } catch (Exception e) {
            System.out.println("\nERROR: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("RESULTS: %d passed, %d failed%n", passed, failed);
        System.out.println("=".repeat(70));

        if (failed == 0) {
            System.out.println("ALL TESTS PASSED");
        }
    }
}
