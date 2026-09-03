package com.bim.backoffice;

import com.bim.backoffice.print.PrintConfig;
import com.bim.backoffice.print.PrintConfig.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-PRINT-CONFIG-1: Print configurator — AD_PrintFormat pattern.
 *
 * <p>Tests the back-office print chooser that lets users tick which output
 * database tables to include in a print job.
 */
class PrintConfigTest {

    private Connection configConn;
    private Connection outputConn;

    @BeforeEach
    void setUp() throws Exception {
        configConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        PrintConfig.initSchema(configConn);

        // Simulate a compiled output DB with typical tables
        outputConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = outputConn.createStatement()) {
            stmt.execute("CREATE TABLE elements_meta (id INTEGER PRIMARY KEY, guid TEXT)");
            stmt.execute("CREATE TABLE element_instances (guid TEXT, geometry_hash TEXT)");
            stmt.execute("CREATE TABLE simple_qto (id INTEGER, discipline TEXT, ifc_class TEXT)");
            stmt.execute("CREATE TABLE building_summary (key TEXT, value TEXT)");
            stmt.execute("CREATE TABLE c_order (order_id TEXT)");
            stmt.execute("CREATE TABLE c_orderline (line_id TEXT)");
            stmt.execute("CREATE TABLE base_geometries (geometry_hash TEXT, vertices BLOB)");
            stmt.execute("CREATE TABLE surface_styles (style_id TEXT)");
            stmt.execute("CREATE TABLE area_by_storey (storey TEXT, area REAL)");
            stmt.execute("CREATE TABLE spatial_structure (guid TEXT, type TEXT)");
            // Internal tables that should be filtered
            stmt.execute("CREATE TABLE elements_rtree (id INTEGER, minX REAL, maxX REAL)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (configConn != null) configConn.close();
        if (outputConn != null) outputConn.close();
    }

    @Test
    @DisplayName("W-PRINT-DISCOVER: discoverOutputTables returns categorized items, filters rtree")
    void discoverOutputTables() throws Exception {
        List<PrintItem> items = PrintConfig.discoverOutputTables(outputConn);

        // Should NOT include rtree internal tables
        assertTrue(items.stream().noneMatch(i -> i.tableName().contains("rtree")),
            "rtree tables should be filtered");

        // Should include the 10 real tables
        assertEquals(10, items.size(), "10 output tables discovered");

        // Categories assigned
        PrintItem summary = items.stream()
            .filter(i -> i.tableName().equals("building_summary")).findFirst().orElseThrow();
        assertEquals("SUMMARY", summary.category());

        PrintItem qto = items.stream()
            .filter(i -> i.tableName().equals("simple_qto")).findFirst().orElseThrow();
        assertEquals("QUANTITY", qto.category());

        PrintItem geom = items.stream()
            .filter(i -> i.tableName().equals("base_geometries")).findFirst().orElseThrow();
        assertEquals("GEOMETRY", geom.category());
    }

    @Test
    @DisplayName("W-PRINT-DEFAULTS: default items include summary + quantities + order")
    void defaultInclusions() throws Exception {
        List<PrintItem> items = PrintConfig.discoverOutputTables(outputConn);
        List<String> defaults = items.stream()
            .filter(PrintItem::includedByDefault)
            .map(PrintItem::tableName)
            .toList();

        assertTrue(defaults.contains("building_summary"), "summary is default");
        assertTrue(defaults.contains("simple_qto"), "qto is default");
        assertTrue(defaults.contains("c_order"), "order is default");
        assertTrue(defaults.contains("elements_meta"), "meta is default");
        assertFalse(defaults.contains("base_geometries"), "geometry NOT default");
    }

    @Test
    @DisplayName("W-PRINT-SAVE-LOAD: save and load a print format")
    void saveAndLoadFormat() throws Exception {
        PrintFormat format = new PrintFormat(
            "PF_001", "BUILDING_SH", "Standard Print",
            List.of("building_summary", "simple_qto", "c_order", "c_orderline"));

        PrintConfig.saveFormat(configConn, format);

        PrintFormat loaded = PrintConfig.loadFormat(configConn, "PF_001");
        assertNotNull(loaded);
        assertEquals("BUILDING_SH", loaded.buildingId());
        assertEquals("Standard Print", loaded.formatName());
        assertEquals(4, loaded.includedTables().size());
        assertTrue(loaded.includedTables().contains("building_summary"));
        assertTrue(loaded.includedTables().contains("c_orderline"));
    }

    @Test
    @DisplayName("W-PRINT-LIST: list all formats for a building")
    void listFormatsForBuilding() throws Exception {
        PrintConfig.saveFormat(configConn, new PrintFormat(
            "PF_001", "BUILDING_SH", "Full Report",
            List.of("building_summary", "simple_qto", "elements_meta")));
        PrintConfig.saveFormat(configConn, new PrintFormat(
            "PF_002", "BUILDING_SH", "Cost Only",
            List.of("simple_qto", "c_order")));
        PrintConfig.saveFormat(configConn, new PrintFormat(
            "PF_003", "BUILDING_DX", "DX Report",
            List.of("building_summary")));

        List<PrintFormat> shFormats = PrintConfig.listFormats(configConn, "BUILDING_SH");
        assertEquals(2, shFormats.size(), "2 formats for SH");

        List<PrintFormat> dxFormats = PrintConfig.listFormats(configConn, "BUILDING_DX");
        assertEquals(1, dxFormats.size(), "1 format for DX");
    }

    @Test
    @DisplayName("W-PRINT-UPDATE: updating a format replaces items")
    void updateFormat() throws Exception {
        PrintConfig.saveFormat(configConn, new PrintFormat(
            "PF_001", "BUILDING_SH", "Draft",
            List.of("building_summary")));

        // Update with more items
        PrintConfig.saveFormat(configConn, new PrintFormat(
            "PF_001", "BUILDING_SH", "Final",
            List.of("building_summary", "simple_qto", "c_order")));

        PrintFormat loaded = PrintConfig.loadFormat(configConn, "PF_001");
        assertEquals("Final", loaded.formatName());
        assertEquals(3, loaded.includedTables().size(), "items replaced, not appended");
    }
}
