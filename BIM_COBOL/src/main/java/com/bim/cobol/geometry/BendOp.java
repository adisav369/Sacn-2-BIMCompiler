package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

/**
 * BEND — change direction by a given angle. Produces 1 × elbow fitting.
 *
 * @param angleDeg bend angle in degrees (positive = left in XY plane)
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.1 — Witness: W-BEND-1
public record BendOp(double angleDeg) implements CrawlOp {

    private static final String TAG = "CRAWL";

    public BendOp {
        if (angleDeg == 0) throw new IllegalArgumentException("angleDeg must be non-zero");
    }

    @Override
    public String toVerbLine() {
        return String.format("BEND %.0f", angleDeg);
    }

    @Override
    public CrawlState apply(CrawlState state, CrawlRouter.ResultBuilder builder) {
        String fittingType = resolveFittingType(Math.abs(angleDeg));
        BIMLogger.fine(TAG, "BEND: pos={} dir={} angle={:.0f}° → fitting={} diameter={:.0f}mm",
                state.position(), state.direction(), angleDeg, fittingType, state.diameterMm());
        builder.addFitting(new CrawlRouter.FittingSpec(
                state.position(), fittingType, state.diameterMm(),
                state.diameterMm(), angleDeg));
        // Implementing EYES_SRS §4.7 — GEO-ROUTE proof coverage for MEP segments
        BIMLogger.geo("ROUTE", "FITTING {}: pos=({:.0f},{:.0f},{:.0f}) type={} angle={:.0f}deg",
                state.product(),
                state.position().x() * 1000, state.position().y() * 1000, state.position().z() * 1000,
                fittingType, angleDeg);
        CrawlState after = state.turn(angleDeg);
        BIMLogger.fine(TAG, "BEND: done dir={}", after.direction());
        return after;
    }

    /** Resolve fitting name from angle — elbows are standard at 45° and 90°. */
    private static String resolveFittingType(double absAngle) {
        if (Math.abs(absAngle - 90) < 0.5) return "ELBOW_90";
        if (Math.abs(absAngle - 45) < 0.5) return "ELBOW_45";
        return String.format("ELBOW_%.0f", absAngle);
    }
}
