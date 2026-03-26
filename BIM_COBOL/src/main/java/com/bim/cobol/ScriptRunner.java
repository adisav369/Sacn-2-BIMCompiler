package com.bim.cobol;

import com.bim.orm.BIMLogger;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads {@code .bimcobol} text and dispatches each line to a VerbRegistry.
 *
 * <p>Line processing:
 * <ul>
 *   <li>Lines starting with {@code --} are comments (ignored)</li>
 *   <li>Blank / whitespace-only lines are skipped</li>
 *   <li>Inline comments: everything after {@code --} is stripped</li>
 *   <li>Remaining lines are dispatched to the registry</li>
 * </ul>
 *
 * <p>Returns a {@link ScriptReport} summarising all verb results.
 */
public class ScriptRunner {

    private final VerbRegistry registry;

    public ScriptRunner(VerbRegistry registry) {
        this.registry = registry;
    }

    /**
     * Execute a multi-line BIM COBOL script.
     *
     * @param ctx    verb context (connections)
     * @param script the full script text (newline-separated lines)
     * @return report with per-line results and summary counts
     */
    public ScriptReport run(VerbContext ctx, String script) {
        if (script == null || script.isBlank())
            return new ScriptReport(List.of(), 0, 0, 0);

        List<VerbResult<?>> results = new ArrayList<>();
        int totalLines = 0;

        for (String raw : script.split("\n")) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;

            totalLines++;
            VerbResult<?> r = registry.dispatch(ctx, line);
            if (r.pass()) {
                BIMLogger.fine("VERB", "{} → {}", r.verb(), r.summary());
            } else {
                BIMLogger.warn("VERB", "{} → FAIL: {}", r.verb(), r.summary());
            }
            results.add(r);
        }

        int pass = 0, fail = 0;
        for (VerbResult<?> r : results) {
            if (r.pass()) pass++;
            else fail++;
        }

        return new ScriptReport(
                Collections.unmodifiableList(results),
                pass, fail, totalLines);
    }

    /** Strip inline comment: everything from first {@code --} onward. */
    private static String stripComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    /**
     * Summary of a script execution.
     *
     * @param results   per-line verb results (in execution order)
     * @param passCount number of passing results
     * @param failCount number of failing results
     * @param totalLines number of non-blank, non-comment lines processed
     */
    public record ScriptReport(
            List<VerbResult<?>> results,
            int passCount,
            int failCount,
            int totalLines
    ) {
        private static final Gson GSON = new Gson();

        /** True if every verb result passed. */
        public boolean allPass() {
            return failCount == 0 && passCount > 0;
        }

        /** Serialize the full report to JSON. */
        public String toJson() {
            return GSON.toJson(this);
        }
    }
}
