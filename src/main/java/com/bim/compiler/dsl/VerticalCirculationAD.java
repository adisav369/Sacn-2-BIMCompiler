package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Application Dictionary for Vertical Circulation rules.
 *
 * Queries ad_vert_circ_trigger, ad_stair_requirement, ad_elevator_requirement,
 * ad_egress_travel, ad_pressurization_trigger tables.
 *
 * Usage:
 *   VerticalCirculationAD ad = new VerticalCirculationAD(dbPath);
 *   List<CircTrigger> triggers = ad.getTriggersFor("ELEVATOR", 18, 54.0, "MALAYSIA");
 *   StairRequirement stair = ad.getStairRequirement("R", "MALAYSIA");
 *
 * Phase 55: AD-driven vertical circulation placement.
 */
public class VerticalCirculationAD {

    private final String dbPath;

    public VerticalCirculationAD(String dbPath) {
        this.dbPath = dbPath;
    }

    // =========================================================================
    // Data Classes
    // =========================================================================

    /** Trigger record - when an element type is required */
    public record CircTrigger(
        String triggerId,
        String elementType,      // STAIR, ELEVATOR, FIRE_LIFT, STRETCHER_LIFT
        String triggerType,      // STOREY_COUNT, TRAVEL_HEIGHT, OCCUPANT_LOAD
        int minQuantity,
        String quantityFormula,
        String codeId,
        String clause,
        String description,
        boolean mandatory
    ) {}

    /** Stair requirement - design parameters */
    public record StairRequirement(
        String reqId,
        String occupancyGroup,
        int minWidthMm,
        Double widthPerPersonMm,
        Integer maxOccupantPerStair,
        int maxRiserMm,
        int minRiserMm,
        int minTreadMm,
        double maxFlightRiseM,
        int minLandingMm,
        int handrailHeightMm,
        double fireRatingHr,
        String codeId,
        String jurisdiction
    ) {}

    /** Elevator requirement - specifications */
    public record ElevatorRequirement(
        String reqId,
        String elevatorType,     // PASSENGER, FIRE, STRETCHER, ACCESSIBLE
        String occupancyGroup,
        Integer triggerStoreys,
        Double triggerTravelM,
        int minQuantity,
        String quantityFormula,
        int minWidthMm,
        int minDepthMm,
        int minDoorWidthMm,
        double minLobbyDepthM,
        double minLobbyWidthM,
        double fireRatingHr,
        boolean smokeControl,
        boolean emergencyPower,
        boolean accessible,
        String codeId,
        String jurisdiction
    ) {}

    /** Egress travel limits */
    public record EgressTravel(
        String travelId,
        String occupancyGroup,
        double maxTravelM,
        double maxDeadEndM,
        double maxCommonPathM,
        double sprinkleredBonus,
        String codeId,
        String jurisdiction
    ) {}

