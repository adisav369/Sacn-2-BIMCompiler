package com.bim.compiler.dsl;

import java.util.*;

/**
 * Phase 28: Profile Registry
 *
 * Profiles adapt the DSL for regional codes, building types, or project requirements.
 * Inspired by HL7 FHIR Profiles and STEP Application Protocols.
 *
 * Predefined profiles:
 * - Malaysian_Residential (UBBL 1984)
 * - US_Residential_IRC (IRC 2021)
 * - UK_Residential (Building Regs 2010)
 * - Commercial_IBC (IBC 2021)
 */
public class ProfileRegistry {

    /**
     * Profile definition.
     */
    public record ProfileDef(
        String name,
        String extendsProfile,      // Parent profile name or null
        String validationCode,      // UBBL_1984, IRC_2021, etc.
        String localization,        // MS_MY, EN_US, EN_GB
        Set<String> includedTypes,  // Space types included
        Set<String> excludedTypes,  // Space types excluded
        Map<String, Object> defaults // Default values
    ) {
        public double getDefaultStoreyHeight() {
            Object val = defaults.get("storey_height");
            if (val instanceof Number) return ((Number) val).doubleValue();
            return 2.8; // Default
        }

        public double getDefaultWallThickness() {
            Object val = defaults.get("wall_thickness");
            if (val instanceof Number) return ((Number) val).doubleValue();
            return 0.15; // 150mm default
        }

        public boolean allowsSpaceType(String type) {
            String upper = type.toUpperCase();
            if (!excludedTypes.isEmpty() && excludedTypes.contains(upper)) {
                return false;
            }
            if (!includedTypes.isEmpty() && !includedTypes.contains(upper)) {
                return false;
            }
            return true;
        }
    }

    private static final Map<String, ProfileDef> PROFILES = new HashMap<>();

    static {
        // Malaysian Residential (UBBL 1984)
        PROFILES.put("Malaysian_Residential", new ProfileDef(
            "Malaysian_Residential",
            null,
            "UBBL_1984",
            "MS_MY",
            Set.of("ANJUNG", "RUANG_TAMU", "BILIK", "BILIK_UTAMA", "DAPUR",
                   "BILIK_MANDI", "TANDAS", "RUANG_BASUH", "BEDROOM", "BATHROOM",
                   "KITCHEN", "LIVING", "PORCH", "OPEN_PLAN"),
            Set.of(),
            Map.of(
                "storey_height", 2.8,
                "wall_thickness", 0.15,
                "min_ceiling", 2.5,
                "min_bedroom_area", 9.3,
                "min_bathroom_width", 1.2
            )
        ));

        // US Residential (IRC 2021)
        PROFILES.put("US_Residential_IRC", new ProfileDef(
            "US_Residential_IRC",
            null,
            "IRC_2021",
            "EN_US",
            Set.of("BEDROOM", "BATHROOM", "KITCHEN", "LIVING", "DINING",
                   "GARAGE", "BASEMENT", "PORCH", "OPEN_PLAN", "CORRIDOR"),
            Set.of(),
            Map.of(
                "storey_height", 2.74,      // 9 ft
                "wall_thickness", 0.14,     // 2x4 + drywall
                "min_ceiling", 2.13,        // 7 ft
                "min_bedroom_area", 6.5,    // 70 sq ft
                "min_egress_window", 0.52   // 5.7 sq ft
            )
        ));

        // UK Residential (Building Regs 2010)
        PROFILES.put("UK_Residential", new ProfileDef(
            "UK_Residential",
            null,
            "BUILDING_REGS_2010",
            "EN_GB",
            Set.of("BEDROOM", "BATHROOM", "KITCHEN", "LIVING", "DINING",
                   "UTILITY", "PORCH", "OPEN_PLAN", "CORRIDOR", "WC"),
            Set.of(),
            Map.of(
                "storey_height", 2.4,
                "wall_thickness", 0.10,
                "min_ceiling", 2.3,
                "min_bedroom_area", 6.5
            )
        ));

        // Commercial (IBC 2021)
        PROFILES.put("Commercial_IBC", new ProfileDef(
            "Commercial_IBC",
            null,
            "IBC_2021",
            "EN_US",
            Set.of("OFFICE", "CONFERENCE", "LOBBY", "CORRIDOR", "RESTROOM",
                   "STORAGE", "MECHANICAL", "ELECTRICAL", "STAIR", "ELEVATOR",
                   "DEPARTURE_LOUNGE", "GATE", "CONCOURSE"),
            Set.of("BEDROOM", "KITCHEN"), // Residential types excluded
            Map.of(
                "storey_height", 3.0,
                "corridor_width", 1.2,
                "exit_distance", 61.0
            )
        ));
    }

    /**
     * Get profile by name.
     */
    public static ProfileDef getProfile(String name) {
        return PROFILES.get(name);
    }

    /**
     * Check if profile exists.
     */
    public static boolean hasProfile(String name) {
        return PROFILES.containsKey(name);
    }

    /**
     * Get all profile names.
     */
    public static Set<String> getProfileNames() {
        return PROFILES.keySet();
    }

    /**
     * Validate building against profile.
     */
    public static List<String> validateAgainstProfile(BuildingDefinition building, String profileName) {
        List<String> errors = new ArrayList<>();

        ProfileDef profile = getProfile(profileName);
        if (profile == null) {
            errors.add("Unknown profile: " + profileName);
            return errors;
        }

        // Check all space types are allowed
        for (var storey : building.storeys()) {
            for (var room : storey.rooms()) {
                if (!profile.allowsSpaceType(room.type())) {
                    errors.add(String.format("Space type '%s' not allowed in profile '%s'",
                        room.type(), profileName));
                }
            }
        }

        return errors;
    }
}
