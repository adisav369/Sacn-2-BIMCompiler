package com.bim.designer;

import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;
import com.bim.designer.dao.DesignerDAO;
import com.bim.designer.dao.StubDataSeeder;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOM Chooser — search-first product browser with container fit check.
 *
 * <p>Implements BIM_Designer.md §17.18 — Witness: W-BROWSE-*
 *
 * <p>Tests the browseItems flow: DAO query, fit status computation,
 * category counts, pagination, building-type filtering.
 * Uses StubDataSeeder with 14 products (13 active, 1 inactive).
 */
@DisplayName("BOM Chooser — Browse Items (§17.18)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrowseItemsTest {

    private static Connection conn;
    private static DesignerAPIImpl api;
    private static DesignerDAO dao;

    // Bedroom container: 3100x3100x3000mm
    private static final double CONT_W = 3100;
    private static final double CONT_D = 3100;
    private static final double CONT_H = 3000;

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(conn);
        api = new DesignerAPIImpl(conn);
        dao = new DesignerDAO(conn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── DAO layer ───────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-BROWSE-1: DAO returns active products only (inactive filtered)")
    void w_browse_1_active_only() throws Exception {
        var all = dao.browseProducts(null, null, null, 0, 100);
        // 13 active products seeded (1 inactive DELETED_ITEM excluded)
        assertEquals(13, all.size(), "Expected 13 active products");
        assertTrue(all.stream().noneMatch(r -> r.productId().equals("DELETED_ITEM")),
                "Inactive product must not appear");
    }

    @Test
    @Order(2)
    @DisplayName("W-BROWSE-2: DAO LIKE search matches partial product names")
    void w_browse_2_search() throws Exception {
        var beds = dao.browseProducts("BED", null, null, 0, 100);
        assertEquals(4, beds.size(), "4 products contain 'BED': QUEEN×2, KING, SUPER_KING");

        var queens = dao.browseProducts("QUEEN", null, null, 0, 100);
        assertEquals(2, queens.size(), "2 products contain 'QUEEN'");
    }

    @Test
    @Order(3)
    @DisplayName("W-BROWSE-3: DAO category filter narrows by product_type")
    void w_browse_3_category_filter() throws Exception {
        var doors = dao.browseProducts(null, "DOOR", null, 0, 100);
        assertEquals(2, doors.size(), "2 DOOR products");

        var windows = dao.browseProducts(null, "WINDOW", null, 0, 100);
        assertEquals(2, windows.size(), "2 WINDOW products");
    }

    @Test
    @Order(4)
    @DisplayName("W-BROWSE-4: DAO building_type filter separates SH from TE")
    void w_browse_4_building_filter() throws Exception {
        var sh = dao.browseProducts(null, null, "Ifc4_SampleHouse", 0, 100);
        assertEquals(12, sh.size(), "12 active SH products");

        var te = dao.browseProducts(null, null, "Terminal_KLIA", 0, 100);
        assertEquals(1, te.size(), "1 TE product (sprinkler)");
    }

    @Test
    @Order(5)
    @DisplayName("W-BROWSE-5: DAO dimensions converted from m to mm")
    void w_browse_5_dimensions_mm() throws Exception {
        var beds = dao.browseProducts("BED_QUEEN_1600", null, null, 0, 1);
        assertEquals(1, beds.size());
        var bed = beds.get(0);
        assertEquals(1600, bed.widthMm(), 0.1, "Width 1.6m = 1600mm");
        assertEquals(2100, bed.depthMm(), 0.1, "Depth 2.1m = 2100mm");
        assertEquals(500, bed.heightMm(), 0.1, "Height 0.5m = 500mm");
    }

    @Test
    @Order(6)
    @DisplayName("W-BROWSE-6: DAO pagination (offset + limit)")
    void w_browse_6_pagination() throws Exception {
        var page1 = dao.browseProducts(null, null, "Ifc4_SampleHouse", 0, 5);
        assertEquals(5, page1.size(), "Page 1: 5 items");

        var page2 = dao.browseProducts(null, null, "Ifc4_SampleHouse", 5, 5);
        assertEquals(5, page2.size(), "Page 2: 5 items");

        var page3 = dao.browseProducts(null, null, "Ifc4_SampleHouse", 10, 5);
        assertEquals(2, page3.size(), "Page 3: 2 remaining items");

        int total = dao.countProducts(null, null, "Ifc4_SampleHouse");
        assertEquals(12, total, "Total count matches");
    }

    // ── Fit check logic ─────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-BROWSE-7: Fit status — FITS / TIGHT / TOO_WIDE / TOO_DEEP / TOO_TALL")
    void w_browse_7_fit_status() {
        // FITS: 1600x2100x500 in 3100x3100x3000 (clearance >= 100mm on all axes)
        assertEquals("FITS", DesignerAPIImpl.computeFitStatus(
                1600, 2100, 500, CONT_W, CONT_D, CONT_H));

        // TIGHT: 3050x2100x500 in 3100x3100x3000 (width clearance = 50mm < 100mm)
        assertEquals("TIGHT", DesignerAPIImpl.computeFitStatus(
                3050, 2100, 500, CONT_W, CONT_D, CONT_H));

        // TOO_WIDE: 4000x2500x900 in 3100x3100x3000
        assertEquals("TOO_WIDE", DesignerAPIImpl.computeFitStatus(
                4000, 2500, 900, CONT_W, CONT_D, CONT_H));

        // TOO_DEEP: 2000x4000x500 in 3100x3100x3000
        assertEquals("TOO_DEEP", DesignerAPIImpl.computeFitStatus(
                2000, 4000, 500, CONT_W, CONT_D, CONT_H));

        // TOO_TALL: 1000x1000x3500 in 3100x3100x3000
        assertEquals("TOO_TALL", DesignerAPIImpl.computeFitStatus(
                1000, 1000, 3500, CONT_W, CONT_D, CONT_H));

        // No container → always FITS
        assertEquals("FITS", DesignerAPIImpl.computeFitStatus(
                9999, 9999, 9999, 0, 0, 0));
    }

    // ── API layer (full browseItems) ────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-BROWSE-8: API browseItems returns items with fit status")
    void w_browse_8_api_browse() {
        var resp = api.browseItems(new BrowseItemsRequest(
                "BED", null, "Ifc4_SampleHouse",
                CONT_W, CONT_D, CONT_H, 0, 20));

        assertTrue(resp.success());
        assertEquals(4, resp.items().size(), "4 BED items");
        assertEquals(4, resp.totalCount(), "totalCount = 4");

        // Check fit statuses
        var queen1600 = resp.items().stream()
                .filter(i -> i.productId().equals("BED_QUEEN_1600")).findFirst().orElseThrow();
        assertEquals("FITS", queen1600.fitStatus());

        var superKing = resp.items().stream()
                .filter(i -> i.productId().equals("BED_SUPER_KING_3050")).findFirst().orElseThrow();
        assertEquals("TIGHT", superKing.fitStatus());
    }

    @Test
    @Order(21)
    @DisplayName("W-BROWSE-9: API browseItems returns category counts with fitsCount")
    void w_browse_9_category_counts() {
        var resp = api.browseItems(new BrowseItemsRequest(
                null, null, "Ifc4_SampleHouse",
                CONT_W, CONT_D, CONT_H, 0, 20));

        assertTrue(resp.success());
        assertFalse(resp.categories().isEmpty(), "Should have categories");

        // ELEMENT category: 7 total, some don't fit
        var elementCat = resp.categories().stream()
                .filter(c -> c.name().equals("ELEMENT")).findFirst().orElseThrow();
        assertEquals(7, elementCat.count(), "7 ELEMENT products for SH");
        assertTrue(elementCat.fitsCount() < elementCat.count(),
                "fitsCount < count (SOFA_SECTIONAL_4000 doesn't fit)");
    }

    @Test
    @Order(22)
    @DisplayName("W-BROWSE-10: API browseItems search < 100ms on stub data")
    void w_browse_10_latency() {
        long t0 = System.currentTimeMillis();
        var resp = api.browseItems(new BrowseItemsRequest(
                "bed", null, null, CONT_W, CONT_D, CONT_H, 0, 20));
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(resp.success());
        assertTrue(elapsed < 100, "Browse should complete in < 100ms, was " + elapsed + "ms");
    }

    @Test
    @Order(23)
    @DisplayName("W-BROWSE-11: Empty search returns all products for building")
    void w_browse_11_empty_search() {
        var resp = api.browseItems(new BrowseItemsRequest(
                null, null, "Ifc4_SampleHouse",
                0, 0, 0, 0, 20));

        assertTrue(resp.success());
        assertEquals(12, resp.totalCount(), "12 active SH products");
        // No container → all FITS
        assertTrue(resp.items().stream().allMatch(i -> "FITS".equals(i.fitStatus())),
                "No container → all items FITS");
    }
}
