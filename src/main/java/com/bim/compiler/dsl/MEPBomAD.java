package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * MEP BOM Application Dictionary Query Class.
 *
 * Queries AD tables for MEP requirements - no hardcoded rules.
 * Add/change requirements via SQL, no Java changes needed.
 *
 * Tables:
 * - ad_mep_profile: BUDGET/STANDARD/PREMIUM profiles
 * - ad_space_type_mep_bom: MEP quantities per space type
 * - ad_building_code: Code definitions
 * - ad_code_requirement: Actual code rules
 * - ad_jurisdiction_codes: Which codes apply where
 */
public class MEPBomAD {

    private static final String DB_PATH = "library/component_library.db";
    private static Connection conn;

    // =========================================================================
    // Records (data from AD tables)
    // =========================================================================

    public record MEPProfile(
        String profileId,
        String description,
        String qtyColumn,
        String perAreaColumn,
        String conduitColumn
    ) {}

    public record MEPBomEntry(
        String spaceType,
        String productId,
        int qtyMin, int qtyNormal, int qtyMax,
        double perAreaMin, double perAreaNormal, double perAreaMax,
        String placementRule,
        String hostSurface,
        String buildingCode,
        String codeClause,
        double conduitMin, double conduitNormal, double conduitMax
    ) {}

    public record CodeRequirement(
        String codeId,
        String clause,
        String elementType,
        String spaceType,
        Integer qtyFixed,
        Double qtyPerArea,
        Integer qtyPerWall,
        Double maxSpacing,
        Double minClearance,
        Double coverageArea,
        Double minHeight,
        Double maxHeight,
        String placementRule,
        String description,
        boolean isMandatory
    ) {}

    public record BuildingCode(
        String codeId,
        String codeName,
        String category,
        String jurisdiction,
        int year,
        boolean isActive
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
    }

    // =========================================================================
    // Profile Queries
    // =========================================================================

