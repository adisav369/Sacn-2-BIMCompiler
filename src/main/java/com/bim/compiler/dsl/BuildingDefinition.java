package com.bim.compiler.dsl;

import java.util.List;

/**
 * Parsed BUILDING definition from DSL.
 * Contains multiple storeys with vertical connections (stairs).
 */
public record BuildingDefinition(
    String name,
    List<StoreyDef> storeys,
    RoofDef roof
) {
    /**
     * Individual storey within building.
     */
    public record StoreyDef(
        String name,
        int level,           // 0 = ground, 1 = first floor, etc.
        double height,       // Floor-to-floor height in meters
        List<RoomDef> rooms,
        List<StairDef> stairs,
        List<LandingDef> landings
    ) {}

    /**
     * Room definition (LIVING, BEDROOM, DEPARTURE_LOUNGE, GATE, etc.)
     * Supports both explicit positioning (at:A1) and constraint-based placement.
     */
    public record RoomDef(
        String type,         // LIVING, BEDROOM, DEPARTURE_LOUNGE, GATE, etc.
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
        String exteriorWall,         // Direction (north/south/east/west) or null
        // Phase 16: Vertical constraint
        String alignsWith,           // Room name to align with (cross-storey)
        // Phase 17: Extended vertical vocabulary
        String above,                // Room name this must be above (higher storey)
        String below,                // Room name this must be below (lower storey)
        String stack                 // Named vertical stack this room belongs to
    ) {
        // Backward-compatible constructor without MEP or constraints
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings) {
            this(type, name, gridPosition, width, depth, openings, null, null,
                 List.of(), List.of(), null, null, null, null, null);
        }

        // Constructor with sprinklers only (backward compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, null,
                 List.of(), List.of(), null, null, null, null, null);
        }

        // Constructor with MEP but no constraints (backward compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 List.of(), List.of(), null, null, null, null, null);
        }

        // Constructor without alignsWith (backward compat Phase 15)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing,
                       List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 adjacentTo, notAdjacentTo, exteriorWall, null, null, null, null);
        }

        // Constructor with alignsWith only (Phase 16 compat)
        public RoomDef(String type, String name, String gridPosition,
                       double width, double depth, List<OpeningDef> openings,
                       Double sprinklerSpacing, Double lightSpacing,
                       List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall,
                       String alignsWith) {
            this(type, name, gridPosition, width, depth, openings, sprinklerSpacing, lightSpacing,
                 adjacentTo, notAdjacentTo, exteriorWall, alignsWith, null, null, null);
        }

        public boolean hasSprinklers() {
            return sprinklerSpacing != null;
        }

        public boolean hasLights() {
            return lightSpacing != null;
        }

        /** Returns true if this room needs solver placement (no explicit position) */
        public boolean needsSolverPlacement() {
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
                              exteriorWall, alignsWith, above, below, stack);
        }
    }

    /**
     * Door/Window opening.
     */
    public record OpeningDef(
        String type,         // DOOR or WINDOW
        String wall,         // north, south, east, west
        String connectsTo,   // room name for doors
        double width,
        double height
    ) {}

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

    /**
     * Building roof.
     */
    public record RoofDef(
        double pitchDegrees
    ) {}
}
