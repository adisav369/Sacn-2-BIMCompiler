package com.bim.compiler.dsl;

import java.util.EnumMap;
import java.util.Map;

/**
 * Building code requirements for residential rooms.
 * All values extracted from standards - NOT invented.
 *
 * Primary source: International Residential Code (IRC) 2021
 * Secondary: International Building Code (IBC) 2021
 */
public record RoomRequirements(
    RoomType type,
    double minArea,              // m² (0 = no minimum)
    double minDimension,         // m (0 = no minimum)
    boolean requiresWindow,      // natural light requirement
    boolean requiresEgress,      // emergency escape window
    boolean requiresVentilation, // mechanical or natural
    double naturalLightPercent,  // % of floor area for glazing (0 = not required)
    String codeReference         // e.g., "IRC 2021 R304.1"
) {

    /**
     * IRC 2021 Residential Code Requirements.
     *
     * Sources:
     * - R303: Light, Ventilation and Heating
     * - R304: Minimum Room Areas
     * - R310: Emergency Escape and Rescue Openings
     * - M1507.3: Kitchen/Bath exhaust rates
     * - IBC 2903.1.1: Fixture clearances
     */
    public static final Map<RoomType, RoomRequirements> IRC_2021 = createIRC2021();

    private static Map<RoomType, RoomRequirements> createIRC2021() {
        Map<RoomType, RoomRequirements> map = new EnumMap<>(RoomType.class);

        // BEDROOM - Habitable room used for sleeping
        // IRC R304.1: 70 sq ft (6.5 m²) minimum
        // IRC R304.2: 7 ft (2.134 m) minimum dimension
        // IRC R303.1: 8% glazing for natural light
        // IRC R310.1: Egress window required for sleeping rooms
        map.put(RoomType.BEDROOM, new RoomRequirements(
            RoomType.BEDROOM,
            6.5,        // 70 sq ft = 6.5 m²
            2.134,      // 7 ft = 2.134 m
            true,       // requires window (R303.1)
            true,       // requires egress (R310.1)
            true,       // requires ventilation (R303.1: 4% openable)
            8.0,        // 8% of floor area (R303.1)
            "IRC 2021 R304.1, R303.1, R310.1"
        ));

        // BATHROOM - Not a habitable space
        // IRC R303.3: 3 sq ft glazing OR mechanical exhaust
        // IRC M1507.3: 50 CFM intermittent / 20 CFM continuous
        // No minimum area in IRC (not habitable)
        map.put(RoomType.BATHROOM, new RoomRequirements(
            RoomType.BATHROOM,
            0,          // no minimum area (not habitable)
            0,          // no minimum dimension
            false,      // window not required if mechanical vent
            false,      // no egress required
            true,       // requires ventilation (R303.3)
            0,          // natural light not required
            "IRC 2021 R303.3, M1507.3"
        ));

        // KITCHEN - Habitable but exempt from area minimum
        // IRC R304.1 Exception: Kitchens exempt from 70 sq ft
        // IRC M1503/M1507.3: 100 CFM intermittent / 25 CFM continuous
        map.put(RoomType.KITCHEN, new RoomRequirements(
            RoomType.KITCHEN,
            0,          // exempt from minimum (R304.1 exception)
            0,          // exempt from minimum dimension
            false,      // window not required if mechanical vent
            false,      // no egress required
            true,       // requires ventilation (M1503)
            0,          // natural light not required if artificial
            "IRC 2021 R304.1 Exception, M1503, M1507.3"
        ));

        // LIVING_ROOM - Habitable room
        // IRC R304.1: 70 sq ft (6.5 m²) minimum
        // IRC R304.2: 7 ft (2.134 m) minimum dimension
        // IRC R303.1: 8% glazing for natural light
        map.put(RoomType.LIVING, new RoomRequirements(
            RoomType.LIVING,
            6.5,        // 70 sq ft = 6.5 m²
            2.134,      // 7 ft = 2.134 m
            true,       // requires window (R303.1)
            false,      // no egress (not sleeping)
            true,       // requires ventilation
            8.0,        // 8% of floor area
            "IRC 2021 R304.1, R303.1"
        ));

        // CORRIDOR - Not a habitable space
        // IRC R311: Egress path requirements
        // IBC: 36 in (0.914 m) minimum width for dwelling units
        // No natural light/ventilation requirement
        map.put(RoomType.CORRIDOR, new RoomRequirements(
            RoomType.CORRIDOR,
            0,          // no minimum area
            0.914,      // 36 in = 0.914 m minimum width (IBC)
            false,      // no window required
            false,      // no egress window (is egress path)
            false,      // no ventilation required
            0,          // no natural light required
            "IRC 2021 R311, IBC dwelling unit corridor"
        ));

        // OFFICE - Habitable room (same as living)
        // IRC R304.1: 70 sq ft minimum
        // IRC R303.1: Natural light/ventilation
        map.put(RoomType.OFFICE, new RoomRequirements(
            RoomType.OFFICE,
            6.5,        // 70 sq ft = 6.5 m²
            2.134,      // 7 ft = 2.134 m
            true,       // requires window
            false,      // no egress (not sleeping)
            true,       // requires ventilation
            8.0,        // 8% of floor area
            "IRC 2021 R304.1, R303.1"
        ));

        // STORAGE - Not a habitable space
        // No IRC requirements for residential storage
        map.put(RoomType.STORAGE, new RoomRequirements(
            RoomType.STORAGE,
            0,          // no minimum
            0,          // no minimum
            false,      // no window
            false,      // no egress
            false,      // no ventilation
            0,          // no light
            "No IRC requirements"
        ));

        // GARAGE - Special requirements
        // IRC R302.5/R302.6: Fire separation from dwelling
        // No minimum dimensions in IRC
        // Typical: 3m x 6m single car (10ft x 20ft)
        map.put(RoomType.GARAGE, new RoomRequirements(
            RoomType.GARAGE,
            0,          // no code minimum (typical 18m² / 3x6m)
            0,          // no minimum dimension
            false,      // no window required
            false,      // no egress window
            false,      // no ventilation required (detached)
            0,          // no natural light
            "IRC 2021 R302.5, R302.6 (fire separation)"
        ));

        return map;
    }

    /**
     * Get requirements for a room type.
     * Returns null if type not found in code.
     */
    public static RoomRequirements forType(RoomType type) {
        return IRC_2021.get(type);
    }

    /**
     * Validate a room against code requirements.
     * Returns validation result with any warnings.
     */
    public ValidationResult validate(String roomName, double actualArea, double minActualDimension) {
        StringBuilder warnings = new StringBuilder();
        boolean passed = true;

        // Check minimum area
        if (minArea > 0 && actualArea < minArea) {
            warnings.append(String.format(
                "Room '%s' (%s): area %.1f m² below code minimum %.1f m² [%s]%n",
                roomName, type.name(), actualArea, minArea, codeReference
            ));
            passed = false;
        }

        // Check minimum dimension
        if (minDimension > 0 && minActualDimension < minDimension) {
            warnings.append(String.format(
                "Room '%s' (%s): dimension %.2f m below code minimum %.2f m [%s]%n",
                roomName, type.name(), minActualDimension, minDimension, codeReference
            ));
            passed = false;
        }

        return new ValidationResult(passed, warnings.toString());
    }

    /**
     * Result of room validation.
     */
    public record ValidationResult(boolean passed, String warnings) {
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
