package com.bim.designer.validation;

/**
 * TerrainSnap — snaps infrastructure elements to terrain surface.
 *
 * <p>During interactive drag, elements "snuggle" onto the terrain:
 * their Z is continuously updated from {@link PlacementContext#elevationAt(double, double)}.
 * The snap mode determines HOW the element relates to the terrain surface:
 *
 * <ul>
 *   <li>{@link Mode#ON_SURFACE} — element base sits on terrain (road course, sleeper)</li>
 *   <li>{@link Mode#ABOVE} — element floats above terrain by clearance (bridge deck)</li>
 *   <li>{@link Mode#BELOW} — element buried below terrain (tunnel, pipeline)</li>
 *   <li>{@link Mode#PIER} — element extends from terrain down to foundation depth,
 *       or up to deck level (bridge pier, abutment)</li>
 * </ul>
 *
 * <p>Wireframe bboxes are shown during drag. On CO save to output DB, the
 * actual geometry is computed incrementally and shape updates.
 *
 * <p>Usage in drag loop:
 * <pre>
 *   TerrainSnap snap = TerrainSnap.onSurface(layerOffsetMm);
 *   double z = snap.computeZ(terrain, dragX, dragY, elementHeightMm);
 *   // Update bbox: minZ = z, maxZ = z + elementHeightMm
 * </pre>
 *
 * // Implementing INFRA_DESIGNER_SRS.md §5 — Witness: W-TERRAIN-SNAP-1
 */
public record TerrainSnap(Mode mode, double offsetMm) {

    public enum Mode {
        /** Base on terrain surface + offset. Road layers, sleepers, ballast. */
        ON_SURFACE,
        /** Above terrain by clearance. Bridge decks, overhead lines. */
        ABOVE,
        /** Below terrain by depth. Tunnels, pipelines, foundations. */
        BELOW,
        /** Extends from terrain to a target level. Piers, abutments, retaining walls. */
        PIER
    }

    // ── Factory methods ─────────────────────────────────────────────

    /** Road course, sleeper, ballast — sits on terrain + stack offset. */
    public static TerrainSnap onSurface(double layerOffsetMm) {
        return new TerrainSnap(Mode.ON_SURFACE, layerOffsetMm);
    }

    /** Bridge deck — floats above terrain by clearanceMm. */
    public static TerrainSnap above(double clearanceMm) {
        return new TerrainSnap(Mode.ABOVE, clearanceMm);
    }

    /** Tunnel, pipeline — buried at depthMm below terrain. */
    public static TerrainSnap below(double depthMm) {
        return new TerrainSnap(Mode.BELOW, depthMm);
    }

    /** Pier — base at terrain, extends up (positive offset) or down (negative). */
    public static TerrainSnap pier() {
        return new TerrainSnap(Mode.PIER, 0);
    }

    // ── Z computation ───────────────────────────────────────────────

    /**
     * Compute the element's base Z (minZ) given the terrain at (x, y).
     *
     * @param terrain         the placement context providing elevation
     * @param xMm             drag position X (mm)
     * @param yMm             drag position Y (mm)
     * @param elementHeightMm element height (mm) — used for PIER mode
     * @return base Z (mm) for the element's bbox minZ
     */
    public double computeZ(PlacementContext terrain, double xMm, double yMm,
                           double elementHeightMm) {
        double terrainZ = terrain.elevationAt(xMm, yMm);

        return switch (mode) {
            case ON_SURFACE -> terrainZ + offsetMm;
            case ABOVE      -> terrainZ + offsetMm;
            case BELOW      -> terrainZ - offsetMm - elementHeightMm;
            case PIER       -> terrainZ;  // base at terrain, height extends upward
        };
    }

    /**
     * Compute full bbox Z range for an element at (x, y).
     *
     * @return [minZ, maxZ] in mm
     */
    public double[] computeZRange(PlacementContext terrain, double xMm, double yMm,
                                   double elementHeightMm) {
        double baseZ = computeZ(terrain, xMm, yMm, elementHeightMm);
        return switch (mode) {
            case ON_SURFACE, ABOVE -> new double[]{ baseZ, baseZ + elementHeightMm };
            case BELOW             -> new double[]{ baseZ, baseZ + elementHeightMm };
            case PIER              -> new double[]{ baseZ, baseZ + elementHeightMm };
        };
    }

    /**
     * Compute Z for a road layer stack at a given position.
     * Each layer stacks on the previous: subgrade → base → binder → surface.
     *
     * @param terrain         terrain context
     * @param xMm             position X
     * @param yMm             position Y
     * @param layerIndex      0 = bottom (subgrade), 1 = base, 2 = binder, 3 = surface
     * @param layerThicknesses thickness of each layer (mm), bottom to top
     * @return base Z for this layer
     */
    public static double computeLayerZ(PlacementContext terrain, double xMm, double yMm,
                                        int layerIndex, double[] layerThicknesses) {
        double terrainZ = terrain.elevationAt(xMm, yMm);
        double offset = 0;
        for (int i = 0; i < layerIndex; i++) {
            offset += layerThicknesses[i];
        }
        return terrainZ + offset;
    }
}