    /** Pressurization trigger */
    public record PressurizationTrigger(
        String triggerId,
        String elementType,      // STAIR, ELEVATOR_LOBBY, CORRIDOR
        Integer minStoreys,
        Double minHeightM,
        Integer undergroundStoreys,
        int minPressurePa,
        int maxPressurePa,
        int maxDoorForceN,
        String codeId,
        String description
    ) {}

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Get all circulation triggers that apply for given building parameters.
     *
     * @param elementType STAIR, ELEVATOR, FIRE_LIFT, or STRETCHER_LIFT (null for all)
     * @param storeys Number of storeys
     * @param travelHeightM Total travel height in meters
     * @param jurisdiction MALAYSIA, USA, or INTERNATIONAL
     */
    public List<CircTrigger> getTriggersFor(String elementType, int storeys,
                                             double travelHeightM, String jurisdiction) {
        List<CircTrigger> result = new ArrayList<>();

        String sql = """
            SELECT trigger_id, element_type, trigger_type, min_quantity,
                   quantity_formula, code_id, clause, description, is_mandatory
            FROM ad_vert_circ_trigger
            WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND (
                (trigger_type = 'STOREY_COUNT' AND min_storeys <= ?)
                OR (trigger_type = 'TRAVEL_HEIGHT' AND min_travel_m <= ?)
              )
            """;

        if (elementType != null) {
            sql += " AND element_type = ?";
        }

        sql += " ORDER BY element_type, min_quantity DESC";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jurisdiction);
            stmt.setInt(2, storeys);
            stmt.setDouble(3, travelHeightM);
            if (elementType != null) {
                stmt.setString(4, elementType);
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new CircTrigger(
                    rs.getString("trigger_id"),
                    rs.getString("element_type"),
                    rs.getString("trigger_type"),
                    rs.getInt("min_quantity"),
                    rs.getString("quantity_formula"),
                    rs.getString("code_id"),
                    rs.getString("clause"),
                    rs.getString("description"),
                    rs.getInt("is_mandatory") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("VerticalCirculationAD query error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get the applicable stair requirement for occupancy and jurisdiction.
     */
    public StairRequirement getStairRequirement(String occupancyGroup, String jurisdiction) {
        String sql = """
            SELECT * FROM ad_stair_requirement
            WHERE (occupancy_group = ? OR occupancy_group = 'ANY')
              AND (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
            ORDER BY
              CASE WHEN jurisdiction = ? THEN 0 ELSE 1 END,
              CASE WHEN occupancy_group = ? THEN 0 ELSE 1 END
            LIMIT 1
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, occupancyGroup);
            stmt.setString(2, jurisdiction);
            stmt.setString(3, jurisdiction);
            stmt.setString(4, occupancyGroup);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new StairRequirement(
                    rs.getString("req_id"),
                    rs.getString("occupancy_group"),
                    rs.getInt("min_width_mm"),
                    getDoubleOrNull(rs, "width_per_person_mm"),
                    getIntOrNull(rs, "max_occupant_per_stair"),
                    rs.getInt("max_riser_mm"),
                    rs.getInt("min_riser_mm"),
                    rs.getInt("min_tread_mm"),
                    rs.getDouble("max_flight_rise_m"),
                    rs.getInt("min_landing_length_mm"),
                    rs.getInt("handrail_height_mm"),
                    rs.getDouble("min_fire_rating_hr"),
                    rs.getString("code_id"),
                    rs.getString("jurisdiction")
                );
            }
        } catch (SQLException e) {
            System.err.println("VerticalCirculationAD stair query error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get elevator requirements that apply for given parameters.
     */
    public List<ElevatorRequirement> getElevatorRequirements(
            String elevatorType, int storeys, double travelHeightM,
            String occupancyGroup, String jurisdiction) {

        List<ElevatorRequirement> result = new ArrayList<>();

        String sql = """
            SELECT * FROM ad_elevator_requirement
            WHERE (occupancy_group = ? OR occupancy_group = 'ANY')
              AND (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND (
                (trigger_storeys IS NOT NULL AND trigger_storeys <= ?)
                OR (trigger_travel_m IS NOT NULL AND trigger_travel_m <= ?)
                OR (trigger_storeys IS NULL AND trigger_travel_m IS NULL)
              )
            """;

        if (elevatorType != null) {
            sql += " AND elevator_type = ?";
        }

        sql += " ORDER BY elevator_type, fire_rating_hr DESC";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, occupancyGroup);
            stmt.setString(2, jurisdiction);
            stmt.setInt(3, storeys);
            stmt.setDouble(4, travelHeightM);
            if (elevatorType != null) {
                stmt.setString(5, elevatorType);
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new ElevatorRequirement(
                    rs.getString("req_id"),
                    rs.getString("elevator_type"),
                    rs.getString("occupancy_group"),
                    getIntOrNull(rs, "trigger_storeys"),
                    getDoubleOrNull(rs, "trigger_travel_m"),
                    rs.getInt("min_quantity"),
                    rs.getString("quantity_formula"),
                    rs.getInt("min_width_mm"),
                    rs.getInt("min_depth_mm"),
                    rs.getInt("min_door_width_mm"),
                    rs.getDouble("min_lobby_depth_m"),
                    rs.getDouble("min_lobby_width_m"),
                    rs.getDouble("fire_rating_hr"),
                    rs.getInt("smoke_control") == 1,
                    rs.getInt("emergency_power") == 1,
                    rs.getInt("is_accessible") == 1,
                    rs.getString("code_id"),
                    rs.getString("jurisdiction")
                ));
            }
        } catch (SQLException e) {
            System.err.println("VerticalCirculationAD elevator query error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get egress travel limits for occupancy and jurisdiction.
     */
    public EgressTravel getEgressTravel(String occupancyGroup, String jurisdiction,
                                         boolean sprinklered) {
        String sql = """
            SELECT * FROM ad_egress_travel
            WHERE (occupancy_group = ? OR occupancy_group = 'ANY')
              AND (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
            ORDER BY
              CASE WHEN jurisdiction = ? THEN 0 ELSE 1 END,
              CASE WHEN occupancy_group = ? THEN 0 ELSE 1 END
            LIMIT 1
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, occupancyGroup);
            stmt.setString(2, jurisdiction);
            stmt.setString(3, jurisdiction);
            stmt.setString(4, occupancyGroup);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double bonus = sprinklered ? rs.getDouble("sprinklered_bonus") : 1.0;
                return new EgressTravel(
                    rs.getString("travel_id"),
                    rs.getString("occupancy_group"),
                    rs.getDouble("max_travel_m") * bonus,
                    rs.getDouble("max_dead_end_m") * bonus,
                    rs.getDouble("max_common_path_m") * bonus,
                    bonus,
                    rs.getString("code_id"),
                    rs.getString("jurisdiction")
                );
            }
        } catch (SQLException e) {
            System.err.println("VerticalCirculationAD egress query error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get pressurization triggers for given building parameters.
     */
    public List<PressurizationTrigger> getPressurizationTriggers(
            int storeys, double heightM, int basementLevels, String jurisdiction) {

        List<PressurizationTrigger> result = new ArrayList<>();

        String sql = """
            SELECT * FROM ad_pressurization_trigger
            WHERE (jurisdiction = ? OR jurisdiction = 'INTERNATIONAL')
              AND (
                (min_storeys IS NOT NULL AND min_storeys <= ?)
                OR (min_height_m IS NOT NULL AND min_height_m <= ?)
                OR (underground_storeys IS NOT NULL AND underground_storeys <= ?)
              )
            ORDER BY element_type
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jurisdiction);
            stmt.setInt(2, storeys);
            stmt.setDouble(3, heightM);
            stmt.setInt(4, basementLevels);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new PressurizationTrigger(
                    rs.getString("trigger_id"),
                    rs.getString("element_type"),
                    getIntOrNull(rs, "min_storeys"),
                    getDoubleOrNull(rs, "min_height_m"),
                    getIntOrNull(rs, "underground_storeys"),
                    rs.getInt("min_pressure_pa"),
                    rs.getInt("max_pressure_pa"),
                    rs.getInt("max_door_force_n"),
                    rs.getString("code_id"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            System.err.println("VerticalCirculationAD pressurization query error: " + e.getMessage());
        }

        return result;
    }

    // =========================================================================
    // Calculation Helpers
    // =========================================================================

    /**
     * Calculate required stair width based on occupant load.
     */
    public int calculateStairWidth(int occupantLoad, StairRequirement req) {
        if (req == null) return 1050; // Default fallback

        int baseWidth = req.minWidthMm();
        if (req.widthPerPersonMm() != null && req.maxOccupantPerStair() != null) {
            int personsPerStair = Math.min(occupantLoad, req.maxOccupantPerStair());
            int additionalWidth = (int) (personsPerStair * req.widthPerPersonMm());
            return Math.max(baseWidth, baseWidth + additionalWidth);
        }
        return baseWidth;
    }

    /**
     * Calculate required number of stairs based on occupant load.
     */
    public int calculateStairCount(int occupantLoad, StairRequirement req) {
        if (req == null || req.maxOccupantPerStair() == null) return 2; // Default

        int perStair = req.maxOccupantPerStair();
        return Math.max(2, (int) Math.ceil((double) occupantLoad / perStair));
    }

    /**
     * Calculate required number of elevators using formula.
     */
    public int calculateElevatorCount(int units, int storeys, ElevatorRequirement req) {
        if (req == null) return 1;

        int base = req.minQuantity();
        String formula = req.quantityFormula();

        if (formula == null) return base;

        // Simple formula parser: "1 + floor((units-50)/60)" or "1 + floor(occupants/500)"
        try {
            if (formula.contains("units")) {
                // Parse units-based formula
                int threshold = extractNumber(formula, "units-", ")");
                int divisor = extractNumber(formula, ")/", ")");
                if (threshold > 0 && divisor > 0) {
                    return base + Math.max(0, (units - threshold) / divisor);
                }
            } else if (formula.contains("floors")) {
                // Parse floors-based formula
                int threshold = extractNumber(formula, "floors-", ")");
                int divisor = extractNumber(formula, ")/", ")");
                if (threshold > 0 && divisor > 0) {
                    return base + Math.max(0, (storeys - threshold) / divisor);
                }
            }
        } catch (Exception e) {
            // Fall back to base
        }

        return base;
    }

    private int extractNumber(String formula, String after, String before) {
        int start = formula.indexOf(after);
        if (start < 0) return -1;
        start += after.length();
        int end = formula.indexOf(before, start);
        if (end < 0) end = formula.length();
        try {
            return Integer.parseInt(formula.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

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
     * Generate a compliance report for a building.
     */
    public String generateComplianceReport(int storeys, double heightM,
                                           int occupantLoad, String occupancyGroup,
                                           String jurisdiction, boolean sprinklered) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VERTICAL CIRCULATION COMPLIANCE REPORT ===\n");
        sb.append(String.format("Building: %d storeys, %.1fm height, %d occupants\n",
            storeys, heightM, occupantLoad));
        sb.append(String.format("Occupancy: %s, Jurisdiction: %s, Sprinklered: %s\n\n",
            occupancyGroup, jurisdiction, sprinklered));

        // Triggers
        sb.append("REQUIRED ELEMENTS:\n");
        List<CircTrigger> triggers = getTriggersFor(null, storeys, heightM, jurisdiction);
        for (CircTrigger t : triggers) {
            sb.append(String.format("  [%s] %s: %d required (%s %s)\n",
                t.mandatory() ? "MANDATORY" : "optional",
                t.elementType(), t.minQuantity(), t.codeId(), t.clause()));
        }

        // Stair requirements
        sb.append("\nSTAIR REQUIREMENTS:\n");
        StairRequirement stair = getStairRequirement(occupancyGroup, jurisdiction);
        if (stair != null) {
            int width = calculateStairWidth(occupantLoad, stair);
            int count = calculateStairCount(occupantLoad, stair);
            sb.append(String.format("  Width: %dmm (calculated for %d occupants)\n", width, occupantLoad));
            sb.append(String.format("  Count: %d stairs\n", count));
            sb.append(String.format("  Riser: ≤%dmm, Tread: ≥%dmm\n", stair.maxRiserMm(), stair.minTreadMm()));
            sb.append(String.format("  Fire rating: %.1f hours (%s)\n", stair.fireRatingHr(), stair.codeId()));
        }

        // Elevator requirements
        sb.append("\nELEVATOR REQUIREMENTS:\n");
        List<ElevatorRequirement> elevs = getElevatorRequirements(null, storeys, heightM, occupancyGroup, jurisdiction);
        for (ElevatorRequirement e : elevs) {
            sb.append(String.format("  %s: %dmm x %dmm car, %dmm door\n",
                e.elevatorType(), e.minWidthMm(), e.minDepthMm(), e.minDoorWidthMm()));
            sb.append(String.format("    Lobby: %.1fm x %.1fm, Fire: %.1fhr\n",
                e.minLobbyDepthM(), e.minLobbyWidthM(), e.fireRatingHr()));
        }

        // Egress travel
        sb.append("\nEGRESS TRAVEL LIMITS:\n");
        EgressTravel egress = getEgressTravel(occupancyGroup, jurisdiction, sprinklered);
        if (egress != null) {
            sb.append(String.format("  Max travel: %.1fm%s\n", egress.maxTravelM(),
                sprinklered ? " (sprinklered bonus applied)" : ""));
            sb.append(String.format("  Max dead-end: %.1fm\n", egress.maxDeadEndM()));
            sb.append(String.format("  Max common path: %.1fm (%s)\n", egress.maxCommonPathM(), egress.codeId()));
        }

        // Pressurization
        sb.append("\nPRESSURIZATION REQUIREMENTS:\n");
        List<PressurizationTrigger> press = getPressurizationTriggers(storeys, heightM, 0, jurisdiction);
        if (press.isEmpty()) {
            sb.append("  None required for this building height\n");
        } else {
            for (PressurizationTrigger p : press) {
                sb.append(String.format("  %s: %d-%dPa (%s)\n",
                    p.elementType(), p.minPressurePa(), p.maxPressurePa(), p.description()));
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Test Entry Point
    // =========================================================================

    public static void main(String[] args) {
        String dbPath = "library/component_library.db";
        VerticalCirculationAD ad = new VerticalCirculationAD(dbPath);

        // Test: 18-storey condo in Malaysia
        System.out.println(ad.generateComplianceReport(
            18,           // storeys
            54.0,         // height (18 * 3m)
            360,          // occupant load (20 units/floor * 4 persons * 18 / 4 per stair)
            "R",          // Residential
            "MALAYSIA",   // Jurisdiction
            true          // Sprinklered
        ));

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test: 6-storey school in International
        System.out.println(ad.generateComplianceReport(
            6,            // storeys
            21.0,         // height
            800,          // occupant load
            "E",          // Educational
            "INTERNATIONAL",
            true
        ));
    }
}
