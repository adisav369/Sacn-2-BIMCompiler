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
 * <p>R17: Mining queries read from per-building reference DBs
 * (elements_meta + elements_rtree) — the real oracle extracted by IfcOpenShell.
 * I_Element_Extraction removed from component_library.db.
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

    // R17: Mining queries now read from per-building reference DBs
    static final Path VALIDATION_DB = Path.of("library/validation.db");
    static final Path TE_REF_DB = Path.of("DAGCompiler/lib/input/SJTII_Terminal_extracted.db");
    static final Path DX_REF_DB = Path.of("DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db");

    static Connection valConn;
    static Connection teConn;
    static Connection dxConn;

    @BeforeAll
    static void openConnections() throws Exception {
        assumeTrue(Files.exists(VALIDATION_DB),
                "validation.db must exist — run migration/V001 + V002 first");
        assumeTrue(Files.exists(TE_REF_DB),
                "TE reference DB must exist: " + TE_REF_DB);
        assumeTrue(Files.exists(DX_REF_DB),
                "DX reference DB must exist: " + DX_REF_DB);

        valConn = DriverManager.getConnection("jdbc:sqlite:" + VALIDATION_DB);
        teConn = DriverManager.getConnection("jdbc:sqlite:" + TE_REF_DB);
        dxConn = DriverManager.getConnection("jdbc:sqlite:" + DX_REF_DB);
    }

    @AfterAll
    static void closeConnections() throws Exception {
        if (valConn != null) valConn.close();
        if (teConn != null) teConn.close();
        if (dxConn != null) dxConn.close();
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

        // R17: Mine from TE reference DB (elements_meta + elements_rtree)
        int violations;
        try (Statement st = teConn.createStatement();
             ResultSet rs = st.executeQuery(String.format(
                "SELECT COUNT(*) FROM (" +
                "  SELECT a.id," +
                "    MIN(SQRT(" +
                "      (ra.minX - rb.minX)*(ra.minX - rb.minX) +" +
                "      (ra.minY - rb.minY)*(ra.minY - rb.minY)" +
                "    )) * 1000 as nn_mm" +
                "  FROM elements_meta a" +
                "  JOIN elements_rtree ra ON a.id = ra.id" +
                "  JOIN elements_meta b ON a.storey = b.storey" +
                "    AND a.ifc_class = b.ifc_class" +
                "    AND a.id != b.id" +
                "  JOIN elements_rtree rb ON b.id = rb.id" +
                "  WHERE a.ifc_class = 'IfcFireSuppressionTerminal'" +
                "  GROUP BY a.id" +
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

        // R17: Mine from TE reference DB (elements_meta + elements_rtree)
        int closePairs;
        try (Statement st = teConn.createStatement();
             ResultSet rs = st.executeQuery(String.format(
                "SELECT COUNT(*) FROM (" +
                "  SELECT" +
                "    SQRT(" +
                "      ((ra.minX+ra.maxX)/2 - (rb.minX+rb.maxX)/2) *" +
                "      ((ra.minX+ra.maxX)/2 - (rb.minX+rb.maxX)/2) +" +
                "      ((ra.minY+ra.maxY)/2 - (rb.minY+rb.maxY)/2) *" +
                "      ((ra.minY+ra.maxY)/2 - (rb.minY+rb.maxY)/2)" +
                "    ) * 1000 as dist_mm" +
                "  FROM elements_meta a" +
                "  JOIN elements_rtree ra ON a.id = ra.id" +
                "  JOIN elements_meta b ON a.storey = b.storey" +
                "  JOIN elements_rtree rb ON b.id = rb.id" +
                "  WHERE a.discipline = 'ELEC' AND b.discipline = 'SP'" +
                "    AND a.id < b.id" +
                ") WHERE dist_mm < %.1f", minClearanceMm))) {
            rs.next();
            closePairs = rs.getInt(1);
        }

        System.out.printf("  ELEC-SP: min_clearance=%.0fmm, close_pairs=%d (centroid proxy)%n",
                minClearanceMm, closePairs);
        // Advisory only — centroid proxy overstates violations.
        System.out.println("  [advisory] Centroid proxy — AABB clearance needs proper spatial join");
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

        // R17: Query DX reference DB directly
        int actualFittings;
        try (Statement st = dxConn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM elements_meta " +
                "WHERE ifc_class = 'IfcFlowFitting'")) {
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
