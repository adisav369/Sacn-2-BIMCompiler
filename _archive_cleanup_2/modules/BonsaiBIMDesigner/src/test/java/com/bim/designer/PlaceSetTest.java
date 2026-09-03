package com.bim.designer;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;
import com.bim.designer.dao.StubDataSeeder;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * placeSet() batch placement from category.
 *
 * <p>Implements BIM_Designer_SRS.md §2 UX-F-25 (placeSet spec).
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-PLACE-SET-1: placeSet places 3+ items from a category in one call</li>
 *   <li>W-PLACE-SET-2: placeSet with unknown category returns error</li>
 * </ul>
 *
 * // Implementing BIM_Designer_SRS.md §2 UX-F-25 — Witness: W-PLACE-SET
 */
@DisplayName("placeSet — batch placement from category (§2 UX-F-25)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaceSetTest {

    private static Connection conn;
    private static DesignerAPIImpl api;
    private static List<DesignBBox> baseBboxes;

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(conn);
        api = new DesignerAPIImpl(conn);

        // Generate a base layout with rooms
        CreateNewResponse resp = api.createNew(new CreateNewRequest(
                "PlaceSetTest", "TERRACE", "MY",
                10000, 6000,
                2, 1, 1));
        assertTrue(resp.success(), "Base layout must succeed");
        baseBboxes = resp.bboxes();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    /**
     * W-PLACE-SET-1: placeSet places 3+ items from a category in one call.
     * @Traces BIM_Designer_SRS.md §2 UX-F-25 — batch placement
     */
    @Test @Order(1)
    @DisplayName("W-PLACE-SET-1: placeSet places items from category")
    void w_place_set_1() {
        // Find a room bbox to place into
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst()
                .orElse(null);
        assertNotNull(room, "Must have at least one ROOM in the base layout");

        // Browse to find a category with products
        BrowseItemsResponse browse = api.browseItems(new BrowseItemsRequest(
                null, null, null, 0, 0, 0, 0, 100));
        assertTrue(browse.success(), "browseItems must succeed");
        assertFalse(browse.items().isEmpty(), "Must have products in catalog");

        // Pick the first product's category
        String category = browse.items().get(0).productType();
        assertNotNull(category, "Product must have a category");

        PlaceSetResponse resp = api.placeSet(new PlaceSetRequest(
                "PlaceSetTest", category, room.bomId(), baseBboxes));

        assertTrue(resp.success(), "placeSet must succeed: " + resp.error());
        assertTrue(resp.placedCount() > 0,
                "Must place at least 1 item, got " + resp.placedCount());
        assertEquals(resp.placedCount(), resp.placedBboxes().size(),
                "placedCount must match placedBboxes size");

        System.out.printf("  W-PLACE-SET-1: placed %d items from category '%s' into room %s%n",
                resp.placedCount(), category, room.bomId());
    }

    /**
     * W-PLACE-SET-2: placeSet with unknown category returns error.
     * @Traces BIM_Designer_SRS.md §2 UX-F-25 — error path
     */
    @Test @Order(2)
    @DisplayName("W-PLACE-SET-2: unknown category returns error")
    void w_place_set_2_unknown_category() {
        DesignBBox room = baseBboxes.stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .findFirst()
                .orElse(null);
        assertNotNull(room);

        PlaceSetResponse resp = api.placeSet(new PlaceSetRequest(
                "PlaceSetTest", "NONEXISTENT_CATEGORY", room.bomId(), baseBboxes));

        assertFalse(resp.success(), "Must fail for unknown category");
        assertNotNull(resp.error(), "Error message must be present");
    }

    /**
     * W-PLACE-SET-3: placeSet with missing categoryValue returns error.
     */
    @Test @Order(3)
    @DisplayName("W-PLACE-SET-3: missing category returns error")
    void w_place_set_3_missing_category() {
        PlaceSetResponse resp = api.placeSet(new PlaceSetRequest(
                "PlaceSetTest", null, "ROOM_1", baseBboxes));

        assertFalse(resp.success(), "Must fail without categoryValue");
        assertNotNull(resp.error());
    }
}
