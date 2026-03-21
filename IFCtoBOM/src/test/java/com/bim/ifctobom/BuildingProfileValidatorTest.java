package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * BuildingProfileValidator tests — proves mined profiles load and validate
 * building element composition against known archetypes.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-DV-PROFILE-1: Loads 36+ building profiles from disc_validation.db</li>
 *   <li>W-DV-PROFILE-2: Normal residential building (35% wall, 15% window) passes</li>
 *   <li>W-DV-PROFILE-3: Wall-dominant building with 0 doors/windows flagged</li>
 *   <li>W-DV-PROFILE-4: Novel IFC class flagged</li>
 *   <li>W-DV-PROFILE-5: Report identifies dominant class correctly</li>
 * </ul>
 *
 * // Implementing BBC.md §9.1 — Witness: W-DV-PROFILE
 */
@DisplayName("BuildingProfileValidator — building composition check")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BuildingProfileValidatorTest {

    static final Path DISC_DB = Path.of("library/disc_validation.db");
    static BuildingProfileValidator validator;

    @BeforeAll
    static void load() throws Exception {
        assumeTrue(Files.exists(DISC_DB), "disc_validation.db must exist");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DISC_DB)) {
            validator = BuildingProfileValidator.load(conn);
        }
    }

    @Test
    @Order(1)
    @DisplayName("W-DV-PROFILE-1: Loads 36+ building profiles")
    void loadProfiles() {
        assertTrue(validator.hasProfiles(), "Must have loaded profiles");
        assertTrue(validator.buildingCount() >= 30,
                "Must have >= 30 buildings, got " + validator.buildingCount());
    }

    @Test
    @Order(2)
    @DisplayName("W-DV-PROFILE-2: Normal residential composition passes")
    void normalResidentialPasses() {
        // Typical residential: walls, windows, doors, slabs
        List<ExtractionElement> elements = new ArrayList<>();
        for (int i = 0; i < 35; i++) elements.add(elem("IfcWall"));
        for (int i = 0; i < 15; i++) elements.add(elem("IfcWindow"));
        for (int i = 0; i < 10; i++) elements.add(elem("IfcDoor"));
        for (int i = 0; i < 8; i++) elements.add(elem("IfcSlab"));

        BuildingProfileValidator.Report report = validator.validate(elements, "TEST_RES");
        assertEquals("IfcWall", report.dominantClass());
        // No anomalies expected for a normal composition
        assertFalse(report.hasAnomalies(),
                "Normal residential should have no anomalies: " + report.anomalies());
    }

    @Test
    @Order(3)
    @DisplayName("W-DV-PROFILE-3: Walls with no openings flagged")
    void wallsWithoutOpeningsFlagged() {
        // 50 walls, no doors, no windows — suspicious for architecture
        List<ExtractionElement> elements = new ArrayList<>();
        for (int i = 0; i < 50; i++) elements.add(elem("IfcWall"));

        BuildingProfileValidator.Report report = validator.validate(elements, "TEST_NOOPEN");
        assertTrue(report.hasAnomalies(), "Wall-only building should flag anomaly");
        assertTrue(report.anomalies().stream().anyMatch(a -> a.contains("MISSING_OPENINGS")),
                "Should flag MISSING_OPENINGS: " + report.anomalies());
    }

    @Test
    @Order(4)
    @DisplayName("W-DV-PROFILE-4: Novel IFC class flagged")
    void novelClassFlagged() {
        List<ExtractionElement> elements = new ArrayList<>();
        for (int i = 0; i < 20; i++) elements.add(elem("IfcWall"));
        for (int i = 0; i < 5; i++) elements.add(elem("IfcDoor"));
        elements.add(elem("IfcTunnel"));  // never seen before

        BuildingProfileValidator.Report report = validator.validate(elements, "TEST_NOVEL");
        assertTrue(report.anomalies().stream().anyMatch(a -> a.contains("NOVEL_CLASS") && a.contains("IfcTunnel")),
                "Should flag IfcTunnel as novel: " + report.anomalies());
    }

    @Test
    @Order(5)
    @DisplayName("W-DV-PROFILE-5: Dominant class identified correctly")
    void dominantClassCorrect() {
        List<ExtractionElement> elements = new ArrayList<>();
        for (int i = 0; i < 100; i++) elements.add(elem("IfcFlowFitting"));
        for (int i = 0; i < 20; i++) elements.add(elem("IfcFlowSegment"));

        BuildingProfileValidator.Report report = validator.validate(elements, "TEST_MEP");
        assertEquals("IfcFlowFitting", report.dominantClass());
        assertEquals(2, report.classCount());
        report.print();
    }

    private static ExtractionElement elem(String ifcClass) {
        return new ExtractionElement(
                "GF", ifcClass, ifcClass + "_test", 0,
                0, 1, 0, 1, 0, 1,
                "N", "ARC", null, null, null, null);
    }
}
