package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase A: Metadata-driven slab specification resolver.
 * Reads ad_slab_spec from component_library.db for per-building slab extends and thicknesses.
 * Pattern: same lazy-load + cache as FloorTypeAD, OpeningBomAD.
 */
class SlabSpecAD {

    record SlabEntry(double extendX, double extendY, Double thickness,
                     String slabName, Double ceilingZ) {}

    // Cache: "building_type|slab_role" → entry
    private final Map<String, SlabEntry> cache = new HashMap<>();
    private boolean loaded = false;

    /**
     * Get slab spec for a building type and role (FOUNDATION, FLOOR, CEILING).
     * Returns null if no metadata entry exists.
     */
    SlabEntry get(String buildingType, String slabRole) {
        if (!loaded) load();
        if (buildingType == null || slabRole == null) return null;
        // Exact match first
        SlabEntry entry = cache.get(buildingType + "|" + slabRole);
        if (entry != null) return entry;
        // Phase A: Prefix fallback for multi-unit sub-buildings (e.g. "Ifc2x3_Duplex_A" → "Ifc2x3_Duplex")
        int lastUnderscore = buildingType.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String parent = buildingType.substring(0, lastUnderscore);
            return cache.get(parent + "|" + slabRole);
        }
        return null;
    }

    private void load() {
        loaded = true;
        String sql = """
            SELECT building_type, slab_role, extend_x_m, extend_y_m,
                   thickness_m, slab_name, ceiling_z_m
            FROM ad_slab_spec
            WHERE is_active = 1
            """;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:library/BOM.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String bt = rs.getString("building_type");
                String role = rs.getString("slab_role");
                double ex = rs.getDouble("extend_x_m");
                double ey = rs.getDouble("extend_y_m");
                Double t = rs.getObject("thickness_m") != null ? rs.getDouble("thickness_m") : null;
                String name = rs.getString("slab_name");
                Double cz = rs.getObject("ceiling_z_m") != null ? rs.getDouble("ceiling_z_m") : null;
                cache.put(bt + "|" + role, new SlabEntry(ex, ey, t, name, cz));
            }
        } catch (SQLException e) {
            System.err.println("[SlabSpecAD] Failed to load slab specs: " + e.getMessage());
        }
    }
}
