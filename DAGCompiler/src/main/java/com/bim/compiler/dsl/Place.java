package com.bim.compiler.dsl;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.Vector3D;

/**
 * Place — the fundamental spatial unit for every geometry element in the compiler.
 *
 * <p>A Place captures the full positional identity of one BOM child after layout:
 * where it sits ({@link #anchor}), which way it faces ({@link #front}), which way
 * is up ({@link #up}), along which axis the GPD advances ({@link #hostAxis}),
 * and what space it occupies ({@link #bbox}).
 *
 * <p><b>Invariant:</b> {@code front ⊥ hostAxis} — the item faces INTO the locator
 * (perpendicular to the wall), while the GPD walks ALONG the wall edge.
 *
 * <p><b>Units:</b> all coordinates in <b>meters</b> (world-coordinate system).
 * Extent values for {@link PhantomLayout} capacity tracking are in <b>mm</b>
 * (ALB grid from {@code ad_room_boundary} / {@code wm_empty_storage_line}).
 *
 * <p>Variance children (SPACER_VAR, {@code is_variance=1}) have a null {@link #bbox}.
 * Their extent is unknown until all fixed siblings are placed; the remaining room
 * capacity from {@link PhantomLayout#remainingMm()} defines their size.
 *
 * @param elementRef  BOM child reference (product_id or family_ref for tracing)
 * @param locatorRef  M_Locator zone: NORTH_WALL, SOUTH_WALL, EAST_WALL, WEST_WALL, CENTRE, FLOAT
 * @param sequenceNo  Position within this locator's GPD sequence (1-based)
 * @param bbox        World-coordinate bounding box (meters); null for variance children
 * @param anchor      GPD insertion point — world coordinates (meters)
 * @param front       Unit vector: direction the item faces (into room, perpendicular to hostAxis)
 * @param up          Unit vector: upward direction (typically {@link Vector3D#Z_AXIS})
 * @param hostAxis    Unit vector: GPD advance direction (parallel to host wall)
 */
public record Place(
        String elementRef,
        String locatorRef,
        int sequenceNo,
        BoundingBox bbox,       // nullable — variance child has no fixed bbox
        Point3D anchor,
        Vector3D front,
        Vector3D up,
        Vector3D hostAxis
) {

    // ── Convenience factories ─────────────────────────────────────────────────

    /**
     * Factory for a fixed child (known bbox).
     * {@code up} defaults to {@link Vector3D#Z_AXIS} — standard floor-placed item.
     */
    public static Place fixed(String elementRef, String locatorRef, int sequenceNo,
                              BoundingBox bbox, Point3D anchor,
                              Vector3D front, Vector3D hostAxis) {
        return new Place(elementRef, locatorRef, sequenceNo,
                         bbox, anchor, front, Vector3D.Z_AXIS, hostAxis);
    }

    /**
     * Factory for a variance/spacer child (SPACER_VAR — no fixed bbox).
     * Placed at the current GPD anchor; extent resolved at locator completion
     * from {@link PhantomLayout#remainingMm()}.
     */
    public static Place variance(String elementRef, String locatorRef, int sequenceNo,
                                 Point3D anchor, Vector3D front, Vector3D hostAxis) {
        return new Place(elementRef, locatorRef, sequenceNo,
                         null, anchor, front, Vector3D.Z_AXIS, hostAxis);
    }

    // ── Derived geometry ──────────────────────────────────────────────────────

    /** True when this Place represents a variance/spacer child (null bbox). */
    public boolean isVariance() {
        return bbox == null;
    }

    /**
     * Extent of this Place along {@link #hostAxis} in mm.
     * Projects the bbox dimension onto hostAxis via absolute dot products.
     * Returns 0 for variance children — their extent is resolved at locator completion.
     *
     * <p>Examples:
     * <ul>
     *   <li>hostAxis=X_AXIS  →  extent = bbox.width() × 1000</li>
     *   <li>hostAxis=Y_AXIS  →  extent = bbox.depth() × 1000</li>
     *   <li>hostAxis=NEG_X   →  extent = bbox.width() × 1000  (magnitude only)</li>
     * </ul>
     */
    public double extentMm() {
        if (bbox == null) return 0.0;
        double wx = Math.abs(hostAxis.x()) * bbox.width();
        double wy = Math.abs(hostAxis.y()) * bbox.depth();
        double wz = Math.abs(hostAxis.z()) * bbox.height();
        return (wx + wy + wz) * 1000.0; // meters → mm
    }

    /**
     * Next GPD anchor after this Place — advances {@link #anchor} by
     * {@link #extentMm()} along {@link #hostAxis}.
     * For variance children, returns the same anchor (stride = 0).
     */
    public Point3D nextAnchor() {
        double strideM = extentMm() / 1000.0;
        return new Point3D(
            anchor.x() + hostAxis.x() * strideM,
            anchor.y() + hostAxis.y() * strideM,
            anchor.z() + hostAxis.z() * strideM
        );
    }

    @Override
    public String toString() {
        return String.format("Place{%s@%s seq=%d anchor=%s ext=%.0fmm}",
            elementRef, locatorRef, sequenceNo, anchor, extentMm());
    }
}
