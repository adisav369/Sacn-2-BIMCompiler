/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

/**
 * Wall thickness standards extracted from enhanced_federation_GI.db
 * Query: SELECT ROUND((maxX-minX)*1000) AS thickness_mm, COUNT(*)
 *        FROM elements_rtree r JOIN elements_meta m ON r.guid=m.guid
 *        WHERE ifc_class='IfcWall' GROUP BY 1 ORDER BY 2 DESC;
 *
 * ONLY 4 VALUES EXIST - Use enum, not free-form double
 * EXTRACTED - DO NOT INVENT ADDITIONAL THICKNESSES
 */
public enum WallThickness {
    MM_150(0.150, 306),  // 92% - standard interior walls
    MM_230(0.230, 6),    // 2%
    MM_250(0.250, 18),   // 5%
    MM_300(0.300, 3);    // 1% - exterior/structural walls

    private final double meters;
    private final int wallCount;

    WallThickness(double meters, int wallCount) {
        this.meters = meters;
        this.wallCount = wallCount;
    }

    /**
     * Thickness in meters (DB units)
     */
    public double getMeters() {
        return meters;
    }

    /**
     * Thickness in millimeters (display units)
     */
    public int getMillimeters() {
        return (int) (meters * 1000);
    }

    /**
     * Count from DB extraction (for reference)
     */
    public int getWallCount() {
        return wallCount;
    }

    /**
     * Find closest matching thickness from a measured value
     * Uses 5mm tolerance (from FACT 1: coordinate precision)
     *
     * @param thicknessMeters measured thickness in meters
     * @return closest matching enum, or null if outside all tolerances
     */
    public static WallThickness fromMeasurement(double thicknessMeters) {
        final double TOLERANCE = 0.005; // 5mm

        for (WallThickness wt : values()) {
            if (Math.abs(wt.meters - thicknessMeters) <= TOLERANCE) {
                return wt;
            }
        }
        return null; // Non-standard thickness
    }
}
