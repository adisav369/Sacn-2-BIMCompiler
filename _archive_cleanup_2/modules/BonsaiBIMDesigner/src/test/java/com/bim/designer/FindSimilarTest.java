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
 * Find Similar — JEPA-inspired embedding similarity search (§25.3).
 *
 * <p>Implements BIM_Designer_SRS.md §25.3 — Witness: W-EMB-*
 *
 * <p>Tests the findSimilar flow: cosine similarity of feature vectors,
 * fit status integration, result ranking, performance.
 * Uses StubDataSeeder with 14 products (13 active, 1 inactive).
 */
@DisplayName("Find Similar — Embedding Similarity (§25.3)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FindSimilarTest {

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
    @DisplayName("W-EMB-SIM-1: Find Similar on a door returns other doors, not walls")
    void w_emb_sim_1_doors_similar_to_doors() throws Exception {
        var results = dao.findSimilar("DOOR_INT_810", 5, 0, 0, 0);
        assertFalse(results.isEmpty(), "Should find similar products");

        // First result should be the other door (DOOR_EXT_1810)
        var first = results.get(0);
        assertEquals("DOOR", first.product().productType(),
                "Most similar to a DOOR should be another DOOR");
        assertEquals("DOOR_EXT_1810", first.product().productId());
        assertTrue(first.similarity() > 0.5, "Door-to-door similarity should be high");
    }

    @Test
    @Order(2)
    @DisplayName("W-EMB-SIM-2: Find Similar excludes the source product itself")
    void w_emb_sim_2_excludes_self() throws Exception {
        var results = dao.findSimilar("BED_QUEEN_1600", 20, 0, 0, 0);
        assertTrue(results.stream().noneMatch(
                r -> r.product().productId().equals("BED_QUEEN_1600")),
                "Source product must not appear in results");
    }

    @Test
    @Order(3)
    @DisplayName("W-EMB-SIM-3: Similar beds rank higher than dissimilar products")
    void w_emb_sim_3_beds_rank_high() throws Exception {
        var results = dao.findSimilar("BED_QUEEN_1600", 13, 0, 0, 0);

        // The other bed variants should be in the top results
        var topProductIds = results.subList(0, Math.min(4, results.size()))
                .stream().map(r -> r.product().productId()).toList();

        assertTrue(topProductIds.contains("BED_QUEEN_1500"),
                "BED_QUEEN_1500 should be in top-4 similar to BED_QUEEN_1600");
    }

    @Test
    @Order(4)
    @DisplayName("W-EMB-SIM-4: Similarity scores are between 0 and 1")
    void w_emb_sim_4_score_range() throws Exception {
        var results = dao.findSimilar("WINDOW_1810", 10, 0, 0, 0);
        for (var r : results) {
            assertTrue(r.similarity() >= 0 && r.similarity() <= 1.0,
                    "Similarity must be in [0, 1], was " + r.similarity());
        }
    }

    @Test
    @Order(5)
    @DisplayName("W-EMB-SIM-5: Results are sorted descending by similarity")
    void w_emb_sim_5_sorted() throws Exception {
        var results = dao.findSimilar("BED_QUEEN_1600", 10, 0, 0, 0);
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).similarity() >= results.get(i).similarity(),
                    "Results must be sorted descending by similarity");
        }
    }

    @Test
    @Order(6)
    @DisplayName("W-EMB-SIM-6: Unknown product returns empty list")
    void w_emb_sim_6_unknown_product() throws Exception {
        var results = dao.findSimilar("NONEXISTENT_PRODUCT", 10, 0, 0, 0);
        assertTrue(results.isEmpty(), "Unknown product should return empty list");
    }

    // ── API layer ───────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-EMB-FIT-1: Similarity results carry correct fit status badges")
    void w_emb_fit_1_fit_status() {
        var resp = api.findSimilar(new FindSimilarRequest(
                "BED_QUEEN_1600", CONT_W, CONT_D, CONT_H, 10));

        assertTrue(resp.success());
        assertFalse(resp.items().isEmpty());

        // Check that fit statuses are computed correctly
        for (var item : resp.items()) {
            assertNotNull(item.fitStatus(), "Every item must have a fitStatus");
            assertTrue(item.fitStatus().matches("FITS|TIGHT|TOO_WIDE|TOO_DEEP|TOO_TALL"),
                    "fitStatus must be a valid value, was: " + item.fitStatus());
        }

        // BED_SUPER_KING_3050 should be TIGHT (3050mm in 3100mm container)
        var superKing = resp.items().stream()
                .filter(i -> i.productId().equals("BED_SUPER_KING_3050")).findFirst();
        if (superKing.isPresent()) {
            assertEquals("TIGHT", superKing.get().fitStatus());
        }
    }

    @Test
    @Order(11)
    @DisplayName("W-EMB-SIM-7: API findSimilar returns similarity scores")
    void w_emb_sim_7_api_similarity_scores() {
        var resp = api.findSimilar(new FindSimilarRequest(
                "DOOR_INT_810", 0, 0, 0, 5));

        assertTrue(resp.success());
        assertEquals("DOOR_INT_810", resp.sourceProductId());

        // All items should have similarity > 0
        for (var item : resp.items()) {
            assertTrue(item.similarity() > 0,
                    "Similarity should be > 0, was " + item.similarity());
        }
    }

    @Test
    @Order(12)
    @DisplayName("W-EMB-SIM-8: API respects limit parameter")
    void w_emb_sim_8_limit() {
        var resp3 = api.findSimilar(new FindSimilarRequest(
                "BED_QUEEN_1600", 0, 0, 0, 3));
        assertTrue(resp3.success());
        assertEquals(3, resp3.items().size(), "limit=3 should return 3 items");

        var resp1 = api.findSimilar(new FindSimilarRequest(
                "BED_QUEEN_1600", 0, 0, 0, 1));
        assertTrue(resp1.success());
        assertEquals(1, resp1.items().size(), "limit=1 should return 1 item");
    }

    @Test
    @Order(20)
    @DisplayName("W-EMB-PERF-1: Full similarity search completes within 200ms")
    void w_emb_perf_1_latency() {
        long t0 = System.currentTimeMillis();
        var resp = api.findSimilar(new FindSimilarRequest(
                "BED_QUEEN_1600", CONT_W, CONT_D, CONT_H, 10));
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(resp.success());
        assertTrue(elapsed < 200, "findSimilar should complete in < 200ms, was " + elapsed + "ms");
    }

    @Test
    @Order(21)
    @DisplayName("W-EMB-DET-1: Same product → same similarity ranking across runs")
    void w_emb_det_1_deterministic() {
        var resp1 = api.findSimilar(new FindSimilarRequest(
                "WINDOW_1810", 0, 0, 0, 5));
        var resp2 = api.findSimilar(new FindSimilarRequest(
                "WINDOW_1810", 0, 0, 0, 5));

        assertTrue(resp1.success());
        assertTrue(resp2.success());
        assertEquals(resp1.items().size(), resp2.items().size());

        for (int i = 0; i < resp1.items().size(); i++) {
            assertEquals(resp1.items().get(i).productId(),
                    resp2.items().get(i).productId(),
                    "Same product order on repeated calls");
            assertEquals(resp1.items().get(i).similarity(),
                    resp2.items().get(i).similarity(), 0.0001,
                    "Same similarity scores on repeated calls");
        }
    }
}
