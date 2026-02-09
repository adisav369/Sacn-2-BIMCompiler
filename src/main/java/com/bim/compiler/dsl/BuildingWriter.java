package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.bom.BOMAssemblerAD;
import com.bim.compiler.bom.WallOpeningAssembler;
import com.bim.compiler.dsl.BuildingCompiler.*;
import static com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.library.FireSuppressionPlacer;
import com.bim.compiler.library.FireSuppressionPlacer.FPPipeSpec;
import com.bim.compiler.system.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Writes BuildingSpec to database and exports to IFC-ready JSON.
 *
 * Phase 29: LOD400 library integration for doors.
 * - Doors: Use library geometry when available (matches SCHEDULE types)
 * - Windows: Parametric fallback (TERMINAL library has commercial windows)
 */
public class BuildingWriter {

    private final Connection conn;
    private int elementId = 0;
    private DoorWindowLibraryMapper libraryMapper;
    private StairLibraryMapper stairLibraryMapper;  // Phase 58
    private int libraryDoorCount = 0;
    private int parametricDoorCount = 0;
    private int libraryWindowCount = 0;   // Phase 57B
    private int parametricWindowCount = 0;
    private int libraryStairCount = 0;    // Phase 58
    private int parametricStairCount = 0; // Phase 58
    private int libraryFixtureCount = 0;
    private int parametricFixtureCount = 0;
    private int libraryLightCount = 0;      // Phase 33
    private int parametricLightCount = 0;   // Phase 33
    private int librarySprinklerCount = 0;  // Phase 79
    private int parametricSprinklerCount = 0; // Phase 79
    private int pipeCount = 0;              // Phase 34
    private int fpPipeCount = 0;            // Phase 80: Fire protection pipes
    private int diffuserCount = 0;          // Phase 89: Diffusers

    // Phase 88: Wall assembly index for direct opening→wall linking (replaces spatial join)
    private record WallRegion(String assemblyGuid, String storey,
                              double minX, double maxX, double minY, double maxY,
                              double minZ, double maxZ) {}
    private final List<WallRegion> wallAssemblyIndex = new ArrayList<>();

