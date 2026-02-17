package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Application Dictionary for Fire Protection rules.
 *
 * Queries ad_fp_trigger, ad_fire_riser_requirement, ad_fire_compartment tables.
 * Also uses ad_fp_coverage from MEPAD for sprinkler spacing rules.
 *
 * Usage:
 *   FireProtectionAD ad = new FireProtectionAD(dbPath);
 *   List<FPTrigger> triggers = ad.getTriggersFor(18, 54.0, 1500.0, "R", "MALAYSIA");
 *   RiserRequirement riser = ad.getRiserRequirement(18, 54.0, 1500.0, "MALAYSIA");
 *   boolean required = ad.isSprinklerRequired(18, 54.0, 1500.0, "R", "MALAYSIA");
 *
 * Phase 57: Fire Protection AD for sprinkler triggers and riser sizing.
 */
public class FireProtectionAD {

    private final String dbPath;

    public FireProtectionAD(String dbPath) {
        this.dbPath = dbPath;
    }

    // =========================================================================
    // Data Classes
    // =========================================================================

    /** FP Trigger - when fire protection is required */
    public record FPTrigger(
        String triggerId,
        String triggerType,      // STOREY_COUNT, BUILDING_HEIGHT, FLOOR_AREA, OCCUPANCY
        String elementType,      // SPRINKLER, STANDPIPE, FIRE_ALARM, SMOKE_DETECTION
        Integer minStoreys,
        Double minHeightM,
        Double minFloorAreaM2,
        String occupancyGroup,
        int minQuantity,
        String quantityFormula,
        String codeId,
        String clause,
        String description,
        boolean mandatory
    ) {}

    /** Fire Riser Requirement - sizing specifications */
    public record RiserRequirement(
        String reqId,
        String riserType,        // WET_RISER, DRY_RISER, COMBINED, STANDPIPE
        String hazardClass,      // LIGHT, ORDINARY_1, ORDINARY_2, EXTRA
        Integer minStoreys,
        Integer maxStoreys,
        Double minHeightM,
        Double maxHeightM,
        Double maxFloorAreaM2,
        int pipeDiameterMm,
        Integer branchDiameterMm,
        Integer minFlowLpm,
        Double minPressureBar,
        boolean pumpRequired,
        Integer tankCapacityL,
        String valveType,
        String codeId,
        String clause,
        String description,
        String jurisdiction
    ) {}

