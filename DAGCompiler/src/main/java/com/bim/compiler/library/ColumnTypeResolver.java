package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 122D: Resolves column dimensions from ad_column_type + ad_column_type_rule.
 *
 * Follows BeamTypeResolver pattern exactly:
 * - Lazy singleton, loads on first access
 * - Graceful degradation: if tables don't exist, returns null (caller uses fallback)
 * - Profile-aware: profile-specific rules tried first, then generic
 *
 * Resolution algorithm:
 *   1. Filter rules by context (CORNER, T_JUNCTION, INTERMEDIATE) and profile
 *   2. Rules with lower priority number win (more specific = lower priority)
 *   3. Return null if no rule matches → caller uses hardcoded COLUMN_SIZE fallback
 */
public class ColumnTypeResolver {

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    private static ColumnTypeResolver instance;

    private final Map<String, ColumnTypeEntry> columnTypes = new HashMap<>();
    private final List<ColumnTypeRule> rules = new ArrayList<>();
    private boolean loaded = false;

    public record ColumnTypeEntry(
        String columnTypeId, String construction,
        int widthMm, int depthMm,
        String ifcClass, String description
    ) {
        public double widthM() { return widthMm / 1000.0; }
        public double depthM() { return depthMm / 1000.0; }
    }

    public record ColumnTypeRule(
        String context, String profile,
        String columnTypeId, int priority
    ) {}

    private ColumnTypeResolver() {}

    public static synchronized ColumnTypeResolver getInstance() {
        if (instance == null) {
            instance = new ColumnTypeResolver();
        }
        return instance;
    }

    /**
     * Resolve column type for a given context and profile.
     *
     * Two-pass resolution:
     *   Pass 1: Profile-specific rules
     *   Pass 2: Generic rules (profile=NULL)
     *
     * @param context  CORNER, T_JUNCTION, or INTERMEDIATE
     * @param profile  Building profile (e.g. "Malaysian_Institutional"), nullable
     * @return ColumnTypeEntry or null if no rule matches
     */
    public ColumnTypeEntry resolve(String context, String profile) {
        ensureLoaded();
        if (rules.isEmpty()) return null;

        // Pass 1: Profile-specific rules
        if (profile != null) {
            for (ColumnTypeRule rule : rules) {
                if (!rule.context.equals(context)) continue;
                if (rule.profile == null || !rule.profile.equals(profile)) continue;
                return columnTypes.get(rule.columnTypeId);
            }
        }

        // Pass 2: Generic rules (profile = NULL)
        for (ColumnTypeRule rule : rules) {
            if (!rule.context.equals(context)) continue;
            if (rule.profile != null) continue;
            return columnTypes.get(rule.columnTypeId);
        }

        return null;
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            // Check if table exists
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "ad_column_type", null)) {
                if (!rs.next()) {
                    System.err.println("[ColumnTypeResolver] ad_column_type not found — using defaults");
                    return;
                }
            }

            // Load column types
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT column_type_id, construction, width_mm, depth_mm, " +
                     "ifc_class, description FROM ad_column_type WHERE is_active = 1")) {
                while (rs.next()) {
                    ColumnTypeEntry e = new ColumnTypeEntry(
                        rs.getString("column_type_id"),
                        rs.getString("construction"),
                        rs.getInt("width_mm"),
                        rs.getInt("depth_mm"),
                        rs.getString("ifc_class"),
                        rs.getString("description")
                    );
                    columnTypes.put(e.columnTypeId, e);
                }
            }

            // Load rules
            try (ResultSet rs2 = conn.getMetaData().getTables(null, null, "ad_column_type_rule", null)) {
                if (!rs2.next()) return;
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT context, profile, column_type_id, priority " +
                     "FROM ad_column_type_rule WHERE is_active = 1 ORDER BY priority ASC")) {
                while (rs.next()) {
                    rules.add(new ColumnTypeRule(
                        rs.getString("context"),
                        rs.getString("profile"),
                        rs.getString("column_type_id"),
                        rs.getInt("priority")
                    ));
                }
            }

            System.out.printf("[ColumnTypeResolver] Loaded %d column types, %d rules%n",
                columnTypes.size(), rules.size());

        } catch (SQLException ex) {
            System.err.println("[ColumnTypeResolver] Failed to load: " + ex.getMessage());
        }
    }
}
