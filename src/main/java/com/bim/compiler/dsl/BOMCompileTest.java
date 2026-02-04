package com.bim.compiler.dsl;

import java.util.*;

/**
 * Test the BOM compilation path:
 * Manifest → PreCompiler → LayoutResolver → BuildingSpec
 */
public class BOMCompileTest {
    public static void main(String[] args) {
        System.out.println("=== BOM Compile Test ===\n");

        // Test MEP profiles first
        testMEPProfiles();

        // Create manifest for 2-storey house
        PreCompiler.Manifest manifest = new PreCompiler.Manifest(
            "TestHouse",
            "LANDED_2S",
            null,  // Use default floors
            List.of(
                PreCompiler.room("living", "LIVING"),
                PreCompiler.room("kitchen", "KITCHEN"),
                PreCompiler.room("BATHROOM"),
                PreCompiler.room("BEDROOM", 2),
                PreCompiler.room("master", "MASTER_BEDROOM", "Upper")
            ),
            Map.of()
        );

        System.out.println("\n=== BOM Compilation ===\n");
        System.out.println("Manifest: " + manifest.buildingName());
        System.out.println("Template: " + manifest.templateId());
        System.out.println("Rooms: " + manifest.rooms().size());

        // Compile via BOM path
        try {
            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compileFromManifest(manifest, null);

            System.out.println("\n=== Result ===");
            System.out.println("Building: " + spec.name());
            System.out.println("Storeys: " + spec.storeys().size());

            for (var storey : spec.storeys()) {
                System.out.printf("\n%s (Z=%.1f):%n", storey.name(), storey.baseZ());
                System.out.printf("  Rooms: %d%n", storey.rooms().size());
                System.out.printf("  Walls: %d%n", storey.walls().size());
                System.out.printf("  Doors: %d%n", storey.doors().size());
                System.out.printf("  Windows: %d%n", storey.windows().size());
                System.out.printf("  Lights: %d%n", storey.lights().size());
                System.out.printf("  Fixtures: %d%n", storey.fixtures().size());
            }

            System.out.println("\n✓ BOM compilation path works!");

        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }

        // Cleanup
        BuildingBOM.close();
        AutoFitter.close();
        MEPBomAD.close();
    }

    /**
     * Test MEP profile integration - BUDGET/STANDARD/PREMIUM
     */
    private static void testMEPProfiles() {
        System.out.println("=== MEP Profile Test ===\n");

        String[] profiles = {"BUDGET", "STANDARD", "PREMIUM"};

        // Test BATHROOM
        System.out.println("BATHROOM (7.5m²):");
        for (String profile : profiles) {
            AutoFitter.FittedSpace fitted = AutoFitter.fitSpaceWithProfile(
                "BATHROOM", 3.0, 2.5, 2.7, profile);
            System.out.printf("  %s: %d products%n", profile, fitted.placements().size());
        }

        // Test BEDROOM with different profiles
        System.out.println("\nBEDROOM (12m²):");
        for (String profile : profiles) {
            AutoFitter.FittedSpace fitted = AutoFitter.fitSpaceWithProfile(
                "BEDROOM", 4.0, 3.0, 2.7, profile);

            System.out.printf("\n  %s:%n", profile);
            for (AutoFitter.ProductPlacement p : fitted.placements()) {
                System.out.printf("    %s at (%.1f,%.1f,%.1f) on %s%n",
                    p.productId(), p.x(), p.y(), p.z(), p.hostFace());
            }
        }

        // Conduit budgets
        System.out.println("\nConduit budgets:");
        for (String space : new String[]{"BATHROOM", "BEDROOM", "KITCHEN"}) {
            System.out.printf("  %s: BUDGET=%.0fm, STANDARD=%.0fm, PREMIUM=%.0fm%n",
                space,
                MEPBomAD.getConduitBudget(space, "BUDGET"),
                MEPBomAD.getConduitBudget(space, "STANDARD"),
                MEPBomAD.getConduitBudget(space, "PREMIUM"));
        }
    }
}
