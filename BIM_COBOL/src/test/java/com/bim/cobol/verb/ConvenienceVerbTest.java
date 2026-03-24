package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for §18.6 Level 1 — Room-Level Convenience Verbs.
 *
 * <p>W-SY-30..43: CREATE ROOM, FURNISH ROOM, RESIZE ROOM, STRIP ROOM.
 *
 * <p>All tests use a temp copy of BOM.db so production data is never mutated.
 * FURNISH ROOM needs M_Product lookup — M_Product is in BOM.db, so bomConn
 * serves as both bomConn and componentConn.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConvenienceVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext bomCtx;
    private static VerbContext fullCtx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File(System.getProperty("bom.db"));
        assertTrue(source.exists(), System.getProperty("bom.db") + " must exist (set -Dbom.db=library/_XX_compile.db)");
        tempDb = File.createTempFile("convenience_verb_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        bomCtx = VerbContext.ofBom(conn);
        // M_Product is in BOM.db, so bomConn serves as componentConn too
        fullCtx = VerbContext.of(conn, conn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
        if (tempDb != null) tempDb.delete();
    }

    // ── W-SY-30..33: CREATE ROOM ────────────────────────────────────────

    /**
     * W-SY-30: CREATE ROOM with known category creates BOM + populates children.
     */
    @Test
    @Order(1)
    void w_sy_30_createRoomWithChildren() throws SQLException {
        // BT category has BATHROOM_FURNITURE_SET (sumW=800, maxD=500, maxH=850) — fits 2000x1000x1000
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE ROOM BT 2000 1000 1000");

        assertTrue(result.pass(), "CREATE ROOM should pass: " + result.summary());
        assertEquals("CREATE ROOM", result.verb());

        // Verify BOM was created
        MBOM bom = MBOM.get(conn, "SY_BT_2000x1000");
        assertNotNull(bom, "SY_BT_2000x1000 should exist");
        assertEquals("SET", bom.getBomType());
        assertEquals("BT", bom.getProductCategory());

        // Verify children were cloned from template
        CreateRoomVerb.CreateRoomPayload p =
            (CreateRoomVerb.CreateRoomPayload) result.payload();
        assertNotNull(p.bomId());
        assertEquals("BT", p.category());
        assertTrue(p.childCount() > 0, "should have children from template");
        assertNotNull(p.sourceTemplateBomId(), "should report source template");

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_BT_2000x1000");
        assertEquals(p.childCount(), children.size());
    }

    /**
     * W-SY-31: CREATE ROOM EMPTY creates BOM with zero children.
     */
    @Test
    @Order(2)
    void w_sy_31_createRoomEmpty() throws SQLException {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE ROOM BD 4000 3000 2800 EMPTY");

        assertTrue(result.pass(), "CREATE ROOM EMPTY should pass: " + result.summary());

        MBOM bom = MBOM.get(conn, "SY_BD_4000x3000");
        assertNotNull(bom, "SY_BD_4000x3000 should exist");
        assertEquals("SET", bom.getBomType());
        assertEquals("BD", bom.getProductCategory());

        CreateRoomVerb.CreateRoomPayload p =
            (CreateRoomVerb.CreateRoomPayload) result.payload();
        assertEquals(0, p.childCount(), "EMPTY room should have 0 children");
        assertNull(p.sourceTemplateBomId(), "EMPTY has no template");

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_BD_4000x3000");
        assertEquals(0, children.size());
    }

    /**
     * W-SY-32: CREATE ROOM with unknown category fails gracefully.
     */
    @Test
    @Order(3)
    void w_sy_32_createRoomUnknownCategory() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE ROOM ZZ 3000 2500 2800");

        assertFalse(result.pass(), "unknown category should fail");
        assertTrue(result.summary().contains("no BOMs found"),
            "should mention missing category: " + result.summary());
    }

    /**
     * W-SY-33: CREATE ROOM with too-small AABB: BOM created but no children (no template fit).
     */
    @Test
    @Order(4)
    void w_sy_33_createRoomTooSmall() throws SQLException {
        // 100x100x100 mm is too small for any KT template to fit
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE ROOM KT 100 100 100");

        // The verb succeeds (creates BOM) but with 0 children since no template fits
        assertTrue(result.pass(), "CREATE ROOM should pass even with no template fit");

        CreateRoomVerb.CreateRoomPayload p =
            (CreateRoomVerb.CreateRoomPayload) result.payload();
        assertEquals(0, p.childCount(), "too-small AABB should yield 0 children");

        // Cleanup
        registry.dispatch(bomCtx, "DELETE BOM SY_KT_100x100");
    }

    // ── W-SY-34..36: FURNISH ROOM ───────────────────────────────────────

    /**
     * W-SY-34: FURNISH ROOM adds products to existing BOM.
     */
    @Test
    @Order(5)
    void w_sy_34_furnishRoomAddsProducts() throws SQLException {
        // Use the empty BD room created in W-SY-31
        VerbResult<?> result = registry.dispatch(fullCtx,
            "FURNISH ROOM SY_BD_4000x3000 WITH Base_Cabinet Desk");

        assertTrue(result.pass(), "FURNISH ROOM should pass: " + result.summary());
        assertEquals("FURNISH ROOM", result.verb());

        FurnishRoomVerb.FurnishPayload p =
            (FurnishRoomVerb.FurnishPayload) result.payload();
        assertEquals(2, p.addedCount());
        assertEquals(2, p.placedProducts().length);
        assertEquals(0, p.unplacedProducts().length);

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_BD_4000x3000");
        assertEquals(2, children.size());
        // First product at dx=0, second at dx=0.6 (Base_Cabinet width=600mm=0.6m)
        assertEquals(0.0, children.get(0).getDx(), 0.0001);
        assertEquals(0.6, children.get(1).getDx(), 0.0001);
    }

    /**
     * W-SY-35: FURNISH ROOM with COUNT adds N copies.
     */
    @Test
    @Order(6)
    void w_sy_35_furnishRoomCount() throws SQLException {
        // Create a fresh empty room for COUNT test
        registry.dispatch(bomCtx,
            "CREATE ROOM LI 8000 5000 2800 EMPTY");

        VerbResult<?> result = registry.dispatch(fullCtx,
            "FURNISH ROOM SY_LI_8000x5000 WITH Upper_Cabinet COUNT 3");

        assertTrue(result.pass(), "FURNISH ROOM COUNT should pass: " + result.summary());

        FurnishRoomVerb.FurnishPayload p =
            (FurnishRoomVerb.FurnishPayload) result.payload();
        assertEquals(3, p.addedCount(), "COUNT 3 should add 3 products");

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_LI_8000x5000");
        assertEquals(3, children.size());
    }

    /**
     * W-SY-36: FURNISH ROOM fails on non-existent BOM.
     */
    @Test
    @Order(7)
    void w_sy_36_furnishRoomNonExistent() {
        VerbResult<?> result = registry.dispatch(fullCtx,
            "FURNISH ROOM SY_NONEXISTENT WITH Base_Cabinet");

        assertFalse(result.pass(), "non-existent BOM should fail");
        assertTrue(result.summary().contains("not found"));
    }

    // ── W-SY-37..39: RESIZE ROOM ────────────────────────────────────────

    /**
     * W-SY-37: RESIZE ROOM clones with AS keyword.
     */
    @Test
    @Order(8)
    void w_sy_37_resizeRoomClone() throws SQLException {
        // Resize the BT room (created in W-SY-30) to a bigger AABB with AS
        VerbResult<?> result = registry.dispatch(bomCtx,
            "RESIZE ROOM SY_BT_2000x1000 TO 3000 2000 1500 AS SY_BT_3000x2000");

        assertTrue(result.pass(), "RESIZE ROOM AS should pass: " + result.summary());
        assertEquals("RESIZE ROOM", result.verb());

        ResizeRoomVerb.ResizePayload p =
            (ResizeRoomVerb.ResizePayload) result.payload();
        assertEquals("SY_BT_3000x2000", p.newBomId());
        assertTrue(p.keptCount() > 0, "should keep at least some children");

        // Verify new BOM was created
        MBOM newBom = MBOM.get(conn, "SY_BT_3000x2000");
        assertNotNull(newBom, "cloned BOM should exist");
        assertEquals("SET", newBom.getBomType());
        assertEquals("BT", newBom.getProductCategory());

        // Original should still have its children
        List<MBOMLine> origChildren = MBOMLine.getByBom(conn, "SY_BT_2000x1000");
        assertTrue(origChildren.size() > 0, "original should be untouched");
    }

    /**
     * W-SY-38: RESIZE ROOM drops items that don't fit.
     */
    @Test
    @Order(9)
    void w_sy_38_resizeRoomDrops() throws SQLException {
        // Create a test BOM with items of known sizes
        registry.dispatch(bomCtx,
            "CREATE BOM SY_RESIZE_SRC TYPE SET CATEGORY BD DOC_SUB_TYPE SY");
        // Add a small item (fits 1000x1000x1000)
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_RESIZE_SRC CHILD ELEC_OUTLET ROLE SMALL_ITEM SEQ 10 WIDTH 85 DEPTH 40 HEIGHT 85");
        // Add a large item (needs 900x900x2100)
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_RESIZE_SRC CHILD FIXTURE_SHOWER ROLE BIG_ITEM SEQ 20 WIDTH 900 DEPTH 900 HEIGHT 2100");

        // Resize to a small AABB that won't fit the shower
        VerbResult<?> result = registry.dispatch(bomCtx,
            "RESIZE ROOM SY_RESIZE_SRC TO 500 500 1000 AS SY_RESIZE_SMALL");

        assertTrue(result.pass(), "RESIZE should pass: " + result.summary());

        ResizeRoomVerb.ResizePayload p =
            (ResizeRoomVerb.ResizePayload) result.payload();
        assertEquals(1, p.keptCount(), "only small item should fit");
        assertEquals(1, p.droppedProducts().length, "shower should be dropped");
        assertEquals("FIXTURE_SHOWER", p.droppedProducts()[0]);

        // Verify new BOM has only 1 child
        List<MBOMLine> newChildren = MBOMLine.getByBom(conn, "SY_RESIZE_SMALL");
        assertEquals(1, newChildren.size());
        assertEquals("ELEC_OUTLET", newChildren.get(0).getChildProductId());
    }

    /**
     * W-SY-39: RESIZE ROOM without AS modifies in-place.
     */
    @Test
    @Order(10)
    void w_sy_39_resizeRoomInPlace() throws SQLException {
        // Use SY_RESIZE_SRC (2 children: ELEC_OUTLET, FIXTURE_SHOWER)
        // Resize in-place to small AABB — shower should be removed
        int beforeCount = MBOMLine.getByBom(conn, "SY_RESIZE_SRC").size();
        assertEquals(2, beforeCount, "should have 2 children before resize");

        VerbResult<?> result = registry.dispatch(bomCtx,
            "RESIZE ROOM SY_RESIZE_SRC TO 500 500 1000");

        assertTrue(result.pass(), "in-place RESIZE should pass: " + result.summary());

        ResizeRoomVerb.ResizePayload p =
            (ResizeRoomVerb.ResizePayload) result.payload();
        assertEquals("SY_RESIZE_SRC", p.newBomId(), "in-place returns same bom_id");
        assertEquals(1, p.keptCount());
        assertEquals(1, p.droppedProducts().length);

        // Verify in DB — shower removed, outlet remains
        List<MBOMLine> afterChildren = MBOMLine.getByBom(conn, "SY_RESIZE_SRC");
        assertEquals(1, afterChildren.size());
        assertEquals("ELEC_OUTLET", afterChildren.get(0).getChildProductId());
    }

    // ── W-SY-40..42: STRIP ROOM ─────────────────────────────────────────

    /**
     * W-SY-40: STRIP ROOM removes all children.
     */
    @Test
    @Order(11)
    void w_sy_40_stripRoomAll() throws SQLException {
        // Create a BOM with mixed children
        registry.dispatch(bomCtx,
            "CREATE BOM SY_STRIP_TEST TYPE SET CATEGORY LI DOC_SUB_TYPE SY");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD Base_Cabinet ROLE ITEM_1 SEQ 10");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD SH_BED_SET ROLE SUB_ASM SEQ 20 COMPONENT_TYPE MAKE");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD BUFFER ROLE FILLER SEQ 30 COMPONENT_TYPE PHANTOM");

        assertEquals(3, MBOMLine.getByBom(conn, "SY_STRIP_TEST").size());

        VerbResult<?> result = registry.dispatch(bomCtx,
            "STRIP ROOM SY_STRIP_TEST");

        assertTrue(result.pass(), "STRIP ROOM should pass: " + result.summary());
        assertEquals("STRIP ROOM", result.verb());

        StripRoomVerb.StripPayload p =
            (StripRoomVerb.StripPayload) result.payload();
        assertEquals(3, p.removedCount());
        assertEquals(0, p.remainingCount());

        assertEquals(0, MBOMLine.getByBom(conn, "SY_STRIP_TEST").size());
    }

    /**
     * W-SY-41: STRIP ROOM KEEP STRUCTURE preserves MAKE items.
     */
    @Test
    @Order(12)
    void w_sy_41_stripRoomKeepStructure() throws SQLException {
        // Repopulate SY_STRIP_TEST with mixed children
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD Base_Cabinet ROLE BUY_ITEM SEQ 10");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD SH_BED_SET ROLE MAKE_SUB SEQ 20 COMPONENT_TYPE MAKE");
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD BUFFER ROLE PHANTOM_FILLER SEQ 30 COMPONENT_TYPE PHANTOM");

        assertEquals(3, MBOMLine.getByBom(conn, "SY_STRIP_TEST").size());

        VerbResult<?> result = registry.dispatch(bomCtx,
            "STRIP ROOM SY_STRIP_TEST KEEP STRUCTURE");

        assertTrue(result.pass(), "STRIP KEEP STRUCTURE should pass: " + result.summary());

        StripRoomVerb.StripPayload p =
            (StripRoomVerb.StripPayload) result.payload();
        assertEquals(2, p.removedCount(), "BUY + PHANTOM removed");
        assertEquals(1, p.remainingCount(), "MAKE preserved");

        List<MBOMLine> remaining = MBOMLine.getByBom(conn, "SY_STRIP_TEST");
        assertEquals(1, remaining.size());
        assertEquals("MAKE", remaining.get(0).getComponentType());
    }

    /**
     * W-SY-42: STRIP ROOM ROLE removes only matching role.
     */
    @Test
    @Order(13)
    void w_sy_42_stripRoomRole() throws SQLException {
        // Add another line to have 2 children (MAKE_SUB from W-SY-41 + a new one)
        registry.dispatch(bomCtx,
            "ADD LINE TO SY_STRIP_TEST CHILD Desk ROLE DESK_1 SEQ 40");

        assertEquals(2, MBOMLine.getByBom(conn, "SY_STRIP_TEST").size());

        VerbResult<?> result = registry.dispatch(bomCtx,
            "STRIP ROOM SY_STRIP_TEST ROLE DESK_1");

        assertTrue(result.pass(), "STRIP ROLE should pass: " + result.summary());

        StripRoomVerb.StripPayload p =
            (StripRoomVerb.StripPayload) result.payload();
        assertEquals(1, p.removedCount(), "only DESK_1 removed");
        assertEquals(1, p.remainingCount(), "MAKE_SUB preserved");

        List<MBOMLine> remaining = MBOMLine.getByBom(conn, "SY_STRIP_TEST");
        assertEquals(1, remaining.size());
        assertEquals("MAKE_SUB", remaining.get(0).getRole());
    }

    // ── W-SY-43: Registry count ──────────────────────────────────────────

    /**
     * W-SY-43: VerbRegistry verb count.
     */
    @Test
    @Order(14)
    void w_sy_43_registryCount() {
        assertEquals(60, registry.size(),
            "54 prior + 5 H2 verb wrappers + 1 H2+ VoidEmptySpace = 60");
    }

    // Cleanup
    @Test
    @Order(99)
    void cleanup() {
        registry.dispatch(bomCtx, "DELETE BOM SY_BT_2000x1000");
        registry.dispatch(bomCtx, "DELETE BOM SY_BD_4000x3000");
        registry.dispatch(bomCtx, "DELETE BOM SY_LI_8000x5000");
        registry.dispatch(bomCtx, "DELETE BOM SY_BT_3000x2000");
        registry.dispatch(bomCtx, "DELETE BOM SY_RESIZE_SRC");
        registry.dispatch(bomCtx, "DELETE BOM SY_RESIZE_SMALL");
        registry.dispatch(bomCtx, "DELETE BOM SY_STRIP_TEST");
    }
}
