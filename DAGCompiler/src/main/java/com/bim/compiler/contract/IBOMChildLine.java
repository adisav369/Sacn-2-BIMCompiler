package com.bim.compiler.contract;

/**
 * Contract for a BOM child line — one line in a Bill of Materials.
 *
 * <p>A building is made of exactly what a vehicle is made of: bricks, beams, designs.
 * Raw materials → components → sub-assemblies → assemblies → complete product.
 * Each level in the chain must tell the worker two things:
 * <ol>
 *   <li><b>What</b> — which child BOM or product (the item or set)</li>
 *   <li><b>Where</b> — intra-space location + orientation within the parent BOM's space bbox</li>
 * </ol>
 *
 * <p>Without both, there is a gap. The worker is at a loss where to put the item.
 * A child line with no location or no orientation is incomplete and must be rejected.
 *
 * <p>iDempiere analogy: {@code M_BOM_Line} carries {@code M_Product_ID} (what) plus
 * quantity attributes. Here the spatial attributes replace quantity — location and
 * orientation are the positional quantity of a BOM child in space.
 *
 * <p>Chain accumulation: each child's world position is computed at compile time by
 * adding its intra-space offset to the parent's resolved world origin. The values
 * stored here are local to the parent's space bbox — never absolute world coordinates.
 *
 * <pre>
 * UNIT_SH_STD (world origin: 1.620, -1.246)
 *   FLOOR_SH_GF_STD  dX=0, dY=0, dZ=0,       rot=0
 *     LIVING_SET     dX=0, dY=0, dZ=0,         rot=0    (room at floor-relative pos)
 *       Sofa_3Seat   dX=-1.1, dY=0.5, dZ=0,   rot=0
 *       Coffee_Table dX= 0.0, dY=1.2, dZ=0,   rot=0
 * </pre>
 *
 * <p>Maps to: {@code ad_bom_child_param} (dx, dy, dz, rotation_rule) — one row per
 * child per BOM. The parent BOM is {@code ad_bom}; the child reference is
 * {@code ad_bom_child.child_bom_id} or {@code child_name_pattern}.
 *
 * @see IHostable  — for elements hosted by a spatial container (room, wall, ceiling)
 */
public interface IBOMChildLine {

    /**
     * Identity of the child — BOM ID or product catalog ID.
     * FK to {@code ad_bom.bom_id} (for assemblies) or {@code ad_product_dim.product_id}
     * (for leaf items: chair, table, screw, basket, vase).
     *
     * @return child reference, never null
     */
    String childRef();

    /**
     * X offset of this child within the parent BOM's space bbox, in meters.
     * Measured from the parent's local origin (min-corner of its space bbox).
     * Positive = eastward (right) within parent space.
     *
     * @return local X offset, 0.0 if at parent origin
     */
    double dx();

    /**
     * Y offset of this child within the parent BOM's space bbox, in meters.
     * Positive = northward (back wall direction) within parent space.
     *
     * @return local Y offset, 0.0 if at parent origin
     */
    double dy();

    /**
     * Z offset of this child within the parent BOM's space bbox, in meters.
     * Positive = upward. Storeys use this to declare floor elevation above unit origin.
     * Furniture at floor level = 0.0.
     *
     * @return local Z offset, 0.0 for ground-level items
     */
    double dz();

    /**
     * Orientation rule for this child within the parent BOM's space.
     *
     * <p>Either a literal radian value ("0", "1.5707", "-1.5707", "3.14159")
     * or a semantic rule resolved at compile time against the parent MANIFEST:
     * <ul>
     *   <li>{@code FACE_INTO_ROOM} — front face toward the room interior</li>
     *   <li>{@code FACE_AWAY_FROM_WALL} — back against the WALL_BACK face</li>
     *   <li>{@code PARALLEL_TO_WALL} — long axis runs along the wall</li>
     * </ul>
     *
     * <p><b>Block-level:</b> this rule governs the entire child block — all its
     * sub-children rotate with it. Without it, blocks from different rooms pile at
     * the same angle: furniture sunken or overlapping (the TB-LKTN symptom).
     *
     * <p><b>Self-orienting guarantee:</b> a door seen from outside appears on a
     * different wall than from inside — manual assignment gets this wrong. The BOM
     * chain resolves orientation from extracted world coordinates (the Stone geometry),
     * not visual judgment. The toilet's {@code FACE_AWAY_FROM_WALL} then resolves
     * against the objectively anchored MANIFEST: it cannot be wrong-facing as long
     * as the chain is complete and phantom items (door, exterior wall) are in the BOM.
     *
     * @return literal radian string or semantic rule name, never null
     */
    String rotationRule();

    /**
     * Sequence order of this child within the parent BOM.
     * Lower sequence = placed first (reserves space first for clearance enforcement).
     * Matches {@code ad_bom_child.sequence}.
     *
     * @return placement sequence (1-based)
     */
    int sequence();

    /**
     * Whether this child line is required for the parent BOM to be valid.
     * A required child missing its location or orientation is a compile-time error.
     * An optional child missing its location produces a warning only.
     *
     * @return true if this child must be present and fully located
     */
    boolean isRequired();
}
