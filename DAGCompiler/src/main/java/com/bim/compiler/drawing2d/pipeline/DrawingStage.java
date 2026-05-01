/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d.pipeline;

/**
 * A single stage in the 2D drawing pipeline.
 *
 * Mirrors {@link com.bim.compiler.dsl.CompilerStage} — stages execute
 * sequentially, each reading from {@link DrawingContext} and writing
 * its results back. The pipeline loop handles logging and skip logic.
 *
 * Every stage must emit §STAGE_NAME log lines proving what it did.
 * Implementing BBC.md §1 R7 — forensic logging.
 */
public interface DrawingStage {

    /** Execute this stage, mutating ctx with results. */
    void execute(DrawingContext ctx) throws Exception;

    /** Human-readable stage name for logging (used as §STAGE_NAME prefix). */
    String name();

    /** Return true to skip this stage for this context. */
    default boolean shouldSkip(DrawingContext ctx) { return false; }
}
