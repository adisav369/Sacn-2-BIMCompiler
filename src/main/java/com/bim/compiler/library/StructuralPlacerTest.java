package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.library.StructuralPlacer.*;

import java.util.List;

/**
 * Phase 23: Test structural element placement.
 *
 * Verifies:
 * 1. Columns placed at building corners
 * 2. Columns placed at T-junctions
 * 3. Lintels placed over openings > 900mm
 */
public class StructuralPlacerTest {

    private static final String LIBRARY_PATH = "library/component_library.db";

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 23: STRUCTURAL PLACER TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Test 1: Library structural elements available
        if (testLibraryElements()) passed++; else failed++;

        // Test 2: Corner column placement
        if (testCornerColumns()) passed++; else failed++;

        // Test 3: T-junction detection
        if (testTJunctions()) passed++; else failed++;

        // Test 4: Lintel placement over openings
        if (testLintels()) passed++; else failed++;

        // Summary
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.printf("RESULT: %d/%d tests passed%n", passed, passed + failed);
        System.out.println("=".repeat(70));

        if (failed > 0) System.exit(1);
    }

    // =========================================================================
    // TEST 1: Library elements available
    // =========================================================================

    private static boolean testLibraryElements() {
        System.out.println("TEST 1: Library structural elements available");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);

            var column = library.getByName("Rectangular Column");
            System.out.printf("Column: %s%n", column != null ?
                String.format("%s (%.2f x %.2f x %.2fm)",
                    column.name(),
                    column.localBounds().width(),
                    column.localBounds().depth(),
                    column.localBounds().height()) : "NOT FOUND");

            var beam = library.getByName("Concrete-Rectangular Beam");
            System.out.printf("Beam: %s%n", beam != null ?
                String.format("%s (%.2f x %.2f x %.2fm)",
                    beam.name(),
                    beam.localBounds().width(),
                    beam.localBounds().depth(),
                    beam.localBounds().height()) : "NOT FOUND");

            boolean pass = column != null && beam != null;
            System.out.printf("%n[%s] Structural elements found%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 2: Corner column placement
    // =========================================================================

    private static boolean testCornerColumns() {
        System.out.println("TEST 2: Corner column placement");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            StructuralPlacer placer = new StructuralPlacer(library);

            // Simple rectangular building: 10m x 8m
            double minX = 0, minY = 0, maxX = 10, maxY = 8;
            double baseZ = 0, height = 2.8;

            List<Point3D> corners = placer.findCorners(minX, minY, maxX, maxY, baseZ);
            List<ColumnInstance> columns = placer.placeColumns(corners, List.of(), baseZ, height);

            System.out.printf("Building: %.0f x %.0f m%n", maxX - minX, maxY - minY);
            System.out.printf("Corners found: %d%n", corners.size());
            System.out.printf("Columns placed: %d%n%n", columns.size());

            for (ColumnInstance col : columns) {
                System.out.printf("  %s (%s) at (%.1f, %.1f) h=%.1fm%n",
                    col.id(), col.type(),
                    col.basePosition().x(), col.basePosition().y(),
                    col.height());
            }

            // Should have 4 corner columns
            boolean pass = columns.size() == 4 &&
                columns.stream().allMatch(c -> c.type() == ColumnType.CORNER);

            System.out.printf("%n[%s] 4 corner columns placed%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 3: T-junction detection
    // =========================================================================

    private static boolean testTJunctions() {
        System.out.println("TEST 3: T-junction detection");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            StructuralPlacer placer = new StructuralPlacer(library);

            // Building with interior wall creating T-junction
            double minX = 0, minY = 0, maxX = 10, maxY = 8;
            double baseZ = 0;

            // Interior wall from (5, 0) to (5, 4) - T-junction at south wall
            List<WallInfo> interiorWalls = List.of(
                new WallInfo(5, 0, 5, 4, true)
            );

            List<Point3D> junctions = placer.findTJunctions(
                interiorWalls, minX, minY, maxX, maxY, baseZ);

            System.out.printf("Building: %.0f x %.0f m%n", maxX - minX, maxY - minY);
            System.out.printf("Interior walls: %d%n", interiorWalls.size());
            System.out.printf("T-junctions found: %d%n%n", junctions.size());

            for (Point3D j : junctions) {
                System.out.printf("  Junction at (%.1f, %.1f)%n", j.x(), j.y());
            }

            // Should find 1 T-junction at (5, 0)
            boolean pass = junctions.size() >= 1;

            System.out.printf("%n[%s] T-junctions detected%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 4: Lintel placement
    // =========================================================================

    private static boolean testLintels() {
        System.out.println("TEST 4: Lintel placement over openings");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            StructuralPlacer placer = new StructuralPlacer(library);

            // Openings: 1 door (0.9m), 1 window (1.2m), 1 small window (0.6m)
            List<OpeningInfo> openings = List.of(
                new OpeningInfo("door1", "south", 2.0, 0.0, 0.9, 2.1),
                new OpeningInfo("window1", "east", 10.0, 3.0, 1.2, 1.2),
                new OpeningInfo("small_window", "north", 5.0, 8.0, 0.6, 0.6)  // Too small
            );

            double baseZ = 0;
            List<BeamInstance> lintels = placer.placeLintels(openings, baseZ);

            System.out.printf("Openings: %d%n", openings.size());
            System.out.printf("Lintels placed: %d (only for openings >= 0.9m)%n%n", lintels.size());

            for (BeamInstance lintel : lintels) {
                System.out.printf("  %s over opening, length=%.2fm at z=%.2f%n",
                    lintel.id(), lintel.length(), lintel.position().z());
            }

            // Should have 2 lintels (door + large window, not small window)
            boolean pass = lintels.size() == 2;

            System.out.printf("%n[%s] Lintels placed over qualifying openings%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
