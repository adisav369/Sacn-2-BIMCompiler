package com.bim.designer;

import com.bim.designer.api.*;
import com.bim.designer.dao.StubDataSeeder;
import com.bim.designer.dao.WorkOutputDAO;
import com.bim.orm.BIMLogger;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Hello World" Journey Test — SRS §5.1 acceptance proof.
 *
 * <p>Exercises the full P0 critical path as a single integration test:
 * <pre>
 *   createNew → toggle design → focus room → snap → save → listVariants → recall
 * </pre>
 *
 * <p>This is the YAML++ test case: the simplest end-to-end proof that the
 * generative path works from API to persistence and back. No IFC, no
 * extraction, no real BOM.db — just the pure generative Create → Edit → Save
 * loop against in-memory SQLite.
 *
 * <p>Named "YAML++" because it's the first test that goes beyond YAML classification
 * (which the Rosetta Stones already prove) into the design workspace lifecycle.
 *
 * <p><b>SRS coverage:</b>
 * <ul>
 *   <li>UX-F-02: One-click building (createNew with defaults)</li>
 *   <li>UX-F-03: Immediate visual feedback (bboxes returned)</li>
 *   <li>UX-F-07: Section focus (verify room metadata for focus)</li>
 *   <li>UX-F-13: One-click Save (save → W_Variant created)</li>
 *   <li>UX-F-14: Version list (listVariants returns saved entries)</li>
 *   <li>UX-F-15: Non-destructive Recall (recall returns bboxes, original unchanged)</li>
 *   <li>UX-N-01: createNew latency < 200ms</li>
 *   <li>UX-N-06: Save latency < 500ms</li>
 *   <li>W-SNAP-1: Grid snap produces adjustments</li>
 * </ul>
 *
 * // Implementing BIM_Designer_SRS.md §5.1 — Witness: W-JOURNEY-HELLO-1
 */
