package com.bim.compiler.dsl;

import java.sql.Connection;
import java.util.List;

/**
 * SPI interface for discipline routing — fires RouteDocEvent during compilation.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} by
 * {@link CompilationPipeline} RouteStage. If no implementation is found on the
 * classpath, RouteStage falls back to log-only mode.
 *
 * <p>The canonical implementation is {@code com.bim.cobol.RouteExecutorImpl}
 * in the BIM_COBOL module. This SPI pattern breaks the circular dependency:
 * DAGCompiler defines the interface, BIM_COBOL provides the implementation.
 *
 * <p>// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.1 Implementation — Witness: W-ROUTE-STAGE-1
 */
public interface RouteExecutor {

    /**
     * Fire discipline routing for a building.
     *
     * @param compileDb    connection to compile DB (has C_OrderLine with ARC positions)
     * @param disciplines  list of discipline codes to route (e.g., [ELEC, SP])
     * @return routing report with edges and nodes for persistence
     */
    RouteReport executeRoutes(Connection compileDb, List<String> disciplines) throws Exception;

    /** Complete routing report — edges and nodes for output.db persistence. */
    record RouteReport(
            List<EdgeRow> edges,
            List<NodeRow> nodes,
            int routeCount,
            int totalSegments,
            int totalFittings
    ) {}

    /** A single system_edges row. */
    record EdgeRow(
            String discipline,
            int fromIndex, int toIndex,
            String fromXyz, String toXyz,
            String edgeType
    ) {}

    /** A single system_nodes row. */
    record NodeRow(
            String discipline,
            int nodeIndex,
            String nodeType,
            String xyz,
            double diameterMm,
            String product,
            double lengthMm
    ) {}
}
