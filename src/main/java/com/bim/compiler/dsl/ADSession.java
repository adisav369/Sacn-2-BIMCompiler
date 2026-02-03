package com.bim.compiler.dsl;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * ADSession - Abstract Factory for Application Dictionary access.
 *
 * Consolidates all AD queries into a single session with:
 * - Single database connection (lifecycle managed)
 * - Shared cache across all AD instances
 * - Lazy instantiation of AD facades
 * - Cross-AD data reuse
 *
 * Usage:
 * <pre>
 * try (ADSession ad = ADSession.open()) {
 *     // All queries share one connection
 *     boolean fpRequired = ad.fireProtection().isSprinklerRequired(18, 54.0, 1500.0, "R", "MALAYSIA");
 *     var coverage = ad.mep().getSprinklerCoverage("LIGHT");
 *     var stair = ad.verticalCirculation().getStairRequirement("R", "MALAYSIA");
 *     var rule = ad.placement().getRule("OUTLET_WALL");
 * }
 * </pre>
 *
 * Phase 57: Session pattern for AD consolidation.
 */
public class ADSession implements AutoCloseable {

    private static final String DEFAULT_DB_PATH = "library/component_library.db";

    private final Connection conn;
    private final String dbPath;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    // Lazy-initialized AD facades
    private FireProtectionADFacade fireProtection;
    private MEPADFacade mep;
    private VerticalCirculationADFacade verticalCirculation;
    private PlacementRuleADFacade placement;
    private SpaceTypeADFacade spaceType;

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * Open a session with default database path.
     */
    public static ADSession open() throws SQLException {
        return new ADSession(DEFAULT_DB_PATH);
    }

    /**
     * Open a session with specific database path.
     */
    public static ADSession open(String dbPath) throws SQLException {
        return new ADSession(dbPath);
    }

    private ADSession(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    // =========================================================================
    // AD Facade Accessors (Lazy)
    // =========================================================================

    /**
     * Fire Protection AD - triggers, risers, compartments.
     */
    public FireProtectionADFacade fireProtection() {
        if (fireProtection == null) {
            fireProtection = new FireProtectionADFacade(this);
        }
        return fireProtection;
    }

    /**
     * MEP AD - elements, coverage, reference values.
     */
    public MEPADFacade mep() {
        if (mep == null) {
            mep = new MEPADFacade(this);
        }
        return mep;
    }

    /**
     * Vertical Circulation AD - stairs, elevators, egress.
     */
    public VerticalCirculationADFacade verticalCirculation() {
        if (verticalCirculation == null) {
            verticalCirculation = new VerticalCirculationADFacade(this);
        }
        return verticalCirculation;
    }

    /**
     * Placement Rule AD - positioning rules.
     */
    public PlacementRuleADFacade placement() {
        if (placement == null) {
            placement = new PlacementRuleADFacade(this);
        }
        return placement;
    }

    /**
     * Space Type AD - room types, aliases, MEP config.
     */
    public SpaceTypeADFacade spaceType() {
        if (spaceType == null) {
            spaceType = new SpaceTypeADFacade(this);
        }
        return spaceType;
    }

    // =========================================================================
    // Shared Infrastructure
    // =========================================================================

    /**
     * Get the shared connection (for AD facades).
     */
    Connection connection() {
        return conn;
    }

    /**
     * Get cached value or compute and cache.
     */
    @SuppressWarnings("unchecked")
    public <T> T cached(String key, Supplier<T> loader) {
        return (T) cache.computeIfAbsent(key, k -> loader.get());
    }

    /**
     * Check if value is cached.
     */
    public boolean isCached(String key) {
        return cache.containsKey(key);
    }

    /**
     * Clear specific cache entry.
     */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /**
     * Clear all cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Get cache statistics.
     */
    public int cacheSize() {
        return cache.size();
    }

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {}
        cache.clear();
    }

    // =========================================================================
    // AD Facade Implementations (Inner Classes)
    // =========================================================================

    /**
     * Fire Protection AD Facade.
     */
    public static class FireProtectionADFacade {
        private final ADSession session;

        FireProtectionADFacade(ADSession session) {
            this.session = session;
        }

