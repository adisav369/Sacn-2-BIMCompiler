package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

/**
 * Immutable crawl state for movement ops along a pipe/duct/cable route.
 *
 * <p>Each CrawlOp reads state, produces BOM line(s), and returns an updated state.
 * All distances in millimeters; Point3D in meters (project convention).
 *
 * @param position   current head position (meters)
 * @param direction  unit vector — which way we're going
 * @param diameterMm current pipe/duct diameter in mm
 * @param product    what's being laid (PipeSegment, DuctSegment, CableTray)
 * @param discipline AD_Org discipline code (FP, ELEC, ACMV, CW, SP, LPG)
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — crawl state for all movement ops
public record CrawlState(
        Point3D position,
        Point3D direction,
        double diameterMm,
        String product,
        String discipline
) {
    /** Advance position by distanceMm along current direction. */
    public CrawlState advance(double distanceMm) {
        double distM = distanceMm / 1000.0;
        Point3D newPos = new Point3D(
                position.x() + direction.x() * distM,
                position.y() + direction.y() * distM,
                position.z() + direction.z() * distM);
        return new CrawlState(newPos, direction, diameterMm, product, discipline);
    }

    /** Turn direction by angleDeg in the XY plane. */
    public CrawlState turn(double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        Point3D newDir = new Point3D(
                direction.x() * cos - direction.y() * sin,
                direction.x() * sin + direction.y() * cos,
                direction.z());
        return new CrawlState(position, newDir, diameterMm, product, discipline);
    }

    /** Change diameter (for reducer transitions). */
    public CrawlState resize(double newDiameterMm) {
        return new CrawlState(position, direction, newDiameterMm, product, discipline);
    }
}
