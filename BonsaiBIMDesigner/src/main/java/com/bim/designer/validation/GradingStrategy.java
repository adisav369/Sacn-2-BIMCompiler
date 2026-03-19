package com.bim.designer.validation;

import com.bim.designer.validation.AlignmentContext.StationPoint;

import java.util.List;
import java.util.Objects;

/**
 * GradingStrategy — controls how design Z relates to terrain Z.
 *
 * <p>Infrastructure alignments have two extremes:
 * <ul>
 *   <li><b>Contour-following</b> (default): the road bends to stay at the
 *       terrain's natural elevation, minimising cut-and-fill earthworks.
 *       The alignment curves along the contour band.</li>
 *   <li><b>Straight cut-and-fill</b>: the road runs in a straight line at a
 *       fixed design level. Earth is excavated where terrain is above
 *       (cut) and imported where terrain is below (fill).</li>
 * </ul>
 *
 * <p>A blend slider (0.0–1.0) interpolates between the two extremes:
 * <pre>
 *   designZ = contourZ × (1 − blend) + straightZ × blend
 * </pre>
 *
 * <ul>
 *   <li><b>blend = 0.0</b> → pure contour (default, minimum earthworks)</li>
 *   <li><b>blend = 0.5</b> → half contour, half straight (gentle smoothing)</li>
 *   <li><b>blend = 1.0</b> → pure straight (maximum earthworks, shortest route)</li>
 * </ul>
 *
 * <p>The user drags a slider in the Designer UI. At each position, the
 * alignment path is recomputed and the cut/fill volumes update live.
 *
 * // Implementing INFRA_DESIGNER_SRS.md §5.2+§6 — Witness: W-GRADING-1
 */
public record GradingStrategy(
        Mode mode,
        double blend,           // 0.0 = contour, 1.0 = straight
        double designLevelMm    // target Z for STRAIGHT mode (mm)
) {

    /** Grading mode — how the alignment relates to terrain. */
    public enum Mode {
        /** Follow terrain contour band. Minimises earthworks. Default. */
        CONTOUR,
        /** Fixed design level — traditional cut-and-fill. */
        STRAIGHT,
        /** Blend between contour and straight. User-adjustable slider. */
        BLEND
    }

    public GradingStrategy {
        if (blend < 0 || blend > 1.0) {
            throw new IllegalArgumentException("Blend must be 0.0–1.0, got " + blend);
        }
    }

    // ── Factory methods ─────────────────────────────────────────────

    /** Default: pure contour-following. Minimises earthworks. */
    public static GradingStrategy contour() {
        return new GradingStrategy(Mode.CONTOUR, 0.0, 0);
    }

    /** Pure straight cut-and-fill at a fixed design level. */
    public static GradingStrategy straight(double designLevelMm) {
        return new GradingStrategy(Mode.STRAIGHT, 1.0, designLevelMm);
    }

    /**
     * Blended: interpolate between contour and straight.
     * @param blend 0.0 = contour, 1.0 = straight
     * @param designLevelMm target Z for the straight component
     */
    public static GradingStrategy blended(double blend, double designLevelMm) {
        return new GradingStrategy(Mode.BLEND, blend, designLevelMm);
    }

    // ── Design Z computation ─────────────────────────────────────────

    /**
     * Compute the design Z at a position, given the terrain's natural Z.
     *
     * <p>Contour mode: design Z = terrain Z (follow the ground exactly).
     * Straight mode: design Z = fixed designLevelMm.
     * Blend mode: linear interpolation.
     *
     * @param terrainZMm natural terrain elevation at this position (mm)
     * @return the design elevation (mm) the road surface should be at
     */
    public double designZAt(double terrainZMm) {
        return switch (mode) {
            case CONTOUR  -> terrainZMm;                    // follow terrain
            case STRAIGHT -> designLevelMm;                 // fixed level
            case BLEND    -> terrainZMm * (1.0 - blend)     // weighted mix
                           + designLevelMm * blend;
        };
    }

    /**
     * Compute design Z along a series of terrain stations.
     * Returns a new list of StationPoints with Z replaced by design Z.
     *
     * @param terrainStations terrain points with natural Z
     * @return design profile: same XY, design Z
     */
    public List<StationPoint> computeDesignProfile(List<StationPoint> terrainStations) {
        Objects.requireNonNull(terrainStations);
        return terrainStations.stream()
                .map(p -> new StationPoint(p.station(), p.x(), p.y(),
                        designZAt(p.z())))
                .toList();
    }

    /**
     * Compute the cut/fill volume difference caused by this grading strategy
     * relative to the natural terrain.
     *
     * @param terrainContext  terrain providing elevationAt(x,y)
     * @param terrainStations terrain stations for design profile computation
     * @param corridorWidthMm corridor width (mm)
     * @return cut/fill volumes in m³
     */
    public CutFillCalculator.CutFillResult computeEarthworks(
            PlacementContext terrainContext,
            List<StationPoint> terrainStations,
            double corridorWidthMm) {
        List<StationPoint> designProfile = computeDesignProfile(terrainStations);
        return CutFillCalculator.alongAlignment(terrainContext, designProfile, corridorWidthMm);
    }

    /** @return true if this strategy is pure contour (no earthworks) */
    public boolean isContour() { return mode == Mode.CONTOUR || blend == 0.0; }

    /** @return true if this strategy involves any straightening */
    public boolean hasCutFill() { return blend > 0.0; }

    @Override
    public String toString() {
        return switch (mode) {
            case CONTOUR  -> "CONTOUR (follow terrain)";
            case STRAIGHT -> String.format("STRAIGHT @%.1fm", designLevelMm / 1000);
            case BLEND    -> String.format("BLEND %.0f%% (design @%.1fm)",
                    blend * 100, designLevelMm / 1000);
        };
    }
}
