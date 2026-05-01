/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.geometry.Point3D;

/**
 * Physical connection point on a component.
 * Specifies where and how fasteners attach.
 *
 * <p>Used in LOD400 fabrication models to ensure proper
 * connection between components.
 *
 * <p>Theory: IfcConnectionPointGeometry, AS 1684 nailing patterns
 *
 * @param location 3D coordinates relative to component origin
 * @param type Type of connection (nail, screw, bolt, etc.)
 * @param fastenerSpec Fastener specification string (e.g., "75mm_FRAMING_NAIL")
 */
public record ConnectionPoint(
    Point3D location,
    ConnectionType type,
    String fastenerSpec
) {
    /**
     * Creates a nail connection point.
     */
    public static ConnectionPoint nail(Point3D location, String spec) {
        return new ConnectionPoint(location, ConnectionType.NAIL, spec);
    }

    /**
     * Creates a screw connection point.
     */
    public static ConnectionPoint screw(Point3D location, String spec) {
        return new ConnectionPoint(location, ConnectionType.SCREW, spec);
    }

    /**
     * Creates a bolt connection point.
     */
    public static ConnectionPoint bolt(Point3D location, String spec) {
        return new ConnectionPoint(location, ConnectionType.BOLT, spec);
    }
}
