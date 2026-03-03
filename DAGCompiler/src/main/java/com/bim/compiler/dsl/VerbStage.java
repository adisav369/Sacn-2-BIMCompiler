package com.bim.compiler.dsl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Pipeline stage: BIM COBOL verb execution (Step 6).
 *
 * <p>Positioned after WriteStage, before DigestStage.
 * Looks for {@code scripts/<building_id>.bimcobol}. If found, parses verb lines
 * and delegates execution to a {@link VerbExecutor} discovered via SPI.
 *
 * <p>SPI pattern breaks circular dependency: DAGCompiler defines
 * {@link VerbExecutor}, BIM_COBOL provides the implementation
 * ({@code BimCobolVerbExecutor}). When BIM_COBOL is on the classpath,
 * verbs execute and results persist to PP_Order_Node. When absent,
 * VerbStage falls back to log-only mode.
 */
public class VerbStage implements CompilerStage {

    static final String SCRIPT_DIR = "scripts";

    @Override
    public String name() { return "VERB STAGE (BIM COBOL)"; }

    @Override
    public boolean shouldSkip(CompilationContext ctx) {
        return !scriptPath(ctx.buildingId()).toFile().exists();
    }

    @Override
    public void execute(CompilationContext ctx) throws Exception {
        Path script = scriptPath(ctx.buildingId());
        System.out.printf("[VERB] Found script: %s%n", script);

        List<String> verbLines = parseVerbLines(script);
        System.out.printf("[VERB] %d verb line(s):%n", verbLines.size());
        for (String line : verbLines) {
            System.out.printf("[VERB]   > %s%n", line);
        }

        if (verbLines.isEmpty()) {
            System.out.println("[VERB] WARN: script exists but no verb lines");
            return;
        }

        // SPI: discover VerbExecutor implementation
        VerbExecutor executor = ServiceLoader.load(VerbExecutor.class)
                .findFirst().orElse(null);

        if (executor == null) {
            System.out.println("[VERB] No VerbExecutor on classpath — log-only mode");
            return;
        }

        // Execute verbs: BOM.db read-only, output.db read-write
        String outputDbPath = ctx.entry().outputDbPath();
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
             Connection outputConn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath)) {
            outputConn.setAutoCommit(false);

            VerbExecutor.ExecutionReport report =
                    executor.execute(bomConn, outputConn, ctx.buildingId(), verbLines);

            for (String detail : report.details()) {
                System.out.printf("[VERB]   %s%n", detail);
            }

            outputConn.commit();

            System.out.printf("[VERB] %s — %d pass, %d fail, %d PP_Order_Node rows%n",
                    report.allPass() ? "PASS" : "FAIL",
                    report.passCount(), report.failCount(), report.totalNodes());

            if (!report.allPass()) {
                System.err.printf("[VERB] WARNING: %d verb(s) failed — DocStatus=VO%n",
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
