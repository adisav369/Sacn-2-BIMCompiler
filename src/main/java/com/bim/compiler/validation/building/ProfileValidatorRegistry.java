package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.ProfileRegistry;

import java.util.*;

/**
 * Phase 28: Registry mapping Profile names to validators.
 *
 * Each profile represents a regional building code with specific requirements:
 * - Malaysian_Residential → UBBL 1984 compliance
 * - US_Residential_IRC → IRC 2021 compliance
 * - UK_Residential → Building Regulations 2010
 * - Commercial_IBC → IBC 2021 compliance
 *
 * Profile validators check region-specific rules like:
 * - Wet area clustering (Malaysian)
 * - Cross ventilation requirements
 * - Setback requirements
 * - Fire separation distances
 */
public class ProfileValidatorRegistry {

    private static final Map<String, BuildingValidator> VALIDATORS = new HashMap<>();

    static {
        // Malaysian Residential (UBBL 1984)
        VALIDATORS.put("Malaysian_Residential", new MalaysianResidentialValidator());

        // US Residential (IRC 2021)
        VALIDATORS.put("US_Residential_IRC", new USResidentialIRCValidator());

        // UK Residential (Building Regulations 2010)
        VALIDATORS.put("UK_Residential", new UKResidentialValidator());

        // Commercial (IBC 2021)
        VALIDATORS.put("Commercial_IBC", new CommercialIBCValidator());
    }

    /**
     * Get validator for a profile name.
     */
    public static BuildingValidator get(String profileName) {
        return VALIDATORS.get(profileName);
    }

    /**
     * Check if a profile is registered.
     */
    public static boolean hasProfile(String profileName) {
        return VALIDATORS.containsKey(profileName);
    }

    /**
     * Get all registered profile names.
     */
    public static Set<String> getProfileNames() {
        return VALIDATORS.keySet();
    }

    // =========================================================================
    // Profile Validators
    // =========================================================================

    /**
     * Malaysian Residential validator (UBBL 1984).
     * Checks wet area clustering, natural ventilation, etc.
     */
    public static class MalaysianResidentialValidator implements BuildingValidator {

        @Override
        public String getName() {
            return "MalaysianResidentialValidator";
        }

        @Override
        public boolean isRequired() {
            return false; // Advisory, doesn't block
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            for (StoreySpec storey : building.storeys()) {
                // Check 1: Wet areas should be clustered (UBBL requirement)
                validateWetAreaClustering(storey, result);

                // Check 2: Natural ventilation (cross ventilation preferred)
                validateNaturalVentilation(storey, result);
            }

            return result;
        }

        private void validateWetAreaClustering(StoreySpec storey, BuildingValidationResult result) {
            // Find all wet areas (bathroom, kitchen)
            List<RoomSpec> wetAreas = storey.rooms().stream()
                .filter(r -> isWetArea(r.type()))
                .toList();

            if (wetAreas.size() < 2) {
                return; // No clustering needed
            }

            // Check if wet areas are adjacent (share a wall or are nearby)
            for (int i = 0; i < wetAreas.size(); i++) {
                for (int j = i + 1; j < wetAreas.size(); j++) {
                    RoomSpec r1 = wetAreas.get(i);
                    RoomSpec r2 = wetAreas.get(j);

                    double distance = centerDistance(r1, r2);
                    if (distance > 8.0) { // More than 8m apart
                        result.addWarning(
                            r1.name() + "/" + r2.name(),
                            "WetAreaClustering",
                            "Wet areas are %.1fm apart - consider clustering for efficient plumbing",
                            distance
                        );
                    }
                }
            }
        }

        private void validateNaturalVentilation(StoreySpec storey, BuildingValidationResult result) {
            // Check that habitable rooms have windows on opposite or adjacent walls
            // (cross ventilation requirement in tropical climates)
            for (RoomSpec room : storey.rooms()) {
                if (!isHabitable(room.type())) {
                    continue;
                }

                // Count windows per wall
                Map<String, Integer> windowsPerWall = new HashMap<>();
                for (WindowSpec window : storey.windows()) {
                    if (window.roomName().equals(room.name())) {
                        windowsPerWall.merge(window.wall(), 1, Integer::sum);
                    }
                }

                // Tropical buildings should have openings on at least 2 walls
                if (windowsPerWall.size() < 2) {
                    result.addInfo(
                        room.name(),
                        "CrossVentilation",
                        "Room has windows on only %d wall(s) - cross ventilation improves comfort",
                        windowsPerWall.size()
                    );
                }
            }
        }

