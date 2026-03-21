package com.bim.eyes.shape;

/**
 * Geometric invariant classification from AABB dimensionless ratios.
 * // Implementing EYES_SRS.md §3.1 — Witness: W-EYES-CLASSIFY
 */
public enum ShapeArchetype {
    /** One dimension far smaller than other two: wall, slab, plate, glass panel. */
    PLANAR,
    /** Two dimensions far smaller than third: column, beam, member, pipe, mullion. */
    ELONGATED,
    /** All dimensions comparable: furniture, equipment, compact fittings. */
    COMPACT,
    /** Transitional — dimensions don't clearly fit one archetype. */
    MIXED
}
