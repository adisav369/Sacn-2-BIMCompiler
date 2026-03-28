package com.bim.cobol.geometry;

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
 * and produces a list of {@link CrawlOp}s that {@link CrawlRouter} executes.
 * No invention — all positions come from ARC extraction.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4
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
     * Build the complete route for this discipline through a building.
     *
     * @param geo building geometry (read-only ARC data)
     * @return route result with all segments, fittings, edges
     */
    DisciplineRouteResult buildRoute(BuildingGeometry geo);

    /** Complete routing result for a discipline, wrapping CrawlRouter.RouteResult. */
    record DisciplineRouteResult(
            String discipline,
            CrawlRouter.RouteResult routeResult,
            int floorCount,
            int roomCount,
            String pattern
    ) {
        public int segmentCount() { return routeResult.segments().size(); }
        public int fittingCount() { return routeResult.fittings().size(); }
        public double totalLengthMm() { return routeResult.totalLengthMm(); }
    }
}
