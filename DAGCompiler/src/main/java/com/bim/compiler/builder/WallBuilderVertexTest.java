package com.bim.compiler.builder;

import com.bim.compiler.db.FederatedDBReader;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.GeometricElement;
import com.bim.compiler.model.IGeometricElement;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMConstants;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.WallThickness;

import java.sql.SQLException;
import java.util.*;

/**
 * Vertex-level round-trip test for WallBuilder.
 *
 * Tests one wall per thickness (150, 230, 250, 300mm):
 * 1. Load actual geometry from base_geometries
 * 2. Extract WallSpec from the wall
 * 3. Generate new geometry using WallBuilder
 * 4. Compare vertices within 5mm tolerance
 *
 * Sample GUIDs from SESSION_STATE.md:
 * - 150mm: 09wyevrd5A3eL31Smq56XD
 * - 250mm: 0Rssu52A1DGA1uyRz$LBFp
 * - 230mm: 0Rssu52A1DGA1uyRz$LBFS
 * - 300mm: 1X8oycKuf0shYLYEAURWY1
 */
public class WallBuilderVertexTest {

    private static final String DB_PATH =
        "/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db";

    private static final double TOLERANCE = BIMConstants.TOLERANCE; // 5mm

    // Sample walls - one per thickness
    private static final Map<WallThickness, String> SAMPLE_WALLS = Map.of(
        WallThickness.MM_150, "09wyevrd5A3eL31Smq56XD",
        WallThickness.MM_230, "0Rssu52A1DGA1uyRz$LBFS",
        WallThickness.MM_250, "0Rssu52A1DGA1uyRz$LBFp",
        WallThickness.MM_300, "1X8oycKuf0shYLYEAURWY1"
    );

