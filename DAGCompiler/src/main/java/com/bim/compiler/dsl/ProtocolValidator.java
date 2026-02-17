package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import java.util.*;

/**
 * Phase 28: Protocol Validator
 *
 * Protocols define complete building type requirements.
 * Inspired by STEP Application Protocols (AP214, AP242).
 *
 * Predefined protocols:
 * - Residential_Single_Storey
 * - Residential_Multi_Storey
 * - Apartment_Unit
 * - Commercial_Office
 */
public class ProtocolValidator {

    /**
     * Space requirement definition.
     */
    public record SpaceRequirement(
        String spaceType,
        int minCount,
        int maxCount,           // -1 = unlimited
        Double minArea,         // null = no minimum
        Double maxArea          // null = no maximum
    ) {}

    /**
     * Relationship requirement.
     */
    public record RelationshipRequirement(
        String sourceType,
        String relationship,    // opens_to, adjacent, stack
        String targetType
    ) {}

    /**
     * Protocol definition.
     */
    public record ProtocolDef(
        String name,
        String description,
        List<String> applicableProfiles,
        List<SpaceRequirement> requiredSpaces,
        List<SpaceRequirement> optionalSpaces,
        Set<String> excludedSpaces,
        List<RelationshipRequirement> requiredRelationships
    ) {}

    private static final Map<String, ProtocolDef> PROTOCOLS = new HashMap<>();

    static {
        // Residential Single Storey
        PROTOCOLS.put("Residential_Single_Storey", new ProtocolDef(
            "Residential_Single_Storey",
            "Single-storey detached house",
            List.of("Malaysian_Residential", "US_Residential_IRC", "UK_Residential"),
            List.of(
                new SpaceRequirement("BEDROOM", 1, 6, 9.0, null),
                new SpaceRequirement("BATHROOM", 1, 4, 2.0, null),
                new SpaceRequirement("KITCHEN", 1, 1, 4.0, null),
                new SpaceRequirement("LIVING", 1, 2, 12.0, null)
            ),
            List.of(
                new SpaceRequirement("PORCH", 0, 1, null, null),
                new SpaceRequirement("GARAGE", 0, 2, null, null),
                new SpaceRequirement("STORAGE", 0, 4, null, null),
                new SpaceRequirement("OPEN_PLAN", 0, 1, null, null)
            ),
            Set.of("OFFICE", "CONFERENCE", "LOBBY", "GATE", "CONCOURSE"),
            List.of(
                new RelationshipRequirement("BEDROOM", "opens_to", "CORRIDOR|LIVING|OPEN_PLAN"),
                new RelationshipRequirement("BATHROOM", "opens_to", "CORRIDOR|LIVING|OPEN_PLAN")
            )
        ));

        // Residential Multi Storey
        PROTOCOLS.put("Residential_Multi_Storey", new ProtocolDef(
            "Residential_Multi_Storey",
            "Multi-storey detached house",
            List.of("Malaysian_Residential", "US_Residential_IRC", "UK_Residential"),
            List.of(
                new SpaceRequirement("BEDROOM", 1, 8, 9.0, null),
                new SpaceRequirement("BATHROOM", 1, 6, 2.0, null),
                new SpaceRequirement("KITCHEN", 1, 2, 4.0, null),
                new SpaceRequirement("LIVING", 1, 3, 12.0, null),
                new SpaceRequirement("STAIR", 1, 2, null, null)
            ),
            List.of(
                new SpaceRequirement("PORCH", 0, 1, null, null),
                new SpaceRequirement("GARAGE", 0, 2, null, null),
                new SpaceRequirement("LANDING", 0, 4, null, null)
            ),
            Set.of("OFFICE", "CONFERENCE", "GATE", "CONCOURSE"),
            List.of()
        ));

        // Apartment Unit
        PROTOCOLS.put("Apartment_Unit", new ProtocolDef(
            "Apartment_Unit",
            "Individual apartment unit within building",
            List.of("Malaysian_Residential", "US_Residential_IRC", "UK_Residential"),
            List.of(
                new SpaceRequirement("BATHROOM", 1, 2, 2.0, null)
            ),
            List.of(
                new SpaceRequirement("BEDROOM", 0, 3, 9.0, null),  // 0 for studio
                new SpaceRequirement("KITCHEN", 0, 1, 3.0, null),
                new SpaceRequirement("LIVING", 0, 1, 10.0, null),
                new SpaceRequirement("OPEN_PLAN", 0, 1, null, null)
            ),
            Set.of("GARAGE", "PORCH", "STAIR"),  // Building-level elements
            List.of()
        ));

        // Commercial Office
        PROTOCOLS.put("Commercial_Office", new ProtocolDef(
            "Commercial_Office",
            "Commercial office building",
            List.of("Commercial_IBC"),
            List.of(
                new SpaceRequirement("LOBBY", 1, 1, 20.0, null),
                new SpaceRequirement("CORRIDOR", 1, -1, null, null),
                new SpaceRequirement("RESTROOM", 1, -1, null, null),
                new SpaceRequirement("STAIR", 2, -1, null, null)  // Min 2 exits
            ),
            List.of(
                new SpaceRequirement("OFFICE", 0, -1, 6.0, null),
                new SpaceRequirement("CONFERENCE", 0, -1, 10.0, null),
                new SpaceRequirement("STORAGE", 0, -1, null, null)
            ),
            Set.of("BEDROOM", "KITCHEN", "LIVING"),  // Residential excluded
            List.of()
        ));
    }

