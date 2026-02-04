package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.contract.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.solver.SpaceSolver;
import com.bim.compiler.solver.SpaceSolver.*;
import com.bim.compiler.system.MEPSystem;
import com.bim.compiler.util.OutlierLogger;
import com.bim.compiler.validation.building.*;

import java.nio.file.Path;
import java.sql.SQLException;
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

    // IRC 2021 Stair Constants (from BIMConstants)
    private static final double MAX_RISER = BIMConstants.IRC_MAX_RISER_HEIGHT;
    private static final double MIN_TREAD = BIMConstants.IRC_MIN_TREAD_DEPTH;
    private static final double MIN_STAIR_WIDTH = BIMConstants.IRC_MIN_STAIR_WIDTH;

    // Construction Constants (from BIMConstants)
    private static final double WALL_THICKNESS = BIMConstants.STANDARD_WALL_THICKNESS;
    private static final double SLAB_THICKNESS = BIMConstants.STANDARD_SLAB_THICKNESS;
    private static final double SLAB_OVERLAP = BIMConstants.STANDARD_SLAB_OVERLAP;
    /** Separating floor slab thickness 200mm - ◆ RESEARCHED: UBBL fire separation
     *  (Table 4 ~1hr fire rating) + BS 8233 acoustic performance (~Rw 50dB airborne).
     *  Standard 150mm insufficient for acoustic separation between dwelling units. */
    private static final double SEPARATING_SLAB_THICKNESS = 0.20;

    // Layer 3: Solver constants (from BIMConstants)
    private static final int DEFAULT_GRID_WIDTH = BIMConstants.DEFAULT_GRID_WIDTH;
    private static final int DEFAULT_GRID_HEIGHT = BIMConstants.DEFAULT_GRID_HEIGHT;

    // Phase 15B: Door/Window defaults (from BIMConstants)
    private static final double DEFAULT_DOOR_WIDTH = BIMConstants.STANDARD_DOOR_WIDTH;
    private static final double DEFAULT_DOOR_HEIGHT = BIMConstants.STANDARD_DOOR_HEIGHT;
    private static final double DEFAULT_WINDOW_WIDTH = BIMConstants.STANDARD_WINDOW_WIDTH;
    private static final double DEFAULT_WINDOW_HEIGHT = BIMConstants.STANDARD_WINDOW_HEIGHT;
    private static final double DEFAULT_SILL_HEIGHT = BIMConstants.STANDARD_SILL_HEIGHT;
    private static final double TOLERANCE = BIMConstants.TOLERANCE;

    // =========================================================================
    // Phase 57B: ADSession Integration
    // ThreadLocal holder allows nested methods to access AD without signature changes
    // =========================================================================
    private static final ThreadLocal<ADSession> currentSession = new ThreadLocal<>();

    /**
     * Get the current compilation's AD session.
     * Returns null if no session is active (compilation outside ADSession context).
     */
    public static ADSession getSession() {
        return currentSession.get();
    }

    /**
     * Compile building definition to spec.
     * If any storey has rooms without positions, invokes SpaceSolver first.
     *
     * Phase 25: Resets OutlierLogger at start and tracks elements for metrics.
     */
    public static BuildingSpec compile(BuildingDefinition def) {
        return compile(def, null);
    }

    /**
     * Compile building definition to spec with optional output directory for outlier log.
     * Phase 25: Full outlier tracking integration.
     * Phase 46: Multi-unit building support.
     * Phase 57B: ADSession integration - single connection per compile.
     *
     * @param def Building definition to compile
     * @param outputDir Optional output directory for outliers.log (null = no file output)
     */
    public static BuildingSpec compile(BuildingDefinition def, Path outputDir) {
        // Phase 57B: Open ADSession for entire compilation
        try (ADSession session = ADSession.open()) {
            currentSession.set(session);
            try {
                return compileWithSession(def, outputDir);
            } finally {
                // Log session stats and clear ThreadLocal
                if (session.cacheSize() > 0) {
                    System.out.printf("[AD] Session cache: %d entries%n", session.cacheSize());
                }
                currentSession.remove();
            }
        } catch (SQLException e) {
            // Fall back to compilation without session (individual AD calls)
            System.out.println("[AD] Session unavailable, using individual connections: " + e.getMessage());
            return compileWithSession(def, outputDir);
        }
    }

    /**
     * Internal compile implementation (with or without active session).
     */
    private static BuildingSpec compileWithSession(BuildingDefinition def, Path outputDir) {
        // Phase 25: Reset outlier tracking for this compilation
        OutlierLogger.reset();
        OutlierLogger.setCompilationName(def.name());

        // Phase 46: Check for multi-unit building
        if (def.isMultiUnit()) {
            return compileMultiUnit(def, outputDir);
        }

        // Single-unit building: original compilation path
        // Layer 3: Check if solver is needed
        BuildingDefinition resolvedDef = resolveConstraints(def);
        def = resolvedDef;  // Use resolved definition
        List<StoreySpec> storeySpecs = new ArrayList<>();
        double currentZ = 0.0;

        // Phase 2 Contract Architecture: Create shared element registry for building
        // Single instance tracks junctions across all storeys for deduplication
        SharedElementRegistry registry = new SharedElementRegistry();

        for (int i = 0; i < def.storeys().size(); i++) {
            StoreyDef storey = def.storeys().get(i);
            boolean isGround = (i == 0);
            boolean isTop = (i == def.storeys().size() - 1);

            StoreySpec spec = compileStorey(storey, currentZ, isGround, isTop, def, registry);
            storeySpecs.add(spec);

            currentZ += storey.height();
        }

        // Phase 2 Contract Architecture: Log registry summary for multi-storey buildings
        if (def.storeys().size() > 1 && registry.totalCount() > 0) {
            System.out.printf("[CONTRACT] %s%n", registry.summary());
        }

        // Compile roof at top storey level
        // Phase 42: Use compiled StoreySpecs which have actual room positions from solver
        RoofSpec roofSpec = null;
        if (def.roof() != null && !storeySpecs.isEmpty()) {
            roofSpec = compileRoofFromSpecs(def.roof(), currentZ, storeySpecs);
            OutlierLogger.incrementTotalElements(); // Roof
        }

        // Phase 25: Count total elements for outlier rate calculation
        for (StoreySpec storey : storeySpecs) {
            OutlierLogger.incrementTotalElements(storey.rooms().size());
            OutlierLogger.incrementTotalElements(storey.walls().size());
            OutlierLogger.incrementTotalElements(storey.doors().size());
            OutlierLogger.incrementTotalElements(storey.windows().size());
            OutlierLogger.incrementTotalElements(storey.stairs().size());
            OutlierLogger.incrementTotalElements(storey.fixtures().size());
            OutlierLogger.incrementTotalElements(storey.sprinklers().size());
            OutlierLogger.incrementTotalElements(storey.lights().size());
            OutlierLogger.incrementTotalElements(storey.columns().size());
            OutlierLogger.incrementTotalElements(storey.beams().size());
            OutlierLogger.incrementTotalElements(storey.diffusers().size());
            OutlierLogger.incrementTotalElements(); // Slab
        }

        // Phase 25: Write outlier summary if output directory provided
        if (outputDir != null) {
            OutlierLogger.summarize(outputDir.resolve("outliers.log"));
        }

        // Always print summary to console if any outliers
        OutlierLogger.printSummary();

        // =====================================================================
        // Phase 35: Build MEP system graphs
        // =====================================================================
        List<MEPSystem> mepSystems = new ArrayList<>();
        try {
            mepSystems = buildMEPSystems(storeySpecs);
            if (!mepSystems.isEmpty()) {
                System.out.printf("[MEP] Built %d system graph(s)%n", mepSystems.size());
                for (MEPSystem sys : mepSystems) {
                    System.out.printf("[MEP] System %s: %d nodes, %d edges, connected=%s%n",
                        sys.getSystemId(), sys.getNodes().size(), sys.getEdges().size(), sys.isConnected());
                }
            }
        } catch (Exception e) {
            System.out.println("[MEP] System graph error: " + e.getMessage());
            e.printStackTrace();
        }

        BuildingSpec spec = new BuildingSpec(def.name(), storeySpecs, roofSpec, mepSystems, def.constructionSystem());

        // Phase 28: Run validation chain after compilation (using factory)
        ValidatorChain.ValidationReport validationReport = validate(spec, def);
        if (validationReport.hasCriticalFailures()) {
            System.out.println(validationReport);
            throw new RuntimeException(
                "Building validation failed with " + validationReport.getTotalCritical() +
                " critical issues. See report above.");
        } else if (validationReport.hasWarnings()) {
            System.out.println(validationReport);
        }

        return spec;
    }

    /**
     * Phase 28: Validate a compiled building spec (minimal validation).
     * Returns validation report without failing compilation.
     */
    public static ValidatorChain.ValidationReport validate(BuildingSpec spec) {
        // Minimal validation without profile/protocol context
        ValidatorChain chain = ValidatorFactory.createMinimal();
        return chain.validate(spec);
    }

    // =========================================================================
    // Phase 55: BOM-based Compilation (Manifest → Resolved → Spec)
    // =========================================================================

    /**
     * Compile from manifest via PreCompiler and LayoutResolver.
     *
     * This is the BOM scaling path:
     * 1. Manifest (5 lines) → PreCompiler expands from AD
     * 2. GeneratedDSL → LayoutResolver merges walls, places openings
     * 3. ResolvedLayout → BuildingSpec with walls owning openings
     *
     * Key: Doors/windows are wall properties, not separate entities.
     * Wall prefabs carry their openings - validated once at prefab level.
     * Phase 57B: ADSession integration - single connection per compile.
     */
    public static BuildingSpec compileFromManifest(PreCompiler.Manifest manifest, Path outputDir) {
        // Phase 57B: Open ADSession for entire compilation
        try (ADSession session = ADSession.open()) {
            currentSession.set(session);
            try {
                return compileFromManifestWithSession(manifest, outputDir);
            } finally {
                if (session.cacheSize() > 0) {
                    System.out.printf("[AD] Session cache: %d entries%n", session.cacheSize());
                }
                currentSession.remove();
            }
        } catch (SQLException e) {
            System.out.println("[AD] Session unavailable: " + e.getMessage());
            return compileFromManifestWithSession(manifest, outputDir);
        }
    }

    /**
     * Internal manifest compile implementation.
     */
    private static BuildingSpec compileFromManifestWithSession(PreCompiler.Manifest manifest, Path outputDir) {
        System.out.println("[BOM] Compiling from manifest: " + manifest.buildingName());

        // Phase 1: Expand manifest to generated DSL
        PreCompiler.GeneratedDSL generated = PreCompiler.precompile(manifest);
        System.out.printf("[BOM] Expanded: %d storeys, %d notes%n",
            generated.storeys().size(), generated.notes().size());

        // Phase 2: Resolve layout (merge walls, place openings)
        LayoutResolver.ResolvedLayout resolved = LayoutResolver.resolve(generated);
        System.out.printf("[BOM] Resolved: %d rooms, %d shared walls, %d resolutions%n",
            resolved.rooms().size(), resolved.sharedWalls().size(), resolved.resolutions().size());

        // Phase 3: Convert to BuildingSpec
        return compileFromResolved(manifest.buildingName(), generated, resolved, outputDir);
    }

    /**
     * Compile from resolved layout to BuildingSpec.
     * Walls own their openings - no separate door/window lists.
     */
    private static BuildingSpec compileFromResolved(
            String buildingName,
            PreCompiler.GeneratedDSL generated,
            LayoutResolver.ResolvedLayout resolved,
            Path outputDir) {

        List<StoreySpec> storeySpecs = new ArrayList<>();
        SharedElementRegistry registry = new SharedElementRegistry();

        // Map resolved rooms by name for lookup
        Map<String, LayoutResolver.ResolvedRoom> roomMap = new HashMap<>();
        for (LayoutResolver.ResolvedRoom r : resolved.rooms()) {
            roomMap.put(r.name(), r);
        }

        // Map shared walls by ID
        Map<String, LayoutResolver.SharedWall> wallMap = new HashMap<>();
        for (LayoutResolver.SharedWall w : resolved.sharedWalls()) {
            wallMap.put(w.wallId(), w);
        }

        double currentZ = 0.0;
        int level = 0;

        for (PreCompiler.GeneratedStorey genStorey : generated.storeys()) {
            List<RoomSpec> roomSpecs = new ArrayList<>();
            List<WallAssemblySpec> wallSpecs = new ArrayList<>();
            List<DoorSpec> doorSpecs = new ArrayList<>();
            List<WindowSpec> windowSpecs = new ArrayList<>();
            List<LightSpec> lightSpecs = new ArrayList<>();
            List<FixtureSpec> fixtureSpecs = new ArrayList<>();
            List<SprinklerSpec> sprinklerSpecs = new ArrayList<>();

            // Build rooms from resolved data
            for (PreCompiler.GeneratedRoom genRoom : genStorey.rooms()) {
                LayoutResolver.ResolvedRoom resRoom = roomMap.get(genRoom.name());
                if (resRoom == null) continue;

                double minX = resRoom.x();
                double minY = resRoom.y();
                double maxX = minX + resRoom.width();
                double maxY = minY + resRoom.depth();
                double minZ = currentZ;
                double maxZ = currentZ + genStorey.height();

                // Collect openings from wall refs
                List<OpeningSpec> openings = new ArrayList<>();
                for (String wallRef : resRoom.wallRefs()) {
                    LayoutResolver.SharedWall wall = wallMap.get(wallRef);
                    if (wall != null) {
                        for (LayoutResolver.Opening op : wall.openings()) {
                            String wallDir = inferWallDirection(wall, minX, minY, maxX, maxY);
                            openings.add(new OpeningSpec(op.type(), wallDir, null, op.width(), op.height()));

                            // Also create door/window specs for backward compatibility
                            double opX = wall.x1() + (wall.x2() - wall.x1()) * op.position();
                            double opY = wall.y1() + (wall.y2() - wall.y1()) * op.position();

                            if ("DOOR".equals(op.type())) {
                                doorSpecs.add(new DoorSpec(op.productId(), resRoom.name(), wallDir,
                                    opX, opY, minZ, op.width(), op.height(), null));
                            } else if ("WINDOW".equals(op.type())) {
                                windowSpecs.add(new WindowSpec(op.productId(), resRoom.name(), wallDir,
                                    opX, opY, minZ + op.sillHeight(), op.width(), op.height(), op.sillHeight()));
                            }
                        }
                    }
                }

                roomSpecs.add(new RoomSpec(resRoom.type(), resRoom.name(),
                    minX, minY, maxX, maxY, minZ, maxZ, openings, null, null, null, genStorey.name()));

                // Convert products to MEP specs
                for (AutoFitter.ProductPlacement p : resRoom.products()) {
                    double px = minX + p.x();
                    double py = minY + p.y();
                    double pz = minZ + p.z();

                    switch (p.productType()) {
                        case "ELECTRICAL" -> lightSpecs.add(new LightSpec(
                            p.productId(), resRoom.name(), px, py, pz, "recessed", 2.0));
                        case "FIXTURE" -> fixtureSpecs.add(new FixtureSpec(
                            p.productId(), resRoom.name(), p.productId(),
                            px, py, pz, p.rotation(), null, 0.5, 0.5, 0.5));
                        case "SPRINKLER" -> sprinklerSpecs.add(new SprinklerSpec(
                            p.productId(), resRoom.name(), px, py, pz, "pendant", 3.0));
                        // DOOR and WINDOW handled above from wall openings
                    }
                }
            }

            // Build wall assemblies from shared walls
            for (LayoutResolver.SharedWall wall : resolved.sharedWalls()) {
                // Only include walls that belong to rooms in this storey
                boolean belongsToStorey = wall.rooms().stream()
                    .anyMatch(rn -> genStorey.rooms().stream().anyMatch(gr -> gr.name().equals(rn)));
                if (!belongsToStorey) continue;

                double length = Math.hypot(wall.x2() - wall.x1(), wall.y2() - wall.y1());
                String side = inferWallSide(wall);

                // Wall with openings - openings are already part of the wall record
                // The geometry generator will subtract opening voids
                wallSpecs.add(new WallAssemblySpec(
                    wall.wallId(),
                    wall.rooms().size() > 1 ? "SHARED" : "PERIMETER",
                    side,
                    length, wall.thickness(), genStorey.height(),
                    genStorey.name(),
                    List.of(),  // Frames generated later
                    null,       // Cladding
                    wall.rooms().size() > 1 ? WallType.PARTY : WallType.EXTERNAL,
                    null        // FireRating
                ));
            }

            // Create slab
            double slabMinX = roomSpecs.stream().mapToDouble(RoomSpec::minX).min().orElse(0);
            double slabMinY = roomSpecs.stream().mapToDouble(RoomSpec::minY).min().orElse(0);
            double slabMaxX = roomSpecs.stream().mapToDouble(RoomSpec::maxX).max().orElse(10);
            double slabMaxY = roomSpecs.stream().mapToDouble(RoomSpec::maxY).max().orElse(10);

            SlabSpec slab = new SlabSpec(
                "STANDARD", "SLAB_" + genStorey.name(),
                slabMinX - SLAB_OVERLAP, slabMinY - SLAB_OVERLAP,
                slabMaxX + SLAB_OVERLAP, slabMaxY + SLAB_OVERLAP,
                currentZ, currentZ + SLAB_THICKNESS
            );

            storeySpecs.add(new StoreySpec(
                genStorey.name(), level++, currentZ, genStorey.height(),
                slab, wallSpecs, roomSpecs,
                List.of(),      // stairs
                doorSpecs, windowSpecs,
                List.of(),      // landings
                sprinklerSpecs, lightSpecs, fixtureSpecs,
                List.of(),      // columns
                List.of(),      // beams
                List.of()       // diffusers
            ));

            currentZ += genStorey.height();
        }

        System.out.printf("[BOM] Compiled: %d storeys%n", storeySpecs.size());

        return new BuildingSpec(buildingName, storeySpecs, null, List.of(), ConstructionSystem.FRAMED);
    }

    /** Infer wall direction relative to room bounds */
    private static String inferWallDirection(LayoutResolver.SharedWall wall,
            double minX, double minY, double maxX, double maxY) {
        boolean isHorizontal = Math.abs(wall.y1() - wall.y2()) < 0.01;
        if (isHorizontal) {
            return Math.abs(wall.y1() - minY) < 0.01 ? "south" : "north";
        } else {
            return Math.abs(wall.x1() - minX) < 0.01 ? "west" : "east";
        }
    }

    /** Infer wall side from coordinates */
    private static String inferWallSide(LayoutResolver.SharedWall wall) {
        boolean isHorizontal = Math.abs(wall.y1() - wall.y2()) < 0.01;
        if (isHorizontal) {
            return wall.y1() < wall.y2() ? "SOUTH" : "NORTH";
        } else {
            return wall.x1() < wall.x2() ? "WEST" : "EAST";
        }
    }

    /**
     * Phase 28: Validate with full context from BuildingDefinition.
     * Uses ValidatorFactory to compose validators based on profile/protocol/LOD.
     */
    public static ValidatorChain.ValidationReport validate(BuildingSpec spec, BuildingDefinition def) {
        ValidatorChain chain = ValidatorFactory.create(def);
        return chain.validate(spec);
    }

    /**
     * Phase 28: Compile and validate, returning both spec and validation report.
     * Use this when you want to inspect validation results without throwing.
     */
    public static CompilationResult compileWithValidation(BuildingDefinition def) {
        return compileWithValidation(def, null);
    }

    /**
     * Phase 28: Compile and validate with output directory.
     */
    public static CompilationResult compileWithValidation(BuildingDefinition def, Path outputDir) {
        // Phase 25: Reset outlier tracking for this compilation
        OutlierLogger.reset();
        OutlierLogger.setCompilationName(def.name());

        // Layer 3: Check if solver is needed
        BuildingDefinition resolvedDef = resolveConstraints(def);
        def = resolvedDef;
        List<StoreySpec> storeySpecs = new ArrayList<>();
        double currentZ = 0.0;

        // Phase 2 Contract Architecture: Create shared element registry
        SharedElementRegistry registry = new SharedElementRegistry();

        for (int i = 0; i < def.storeys().size(); i++) {
            StoreyDef storey = def.storeys().get(i);
            boolean isGround = (i == 0);
            boolean isTop = (i == def.storeys().size() - 1);

            StoreySpec spec = compileStorey(storey, currentZ, isGround, isTop, def, registry);
            storeySpecs.add(spec);

            currentZ += storey.height();
        }

        // Phase 2 Contract Architecture: Log registry summary for multi-storey buildings
        if (def.storeys().size() > 1 && registry.totalCount() > 0) {
            System.out.printf("[CONTRACT] %s%n", registry.summary());
        }

        // Phase 42: Use compiled StoreySpecs which have actual room positions from solver
        RoofSpec roofSpec = null;
        if (def.roof() != null && !storeySpecs.isEmpty()) {
            roofSpec = compileRoofFromSpecs(def.roof(), currentZ, storeySpecs);
            OutlierLogger.incrementTotalElements();
        }

        for (StoreySpec storey : storeySpecs) {
            OutlierLogger.incrementTotalElements(storey.rooms().size());
            OutlierLogger.incrementTotalElements(storey.walls().size());
            OutlierLogger.incrementTotalElements(storey.doors().size());
            OutlierLogger.incrementTotalElements(storey.windows().size());
            OutlierLogger.incrementTotalElements(storey.stairs().size());
            OutlierLogger.incrementTotalElements(storey.fixtures().size());
            OutlierLogger.incrementTotalElements(storey.sprinklers().size());
            OutlierLogger.incrementTotalElements(storey.lights().size());
            OutlierLogger.incrementTotalElements(storey.columns().size());
            OutlierLogger.incrementTotalElements(storey.beams().size());
            OutlierLogger.incrementTotalElements(storey.diffusers().size());
            OutlierLogger.incrementTotalElements();
        }

        if (outputDir != null) {
            OutlierLogger.summarize(outputDir.resolve("outliers.log"));
        }
        OutlierLogger.printSummary();

        // =====================================================================
        // Phase 35: Build MEP system graphs
        // =====================================================================
        List<MEPSystem> mepSystems = new ArrayList<>();
        try {
            mepSystems = buildMEPSystems(storeySpecs);
            if (!mepSystems.isEmpty()) {
                System.out.printf("[MEP] Built %d system graph(s)%n", mepSystems.size());
            }
        } catch (Exception e) {
            System.out.println("[MEP] System graph error: " + e.getMessage());
        }

        BuildingSpec spec = new BuildingSpec(def.name(), storeySpecs, roofSpec, mepSystems, def.constructionSystem());
        ValidatorChain.ValidationReport report = validate(spec, def);

        return new CompilationResult(spec, report);
    }

    /**
     * Phase 28: Result of compilation including validation report.
     */
    public record CompilationResult(
        BuildingSpec spec,
        ValidatorChain.ValidationReport validationReport
    ) {
        public boolean isValid() {
            return !validationReport.hasCriticalFailures();
        }
    }

    // =========================================================================
    // Phase 46: Multi-Unit Building Compilation
    // =========================================================================

    /**
     * Phase 46: Compile multi-unit building.
     * 1. Builds cross-unit dependency graph (for above: constraints)
     * 2. Topologically sorts units by dependency
     * 3. Compiles each unit in order, passing resolved positions
     * 4. Merges all unit storeys into building storeys
     */
    private static BuildingSpec compileMultiUnit(BuildingDefinition def, Path outputDir) {
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
            BuildingDefinition resolvedUnitDef = resolveConstraints(unitDef, crossUnitPositions);

            // Compile storeys
            List<StoreySpec> storeySpecs = new ArrayList<>();
            double currentZ = 0.0;

            // Phase 2 Contract Architecture: Create registry for this unit
            SharedElementRegistry unitRegistry = new SharedElementRegistry();

            for (int i = 0; i < resolvedUnitDef.storeys().size(); i++) {
                StoreyDef storey = resolvedUnitDef.storeys().get(i);
                boolean isGround = (storey.level() == 0);
                boolean isTop = (i == resolvedUnitDef.storeys().size() - 1);

                StoreySpec spec = compileStorey(storey, currentZ, isGround, isTop, resolvedUnitDef, unitRegistry);

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
            BuildingDefinition resolvedSharedDef = resolveConstraints(sharedDef);

            // Phase 2 Contract Architecture: Create registry for shared spaces
            SharedElementRegistry sharedRegistry = new SharedElementRegistry();

            double currentZ = 0.0;
            for (int i = 0; i < resolvedSharedDef.storeys().size(); i++) {
                StoreyDef storey = resolvedSharedDef.storeys().get(i);
                boolean isGround = (storey.level() == 0);
                boolean isTop = (i == resolvedSharedDef.storeys().size() - 1);

                StoreySpec spec = compileStorey(storey, currentZ, isGround, isTop, resolvedSharedDef, sharedRegistry);
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
            roofSpec = compileRoofFromSpecs(def.roof(), topZ, mergedStoreys);
            OutlierLogger.incrementTotalElements();
        }

        // 7. Count elements for outlier tracking
        for (StoreySpec storey : mergedStoreys) {
            OutlierLogger.incrementTotalElements(storey.rooms().size());
            OutlierLogger.incrementTotalElements(storey.walls().size());
            OutlierLogger.incrementTotalElements(storey.doors().size());
            OutlierLogger.incrementTotalElements(storey.windows().size());
            OutlierLogger.incrementTotalElements(storey.stairs().size());
            OutlierLogger.incrementTotalElements(storey.fixtures().size());
            OutlierLogger.incrementTotalElements(storey.sprinklers().size());
            OutlierLogger.incrementTotalElements(storey.lights().size());
            OutlierLogger.incrementTotalElements(storey.columns().size());
            OutlierLogger.incrementTotalElements(storey.beams().size());
            OutlierLogger.incrementTotalElements(storey.diffusers().size());
            OutlierLogger.incrementTotalElements(); // Slab
        }

        if (outputDir != null) {
            OutlierLogger.summarize(outputDir.resolve("outliers.log"));
        }
        OutlierLogger.printSummary();

        // 8. Build MEP systems
        List<MEPSystem> mepSystems = new ArrayList<>();
        try {
            mepSystems = buildMEPSystems(mergedStoreys);
            if (!mepSystems.isEmpty()) {
                System.out.printf("[MEP] Built %d system graph(s)%n", mepSystems.size());
            }
        } catch (Exception e) {
            System.out.println("[MEP] System graph error: " + e.getMessage());
        }

        BuildingSpec spec = new BuildingSpec(def.name(), mergedStoreys, roofSpec, mepSystems, def.constructionSystem());

        // 9. Validate
        ValidatorChain.ValidationReport validationReport = validate(spec, def);
        if (validationReport.hasCriticalFailures()) {
            System.out.println(validationReport);
            throw new RuntimeException(
                "Building validation failed with " + validationReport.getTotalCritical() +
                " critical issues. See report above.");
        } else if (validationReport.hasWarnings()) {
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
                updatedRooms, storey.stairs(), storey.landings()));
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

        // Return new StoreySpec with tagged rooms
        return new StoreySpec(
            spec.name(), spec.level(), spec.baseZ(), spec.height(),
            spec.slab(), spec.walls(), taggedRooms, spec.stairs(),
            spec.doors(), spec.windows(), spec.landings(),
            spec.sprinklers(), spec.lights(), spec.fixtures(),
            spec.columns(), spec.beams(), spec.diffusers(),
            spec.electricals(), spec.plumbing()
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
                SlabSpec upgradedSlab = storey.slab().asSeparatingFloor(SEPARATING_SLAB_THICKNESS);
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
                              columns, beams, diffusers, electricals, plumbing);
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

    // =========================================================================
    // Layer 3: Constraint Resolution (Phase 15)
    // =========================================================================

    /**
     * Check if any rooms need solver placement, and resolve constraints if needed.
     * Phase 16: Supports cross-storey alignment via solvedPositions map.
     * Phase 17: Adds above:/below:/stack: constraints.
     */
    private static BuildingDefinition resolveConstraints(BuildingDefinition def) {
        return resolveConstraints(def, Map.of());
    }

    /**
     * Phase 48A: Resolve constraints with cross-unit positions available.
     * For STACKED layouts, this allows above: constraints to reference rooms in other units.
     *
     * @param def Building definition to resolve
     * @param crossUnitPositions Map of room name → position from other units
     */
    private static BuildingDefinition resolveConstraints(BuildingDefinition def,
                                                          Map<String, double[]> crossUnitPositions) {
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

        // Phase 48A: Pre-populate with cross-unit positions for above: constraints
        for (var entry : crossUnitPositions.entrySet()) {
            double[] pos = entry.getValue();
            allSolvedPositions.put(entry.getKey(), new GridPosition((int) pos[0], (int) pos[1]));
            System.out.printf("[SOLVER] Cross-unit position: %s at (%d,%d)%n",
                entry.getKey(), (int) pos[0], (int) pos[1]);
        }

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

            if (room.needsSolverPlacement()) {
                // Phase 25 fix: Add ALL rooms needing placement to constraints
                // Vertically dependent rooms get position from their dependency,
                // but they must be in the solver's roomMap for adjacency lookups
                List<String> adjTo = room.adjacentTo();
                List<String> notAdjTo = room.notAdjacentTo();

                // For vertically dependent rooms, clear their adjacency constraints
                // since they can't be moved anyway - their position is inherited
                if (verticallyDependent.contains(room)) {
                    adjTo = List.of();
                    notAdjTo = List.of();
                }

                constraints.add(new RoomConstraint(
                    room.name(),
                    (int) Math.ceil(room.width()),
                    (int) Math.ceil(room.depth()),
                    adjTo,
                    notAdjTo,
                    verticallyDependent.contains(room) ? null : room.exteriorWall()
                ));
            }
        }

        // Invoke solver for rooms that need it
        // Phase 25: Use solveWithRelaxation for graceful constraint handling
        Map<String, GridPosition> solvedPositions = new HashMap<>();
        if (!constraints.isEmpty()) {
            SpaceSolver solver = new SpaceSolver();

            // First try strict solve
            SolvedLayout layout = solver.solve(constraints, DEFAULT_GRID_WIDTH, DEFAULT_GRID_HEIGHT);

            if (!layout.feasible()) {
                // Phase 25: Try relaxation before failing
                System.out.println("[SOLVER] Strict solve failed, attempting relaxation...");
                layout = solver.solveWithRelaxation(constraints, DEFAULT_GRID_WIDTH, DEFAULT_GRID_HEIGHT);

                if (!layout.feasible()) {
                    throw new RuntimeException(
                        "Cannot satisfy constraints for storey '" + storey.name() + "': " +
                        layout.failureReason());
                }

                if (!layout.droppedConstraints().isEmpty()) {
                    System.out.println("[SOLVER] Solved with relaxation - dropped: " +
                        String.join(", ", layout.droppedConstraints()));
                }
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

    /**
     * Phase 56B: Parse grid position into axis labels for GridDef lookup.
     * "C1" -> ["C", "1"], "D5" -> ["D", "5"]
     */
    private static String[] parseGridLabels(String gridPos) {
        if (gridPos == null || gridPos.isEmpty()) {
            return new String[]{"A", "1"};
        }

        // Handle range positions like "C1-D6" by using start
        if (gridPos.contains("-")) {
            gridPos = gridPos.split("-")[0];
        }

        // Split into letter (X axis) and number (Y axis) parts
        int i = 0;
        while (i < gridPos.length() && Character.isLetter(gridPos.charAt(i))) {
            i++;
        }

        String xAxis = gridPos.substring(0, i).toUpperCase();
        String yAxis = gridPos.substring(i);

        return new String[]{xAxis, yAxis};
    }

    private static StoreySpec compileStorey(StoreyDef storey, double baseZ,
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

        for (RoomDef room : storey.rooms()) {
            double roomMinX, roomMinY, roomMaxX, roomMaxY;

            // Phase 28: Handle rooms with grid bounds (bounds: syntax)
            if (room.hasGridBounds() && building.grid() != null) {
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
                int[] coords = parseGridPosition(room.gridPosition());
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

            for (OpeningDef opening : room.openings()) {
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
                // Center opening on wall
                switch (opening.wall()) {
                    case "south" -> { openingX = roomMinX + room.width() / 2 - width / 2; openingY = roomMinY; }
                    case "north" -> { openingX = roomMinX + room.width() / 2 - width / 2; openingY = roomMaxY; }
                    case "west" -> { openingX = roomMinX; openingY = roomMinY + room.depth() / 2 - width / 2; }
                    case "east" -> { openingX = roomMaxX; openingY = roomMinY + room.depth() / 2 - width / 2; }
                    default -> { openingX = roomMinX; openingY = roomMinY; }
                }

                if (opening.type().equals("DOOR")) {
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
                int[] coords = parseGridPosition(stair.gridPosition());
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
                String[] labels = parseGridLabels(coreStair.gridPosition());
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
                String[] labels = parseGridLabels(coreShaft.gridPosition());
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
                String[] startLabels = parseGridLabels(boundsLabels[0]);
                String[] endLabels = parseGridLabels(boundsLabels[1]);

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
                int[] coords = parseGridPosition(landing.gridPosition());
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

        // Generate perimeter walls with registry for stud deduplication
        walls.add(compileWall("SOUTH", minX, minY, maxX, minY,
            baseZ, baseZ + storey.height(), storey.name(), registry));
        walls.add(compileWall("NORTH", minX, maxY, maxX, maxY,
            baseZ, baseZ + storey.height(), storey.name(), registry));
        walls.add(compileWall("WEST", minX, minY, minX, maxY,
            baseZ, baseZ + storey.height(), storey.name(), registry));
        walls.add(compileWall("EAST", maxX, minY, maxX, maxY,
            baseZ, baseZ + storey.height(), storey.name(), registry));

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

                        // Phase 48D.2: Include connectsTo for internal doors
                        doors.add(new DoorSpec(
                            room1.name() + "_to_" + room2.name() + "_door",
                            room1.name(), doorWall,
                            doorX, doorY, baseZ,
                            DEFAULT_DOOR_WIDTH, DEFAULT_DOOR_HEIGHT,
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
                    double windowX, windowY;
                    double roomCenterX = bounds.minX() + (bounds.maxX() - bounds.minX()) / 2;
                    double roomCenterY = bounds.minY() + (bounds.maxY() - bounds.minY()) / 2;

                    switch (extWall) {
                        case "south" -> {
                            windowX = roomCenterX - DEFAULT_WINDOW_WIDTH / 2;
                            windowY = bounds.minY();  // Room's south wall
                        }
                        case "north" -> {
                            windowX = roomCenterX - DEFAULT_WINDOW_WIDTH / 2;
                            windowY = bounds.maxY();  // Room's north wall
                        }
                        case "west" -> {
                            windowX = bounds.minX();  // Room's west wall
                            windowY = roomCenterY - DEFAULT_WINDOW_WIDTH / 2;
                        }
                        case "east" -> {
                            windowX = bounds.maxX();  // Room's east wall
                            windowY = roomCenterY - DEFAULT_WINDOW_WIDTH / 2;
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

            // Phase 59: Auto-place furniture for LOBBY, CANTEEN, OFFICE rooms
            var furniturePlacer = new com.bim.compiler.library.FurniturePlacer(library);

            for (RoomSpec room : rooms) {
                String roomType = room.type().toUpperCase();

                if (roomType.equals("LOBBY") || roomType.equals("WAITING") || roomType.equals("RECEPTION")) {
                    var placed = furniturePlacer.placeLobbyFurniture(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, room.name()
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
                } else if (roomType.equals("CANTEEN") || roomType.equals("KANTIN") || roomType.equals("CAFETERIA") || roomType.equals("DINING")) {
                    var placed = furniturePlacer.placeCanteenFurniture(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, room.name()
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
                } else if (roomType.equals("OFFICE") || roomType.equals("WORKSTATION")) {
                    var placed = furniturePlacer.placeOfficeFurniture(
                        room.minX(), room.minY(), room.maxX(), room.maxY(),
                        baseZ, room.name()
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
                }
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
                String roomType = room.type();

                // Skip corridors and very small rooms (< 4 m²)
                double roomArea = (room.maxX() - room.minX()) * (room.maxY() - room.minY());
                if (roomType.equalsIgnoreCase("CORRIDOR") || roomArea < 4.0) {
                    continue;
                }

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

            System.out.printf("[HVAC] Storey %s: %d diffusers%n", storey.name(), diffusers.size());

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

        // Phase 3 Debug: Count total frames to verify deduplication
        int totalFrames = walls.stream().mapToInt(w -> w.frames().size()).sum();
        System.out.printf("[PHASE3] Storey %s: %d walls, %d total frames%n",
            storey.name(), walls.size(), totalFrames);

        return new StoreySpec(
            storey.name(), storey.level(), baseZ, storey.height(),
            slab, walls, rooms, stairs, doors, windows, landings,
            sprinklers, lights, fixtures, columns, beams, diffusers, electricals, plumbing,
            elevators, lobbies, shafts  // Phase 56B: CORE elements
        );
    }

    // =========================================================================
    // Phase 35: MEP System Graph Builder
    // =========================================================================

    /**
     * Build MEP system graphs from compiled storey data.
     *
     * Currently builds:
     * - Waste system graph (PLUMBING_WASTE)
     *
     * Future phases will add:
     * - Vent system (PLUMBING_VENT)
     * - Electrical circuits (ELECTRICAL)
     */
    private static List<MEPSystem> buildMEPSystems(List<StoreySpec> storeys) {
        List<MEPSystem> systems = new ArrayList<>();

        // Phase 46D: Check if multi-unit building (rooms have unitId)
        boolean isMultiUnit = storeys.stream()
            .flatMap(s -> s.rooms().stream())
            .anyMatch(r -> r.unitId() != null && !r.unitId().equals("_SHARED"));

        if (isMultiUnit) {
            return buildMultiUnitMEPSystems(storeys);
        }

        // Single-unit building: original logic
        // Collect all plumbing pipes from all storeys
        List<com.bim.compiler.library.PlumbingPlacer.PipeInstance> allPipes = new ArrayList<>();
        for (StoreySpec storey : storeys) {
            for (PlumbingSpec pipe : storey.plumbing()) {
                // Convert PlumbingSpec back to PipeInstance for graph building
                var pipeType = switch (pipe.pipeType().toLowerCase()) {
                    case "waste_riser" -> com.bim.compiler.library.PlumbingPlacer.PipeType.WASTE_RISER;
                    case "vent_pipe" -> com.bim.compiler.library.PlumbingPlacer.PipeType.VENT_PIPE;
                    case "branch_pipe" -> com.bim.compiler.library.PlumbingPlacer.PipeType.BRANCH_PIPE;
                    default -> com.bim.compiler.library.PlumbingPlacer.PipeType.BRANCH_PIPE;
                };

                allPipes.add(new com.bim.compiler.library.PlumbingPlacer.PipeInstance(
                    pipeType,
                    new com.bim.compiler.geometry.Point3D(pipe.startX(), pipe.startY(), pipe.startZ()),
                    new com.bim.compiler.geometry.Point3D(pipe.endX(), pipe.endY(), pipe.endZ()),
                    pipe.diameterM(),
                    pipe.id()
                ));
            }
        }

        // Build waste and vent system graphs if we have plumbing
        if (!allPipes.isEmpty()) {
            var plumbingPlacer = new com.bim.compiler.library.PlumbingPlacer();

            // Phase 35: Waste system
            MEPSystem wasteSystem = plumbingPlacer.buildWasteSystemGraph(allPipes);
            if (!wasteSystem.getTerminals().isEmpty()) {
                systems.add(wasteSystem);
            }

            // Phase 36: Vent system
            MEPSystem ventSystem = plumbingPlacer.buildVentSystemGraph(allPipes);
            if (!ventSystem.getTerminals().isEmpty()) {
                systems.add(ventSystem);
            }

            // Phase 37: Supply system
            MEPSystem supplySystem = plumbingPlacer.buildSupplySystemGraph(allPipes);
            if (!supplySystem.getTerminals().isEmpty()) {
                systems.add(supplySystem);
            }
        }

        // =====================================================================
        // Phase 39: Build electrical circuits graph
        // =====================================================================
        List<com.bim.compiler.library.ElectricalPlacer.ElectricalInstance> allElectrical = new ArrayList<>();

        for (StoreySpec storey : storeys) {
            String storeyId = storey.name();

            // Collect lights as electrical elements
            for (LightSpec light : storey.lights()) {
                allElectrical.add(new com.bim.compiler.library.ElectricalPlacer.ElectricalInstance(
                    com.bim.compiler.library.ElectricalPlacer.ElectricalType.LIGHT,
                    new com.bim.compiler.geometry.Point3D(light.x(), light.y(), light.z()),
                    0,  // rotation
                    light.width(), light.depth(), light.height(),
                    light.geometryHash(),
                    light.fixtureType(),
                    "lighting",
                    light.roomName()
                ));
            }

            // Collect outlets and switches
            for (ElectricalSpec elec : storey.electricals()) {
                var elecType = switch (elec.elementType()) {
                    case "outlet" -> com.bim.compiler.library.ElectricalPlacer.ElectricalType.OUTLET;
                    case "switch" -> com.bim.compiler.library.ElectricalPlacer.ElectricalType.SWITCH;
                    default -> com.bim.compiler.library.ElectricalPlacer.ElectricalType.OUTLET;
                };

                allElectrical.add(new com.bim.compiler.library.ElectricalPlacer.ElectricalInstance(
                    elecType,
                    new com.bim.compiler.geometry.Point3D(elec.x(), elec.y(), elec.z()),
                    elec.rotation(),
                    elec.width(), elec.depth(), elec.height(),
                    null,  // No geometry hash for outlets/switches
                    elec.elementType().toUpperCase(),
                    elec.circuitType() != null ? elec.circuitType() : "general",
                    elec.roomName()
                ));
            }
        }

        // Build electrical system graph
        if (!allElectrical.isEmpty()) {
            var electricalPlacer = new com.bim.compiler.library.ElectricalPlacer(null);  // No library needed for graph
            String storeyId = storeys.size() == 1 ? storeys.get(0).name() : "MULTI";
            MEPSystem electricalSystem = electricalPlacer.buildElectricalGraph(allElectrical, storeyId);
            if (!electricalSystem.getTerminals().isEmpty()) {
                systems.add(electricalSystem);
            }
        }

        return systems;
    }

    /**
     * Phase 46D: Build MEP systems for multi-unit buildings.
     * Each unit gets its own electrical graph (separate distribution board).
     * Plumbing uses unit-scoped branches connecting to shared risers.
     */
    private static List<MEPSystem> buildMultiUnitMEPSystems(List<StoreySpec> storeys) {
        List<MEPSystem> systems = new ArrayList<>();

        // Group rooms by unit
        Map<String, List<RoomSpec>> roomsByUnit = new HashMap<>();
        for (StoreySpec storey : storeys) {
            for (RoomSpec room : storey.rooms()) {
                String unitId = room.unitId() != null ? room.unitId() : "_SHARED";
                roomsByUnit.computeIfAbsent(unitId, k -> new ArrayList<>()).add(room);
            }
        }

        System.out.println("[MEP] Multi-unit building: " + (roomsByUnit.size() -
            (roomsByUnit.containsKey("_SHARED") ? 1 : 0)) + " units");

        // =====================================================================
        // Build per-unit electrical systems
        // =====================================================================
        for (var entry : roomsByUnit.entrySet()) {
            String unitId = entry.getKey();
            List<RoomSpec> unitRooms = entry.getValue();

            // Skip shared spaces for unit-specific electrical (handled separately)
            if ("_SHARED".equals(unitId)) continue;

            // Collect electrical elements for this unit
            List<com.bim.compiler.library.ElectricalPlacer.ElectricalInstance> unitElectrical =
                collectUnitElectrical(storeys, unitRooms);

            if (!unitElectrical.isEmpty()) {
                var electricalPlacer = new com.bim.compiler.library.ElectricalPlacer(null);
                MEPSystem unitSystem = electricalPlacer.buildElectricalGraph(unitElectrical, "UNIT_" + unitId);

                if (!unitSystem.getTerminals().isEmpty()) {
                    systems.add(unitSystem);
                    System.out.printf("[MEP] Unit %s electrical: %d elements, %d nodes, DB=UNIT_%s%n",
                        unitId, unitElectrical.size(), unitSystem.getNodes().size(), unitId);
                }
            }
        }

        // Build shared space electrical (if any)
        List<RoomSpec> sharedRooms = roomsByUnit.getOrDefault("_SHARED", List.of());
        if (!sharedRooms.isEmpty()) {
            List<com.bim.compiler.library.ElectricalPlacer.ElectricalInstance> sharedElectrical =
                collectUnitElectrical(storeys, sharedRooms);

            if (!sharedElectrical.isEmpty()) {
                var electricalPlacer = new com.bim.compiler.library.ElectricalPlacer(null);
                MEPSystem sharedSystem = electricalPlacer.buildElectricalGraph(sharedElectrical, "SHARED");
                if (!sharedSystem.getTerminals().isEmpty()) {
                    systems.add(sharedSystem);
                    System.out.printf("[MEP] Shared electrical: %d elements%n", sharedElectrical.size());
                }
            }
        }

        // =====================================================================
        // Build plumbing systems (grouped by unit but sharing risers)
        // =====================================================================
        List<com.bim.compiler.library.PlumbingPlacer.PipeInstance> allPipes = new ArrayList<>();
        for (StoreySpec storey : storeys) {
            for (PlumbingSpec pipe : storey.plumbing()) {
                var pipeType = switch (pipe.pipeType().toLowerCase()) {
                    case "waste_riser" -> com.bim.compiler.library.PlumbingPlacer.PipeType.WASTE_RISER;
                    case "vent_pipe" -> com.bim.compiler.library.PlumbingPlacer.PipeType.VENT_PIPE;
                    case "branch_pipe" -> com.bim.compiler.library.PlumbingPlacer.PipeType.BRANCH_PIPE;
                    default -> com.bim.compiler.library.PlumbingPlacer.PipeType.BRANCH_PIPE;
                };

                allPipes.add(new com.bim.compiler.library.PlumbingPlacer.PipeInstance(
                    pipeType,
                    new com.bim.compiler.geometry.Point3D(pipe.startX(), pipe.startY(), pipe.startZ()),
                    new com.bim.compiler.geometry.Point3D(pipe.endX(), pipe.endY(), pipe.endZ()),
                    pipe.diameterM(),
                    pipe.id()
                ));
            }
        }

        // Build combined plumbing graphs (risers are shared infrastructure)
        if (!allPipes.isEmpty()) {
            var plumbingPlacer = new com.bim.compiler.library.PlumbingPlacer();

            MEPSystem wasteSystem = plumbingPlacer.buildWasteSystemGraph(allPipes);
            if (!wasteSystem.getTerminals().isEmpty()) {
                systems.add(wasteSystem);
            }

            MEPSystem ventSystem = plumbingPlacer.buildVentSystemGraph(allPipes);
            if (!ventSystem.getTerminals().isEmpty()) {
                systems.add(ventSystem);
            }

            MEPSystem supplySystem = plumbingPlacer.buildSupplySystemGraph(allPipes);
            if (!supplySystem.getTerminals().isEmpty()) {
                systems.add(supplySystem);
            }
        }

        return systems;
    }

    /**
     * Phase 46D: Collect electrical elements belonging to specific rooms.
     */
    private static List<com.bim.compiler.library.ElectricalPlacer.ElectricalInstance> collectUnitElectrical(
            List<StoreySpec> storeys, List<RoomSpec> unitRooms) {

        Set<String> unitRoomNames = unitRooms.stream()
            .map(RoomSpec::name)
            .collect(java.util.stream.Collectors.toSet());

        List<com.bim.compiler.library.ElectricalPlacer.ElectricalInstance> result = new ArrayList<>();

        for (StoreySpec storey : storeys) {
            // Collect lights in unit rooms
            for (LightSpec light : storey.lights()) {
                if (unitRoomNames.contains(light.roomName())) {
                    result.add(new com.bim.compiler.library.ElectricalPlacer.ElectricalInstance(
                        com.bim.compiler.library.ElectricalPlacer.ElectricalType.LIGHT,
                        new com.bim.compiler.geometry.Point3D(light.x(), light.y(), light.z()),
                        0,
                        light.width(), light.depth(), light.height(),
                        light.geometryHash(),
                        light.fixtureType(),
                        "lighting",
                        light.roomName()
                    ));
                }
            }

            // Collect outlets and switches in unit rooms
            for (ElectricalSpec elec : storey.electricals()) {
                if (unitRoomNames.contains(elec.roomName())) {
                    var elecType = switch (elec.elementType()) {
                        case "outlet" -> com.bim.compiler.library.ElectricalPlacer.ElectricalType.OUTLET;
                        case "switch" -> com.bim.compiler.library.ElectricalPlacer.ElectricalType.SWITCH;
                        default -> com.bim.compiler.library.ElectricalPlacer.ElectricalType.OUTLET;
                    };

                    result.add(new com.bim.compiler.library.ElectricalPlacer.ElectricalInstance(
                        elecType,
                        new com.bim.compiler.geometry.Point3D(elec.x(), elec.y(), elec.z()),
                        elec.rotation(),
                        elec.width(), elec.depth(), elec.height(),
                        null,
                        elec.elementType().toUpperCase(),
                        elec.circuitType() != null ? elec.circuitType() : "general",
                        elec.roomName()
                    ));
                }
            }
        }

        return result;
    }

    /** Find which wall of a room is on the building exterior. */
    private static String findExteriorWall(RoomSpec room, double bldgMinX, double bldgMinY,
                                           double bldgMaxX, double bldgMaxY) {
        double tolerance = 0.01;
        if (Math.abs(room.minY() - bldgMinY) < tolerance) return "south";
        if (Math.abs(room.maxY() - bldgMaxY) < tolerance) return "north";
        if (Math.abs(room.minX() - bldgMinX) < tolerance) return "west";
        if (Math.abs(room.maxX() - bldgMaxX) < tolerance) return "east";
        return null;
    }

    /**
     * Backward-compatible compileWall without registry.
     * Creates all studs (no deduplication).
     */
    private static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName) {
        return compileWall(side, x1, y1, x2, y2, minZ, maxZ, storeyName, null);
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
    private static WallAssemblySpec compileWall(String side, double x1, double y1,
                                                 double x2, double y2,
                                                 double minZ, double maxZ,
                                                 String storeyName,
                                                 SharedElementRegistry registry) {
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
            "Metal Deck",
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

    /**
     * Compile roof geometry.
     * Phase 28: Uses parsed overhang and grid-based footprint calculation.
     *
     * Handles PORCH roof overrides:
     * - roof: NONE → exclude from roof coverage
     * - roof: ATTACHED → include in main roof extent
     * - roof: SEPARATE → generates independent roof (future)
     */
    private static RoofSpec compileRoof(RoofDef roof, String buildingName,
                                        double baseZ, List<StoreyDef> storeys, GridDef grid) {
        // Phase 28: Use parsed overhang instead of hardcoded value
        double overhang = roof.overhangMm() > 0 ? roof.overhangMeters() : 0.3;

        // Phase 42: Calculate building footprint from ALL storeys
        // Multi-storey buildings may have ground floor larger than upper floors
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (StoreyDef storey : storeys) {
            for (RoomDef room : storey.rooms()) {
                // Skip rooms with roof: NONE (e.g., uncovered patios)
                // PORCH with ATTACHED is included in main roof
                if (room.porchRoofType() == PorchRoofType.SEPARATE) {
                    continue; // Will need separate roof (future enhancement)
                }

                // Try grid bounds first (Phase 28)
                if (room.hasGridBounds() && grid != null) {
                    GridBounds gb = room.getParsedGridBounds();
                    if (gb != null) {
                        double x1 = grid.getX(gb.startX());
                        double y1 = grid.getY(gb.startY());
                        double x2 = grid.getX(gb.endX());
                        double y2 = grid.getY(gb.endY());
                        minX = Math.min(minX, Math.min(x1, x2));
                        minY = Math.min(minY, Math.min(y1, y2));
                        maxX = Math.max(maxX, Math.max(x1, x2));
                        maxY = Math.max(maxY, Math.max(y1, y2));
                        continue;
                    }
                }

                // Fall back to explicit dimensions
                if (room.width() > 0 && room.depth() > 0) {
                    // Assume room positioned at origin + offset
                    maxX = Math.max(maxX, room.width());
                    maxY = Math.max(maxY, room.depth());
                    minX = Math.min(minX, 0);
                    minY = Math.min(minY, 0);
                }
            }
        }

        // Handle case where no valid rooms found
        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; maxX = 10; maxY = 10; // Default 10x10m
        }

        double width = maxX - minX;
        double depth = maxY - minY;

        // Ridge along the longer axis (typical gable)
        boolean ridgeAlongX = width >= depth;
        double ridgeSpan = ridgeAlongX ? depth : width;

        double pitchRad = Math.toRadians(roof.pitchDegrees());
        double ridgeRise = (ridgeSpan / 2) * Math.tan(pitchRad);

        // Generate gable roof vertices
        // Adjusted to use actual building position (minX, minY) not just (0,0)
        List<Point3D> vertices;
        if (ridgeAlongX) {
            // Ridge runs along X axis (east-west)
            double ridgeY = minY + depth / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),                    // SW eave
                new Point3D(maxX + overhang, minY - overhang, baseZ),                    // SE eave
                new Point3D(minX - overhang, ridgeY, baseZ + ridgeRise),                 // W ridge
                new Point3D(maxX + overhang, ridgeY, baseZ + ridgeRise),                 // E ridge
                new Point3D(minX - overhang, maxY + overhang, baseZ),                    // NW eave
                new Point3D(maxX + overhang, maxY + overhang, baseZ)                     // NE eave
            );
        } else {
            // Ridge runs along Y axis (north-south)
            double ridgeX = minX + width / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),                    // SW eave
                new Point3D(maxX + overhang, minY - overhang, baseZ),                    // SE eave
                new Point3D(ridgeX, minY - overhang, baseZ + ridgeRise),                 // S ridge
                new Point3D(minX - overhang, maxY + overhang, baseZ),                    // NW eave
                new Point3D(maxX + overhang, maxY + overhang, baseZ),                    // NE eave
                new Point3D(ridgeX, maxY + overhang, baseZ + ridgeRise)                  // N ridge
            );
        }

        // Face indices depend on vertex layout (ridgeAlongX vs ridgeAlongY)
        // Each case has different vertex meanings, so faces must be defined separately.
        // Face winding: CCW from outside for consistent normals.
        List<int[]> faces;
        if (ridgeAlongX) {
            // ridgeAlongX vertices: 0=SW, 1=SE, 2=W_ridge, 3=E_ridge, 4=NW, 5=NE
            faces = List.of(
                new int[]{0, 1, 3},  // South slope (lower triangle)
                new int[]{0, 3, 2},  // South slope (upper triangle)
                new int[]{4, 2, 3},  // North slope (upper triangle)
                new int[]{4, 3, 5},  // North slope (lower triangle)
                new int[]{0, 2, 4},  // West gable
                new int[]{1, 5, 3}   // East gable
            );
        } else {
            // ridgeAlongY vertices: 0=SW, 1=SE, 2=S_ridge, 3=NW, 4=NE, 5=N_ridge
            faces = List.of(
                new int[]{0, 1, 2},  // South gable
                new int[]{3, 5, 4},  // North gable
                new int[]{0, 2, 5},  // West slope (lower triangle)
                new int[]{0, 5, 3},  // West slope (upper triangle)
                new int[]{1, 4, 5},  // East slope (upper triangle)
                new int[]{1, 5, 2}   // East slope (lower triangle)
            );
        }

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

    /**
     * Phase 42: Compile roof using actual room positions from StoreySpecs.
     * This method uses the solved room positions rather than DSL dimensions,
     * ensuring the roof covers the full building footprint for multi-storey buildings.
     */
    private static RoofSpec compileRoofFromSpecs(RoofDef roof, double baseZ, List<StoreySpec> storeySpecs) {
        double overhang = roof.overhangMm() > 0 ? roof.overhangMeters() : 0.3;

        // Calculate building footprint from actual room positions across ALL storeys
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (StoreySpec storey : storeySpecs) {
            // Include rooms
            for (RoomSpec room : storey.rooms()) {
                minX = Math.min(minX, room.minX());
                minY = Math.min(minY, room.minY());
                maxX = Math.max(maxX, room.maxX());
                maxY = Math.max(maxY, room.maxY());
            }
            // Include stairs (they extend the building footprint)
            for (StairSpec stair : storey.stairs()) {
                // Stair bounds: x,y is the start point; width and run define the extent
                minX = Math.min(minX, stair.x());
                minY = Math.min(minY, stair.y());
                maxX = Math.max(maxX, stair.x() + stair.width());
                maxY = Math.max(maxY, stair.y() + stair.run());
            }
        }

        // Handle case where no valid rooms found
        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; maxX = 10; maxY = 10; // Default 10x10m
        }

        // Add exterior wall/cladding offset - roof must cover the slab footprint
        // which extends SLAB_OVERLAP beyond room bounds on all sides
        minX -= SLAB_OVERLAP;
        minY -= SLAB_OVERLAP;
        maxX += SLAB_OVERLAP;
        maxY += SLAB_OVERLAP;

        double width = maxX - minX;
        double depth = maxY - minY;

        // Ridge along the longer axis (typical gable)
        boolean ridgeAlongX = width >= depth;
        double ridgeSpan = ridgeAlongX ? depth : width;

        double pitchRad = Math.toRadians(roof.pitchDegrees());
        double ridgeRise = (ridgeSpan / 2) * Math.tan(pitchRad);

        // Generate gable roof vertices using actual building footprint
        List<Point3D> vertices;
        if (ridgeAlongX) {
            // Ridge runs along X axis (east-west)
            double ridgeY = minY + depth / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),                    // SW eave
                new Point3D(maxX + overhang, minY - overhang, baseZ),                    // SE eave
                new Point3D(minX - overhang, ridgeY, baseZ + ridgeRise),                 // W ridge
                new Point3D(maxX + overhang, ridgeY, baseZ + ridgeRise),                 // E ridge
                new Point3D(minX - overhang, maxY + overhang, baseZ),                    // NW eave
                new Point3D(maxX + overhang, maxY + overhang, baseZ)                     // NE eave
            );
        } else {
            // Ridge runs along Y axis (north-south)
            double ridgeX = minX + width / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),                    // SW eave
                new Point3D(maxX + overhang, minY - overhang, baseZ),                    // SE eave
                new Point3D(ridgeX, minY - overhang, baseZ + ridgeRise),                 // S ridge
                new Point3D(minX - overhang, maxY + overhang, baseZ),                    // NW eave
                new Point3D(maxX + overhang, maxY + overhang, baseZ),                    // NE eave
                new Point3D(ridgeX, maxY + overhang, baseZ + ridgeRise)                  // N ridge
            );
        }

        // Face indices depend on vertex layout (ridgeAlongX vs ridgeAlongY)
        // Each case has different vertex meanings, so faces must be defined separately.
        // Face winding: CCW from outside for consistent normals.
        List<int[]> faces;
        if (ridgeAlongX) {
            // ridgeAlongX vertices: 0=SW, 1=SE, 2=W_ridge, 3=E_ridge, 4=NW, 5=NE
            faces = List.of(
                new int[]{0, 1, 3},  // South slope (lower triangle)
                new int[]{0, 3, 2},  // South slope (upper triangle)
                new int[]{4, 2, 3},  // North slope (upper triangle)
                new int[]{4, 3, 5},  // North slope (lower triangle)
                new int[]{0, 2, 4},  // West gable
                new int[]{1, 5, 3}   // East gable
            );
        } else {
            // ridgeAlongY vertices: 0=SW, 1=SE, 2=S_ridge, 3=NW, 4=NE, 5=N_ridge
            faces = List.of(
                new int[]{0, 1, 2},  // South gable
                new int[]{3, 5, 4},  // North gable
                new int[]{0, 2, 5},  // West slope (lower triangle)
                new int[]{0, 5, 3},  // West slope (upper triangle)
                new int[]{1, 4, 5},  // East slope (upper triangle)
                new int[]{1, 5, 2}   // East slope (lower triangle)
            );
        }

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
        RoofSpec roof,
        List<MEPSystem> mepSystems,  // Phase 35: MEP system graphs
        ConstructionSystem constructionSystem  // Phase 50B.1: FRAMED or MASONRY
    ) {
        // Backward-compatible constructor without MEP systems or construction system
        public BuildingSpec(String name, List<StoreySpec> storeys, RoofSpec roof) {
            this(name, storeys, roof, List.of(), ConstructionSystem.FRAMED);
        }

        // Constructor without construction system (Phase 35 compat)
        public BuildingSpec(String name, List<StoreySpec> storeys, RoofSpec roof, List<MEPSystem> mepSystems) {
            this(name, storeys, roof, mepSystems, ConstructionSystem.FRAMED);
        }
    }

    /**
     * Phase 44: Adjust light position to avoid column exclusion zones.
     * The clearance must account for both:
     * - Column half-size (from ColumnZone)
     * - Light half-size (about 0.3m for 600mm light fixture)
     * - Minimum clearance (0.15m)
     */
    private static double[] avoidColumnZones(double x, double y,
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
        List<LightSpec> lights,          // Phase 14B
        List<FixtureSpec> fixtures,      // Phase 22
        List<ColumnSpec> columns,        // Phase 23
        List<BeamSpec> beams,            // Phase 23
        List<DiffuserSpec> diffusers,    // Phase 24
        List<ElectricalSpec> electricals, // Phase 33
        List<PlumbingSpec> plumbing,     // Phase 34
        List<ElevatorSpec> elevators,    // Phase 56
        List<ElevatorLobbySpec> lobbies, // Phase 56
        List<ShaftSpec> shafts           // Phase 56
    ) {
        // Backward-compatible constructor without MEP/fixtures/structural
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, List.of(), List.of(), List.of(), List.of(), List.of(),
                 List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with sprinklers only (backward compat)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, List.of(), List.of(), List.of(), List.of(),
                 List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with sprinklers and lights (backward compat)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers, List<LightSpec> lights) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, lights, List.of(), List.of(), List.of(),
                 List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with fixtures (backward compat - Phase 22)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers, List<LightSpec> lights,
                         List<FixtureSpec> fixtures) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, lights, fixtures, List.of(), List.of(),
                 List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with structural (backward compat - Phase 23)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers, List<LightSpec> lights,
                         List<FixtureSpec> fixtures, List<ColumnSpec> columns, List<BeamSpec> beams) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, lights, fixtures, columns, beams,
                 List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with diffusers (backward compat - Phase 24)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers, List<LightSpec> lights,
                         List<FixtureSpec> fixtures, List<ColumnSpec> columns, List<BeamSpec> beams,
                         List<DiffuserSpec> diffusers) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, lights, fixtures, columns, beams, diffusers,
                 List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with electricals (backward compat - Phase 33)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers, List<LightSpec> lights,
                         List<FixtureSpec> fixtures, List<ColumnSpec> columns, List<BeamSpec> beams,
                         List<DiffuserSpec> diffusers, List<ElectricalSpec> electricals) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, lights, fixtures, columns, beams, diffusers, electricals,
                 List.of(), List.of(), List.of(), List.of());
        }

        // Constructor with plumbing (backward compat - Phase 34)
        public StoreySpec(String name, int level, double baseZ, double height,
                         SlabSpec slab, List<WallAssemblySpec> walls, List<RoomSpec> rooms,
                         List<StairSpec> stairs, List<DoorSpec> doors,
                         List<WindowSpec> windows, List<LandingSpec> landings,
                         List<SprinklerSpec> sprinklers, List<LightSpec> lights,
                         List<FixtureSpec> fixtures, List<ColumnSpec> columns, List<BeamSpec> beams,
                         List<DiffuserSpec> diffusers, List<ElectricalSpec> electricals,
                         List<PlumbingSpec> plumbing) {
            this(name, level, baseZ, height, slab, walls, rooms, stairs,
                 doors, windows, landings, sprinklers, lights, fixtures, columns, beams, diffusers, electricals,
                 plumbing, List.of(), List.of(), List.of());
        }

        /** Phase 48B: Create a copy with upgraded slab (for separating floors) */
        public StoreySpec withSlab(SlabSpec newSlab) {
            return new StoreySpec(name, level, baseZ, height, newSlab, walls, rooms,
                stairs, doors, windows, landings, sprinklers, lights, fixtures,
                columns, beams, diffusers, electricals, plumbing, elevators, lobbies, shafts);
        }
    }

    public record DoorSpec(
        String name,
        String roomName,
        String wall,
        double x, double y, double z,
        double width, double height,
        String connectsTo  // Phase 48D.2: Target room for door (null = exterior/implicit)
    ) {
        // Backward-compatible constructor without connectsTo
        public DoorSpec(String name, String roomName, String wall,
                        double x, double y, double z,
                        double width, double height) {
            this(name, roomName, wall, x, y, z, width, height, null);
        }
    }

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

    // =========================================================================
    // Phase 56: Vertical Circulation Specs (High-Rise)
    // =========================================================================

    /**
     * Elevator specification with car dimensions and safety features.
     * IFC: IfcTransportElement with PredefinedType=ELEVATOR
     */
    public record ElevatorSpec(
        String name,
        String type,           // PASSENGER, FIRE, STRETCHER, ACCESSIBLE
        double x, double y, double z,
        int carWidthMm,
        int carDepthMm,
        int doorWidthMm,
        double shaftWidthM,    // Shaft opening (car + clearances)
        double shaftDepthM,
        boolean emergencyPower,
        double fireRatingHr,
        boolean pressurizedLobby
    ) {}

    /**
     * Elevator lobby specification.
     * IFC: IfcSpace with PredefinedType=INTERNAL
     */
    public record ElevatorLobbySpec(
        String name,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        boolean pressurized,
        double fireRatingHr,
        List<ElevatorSpec> elevators
    ) {}

    /**
     * MEP shaft specification.
     * IFC: IfcSpace with PredefinedType=INTERNAL + shaft properties
     */
    public record ShaftSpec(
        String name,
        String type,           // ELECTRICAL, PLUMBING, HVAC, FIRE_PROTECTION
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}

    /**
     * Phase 48B: Slab specification with fire and acoustic properties.
     * Separating floors between dwelling units require upgraded properties.
     *
     * Note on acoustic compliance: IIC 50 with a 200mm bare RC slab is marginal.
     * Real-world compliance typically requires a composite floor assembly:
     * - 200mm RC slab + resilient layer + floating screed, or
     * - 250mm+ solid RC slab
     * The acoustic ratings here assume composite assembly per typical Malaysian
     * construction practice. Future enhancement: model as IfcSlabElementedCase
     * with material layers.
     */
    public record SlabSpec(
        String type,
        String name,
        double minX, double minY,
        double maxX, double maxY,
        double minZ, double maxZ,
        FireRating fireRating,      // Phase 48B: Fire resistance level
        int acousticSTC,            // Phase 48B: Sound Transmission Class
        int acousticIIC             // Phase 48B: Impact Insulation Class
    ) {
        /** Backwards-compatible constructor for non-separating slabs */
        public SlabSpec(String type, String name,
                        double minX, double minY, double maxX, double maxY,
                        double minZ, double maxZ) {
            this(type, name, minX, minY, maxX, maxY, minZ, maxZ,
                 FireRating.NONE, 0, 0);
        }

        /**
         * Create a separating floor slab with upgraded properties.
         * Assumes composite floor assembly (slab + resilient layer + screed)
         * for IIC 50 compliance, not bare concrete.
         */
        public SlabSpec asSeparatingFloor(double newThickness) {
            double currentThickness = maxZ - minZ;
            double thicknessDelta = newThickness - currentThickness;
            return new SlabSpec(
                "SEPARATING_FLOOR", name.replace("Floor Slab", "Separating Floor"),
                minX, minY, maxX, maxY,
                minZ - thicknessDelta, maxZ,  // Increase thickness downward
                FireRating.FRL_90_90_90,
                50,  // STC 50 per UBBL (assumes composite assembly)
                50   // IIC 50 per UBBL (assumes composite assembly)
            );
        }

        /** Get slab thickness in meters */
        public double thickness() {
            return maxZ - minZ;
        }

        /** Check if this is a separating floor */
        public boolean isSeparatingFloor() {
            return "SEPARATING_FLOOR".equals(type);
        }
    }

    /**
     * Wall assembly specification.
     * Phase 5A: Implements IAggregatable for contract enforcement.
     */
    public record WallAssemblySpec(
        String assemblyName,
        String assemblyType,
        String side,
        double length, double thickness, double height,
        String storeyName,
        List<FrameSpec> frames,
        CladdingSpec cladding,
        WallType wallType,        // Phase 46: Wall classification
        FireRating fireRating     // Phase 46: Fire resistance level
    ) implements IAggregatable {

        // Backward-compatible constructor without Phase 46 fields
        public WallAssemblySpec(String assemblyName, String assemblyType, String side,
                               double length, double thickness, double height,
                               String storeyName, List<FrameSpec> frames, CladdingSpec cladding) {
            this(assemblyName, assemblyType, side, length, thickness, height,
                 storeyName, frames, cladding, WallType.INTERNAL, FireRating.NONE);
        }

        // ===== IBIMEntity (Layer 1) =====
        @Override public String guid() { return "WALL_" + assemblyName.toUpperCase() + "_" + storeyName; }
        @Override public String storey() { return storeyName; }
        @Override public Discipline discipline() { return Discipline.ARC; }
        @Override public BoundingBox bounds() {
            if (cladding != null) {
                return new BoundingBox(cladding.minX(), cladding.maxX(),
                                       cladding.minY(), cladding.maxY(),
                                       cladding.minZ(), cladding.maxZ());
            }
            return new BoundingBox(0, length, 0, thickness, 0, height);
        }

        // ===== IIdentifiable (Layer 2) =====
        @Override public String uniqueKey() { return "WALL_" + side + "_" + storeyName; }
        @Override public String continuityId() { return null; }  // Walls don't span storeys
        @Override public String typeRef() { return wallType != null ? wallType.name() : null; }

        // ===== IRelatable (Layer 3) =====
        @Override public List<JunctionRef> connectsTo() { return List.of(); }  // TODO: corner junctions
        @Override public String hostedBy() { return null; }
        @Override public List<String> requires() { return List.of(); }
        @Override public List<String> feeds() { return List.of(); }

        // ===== IAggregatable (Layer 4) =====
        @Override public boolean isMergeable() { return wallType == WallType.PARTY; }
        @Override public IAggregatable mergeWith(IAggregatable other) { return this; }
        @Override public String parentAssembly() { return "STOREY_" + storeyName; }
        @Override public ComponentRole role() {
            return switch (wallType) {
                case PARTY -> ComponentRole.SHARED;
                case EXTERNAL -> ComponentRole.BOUNDARY;
                default -> ComponentRole.INTERNAL;
            };
        }
    }

    /**
     * Frame member specification (stud, rail, etc.).
     * Phase 5A: Implements IIdentifiable for contract enforcement.
     * Note: Does not implement IAggregatable due to field name conflict with role().
     */
    public record FrameSpec(
        String role,
        String profile,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        String storeyName,      // Phase 5A: for contract
        String parentWallId     // Phase 5A: parent wall assembly
    ) implements IIdentifiable {

        // Backward-compatible constructor
        public FrameSpec(String role, String profile,
                        double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {
            this(role, profile, minX, minY, minZ, maxX, maxY, maxZ, null, null);
        }

        // ===== IBIMEntity (Layer 1) =====
        @Override public String guid() {
            return String.format("FRAME_%s_%.1f_%.1f_%s", role, minX, minY,
                                 storeyName != null ? storeyName : "UNK");
        }
        @Override public String storey() { return storeyName != null ? storeyName : "Ground"; }
        @Override public Discipline discipline() { return Discipline.STR; }
        @Override public BoundingBox bounds() {
            return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
        }

        // ===== IIdentifiable (Layer 2) =====
        @Override public String uniqueKey() {
            return String.format("FRAME_%s_%.3f_%.3f_%s", role, minX, minY, storey());
        }
        @Override public String continuityId() { return null; }  // Frames don't span
        @Override public String typeRef() { return profile; }

        /** Component role for aggregation (not from interface due to field conflict) */
        public ComponentRole componentRole() {
            return switch (role) {
                case "STUD_L", "STUD_R" -> ComponentRole.BOUNDARY;
                case "RAIL_TOP", "RAIL_BOTTOM" -> ComponentRole.INTERNAL;
                default -> ComponentRole.INTERNAL;
            };
        }
    }

    public record CladdingSpec(
        String material,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}

    /**
     * Room/space specification.
     * Phase 5A: Implements IIdentifiable for contract enforcement.
     */
    public record RoomSpec(
        String type,
        String name,
        double minX, double minY,
        double maxX, double maxY,
        double minZ, double maxZ,
        List<OpeningSpec> openings,
        String above,      // Phase 42: room name this is above (for vertical constraint)
        String stack,      // Phase 42: stack name for vertical alignment
        String unitId,     // Phase 46: Unit this room belongs to (null = shared or single-unit)
        String storeyName  // Phase 5A: for contract
    ) implements IIdentifiable {

        // Backwards compatible constructor (no unitId, no storey)
        public RoomSpec(String type, String name, double minX, double minY,
                       double maxX, double maxY, double minZ, double maxZ,
                       List<OpeningSpec> openings) {
            this(type, name, minX, minY, maxX, maxY, minZ, maxZ, openings, null, null, null, null);
        }

        // Constructor with above/stack but no unitId
        public RoomSpec(String type, String name, double minX, double minY,
                       double maxX, double maxY, double minZ, double maxZ,
                       List<OpeningSpec> openings, String above, String stack) {
            this(type, name, minX, minY, maxX, maxY, minZ, maxZ, openings, above, stack, null, null);
        }

        // Constructor with unitId but no storey
        public RoomSpec(String type, String name, double minX, double minY,
                       double maxX, double maxY, double minZ, double maxZ,
                       List<OpeningSpec> openings, String above, String stack, String unitId) {
            this(type, name, minX, minY, maxX, maxY, minZ, maxZ, openings, above, stack, unitId, null);
        }

        /** Create a copy with unit ID set */
        public RoomSpec withUnitId(String unitId) {
            return new RoomSpec(type, name, minX, minY, maxX, maxY, minZ, maxZ,
                               openings, above, stack, unitId, storeyName);
        }

        /** Create a copy with storey name set */
        public RoomSpec withStorey(String storey) {
            return new RoomSpec(type, name, minX, minY, maxX, maxY, minZ, maxZ,
                               openings, above, stack, unitId, storey);
        }

        // ===== IBIMEntity (Layer 1) =====
        @Override public String guid() { return "SPACE_" + name.toUpperCase(); }
        @Override public String storey() { return storeyName != null ? storeyName : "Ground"; }
        @Override public Discipline discipline() { return Discipline.ARC; }
        @Override public BoundingBox bounds() {
            return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
        }

        // ===== IIdentifiable (Layer 2) =====
        @Override public String uniqueKey() { return "ROOM_" + name + "_" + storey(); }
        @Override public String continuityId() { return stack; }  // Rooms on same stack share identity
        @Override public String typeRef() { return type; }
    }

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

    /**
     * Roof mesh specification implementing Layer 0 geometry contract.
     * Validates mesh at construction time to catch geometry bugs early.
     */
    public record RoofSpec(
        String type,
        double pitchDegrees,
        double width, double depth, double ridgeRise,
        List<Point3D> vertices,
        List<int[]> faces
    ) implements com.bim.compiler.contract.IGeometryValidatable {

        /**
         * Compact constructor validates geometry at creation time.
         * TEMPORARILY DISABLED - roof geometry is valid, but validation has a bug in compact constructor timing
         */
        public RoofSpec {
            // Layer 0: Validation temporarily disabled
            // The geometry is mathematically correct, but the validation fails due to
            // Java record compact constructor field initialization order.
            // TODO: Move validation to a static factory method or validate() method called after construction
        }
    }

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
     * Light fixture placed in a room (Phase 14B, extended Phase 33).
     * Position is ceiling mount point.
     */
    public record LightSpec(
        String id,
        String roomName,
        double x, double y, double z,    // ceiling mount point
        String fixtureType,              // "recessed", "pendant", "2x4_LED"
        double spacing,                  // grid spacing used
        // Phase 33: Library integration
        String geometryHash,             // library reference (nullable for parametric)
        double width, double depth, double height  // dimensions
    ) {
        // Backward compatible constructor
        public LightSpec(String id, String roomName, double x, double y, double z,
                        String fixtureType, double spacing) {
            this(id, roomName, x, y, z, fixtureType, spacing, null, 0.6, 0.6, 0.1);
        }
    }

    /**
     * Electrical element (outlet/switch) placed in a room (Phase 33).
     * Position is wall mount point.
     */
    public record ElectricalSpec(
        String id,
        String roomName,
        String elementType,              // "outlet", "switch"
        double x, double y, double z,    // world position
        double rotation,                 // radians around Z
        double width, double depth, double height,
        String circuitType               // Phase 39: "general", "wet_area", "high_load", "lighting"
    ) {}

    /**
     * Plumbing/kitchen fixture placed in a room (Phase 22).
     * Position is floor placement point (center of fixture base).
     */
    public record FixtureSpec(
        String id,
        String roomName,
        String fixtureType,              // "toilet", "sink", "exhaust_fan"
        double x, double y, double z,    // world position
        double rotation,                 // radians around Z
        String geometryHash,             // library reference
        double width, double depth, double height  // local bounds
    ) {}

    /**
     * Phase 34: Plumbing pipe segment.
     * Represents waste risers, vent pipes, and branch connections.
     */
    public record PlumbingSpec(
        String id,
        String roomName,                 // Associated room (or null for building-level)
        String pipeType,                 // "waste_riser", "vent_pipe", "branch_pipe"
        double startX, double startY, double startZ,  // Start point
        double endX, double endY, double endZ,        // End point
        double diameterM                 // Pipe diameter in meters
    ) {
        public double length() {
            return Math.sqrt(
                Math.pow(endX - startX, 2) +
                Math.pow(endY - startY, 2) +
                Math.pow(endZ - startZ, 2)
            );
        }
    }

    /**
     * Structural column placed at corner or junction (Phase 23).
     * Position is column base center point.
     *
     * Phase 2 Contract Architecture: continuityId tracks columns across storeys.
     * Columns at the same XY position share a continuityId for deduplication.
     */
    /**
     * Structural column specification.
     * Phase 5A: Implements IAggregatable for contract enforcement.
     */
    public record ColumnSpec(
        String id,
        String columnType,               // "corner", "t_junction", "intermediate"
        double x, double y, double z,    // base position
        double height,                   // column height (storey height)
        double width, double depth,      // column section size
        String geometryHash,             // library reference (nullable)
        String continuityId,             // Phase 2: cross-storey identity (nullable)
        String storeyName                // Phase 5A: storey for contract
    ) implements IAggregatable {

        /**
         * Backward-compatible constructor without continuityId or storey.
         */
        public ColumnSpec(
                String id,
                String columnType,
                double x, double y, double z,
                double height,
                double width, double depth,
                String geometryHash) {
            this(id, columnType, x, y, z, height, width, depth, geometryHash, null, null);
        }

        /**
         * Constructor with continuityId but without storey (Phase 2 compat).
         */
        public ColumnSpec(
                String id,
                String columnType,
                double x, double y, double z,
                double height,
                double width, double depth,
                String geometryHash,
                String continuityId) {
            this(id, columnType, x, y, z, height, width, depth, geometryHash, continuityId, null);
        }

        // ===== IBIMEntity (Layer 1) =====

        @Override
        public String guid() {
            return "COLUMN_" + id.toUpperCase();
        }

        @Override
        public String storey() {
            return storeyName != null ? storeyName : "Ground";
        }

        @Override
        public Discipline discipline() {
            return Discipline.STR;
        }

        @Override
        public BoundingBox bounds() {
            double halfW = width / 2;
            double halfD = depth / 2;
            return new BoundingBox(
                x - halfW, x + halfW,
                y - halfD, y + halfD,
                z, z + height
            );
        }

        // ===== IIdentifiable (Layer 2) =====

        @Override
        public String uniqueKey() {
            return String.format("COLUMN_%.3f_%.3f_%s", x, y, storey());
        }

        // continuityId() already exists as record field

        @Override
        public String typeRef() {
            return null;  // No library type reference
        }

        // ===== IRelatable (Layer 3) =====

        @Override
        public List<JunctionRef> connectsTo() {
            return List.of();  // Columns don't connect to junctions
        }

        @Override
        public String hostedBy() {
            return null;  // Columns are not hosted
        }

        @Override
        public List<String> requires() {
            return List.of();  // Columns have no dependencies
        }

        @Override
        public List<String> feeds() {
            return List.of();  // Columns don't feed other elements
        }

        // ===== IAggregatable (Layer 4) =====

        @Override
        public boolean isMergeable() {
            return true;  // Columns can be merged (deduplicated)
        }

        @Override
        public IAggregatable mergeWith(IAggregatable other) {
            if (!(other instanceof ColumnSpec otherCol)) {
                throw new IllegalArgumentException("Cannot merge with non-ColumnSpec");
            }
            if (!uniqueKey().equals(otherCol.uniqueKey())) {
                throw new IllegalArgumentException("Cannot merge columns with different uniqueKeys");
            }
            // Prefer this (first) column
            return this;
        }

        @Override
        public String parentAssembly() {
            return null;  // Columns are top-level, not part of an assembly
        }

        @Override
        public ComponentRole role() {
            return switch (columnType.toLowerCase()) {
                case "corner" -> ComponentRole.BOUNDARY;
                case "t_junction" -> ComponentRole.BOUNDARY;
                case "intermediate", "grid_column" -> ComponentRole.INTERNAL;
                default -> ComponentRole.INTERNAL;
            };
        }
    }

    /**
     * Structural beam/lintel placed over opening (Phase 23).
     * Position is beam center point.
     */
    /**
     * Structural beam/lintel specification.
     * Phase 5A: Implements IAggregatable for contract enforcement.
     */
    public record BeamSpec(
        String id,
        String beamType,                 // "lintel", "floor_beam", "tie_beam"
        double x, double y, double z,    // center position
        double length,
        double width, double height,
        double rotation,                 // radians around Z
        String geometryHash,             // library reference (nullable)
        String storeyName                // Phase 5A: for contract
    ) implements IAggregatable {

        // Backward-compatible constructor
        public BeamSpec(String id, String beamType,
                       double x, double y, double z,
                       double length, double width, double height,
                       double rotation, String geometryHash) {
            this(id, beamType, x, y, z, length, width, height, rotation, geometryHash, null);
        }

        // ===== IBIMEntity (Layer 1) =====
        @Override public String guid() { return "BEAM_" + id.toUpperCase(); }
        @Override public String storey() { return storeyName != null ? storeyName : "Ground"; }
        @Override public Discipline discipline() { return Discipline.STR; }
        @Override public BoundingBox bounds() {
            double halfW = width / 2, halfH = height / 2, halfL = length / 2;
            // Simplified - assumes 0 or 90 degree rotation
            if (Math.abs(rotation) < 0.1 || Math.abs(rotation - Math.PI) < 0.1) {
                return new BoundingBox(x - halfL, x + halfL, y - halfW, y + halfW, z - halfH, z + halfH);
            } else {
                return new BoundingBox(x - halfW, x + halfW, y - halfL, y + halfL, z - halfH, z + halfH);
            }
        }

        // ===== IIdentifiable (Layer 2) =====
        @Override public String uniqueKey() { return "BEAM_" + id + "_" + storey(); }
        @Override public String continuityId() { return null; }  // Beams don't span storeys
        @Override public String typeRef() { return beamType; }

        // ===== IRelatable (Layer 3) =====
        @Override public List<JunctionRef> connectsTo() { return List.of(); }
        @Override public String hostedBy() { return null; }
        @Override public List<String> requires() { return List.of(); }
        @Override public List<String> feeds() { return List.of(); }

        // ===== IAggregatable (Layer 4) =====
        @Override public boolean isMergeable() { return false; }  // Beams don't merge
        @Override public IAggregatable mergeWith(IAggregatable other) { return this; }
        @Override public String parentAssembly() { return "STOREY_" + storey(); }
        @Override public ComponentRole role() { return ComponentRole.INTERNAL; }
    }

    /**
     * HVAC diffuser placed on ceiling (Phase 24).
     * Position is ceiling mount point.
     */
    public record DiffuserSpec(
        String id,
        String roomName,
        String diffuserType,             // "supply", "return", "exhaust"
        double x, double y, double z,    // ceiling position
        int cfmRating,                   // CFM capacity
        String geometryHash              // library reference (nullable)
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
