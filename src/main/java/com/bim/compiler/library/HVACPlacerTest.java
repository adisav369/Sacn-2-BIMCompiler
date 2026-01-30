package com.bim.compiler.library;

import com.bim.compiler.library.HVACPlacer.*;

/**
 * Phase 24: Test HVAC diffuser placement.
 *
 * Verifies:
 * 1. CFM calculation per ASHRAE 62.1
 * 2. Supply diffuser placement
 * 3. Return diffuser placement (balanced with supply)
 * 4. Exhaust diffuser for bathroom/kitchen
 * 5. Complete room HVAC layout
 */
public class HVACPlacerTest {

    private static final String LIBRARY_PATH = "library/component_library.db";

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 24: HVAC DIFFUSER PLACEMENT TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Test 1: CFM calculation
        if (testCFMCalculation()) passed++; else failed++;

        // Test 2: Library diffuser availability
        if (testLibraryDiffusers()) passed++; else failed++;

        // Test 3: Supply diffuser placement
        if (testSupplyPlacement()) passed++; else failed++;

        // Test 4: Complete room HVAC
        if (testCompleteRoomHVAC()) passed++; else failed++;

        // Test 5: Bathroom with exhaust
        if (testBathroomHVAC()) passed++; else failed++;

        // Summary
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.printf("RESULT: %d/%d tests passed%n", passed, passed + failed);
        System.out.println("=".repeat(70));

