/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Parsed shed definition from DSL.
 *
 * Syntax:
 * SHED "name" size:WxDm height:Hm {
 *     FOUNDATION slab:Tmm
 *     DOOR direction size:WxH
 *     WINDOW direction size:WxH
 *     ROOF pitch:Ddeg
 * }
 */
public record ShedDefinition(
    String name,
    double width,       // X dimension (meters)
    double depth,       // Y dimension (meters)
    double wallHeight,  // Wall height to eaves (meters)
    double foundationThickness,  // Slab thickness (meters)
    DoorSpec door,
    WindowSpec window,
    RoofSpec roof
) {
    public record DoorSpec(
        Direction side,
        double width,
        double height
    ) {}

    public record WindowSpec(
        Direction side,
        double width,
        double height
    ) {}

    public record RoofSpec(
        double pitchDegrees  // Roof pitch angle
    ) {
        public double pitchRadians() {
            return Math.toRadians(pitchDegrees);
        }

        /**
         * Calculate ridge height above eaves.
         * For a gable roof: ridge_rise = (span/2) * tan(pitch)
         */
        public double ridgeRise(double span) {
            return (span / 2.0) * Math.tan(pitchRadians());
        }
    }

    public enum Direction {
        NORTH, SOUTH, EAST, WEST;

        public boolean isXAligned() {
            return this == NORTH || this == SOUTH;
        }
    }
}
