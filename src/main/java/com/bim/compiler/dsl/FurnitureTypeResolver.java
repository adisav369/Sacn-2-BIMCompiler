package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase 108B: Resolves furniture routing rules from ad_space_type_furniture.
 * Lazy singleton — loads once from component_library.db on first access.
 */
class FurnitureTypeResolver {

    private static final String LIB_PATH = "library/component_library.db";

    record FurnitureRule(String bomId, String fallback) {
        boolean isNone() { return bomId == null && "NONE".equals(fallback); }
    }

    private static final FurnitureRule DEFAULT_NONE = new FurnitureRule(null, "NONE");

    private final Map<String, FurnitureRule> rules = new HashMap<>();
    private boolean loaded = false;

    FurnitureRule resolve(String spaceType) {
        ensureLoaded();
        return rules.getOrDefault(spaceType.toUpperCase(), DEFAULT_NONE);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT space_type_id, furniture_bom_id, fallback_rule " +
                 "FROM ad_space_type_furniture WHERE is_active = 1")) {
            while (rs.next()) {
                rules.put(rs.getString("space_type_id"),
                    new FurnitureRule(
                        rs.getString("furniture_bom_id"),
                        rs.getString("fallback_rule")));
            }
            System.out.printf("[FURNITURE-TYPE] Loaded %d furniture routing rules%n", rules.size());
        } catch (SQLException e) {
            System.err.println("[FURNITURE-TYPE] Failed to load: " + e.getMessage());
        }
    }
}
