package com.bim.compiler.topology;

/**
 * Construction disciplines extracted from enhanced_federation_GI.db
 * Query: SELECT DISTINCT discipline, COUNT(*) FROM elements_meta GROUP BY 1 ORDER BY 2 DESC;
 *
 * EXTRACTED - DO NOT INVENT ADDITIONAL DISCIPLINES
 */
public enum Discipline {
    ARC(35338),   // Architecture: Plates, Walls, Doors, Windows, Furniture
    FP(6884),     // Fire Protection: Pipes, Sprinklers, Alarms
    REB(2660),    // Reinforcement: Reinforcing Bars
    ACMV(1621),   // HVAC: Ducts, Air Terminals
    CW(1431),     // Curtain Wall services
    STR(1429),    // Structural: Beams, Columns, Slabs
    ELEC(1172),   // Electrical: Light Fixtures, Appliances
    SP(979),      // Sanitary/Plumbing
    LPG(209);     // Gas piping

    private final int elementCount;

    Discipline(int elementCount) {
        this.elementCount = elementCount;
    }

    /**
     * Element count from DB extraction (for reference/validation)
     */
    public int getElementCount() {
        return elementCount;
    }

    /**
     * Check if this is an MEP discipline (mechanical/electrical/plumbing)
     */
    public boolean isMEP() {
        return this == ACMV || this == FP || this == SP || this == CW || this == LPG || this == ELEC;
    }

    /**
     * Check if this is a structural discipline
     */
    public boolean isStructural() {
        return this == STR || this == REB;
    }

    /**
     * Convert from string to enum
     * @param discipline discipline code string (e.g., "ARC", "FP")
     * @return corresponding enum value, or null if not found
     */
    public static Discipline fromString(String discipline) {
        if (discipline == null) return null;
        try {
            return valueOf(discipline.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
