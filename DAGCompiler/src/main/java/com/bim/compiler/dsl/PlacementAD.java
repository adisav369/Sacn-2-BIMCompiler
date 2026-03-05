package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase B1: Metadata-driven element placement resolver.
 * Reads lod_element_placement from component_library.db (LOD).
 * Each row = one element to emit at extracted reference coordinates.
 *
 * Pattern: same lazy-load + cache as SlabSpecAD, FloorTypeAD.
 * The metadata IS the production list — compose functions read positions
 * from here instead of computing them from grid/solver.
 */
public class PlacementAD {

    public record Placement(
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
        String materialRgba,
        String familyRef
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

    // Phase BOM-2c: Consumption registry — elements processed by StoreyCompiler.applyPlacementOverrides
    // are RELATIONAL source; emitGlobalPlacementElements (FLAT source) must skip them.
    // Key: buildingType + NUL + elementRef. Replaces the perStoreyClasses storey-name guard
    // which was an accident of naming mismatch, not an explicit design contract.
    // [EXTRACTED: Phase BOM-2c FLAT→RELATIONAL source contract]
    private final Set<String> consumed = new HashSet<>();

    /** Mark an element as consumed (processed) by StoreyCompiler.applyPlacementOverrides. */
    void markConsumed(String buildingType, String elementRef) {
        consumed.add(buildingType + "\u0000" + elementRef);
    }

    /** True if this element was consumed by the compiled (RELATIONAL) path and must not be re-emitted. */
    boolean isConsumed(String buildingType, String elementRef) {
        return consumed.contains(buildingType + "\u0000" + elementRef);
    }

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
    public List<Placement> getAll(String buildingType) {
        if (!loaded) load();
        return cache.getOrDefault(buildingType, List.of());
    }

    private void load() {
        loaded = true;
        loadFromComponentLibrary();
    }

    /**
     * Load all active placements from lod_element_placement in component_library.db.
     * This is the sole placement source for EXTRACTED buildings (SH, DX).
     */
    private void loadFromComponentLibrary() {
        String sql = """
            SELECT building_type, storey, ifc_class, element_ref, ordinal,
                   min_x, max_x, min_y, max_y, min_z, max_z,
                   orientation, discipline, material_name, material_rgba
            FROM lod_element_placement
            WHERE is_active = 1
            ORDER BY building_type, storey, ifc_class, ordinal
            """;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String bt = rs.getString("building_type");
                Placement p = new Placement(
                    bt,
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
                    rs.getString("material_rgba"),
                    null  // familyRef — flat table has no family_ref
                );
                cache.computeIfAbsent(p.buildingType(), k -> new ArrayList<>()).add(p);
            }
        } catch (SQLException e) {
            System.err.println("[PlacementAD] Failed to load placements: " + e.getMessage());
        }
    }

    // Singleton for shared use
    private static PlacementAD instance;
    public static PlacementAD getInstance() {
        if (instance == null) instance = new PlacementAD();
        return instance;
    }
    public static void resetInstance() { instance = null; }
}
