package com.bim.compiler.dsl;

/**
 * Wall classification based on room ownership.
 * Phase 46: Multi-unit residential support.
 *
 * Wall type determines:
 * - Thickness (structural requirements)
 * - Fire rating (separation requirements)
 * - Acoustic rating (privacy requirements)
 * - Opening allowance (party walls prohibit openings)
 */
public enum WallType {
    /**
     * Internal wall within same unit.
     * Typical: 150mm, no fire rating required.
     */
    INTERNAL(0.150, false, false),

    /**
     * Party wall between different units.
     * Typical: 250mm, fire-rated (FRL 60/60/60), acoustic rated.
     * No openings permitted (UBBL compliance).
     */
    PARTY(0.250, true, true),

    /**
     * External wall to building exterior.
     * Typical: 250mm, weatherproof, may have openings.
     */
    EXTERNAL(0.250, false, true),

    /**
     * Wall between unit and shared space.
     * Typical: 200mm, fire-rated for compartmentation.
     */
    SHARED(0.200, true, false);

    private final double defaultThickness;
    private final boolean requiresFireRating;
    private final boolean allowsOpenings;

    WallType(double thickness, boolean fireRating, boolean openings) {
        this.defaultThickness = thickness;
        this.requiresFireRating = fireRating;
        this.allowsOpenings = openings;
    }

    public double getDefaultThickness() {
        return defaultThickness;
    }

    public boolean requiresFireRating() {
        return requiresFireRating;
    }

    public boolean allowsOpenings() {
        return allowsOpenings;
    }

    /**
     * Classify wall based on room ownership.
     *
     * @param unitA Unit ID of room A (null if shared space)
     * @param unitB Unit ID of room B (null if shared/exterior)
     * @return Appropriate wall type
     */
    public static WallType classify(String unitA, String unitB) {
        // Both null - shared to shared
        if (unitA == null && unitB == null) {
            return INTERNAL;
        }
        // One null - unit to shared
        if (unitA == null || unitB == null) {
            return SHARED;
        }
        // Same unit - internal
        if (unitA.equals(unitB)) {
            return INTERNAL;
        }
        // Different units - party wall
        return PARTY;
    }
}
