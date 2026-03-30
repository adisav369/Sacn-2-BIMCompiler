package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Discipline-specific route builder — composes CrawlOps for one MEP discipline.
 *
 * <p>Each implementation encodes the routing pattern for its discipline:
 * FP (riser→header→branches→sprinklers), ELEC (DB→tray→fixtures),
 * CW (tank→riser→fixtures), SP (fixtures→stack→drain), ACMV (AHU→duct→terminals),
 * LPG (meter→riser→kitchen).
 *
 * <p>The builder reads geometry from {@link BuildingGeometry} (ARC-compiled data)
 * and produces a {@link RoutePlan} — a declarative list of {@link CrawlOp}s.
 * The framework logs each op as a verb line (audit trail), then executes via
 * {@link CrawlRouter}. No invention — all positions come from ARC extraction.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4
// Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 3 — auditable verb lines
public interface DisciplineRouteBuilder {

    /** Discipline code matching {@link com.bim.compiler.topology.Discipline}. */
    String discipline();

    /** Product type for segments (PipeSegment, DuctSegment, CableTray). */
    String segmentProduct();

    /** Main line diameter in mm. */
    double mainDiameterMm();

    /** Branch line diameter in mm. */
    double branchDiameterMm();

    /** Default stock length for FOLLOW ops (mm). */
    double stockLengthMm();

    /**
     * Plan the complete route for this discipline through a building.
     * Returns a declarative plan (initial state + ops) — the framework
     * handles logging and execution.
     *
     * @param geo building geometry (read-only ARC data)
     * @return route plan with initial state and CrawlOps
     */
    RoutePlan plan(BuildingGeometry geo);

    /**
     * Build the route: plan → log verb lines → execute via CrawlRouter.
     * Default implementation — builders only need to implement {@link #plan(BuildingGeometry)}.
     */
    default DisciplineRouteResult buildRoute(BuildingGeometry geo) {
        RoutePlan rp = plan(geo);

        // Log each op as a verb line — the auditor reads these
        List<String> verbLines = new ArrayList<>();
        for (CrawlOp op : rp.ops()) {
            verbLines.add(op.toVerbLine());
        }
        BIMLogger.info("ROUTE", "{} plan: {} verb lines for {} floors",
                discipline(), verbLines.size(), rp.floorCount());
        for (String line : verbLines) {
            BIMLogger.info("ROUTE", "  {}", line);
        }

        // Execute
        CrawlRouter.RouteResult result = CrawlRouter.execute(rp.initial(), rp.ops());
        return new DisciplineRouteResult(discipline(), result, verbLines,
                rp.floorCount(), rp.roomCount(), rp.pattern());
    }

    /** Declarative route plan — initial state + ops list. */
    record RoutePlan(
            CrawlState initial,
            List<CrawlOp> ops,
            int floorCount,
            int roomCount,
            String pattern
    ) {}

    /** Complete routing result for a discipline, wrapping CrawlRouter.RouteResult. */
    record DisciplineRouteResult(
            String discipline,
            CrawlRouter.RouteResult routeResult,
            List<String> verbLines,
            int floorCount,
            int roomCount,
            String pattern
    ) {
        public int segmentCount() { return routeResult.segments().size(); }
        public int fittingCount() { return routeResult.fittings().size(); }
        public double totalLengthMm() { return routeResult.totalLengthMm(); }
    }
}
