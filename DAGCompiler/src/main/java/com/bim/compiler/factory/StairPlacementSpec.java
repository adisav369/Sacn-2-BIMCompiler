/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.factory;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.library.ComponentLibrary.ComponentDefinition;
import java.util.List;
import java.util.ArrayList;

/**
 * Specification for placing a stair assembly from library components.
 *
 * A complete stair assembly consists of:
 * - 1-2 StairFlights (runs)
 * - 0-1 Landings (if 2 runs)
 * - 2 Railings (one per side)
 * - 2-4 Stringers
 *
 * Phase 14A: Library-composed stairs replace parametric generation.
 */
public class StairPlacementSpec extends ElementSpec {

    // Target dimensions
    private final double width;           // Stair width (perpendicular to travel)
    private final double totalRise;       // Total height change (floor-to-floor)
    private final double totalRun;        // Total horizontal travel
    private final int numFlights;         // 1 or 2 (2 = U-turn or L-turn with landing)

    // Placement
    private final Point3D startPosition;  // Bottom of first flight
    private final double rotation;        // Rotation in radians (direction of travel)

    // Library components (resolved during creation)
    private ComponentDefinition flightDef;
    private ComponentDefinition railingDef;
    private List<ComponentDefinition> stringerDefs;

    // Calculated values
    private double flightRise;            // Rise per flight
    private double flightRun;             // Run per flight
    private double landingDepth;          // Landing size (if 2 flights)

    /**
     * Create a stair placement spec.
     *
     * @param width Target stair width (m)
     * @param totalRise Total height change (m), typically storey height
     * @param startPosition Bottom-left corner of stair at floor level
     * @param rotation Direction of travel (0 = +Y, PI/2 = +X)
     */
    public StairPlacementSpec(double width, double totalRise, Point3D startPosition, double rotation) {
        this.width = width;
        this.totalRise = totalRise;
        this.startPosition = startPosition;
        this.rotation = rotation;

        // Default: calculate based on typical stair geometry
        // Rise/run ratio ~0.67 (7" rise / 10.5" run per IRC)
        this.totalRun = totalRise / 0.67;

        // Determine number of flights based on rise
        // Terminal flights have ~2.0-2.2m rise max
        if (totalRise <= 2.5) {
            this.numFlights = 1;
            this.flightRise = totalRise;
            this.flightRun = totalRun;
            this.landingDepth = 0;
        } else {
            this.numFlights = 2;
            this.flightRise = totalRise / 2;
            this.flightRun = totalRun / 2;
            this.landingDepth = width; // Square landing
        }

        this.stringerDefs = new ArrayList<>();
    }

    /**
     * Create with explicit flight count.
     */
    public StairPlacementSpec(double width, double totalRise, double totalRun,
                              int numFlights, Point3D startPosition, double rotation) {
        this.width = width;
        this.totalRise = totalRise;
        this.totalRun = totalRun;
        this.numFlights = numFlights;
        this.startPosition = startPosition;
        this.rotation = rotation;

        if (numFlights == 1) {
            this.flightRise = totalRise;
            this.flightRun = totalRun;
            this.landingDepth = 0;
        } else {
            this.flightRise = totalRise / numFlights;
            this.flightRun = totalRun / numFlights;
            this.landingDepth = width;
        }

        this.stringerDefs = new ArrayList<>();
    }

    // === Library Component Resolution ===

    public void setFlightDefinition(ComponentDefinition def) {
        this.flightDef = def;
    }

    public void setRailingDefinition(ComponentDefinition def) {
        this.railingDef = def;
    }

    public void addStringerDefinition(ComponentDefinition def) {
        this.stringerDefs.add(def);
    }

    public boolean hasLibraryComponents() {
        return flightDef != null;
    }

    // === Getters ===

    public double getWidth() { return width; }
    public double getTotalRise() { return totalRise; }
    public double getTotalRun() { return totalRun; }
    public int getNumFlights() { return numFlights; }
    public Point3D getStartPosition() { return startPosition; }
    public double getRotation() { return rotation; }

