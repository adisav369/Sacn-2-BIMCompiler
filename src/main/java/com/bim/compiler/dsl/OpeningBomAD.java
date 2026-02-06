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
        String description
    ) {}

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
        boolean isFireRated
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
                OpeningFamily f = new OpeningFamily(
                    rs.getString("family_id"),
                    rs.getString("family_name"),
                    rs.getString("opening_type"),
                    rs.getString("ifc_class"),
                    rs.getInt("default_width_mm"),
                    rs.getInt("default_height_mm"),
                    rs.getInt("is_fire_rated") == 1,
                    rs.getString("description")
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
     */
    private static void ensureDefaultsLoaded() {
        if (defaultsCache != null) return;
        defaultsCache = new HashMap<>();
        String sql = "SELECT * FROM ad_space_type_opening WHERE is_active = 1 ORDER BY priority";
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
                    rs.getInt("is_fire_rated") == 1
                );
                defaultsCache.computeIfAbsent(d.spaceTypeId(), k -> new ArrayList<>()).add(d);
            }
        } catch (SQLException e) {
            System.err.println("[OpeningBomAD] Error loading defaults: " + e.getMessage());
        }
    }

    /**
     * Get opening defaults for a space type.
     */
    public static List<OpeningDefault> getDefaults(String spaceType) {
        ensureDefaultsLoaded();
        return defaultsCache.getOrDefault(spaceType.toUpperCase(), Collections.emptyList());
    }

    /**
     * Get only door defaults for a space type.
     */
    public static List<OpeningDefault> getDoorDefaults(String spaceType) {
        ensureFamiliesLoaded();
        return getDefaults(spaceType).stream()
            .filter(d -> {
                OpeningFamily f = familyCache.get(d.familyId());
                return f != null && "DOOR".equals(f.openingType());
            })
            .toList();
    }

    /**
     * Get only window defaults for a space type.
     */
    public static List<OpeningDefault> getWindowDefaults(String spaceType) {
        ensureFamiliesLoaded();
        return getDefaults(spaceType).stream()
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
