/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Type classification for structural/framing components.
 *
 * <p>Used in LOD400 clearance calculations to determine
 * required spacing between adjacent components.
 *
 * <p>Theory: AS 1684 timber framing, platform construction
 */
public enum ComponentType {
    /** Vertical framing member (stud, post, column) */
    STUD,

    /** Horizontal framing member at floor/ceiling level (plate, rail) */
    PLATE,

    /** Horizontal blocking between studs (nogging, blocking, dwang) */
    NOGGING,

    /** Horizontal spanning member (beam, lintel, header) */
    BEAM,

    /** Diagonal bracing member */
    BRACE,

    /** Floor/ceiling framing member (joist, rafter) */
    JOIST,

    /** External cladding/sheathing */
    CLADDING,

    /** Internal lining (plasterboard, paneling) */
    LINING,

    /** Insulation between framing */
    INSULATION
}
