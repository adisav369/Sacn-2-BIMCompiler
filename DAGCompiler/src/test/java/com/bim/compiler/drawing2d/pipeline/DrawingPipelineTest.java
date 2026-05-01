/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d.pipeline;

import com.bim.compiler.drawing2d.model.DrawingScope;
import com.bim.compiler.drawing2d.model.PageEntry;
import com.bim.compiler.drawing2d.model.PageManifest;
import com.bim.compiler.drawing2d.pipeline.DrawingContext.PipelineResult;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: ScopeStage + PageManifest against real building DBs.
 *
 * Verifies that auto-detected scope matches the hand-crafted manifests:
 *   SH -> 1 storey, ARC only, 9+ pages
 *   DX -> 2 storeys, ARC+PLMB, 12+ pages
 *
 * Implementing 2D_ARCHITECTURAL_LAYOUT.md §2 — pipeline stages.
 */
class DrawingPipelineTest {

    private static Path INPUT_DIR;
    private static Path META_DB;
    private static Path TEMPLATE;

    @BeforeAll
    static void resolvePaths() {
        // Try multiple resolution strategies for finding 2D_Layout/input
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path candidate = cwd.resolve("../2D_Layout/input").normalize();
        if (!Files.exists(candidate)) {
            candidate = cwd.getParent().resolve("2D_Layout/input");
        }
        if (!Files.exists(candidate)) {
            candidate = Path.of("/home/red1/bim-compiler/2D_Layout/input");
        }
        INPUT_DIR = candidate;
        META_DB = INPUT_DIR.resolve("2D.db");
        TEMPLATE = INPUT_DIR.getParent().resolve("drawing_template.json");
    }

    private void assumeSH() {
        Assumptions.assumeTrue(Files.exists(INPUT_DIR.resolve("SH_extracted.db")),
            "SH_extracted.db not found at " + INPUT_DIR);
    }

    private void assumeDX() {
        Assumptions.assumeTrue(Files.exists(INPUT_DIR.resolve("DX_extracted.db")),
            "DX_extracted.db not found at " + INPUT_DIR);
    }

    // ── SampleHouse: 1 storey, ARC-only ──────────────────────────

    @Test
    void sh_scope_detects_one_storey() throws Exception {
        assumeSH();

        DrawingContext ctx = buildContext("SH_extracted.db");
        new ScopeStage().execute(ctx);
        DrawingScope scope = ctx.scope();

        assertEquals(1, scope.storeyCount(),
            "SH has 1 habitable storey (Ground Floor)");
        assertEquals("Ground Floor", scope.storeys().get(0).name());
        assertEquals("GF", scope.storeys().get(0).code());
        assertTrue(scope.storeys().get(0).floorZ() < 0.01,
            "Ground Floor Z ~ 0.0m");
        assertTrue(scope.hasMeshData(), "SH has mesh data");
        assertTrue(scope.hasRoofElements(), "SH has roof elements");
        assertTrue(scope.disciplines().contains("ARC"));
    }

    @Test
    void sh_manifest_matches_expected_pages() throws Exception {
        assumeSH();

        DrawingContext ctx = buildContext("SH_extracted.db");
        new ScopeStage().execute(ctx);
        PageManifest manifest = ctx.manifest();

        assertTrue(manifest.size() >= 9,
            "SH manifest should have >= 9 pages, got " + manifest.size());

        assertPageExists(manifest, "FLOOR_PLAN", "FLOOR PLAN");
        assertPageExists(manifest, "FRONT_ELEV", "FRONT ELEVATION");
        assertPageExists(manifest, "REAR_ELEV",  "REAR ELEVATION");
        assertPageExists(manifest, "LEFT_ELEV",  "LEFT ELEVATION");
        assertPageExists(manifest, "RIGHT_ELEV", "RIGHT ELEVATION");
        assertPageExists(manifest, "ROOF_PLAN",  "ROOF PLAN");

        PageEntry floor = findPage(manifest, "FLOOR_PLAN");
        assertEquals("FLOOR PLAN", floor.title(),
            "Single-storey title should not have storey prefix");
    }

    // ── Duplex: 2 storeys, ARC+PLMB ─────────────────────────────

    @Test
    void dx_scope_detects_two_storeys_and_plumbing() throws Exception {
        assumeDX();

        DrawingContext ctx = buildContext("DX_extracted.db");
        new ScopeStage().execute(ctx);
        DrawingScope scope = ctx.scope();

        assertEquals(2, scope.storeyCount(),
            "DX has 2 habitable storeys (Level 1, Level 2)");
        assertEquals("GF", scope.storeys().get(0).code());
        assertEquals("FF", scope.storeys().get(1).code());

        assertTrue(scope.disciplines().contains("ARC"), "DX has ARC");
        assertTrue(scope.disciplines().contains("PLMB"), "DX has PLMB");
        assertTrue(scope.hasMeshData(), "DX has mesh data");
    }

    @Test
    void dx_manifest_has_multistorey_pages() throws Exception {
        assumeDX();

        DrawingContext ctx = buildContext("DX_extracted.db");
        new ScopeStage().execute(ctx);
        PageManifest manifest = ctx.manifest();

        assertTrue(manifest.size() >= 12,
            "DX manifest should have >= 12 pages, got " + manifest.size());

        List<PageEntry> floors = manifest.pages().stream()
            .filter(p -> "FLOOR_PLAN".equals(p.drawingCode()))
            .toList();
        assertEquals(2, floors.size(), "DX should have 2 floor plans");
        assertEquals("GROUND FL FLOOR PLAN", floors.get(0).title());
        assertEquals("FIRST FL FLOOR PLAN",  floors.get(1).title());

        List<PageEntry> plumbing = manifest.pages().stream()
            .filter(p -> "PLUMBING_PLAN".equals(p.drawingCode()))
            .toList();
        assertEquals(2, plumbing.size(), "DX should have 2 plumbing plans");
        assertTrue(plumbing.get(0).title().contains("PLUMBING PLAN"));
    }

    @Test
    void dx_pipeline_produces_forensic_log() throws Exception {
        assumeDX();

        PipelineResult r = runPipeline("DX_extracted.db");

        assertTrue(r.logLines().stream()
            .anyMatch(l -> l.startsWith("§SCOPE storey=")),
            "Log must contain §SCOPE storey lines");
        assertTrue(r.logLines().stream()
            .anyMatch(l -> l.startsWith("§SCOPE manifest:")),
            "Log must contain §SCOPE manifest line");
        assertTrue(r.logLines().stream()
            .anyMatch(l -> l.contains("PIPELINE") && l.contains("complete")),
            "Log must contain pipeline complete line");
    }

    // ── Helpers ──────────────────────────────────────────────────

    private PipelineResult runPipeline(String dbName) {
        return DrawingPipeline.run(
            INPUT_DIR.resolve(dbName), META_DB, TEMPLATE);
    }

    private DrawingContext buildContext(String dbName) {
        return new DrawingContext(
            INPUT_DIR.resolve(dbName), META_DB,
            TEMPLATE, "JKR_Malaysian");
    }

    private void assertPageExists(PageManifest m, String code, String title) {
        assertTrue(m.pages().stream()
            .anyMatch(p -> code.equals(p.drawingCode())
                && title.equals(p.title())),
            "Manifest should contain " + code + " with title '" + title + "'");
    }

    private PageEntry findPage(PageManifest m, String code) {
        return m.pages().stream()
            .filter(p -> code.equals(p.drawingCode()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Page " + code + " not found"));
    }
}
