/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d;

/**
 * Thrown when the 2D drawing engine would produce invented geometry.
 *
 * §1 R1: every line, number, and label must trace to either the building
 * database or the drawing template. If a value cannot be traced, it must
 * not exist. This exception enforces that rule at runtime.
 *
 * Hard-fail rules:
 * - ROOF_HULL: curved mesh collapsed to bbox by convex hull
 * - CLOSE_INVENTED: open polyline would be closed with imaginary segment
 * - LEVEL_MARKERS_MISSING: elevation sheet has 0 level markers
 */
public class DrawingInventionError extends RuntimeException {

    private final String rule;

    public DrawingInventionError(String rule, String message) {
        super("§HARD_FAIL " + rule + ": " + message);
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
