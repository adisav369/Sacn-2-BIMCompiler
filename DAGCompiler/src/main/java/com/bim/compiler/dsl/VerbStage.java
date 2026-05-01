/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import com.bim.orm.BIMLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Pipeline stage: BIM COBOL verb execution (Step 7).
 *
 * <p>Hybrid design (P108 recommendation B): always fires for every building.
 * <ol>
 *   <li>Logs BOM verb breakdown from CompileStage (PLACE/CLUSTER/TILE/ROUTE/FRAME/SPRAY)</li>
 *   <li>If a {@code scripts/<building_id>.bimcobol} script exists, parses and executes
 *       script verbs as overrides/supplements via SPI {@link VerbExecutor}</li>
 * </ol>
 *
 * <p>SPI pattern breaks circular dependency: DAGCompiler defines
 * {@link VerbExecutor}, BIM_COBOL provides the implementation
 * ({@code BimCobolVerbExecutor}). When BIM_COBOL is on the classpath,
 * verbs execute and results persist to W_Verb_Node. When absent,
 * VerbStage falls back to log-only mode.
 */
// Implementing BBC.md §3.5 — Witness: W-VERB-1
public class VerbStage implements CompilerStage {

    static final String SCRIPT_DIR = "scripts";

    @Override
    public String name() { return "VERB STAGE (BIM COBOL)"; }

    @Override
    public boolean shouldSkip(CompilationContext ctx) {
        // Hybrid: never skip — always logs BOM verb breakdown
        return false;
    }

    @Override
    public void execute(CompilationContext ctx) throws Exception {
        // ── Part 1: BOM verb breakdown (from CompileStage) ──
        String breakdown = ctx.verbBreakdown();
        if (breakdown != null) {
            BIMLogger.info("VERB", "BOM verbs: {}", breakdown);
        } else {
            BIMLogger.fine("VERB", "No BOM verb breakdown available");
        }

        // ── Part 2: Script verbs (override/supplement) or default recipe ──
        Path script = scriptPath(ctx.buildingId());
        List<String> verbLines;

        if (script.toFile().exists()) {
            BIMLogger.info("VERB", "Found script: {}", script);
            verbLines = parseVerbLines(script);
        } else {
            // Implementing BBC.md §6 — VerbStage default recipe from pipeline context
            // Implementing P108 recommendation (B) — hybrid: BOM-driven default, script override
            BIMLogger.fine("VERB", "No .bimcobol script — generating default recipe");
            verbLines = defaultRecipe(ctx);
            if (verbLines.isEmpty()) {
                BIMLogger.fine("VERB", "Default recipe empty — skipping");
                return;
            }
            BIMLogger.info("VERB", "Default recipe: {}", verbLines);
        }

        BIMLogger.info("VERB", "{} verb line(s):", verbLines.size());
        for (String line : verbLines) {
            BIMLogger.fine("VERB", "  > {}", line);
        }

        if (verbLines.isEmpty()) {
            BIMLogger.warn("VERB", "Script exists but no verb lines");
            return;
        }

        // SPI: discover VerbExecutor implementation
        VerbExecutor executor = ServiceLoader.load(VerbExecutor.class)
                .findFirst().orElse(null);

        if (executor == null) {
            BIMLogger.info("VERB", "No VerbExecutor on classpath — log-only mode");
            return;
        }

        // Execute verbs: BOM.db read-only, output.db read-write
        String outputDbPath = ctx.entry().outputDbPath();
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
             Connection outputConn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath)) {
            outputConn.setAutoCommit(false);

            VerbExecutor.ExecutionReport report =
                    executor.execute(bomConn, outputConn, ctx.buildingId(), verbLines);

            for (String detail : report.details()) {
                BIMLogger.fine("VERB", "  {}", detail);
            }

            outputConn.commit();

            BIMLogger.info("VERB", "{} — {} pass, {} fail, {} W_Verb_Node rows",
                    report.allPass() ? "PASS" : "FAIL",
                    report.passCount(), report.failCount(), report.totalNodes());

            if (!report.allPass()) {
                BIMLogger.warn("VERB", "{} verb(s) failed — DocStatus=VO",
                        report.failCount());
            }
        }
    }

    // ── default recipe ──

    /**
     * Generate minimal verb lines when no .bimcobol script exists.
     * Always: CHECK BOM + CHECK PLACEMENT. If routes exist: CHECK CLASH.
     */
    private List<String> defaultRecipe(CompilationContext ctx) {
        List<String> lines = new ArrayList<>();

        String rootBom = findRootBom(ctx.entry());
        if (rootBom != null) {
            lines.add("CHECK BOM " + rootBom);
        }

        lines.add("CHECK PLACEMENT");

        if (ctx.routeReport() != null && ctx.routeReport().routeCount() > 0) {
            lines.add("CHECK CLASH");
        }

        return lines;
    }

    /**
     * Find the root BUILDING BOM for a building entry.
     * Same logic as BomDropper.findBuildingBom — root = M_BOM not referenced as a child.
     */
    private String findRootBom(BuildingRegistry.BuildingEntry entry) {
        String dbPath = System.getProperty("bom.db");
        if (dbPath == null) return null;
        String sql = "SELECT Value FROM m_bom "
                + "WHERE Value NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1) "
                + "AND m_product_category_id = (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = ?) "
                + "AND doc_sub_type = ? "
                + "AND is_active = 1 ORDER BY seq_no LIMIT 1";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.mProductCategoryId());
            ps.setString(2, entry.docSubType());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    // ── public helpers ──

    public static Path scriptPath(String buildingId) {
        return Path.of(SCRIPT_DIR, buildingId + ".bimcobol");
    }

    public static List<String> parseVerbLines(Path scriptPath) throws IOException {
        return Files.readAllLines(scriptPath).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .collect(Collectors.toList());
    }

    public static List<String> parseVerbLines(String scriptContent) {
        return scriptContent.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .collect(Collectors.toList());
    }
}
