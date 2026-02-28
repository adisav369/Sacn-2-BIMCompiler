package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.Vector3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry: T-junction valley stitching between hip and gable roofs.
 *
 * <p>Given three slope planes (hip south, gable west, gable east), computes
 * the two valley lines where slopes intersect and their meeting point where
 * all three planes converge.
 *
 * <p>No DB access, no side effects — deterministic geometry only.
 */
public final class ValleyStitcher {

    private ValleyStitcher() {} // utility class

    private static final double TOLERANCE = 0.005; // 5mm in meters

    // ── Inner types ─────────────────────────────────────────────────

    /**
     * Plane ax + by + cz = d.
     * Normal vector is (a, b, c) — not necessarily unit length.
     */
    public record Plane3D(double a, double b, double c, double d) {

        /** Construct plane through three non-collinear points. */
        public static Plane3D fromPoints(Point3D p0, Point3D p1, Point3D p2) {
            Vector3D e1 = new Vector3D(p1.x() - p0.x(), p1.y() - p0.y(), p1.z() - p0.z());
            Vector3D e2 = new Vector3D(p2.x() - p0.x(), p2.y() - p0.y(), p2.z() - p0.z());
            Vector3D n = e1.cross(e2);
            double dd = n.x() * p0.x() + n.y() * p0.y() + n.z() * p0.z();
            return new Plane3D(n.x(), n.y(), n.z(), dd);
        }

        /** True signed distance from point to plane (meters). */
        public double signedDistance(Point3D p) {
            double num = a * p.x() + b * p.y() + c * p.z() - d;
            double den = Math.sqrt(a * a + b * b + c * c);
            return num / den;
        }

        /** True if point is within tolerance of this plane. */
        public boolean contains(Point3D p, double tol) {
            return Math.abs(signedDistance(p)) <= tol;
        }

        /** Normal vector (not unit length). */
        public Vector3D normal() {
            return new Vector3D(a, b, c);
        }
    }

    /** One valley line — intersection of two slope planes. */
    public record ValleyLine(Point3D origin, Vector3D direction,
                             Plane3D planeA, Plane3D planeB) {}

    /** Result of T-junction valley computation. */
    public record ValleyResult(
            ValleyLine westValley,
            ValleyLine eastValley,
            Point3D meetingPoint,
            double meetingAngleDeg,
            List<String> violations
    ) {
        public boolean isValid() { return violations.isEmpty(); }
    }

    // ── Main algorithm ──────────────────────────────────────────────

    /**
     * Compute T-junction valley geometry.
     *
     * @param hipSlope  hip roof south slope plane
     * @param gableWest gable west slope plane
     * @param gableEast gable east slope plane
     * @param ridgeMeet expected ridge-hip intersection (for forward compat)
     * @return valley result with lines, meeting point, angle, and violations
     */
    public static ValleyResult computeTJunction(Plane3D hipSlope,
                                                 Plane3D gableWest,
                                                 Plane3D gableEast,
                                                 Point3D ridgeMeet) {
        List<String> violations = new ArrayList<>();

        // 1. Intersect hip south slope with gable west slope → west valley line
        Point3D[] westIntersect = intersectPlanes(hipSlope, gableWest);

        // 2. Intersect hip south slope with gable east slope → east valley line
        Point3D[] eastIntersect = intersectPlanes(hipSlope, gableEast);

        if (westIntersect == null || eastIntersect == null) {
            violations.add("planes are parallel — no intersection");
            return new ValleyResult(null, null, null, 0, violations);
        }

        Vector3D westDir = new Vector3D(
                westIntersect[1].x(), westIntersect[1].y(), westIntersect[1].z());
        Vector3D eastDir = new Vector3D(
                eastIntersect[1].x(), eastIntersect[1].y(), eastIntersect[1].z());

        // 3. Orient directions downhill (z decreasing)
        if (westDir.z() > 0)
            westDir = new Vector3D(-westDir.x(), -westDir.y(), -westDir.z());
        if (eastDir.z() > 0)
            eastDir = new Vector3D(-eastDir.x(), -eastDir.y(), -eastDir.z());

        ValleyLine westValley = new ValleyLine(westIntersect[0], westDir,
                hipSlope, gableWest);
        ValleyLine eastValley = new ValleyLine(eastIntersect[0], eastDir,
                hipSlope, gableEast);

        // 4. Compute meeting point (closest approach of two skew/coplanar lines)
        Point3D meetingPoint = closestApproach(
                westValley.origin(), westValley.direction(),
                eastValley.origin(), eastValley.direction());

        // 5. Validate
        if (!hipSlope.contains(meetingPoint, TOLERANCE))
            violations.add("meeting point not on hip slope (dist="
                    + String.format("%.4f", Math.abs(hipSlope.signedDistance(meetingPoint)))
                    + "m)");
        if (!gableWest.contains(meetingPoint, TOLERANCE))
            violations.add("meeting point not on gable west slope (dist="
                    + String.format("%.4f", Math.abs(gableWest.signedDistance(meetingPoint)))
                    + "m)");
        if (!gableEast.contains(meetingPoint, TOLERANCE))
            violations.add("meeting point not on gable east slope (dist="
                    + String.format("%.4f", Math.abs(gableEast.signedDistance(meetingPoint)))
                    + "m)");

        double angleDeg = Math.toDegrees(westDir.angleTo(eastDir));
        if (angleDeg < 20 || angleDeg > 160)
            violations.add("valley angle " + String.format("%.1f", angleDeg)
                    + "° outside 20-160° range");

        if (westDir.z() > 0) violations.add("west valley does not descend");
        if (eastDir.z() > 0) violations.add("east valley does not descend");

        return new ValleyResult(westValley, eastValley, meetingPoint,
                angleDeg, violations);
    }

