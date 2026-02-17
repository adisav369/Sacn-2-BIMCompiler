package com.bim.compiler.export;

import com.bim.compiler.builder.PipeSpec;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMConstants;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.topology.WallThickness;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Round-trip validation test for IFC export.
 *
 * Proves the loop:
 *   Java Generate -> IFC Export -> Reimport -> Compare
 *
 * Success criteria:
 *   - Reimported bbox matches original within 5mm tolerance
 *   - All elements preserved (no data loss)
 *
 * Starts simple: single wall, no openings.
 */
public class IFCRoundTripTest {

    private static final String OUTPUT_DIR = "/home/red1/bim-compiler/output";
    private static final double TOLERANCE = BIMConstants.TOLERANCE; // 5mm

    private final IFCExporter exporter = new IFCExporter();

    public static void main(String[] args) {
        // Ensure output directory exists
        new File(OUTPUT_DIR).mkdirs();

        IFCRoundTripTest test = new IFCRoundTripTest();

        System.out.println("======================================================================");
        System.out.println("IFC ROUND-TRIP VALIDATION TEST");
        System.out.println("======================================================================");
        System.out.println("Tolerance: " + (TOLERANCE * 1000) + "mm");
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Test 1: Simple horizontal wall (X-aligned)
        if (test.testSimpleWallX()) passed++; else failed++;

        // Test 2: Simple horizontal wall (Y-aligned)
        if (test.testSimpleWallY()) passed++; else failed++;

        // Test 3: Wall at angle
        if (test.testAngledWall()) passed++; else failed++;

        // Test 4: Horizontal pipe (X-aligned)
        if (test.testHorizontalPipe()) passed++; else failed++;

        // Test 5: Vertical pipe (Z-aligned)
        if (test.testVerticalPipe()) passed++; else failed++;

        // Test 6: Diagonal pipe
        if (test.testDiagonalPipe()) passed++; else failed++;

        System.out.println();
        System.out.println("======================================================================");
        System.out.println(String.format("RESULTS: %d passed, %d failed", passed, failed));
        if (failed == 0) {
            System.out.println("ALL ROUND-TRIP TESTS PASSED");
        } else {
            System.out.println("SOME TESTS FAILED");
        }
        System.out.println("======================================================================");
    }

    /**
     * Test 1: Simple X-aligned wall.
     */
    private boolean testSimpleWallX() {
        System.out.println("Test 1: Simple wall (X-aligned)");

        WallSpec spec = new WallSpec(
            new Point3D(0, 0, 0),
            new Point3D(10, 0, 0),
            WallThickness.MM_150,
            3.0,
            new ArrayList<>()
        );

        return testWall(spec, "wall_x_aligned.ifc");
    }

    /**
     * Test 2: Simple Y-aligned wall.
     */
    private boolean testSimpleWallY() {
        System.out.println("Test 2: Simple wall (Y-aligned)");

        WallSpec spec = new WallSpec(
            new Point3D(5, 0, 0),
            new Point3D(5, 8, 0),
            WallThickness.MM_300,
            4.0,
            new ArrayList<>()
        );

        return testWall(spec, "wall_y_aligned.ifc");
    }

    /**
     * Test 3: Angled wall (45 degrees).
     */
    private boolean testAngledWall() {
        System.out.println("Test 3: Angled wall (45 degrees)");

        double length = 6.0;
        double angle45 = length / Math.sqrt(2);

        WallSpec spec = new WallSpec(
            new Point3D(0, 0, 0),
            new Point3D(angle45, angle45, 0),
            WallThickness.MM_250,
            2.5,
            new ArrayList<>()
        );

        return testWall(spec, "wall_angled.ifc");
    }

    /**
     * Test 4: Horizontal pipe (X-aligned).
     */
    private boolean testHorizontalPipe() {
        System.out.println("Test 4: Horizontal pipe (X-aligned)");

        PipeSpec spec = new PipeSpec(
            new Point3D(0, 0, 1),
            new Point3D(5, 0, 1),
            0.050,  // 50mm diameter
            Discipline.FP,
            PipeSpec.TerminationType.OPEN,
            PipeSpec.TerminationType.OPEN
        );

        return testPipe(spec, "pipe_horizontal.ifc");
    }

    /**
     * Test 5: Vertical pipe (Z-aligned).
     */
    private boolean testVerticalPipe() {
        System.out.println("Test 5: Vertical pipe (Z-aligned)");

        PipeSpec spec = new PipeSpec(
            new Point3D(2, 2, 0),
            new Point3D(2, 2, 3),
            0.100,  // 100mm diameter
            Discipline.SP,
            PipeSpec.TerminationType.OPEN,
            PipeSpec.TerminationType.OPEN
        );

        return testPipe(spec, "pipe_vertical.ifc");
    }

    /**
     * Test 6: Diagonal pipe.
     */
    private boolean testDiagonalPipe() {
        System.out.println("Test 6: Diagonal pipe");

        PipeSpec spec = new PipeSpec(
            new Point3D(0, 0, 0),
            new Point3D(3, 4, 2),
            0.075,  // 75mm diameter
            Discipline.CW,
            PipeSpec.TerminationType.OPEN,
            PipeSpec.TerminationType.OPEN
        );

        return testPipe(spec, "pipe_diagonal.ifc");
    }

