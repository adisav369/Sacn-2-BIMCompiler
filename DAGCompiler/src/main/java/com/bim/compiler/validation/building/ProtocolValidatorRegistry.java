/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.ProtocolValidator;
import com.bim.compiler.dsl.ProtocolValidator.*;

import java.util.*;

/**
 * Phase 28: Registry mapping Protocol names to validators.
 *
 * Each protocol represents a building type with specific requirements:
 * - Residential_Single_Storey → House requirements
 * - Residential_Multi_Storey → Multi-level house
 * - Apartment_Unit → Individual unit within building
 * - Commercial_Office → Office building
 *
 * Protocol validators check building-type-specific rules like:
 * - Required spaces (BEDROOM, BATHROOM, etc.)
 * - Excluded spaces (no BEDROOM in Commercial_Office)
 * - Space count limits (min/max bedrooms)
 */
public class ProtocolValidatorRegistry {

    private static final Map<String, BuildingValidator> VALIDATORS = new HashMap<>();

    static {
        // Residential protocols
        VALIDATORS.put("Residential_Single_Storey", new ResidentialSingleStoreyValidator());
        VALIDATORS.put("Residential_Multi_Storey", new ResidentialMultiStoreyValidator());
        VALIDATORS.put("Apartment_Unit", new ApartmentUnitValidator());

        // Commercial protocols
        VALIDATORS.put("Commercial_Office", new CommercialOfficeValidator());
    }

    /**
     * Get validator for a protocol name.
     */
    public static BuildingValidator get(String protocolName) {
        return VALIDATORS.get(protocolName);
    }

    /**
     * Check if a protocol is registered.
     */
    public static boolean hasProtocol(String protocolName) {
        return VALIDATORS.containsKey(protocolName);
    }

    /**
     * Get all registered protocol names.
     */
    public static Set<String> getProtocolNames() {
        return VALIDATORS.keySet();
    }

    // =========================================================================
    // Protocol Validators
    // =========================================================================

    /**
     * Base class for protocol validators that use ProtocolValidator definitions.
     */
    private static abstract class BaseProtocolValidator implements BuildingValidator {

        protected abstract String getProtocolName();

        @Override
        public boolean isRequired() {
            return false; // Protocol compliance is advisory
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            ProtocolDef protocol = ProtocolValidator.getProtocol(getProtocolName());
            if (protocol == null) {
                result.addWarning("Building", "ProtocolLookup",
                    "Protocol '%s' not found in registry", getProtocolName());
                return result;
            }

            // Count spaces by type
            Map<String, Integer> spaceCounts = countSpaces(building);

            // Check required spaces
            for (SpaceRequirement req : protocol.requiredSpaces()) {
                int count = getCountForType(req.spaceType(), spaceCounts);

                if (count < req.minCount()) {
                    // Check for OPEN_PLAN satisfying LIVING/KITCHEN requirements
                    if (canSatisfyViaOpenPlan(req.spaceType(), spaceCounts)) {
                        continue;
                    }

                    result.addWarning(
                        "Building",
                        "RequiredSpace",
                        "Protocol requires at least %d %s, found %d",
                        req.minCount(), req.spaceType(), count
                    );
                }

                if (req.maxCount() > 0 && count > req.maxCount()) {
                    result.addInfo(
                        "Building",
                        "SpaceCount",
                        "%s count (%d) exceeds protocol maximum (%d)",
                        req.spaceType(), count, req.maxCount()
                    );
                }
            }

            // Check excluded spaces
            for (String excluded : protocol.excludedSpaces()) {
                int count = getCountForType(excluded, spaceCounts);
                if (count > 0) {
                    result.addWarning(
                        "Building",
                        "ExcludedSpace",
                        "Protocol excludes %s but %d found",
                        excluded, count
                    );
                }
            }

            return result;
        }

        private Map<String, Integer> countSpaces(BuildingSpec building) {
            Map<String, Integer> counts = new HashMap<>();
            for (StoreySpec storey : building.storeys()) {
                for (RoomSpec room : storey.rooms()) {
                    String type = normalizeType(room.type());
                    counts.merge(type, 1, Integer::sum);
                }
            }
            return counts;
        }

        private String normalizeType(String type) {
            String upper = type.toUpperCase();
            return switch (upper) {
                case "BILIK", "BILIK_UTAMA" -> "BEDROOM";
                case "BILIK_MANDI", "TANDAS" -> "BATHROOM";
                case "DAPUR" -> "KITCHEN";
                case "RUANG_TAMU" -> "LIVING";
                case "ANJUNG" -> "PORCH";
                default -> upper;
            };
        }

