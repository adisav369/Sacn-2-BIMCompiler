package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase B1: Metadata-driven element placement resolver.
 * Reads ad_element_placement from component_library.db.
 * Each row = one element to emit at extracted reference coordinates.
 *
 * Pattern: same lazy-load + cache as SlabSpecAD, FloorTypeAD.
 * The metadata IS the production list — compose functions read positions
 * from here instead of computing them from grid/solver.
 */
class PlacementAD {

    record Placement(
        String buildingType,
        String storey,
        String ifcClass,
        String elementRef,
        int ordinal,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ,
        String orientation,
        String discipline,
        String materialName,
        String materialRgba
    ) {
        double cx() { return (minX + maxX) / 2; }
        double cy() { return (minY + maxY) / 2; }
        double cz() { return (minZ + maxZ) / 2; }
        double dx() { return maxX - minX; }
        double dy() { return maxY - minY; }
        double dz() { return maxZ - minZ; }
    }

    // Cache: "building_type" → list of all placements
    private final Map<String, List<Placement>> cache = new HashMap<>();
    private boolean loaded = false;

    /**
     * Check if placement metadata exists for a building type.
     */
    boolean hasPlacement(String buildingType) {
        if (!loaded) load();
        return cache.containsKey(buildingType);
    }

    /**
     * Get all placements for a building type, storey, and IFC class.
     * Returns empty list if no metadata exists.
     */
    List<Placement> get(String buildingType, String storey, String ifcClass) {
        if (!loaded) load();
        List<Placement> all = cache.getOrDefault(buildingType, List.of());
        List<Placement> result = new ArrayList<>();
        for (Placement p : all) {
            if (p.storey().equals(storey) && p.ifcClass().equals(ifcClass)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Get all placements for a building type and storey (all classes).
     */
    List<Placement> getForStorey(String buildingType, String storey) {
        if (!loaded) load();
        List<Placement> all = cache.getOrDefault(buildingType, List.of());
        List<Placement> result = new ArrayList<>();
        for (Placement p : all) {
            if (p.storey().equals(storey)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Get all placements for a building type (all storeys, all classes).
     */
    List<Placement> getAll(String buildingType) {
        if (!loaded) load();
        return cache.getOrDefault(buildingType, List.of());
    }

    private void load() {
        loaded = true;
        String sql = """
            SELECT building_type, storey, ifc_class, element_ref, ordinal,
                   min_x, max_x, min_y, max_y, min_z, max_z,
                   orientation, discipline, material_name, material_rgba
            FROM ad_element_placement
            WHERE is_active = 1
            ORDER BY building_type, storey, ifc_class, ordinal
            """;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Placement p = new Placement(
                    rs.getString("building_type"),
                    rs.getString("storey"),
                    rs.getString("ifc_class"),
                    rs.getString("element_ref"),
                    rs.getInt("ordinal"),
                    rs.getDouble("min_x"), rs.getDouble("max_x"),
                    rs.getDouble("min_y"), rs.getDouble("max_y"),
                    rs.getDouble("min_z"), rs.getDouble("max_z"),
                    rs.getString("orientation"),
                    rs.getString("discipline"),
                    rs.getString("material_name"),
                    rs.getString("material_rgba")
                );
                cache.computeIfAbsent(p.buildingType(), k -> new ArrayList<>()).add(p);
            }
        } catch (SQLException e) {
            System.err.println("[PlacementAD] Failed to load placements: " + e.getMessage());
        }
    }

    // Singleton for shared use
    private static PlacementAD instance;
    static PlacementAD getInstance() {
        if (instance == null) instance = new PlacementAD();
        return instance;
    }
    static void resetInstance() { instance = null; }
}
