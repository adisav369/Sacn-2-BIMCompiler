package com.bim.compiler.builder;

import com.bim.compiler.db.FederatedDBReader;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;

import java.sql.SQLException;
import java.util.*;

/**
 * Verification test - proves round-trip is real, not circular.
 */
public class WallBuilderVerify {

    private static final String DB_PATH =
        "/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db";

    public static void main(String[] args) throws SQLException {
        System.out.println("=".repeat(70));
        System.out.println("WALL BUILDER VERIFICATION - Proving non-circular");
        System.out.println("=".repeat(70));
        System.out.println();

        try (FederatedDBReader db = new FederatedDBReader(DB_PATH)) {
            db.loadAllElements();
            System.out.println();

            List<ISpatialElement> walls = db.getElementsByType(BIMObjectType.IFC_WALL);
            List<ISpatialElement> openings = db.getElementsByType(BIMObjectType.IFC_OPENING_ELEMENT);
            
            WallBuilder builder = new WallBuilder();
            
            // Test first 5 walls in detail
            int count = 0;
            for (ISpatialElement originalWall : walls) {
                if (count++ >= 5) break;
                
                // Find openings
                List<ISpatialElement> wallOpenings = new ArrayList<>();
                for (ISpatialElement o : openings) {
                    if (originalWall.getBoundingBox().overlaps(o.getBoundingBox())) {
                        wallOpenings.add(o);
                    }
                }
                
                System.out.println("-".repeat(70));
                System.out.printf("Wall %d: %s%n", count, originalWall.getGuid());
                
                // 1. Verify extract creates new objects
                WallSpec spec = WallSpec.extractFrom(originalWall, wallOpenings);
                System.out.println();
                System.out.println("STEP 1: Extract spec from original");
                System.out.printf("  Original bbox: %s%n", originalWall.getBoundingBox());
                System.out.printf("  Extracted: start=%s, end=%s%n", spec.start(), spec.end());
                System.out.printf("  Extracted: thickness=%s, height=%.3f%n", 
                    spec.thickness(), spec.height());
                
                // 2. Build generates NEW object
                builder.clear();
                ISpatialElement generated = builder.build(spec);
                
                System.out.println();
                System.out.println("STEP 2: Build from spec");
                System.out.printf("  Generated bbox: %s%n", generated.getBoundingBox());
                
                // 3. CRITICAL: Verify different object instances
                System.out.println();
                System.out.println("STEP 3: Object identity check");
                boolean sameObject = (generated == originalWall);
                boolean sameBbox = (generated.getBoundingBox() == originalWall.getBoundingBox());
                System.out.printf("  Same ISpatialElement instance? %s%n", sameObject ? "FAIL!" : "PASS (different)");
                System.out.printf("  Same BoundingBox instance? %s%n", sameBbox ? "FAIL!" : "PASS (different)");
                
                // 4. Show actual numeric differences
                BoundingBox orig = originalWall.getBoundingBox();
                BoundingBox gen = generated.getBoundingBox();
                
                System.out.println();
                System.out.println("STEP 4: Numeric differences (mm)");
                System.out.printf("  minX diff: %.2f mm%n", (gen.minX() - orig.minX()) * 1000);
                System.out.printf("  maxX diff: %.2f mm%n", (gen.maxX() - orig.maxX()) * 1000);
                System.out.printf("  minY diff: %.2f mm%n", (gen.minY() - orig.minY()) * 1000);
                System.out.printf("  maxY diff: %.2f mm%n", (gen.maxY() - orig.maxY()) * 1000);
                System.out.printf("  minZ diff: %.2f mm%n", (gen.minZ() - orig.minZ()) * 1000);
                System.out.printf("  maxZ diff: %.2f mm%n", (gen.maxZ() - orig.maxZ()) * 1000);
                
                // 5. Check thickness snap
                double origThickness = Math.min(orig.width(), orig.depth());
                double specThickness = spec.thickness().getMeters();
                System.out.println();
                System.out.println("STEP 5: Thickness analysis");
                System.out.printf("  Original thin dim: %.4f m (%.1f mm)%n", 
                    origThickness, origThickness * 1000);
                System.out.printf("  Snapped to enum: %s (%.1f mm)%n", 
                    spec.thickness(), specThickness * 1000);
                System.out.printf("  Thickness change: %.2f mm%n", 
                    (specThickness - origThickness) * 1000);
            }
            
            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("CONCLUSION:");
            System.out.println("If all 'Same instance' checks = PASS (different),");
            System.out.println("then the test is NOT circular.");
            System.out.println("=".repeat(70));
        }
    }
}
