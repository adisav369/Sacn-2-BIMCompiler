/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Reference to a shared junction point.
 *
 * <p>A JunctionRef is how an element declares its connection to a shared point
 * without owning that point. Multiple elements can reference the same junction,
 * enabling proper boundary sharing.
 *
 * <p>Maps to IfcRelConnectsPathElements relationship.
 * <p>Pattern: Flyweight reference (GoF)
 *
 * @param junctionId Registry key for the shared junction (from SharedElementRegistry)
 * @param role How this element connects to the junction
 */
public record JunctionRef(
    String junctionId,
    ConnectionRole role
) {
    /**
     * Creates a reference to a junction where this element starts.
     */
    public static JunctionRef startAt(String junctionId) {
        return new JunctionRef(junctionId, ConnectionRole.START);
    }

    /**
     * Creates a reference to a junction where this element ends.
     */
    public static JunctionRef endAt(String junctionId) {
        return new JunctionRef(junctionId, ConnectionRole.END);
    }

    /**
     * Creates a reference to a junction this element passes through.
     */
    public static JunctionRef passThrough(String junctionId) {
        return new JunctionRef(junctionId, ConnectionRole.MID);
    }

    /**
     * Creates a reference to a junction where this element terminates.
     */
    public static JunctionRef terminateAt(String junctionId) {
        return new JunctionRef(junctionId, ConnectionRole.TERMINATES);
    }
}
