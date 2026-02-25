package com.bim.compiler.coordinate;

/**
 * BOM child offset relative to its parent BOM anchor.
 *
 * <p>Values come from {@code m_attribute}: dx, dy, dz, rotation_rule.
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

    /**
     * Extract placement offset from a BOM child record.
     *
     * <p>Single extraction point — all BOM-to-LocalCoord conversion MUST use this.
     * Handles both literal radian values and semantic rotation rules
     * (FACE_INTO_ROOM, PARALLEL_TO_WALL, etc.). Prevents the latent bug
     * where paramDouble("rotation_rule", 0) silently drops semantics.
     *
     * @param dx            child dx offset (metres, from m_bom_line)
     * @param dy            child dy offset (metres, from m_bom_line)
     * @param dz            child dz offset (metres, from m_bom_line)
     * @param rotationRule  rotation_rule string (literal radians OR semantic)
     * @param wallContext   wall name for semantic resolution (null if no wall context)
     */
    public static LocalCoord fromBOMChild(
            double dx, double dy, double dz,
            String rotationRule, String wallContext) {
        double rotation = resolveRotation(rotationRule, wallContext);
        return new LocalCoord(dx, dy, dz, rotation);
    }

    /**
     * Resolve a rotation_rule string to radians.
     *
     * <p>Single definition for all rotation resolution in the compiler.
     * Handles literal radian strings ("0", "3.14159265") and semantic rules
     * (FACE_INTO_ROOM, PARALLEL_TO_WALL, etc.). Unknown semantics fail-safe to 0.
     *
     * @param rotationRule  rotation_rule value from m_attribute
     * @param wallContext   lowercase wall name for semantic resolution (null if no wall context)
     * @return resolved rotation in radians
     */
    public static double resolveRotation(String rotationRule, String wallContext) {
        if (rotationRule == null || rotationRule.isEmpty()) return 0;
        return switch (rotationRule) {
            case "FACE_INTO_ROOM", "FACE_AWAY_FROM_WALL" ->
                wallContext != null ? wallToRotation(wallContext) : 0;
            case "PARALLEL_TO_WALL" ->
                wallContext != null ? wallToRotation(wallContext) + Math.PI / 2 : 0;
            default -> {
                try { yield Double.parseDouble(rotationRule); }
                catch (NumberFormatException e) { yield 0; }
            }
        };
    }

    /**
     * Convert wall name to rotation angle (fixture facing into room from wall).
     *
     * <p>Single definition — all wall-to-radians mapping MUST use this.
     * South=0, North=π, West=-π/2, East=π/2.
     *
     * @param wall  lowercase wall name (south, north, east, west)
     * @return rotation in radians
     */
    public static double wallToRotation(String wall) {
        return switch (wall.toLowerCase()) {
            case "south"  -> 0;
            case "north"  -> Math.PI;
            case "west"   -> -Math.PI / 2;
            case "east"   -> Math.PI / 2;
            default       -> 0;
        };
    }
}
