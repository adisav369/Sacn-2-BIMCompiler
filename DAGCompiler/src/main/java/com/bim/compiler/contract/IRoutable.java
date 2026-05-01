/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import java.util.List;

/**
 * Contract for linear MEP elements that follow paths.
 * Defines routing constraints and path geometry.
 *
 * <p>Maps to: IfcFlowSegment, IfcCableCarrierSegment, IfcDuctSegment, IfcPipeSegment
 *
 * <p>Routable Elements:
 * <ul>
 *   <li>Pipes - water supply, waste, vent</li>
 *   <li>Ducts - HVAC supply, return, exhaust</li>
 *   <li>Conduits - electrical, data</li>
 *   <li>Cable trays - open routing</li>
 * </ul>
 *
 * <p>Routing follows these priorities:
 * <ol>
 *   <li>Avoid structural elements</li>
 *   <li>Follow ceiling/floor voids</li>
 *   <li>Minimize bends</li>
 *   <li>Maintain clearances</li>
 *   <li>Allow access for maintenance</li>
 * </ol>
 */
public interface IRoutable extends IConnectable {

    /**
     * Nominal size of the routable element.
     * Diameter for pipes/conduits, width×height for ducts.
     *
     * @return cross-section dimensions in meters
     */
    CrossSection crossSection();

    /**
     * Path waypoints from start to end.
     * Each segment between waypoints is straight.
     *
     * @return ordered list of waypoints
     */
    List<Waypoint> routePath();

    /**
     * Minimum bend radius for this element.
     * Based on material and size.
     *
     * @return minimum radius in meters
     */
    double minBendRadius();

    /**
     * Maximum segment length before support required.
     * From manufacturer specs or code requirements.
     *
     * @return max length in meters
     */
    double maxUnsupportedLength();

    /**
     * Required clearance to other elements.
     *
     * @return clearance in meters
     */
    default double requiredClearance() {
        return 0.05; // 50mm default
    }

    /**
     * Whether this element can penetrate structural elements.
     * If true, sleeves/openings will be generated.
     */
    default boolean canPenetrateStructure() {
        return true;
    }

    /**
     * Routing priority (lower = route first).
     * Large ducts route first, small conduits last.
     *
     * @return priority value (1-100)
     */
    default int routingPriority() {
        return 50;
    }

    /**
     * Cross-section shape and dimensions.
     */
    sealed interface CrossSection permits CircularSection, RectangularSection {
        double area(); // Cross-sectional area in m²
    }

    /**
     * Circular cross-section (pipes, conduits).
     */
    record CircularSection(double diameter) implements CrossSection {
        @Override
        public double area() {
            return Math.PI * diameter * diameter / 4;
        }
    }

    /**
     * Rectangular cross-section (ducts).
     */
    record RectangularSection(double width, double height) implements CrossSection {
        @Override
        public double area() {
            return width * height;
        }
    }

    /**
     * Waypoint along a route path.
     */
    record Waypoint(
        double x, double y, double z,   // World coordinates
        WaypointType type,              // Type of waypoint
        String annotation               // Optional note ("through wall", "above ceiling")
    ) {
        public Waypoint(double x, double y, double z) {
            this(x, y, z, WaypointType.STRAIGHT, null);
        }
    }

    /**
     * Types of waypoints.
     */
    enum WaypointType {
        START,          // Beginning of route
        END,            // End of route
        STRAIGHT,       // Continue straight
        BEND,           // Direction change
        TEE,            // Branch point
        REDUCER,        // Size change
        PENETRATION,    // Through wall/floor
        SUPPORT,        // Hanger/support point
        VALVE,          // Valve/damper location
        ACCESS          // Access point for maintenance
    }
}