    /**
     * Get protocol by name.
     */
    public static ProtocolDef getProtocol(String name) {
        return PROTOCOLS.get(name);
    }

    /**
     * Check if protocol exists.
     */
    public static boolean hasProtocol(String name) {
        return PROTOCOLS.containsKey(name);
    }

    /**
     * Get all protocol names.
     */
    public static Set<String> getProtocolNames() {
        return PROTOCOLS.keySet();
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Map<String, Integer> spaceCounts
    ) {}

    /**
     * Validate building against protocol.
     */
    public static ValidationResult validate(BuildingDefinition building, String protocolName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ProtocolDef protocol = getProtocol(protocolName);
        if (protocol == null) {
            errors.add("Unknown protocol: " + protocolName);
            return new ValidationResult(false, errors, warnings, Map.of());
        }

        // Count spaces by type
        Map<String, Integer> spaceCounts = new HashMap<>();
        Map<String, Double> spaceAreas = new HashMap<>();

        for (var storey : building.storeys()) {
            for (var room : storey.rooms()) {
                String type = normalizeType(room.type());
                spaceCounts.merge(type, 1, Integer::sum);

                // Calculate area if grid bounds available
                if (room.hasGridBounds() && building.grid() != null) {
                    var gb = room.getParsedGridBounds();
                    if (gb != null) {
                        double[] dims = gb.getDimensions(building.grid());
                        double area = dims[0] * dims[1];
                        spaceAreas.merge(type, area, Double::sum);
                    }
                }
            }
        }

        // Check required spaces
        for (SpaceRequirement req : protocol.requiredSpaces()) {
            String type = req.spaceType();
            int count = getCountForType(type, spaceCounts);

            if (count < req.minCount()) {
                // Special case: LIVING can be satisfied by OPEN_PLAN with LIVING zone
                if (type.equals("LIVING") && spaceCounts.getOrDefault("OPEN_PLAN", 0) > 0) {
                    // Check if OPEN_PLAN has LIVING zone
                    boolean hasLivingZone = hasZoneOfType(building, "LIVING");
                    if (hasLivingZone) {
                        continue; // Satisfied
                    }
                }
                // Similar for KITCHEN
                if (type.equals("KITCHEN") && spaceCounts.getOrDefault("OPEN_PLAN", 0) > 0) {
                    boolean hasKitchenZone = hasZoneOfType(building, "KITCHEN");
                    if (hasKitchenZone) {
                        continue;
                    }
                }

                errors.add(String.format("Required: %s (min %d), found %d",
                    type, req.minCount(), count));
            }

            if (req.maxCount() > 0 && count > req.maxCount()) {
                warnings.add(String.format("Warning: %s exceeds max (%d > %d)",
                    type, count, req.maxCount()));
            }
        }

        // Check excluded spaces
        for (String excluded : protocol.excludedSpaces()) {
            int count = getCountForType(excluded, spaceCounts);
            if (count > 0) {
                errors.add(String.format("Excluded space type present: %s (%d found)",
                    excluded, count));
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings, spaceCounts);
    }

    /**
     * Normalize space type name.
     */
    private static String normalizeType(String type) {
        String upper = type.toUpperCase();
        // Map Malaysian terms to standard
        return switch (upper) {
            case "BILIK", "BILIK_UTAMA" -> "BEDROOM";
            case "BILIK_MANDI", "TANDAS" -> "BATHROOM";
            case "DAPUR" -> "KITCHEN";
            case "RUANG_TAMU" -> "LIVING";
            case "ANJUNG" -> "PORCH";
            default -> upper;
        };
    }

    /**
     * Get count for type, checking aliases.
     */
    private static int getCountForType(String type, Map<String, Integer> counts) {
        int count = counts.getOrDefault(type, 0);

        // Check aliases
        if (type.equals("BEDROOM")) {
            count += counts.getOrDefault("BILIK", 0);
            count += counts.getOrDefault("BILIK_UTAMA", 0);
        } else if (type.equals("BATHROOM")) {
            count += counts.getOrDefault("BILIK_MANDI", 0);
            count += counts.getOrDefault("TANDAS", 0);
        } else if (type.equals("KITCHEN")) {
            count += counts.getOrDefault("DAPUR", 0);
        } else if (type.equals("LIVING")) {
            count += counts.getOrDefault("RUANG_TAMU", 0);
        } else if (type.equals("PORCH")) {
            count += counts.getOrDefault("ANJUNG", 0);
        }

        return count;
    }

    /**
     * Check if building has OPEN_PLAN with specified zone type.
     */
    private static boolean hasZoneOfType(BuildingDefinition building, String zoneType) {
        for (var storey : building.storeys()) {
            for (var room : storey.rooms()) {
                if (room.isOpenPlan() && room.zones() != null) {
                    for (String zone : room.zones()) {
                        if (zone.equalsIgnoreCase(zoneType)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
