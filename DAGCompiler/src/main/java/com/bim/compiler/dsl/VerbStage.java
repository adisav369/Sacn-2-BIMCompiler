package com.bim.compiler.dsl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pipeline stage: BIM COBOL script integration hook.
 *
 * <p>Positioned after WriteStage, before DigestStage (Step 6).
 * Looks for a {@code .bimcobol} script file for the current building:
 * {@code scripts/<building_id>.bimcobol}
 *
 * <p>Behaviour:
 * <ul>
 *   <li>File absent → {@link #shouldSkip(CompilationContext)} returns true (graceful skip).
 *       No error is raised. Buildings without scripts compile normally.</li>
 *   <li>File present → reads the script, parses non-comment lines, and logs them.
 *       Execution integration is wired in the BIM_COBOL module (see VerbStage integration
 *       notes in docs/BIM_COBOL.md §16). For now, finding a valid script is a PASS.</li>
 * </ul>
 *
 * <p>Full execution via BIM_COBOL ScriptRunner is a planned integration step —
 * prerequisite: RelationalResolver deleted, L2 ESLines stable, LocalCoord complete.
 * When that integration is done, the BIM_COBOL module will provide an executor plugin
 * that this stage will delegate to.
 */
public class VerbStage implements CompilerStage {

    /** Base directory for .bimcobol scripts (relative to project root). */
    static final String SCRIPT_DIR = "scripts";

    @Override
    public String name() { return "VERB STAGE (BIM COBOL)"; }

    /**
     * Skip if no .bimcobol script exists for this building.
     * Graceful: missing script means "no BIM COBOL verbs for this building" — valid.
     */
    @Override
    public boolean shouldSkip(CompilationContext ctx) {
        return !scriptPath(ctx.buildingId()).toFile().exists();
    }

    @Override
    public void execute(CompilationContext ctx) throws Exception {
        Path script = scriptPath(ctx.buildingId());
        System.out.printf("[VERB] Found script: %s%n", script);

        List<String> verbLines = parseVerbLines(script);
        System.out.printf("[VERB] %d verb line(s) to execute (BIM COBOL integration pending):%n",
            verbLines.size());
        for (String line : verbLines) {
            System.out.printf("[VERB]   > %s%n", line);
        }

        if (verbLines.isEmpty()) {
            System.out.println("[VERB] WARN: script exists but contains no verb lines — check comments/blanks");
        }

        // Integration point: when BIM_COBOL ScriptRunner is wired in, call it here:
        //   ScriptRunner runner = new ScriptRunner(VerbRegistry.createDefault());
        //   VerbContext verbCtx = VerbContext.ofBom(bomConn);
        //   ScriptReport report = runner.run(verbCtx, content);
        //   if (!report.allPass()) throw new Exception("BIM COBOL script failed: " + report.toJson());
        System.out.println("[VERB] PASS (execution integration pending — script parsed OK)");
    }

    // ── public helpers (used by tests and future integration) ────────────────

    /** Resolve the expected script path for a building. */
    public static Path scriptPath(String buildingId) {
        return Path.of(SCRIPT_DIR, buildingId + ".bimcobol");
    }

    /**
     * Parse a .bimcobol file: return non-comment, non-blank lines.
     * Lines starting with {@code --} (after trimming) are comments.
     */
    public static List<String> parseVerbLines(Path scriptPath) throws IOException {
        return Files.readAllLines(scriptPath).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("--"))
            .collect(Collectors.toList());
    }

    /** Parse verb lines from a raw string (for testing and pipeline integration). */
    public static List<String> parseVerbLines(String scriptContent) {
        return scriptContent.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("--"))
            .collect(Collectors.toList());
    }
}
