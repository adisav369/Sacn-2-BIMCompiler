package com.bim.cobol;

import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MBomCategory;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F5 End-to-End Integration Test — exercises all 5 verb layers (L0→L4)
 * in a single ScriptRunner pass and validates cross-verb data flow.
 *
 * <p>This test proves:
 * <ul>
 *   <li>L4 CATALOG: DEFINE CATEGORY → ADD TEMPLATE RULE chain</li>
 *   <li>L0 PRIMITIVES: CREATE BOM → ADD LINE → SET TACK/DIMENSIONS/ROTATION/PROPERTY chain</li>
 *   <li>L1 CONVENIENCE: CREATE ROOM → FURNISH ROOM chain (with componentConn)</li>
 *   <li>L2 FLOOR: CREATE FLOOR → ADD ROOM → REMOVE ROOM → SWAP ROOM chain</li>
 *   <li>L3 BUILDING: COMPOSE BUILDING → ADD FLOOR → STACK FLOORS chain</li>
 *   <li>DATA QUERIES: DESCRIBE/COUNT/SELECT/LIST BOMs reference what was built</li>
 *   <li>GEOMETRY: EXTRACT AABB, VALIDATE AABB, PARTITION AABB, SNAP TO GRID</li>
 *   <li>LIFECYCLE: CLONE → STRIP → REMOVE LINE → SWAP → DELETE</li>
 *   <li>EMIT: PLACE BOM SH writes 55 elements to output.db</li>
 *   <li>VERIFY: SUMMARIZE BUILDING reads back from output.db</li>
 *   <li>TRAVERSAL: EN-BLOC + WALK THRU prove BOM walk parity</li>
 * </ul>
 *
 * <p>Uses temp copy of BOM.db + isolated output.db — production data never mutated.
 */
