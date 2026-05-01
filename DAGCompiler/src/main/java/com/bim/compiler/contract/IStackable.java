/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import java.util.List;

/**
 * Contract for elements that stack vertically across storeys.
 * Core pattern for high-rise construction.
 *
 * <p>Stackable Elements:
 * <ul>
 *   <li>Columns - structural continuity through floors</li>
 *   <li>Shear walls - lateral system continuity</li>
 *   <li>MEP risers - plumbing, electrical, HVAC verticals</li>
 *   <li>Shafts - lift, stair, service cores</li>
 *   <li>Stacks - wet areas aligned vertically</li>
 * </ul>
 *
 * <p>Pattern: Define once at typical floor, stack automatically.
 * <pre>
 * STACK "plumbing_riser" at:A1 {
 *     from: "Ground"
 *     to: "Level_20"
 *     system: PLUMBING_WASTE
 *     size: 150mm
 * }
 * </pre>
 *
 * <p>80/20: Most high-rise MEP is vertical risers + horizontal branches.
 * Get the stacks right, branches follow.
 */
public interface IStackable extends IBIMEntity {

    /**
     * Unique ID that persists across all storeys.
     * All instances of this stack share this ID.
     */
    String stackId();

    /**
     * Grid position where this stack is located.
     * Format: "A1", "B3", or "X.Y" coordinates.
     */
    String gridPosition();

    /**
     * First (lowest) storey of this stack.
     */
    String fromStorey();

    /**
     * Last (highest) storey of this stack.
     */
    String toStorey();

    /**
     * All storeys this stack passes through.
     * Ordered from bottom to top.
     */
    List<String> storeys();

    /**
     * Stack category for grouping and visualization.
     */
    StackCategory category();

    /**
     * Cross-section dimensions (for risers/shafts).
     * Null for point elements like columns.
     */
    default CrossSection section() {
        return null;
    }

    /**
     * Whether stack penetrates floor slabs.
     * True for risers/shafts, false for columns.
     */
    default boolean penetratesSlabs() {
        return true;
    }

    /**
     * Fire rating requirement for shaft enclosure.
     * Null if no rating required.
     */
    default String fireRating() {
        return null;
    }

    /**
     * Categories of vertical stacks.
     */
    enum StackCategory {
        STRUCTURAL,     // Columns, shear walls
        CIRCULATION,    // Stairs, lifts
        MEP_PLUMBING,   // Waste stacks, supply risers
        MEP_ELECTRICAL, // Electrical risers
        MEP_HVAC,       // Duct shafts
        MEP_FIRE,       // FP risers, smoke shafts
        SERVICE,        // Rubbish chutes, service lifts
        VOID            // Open voids, atriums
    }

    /**
     * Cross-section for shafts/risers.
     */
    sealed interface CrossSection permits CircularSection, RectangularSection {
        double area();
    }

    record CircularSection(double diameter) implements CrossSection {
        @Override public double area() { return Math.PI * diameter * diameter / 4; }
    }

    record RectangularSection(double width, double depth) implements CrossSection {
        @Override public double area() { return width * depth; }
    }
}
