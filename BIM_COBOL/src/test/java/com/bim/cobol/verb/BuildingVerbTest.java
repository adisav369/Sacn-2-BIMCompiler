package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MBomCategory;
import com.bim.ormsandbox.po.MBomCategoryLine;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for §18.8/§18.9 — Building-Level (L3) + Catalog-Level (L4) Verbs.
 *
 * <p>W-SY-57..72: COMPOSE BUILDING, ADD FLOOR, STACK FLOORS,
 * DEFINE CATEGORY, ADD TEMPLATE RULE, REGISTER BOM.
 *
 * <p>All tests use a temp copy of BOM.db so production data is never mutated.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BuildingVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext bomCtx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File(System.getProperty("bom.db"));
        assertTrue(source.exists(), System.getProperty("bom.db") + " must exist (set -Dbom.db=library/_XX_compile.db)");
        tempDb = File.createTempFile("building_verb_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        bomCtx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
        if (tempDb != null) tempDb.delete();
    }

    // ── W-SY-57..59: DEFINE CATEGORY (L4) ───────────────────────────────

    /**
     * W-SY-57: DEFINE CATEGORY creates a new M_BomCategory row.
     */
    @Test
    @Order(1)
    void w_sy_57_defineCategory() throws SQLException {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "DEFINE CATEGORY CL CLASSROOM doc_type CO");

        assertTrue(result.pass(), "DEFINE CATEGORY should pass: " + result.summary());
        assertEquals("DEFINE CATEGORY", result.verb());

        DefineCategoryVerb.DefineCategoryPayload p =
            (DefineCategoryVerb.DefineCategoryPayload) result.payload();
        assertEquals("CL", p.categoryId());
        assertEquals("CLASSROOM", p.name());
        assertEquals("CO", p.docType());

        // Verify in DB
        MBomCategory cat = MBomCategory.get(conn, "CL");
        assertNotNull(cat, "CL category should exist");
        assertEquals("CLASSROOM", cat.getName());
    }

    /**
     * W-SY-58: DEFINE CATEGORY with duplicate fails.
     */
    @Test
    @Order(2)
    void w_sy_58_defineCategoryDuplicate() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "DEFINE CATEGORY CL CLASSROOM2");

        assertFalse(result.pass(), "duplicate category should fail");
        assertTrue(result.summary().contains("already exists"));
    }

    /**
     * W-SY-59: DEFINE CATEGORY without doc_type succeeds.
     */
    @Test
    @Order(3)
    void w_sy_59_defineCategoryNoDocType() throws SQLException {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "DEFINE CATEGORY GA GARAGE");

        assertTrue(result.pass(), "DEFINE CATEGORY without doc_type: " + result.summary());

        MBomCategory cat = MBomCategory.get(conn, "GA");
        assertNotNull(cat, "GA category should exist");
        assertNull(cat.getDocType(), "doc_type should be null");
    }

    // ── W-SY-60..62: ADD TEMPLATE RULE (L4) ─────────────────────────────

    /**
     * W-SY-60: ADD TEMPLATE RULE creates M_BomCategoryLine linking parent→child.
     */
    @Test
    @Order(4)
    void w_sy_60_addTemplateRule() throws SQLException {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD TEMPLATE RULE GF CONTAINS CL MIN 2 MAX 6");

        assertTrue(result.pass(), "ADD TEMPLATE RULE should pass: " + result.summary());
        assertEquals("ADD TEMPLATE RULE", result.verb());

        AddTemplateRuleVerb.AddRulePayload p =
            (AddTemplateRuleVerb.AddRulePayload) result.payload();
        assertEquals("GF", p.parent());
        assertEquals("CL", p.child());
        assertEquals(2, p.minQty());
        assertEquals(6, p.maxQty());

        // Verify in DB
        List<MBomCategoryLine> children = MBomCategoryLine.getByCategory(conn, "GF");
        boolean found = children.stream()
            .anyMatch(l -> "CL".equals(l.getChildCategoryId()));
        assertTrue(found, "GF should now contain CL rule");
    }

    /**
     * W-SY-61: ADD TEMPLATE RULE with unknown parent fails.
     */
    @Test
    @Order(5)
    void w_sy_61_addTemplateRuleUnknownParent() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD TEMPLATE RULE ZZ CONTAINS CL MIN 1");

        assertFalse(result.pass(), "unknown parent should fail");
        assertTrue(result.summary().contains("not found"));
    }

    /**
     * W-SY-62: ADD TEMPLATE RULE with Z_EXTENT and MIRRORING.
     */
    @Test
    @Order(6)
    void w_sy_62_addTemplateRuleWithOptions() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD TEMPLATE RULE GF CONTAINS GA MIN 1 MAX 2 Z_EXTENT 0.3 MIRRORING PARTY_WALL_PI");

        assertTrue(result.pass(), "ADD TEMPLATE RULE with options: " + result.summary());

        AddTemplateRuleVerb.AddRulePayload p =
            (AddTemplateRuleVerb.AddRulePayload) result.payload();
        assertEquals(0.3, p.zExtent(), 0.001);
        assertEquals("PARTY_WALL_PI", p.mirroringRule());
    }

    // ── W-SY-63..65: REGISTER BOM (L4) ──────────────────────────────────

    /**
     * W-SY-63: REGISTER BOM updates m_product_category_id.
     */
    @Test
    @Order(7)
    void w_sy_63_registerBom() throws SQLException {
        // Create a test BOM first
        registry.dispatch(bomCtx,
            "CREATE BOM SY_REGISTER_TEST TYPE SET CATEGORY FR DOC_SUB_TYPE SY");

        VerbResult<?> result = registry.dispatch(bomCtx,
            "REGISTER BOM SY_REGISTER_TEST AS BT");

        assertTrue(result.pass(), "REGISTER BOM should pass: " + result.summary());
        assertEquals("REGISTER BOM", result.verb());

        RegisterBomVerb.RegisterPayload p =
            (RegisterBomVerb.RegisterPayload) result.payload();
        assertEquals("SY_REGISTER_TEST", p.bomId());
        assertEquals("BT", p.categoryId());

        // Verify in DB
        MBOM bom = MBOM.get(conn, "SY_REGISTER_TEST");
        assertEquals("BT", bom.getBomCategory(), "category should be updated to BT");
    }

    /**
     * W-SY-64: REGISTER BOM with non-existent BOM fails.
     */
    @Test
    @Order(8)
    void w_sy_64_registerBomNonExistent() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "REGISTER BOM SY_NONEXISTENT AS BT");

        assertFalse(result.pass(), "non-existent BOM should fail");
        assertTrue(result.summary().contains("not found"));
    }

    /**
     * W-SY-65: REGISTER BOM with non-existent category fails.
     */
    @Test
    @Order(9)
    void w_sy_65_registerBomBadCategory() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "REGISTER BOM SY_REGISTER_TEST AS ZZ");

        assertFalse(result.pass(), "non-existent category should fail");
        assertTrue(result.summary().contains("not found"));
    }

    // ── W-SY-66..68: STACK FLOORS (L3) ──────────────────────────────────

    /**
     * W-SY-66: STACK FLOORS computes cumulative dz offsets.
     */
    @Test
    @Order(10)
    void w_sy_66_stackFloors() throws SQLException {
        // Create a unit with two floors of known heights
        registry.dispatch(bomCtx,
            "CREATE BOM SY_STACK_TEST TYPE BUILDING CATEGORY RE");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STACK_TEST CHILD FLOOR_SH_GF_STD ROLE SLAB SEQ 10 HEIGHT 200 COMPONENT_TYPE MAKE");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STACK_TEST CHILD FLOOR_SH_GF_STD ROLE FLOOR_GF SEQ 20 HEIGHT 3000 COMPONENT_TYPE MAKE");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STACK_TEST CHILD FLOOR_SH_GF_STD ROLE ROOF SEQ 30 HEIGHT 500 COMPONENT_TYPE MAKE");

        VerbResult<?> result = registry.dispatch(bomCtx,
            "STACK FLOORS IN SY_STACK_TEST");

        assertTrue(result.pass(), "STACK FLOORS should pass: " + result.summary());
        assertEquals("STACK FLOORS", result.verb());

        StackFloorsVerb.StackPayload p =
            (StackFloorsVerb.StackPayload) result.payload();
        assertEquals("SY_STACK_TEST", p.unitBomId());
        assertEquals(3, p.floorCount());

        // Verify dz offsets: 0, 0.2 (200mm), 3.2 (200+3000mm)
        assertEquals(0.0, p.offsets()[0], 0.001, "slab at dz=0");
        assertEquals(0.2, p.offsets()[1], 0.001, "floor at dz=0.2");
        assertEquals(3.2, p.offsets()[2], 0.001, "roof at dz=3.2");
        assertEquals(3.7, p.totalHeight(), 0.001, "total=3.7m");
    }

    /**
     * W-SY-67: STACK FLOORS on empty unit fails.
     */
    @Test
    @Order(11)
    void w_sy_67_stackFloorsEmpty() {
        registry.dispatch(bomCtx,
            "CREATE BOM SY_EMPTY_UNIT TYPE BUILDING CATEGORY RE");

        VerbResult<?> result = registry.dispatch(bomCtx,
            "STACK FLOORS IN SY_EMPTY_UNIT");

        assertFalse(result.pass(), "empty unit should fail");
        assertTrue(result.summary().contains("no children"));
    }

    /**
     * W-SY-68: STACK FLOORS on non-existent unit fails.
     */
    @Test
    @Order(12)
    void w_sy_68_stackFloorsNonExistent() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "STACK FLOORS IN SY_NONEXISTENT_UNIT");

        assertFalse(result.pass(), "non-existent unit should fail");
        assertTrue(result.summary().contains("not found"));
    }

    // ── W-SY-69..70: COMPOSE BUILDING (L3) ──────────────────────────────

    /**
     * W-SY-69: COMPOSE BUILDING RESIDENTIAL creates a unit BOM tree.
     */
    @Test
    @Order(13)
    void w_sy_69_composeBuilding() throws SQLException {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "COMPOSE BUILDING RESIDENTIAL 10000 8000 6000 UNITS 1");

        assertTrue(result.pass(), "COMPOSE BUILDING should pass: " + result.summary());
        assertEquals("COMPOSE BUILDING", result.verb());

        ComposeBuildingVerb.ComposeBuildingPayload p =
            (ComposeBuildingVerb.ComposeBuildingPayload) result.payload();
        assertNotNull(p.bldgBomId());
        assertTrue(p.totalLines() > 0, "should have created BOM lines");

        // Verify BOM exists
        MBOM unit = MBOM.get(conn, p.bldgBomId());
        assertNotNull(unit, "building BOM should exist");
        assertEquals("BUILDING", unit.getBomType());
        assertEquals("RE", unit.getBomCategory());  // derived from RESIDENTIAL input

        // Verify it has children
        List<MBOMLine> children = MBOMLine.getByBom(conn, p.bldgBomId());
        assertFalse(children.isEmpty(), "should have child lines");
    }

    /**
     * W-SY-70: COMPOSE BUILDING with unknown type fails.
     */
    @Test
    @Order(14)
    void w_sy_70_composeBuildingUnknownType() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "COMPOSE BUILDING LUNAR 10000 8000 6000 UNITS 1");

        assertFalse(result.pass(), "unknown building type should fail");
        assertTrue(result.summary().contains("unknown building type"));
    }

    // ── W-SY-71..72: ADD FLOOR (L3) ─────────────────────────────────────

    /**
     * W-SY-71: ADD FLOOR appends slab + floor to existing unit.
     */
    @Test
    @Order(15)
    void w_sy_71_addFloor() throws SQLException {
        // Use SY_STACK_TEST from W-SY-66
        int before = MBOMLine.getByBom(conn, "SY_STACK_TEST").size();

        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD FLOOR L2 TO SY_STACK_TEST HEIGHT 3000 ROOMS BT");

        assertTrue(result.pass(), "ADD FLOOR should pass: " + result.summary());
        assertEquals("ADD FLOOR", result.verb());

        AddFloorVerb.AddFloorPayload p =
            (AddFloorVerb.AddFloorPayload) result.payload();
        assertEquals("SY_STACK_TEST", p.unitBomId());
        assertNotNull(p.slabBomId(), "slab should be created");
        assertTrue(p.newRoofDz() > 0, "roofDz should be positive");

        int after = MBOMLine.getByBom(conn, "SY_STACK_TEST").size();
        assertTrue(after > before, "should have added lines");
    }

    /**
     * W-SY-72: ADD FLOOR to non-existent unit fails.
     */
    @Test
    @Order(16)
    void w_sy_72_addFloorNonExistent() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD FLOOR L2 TO SY_NONEXISTENT_UNIT HEIGHT 3000 ROOMS BT");

        assertFalse(result.pass(), "non-existent unit should fail");
        assertTrue(result.summary().contains("not found"));
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    @Test
    @Order(99)
    void cleanup() {
        // Clean up test BOMs
        registry.dispatch(bomCtx, "DELETE BOM SY_REGISTER_TEST");
        registry.dispatch(bomCtx, "DELETE BOM SY_STACK_TEST");
        registry.dispatch(bomCtx, "DELETE BOM SY_EMPTY_UNIT");
        // COMPOSE BUILDING creates SY_RE_UNIT_* — clean up
        registry.dispatch(bomCtx, "DELETE BOM SY_RE_UNIT_10000x8000x6000");
        // ADD FLOOR creates SY_SLAB_* and SY_FLOOR_*
        registry.dispatch(bomCtx, "DELETE BOM SY_SLAB_L2_10000x8000");
        registry.dispatch(bomCtx, "DELETE BOM SY_FLOOR_L2_10000x8000");
        // L4 category cleanup — we can't delete via verbs, so leave them
        // (temp DB is deleted on tearDown anyway)
    }
}
