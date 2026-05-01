/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.geometry;

/**
 * Immutable 2D point for profile definitions.
 * Used for extrusion profiles, floor plans, and XY-plane operations.
 *
 * @param x X coordinate in meters
 * @param y Y coordinate in meters
 */
public record Point2D(double x, double y) {

    /**
     * Distance to another point.
     */
    public double distanceTo(Point2D other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Translate by offset.
     */
    public Point2D translate(double dx, double dy) {
        return new Point2D(x + dx, y + dy);
    }

    /**
     * Rotate around origin by angle (radians, CCW).
     */
    public Point2D rotate(double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        return new Point2D(
            x * cos - y * sin,
            x * sin + y * cos
        );
    }

    /**
     * Convert to 3D point at given Z level.
     */
    public Point3D toPoint3D(double z) {
        return new Point3D(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f)", x, y);
    }
}
