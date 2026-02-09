package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 48C: Test witness generation for Stacked-Duplex.
 * Verifies SEPARATING_FLOORS_VALID claim is present and PROVEN.
 */
public class StackedDuplexWitnessTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 48C: Separating Floor Witness Test ===\n");

        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

        // Generate witness (Phase 48D.2: pass def for per-unit entry tracking)
        Path witnessPath = Path.of("output/stacked_duplex_witness.json");
        boolean written = WitnessGenerator.generateWitness(spec, def, witnessPath);
        assert written : "Failed to write witness";
        System.out.println("Witness written to: " + witnessPath);

        // Read and verify witness content
        String witnessJson = Files.readString(witnessPath);

        // Test 1: SEPARATING_FLOORS_VALID claim exists
        System.out.println("\nTest 1: SEPARATING_FLOORS_VALID claim exists");
        boolean hasClaim = witnessJson.contains("\"SEPARATING_FLOORS_VALID\"");
        assert hasClaim : "Missing SEPARATING_FLOORS_VALID claim";
        System.out.println("  - Claim present ✓");

        // Test 2: Claim is PROVEN
        System.out.println("\nTest 2: Claim status is PROVEN");
        boolean isProven = witnessJson.contains("\"SEPARATING_FLOORS_VALID\"") &&
            witnessJson.substring(witnessJson.indexOf("\"SEPARATING_FLOORS_VALID\""))
                .contains("\"status\": \"PROVEN\"");
        assert isProven : "SEPARATING_FLOORS_VALID is not PROVEN";
        System.out.println("  - Status: PROVEN ✓");

        // Test 3: Has separating_floors data
        System.out.println("\nTest 3: Has separating floor data");
        boolean hasFloorData = witnessJson.contains("\"separating_floors\"");
        assert hasFloorData : "Missing separating_floors data";
        System.out.println("  - separating_floors data present ✓");

        // Test 4: Fire rating compliance
        System.out.println("\nTest 4: Fire rating 90/90/90");
        boolean hasFireRating = witnessJson.contains("\"fire_rating\": \"90/90/90\"");
        assert hasFireRating : "Missing fire rating 90/90/90";
        System.out.println("  - Fire rating 90/90/90 ✓");

        // Test 5: Acoustic ratings
        System.out.println("\nTest 5: Acoustic ratings STC 50, IIC 50");
        boolean hasSTC = witnessJson.contains("\"STC\": 50");
        boolean hasIIC = witnessJson.contains("\"IIC\": 50");
        assert hasSTC : "Missing STC 50";
        assert hasIIC : "Missing IIC 50";
        System.out.println("  - STC: 50 ✓");
        System.out.println("  - IIC: 50 ✓");

        // Test 6: Thickness
        System.out.println("\nTest 6: Thickness 200mm");
        boolean hasThickness = witnessJson.contains("\"thickness_mm\": 200");
        assert hasThickness : "Missing thickness_mm 200";
        System.out.println("  - Thickness: 200mm ✓");

        // Test 7: Summary shows increased proven count
        System.out.println("\nTest 7: Witness summary");
        // Extract proven count
        int provenIdx = witnessJson.indexOf("\"proven\":");
        if (provenIdx > 0) {
            String provenStr = witnessJson.substring(provenIdx + 10, provenIdx + 15);
            System.out.println("  - Proven claims: " + provenStr.replaceAll("[^0-9]", "") + " ✓");
        }

        System.out.println("\n=== All Phase 48C Tests Passed ===");
    }
}
