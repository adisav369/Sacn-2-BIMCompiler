/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 56B: Query placement rules from AD table.
 * Replaces hardcoded values in BuildingCompiler with metadata-driven parameters.
 *
 * Usage:
 *   PlacementRuleAD rules = new PlacementRuleAD(System.getProperty("bom.db"));
 *   double partyWallThickness = rules.get("PARTY_WALL");  // 0.25m
 *   double sillHeight = rules.get("WINDOW_SILL");         // 0.9m
 */
public class PlacementRuleAD {

    private final String dbPath;
    private final Map<String, Double> cache = new HashMap<>();

    // Default values if DB query fails
    private static final Map<String, Double> DEFAULTS = Map.of(
        "PARTY_WALL", 0.25,
        "LANDING_SLAB", 0.15,
        "WINDOW_SILL", 0.9,
        "CEILING_OFFSET", 0.05,
        "DOOR_DEFAULT_WIDTH", 0.9,
        "WINDOW_DEFAULT_WIDTH", 1.2
    );

    public PlacementRuleAD(String dbPath) {
        this.dbPath = dbPath;
        loadCache();
    }

    private void loadCache() {
        String sql = "SELECT rule_id, height_m FROM ad_placement_rule";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String ruleId = rs.getString("rule_id");
                double value = rs.getDouble("height_m");
                if (!rs.wasNull()) {
                    cache.put(ruleId, value);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AD] PlacementRuleAD load error: " + e.getMessage());
        }
    }

    /**
     * Get placement rule value by ID.
     * Returns cached value, falls back to default if not found.
     */
    public double get(String ruleId) {
        return cache.getOrDefault(ruleId, DEFAULTS.getOrDefault(ruleId, 0.0));
    }

    /**
     * Get placement rule value with explicit default.
     */
    public double get(String ruleId, double defaultValue) {
        return cache.getOrDefault(ruleId, defaultValue);
    }

    // Convenience methods for common parameters
    public double partyWallThickness() { return get("PARTY_WALL"); }
    public double landingThickness() { return get("LANDING_SLAB"); }
    public double windowSillHeight() { return get("WINDOW_SILL"); }
    public double ceilingOffset() { return get("CEILING_OFFSET"); }
    public double defaultDoorWidth() { return get("DOOR_DEFAULT_WIDTH"); }
    public double defaultWindowWidth() { return get("WINDOW_DEFAULT_WIDTH"); }
}
