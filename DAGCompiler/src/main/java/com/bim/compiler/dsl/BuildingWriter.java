package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.eyes.shape.ShapeArchetype;
import com.bim.eyes.shape.ScaleBand;
import com.bim.eyes.shape.ShapeClassifier;
import com.bim.compiler.bom.walker.AssemblyStructureVisitor;
import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.contract.AssemblerFactory;
import com.bim.compiler.contract.IAssembler;
import com.bim.compiler.dsl.BuildingSpecs.*;
import static com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.library.FireSuppressionPlacer;
import com.bim.compiler.library.FireSuppressionPlacer.FPPipeSpec;
import com.bim.compiler.system.*;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.validation.PlacementProver;

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
 *
 * <p>ASSUMPTION: Emits IfcBuilding + IfcBuildingStorey spatial structure.
 * Infrastructure IFCs need IfcFacility + IfcFacilityPart instead.
 * See {@code docs/InfrastructureAnalysis.md}.
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
    private String currentBuildingName; // set during write() for BOM scoping

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
                    fire_rating_hr REAL,
                    material_name TEXT,
                    material_rgba TEXT,
                    element_ref TEXT
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

            // Rich material library tables
            stmt.execute("DROP TABLE IF EXISTS surface_styles");
            stmt.execute("DROP TABLE IF EXISTS material_layers");

            stmt.execute("""
                CREATE TABLE surface_styles (
                    style_name TEXT PRIMARY KEY,
                    surface_r REAL, surface_g REAL, surface_b REAL,
                    transparency REAL DEFAULT 0.0,
                    specular_r REAL, specular_g REAL, specular_b REAL,
                    specular_ratio REAL,
                    specular_exponent REAL,
                    reflectance_method TEXT DEFAULT 'NOTDEFINED',
                    side TEXT DEFAULT 'BOTH',
                    source TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE material_layers (
                    layer_set_name TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    material_name TEXT,
                    thickness_m REAL,
                    is_ventilated INTEGER DEFAULT 0,
                    PRIMARY KEY (layer_set_name, sequence)
                )
            """);

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

            // C_Order: transactional — created fresh each compile from C_DocType config.
            // C_OrderLine: generated at compile time from BOM explosion (not copied from BOM.db).
            stmt.execute("DROP TABLE IF EXISTS c_orderline");
            stmt.execute("DROP TABLE IF EXISTS c_order");

            stmt.execute("""
                CREATE TABLE c_order (
                    C_Order_ID             TEXT PRIMARY KEY,     -- ProjectName from C_DocType
                    Name                   TEXT NOT NULL,        -- building display name
                    DSLContent             TEXT NOT NULL,        -- DSL template (from C_DocType)
                    OutputDbPath           TEXT NOT NULL,        -- output path (from C_DocType)
                    ReferenceDbPath        TEXT,                 -- reference DB (from C_DocType)
                    IsActive               INTEGER DEFAULT 1,
                    SeqNo                  INTEGER DEFAULT 10,
                    ExpectedElements       INTEGER,             -- from C_DocType (std), updated post-compile
                    SpatialDigest          TEXT,                 -- computed post-compile (transactional)
                    Provenance             TEXT DEFAULT 'EXTRACTED',
                    Description            TEXT,
                    GeometryFailThreshold  INTEGER DEFAULT 0,
                    DocStatus              TEXT DEFAULT 'DR'     -- DR→IP→CO (transactional lifecycle)
                        CHECK(DocStatus IS NULL OR DocStatus IN ('DR','IP','CO','VO')),
                    AabbWidthMm            REAL,                -- std from C_DocType, updated post-compile
                    AabbDepthMm            REAL,
                    AabbHeightMm           REAL,
                    CompiledAt             TEXT,                 -- compile timestamp
                    CompilerVersion        TEXT,
                    C_DocType_ID           TEXT                  -- FK → C_DocType in BOM.db (cross-DB)
                )
            """);

            stmt.execute("""
                CREATE TABLE c_orderline (
                    C_OrderLine_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID       TEXT NOT NULL,       -- FK to c_order (was: building_type)
                    Storey           TEXT NOT NULL,       -- storey identifier (was: storey)
                    Name             TEXT NOT NULL,       -- element instance name (was: element_ref)
                    IfcClass         TEXT NOT NULL,       -- IFC entity type (was: ifc_class)
                    Discipline       TEXT DEFAULT 'ARC',  -- ARC|STR|MEP|FURN (was: discipline)
                    AD_Org_ID        INTEGER,             -- FK to AD_Org (ERP.db) — integer discipline
                    M_Product_ID     TEXT,                -- FK to M_Product (was: family_ref)
                    IsActive         INTEGER DEFAULT 1,   -- (was: is_active)
                    UNIQUE(C_Order_ID, Storey, Name)
                    -- §11.9 DROPPED → W_Verb_Node: host_type, host_ref, position_rule,
                    --   position_value, position_value_2, position_value_3, height_mm, orientation
                    -- §11.9 DROPPED → M_Product: width_mm, height_extent_mm, depth_mm,
                    --   geometry_hash, material_name, material_rgba
                )
            """);

            // ── PRODUCTION layer (HOW) — iDempiere Manufacturing ──
            // W_Verb_Node: one verb invocation per row
            // W_Verb_NodeProduct: structured parameters per verb
            stmt.execute("""
                CREATE TABLE W_Verb_Node (
                    W_Verb_Node_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                    C_Order_ID        TEXT NOT NULL REFERENCES c_order(C_Order_ID),
                    SeqNo             INTEGER NOT NULL DEFAULT 10,
                    Name              TEXT NOT NULL,
                    Description       TEXT NOT NULL,
                    S_Resource_ID     INTEGER,
                    M_Product_ID      TEXT,
                    IsActive          INTEGER DEFAULT 1,
                    DocStatus         TEXT DEFAULT 'DR'
                        CHECK(DocStatus IN ('DR','IP','CO','VO')),
                    last_result       TEXT,
                    element_count     INTEGER DEFAULT 0,
                    Created           TEXT DEFAULT (datetime('now')),
                    Updated           TEXT DEFAULT (datetime('now'))
                )
            """);

            stmt.execute("""
                CREATE TABLE W_Verb_NodeProduct (
                    W_Verb_NodeProduct_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                    W_Verb_Node_ID         INTEGER NOT NULL
                        REFERENCES W_Verb_Node(W_Verb_Node_ID),
                    Name                     TEXT NOT NULL,
                    Value                    TEXT NOT NULL,
                    ValueType                TEXT DEFAULT 'TEXT'
                        CHECK(ValueType IN ('TEXT','REAL','INTEGER')),
                    UNIQUE(W_Verb_Node_ID, Name)
                )
            """);
        }
    }

    /**
     * Write building spec to database.
     */
    public void write(BuildingSpec spec) throws SQLException {
        this.currentBuildingName = spec.name();

        // Phase 50B.1: Get construction system (default FRAMED for backwards compatibility)
        ConstructionSystem constructionSystem = spec.constructionSystem() != null
            ? spec.constructionSystem()
            : ConstructionSystem.FRAMED;

        // Create building in spatial structure
        String buildingGuid = "BUILDING_" + spec.name().toUpperCase().replace(" ", "_");
        ep.writeSpatialStructure(buildingGuid, "IfcBuilding", spec.name(), null);

        // Phase DE-1: When metadata-driven, suppress surplus compiled writers
        boolean hasMetadata = PlacementLoader.getInstance().hasPlacement(spec.name());

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
            // C12: When metadata-driven (EN-BLOC), skip — MeshBinder in emitGlobalPlacementElements
            // resolves library geometry for WYSIWYG fidelity with stone.
            if (storey.slab() != null && !hasMetadata) {
                ep.writeElement(
                    "SLAB_" + storey.name().toUpperCase(),
                    "IfcSlab",
                    storey.slab().name(),
                    storey.slab().type(),
                    storey.name(),
                    ep.createBoxGeometry(
                        storey.slab().minX(), storey.slab().minY(), storey.slab().minZ(),
                        storey.slab().maxX(), storey.slab().maxY(), storey.slab().maxZ()
                    ),
                    null, storey.slab().materialName(), storey.slab().materialRgba()
                );
            }

            // Phase A: Write additional slabs (bay slabs, ceiling slabs)
            // G4 fix: gate by !hasMetadata — metadata path emits slabs via emitGlobalPlacementElements
            if (storey.baySlabs() != null && !storey.baySlabs().isEmpty() && !hasMetadata) {
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
                        ),
                        null, baySlab.materialName(), baySlab.materialRgba()
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

        // Phase RM-6: Build MEP system from CONNECTS_TO dependency data
        buildMEPSystemFromDependencies(spec.name(), buildingGuid);

        // Phase 81B: Link openings (doors/windows) to wall assemblies
        linkOpeningsToWallAssemblies();

        // Phase 81B: Apply AD-driven BOM recipes (iDempiere M_BOM pattern)
        applyADBOMRecipes();

        // Copy rich material styles from component_library.db to output DB
        copySurfaceStyles();

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
        PlacementLoader pad = PlacementLoader.getInstance();
        String buildingName = spec.name();
        if (!pad.hasPlacement(buildingName)) return;

        // Phase BOM-2c: FLAT vs RELATIONAL source contract.
        // Elements consumed by StoreyCompiler.applyPlacementOverrides are RELATIONAL (compiled path
        // owns them: ctx.slab → StoreySpec.slab() → BuildingWriter.write() → already written).
        // emitGlobalPlacementElements is FLAT source: only writes elements NOT consumed by compiled path.
        // Deduplication is by explicit consumption registry (PlacementLoader.isConsumed), NOT by
        // storey name matching. The perStoreyClasses guard was deleted — it relied on an accidental
        // name mismatch between relational storey names and DSL compiled storey names, which broke
        // silently on MULTI_UNIT buildings (DX) where applyPlacementOverrides returns early.
        // [EXTRACTED: Phase BOM-2c — explicit source contract, see PlacementLoader.markConsumed()]

        // Collect compiled storey names (used for fixOpeningPositions post-write fixup — not deduplication)
        Set<String> compiledStoreys = new HashSet<>();
        for (StoreySpec s : spec.storeys()) {
            compiledStoreys.add(s.name());
        }

        // Get all placements — emit those NOT already handled by per-storey overrides
        List<PlacementLoader.Placement> allPlacements = pad.getAll(buildingName);
        int emitted = 0;
        int roofOverrides = 0;
        int bound = 0;

        // Open component library for LOD400 geometry resolution
        ComponentLibrary furnitureLibrary = null;
        try {
            furnitureLibrary = new ComponentLibrary("library/component_library.db");
        } catch (Exception e) {
            // Can't open library — falls back to box geometry
        }

        // Unified path: ALL buildings use MeshBinder for dimensional contract + scaling.
        // EN-BLOC buildings (EB_) get scale≈1.0 (same IFC source).
        // Generative buildings get closestFit matching.
        MeshBinder binder = null;
        java.util.List<DimensionalContractViolation> degradations = new java.util.ArrayList<>();
        if (furnitureLibrary != null && libraryMapper != null) {
            boolean closestFit = CompilerConfig.getInstance().isClosestFitEnabled();
            binder = new MeshBinder(furnitureLibrary, libraryMapper, conn, ep, closestFit);
        }

        // Pre-write mathematical proof (diagnostic — does not block emission)
        {
            List<PlacementProver.PlacementData> proofData = new java.util.ArrayList<>();
            for (PlacementLoader.Placement p : allPlacements) {
                proofData.add(new PlacementProver.PlacementData(
                    p.elementRef(), p.ifcClass(),
                    com.bim.compiler.validation.ProductCategory.resolve(p.ifcClass()),
                    p.elementRef(), p.storey(),
                    p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ()));
            }
            PlacementProver.ProofReport proofReport = PlacementProver.prove(proofData, buildingName);
            PlacementProver.printReport(proofReport);
            if (!proofReport.allCriticalProven()) {
                System.err.printf("[PROOF] %d critical violations — review before trusting output%n",
                    proofReport.criticalViolations());
            }
        }

        for (PlacementLoader.Placement p : allPlacements) {
            // Skip elements consumed by StoreyCompiler.applyPlacementOverrides (RELATIONAL source).
            // These were already written via the compiled path; emitting again would produce duplicates.
            if (PlacementLoader.getInstance().isConsumed(p.buildingType(), p.elementRef())) continue;

            // Discipline-aware GUID prefix mapping
            // Non-ARC: include class in GUID to avoid collisions within same discipline
            // (ordinal is unique per building+storey+class, not per building+storey+discipline)
            // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
            String discPrefix = p.discipline() == null ? "" : switch (p.discipline()) {
                case FP   -> "FP_MD_";
                case ELEC -> "ELEC_MD_";
                case ACMV -> "ACMV_MD_";
                case SP   -> "SP_MD_";
                case CW   -> "CW_MD_";
                case LPG  -> "LPG_MD_";
                case STR  -> "STR_MD_";
                default -> "";  // ARC, REB — use existing class-based logic
            };
            String guidPrefix;
            if (!discPrefix.isEmpty()) {
                guidPrefix = discPrefix + p.ifcClass().replace("Ifc", "").toUpperCase() + "_";
            } else {
                // CP-4 §4c: ARC GUID prefix from archetype, not IFC class
                double wMm = (p.maxX() - p.minX()) * 1000;
                double dMm = (p.maxY() - p.minY()) * 1000;
                double hMm = (p.maxZ() - p.minZ()) * 1000;
                var arch = ShapeClassifier.classifyArchetype(wMm, dMm, hMm);
                guidPrefix = switch (arch) {
                    case ELONGATED -> "FRAME_MD_";
                    case PLANAR    -> "SLAB_MD_";
                    case COMPACT   -> "COMPACT_MD_";
                    default        -> "MD_";
                };
            }
            String guid = guidPrefix + p.storey().replace(" ", "_").toUpperCase() + "_" + p.ordinal()
                    + (p.elementRef() != null && p.elementRef().startsWith("A_") ? "_A"
                     : p.elementRef() != null && p.elementRef().startsWith("B_") ? "_B" : "");

            // CP-4 §4c: element type from archetype, not IFC class
            double wMm2 = (p.maxX() - p.minX()) * 1000;
            double dMm2 = (p.maxY() - p.minY()) * 1000;
            double hMm2 = (p.maxZ() - p.minZ()) * 1000;
            var arch2 = ShapeClassifier.classifyArchetype(wMm2, dMm2, hMm2);
            var band2 = ShapeClassifier.classifyScaleBand(wMm2, dMm2, hMm2);
            String type = switch (arch2) {
                case PLANAR -> band2 == ScaleBand.ARCHITECTURAL ? "FLOOR" : "CURTAIN_PANEL";
                case ELONGATED -> "FRAME";
                case COMPACT -> "FITTING";
                default -> p.ifcClass();
            };

            // Roof detection: PLANAR or MIXED elements at roof storey height
            boolean isRoof = hMm2 > 0 && p.minZ() > 2.5
                    && (arch2 == ShapeArchetype.PLANAR
                        || arch2 == ShapeArchetype.MIXED)
                    && "IfcRoof".equals(p.ifcClass()); // Belt-and-suspenders: geometric filter + IFC class guard prevents high-Z slab misclassification
            if (isRoof) {
                dispatchOverrideRoof(p, roofOverrides, furnitureLibrary);
                roofOverrides++;
            } else if (binder != null) {
                // Unified path: MeshBinder enforces dimensional contract + scaling
                BoundElement be;
                try {
                    be = binder.bind(p, guid, type);
                    if (be == null) {
                        // Phase BOM-2a: BOM-generated elements — resolve LOD mesh from familyRef catalog ID.
                        // binder.bind() returns null (no I_Geometry_Map entry for BOM children).
                        // Try ComponentLibrary.getByName(familyRef) → scale → transformAndWriteGeometryScaled.
                        // Applies to FURN (furniture) and MEP (sprinklers, alarms, etc.) — any discipline
                        // with a familyRef pointing to a component_definitions entry.
                        if (p.familyRef() != null
                                && furnitureLibrary != null && libraryMapper != null) {
                            try {
                                var cd = furnitureLibrary.getByName(p.familyRef());
                                if (cd != null) {
                                    double[] lb = libraryMapper.getLocalBounds(cd.geometryHash());
                                    if (lb != null) {
                                        double meshW = lb[1] - lb[0];
                                        double meshD = lb[3] - lb[2];
                                        double meshH = lb[5] - lb[4];
                                        if (meshW > 0.001 && meshD > 0.001 && meshH > 0.001) {
                                            double sX = (p.maxX() - p.minX()) / meshW;
                                            double sY = (p.maxY() - p.minY()) / meshD;
                                            double sZ = (p.maxZ() - p.minZ()) / meshH;
                                            // Align scaled mesh min-corner to placement min-corner.
                                            // translateX = p.minX() - lb[0]*sX aligns any mesh origin.
                                            // Rotation suppressed: applying rotation around world-space
                                            // origin (not mesh center) overflows the placement bbox and
                                            // fails GIC. Rotation is a Phase BOM-3 concern.
                                            double tx = p.minX() - lb[0] * sX;
                                            double ty = p.minY() - lb[2] * sY;
                                            double tz = p.minZ() - lb[4] * sZ;
                                            String geoHash = libraryMapper.transformAndWriteGeometryScaled(
                                                conn, cd.geometryHash(), tx, ty, tz, 0.0, sX, sY, sZ);
                                            if (geoHash != null) {
                                                ep.writeElementMeta(guid, p.ifcClass(), p.familyRef(), type,
                                                    p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(),
                                                    p.minZ(), p.maxZ(), null, p.materialName(), p.materialRgba(),
                                                    p.elementRef());
                                                ep.writeInstance(guid, geoHash);
                                                bound++;
                                                continue;
                                            }
                                        }
                                    }
                                }
                            } catch (SQLException ex) { /* best-effort furniture LOD lookup */ }
                        }
                        // No geometry_map entry and no furnitureLibrary LOD match.
                        //
                        // FURN elements MUST have LOD geometry — the LOD X-Ray gate (X1-X4)
                        // requires vertex_count > 8. A bounding box is a false-green test pass.
                        // Fix: set m_bom_line.child_name_pattern to the exact
                        // component_definitions.name (LIKE wildcards never resolve here).
                        //
                        // Non-FURN elements (ARC walls, STR beams) without a geometry_map
                        // entry are either generative (box = correct) or known structural debt.
                        // These fall to [GEN-BOX] — visible in build output, not silent.
                        // NO FALLBACK — every element must have library geometry.
                        // FURN already threw here; now ALL disciplines do.
                        throw new MetadataMissingException(
                            "No geometry for " + p.ifcClass()
                            + " element_ref=" + p.elementRef()
                            + " familyRef=" + p.familyRef()
                            + " discipline=" + p.discipline()
                            + " [NO FALLBACK — add component_definitions + component_geometries in component_library.db]");
                    }
                } catch (DimensionalContractViolation e) {
                    // C13: No parametric mesh in pipeline (BBC.md §2).
                    // DimensionalContractViolation = library mesh doesn't fit.
                    // Fix the library data, do not fall back to parametric box.
                    degradations.add(e);
                    throw new MetadataMissingException(
                        "DimensionalContractViolation for " + p.ifcClass()
                        + " element_ref=" + p.elementRef()
                        + " " + e.getMessage()
                        + " [NO FALLBACK — fix library mesh or ASI, not parametric box]");
                }
                writeBoundElement(be);
                if (be.scaleRequired()) {
                    System.out.printf("[BIND] Scaled %s %s: %.2fx%.2fx%.2f%n",
                        p.ifcClass(), p.elementRef(), be.scaleX(), be.scaleY(), be.scaleZ());
                }
                bound++;
            } else {
                // NO FALLBACK — component library must be available.
                throw new MetadataMissingException(
                    "Component library unavailable for " + p.ifcClass()
                    + " element_ref=" + p.elementRef()
                    + " [NO FALLBACK — component_library.db must be present and readable]");
            }
        }

        if (furnitureLibrary != null) {
            try { furnitureLibrary.close(); } catch (Exception closeEx) { /* best-effort cleanup */ }
        }

        if (emitted > 0 || roofOverrides > 0 || bound > 0) {
            System.out.printf("[PLACEMENT] Global: emitted %d elements, %d roof overrides, %d bound (contract-checked)%n",
                emitted, roofOverrides, bound);
        }
        // Extreme scale handled in MeshBinder (log + proceed). Parametric fallback in catch.
        if (!degradations.isEmpty()) {
            System.out.printf("[PLACEMENT] %d elements used parametric fallback (extreme scale)%n", degradations.size());
        }

        // Fix bounding boxes for metadata-placed doors/windows on compiled storeys.
        // The DoorSpec/WindowSpec → OpeningWriter chain distorts orientation;
        // this post-write step corrects to exact reference positions via FIX OPENING BBOX verb.
        int fixed = 0;
        for (String compiledStorey : compiledStoreys) {
            fixed += dispatchFixOpeningBbox(buildingName, compiledStorey, "IfcDoor", "DOOR_MD_DOOR_");
            fixed += dispatchFixOpeningBbox(buildingName, compiledStorey, "IfcWindow", "WINDOW_MD_WIN_");
        }
        if (fixed > 0) {
            System.out.printf("[PLACEMENT] Fixed %d door/window bounding boxes to metadata positions%n", fixed);
        }
    }

    /**
     * Phase RM-6: Build MEP system graph from CONNECTS_TO dependency edges.
     * Reads ad_element_dependency where relation='CONNECTS_TO', creates MEPSystem,
     * and writes to output DB via MEPWriter.
     *
     * Node roles:
     *   - IfcFurnishingElement/IfcFlowTerminal = TERMINAL (fixtures)
     *   - IfcFlowSegment with family 'PVC' or underground = SOURCE (drain endpoint)
     *   - Other IfcFlowSegment = DISTRIBUTION (pipes)
     */
    private void buildMEPSystemFromDependencies(String buildingName, String buildingGuid) throws SQLException {
        PlacementLoader pad = PlacementLoader.getInstance();
        if (!pad.hasPlacement(buildingName)) return;

        // Load CONNECTS_TO edges from BOM.db
        List<String[]> edges = new ArrayList<>();
        try (Connection lib = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"))) {
            String sql = """
                SELECT element_ref, parent_ref
                FROM ad_element_dependency
                WHERE building_type = ? AND relation = 'CONNECTS_TO' AND is_active = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        edges.add(new String[]{rs.getString(1), rs.getString(2)});
                    }
                }
            }
        }

        if (edges.isEmpty()) return;

        // Collect all element refs participating in the graph
        Set<String> allRefs = new LinkedHashSet<>();
        for (String[] edge : edges) {
            allRefs.add(edge[0]);
            allRefs.add(edge[1]);
        }

        // Build placement lookup for element GUID resolution
        Map<String, PlacementLoader.Placement> placementByRef = new HashMap<>();
        for (PlacementLoader.Placement p : pad.getAll(buildingName)) {
            placementByRef.put(p.elementRef(), p);
        }

        // Create waste system
        MEPSystem wasteSystem = new MEPSystem(
            "PLUMBING_WASTE_" + buildingName, SystemType.PLUMBING_WASTE);

        // Add nodes
        Map<String, String> refToNodeId = new HashMap<>();
        for (String ref : allRefs) {
            String nodeId = "NODE_" + ref.replace(" ", "_").toUpperCase();
            refToNodeId.put(ref, nodeId);

            PlacementLoader.Placement p = placementByRef.get(ref);
            String elementGuid = p != null ? resolveGuid(ref, p) : null;

            // Determine role
            NodeRole role;
            if (ref.contains("FurnishingElement") || ref.contains("FlowTerminal")) {
                role = NodeRole.TERMINAL;
            } else if (ref.contains("FlowSegment_7")) {
                // Underground main = source (drain endpoint)
                role = NodeRole.SOURCE;
            } else {
                role = NodeRole.DISTRIBUTION;
            }

            wasteSystem.addNode(new SystemNode(nodeId, elementGuid, role, ref, Map.of()));
        }

        // Add edges
        int edgeIdx = 0;
        for (String[] edge : edges) {
            String fromId = refToNodeId.get(edge[0]);
            String toId = refToNodeId.get(edge[1]);
            if (fromId == null || toId == null) continue;

            wasteSystem.addEdge(new SystemEdge(
                "EDGE_" + (++edgeIdx),
                fromId, toId,
                EdgeType.DRAINS_TO,
                Map.of()
            ));
        }

        // Validate and write
        boolean connected = wasteSystem.isConnected();
        System.out.printf("[MEP] %s: %d nodes, %d edges, connected=%s%n",
            wasteSystem.getSystemId(), wasteSystem.getNodes().size(),
            wasteSystem.getEdges().size(), connected);

        mep.writeMEPSystem(wasteSystem, buildingGuid);
    }

    /**
     * Resolve the output DB GUID for an element given its elementRef and placement.
     */
    private String resolveGuid(String elementRef, PlacementLoader.Placement p) {
        // Match the GUID generation logic in emitGlobalPlacementElements
        String discPrefix = p.discipline() == null ? "" : switch (p.discipline()) {
            case FP   -> "FP_MD_";
            case ELEC -> "ELEC_MD_";
            case ACMV -> "ACMV_MD_";
            case SP   -> "SP_MD_";
            case CW   -> "CW_MD_";
            case LPG  -> "LPG_MD_";
            case STR  -> "STR_MD_";
            default -> "";  // ARC, REB
        };
        String guidPrefix;
        if (!discPrefix.isEmpty()) {
            guidPrefix = discPrefix + p.ifcClass().replace("Ifc", "").toUpperCase() + "_";
        } else {
                // CP-4 §4c: ARC GUID prefix from archetype
                double rwMm = (p.maxX() - p.minX()) * 1000;
                double rdMm = (p.maxY() - p.minY()) * 1000;
                double rhMm = (p.maxZ() - p.minZ()) * 1000;
                var rArch = ShapeClassifier.classifyArchetype(rwMm, rdMm, rhMm);
                guidPrefix = switch (rArch) {
                    case ELONGATED -> "FRAME_MD_";
                    case PLANAR    -> "SLAB_MD_";
                    case COMPACT   -> "COMPACT_MD_";
                    default        -> "MD_";
                };
            }
            return guidPrefix + p.storey().replace(" ", "_").toUpperCase() + "_" + p.ordinal();
    }

    /**
     * Phase H3: Override roof position using ElementPersistence authorized UPDATE path.
     * For roof index 0: updates existing IfcRoof element via ep helpers.
     * For additional roofs: emits new elements (INSERT, no UPDATE needed).
     */
    private void dispatchOverrideRoof(PlacementLoader.Placement p, int roofIndex,
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
                    // Authorized UPDATE via ElementPersistence helpers
                    ep.updateElementRtree(id, p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ());
                    ep.updateElementMeta(id, p.storey(), p.materialName(), p.materialRgba());
                    // Upgrade geometry from library if available
                    String libGeoHash = resolveLibraryGeometry(p, library);
                    if (libGeoHash != null) {
                        ep.updateInstanceGeometryByElementId(id, libGeoHash);
                    }
                }
            }
            if (!found) {
                // Phase DE-2: No compiled roof to override — emit from metadata
                String guid = "MD_ROOF_" + p.storey().replace(" ", "_").toUpperCase() + "_1";
                String geoHash = resolveLibraryGeometry(p, library);
                if (geoHash == null) geoHash = resolveRoofGeometry(p);
                String roofName = p.familyRef() != null ? p.familyRef() : p.elementRef();
                ep.writeElementMeta(guid, "IfcRoof", roofName, "ROOF",
                    p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ(),
                    null, p.materialName(), p.materialRgba(), p.elementRef());
                ep.writeInstance(guid, geoHash);
            }
        } else {
            // Additional roofs — emit as new elements (INSERTs only)
            String guid = "MD_ROOF_" + p.storey().replace(" ", "_").toUpperCase() + "_" + (roofIndex + 1);
            String geoHash = resolveLibraryGeometry(p, library);
            if (geoHash == null) geoHash = resolveRoofGeometry(p);
            String roofName2 = p.familyRef() != null ? p.familyRef() : p.elementRef();
            ep.writeElementMeta(guid, "IfcRoof", roofName2, "ROOF",
                p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ(),
                null, p.materialName(), p.materialRgba(), p.elementRef());
            ep.writeInstance(guid, geoHash);
        }
    }

    /**
     * Resolve library geometry for a placement element.
     * Looks up I_Geometry_Map by element_ref + ifc_class, then transforms
     * the local-coordinate mesh to world position.
     * Reusable for any element type with extracted reference geometry.
     *
     * NOTE: This is the legacy path used by SH/DX (IFC-extracted buildings).
     * For generative buildings (TB-LKTN), use MeshBinder instead — it enforces
     * the dimensional contract and applies scaling when mesh != bbox.
     *
     * @return geometry hash in output DB, or null if no library geometry available
     */
    private String resolveLibraryGeometry(PlacementLoader.Placement p,
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
     * Write a BoundElement to the output DB.
     * The BoundElement is the PROOF that mesh fits bbox — constructed by MeshBinder.
     * This method is a pure persistence layer: no resolution, no transformation.
     */
    private void writeBoundElement(BoundElement bound) throws SQLException {
        // Use familyRef as element_name when available (descriptive name from relational rules).
        // Fallback: productId (product name, e.g., "W1:W1a 3400 x 1250") — NOT elementRef
        // which may be a GUID (CP-1 MA identity). C8 groups by element_name prefix to measure
        // per-instance mesh diversity; GUIDs as element_name break the grouping.
        String elementName = bound.placement().familyRef() != null
            ? bound.placement().familyRef()
            : (bound.placement().productId() != null
                ? bound.placement().productId()
                : bound.elementRef());
        ep.writeElementMeta(bound.guid(), bound.ifcClass(), elementName, bound.type(),
            bound.storey(),
            bound.placement().minX(), bound.placement().maxX(),
            bound.placement().minY(), bound.placement().maxY(),
            bound.placement().minZ(), bound.placement().maxZ(),
            null, bound.materialName(), bound.materialRgba(),
            bound.placement().elementRef());
        ep.writeInstance(bound.guid(), bound.geometryHash());
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
    private String writeBoxGeometry(PlacementLoader.Placement p) throws SQLException {
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
     * Resolve roof geometry: gable mesh if orientation=GABLE_*, else box fallback.
     */
    private String resolveRoofGeometry(PlacementLoader.Placement p) throws SQLException {
        if (p.orientation() != null && p.orientation().startsWith("GABLE_")) {
            return writeGableGeometry(p);
        }
        return writeBoxGeometry(p);
    }

    /**
     * Generate gable roof mesh from placement bbox.
     * Ridge runs along the longer axis (X if dx >= dy, else Y).
     * Pitch encoded in orientation as GABLE_{degrees} — ridge height = maxZ from bbox.
     * 6 vertices (4 eave corners + 2 ridge endpoints), 6 triangular faces.
     */
    private String writeGableGeometry(PlacementLoader.Placement p) throws SQLException {
        float x0 = (float) p.minX(), x1 = (float) p.maxX();
        float y0 = (float) p.minY(), y1 = (float) p.maxY();
        float eaveZ = (float) p.minZ();
        float ridgeZ = (float) p.maxZ();

        boolean ridgeAlongX = (x1 - x0) >= (y1 - y0);

        float[] vertices;
        int[] faces;

        if (ridgeAlongX) {
            // Ridge runs East-West (along X). Span across Y.
            float ridgeY = (y0 + y1) / 2.0f;
            vertices = new float[] {
                x0, y0, eaveZ,    // 0: SW eave
                x1, y0, eaveZ,    // 1: SE eave
                x1, y1, eaveZ,    // 2: NE eave
                x0, y1, eaveZ,    // 3: NW eave
                x0, ridgeY, ridgeZ,  // 4: W ridge
                x1, ridgeY, ridgeZ   // 5: E ridge
            };
            faces = new int[] {
                0, 1, 5,  0, 5, 4,   // South slope
                3, 4, 5,  3, 5, 2,   // North slope
                0, 4, 3,             // West gable end
                1, 2, 5              // East gable end
            };
        } else {
            // Ridge runs North-South (along Y). Span across X.
            float ridgeX = (x0 + x1) / 2.0f;
            vertices = new float[] {
                x0, y0, eaveZ,       // 0: SW eave
                x1, y0, eaveZ,       // 1: SE eave
                x1, y1, eaveZ,       // 2: NE eave
                x0, y1, eaveZ,       // 3: NW eave
                ridgeX, y0, ridgeZ,  // 4: S ridge
                ridgeX, y1, ridgeZ   // 5: N ridge
            };
            // Pattern from BuildingCompiler.generateGableRoof (ridge along Y)
            faces = new int[] {
                0, 1, 4,             // South gable end
                3, 5, 2,             // North gable end
                0, 4, 5,  0, 5, 3,  // West slope
                1, 2, 5,  1, 5, 4   // East slope
            };
        }

        return ep.writeGeometry(vertices, faces);
    }

    /**
     * Phase H3: Fix bounding boxes for metadata-placed doors/windows.
     * Uses ElementPersistence authorized UPDATE path (no raw SQL).
     * Returns count of elements fixed.
     */
    private int dispatchFixOpeningBbox(String buildingName, String storeyName,
                                        String ifcClass, String guidPrefix) throws SQLException {
        PlacementLoader pad = PlacementLoader.getInstance();
        List<PlacementLoader.Placement> placements = pad.get(buildingName, storeyName, ifcClass);
        int fixed = 0;

        for (PlacementLoader.Placement p : placements) {
            String guid = guidPrefix + p.ordinal() + "_" + storeyName;

            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT m.id FROM elements_meta m WHERE m.guid = ?")) {
                ps.setString(1, guid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        // Authorized UPDATE via ElementPersistence helper
                        ep.updateElementRtree(id, p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ());
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
        try (IAssembler assembler = AssemblerFactory.wallOpeningAssembler(conn)) {
            assembler.assemble().print();
        } catch (Exception e) {
            System.err.println("[BuildingWriter] Wall+Opening assembly failed: " + e.getMessage());
        }
    }

    /**
     * Phase 81B: Apply AD-driven BOM recipes from component_library.db.
     */
    private void applyADBOMRecipes() {
        // NORM-3a Phase D: BOMAssemblerAD removed from pipeline; walker is now sole source.
        applyADBOMRecipesViaWalker();
    }

    /**
     * NORM-3a Phase D: AssemblyStructureVisitor (sole source, BOMAssemblerAD removed).
     *
     * <p>Walks all active BOMs via BOMWalker and creates element_assemblies +
     * assembly_components. INSERT OR IGNORE guards idempotency.
     */
    private void applyADBOMRecipesViaWalker() {
        String bomDbPath = System.getProperty("bom.db");
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath);
             Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            AssemblyStructureVisitor visitor = new AssemblyStructureVisitor(conn, bomConn);
            BOMWalker walker = new BOMWalker(bomConn, compConn);

            // Walk every active BOM as its own assembly root
            List<String> bomIds = loadAllActiveBomIds(bomConn);
            for (String bomId : bomIds) {
                walker.walkSelf(bomId, List.of(visitor), null);
            }
            visitor.flush(conn);
        } catch (SQLException e) {
            System.err.println("[BuildingWriter] BOMWalker assembly pass failed: " + e.getMessage());
        }
    }

    private List<String> loadAllActiveBomIds(Connection bomConn) throws SQLException {
        // Gap #1 fix: scope by docSubType to prevent assembly contamination
        String docSubType = lookupDocSubType(bomConn, currentBuildingName);
        List<String> ids = new ArrayList<>();
        if (docSubType != null) {
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "SELECT bom_id FROM m_bom WHERE is_active = 1"
                    + " AND (doc_sub_type = ? OR doc_sub_type IS NULL) ORDER BY bom_id")) {
                ps.setString(1, docSubType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(rs.getString(1));
                }
            }
        } else {
            // Fallback: no docSubType — only walk generic BOMs
            try (Statement stmt = bomConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT bom_id FROM m_bom WHERE is_active = 1 AND doc_sub_type IS NULL ORDER BY bom_id")) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    /** Look up DocSubType from C_DocType by ProjectName. */
    private String lookupDocSubType(Connection bomConn, String buildingName) throws SQLException {
        if (buildingName == null) return null;
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT DocSubType FROM C_DocType WHERE ProjectName = ?")) {
            ps.setString(1, buildingName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Copy surface_styles + material_layers from component_library.db to output DB.
     * Small tables (32-200 rows) — bulk copy is fine.
     */
    private void copySurfaceStyles() {
        try (Connection libConn = DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db")) {
            // Check if source tables exist
            boolean hasStyles = false;
            boolean hasLayers = false;
            try (ResultSet rs = libConn.getMetaData().getTables(null, null, "surface_styles", null)) {
                hasStyles = rs.next();
            }
            try (ResultSet rs = libConn.getMetaData().getTables(null, null, "material_layers", null)) {
                hasLayers = rs.next();
            }

            // Gap #2 fix: collect material names actually used in this output
            Set<String> usedMaterials = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet mrs = stmt.executeQuery(
                     "SELECT DISTINCT material_name FROM elements_meta WHERE material_name IS NOT NULL")) {
                while (mrs.next()) usedMaterials.add(mrs.getString(1));
            }

            int styleCount = 0;
            if (hasStyles) {
                try (Statement src = libConn.createStatement();
                     ResultSet rs = src.executeQuery("SELECT * FROM surface_styles");
                     PreparedStatement dst = conn.prepareStatement(
                         "INSERT OR REPLACE INTO surface_styles "
                         + "(style_name, surface_r, surface_g, surface_b, transparency, "
                         + "specular_r, specular_g, specular_b, specular_ratio, specular_exponent, "
                         + "reflectance_method, side, source) "
                         + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    while (rs.next()) {
                        String styleName = rs.getString("style_name");
                        if (!usedMaterials.contains(styleName)) continue; // Gap #2: skip unused
                        dst.setString(1, styleName);
                        dst.setObject(2, rs.getObject("surface_r"));
                        dst.setObject(3, rs.getObject("surface_g"));
                        dst.setObject(4, rs.getObject("surface_b"));
                        dst.setObject(5, rs.getObject("transparency"));
                        dst.setObject(6, rs.getObject("specular_r"));
                        dst.setObject(7, rs.getObject("specular_g"));
                        dst.setObject(8, rs.getObject("specular_b"));
                        dst.setObject(9, rs.getObject("specular_ratio"));
                        dst.setObject(10, rs.getObject("specular_exponent"));
                        dst.setString(11, rs.getString("reflectance_method"));
                        dst.setString(12, rs.getString("side"));
                        dst.setString(13, rs.getString("source"));
                        dst.executeUpdate();
                        styleCount++;
                    }
                }
            }

            int layerCount = 0;
            if (hasLayers) {
                try (Statement src = libConn.createStatement();
                     ResultSet rs = src.executeQuery("SELECT * FROM material_layers");
                     PreparedStatement dst = conn.prepareStatement(
                         "INSERT OR REPLACE INTO material_layers "
                         + "(layer_set_name, sequence, material_name, thickness_m, is_ventilated) "
                         + "VALUES (?,?,?,?,?)")) {
                    while (rs.next()) {
                        String layerSetName = rs.getString("layer_set_name");
                        if (!usedMaterials.contains(layerSetName)) continue; // Gap #3: skip unused
                        dst.setString(1, layerSetName);
                        dst.setInt(2, rs.getInt("sequence"));
                        dst.setString(3, rs.getString("material_name"));
                        dst.setObject(4, rs.getObject("thickness_m"));
                        dst.setInt(5, rs.getInt("is_ventilated"));
                        dst.executeUpdate();
                        layerCount++;
                    }
                }
            }

            if (styleCount > 0 || layerCount > 0) {
                System.out.printf("[MATERIALS] Copied %d surface styles, %d material layers from library%n",
                    styleCount, layerCount);
            }
        } catch (SQLException e) {
            // Library not available or tables don't exist yet — not fatal
            System.out.println("[MATERIALS] Style copy skipped: " + e.getMessage());
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
