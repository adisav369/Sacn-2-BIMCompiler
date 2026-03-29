package com.bim.compiler.dsl;

import com.bim.orm.BIMLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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

        // ── Part 2: Script verbs (override/supplement) ──
        Path script = scriptPath(ctx.buildingId());
        if (!script.toFile().exists()) {
            BIMLogger.fine("VERB", "No .bimcobol script for {} — BOM verbs only", ctx.buildingId());
            return;
        }

        BIMLogger.info("VERB", "Found script: {}", script);

        List<String> verbLines = parseVerbLines(script);
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
