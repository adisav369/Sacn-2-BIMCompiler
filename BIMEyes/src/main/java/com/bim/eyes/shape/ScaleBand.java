package com.bim.eyes.shape;

/**
 * Absolute size classification from AABB volume.
 * // Implementing EYES_SRS.md §3.2 — Witness: W-EYES-CLASSIFY
 */
public enum ScaleBand {
    /** Volume > 1.0 m³: rooms, walls, slabs, roofs. */
    ARCHITECTURAL,
    /** 0.01 – 1.0 m³: furniture, large fittings. */
    FURNITURE,
    /** 0.0001 – 0.01 m³: small fittings, hardware. */
    FITTING,
    /** < 0.0001 m³: fasteners, tiny parts. */
    TINY
}
