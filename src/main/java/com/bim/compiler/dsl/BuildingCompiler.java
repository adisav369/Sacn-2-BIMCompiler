package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.solver.SpaceSolver;
import com.bim.compiler.solver.SpaceSolver.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles BuildingDefinition into BuildingSpec with geometry.
 *
 * IRC 2021 Stair Requirements:
 * - Max riser height: 196mm (7.75")
 * - Min tread depth: 254mm (10")
 * - Min width: 914mm (36") for residential
 *
 * Slab overlap pattern (from Terminal G8):
 * - Intermediate slabs overlap walls by 175-250mm
 *
 * Layer 3 Integration (Phase 15):
 * - Detects rooms without explicit positions (no at: clause)
 * - Invokes SpaceSolver to find valid positions from constraints
 * - Injects solved positions before generating geometry
 */
public class BuildingCompiler {

    // IRC 2021 Stair Constants
    private static final double MAX_RISER = 0.196;   // 196mm max
    private static final double MIN_TREAD = 0.254;   // 254mm min
    private static final double MIN_STAIR_WIDTH = 0.914; // 914mm min

    // Construction Constants
    private static final double WALL_THICKNESS = 0.15;  // 150mm
    private static final double SLAB_THICKNESS = 0.15;  // 150mm
    private static final double SLAB_OVERLAP = 0.2;     // 200mm overlap on walls

    // Layer 3: Solver constants
    private static final int DEFAULT_GRID_WIDTH = 20;   // Max building width for solver
    private static final int DEFAULT_GRID_HEIGHT = 20;  // Max building depth for solver

    // Phase 15B: Door/Window defaults
    private static final double DEFAULT_DOOR_WIDTH = 0.9;    // 900mm standard door
    private static final double DEFAULT_DOOR_HEIGHT = 2.1;   // 2100mm standard height
    private static final double DEFAULT_WINDOW_WIDTH = 1.2;  // 1200mm window
    private static final double DEFAULT_WINDOW_HEIGHT = 1.2; // 1200mm window
    private static final double DEFAULT_SILL_HEIGHT = 0.9;   // 900mm sill height
    private static final double TOLERANCE = 0.005;           // 5mm tolerance

    /**
     * Compile building definition to spec.
     * If any storey has rooms without positions, invokes SpaceSolver first.
     */
    public static BuildingSpec compile(BuildingDefinition def) {
        // Layer 3: Check if solver is needed
        BuildingDefinition resolvedDef = resolveConstraints(def);
        def = resolvedDef;  // Use resolved definition
        List<StoreySpec> storeySpecs = new ArrayList<>();
        double currentZ = 0.0;

        for (int i = 0; i < def.storeys().size(); i++) {
            StoreyDef storey = def.storeys().get(i);
            boolean isGround = (i == 0);
            boolean isTop = (i == def.storeys().size() - 1);

            StoreySpec spec = compileStorey(storey, currentZ, isGround, isTop, def);
            storeySpecs.add(spec);

            currentZ += storey.height();
        }

        // Compile roof at top storey level
        RoofSpec roofSpec = null;
        if (def.roof() != null && !def.storeys().isEmpty()) {
            StoreyDef topStorey = def.storeys().get(def.storeys().size() - 1);
            roofSpec = compileRoof(def.roof(), def.name(), currentZ, topStorey);
        }

        return new BuildingSpec(def.name(), storeySpecs, roofSpec);
    }

    // =========================================================================
    // Layer 3: Constraint Resolution (Phase 15)
    // =========================================================================

    /**
     * Check if any rooms need solver placement, and resolve constraints if needed.
     * Phase 16: Supports cross-storey alignment via solvedPositions map.
     * Phase 17: Adds above:/below:/stack: constraints.
     */
    private static BuildingDefinition resolveConstraints(BuildingDefinition def) {
        boolean needsSolver = false;
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                if (room.needsSolverPlacement()) {
                    needsSolver = true;
                    break;
                }
            }
            if (needsSolver) break;
        }

        if (!needsSolver) {
            return def;  // No solver needed, use original definition
        }

        System.out.println("[SOLVER] Constraint-based room placement detected");

        // Phase 16+17: Track solved positions across storeys for vertical alignment
        Map<String, GridPosition> allSolvedPositions = new HashMap<>();

        // Phase 17: Track named stacks - first room sets position, others copy
        Map<String, GridPosition> stackPositions = new HashMap<>();

        // Build storey index map for above/below validation
        Map<String, Integer> roomStoreyLevel = new HashMap<>();
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                roomStoreyLevel.put(room.name(), storey.level());
            }
        }

        // Phase 17: Pre-scan for explicit positions (for below: constraint support)
        // Rooms with explicit at: positions are known before solver runs
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                if (room.gridPosition() != null && !room.needsSolverPlacement()) {
                    int[] coords = parseGridPosition(room.gridPosition());
                    allSolvedPositions.put(room.name(), new GridPosition(coords[0], coords[1]));
                }
            }
        }

        // Resolve each storey (in level order - lower storeys first)
        List<StoreyDef> resolvedStoreys = new ArrayList<>();
        for (StoreyDef storey : def.storeys()) {
            StoreyDef resolved = resolveStoreyConstraints(storey, allSolvedPositions,
                                                           stackPositions, roomStoreyLevel);
            resolvedStoreys.add(resolved);
        }

        return new BuildingDefinition(def.name(), resolvedStoreys, def.roof());
    }

    /**
     * Resolve constraints for a single storey.
     * Phase 16: Accepts allSolvedPositions for cross-storey alignment.
     * Phase 17: Adds above:/below:/stack: constraint handling.
     */
    private static StoreyDef resolveStoreyConstraints(StoreyDef storey,
                                                       Map<String, GridPosition> allSolvedPositions,
                                                       Map<String, GridPosition> stackPositions,
                                                       Map<String, Integer> roomStoreyLevel) {
        // Check if this storey has any rooms needing solver
        boolean hasConstrainedRooms = storey.rooms().stream()
            .anyMatch(RoomDef::needsSolverPlacement);

        if (!hasConstrainedRooms) {
            return storey;
        }

        System.out.println("[SOLVER] Resolving storey: " + storey.name());

        // Phase 16+17: Handle rooms with vertical dependency separately
        // These get their position from the target room/stack
        List<RoomDef> verticallyDependent = new ArrayList<>();
        List<RoomDef> needsSolving = new ArrayList<>();

        for (RoomDef room : storey.rooms()) {
            if (room.needsSolverPlacement()) {
                // Check if this room has a vertical dependency that's already resolved
                boolean hasDependency = false;

                // aligns: constraint
                if (room.alignsWith() != null && allSolvedPositions.containsKey(room.alignsWith())) {
                    hasDependency = true;
                }

                // Phase 17: above: constraint (this room above target, so target must be solved)
                if (room.above() != null && allSolvedPositions.containsKey(room.above())) {
                    // Verify this storey is actually above the target room's storey
                    int targetLevel = roomStoreyLevel.getOrDefault(room.above(), -1);
                    if (storey.level() > targetLevel) {
                        hasDependency = true;
                    } else {
                        throw new RuntimeException(
                            "Room '" + room.name() + "' has above:" + room.above() +
                            " but is not on a higher storey (level " + storey.level() +
                            " vs target level " + targetLevel + ")");
                    }
                }

                // Phase 17: below: constraint (this room below target, so target must be solved)
                if (room.below() != null && allSolvedPositions.containsKey(room.below())) {
                    // Verify this storey is actually below the target room's storey
                    int targetLevel = roomStoreyLevel.getOrDefault(room.below(), Integer.MAX_VALUE);
                    if (storey.level() < targetLevel) {
                        hasDependency = true;
                    } else {
                        throw new RuntimeException(
                            "Room '" + room.name() + "' has below:" + room.below() +
                            " but is not on a lower storey (level " + storey.level() +
                            " vs target level " + targetLevel + ")");
                    }
                }

                // Phase 17: stack: constraint - check if stack position exists
                if (room.stack() != null && stackPositions.containsKey(room.stack())) {
                    hasDependency = true;
                }

                if (hasDependency) {
                    verticallyDependent.add(room);
                } else {
                    needsSolving.add(room);
                }
            }
        }

        // Build constraints list for solver (excluding vertically dependent rooms)
        List<RoomConstraint> constraints = new ArrayList<>();
        Map<String, RoomDef> roomDefMap = new HashMap<>();

        for (RoomDef room : storey.rooms()) {
            roomDefMap.put(room.name(), room);

            // Only add to solver if needs placement and not vertically dependent
            if (room.needsSolverPlacement() && !verticallyDependent.contains(room)) {
                constraints.add(new RoomConstraint(
                    room.name(),
                    (int) Math.ceil(room.width()),
                    (int) Math.ceil(room.depth()),
                    room.adjacentTo(),
                    room.notAdjacentTo(),
                    room.exteriorWall()
                ));
            }
        }

        // Invoke solver for rooms that need it
        Map<String, GridPosition> solvedPositions = new HashMap<>();
        if (!constraints.isEmpty()) {
            SpaceSolver solver = new SpaceSolver();
            SolvedLayout layout = solver.solve(constraints, DEFAULT_GRID_WIDTH, DEFAULT_GRID_HEIGHT);

            if (!layout.feasible()) {
                throw new RuntimeException(
                    "Cannot satisfy constraints for storey '" + storey.name() + "': " +
                    layout.failureReason());
            }

            System.out.println("[SOLVER] Solution found in " + layout.solveTimeMs() + "ms");
            SpaceSolver.printLayout(layout, constraints);
            solvedPositions.putAll(layout.positions());
        }

        // Apply solved positions to rooms
        List<RoomDef> resolvedRooms = new ArrayList<>();
        for (RoomDef room : storey.rooms()) {
            if (verticallyDependent.contains(room)) {
                // Get position from the vertical dependency
                GridPosition dependentPos = null;
                String dependencyType = null;
                String dependencyTarget = null;

                if (room.alignsWith() != null && allSolvedPositions.containsKey(room.alignsWith())) {
                    dependentPos = allSolvedPositions.get(room.alignsWith());
                    dependencyType = "aligns";
                    dependencyTarget = room.alignsWith();
                } else if (room.above() != null && allSolvedPositions.containsKey(room.above())) {
                    dependentPos = allSolvedPositions.get(room.above());
                    dependencyType = "above";
                    dependencyTarget = room.above();
                } else if (room.below() != null && allSolvedPositions.containsKey(room.below())) {
                    dependentPos = allSolvedPositions.get(room.below());
                    dependencyType = "below";
                    dependencyTarget = room.below();
                } else if (room.stack() != null && stackPositions.containsKey(room.stack())) {
                    dependentPos = stackPositions.get(room.stack());
                    dependencyType = "stack";
                    dependencyTarget = room.stack();
                }

                if (dependentPos != null) {
                    String gridRef = SpaceSolver.toGridRef(dependentPos);
                    System.out.println("[SOLVER] " + room.name() + " -> " + gridRef +
                                     " (" + dependencyType + ": " + dependencyTarget + ")");
                    resolvedRooms.add(room.withPosition(gridRef));

                    // Track this position for potential future alignment
                    allSolvedPositions.put(room.name(), dependentPos);

                    // If room belongs to a stack, update stack position
                    if (room.stack() != null && !stackPositions.containsKey(room.stack())) {
                        stackPositions.put(room.stack(), dependentPos);
                    }
                } else {
                    // Shouldn't happen, but fallback
                    resolvedRooms.add(room);
                }
            } else if (room.needsSolverPlacement()) {
                GridPosition pos = solvedPositions.get(room.name());
                String gridRef = SpaceSolver.toGridRef(pos);
                System.out.println("[SOLVER] " + room.name() + " -> " + gridRef +
                                 " (" + pos.x() + "," + pos.y() + ")");
                resolvedRooms.add(room.withPosition(gridRef));

                // Track position for upper storey alignment
                allSolvedPositions.put(room.name(), pos);

                // Phase 17: If room belongs to a stack, register stack position
                if (room.stack() != null && !stackPositions.containsKey(room.stack())) {
                    stackPositions.put(room.stack(), pos);
                    System.out.println("[SOLVER] Stack '" + room.stack() + "' position set to " + gridRef);
                }
            } else {
                resolvedRooms.add(room);
            }
        }

        return new StoreyDef(
            storey.name(), storey.level(), storey.height(),
            resolvedRooms, storey.stairs(), storey.landings()
        );
    }

    /**
     * Parse grid position (e.g., "A1" -> [0, 0], "C5" -> [2, 4]).
     * Column letter = X position (meters), Row number = Y position (meters).
     */
    private static int[] parseGridPosition(String gridPos) {
        if (gridPos == null || gridPos.isEmpty()) {
            return new int[]{0, 0};
        }

        // Handle range positions like "A1-B2" by using start
        if (gridPos.contains("-")) {
            gridPos = gridPos.split("-")[0];
        }

        // Extract column letter(s) and row number
        int i = 0;
        while (i < gridPos.length() && Character.isLetter(gridPos.charAt(i))) {
            i++;
        }

        String colPart = gridPos.substring(0, i).toUpperCase();
        String rowPart = gridPos.substring(i);

        // Convert column letters to number (A=0, B=1, ..., Z=25, AA=26, etc.)
        int col = 0;
        for (char c : colPart.toCharArray()) {
            col = col * 26 + (c - 'A');
        }

        // Row number (1-indexed in DSL, 0-indexed internally)
        int row = 0;
        if (!rowPart.isEmpty()) {
            row = Integer.parseInt(rowPart) - 1;
        }

        return new int[]{col, row};
    }

    private static StoreySpec compileStorey(StoreyDef storey, double baseZ,
                                            boolean isGround, boolean isTop,
                                            BuildingDefinition building) {
        List<WallAssemblySpec> walls = new ArrayList<>();
        List<RoomSpec> rooms = new ArrayList<>();
        List<StairSpec> stairs = new ArrayList<>();
        List<DoorSpec> doors = new ArrayList<>();
        List<WindowSpec> windows = new ArrayList<>();
        List<LandingSpec> landings = new ArrayList<>();
        List<SprinklerSpec> sprinklers = new ArrayList<>();  // Phase 14B
        List<LightSpec> lights = new ArrayList<>();          // Phase 14B
        SlabSpec slab = null;

        // Calculate storey bounds from rooms
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        // Track room bounds for MEP generation
        record RoomMEP(String name, double minX, double minY, double maxX, double maxY,
                       Double sprinklerSpacing, Double lightSpacing) {}
        List<RoomMEP> roomsWithMEP = new ArrayList<>();

        // =====================================================================
        // Phase 21B: Two-pass room layout with adjacency snapping
        // Pass 1: Calculate initial bounds from grid positions
        // Pass 2: Snap adjacent rooms together to eliminate gaps
        // =====================================================================

        // Pass 1: Calculate initial room bounds
        record MutableBounds(String name, double[] bounds) {} // [minX, minY, maxX, maxY]
        Map<String, double[]> roomBounds = new HashMap<>();
        double currentX = 0;

        for (RoomDef room : storey.rooms()) {
            double roomMinX, roomMinY;

            if (room.gridPosition() != null) {
                int[] coords = parseGridPosition(room.gridPosition());
                roomMinX = coords[0];
                roomMinY = coords[1];
            } else {
                roomMinX = currentX;
                roomMinY = 0;
            }

            double roomMaxX = roomMinX + room.width();
            double roomMaxY = roomMinY + room.depth();

            roomBounds.put(room.name(), new double[]{roomMinX, roomMinY, roomMaxX, roomMaxY});
            currentX = roomMaxX;
        }

        // Pass 2: Snap adjacent rooms together
        // When rooms have adjacent: constraint, ensure they physically touch
        for (RoomDef room : storey.rooms()) {
            for (String adjacentName : room.adjacentTo()) {
                double[] myBounds = roomBounds.get(room.name());
                double[] theirBounds = roomBounds.get(adjacentName);

                if (myBounds != null && theirBounds != null) {
                    snapAdjacentRooms(myBounds, theirBounds);
                }
            }
        }

        // Pass 3: Create RoomSpecs with snapped bounds
        for (RoomDef room : storey.rooms()) {
            double[] bounds = roomBounds.get(room.name());
            double roomMinX = bounds[0], roomMinY = bounds[1];
            double roomMaxX = bounds[2], roomMaxY = bounds[3];

            rooms.add(new RoomSpec(
                room.type(), room.name(),
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                baseZ, baseZ + storey.height(),
                compileOpenings(room.openings())
            ));

            // Track for MEP generation (Phase 14B)
            if (room.hasSprinklers() || room.hasLights()) {
                roomsWithMEP.add(new RoomMEP(
                    room.name(), roomMinX, roomMinY, roomMaxX, roomMaxY,
                    room.sprinklerSpacing(), room.lightSpacing()
                ));
            }

            // Generate doors and windows from room openings
            for (OpeningDef opening : room.openings()) {
                double openingX, openingY;
                // Center opening on wall
                switch (opening.wall()) {
                    case "south" -> { openingX = roomMinX + room.width() / 2 - opening.width() / 2; openingY = roomMinY; }
                    case "north" -> { openingX = roomMinX + room.width() / 2 - opening.width() / 2; openingY = roomMaxY; }
                    case "west" -> { openingX = roomMinX; openingY = roomMinY + room.depth() / 2 - opening.width() / 2; }
                    case "east" -> { openingX = roomMaxX; openingY = roomMinY + room.depth() / 2 - opening.width() / 2; }
                    default -> { openingX = roomMinX; openingY = roomMinY; }
                }

                if (opening.type().equals("DOOR")) {
                    doors.add(new DoorSpec(
                        room.name() + "_door_" + opening.wall(),
                        room.name(), opening.wall(),
                        openingX, openingY, baseZ,
                        opening.width(), opening.height()
                    ));
                } else if (opening.type().equals("WINDOW")) {
                    double sillHeight = 0.9; // 900mm sill height
                    windows.add(new WindowSpec(
                        room.name() + "_window_" + opening.wall(),
                        room.name(), opening.wall(),
                        openingX, openingY, baseZ + sillHeight,
                        opening.width(), opening.height(),
                        sillHeight
                    ));
                }
            }

            minX = Math.min(minX, roomMinX);
            maxX = Math.max(maxX, roomMaxX);
            minY = Math.min(minY, roomMinY);
            maxY = Math.max(maxY, roomMaxY);

            currentX = roomMaxX;
        }

        // Add stair footprint to bounds
        for (StairDef stair : storey.stairs()) {
            double stairRun = calculateStairRun(storey.height());
            // Place stair after rooms
            double stairX = maxX;
            double stairY = 0;

            stairs.add(compileStair(stair, stairX, stairY, baseZ, storey.height()));

            maxX = stairX + stair.width();
            maxY = Math.max(maxY, stairRun);
        }

        // Generate landings at stair top
        for (LandingDef landing : storey.landings()) {
            // Find associated stair from lower storey
            double landingX = maxX - landing.width();
            double landingY = 0;
            double landingZ = baseZ; // Landing is at this storey's floor level
            double landingThickness = 0.15; // 150mm

            landings.add(new LandingSpec(
                landing.name(),
                landing.fromStair(),
                landingX, landingY, landingZ - landingThickness,
                landingX + landing.width(), landingY + landing.depth(), landingZ
            ));
        }

        // Generate slab
        if (isGround) {
            // Foundation slab
            slab = new SlabSpec(
                "FOUNDATION", "Foundation Slab",
                minX - SLAB_OVERLAP, minY - SLAB_OVERLAP,
                maxX + SLAB_OVERLAP, maxY + SLAB_OVERLAP,
                baseZ - SLAB_THICKNESS, baseZ
            );
        } else {
            // Intermediate floor slab
            slab = new SlabSpec(
                "FLOOR", "Floor Slab Level " + storey.level(),
                minX - SLAB_OVERLAP, minY - SLAB_OVERLAP,
                maxX + SLAB_OVERLAP, maxY + SLAB_OVERLAP,
                baseZ - SLAB_THICKNESS, baseZ
            );
        }

        // Generate perimeter walls
        walls.add(compileWall("SOUTH", minX, minY, maxX, minY,
            baseZ, baseZ + storey.height(), storey.name()));
        walls.add(compileWall("NORTH", minX, maxY, maxX, maxY,
            baseZ, baseZ + storey.height(), storey.name()));
        walls.add(compileWall("WEST", minX, minY, minX, maxY,
            baseZ, baseZ + storey.height(), storey.name()));
        walls.add(compileWall("EAST", maxX, minY, maxX, maxY,
            baseZ, baseZ + storey.height(), storey.name()));

        // =====================================================================
        // Phase 15B: Interior Walls + Auto-Doors + Auto-Windows
        // =====================================================================

        // Build room bounds map for interior wall detection
        Map<String, RoomBounds> roomBoundsMap = new HashMap<>();
        Map<String, RoomDef> roomDefMap = new HashMap<>();
        for (int i = 0; i < storey.rooms().size(); i++) {
            RoomDef roomDef = storey.rooms().get(i);
            RoomSpec roomSpec = rooms.get(i);
            roomBoundsMap.put(roomDef.name(), new RoomBounds(
                roomSpec.minX(), roomSpec.minY(), roomSpec.maxX(), roomSpec.maxY()
            ));
            roomDefMap.put(roomDef.name(), roomDef);
        }

        // Find shared edges and generate interior walls
        List<RoomDef> roomList = storey.rooms();
        for (int i = 0; i < roomList.size(); i++) {
            for (int j = i + 1; j < roomList.size(); j++) {
                RoomDef room1 = roomList.get(i);
                RoomDef room2 = roomList.get(j);

                RoomBounds bounds1 = roomBoundsMap.get(room1.name());
                RoomBounds bounds2 = roomBoundsMap.get(room2.name());

                SharedEdge edge = findSharedEdge(bounds1, bounds2);
                if (edge != null) {
                    // Generate interior wall along shared edge
                    String wallName = "INTERIOR_" + room1.name() + "_" + room2.name();
                    walls.add(compileWall(wallName,
                        edge.x1(), edge.y1(), edge.x2(), edge.y2(),
                        baseZ, baseZ + storey.height(), storey.name()));

                    // Check if rooms have ADJACENT constraint - if so, auto-place door
                    boolean areAdjacent = room1.adjacentTo().contains(room2.name()) ||
                                          room2.adjacentTo().contains(room1.name());

                    if (areAdjacent) {
                        // Place door at center of shared edge
                        double doorX, doorY;
                        String doorWall;

                        if (edge.isVertical()) {
                            // Vertical wall (north-south oriented)
                            doorX = edge.x1();
                            doorY = (edge.y1() + edge.y2()) / 2 - DEFAULT_DOOR_WIDTH / 2;
                            doorWall = edge.x1() == bounds1.maxX() ? "east" : "west";
                        } else {
                            // Horizontal wall (east-west oriented)
                            doorX = (edge.x1() + edge.x2()) / 2 - DEFAULT_DOOR_WIDTH / 2;
                            doorY = edge.y1();
                            doorWall = edge.y1() == bounds1.maxY() ? "north" : "south";
                        }

                        doors.add(new DoorSpec(
                            room1.name() + "_to_" + room2.name() + "_door",
                            room1.name(), doorWall,
                            doorX, doorY, baseZ,
                            DEFAULT_DOOR_WIDTH, DEFAULT_DOOR_HEIGHT
                        ));
                    }
                }
            }
        }

        // Generate walls for exposed room edges (not at perimeter, not shared with other room)
        // This ensures all rooms are fully enclosed
        Set<String> coveredEdges = new HashSet<>(); // Track edges already covered

        // Mark perimeter edges as covered
        for (RoomDef room : roomList) {
            RoomBounds bounds = roomBoundsMap.get(room.name());
            if (Math.abs(bounds.minY() - minY) < TOLERANCE) coveredEdges.add(room.name() + "_south");
            if (Math.abs(bounds.maxY() - maxY) < TOLERANCE) coveredEdges.add(room.name() + "_north");
            if (Math.abs(bounds.minX() - minX) < TOLERANCE) coveredEdges.add(room.name() + "_west");
            if (Math.abs(bounds.maxX() - maxX) < TOLERANCE) coveredEdges.add(room.name() + "_east");
        }

        // Mark shared edges as covered
        for (int i = 0; i < roomList.size(); i++) {
            for (int j = i + 1; j < roomList.size(); j++) {
                RoomBounds b1 = roomBoundsMap.get(roomList.get(i).name());
                RoomBounds b2 = roomBoundsMap.get(roomList.get(j).name());
                SharedEdge edge = findSharedEdge(b1, b2);
                if (edge != null) {
                    // Determine which edges are shared
                    if (edge.isVertical()) {
                        if (Math.abs(edge.x1() - b1.maxX()) < TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_east");
                        if (Math.abs(edge.x1() - b1.minX()) < TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_west");
                        if (Math.abs(edge.x1() - b2.maxX()) < TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_east");
                        if (Math.abs(edge.x1() - b2.minX()) < TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_west");
                    } else {
                        if (Math.abs(edge.y1() - b1.maxY()) < TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_north");
                        if (Math.abs(edge.y1() - b1.minY()) < TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_south");
                        if (Math.abs(edge.y1() - b2.maxY()) < TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_north");
                        if (Math.abs(edge.y1() - b2.minY()) < TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_south");
                    }
                }
            }
        }

        // Generate partition walls for uncovered edges
        for (RoomDef room : roomList) {
            RoomBounds bounds = roomBoundsMap.get(room.name());

            // North edge
            if (!coveredEdges.contains(room.name() + "_north")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_north",
                    bounds.minX(), bounds.maxY(), bounds.maxX(), bounds.maxY(),
                    baseZ, baseZ + storey.height(), storey.name()));
            }
            // South edge
            if (!coveredEdges.contains(room.name() + "_south")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_south",
                    bounds.minX(), bounds.minY(), bounds.maxX(), bounds.minY(),
                    baseZ, baseZ + storey.height(), storey.name()));
            }
            // East edge
            if (!coveredEdges.contains(room.name() + "_east")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_east",
                    bounds.maxX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
                    baseZ, baseZ + storey.height(), storey.name()));
            }
            // West edge
            if (!coveredEdges.contains(room.name() + "_west")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_west",
                    bounds.minX(), bounds.minY(), bounds.minX(), bounds.maxY(),
                    baseZ, baseZ + storey.height(), storey.name()));
            }
        }

        // Auto-place windows for rooms with EXTERIOR constraints (if not already specified)
        for (RoomDef room : storey.rooms()) {
            if (room.exteriorWall() != null) {
                String extWall = room.exteriorWall().toLowerCase();

                // Check if room already has a window on that wall
                boolean hasWindowOnWall = room.openings().stream()
                    .anyMatch(o -> o.type().equals("WINDOW") && o.wall().equalsIgnoreCase(extWall));

                if (!hasWindowOnWall) {
                    // Auto-place window on exterior wall
                    RoomBounds bounds = roomBoundsMap.get(room.name());
                    double windowX, windowY;

                    switch (extWall) {
                        case "south" -> {
                            windowX = bounds.minX() + (bounds.maxX() - bounds.minX()) / 2 - DEFAULT_WINDOW_WIDTH / 2;
                            windowY = minY;  // Building south edge
                        }
                        case "north" -> {
                            windowX = bounds.minX() + (bounds.maxX() - bounds.minX()) / 2 - DEFAULT_WINDOW_WIDTH / 2;
                            windowY = maxY;  // Building north edge
                        }
                        case "west" -> {
                            windowX = minX;  // Building west edge
                            windowY = bounds.minY() + (bounds.maxY() - bounds.minY()) / 2 - DEFAULT_WINDOW_WIDTH / 2;
                        }
                        case "east" -> {
                            windowX = maxX;  // Building east edge
                            windowY = bounds.minY() + (bounds.maxY() - bounds.minY()) / 2 - DEFAULT_WINDOW_WIDTH / 2;
                        }
                        default -> {
                            windowX = bounds.minX();
                            windowY = bounds.minY();
                        }
                    }

                    windows.add(new WindowSpec(
                        room.name() + "_auto_window_" + extWall,
                        room.name(), extWall,
                        windowX, windowY, baseZ + DEFAULT_SILL_HEIGHT,
                        DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT,
                        DEFAULT_SILL_HEIGHT
                    ));
                }
            }
        }
        // End Phase 15B

        // Generate MEP elements for rooms (Phase 14B)
        double ceilingZ = baseZ + storey.height() - 0.05;  // 50mm below ceiling
        for (var roomMEP : roomsWithMEP) {
            double roomWidth = roomMEP.maxX() - roomMEP.minX();
            double roomDepth = roomMEP.maxY() - roomMEP.minY();

            // Generate sprinklers
            if (roomMEP.sprinklerSpacing() != null) {
                double spacing = roomMEP.sprinklerSpacing();
                int numX = (int) Math.ceil(roomWidth / spacing);
                int numY = (int) Math.ceil(roomDepth / spacing);
                double startX = roomMEP.minX() + (roomWidth - (numX - 1) * spacing) / 2;
                double startY = roomMEP.minY() + (roomDepth - (numY - 1) * spacing) / 2;

                int sprinklerIndex = 0;
                for (int ix = 0; ix < numX; ix++) {
                    for (int iy = 0; iy < numY; iy++) {
                        double x = startX + ix * spacing;
                        double y = startY + iy * spacing;
                        sprinklers.add(new SprinklerSpec(
                            roomMEP.name() + "_sprinkler_" + (++sprinklerIndex),
                            roomMEP.name(),
                            x, y, ceilingZ,
                            "pendant",
                            spacing
                        ));
                    }
                }
            }

            // Generate lights
            if (roomMEP.lightSpacing() != null) {
                double spacing = roomMEP.lightSpacing();
                int numX = (int) Math.ceil(roomWidth / spacing);
                int numY = (int) Math.ceil(roomDepth / spacing);
                double startX = roomMEP.minX() + (roomWidth - (numX - 1) * spacing) / 2;
                double startY = roomMEP.minY() + (roomDepth - (numY - 1) * spacing) / 2;

                int lightIndex = 0;
                for (int ix = 0; ix < numX; ix++) {
                    for (int iy = 0; iy < numY; iy++) {
                        double x = startX + ix * spacing;
                        double y = startY + iy * spacing;
                        lights.add(new LightSpec(
                            roomMEP.name() + "_light_" + (++lightIndex),
                            roomMEP.name(),
                            x, y, ceilingZ,
                            "recessed",
                            spacing
                        ));
                    }
                }
            }
        }

        return new StoreySpec(
            storey.name(), storey.level(), baseZ, storey.height(),
            slab, walls, rooms, stairs, doors, windows, landings, sprinklers, lights
        );
    }

    private static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName) {
        // Calculate wall dimensions
        double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double height = maxZ - minZ;

        // Wall extends thickness perpendicular to line
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);

        // Normal direction (perpendicular, pointing inward)
        double nx = -dy / len * WALL_THICKNESS;
        double ny = dx / len * WALL_THICKNESS;

        // Frame members
        List<FrameSpec> frames = new ArrayList<>();

        // Bottom rail
        frames.add(new FrameSpec(
            "RAIL_BOTTOM", "RHS 150x100",
            x1, y1, minZ,
            x2, y2, minZ + 0.15
        ));

        // Top rail
        frames.add(new FrameSpec(
            "RAIL_TOP", "RHS 150x100",
            x1, y1, maxZ - 0.15,
            x2, y2, maxZ
        ));

        // Studs at ends
        frames.add(new FrameSpec(
            "STUD_L", "RHS 150x100",
            x1, y1, minZ,
            x1 + 0.1, y1 + 0.15, maxZ
        ));

        frames.add(new FrameSpec(
            "STUD_R", "RHS 150x100",
            x2 - 0.1, y2 - 0.15, minZ,
            x2, y2, maxZ
        ));

        // Cladding
        CladdingSpec cladding = new CladdingSpec(
            "Metal Deck",
            x1, y1, minZ,
            x2 + nx, y2 + ny, maxZ
        );

        return new WallAssemblySpec(
            side + "_WALL_ASSEMBLY",
            "WALL_PANEL",
            side,
            length, WALL_THICKNESS, height,
            storeyName,
            frames,
            cladding
        );
    }

    private static StairSpec compileStair(StairDef stair, double x, double y,
                                          double baseZ, double storeyHeight) {
        // IRC compliant stair calculation
        int numRisers = (int) Math.ceil(storeyHeight / MAX_RISER);
        double actualRiser = storeyHeight / numRisers;
        double actualTread = Math.max(MIN_TREAD, 0.267); // 267mm standard

        double stairRun = actualTread * (numRisers - 1);
        double stairWidth = Math.max(stair.width(), MIN_STAIR_WIDTH);

        // Generate stair geometry (simplified straight flight)
        List<Point3D> vertices = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();

        // Create step geometry
        for (int i = 0; i < numRisers; i++) {
            double stepX = x;
            double stepY = y + i * actualTread;
            double stepZ = baseZ + i * actualRiser;

            // Tread vertices (4 corners)
            int baseIdx = vertices.size();
            vertices.add(new Point3D(stepX, stepY, stepZ));
            vertices.add(new Point3D(stepX + stairWidth, stepY, stepZ));
            vertices.add(new Point3D(stepX + stairWidth, stepY + actualTread, stepZ));
            vertices.add(new Point3D(stepX, stepY + actualTread, stepZ));

            // Top of riser
            vertices.add(new Point3D(stepX, stepY, stepZ + actualRiser));
            vertices.add(new Point3D(stepX + stairWidth, stepY, stepZ + actualRiser));
            vertices.add(new Point3D(stepX + stairWidth, stepY + actualTread, stepZ + actualRiser));
            vertices.add(new Point3D(stepX, stepY + actualTread, stepZ + actualRiser));

            // Add faces for this step (simplified box)
            // Bottom
            faces.add(new int[]{baseIdx, baseIdx + 1, baseIdx + 2});
            faces.add(new int[]{baseIdx, baseIdx + 2, baseIdx + 3});
            // Top
            faces.add(new int[]{baseIdx + 4, baseIdx + 6, baseIdx + 5});
            faces.add(new int[]{baseIdx + 4, baseIdx + 7, baseIdx + 6});
            // Front (riser)
            faces.add(new int[]{baseIdx, baseIdx + 4, baseIdx + 5});
            faces.add(new int[]{baseIdx, baseIdx + 5, baseIdx + 1});
            // Back
            faces.add(new int[]{baseIdx + 2, baseIdx + 6, baseIdx + 7});
            faces.add(new int[]{baseIdx + 2, baseIdx + 7, baseIdx + 3});
            // Left
            faces.add(new int[]{baseIdx, baseIdx + 3, baseIdx + 7});
            faces.add(new int[]{baseIdx, baseIdx + 7, baseIdx + 4});
            // Right
            faces.add(new int[]{baseIdx + 1, baseIdx + 5, baseIdx + 6});
            faces.add(new int[]{baseIdx + 1, baseIdx + 6, baseIdx + 2});
        }

        return new StairSpec(
            stair.name(),
            stair.toStorey(),
            x, y, baseZ,
            stairWidth, stairRun, storeyHeight,
            numRisers, actualRiser, actualTread,
            vertices, faces
        );
    }

    private static double calculateStairRun(double height) {
        int numRisers = (int) Math.ceil(height / MAX_RISER);
        double actualTread = Math.max(MIN_TREAD, 0.267);
        return actualTread * (numRisers - 1);
    }

    private static RoofSpec compileRoof(RoofDef roof, String buildingName,
                                        double baseZ, StoreyDef topStorey) {
        // Calculate building footprint from top storey
        double width = 0, depth = 0;
        for (RoomDef room : topStorey.rooms()) {
            width += room.width();
            depth = Math.max(depth, room.depth());
        }

        // Add stair footprint
        for (StairDef stair : topStorey.stairs()) {
            width += stair.width();
        }

        double overhang = 0.3; // 300mm overhang
        double pitchRad = Math.toRadians(roof.pitchDegrees());
        double ridgeRise = (width / 2 + overhang) * Math.tan(pitchRad);

        List<Point3D> vertices = List.of(
            new Point3D(-overhang, -overhang, baseZ),                    // SW eave
            new Point3D(width + overhang, -overhang, baseZ),             // SE eave
            new Point3D(width / 2, -overhang, baseZ + ridgeRise),        // S ridge
            new Point3D(-overhang, depth + overhang, baseZ),             // NW eave
            new Point3D(width + overhang, depth + overhang, baseZ),      // NE eave
            new Point3D(width / 2, depth + overhang, baseZ + ridgeRise)  // N ridge
        );

        List<int[]> faces = List.of(
            new int[]{0, 1, 2},  // South slope left
            new int[]{3, 5, 4},  // North slope left
            new int[]{0, 2, 5},  // West gable
            new int[]{0, 5, 3},
            new int[]{1, 4, 5},  // East gable
            new int[]{1, 5, 2}
        );

        return new RoofSpec(
            "GABLE",
            roof.pitchDegrees(),
            width + 2 * overhang,
            depth + 2 * overhang,
            ridgeRise,
            vertices,
            faces
        );
    }

    private static List<OpeningSpec> compileOpenings(List<OpeningDef> defs) {
        return defs.stream()
            .map(o -> new OpeningSpec(o.type(), o.wall(), o.connectsTo(), o.width(), o.height()))
            .toList();
    }

    // =========================================================================
    // Phase 15B: Interior Wall Helpers
    // =========================================================================

    /** Room bounding box for shared edge detection */
    private record RoomBounds(double minX, double minY, double maxX, double maxY) {}

    /** Shared edge between two rooms */
    private record SharedEdge(double x1, double y1, double x2, double y2) {
        boolean isVertical() {
            return Math.abs(x1 - x2) < 0.001;
        }
        boolean isHorizontal() {
            return Math.abs(y1 - y2) < 0.001;
        }
    }

    /**
     * Phase 21B: Snap two adjacent rooms together to eliminate gaps.
     * Modifies bounds in-place. Moves the room that is further from origin.
     * bounds format: [minX, minY, maxX, maxY]
     */
    private static void snapAdjacentRooms(double[] b1, double[] b2) {
        // Check X-axis gaps (rooms side by side)
        double gapEastWest = b2[0] - b1[2];  // b2.minX - b1.maxX (b1 left of b2)
        double gapWestEast = b1[0] - b2[2];  // b1.minX - b2.maxX (b2 left of b1)

        // Check Y-axis gaps (rooms above/below)
        double gapNorthSouth = b2[1] - b1[3];  // b2.minY - b1.maxY (b1 below b2)
        double gapSouthNorth = b1[1] - b2[3];  // b1.minY - b2.maxY (b2 below b1)

        // Check if Y ranges overlap (for X-axis snapping)
        boolean yOverlap = !(b1[3] < b2[1] || b2[3] < b1[1]);
        // Check if X ranges overlap (for Y-axis snapping)
        boolean xOverlap = !(b1[2] < b2[0] || b2[2] < b1[0]);

        // Snap smallest gap first
        double snapThreshold = 2.0; // Max gap to snap (meters)

        // X-axis: b1 left of b2, gap exists, Y ranges overlap
        if (gapEastWest > 0 && gapEastWest < snapThreshold && yOverlap) {
            // Move b2 left to touch b1
            double shift = gapEastWest;
            b2[0] -= shift;
            b2[2] -= shift;
            return;
        }

        // X-axis: b2 left of b1, gap exists, Y ranges overlap
        if (gapWestEast > 0 && gapWestEast < snapThreshold && yOverlap) {
            // Move b1 left to touch b2
            double shift = gapWestEast;
            b1[0] -= shift;
            b1[2] -= shift;
            return;
        }

        // Y-axis: b1 below b2, gap exists, X ranges overlap
        if (gapNorthSouth > 0 && gapNorthSouth < snapThreshold && xOverlap) {
            // Move b2 down to touch b1
            double shift = gapNorthSouth;
            b2[1] -= shift;
            b2[3] -= shift;
            return;
        }

        // Y-axis: b2 below b1, gap exists, X ranges overlap
        if (gapSouthNorth > 0 && gapSouthNorth < snapThreshold && xOverlap) {
            // Move b1 down to touch b2
            double shift = gapSouthNorth;
            b1[1] -= shift;
            b1[3] -= shift;
            return;
        }

        // No gap to snap (rooms already touch or are not alignable)
    }

    /**
     * Find the shared edge between two room bounding boxes.
     * Returns null if rooms don't share an edge (not touching or only corners).
     */
    private static SharedEdge findSharedEdge(RoomBounds r1, RoomBounds r2) {
        double tolerance = 0.001;

        // Check if r1's east edge touches r2's west edge (r1 left of r2)
        if (Math.abs(r1.maxX - r2.minX) < tolerance) {
            double overlapMinY = Math.max(r1.minY, r2.minY);
            double overlapMaxY = Math.min(r1.maxY, r2.maxY);
            if (overlapMaxY - overlapMinY > tolerance) {
                return new SharedEdge(r1.maxX, overlapMinY, r1.maxX, overlapMaxY);
            }
        }

        // Check if r1's west edge touches r2's east edge (r1 right of r2)
        if (Math.abs(r1.minX - r2.maxX) < tolerance) {
            double overlapMinY = Math.max(r1.minY, r2.minY);
            double overlapMaxY = Math.min(r1.maxY, r2.maxY);
            if (overlapMaxY - overlapMinY > tolerance) {
                return new SharedEdge(r1.minX, overlapMinY, r1.minX, overlapMaxY);
            }
        }

        // Check if r1's north edge touches r2's south edge (r1 below r2)
        if (Math.abs(r1.maxY - r2.minY) < tolerance) {
            double overlapMinX = Math.max(r1.minX, r2.minX);
            double overlapMaxX = Math.min(r1.maxX, r2.maxX);
            if (overlapMaxX - overlapMinX > tolerance) {
                return new SharedEdge(overlapMinX, r1.maxY, overlapMaxX, r1.maxY);
            }
        }

        // Check if r1's south edge touches r2's north edge (r1 above r2)
        if (Math.abs(r1.minY - r2.maxY) < tolerance) {
            double overlapMinX = Math.max(r1.minX, r2.minX);
            double overlapMaxX = Math.min(r1.maxX, r2.maxX);
            if (overlapMaxX - overlapMinX > tolerance) {
                return new SharedEdge(overlapMinX, r1.minY, overlapMaxX, r1.minY);
            }
        }

        return null; // No shared edge
    }

    // =========================================================================
    // Spec Records
    // =========================================================================

    public record BuildingSpec(
        String name,
        List<StoreySpec> storeys,
        RoofSpec roof
    ) {}

    public record StoreySpec(
        String name,
        int level,
        double baseZ,
        double height,
        SlabSpec slab,
        List<WallAssemblySpec> walls,
        List<RoomSpec> rooms,
        List<StairSpec> stairs,
        List<DoorSpec> doors,
        List<WindowSpec> windows,
        List<LandingSpec> landings,
        List<SprinklerSpec> sprinklers,  // Phase 14B
        List<LightSpec> lights           // Phase 14B
    ) {
        // Backward-compatible constructor without MEP
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, List.of(), List.of());
        }

        // Constructor with sprinklers only (backward compat)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, List.of());
        }
    }

    public record DoorSpec(
        String name,
        String roomName,
        String wall,
        double x, double y, double z,
        double width, double height
    ) {}

    public record WindowSpec(
        String name,
        String roomName,
        String wall,
        double x, double y, double z,
        double width, double height,
        double sillHeight
    ) {}

    public record LandingSpec(
        String name,
        String fromStair,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}

    public record SlabSpec(
        String type,
        String name,
        double minX, double minY,
        double maxX, double maxY,
        double minZ, double maxZ
    ) {}

    public record WallAssemblySpec(
        String assemblyName,
        String assemblyType,
        String side,
        double length, double thickness, double height,
        String storeyName,
        List<FrameSpec> frames,
        CladdingSpec cladding
    ) {}

    public record FrameSpec(
        String role,
        String profile,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}

    public record CladdingSpec(
        String material,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}

    public record RoomSpec(
        String type,
        String name,
        double minX, double minY,
        double maxX, double maxY,
        double minZ, double maxZ,
        List<OpeningSpec> openings
    ) {}

    public record OpeningSpec(
        String type,
        String wall,
        String connectsTo,
        double width,
        double height
    ) {}

    public record StairSpec(
        String name,
        String toStorey,
        double x, double y, double z,
        double width, double run, double rise,
        int numRisers,
        double riserHeight,
        double treadDepth,
        List<Point3D> vertices,
        List<int[]> faces,
        // Library integration (Phase 14A)
        String libraryGeometryHash,  // If set, use library geometry
        double scaleX,               // Scale factor for library geometry
        double scaleY,
        double scaleZ
    ) {
        // Convenience constructor for parametric stairs (existing code)
        public StairSpec(String name, String toStorey,
                        double x, double y, double z,
                        double width, double run, double rise,
                        int numRisers, double riserHeight, double treadDepth,
                        List<Point3D> vertices, List<int[]> faces) {
            this(name, toStorey, x, y, z, width, run, rise,
                 numRisers, riserHeight, treadDepth, vertices, faces,
                 null, 1.0, 1.0, 1.0);  // No library geometry
        }

        public boolean usesLibrary() {
            return libraryGeometryHash != null && !libraryGeometryHash.isEmpty();
        }

        // Create a library-based stair spec
        public static StairSpec fromLibrary(String name, String toStorey,
                                           double x, double y, double z,
                                           double width, double run, double rise,
                                           String geometryHash,
                                           double scaleX, double scaleY, double scaleZ) {
            return new StairSpec(name, toStorey, x, y, z, width, run, rise,
                               0, 0, 0, List.of(), List.of(),
                               geometryHash, scaleX, scaleY, scaleZ);
        }
    }

    public record RoofSpec(
        String type,
        double pitchDegrees,
        double width, double depth, double ridgeRise,
        List<Point3D> vertices,
        List<int[]> faces
    ) {}

    /**
     * Sprinkler instance placed in a room (Phase 14B).
     * Position is ceiling attachment point.
     */
    public record SprinklerSpec(
        String id,
        String roomName,
        double x, double y, double z,    // ceiling attachment point
        String type,                      // "pendant" or "upright"
        double spacing                    // grid spacing used
    ) {}

    /**
     * Light fixture placed in a room (Phase 14B).
     * Position is ceiling mount point.
     */
    public record LightSpec(
        String id,
        String roomName,
        double x, double y, double z,    // ceiling mount point
        String fixtureType,              // "recessed", "pendant", "2x4_LED"
        double spacing                   // grid spacing used
    ) {}

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) {
        String dsl = """
            BUILDING "test_duplex" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {
                        DOOR south
                        WINDOW east
                    }
                    STAIR "stair1" at:B1 width:1.0m to:"Upper"
                }
                STOREY "Upper" level:1 height:2.8m {
                    BEDROOM "bed1" at:A1 size:4x4m {
                        DOOR south to:landing
                        WINDOW east
                    }
                    LANDING "landing" at:B1 size:2x2m from:"stair1"
                }
                ROOF pitch:15deg
            }
            """;

        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingSpec spec = compile(def);

        System.out.println("=".repeat(60));
        System.out.println("BUILDING COMPILATION TEST");
        System.out.println("=".repeat(60));

        System.out.println("\nBuilding: " + spec.name());
        System.out.println("Storeys: " + spec.storeys().size());

        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("\n  Storey: %s (level %d)%n", storey.name(), storey.level());
            System.out.printf("    Base Z: %.3fm, Height: %.3fm%n", storey.baseZ(), storey.height());

            System.out.printf("    Slab: %s [%.2f,%.2f] to [%.2f,%.2f] Z[%.2f-%.2f]%n",
                storey.slab().name(),
                storey.slab().minX(), storey.slab().minY(),
                storey.slab().maxX(), storey.slab().maxY(),
                storey.slab().minZ(), storey.slab().maxZ());

            System.out.printf("    Walls: %d assemblies%n", storey.walls().size());
            for (WallAssemblySpec wall : storey.walls()) {
                System.out.printf("      %s: %.2f x %.2f x %.2fm%n",
                    wall.assemblyName(), wall.length(), wall.thickness(), wall.height());
            }

            System.out.printf("    Rooms: %d%n", storey.rooms().size());
            System.out.printf("    Stairs: %d%n", storey.stairs().size());

            for (StairSpec stair : storey.stairs()) {
                System.out.printf("      %s: %d risers @ %.0fmm, treads %.0fmm%n",
                    stair.name(), stair.numRisers(),
                    stair.riserHeight() * 1000, stair.treadDepth() * 1000);
                System.out.printf("        IRC Check: Riser %.0fmm <= 196mm? %s%n",
                    stair.riserHeight() * 1000,
                    stair.riserHeight() <= MAX_RISER ? "PASS" : "FAIL");
                System.out.printf("        IRC Check: Tread %.0fmm >= 254mm? %s%n",
                    stair.treadDepth() * 1000,
                    stair.treadDepth() >= MIN_TREAD ? "PASS" : "FAIL");
            }
        }

        if (spec.roof() != null) {
            System.out.printf("\nRoof: %s, %.0f deg pitch%n",
                spec.roof().type(), spec.roof().pitchDegrees());
            System.out.printf("  Ridge rise: %.3fm%n", spec.roof().ridgeRise());
        }

        // Verify Z-continuity
        System.out.println("\n" + "-".repeat(60));
        System.out.println("Z-CONTINUITY CHECK:");
        for (int i = 0; i < spec.storeys().size() - 1; i++) {
            StoreySpec lower = spec.storeys().get(i);
            StoreySpec upper = spec.storeys().get(i + 1);

            double lowerTop = lower.baseZ() + lower.height();
            double upperBase = upper.baseZ();
            double gap = Math.abs(upperBase - lowerTop);

            System.out.printf("  %s top (%.3fm) -> %s base (%.3fm): gap %.3fm %s%n",
                lower.name(), lowerTop,
                upper.name(), upperBase,
                gap, gap < 0.001 ? "✓" : "✗");
        }

        System.out.println("\n[PASS] Compilation test complete");
    }
}
