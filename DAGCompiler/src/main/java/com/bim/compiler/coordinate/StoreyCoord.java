package com.bim.compiler.coordinate;

/**
 * Wall anchor in storey/room space — the starting position for a BOM assembly.
 *
 * <p>Produced by {@code computeZoneAnchor()} from wall rule + room bounds.
 * Encodes both position (x, y, z) and the wall-facing orientation (rotation)
 * so callers do not need a separate {@code wallRotation} variable.
 *
 * <p>Also used as the recursive anchor when expanding nested BOM assemblies:
 * a child's {@link WorldCoord} is promoted to a new {@code StoreyCoord} via
 * {@link #fromWorld(WorldCoord)} before recursing into its sub-BOM.
 *
 * <p>Unlike {@link WorldCoord}, this type has an unrestricted public constructor —
 * it is a legitimate input to the accumulation chain, not its output.
 */
public record StoreyCoord(double x, double y, double z, double rotation)
        implements Coordinate {

    /**
     * Promote a child's world position to a new BOM anchor for recursive expansion.
     *
     * <p>Used when a BOM child itself has a {@code child_bom_id}: the child's
     * accumulated world position becomes the parent anchor for the sub-BOM's children.
     */
    public static StoreyCoord fromWorld(WorldCoord w) {
        return new StoreyCoord(w.x(), w.y(), w.z(), w.rotation());
    }

    /**
     * Promote this anchor to a WorldCoord at the same position (zero local offset).
     * Used when a leaf element IS the anchor — no child offset needed.
     */
    WorldCoord asWorld() {
        return new WorldCoord(x, y, z, rotation);
    }
}
