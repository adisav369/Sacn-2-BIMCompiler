package com.bim.compiler;

/**
 * Centralized constants for BIM Compiler.
 *
 * Phase 29: Extracted from hardcoded values across codebase.
 *
 * Organization:
 * - TOLERANCE_*: Precision thresholds for geometry checks
 * - IRC_*: International Residential Code 2021 requirements
 * - STANDARD_*: Default building component dimensions
 * - ASSEMBLY_*: Assembly-level geometry values
 * - MEP_*: Mechanical/Electrical/Plumbing values
 *
 * Sources:
 * - IRC 2021 (International Residential Code)
 * - TERMINAL extraction (proven values)
 * - AS/NZS standards where noted
 */
public final class BIMConstants {

    private BIMConstants() {} // Prevent instantiation

    // =========================================================================
    // TOLERANCES
    // Source markers: ✓ EXTRACTED (from TERMINAL), ◆ RESEARCHED (standards), ○ ASSUMED
    // =========================================================================

    /** General tolerance for geometry checks (5mm) - ✓ EXTRACTED: TERMINAL FACT 1 */
    public static final double TOLERANCE = 0.005;

    /** Tolerance for plane alignment (50mm) - ○ ASSUMED: accounts for grid vs wall center offsets
     *  TODO: Validate against TERMINAL door/window placement analysis */
    public static final double PLANE_TOLERANCE = 0.05;

    /** Tolerance for assembly-level checks (10mm) - ○ ASSUMED: 2x base tolerance for component fit
     *  TODO: Validate against TERMINAL assembly component positioning */
    public static final double ASSEMBLY_TOLERANCE = 0.01;

    /** Tolerance for stud height validation (500mm) - ◆ RESEARCHED: AS 1684 allows partial studs at openings
     *  Cripple studs and jack studs vary in height around doors/windows */
    public static final double STUD_HEIGHT_TOLERANCE = 0.5;

    /** Minimum gap to ignore floating point errors - ○ ASSUMED: standard FP epsilon */
    public static final double MIN_GAP_EPSILON = 0.0001;

    // =========================================================================
    // IRC 2021 - STAIR REQUIREMENTS (R311.7)
    // =========================================================================

    /** Maximum riser height 196mm (7-3/4 inches) - IRC R311.7.5.1 */
    public static final double IRC_MAX_RISER_HEIGHT = 0.196;

    /** Minimum tread depth 254mm (10 inches) - IRC R311.7.5.2 */
    public static final double IRC_MIN_TREAD_DEPTH = 0.254;

    /** Minimum stair width 914mm (36 inches) - IRC R311.7.1 */
    public static final double IRC_MIN_STAIR_WIDTH = 0.914;

    /** Phase RM/C8: Preferred stair tread depth 267mm (was hardcoded 0.267) */
    public static final double PREFERRED_TREAD_DEPTH = 0.267;

    // =========================================================================
    // IRC 2021 - HABITABLE SPACE REQUIREMENTS (R304)
    // =========================================================================

    /** Minimum habitable room area 6.5 m² (70 sq ft) - IRC R304.1 */
    public static final double IRC_MIN_HABITABLE_AREA = 6.5;

    /** Minimum room dimension 2.134m (7 ft) - IRC R304.2 */
    public static final double IRC_MIN_DIMENSION = 2.134;

    /** Minimum corridor width 914mm (36 inches) - IRC R311.6 */
    public static final double IRC_MIN_CORRIDOR_WIDTH = 0.914;

    /** Practical minimum room width 1.5m */
    public static final double PRACTICAL_MIN_ROOM_WIDTH = 1.5;

    // =========================================================================
    // IRC 2021 - EGRESS WINDOW REQUIREMENTS (R310)
    // =========================================================================

    /** Minimum egress window area 0.53 m² (5.7 sq ft) - IRC R310.1 */
    public static final double IRC_EGRESS_MIN_AREA = 0.53;

    /** Minimum egress window height 610mm (24 inches) - IRC R310.1 */
    public static final double IRC_EGRESS_MIN_HEIGHT = 0.61;

    /** Minimum egress window width 508mm (20 inches) - IRC R310.1 */
    public static final double IRC_EGRESS_MIN_WIDTH = 0.508;

    // =========================================================================
    // STANDARD BUILDING DIMENSIONS
    // =========================================================================

    /** Standard wall thickness 150mm - ✓ EXTRACTED: TERMINAL concrete panels.
     *  ○ ASSUMED for residential — Malaysian_Residential profile should override
     *  to 100mm (half-brick partition) per Malaysian brick construction practice.
     *  Malaysian_Educational uses 150mm (blockwork). */
    public static final double STANDARD_WALL_THICKNESS = 0.15;

    /** Standard slab thickness 150mm - ◆ RESEARCHED: Malaysian residential structural
     *  practice for spans ≤ 4m. TERMINAL slabs are thicker (200mm+). */
    public static final double STANDARD_SLAB_THICKNESS = 0.15;

    /** Standard slab edge projection past wall face 200mm.
     *  ✓ EXTRACTED: TERMINAL 175-250mm range. ○ ASSUMED 200mm mid-range.
     *  Profile override needed — Malaysian residential 50-100mm per brick wall construction.
     *  Note: 200mm on a 100mm brick wall = cantilever (2x wall thickness). */
    public static final double STANDARD_SLAB_OVERLAP = 0.2;

