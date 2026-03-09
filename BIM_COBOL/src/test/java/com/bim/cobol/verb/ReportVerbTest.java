package com.bim.cobol.verb;

import com.bim.cobol.*;
import com.bim.ormsandbox.po.MADUser;
import com.bim.ormsandbox.po.MCCampaign;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for Phase H0 — ERP Dimensions + Report Verbs (XLSX).
 *
 * <p>W-H0-1..10: REPORT BOM CATALOG, REPORT PRODUCT CATALOG,
 * REPORT BOM STRUCTURE, C_Campaign DAO, AD_User DAO, VerbRegistry count,
 * VerbLogger detail mode with report payload.
 *
 * <p>All tests use BOM.db directly (read-only verbs, no mutations).
 * Migration H0 must be applied before running.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportVerbTest {

    private static Connection conn;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File("library/BOM.db");
        assertTrue(source.exists(), "library/BOM.db must exist");

        // Use a temp copy so tests remain isolated
        File tempDb = File.createTempFile("report_verb_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        ctx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // ── W-H0-1: REPORT BOM CATALOG returns OK, count > 0 ────────────────

    @Test
    @Order(1)
    void w_h0_1_reportBomCatalogOk() {
        VerbResult<?> result = registry.dispatch(ctx, "REPORT BOM CATALOG");

        assertTrue(result.pass(), "REPORT BOM CATALOG should pass: " + result.summary());
        assertEquals("REPORT BOM CATALOG", result.verb());

        ReportBomCatalogVerb.CatalogPayload p =
            (ReportBomCatalogVerb.CatalogPayload) result.payload();
        assertTrue(p.count() > 0, "should find at least 1 BOM");
    }

    // ── W-H0-2: REPORT BOM CATALOG FILE writes XLSX ─────────────────────

    @Test
    @Order(2)
    void w_h0_2_reportBomCatalogFile() throws Exception {
        File tmpFile = File.createTempFile("bom_catalog_", ".xlsx");
        tmpFile.deleteOnExit();

        VerbResult<?> result = registry.dispatch(ctx,
            "REPORT BOM CATALOG FILE " + tmpFile.getAbsolutePath());

        assertTrue(result.pass(), "REPORT BOM CATALOG FILE should pass: " + result.summary());
        assertTrue(tmpFile.exists(), "XLSX file should exist");
        assertTrue(tmpFile.length() > 100, "XLSX should not be empty");

        // Verify it's a valid XLSX (ZIP magic bytes PK)
        byte[] header = Files.readAllBytes(tmpFile.toPath());
        assertEquals(0x50, header[0] & 0xFF, "XLSX starts with PK (ZIP)");
        assertEquals(0x4B, header[1] & 0xFF, "XLSX starts with PK (ZIP)");
    }

    // ── W-H0-3: REPORT PRODUCT CATALOG returns OK, count > 0 ────────────

    @Test
    @Order(3)
    void w_h0_3_reportProductCatalogOk() {
        VerbResult<?> result = registry.dispatch(ctx, "REPORT PRODUCT CATALOG");

        assertTrue(result.pass(), "REPORT PRODUCT CATALOG should pass: " + result.summary());

        ReportProductCatalogVerb.ProductCatalogPayload p =
            (ReportProductCatalogVerb.ProductCatalogPayload) result.payload();
        assertTrue(p.count() > 0, "should find at least 1 product");
    }

    // ── W-H0-4: REPORT PRODUCT CATALOG FILE writes XLSX with products ───

    @Test
    @Order(4)
    void w_h0_4_reportProductCatalogFile() throws Exception {
        File tmpFile = File.createTempFile("product_catalog_", ".xlsx");
        tmpFile.deleteOnExit();

        VerbResult<?> result = registry.dispatch(ctx,
            "REPORT PRODUCT CATALOG FILE " + tmpFile.getAbsolutePath());

        assertTrue(result.pass(), "REPORT PRODUCT CATALOG FILE should pass: " + result.summary());
        assertTrue(tmpFile.exists(), "XLSX file should exist");
        assertTrue(tmpFile.length() > 100, "XLSX should have product data");
    }

    // ── W-H0-5: REPORT BOM STRUCTURE EB_SH returns OK, 55+ rows ────────

    @Test
    @Order(5)
    void w_h0_5_reportBomStructureOk() {
        VerbResult<?> result = registry.dispatch(ctx, "REPORT BOM STRUCTURE EB_SH");

        assertTrue(result.pass(), "REPORT BOM STRUCTURE should pass: " + result.summary());

        ReportBomStructureVerb.StructurePayload p =
            (ReportBomStructureVerb.StructurePayload) result.payload();
        assertEquals("EB_SH", p.bomId());
        assertTrue(p.rowCount() >= 55,
            "EB_SH should have at least 55 rows (recursive), got " + p.rowCount());
    }

    // ── W-H0-6: REPORT BOM STRUCTURE EB_SH FILE writes XLSX ────────────

    @Test
    @Order(6)
    void w_h0_6_reportBomStructureFile() throws Exception {
        File tmpFile = File.createTempFile("bom_structure_", ".xlsx");
        tmpFile.deleteOnExit();

        VerbResult<?> result = registry.dispatch(ctx,
            "REPORT BOM STRUCTURE EB_SH FILE " + tmpFile.getAbsolutePath());

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertTrue(tmpFile.exists(), "XLSX file should exist");
        assertTrue(tmpFile.length() > 100, "XLSX should have structure data");

        // Verify resolved product names appear in payload
        ReportBomStructureVerb.StructurePayload p =
            (ReportBomStructureVerb.StructurePayload) result.payload();
        boolean hasProductName = p.rows().stream()
            .anyMatch(r -> r.productName() != null && !r.productName().isEmpty());
        assertTrue(hasProductName, "at least one row should have resolved product name");
    }

    // ── W-H0-6b: Multi-sheet workbook (SH + DX) ──────────────────────────

    @Test
    @Order(61)
    void w_h0_6b_multisheetBook() throws Exception {
        File tmpFile = File.createTempFile("bom_book_", ".xlsx");
        tmpFile.deleteOnExit();

        VerbResult<?> result = registry.dispatch(ctx,
            "REPORT BOM STRUCTURE EB_SH EB_DX FILE " + tmpFile.getAbsolutePath());

        assertTrue(result.pass(), "multi-sheet should pass: " + result.summary());
        assertTrue(tmpFile.exists(), "XLSX file should exist");
        // Multi-sheet: SH rows + DX rows combined
        ReportBomStructureVerb.StructurePayload p =
            (ReportBomStructureVerb.StructurePayload) result.payload();
        assertTrue(p.rowCount() >= 55, "at least SH rows present");
        assertTrue(result.summary().contains("EB_SH+EB_DX"), "summary shows both BOMs");
    }

    // ── W-H0-7: C_Campaign DAO ──────────────────────────────────────────

    @Test
    @Order(7)
    void w_h0_7_ccampaignDao() throws Exception {
        MCCampaign scan = MCCampaign.get(conn, "SCAN");
        assertNotNull(scan, "SCAN campaign should exist");
        assertEquals("Scandinavian", scan.getName());
        assertEquals("D", scan.getEntityType(), "seed data entity_type = D");

        List<MCCampaign> all = MCCampaign.getAll(conn);
        assertEquals(4, all.size(), "4 seeded campaigns");
    }

    // ── W-H0-8: AD_User DAO ────────────────────────────────────────────

    @Test
    @Order(8)
    void w_h0_8_adUserDao() throws Exception {
        MADUser system = MADUser.get(conn, 100);
        assertNotNull(system, "System user (ID=100) should exist");
        assertEquals("System", system.getName());
        assertEquals("D", system.getEntityType(), "seed data entity_type = D");
    }

    // ── W-H0-9: VerbRegistry size = 41 ─────────────────────────────────

    @Test
    @Order(9)
    void w_h0_9_registryCount() {
        assertEquals(53, registry.size(),
            "41 prior + 11 L2/L3/L4 + HELLO WORLD = 53");
    }

    // ── W-H0-10: VerbLogger DETAIL mode shows report payload fields ─────

    @Test
    @Order(10)
    void w_h0_10_verbLoggerDetailReport() {
        List<String> lines = new ArrayList<>();
        VerbLogger log = VerbLogger.detail(lines::add);

        VerbResult<?> result = registry.dispatch(ctx, "REPORT BOM CATALOG");
        log.log(result);

        assertTrue(lines.size() > 1, "detail mode should have header + fields");
        assertTrue(lines.get(0).contains("[OK]"));
        boolean hasCount = lines.stream().anyMatch(l -> l.contains("count"));
        assertTrue(hasCount, "detail should show count field from CatalogPayload");
    }
}
