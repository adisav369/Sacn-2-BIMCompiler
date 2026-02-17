package com.bim.compiler.dsl;

/**
 * Unit entry access type (UBBL compliance).
 * Phase 46: Multi-unit residential support.
 *
 * Each dwelling unit must have independent entry:
 * - DIRECT: Own exterior door (ground floor, terrace unit)
 * - SHARED: Access via shared corridor/stairwell (apartment)
 *
 * Pass-through access (entering Unit B only via Unit A) is prohibited.
 */
public enum EntryType {
    /**
     * Direct exterior access.
     * Unit has its own door to outside (not through shared space).
     */
    DIRECT,

    /**
     * Access via shared circulation.
     * Unit door opens to shared corridor, stairwell, or lobby.
     */
    SHARED;

    public static EntryType fromKeyword(String keyword) {
        if (keyword == null) return DIRECT;
        return switch (keyword.toUpperCase()) {
            case "DIRECT", "EXTERIOR" -> DIRECT;
            case "SHARED", "CORRIDOR", "COMMON" -> SHARED;
            default -> DIRECT;
        };
    }
}