    /** Fire Compartment Rule */
    public record CompartmentRule(
        String compartmentId,
        String occupancyGroup,
        String spaceType,
        double maxAreaM2,
        Double maxAreaSprinkM2,
        double fireRatingHr,
        boolean requiresSprinkler,
        boolean requiresDetection,
        boolean requiresSmokeControl,
        String codeId,
        String clause,
        String jurisdiction
    ) {}

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Get all FP triggers that apply for given building parameters.
     *
     * @param storeys Number of storeys (positive = above ground, negative = basement)
     * @param heightM Building height in meters
     * @param floorAreaM2 Typical floor area in m²
     * @param occupancyGroup IBC occupancy group (R, A, B, E, etc.)
     * @param jurisdiction MALAYSIA or INTERNATIONAL
     */
    public List<FPTrigger> getTriggersFor(int storeys, double heightM, double floorAreaM2,
                                          String occupancyGroup, String jurisdiction) {
        List<FPTrigger> result = new ArrayList<>();

        String sql = """
            SELECT trigger_id, trigger_type, element_type, min_storeys, min_height_m,
                   min_floor_area_m2, occupancy_group, min_quantity, quantity_formula,
                   code_id, clause, description, is_mandatory
            FROM ad_fp_trigger
            WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND is_active = 1
              AND (
                (trigger_type = 'STOREY_COUNT' AND min_storeys IS NOT NULL AND min_storeys > 0 AND min_storeys <= ?)
                OR (trigger_type = 'STOREY_COUNT' AND min_storeys IS NOT NULL AND min_storeys < 0 AND ? < 0)
                OR (trigger_type = 'BUILDING_HEIGHT' AND min_height_m IS NOT NULL AND min_height_m <= ?)
                OR (trigger_type = 'FLOOR_AREA' AND min_floor_area_m2 IS NOT NULL AND min_floor_area_m2 <= ?)
                OR (trigger_type = 'OCCUPANCY' AND occupancy_group = ?)
              )
            ORDER BY element_type, is_mandatory DESC
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jurisdiction);
            stmt.setInt(2, storeys);
            stmt.setInt(3, storeys);  // For basement check
            stmt.setDouble(4, heightM);
            stmt.setDouble(5, floorAreaM2);
            stmt.setString(6, occupancyGroup);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new FPTrigger(
                    rs.getString("trigger_id"),
                    rs.getString("trigger_type"),
                    rs.getString("element_type"),
                    getIntOrNull(rs, "min_storeys"),
                    getDoubleOrNull(rs, "min_height_m"),
                    getDoubleOrNull(rs, "min_floor_area_m2"),
                    rs.getString("occupancy_group"),
                    rs.getInt("min_quantity"),
                    rs.getString("quantity_formula"),
                    rs.getString("code_id"),
                    rs.getString("clause"),
                    rs.getString("description"),
                    rs.getInt("is_mandatory") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("FireProtectionAD trigger query error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get all triggers for a specific element type.
     */
    public List<FPTrigger> getTriggersForElement(String elementType, int storeys,
                                                  double heightM, double floorAreaM2,
                                                  String jurisdiction) {
        return getTriggersFor(storeys, heightM, floorAreaM2, null, jurisdiction).stream()
            .filter(t -> elementType.equals(t.elementType()))
            .toList();
    }

    /**
     * Check if sprinklers are required for given building parameters.
     */
    public boolean isSprinklerRequired(int storeys, double heightM, double floorAreaM2,
                                       String occupancyGroup, String jurisdiction) {
        return getTriggersFor(storeys, heightM, floorAreaM2, occupancyGroup, jurisdiction).stream()
            .anyMatch(t -> "SPRINKLER".equals(t.elementType()) && t.mandatory());
    }

    /**
     * Check if standpipe is required.
     */
    public boolean isStandpipeRequired(int storeys, double heightM, String jurisdiction) {
        return getTriggersFor(storeys, heightM, 0, null, jurisdiction).stream()
            .anyMatch(t -> "STANDPIPE".equals(t.elementType()) && t.mandatory());
    }

    /**
     * Get the appropriate riser requirement for building parameters.
     *
     * @param storeys Number of storeys
     * @param heightM Building height in meters
     * @param floorAreaM2 Floor area in m²
     * @param jurisdiction MALAYSIA or INTERNATIONAL
     */
    public RiserRequirement getRiserRequirement(int storeys, double heightM,
                                                 double floorAreaM2, String jurisdiction) {
        String sql = """
            SELECT * FROM ad_fire_riser_requirement
            WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND is_active = 1
              AND (min_storeys IS NULL OR min_storeys <= ?)
              AND (max_storeys IS NULL OR max_storeys >= ?)
              AND (min_height_m IS NULL OR min_height_m <= ?)
              AND (max_height_m IS NULL OR max_height_m >= ?)
              AND (max_floor_area_m2 IS NULL OR max_floor_area_m2 >= ?)
            ORDER BY
              CASE WHEN jurisdiction = ? THEN 0 ELSE 1 END,
              pipe_diameter_mm DESC
            LIMIT 1
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jurisdiction);
            stmt.setInt(2, storeys);
            stmt.setInt(3, storeys);
            stmt.setDouble(4, heightM);
            stmt.setDouble(5, heightM);
            stmt.setDouble(6, floorAreaM2);
            stmt.setString(7, jurisdiction);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return parseRiserRequirement(rs);
            }
        } catch (SQLException e) {
            System.err.println("FireProtectionAD riser query error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get all applicable riser requirements (for multi-zone buildings).
     */
    public List<RiserRequirement> getAllRiserRequirements(int storeys, double heightM,
                                                          double floorAreaM2, String jurisdiction) {
        List<RiserRequirement> result = new ArrayList<>();

        String sql = """
            SELECT * FROM ad_fire_riser_requirement
            WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND is_active = 1
              AND (min_storeys IS NULL OR min_storeys <= ?)
              AND (max_storeys IS NULL OR max_storeys >= ?)
            ORDER BY riser_type, pipe_diameter_mm DESC
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jurisdiction);
            stmt.setInt(2, storeys);
            stmt.setInt(3, storeys);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(parseRiserRequirement(rs));
            }
        } catch (SQLException e) {
            System.err.println("FireProtectionAD getAllRisers error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get fire compartment rules for an occupancy group.
     */
    public CompartmentRule getCompartmentRule(String occupancyGroup, String spaceType,
                                               String jurisdiction) {
        String sql = """
            SELECT * FROM ad_fire_compartment
            WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND is_active = 1
              AND (occupancy_group = ? OR occupancy_group = 'ANY')
              AND (space_type IS NULL OR space_type = ?)
            ORDER BY
              CASE WHEN jurisdiction = ? THEN 0 ELSE 1 END,
              CASE WHEN space_type = ? THEN 0 ELSE 1 END
            LIMIT 1
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jurisdiction);
            stmt.setString(2, occupancyGroup);
            stmt.setString(3, spaceType);
            stmt.setString(4, jurisdiction);
            stmt.setString(5, spaceType);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new CompartmentRule(
                    rs.getString("compartment_id"),
                    rs.getString("occupancy_group"),
                    rs.getString("space_type"),
                    rs.getDouble("max_area_m2"),
                    getDoubleOrNull(rs, "max_area_sprink_m2"),
                    rs.getDouble("min_fire_rating_hr"),
                    rs.getInt("requires_sprinkler") == 1,
                    rs.getInt("requires_detection") == 1,
                    rs.getInt("requires_smoke_ctrl") == 1,
                    rs.getString("code_id"),
                    rs.getString("clause"),
                    rs.getString("jurisdiction")
                );
            }
        } catch (SQLException e) {
            System.err.println("FireProtectionAD compartment query error: " + e.getMessage());
        }

        return null;
    }

    // =========================================================================
    // Calculation Helpers
    // =========================================================================

    /**
     * Calculate required number of sprinkler heads per floor.
     *
     * @param floorAreaM2 Floor area in m²
     * @param coverageM2 Coverage per head (from ad_fp_coverage)
     */
    public int calculateSprinklerCount(double floorAreaM2, double coverageM2) {
        if (coverageM2 <= 0) return 0;
        return (int) Math.ceil(floorAreaM2 / coverageM2);
    }

    /**
     * Calculate required tank capacity.
     *
     * @param riser Riser requirement with flow rate
     * @param durationMinutes Design duration (typically 30-60 min)
     */
    public int calculateTankCapacity(RiserRequirement riser, int durationMinutes) {
        if (riser == null || riser.minFlowLpm() == null) return 0;
        return riser.minFlowLpm() * durationMinutes;
    }

    /**
     * Check if a fire pump is required based on building height.
     *
     * @param heightM Building height
     * @param riser Riser requirement
     */
    public boolean isPumpRequired(double heightM, RiserRequirement riser) {
        if (riser == null) return false;
        if (riser.pumpRequired()) return true;

        // Calculate if gravity head is insufficient
        // Rule: pump needed if static pressure < required + (height * 0.1 bar/m)
        double requiredPressure = riser.minPressureBar() != null ? riser.minPressureBar() : 3.0;
        double gravityPressure = 3.0; // Assume 30m head tank standard
        double heightPressure = heightM * 0.098; // 0.098 bar per meter

        return (gravityPressure - heightPressure) < requiredPressure;
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private RiserRequirement parseRiserRequirement(ResultSet rs) throws SQLException {
        return new RiserRequirement(
            rs.getString("req_id"),
            rs.getString("riser_type"),
            rs.getString("hazard_class"),
            getIntOrNull(rs, "min_storeys"),
            getIntOrNull(rs, "max_storeys"),
            getDoubleOrNull(rs, "min_height_m"),
            getDoubleOrNull(rs, "max_height_m"),
            getDoubleOrNull(rs, "max_floor_area_m2"),
            rs.getInt("pipe_diameter_mm"),
            getIntOrNull(rs, "branch_diameter_mm"),
            getIntOrNull(rs, "min_flow_lpm"),
            getDoubleOrNull(rs, "min_pressure_bar"),
            rs.getInt("pump_required") == 1,
            getIntOrNull(rs, "tank_capacity_l"),
            rs.getString("valve_type"),
            rs.getString("code_id"),
            rs.getString("clause"),
            rs.getString("description"),
            rs.getString("jurisdiction")
        );
    }

    private Double getDoubleOrNull(ResultSet rs, String column) throws SQLException {
        double val = rs.getDouble(column);
        return rs.wasNull() ? null : val;
    }

    private Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    // =========================================================================
    // Summary Report
    // =========================================================================

    /**
     * Generate a fire protection compliance report for a building.
     */
    public String generateComplianceReport(int storeys, double heightM, double floorAreaM2,
                                           String occupancyGroup, String jurisdiction) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FIRE PROTECTION COMPLIANCE REPORT ===\n");
        sb.append(String.format("Building: %d storeys, %.1fm height, %.0fm² floor area\n",
            storeys, heightM, floorAreaM2));
        sb.append(String.format("Occupancy: %s, Jurisdiction: %s\n\n", occupancyGroup, jurisdiction));

        // Triggers
        sb.append("TRIGGERED REQUIREMENTS:\n");
        List<FPTrigger> triggers = getTriggersFor(storeys, heightM, floorAreaM2, occupancyGroup, jurisdiction);
        if (triggers.isEmpty()) {
            sb.append("  No fire protection triggers applicable\n");
        } else {
            for (FPTrigger t : triggers) {
                sb.append(String.format("  [%s] %s: %s (%s %s)\n",
                    t.mandatory() ? "MANDATORY" : "optional",
                    t.elementType(), t.description(), t.codeId(), t.clause()));
            }
        }

        // Sprinkler status
        sb.append("\nSPRINKLER SYSTEM:\n");
        boolean sprinklered = isSprinklerRequired(storeys, heightM, floorAreaM2, occupancyGroup, jurisdiction);
        sb.append(String.format("  Required: %s\n", sprinklered ? "YES" : "NO"));
        if (sprinklered) {
            // Get coverage from MEPAD
            MEPAD.FPCoverage cov = MEPAD.getSprinklerCoverage("LIGHT");
            if (cov != null) {
                int heads = calculateSprinklerCount(floorAreaM2, cov.maxCoverageM2());
                sb.append(String.format("  Coverage: %.1f m² per head (max %.1fm spacing)\n",
                    cov.maxCoverageM2(), cov.maxSpacingM()));
                sb.append(String.format("  Est. heads per floor: %d\n", heads));
            }
        }

        // Riser requirements
        sb.append("\nRISER REQUIREMENTS:\n");
        RiserRequirement riser = getRiserRequirement(storeys, heightM, floorAreaM2, jurisdiction);
        if (riser != null) {
            sb.append(String.format("  Type: %s (%s)\n", riser.riserType(), riser.hazardClass()));
            sb.append(String.format("  Pipe: %dmm main", riser.pipeDiameterMm()));
            if (riser.branchDiameterMm() != null) {
                sb.append(String.format(", %dmm branch", riser.branchDiameterMm()));
            }
            sb.append("\n");
            if (riser.minFlowLpm() != null) {
                sb.append(String.format("  Flow: %d lpm @ %.1f bar\n",
                    riser.minFlowLpm(), riser.minPressureBar()));
            }
            boolean pumpNeeded = isPumpRequired(heightM, riser);
            sb.append(String.format("  Fire pump: %s\n", pumpNeeded ? "REQUIRED" : "Not required"));
            if (riser.tankCapacityL() != null) {
                sb.append(String.format("  Tank capacity: %,d L\n", riser.tankCapacityL()));
            }
            sb.append(String.format("  Code: %s %s\n", riser.codeId(), riser.clause()));
        } else {
            sb.append("  No specific riser requirement found\n");
        }

        // Compartmentation
        sb.append("\nFIRE COMPARTMENTATION:\n");
        CompartmentRule comp = getCompartmentRule(occupancyGroup, null, jurisdiction);
        if (comp != null) {
            sb.append(String.format("  Max compartment: %.0f m²", comp.maxAreaM2()));
            if (comp.maxAreaSprinkM2() != null) {
                sb.append(String.format(" (%.0f m² if sprinklered)", comp.maxAreaSprinkM2()));
            }
            sb.append("\n");
            sb.append(String.format("  Fire rating: %.1f hours\n", comp.fireRatingHr()));
            sb.append(String.format("  Code: %s %s\n", comp.codeId(), comp.clause()));
        } else {
            sb.append("  No compartment rules found\n");
        }

        return sb.toString();
    }

    // =========================================================================
    // Test Entry Point
    // =========================================================================

    public static void main(String[] args) {
        String dbPath = "library/component_library.db";
        FireProtectionAD ad = new FireProtectionAD(dbPath);

        // Test: 18-storey condo in Malaysia
        System.out.println(ad.generateComplianceReport(
            18,           // storeys
            54.0,         // height (18 * 3m)
            1500.0,       // floor area per floor
            "R",          // Residential
            "MALAYSIA"    // Jurisdiction
        ));

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test: 6-storey school in International
        System.out.println(ad.generateComplianceReport(
            6,            // storeys
            21.0,         // height
            800.0,        // floor area
            "E",          // Educational
            "INTERNATIONAL"
        ));

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test: 3-storey house (should not trigger)
        System.out.println(ad.generateComplianceReport(
            3,            // storeys
            9.0,          // height
            200.0,        // floor area
            "R",          // Residential
            "MALAYSIA"
        ));
    }
}
