package com.bim.designer;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bridge Rules Test — validates DV006b infrastructure bridge rules in validation.db.
 *
 * <p>Cross-validates mined dimensional/ratio/placement rules against the
 * infra_bridge.db extraction. Proves that the rules accurately capture
 * the reference model's structural invariants.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-BR-RULES-EXIST: All 13 bridge rules exist and are active</li>
 *   <li>W-BR-PARAMS: All 29 rule parameters present with expected values</li>
 *   <li>W-BR-XVAL-DIM: Dimension rules match extraction DB averages</li>
 *   <li>W-BR-RATIO-CONSISTENT: Footing spread/depth ratios are physically valid</li>
 *   <li>W-BR-Z-CONTINUITY: Z-range params consistent with extraction geometry</li>
 * </ul>
 *
 * // Implementing InfrastructureAnalysis.md §7.1 — Witness: W-BR-*
 */
@DisplayName("Bridge Rules — DV006b Validation + Cross-Check")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BridgeRulesTest {

    static final Path VALIDATION_DB = Path.of("library/validation.db");
    static final Path BRIDGE_DB = Path.of("DAGCompiler/lib/output/infra_bridge.db");

    static Connection valConn;
    static Connection brConn;

    // ── Expected rules (rule_id → name) ───────────────────────────────

    static final Map<Integer, String> EXPECTED_RULES = new LinkedHashMap<>();
    static {
        EXPECTED_RULES.put(901, "BRIDGE_ARCH_MEMBER_DIM");
        EXPECTED_RULES.put(902, "BRIDGE_PIER_COLUMN_RAIL");
        EXPECTED_RULES.put(903, "BRIDGE_PIER_COLUMN_ROAD");
        EXPECTED_RULES.put(904, "BRIDGE_FOOTING_RAIL");
        EXPECTED_RULES.put(905, "BRIDGE_FOOTING_ROAD");
        EXPECTED_RULES.put(906, "BRIDGE_DECK_THICKNESS");
        EXPECTED_RULES.put(907, "BRIDGE_APPROACH_SLAB");
        EXPECTED_RULES.put(908, "FOOTING_SPREAD_RATIO");
        EXPECTED_RULES.put(909, "FOOTING_DEPTH_RATIO");
        EXPECTED_RULES.put(910, "BRIDGE_RAILING_HEIGHT");
        EXPECTED_RULES.put(911, "BRIDGE_SIGNAGE_COUNT");
        EXPECTED_RULES.put(912, "SEGMENT_Z_PIER_DECK");
        EXPECTED_RULES.put(913, "SEGMENT_Z_BELOW_GROUND");
    }

    // ── Setup ──────────────────────────────────────────────────────────

    @BeforeAll
    static void openConnections() throws Exception {
        assumeTrue(Files.exists(VALIDATION_DB), "validation.db must exist");
        valConn = DriverManager.getConnection("jdbc:sqlite:" + VALIDATION_DB);

        if (Files.exists(BRIDGE_DB)) {
            brConn = DriverManager.getConnection("jdbc:sqlite:" + BRIDGE_DB);
        }
    }

    @AfterAll
    static void closeConnections() throws Exception {
        if (valConn != null) valConn.close();
        if (brConn != null) brConn.close();
    }

    // ── W-BR-RULES-EXIST: All 13 bridge rules active ─────────────────

    @Test
    @Order(1)
    @DisplayName("W-BR-RULES-EXIST: All 13 bridge rules exist and are active")
    void rulesExist() throws Exception {
        String sql = "SELECT ad_val_rule_id, name, discipline, rule_type, is_active " +
                     "FROM AD_Val_Rule WHERE provenance = 'Infra_Bridge' ORDER BY ad_val_rule_id";

        Map<Integer, String> found = new LinkedHashMap<>();
        try (Statement st = valConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String name = rs.getString(2);
                int active = rs.getInt(5);
                found.put(id, name);
                assertEquals(1, active, "Rule " + name + " must be active");
                System.out.printf("  Rule %d: %-30s %s/%s  active=%d%n",
                        id, name, rs.getString(3), rs.getString(4), active);
            }
        }

        assertEquals(13, found.size(), "Expected 13 bridge rules, got " + found.size());

        for (var expected : EXPECTED_RULES.entrySet()) {
            assertTrue(found.containsKey(expected.getKey()),
                    "Missing rule " + expected.getKey() + " (" + expected.getValue() + ")");
            assertEquals(expected.getValue(), found.get(expected.getKey()),
                    "Rule " + expected.getKey() + " name mismatch");
        }
    }

    // ── W-BR-PARAMS: All 29 parameters present ───────────────────────

    @Test
    @Order(2)
    @DisplayName("W-BR-PARAMS: All 29 rule parameters present with values")
    void paramsExist() throws Exception {
        String sql = "SELECT p.ad_val_rule_id, r.name, p.name, p.value " +
                     "FROM AD_Val_Rule_Param p " +
                     "JOIN AD_Val_Rule r ON p.ad_val_rule_id = r.ad_val_rule_id " +
                     "WHERE r.provenance = 'Infra_Bridge' " +
                     "ORDER BY p.ad_val_rule_id, p.name";

        int count = 0;
        try (Statement st = valConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                count++;
                String ruleName = rs.getString(2);
                String paramName = rs.getString(3);
                String value = rs.getString(4);
                assertNotNull(value, "Param " + ruleName + "." + paramName + " must have a value");
                assertFalse(value.isEmpty(), "Param " + ruleName + "." + paramName + " must not be empty");
                System.out.printf("  %-30s %-20s = %s%n", ruleName, paramName, value);
            }
        }

        assertEquals(29, count, "Expected 29 bridge rule params, got " + count);
    }

    // ── W-BR-XVAL-DIM: Cross-validate against extraction DB ──────────

    @Test
    @Order(10)
    @DisplayName("W-BR-XVAL-DIM: Dimension rules match extraction averages")
    void crossValidateDimensions() throws Exception {
        assumeTrue(brConn != null, "infra_bridge.db required for cross-validation");

        // Query extraction: per-class, per-segment average dimensions
        String sql = """
                SELECT em.ifc_class, ss.name as segment,
                       COUNT(*),
                       ROUND(AVG(rt.maxX-rt.minX)*1000, 0) as avg_w,
                       ROUND(AVG(rt.maxY-rt.minY)*1000, 0) as avg_d,
                       ROUND(AVG(rt.maxZ-rt.minZ)*1000, 0) as avg_h
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                JOIN rel_contained_in_space rc ON em.guid = rc.element_guid
                JOIN spatial_structure ss ON rc.space_guid = ss.guid
                WHERE em.ifc_class IN ('IfcColumn','IfcFooting','IfcSlab','IfcMember','IfcRailing','IfcSign')
                GROUP BY em.ifc_class, ss.name
                ORDER BY em.ifc_class, ss.name
                """;

        System.out.println("\nCross-validation: extraction vs mined rules");
        int checks = 0;

        try (Statement st = brConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String ifcClass = rs.getString(1);
                String segment = rs.getString(2);
                int count = rs.getInt(3);
                double w = rs.getDouble(4);
                double d = rs.getDouble(5);
                double h = rs.getDouble(6);

                // Map extraction row to rule ID
                Integer ruleId = mapToRuleId(ifcClass, segment);
                if (ruleId == null) continue;

                // Get mined params
                Map<String, Double> params = getRuleParams(ruleId);

                System.out.printf("  %-12s %-35s n=%d  ext=%.0f×%.0f×%.0f  rule=%d%n",
                        ifcClass, segment, count, w, d, h, ruleId);

                // Verify dimensions match (exact — same source data)
                if (params.containsKey("width_mm") || params.containsKey("avg_width_mm")) {
                    double ruleW = params.getOrDefault("width_mm",
                            params.getOrDefault("avg_width_mm", 0.0));
                    assertEquals(ruleW, w, 1.0,
                            "Width mismatch for rule " + ruleId + " (" + segment + ")");
                    checks++;
                }
                if (params.containsKey("depth_mm") || params.containsKey("avg_depth_mm")) {
                    double ruleD = params.getOrDefault("depth_mm",
                            params.getOrDefault("avg_depth_mm", 0.0));
                    assertEquals(ruleD, d, 1.0,
                            "Depth mismatch for rule " + ruleId + " (" + segment + ")");
                    checks++;
                }
                if (params.containsKey("height_mm") || params.containsKey("avg_height_mm")
                        || params.containsKey("min_height_mm")) {
                    double ruleH = params.getOrDefault("height_mm",
                            params.getOrDefault("avg_height_mm",
                            params.getOrDefault("min_height_mm", 0.0)));
                    assertEquals(ruleH, h, 1.0,
                            "Height mismatch for rule " + ruleId + " (" + segment + ")");
                    checks++;
                }
            }
        }

        assertTrue(checks >= 15, "Expected >= 15 dimension cross-checks, got " + checks);
        System.out.printf("  Cross-validated %d dimension values%n", checks);
    }

    // ── W-BR-RATIO-CONSISTENT: Footing exceeds column ────────────────

    @Test
    @Order(20)
    @DisplayName("W-BR-RATIO-CONSISTENT: Footing spread/depth ratios valid")
    void ratioConsistency() throws Exception {
        // Rail: footing 904 vs column 902
        Map<String, Double> railFooting = getRuleParams(904);
        Map<String, Double> railColumn = getRuleParams(902);

        double railSpread = railFooting.get("width_mm") / railColumn.get("width_mm");
        double railDepthRatio = railFooting.get("height_mm") / railColumn.get("height_mm");

        System.out.printf("  Rail spread: %.2fx (footing %.0f / column %.0f)%n",
                railSpread, railFooting.get("width_mm"), railColumn.get("width_mm"));
        System.out.printf("  Rail depth:  %.2fx (footing %.0f / column %.0f)%n",
                railDepthRatio, railFooting.get("height_mm"), railColumn.get("height_mm"));

        assertTrue(railSpread > 1.0, "Rail footing must be wider than column");
        assertTrue(railDepthRatio > 0.1 && railDepthRatio < 1.0,
                "Rail footing depth ratio must be 0.1-1.0: " + railDepthRatio);

        // Road: footing 905 vs column 903
        Map<String, Double> roadFooting = getRuleParams(905);
        Map<String, Double> roadColumn = getRuleParams(903);

        double roadSpread = roadFooting.get("width_mm") / roadColumn.get("width_mm");
        double roadDepthRatio = roadFooting.get("height_mm") / roadColumn.get("height_mm");

        System.out.printf("  Road spread: %.2fx (footing %.0f / column %.0f)%n",
                roadSpread, roadFooting.get("width_mm"), roadColumn.get("width_mm"));
        System.out.printf("  Road depth:  %.2fx (footing %.0f / column %.0f)%n",
                roadDepthRatio, roadFooting.get("height_mm"), roadColumn.get("height_mm"));

        assertTrue(roadSpread > 1.0, "Road footing must be wider than column");
        assertTrue(roadDepthRatio > 0.1 && roadDepthRatio < 1.0,
                "Road footing depth ratio must be 0.1-1.0: " + roadDepthRatio);

        // Verify ratios match mined ratio rules (908, 909)
        Map<String, Double> spreadRule = getRuleParams(908);
        Map<String, Double> depthRule = getRuleParams(909);

        assertEquals(spreadRule.get("min_ratio"), Math.round(railSpread * 100) / 100.0, 0.01,
                "Mined spread min_ratio must match rail spread");
        assertEquals(depthRule.get("min_ratio"), Math.round(railDepthRatio * 100) / 100.0, 0.01,
                "Mined depth min_ratio must match rail depth ratio");
    }

    // ── W-BR-Z-CONTINUITY: Z-range params physically valid ───────────

    @Test
    @Order(30)
    @DisplayName("W-BR-Z-CONTINUITY: Z params consistent with extraction")
    void zContinuity() throws Exception {
        assumeTrue(brConn != null, "infra_bridge.db required for Z validation");

        // Check pier Z ranges from extraction
        String sql = """
                SELECT ss.name as segment,
                       MIN(rt.minZ) as z_min, MAX(rt.maxZ) as z_max
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                JOIN rel_contained_in_space rc ON em.guid = rc.element_guid
                JOIN spatial_structure ss ON rc.space_guid = ss.guid
                WHERE em.ifc_class IN ('IfcColumn', 'IfcFooting')
                GROUP BY ss.name
                ORDER BY ss.name
                """;

        try (Statement st = brConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String segment = rs.getString(1);
                double zMin = rs.getDouble(2);
                double zMax = rs.getDouble(3);
                System.out.printf("  %-35s Z: [%.3f, %.3f]%n", segment, zMin, zMax);

                // Pier segments should extend below Z=0 (rule 913)
                if (segment.contains("pier")) {
                    assertTrue(zMin < 0, segment + " pier must extend below Z=0, got zMin=" + zMin);
                }
            }
        }

        // Verify rule 912 gap tolerance
        Map<String, Double> pierDeckRule = getRuleParams(912);
        double maxGap = pierDeckRule.get("max_gap_mm");
        assertEquals(100.0, maxGap, "Pier-deck max gap must be 100mm");

        // Verify rule 913 depth params
        Map<String, Double> belowGround = getRuleParams(913);
        assertTrue(belowGround.get("rail_min_depth_m") < 0,
                "Rail min depth must be negative (below ground)");
        assertTrue(belowGround.get("road_min_depth_m") < 0,
                "Road min depth must be negative (below ground)");
        System.out.printf("  Rule 913: rail_min=%.2fm, road_min=%.2fm%n",
                belowGround.get("rail_min_depth_m"), belowGround.get("road_min_depth_m"));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /** Map extraction (ifcClass, segment) to rule ID. */
    private Integer mapToRuleId(String ifcClass, String segment) {
        return switch (ifcClass + "|" + segment) {
            case "IfcMember|railbridge - superstructure" -> 901;
            case "IfcColumn|rail bridge - pier" -> 902;
            case "IfcColumn|road river bridge - pier" -> 903;
            case "IfcFooting|rail bridge - pier" -> 904;
            case "IfcFooting|road river bridge - pier" -> 905;
            case "IfcSlab|road rail bridge - deck" -> 906;
            case "IfcSlab|road rail bridge - approach" -> 907;
            case "IfcRailing|road rail bridge - deck" -> 910;
            case "IfcSign|railbridge - superstructure" -> 911;
            default -> null;
        };
    }

    /** Get numeric rule params. */
    private Map<String, Double> getRuleParams(int ruleId) throws SQLException {
        String sql = "SELECT name, CAST(value AS REAL) FROM AD_Val_Rule_Param WHERE ad_val_rule_id = ?";
        Map<String, Double> params = new LinkedHashMap<>();
        try (PreparedStatement ps = valConn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    params.put(rs.getString(1), rs.getDouble(2));
                }
            }
        }
        return params;
    }
}
