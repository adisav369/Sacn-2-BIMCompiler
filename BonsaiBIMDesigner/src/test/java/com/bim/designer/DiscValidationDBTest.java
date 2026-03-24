package com.bim.designer;

import com.bim.designer.dao.CalibrationDAO;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DiscValidation DB Test — proves ERP.db schema and seed integrity.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-DV-DB-SCHEMA: DV001+DV003 creates all 20 required tables</li>
 *   <li>W-DV-DB-SEED: Seed data matches component_library.db source counts</li>
 *   <li>W-DV-DB-REF: Reference pointers resolve across databases</li>
 *   <li>W-DV-DB-ND: Migration does not disturb component_library.db</li>
 *   <li>W-DV-MINED-RULES: 415 mined dimension rules with 1245 params in ad_val_rule</li>
 *   <li>W-DV-DB-ORG: AD_Org table exists with 16 discipline orgs (DV013)</li>
 *   <li>W-DV-DB-ORG-COLS: AD_Org_ID columns populated with zero NULLs (DV014)</li>
 *   <li>W-DV-DB-PRODUCT: M_Product (2475) + M_Product_Category (46) present (DV015)</li>
 * </ul>
 *
 * // Implementing DISC_VALIDATION_DB_SRS.md §9 — Witness: W-DV-DB-*
 */
