package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.contract.*;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.solver.SpaceSolver;
import com.bim.compiler.solver.SpaceSolver.*;
import com.bim.compiler.system.MEPSystem;
import com.bim.compiler.util.OutlierLogger;
import com.bim.compiler.validation.building.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 46: Multi-unit building compilation.
 * Extracted from BuildingCompiler to reduce monolith size.
 *
 * Handles:
 * - Cross-unit dependency graph and topological sorting
 * - Party wall constraint resolution
 * - Joint multi-unit layout solving
 * - Per-unit compilation with cross-unit position sharing
 * - Storey merging and wall deduplication
 */
class MultiUnitCompiler {
    static BuildingSpec compile(BuildingDefinition def, Path outputDir) {
        System.out.println("[MULTI-UNIT] Compiling multi-unit building: " + def.name());
        System.out.println("[MULTI-UNIT] Units: " + def.units().size());

        // Phase 48A: Detect layout type from unit level occupancy
        LayoutType buildingLayoutType = detectLayoutTypeFromUnits(def);
        System.out.println("[MULTI-UNIT] Building layout: " + buildingLayoutType);

        // 1. Build cross-unit dependency graph
        Map<String, Set<String>> unitDependencies = buildUnitDependencies(def);

        // 1b. Phase 47A: Extract party wall constraints (only for SIDE_BY_SIDE)
        List<PartyWallConstraint> partyConstraints = new ArrayList<>();
        if (buildingLayoutType == LayoutType.SIDE_BY_SIDE) {
            partyConstraints = extractPartyConstraints(def);
        } else if (buildingLayoutType == LayoutType.STACKED) {
            System.out.println("[MULTI-UNIT] STACKED layout: separating floor instead of party walls");
        }

        // 2. Topologically sort units
        List<String> compilationOrder = topologicalSortUnits(unitDependencies, def.units());
        System.out.println("[MULTI-UNIT] Compilation order: " + compilationOrder);

        // 2b. Phase 47A.2: Joint solve if party constraints exist
        // Solve all units together so adjacent_unit: constraints work
        Map<String, Map<String, GridPosition>> jointSolvedPositions = new HashMap<>();
        if (!partyConstraints.isEmpty()) {
            jointSolvedPositions = solveMultiUnitLayout(def, partyConstraints);
        }

        // 3. Compile each unit in order
        Map<String, List<StoreySpec>> unitStoreySpecs = new HashMap<>();
        Map<String, Map<String, double[]>> unitRoomPositions = new HashMap<>();  // For cross-unit refs

        // Inject joint-solved positions into unitRoomPositions for cross-unit refs
        for (var entry : jointSolvedPositions.entrySet()) {
            String unitName = entry.getKey();
            Map<String, double[]> positions = new HashMap<>();
            for (var posEntry : entry.getValue().entrySet()) {
                GridPosition gp = posEntry.getValue();
                positions.put(posEntry.getKey(), new double[]{gp.x(), gp.y()});
            }
            unitRoomPositions.put(unitName, positions);
        }

        for (String unitName : compilationOrder) {
            UnitDefinition unit = def.getUnit(unitName);
            System.out.println("[MULTI-UNIT] Compiling unit: " + unitName);

            // Create a temporary BuildingDefinition for this unit
            BuildingDefinition unitDef = createUnitBuildingDefinition(unit, def, unitRoomPositions);

            // Phase 48A: Collect cross-unit positions for above:/below: constraints
            Map<String, double[]> crossUnitPositions = new HashMap<>();
            for (var entry : unitRoomPositions.entrySet()) {
                if (!entry.getKey().equals(unitName)) {
                    crossUnitPositions.putAll(entry.getValue());
                }
            }

            // Resolve constraints with cross-unit positions available
            BuildingDefinition resolvedUnitDef = BuildingCompiler.resolveConstraints(unitDef, crossUnitPositions);

            // Compile storeys
            List<StoreySpec> storeySpecs = new ArrayList<>();
            double currentZ = 0.0;

            // Phase 2 Contract Architecture: Create registry for this unit
            SharedElementRegistry unitRegistry = new SharedElementRegistry();

            for (int i = 0; i < resolvedUnitDef.storeys().size(); i++) {
                StoreyDef storey = resolvedUnitDef.storeys().get(i);
                boolean isGround = (storey.level() == 0);
                boolean isTop = (i == resolvedUnitDef.storeys().size() - 1);

                StoreySpec spec = StoreyCompiler.compileStorey(storey, currentZ, isGround, isTop, resolvedUnitDef, unitRegistry);

                // Tag rooms with unit ID for later processing
                spec = tagStoreyWithUnit(spec, unitName);
                storeySpecs.add(spec);

                currentZ += storey.height();
            }

            unitStoreySpecs.put(unitName, storeySpecs);

            // Extract room positions for cross-unit references
            Map<String, double[]> positions = extractRoomPositions(storeySpecs);
            unitRoomPositions.put(unitName, positions);
        }

        // 4. Compile shared spaces (if any)
        List<StoreySpec> sharedStoreySpecs = new ArrayList<>();
        if (def.shared() != null && !def.shared().isEmpty()) {
            System.out.println("[MULTI-UNIT] Compiling shared spaces");
            BuildingDefinition sharedDef = createSharedBuildingDefinition(def.shared(), def);
            BuildingDefinition resolvedSharedDef = BuildingCompiler.resolveConstraints(sharedDef);

            // Phase 2 Contract Architecture: Create registry for shared spaces
            SharedElementRegistry sharedRegistry = new SharedElementRegistry();

            double currentZ = 0.0;
            for (int i = 0; i < resolvedSharedDef.storeys().size(); i++) {
                StoreyDef storey = resolvedSharedDef.storeys().get(i);
                boolean isGround = (storey.level() == 0);
                boolean isTop = (i == resolvedSharedDef.storeys().size() - 1);

                StoreySpec spec = StoreyCompiler.compileStorey(storey, currentZ, isGround, isTop, resolvedSharedDef, sharedRegistry);
                spec = tagStoreyWithUnit(spec, "_SHARED");
                sharedStoreySpecs.add(spec);

                currentZ += storey.height();
            }
        }

        // 5. Merge all storeys by level
        List<StoreySpec> mergedStoreys = mergeStoreysByLevel(unitStoreySpecs, sharedStoreySpecs);

        // 6. Compile roof
        RoofSpec roofSpec = null;
        if (def.roof() != null && !mergedStoreys.isEmpty()) {
            double topZ = mergedStoreys.stream()
                .mapToDouble(s -> s.baseZ() + s.height())
                .max()
                .orElse(0);
            roofSpec = BuildingCompiler.compileRoofFromSpecs(def.roof(), topZ, mergedStoreys);
            OutlierLogger.incrementTotalElements();
        }

        // 7. Count elements for outlier tracking
        mergedStoreys.forEach(BuildingCompiler::countStoreyElements);

        if (outputDir != null) {
            OutlierLogger.summarize(outputDir.resolve("outliers.log"));
        }
        OutlierLogger.printSummary();

        // 8. Build MEP systems
        List<MEPSystem> mepSystems = new ArrayList<>();
        try {
            mepSystems = BuildingCompiler.buildMEPSystems(mergedStoreys);
            if (!mepSystems.isEmpty()) {
                System.out.printf("[MEP] Built %d system graph(s)%n", mepSystems.size());
            }
        } catch (Exception e) {
            System.out.println("[MEP] System graph error: " + e.getMessage());
        }

        BuildingSpec spec = new BuildingSpec(def.name(), mergedStoreys, roofSpec, mepSystems, def.constructionSystem());

        // 9. Validate (non-fatal — caller handles validation reporting)
        ValidatorChain.ValidationReport validationReport = BuildingCompiler.validate(spec, def);
        if (validationReport.hasCriticalFailures() || validationReport.hasWarnings()) {
            System.out.println(validationReport);
        }

        System.out.println("[MULTI-UNIT] Compilation complete: " + mergedStoreys.size() + " storeys");
        return spec;
    }

