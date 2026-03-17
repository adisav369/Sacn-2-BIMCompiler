package com.bim.designer.validation;

/**
 * Semantic geometry construct for validation.
 *
 * <p>This is the "what" — not raw mesh, but a semantic placement:
 * "a BEDROOM, 3100×3100×2800mm, at position (x,y,z), verb=SNAP".
 *
 * <p>The validator doesn't care who created this request (compiler,
 * generative path, Blender drag). It only checks against AD_Val_Rule.
 *
 * <p>Immutable record — create via builder or constructor.
 */
public record PlacementRequest(
        /** BOM category: BEDROOM, KITCHEN, BATHROOM, CORRIDOR, etc. */
        String bomCategory,
        /** IFC class (for clash rules): IfcFireSuppressionTerminal, IfcWall, etc. */
        String ifcClass,
        /** Discipline: ARC, STR, FPR, ELEC, SP, ACMV, etc. */
        String discipline,
        /** Allocated width in mm */
        double widthMm,
        /** Allocated depth in mm */
        double depthMm,
        /** Allocated height in mm */
        double heightMm,
        /** World position X in mm */
        double positionXMm,
        /** World position Y in mm */
        double positionYMm,
        /** World position Z in mm */
        double positionZMm,
        /** Verb that placed this element: SNAP, SCREW, JOIN, TILE, ROUTE */
        String verb,
        /** Parent BOM id (for container constraint check) */
        int parentBomId,
        /** Storey name (for same-storey spatial queries) */
        String storey
) {
    /**
     * Area in m² (width × depth converted from mm²).
     */
    public double areaSqM() {
        return (widthMm * depthMm) / 1_000_000.0;
    }

    /**
     * Minimum plan dimension in mm.
     */
    public double minDimMm() {
        return Math.min(widthMm, depthMm);
    }
}
