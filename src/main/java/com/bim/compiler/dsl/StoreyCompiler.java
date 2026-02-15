package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.contract.*;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.solver.SpaceSolver.*;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.library.FurnitureWorker;
import com.bim.compiler.library.SlotRegistry;
import com.bim.compiler.library.BeamTypeResolver;
import com.bim.compiler.library.WallTypeResolver;
import com.bim.compiler.library.WorkerRegistry;
import com.bim.compiler.util.OutlierLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
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

    // Phase A: Lazy singleton — slab extend/thickness metadata
    private static SlabSpecAD slabSpecAD;
    static SlabSpecAD getSlabSpecAD() {
        if (slabSpecAD == null) {
            slabSpecAD = new SlabSpecAD();
        }
        return slabSpecAD;
    }

    // Phase 104: Mutable context passed between decomposed compileStorey sub-methods
    static class StoreyBuildContext {
        final StoreyDef storey;
        final double baseZ;
        final boolean isGround, isTop;
        final BuildingDefinition building;
        final SharedElementRegistry registry;

        // Storey envelope (computed during room layout)
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double ceilingZ;    // baseZ + height - 0.05 (general fixture ref)
        double wallMaxZ;   // Phase 122M: interior wall top = storeyHeight - slabThickness (non-top) or full height (top)
        double extWallMaxZ; // Phase 122M: exterior wall top = always full storey height (weather seal)

        // Phase 112: Grid-derived building footprint (maths-proven, from axis spacings)
        double gridMinX = 0, gridMinY = 0, gridMaxX = 0, gridMaxY = 0;

        // Room layout state
        Map<String, double[]> roomBounds = new HashMap<>();   // name → [minX, minY, maxX, maxY]
        Map<String, RoomBounds> roomBoundsMap = new HashMap<>();
        Map<String, RoomDef> roomDefMap = new HashMap<>();

        // Result lists
        List<WallAssemblySpec> walls = new ArrayList<>();
        List<RoomSpec> rooms = new ArrayList<>();
        List<StairSpec> stairs = new ArrayList<>();
        List<DoorSpec> doors = new ArrayList<>();
        List<WindowSpec> windows = new ArrayList<>();
        List<LandingSpec> landings = new ArrayList<>();
        List<SprinklerSpec> sprinklers = new ArrayList<>();
        List<LightSpec> lights = new ArrayList<>();
        List<FixtureSpec> fixtures = new ArrayList<>();
        List<ElevatorSpec> elevators = new ArrayList<>();
        List<ElevatorLobbySpec> lobbies = new ArrayList<>();
        List<ShaftSpec> shafts = new ArrayList<>();
        List<DiffuserSpec> diffusers = new ArrayList<>();
        List<ElectricalSpec> electricals = new ArrayList<>();
        List<PlumbingSpec> plumbing = new ArrayList<>();
        List<ColumnSpec> columns = new ArrayList<>();
        List<BeamSpec> beams = new ArrayList<>();
        SlabSpec slab = null;
        List<SlabSpec> baySlabs = new ArrayList<>();  // Phase 122F: grid-bay structural slabs

        // Phase 108: Unit zone state
        Map<String, FloorPlateBOMResolver.UnitZoneInfo> unitZones;
        List<RoomDef> syntheticRoomDefs;  // for wall/opening generation of unit interior rooms

        // MEP tracking
        List<RoomMEP> roomsWithMEP = new ArrayList<>();
        double sprinklerZ; // resolved in placeMEPSprinklers, used in mepBomGapFill

        StoreyBuildContext(StoreyDef storey, double baseZ, boolean isGround, boolean isTop,
                           BuildingDefinition building, SharedElementRegistry registry) {
            this.storey = storey;
            this.baseZ = baseZ;
            this.isGround = isGround;
            this.isTop = isTop;
            this.building = building;
            this.registry = registry;
            this.ceilingZ = baseZ + storey.height() - 0.05;
            // Phase 122M: Exterior walls always full storey height (weather seal continuous)
            this.extWallMaxZ = baseZ + storey.height();
            // Phase 121/122I: Interior walls stop at slab underside for non-top storeys (framed)
            // Masonry/institutional walls run full storey height (slab sits on wall)
            boolean isMasonry = building.constructionSystem() != null
                && building.constructionSystem().name().contains("MASONRY");
            boolean isInstitutional = building.profile() != null
                && building.profile().contains("Institutional");
            // Phase 122M: Profile-based slab thickness (US=305mm from Duplex reference)
            double slabT = resolveSlabThickness(building.profile());
            this.wallMaxZ = (isTop || isMasonry || isInstitutional)
                ? baseZ + storey.height()
                : baseZ + storey.height() - slabT;
        }
    }

    record RoomMEP(String name, double minX, double minY, double maxX, double maxY,
                   Double sprinklerSpacing, Double lightSpacing) {}

    /**
     * Phase 122M: Profile-based structural slab thickness for wall height deduction.
     * Extracted from Rosetta reference stones (ad_floor_type structural entries).
     * US_Residential: 305mm (Wood Joist with Subflooring — Duplex reference)
     * UK_Residential: 165mm (standard domestic — SampleHouse reference)
     * Default: 150mm (Malaysian residential practice)
     */
    private static double resolveSlabThickness(String profile) {
        if (profile == null) return BIMConstants.STANDARD_SLAB_THICKNESS;
        if (profile.contains("US_Residential")) return 0.305;
        if (profile.contains("UK_Residential")) return 0.165;
        return BIMConstants.STANDARD_SLAB_THICKNESS;
    }

    /**
     * Phase 122N: Ground slab thickness differs from upper-floor structural slab.
     * Extracted from Rosetta reference stones.
     * US_Residential: 127mm (Slab on Grade — Duplex reference)
     * UK_Residential: 470mm (Floor-Grnd-Susp composite — SampleHouse reference)
     * Default: 150mm (standard slab on grade)
     */
    private static double resolveGroundSlabThickness(String profile) {
        if (profile == null) return BIMConstants.STANDARD_SLAB_THICKNESS;
        if (profile.contains("US_Residential")) return 0.127;
        if (profile.contains("UK_Residential")) return 0.470;
        return BIMConstants.STANDARD_SLAB_THICKNESS;
    }

    static StoreySpec compileStorey(StoreyDef storey, double baseZ,
                                            boolean isGround, boolean isTop,
                                            BuildingDefinition building,
                                            SharedElementRegistry registry) {
        var ctx = new StoreyBuildContext(storey, baseZ, isGround, isTop, building, registry);

        boolean hasMetadata = PlacementAD.getInstance().hasPlacement(building.name());
        if (!hasMetadata) {
            // Multi-unit: try parent building name (e.g., "Ifc2x3_Duplex_A" → "Ifc2x3_Duplex")
            String name = building.name();
            int lastUnderscore = name.lastIndexOf('_');
            if (lastUnderscore > 0) {
                hasMetadata = PlacementAD.getInstance().hasPlacement(name.substring(0, lastUnderscore));
            }
        }

        if (hasMetadata) {
            // Phase DE-1: Fully metadata-driven — no compiled elements.
            // Only resolve room layout (for BOM/QTO), then populate from metadata.
            resolveRoomLayout(ctx);
            // Phase DE-2: Clear compiled doors/windows — metadata buildings get these from
            // applyPlacementOverrides (per-storey) or global emission (emitGlobalPlacementElements)
            ctx.doors.clear();
            ctx.windows.clear();
            applyPlacementOverrides(ctx);
        } else {
            // Legacy compiled path for non-metadata buildings
            resolveRoomLayout(ctx);
            compileCoreElements(ctx);
            resolveUnitInteriors(ctx);           // Phase 112: moved BEFORE slab — interior rooms expand envelope
            compileSlabAndPerimeter(ctx);        // Phase 112: slab now includes all physical rooms
            compileInteriorWallsAndOpenings(ctx);
            placeMEPSprinklers(ctx);
            placeFixturesAndFurniture(ctx);
            placeStructural(ctx);
            segmentWallsAtOpenings(ctx);    // Phase 122I: split walls at opening edges + column faces
            applyPlacementOverrides(ctx);   // Phase B2: replace computed positions with metadata-extracted
            placeHVAC(ctx);
            placeElectrical(ctx);
            placePlumbing(ctx);
            mepBomGapFill(ctx);
        }
        return assembleStoreySpec(ctx);
    }

    // =====================================================================
    // Phase 104: Decomposed sub-methods
    // =====================================================================

    private static void resolveRoomLayout(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 21B: Two-pass room layout with adjacency snapping
        // Pass 1: Calculate initial bounds from grid positions
        // Pass 2: Snap adjacent rooms together to eliminate gaps
        // =====================================================================

        // Phase 112: Compute grid-derived building footprint (maths-proven)
        if (ctx.building.grid() != null) {
            var gd = ctx.building.grid();
            ctx.gridMaxX = gd.xSpacing() != null ? gd.xSpacing().stream().mapToDouble(Double::doubleValue).sum() : 0;
            ctx.gridMaxY = gd.ySpacing() != null ? gd.ySpacing().stream().mapToDouble(Double::doubleValue).sum() : 0;
            // gridMinX/gridMinY stay 0 (grid origin)
        }

        // Pass 1: Calculate initial room bounds
        double currentX = 0;

        // Phase 95B: Resolve floor BOM bounds if declared
        Map<String, double[]> resolvedBomBounds = Map.of();
        if (ctx.storey.floorBom() != null && ctx.building.grid() != null) {
            FloorPlateBOMResolver.GridInfo gridInfo =
                FloorPlateBOMResolver.gridInfoFromGridDef(ctx.building.grid());
            resolvedBomBounds = getFloorBomResolver().resolveRoomBoundsMap(
                ctx.storey.floorBom(), gridInfo);
        }

        // Phase 102B: Building-wide type-based BOM fallback for rooms without bounds.
        // If this storey has no floor_bom, find one from any other storey and resolve
        // by space type. This lets rooms like TOILET_BLOCK auto-inherit their zone.
        Map<String, double[]> typeBomBounds = Map.of();
        if (resolvedBomBounds.isEmpty() && ctx.building.grid() != null) {
            for (StoreyDef s : ctx.building.storeys()) {
                if (s.floorBom() != null && !s.floorBom().isEmpty()) {
                    FloorPlateBOMResolver.GridInfo gi =
                        FloorPlateBOMResolver.gridInfoFromGridDef(ctx.building.grid());
                    typeBomBounds = getFloorBomResolver().resolveTypeBoundsMap(
                        s.floorBom(), gi);
                    break;
                }
            }
        }

        // Phase 119E: Pre-compute rooms that participate in intra-storey adjacency.
        // These rooms get their doors from the adjacency handler (line ~752), not BOM auto-door.
        Set<String> storeyRoomNames = new HashSet<>();
        for (RoomDef r : ctx.storey.rooms()) storeyRoomNames.add(r.name());
        Set<String> adjacencyParticipants = new HashSet<>();
        for (RoomDef r : ctx.storey.rooms()) {
            for (String adj : r.adjacentTo()) {
                if (storeyRoomNames.contains(adj)) {
                    adjacencyParticipants.add(r.name());
                    adjacencyParticipants.add(adj);
                }
            }
        }

        for (RoomDef room : ctx.storey.rooms()) {
            double roomMinX, roomMinY, roomMaxX, roomMaxY;

            // Phase 95B: Check floor BOM resolver first (by name)
            double[] bomResolved = resolvedBomBounds.get(room.name());
            if (bomResolved != null) {
                roomMinX = bomResolved[0]; roomMinY = bomResolved[1];
                roomMaxX = bomResolved[2]; roomMaxY = bomResolved[3];
            } else if (room.hasGridBounds() && ctx.building.grid() != null) {
                GridBounds gb = room.getParsedGridBounds();
                if (gb != null) {
                    roomMinX = ctx.building.grid().getX(gb.startX());
                    roomMinY = ctx.building.grid().getY(gb.startY());
                    roomMaxX = ctx.building.grid().getX(gb.endX());
                    roomMaxY = ctx.building.grid().getY(gb.endY());
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
                // Phase 102B: Type-based BOM fallback — room inherits zone from building BOM
                double[] typeResolved = typeBomBounds.get(room.type().toUpperCase());
                if (typeResolved != null) {
                    roomMinX = typeResolved[0]; roomMinY = typeResolved[1];
                    roomMaxX = typeResolved[2]; roomMaxY = typeResolved[3];
                } else {
                    roomMinX = currentX;
                    roomMinY = 0;
                    roomMaxX = roomMinX + room.width();
                    roomMaxY = roomMinY + room.depth();
                }
            }

            ctx.roomBounds.put(room.name(), new double[]{roomMinX, roomMinY, roomMaxX, roomMaxY});
            currentX = roomMaxX;
        }

        // Pass 2: Snap adjacent rooms together
        // When rooms have adjacent: constraint, ensure they physically touch
        for (RoomDef room : ctx.storey.rooms()) {
            for (String adjacentName : room.adjacentTo()) {
                double[] myBounds = ctx.roomBounds.get(room.name());
                double[] theirBounds = ctx.roomBounds.get(adjacentName);

                if (myBounds != null && theirBounds != null) {
                    snapAdjacentRooms(myBounds, theirBounds);
                }
            }
        }

        // Pass 3: Create RoomSpecs with snapped bounds
        for (RoomDef room : ctx.storey.rooms()) {
            double[] bounds = ctx.roomBounds.get(room.name());
            double roomMinX = bounds[0], roomMinY = bounds[1];
            double roomMaxX = bounds[2], roomMaxY = bounds[3];

            // Phase AD: Use SpaceTypeRegistry instead of RoomType.fromKeyword()
            // SpaceTypeRegistry queries AD database first, falls back to YAML
            SpaceTypeRegistry.SpaceTypeConfig spaceTypeConfig = SpaceTypeRegistry.get(room.type());
            OutlierLogger.incrementTotalElements();

            ctx.rooms.add(new RoomSpec(
                spaceTypeConfig.name(), room.name(),  // Use resolved type name from AD/YAML
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                ctx.baseZ, ctx.baseZ + ctx.storey.height(),
                compileOpenings(room.openings()),
                room.above(),   // Phase 42: vertical constraint
                room.stack()    // Phase 42: stack alignment
            ));

            // Track for MEP generation (Phase 14B)
            if (room.hasSprinklers() || room.hasLights()) {
                ctx.roomsWithMEP.add(new RoomMEP(
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

            {
                boolean isAdjacencyRoom = hasOpensTo || adjacencyParticipants.contains(room.name());
                var bomDoorDefaults = OpeningBomAD.getDoorDefaults(room.type(), ctx.building.profile());
                List<String> extWalls = room.getAllExteriorWalls().stream()
                    .map(String::toLowerCase).toList();
                // Phase 122G: Track used walls so multiple door roles go on different walls
                Set<String> usedDoorWalls = new HashSet<>();

                if (!hasExplicitDoors && !isAdjacencyRoom) {
                    // Non-adjacency rooms: all BOM doors (interior + exterior)
                    for (var bomDef : bomDoorDefaults) {
                        var family = OpeningBomAD.getFamily(bomDef.familyId());
                        if (family == null) continue;
                        String doorWall = null;
                        for (String candidate : List.of("south", "north", "west", "east")) {
                            if (!extWalls.contains(candidate) && !usedDoorWalls.contains(candidate)) {
                                doorWall = candidate;
                                break;
                            }
                        }
                        if (doorWall == null) {
                            for (String candidate : List.of("south", "north", "west", "east")) {
                                if (!usedDoorWalls.contains(candidate)) {
                                    doorWall = candidate;
                                    break;
                                }
                            }
                        }
                        if (doorWall == null) doorWall = "south";
                        usedDoorWalls.add(doorWall);
                        double w = bomDef.overrideWidthMm() != null
                            ? bomDef.overrideWidthMm() / 1000.0 : family.defaultWidthMm() / 1000.0;
                        double h = bomDef.overrideHeightMm() != null
                            ? bomDef.overrideHeightMm() / 1000.0 : family.defaultHeightMm() / 1000.0;
                        effectiveOpenings.add(new OpeningDef("DOOR", doorWall, null, w, h, null));
                    }
                } else if (isAdjacencyRoom && !extWalls.isEmpty()) {
                    // Phase 122J: Adjacency rooms still get exterior BOM doors (EGRESS)
                    for (var bomDef : bomDoorDefaults) {
                        if (!"exterior".equals(bomDef.placementWall())) continue;
                        var family = OpeningBomAD.getFamily(bomDef.familyId());
                        if (family == null) continue;
                        String doorWall = extWalls.get(0);
                        double w = bomDef.overrideWidthMm() != null
                            ? bomDef.overrideWidthMm() / 1000.0 : family.defaultWidthMm() / 1000.0;
                        double h = bomDef.overrideHeightMm() != null
                            ? bomDef.overrideHeightMm() / 1000.0 : family.defaultHeightMm() / 1000.0;
                        effectiveOpenings.add(new OpeningDef("DOOR", doorWall, null, w, h, null));
                    }
                }
            }

            for (OpeningDef opening : effectiveOpenings) {
                // Phase 50: Resolve dimensions from schedule if width=0
                double width = opening.width();
                double height = opening.height();
                if ((width == 0 || height == 0) && opening.typeCode() != null) {
                    var schedule = opening.type().equals("DOOR")
                        ? ctx.building.doorSchedule()
                        : ctx.building.windowSchedule();
                    if (schedule != null) {
                        double[] dims = schedule.resolve(opening.typeCode());
                        if (dims != null) {
                            width = dims[0];
                            height = dims[1];
                        }
                    }
                }
                // Phase 122E: Always resolve depth + sill from BOM family (not just when width/height=0).
                // Phase 119B depth was gated on width==0||height==0 — BOM auto-doors had known
                // width/height so depth was never set, falling to 100mm DOOR_THICKNESS.
                double depth = 0;
                int bomSillMm = -1;
                if (opening.type().equals("DOOR")) {
                    var bomDoors = OpeningBomAD.getDoorDefaults(room.type(), ctx.building.profile());
                    if (!bomDoors.isEmpty()) {
                        // Phase 122G: Match family by width to get correct depth per door role.
                        // Before: always picked get(0) (ENTRY), so FIRE_EXIT doors got wrong depth.
                        OpeningBomAD.OpeningFamily fam = null;
                        if (width > 0) {
                            for (var d : bomDoors) {
                                var f = OpeningBomAD.getFamily(d.familyId());
                                if (f != null && Math.abs(f.defaultWidthMm() / 1000.0 - width) < 0.01) {
                                    fam = f;
                                    break;
                                }
                            }
                        }
                        if (fam == null) fam = OpeningBomAD.getFamily(bomDoors.get(0).familyId());
                        if (fam != null) {
                            depth = fam.depthM();
                            if (width == 0) width = fam.defaultWidthMm() / 1000.0;
                            if (height == 0) height = fam.defaultHeightMm() / 1000.0;
                        }
                    }
                } else {
                    var bomWins = OpeningBomAD.getWindowDefaults(room.type(), ctx.building.profile());
                    if (!bomWins.isEmpty()) {
                        var fam = OpeningBomAD.getFamily(bomWins.get(0).familyId());
                        if (fam != null) {
                            depth = fam.depthM();
                            if (width == 0) width = fam.defaultWidthMm() / 1000.0;
                            if (height == 0) height = fam.defaultHeightMm() / 1000.0;
                        }
                        bomSillMm = bomWins.get(0).sillHeightMm();
                    }
                }
                // Final hardcoded fallback
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
                    if (room.opensTo() != null && isOpensToWall(room, opening.wall(), ctx.roomBounds)) {
                        continue;  // Connection door at shared edge handles this
                    }
                    // Phase 50: Unique naming with counter for multiple doors on same wall
                    int count = doorCountPerWall.merge(opening.wall(), 1, Integer::sum);
                    String doorName = opening.connectsTo() != null
                        ? room.name() + "_to_" + opening.connectsTo() + "_door"
                        : room.name() + "_door_" + opening.wall() + (count > 1 ? "_" + count : "");
                    ctx.doors.add(new DoorSpec(
                        doorName,
                        room.name(), opening.wall(),
                        openingX, openingY, ctx.baseZ,
                        width, height,
                        opening.connectsTo(),
                        depth > 0 ? depth : BIMConstants.DOOR_THICKNESS
                    ));
                } else if (opening.type().equals("WINDOW")) {
                    // Phase 50: Unique naming with counter for multiple windows on same wall
                    int count = windowCountPerWall.merge(opening.wall(), 1, Integer::sum);
                    // Phase 122E: Use BOM sill height if available (e.g. 0mm for curtain wall)
                    double sillHeight = bomSillMm >= 0 ? bomSillMm / 1000.0 : 0.9;
                    ctx.windows.add(new WindowSpec(
                        room.name() + "_window_" + opening.wall() + (count > 1 ? "_" + count : ""),
                        room.name(), opening.wall(),
                        openingX, openingY, ctx.baseZ + sillHeight,
                        width, height,
                        sillHeight,
                        depth > 0 ? depth : BIMConstants.WINDOW_THICKNESS
                    ));
                }
            }

            ctx.minX = Math.min(ctx.minX, roomMinX);
            ctx.maxX = Math.max(ctx.maxX, roomMaxX);
            ctx.minY = Math.min(ctx.minY, roomMinY);
            ctx.maxY = Math.max(ctx.maxY, roomMaxY);

            currentX = roomMaxX;
        }

        // Phase 108: Inject UNIT zones from floor BOM into building envelope
        if (ctx.storey.floorBom() != null && ctx.building.grid() != null) {
            var gridInfo = FloorPlateBOMResolver.gridInfoFromGridDef(ctx.building.grid());
            ctx.unitZones = getFloorBomResolver().resolveUnitZones(ctx.storey.floorBom(), gridInfo);
            for (var entry : ctx.unitZones.entrySet()) {
                var zone = entry.getValue();
                ctx.rooms.add(new RoomSpec("UNIT", entry.getKey(),
                    zone.minX(), zone.minY(), zone.maxX(), zone.maxY(),
                    ctx.baseZ, ctx.baseZ + ctx.storey.height(), List.of(), null, null));
                ctx.roomBounds.put(entry.getKey(),
                    new double[]{zone.minX(), zone.minY(), zone.maxX(), zone.maxY()});
                // Phase 112: Do NOT expand envelope for UNIT zones — they are virtual.
                // Physical interior rooms will expand envelope in resolveUnitInteriors().
            }
        }
    }

    private static void compileCoreElements(StoreyBuildContext ctx) {
        // Add stair footprint to bounds
        for (StairDef stair : ctx.storey.stairs()) {
            double stairRun = calculateStairRun(ctx.storey.height());
            double stairX, stairY;

            // Phase 48D: Use grid position if available (for SHARED storeys)
            if (stair.gridPosition() != null && !stair.gridPosition().isEmpty()) {
                int[] coords = BuildingCompiler.parseGridPosition(stair.gridPosition());
                stairX = coords[0];
                stairY = coords[1];
            } else {
                // Fallback: place stair after rooms
                stairX = ctx.maxX;
                stairY = 0;
            }

            ctx.stairs.add(compileStair(stair, stairX, stairY, ctx.baseZ, ctx.storey.height()));

            // Update bounds to include stair
            if (ctx.maxX == Double.MIN_VALUE) {
                ctx.minX = stairX;
                ctx.maxX = stairX + stair.width();
            } else {
                ctx.minX = Math.min(ctx.minX, stairX);
                ctx.maxX = Math.max(ctx.maxX, stairX + stair.width());
            }
            if (ctx.maxY == Double.MIN_VALUE) {
                ctx.minY = stairY;
                ctx.maxY = stairY + stairRun;
            } else {
                ctx.minY = Math.min(ctx.minY, stairY);
                ctx.maxY = Math.max(ctx.maxY, stairY + stairRun);
            }
        }

        // Phase 56B: Compile CORE stairs (building-level vertical circulation)
        if (ctx.building.core() != null && ctx.building.grid() != null) {
            for (StairDef coreStair : ctx.building.core().stairs()) {
                double stairRun = calculateStairRun(ctx.storey.height());
                double stairX, stairY;

                // Use grid lookup for proper coordinate resolution
                String[] labels = BuildingCompiler.parseGridLabels(coreStair.gridPosition());
                stairX = ctx.building.grid().getX(labels[0]);
                stairY = ctx.building.grid().getY(labels[1]);

                ctx.stairs.add(compileStair(coreStair, stairX, stairY, ctx.baseZ, ctx.storey.height()));

                // Update bounds
                ctx.minX = Math.min(ctx.minX, stairX);
                ctx.maxX = Math.max(ctx.maxX, stairX + coreStair.width());
                ctx.minY = Math.min(ctx.minY, stairY);
                ctx.maxY = Math.max(ctx.maxY, stairY + stairRun);
            }

            // Phase 56B: Compile CORE shafts (elevator + MEP)
            for (ShaftDef coreShaft : ctx.building.core().shafts()) {
                String[] labels = BuildingCompiler.parseGridLabels(coreShaft.gridPosition());
                double shaftX = ctx.building.grid().getX(labels[0]);
                double shaftY = ctx.building.grid().getY(labels[1]);

                ctx.shafts.add(new ShaftSpec(
                    coreShaft.name(),
                    coreShaft.type(),
                    shaftX, shaftY, ctx.baseZ,
                    shaftX + coreShaft.widthM(), shaftY + coreShaft.depthM(), ctx.baseZ + ctx.storey.height()
                ));
            }

            // Phase 56B: Compile CORE elevator lobbies with elevators
            for (ElevatorLobbyDef coreLobby : ctx.building.core().lobbies()) {
                // Parse lobby bounds (e.g., C2-D4)
                String[] boundsLabels = coreLobby.gridBounds().split("-");
                String[] startLabels = BuildingCompiler.parseGridLabels(boundsLabels[0]);
                String[] endLabels = BuildingCompiler.parseGridLabels(boundsLabels[1]);

                double lobbyMinX = ctx.building.grid().getX(startLabels[0]);
                double lobbyMinY = ctx.building.grid().getY(startLabels[1]);
                double lobbyMaxX = ctx.building.grid().getX(endLabels[0]);
                double lobbyMaxY = ctx.building.grid().getY(endLabels[1]);

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
                        elevX, lobbyMinY + 0.5, ctx.baseZ,  // Position
                        elev.carWidthMm(),
                        elev.carDepthMm(),
                        elev.doorWidthMm(),
                        shaftWidth,
                        shaftDepth,
                        elev.emergencyPower(),
                        elev.fireRatingHr(),
                        coreLobby.pressurized()
                    ));
                    ctx.elevators.add(lobbyElevators.get(lobbyElevators.size() - 1));
                    elevX += shaftWidth + 0.3; // 300mm gap between shafts
                }

                ctx.lobbies.add(new ElevatorLobbySpec(
                    coreLobby.name(),
                    lobbyMinX, lobbyMinY, ctx.baseZ,
                    lobbyMaxX, lobbyMaxY, ctx.baseZ + ctx.storey.height(),
                    coreLobby.pressurized(),
                    coreLobby.fireRatingHr(),
                    lobbyElevators
                ));
            }
        }

        // Generate landings at stair top
        for (LandingDef landing : ctx.storey.landings()) {
            double landingX, landingY;

            // Phase 48D: Use grid position if available
            if (landing.gridPosition() != null && !landing.gridPosition().isEmpty()) {
                int[] coords = BuildingCompiler.parseGridPosition(landing.gridPosition());
                landingX = coords[0];
                landingY = coords[1];
            } else {
                // Fallback: place after rooms
                landingX = ctx.maxX - landing.width();
                landingY = 0;
            }

            double landingZ = ctx.baseZ; // Landing is at this storey's floor level
            double landingThickness = 0.15; // 150mm

            ctx.landings.add(new LandingSpec(
                landing.name(),
                landing.fromStair(),
                landingX, landingY, landingZ - landingThickness,
                landingX + landing.width(), landingY + landing.depth(), landingZ
            ));

            // Update bounds to include landing
            if (ctx.maxX == Double.MIN_VALUE) {
                ctx.minX = landingX;
                ctx.maxX = landingX + landing.width();
            } else {
                ctx.minX = Math.min(ctx.minX, landingX);
                ctx.maxX = Math.max(ctx.maxX, landingX + landing.width());
            }
            if (ctx.maxY == Double.MIN_VALUE) {
                ctx.minY = landingY;
                ctx.maxY = landingY + landing.depth();
            } else {
                ctx.minY = Math.min(ctx.minY, landingY);
                ctx.maxY = Math.max(ctx.maxY, landingY + landing.depth());
            }
        }
    }

    private static void compileSlabAndPerimeter(StoreyBuildContext ctx) {
        // Phase A: Look up slab spec from metadata (ad_slab_spec)
        String buildingName = ctx.building.name();
        String slabRole = ctx.isGround ? "FOUNDATION" : "FLOOR";
        SlabSpecAD.SlabEntry slabMeta = getSlabSpecAD().get(buildingName, slabRole);

        // Phase 122N: Profile-based slab thickness (ground vs upper differ)
        double slabThickness = slabMeta != null && slabMeta.thickness() != null
            ? slabMeta.thickness()
            : (ctx.isGround
                ? resolveGroundSlabThickness(ctx.building.profile())
                : resolveSlabThickness(ctx.building.profile()));

        // Phase A: Slab extend from metadata or default
        double extendX = slabMeta != null ? slabMeta.extendX() : BIMConstants.STANDARD_SLAB_OVERLAP;
        double extendY = slabMeta != null ? slabMeta.extendY() : BIMConstants.STANDARD_SLAB_OVERLAP;

        // Phase A: For multi-unit sub-buildings, clamp slab to unit footprint
        // The solver places rooms on integer grid → bounding box exceeds actual unit dimensions.
        // Use the known unit footprint from metadata to produce correct per-unit slabs.
        double slabMinX = ctx.minX, slabMinY = ctx.minY;
        double slabMaxX = ctx.maxX, slabMaxY = ctx.maxY;
        double[] unitFP = MultiUnitCompiler.getUnitFootprint(buildingName);
        if (unitFP != null) {
            slabMaxX = Math.min(slabMaxX, slabMinX + unitFP[0]);
            slabMaxY = Math.min(slabMaxY, slabMinY + unitFP[1]);
        }

        // Generate slab
        String slabName = slabMeta != null && slabMeta.slabName() != null
            ? slabMeta.slabName()
            : (ctx.isGround ? "Foundation Slab" : "Floor Slab Level " + ctx.storey.level());
        if (ctx.isGround) {
            ctx.slab = new SlabSpec(
                "FOUNDATION", slabName,
                slabMinX - extendX, slabMinY - extendY,
                slabMaxX + extendX, slabMaxY + extendY,
                ctx.baseZ - slabThickness, ctx.baseZ
            );
        } else {
            ctx.slab = new SlabSpec(
                "FLOOR", slabName,
                slabMinX - extendX, slabMinY - extendY,
                slabMaxX + extendX, slabMaxY + extendY,
                ctx.baseZ - slabThickness, ctx.baseZ
            );
        }

        // Phase A: Ceiling slab for topmost storey (if metadata exists)
        if (ctx.isTop) {
            SlabSpecAD.SlabEntry ceilingMeta = getSlabSpecAD().get(buildingName, "CEILING");
            if (ceilingMeta != null && ceilingMeta.ceilingZ() != null && ceilingMeta.thickness() != null) {
                double ceilExtX = ceilingMeta.extendX();
                double ceilExtY = ceilingMeta.extendY();
                double ceilMaxZ = ctx.baseZ + ceilingMeta.ceilingZ();
                double ceilMinZ = ceilMaxZ - ceilingMeta.thickness();
                String ceilName = ceilingMeta.slabName() != null ? ceilingMeta.slabName() : "Ceiling Slab";
                ctx.baySlabs.add(new SlabSpec(
                    "CEILING", ceilName,
                    slabMinX - ceilExtX, slabMinY - ceilExtY,
                    slabMaxX + ceilExtX, slabMaxY + ceilExtY,
                    ceilMinZ, ceilMaxZ
                ));
            }
        }

        // Phase 122F: Generate grid-bay structural slabs for RC frame grid buildings
        // Only for large grids (>= 6 bays) that indicate structural framing, not room-layout grids
        boolean isStructuralGrid = ctx.building.grid() != null
                && ctx.building.grid().xSpacing() != null
                && ctx.building.grid().ySpacing() != null
                && ctx.building.grid().xSpacing().size() * ctx.building.grid().ySpacing().size() >= 6;
        if (isStructuralGrid) {
            List<Double> xSpacing = ctx.building.grid().xSpacing();
            List<Double> ySpacing = ctx.building.grid().ySpacing();

            // Compute grid axis positions from spacings
            double[] xPos = new double[xSpacing.size() + 1];
            for (int i = 0; i < xSpacing.size(); i++) {
                xPos[i + 1] = xPos[i] + xSpacing.get(i);
            }
            double[] yPos = new double[ySpacing.size() + 1];
            for (int i = 0; i < ySpacing.size(); i++) {
                yPos[i + 1] = yPos[i] + ySpacing.get(i);
            }

            // Profile-specific slab thickness (institutional 200mm, default 150mm)
            double bayThickness = 0.200;  // RC institutional standard
            double slabMinZ = ctx.baseZ - bayThickness;

            // Generate half-bay slabs (beam mid-spans divide each bay in X and Y)
            int slabIdx = 0;
            for (int xi = 0; xi < xSpacing.size(); xi++) {
                double xMid = xPos[xi] + xSpacing.get(xi) / 2.0;
                for (int yi = 0; yi < ySpacing.size(); yi++) {
                    double yMid = yPos[yi] + ySpacing.get(yi) / 2.0;
                    // 4 quarter-bay panels per full bay
                    double[][] quarters = {
                        { xPos[xi],   yPos[yi],   xMid,       yMid },
                        { xMid,       yPos[yi],   xPos[xi+1], yMid },
                        { xPos[xi],   yMid,       xMid,       yPos[yi+1] },
                        { xMid,       yMid,       xPos[xi+1], yPos[yi+1] }
                    };
                    for (double[] q : quarters) {
                        ctx.baySlabs.add(new SlabSpec(
                            ctx.isGround ? "FOUNDATION" : "FLOOR",
                            "Bay Slab " + (++slabIdx),
                            q[0], q[1], q[2], q[3],
                            slabMinZ, ctx.baseZ
                        ));
                    }
                }
            }
            System.out.printf("[SLAB] Storey %s: %d grid-bay slabs (%.0f×%.0fmm quarter-panels, %.0fmm thick)%n",
                ctx.storey.name(), ctx.baySlabs.size(),
                xSpacing.get(0) / 2.0 * 1000, ySpacing.get(0) / 2.0 * 1000, bayThickness * 1000);
        }

        // Phase 102B: AD-driven facade resolution (AD > DSL > default)
        SpaceDimResolver dimResolver = SpaceDimResolver.getInstance();
        String facadeMaterial = "Metal Deck"; // default
        // Check AD: any perimeter-touching room with GLASS_CURTAIN facade?
        for (RoomDef room : ctx.storey.rooms()) {
            if (!room.getAllExteriorWalls().isEmpty()
                    && "GLASS_CURTAIN".equals(dimResolver.resolveFacadeType(room.type()))) {
                facadeMaterial = "Glass Curtain Wall";
                break;
            }
        }
        // DSL override (backward-compat)
        if (ctx.building.facade() != null && ctx.building.facade().equalsIgnoreCase("glass")) {
            facadeMaterial = "Glass Curtain Wall";
        }

        // Phase 119: Resolve exterior wall type from profile (if set)
        double extWallT = BIMConstants.STANDARD_WALL_THICKNESS;
        String extWallName = facadeMaterial; // default: "Metal Deck" or "Glass Curtain Wall"
        if (ctx.building.profile() != null) {
            var extEntry = WallTypeResolver.getInstance().resolve(
                "EXTERIOR", null, null, ctx.building.profile());
            if (extEntry != null) {
                extWallT = extEntry.thicknessM();
                extWallName = extEntry.ifcName();
            }
        }

        // Generate perimeter walls with registry for stud deduplication
        // Phase 122M: Exterior walls use extWallMaxZ (full storey height, weather seal)
        ctx.walls.add(compilePerimeterWall("SOUTH", ctx.minX, ctx.minY, ctx.maxX, ctx.minY,
            ctx.baseZ, ctx.extWallMaxZ, ctx.storey.name(), ctx.registry, extWallT, extWallName));
        ctx.walls.add(compilePerimeterWall("NORTH", ctx.minX, ctx.maxY, ctx.maxX, ctx.maxY,
            ctx.baseZ, ctx.extWallMaxZ, ctx.storey.name(), ctx.registry, extWallT, extWallName));
        ctx.walls.add(compilePerimeterWall("WEST", ctx.minX, ctx.minY, ctx.minX, ctx.maxY,
            ctx.baseZ, ctx.extWallMaxZ, ctx.storey.name(), ctx.registry, extWallT, extWallName));
        ctx.walls.add(compilePerimeterWall("EAST", ctx.maxX, ctx.minY, ctx.maxX, ctx.maxY,
            ctx.baseZ, ctx.extWallMaxZ, ctx.storey.name(), ctx.registry, extWallT, extWallName));
    }

    /**
     * Phase 108: Expand UNIT zone rooms into interior rooms (living, bedroom, etc.)
     * using UnitInteriorResolver with ad_unit_type_room templates.
     */
    private static void resolveUnitInteriors(StoreyBuildContext ctx) {
        if (ctx.unitZones == null || ctx.unitZones.isEmpty()) return;
        var resolver = new UnitInteriorResolver();
        List<RoomSpec> toRemove = new ArrayList<>();
        List<RoomSpec> toAdd = new ArrayList<>();
        ctx.syntheticRoomDefs = new ArrayList<>();

        for (RoomSpec room : ctx.rooms) {
            if (!"UNIT".equals(room.type())) continue;
            var zone = ctx.unitZones.get(room.name());
            if (zone == null || zone.unitType() == null) continue;

            // Phase 112: Use grid-derived footprint for mirror detection (not running envelope)
            var interiorRooms = resolver.resolveInterior(
                room.name(), zone.unitType(),
                room.minX(), room.minY(), room.maxX(), room.maxY(),
                ctx.gridMinX, ctx.gridMinY, ctx.gridMaxX, ctx.gridMaxY);

            if (interiorRooms.isEmpty()) continue;
            toRemove.add(room);

            for (var ur : interiorRooms) {
                SpaceTypeRegistry.SpaceTypeConfig config = SpaceTypeRegistry.get(ur.type());
                toAdd.add(new RoomSpec(config.name(), ur.name(),
                    ur.minX(), ur.minY(), ur.maxX(), ur.maxY(),
                    ctx.baseZ, ctx.baseZ + ctx.storey.height(),
                    List.of(), null, null));
                ctx.roomBounds.put(ur.name(),
                    new double[]{ur.minX(), ur.minY(), ur.maxX(), ur.maxY()});

                // Phase 112: Interior rooms are physical — expand envelope for slab
                ctx.minX = Math.min(ctx.minX, ur.minX());
                ctx.maxX = Math.max(ctx.maxX, ur.maxX());
                ctx.minY = Math.min(ctx.minY, ur.minY());
                ctx.maxY = Math.max(ctx.maxY, ur.maxY());

                // Synthetic RoomDef for wall/opening generation
                List<String> extWalls = ur.exteriorWalls() != null ? ur.exteriorWalls() : List.of();
                ctx.syntheticRoomDefs.add(new RoomDef(
                    ur.type(), ur.name(), null, 0, 0, List.of(), null, null,
                    List.of(), List.of(), null, null, null, null, null, null, null,
                    ur.opensTo(), List.of(), extWalls, null));
            }
        }
        ctx.rooms.removeAll(toRemove);
        ctx.rooms.addAll(toAdd);
    }

    private static void compileInteriorWallsAndOpenings(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 15B: Interior Walls + Auto-Doors + Auto-Windows
        // =====================================================================

        // Phase 108: Build room bounds map using name-based lookup (not index-based)
        // to support both parsed rooms and synthetic unit-interior rooms
        Map<String, RoomSpec> specByName = new HashMap<>();
        for (RoomSpec rs : ctx.rooms) specByName.put(rs.name(), rs);

        for (RoomDef roomDef : ctx.storey.rooms()) {
            RoomSpec roomSpec = specByName.get(roomDef.name());
            if (roomSpec == null) continue;
            ctx.roomBoundsMap.put(roomDef.name(), new RoomBounds(
                roomSpec.minX(), roomSpec.minY(), roomSpec.maxX(), roomSpec.maxY()
            ));
            ctx.roomDefMap.put(roomDef.name(), roomDef);
        }
        // Add synthetic rooms from unit interiors
        if (ctx.syntheticRoomDefs != null) {
            for (RoomDef rd : ctx.syntheticRoomDefs) {
                RoomSpec rs = specByName.get(rd.name());
                if (rs == null) continue;
                ctx.roomBoundsMap.put(rd.name(), new RoomBounds(
                    rs.minX(), rs.minY(), rs.maxX(), rs.maxY()
                ));
                ctx.roomDefMap.put(rd.name(), rd);
            }
        }

        // Find shared edges and generate interior walls
        // Phase 108: Combine parsed + synthetic room defs
        List<RoomDef> roomList = new ArrayList<>(ctx.storey.rooms());
        if (ctx.syntheticRoomDefs != null) roomList.addAll(ctx.syntheticRoomDefs);
        for (int i = 0; i < roomList.size(); i++) {
            for (int j = i + 1; j < roomList.size(); j++) {
                RoomDef room1 = roomList.get(i);
                RoomDef room2 = roomList.get(j);

                RoomBounds bounds1 = ctx.roomBoundsMap.get(room1.name());
                RoomBounds bounds2 = ctx.roomBoundsMap.get(room2.name());

                SharedEdge edge = findSharedEdge(bounds1, bounds2);
                if (edge != null) {
                    // Generate interior wall along shared edge (with registry for stud deduplication)
                    // Phase 116/119: Resolve wall type from ad_wall_type_rule based on room types + profile
                    String wallName = "INTERIOR_" + room1.name() + "_" + room2.name();
                    var intEntry = WallTypeResolver.getInstance().resolve(
                        "INTERIOR", room1.type(), room2.type(), ctx.building.profile());
                    double wallT = intEntry != null ? intEntry.thicknessM() : BIMConstants.STANDARD_WALL_THICKNESS;
                    String intWallMat = intEntry != null ? intEntry.ifcName() : "Metal Deck";
                    ctx.walls.add(compileWall(wallName,
                        edge.x1(), edge.y1(), edge.x2(), edge.y2(),
                        ctx.baseZ, ctx.wallMaxZ, ctx.storey.name(),
                        ctx.registry, wallT, intWallMat));

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
                        // Phase 122B: Adjacency = interior connection → prefer interior door family
                        double connDoorWidth = BIMConstants.STANDARD_DOOR_WIDTH;
                        double connDoorHeight = BIMConstants.STANDARD_DOOR_HEIGHT;
                        double connDoorDepth = BIMConstants.DOOR_THICKNESS;
                        var connFam = resolveAdjacencyDoor(room1, room2, ctx.building.profile());
                        if (connFam != null) {
                            connDoorWidth = connFam.defaultWidthMm() / 1000.0;
                            connDoorHeight = connFam.defaultHeightMm() / 1000.0;
                            connDoorDepth = connFam.depthM();
                        }
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
                                    && ctx.building.doorSchedule() != null) {
                                double[] dims = ctx.building.doorSchedule().resolve(matchedOpening.typeCode());
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
                        ctx.doors.add(new DoorSpec(
                            room1.name() + "_to_" + room2.name() + "_door",
                            room1.name(), doorWall,
                            doorX, doorY, ctx.baseZ,
                            connDoorWidth, connDoorHeight,
                            room2.name(),
                            connDoorDepth
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
            RoomBounds bounds = ctx.roomBoundsMap.get(room.name());
            if (Math.abs(bounds.minY() - ctx.minY) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_south");
            if (Math.abs(bounds.maxY() - ctx.maxY) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_north");
            if (Math.abs(bounds.minX() - ctx.minX) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_west");
            if (Math.abs(bounds.maxX() - ctx.maxX) < BIMConstants.TOLERANCE) coveredEdges.add(room.name() + "_east");
        }

        // Mark shared edges as covered
        for (int i = 0; i < roomList.size(); i++) {
            for (int j = i + 1; j < roomList.size(); j++) {
                RoomBounds b1 = ctx.roomBoundsMap.get(roomList.get(i).name());
                RoomBounds b2 = ctx.roomBoundsMap.get(roomList.get(j).name());
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
        // Phase 119B: Resolve partition type from profile for thickness + IFC name
        for (RoomDef room : roomList) {
            RoomBounds bounds = ctx.roomBoundsMap.get(room.name());
            var partEntry = WallTypeResolver.getInstance().resolve(
                "INTERIOR", room.type(), null, ctx.building.profile());
            double partT = partEntry != null ? partEntry.thicknessM() : BIMConstants.STANDARD_WALL_THICKNESS;
            String partName = partEntry != null ? partEntry.ifcName() : "Metal Deck";

            // North edge
            if (!coveredEdges.contains(room.name() + "_north")) {
                ctx.walls.add(compileWall("PARTITION_" + room.name() + "_north",
                    bounds.minX(), bounds.maxY(), bounds.maxX(), bounds.maxY(),
                    ctx.baseZ, ctx.wallMaxZ, ctx.storey.name(), ctx.registry, partT, partName));
            }
            // South edge
            if (!coveredEdges.contains(room.name() + "_south")) {
                ctx.walls.add(compileWall("PARTITION_" + room.name() + "_south",
                    bounds.minX(), bounds.minY(), bounds.maxX(), bounds.minY(),
                    ctx.baseZ, ctx.wallMaxZ, ctx.storey.name(), ctx.registry, partT, partName));
            }
            // East edge
            if (!coveredEdges.contains(room.name() + "_east")) {
                ctx.walls.add(compileWall("PARTITION_" + room.name() + "_east",
                    bounds.maxX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
                    ctx.baseZ, ctx.wallMaxZ, ctx.storey.name(), ctx.registry, partT, partName));
            }
            // West edge
            if (!coveredEdges.contains(room.name() + "_west")) {
                ctx.walls.add(compileWall("PARTITION_" + room.name() + "_west",
                    bounds.minX(), bounds.minY(), bounds.minX(), bounds.maxY(),
                    ctx.baseZ, ctx.wallMaxZ, ctx.storey.name(), ctx.registry, partT, partName));
            }
        }

        // Auto-place windows for rooms with EXTERIOR constraints (if not already specified)
        // Phase 28: Use getAllExteriorWalls() to support both legacy exteriorWall and new exteriorWalls list
        // Phase 108: Use combined roomList (parsed + synthetic) for unit interior windows
        for (RoomDef room : roomList) {
            // Phase 119E/122D: If room has ANY explicit window declarations, skip auto-windows.
            // For institutional profiles: per-wall skip (rooms may have windows on some walls only).
            // For residential: room-level skip (DSL-declared windows are complete spec).
            boolean hasAnyExplicitWindows = room.openings().stream()
                .anyMatch(o -> o.type().equals("WINDOW"));
            boolean isInstitutional = ctx.building.profile() != null
                && ctx.building.profile().contains("Institutional");
            if (hasAnyExplicitWindows && !isInstitutional) continue;

            for (String extWall : room.getAllExteriorWalls()) {
                extWall = extWall.toLowerCase();

                // Check if room already has a window on that wall
                final String finalExtWall = extWall;
                boolean hasWindowOnWall = room.openings().stream()
                    .anyMatch(o -> o.type().equals("WINDOW") && o.wall().equalsIgnoreCase(finalExtWall));

                // Phase 119E: Skip auto-window on walls that already have a door
                boolean hasDoorOnWall = room.openings().stream()
                    .anyMatch(o -> o.type().equals("DOOR") && o.wall().equalsIgnoreCase(finalExtWall));

                // Phase 122G: For institutional, allow auto-windows on walls with doors
                // (curtain wall panels coexist with entrance doors on same facade)
                boolean skipWall = isInstitutional
                    ? hasWindowOnWall  // only skip if explicit window already on wall
                    : (hasWindowOnWall || hasDoorOnWall);
                if (!skipWall) {
                    // Auto-place window on exterior wall
                    // Phase 47A.3: Place on ROOM's wall, not building edge
                    RoomBounds bounds = ctx.roomBoundsMap.get(room.name());
                    if (bounds == null) continue;

                    // Phase 87: Use BOM window family dimensions if available
                    var windowDefs = OpeningBomAD.getWindowDefaults(room.type(), ctx.building.profile());
                    OpeningBomAD.OpeningFamily winFamily = !windowDefs.isEmpty()
                        ? OpeningBomAD.getFamily(windowDefs.get(0).familyId()) : null;
                    double winW = winFamily != null ? winFamily.defaultWidthMm() / 1000.0 : BIMConstants.STANDARD_WINDOW_WIDTH;
                    double winH = winFamily != null ? winFamily.defaultHeightMm() / 1000.0 : BIMConstants.STANDARD_WINDOW_HEIGHT;
                    double winDepth = winFamily != null ? winFamily.depthM() : BIMConstants.WINDOW_THICKNESS;
                    int sillMm = !windowDefs.isEmpty() ? windowDefs.get(0).sillHeightMm() : 900;
                    double sillH = sillMm / 1000.0;

                    // Phase 122G: Compute wall length FIRST (needed for qty calculation)
                    boolean isNS = "north".equals(extWall) || "south".equals(extWall);
                    double wallLen = isNS ? bounds.maxX() - bounds.minX() : bounds.maxY() - bounds.minY();

                    // Phase 103/122G: Qty from BOM — wall-length-based for PER_EXTERIOR_WALL
                    int qtyPerWall = 1;
                    if (!windowDefs.isEmpty()) {
                        String qtyRule = windowDefs.get(0).qtyRule();
                        if ("FIXED".equals(qtyRule)) {
                            qtyPerWall = Math.max(1, windowDefs.get(0).qtyBase());
                        } else if ("PER_EXTERIOR_WALL".equals(qtyRule)) {
                            // Phase 122G: Fill wall with windows spaced at winW + gap
                            // Gap = 0.5m between panels (mullion/frame spacing)
                            double spacing = winW + 0.5;
                            qtyPerWall = Math.max(1, (int)(wallLen / spacing));
                        }
                    }

                    for (int wi = 0; wi < qtyPerWall; wi++) {
                        double pos = (wi + 1) * wallLen / (qtyPerWall + 1) - winW / 2;
                        double windowX, windowY;

                        switch (extWall) {
                            case "south" -> {
                                windowX = bounds.minX() + pos;
                                windowY = bounds.minY();
                            }
                            case "north" -> {
                                windowX = bounds.minX() + pos;
                                windowY = bounds.maxY();
                            }
                            case "west" -> {
                                windowX = bounds.minX();
                                windowY = bounds.minY() + pos;
                            }
                            case "east" -> {
                                windowX = bounds.maxX();
                                windowY = bounds.minY() + pos;
                            }
                            default -> {
                                windowX = bounds.minX();
                                windowY = bounds.minY();
                            }
                        }

                        String suffix = qtyPerWall > 1 ? extWall + "_" + (wi + 1) : extWall;
                        ctx.windows.add(new WindowSpec(
                            room.name() + "_auto_window_" + suffix,
                            room.name(), extWall,
                            windowX, windowY, ctx.baseZ + sillH,
                            winW, winH,
                            sillH,
                            winDepth
                        ));
                    }
                }
            }
        }

        // Phase 102B: AD-driven ventilation openings for rooms
        // Phase 108: Use combined roomList for unit interior ventilation
        SpaceDimResolver dimResolver = SpaceDimResolver.getInstance();
        for (RoomDef room : roomList) {
            SpaceDimResolver.VentilationSpec ventSpec = dimResolver.resolveVentilation(room.type());
            if (ventSpec == null) continue;
            if (!"exterior".equals(ventSpec.wall())) continue;

            RoomBounds bounds = ctx.roomBoundsMap.get(room.name());
            if (bounds == null) continue;

            double ventW = 0.6;  // 600mm default ventilation window
            double ventH = ventSpec.heightMm() / 1000.0;
            double sillH = ventSpec.sillMm() / 1000.0;

            for (String extWall : room.getAllExteriorWalls()) {
                String ew = extWall.toLowerCase();
                // Skip if room already has a window on that wall
                String windowName = room.name() + "_vent_" + ew;
                final String ewFinal = ew;
                boolean alreadyHasWindow = ctx.windows.stream()
                    .anyMatch(w -> w.roomName().equals(room.name()) && w.wall().equalsIgnoreCase(ewFinal));
                if (alreadyHasWindow) continue;

                double windowX, windowY;
                double roomCenterX = bounds.minX() + (bounds.maxX() - bounds.minX()) / 2;
                double roomCenterY = bounds.minY() + (bounds.maxY() - bounds.minY()) / 2;

                switch (ew) {
                    case "south" -> { windowX = roomCenterX - ventW / 2; windowY = bounds.minY(); }
                    case "north" -> { windowX = roomCenterX - ventW / 2; windowY = bounds.maxY(); }
                    case "west"  -> { windowX = bounds.minX(); windowY = roomCenterY - ventW / 2; }
                    case "east"  -> { windowX = bounds.maxX(); windowY = roomCenterY - ventW / 2; }
                    default -> { windowX = bounds.minX(); windowY = bounds.minY(); }
                }

                ctx.windows.add(new WindowSpec(
                    windowName, room.name(), ew,
                    windowX, windowY, ctx.baseZ + sillH,
                    ventW, ventH, sillH
                ));
            }
        }
    }

    private static void placeMEPSprinklers(StoreyBuildContext ctx) {
        // Generate MEP elements for rooms (Phase 14B)
        // Phase 85: Sprinkler Z from BOM metadata (BELOW_SLAB rule) — separate from ceilingZ
        BOMRuleAD.BOMPlacementParams headParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "HEAD");
        ctx.sprinklerZ = headParams.resolveZ(ctx.baseZ, ctx.storey.height(), BIMConstants.STANDARD_SLAB_THICKNESS);
        for (var roomMEP : ctx.roomsWithMEP) {
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
                        ctx.sprinklers.add(new SprinklerSpec(
                            roomMEP.name() + "_sprinkler_" + (++sprinklerIndex),
                            roomMEP.name(),
                            x, y, ctx.sprinklerZ,
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
    }

    private static void placeFixturesAndFurniture(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase B4: Unified slot dispatch — fixtures, furniture, all through
        // WorkerRegistry. FixtureWorker for fixture slots, FurnitureWorker default.
        // =====================================================================
        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");

            // Unified WorkerRegistry: FixtureWorker for fixture BOMs, FurnitureWorker default
            var slotRegistry = SlotRegistry.getInstance();
            var workerRegistry = new WorkerRegistry();
            workerRegistry.registerDefault(id -> new FurnitureWorker(id, library));
            workerRegistry.register("TOILET_BLOCK_FIXTURES",
                new com.bim.compiler.library.FixtureWorker("TOILET_BLOCK_FIXTURES", library));
            workerRegistry.register("DUPLEX_BATHROOM_SET",
                new com.bim.compiler.library.FixtureWorker("DUPLEX_BATHROOM_SET", library));

            // BOM resolver for fallback quantity hints
            BOMResolver bomResolver = null;
            Map<String, BOMResolver.RoomBOM> roomBOMs = new HashMap<>();
            try {
                bomResolver = new BOMResolver();
                for (RoomSpec room : ctx.rooms) {
                    double roomArea = (room.maxX() - room.minX()) * (room.maxY() - room.minY());
                    BOMResolver.RoomBOM bom = bomResolver.resolveRoom(
                        room.name(), room.type().toUpperCase(), roomArea, 0);
                    roomBOMs.put(room.name(), bom);
                }
            } catch (SQLException e) {
                System.out.println("[BOM] BOMResolver not available: " + e.getMessage());
            }

            // Fallback for room types without ad_room_slot entries
            var furnitureTypeResolver = new FurnitureTypeResolver();
            var furniturePlacer = new com.bim.compiler.library.FurniturePlacer(library);

            var placementCtx = new com.bim.compiler.contract.BundleWorker.PlacementContext(
                ctx.baseZ, ctx.ceilingZ, 0.15, "RC_FRAME");

            for (RoomSpec room : ctx.rooms) {
                String roomType = room.type().toUpperCase();

                // Build openings: base from RoomSpec + augmented context for fixtures
                var openings = new java.util.ArrayList<com.bim.compiler.contract.BundleWorker.OpeningInfo>();
                if (room.openings() != null) {
                    for (var o : room.openings()) {
                        openings.add(new com.bim.compiler.contract.BundleWorker.OpeningInfo(
                            o.type(), o.wall(), o.width()));
                    }
                }
                // Augment with exterior walls for toilet rooms (from DSL RoomDef)
                if (roomType.contains("TOILET") || roomType.equals("WC")) {
                    for (String ew : findRoomDefExteriorWalls(ctx.storey, room.name())) {
                        openings.add(new com.bim.compiler.contract.BundleWorker.OpeningInfo("EXTERIOR", ew, 0));
                    }
                } else if (roomType.equals("KITCHEN")) {
                    String ew = findExteriorWall(room, ctx.minX, ctx.minY, ctx.maxX, ctx.maxY);
                    if (ew != null) {
                        openings.add(new com.bim.compiler.contract.BundleWorker.OpeningInfo("WINDOW", ew, 0));
                    }
                }

                var envelope = new com.bim.compiler.contract.BundleWorker.RoomEnvelope(
                    room.name(), roomType,
                    room.minX(), room.minY(), room.minZ(),
                    room.maxX(), room.maxY(), room.maxZ(),
                    openings, List.of());

                // Dispatch all slots for this room type
                var allSlots = slotRegistry.getSlotsForType(roomType, ctx.building.profile());
                boolean dispatched = false;
                var allPlacedElements = new java.util.ArrayList<com.bim.compiler.contract.BundleWorker.PlacedElement>();

                for (var slot : allSlots) {
                    if (slot.assemblyId() == null) continue;
                    if ("CEILING_MEP".equals(slot.slotName())) continue;

                    var worker = workerRegistry.getWorker(slot.assemblyId());
                    var placedElements = worker.execute(envelope, placementCtx);
                    addPlacedElementsToCtx(ctx, room.name(), placedElements);
                    allPlacedElements.addAll(placedElements);
                    dispatched = true;
                }

                // Phase 98: Stall divider walls between toilets (post-dispatch)
                if (roomType.contains("TOILET") || roomType.equals("WC")) {
                    long toiletCount = allPlacedElements.stream()
                        .filter(e -> "TOILET".equals(e.role())).count();
                    if (toiletCount > 1) {
                        String doorWall = findDoorWall(room);
                        String dw = doorWall != null ? doorWall.toLowerCase() : "west";
                        String backWall = oppositeWall(dw);
                        boolean backIsEW = backWall.equals("east") || backWall.equals("west");
                        double wallLen = backIsEW
                            ? (room.maxY() - room.minY()) : (room.maxX() - room.minX());
                        int tc = (int) toiletCount;
                        double stallSpacing = 1.3;
                        double totalStalls = tc * stallSpacing;
                        double startOff = (wallLen - totalStalls) / 2.0;
                        double dividerDepth = 1.2;
                        double stallHeight = 1.8;

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
                            ctx.walls.add(compileWall(
                                "STALL_" + room.name() + "_" + di,
                                x1, y1, x2, y2,
                                ctx.baseZ, ctx.baseZ + stallHeight,
                                ctx.storey.name(), ctx.registry, 0.05));
                        }
                        System.out.printf("[STALL] %s: %d dividers for %d stalls%n",
                            room.name(), tc - 1, tc);
                    }
                }

                if (!dispatched) {
                    // Fallback — FurnitureTypeResolver for room types without ad_room_slot
                    BOMResolver.RoomBOM bom = roomBOMs.get(room.name());
                    int furnitureQty = (bom != null) ? bom.getQuantity("FURNITURE") : -1;
                    var rule = furnitureTypeResolver.resolve(roomType);

                    if ("CANTEEN".equals(rule.fallback())) {
                        var placed = furniturePlacer.placeCanteenFurniture(
                            room.minX(), room.minY(), room.maxX(), room.maxY(),
                            ctx.baseZ, room.name(), furnitureQty
                        );
                        addFurnitureToCtx(ctx, room.name(), placed);
                    } else if ("SEATING".equals(rule.fallback())) {
                        var placed = furniturePlacer.placeGenericFurniture(
                            room.minX(), room.minY(), room.maxX(), room.maxY(),
                            ctx.baseZ, room.name()
                        );
                        addFurnitureToCtx(ctx, room.name(), placed);
                    } else if ("WORKSTATION".equals(rule.fallback())) {
                        var placed = furniturePlacer.placeOfficeFurniture(
                            room.minX(), room.minY(), room.maxX(), room.maxY(),
                            ctx.baseZ, room.name()
                        );
                        addFurnitureToCtx(ctx, room.name(), placed);
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

    }

    /** Phase 108B: Convert placed furniture instances to FixtureSpecs. */
    private static void addFurnitureToCtx(StoreyBuildContext ctx, String roomName,
            List<com.bim.compiler.library.FurniturePlacer.FurnitureInstance> placed) {
        int idx = 0;
        for (var f : placed) {
            ctx.fixtures.add(new FixtureSpec(
                roomName + "_" + f.type().name().toLowerCase() + "_" + (++idx),
                roomName, f.type().name().toLowerCase(),
                f.worldPosition().x(), f.worldPosition().y(), f.worldPosition().z(),
                f.rotation(), f.geometryHash(),
                f.localBounds().width(), f.localBounds().depth(), f.localBounds().height()
            ));
        }
    }

    /** Phase 118B: Convert BundleWorker PlacedElements to FixtureSpecs. */
    private static void addPlacedElementsToCtx(StoreyBuildContext ctx, String roomName,
            List<com.bim.compiler.contract.BundleWorker.PlacedElement> elements) {
        int idx = 0;
        for (var e : elements) {
            ctx.fixtures.add(new FixtureSpec(
                roomName + "_" + e.role().toLowerCase() + "_" + (++idx),
                roomName, e.role().toLowerCase(),
                e.x(), e.y(), e.z(),
                e.rotation(), e.geometryHash(),
                e.width(), e.depth(), e.height()
            ));
        }
    }

    private static void placeStructural(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 23/122A: Auto-place structural elements (columns, lintels, frame beams)
        // Phase 122A: Profile-aware beam generation via BeamTypeResolver
        //   - Lintels: sized from resolver (fallback to hardcoded LINTEL_DEPTH)
        //   - Frame beams: only generated if profile has FLOOR rules in ad_beam_type_rule
        //   - Per-room grid beams (Phase 50B.1): REMOVED — subsumed by profile-aware frame
        // =====================================================================

        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var structuralPlacer = new com.bim.compiler.library.StructuralPlacer(library);
            var beamResolver = BeamTypeResolver.getInstance();

            // Build wall info for T-junction detection
            List<com.bim.compiler.library.StructuralPlacer.WallInfo> interiorWalls = new ArrayList<>();
            for (WallAssemblySpec wall : ctx.walls) {
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
            for (int i = 0; i < ctx.storey.rooms().size(); i++) {
                for (int j = i + 1; j < ctx.storey.rooms().size(); j++) {
                    RoomDef room1 = ctx.storey.rooms().get(i);
                    RoomDef room2 = ctx.storey.rooms().get(j);
                    double[] b1 = ctx.roomBounds.get(room1.name());
                    double[] b2 = ctx.roomBounds.get(room2.name());

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
            List<Point3D> corners = structuralPlacer.findCorners(ctx.minX, ctx.minY, ctx.maxX, ctx.maxY, ctx.baseZ);
            List<Point3D> tJunctions = structuralPlacer.findTJunctions(
                interiorWalls, ctx.minX, ctx.minY, ctx.maxX, ctx.maxY, ctx.baseZ);

            // Phase 2/122D: Place columns using registry + profile-aware sizing
            var placedColumns = structuralPlacer.placeColumns(
                corners, tJunctions, ctx.baseZ, ctx.storey.height(), ctx.storey.name(), ctx.registry,
                ctx.building.profile());
            for (var col : placedColumns) {
                ctx.columns.add(new ColumnSpec(
                    col.id(),
                    col.type().name().toLowerCase(),
                    col.basePosition().x(), col.basePosition().y(), col.basePosition().z(),
                    col.height(),
                    col.width(), col.depth(),
                    col.geometryHash(),
                    col.continuityId(),  // Phase 2: Cross-ctx.storey identity
                    ctx.storey.name()        // Phase 5A: Contract ctx.storey
                ));
            }

            // Build opening info for lintel placement
            List<com.bim.compiler.library.StructuralPlacer.OpeningInfo> openings = new ArrayList<>();
            for (DoorSpec door : ctx.doors) {
                openings.add(new com.bim.compiler.library.StructuralPlacer.OpeningInfo(
                    door.name(), door.wall(),
                    door.x(), door.y(),
                    door.width(), door.height()
                ));
            }
            for (WindowSpec window : ctx.windows) {
                // Window head height = sill + window height (for lintel placement)
                double headHeight = window.sillHeight() + window.height();
                openings.add(new com.bim.compiler.library.StructuralPlacer.OpeningInfo(
                    window.name(), window.wall(),
                    window.x(), window.y(),
                    window.width(), headHeight
                ));
            }

            // Place lintels over openings
            var placedBeams = structuralPlacer.placeLintels(openings, ctx.baseZ);
            for (var beam : placedBeams) {
                ctx.beams.add(new BeamSpec(
                    beam.id(),
                    beam.type().name().toLowerCase(),
                    beam.position().x(), beam.position().y(), beam.position().z(),
                    beam.length(),
                    beam.width(), beam.height(),
                    beam.rotation(),
                    beam.geometryHash()
                ));
            }

            // Phase 50B.1 per-room grid beams: REMOVED in Phase 122A
            // Was: iterate rooms with structural_grid → placeGridBeams per room
            // Subsumed by profile-aware building-wide frame (below)
            // Residential profiles (no ad_beam_type_rule) get no grid beams (correct)
            // Institutional profiles get building-wide frame with resolved dimensions

            // Phase 99A/122A: Building-wide RC frame beam grid — conditional on profile
            // Only generate frame beams if profile has FLOOR beam rules in ad_beam_type_rule
            if (ctx.building.grid() != null) {
                // Test with 8m span — if resolver returns non-null, profile supports RC frame
                BeamTypeResolver.BeamTypeEntry testEntry = beamResolver.resolveFloor(8.0, ctx.building.profile());
                if (testEntry != null) {
                    BoundingBox envelope = new BoundingBox(
                        ctx.minX, ctx.maxX, ctx.minY, ctx.maxY, ctx.baseZ, ctx.baseZ + ctx.storey.height());
                    double gridCeilingZ = ctx.baseZ + ctx.storey.height() - testEntry.depthM();
                    var frameBeams = structuralPlacer.placeGridBeams(
                        envelope, com.bim.compiler.library.StructuralPlacer.MAX_BEAM_SPAN_FRAMED,
                        gridCeilingZ, "FRAME", ctx.building.grid(),
                        testEntry.widthM(), testEntry.depthM());
                    for (var beam : frameBeams) {
                        ctx.beams.add(new BeamSpec(
                            beam.id(), beam.type().name().toLowerCase(),
                            beam.position().x(), beam.position().y(), beam.position().z(),
                            beam.length(), beam.width(), beam.height(),
                            beam.rotation(), beam.geometryHash()));
                    }
                    // Phase 122H: Collect existing column positions to avoid duplicating
                    // CORNER/T_JUNCTION columns at perimeter grid intersections
                    Set<String> existingColPositions = new HashSet<>();
                    for (var ec : ctx.columns) {
                        existingColPositions.add(String.format("%.1f_%.1f", ec.x(), ec.y()));
                    }
                    var frameColumns = structuralPlacer.placeGridColumns(
                        envelope, com.bim.compiler.library.StructuralPlacer.MAX_BEAM_SPAN_FRAMED,
                        ctx.baseZ, ctx.storey.height(), "FRAME", ctx.building.grid(),
                        ctx.building.profile(), existingColPositions);
                    for (var col : frameColumns) {
                        ctx.columns.add(new ColumnSpec(
                            col.id(), col.type().name().toLowerCase(),
                            col.basePosition().x(), col.basePosition().y(), col.basePosition().z(),
                            col.height(), col.width(), col.depth(),
                            col.geometryHash(), null, ctx.storey.name()));
                    }
                    System.out.printf("[FRAME] Storey %s: +%d frame beams (%s, %dx%dmm), +%d frame columns%n",
                        ctx.storey.name(), frameBeams.size(), testEntry.beamTypeId(),
                        testEntry.widthMm(), testEntry.depthMm(), frameColumns.size());
                }
            }

            System.out.printf("[STRUCTURAL] Storey %s: %d columns, %d lintels/beams%n",
                ctx.storey.name(), ctx.columns.size(), ctx.beams.size());

        } catch (Exception e) {
            // Library not available - skip structural placement
            System.out.println("[STRUCTURAL] Library not available: " + e.getMessage());
        }

    }

    private static void placeHVAC(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 24: Auto-place HVAC diffusers (supply/return/exhaust)
        // =====================================================================

        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var hvacPlacer = new com.bim.compiler.library.HVACPlacer(library);

            for (RoomSpec room : ctx.rooms) {
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
                    ctx.baseZ, ctx.ceilingZ + 0.05, roomType
                );

                // Convert to DiffuserSpecs
                for (var d : layout.allDiffusers()) {
                    ctx.diffusers.add(new DiffuserSpec(
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
            for (var d : ctx.diffusers) {
                if ("exhaust".equals(d.diffuserType())) roomsWithExhaust.add(d.roomName());
            }

            int fanCount = 0;
            for (RoomSpec room : ctx.rooms) {
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
                    ctx.diffusers.add(new DiffuserSpec(
                        room.name() + "_ceiling_fan_" + (setIdx + 1), room.name(), "exhaust",
                        cx, cy, ctx.ceilingZ, 50, ceilingFanHash
                    ));
                    fanCount++;
                }
            }

            System.out.printf("[HVAC] Storey %s: %d diffusers (%d ceiling fans added)%n",
                ctx.storey.name(), ctx.diffusers.size(), fanCount);

        } catch (Exception e) {
            // Library not available - skip HVAC placement
            System.out.println("[HVAC] Library not available: " + e.getMessage());
        }

    }

    private static void placeElectrical(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 33: Auto-place electrical elements (lights, outlets, switches)
        // Phase 44: With T-junction column avoidance
        // =====================================================================
        // Surface-mounted lights attach directly to ceiling (no offset)
        double actualCeilingZ = ctx.baseZ + ctx.storey.height();
        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            var electricalPlacer = new com.bim.compiler.library.ElectricalPlacer(library);

            // Phase 44: Build column zones for clash avoidance
            List<com.bim.compiler.library.ElectricalPlacer.ColumnZone> columnZones = new ArrayList<>();
            for (ColumnSpec col : ctx.columns) {
                columnZones.add(new com.bim.compiler.library.ElectricalPlacer.ColumnZone(
                    col.x(), col.y(),
                    col.width() / 2, col.depth() / 2
                ));
            }
            electricalPlacer.setColumnZones(columnZones);

            // Phase 90: Calculate sprinkler offset for light grid staggering
            // Use half the typical sprinkler spacing (2.3m / 2 = 1.15m)
            double sprinklerOffset = 1.15;
            if (!ctx.roomsWithMEP.isEmpty()) {
                for (var rm : ctx.roomsWithMEP) {
                    if (rm.sprinklerSpacing() != null) {
                        sprinklerOffset = rm.sprinklerSpacing() / 2.0;
                        break;
                    }
                }
            }
            electricalPlacer.setLightGridOffset(sprinklerOffset, sprinklerOffset);

            // Phase 44: Generate DSL-specified lights with column avoidance
            // These are rooms with "LIGHTS grid:X.Xm" in DSL
            for (var roomMEP : ctx.roomsWithMEP) {
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
                double lightCeilingZ = ctx.baseZ + ctx.storey.height();

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
                        ctx.lights.add(new LightSpec(
                            roomMEP.name() + "_light_" + (++lightIndex),
                            roomMEP.name(),
                            x, y, lightZ,
                            "surface",
                            spacing
                        ));
                    }
                }
            }

            for (RoomSpec room : ctx.rooms) {
                // Get MEP config from SpaceTypeRegistry
                var spaceConfig = SpaceTypeRegistry.get(room.type());
                var elecConfig = spaceConfig.mep().electrical();

                // Skip rooms with no electrical requirements
                if (elecConfig.lightPoints() == 0 && elecConfig.powerPoints() == 0 && elecConfig.switchPoints() == 0) {
                    continue;
                }

                var placed = electricalPlacer.placeElectricalElements(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    ctx.baseZ, actualCeilingZ,  // Use actual ceiling for surface-mount
                    elecConfig,
                    room.name()
                );

                // Phase 42: Check if room already has DSL-specified lights
                final String roomNameFinal = room.name();
                boolean hasDslLights = ctx.lights.stream()
                    .anyMatch(l -> l.roomName().equals(roomNameFinal));

                int elementIdx = 0;
                for (var e : placed) {
                    if (e.type() == com.bim.compiler.library.ElectricalPlacer.ElectricalType.LIGHT) {
                        // Skip if DSL already specified lights for this room
                        if (hasDslLights) {
                            continue;
                        }
                        // Add to lights list (with library support)
                        ctx.lights.add(new LightSpec(
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
                        ctx.electricals.add(new ElectricalSpec(
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
                ctx.storey.name(), ctx.lights.size(), ctx.electricals.size());

        } catch (Exception e) {
            // Library not available - skip electrical placement
            System.out.println("[ELEC] Library not available: " + e.getMessage());
        }

    }

    private static void placePlumbing(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 34: Auto-place plumbing pipes (risers, vents, branches)
        // =====================================================================
        double plumbingCeilingZ = ctx.baseZ + ctx.storey.height();
        double roofZ = plumbingCeilingZ + 0.5;  // Estimate roof 500mm above ceiling

        try {
            var plumbingPlacer = new com.bim.compiler.library.PlumbingPlacer();

            for (RoomSpec room : ctx.rooms) {
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

                for (FixtureSpec fixture : ctx.fixtures) {
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
                    ctx.baseZ, plumbingCeilingZ, roofZ,
                    plumbingConfig,
                    room.name(),
                    hasToilet, toiletX, toiletY,
                    hasSink, sinkX, sinkY
                );

                for (var pipe : pipes) {
                    ctx.plumbing.add(new PlumbingSpec(
                        pipe.name(),
                        room.name(),
                        pipe.type().name().toLowerCase(),
                        pipe.start().x(), pipe.start().y(), pipe.start().z(),
                        pipe.end().x(), pipe.end().y(), pipe.end().z(),
                        pipe.diameterM()
                    ));
                }
            }

            if (!ctx.plumbing.isEmpty()) {
                System.out.printf("[PLUMB] Storey %s: %d pipes%n", ctx.storey.name(), ctx.plumbing.size());
            }

        } catch (Exception e) {
            // Plumbing placer not available - skip
            System.out.println("[PLUMB] PlumbingPlacer error: " + e.getMessage());
        }

    }

    private static void mepBomGapFill(StoreyBuildContext ctx) {
        // =====================================================================
        // Phase 92D/B4: Data-driven MEP gap-fill via MEPWorker adapter.
        // MEPWorker resolves + positions; gap-fill filtering stays here.
        // =====================================================================

        // Index existing MEP by room name (gap-fill = skip if already present)
        Set<String> roomsWithSprinkler = new HashSet<>();
        for (var s : ctx.sprinklers) roomsWithSprinkler.add(s.roomName());
        Set<String> roomsWithLight = new HashSet<>();
        for (var l : ctx.lights) roomsWithLight.add(l.roomName());
        Set<String> roomsWithSupply = new HashSet<>();
        Set<String> roomsWithFan = new HashSet<>();
        for (var d : ctx.diffusers) {
            if ("exhaust".equals(d.diffuserType())) roomsWithFan.add(d.roomName());
            else roomsWithSupply.add(d.roomName());
        }

        com.bim.compiler.library.MEPWorker mepWorker;
        try {
            var library = new com.bim.compiler.library.ComponentLibrary("library/component_library.db");
            mepWorker = new com.bim.compiler.library.MEPWorker(library);
        } catch (Exception e) {
            System.err.println("[MEP-BOM] Library not available: " + e.getMessage());
            return;
        }

        var placementCtx = new com.bim.compiler.contract.BundleWorker.PlacementContext(
            ctx.baseZ, ctx.ceilingZ, 0.15, "RC_FRAME");

        int added = 0;
        for (RoomSpec room : ctx.rooms) {
            var envelope = new com.bim.compiler.contract.BundleWorker.RoomEnvelope(
                room.name(), room.type().toUpperCase(),
                room.minX(), room.minY(), room.minZ(),
                room.maxX(), room.maxY(), room.maxZ(),
                List.of(), List.of());

            var placedElements = mepWorker.execute(envelope, placementCtx);

            // Gap-fill: only add elements for MEP types the room doesn't already have
            for (var e : placedElements) {
                switch (e.role()) {
                    case "SPRINKLER" -> {
                        if (!roomsWithSprinkler.contains(room.name())) {
                            ctx.sprinklers.add(new SprinklerSpec(
                                e.name(), room.name(),
                                e.x(), e.y(), e.z(), "pendant", 0
                            ));
                            added++;
                        }
                    }
                    case "LIGHT" -> {
                        if (!roomsWithLight.contains(room.name())) {
                            ctx.lights.add(new LightSpec(
                                e.name(), room.name(),
                                e.x(), e.y(), e.z(), "surface", 0,
                                e.geometryHash(), e.width(), e.depth(), e.height()
                            ));
                            added++;
                        }
                    }
                    case "SUPPLY_DIFFUSER" -> {
                        if (!roomsWithSupply.contains(room.name())) {
                            ctx.diffusers.add(new DiffuserSpec(
                                e.name(), room.name(), "supply",
                                e.x(), e.y(), e.z(), (int) e.width(), null
                            ));
                            added++;
                        }
                    }
                    case "CEILING_FAN" -> {
                        if (!roomsWithFan.contains(room.name())) {
                            ctx.diffusers.add(new DiffuserSpec(
                                e.name(), room.name(), "exhaust",
                                e.x(), e.y(), e.z(), (int) e.width(), null
                            ));
                            added++;
                        }
                    }
                }
            }
        }
        if (added > 0) {
            System.out.printf("[MEP-BOM] Storey %s: %d elements added to fill gaps%n",
                ctx.storey.name(), added);
        }
    }

    // =================================================================
    // Phase B2: Placement Determinism — metadata overrides computed positions
    // =================================================================

    /**
     * Phase B2: If placement metadata exists for this building, replace computed
     * element lists with metadata-driven elements. The metadata IS the production
     * list — each row = one element at extracted reference coordinates.
     *
     * Only overrides classes that have metadata entries. Classes without metadata
     * (e.g., MEP) continue using the computed path.
     */
    private static void applyPlacementOverrides(StoreyBuildContext ctx) {
        PlacementAD pad = PlacementAD.getInstance();
        String buildingName = ctx.building.name();
        if (!pad.hasPlacement(buildingName)) return;

        String storeyName = ctx.storey.name();

        // --- WALLS (IfcWall entries → our IfcPlate cladding panels) ---
        List<PlacementAD.Placement> wallPlacements = pad.get(buildingName, storeyName, "IfcWall");
        if (!wallPlacements.isEmpty()) {
            int oldCount = ctx.walls.size();
            ctx.walls.clear();

            // Resolve wall types from profile
            var extEntry = WallTypeResolver.getInstance().resolve(
                "EXTERIOR", null, null, ctx.building.profile());
            double extWallT = extEntry != null ? extEntry.thicknessM() : BIMConstants.STANDARD_WALL_THICKNESS;
            String extWallName = extEntry != null ? extEntry.ifcName() : "Metal Deck";

            var intEntry = WallTypeResolver.getInstance().resolve(
                "INTERIOR", null, null, ctx.building.profile());
            double intWallT = intEntry != null ? intEntry.thicknessM() : BIMConstants.STANDARD_WALL_THICKNESS;
            String intWallName = intEntry != null ? intEntry.ifcName() : "Metal Deck";

            for (PlacementAD.Placement wp : wallPlacements) {
                boolean isInterior = wp.elementRef().contains("Partn")
                    || wp.elementRef().contains("INT")
                    || wp.elementRef().contains("Interior");
                double wallT = isInterior ? intWallT : extWallT;
                String wallMat = isInterior ? intWallName : extWallName;

                double x1, y1, x2, y2;
                if ("EW".equals(wp.orientation())) {
                    double cy = wp.cy();
                    x1 = wp.minX(); y1 = cy;
                    x2 = wp.maxX(); y2 = cy;
                } else if ("NS".equals(wp.orientation())) {
                    double cx = wp.cx();
                    x1 = cx; y1 = wp.minY();
                    x2 = cx; y2 = wp.maxY();
                } else {
                    x1 = wp.minX(); y1 = wp.minY();
                    x2 = wp.maxX(); y2 = wp.maxY();
                }

                String side = "MD_WALL_" + wp.ordinal() + "_" + wp.orientation();
                var wall = compileWall(side,
                    x1, y1, x2, y2,
                    wp.minZ(), wp.maxZ(),
                    storeyName, ctx.registry, wallT, wallMat);
                // Phase DE-2: Strip framing — metadata walls only need cladding (IfcPlate).
                // The reference IfcMember elements come from global emission (STR_MD_MEMBER_*).
                ctx.walls.add(new WallAssemblySpec(wall.assemblyName(), wall.assemblyType(), wall.side(),
                    wall.length(), wall.thickness(), wall.height(), wall.storeyName(),
                    List.of(), wall.cladding(), wall.wallType(), wall.fireRating()));
            }

            System.out.printf("[PLACEMENT] Storey %s: %d walls from metadata (was %d computed)%n",
                storeyName, ctx.walls.size(), oldCount);
        }

        // --- SLABS (IfcSlab entries) ---
        List<PlacementAD.Placement> slabPlacements = pad.get(buildingName, storeyName, "IfcSlab");
        if (!slabPlacements.isEmpty()) {
            ctx.slab = null;
            ctx.baySlabs.clear();
            for (PlacementAD.Placement sp : slabPlacements) {
                String role = ctx.isGround ? "FOUNDATION" : "FLOOR";
                SlabSpec slab = new SlabSpec(role, sp.elementRef(),
                    sp.minX(), sp.minY(), sp.maxX(), sp.maxY(),
                    sp.minZ(), sp.maxZ());
                if (ctx.slab == null) {
                    ctx.slab = slab;
                } else {
                    ctx.baySlabs.add(slab);
                }
            }
            System.out.printf("[PLACEMENT] Storey %s: %d slabs from metadata%n",
                storeyName, slabPlacements.size());
        }

        // --- DOORS (IfcDoor entries) ---
        List<PlacementAD.Placement> doorPlacements = pad.get(buildingName, storeyName, "IfcDoor");
        if (!doorPlacements.isEmpty()) {
            int oldCount = ctx.doors.size();
            ctx.doors.clear();
            for (PlacementAD.Placement dp : doorPlacements) {
                String hostWall = findNearestWallSide(ctx.walls, dp.cx(), dp.cy());
                // DoorSpec: (name, room, wall, x, y, z, width, height, connectsTo, depth)
                double doorWidth = Math.max(dp.dx(), dp.dy());
                double doorDepth = Math.min(dp.dx(), dp.dy());
                ctx.doors.add(new DoorSpec(
                    "MD_DOOR_" + dp.ordinal(),
                    "", hostWall,
                    dp.minX(), dp.minY(), dp.minZ(),
                    doorWidth, dp.dz(),
                    null, doorDepth
                ));
            }
            System.out.printf("[PLACEMENT] Storey %s: %d doors from metadata (was %d computed)%n",
                storeyName, ctx.doors.size(), oldCount);
        }

        // --- WINDOWS (IfcWindow entries) ---
        List<PlacementAD.Placement> winPlacements = pad.get(buildingName, storeyName, "IfcWindow");
        if (!winPlacements.isEmpty()) {
            int oldCount = ctx.windows.size();
            ctx.windows.clear();
            for (PlacementAD.Placement wp : winPlacements) {
                String hostWall = findNearestWallSide(ctx.walls, wp.cx(), wp.cy());
                // WindowSpec: (name, room, wall, x, y, z, width, height, sillHeight, depth)
                double winWidth = Math.max(wp.dx(), wp.dy());
                double winDepth = Math.min(wp.dx(), wp.dy());
                ctx.windows.add(new WindowSpec(
                    "MD_WIN_" + wp.ordinal(),
                    "", hostWall,
                    wp.minX(), wp.minY(), wp.minZ(),
                    winWidth, wp.dz(),
                    wp.minZ() - ctx.baseZ, winDepth
                ));
            }
            System.out.printf("[PLACEMENT] Storey %s: %d windows from metadata (was %d computed)%n",
                storeyName, ctx.windows.size(), oldCount);
        }

        // --- FURNITURE (IfcFurnishingElement entries) ---
        List<PlacementAD.Placement> furnPlacements = pad.get(buildingName, storeyName, "IfcFurnishingElement");
        if (!furnPlacements.isEmpty()) {
            int oldCount = ctx.fixtures.size();
            ctx.fixtures.clear();
            // Open component library for LOD400 geometry hash resolution
            ComponentLibrary furnitureLibrary = null;
            try {
                furnitureLibrary = new ComponentLibrary("library/component_library.db");
            } catch (Exception e) {
                // Can't open library — all furniture falls back to box geometry
            }
            for (PlacementAD.Placement fp : furnPlacements) {
                // Map reference element name to fixture type keyword for IfcFurniture dispatch
                String fixtureType = mapToFixtureType(fp.elementRef());
                // Resolve LOD400 geometry hash from component library
                String geoHash = resolveComponentHash(fp.elementRef(), furnitureLibrary);
                // FixtureSpec position: x,y = centroid, z = minZ. Width/depth/height = bbox dims.
                ctx.fixtures.add(new FixtureSpec(
                    "MD_FURN_" + fp.ordinal(),
                    "", fixtureType,
                    fp.cx(), fp.cy(), fp.minZ(),
                    0.0, geoHash,
                    fp.dx(), fp.dy(), fp.dz()
                ));
            }
            if (furnitureLibrary != null) {
                try { furnitureLibrary.close(); } catch (Exception ignored) {}
            }
            System.out.printf("[PLACEMENT] Storey %s: %d furniture from metadata (was %d computed)%n",
                storeyName, ctx.fixtures.size(), oldCount);
        }
    }

    /**
     * Phase B2: Map reference element name to fixture type keyword for IfcFurniture dispatch.
     */
    private static String mapToFixtureType(String elementRef) {
        if (elementRef == null) return "generic_seating";
        String lower = elementRef.toLowerCase();
        if (lower.contains("bed")) return "bed";
        if (lower.contains("desk")) return "workstation_desk";
        if (lower.contains("chair") && lower.contains("dining")) return "dining_chair";
        if (lower.contains("chair") && lower.contains("viper")) return "lounge_chair";
        if (lower.contains("chair")) return "generic_seating";
        if (lower.contains("table") && lower.contains("dining")) return "dining_table";
        if (lower.contains("table") && lower.contains("coffee")) return "coffee_table";
        if (lower.contains("table") && lower.contains("side")) return "side_table";
        if (lower.contains("table")) return "dining_table";
        if (lower.contains("couch") || lower.contains("sofa")) return "sofa";
        if (lower.contains("piano")) return "piano";
        if (lower.contains("cabinet")) return "cabinet";
        if (lower.contains("counter")) return "counter_top";
        return "generic_seating";
    }

    /**
     * Phase LOD400: Resolve element_ref to component_library geometry hash.
     * Maps furniture element names from placement metadata to library component names
     * via keyword matching, then returns the geometry_hash for LOD400 mesh rendering.
     * Returns null if no match (falls back to box geometry).
     */
    static String resolveComponentHash(String elementRef, ComponentLibrary library) {
        if (elementRef == null || library == null) return null;
        String lower = elementRef.toLowerCase();

        String componentName = null;

        // 1. Beds
        if (lower.contains("bed")) {
            if (lower.contains("king")) componentName = "Bed_King";
            else if (lower.contains("single")) componentName = "Bed_Single";
            else componentName = "Bed_Queen";
        }
        // 2. Chairs (check before tables — "dining chair" should match chair, not table)
        else if (lower.contains("chair") && !lower.contains("table")) {
            if (lower.contains("viper")) componentName = "Lounge_Chair";
            else if (lower.contains("dining")) componentName = "Dining_Chair";
            else componentName = "Dining_Chair";
        }
        // 3. Tables
        else if (lower.contains("table")) {
            if (lower.contains("dining") && lower.contains("chair")) componentName = "Dining_Table_With_Chairs";
            else if (lower.contains("dining")) componentName = "Dining_Table";
            else if (lower.contains("coffee") && lower.contains("061")) componentName = "Side_Table_Cube_610";
            else if (lower.contains("coffee") && lower.contains("091")) componentName = "Coffee_Table_Large";
            else if (lower.contains("coffee")) componentName = "Coffee_Table_Rect_1200";
            else if (lower.contains("side")) componentName = "Side_Table";
            else if (lower.contains("canteen")) componentName = "Canteen Table";
            else componentName = "Coffee_Table_Rect_1200";
        }
        // 4. Seating
        else if (lower.contains("couch") || lower.contains("sofa")) {
            componentName = "Sofa";
        }
        // 5. Other
        else if (lower.contains("piano")) componentName = "Piano";
        else if (lower.contains("desk")) componentName = "Desk";
        else if (lower.contains("counter")) componentName = "Counter_Top";
        else if (lower.contains("cabinet")) {
            if (lower.contains("base")) componentName = "Base_Cabinet";
            else if (lower.contains("tall")) componentName = "Tall_Cabinet";
            else if (lower.contains("upper")) componentName = "Upper_Cabinet";
            else if (lower.contains("vanity")) componentName = "Vanity_Cabinet";
            else componentName = "Cabinet";
        }
        else if (lower.contains("wardrobe")) componentName = "Wardrobe";

        if (componentName == null) return null;

        try {
            var def = library.getByName(componentName);
            return def != null ? def.geometryHash() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String findNearestWallSide(List<WallAssemblySpec> walls, double cx, double cy) {
        String best = "unknown";
        double bestDist = Double.MAX_VALUE;
        for (WallAssemblySpec w : walls) {
            if (w.cladding() == null) continue;
            double wcx = (w.cladding().minX() + w.cladding().maxX()) / 2.0;
            double wcy = (w.cladding().minY() + w.cladding().maxY()) / 2.0;
            double d = Math.sqrt((cx - wcx) * (cx - wcx) + (cy - wcy) * (cy - wcy));
            if (d < bestDist) {
                bestDist = d;
                best = w.side();
            }
        }
        return best;
    }

    /**
     * Phase 122I: Split walls at opening edges and column faces.
     * Reference buildings break walls into segments at doorframes, window mullions, and column faces.
     * Our compiler produces one wall per room side → this post-processing step splits them.
     *
     * Algorithm: For each wall, find openings/columns on its line, compute cut points
     * along the wall's primary axis, produce wall segments for the solid portions.
     */
    private static void segmentWallsAtOpenings(StoreyBuildContext ctx) {
        // Only segment for buildings with structural grids (institutional/commercial).
        // Residential buildings have full-room-side walls matching their reference.
        boolean isStructuralGrid = ctx.building.grid() != null
                && ctx.building.grid().xSpacing() != null
                && ctx.building.grid().ySpacing() != null
                && ctx.building.grid().xSpacing().size() * ctx.building.grid().ySpacing().size() >= 6;
        if (!isStructuralGrid) return;

        record OpeningPos(double axisStart, double axisEnd) {}

        List<WallAssemblySpec> segmented = new ArrayList<>();
        int originalCount = ctx.walls.size();
        double TOL = 0.05; // 50mm tolerance for matching opening to wall line

        for (WallAssemblySpec wall : ctx.walls) {
            CladdingSpec clad = wall.cladding();
            if (clad == null) { segmented.add(wall); continue; }

            // Normalize bounds (west/south walls may have min > max)
            double wMinX = Math.min(clad.minX(), clad.maxX());
            double wMaxX = Math.max(clad.minX(), clad.maxX());
            double wMinY = Math.min(clad.minY(), clad.maxY());
            double wMaxY = Math.max(clad.minY(), clad.maxY());

            // Determine wall orientation: horizontal (X-running) or vertical (Y-running)
            double xSpan = wMaxX - wMinX;
            double ySpan = wMaxY - wMinY;
            boolean isHorizontal = xSpan > ySpan;
            // Short walls (< 2m) don't benefit from segmentation
            double wallLen = isHorizontal ? xSpan : ySpan;
            if (wallLen < 2.0) { segmented.add(wall); continue; }

            double wallThickness = isHorizontal ? ySpan : xSpan;
            double wallLinePos = isHorizontal
                ? (wMinY + wMaxY) / 2.0   // Y-center for horizontal walls
                : (wMinX + wMaxX) / 2.0;  // X-center for vertical walls

            // Find openings on this wall
            List<OpeningPos> cuts = new ArrayList<>();

            for (DoorSpec door : ctx.doors) {
                double doorAxis, doorPerp, doorWidth;
                if (isHorizontal) {
                    doorAxis = door.x();
                    doorPerp = door.y();
                    doorWidth = door.width();
                } else {
                    doorAxis = door.y();
                    doorPerp = door.x();
                    doorWidth = door.width();
                }
                if (Math.abs(doorPerp - wallLinePos) < TOL + wallThickness / 2.0) {
                    cuts.add(new OpeningPos(doorAxis, doorAxis + doorWidth));
                }
            }

            for (WindowSpec win : ctx.windows) {
                double winAxis, winPerp, winWidth;
                if (isHorizontal) {
                    winAxis = win.x();
                    winPerp = win.y();
                    winWidth = win.width();
                } else {
                    winAxis = win.y();
                    winPerp = win.x();
                    winWidth = win.width();
                }
                if (Math.abs(winPerp - wallLinePos) < TOL + wallThickness / 2.0) {
                    cuts.add(new OpeningPos(winAxis, winAxis + winWidth));
                }
            }

            // Find columns on this wall line
            for (ColumnSpec col : ctx.columns) {
                double colAxis, colPerp, colHalf;
                if (isHorizontal) {
                    colAxis = col.x();
                    colPerp = col.y();
                    colHalf = col.width() / 2.0;  // column width along wall axis
                } else {
                    colAxis = col.y();
                    colPerp = col.x();
                    colHalf = col.depth() / 2.0;
                }
                if (Math.abs(colPerp - wallLinePos) < TOL + wallThickness / 2.0 + col.depth() / 2.0) {
                    cuts.add(new OpeningPos(colAxis - colHalf, colAxis + colHalf));
                }
            }

            // No openings/columns → keep original wall
            if (cuts.isEmpty()) { segmented.add(wall); continue; }

            // Sort cuts by start position and merge overlapping
            cuts.sort(Comparator.comparingDouble(OpeningPos::axisStart));
            List<OpeningPos> merged = new ArrayList<>();
            OpeningPos current = cuts.get(0);
            for (int i = 1; i < cuts.size(); i++) {
                OpeningPos next = cuts.get(i);
                if (next.axisStart() <= current.axisEnd() + TOL) {
                    current = new OpeningPos(current.axisStart(),
                        Math.max(current.axisEnd(), next.axisEnd()));
                } else {
                    merged.add(current);
                    current = next;
                }
            }
            merged.add(current);

            // Generate wall segments for solid portions between openings
            double wallStart = isHorizontal ? wMinX : wMinY;
            double wallEnd = isHorizontal ? wMaxX : wMaxY;
            double minZ = clad.minZ();
            double maxZ = clad.maxZ();
            // Original wall endpoint coordinates (before normal offset)
            double origX1 = clad.minX(), origY1 = clad.minY();
            double origX2, origY2;
            // Recover the wall line direction — the cladding includes normal offset
            if (isHorizontal) {
                origY1 = wMinY;  // south face of wall
                origY2 = origY1;
                origX2 = clad.maxX(); // use raw maxX (may include nx=0 for horizontal)
            } else {
                origX1 = wMinX;
                origX2 = origX1;
                origY2 = clad.maxY();
            }

            int segIdx = 0;
            double pos = wallStart;
            double minSegLen = 0.15;  // Skip tiny segments (< 150mm)
            for (OpeningPos cut : merged) {
                double cutStart = Math.max(cut.axisStart(), wallStart);
                double cutEnd = Math.min(cut.axisEnd(), wallEnd);
                // Segment before this opening
                if (cutStart - pos > minSegLen) {
                    segmented.add(createWallSegment(wall, pos, cutStart,
                        isHorizontal, origY1, origX1, wallThickness,
                        minZ, maxZ, segIdx++, ctx.registry));
                }
                pos = cutEnd;
            }
            // Final segment after last opening
            if (wallEnd - pos > minSegLen) {
                segmented.add(createWallSegment(wall, pos, wallEnd,
                    isHorizontal, origY1, origX1, wallThickness,
                    minZ, maxZ, segIdx++, ctx.registry));
            }
        }

        if (segmented.size() != originalCount) {
            System.out.printf("[WALL-SEG] Storey %s: %d walls → %d segments (+%d)%n",
                ctx.storey.name(), originalCount, segmented.size(),
                segmented.size() - originalCount);
        }
        ctx.walls = segmented;
    }

    /** Create a wall segment from axisStart to axisEnd along the wall's primary direction. */
    private static WallAssemblySpec createWallSegment(
            WallAssemblySpec parent, double axisStart, double axisEnd,
            boolean isHorizontal, double constCoord, double constCoordAlt,
            double thickness, double minZ, double maxZ,
            int segIdx, SharedElementRegistry registry) {
        double x1, y1, x2, y2;
        if (isHorizontal) {
            x1 = axisStart; y1 = constCoord;
            x2 = axisEnd;   y2 = constCoord;
        } else {
            x1 = constCoordAlt; y1 = axisStart;
            x2 = constCoordAlt; y2 = axisEnd;
        }
        String segName = parent.assemblyName() + "_SEG" + segIdx;
        return compileWallInternal(segName, x1, y1, x2, y2, minZ, maxZ,
            parent.storeyName(), registry, thickness,
            parent.cladding().material());
    }

    private static StoreySpec assembleStoreySpec(StoreyBuildContext ctx) {
        // Phase 3 Debug: Count total frames to verify deduplication
        int totalFrames = ctx.walls.stream().mapToInt(w -> w.frames().size()).sum();
        System.out.printf("[PHASE3] Storey %s: %d walls, %d total frames%n",
            ctx.storey.name(), ctx.walls.size(), totalFrames);

        return new StoreySpec(
            ctx.storey.name(), ctx.storey.level(), ctx.baseZ, ctx.storey.height(),
            ctx.slab, ctx.walls, ctx.rooms, ctx.stairs, ctx.doors, ctx.windows, ctx.landings,
            ctx.sprinklers, ctx.lights, ctx.fixtures, ctx.columns, ctx.beams, ctx.diffusers, ctx.electricals, ctx.plumbing,
            ctx.elevators, ctx.lobbies, ctx.shafts,  // Phase 56B: CORE elements
            List.of(),  // Phase 100: alarms (populated by StandardsResolver)
            ctx.baySlabs  // Phase 122F: grid-bay structural slabs
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

    // Phase 119B: Overload with custom wall thickness AND cladding material name
    static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry,
                                                 double wallThickness,
                                                 String claddingMaterial) {
        return compileWallInternal(side, x1, y1, x2, y2, minZ, maxZ, storeyName, registry,
            wallThickness, claddingMaterial);
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

    // Phase 119: Overload with custom thickness AND cladding material
    static WallAssemblySpec compilePerimeterWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry,
                                                 double wallThickness,
                                                 String claddingMaterial) {
        return compileWallInternal(side, x1, y1, x2, y2, minZ, maxZ, storeyName, registry,
            wallThickness, claddingMaterial);
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

        // Cladding — centered on wall centerline
        CladdingSpec cladding = new CladdingSpec(
            claddingMaterial,
            x1 - nx / 2, y1 - ny / 2, minZ,
            x2 + nx / 2, y2 + ny / 2, maxZ
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

    /** Phase 122B/J: Resolve door family for interior adjacency connection.
     *  Priority: ADJACENT role > interior-placement ENTRY (smallest wins) > any ENTRY from room1.
     *  When both rooms have interior doors, pick the narrower one (bathroom < kitchen). */
    private static OpeningBomAD.OpeningFamily resolveAdjacencyDoor(
            RoomDef room1, RoomDef room2, String profile) {
        // Pass 1: ADJACENT role from either room
        for (RoomDef r : List.of(room1, room2)) {
            for (var d : OpeningBomAD.getDoorDefaults(r.type(), profile)) {
                if ("ADJACENT".equals(d.openingRole())) {
                    var f = OpeningBomAD.getFamily(d.familyId());
                    if (f != null) return f;
                }
            }
        }
        // Pass 2: Interior-placement ENTRY from both rooms — pick narrowest
        OpeningBomAD.OpeningFamily narrowest = null;
        for (RoomDef r : List.of(room1, room2)) {
            for (var d : OpeningBomAD.getDoorDefaults(r.type(), profile)) {
                if ("interior".equals(d.placementWall())) {
                    var f = OpeningBomAD.getFamily(d.familyId());
                    if (f != null && (narrowest == null || f.defaultWidthMm() < narrowest.defaultWidthMm())) {
                        narrowest = f;
                    }
                }
            }
        }
        if (narrowest != null) return narrowest;
        // Pass 3: Any door default from room1 (original behavior)
        var defs = OpeningBomAD.getDoorDefaults(room1.type(), profile);
        if (!defs.isEmpty()) return OpeningBomAD.getFamily(defs.get(0).familyId());
        return null;
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
