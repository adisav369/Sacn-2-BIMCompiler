/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d.pipeline;

import com.bim.compiler.drawing2d.pipeline.DrawingContext.PipelineResult;

import java.nio.file.Path;
import java.util.List;

/**
 * 2D Drawing Pipeline — stage runner loop.
 *
 * Mirrors {@link com.bim.compiler.dsl.CompilationPipeline} —
 * sequential execution of typed stages with skip logic and
 * forensic logging.
 *
 * Current stages (Phase 1 — data-only, no rendering):
 *   1. ScopeStage  — detect floors, disciplines, generate manifest
 *
 * Future stages (Phase 2-4):
 *   2. LoadStage         — query DB for elements per storey
 *   3. GridStage         — derive grids + dimensions
 *   4. LevelStage        — detect elevation levels
 *   5. CompositionStage  — read 2d_page_composition per page
 *   6. RenderStage       — dispatch to page renderers
 *   7. VerifyStage       — conformity checks + log summary
 */
public class DrawingPipeline {

    private static final List<DrawingStage> STAGES = List.of(
        new ScopeStage()
        // Future: LoadStage, GridStage, LevelStage, CompositionStage,
        //         RenderStage, VerifyStage
    );

    /**
     * Run the drawing pipeline for one building.
     *
     * @param dbPath       path to compiled building DB (e.g. SH_extracted.db)
     * @param metaDbPath   path to 2D.db (drawing metadata / labels / styles)
     * @param templatePath path to drawing_template.json
     * @param profileId    drawing profile ("JKR_Malaysian")
     * @return pipeline result with verdicts and log
     */
    public static PipelineResult run(Path dbPath, Path metaDbPath,
                                     Path templatePath, String profileId) {
        DrawingContext ctx = new DrawingContext(dbPath, metaDbPath,
                                               templatePath, profileId);

        ctx.log("PIPELINE", "start db=" + dbPath.getFileName()
            + " meta=" + metaDbPath.getFileName()
            + " profile=" + profileId);

        for (DrawingStage stage : STAGES) {
            ctx.log("PIPELINE", stage.name() + " starting");
            if (stage.shouldSkip(ctx)) {
                ctx.log("PIPELINE", "[SKIP] " + stage.name());
                continue;
            }
            try {
                stage.execute(ctx);
                ctx.log("PIPELINE", stage.name() + " done");
            } catch (Exception e) {
                ctx.log("PIPELINE", "[ERROR] " + stage.name()
                    + ": " + e.getMessage());
                throw new RuntimeException("Stage " + stage.name()
                    + " failed", e);
            }
        }

        ctx.log("PIPELINE", "complete");
        return ctx.toResult();
    }

    /** Convenience: run with default JKR_Malaysian profile. */
    public static PipelineResult run(Path dbPath, Path metaDbPath,
                                     Path templatePath) {
        return run(dbPath, metaDbPath, templatePath, "JKR_Malaysian");
    }
}