    /**
     * Get MEP profile definition.
     */
    public static MEPProfile getProfile(String profileId) {
        String sql = "SELECT * FROM ad_mep_profile WHERE profile_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, profileId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new MEPProfile(
                    rs.getString("profile_id"),
                    rs.getString("description"),
                    rs.getString("qty_column"),
                    rs.getString("per_area_column"),
                    rs.getString("conduit_column")
                );
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting profile: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get all available profiles.
     */
    public static List<MEPProfile> getAllProfiles() {
        List<MEPProfile> profiles = new ArrayList<>();
        String sql = "SELECT * FROM ad_mep_profile ORDER BY profile_id";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                profiles.add(new MEPProfile(
                    rs.getString("profile_id"),
                    rs.getString("description"),
                    rs.getString("qty_column"),
                    rs.getString("per_area_column"),
                    rs.getString("conduit_column")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting profiles: " + e.getMessage());
        }
        return profiles;
    }

    // =========================================================================
    // BOM Queries
    // =========================================================================

    /**
     * Get MEP BOM for a space type.
     */
    public static List<MEPBomEntry> getBOM(String spaceType) {
        List<MEPBomEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM ad_space_type_mep_bom WHERE space_type_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, spaceType.toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entries.add(bomEntryFromResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting BOM: " + e.getMessage());
        }
        return entries;
    }

    /**
     * Get specific BOM entry.
     */
    public static MEPBomEntry getBOMEntry(String spaceType, String productId) {
        String sql = "SELECT * FROM ad_space_type_mep_bom WHERE space_type_id = ? AND mep_product_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, spaceType.toUpperCase());
            ps.setString(2, productId.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return bomEntryFromResultSet(rs);
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting BOM entry: " + e.getMessage());
        }
        return null;
    }

    private static MEPBomEntry bomEntryFromResultSet(ResultSet rs) throws SQLException {
        return new MEPBomEntry(
            rs.getString("space_type_id"),
            rs.getString("mep_product_id"),
            rs.getInt("qty_min"), rs.getInt("qty_normal"), rs.getInt("qty_max"),
            rs.getDouble("per_area_min"), rs.getDouble("per_area_normal"), rs.getDouble("per_area_max"),
            rs.getString("placement_rule"),
            rs.getString("host_surface"),
            rs.getString("building_code"),
            rs.getString("code_clause"),
            rs.getDouble("conduit_min"), rs.getDouble("conduit_normal"), rs.getDouble("conduit_max")
        );
    }

    // =========================================================================
    // Quantity Calculation
    // =========================================================================

    /**
     * Calculate MEP quantity for a space using profile.
     *
     * @param spaceType Room type (BATHROOM, BEDROOM, etc.)
     * @param productId MEP product (OUTLET, SPRINKLER, etc.)
     * @param profile MEP profile (BUDGET, STANDARD, PREMIUM)
     * @param roomArea Room area in m²
     * @return Quantity to place (clamped to code minimum)
     */
    public static int getQuantity(String spaceType, String productId, String profile, double roomArea) {
        MEPBomEntry bom = getBOMEntry(spaceType, productId);
        if (bom == null) return 0;

        MEPProfile prof = getProfile(profile);
        if (prof == null) prof = getProfile("STANDARD");

        // Get quantity based on profile
        int qty = switch (prof.qtyColumn()) {
            case "qty_min" -> bom.qtyMin();
            case "qty_max" -> bom.qtyMax();
            default -> bom.qtyNormal();
        };

        // Per-area override
        double perArea = switch (prof.perAreaColumn()) {
            case "per_area_min" -> bom.perAreaMin();
            case "per_area_max" -> bom.perAreaMax();
            default -> bom.perAreaNormal();
        };

        if (perArea > 0) {
            qty = (int) Math.ceil(roomArea * perArea);
        }

        // Always respect code minimum
        return Math.max(bom.qtyMin(), qty);
    }

    /**
     * Get full MEP list for a room with quantities calculated.
     */
    public static List<MEPPlacement> getMEPForRoom(String spaceType, String profile, double roomArea) {
        List<MEPPlacement> placements = new ArrayList<>();
        List<MEPBomEntry> bom = getBOM(spaceType);

        for (MEPBomEntry entry : bom) {
            int qty = getQuantity(spaceType, entry.productId(), profile, roomArea);
            if (qty > 0) {
                placements.add(new MEPPlacement(
                    entry.productId(),
                    qty,
                    entry.placementRule(),
                    entry.hostSurface(),
                    entry.buildingCode() + " " + entry.codeClause()
                ));
            }
        }

        return placements;
    }

    public record MEPPlacement(
        String productId,
        int quantity,
        String placementRule,
        String hostSurface,
        String codeRef
    ) {}

    // =========================================================================
    // Conduit Budget
    // =========================================================================

    /**
     * Calculate conduit budget for a room.
     */
    public static double getConduitBudget(String spaceType, String profile) {
        MEPProfile prof = getProfile(profile);
        if (prof == null) prof = getProfile("STANDARD");

        String col = prof.conduitColumn();
        String sql = "SELECT SUM(" + col + ") FROM ad_space_type_mep_bom WHERE space_type_id = ?";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, spaceType.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting conduit budget: " + e.getMessage());
        }
        return 0;
    }

    // =========================================================================
    // Building Code Queries
    // =========================================================================

    /**
     * Get codes for a jurisdiction.
     */
    public static List<BuildingCode> getCodesForJurisdiction(String jurisdiction) {
        List<BuildingCode> codes = new ArrayList<>();
        String sql = """
            SELECT c.* FROM ad_building_code c
            JOIN ad_jurisdiction_codes j ON c.code_id = j.code_id
            WHERE j.jurisdiction = ? AND c.is_active = 1
            ORDER BY j.is_primary DESC, c.category
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, jurisdiction.toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                codes.add(new BuildingCode(
                    rs.getString("code_id"),
                    rs.getString("code_name"),
                    rs.getString("category"),
                    rs.getString("jurisdiction"),
                    rs.getInt("year"),
                    rs.getInt("is_active") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting jurisdiction codes: " + e.getMessage());
        }
        return codes;
    }

    /**
     * Get code requirements for an element type.
     */
    public static List<CodeRequirement> getRequirements(String codeId, String elementType, String spaceType) {
        List<CodeRequirement> reqs = new ArrayList<>();
        String sql = """
            SELECT * FROM ad_code_requirement
            WHERE code_id = ?
            AND element_type = ?
            AND (space_type = ? OR space_type = 'ANY')
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, codeId);
            ps.setString(2, elementType.toUpperCase());
            ps.setString(3, spaceType.toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                reqs.add(requirementFromResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("[MEPBomAD] Error getting requirements: " + e.getMessage());
        }
        return reqs;
    }