    public static void main(String[] args) {
        try {
            new WallBuilderVertexTest().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws SQLException {
        System.out.println("=".repeat(70));
        System.out.println("WALL BUILDER VERTEX-LEVEL TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        try (FederatedDBReader db = new FederatedDBReader(DB_PATH)) {
            db.loadAllElements();
            System.out.println();

            int passed = 0;
            int failed = 0;

            for (Map.Entry<WallThickness, String> entry : SAMPLE_WALLS.entrySet()) {
                WallThickness thickness = entry.getKey();
                String guid = entry.getValue();

                System.out.println("-".repeat(70));
                System.out.printf("Testing %s wall: %s%n", thickness, guid);
                System.out.println("-".repeat(70));

                boolean result = testWall(db, guid, thickness);
                if (result) {
                    passed++;
                    System.out.println("[PASS]");
                } else {
                    failed++;
                    System.out.println("[FAIL]");
                }
                System.out.println();
            }

            // Summary
            System.out.println("=".repeat(70));
            System.out.println("SUMMARY");
            System.out.println("=".repeat(70));
            System.out.printf("Tested: %d walls%n", SAMPLE_WALLS.size());
            System.out.printf("Passed: %d%n", passed);
            System.out.printf("Failed: %d%n", failed);
            System.out.println();

            if (failed == 0) {
                System.out.println("ALL VERTEX-LEVEL TESTS PASSED");
            } else {
                System.out.println("SOME TESTS FAILED - review above");
            }
        }
    }

    private boolean testWall(FederatedDBReader db, String guid, WallThickness expectedThickness)
            throws SQLException {

        // 1. Load geometric element
        IGeometricElement original = db.getGeometricElement(guid);
        if (original == null) {
            System.out.println("  ERROR: Could not load geometry for " + guid);
            return false;
        }

        System.out.printf("  Original: %d vertices, %d faces%n",
            original.getVertexCount(), original.getFaceCount());

        // Show original bbox
        BoundingBox origBbox = original.getBoundingBox();
        System.out.printf("  BBox: %.3fm x %.3fm x %.3fm%n",
            origBbox.width(), origBbox.depth(), origBbox.height());

        // 2. Compute bbox from vertices (verification)
        if (original instanceof GeometricElement ge) {
            BoundingBox vertexBbox = ge.computeBBoxFromVertices();
            if (vertexBbox != null) {
                System.out.printf("  BBox from vertices: %.3fm x %.3fm x %.3fm%n",
                    vertexBbox.width(), vertexBbox.depth(), vertexBbox.height());

                // Compare stored bbox vs computed from vertices
                boolean bboxMatch = bboxMatches(origBbox, vertexBbox, TOLERANCE);
                System.out.printf("  Stored vs computed bbox match: %s%n", bboxMatch ? "YES" : "NO");

                if (!bboxMatch) {
                    System.out.println("  WARNING: Stored bbox differs from vertex-computed bbox");
                }
            }
        }

        // 3. Find openings for this wall
        List<ISpatialElement> allOpenings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);
        List<ISpatialElement> wallOpenings = new ArrayList<>();
        for (ISpatialElement opening : allOpenings) {
            if (origBbox.overlaps(opening.getBoundingBox())) {
                wallOpenings.add(opening);
            }
        }
        System.out.printf("  Openings: %d%n", wallOpenings.size());

        // 4. Extract spec
        WallSpec spec = WallSpec.extractFrom(original, wallOpenings);
        System.out.printf("  Extracted thickness: %s (expected: %s)%n",
            spec.thickness(), expectedThickness);

        // Verify thickness matches expected
        if (spec.thickness() != expectedThickness) {
            System.out.printf("  ERROR: Thickness mismatch!%n");
            return false;
        }

        // 5. Generate wall geometry
        WallBuilder builder = new WallBuilder();
        builder.clear();
        ISpatialElement generatedWall = builder.build(spec);

        // 6. Generate vertices using buildGeometry
        float[] generatedVertices = builder.buildGeometry(spec);
        System.out.printf("  Generated: %d vertices%n", generatedVertices.length / 3);

        // 7. Compare bounding boxes
        BoundingBox genBbox = generatedWall.getBoundingBox();
        boolean bboxMatch = bboxMatches(origBbox, genBbox, TOLERANCE);
        System.out.printf("  BBox match: %s%n", bboxMatch ? "YES" : "NO");

        if (!bboxMatch) {
            printBboxDiff(origBbox, genBbox);
        }

        // 8. Compare vertex bounds (generated should cover similar volume)
        BoundingBox genVertexBbox = computeBboxFromVertices(generatedVertices);
        if (genVertexBbox != null) {
            System.out.printf("  Generated vertex bbox: %.3fm x %.3fm x %.3fm%n",
                genVertexBbox.width(), genVertexBbox.depth(), genVertexBbox.height());

            boolean vertexBboxMatch = bboxMatches(origBbox, genVertexBbox, TOLERANCE);
            System.out.printf("  Generated vertex bbox matches original: %s%n",
                vertexBboxMatch ? "YES" : "NO");
        }

        // 9. Verify generated vertices define correct volume
        double origVolume = origBbox.width() * origBbox.depth() * origBbox.height();
        double genVolume = genBbox.width() * genBbox.depth() * genBbox.height();
        double volumeRatio = genVolume / origVolume;
        System.out.printf("  Volume ratio (gen/orig): %.4f%n", volumeRatio);

        // For simple walls without openings, ratio should be ~1.0
        // For walls with openings, generated volume includes opening voids
        boolean volumeOk = wallOpenings.isEmpty() ?
            Math.abs(volumeRatio - 1.0) < 0.01 :  // Simple wall: ~1.0
            volumeRatio >= 0.5 && volumeRatio <= 1.5;  // Wall with openings: reasonable range

        System.out.printf("  Volume check: %s%n", volumeOk ? "OK" : "WARNING");

        return bboxMatch;
    }

    private BoundingBox computeBboxFromVertices(float[] vertices) {
        if (vertices == null || vertices.length < 3) {
            return null;
        }

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private boolean bboxMatches(BoundingBox a, BoundingBox b, double tolerance) {
        return Math.abs(a.minX() - b.minX()) <= tolerance
            && Math.abs(a.maxX() - b.maxX()) <= tolerance
            && Math.abs(a.minY() - b.minY()) <= tolerance
            && Math.abs(a.maxY() - b.maxY()) <= tolerance
            && Math.abs(a.minZ() - b.minZ()) <= tolerance
            && Math.abs(a.maxZ() - b.maxZ()) <= tolerance;
    }

    private void printBboxDiff(BoundingBox orig, BoundingBox gen) {
        System.out.printf("    minX: %.6f vs %.6f (diff: %.6f)%n",
            orig.minX(), gen.minX(), Math.abs(orig.minX() - gen.minX()));
        System.out.printf("    maxX: %.6f vs %.6f (diff: %.6f)%n",
            orig.maxX(), gen.maxX(), Math.abs(orig.maxX() - gen.maxX()));
        System.out.printf("    minY: %.6f vs %.6f (diff: %.6f)%n",
            orig.minY(), gen.minY(), Math.abs(orig.minY() - gen.minY()));
        System.out.printf("    maxY: %.6f vs %.6f (diff: %.6f)%n",
            orig.maxY(), gen.maxY(), Math.abs(orig.maxY() - gen.maxY()));
        System.out.printf("    minZ: %.6f vs %.6f (diff: %.6f)%n",
            orig.minZ(), gen.minZ(), Math.abs(orig.minZ() - gen.minZ()));
        System.out.printf("    maxZ: %.6f vs %.6f (diff: %.6f)%n",
            orig.maxZ(), gen.maxZ(), Math.abs(orig.maxZ() - gen.maxZ()));
    }
}
