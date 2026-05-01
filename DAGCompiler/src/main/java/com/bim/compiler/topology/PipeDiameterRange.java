/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pipe diameter validation ranges by discipline.
 * Extracted from enhanced_federation_GI.db
 * Query: SELECT discipline, MIN(diameter_mm), MAX(diameter_mm), COUNT(DISTINCT diameter_mm)
 *        FROM pipe_analysis GROUP BY 1;
 *
 * From FACT 4: Pipe Diameter Ranges by Discipline
 * - CW:  4-141mm,  55 unique sizes
 * - FP:  3-168mm,  32 unique sizes
 * - LPG: 3-383mm,  9 unique sizes
 * - SP:  3-6696mm, 99 unique sizes (includes large drains)
 *
 * EXTRACTED - DO NOT INVENT ADDITIONAL RANGES
 */
public final class PipeDiameterRange {

    private PipeDiameterRange() {} // Prevent instantiation

    private static final Map<Discipline, Range> RANGES;

    static {
        RANGES = new EnumMap<>(Discipline.class);
        // All values in meters (from CODE PATTERN 1)
        // Updated to include BOTH segment and fitting max dimensions
        // (fittings are larger due to branches/tees)
        RANGES.put(Discipline.CW,  new Range(0.004, 0.155, 55));   // Curtain wall (fitting max)
        RANGES.put(Discipline.FP,  new Range(0.003, 0.184, 32));   // Fire protection (fitting max)
        RANGES.put(Discipline.LPG, new Range(0.003, 0.383, 9));    // Gas piping
        RANGES.put(Discipline.SP,  new Range(0.003, 6.696, 99));   // Sanitary/plumbing
    }

    /**
     * Range record for pipe diameters.
     * @param minMeters minimum diameter in meters
     * @param maxMeters maximum diameter in meters
     * @param uniqueSizes count of unique sizes found in DB
     */
    public record Range(double minMeters, double maxMeters, int uniqueSizes) {

        public double minMm() {
            return minMeters * 1000;
        }

        public double maxMm() {
            return maxMeters * 1000;
        }

        /**
         * Check if diameter is within range.
         * Uses 1mm tolerance for floating point precision at boundaries.
         */
        public boolean contains(double diameterMeters) {
            final double TOLERANCE = 0.001; // 1mm tolerance
            return diameterMeters >= (minMeters - TOLERANCE)
                && diameterMeters <= (maxMeters + TOLERANCE);
        }
    }

    /**
     * Check if a pipe diameter is valid for a discipline.
     * @param discipline the piping discipline (CW, FP, LPG, SP)
     * @param diameterMeters pipe diameter in meters
     * @return true if within valid range
     */
    public static boolean isValid(Discipline discipline, double diameterMeters) {
        Range range = RANGES.get(discipline);
        if (range == null) {
            return false; // Not a piping discipline
        }
        return range.contains(diameterMeters);
    }

    /**
     * Get the valid range for a discipline.
     * @param discipline the piping discipline
     * @return the range, or null if not a piping discipline
     */
    public static Range getRange(Discipline discipline) {
        return RANGES.get(discipline);
    }

    /**
     * Check if a discipline uses pipes.
     * @param discipline the discipline to check
     * @return true if discipline has pipe diameter range
     */
    public static boolean isPipingDiscipline(Discipline discipline) {
        return RANGES.containsKey(discipline);
    }

    /**
     * Calculate typical pipe diameter for a discipline (average).
     * From PATTERN 4: Dimensional Standards
     * @param discipline the piping discipline
     * @return average diameter in meters, or 0 if not a piping discipline
     */
    public static double getTypicalDiameter(Discipline discipline) {
        return switch (discipline) {
            case FP -> 0.039;   // 39mm - small fire sprinkler pipes
            case SP -> 0.236;  // 236mm - large drainage
            case CW -> 0.050;  // 50mm - curtain wall services
            case LPG -> 0.036; // 36mm - gas piping
            default -> 0.0;
        };
    }
}
