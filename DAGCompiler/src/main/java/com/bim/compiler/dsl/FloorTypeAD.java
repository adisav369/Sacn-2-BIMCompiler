/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase 122J: Profile-aware finish slab resolver.
 * Reads ad_floor_type_rule + ad_floor_type from component_library.db.
 * Replaces hardcoded getFinishSlabThickness() switch in BuildingWriter.
 *
 * Pattern: same as OpeningBomAD — loads from library, dispatches by (roomType, profile).
 */
class FloorTypeAD {

    // Cache: (roomType_upper + "|" + profile) → thickness in meters, or null
    private final Map<String, FinishFloorEntry> cache = new HashMap<>();
    private boolean loaded = false;

    record FinishFloorEntry(String floorTypeId, String floorName, double thicknessM) {}

    /**
     * Get finish slab thickness for a room type under a given profile.
     * Returns 0 if no rule matches (no finish slab for this room type).
     */
    double getFinishSlabThickness(String roomType, String profile) {
        if (!loaded) load();
        if (roomType == null) return 0;

        String key = roomType.toUpperCase() + "|" + (profile != null ? profile : "");
        FinishFloorEntry entry = cache.get(key);
        if (entry != null) return entry.thicknessM;

        // Try generic fallback (profile IS NULL)
        String fallbackKey = roomType.toUpperCase() + "|";
        entry = cache.get(fallbackKey);
        return entry != null ? entry.thicknessM : 0;
    }

    /**
     * Get finish floor name for a room type under a given profile.
     * Returns null if no rule matches.
     */
    String getFinishFloorName(String roomType, String profile) {
        if (!loaded) load();
        if (roomType == null) return null;

        String key = roomType.toUpperCase() + "|" + (profile != null ? profile : "");
        FinishFloorEntry entry = cache.get(key);
        if (entry != null) return entry.floorName;

        String fallbackKey = roomType.toUpperCase() + "|";
        entry = cache.get(fallbackKey);
        return entry != null ? entry.floorName : null;
    }

    private void load() {
        loaded = true;
        String sql = """
            SELECT r.room_type, r.profile, f.floor_type_id, f.floor_name, f.slab_thickness
            FROM ad_floor_type_rule r
            JOIN ad_floor_type f ON r.floor_type_id = f.floor_type_id
            WHERE r.is_active = 1 AND f.is_active = 1
            ORDER BY r.priority DESC
            """;
        try (Connection libConn = DriverManager.getConnection(
                "jdbc:sqlite:" + System.getProperty("bom.db"));
             Statement stmt = libConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String roomType = rs.getString("room_type").toUpperCase();
                String profile = rs.getString("profile"); // may be null
                String floorTypeId = rs.getString("floor_type_id");
                String floorName = rs.getString("floor_name");
                double thickness = rs.getDouble("slab_thickness");

                String key = roomType + "|" + (profile != null ? profile : "");
                // Higher priority wins (ORDER BY priority DESC, first insert wins)
                cache.putIfAbsent(key, new FinishFloorEntry(floorTypeId, floorName, thickness));
            }
        } catch (SQLException e) {
            System.err.println("[FloorTypeAD] Failed to load floor type rules: " + e.getMessage());
        }
    }
}
