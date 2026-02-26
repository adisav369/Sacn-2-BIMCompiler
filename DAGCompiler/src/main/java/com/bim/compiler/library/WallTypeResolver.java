package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 116: Resolves wall type (thickness, construction, fire rating) from
 * ad_wall_type + ad_wall_type_rule metadata tables.
 *
 * Lazy singleton — loads on first access. Graceful degradation: if tables
 * don't exist (migration not applied), falls back to WallType enum defaults.
 *
 * Resolution algorithm:
 *   1. Query ad_wall_type_rule for matching (context, space_type_a, space_type_b)
 *   2. Rules with lower priority number win (more specific = lower priority)
 *   3. Fall back to WallType.getDefaultThickness() if no rule matches
 */
public class WallTypeResolver {

    private static final String LIB_PATH = "library/BOM.db";

    /** Phase RM/A4: Default wall material when no rule matches (was hardcoded "Metal Deck") */
    public static final String DEFAULT_WALL_MATERIAL = "Metal Deck";

    /** Phase RM/A4: Glass curtain wall material name (GLASS_CURTAIN facade preference) */
    public static final String GLASS_CURTAIN_MATERIAL = "Glass Curtain Wall";

    /** Phase RM/A5: Default frame section designation (was hardcoded "RHS 150x100") */
    public static final String DEFAULT_FRAME_SECTION = "RHS 150x100";
    /** Phase RM/A5: Default frame rail height in metres (150mm from RHS 150x100) */
    public static final double DEFAULT_FRAME_RAIL_HEIGHT = 0.15;
    /** Phase RM/A5: Default frame stud width in metres (100mm from RHS 150x100) */
    public static final double DEFAULT_FRAME_STUD_WIDTH = 0.1;

    private static WallTypeResolver instance;

    /** All wall types keyed by wall_type_id */
    private final Map<String, WallTypeEntry> wallTypes = new HashMap<>();

    /** Resolution rules sorted by priority (ascending = most specific first) */
    private final List<WallTypeRule> rules = new ArrayList<>();

    private boolean loaded = false;

    public record WallTypeEntry(
        String wallTypeId, String category, String construction,
        int studMm, int totalMm, String fireRating,
        boolean isLoadbearing, boolean isExterior,
        String materialPrimary, String description
    ) {
        /** Total thickness in metres */
        public double thicknessM() { return totalMm / 1000.0; }

        /** IFC-style element name: "Wall-Ext_102Bwk+75Ins+100LBlk+12P" */
        public String ifcName() {
            if (description != null && !description.isEmpty()) {
                return "Wall-" + category.substring(0, 3) + "_" + description;
            }
            return "Wall-" + category + ":" + totalMm + "mm";
        }
    }

    public record WallTypeRule(
        String context, String spaceTypeA, String spaceTypeB,
        String wallTypeId, int priority, String profile
    ) {}

    private WallTypeResolver() {}

    public static synchronized WallTypeResolver getInstance() {
        if (instance == null) {
            instance = new WallTypeResolver();
        }
        return instance;
    }

    /**
     * Resolve wall thickness for a given context and adjacent space types.
     *
     * @param context    EXTERIOR, INTERIOR, PARTY, or FOUNDATION
     * @param spaceTypeA Room type on side A (e.g. "BATHROOM", "KITCHEN"), nullable
     * @param spaceTypeB Room type on side B, nullable
     * @param fallbackM  Default thickness if no rule matches
     * @return Wall thickness in metres
     */
    public double resolveThickness(String context, String spaceTypeA, String spaceTypeB, double fallbackM) {
        WallTypeEntry entry = resolve(context, spaceTypeA, spaceTypeB);
        return entry != null ? entry.thicknessM() : fallbackM;
    }

    /**
     * Phase 119: Resolve wall thickness with profile-specific rules.
     * Profile-specific rules (profile != null) are tried first; generic rules (profile == null) are fallback.
     *
     * @param context    EXTERIOR, INTERIOR, PARTY, or FOUNDATION
     * @param spaceTypeA Room type on side A, nullable
     * @param spaceTypeB Room type on side B, nullable
     * @param profile    Building profile (e.g. "UK_Residential", "Malaysian_Residential"), nullable
     * @param fallbackM  Default thickness if no rule matches
     * @return Wall thickness in metres
     */
    public double resolveThickness(String context, String spaceTypeA, String spaceTypeB,
                                   String profile, double fallbackM) {
        WallTypeEntry entry = resolve(context, spaceTypeA, spaceTypeB, profile);
        return entry != null ? entry.thicknessM() : fallbackM;
    }

    /**
     * Resolve full wall type entry for a given context and adjacent spaces.
     *
     * @return WallTypeEntry or null if no rule matches
     */
    public WallTypeEntry resolve(String context, String spaceTypeA, String spaceTypeB) {
        return resolve(context, spaceTypeA, spaceTypeB, null);
    }

