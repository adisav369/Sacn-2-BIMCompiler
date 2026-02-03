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
     * Commercial unit (future use).
     * Different MEP requirements, no habitable room requirements.
     */
    COMMERCIAL;

    public static UnitType fromKeyword(String keyword) {
        if (keyword == null) return RESIDENTIAL;
        return switch (keyword.toUpperCase()) {
            case "RESIDENTIAL", "RES" -> RESIDENTIAL;
            case "COMMERCIAL", "COM" -> COMMERCIAL;
            default -> RESIDENTIAL;
        };
    }
}
