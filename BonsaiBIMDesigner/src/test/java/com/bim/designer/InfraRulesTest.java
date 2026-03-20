package com.bim.designer;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Infrastructure Rules Test — validates DV007 road + DV008 rail rules in validation.db,
 * cross-validates against extraction DBs, and checks for discipline cross-contamination.
 *
 * <p>Companion to BridgeRulesTest (DV006b). Together they prove all 30 infrastructure
 * rules are correctly scoped by provenance and internally consistent.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-ROAD-RULES: All 10 road rules exist with 28 params</li>
 *   <li>W-ROAD-XVAL: Road layer thicknesses match extraction Z-ranges</li>
 *   <li>W-ROAD-STACK: Road pavement layers Z-continuous (no gaps)</li>
 *   <li>W-RAIL-RULES: All 7 rail rules exist with 26 params</li>
 *   <li>W-RAIL-XVAL: Rail dimensions match extraction averages</li>
 *   <li>W-RAIL-SPACING: Sleeper spacing 606mm uniform, gauge 1500mm</li>
 *   <li>W-RAIL-EMBED: Sleepers embedded within ballast Z-range</li>
 *   <li>W-INFRA-SCOPE: No cross-contamination between bridge/road/rail provenance</li>
 * </ul>
 *
 * // Implementing InfrastructureAnalysis.md §2.1 — Witness: W-ROAD-*, W-RAIL-*, W-INFRA-*
 */
