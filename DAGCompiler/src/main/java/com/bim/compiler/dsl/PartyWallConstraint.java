/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Party wall constraint between rooms in different units.
 * Phase 47A: Cross-unit solver for multi-unit residential.
 *
 * A party wall constraint specifies that two rooms must share a boundary
 * and that boundary must be a fire-rated party wall (250mm, FRL 60/60/60).
 *
 * DSL Syntax (in room block):
 *   adjacent_unit: other_room_name
 *
 * Example:
 *   LIVING "living_b" size:4x5m {
 *       adjacent_unit: living_a    // Forces party wall between living_a and living_b
 *   }
 *
 * Solver enforces:
 * 1. Rooms share a non-zero length boundary
 * 2. Wall thickness is 250mm (party wall)
 * 3. No doors/windows through party wall segment
 * 4. Fire rating applied (FRL 60/60/60 per UBBL)
 *
 * Witness:
 *   PARTY_WALLS_VALID - proves all constraints are satisfied
 */
public record PartyWallConstraint(
    String thisRoom,       // Room declaring the constraint (e.g., "living_b")
    String thisUnit,       // Unit containing thisRoom (e.g., "B")
    String otherRoom,      // Room in other unit (e.g., "living_a")
    String otherUnit,      // Unit containing otherRoom (e.g., "A")
    int storeyLevel        // Storey level where constraint applies
) {
    /**
     * Simple constructor when units are not yet resolved.
     */
    public PartyWallConstraint(String thisRoom, String otherRoom, int storeyLevel) {
        this(thisRoom, null, otherRoom, null, storeyLevel);
    }

    /**
     * Check if constraint is fully resolved (both units known).
     */
    public boolean isResolved() {
        return thisUnit != null && otherUnit != null;
    }

    /**
     * Create a resolved copy with unit information.
     */
    public PartyWallConstraint withUnits(String thisUnit, String otherUnit) {
        return new PartyWallConstraint(thisRoom, thisUnit, otherRoom, otherUnit, storeyLevel);
    }

    /**
     * Generate a unique key for this constraint (order-independent).
     * Used to detect duplicate constraints (A→B same as B→A).
     */
    public String canonicalKey() {
        String room1, room2;
        if (thisRoom.compareTo(otherRoom) < 0) {
            room1 = thisRoom;
            room2 = otherRoom;
        } else {
            room1 = otherRoom;
            room2 = thisRoom;
        }
        return room1 + "|" + room2 + "@" + storeyLevel;
    }

    @Override
    public String toString() {
        if (isResolved()) {
            return String.format("PartyWall[%s(%s) <-> %s(%s) @L%d]",
                thisRoom, thisUnit, otherRoom, otherUnit, storeyLevel);
        } else {
            return String.format("PartyWall[%s <-> %s @L%d]",
                thisRoom, otherRoom, storeyLevel);
        }
    }
}
