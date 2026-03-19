package com.bim.designer.validation;

import java.util.List;
import java.util.Objects;

/**
 * AlignmentContext — corridor along a polyline over terrain for infrastructure.
 *
 * <p>An alignment is defined by a centreline (list of station points with X,Y,Z)
 * and a corridor width. Elements are placed along the alignment at station offsets.
 * The Z elevation varies along the alignment — it follows the terrain surface
 * plus any design vertical offset (super-elevation, grade).
 *
 * <p>Fit check: element must fit within the corridor width at its station.
 * Elevation: interpolated Z between the nearest alignment points.
 *
 * <p>This models road centrelines, rail alignments, and bridge spans as
 * placement containers — same abstract contract as a room.
 *
 * // Implementing INFRA_DESIGNER_SRS.md §3.4 — Witness: W-CONTEXT-ALIGN-1
 */
public class AlignmentContext implements PlacementContext {

    /** A point on the alignment centreline. */
    public record StationPoint(
            double station,    // chainage along alignment (mm)
            double x,          // world X (mm)
            double y,          // world Y (mm)
            double z           // elevation (mm) — from terrain + vertical offset
    ) {}

    private final List<StationPoint> points;
    private final double corridorWidthMm;  // half-width each side of centreline
    private final Bounds bounds;

    /**
     * @param points           centreline stations (must be sorted by station, ≥2 points)
     * @param corridorWidthMm  total corridor width (mm) — element must fit within this
     */
    public AlignmentContext(List<StationPoint> points, double corridorWidthMm) {
        Objects.requireNonNull(points);
        if (points.size() < 2) throw new IllegalArgumentException("Alignment needs ≥2 points");
        this.points = List.copyOf(points);
        this.corridorWidthMm = corridorWidthMm;
        this.bounds = computeBounds();
    }

    @Override
    public boolean fits(double widthMm, double depthMm, double heightMm,
                        double xMm, double yMm) {
        // Element must be within corridor bounds (AABB check against alignment extent)
        // depthMm = along-alignment extent, widthMm = cross-alignment extent
        return widthMm <= corridorWidthMm
            && xMm >= bounds.minX() && xMm <= bounds.maxX()
            && yMm >= bounds.minY() && yMm <= bounds.maxY();
    }

    @Override
    public double elevationAt(double xMm, double yMm) {
        // Find nearest alignment station and interpolate Z
        StationPoint nearest = null;
        StationPoint secondNearest = null;
        double nearestDist = Double.MAX_VALUE;
        double secondDist = Double.MAX_VALUE;

        for (StationPoint p : points) {
            double dist = Math.hypot(p.x - xMm, p.y - yMm);
            if (dist < nearestDist) {
                secondNearest = nearest;
                secondDist = nearestDist;
                nearest = p;
                nearestDist = dist;
            } else if (dist < secondDist) {
                secondNearest = p;
                secondDist = dist;
            }
        }

        if (nearest == null) return 0;
        if (secondNearest == null) return nearest.z;

        // Linear interpolation between two nearest points
        double totalDist = nearestDist + secondDist;
        if (totalDist < 0.001) return nearest.z;
        double weight = 1.0 - (nearestDist / totalDist);
        return nearest.z * weight + secondNearest.z * (1.0 - weight);
    }

    @Override
    public Bounds bounds() { return bounds; }

    @Override
    public String contextType() { return "ALIGNMENT"; }

    /** @return the centreline points */
    public List<StationPoint> points() { return points; }

    /** @return corridor width (mm) */
    public double corridorWidthMm() { return corridorWidthMm; }

    /** @return total alignment length from first to last station (mm) */
    public double length() {
        return points.get(points.size() - 1).station() - points.get(0).station();
    }

    /** @return interpolated elevation at a given station (chainage mm) */
    public double elevationAtStation(double stationMm) {
        // Find bracketing points
        for (int i = 0; i < points.size() - 1; i++) {
            StationPoint a = points.get(i);
            StationPoint b = points.get(i + 1);
            if (stationMm >= a.station() && stationMm <= b.station()) {
                double t = (stationMm - a.station()) / (b.station() - a.station());
                return a.z + t * (b.z - a.z);
            }
        }
        // Beyond range: clamp to nearest endpoint
        if (stationMm <= points.get(0).station()) return points.get(0).z;
        return points.get(points.size() - 1).z;
    }

    /**
     * Interpolated XY position at a given station (chainage mm).
     * For curved alignments with many closely-spaced points, this traces
     * the curve. For straight segments, it's linear between points.
     *
     * @return [x, y] in mm at the given station
     */
    public double[] positionAtStation(double stationMm) {
        for (int i = 0; i < points.size() - 1; i++) {
            StationPoint a = points.get(i);
            StationPoint b = points.get(i + 1);
            if (stationMm >= a.station() && stationMm <= b.station()) {
                double t = (stationMm - a.station()) / (b.station() - a.station());
                return new double[]{
                    a.x + t * (b.x - a.x),
                    a.y + t * (b.y - a.y)
                };
            }
        }
        if (stationMm <= points.get(0).station())
            return new double[]{ points.get(0).x, points.get(0).y };
        StationPoint last = points.get(points.size() - 1);
        return new double[]{ last.x, last.y };
    }

