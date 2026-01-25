package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.Map;

/**
 * Extrusion direction conventions per type.
 * From PATTERN G5 in SESSION_STATE.md.
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public enum ExtrusionAxis {
    X_AXIS,  // Length along X (plates, horizontal beams)
    Y_AXIS,  // Length along Y (some ducts, stairs)
    Z_UP;    // Vertical extrusion (columns, walls, doors, windows)

    private static final Map<BIMObjectType, ExtrusionAxis> CONVENTIONS;

    static {
        CONVENTIONS = new EnumMap<>(BIMObjectType.class);

        // Always Z-up
        CONVENTIONS.put(BIMObjectType.IFC_COLUMN, Z_UP);
        CONVENTIONS.put(BIMObjectType.IFC_DOOR, Z_UP);
        CONVENTIONS.put(BIMObjectType.IFC_WINDOW, Z_UP);
        CONVENTIONS.put(BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL, Z_UP);
        CONVENTIONS.put(BIMObjectType.IFC_RAILING, Z_UP);
        CONVENTIONS.put(BIMObjectType.IFC_MEMBER, Z_UP);  // 60% Z-up

        // Predominantly Z-up
        CONVENTIONS.put(BIMObjectType.IFC_WALL, Z_UP);  // 61% Z-up
        CONVENTIONS.put(BIMObjectType.IFC_OPENING_ELEMENT, Z_UP);  // 81% Z-up

        // Always/mostly X-axis
        CONVENTIONS.put(BIMObjectType.IFC_PLATE, X_AXIS);  // 100% X-axis

        // Varies by routing - default to longest dimension
        // Pipes, ducts, beams: determined at runtime from bbox
    }

    /**
     * Get the conventional extrusion axis for a type.
     * @param type the BIM object type
     * @return conventional axis, or null if varies by instance
     */
    public static ExtrusionAxis getConvention(BIMObjectType type) {
        return CONVENTIONS.get(type);
    }

    /**
     * Infer extrusion axis from bounding box dimensions.
     * @param width X dimension
     * @param depth Y dimension
     * @param height Z dimension
     * @return the axis along the longest dimension
     */
    public static ExtrusionAxis fromDimensions(double width, double depth, double height) {
        if (height >= width && height >= depth) {
            return Z_UP;
        } else if (width >= depth) {
            return X_AXIS;
        } else {
            return Y_AXIS;
        }
    }

    /**
     * Check if a type has a fixed extrusion convention.
     */
    public static boolean hasFixedConvention(BIMObjectType type) {
        return CONVENTIONS.containsKey(type);
    }
}