    /**
     * Phase 46: Build cross-unit dependency graph.
     * Unit B depends on Unit A if any room in B has above:/below: referencing a room in A.
     */
    private static Map<String, Set<String>> buildUnitDependencies(BuildingDefinition def) {
        Map<String, Set<String>> deps = new HashMap<>();

        // Initialize empty dependency sets
        for (UnitDefinition unit : def.units()) {
            deps.put(unit.name(), new HashSet<>());
        }

        // Find cross-unit references
        for (UnitDefinition unit : def.units()) {
            for (StoreyDef storey : unit.storeys()) {
                for (RoomDef room : storey.rooms()) {
                    // Check above: constraint
                    if (room.above() != null) {
                        String referencedUnit = def.findUnitContainingRoom(room.above());
                        if (referencedUnit != null && !referencedUnit.equals(unit.name())) {
                            deps.get(unit.name()).add(referencedUnit);
                        }
                    }
                    // Check below: constraint
                    if (room.below() != null) {
                        String referencedUnit = def.findUnitContainingRoom(room.below());
                        if (referencedUnit != null && !referencedUnit.equals(unit.name())) {
                            // below: means this unit provides position, so target depends on us
                            deps.get(referencedUnit).add(unit.name());
                        }
                    }
                    // Check alignsWith: constraint
                    if (room.alignsWith() != null) {
                        String referencedUnit = def.findUnitContainingRoom(room.alignsWith());
                        if (referencedUnit != null && !referencedUnit.equals(unit.name())) {
                            deps.get(unit.name()).add(referencedUnit);
                        }
                    }
                }
            }
        }

        return deps;
    }

    /**
     * Phase 46: Topologically sort units by dependencies.
     * Returns units in compilation order (dependencies first).
     */
    private static List<String> topologicalSortUnits(Map<String, Set<String>> deps,
                                                      List<UnitDefinition> units) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (UnitDefinition unit : units) {
            if (!visited.contains(unit.name())) {
                topologicalVisit(unit.name(), deps, visited, visiting, result);
            }
        }

