package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.bom.BOMAssemblerAD;
import com.bim.compiler.bom.WallOpeningAssembler;
import com.bim.compiler.dsl.BuildingSpecs.*;
import static com.bim.compiler.dsl.BuildingSpecs.*;
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
            Map<String, String> roomToSpaceGuid = new HashMap<>();
            for (RoomSpec room : storey.rooms()) {
                writeSpace(room, storey.name(), storeyGuid);
                String spaceGuid = "SPACE_" + storey.name().toUpperCase() + "_" + room.name().toUpperCase();
                roomToSpaceGuid.put(room.name().toLowerCase(), spaceGuid);
            }

            // Write slab(s) — Phase 122F: bay slabs replace envelope slab for grid buildings
            if (storey.baySlabs() != null && !storey.baySlabs().isEmpty()) {
                int bayIdx = 0;
                for (SlabSpec baySlab : storey.baySlabs()) {
                    ep.writeElement(
                        "SLAB_BAY_" + storey.name().toUpperCase() + "_" + (++bayIdx),
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
            } else {
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

            // Phase 121: Write per-room finish slabs (Grammar Rule 6: SLAB = structural + finish)
            for (RoomSpec room : storey.rooms()) {
                double finishThickness = getFinishSlabThickness(room.type());
                if (finishThickness > 0) {
                    String finishName = finishThickness < 0.015
                        ? "Finish Floor - Ceramic Tile" : "Finish Floor - Wood";
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

            // Write walls as assemblies
            for (WallAssemblySpec wall : storey.walls()) {
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

        // Write roof
        if (spec.roof() != null) {
            structural.writeRoof(spec.roof(), spec.storeys().get(spec.storeys().size() - 1).name());
        }

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
                pw.println("      \"slab\": {");
                pw.println("        \"type\": \"" + storey.slab().type() + "\",");
                pw.println("        \"bounds\": [" +
                    storey.slab().minX() + ", " + storey.slab().minY() + ", " + storey.slab().minZ() + ", " +
                    storey.slab().maxX() + ", " + storey.slab().maxY() + ", " + storey.slab().maxZ() + "]");
                pw.println("      },");

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

    /**
     * Phase 121: Resolve finish slab thickness by room type.
     * Wet rooms → ceramic tile (13mm), dry habitable rooms → wood (19mm).
     * Returns 0 for room types that don't get a finish slab (UNIT, STAIRWELL, PORCH, STORAGE).
     */
    private static double getFinishSlabThickness(String roomType) {
        if (roomType == null) return 0;
        return switch (roomType.toUpperCase()) {
            case "BATHROOM", "TOILET", "TOILET_BLOCK", "KITCHEN" -> 0.013; // ceramic tile
            case "BEDROOM", "LIVING", "CORRIDOR", "OFFICE", "LOBBY",
                 "DINING", "STAFFROOM", "OPEN_PLAN", "STUDY" -> 0.019;    // wood
            default -> 0; // UNIT, STAIRWELL, PORCH, STORAGE, etc.
        };
    }
}
