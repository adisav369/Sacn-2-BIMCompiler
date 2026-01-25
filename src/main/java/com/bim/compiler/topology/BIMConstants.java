package com.bim.compiler.topology;

/**
 * Constants extracted from enhanced_federation_GI.db and federation Python code.
 * All values are PROVEN from data analysis, not assumed.
 *
 * EXTRACTED - DO NOT MODIFY WITHOUT DB VERIFICATION
 */
public final class BIMConstants {

    private BIMConstants() {} // Prevent instantiation

    // =========================================================================
    // COORDINATE SYSTEM (from FACT 1, FACT 2, CODE PATTERN 1)
    // =========================================================================

    /**
     * Coordinate tolerance in meters.
     * From FACT 1: 2 decimal place precision = ~5mm
     */
    public static final double TOLERANCE = 0.005;

    /**
     * All coordinates are in METERS.
     * From CODE PATTERN 1: "Store/load meters 1:1. NO /1000 divisions."
     */
    public static final String UNIT = "METERS";

    /**
     * Global project offset X in meters.
     * From FACT 2: global_offset table
     */
    public static final double OFFSET_X = 121.47;

    /**
     * Global project offset Y in meters.
     */
    public static final double OFFSET_Y = -21.66;

    /**
     * Global project offset Z in meters.
     */
    public static final double OFFSET_Z = -0.78;

    /**
     * Project extent X in meters.
     */
    public static final double EXTENT_X = 73.7;

    /**
     * Project extent Y in meters.
     */
    public static final double EXTENT_Y = 59.1;

    /**
     * Project extent Z in meters.
     */
    public static final double EXTENT_Z = 59.8;

    // =========================================================================
    // BUILDING STRUCTURE (from PATTERN 5)
    // =========================================================================

    /**
     * Floor-to-floor height in meters (commercial/airport building).
     */
    public static final double FLOOR_TO_FLOOR = 4.0;

    /**
     * Foundation level Z in meters.
     */
    public static final double Z_FOUNDATION = -31.0;

    /**
     * Ground level Z in meters.
     */
    public static final double Z_GROUND = 0.0;

    /**
     * Roof level Z in meters.
     */
    public static final double Z_ROOF = 27.0;

    /**
     * Number of floors.
     */
    public static final int FLOOR_COUNT = 8;

    // =========================================================================
    // CONSTRUCTION RULES (from Construction Rules Research)
    // =========================================================================

    /**
     * Minimum MEP-to-structure clearance in meters.
     * From RULE 1: BIM coordination standard = 50mm
     */
    public static final double MEP_STRUCTURE_CLEARANCE = 0.050;

    /**
     * Maximum sprinkler head spacing in meters.
     * From RULE 6: NFPA 13 light hazard = 4.6m
     */
    public static final double MAX_SPRINKLER_SPACING = 4.6;

    /**
     * Minimum opening distance from wall corner in meters.
     * From RULE 10: UK Building Regs = 665mm
     * NOTE: Not enforced in this project (documented as optional)
     */
    public static final double MIN_OPENING_CORNER_CLEARANCE = 0.665;

    /**
     * Typical ceiling plenum depth in meters.
     * From RULE 7: DB finding 829-916mm
     */
    public static final double PLENUM_DEPTH = 0.9;

    // =========================================================================
    // STRUCTURAL GRID (from RULE 8)
    // =========================================================================

    /**
     * Structural grid X spacing in meters.
     */
    public static final double GRID_SPACING_X = 5.0;

    /**
     * Structural grid Y spacing in meters.
     */
    public static final double GRID_SPACING_Y = 8.0;

    // =========================================================================
    // GEOMETRY (from FACT 10, CODE PATTERN 7)
    // =========================================================================

    /**
     * Bytes per vertex in geometry blob.
     * From FACT 10: 3 x float32 (x, y, z)
     */
    public static final int BYTES_PER_VERTEX = 12;

    /**
     * Bytes per face in geometry blob.
     * From CODE PATTERN 7: 3 x int32 (v1, v2, v3)
     */
    public static final int BYTES_PER_FACE = 12;

    /**
     * Multi-story element threshold in meters.
     * From FACT 5: Elements with Z-span > 8m span multiple floors
     */
    public static final double MULTI_STORY_THRESHOLD = 8.0;
}
