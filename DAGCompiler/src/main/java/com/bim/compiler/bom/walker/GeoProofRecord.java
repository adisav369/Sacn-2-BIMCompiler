/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.bom.walker;

/**
 * Structured proof record capturing the full Input → Process → Output chain
 * for a single placed element. Emitted by PlacementCollectorVisitor during
 * the BOM walk; formatted by GeoProofFormatter after the walk completes.
 *
 * <p>The walker populates all fields EXCEPT {@code inputLBD}, which is null
 * at walk time (§6.12.1 Compilation Isolation Invariant — walker must not
 * read extraction DB). The proof stage enriches records with inputLBD from
 * the extraction DB for round-trip comparison.
 *
 * Implementing S144_geo_white_box_logging.md §Task 1 — Witness: W-GEO-PROOF
 */
public record GeoProofRecord(
    String guid,              // IFC GUID (from m_bom_line_ma or generated)
    String productId,         // child_product_id
    String bomChain,          // "BUILDING→FLOOR→SET→LEAF" path
    double[] inputLBD,        // from extraction: element minX/minY/minZ (null until proof stage)
    double[] parentAnchor,    // accumulated anchor from walker stack
    double cumRotation,       // accumulated rotation (radians)
    double[] lineOffset,      // raw dx/dy/dz from BOM line (before rotation)
    double[] rotatedOffset,   // dx/dy/dz after rotation applied
    double[] outputCentroid,  // final placed centroid (world absolute)
    double[] outputLBD,       // final placed LBD (world absolute)
    double[] halfExtents,     // halfW, halfD, halfH
    double[] parentAABB,      // parent's width/depth/height in metres (for envelope check)
    boolean roundTrip,        // inputLBD == outputLBD (codec identity) — false until enriched
    boolean lmpContained,     // outputLBD >= parentAnchor (current LMP check)
    boolean envelopeContained // outputMAX <= parentMAX (new envelope check)
) {

    /** Overshoot in each axis (positive = child exceeds parent). Zero or negative = contained. */
    public double[] overshootMetres() {
        if (parentAABB == null || parentAnchor == null || outputLBD == null || halfExtents == null) {
            return new double[]{0, 0, 0};
        }
        double childMaxX = outputLBD[0] + 2 * halfExtents[0];
        double childMaxY = outputLBD[1] + 2 * halfExtents[1];
        double childMaxZ = outputLBD[2] + 2 * halfExtents[2];
        double parentMaxX = parentAnchor[0] + parentAABB[0];
        double parentMaxY = parentAnchor[1] + parentAABB[1];
        double parentMaxZ = parentAnchor[2] + parentAABB[2];
        return new double[]{
            childMaxX - parentMaxX,
            childMaxY - parentMaxY,
            childMaxZ - parentMaxZ
        };
    }

    /** Maximum overshoot in mm across all axes (positive = overshoots). */
    public double maxOvershootMm() {
        double[] ov = overshootMetres();
        return Math.max(ov[0], Math.max(ov[1], ov[2])) * 1000.0;
    }

    /** Which axis has the largest overshoot, or "" if contained. */
    public String overshootAxis() {
        double[] ov = overshootMetres();
        double max = Math.max(ov[0], Math.max(ov[1], ov[2]));
        if (max <= 0.001) return "";
        if (max == ov[2]) return "Z";
        if (max == ov[0]) return "X";
        return "Y";
    }
}
