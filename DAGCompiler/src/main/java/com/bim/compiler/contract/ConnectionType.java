/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Type of physical connection between components.
 *
 * <p>Used in LOD400 fabrication-level models to specify
 * how components are fastened together.
 *
 * <p>Theory: IfcMechanicalFastener, AS 1684 timber framing
 */
public enum ConnectionType {
    /** Nail connection (framing nails, finish nails) */
    NAIL,

    /** Screw connection (wood screws, machine screws) */
    SCREW,

    /** Bolt connection (through bolts, anchor bolts) */
    BOLT,

    /** Welded connection (steel construction) */
    WELD,

    /** Adhesive connection (glue, epoxy) */
    ADHESIVE,

    /** Bracket/hanger connection (joist hangers, angles) */
    BRACKET,

    /** Friction fit (no mechanical fastener) */
    FRICTION
}
