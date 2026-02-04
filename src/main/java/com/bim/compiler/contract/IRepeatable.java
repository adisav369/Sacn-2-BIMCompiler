package com.bim.compiler.contract;

import java.util.List;

/**
 * Contract for elements that repeat in patterns.
 * Core pattern for efficient multi-unit design.
 *
 * <p>Repeatable Patterns:
 * <ul>
 *   <li>Unit types - studio, 1BR, 2BR, 3BR apartments</li>
 *   <li>Structural bays - typical grid modules</li>
 *   <li>Floor plates - typical floors repeated</li>
 *   <li>Room modules - bathroom pods, kitchen modules</li>
 *   <li>Facade modules - curtain wall units</li>
 * </ul>
 *
 * <p>Pattern: Define template once, instantiate many times.
 * <pre>
 * UNIT_TYPE "2BR_A" {
 *     rooms: [living, kitchen, br1, br2, bath1, bath2]
 *     area: 85m²
 *     mirror_allowed: true
 * }
 *
 * FLOOR "Level_5" repeats:"typical" {
 *     UNIT "501" type:"2BR_A" at:A1
 *     UNIT "502" type:"2BR_A" at:A3 mirror:true
 *     UNIT "503" type:"1BR_B" at:B1
 * }
 * </pre>
 *
 * <p>80/20: Most condos have 3-5 unit types repeated across floors.
 * Define types well, placement is simple.
 */
public interface IRepeatable extends IBIMEntity {

    /**
     * Type template this is an instance of.
     * References a defined type (e.g., "2BR_A", "STRUCTURAL_BAY_8x8").
     */
    String templateId();

    /**
     * Instance identifier within this context.
     * E.g., "501" for unit 501 on floor 5.
     */
    String instanceId();

    /**
     * Category of repeatable pattern.
     */
    RepeatCategory category();

    /**
     * Transformation applied to template.
     */
    Transform transform();

    /**
     * Parameters that vary per instance.
     * Template defines defaults, instances can override.
     */
    default java.util.Map<String, Object> parameters() {
        return java.util.Map.of();
    }

    /**
     * Whether this instance can be modified from template.
     * False for manufactured/prefab elements.
     */
    default boolean allowModification() {
        return true;
    }

    /**
     * Categories of repeatable patterns.
     */
    enum RepeatCategory {
        UNIT,           // Dwelling units
        FLOOR_PLATE,    // Entire floors
        STRUCTURAL_BAY, // Grid modules
        ROOM_MODULE,    // Prefab rooms (pods)
        FACADE_MODULE,  // Curtain wall units
        MEP_MODULE,     // Prefab MEP assemblies
        PARKING_BAY     // Car park bays
    }

    /**
     * Transformation from template to instance.
     */
    record Transform(
        double offsetX,
        double offsetY,
        double rotation,    // Degrees
        boolean mirrorX,
        boolean mirrorY
    ) {
        public static Transform identity() {
            return new Transform(0, 0, 0, false, false);
        }

        public static Transform at(double x, double y) {
            return new Transform(x, y, 0, false, false);
        }

        public static Transform mirrored(double x, double y) {
            return new Transform(x, y, 0, true, false);
        }

        public static Transform rotated(double x, double y, double degrees) {
            return new Transform(x, y, degrees, false, false);
        }
    }
}
