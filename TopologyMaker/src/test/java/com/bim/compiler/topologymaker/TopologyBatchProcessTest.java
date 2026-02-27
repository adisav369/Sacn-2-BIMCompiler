package com.bim.compiler.topologymaker;

import com.bim.compiler.topologymaker.db.TopologyAccessLayer;
import com.bim.compiler.topologymaker.db.TopologyWriter;
import com.bim.ormsandbox.po.BomTemplateContract;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TopologyBatchProcess.
 *
 * <p>Uses a temporary copy of the library DB so that each test run is isolated
 * and the canonical library DB is never modified by the test suite.
 *
 * <p>Applies migration_topology_maker_bootstrap.sql to the test DB copy before
 * running the batch process. Cleans up temp file after all tests.
 */
@DisplayName("TopologyBatchProcess — TERRACE_MY_1S generation")
class TopologyBatchProcessTest {

    private static final String SOURCE_DB  = "library/BOM.db";
    private static final String MIGRATION  = "migration/migration_topology_maker_bootstrap.sql";

    private static Path tempDb;
    private static String tempDbPath;

    @BeforeAll
    static void setupTestDb() throws Exception {
        // Copy canonical BOM.db (unified working database) to temp file
        tempDb = Files.createTempFile("topology_test_", ".db");
        Files.copy(Path.of(SOURCE_DB), tempDb, StandardCopyOption.REPLACE_EXISTING);
        tempDbPath = tempDb.toAbsolutePath().toString();

        // Apply migration
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             Statement st = conn.createStatement()) {
            applyMigration(conn, MIGRATION);
        }
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (tempDb != null) Files.deleteIfExists(tempDb);
    }

    // ── Test cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("T6-1: TERRACE_MY_1S order completes with DocStatus.CO")
    void orderCompletesSuccessfully() {
        TopologyOrder order = makeOrder("TERRACE_001");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);

        assertTrue(result.isComplete(),
            "Expected DocStatus.CO but got " + result.status()
            + ". Violations: " + result.violations()
            + ". Log: " + result.log());
    }

    @Test
    @DisplayName("T6-2: Exactly 7 room boundaries written")
    void sevenRoomBoundariesWritten() {
        TopologyOrder order = makeOrder("TERRACE_002");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);

        assertTrue(result.isComplete(), "Process did not complete: " + result.log());
        assertEquals(7, result.roomsWritten(),
            "Expected 7 rooms (PORCH + 3×BEDROOM + COMMON + BATHROOM + TOILET) "
            + "but got " + result.roomsWritten()
            + ". Log: " + result.log());
    }

    @Test
    @DisplayName("T6-3: All boundaries have coordinate_frame = DERIVED_MM")
    void allBoundariesAreDerivedMm() throws SQLException {
        TopologyOrder order = makeOrder("TERRACE_003");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);
        assertTrue(result.isComplete(), "Process did not complete");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM ad_room_boundary " +
                 "WHERE building_type = ? AND coordinate_frame != 'DERIVED_MM'")) {
            stmt.setString(1, "TERRACE_003");
            try (ResultSet rs = stmt.executeQuery()) {
                int wrongFrame = rs.next() ? rs.getInt(1) : 0;
                assertEquals(0, wrongFrame,
                    "Some rows have coordinate_frame != DERIVED_MM");
            }
        }
    }

    @Test
    @DisplayName("T6-4: UBBL check passes — all bedrooms >= 9290 mm² and >= 2700mm min dim")
    void ubblPassesForTerrace3Br() {
        TopologyOrder order = makeOrder("TERRACE_004");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);

        assertTrue(result.violations().isEmpty(),
            "Expected no UBBL violations but got: " + result.violations());
        assertTrue(result.isComplete(),
            "Process should complete when no violations. Log: " + result.log());
    }

    @Test
    @DisplayName("T6-5: 3 wall prefab BOMs seeded in m_bom by migration")
    void wallPrefabBomsExist() throws SQLException {
        try (TopologyAccessLayer reader = new TopologyAccessLayer(tempDbPath)) {
            assertTrue(reader.bomExists("WALL_EXT_MY_150_SOLID"),
                "WALL_EXT_MY_150_SOLID not found in m_bom");
            assertTrue(reader.bomExists("WALL_EXT_MY_150_WIN_STD"),
                "WALL_EXT_MY_150_WIN_STD not found in m_bom");
            assertTrue(reader.bomExists("WALL_EXT_MY_150_WIN_WIDE"),
                "WALL_EXT_MY_150_WIN_WIDE not found in m_bom");
        }
    }

    @Test
    @DisplayName("T6-6: FLOOR and UNIT BOMs generated with correct building_type prefix")
    void floorAndUnitBomsGenerated() throws SQLException {
        TopologyOrder order = makeOrder("TERRACE_006");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);
        assertTrue(result.isComplete(), "Process did not complete: " + result.log());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT bom_id, bom_type FROM m_bom WHERE bom_id LIKE 'TERRACE_006%' " +
                 "ORDER BY bom_id")) {
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    String bomId   = rs.getString("bom_id");
                    String bomType = rs.getString("bom_type");
                    if (bomId.endsWith("_GF"))   assertEquals("FLOOR", bomType);
                    if (bomId.endsWith("_UNIT"))  assertEquals("UNIT",  bomType);
                    count++;
                }
                assertEquals(2, count,
                    "Expected TERRACE_006_GF (FLOOR) + TERRACE_006_UNIT (UNIT) in m_bom");
            }
        }
    }

    @Test
    @DisplayName("T6-7: Building registered in c_order")
    void buildingRegistered() throws SQLException {
        TopologyOrder order = makeOrder("TERRACE_007");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);
        assertTrue(result.isComplete(), "Process did not complete: " + result.log());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT building_id, provenance FROM c_order " +
                 "WHERE building_id = ?")) {
            stmt.setString(1, "TERRACE_007");
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Building TERRACE_007 not found in c_order");
                assertEquals("GENERATIVE", rs.getString("provenance"));
            }
        }
    }

    @Test
    @DisplayName("T6-11: MY BOM catalog satisfies RE template after generation")
    void t6_11_templateContractAfterGeneration() throws Exception {
        // Run full TopologyBatchProcess to populate MY BOMs
        TopologyOrder order = makeOrder("TERRACE_011");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);
        assertTrue(result.isComplete(), "Process did not complete: " + result.log());

        // Check template contract against MY scope
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath)) {
            var report = BomTemplateContract.check(conn, "MY");
            assertTrue(report.isComplete(),
                "T6-11: template gaps after MY generation: " + report.gaps());
            // Info dump
            for (var check : report.checks()) {
                System.out.printf("  %s [%s]: found=%d best=%s %s%n",
                    check.categoryId(), check.categoryName(),
                    check.found(), check.bestFitBomId(),
                    check.satisfied() ? "OK" : "GAP");
            }
        }
    }

    // ── T6-8..T6-10: Interstitial filler tests ────────────────────────────
    //
    // After migration, SETs have extra product_ref rows. Counts below match migrated state.
    // Fillers tile every gap between consecutive fixed items: N items → N−1 fillers.

    @Test
    @DisplayName("T6-8: fillBuffers — WARDROBE_SET exact fit → 3 interstitial fillers, all width=0")
    void wardrobeSetExactFitFillersZero() throws Exception {
        try (TopologyWriter writer = new TopologyWriter(tempDbPath)) {
            // WARDROBE_SET after migration: 4 fixed items (2 geometry + 2 product_ref)
            // fixedSumW = 1200+0+1200+0 = 2400, parentW = 2400 → exact fit
            // Expect: 3 interstitial fillers (4−1), all width=0
            java.util.List<String> log = writer.fillBuffers("WARDROBE_SET", 2400, 600, 2100);
            writer.commit();

            assertFalse(log.isEmpty(), "Expected log output");

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath)) {
                // Verify 3 fillers
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM m_bom_line " +
                        "WHERE bom_id = 'WARDROBE_SET' AND is_variance = 1 AND is_active = 1");
                     ResultSet crs = stmt.executeQuery()) {
                    assertTrue(crs.next());
                    assertEquals(3, crs.getInt(1), "Expected 3 interstitial fillers (4 items − 1)");
                }
                // Verify all fillers width=0 (exact fit)
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT MAX(allocated_width_mm) FROM m_bom_line " +
                        "WHERE bom_id = 'WARDROBE_SET' AND is_variance = 1 AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Exact fit: all fillers width=0");
                }
                // Verify total strip = parent width
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT SUM(allocated_width_mm) FROM m_bom_line " +
                        "WHERE bom_id = 'WARDROBE_SET' AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2400, rs.getInt(1), "Total strip should equal parentW=2400");
                }
            }
        }
    }

    @Test
    @DisplayName("T6-9: fillBuffers — BED_SET positive remaining → 4 interstitial fillers")
    void bedSetPositiveRemainderFillers() throws Exception {
        try (TopologyWriter writer = new TopologyWriter(tempDbPath)) {
            // BED_SET: 5 fixed items, fixedW=600+600=1200, parentW=3000
            // Expect: 4 interstitial fillers (5−1), remainW=1800, total filler=1800
            java.util.List<String> log = writer.fillBuffers("BED_SET", 3000, 600, 2000);
            writer.commit();

            assertFalse(log.isEmpty(), "Expected log output");

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath)) {
                // Verify 4 fillers (N=5 items → N−1=4)
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM m_bom_line " +
                        "WHERE bom_id = 'BED_SET' AND is_variance = 1 AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(4, rs.getInt(1), "Expected 4 interstitial fillers");
                }
                // Verify total filler width = 1800
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT SUM(allocated_width_mm) FROM m_bom_line " +
                        "WHERE bom_id = 'BED_SET' AND is_variance = 1 AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1800, rs.getInt(1), "Total filler width should be 1800");
                }
                // Verify total strip = parent width (ground truth)
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT SUM(allocated_width_mm) FROM m_bom_line " +
                        "WHERE bom_id = 'BED_SET' AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(3000, rs.getInt(1), "Total strip should equal parentW=3000");
                }
            }
        }
    }

    @Test
    @DisplayName("T6-10: fillBuffers — LIVING_SET overflow → 5 fillers all width=0, warn logged")
    void livingSetOverflowFillersZero() throws Exception {
        try (TopologyWriter writer = new TopologyWriter(tempDbPath)) {
            // LIVING_SET: 6 active fixed items (TV+LOUNGE_CHAIR inactive),
            // fixedSumW=2000+1000+1600+610+610+1371=7191, parentW=4871 → overflow
            // Expect: 5 interstitial fillers (6−1), all width=0, [WARN] logged
            java.util.List<String> log = writer.fillBuffers("LIVING_SET", 4871, 1400, 1170);
            writer.commit();

            assertTrue(log.stream().anyMatch(l -> l.contains("[WARN]")),
                "Expected [WARN] for overflow. Log: " + log);

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath)) {
                // Verify 5 fillers (N=6 active items → N−1=5)
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM m_bom_line " +
                        "WHERE bom_id = 'LIVING_SET' AND is_variance = 1 AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(5, rs.getInt(1), "Expected 5 interstitial fillers");
                }
                // Verify all fillers have width=0
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT MAX(allocated_width_mm) FROM m_bom_line " +
                        "WHERE bom_id = 'LIVING_SET' AND is_variance = 1 AND is_active = 1");
                     ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "All fillers should have width=0 (overflow)");
                }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private TopologyOrder makeOrder(String orderId) {
        return new TopologyOrder(
            orderId, "TERRACE_MY_1S",
            new SiteEnvelope(9900, 8500, 3, true),
            DocStatus.IP
        );
    }

    private static void applyMigration(Connection conn, String sqlFile) throws Exception {
        String sql = Files.readString(Path.of(sqlFile));
        try (Statement stmt = conn.createStatement()) {
            // Split on semicolons, skip blanks and comment-only lines
            for (String chunk : sql.split(";")) {
                String trimmed = chunk.strip();
                // Remove comment lines for execution check
                String noComments = trimmed.lines()
                    .filter(l -> !l.stripLeading().startsWith("--"))
                    .reduce("", (a, b) -> a + "\n" + b)
                    .strip();
                if (!noComments.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }
}
