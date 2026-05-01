/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Construction system for IFC class mapping.
 * Phase 50B.1: Determines how walls and structural elements are serialized.
 *
 * FRAMED: Steel/timber framing with cladding → IfcMember + IfcPlate
 * MASONRY: Brick/block solid walls → IfcWall
 */
public enum ConstructionSystem {
    FRAMED,   // Steel/timber frame + cladding (terminal, residential)
    MASONRY;  // Brick/block solid walls (institutional, school)

    /**
     * Parse from DSL keyword.
     */
    public static ConstructionSystem fromKeyword(String keyword) {
        if (keyword == null) return FRAMED;
        return switch (keyword.toUpperCase()) {
            case "MASONRY" -> MASONRY;
            case "FRAMED", "RC" -> FRAMED;
            default -> FRAMED;
        };
    }
}
