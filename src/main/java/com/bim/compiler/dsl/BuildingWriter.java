package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.bom.BOMAssemblerAD;
import com.bim.compiler.bom.WallOpeningAssembler;
import com.bim.compiler.dsl.BuildingSpecs.*;
import static com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.library.FireSuppressionPlacer;
import com.bim.compiler.library.FireSuppressionPlacer.FPPipeSpec;
import com.bim.compiler.system.*;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Writes BuildingSpec to database and exports to IFC-ready JSON.
 *
 * Phase 103: Delegates to sub-writers for bus-factor reduction.
 * - ElementPersistence: Low-level DB write helpers
 * - StructuralWriter: Walls, columns, beams, roof
 * - StairWriter: Stairs, landings, stair assemblies
 * - OpeningWriter: Doors, windows
 * - MEPWriter: Sprinklers, lights, diffusers, fixtures, pipes, alarms, MEP systems
 */
public class BuildingWriter {

    private final Connection conn;
    private final ElementPersistence ep;
    private final StructuralWriter structural;
    private final StairWriter stairs;
    private final OpeningWriter openings;
    private final MEPWriter mep;
    private final CoveringWriter coverings;
    private final RailingWriter railings;
    private final FloorTypeAD floorTypeAD;
    private DoorWindowLibraryMapper libraryMapper;

    public BuildingWriter(Connection conn) {
        this.conn = conn;
        this.ep = new ElementPersistence(conn);

        // Try to initialize library mappers
        DoorWindowLibraryMapper libMapper = null;
        StairLibraryMapper stairLibMapper = null;
        try {
            libMapper = new DoorWindowLibraryMapper();
        } catch (Exception e) {
            System.out.println("[BuildingWriter] Door/Window library mapper not available: " + e.getMessage());
        }
        try {
            stairLibMapper = new StairLibraryMapper();
        } catch (Exception e) {
            System.out.println("[BuildingWriter] Stair library mapper not available: " + e.getMessage());
        }

        this.libraryMapper = libMapper;
        this.structural = new StructuralWriter(ep, conn);
        this.stairs = new StairWriter(ep, conn, stairLibMapper, libMapper);
        this.openings = new OpeningWriter(ep, conn, libMapper);
        this.mep = new MEPWriter(ep, conn, libMapper);
        this.coverings = new CoveringWriter(ep);
        this.railings = new RailingWriter(ep);
        this.floorTypeAD = new FloorTypeAD();
    }

    /**
     * Initialize database schema (same as ShedWriter).
     */
    public void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS assembly_components");
            stmt.execute("DROP TABLE IF EXISTS element_assemblies");
            stmt.execute("DROP TABLE IF EXISTS element_instances");
            stmt.execute("DROP TABLE IF EXISTS elements_rtree");
            stmt.execute("DROP TABLE IF EXISTS elements_meta");
            stmt.execute("DROP TABLE IF EXISTS base_geometries");
            stmt.execute("DROP TABLE IF EXISTS spatial_structure");

            stmt.execute("""
                CREATE TABLE elements_meta (
                    id INTEGER PRIMARY KEY,
                    guid TEXT UNIQUE NOT NULL,
                    discipline TEXT NOT NULL,
                    ifc_class TEXT NOT NULL,
                    element_name TEXT,
                    element_type TEXT,
                    storey TEXT,
                    fire_rating_hr REAL
                )
            """);

