package com.bim.compiler.coordinate;

/**
 * BOM child offset relative to its parent BOM anchor.
 *
 * <p>Values come from {@code ad_bom_child_param}: dx, dy, dz, rotation_rule.
 * A zero dx/dy means "place directly at parent anchor" — it does NOT mean
 * "centre of room". Two children with dx=0/dy=0 produce the same world position
 * (bunching). This type makes that explicit.
 *
 * <p>The rotation is additive — it accumulates on top of the parent's rotation.
 *
 * <p>ACCUMULATION: {@link #toWorld(StoreyCoord)} is the only way to produce a
 * {@link WorldCoord}. Offsets are rotated by the parent's orientation before adding.
 */
public record LocalCoord(double dx, double dy, double dz, double rotation)
        implements Coordinate {

    /**
     * Accumulate this local offset into world position.
     *
     * <p>Applies parent rotation to dx/dy (orientation-aware offset), then adds
     * to parent position. This is the single definition of BOM child accumulation
     * in the compiler — the math that was previously repeated inline at every
     * expandBOMNode callsite.
     */
    public WorldCoord toWorld(StoreyCoord parent) {
        double cos = Math.cos(parent.rotation());
        double sin = Math.sin(parent.rotation());
        return new WorldCoord(
            parent.x() + dx * cos - dy * sin,
            parent.y() + dx * sin + dy * cos,
            parent.z() + dz,
            parent.rotation() + rotation
        );
    }

    /** Zero offset — child placed directly at parent anchor. */
    public static LocalCoord zero() {
        return new LocalCoord(0, 0, 0, 0);
    }
}