    /**
     * Heading angle (radians) at a given station — tangent to the curve.
     * Used for orienting elements (bridge deck, road cross-section) perpendicular
     * to the centreline at each station.
     *
     * @return heading in radians (0 = +X, π/2 = +Y)
     */
    public double headingAtStation(double stationMm) {
        // Find bracketing segment
        for (int i = 0; i < points.size() - 1; i++) {
            StationPoint a = points.get(i);
            StationPoint b = points.get(i + 1);
            if (stationMm >= a.station() && stationMm <= b.station()) {
                return Math.atan2(b.y - a.y, b.x - a.x);
            }
        }
        // Default: heading of last segment
        if (points.size() >= 2) {
            StationPoint a = points.get(points.size() - 2);
            StationPoint b = points.get(points.size() - 1);
            return Math.atan2(b.y - a.y, b.x - a.x);
        }
        return 0;
    }

    /**
     * Curvature (1/radius in 1/mm) at a given station.
     * Computed from three consecutive points. Straight = 0. Tight bend = large.
     * Used for super-elevation calculation and speed limit checks.
     *
     * @return curvature (1/mm), 0 for straight sections
     */
    public double curvatureAtStation(double stationMm) {
        // Find the bracketing index
        int idx = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            if (stationMm >= points.get(i).station()
                    && stationMm <= points.get(i + 1).station()) {
                idx = i;
                break;
            }
        }
        // Need 3 points: idx-1, idx, idx+1
        if (idx < 1 || idx >= points.size() - 1) return 0;
        StationPoint p0 = points.get(idx - 1);
        StationPoint p1 = points.get(idx);
        StationPoint p2 = points.get(idx + 1);

        // Menger curvature: κ = 4·area(triangle) / (|a|·|b|·|c|)
        double ax = p1.x - p0.x, ay = p1.y - p0.y;
        double bx = p2.x - p1.x, by = p2.y - p1.y;
        double cx = p2.x - p0.x, cy = p2.y - p0.y;
        double area2 = Math.abs(ax * by - ay * bx);  // 2× triangle area
        double a = Math.hypot(ax, ay);
        double b = Math.hypot(bx, by);
        double c = Math.hypot(cx, cy);
        double denom = a * b * c;
        if (denom < 0.001) return 0;
        return (2.0 * area2) / denom;  // = 1/radius
    }

    /**
     * Generate a curved alignment from terrain points that follows a contour band.
     * Selects terrain points within ±toleranceMm of the target elevation and
     * orders them to form a smooth path.
     *
     * <p>This is the "contour-following" mode: the alignment hugs the terrain
     * at a target elevation, curving around hills and valleys instead of
     * cutting straight through them. Minimizes earthworks.
     *
     * @param allPoints      all terrain elevation points
     * @param targetZ        target elevation (mm) to follow
     * @param toleranceMm    how far from target Z to accept points (mm)
     * @param corridorWidthMm corridor width (mm)
     * @return AlignmentContext that curves along the contour
     */
    public static AlignmentContext fromContour(List<StationPoint> allPoints,
                                                double targetZ, double toleranceMm,
                                                double corridorWidthMm) {
        // Filter points within tolerance band of target elevation
        List<StationPoint> band = new java.util.ArrayList<>();
        for (StationPoint p : allPoints) {
            if (Math.abs(p.z - targetZ) <= toleranceMm) {
                band.add(p);
            }
        }
        if (band.size() < 2) {
            throw new IllegalArgumentException(
                    "Not enough points within " + toleranceMm + "mm of Z=" + targetZ +
                    "mm (found " + band.size() + ")");
        }

        // Order points into a path using nearest-neighbour greedy walk
        List<StationPoint> path = new java.util.ArrayList<>();
        boolean[] used = new boolean[band.size()];
        path.add(band.get(0));
        used[0] = true;

        for (int step = 1; step < band.size(); step++) {
            StationPoint last = path.get(path.size() - 1);
            double bestDist = Double.MAX_VALUE;
            int bestIdx = -1;
            for (int j = 0; j < band.size(); j++) {
                if (used[j]) continue;
                double dist = Math.hypot(band.get(j).x - last.x, band.get(j).y - last.y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = j;
                }
            }
            if (bestIdx < 0) break;
            used[bestIdx] = true;
            path.add(band.get(bestIdx));
        }

        // Assign cumulative station distances
        List<StationPoint> stationPath = new java.util.ArrayList<>();
        double station = 0;
        stationPath.add(new StationPoint(0, path.get(0).x, path.get(0).y, path.get(0).z));
        for (int i = 1; i < path.size(); i++) {
            StationPoint prev = path.get(i - 1);
            StationPoint cur = path.get(i);
            station += Math.hypot(cur.x - prev.x, cur.y - prev.y);
            stationPath.add(new StationPoint(station, cur.x, cur.y, cur.z));
        }

        return new AlignmentContext(stationPath, corridorWidthMm);
    }

    private Bounds computeBounds() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double halfW = corridorWidthMm / 2.0;
        for (StationPoint p : points) {
            minX = Math.min(minX, p.x - halfW);
            maxX = Math.max(maxX, p.x + halfW);
            minY = Math.min(minY, p.y - halfW);
            maxY = Math.max(maxY, p.y + halfW);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