    /** Standard landing thickness 150mm - ◆ RESEARCHED: matches slab thickness */
    public static final double STANDARD_LANDING_THICKNESS = 0.15;

    /** Separating floor slab thickness 200mm - ◆ RESEARCHED: UBBL fire separation
     *  (Table 4 ~1hr fire rating) + BS 8233 acoustic (Rw 50dB).
     *  Phase RM/A9: Externalized from BuildingCompiler. */
    public static final double SEPARATING_SLAB_THICKNESS = 0.20;

    // =========================================================================
    // STANDARD DOOR DIMENSIONS
    // =========================================================================

    /** Standard door width 900mm - ◆ RESEARCHED: MS 1184 accessible minimum.
     *  Non-accessible internal doors may be 750mm per Malaysian practice. */
    public static final double STANDARD_DOOR_WIDTH = 0.9;

    /** Standard door height 2100mm - ◆ RESEARCHED: Malaysian residential standard */
    public static final double STANDARD_DOOR_HEIGHT = 2.1;

    /** Door leaf thickness 100mm - ○ ASSUMED: typical hollow-core leaf + frame */
    public static final double DOOR_THICKNESS = 0.1;

    /** Standard hinges per door (3 for standard height) - ◆ RESEARCHED: industry standard */
    public static final int STANDARD_DOOR_HINGES = 3;

    // =========================================================================
    // STANDARD WINDOW DIMENSIONS
    // =========================================================================

    /** Standard window width 1200mm - ◆ RESEARCHED: common Malaysian residential module */
    public static final double STANDARD_WINDOW_WIDTH = 1.2;

    /** Standard window height 1200mm - ◆ RESEARCHED: common Malaysian residential module */
    public static final double STANDARD_WINDOW_HEIGHT = 1.2;

    /** Standard window sill height 900mm - ◆ RESEARCHED: JKR practice for child safety,
     *  aligns with Malaysian residential standard */
    public static final double STANDARD_SILL_HEIGHT = 0.9;

    /** Window frame thickness 100mm - ○ ASSUMED: typical aluminium frame depth */
    public static final double WINDOW_THICKNESS = 0.1;

    /** Phase RM/A11: Window panel spacing gap 500mm (mullion/frame between panels) */
    public static final double WINDOW_PANEL_GAP = 0.5;

    // =========================================================================
    // ASSEMBLY GEOMETRY THRESHOLDS
    // =========================================================================

    /** Threshold for detecting thin walls (under 300mm = wall, over = bulk element) */
    public static final double WALL_THIN_THRESHOLD = 0.3;

    /** Maximum distance to consider opening "near" a wall (1m) */
    public static final double WALL_PROXIMITY_DISTANCE = 1.0;

    /** Threshold for extreme position values (likely error) */
    public static final double EXTREME_POSITION_THRESHOLD = 1000.0;

    /** Corner cluster radius for gap detection (50mm) */
    public static final double CORNER_CLUSTER_RADIUS = 0.05;

    /** Wall edge margin for detection (100mm) */
    public static final double WALL_EDGE_MARGIN = 0.1;

    // =========================================================================
    // MEP DIMENSIONS (MECHANICAL/ELECTRICAL/PLUMBING)
    // =========================================================================

    /** Phase RM/A10: Default sprinkler fixture type (was hardcoded "pendant") */
    public static final String SPRINKLER_FIXTURE_TYPE = "pendant";

    /** Phase RM/A10: Default light fixture type (was hardcoded "surface") */
    public static final String LIGHT_FIXTURE_TYPE = "surface";

    /** Sprinkler pendant head radius 50mm */
    public static final double SPRINKLER_HEAD_RADIUS = 0.05;

    /** Sprinkler drop distance below ceiling 100mm */
    public static final double SPRINKLER_CEILING_DROP = 0.1;

    /** Light fixture half-width 300mm (typical 600x600mm recessed) */
    public static final double LIGHT_FIXTURE_HALF_SIZE = 0.3;

    /** Light fixture depth 100mm */
    public static final double LIGHT_FIXTURE_DEPTH = 0.1;

    /** Z-distance threshold for same floor detection (1m) */
    public static final double SAME_FLOOR_Z_THRESHOLD = 1.0;

    /** Minimum sprinkler spacing for duplicate detection 500mm */
    public static final double SPRINKLER_MIN_SPACING = 0.5;

    /** MEP to structure clearance requirement 50mm */
    public static final double MEP_STRUCTURE_CLEARANCE = 0.05;

    // =========================================================================
    // GRID AND SOLVER DEFAULTS
    // =========================================================================

    /** Default grid width for constraint solver */
    public static final int DEFAULT_GRID_WIDTH = 20;

    /** Default grid height for constraint solver */
    public static final int DEFAULT_GRID_HEIGHT = 20;

    // =========================================================================
    // BYTE BUFFER CONSTANTS
    // =========================================================================

    /** Bytes per float in geometry buffers */
    public static final int BYTES_PER_FLOAT = 4;

    /** Bytes per integer in geometry buffers */
    public static final int BYTES_PER_INT = 4;

    /** Components per vertex (x, y, z) */
    public static final int VERTEX_STRIDE = 3;

    /** Indices per triangle face */
    public static final int FACE_STRIDE = 3;
}
