package com.bim.cobol;

import com.bim.compiler.dsl.VerbExecutor;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * SPI implementation: executes BIM COBOL verbs and persists results to PP_Order_Node.
 *
 * <p>Discovered by {@link java.util.ServiceLoader} when BIM_COBOL is on the classpath.
 * Called by {@code VerbStage} in the DAGCompiler pipeline.
 *
 * <p>Flow:
 * <ol>
 *   <li>Create ScriptRunner with all 12 built-in verbs</li>
 *   <li>Execute each line via VerbRegistry dispatch</li>
 *   <li>Persist results to PP_Order_Node + PP_Order_NodeProduct via VerbNodePersister</li>
 *   <li>Return ExecutionReport for pipeline logging</li>
 * </ol>
 */
public class BimCobolVerbExecutor implements VerbExecutor {

    @Override
    public ExecutionReport execute(Connection bomConn, Connection outputConn,
                                   String buildingId, List<String> verbLines)
            throws Exception {

        // 1. Build script text from lines
        String script = String.join("\n", verbLines);

        // 2. Execute via ScriptRunner
        VerbRegistry registry = VerbRegistry.createDefault();
        ScriptRunner runner = new ScriptRunner(registry);
        VerbContext ctx = VerbContext.ofBom(bomConn);
        ScriptRunner.ScriptReport report = runner.run(ctx, script);

        // 3. Persist to PP_Order_Node
        VerbNodePersister persister = new VerbNodePersister(outputConn, buildingId);
        int nodeCount = persister.persist(verbLines, report);

        // 4. Build detail lines for logging
        List<String> details = new ArrayList<>();
        List<VerbResult<?>> results = report.results();
        for (int i = 0; i < results.size(); i++) {
            VerbResult<?> r = results.get(i);
            String line = i < verbLines.size() ? verbLines.get(i) : r.verb();
            details.add(String.format("[%s] %s → %s",
                    r.pass() ? "PASS" : "FAIL", r.verb(), r.summary()));
        }

        return new ExecutionReport(
                report.passCount(), report.failCount(), nodeCount, details);
    }
}
