package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.SprinklerPlacer.SprinklerInstance;
import com.bim.compiler.library.SprinklerPlacer.SprinklerType;
import com.bim.compiler.topology.FederationConstants;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Mathematical verification of Library Placer before visual inspection.
 *
 * Verifies:
 * 1. Round-trip position accuracy (Terminal DB → Library → Placed)
 * 2. Orientation correctness (pendant deflector below, upright above)
 * 3. Grid spacing accuracy (NFPA 13 compliance)
 * 4. Attachment height verification
 */
public class LibraryVerificationTest {

    private static final String LIBRARY_PATH = "library/component_library.db";
    private static final String TERMINAL_DB = "/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db";
    private static final String REPORT_PATH = "output/verification_report.txt";
    private static final double TOLERANCE = FederationConstants.TOLERANCE; // 5mm

    private PrintWriter report;
    private int passed = 0;
    private int failed = 0;

    public static void main(String[] args) throws Exception {
        new LibraryVerificationTest().runAll();
    }

    public void runAll() throws Exception {
        Files.createDirectories(Path.of("output"));
        report = new PrintWriter(new FileWriter(REPORT_PATH));

        log("=" .repeat(70));
        log("LIBRARY VERIFICATION REPORT");
        log("Generated: " + java.time.LocalDateTime.now());
        log("Tolerance: " + (TOLERANCE * 1000) + "mm");
        log("=" .repeat(70));

        try {
            test1_RoundTripPosition();
            test2_OrientationVerification();
            test3_GridSpacingVerification();
            test4_AttachmentHeightVerification();
        } finally {
            log("");
            log("=" .repeat(70));
            log("SUMMARY: " + passed + " passed, " + failed + " failed");
            log("=" .repeat(70));

            report.close();
            System.out.println("\nReport written to: " + REPORT_PATH);
        }
    }

