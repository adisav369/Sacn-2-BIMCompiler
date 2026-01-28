package com.bim.compiler.dsl;

/**
 * Light fixture definition from DSL.
 *
 * Examples:
 *   LIGHTS grid:3.0m                    // Default ceiling recessed
 *   LIGHTS grid:3.0m type:2x4_LED       // Specific fixture type
 *
 * Grid spacing based on illumination requirements:
 * - Office/Lounge: 2.5-3.0m (500 lux)
 * - Corridor: 3.0-4.0m (100 lux)
 * - Gate: 3.0m (300 lux)
 */
public record LightDefinition(
    double gridSpacing,  // meters
    String fixtureType   // "recessed", "pendant", "2x4_LED", etc.
) {
    // Default spacing for general illumination
    public static final double DEFAULT_SPACING = 3.0;
    public static final String DEFAULT_TYPE = "recessed";

    /**
     * Parse from DSL: "LIGHTS grid:3.0m" or "LIGHTS grid:3.0m type:2x4_LED"
     */
    public static LightDefinition parse(String spacingStr, String typeStr) {
        double spacing = DEFAULT_SPACING;
        if (spacingStr != null && !spacingStr.isEmpty()) {
            spacingStr = spacingStr.replace("m", "").trim();
            spacing = Double.parseDouble(spacingStr);
        }

        String type = DEFAULT_TYPE;
        if (typeStr != null && !typeStr.isEmpty()) {
            type = typeStr.trim();
        }

        return new LightDefinition(spacing, type);
    }

    /**
     * Parse simple form: "LIGHTS grid:3.0m"
     */
    public static LightDefinition parse(String spacingStr) {
        return parse(spacingStr, null);
    }

    /**
     * Default definition.
     */
    public static LightDefinition defaultSpec() {
        return new LightDefinition(DEFAULT_SPACING, DEFAULT_TYPE);
    }
}