        public boolean isSprinklerRequired(int storeys, double heightM, double floorAreaM2,
                                           String occupancyGroup, String jurisdiction) {
            String key = "fp_required_" + storeys + "_" + heightM + "_" + floorAreaM2 + "_" + jurisdiction;
            return session.cached(key, () -> {
                try {
                    String sql = """
                        SELECT COUNT(*) FROM ad_fp_trigger
                        WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
                          AND element_type = 'SPRINKLER'
                          AND is_mandatory = 1
                          AND is_active = 1
                          AND (
                            (trigger_type = 'STOREY_COUNT' AND min_storeys > 0 AND min_storeys <= ?)
                            OR (trigger_type = 'STOREY_COUNT' AND min_storeys < 0 AND ? < 0)
                            OR (trigger_type = 'BUILDING_HEIGHT' AND min_height_m <= ?)
                            OR (trigger_type = 'FLOOR_AREA' AND min_floor_area_m2 <= ?)
                            OR (trigger_type = 'OCCUPANCY' AND occupancy_group = ?)
                          )
                        """;
                    try (PreparedStatement stmt = session.conn.prepareStatement(sql)) {
                        stmt.setString(1, jurisdiction);
                        stmt.setInt(2, storeys);
                        stmt.setInt(3, storeys);
                        stmt.setDouble(4, heightM);
                        stmt.setDouble(5, floorAreaM2);
                        stmt.setString(6, occupancyGroup);
                        ResultSet rs = stmt.executeQuery();
                        return rs.next() && rs.getInt(1) > 0;
                    }
                } catch (SQLException e) {
                    return false;
                }
            });
        }

        public FireProtectionAD.RiserRequirement getRiserRequirement(int storeys, double heightM,
                                                                      double floorAreaM2, String jurisdiction) {
            // Delegate to full AD class for complex queries
            return new FireProtectionAD(session.dbPath).getRiserRequirement(storeys, heightM, floorAreaM2, jurisdiction);
        }
    }

    /**
     * MEP AD Facade.
     */
    public static class MEPADFacade {
        private final ADSession session;

        MEPADFacade(ADSession session) {
            this.session = session;
        }

        public MEPAD.FPCoverage getSprinklerCoverage(String hazardClass) {
            String key = "fp_coverage_" + hazardClass;
            return session.cached(key, () -> {
                try {
                    String sql = """
                        SELECT hazard_class, max_coverage_m2, max_spacing_m, min_spacing_m,
                               wall_distance_m, k_factor, code_ref
                        FROM ad_fp_coverage WHERE hazard_class = ? AND is_active = 1
                        """;
                    try (PreparedStatement stmt = session.conn.prepareStatement(sql)) {
                        stmt.setString(1, hazardClass.toUpperCase());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            return new MEPAD.FPCoverage(
                                rs.getString("hazard_class"),
                                rs.getDouble("max_coverage_m2"),
                                rs.getDouble("max_spacing_m"),
                                rs.getDouble("min_spacing_m"),
                                rs.getDouble("wall_distance_m"),
                                rs.getDouble("k_factor"),
                                rs.getString("code_ref")
                            );
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("[ADSession.mep] Coverage query error: " + e.getMessage());
                }
                return null;
            });
        }

        public MEPAD.ElementMEP getElement(String elementType) {
            String key = "mep_element_" + elementType;
            return session.cached(key, () -> MEPAD.getElement(elementType));
        }
    }

    /**
     * Vertical Circulation AD Facade.
     */
    public static class VerticalCirculationADFacade {
        private final ADSession session;
        private final VerticalCirculationAD delegate;

        VerticalCirculationADFacade(ADSession session) {
            this.session = session;
            this.delegate = new VerticalCirculationAD(session.dbPath);
        }

        public VerticalCirculationAD.StairRequirement getStairRequirement(String occupancyGroup, String jurisdiction) {
            String key = "stair_req_" + occupancyGroup + "_" + jurisdiction;
            return session.cached(key, () -> delegate.getStairRequirement(occupancyGroup, jurisdiction));
        }

        public VerticalCirculationAD.EgressTravel getEgressTravel(String occupancyGroup, String jurisdiction, boolean sprinklered) {
            String key = "egress_" + occupancyGroup + "_" + jurisdiction + "_" + sprinklered;
            return session.cached(key, () -> delegate.getEgressTravel(occupancyGroup, jurisdiction, sprinklered));
        }

        public java.util.List<VerticalCirculationAD.ElevatorRequirement> getElevatorRequirements(
                String elevatorType, int storeys, double travelHeightM, String occupancyGroup, String jurisdiction) {
            return delegate.getElevatorRequirements(elevatorType, storeys, travelHeightM, occupancyGroup, jurisdiction);
        }
    }

    /**
     * Placement Rule AD Facade.
     */
    public static class PlacementRuleADFacade {
        private final ADSession session;

        PlacementRuleADFacade(ADSession session) {
            this.session = session;
        }

