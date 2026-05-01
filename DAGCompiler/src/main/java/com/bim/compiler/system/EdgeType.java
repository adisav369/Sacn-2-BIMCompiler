/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.system;

/**
 * Phase 35: Type of connection between nodes in an MEP system.
 */
public enum EdgeType {
    FEEDS,              // Electrical: panel -> outlet
    DRAINS_TO,          // Plumbing waste: toilet -> riser -> MH
    VENTS_TO,           // Plumbing vent: trap -> vent stack -> roof
    SUPPLIES,           // HVAC/water: source -> terminal
    RETURNS,            // HVAC: terminal -> return trunk
    CONNECTS_VERTICAL   // Phase 38: Plumbing stack spans storeys (upper -> lower)
}
