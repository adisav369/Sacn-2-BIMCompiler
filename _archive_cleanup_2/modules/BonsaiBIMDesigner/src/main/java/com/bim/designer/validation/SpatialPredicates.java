package com.bim.designer.validation;

import java.util.List;

/**
 * ERP-maths spatial predicates for validation.
 *
 * <p>Pure math — no mesh, no Blender, no viewport. Uses only:
 * <ul>
 *   <li>Position: PlacementRequest.positionXMm, positionYMm, positionZMm</li>
 *   <li>Dimensions: PlacementRequest.widthMm, depthMm, heightMm</li>
 * </ul>
 *
 * <p>These are the same ERP-maths from TE_MINING_RESULTS.md:
 * proven against 48,428 Terminal elements in sub-second SQL aggregation.
 *
 * // Implementing DocAction_SRS §4.3 — Witness: W-DV-SPATIAL-1
 */
public final class SpatialPredicates {

    private SpatialPredicates() {} // utility class

    /**
     * DV-F-02: Planar (XY) nearest-neighbour distance from {@code target}
     * to the closest other element in {@code group}.
     *
     * @param group  all same-class elements on the same storey
     * @param target the element to measure from
     * @return NN distance in mm, or Double.MAX_VALUE if group has only target
     */
    public static double nnDistance(List<PlacementRequest> group, PlacementRequest target) {
        double minDist = Double.MAX_VALUE;
        for (PlacementRequest other : group) {
            if (other == target) continue;
            double dist = planarDistance(target, other);
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist;
    }

    /**
     * DV-F-03: Centre-to-centre clearance minus half cross-section of each element.
     * {@code clearance = centroid_2D_dist - radius_a - radius_b}
     *
     * <p>Proven M12 formula from TE_MINING_RESULTS.md:
     * {@code radius = MIN(width, depth) / 2} (cross-section from product dimensions).
     *
     * @return clearance in mm (negative = overlap)
     */
    public static double centreClearance(PlacementRequest a, PlacementRequest b) {
        double dist = planarDistance(a, b);
        double radiusA = Math.min(a.widthMm(), a.depthMm()) / 2.0;
        double radiusB = Math.min(b.widthMm(), b.depthMm()) / 2.0;
        return dist - radiusA - radiusB;
    }

    /**
     * DV-F-06: Standard deviation of Z positions for a group of elements.
     * Used for PER_STOREY_Z_CONSISTENCY check.
     *
     * @param group elements of same class on same storey
     * @return standard deviation of positionZMm in mm
     */
    public static double zStdDev(List<PlacementRequest> group) {
        if (group.size() < 2) return 0;

        double sum = 0;
        for (PlacementRequest r : group) {
            sum += r.positionZMm();
        }
        double mean = sum / group.size();

        double variance = 0;
        for (PlacementRequest r : group) {
            double diff = r.positionZMm() - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / group.size());
    }

    /**
     * 2D planar (XY) distance between two element centroids.
     */
    public static double planarDistance(PlacementRequest a, PlacementRequest b) {
        double dx = a.positionXMm() - b.positionXMm();
        double dy = a.positionYMm() - b.positionYMm();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
