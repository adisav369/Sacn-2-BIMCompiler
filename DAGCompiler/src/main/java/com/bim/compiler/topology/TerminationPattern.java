/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pipe/duct termination patterns by discipline.
 * From PATTERN G10 in SESSION_STATE.md.
 *
 * Query: Fitting-to-pipe ratio by discipline
 * - CW:  1.03 (1:1 ratio)
 * - FP:  1.18 (branching systems)
 * - LPG: 1.16
 * - SP:  0.82 (more continuous runs)
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public final class TerminationPattern {

    private TerminationPattern() {}

    /**
     * Termination type for linear elements.
     */
    public enum TerminationType {
        CONNECTED_TO_FITTING,  // Most common: pipe ends at fitting
        CAPPED,                // End cap or plug
        OPEN,                  // Open end (unlikely in finished model)
        PENETRATES_ELEMENT     // Goes through wall/slab
    }

    /**
     * Fitting-to-pipe ratio by discipline.
     * Higher ratio = more branching/connections.
     */
    private static final Map<Discipline, Double> FITTING_RATIOS;

    static {
        FITTING_RATIOS = new EnumMap<>(Discipline.class);
        FITTING_RATIOS.put(Discipline.CW, 1.03);   // 1:1 ratio
        FITTING_RATIOS.put(Discipline.FP, 1.18);   // Most branching
        FITTING_RATIOS.put(Discipline.LPG, 1.16);
        FITTING_RATIOS.put(Discipline.SP, 0.82);   // Fewest fittings
    }

    /**
     * Get the typical fitting-to-pipe ratio for a discipline.
     * @param discipline the piping discipline
     * @return ratio
     */
    public static double getFittingRatio(Discipline discipline) {
        Double ratio = FITTING_RATIOS.get(discipline);
        if (ratio == null)
            throw new IllegalArgumentException(
                "No fitting ratio for discipline " + discipline);
        return ratio;
    }

    /**
     * Check if discipline has high branching (more fittings than pipes).
     * @param discipline the discipline
     * @return true if fitting ratio > 1.1
     */
    public static boolean isHighBranching(Discipline discipline) {
        return getFittingRatio(discipline) > 1.1;
    }

    /**
     * Check if discipline has continuous runs (fewer fittings).
     * @param discipline the discipline
     * @return true if fitting ratio < 0.9
     */
    public static boolean hasContinuousRuns(Discipline discipline) {
        return getFittingRatio(discipline) < 0.9;
    }

    /**
     * Estimate expected fitting count for a given pipe count.
     * @param discipline the discipline
     * @param pipeCount number of pipes
     * @return expected fitting count
     */
    public static int estimateFittingCount(Discipline discipline, int pipeCount) {
        return (int) Math.round(pipeCount * getFittingRatio(discipline));
    }
}
