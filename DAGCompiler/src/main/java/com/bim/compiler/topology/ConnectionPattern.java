package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.Map;

/**
 * Connection patterns for element junctions.
 * From PATTERN G4 in SESSION_STATE.md.
 *
 * Extracted from enhanced_federation_GI.db:
 * - Wall junction overlaps
 * - Pipe-to-fitting insertion depths
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public final class ConnectionPattern {

    private ConnectionPattern() {}

    // =========================================================================
    // WALL JUNCTIONS (from PATTERN G4)
    // =========================================================================

    /**
     * Typical wall overlap at perpendicular junction.
     * From query: avg 133mm (≈ wall thickness)
     * Walls overlap by approximately wall thickness at corners.
     */
    public static final double WALL_JUNCTION_OVERLAP_METERS = 0.133;

    /**
     * Number of L-corner junctions found in model.
     */
    public static final int L_CORNER_COUNT = 267;

    /**
     * Number of perpendicular junctions found in model.
     */
    public static final int PERPENDICULAR_COUNT = 81;

    /**
     * Total wall-wall connections in model.
     */
    public static final int TOTAL_WALL_CONNECTIONS = 1194;

    // =========================================================================
    // PIPE CONNECTIONS (from PATTERN G4)
    // =========================================================================

    /**
     * Pipe insertion depths by discipline (meters).
     */
    private static final Map<Discipline, Double> PIPE_INSERTION_DEPTH;

    static {
        PIPE_INSERTION_DEPTH = new EnumMap<>(Discipline.class);
        PIPE_INSERTION_DEPTH.put(Discipline.CW, 0.017);   // 17mm
        PIPE_INSERTION_DEPTH.put(Discipline.FP, 0.014);   // 14mm
        PIPE_INSERTION_DEPTH.put(Discipline.LPG, 0.018);  // 18mm
        PIPE_INSERTION_DEPTH.put(Discipline.SP, 0.042);   // 42mm (larger pipes)
    }

    /**
     * Get pipe insertion depth for a discipline.
     * @param discipline the piping discipline
     * @return insertion depth in meters
     */
    public static double getPipeInsertionDepth(Discipline discipline) {
        Double depth = PIPE_INSERTION_DEPTH.get(discipline);
        if (depth == null)
            throw new IllegalArgumentException(
                "No pipe insertion depth for discipline " + discipline);
        return depth;
    }

    /**
     * Get pipe insertion depth in millimeters.
     */
    public static double getPipeInsertionDepthMm(Discipline discipline) {
        return getPipeInsertionDepth(discipline) * 1000;
    }

    // =========================================================================
    // CONNECTION VALIDATION
    // =========================================================================

    /**
     * Check if wall junction overlap is valid.
     * Expected: overlap ≈ wall thickness (100-300mm range).
     *
     * @param overlapMeters the overlap distance
     * @return true if overlap is in expected range
     */
    public static boolean isValidWallJunction(double overlapMeters) {
        // Allow 50mm to 400mm overlap (covers all wall thicknesses)
        return overlapMeters >= 0.050 && overlapMeters <= 0.400;
    }

    /**
     * Check if pipe-fitting connection is valid.
     * Expected: pipe overlaps into fitting by insertion depth.
     *
     * @param discipline the pipe discipline
     * @param overlapMeters the overlap distance
     * @return true if overlap is reasonable
     */
    public static boolean isValidPipeConnection(Discipline discipline, double overlapMeters) {
        double expected = getPipeInsertionDepth(discipline);
        double tolerance = 0.020; // 20mm tolerance

        return overlapMeters >= (expected - tolerance)
            && overlapMeters <= (expected + tolerance * 3); // More lenient on over-insertion
    }

    /**
     * Calculate expected wall overlap at corner given wall thicknesses.
     * At L-corners, overlap typically equals the thinner wall's thickness.
     *
     * @param thickness1 first wall thickness (meters)
     * @param thickness2 second wall thickness (meters)
     * @return expected overlap in meters
     */
    public static double expectedWallOverlap(double thickness1, double thickness2) {
        return Math.min(thickness1, thickness2);
    }
}