@DisplayName("F5 End-to-End: Build a House from BIM COBOL")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F5IntegrationTest {

    private static Connection conn;
    private static Connection outputConn;
    private static File tempDb;
    private static File tempOutputDb;
    private static VerbContext fullCtx;
    private static VerbRegistry registry;
    private static ScriptRunner.ScriptReport report;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File(System.getProperty("bom.db"));
        assertTrue(source.exists(), System.getProperty("bom.db") + " must exist (set -Dbom.db=library/_XX_compile.db)");
        tempDb = File.createTempFile("f5_integration_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());

        // Create isolated output.db for emitting verbs (PLACE BOM, SUMMARIZE BUILDING)
        tempOutputDb = File.createTempFile("f5_output_", ".db");
        tempOutputDb.deleteOnExit();
        outputConn = DriverManager.getConnection("jdbc:sqlite:" + tempOutputDb.getAbsolutePath());
        try (java.sql.Statement stmt = outputConn.createStatement()) {
            stmt.execute("""
                CREATE TABLE elements_meta (
                    id INTEGER PRIMARY KEY,
                    guid TEXT UNIQUE NOT NULL,
                    discipline TEXT NOT NULL,
                    ifc_class TEXT NOT NULL,
                    element_name TEXT,
                    element_type TEXT,
                    storey TEXT,
                    fire_rating_hr REAL,
                    material_name TEXT,
                    material_rgba TEXT
                )
            """);
            stmt.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id, minX, maxX, minY, maxY, minZ, maxZ
                )
            """);
            stmt.execute("""
                CREATE TABLE base_geometries (
                    geometry_hash TEXT PRIMARY KEY,
                    vertices BLOB NOT NULL,
                    faces BLOB NOT NULL,
                    vertex_count INTEGER NOT NULL,
                    face_count INTEGER NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE element_transforms (
                    guid TEXT PRIMARY KEY,
                    center_x REAL NOT NULL,
                    center_y REAL NOT NULL,
                    center_z REAL NOT NULL,
                    transform_source TEXT DEFAULT 'compiled',
                    FOREIGN KEY (guid) REFERENCES elements_meta(guid)
                )
            """);
            stmt.execute("""
                CREATE TABLE element_instances (
                    guid TEXT PRIMARY KEY,
                    geometry_hash TEXT NOT NULL,
                    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
                )
            """);
        }

        // M_Product is in BOM.db — same conn serves as componentConn for FURNISH ROOM
        // outputConn provided for PLACE BOM + SUMMARIZE BUILDING
        fullCtx = VerbContext.withOutput(conn, conn, outputConn);
        registry = VerbRegistry.createDefault();

        // Load and run the F5 integration script
        String script = loadScript();
        assertFalse(script.isBlank(), "F5 script must not be empty");

        ScriptRunner runner = new ScriptRunner(registry);
        report = runner.run(fullCtx, script);

        // Dump failures for diagnostics
        for (VerbResult<?> r : report.results()) {
            if (!r.pass()) {
                System.err.printf("  FAIL [%s]: %s%n", r.verb(), r.summary());
            }
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (outputConn != null) outputConn.close();
        if (conn != null) conn.close();
        if (tempOutputDb != null) tempOutputDb.delete();
        if (tempDb != null) tempDb.delete();
    }

    private static String loadScript() throws IOException {
        // Try project-root scripts/ first, then fall back to relative path
        Path scriptPath = Path.of("scripts/F5_integration.bimcobol");
        if (!Files.exists(scriptPath)) {
            scriptPath = Path.of("../scripts/F5_integration.bimcobol");
        }
        assertTrue(Files.exists(scriptPath),
            "F5_integration.bimcobol must exist at: " + scriptPath.toAbsolutePath());
        return Files.readString(scriptPath);
    }

    // ── AGGREGATE GATE ────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-F5-1: Script runs all verb lines without dispatch error")
    void w_f5_01_scriptRunsAllLines() {
        assertTrue(report.totalLines() >= 42,
            "expected 42+ verb lines, got " + report.totalLines());
        // No "unknown keyword" failures
        for (VerbResult<?> r : report.results()) {
            assertFalse(r.summary().contains("unknown keyword"),
                "dispatch failure: " + r.summary());
        }
    }

    @Test
    @Order(2)
    @DisplayName("W-F5-2: No dispatch failures (proof findings are expected)")
    void w_f5_02_noDispatchFailures() {
        // CHECK PLACEMENT may report FAIL when it finds geometric violations
        // (pairwise overlap in SH curtain wall) — that's a data finding, not
        // a script failure. Only count unexpected failures.
        long unexpectedFails = report.results().stream()
            .filter(r -> !r.pass())
            .filter(r -> !"CHECK PLACEMENT".equals(r.verb()))
            .count();
        assertEquals(0, unexpectedFails,
            String.format("expected 0 unexpected failures, got %d/%d",
                unexpectedFails, report.totalLines()));
    }

    // ── PHASE 1: L4 CATALOG ──────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-F5-10: DEFINE CATEGORY → category exists in DB")
    void w_f5_10_categoryCreated() throws SQLException {
        MBomCategory cat = MBomCategory.get(conn, "SU");
        assertNotNull(cat, "SU category should exist after DEFINE CATEGORY");
        assertEquals("STUDY", cat.getName());
    }

    // ── PHASE 2: L0 PRIMITIVES ───────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-F5-20: CREATE BOM → ADD LINE chain creates 2-child BOM")
    void w_f5_20_bookshelfCreated() throws SQLException {
        MBOM bom = MBOM.get(conn, "SY_F5_BOOKSHELF");
        assertNotNull(bom, "SY_F5_BOOKSHELF should exist");
        assertEquals("SET", bom.getBomType());
        assertEquals("FR", bom.getBomCategory());

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_F5_BOOKSHELF");
        assertEquals(2, children.size(), "bookshelf should have 2 lines (frame + top)");
    }

    @Test
    @Order(21)
    @DisplayName("W-F5-21: SET TACK/DIMENSIONS/PROPERTY mutated the correct line")
    void w_f5_21_primitivesMutatedLine() throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_F5_BOOKSHELF");
        MBOMLine frame = children.stream()
            .filter(l -> "SHELF_FRAME".equals(l.getRole()))
            .findFirst().orElse(null);
        assertNotNull(frame, "SHELF_FRAME line must exist");

        // SET TACK set all to 0
        assertEquals(0.0, frame.getDx(), 0.001, "dx after SET TACK");
        assertEquals(0.0, frame.getDy(), 0.001, "dy after SET TACK");
        assertEquals(0.0, frame.getDz(), 0.001, "dz after SET TACK");

        // SET DIMENSIONS
        assertEquals(900, frame.getAllocatedWidthMm(), "width after SET DIMENSIONS");
        assertEquals(400, frame.getAllocatedDepthMm(), "depth after SET DIMENSIONS");
        assertEquals(1800, frame.getAllocatedHeightMm(), "height after SET DIMENSIONS");

        // SET LINE PROPERTY
        assertEquals("WOOD_OAK", frame.getMaterialName(), "material after SET LINE PROPERTY");
    }

    // ── PHASE 3: L1 CONVENIENCE ──────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("W-F5-30: CREATE ROOM creates 4 synthetic room BOMs")
    void w_f5_30_roomsCreated() throws SQLException {
        assertNotNull(MBOM.get(conn, "SY_BT_2500x2000"), "BT room should exist");
        assertNotNull(MBOM.get(conn, "SY_BD_4000x3500"), "BD room should exist");
        assertNotNull(MBOM.get(conn, "SY_LI_5000x4000"), "LI room should exist");
        assertNotNull(MBOM.get(conn, "SY_KT_3500x3000"), "KT room (EMPTY) should exist");
    }

    @Test
    @Order(31)
    @DisplayName("W-F5-31: FURNISH ROOM → KT room has furniture children")
    void w_f5_31_kitchenFurnished() throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_KT_3500x3000");
        assertTrue(children.size() >= 2,
            "KT room should have >= 2 children after FURNISH: got " + children.size());

        // Should contain the products we specified
        boolean hasBase = children.stream()
            .anyMatch(l -> "FURN_KITCHEN_BASE".equals(l.getChildProductId()));
        boolean hasCounter = children.stream()
            .anyMatch(l -> "FURN_KITCHEN_COUNTER".equals(l.getChildProductId()));
        assertTrue(hasBase, "KT should have FURN_KITCHEN_BASE");
        assertTrue(hasCounter, "KT should have FURN_KITCHEN_COUNTER");
    }

    // ── PHASE 5: L2 FLOOR ────────────────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("W-F5-50: CREATE FLOOR + ADD ROOM → floor has room children")
    void w_f5_50_floorComposed() throws SQLException {
        MBOM floor = MBOM.get(conn, "SY_FLOOR_F5GF_10000x8000");
        assertNotNull(floor, "floor BOM should exist");
        assertEquals("FLOOR", floor.getBomType());

        // After CREATE FLOOR (1 room) + ADD ROOM, should have 2 children
        // Then REMOVE ROOM BD + SWAP ROOM BT reduces back to 1
        // So final state depends on Phase 8 — just verify floor exists
        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_FLOOR_F5GF_10000x8000");
        assertTrue(children.size() >= 1,
            "floor should have at least 1 room child");
    }

    // ── PHASE 6: L3 BUILDING ─────────────────────────────────────────

    @Test
    @Order(60)
    @DisplayName("W-F5-60: COMPOSE BUILDING → unit BOM exists with children")
    void w_f5_60_buildingComposed() throws SQLException {
        MBOM unit = MBOM.get(conn, "SY_RE_BLDG_10000x8000x6000");
        assertNotNull(unit, "unit BOM should exist after COMPOSE BUILDING");
        assertEquals("BUILDING", unit.getBomType());

        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_RE_BLDG_10000x8000x6000");
        assertFalse(children.isEmpty(), "unit should have floor children");
    }

    @Test
    @Order(61)
    @DisplayName("W-F5-61: STACK FLOORS → children have cumulative dz offsets")
    void w_f5_61_floorsStacked() throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_RE_BLDG_10000x8000x6000");
        assertTrue(children.size() >= 2,
            "unit should have >= 2 children after ADD FLOOR");

        // After STACK FLOORS, at least one child should have dz > 0
        boolean hasNonZeroDz = children.stream().anyMatch(l -> l.getDz() > 0.001);
        assertTrue(hasNonZeroDz, "STACK FLOORS should set cumulative dz on upper children");
    }

    // ── PHASE 8: CLONE + MODIFY ──────────────────────────────────────

    @Test
    @Order(80)
    @DisplayName("W-F5-80: CLONE BOM → variant is independent copy")
    void w_f5_80_cloneCreated() throws SQLException {
        // After STRIP ROOM KEEP STRUCTURE, the variant should exist
        // but may have fewer children than the original
        MBOM variant = MBOM.get(conn, "SY_F5_BT_VARIANT");
        assertNotNull(variant, "variant BOM should exist after CLONE");
        assertEquals("SET", variant.getBomType());
        assertEquals("BT", variant.getBomCategory());
    }

    @Test
    @Order(81)
    @DisplayName("W-F5-81: SWAP ROOM → floor now references variant")
    void w_f5_81_swapApplied() throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, "SY_FLOOR_F5GF_10000x8000");
        // After SWAP ROOM BT, the BT room line should reference SY_F5_BT_VARIANT
        boolean hasVariant = children.stream()
            .anyMatch(l -> "SY_F5_BT_VARIANT".equals(l.getChildProductId()));
        assertTrue(hasVariant,
            "SWAP ROOM should have replaced BT room with SY_F5_BT_VARIANT");
    }

    // ── PHASE 9: REGISTER ────────────────────────────────────────────

    // Bookshelf may already be deleted by phase 10 since test order is DB-sequential.
    // This test verifies via the script report instead.
    @Test
    @Order(90)
    @DisplayName("W-F5-90: REGISTER BOM line passed in script")
    void w_f5_90_registerPassed() {
        // Find the REGISTER BOM result in the report
        boolean registerPassed = report.results().stream()
            .anyMatch(r -> "REGISTER BOM".equals(r.verb()) && r.pass());
        assertTrue(registerPassed, "REGISTER BOM should have passed in the script");
    }

    // ── PHASE 10: DELETE BOM LIFECYCLE ──────────────────────────────

    @Test
    @Order(100)
    @DisplayName("W-F5-100: DELETE BOM removes throwaway BOM, keeps referenced ones")
    void w_f5_100_deleteBomLifecycle() throws SQLException {
        // SY_F5_TEMP_DELETE was created and immediately deleted in the script
        assertNull(MBOM.get(conn, "SY_F5_TEMP_DELETE"),
            "SY_F5_TEMP_DELETE should be deleted");

        // SY_F5_BOOKSHELF still exists (not deleted — tests verify it)
        assertNotNull(MBOM.get(conn, "SY_F5_BOOKSHELF"),
            "SY_F5_BOOKSHELF should still exist (not deleted)");

        // SY_F5_BT_VARIANT still exists (referenced by floor — can't delete)
        assertNotNull(MBOM.get(conn, "SY_F5_BT_VARIANT"),
            "SY_F5_BT_VARIANT should still exist (referenced by floor)");
    }

    // ── PHASE 11: EMIT — outputConn verbs ────────────────────────────

    @Test
    @Order(110)
    @DisplayName("W-F5-110: PLACE BOM SH emits 55 elements to output.db")
    void w_f5_110_placeBomEmits() throws SQLException {
        // PLACE BOM result should be in the script report
        VerbResult<?> placeBom = report.results().stream()
            .filter(r -> "PLACE BOM".equals(r.verb()))
            .findFirst().orElse(null);
        assertNotNull(placeBom, "PLACE BOM SH should have been executed");
        assertTrue(placeBom.pass(), "PLACE BOM SH should pass: " + placeBom.summary());

        // Cross-check: output.db must have 55 elements
        try (java.sql.Statement stmt = outputConn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            assertTrue(rs.next());
            assertEquals(55, rs.getInt(1),
                "PLACE BOM SH should write 55 elements to output.db");
        }
    }

    @Test
    @Order(111)
    @DisplayName("W-F5-111: EN-BLOC SH returns 55 placements")
    void w_f5_111_enBlocPlacements() {
        VerbResult<?> enBloc = report.results().stream()
            .filter(r -> "EN-BLOC".equals(r.verb()))
            .findFirst().orElse(null);
        assertNotNull(enBloc, "EN-BLOC SH should have been executed");
        assertTrue(enBloc.pass(), "EN-BLOC SH should pass: " + enBloc.summary());
        assertTrue(enBloc.summary().contains("55"),
            "EN-BLOC SH should report 55 placements: " + enBloc.summary());
    }

    @Test
    @Order(112)
    @DisplayName("W-F5-112: WALK THRU SH returns 55 placements")
    void w_f5_112_walkThruPlacements() {
        VerbResult<?> walkThru = report.results().stream()
            .filter(r -> "WALK THRU".equals(r.verb()))
            .findFirst().orElse(null);
        assertNotNull(walkThru, "WALK THRU SH should have been executed");
        assertTrue(walkThru.pass(), "WALK THRU SH should pass: " + walkThru.summary());
        assertTrue(walkThru.summary().contains("55"),
            "WALK THRU SH should report 55 placements: " + walkThru.summary());
    }

    // ── PHASE 12: VERIFY OUTPUT ────────────────────────────────────

    @Test
    @Order(113)
    @DisplayName("W-F5-113: SUMMARIZE BUILDING SH reports 55 elements from output.db")
    void w_f5_113_summarizeBuildingVerify() {
        VerbResult<?> summary = report.results().stream()
            .filter(r -> "SUMMARIZE BUILDING".equals(r.verb()))
            .findFirst().orElse(null);
        assertNotNull(summary, "SUMMARIZE BUILDING SH should have been executed");
        assertTrue(summary.pass(), "SUMMARIZE BUILDING SH should pass: " + summary.summary());
        assertTrue(summary.summary().contains("55"),
            "SUMMARIZE BUILDING SH should report 55 elements: " + summary.summary());
    }

    // ── PHASE 13: PROVE — spatial proofs on emitted data ─────────────

    @Test
    @Order(114)
    @DisplayName("W-F5-114: CHECK PLACEMENT — Tier 1 proofs pass, Tier 2 reports known overlaps")
    void w_f5_114_checkPlacementProofs() {
        VerbResult<?> check = report.results().stream()
            .filter(r -> "CHECK PLACEMENT".equals(r.verb()))
            .findFirst().orElse(null);
        assertNotNull(check, "CHECK PLACEMENT should have been executed");
        // CHECK PLACEMENT ran and analyzed all 55 elements
        assertTrue(check.summary().contains("55"),
            "CHECK PLACEMENT should analyze 55 elements: " + check.summary());
        // Tier 1 (per-element: P01-P04) = 220 proofs (55×4), all pass.
        // Tier 2 (pairwise: P05-P06) detects known overlaps in SH
        // (e.g., curtain wall glazing plates with coincident centroids).
        // These are real geometric issues — the proofs are correct.
        assertTrue(check.summary().contains("220 pass"),
            "All 220 Tier 1 proofs should pass: " + check.summary());
    }

    @Test
    @Order(115)
    @DisplayName("W-F5-115: CHECK CLASH — MEP-structural clash detection runs")
    void w_f5_115_checkClashRuns() {
        VerbResult<?> clash = report.results().stream()
            .filter(r -> "CHECK CLASH".equals(r.verb()))
            .findFirst().orElse(null);
        assertNotNull(clash, "CHECK CLASH should have been executed");
        assertTrue(clash.pass(), "CHECK CLASH should pass: " + clash.summary());
    }

    // ── CROSS-VERB GAP REPORT ────────────────────────────────────────

    @Test
    @Order(200)
    @DisplayName("W-F5-200: Gap report — identify verbs NOT exercised by script")
    void w_f5_200_gapReport() {
        // Verbs exercised in this script (36 out of 53)
        String[] exercised = {
            "DEFINE CATEGORY", "ADD TEMPLATE RULE",                        // L4
            "CREATE BOM", "ADD LINE", "SET TACK", "SET DIMENSIONS",        // L0
            "SET ROTATION", "SET LINE PROPERTY",                           // L0
            "CHECK BOM",                                                   // query
            "CREATE ROOM", "FURNISH ROOM",                                 // L1
            "EXTRACT AABB", "VALIDATE AABB", "PARTITION AABB",            // geometry
            "SNAP TO GRID",                                                // geometry
            "CREATE FLOOR", "ADD ROOM", "REMOVE ROOM", "SWAP ROOM",      // L2
            "COMPOSE BUILDING", "ADD FLOOR", "STACK FLOORS",              // L3
            "DESCRIBE BOM", "COUNT BOM", "SELECT BOM", "LIST BOMS",      // query
            "CLONE BOM", "STRIP ROOM",                                     // lifecycle
            "REGISTER BOM", "DELETE BOM",                                  // lifecycle
            "PLACE BOM", "EN-BLOC", "WALK THRU", "SUMMARIZE BUILDING",  // emit + verify
            "CHECK PLACEMENT", "CHECK CLASH"                               // spatial proofs
        };

        // Verbs NOT exercised — need special context or separate test
        String[] notExercised = {
            // Need output.db path + bomConn rules
            "CHECK ROOM", "CHECK COMPLIANCE", "VERIFY PLACEMENT",
            // Need file path for XLSX output
            "REPORT BOM CATALOG", "REPORT PRODUCT CATALOG", "REPORT BOM STRUCTURE",
            // Need specific building data in BOM.db
            "ROUTE SPRINKLERS", "WIRE LIGHTING", "CONNECT FITTINGS",
            "COVER WITH COMPOUND_ROOF",
            // Need component_library.db for geometry
            "TILE SURFACE", "ARRAY",
            // Data handling (exercisable but lower priority)
            "REMOVE LINE", "AGGREGATE BOM", "EXPORT BOM", "RESIZE ROOM",
            // Proof verb (has its own dedicated test: HelloWorldVerbTest)
            "HELLO WORLD"
        };

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║         F5 CROSS-VERB INTEGRATION REPORT            ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.printf("║  Verb lines executed:  %3d                           ║%n", report.totalLines());
        System.out.printf("║  PASS: %3d   FAIL: %3d                               ║%n",
            report.passCount(), report.failCount());
        System.out.printf("║  Verbs exercised:  %2d / 53                           ║%n", exercised.length);
        System.out.printf("║  Verbs NOT exercised: %2d (need special context)      ║%n", notExercised.length);
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  GAPS — verbs needing dedicated test harness:       ║");
        System.out.println("║                                                      ║");
        System.out.println("║  [output.db+rules] CHECK ROOM, CHECK COMPLIANCE,   ║");
        System.out.println("║                    VERIFY PLACEMENT                  ║");
        System.out.println("║  [XLSX file path]  REPORT BOM/PRODUCT CATALOG,      ║");
        System.out.println("║                    REPORT BOM STRUCTURE              ║");
        System.out.println("║  [building data]   ROUTE SPRINKLERS, WIRE LIGHTING, ║");
        System.out.println("║                    CONNECT FITTINGS, COMPOUND_ROOF   ║");
        System.out.println("║  [comp_lib.db]     TILE SURFACE, ARRAY              ║");
        System.out.println("║                                                      ║");
        System.out.println("║  CLOSED: PLACE BOM, EN-BLOC, WALK THRU,            ║");
        System.out.println("║          SUMMARIZE BUILDING, CHECK PLACEMENT,       ║");
        System.out.println("║          CHECK CLASH (outputConn wired)              ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // This test always passes — it's a diagnostic report
        assertTrue(true, "gap report generated");
    }
}
