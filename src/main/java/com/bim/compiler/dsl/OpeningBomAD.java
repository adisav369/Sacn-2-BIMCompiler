package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Opening BOM Application Dictionary Query Class.
 *
 * Queries AD tables for opening (door/window) defaults per space type.
 * No hardcoded rules — add/change via SQL.
 *
 * Tables:
 * - ad_opening_family: Door/window family definitions
 * - ad_space_type_opening: Default openings per space type
 *
 * Override priority: DSL explicit > BOM default > hardcoded fallback
 */
public class OpeningBomAD {

    private static final String DB_PATH = "library/component_library.db";
    private static Connection conn;

    // Caches
    private static Map<String, OpeningFamily> familyCache;
    private static Map<String, List<OpeningDefault>> defaultsCache;

    // =========================================================================
    // Records
    // =========================================================================

    public record OpeningFamily(
        String familyId,
        String familyName,
        String openingType,   // DOOR or WINDOW
        String ifcClass,      // IfcDoor or IfcWindow
        int defaultWidthMm,
        int defaultHeightMm,
        boolean isFireRated,
        String description,
        int depthMm           // Phase 119B: frame/panel depth for bbox thin dimension
    ) {
        /** Depth in metres */
        public double depthM() { return depthMm / 1000.0; }
    }

    public record OpeningDefault(
        String spaceTypeId,
        String openingRole,    // ENTRY, EGRESS, NATURAL_LIGHT, VENTILATION
        String familyId,
        String qtyRule,        // FIXED, PER_EXTERIOR_WALL
        int qtyBase,
        Integer overrideWidthMm,
        Integer overrideHeightMm,
        int sillHeightMm,
        String placementWall,  // exterior, interior, or null
        String placementRule,  // CENTER
        boolean isFireRated,
        String profile         // Phase 119B: null = generic, non-null = profile-specific
    ) {}

    public record ResolvedOpening(
        String openingType,    // DOOR or WINDOW
        String openingRole,    // ENTRY, NATURAL_LIGHT, etc.
        double widthM,
        double heightM,
        double sillHeightM,
        int quantity,
        String placementWall,  // exterior or interior
        String placementRule,
        boolean isFireRated,
        String familyId
    ) {}

    // =========================================================================
    // Connection Management
    // =========================================================================

