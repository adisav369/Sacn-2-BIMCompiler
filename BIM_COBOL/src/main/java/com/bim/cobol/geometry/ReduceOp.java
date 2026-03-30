package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

/**
 * REDUCE — change diameter at current position. Produces 1 × reducer fitting.
 *
 * @param newDiameterMm target diameter in mm
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.3 — Witness: W-REDUCE-1
public record ReduceOp(double newDiameterMm) implements CrawlOp {

    private static final String TAG = "CRAWL";

    public ReduceOp {
        if (newDiameterMm <= 0)
            throw new IllegalArgumentException("newDiameterMm must be > 0");
    }

    @Override
    public String toVerbLine() {
        return String.format("REDUCE %.0f", newDiameterMm);
    }

    @Override
    public CrawlState apply(CrawlState state, CrawlRouter.ResultBuilder builder) {
        BIMLogger.fine(TAG, "REDUCE: pos={} fromDia={:.0f}mm toDia={:.0f}mm → fitting=REDUCER",
                state.position(), state.diameterMm(), newDiameterMm);
        builder.addFitting(new CrawlRouter.FittingSpec(
                state.position(), "REDUCER", state.diameterMm(),
                newDiameterMm, 0));
        // Implementing EYES_SRS §4.7 — GEO-ROUTE proof coverage for MEP segments
        BIMLogger.geo("ROUTE", "FITTING {}: pos=({:.0f},{:.0f},{:.0f}) type=REDUCER angle={:.0f}deg",
                state.product(),
                state.position().x() * 1000, state.position().y() * 1000, state.position().z() * 1000,
                0.0);
        return state.resize(newDiameterMm);
    }
}
