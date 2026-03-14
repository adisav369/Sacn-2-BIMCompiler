package com.bim.compiler.dsl;

import com.bim.compiler.dsl.VerticalCirculationAD.*;
import java.util.*;

/**
 * Validates vertical circulation compliance against AD rules.
 *
 * Usage:
 *   VerticalCirculationValidator validator = new VerticalCirculationValidator(dbPath);
 *   ValidationResult result = validator.validate(buildingSpec, "R", "MALAYSIA", true);
 *   if (!result.isCompliant()) {
 *       System.out.println(result.getReport());
 *   }
 *
 * Phase 56: AD-driven vertical circulation validation.
 * Phase 57B: ADSession integration - uses shared connection when available.
 */
public class VerticalCirculationValidator {

    private static final String DB_PATH = System.getProperty("bom.db");
    private final VerticalCirculationAD ad;

    public VerticalCirculationValidator(String dbPath) {
        this.ad = new VerticalCirculationAD(dbPath);
    }

    /**
     * Phase 57B: Create validator using ADSession when available.
     * Reuses the session's VerticalCirculationAD instance for shared caching.
     */
    public VerticalCirculationValidator() {
        ADSession session = BuildingCompiler.getSession();
        if (session != null) {
            // Reuse session's delegate (shared instance within session)
            this.ad = session.verticalCirculation().getDelegate();
        } else {
            this.ad = new VerticalCirculationAD(DB_PATH);
        }
    }

    // =========================================================================
    // Validation Result
    // =========================================================================

