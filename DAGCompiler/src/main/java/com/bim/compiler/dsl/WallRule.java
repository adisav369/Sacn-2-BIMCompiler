package com.bim.compiler.dsl;

/**
 * Wall generation rules for space types.
 * Determines how walls are generated for a space type.
 * Source of truth: ad_space_type.wall_rule column in BOM.db.
 */
public enum WallRule {
    ENCLOSED,        // All 4 walls (private rooms: BEDROOM, BATHROOM)
    PERIMETER_ONLY,  // Exterior walls only (OPEN_PLAN, DEPARTURE_LOUNGE)
    NONE,            // No walls - posts/columns only (PORCH)
    AS_REQUIRED      // Context-dependent (CORRIDOR, LOBBY)
}