            stmt.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id, minX, maxX, minY, maxY, minZ, maxZ
                )
            """);

            stmt.execute("""
                CREATE TABLE base_geometries (
                    geometry_hash TEXT PRIMARY KEY,
                    vertices BLOB NOT NULL,
                    faces BLOB NOT NULL,
                    vertex_count INTEGER NOT NULL,
                    face_count INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE element_transforms (
                    guid TEXT PRIMARY KEY,
                    center_x REAL NOT NULL,
                    center_y REAL NOT NULL,
                    center_z REAL NOT NULL,
                    transform_source TEXT DEFAULT 'compiled',
                    FOREIGN KEY (guid) REFERENCES elements_meta(guid)
                )
            """);

            stmt.execute("""
                CREATE TABLE element_instances (
                    guid TEXT PRIMARY KEY,
                    geometry_hash TEXT NOT NULL,
                    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
                )
            """);

            stmt.execute("""
                CREATE TABLE element_assemblies (
                    assembly_guid TEXT PRIMARY KEY,
                    assembly_type TEXT NOT NULL,
                    ifc_class TEXT,
                    name TEXT,
                    total_width REAL, total_depth REAL, total_height REAL,
                    storey TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE assembly_components (
                    assembly_guid TEXT,
                    component_guid TEXT,
                    role TEXT,
                    local_x REAL, local_y REAL, local_z REAL,
                    sequence INTEGER,
                    optional BOOLEAN DEFAULT FALSE
                )
            """);

            stmt.execute("""
                CREATE TABLE spatial_structure (
                    guid TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    name TEXT,
                    parent_guid TEXT,
                    object_type TEXT,
                    predefined_type TEXT
                )
            """);

            // Phase 43: Element-to-space containment (IFC IfcRelContainedInSpatialStructure)
            stmt.execute("""
                CREATE TABLE rel_contained_in_space (
                    element_guid TEXT PRIMARY KEY,
                    space_guid TEXT NOT NULL,
                    FOREIGN KEY (space_guid) REFERENCES spatial_structure(guid)
                )
            """);

            // Phase 45: Room Area BOM view for ERP integration
            stmt.execute("DROP VIEW IF EXISTS room_areas");
            stmt.execute("""
                CREATE VIEW room_areas AS
                SELECT
                    s.name AS room,
                    ROUND((r.maxX - r.minX) * (r.maxY - r.minY), 2) AS area_m2,
                    REPLACE(s.parent_guid, 'STOREY_', '') AS storey,
                    s.object_type AS room_type,
                    s.predefined_type AS ifc_type,
                    r.minX, r.minY, r.maxX, r.maxY
                FROM spatial_structure s
                JOIN elements_meta m ON s.guid = m.guid
                JOIN elements_rtree r ON m.id = r.id
                WHERE s.type = 'IfcSpace'
            """);

            // Phase 45: Aggregate views for BOM reporting
            stmt.execute("DROP VIEW IF EXISTS area_by_storey");
            stmt.execute("""
                CREATE VIEW area_by_storey AS
                SELECT storey, SUM(area_m2) AS total_area_m2, COUNT(*) AS room_count
                FROM room_areas
                GROUP BY storey
            """);

            stmt.execute("DROP VIEW IF EXISTS area_by_type");
            stmt.execute("""
                CREATE VIEW area_by_type AS
                SELECT room_type, SUM(area_m2) AS total_area_m2, COUNT(*) AS room_count
                FROM room_areas
                GROUP BY room_type
            """);

            stmt.execute("DROP VIEW IF EXISTS building_summary");
            stmt.execute("""
                CREATE VIEW building_summary AS
                SELECT
                    (SELECT COUNT(DISTINCT storey) FROM room_areas) AS storey_count,
                    (SELECT COUNT(*) FROM room_areas) AS room_count,
                    (SELECT SUM(area_m2) FROM room_areas) AS total_buildup_m2
            """);

            // Phase 35: MEP System tables
            stmt.execute("DROP TABLE IF EXISTS system_edges");
            stmt.execute("DROP TABLE IF EXISTS system_nodes");
            stmt.execute("DROP TABLE IF EXISTS mep_systems");

            stmt.execute("""
                CREATE TABLE mep_systems (
                    system_id TEXT PRIMARY KEY,
                    system_type TEXT NOT NULL,
                    building_guid TEXT NOT NULL,
                    is_connected INTEGER,
                    is_complete INTEGER,
                    node_count INTEGER,
                    edge_count INTEGER
                )
            """);

            stmt.execute("""
                CREATE TABLE system_nodes (
                    node_id TEXT PRIMARY KEY,
                    system_id TEXT NOT NULL REFERENCES mep_systems(system_id),
                    element_guid TEXT,
                    role TEXT NOT NULL,
                    name TEXT,
                    properties_json TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE system_edges (
                    edge_id TEXT PRIMARY KEY,
                    system_id TEXT NOT NULL REFERENCES mep_systems(system_id),
                    from_node_id TEXT NOT NULL REFERENCES system_nodes(node_id),
                    to_node_id TEXT NOT NULL REFERENCES system_nodes(node_id),
                    edge_type TEXT NOT NULL,
                    properties_json TEXT
                )
            """);

            // Indices for graph traversal
            stmt.execute("CREATE INDEX idx_edges_from ON system_edges(from_node_id)");
            stmt.execute("CREATE INDEX idx_edges_to ON system_edges(to_node_id)");

            // Phase 90: element_properties table for NLP property queries
            stmt.execute("DROP TABLE IF EXISTS element_properties");
            stmt.execute("""
                CREATE TABLE element_properties (
                    guid TEXT NOT NULL,
                    pset_name TEXT NOT NULL,
                    property_name TEXT NOT NULL,
                    property_value TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ep_guid ON element_properties(guid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ep_prop ON element_properties(property_name)");

            // Phase 89: Simple QTO table for NLP search + 5D costing
            stmt.execute("DROP TABLE IF EXISTS simple_qto");
            stmt.execute("""
                CREATE TABLE simple_qto (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    discipline TEXT,
                    ifc_class TEXT,
                    storey TEXT,
                    measurement_type TEXT,
                    element_count INTEGER,
                    total_quantity REAL,
                    uom TEXT,
                    avg_quantity REAL,
                    unit_cost_rm REAL,
                    total_cost_rm REAL
                )
            """);
        }
    }

    /**
     * Write building spec to database.
     */
    public void write(BuildingSpec spec) throws SQLException {
        // Phase 50B.1: Get construction system (default FRAMED for backwards compatibility)
        ConstructionSystem constructionSystem = spec.constructionSystem() != null
            ? spec.constructionSystem()
            : ConstructionSystem.FRAMED;

        // Create building in spatial structure
        String buildingGuid = "BUILDING_" + spec.name().toUpperCase().replace(" ", "_");
        ep.writeSpatialStructure(buildingGuid, "IfcBuilding", spec.name(), null);

        // Phase DE-1: When metadata-driven, suppress surplus compiled writers
        boolean hasMetadata = PlacementAD.getInstance().hasPlacement(spec.name());

        // Phase 49: Collect all landings for stair aggregate processing
        List<LandingSpec> allLandings = new ArrayList<>();
        Map<String, String> landingToStorey = new HashMap<>();
        for (StoreySpec storey : spec.storeys()) {
            for (LandingSpec landing : storey.landings()) {
                allLandings.add(landing);
                landingToStorey.put(landing.name(), storey.name());
            }
        }

        // Track processed landings (those included in stair aggregates)
        Set<String> processedLandings = new HashSet<>();

        // Track processed stair assemblies (to avoid duplicate GUIDs for multi-storey stairs)
        Set<String> processedStairs = new HashSet<>();

        // Phase 122L: Merge walls by XY position for CONTINUOUS span_mode (institutional RC)
        // Residential PER_STOREY buildings skip this entirely (platform framing = walls per floor)
        boolean isContinuousWalls = spec.profile() != null
            && spec.profile().contains("Institutional");
        Map<String, StructuralWriter.SpanningWallInfo> spanningWalls = new LinkedHashMap<>();
        Set<String> writtenSpanningWalls = new HashSet<>();

        if (isContinuousWalls && spec.storeys().size() > 1) {
            for (StoreySpec storey : spec.storeys()) {
                for (WallAssemblySpec wall : storey.walls()) {
                    String key = wallSpanKey(wall);
                    StructuralWriter.SpanningWallInfo existing = spanningWalls.get(key);
                    if (existing == null) {
                        spanningWalls.put(key, new StructuralWriter.SpanningWallInfo(wall, storey.name()));
                    } else {
                        existing.extendTo(wall);
                    }
                }
            }
            int multiStoreyCount = (int) spanningWalls.values().stream()
                .filter(w -> w.storeyCount > 1).count();
            System.out.printf("[PHASE122L] %d wall positions, %d span multiple storeys%n",
                spanningWalls.size(), multiStoreyCount);
        }

        // Phase 4 Contract Architecture: Merge columns by continuityId for cross-storey spanning
        Map<String, StructuralWriter.SpanningColumnInfo> spanningColumns = new LinkedHashMap<>();
        Set<String> writtenContinuityIds = new HashSet<>();

        // Collect all columns and group by continuityId
        for (StoreySpec storey : spec.storeys()) {
            for (ColumnSpec column : storey.columns()) {
                String contId = column.continuityId();
                if (contId != null && !contId.isEmpty()) {
                    StructuralWriter.SpanningColumnInfo existing = spanningColumns.get(contId);
                    if (existing == null) {
                        // First occurrence - create spanning column info
                        spanningColumns.put(contId, new StructuralWriter.SpanningColumnInfo(
                            contId,
                            column.columnType(),
                            column.x(), column.y(),
                            column.z(),                    // Base Z (lowest storey)
                            column.height(),               // Initial height (one storey)
                            column.width(), column.depth(),
                            column.geometryHash(),
                            storey.name()                  // Lowest storey name
                        ));
                    } else {
                        // Extend existing spanning column
                        existing.extendHeight(column.height());
                        existing.addStorey(storey.name());
                    }
                }
            }
        }

        // Log spanning column info for multi-storey buildings
        if (spec.storeys().size() > 1 && !spanningColumns.isEmpty()) {
            int multiStoreyCount = (int) spanningColumns.values().stream()
                .filter(c -> c.storeyCount() > 1).count();
            System.out.printf("[PHASE4] %d columns with continuityId, %d span multiple storeys%n",
                spanningColumns.size(), multiStoreyCount);
        }

        // Write each storey
        for (StoreySpec storey : spec.storeys()) {
            String storeyGuid = "STOREY_" + storey.name().toUpperCase().replace(" ", "_");
            ep.writeSpatialStructure(storeyGuid, "IfcBuildingStorey", storey.name(), buildingGuid);

            // Phase 43: Write IfcSpace for each room
            // Phase DE-1: Skip when metadata-driven — IfcSpace not in reference DBs
            Map<String, String> roomToSpaceGuid = new HashMap<>();
            if (!hasMetadata) {
                for (RoomSpec room : storey.rooms()) {
                    writeSpace(room, storey.name(), storeyGuid);
                    String spaceGuid = "SPACE_" + storey.name().toUpperCase() + "_" + room.name().toUpperCase();
                    roomToSpaceGuid.put(room.name().toLowerCase(), spaceGuid);
                }
            }

            // Write main structural slab (foundation or floor) — null for multi-unit (per-unit slabs in baySlabs)
            if (storey.slab() != null) {
                ep.writeElement(
                    "SLAB_" + storey.name().toUpperCase(),
                    "IfcSlab",
                    storey.slab().name(),
                    storey.slab().type(),
                    storey.name(),
                    ep.createBoxGeometry(
                        storey.slab().minX(), storey.slab().minY(), storey.slab().minZ(),
                        storey.slab().maxX(), storey.slab().maxY(), storey.slab().maxZ()
                    )
                );
            }

            // Phase A: Write additional slabs (bay slabs, ceiling slabs)
            if (storey.baySlabs() != null && !storey.baySlabs().isEmpty()) {
                int bayIdx = 0;
                for (SlabSpec baySlab : storey.baySlabs()) {
                    // Phase A: Ceiling and per-unit foundation/floor slabs are ARC, bay slabs are STR
                    String slabType = baySlab.type();
                    String guidPrefix;
                    if ("CEILING".equals(slabType)) {
                        guidPrefix = "SLAB_CEIL_" + storey.name().toUpperCase();
                    } else if ("FOUNDATION".equals(slabType) || "FLOOR".equals(slabType)) {
                        // Per-unit slab from multi-unit merge — ARC discipline
                        guidPrefix = "SLAB_" + storey.name().toUpperCase() + "_UNIT_" + (++bayIdx);
                    } else {
                        guidPrefix = "SLAB_BAY_" + storey.name().toUpperCase() + "_" + (++bayIdx);
                    }
                    ep.writeElement(
                        guidPrefix,
                        "IfcSlab",
                        baySlab.name(),
                        baySlab.type(),
                        storey.name(),
                        ep.createBoxGeometry(
                            baySlab.minX(), baySlab.minY(), baySlab.minZ(),
                            baySlab.maxX(), baySlab.maxY(), baySlab.maxZ()
                        )
                    );
                }
            }

            // Phase 122J: Write per-room finish slabs from library (Pattern A)
            // Phase DE-1: Skip when metadata-driven — not in reference metadata
            if (!hasMetadata) {
                for (RoomSpec room : storey.rooms()) {
                    double finishThickness = floorTypeAD.getFinishSlabThickness(room.type(), spec.profile());
                    if (finishThickness > 0) {
                        String finishName = floorTypeAD.getFinishFloorName(room.type(), spec.profile());
                        if (finishName == null) finishName = "Finish Floor";
                        String guidSuffix = room.name().toUpperCase().replace(" ", "_")
                            + "_" + storey.name().toUpperCase().replace(" ", "_");
                        ep.writeElement(
                            "SLAB_FINISH_" + guidSuffix,
                            "IfcSlab",
                            finishName,
                            "FINISH",
                            storey.name(),
                            ep.createBoxGeometry(
                                room.minX(), room.minY(), room.minZ(),
                                room.maxX(), room.maxY(), room.minZ() + finishThickness
                            )
                        );
                    }
                }
            }

            // Phase 122J: Write ceiling coverings for each room
            // Phase DE-1: Skip when metadata-driven — not in reference metadata
            if (!hasMetadata) {
                coverings.writeCoverings(storey.rooms(), storey.name(),
                    storey.baseZ(), storey.height());
            }

            // Write walls as assemblies (Phase 122L: spanning walls for CONTINUOUS profiles)
            for (WallAssemblySpec wall : storey.walls()) {
                if (isContinuousWalls && spec.storeys().size() > 1) {
                    String key = wallSpanKey(wall);
                    StructuralWriter.SpanningWallInfo spanning = spanningWalls.get(key);
                    if (spanning != null && spanning.storeyCount > 1) {
                        if (!writtenSpanningWalls.contains(key)) {
                            structural.writeSpanningWall(spanning, key);
                            writtenSpanningWalls.add(key);
                        }
                        continue; // Skip — already written as spanning wall
                    }
                }
                structural.writeWallAssembly(wall, storey.name(), constructionSystem);
            }

            // Phase 49: Write stairs as IFC aggregates
            for (StairSpec stair : storey.stairs()) {
                if (!processedStairs.contains(stair.name())) {
                    Set<String> landingsInAggregate = stairs.writeStairAssembly(stair, allLandings, storey.name());
                    processedLandings.addAll(landingsInAggregate);
                    processedStairs.add(stair.name());
                }
            }

            // Phase 122J: Write stair guard railings
            railings.writeStairRailings(storey.stairs(), storey.name());

            // Write doors
            for (DoorSpec door : storey.doors()) {
                openings.writeDoor(structural.wallAssemblyIndex, door, storey.name());
                String spaceGuid = roomToSpaceGuid.get(door.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String doorGuid = "DOOR_" + door.name().toUpperCase() + "_" + storey.name();
                    ep.writeSpaceContainment(doorGuid, spaceGuid);
                }
            }

            // Write windows
            for (WindowSpec window : storey.windows()) {
                openings.writeWindow(structural.wallAssemblyIndex, window, storey.name());
                String spaceGuid = roomToSpaceGuid.get(window.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String windowGuid = "WINDOW_" + window.name().toUpperCase() + "_" + storey.name();
                    ep.writeSpaceContainment(windowGuid, spaceGuid);
                }
            }

            // Write landings not part of stair aggregates (standalone landings)
            for (LandingSpec landing : storey.landings()) {
                if (!processedLandings.contains(landing.name())) {
                    stairs.writeLanding(landing, storey.name());
                }
            }

            // Phase 92C: Compute riserX so sprinkler heads snap to pipe axis
            double fpRiserX = 0;
            if (!storey.sprinklers().isEmpty()) {
                double sumX = 0;
                for (SprinklerSpec s : storey.sprinklers()) sumX += s.x();
                fpRiserX = sumX / storey.sprinklers().size();
            }

            // Phase 92C: Derive sprinkler Z from federation maths
            double fpSprinklerZ = 0;
            if (!storey.sprinklers().isEmpty()) {
                double slabT = BIMConstants.STANDARD_SLAB_THICKNESS;
                BOMRuleAD.BOMPlacementParams mParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "MAIN");
                double fpMainZ = (storey.baseZ() + storey.height()) - slabT - mParams.zOffset();
                double fpTeeZ = fpMainZ - FireSuppressionPlacer.TEE_BELOW_MAIN;
                fpSprinklerZ = fpTeeZ - FireSuppressionPlacer.HEAD_TOP_BELOW_TEE;
            }

            // Write sprinklers (Phase 14B) with space containment
            for (SprinklerSpec sprinkler : storey.sprinklers()) {
                // Phase 92C: Snap sprinkler X to riserX and Z to federation-derived position
                SprinklerSpec snapped = new SprinklerSpec(
                    sprinkler.id(), sprinkler.roomName(),
                    fpRiserX, sprinkler.y(), fpSprinklerZ,
                    sprinkler.type(), sprinkler.spacing()
                );
                mep.writeSprinkler(snapped, storey.name());
                String spaceGuid = roomToSpaceGuid.get(sprinkler.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String sprinklerGuid = "SPRINKLER_" + storey.name() + "_" + sprinkler.id().toUpperCase();
                    ep.writeSpaceContainment(sprinklerGuid, spaceGuid);
                }
            }

            // Phase 80/85: Write fire suppression piping connecting sprinklers
            if (!storey.sprinklers().isEmpty()) {
                FireSuppressionPlacer fpPlacer = new FireSuppressionPlacer();
                double[] riserPos = fpPlacer.calculateOptimalRiserPosition(storey.sprinklers());
                double slabThickness = BIMConstants.STANDARD_SLAB_THICKNESS;
                BOMRuleAD.BOMPlacementParams mainParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "MAIN");
                List<FPPipeSpec> fpPipes = fpPlacer.generateStoreyPiping(
                    storey.sprinklers(),
                    storey.name(),
                    riserPos[0], riserPos[1],
                    storey.baseZ(), storey.baseZ() + storey.height(),
                    slabThickness, mainParams.zOffset()
                );
                for (FPPipeSpec fpPipe : fpPipes) {
                    // Phase 92C: Route T-assembly parts to LOD400 writer
                    var pipeType = fpPipe.type();
                    if (pipeType == FireSuppressionPlacer.FPPipeType.TEE
                            || pipeType == FireSuppressionPlacer.FPPipeType.TRANSITION
                            || pipeType == FireSuppressionPlacer.FPPipeType.DROP) {
                        mep.writeFPFitting(fpPipe, storey.name());
                    } else {
                        double pipeHeight = Math.abs(fpPipe.end().z() - fpPipe.start().z());
                        if (pipeHeight > storey.height()) continue;
                        mep.writeFPPipeSegment(fpPipe, storey.name());
                    }
                }
            }

            // Write lights (Phase 14B) with space containment
            for (LightSpec light : storey.lights()) {
                mep.writeLight(light, storey.name());
                String spaceGuid = roomToSpaceGuid.get(light.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String lightGuid = "LIGHT_" + storey.name() + "_" + light.id().toUpperCase();
                    ep.writeSpaceContainment(lightGuid, spaceGuid);
                }
            }

            // Write plumbing fixtures (Phase 32) with space containment
            for (FixtureSpec fixture : storey.fixtures()) {
                mep.writeFixture(fixture, storey.name());
                String spaceGuid = roomToSpaceGuid.get(fixture.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String fixtureGuid = "FIXTURE_" + storey.name().toUpperCase() + "_" + fixture.id().toUpperCase();
                    ep.writeSpaceContainment(fixtureGuid, spaceGuid);
                }
            }

            // Write diffusers (Phase 89) with space containment
            for (DiffuserSpec diffuser : storey.diffusers()) {
                mep.writeDiffuser(diffuser, storey.name());
                String spaceGuid = roomToSpaceGuid.get(diffuser.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String diffuserGuid = "DIFFUSER_" + storey.name() + "_" + diffuser.id().toUpperCase();
                    ep.writeSpaceContainment(diffuserGuid, spaceGuid);
                }
            }

            // Write electrical elements (Phase 33) with space containment
            for (ElectricalSpec elec : storey.electricals()) {
                mep.writeElectricalElement(elec, storey.name());
                String spaceGuid = roomToSpaceGuid.get(elec.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String elecGuid = "ELEC_" + elec.roomName().toUpperCase() + "_" + elec.elementType().toUpperCase() + "_" + elec.id() + "_" + storey.name();
                    ep.writeSpaceContainment(elecGuid, spaceGuid);
                }
            }

            // Write plumbing pipes (Phase 34) with space containment
            for (PlumbingSpec pipe : storey.plumbing()) {
                mep.writePipeSegment(pipe, storey.name());
                if (pipe.roomName() != null) {
                    String spaceGuid = roomToSpaceGuid.get(pipe.roomName().toLowerCase());
                    if (spaceGuid != null) {
                        String pipeGuid = "PIPE_" + pipe.roomName().toUpperCase() + "_" + pipe.pipeType().toUpperCase() + "_" + pipe.id() + "_" + storey.name();
                        ep.writeSpaceContainment(pipeGuid, spaceGuid);
                    }
                }
            }

            // Write fire detection alarms (Phase 100) with space containment
            for (AlarmSpec alarm : storey.alarms()) {
                mep.writeAlarm(alarm, storey.name());
                String spaceGuid = roomToSpaceGuid.get(alarm.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String alarmGuid = "ALARM_" + alarm.id().toUpperCase() + "_" + storey.name();
                    ep.writeSpaceContainment(alarmGuid, spaceGuid);
                }
            }

            // Phase 50B.1 + Phase 4: Write structural columns with spanning support
            for (ColumnSpec column : storey.columns()) {
                String contId = column.continuityId();
                if (contId != null && !contId.isEmpty()) {
                    if (!writtenContinuityIds.contains(contId)) {
                        StructuralWriter.SpanningColumnInfo spanning = spanningColumns.get(contId);
                        if (spanning != null && spanning.storeyCount() > 1) {
                            structural.writeSpanningColumn(spanning);
                            writtenContinuityIds.add(contId);
                        } else {
                            structural.writeColumn(column, storey.name());
                            writtenContinuityIds.add(contId);
                        }
                    }
                } else {
                    structural.writeColumn(column, storey.name());
                }
            }

            // Phase 50B.1: Write structural beams/lintels
            for (BeamSpec beam : storey.beams()) {
                structural.writeBeam(beam, storey.name());
            }
        }

        // Write roof — Phase DE-2: metadata buildings get roof from global emission path
        if (spec.roof() != null && !hasMetadata) {
            structural.writeRoof(spec.roof(), spec.storeys().get(spec.storeys().size() - 1).name());
        }

        // Phase B2: Emit global placement elements (non-storey: roof, curtain wall, etc.)
        emitGlobalPlacementElements(spec);

        // Phase 35: Write MEP system graphs
        for (var system : spec.mepSystems()) {
            mep.writeMEPSystem(system, buildingGuid);
        }

        // Phase 81B: Link openings (doors/windows) to wall assemblies
        linkOpeningsToWallAssemblies();

        // Phase 81B: Apply AD-driven BOM recipes (iDempiere M_BOM pattern)
        applyADBOMRecipes();

        // Phase 89: Generate Simple QTO (quantities + costs)
        generateSimpleQTO();

        // Phase 29: Print library usage summary
        printLibraryUsageSummary();
    }

    /**
     * Phase B2: Emit elements from placement metadata for non-compiled storeys.
     * Handles Roof position override, roof-storey slabs, curtain wall panels, etc.
     */
    private void emitGlobalPlacementElements(BuildingSpec spec) throws SQLException {
        PlacementAD pad = PlacementAD.getInstance();
        String buildingName = spec.name();
        if (!pad.hasPlacement(buildingName)) return;

        // IFC classes already handled by StoreyCompiler.applyPlacementOverrides on compiled storeys
        Set<String> perStoreyClasses = Set.of(
            "IfcWall", "IfcSlab", "IfcDoor", "IfcWindow", "IfcFurnishingElement");

        // Collect compiled storey names
        Set<String> compiledStoreys = new HashSet<>();
        for (StoreySpec s : spec.storeys()) {
            compiledStoreys.add(s.name());
        }

        // Get all placements — emit those NOT already handled by per-storey overrides
        List<PlacementAD.Placement> allPlacements = pad.getAll(buildingName);
        int emitted = 0;
        int roofOverrides = 0;

        // Open component library for LOD400 furniture geometry resolution
        ComponentLibrary furnitureLibrary = null;
        try {
            furnitureLibrary = new ComponentLibrary("library/component_library.db");
        } catch (Exception e) {
            // Can't open library — furniture falls back to box geometry
        }

        for (PlacementAD.Placement p : allPlacements) {
            // Skip per-storey classes on compiled storeys (handled by StoreyCompiler)
            if (compiledStoreys.contains(p.storey()) && perStoreyClasses.contains(p.ifcClass())) continue;

            // Discipline-aware GUID prefix mapping
            // Non-ARC: include class in GUID to avoid collisions within same discipline
            // (ordinal is unique per building+storey+class, not per building+storey+discipline)
            String discPrefix = switch (p.discipline()) {
                case "FP"   -> "FP_MD_";
                case "ELEC" -> "ELEC_MD_";
                case "ACMV" -> "ACMV_MD_";
                case "SP"   -> "SP_MD_";
                case "CW"   -> "CW_MD_";
                case "LPG"  -> "LPG_MD_";
                case "STR"  -> "STR_MD_";
                case "MEP"  -> "MEP_MD_";
                default -> "";  // ARC — use existing class-based logic
            };
            String guidPrefix;
            if (!discPrefix.isEmpty()) {
                guidPrefix = discPrefix + p.ifcClass().replace("Ifc", "").toUpperCase() + "_";
            } else {
                // Existing ARC logic
                guidPrefix = switch (p.ifcClass()) {
                    case "IfcColumn" -> "COLUMN_MD_";
                    case "IfcMember" -> "FRAME_MD_";
                    case "IfcSlab"   -> "SLAB_MD_";
                    default          -> "MD_" + p.ifcClass().replace("Ifc", "").toUpperCase() + "_";
                };
            }
            String guid = guidPrefix + p.storey().replace(" ", "_").toUpperCase() + "_" + p.ordinal();

            if ("IfcRoof".equals(p.ifcClass())) {
                overrideRoofPosition(p, roofOverrides, furnitureLibrary);
                roofOverrides++;
            } else if ("IfcFurnishingElement".equals(p.ifcClass()) && libraryMapper != null) {
                // Phase DE-3: Instance-level geometry lookup first, then keyword fallback
                String compHash = null;
                if (furnitureLibrary != null) {
                    try {
                        compHash = furnitureLibrary.resolveGeometryByInstance(
                            buildingName, p.ifcClass(), p.storey(), p.ordinal(), p.elementRef());
                    } catch (SQLException ignored) {}
                }
                if (compHash == null) {
                    compHash = StoreyCompiler.resolveComponentHash(p.elementRef(), furnitureLibrary);
                }
                String geoHash = null;
                if (compHash != null) {
                    double cx = (p.minX() + p.maxX()) / 2;
                    double cy = (p.minY() + p.maxY()) / 2;
                    double translateZ = p.minZ();
                    try {
                        double[] zBounds = libraryMapper.getLocalZBounds(compHash);
                        if (zBounds != null) {
                            translateZ = p.minZ() - zBounds[0];
                        }
                    } catch (SQLException ignored) {}
                    geoHash = libraryMapper.transformAndWriteGeometry(
                        conn, compHash, cx, cy, translateZ, 0.0);
                }
                if (geoHash == null) {
                    geoHash = writeBoxGeometry(p);
                }
                ep.writeElementMeta(guid, p.ifcClass(), p.elementRef(), p.ifcClass(),
                    p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ());
                ep.writeInstance(guid, geoHash);
                emitted++;
            } else {
                String geoHash = writeBoxGeometry(p);
                String type = switch (p.ifcClass()) {
                    case "IfcSlab"   -> "FLOOR";
                    case "IfcPlate"  -> "CURTAIN_PANEL";
                    default          -> p.ifcClass();
                };
                ep.writeElementMeta(guid, p.ifcClass(), p.elementRef(), type,
                    p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ());
                ep.writeInstance(guid, geoHash);
                emitted++;
            }
        }

        if (furnitureLibrary != null) {
            try { furnitureLibrary.close(); } catch (Exception ignored) {}
        }

        if (emitted > 0 || roofOverrides > 0) {
            System.out.printf("[PLACEMENT] Global: emitted %d elements, %d roof overrides from metadata%n",
                emitted, roofOverrides);
        }

        // Fix bounding boxes for metadata-placed doors/windows on compiled storeys.
        // The DoorSpec/WindowSpec → OpeningWriter chain distorts orientation;
        // this post-write step corrects to exact reference positions.
        int fixed = 0;
        for (String compiledStorey : compiledStoreys) {
            fixed += fixOpeningPositions(buildingName, compiledStorey, "IfcDoor", "DOOR_MD_DOOR_");
            fixed += fixOpeningPositions(buildingName, compiledStorey, "IfcWindow", "WINDOW_MD_WIN_");
        }
        if (fixed > 0) {
            System.out.printf("[PLACEMENT] Fixed %d door/window bounding boxes to metadata positions%n", fixed);
        }
    }

    /**
     * Phase B2: Override existing roof element's bounding box and storey to match reference.
     * For the first roof (index 0), updates the existing IfcRoof element.
     * For additional roofs, emits new elements.
     */
    private void overrideRoofPosition(PlacementAD.Placement p, int roofIndex,
                                      ComponentLibrary library) throws SQLException {
        if (roofIndex == 0) {
            // Override existing roof if present, otherwise emit fresh
            boolean found = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT m.id FROM elements_meta m WHERE m.ifc_class = 'IfcRoof' LIMIT 1")) {
                if (rs.next()) {
                    found = true;
                    int id = rs.getInt(1);
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE elements_rtree SET minX=?, maxX=?, minY=?, maxY=?, minZ=?, maxZ=? WHERE id=?")) {
                        ps.setDouble(1, p.minX());
                        ps.setDouble(2, p.maxX());
                        ps.setDouble(3, p.minY());
                        ps.setDouble(4, p.maxY());
                        ps.setDouble(5, p.minZ());
                        ps.setDouble(6, p.maxZ());
                        ps.setInt(7, id);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE elements_meta SET storey=? WHERE id=?")) {
                        ps.setString(1, p.storey());
                        ps.setInt(2, id);
                        ps.executeUpdate();
                    }
                    // Upgrade geometry from library if available
                    String libGeoHash = resolveLibraryGeometry(p, library);
                    if (libGeoHash != null) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE element_instances SET geometry_hash=? WHERE guid=(SELECT guid FROM elements_meta WHERE id=?)")) {
                            ps.setString(1, libGeoHash);
                            ps.setInt(2, id);
                            ps.executeUpdate();
                        }
                    }
                }
            }
            if (!found) {
                // Phase DE-2: No compiled roof to override — emit from metadata
                String guid = "MD_ROOF_" + p.storey().replace(" ", "_").toUpperCase() + "_1";
                String geoHash = resolveLibraryGeometry(p, library);
                if (geoHash == null) geoHash = writeBoxGeometry(p);
                ep.writeElementMeta(guid, "IfcRoof", p.elementRef(), "ROOF",
                    p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ());
                ep.writeInstance(guid, geoHash);
            }
        } else {
            // Additional roofs — emit as new elements
            String guid = "MD_ROOF_" + p.storey().replace(" ", "_").toUpperCase() + "_" + (roofIndex + 1);
            String geoHash = resolveLibraryGeometry(p, library);
            if (geoHash == null) geoHash = writeBoxGeometry(p);
            ep.writeElementMeta(guid, "IfcRoof", p.elementRef(), "ROOF",
                p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ());
            ep.writeInstance(guid, geoHash);
        }
    }

    /**
     * Resolve library geometry for a placement element.
     * Looks up ad_geometry_map by element_ref + ifc_class, then transforms
     * the local-coordinate mesh to world position.
     * Reusable for any element type with extracted reference geometry.
     *
     * @return geometry hash in output DB, or null if no library geometry available
     */
    private String resolveLibraryGeometry(PlacementAD.Placement p,
                                          ComponentLibrary library) throws SQLException {
        if (library == null || libraryMapper == null) return null;

        // Phase DE-3: try instance-level, then type-level
        String refGeoHash = library.resolveGeometryByInstance(
            p.buildingType(), p.ifcClass(), p.storey(), p.ordinal(), p.elementRef());
        if (refGeoHash == null) return null;

        // Compute translation: align local mesh min corner to world min corner
        var localBounds = getLocalBoundsFromLibrary(library, refGeoHash);
        if (localBounds == null) return null;

        double translateX = p.minX() - localBounds[0];
        double translateY = p.minY() - localBounds[2];
        double translateZ = p.minZ() - localBounds[4];

        return libraryMapper.transformAndWriteGeometry(
            conn, refGeoHash, translateX, translateY, translateZ, 0.0);
    }

    /**
     * Get local bounds [minX, maxX, minY, maxY, minZ, maxZ] from component library geometry.
     */
    private double[] getLocalBoundsFromLibrary(ComponentLibrary library, String geoHash) throws SQLException {
        try (PreparedStatement ps = library.getConnection().prepareStatement(
                "SELECT local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z " +
                "FROM component_definitions WHERE geometry_hash = ?")) {
            ps.setString(1, geoHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[] {
                        rs.getDouble(1), rs.getDouble(2),
                        rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6)
                    };
                }
            }
        }
        return null;
    }

    /**
     * Phase B2: Generate simple box geometry from placement bounding box.
     */
    private String writeBoxGeometry(PlacementAD.Placement p) throws SQLException {
        float x0 = (float) p.minX(), x1 = (float) p.maxX();
        float y0 = (float) p.minY(), y1 = (float) p.maxY();
        float z0 = (float) p.minZ(), z1 = (float) p.maxZ();
        float[] vertices = {
            x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0,
            x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1
        };
        int[] faces = {
            0,1,2, 0,2,3, 4,6,5, 4,7,6,
            0,4,5, 0,5,1, 2,6,7, 2,7,3,
            0,3,7, 0,7,4, 1,5,6, 1,6,2
        };
        return ep.writeGeometry(vertices, faces);
    }

    /**
     * Phase B2: Fix bounding boxes for metadata-placed doors/windows.
     * Returns count of elements fixed.
     */
    private int fixOpeningPositions(String buildingName, String storeyName,
                                    String ifcClass, String guidPrefix) throws SQLException {
        PlacementAD pad = PlacementAD.getInstance();
        List<PlacementAD.Placement> placements = pad.get(buildingName, storeyName, ifcClass);
        int fixed = 0;

        for (PlacementAD.Placement p : placements) {
            String guid = guidPrefix + p.ordinal() + "_" + storeyName;

            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT m.id FROM elements_meta m WHERE m.guid = ?")) {
                ps.setString(1, guid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE elements_rtree SET minX=?, maxX=?, minY=?, maxY=?, minZ=?, maxZ=? WHERE id=?")) {
                            up.setDouble(1, p.minX());
                            up.setDouble(2, p.maxX());
                            up.setDouble(3, p.minY());
                            up.setDouble(4, p.maxY());
                            up.setDouble(5, p.minZ());
                            up.setDouble(6, p.maxZ());
                            up.setInt(7, id);
                            up.executeUpdate();
                        }
                        fixed++;
                    }
                }
            }
        }
        return fixed;
    }

    /**
     * Phase 81B: Link doors and windows to their parent wall assemblies.
     */
    private void linkOpeningsToWallAssemblies() {
        try {
            WallOpeningAssembler assembler = new WallOpeningAssembler(conn);
            WallOpeningAssembler.AssemblyResult result = assembler.linkOpeningsToWalls();
            result.print();
        } catch (SQLException e) {
            System.err.println("[BuildingWriter] Wall+Opening assembly failed: " + e.getMessage());
        }
    }

    /**
     * Phase 81B: Apply AD-driven BOM recipes from component_library.db.
     */
    private void applyADBOMRecipes() {
        try {
            BOMAssemblerAD assembler = new BOMAssemblerAD(conn);
            BOMAssemblerAD.Result result = assembler.applyAllRecipes();
            result.print();
            assembler.close();
        } catch (SQLException e) {
            System.err.println("[BuildingWriter] AD BOM assembly failed: " + e.getMessage());
        }
    }

    /**
     * Phase 89: Generate Simple QTO table for NLP search + 5D costing.
     */
    private void generateSimpleQTO() {
        try (Statement stmt = conn.createStatement()) {

            // 1. LINEAR quantities (pipes, beams, columns, members)
            stmt.execute("""
                INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type, element_count, total_quantity, uom, avg_quantity)
                SELECT e.discipline, e.ifc_class, e.storey, 'LINEAR', COUNT(*),
                       ROUND(SUM(r.maxZ - r.minZ), 2),
                       'M',
                       ROUND(AVG(r.maxZ - r.minZ), 2)
                FROM elements_meta e
                JOIN elements_rtree r ON e.id = r.id
                WHERE e.ifc_class IN ('IfcPipeSegment','IfcPipeFitting','IfcBeam','IfcColumn','IfcMember')
                GROUP BY e.discipline, e.ifc_class, e.storey
            """);

            // 2. AREA quantities (walls, slabs, roofs, cladding)
            stmt.execute("""
                INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type, element_count, total_quantity, uom, avg_quantity)
                SELECT e.discipline, e.ifc_class, e.storey, 'AREA', COUNT(*),
                       ROUND(SUM((r.maxX - r.minX) * (r.maxY - r.minY)), 2),
                       'M2',
                       ROUND(AVG((r.maxX - r.minX) * (r.maxY - r.minY)), 2)
                FROM elements_meta e
                JOIN elements_rtree r ON e.id = r.id
                WHERE e.ifc_class IN ('IfcSlab','IfcRoof','IfcCovering','IfcWall','IfcWallStandardCase','IfcPlate')
                GROUP BY e.discipline, e.ifc_class, e.storey
            """);

            // 3. VOLUME quantities (spaces)
            stmt.execute("""
                INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type, element_count, total_quantity, uom, avg_quantity)
                SELECT e.discipline, e.ifc_class, e.storey, 'VOLUME', COUNT(*),
                       ROUND(SUM((r.maxX - r.minX) * (r.maxY - r.minY) * (r.maxZ - r.minZ)), 2),
                       'M3',
                       ROUND(AVG((r.maxX - r.minX) * (r.maxY - r.minY) * (r.maxZ - r.minZ)), 2)
                FROM elements_meta e
                JOIN elements_rtree r ON e.id = r.id
                WHERE e.ifc_class IN ('IfcSpace','IfcFooting','IfcPile')
                GROUP BY e.discipline, e.ifc_class, e.storey
            """);

            // 4. COUNT quantities (doors, windows, fixtures, MEP terminals)
            stmt.execute("""
                INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type, element_count, total_quantity, uom, avg_quantity)
                SELECT e.discipline, e.ifc_class, e.storey, 'COUNT', COUNT(*), COUNT(*), 'EA', 1.0
                FROM elements_meta e
                WHERE e.ifc_class IN (
                    'IfcDoor','IfcWindow','IfcStair','IfcStairFlight',
                    'IfcLightFixture','IfcOutlet','IfcSwitchingDevice',
                    'IfcFireSuppressionTerminal','IfcFlowTerminal',
                    'IfcAirTerminal','IfcFan','IfcFurniture','IfcSanitaryTerminal',
                    'IfcBuildingElementProxy'
                )
                GROUP BY e.discipline, e.ifc_class, e.storey
            """);

            // 5. Apply CIDB Malaysia 2024 unit costs
            Map<String, Double> unitRates = Map.ofEntries(
                Map.entry("IfcPipeSegment", 65.0),   Map.entry("IfcPipeFitting", 120.0),
                Map.entry("IfcBeam", 850.0),          Map.entry("IfcColumn", 920.0),
                Map.entry("IfcMember", 120.0),
                Map.entry("IfcSlab", 280.0),          Map.entry("IfcRoof", 185.0),
                Map.entry("IfcWallStandardCase", 195.0), Map.entry("IfcPlate", 65.0),
                Map.entry("IfcDoor", 1850.0),         Map.entry("IfcWindow", 1200.0),
                Map.entry("IfcLightFixture", 380.0),  Map.entry("IfcOutlet", 85.0),
                Map.entry("IfcSwitchingDevice", 35.0),
                Map.entry("IfcFireSuppressionTerminal", 180.0),
                Map.entry("IfcFlowTerminal", 2500.0),
                Map.entry("IfcAirTerminal", 350.0),   Map.entry("IfcFan", 450.0),
                Map.entry("IfcFurniture", 800.0),     Map.entry("IfcSanitaryTerminal", 600.0),
                Map.entry("IfcStair", 5000.0),        Map.entry("IfcBuildingElementProxy", 500.0)
            );

            var costUpdate = conn.prepareStatement(
                "UPDATE simple_qto SET unit_cost_rm = ?, total_cost_rm = ROUND(total_quantity * ?, 2) WHERE ifc_class = ?"
            );
            for (var entry : unitRates.entrySet()) {
                costUpdate.setDouble(1, entry.getValue());
                costUpdate.setDouble(2, entry.getValue());
                costUpdate.setString(3, entry.getKey());
                costUpdate.executeUpdate();
            }
            costUpdate.close();

            // Summary
            var rs = stmt.executeQuery("SELECT COUNT(*), COALESCE(SUM(total_cost_rm), 0) FROM simple_qto");
            if (rs.next()) {
                System.out.printf("[QTO] %d rows, total cost: RM %,.2f%n", rs.getInt(1), rs.getDouble(2));
            }
            rs.close();

            // Phase 90: Populate element_properties from available data
            stmt.execute("""
                INSERT INTO element_properties (guid, pset_name, property_name, property_value)
                SELECT guid, 'Pset_' || ifc_class || 'Common', 'FireRating',
                       CAST(fire_rating_hr AS TEXT) || 'HR'
                FROM elements_meta
                WHERE fire_rating_hr IS NOT NULL AND fire_rating_hr > 0
            """);
            stmt.execute("""
                INSERT INTO element_properties (guid, pset_name, property_name, property_value)
                SELECT guid, 'Pset_' || ifc_class || 'Common', 'Reference', element_type
                FROM elements_meta
                WHERE element_type IS NOT NULL AND element_type != ''
            """);
            var epRs = stmt.executeQuery("SELECT COUNT(*) FROM element_properties");
            if (epRs.next()) {
                System.out.printf("[PROPS] %d element properties%n", epRs.getInt(1));
            }
            epRs.close();

        } catch (SQLException e) {
            System.out.println("[QTO] Warning: " + e.getMessage());
        }
    }

    private void printLibraryUsageSummary() {
        System.out.println("\n=== LOD400 Library Usage Summary ===");
        System.out.printf("Doors:      %d library, %d parametric%n", openings.libraryDoorCount, openings.parametricDoorCount);
        System.out.printf("Windows:    %d library, %d parametric%n", openings.libraryWindowCount, openings.parametricWindowCount);
        System.out.printf("Stairs:     %d library, %d parametric%n", stairs.libraryStairCount, stairs.parametricStairCount);
        System.out.printf("Fixtures:   %d library, %d parametric%n", mep.libraryFixtureCount, mep.parametricFixtureCount);
        System.out.printf("Lights:     %d library, %d parametric%n", mep.libraryLightCount, mep.parametricLightCount);
        System.out.printf("Sprinklers: %d library, %d parametric%n", mep.librarySprinklerCount, mep.parametricSprinklerCount);
        System.out.printf("Pipes:      %d plumbing, %d FP%n", mep.pipeCount, mep.fpPipeCount);
        System.out.printf("Diffusers:  %d%n", mep.diffuserCount);
        System.out.printf("Coverings:  %d%n", coverings.coveringCount);
        System.out.printf("Railings:   %d%n", railings.railingCount);

        int totalLibrary = openings.libraryDoorCount + openings.libraryWindowCount + stairs.libraryStairCount +
            mep.libraryFixtureCount + mep.libraryLightCount + mep.librarySprinklerCount;
        if (totalLibrary > 0) {
            System.out.println("Status: CONNECTED (using LOD400 geometry)");
        } else if (libraryMapper == null) {
            System.out.println("Status: DISCONNECTED (library mappers not available)");
        } else {
            System.out.println("Status: FALLBACK (no matching library components)");
        }
    }

    /**
     * Phase 43: Write IfcSpace for a room with geometry.
     */
    private void writeSpace(RoomSpec room, String storeyName, String storeyGuid) throws SQLException {
        String spaceGuid = "SPACE_" + storeyName.toUpperCase() + "_" + room.name().toUpperCase();

        // Map room type to IFC predefined type
        String predefinedType = mapRoomTypeToPredefinedType(room.type());

        // Write to spatial_structure
        ep.writeSpatialStructure(spaceGuid, "IfcSpace", room.name(), storeyGuid,
            room.type(), predefinedType);

        // Write space geometry to elements_meta and elements_rtree
        ep.writeElement(spaceGuid, "IfcSpace", room.name(), room.type(), storeyName,
            ep.createBoxGeometry(room.minX(), room.minY(), room.minZ(),
                             room.maxX(), room.maxY(), room.maxZ()));
    }

    /**
     * Map room type to IFC PredefinedType for IfcSpace.
     */
    private String mapRoomTypeToPredefinedType(String roomType) {
        if (roomType == null) return "INTERNAL";
        return switch (roomType.toUpperCase()) {
            case "PORCH", "ANJUNG", "VERANDA", "DECK" -> "EXTERNAL";
            case "GARAGE", "CARPORT" -> "PARKING";
            case "CORRIDOR", "HALL", "LANDING" -> "CIRCULATION";
            default -> "INTERNAL";
        };
    }

    /**
     * Export to JSON for IFC conversion.
     */
    public void exportJson(BuildingSpec spec, String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("{");
            pw.println("  \"building_name\": \"" + spec.name() + "\",");
            pw.println("  \"storeys\": [");

            for (int i = 0; i < spec.storeys().size(); i++) {
                StoreySpec storey = spec.storeys().get(i);
                pw.println("    {");
                pw.println("      \"name\": \"" + storey.name() + "\",");
                pw.println("      \"level\": " + storey.level() + ",");
                pw.println("      \"base_z\": " + storey.baseZ() + ",");
                pw.println("      \"height\": " + storey.height() + ",");

                // Slab
                if (storey.slab() != null) {
                    pw.println("      \"slab\": {");
                    pw.println("        \"type\": \"" + storey.slab().type() + "\",");
                    pw.println("        \"bounds\": [" +
                        storey.slab().minX() + ", " + storey.slab().minY() + ", " + storey.slab().minZ() + ", " +
                        storey.slab().maxX() + ", " + storey.slab().maxY() + ", " + storey.slab().maxZ() + "]");
                    pw.println("      },");
                } else {
                    pw.println("      \"slab\": null,");
                }

                // Walls
                pw.println("      \"walls\": [");
                for (int j = 0; j < storey.walls().size(); j++) {
                    WallAssemblySpec wall = storey.walls().get(j);
                    pw.println("        {");
                    pw.println("          \"side\": \"" + wall.side() + "\",");
                    pw.println("          \"length\": " + wall.length() + ",");
                    pw.println("          \"height\": " + wall.height());
                    pw.println("        }" + (j < storey.walls().size() - 1 ? "," : ""));
                }
                pw.println("      ],");

                // Stairs
                pw.println("      \"stairs\": [");
                for (int j = 0; j < storey.stairs().size(); j++) {
                    StairSpec stair = storey.stairs().get(j);
                    pw.println("        {");
                    pw.println("          \"name\": \"" + stair.name() + "\",");
                    pw.println("          \"to_storey\": \"" + stair.toStorey() + "\",");
                    pw.println("          \"num_risers\": " + stair.numRisers() + ",");
                    pw.println("          \"riser_height\": " + stair.riserHeight() + ",");
                    pw.println("          \"tread_depth\": " + stair.treadDepth() + ",");
                    pw.println("          \"position\": [" + stair.x() + ", " + stair.y() + ", " + stair.z() + "]");
                    pw.println("        }" + (j < storey.stairs().size() - 1 ? "," : ""));
                }
                pw.println("      ]");

                pw.println("    }" + (i < spec.storeys().size() - 1 ? "," : ""));
            }

            pw.println("  ],");

            // Roof
            if (spec.roof() != null) {
                pw.println("  \"roof\": {");
                pw.println("    \"type\": \"" + spec.roof().type() + "\",");
                pw.println("    \"pitch_degrees\": " + spec.roof().pitchDegrees() + ",");
                pw.println("    \"ridge_rise\": " + spec.roof().ridgeRise());
                pw.println("  }");
            } else {
                pw.println("  \"roof\": null");
            }

            pw.println("}");
        }
    }

    /**
     * Get code version for provenance.
     */
    static String getCodeVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--short", "HEAD");
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String commit = new String(p.getInputStream().readAllBytes()).trim();
            int exitCode = p.waitFor();
            if (exitCode == 0 && !commit.isEmpty() && commit.length() <= 12) {
                return "git:" + commit;
            }
        } catch (Exception e) {
            // Git not available or not a git repo
        }
        return "v0.50.4";
    }

    /**
     * Phase 122L: Wall span key — same XY footprint across storeys → merge candidate.
     * Format: side_roundedMinX_roundedMinY_roundedMaxX_roundedMaxY_thickness
     * Rounding to 1 decimal (100mm) absorbs minor numeric drift between storeys.
     */
    private static String wallSpanKey(WallAssemblySpec wall) {
        CladdingSpec c = wall.cladding();
        return wall.side() + "_"
            + Math.round(Math.min(c.minX(), c.maxX()) * 10) + "_"
            + Math.round(Math.min(c.minY(), c.maxY()) * 10) + "_"
            + Math.round(Math.max(c.minX(), c.maxX()) * 10) + "_"
            + Math.round(Math.max(c.minY(), c.maxY()) * 10) + "_"
            + Math.round(wall.thickness() * 1000);

    }

    /**
     * Phase 4 Contract Architecture: Simplified helper for witness recording.
     * Only tracks height and storey count (not full geometry).
     */
    static class SpanningColumnInfoForWitness {
        final String continuityId;
        final double baseZ;
        double totalHeight;
        final String lowestStorey;
        int storeyCount = 1;

        SpanningColumnInfoForWitness(String continuityId, double baseZ, double height, String lowestStorey) {
            this.continuityId = continuityId;
            this.baseZ = baseZ;
            this.totalHeight = height;
            this.lowestStorey = lowestStorey;
        }

        void extend(double additionalHeight, String storeyName) {
            this.totalHeight += additionalHeight;
            this.storeyCount++;
        }
    }

}
