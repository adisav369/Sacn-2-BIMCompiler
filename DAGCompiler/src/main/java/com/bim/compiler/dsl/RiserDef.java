package com.bim.compiler.dsl;

import java.util.List;

/**
 * Vertical service riser definition.
 * Phase 46: Multi-unit residential support.
 *
 * Risers are vertical shafts carrying services between floors:
 * - Plumbing: Waste stack, vent stack, water supply
 * - Electrical: Main distribution cables
 *
 * In multi-unit buildings, risers may be:
 * - Unit-specific: Serves only one unit (e.g., riser_a, riser_b)
 * - Shared: Serves multiple units with individual branches
 */
public record RiserDef(
    String name,            // Unique identifier (e.g., "riser_a", "main_stack")
    RiserType type,         // PLUMBING or ELECTRICAL
    String locationRoom,    // Room/area where riser is located
    List<String> servesUnits, // Unit IDs served by this riser (empty = all)
    double[] position       // Computed position [x, y], null until compiled
) {
    /**
     * Riser service types.
     */
    public enum RiserType {
        PLUMBING,    // Waste/vent/supply stack
        ELECTRICAL   // Main electrical distribution
    }

    /**
     * Create riser definition from DSL.
     * Position and served units computed during compilation.
     */
    public RiserDef(String name, RiserType type, String locationRoom) {
        this(name, type, locationRoom, List.of(), null);
    }

    /**
     * Create copy with computed position.
     */
    public RiserDef withPosition(double x, double y) {
        return new RiserDef(name, type, locationRoom, servesUnits, new double[]{x, y});
    }

    /**
     * Create copy with served units.
     */
    public RiserDef withServedUnits(List<String> units) {
        return new RiserDef(name, type, locationRoom, units, position);
    }

    /**
     * Check if position has been computed.
     */
    public boolean hasPosition() {
        return position != null;
    }

    /**
     * Check if this riser serves a specific unit.
     */
    public boolean servesUnit(String unitId) {
        if (servesUnits == null || servesUnits.isEmpty()) {
            return true; // Empty list = serves all units
        }
        return servesUnits.contains(unitId);
    }

    /**
     * Check if this is a shared riser (serves multiple units).
     */
    public boolean isShared() {
        return servesUnits == null || servesUnits.isEmpty() || servesUnits.size() > 1;
    }

    /**
     * Parse riser type from keyword.
     */
    public static RiserType typeFromKeyword(String keyword) {
        if (keyword == null) return RiserType.PLUMBING;
        return switch (keyword.toLowerCase()) {
            case "electrical", "elec" -> RiserType.ELECTRICAL;
            case "plumbing", "plumb", "waste", "water" -> RiserType.PLUMBING;
            default -> RiserType.PLUMBING;
        };
    }
}
