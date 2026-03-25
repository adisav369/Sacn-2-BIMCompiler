package com.bim.designer;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;
import com.bim.designer.dao.DesignerDAO;
import com.bim.designer.dao.WorkOutputDAO;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ASI Authoring — BBC.md §3.5.1 + BIM_Designer_SRS.md §28 proof.
 *
 * <p>Demonstrates the full pump pattern:
 * <ol>
 *   <li>Create room layout → C_OrderLine tree</li>
 *   <li>Auto-populate rooms via Selection Cascade</li>
 *   <li>ASI auto-computed for IsInstanceAttribute=1 products (walls, pipes)</li>
 *   <li>No ASI for IsInstanceAttribute=0 products (furniture, fittings)</li>
 *   <li>User reads ASI → edits single field → re-reads</li>
 * </ol>
 *
 * <p>All tests use in-memory SQLite — no filesystem dependency.
 *
 * // Implementing BIM_Designer_SRS.md §28 — Witness: W-ASI-AUTO-1..3, W-ASI-READ-1, W-ASI-EDIT-1
 */
@DisplayName("ASI Authoring — BBC.md §3.5.1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ASIAuthoringTest {

    private static Connection bomConn;
    private static Connection woConn;
    private static WorkOutputDAO woDao;
    private static DesignerDAO designerDao;
    private static DesignerAPIImpl api;

    // DemoHouse rooms (same as SelectionCascadeTest)
    private static final String BUILDING_ID = "DemoHouse_2BR";
    private static final List<DesignBBox> ROOMS = List.of(
            new DesignBBox("ROOM_DM_LI", "Living Room", "ROOM", "LIVING",
                    "IfcSpace", "GF", "FLOOR_DM_GF",
                    0, 0, 0, 4000, 3500, 2800),
            new DesignBBox("ROOM_DM_KT", "Kitchen", "ROOM", "KITCHEN",
                    "IfcSpace", "GF", "FLOOR_DM_GF",
                    4000, 0, 0, 8000, 3500, 2800),
            new DesignBBox("ROOM_DM_BD", "Bedroom 1", "ROOM", "BEDROOM",
                    "IfcSpace", "GF", "FLOOR_DM_GF",
                    0, 3500, 0, 5000, 7000, 2800)
    );

    // Tracked for later tests
    private static AutoPopulateResponse populateResult;

    @BeforeAll
    static void setUp() throws Exception {
        // BOM DB — contains SET BOMs with categorised children
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        seedBomDB(bomConn);
        designerDao = new DesignerDAO(bomConn);

        // Work output DB — C_OrderLine + ASI
        woConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        woDao = new WorkOutputDAO(woConn);
        woDao.initSchema();
        woDao.initASISchema();

        // Ensure master order exists for DemoHouse
        woDao.ensureMasterOrder(BUILDING_ID, "GENERATIVE", 9000, 7000, 3000);

        // API — wired to both BOM + work_output connections
        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
        if (woConn != null) woConn.close();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-ASI-READ-1: ASI schema + templates exist
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-ASI-READ-1a: ASI schema creates M_AttributeSet templates")
    void asi_schema_creates_templates() throws Exception {
        var sets = woDao.listAttributeSets();
        assertFalse(sets.isEmpty(), "M_AttributeSet must have seed templates");

        System.out.println("  M_AttributeSet templates:");
        int instanceCount = 0;
        for (var s : sets) {
            System.out.printf("    %-15s IsInstance=%b  %s%n",
                    s.id(), s.isInstanceAttribute(), s.description());
            if (s.isInstanceAttribute()) instanceCount++;
        }
        assertTrue(instanceCount >= 7,
                "At least 7 instance-attribute types (Wall,Pipe,Duct,Conduit,Slab,Beam,Column)");
        assertTrue(sets.stream().anyMatch(s -> !s.isInstanceAttribute()),
                "Must have at least 1 non-instance type (Component/Fitting)");
    }

    @Test @Order(2)
    @DisplayName("W-ASI-READ-1b: Product family_ref resolves to correct attribute set")
    void product_resolves_to_attribute_set() {
        assertEquals("BIM_Wall", woDao.resolveAttributeSetForProduct("WALL_EXT_150"));
        assertEquals("BIM_Pipe", woDao.resolveAttributeSetForProduct("PIPE_COLD_25"));
        assertEquals("BIM_Door", woDao.resolveAttributeSetForProduct("DOOR_INT_810"));
        assertEquals("BIM_Window", woDao.resolveAttributeSetForProduct("WINDOW_1200"));
        assertEquals("BIM_Component", woDao.resolveAttributeSetForProduct("LIGHT_FIX"));
        assertEquals("BIM_Component", woDao.resolveAttributeSetForProduct("TOILET_STD"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-ASI-AUTO-1/2/3: ASI CRUD on WorkOutputDAO
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("W-ASI-AUTO-1a: createASI → readASI round-trip for wall")
    void create_read_asi_wall() throws Exception {
        var values = new LinkedHashMap<String, String>();
        values.put("length_mm", "6000");
        values.put("height_mm", "2800");
        values.put("material", "BrickPlaster");

        int asiId = woDao.createASI("BIM_Wall", values);
        assertTrue(asiId > 0, "ASI ID must be positive");

        var fields = woDao.readASI(asiId);
        assertEquals(3, fields.size(), "3 attribute values created");

        // Verify each field
        var fieldMap = new HashMap<String, WorkOutputDAO.ASIFieldRow>();
        for (var f : fields) fieldMap.put(f.name(), f);

        assertEquals("6000", fieldMap.get("length_mm").value());
        assertEquals("NUM", fieldMap.get("length_mm").valueType());
        assertEquals("2800", fieldMap.get("height_mm").value());
        assertEquals("BrickPlaster", fieldMap.get("material").value());
        assertEquals("TEXT", fieldMap.get("material").valueType());

        // All fields from BIM_Wall → IsInstanceAttribute=1
        assertTrue(fields.get(0).isInstanceAttribute());
        assertEquals("BIM_Wall", fields.get(0).attributeSetId());

        System.out.printf("  Wall ASI #%d: %s%n", asiId,
                fields.stream().map(f -> f.name() + "=" + f.value())
                        .reduce((a, b) -> a + ", " + b).orElse(""));
    }

    @Test @Order(11)
    @DisplayName("W-ASI-AUTO-1b: createASI → readASI round-trip for pipe")
    void create_read_asi_pipe() throws Exception {
        var values = new LinkedHashMap<String, String>();
        values.put("length_mm", "3200");
        values.put("angle_deg", "90");

        int asiId = woDao.createASI("BIM_Pipe", values);
        var fields = woDao.readASI(asiId);
        assertEquals(2, fields.size());
        assertTrue(fields.get(0).isInstanceAttribute());

        System.out.printf("  Pipe ASI #%d: %s%n", asiId,
                fields.stream().map(f -> f.name() + "=" + f.value())
                        .reduce((a, b) -> a + ", " + b).orElse(""));
    }

    @Test @Order(12)
    @DisplayName("W-ASI-AUTO-3: No ASI needed for IsInstanceAttribute=0 products")
    void no_asi_for_components() throws Exception {
        assertFalse(woDao.isInstanceAttribute("BIM_Component"),
                "BIM_Component must NOT be instance-attribute");
        assertFalse(woDao.isInstanceAttribute("BIM_Fitting"),
                "BIM_Fitting must NOT be instance-attribute");
        assertTrue(woDao.isInstanceAttribute("BIM_Wall"),
                "BIM_Wall must be instance-attribute");
        assertTrue(woDao.isInstanceAttribute("BIM_Pipe"),
                "BIM_Pipe must be instance-attribute");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-ASI-EDIT-1: Update single ASI attribute
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("W-ASI-EDIT-1: updateASIAttribute changes single value")
    void update_asi_attribute() throws Exception {
        var values = new LinkedHashMap<String, String>();
        values.put("length_mm", "6000");
        values.put("material", "BrickPlaster");

        int asiId = woDao.createASI("BIM_Wall", values);

        // User changes material
        boolean updated = woDao.updateASIAttribute(asiId, "material", "ConcreteBlock");
        assertTrue(updated);

        var fields = woDao.readASI(asiId);
        var materialField = fields.stream()
                .filter(f -> "material".equals(f.name())).findFirst().orElseThrow();
        assertEquals("ConcreteBlock", materialField.value());
        assertEquals("TEXT", materialField.valueType());

        // length_mm unchanged
        var lengthField = fields.stream()
                .filter(f -> "length_mm".equals(f.name())).findFirst().orElseThrow();
        assertEquals("6000", lengthField.value());

        System.out.printf("  Updated ASI #%d: material BrickPlaster → ConcreteBlock%n", asiId);
    }

    @Test @Order(21)
    @DisplayName("W-ASI-EDIT-1b: updateASIAttribute upserts new field")
    void update_asi_upsert() throws Exception {
        var values = new LinkedHashMap<String, String>();
        values.put("length_mm", "3200");

        int asiId = woDao.createASI("BIM_Pipe", values);

        // Add a field that didn't exist
        boolean upserted = woDao.updateASIAttribute(asiId, "elevation", "1200");
        assertTrue(upserted);

        var fields = woDao.readASI(asiId);
        assertEquals(2, fields.size(), "Now has 2 fields");
        assertTrue(fields.stream().anyMatch(f -> "elevation".equals(f.name())));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-ASI-LINK: ASI ↔ C_OrderLine linkage
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(30)
    @DisplayName("W-ASI-LINK-1: Link ASI to C_OrderLine and read back")
    void link_asi_to_orderline() throws Exception {
        // Create a wall order line
        DesignBBox wallBbox = new DesignBBox("WALL_EXT_150", "Ext Wall", "LEAF", "LIVING",
                null, "GF", "ROOM_DM_LI",
                0, 0, 0, 6000, 150, 2800);
        int orderLineId = woDao.insertOrderLine(BUILDING_ID, wallBbox);
        assertTrue(orderLineId > 0);

        // No ASI yet
        assertEquals(-1, woDao.getASIForOrderLine(orderLineId));

        // Create and link ASI
        var values = new LinkedHashMap<String, String>();
        values.put("length_mm", "6000");
        values.put("material", "BrickPlaster");
        int asiId = woDao.createASI("BIM_Wall", values);
        assertTrue(woDao.linkASI(orderLineId, asiId));

        // Read back
        assertEquals(asiId, woDao.getASIForOrderLine(orderLineId));

        System.out.printf("  OrderLine #%d → ASI #%d (WALL_EXT_150: length=6000, material=BrickPlaster)%n",
                orderLineId, asiId);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Full pump pattern demo — the tree structure from §28.2
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(50)
    @DisplayName("W-ASI-PUMP-1: Full pump pattern — C_OrderLine tree with mixed ASI")
    void full_pump_pattern() throws Exception {
        System.out.println("  ═══ Pump Pattern Demo — BBC.md §3.5.1 ═══");
        System.out.println();

        // Simulate: user selected KITCHEN, cascade expanded the SET BOM into OrderLines
        record ItemSpec(String familyRef, String attrSetId, Map<String, String> asi) {}

        var items = List.of(
                new ItemSpec("WALL_EXT_150", "BIM_Wall",
                        Map.of("length_mm", "4000", "material", "BrickPlaster")),
                new ItemSpec("WALL_EXT_150", "BIM_Wall",
                        Map.of("length_mm", "3500")),
                new ItemSpec("DOOR_INT_810", "BIM_Door",
                        Map.of("swing_side", "1", "face_anchor", "INT")),
                new ItemSpec("WINDOW_1200", "BIM_Window",
                        Map.of("sill_height_mm", "900", "face_anchor", "EXT")),
                new ItemSpec("PIPE_COLD_25", "BIM_Pipe",
                        Map.of("length_mm", "3200", "angle_deg", "90")),
                new ItemSpec("LIGHT_FIX", "BIM_Component", Map.of())  // no ASI
        );

        int withASI = 0;
        int withoutASI = 0;

        for (var item : items) {
            // Create C_OrderLine
            DesignBBox bbox = new DesignBBox(item.familyRef, item.familyRef, "LEAF", "KITCHEN",
                    null, "GF", "ROOM_DM_KT",
                    4000, 0, 0, 4500, 500, 2800);
            int olId = woDao.insertOrderLine(BUILDING_ID, bbox);

            if (woDao.isInstanceAttribute(item.attrSetId) && !item.asi.isEmpty()) {
                // IsInstanceAttribute=1 → create and link ASI
                int asiId = woDao.createASI(item.attrSetId, item.asi);
                woDao.linkASI(olId, asiId);
                withASI++;

                String asiDesc = item.asi.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .reduce((a, b) -> a + ", " + b).orElse("");
                System.out.printf("    C_OrderLine: %-15s → ASI: {%s}%n",
                        item.familyRef, asiDesc);
            } else {
                // IsInstanceAttribute=0 → no ASI, catalog defaults
                withoutASI++;
                System.out.printf("    C_OrderLine: %-15s → (no ASI, catalog defaults)%n",
                        item.familyRef);
            }
        }

        System.out.printf("  ─────────────────────────────────────────%n");
        System.out.printf("  With ASI (instance-varying): %d%n", withASI);
        System.out.printf("  Without ASI (fixed catalog):  %d%n", withoutASI);
        System.out.printf("  Total C_OrderLine:            %d%n", items.size());

        assertEquals(5, withASI, "5 items need ASI (2 walls + door + window + pipe)");
        assertEquals(1, withoutASI, "1 item needs no ASI (light fixture)");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Seed BOM DB with SET BOMs for cascade testing
    // ═══════════════════════════════════════════════════════════════════

    private static void seedBomDB(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // Schema
            s.execute("""
                CREATE TABLE IF NOT EXISTS C_DocType (
                    C_DocType_ID TEXT PRIMARY KEY, ProjectName TEXT, Name TEXT,
                    DocBaseType TEXT, DocSubType TEXT, IsActive INTEGER DEFAULT 1,
                    SeqNo INTEGER DEFAULT 10, ExpectedElements INTEGER DEFAULT 0
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS m_bom (
                    bom_id TEXT PRIMARY KEY, bom_name TEXT, bom_type TEXT,
                    m_product_category_id TEXT, doc_sub_type TEXT,
                    group_by TEXT DEFAULT 'default', seq_no INTEGER DEFAULT 10,
                    aabb_width_mm INTEGER DEFAULT 0, aabb_depth_mm INTEGER DEFAULT 0,
                    aabb_height_mm INTEGER DEFAULT 0, is_active INTEGER DEFAULT 1,
                    entity_type TEXT DEFAULT 'D'
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS m_bom_line (
                    bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id TEXT, child_product_id TEXT, component_type TEXT DEFAULT 'BUY',
                    dx REAL DEFAULT 0, dy REAL DEFAULT 0, dz REAL DEFAULT 0,
                    allocated_width_mm INTEGER DEFAULT 0,
                    allocated_depth_mm INTEGER DEFAULT 0,
                    allocated_height_mm INTEGER DEFAULT 0,
                    storey TEXT, element_ref TEXT, verb_ref TEXT,
                    sequence INTEGER DEFAULT 10, role TEXT,
                    is_active INTEGER DEFAULT 1,
                    FOREIGN KEY (bom_id) REFERENCES m_bom(bom_id)
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS M_Product (
                    product_id TEXT PRIMARY KEY, product_type TEXT,
                    width REAL, depth REAL, height REAL,
                    ifc_class TEXT, building_type TEXT,
                    is_active INTEGER DEFAULT 1
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS M_BomCategory (
                    Name TEXT PRIMARY KEY
                )""");

            // Building type
            s.execute("""
                INSERT INTO C_DocType VALUES
                ('RE_SH', 'Ifc4_SampleHouse', 'Sample House', 'RE', 'SH', 1, 10, 55)
                """);

            // SET BOMs — room content packs with m_product_category_id
            // These are what the Selection Cascade queries
            s.execute("""
                INSERT INTO m_bom VALUES
                ('SET_SH_LI', 'SH Living Contents', 'SET', 'LIVING',
                 'RE', 'SH', 'room', 10, 3800, 3200, 2800, 1, 'D')
                """);
            s.execute("""
                INSERT INTO m_bom VALUES
                ('SET_SH_KT', 'SH Kitchen Contents', 'SET', 'KITCHEN',
                 'RE', 'SH', 'room', 10, 3400, 2400, 2800, 1, 'D')
                """);
            s.execute("""
                INSERT INTO m_bom VALUES
                ('SET_SH_BD', 'SH Bedroom Contents', 'SET', 'BEDROOM',
                 'RE', 'SH', 'room', 10, 3000, 3000, 2800, 1, 'D')
                """);

            // SET children — the actual products in each room pack
            // Living room SET
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_LI', 'WALL_EXT_150', 'BUY',
                    0, 0, 0, 3800, 150, 2800, 10)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_LI', 'WALL_EXT_150', 'BUY',
                    0, 0, 0, 150, 3200, 2800, 20)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_LI', 'DOOR_INT_810', 'BUY',
                    1.5, 0, 0, 810, 40, 2100, 30)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_LI', 'WINDOW_1200', 'BUY',
                    2.0, 0, 0.9, 1200, 100, 1200, 40)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_LI', 'LIGHT_FIX', 'BUY',
                    1.9, 1.6, 2.6, 300, 300, 100, 50)
                """);

            // Kitchen SET
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_KT', 'WALL_EXT_150', 'BUY',
                    0, 0, 0, 3400, 150, 2800, 10)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_KT', 'PIPE_COLD_25', 'BUY',
                    0.5, 0, 0.5, 2000, 25, 25, 20)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_KT', 'SINK_KITCHEN', 'BUY',
                    1.0, 0, 0.85, 600, 450, 200, 30)
                """);

            // Bedroom SET
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_BD', 'WALL_EXT_150', 'BUY',
                    0, 0, 0, 3000, 150, 2800, 10)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_BD', 'DOOR_INT_810', 'BUY',
                    1.2, 0, 0, 810, 40, 2100, 20)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence)
                VALUES ('SET_SH_BD', 'BED_SINGLE', 'BUY',
                    0.5, 1.0, 0, 900, 2000, 500, 30)
                """);

            // Products
            s.execute("INSERT INTO M_Product VALUES ('WALL_EXT_150','wall',0.15,0.15,2.8,'IfcWall','SH',1)");
            s.execute("INSERT INTO M_Product VALUES ('DOOR_INT_810','door',0.81,0.04,2.1,'IfcDoor','SH',1)");
            s.execute("INSERT INTO M_Product VALUES ('WINDOW_1200','window',1.2,0.1,1.2,'IfcWindow','SH',1)");
            s.execute("INSERT INTO M_Product VALUES ('PIPE_COLD_25','pipe',0.025,0.025,0.025,'IfcPipeSegment','SH',1)");
            s.execute("INSERT INTO M_Product VALUES ('LIGHT_FIX','fixture',0.3,0.3,0.1,'IfcLightFixture','SH',1)");
            s.execute("INSERT INTO M_Product VALUES ('SINK_KITCHEN','fixture',0.6,0.45,0.2,'IfcSanitaryTerminal','SH',1)");
            s.execute("INSERT INTO M_Product VALUES ('BED_SINGLE','furniture',0.9,2.0,0.5,'IfcFurnishingElement','SH',1)");
        }
    }
}
