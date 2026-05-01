/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.bom.walker;

import com.bim.ormsandbox.po.MProduct;

/**
 * Runtime mesh computation for variable-length MEP pieces.
 *
 * <p>Like a contractor's workshop: takes a standard piece template from
 * component_library.db, recomputes the mathematical primitive (cylinder,
 * box, torus arc) at the required length. LOD (face count, tessellation)
 * comes from the library template. Dimensions come from the BOM line
 * (qty + c_uom_id).
 *
 * <p>This is NOT parametric mesh generation. MEP pieces are mathematical
 * primitives — their shape is fully determined by diameter + length.
 * Materials are flat RGBA per face (no UV mapping). A shorter cylinder has
 * shorter side faces with the same uniform colour. No distortion.
 * The library piece is the template; only the forward-axis half-extent changes.
 *
 * <p>Results are ephemeral — not stored back to library, ERP.db, or _BOM.db.
 *
 * <p>UOM drives this class, not a CUT verb. This follows the Compiere BOM Drop
 * convention: BOMQty + C_UOM_ID, where qty=2345 + UOM=MM means "2345 mm of
 * this material". A CUT verb would be redundant and is explicitly excluded.
 *
 * <p>Guard: {@code qty_type=FIXED} pieces (tees, elbows, sprinklers) are
 * rejected before recompute — their tack i/o is predetermined and must not
 * shift. Only {@code qty_type=VARIABLE} pieces (straight pipe, straight duct)
 * are eligible.
 *
 * <p>Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §6 — InterimWorkshop
 * <br>iDempiere convention: BOMQty + C_UOM_ID (Compiere BOM Drop, Red1)
 * <br>Witness: W-WORKSHOP-1 (UOM-driven primitive recomputation)
 */
public class InterimWorkshop {

    /**
     * BOM line variability type — prevents rogue code from workshop-modifying
     * fixed-geometry pieces (tees, elbows, sprinklers).
     */
    public enum QtyType {
        /** Tack i/o predetermined. Workshop rejected. (tee, elbow, terminal) */
        FIXED,
        /** Outlet shifts with length. Workshop allowed. (pipe straight, duct straight) */
        VARIABLE
    }

    /**
     * Recompute half-extents for a variable-length piece.
     *
     * <p>The library product provides the cross-section dimensions (diameter,
     * wall thickness). Only the forward-axis half-extent is replaced by
     * {@code targetLengthM / 2.0}. Cross-section axes are unchanged.
     *
     * <p>Guard: if {@code product} is null or {@code forwardAxis} is unrecognised,
     * returns null — the caller must fall back to library dimensions.
     *
     * @param product       M_Product (carries width/depth/height in metres)
     * @param forwardAxis   from component_library.db (X, Y, or Z)
     * @param targetLengthM target length in metres (from BOM line qty + UOM)
     * @return [halfW, halfD, halfH] with forward axis replaced, or null if invalid
     */
    public static double[] recompute(MProduct product, String forwardAxis,
                                     double targetLengthM) {
        if (product == null || forwardAxis == null) return null;

        double w = product.getWidth();
        double d = product.getDepth();
        double h = product.getHeight();

        return switch (forwardAxis) {
            case "X" -> new double[]{ targetLengthM / 2.0, d / 2.0, h / 2.0 };
            case "Y" -> new double[]{ w / 2.0, targetLengthM / 2.0, h / 2.0 };
            case "Z" -> new double[]{ w / 2.0, d / 2.0, targetLengthM / 2.0 };
            default  -> null;
        };
    }

    /**
     * Convert BOM line qty to metres based on c_uom_id.
     *
     * <p>Only MM and M are length units. EA, KG, M2, M3 are not lengths and
     * should not reach this method — callers must guard with
     * {@link com.bim.ormsandbox.po.X_M_BOMLine#isLengthBased()}.
     *
     * @param qty    raw qty value from BOM line (INTEGER — see FACTORIZE-v1)
     * @param uomId  c_uom_id value (MM or M)
     * @return length in metres
     */
    public static double qtyToMetres(double qty, String uomId) {
        return switch (uomId) {
            case "MM" -> qty / 1000.0;
            case "M"  -> qty;
            default   -> qty; // EA, KG, M2, M3 — not a length, should not reach here
        };
    }
}