    private static CodeRequirement requirementFromResultSet(ResultSet rs) throws SQLException {
        return new CodeRequirement(
            rs.getString("code_id"),
            rs.getString("clause"),
            rs.getString("element_type"),
            rs.getString("space_type"),
            rs.getObject("qty_fixed") != null ? rs.getInt("qty_fixed") : null,
            rs.getObject("qty_per_area") != null ? rs.getDouble("qty_per_area") : null,
            rs.getObject("qty_per_wall") != null ? rs.getInt("qty_per_wall") : null,
            rs.getObject("max_spacing_m") != null ? rs.getDouble("max_spacing_m") : null,
            rs.getObject("min_clearance_m") != null ? rs.getDouble("min_clearance_m") : null,
            rs.getObject("coverage_area") != null ? rs.getDouble("coverage_area") : null,
            rs.getObject("min_height_m") != null ? rs.getDouble("min_height_m") : null,
            rs.getObject("max_height_m") != null ? rs.getDouble("max_height_m") : null,
            rs.getString("placement_rule"),
            rs.getString("description"),
            rs.getInt("is_mandatory") == 1
        );
    }

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== MEPBomAD Test ===\n");

        // Profiles
        System.out.println("Profiles:");
        for (MEPProfile p : getAllProfiles()) {
            System.out.printf("  %s: %s%n", p.profileId(), p.description());
        }

        // BATHROOM BOM
        System.out.println("\nBATHROOM BOM:");
        for (MEPBomEntry e : getBOM("BATHROOM")) {
            System.out.printf("  %s: min=%d normal=%d max=%d (%s)%n",
                e.productId(), e.qtyMin(), e.qtyNormal(), e.qtyMax(), e.buildingCode());
        }

        // Quantity calculation
        System.out.println("\nQuantity for BATHROOM (7.5m²):");
        System.out.printf("  BUDGET:   OUTLET_GFCI=%d, LIGHT=%d%n",
            getQuantity("BATHROOM", "OUTLET_GFCI", "BUDGET", 7.5),
            getQuantity("BATHROOM", "LIGHT", "BUDGET", 7.5));
        System.out.printf("  STANDARD: OUTLET_GFCI=%d, LIGHT=%d%n",
            getQuantity("BATHROOM", "OUTLET_GFCI", "STANDARD", 7.5),
            getQuantity("BATHROOM", "LIGHT", "STANDARD", 7.5));
        System.out.printf("  PREMIUM:  OUTLET_GFCI=%d, LIGHT=%d%n",
            getQuantity("BATHROOM", "OUTLET_GFCI", "PREMIUM", 7.5),
            getQuantity("BATHROOM", "LIGHT", "PREMIUM", 7.5));

        // Full MEP for room
        System.out.println("\nFull MEP for BEDROOM (12m²) STANDARD:");
        for (MEPPlacement p : getMEPForRoom("BEDROOM", "STANDARD", 12.0)) {
            System.out.printf("  %s × %d (%s) - %s%n",
                p.productId(), p.quantity(), p.placementRule(), p.codeRef());
        }

        // Conduit budget
        System.out.println("\nConduit budgets (STANDARD):");
        System.out.printf("  BATHROOM: %.0fm%n", getConduitBudget("BATHROOM", "STANDARD"));
        System.out.printf("  BEDROOM:  %.0fm%n", getConduitBudget("BEDROOM", "STANDARD"));
        System.out.printf("  KITCHEN:  %.0fm%n", getConduitBudget("KITCHEN", "STANDARD"));

        // Building codes
        System.out.println("\nMalaysia codes:");
        for (BuildingCode c : getCodesForJurisdiction("MALAYSIA")) {
            System.out.printf("  %s: %s (%s)%n", c.codeId(), c.codeName(), c.category());
        }

        // Code requirements
        System.out.println("\nNEC outlet requirements for BEDROOM:");
        for (CodeRequirement r : getRequirements("NEC_2020", "OUTLET", "BEDROOM")) {
            System.out.printf("  %s: per_wall=%s, spacing=%.1fm - %s%n",
                r.clause(), r.qtyPerWall(), r.maxSpacing() != null ? r.maxSpacing() : 0, r.description());
        }

        close();
        System.out.println("\nDone!");
    }
}
