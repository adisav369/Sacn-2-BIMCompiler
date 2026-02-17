package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.Map;

/**
 * Routing constraints for MEP elements by discipline.
 * From PATTERN G3 in SESSION_STATE.md.
 *
 * Extracted from enhanced_federation_GI.db:
 * - Distance from wall surfaces
 * - Distance below structural slabs
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public final class RoutingConstraints {

    private RoutingConstraints() {}

    /**
     * Routing constraint record.
     * @param wallClearanceMeters typical distance from wall (meters)
     * @param ceilingClearanceMeters typical distance below slab (meters)
     * @param minCeilingClearanceMeters minimum observed clearance (meters)
     * @param canPenetrateSlab true if risers/vertical runs allowed
     */
    public record Constraints(
        double wallClearanceMeters,
        double ceilingClearanceMeters,
        double minCeilingClearanceMeters,
        boolean canPenetrateSlab
    ) {
        public double wallClearanceMm() {
            return wallClearanceMeters * 1000;
        }

        public double ceilingClearanceMm() {
            return ceilingClearanceMeters * 1000;
        }
    }

    private static final Map<Discipline, Constraints> CONSTRAINTS;

    static {
        CONSTRAINTS = new EnumMap<>(Discipline.class);

        // From PATTERN G3 queries
        CONSTRAINTS.put(Discipline.ACMV, new Constraints(
            0.175,  // 159-191mm from wall → 175mm avg
            0.669,  // avg below slab
            0.067,  // min below slab
            false   // ducts don't penetrate slabs
        ));

        CONSTRAINTS.put(Discipline.FP, new Constraints(
            0.173,  // 172-174mm from wall
            0.727,  // avg below slab
            0.166,  // min below slab
            false   // FP runs horizontal in ceiling
        ));

        CONSTRAINTS.put(Discipline.CW, new Constraints(
            0.216,  // 212-221mm from wall
            0.718,  // avg below slab
            0.020,  // min below slab
            false
        ));

        CONSTRAINTS.put(Discipline.SP, new Constraints(
            0.223,  // 218-229mm from wall
            0.440,  // avg below slab
            0.000,  // min 0 = penetrations!
            true    // SP can penetrate (drainage risers)
        ));

        CONSTRAINTS.put(Discipline.LPG, new Constraints(
            0.106,  // 87-125mm from wall (closest)
            0.500,  // estimated
            0.100,  // estimated
            false
        ));
    }

    /**
     * Get routing constraints for a discipline.
     * @param discipline the MEP discipline
     * @return constraints, or null if not an MEP discipline
     */
    public static Constraints getConstraints(Discipline discipline) {
        return CONSTRAINTS.get(discipline);
    }

    /**
     * Check if a wall clearance is acceptable.
     * Allows ±50mm tolerance from typical.
     */
    public static boolean isValidWallClearance(Discipline discipline, double clearanceMeters) {
        Constraints c = CONSTRAINTS.get(discipline);
        if (c == null) return true;

        double tolerance = 0.050; // 50mm
        return clearanceMeters >= (c.wallClearanceMeters - tolerance);
    }

    /**
     * Check if a ceiling clearance is acceptable.
     */
    public static boolean isValidCeilingClearance(Discipline discipline, double clearanceMeters) {
        Constraints c = CONSTRAINTS.get(discipline);
        if (c == null) return true;

        // Must be above minimum (with some tolerance for penetrations)
        if (c.canPenetrateSlab) {
            return true; // Any clearance OK for penetrating types
        }
        return clearanceMeters >= (c.minCeilingClearanceMeters - 0.010);
    }
}
