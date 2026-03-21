package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 122A: Resolves beam type (section dimensions, construction) from
 * ad_beam_type + ad_beam_type_rule metadata tables.
 *
 * Follows WallTypeResolver pattern exactly:
 * - Lazy singleton, loads on first access
 * - Graceful degradation: if tables don't exist, returns null (caller uses fallback)
 * - Profile-aware: profile-specific rules tried first, then generic
 * - Span-range matching: rule applies when span falls in [span_min_m, span_max_m]
 *
 * Resolution algorithm:
 *   1. Filter rules by context (FLOOR, LINTEL) and profile
 *   2. Match span within [span_min_m, span_max_m] range
 *   3. Rules with lower priority number win (more specific = lower priority)
 *   4. Return null if no rule matches → caller skips beam generation or uses hardcoded fallback
 */
public class BeamTypeResolver {

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    private static BeamTypeResolver instance;

    /** All beam types keyed by beam_type_id */
    private final Map<String, BeamTypeEntry> beamTypes = new HashMap<>();

    /** Resolution rules sorted by priority ascending (most specific first) */
    private final List<BeamTypeRule> rules = new ArrayList<>();

    private boolean loaded = false;

    public record BeamTypeEntry(
        String beamTypeId, String category, String construction,
        int depthMm, int widthMm, Double spanMaxM,
        boolean isLoadbearing, String materialPrimary,
        String ifcClass, String description
    ) {
        /** Section depth in metres */
        public double depthM() { return depthMm / 1000.0; }

        /** Section width in metres */
        public double widthM() { return widthMm / 1000.0; }
    }

    public record BeamTypeRule(
        String context, double spanMinM, Double spanMaxM,
        String beamTypeId, int priority, String profile
    ) {}

    private BeamTypeResolver() {}

    public static synchronized BeamTypeResolver getInstance() {
        if (instance == null) {
            instance = new BeamTypeResolver();
        }
        return instance;
    }

    /**
     * Resolve beam type for a given context, span, and profile.
     *
     * Two-pass resolution:
     *   Pass 1: Profile-specific rules matching span range
     *   Pass 2: Generic rules (profile=NULL) matching span range
     *
     * @param context  FLOOR or LINTEL
     * @param spanM    Beam span in metres
     * @param profile  Building profile (e.g. "Malaysian_Institutional"), nullable
     * @return BeamTypeEntry or null if no rule matches
     */
    public BeamTypeEntry resolve(String context, double spanM, String profile) {
        ensureLoaded();
        if (rules.isEmpty()) return null;

        // Pass 1: Profile-specific rules
        if (profile != null) {
            for (BeamTypeRule rule : rules) {
                if (!rule.context.equals(context)) continue;
                if (rule.profile == null || !rule.profile.equals(profile)) continue;
                if (matchesSpan(rule, spanM)) {
                    return beamTypes.get(rule.beamTypeId);
                }
            }
        }

        // Pass 2: Generic rules (profile = NULL)
        for (BeamTypeRule rule : rules) {
            if (!rule.context.equals(context)) continue;
            if (rule.profile != null) continue;
            if (matchesSpan(rule, spanM)) {
                return beamTypes.get(rule.beamTypeId);
            }
        }

        return null;
    }

    /** Convenience: resolve FLOOR beam type */
    public BeamTypeEntry resolveFloor(double spanM, String profile) {
        return resolve("FLOOR", spanM, profile);
    }

    /** Convenience: resolve LINTEL beam type */
    public BeamTypeEntry resolveLintel(double spanM, String profile) {
        return resolve("LINTEL", spanM, profile);
    }

    /** Check if span falls within rule's [spanMinM, spanMaxM] range */
    private boolean matchesSpan(BeamTypeRule rule, double spanM) {
        if (spanM < rule.spanMinM) return false;
        if (rule.spanMaxM != null && spanM > rule.spanMaxM) return false;
        return true;
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            // Check if table exists
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "ad_beam_type", null)) {
                if (!rs.next()) {
                    System.err.println("[BeamTypeResolver] ad_beam_type not found — using defaults");
                    return;
                }
            }

            // Load beam types
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT beam_type_id, category, construction, depth_mm, width_mm, " +
                     "span_max_m, is_loadbearing, material_primary, ifc_class, description " +
                     "FROM ad_beam_type WHERE is_active = 1")) {
                while (rs.next()) {
                    Double spanMax = rs.getDouble("span_max_m");
                    if (rs.wasNull()) spanMax = null;
                    BeamTypeEntry e = new BeamTypeEntry(
                        rs.getString("beam_type_id"),
                        rs.getString("category"),
                        rs.getString("construction"),
                        rs.getInt("depth_mm"),
                        rs.getInt("width_mm"),
                        spanMax,
                        rs.getInt("is_loadbearing") == 1,
                        rs.getString("material_primary"),
                        rs.getString("ifc_class"),
                        rs.getString("description")
                    );
                    beamTypes.put(e.beamTypeId, e);
                }
            }

            // Load rules
            try (ResultSet rs2 = conn.getMetaData().getTables(null, null, "ad_beam_type_rule", null)) {
                if (!rs2.next()) return;
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT context, span_min_m, span_max_m, beam_type_id, priority, profile " +
                     "FROM ad_beam_type_rule WHERE is_active = 1 ORDER BY priority ASC")) {
                while (rs.next()) {
                    Double spanMax = rs.getDouble("span_max_m");
                    if (rs.wasNull()) spanMax = null;
                    rules.add(new BeamTypeRule(
                        rs.getString("context"),
                        rs.getDouble("span_min_m"),
                        spanMax,
                        rs.getString("beam_type_id"),
                        rs.getInt("priority"),
                        rs.getString("profile")
                    ));
                }
            }

            System.out.printf("[BeamTypeResolver] Loaded %d beam types, %d rules%n",
                beamTypes.size(), rules.size());

        } catch (SQLException ex) {
            System.err.println("[BeamTypeResolver] Failed to load: " + ex.getMessage());
        }
    }
}