        return result;
    }

    private static void topologicalVisit(String unitName, Map<String, Set<String>> deps,
                                         Set<String> visited, Set<String> visiting,
                                         List<String> result) {
        if (visiting.contains(unitName)) {
            throw new RuntimeException("Circular dependency detected involving unit: " + unitName);
        }
        if (visited.contains(unitName)) {
            return;
        }

        visiting.add(unitName);

        // Visit dependencies first
        Set<String> unitDeps = deps.get(unitName);
        if (unitDeps != null) {
            for (String dep : unitDeps) {
                topologicalVisit(dep, deps, visited, visiting, result);
            }
        }

        visiting.remove(unitName);
        visited.add(unitName);
        result.add(unitName);
    }

    /**
     * Phase 47A: Extract party wall constraints from all units.
     * Scans rooms for adjacent_unit: declarations and builds resolved constraint list.
     *
     * @param def Building definition with parsed units
     * @return List of party wall constraints with unit assignments resolved
     */
    public static List<PartyWallConstraint> extractPartyConstraints(BuildingDefinition def) {
        List<PartyWallConstraint> constraints = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();  // Deduplicate A→B and B→A

        for (UnitDefinition unit : def.units()) {
            for (StoreyDef storey : unit.storeys()) {
                for (RoomDef room : storey.rooms()) {
                    if (room.hasAdjacentUnit()) {
                        String otherRoom = room.adjacentUnit();
                        String otherUnit = def.findUnitContainingRoom(otherRoom);

                        if (otherUnit == null) {
                            System.out.printf("[PARTY-WALL] Warning: adjacent_unit:%s in room %s " +
                                "references unknown room%n", otherRoom, room.name());
                            continue;
                        }

                        if (otherUnit.equals(unit.name())) {
                            System.out.printf("[PARTY-WALL] Warning: adjacent_unit:%s in room %s " +
                                "references room in same unit (use adjacent: instead)%n",
                                otherRoom, room.name());
                            continue;
                        }

                        PartyWallConstraint constraint = new PartyWallConstraint(
                            room.name(),
                            unit.name(),
                            otherRoom,
                            otherUnit,
                            storey.level()
                        );

                        // Deduplicate bidirectional constraints
                        String key = constraint.canonicalKey();
                        if (!seenKeys.contains(key)) {
                            seenKeys.add(key);
                            constraints.add(constraint);
                            System.out.printf("[PARTY-WALL] Constraint: %s%n", constraint);
                        }
                    }
                }
            }
        }

        if (!constraints.isEmpty()) {
            System.out.printf("[PARTY-WALL] Extracted %d party wall constraint(s)%n", constraints.size());
        }

        return constraints;
    }

    /**
     * Phase 47A.2: Joint solve for all units with party wall constraints.
     * Flattens rooms from all units at each level and solves jointly so
     * adjacent_unit: constraints can be enforced.
     *
     * @param def Building definition with units
     * @param partyConstraints Party wall constraints to enforce
     * @return Map of unit name -> (room name -> GridPosition)
     */
    private static Map<String, Map<String, GridPosition>> solveMultiUnitLayout(
            BuildingDefinition def,
            List<PartyWallConstraint> partyConstraints) {

        System.out.println("[PARTY-WALL] Joint multi-unit solve starting");

        Map<String, Map<String, GridPosition>> result = new HashMap<>();

        // Initialize result maps for each unit
        for (UnitDefinition unit : def.units()) {
            result.put(unit.name(), new HashMap<>());
        }

        // Group party constraints by level
        Map<Integer, List<PartyWallConstraint>> constraintsByLevel = new HashMap<>();
        for (PartyWallConstraint pc : partyConstraints) {
            constraintsByLevel.computeIfAbsent(pc.storeyLevel(), k -> new ArrayList<>()).add(pc);
        }

        // Solve each level that has party constraints
        for (var levelEntry : constraintsByLevel.entrySet()) {
            int level = levelEntry.getKey();
            List<PartyWallConstraint> levelConstraints = levelEntry.getValue();

            System.out.printf("[PARTY-WALL] Solving level %d with %d party constraint(s)%n",
                level, levelConstraints.size());

            // Phase 47D: Detect layout type from party wall edges
            LayoutType layoutType = detectLayoutType(levelConstraints);
            System.out.printf("[PARTY-WALL] Layout type: %s%n", layoutType);

            // Calculate unit zone bounds for SIDE_BY_SIDE or STACKED layouts
            List<UnitDefinition> units = def.units();
            int maxWidth = 30;  // Generous grid for multi-unit
            int maxDepth = 30;
            Map<String, int[]> unitZoneBounds = new HashMap<>();  // unit -> [minX, maxX, minY, maxY]

            if (layoutType == LayoutType.SIDE_BY_SIDE) {
                // Partition X axis by number of units
                int unitWidth = maxWidth / units.size();
                for (int i = 0; i < units.size(); i++) {
                    int minX = i * unitWidth;
                    int maxX = (i + 1) * unitWidth;
                    unitZoneBounds.put(units.get(i).name(), new int[]{minX, maxX, 0, maxDepth});
                    System.out.printf("[PARTY-WALL] Unit %s zone: X=[%d,%d]%n",
                        units.get(i).name(), minX, maxX);
                }
            } else if (layoutType == LayoutType.STACKED) {
                // Partition Y axis by number of units
                int unitDepth = maxDepth / units.size();
                for (int i = 0; i < units.size(); i++) {
                    int minY = i * unitDepth;
                    int maxY = (i + 1) * unitDepth;
                    unitZoneBounds.put(units.get(i).name(), new int[]{0, maxWidth, minY, maxY});
                    System.out.printf("[PARTY-WALL] Unit %s zone: Y=[%d,%d]%n",
                        units.get(i).name(), minY, maxY);
                }
            } else {
                // COMPLEX - log warning and proceed without zone constraints
                System.out.println("[PARTY-WALL] WARNING: Complex layout detected - zone partitioning not applied");
                System.out.println("[PARTY-WALL] This may result in JAGGED topology");
            }

            // Collect all rooms at this level from all units
            List<RoomConstraint> solverConstraints = new ArrayList<>();
            Map<String, String> roomToUnit = new HashMap<>();  // Track which unit owns each room

            // Phase 111: First pass — collect all room names at this level for adjacency filtering
            Set<String> allRoomsAtLevel = new HashSet<>();
            for (UnitDefinition unit : def.units()) {
                StoreyDef storey = unit.getStoreyAtLevel(level);
                if (storey == null) continue;
                for (RoomDef room : storey.rooms()) allRoomsAtLevel.add(room.name());
            }

            for (UnitDefinition unit : def.units()) {
                StoreyDef storey = unit.getStoreyAtLevel(level);
                if (storey == null) continue;

                int[] zoneBounds = unitZoneBounds.get(unit.name());

                for (RoomDef room : storey.rooms()) {
                    roomToUnit.put(room.name(), unit.name());

                    // Build adjacency list: intra-unit + cross-unit
                    List<String> adjacentTo = new ArrayList<>(room.adjacentTo());

                    // Add cross-unit adjacency from party constraints
                    for (PartyWallConstraint pc : levelConstraints) {
                        if (pc.thisRoom().equals(room.name())) {
                            adjacentTo.add(pc.otherRoom());
                        } else if (pc.otherRoom().equals(room.name())) {
                            adjacentTo.add(pc.thisRoom());
                        }
                    }

                    // Phase 111: Filter out references to rooms not in the joint solve
                    // (e.g., SHARED landings referenced via adjacent: from unit rooms)
                    adjacentTo.removeIf(name -> !allRoomsAtLevel.contains(name));

                    // Phase 47D: Apply zone bounds if available
                    RoomConstraint rc;
                    if (zoneBounds != null) {
                        rc = new RoomConstraint(
                            room.name(),
                            (int) Math.ceil(room.width()),
                            (int) Math.ceil(room.depth()),
                            adjacentTo,
                            room.notAdjacentTo(),
                            room.exteriorWall(),
                            zoneBounds[0], zoneBounds[1],  // minX, maxX
                            zoneBounds[2], zoneBounds[3]   // minY, maxY
                        );
                    } else {
                        rc = new RoomConstraint(
                            room.name(),
                            (int) Math.ceil(room.width()),
                            (int) Math.ceil(room.depth()),
                            adjacentTo,
                            room.notAdjacentTo(),
                            room.exteriorWall()
                        );
                    }
                    solverConstraints.add(rc);
                }
            }

            if (solverConstraints.isEmpty()) {
                continue;
            }

            // Solve jointly using relaxation (exterior constraints may conflict with party walls)
            SpaceSolver solver = new SpaceSolver();

            // Try strict solve first, fall back to relaxation
            SolvedLayout layout = solver.solveWithRelaxation(solverConstraints, maxWidth, maxDepth);

            if (!layout.feasible()) {
                System.out.printf("[PARTY-WALL] Warning: Joint solve failed for level %d: %s%n",
                    level, layout.failureReason());
                // Fall back to per-unit solving
                continue;
            }

            System.out.printf("[PARTY-WALL] Joint solve succeeded in %dms%n", layout.solveTimeMs());
            SpaceSolver.printLayout(layout, solverConstraints);

            // Partition results back to units
            for (var posEntry : layout.positions().entrySet()) {
                String roomName = posEntry.getKey();
                GridPosition pos = posEntry.getValue();
                String unitName = roomToUnit.get(roomName);

                if (unitName != null) {
                    result.get(unitName).put(roomName, pos);
                    System.out.printf("[PARTY-WALL] %s -> %s (unit %s)%n",
                        roomName, SpaceSolver.toGridRef(pos), unitName);
                }
            }

            // Verify party constraints are satisfied
            for (PartyWallConstraint pc : levelConstraints) {
                GridPosition posA = layout.positions().get(pc.thisRoom());
                GridPosition posB = layout.positions().get(pc.otherRoom());
                if (posA != null && posB != null) {
                    // Find room dimensions
                    RoomConstraint rcA = null, rcB = null;
                    for (RoomConstraint rc : solverConstraints) {
                        if (rc.name().equals(pc.thisRoom())) rcA = rc;
                        if (rc.name().equals(pc.otherRoom())) rcB = rc;
                    }
                    if (rcA != null && rcB != null) {
                        boolean adjacent = areRoomsAdjacent(posA, rcA, posB, rcB);
                        System.out.printf("[PARTY-WALL] Verify %s <-> %s: %s%n",
                            pc.thisRoom(), pc.otherRoom(),
                            adjacent ? "ADJACENT ✓" : "NOT ADJACENT (constraint violation!)");
                    }
                }
            }

            // Phase 47A.3: Compute unit envelopes and determine true exterior edges
            Map<String, int[]> unitEnvelopes = computeUnitEnvelopes(def, result, solverConstraints);
            int[] buildingEnvelope = computeBuildingEnvelope(unitEnvelopes);

            System.out.printf("[PARTY-WALL] Building envelope: [%d,%d] to [%d,%d]%n",
                buildingEnvelope[0], buildingEnvelope[1], buildingEnvelope[2], buildingEnvelope[3]);

            // Determine which unit edges are true exterior
            for (var unitEntry : unitEnvelopes.entrySet()) {
                String unitName = unitEntry.getKey();
                int[] env = unitEntry.getValue();
                Set<String> exteriorEdges = new HashSet<>();

                if (env[0] == buildingEnvelope[0]) exteriorEdges.add("west");
                if (env[1] == buildingEnvelope[1]) exteriorEdges.add("south");
                if (env[2] == buildingEnvelope[2]) exteriorEdges.add("east");
                if (env[3] == buildingEnvelope[3]) exteriorEdges.add("north");

                System.out.printf("[PARTY-WALL] Unit %s envelope: [%d,%d] to [%d,%d], exterior: %s%n",
                    unitName, env[0], env[1], env[2], env[3], exteriorEdges);

                // Store exterior edge info for this unit (used in room compilation)
                unitExteriorEdges.put(unitName, exteriorEdges);
            }
        }

        return result;
    }

    // Phase 47A.3: Track which edges of each unit are true exterior
    private static Map<String, Set<String>> unitExteriorEdges = new HashMap<>();

    /**
     * Phase 47A.3: Get exterior edges for a unit (computed during joint solve).
     */
    public static Set<String> getUnitExteriorEdges(String unitName) {
        return unitExteriorEdges.getOrDefault(unitName, Set.of("north", "south", "east", "west"));
    }

    /**
     * Phase 47A.3: Compute bounding box for each unit from solved room positions.
     * Returns map of unit name -> [minX, minY, maxX, maxY]
     */
    private static Map<String, int[]> computeUnitEnvelopes(
            BuildingDefinition def,
            Map<String, Map<String, GridPosition>> unitPositions,
            List<RoomConstraint> constraints) {

        Map<String, int[]> envelopes = new HashMap<>();
        Map<String, RoomConstraint> constraintMap = new HashMap<>();
        for (RoomConstraint rc : constraints) {
            constraintMap.put(rc.name(), rc);
        }

        for (UnitDefinition unit : def.units()) {
            Map<String, GridPosition> positions = unitPositions.get(unit.name());
            if (positions == null || positions.isEmpty()) continue;

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

            for (var entry : positions.entrySet()) {
                String roomName = entry.getKey();
                GridPosition pos = entry.getValue();
                RoomConstraint rc = constraintMap.get(roomName);
                if (rc == null) continue;

                minX = Math.min(minX, pos.x());
                minY = Math.min(minY, pos.y());
                maxX = Math.max(maxX, pos.x() + rc.widthMeters());
                maxY = Math.max(maxY, pos.y() + rc.depthMeters());
            }

            if (minX != Integer.MAX_VALUE) {
                envelopes.put(unit.name(), new int[]{minX, minY, maxX, maxY});
            }
        }

        return envelopes;
    }

    /**
     * Phase 47A.3: Compute building envelope from all unit envelopes.
     * Returns [minX, minY, maxX, maxY]
     */
    private static int[] computeBuildingEnvelope(Map<String, int[]> unitEnvelopes) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (int[] env : unitEnvelopes.values()) {
            minX = Math.min(minX, env[0]);
            minY = Math.min(minY, env[1]);
            maxX = Math.max(maxX, env[2]);
            maxY = Math.max(maxY, env[3]);
        }

        return new int[]{minX, minY, maxX, maxY};
    }

    /**
     * Phase 48A: Detect layout type from unit level occupancy.
     * Analyzes which levels each unit occupies to determine arrangement.
     *
     * @param def Building definition with units
     * @return SIDE_BY_SIDE if units share same levels (adjacent horizontally),
     *         STACKED if units occupy disjoint levels (one above another),
     *         COMPLEX if partial overlap or cannot determine
     */
    private static LayoutType detectLayoutTypeFromUnits(BuildingDefinition def) {
        List<UnitDefinition> units = def.units();
        if (units.size() < 2) {
            return LayoutType.SIDE_BY_SIDE;  // Single unit, default
        }

        // Collect level sets for each unit
        Map<String, Set<Integer>> unitLevels = new HashMap<>();
        for (UnitDefinition unit : units) {
            Set<Integer> levels = new HashSet<>();
            for (StoreyDef storey : unit.storeys()) {
                levels.add(storey.level());
            }
            unitLevels.put(unit.name(), levels);
        }

        // Check all pairs of units for level intersection
        List<String> unitNames = new ArrayList<>(unitLevels.keySet());
        boolean anyIntersection = false;
        boolean allDisjoint = true;
        boolean anyPartialOverlap = false;

        for (int i = 0; i < unitNames.size(); i++) {
            for (int j = i + 1; j < unitNames.size(); j++) {
                Set<Integer> levelsA = unitLevels.get(unitNames.get(i));
                Set<Integer> levelsB = unitLevels.get(unitNames.get(j));

                // Calculate intersection
                Set<Integer> intersection = new HashSet<>(levelsA);
                intersection.retainAll(levelsB);

                if (!intersection.isEmpty()) {
                    anyIntersection = true;
                    allDisjoint = false;

                    // Check if it's partial overlap (not all levels shared)
                    if (!intersection.equals(levelsA) || !intersection.equals(levelsB)) {
                        anyPartialOverlap = true;
                    }
                }
            }
        }

        // Determine layout type
        if (allDisjoint) {
            System.out.println("[LAYOUT] Detected STACKED: units occupy disjoint levels");
            for (var entry : unitLevels.entrySet()) {
                System.out.printf("[LAYOUT]   Unit %s: levels %s%n", entry.getKey(), entry.getValue());
            }
            return LayoutType.STACKED;
        } else if (anyPartialOverlap) {
            System.out.println("[LAYOUT] Detected COMPLEX: units have partial level overlap");
            return LayoutType.COMPLEX;
        } else {
            System.out.println("[LAYOUT] Detected SIDE_BY_SIDE: units share same levels");
            return LayoutType.SIDE_BY_SIDE;
        }
    }

    /**
     * Phase 47D: Detect layout type from party wall constraints (per-level).
     * Falls back to SIDE_BY_SIDE if no directional information available.
     *
     * @param partyConstraints List of party wall constraints at a single level
     * @return SIDE_BY_SIDE (default for same-level constraints)
     */
    private static LayoutType detectLayoutType(List<PartyWallConstraint> partyConstraints) {
        if (partyConstraints.isEmpty()) {
            return LayoutType.SIDE_BY_SIDE;  // No constraints = use unit-level detection
        }
        // For same-level party constraints, layout is SIDE_BY_SIDE
        return LayoutType.SIDE_BY_SIDE;
    }

    /**
     * Check if two rooms are adjacent based on their solved positions and dimensions.
     */
    private static boolean areRoomsAdjacent(GridPosition posA, RoomConstraint rcA,
                                            GridPosition posB, RoomConstraint rcB) {
        // Check if bounding boxes share an edge
        int aMinX = posA.x(), aMaxX = posA.x() + rcA.widthMeters();
        int aMinY = posA.y(), aMaxY = posA.y() + rcA.depthMeters();
        int bMinX = posB.x(), bMaxX = posB.x() + rcB.widthMeters();
        int bMinY = posB.y(), bMaxY = posB.y() + rcB.depthMeters();

        // Shared vertical edge (east/west adjacency)
        boolean verticalEdge = (aMaxX == bMinX || bMaxX == aMinX) &&
                               !(aMaxY <= bMinY || bMaxY <= aMinY);

        // Shared horizontal edge (north/south adjacency)
        boolean horizontalEdge = (aMaxY == bMinY || bMaxY == aMinY) &&
                                 !(aMaxX <= bMinX || bMaxX <= aMinX);

        return verticalEdge || horizontalEdge;
    }

    /**
     * Phase 46: Create a temporary BuildingDefinition for a single unit.
     * Phase 47A.2: Injects pre-solved positions from joint multi-unit solve.
     */
    private static BuildingDefinition createUnitBuildingDefinition(
            UnitDefinition unit,
            BuildingDefinition parent,
            Map<String, Map<String, double[]>> unitRoomPositions) {

        // Check if this unit has pre-solved positions from joint solve
        Map<String, double[]> preSolvedPositions = unitRoomPositions.get(unit.name());

        if (preSolvedPositions == null || preSolvedPositions.isEmpty()) {
            // No pre-solved positions, use original storeys
            return new BuildingDefinition(
                parent.name() + "_" + unit.name(),
                unit.storeys(),
                parent.roof(),
                parent.grid(),
                parent.envelope()
            );
        }

        // Phase 47A.3: Get this unit's true exterior edges (computed during joint solve)
        Set<String> unitExterior = getUnitExteriorEdges(unit.name());

        // Inject pre-solved positions into rooms, filtering exterior walls
        List<StoreyDef> updatedStoreys = new ArrayList<>();
        for (StoreyDef storey : unit.storeys()) {
            List<RoomDef> updatedRooms = new ArrayList<>();
            for (RoomDef room : storey.rooms()) {
                double[] pos = preSolvedPositions.get(room.name());
                if (pos != null) {
                    // Convert grid position to grid reference (e.g., "A1")
                    String gridRef = SpaceSolver.toGridRef(new GridPosition((int) pos[0], (int) pos[1]));

                    // Phase 47A.3: Filter exterior walls to only those on unit's true exterior
                    RoomDef updated = room.withPositionAndExterior(gridRef, unitExterior);

                    // Log if exterior was filtered
                    String origExt = room.exteriorWall();
                    String newExt = updated.exteriorWall();
                    if (origExt != null && newExt == null) {
                        System.out.printf("[PARTY-WALL] %s: exterior:%s filtered (not on unit exterior)%n",
                            room.name(), origExt);
                    }
                    System.out.printf("[PARTY-WALL] Injecting position for %s: %s, exterior=%s%n",
                        room.name(), gridRef, newExt);
                    updatedRooms.add(updated);
                } else {
                    updatedRooms.add(room);
                }
            }
            updatedStoreys.add(new StoreyDef(storey.name(), storey.level(), storey.height(),
                updatedRooms, storey.stairs(), storey.landings(),
                storey.elevators(), storey.lobbies(), storey.shafts(),
                storey.floorBom()));
        }

        return new BuildingDefinition(
            parent.name() + "_" + unit.name(),
            updatedStoreys,
            parent.roof(),
            parent.grid(),
            parent.envelope()
        );
    }

    /**
     * Phase 46: Create a temporary BuildingDefinition for shared spaces.
     */
    private static BuildingDefinition createSharedBuildingDefinition(
            SharedDefinition shared,
            BuildingDefinition parent) {

        return new BuildingDefinition(
            parent.name() + "_SHARED",
            shared.storeys(),
            null,  // No roof for shared
            parent.grid(),
            parent.envelope()
        );
    }

    /**
     * Phase 46C: Tag all rooms in a storey with unit ID.
     */
    private static StoreySpec tagStoreyWithUnit(StoreySpec spec, String unitName) {
        // Tag each room with unit ID
        List<RoomSpec> taggedRooms = new ArrayList<>();
        for (RoomSpec room : spec.rooms()) {
            taggedRooms.add(room.withUnitId(unitName));
        }

        // Phase 111: Prefix beam/column IDs with unit name to prevent GUID collisions in merged storeys
        List<BeamSpec> taggedBeams = new ArrayList<>();
        for (BeamSpec beam : spec.beams()) {
            taggedBeams.add(new BeamSpec(
                unitName + "_" + beam.id(), beam.beamType(),
                beam.x(), beam.y(), beam.z(),
                beam.length(), beam.width(), beam.height(),
                beam.rotation(), beam.geometryHash(), beam.storeyName()));
        }

        List<ColumnSpec> taggedColumns = new ArrayList<>();
        for (ColumnSpec col : spec.columns()) {
            String taggedContinuityId = col.continuityId() != null
                ? unitName + "_" + col.continuityId() : null;
            taggedColumns.add(new ColumnSpec(
                unitName + "_" + col.id(), col.columnType(),
                col.x(), col.y(), col.z(),
                col.height(), col.width(), col.depth(),
                col.geometryHash(), taggedContinuityId, col.storeyName()));
        }

        // Return new StoreySpec with tagged rooms, beams, and columns
        return new StoreySpec(
            spec.name(), spec.level(), spec.baseZ(), spec.height(),
            spec.slab(), spec.walls(), taggedRooms, spec.stairs(),
            spec.doors(), spec.windows(), spec.landings(),
            spec.sprinklers(), spec.lights(), spec.fixtures(),
            taggedColumns, taggedBeams, spec.diffusers(),
            spec.electricals(), spec.plumbing(),
            spec.elevators(), spec.lobbies(), spec.shafts(), spec.alarms()
        );
    }

    /**
     * Phase 46: Extract room positions from compiled storeys.
     * Phase 48A: Returns corner coordinates (minX, minY) for solver compatibility,
     * not center coordinates. The solver uses corner positions for placement.
     */
    private static Map<String, double[]> extractRoomPositions(List<StoreySpec> storeySpecs) {
        Map<String, double[]> positions = new HashMap<>();

        for (StoreySpec storey : storeySpecs) {
            for (RoomSpec room : storey.rooms()) {
                // Use corner coordinates (minX, minY) for solver compatibility
                // The solver places rooms at corner positions, not centers
                positions.put(room.name(), new double[]{room.minX(), room.minY(), storey.baseZ()});
            }
        }

        return positions;
    }

    /**
     * Phase 46: Merge storeys from all units by level.
     * Rooms at the same level are combined into a single StoreySpec.
     */
    private static List<StoreySpec> mergeStoreysByLevel(
            Map<String, List<StoreySpec>> unitStoreySpecs,
            List<StoreySpec> sharedStoreySpecs) {

        // Group all storeys by level
        Map<Integer, List<StoreySpec>> storeysByLevel = new HashMap<>();

        for (List<StoreySpec> unitStoreys : unitStoreySpecs.values()) {
            for (StoreySpec storey : unitStoreys) {
                storeysByLevel.computeIfAbsent(storey.level(), k -> new ArrayList<>()).add(storey);
            }
        }

        for (StoreySpec storey : sharedStoreySpecs) {
            storeysByLevel.computeIfAbsent(storey.level(), k -> new ArrayList<>()).add(storey);
        }

        // Merge storeys at each level
        List<StoreySpec> merged = new ArrayList<>();
        List<Integer> levels = new ArrayList<>(storeysByLevel.keySet());
        levels.sort(Integer::compareTo);

        for (int level : levels) {
            List<StoreySpec> storeysAtLevel = storeysByLevel.get(level);
            if (storeysAtLevel.size() == 1) {
                merged.add(storeysAtLevel.get(0));
            } else {
                // Merge multiple storeys at same level
                StoreySpec mergedStorey = mergeStoreysAtLevel(storeysAtLevel);
                merged.add(mergedStorey);
            }
        }

        // Phase 48B: Detect and upgrade separating floors between units
        merged = detectAndUpgradeSeparatingFloors(merged, unitStoreySpecs);

        return merged;
    }

    /**
     * Phase 48B: Detect slabs that separate different dwelling units and upgrade them.
     * For STACKED layouts, the floor slab between levels occupied by different units
     * becomes a separating floor with upgraded fire and acoustic ratings.
     *
     * Requirements per UBBL:
     * - Fire rating: FRL 90/90/90 (1.5 hours)
     * - Thickness: minimum 200mm
     * - Acoustic: STC 50, IIC 50
     */
    private static List<StoreySpec> detectAndUpgradeSeparatingFloors(
            List<StoreySpec> merged,
            Map<String, List<StoreySpec>> unitStoreySpecs) {

        // Build map: level -> set of unit IDs that have rooms at that level
        Map<Integer, Set<String>> unitsByLevel = new HashMap<>();
        for (var entry : unitStoreySpecs.entrySet()) {
            String unitId = entry.getKey();
            for (StoreySpec storey : entry.getValue()) {
                unitsByLevel.computeIfAbsent(storey.level(), k -> new HashSet<>()).add(unitId);
            }
        }

        // For each level > 0, check if it's a separating floor
        List<StoreySpec> result = new ArrayList<>();
        Set<Integer> separatingLevels = new HashSet<>();

        for (int i = 0; i < merged.size(); i++) {
            StoreySpec storey = merged.get(i);
            int level = storey.level();

            // Check if this level's slab separates different units
            boolean isSeparating = false;
            if (level > 0) {
                Set<String> unitsAtThisLevel = unitsByLevel.getOrDefault(level, Set.of());
                Set<String> unitsBelow = unitsByLevel.getOrDefault(level - 1, Set.of());

                // Separating floor if units at this level differ from units below
                // (neither is a subset of the other or they're disjoint)
                if (!unitsAtThisLevel.isEmpty() && !unitsBelow.isEmpty()) {
                    Set<String> intersection = new HashSet<>(unitsAtThisLevel);
                    intersection.retainAll(unitsBelow);

                    // Different units if intersection is empty (disjoint)
                    // or if there are units unique to either level
                    if (intersection.isEmpty() ||
                        !unitsAtThisLevel.equals(unitsBelow)) {
                        // Check for truly different units (not just same unit spanning floors)
                        Set<String> uniqueAbove = new HashSet<>(unitsAtThisLevel);
                        uniqueAbove.removeAll(unitsBelow);
                        Set<String> uniqueBelow = new HashSet<>(unitsBelow);
                        uniqueBelow.removeAll(unitsAtThisLevel);

                        if (!uniqueAbove.isEmpty() || !uniqueBelow.isEmpty()) {
                            isSeparating = true;
                            separatingLevels.add(level);
                        }
                    }
                }
            }

            if (isSeparating && storey.slab() != null) {
                // Upgrade slab to separating floor
                SlabSpec upgradedSlab = storey.slab().asSeparatingFloor(0.20);
                System.out.printf("[SEPARATING-FLOOR] Level %d: upgraded to FRL 90/90/90, " +
                    "thickness=%.0fmm, STC=%d, IIC=%d%n",
                    level,
                    upgradedSlab.thickness() * 1000,
                    upgradedSlab.acousticSTC(),
                    upgradedSlab.acousticIIC());

                result.add(storey.withSlab(upgradedSlab));
            } else {
                result.add(storey);
            }
        }

        if (!separatingLevels.isEmpty()) {
            System.out.printf("[SEPARATING-FLOOR] Detected %d separating floor(s) at level(s): %s%n",
                separatingLevels.size(), separatingLevels);
        }

        return result;
    }

    /**
     * Phase 46: Merge multiple StoreySpecs at the same level into one.
     * Includes party wall detection and deduplication.
     */
    private static StoreySpec mergeStoreysAtLevel(List<StoreySpec> storeys) {
        if (storeys.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty storey list");
        }

        StoreySpec first = storeys.get(0);
        String name = first.name();
        int level = first.level();
        double baseZ = first.baseZ();
        double height = first.height();

        // Merge all elements
        List<RoomSpec> rooms = new ArrayList<>();
        List<WallAssemblySpec> walls = new ArrayList<>();
        List<DoorSpec> doors = new ArrayList<>();
        List<WindowSpec> windows = new ArrayList<>();
        List<StairSpec> stairs = new ArrayList<>();
        List<LandingSpec> landings = new ArrayList<>();
        List<FixtureSpec> fixtures = new ArrayList<>();
        List<SprinklerSpec> sprinklers = new ArrayList<>();
        List<LightSpec> lights = new ArrayList<>();
        List<DiffuserSpec> diffusers = new ArrayList<>();
        List<ElectricalSpec> electricals = new ArrayList<>();
        List<PlumbingSpec> plumbing = new ArrayList<>();
        List<ColumnSpec> columns = new ArrayList<>();
        List<BeamSpec> beams = new ArrayList<>();
        SlabSpec slab = first.slab();

        for (StoreySpec storey : storeys) {
            rooms.addAll(storey.rooms());
            walls.addAll(storey.walls());
            doors.addAll(storey.doors());
            windows.addAll(storey.windows());
            stairs.addAll(storey.stairs());
            landings.addAll(storey.landings());
            fixtures.addAll(storey.fixtures());
            sprinklers.addAll(storey.sprinklers());
            lights.addAll(storey.lights());
            diffusers.addAll(storey.diffusers());
            electricals.addAll(storey.electricals());
            plumbing.addAll(storey.plumbing());
            columns.addAll(storey.columns());
            beams.addAll(storey.beams());

            // Merge slab bounds (take union)
            if (storey.slab() != null) {
                slab = mergeSlabs(slab, storey.slab());
            }
        }

        // Phase 47B: Generate cross-unit party walls (walls between rooms from different units)
        List<WallAssemblySpec> crossUnitWalls = generateCrossUnitPartyWalls(rooms, name, height);
        walls.addAll(crossUnitWalls);

        // Phase 46C: Classify and deduplicate party walls
        walls = classifyAndDeduplicateWalls(walls, rooms);

        return new StoreySpec(name, level, baseZ, height, slab, walls, rooms, stairs,
                              doors, windows, landings, sprinklers, lights, fixtures,
                              columns, beams, diffusers, electricals, plumbing,
                              List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Phase 47B: Generate party walls between rooms from different units.
     * Each unit compiles independently, so cross-unit walls must be generated during merge.
     */
    private static List<WallAssemblySpec> generateCrossUnitPartyWalls(
            List<RoomSpec> rooms, String storeyName, double wallHeight) {

        List<WallAssemblySpec> partyWalls = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>();  // Track room pairs to avoid duplicates

        for (RoomSpec roomA : rooms) {
            if (roomA.unitId() == null) continue;  // Skip shared rooms

            for (RoomSpec roomB : rooms) {
                if (roomB.unitId() == null) continue;  // Skip shared rooms
                if (roomA.name().equals(roomB.name())) continue;  // Skip self
                if (roomA.unitId().equals(roomB.unitId())) continue;  // Same unit - not a party wall

                // Create canonical pair key to avoid duplicates
                String pairKey = roomA.name().compareTo(roomB.name()) < 0
                    ? roomA.name() + "|" + roomB.name()
                    : roomB.name() + "|" + roomA.name();
                if (processedPairs.contains(pairKey)) continue;

                // Check if rooms are adjacent
                String sharedSide = getSharedSide(roomA, roomB);
                if (sharedSide != null) {
                    processedPairs.add(pairKey);

                    // Calculate wall geometry
                    double wallLength, minX, minY, maxX, maxY;
                    double thickness = 0.250;  // 250mm party wall

                    if ("NORTH".equals(sharedSide) || "SOUTH".equals(sharedSide)) {
                        // Horizontal wall - length is X overlap
                        double overlapStart = Math.max(roomA.minX(), roomB.minX());
                        double overlapEnd = Math.min(roomA.maxX(), roomB.maxX());
                        wallLength = overlapEnd - overlapStart;
                        minX = overlapStart;
                        maxX = overlapEnd;
                        // Y position at the boundary
                        double boundaryY = "NORTH".equals(sharedSide) ? roomA.maxY() : roomA.minY();
                        minY = boundaryY - thickness / 2;
                        maxY = boundaryY + thickness / 2;
                    } else {
                        // Vertical wall - length is Y overlap
                        double overlapStart = Math.max(roomA.minY(), roomB.minY());
                        double overlapEnd = Math.min(roomA.maxY(), roomB.maxY());
                        wallLength = overlapEnd - overlapStart;
                        minY = overlapStart;
                        maxY = overlapEnd;
                        // X position at the boundary
                        double boundaryX = "EAST".equals(sharedSide) ? roomA.maxX() : roomA.minX();
                        minX = boundaryX - thickness / 2;
                        maxX = boundaryX + thickness / 2;
                    }

                    // Use canonical naming (alphabetically first room first)
                    String canonicalName = roomA.name().compareTo(roomB.name()) < 0
                        ? "PARTY_" + roomA.name() + "_" + roomB.name() + "_WALL"
                        : "PARTY_" + roomB.name() + "_" + roomA.name() + "_WALL";

                    System.out.printf("[PARTY-WALL] Generating: %s <-> %s, side=%s, length=%.2fm%n",
                        roomA.name(), roomB.name(), sharedSide, wallLength);

                    // Create cladding spec with wall geometry (required by GeometryValidator)
                    CladdingSpec cladding = new CladdingSpec(
                        "FIRE_RATED_GYPSUM",  // Fire-rated material for party wall
                        minX, minY, 0,        // minZ = 0 (ground level)
                        maxX, maxY, wallHeight
                    );

                    WallAssemblySpec partyWall = new WallAssemblySpec(
                        canonicalName,
                        "PARTY_WALL",
                        sharedSide,
                        wallLength,
                        thickness,
                        wallHeight,
                        storeyName,
                        List.of(),  // No detailed framing for now
                        cladding,
                        WallType.PARTY,
                        FireRating.FRL_60_60_60
                    );
                    partyWalls.add(partyWall);
                }
            }
        }

        System.out.printf("[PARTY-WALL] Generated %d cross-unit party walls%n", partyWalls.size());
        return partyWalls;
    }

    /**
     * Check if two rooms share a wall and return the shared side (from roomA's perspective).
     */
    private static String getSharedSide(RoomSpec roomA, RoomSpec roomB) {
        double tolerance = 0.1; // 100mm tolerance

        // Check if roomB is to the NORTH of roomA
        if (Math.abs(roomB.minY() - roomA.maxY()) < tolerance && overlapsX(roomA, roomB)) {
            return "NORTH";
        }
        // Check if roomB is to the SOUTH of roomA
        if (Math.abs(roomB.maxY() - roomA.minY()) < tolerance && overlapsX(roomA, roomB)) {
            return "SOUTH";
        }
        // Check if roomB is to the EAST of roomA
        if (Math.abs(roomB.minX() - roomA.maxX()) < tolerance && overlapsY(roomA, roomB)) {
            return "EAST";
        }
        // Check if roomB is to the WEST of roomA
        if (Math.abs(roomB.maxX() - roomA.minX()) < tolerance && overlapsY(roomA, roomB)) {
            return "WEST";
        }

        return null;  // Not adjacent
    }

    /**
     * Phase 46C: Classify walls and deduplicate party walls.
     * Party walls are owned by the canonical (alphabetically first) unit.
     */
    private static List<WallAssemblySpec> classifyAndDeduplicateWalls(
            List<WallAssemblySpec> walls, List<RoomSpec> rooms) {

        // Build room lookup by position
        Map<String, RoomSpec> roomsByName = new HashMap<>();
        for (RoomSpec room : rooms) {
            roomsByName.put(room.name(), room);
        }

        // Track party wall positions to detect duplicates
        // Key: normalized position string (e.g., "X=4.0,Y=0-5")
        Map<String, WallAssemblySpec> partyWallsByPosition = new HashMap<>();

        // Phase 49: Track perimeter wall names to deduplicate during multi-unit merge
        // Perimeter walls (SOUTH, NORTH, EAST, WEST) can be duplicated when units share storeys
        Set<String> seenPerimeterWalls = new HashSet<>();

        List<WallAssemblySpec> result = new ArrayList<>();

        for (WallAssemblySpec wall : walls) {
            // Phase 47B: Pass through pre-classified party walls from generateCrossUnitPartyWalls
            if (wall.assemblyName().startsWith("PARTY_") && wall.wallType() == WallType.PARTY) {
                result.add(wall);
                continue;
            }

            // Phase 47B: Handle interior walls specially - they have both room names
            if (wall.assemblyName().startsWith("INTERIOR_")) {
                String[] roomNames = extractRoomNamesFromInteriorWall(wall.assemblyName(), roomsByName);
                if (roomNames != null && roomNames.length == 2) {
                    RoomSpec roomA = roomsByName.get(roomNames[0]);
                    RoomSpec roomB = roomsByName.get(roomNames[1]);

                    if (roomA != null && roomB != null &&
                        roomA.unitId() != null && roomB.unitId() != null &&
                        !roomA.unitId().equals(roomB.unitId())) {
                        // Different units - this is a party wall!
                        System.out.printf("[WALL] Party wall detected: %s (%s) <-> %s (%s)%n",
                            roomA.name(), roomA.unitId(), roomB.name(), roomB.unitId());

                        // Canonical ownership: alphabetically first unit owns the wall
                        RoomSpec owner = roomA.unitId().compareTo(roomB.unitId()) < 0 ? roomA : roomB;

                        // Check for duplicate
                        String posKey = getWallPositionKey(wall);
                        if (!partyWallsByPosition.containsKey(posKey)) {
                            partyWallsByPosition.put(posKey, wall);

                            WallAssemblySpec partyWall = new WallAssemblySpec(
                                wall.assemblyName(),
                                wall.assemblyType(),
                                wall.side(),
                                wall.length(),
                                0.250,  // Party wall thickness
                                wall.height(),
                                wall.storeyName(),
                                wall.frames(),
                                wall.cladding(),
                                WallType.PARTY,
                                FireRating.FRL_60_60_60
                            );
                            result.add(partyWall);
                        }
                        continue;
                    }
                }
            }

            // Extract room name from wall assembly name (e.g., "living_a_EAST_WALL_ASSEMBLY")
            String roomName = extractRoomNameFromWall(wall.assemblyName());
            RoomSpec room = roomName != null ? roomsByName.get(roomName) : null;

            if (room == null || room.unitId() == null) {
                // Phase 49: Deduplicate perimeter walls by POSITION
                // Walls at same position (e.g., overlapping building envelopes) are duplicates
                // But walls at different positions (different units) should be kept
                String posKey = getWallPositionKey(wall);
                if (seenPerimeterWalls.contains(posKey)) {
                    // Duplicate perimeter wall at same position - skip
                    continue;
                }
                seenPerimeterWalls.add(posKey);
                result.add(wall);
                continue;
            }

            // Find adjacent room on the other side of this wall
            RoomSpec adjacentRoom = findAdjacentRoom(room, wall.side(), rooms);

            // Classify wall
            WallType wallType;
            FireRating fireRating;

            if (adjacentRoom == null) {
                // No adjacent room - external wall
                wallType = WallType.EXTERNAL;
                fireRating = FireRating.NONE;
            } else if (adjacentRoom.unitId() == null) {
                // Adjacent to shared space
                wallType = WallType.SHARED;
                fireRating = FireRating.FRL_60_60_60;
            } else if (adjacentRoom.unitId().equals(room.unitId())) {
                // Same unit - internal wall
                wallType = WallType.INTERNAL;
                fireRating = FireRating.NONE;
            } else {
                // Different units - party wall
                wallType = WallType.PARTY;
                fireRating = FireRating.FRL_60_60_60;

                // Canonical ownership: alphabetically first unit owns the wall
                if (room.unitId().compareTo(adjacentRoom.unitId()) > 0) {
                    // This unit doesn't own the party wall - skip
                    continue;
                }

                // Check for duplicate party wall at same position
                String posKey = getWallPositionKey(wall);
                if (partyWallsByPosition.containsKey(posKey)) {
                    // Duplicate - skip
                    continue;
                }
                partyWallsByPosition.put(posKey, wall);
            }

            // Create classified wall
            WallAssemblySpec classifiedWall = new WallAssemblySpec(
                wall.assemblyName(),
                wall.assemblyType(),
                wall.side(),
                wall.length(),
                wallType == WallType.PARTY ? 0.250 : wall.thickness(), // Party walls thicker
                wall.height(),
                wall.storeyName(),
                wall.frames(),
                wall.cladding(),
                wallType,
                fireRating
            );

            result.add(classifiedWall);
        }

        int partyWallCount = (int) result.stream()
            .filter(w -> w.wallType() == WallType.PARTY)
            .count();
        if (partyWallCount > 0) {
            System.out.println("[WALL] Classified " + partyWallCount + " party walls (FRL 60/60/60)");
        }

        return result;
    }

    /**
     * Extract room name from wall assembly name.
     * E.g., "living_a_EAST_WALL_ASSEMBLY" -> "living_a"
     */
    private static String extractRoomNameFromWall(String assemblyName) {
        // Wall names are like "INTERIOR_roomA_roomB_WALL_ASSEMBLY" or "roomname_SIDE_WALL_ASSEMBLY"
        if (assemblyName.startsWith("INTERIOR_")) {
            // Interior wall between two rooms: INTERIOR_roomA_roomB_WALL_ASSEMBLY
            // Room names can contain underscores, so we can't just split
            // Look for _WALL_ASSEMBLY suffix and remove it
            String withoutSuffix = assemblyName.replace("_WALL_ASSEMBLY", "");
            // Now we have INTERIOR_roomA_roomB
            // Return first room (alphabetically first for canonical ownership)
            String roomsPart = withoutSuffix.substring(9); // Remove "INTERIOR_"
            // This is "roomA_roomB" - return roomA (everything up to last occurrence that matches a room)
            return roomsPart;  // Return full string for now, handle in classification
        }

        // Try to extract room name before _NORTH/_SOUTH/_EAST/_WEST
        for (String dir : new String[]{"_NORTH_", "_SOUTH_", "_EAST_", "_WEST_"}) {
            int idx = assemblyName.indexOf(dir);
            if (idx > 0) {
                return assemblyName.substring(0, idx);
            }
        }

        return null;
    }

    /**
     * Extract both room names from interior wall assembly name.
     * E.g., "INTERIOR_living_a_kitchen_a_WALL_ASSEMBLY" -> ["living_a", "kitchen_a"]
     */
    private static String[] extractRoomNamesFromInteriorWall(String assemblyName, Map<String, RoomSpec> roomsByName) {
        if (!assemblyName.startsWith("INTERIOR_")) {
            return null;
        }

        String withoutPrefix = assemblyName.substring(9);  // Remove "INTERIOR_"
        String withoutSuffix = withoutPrefix.replace("_WALL_ASSEMBLY", "");

        // Try to find two known room names in the string
        // Format is "roomA_roomB" where room names can contain underscores
        for (String roomA : roomsByName.keySet()) {
            if (withoutSuffix.startsWith(roomA + "_")) {
                String remainder = withoutSuffix.substring(roomA.length() + 1);
                if (roomsByName.containsKey(remainder)) {
                    return new String[]{roomA, remainder};
                }
            }
        }

        return null;
    }

    /**
     * Find room adjacent to given room on specified side.
     */
    private static RoomSpec findAdjacentRoom(RoomSpec room, String side, List<RoomSpec> rooms) {
        double tolerance = 0.1; // 100mm tolerance for adjacency

        for (RoomSpec other : rooms) {
            if (other.name().equals(room.name())) continue;

            boolean adjacent = switch (side.toUpperCase()) {
                case "NORTH" -> Math.abs(other.minY() - room.maxY()) < tolerance &&
                                overlapsX(room, other);
                case "SOUTH" -> Math.abs(other.maxY() - room.minY()) < tolerance &&
                                overlapsX(room, other);
                case "EAST" -> Math.abs(other.minX() - room.maxX()) < tolerance &&
                               overlapsY(room, other);
                case "WEST" -> Math.abs(other.maxX() - room.minX()) < tolerance &&
                               overlapsY(room, other);
                default -> false;
            };

            if (adjacent) {
                return other;
            }
        }

        return null;
    }

    /** Check if two rooms overlap in X dimension */
    private static boolean overlapsX(RoomSpec a, RoomSpec b) {
        return a.maxX() > b.minX() && a.minX() < b.maxX();
    }

    /** Check if two rooms overlap in Y dimension */
    private static boolean overlapsY(RoomSpec a, RoomSpec b) {
        return a.maxY() > b.minY() && a.minY() < b.maxY();
    }

    /**
     * Generate position key for wall deduplication.
     */
    private static String getWallPositionKey(WallAssemblySpec wall) {
        // Use cladding bounds as position reference
        CladdingSpec c = wall.cladding();
        return String.format("%.2f,%.2f-%.2f,%.2f", c.minX(), c.minY(), c.maxX(), c.maxY());
    }

    /**
     * Phase 46: Merge two slabs by taking their bounding box union.
     */
    private static SlabSpec mergeSlabs(SlabSpec a, SlabSpec b) {
        if (a == null) return b;
        if (b == null) return a;

        double minX = Math.min(a.minX(), b.minX());
        double minY = Math.min(a.minY(), b.minY());
        double maxX = Math.max(a.maxX(), b.maxX());
        double maxY = Math.max(a.maxY(), b.maxY());
        double minZ = Math.min(a.minZ(), b.minZ());
        double maxZ = Math.max(a.maxZ(), b.maxZ());

        return new SlabSpec(a.type(), a.name(), minX, minY, maxX, maxY, minZ, maxZ);
    }

}
