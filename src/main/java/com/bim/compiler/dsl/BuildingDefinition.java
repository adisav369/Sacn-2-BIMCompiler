package com.bim.compiler.dsl;

import java.util.List;

/**
 * Parsed BUILDING definition from DSL.
 * Contains multiple storeys with vertical connections (stairs).
 *
 * Phase 26: Extended with construction-aware elements:
 * - Structural grid (axes A-E, 1-5)
 * - Building envelope (roof overhang, drainage)
 * - Porch semantics (attached/separate roof)
 *
 * Phase 46: Extended with multi-unit support:
 * - Building type (SINGLE_UNIT, MULTI_UNIT)
 * - Unit definitions (rooms grouped by dwelling)
 * - Shared spaces (corridors, risers)
 */
public record BuildingDefinition(
    String name,
    BuildingType buildingType,      // Phase 46: SINGLE_UNIT or MULTI_UNIT
    List<StoreyDef> storeys,        // Storeys (for SINGLE_UNIT, or legacy)
    List<UnitDefinition> units,     // Phase 46: Unit definitions (for MULTI_UNIT)
    SharedDefinition shared,        // Phase 46: Shared spaces (for MULTI_UNIT)
    CoreDef core,                   // Phase 56B: Vertical circulation core (building-level)
    RoofDef roof,
    GridDef grid,           // Phase 26: Structural grid
    EnvelopeDef envelope,   // Phase 26: Building envelope
    ScheduleDef doorSchedule,   // Phase 28: Door schedule registry
    ScheduleDef windowSchedule, // Phase 28: Window schedule registry
    String profile,             // Phase 28: Profile name (Malaysian_Residential, etc.)
    String protocol,            // Phase 28: Protocol name (Residential_Single_Storey, etc.)
    int lod,                    // Phase 28: Level of Detail (100-500)
    ConstructionSystem constructionSystem  // Phase 50B.1: FRAMED or MASONRY
) {
    // Backward-compatible constructor without Phase 26/28/46/56B fields
    public BuildingDefinition(String name, List<StoreyDef> storeys, RoofDef roof) {
        this(name, BuildingType.SINGLE_UNIT, storeys, List.of(), SharedDefinition.EMPTY,
             null, roof, null, null, null, null, null, null, 300, ConstructionSystem.FRAMED);
    }

    // Backward-compatible constructor without Phase 28/46/56B fields
    public BuildingDefinition(String name, List<StoreyDef> storeys, RoofDef roof,
                              GridDef grid, EnvelopeDef envelope) {
        this(name, BuildingType.SINGLE_UNIT, storeys, List.of(), SharedDefinition.EMPTY,
             null, roof, grid, envelope, null, null, null, null, 300, ConstructionSystem.FRAMED);
    }

    // Constructor without profile/protocol/lod (Phase 26 compat)
    public BuildingDefinition(String name, List<StoreyDef> storeys, RoofDef roof,
                              GridDef grid, EnvelopeDef envelope,
                              ScheduleDef doorSchedule, ScheduleDef windowSchedule) {
        this(name, BuildingType.SINGLE_UNIT, storeys, List.of(), SharedDefinition.EMPTY,
             null, roof, grid, envelope, doorSchedule, windowSchedule, null, null, 300, ConstructionSystem.FRAMED);
    }

    // Full constructor without Phase 46/56B fields (Phase 28 compat)
    public BuildingDefinition(String name, List<StoreyDef> storeys, RoofDef roof,
                              GridDef grid, EnvelopeDef envelope,
                              ScheduleDef doorSchedule, ScheduleDef windowSchedule,
                              String profile, String protocol, int lod) {
        this(name, BuildingType.SINGLE_UNIT, storeys, List.of(), SharedDefinition.EMPTY,
             null, roof, grid, envelope, doorSchedule, windowSchedule, profile, protocol, lod, ConstructionSystem.FRAMED);
    }

    /**
     * Check if this is a multi-unit building.
     */
    public boolean isMultiUnit() {
        return buildingType == BuildingType.MULTI_UNIT && !units.isEmpty();
    }

    /**
     * Get all storeys across all units (for multi-unit buildings).
     * For single-unit buildings, returns the storeys list directly.
     */
    public List<StoreyDef> getAllStoreys() {
        if (!isMultiUnit()) {
            return storeys;
        }
        List<StoreyDef> all = new java.util.ArrayList<>();
        for (UnitDefinition unit : units) {
            all.addAll(unit.storeys());
        }
        if (shared != null && !shared.isEmpty()) {
            all.addAll(shared.storeys());
        }
        // Sort by level
        all.sort((a, b) -> Integer.compare(a.level(), b.level()));
        return all;
    }

    /**
     * Get unit by name.
     */
    public UnitDefinition getUnit(String unitName) {
        return units.stream()
            .filter(u -> u.name().equals(unitName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Find which unit contains a room.
     * Returns null if room is in shared space or not found.
     */
    public String findUnitContainingRoom(String roomName) {
        for (UnitDefinition unit : units) {
            if (unit.containsRoom(roomName)) {
                return unit.name();
            }
        }
        return null;
    }

    /**
     * Check if a room is in shared space.
     */
    public boolean isRoomInShared(String roomName) {
        return shared != null && shared.findRoom(roomName) != null;
    }

    /**
     * Resolve door type to dimensions from schedule.
     * @param typeCode e.g. "D1"
     * @return [width, height] in meters, or null if not found
     */
    public double[] resolveDoorType(String typeCode) {
        if (doorSchedule == null) return null;
        return doorSchedule.resolve(typeCode);
    }

    /**
     * Resolve window type to dimensions from schedule.
     * @param typeCode e.g. "W1"
     * @return [width, height] in meters, or null if not found
     */
    public double[] resolveWindowType(String typeCode) {
        if (windowSchedule == null) return null;
        return windowSchedule.resolve(typeCode);
    }
    /**
     * Individual storey within building.
     * Phase 56: Extended with elevators and shafts for high-rise.
     */
    public record StoreyDef(
        String name,
        int level,           // 0 = ground, 1 = first floor, etc.
        double height,       // Floor-to-floor height in meters
        List<RoomDef> rooms,
        List<StairDef> stairs,
        List<LandingDef> landings,
        List<ElevatorDef> elevators,      // Phase 56: Elevator elements
        List<ElevatorLobbyDef> lobbies,   // Phase 56: Elevator lobbies
        List<ShaftDef> shafts             // Phase 56: MEP shafts
    ) {
        // Backward-compatible constructor (no elevators/shafts)
        public StoreyDef(String name, int level, double height,
                        List<RoomDef> rooms, List<StairDef> stairs,
                        List<LandingDef> landings) {
            this(name, level, height, rooms, stairs, landings,
                 List.of(), List.of(), List.of());
        }
    }

    /**
     * Room definition (LIVING, BEDROOM, DEPARTURE_LOUNGE, GATE, PORCH, OPEN_PLAN, etc.)
     * Supports multiple positioning modes:
     * - Explicit position: at:A1
     * - Grid bounds: bounds:A2-B4 (Phase 26)
     * - Constraint-based: adjacent:room1, exterior:north
     *
     * Phase 26 additions:
     * - gridBounds: Grid-referenced bounds (A2-B4)
     * - porchRoofType: ATTACHED or SEPARATE for porch rooms
     *
     * Phase 27 (TB-LKTN) additions:
     * - opensTo: Room this opens to (implies door connection)
     * - zones: Zone types for OPEN_PLAN (LIVING, DINING, KITCHEN)
     * - exteriorWalls: Multiple exterior wall directions
     */
    public record RoomDef(
        String type,         // LIVING, BEDROOM, OPEN_PLAN, PORCH, etc.
        String name,
        String gridPosition, // A1, B2, etc. - NULL if solver-placed
        double width,
        double depth,
        List<OpeningDef> openings,
        Double sprinklerSpacing,  // null if no sprinklers, else spacing in meters
        Double lightSpacing,      // null if no lights, else spacing in meters
        // Layer 3: Constraint fields
        List<String> adjacentTo,     // Room names this must be adjacent to
        List<String> notAdjacentTo,  // Room names this cannot be adjacent to
        String exteriorWall,         // Direction (north/south/east/west) or null (deprecated, use exteriorWalls)
        // Phase 16: Vertical constraint
        String alignsWith,           // Room name to align with (cross-storey)
        // Phase 17: Extended vertical vocabulary
        String above,                // Room name this must be above (higher storey)
        String below,                // Room name this must be below (lower storey)
        String stack,                // Named vertical stack this room belongs to
        // Phase 26: Construction-aware fields
        String gridBounds,           // Grid bounds reference: "A2-B4"
        PorchRoofType porchRoofType, // ATTACHED or SEPARATE (for PORCH type only)
        // Phase 27: TB-LKTN extensions
        String opensTo,              // Room this opens to (implies door)
        List<String> zones,          // Zone types for OPEN_PLAN
        List<String> exteriorWalls,  // Multiple exterior directions
        // Phase 47: Cross-unit constraint
        String adjacentUnit          // Room name in another unit (party wall constraint)
    ) {
        // Backward-compatible constructor without MEP or constraints
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings) {
            this(type, name, gridPosition, width, depth, openings, null, null,
                 List.of(), List.of(), null, null, null, null, null, null, null,
                 null, List.of(), List.of(), null);
        }

        // Constructor with sprinklers only (backward compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, null,
                 List.of(), List.of(), null, null, null, null, null, null, null,
                 null, List.of(), List.of(), null);
        }

        // Constructor with MEP but no constraints (backward compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 List.of(), List.of(), null, null, null, null, null, null, null,
                 null, List.of(), List.of(), null);
        }

        // Constructor without alignsWith (backward compat Phase 15)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing,
                       List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 adjacentTo, notAdjacentTo, exteriorWall, null, null, null, null, null, null,
                 null, List.of(), List.of(), null);
        }

        // Constructor with alignsWith only (Phase 16 compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing,
                       List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall,
                       String alignsWith) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 adjacentTo, notAdjacentTo, exteriorWall, alignsWith, null, null, null, null, null,
                 null, List.of(), List.of(), null);
        }

        // Constructor without Phase 26 fields (backward compat Phase 17)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing,
                       List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall,
                       String alignsWith, String above, String below, String stack) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 adjacentTo, notAdjacentTo, exteriorWall, alignsWith, above, below, stack, null, null,
                 null, List.of(), List.of(), null);
        }

        // Constructor without Phase 27 fields (Phase 26 compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing,
                       List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall,
                       String alignsWith, String above, String below, String stack,
                       String gridBounds, PorchRoofType porchRoofType) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 adjacentTo, notAdjacentTo, exteriorWall, alignsWith, above, below, stack,
                 gridBounds, porchRoofType, null, List.of(), List.of(), null);
        }

        public boolean hasSprinklers() {
            return sprinklerSpacing != null;
        }

        public boolean hasLights() {
            return lightSpacing != null;
        }

        /** Returns true if this room needs solver placement (no explicit position) */
        public boolean needsSolverPlacement() {
            // Rooms with grid bounds have fully defined positions
            if (hasGridBounds()) {
                return false;
            }
            return gridPosition == null && hasConstraints();
        }

        /** Returns true if room has any placement constraints */
        public boolean hasConstraints() {
            return !adjacentTo.isEmpty() || !notAdjacentTo.isEmpty() ||
                   exteriorWall != null || alignsWith != null ||
                   above != null || below != null || stack != null;
        }

        /** Returns true if room has vertical alignment constraint */
        public boolean hasVerticalConstraint() {
            return alignsWith != null || above != null || below != null || stack != null;
        }

        /** Returns true if this room gets position from another room/stack */
        public boolean hasVerticalDependency() {
            return alignsWith != null || above != null || below != null;
        }

        /** Get the room name this room depends on vertically */
        public String getVerticalDependency() {
            if (alignsWith != null) return alignsWith;
            if (above != null) return above;
            if (below != null) return below;
            return null;
        }

        /** Create a copy with solved position */
        public RoomDef withPosition(String newGridPosition) {
            return new RoomDef(type, name, newGridPosition, width, depth, openings,
                              sprinklerSpacing, lightSpacing, adjacentTo, notAdjacentTo,
                              exteriorWall, alignsWith, above, below, stack, gridBounds, porchRoofType,
                              opensTo, zones, exteriorWalls, adjacentUnit);
        }

        /**
         * Phase 47A.3: Create a copy with solved position and filtered exterior walls.
         * Only keeps exterior walls that are in the allowed set (unit's true exterior edges).
         */
        public RoomDef withPositionAndExterior(String newGridPosition, java.util.Set<String> allowedExterior) {
            // Filter exterior wall - only keep if in allowed set
            String filteredExteriorWall = (exteriorWall != null && allowedExterior.contains(exteriorWall.toLowerCase()))
                ? exteriorWall : null;

            // Filter exterior walls list
            List<String> filteredExteriorWalls = exteriorWalls.stream()
                .filter(w -> allowedExterior.contains(w.toLowerCase()))
                .collect(java.util.stream.Collectors.toList());

            return new RoomDef(type, name, newGridPosition, width, depth, openings,
                              sprinklerSpacing, lightSpacing, adjacentTo, notAdjacentTo,
                              filteredExteriorWall, alignsWith, above, below, stack, gridBounds, porchRoofType,
                              opensTo, zones, filteredExteriorWalls, adjacentUnit);
        }

        /** Check if room has cross-unit adjacency constraint (party wall) */
        public boolean hasAdjacentUnit() {
            return adjacentUnit != null && !adjacentUnit.isEmpty();
        }

        /** Check if room has grid bounds reference */
        public boolean hasGridBounds() {
            return gridBounds != null && !gridBounds.isEmpty();
        }

        /** Check if this is a porch */
        public boolean isPorch() {
            return "PORCH".equalsIgnoreCase(type) || "ANJUNG".equalsIgnoreCase(type);
        }

        /** Check if this is an open plan space */
        public boolean isOpenPlan() {
            return "OPEN_PLAN".equalsIgnoreCase(type);
        }

        /** Check if room opens to another (implies door) */
        public boolean hasOpensTo() {
            return opensTo != null && !opensTo.isEmpty();
        }

        /** Get all exterior walls (combines legacy exteriorWall with new exteriorWalls list, deduplicated) */
        public List<String> getAllExteriorWalls() {
            List<String> all = new java.util.ArrayList<>();
            if (exteriorWall != null) all.add(exteriorWall);
            if (exteriorWalls != null) {
                for (String wall : exteriorWalls) {
                    if (!all.contains(wall)) all.add(wall);
                }
            }
            return all;
        }

        /** Get parsed grid bounds */
        public GridBounds getParsedGridBounds() {
            return GridBounds.parse(gridBounds);
        }
    }

    /**
     * Door/Window opening.
     * Phase 28: Extended with typeCode for schedule resolution.
     */
    public record OpeningDef(
        String type,         // DOOR or WINDOW
        String wall,         // north, south, east, west
        String connectsTo,   // room name for doors
        double width,
        double height,
        String typeCode      // Phase 28: D1, D2, W1 etc. for schedule lookup
    ) {
        // Backward-compatible constructor without typeCode
        public OpeningDef(String type, String wall, String connectsTo, double width, double height) {
            this(type, wall, connectsTo, width, height, null);
        }

        /**
         * Check if this opening has a type code for schedule lookup.
         */
        public boolean hasTypeCode() {
            return typeCode != null && !typeCode.isEmpty();
        }

        /**
         * Resolve dimensions from schedule.
         * Returns [width, height] from schedule if typeCode exists, else current dimensions.
         */
        public double[] resolveFromSchedule(BuildingDefinition building) {
            if (!hasTypeCode() || building == null) {
                return new double[]{width, height};
            }
            double[] resolved = "DOOR".equals(type) ?
                building.resolveDoorType(typeCode) :
                building.resolveWindowType(typeCode);
            return resolved != null ? resolved : new double[]{width, height};
        }

        /**
         * Create a new OpeningDef with resolved dimensions from schedule.
         */
        public OpeningDef withResolvedDimensions(BuildingDefinition building) {
            double[] dims = resolveFromSchedule(building);
            return new OpeningDef(type, wall, connectsTo, dims[0], dims[1], typeCode);
        }
    }

    /**
     * Stair flight connecting two storeys.
     */
    public record StairDef(
        String name,
        String gridPosition,
        double width,
        String toStorey      // Target storey name
    ) {}

    /**
     * Landing at top of stairs.
     */
    public record LandingDef(
        String name,
        String gridPosition,
        double width,
        double depth,
        String fromStair     // Source stair name
    ) {}

    // =========================================================================
    // Phase 56: Vertical Circulation Elements (High-Rise Support)
    // =========================================================================

    /**
     * Elevator definition.
     * Types: PASSENGER, FIRE, STRETCHER, ACCESSIBLE, FREIGHT
     */
    public record ElevatorDef(
        String name,
        String type,           // PASSENGER, FIRE, STRETCHER
        String gridPosition,   // Grid position (e.g., C2)
        int carWidthMm,        // Car internal width
        int carDepthMm,        // Car internal depth
        int doorWidthMm,       // Door clear width
        boolean emergencyPower,
        double fireRatingHr
    ) {
        // Convenience constructor for simple elevators
        public ElevatorDef(String name, String type, String gridPosition) {
            this(name, type, gridPosition, 1100, 1400, 900, false, 1.0);
        }
    }

    /**
     * Elevator lobby definition.
     * Pressurized for high-rise per UBBL 179.
     */
    public record ElevatorLobbyDef(
        String name,
        String gridBounds,     // Grid bounds (e.g., C2-D4)
        boolean pressurized,
        double fireRatingHr,
        List<ElevatorDef> elevators
    ) {}

    /**
     * MEP shaft definition.
     * Types: ELECTRICAL, PLUMBING, HVAC, FIRE_PROTECTION
     */
    public record ShaftDef(
        String name,
        String type,           // ELECTRICAL, PLUMBING, HVAC, FIRE_PROTECTION
        String gridPosition,
        double widthM,
        double depthM
    ) {}

    /**
     * Core block containing vertical circulation elements.
     * Encapsulates stairs, elevators, shafts, and lobbies.
     */
    public record CoreDef(
        String name,
        String gridBounds,             // Grid bounds (e.g., C1-D6)
        List<StairDef> stairs,
        List<ElevatorLobbyDef> lobbies,
        List<ShaftDef> shafts
    ) {}

    /**
     * Building roof.
     * Phase 26: Extended with overhang dimension.
     */
    public record RoofDef(
        double pitchDegrees,
        double overhangMm       // Phase 26: Roof overhang in mm (e.g., 600mm)
    ) {
        // Backward-compatible constructor
        public RoofDef(double pitchDegrees) {
            this(pitchDegrees, 0);
        }

        public double overhangMeters() {
            return overhangMm / 1000.0;
        }
    }

    // =========================================================================
    // Phase 26: Construction-Aware Elements
    // =========================================================================

    /**
     * Structural grid definition.
     * Grid lines serve as:
     * - Column placement anchors (at intersections)
     * - Wall alignment guides (along grid lines)
     * - Room boundary references (bounds:A2-B4)
     *
     * Example: GRID { axes: A,B,C,D,E / 2,3,4,5  spacing: 3.0m }
     */
    public record GridDef(
        List<String> xAxes,      // Horizontal axes: A, B, C, D, E
        List<String> yAxes,      // Vertical axes: 2, 3, 4, 5 (can be any labels)
        List<Double> xSpacing,   // Spacing between X axes (in meters), or null for from_pdf
        List<Double> ySpacing,   // Spacing between Y axes (in meters), or null for from_pdf
        boolean fromPdf          // True if spacing extracted from PDF dimensions
    ) {
        /**
         * Get X coordinate for a grid axis label.
         * Returns cumulative distance from origin (axis 0).
         */
        public double getX(String axis) {
            int idx = xAxes.indexOf(axis);
            if (idx < 0) return 0;
            if (xSpacing == null || xSpacing.isEmpty()) return idx * 3.0; // Default 3m
            double x = 0;
            for (int i = 0; i < idx && i < xSpacing.size(); i++) {
                x += xSpacing.get(i);
            }
            return x;
        }

        /**
         * Get Y coordinate for a grid axis label.
         */
        public double getY(String axis) {
            int idx = yAxes.indexOf(axis);
            if (idx < 0) return 0;
            if (ySpacing == null || ySpacing.isEmpty()) return idx * 3.0; // Default 3m
            double y = 0;
            for (int i = 0; i < idx && i < ySpacing.size(); i++) {
                y += ySpacing.get(i);
            }
            return y;
        }

        /**
         * Get grid intersection points (for column placement).
         */
        public List<double[]> getIntersections() {
            List<double[]> points = new java.util.ArrayList<>();
            for (String x : xAxes) {
                for (String y : yAxes) {
                    points.add(new double[]{getX(x), getY(y)});
                }
            }
            return points;
        }
    }

    /**
     * Building envelope definition.
     * Captures construction relationships:
     * - Foundation → building footprint + structural base
     * - Roof overhang → determines gutter/drain position
     * - Drainage system → perimeter drain, gutters, downpipes
     *
     * Phase 28: Extended with foundation and roof-drain correlation.
     */
    public record EnvelopeDef(
        FoundationDef foundation,   // Phase 28
        DrainageDef drainage,
        RoofDef roofSpec            // Phase 28: Reference to building roof for correlation
    ) {
        // Backward-compatible constructor
        public EnvelopeDef(DrainageDef drainage) {
            this(null, drainage, null);
        }

        /**
         * Validate roof-drain correlation.
         * Roof overhang should match perimeter drain offset.
         */
        public boolean validateRoofDrainCorrelation() {
            if (roofSpec == null || drainage == null) return true; // Can't validate
            double roofOverhang = roofSpec.overhangMm();
            double drainOffset = drainage.perimeterDrainOffsetMm();
            // Allow 100mm tolerance
            return Math.abs(roofOverhang - drainOffset) <= 100;
        }

        /**
         * Get correlation status message.
         */
        public String getCorrelationStatus() {
            if (roofSpec == null || drainage == null) {
                return "Correlation: N/A (incomplete data)";
            }
            double roofOverhang = roofSpec.overhangMm();
            double drainOffset = drainage.perimeterDrainOffsetMm();
            if (validateRoofDrainCorrelation()) {
                return String.format("Correlation: OK (roof overhang %.0fmm ≈ drain offset %.0fmm)",
                    roofOverhang, drainOffset);
            } else {
                return String.format("Correlation: MISMATCH (roof overhang %.0fmm ≠ drain offset %.0fmm)",
                    roofOverhang, drainOffset);
            }
        }
    }

    /**
     * Foundation definition.
     * Phase 28: Added to ENVELOPE.
     */
    public record FoundationDef(
        FoundationType type,
        double depthMm,             // Depth below ground level
        double slabThicknessMm,     // Slab thickness (if applicable)
        String material             // Concrete grade, etc.
    ) {
        public enum FoundationType {
            SLAB_ON_GRADE,      // Simple slab foundation
            STRIP_FOOTING,      // Strip/continuous footing
            PAD_FOOTING,        // Individual column footings
            RAFT,               // Raft/mat foundation
            PILED               // Deep foundation with piles
        }

        public double depthMeters() { return depthMm / 1000.0; }
        public double slabThicknessMeters() { return slabThicknessMm / 1000.0; }
    }

    /**
     * Drainage system definition.
     * Models the chain: ROOF overhang → GUTTER → DOWNPIPE → PERIMETER_DRAIN
     */
    public record DrainageDef(
        double perimeterDrainOffsetMm,  // Offset from exterior wall (= roof overhang)
        String connectsTo,               // e.g., "municipal_drain"
        List<String> downpipeLocations   // Grid positions: A2, A5, E2, E5
    ) {
        public double perimeterDrainOffsetMeters() {
            return perimeterDrainOffsetMm / 1000.0;
        }
    }

    /**
     * Porch roof attachment type.
     */
    public enum PorchRoofType {
        ATTACHED,   // Shares main roof structure
        SEPARATE    // Independent roof with own pitch
    }

    /**
     * Grid bounds for room placement.
     * Parses "A2-B4" into start/end grid references.
     */
    public record GridBounds(
        String startX, String startY,  // e.g., A, 2
        String endX, String endY       // e.g., B, 4
    ) {
        /**
         * Parse "A2-B4" format into GridBounds.
         */
        public static GridBounds parse(String bounds) {
            if (bounds == null || !bounds.contains("-")) return null;
            String[] parts = bounds.split("-");
            if (parts.length != 2) return null;

            // Parse start: A2 → A, 2
            String start = parts[0].trim();
            String end = parts[1].trim();

            // Extract letter (X axis) and number (Y axis)
            String startX = start.replaceAll("[^A-Za-z]", "");
            String startY = start.replaceAll("[^0-9]", "");
            String endX = end.replaceAll("[^A-Za-z]", "");
            String endY = end.replaceAll("[^0-9]", "");

            return new GridBounds(startX, startY, endX, endY);
        }

        /**
         * Calculate room dimensions from grid.
         */
        public double[] getDimensions(GridDef grid) {
            if (grid == null) return new double[]{0, 0};
            double x1 = grid.getX(startX);
            double x2 = grid.getX(endX);
            double y1 = grid.getY(startY);
            double y2 = grid.getY(endY);
            return new double[]{Math.abs(x2 - x1), Math.abs(y2 - y1)};
        }
    }

    // =========================================================================
    // Phase 28: SCHEDULE Registry
    // =========================================================================

    /**
     * Schedule registry for doors or windows.
     * Provides type-code to dimension lookup.
     *
     * DSL Syntax:
     *   SCHEDULE doors {
     *       D1: 900x2100 "Metal frame solid timber"
     *       D2: 750x2100 "Flush door gloss paint"
     *   }
     */
    public record ScheduleDef(
        String category,                    // "doors" or "windows"
        List<ScheduleEntryDef> entries
    ) {
        /**
         * Resolve type code to dimensions.
         * @param typeCode e.g. "D1", "W2"
         * @return [width, height] in meters, or null if not found
         */
        public double[] resolve(String typeCode) {
            if (entries == null) return null;
            for (ScheduleEntryDef entry : entries) {
                if (entry.typeCode().equalsIgnoreCase(typeCode)) {
                    return new double[]{entry.widthMm() / 1000.0, entry.heightMm() / 1000.0};
                }
            }
            return null;
        }

        /**
         * Get entry by type code.
         */
        public ScheduleEntryDef getEntry(String typeCode) {
            if (entries == null) return null;
            for (ScheduleEntryDef entry : entries) {
                if (entry.typeCode().equalsIgnoreCase(typeCode)) {
                    return entry;
                }
            }
            return null;
        }

        /**
         * Check if type code exists in schedule.
         */
        public boolean hasType(String typeCode) {
            return resolve(typeCode) != null;
        }
    }

    /**
     * Single entry in a schedule.
     * Example: D1: 900x2100 "Metal frame solid timber"
     */
    public record ScheduleEntryDef(
        String typeCode,    // D1, D2, W1, W2, etc.
        double widthMm,     // Width in millimeters
        double heightMm,    // Height in millimeters
        String description  // Material/style description
    ) {
        /**
         * Get width in meters.
         */
        public double widthMeters() {
            return widthMm / 1000.0;
        }

        /**
         * Get height in meters.
         */
        public double heightMeters() {
            return heightMm / 1000.0;
        }
    }
}
