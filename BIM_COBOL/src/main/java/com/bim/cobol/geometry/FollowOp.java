package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

/**
 * FOLLOW — trace straight along current direction for a given distance.
 * Produces N × segment product where N = ceil(distance / stockLength).
 * No fittings — straight run only.
 *
 * @param distanceMm   total run distance in mm
 * @param stockLengthMm stock pipe length in mm (default 6000)
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 FOLLOW
public record FollowOp(double distanceMm, double stockLengthMm) implements CrawlOp {

    private static final String TAG = "CRAWL";

    public FollowOp {
        if (distanceMm <= 0) throw new IllegalArgumentException("distanceMm must be > 0");
        if (stockLengthMm <= 0) throw new IllegalArgumentException("stockLengthMm must be > 0");
    }

    @Override
    public String toVerbLine() {
        return String.format("FOLLOW %.0f STOCK_LENGTH %.0f", distanceMm, stockLengthMm);
    }

    @Override
    public CrawlState apply(CrawlState state, CrawlRouter.ResultBuilder builder) {
        int segmentCount = (int) Math.ceil(distanceMm / stockLengthMm);
        BIMLogger.fine(TAG, "FOLLOW: pos={} dir={} dist={:.0f}mm stock={:.0f}mm → {} segments",
                state.position(), state.direction(), distanceMm, stockLengthMm, segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            double segLen = Math.min(stockLengthMm, distanceMm - i * stockLengthMm);
            builder.addSegment(new CrawlRouter.SegmentSpec(
                    state.position(), state.advance(segLen).position(),
                    state.diameterMm(), state.product(), segLen));
            state = state.advance(segLen);
        }
        BIMLogger.fine(TAG, "FOLLOW: done pos={}", state.position());
        return state;
    }
}
