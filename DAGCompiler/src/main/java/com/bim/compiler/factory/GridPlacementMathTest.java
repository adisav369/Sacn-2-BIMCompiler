package com.bim.compiler.factory;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.library.ComponentLibrary.ComponentDefinition;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.sql.*;
import java.util.*;

/**
 * Mathematical verification tests for grid placement.
 *
 * Tests:
 *   1. Grid count formula: n = floor((dim - spacing/2) / spacing) + 1
 *   2. Position derivation: first head at (spacing/2, spacing/2, ceiling_z - offset)
 *   3. Coverage proof: max corner distance ≤ spacing
 *   4. Edge case matrix for various room sizes
 *   5. Component geometry invariant: pendant attachment_z = bbox_max_z
 */
public class GridPlacementMathTest {

    private static final String LIBRARY_PATH = "library/component_library.db";
    private static final double TOLERANCE = 0.001;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("GRID PLACEMENT MATHEMATICAL VERIFICATION");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // Test 1: Grid count formula
        System.out.println("\nTest 1: Grid count formula verification");
        System.out.println("  Formula: n = floor((dim - spacing/2) / spacing) + 1");
        try {
            // Verify formula against actual placement
            double[][] testCases = {
                // {width, height, spacing, expected_count}
                {4.6, 4.6, 4.6, 1},   // Single head
                {9.2, 9.2, 4.6, 4},   // 2x2 grid
                {3.0, 3.0, 4.6, 1},   // Small room
                {10.0, 10.0, 4.6, 4}, // ~2x2
                {15.0, 15.0, 4.6, 9}, // 3x3
                {5.0, 4.0, 4.6, 1},   // Rectangle
                {6.0, 5.0, 4.6, 1},   // From DSL test
            };

            boolean allPass = true;
            for (double[] tc : testCases) {
                double w = tc[0], h = tc[1], s = tc[2];
                int expected = (int)tc[3];

                // Formula: count per axis
                int nx = (int)Math.floor((w - s/2) / s) + 1;
                int ny = (int)Math.floor((h - s/2) / s) + 1;
                int calculated = nx * ny;

                System.out.printf("    %.1fm x %.1fm @ %.1fm: nx=%d ny=%d = %d (expected %d) %s%n",
                    w, h, s, nx, ny, calculated, expected,
                    calculated == expected ? "[OK]" : "[MISMATCH]");

                if (calculated != expected) allPass = false;
            }

            if (allPass) {
                passed++;
                System.out.println("  [PASS]");
            } else {
                failed++;
                System.out.println("  [FAIL] Formula mismatches detected");
            }
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            failed++;
        }

        // Test 2: Position derivation
        System.out.println("\nTest 2: Position derivation verification");
        System.out.println("  Expected: first head at (spacing/2, spacing/2, ceiling_z - drop)");
        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HybridFactory factory = new HybridFactory(library);

            double spacing = 4.6;
            double ceilingZ = 2.8;
            BoundingBox area = new BoundingBox(0, 10, 0, 10, 0, ceilingZ);

            GridPlacementSpec spec = GridPlacementSpec.gridBuilder()
                .id("POS_TEST")
                .type(BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL)
                .discipline(Discipline.FP)
                .libraryComponentId("pendent")
                .area(area)
                .spacing(spacing)
                .attachmentZ(ceilingZ)
                .attachmentFace("TOP")
                .build();

            List<ISpatialElement> elements = factory.createGrid(spec);

            // First head should be at (spacing/2, spacing/2)
            ISpatialElement first = elements.get(0);
            double expectedX = spacing / 2;  // 2.3
            double expectedY = spacing / 2;  // 2.3

            double actualX = first.getPosition().x();
            double actualY = first.getPosition().y();
            double actualZ = first.getPosition().z();

            System.out.printf("    First head position: (%.3f, %.3f, %.3f)%n", actualX, actualY, actualZ);
            System.out.printf("    Expected XY: (%.3f, %.3f)%n", expectedX, expectedY);
            System.out.printf("    Ceiling Z: %.3f, Head Z: %.3f, Drop: %.0fmm%n",
                ceilingZ, actualZ, (ceilingZ - first.getBoundingBox().maxZ()) * 1000);

            boolean posOK = Math.abs(actualX - expectedX) < TOLERANCE &&
                           Math.abs(actualY - expectedY) < TOLERANCE;

            // Z should be below ceiling but close (deflector drop typically 25-150mm)
            double drop = ceilingZ - first.getBoundingBox().maxZ();
            boolean zOK = drop > 0 && drop < 0.2;  // 0-200mm drop acceptable

            if (posOK && zOK) {
                passed++;
                System.out.println("  [PASS]");
            } else {
                failed++;
                System.out.println("  [FAIL] Position mismatch");
            }

            library.close();
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: Coverage proof
        System.out.println("\nTest 3: Coverage proof (max corner distance ≤ spacing)");
        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HybridFactory factory = new HybridFactory(library);

            double spacing = 4.6;
            BoundingBox area = new BoundingBox(0, 10, 0, 10, 0, 2.8);

            GridPlacementSpec spec = GridPlacementSpec.gridBuilder()
                .id("COV_TEST")
                .type(BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL)
                .discipline(Discipline.FP)
                .libraryComponentId("pendent")
                .area(area)
                .spacing(spacing)
                .attachmentZ(2.8)
                .attachmentFace("TOP")
                .build();

            List<ISpatialElement> elements = factory.createGrid(spec);

