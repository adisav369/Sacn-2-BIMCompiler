/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.system;

/**
 * Phase 35: Types of MEP systems in a building.
 */
public enum SystemType {
    PLUMBING_WASTE,    // Drainage to septic/sewer
    PLUMBING_VENT,     // Vent to atmosphere
    PLUMBING_SUPPLY,   // Water supply (hot/cold)
    ELECTRICAL,        // Power distribution
    HVAC_SUPPLY,       // Conditioned air supply
    HVAC_RETURN,       // Return air
    FIRE_SUPPRESSION   // Sprinkler system
}
