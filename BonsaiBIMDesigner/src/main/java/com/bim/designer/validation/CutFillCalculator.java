package com.bim.designer.validation;

import com.bim.designer.validation.AlignmentContext.StationPoint;

import java.util.List;
import java.util.Objects;

/**
 * Cut-and-fill volume computation for earthworks estimation.
 *
 * <p>Computes the volume of earth to be cut (excavated) and filled (imported)
 * when a design level is imposed on natural terrain. The difference between
 * terrain Z and design Z at each point determines whether that point is
 * a cut (terrain above design) or fill (terrain below design).
 *
 * <p>Method: Delaunay-free prismoidal approximation. The terrain is treated
 * as a set of influence cells (Voronoi-like), each with area proportional
 * to the average spacing between points. For alignment-based calculations,
 * station-to-station trapezoidal segments are used.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Flat design level:</b> constant Z across entire site (traditional cut-and-fill)</li>
 *   <li><b>Alignment design profile:</b> variable Z along an alignment (road/rail grading)</li>
 * </ul>
 *
 * // Implementing INFRA_DESIGNER_SRS.md §1.4 — Witness: W-CUTFILL-1
 */
public class CutFillCalculator {

    /** Result of a cut-and-fill computation. All volumes in m³. */
    public record CutFillResult(
            double cutVolumeM3,    // earth to excavate (terrain > design)
            double fillVolumeM3,   // earth to import (terrain < design)
            double netVolumeM3,    // cut - fill (positive = net excavation)
            int cutPointCount,     // number of terrain points above design
            int fillPointCount,    // number of terrain points below design
            int onGradeCount       // number of points at design level (within tolerance)
    ) {
        /** @return total earth movement (cut + fill) in m³ */
        public double totalMovementM3() { return cutVolumeM3 + fillVolumeM3; }
    }

    private static final double ON_GRADE_TOLERANCE_MM = 50.0; // ±50mm = on grade

    /**
     * Compute cut-and-fill volumes for a flat design level across terrain points.
     *
     * <p>Each terrain point has an influence area estimated from the average
     * point spacing (total area / point count). The volume contribution of
     * each point = |ΔZ| × influence area.
     *
     * @param terrainPoints terrain survey points (station, x, y, z in mm)
     * @param designLevelMm constant design elevation (mm)
     * @param siteWidthMm   site width for area estimation (mm)
     * @return cut/fill volumes in m³
     */
    public static CutFillResult flatLevel(List<StationPoint> terrainPoints,
                                           double designLevelMm,
                                           double siteWidthMm) {
        Objects.requireNonNull(terrainPoints);
        if (terrainPoints.size() < 2) {
            return new CutFillResult(0, 0, 0, 0, 0, 0);
        }

        // Compute bounding box for influence area
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (StationPoint p : terrainPoints) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }

        // If siteWidthMm provided, use it for cross-section; otherwise use bounding box
        double extentX = maxX - minX;
        double extentY = maxY - minY;
        double totalAreaMm2 = extentX * extentY;
        if (totalAreaMm2 < 1.0) {
            return new CutFillResult(0, 0, 0, 0, 0, 0);
        }

        // Each point's influence area = total area / N
        double influenceAreaMm2 = totalAreaMm2 / terrainPoints.size();

        double cutMm3 = 0, fillMm3 = 0;
        int cutCount = 0, fillCount = 0, onGradeCount = 0;

        for (StationPoint p : terrainPoints) {
            double dz = p.z() - designLevelMm;
            if (Math.abs(dz) <= ON_GRADE_TOLERANCE_MM) {
                onGradeCount++;
            } else if (dz > 0) {
                cutMm3 += dz * influenceAreaMm2;
                cutCount++;
            } else {
                fillMm3 += (-dz) * influenceAreaMm2;
                fillCount++;
            }
        }

