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
 * Witness tests for §18.7 Level 2 — Floor-Level Verbs + PARTITION AABB.
 *
 * <p>W-SY-44..56: PARTITION AABB, CREATE FLOOR, ADD ROOM, REMOVE ROOM, SWAP ROOM.
 *
 * <p>All tests use a temp copy of BOM.db so production data is never mutated.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FloorVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext bomCtx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File("library/BOM.db");
        assertTrue(source.exists(), "library/BOM.db must exist");
        tempDb = File.createTempFile("floor_verb_test_", ".db");
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

    // ── W-SY-44..46: PARTITION AABB ──────────────────────────────────────

    /**
     * W-SY-44: PARTITION AABB with known categories produces non-empty slots.
     */
    @Test
    @Order(1)
    void w_sy_44_partitionAabbBasic() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "PARTITION AABB 10000 8000 ROOMS BT");

        assertTrue(result.pass(), "PARTITION AABB should pass: " + result.summary());
        assertEquals("PARTITION AABB", result.verb());

        PartitionAabbVerb.PartitionPayload p =
            (PartitionAabbVerb.PartitionPayload) result.payload();
        assertFalse(p.slots().isEmpty(), "should have at least one slot");
    }

    /**
     * W-SY-45: PARTITION AABB with multiple categories strip-packs.
     */
    @Test
    @Order(2)
    void w_sy_45_partitionAabbMultiple() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "PARTITION AABB 20000 8000 ROOMS BT BT");

        assertTrue(result.pass(), "PARTITION AABB multi should pass: " + result.summary());

        PartitionAabbVerb.PartitionPayload p =
            (PartitionAabbVerb.PartitionPayload) result.payload();
        assertEquals(2, p.slots().size(), "should have 2 slots");

        // Second slot's offsetX should be > 0 (strip-packed after first)
        if (p.slots().get(0).allocW() > 0) {
            assertTrue(p.slots().get(1).offsetX() > 0,
                "second slot should be offset from first");
        }
    }

    /**
     * W-SY-46: PARTITION AABB with unknown category yields empty slot.
     */
    @Test
    @Order(3)
    void w_sy_46_partitionAabbUnknown() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "PARTITION AABB 10000 8000 ROOMS ZZ");

        assertTrue(result.pass(), "PARTITION AABB with unknown cat still passes");

        PartitionAabbVerb.PartitionPayload p =
            (PartitionAabbVerb.PartitionPayload) result.payload();
        assertEquals(1, p.slots().size());
        assertNull(p.slots().get(0).bestFitBomId(), "unknown category → null BOM");
    }

    // ── W-SY-47..49: CREATE FLOOR ────────────────────────────────────────

    /**
     * W-SY-47: CREATE FLOOR with BT category creates FLOOR BOM with room children.
     */
    @Test
    @Order(4)
    void w_sy_47_createFloorBasic() throws SQLException {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE FLOOR GF ROOMS BT WIDTH 10000 DEPTH 8000 HEIGHT 3000");

        assertTrue(result.pass(), "CREATE FLOOR should pass: " + result.summary());
        assertEquals("CREATE FLOOR", result.verb());

        CreateFloorVerb.CreateFloorPayload p =
            (CreateFloorVerb.CreateFloorPayload) result.payload();
        assertNotNull(p.bomId(), "should have bomId");
        assertEquals(1, p.roomCount(), "one room category → one room");
        assertTrue(p.rooms().length > 0);

        // Verify BOM was created
        MBOM bom = MBOM.get(conn, p.bomId());
        assertNotNull(bom, "floor BOM should exist");
        assertEquals("FLOOR", bom.getBomType());

        // Verify children
        List<MBOMLine> children = MBOMLine.getByBom(conn, p.bomId());
        assertEquals(1, children.size());
        assertEquals("MAKE", children.get(0).getComponentType());
    }

    /**
     * W-SY-48: CREATE FLOOR fails with unknown category.
     */
    @Test
    @Order(5)
    void w_sy_48_createFloorUnknownCategory() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE FLOOR GF2 ROOMS ZZ WIDTH 10000 DEPTH 8000 HEIGHT 3000");

        assertFalse(result.pass(), "unknown category should fail");
        assertTrue(result.summary().contains("no BOM fits"),
            "should mention no fit: " + result.summary());
    }

    /**
     * W-SY-49: CREATE FLOOR fails on missing dimensions.
     */
    @Test
    @Order(6)
    void w_sy_49_createFloorMissingDims() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "CREATE FLOOR GF3 ROOMS BT");

        assertFalse(result.pass(), "missing dims should fail");
    }

    // ── W-SY-50..51: ADD ROOM ────────────────────────────────────────────

    /**
     * W-SY-50: ADD ROOM appends a room to an existing floor.
     */
    @Test
    @Order(7)
    void w_sy_50_addRoom() throws SQLException {
        // Get the floor created in W-SY-47
        String floorBomId = "SY_FLOOR_GF_10000x8000";
        int before = MBOMLine.getByBom(conn, floorBomId).size();

        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD ROOM BT TO " + floorBomId);

        assertTrue(result.pass(), "ADD ROOM should pass: " + result.summary());
        assertEquals("ADD ROOM", result.verb());

        AddRoomVerb.AddRoomPayload p =
            (AddRoomVerb.AddRoomPayload) result.payload();
        assertEquals(floorBomId, p.floorBomId());
        assertNotNull(p.addedBomId());

        int after = MBOMLine.getByBom(conn, floorBomId).size();
        assertEquals(before + 1, after, "one line added");
    }

    /**
     * W-SY-51: ADD ROOM to non-existent floor fails.
     */
    @Test
    @Order(8)
    void w_sy_51_addRoomNonExistent() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "ADD ROOM BT TO SY_NONEXISTENT_FLOOR");

        assertFalse(result.pass(), "non-existent floor should fail");
        assertTrue(result.summary().contains("not found"));
    }

    // ── W-SY-52..53: REMOVE ROOM ────────────────────────────────────────

    /**
     * W-SY-52: REMOVE ROOM deletes a room line from floor.
     */
    @Test
    @Order(9)
    void w_sy_52_removeRoom() throws SQLException {
        String floorBomId = "SY_FLOOR_GF_10000x8000";
        int before = MBOMLine.getByBom(conn, floorBomId).size();
        assertTrue(before > 0, "should have rooms to remove");

        VerbResult<?> result = registry.dispatch(bomCtx,
            "REMOVE ROOM ROOM_BT FROM " + floorBomId);

        assertTrue(result.pass(), "REMOVE ROOM should pass: " + result.summary());
        assertEquals("REMOVE ROOM", result.verb());

        RemoveRoomVerb.RemoveRoomPayload p =
            (RemoveRoomVerb.RemoveRoomPayload) result.payload();
        assertEquals(floorBomId, p.floorBomId());

        int after = MBOMLine.getByBom(conn, floorBomId).size();
        assertEquals(before - 1, after, "one line removed");
    }

    /**
     * W-SY-53: REMOVE ROOM with unknown role fails.
     */
    @Test
    @Order(10)
    void w_sy_53_removeRoomUnknownRole() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "REMOVE ROOM ROOM_ZZ FROM SY_FLOOR_GF_10000x8000");

        assertFalse(result.pass(), "unknown role should fail");
        assertTrue(result.summary().contains("no room"));
    }

    // ── W-SY-54..56: SWAP ROOM ──────────────────────────────────────────

    /**
     * W-SY-54: SWAP ROOM replaces a room's child BOM.
     */
    @Test
    @Order(11)
    void w_sy_54_swapRoom() throws SQLException {
        String floorBomId = "SY_FLOOR_GF_10000x8000";

        // Ensure there's a room to swap (add one back after W-SY-52 removed one)
        registry.dispatch(bomCtx, "ADD ROOM BT TO " + floorBomId);

        List<MBOMLine> children = MBOMLine.getByBom(conn, floorBomId);
        assertFalse(children.isEmpty(), "should have at least one room");

        String originalChild = children.get(0).getChildProductId();

        // We need a known BOM to swap to — use the same BOM (self-swap validates the mechanism)
        VerbResult<?> result = registry.dispatch(bomCtx,
            "SWAP ROOM ROOM_BT IN " + floorBomId + " WITH " + originalChild);

        assertTrue(result.pass(), "SWAP ROOM should pass: " + result.summary());
        assertEquals("SWAP ROOM", result.verb());

        SwapRoomVerb.SwapPayload p =
            (SwapRoomVerb.SwapPayload) result.payload();
        assertEquals(floorBomId, p.floorBomId());
    }

    /**
     * W-SY-55: SWAP ROOM to non-existent BOM fails.
     */
    @Test
    @Order(12)
    void w_sy_55_swapRoomNonExistentBom() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "SWAP ROOM ROOM_BT IN SY_FLOOR_GF_10000x8000 WITH SY_NONEXISTENT");

        assertFalse(result.pass(), "non-existent replacement should fail");
        assertTrue(result.summary().contains("not found"));
    }

    /**
     * W-SY-56: SWAP ROOM with unknown role fails.
     */
    @Test
    @Order(13)
    void w_sy_56_swapRoomUnknownRole() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "SWAP ROOM ROOM_ZZ IN SY_FLOOR_GF_10000x8000 WITH BATHROOM_FURNITURE_SET");

        assertFalse(result.pass(), "unknown role should fail");
        assertTrue(result.summary().contains("no room"));
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    @Test
    @Order(99)
    void cleanup() {
        registry.dispatch(bomCtx, "DELETE BOM SY_FLOOR_GF_10000x8000");
    }
}
