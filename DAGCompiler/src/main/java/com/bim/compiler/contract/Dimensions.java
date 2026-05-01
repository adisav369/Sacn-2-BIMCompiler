/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Physical dimensions of a component.
 * All values in meters.
 *
 * <p>Used in LOD400 components for dimensional verification.
 *
 * @param width X dimension in meters
 * @param depth Y dimension in meters
 * @param height Z dimension in meters
 */
public record Dimensions(double width, double depth, double height) {

    /**
     * Calculate volume in cubic meters.
     */
    public double volume() {
        return width * depth * height;
    }

    /**
     * Get the smallest dimension.
     * Useful for determining component orientation.
     */
    public double minDimension() {
        return Math.min(width, Math.min(depth, height));
    }

    /**
     * Get the largest dimension.
     * Useful for span calculations.
     */
    public double maxDimension() {
        return Math.max(width, Math.max(depth, height));
    }

    /**
     * Check if dimensions fit within other dimensions (with tolerance).
     *
     * @param other Outer dimensions to fit within
     * @param tolerance Allowed gap in meters
     * @return true if this fits within other + tolerance
     */
    public boolean fitsWithin(Dimensions other, double tolerance) {
        return width <= other.width + tolerance &&
               depth <= other.depth + tolerance &&
               height <= other.height + tolerance;
    }

    @Override
    public String toString() {
        return String.format("%.0fx%.0fx%.0fmm",
            width * 1000, depth * 1000, height * 1000);
    }
}