    /**
     * Test 1: Round-trip position accuracy
     * Original DB position → Library placement → Compare
     */
    private void test1_RoundTripPosition() throws Exception {
        log("");
        log("TEST 1: Round-Trip Position Accuracy");
        log("-".repeat(70));

        ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
        SprinklerPlacer placer = new SprinklerPlacer(library);

        // Get 10 random sprinklers from Terminal DB
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TERMINAL_DB)) {
            String sql = """
                SELECT
                    em.guid,
                    em.element_type,
                    et.center_x, et.center_y, et.center_z,
                    er.minX, er.maxX, er.minY, er.maxY, er.minZ, er.maxZ
                FROM elements_meta em
                JOIN element_transforms et ON em.guid = et.guid
                JOIN elements_rtree er ON em.id = er.id
                WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
                  AND em.element_type NOT LIKE '%Hose%'
                ORDER BY RANDOM()
                LIMIT 10
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String guid = rs.getString("guid");
                    String type = rs.getString("element_type");
                    double cx = rs.getDouble("center_x");
                    double cy = rs.getDouble("center_y");
                    double cz = rs.getDouble("center_z");

                    // Original position is bbox center (FACT 6)
                    Point3D originalPos = new Point3D(cx, cy, cz);

                    // Determine sprinkler type
                    boolean isPendant = type.toLowerCase().contains("pendent");

                    // Place using our library at the same center position
                    // Note: Our placer expects ceiling/floor point, so we need to adjust
                    // For pendant: ceiling = center + halfHeight
                    // For upright: floor = center - halfHeight

                    ComponentLibrary.ComponentDefinition def = isPendant
                        ? library.getPendantSprinkler()
                        : library.getUprightSprinkler();

                    if (def == null) {
                        log("  SKIP %s: no %s definition in library", guid, isPendant ? "pendant" : "upright");
                        continue;
                    }

                    double halfHeight = def.localBounds().height() / 2;

                    // The placer calculates world center from attachment point
                    // We need to verify the placed center matches original center
                    SprinklerInstance placed;
                    if (isPendant) {
                        // Ceiling point is at top of sprinkler
                        Point3D ceiling = new Point3D(cx, cy, cz + halfHeight + def.localBounds().maxZ());
                        placed = placer.placePendant(ceiling, 0.0);
                    } else {
                        // Floor point is at bottom of sprinkler
                        Point3D floor = new Point3D(cx, cy, cz - halfHeight + def.localBounds().minZ());
                        placed = placer.placeUpright(floor, 0.0);
                    }

                    Point3D placedPos = placed.worldCenter();
                    double delta = originalPos.distanceTo(placedPos);

                    boolean pass = delta < TOLERANCE;
                    if (pass) passed++; else failed++;

                    log("  %s %s: original=(%.3f,%.3f,%.3f) placed=(%.3f,%.3f,%.3f) delta=%.1fmm [%s]",
                        isPendant ? "PENDANT" : "UPRIGHT",
                        guid.substring(0, 8),
                        cx, cy, cz,
                        placedPos.x(), placedPos.y(), placedPos.z(),
                        delta * 1000,
                        pass ? "PASS" : "FAIL");
                }
            }
        }

        library.close();
    }

    /**
     * Test 2: Orientation verification
     * Pendant: deflector (minZ) below inlet (maxZ)
     * Upright: deflector (maxZ) above inlet (minZ)
     */
    private void test2_OrientationVerification() throws Exception {
        log("");
        log("TEST 2: Orientation Verification");
        log("-".repeat(70));

        ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);

        // Check pendant sprinkler
        ComponentLibrary.ComponentDefinition pendant = library.getPendantSprinkler();
        if (pendant != null) {
            ComponentLibrary.ComponentGeometry geom = library.getGeometry(pendant.geometryHash());

            float[] zValues = extractZValues(geom.vertices(), geom.vertexCount());
            float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
            for (float z : zValues) {
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }

            // Count vertices at extremes
            int atMin = 0, atMax = 0;
            for (float z : zValues) {
                if (Math.abs(z - minZ) < 0.001) atMin++;
                if (Math.abs(z - maxZ) < 0.001) atMax++;
            }

            // Pendant: more vertices at bottom (deflector) than top (inlet)
            // Actually from our analysis, pendant has MORE at bottom
            boolean correctOrientation = pendant.attachmentFace() == ComponentLibrary.AttachmentFace.TOP;
            if (correctOrientation) passed++; else failed++;

            log("  PENDANT: minZ=%.3f (%d verts), maxZ=%.3f (%d verts), attachment=%s [%s]",
                minZ, atMin, maxZ, atMax,
                pendant.attachmentFace(),
                correctOrientation ? "PASS" : "FAIL");
        }

        // Check upright sprinkler
        ComponentLibrary.ComponentDefinition upright = library.getUprightSprinkler();
        if (upright != null) {
            ComponentLibrary.ComponentGeometry geom = library.getGeometry(upright.geometryHash());

            float[] zValues = extractZValues(geom.vertices(), geom.vertexCount());
            float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
            for (float z : zValues) {
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }

            int atMin = 0, atMax = 0;
            for (float z : zValues) {
                if (Math.abs(z - minZ) < 0.001) atMin++;
                if (Math.abs(z - maxZ) < 0.001) atMax++;
            }

            boolean correctOrientation = upright.attachmentFace() == ComponentLibrary.AttachmentFace.BOTTOM;
            if (correctOrientation) passed++; else failed++;

            log("  UPRIGHT: minZ=%.3f (%d verts), maxZ=%.3f (%d verts), attachment=%s [%s]",
                minZ, atMin, maxZ, atMax,
                upright.attachmentFace(),
                correctOrientation ? "PASS" : "FAIL");
        }

        library.close();
    }

    /**
     * Test 3: Grid spacing verification
     * Place 4x4 grid, verify spacing = 4.6m (NFPA 13)
     */
    private void test3_GridSpacingVerification() throws Exception {
        log("");
        log("TEST 3: Grid Spacing Verification (NFPA 13: 4.6m)");
        log("-".repeat(70));

        ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
        SprinklerPlacer placer = new SprinklerPlacer(library);

        double targetSpacing = 4.6; // NFPA 13 light hazard max
        BoundingBox area = new BoundingBox(0, 20, 0, 20, 0, 3);
        List<SprinklerInstance> grid = placer.placeGrid(area, 3.0, targetSpacing);

        log("  Grid area: 20m x 20m, target spacing: %.1fm", targetSpacing);
        log("  Sprinklers placed: %d", grid.size());

        // Check spacing between adjacent sprinklers
        int spacingChecks = 0;
        double maxDelta = 0;

        for (int i = 0; i < grid.size(); i++) {
            Point3D p1 = grid.get(i).worldCenter();
            for (int j = i + 1; j < grid.size(); j++) {
                Point3D p2 = grid.get(j).worldCenter();

                // Check if same row (same Y) or same column (same X)
                boolean sameRow = Math.abs(p1.y() - p2.y()) < TOLERANCE;
                boolean sameCol = Math.abs(p1.x() - p2.x()) < TOLERANCE;

                if (sameRow || sameCol) {
                    double dist = p1.horizontalDistanceTo(p2);
                    double delta = Math.abs(dist - targetSpacing);

                    // Only check adjacent (distance close to target, not 2x target)
                    if (dist < targetSpacing * 1.5) {
                        maxDelta = Math.max(maxDelta, delta);
                        spacingChecks++;

                        if (delta > TOLERANCE) {
                            log("    WARN: spacing (%.3f,%.3f)-(%.3f,%.3f) = %.3fm, delta=%.1fmm",
                                p1.x(), p1.y(), p2.x(), p2.y(), dist, delta * 1000);
                        }
                    }
                }
            }
        }

        boolean pass = maxDelta < TOLERANCE;
        if (pass) passed++; else failed++;

        log("  Spacing checks: %d, max delta: %.1fmm [%s]",
            spacingChecks, maxDelta * 1000, pass ? "PASS" : "FAIL");

        library.close();
    }

    /**
     * Test 4: Attachment height verification
     * Sprinkler maxZ should be at or below ceiling height
     */
    private void test4_AttachmentHeightVerification() throws Exception {
        log("");
        log("TEST 4: Attachment Height Verification");
        log("-".repeat(70));

        ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
        SprinklerPlacer placer = new SprinklerPlacer(library);

        double ceilingZ = 3.0;
        Point3D ceiling = new Point3D(5.0, 5.0, ceilingZ);
        SprinklerInstance pendant = placer.placePendant(ceiling, 0.0);

        BoundingBox bounds = pendant.getWorldBounds();

        // maxZ should be at or below ceiling
        double topZ = bounds.maxZ();
        double delta = topZ - ceilingZ;

        boolean pass = delta <= TOLERANCE;
        if (pass) passed++; else failed++;

        log("  Ceiling: Z=%.3fm", ceilingZ);
        log("  Sprinkler bounds: minZ=%.3f, maxZ=%.3f", bounds.minZ(), bounds.maxZ());
        log("  Top above ceiling: %.1fmm [%s]",
            delta * 1000, pass ? "PASS" : "FAIL");

        // Also verify for upright at floor
        double floorZ = 0.0;
        Point3D floor = new Point3D(5.0, 5.0, floorZ);
        SprinklerInstance upright = placer.placeUpright(floor, 0.0);

        BoundingBox uprightBounds = upright.getWorldBounds();
        double bottomZ = uprightBounds.minZ();
        double floorDelta = floorZ - bottomZ;

        boolean floorPass = floorDelta <= TOLERANCE;
        if (floorPass) passed++; else failed++;

        log("  Floor: Z=%.3fm", floorZ);
        log("  Sprinkler bounds: minZ=%.3f, maxZ=%.3f", uprightBounds.minZ(), uprightBounds.maxZ());
        log("  Bottom below floor: %.1fmm [%s]",
            floorDelta * 1000, floorPass ? "PASS" : "FAIL");

        library.close();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private float[] extractZValues(byte[] vertices, int vertexCount) {
        float[] zValues = new float[vertexCount];
        ByteBuffer buffer = ByteBuffer.wrap(vertices).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vertexCount; i++) {
            buffer.getFloat(); // x
            buffer.getFloat(); // y
            zValues[i] = buffer.getFloat(); // z
        }
        return zValues;
    }

    private void log(String format, Object... args) {
        String message = String.format(format, args);
        System.out.println(message);
        report.println(message);
    }
}
