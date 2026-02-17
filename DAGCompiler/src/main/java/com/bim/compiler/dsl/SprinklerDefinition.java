package com.bim.compiler.dsl;

/**
 * Sprinkler definition from DSL.
 *
 * Example:
 *   SPRINKLERS grid:4.6m
 *
 * Uses pendant sprinklers by default (ceiling mount).
 * Spacing from NFPA 13 light hazard: 4.6m max.
 */
public record SprinklerDefinition(
    double gridSpacing,  // meters (default 4.6m from NFPA 13)
    String type          // "pendant" (default) or "upright"
) {
    // NFPA 13 light hazard maximum spacing
    public static final double DEFAULT_SPACING = 4.6;

    /**
     * Parse from DSL: "SPRINKLERS grid:4.6m"
     */
    public static SprinklerDefinition parse(String spacingStr) {
        double spacing = DEFAULT_SPACING;
        if (spacingStr != null && !spacingStr.isEmpty()) {
            // Remove 'm' suffix if present
            spacingStr = spacingStr.replace("m", "").trim();
            spacing = Double.parseDouble(spacingStr);
        }
        return new SprinklerDefinition(spacing, "pendant");
    }

    /**
     * Default definition with NFPA 13 spacing.
     */
    public static SprinklerDefinition defaultSpec() {
        return new SprinklerDefinition(DEFAULT_SPACING, "pendant");
    }
}