        public double getRule(String ruleId, String field, double defaultValue) {
            String key = "placement_" + ruleId + "_" + field;
            return session.cached(key, () -> {
                try {
                    String sql = "SELECT " + field + " FROM ad_placement_rule WHERE rule_id = ?";
                    try (PreparedStatement stmt = session.conn.prepareStatement(sql)) {
                        stmt.setString(1, ruleId);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            double val = rs.getDouble(1);
                            return rs.wasNull() ? defaultValue : val;
                        }
                    }
                } catch (SQLException e) {
                    // Fall through to default
                }
                return defaultValue;
            });
        }

        public double getHeight(String ruleId) {
            return getRule(ruleId, "height_m", 0.0);
        }

        public double getMaxSpacing(String ruleId) {
            return getRule(ruleId, "max_spacing_m", 4.6);
        }
    }

    /**
     * Space Type AD Facade.
     */
    public static class SpaceTypeADFacade {
        private final ADSession session;

        SpaceTypeADFacade(ADSession session) {
            this.session = session;
        }

        public String resolveAlias(String alias) {
            String key = "space_alias_" + alias;
            return session.cached(key, () -> {
                try {
                    String sql = "SELECT space_type_id FROM ad_space_type_alias WHERE alias = ?";
                    try (PreparedStatement stmt = session.conn.prepareStatement(sql)) {
                        stmt.setString(1, alias.toUpperCase());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            return rs.getString(1);
                        }
                    }
                } catch (SQLException e) {
                    // Fall through
                }
                return alias;  // Return original if not found
            });
        }

        public boolean isValid(String spaceType) {
            String key = "space_valid_" + spaceType;
            return session.cached(key, () -> {
                try {
                    String sql = "SELECT COUNT(*) FROM ad_space_type WHERE space_type_id = ? AND is_active = 1";
                    try (PreparedStatement stmt = session.conn.prepareStatement(sql)) {
                        stmt.setString(1, spaceType.toUpperCase());
                        ResultSet rs = stmt.executeQuery();
                        return rs.next() && rs.getInt(1) > 0;
                    }
                } catch (SQLException e) {
                    return false;
                }
            });
        }
    }

    // =========================================================================
    // Test Entry Point
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== ADSession Test ===\n");

        try (ADSession ad = ADSession.open()) {
            System.out.println("Session opened successfully\n");

            // Test Fire Protection
            System.out.println("--- Fire Protection ---");
            boolean fp1 = ad.fireProtection().isSprinklerRequired(18, 54.0, 1500.0, "R", "MALAYSIA");
            System.out.printf("18-storey condo: sprinklers required = %s%n", fp1);

            boolean fp2 = ad.fireProtection().isSprinklerRequired(2, 5.6, 60.0, "R", "MALAYSIA");
            System.out.printf("2-storey house: sprinklers required = %s%n", fp2);

            // Test MEP (should use cache for coverage)
            System.out.println("\n--- MEP ---");
            var cov1 = ad.mep().getSprinklerCoverage("LIGHT");
            System.out.printf("LIGHT coverage: %.1f m², %.1fm spacing%n",
                cov1.maxCoverageM2(), cov1.maxSpacingM());

            var cov2 = ad.mep().getSprinklerCoverage("LIGHT");  // Cache hit
            System.out.printf("LIGHT coverage (cached): %.1f m²%n", cov2.maxCoverageM2());

            // Test Vertical Circulation
            System.out.println("\n--- Vertical Circulation ---");
            var stair = ad.verticalCirculation().getStairRequirement("R", "MALAYSIA");
            if (stair != null) {
                System.out.printf("Stair: %dmm width, %d/%dmm riser%n",
                    stair.minWidthMm(), stair.minRiserMm(), stair.maxRiserMm());
            }

            // Test Egress (uses cached sprinkler status)
            var egress = ad.verticalCirculation().getEgressTravel("R", "MALAYSIA", fp1);
            if (egress != null) {
                System.out.printf("Egress: max %.1fm travel%n", egress.maxTravelM());
            }

            // Test Placement
            System.out.println("\n--- Placement ---");
            double outletHeight = ad.placement().getHeight("OUTLET_WALL");
            System.out.printf("Outlet height: %.2fm%n", outletHeight);

            // Cache stats
            System.out.printf("%nCache size: %d entries%n", ad.cacheSize());

        } catch (SQLException e) {
            System.err.println("Session error: " + e.getMessage());
        }

        System.out.println("\n=== ADSession Test Complete ===");
    }
}
