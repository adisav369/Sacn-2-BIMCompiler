package com.bim.designer;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Non-Disturbance Test — DocValidate §7.2
 *
 * <p>Mined validation rules MUST pass against the buildings they were mined from.
 * If a rule reports a violation against its source building, the rule is wrong,
 * not the building. The engineer's design is the ground truth.
 *
 * <p>Separation layer: mining queries use an {@code ExtractionView} abstraction.
 * Currently reads {@code I_Element_Extraction} from {@code component_library.db}.
 * When R13 moves those rows to per-building extraction DBs, only the connection
 * source changes — queries stay identical.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-ND-1: Sprinkler NN spacing within NFPA 13 max (+ documented exceptions)</li>
 *   <li>W-ND-2: ELEC-SP clearance measured (advisory — centroid proxy)</li>
 *   <li>W-ND-3: DX P23 exception count consistent with actual fittings</li>
 *   <li>W-ND-4: All non-CLASH rules have at least one parameter</li>
 *   <li>W-ND-5: All mined rules have mining provenance records</li>
 *   <li>W-ND-6: Validation DB schema integrity (all tables exist)</li>
 * </ul>
 */
@DisplayName("Non-Disturbance — DocValidate §7.2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NonDisturbanceTest {

    // -- Separation layer: paths to DBs --
    // When R13 moves I_Element_Extraction, change EXTRACTION_DB path only
    static final Path VALIDATION_DB = Path.of("library/validation.db");
    static final Path EXTRACTION_DB = Path.of("library/component_library.db");

    static Connection valConn;
    static Connection extConn;

    @BeforeAll
    static void openConnections() throws Exception {
        assumeTrue(Files.exists(VALIDATION_DB),
                "validation.db must exist — run migration/V001 + V002 first");
        assumeTrue(Files.exists(EXTRACTION_DB),
                "component_library.db must exist");

        valConn = DriverManager.getConnection("jdbc:sqlite:" + VALIDATION_DB);
        extConn = DriverManager.getConnection("jdbc:sqlite:" + EXTRACTION_DB);
    }

    @AfterAll
    static void closeConnections() throws Exception {
        if (valConn != null) valConn.close();
        if (extConn != null) extConn.close();
    }

    // ── W-ND-6: Schema integrity ──────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-ND-6: validation.db has all required tables")
    void schemaIntegrity() throws Exception {
        List<String> required = List.of(
                "AD_Val_Rule", "AD_Val_Rule_Param", "AD_Clash_Rule",
                "AD_Occupancy_Class", "AD_Val_Rule_Occupancy",
                "AD_Validation_Result", "AD_Val_Rule_Exception",
                "AD_Val_Rule_Mining_Source");

        try (Statement st = valConn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table'")) {
            Set<String> tables = new HashSet<>();
            while (rs.next()) tables.add(rs.getString(1));

            for (String t : required) {
                assertTrue(tables.contains(t), "Missing table: " + t);
            }
        }

        // Verify non-empty
        try (Statement st = valConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM AD_Val_Rule")) {
            rs.next();
            assertTrue(rs.getInt(1) > 0, "AD_Val_Rule must have seed data");
            System.out.printf("  validation.db: %d rules loaded%n", rs.getInt(1));
        }
    }

    // ── W-ND-1: Sprinkler NN spacing ──────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("W-ND-1: TE sprinkler NN spacing within NFPA 13 max (+ exceptions)")
    void sprinklerNNSpacing() throws Exception {
        // Read threshold from validation.db (rule 601)
        double maxSpacingMm;
        try (PreparedStatement ps = valConn.prepareStatement(
                "SELECT CAST(value AS REAL) FROM AD_Val_Rule_Param " +
                "WHERE ad_val_rule_id = 601 AND name = 'max_nn_spacing_mm'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Rule 601 must have max_nn_spacing_mm param");
            maxSpacingMm = rs.getDouble(1);
        }

        // Read documented exceptions
        int exceptions;
        try (PreparedStatement ps = valConn.prepareStatement(
                "SELECT COALESCE(SUM(count), 0) FROM AD_Val_Rule_Exception " +
                "WHERE ad_val_rule_id = 601 AND building_id = 'SJTII_Terminal'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            exceptions = rs.getInt(1);
        }

        // Mine: count TE sprinkler heads with NN > threshold
        // Separation layer: query uses I_Element_Extraction — portable
        int violations;
        try (Statement st = extConn.createStatement();
             ResultSet rs = st.executeQuery(String.format(
                "SELECT COUNT(*) FROM (" +
                "  SELECT a.placement_id," +
                "    MIN(SQRT(" +
                "      (a.min_x - b.min_x)*(a.min_x - b.min_x) +" +
                "      (a.min_y - b.min_y)*(a.min_y - b.min_y)" +
                "    )) * 1000 as nn_mm" +
                "  FROM I_Element_Extraction a" +
                "  JOIN I_Element_Extraction b" +
                "    ON a.building_type = b.building_type" +
                "    AND a.storey = b.storey" +
                "    AND a.ifc_class = b.ifc_class" +
                "    AND a.placement_id != b.placement_id" +
                "  WHERE a.building_type = 'SJTII_Terminal'" +
                "    AND a.ifc_class = 'IfcFireSuppressionTerminal'" +
                "  GROUP BY a.placement_id" +
                "  HAVING nn_mm > 10" +
                ") WHERE nn_mm > %.1f", maxSpacingMm))) {
            rs.next();
            violations = rs.getInt(1);
        }

        System.out.printf("  Sprinkler NN: max=%.0fmm, violations=%d, exceptions=%d%n",
                maxSpacingMm, violations, exceptions);
        assertTrue(violations <= exceptions,
                String.format("TE sprinkler violations (%d) exceed documented exceptions (%d)",
                        violations, exceptions));
    }

    // ── W-ND-2: ELEC-SP clearance (advisory) ─────────────────────────────

    @Test
    @Order(3)
    @DisplayName("W-ND-2: ELEC-SP clearance measured (advisory — centroid proxy)")
    void elecSpClearance() throws Exception {
        // Read threshold
        double minClearanceMm;
        try (PreparedStatement ps = valConn.prepareStatement(
                "SELECT CAST(value AS REAL) FROM AD_Val_Rule_Param " +
                "WHERE ad_val_rule_id = 603 AND name = 'min_clearance_mm'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Rule 603 must have min_clearance_mm param");
            minClearanceMm = rs.getDouble(1);
        }

        // Mine: count close pairs on Ground Floor (manageable cross-join)
        int closePairs;
        try (Statement st = extConn.createStatement();
             ResultSet rs = st.executeQuery(String.format(
                "SELECT COUNT(*) FROM (" +
                "  SELECT" +
                "    SQRT(" +
                "      ((a.min_x+a.max_x)/2 - (b.min_x+b.max_x)/2) *" +
                "      ((a.min_x+a.max_x)/2 - (b.min_x+b.max_x)/2) +" +
                "      ((a.min_y+a.max_y)/2 - (b.min_y+b.max_y)/2) *" +
                "      ((a.min_y+a.max_y)/2 - (b.min_y+b.max_y)/2)" +
                "    ) * 1000 as dist_mm" +
                "  FROM I_Element_Extraction a, I_Element_Extraction b" +
                "  WHERE a.building_type = 'SJTII_Terminal'" +
                "    AND b.building_type = 'SJTII_Terminal'" +
                "    AND a.discipline = 'ELEC' AND b.discipline = 'SP'" +
                "    AND a.storey = b.storey" +
                "    AND a.placement_id < b.placement_id" +
                ") WHERE dist_mm < %.1f", minClearanceMm))) {
            rs.next();
            closePairs = rs.getInt(1);
        }

        System.out.printf("  ELEC-SP: min_clearance=%.0fmm, close_pairs=%d (centroid proxy)%n",
                minClearanceMm, closePairs);
        // Advisory only — centroid proxy overstates violations.
        // Real AABB-to-AABB clearance needs R13 spatial join.
        System.out.println("  [advisory] Centroid proxy — AABB clearance pending R13");
    }

    // ── W-ND-3: DX P23 exception consistency ─────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-ND-3: DX P23 exception count consistent with actual fittings")
    void dxP23Exceptions() throws Exception {
        int docCount;
        try (PreparedStatement ps = valConn.prepareStatement(
                "SELECT count FROM AD_Val_Rule_Exception " +
                "WHERE ad_val_rule_exception_id = 1")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Exception 1 must exist");
            docCount = rs.getInt(1);
        }

        int actualFittings;
        try (Statement st = extConn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM I_Element_Extraction " +
                "WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcFlowFitting'")) {
            rs.next();
            actualFittings = rs.getInt(1);
        }

        System.out.printf("  DX P23: documented=%d, actual_fittings=%d%n",
                docCount, actualFittings);
        assertTrue(actualFittings >= docCount,
                String.format("Exception count (%d) exceeds actual fittings (%d)",
                        docCount, actualFittings));
    }

    // ── W-ND-4: Rule param integrity ──────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("W-ND-4: All non-CLASH rules have at least one parameter")
    void ruleParamIntegrity() throws Exception {
        int orphans;
        try (Statement st = valConn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM AD_Val_Rule r " +
                "WHERE NOT EXISTS (" +
                "  SELECT 1 FROM AD_Val_Rule_Param p " +
                "  WHERE p.ad_val_rule_id = r.ad_val_rule_id" +
                ") AND r.rule_type != 'CLASH'")) {
            rs.next();
            orphans = rs.getInt(1);
        }

        assertEquals(0, orphans, "Non-CLASH rules without parameters");
    }

    // ── W-ND-5: Mining provenance ─────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("W-ND-5: All mined rules have mining provenance records")
    void miningProvenance() throws Exception {
        int minedRules, minedSources;
        try (Statement st = valConn.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM AD_Val_Rule WHERE provenance LIKE 'MINED:%'");
            rs.next();
            minedRules = rs.getInt(1);

            rs = st.executeQuery(
                    "SELECT COUNT(DISTINCT ad_val_rule_id) FROM AD_Val_Rule_Mining_Source");
            rs.next();
            minedSources = rs.getInt(1);
        }

        System.out.printf("  Mining: %d mined rules, %d with provenance%n",
                minedRules, minedSources);
        assertEquals(minedRules, minedSources,
                "Mined rules must have matching mining source records");
    }
}
