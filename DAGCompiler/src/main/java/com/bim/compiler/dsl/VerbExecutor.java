package com.bim.compiler.dsl;

import java.sql.Connection;
import java.util.List;

/**
 * SPI interface for BIM COBOL verb execution.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} by
 * {@link VerbStage}. If no implementation is found on the classpath,
 * VerbStage falls back to log-only mode (script parsed, not executed).
 *
 * <p>The canonical implementation is {@code com.bim.cobol.BimCobolVerbExecutor}
 * in the BIM_COBOL module. This SPI pattern breaks the circular dependency:
 * DAGCompiler defines the interface, BIM_COBOL provides the implementation.
 *
 * <p>Transaction ownership: the caller (VerbStage) commits after execute().
 * Implementations must NOT call commit() on the output connection.
 */
public interface VerbExecutor {

    /**
     * Execute verb lines against the given connections.
     *
     * @param bomConn    read-only connection to BOM.db (design authority)
     * @param outputConn read-write connection to output.db (W_Verb_Node target)
     * @param buildingId the building being compiled
     * @param verbLines  non-comment, non-blank verb lines (already parsed by VerbStage)
     * @return execution report
     */
    ExecutionReport execute(Connection bomConn, Connection outputConn,
                            String buildingId, List<String> verbLines) throws Exception;

    /**
     * Summary of verb execution.
     *
     * @param passCount  number of verbs that passed
     * @param failCount  number of verbs that failed
     * @param totalNodes number of W_Verb_Node rows created
     * @param details    per-verb one-liner summaries (for logging)
     */
    record ExecutionReport(int passCount, int failCount, int totalNodes,
                           List<String> details) {
        public boolean allPass() { return failCount == 0 && passCount > 0; }
    }
}
