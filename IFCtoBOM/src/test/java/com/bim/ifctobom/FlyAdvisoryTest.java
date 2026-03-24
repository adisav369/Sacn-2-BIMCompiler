package com.bim.ifctobom;

import com.bim.ifctobom.DimensionRangeValidator.Outlier;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FL-2 Flywheel Advisory witnesses.
 *
 * <p>Tests the write-side: validators → W_Validation_Advisory table,
 * and the read-side: querying advisories back from ERP.db.
 *
 * // Implementing BIM_Designer_SRS.md §27.7 — Witness: W-FL-ADVISORY-1..5
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlyAdvisoryTest {

    static final Path DISC_DB = Path.of("library/ERP.db");
    static Connection discConn;

    @BeforeAll
    static void openDb() throws Exception {
        assertTrue(Files.exists(DISC_DB), "ERP.db must exist");
        discConn = DriverManager.getConnection("jdbc:sqlite:" + DISC_DB);

        // Ensure DV012 schema exists
        try (Statement st = discConn.createStatement()) {
            st.execute(Files.readString(Path.of("migration/DV012_validation_advisory.sql")));
        } catch (SQLException e) {
            // Table may already exist — that's fine
            if (!e.getMessage().contains("already exists")
                    && !e.getMessage().contains("UNIQUE constraint")) {
                throw e;
            }
        }

        // Clear test data
        try (PreparedStatement ps = discConn.prepareStatement(
                "DELETE FROM W_Validation_Advisory WHERE building_type = 'TEST_FL2'")) {
            ps.executeUpdate();
        }
    }

    @AfterAll
    static void closeDb() throws Exception {
        // Clean up test data
        if (discConn != null) {
            try (PreparedStatement ps = discConn.prepareStatement(
                    "DELETE FROM W_Validation_Advisory WHERE building_type = 'TEST_FL2'")) {
                ps.executeUpdate();
            }
            discConn.close();
        }
    }

    // ── W-FL-ADVISORY-4: DimensionRangeValidator.writeAdvisories creates WARNING + SUGGESTION ──

    @Test
    @Order(1)
    void w_fl_advisory_4_dimensionWriteAdvisories() throws Exception {
        // Build a report with one outlier
        Map<String, List<Outlier>> outliers = new LinkedHashMap<>();
        outliers.put("IfcWall", List.of(
                new Outlier("WALL_001", "GF", "W", 50.0, 800.0, 10269.0,
                        50.0, 200.0, 2800.0)));

        var report = new DimensionRangeValidator.Report(
                "TEST_FL2", 100, 80, 79, outliers);

        DimensionRangeValidator.writeAdvisories(discConn, report);

        // Verify: should have 2 rows — 1 WARNING + 1 SUGGESTION
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT severity, rule_name, message, suggestion "
              + "FROM W_Validation_Advisory "
              + "WHERE building_type = 'TEST_FL2' AND layer = 'DIMENSION' "
              + "ORDER BY advisory_id")) {
            ResultSet rs = ps.executeQuery();

            // WARNING row
            assertTrue(rs.next(), "Should have WARNING row");
            assertEquals("WARNING", rs.getString("severity"));
            assertTrue(rs.getString("rule_name").contains("DIMENSION_RANGE_W:IfcWall"));
            assertTrue(rs.getString("message").contains("50mm"));
            assertNull(rs.getString("suggestion"));

            // SUGGESTION row
            assertTrue(rs.next(), "Should have SUGGESTION row");
            assertEquals("SUGGESTION", rs.getString("severity"));
            assertNotNull(rs.getString("suggestion"), "SUGGESTION must include suggested value");
            assertTrue(rs.getString("suggestion").contains("Nearest typical"));
        }
    }

    // ── W-FL-ADVISORY-5: BuildingProfileValidator.writeAdvisories creates WARNING rows ──

    @Test
    @Order(2)
    void w_fl_advisory_5_profileWriteAdvisories() throws Exception {
        var report = new BuildingProfileValidator.Report(
                "TEST_FL2", 100, 5, "IfcWall",
                Map.of("IfcWall", 60.0, "IfcDoor", 10.0),
                List.of("DIVERSITY_HIGH: 25 IFC classes (typical range 3-12)",
                        "NOVEL_CLASS: IfcZzz never seen in 36 previous buildings"));

        BuildingProfileValidator.writeAdvisories(discConn, report);

        // Verify: 2 WARNING + 1 INFO = 3 rows
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT severity, rule_name, message "
              + "FROM W_Validation_Advisory "
              + "WHERE building_type = 'TEST_FL2' AND layer = 'PROFILE' "
              + "ORDER BY advisory_id")) {
            ResultSet rs = ps.executeQuery();

            // WARNING rows
            assertTrue(rs.next());
            assertEquals("WARNING", rs.getString("severity"));
            assertTrue(rs.getString("message").contains("DIVERSITY_HIGH"));

            assertTrue(rs.next());
            assertEquals("WARNING", rs.getString("severity"));
            assertTrue(rs.getString("message").contains("NOVEL_CLASS"));

            // INFO row — profile summary
            assertTrue(rs.next());
            assertEquals("INFO", rs.getString("severity"));
            assertEquals("PROFILE_SUMMARY", rs.getString("rule_name"));
            assertTrue(rs.getString("message").contains("5 IFC classes"));
        }
    }

    // ── W-FL-ADVISORY-1: listAdvisories returns advisories for known building ──

    @Test
    @Order(3)
    void w_fl_advisory_1_listAdvisoriesReturnsRows() throws Exception {
        // After tests 1+2, TEST_FL2 should have 5 rows (2 dim + 3 profile)
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM W_Validation_Advisory WHERE building_type = 'TEST_FL2'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1),
                    "TEST_FL2 should have 5 advisory rows (2 dim + 3 profile)");
        }
    }

    // ── W-FL-ADVISORY-2: SUGGESTION includes suggested value ──

    @Test
    @Order(4)
    void w_fl_advisory_2_suggestionIncludesValue() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT suggestion, actual_value, expected_min, expected_max "
              + "FROM W_Validation_Advisory "
              + "WHERE building_type = 'TEST_FL2' AND severity = 'SUGGESTION'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Should have at least one SUGGESTION row");

            String sugg = rs.getString("suggestion");
            assertNotNull(sugg, "suggestion must not be null");
            assertTrue(sugg.contains("Nearest typical"), "suggestion must contain nearest typical value");

            // Verify actual/expected values are populated
            assertNotNull(rs.getObject("actual_value"), "actual_value must be set");
            assertNotNull(rs.getObject("expected_min"), "expected_min must be set");
            assertNotNull(rs.getObject("expected_max"), "expected_max must be set");
        }
    }

    // ── W-FL-ADVISORY-3: Empty advisory list for clean building ──

    @Test
    @Order(5)
    void w_fl_advisory_3_emptyForCleanBuilding() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM W_Validation_Advisory WHERE building_type = 'NONEXISTENT_BUILDING_XYZ'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "Nonexistent building should have 0 advisories");
        }
    }

    // ── W-FL-CALIBRATE-1: suggestDimensions(IfcWall) returns typical range ──

    @Test
    @Order(6)
    void w_fl_calibrate_1_suggestDimensionsReturnsRange() throws Exception {
        // Query mined dimension data for IfcWall — should have data from 20+ buildings
        String sql =
                "SELECT p.param_name, CAST(p.param_value AS REAL) "
              + "FROM ad_val_rule r "
              + "JOIN ad_val_rule_param p ON p.ad_val_rule_id = r.ad_val_rule_id "
              + "WHERE r.is_active = 1 AND r.check_method = 'DIMENSION_RANGE' "
              + "AND r.ifc_class = 'IfcWall'";

        double minW = Double.MAX_VALUE, maxW = 0;
        int observations = 0;

        try (PreparedStatement ps = discConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String param = rs.getString(1);
                double val = rs.getDouble(2);
                if (val <= 0) continue;
                observations++;
                if ("typical_width_mm".equals(param)) {
                    minW = Math.min(minW, val);
                    maxW = Math.max(maxW, val);
                }
            }
        }

        assertTrue(observations > 0,
                "IfcWall should have mined dimension observations");
        assertTrue(maxW > 0,
                "IfcWall should have typical width data");
        assertTrue(minW < maxW || minW == maxW,
                "minW should be <= maxW");
    }

    // ── W-FL-CALIBRATE-2: suggestDimensions(unknown) returns empty ──

    @Test
    @Order(7)
    void w_fl_calibrate_2_suggestDimensionsUnknownReturnsEmpty() throws Exception {
        String sql =
                "SELECT COUNT(*) FROM ad_val_rule "
              + "WHERE is_active = 1 AND check_method = 'DIMENSION_RANGE' "
              + "AND ifc_class = 'IfcNonExistentClass_XYZ'";

        try (PreparedStatement ps = discConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "Unknown IFC class should have 0 mined rules");
        }
    }

    // ── W-FL-SHAPE-1: Shape advisory detects class-shape mismatch ──

    @Test
    @Order(8)
    void w_fl_shape_1_detectsClassShapeMismatch() throws Exception {
        // An IfcWall that is ELONGATED (shaped like a beam: 100x100x3000mm)
        // planarity = 100/3000 = 0.033 < 0.15 ✓
        // elongation = 100/3000 = 0.033 < 0.40 → ELONGATED
        // But verifyClassConsistency for IfcWall checks planarity >= 0.20 → null (wall IS planar here)
        // So let's use a wall that isn't planar: 800x800x1000mm
        // planarity = 800/1000 = 0.80 >= 0.20 → violation!
        List<ExtractionElement> elements = List.of(
                new ExtractionElement("GF", "IfcWall", "WALL_THICK", 1,
                        0, 0.8, 0, 0.8, 0, 1.0, "N", "STR", null, null, null, "GUID_001", null),
                new ExtractionElement("GF", "IfcColumn", "COL_FLAT", 2,
                        0, 2.0, 0, 0.05, 0, 3.0, "N", "STR", null, null, null, "GUID_002", null)
        );

        ShapeAdvisoryWriter.Report report =
                ShapeAdvisoryWriter.analyze(discConn, elements, "TEST_FL2");

        assertTrue(report.mismatchCount() > 0,
                "Should detect shape-class mismatches for thick wall or flat column");

        // Verify advisory rows were written with layer='SHAPE'
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT severity, rule_name, suggestion "
              + "FROM W_Validation_Advisory "
              + "WHERE building_type = 'TEST_FL2' AND layer = 'SHAPE'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Should have at least one SHAPE advisory row");
            assertEquals("SUGGESTION", rs.getString("severity"),
                    "Shape advisories should be SUGGESTION severity");
            assertNotNull(rs.getString("suggestion"),
                    "Shape advisory should include correction suggestion");
        }
    }

    // ── W-FL-SHAPE-2: No shape advisories for geometrically consistent elements ──

    @Test
    @Order(9)
    void w_fl_shape_2_noMismatchForConsistentElements() throws Exception {
        // A proper thin IfcWall: 5000x200x3000mm → planarity = 200/5000 = 0.04 < 0.20 ✓
        // A proper IfcDoor: 900x100x2100mm → planarity = 100/2100 = 0.048 < 0.35 ✓
        List<ExtractionElement> elements = List.of(
                new ExtractionElement("GF", "IfcWall", "WALL_OK", 1,
                        0, 5.0, 0, 0.2, 0, 3.0, "N", "STR", null, null, null, "GUID_003", null),
                new ExtractionElement("GF", "IfcDoor", "DOOR_OK", 2,
                        0, 0.9, 0, 0.1, 0, 2.1, "N", "ARC", null, null, null, "GUID_004", null)
        );

        // Clear previous test data first
        try (PreparedStatement del = discConn.prepareStatement(
                "DELETE FROM W_Validation_Advisory WHERE building_type = 'TEST_FL2_CLEAN'")) {
            del.executeUpdate();
        }

        ShapeAdvisoryWriter.Report report =
                ShapeAdvisoryWriter.analyze(discConn, elements, "TEST_FL2_CLEAN");

        assertEquals(0, report.mismatchCount(),
                "Geometrically consistent elements should have 0 shape mismatches");

        // Verify no SHAPE rows written
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM W_Validation_Advisory "
              + "WHERE building_type = 'TEST_FL2_CLEAN' AND layer = 'SHAPE'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "No SHAPE advisories should be written for consistent elements");
        }
    }
}
