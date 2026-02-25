package com.bim.compiler.topologymaker;

import com.bim.compiler.topologymaker.db.TopologyAccessLayer;
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

    private static final String SOURCE_DB  = "library/component_library.db";
    private static final String MIGRATION  = "migration/migration_topology_maker_bootstrap.sql";

    private static Path tempDb;
    private static String tempDbPath;

    @BeforeAll
    static void setupTestDb() throws Exception {
        // Copy canonical library DB to a temp file
        tempDb = Files.createTempFile("topology_test_", ".db");
        Files.copy(Path.of(SOURCE_DB), tempDb, StandardCopyOption.REPLACE_EXISTING);
        tempDbPath = tempDb.toAbsolutePath().toString();

        // Apply migration to the temp DB
        applyMigration(tempDbPath, MIGRATION);
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
    @DisplayName("T6-7: Building registered in ad_building_registry")
    void buildingRegistered() throws SQLException {
        TopologyOrder order = makeOrder("TERRACE_007");
        TopologyResult result = new TopologyBatchProcess(tempDbPath).complete(order);
        assertTrue(result.isComplete(), "Process did not complete: " + result.log());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT building_id, provenance FROM ad_building_registry " +
                 "WHERE building_id = ?")) {
            stmt.setString(1, "TERRACE_007");
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Building TERRACE_007 not found in ad_building_registry");
                assertEquals("GENERATIVE", rs.getString("provenance"));
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

    private static void applyMigration(String dbPath, String sqlFile) throws Exception {
        String sql = Files.readString(Path.of(sqlFile));
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {
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
