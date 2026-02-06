package com.bim.tools.sanity;

import java.sql.*;
import java.util.*;

/**
 * Application Dictionary for Sanity Check rules.
 *
 * Queries ad_check_applicability and ad_check_threshold tables.
 * Follows the AD pattern from FireProtectionAD, SpaceTypeAD.
 *
 * Usage:
 *   SanityCheckAD ad = new SanityCheckAD(libraryDbPath);
 *   List<CheckSpec> checks = ad.getApplicableChecks(storeys, occupancy, area, jurisdiction);
 *   double travelLimit = ad.getThreshold("escape_route", "max_travel_distance", "R", "MULTI", "MALAYSIA");
 *
 * Phase 78: Metadata-driven sanity check selection.
 */
public class SanityCheckAD {

    private final String dbPath;

    // Cache for performance
    private final Map<String, CheckSpec> checkCache = new HashMap<>();
    private final Map<String, Double> thresholdCache = new HashMap<>();
    private boolean cacheLoaded = false;

    public SanityCheckAD(String dbPath) {
        this.dbPath = dbPath;
    }

    // =========================================================================
    // Data Classes (Records)
    // =========================================================================

    /** Check specification - when and how a check should run */
    public record CheckSpec(
        String checkId,
        String checkName,
        String checkClass,
        String category,           // STRUCTURAL, MEP, EGRESS, ENVELOPE, GEOMETRY, COMPLIANCE
        Integer minStoreys,
        Integer maxStoreys,
        String occupancyGroups,    // Comma-separated or null for all
        Double minAreaM2,
        String requiresElement,    // IFC type required (e.g., IfcStair)
        String skipCondition,
        String skipReason,
        String severity,           // ERROR, WARNING, INFO
        boolean enabled,
        int sortOrder,
        String codeId,
        String clause,
        String description
    ) {
        /** Check if this check applies to given building parameters */
        public boolean appliesTo(int storeys, String occupancy, double areaM2) {
            // Storey range check
            if (minStoreys != null && storeys < minStoreys) return false;
            if (maxStoreys != null && storeys > maxStoreys) return false;

            // Area check
            if (minAreaM2 != null && areaM2 < minAreaM2) return false;

            // Occupancy check
            if (occupancyGroups != null && !occupancyGroups.isEmpty()) {
                if (occupancy == null) return false;
                boolean found = false;
                for (String group : occupancyGroups.split(",")) {
                    if (group.trim().equalsIgnoreCase(occupancy)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }

            return true;
        }
    }

    /** Threshold specification - configurable limit for a check */
    public record ThresholdSpec(
        String thresholdId,
        String checkId,
        String thresholdName,
        String occupancyGroup,
        String storeyType,         // SINGLE, MULTI, HIGH_RISE
        String buildingClass,      // RESIDENTIAL, PUBLIC, COMMERCIAL
        double thresholdValue,
        String thresholdUnit,
        double sprinklerBonus,
        String codeId,
        String clause,
        String description,
        String jurisdiction
    ) {}

    // =========================================================================
    // Query Methods - Check Applicability
    // =========================================================================

    /**
     * Get all checks that apply to a building with given parameters.
     *
     * @param storeys Number of storeys
     * @param occupancy IBC occupancy group (R, A, B, E, etc.) or null
     * @param areaM2 Building total area in m²
     * @param jurisdiction MALAYSIA or INTERNATIONAL
     * @return List of applicable CheckSpecs sorted by execution order
     */
    public List<CheckSpec> getApplicableChecks(int storeys, String occupancy,
                                                double areaM2, String jurisdiction) {
        loadCacheIfNeeded();

        return checkCache.values().stream()
            .filter(CheckSpec::enabled)
            .filter(c -> c.appliesTo(storeys, occupancy, areaM2))
            .sorted(Comparator.comparingInt(CheckSpec::sortOrder))
            .toList();
    }

    /**
     * Get all registered checks (regardless of applicability).
     */
    public List<CheckSpec> getAllChecks() {
        loadCacheIfNeeded();
        return checkCache.values().stream()
            .sorted(Comparator.comparingInt(CheckSpec::sortOrder))
            .toList();
    }

    /**
     * Get a specific check by ID.
     */
    public CheckSpec getCheck(String checkId) {
        loadCacheIfNeeded();
        return checkCache.get(checkId);
    }

    /**
     * Get checks by category.
     */
    public List<CheckSpec> getChecksByCategory(String category) {
        loadCacheIfNeeded();
        return checkCache.values().stream()
            .filter(c -> category.equalsIgnoreCase(c.category()))
            .sorted(Comparator.comparingInt(CheckSpec::sortOrder))
            .toList();
    }

    // =========================================================================
    // Query Methods - Thresholds
    // =========================================================================

    /**
     * Get threshold value for a check with best-match parameters.
     *
     * @param checkId The check ID (e.g., "escape_route")
     * @param thresholdName The threshold name (e.g., "max_travel_distance")
     * @param occupancy Occupancy group (or null)
     * @param storeyType SINGLE, MULTI, or HIGH_RISE (or null)
     * @param buildingClass RESIDENTIAL, PUBLIC, or COMMERCIAL (or null)
     * @param jurisdiction MALAYSIA or INTERNATIONAL
     * @param sprinklered Whether building is sprinklered (applies bonus)
     * @return Threshold value (with sprinkler bonus if applicable) or null
     */
    public Double getThreshold(String checkId, String thresholdName,
                               String occupancy, String storeyType, String buildingClass,
                               String jurisdiction, boolean sprinklered) {
        ThresholdSpec spec = getThresholdSpec(checkId, thresholdName, occupancy,
                                               storeyType, buildingClass, jurisdiction);
        if (spec == null) return null;

        double value = spec.thresholdValue();
        if (sprinklered && spec.sprinklerBonus() != 1.0) {
            value *= spec.sprinklerBonus();
        }
        return value;
    }

    /**
     * Get threshold specification with best-match logic.
     */
    public ThresholdSpec getThresholdSpec(String checkId, String thresholdName,
                                           String occupancy, String storeyType, String buildingClass,
                                           String jurisdiction) {
        String sql = """
            SELECT * FROM ad_check_threshold
            WHERE check_id = ? AND threshold_name = ?
              AND (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND is_active = 1
              AND (occupancy_group IS NULL OR occupancy_group = ?)
              AND (storey_type IS NULL OR storey_type = ?)
              AND (building_class IS NULL OR building_class = ?)
            ORDER BY
              CASE WHEN jurisdiction = ? THEN 0 ELSE 1 END,
              CASE WHEN occupancy_group = ? THEN 0 WHEN occupancy_group IS NULL THEN 1 ELSE 2 END,
              CASE WHEN storey_type = ? THEN 0 WHEN storey_type IS NULL THEN 1 ELSE 2 END,
              CASE WHEN building_class = ? THEN 0 WHEN building_class IS NULL THEN 1 ELSE 2 END
            LIMIT 1
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, checkId);
            stmt.setString(2, thresholdName);
            stmt.setString(3, jurisdiction);
            stmt.setString(4, occupancy);
            stmt.setString(5, storeyType);
            stmt.setString(6, buildingClass);
            stmt.setString(7, jurisdiction);
            stmt.setString(8, occupancy);
            stmt.setString(9, storeyType);
            stmt.setString(10, buildingClass);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return parseThresholdSpec(rs);
            }
        } catch (SQLException e) {
            System.err.println("[SanityCheckAD] Threshold query error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get all thresholds for a check.
     */
    public List<ThresholdSpec> getThresholdsForCheck(String checkId) {
        List<ThresholdSpec> result = new ArrayList<>();

        String sql = """
            SELECT * FROM ad_check_threshold
            WHERE check_id = ? AND is_active = 1
            ORDER BY threshold_name, occupancy_group, storey_type
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, checkId);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(parseThresholdSpec(rs));
            }
        } catch (SQLException e) {
            System.err.println("[SanityCheckAD] Thresholds query error: " + e.getMessage());
        }

