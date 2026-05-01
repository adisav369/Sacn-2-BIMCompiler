/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.bom.WallOpeningAssembler;
import java.sql.Connection;

/**
 * Factory — creates IAssembler instances from bom-package concrete classes.
 *
 * The contract package is the approved bridge between the dsl layer and
 * the bom package. This keeps the dsl layer free of direct bom dependencies.
 * [EXTRACTED: ARCHITECTURE.md §Contracts — A2 enforcement boundary]
 */
public final class AssemblerFactory {

    private AssemblerFactory() {}

    /** Create a wall-opening linker assembler for the given target connection. */
    public static IAssembler wallOpeningAssembler(Connection conn) {
        return new WallOpeningAssembler(conn);
    }
}
