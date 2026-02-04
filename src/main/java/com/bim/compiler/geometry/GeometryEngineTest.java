package com.bim.compiler.geometry;

import java.util.*;

/**
 * Test harness for GeometryEngine.
 * Verifies all primitives produce valid meshes.
 */
public class GeometryEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== GeometryEngine Test Suite ===\n");

        // Primitive Tests
        testBox();
        testGablePrism();
        testExtrude();
        testWall();
        testStairFlight();
        testRoofSurface();

        // Transform Tests
        testTranslate();
        testRotate();
        testScale();
        testMirror();

        // Array Tests
        testArray();
        testGridArray();

        // Merge Tests
        testMerge();

        // Validation Tests
        testValidation();

        // Euler Characteristic Tests
        testEulerCharacteristic();

        System.out.println("\n=== Summary ===");
        System.out.printf("Passed: %d / %d%n", passed, passed + failed);
        if (failed > 0) {
            System.out.println("SOME TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("All tests passed.");
        }
    }

    // =========================================================================
    // Primitive Tests
    // =========================================================================

    private static void testBox() {
        System.out.println("--- Box Tests ---");

        Mesh box = GeometryEngine.box(3, 4, 2.7);
        assertMesh("box(3,4,2.7)", box, 8, 12, 2);

        Mesh boxAt = GeometryEngine.boxAt(1, 2, 0.5, 2, 3, 1);
        assertMesh("boxAt(1,2,0.5,2,3,1)", boxAt, 8, 12, 2);

        // Verify bounds
        BoundingBox bounds = boxAt.bounds();
        assertEqual("boxAt minX", bounds.minX(), 1.0);
        assertEqual("boxAt maxX", bounds.maxX(), 3.0);
        assertEqual("boxAt minZ", bounds.minZ(), 0.5);
        assertEqual("boxAt maxZ", bounds.maxZ(), 1.5);
    }

    private static void testGablePrism() {
        System.out.println("--- Gable Prism Tests ---");

        Mesh prismX = GeometryEngine.gablePrism(6, 8, 2.7, 25, true);
        assertValid("gablePrism(ridgeAlongX)", prismX);

        Mesh prismY = GeometryEngine.gablePrism(6, 8, 2.7, 25, false);
        assertValid("gablePrism(ridgeAlongY)", prismY);

        // Verify height includes pitch
        double pitchRad = Math.toRadians(25);
        double ridgeRise = (8.0 / 2) * Math.tan(pitchRad);
        double expectedMaxZ = 2.7 + ridgeRise;
        assertEqual("gablePrism maxZ", prismX.bounds().maxZ(), expectedMaxZ);
    }

    private static void testExtrude() {
        System.out.println("--- Extrude Tests ---");

        // L-shaped profile
        List<Point2D> lShape = List.of(
            new Point2D(0, 0),
            new Point2D(2, 0),
            new Point2D(2, 1),
            new Point2D(1, 1),
            new Point2D(1, 2),
            new Point2D(0, 2)
        );

        Mesh extruded = GeometryEngine.extrude(lShape, 3);
        assertValid("extrude(L-shape)", extruded);

        // Vertices: 6 bottom + 6 top = 12
        assertEqual("extrude vertices", extruded.vertexCount(), 12);

        // Faces: bottom fan (4) + top fan (4) + sides (6*2) = 20
        assertEqual("extrude faces", extruded.faceCount(), 20);
    }

    private static void testWall() {
        System.out.println("--- Wall Tests ---");

        Mesh wall = GeometryEngine.wall(
            new Point3D(0, 0, 0),
            new Point3D(5, 0, 0),
            0.15,  // 150mm thickness
            2.7    // 2.7m height
        );
        assertMesh("wall(5m)", wall, 8, 12, 2);

        // Diagonal wall
        Mesh diagWall = GeometryEngine.wall(
            new Point3D(0, 0, 0),
            new Point3D(3, 4, 0),
            0.2,
            3.0
        );
        assertValid("wall(diagonal)", diagWall);
    }

    private static void testStairFlight() {
        System.out.println("--- Stair Flight Tests ---");

        Mesh stairs = GeometryEngine.stairFlight(1.0, 0.28, 0.175, 14);
        assertValid("stairFlight(14 steps)", stairs);

        // Total height should be 14 * 0.175 = 2.45m
        double expectedMaxZ = 14 * 0.175;
        assertEqual("stairFlight maxZ", stairs.bounds().maxZ(), expectedMaxZ);
    }

    private static void testRoofSurface() {
        System.out.println("--- Roof Surface Tests ---");

        Mesh roofX = GeometryEngine.roofSurface(0, 10, 0, 12, 5.4, 25, 0.3, true);
        assertValid("roofSurface(ridgeAlongX)", roofX);

        Mesh roofY = GeometryEngine.roofSurface(0, 10, 0, 8, 5.4, 25, 0.3, false);
        assertValid("roofSurface(ridgeAlongY)", roofY);

        // Verify winding consistency
        assertTrue("roofX consistent winding", roofX.hasConsistentWinding());
        assertTrue("roofY consistent winding", roofY.hasConsistentWinding());
    }

    // =========================================================================
    // Transform Tests
    // =========================================================================

    private static void testTranslate() {
        System.out.println("--- Translate Tests ---");

        Mesh box = GeometryEngine.box(1, 1, 1);
        Mesh moved = GeometryEngine.translate(box, 5, 3, 2);

        assertEqual("translate minX", moved.bounds().minX(), 5.0);
        assertEqual("translate minY", moved.bounds().minY(), 3.0);
        assertEqual("translate minZ", moved.bounds().minZ(), 2.0);
        assertValid("translated box", moved);
    }

    private static void testRotate() {
        System.out.println("--- Rotate Tests ---");

        Mesh box = GeometryEngine.boxAt(1, 0, 0, 2, 1, 1);
        Mesh rotated = GeometryEngine.rotateZ(box, Math.PI / 2);  // 90° CCW

        // After 90° CCW, the box at X=1-3 should be at Y=1-3
        assertClose("rotated minX", rotated.bounds().minX(), -1.0, 0.001);
        assertClose("rotated minY", rotated.bounds().minY(), 1.0, 0.001);
        assertValid("rotated box", rotated);
    }

    private static void testScale() {
        System.out.println("--- Scale Tests ---");

        Mesh box = GeometryEngine.box(1, 1, 1);
        Mesh scaled = GeometryEngine.scale(box, 2);

        assertEqual("scaled maxX", scaled.bounds().maxX(), 2.0);
        assertEqual("scaled maxY", scaled.bounds().maxY(), 2.0);
        assertEqual("scaled maxZ", scaled.bounds().maxZ(), 2.0);
        assertValid("scaled box", scaled);
    }

    private static void testMirror() {
        System.out.println("--- Mirror Tests ---");

        Mesh box = GeometryEngine.boxAt(1, 0, 0, 1, 1, 1);
        Mesh mirrored = GeometryEngine.mirrorX(box);

        assertEqual("mirrored minX", mirrored.bounds().minX(), -2.0);
        assertEqual("mirrored maxX", mirrored.bounds().maxX(), -1.0);
        assertValid("mirrored box", mirrored);
    }

    // =========================================================================
    // Array Tests
    // =========================================================================

    private static void testArray() {
        System.out.println("--- Array Tests ---");

        Mesh box = GeometryEngine.box(0.2, 0.2, 2.7);  // Column
        List<Mesh> columns = GeometryEngine.array(box, 5, 4.0, GeometryEngine.Axis.X);

        assertEqual("array count", columns.size(), 5);

        // Last column should be at X = 4 * 4.0 = 16
        assertEqual("array last minX", columns.get(4).bounds().minX(), 16.0);

        for (int i = 0; i < columns.size(); i++) {
            assertValid("array[" + i + "]", columns.get(i));
        }
    }

    private static void testGridArray() {
        System.out.println("--- Grid Array Tests ---");

        Mesh column = GeometryEngine.box(0.3, 0.3, 3.0);
        List<Mesh> grid = GeometryEngine.gridArray(column, 3, 4, 5.0, 6.0);

        assertEqual("grid count", grid.size(), 12);

        for (Mesh m : grid) {
            assertValid("grid element", m);
        }
    }

    // =========================================================================
    // Merge Tests
    // =========================================================================

    private static void testMerge() {
        System.out.println("--- Merge Tests ---");

        Mesh box1 = GeometryEngine.box(1, 1, 1);
        Mesh box2 = GeometryEngine.translate(GeometryEngine.box(1, 1, 1), 2, 0, 0);

        Mesh merged = GeometryEngine.merge(box1, box2);

        assertEqual("merged vertices", merged.vertexCount(), 16);
        assertEqual("merged faces", merged.faceCount(), 24);

        // Note: merged mesh is NOT manifold (two disjoint boxes)
        // but it should still have valid indices
        assertTrue("merged valid indices", merged.hasValidIndices());
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    private static void testValidation() {
        System.out.println("--- Validation Tests ---");

        // Create mesh with invalid face index
        List<Point3D> verts = List.of(
            new Point3D(0, 0, 0),
            new Point3D(1, 0, 0),
            new Point3D(0.5, 1, 0)
        );
        List<int[]> badFaces = List.of(
            new int[]{0, 1, 5}  // Index 5 is out of bounds
        );
        Mesh badMesh = new Mesh(verts, badFaces);

        assertFalse("badMesh valid indices", badMesh.hasValidIndices());

        // Create degenerate triangle (zero area)
        List<Point3D> collinearVerts = List.of(
            new Point3D(0, 0, 0),
            new Point3D(1, 0, 0),
            new Point3D(2, 0, 0)  // Collinear!
        );
        Mesh degenerateMesh = new Mesh(collinearVerts, List.of(new int[]{0, 1, 2}));

        assertFalse("degenerate has no degenerate", degenerateMesh.hasNoDegenerateFaces());
    }

    // =========================================================================
    // Euler Characteristic Tests
    // =========================================================================

    private static void testEulerCharacteristic() {
        System.out.println("--- Euler Characteristic Tests ---");

        // Box should have χ = 2 (closed surface, genus 0)
        Mesh box = GeometryEngine.box(1, 1, 1);
        assertEqual("box χ", box.eulerCharacteristic(), 2);

        // Gable prism should also have χ = 2
        Mesh prism = GeometryEngine.gablePrism(4, 5, 2.7, 25, true);
        assertEqual("prism χ", prism.eulerCharacteristic(), 2);

        // Roof surface (open, like two planes meeting) should have χ ≠ 2
        Mesh roof = GeometryEngine.roofSurface(0, 10, 0, 8, 5.4, 25, 0.3, true);
        System.out.printf("  roof χ = %d (open surface)%n", roof.eulerCharacteristic());
    }

    // =========================================================================
    // Assertion Helpers
    // =========================================================================

    private static void assertMesh(String name, Mesh m, int expectedVerts, int expectedFaces, int expectedEuler) {
        assertEqual(name + " vertices", m.vertexCount(), expectedVerts);
        assertEqual(name + " faces", m.faceCount(), expectedFaces);
        assertEqual(name + " euler", m.eulerCharacteristic(), expectedEuler);
        assertValid(name, m);
    }

    private static void assertValid(String name, Mesh m) {
        Mesh.ValidationResult result = m.validate();
        if (result.valid()) {
            System.out.printf("  ✓ %s is valid%n", name);
            passed++;
        } else {
            System.out.printf("  ✗ %s INVALID: %s%n", name, result.violations());
            failed++;
        }
    }

    private static void assertEqual(String name, int actual, int expected) {
        if (actual == expected) {
            System.out.printf("  ✓ %s = %d%n", name, actual);
            passed++;
        } else {
            System.out.printf("  ✗ %s = %d (expected %d)%n", name, actual, expected);
            failed++;
        }
    }

    private static void assertEqual(String name, double actual, double expected) {
        assertClose(name, actual, expected, 0.0001);
    }

    private static void assertClose(String name, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) < tolerance) {
            System.out.printf("  ✓ %s = %.4f%n", name, actual);
            passed++;
        } else {
            System.out.printf("  ✗ %s = %.4f (expected %.4f)%n", name, actual, expected);
            failed++;
        }
    }

    private static void assertTrue(String name, boolean condition) {
        if (condition) {
            System.out.printf("  ✓ %s%n", name);
            passed++;
        } else {
            System.out.printf("  ✗ %s (expected true)%n", name);
            failed++;
        }
    }

    private static void assertFalse(String name, boolean condition) {
        if (!condition) {
            System.out.printf("  ✓ %s%n", name);
            passed++;
        } else {
            System.out.printf("  ✗ %s (expected false)%n", name);
            failed++;
        }
    }
}
