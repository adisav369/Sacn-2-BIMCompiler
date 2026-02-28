package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks sprinkler routing result against fire protection coverage rules (ad_fp_coverage).
 *
 * <p>Validates coverage per head, maximum spacing, and wall distance for rectangular rooms.
 * All dimensions in meters.
 */
public final class ComplianceChecker {

    private ComplianceChecker() {}

    /** Fire protection coverage rule (from ad_fp_coverage row). */
    public record CoverageRule(
            String hazardClass,
            double maxCoverageM2,
            double maxSpacingM,
            double minSpacingM,
            double wallDistanceM
    ) {}

    /** Single compliance check result. */
    public record ComplianceResult(String rule, boolean pass, double actual, double limit, String detail) {}

    /**
     * Check sprinkler placement against coverage rules.
     *
     * @param heads      head positions (meters)
     * @param roomMinX   room AABB min X (meters)
     * @param roomMaxX   room AABB max X (meters)
     * @param roomMinY   room AABB min Y (meters)
     * @param roomMaxY   room AABB max Y (meters)
     * @param rule       fire protection coverage rule
     * @return list of compliance results (one per check)
     */
    public static List<ComplianceResult> check(List<Point3D> heads,
                                               double roomMinX, double roomMaxX,
                                               double roomMinY, double roomMaxY,
                                               CoverageRule rule) {
        List<ComplianceResult> results = new ArrayList<>();
        double roomAreaM2 = (roomMaxX - roomMinX) * (roomMaxY - roomMinY);

        if (heads.isEmpty()) {
            results.add(new ComplianceResult("COVERAGE", false, 0, rule.maxCoverageM2,
                    "no heads in room"));
            return results;
        }

        // 1. Coverage per head <= max_coverage_m2
        double coveragePerHead = roomAreaM2 / heads.size();
        results.add(new ComplianceResult("COVERAGE",
                coveragePerHead <= rule.maxCoverageM2,
                coveragePerHead, rule.maxCoverageM2,
                String.format("%.1f m2/head (max %.1f)", coveragePerHead, rule.maxCoverageM2)));

        // 2. Max spacing between adjacent heads <= max_spacing_m
        if (heads.size() > 1) {
            double maxDist = 0;
            for (int i = 0; i < heads.size(); i++) {
                for (int j = i + 1; j < heads.size(); j++) {
                    double d = heads.get(i).horizontalDistanceTo(heads.get(j));
                    if (d < rule.maxSpacingM * 1.5) { // only check adjacent-ish heads
                        maxDist = Math.max(maxDist, d);
                    }
                }
            }
            results.add(new ComplianceResult("SPACING",
                    maxDist <= rule.maxSpacingM,
                    maxDist, rule.maxSpacingM,
                    String.format("max %.2f m (limit %.1f m)", maxDist, rule.maxSpacingM)));
        }

        // 3. Wall distance: for each wall, nearest head must be <= wall_distance_m
        double distMinX = Double.MAX_VALUE, distMaxX = Double.MAX_VALUE;
        double distMinY = Double.MAX_VALUE, distMaxY = Double.MAX_VALUE;
        for (Point3D h : heads) {
            distMinX = Math.min(distMinX, h.x() - roomMinX);
            distMaxX = Math.min(distMaxX, roomMaxX - h.x());
            distMinY = Math.min(distMinY, h.y() - roomMinY);
            distMaxY = Math.min(distMaxY, roomMaxY - h.y());
        }
        double worstWallDist = Math.max(Math.max(distMinX, distMaxX),
                Math.max(distMinY, distMaxY));
        results.add(new ComplianceResult("WALL_DISTANCE",
                worstWallDist <= rule.wallDistanceM,
                worstWallDist, rule.wallDistanceM,
                String.format("worst %.2f m (max %.1f m)", worstWallDist, rule.wallDistanceM)));

        return results;
    }
}
