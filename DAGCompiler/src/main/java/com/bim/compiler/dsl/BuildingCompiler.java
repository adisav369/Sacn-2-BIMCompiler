package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.contract.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.dsl.BuildingSpecs.*;
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
    /** Phase RM/A9: Separating slab from BIMConstants (was local 0.20 hardcode) */
    private static final double SEPARATING_SLAB_THICKNESS = BIMConstants.SEPARATING_SLAB_THICKNESS;

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
            return MultiUnitCompiler.compile(def, outputDir);
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

            StoreySpec spec = StoreyCompiler.compileStorey(storey, currentZ, isGround, isTop, def, registry);
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
        storeySpecs.forEach(BuildingCompiler::countStoreyElements);

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

        // =====================================================================
        // Phase 64: Fire Protection Auto-Generation
        // =====================================================================
        storeySpecs = addFireProtectionIfRequired(storeySpecs, def, "MALAYSIA");

        // =====================================================================
        // Phase 100: Fire Detection Auto-Generation (Standards Resolution)
        // =====================================================================
        storeySpecs = addFireDetectionIfRequired(storeySpecs, def, "MALAYSIA");

        BuildingSpec spec = new BuildingSpec(def.name(), storeySpecs, roofSpec, mepSystems, def.constructionSystem(), def.profile());

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

        return new BuildingSpec(buildingName, storeySpecs, null, List.of(), ConstructionSystem.FRAMED, null);
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
        // Phase 111: Handle multi-unit buildings via compile() which delegates to MultiUnitCompiler
        if (def.isMultiUnit()) {
            BuildingSpec spec = compile(def, outputDir);
            ValidatorChain.ValidationReport report = validate(spec, def);
            return new CompilationResult(spec, report);
        }

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

            StoreySpec spec = StoreyCompiler.compileStorey(storey, currentZ, isGround, isTop, def, registry);
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

        storeySpecs.forEach(BuildingCompiler::countStoreyElements);

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

        // =====================================================================
        // Phase 64: Fire Protection Auto-Generation
        // =====================================================================
        storeySpecs = addFireProtectionIfRequired(storeySpecs, def, "MALAYSIA");

        // =====================================================================
        // Phase 100: Fire Detection Auto-Generation (Standards Resolution)
        // =====================================================================
        storeySpecs = addFireDetectionIfRequired(storeySpecs, def, "MALAYSIA");

        BuildingSpec spec = new BuildingSpec(def.name(), storeySpecs, roofSpec, mepSystems, def.constructionSystem(), def.profile());
        ValidatorChain.ValidationReport report = validate(spec, def);

        return new CompilationResult(spec, report);
    }



    // =========================================================================
    // Layer 3: Constraint Resolution (Phase 15)
    // =========================================================================

    /**
     * Check if any rooms need solver placement, and resolve constraints if needed.
     * Phase 16: Supports cross-storey alignment via solvedPositions map.
     * Phase 17: Adds above:/below:/stack: constraints.
     */
    static BuildingDefinition resolveConstraints(BuildingDefinition def) {
        return resolveConstraints(def, Map.of());
    }

    /**
     * Phase 48A: Resolve constraints with cross-unit positions available.
     * For STACKED layouts, this allows above: constraints to reference rooms in other units.
     *
     * @param def Building definition to resolve
     * @param crossUnitPositions Map of room name → position from other units
     */
    static BuildingDefinition resolveConstraints(BuildingDefinition def,
                                                          Map<String, double[]> crossUnitPositions) {
        // Phase 102B: Compute building-wide BOM type bounds.
        // Rooms whose type matches a BOM zone skip solver — StoreyCompiler resolves them.
        Set<String> bomResolvedTypes = Set.of();
        if (def.grid() != null) {
            for (StoreyDef s : def.storeys()) {
                if (s.floorBom() != null && !s.floorBom().isEmpty()) {
                    var gi = FloorPlateBOMResolver.gridInfoFromGridDef(def.grid());
                    var resolver = StoreyCompiler.getFloorBomResolver();
                    bomResolvedTypes = resolver.resolveTypeBoundsMap(s.floorBom(), gi).keySet();
                    break;
                }
            }
        }

        final Set<String> bomTypes = bomResolvedTypes;
        boolean needsSolver = false;
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                if (room.needsSolverPlacement()
                        && !bomTypes.contains(room.type().toUpperCase())) {
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
                                                           stackPositions, roomStoreyLevel, bomTypes);
            resolvedStoreys.add(resolved);
        }

        return new BuildingDefinition(def.name(), def.buildingType(), resolvedStoreys,
            def.units(), def.shared(), def.core(), def.roof(), def.grid(), def.envelope(),
            def.doorSchedule(), def.windowSchedule(), def.profile(), def.protocol(),
            def.lod(), def.constructionSystem(), def.facade());
    }

    /**
     * Resolve constraints for a single storey.
     * Phase 16: Accepts allSolvedPositions for cross-storey alignment.
     * Phase 17: Adds above:/below:/stack: constraint handling.
     */
    private static StoreyDef resolveStoreyConstraints(StoreyDef storey,
                                                       Map<String, GridPosition> allSolvedPositions,
                                                       Map<String, GridPosition> stackPositions,
                                                       Map<String, Integer> roomStoreyLevel,
                                                       Set<String> bomTypes) {
        // Check if this storey has any rooms needing solver (exclude BOM-resolved types)
        boolean hasConstrainedRooms = storey.rooms().stream()
            .anyMatch(r -> r.needsSolverPlacement()
                        && !bomTypes.contains(r.type().toUpperCase()));

        if (!hasConstrainedRooms) {
            return storey;
        }

        System.out.println("[SOLVER] Resolving storey: " + storey.name());

        // Phase 16+17: Handle rooms with vertical dependency separately
        // These get their position from the target room/stack
        List<RoomDef> verticallyDependent = new ArrayList<>();
        List<RoomDef> needsSolving = new ArrayList<>();

        for (RoomDef room : storey.rooms()) {
            if (room.needsSolverPlacement()
                    && !bomTypes.contains(room.type().toUpperCase())) {
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

        // Phase 111: Collect storey room names for adjacency filtering
        Set<String> storeyRoomNames = new HashSet<>();
        for (RoomDef r : storey.rooms()) storeyRoomNames.add(r.name());

        for (RoomDef room : storey.rooms()) {
            roomDefMap.put(room.name(), room);

            if (room.needsSolverPlacement()) {
                // Phase 25 fix: Add ALL rooms needing placement to constraints
                // Vertically dependent rooms get position from their dependency,
                // but they must be in the solver's roomMap for adjacency lookups
                List<String> adjTo = new ArrayList<>(room.adjacentTo());
                List<String> notAdjTo = new ArrayList<>(room.notAdjacentTo());

                // Phase 111: Filter out references to rooms not in this storey
                // (e.g., SHARED landings referenced via adjacent: from unit rooms)
                adjTo.removeIf(name -> !storeyRoomNames.contains(name));
                notAdjTo.removeIf(name -> !storeyRoomNames.contains(name));

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
            } else if (room.needsSolverPlacement()
                       && !bomTypes.contains(room.type().toUpperCase())) {
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
            resolvedRooms, storey.stairs(), storey.landings(),
            storey.elevators(), storey.lobbies(), storey.shafts(),
            storey.floorBom()
        );
    }

    /**
     * Parse grid position (e.g., "A1" -> [0, 0], "C5" -> [2, 4]).
     * Column letter = X position (meters), Row number = Y position (meters).
     */
    static int[] parseGridPosition(String gridPos) {
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

    /** Phase 105: Count all elements in a storey for outlier rate calculation. */
    static void countStoreyElements(StoreySpec storey) {
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

    /**
     * Phase 56B: Parse grid position into axis labels for GridDef lookup.
     * "C1" -> ["C", "1"], "D5" -> ["D", "5"]
     */
    static String[] parseGridLabels(String gridPos) {
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
    static List<MEPSystem> buildMEPSystems(List<StoreySpec> storeys) {
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

    // =========================================================================
    // Phase 64: Fire Protection Auto-Generation
    // =========================================================================

    /**
     * Add fire protection (sprinklers) to storeys if AD triggers require it.
     *
     * MATHS:
     * - Trigger: height >= 18m OR floor_area >= 1000m²
     * - Coverage: ceil(floor_area / 18.6) = heads per storey
     * - Spacing: sqrt(18.6) ≈ 4.3m < 4.6m max
     *
     * @param storeySpecs Compiled storeys
     * @param def Building definition
     * @param jurisdiction MALAYSIA or INTERNATIONAL
     * @return Modified storeys with sprinklers added (if required)
     */
    static List<StoreySpec> addFireProtectionIfRequired(
            List<StoreySpec> storeySpecs,
            BuildingDefinition def,
            String jurisdiction) {

        if (storeySpecs.isEmpty()) return storeySpecs;

        // Phase DE-3e: Skip heuristic fire protection for metadata-driven buildings
        // (sprinklers/piping come from placement metadata, not auto-generation)
        if (PlacementAD.getInstance().hasPlacement(def.name())) {
            return storeySpecs;
        }

        // Calculate building parameters
        double buildingHeight = 0;
        double totalFloorArea = 0;
        for (StoreySpec storey : storeySpecs) {
            buildingHeight += storey.height();
            for (RoomSpec room : storey.rooms()) {
                double roomWidth = room.maxX() - room.minX();
                double roomDepth = room.maxY() - room.minY();
                totalFloorArea += roomWidth * roomDepth;
            }
        }
        int storeyCount = storeySpecs.size();

        // Determine occupancy from building type/name (default to E for schools, R for residential)
        String occupancy = "R";  // Default residential
        String nameLower = def.name().toLowerCase();
        if (nameLower.contains("school") || nameLower.contains("sekolah") ||
            nameLower.contains("education") || nameLower.contains("class")) {
            occupancy = "E";  // Educational
        } else if (nameLower.contains("office") || nameLower.contains("pejabat")) {
            occupancy = "B";  // Business
        } else if (nameLower.contains("assembly") || nameLower.contains("hall") || nameLower.contains("dewan")) {
            occupancy = "A";  // Assembly
        }

        // Use FireProtectionResolver to check if sprinklers required
        FireProtectionResolver resolver = new FireProtectionResolver(jurisdiction);

        // Create room bounds for resolver
        List<List<FireProtectionResolver.RoomBounds>> storeyRoomBounds = new ArrayList<>();
        for (StoreySpec storey : storeySpecs) {
            List<FireProtectionResolver.RoomBounds> roomBounds = new ArrayList<>();
            // Phase 85: Sprinkler Z from BOM metadata (BELOW_SLAB rule)
            BOMRuleAD.BOMPlacementParams fpHeadParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "HEAD");
            double sprinklerZ = fpHeadParams.resolveZ(storey.baseZ(), storey.height(), SLAB_THICKNESS);
            for (RoomSpec room : storey.rooms()) {
                // Phase 92B: Stair enclosures now get sprinklers (full coverage)
                roomBounds.add(new FireProtectionResolver.RoomBounds(
                    room.name(),
                    room.minX(), room.minY(),
                    room.maxX(), room.maxY(),
                    sprinklerZ
                ));
            }
            storeyRoomBounds.add(roomBounds);
        }

        // Resolve sprinklers for each storey
        List<StoreySpec> result = new ArrayList<>();
        int totalSprinklers = 0;

        for (int i = 0; i < storeySpecs.size(); i++) {
            StoreySpec storey = storeySpecs.get(i);
            List<FireProtectionResolver.RoomBounds> roomBounds = storeyRoomBounds.get(i);

            // Get storey floor area
            double storeyArea = roomBounds.stream()
                .mapToDouble(rb -> (rb.maxX() - rb.minX()) * (rb.maxY() - rb.minY()))
                .sum();

            List<SprinklerSpec> sprinklers = resolver.resolveSprinklers(
                storey.name(),
                roomBounds,
                buildingHeight,
                storeyArea,
                occupancy
            );

            if (!sprinklers.isEmpty()) {
                // Get rooms that already have DSL-specified sprinklers
                Set<String> roomsWithSprinklers = storey.sprinklers().stream()
                    .map(SprinklerSpec::roomName)
                    .collect(java.util.stream.Collectors.toSet());

                // Filter out auto-generated sprinklers for rooms that already have them
                List<SprinklerSpec> newSprinklers = sprinklers.stream()
                    .filter(s -> !roomsWithSprinklers.contains(s.roomName()))
                    .toList();

                // Merge with existing sprinklers
                List<SprinklerSpec> allSprinklers = new ArrayList<>(storey.sprinklers());
                allSprinklers.addAll(newSprinklers);
                totalSprinklers += newSprinklers.size();

                // Create new StoreySpec with added sprinklers
                result.add(new StoreySpec(
                    storey.name(), storey.level(), storey.baseZ(), storey.height(),
                    storey.slab(), storey.walls(), storey.rooms(), storey.stairs(),
                    storey.doors(), storey.windows(), storey.landings(),
                    allSprinklers, storey.lights(), storey.fixtures(),
                    storey.columns(), storey.beams(), storey.diffusers(),
                    storey.electricals(), storey.plumbing(),
                    storey.elevators(), storey.lobbies(), storey.shafts(), storey.alarms(),
                    storey.baySlabs()
                ));
            } else {
                result.add(storey);
            }
        }

        if (totalSprinklers > 0) {
            System.out.printf("[FP] Fire protection: %d sprinklers added (height=%.1fm, area=%.0fm², occupancy=%s)%n",
                totalSprinklers, buildingHeight, totalFloorArea, occupancy);
        }

        return result;
    }

    // =========================================================================
    // Phase 100: Fire Detection Auto-Generation (Standards Resolution Engine)
    // =========================================================================

    /**
     * Phase 100: Add fire detection elements (smoke detectors, alarm bells, break glass)
     * if required by building code triggers in AD tables.
     * Pattern mirrors addFireProtectionIfRequired.
     */
    static List<StoreySpec> addFireDetectionIfRequired(
            List<StoreySpec> storeySpecs,
            BuildingDefinition def,
            String jurisdiction) {

        if (storeySpecs.isEmpty()) return storeySpecs;

        // Phase DE-2: Skip heuristic fire detection for metadata-driven buildings
        if (PlacementAD.getInstance().hasPlacement(def.name())) {
            return storeySpecs;
        }

        // Calculate building parameters
        double buildingHeight = 0;
        double totalFloorArea = 0;
        for (StoreySpec storey : storeySpecs) {
            buildingHeight += storey.height();
            for (RoomSpec room : storey.rooms()) {
                double roomWidth = room.maxX() - room.minX();
                double roomDepth = room.maxY() - room.minY();
                totalFloorArea += roomWidth * roomDepth;
            }
        }
        int storeyCount = storeySpecs.size();

        // Determine occupancy (reuse same logic as fire protection)
        String occupancy = "R";
        String nameLower = def.name().toLowerCase();
        if (nameLower.contains("school") || nameLower.contains("sekolah") ||
            nameLower.contains("education") || nameLower.contains("class")) {
            occupancy = "E";
        } else if (nameLower.contains("office") || nameLower.contains("pejabat")) {
            occupancy = "B";
        } else if (nameLower.contains("assembly") || nameLower.contains("hall") || nameLower.contains("dewan")) {
            occupancy = "A";
        }

        // Evaluate triggers
        StandardsResolver resolver = new StandardsResolver();
        resolver.evaluateTriggers(storeyCount, buildingHeight, totalFloorArea, occupancy, jurisdiction);

        if (!resolver.isDetectionRequired() && !resolver.isAlarmRequired()) {
            return storeySpecs;
        }

        List<StoreySpec> result = new ArrayList<>();
        int totalAlarms = 0;

        for (StoreySpec storey : storeySpecs) {
            double ceilingZ = storey.baseZ() + storey.height() - BIMConstants.STANDARD_SLAB_THICKNESS;
            List<AlarmSpec> allAlarms = new ArrayList<>(storey.alarms());

            for (RoomSpec room : storey.rooms()) {
                List<AlarmSpec> roomAlarms = resolver.resolveForRoom(
                    room, ceilingZ, storey.baseZ(), storey.name());
                allAlarms.addAll(roomAlarms);
            }

            if (!allAlarms.isEmpty() && allAlarms.size() != storey.alarms().size()) {
                totalAlarms += allAlarms.size() - storey.alarms().size();
                result.add(new StoreySpec(
                    storey.name(), storey.level(), storey.baseZ(), storey.height(),
                    storey.slab(), storey.walls(), storey.rooms(), storey.stairs(),
                    storey.doors(), storey.windows(), storey.landings(),
                    storey.sprinklers(), storey.lights(), storey.fixtures(),
                    storey.columns(), storey.beams(), storey.diffusers(),
                    storey.electricals(), storey.plumbing(),
                    storey.elevators(), storey.lobbies(), storey.shafts(), allAlarms,
                    storey.baySlabs()
                ));
            } else {
                result.add(storey);
            }
        }

        if (totalAlarms > 0) {
            System.out.printf("[STANDARDS] Fire detection: %d alarms added " +
                "(height=%.1fm, area=%.0fm², occupancy=%s, detection=%s, alarm=%s)%n",
                totalAlarms, buildingHeight, totalFloorArea, occupancy,
                resolver.isDetectionRequired(), resolver.isAlarmRequired());
        }

        return result;
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

    private static RoofSpec compileRoof(RoofDef roof, String buildingName,
                                        double baseZ, List<StoreyDef> storeys, GridDef grid) {
        // Phase 42: Calculate building footprint from ALL storeys
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (StoreyDef storey : storeys) {
            for (RoomDef room : storey.rooms()) {
                if (room.porchRoofType() == PorchRoofType.SEPARATE) {
                    continue;
                }
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
                if (room.width() > 0 && room.depth() > 0) {
                    maxX = Math.max(maxX, room.width());
                    maxY = Math.max(maxY, room.depth());
                    minX = Math.min(minX, 0);
                    minY = Math.min(minY, 0);
                }
            }
        }

        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; maxX = 10; maxY = 10;
        }

        return generateGableRoof(roof, baseZ, minX, minY, maxX, maxY);
    }

    /**
     * Phase 42: Compile roof using actual room positions from StoreySpecs.
     * This method uses the solved room positions rather than DSL dimensions,
     * ensuring the roof covers the full building footprint for multi-storey buildings.
     */
    static RoofSpec compileRoofFromSpecs(RoofDef roof, double baseZ, List<StoreySpec> storeySpecs) {
        // Calculate roof footprint from the LAST (topmost) storey only.
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        StoreySpec topStorey = storeySpecs.get(storeySpecs.size() - 1);
        for (RoomSpec room : topStorey.rooms()) {
            minX = Math.min(minX, room.minX());
            minY = Math.min(minY, room.minY());
            maxX = Math.max(maxX, room.maxX());
            maxY = Math.max(maxY, room.maxY());
        }
        for (StairSpec stair : topStorey.stairs()) {
            minX = Math.min(minX, stair.x());
            minY = Math.min(minY, stair.y());
            maxX = Math.max(maxX, stair.x() + stair.width());
            maxY = Math.max(maxY, stair.y() + stair.run());
        }

        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; maxX = 10; maxY = 10;
        }

        // Roof must cover the slab footprint (extends SLAB_OVERLAP beyond room bounds)
        minX -= SLAB_OVERLAP;
        minY -= SLAB_OVERLAP;
        maxX += SLAB_OVERLAP;
        maxY += SLAB_OVERLAP;

        return generateGableRoof(roof, baseZ, minX, minY, maxX, maxY);
    }

    /** Phase 105: Shared gable roof geometry generation. */
    private static RoofSpec generateGableRoof(RoofDef roof, double baseZ,
                                               double minX, double minY, double maxX, double maxY) {
        double overhang = roof.overhangMm() > 0 ? roof.overhangMeters() : 0.3;
        double width = maxX - minX;
        double depth = maxY - minY;

        boolean ridgeAlongX = width >= depth;
        double ridgeSpan = ridgeAlongX ? depth : width;
        double pitchRad = Math.toRadians(roof.pitchDegrees());
        double ridgeRise = (ridgeSpan / 2) * Math.tan(pitchRad);

        List<Point3D> vertices;
        if (ridgeAlongX) {
            double ridgeY = minY + depth / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),
                new Point3D(maxX + overhang, minY - overhang, baseZ),
                new Point3D(minX - overhang, ridgeY, baseZ + ridgeRise),
                new Point3D(maxX + overhang, ridgeY, baseZ + ridgeRise),
                new Point3D(minX - overhang, maxY + overhang, baseZ),
                new Point3D(maxX + overhang, maxY + overhang, baseZ)
            );
        } else {
            double ridgeX = minX + width / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),
                new Point3D(maxX + overhang, minY - overhang, baseZ),
                new Point3D(ridgeX, minY - overhang, baseZ + ridgeRise),
                new Point3D(minX - overhang, maxY + overhang, baseZ),
                new Point3D(maxX + overhang, maxY + overhang, baseZ),
                new Point3D(ridgeX, maxY + overhang, baseZ + ridgeRise)
            );
        }

        List<int[]> faces;
        if (ridgeAlongX) {
            faces = List.of(
                new int[]{0, 1, 3}, new int[]{0, 3, 2},
                new int[]{4, 2, 3}, new int[]{4, 3, 5},
                new int[]{0, 2, 4}, new int[]{1, 5, 3}
            );
        } else {
            faces = List.of(
                new int[]{0, 1, 2}, new int[]{3, 5, 4},
                new int[]{0, 2, 5}, new int[]{0, 5, 3},
                new int[]{1, 4, 5}, new int[]{1, 5, 2}
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


    // Records moved to BuildingSpecs.java in Phase 114

    // =========================================================================
    // Test
    // =========================================================================

}
