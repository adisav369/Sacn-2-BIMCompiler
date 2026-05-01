/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Role describing how an element connects to a junction point.
 *
 * <p>Maps to IfcRelConnectsPathElements connection geometry semantics.
 * <p>Theory: RCC-8 spatial calculus (Cohn et al. 1997)
 */
public enum ConnectionRole {
    /**
     * Element starts at this junction.
     * The junction is at the element's origin/start point.
     */
    START,

    /**
     * Element ends at this junction.
     * The junction is at the element's terminal/end point.
     */
    END,

    /**
     * Element passes through this junction without terminating.
     * The junction is at an intermediate point along the element.
     */
    MID,

    /**
     * Element terminates at junction as a dead end.
     * No continuation expected beyond this point.
     */
    TERMINATES
}
