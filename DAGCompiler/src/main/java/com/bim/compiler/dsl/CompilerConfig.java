package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Compiler configuration resolver.
 * Reads ad_sysconfig from BOM.db (main working database).
 *
 * Config keys are multi-valued — e.g. multiple 'exclude_ifc_class' rows
 * define the full exclusion set. The Composer Editor will manage these
 * entries via the same table.
 *
 * Pattern: singleton lazy-load, same as PlacementLoader / SlabSpecAD.
 */
class CompilerConfig {

    /** Main working database — ad_* config + m_* BOM tables. Read lazily so
     *  tests can set bom.db between class-loads without stale caching. */
    static String dbPath() { return System.getProperty("bom.db"); }

    /** LOD geometry store — meshes, materials, element instances. */
    static final String LIBRARY_DB_PATH = "library/component_library.db";

    /** Discipline metadata store — schedules, types, connectors (Phase 2 split). */
    static final String ERP_DB_PATH = "library/ERP.db";

    private final Map<String, List<String>> config = new HashMap<>();
    private boolean loaded = false;

    /**
     * Get all values for a config key. Returns empty list if key not found.
     */
    List<String> getValues(String key) {
        if (!loaded) load();
        return config.getOrDefault(key, List.of());
    }

    /**
     * Get excluded IFC classes (convenience method).
     */
    Set<String> getExcludedIfcClasses() {
        return new HashSet<>(getValues("exclude_ifc_class"));
    }

    /**
     * Check if an IFC class is excluded by config.
     */
    boolean isExcluded(String ifcClass) {
        return getExcludedIfcClasses().contains(ifcClass);
    }

    /**
     * Check if closest-fit geometry matching is enabled.
     * When enabled, MeshBinder uses depth-axis relaxation for openings
     * and searches the component library for better-fitting meshes.
     */
    boolean isClosestFitEnabled() {
        return getValues("closest_fit").stream().anyMatch("true"::equalsIgnoreCase);
    }

    private void load() {
        loaded = true;
        String sql = "SELECT config_key, config_value FROM ad_sysconfig WHERE is_active = 1";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String key = rs.getString("config_key");
                String val = rs.getString("config_value");
                config.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
            }
        } catch (SQLException e) {
            System.err.println("[CompilerConfig] Failed to load config: " + e.getMessage());
        }
    }

    // Singleton
    private static CompilerConfig instance;
    static CompilerConfig getInstance() {
        if (instance == null) instance = new CompilerConfig();
        return instance;
    }
    static void resetInstance() { instance = null; }
}