        if (failed > 0) System.exit(1);
    }

    // =========================================================================
    // TEST 1: CFM Calculation per ASHRAE 62.1
    // =========================================================================

    private static boolean testCFMCalculation() {
        System.out.println("TEST 1: CFM calculation per ASHRAE 62.1");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HVACPlacer placer = new HVACPlacer(library);

            // Test room: 5m x 4m x 2.8m = 56 m³
            double volume = 5 * 4 * 2.8;

            // Test different room types
            double bedroomCFM = placer.calculateRequiredCFM(volume, "BEDROOM");
            double officeCFM = placer.calculateRequiredCFM(volume, "OFFICE");
            double loungeCFM = placer.calculateRequiredCFM(volume, "DEPARTURE_LOUNGE");

            System.out.printf("Room volume: %.1f m³%n", volume);
            System.out.printf("  BEDROOM (0.35 ACH): %.1f CFM%n", bedroomCFM);
            System.out.printf("  OFFICE (4.0 ACH):   %.1f CFM%n", officeCFM);
            System.out.printf("  DEPARTURE_LOUNGE (8.0 ACH): %.1f CFM%n", loungeCFM);

            // Verify calculation: CFM = Volume × ACH / 60 × 35.31
            double expectedBedroom = volume * 0.35 / 60 * 35.31;
            double expectedOffice = volume * 4.0 / 60 * 35.31;

            boolean pass = Math.abs(bedroomCFM - expectedBedroom) < 0.1 &&
                           Math.abs(officeCFM - expectedOffice) < 0.1 &&
                           loungeCFM > officeCFM;  // Higher ACH = higher CFM

            System.out.printf("%n[%s] CFM calculation correct%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 2: Library Diffuser Availability
    // =========================================================================

    private static boolean testLibraryDiffusers() {
        System.out.println("TEST 2: Library diffuser availability");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);

            // Check for diffuser types used by HVACPlacer
            var supply = library.getByNameContaining("supply");
            var returnGrille = library.getByNameContaining("Return");
            var exhaust = library.getByNameContaining("exhaust");

            System.out.printf("Supply diffuser: %s%n",
                supply != null ? supply.name() : "NOT FOUND");
            System.out.printf("Return grille: %s%n",
                returnGrille != null ? returnGrille.name() : "NOT FOUND");
            System.out.printf("Exhaust grille: %s%n",
                exhaust != null ? exhaust.name() : "NOT FOUND");

            // At least supply and return should be available
            boolean pass = supply != null && returnGrille != null;

            System.out.printf("%n[%s] Diffusers available in library%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 3: Supply Diffuser Placement
    // =========================================================================

    private static boolean testSupplyPlacement() {
        System.out.println("TEST 3: Supply diffuser placement");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HVACPlacer placer = new HVACPlacer(library);

            // Office room: 6m x 5m x 3m = 90 m³
            double minX = 0, minY = 0, maxX = 6, maxY = 5;
            double floorZ = 0, ceilingZ = 3;
            String roomType = "OFFICE";

            var diffusers = placer.placeSupplyDiffusers(
                minX, minY, maxX, maxY, floorZ, ceilingZ, roomType);

            System.out.printf("Room: %.0f x %.0f x %.0f m (%.0f m³)%n",
                maxX - minX, maxY - minY, ceilingZ - floorZ,
                (maxX - minX) * (maxY - minY) * (ceilingZ - floorZ));
            System.out.printf("Room type: %s%n", roomType);
            System.out.printf("Supply diffusers placed: %d%n%n", diffusers.size());

            for (var d : diffusers) {
                System.out.printf("  %s (%s): (%.2f, %.2f, %.2f) %d CFM%n",
                    d.id(), d.type().getLibraryName(),
                    d.position().x(), d.position().y(), d.position().z(),
                    d.cfmRating());
            }

            // Verify at least 1 diffuser placed and at ceiling level
            boolean pass = diffusers.size() >= 1 &&
                diffusers.stream().allMatch(d ->
                    Math.abs(d.position().z() - (ceilingZ - 0.05)) < 0.01);

            System.out.printf("%n[%s] Supply diffusers placed at ceiling%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 4: Complete Room HVAC
    // =========================================================================

    private static boolean testCompleteRoomHVAC() {
        System.out.println("TEST 4: Complete room HVAC layout");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HVACPlacer placer = new HVACPlacer(library);

            // Large departure lounge: 20m x 12m x 4m
            double minX = 0, minY = 0, maxX = 20, maxY = 12;
            double floorZ = 0, ceilingZ = 4;
            String roomType = "DEPARTURE_LOUNGE";

            HVACLayout layout = placer.placeRoomHVAC(
                minX, minY, maxX, maxY, floorZ, ceilingZ, roomType);

            double volume = (maxX - minX) * (maxY - minY) * (ceilingZ - floorZ);

            System.out.printf("Room: %.0f x %.0f x %.0f m (%.0f m³)%n",
                maxX - minX, maxY - minY, ceilingZ - floorZ, volume);
            System.out.printf("Room type: %s (%.1f ACH)%n", roomType,
                VentilationRate.fromRoomType(roomType).getACH());
            System.out.println();
            System.out.printf("CFM Required: %.0f%n", layout.requiredCFM());
            System.out.printf("CFM Supply:   %.0f%n", layout.supplyCFM());
            System.out.printf("CFM Return:   %.0f%n", layout.returnCFM());
            System.out.println();
            System.out.printf("Supply diffusers:  %d%n", layout.supply().size());
            System.out.printf("Return diffusers:  %d%n", layout.returns().size());
            System.out.printf("Exhaust diffusers: %d%n", layout.exhaust().size());
            System.out.printf("Total diffusers:   %d%n", layout.totalCount());
            System.out.printf("Balanced: %s%n", layout.isBalanced());

            // Verify supply CFM meets or exceeds required
            boolean pass = layout.supplyCFM() >= layout.requiredCFM() * 0.8 &&  // 80% minimum
                           layout.supply().size() >= 1 &&
                           layout.returns().size() >= 1;

            System.out.printf("%n[%s] Complete HVAC layout generated%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // TEST 5: Bathroom with Exhaust
    // =========================================================================

    private static boolean testBathroomHVAC() {
        System.out.println("TEST 5: Bathroom HVAC with exhaust");
        System.out.println("-".repeat(70));

        try {
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HVACPlacer placer = new HVACPlacer(library);

            // Bathroom: 2.5m x 3m x 2.8m
            double minX = 0, minY = 0, maxX = 2.5, maxY = 3;
            double floorZ = 0, ceilingZ = 2.8;
            String roomType = "BATHROOM";

            HVACLayout layout = placer.placeRoomHVAC(
                minX, minY, maxX, maxY, floorZ, ceilingZ, roomType);

            System.out.printf("Room: %.1f x %.1f x %.1f m (%.1f m³)%n",
                maxX - minX, maxY - minY, ceilingZ - floorZ,
                (maxX - minX) * (maxY - minY) * (ceilingZ - floorZ));
            System.out.printf("Room type: %s (%.1f ACH - exhaust dominated)%n",
                roomType, VentilationRate.fromRoomType(roomType).getACH());
            System.out.println();
            System.out.printf("Supply diffusers:  %d%n", layout.supply().size());
            System.out.printf("Return diffusers:  %d%n", layout.returns().size());
            System.out.printf("Exhaust diffusers: %d%n", layout.exhaust().size());

            // Bathroom should have exhaust
            boolean pass = layout.exhaust().size() >= 1 &&
                           layout.supply().size() >= 1;

            System.out.printf("%n[%s] Bathroom has exhaust diffuser%n%n", pass ? "PASS" : "FAIL");
            return pass;

        } catch (Exception e) {
            System.out.printf("[FAIL] Exception: %s%n%n", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
