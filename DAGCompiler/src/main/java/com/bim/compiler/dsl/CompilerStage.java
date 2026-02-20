package com.bim.compiler.dsl;

/**
 * A single stage in the compilation pipeline.
 *
 * Stages execute sequentially — each reads from {@link CompilationContext}
 * and writes its results back. The pipeline loop handles logging and skip logic.
 */
public interface CompilerStage {

    /** Execute this stage, mutating ctx with results. */
    void execute(CompilationContext ctx) throws Exception;

    /** Human-readable stage name for logging. */
    String name();

    /** Return true to skip this stage for this context. */
    default boolean shouldSkip(CompilationContext ctx) { return false; }
}