    // ── Geometry helpers ────────────────────────────────────────────

    /**
     * Intersect two planes. Returns [pointOnLine, directionVector] or null if parallel.
     * Direction encoded as Point3D for internal use.
     */
    static Point3D[] intersectPlanes(Plane3D p1, Plane3D p2) {
        Vector3D n1 = p1.normal();
        Vector3D n2 = p2.normal();
        Vector3D dir = n1.cross(n2);

        double lenSq = dir.x() * dir.x() + dir.y() * dir.y() + dir.z() * dir.z();
        if (lenSq < 1e-20) return null; // parallel planes

        // Find a point on the line by zeroing the coordinate with largest |dir| component
        double ax = Math.abs(dir.x()), ay = Math.abs(dir.y()), az = Math.abs(dir.z());
        double px, py, pz;

        if (az >= ax && az >= ay) {
            // Set z=0, solve 2×2: a1*x + b1*y = d1, a2*x + b2*y = d2
            double det = p1.a() * p2.b() - p2.a() * p1.b();
            px = (p1.d() * p2.b() - p2.d() * p1.b()) / det;
            py = (p1.a() * p2.d() - p2.a() * p1.d()) / det;
            pz = 0;
        } else if (ay >= ax) {
            // Set y=0, solve 2×2: a1*x + c1*z = d1, a2*x + c2*z = d2
            double det = p1.a() * p2.c() - p2.a() * p1.c();
            px = (p1.d() * p2.c() - p2.d() * p1.c()) / det;
            pz = (p1.a() * p2.d() - p2.a() * p1.d()) / det;
            py = 0;
        } else {
            // Set x=0, solve 2×2: b1*y + c1*z = d1, b2*y + c2*z = d2
            double det = p1.b() * p2.c() - p2.b() * p1.c();
            py = (p1.d() * p2.c() - p2.d() * p1.c()) / det;
            pz = (p1.b() * p2.d() - p2.b() * p1.d()) / det;
            px = 0;
        }

        return new Point3D[]{
                new Point3D(px, py, pz),
                new Point3D(dir.x(), dir.y(), dir.z())
        };
    }

    /**
     * Closest approach midpoint between two lines.
     * Line 1: p1 + t·d1,  Line 2: p2 + s·d2.
     * For coplanar intersecting lines, returns the exact intersection.
     */
    private static Point3D closestApproach(Point3D p1, Vector3D d1,
                                           Point3D p2, Vector3D d2) {
        Vector3D w = new Vector3D(
                p1.x() - p2.x(), p1.y() - p2.y(), p1.z() - p2.z());
        double a = d1.dot(d1);
        double b = d1.dot(d2);
        double c = d2.dot(d2);
        double dd = d1.dot(w);
        double e = d2.dot(w);

        double denom = a * c - b * b;
        if (Math.abs(denom) < 1e-20) return p1; // parallel — degenerate

        double t = (b * e - c * dd) / denom;
        double s = (a * e - b * dd) / denom;

        double x1 = p1.x() + t * d1.x(), x2 = p2.x() + s * d2.x();
        double y1 = p1.y() + t * d1.y(), y2 = p2.y() + s * d2.y();
        double z1 = p1.z() + t * d1.z(), z2 = p2.z() + s * d2.z();

        return new Point3D((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
    }
}
