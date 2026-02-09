package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.contract.*;
import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.solver.SpaceSolver.*;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.util.OutlierLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a single storey from BuildingDefinition into StoreySpec.
 * Extracted from BuildingCompiler to reduce monolith size.
 *
 * Handles:
 * - Room layout and grid positioning
 * - Interior/exterior wall generation
 * - Opening resolution (doors, windows)
 * - MEP placement (sprinklers, HVAC, electrical, plumbing)
 * - Structural placement (columns, beams, lintels)
 * - Furniture placement
 * - Fire protection
 */
class StoreyCompiler {
    // Phase 95B: Lazy singleton — loaded once, reused across repeated storeys
    private static FloorPlateBOMResolver floorBomResolver;
    static FloorPlateBOMResolver getFloorBomResolver() {
        if (floorBomResolver == null) {
            floorBomResolver = new FloorPlateBOMResolver();
        }
        return floorBomResolver;
    }

    static StoreySpec compileStorey(StoreyDef storey, double baseZ,
                                            boolean isGround, boolean isTop,
                                            BuildingDefinition building,
                                            SharedElementRegistry registry) {
        List<WallAssemblySpec> walls = new ArrayList<>();
        List<RoomSpec> rooms = new ArrayList<>();
        List<StairSpec> stairs = new ArrayList<>();
        List<DoorSpec> doors = new ArrayList<>();
        List<WindowSpec> windows = new ArrayList<>();
        List<LandingSpec> landings = new ArrayList<>();
        List<SprinklerSpec> sprinklers = new ArrayList<>();  // Phase 14B
        List<LightSpec> lights = new ArrayList<>();          // Phase 14B
        List<FixtureSpec> fixtures = new ArrayList<>();      // Phase 22
        List<ElevatorSpec> elevators = new ArrayList<>();    // Phase 56B
        List<ElevatorLobbySpec> lobbies = new ArrayList<>(); // Phase 56B
        List<ShaftSpec> shafts = new ArrayList<>();          // Phase 56B
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

        // Phase 95B: Resolve floor BOM bounds if declared
        Map<String, double[]> resolvedBomBounds = Map.of();
        if (storey.floorBom() != null && building.grid() != null) {
            FloorPlateBOMResolver.GridInfo gridInfo =
                FloorPlateBOMResolver.gridInfoFromGridDef(building.grid());
            resolvedBomBounds = getFloorBomResolver().resolveRoomBoundsMap(
                storey.floorBom(), gridInfo);
        }

        for (RoomDef room : storey.rooms()) {
            double roomMinX, roomMinY, roomMaxX, roomMaxY;

            // Phase 95B: Check floor BOM resolver first
            double[] bomResolved = resolvedBomBounds.get(room.name());
            if (bomResolved != null) {
                roomMinX = bomResolved[0]; roomMinY = bomResolved[1];
                roomMaxX = bomResolved[2]; roomMaxY = bomResolved[3];
            } else if (room.hasGridBounds() && building.grid() != null) {
                GridBounds gb = room.getParsedGridBounds();
                if (gb != null) {
                    roomMinX = building.grid().getX(gb.startX());
                    roomMinY = building.grid().getY(gb.startY());
                    roomMaxX = building.grid().getX(gb.endX());
                    roomMaxY = building.grid().getY(gb.endY());
                } else {
                    // Fallback if bounds parsing fails
                    roomMinX = currentX;
                    roomMinY = 0;
                    roomMaxX = roomMinX + room.width();
                    roomMaxY = roomMinY + room.depth();
                }
            } else if (room.gridPosition() != null) {
                int[] coords = BuildingCompiler.parseGridPosition(room.gridPosition());
                roomMinX = coords[0];
                roomMinY = coords[1];
                roomMaxX = roomMinX + room.width();
                roomMaxY = roomMinY + room.depth();
            } else {
                roomMinX = currentX;
                roomMinY = 0;
                roomMaxX = roomMinX + room.width();
                roomMaxY = roomMinY + room.depth();
            }

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

            // Phase AD: Use SpaceTypeRegistry instead of RoomType.fromKeyword()
            // SpaceTypeRegistry queries AD database first, falls back to YAML
            SpaceTypeRegistry.SpaceTypeConfig spaceTypeConfig = SpaceTypeRegistry.get(room.type());
            OutlierLogger.incrementTotalElements();

            rooms.add(new RoomSpec(
                spaceTypeConfig.name(), room.name(),  // Use resolved type name from AD/YAML
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                baseZ, baseZ + storey.height(),
                compileOpenings(room.openings()),
                room.above(),   // Phase 42: vertical constraint
                room.stack()    // Phase 42: stack alignment
            ));

            // Track for MEP generation (Phase 14B)
            if (room.hasSprinklers() || room.hasLights()) {
                roomsWithMEP.add(new RoomMEP(
                    room.name(), roomMinX, roomMinY, roomMaxX, roomMaxY,
                    room.sprinklerSpacing(), room.lightSpacing()
                ));
            }

            // Generate doors and windows from room openings
            // Phase 50: Track counts per wall for unique naming
            Map<String, Integer> doorCountPerWall = new HashMap<>();
            Map<String, Integer> windowCountPerWall = new HashMap<>();

            // Phase 87: BOM-driven opening defaults (doors only — windows handled in auto-window section)
            // Override priority: DSL explicit > opens_to connection > BOM default
            List<OpeningDef> effectiveOpenings = new ArrayList<>(room.openings());
            boolean hasExplicitDoors = effectiveOpenings.stream()
                .anyMatch(o -> o.type().equals("DOOR"));
            boolean hasOpensTo = room.opensTo() != null && !room.opensTo().isEmpty();

            if (!hasExplicitDoors && !hasOpensTo) {
                var bomDoorDefaults = OpeningBomAD.getDoorDefaults(room.type());
                for (var bomDef : bomDoorDefaults) {
                    var family = OpeningBomAD.getFamily(bomDef.familyId());
                    if (family == null) continue;
                    // Pick first interior wall (not exterior)
                    List<String> extWalls = room.getAllExteriorWalls().stream()
                        .map(String::toLowerCase).toList();
                    String doorWall = null;
                    for (String candidate : List.of("south", "north", "west", "east")) {
                        if (!extWalls.contains(candidate)) {
                            doorWall = candidate;
                            break;
                        }
                    }
                    if (doorWall == null) doorWall = "south"; // fallback: all walls exterior
                    double w = bomDef.overrideWidthMm() != null
                        ? bomDef.overrideWidthMm() / 1000.0 : family.defaultWidthMm() / 1000.0;
                    double h = bomDef.overrideHeightMm() != null
                        ? bomDef.overrideHeightMm() / 1000.0 : family.defaultHeightMm() / 1000.0;
                    effectiveOpenings.add(new OpeningDef("DOOR", doorWall, null, w, h, null));
                }
            }

            for (OpeningDef opening : effectiveOpenings) {
                // Phase 50: Resolve dimensions from schedule if width=0
                double width = opening.width();
                double height = opening.height();
                if ((width == 0 || height == 0) && opening.typeCode() != null) {
                    var schedule = opening.type().equals("DOOR")
                        ? building.doorSchedule()
                        : building.windowSchedule();
                    if (schedule != null) {
                        double[] dims = schedule.resolve(opening.typeCode());
                        if (dims != null) {
                            width = dims[0];
                            height = dims[1];
                        }
                    }
                }
                // Default dimensions if still not resolved
                if (width == 0) width = opening.type().equals("DOOR") ? 0.9 : 1.2;
                if (height == 0) height = 2.1;

                double openingX, openingY;
                // Center opening on wall using actual bounds (room.width()/depth() may be 0 for grid rooms)
                double actualWidth = roomMaxX - roomMinX;
                double actualDepth = roomMaxY - roomMinY;
                switch (opening.wall()) {
                    case "south" -> { openingX = roomMinX + actualWidth / 2 - width / 2; openingY = roomMinY; }
                    case "north" -> { openingX = roomMinX + actualWidth / 2 - width / 2; openingY = roomMaxY; }
                    case "west" -> { openingX = roomMinX; openingY = roomMinY + actualDepth / 2 - width / 2; }
                    case "east" -> { openingX = roomMaxX; openingY = roomMinY + actualDepth / 2 - width / 2; }
                    default -> { openingX = roomMinX; openingY = roomMinY; }
                }

                if (opening.type().equals("DOOR")) {
                    // Phase 86: Skip if opens_to will handle this door (avoid duplicate)
                    if (room.opensTo() != null && isOpensToWall(room, opening.wall(), roomBounds)) {
                        continue;  // Connection door at shared edge handles this
                    }
                    // Phase 50: Unique naming with counter for multiple doors on same wall
                    int count = doorCountPerWall.merge(opening.wall(), 1, Integer::sum);
                    String doorName = opening.connectsTo() != null
                        ? room.name() + "_to_" + opening.connectsTo() + "_door"
                        : room.name() + "_door_" + opening.wall() + (count > 1 ? "_" + count : "");
                    doors.add(new DoorSpec(
                        doorName,
                        room.name(), opening.wall(),
                        openingX, openingY, baseZ,
                        width, height,
                        opening.connectsTo()
                    ));
                } else if (opening.type().equals("WINDOW")) {
                    // Phase 50: Unique naming with counter for multiple windows on same wall
                    int count = windowCountPerWall.merge(opening.wall(), 1, Integer::sum);
                    double sillHeight = 0.9; // 900mm sill height
                    windows.add(new WindowSpec(
                        room.name() + "_window_" + opening.wall() + (count > 1 ? "_" + count : ""),
                        room.name(), opening.wall(),
                        openingX, openingY, baseZ + sillHeight,
                        width, height,
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
            double stairX, stairY;

            // Phase 48D: Use grid position if available (for SHARED storeys)
            if (stair.gridPosition() != null && !stair.gridPosition().isEmpty()) {
                int[] coords = BuildingCompiler.parseGridPosition(stair.gridPosition());
                stairX = coords[0];
                stairY = coords[1];
            } else {
                // Fallback: place stair after rooms
                stairX = maxX;
                stairY = 0;
            }

            stairs.add(compileStair(stair, stairX, stairY, baseZ, storey.height()));

            // Update bounds to include stair
            if (maxX == Double.MIN_VALUE) {
                minX = stairX;
                maxX = stairX + stair.width();
            } else {
                minX = Math.min(minX, stairX);
                maxX = Math.max(maxX, stairX + stair.width());
            }
            if (maxY == Double.MIN_VALUE) {
                minY = stairY;
                maxY = stairY + stairRun;
            } else {
                minY = Math.min(minY, stairY);
                maxY = Math.max(maxY, stairY + stairRun);
            }
        }

        // Phase 56B: Compile CORE stairs (building-level vertical circulation)
        if (building.core() != null && building.grid() != null) {
            for (StairDef coreStair : building.core().stairs()) {
                double stairRun = calculateStairRun(storey.height());
                double stairX, stairY;

                // Use grid lookup for proper coordinate resolution
                String[] labels = BuildingCompiler.parseGridLabels(coreStair.gridPosition());
                stairX = building.grid().getX(labels[0]);
                stairY = building.grid().getY(labels[1]);

                stairs.add(compileStair(coreStair, stairX, stairY, baseZ, storey.height()));

                // Update bounds
                minX = Math.min(minX, stairX);
                maxX = Math.max(maxX, stairX + coreStair.width());
                minY = Math.min(minY, stairY);
                maxY = Math.max(maxY, stairY + stairRun);
            }

            // Phase 56B: Compile CORE shafts (elevator + MEP)
            for (ShaftDef coreShaft : building.core().shafts()) {
                String[] labels = BuildingCompiler.parseGridLabels(coreShaft.gridPosition());
                double shaftX = building.grid().getX(labels[0]);
                double shaftY = building.grid().getY(labels[1]);

                shafts.add(new ShaftSpec(
                    coreShaft.name(),
                    coreShaft.type(),
                    shaftX, shaftY, baseZ,
                    shaftX + coreShaft.widthM(), shaftY + coreShaft.depthM(), baseZ + storey.height()
                ));
            }

            // Phase 56B: Compile CORE elevator lobbies with elevators
            for (ElevatorLobbyDef coreLobby : building.core().lobbies()) {
                // Parse lobby bounds (e.g., C2-D4)
                String[] boundsLabels = coreLobby.gridBounds().split("-");
                String[] startLabels = BuildingCompiler.parseGridLabels(boundsLabels[0]);
                String[] endLabels = BuildingCompiler.parseGridLabels(boundsLabels[1]);

                double lobbyMinX = building.grid().getX(startLabels[0]);
                double lobbyMinY = building.grid().getY(startLabels[1]);
                double lobbyMaxX = building.grid().getX(endLabels[0]);
                double lobbyMaxY = building.grid().getY(endLabels[1]);

                // Compile elevators within the lobby
                List<ElevatorSpec> lobbyElevators = new ArrayList<>();
                double elevX = lobbyMinX + 0.5; // Offset from lobby edge
                for (ElevatorDef elev : coreLobby.elevators()) {
                    // Calculate shaft dimensions (car + clearances ~200mm each side)
                    double shaftWidth = elev.carWidthMm() / 1000.0 + 0.4;
                    double shaftDepth = elev.carDepthMm() / 1000.0 + 0.4;

                    lobbyElevators.add(new ElevatorSpec(
                        elev.name(),
                        elev.type(),
                        elevX, lobbyMinY + 0.5, baseZ,  // Position
                        elev.carWidthMm(),
                        elev.carDepthMm(),
                        elev.doorWidthMm(),
                        shaftWidth,
                        shaftDepth,
                        elev.emergencyPower(),
                        elev.fireRatingHr(),
                        coreLobby.pressurized()
                    ));
                    elevators.add(lobbyElevators.get(lobbyElevators.size() - 1));
                    elevX += shaftWidth + 0.3; // 300mm gap between shafts
                }

                lobbies.add(new ElevatorLobbySpec(
                    coreLobby.name(),
                    lobbyMinX, lobbyMinY, baseZ,
                    lobbyMaxX, lobbyMaxY, baseZ + storey.height(),
                    coreLobby.pressurized(),
                    coreLobby.fireRatingHr(),
                    lobbyElevators
                ));
            }
        }

        // Generate landings at stair top
        for (LandingDef landing : storey.landings()) {
            double landingX, landingY;

            // Phase 48D: Use grid position if available
            if (landing.gridPosition() != null && !landing.gridPosition().isEmpty()) {
                int[] coords = BuildingCompiler.parseGridPosition(landing.gridPosition());
                landingX = coords[0];
                landingY = coords[1];
            } else {
                // Fallback: place after rooms
                landingX = maxX - landing.width();
                landingY = 0;
            }

            double landingZ = baseZ; // Landing is at this storey's floor level
            double landingThickness = 0.15; // 150mm

            landings.add(new LandingSpec(
                landing.name(),
                landing.fromStair(),
                landingX, landingY, landingZ - landingThickness,
                landingX + landing.width(), landingY + landing.depth(), landingZ
            ));

            // Update bounds to include landing
            if (maxX == Double.MIN_VALUE) {
                minX = landingX;
                maxX = landingX + landing.width();
            } else {
                minX = Math.min(minX, landingX);
                maxX = Math.max(maxX, landingX + landing.width());
            }
            if (maxY == Double.MIN_VALUE) {
                minY = landingY;
                maxY = landingY + landing.depth();
            } else {
                minY = Math.min(minY, landingY);
                maxY = Math.max(maxY, landingY + landing.depth());
            }
        }

        // Generate slab
        if (isGround) {
            // Foundation slab
            slab = new SlabSpec(
                "FOUNDATION", "Foundation Slab",
                minX - BIMConstants.STANDARD_SLAB_OVERLAP, minY - BIMConstants.STANDARD_SLAB_OVERLAP,
                maxX + BIMConstants.STANDARD_SLAB_OVERLAP, maxY + BIMConstants.STANDARD_SLAB_OVERLAP,
                baseZ - BIMConstants.STANDARD_SLAB_THICKNESS, baseZ
            );
        } else {
            // Intermediate floor slab
            slab = new SlabSpec(
                "FLOOR", "Floor Slab Level " + storey.level(),
                minX - BIMConstants.STANDARD_SLAB_OVERLAP, minY - BIMConstants.STANDARD_SLAB_OVERLAP,
                maxX + BIMConstants.STANDARD_SLAB_OVERLAP, maxY + BIMConstants.STANDARD_SLAB_OVERLAP,
                baseZ - BIMConstants.STANDARD_SLAB_THICKNESS, baseZ
            );
        }

        // Phase 99C: Resolve facade material for perimeter walls
        String facadeMaterial = "Metal Deck"; // default
        if (building.facade() != null && building.facade().equalsIgnoreCase("glass")) {
            facadeMaterial = "Glass Curtain Wall";
        }

        // Generate perimeter walls with registry for stud deduplication
        walls.add(compilePerimeterWall("SOUTH", minX, minY, maxX, minY,
            baseZ, baseZ + storey.height(), storey.name(), registry, facadeMaterial));
        walls.add(compilePerimeterWall("NORTH", minX, maxY, maxX, maxY,
            baseZ, baseZ + storey.height(), storey.name(), registry, facadeMaterial));
        walls.add(compilePerimeterWall("WEST", minX, minY, minX, maxY,
            baseZ, baseZ + storey.height(), storey.name(), registry, facadeMaterial));
        walls.add(compilePerimeterWall("EAST", maxX, minY, maxX, maxY,
            baseZ, baseZ + storey.height(), storey.name(), registry, facadeMaterial));

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
                    // Generate interior wall along shared edge (with registry for stud deduplication)
                    String wallName = "INTERIOR_" + room1.name() + "_" + room2.name();
                    walls.add(compileWall(wallName,
                        edge.x1(), edge.y1(), edge.x2(), edge.y2(),
                        baseZ, baseZ + storey.height(), storey.name(), registry));

                    // Check if rooms have ADJACENT or OPENS_TO constraint - if so, auto-place door
                    boolean areAdjacent = room1.adjacentTo().contains(room2.name()) ||
                                          room2.adjacentTo().contains(room1.name());
                    // Phase 28: Also check opens_to constraint
                    boolean hasOpening = (room1.opensTo() != null && room1.opensTo().equals(room2.name())) ||
                                         (room2.opensTo() != null && room2.opensTo().equals(room1.name()));

                    if (areAdjacent || hasOpening) {
                        // Place door at center of shared edge
                        double doorX, doorY;
                        String doorWall;

                        // Phase 86: Look up explicit DOOR declaration from either room for this wall
                        double connDoorWidth = BIMConstants.STANDARD_DOOR_WIDTH;
                        double connDoorHeight = BIMConstants.STANDARD_DOOR_HEIGHT;
                        String room1Wall = edge.isVertical()
                            ? (edge.x1() == bounds1.maxX() ? "east" : "west")
                            : (edge.y1() == bounds1.maxY() ? "north" : "south");
                        String room2Wall = edge.isVertical()
                            ? (edge.x1() == bounds2.maxX() ? "east" : "west")
                            : (edge.y1() == bounds2.maxY() ? "north" : "south");

                        // Check room1's openings first, then room2's
                        OpeningDef matchedOpening = null;
                        for (OpeningDef op : room1.openings()) {
                            if (op.type().equals("DOOR") && op.wall().equals(room1Wall)) {
                                matchedOpening = op;
                                break;
                            }
                        }
                        if (matchedOpening == null) {
                            for (OpeningDef op : room2.openings()) {
                                if (op.type().equals("DOOR") && op.wall().equals(room2Wall)) {
                                    matchedOpening = op;
                                    break;
                                }
                            }
                        }
                        if (matchedOpening != null) {
                            double w = matchedOpening.width();
                            double h = matchedOpening.height();
                            if ((w == 0 || h == 0) && matchedOpening.typeCode() != null
                                    && building.doorSchedule() != null) {
                                double[] dims = building.doorSchedule().resolve(matchedOpening.typeCode());
                                if (dims != null) { w = dims[0]; h = dims[1]; }
                            }
                            if (w > 0) connDoorWidth = w;
                            if (h > 0) connDoorHeight = h;
                        }

                        if (edge.isVertical()) {
                            // Vertical wall (north-south oriented)
                            doorX = edge.x1();
                            doorY = (edge.y1() + edge.y2()) / 2 - connDoorWidth / 2;
                            doorWall = room1Wall;
                        } else {
                            // Horizontal wall (east-west oriented)
                            doorX = (edge.x1() + edge.x2()) / 2 - connDoorWidth / 2;
                            doorY = edge.y1();
                            doorWall = room1Wall;
                        }

                        // Phase 48D.2: Include connectsTo for internal doors
                        doors.add(new DoorSpec(
                            room1.name() + "_to_" + room2.name() + "_door",
                            room1.name(), doorWall,
                            doorX, doorY, baseZ,
                            connDoorWidth, connDoorHeight,
                            room2.name()
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
            if (Math.abs(bounds.minY() - minY) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_south");
            if (Math.abs(bounds.maxY() - maxY) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_north");
            if (Math.abs(bounds.minX() - minX) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_west");
            if (Math.abs(bounds.maxX() - maxX) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_east");
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
                        if (Math.abs(edge.x1() - b1.maxX()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_east");
                        if (Math.abs(edge.x1() - b1.minX()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_west");
                        if (Math.abs(edge.x1() - b2.maxX()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_east");
                        if (Math.abs(edge.x1() - b2.minX()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_west");
                    } else {
                        if (Math.abs(edge.y1() - b1.maxY()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_north");
                        if (Math.abs(edge.y1() - b1.minY()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(i).name() + "_south");
                        if (Math.abs(edge.y1() - b2.maxY()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_north");
                        if (Math.abs(edge.y1() - b2.minY()) < BIMConstants.TOLERANCE) coveredEdges.add(roomList.get(j).name() + "_south");
                    }
                }
            }
        }

        // Generate partition walls for uncovered edges (with registry for stud deduplication)
        for (RoomDef room : roomList) {
            RoomBounds bounds = roomBoundsMap.get(room.name());

            // North edge
            if (!coveredEdges.contains(room.name() + "_north")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_north",
                    bounds.minX(), bounds.maxY(), bounds.maxX(), bounds.maxY(),
                    baseZ, baseZ + storey.height(), storey.name(), registry));
            }
            // South edge
            if (!coveredEdges.contains(room.name() + "_south")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_south",
                    bounds.minX(), bounds.minY(), bounds.maxX(), bounds.minY(),
                    baseZ, baseZ + storey.height(), storey.name(), registry));
            }
            // East edge
            if (!coveredEdges.contains(room.name() + "_east")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_east",
                    bounds.maxX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
                    baseZ, baseZ + storey.height(), storey.name(), registry));
            }
            // West edge
            if (!coveredEdges.contains(room.name() + "_west")) {
                walls.add(compileWall("PARTITION_" + room.name() + "_west",
                    bounds.minX(), bounds.minY(), bounds.minX(), bounds.maxY(),
                    baseZ, baseZ + storey.height(), storey.name(), registry));
            }
        }

        // Auto-place windows for rooms with EXTERIOR constraints (if not already specified)
        // Phase 28: Use getAllExteriorWalls() to support both legacy exteriorWall and new exteriorWalls list
        for (RoomDef room : storey.rooms()) {
            for (String extWall : room.getAllExteriorWalls()) {
                extWall = extWall.toLowerCase();

                // Check if room already has a window on that wall
                final String finalExtWall = extWall;
                boolean hasWindowOnWall = room.openings().stream()
                    .anyMatch(o -> o.type().equals("WINDOW") && o.wall().equalsIgnoreCase(finalExtWall));

                if (!hasWindowOnWall) {
                    // Auto-place window on exterior wall
                    // Phase 47A.3: Place on ROOM's wall, not building edge
                    // This ensures windows work correctly for multi-unit with party walls
                    RoomBounds bounds = roomBoundsMap.get(room.name());
                    if (bounds == null) continue;

                    // Phase 87: Use BOM window family dimensions if available
                    var windowDefs = OpeningBomAD.getWindowDefaults(room.type());
                    OpeningBomAD.OpeningFamily winFamily = !windowDefs.isEmpty()
                        ? OpeningBomAD.getFamily(windowDefs.get(0).familyId()) : null;
                    double winW = winFamily != null ? winFamily.defaultWidthMm() / 1000.0 : BIMConstants.STANDARD_WINDOW_WIDTH;
                    double winH = winFamily != null ? winFamily.defaultHeightMm() / 1000.0 : BIMConstants.STANDARD_WINDOW_HEIGHT;
                    int sillMm = !windowDefs.isEmpty() ? windowDefs.get(0).sillHeightMm() : 900;
                    double sillH = sillMm / 1000.0;

                    double windowX, windowY;
                    double roomCenterX = bounds.minX() + (bounds.maxX() - bounds.minX()) / 2;
                    double roomCenterY = bounds.minY() + (bounds.maxY() - bounds.minY()) / 2;

                    switch (extWall) {
                        case "south" -> {
                            windowX = roomCenterX - winW / 2;
                            windowY = bounds.minY();  // Room's south wall
                        }
                        case "north" -> {
                            windowX = roomCenterX - winW / 2;
                            windowY = bounds.maxY();  // Room's north wall
                        }
                        case "west" -> {
                            windowX = bounds.minX();  // Room's west wall
                            windowY = roomCenterY - winW / 2;
                        }
                        case "east" -> {
                            windowX = bounds.maxX();  // Room's east wall
                            windowY = roomCenterY - winW / 2;
                        }
                        default -> {
                            windowX = bounds.minX();
                            windowY = bounds.minY();
                        }
                    }

                    windows.add(new WindowSpec(
                        room.name() + "_auto_window_" + extWall,
                        room.name(), extWall,
                        windowX, windowY, baseZ + sillH,
                        winW, winH,
                        sillH
                    ));
                }
            }
        }
        // End Phase 15B

        // Generate MEP elements for rooms (Phase 14B)
        double ceilingZ = baseZ + storey.height() - 0.05;  // General ceiling ref for fixtures/HVAC
        // Phase 85: Sprinkler Z from BOM metadata (BELOW_SLAB rule) — separate from ceilingZ
        BOMRuleAD.BOMPlacementParams headParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "HEAD");
        double sprinklerZ = headParams.resolveZ(baseZ, storey.height(), BIMConstants.STANDARD_SLAB_THICKNESS);
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
                            x, y, sprinklerZ,
                            "pendant",
                            spacing
                        ));
                    }
                }
            }

            // Generate lights
            // Phase 44: Light placement moved after column detection for clash avoidance
            // (lightSpacing will be handled in Phase 33 section with column avoidance)
        }

        // =====================================================================
        // Phase 22: Auto-place fixtures for BATHROOM and KITCHEN rooms
        // =====================================================================
        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var fixturePlacer = new com.bim.compiler.library.FixturePlacer(library);

            for (RoomSpec room : rooms) {
                String roomType = room.type().toUpperCase();

                if (roomType.equals("BATHROOM")) {
                    var placed = fixturePlacer.placeBathroomFixtures(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, ceilingZ + 0.05  // Ceiling for exhaust fan
                    );

                    int fixtureIdx = 0;
                    for (var f : placed) {
                        fixtures.add(new FixtureSpec(
                            room.name() + "_" + f.type().name().toLowerCase() + "_" + (++fixtureIdx),
                            room.name(),
                            f.type().name().toLowerCase(),
                            f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z(),
                            f.rotation(),
                            f.geometryHash(),
                            f.localBounds().width(), f.localBounds().depth(), f.localBounds().height()
                        ));
                    }
                } else if (roomType.contains("TOILET") || roomType.equals("WC")) {
                    // Phase 96B: BOM-driven toilet fixture layout
                    String doorWall = findDoorWall(room);
                    // Find exterior walls from the RoomDef (parsed DSL, not compiled RoomSpec)
                    List<String> exteriorWalls = findRoomDefExteriorWalls(storey, room.name());
                    var placed = fixturePlacer.placeToiletBlockFixtures(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, ceilingZ + 0.05,
                        room.name(), doorWall, exteriorWalls
                    );

                    int fixtureIdx = 0;
                    for (var f : placed) {
                        fixtures.add(new FixtureSpec(
                            room.name() + "_" + f.type().name().toLowerCase() + "_" + (++fixtureIdx),
                            room.name(),
                            f.type().name().toLowerCase(),
                            f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z(),
                            f.rotation(),
                            f.geometryHash(),
                            f.localBounds().width(), f.localBounds().depth(), f.localBounds().height()
                        ));
                    }

                    // Phase 98: Stall divider walls between toilets
                    long toiletCount = placed.stream()
                        .filter(f -> f.type() == com.bim.compiler.library.FixturePlacer.FixtureType.TOILET).count();
                    if (toiletCount > 1) {
                        String dw = doorWall != null ? doorWall.toLowerCase() : "west";
                        String backWall = oppositeWall(dw);
                        boolean backIsEW = backWall.equals("east") || backWall.equals("west");
                        double wallLen = backIsEW
                            ? (room.maxY() - room.minY()) : (room.maxX() - room.minX());
                        int tc = (int) toiletCount;
                        double stallSpacing = 1.3;
                        double totalStalls = tc * stallSpacing;
                        double startOff = (wallLen - totalStalls) / 2.0;
                        double dividerDepth = 1.2;  // 1.2m from back wall into room
                        double stallHeight = 1.8;   // Phase 99B: 1.8m stall height

                        for (int di = 1; di < tc; di++) {
                            double divOff = startOff + di * stallSpacing;
                            double x1, y1, x2, y2;
                            if (backIsEW) {
                                double divY = room.minY() + divOff;
                                if (backWall.equals("east")) {
                                    x1 = room.maxX() - dividerDepth; x2 = room.maxX();
                                } else {
                                    x1 = room.minX(); x2 = room.minX() + dividerDepth;
                                }
                                y1 = divY; y2 = divY;
                            } else {
                                double divX = room.minX() + divOff;
                                if (backWall.equals("north")) {
                                    y1 = room.maxY() - dividerDepth; y2 = room.maxY();
                                } else {
                                    y1 = room.minY(); y2 = room.minY() + dividerDepth;
                                }
                                x1 = divX; x2 = divX;
                            }
                            walls.add(compileWall(
                                "STALL_" + room.name() + "_" + di,
                                x1, y1, x2, y2,
                                baseZ, baseZ + stallHeight,
                                storey.name(), registry, 0.05));
                        }
                        System.out.printf("[STALL] %s: %d dividers for %d stalls%n",
                            room.name(), tc - 1, tc);
                    }
                } else if (roomType.equals("KITCHEN")) {
                    // Find exterior wall for this room (sink goes under window)
                    String exteriorWall = findExteriorWall(room, minX, minY, maxX, maxY);

                    var placed = fixturePlacer.placeKitchenFixtures(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, exteriorWall
                    );

                    int fixtureIdx = 0;
                    for (var f : placed) {
                        fixtures.add(new FixtureSpec(
                            room.name() + "_" + f.type().name().toLowerCase() + "_" + (++fixtureIdx),
                            room.name(),
                            f.type().name().toLowerCase(),
                            f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z(),
                            f.rotation(),
                            f.geometryHash(),
                            f.localBounds().width(), f.localBounds().depth(), f.localBounds().height()
                        ));
                    }
                }
            }

            // Phase 59/61: Auto-place furniture with BOM resolution
            var furniturePlacer = new com.bim.compiler.library.FurniturePlacer(library);

            // Phase 61: Initialize BOM resolver for code-backed quantities
            BOMResolver bomResolver = null;
            Map<String, BOMResolver.RoomBOM> roomBOMs = new HashMap<>();
            try {
                bomResolver = new BOMResolver();
                for (RoomSpec room : rooms) {
                    double roomArea = (room.maxX() - room.minX()) * (room.maxY() - room.minY());
                    BOMResolver.RoomBOM bom = bomResolver.resolveRoom(
                        room.name(), room.type().toUpperCase(), roomArea, 0);
                    roomBOMs.put(room.name(), bom);
                }
            } catch (SQLException e) {
                System.out.println("[BOM] BOMResolver not available: " + e.getMessage());
            }

            for (RoomSpec room : rooms) {
                String roomType = room.type().toUpperCase();

                // Get BOM-resolved furniture quantity (default to -1 = auto-calculate)
                BOMResolver.RoomBOM bom = roomBOMs.get(room.name());
                int furnitureQty = (bom != null) ? bom.getQuantity("FURNITURE") : -1;

                if (roomType.equals("CANTEEN") || roomType.equals("KANTIN") || roomType.equals("CAFETERIA") || roomType.equals("DINING")) {
                    // Keep canteen-specific table layout
                    var placed = furniturePlacer.placeCanteenFurniture(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, room.name(), furnitureQty
                    );

                    int furnitureIdx = 0;
                    for (var f : placed) {
                        fixtures.add(new FixtureSpec(
                            room.name() + "_" + f.type().name().toLowerCase() + "_" + (++furnitureIdx),
                            room.name(),
                            f.type().name().toLowerCase(),
                            f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z(),
                            f.rotation(),
                            f.geometryHash(),
                            f.localBounds().width(), f.localBounds().depth(), f.localBounds().height()
                        ));
                    }
                } else if (roomType.contains("CORRIDOR") || room.name().toLowerCase().contains("corridor")) {
                    // Phase 92C: Corridors are circulation-only — no furniture
                } else if (!roomType.contains("SHAFT") &&
                           !roomType.contains("TNB") && !roomType.contains("PUMP") &&
                           !roomType.contains("GENSET") && !roomType.contains("MACHINE") &&
                           !roomType.contains("TANK") && !roomType.contains("BATHROOM") &&
                           !roomType.contains("TOILET") && !roomType.contains("WC") &&
                           !roomType.contains("RISER")) {
                    // Phase 93: BOM-driven furniture with opening avoidance
                    var openingInfos = room.openings() == null ? java.util.List.<com.bim.compiler.library.FurnitureBOMResolver.OpeningInfo>of()
                        : room.openings().stream()
                            .map(o -> new com.bim.compiler.library.FurnitureBOMResolver.OpeningInfo(o.type(), o.wall(), o.width()))
                            .toList();
                    var placed = furniturePlacer.placeUniversalFurniture(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, room.name(), roomType, openingInfos
                    );
                    int furnitureIdx = 0;
                    for (var f : placed) {
                        fixtures.add(new FixtureSpec(
                            room.name() + "_" + f.type().name().toLowerCase() + "_" + (++furnitureIdx),
                            room.name(), f.type().name().toLowerCase(),
                            f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z(),
                            f.rotation(), f.geometryHash(),
                            f.localBounds().width(), f.localBounds().depth(), f.localBounds().height()
                        ));
                    }
                }
            }

            // Close BOM resolver
            if (bomResolver != null) {
                try { bomResolver.close(); } catch (SQLException ignored) {}
            }
        } catch (Exception e) {
            // Library not available - skip fixture/furniture placement
            System.out.println("[FIXTURE/FURNITURE] Library not available: " + e.getMessage());
        }

        // =====================================================================
        // Phase 23: Auto-place structural elements (columns, lintels)
        // =====================================================================
        List<ColumnSpec> columns = new ArrayList<>();
        List<BeamSpec> beams = new ArrayList<>();

        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var structuralPlacer = new com.bim.compiler.library.StructuralPlacer(library);

            // Build wall info for T-junction detection
            List<com.bim.compiler.library.StructuralPlacer.WallInfo> interiorWalls = new ArrayList<>();
            for (WallAssemblySpec wall : walls) {
                if (wall.assemblyName().startsWith("INTERIOR_")) {
                    // Interior walls are vertical or horizontal lines
                    double x1, y1, x2, y2;
                    if (wall.side().equals("INTERIOR")) {
                        continue; // Skip, need actual coordinates
                    }
                    // We'll detect from room adjacencies instead
                }
            }

            // Detect interior walls from room shared edges
            for (int i = 0; i < storey.rooms().size(); i++) {
                for (int j = i + 1; j < storey.rooms().size(); j++) {
                    RoomDef room1 = storey.rooms().get(i);
                    RoomDef room2 = storey.rooms().get(j);
                    double[] b1 = roomBounds.get(room1.name());
                    double[] b2 = roomBounds.get(room2.name());

                    if (b1 != null && b2 != null) {
                        SharedEdge edge = findSharedEdge(
                            new RoomBounds(b1[0], b1[1], b1[2], b1[3]),
                            new RoomBounds(b2[0], b2[1], b2[2], b2[3]));
                        if (edge != null) {
                            interiorWalls.add(new com.bim.compiler.library.StructuralPlacer.WallInfo(
                                edge.x1(), edge.y1(), edge.x2(), edge.y2(), true));
                        }
                    }
                }
            }

            // Find corners and T-junctions
            List<Point3D> corners = structuralPlacer.findCorners(minX, minY, maxX, maxY, baseZ);
            List<Point3D> tJunctions = structuralPlacer.findTJunctions(
                interiorWalls, minX, minY, maxX, maxY, baseZ);

            // Phase 2 Contract Architecture: Place columns using registry for junction tracking
            // Columns at same XY position across storeys share continuityId
            var placedColumns = structuralPlacer.placeColumns(
                corners, tJunctions, baseZ, storey.height(), storey.name(), registry);
            for (var col : placedColumns) {
                columns.add(new ColumnSpec(
                    col.id(),
                    col.type().name().toLowerCase(),
                    col.basePosition().x(), col.basePosition().y(), col.basePosition().z(),
                    col.height(),
                    col.width(), col.depth(),
                    col.geometryHash(),
                    col.continuityId(),  // Phase 2: Cross-storey identity
                    storey.name()        // Phase 5A: Contract storey
                ));
            }

            // Build opening info for lintel placement
            List<com.bim.compiler.library.StructuralPlacer.OpeningInfo> openings = new ArrayList<>();
            for (DoorSpec door : doors) {
                openings.add(new com.bim.compiler.library.StructuralPlacer.OpeningInfo(
                    door.name(), door.wall(),
                    door.x(), door.y(),
                    door.width(), door.height()
                ));
            }
            for (WindowSpec window : windows) {
                // Window head height = sill + window height (for lintel placement)
                double headHeight = window.sillHeight() + window.height();
                openings.add(new com.bim.compiler.library.StructuralPlacer.OpeningInfo(
                    window.name(), window.wall(),
                    window.x(), window.y(),
                    window.width(), headHeight
                ));
            }

            // Place lintels over openings
            var placedBeams = structuralPlacer.placeLintels(openings, baseZ);
            for (var beam : placedBeams) {
                beams.add(new BeamSpec(
                    beam.id(),
                    beam.type().name().toLowerCase(),
                    beam.position().x(), beam.position().y(), beam.position().z(),
                    beam.length(),
                    beam.width(), beam.height(),
                    beam.rotation(),
                    beam.geometryHash()
                ));
            }

            // Phase 50B.1: Place grid beams and columns for large-span rooms with structural_grid
            for (RoomSpec room : rooms) {
                SpaceTypeRegistry.SpaceTypeConfig spaceConfig = SpaceTypeRegistry.get(room.type());
                if (spaceConfig != null && spaceConfig.structural().structuralGrid()) {
                    double beamMaxSpan = spaceConfig.structural().beamMaxSpan();
                    BoundingBox gridRoomBounds = new BoundingBox(
                        room.minX(), room.maxX(),
                        room.minY(), room.maxY(),
                        room.minZ(), room.maxZ()
                    );

                    // Place grid beams (at ceiling level)
                    // Phase 51.1: Pass building grid for DSL-aligned beam placement
                    double gridCeilingZ = baseZ + storey.height() - 0.3; // Beam below ceiling
                    var gridBeams = structuralPlacer.placeGridBeams(
                        gridRoomBounds, beamMaxSpan, gridCeilingZ, room.name(), building.grid());
                    for (var beam : gridBeams) {
                        beams.add(new BeamSpec(
                            beam.id(),
                            beam.type().name().toLowerCase(),
                            beam.position().x(), beam.position().y(), beam.position().z(),
                            beam.length(),
                            beam.width(), beam.height(),
                            beam.rotation(),
                            beam.geometryHash()
                        ));
                    }

                    // Place grid columns (at beam intersections)
                    // Phase 51.1: Pass building grid for DSL-aligned column placement
                    var gridColumns = structuralPlacer.placeGridColumns(
                        gridRoomBounds, beamMaxSpan, baseZ, storey.height(), room.name(), building.grid());
                    for (var col : gridColumns) {
                        columns.add(new ColumnSpec(
                            col.id(),
                            col.type().name().toLowerCase(),
                            col.basePosition().x(), col.basePosition().y(), col.basePosition().z(),
                            col.height(),
                            col.width(), col.depth(),
                            col.geometryHash(),
                            null,            // No continuityId for grid columns
                            storey.name()    // Phase 5A: Contract storey
                        ));
                    }
                }
            }

            // Phase 99A: Building-wide RC frame beam grid along ALL structural grid lines
            if (building.grid() != null) {
                BoundingBox envelope = new BoundingBox(
                    minX, maxX, minY, maxY, baseZ, baseZ + storey.height());
                double gridCeilingZ = baseZ + storey.height() - 0.3;
                var frameBeams = structuralPlacer.placeGridBeams(
                    envelope, com.bim.compiler.library.StructuralPlacer.MAX_BEAM_SPAN_FRAMED,
                    gridCeilingZ, "FRAME", building.grid());
                for (var beam : frameBeams) {
                    beams.add(new BeamSpec(
                        beam.id(), beam.type().name().toLowerCase(),
                        beam.position().x(), beam.position().y(), beam.position().z(),
                        beam.length(), beam.width(), beam.height(),
                        beam.rotation(), beam.geometryHash()));
                }
                var frameColumns = structuralPlacer.placeGridColumns(
                    envelope, com.bim.compiler.library.StructuralPlacer.MAX_BEAM_SPAN_FRAMED,
                    baseZ, storey.height(), "FRAME", building.grid());
                for (var col : frameColumns) {
                    columns.add(new ColumnSpec(
                        col.id(), col.type().name().toLowerCase(),
                        col.basePosition().x(), col.basePosition().y(), col.basePosition().z(),
                        col.height(), col.width(), col.depth(),
                        col.geometryHash(), null, storey.name()));
                }
                System.out.printf("[FRAME] Storey %s: +%d frame beams, +%d frame columns%n",
                    storey.name(), frameBeams.size(), frameColumns.size());
            }

            System.out.printf("[STRUCTURAL] Storey %s: %d columns, %d lintels/beams%n",
                storey.name(), columns.size(), beams.size());

        } catch (Exception e) {
            // Library not available - skip structural placement
            System.out.println("[STRUCTURAL] Library not available: " + e.getMessage());
        }

        // =====================================================================
        // Phase 24: Auto-place HVAC diffusers (supply/return/exhaust)
        // =====================================================================
        List<DiffuserSpec> diffusers = new ArrayList<>();

        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var hvacPlacer = new com.bim.compiler.library.HVACPlacer(library);

            for (RoomSpec room : rooms) {
                // Phase 92C: Use room name for type inference when type is GENERIC
                String roomType = room.type();
                if ("GENERIC".equals(roomType) && room.name().toLowerCase().contains("stair")) {
                    roomType = "STAIR";
                }

                // Skip very small rooms (< 4 m²) — corridors now get HVAC via VentilationRate.CORRIDOR
                double roomArea = (room.maxX() - room.minX()) * (room.maxY() - room.minY());
                if (roomArea < 4.0) {
                    continue;
                }
                // Phase 92B: Stair enclosures now get full HVAC coverage (not pressurized in this building type)

                var layout = hvacPlacer.placeRoomHVAC(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    baseZ, ceilingZ + 0.05, roomType
                );

                // Convert to DiffuserSpecs
                for (var d : layout.allDiffusers()) {
                    diffusers.add(new DiffuserSpec(
                        room.name() + "_" + d.id(),
                        room.name(),
                        d.function(),  // "supply", "return", "exhaust"
                        d.position().x(), d.position().y(), d.position().z(),
                        d.cfmRating(),
                        d.geometryHash()
                    ));
                }
            }

            // Phase 92B: Load ceiling fan geometry hash from library
            String ceilingFanHash = null;
            try {
                var fanDef = library.getByName("E_Fan_Ceiling_1500mm");
                if (fanDef != null) ceilingFanHash = fanDef.geometryHash();
            } catch (Exception ignored) {}

            // Phase 91+92B: Ensure at least 1 IfcFan per habitable room; big rooms get 2
            Set<String> roomsWithExhaust = new HashSet<>();
            for (var d : diffusers) {
                if ("exhaust".equals(d.diffuserType())) roomsWithExhaust.add(d.roomName());
            }

            int fanCount = 0;
            for (RoomSpec room : rooms) {
                if (roomsWithExhaust.contains(room.name())) continue;
                double area = (room.maxX() - room.minX()) * (room.maxY() - room.minY());
                if (area < 4.0) continue;

                double roomW = room.maxX() - room.minX();
                double roomD = room.maxY() - room.minY();
                // Phase 92C: Big rooms (≥80m², w≥3, d≥3) get 2 fan sets — raised from 50 to avoid cramming stairs
                int setCount = (area >= 80.0 && roomW >= 3.0 && roomD >= 3.0) ? 2 : 1;

                for (int setIdx = 0; setIdx < setCount; setIdx++) {
                    // Calculate zone center
                    double cx, cy;
                    if (setCount == 1) {
                        cx = (room.minX() + room.maxX()) / 2;
                        cy = (room.minY() + room.maxY()) / 2;
                    } else {
                        // Split along longer axis at 25%/75%
                        if (roomD >= roomW) {
                            cx = (room.minX() + room.maxX()) / 2;
                            cy = room.minY() + roomD * (setIdx == 0 ? 0.25 : 0.75);
                        } else {
                            cx = room.minX() + roomW * (setIdx == 0 ? 0.25 : 0.75);
                            cy = (room.minY() + room.maxY()) / 2;
                        }
                    }

                    // Offset fan along shorter axis to avoid overlap with supply/return cluster
                    if (roomW >= roomD) {
                        double fanOffsetY = Math.min(1.0, roomD * 0.20);
                        cy -= fanOffsetY;
                    } else {
                        double fanOffsetX = Math.min(1.0, roomW * 0.20);
                        cx -= fanOffsetX;
                    }
                    diffusers.add(new DiffuserSpec(
                        room.name() + "_ceiling_fan_" + (setIdx + 1), room.name(), "exhaust",
                        cx, cy, ceilingZ, 50, ceilingFanHash
                    ));
                    fanCount++;
                }
            }

            System.out.printf("[HVAC] Storey %s: %d diffusers (%d ceiling fans added)%n",
                storey.name(), diffusers.size(), fanCount);

        } catch (Exception e) {
            // Library not available - skip HVAC placement
            System.out.println("[HVAC] Library not available: " + e.getMessage());
        }

        // =====================================================================
        // Phase 33: Auto-place electrical elements (lights, outlets, switches)
        // Phase 44: With T-junction column avoidance
        // =====================================================================
        List<ElectricalSpec> electricals = new ArrayList<>();
        // Surface-mounted lights attach directly to ceiling (no offset)
        double actualCeilingZ = baseZ + storey.height();
        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var electricalPlacer = new com.bim.compiler.library.ElectricalPlacer(library);

            // Phase 44: Build column zones for clash avoidance
            List<com.bim.compiler.library.ElectricalPlacer.ColumnZone> columnZones = new ArrayList<>();
            for (ColumnSpec col : columns) {
                columnZones.add(new com.bim.compiler.library.ElectricalPlacer.ColumnZone(
                    col.x(), col.y(),
                    col.width() / 2, col.depth() / 2
                ));
            }
            electricalPlacer.setColumnZones(columnZones);

            // Phase 90: Calculate sprinkler offset for light grid staggering
            // Use half the typical sprinkler spacing (2.3m / 2 = 1.15m)
            double sprinklerOffset = 1.15;
            if (!roomsWithMEP.isEmpty()) {
                for (var rm : roomsWithMEP) {
                    if (rm.sprinklerSpacing() != null) {
                        sprinklerOffset = rm.sprinklerSpacing() / 2.0;
                        break;
                    }
                }
            }
            electricalPlacer.setLightGridOffset(sprinklerOffset, sprinklerOffset);

            // Phase 44: Generate DSL-specified lights with column avoidance
            // These are rooms with "LIGHTS grid:X.Xm" in DSL
            for (var roomMEP : roomsWithMEP) {
                if (roomMEP.lightSpacing() == null) continue;

                double roomWidth = roomMEP.maxX() - roomMEP.minX();
                double roomDepth = roomMEP.maxY() - roomMEP.minY();
                double spacing = roomMEP.lightSpacing();
                int numX = (int) Math.ceil(roomWidth / spacing);
                int numY = (int) Math.ceil(roomDepth / spacing);
                double startX = roomMEP.minX() + (roomWidth - (numX - 1) * spacing) / 2;
                double startY = roomMEP.minY() + (roomDepth - (numY - 1) * spacing) / 2;

                int lightIndex = 0;
                double lightHeight = 0.1;  // Default light height
                double lightCeilingZ = baseZ + storey.height();

                for (int ix = 0; ix < numX; ix++) {
                    for (int iy = 0; iy < numY; iy++) {
                        double x = startX + ix * spacing;
                        double y = startY + iy * spacing;

                        // Phase 44: Apply column avoidance
                        double[] adjusted = avoidColumnZones(x, y, roomMEP.minX(), roomMEP.minY(),
                                                             roomMEP.maxX(), roomMEP.maxY(), columnZones);
                        x = adjusted[0];
                        y = adjusted[1];

                        double lightZ = lightCeilingZ - lightHeight;
                        lights.add(new LightSpec(
                            roomMEP.name() + "_light_" + (++lightIndex),
                            roomMEP.name(),
                            x, y, lightZ,
                            "surface",
                            spacing
                        ));
                    }
                }
            }

            for (RoomSpec room : rooms) {
                // Get MEP config from SpaceTypeRegistry
                var spaceConfig = SpaceTypeRegistry.get(room.type());
                var elecConfig = spaceConfig.mep().electrical();

                // Skip rooms with no electrical requirements
                if (elecConfig.lightPoints() == 0 && elecConfig.powerPoints() == 0 && elecConfig.switchPoints() == 0) {
                    continue;
                }

                var placed = electricalPlacer.placeElectricalElements(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    baseZ, actualCeilingZ,  // Use actual ceiling for surface-mount
                    elecConfig,
                    room.name()
                );

                // Phase 42: Check if room already has DSL-specified lights
                final String roomNameFinal = room.name();
                boolean hasDslLights = lights.stream()
                    .anyMatch(l -> l.roomName().equals(roomNameFinal));

                int elementIdx = 0;
                for (var e : placed) {
                    if (e.type() == com.bim.compiler.library.ElectricalPlacer.ElectricalType.LIGHT) {
                        // Skip if DSL already specified lights for this room
                        if (hasDslLights) {
                            continue;
                        }
                        // Add to lights list (with library support)
                        lights.add(new LightSpec(
                            room.name() + "_light_" + (++elementIdx),
                            room.name(),
                            e.worldPosition().x(), e.worldPosition().y(), e.worldPosition().z(),
                            e.name(),
                            0,  // spacing (not used for single placement)
                            e.geometryHash(),
                            e.width(), e.depth(), e.height()
                        ));
                    } else {
                        // Add outlets and switches to electricals list
                        electricals.add(new ElectricalSpec(
                            room.name() + "_" + e.type().name().toLowerCase() + "_" + (++elementIdx),
                            room.name(),
                            e.type().name().toLowerCase(),
                            e.worldPosition().x(), e.worldPosition().y(), e.worldPosition().z(),
                            e.rotation(),
                            e.width(), e.depth(), e.height(),
                            e.circuitType()  // Phase 39
                        ));
                    }
                }
            }

            System.out.printf("[ELEC] Storey %s: %d lights, %d outlets/switches%n",
                storey.name(), lights.size(), electricals.size());

        } catch (Exception e) {
            // Library not available - skip electrical placement
            System.out.println("[ELEC] Library not available: " + e.getMessage());
        }

        // =====================================================================
        // Phase 34: Auto-place plumbing pipes (risers, vents, branches)
        // =====================================================================
        List<PlumbingSpec> plumbing = new ArrayList<>();
        double plumbingCeilingZ = baseZ + storey.height();
        double roofZ = plumbingCeilingZ + 0.5;  // Estimate roof 500mm above ceiling

        try {
            var plumbingPlacer = new com.bim.compiler.library.PlumbingPlacer();

            for (RoomSpec room : rooms) {
                // Get MEP config from SpaceTypeRegistry
                var spaceConfig = SpaceTypeRegistry.get(room.type());
                var plumbingConfig = spaceConfig.mep().plumbing();

                if (plumbingConfig == null || !plumbingConfig.requiresStack()) {
                    continue;
                }

                // Find toilet and sink positions for this room
                boolean hasToilet = false;
                double toiletX = 0, toiletY = 0;
                boolean hasSink = false;
                double sinkX = 0, sinkY = 0;

                for (FixtureSpec fixture : fixtures) {
                    if (fixture.roomName().equals(room.name())) {
                        if (fixture.fixtureType().equalsIgnoreCase("toilet")) {
                            hasToilet = true;
                            toiletX = fixture.x();
                            toiletY = fixture.y();
                        } else if (fixture.fixtureType().equalsIgnoreCase("sink")) {
                            hasSink = true;
                            sinkX = fixture.x();
                            sinkY = fixture.y();
                        }
                    }
                }

                var pipes = plumbingPlacer.placeRoomPlumbing(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    baseZ, plumbingCeilingZ, roofZ,
                    plumbingConfig,
                    room.name(),
                    hasToilet, toiletX, toiletY,
                    hasSink, sinkX, sinkY
                );

                for (var pipe : pipes) {
                    plumbing.add(new PlumbingSpec(
                        pipe.name(),
                        room.name(),
                        pipe.type().name().toLowerCase(),
                        pipe.start().x(), pipe.start().y(), pipe.start().z(),
                        pipe.end().x(), pipe.end().y(), pipe.end().z(),
                        pipe.diameterM()
                    ));
                }
            }

            if (!plumbing.isEmpty()) {
                System.out.printf("[PLUMB] Storey %s: %d pipes%n", storey.name(), plumbing.size());
            }

        } catch (Exception e) {
            // Plumbing placer not available - skip
            System.out.println("[PLUMB] PlumbingPlacer error: " + e.getMessage());
        }

        // =====================================================================
        // Phase 92D: Data-driven MEP gap-fill using ad_space_type_mep_bom.
        // Replaces hardcoded guarantee loop with BOM-resolved quantities.
        // =====================================================================
        {
            Set<String> exemptKeywords = Set.of("shaft", "riser", "void",
                "tnb", "pump", "genset", "tank", "machine");

            // Load light geometry hash for LOD400 guarantee lights
            String guarLightHash = null;
            double guarLightW = 0.6, guarLightD = 0.6, guarLightH = 0.1;
            try {
                var libTemp = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
                var lightDef = libTemp.getComponentWithFallback("14W_Surface_LED", "guarantee");
                if (lightDef == null) lightDef = libTemp.getComponentWithFallback("28W_Surface_LED", "guarantee");
                if (lightDef != null) {
                    guarLightHash = lightDef.geometryHash();
                    guarLightW = lightDef.localBounds().width();
                    guarLightD = lightDef.localBounds().depth();
                    guarLightH = lightDef.localBounds().height();
                }
            } catch (Exception ignored) {}

            // Index existing MEP by room name
            Set<String> roomsWithSprinkler = new HashSet<>();
            for (var s : sprinklers) roomsWithSprinkler.add(s.roomName());
            Set<String> roomsWithLight = new HashSet<>();
            for (var l : lights) roomsWithLight.add(l.roomName());
            Set<String> roomsWithSupply = new HashSet<>();
            Set<String> roomsWithFan = new HashSet<>();
            for (var d : diffusers) {
                if ("exhaust".equals(d.diffuserType())) roomsWithFan.add(d.roomName());
                else roomsWithSupply.add(d.roomName());
            }

            MEPBOMResolver mepResolver = new MEPBOMResolver();
            int added = 0;
            for (RoomSpec room : rooms) {
                String nameLower = room.name().toLowerCase();
                boolean exempt = false;
                for (String kw : exemptKeywords) {
                    if (nameLower.contains(kw)) { exempt = true; break; }
                }
                if (exempt) continue;

                double roomW = room.maxX() - room.minX();
                double roomD = room.maxY() - room.minY();
                double area = roomW * roomD;
                if (area < 4.0) continue;

                // Resolve MEP requirements from BOM table
                List<MEPBOMResolver.MEPSet> mepSets = mepResolver.resolveForRoom(
                    room.type(), room.name(), area, roomW, roomD);

                // Determine set count from BOM (big rooms get multiplied quantities)
                // The resolver already handles the multiplier, but we need zone positions
                boolean bigRoom = area >= 80.0 && roomW >= 3.0 && roomD >= 3.0;
                int setCount = bigRoom ? 2 : 1;

                for (int setIdx = 0; setIdx < setCount; setIdx++) {
                    // Calculate zone center
                    double cx, cy;
                    if (setCount == 1) {
                        cx = (room.minX() + room.maxX()) / 2;
                        cy = (room.minY() + room.maxY()) / 2;
                    } else {
                        if (roomD >= roomW) {
                            cx = (room.minX() + room.maxX()) / 2;
                            cy = room.minY() + roomD * (setIdx == 0 ? 0.25 : 0.75);
                        } else {
                            cx = room.minX() + roomW * (setIdx == 0 ? 0.25 : 0.75);
                            cy = (room.minY() + room.maxY()) / 2;
                        }
                    }
                    String suffix = "_" + (setIdx + 1);

                    // Offset each MEP type to different quadrant to avoid overlap
                    double ox = Math.min(0.6, roomW * 0.15);
                    double oy = Math.min(0.6, roomD * 0.15);

                    // Place each BOM-resolved product (gap-fill only)
                    for (MEPBOMResolver.MEPSet mep : mepSets) {
                        switch (mep.productId()) {
                            case "SPRINKLER" -> {
                                if (!roomsWithSprinkler.contains(room.name())) {
                                    sprinklers.add(new SprinklerSpec(
                                        room.name() + "_bom_sprinkler" + suffix, room.name(),
                                        cx - ox, cy + oy, sprinklerZ, "pendant", 0
                                    ));
                                    added++;
                                }
                            }
                            case "LIGHT" -> {
                                if (!roomsWithLight.contains(room.name())) {
                                    lights.add(new LightSpec(
                                        room.name() + "_bom_light" + suffix, room.name(),
                                        cx + ox, cy + oy, actualCeilingZ - 0.1, "surface", 0,
                                        guarLightHash, guarLightW, guarLightD, guarLightH
                                    ));
                                    added++;
                                }
                            }
                            case "SUPPLY_DIFFUSER" -> {
                                if (!roomsWithSupply.contains(room.name())) {
                                    diffusers.add(new DiffuserSpec(
                                        room.name() + "_bom_supply" + suffix, room.name(), "supply",
                                        cx + ox, cy - oy, ceilingZ, 100, null
                                    ));
                                    added++;
                                }
                            }
                            case "CEILING_FAN" -> {
                                if (!roomsWithFan.contains(room.name())) {
                                    diffusers.add(new DiffuserSpec(
                                        room.name() + "_bom_fan" + suffix, room.name(), "exhaust",
                                        cx - ox, cy - oy, ceilingZ, 50, null
                                    ));
                                    added++;
                                }
                            }
                        }
                    }
                }
            }
            if (added > 0) {
                System.out.printf("[MEP-BOM] Storey %s: %d elements added to fill gaps%n",
                    storey.name(), added);
            }
        }

        // Phase 3 Debug: Count total frames to verify deduplication
        int totalFrames = walls.stream().mapToInt(w -> w.frames().size()).sum();
        System.out.printf("[PHASE3] Storey %s: %d walls, %d total frames%n",
            storey.name(), walls.size(), totalFrames);

        return new StoreySpec(
            storey.name(), storey.level(), baseZ, storey.height(),
            slab, walls, rooms, stairs, doors, windows, landings,
            sprinklers, lights, fixtures, columns, beams, diffusers, electricals, plumbing,
            elevators, lobbies, shafts,  // Phase 56B: CORE elements
            List.of()  // Phase 100: alarms (populated by StandardsResolver)
        );
    }

    static String findExteriorWall(RoomSpec room, double bldgMinX, double bldgMinY,
                                           double bldgMaxX, double bldgMaxY) {
        double tolerance = 0.01;
        if (Math.abs(room.minY() - bldgMinY) < tolerance) return "south";
        if (Math.abs(room.maxY() - bldgMaxY) < tolerance) return "north";
        if (Math.abs(room.minX() - bldgMinX) < tolerance) return "west";
        if (Math.abs(room.maxX() - bldgMaxX) < tolerance) return "east";
        return null;
    }

    /** Phase 96B: Look up exterior walls from the RoomDef (parsed DSL) matching a compiled RoomSpec by name. */
    static List<String> findRoomDefExteriorWalls(StoreyDef storey, String roomName) {
        for (var rd : storey.rooms()) {
            if (rd.name().equals(roomName)) {
                return rd.getAllExteriorWalls();
            }
        }
        return List.of();
    }

    /** Phase 94A: Find the wall that has a door opening, for toilet fixture layout. Default "south".
     *  Opening type is schedule ref (e.g. "D2") — doors start with "D", windows with "W". */
    static String findDoorWall(RoomSpec room) {
        if (room.openings() != null) {
            for (var opening : room.openings()) {
                String t = opening.type();
                if (t != null && t.toUpperCase().startsWith("D") && opening.wall() != null) {
                    return opening.wall().toLowerCase();
                }
            }
        }
        return "south";
    }

    /**
     * Backward-compatible compileWall without registry.
     * Creates all studs (no deduplication).
     */
    static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName) {
        return compileWall(side, x1, y1, x2, y2, minZ, maxZ, storeyName, null);
    }

    // Phase 99B: Overload with custom wall thickness (e.g. 50mm stall dividers)
    static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry,
                                                 double wallThickness) {
        return compileWallInternal(side, x1, y1, x2, y2, minZ, maxZ, storeyName, registry, wallThickness);
    }

    // Phase 99C: Overload with custom cladding material (e.g. glass facade)
    static WallAssemblySpec compilePerimeterWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry,
                                                 String claddingMaterial) {
        return compileWallInternal(side, x1, y1, x2, y2, minZ, maxZ, storeyName, registry,
            BIMConstants.STANDARD_WALL_THICKNESS, claddingMaterial);
    }

