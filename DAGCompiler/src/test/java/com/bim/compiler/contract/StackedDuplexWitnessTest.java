package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingCompiler;
import com.bim.compiler.dsl.BuildingDefinition;
import com.bim.compiler.dsl.BuildingParser;
import com.bim.compiler.dsl.BuildingSpecs.BuildingSpec;
import com.bim.compiler.dsl.WitnessGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 48C: Witness generation for Stacked-Duplex.
 * Verifies SEPARATING_FLOORS_VALID claim is present and PROVEN,
 * with correct fire rating, acoustic ratings, and thickness data.
 */
public class StackedDuplexWitnessTest {

    private static String witnessJson;

    @BeforeAll
    static void compileAndGenerateWitness() throws Exception {
        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingSpec spec = BuildingCompiler.compile(def);

        Path witnessPath = Path.of("output/stacked_duplex_witness.json");
        boolean written = WitnessGenerator.generateWitness(spec, def, witnessPath);
        assertTrue(written, "Failed to write witness file");

        witnessJson = Files.readString(witnessPath);
    }

    @Test
    @DisplayName("SEPARATING_FLOORS_VALID claim exists in witness")
    void separatingFloorsClaimExists() {
        assertTrue(witnessJson.contains("\"SEPARATING_FLOORS_VALID\""),
                "Witness must contain SEPARATING_FLOORS_VALID claim");
    }

    @Test
    @DisplayName("SEPARATING_FLOORS_VALID claim status is PROVEN")
    void separatingFloorsClaimIsProven() {
        assertTrue(witnessJson.contains("\"SEPARATING_FLOORS_VALID\""),
                "Witness must contain SEPARATING_FLOORS_VALID claim");
        String afterClaim = witnessJson.substring(
                witnessJson.indexOf("\"SEPARATING_FLOORS_VALID\""));
        assertTrue(afterClaim.contains("\"status\": \"PROVEN\""),
                "SEPARATING_FLOORS_VALID must have status PROVEN");
    }

    @Test
    @DisplayName("Witness contains separating_floors data")
    void hasSeparatingFloorsData() {
        assertTrue(witnessJson.contains("\"separating_floors\""),
                "Witness must contain separating_floors data");
    }

    @Test
    @DisplayName("Fire rating is 90/90/90")
    void fireRatingCompliance() {
        assertTrue(witnessJson.contains("\"fire_rating\": \"90/90/90\""),
                "Witness must contain fire_rating 90/90/90");
    }

    @Test
    @DisplayName("Acoustic ratings STC 50 and IIC 50")
    void acousticRatings() {
        assertTrue(witnessJson.contains("\"STC\": 50"),
                "Witness must contain STC 50");
        assertTrue(witnessJson.contains("\"IIC\": 50"),
                "Witness must contain IIC 50");
    }

    @Test
    @DisplayName("Separating floor thickness is 200mm")
    void thickness200mm() {
        assertTrue(witnessJson.contains("\"thickness_mm\": 200"),
                "Witness must contain thickness_mm 200");
    }

    @Test
    @DisplayName("Witness summary contains proven count")
    void witnessSummaryHasProvenCount() {
        assertTrue(witnessJson.contains("\"proven\":"),
                "Witness summary must contain a proven count");
        int provenIdx = witnessJson.indexOf("\"proven\":");
        assertTrue(provenIdx > 0, "proven key must be present in witness JSON");
        // Extract the numeric value after "proven":
        String afterProven = witnessJson.substring(provenIdx + "\"proven\":".length()).trim();
        String numStr = afterProven.replaceAll("[^0-9].*", "");
        assertFalse(numStr.isEmpty(), "proven count must be a number");
        int provenCount = Integer.parseInt(numStr);
        assertTrue(provenCount > 0, "proven count must be > 0, was: " + provenCount);
    }
}
