/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import java.util.List;

/**
 * A BOM that has been bound to a resolved world position.
 *
 * <p>{@link IBOMChildLine} is the declaration — local offsets stored in
 * {@code m_attribute}, relative to the parent's space bbox.
 * {@code BoundBOM} is the result — the same BOM after the compiler has
 * accumulated the full positional chain from the building root downward.
 *
 * <p>Chain accumulation:
 * <pre>
 * BUILDING_SH_STD  worldX=1.620, worldY=-1.246, worldZ=0, rot=0   ← absolute anchor
 *   FLOOR_SH_GF   +dX=0,        +dY=0,         +dZ=0              → worldZ=0
 *     LIVING_SET  +dX=0,        +dY=0,         +dZ=0              → room world origin
 *       Sofa      +dX=-1.1,     +dY=0.5,       +dZ=0,  rot=0     → (0.520, -0.746, 0)
 * </pre>
 *
 * <p>A {@code BoundBOM} with {@code resolved=false} has a gap in its chain.
 * The compiler must not emit geometry from an unresolved node — the worker
 * does not know where to put the item.
 *
 * <p>Maps to: the in-memory expansion produced by
 * {@code BOMTierResolver.expandBOMNode()} at each level of the DAG.
 *
 * @param bomId       BOM or product catalog ID (FK to {@code m_bom.bom_id} or leaf)
 * @param worldX      Resolved X world coordinate, meters
 * @param worldY      Resolved Y world coordinate, meters
 * @param worldZ      Resolved Z world coordinate (floor elevation), meters
 * @param rotRad      Resolved rotation about Z axis, radians (accumulated through chain)
 * @param widthMm     Space bbox width in mm (extracted from metadata, never invented)
 * @param depthMm     Space bbox depth in mm
 * @param heightMm    Space bbox height in mm
 * @param resolved    True if the full parent chain is complete — no positional gap
 * @param children    Child {@code BoundBOM} instances at the next level of the hierarchy
 */
public record BoundBOM(
        String   bomId,
        double   worldX,
        double   worldY,
        double   worldZ,
        double   rotRad,
        double   widthMm,
        double   depthMm,
        double   heightMm,
        boolean  resolved,
        List<BoundBOM> children
) {
    /** Children are immutable once bound — the tree does not mutate after resolution. */
    public BoundBOM {
        children = List.copyOf(children);
    }

    /** World max-X of this BOM's space bbox (worldX + width). */
    public double maxX() { return worldX + widthMm / 1000.0; }

    /** World max-Y of this BOM's space bbox (worldY + depth). */
    public double maxY() { return worldY + depthMm / 1000.0; }

    /** World max-Z — ceiling of this BOM's space (worldZ + height). */
    public double maxZ() { return worldZ + heightMm / 1000.0; }

    /**
     * Count of all descendant nodes (recursive) — total items bound under this BOM.
     */
    public int totalDescendants() {
        return children.stream()
                .mapToInt(c -> 1 + c.totalDescendants())
                .sum();
    }
}
