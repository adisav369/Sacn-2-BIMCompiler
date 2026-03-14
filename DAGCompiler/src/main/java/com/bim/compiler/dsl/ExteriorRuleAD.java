package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase RM/B1: Wires ad_space_exterior_rule metadata.
 * Determines which room types require exterior walls, and with what facade preference.
 * Replaces hardcoded TOILET/KITCHEN checks for exterior wall augmentation.
 *
 * Lazy singleton — loaded once, reused across storeys.
 */
class ExteriorRuleAD {

    private static final String DB_PATH = System.getProperty("bom.db");
    private static ExteriorRuleAD instance;

    record ExteriorRule(
        String spaceTypeId,
        boolean requiresExterior,
        String preferredExterior,    // ANY, SOUTH, NORTH, STREET_FACING
        double minExteriorRatio,     // 0-1
        String facadePreference      // GLASS_CURTAIN, OPAQUE, MIXED
    ) {}

    private final Map<String, ExteriorRule> rules = new HashMap<>();
    private boolean loaded = false;

    static synchronized ExteriorRuleAD getInstance() {
        if (instance == null) {
            instance = new ExteriorRuleAD();
        }
        return instance;
    }

    /**
     * Check if a room type requires exterior wall exposure.
     */
    boolean requiresExterior(String roomType) {
        ensureLoaded();
        if (roomType == null) return false;
        ExteriorRule rule = rules.get(roomType.toUpperCase());
        return rule != null && rule.requiresExterior();
    }

    /**
     * Get the facade preference for a room type (GLASS_CURTAIN, OPAQUE, MIXED).
     * Returns "OPAQUE" if not defined.
     */
    String facadePreference(String roomType) {
        ensureLoaded();
        if (roomType == null) return "OPAQUE";
        ExteriorRule rule = rules.get(roomType.toUpperCase());
        return rule != null && rule.facadePreference() != null ? rule.facadePreference() : "OPAQUE";
    }

    /**
     * Get the full exterior rule for a room type, or null.
     */
    ExteriorRule getRule(String roomType) {
        ensureLoaded();
        if (roomType == null) return null;
        return rules.get(roomType.toUpperCase());
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        String sql = """
            SELECT space_type_id, requires_exterior, preferred_exterior,
                   min_exterior_ratio, facade_preference
            FROM ad_space_exterior_rule
            WHERE is_active = 1
            """;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("space_type_id");
                ExteriorRule rule = new ExteriorRule(
                    id,
                    rs.getInt("requires_exterior") == 1,
                    rs.getString("preferred_exterior"),
                    rs.getDouble("min_exterior_ratio"),
                    rs.getString("facade_preference")
                );
                rules.put(id, rule);
            }
            System.out.printf("[ExteriorRuleAD] Loaded %d exterior rules%n", rules.size());
        } catch (SQLException e) {
            System.err.println("[ExteriorRuleAD] Failed to load: " + e.getMessage());
        }
    }
}
