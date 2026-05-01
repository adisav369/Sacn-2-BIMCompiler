/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Building classification for unit structure.
 * Phase 46: Multi-unit residential support.
 */
public enum BuildingType {
    /**
     * Single dwelling unit (traditional house, bungalow).
     * All rooms belong to a single implicit unit.
     */
    SINGLE_UNIT,

    /**
     * Multiple dwelling units (duplex, apartment, townhouse row).
     * Rooms grouped into explicit UNIT blocks with party walls.
     */
    MULTI_UNIT;

    public static BuildingType fromKeyword(String keyword) {
        if (keyword == null) return SINGLE_UNIT;
        return switch (keyword.toUpperCase()) {
            case "SINGLE_UNIT", "SINGLE" -> SINGLE_UNIT;
            case "MULTI_UNIT", "MULTI" -> MULTI_UNIT;
            default -> SINGLE_UNIT;
        };
    }
}