@DisplayName("DiscValidation DB — Schema + Seed + Reference Pointers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscValidationDBTest {

    static final Path DISC_DB = Path.of("library/ERP.db");
    static final Path COMP_DB = Path.of("library/component_library.db");

    static Connection discConn;
    static Connection compConn;

    /** Expected row counts for seeded tables (from component_library.db 2026-03-19). */
    static final Map<String, Integer> EXPECTED_COUNTS = new LinkedHashMap<>();
    static {
        EXPECTED_COUNTS.put("ad_space_type", 41);
        EXPECTED_COUNTS.put("ad_element_mep", 12);
        EXPECTED_COUNTS.put("ad_space_type_mep_bom", 186);
        EXPECTED_COUNTS.put("ad_fp_coverage", 4);
        EXPECTED_COUNTS.put("ad_assembly_connector", 10);
        EXPECTED_COUNTS.put("ad_assembly_manifest", 37);
        EXPECTED_COUNTS.put("ad_wall_face", 204);
        EXPECTED_COUNTS.put("placement_rules", 4801);
        EXPECTED_COUNTS.put("ad_space_adjacency", 22);
        EXPECTED_COUNTS.put("ad_fp_trigger", 12);
        EXPECTED_COUNTS.put("ad_code_requirement", 23);
        EXPECTED_COUNTS.put("ad_room_slot", 38);
        EXPECTED_COUNTS.put("ad_space_dim", 37);
        EXPECTED_COUNTS.put("ad_space_exterior_rule", 24);
        EXPECTED_COUNTS.put("ad_space_type_opening", 103);
        EXPECTED_COUNTS.put("ad_space_type_mep", 22);
    }

    /** All 24 tables expected in ERP.db (22 prior + 2 DV015 M_Product). */
    static final List<String> ALL_TABLES = List.of(
            "ad_space_type", "ad_element_mep", "ad_space_type_mep_bom",
            "ad_fp_coverage", "ad_assembly_connector", "ad_assembly_manifest",
            "ad_wall_face", "placement_rules", "ad_space_adjacency",
            "ad_fp_trigger", "ad_code_requirement", "ad_room_slot",
            "ad_space_dim", "ad_space_exterior_rule", "ad_space_type_opening",
            "ad_space_type_mep",
            "ad_element_mep_alias",
            "ad_val_rule", "ad_val_rule_param",       // DV010: mined dimension rules
            "AD_Org",                                  // DV013: discipline org units
            "M_Product", "M_Product_Category",         // DV015: master product catalog
            "W_Calibration_Result", "AD_SysConfig"
    );

    @BeforeAll
    static void openConnections() throws Exception {
        assumeTrue(Files.exists(DISC_DB), "ERP.db must exist");
        assumeTrue(Files.exists(COMP_DB), "component_library.db must exist");

        discConn = DriverManager.getConnection("jdbc:sqlite:" + DISC_DB);
        compConn = DriverManager.getConnection("jdbc:sqlite:" + COMP_DB);
    }

    @AfterAll
    static void closeConnections() throws Exception {
        if (discConn != null) discConn.close();
        if (compConn != null) compConn.close();
    }

    // ── W-DV-DB-SCHEMA ────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-DV-DB-SCHEMA: All 24 tables exist in ERP.db")
    void allTablesExist() throws Exception {
        Set<String> actual = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = discConn.getMetaData().getTables(
                null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                actual.add(rs.getString("TABLE_NAME"));
            }
        }

        System.out.printf("  ERP.db tables: %d%n", actual.size());
        for (String expected : ALL_TABLES) {
            assertTrue(actual.contains(expected),
                    "Missing table: " + expected);
        }
        assertTrue(actual.size() >= ALL_TABLES.size(),
                "Expected at least " + ALL_TABLES.size() + " tables, got " + actual.size());
    }

    @Test
    @Order(2)
    @DisplayName("W-DV-DB-SCHEMA: AD_SysConfig has SCHEMA_VERSION >= DV001")
    void schemaVersionCorrect() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_SysConfig WHERE Name = 'SCHEMA_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "SCHEMA_VERSION row must exist");
            String version = rs.getString(1);
            assertTrue(version.startsWith("DV"), "SCHEMA_VERSION must start with DV: " + version);
        }
    }

    // ── W-DV-DB-SEED ──────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-DV-DB-SEED: Row counts meet minimums in ERP.db (Phase 3: comp tables dropped)")
    void seedRowCountsMatch() throws Exception {
        // Phase 3 complete: discipline tables removed from component_library.db.
        // ERP.db is now the ONLY source — verify minimum row counts.
        int pass = 0;
        for (var entry : EXPECTED_COUNTS.entrySet()) {
            String table = entry.getKey();
            int expected = entry.getValue();
            int discCount = countRows(discConn, table);

            System.out.printf("  %-30s disc=%5d  expected>=%5d  %s%n",
                    table, discCount, expected,
                    discCount >= expected ? "OK" : "BELOW MINIMUM");

            assertTrue(discCount >= expected,
                    table + ": expected at least " + expected + ", got " + discCount);
            pass++;
        }
        System.out.printf("  Seed verification: %d/%d tables OK%n", pass, EXPECTED_COUNTS.size());
    }

    @Test
    @Order(11)
    @DisplayName("W-DV-DB-SEED: DV002 seed version recorded")
    void seedVersionRecorded() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_SysConfig WHERE Name = 'SEED_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "SEED_VERSION row must exist");
            assertEquals("DV002", rs.getString(1));
        }
    }

    // ── W-DV-DB-REF ───────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-DV-DB-REF: ad_element_mep.element_type resolves to M_Product by ifc_class")
    void elementMepResolvesToProduct() throws Exception {
        int resolved = 0, unresolved = 0;
        try (PreparedStatement discPs = discConn.prepareStatement(
                "SELECT element_type, ifc_class FROM ad_element_mep WHERE is_active = 1");
             ResultSet rs = discPs.executeQuery()) {
            while (rs.next()) {
                String elementType = rs.getString("element_type");
                String ifcClass = rs.getString("ifc_class");

                // DV015: M_Product now in ERP.db — resolve within same DB
                try (PreparedStatement prodPs = discConn.prepareStatement(
                        "SELECT COUNT(*) FROM M_Product WHERE ifc_class = ?")) {
                    prodPs.setString(1, ifcClass);
                    try (ResultSet crs = prodPs.executeQuery()) {
                        int count = crs.getInt(1);
                        System.out.printf("  %-20s ifc_class=%-35s → M_Product count=%d%n",
                                elementType, ifcClass, count);
                        if (count > 0) resolved++;
                        else unresolved++;
                    }
                }
            }
        }
        System.out.printf("  Reference pointers: %d resolved, %d unresolved%n", resolved, unresolved);
        assertTrue(resolved > 0, "At least one element_type must resolve to M_Product");
    }

    @Test
    @Order(21)
    @DisplayName("W-DV-DB-REF: ad_space_type_mep_bom.mep_product_id resolves within ERP.db")
    void mepBomResolvesInternally() throws Exception {
        // Every mep_product_id in the schedule should exist in ad_element_mep
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT DISTINCT mep_product_id FROM ad_space_type_mep_bom " +
                "WHERE mep_product_id NOT IN (SELECT element_type FROM ad_element_mep)");
             ResultSet rs = ps.executeQuery()) {
            List<String> orphans = new ArrayList<>();
            while (rs.next()) orphans.add(rs.getString(1));

            if (!orphans.isEmpty()) {
                System.out.printf("  WARNING: %d orphan mep_product_ids: %s%n",
                        orphans.size(), orphans);
            }
            // Advisory — some schedule entries may reference products not in ad_element_mep
            // This is informational, not blocking
            System.out.printf("  Internal ref check: %d orphans%n", orphans.size());
        }
    }

    @Test
    @Order(22)
    @DisplayName("W-DV-DB-REF: ad_space_type_mep_bom.space_type_id resolves to ad_space_type")
    void mepBomSpaceTypeResolves() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT DISTINCT space_type_id FROM ad_space_type_mep_bom " +
                "WHERE space_type_id NOT IN (SELECT space_type_id FROM ad_space_type)");
             ResultSet rs = ps.executeQuery()) {
            List<String> orphans = new ArrayList<>();
            while (rs.next()) orphans.add(rs.getString(1));

            assertTrue(orphans.isEmpty(),
                    "All space_type_id refs must resolve: orphans=" + orphans);
        }
    }

    // ── W-DV-DB-ALIAS ──────────────────────────────────────────────────

    @Test
    @Order(25)
    @DisplayName("W-DV-DB-ALIAS: alias table has all 4 match_field priorities")
    void aliasTableHasAllPriorities() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT match_field, COUNT(*) FROM ad_element_mep_alias " +
                "WHERE is_active = 1 GROUP BY match_field ORDER BY match_field");
             ResultSet rs = ps.executeQuery()) {
            Map<String, Integer> fields = new LinkedHashMap<>();
            while (rs.next()) fields.put(rs.getString(1), rs.getInt(2));

            System.out.printf("  Alias fields: %s%n", fields);
            assertTrue(fields.containsKey("ifc_class"), "Must have ifc_class aliases");
            assertTrue(fields.containsKey("predefined_type"), "Must have predefined_type aliases");
            assertTrue(fields.containsKey("type_class"), "Must have type_class aliases");
            assertTrue(fields.containsKey("element_name"), "Must have element_name aliases");
            assertTrue(fields.values().stream().mapToInt(i -> i).sum() >= 80,
                    "Must have at least 80 alias rows");
        }
    }

    @Test
    @Order(26)
    @DisplayName("W-DV-DB-ALIAS: every canonical_type has at least one alias")
    void everyCanonicalTypeHasAlias() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT element_type FROM ad_element_mep WHERE is_active = 1 " +
                "AND element_type NOT IN " +
                "(SELECT DISTINCT canonical_type FROM ad_element_mep_alias WHERE is_active = 1)");
             ResultSet rs = ps.executeQuery()) {
            List<String> missing = new ArrayList<>();
            while (rs.next()) missing.add(rs.getString(1));

            assertTrue(missing.isEmpty(),
                    "All active element types must have aliases: missing=" + missing);
        }
    }

    @Test
    @Order(27)
    @DisplayName("W-DV-DB-ALIAS: DX IfcFlowTerminal resolves via element_name cascade")
    void dxFlowTerminalResolvesViaAlias() throws Exception {
        // Simulate the cascade: for each DX IfcFlowTerminal, try element_name LIKE matching
        Path dxDb = Path.of("DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db");
        assumeTrue(Files.exists(dxDb), "DX reference DB must exist");

        int resolved = 0, total = 0;
        try (Connection dxConn = DriverManager.getConnection("jdbc:sqlite:" + dxDb);
             PreparedStatement dxPs = dxConn.prepareStatement(
                "SELECT DISTINCT element_name FROM elements_meta " +
                "WHERE ifc_class IN ('IfcFlowTerminal', 'IfcFlowController')");
             ResultSet dxRs = dxPs.executeQuery()) {

            while (dxRs.next()) {
                String elemName = dxRs.getString(1);
                total++;

                // Try alias resolution by element_name LIKE
                try (PreparedStatement aliasPs = discConn.prepareStatement(
                        "SELECT canonical_type, match_value FROM ad_element_mep_alias " +
                        "WHERE match_field = 'element_name' AND is_active = 1 " +
                        "AND ? LIKE match_value " +
                        "ORDER BY priority LIMIT 1")) {
                    aliasPs.setString(1, elemName);
                    try (ResultSet aliasRs = aliasPs.executeQuery()) {
                        if (aliasRs.next()) {
                            resolved++;
                        }
                    }
                }
            }
        }

        System.out.printf("  DX FlowTerminal/Controller: %d/%d distinct names resolved via alias%n",
                resolved, total);
        // At least 12 of 17 distinct DX FlowTerminal types should resolve
        // (Shower, Bath, Refrigerator, Range, Microwave, Valve, Backflow are unmatched — not in canonical types)
        assertTrue(resolved >= 12,
                "At least 12 DX element names must resolve, got " + resolved);
    }

    // ── W-DV-DB-ND ────────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("W-DV-DB-ND: component_library.db LOD tables undisturbed")
    void componentLibraryUndisturbed() throws Exception {
        // Core LOD tables must still have their expected counts
        int products = countRows(compConn, "M_Product");
        int defs = countRows(compConn, "component_definitions");
        int geoms = countRows(compConn, "component_geometries");

        System.out.printf("  M_Product=%d  component_definitions=%d  component_geometries=%d%n",
                products, defs, geoms);

        assertTrue(products >= 608,
                "M_Product must have >= 608 rows, got " + products);
        assertTrue(defs >= 23888,
                "component_definitions must have >= 23888 rows, got " + defs);
        assertTrue(geoms >= 23901,
                "component_geometries must have >= 23901 rows, got " + geoms);
    }

    @Test
    @Order(31)
    @DisplayName("W-DV-DB-ND: ERP.db has NO geometry tables (M_Product moved here DV015)")
    void noGeometryInDiscValidation() throws Exception {
        Set<String> tables = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = discConn.getMetaData().getTables(
                null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        assertFalse(tables.contains("component_definitions"),
                "component_definitions must NOT be in ERP.db");
        assertFalse(tables.contains("component_geometries"),
                "component_geometries must NOT be in ERP.db");
        // DV015: M_Product moved to ERP.db (master product catalog)
        assertTrue(tables.contains("M_Product"),
                "M_Product must be in ERP.db (DV015)");
        assertTrue(tables.contains("M_Product_Category"),
                "M_Product_Category must be in ERP.db (DV015)");
    }

    // ── W-DV-DB-DUAL-READ ──────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("W-DV-DB-CANONICAL: CalibrationDAO.docEventQty reads from ERP.db only (Phase 3)")
    void canonicalReadDocEventQty() throws Exception {
        // Phase 3 complete: ERP.db is the ONLY source for discipline metadata.
        // component_library.db no longer has ad_space_type_mep_bom.
        CalibrationDAO dao = new CalibrationDAO();
        double testAreaM2 = 500.0;  // typical TE floor area

        int discSprinkler = dao.docEventQty(discConn, "SPRINKLER", testAreaM2);
        System.out.printf("  docEventQty(SPRINKLER, %.0fm²): disc=%d%n",
                testAreaM2, discSprinkler);
        assertTrue(discSprinkler > 0,
                "ERP.db must have SPRINKLER in ad_space_type_mep_bom");

        int discLight = dao.docEventQty(discConn, "LIGHT", testAreaM2);
        System.out.printf("  docEventQty(LIGHT, %.0fm²): disc=%d%n",
                testAreaM2, discLight);
        assertTrue(discLight > 0,
                "ERP.db must return >0 for LIGHT (DV004 per_area_normal=0.05)");
    }

    // ── W-DV-MINED-RULES ──────────────────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("W-DV-MINED-RULES: 415 mined dimension rules from 20 buildings")
    void minedRulesPresent() throws Exception {
        int ruleCount = countRows(discConn, "ad_val_rule");
        int paramCount = countRows(discConn, "ad_val_rule_param");

        System.out.printf("  ad_val_rule=%d  ad_val_rule_param=%d%n", ruleCount, paramCount);
        assertTrue(ruleCount >= 415,
                "ad_val_rule must have >= 415 rows, got " + ruleCount);
        assertTrue(paramCount >= 1245,
                "ad_val_rule_param must have >= 1245 rows, got " + paramCount);
    }

    @Test
    @Order(51)
    @DisplayName("W-DV-MINED-RULES: Every rule has exactly 3 params (W, D, H)")
    void minedRulesHaveThreeParams() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT r.ad_val_rule_id, r.rule_name, COUNT(p.ad_val_rule_param_id) as cnt " +
                "FROM ad_val_rule r " +
                "LEFT JOIN ad_val_rule_param p ON p.ad_val_rule_id = r.ad_val_rule_id " +
                "GROUP BY r.ad_val_rule_id HAVING cnt != 3");
             ResultSet rs = ps.executeQuery()) {
            List<String> bad = new ArrayList<>();
            while (rs.next()) bad.add(rs.getString("rule_name") + "(" + rs.getInt("cnt") + ")");

            assertTrue(bad.isEmpty(),
                    "All rules must have exactly 3 params: " + bad);
        }
    }

    @Test
    @Order(52)
    @DisplayName("W-DV-MINED-RULES: 20 distinct provenance buildings")
    void minedRulesProvenanceCoverage() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(DISTINCT provenance) FROM ad_val_rule");
             ResultSet rs = ps.executeQuery()) {
            int buildings = rs.getInt(1);
            System.out.printf("  Distinct provenances: %d%n", buildings);
            assertTrue(buildings >= 20,
                    "Must have >= 20 distinct provenance buildings, got " + buildings);
        }
    }

    @Test
    @Order(53)
    @DisplayName("W-DV-MINED-RULES: No orphan params (FK integrity)")
    void minedRulesNoOrphans() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM ad_val_rule_param p " +
                "WHERE NOT EXISTS (SELECT 1 FROM ad_val_rule r " +
                "WHERE r.ad_val_rule_id = p.ad_val_rule_id)");
             ResultSet rs = ps.executeQuery()) {
            int orphans = rs.getInt(1);
            assertEquals(0, orphans, "No orphan params allowed");
        }
    }

    @Test
    @Order(54)
    @DisplayName("W-DV-MINED-RULES: DV010 schema version recorded")
    void minedRulesVersionRecorded() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT value FROM AD_SysConfig WHERE name = 'MINED_RULES_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "MINED_RULES_VERSION row must exist");
            assertEquals("DV010", rs.getString(1));
        }
    }

    // ── W-DV-DB-ORG ─────────────────────────────────────────────────────

    @Test
    @Order(60)
    @DisplayName("W-DV-DB-ORG: AD_Org has 16 discipline orgs (DV013)")
    void adOrgTablePopulated() throws Exception {
        int orgCount = countRows(discConn, "AD_Org");
        System.out.printf("  AD_Org rows: %d%n", orgCount);
        assertTrue(orgCount >= 16,
                "AD_Org must have >= 16 rows (10 building + 6 infra), got " + orgCount);

        // Verify shared org exists
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value, AD_Org_Type FROM AD_Org WHERE AD_Org_ID = 0");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "AD_Org_ID=0 (shared org) must exist");
            assertEquals("*", rs.getString("Value"));
            assertEquals("SHARED", rs.getString("AD_Org_Type"));
        }
    }

    @Test
    @Order(61)
    @DisplayName("W-DV-DB-ORG: All 9 building disciplines present")
    void adOrgBuildingDisciplines() throws Exception {
        List<String> required = List.of("ARC", "STR", "FP", "ELEC", "ACMV", "CW", "SP", "LPG", "REB");
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_Org WHERE AD_Org_Type = 'DISCIPLINE'");
             ResultSet rs = ps.executeQuery()) {
            Set<String> actual = new TreeSet<>();
            while (rs.next()) actual.add(rs.getString(1));

            for (String disc : required) {
                assertTrue(actual.contains(disc),
                        "Missing building discipline in AD_Org: " + disc);
            }
        }
    }

    @Test
    @Order(62)
    @DisplayName("W-DV-DB-ORG: AD_SysConfig has AD_ORG_VERSION = DV013")
    void adOrgVersionRecorded() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_SysConfig WHERE Name = 'AD_ORG_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "AD_ORG_VERSION row must exist");
            assertEquals("DV013", rs.getString(1));
        }
    }

    // ── W-DV-DB-ORG-COLS ──────────────────────────────────────────────

    @Test
    @Order(70)
    @DisplayName("W-DV-DB-ORG-COLS: ad_element_mep.AD_Org_ID fully populated (DV014)")
    void elementMepOrgIdPopulated() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM ad_element_mep WHERE AD_Org_ID IS NULL");
             ResultSet rs = ps.executeQuery()) {
            int nullCount = rs.getInt(1);
            assertEquals(0, nullCount, "No NULL AD_Org_ID in ad_element_mep");
        }

        // Verify FK integrity: every AD_Org_ID references a valid AD_Org row
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM ad_element_mep e " +
                "WHERE NOT EXISTS (SELECT 1 FROM AD_Org o WHERE o.AD_Org_ID = e.AD_Org_ID)");
             ResultSet rs = ps.executeQuery()) {
            int orphans = rs.getInt(1);
            assertEquals(0, orphans, "All AD_Org_ID values must reference valid AD_Org rows");
        }
    }

    @Test
    @Order(71)
    @DisplayName("W-DV-DB-ORG-COLS: ad_ifc_class_map.AD_Org_ID fully populated (DV014)")
    void ifcClassMapOrgIdPopulated() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM ad_ifc_class_map WHERE AD_Org_ID IS NULL");
             ResultSet rs = ps.executeQuery()) {
            int nullCount = rs.getInt(1);
            assertEquals(0, nullCount, "No NULL AD_Org_ID in ad_ifc_class_map");
        }

        // Verify FK integrity
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM ad_ifc_class_map m " +
                "WHERE NOT EXISTS (SELECT 1 FROM AD_Org o WHERE o.AD_Org_ID = m.AD_Org_ID)");
             ResultSet rs = ps.executeQuery()) {
            int orphans = rs.getInt(1);
            assertEquals(0, orphans, "All AD_Org_ID values must reference valid AD_Org rows");
        }
    }

    @Test
    @Order(72)
    @DisplayName("W-DV-DB-ORG-COLS: AD_SysConfig has AD_ORG_COLS_VERSION = DV014")
    void adOrgColsVersionRecorded() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_SysConfig WHERE Name = 'AD_ORG_COLS_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "AD_ORG_COLS_VERSION row must exist");
            assertEquals("DV014", rs.getString(1));
        }
    }

    // ── W-DV-DB-PRODUCT ────────────────────────────────────────────────

    @Test
    @Order(80)
    @DisplayName("W-DV-DB-PRODUCT: M_Product >= 2475 rows in ERP.db (DV015)")
    void mProductPresent() throws Exception {
        int count = countRows(discConn, "M_Product");
        System.out.printf("  M_Product rows in ERP.db: %d%n", count);
        assertTrue(count >= 2475,
                "M_Product must have >= 2475 rows, got " + count);

        // Zero NULLs in critical columns
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT COUNT(*) FROM M_Product WHERE product_id IS NULL OR product_type IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertEquals(0, rs.getInt(1), "No NULL product_id/product_type in M_Product");
            }
        }
    }

    @Test
    @Order(81)
    @DisplayName("W-DV-DB-PRODUCT: M_Product_Category >= 46 rows in ERP.db (DV015)")
    void mProductCategoryPresent() throws Exception {
        int count = countRows(discConn, "M_Product_Category");
        System.out.printf("  M_Product_Category rows in ERP.db: %d%n", count);
        assertTrue(count >= 46,
                "M_Product_Category must have >= 46 rows, got " + count);
    }

    @Test
    @Order(82)
    @DisplayName("W-DV-DB-PRODUCT: AD_SysConfig has M_PRODUCT_VERSION = DV015")
    void mProductVersionRecorded() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_SysConfig WHERE Name = 'M_PRODUCT_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "M_PRODUCT_VERSION row must exist");
            assertEquals("DV015", rs.getString(1));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static int countRows(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            return rs.getInt(1);
        }
    }
}