    public record ValidationResult(
        boolean compliant,
        List<Violation> violations,
        List<Recommendation> recommendations,
        VerticalCircSummary provided,
        VerticalCircSummary required
    ) {
        public boolean isCompliant() { return compliant; }

        public String getReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== VERTICAL CIRCULATION VALIDATION ===\n\n");

            sb.append("PROVIDED:\n");
            sb.append(String.format("  Stairs: %d (width: %dmm avg)\n",
                provided.stairCount(), provided.avgStairWidthMm()));
            sb.append(String.format("  Passenger elevators: %d\n", provided.passengerElevators()));
            sb.append(String.format("  Fire lift: %s\n", provided.hasFireLift() ? "YES" : "NO"));
            sb.append(String.format("  Stretcher lift: %s\n", provided.hasStretcherLift() ? "YES" : "NO"));
            sb.append(String.format("  Pressurized stairs: %d\n", provided.pressurizedStairs()));
            sb.append(String.format("  Pressurized lobbies: %d\n", provided.pressurizedLobbies()));

            sb.append("\nREQUIRED:\n");
            sb.append(String.format("  Stairs: %d (width: %dmm min)\n",
                required.stairCount(), required.avgStairWidthMm()));
            sb.append(String.format("  Passenger elevators: %d\n", required.passengerElevators()));
            sb.append(String.format("  Fire lift: %s\n", required.hasFireLift() ? "YES" : "NO"));
            sb.append(String.format("  Stretcher lift: %s\n", required.hasStretcherLift() ? "YES" : "NO"));
            sb.append(String.format("  Pressurized stairs: %d\n", required.pressurizedStairs()));
            sb.append(String.format("  Pressurized lobbies: %d\n", required.pressurizedLobbies()));

            if (!violations.isEmpty()) {
                sb.append("\nVIOLATIONS:\n");
                for (Violation v : violations) {
                    sb.append(String.format("  [%s] %s (%s %s)\n",
                        v.severity(), v.message(), v.codeId(), v.clause()));
                }
            }

            if (!recommendations.isEmpty()) {
                sb.append("\nRECOMMENDATIONS:\n");
                for (Recommendation r : recommendations) {
                    sb.append(String.format("  - %s\n", r.message()));
                }
            }

            sb.append(String.format("\nSTATUS: %s\n", compliant ? "COMPLIANT" : "NON-COMPLIANT"));
            return sb.toString();
        }
    }

    public record Violation(
        String severity,  // ERROR, WARNING
        String message,
        String codeId,
        String clause
    ) {}

    public record Recommendation(
        String message,
        String codeId
    ) {}

    public record VerticalCircSummary(
        int stairCount,
        int avgStairWidthMm,
        int passengerElevators,
        boolean hasFireLift,
        boolean hasStretcherLift,
        int pressurizedStairs,
        int pressurizedLobbies,
        double maxTravelM
    ) {}

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Validate a BuildingSpec against vertical circulation requirements.
     */
    public ValidationResult validate(BuildingSpecs.BuildingSpec spec,
                                     String occupancyGroup,
                                     String jurisdiction,
                                     boolean sprinklered) {

        int storeys = spec.storeys().size();
        double height = calculateBuildingHeight(spec);
        int occupantLoad = estimateOccupantLoad(spec, occupancyGroup);

        // Get provided elements from spec
        VerticalCircSummary provided = extractProvided(spec);

        // Get required elements from AD
        VerticalCircSummary required = calculateRequired(
            storeys, height, occupantLoad, occupancyGroup, jurisdiction, sprinklered);

        // Check violations
        List<Violation> violations = new ArrayList<>();
        List<Recommendation> recommendations = new ArrayList<>();

        // Stair count
        if (provided.stairCount() < required.stairCount()) {
            List<CircTrigger> triggers = ad.getTriggersFor("STAIR", storeys, height, jurisdiction);
            CircTrigger trigger = triggers.isEmpty() ? null : triggers.get(0);
            violations.add(new Violation(
                "ERROR",
                String.format("Insufficient stairs: %d provided, %d required",
                    provided.stairCount(), required.stairCount()),
                trigger != null ? trigger.codeId() : "UBBL_2012",
                trigger != null ? trigger.clause() : "166"
            ));
        }

        // Stair width
        StairRequirement stairReq = ad.getStairRequirement(occupancyGroup, jurisdiction);
        if (stairReq != null && provided.avgStairWidthMm() < stairReq.minWidthMm()) {
            violations.add(new Violation(
                "ERROR",
                String.format("Stair width insufficient: %dmm provided, %dmm required",
                    provided.avgStairWidthMm(), stairReq.minWidthMm()),
                stairReq.codeId(),
                stairReq.jurisdiction()
            ));
        }

        // Fire lift
        if (required.hasFireLift() && !provided.hasFireLift()) {
            List<CircTrigger> triggers = ad.getTriggersFor("FIRE_LIFT", storeys, height, jurisdiction);
            CircTrigger trigger = triggers.isEmpty() ? null : triggers.get(0);
            violations.add(new Violation(
                "ERROR",
                "Fire lift required but not provided",
                trigger != null ? trigger.codeId() : "UBBL_2012",
                trigger != null ? trigger.clause() : "173"
            ));
        }

        // Stretcher lift
        if (required.hasStretcherLift() && !provided.hasStretcherLift()) {
            List<CircTrigger> triggers = ad.getTriggersFor("STRETCHER_LIFT", storeys, height, jurisdiction);
            CircTrigger trigger = triggers.isEmpty() ? null : triggers.get(0);
            violations.add(new Violation(
                "ERROR",
                "Stretcher lift required but not provided",
                trigger != null ? trigger.codeId() : "UBBL_2012",
                trigger != null ? trigger.clause() : "174"
            ));
        }

        // Pressurized stairs
        List<PressurizationTrigger> pressTriggers = ad.getPressurizationTriggers(
            storeys, height, 0, jurisdiction);
        int requiredPressStairs = 0;
        for (PressurizationTrigger pt : pressTriggers) {
            if ("STAIR".equals(pt.elementType())) {
                requiredPressStairs = required.stairCount();
                break;
            }
        }
        if (provided.pressurizedStairs() < requiredPressStairs) {
            violations.add(new Violation(
                "ERROR",
                String.format("Pressurized stairs required: %d provided, %d required",
                    provided.pressurizedStairs(), requiredPressStairs),
                "UBBL_2012",
                "178"
            ));
        }

        // Passenger elevators
        if (provided.passengerElevators() < required.passengerElevators()) {
            violations.add(new Violation(
                "WARNING",
                String.format("Passenger elevators: %d provided, %d recommended",
                    provided.passengerElevators(), required.passengerElevators()),
                "UBBL_2012",
                "172"
            ));
        }

        // Egress travel distance check
        EgressTravel egress = ad.getEgressTravel(occupancyGroup, jurisdiction, sprinklered);
        if (egress != null) {
            double maxTravel = calculateMaxTravelDistance(spec);
            if (maxTravel > egress.maxTravelM()) {
                violations.add(new Violation(
                    "ERROR",
                    String.format("Max travel distance %.1fm exceeds limit %.1fm",
                        maxTravel, egress.maxTravelM()),
                    egress.codeId(),
                    "165"
                ));
            }

            // Dead-end check
            double maxDeadEnd = calculateMaxDeadEnd(spec);
            if (maxDeadEnd > egress.maxDeadEndM()) {
                violations.add(new Violation(
                    "WARNING",
                    String.format("Dead-end corridor %.1fm exceeds limit %.1fm",
                        maxDeadEnd, egress.maxDeadEndM()),
                    egress.codeId(),
                    "165"
                ));
            }
        }

        // Recommendations
        if (storeys >= 10 && provided.passengerElevators() < 2) {
            recommendations.add(new Recommendation(
                "Consider additional passenger elevator for buildings 10+ storeys",
                "Best Practice"
            ));
        }

        if (height > 50 && !provided.hasStretcherLift()) {
            recommendations.add(new Recommendation(
                "Stretcher-capable elevator recommended for buildings >50m",
                "UBBL_2012"
            ));
        }

        boolean compliant = violations.stream().noneMatch(v -> "ERROR".equals(v.severity()));

        return new ValidationResult(compliant, violations, recommendations, provided, required);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private VerticalCircSummary extractProvided(BuildingSpecs.BuildingSpec spec) {
        int stairCount = 0;
        int totalStairWidth = 0;
        int passengerElevators = 0;
        boolean hasFireLift = false;
        boolean hasStretcherLift = false;
        int pressurizedStairs = 0;
        int pressurizedLobbies = 0;

        for (var storey : spec.storeys()) {
            for (var stair : storey.stairs()) {
                stairCount++;
                totalStairWidth += (int)(stair.width() * 1000);
                // Check if pressurized (would need to extend StairSpec)
                // For now, assume non-pressurized unless spec says otherwise
            }

            // Check for elevators in rooms (elevator shafts/lobbies)
            for (var room : storey.rooms()) {
                String type = room.type().toUpperCase();
                if (type.contains("ELEVATOR") || type.contains("LIFT")) {
                    if (type.contains("FIRE")) {
                        hasFireLift = true;
                    } else if (type.contains("STRETCHER")) {
                        hasStretcherLift = true;
                    } else {
                        passengerElevators++;
                    }
                    if (type.contains("PRESSUR")) {
                        pressurizedLobbies++;
                    }
                }
                if (type.contains("STAIR") && type.contains("PRESSUR")) {
                    pressurizedStairs++;
                }
            }
        }

        // Deduplicate stair count across storeys (same stair appears on each floor)
        int uniqueStairs = stairCount > 0 ? Math.max(1, stairCount / Math.max(1, spec.storeys().size())) : 0;
        int avgWidth = stairCount > 0 ? totalStairWidth / stairCount : 0;

        return new VerticalCircSummary(
            uniqueStairs, avgWidth,
            Math.max(1, passengerElevators / Math.max(1, spec.storeys().size())),
            hasFireLift, hasStretcherLift,
            pressurizedStairs / Math.max(1, spec.storeys().size()),
            pressurizedLobbies / Math.max(1, spec.storeys().size()),
            0  // Calculated separately
        );
    }

    private VerticalCircSummary calculateRequired(int storeys, double height,
                                                   int occupantLoad, String occupancyGroup,
                                                   String jurisdiction, boolean sprinklered) {

        // Stair requirements
        List<CircTrigger> stairTriggers = ad.getTriggersFor("STAIR", storeys, height, jurisdiction);
        int requiredStairs = 1;
        for (CircTrigger t : stairTriggers) {
            requiredStairs = Math.max(requiredStairs, t.minQuantity());
        }

        StairRequirement stairReq = ad.getStairRequirement(occupancyGroup, jurisdiction);
        int minStairWidth = stairReq != null ? stairReq.minWidthMm() : 1050;
        int calcStairs = ad.calculateStairCount(occupantLoad, stairReq);
        requiredStairs = Math.max(requiredStairs, calcStairs);

        // Elevator requirements
        List<ElevatorRequirement> elevReqs = ad.getElevatorRequirements(
            "PASSENGER", storeys, height, occupancyGroup, jurisdiction);
        int requiredElevators = elevReqs.isEmpty() ? 0 : elevReqs.get(0).minQuantity();

        // Fire lift
        List<CircTrigger> fireTriggers = ad.getTriggersFor("FIRE_LIFT", storeys, height, jurisdiction);
        boolean needsFireLift = !fireTriggers.isEmpty();

        // Stretcher lift
        List<CircTrigger> stretcherTriggers = ad.getTriggersFor("STRETCHER_LIFT", storeys, height, jurisdiction);
        boolean needsStretcherLift = !stretcherTriggers.isEmpty();

        // Pressurization
        List<PressurizationTrigger> pressTriggers = ad.getPressurizationTriggers(
            storeys, height, 0, jurisdiction);
        int pressurizedStairs = 0;
        int pressurizedLobbies = 0;
        for (PressurizationTrigger pt : pressTriggers) {
            if ("STAIR".equals(pt.elementType())) {
                pressurizedStairs = requiredStairs;
            } else if ("ELEVATOR_LOBBY".equals(pt.elementType())) {
                pressurizedLobbies = 1;
            }
        }

        // Egress travel
        EgressTravel egress = ad.getEgressTravel(occupancyGroup, jurisdiction, sprinklered);
        double maxTravel = egress != null ? egress.maxTravelM() : 60.0;

        return new VerticalCircSummary(
            requiredStairs, minStairWidth,
            requiredElevators,
            needsFireLift, needsStretcherLift,
            pressurizedStairs, pressurizedLobbies,
            maxTravel
        );
    }

    private double calculateBuildingHeight(BuildingSpecs.BuildingSpec spec) {
        double height = 0;
        for (var storey : spec.storeys()) {
            height += storey.height();
        }
        return height;
    }

    private int estimateOccupantLoad(BuildingSpecs.BuildingSpec spec, String occupancyGroup) {
        // Simplified occupant load calculation
        // R (Residential): 200 gross sq ft per person ≈ 18.5 m²
        // B (Business): 100 gross sq ft per person ≈ 9.3 m²
        // E (Educational): 20 net sq ft per person ≈ 1.9 m²
        // A (Assembly): varies by type

        double totalArea = 0;
        for (var storey : spec.storeys()) {
            for (var room : storey.rooms()) {
                totalArea += (room.maxX() - room.minX()) * (room.maxY() - room.minY());
            }
        }

        double areaPerPerson = switch (occupancyGroup) {
            case "R" -> 18.5;
            case "B" -> 9.3;
            case "E" -> 1.9;
            case "A" -> 5.0;
            case "M" -> 5.6;
            default -> 10.0;
        };

        return (int) Math.ceil(totalArea / areaPerPerson);
    }

    private double calculateMaxTravelDistance(BuildingSpecs.BuildingSpec spec) {
        // Simplified: estimate based on floor plate diagonal
        if (spec.storeys().isEmpty()) return 0;

        var firstStorey = spec.storeys().get(0);
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;

        for (var room : firstStorey.rooms()) {
            minX = Math.min(minX, room.minX());
            maxX = Math.max(maxX, room.maxX());
            minY = Math.min(minY, room.minY());
            maxY = Math.max(maxY, room.maxY());
        }

        // Diagonal / 2 as rough estimate (assumes stairs at center or ends)
        double width = maxX - minX;
        double depth = maxY - minY;
        return Math.sqrt(width * width + depth * depth) / 2;
    }

    private double calculateMaxDeadEnd(BuildingSpecs.BuildingSpec spec) {
        // Simplified: assume dead-end is 1/4 of longest corridor
        // Real implementation would trace corridor geometry
        if (spec.storeys().isEmpty()) return 0;

        for (var storey : spec.storeys()) {
            for (var room : storey.rooms()) {
                if (room.type().toUpperCase().contains("CORRIDOR")) {
                    double length = Math.max(
                        room.maxX() - room.minX(),
                        room.maxY() - room.minY()
                    );
                    return length / 4;  // Conservative estimate
                }
            }
        }
        return 0;
    }

    // =========================================================================
    // Test Entry Point
    // =========================================================================

    public static void main(String[] args) {
        String dbPath = System.getProperty("bom.db");
        VerticalCirculationValidator validator = new VerticalCirculationValidator(dbPath);

        System.out.println("=== Vertical Circulation Validator Test ===\n");

        // Test with AD directly (no BuildingSpec needed for basic query)
        VerticalCirculationAD ad = new VerticalCirculationAD(dbPath);

        System.out.println("18-storey condo in Malaysia:");
        System.out.println(ad.generateComplianceReport(18, 54.0, 360, "R", "MALAYSIA", true));

        System.out.println("\n6-storey school in Malaysia:");
        System.out.println(ad.generateComplianceReport(6, 19.2, 800, "E", "MALAYSIA", true));

        System.out.println("\n4-storey office (International):");
        System.out.println(ad.generateComplianceReport(4, 14.0, 200, "B", "INTERNATIONAL", false));
    }
}