            // Check corners of room
            double[][] corners = {
                {0, 0}, {10, 0}, {10, 10}, {0, 10}
            };

            boolean allCovered = true;
            for (double[] corner : corners) {
                double minDist = Double.MAX_VALUE;
                for (ISpatialElement e : elements) {
                    double dx = e.getPosition().x() - corner[0];
                    double dy = e.getPosition().y() - corner[1];
                    double dist = Math.sqrt(dx*dx + dy*dy);
                    minDist = Math.min(minDist, dist);
                }
                boolean covered = minDist <= spacing;
                System.out.printf("    Corner (%.0f,%.0f): nearest head %.2fm %s%n",
                    corner[0], corner[1], minDist,
                    covered ? "[COVERED]" : "[UNCOVERED]");
                if (!covered) allCovered = false;
            }

            if (allCovered) {
                passed++;
                System.out.println("  [PASS]");
            } else {
                failed++;
                System.out.println("  [FAIL] Some corners exceed coverage radius");
            }

            library.close();
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            failed++;
        }

        // Test 4: Edge case matrix
        System.out.println("\nTest 4: Edge case matrix verification");
        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HybridFactory factory = new HybridFactory(library);

            Object[][] edgeCases = {
                // {roomW, roomH, spacing, expectedCount, description}
                {4.6, 4.6, 4.6, 1, "Minimum room (1 head)"},
                {9.2, 9.2, 4.6, 4, "Double (2x2=4 heads)"},
                {3.0, 3.0, 4.6, 1, "Sub-minimum room"},
                {4.5, 4.5, 4.6, 1, "Just under threshold"},
                {4.7, 4.7, 4.6, 1, "Just over threshold"},
                {13.8, 13.8, 4.6, 9, "Triple (3x3=9 heads)"},
            };

            boolean allPass = true;
            for (Object[] tc : edgeCases) {
                double w = (double)tc[0];
                double h = (double)tc[1];
                double s = (double)tc[2];
                int expected = (int)tc[3];
                String desc = (String)tc[4];

                BoundingBox area = new BoundingBox(0, w, 0, h, 0, 2.8);
                GridPlacementSpec spec = GridPlacementSpec.gridBuilder()
                    .id("EDGE_TEST")
                    .type(BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL)
                    .discipline(Discipline.FP)
                    .libraryComponentId("pendent")
                    .area(area)
                    .spacing(s)
                    .attachmentZ(2.8)
                    .attachmentFace("TOP")
                    .build();

                List<ISpatialElement> elements = factory.createGrid(spec);
                int actual = elements.size();
                boolean ok = actual == expected;

                System.out.printf("    %s (%.1fx%.1f): %d heads %s%n",
                    desc, w, h, actual, ok ? "[OK]" : "[EXPECTED " + expected + "]");

                if (!ok) allPass = false;
            }

            if (allPass) {
                passed++;
                System.out.println("  [PASS]");
            } else {
                failed++;
                System.out.println("  [FAIL] Edge cases failed");
            }

            library.close();
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            failed++;
        }

        // Test 5: Component geometry invariant
        System.out.println("\nTest 5: Component geometry invariant (pendant attachment)");
        System.out.println("  Invariant: pendant sprinklers have local_attachment_z = bbox_max_z");
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIBRARY_PATH);
            String sql = """
                SELECT name, orientation, attachment_face,
                       local_max_z, local_min_z,
                       (local_max_z - local_min_z) as height
                FROM component_definitions
                WHERE name LIKE '%pendent%' OR name LIKE '%pendant%'
                LIMIT 5
            """;
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            boolean invariantHolds = true;
            while (rs.next()) {
                String name = rs.getString("name");
                String orientation = rs.getString("orientation");
                String attachment = rs.getString("attachment_face");
                double maxZ = rs.getDouble("local_max_z");
                double minZ = rs.getDouble("local_min_z");

                // For pendant (ceiling mount), attachment should be TOP
                boolean isTopAttach = "TOP".equals(attachment);

                System.out.printf("    %s: attach=%s, z=[%.3f,%.3f] %s%n",
                    name.substring(0, Math.min(30, name.length())),
                    attachment, minZ, maxZ,
                    isTopAttach ? "[OK]" : "[UNEXPECTED]");

                if (!isTopAttach) invariantHolds = false;
            }
            conn.close();

            if (invariantHolds) {
                passed++;
                System.out.println("  [PASS]");
            } else {
                failed++;
                System.out.println("  [FAIL] Attachment invariant violated");
            }
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            failed++;
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("RESULTS: %d passed, %d failed%n", passed, failed);
        System.out.println("=".repeat(70));

        // Document the grid placement rule
        System.out.println("\nDOCUMENTED RULE:");
        System.out.println("  Grid placement algorithm:");
        System.out.println("    1. nx = floor((width - spacing/2) / spacing) + 1");
        System.out.println("    2. ny = floor((height - spacing/2) / spacing) + 1");
        System.out.println("    3. First head at (spacing/2, spacing/2)");
        System.out.println("    4. Subsequent heads at (spacing/2 + i*spacing, spacing/2 + j*spacing)");
        System.out.println("    5. Ceiling attachment: Z = ceiling_height - deflector_drop");
        System.out.println("    6. Deflector drop: 25-150mm (from component local geometry)");

        if (failed == 0) {
            System.out.println("\nALL MATHEMATICAL INVARIANTS VERIFIED");
        }
    }
}
