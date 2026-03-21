package com.bim.eyes;

/**
 * Single source of truth for all geometric thresholds.
 * // Implementing EYES_SRS.md §3.5 — Witness: W-EYES-CONSTANTS
 *
 * Coordinate system convention (IFC default):
 *   X = East-West (positive East)
 *   Y = North-South (positive North)
 *   Z = Vertical (positive Up, 0 = ground level)
 */
public final class EyesConstants {

    private EyesConstants() {}

    // ── Shape archetype boundaries ──
    public static final double PLANARITY_THRESHOLD   = 0.15;
    public static final double ELONGATION_THRESHOLD   = 0.40;
    public static final double COMPACT_PLANARITY      = 0.25;
    public static final double COMPACT_ELONGATION     = 0.50;

    // ── Scale band boundaries (m³) ──
    public static final double SCALE_ARCHITECTURAL    = 1.0;
    public static final double SCALE_FURNITURE        = 0.01;
    public static final double SCALE_FITTING          = 0.0001;

    // ── Fingerprint comparison ──
    public static final double POSITION_TOLERANCE_M   = 0.050;  // 50mm centroid match
    public static final double CENTROID_GRID_M        = 0.1;    // 100mm quantization

    // ── Placement proof thresholds ──
    public static final double COORD_MIN              = -100.0;
    public static final double COORD_MAX              = 1000.0;
    public static final double MIN_DIMENSION_M        = 0.001;  // 1mm
    public static final double CENTROID_TOLERANCE_M   = 0.001;  // 1mm duplicate detection
    public static final double OVERLAP_VOLUME_M3      = 1e-5;   // 10mm cube

    // ── SpatialDiff tolerance bands (mm) ──
    public static final double SPATIAL_EXACT_MM       = 1.0;
    public static final double SPATIAL_DRIFT_MM       = 50.0;

    // ── Mesh integrity ──
    public static final double BOUNDS_TOLERANCE_M     = 0.001;  // 1mm vertex-to-bbox

    // ── Dimension range validation ──
    public static final double DIM_RANGE_RATIO        = 5.0;

    // ── Containment/coverage tolerances ──
    public static final double CONTAINMENT_TOLERANCE_M = 0.050;  // 50mm opening/fixture
    public static final double COVERAGE_TOLERANCE_M    = 0.010;  // 10mm wall coverage
    public static final double PLATE_THIN_WALL_TOL_M   = 0.050;  // 50mm curtain wall junction
    public static final double AREA_CONSERVATION_TOL   = 0.10;   // 10% floor area
    public static final double STANDARD_WALL_THICK_M   = 0.15;   // 150mm wall thickness
}
