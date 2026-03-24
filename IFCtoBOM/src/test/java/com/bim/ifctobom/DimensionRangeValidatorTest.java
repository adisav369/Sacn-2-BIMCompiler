package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DimensionRangeValidator tests — proves mined rules load and validate
 * extracted element dimensions against typical ranges.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-DV-PIPELINE-1: Loads 415+ rules from ERP.db (25 IFC classes)</li>
 *   <li>W-DV-PIPELINE-2: Normal IfcWall (3000×3500×3000mm) passes</li>
 *   <li>W-DV-PIPELINE-3: Absurd IfcWall (500000mm width) flagged as outlier</li>
 *   <li>W-DV-PIPELINE-4: Unknown IFC class produces zero outliers</li>
 *   <li>W-DV-PIPELINE-5: Report counts match (checked + outliers + passed = total checkable)</li>
 * </ul>
 *
 * // Implementing DISC_VALIDATION_DB_SRS.md §DV010 — Witness: W-DV-PIPELINE
 */
@DisplayName("DimensionRangeValidator — IFC pipeline dimension check")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DimensionRangeValidatorTest {

    static final Path DISC_DB = Path.of("library/ERP.db");
    static DimensionRangeValidator validator;

    @BeforeAll
    static void load() throws Exception {
        assumeTrue(Files.exists(DISC_DB), "ERP.db must exist");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DISC_DB)) {
            validator = DimensionRangeValidator.load(conn);
        }
    }

    @Test
    @Order(1)
    @DisplayName("W-DV-PIPELINE-1: Loads 25+ IFC classes from ERP.db")
    void loadRules() {
        assertTrue(validator.hasRules(), "Must have loaded rules");
        assertTrue(validator.classCount() >= 25,
                "Must have >= 25 IFC classes, got " + validator.classCount());
    }

    @Test
    @Order(2)
    @DisplayName("W-DV-PIPELINE-2: Normal IfcWall passes")
    void normalWallPasses() {
        // 3000×3500×3000mm — well within typical IfcWall range from 20 buildings
        List<ExtractionElement> elements = List.of(
                element("IfcWall", "GF", 3.0, 3.5, 3.0));  // metres

        DimensionRangeValidator.Report report = validator.validate(elements, "TEST");
        assertEquals(1, report.checked());
        assertEquals(1, report.passed());
        assertEquals(0, report.outlierCount(), "Normal wall should have 0 outliers");
    }

    @Test
    @Order(3)
    @DisplayName("W-DV-PIPELINE-3: Absurd IfcWall flagged as outlier")
    void absurdWallFlagged() {
        // 500m wide wall — 500000mm, way beyond any observed max (~55250mm in Esplanades)
        List<ExtractionElement> elements = List.of(
                element("IfcWall", "GF", 500.0, 3.5, 3.0));  // 500m wide

        DimensionRangeValidator.Report report = validator.validate(elements, "TEST");
        assertEquals(1, report.checked());
        assertEquals(0, report.passed());
        assertTrue(report.hasOutliers(), "500m wall must be an outlier");
        assertEquals(1, report.outlierCount());

        // Verify the outlier details
        var outliers = report.outliersByClass().get("IfcWall");
        assertNotNull(outliers);
        assertEquals("W", outliers.get(0).dimension(), "Width should be flagged");
    }

    @Test
    @Order(4)
    @DisplayName("W-DV-PIPELINE-4: Unknown IFC class has zero outliers")
    void unknownClassNoOutliers() {
        List<ExtractionElement> elements = List.of(
                element("IfcChimney", "GF", 0.5, 0.5, 5.0));

        DimensionRangeValidator.Report report = validator.validate(elements, "TEST");
        assertEquals(0, report.checked(), "Unknown class should not be checked");
        assertEquals(0, report.outlierCount());
    }

    @Test
    @Order(5)
    @DisplayName("W-DV-PIPELINE-5: Report arithmetic: checked = passed + outliers")
    void reportArithmetic() {
        List<ExtractionElement> elements = List.of(
                element("IfcWall", "GF", 3.0, 3.5, 3.0),      // normal → pass
                element("IfcWall", "L1", 500.0, 3.5, 3.0),     // absurd → outlier
                element("IfcDoor", "GF", 0.9, 0.1, 2.1),       // normal door → pass
                element("IfcChimney", "GF", 0.5, 0.5, 5.0)     // unknown → not checked
        );

        DimensionRangeValidator.Report report = validator.validate(elements, "TEST");
        assertEquals(4, report.totalElements());
        // IfcChimney not checked → checked = 3
        assertEquals(3, report.checked());
        assertEquals(report.checked(), report.passed() + report.outlierCount(),
                "checked must equal passed + outliers");
        report.print();  // visual verification in test log
    }

    // ── Helper ──────────────────────────────────────────────────────

    /** Create a test ExtractionElement with given dimensions in metres. */
    private static ExtractionElement element(String ifcClass, String storey,
                                              double wMetres, double dMetres, double hMetres) {
        return new ExtractionElement(
                storey, ifcClass, ifcClass + "_test", 0,
                0, wMetres, 0, dMetres, 0, hMetres,  // min=0, max=dim
                "N", "ARC", null, null, null, null, null);
    }
}
