package com.bim.designer;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;
import com.bim.designer.dao.DesignerDAO;
import com.bim.designer.dao.StubDataSeeder;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Design Editing — placeItem, addRoom, removeRoom, addStorey.
 *
 * <p>Implements BIM_Designer_SRS.md §15 (placeItem) and §16 (layout editing)
 * — Witness: W-PLACE-*, W-LAYOUT-*
 *
 * <p>Uses StubDataSeeder with in-memory SQLite (same pattern as BrowseItemsTest).
 */
@DisplayName("Design Editing — Place Item + Layout (§15, §16)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesignEditingTest {

    private static Connection conn;
    private static DesignerAPIImpl api;

    /** Reusable bboxes: a 1-storey 2BR+1BT house at 10000x6000mm. */
    private static List<DesignBBox> baseBboxes;

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(conn);
        api = new DesignerAPIImpl(conn);

        // Generate a base layout for testing
        CreateNewRequest req = new CreateNewRequest(
                "TestHouse", "TERRACE", "MY",
                10000, 6000,
                2, 1, 1);
        CreateNewResponse resp = api.createNew(req);
        assertTrue(resp.success(), "Base layout generation must succeed");
        baseBboxes = resp.bboxes();
        assertFalse(baseBboxes.isEmpty(), "Base bboxes must not be empty");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── placeItem (§15) ──────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-PLACE-1: placeItem creates correct bbox with product dimensions")
    // Implementing BIM_Designer_SRS.md §15 — Witness: W-PLACE-1
    void w_place_1_creates_bbox() {
        // Find a ROOM bbox to place into
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        PlaceItemResponse resp = api.placeItem(new PlaceItemRequest(
                "TestHouse", room.bomId(), "BED_QUEEN_1600",
                0, 0, 0, baseBboxes));

        assertTrue(resp.success(), "placeItem should succeed");
        assertNotNull(resp.bbox(), "Response must contain a bbox");
        assertEquals("ITEM", resp.bbox().bomType(), "Placed item bomType = ITEM");

        // BED_QUEEN_1600: 1600x2100x500mm (stored as 1.6x2.1x0.5m in DB)
        double w = resp.bbox().maxX() - resp.bbox().minX();
        double d = resp.bbox().maxY() - resp.bbox().minY();
        double h = resp.bbox().maxZ() - resp.bbox().minZ();
        assertEquals(1600, w, 1.0, "Width should be 1600mm");
        assertEquals(2100, d, 1.0, "Depth should be 2100mm");
        assertEquals(500, h, 1.0, "Height should be 500mm");
    }

    @Test
    @Order(2)
    @DisplayName("W-PLACE-2: placed item at room origin + offset")
    // Implementing BIM_Designer_SRS.md §15 — Witness: W-PLACE-2
    void w_place_2_offset_position() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        double offsetX = 500, offsetY = 300, offsetZ = 0;
        PlaceItemResponse resp = api.placeItem(new PlaceItemRequest(
                "TestHouse", room.bomId(), "DESK_1500",
                offsetX, offsetY, offsetZ, baseBboxes));

        assertTrue(resp.success());
        DesignBBox item = resp.bbox();

        assertEquals(room.minX() + offsetX, item.minX(), 0.01,
                "Item minX = room minX + offsetX");
        assertEquals(room.minY() + offsetY, item.minY(), 0.01,
                "Item minY = room minY + offsetY");
        assertEquals(room.minZ() + offsetZ, item.minZ(), 0.01,
                "Item minZ = room minZ + offsetZ");
    }

    @Test
    @Order(3)
    @DisplayName("W-PLACE-3: placeItem with bad product returns error")
    // Implementing BIM_Designer_SRS.md §15 — Witness: W-PLACE-3
    void w_place_3_bad_product() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        PlaceItemResponse resp = api.placeItem(new PlaceItemRequest(
                "TestHouse", room.bomId(), "NONEXISTENT_PRODUCT",
                0, 0, 0, baseBboxes));

        assertFalse(resp.success(), "Should fail for unknown product");
        assertNotNull(resp.error(), "Error message must be present");
        assertTrue(resp.error().contains("NONEXISTENT_PRODUCT"),
                "Error should mention the product ID");
    }

    // ── addRoom (§16) ────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-LAYOUT-1: addRoom increases room count")
    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-1
    void w_layout_1_add_room() {
        // First save current layout so addRoom can retrieve it
        api.save("TestHouse", baseBboxes, "base");

        int baseRoomCount = (int) baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType())).count();

        LayoutResponse resp = api.addRoom("TestHouse", "BEDROOM", "GF");

        assertTrue(resp.success(), "addRoom should succeed");
        assertTrue(resp.roomCount() > baseRoomCount,
                "Room count should increase: was " + baseRoomCount + ", now " + resp.roomCount());
        assertFalse(resp.bboxes().isEmpty(), "Bboxes must not be empty");
    }

    // ── removeRoom (§16) ─────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-LAYOUT-2: removeRoom decreases room count")
    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-2
    void w_layout_2_remove_room() {
        // Save the base bboxes so removeRoom can retrieve them
        api.save("RemoveTest", baseBboxes, "base");

        int baseRoomCount = (int) baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType())).count();

        // Find a BEDROOM to remove
        DesignBBox bedroom = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()) && "BEDROOM".equals(b.category()))
                .findFirst().orElseThrow();

        LayoutResponse resp = api.removeRoom("RemoveTest", bedroom.bomId());

        assertTrue(resp.success(), "removeRoom should succeed");
        assertTrue(resp.roomCount() < baseRoomCount,
                "Room count should decrease: was " + baseRoomCount + ", now " + resp.roomCount());
    }

    // ── addStorey (§16) ──────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("W-LAYOUT-3: addStorey clones rooms at correct Z offset")
    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-3
    void w_layout_3_add_storey() {
        // Save base (1-storey) layout
        api.save("StoreyTest", baseBboxes, "base");

        int baseFloorCount = (int) baseBboxes.stream()
                .filter(b -> "FLOOR".equals(b.bomType())).count();

        LayoutResponse resp = api.addStorey("StoreyTest");

        assertTrue(resp.success(), "addStorey should succeed");

        int newFloorCount = (int) resp.bboxes().stream()
                .filter(b -> "FLOOR".equals(b.bomType())).count();
        assertEquals(baseFloorCount + 1, newFloorCount,
                "Should have one more floor: was " + baseFloorCount);

        // Verify new storey rooms are at Z > 0
        boolean hasUpperRooms = resp.bboxes().stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .anyMatch(b -> b.minZ() > 0);
        assertTrue(hasUpperRooms, "New storey rooms should have minZ > 0");

        // Verify BUILDING bbox height increased
        DesignBBox building = resp.bboxes().stream()
                .filter(b -> "BUILDING".equals(b.bomType()))
                .findFirst().orElseThrow();
        double buildingHeight = building.maxZ() - building.minZ();
        assertTrue(buildingHeight > 3000,
                "Building height should be > 3000mm (multiple storeys): " + buildingHeight);
    }

    // ── Latency ──────────────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("W-LAYOUT-4: all operations < 200ms")
    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-4
    void w_layout_4_latency() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        // placeItem
        long t0 = System.currentTimeMillis();
        api.placeItem(new PlaceItemRequest(
                "TestHouse", room.bomId(), "BED_QUEEN_1600",
                0, 0, 0, baseBboxes));
        long placeMs = System.currentTimeMillis() - t0;
        assertTrue(placeMs < 200, "placeItem should be < 200ms, was " + placeMs);

        // Save for layout ops
        api.save("LatencyTest", baseBboxes, "base");

        // addRoom
        t0 = System.currentTimeMillis();
        api.addRoom("LatencyTest", "BEDROOM", "GF");
        long addRoomMs = System.currentTimeMillis() - t0;
        assertTrue(addRoomMs < 200, "addRoom should be < 200ms, was " + addRoomMs);

        // removeRoom
        t0 = System.currentTimeMillis();
        api.removeRoom("LatencyTest", room.bomId());
        long removeMs = System.currentTimeMillis() - t0;
        assertTrue(removeMs < 200, "removeRoom should be < 200ms, was " + removeMs);

        // addStorey
        t0 = System.currentTimeMillis();
        api.addStorey("LatencyTest");
        long storeyMs = System.currentTimeMillis() - t0;
        assertTrue(storeyMs < 200, "addStorey should be < 200ms, was " + storeyMs);
    }
}