        private boolean isWetArea(String type) {
            String upper = type.toUpperCase();
            return upper.equals("BATHROOM") || upper.equals("KITCHEN") ||
                   upper.equals("LAUNDRY") || upper.equals("TOILET");
        }

        private boolean isHabitable(String type) {
            String upper = type.toUpperCase();
            return upper.equals("BEDROOM") || upper.equals("LIVING") ||
                   upper.equals("KITCHEN") || upper.equals("OPEN_PLAN");
        }

        private double centerDistance(RoomSpec r1, RoomSpec r2) {
            double cx1 = (r1.minX() + r1.maxX()) / 2;
            double cy1 = (r1.minY() + r1.maxY()) / 2;
            double cx2 = (r2.minX() + r2.maxX()) / 2;
            double cy2 = (r2.minY() + r2.maxY()) / 2;
            return Math.sqrt(Math.pow(cx2 - cx1, 2) + Math.pow(cy2 - cy1, 2));
        }
    }

    /**
     * US Residential validator (IRC 2021).
     * Most checks already in HabitabilityValidator.
     */
    public static class USResidentialIRCValidator implements BuildingValidator {

        @Override
        public String getName() {
            return "USResidentialIRCValidator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            // IRC-specific checks beyond base habitability
            for (StoreySpec storey : building.storeys()) {
                // Check ceiling heights (IRC R305.1)
                validateCeilingHeights(storey, result);
            }

            return result;
        }

        private void validateCeilingHeights(StoreySpec storey, BuildingValidationResult result) {
            // IRC R305.1: Min 7 ft (2.134m) ceiling in habitable rooms
            double minCeiling = 2.134;

            if (storey.height() < minCeiling) {
                result.addWarning(
                    storey.name(),
                    "CeilingHeight",
                    "Storey height (%.2fm) below IRC minimum (%.2fm)",
                    storey.height(), minCeiling
                );
            }
        }
    }

    /**
     * UK Residential validator (Building Regulations 2010).
     */
    public static class UKResidentialValidator implements BuildingValidator {

        @Override
        public String getName() {
            return "UKResidentialValidator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            // UK-specific checks
            for (StoreySpec storey : building.storeys()) {
                // Part M: Accessibility checks
                validateAccessibility(storey, result);
            }

            return result;
        }

        private void validateAccessibility(StoreySpec storey, BuildingValidationResult result) {
            // Part M requires 900mm minimum door width for accessible routes
            double minDoorWidth = 0.9;

            for (DoorSpec door : storey.doors()) {
                if (door.width() < minDoorWidth) {
                    result.addInfo(
                        door.name(),
                        "Accessibility",
                        "Door width (%.2fm) below Part M recommendation (%.2fm)",
                        door.width(), minDoorWidth
                    );
                }
            }
        }
    }

    /**
     * Commercial IBC validator (IBC 2021).
     */
    public static class CommercialIBCValidator implements BuildingValidator {

        @Override
        public String getName() {
            return "CommercialIBCValidator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            for (StoreySpec storey : building.storeys()) {
                // IBC: Two means of egress required
                validateDualEgress(storey, result);

                // IBC: Corridor width requirements
                validateCorridorWidths(storey, result);
            }

            return result;
        }

        private void validateDualEgress(StoreySpec storey, BuildingValidationResult result) {
            // Count stairs (egress points)
            long stairCount = storey.stairs().size();

            if (stairCount < 2) {
                result.addWarning(
                    storey.name(),
                    "DualEgress",
                    "IBC requires 2 means of egress, found %d stairs",
                    stairCount
                );
            }
        }

        private void validateCorridorWidths(StoreySpec storey, BuildingValidationResult result) {
            // IBC 1020.2: Min corridor width 44 inches (1.118m) for occupant load > 50
            double minCorridorWidth = 1.118;

            for (RoomSpec room : storey.rooms()) {
                if ("CORRIDOR".equalsIgnoreCase(room.type())) {
                    double width = Math.min(room.maxX() - room.minX(), room.maxY() - room.minY());
                    if (width < minCorridorWidth) {
                        result.addWarning(
                            room.name(),
                            "CorridorWidth",
                            "Corridor width (%.2fm) below IBC minimum (%.2fm)",
                            width, minCorridorWidth
                        );
                    }
                }
            }
        }
    }
}
