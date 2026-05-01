/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Door definition from DSL.
 * Example: DOOR south to:corridor
 *
 * @param direction which wall the door is on
 * @param connectsTo name of room this door connects to
 */
public record DoorDefinition(
    Direction direction,
    String connectsTo
) {
    /**
     * Parse from DSL tokens.
     * Format: "south to:corridor" or "south to:exterior"
     */
    public static DoorDefinition parse(String directionStr, String connectsTo) {
        Direction dir = Direction.fromKeyword(directionStr);
        return new DoorDefinition(dir, connectsTo);
    }
}