    public BuildingWriter(Connection conn) {
        this.conn = conn;
        // Try to initialize library mappers
        try {
            this.libraryMapper = new DoorWindowLibraryMapper();
        } catch (Exception e) {
            System.out.println("[BuildingWriter] Door/Window library mapper not available: " + e.getMessage());
            this.libraryMapper = null;
        }
        // Phase 58: Stair library mapper
        try {
            this.stairLibraryMapper = new StairLibraryMapper();
        } catch (Exception e) {
            System.out.println("[BuildingWriter] Stair library mapper not available: " + e.getMessage());
            this.stairLibraryMapper = null;
        }
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
        writeSpatialStructure(buildingGuid, "IfcBuilding", spec.name(), null);

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
        Map<String, SpanningColumnInfo> spanningColumns = new LinkedHashMap<>();
        Set<String> writtenContinuityIds = new HashSet<>();

        // Collect all columns and group by continuityId
        for (StoreySpec storey : spec.storeys()) {
            for (ColumnSpec column : storey.columns()) {
                String contId = column.continuityId();
                if (contId != null && !contId.isEmpty()) {
                    SpanningColumnInfo existing = spanningColumns.get(contId);
                    if (existing == null) {
                        // First occurrence - create spanning column info
                        spanningColumns.put(contId, new SpanningColumnInfo(
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
            writeSpatialStructure(storeyGuid, "IfcBuildingStorey", storey.name(), buildingGuid);

            // Phase 43: Write IfcSpace for each room
            Map<String, String> roomToSpaceGuid = new HashMap<>();
            for (RoomSpec room : storey.rooms()) {
                writeSpace(room, storey.name(), storeyGuid);
                String spaceGuid = "SPACE_" + storey.name().toUpperCase() + "_" + room.name().toUpperCase();
                roomToSpaceGuid.put(room.name().toLowerCase(), spaceGuid);
            }

            // Write slab
            writeElement(
                "SLAB_" + storey.name().toUpperCase(),
                "IfcSlab",
                storey.slab().name(),
                storey.slab().type(),
                storey.name(),
                createBoxGeometry(
                    storey.slab().minX(), storey.slab().minY(), storey.slab().minZ(),
                    storey.slab().maxX(), storey.slab().maxY(), storey.slab().maxZ()
                )
            );

            // Write walls as assemblies
            for (WallAssemblySpec wall : storey.walls()) {
                writeWallAssembly(wall, storey.name(), constructionSystem);
            }

            // Phase 49: Write stairs as IFC aggregates
            // Stair aggregate is contained in the storey where the stair starts (lowest)
            // Skip stairs already processed (multi-storey stairs have same name across levels)
            for (StairSpec stair : storey.stairs()) {
                if (!processedStairs.contains(stair.name())) {
                    Set<String> landingsInAggregate = writeStairAssembly(stair, allLandings, storey.name());
                    processedLandings.addAll(landingsInAggregate);
                    processedStairs.add(stair.name());
                }
            }

            // Write doors
            for (DoorSpec door : storey.doors()) {
                writeDoor(door, storey.name());
                String spaceGuid = roomToSpaceGuid.get(door.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String doorGuid = "DOOR_" + door.name().toUpperCase() + "_" + storey.name();
                    writeSpaceContainment(doorGuid, spaceGuid);
                }
            }

            // Write windows
            for (WindowSpec window : storey.windows()) {
                writeWindow(window, storey.name());
                String spaceGuid = roomToSpaceGuid.get(window.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String windowGuid = "WINDOW_" + window.name().toUpperCase() + "_" + storey.name();
                    writeSpaceContainment(windowGuid, spaceGuid);
                }
            }

            // Write landings not part of stair aggregates (standalone landings)
            for (LandingSpec landing : storey.landings()) {
                if (!processedLandings.contains(landing.name())) {
                    writeLanding(landing, storey.name());
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
            // sprinklerZ (head top) = mainZ - TEE_BELOW_MAIN - HEAD_TOP_BELOW_TEE
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
                writeSprinkler(snapped, storey.name());
                String spaceGuid = roomToSpaceGuid.get(sprinkler.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String sprinklerGuid = "SPRINKLER_" + storey.name() + "_" + sprinkler.id().toUpperCase();
                    writeSpaceContainment(sprinklerGuid, spaceGuid);
                }
            }

            // Phase 80/85: Write fire suppression piping connecting sprinklers
            // Phase 85: Use BOM metadata for pipe Z positioning (below slab)
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
                        writeFPFitting(fpPipe, storey.name());
                    } else {
                        double pipeHeight = Math.abs(fpPipe.end().z() - fpPipe.start().z());
                        if (pipeHeight > storey.height()) continue;
                        writeFPPipeSegment(fpPipe, storey.name());
                    }
                }
            }

            // Write lights (Phase 14B) with space containment
            for (LightSpec light : storey.lights()) {
                writeLight(light, storey.name());
                String spaceGuid = roomToSpaceGuid.get(light.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String lightGuid = "LIGHT_" + storey.name() + "_" + light.id().toUpperCase();
                    writeSpaceContainment(lightGuid, spaceGuid);
                }
            }

            // Write plumbing fixtures (Phase 32) with space containment
            for (FixtureSpec fixture : storey.fixtures()) {
                writeFixture(fixture, storey.name());
                String spaceGuid = roomToSpaceGuid.get(fixture.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String fixtureGuid = "FIXTURE_" + storey.name().toUpperCase() + "_" + fixture.id().toUpperCase();
                    writeSpaceContainment(fixtureGuid, spaceGuid);
                }
            }

            // Write diffusers (Phase 89) with space containment
            for (DiffuserSpec diffuser : storey.diffusers()) {
                writeDiffuser(diffuser, storey.name());
                String spaceGuid = roomToSpaceGuid.get(diffuser.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String diffuserGuid = "DIFFUSER_" + storey.name() + "_" + diffuser.id().toUpperCase();
                    writeSpaceContainment(diffuserGuid, spaceGuid);
                }
            }

            // Write electrical elements (Phase 33) with space containment
            for (ElectricalSpec elec : storey.electricals()) {
                writeElectricalElement(elec, storey.name());
                String spaceGuid = roomToSpaceGuid.get(elec.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String elecGuid = "ELEC_" + elec.roomName().toUpperCase() + "_" + elec.elementType().toUpperCase() + "_" + elec.id() + "_" + storey.name();
                    writeSpaceContainment(elecGuid, spaceGuid);
                }
            }

            // Write plumbing pipes (Phase 34) with space containment
            for (PlumbingSpec pipe : storey.plumbing()) {
                writePipeSegment(pipe, storey.name());
                if (pipe.roomName() != null) {
                    String spaceGuid = roomToSpaceGuid.get(pipe.roomName().toLowerCase());
                    if (spaceGuid != null) {
                        String pipeGuid = "PIPE_" + pipe.roomName().toUpperCase() + "_" + pipe.pipeType().toUpperCase() + "_" + pipe.id() + "_" + storey.name();
                        writeSpaceContainment(pipeGuid, spaceGuid);
                    }
                }
            }

            // Write fire detection alarms (Phase 100) with space containment
            for (AlarmSpec alarm : storey.alarms()) {
                writeAlarm(alarm, storey.name());
                String spaceGuid = roomToSpaceGuid.get(alarm.roomName().toLowerCase());
                if (spaceGuid != null) {
                    String alarmGuid = "ALARM_" + alarm.id().toUpperCase() + "_" + storey.name();
                    writeSpaceContainment(alarmGuid, spaceGuid);
                }
            }

            // Phase 50B.1 + Phase 4: Write structural columns with spanning support
            for (ColumnSpec column : storey.columns()) {
                String contId = column.continuityId();
                if (contId != null && !contId.isEmpty()) {
                    // Column has continuityId - check if spanning column should be written
                    if (!writtenContinuityIds.contains(contId)) {
                        // First encounter - write the merged spanning column
                        SpanningColumnInfo spanning = spanningColumns.get(contId);
                        if (spanning != null && spanning.storeyCount() > 1) {
                            // Multi-storey spanning column - write combined geometry
                            writeSpanningColumn(spanning);
                            writtenContinuityIds.add(contId);
                        } else {
                            // Single-storey with continuityId - write normally
                            writeColumn(column, storey.name());
                            writtenContinuityIds.add(contId);
                        }
                    }
                    // else: already written, skip this per-storey instance
                } else {
                    // No continuityId (e.g., grid columns) - write normally
                    writeColumn(column, storey.name());
                }
            }

            // Phase 50B.1: Write structural beams/lintels
            for (BeamSpec beam : storey.beams()) {
                writeBeam(beam, storey.name());
            }
        }

        // Write roof
        if (spec.roof() != null) {
            writeRoof(spec.roof(), spec.storeys().get(spec.storeys().size() - 1).name());
        }

        // Phase 35: Write MEP system graphs
        for (var system : spec.mepSystems()) {
            writeMEPSystem(system, buildingGuid);
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
     * Creates BOM hierarchy: WALL_PANEL → OPENING (door/window)
     *
     * Benefits:
     * - Outliner shows openings under their walls
     * - Move wall → openings move with it
     * - Change type in library → recompile → all updated (no code edits)
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
     * Follows iDempiere M_BOM / M_BOM_Product pattern:
     * - ad_bom: BOM header definitions (assembly types)
     * - ad_bom_child: BOM children (what belongs to each assembly)
     *
     * Key concept: Items remain as individual objects. BOM is just the recipe
     * that describes how they group together. A child can reference another BOM
     * (nested assemblies).
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
     * Print summary of LOD400 library usage (Phase 29, 32, 58, 79).
     */
    /**
     * Phase 89: Generate Simple QTO table for NLP search + 5D costing.
     * Follows the same 4-query pattern as simple_qto_extract.py (federation reference).
     * Uses discipline column from elements_meta + CIDB Malaysia 2024 standard rates.
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

            // 5. Apply CIDB Malaysia 2024 unit costs (same as simple_qto_extract.py)
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
            // Fire rating properties
            stmt.execute("""
                INSERT INTO element_properties (guid, pset_name, property_name, property_value)
                SELECT guid, 'Pset_' || ifc_class || 'Common', 'FireRating',
                       CAST(fire_rating_hr AS TEXT) || 'HR'
                FROM elements_meta
                WHERE fire_rating_hr IS NOT NULL AND fire_rating_hr > 0
            """);
            // Element type as Reference property
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
        System.out.printf("Doors:      %d library, %d parametric%n", libraryDoorCount, parametricDoorCount);
        System.out.printf("Windows:    %d library, %d parametric%n", libraryWindowCount, parametricWindowCount);
        System.out.printf("Stairs:     %d library, %d parametric%n", libraryStairCount, parametricStairCount);
        System.out.printf("Fixtures:   %d library, %d parametric%n", libraryFixtureCount, parametricFixtureCount);
        System.out.printf("Lights:     %d library, %d parametric%n", libraryLightCount, parametricLightCount);
        System.out.printf("Sprinklers: %d library, %d parametric%n", librarySprinklerCount, parametricSprinklerCount);  // Phase 79
        System.out.printf("Pipes:      %d plumbing, %d FP%n", pipeCount, fpPipeCount);  // Phase 80
        System.out.printf("Diffusers:  %d%n", diffuserCount);  // Phase 89

        int totalLibrary = libraryDoorCount + libraryWindowCount + libraryStairCount +
            libraryFixtureCount + libraryLightCount + librarySprinklerCount;
        if (totalLibrary > 0) {
            System.out.println("Status: CONNECTED (using LOD400 geometry)");
        } else if (libraryMapper == null && stairLibraryMapper == null) {
            System.out.println("Status: DISCONNECTED (library mappers not available)");
        } else {
            System.out.println("Status: FALLBACK (no matching library components)");
        }
    }

    /**
     * Generate witness JSON from BuildingSpec (Phase 31 - Witness System).
     * NON-BLOCKING: Errors are caught and logged, returns false on failure.
     *
     * @param spec The building spec to extract witness data from
     * @param outputPath Path to write witness.json
     * @return true if witness was written successfully
     */
    private void writeWallAssembly(WallAssemblySpec wall, String storeyName,
                                   ConstructionSystem constructionSystem) throws SQLException {
        // Phase 65: Extract fire rating in hours (null if NONE)
        Double fireRatingHr = null;
        if (wall.fireRating() != null && wall.fireRating() != FireRating.NONE) {
            fireRatingHr = wall.fireRating().getRatingHours();
        }

        // Phase 50B.1: Branch on construction system
        if (constructionSystem == ConstructionSystem.MASONRY) {
            // MASONRY: Single IfcWall element (no frame/cladding decomposition)
            String wallGuid = "WALL_" + wall.assemblyName() + "_" + storeyName;
            CladdingSpec cladding = wall.cladding();
            writeElement(
                wallGuid,
                "IfcWall",
                cladding.material(),
                wall.wallType() != null ? wall.wallType().name() : "WALL",
                storeyName,
                createBoxGeometry(
                    cladding.minX(), cladding.minY(), cladding.minZ(),
                    cladding.maxX(), cladding.maxY(), cladding.maxZ()
                ),
                fireRatingHr
            );
            return;
        }

        // FRAMED: Frame members + cladding plate (existing behavior)
        String assemblyGuid = "ASSEMBLY_" + wall.assemblyName() + "_" + storeyName.toUpperCase();

        // Write assembly (ifc_class=NULL for BOM-only wall assemblies)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, wall.assemblyType());
            ps.setNull(3, java.sql.Types.VARCHAR);  // ifc_class (BOM-only)
            ps.setString(4, wall.assemblyName());
            ps.setDouble(5, wall.length());
            ps.setDouble(6, wall.thickness());
            ps.setDouble(7, wall.height());
            ps.setString(8, storeyName);
            ps.execute();
        }

        int seq = 0;

        // Write frames - include wall name for uniqueness
        int frameIdx = 0;
        for (FrameSpec frame : wall.frames()) {
            String frameGuid = "FRAME_" + wall.assemblyName() + "_" + frame.role() + "_" + (frameIdx++) + "_" + storeyName;
            writeElement(
                frameGuid,
                "IfcMember",
                frame.profile(),
                "FRAME",
                storeyName,
                createBoxGeometry(
                    frame.minX(), frame.minY(), frame.minZ(),
                    frame.maxX(), frame.maxY(), frame.maxZ()
                )
            );

            writeAssemblyComponent(assemblyGuid, frameGuid, "FRAME", 0, 0, 0, seq++);
        }

        // Write cladding - include wall name for uniqueness
        // Phase 65: Include fire rating on cladding (main wall surface)
        String claddingGuid = "CLADDING_" + wall.assemblyName() + "_" + storeyName;
        writeElement(
            claddingGuid,
            "IfcPlate",
            wall.cladding().material(),
            "CLADDING",
            storeyName,
            createBoxGeometry(
                wall.cladding().minX(), wall.cladding().minY(), wall.cladding().minZ(),
                wall.cladding().maxX(), wall.cladding().maxY(), wall.cladding().maxZ()
            ),
            fireRatingHr
        );

        writeAssemblyComponent(assemblyGuid, claddingGuid, "CLADDING", 0, 0, 0, seq);

        // Phase 88: Index wall assembly for direct opening→wall linking
        // Normalize bounds (cladding may have swapped min/max for west/south walls)
        CladdingSpec clad = wall.cladding();
        wallAssemblyIndex.add(new WallRegion(assemblyGuid, storeyName,
            Math.min(clad.minX(), clad.maxX()), Math.max(clad.minX(), clad.maxX()),
            Math.min(clad.minY(), clad.maxY()), Math.max(clad.minY(), clad.maxY()),
            Math.min(clad.minZ(), clad.maxZ()), Math.max(clad.minZ(), clad.maxZ())));
    }

    private void writeStair(StairSpec stair, String storeyName) throws SQLException {
        String stairGuid = "STAIR_" + stair.name().toUpperCase() + "_" + storeyName;

        // Phase 14A: Check if using library geometry
        if (stair.usesLibrary()) {
            writeLibraryStair(stair, stairGuid, storeyName);
            return;
        }

        // Parametric stair: convert geometry
        float[] vertices = new float[stair.vertices().size() * 3];
        for (int i = 0; i < stair.vertices().size(); i++) {
            Point3D v = stair.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[stair.faces().size() * 3];
        for (int i = 0; i < stair.faces().size(); i++) {
            int[] face = stair.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        // Calculate bounds
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : stair.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = writeGeometry(vertices, faces);
        writeElementMeta(stairGuid, "IfcStairFlight", "Stair Flight", "STAIR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(stairGuid, geoHash);
    }

    /**
     * Write a library-based stair (Phase 14A).
     * Uses pre-extracted LOD400 geometry from component_library.db.
     */
    private void writeLibraryStair(StairSpec stair, String stairGuid, String storeyName) throws SQLException {
        // Library geometry hash is already computed
        String geoHash = stair.libraryGeometryHash();

        // Calculate scaled bounds
        double halfWidth = stair.width() / 2;
        double minX = stair.x() - halfWidth * stair.scaleX();
        double maxX = stair.x() + halfWidth * stair.scaleX();
        double minY = stair.y();
        double maxY = stair.y() + stair.run() * stair.scaleY();
        double minZ = stair.z();
        double maxZ = stair.z() + stair.rise() * stair.scaleZ();

        // Write metadata with library geometry reference
        writeElementMeta(stairGuid, "IfcStairFlight", "Library Stair Flight", "STAIR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);

        // Write instance with scale factors
        // Note: Geometry is referenced by hash, scaling applied in transform
        writeInstanceWithScale(stairGuid, geoHash,
            stair.x(), stair.y(), stair.z(),
            stair.scaleX(), stair.scaleY(), stair.scaleZ());
    }

    /**
     * Write element instance with scale transform.
     */
    private void writeInstanceWithScale(String guid, String geoHash,
                                       double x, double y, double z,
                                       double scaleX, double scaleY, double scaleZ) throws SQLException {
        // For now, use the existing instance method
        // Scale factors would be applied during IFC export (IfcCartesianTransformationOperator3DnonUniform)
        PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO element_instances (guid, geometry_hash)
            VALUES (?, ?)
            """);
        ps.setString(1, guid);
        ps.setString(2, geoHash);
        ps.executeUpdate();

        // Store transform with scale in element_transforms (if table exists)
        // For now, just log the scale
        if (scaleX != 1.0 || scaleY != 1.0 || scaleZ != 1.0) {
            System.out.printf("  Library stair %s: scale=(%.2f, %.2f, %.2f)%n",
                guid, scaleX, scaleY, scaleZ);
        }
    }

    /**
     * Phase 49: Write stair as IFC aggregate structure.
     *
     * Creates:
     *   IfcStair (aggregate parent, no geometry)
     *     └─ IfcRelAggregates
     *          ├─ IfcStairFlight (actual geometry)
     *          └─ IfcSlab/LANDING (if fromStair matches)
     *
     * Only the parent IfcStair gets IfcRelContainedInSpatialStructure.
     * Children inherit spatial containment transitively through IfcRelAggregates.
     *
     * @param stair The stair spec
     * @param landings All landings in the building (to find associated ones)
     * @param containmentStorey The storey for spatial containment (lowest served)
     * @return Set of landing names that were included in this aggregate
     */
    private Set<String> writeStairAssembly(StairSpec stair, List<LandingSpec> landings,
                                           String containmentStorey) throws SQLException {
        Set<String> processedLandings = new HashSet<>();

        // 1. Create IfcStair aggregate parent (no geometry)
        String stairAssemblyGuid = "STAIR_" + stair.name().toUpperCase();

        // Calculate aggregate bounds (stair + all associated landings)
        double minX = stair.x();
        double minY = stair.y();
        double minZ = stair.z();
        double maxX = stair.x() + stair.width();
        double maxY = stair.y() + stair.run();
        double maxZ = stair.z() + stair.rise();

        // Find associated landings and expand bounds
        List<LandingSpec> associatedLandings = new ArrayList<>();
        for (LandingSpec landing : landings) {
            if (stair.name().equals(landing.fromStair())) {
                associatedLandings.add(landing);
                minX = Math.min(minX, landing.minX());
                minY = Math.min(minY, landing.minY());
                minZ = Math.min(minZ, landing.minZ());
                maxX = Math.max(maxX, landing.maxX());
                maxY = Math.max(maxY, landing.maxY());
                maxZ = Math.max(maxZ, landing.maxZ());
            }
        }

        // Write IfcStair parent in elements_meta (no geometry - aggregate container)
        // predefined_type: STRAIGHT_RUN_STAIR for simple single-flight stairs
        // Note: writeElementMeta also writes to elements_rtree
        writeElementMeta(stairAssemblyGuid, "IfcStair", "Stair " + stair.name(),
                        "STRAIGHT_RUN_STAIR", containmentStorey,
                        minX, maxX, minY, maxY, minZ, maxZ);

        // No element_instances entry for aggregate parent (no geometry)

        // Write assembly record with ifc_class='IfcStair' (IFC aggregate)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, stairAssemblyGuid);
            ps.setString(2, "STAIR_ASSEMBLY");
            ps.setString(3, "IfcStair");  // ifc_class - marks this as IFC aggregate
            ps.setString(4, stair.name());
            ps.setDouble(5, maxX - minX);  // total_width
            ps.setDouble(6, maxY - minY);  // total_depth (run)
            ps.setDouble(7, maxZ - minZ);  // total_height (rise)
            ps.setString(8, containmentStorey);
            ps.execute();
        }

        // 2. Create IfcStairFlight child (actual stair geometry)
        String flightGuid = "STAIRFLIGHT_" + stair.name().toUpperCase();
        writeStairFlightChild(stair, flightGuid, containmentStorey);

        // Link flight to assembly
        writeAssemblyComponent(stairAssemblyGuid, flightGuid, "FLIGHT", 0, 0, 0, 0);

        // 3. Create IfcSlab/LANDING children
        int seq = 1;
        for (LandingSpec landing : associatedLandings) {
            String landingGuid = "LANDING_" + landing.name().toUpperCase();
            writeLandingChild(landing, landingGuid, containmentStorey);

            // Link landing to assembly
            writeAssemblyComponent(stairAssemblyGuid, landingGuid, "LANDING", 0, 0, 0, seq++);
            processedLandings.add(landing.name());
        }

        System.out.printf("  [STAIR] %s: IfcStair aggregate with %d flight + %d landings (storey: %s)%n",
            stair.name(), 1, associatedLandings.size(), containmentStorey);

        return processedLandings;
    }

    /**
     * Write IfcStairFlight as child of aggregate (no separate spatial containment).
     * Phase 58: Uses LOD400 library geometry when available.
     */
    private void writeStairFlightChild(StairSpec stair, String flightGuid,
                                       String storeyName) throws SQLException {
        // Phase 58: Try library lookup first
        if (stairLibraryMapper != null) {
            var mapping = stairLibraryMapper.mapStair(stair.width(), stair.rise(), stair.run());

            if (mapping.usesLibrary()) {
                writeLibraryStairFlight(stair, flightGuid, storeyName, mapping);
                libraryStairCount++;
                return;
            }
        }

        // Fallback: Parametric stair geometry
        parametricStairCount++;

        // Convert geometry
        float[] vertices = new float[stair.vertices().size() * 3];
        for (int i = 0; i < stair.vertices().size(); i++) {
            Point3D v = stair.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[stair.faces().size() * 3];
        for (int i = 0; i < stair.faces().size(); i++) {
            int[] face = stair.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        // Calculate bounds
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : stair.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = writeGeometry(vertices, faces);

        // Write element meta - storey is for reference only (containment via aggregate)
        writeElementMeta(flightGuid, "IfcStairFlight", "Stair Flight " + stair.name(),
                        "STRAIGHT", storeyName,
                        minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(flightGuid, geoHash);
    }

    /**
     * Write library-based stair flight with LOD400 geometry.
     * Phase 58: Transforms library geometry to world position with optional scaling.
     */
    private void writeLibraryStairFlight(StairSpec stair, String flightGuid, String storeyName,
                                          StairLibraryMapper.StairMappingResult mapping) throws SQLException {
        var comp = mapping.component();

        // Transform library geometry to world position
        // Stair origin is at (x, y, z) with run along +Y
        String geoHash = stairLibraryMapper.transformAndWriteGeometry(
            conn,
            comp.geometryHash(),
            stair.x(), stair.y(), stair.z(),
            0.0,  // No rotation (stair already aligned with +Y)
            mapping.scaleX(), mapping.scaleY(), mapping.scaleZ()
        );

        if (geoHash == null) {
            // Fallback to parametric if transform fails
            System.out.printf("  [STAIR] %s: Library transform failed, using parametric%n", stair.name());
            parametricStairCount++;
            libraryStairCount--;  // Undo the increment
            writeStairFlightChildParametric(stair, flightGuid, storeyName);
            return;
        }

        // Calculate scaled bounds
        double minX = stair.x();
        double maxX = stair.x() + stair.width() * mapping.scaleX();
        double minY = stair.y();
        double maxY = stair.y() + stair.run() * mapping.scaleY();
        double minZ = stair.z();
        double maxZ = stair.z() + stair.rise() * mapping.scaleZ();

        // Write element meta with library reference
        String elementName = mapping.isScaled()
            ? String.format("LOD400 Stair Flight %s (scaled %.0f%%)", stair.name(), mapping.scaleZ() * 100)
            : "LOD400 Stair Flight " + stair.name();

        writeElementMeta(flightGuid, "IfcStairFlight", elementName,
                        "STRAIGHT", storeyName,
                        minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(flightGuid, geoHash);
    }

    /**
     * Write parametric stair flight (fallback helper).
     */
    private void writeStairFlightChildParametric(StairSpec stair, String flightGuid,
                                                  String storeyName) throws SQLException {
        float[] vertices = new float[stair.vertices().size() * 3];
        for (int i = 0; i < stair.vertices().size(); i++) {
            Point3D v = stair.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[stair.faces().size() * 3];
        for (int i = 0; i < stair.faces().size(); i++) {
            int[] face = stair.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : stair.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = writeGeometry(vertices, faces);
        writeElementMeta(flightGuid, "IfcStairFlight", "Stair Flight " + stair.name(),
                        "STRAIGHT", storeyName,
                        minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(flightGuid, geoHash);
    }

    /**
     * Write IfcSlab/LANDING as child of stair aggregate (no separate spatial containment).
     */
    private void writeLandingChild(LandingSpec landing, String landingGuid,
                                   String storeyName) throws SQLException {
        BoxGeometry geo = createBoxGeometry(
            landing.minX(), landing.minY(), landing.minZ(),
            landing.maxX(), landing.maxY(), landing.maxZ()
        );

        String geoHash = writeGeometry(geo.vertices(), geo.faces());

        // Write element meta - storey is for reference only (containment via aggregate)
        writeElementMeta(landingGuid, "IfcSlab", "Stair Landing " + landing.name(),
                        "LANDING", storeyName,
                        landing.minX(), landing.maxX(), landing.minY(), landing.maxY(),
                        landing.minZ(), landing.maxZ());
        writeInstance(landingGuid, geoHash);
    }

    /**
     * Phase 88: Find wall assembly for an opening at the given position.
     * Matches by storey + XY overlap (same logic as WallOpeningAssembler, but in-memory).
     * Returns full WallRegion so caller can compute relative offsets.
     */
    private WallRegion findWallForOpening(String storey, double minX, double maxX,
                                          double minY, double maxY) {
        WallRegion best = null;
        double bestOverlap = 0;
        double tolerance = 0.3; // 300mm match tolerance

        for (WallRegion wall : wallAssemblyIndex) {
            if (!wall.storey().equalsIgnoreCase(storey)) continue;

            double overlapX = Math.min(wall.maxX(), maxX + tolerance) - Math.max(wall.minX(), minX - tolerance);
            double overlapY = Math.min(wall.maxY(), maxY + tolerance) - Math.max(wall.minY(), minY - tolerance);
            double overlap = Math.max(0, overlapX) * Math.max(0, overlapY);

            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                best = wall;
            }
        }
        return best;
    }

    private void writeDoor(DoorSpec door, String storeyName) throws SQLException {
        String doorGuid = "DOOR_" + door.name().toUpperCase() + "_" + storeyName;

        // Phase 29: Try library lookup first
        if (libraryMapper != null) {
            double widthMm = door.width() * 1000;
            double heightMm = door.height() * 1000;
            var mapping = libraryMapper.mapDoor(widthMm, heightMm, "D?", door.wall());

            if (mapping.usesLibrary()) {
                writeLibraryDoor(door, doorGuid, storeyName, mapping);
                libraryDoorCount++;
                return;
            }
        }

        // Fallback: Door as simple box (parametric)
        parametricDoorCount++;
        double depth = BIMConstants.DOOR_THICKNESS;
        double halfDepth = depth / 2;

        // Phase 29: Orient door based on wall direction
        // Door position is at wall plane; center depth around that position
        // This works for both exterior walls (face) and interior walls (centerline)
        double minX, maxX, minY, maxY;
        switch (door.wall()) {
            case "south", "north" -> {
                // Horizontal wall (running east-west): width along X, depth centered on Y
                minX = door.x();
                maxX = door.x() + door.width();
                minY = door.y() - halfDepth;
                maxY = door.y() + halfDepth;
            }
            case "west", "east" -> {
                // Vertical wall (running north-south): width along Y, depth centered on X
                minX = door.x() - halfDepth;
                maxX = door.x() + halfDepth;
                minY = door.y();
                maxY = door.y() + door.width();
            }
            default -> {
                // Fallback: door along X
                minX = door.x();
                maxX = door.x() + door.width();
                minY = door.y() - halfDepth;
                maxY = door.y() + halfDepth;
            }
        }

        writeElement(
            doorGuid,
            "IfcDoor",
            "Entry Door",
            "DOOR",
            storeyName,
            createBoxGeometry(
                minX, minY, door.z(),
                maxX, maxY, door.z() + door.height()
            )
        );

        // Phase 88: Direct wall→opening link for parametric doors
        WallRegion matchedWall = findWallForOpening(storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            writeAssemblyComponent(matchedWall.assemblyGuid(), doorGuid, "OPENING",
                minX - matchedWall.minX(), minY - matchedWall.minY(),
                door.z() - matchedWall.minZ(), 100);
        }
    }

    /**
     * Write door using LOD400 library geometry (Phase 29, updated Phase 54).
     *
     * Phase 54: Now transforms library geometry to world-space using GeometryEngine,
     * preserving LOD400 detail while maintaining Pattern B (world-space + zero transform).
     */
    private void writeLibraryDoor(DoorSpec door, String doorGuid, String storeyName,
                                  DoorWindowLibraryMapper.MappingResult result) throws SQLException {
        var libComp = result.component();

        // Phase 54: Calculate transformation for library geometry
        // Library convention: widthMm = Y-extent, depthMm = X-extent, heightMm = Z-extent
        // Phase 88: When orientation-matched, use actual axis extents for bounds
        boolean isNorthSouth = door.wall().equals("north") || door.wall().equals("south");
        double xExtent = libComp.depthMm() / 1000.0;   // X-axis extent
        double yExtent = libComp.widthMm() / 1000.0;    // Y-axis extent

        // For bounds: NS walls → opening width along X, depth along Y
        //             EW walls → opening width along Y, depth along X
        // Phase 97: When NOT orientation-matched, mesh is rotated 90° so local X↔Y swap.
        double visibleWidth, physicalDepth;
        if (result.orientationMatched()) {
            if (isNorthSouth) {
                visibleWidth = xExtent;  // X-extent is opening width on NS wall
                physicalDepth = yExtent; // Y-extent is depth
            } else {
                visibleWidth = yExtent;  // Y-extent is opening width on EW wall
                physicalDepth = xExtent; // X-extent is depth
            }
        } else {
            // Rotated: local axes swap
            if (isNorthSouth) {
                visibleWidth = yExtent;
                physicalDepth = xExtent;
            } else {
                visibleWidth = xExtent;
                physicalDepth = yExtent;
            }
        }
        double halfDepth = physicalDepth / 2;

        // Phase 88: Skip rotation when orientation-matched variant selected
        // Phase 81 fallback: Deterministic rotation from library forward_axis
        double rotateZ = result.orientationMatched() ? 0.0 : libComp.calculateRotation(door.wall());
        double centerX, centerY;
        double minX, maxX, minY, maxY;

        // Calculate center and bounds based on wall direction
        if (isNorthSouth) {
            // Door on north/south wall: width along X
            centerX = door.x() + door.width() / 2;
            centerY = door.y();
            minX = door.x();
            maxX = door.x() + door.width();
            minY = door.y() - halfDepth;
            maxY = door.y() + halfDepth;
        } else {
            // Door on east/west wall: width along Y
            centerX = door.x();
            centerY = door.y() + door.width() / 2;
            minX = door.x() - halfDepth;
            maxX = door.x() + halfDepth;
            minY = door.y();
            maxY = door.y() + door.width();
        }

        double minZ = door.z();
        double maxZ = door.z() + door.height();
        // Phase 79: Compute attachment offset - library geometry is centered,
        // we need to offset so bottom (localMinZ) aligns with target floor level
        double translateZ = door.z() - libComp.localMinZ();

        // Write metadata (bounds for spatial queries)
        writeElementMeta(doorGuid, "IfcDoor", libComp.name(), "DOOR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);

        // Phase 54/79: Transform library geometry to world-space
        // CONTRACT: Pattern B - world-space geometry + zero transform
        // Maths: translateZ = targetZ - localMinZ, so localMinZ + translateZ = targetZ
        String geoHash = libraryMapper.transformAndWriteGeometry(
            conn,
            libComp.geometryHash(),
            centerX, centerY, translateZ,
            rotateZ
        );

        if (geoHash == null) {
            // Fallback to box if library geometry not available
            System.out.println("[BuildingWriter] LOD400 geometry not found, using box fallback: " + doorGuid);
            BoxGeometry worldGeo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(worldGeo.vertices(), worldGeo.faces());
        }

        writeInstance(doorGuid, geoHash);

        // Create DOOR_ASSEMBLY for BOM
        String doorAssemblyGuid = "ASSEMBLY_" + doorGuid;
        writeDoorAssembly(doorAssemblyGuid, doorGuid, door, storeyName);

        // Phase 88: Direct wall→opening link (replaces post-hoc spatial join)
        WallRegion matchedWall = findWallForOpening(storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            double localX = minX - matchedWall.minX();
            double localY = minY - matchedWall.minY();
            double localZ = minZ - matchedWall.minZ();
            writeAssemblyComponent(matchedWall.assemblyGuid(), doorGuid, "OPENING",
                localX, localY, localZ, 100);
        }
    }

    /**
     * Write door assembly structure (Phase 29).
     * Components: LEAF (door panel) + HARDWARE (hinges, handle) for BOM.
     */
    private void writeDoorAssembly(String assemblyGuid, String doorGuid, DoorSpec door,
                                   String storeyName) throws SQLException {
        // Create assembly record (ifc_class=NULL for BOM-only door assemblies)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, "DOOR_ASSEMBLY");
            ps.setNull(3, java.sql.Types.VARCHAR);  // ifc_class (BOM-only)
            ps.setString(4, door.name());
            ps.setDouble(5, door.width());
            ps.setDouble(6, BIMConstants.DOOR_THICKNESS);
            ps.setDouble(7, door.height());
            ps.setString(8, storeyName);
            ps.execute();
        }

        // Add door leaf as component
        writeAssemblyComponent(assemblyGuid, doorGuid, "LEAF", 0, 0, 0, 0);

        // Add hardware as BOM-only components (optional=true, no geometry)
        for (int i = 0; i < BIMConstants.STANDARD_DOOR_HINGES; i++) {
            String hingeId = "HINGE_" + door.name() + "_" + i;
            writeAssemblyComponent(assemblyGuid, hingeId, "HINGE_100MM", 0, 0, 0, i + 1, true);
        }
        // 1 handle set
        String handleId = "HANDLE_" + door.name();
        writeAssemblyComponent(assemblyGuid, handleId, "HANDLE_SET", 0, 0, 0, 4, true);
    }

    private void writeWindow(WindowSpec window, String storeyName) throws SQLException {
        String windowGuid = "WINDOW_" + window.name().toUpperCase() + "_" + storeyName;

        // Phase 57B: Try library match with scaling support
        if (libraryMapper != null) {
            var result = libraryMapper.mapWindow(
                window.width() * 1000,  // Convert to mm
                window.height() * 1000,
                window.name(),
                window.wall()
            );
            if (result.usesLibrary()) {
                writeWindowFromLibrary(window, windowGuid, storeyName, result);
                return;
            }
        }

        parametricWindowCount++;

        // Window as simple box (frame)
        double depth = BIMConstants.WINDOW_THICKNESS;
        double halfDepth = depth / 2;

        // Phase 29: Orient window based on wall direction
        // Window position is at wall plane; center depth around that position
        double minX, maxX, minY, maxY;
        switch (window.wall()) {
            case "south", "north" -> {
                // Horizontal wall: width along X, depth centered on Y
                minX = window.x();
                maxX = window.x() + window.width();
                minY = window.y() - halfDepth;
                maxY = window.y() + halfDepth;
            }
            case "west", "east" -> {
                // Vertical wall: width along Y, depth centered on X
                minX = window.x() - halfDepth;
                maxX = window.x() + halfDepth;
                minY = window.y();
                maxY = window.y() + window.width();
            }
            default -> {
                // Fallback: center depth along Y
                minX = window.x();
                maxX = window.x() + window.width();
                minY = window.y() - halfDepth;
                maxY = window.y() + halfDepth;
            }
        }

        writeElement(
            windowGuid,
            "IfcWindow",
            "Standard Window",
            "WINDOW",
            storeyName,
            createBoxGeometry(
                minX, minY, window.z(),
                maxX, maxY, window.z() + window.height()
            )
        );

        // Phase 88: Direct wall→opening link for parametric windows
        WallRegion matchedWall = findWallForOpening(storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            writeAssemblyComponent(matchedWall.assemblyGuid(), windowGuid, "OPENING",
                minX - matchedWall.minX(), minY - matchedWall.minY(),
                window.z() - matchedWall.minZ(), 100);
        }
    }

    /**
     * Phase 57B: Write window using LOD400 library geometry with optional scaling.
     * Follows same pattern as writeLibraryDoor.
     */
    private void writeWindowFromLibrary(WindowSpec window, String windowGuid, String storeyName,
                                        DoorWindowLibraryMapper.MappingResult result) throws SQLException {
        libraryWindowCount++;
        var libComp = result.component();

        // Phase 88: Use actual axis extents for bounds (same logic as writeLibraryDoor)
        boolean isNorthSouth = window.wall().equals("north") || window.wall().equals("south");
        double xExtent = libComp.depthMm() / 1000.0;   // X-axis extent (local)
        double yExtent = libComp.widthMm() / 1000.0;    // Y-axis extent (local)
        // Phase 97: When NOT orientation-matched, mesh is rotated 90° so local X↔Y swap.
        // NS wall depth (world Y) = local Y (oriented) or local X (rotated).
        double physicalDepth;
        if (result.orientationMatched()) {
            physicalDepth = isNorthSouth ? yExtent : xExtent;
        } else {
            // Rotated: local axes swap → NS depth = xExtent, EW depth = yExtent
            physicalDepth = isNorthSouth ? xExtent : yExtent;
        }
        double halfDepth = physicalDepth / 2;

        // Phase 88: Skip rotation when orientation-matched variant selected
        // Phase 81 fallback: Deterministic rotation from library forward_axis
        double rotateZ = result.orientationMatched() ? 0.0 : libComp.calculateRotation(window.wall());
        double centerX, centerY;
        double minX, maxX, minY, maxY;

        // Calculate center and bounds based on wall direction
        if (isNorthSouth) {
            // Window on north/south wall: width along X
            centerX = window.x() + window.width() / 2;
            centerY = window.y();
            minX = window.x();
            maxX = window.x() + window.width();
            minY = window.y() - halfDepth;
            maxY = window.y() + halfDepth;
        } else {
            // Window on east/west wall: width along Y
            centerX = window.x();
            centerY = window.y() + window.width() / 2;
            minX = window.x() - halfDepth;
            maxX = window.x() + halfDepth;
            minY = window.y();
            maxY = window.y() + window.width();
        }

        double minZ = window.z();
        double maxZ = window.z() + window.height();
        // Phase 79: Compute attachment offset - library geometry is centered,
        // we need to offset so bottom (localMinZ) aligns with target sill level
        double translateZ = window.z() - libComp.localMinZ();

        // Write metadata
        writeElementMeta(windowGuid, "IfcWindow", "Library Window " + libComp.name(),
            "WINDOW", storeyName, minX, maxX, minY, maxY, minZ, maxZ);

        // Phase 79: Transform library geometry to world-space (with scaling if needed)
        // Maths: translateZ = targetZ - localMinZ, so localMinZ + translateZ = targetZ
        String geoHash = libraryMapper.transformAndWriteGeometryScaled(
            conn,
            libComp.geometryHash(),
            centerX, centerY, translateZ,
            rotateZ,
            result.scaleX(), result.scaleY(), result.scaleZ()
        );

        if (geoHash == null) {
            // Fallback to box
            System.out.println("[BuildingWriter] Window LOD400 not found, using box: " + windowGuid);
            BoxGeometry worldGeo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(worldGeo.vertices(), worldGeo.faces());
        }

        writeInstance(windowGuid, geoHash);

        // Phase 88: Direct wall→opening link for library windows
        WallRegion matchedWall = findWallForOpening(storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            writeAssemblyComponent(matchedWall.assemblyGuid(), windowGuid, "OPENING",
                minX - matchedWall.minX(), minY - matchedWall.minY(),
                minZ - matchedWall.minZ(), 100);
        }
    }

    private void writeLanding(LandingSpec landing, String storeyName) throws SQLException {
        String landingGuid = "LANDING_" + landing.name().toUpperCase() + "_" + storeyName;

        writeElement(
            landingGuid,
            "IfcSlab",
            "Stair Landing",
            "LANDING",
            storeyName,
            createBoxGeometry(
                landing.minX(), landing.minY(), landing.minZ(),
                landing.maxX(), landing.maxY(), landing.maxZ()
            )
        );
    }

    /**
     * Write sprinkler element (Phase 14B, Phase 64, Phase 79).
     * Uses IfcFireSuppressionTerminal for fire suppression (NFPA 13 compliant).
     * Phase 79: Uses LOD400 library geometry when available.
     */
    private void writeSprinkler(SprinklerSpec sprinkler, String storeyName) throws SQLException {
        String sprinklerGuid = "SPRINKLER_" + storeyName + "_" + sprinkler.id().toUpperCase();

        double size = BIMConstants.SPRINKLER_HEAD_RADIUS;
        double minX = sprinkler.x() - size;
        double maxX = sprinkler.x() + size;
        double minY = sprinkler.y() - size;
        double maxY = sprinkler.y() + size;
        double minZ = sprinkler.z() - BIMConstants.SPRINKLER_CEILING_DROP;
        double maxZ = sprinkler.z();

        String geoHash = null;

        // Phase 79: Try to use LOD400 library sprinkler geometry
        if (libraryMapper != null && libraryMapper.hasSprinklerComponents()) {
            var libComp = libraryMapper.getSprinklerComponent(sprinkler.type());
            if (libComp != null) {
                try {
                    // Phase 79: Sprinklers are ceiling-mounted (TOP attachment)
                    // Maths: localMaxZ = localMinZ + height; translateZ = attachZ - localMaxZ
                    double heightM = libComp.heightMm() / 1000.0;
                    double localMaxZ = libComp.localMinZ() + heightM;
                    double translateZ = sprinkler.z() - localMaxZ;

                    geoHash = libraryMapper.transformAndWriteGeometry(
                        conn, libComp.geometryHash(),
                        sprinkler.x(), sprinkler.y(), translateZ,
                        0  // No rotation for ceiling-mounted sprinklers
                    );
                    if (geoHash != null) {
                        librarySprinklerCount++;
                        // Phase 92C: Bbox must match LOD400 mesh, not parametric constants
                        // head maxZ = sprinkler.z() (attachment), minZ = maxZ - height
                        double halfW = libComp.widthMm() / 2000.0;
                        double halfD = libComp.depthMm() / 2000.0;
                        minX = sprinkler.x() - halfW;
                        maxX = sprinkler.x() + halfW;
                        minY = sprinkler.y() - halfD;
                        maxY = sprinkler.y() + halfD;
                        maxZ = sprinkler.z();
                        minZ = sprinkler.z() - heightM;
                    }
                } catch (SQLException e) {
                    // Fall through to parametric
                }
            }
        }

        // Fallback to parametric box geometry
        if (geoHash == null) {
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
            parametricSprinklerCount++;
        }

        writeElementMeta(sprinklerGuid, "IfcFireSuppressionTerminal", "Fire Sprinkler",
            sprinkler.type().toUpperCase(), storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is world-space (transformed), zero transform
        writeInstance(sprinklerGuid, geoHash);
    }

    /**
     * Write light fixture element (Phase 14B, enhanced Phase 33).
     * Uses IfcLightFixture for illumination.
     * Phase 33: Supports library geometry when available.
     */
    private void writeLight(LightSpec light, String storeyName) throws SQLException {
        String lightGuid = "LIGHT_" + storeyName + "_" + light.id().toUpperCase();

        // Phase 33: Use library geometry if available
        String geoHash = light.geometryHash();
        double halfW = light.width() / 2;
        double halfD = light.depth() / 2;
        double minX = light.x() - halfW;
        double maxX = light.x() + halfW;
        double minY = light.y() - halfD;
        double maxY = light.y() + halfD;
        // Phase 89: Drop lights below slab so they're visible
        double ceilingZ = light.z() - BIMConstants.STANDARD_SLAB_THICKNESS;
        double minZ = ceilingZ;
        double maxZ = ceilingZ + light.height();

        if (geoHash != null && !geoHash.isEmpty() && libraryMapper != null) {
            // Phase 79: Transform library geometry to world position with attachment offset
            try {
                // Lights are ceiling-mounted (TOP attachment)
                // Maths: For TOP attachment, translateZ = ceilingZ - localMaxZ
                double translateZ = ceilingZ;
                double[] zBounds = libraryMapper.getLocalZBounds(geoHash);
                if (zBounds != null) {
                    double localMaxZ = zBounds[1];
                    translateZ = ceilingZ - localMaxZ;
                }

                String transformedHash = libraryMapper.transformAndWriteGeometry(
                    conn, geoHash,
                    light.x(), light.y(), translateZ,
                    0  // No rotation for ceiling-mounted lights
                );
                if (transformedHash != null) {
                    geoHash = transformedHash;
                    libraryLightCount++;
                } else {
                    // Fall back to parametric
                    BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                    geoHash = writeGeometry(geo.vertices(), geo.faces());
                    parametricLightCount++;
                }
            } catch (SQLException e) {
                // Fall back to parametric
                BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                geoHash = writeGeometry(geo.vertices(), geo.faces());
                parametricLightCount++;
            }
        } else {
            // Parametric fallback
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
            parametricLightCount++;
        }

        writeElementMeta(lightGuid, "IfcLightFixture", "Light Fixture", light.fixtureType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is world-space (transformed), zero transform
        writeInstance(lightGuid, geoHash);
    }

    /**
     * Write HVAC diffuser element (Phase 89).
     * Ceiling-mounted: exhaust → IfcFan, supply/return → IfcAirTerminal.
     */
    private void writeDiffuser(DiffuserSpec diffuser, String storeyName) throws SQLException {
        String guid = "DIFFUSER_" + storeyName + "_" + diffuser.id().toUpperCase();

        // Map type to IFC class
        String ifcClass = diffuser.diffuserType().equals("exhaust") ? "IfcFan" : "IfcAirTerminal";

        // Phase 89: Drop below slab (same as lights)
        double ceilingZ = diffuser.z() - BIMConstants.STANDARD_SLAB_THICKNESS;

        // Parametric bounds (ceiling-mounted square)
        double size = 0.3;  // 300mm default
        double depth = 0.1; // 100mm default
        // Phase 92B: IfcFan with library mesh uses 1500mm bounds
        if (ifcClass.equals("IfcFan") && diffuser.geometryHash() != null && !diffuser.geometryHash().isEmpty()) {
            size = 0.75;  // 750mm half-width = 1500mm fan diameter
            depth = 0.35; // 350mm height
        }
        double minX = diffuser.x() - size / 2, maxX = diffuser.x() + size / 2;
        double minY = diffuser.y() - size / 2, maxY = diffuser.y() + size / 2;
        double minZ = ceilingZ - depth,         maxZ = ceilingZ;

        String geoHash = null;

        // Try library geometry (TOP attachment like lights)
        if (diffuser.geometryHash() != null && !diffuser.geometryHash().isEmpty() && libraryMapper != null) {
            try {
                double translateZ = ceilingZ;
                double[] zBounds = libraryMapper.getLocalZBounds(diffuser.geometryHash());
                if (zBounds != null) {
                    translateZ = ceilingZ - zBounds[1]; // TOP: align top of mesh to ceiling
                }
                geoHash = libraryMapper.transformAndWriteGeometry(
                    conn, diffuser.geometryHash(),
                    diffuser.x(), diffuser.y(), translateZ, 0);
            } catch (SQLException ignored) {}
        }

        if (geoHash == null) {
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
        }

        writeElementMeta(guid, ifcClass, diffuser.diffuserType() + " diffuser",
            "DIFFUSER", storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash);
        diffuserCount++;
    }

    /**
     * Write electrical element (outlet or switch) - Phase 33.
     * Uses IfcOutlet for power outlets, IfcSwitchingDevice for switches.
     */
    private void writeElectricalElement(ElectricalSpec elec, String storeyName) throws SQLException {
        String guid = "ELEC_" + elec.id().toUpperCase() + "_" + storeyName;

        // Map element type to IFC class
        String ifcClass = switch (elec.elementType().toLowerCase()) {
            case "outlet" -> "IfcOutlet";
            case "switch" -> "IfcSwitchingDevice";
            default -> "IfcElectricAppliance";
        };

        // Compute bounding box
        double halfW = elec.width() / 2;
        double halfD = elec.depth() / 2;
        double minX = elec.x() - halfW;
        double maxX = elec.x() + halfW;
        double minY = elec.y() - halfD;
        double maxY = elec.y() + halfD;
        double minZ = elec.z();
        double maxZ = elec.z() + elec.height();

        // Generate parametric box geometry (no library for outlets/switches yet)
        BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
        String geoHash = writeGeometry(geo.vertices(), geo.faces());

        writeElementMeta(guid, ifcClass, elec.elementType(), elec.elementType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is already world-space, zero transform
        writeInstance(guid, geoHash);
    }

    /**
     * Phase 100: Write fire detection alarm element.
     * Uses IfcAlarm with LOD400 geometry from library, parametric box fallback.
     */
    private void writeAlarm(AlarmSpec alarm, String storeyName) throws SQLException {
        String guid = "ALARM_" + alarm.id().toUpperCase() + "_" + storeyName;

        // Compute bounding box
        double halfW = alarm.width() / 2;
        double halfD = alarm.depth() / 2;
        double halfH = alarm.height() / 2;
        double minX = alarm.x() - halfW;
        double maxX = alarm.x() + halfW;
        double minY = alarm.y() - halfD;
        double maxY = alarm.y() + halfD;
        double minZ, maxZ;

        // Smoke/heat detectors mount on ceiling (z is ceiling, element hangs below)
        // Alarm bells/break glass mount on wall (z is mounting center)
        if ("smoke_detector".equals(alarm.alarmType()) || "heat_detector".equals(alarm.alarmType())) {
            maxZ = alarm.z();
            minZ = alarm.z() - alarm.height();
        } else {
            minZ = alarm.z() - halfH;
            maxZ = alarm.z() + halfH;
        }

        String geoHash = null;

        // Try LOD400 library geometry
        if (alarm.geometryHash() != null && libraryMapper != null) {
            try {
                geoHash = libraryMapper.transformAndWriteGeometry(
                    conn, alarm.geometryHash(), alarm.x(), alarm.y(), alarm.z(), 0.0);
                // Update bounds from library if available
                double[] bounds = libraryMapper.getLocalBounds(alarm.geometryHash());
                if (bounds != null) {
                    minX = alarm.x() + bounds[0]; maxX = alarm.x() + bounds[1];
                    minY = alarm.y() + bounds[2]; maxY = alarm.y() + bounds[3];
                    minZ = alarm.z() + bounds[4]; maxZ = alarm.z() + bounds[5];
                }
            } catch (SQLException ignored) {}
        }

        // Fallback to parametric box
        if (geoHash == null) {
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
        }

        writeElementMeta(guid, "IfcAlarm", alarm.alarmType(), alarm.alarmType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash);

        // Write code reference to element_properties for standards traceability
        if (alarm.codeRef() != null && !alarm.codeRef().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO element_properties (guid, pset_name, property_name, property_value) " +
                    "VALUES (?, 'Pset_IfcAlarmCommon', 'CodeReference', ?)")) {
                ps.setString(1, guid);
                ps.setString(2, alarm.codeRef());
                ps.execute();
            }
        }
    }

    /**
     * Write pipe segment (Phase 34: plumbing pipes).
     * Uses IfcPipeSegment for all pipe types.
     */
    private void writePipeSegment(PlumbingSpec pipe, String storeyName) throws SQLException {
        String guid = "PIPE_" + pipe.id().toUpperCase() + "_" + storeyName;

        // All plumbing pipes use IfcPipeSegment
        String ifcClass = "IfcPipeSegment";

        // Compute bounding box from pipe start/end and diameter
        double radius = pipe.diameterM() / 2;

        // Determine pipe direction and compute perpendicular extent
        double dx = pipe.endX() - pipe.startX();
        double dy = pipe.endY() - pipe.startY();
        double dz = pipe.endZ() - pipe.startZ();
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double extentX, extentY, extentZ;
        if (len < 0.001) {
            // Point pipe - use radius in all directions
            extentX = extentY = extentZ = radius;
        } else {
            // Radial extent perpendicular to pipe axis
            double dirX = dx / len;
            double dirY = dy / len;
            double dirZ = dz / len;
            extentX = radius * Math.sqrt(1 - dirX * dirX);
            extentY = radius * Math.sqrt(1 - dirY * dirY);
            extentZ = radius * Math.sqrt(1 - dirZ * dirZ);
        }

        double minX = Math.min(pipe.startX(), pipe.endX()) - extentX;
        double maxX = Math.max(pipe.startX(), pipe.endX()) + extentX;
        double minY = Math.min(pipe.startY(), pipe.endY()) - extentY;
        double maxY = Math.max(pipe.startY(), pipe.endY()) + extentY;
        double minZ = Math.min(pipe.startZ(), pipe.endZ()) - extentZ;
        double maxZ = Math.max(pipe.startZ(), pipe.endZ()) + extentZ;

        // Generate cylinder geometry for pipe (world-space coordinates)
        CylinderGeometry geo = createCylinderGeometry(
            pipe.startX(), pipe.startY(), pipe.startZ(),
            pipe.endX(), pipe.endY(), pipe.endZ(),
            radius
        );
        String geoHash = writeGeometry(geo.vertices(), geo.faces());

        writeElementMeta(guid, ifcClass, pipe.pipeType(), pipe.pipeType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is already world-space, zero transform
        writeInstance(guid, geoHash);
        pipeCount++;
    }

    /**
     * Phase 80: Write fire protection pipe segment.
     * Uses IfcPipeSegment with FP discipline for fire suppression piping.
     */
    private void writeFPPipeSegment(FPPipeSpec pipe, String storeyName) throws SQLException {
        String guid = "FP_" + pipe.id().toUpperCase() + "_" + storeyName;

        String ifcClass = "IfcPipeSegment";
        String discipline = "FP";
        String elementType = pipe.type().name();

        // Compute bounding box from pipe start/end and diameter
        double radius = pipe.diameterM() / 2;
        Point3D start = pipe.start();
        Point3D end = pipe.end();

        // Determine pipe direction and compute perpendicular extent
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double dz = end.z() - start.z();
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double extentX, extentY, extentZ;
        if (len < 0.001) {
            extentX = extentY = extentZ = radius;
        } else {
            double dirX = dx / len;
            double dirY = dy / len;
            double dirZ = dz / len;
            extentX = radius * Math.sqrt(1 - dirX * dirX);
            extentY = radius * Math.sqrt(1 - dirY * dirY);
            extentZ = radius * Math.sqrt(1 - dirZ * dirZ);
        }

        double minX = Math.min(start.x(), end.x()) - extentX;
        double maxX = Math.max(start.x(), end.x()) + extentX;
        double minY = Math.min(start.y(), end.y()) - extentY;
        double maxY = Math.max(start.y(), end.y()) + extentY;
        double minZ = Math.min(start.z(), end.z()) - extentZ;
        double maxZ = Math.max(start.z(), end.z()) + extentZ;

        // Generate cylinder geometry (world-space coordinates)
        CylinderGeometry geo = createCylinderGeometry(
            start.x(), start.y(), start.z(),
            end.x(), end.y(), end.z(),
            radius
        );
        String geoHash = writeGeometry(geo.vertices(), geo.faces());

        writeElementMeta(guid, ifcClass, discipline, elementType,
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash);
        fpPipeCount++;
    }

    // Phase 92C: Cached geometry hashes for T-assembly parts (loaded on first use)
    private String fpTeeHash, fpTransitionHash, fpDropHash;
    private boolean fpFittingHashesLoaded = false;

    private void loadFPFittingHashes() {
        if (fpFittingHashesLoaded) return;
        fpFittingHashesLoaded = true;
        try (var libConn = java.sql.DriverManager.getConnection("jdbc:sqlite:library/component_library.db")) {
            var rs = libConn.createStatement().executeQuery(
                "SELECT name, geometry_hash FROM component_definitions WHERE name IN ('FP_Tee_Threaded','FP_Transition_Fitting','FP_Drop_Pipe')");
            while (rs.next()) {
                switch (rs.getString("name")) {
                    case "FP_Tee_Threaded" -> fpTeeHash = rs.getString("geometry_hash");
                    case "FP_Transition_Fitting" -> fpTransitionHash = rs.getString("geometry_hash");
                    case "FP_Drop_Pipe" -> fpDropHash = rs.getString("geometry_hash");
                }
            }
        } catch (SQLException e) {
            System.out.println("[FP] Could not load T-assembly hashes: " + e.getMessage());
        }
    }

    /**
     * Phase 92C: Write T-assembly fitting (TEE, TRANSITION, DROP) with LOD400 geometry.
     * These are point elements placed at a center position with library mesh.
     */
    private void writeFPFitting(FPPipeSpec pipe, String storeyName) throws SQLException {
        loadFPFittingHashes();

        String guid = "FP_" + pipe.id().toUpperCase() + "_" + storeyName;
        String discipline = "FP";

        // Determine IFC class and library geometry hash
        String ifcClass;
        String libGeoHash;
        switch (pipe.type()) {
            case TEE -> { ifcClass = "IfcPipeFitting"; libGeoHash = fpTeeHash; }
            case TRANSITION -> { ifcClass = "IfcPipeFitting"; libGeoHash = fpTransitionHash; }
            case DROP -> { ifcClass = "IfcPipeSegment"; libGeoHash = fpDropHash; }
            default -> { ifcClass = "IfcPipeSegment"; libGeoHash = null; }
        }

        double cx = pipe.start().x();
        double cy = pipe.start().y();
        double cz = pipe.start().z();  // center Z of the fitting

        String geoHash = null;
        double minX = cx - 0.04, maxX = cx + 0.04;
        double minY = cy - 0.02, maxY = cy + 0.02;
        double minZ = cz - 0.03, maxZ = cz + 0.03;

        // Try LOD400 library geometry
        if (libGeoHash != null && libraryMapper != null) {
            try {
                // Phase 92C: TEE rotated π/2 so long arm (local X) aligns with MAIN pipe (world Y).
                // Transition and drop are nearly symmetric — no rotation needed.
                double rotation = (pipe.type() == FireSuppressionPlacer.FPPipeType.TEE)
                    ? Math.PI / 2.0 : 0.0;

                double[] bounds = libraryMapper.getLocalBounds(libGeoHash);
                double translateZ = cz;
                if (bounds != null) {
                    // bounds = [localMinX, localMaxX, localMinY, localMaxY, localMinZ, localMaxZ]
                    if (rotation != 0.0) {
                        // 90° rotation swaps X↔Y extents
                        double halfX = Math.max(Math.abs(bounds[2]), Math.abs(bounds[3]));
                        double halfY = Math.max(Math.abs(bounds[0]), Math.abs(bounds[1]));
                        minX = cx - halfX; maxX = cx + halfX;
                        minY = cy - halfY; maxY = cy + halfY;
                    } else {
                        minX = cx + bounds[0]; maxX = cx + bounds[1];
                        minY = cy + bounds[2]; maxY = cy + bounds[3];
                    }
                    minZ = cz + bounds[4]; maxZ = cz + bounds[5];
                }
                geoHash = libraryMapper.transformAndWriteGeometry(
                    conn, libGeoHash, cx, cy, translateZ, rotation);
                if (geoHash != null) fpPipeCount++;
            } catch (SQLException ignored) {}
        }

        // Fallback to small box
        if (geoHash == null) {
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
            fpPipeCount++;
        }

        writeElementMeta(guid, ifcClass, discipline, pipe.type().name(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash);
    }

    /**
     * Phase 50B.1: Write structural column.
     * Uses IfcColumn for proper IFC classification.
     */
    private void writeColumn(ColumnSpec column, String storeyName) throws SQLException {
        String guid = "COLUMN_" + column.id().toUpperCase() + "_" + storeyName;

        // Column bounds: centered at (x, y), extending from z to z+height
        double halfW = column.width() / 2;
        double halfD = column.depth() / 2;

        double minX = column.x() - halfW;
        double maxX = column.x() + halfW;
        double minY = column.y() - halfD;
        double maxY = column.y() + halfD;
        double minZ = column.z();
        double maxZ = column.z() + column.height();

        writeElement(
            guid,
            "IfcColumn",
            String.format("%.0fx%.0f", column.width() * 1000, column.depth() * 1000),
            column.columnType().toUpperCase(),
            storeyName,
            createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    /**
     * Phase 4 Contract Architecture: Write spanning column that crosses multiple storeys.
     * Uses continuityId for identification. Single INSERT with combined height.
     *
     * <p>Per Watchdog watch brief:
     * - Column base Z = storey.level (lowest storey)
     * - Column top Z = base + totalHeight (continuous through slabs)
     * - Single INSERT per continuityId
     */
    private void writeSpanningColumn(SpanningColumnInfo spanning) throws SQLException {
        // Use continuityId as unique identifier across storeys
        String guid = "COLUMN_" + spanning.continuityId().toUpperCase();

        // Column bounds: centered at (x, y), spanning from baseZ to baseZ + totalHeight
        double halfW = spanning.width() / 2;
        double halfD = spanning.depth() / 2;

        double minX = spanning.x() - halfW;
        double maxX = spanning.x() + halfW;
        double minY = spanning.y() - halfD;
        double maxY = spanning.y() + halfD;
        double minZ = spanning.baseZ();
        double maxZ = spanning.baseZ() + spanning.totalHeight();

        System.out.printf("[PHASE4] Spanning column %s: Z[%.2f-%.2f] = %.2fm (%d storeys)%n",
            spanning.continuityId(), minZ, maxZ, spanning.totalHeight(), spanning.storeyCount());

        writeElement(
            guid,
            "IfcColumn",
            String.format("%.0fx%.0f", spanning.width() * 1000, spanning.depth() * 1000),
            spanning.columnType().toUpperCase(),
            spanning.lowestStorey(),  // Attribute to lowest storey for containment
            createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    /**
     * Phase 50B.1: Write structural beam or lintel.
     * Per IFC 4x3: IfcBeam for all beam types including lintels (PredefinedType=LINTEL).
     * IfcMember reserved for wall studs, bracing, generic members.
     */
    private void writeBeam(BeamSpec beam, String storeyName) throws SQLException {
        String guid = "BEAM_" + beam.id().toUpperCase() + "_" + storeyName;

        // Beam bounds: centered at (x, y, z), with length along rotation axis
        double halfW = beam.width() / 2;
        double halfH = beam.height() / 2;
        double halfL = beam.length() / 2;

        // Apply rotation for beam direction
        double cos = Math.cos(beam.rotation());
        double sin = Math.sin(beam.rotation());

        // For simplicity, axis-aligned bounding box
        double extentX = Math.abs(halfL * cos) + halfW * Math.abs(sin);
        double extentY = Math.abs(halfL * sin) + halfW * Math.abs(cos);

        double minX = beam.x() - extentX;
        double maxX = beam.x() + extentX;
        double minY = beam.y() - extentY;
        double maxY = beam.y() + extentY;
        double minZ = beam.z() - halfH;
        double maxZ = beam.z() + halfH;

        // Per IFC 4x3: IfcBeam for all beam types (BEAM, LINTEL, JOIST, SPANDREL, T_BEAM)
        String ifcClass = "IfcBeam";

        writeElement(
            guid,
            ifcClass,
            String.format("%.0fx%.0f L=%.0f", beam.width() * 1000, beam.height() * 1000, beam.length() * 1000),
            beam.beamType().toUpperCase(),
            storeyName,
            createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    /**
     * Write plumbing fixture element (Phase 32, 59: LOD400 library integration).
     * Uses IfcFlowTerminal for sanitary fixtures, IfcFurniture for furniture.
     *
     * Phase 59: Now uses LOD400 library geometry when available.
     */
    private void writeFixture(FixtureSpec fixture, String storeyName) throws SQLException {
        String guid = "FIXTURE_" + fixture.id().toUpperCase() + "_" + storeyName;
        String fixtureType = fixture.fixtureType().toLowerCase();

        // Map fixture type to IFC class (Phase 59: added furniture types)
        String ifcClass = switch (fixtureType) {
            case "toilet", "urinal" -> "IfcSanitaryTerminal";
            case "sink" -> "IfcSanitaryTerminal";
            case "hand_bidet" -> "IfcSanitaryTerminal";
            case "floor_trap" -> "IfcSanitaryTerminal";
            case "exhaust_fan" -> "IfcFan";
            case "lobby_seating", "canteen_table", "workstation_desk", "workstation_chair",
                 "workstation_monitor", "corridor_bench", "generic_seating",
                 "visitor_chair", "visitor_table", "guest_seat" -> "IfcFurniture";
            default -> "IfcFlowTerminal";
        };

        // Compute bounding box in world coordinates (rotation-aware)
        double halfW = fixture.width() / 2;
        double halfD = fixture.depth() / 2;
        // Phase 91: Rotate bbox corners to get actual world extents
        double cos = Math.abs(Math.cos(fixture.rotation()));
        double sin = Math.abs(Math.sin(fixture.rotation()));
        double rotHalfW = halfW * cos + halfD * sin;
        double rotHalfD = halfW * sin + halfD * cos;
        double minX = fixture.x() - rotHalfW;
        double maxX = fixture.x() + rotHalfW;
        double minY = fixture.y() - rotHalfD;
        double maxY = fixture.y() + rotHalfD;
        double minZ = fixture.z();
        double maxZ = fixture.z() + fixture.height();

        String geoHash;

        // Phase 59: Use LOD400 library geometry if available
        if (fixture.geometryHash() != null && !fixture.geometryHash().isEmpty() && libraryMapper != null) {
            // Phase 89: ON_FLOOR attachment — bottom of mesh aligns to floor level
            double translateZ = fixture.z();
            try {
                double[] zBounds = libraryMapper.getLocalZBounds(fixture.geometryHash());
                if (zBounds != null) {
                    translateZ = fixture.z() - zBounds[0]; // zBounds[0] = localMinZ
                    double meshHeight = zBounds[1] - zBounds[0];
                    maxZ = fixture.z() + meshHeight;
                }
            } catch (SQLException ignored) {}

            // Transform library geometry to world position
            geoHash = libraryMapper.transformAndWriteGeometry(
                conn,
                fixture.geometryHash(),
                fixture.x(), fixture.y(), translateZ,
                fixture.rotation()
            );

            if (geoHash != null) {
                libraryFixtureCount++;
            } else {
                // Fallback to box geometry
                parametricFixtureCount++;
                BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                geoHash = writeGeometry(geo.vertices(), geo.faces());
            }
        } else {
            // Parametric box fallback
            parametricFixtureCount++;
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
        }

        writeElementMeta(guid, ifcClass, fixture.fixtureType(), fixture.fixtureType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash);
    }

    private void writeRoof(RoofSpec roof, String storeyName) throws SQLException {
        String roofGuid = "ROOF_" + roof.type();

        float[] vertices = new float[roof.vertices().size() * 3];
        for (int i = 0; i < roof.vertices().size(); i++) {
            Point3D v = roof.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[roof.faces().size() * 3];
        for (int i = 0; i < roof.faces().size(); i++) {
            int[] face = roof.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : roof.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = writeGeometry(vertices, faces);
        writeElementMeta(roofGuid, "IfcRoof", "Gable Roof", "PITCH_" + (int) roof.pitchDegrees(), storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(roofGuid, geoHash);
    }

    // =========================================================================
    // Phase 35: MEP System Graph Writing
    // =========================================================================

    /**
     * Write an MEP system graph to the database.
     */
    private void writeMEPSystem(MEPSystem system, String buildingGuid) throws SQLException {
        // Write system metadata
        String sql = """
            INSERT INTO mep_systems (system_id, system_type, building_guid,
                                      is_connected, is_complete, node_count, edge_count)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, system.getSystemId());
            stmt.setString(2, system.getType().name());
            stmt.setString(3, buildingGuid);
            stmt.setInt(4, system.isConnected() ? 1 : 0);
            stmt.setInt(5, system.isComplete() ? 1 : 0);
            stmt.setInt(6, system.getNodes().size());
            stmt.setInt(7, system.getEdges().size());
            stmt.executeUpdate();
        }

        // Write nodes
        for (SystemNode node : system.getNodes()) {
            writeSystemNode(system.getSystemId(), node);
        }

        // Write edges
        for (SystemEdge edge : system.getEdges()) {
            writeSystemEdge(system.getSystemId(), edge);
        }
    }

    /**
     * Write a system node to the database.
     */
    private void writeSystemNode(String systemId, SystemNode node) throws SQLException {
        // Phase 94A: OR IGNORE — repeated floors create identical riser nodes (same logical pipe)
        String sql = """
            INSERT OR IGNORE INTO system_nodes (node_id, system_id, element_guid, role, name, properties_json)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, node.nodeId());
            stmt.setString(2, systemId);
            stmt.setString(3, node.elementGuid());  // May be null for external nodes
            stmt.setString(4, node.role().name());
            stmt.setString(5, node.name());
            stmt.setString(6, propertiesToJson(node.properties()));
            stmt.executeUpdate();
        }
    }

    /**
     * Write a system edge to the database.
     */
    private void writeSystemEdge(String systemId, SystemEdge edge) throws SQLException {
        // Phase 94A: OR IGNORE — repeated floors create identical riser edges
        String sql = """
            INSERT OR IGNORE INTO system_edges (edge_id, system_id, from_node_id, to_node_id, edge_type, properties_json)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, edge.edgeId());
            stmt.setString(2, systemId);
            stmt.setString(3, edge.fromNodeId());
            stmt.setString(4, edge.toNodeId());
            stmt.setString(5, edge.type().name());
            stmt.setString(6, propertiesToJson(edge.properties()));
            stmt.executeUpdate();
        }
    }

    /**
     * Convert properties map to JSON string.
     */
    private String propertiesToJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private BoxGeometry createBoxGeometry(double x1, double y1, double z1,
                                           double x2, double y2, double z2) {
        // Ensure proper min/max ordering for rtree
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        float[] vertices = new float[] {
            (float)minX, (float)minY, (float)minZ,
            (float)maxX, (float)minY, (float)minZ,
            (float)maxX, (float)maxY, (float)minZ,
            (float)minX, (float)maxY, (float)minZ,
            (float)minX, (float)minY, (float)maxZ,
            (float)maxX, (float)minY, (float)maxZ,
            (float)maxX, (float)maxY, (float)maxZ,
            (float)minX, (float)maxY, (float)maxZ
        };

        int[] faces = new int[] {
            0, 1, 2, 0, 2, 3,  // bottom
            4, 6, 5, 4, 7, 6,  // top
            0, 4, 5, 0, 5, 1,  // front
            2, 6, 7, 2, 7, 3,  // back
            0, 3, 7, 0, 7, 4,  // left
            1, 5, 6, 1, 6, 2   // right
        };

        return new BoxGeometry(vertices, faces, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private record BoxGeometry(float[] vertices, int[] faces,
                               double minX, double maxX, double minY, double maxY,
                               double minZ, double maxZ) {}

    /**
     * Create cylinder geometry for pipe segments (Phase 34).
     * Approximates cylinder with 16 segments for reasonable file size.
     */
    private CylinderGeometry createCylinderGeometry(
            double startX, double startY, double startZ,
            double endX, double endY, double endZ,
            double radius) {

        int segments = 16;  // 16-segment cylinder approximation

        // Calculate pipe direction
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (len < 0.001) {
            // Degenerate case - return empty geometry
            return new CylinderGeometry(new float[0], new int[0]);
        }

        // Normalized direction
        double dirX = dx / len;
        double dirY = dy / len;
        double dirZ = dz / len;

        // Create perpendicular vectors
        double perpX1, perpY1, perpZ1;
        double perpX2, perpY2, perpZ2;

        if (Math.abs(dirZ) < 0.9) {
            // Not vertical - use Z cross dir
            perpX1 = -dirY;
            perpY1 = dirX;
            perpZ1 = 0;
            double perpLen = Math.sqrt(perpX1*perpX1 + perpY1*perpY1);
            perpX1 /= perpLen;
            perpY1 /= perpLen;
        } else {
            // Vertical - use X cross dir
            perpX1 = 0;
            perpY1 = -dirZ;
            perpZ1 = dirY;
            double perpLen = Math.sqrt(perpY1*perpY1 + perpZ1*perpZ1);
            perpY1 /= perpLen;
            perpZ1 /= perpLen;
        }

        // Second perpendicular (cross product of dir and perp1)
        perpX2 = dirY * perpZ1 - dirZ * perpY1;
        perpY2 = dirZ * perpX1 - dirX * perpZ1;
        perpZ2 = dirX * perpY1 - dirY * perpX1;

        // Generate vertices (2 circles x segments points)
        float[] vertices = new float[segments * 2 * 3];
        int vIdx = 0;

        for (int end = 0; end < 2; end++) {
            double cx = end == 0 ? startX : endX;
            double cy = end == 0 ? startY : endY;
            double cz = end == 0 ? startZ : endZ;

            for (int i = 0; i < segments; i++) {
                double angle = 2 * Math.PI * i / segments;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                vertices[vIdx++] = (float) (cx + radius * (cos * perpX1 + sin * perpX2));
                vertices[vIdx++] = (float) (cy + radius * (cos * perpY1 + sin * perpY2));
                vertices[vIdx++] = (float) (cz + radius * (cos * perpZ1 + sin * perpZ2));
            }
        }

        // Generate faces (side triangles + end caps)
        int sideFaces = segments * 2;
        int capFaces = (segments - 2) * 2;
        int[] faces = new int[(sideFaces + capFaces) * 3];
        int fIdx = 0;

        // Side faces
        for (int i = 0; i < segments; i++) {
            int i1 = i;
            int i2 = (i + 1) % segments;
            int j1 = i + segments;
            int j2 = ((i + 1) % segments) + segments;

            faces[fIdx++] = i1;
            faces[fIdx++] = i2;
            faces[fIdx++] = j2;

            faces[fIdx++] = i1;
            faces[fIdx++] = j2;
            faces[fIdx++] = j1;
        }

        // Start cap (fan from vertex 0)
        for (int i = 1; i < segments - 1; i++) {
            faces[fIdx++] = 0;
            faces[fIdx++] = i + 1;
            faces[fIdx++] = i;
        }

        // End cap (fan from vertex segments)
        for (int i = 1; i < segments - 1; i++) {
            faces[fIdx++] = segments;
            faces[fIdx++] = segments + i;
            faces[fIdx++] = segments + i + 1;
        }

        return new CylinderGeometry(vertices, faces);
    }

    private record CylinderGeometry(float[] vertices, int[] faces) {}

    /**
     * Write a physical element to the database.
     * CONTRACT: BoxGeometry MUST be world-space coordinates.
     * Uses Pattern B: world-space geometry + zero transform.
     */
    private void writeElement(String guid, String ifcClass, String name, String type,
                              String storey, BoxGeometry geo) throws SQLException {
        writeElement(guid, ifcClass, name, type, storey, geo, null);
    }

    /**
     * Write a physical element with optional fire rating (Phase 65).
     * @param fireRatingHr Fire rating in hours (null = no rating)
     */
    private void writeElement(String guid, String ifcClass, String name, String type,
                              String storey, BoxGeometry geo, Double fireRatingHr) throws SQLException {
        String geoHash = writeGeometry(geo.vertices(), geo.faces());
        writeElementMeta(guid, ifcClass, name, type, storey,
            geo.minX(), geo.maxX(), geo.minY(), geo.maxY(), geo.minZ(), geo.maxZ(), fireRatingHr);
        writeInstance(guid, geoHash);
    }

    private String writeGeometry(float[] vertices, int[] faces) throws SQLException {
        String hash = "GEO_" + Arrays.hashCode(vertices) + "_" + Arrays.hashCode(faces);

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO base_geometries VALUES (?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, hash);
            ps.setBytes(2, floatsToBlob(vertices));
            ps.setBytes(3, intsToBlob(faces));
            ps.setInt(4, vertices.length / 3);
            ps.setInt(5, faces.length / 3);
            ps.execute();
        }

        return hash;
    }

    /**
     * Infer discipline from IFC class (EXTRACTED from TypeDisciplineMapping)
     * For ambiguous types (pipes, slabs), infer from GUID prefix or use primary discipline.
     * For unmapped types (IfcOutlet, IfcSwitchingDevice), infer from GUID prefix.
     */
    private String inferDiscipline(String ifcClass, String guid) {
        com.bim.compiler.topology.BIMObjectType type =
            com.bim.compiler.topology.BIMObjectType.fromIfcClass(ifcClass);
        var disciplines = com.bim.compiler.topology.TypeDisciplineMapping.getDisciplinesForType(type);

        // For unmapped types, infer from GUID prefix (ELEC_, PLUMB_, etc.)
        if (disciplines.isEmpty()) {
            if (guid.startsWith("ELEC_")) return "ELEC";
            if (guid.startsWith("PLUMB_") || guid.startsWith("PIPE_")) return "SP";
            if (guid.startsWith("HVAC_") || guid.startsWith("ACMV_")) return "ACMV";
            if (guid.startsWith("ALARM_") || guid.startsWith("FP_")) return "FP";
            return "ARC";  // Default for unmapped types
        }

        // For types with single discipline, use it
        if (disciplines.size() == 1) {
            return disciplines.iterator().next().name();
        }

        // For ambiguous types (IFC_BUILDING_ELEMENT_PROXY, pipes), try GUID prefix first
        if (guid.startsWith("ELEC_")) return "ELEC";
        if (guid.startsWith("PLUMB_") || guid.startsWith("PIPE_")) return "SP";
        if (guid.startsWith("HVAC_") || guid.startsWith("ACMV_")) return "ACMV";
        if (guid.startsWith("FP_")) return "FP";

        // For ambiguous types without clear GUID, use discipline hierarchy
        // Structural takes precedence (for slabs that could be ARC or STR)
        if (disciplines.contains(com.bim.compiler.topology.Discipline.STR)) {
            return "STR";
        }
        if (disciplines.contains(com.bim.compiler.topology.Discipline.SP)) {
            return "SP";   // Plumbing for pipes
        }

        // Fallback to first discipline
        return disciplines.iterator().next().name();
    }

    private void writeElementMeta(String guid, String ifcClass, String name, String type,
                                  String storey, double minX, double maxX, double minY,
                                  double maxY, double minZ, double maxZ) throws SQLException {
        writeElementMeta(guid, ifcClass, name, type, storey, minX, maxX, minY, maxY, minZ, maxZ, null);
    }

    /**
     * Write element meta with optional fire rating (Phase 65).
     * @param fireRatingHr Fire rating in hours (null = no rating)
     */
    private void writeElementMeta(String guid, String ifcClass, String name, String type,
                                  String storey, double minX, double maxX, double minY,
                                  double maxY, double minZ, double maxZ, Double fireRatingHr) throws SQLException {
        int id = ++elementId;

        // Debug: track GUIDs (disabled)
        // System.out.println("  [DB] " + guid + " -> " + ifcClass);

        String discipline = inferDiscipline(ifcClass, guid);

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO elements_meta VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setInt(1, id);
            ps.setString(2, guid);
            ps.setString(3, discipline);
            ps.setString(4, ifcClass);
            ps.setString(5, name);
            ps.setString(6, type);
            ps.setString(7, storey);
            if (fireRatingHr != null) {
                ps.setDouble(8, fireRatingHr);
            } else {
                ps.setNull(8, java.sql.Types.REAL);
            }
            try {
                ps.execute();
            } catch (SQLException e) {
                System.err.println("GUID conflict: " + guid + " (" + ifcClass + ")");
                throw e;
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO elements_rtree VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setInt(1, id);
            ps.setDouble(2, minX);
            ps.setDouble(3, maxX);
            ps.setDouble(4, minY);
            ps.setDouble(5, maxY);
            ps.setDouble(6, minZ);
            ps.setDouble(7, maxZ);
            ps.execute();
        }
    }

    /**
     * Write element instance with ENFORCED Pattern B: zero transform.
     *
     * CONTRACT: All geometry MUST be world-space. Transform is always (0,0,0).
     * This prevents strewn objects caused by double-positioning (geometry + transform).
     *
     * Reference: TERMINAL DB pattern - all elements use world-space geometry + zero transform.
     *
     * @param guid Element GUID
     * @param geoHash Geometry hash (geometry MUST be world-space coordinates)
     */
    private void writeInstance(String guid, String geoHash) throws SQLException {
        // Write to element_instances (geometry reference only)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_instances VALUES (?, ?)"
        )) {
            ps.setString(1, guid);
            ps.setString(2, geoHash);
            ps.execute();
        }

        // ENFORCED: Pattern B - zero transform for world-space geometry
        writeElementTransform(guid, 0, 0, 0);
    }

    private void writeElementTransform(String guid, double centerX, double centerY, double centerZ)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_transforms VALUES (?, ?, ?, ?, 'compiled')"
        )) {
            ps.setString(1, guid);
            ps.setDouble(2, centerX);
            ps.setDouble(3, centerY);
            ps.setDouble(4, centerZ);
            ps.execute();
        }
    }