        // Convert mm³ to m³ (1m³ = 1e9 mm³)
        double cutM3 = cutMm3 / 1e9;
        double fillM3 = fillMm3 / 1e9;

        return new CutFillResult(cutM3, fillM3, cutM3 - fillM3,
                cutCount, fillCount, onGradeCount);
    }

    /**
     * Compute cut-and-fill volumes along an alignment profile.
     *
     * <p>For each consecutive pair of alignment stations, the terrain Z and
     * design Z are compared. The volume between stations is computed as a
     * trapezoidal cross-section: average |ΔZ| × corridor width × station spacing.
     *
     * <p>Design Z is the alignment's own Z (which may follow terrain + offset).
     * The terrain Z at each station is provided by the terrain context.
     *
     * @param terrainContext  terrain providing elevationAt(x,y)
     * @param designProfile   alignment stations with design Z values
     * @param corridorWidthMm width of the corridor (mm)
     * @return cut/fill volumes in m³
     */
    public static CutFillResult alongAlignment(PlacementContext terrainContext,
                                                List<StationPoint> designProfile,
                                                double corridorWidthMm) {
        Objects.requireNonNull(terrainContext);
        Objects.requireNonNull(designProfile);
        if (designProfile.size() < 2) {
            return new CutFillResult(0, 0, 0, 0, 0, 0);
        }

        double cutMm3 = 0, fillMm3 = 0;
        int cutCount = 0, fillCount = 0, onGradeCount = 0;

        for (int i = 0; i < designProfile.size() - 1; i++) {
            StationPoint a = designProfile.get(i);
            StationPoint b = designProfile.get(i + 1);

            // Terrain Z at each station's XY position
            double terrainZa = terrainContext.elevationAt(a.x(), a.y());
            double terrainZb = terrainContext.elevationAt(b.x(), b.y());

            // Design Z at each station
            double designZa = a.z();
            double designZb = b.z();

            // ΔZ at each end
            double dzA = terrainZa - designZa;
            double dzB = terrainZb - designZb;

            // Station-to-station horizontal distance
            double segLen = Math.hypot(b.x() - a.x(), b.y() - a.y());
            if (segLen < 0.001) continue;

            // Cross-section area = corridor width × segment length
            double sliceArea = corridorWidthMm * segLen;

            // If both ends are cut or both are fill, simple trapezoidal volume
            if (dzA >= 0 && dzB >= 0) {
                // Both cut
                double avgDz = (dzA + dzB) / 2.0;
                if (avgDz > ON_GRADE_TOLERANCE_MM) {
                    cutMm3 += avgDz * sliceArea;
                    cutCount++;
                } else {
                    onGradeCount++;
                }
            } else if (dzA <= 0 && dzB <= 0) {
                // Both fill
                double avgDz = (Math.abs(dzA) + Math.abs(dzB)) / 2.0;
                if (avgDz > ON_GRADE_TOLERANCE_MM) {
                    fillMm3 += avgDz * sliceArea;
                    fillCount++;
                } else {
                    onGradeCount++;
                }
            } else {
                // Mixed: part cut, part fill — split at zero crossing
                double total = Math.abs(dzA) + Math.abs(dzB);
                double cutFraction = (dzA > 0 ? Math.abs(dzA) : Math.abs(dzB)) / total;
                double fillFraction = 1.0 - cutFraction;
                double cutDz = dzA > 0 ? dzA : dzB;
                double fillDz = dzA < 0 ? dzA : dzB;

                cutMm3 += (Math.abs(cutDz) / 2.0) * cutFraction * sliceArea;
                fillMm3 += (Math.abs(fillDz) / 2.0) * fillFraction * sliceArea;
                cutCount++;
                fillCount++;
            }
        }

        double cutM3 = cutMm3 / 1e9;
        double fillM3 = fillMm3 / 1e9;

        return new CutFillResult(cutM3, fillM3, cutM3 - fillM3,
                cutCount, fillCount, onGradeCount);
    }
}