        return result;
    }

    // =========================================================================
    // Convenience Methods
    // =========================================================================

    /**
     * Determine storey type classification.
     */
    public static String classifyStoreyType(int storeys, double heightM) {
        if (storeys == 1) return "SINGLE";
        if (heightM > 18.0 || storeys > 6) return "HIGH_RISE";
        return "MULTI";
    }

    /**
     * Determine building class from occupancy and use.
     */
    public static String classifyBuildingClass(String occupancy, boolean hasPublicAccess) {
        if (occupancy == null) return null;
        if ("R".equalsIgnoreCase(occupancy)) return "RESIDENTIAL";
        if (hasPublicAccess || "A".equalsIgnoreCase(occupancy) ||
            "E".equalsIgnoreCase(occupancy) || "I".equalsIgnoreCase(occupancy)) {
            return "PUBLIC";
        }
        return "COMMERCIAL";
    }

    /**
     * Check if AD tables are available.
     */
    public boolean isAvailable() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            String sql = """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type='table' AND name IN ('ad_check_applicability', 'ad_check_threshold')
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return rs.next() && rs.getInt(1) == 2;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Clear caches (for testing or config reload).
     */
    public void clearCache() {
        checkCache.clear();
        thresholdCache.clear();
        cacheLoaded = false;
    }

    // =========================================================================
    // Private Implementation
    // =========================================================================

    private void loadCacheIfNeeded() {
        if (cacheLoaded) return;

        String sql = """
            SELECT * FROM ad_check_applicability
            WHERE is_active = 1
            ORDER BY sort_order
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                CheckSpec spec = parseCheckSpec(rs);
                checkCache.put(spec.checkId(), spec);
            }
            cacheLoaded = true;

        } catch (SQLException e) {
            System.err.println("[SanityCheckAD] Cache load error: " + e.getMessage());
        }
    }

    private CheckSpec parseCheckSpec(ResultSet rs) throws SQLException {
        return new CheckSpec(
            rs.getString("check_id"),
            rs.getString("check_name"),
            rs.getString("check_class"),
            rs.getString("category"),
            getIntOrNull(rs, "min_storeys"),
            getIntOrNull(rs, "max_storeys"),
            rs.getString("occupancy_groups"),
            getDoubleOrNull(rs, "min_area_m2"),
            rs.getString("requires_element"),
            rs.getString("skip_condition"),
            rs.getString("skip_reason"),
            rs.getString("severity"),
            rs.getInt("enabled") == 1,
            rs.getInt("sort_order"),
            rs.getString("code_id"),
            rs.getString("clause"),
            rs.getString("description")
        );
    }

    private ThresholdSpec parseThresholdSpec(ResultSet rs) throws SQLException {
        return new ThresholdSpec(
            rs.getString("threshold_id"),
            rs.getString("check_id"),
            rs.getString("threshold_name"),
            rs.getString("occupancy_group"),
            rs.getString("storey_type"),
            rs.getString("building_class"),
            rs.getDouble("threshold_value"),
            rs.getString("threshold_unit"),
            rs.getDouble("sprinkler_bonus"),
            rs.getString("code_id"),
            rs.getString("clause"),
            rs.getString("description"),
            rs.getString("jurisdiction")
        );
    }

    private Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    private Double getDoubleOrNull(ResultSet rs, String column) throws SQLException {
        double val = rs.getDouble(column);
        return rs.wasNull() ? null : val;
    }

    // =========================================================================
    // Summary Report
    // =========================================================================

    /**
     * Generate a check applicability report for a building.
     */
    public String generateApplicabilityReport(int storeys, String occupancy,
                                               double areaM2, String jurisdiction) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SANITY CHECK APPLICABILITY REPORT ===\n");
        sb.append(String.format("Building: %d storeys, %.0fm², Occupancy: %s\n",
            storeys, areaM2, occupancy != null ? occupancy : "not specified"));
        sb.append(String.format("Jurisdiction: %s\n\n", jurisdiction));

        String storeyType = classifyStoreyType(storeys, storeys * 3.0);
        String buildingClass = classifyBuildingClass(occupancy, !"R".equals(occupancy));

        sb.append(String.format("Classification: %s, %s\n\n", storeyType, buildingClass));

        List<CheckSpec> applicable = getApplicableChecks(storeys, occupancy, areaM2, jurisdiction);
        List<CheckSpec> all = getAllChecks();

        sb.append("APPLICABLE CHECKS:\n");
        Map<String, List<CheckSpec>> byCategory = new LinkedHashMap<>();
        for (CheckSpec c : applicable) {
            byCategory.computeIfAbsent(c.category(), k -> new ArrayList<>()).add(c);
        }

        for (Map.Entry<String, List<CheckSpec>> entry : byCategory.entrySet()) {
            sb.append(String.format("\n  %s:\n", entry.getKey()));
            for (CheckSpec c : entry.getValue()) {
                sb.append(String.format("    [%s] %s (%s)\n",
                    c.severity(), c.checkName(), c.codeId() != null ? c.codeId() + " " + c.clause() : "N/A"));
            }
        }

        // List skipped checks
        sb.append("\nSKIPPED CHECKS:\n");
        for (CheckSpec c : all) {
            if (!applicable.contains(c)) {
                String reason = c.skipReason() != null ? c.skipReason() : "Not applicable to this building type";
                sb.append(String.format("    %s: %s\n", c.checkName(), reason));
            }
        }

        sb.append(String.format("\nSummary: %d/%d checks applicable\n", applicable.size(), all.size()));

        return sb.toString();
    }

    // =========================================================================
    // Test Entry Point
    // =========================================================================

    public static void main(String[] args) {
        // Support running from project root or tools/sanity-checker
        String dbPath = "library/component_library.db";
        if (!new java.io.File(dbPath).exists()) {
            dbPath = "../../library/component_library.db";
        }
        SanityCheckAD ad = new SanityCheckAD(dbPath);

        if (!ad.isAvailable()) {
            System.out.println("AD tables not available.");
            System.out.println("Run: python scripts/create_ad_sanity_check.py");
            return;
        }

        // Test 1: Single-storey residential
        System.out.println(ad.generateApplicabilityReport(1, "R", 88.0, "MALAYSIA"));
        System.out.println("\n" + "=".repeat(60) + "\n");

        // Test 2: Multi-storey school
        System.out.println(ad.generateApplicabilityReport(2, "E", 1500.0, "MALAYSIA"));
        System.out.println("\n" + "=".repeat(60) + "\n");

        // Test 3: High-rise residential
        System.out.println(ad.generateApplicabilityReport(18, "R", 5000.0, "MALAYSIA"));
        System.out.println("\n" + "=".repeat(60) + "\n");

        // Test thresholds
        System.out.println("=== THRESHOLD TESTS ===\n");

        Double travelLimit = ad.getThreshold("escape_route", "max_travel_distance",
            "R", "MULTI", "RESIDENTIAL", "MALAYSIA", false);
        System.out.printf("Travel limit (MULTI, no sprinkler): %.1f m\n", travelLimit);

        travelLimit = ad.getThreshold("escape_route", "max_travel_distance",
            "R", "MULTI", "RESIDENTIAL", "MALAYSIA", true);
        System.out.printf("Travel limit (MULTI, sprinklered): %.1f m\n", travelLimit);

        Double stairWidth = ad.getThreshold("stairwell", "min_stair_width",
            "R", null, "RESIDENTIAL", "MALAYSIA", false);
        System.out.printf("Stair width (residential): %.0f mm\n", stairWidth);

        stairWidth = ad.getThreshold("stairwell", "min_stair_width",
            null, null, "PUBLIC", "MALAYSIA", false);
        System.out.printf("Stair width (public): %.0f mm\n", stairWidth);
    }
}