    public double getFlightRise() { return flightRise; }
    public double getFlightRun() { return flightRun; }
    public double getLandingDepth() { return landingDepth; }

    public ComponentDefinition getFlightDefinition() { return flightDef; }
    public ComponentDefinition getRailingDefinition() { return railingDef; }
    public List<ComponentDefinition> getStringerDefinitions() { return stringerDefs; }

    // === Position Calculations ===

    /**
     * Get position for flight N (0-indexed).
     */
    public Point3D getFlightPosition(int flightIndex) {
        if (flightIndex == 0) {
            return startPosition;
        } else {
            // Second flight starts after landing
            double dx = 0;
            double dy = flightRun + landingDepth;
            double dz = flightRise;

            // Apply rotation
            double cosR = Math.cos(rotation);
            double sinR = Math.sin(rotation);
            double rotX = dx * cosR - dy * sinR;
            double rotY = dx * sinR + dy * cosR;

            return new Point3D(
                startPosition.x() + rotX,
                startPosition.y() + rotY,
                startPosition.z() + dz
            );
        }
    }

    /**
     * Get rotation for flight N.
     * For U-turn stairs, second flight is rotated 180°.
     */
    public double getFlightRotation(int flightIndex) {
        if (flightIndex == 0 || numFlights == 1) {
            return rotation;
        } else {
            // U-turn: reverse direction
            return rotation + Math.PI;
        }
    }

    /**
     * Get landing position (between flights).
     */
    public Point3D getLandingPosition() {
        if (numFlights <= 1) {
            return null; // No landing for single flight
        }

        double dx = 0;
        double dy = flightRun;
        double dz = flightRise;

        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);
        double rotX = dx * cosR - dy * sinR;
        double rotY = dx * sinR + dy * cosR;

        return new Point3D(
            startPosition.x() + rotX,
            startPosition.y() + rotY,
            startPosition.z() + dz
        );
    }

    /**
     * Get railing positions (left and right of stair).
     */
    public Point3D[] getRailingPositions() {
        double halfWidth = width / 2;

        // Left railing
        double leftDx = -halfWidth;
        double leftDy = 0;

        // Right railing
        double rightDx = halfWidth;
        double rightDy = 0;

        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);

        Point3D left = new Point3D(
            startPosition.x() + leftDx * cosR - leftDy * sinR,
            startPosition.y() + leftDx * sinR + leftDy * cosR,
            startPosition.z()
        );

        Point3D right = new Point3D(
            startPosition.x() + rightDx * cosR - rightDy * sinR,
            startPosition.y() + rightDx * sinR + rightDy * cosR,
            startPosition.z()
        );

        return new Point3D[] { left, right };
    }

    // === Scale Factor ===

    /**
     * Calculate scale factor needed to match target rise.
     * Returns 1.0 if library component matches, otherwise a scale factor.
     */
    public double getRiseScaleFactor() {
        if (flightDef == null) return 1.0;

        double libRise = flightDef.localBounds().maxZ() - flightDef.localBounds().minZ();
        return flightRise / libRise;
    }

    /**
     * Calculate scale factor for width.
     */
    public double getWidthScaleFactor() {
        if (flightDef == null) return 1.0;

        double libWidth = flightDef.localBounds().maxX() - flightDef.localBounds().minX();
        return width / libWidth;
    }

    @Override
    public String toString() {
        return String.format("StairPlacementSpec[%.2fm × %.2fm rise, %d flights, at %s, rot=%.1f°]",
            width, totalRise, numFlights, startPosition,
            Math.toDegrees(rotation));
    }

    // === ElementSpec abstract methods ===

    @Override
    public boolean isParametric() {
        // Parametric if no library flight available
        return !hasLibraryComponents();
    }

    @Override
    public boolean isLibraryBased() {
        // Library-based if we found matching components
        return hasLibraryComponents();
    }
}