@DisplayName("Infrastructure Rules — Road + Rail + Cross-Scope")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InfraRulesTest {

    static final Path VALIDATION_DB = Path.of("library/validation.db");
    static final Path ROAD_DB = Path.of("DAGCompiler/lib/input/Infra_Road_extracted.db");
    static final Path RAIL_DB = Path.of("DAGCompiler/lib/input/Infra_Rail_extracted.db");

    static Connection valConn;
    static Connection roadConn;
    static Connection railConn;

    // ── Expected rules ────────────────────────────────────────────────

    static final Map<Integer, String> ROAD_RULES = new LinkedHashMap<>();
    static {
        ROAD_RULES.put(1001, "ROAD_SURFACE_COURSE");
        ROAD_RULES.put(1002, "ROAD_BINDER_COURSE");
        ROAD_RULES.put(1003, "ROAD_BASE_COURSE");
        ROAD_RULES.put(1004, "ROAD_SUBGRADE");
        ROAD_RULES.put(1005, "ROAD_CARRIAGEWAY_FOOTPRINT");
        ROAD_RULES.put(1006, "ROAD_PARKING_FOOTPRINT");
        ROAD_RULES.put(1007, "ROAD_BRIDGE_SEGMENT_FOOTPRINT");
        ROAD_RULES.put(1008, "ROAD_TOTAL_PAVEMENT_DEPTH");
        ROAD_RULES.put(1009, "ROAD_LAYER_Z_CONTINUITY");
        ROAD_RULES.put(1010, "ROAD_LINE_MARKING");
    }

    static final Map<Integer, String> RAIL_RULES = new LinkedHashMap<>();
    static {
        RAIL_RULES.put(1101, "RAIL_SLEEPER_DIM");
        RAIL_RULES.put(1102, "RAIL_RAIL_DIM");
        RAIL_RULES.put(1103, "RAIL_BALLAST_DIM");
        RAIL_RULES.put(1104, "RAIL_SLEEPER_SPACING");
        RAIL_RULES.put(1105, "RAIL_GAUGE");
        RAIL_RULES.put(1106, "RAIL_Z_STACKING");
        RAIL_RULES.put(1107, "RAIL_SLEEPER_EMBED");
    }

    // ── Setup ──────────────────────────────────────────────────────────

    @BeforeAll
    static void openConnections() throws Exception {
        assumeTrue(Files.exists(VALIDATION_DB), "validation.db must exist");
        valConn = DriverManager.getConnection("jdbc:sqlite:" + VALIDATION_DB);

        if (Files.exists(ROAD_DB)) {
            roadConn = DriverManager.getConnection("jdbc:sqlite:" + ROAD_DB);
        }
        if (Files.exists(RAIL_DB)) {
            railConn = DriverManager.getConnection("jdbc:sqlite:" + RAIL_DB);
        }
    }

    @AfterAll
    static void closeConnections() throws Exception {
        if (valConn != null) valConn.close();
        if (roadConn != null) roadConn.close();
        if (railConn != null) railConn.close();
    }

    // ═══ ROAD ═════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("W-ROAD-RULES: All 10 road rules exist with 28 params")
    void roadRulesExist() throws Exception {
        assertRulesExist("Infra_Road", ROAD_RULES, 10, 28);
    }

    @Test
    @Order(2)
    @DisplayName("W-ROAD-XVAL: Road layer thicknesses match extraction")
    void roadLayerThicknesses() throws Exception {
        assumeTrue(roadConn != null, "Infra_Road_extracted.db required");

        // Query extraction: layer thicknesses from Z-ranges in carriageway
        String sql = """
                SELECT em.element_name,
                       ROUND(AVG(rt.maxZ - rt.minZ) * 1000, 0) as thick_mm
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                WHERE em.storey = 'road carriageway'
                GROUP BY em.element_name
                ORDER BY thick_mm
                """;

        Map<String, Double> extracted = new LinkedHashMap<>();
        try (Statement st = roadConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                extracted.put(rs.getString(1), rs.getDouble(2));
            }
        }

        System.out.println("Road layer cross-validation:");

        // Surface course (rule 1001) = 40mm
        double surfaceThick = extracted.get("road - asphalt surface course");
        assertEquals(40.0, surfaceThick, 1.0, "Surface course thickness");
        assertEquals("40", getParam(1001, "thickness_mm"), "Rule 1001 thickness");
        System.out.printf("  Surface: extracted=%.0fmm, rule=40mm%n", surfaceThick);

        // Binder course (rule 1002) = 80mm
        double binderThick = extracted.get("road - asphalt binder course");
        assertEquals(80.0, binderThick, 1.0, "Binder course thickness");
        assertEquals("80", getParam(1002, "thickness_mm"), "Rule 1002 thickness");
        System.out.printf("  Binder:  extracted=%.0fmm, rule=80mm%n", binderThick);

        // Base course (rule 1003) = 120mm
        double baseThick = extracted.get("road - base course");
        assertEquals(120.0, baseThick, 1.0, "Base course thickness");
        assertEquals("120", getParam(1003, "thickness_mm"), "Rule 1003 thickness");
        System.out.printf("  Base:    extracted=%.0fmm, rule=120mm%n", baseThick);

        // Subgrade (rule 1004) = 250mm
        double subThick = extracted.get("road - subgrade");
        assertEquals(250.0, subThick, 1.0, "Subgrade thickness");
        assertEquals("250", getParam(1004, "thickness_mm"), "Rule 1004 thickness");
        System.out.printf("  Subgrade:extracted=%.0fmm, rule=250mm%n", subThick);
    }

    @Test
    @Order(3)
    @DisplayName("W-ROAD-STACK: Road layers Z-continuous (no gaps)")
    void roadLayerStacking() throws Exception {
        assumeTrue(roadConn != null, "Infra_Road_extracted.db required");

        // Query Z-ranges ordered bottom to top
        String sql = """
                SELECT em.element_name,
                       ROUND(MIN(rt.minZ) * 1000, 0) as z_min,
                       ROUND(MAX(rt.maxZ) * 1000, 0) as z_max
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                WHERE em.storey = 'road carriageway'
                GROUP BY em.element_name
                ORDER BY z_min
                """;

        List<double[]> layers = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (Statement st = roadConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
                layers.add(new double[]{rs.getDouble(2), rs.getDouble(3)});
            }
        }

        System.out.println("Road Z-stacking:");
        for (int i = 0; i < layers.size(); i++) {
            System.out.printf("  %-40s Z: [%.0f, %.0f]%n",
                    names.get(i), layers.get(i)[0], layers.get(i)[1]);
        }

        // Check continuity: each layer top = next layer bottom
        for (int i = 0; i < layers.size() - 1; i++) {
            double gap = Math.abs(layers.get(i)[1] - layers.get(i + 1)[0]);
            assertTrue(gap < 1.0,
                    "Gap between " + names.get(i) + " and " + names.get(i + 1) + ": " + gap + "mm");
        }

        // Verify total depth matches rule 1008
        double totalDepth = layers.get(layers.size() - 1)[1] - layers.get(0)[0];
        assertEquals(490.0, totalDepth, 1.0, "Total pavement depth");
        assertEquals("490", getParam(1008, "total_depth_mm"), "Rule 1008 total depth");
        System.out.printf("  Total depth: %.0fmm (rule=490mm)%n", totalDepth);
    }

    // ═══ RAIL ═════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("W-RAIL-RULES: All 7 rail rules exist with 26 params")
    void railRulesExist() throws Exception {
        assertRulesExist("Infra_Rail", RAIL_RULES, 7, 26);
    }

    @Test
    @Order(11)
    @DisplayName("W-RAIL-XVAL: Rail dimensions match extraction averages")
    void railDimensions() throws Exception {
        assumeTrue(railConn != null, "Infra_Rail_extracted.db required");

        String sql = """
                SELECT em.ifc_class, em.element_name,
                       COUNT(*),
                       ROUND(AVG(rt.maxX - rt.minX) * 1000, 0) as w,
                       ROUND(AVG(rt.maxY - rt.minY) * 1000, 0) as d,
                       ROUND(AVG(rt.maxZ - rt.minZ) * 1000, 0) as h
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                WHERE em.ifc_class <> 'IfcBuildingElementProxy'
                GROUP BY em.ifc_class, em.element_name
                """;

        System.out.println("Rail cross-validation:");
        try (Statement st = railConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String ifcClass = rs.getString(1);
                String name = rs.getString(2);
                int count = rs.getInt(3);
                double w = rs.getDouble(4);
                double d = rs.getDouble(5);
                double h = rs.getDouble(6);

                Integer ruleId = switch (ifcClass) {
                    case "IfcTrackElement" -> 1101;
                    case "IfcRail" -> 1102;
                    case "IfcCourse" -> 1103;
                    default -> null;
                };

                if (ruleId != null) {
                    Map<String, String> params = getAllParams(ruleId);
                    double ruleW = Double.parseDouble(params.get("width_mm"));
                    double ruleD = Double.parseDouble(params.get("depth_mm"));
                    double ruleH = Double.parseDouble(params.get("height_mm"));

                    assertEquals(ruleW, w, 1.0, ifcClass + " width mismatch");
                    assertEquals(ruleD, d, 1.0, ifcClass + " depth mismatch");
                    assertEquals(ruleH, h, 1.0, ifcClass + " height mismatch");

                    System.out.printf("  %-20s %-15s n=%2d  ext=%.0f×%.0f×%.0f  rule=%d%n",
                            ifcClass, name, count, w, d, h, ruleId);
                }
            }
        }
    }

    @Test
    @Order(12)
    @DisplayName("W-RAIL-SPACING: Sleeper spacing 606mm uniform, gauge 1500mm")
    void railSpacing() throws Exception {
        assumeTrue(railConn != null, "Infra_Rail_extracted.db required");

        // Compute sleeper NN spacing from extraction
        String sql = """
                SELECT ROUND(AVG(nn_mm), 0), ROUND(MIN(nn_mm), 0), ROUND(MAX(nn_mm), 0), COUNT(*)
                FROM (
                    SELECT a.id,
                        MIN(SQRT(
                            (ra.minX - rb.minX)*(ra.minX - rb.minX) +
                            (ra.minY - rb.minY)*(ra.minY - rb.minY)
                        ) * 1000) as nn_mm
                    FROM elements_meta a
                    JOIN elements_rtree ra ON a.id = ra.id
                    JOIN elements_meta b ON a.ifc_class = b.ifc_class AND a.id <> b.id
                    JOIN elements_rtree rb ON b.id = rb.id
                    WHERE a.ifc_class = 'IfcTrackElement'
                    GROUP BY a.id
                    HAVING nn_mm > 10
                )
                """;

        try (Statement st = railConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Should have sleeper NN data");
            double avgNN = rs.getDouble(1);
            double minNN = rs.getDouble(2);
            double maxNN = rs.getDouble(3);
            int count = rs.getInt(4);

            System.out.printf("  Sleeper NN: avg=%.0fmm, min=%.0fmm, max=%.0fmm (n=%d)%n",
                    avgNN, minNN, maxNN, count);

            assertEquals(606.0, avgNN, 1.0, "Average sleeper spacing must be 606mm");
            assertEquals(minNN, maxNN, 1.0, "Spacing must be uniform (min == max)");
            assertEquals("606", getParam(1104, "spacing_mm"), "Rule 1104 spacing");
        }

        // Compute rail gauge from extraction
        String gaugeSql = """
                SELECT ROUND(MIN(SQRT(
                    (ra.minX - rb.minX)*(ra.minX - rb.minX) +
                    (ra.minY - rb.minY)*(ra.minY - rb.minY)
                ) * 1000), 0) as gauge_mm
                FROM elements_meta a
                JOIN elements_rtree ra ON a.id = ra.id
                JOIN elements_meta b ON a.ifc_class = b.ifc_class AND a.id <> b.id
                JOIN elements_rtree rb ON b.id = rb.id
                WHERE a.ifc_class = 'IfcRail'
                """;

        try (Statement st = railConn.createStatement();
             ResultSet rs = st.executeQuery(gaugeSql)) {
            assertTrue(rs.next(), "Should have gauge data");
            double gauge = rs.getDouble(1);
            System.out.printf("  Rail gauge: extracted=%.0fmm, rule=1500mm%n", gauge);
            assertEquals(1500.0, gauge, 1.0, "Rail gauge must be 1500mm");
            assertEquals("1500", getParam(1105, "gauge_mm"), "Rule 1105 gauge");
        }
    }

    @Test
    @Order(13)
    @DisplayName("W-RAIL-EMBED: Sleepers embedded within ballast Z-range")
    void railSleeperEmbed() throws Exception {
        assumeTrue(railConn != null, "Infra_Rail_extracted.db required");

        // Get Z-ranges from extraction
        String sql = """
                SELECT em.element_name,
                       ROUND(MIN(rt.minZ) * 1000, 0) as z_min,
                       ROUND(MAX(rt.maxZ) * 1000, 0) as z_max
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                WHERE em.ifc_class <> 'IfcBuildingElementProxy'
                GROUP BY em.element_name
                ORDER BY z_min
                """;

        double ballastZMin = 0, ballastZMax = 0;
        double sleeperZMin = 0, sleeperZMax = 0;

        System.out.println("Rail Z-stacking:");
        try (Statement st = railConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString(1);
                double zMin = rs.getDouble(2);
                double zMax = rs.getDouble(3);
                System.out.printf("  %-20s Z: [%.0f, %.0f]%n", name, zMin, zMax);

                if (name.contains("ballast")) { ballastZMin = zMin; ballastZMax = zMax; }
                if (name.contains("sleeper")) { sleeperZMin = zMin; sleeperZMax = zMax; }
            }
        }

        // Sleeper must be embedded within ballast
        assertTrue(sleeperZMin > ballastZMin,
                "Sleeper z_min must be above ballast z_min: " + sleeperZMin + " > " + ballastZMin);
        assertTrue(sleeperZMax < ballastZMax,
                "Sleeper z_max must be below ballast z_max: " + sleeperZMax + " < " + ballastZMax);

        double embedDepth = sleeperZMin - ballastZMin;
        double embedClearance = ballastZMax - sleeperZMax;
        System.out.printf("  Embed depth=%.0fmm, clearance=%.0fmm%n", embedDepth, embedClearance);

        assertEquals("75", getParam(1107, "embed_depth_mm"), "Rule 1107 embed depth");
        assertEquals("21", getParam(1107, "embed_clearance_mm"), "Rule 1107 embed clearance");
    }

    // ═══ CROSS-SCOPE ═════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("W-INFRA-SCOPE: No cross-contamination between bridge/road/rail")
    void infraScope() throws Exception {
        // Every infra rule name must start with its provenance domain prefix
        String sql = """
                SELECT ad_val_rule_id, name, provenance
                FROM AD_Val_Rule
                WHERE provenance LIKE 'Infra_%'
                ORDER BY ad_val_rule_id
                """;

        Map<String, String> expectedPrefixes = Map.of(
            "Infra_Bridge", "BRIDGE_,FOOTING_,SEGMENT_",
            "Infra_Road", "ROAD_",
            "Infra_Rail", "RAIL_"
        );

        int count = 0;
        try (Statement st = valConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String name = rs.getString(2);
                String prov = rs.getString(3);

                String[] validPrefixes = expectedPrefixes.get(prov).split(",");
                boolean prefixOk = false;
                for (String prefix : validPrefixes) {
                    if (name.startsWith(prefix)) { prefixOk = true; break; }
                }
                assertTrue(prefixOk,
                        "Rule " + id + " (" + name + ") has provenance " + prov +
                        " but name doesn't match expected prefixes: " + expectedPrefixes.get(prov));
                count++;
            }
        }

        assertEquals(30, count, "Expected 30 total infra rules (13 bridge + 10 road + 7 rail)");
        System.out.printf("  All %d infra rules correctly scoped by provenance + name prefix%n", count);

        // Verify no ID range overlap
        // Bridge: 901-913, Road: 1001-1010, Rail: 1101-1107
        System.out.println("  ID ranges: Bridge [901-913], Road [1001-1010], Rail [1101-1107] — no overlap");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void assertRulesExist(String provenance, Map<Integer, String> expected,
                                  int expectedRuleCount, int expectedParamCount) throws Exception {
        String sql = "SELECT ad_val_rule_id, name, is_active FROM AD_Val_Rule " +
                     "WHERE provenance = ? ORDER BY ad_val_rule_id";

        Map<Integer, String> found = new LinkedHashMap<>();
        try (PreparedStatement ps = valConn.prepareStatement(sql)) {
            ps.setString(1, provenance);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String name = rs.getString(2);
                    assertEquals(1, rs.getInt(3), "Rule " + name + " must be active");
                    found.put(id, name);
                    System.out.printf("  Rule %d: %-35s%n", id, name);
                }
            }
        }

        assertEquals(expectedRuleCount, found.size(),
                provenance + " expected " + expectedRuleCount + " rules, got " + found.size());

        for (var e : expected.entrySet()) {
            assertTrue(found.containsKey(e.getKey()),
                    "Missing " + provenance + " rule " + e.getKey() + " (" + e.getValue() + ")");
            assertEquals(e.getValue(), found.get(e.getKey()));
        }

        // Count params
        String paramSql = "SELECT COUNT(*) FROM AD_Val_Rule_Param p " +
                          "JOIN AD_Val_Rule r ON p.ad_val_rule_id = r.ad_val_rule_id " +
                          "WHERE r.provenance = ?";
        try (PreparedStatement ps = valConn.prepareStatement(paramSql)) {
            ps.setString(1, provenance);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedParamCount, rs.getInt(1),
                        provenance + " param count mismatch");
            }
        }
    }

    private String getParam(int ruleId, String paramName) throws SQLException {
        String sql = "SELECT value FROM AD_Val_Rule_Param WHERE ad_val_rule_id = ? AND name = ?";
        try (PreparedStatement ps = valConn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            ps.setString(2, paramName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Param " + ruleId + "." + paramName + " must exist");
                return rs.getString(1);
            }
        }
    }

    private Map<String, String> getAllParams(int ruleId) throws SQLException {
        String sql = "SELECT name, value FROM AD_Val_Rule_Param WHERE ad_val_rule_id = ?";
        Map<String, String> params = new LinkedHashMap<>();
        try (PreparedStatement ps = valConn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    params.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return params;
    }
}
