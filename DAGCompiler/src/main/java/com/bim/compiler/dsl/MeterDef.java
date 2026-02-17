package com.bim.compiler.dsl;

/**
 * Utility metering point definition.
 * Phase 46: Multi-unit residential support.
 *
 * Each unit requires independent metering for:
 * - Electrical: Distribution board location
 * - Water: Water meter location
 * - Gas: Gas meter location (if applicable)
 */
public record MeterDef(
    MeterType type,
    String locationRoom,    // Room name where meter is placed
    double[] position       // Computed position [x, y, z], null until compiled
) {
    /**
     * Meter types for utility separation.
     */
    public enum MeterType {
        ELECTRICAL,  // Distribution board
        WATER,       // Water meter
        GAS          // Gas meter
    }

    /**
     * Create meter definition from DSL.
     * Position is computed during compilation.
     */
    public MeterDef(MeterType type, String locationRoom) {
        this(type, locationRoom, null);
    }

    /**
     * Create copy with computed position.
     */
    public MeterDef withPosition(double x, double y, double z) {
        return new MeterDef(type, locationRoom, new double[]{x, y, z});
    }

    /**
     * Check if position has been computed.
     */
    public boolean hasPosition() {
        return position != null;
    }

    /**
     * Parse meter type from keyword.
     */
    public static MeterType typeFromKeyword(String keyword) {
        if (keyword == null) return MeterType.ELECTRICAL;
        return switch (keyword.toLowerCase()) {
            case "electrical", "elec", "db" -> MeterType.ELECTRICAL;
            case "water", "h2o" -> MeterType.WATER;
            case "gas" -> MeterType.GAS;
            default -> MeterType.ELECTRICAL;
        };
    }
}
