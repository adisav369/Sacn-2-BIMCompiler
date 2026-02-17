package com.bim.compiler.dsl;

/**
 * Fire Resistance Level (FRL) per Malaysian UBBL.
 * Phase 46: Multi-unit residential support.
 *
 * FRL format: structural_adequacy / integrity / insulation (minutes)
 * Example: FRL 60/60/60 means:
 * - 60 minutes structural adequacy (load-bearing)
 * - 60 minutes integrity (no flame/hot gas passage)
 * - 60 minutes insulation (limits temperature rise)
 */
public enum FireRating {
    /**
     * No fire rating required.
     * Internal walls within same fire compartment.
     */
    NONE(0, 0, 0),

    /**
     * 30 minutes fire resistance.
     * Minimum for separating walls in small residential.
     */
    FRL_30_30_30(30, 30, 30),

    /**
     * 60 minutes (1 hour) fire resistance.
     * Party walls between dwelling units (UBBL requirement).
     */
    FRL_60_60_60(60, 60, 60),

    /**
     * 90 minutes (1.5 hour) fire resistance.
     * Compartment walls in larger buildings.
     */
    FRL_90_90_90(90, 90, 90),

    /**
     * 120 minutes (2 hour) fire resistance.
     * Major fire compartments, escape routes.
     */
    FRL_120_120_120(120, 120, 120);

    private final int structuralMinutes;
    private final int integrityMinutes;
    private final int insulationMinutes;

    FireRating(int structural, int integrity, int insulation) {
        this.structuralMinutes = structural;
        this.integrityMinutes = integrity;
        this.insulationMinutes = insulation;
    }

    public int getStructuralMinutes() {
        return structuralMinutes;
    }

    public int getIntegrityMinutes() {
        return integrityMinutes;
    }

    public int getInsulationMinutes() {
        return insulationMinutes;
    }

    /**
     * Get total rating in minutes (minimum of all three).
     */
    public int getRatingMinutes() {
        return Math.min(structuralMinutes, Math.min(integrityMinutes, insulationMinutes));
    }

    /**
     * Get rating in hours (for display).
     */
    public double getRatingHours() {
        return getRatingMinutes() / 60.0;
    }

    /**
     * Get UBBL-format string (e.g., "60/60/60").
     */
    public String toUBBLFormat() {
        if (this == NONE) return "-/-/-";
        return structuralMinutes + "/" + integrityMinutes + "/" + insulationMinutes;
    }

    /**
     * Get default fire rating for wall type.
     */
    public static FireRating defaultForWallType(WallType wallType) {
        return switch (wallType) {
            case PARTY -> FRL_60_60_60;      // 1 hour for unit separation
            case SHARED -> FRL_60_60_60;     // 1 hour for compartmentation
            case EXTERNAL -> NONE;            // External walls rated differently
            case INTERNAL -> NONE;            // No rating within unit
        };
    }

    /**
     * Parse from string (e.g., "1HR", "60", "FRL_60_60_60").
     */
    public static FireRating fromString(String value) {
        if (value == null || value.isEmpty()) return NONE;
        String upper = value.toUpperCase().trim();
        return switch (upper) {
            case "NONE", "-", "0" -> NONE;
            case "30", "0.5HR", "FRL_30_30_30" -> FRL_30_30_30;
            case "60", "1HR", "FRL_60_60_60" -> FRL_60_60_60;
            case "90", "1.5HR", "FRL_90_90_90" -> FRL_90_90_90;
            case "120", "2HR", "FRL_120_120_120" -> FRL_120_120_120;
            default -> NONE;
        };
    }
}