        private int getCountForType(String type, Map<String, Integer> counts) {
            int count = counts.getOrDefault(type, 0);

            // Check aliases
            if (type.equals("BEDROOM")) {
                count += counts.getOrDefault("BILIK", 0);
                count += counts.getOrDefault("BILIK_UTAMA", 0);
            } else if (type.equals("BATHROOM")) {
                count += counts.getOrDefault("BILIK_MANDI", 0);
                count += counts.getOrDefault("TANDAS", 0);
            }

            return count;
        }

        private boolean canSatisfyViaOpenPlan(String type, Map<String, Integer> counts) {
            // OPEN_PLAN with zones can satisfy LIVING/KITCHEN requirements
            if ((type.equals("LIVING") || type.equals("KITCHEN")) &&
                counts.getOrDefault("OPEN_PLAN", 0) > 0) {
                return true;
            }
            return false;
        }
    }

    /**
     * Residential Single Storey validator.
     */
    public static class ResidentialSingleStoreyValidator extends BaseProtocolValidator {

        @Override
        public String getName() {
            return "ResidentialSingleStoreyValidator";
        }

        @Override
        protected String getProtocolName() {
            return "Residential_Single_Storey";
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = super.validate(building);

            // Additional: Single storey should have no stairs to upper floors
            for (StoreySpec storey : building.storeys()) {
                if (!storey.stairs().isEmpty() && storey.level() == 0) {
                    // Check if stair leads to another storey (not basement access)
                    for (StairSpec stair : storey.stairs()) {
                        if (stair.toStorey() != null && !stair.toStorey().toLowerCase().contains("basement")) {
                            result.addInfo(
                                stair.name(),
                                "SingleStorey",
                                "Single storey protocol but stair to '%s' found",
                                stair.toStorey()
                            );
                        }
                    }
                }
            }

            return result;
        }
    }

    /**
     * Residential Multi Storey validator.
     */
    public static class ResidentialMultiStoreyValidator extends BaseProtocolValidator {

        @Override
        public String getName() {
            return "ResidentialMultiStoreyValidator";
        }

        @Override
        protected String getProtocolName() {
            return "Residential_Multi_Storey";
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = super.validate(building);

            // Multi-storey requires at least one stair
            boolean hasStair = building.storeys().stream()
                .anyMatch(s -> !s.stairs().isEmpty());

            if (building.storeys().size() > 1 && !hasStair) {
                result.addWarning(
                    "Building",
                    "VerticalCirculation",
                    "Multi-storey building requires at least one stair"
                );
            }

            return result;
        }
    }

    /**
     * Apartment Unit validator.
     */
    public static class ApartmentUnitValidator extends BaseProtocolValidator {

        @Override
        public String getName() {
            return "ApartmentUnitValidator";
        }

        @Override
        protected String getProtocolName() {
            return "Apartment_Unit";
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = super.validate(building);

            // Apartment units shouldn't have building-level elements
            for (StoreySpec storey : building.storeys()) {
                if (!storey.stairs().isEmpty()) {
                    result.addInfo(
                        storey.name(),
                        "ApartmentUnit",
                        "Apartment units typically don't contain stairs (building-level element)"
                    );
                }

                // Check for inappropriate space types
                for (RoomSpec room : storey.rooms()) {
                    if (room.type().equalsIgnoreCase("LOBBY") ||
                        room.type().equalsIgnoreCase("CONCOURSE")) {
                        result.addWarning(
                            room.name(),
                            "ApartmentUnit",
                            "%s is a building-level space, not unit-level",
                            room.type()
                        );
                    }
                }
            }

            return result;
        }
    }

    /**
     * Commercial Office validator.
     */
    public static class CommercialOfficeValidator extends BaseProtocolValidator {

        @Override
        public String getName() {
            return "CommercialOfficeValidator";
        }

        @Override
        protected String getProtocolName() {
            return "Commercial_Office";
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = super.validate(building);

            // Commercial offices need adequate restroom facilities
            int totalArea = 0;
            int restroomCount = 0;

            for (StoreySpec storey : building.storeys()) {
                for (RoomSpec room : storey.rooms()) {
                    double area = (room.maxX() - room.minX()) * (room.maxY() - room.minY());
                    totalArea += area;

                    if (room.type().equalsIgnoreCase("RESTROOM") ||
                        room.type().equalsIgnoreCase("BATHROOM")) {
                        restroomCount++;
                    }
                }
            }

            // Rule of thumb: 1 restroom per 500 m² for offices
            int recommendedRestrooms = Math.max(1, totalArea / 500);
            if (restroomCount < recommendedRestrooms) {
                result.addInfo(
                    "Building",
                    "RestroomCount",
                    "Building has %d m² - consider %d+ restrooms (found %d)",
                    totalArea, recommendedRestrooms, restroomCount
                );
            }

            return result;
        }
    }
}