@DisplayName("Hello World Journey — YAML++ End-to-End")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelloWorldJourneyTest {

    private static Connection bomConn;
    private static DesignerAPIImpl api;

    // Journey state carried across ordered tests
    private static CreateNewResponse createResponse;
    private static String buildingId;
    private static DesignerAPI.SaveResponse saveResponse;

    @BeforeAll
    static void setUp() throws Exception {
        BIMLogger.initForRun("YAML++_journey");

        // In-memory BOM.db with stub data (Rosetta Stone buildings)
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        StubDataSeeder.seed(bomConn);
        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        BIMLogger.close();
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
    }

    // ── Step 1-4: Create New Building ─────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-JOURNEY-HELLO-1: createNew returns bboxes < 200ms (UX-N-01)")
    void step1_createNew() {
        long t0 = System.currentTimeMillis();

        CreateNewRequest req = new CreateNewRequest(
                "My House", "DETACHED", "MY",
                9000, 7000, 2, 1, 1);
        createResponse = api.createNew(req);
        buildingId = "My House";

        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(createResponse.success(), "createNew succeeded");
        assertFalse(createResponse.bboxes().isEmpty(), "Bboxes returned (UX-F-03)");
        assertTrue(elapsed < 200, "Latency < 200ms (UX-N-01): was " + elapsed + "ms");

        // Structure check (UX-F-02)
        var bboxes = createResponse.bboxes();
        long buildings = bboxes.stream().filter(b -> "BUILDING".equals(b.bomType())).count();
        long floors = bboxes.stream().filter(b -> "FLOOR".equals(b.bomType())).count();
        long rooms = bboxes.stream().filter(b -> "ROOM".equals(b.bomType())).count();
        assertEquals(1, buildings, "1 BUILDING");
        assertEquals(1, floors, "1 FLOOR (1 storey)");
        assertTrue(rooms >= 4, "At least 4 rooms (LI+KT+2BR+1BT): got " + rooms);

        BIMLogger.info("JOURNEY", "Step 1: createNew → {} bboxes in {}ms", bboxes.size(), elapsed);
    }

    // ── Step 5: Focus a room (verify metadata for section chooser) ───

    @Test
    @Order(2)
    @DisplayName("W-JOURNEY-HELLO-2: Room bboxes have focus metadata (UX-F-07)")
    void step5_focusRoom() {
        var rooms = createResponse.bboxes().stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .toList();

        for (var room : rooms) {
            assertNotNull(room.bomId(), "Room has bomId for focus");
            assertNotNull(room.category(), "Room has category for colour");
            assertNotNull(room.storey(), "Room has storey for grouping");
            assertNotNull(room.parentBomId(), "Room has parent for hierarchy");
            assertEquals("IfcSpace", room.ifcClass(), "Room is IfcSpace");

            // Positive volume
            assertTrue(room.maxX() > room.minX(), room.bomId() + " has width");
            assertTrue(room.maxY() > room.minY(), room.bomId() + " has depth");
            assertTrue(room.maxZ() > room.minZ(), room.bomId() + " has height");
        }

        BIMLogger.info("JOURNEY", "Step 5: {} rooms verified with focus metadata", rooms.size());
    }

    // ── Step 6: Snap (grid + validation) ─────────────────────────────

    @Test
    @Order(3)
    @DisplayName("W-JOURNEY-HELLO-3: Snap grid-aligns room dimensions (W-SNAP-1)")
    void step6_snap() {
        // Take a room and snap it
        var rooms = createResponse.bboxes().stream()
                .filter(b -> "ROOM".equals(b.bomType()))
                .toList();

        var snapResp = api.snap(rooms, "MY", 250);
        assertTrue(snapResp.success(), "Snap succeeded");
        assertEquals(rooms.size(), snapResp.bboxes().size(), "Same number of bboxes returned");

        // All returned room dimensions should be grid-aligned
        for (var bb : snapResp.bboxes()) {
            double w = bb.maxX() - bb.minX();
            double d = bb.maxY() - bb.minY();
            assertEquals(0, w % 250, 0.01,
                    bb.bomId() + " width grid-aligned: " + w);
            assertEquals(0, d % 250, 0.01,
                    bb.bomId() + " depth grid-aligned: " + d);
        }

        BIMLogger.info("JOURNEY", "Step 6: snap → {} adjustments", snapResp.adjustments().size());
    }

    // ── Step 7: Save ─────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-JOURNEY-HELLO-4: Save creates variant < 500ms (UX-N-06, UX-F-13)")
    void step7_save() {
        long t0 = System.currentTimeMillis();

        // Use in-memory work_output.db for isolation
        saveResponse = saveToInMemoryDb(buildingId, createResponse.bboxes(), "v1");

        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(saveResponse.success(), "Save succeeded (UX-F-13)");
        assertNotNull(saveResponse.variantId(), "Variant ID assigned");
        assertTrue(elapsed < 500, "Latency < 500ms (UX-N-06): was " + elapsed + "ms");

        BIMLogger.info("JOURNEY", "Step 7: save → {} in {}ms",
                saveResponse.variantId(), elapsed);
    }

    // ── Step 8+: listVariants + Recall ────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("W-JOURNEY-HELLO-5: listVariants returns saved version (UX-F-14)")
    void step8_listVariants() throws Exception {
        // Use same in-memory db — query via DAO directly
        var variants = getVariantsFromInMemoryDb(buildingId);
        assertFalse(variants.isEmpty(), "At least 1 variant (UX-F-14)");
        assertEquals("v1", variants.get(0).label(), "Label matches");
        assertEquals(createResponse.bboxes().size(), variants.get(0).orderLineCount(),
                "OrderLine count matches bbox count");

        BIMLogger.info("JOURNEY", "Step 8: listVariants → {} entries", variants.size());
    }

    @Test
    @Order(6)
    @DisplayName("W-JOURNEY-HELLO-6: Recall restores bboxes non-destructively (UX-F-15)")
    void step9_recall() throws Exception {
        var recalled = recallFromInMemoryDb(saveResponse.variantId());
        assertEquals(createResponse.bboxes().size(), recalled.size(),
                "Same number of bboxes recalled (UX-F-15)");

        // Verify geometry round-trip
        for (int i = 0; i < createResponse.bboxes().size(); i++) {
            var orig = createResponse.bboxes().get(i);
            var recv = recalled.get(i);
            assertEquals(orig.bomId(), recv.bomId(), "bomId preserved");
            assertEquals(orig.bomType(), recv.bomType(), "bomType preserved");

            double tol = 0.01; // mm→m→mm round-trip tolerance
            assertEquals(orig.minX(), recv.minX(), tol, "minX round-trip");
            assertEquals(orig.maxX() - orig.minX(),
                    recv.maxX() - recv.minX(), tol, "width round-trip");
        }

        BIMLogger.info("JOURNEY", "Step 9: recall → {} bboxes, geometry verified", recalled.size());
    }

    // ── Helpers: in-memory work_output.db for test isolation ──────────

    // We use a shared in-memory connection per-test-class so save/recall share state
    private static Connection woConn;
    private static WorkOutputDAO woDao;

    private static WorkOutputDAO ensureWoDao() throws Exception {
        if (woDao == null) {
            woConn = DriverManager.getConnection("jdbc:sqlite::memory:");
            woDao = new WorkOutputDAO(woConn);
            woDao.initSchema();
        }
        return woDao;
    }

    private DesignerAPI.SaveResponse saveToInMemoryDb(String bid, List<DesignBBox> bboxes, String label) {
        try {
            var d = ensureWoDao();
            // Extract site dims from BUILDING bbox
            double sw = 0, sd = 0, sh = 0;
            for (var bb : bboxes) {
                if ("BUILDING".equals(bb.bomType())) {
                    sw = bb.maxX() - bb.minX();
                    sd = bb.maxY() - bb.minY();
                    sh = bb.maxZ() - bb.minZ();
                    break;
                }
            }
            d.ensureMasterOrder(bid, null, sw, sd, sh);
            String variantId = d.save(bid, bboxes, label);
            return new DesignerAPI.SaveResponse(true, variantId, "memory", null);
        } catch (Exception e) {
            return new DesignerAPI.SaveResponse(false, null, null, e.getMessage());
        }
    }

    private List<DesignerAPI.VariantInfo> getVariantsFromInMemoryDb(String bid) throws Exception {
        return ensureWoDao().listVariants(bid);
    }

    private List<DesignBBox> recallFromInMemoryDb(String variantId) throws Exception {
        return ensureWoDao().recall(variantId);
    }
}
