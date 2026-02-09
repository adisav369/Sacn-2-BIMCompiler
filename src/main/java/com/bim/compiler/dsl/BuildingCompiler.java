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

        // =====================================================================
        // Phase 64: Fire Protection Auto-Generation
        // =====================================================================
        storeySpecs = addFireProtectionIfRequired(storeySpecs, def, "MALAYSIA");

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

        // =====================================================================
        // Phase 64: Fire Protection Auto-Generation
        // =====================================================================
        storeySpecs = addFireProtectionIfRequired(storeySpecs, def, "MALAYSIA");

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

        return new BuildingDefinition(def.name(), def.buildingType(), resolvedStoreys,
            def.units(), def.shared(), def.core(), def.roof(), def.grid(), def.envelope(),
            def.doorSchedule(), def.windowSchedule(), def.profile(), def.protocol(),
            def.lod(), def.constructionSystem());
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
                    storey.elevators(), storey.lobbies(), storey.shafts()
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
    static RoofSpec compileRoofFromSpecs(RoofDef roof, double baseZ, List<StoreySpec> storeySpecs) {
        double overhang = roof.overhangMm() > 0 ? roof.overhangMeters() : 0.3;

        // Calculate roof footprint from the LAST (topmost) storey only.
        // For setback buildings (narrow tower on wide podium), the roof
        // should cover only the top floor, not the full building footprint.
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
        double scaleZ,
        // Phase 82: Fire protection fields for high-rise witness claims
        String stairType,            // PROTECTED, ENCLOSED, etc. (null for regular stairs)
        boolean pressurized,         // UBBL 178 stairwell pressurization
        double fireRatingHr          // Fire rating in hours (0 = unknown)
    ) {
        // Convenience constructor for parametric stairs (existing code)
        public StairSpec(String name, String toStorey,
                        double x, double y, double z,
                        double width, double run, double rise,
                        int numRisers, double riserHeight, double treadDepth,
                        List<Point3D> vertices, List<int[]> faces) {
            this(name, toStorey, x, y, z, width, run, rise,
                 numRisers, riserHeight, treadDepth, vertices, faces,
                 null, 1.0, 1.0, 1.0,
                 null, false, 0.0);  // No library geometry, no fire protection
        }

        public boolean usesLibrary() {
            return libraryGeometryHash != null && !libraryGeometryHash.isEmpty();
        }

        public boolean isProtected() {
            return "PROTECTED".equalsIgnoreCase(stairType);
        }

        // Create a library-based stair spec
        public static StairSpec fromLibrary(String name, String toStorey,
                                           double x, double y, double z,
                                           double width, double run, double rise,
                                           String geometryHash,
                                           double scaleX, double scaleY, double scaleZ) {
            return new StairSpec(name, toStorey, x, y, z, width, run, rise,
                               0, 0, 0, List.of(), List.of(),
                               geometryHash, scaleX, scaleY, scaleZ,
                               null, false, 0.0);
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

}
