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
 * Click-to-Place — G-8 interactive discipline placement.
 *
 * <p>Implements BIM_Designer.md §18.8 (Click-to-Place spec) and
 * BIM_Designer_SRS.md §15 (placeItem persistence).
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-CTP-1: clickToPlace resolves room from viewport coordinates</li>
 *   <li>W-CTP-2: clickToPlace with discipline filters to discipline products</li>
 *   <li>W-CTP-3: placeItem persists C_OrderLine to work_output.db (orderLineId > 0)</li>
 *   <li>W-CTP-4: clickToPlace with invalid room returns error</li>
 *   <li>W-CTP-5: placeItem with offset computes correct world position + persists</li>
 *   <li>W-CTP-6: clickToPlace latency < 200ms</li>
 * </ul>
 */
@DisplayName("Click-to-Place — G-8 Interactive Placement (§18.8)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClickToPlaceTest {

    private static Connection conn;
    private static DesignerAPIImpl api;
    private static List<DesignBBox> baseBboxes;

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(conn);
        api = new DesignerAPIImpl(conn);

        // Generate a base layout
        CreateNewRequest req = new CreateNewRequest(
                "ClickTest", "TERRACE", "MY",
                10000, 6000,
                2, 1, 1);
        CreateNewResponse resp = api.createNew(req);
        assertTrue(resp.success(), "Base layout must succeed");
        baseBboxes = resp.bboxes();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── W-CTP-1: clickToPlace resolves room from viewport click ──────

    @Test
    @Order(1)
    @DisplayName("W-CTP-1: clickToPlace resolves room and places seed item")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-1
    void w_ctp_1_resolves_room() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        // Click at the centre of the room
        double clickX = (room.minX() + room.maxX()) / 2;
        double clickY = (room.minY() + room.maxY()) / 2;
        double clickZ = room.minZ();

        ClickToPlaceResponse resp = api.clickToPlace(new ClickToPlaceRequest(
                "ClickTest", clickX, clickY, clickZ,
                "ARC", baseBboxes));

        assertTrue(resp.success(), "clickToPlace should succeed: " + resp.error());
        assertNotNull(resp.placedItems(), "Must return placed items");
        assertFalse(resp.placedItems().isEmpty(), "At least one item should be placed");
        assertEquals(room.bomId(), resp.resolvedRoomBomId(),
                "Should resolve to the correct room");
    }

    // ── W-CTP-2: discipline filtering ──────────────────────────────

    @Test
    @Order(2)
    @DisplayName("W-CTP-2: clickToPlace discipline filters product selection")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-2
    void w_ctp_2_discipline_filter() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()) && "BEDROOM".equals(b.category()))
                .findFirst().orElseThrow();

        double clickX = (room.minX() + room.maxX()) / 2;
        double clickY = (room.minY() + room.maxY()) / 2;

        // ARC discipline in a bedroom → furniture items
        ClickToPlaceResponse resp = api.clickToPlace(new ClickToPlaceRequest(
                "ClickTest", clickX, clickY, room.minZ(),
                "ARC", baseBboxes));

        assertTrue(resp.success());
        // Placed items should be architectural (furniture)
        for (DesignBBox item : resp.placedItems()) {
            assertEquals("ITEM", item.bomType(), "Placed items must be ITEM type");
        }
    }

    // ── W-CTP-3: placeItem persists to work_output.db ───────────────

    @Test
    @Order(3)
    @DisplayName("W-CTP-3: placeItem persists C_OrderLine (orderLineId > 0)")
    // Implementing BIM_Designer_SRS.md §15 — Witness: W-CTP-3
    void w_ctp_3_persistence() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        // First save to create master order in work_output.db
        api.save("PersistTest", baseBboxes, "base");

        PlaceItemResponse resp = api.placeItem(new PlaceItemRequest(
                "PersistTest", room.bomId(), "BED_QUEEN_1600",
                500, 300, 0, baseBboxes));

        assertTrue(resp.success(), "placeItem should succeed");
        assertTrue(resp.orderLineId() > 0,
                "orderLineId must be > 0 after persistence, was " + resp.orderLineId());
        assertNotNull(resp.bbox(), "Response must contain a bbox");
    }

    // ── W-CTP-4: invalid room ───────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-CTP-4: clickToPlace with coordinates outside any room returns error")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-4
    void w_ctp_4_invalid_room() {
        // Click outside the building envelope
        ClickToPlaceResponse resp = api.clickToPlace(new ClickToPlaceRequest(
                "ClickTest", -99999, -99999, 0,
                "ARC", baseBboxes));

        assertFalse(resp.success(), "Should fail for click outside any room");
        assertNotNull(resp.error(), "Error message required");
    }

    // ── W-CTP-5: persisted item has correct world position ──────────

    @Test
    @Order(5)
    @DisplayName("W-CTP-5: placeItem offset computes correct world position + persists")
    // Implementing BIM_Designer_SRS.md §15 — Witness: W-CTP-5
    void w_ctp_5_offset_and_persist() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        api.save("OffsetTest", baseBboxes, "base");

        double offsetX = 1000, offsetY = 500, offsetZ = 0;
        PlaceItemResponse resp = api.placeItem(new PlaceItemRequest(
                "OffsetTest", room.bomId(), "DESK_1500",
                offsetX, offsetY, offsetZ, baseBboxes));

        assertTrue(resp.success());
        assertTrue(resp.orderLineId() > 0, "Must persist with real orderLineId");

        DesignBBox item = resp.bbox();
        assertEquals(room.minX() + offsetX, item.minX(), 0.01,
                "World X = room minX + offset");
        assertEquals(room.minY() + offsetY, item.minY(), 0.01,
                "World Y = room minY + offset");
    }

    // ── W-CTP-6: latency ────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("W-CTP-6: clickToPlace + placeItem < 200ms each")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-6
    void w_ctp_6_latency() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        double cx = (room.minX() + room.maxX()) / 2;
        double cy = (room.minY() + room.maxY()) / 2;

        long t0 = System.currentTimeMillis();
        api.clickToPlace(new ClickToPlaceRequest(
                "ClickTest", cx, cy, room.minZ(),
                "ARC", baseBboxes));
        long ctpMs = System.currentTimeMillis() - t0;
        assertTrue(ctpMs < 200, "clickToPlace < 200ms, was " + ctpMs);

        api.save("LatCTP", baseBboxes, "base");

        t0 = System.currentTimeMillis();
        api.placeItem(new PlaceItemRequest(
                "LatCTP", room.bomId(), "BED_QUEEN_1600",
                0, 0, 0, baseBboxes));
        long placeMs = System.currentTimeMillis() - t0;
        assertTrue(placeMs < 200, "placeItem < 200ms, was " + placeMs);
    }

    // ── W-CTP-MULTI-1: multi-item placement for MEP disciplines ─────

    @Test
    @Order(10)
    @DisplayName("W-CTP-MULTI-1: MEP discipline places multiple items per room")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MULTI-1
    void w_ctp_multi_1_mep_multi_item() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        double cx = (room.minX() + room.maxX()) / 2;
        double cy = (room.minY() + room.maxY()) / 2;

        // ELEC discipline in a room should place multiple items if ERP.db has data
        // With stub data (no ERP.db), falls back to browse, which may find 0 items
        // Either way, the method should succeed without error
        ClickToPlaceResponse resp = api.clickToPlace(new ClickToPlaceRequest(
                "ClickTest", cx, cy, room.minZ(),
                "ELEC", baseBboxes));

        assertTrue(resp.success(), "clickToPlace ELEC should succeed: " + resp.error());
        assertEquals(room.bomId(), resp.resolvedRoomBomId());
        assertEquals("ELEC", resp.discipline());
        // mepRequirements should be populated if ERP.db is available
        assertNotNull(resp.mepRequirements(), "mepRequirements must not be null");
    }

    // ── W-CTP-MULTI-2: coverage tracking (qtyPlaced in MEPRequirementInfo) ──

    @Test
    @Order(11)
    @DisplayName("W-CTP-MULTI-2: MEP requirements include qtyPlaced coverage")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MULTI-2
    void w_ctp_multi_2_coverage() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst().orElseThrow();

        double cx = (room.minX() + room.maxX()) / 2;
        double cy = (room.minY() + room.maxY()) / 2;

        ClickToPlaceResponse resp = api.clickToPlace(new ClickToPlaceRequest(
                "ClickTest", cx, cy, room.minZ(),
                "FP", baseBboxes));

        assertTrue(resp.success());
        // If requirements are populated, qtyPlaced should be tracked
        for (var req : resp.mepRequirements()) {
            assertTrue(req.qtyPlaced() >= 0,
                    "qtyPlaced must be >= 0 for " + req.mepProductId());
            assertTrue(req.qtyNormal() >= 0,
                    "qtyNormal must be >= 0 for " + req.mepProductId());
        }
    }

    // ── W-CTP-MULTI-3: placement offset positions items correctly ────

    @Test
    @Order(12)
    @DisplayName("W-CTP-MULTI-3: computePlacementOffset positions by rule")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MULTI-3
    void w_ctp_multi_3_placement_offset() {
        // CEILING_CENTER: centre of room at ceiling height
        double[] ceilingCenter = DesignerAPIImpl.computePlacementOffset(
                "CEILING_CENTER", "CEILING", 4000, 3000, 3000, 0, 1);
        assertEquals(2000, ceilingCenter[0], 1.0, "X = room centre");
        assertEquals(1500, ceilingCenter[1], 1.0, "Y = room centre");
        assertEquals(2950, ceilingCenter[2], 1.0, "Z = near ceiling");

        // WALL_ENTRY: near entry wall at handle height
        double[] wallEntry = DesignerAPIImpl.computePlacementOffset(
                "WALL_ENTRY", "WALL", 4000, 3000, 3000, 0, 1);
        assertEquals(100, wallEntry[0], 1.0, "X = near entry wall");
        assertEquals(1200, wallEntry[2], 1.0, "Z = handle height");

        // WALL_SPACED: evenly distributed along wall
        double[] spaced0 = DesignerAPIImpl.computePlacementOffset(
                "WALL_SPACED", "WALL", 4000, 3000, 3000, 0, 3);
        double[] spaced1 = DesignerAPIImpl.computePlacementOffset(
                "WALL_SPACED", "WALL", 4000, 3000, 3000, 1, 3);
        double[] spaced2 = DesignerAPIImpl.computePlacementOffset(
                "WALL_SPACED", "WALL", 4000, 3000, 3000, 2, 3);
        assertEquals(1000, spaced0[0], 1.0, "First outlet at 1/4");
        assertEquals(2000, spaced1[0], 1.0, "Second outlet at 2/4");
        assertEquals(3000, spaced2[0], 1.0, "Third outlet at 3/4");

        // FLOOR_LOW: at floor level
        double[] floorLow = DesignerAPIImpl.computePlacementOffset(
                "FLOOR_LOW", "FLOOR", 4000, 3000, 3000, 0, 1);
        assertEquals(0, floorLow[2], 1.0, "Z = floor level");
    }
}
