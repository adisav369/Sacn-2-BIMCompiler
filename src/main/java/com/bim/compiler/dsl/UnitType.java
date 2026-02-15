package com.bim.compiler.dsl;

/**
 * Unit usage classification.
 * Phase 46: Multi-unit residential support.
 */
public enum UnitType {
    /**
     * Residential dwelling unit.
     * Must have habitable rooms, kitchen/bathroom facilities.
     */
    RESIDENTIAL,

    /**
     * Duplex unit — metadata-driven room dimensions.
     * Room layout resolved from ad_unit_type (DUPLEX_GROUND/DUPLEX_UPPER)
     * via ad_unit_type_room fractional coordinates.
     */
    DUPLEX,

    /**
     * Commercial unit (future use).
     * Different MEP requirements, no habitable room requirements.
     */
    COMMERCIAL;

    /**
     * Resolve the ad_unit_type_id for a given storey level.
     * Building-level type defines its next-level unit type templates.
     * E.g., DUPLEX + level 0 → DUPLEX_GROUND, DUPLEX + level 1 → DUPLEX_UPPER.
     */
    public String resolveUnitTypeId(int storeyLevel) {
        return switch (this) {
            case DUPLEX -> storeyLevel == 0 ? "DUPLEX_GROUND" : "DUPLEX_UPPER";
            default -> null;  // No metadata template for generic types
        };
    }

    public static UnitType fromKeyword(String keyword) {
        if (keyword == null) return RESIDENTIAL;
        return switch (keyword.toUpperCase()) {
            case "RESIDENTIAL", "RES" -> RESIDENTIAL;
            case "DUPLEX", "DPX" -> DUPLEX;
            case "COMMERCIAL", "COM" -> COMMERCIAL;
            default -> RESIDENTIAL;
        };
    }
}
