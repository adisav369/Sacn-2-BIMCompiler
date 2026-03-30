package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

/**
 * PENETRATE — pass through a barrier (slab or wall). Produces 1 × sleeve
 * plus an optional fire collar if the barrier is fire-rated.
 *
 * @param barrierType SLAB or WALL
 * @param thicknessMm barrier thickness in mm
 * @param fireRated   true if fire-rated barrier (adds fire collar)
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.4 — Witness: W-PENETRATE-1
public record PenetrateOp(String barrierType, double thicknessMm,
                           boolean fireRated) implements CrawlOp {

    private static final String TAG = "CRAWL";

    public PenetrateOp {
        if (thicknessMm <= 0)
            throw new IllegalArgumentException("thicknessMm must be > 0");
    }

    @Override
    public String toVerbLine() {
        return fireRated
                ? String.format("PENETRATE %s %.0f FIRE_RATED", barrierType, thicknessMm)
                : String.format("PENETRATE %s %.0f", barrierType, thicknessMm);
    }

    @Override
    public CrawlState apply(CrawlState state, CrawlRouter.ResultBuilder builder) {
        BIMLogger.fine(TAG, "PENETRATE: pos={} barrier={} thickness={:.0f}mm fireRated={} diameter={:.0f}mm",
                state.position(), barrierType, thicknessMm, fireRated, state.diameterMm());

        // Sleeve — encases pipe through barrier
        builder.addFitting(new CrawlRouter.FittingSpec(
                state.position(), "SLEEVE_" + barrierType, state.diameterMm(),
                state.diameterMm(), 0));

        // Fire collar — if fire-rated
        if (fireRated) {
            BIMLogger.fine(TAG, "PENETRATE: fire-rated → adding FIRE_COLLAR");
            builder.addFitting(new CrawlRouter.FittingSpec(
                    state.position(), "FIRE_COLLAR", state.diameterMm(),
                    state.diameterMm(), 0));
        }

        // Implementing EYES_SRS §4.7 — GEO-ROUTE proof coverage for MEP segments
        BIMLogger.geo("ROUTE", "PENETRATE {}: through={} at=({:.0f},{:.0f},{:.0f})",
                state.product(), barrierType,
                state.position().x() * 1000, state.position().y() * 1000, state.position().z() * 1000);

        // Position jumps through barrier
        CrawlState after = state.advance(thicknessMm);
        BIMLogger.fine(TAG, "PENETRATE: done pos={}", after.position());
        return after;
    }
}
