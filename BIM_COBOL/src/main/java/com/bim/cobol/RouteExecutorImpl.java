package com.bim.cobol;

import com.bim.cobol.geometry.*;
import com.bim.cobol.geometry.DisciplineRouteBuilder.DisciplineRouteResult;
import com.bim.compiler.dsl.RouteExecutor;
import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SPI implementation of {@link RouteExecutor} — fires RouteDocEvent during compilation.
 *
 * <p>Bridges between DAGCompiler (which defines the SPI) and BIM_COBOL (which
 * has the RouteDocEvent, CrawlRouter, and DisciplineRouteBuilder classes).
 *
 * <p>// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.1 Implementation — Witness: W-ROUTE-STAGE-1
 */
public class RouteExecutorImpl implements RouteExecutor {

    private static final String TAG = "ROUTE";

    @Override
    public RouteReport executeRoutes(Connection compileDb, List<String> disciplines) throws Exception {
        return executeRoutes(compileDb, disciplines, null);
    }

    @Override
    public RouteReport executeRoutes(Connection compileDb, List<String> disciplines,
                                      Map<String, double[]> storeyZBands) throws Exception {
        SqlBuildingGeometry geo = new SqlBuildingGeometry(compileDb, storeyZBands);
        List<DisciplineRouteResult> results = RouteDocEvent.fireAll(disciplines, geo);

        List<EdgeRow> edges = new ArrayList<>();
        List<NodeRow> nodes = new ArrayList<>();

        for (DisciplineRouteResult drr : results) {
            String disc = drr.discipline();
            CrawlRouter.RouteResult rr = drr.routeResult();

            // Edges
            for (CrawlRouter.ConnectionEdge edge : rr.edges()) {
                edges.add(new EdgeRow(
                        disc,
                        edge.fromIndex(), edge.toIndex(),
                        formatXyz(edge.fromPos()), formatXyz(edge.toPos()),
                        edge.edgeType()));
            }

            // Nodes: segments
            int idx = 0;
            for (CrawlRouter.SegmentSpec seg : rr.segments()) {
                nodes.add(new NodeRow(
                        disc, idx++, "SEGMENT",
                        formatXyz(seg.start()),
                        seg.diameterMm(), seg.product(), seg.lengthMm()));
            }

            // Nodes: fittings
            for (CrawlRouter.FittingSpec fit : rr.fittings()) {
                nodes.add(new NodeRow(
                        disc, idx++, fit.type(),
                        formatXyz(fit.position()),
                        fit.inletDiameterMm(), null, 0));
            }
        }

        int totalSegs = results.stream().mapToInt(DisciplineRouteResult::segmentCount).sum();
        int totalFits = results.stream().mapToInt(DisciplineRouteResult::fittingCount).sum();

        BIMLogger.info(TAG, "RouteExecutorImpl: {} routes, {} edges, {} nodes",
                results.size(), edges.size(), nodes.size());

        return new RouteReport(edges, nodes, results.size(), totalSegs, totalFits);
    }

    private static String formatXyz(Point3D p) {
        return String.format("%.1f,%.1f,%.1f", p.x(), p.y(), p.z());
    }
}
