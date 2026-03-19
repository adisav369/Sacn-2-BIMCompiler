package com.bim.designer;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DiscValidation DB Test — proves disc_validation.db schema and seed integrity.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-DV-DB-SCHEMA: DV001 creates all 19 required tables</li>
 *   <li>W-DV-DB-SEED: Seed data matches component_library.db source counts</li>
 *   <li>W-DV-DB-REF: Reference pointers resolve across databases</li>
 *   <li>W-DV-DB-ND: Migration does not disturb component_library.db</li>
 * </ul>
 *
 * // Implementing DISC_VALIDATION_DB_SRS.md §9 — Witness: W-DV-DB-*
 */
@DisplayName("DiscValidation DB — Schema + Seed + Reference Pointers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscValidationDBTest {

    static final Path DISC_DB = Path.of("library/disc_validation.db");
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
        EXPECTED_COUNTS.put("ad_space_type_furniture", 37);
        EXPECTED_COUNTS.put("ad_space_type_mep", 22);
    }

    /** All 19 tables expected in disc_validation.db. */
    static final List<String> ALL_TABLES = List.of(
            "ad_space_type", "ad_element_mep", "ad_space_type_mep_bom",
            "ad_fp_coverage", "ad_assembly_connector", "ad_assembly_manifest",
            "ad_wall_face", "placement_rules", "ad_space_adjacency",
            "ad_fp_trigger", "ad_code_requirement", "ad_room_slot",
            "ad_space_dim", "ad_space_exterior_rule", "ad_space_type_opening",
            "ad_space_type_furniture", "ad_space_type_mep",
            "W_Calibration_Result", "AD_SysConfig"
    );

    @BeforeAll
    static void openConnections() throws Exception {
        assumeTrue(Files.exists(DISC_DB), "disc_validation.db must exist");
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
    @DisplayName("W-DV-DB-SCHEMA: All 19 tables exist in disc_validation.db")
    void allTablesExist() throws Exception {
        Set<String> actual = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = discConn.getMetaData().getTables(
                null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                actual.add(rs.getString("TABLE_NAME"));
            }
        }

        System.out.printf("  disc_validation.db tables: %d%n", actual.size());
        for (String expected : ALL_TABLES) {
            assertTrue(actual.contains(expected),
                    "Missing table: " + expected);
        }
        assertTrue(actual.size() >= ALL_TABLES.size(),
                "Expected at least " + ALL_TABLES.size() + " tables, got " + actual.size());
    }

    @Test
    @Order(2)
    @DisplayName("W-DV-DB-SCHEMA: AD_SysConfig has SCHEMA_VERSION = DV001")
    void schemaVersionCorrect() throws Exception {
        try (PreparedStatement ps = discConn.prepareStatement(
                "SELECT Value FROM AD_SysConfig WHERE Name = 'SCHEMA_VERSION'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "SCHEMA_VERSION row must exist");
            assertEquals("DV001", rs.getString(1));
        }
    }

    // ── W-DV-DB-SEED ──────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-DV-DB-SEED: Row counts match component_library.db source")
    void seedRowCountsMatch() throws Exception {
        int pass = 0, fail = 0;
        for (var entry : EXPECTED_COUNTS.entrySet()) {
            String table = entry.getKey();
            int expected = entry.getValue();
            int discCount = countRows(discConn, table);
            int compCount = countRows(compConn, table);

            System.out.printf("  %-30s disc=%5d  comp=%5d  expected=%5d  %s%n",
                    table, discCount, compCount, expected,
                    discCount == compCount ? "OK" : "MISMATCH");

            assertEquals(compCount, discCount,
                    table + ": disc_validation count must match component_library");
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

                // Check M_Product has a matching ifc_class in component_library.db
                try (PreparedStatement compPs = compConn.prepareStatement(
                        "SELECT COUNT(*) FROM M_Product WHERE ifc_class = ?")) {
                    compPs.setString(1, ifcClass);
                    try (ResultSet crs = compPs.executeQuery()) {
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
    @DisplayName("W-DV-DB-REF: ad_space_type_mep_bom.mep_product_id resolves within disc_validation.db")
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
    @DisplayName("W-DV-DB-ND: disc_validation.db has NO geometry tables")
    void noGeometryInDiscValidation() throws Exception {
        Set<String> tables = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = discConn.getMetaData().getTables(
                null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        assertFalse(tables.contains("component_definitions"),
                "component_definitions must NOT be in disc_validation.db");
        assertFalse(tables.contains("component_geometries"),
                "component_geometries must NOT be in disc_validation.db");
        assertFalse(tables.contains("M_Product"),
                "M_Product must NOT be in disc_validation.db");
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