    private static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        }
        return conn;
    }

    public static void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) { }
            conn = null;
        }
        familyCache = null;
        defaultsCache = null;
    }

    // =========================================================================
    // Family Queries
    // =========================================================================

    /**
     * Load and cache all opening families.
     */
    private static void ensureFamiliesLoaded() {
        if (familyCache != null) return;
        familyCache = new HashMap<>();
        String sql = "SELECT * FROM ad_opening_family WHERE is_active = 1";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                // Phase 119B: Load depth_mm if column exists (graceful fallback)
                int depthMm = 100; // default
                try { depthMm = rs.getInt("depth_mm"); if (rs.wasNull()) depthMm = 100; }
                catch (SQLException ignore) { }
                OpeningFamily f = new OpeningFamily(
                    rs.getString("family_id"),
                    rs.getString("family_name"),
                    rs.getString("opening_type"),
                    rs.getString("ifc_class"),
                    rs.getInt("default_width_mm"),
                    rs.getInt("default_height_mm"),
                    rs.getInt("is_fire_rated") == 1,
                    rs.getString("description"),
                    depthMm
                );
                familyCache.put(f.familyId(), f);
            }
        } catch (SQLException e) {
            System.err.println("[OpeningBomAD] Error loading families: " + e.getMessage());
        }
    }

    /**
     * Get a specific opening family by ID.
     */
    public static OpeningFamily getFamily(String familyId) {
        ensureFamiliesLoaded();
        return familyCache.get(familyId);
    }

    // =========================================================================
    // Space-Type Default Queries
    // =========================================================================

    /**
     * Load and cache all space-type opening defaults.
     * Phase 119B: Loads profile column if present (graceful degradation).
     */
    private static void ensureDefaultsLoaded() {
        if (defaultsCache != null) return;
        defaultsCache = new HashMap<>();
        try {
            // Phase 119B: Check if profile column exists
            boolean hasProfile = false;
            try (ResultSet colRs = getConnection().getMetaData()
                    .getColumns(null, null, "ad_space_type_opening", "profile")) {
                hasProfile = colRs.next();
            }
            String sql = hasProfile
                ? "SELECT *, profile FROM ad_space_type_opening WHERE is_active = 1 ORDER BY priority"
                : "SELECT * FROM ad_space_type_opening WHERE is_active = 1 ORDER BY priority";
            try (Statement st = getConnection().createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    OpeningDefault d = new OpeningDefault(
                        rs.getString("space_type_id"),
                        rs.getString("opening_role"),
                        rs.getString("family_id"),
                        rs.getString("qty_rule"),
                        rs.getInt("qty_base"),
                        rs.getObject("override_width_mm") != null ? rs.getInt("override_width_mm") : null,
                        rs.getObject("override_height_mm") != null ? rs.getInt("override_height_mm") : null,
                        rs.getInt("sill_height_mm"),
                        rs.getString("placement_wall"),
                        rs.getString("placement_rule"),
                        rs.getInt("is_fire_rated") == 1,
                        hasProfile ? rs.getString("profile") : null
                    );
                    defaultsCache.computeIfAbsent(d.spaceTypeId(), k -> new ArrayList<>()).add(d);
                }
            }
        } catch (SQLException e) {
            System.err.println("[OpeningBomAD] Error loading defaults: " + e.getMessage());
        }
    }

    /**
     * Get opening defaults for a space type (generic, no profile filter).
     */
    public static List<OpeningDefault> getDefaults(String spaceType) {
        return getDefaults(spaceType, null);
    }

    /**
     * Phase 119B: Get opening defaults with profile-aware two-pass resolution.
     * Pass 1: profile-specific rules (profile != null && matches).
     * Pass 2: generic rules (profile == null) — only for roles not covered by pass 1.
     */
    public static List<OpeningDefault> getDefaults(String spaceType, String profile) {
        ensureDefaultsLoaded();
        List<OpeningDefault> all = defaultsCache.getOrDefault(spaceType.toUpperCase(), Collections.emptyList());
        if (all.isEmpty() || profile == null) {
            // No profile: return only generic rules
            return all.stream().filter(d -> d.profile() == null).toList();
        }

        // Pass 1: profile-specific rules
        List<OpeningDefault> profileRules = all.stream()
            .filter(d -> profile.equals(d.profile()))
            .toList();

        // Collect roles covered by profile-specific rules
        Set<String> coveredRoles = new HashSet<>();
        for (OpeningDefault d : profileRules) coveredRoles.add(d.openingRole());

        // Pass 2: generic rules for uncovered roles
        List<OpeningDefault> result = new ArrayList<>(profileRules);
        for (OpeningDefault d : all) {
            if (d.profile() == null && !coveredRoles.contains(d.openingRole())) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * Get only door defaults for a space type (generic).
     */
    public static List<OpeningDefault> getDoorDefaults(String spaceType) {
        return getDoorDefaults(spaceType, null);
    }

    /**
     * Phase 119B: Get door defaults with profile awareness.
     */
    public static List<OpeningDefault> getDoorDefaults(String spaceType, String profile) {
        ensureFamiliesLoaded();
        return getDefaults(spaceType, profile).stream()
            .filter(d -> {
                OpeningFamily f = familyCache.get(d.familyId());
                return f != null && "DOOR".equals(f.openingType());
            })
            .toList();
    }

    /**
     * Get only window defaults for a space type (generic).
     */
    public static List<OpeningDefault> getWindowDefaults(String spaceType) {
        return getWindowDefaults(spaceType, null);
    }

    /**
     * Phase 119B: Get window defaults with profile awareness.
     */
    public static List<OpeningDefault> getWindowDefaults(String spaceType, String profile) {
        ensureFamiliesLoaded();
        return getDefaults(spaceType, profile).stream()
            .filter(d -> {
                OpeningFamily f = familyCache.get(d.familyId());
                return f != null && "WINDOW".equals(f.openingType());
            })
            .toList();
    }

    // =========================================================================
    // Resolution
    // =========================================================================

    /**
     * Resolve all openings for a space type.
     *
     * FIXED qty_rule → creates qtyBase openings
     * PER_EXTERIOR_WALL → creates 1 opening per exterior wall
     *
     * Width/height: override → family default
     */
    public static List<ResolvedOpening> resolveOpenings(String spaceType, List<String> exteriorWalls) {
        ensureFamiliesLoaded();
        List<OpeningDefault> defaults = getDefaults(spaceType);
        if (defaults.isEmpty()) return Collections.emptyList();

        List<ResolvedOpening> result = new ArrayList<>();

        for (OpeningDefault d : defaults) {
            OpeningFamily family = familyCache.get(d.familyId());
            if (family == null) continue;

            // Resolve dimensions: override → family default
            int widthMm = d.overrideWidthMm() != null ? d.overrideWidthMm() : family.defaultWidthMm();
            int heightMm = d.overrideHeightMm() != null ? d.overrideHeightMm() : family.defaultHeightMm();
            double widthM = widthMm / 1000.0;
            double heightM = heightMm / 1000.0;
            double sillM = d.sillHeightMm() / 1000.0;

            int qty;
            if ("PER_EXTERIOR_WALL".equals(d.qtyRule())) {
                qty = exteriorWalls != null ? exteriorWalls.size() : 0;
            } else {
                qty = d.qtyBase();
            }

            if (qty > 0) {
                result.add(new ResolvedOpening(
                    family.openingType(),
                    d.openingRole(),
                    widthM, heightM, sillM,
                    qty,
                    d.placementWall(),
                    d.placementRule(),
                    d.isFireRated(),
                    d.familyId()
                ));
            }
        }

        return result;
    }
}