    /**
     * Phase 3 Contract Architecture: Compile wall with registry for stud deduplication.
     *
     * When multiple walls meet at a corner, only the FIRST wall to register that
     * junction creates the corner stud. Subsequent walls reference the shared
     * junction but don't create duplicate studs.
     *
     * @param registry SharedElementRegistry for junction tracking (null = no deduplication)
     */
    static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry) {
        return compileWallInternal(side, x1, y1, x2, y2, minZ, maxZ, storeyName, registry,
            BIMConstants.STANDARD_WALL_THICKNESS);
    }

    // Phase 99B/99C: Internal wall compilation with configurable thickness and material
    private static WallAssemblySpec compileWallInternal(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry,
                                                 double wallThickness) {
        return compileWallInternal(side, x1, y1, x2, y2, minZ, maxZ, storeyName, registry, wallThickness, "Metal Deck");
    }

    private static WallAssemblySpec compileWallInternal(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry,
                                                 double wallThickness,
                                                 String claddingMaterial) {
        // Calculate wall dimensions
        double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double height = maxZ - minZ;

        // Wall extends thickness perpendicular to line
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);

        // Normal direction (perpendicular, pointing inward)
        double nx = -dy / len * wallThickness;
        double ny = dx / len * wallThickness;

        // Frame members
        List<FrameSpec> frames = new ArrayList<>();

        // Bottom rail (always created - runs full length)
        frames.add(new FrameSpec(
            "RAIL_BOTTOM", "RHS 150x100",
            x1, y1, minZ,
            x2, y2, minZ + 0.15
        ));

        // Top rail (always created - runs full length)
        frames.add(new FrameSpec(
            "RAIL_TOP", "RHS 150x100",
            x1, y1, maxZ - 0.15,
            x2, y2, maxZ
        ));

        // Phase 3 Contract Architecture: Studs at ends with deduplication
        // Only create stud if this wall is FIRST to claim that junction
        boolean createStudL = true;
        boolean createStudR = true;
        int deduplicatedCount = 0;

        if (registry != null) {
            // Check STUD_L position (wall start at x1, y1)
            Point3D studLPoint = new Point3D(x1, y1, 0);  // Z=0 for XY junction
            com.bim.compiler.contract.JunctionPoint junctionL =
                registry.getOrCreateJunction(studLPoint, com.bim.compiler.contract.JunctionType.CORNER);

            // If junction already has connected elements, another wall owns this stud
            if (!junctionL.connectedElements().isEmpty()) {
                createStudL = false;  // Skip - another wall already created stud here
                deduplicatedCount++;
            } else {
                // This wall claims the junction
                junctionL.addConnection(side + "_STUD_L_" + storeyName);
            }

            // Check STUD_R position (wall end at x2, y2)
            Point3D studRPoint = new Point3D(x2, y2, 0);
            com.bim.compiler.contract.JunctionPoint junctionR =
                registry.getOrCreateJunction(studRPoint, com.bim.compiler.contract.JunctionType.CORNER);

            if (!junctionR.connectedElements().isEmpty()) {
                createStudR = false;  // Skip - another wall already created stud here
                deduplicatedCount++;
            } else {
                junctionR.addConnection(side + "_STUD_R_" + storeyName);
            }

            if (deduplicatedCount > 0) {
                System.out.printf("[DEDUP] Wall %s: %d stud(s) skipped (shared corner)%n",
                    side, deduplicatedCount);
            }
        }

        // Create studs only if not deduplicated
        if (createStudL) {
            frames.add(new FrameSpec(
                "STUD_L", "RHS 150x100",
                x1, y1, minZ,
                x1 + 0.1, y1 + 0.15, maxZ
            ));
        }

        if (createStudR) {
            frames.add(new FrameSpec(
                "STUD_R", "RHS 150x100",
                x2 - 0.1, y2 - 0.15, minZ,
                x2, y2, maxZ
            ));
        }

        // Cladding
        CladdingSpec cladding = new CladdingSpec(
            claddingMaterial,
            x1, y1, minZ,
            x2 + nx, y2 + ny, maxZ
        );

        // Phase 49: Include position in perimeter wall name to avoid GUID collisions
        // when multiple units/SHARED areas contribute to the same storey
        String posHash = String.format("%.0f_%.0f", x1, y1);
        return new WallAssemblySpec(
            side + "_" + posHash + "_WALL_ASSEMBLY",
            "WALL_PANEL",
            side,
            length, wallThickness, height,
            storeyName,
            frames,
            cladding
        );
    }

    static StairSpec compileStair(StairDef stair, double x, double y,
                                          double baseZ, double storeyHeight) {
        // IRC compliant stair calculation
        int numRisers = (int) Math.ceil(storeyHeight / BIMConstants.IRC_MAX_RISER_HEIGHT);
        double actualRiser = storeyHeight / numRisers;
        double actualTread = Math.max(BIMConstants.IRC_MIN_TREAD_DEPTH, 0.267); // 267mm standard

        double stairRun = actualTread * (numRisers - 1);
        double stairWidth = Math.max(stair.width(), BIMConstants.IRC_MIN_STAIR_WIDTH);

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

        // Phase 82: For CORE stairs, toStorey holds stair type (e.g., "PROTECTED")
        String stairType = (stair.pressurized() || stair.fireRatingHr() > 0)
            ? stair.toStorey() : null;

        return new StairSpec(
            stair.name(),
            stair.toStorey(),
            x, y, baseZ,
            stairWidth, stairRun, storeyHeight,
            numRisers, actualRiser, actualTread,
            vertices, faces,
            null, 1.0, 1.0, 1.0,  // No library geometry
            stairType,
            stair.pressurized(),
            stair.fireRatingHr()
        );
    }

    static double calculateStairRun(double height) {
        int numRisers = (int) Math.ceil(height / BIMConstants.IRC_MAX_RISER_HEIGHT);
        double actualTread = Math.max(BIMConstants.IRC_MIN_TREAD_DEPTH, 0.267);
        return actualTread * (numRisers - 1);
    }

    /**
     * Compile roof geometry.
     * Phase 28: Uses parsed overhang and grid-based footprint calculation.
     *
     * Handles PORCH roof overrides:
     * - roof: NONE → exclude from roof coverage
     * - roof: ATTACHED → include in main roof extent
     * - roof: SEPARATE → generates independent roof (future)
     */

    static List<OpeningSpec> compileOpenings(List<OpeningDef> defs) {
        return defs.stream()
            .map(o -> new OpeningSpec(o.type(), o.wall(), o.connectsTo(), o.width(), o.height()))
            .toList();
    }

    // =========================================================================
    // Phase 15B: Interior Wall Helpers
    // =========================================================================

    /** Room bounding box for shared edge detection */

    record RoomBounds(double minX, double minY, double maxX, double maxY) {}

    /** Shared edge between two rooms */
    record SharedEdge(double x1, double y1, double x2, double y2) {
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
    static void snapAdjacentRooms(double[] b1, double[] b2) {
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
     * Phase 86: Check if a door's wall direction faces the opens_to target room.
     * Used to skip DSL-declared doors that will be handled by the opens_to shared-edge mechanism.
     */
    static boolean isOpensToWall(RoomDef room, String doorWall, Map<String, double[]> roomBounds) {
        String target = room.opensTo();
        if (target == null) return false;
        double[] myBounds = roomBounds.get(room.name());
        double[] theirBounds = roomBounds.get(target);
        if (myBounds == null || theirBounds == null) return false;

        double myMinX = myBounds[0], myMinY = myBounds[1], myMaxX = myBounds[2], myMaxY = myBounds[3];
        double theirMinX = theirBounds[0], theirMinY = theirBounds[1], theirMaxX = theirBounds[2], theirMaxY = theirBounds[3];

        return switch (doorWall) {
            case "south" -> Math.abs(myMinY - theirMaxY) < BIMConstants.TOLERANCE;
            case "north" -> Math.abs(myMaxY - theirMinY) < BIMConstants.TOLERANCE;
            case "west"  -> Math.abs(myMinX - theirMaxX) < BIMConstants.TOLERANCE;
            case "east"  -> Math.abs(myMaxX - theirMinX) < BIMConstants.TOLERANCE;
            default -> false;
        };
    }

    /**
     * Find the shared edge between two room bounding boxes.
     * Returns null if rooms don't share an edge (not touching or only corners).
     */
    static SharedEdge findSharedEdge(RoomBounds r1, RoomBounds r2) {
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


    static double[] avoidColumnZones(double x, double y,
                                              double roomMinX, double roomMinY,
                                              double roomMaxX, double roomMaxY,
                                              List<com.bim.compiler.library.ElectricalPlacer.ColumnZone> columnZones) {
        if (columnZones == null || columnZones.isEmpty()) {
            return new double[]{x, y};
        }

        double adjustedX = x;
        double adjustedY = y;
        // Total clearance = light half-size + minimum gap
        // Light is ~600x600mm, so half-size is 0.3m. Add 0.05m gap = 0.35m
        double totalClearance = 0.35;

        // Check each column zone and apply offset if needed
        for (var zone : columnZones) {
            if (zone.contains(adjustedX, adjustedY, totalClearance)) {
                double[] offset = zone.getOffset(adjustedX, adjustedY, totalClearance);
                adjustedX += offset[0];
                adjustedY += offset[1];
            }
        }

        // Clamp to room bounds with margin
        double margin = 0.35;  // Same as clearance to avoid clipping
        adjustedX = Math.max(roomMinX + margin, Math.min(roomMaxX - margin, adjustedX));
        adjustedY = Math.max(roomMinY + margin, Math.min(roomMaxY - margin, adjustedY));

        return new double[]{adjustedX, adjustedY};
    }

    /** Phase 98: Opposite wall helper for stall dividers */
    private static String oppositeWall(String wall) {
        return switch (wall.toLowerCase()) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> "north";
        };
    }
}
