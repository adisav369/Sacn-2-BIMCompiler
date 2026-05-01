/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.system;

/**
 * Phase 35: Role of a node in an MEP system graph.
 */
public enum NodeRole {
    SOURCE,       // Origin: DB panel, water main, MH, AHU
    DISTRIBUTION, // Mid-path: riser, trunk, circuit
    TERMINAL,     // End-point: fixture, outlet, diffuser
    CONNECTOR     // Junction: fitting, tee, elbow
}
