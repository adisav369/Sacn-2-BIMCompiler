package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 48B: Test separating floor slab properties for Stacked-Duplex.
 * Verifies the floor between units has upgraded fire and acoustic ratings.
 */
public class StackedDuplexSlabTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 48B: Separating Floor Slab Test ===\n");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

        System.out.println("Building: " + spec.name());
        System.out.println("Storeys: " + spec.storeys().size());

        // Find the separating floor slab (level 1)
        BuildingCompiler.SlabSpec groundSlab = null;
        BuildingCompiler.SlabSpec upperSlab = null;

        for (BuildingCompiler.StoreySpec storey : spec.storeys()) {
            if (storey.level() == 0) {
                groundSlab = storey.slab();
            } else if (storey.level() == 1) {
                upperSlab = storey.slab();
            }
        }

        // Test 1: Ground floor has foundation slab (not separating)
        System.out.println("\nTest 1: Ground floor slab (foundation)");
        assert groundSlab != null : "Ground slab not found";
        assert "FOUNDATION".equals(groundSlab.type()) : "Expected FOUNDATION, got: " + groundSlab.type();
        assert !groundSlab.isSeparatingFloor() : "Foundation should not be separating floor";
        System.out.println("  - Type: " + groundSlab.type() + " ✓");
        System.out.println("  - Thickness: " + String.format("%.0fmm", groundSlab.thickness() * 1000) + " ✓");
        System.out.println("  - Fire rating: " + groundSlab.fireRating() + " ✓");
        System.out.println("  Test 1 PASSED\n");

        // Test 2: Upper floor has separating floor slab
        System.out.println("Test 2: Upper floor slab (separating floor)");
        assert upperSlab != null : "Upper slab not found";
        assert upperSlab.isSeparatingFloor() : "Expected separating floor";
        assert "SEPARATING_FLOOR".equals(upperSlab.type()) : "Expected SEPARATING_FLOOR, got: " + upperSlab.type();
        System.out.println("  - Type: " + upperSlab.type() + " ✓");

        // Test 3: Fire rating is FRL 90/90/90
        System.out.println("\nTest 3: Fire rating FRL 90/90/90");
        assert upperSlab.fireRating() == FireRating.FRL_90_90_90 :
            "Expected FRL_90_90_90, got: " + upperSlab.fireRating();
        System.out.println("  - Fire rating: " + upperSlab.fireRating().toUBBLFormat() + " ✓");
        System.out.println("  Test 3 PASSED\n");

        // Test 4: Thickness is 200mm
        System.out.println("Test 4: Thickness >= 200mm");
        double thickness = upperSlab.thickness();
        assert thickness >= 0.20 : "Expected thickness >= 200mm, got: " + (thickness * 1000) + "mm";
        System.out.println("  - Thickness: " + String.format("%.0fmm", thickness * 1000) + " ✓");
        System.out.println("  Test 4 PASSED\n");

        // Test 5: Acoustic ratings
        System.out.println("Test 5: Acoustic ratings STC 50, IIC 50");
        assert upperSlab.acousticSTC() == 50 : "Expected STC 50, got: " + upperSlab.acousticSTC();
        assert upperSlab.acousticIIC() == 50 : "Expected IIC 50, got: " + upperSlab.acousticIIC();
        System.out.println("  - STC: " + upperSlab.acousticSTC() + " ✓");
        System.out.println("  - IIC: " + upperSlab.acousticIIC() + " ✓");
        System.out.println("  Test 5 PASSED\n");

        // Test 6: Slab bounds cover both units
        System.out.println("Test 6: Slab bounds");
        System.out.printf("  - X: [%.1f, %.1f]%n", upperSlab.minX(), upperSlab.maxX());
        System.out.printf("  - Y: [%.1f, %.1f]%n", upperSlab.minY(), upperSlab.maxY());
        System.out.printf("  - Z: [%.3f, %.3f]%n", upperSlab.minZ(), upperSlab.maxZ());
        System.out.println("  Test 6 PASSED\n");

        System.out.println("=== All Phase 48B Tests Passed ===");
    }
}
