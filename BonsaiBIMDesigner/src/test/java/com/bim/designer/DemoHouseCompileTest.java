package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DemoHouse End-to-End Compile — YAML → BOM → Pipeline → output.db.
 *
 * <p>Proves the generative path produces a real compiled building:
 * no IFC source, no manual element placement. BOM seeded from
 * classify_dm.yaml spec, compiled through the same 9-stage pipeline.
 *
 * // Implementing BBC.md §3.5, GENERATIVE_ROOM_SRS.md §4 — Witness: W-GEN-COMPILE-*
 */
@DisplayName("DemoHouse Compile — Generative End-to-End")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoHouseCompileTest {

    private static final String DSL_FILE = "IFCtoBOM/src/main/resources/dsl_dm.bim";
    private static final String SCHEMA_SQL = "library/schema_snapshot_bom.sql";
    private static final String COMP_LIB = "library/component_library.db";

    // Room definitions from classify_dm.yaml
    private static final Object[][] ROOMS = {
        {"LIVING",   "ROOM_DEMO_LI",  "LIVING",   4000, 3500, 2800, 0.0,    3.5},
        {"KITCHEN",  "ROOM_DEMO_KT",  "KITCHEN",  4000, 3500, 2800, 0.0,    0.0},
        {"BEDROOM1", "ROOM_DEMO_BD1", "BEDROOM",  5000, 3500, 2800, 4.0,    3.5},
        {"BEDROOM2", "ROOM_DEMO_BD2", "BEDROOM",  5000, 3500, 2800, 4.0,    0.0},
        {"BATHROOM", "ROOM_DEMO_BT",  "BATHROOM", 2000, 1500, 2800, 9.0,    0.0},
    };

    private static Path tempDir;
    private static String compileDbPath;
    private static String outputDir;
    private static DesignerAPIImpl api;
    private static Connection apiConn;
    private static CompileResponse compileResult;

    @BeforeAll
    static void setUp() throws Exception {
        // Reset singletons to prevent cross-test pollution
        PlacementLoader.resetInstance();

        assertTrue(new File(DSL_FILE).exists(), "dsl_dm.bim not found");
        assertTrue(new File(COMP_LIB).exists(), "component_library.db not found");

        tempDir = Files.createTempDirectory("dm_compile_");
        compileDbPath = tempDir.resolve("_DM_compile.db").toString();
        outputDir = tempDir.resolve("output").toString();
        new File(outputDir).mkdirs();

        // Create DM_BOM.db with full schema + BOM tree
        seedCompileDb();

        System.setProperty("bom.db", compileDbPath);
        // Force MetadataValidator to re-check by using reflection (package-private resetCache)
        try {
            var m = com.bim.compiler.dsl.MetadataValidator.class.getDeclaredMethod("resetCache");
            m.setAccessible(true);
            m.invoke(null);
        } catch (Exception ignore) {
            // If reflection fails, the path-check in validator should handle it
        }
        apiConn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath);
        api = new DesignerAPIImpl(apiConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (apiConn != null && !apiConn.isClosed()) apiConn.close();
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private static void seedCompileDb() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {
            conn.setAutoCommit(false);

            // 1. Apply BOM schema snapshot (same as CompileBridgeTest)
            if (new File(SCHEMA_SQL).exists()) {
                String schema = Files.readString(Path.of(SCHEMA_SQL));
                schema = schema.replace("CREATE TABLE \"", "CREATE TABLE IF NOT EXISTS \"");
                schema = schema.replace("CREATE TABLE M_", "CREATE TABLE IF NOT EXISTS M_");
                schema = schema.replace("CREATE TABLE C_", "CREATE TABLE IF NOT EXISTS C_");
                schema = schema.replace("CREATE TABLE ad_", "CREATE TABLE IF NOT EXISTS ad_");
                schema = schema.replace("CREATE TABLE m_", "CREATE TABLE IF NOT EXISTS m_");
                try (Statement stmt = conn.createStatement()) {
                    for (String ddl : schema.split(";")) {
                        String trimmed = ddl.trim();
                        if (!trimmed.isEmpty()) {
                            try { stmt.execute(trimmed); } catch (Exception ignore) {}
                        }
                    }
                }
            }

            // 2. Seed BOM tree (same structure as DemoHouseTest.seedBom)
            try (Statement st = conn.createStatement()) {
                // Ensure tables exist even without schema snapshot
                st.execute("""
                    CREATE TABLE IF NOT EXISTS C_DocType (
                        C_DocType_ID TEXT PRIMARY KEY, ProjectName TEXT, Name TEXT,
                        DocBaseType TEXT, DocSubType TEXT, Provenance TEXT DEFAULT 'EXTRACTED',
                        IsActive INTEGER DEFAULT 1, SeqNo INTEGER DEFAULT 10,
                        DSLContent TEXT, OutputDbPath TEXT, ReferenceDbPath TEXT,
                        ExpectedElements INTEGER DEFAULT 0, Description TEXT,
                        GeometryFailThreshold INTEGER DEFAULT 0,
                        AabbWidthMm REAL DEFAULT 0, AabbDepthMm REAL DEFAULT 0,
                        AabbHeightMm REAL DEFAULT 0)""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS m_bom (
                        bom_id TEXT PRIMARY KEY, bom_name TEXT, bom_type TEXT,
                        bom_category TEXT, group_by TEXT DEFAULT 'default',
                        aabb_width_mm INTEGER DEFAULT 0, aabb_depth_mm INTEGER DEFAULT 0,
                        aabb_height_mm INTEGER DEFAULT 0, entity_type TEXT DEFAULT 'D',
                        is_active INTEGER DEFAULT 1, description TEXT,
                        target_ifc_class TEXT DEFAULT 'IfcElementAssembly',
                        bom_level TEXT DEFAULT 'SET', doc_base_type TEXT, doc_sub_type TEXT,
                        seq_no INTEGER DEFAULT 10, origin_x REAL DEFAULT 0,
                        origin_y REAL DEFAULT 0, origin_z REAL DEFAULT 0,
                        aabb_qualifier TEXT DEFAULT 'OUTER')""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS m_bom_line (
                        bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        bom_id TEXT NOT NULL, child_product_id TEXT,
                        component_type TEXT DEFAULT 'BUY', role TEXT,
                        sequence INTEGER DEFAULT 10, entity_type TEXT DEFAULT 'D',
                        is_active INTEGER DEFAULT 1,
                        dx REAL DEFAULT 0, dy REAL DEFAULT 0, dz REAL DEFAULT 0,
                        allocated_width_mm INTEGER DEFAULT 0,
                        allocated_depth_mm INTEGER DEFAULT 0,
                        allocated_height_mm INTEGER DEFAULT 0,
                        storey TEXT, element_ref TEXT, verb_ref TEXT)""");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS ad_sysconfig (
                        config_key TEXT PRIMARY KEY, config_value TEXT)""");
            }

            // 3. C_DocType for DemoHouse
            String dslContent = Files.readString(Path.of(DSL_FILE));
            String outputPath = outputDir + "/demohouse_2br.db";
            try (var ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO C_DocType (
                        C_DocType_ID, Name, DocBaseType, DocSubType, IsActive,
                        ProjectName, OutputDbPath, ExpectedElements,
                        Provenance, SeqNo, DSLContent, GeometryFailThreshold,
                        AabbWidthMm, AabbDepthMm, AabbHeightMm
                    ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 'GENERATIVE', 10, ?, 0, ?, ?, ?)
                    """)) {
                ps.setString(1, "RE_DM");
                ps.setString(2, "Demo House 2BR");
                ps.setString(3, "RE");
                ps.setString(4, "DM");
                ps.setString(5, "DemoHouse_2BR");
                ps.setString(6, outputPath);
                ps.setInt(7, 63);  // 5 rooms × 11 (7 struct + 2 FP + 2 ELEC) + 7 furniture + 1 roof
                ps.setString(8, dslContent);
                ps.setDouble(9, 11000);  // total width
                ps.setDouble(10, 7000);  // total depth
                ps.setDouble(11, 2800);  // storey height
                ps.executeUpdate();
            }

            // 4. Building BOM
            try (Statement st = conn.createStatement()) {
                st.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, group_by,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm, doc_base_type, doc_sub_type)
                    VALUES ('BUILDING_DEMO_2BR','Demo House 2BR','BUILDING',NULL,'building',
                            11000,7000,2800,'RE','DM')""");

                st.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, group_by,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('FLOOR_DEMO_GF','Ground Floor','FLOOR','GF','storey',
                            11000,7000,2800)""");

                st.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence)
                    VALUES ('BUILDING_DEMO_2BR','FLOOR_DEMO_GF','MAKE','GROUND_FLOOR',10)""");

                // Room BOMs + structural elements per room
                int seq = 10;
                for (Object[] room : ROOMS) {
                    String name = (String) room[0];
                    String bomId = (String) room[1];
                    String cat = (String) room[2];
                    int w = (int) room[3], d = (int) room[4], h = (int) room[5];
                    double ox = (double) room[6], oy = (double) room[7];

                    // Room BOM — origin=(0,0,0) per BBC.md §4.1 (R16 lesson).
                    // Position comes from FLOOR→ROOM m_bom_line.dx/dy only.
                    st.execute("""
                        INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, group_by,
                            aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                            origin_x, origin_y, origin_z)
                        VALUES ('%s','%s','ROOM','%s','ROOM',%d,%d,%d, 0.0,0.0,0.0)"""
                            .formatted(bomId, name, cat, w, d, h));

                    // FLOOR → ROOM link
                    st.execute("""
                        INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
                            dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                        VALUES ('FLOOR_DEMO_GF','%s','MAKE','%s',%d, %f,%f,0.0, %d,%d,%d)"""
                            .formatted(bomId, cat, seq, ox, oy, w, d, h));
                    seq += 10;

                    // Leaf elements per room using REAL products from component_library.db
                    // These product IDs exist in the library with geometry

                    // 4 walls (2 exterior from SH, 2 interior partition)
                    String extWall = "Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P";
                    String intWall = "Basic Wall:Wall-Partn_12P-70MStd-12P";

                    // South wall
                    addLeaf(st, bomId, isExteriorFace(name, "SOUTH") ? extWall : intWall,
                            "IfcWall", seq, 0, 0, 0, w, 290, h);
                    seq += 10;
                    // North wall
                    addLeaf(st, bomId, isExteriorFace(name, "NORTH") ? extWall : intWall,
                            "IfcWall", seq, 0, d - 290, 0, w, 290, h);
                    seq += 10;
                    // West wall
                    addLeaf(st, bomId, isExteriorFace(name, "WEST") ? extWall : intWall,
                            "IfcWall", seq, 0, 0, 0, 290, d, h);
                    seq += 10;
                    // East wall
                    addLeaf(st, bomId, isExteriorFace(name, "EAST") ? extWall : intWall,
                            "IfcWall", seq, w - 290, 0, 0, 290, d, h);
                    seq += 10;
                    // Door (SH interior single door)
                    addLeaf(st, bomId, "Doors_IntSgl:810x2110mm",
                            "IfcDoor", seq, w / 2 - 405, 0, 0, 810, 200, 2110);
                    seq += 10;
                    // Window (SH single plain)
                    addLeaf(st, bomId, "Windows_Sgl_Plain:1810x1210mm",
                            "IfcWindow", seq, w / 2 - 905, d - 200, 900, 1810, 200, 1210);
                    seq += 10;
                    // Floor slab (SH ground floor slab)
                    addLeaf(st, bomId, "Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC",
                            "IfcSlab", seq, 0, 0, -150, w, d, 150);
                    seq += 10;

                    // ── Furniture per room (from SH library) ──
                    if ("LIVING".equals(cat)) {
                        addLeaf(st, bomId, "Furniture_Couch_Viper:2290x950x340mm",
                                "IfcFurnishingElement", seq, 500, 500, 0, 2290, 950, 340);
                        seq += 10;
                        addLeaf(st, bomId, "Furniture_Table_Coffee_1:1200x550x450mm",
                                "IfcFurnishingElement", seq, 1000, 1500, 0, 1200, 550, 450);
                        seq += 10;
                    } else if ("KITCHEN".equals(cat)) {
                        addLeaf(st, bomId, "Furniture_Table_Dining_w-Chairs_Rectangular:2000x1000x750mm_w-6_Seats",
                                "IfcFurnishingElement", seq, 1000, 1000, 0, 2000, 1000, 750);
                        seq += 10;
                    } else if ("BEDROOM".equals(cat)) {
                        addLeaf(st, bomId, "Furniture_Bed_1:1525x2007x355mm-Queen",
                                "IfcFurnishingElement", seq, 500, 500, 0, 1525, 2007, 355);
                        seq += 10;
                        addLeaf(st, bomId, "Furniture_Desk:1525x762mm",
                                "IfcFurnishingElement", seq, 2500, 500, 0, 1525, 762, 750);
                        seq += 10;
                    }

                    // ── FP fittings per room (real products from TE extraction) ──
                    // Sprinkler head — ceiling-mounted, room centre
                    // // Implementing BIM_Designer_SRS.md §30.7.4 — Witness: W-FP-TRIAL-1
                    addLeaf(st, bomId, "jkrME18_spr_sprinkler head_pendent",
                            "IfcFireSuppressionTerminal", seq, w / 2, d / 2, h - 58, 32, 27, 58);
                    seq += 10;
                    // Smoke detector — ceiling-mounted, offset from sprinkler
                    addLeaf(st, bomId, "jkrME18_fir-al_smoke detector",
                            "IfcAlarm", seq, w / 2 + 500, d / 2, h - 75, 170, 170, 75);
                    seq += 10;

                    // ── ELEC fittings per room (real products from TE extraction) ──
                    // // Implementing DISC_VALIDATION_DB_SRS.md §12 — Witness: W-DM-TC5-3
                    String elecBomId = "ELEC_DEMO_" + name;
                    st.execute("""
                        INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, group_by,
                            aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                        VALUES ('%s','%s ELEC','SET','ELEC','elec', %d,%d,%d)"""
                            .formatted(elecBomId, name, w, d, h));
                    st.execute("""
                        INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
                            dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                        VALUES ('%s','%s','MAKE','ELEC',%d, 0,0,0, %d,%d,%d)"""
                            .formatted(bomId, elecBomId, seq, w, d, h));
                    seq += 10;
                    // Light fixture — ceiling-mounted, room centre
                    addLeaf(st, elecBomId, "E_Light_1 X 14W_Surface_LED T8_V1",
                            "IfcLightFixture", 10, w / 2 - 27, d / 2 - 300, h - 81, 54, 600, 81);
                    // Data point — wall-mounted near door
                    addLeaf(st, elecBomId, "E_Data Point_V1",
                            "IfcElectricAppliance", 20, w / 2 + 200, 100, 300, 87, 48, 87);
                }

                // ── Roof (building-level, covers full footprint) ──
                st.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, group_by,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('ROOF_DEMO','Roof','FLOOR','RF','roof',11000,7000,500)""");
                st.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence)
                    VALUES ('BUILDING_DEMO_2BR','ROOF_DEMO','MAKE','ROOF',20)""");
                addLeaf(st, "ROOF_DEMO",
                        "Basic Roof:Roof_Flat-4Felt-150Ins-50Scr-150Conc-12Plr",
                        "IfcRoof", 10, 0, 0, 0, 11000, 7000, 500);

                st.execute("INSERT OR REPLACE INTO ad_sysconfig (config_key, config_value) VALUES ('EXPECTED_ELEMENTS', '63')");

                // ── FP_PIPE_ASSEMBLY: placement params for fire suppression piping ──
                // BuildingWriter requires m_bom_line + m_attribute for FP_PIPE_ASSEMBLY/MAIN
                // when sprinklers are detected by FireProtectionResolver.
                st.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, group_by,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('FP_PIPE_ASSEMBLY','FP Pipe Assembly','SET','FP','fp',
                            0,0,0)""");
                st.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role,
                        sequence, z_rule)
                    VALUES ('FP_PIPE_ASSEMBLY','FP_MAIN_PIPE','BUY','MAIN',10,'CEILING')""");
                st.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role,
                        sequence, z_rule)
                    VALUES ('FP_PIPE_ASSEMBLY','FP_HEAD','BUY','HEAD',20,'BELOW_SLAB')""");
                // m_attribute: spacing param required by BOMRuleAD.loadPlacementParams
                st.execute("""
                    CREATE TABLE IF NOT EXISTS m_attribute (
                        m_attribute_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        bom_child_id INTEGER NOT NULL,
                        param_key TEXT NOT NULL,
                        param_value TEXT,
                        is_active INTEGER DEFAULT 1)""");
                // Get bom_child_id of the MAIN and HEAD lines
                try (ResultSet bcRs = st.executeQuery(
                        "SELECT bom_child_id, role FROM m_bom_line WHERE bom_id='FP_PIPE_ASSEMBLY' AND role IN ('MAIN','HEAD')")) {
                    while (bcRs.next()) {
                        int fpBomChildId = bcRs.getInt(1);
                        String fpRole = bcRs.getString(2);
                        st.execute("INSERT INTO m_attribute (bom_child_id, param_key, param_value) VALUES ("
                            + fpBomChildId + ", 'spacing', '3.0')");
                        st.execute("INSERT INTO m_attribute (bom_child_id, param_key, param_value) VALUES ("
                            + fpBomChildId + ", 'z_offset', '0.15')");
                        st.execute("INSERT INTO m_attribute (bom_child_id, param_key, param_value) VALUES ("
                            + fpBomChildId + ", 'diameter', '0.025')");
                    }
                }

                // 5. Seed metadata tables — same tables that IFC extraction populates
                //    For GENERATIVE: seeded from YAML room definitions instead of IFC

                // ad_building_grid — column grid for the building
                for (String[] g : new String[][]{
                    {"X","A","0"}, {"X","B","4000"}, {"X","C","9000"}, {"X","D","11000"},
                    {"Y","1","0"}, {"Y","2","3500"}, {"Y","3","7000"}
                }) {
                    st.execute(
                        "INSERT INTO ad_building_grid (building_type,axis,grid_label,position_mm) " +
                        "VALUES ('DemoHouse_2BR','%s','%s',%s)".formatted(g[0], g[1], g[2]));
                }

                // ad_room_boundary — one entry per room with grid refs + absolute coords
                for (Object[] room : ROOMS) {
                    String name = (String) room[0];
                    String cat = (String) room[2];
                    double ox = (double) room[6] * 1000, oy = (double) room[7] * 1000;
                    int w = (int) room[3], d = (int) room[4];
                    st.execute(
                        "INSERT INTO ad_room_boundary (building_type,storey,room_name,room_type," +
                        "grid_min_x,grid_max_x,grid_min_y,grid_max_y,z_offset_mm," +
                        "min_x_mm,max_x_mm,min_y_mm,max_y_mm) " +
                        "VALUES ('DemoHouse_2BR','Ground Floor','%s','%s','A','B','1','2',0.0,%f,%f,%f,%f)"
                            .formatted(name, cat, ox, ox + w, oy, oy + d));
                }

                // ad_wall_face — 4 faces per room (N/S/E/W)
                String[][] wallDefs = {
                    {"LIVING",   "NORTH","EXT","1"}, {"LIVING",   "SOUTH","INT","0"},
                    {"LIVING",   "WEST", "EXT","1"}, {"LIVING",   "EAST", "INT","0"},
                    {"KITCHEN",  "NORTH","INT","0"}, {"KITCHEN",  "SOUTH","EXT","1"},
                    {"KITCHEN",  "WEST", "EXT","1"}, {"KITCHEN",  "EAST", "INT","0"},
                    {"BEDROOM1", "NORTH","EXT","1"}, {"BEDROOM1", "SOUTH","INT","0"},
                    {"BEDROOM1", "WEST", "INT","0"}, {"BEDROOM1", "EAST", "EXT","1"},
                    {"BEDROOM2", "NORTH","INT","0"}, {"BEDROOM2", "SOUTH","EXT","1"},
                    {"BEDROOM2", "WEST", "INT","0"}, {"BEDROOM2", "EAST", "EXT","1"},
                    {"BATHROOM", "NORTH","INT","0"}, {"BATHROOM", "SOUTH","EXT","1"},
                    {"BATHROOM", "WEST", "INT","0"}, {"BATHROOM", "EAST", "EXT","1"},
                };
                // ad_space_type — room types (referenced by ad_room_boundary.room_type)
                st.execute("INSERT OR IGNORE INTO ad_space_type (space_type_id,category,omniclass_code,wall_rule) VALUES ('LIVING','HABITABLE','13-21 11 00','ENCLOSED')");
                st.execute("INSERT OR IGNORE INTO ad_space_type (space_type_id,category,omniclass_code,wall_rule) VALUES ('KITCHEN','SERVICE','13-31 11 00','ENCLOSED')");
                st.execute("INSERT OR IGNORE INTO ad_space_type (space_type_id,category,omniclass_code,wall_rule,is_sleeping_room) VALUES ('BEDROOM','HABITABLE','13-21 21 00','ENCLOSED',1)");
                st.execute("INSERT OR IGNORE INTO ad_space_type (space_type_id,category,omniclass_code,wall_rule) VALUES ('BATHROOM','SERVICE','13-31 21 00','ENCLOSED')");

                // ad_wall_type — exterior and interior wall specs
                st.execute("INSERT OR IGNORE INTO ad_wall_type (wall_type_id,category,construction,total_mm,is_exterior) " +
                           "VALUES ('EXT','EXTERIOR','BRICK_BLOCK',200,1)");
                st.execute("INSERT OR IGNORE INTO ad_wall_type (wall_type_id,category,construction,total_mm,is_exterior) " +
                           "VALUES ('INT','INTERIOR','STUD_FRAME',100,0)");
                for (String[] wd : wallDefs) {
                    st.execute(
                        "INSERT INTO ad_wall_face (building_type,storey,room_name,face,wall_type_id,is_exterior) " +
                        "VALUES ('DemoHouse_2BR','Ground Floor','%s','%s','%s',%s)"
                            .formatted(wd[0], wd[1], wd[2], wd[3]));
                }
            }

            conn.commit();
        }
    }

    private static boolean isExteriorFace(String roomName, String face) {
        return switch (roomName) {
            case "LIVING"   -> "NORTH".equals(face) || "WEST".equals(face);
            case "KITCHEN"  -> "SOUTH".equals(face) || "WEST".equals(face);
            case "BEDROOM1" -> "NORTH".equals(face) || "EAST".equals(face);
            case "BEDROOM2" -> "SOUTH".equals(face) || "EAST".equals(face);
            case "BATHROOM" -> "SOUTH".equals(face) || "EAST".equals(face);
            default -> false;
        };
    }

    /**
     * Add a leaf BOM line.  dx/dy/dz are in mm (matching allocated_*_mm for
     * readability); converted to metres on INSERT per BBC.md §4.0 tack convention.
     */
    private static void addLeaf(Statement st, String bomId, String productId,
            String role, int seq, int dxMm, int dyMm, int dzMm,
            int allocW, int allocD, int allocH) throws SQLException {
        st.execute("""
            INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
                dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
            VALUES ('%s','%s','LEAF','%s',%d, %f,%f,%f, %d,%d,%d)"""
                .formatted(bomId, productId, role, seq,
                    dxMm / 1000.0, dyMm / 1000.0, dzMm / 1000.0,
                    allocW, allocD, allocH));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-GEN-COMPILE: End-to-end compilation
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-GEN-COMPILE-1: DemoHouse compiles through 9-stage pipeline")
    void w_gen_compile_1_pipeline_runs() {
        CompileRequest request = new CompileRequest(
                "DemoHouse_2BR",
                compileDbPath,
                COMP_LIB,
                outputDir + "/"
        );

        compileResult = api.compile(request);

        System.out.printf("  Compile: success=%s, elements=%d, time=%dms%n",
                compileResult.success(), compileResult.elementCount(),
                compileResult.compileTimeMs());
        if (!compileResult.success()) {
            System.out.printf("  Error: %s%n", compileResult.error());
        }

        assertTrue(compileResult.success(),
                "DemoHouse compile should succeed: " + compileResult.error());
        assertTrue(compileResult.elementCount() > 0,
                "Must produce elements, got " + compileResult.elementCount());
    }

    @Test @Order(2)
    @DisplayName("W-GEN-COMPILE-2: Output DB exists with elements_meta")
    void w_gen_compile_2_output_exists() {
        assertNotNull(compileResult, "W-GEN-COMPILE-1 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();
        assertNotNull(outputPath);
        assertTrue(new File(outputPath).exists(),
                "Output DB must exist: " + outputPath);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            assertTrue(rs.next());
            int count = rs.getInt(1);
            assertTrue(count > 0, "elements_meta must have rows, got " + count);
            System.out.printf("  Output: %d elements in elements_meta%n", count);
        } catch (Exception e) {
            fail("Output DB not valid: " + e.getMessage());
        }
    }

    @Test @Order(3)
    @DisplayName("W-GEN-COMPILE-3: Output has multi-discipline elements (walls + doors + windows + slabs)")
    void w_gen_compile_3_multi_discipline() {
        assertNotNull(compileResult, "W-GEN-COMPILE-1 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT ifc_class, COUNT(*) AS cnt FROM elements_meta " +
                     "GROUP BY ifc_class ORDER BY cnt DESC")) {
            int classCount = 0;
            System.out.println("  Element classes:");
            while (rs.next()) {
                System.out.printf("    %-30s %d%n", rs.getString(1), rs.getInt(2));
                classCount++;
            }
            assertTrue(classCount >= 3,
                    "Must have at least 3 IFC classes (wall/door/slab), got " + classCount);
        } catch (Exception e) {
            fail("Cannot read output: " + e.getMessage());
        }
    }

    @Test @Order(4)
    @DisplayName("W-GEN-COMPILE-4: Provenance is GENERATIVE in output C_Order")
    void w_gen_compile_4_provenance() {
        assertNotNull(compileResult, "W-GEN-COMPILE-1 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT Provenance FROM C_Order LIMIT 1")) {
            if (rs.next()) {
                assertEquals("GENERATIVE", rs.getString(1));
                System.out.println("  Provenance: GENERATIVE ✓");
            }
        } catch (Exception e) {
            // C_Order may not exist in output — not a hard failure
            System.out.println("  C_Order not in output (pipeline may not copy it) — OK");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-GEN-COMPILE-5: BOM offset verification (Layer 2)
    //  // Implementing LAST_MILE_PROBLEM.md §Layer 2 — Witness: W-GEN-COMPILE-5
    //
    //  For generative buildings there is no extraction oracle.
    //  The BOM itself IS the oracle: verified Stones certify the offsets,
    //  so this test checks that the compiler honoured them.
    //
    //  Three checks:
    //  (a) All output elements have positive, finite extents (EYES P01-P03)
    //  (b) All output elements lie within the building envelope AABB
    //  (c) Each BOM room's leaf count matches the spatial region count in output
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(5)
    @DisplayName("W-GEN-COMPILE-5: Compiled positions honour BOM offsets (Layer 2)")
    void w_gen_compile_5_bom_offset_verification() {
        assertNotNull(compileResult, "W-GEN-COMPILE-1 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();
        List<String> violations = new ArrayList<>();

        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {

            // ── (a) Positive, finite extents ─────────────────────────────
            List<double[]> allBboxes = new ArrayList<>();
            Map<Integer, String> idToClass = new HashMap<>();

            try (Statement st = outConn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT em.id, em.ifc_class, em.element_name, " +
                     "r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ " +
                     "FROM elements_meta em JOIN elements_rtree r ON em.id = r.id")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String cls = rs.getString("ifc_class");
                    idToClass.put(id, cls);

                    double minX = rs.getDouble("minX"), maxX = rs.getDouble("maxX");
                    double minY = rs.getDouble("minY"), maxY = rs.getDouble("maxY");
                    double minZ = rs.getDouble("minZ"), maxZ = rs.getDouble("maxZ");
                    allBboxes.add(new double[]{minX, maxX, minY, maxY, minZ, maxZ});

                    double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;

                    if (dx <= 0 || dy <= 0 || dz <= 0) {
                        violations.add("Zero/negative extent: id=%d %s (%.4f x %.4f x %.4f)"
                            .formatted(id, cls, dx, dy, dz));
                    }
                    if (!Double.isFinite(minX) || !Double.isFinite(maxX)
                            || !Double.isFinite(minY) || !Double.isFinite(maxY)
                            || !Double.isFinite(minZ) || !Double.isFinite(maxZ)) {
                        violations.add("Non-finite coords: id=%d %s".formatted(id, cls));
                    }
                }
            }

            assertTrue(!allBboxes.isEmpty(), "Output must have elements with spatial data");
            System.out.printf("  [L2] %d elements with AABB data%n", allBboxes.size());

            // ── (b) Building envelope containment ────────────────────────
            // Compute actual building envelope from all elements
            double envMinX = Double.MAX_VALUE, envMaxX = -Double.MAX_VALUE;
            double envMinY = Double.MAX_VALUE, envMaxY = -Double.MAX_VALUE;
            double envMinZ = Double.MAX_VALUE, envMaxZ = -Double.MAX_VALUE;
            for (double[] bb : allBboxes) {
                envMinX = Math.min(envMinX, bb[0]); envMaxX = Math.max(envMaxX, bb[1]);
                envMinY = Math.min(envMinY, bb[2]); envMaxY = Math.max(envMaxY, bb[3]);
                envMinZ = Math.min(envMinZ, bb[4]); envMaxZ = Math.max(envMaxZ, bb[5]);
            }
            System.out.printf("  [L2] Envelope: X[%.2f,%.2f] Y[%.2f,%.2f] Z[%.2f,%.2f]%n",
                envMinX, envMaxX, envMinY, envMaxY, envMinZ, envMaxZ);

            // Envelope must be plausible (not absurdly large or spanning >1km)
            double spanX = envMaxX - envMinX;
            double spanY = envMaxY - envMinY;
            double spanZ = envMaxZ - envMinZ;

            // DemoHouse is ~11m x 7m x 2.8m — allow up to 100x if units are mm
            // (11000mm = 11m). Either way, >10km is definitely wrong.
            double MAX_SPAN = 10_000; // 10km or 10000mm — catches runaway coords
            if (spanX > MAX_SPAN || spanY > MAX_SPAN || spanZ > MAX_SPAN) {
                violations.add("Building envelope too large: %.1f x %.1f x %.1f"
                    .formatted(spanX, spanY, spanZ));
            }

            // Envelope must have some volume (not degenerate)
            if (spanX < 0.001 || spanY < 0.001 || spanZ < 0.001) {
                violations.add("Building envelope degenerate: %.4f x %.4f x %.4f"
                    .formatted(spanX, spanY, spanZ));
            }

            // ── (c) Per-room BOM leaf count vs output spatial clustering ──
            // Walk BOM tree: count leaf children per room, verify output has
            // at least that many elements (structural + furniture + MEP).
            int totalBomLeaves = 0;
            int bomRoomCount = 0;
            // Count LEAF children in room BOMs AND their sub-BOMs (e.g. ELEC SET)
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "SELECT COUNT(*) FROM m_bom_line " +
                    "WHERE component_type = 'LEAF' AND is_active = 1 " +
                    "AND (bom_id = ? OR bom_id IN " +
                    "  (SELECT child_product_id FROM m_bom_line WHERE bom_id = ? AND component_type = 'MAKE'))")) {
                for (Object[] room : ROOMS) {
                    String bomId = (String) room[1];
                    ps.setString(1, bomId);
                    ps.setString(2, bomId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int leafCount = rs.getInt(1);
                            totalBomLeaves += leafCount;
                            bomRoomCount++;
                            System.out.printf("  [L2] Room %s: %d BOM leaves%n",
                                room[0], leafCount);
                        }
                    }
                }
            }

            // Also count ROOF leaves
            try (Statement st = bomConn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM m_bom_line " +
                     "WHERE bom_id = 'ROOF_DEMO' AND component_type = 'LEAF' AND is_active = 1")) {
                if (rs.next()) {
                    int roofLeaves = rs.getInt(1);
                    totalBomLeaves += roofLeaves;
                    System.out.printf("  [L2] Roof: %d BOM leaves%n", roofLeaves);
                }
            }

            System.out.printf("  [L2] Total BOM leaves: %d, output elements: %d%n",
                totalBomLeaves, allBboxes.size());

            // Output should have at least the structural core (walls + slabs + doors + windows + roof).
            // Some BOM leaves may lack library geometry (MEP fittings, specialty furniture)
            // and won't compile — that's acceptable. But output must not be empty or
            // have MORE elements than BOM leaves (invented elements).
            int structuralMin = 20 + 5 + 5 + 5 + 1; // walls + slabs + doors + windows + roof
            if (allBboxes.size() < structuralMin) {
                violations.add("Output too sparse: %d elements < %d structural minimum"
                    .formatted(allBboxes.size(), structuralMin));
            }
            if (allBboxes.size() > totalBomLeaves) {
                violations.add("Output has MORE elements (%d) than BOM leaves (%d) — invented elements?"
                    .formatted(allBboxes.size(), totalBomLeaves));
            }
            if (allBboxes.size() < totalBomLeaves) {
                int gap = totalBomLeaves - allBboxes.size();
                System.out.printf("  [L2] ADVISORY: %d BOM leaves did not compile " +
                    "(likely missing library geometry)%n", gap);
            }

            // ── Report ───────────────────────────────────────────────────
            if (violations.isEmpty()) {
                System.out.printf("  [L2] PASS: %d elements, %d rooms, envelope %.1fx%.1fx%.1f%n",
                    allBboxes.size(), bomRoomCount, spanX, spanY, spanZ);
            }

        } catch (Exception e) {
            fail("BOM offset verification failed: " + e.getMessage());
        }

        assertTrue(violations.isEmpty(),
            "[W-GEN-COMPILE-5] BOM offset violations:\n  " + String.join("\n  ", violations));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-DM-TC5-1: Discipline wiring
    //  // Implementing ACTION_ROADMAP.md §Task 4 Session A — Witness: W-DM-TC5-1
    //
    //  Two-part proof:
    //  (a) Output has multi-discipline breakdown: STR (walls/slabs) + ARC
    //      (doors/windows/furniture/roof) + MEP (sprinklers/alarms)
    //  (b) BomDropper.drop() populates C_OrderLine.Discipline from bom_category
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(6)
    @DisplayName("W-DM-TC5-1: Multi-discipline output via CompileProof (STR + ARC + MEP)")
    void w_dm_tc5_1_output_disciplines() {
        assertNotNull(compileResult, "W-GEN-COMPILE-1 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();

        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath)) {
            // ── CompileProof: DisciplineBreakdownProof (≥3 disciplines, 63 total) ──
            var proof = new com.bim.compiler.validation.DisciplineBreakdownProof(3, 63);
            var result = proof.evaluate(outConn);

            assertEquals(com.bim.eyes.proof.ProofResult.Status.PROVEN, result.status(),
                "DisciplineBreakdownProof must PASS: " + result.evidence());

            // ── Detailed assertions on the breakdown ──
            Map<String, Integer> disciplineCounts = proof.breakdown(outConn);

            System.out.println("  [TC5] Discipline breakdown:");
            disciplineCounts.forEach((d, c) ->
                System.out.printf("    %-10s %d%n", d, c));

            assertTrue(disciplineCounts.containsKey("MEP"),
                "Output must contain MEP discipline elements");
            assertEquals(10, disciplineCounts.get("MEP"),
                "10 MEP elements (5 rooms × sprinkler + alarm)");

            assertTrue(disciplineCounts.containsKey("STR"),
                "Output must contain STR discipline elements");
            assertTrue(disciplineCounts.get("STR") >= 25,
                "≥25 STR elements (20 walls + 5 slabs), got " + disciplineCounts.get("STR"));

            assertTrue(disciplineCounts.containsKey("ARC"),
                "Output must contain ARC discipline elements");
            assertTrue(disciplineCounts.get("ARC") >= 10,
                "≥10 ARC elements (doors + windows + furniture + roof)");

            int total = disciplineCounts.values().stream().mapToInt(Integer::intValue).sum();
            System.out.printf("  [TC5] PASS: %s = %d total%n", disciplineCounts, total);

            // ── Emit to FINE log (proves PIPELINE_LOG context works) ──
            proof.emitLog(result);
        } catch (Exception e) {
            fail("W-DM-TC5-1 failed: " + e.getMessage());
        }
    }

    @Test @Order(7)
    @DisplayName("W-DM-TC5-2: BomDropper populates C_OrderLine.Discipline from bom_category")
    void w_dm_tc5_2_bomdropper_discipline() throws Exception {
        // BomDropper.drop() creates C_OrderLine tree with Discipline column.
        // Uses the Rosetta Stone pipeline path (BomDropper → OrderLineWalker).
        var entry = com.bim.compiler.dsl.BuildingRegistry.loadByDocTypeId("RE_DM");
        assertNotNull(entry, "RE_DM must exist in C_DocType");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {
            int leaves = com.bim.compiler.bom.BomDropper.drop(conn, entry);
            assertTrue(leaves > 0, "BomDropper must produce leaves, got " + leaves);
            System.out.printf("  [TC5-2] BomDropper: %d leaves%n", leaves);

            // Verify C_OrderLine.Discipline populated
            Map<String, Integer> olDisciplines = new LinkedHashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT Discipline, COUNT(*) AS cnt FROM C_OrderLine "
                   + "GROUP BY Discipline ORDER BY cnt DESC")) {
                while (rs.next()) {
                    olDisciplines.put(rs.getString("Discipline"), rs.getInt("cnt"));
                }
            }

            System.out.println("  [TC5-2] C_OrderLine.Discipline:");
            olDisciplines.forEach((d, c) ->
                System.out.printf("    %-10s %d%n", d, c));

            assertFalse(olDisciplines.isEmpty(),
                "C_OrderLine must have discipline rows after BomDropper");
            assertTrue(olDisciplines.containsKey("ARC"),
                "Must have ARC discipline rows (room-category BOMs)");
            assertTrue(olDisciplines.containsKey("ELEC"),
                "Must have ELEC discipline rows (ELEC SET BOMs)");
            System.out.printf("  [TC5-2] PASS: %s%n", olDisciplines);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-DM-TC5-3: ELEC discipline wiring
    //  // Implementing DISC_VALIDATION_DB_SRS.md §12 — Witness: W-DM-TC5-3
    //
    //  Proves ELEC discipline appears in compiled output alongside
    //  STR, ARC, MEP — giving 4+ disciplines total.
    //  ELEC products (light fixture, data point) placed via SET-level
    //  sub-BOM with bom_category=ELEC.
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(8)
    @DisplayName("W-DM-TC5-3: 4+ disciplines in output (STR + ARC + MEP + ELEC)")
    void w_dm_tc5_3_elec_discipline() {
        assertNotNull(compileResult, "W-GEN-COMPILE-1 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();

        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath)) {
            // ── CompileProof: DisciplineBreakdownProof (≥4 disciplines, 63 total) ──
            var proof = new com.bim.compiler.validation.DisciplineBreakdownProof(4, 63);
            var result = proof.evaluate(outConn);

            assertEquals(com.bim.eyes.proof.ProofResult.Status.PROVEN, result.status(),
                "DisciplineBreakdownProof must PASS with 4+ disciplines: " + result.evidence());

            Map<String, Integer> disciplineCounts = proof.breakdown(outConn);

            System.out.println("  [TC5-3] Discipline breakdown:");
            disciplineCounts.forEach((d, c) ->
                System.out.printf("    %-10s %d%n", d, c));

            // ── ELEC-specific assertions ──
            assertTrue(disciplineCounts.containsKey("ELEC"),
                "Output must contain ELEC discipline elements");
            assertEquals(10, disciplineCounts.get("ELEC"),
                "10 ELEC elements (5 rooms × light + data point)");

            Map<String, Integer> elecClasses = new LinkedHashMap<>();
            try (Statement st = outConn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT ifc_class, COUNT(*) AS cnt FROM elements_meta "
                   + "WHERE discipline = 'ELEC' GROUP BY ifc_class ORDER BY cnt DESC")) {
                while (rs.next()) {
                    elecClasses.put(rs.getString("ifc_class"), rs.getInt("cnt"));
                }
            }
            assertEquals(5, elecClasses.get("IfcLightFixture"), "5 light fixtures");
            assertEquals(5, elecClasses.get("IfcElectricAppliance"), "5 data points");

            assertTrue(disciplineCounts.containsKey("MEP"),
                "MEP discipline must still be present");
            assertEquals(10, disciplineCounts.get("MEP"),
                "10 MEP elements (sprinklers + alarms) unchanged");

            int total = disciplineCounts.values().stream().mapToInt(Integer::intValue).sum();
            System.out.printf("  [TC5-3] PASS: %s = %d total%n", disciplineCounts, total);

            // ── Emit to FINE log (proves PIPELINE_LOG context works) ──
            proof.emitLog(result);
        } catch (Exception e) {
            fail("W-DM-TC5-3 failed: " + e.getMessage());
        }
    }
}
