package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.Map;

/**
 * Geometry instancing statistics per type.
 * From PATTERN G11 in SESSION_STATE.md.
 *
 * Query findings:
 * - IfcPlate: max 17 instances, 4.2x avg reuse (highest)
 * - IfcWindow: max 11 instances, 1.3x avg reuse
 * - Most types: ~1.0x reuse (unique geometries)
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public final class InstancingStats {

    private InstancingStats() {}

    /**
     * Instancing statistics for a type.
     * @param maxInstances maximum instances of single geometry
     * @param avgReuse average reuse ratio (instances / unique geometries)
     * @param uniqueGeometries count of unique geometry hashes
     */
    public record Stats(int maxInstances, double avgReuse, int uniqueGeometries) {

        /**
         * Check if this type is highly instanced.
         * @return true if avg reuse > 2.0
         */
        public boolean isHighlyInstanced() {
            return avgReuse > 2.0;
        }

        /**
         * Check if this type has mostly unique geometries.
         * @return true if avg reuse < 1.2
         */
        public boolean isMostlyUnique() {
            return avgReuse < 1.2;
        }
    }

    private static final Map<BIMObjectType, Stats> STATS;

    static {
        STATS = new EnumMap<>(BIMObjectType.class);

        // High instancing
        STATS.put(BIMObjectType.IFC_PLATE, new Stats(17, 4.2, 8018));

        // Moderate instancing
        STATS.put(BIMObjectType.IFC_WINDOW, new Stats(11, 1.3, 183));
        STATS.put(BIMObjectType.IFC_SLAB, new Stats(11, 1.5, 485));
        STATS.put(BIMObjectType.IFC_OPENING_ELEMENT, new Stats(11, 1.3, 485));
        STATS.put(BIMObjectType.IFC_FURNITURE, new Stats(4, 1.3, 131));
        STATS.put(BIMObjectType.IFC_COLUMN, new Stats(4, 1.3, 122));
        STATS.put(BIMObjectType.IFC_DOOR, new Stats(4, 1.2, 112));
        STATS.put(BIMObjectType.IFC_MEMBER, new Stats(3, 1.2, 382));
        STATS.put(BIMObjectType.IFC_BEAM, new Stats(3, 1.1, 404));

        // Low instancing (mostly unique)
        STATS.put(BIMObjectType.IFC_WALL, new Stats(2, 1.1, 315));
        STATS.put(BIMObjectType.IFC_PIPE_FITTING, new Stats(1, 1.0, 4198));
        STATS.put(BIMObjectType.IFC_PIPE_SEGMENT, new Stats(1, 1.0, 3787));
        STATS.put(BIMObjectType.IFC_DUCT_FITTING, new Stats(1, 1.0, 683));
        STATS.put(BIMObjectType.IFC_DUCT_SEGMENT, new Stats(1, 1.0, 555));
        STATS.put(BIMObjectType.IFC_LIGHT_FIXTURE, new Stats(3, 1.0, 801));
        STATS.put(BIMObjectType.IFC_AIR_TERMINAL, new Stats(4, 1.1, 268));
    }

    /**
     * Get instancing statistics for a type.
     * @param type the BIM object type
     * @return stats, or null if not available
     */
    public static Stats getStats(BIMObjectType type) {
        return STATS.get(type);
    }

    /**
     * Check if a type should use geometry instancing.
     * Recommended for types with avg reuse > 1.5.
     * @param type the type
     * @return true if instancing is beneficial
     */
    public static boolean shouldInstance(BIMObjectType type) {
        Stats stats = STATS.get(type);
        return stats != null && stats.avgReuse > 1.5;
    }

    /**
     * Get the expected reuse ratio for a type.
     * @param type the type
     * @return reuse ratio, or 1.0 if unknown
     */
    public static double getExpectedReuse(BIMObjectType type) {
        Stats stats = STATS.get(type);
        return stats != null ? stats.avgReuse : 1.0;
    }
}
