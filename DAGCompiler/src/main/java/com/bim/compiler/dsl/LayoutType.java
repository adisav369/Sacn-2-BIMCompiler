package com.bim.compiler.dsl;

/**
 * Phase 47D: Layout type for multi-unit buildings.
 * Determines how units are partitioned spatially.
 */
public enum LayoutType {
    /**
     * Units are side-by-side (left/right).
     * Party wall is vertical (EAST/WEST edge).
     * Example: Duplex with shared wall between units.
     */
    SIDE_BY_SIDE,

    /**
     * Units are stacked (top/bottom).
     * Party wall is horizontal (NORTH/SOUTH edge).
     * Example: Upper/lower apartments in same footprint.
     */
    STACKED,

    /**
     * Complex layout with mixed party wall orientations.
     * Requires full two-phase solver (not yet supported).
     * Example: L-shaped building, courtyard configuration.
     */
    COMPLEX
}
