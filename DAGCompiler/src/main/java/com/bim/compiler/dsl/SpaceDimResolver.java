package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Resolves space dimension contracts from AD tables for compiler use.
 *
 * Provides facade type and ventilation opening rules per space type,
 * so the compiler can apply AD-driven material and opening decisions
 * instead of hardcoded logic.
 *
 * Lazy singleton pattern — loaded once, reused across storeys.
 */
class SpaceDimResolver {

    private static final String DB_PATH = "library/component_library.db";
    private static SpaceDimResolver instance;

    private final Map<String, SpaceDimContract> contracts = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    record SpaceDimContract(
        String spaceType,
        String facadeType,        // OPAQUE, GLASS_CURTAIN
        VentilationSpec ventilation
    ) {}

    record VentilationSpec(
        String type,      // HIGH_WINDOW
        int sillMm,       // e.g. 1800
        int heightMm,     // e.g. 400
        String wall       // "exterior"
    ) {}

    static SpaceDimResolver getInstance() {
        if (instance == null) {
            instance = new SpaceDimResolver();
        }
        return instance;
    }

    private SpaceDimResolver() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            loadContracts(conn);
            loadAliases(conn);
        } catch (SQLException e) {
            System.err.println("[SpaceDimResolver] Warning: Could not load AD tables: " + e.getMessage());
        }
    }

    private void loadContracts(Connection conn) throws SQLException {
        String sql = "SELECT space_type, facade_type, ventilation_opening FROM ad_space_dim WHERE is_active = 1";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String type = rs.getString("space_type");
                String facade = rs.getString("facade_type");
                if (facade == null) facade = "OPAQUE";

                VentilationSpec vent = parseVentilation(rs.getString("ventilation_opening"));
                contracts.put(type, new SpaceDimContract(type, facade, vent));
            }
        }
    }

    private void loadAliases(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT alias, space_type_id FROM ad_space_type_alias")) {
            while (rs.next()) {
                aliases.put(rs.getString("alias").toUpperCase(), rs.getString("space_type_id"));
            }
        }
    }

    /**
     * Resolve the canonical space type, checking aliases.
     */
    private String resolveType(String roomType) {
        if (roomType == null) return null;
        String upper = roomType.toUpperCase();
        if (contracts.containsKey(upper)) return upper;
        String aliased = aliases.get(upper);
        return aliased != null ? aliased : upper;
    }

    /**
     * Returns facade type for a given room type.
     * Returns "OPAQUE" if no contract or no facade_type defined.
     */
    String resolveFacadeType(String roomType) {
        String resolved = resolveType(roomType);
        SpaceDimContract c = contracts.get(resolved);
        return c != null ? c.facadeType() : "OPAQUE";
    }

    /**
     * Returns ventilation spec for a given room type, or null if none defined.
     */
    VentilationSpec resolveVentilation(String roomType) {
        String resolved = resolveType(roomType);
        SpaceDimContract c = contracts.get(resolved);
        return c != null ? c.ventilation() : null;
    }

    /**
     * Simple JSON parser for ventilation_opening column.
     * Expected format: {"type":"HIGH_WINDOW","sill_mm":1800,"height_mm":400,"wall":"exterior"}
     */
    private static VentilationSpec parseVentilation(String json) {
        if (json == null || json.isBlank()) return null;

        String type = extractJsonString(json, "type");
        int sillMm = extractJsonInt(json, "sill_mm", 0);
        int heightMm = extractJsonInt(json, "height_mm", 0);
        String wall = extractJsonString(json, "wall");

        if (type == null) return null;
        return new VentilationSpec(type, sillMm, heightMm, wall);
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private static int extractJsonInt(String json, String key, int defaultVal) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return defaultVal;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