    /**
     * Phase 119: Resolve full wall type entry with profile awareness.
     *
     * 4-pass resolution — specificity trumps profile:
     *   Pass 1: Profile rules with specific space_type match
     *   Pass 2: Generic rules with specific space_type match
     *   Pass 3: Profile wildcard rules (space_type_a = NULL)
     *   Pass 4: Generic wildcard rules
     *
     * This ensures a generic BATHROOM→184mm rule beats a profile wildcard→124mm rule.
     *
     * @return WallTypeEntry or null if no rule matches
     */
    public WallTypeEntry resolve(String context, String spaceTypeA, String spaceTypeB, String profile) {
        ensureLoaded();
        if (rules.isEmpty()) return null;

        // Pass 1: Profile rules with specific space_type match
        if (profile != null) {
            for (WallTypeRule rule : rules) {
                if (!rule.context.equals(context)) continue;
                if (rule.profile == null || !rule.profile.equals(profile)) continue;
                if (!isSpecific(rule)) continue;  // Skip wildcards in this pass

                if (matchesSpaceTypes(rule, spaceTypeA, spaceTypeB)) {
                    return wallTypes.get(rule.wallTypeId);
                }
            }
        }

        // Pass 2: Generic rules with specific space_type match
        for (WallTypeRule rule : rules) {
            if (!rule.context.equals(context)) continue;
            if (rule.profile != null) continue;
            if (!isSpecific(rule)) continue;

            if (matchesSpaceTypes(rule, spaceTypeA, spaceTypeB)) {
                return wallTypes.get(rule.wallTypeId);
            }
        }

        // Pass 3: Profile wildcard rules
        if (profile != null) {
            for (WallTypeRule rule : rules) {
                if (!rule.context.equals(context)) continue;
                if (rule.profile == null || !rule.profile.equals(profile)) continue;

                if (matchesSpaceTypes(rule, spaceTypeA, spaceTypeB)) {
                    return wallTypes.get(rule.wallTypeId);
                }
            }
        }

        // Pass 4: Generic wildcard rules
        for (WallTypeRule rule : rules) {
            if (!rule.context.equals(context)) continue;
            if (rule.profile != null) continue;

            if (matchesSpaceTypes(rule, spaceTypeA, spaceTypeB)) {
                return wallTypes.get(rule.wallTypeId);
            }
        }
        return null;
    }

    /** True if rule has at least one non-null space_type constraint */
    private boolean isSpecific(WallTypeRule rule) {
        return (rule.spaceTypeA != null && !rule.spaceTypeA.isEmpty())
            || (rule.spaceTypeB != null && !rule.spaceTypeB.isEmpty());
    }

    /** Check if rule's space_type constraints match the given pair (symmetric) */
    private boolean matchesSpaceTypes(WallTypeRule rule, String spaceTypeA, String spaceTypeB) {
        boolean matchA = rule.spaceTypeA == null || rule.spaceTypeA.isEmpty()
            || rule.spaceTypeA.equals(spaceTypeA) || rule.spaceTypeA.equals(spaceTypeB);
        boolean matchB = rule.spaceTypeB == null || rule.spaceTypeB.isEmpty()
            || rule.spaceTypeB.equals(spaceTypeA) || rule.spaceTypeB.equals(spaceTypeB);
        return matchA && matchB;
    }

    /**
     * Get a wall type by its ID directly.
     */
    public WallTypeEntry getWallType(String wallTypeId) {
        ensureLoaded();
        return wallTypes.get(wallTypeId);
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Check if table exists
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "ad_wall_type", null)) {
                if (!rs.next()) {
                    System.err.println("[WallTypeResolver] ad_wall_type not found — using defaults");
                    return;
                }
            }

            // Load wall types
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT wall_type_id, category, construction, stud_mm, total_mm, " +
                     "fire_rating, is_loadbearing, is_exterior, material_primary, description " +
                     "FROM ad_wall_type WHERE is_active = 1")) {
                while (rs.next()) {
                    WallTypeEntry e = new WallTypeEntry(
                        rs.getString("wall_type_id"),
                        rs.getString("category"),
                        rs.getString("construction"),
                        rs.getInt("stud_mm"),
                        rs.getInt("total_mm"),
                        rs.getString("fire_rating"),
                        rs.getInt("is_loadbearing") == 1,
                        rs.getInt("is_exterior") == 1,
                        rs.getString("material_primary"),
                        rs.getString("description")
                    );
                    wallTypes.put(e.wallTypeId, e);
                }
            }

            // Load rules (sorted by priority ascending = most specific first)
            try (ResultSet rs2 = conn.getMetaData().getTables(null, null, "ad_wall_type_rule", null)) {
                if (!rs2.next()) return;  // rules table optional
            }
            // Phase 119: Check if profile column exists (migration may not be applied yet)
            boolean hasProfileCol = false;
            try (ResultSet colRs = conn.getMetaData().getColumns(null, null, "ad_wall_type_rule", "profile")) {
                hasProfileCol = colRs.next();
            }
            String ruleSql = hasProfileCol
                ? "SELECT context, space_type_a, space_type_b, wall_type_id, priority, profile " +
                  "FROM ad_wall_type_rule WHERE is_active = 1 ORDER BY priority ASC"
                : "SELECT context, space_type_a, space_type_b, wall_type_id, priority " +
                  "FROM ad_wall_type_rule WHERE is_active = 1 ORDER BY priority ASC";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(ruleSql)) {
                while (rs.next()) {
                    rules.add(new WallTypeRule(
                        rs.getString("context"),
                        rs.getString("space_type_a"),
                        rs.getString("space_type_b"),
                        rs.getString("wall_type_id"),
                        rs.getInt("priority"),
                        hasProfileCol ? rs.getString("profile") : null
                    ));
                }
            }

            System.out.printf("[WallTypeResolver] Loaded %d wall types, %d rules%n",
                wallTypes.size(), rules.size());

        } catch (SQLException ex) {
            System.err.println("[WallTypeResolver] Failed to load: " + ex.getMessage());
        }
    }
}
