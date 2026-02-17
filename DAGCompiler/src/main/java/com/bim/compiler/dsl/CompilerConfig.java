package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Compiler configuration resolver.
 * Reads ad_compiler_config from component_library.db.
 *
 * Config keys are multi-valued — e.g. multiple 'exclude_ifc_class' rows
 * define the full exclusion set. The Composer Editor will manage these
 * entries via the same table.
 *
 * Pattern: singleton lazy-load, same as PlacementAD / SlabSpecAD.
 */
class CompilerConfig {

    private static final String DB_PATH = "library/component_library.db";

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

    private void load() {
        loaded = true;
        String sql = "SELECT config_key, config_value FROM ad_compiler_config";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
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
