package com.bim.cobol.geometry;

import com.bim.cobol.geometry.DisciplineRouteBuilder.DisciplineRouteResult;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * DocEvent activation — fires discipline RouteBuilders when OrderLines compile.
 *
 * <p>iDempiere DocEvent pattern: when a DISCIPLINE C_OrderLine is processed
 * during compilation, this event handler resolves the discipline from AD_Org_ID,
 * looks up the corresponding {@link DisciplineRouteBuilder} from the
 * {@link DisciplineRouteRegistry}, and executes the route.
 *
 * <p>The result (segments, fittings, edges) feeds back into the BOM as
 * child M_BOM_Line rows — each segment/fitting becomes a product reference.
 *
 * <p>// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4
 * // Pre-Flight Citation: DISC_VALIDATION_DB_SRS §10.4.11 Phase 2 — Discipline Routing
 */
public final class RouteDocEvent {

    private static final String TAG = "ROUTE";

    private RouteDocEvent() {}

    /**
     * Fire routing for a single discipline.
     *
     * @param discipline discipline code (FP, ELEC, CW, SP, ACMV, LPG)
     * @param geo        building geometry (read-only ARC data)
     * @return route result, or null if discipline has no route builder
     */
    public static DisciplineRouteResult fire(String discipline, BuildingGeometry geo) {
        DisciplineRouteBuilder builder = DisciplineRouteRegistry.get(discipline);
        if (builder == null) {
            BIMLogger.fine(TAG, "RouteDocEvent: no builder for discipline={}", discipline);
            return null;
        }

        BIMLogger.info(TAG, "RouteDocEvent: firing {} route ({})",
                discipline, builder.segmentProduct());
        DisciplineRouteResult result = builder.buildRoute(geo);
        BIMLogger.info(TAG, "RouteDocEvent: {} done — segments={} fittings={} len={:.0f}mm floors={} rooms={}",
                discipline, result.segmentCount(), result.fittingCount(),
                result.totalLengthMm(), result.floorCount(), result.roomCount());
        return result;
    }

    /**
     * Fire routing for all MEP disciplines in a building.
     *
     * <p>Called during compilation after discipline OrderLines are created
     * (by {@link com.bim.compiler.callout.OrderLineProductCallout}).
     * Results are collected for BOM child expansion.
     *
     * @param disciplines list of discipline codes to route
     * @param geo         building geometry
     * @return list of route results (one per discipline that has a builder)
     */
    public static List<DisciplineRouteResult> fireAll(List<String> disciplines,
                                                       BuildingGeometry geo) {
        BIMLogger.info(TAG, "RouteDocEvent: fireAll for {} disciplines", disciplines.size());
        List<DisciplineRouteResult> results = new ArrayList<>();
        for (String disc : disciplines) {
            DisciplineRouteResult r = fire(disc, geo);
            if (r != null) {
                results.add(r);
            }
        }

        int totalSegs = results.stream().mapToInt(DisciplineRouteResult::segmentCount).sum();
        int totalFits = results.stream().mapToInt(DisciplineRouteResult::fittingCount).sum();
        BIMLogger.info(TAG, "RouteDocEvent: fireAll done — {} routes, {} total segments, {} total fittings",
                results.size(), totalSegs, totalFits);
        return results;
    }
}