    private void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                        String role, double x, double y, double z, int seq)
            throws SQLException {
        writeAssemblyComponent(assemblyGuid, componentGuid, role, x, y, z, seq, false);
    }

    /**
     * Write assembly component with optional flag (Phase 29).
     * Optional components appear in BOM but may not have geometry (e.g., hardware).
     */
    private void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                        String role, double x, double y, double z, int seq,
                                        boolean optional) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO assembly_components VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            ps.setString(3, role);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setInt(7, seq);
            ps.setBoolean(8, optional);
            ps.execute();
        }
    }

    private void writeSpatialStructure(String guid, String type, String name, String parentGuid)
            throws SQLException {
        writeSpatialStructure(guid, type, name, parentGuid, null, null);
    }

    /**
     * Phase 43: Extended spatial structure for IfcSpace with object_type and predefined_type.
     */
    private void writeSpatialStructure(String guid, String type, String name, String parentGuid,
                                       String objectType, String predefinedType)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO spatial_structure VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, guid);
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setString(4, parentGuid);
            ps.setString(5, objectType);
            ps.setString(6, predefinedType);
            ps.execute();
        }
    }

    /**
     * Phase 43: Write element-to-space containment relationship.
     */
    private void writeSpaceContainment(String elementGuid, String spaceGuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR REPLACE INTO rel_contained_in_space VALUES (?, ?)"
        )) {
            ps.setString(1, elementGuid);
            ps.setString(2, spaceGuid);
            ps.execute();
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
        writeSpatialStructure(spaceGuid, "IfcSpace", room.name(), storeyGuid,
            room.type(), predefinedType);

        // Write space geometry to elements_meta and elements_rtree
        writeElement(spaceGuid, "IfcSpace", room.name(), room.type(), storeyName,
            createBoxGeometry(room.minX(), room.minY(), room.minZ(),
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

    private byte[] floatsToBlob(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * BIMConstants.BYTES_PER_FLOAT)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) buffer.putFloat(f);
        return buffer.array();
    }

    private byte[] intsToBlob(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * BIMConstants.BYTES_PER_INT)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (int i : ints) buffer.putInt(i);
        return buffer.array();
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
     * Tries to get git commit hash, falls back to version string.
     */
    static String getCodeVersion() {
        // Try to get git commit hash
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
        // Fallback to version
        return "v0.50.4";
    }

    // =========================================================================
    // Phase 4 Contract Architecture: Spanning Column Helper
    // =========================================================================

    /**
     * Mutable helper class to track column spanning across storeys.
     * Created during the pre-write scan phase and used to generate
     * a single combined IfcColumn element.
     *
     * <p>Per contract architecture spec:
     * - continuityId provides cross-storey identity
     * - totalHeight accumulates as storeys are added
     * - lowestStorey determines containment attribution
     */
    private static class SpanningColumnInfo {
        private final String continuityId;
        private final String columnType;
        private final double x, y;
        private final double baseZ;          // Z of lowest storey
        private double totalHeight;          // Accumulated height
        private final double width, depth;
        private final String geometryHash;
        private final String lowestStorey;
        private final List<String> storeys;

        SpanningColumnInfo(String continuityId, String columnType,
                          double x, double y, double baseZ, double height,
                          double width, double depth, String geometryHash,
                          String lowestStorey) {
            this.continuityId = continuityId;
            this.columnType = columnType;
            this.x = x;
            this.y = y;
            this.baseZ = baseZ;
            this.totalHeight = height;
            this.width = width;
            this.depth = depth;
            this.geometryHash = geometryHash;
            this.lowestStorey = lowestStorey;
            this.storeys = new ArrayList<>();
            this.storeys.add(lowestStorey);
        }

        void extendHeight(double additionalHeight) {
            this.totalHeight += additionalHeight;
        }

        void addStorey(String storeyName) {
            if (!storeys.contains(storeyName)) {
                storeys.add(storeyName);
            }
        }

        int storeyCount() { return storeys.size(); }
        String continuityId() { return continuityId; }
        String columnType() { return columnType; }
        double x() { return x; }
        double y() { return y; }
        double baseZ() { return baseZ; }
        double totalHeight() { return totalHeight; }
        double width() { return width; }
        double depth() { return depth; }
        String geometryHash() { return geometryHash; }
        String lowestStorey() { return lowestStorey; }
    }

    /**
     * Phase 4 Contract Architecture: Simplified helper for witness recording.
     * Only tracks height and storey count (not full geometry).
     */
    private static class SpanningColumnInfoForWitness {
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
