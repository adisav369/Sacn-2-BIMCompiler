package com.bim.backoffice.print;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Print configurator — AD_PrintFormat pattern (iDempiere).
 *
 * <p>Users tick which output database tables/views to include in a print job.
 * Each building gets an AD_PrintFormat row; each selected table gets an
 * AD_PrintFormatItem row. The chooser reads available tables from the compiled
 * output DB and presents them as a checklist.
 *
 * <p>Multi-project: the back-office view can configure print across multiple
 * buildings simultaneously. The Bonsai client calls this for the active project.
 *
 * // Implementing BACK_OFFICE_SRS.md §1 — Witness: W-PRINT-CONFIG-1
 */
public class PrintConfig {

    // ── Records ──────────────────────────────────────────────────────────

    /** One configurable output table/view that can be included in a print. */
    public record PrintItem(String tableName, String displayName,
                            String category, boolean includedByDefault) {}

    /** A saved print format — which items are ticked for a building. */
    public record PrintFormat(String formatId, String buildingId,
                              String formatName, List<String> includedTables) {}

    /** Available output for one building — what CAN be printed. */
    public record OutputInventory(String buildingId, String buildingName,
                                  String outputDbPath, List<PrintItem> items) {}

    // ── Schema (AD_PrintFormat + AD_PrintFormatItem) ─────────────────────

    /** Create print config tables. Safe to call repeatedly. */
    public static void initSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS AD_PrintFormat (
                    format_id     TEXT PRIMARY KEY,
                    building_id   TEXT NOT NULL,
                    format_name   TEXT NOT NULL,
                    is_default    INTEGER DEFAULT 0,
                    created       TEXT DEFAULT (datetime('now')),
                    updated       TEXT DEFAULT (datetime('now'))
                )""");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS AD_PrintFormatItem (
                    format_id     TEXT NOT NULL,
                    table_name    TEXT NOT NULL,
                    sequence      INTEGER DEFAULT 100,
                    is_active     INTEGER DEFAULT 1,
                    display_name  TEXT,
                    PRIMARY KEY (format_id, table_name),
                    FOREIGN KEY (format_id) REFERENCES AD_PrintFormat(format_id)
                )""");
        }
    }

    // ── Inventory: discover what's in an output DB ────────────────────────

    /**
     * Scan a compiled output database and return the available tables as
     * print items. Groups by category (spatial, quantity, cost, meta).
     */
    public static List<PrintItem> discoverOutputTables(Connection outputConn)
            throws SQLException {
        List<PrintItem> items = new ArrayList<>();
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name.startsWith("sqlite_") || name.endsWith("_rtree")
                        || name.endsWith("_rtree_node") || name.endsWith("_rtree_parent")
                        || name.endsWith("_rtree_rowid")) {
                    continue;  // skip internal/rtree tables
                }
                String category = categorize(name);
                boolean defaultIncluded = isDefaultIncluded(name);
                String display = humanize(name);
                items.add(new PrintItem(name, display, category, defaultIncluded));
            }
        }
        return items;
    }

    // ── Save/Load print format ──────────────────────────────────────────

    /** Save a print format (insert or replace). */
    public static void saveFormat(Connection configConn, PrintFormat format)
            throws SQLException {
        try (PreparedStatement ps = configConn.prepareStatement("""
                INSERT OR REPLACE INTO AD_PrintFormat
                (format_id, building_id, format_name, updated)
                VALUES (?, ?, ?, datetime('now'))""")) {
            ps.setString(1, format.formatId());
            ps.setString(2, format.buildingId());
            ps.setString(3, format.formatName());
            ps.executeUpdate();
        }

        // Clear old items, insert new
        try (PreparedStatement del = configConn.prepareStatement(
                "DELETE FROM AD_PrintFormatItem WHERE format_id = ?")) {
            del.setString(1, format.formatId());
            del.executeUpdate();
        }

        try (PreparedStatement ins = configConn.prepareStatement("""
                INSERT INTO AD_PrintFormatItem (format_id, table_name, sequence, is_active)
                VALUES (?, ?, ?, 1)""")) {
            int seq = 10;
            for (String table : format.includedTables()) {
                ins.setString(1, format.formatId());
                ins.setString(2, table);
                ins.setInt(3, seq);
                ins.addBatch();
                seq += 10;
            }
            ins.executeBatch();
        }
    }

    /** Load a print format by ID. Returns null if not found. */
    public static PrintFormat loadFormat(Connection configConn, String formatId)
            throws SQLException {
        String buildingId = null, name = null;
        try (PreparedStatement ps = configConn.prepareStatement(
                "SELECT building_id, format_name FROM AD_PrintFormat WHERE format_id = ?")) {
            ps.setString(1, formatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                buildingId = rs.getString(1);
                name = rs.getString(2);
            }
        }
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = configConn.prepareStatement(
                "SELECT table_name FROM AD_PrintFormatItem WHERE format_id = ? AND is_active = 1 ORDER BY sequence")) {
            ps.setString(1, formatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
        }
        return new PrintFormat(formatId, buildingId, name, tables);
    }

    /** List all print formats for a building. */
    public static List<PrintFormat> listFormats(Connection configConn, String buildingId)
            throws SQLException {
        List<PrintFormat> formats = new ArrayList<>();
        try (PreparedStatement ps = configConn.prepareStatement(
                "SELECT format_id, format_name FROM AD_PrintFormat WHERE building_id = ? ORDER BY format_name")) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fid = rs.getString(1);
                    PrintFormat full = loadFormat(configConn, fid);
                    if (full != null) formats.add(full);
                }
            }
        }
        return formats;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static String categorize(String tableName) {
        return switch (tableName) {
            case "elements_meta", "element_instances", "spatial_structure",
                 "rel_contained_in_space" -> "SPATIAL";
            case "simple_qto", "area_by_storey", "area_by_type",
                 "room_areas" -> "QUANTITY";
            case "base_geometries", "surface_styles", "material_layers" -> "GEOMETRY";
            case "c_order", "c_orderline", "PP_Order_Node",
                 "PP_Order_NodeProduct" -> "ORDER";
            case "building_summary" -> "SUMMARY";
            default -> "OTHER";
        };
    }

    private static boolean isDefaultIncluded(String tableName) {
        // Default print includes summary + quantities + order data
        return switch (tableName) {
            case "building_summary", "simple_qto", "area_by_storey",
                 "c_order", "c_orderline", "elements_meta" -> true;
            default -> false;
        };
    }

    private static String humanize(String tableName) {
        return tableName.replace('_', ' ')
                .substring(0, 1).toUpperCase()
                + tableName.replace('_', ' ').substring(1);
    }
}