    /**
     * Test a wall round-trip.
     */
    private boolean testWall(WallSpec spec, String filename) {
        String outputPath = OUTPUT_DIR + "/" + filename;

        // Calculate expected bbox
        BoundingBox expected = spec.toBoundingBox();
        System.out.println("  Expected bbox: " + formatBbox(expected));

        // Export to IFC
        boolean exported = exporter.exportWall(spec, outputPath);
        if (!exported) {
            System.out.println("  [FAIL] Export failed");
            return false;
        }

        // Reimport and extract bbox
        List<IFCExporter.ReimportedElement> elements = exporter.reimport(outputPath);
        if (elements.isEmpty()) {
            System.out.println("  [FAIL] No elements reimported");
            return false;
        }

        // Find wall element
        IFCExporter.ReimportedElement reimported = null;
        for (IFCExporter.ReimportedElement e : elements) {
            if (e.type().equals("IfcWall")) {
                reimported = e;
                break;
            }
        }

        if (reimported == null) {
            System.out.println("  [FAIL] No IfcWall found in reimport");
            return false;
        }

        System.out.println("  Reimported bbox: " + formatBbox(reimported.bbox()));

        // Compare bboxes
        boolean match = bboxMatch(expected, reimported.bbox());
        if (match) {
            System.out.println("  [PASS] Bbox match within tolerance");
        } else {
            System.out.println("  [FAIL] Bbox mismatch");
            printBboxDiff(expected, reimported.bbox());
        }

        return match;
    }

    /**
     * Test a pipe round-trip.
     */
    private boolean testPipe(PipeSpec spec, String filename) {
        String outputPath = OUTPUT_DIR + "/" + filename;

        // Calculate expected bbox
        BoundingBox expected = spec.toBoundingBox();
        System.out.println("  Expected bbox: " + formatBbox(expected));

        // Export to IFC
        boolean exported = exporter.exportPipe(spec, outputPath);
        if (!exported) {
            System.out.println("  [FAIL] Export failed");
            return false;
        }

        // Reimport and extract bbox
        List<IFCExporter.ReimportedElement> elements = exporter.reimport(outputPath);
        if (elements.isEmpty()) {
            System.out.println("  [FAIL] No elements reimported");
            return false;
        }

        // Find pipe element
        IFCExporter.ReimportedElement reimported = null;
        for (IFCExporter.ReimportedElement e : elements) {
            if (e.type().equals("IfcPipeSegment")) {
                reimported = e;
                break;
            }
        }

        if (reimported == null) {
            System.out.println("  [FAIL] No IfcPipeSegment found in reimport");
            return false;
        }

        System.out.println("  Reimported bbox: " + formatBbox(reimported.bbox()));

        // Compare bboxes
        boolean match = bboxMatch(expected, reimported.bbox());
        if (match) {
            System.out.println("  [PASS] Bbox match within tolerance");
        } else {
            System.out.println("  [FAIL] Bbox mismatch");
            printBboxDiff(expected, reimported.bbox());
        }

        return match;
    }

    /**
     * Check if two bboxes match within tolerance.
     */
    private boolean bboxMatch(BoundingBox a, BoundingBox b) {
        return Math.abs(a.minX() - b.minX()) <= TOLERANCE
            && Math.abs(a.maxX() - b.maxX()) <= TOLERANCE
            && Math.abs(a.minY() - b.minY()) <= TOLERANCE
            && Math.abs(a.maxY() - b.maxY()) <= TOLERANCE
            && Math.abs(a.minZ() - b.minZ()) <= TOLERANCE
            && Math.abs(a.maxZ() - b.maxZ()) <= TOLERANCE;
    }

    /**
     * Format bbox for display.
     */
    private String formatBbox(BoundingBox b) {
        return String.format("[%.3f,%.3f,%.3f]-[%.3f,%.3f,%.3f]",
            b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /**
     * Print bbox differences.
     */
    private void printBboxDiff(BoundingBox expected, BoundingBox actual) {
        System.out.println(String.format("    minX: expected %.3f, got %.3f, diff %.3f",
            expected.minX(), actual.minX(), actual.minX() - expected.minX()));
        System.out.println(String.format("    maxX: expected %.3f, got %.3f, diff %.3f",
            expected.maxX(), actual.maxX(), actual.maxX() - expected.maxX()));
        System.out.println(String.format("    minY: expected %.3f, got %.3f, diff %.3f",
            expected.minY(), actual.minY(), actual.minY() - expected.minY()));
        System.out.println(String.format("    maxY: expected %.3f, got %.3f, diff %.3f",
            expected.maxY(), actual.maxY(), actual.maxY() - expected.maxY()));
        System.out.println(String.format("    minZ: expected %.3f, got %.3f, diff %.3f",
            expected.minZ(), actual.minZ(), actual.minZ() - expected.minZ()));
        System.out.println(String.format("    maxZ: expected %.3f, got %.3f, diff %.3f",
            expected.maxZ(), actual.maxZ(), actual.maxZ() - expected.maxZ()));
    }
}
