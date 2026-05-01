/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

/**
 * Storey/floor conventions extracted from model.
 * From PATTERNS G8 and G9 in SESSION_STATE.md.
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public final class StoreyConvention {

    private StoreyConvention() {}

    // =========================================================================
    // FLOOR-TO-FLOOR (from PATTERN G8)
    // =========================================================================

    /**
     * Typical floor-to-floor height in meters.
     * From Z-level analysis: floors at 0, 8, 12, 16 = ~4m intervals
     */
    public static final double FLOOR_TO_FLOOR_HEIGHT = 4.0;

    /**
     * Slab-to-slab overlap at floor transitions (meters).
     * From query: -0.175 to -0.25m (slabs overlap)
     */
    public static final double SLAB_OVERLAP = 0.200;

    /**
     * Foundation level Z coordinate.
     */
    public static final double Z_FOUNDATION = -31.0;

    /**
     * Ground floor Z coordinate.
     */
    public static final double Z_GROUND = 0.0;

    // =========================================================================
    // MEP ZONE (from PATTERN G9)
    // =========================================================================

    /**
     * Minimum MEP zone height (ceiling to slab above).
     * From query: min 0.829m
     */
    public static final double MIN_MEP_ZONE = 0.830;

    /**
     * Typical MEP zone height.
     * From query: avg 0.916m
     */
    public static final double TYPICAL_MEP_ZONE = 0.900;

    /**
     * Maximum MEP zone height.
     * From query: max 1.604m
     */
    public static final double MAX_MEP_ZONE = 1.600;

    // =========================================================================
    // DERIVED DIMENSIONS
    // =========================================================================

    /**
     * Typical clear ceiling height (floor-to-floor minus slab minus MEP zone).
     * 4.0 - 0.3 (slab) - 0.9 (MEP) ≈ 2.8m
     */
    public static final double TYPICAL_CLEAR_HEIGHT = 2.8;

    /**
     * Typical structural slab thickness.
     * From PATTERN 4: 100-300mm, typically 200-300mm for STR
     */
    public static final double TYPICAL_SLAB_THICKNESS = 0.250;

    // =========================================================================
    // STOREY LEVELS (extracted Z values)
    // =========================================================================

    /**
     * Known floor Z levels from model.
     */
    public static final double[] FLOOR_LEVELS = {
        -31.0,  // Foundation
        -1.0,   // Basement
        0.0,    // Ground
        8.0,    // Level 2
        12.0,   // Level 3
        16.0,   // Level 4
        27.0    // Roof (from PATTERN 5)
    };

    /**
     * Get the storey index for a Z coordinate.
     * @param z the Z coordinate in meters
     * @return storey index (0 = foundation), or -1 if below foundation
     */
    public static int getStoreyIndex(double z) {
        for (int i = FLOOR_LEVELS.length - 1; i >= 0; i--) {
            if (z >= FLOOR_LEVELS[i] - 0.5) {  // 0.5m tolerance
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if Z coordinate is within MEP zone (ceiling to slab above).
     * @param z the Z coordinate
     * @param floorLevel the floor level Z
     * @return true if in MEP zone
     */
    public static boolean isInMepZone(double z, double floorLevel) {
        double ceilingTop = floorLevel + TYPICAL_CLEAR_HEIGHT;
        double slabBottom = floorLevel + FLOOR_TO_FLOOR_HEIGHT - TYPICAL_SLAB_THICKNESS;
        return z >= ceilingTop && z <= slabBottom;
    }
}
