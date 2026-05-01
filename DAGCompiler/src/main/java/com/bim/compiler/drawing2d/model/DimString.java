/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d.model;

/**
 * A dimension annotation string between two points.
 *
 * text: formatted dimension value ("4900", "14100")
 * fromLabel/toLabel: grid or element labels for provenance
 * pos: position along the dimension line (paper-space mm)
 * span: distance in metres (world space)
 */
public record DimString(
    String text,
    String fromLabel,
    String toLabel,
    double pos,
    double span
) {
    /** Format a dimension value: metres → mm string, no decimals. */
    public static String formatDim(double metres) {
        long mm = Math.round(metres * 1000.0);
        return String.valueOf(mm);
    }
}
