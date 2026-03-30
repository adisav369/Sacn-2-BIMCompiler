package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generalised routing engine — replaces PipeRouter + ConduitRouter.
 *
 * <p>Takes a {@link CrawlState} and a list of {@link CrawlOp}s, executes them
 * in sequence, and produces a list of segments, fittings, and CONNECTS_TO edges.
 *
 * <p>CONNECTS_TO edges are the first data that BIMEyes proofs P15/P16/P17 consume.
 * Crawl order IS connection order — each element connects to the previous one.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — CrawlState + CrawlOp model
public final class CrawlRouter {

    private static final String TAG = "CRAWL";

    private CrawlRouter() {}

    /** A pipe/duct/cable segment produced by a crawl. */
    public record SegmentSpec(
            Point3D start, Point3D end,
            double diameterMm, String product, double lengthMm
    ) {}

    /** A fitting produced by a crawl (elbow, tee, reducer, sleeve, fire collar). */
    public record FittingSpec(
            Point3D position, String type,
            double inletDiameterMm, double outletDiameterMm,
            double angleDeg
    ) {}

    /**
     * A CONNECTS_TO edge between two crawl elements.
     * Crawl order IS connection order — P16 WasteGradientProof and P17 SystemConnectedProof
     * consume these edges for BFS connectivity and slope checks.
     *
     * @param fromIndex index in the combined element list (segments + fittings, insertion order)
     * @param toIndex   index of the next element
     * @param fromPos   position of the from-element
     * @param toPos     position of the to-element
     * @param edgeType  SEGMENT_TO_FITTING, FITTING_TO_SEGMENT, SEGMENT_TO_SEGMENT
     */
    public record ConnectionEdge(
            int fromIndex, int toIndex,
            Point3D fromPos, Point3D toPos,
            String edgeType
    ) {}

    /** Complete routing result. */
    public record RouteResult(
            List<SegmentSpec> segments,
            List<FittingSpec> fittings,
            List<ConnectionEdge> edges,
            CrawlState finalState,
            double totalLengthMm
    ) {}

    /**
     * Execute a sequence of crawl ops.
     *
     * @param initial starting crawl state
     * @param ops     ordered list of operations
     * @return routing result with all segments, fittings, edges, and final state
     */
    public static RouteResult execute(CrawlState initial, List<CrawlOp> ops) {
        BIMLogger.fine(TAG, "CrawlRouter: start pos={} dir={} dia={:.0f}mm product={} disc={} ops={}",
                initial.position(), initial.direction(), initial.diameterMm(),
                initial.product(), initial.discipline(), ops.size());
        ResultBuilder builder = new ResultBuilder();
        CrawlState current = initial;
        for (CrawlOp op : ops) {
            current = op.apply(current, builder);
        }
        RouteResult result = builder.build(current);
        BIMLogger.fine(TAG, "CrawlRouter: done segments={} fittings={} edges={} totalLen={:.0f}mm",
                result.segments().size(), result.fittings().size(),
                result.edges().size(), result.totalLengthMm());
        // Implementing EYES_SRS §4.7 — GEO-ROUTE proof coverage for MEP segments
        BIMLogger.geo("ROUTE", "DONE {}: {} segments, {} fittings, {} edges, total={:.0f}mm",
                initial.product(), result.segments().size(), result.fittings().size(),
                result.edges().size(), result.totalLengthMm());
        return result;
    }

    /** Accumulator for segments, fittings, and CONNECTS_TO edges during crawl execution. */
    public static final class ResultBuilder {
        private final List<SegmentSpec> segments = new ArrayList<>();
        private final List<FittingSpec> fittings = new ArrayList<>();
        private final List<ConnectionEdge> edges = new ArrayList<>();
        /** Running element count (segments + fittings in insertion order). */
        private int elementCount = 0;
        private int lastElementIndex = -1;
        private Point3D lastElementPos = null;
        private String lastElementType = null;

        public void addSegment(SegmentSpec seg) {
            int idx = elementCount++;
            segments.add(seg);
            emitEdge(idx, seg.start(), "SEGMENT");
            lastElementIndex = idx;
            lastElementPos = seg.end();
            lastElementType = "SEGMENT";
        }

        public void addFitting(FittingSpec fit) {
            int idx = elementCount++;
            fittings.add(fit);
            emitEdge(idx, fit.position(), "FITTING");
            lastElementIndex = idx;
            lastElementPos = fit.position();
            lastElementType = "FITTING";
        }

        public int segmentCount() { return segments.size(); }
        public int fittingCount() { return fittings.size(); }

        private void emitEdge(int toIndex, Point3D toPos, String toType) {
            if (lastElementIndex >= 0) {
                String edgeType = lastElementType + "_TO_" + toType;
                edges.add(new ConnectionEdge(
                        lastElementIndex, toIndex, lastElementPos, toPos, edgeType));
            }
        }

        RouteResult build(CrawlState finalState) {
            double totalLen = segments.stream()
                    .mapToDouble(SegmentSpec::lengthMm).sum();
            return new RouteResult(
                    Collections.unmodifiableList(new ArrayList<>(segments)),
                    Collections.unmodifiableList(new ArrayList<>(fittings)),
                    Collections.unmodifiableList(new ArrayList<>(edges)),
                    finalState,
                    totalLen);
        }
    }
}
