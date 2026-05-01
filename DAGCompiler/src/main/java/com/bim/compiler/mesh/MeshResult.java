/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.mesh;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Mesh;

/**
 * Typed result from a {@link ParametricMesh} generator.
 *
 * <p>Carries:
 * <ul>
 *   <li>{@code mesh} — the computed vertices + triangulated faces</li>
 *   <li>{@code bounds} — bounding box derived from vertices (computed, not stored)</li>
 *   <li>{@code provenance} — trace string linking back to the source parameters,
 *       e.g. {@code "COMPUTED_FROM_PARAMS:GABLE_ROOF_MY"}</li>
 * </ul>
 *
 * <p>iDempiere analogy: like a {@code C_Invoice} line that carries the
 * computed result of a {@code C_Order} line — the derivation is documented
 * in the provenance, not re-computed later.
 *
 * <p>Contract: {@code bounds.volume() > 0} — a zero-volume mesh is a
 * dimensional contract violation and must not be written to the output DB.
 */
public record MeshResult(
    Mesh mesh,
    BoundingBox bounds,
    String provenance
) {
    public MeshResult {
        double vol = bounds.width() * bounds.depth() * bounds.height();
        if (vol <= 0)
            throw new IllegalArgumentException(
                "DimensionalContractViolation: zero-volume mesh in " + provenance);
    }
}
