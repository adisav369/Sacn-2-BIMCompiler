package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for FILL BUFFERS IN BOM verb.
 * W-H2-7..9: happy path, single-item (no fillers), data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FillBuffersVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File(System.getProperty("bom.db"));
        tempDb = File.createTempFile("fill_buffers_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        ctx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();

        // Seed: create a test SET BOM with 3 fixed children (widths: 400, 300, 300 = 1000mm)
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "INSERT INTO m_bom (bom_id, bom_name, bom_type, group_by, is_active) " +
                "VALUES ('SY_FILL_TEST', 'FillTest', 'SET', 'ROOM', 1)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, " +
                "allocated_width_mm, allocated_depth_mm, allocated_height_mm) " +
                "VALUES ('SY_FILL_TEST', 'ITEM_A', 10, 0, 1, 400, 600, 800)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, " +
                "allocated_width_mm, allocated_depth_mm, allocated_height_mm) " +
                "VALUES ('SY_FILL_TEST', 'ITEM_B', 20, 0, 1, 300, 600, 800)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, " +
                "allocated_width_mm, allocated_depth_mm, allocated_height_mm) " +
                "VALUES ('SY_FILL_TEST', 'ITEM_C', 30, 0, 1, 300, 600, 800)");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
        if (tempDb != null) tempDb.delete();
    }

    @Test @Order(1)
    @DisplayName("W-H2-7: FILL BUFFERS creates N-1 interstitial fillers with correct widths")
    void w_h2_7_happyPath() throws Exception {
        // 3 fixed items (sum=1000mm), parent=2000mm → remain=1000mm, 2 fillers of 500 each
        VerbResult<?> result = registry.dispatch(ctx,
            "FILL BUFFERS IN BOM SY_FILL_TEST WIDTH 2000 DEPTH 600 HEIGHT 800");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("FILL BUFFERS IN BOM", result.verb());

        FillBuffersVerb.FillBuffersPayload p =
            (FillBuffersVerb.FillBuffersPayload) result.payload();
        assertNotNull(p);
        assertEquals("SY_FILL_TEST", p.bomId());
        assertEquals(3, p.fixedCount(), "3 fixed items");
        assertEquals(2, p.fillersInserted(), "N-1 = 2 fillers");
        assertEquals(1000, p.remainWidth(), "2000 - 1000 = 1000mm remaining");
        assertTrue(p.warnings().isEmpty(), "no warnings expected");

        // Cross-check: 3 fixed + 2 fillers = 5 total rows
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id = ? AND is_active = 1")) {
            ps.setString(1, "SY_FILL_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "3 fixed + 2 fillers = 5 total");
            }
        }

        // Cross-check: filler widths sum to remainW
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT SUM(allocated_width_mm) FROM m_bom_line WHERE bom_id = ? AND is_variance = 1")) {
            ps.setString(1, "SY_FILL_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1000, rs.getInt(1), "filler widths should sum to remainW=1000");
            }
        }

        // Cross-check: sequences are interleaved (fixed: 10,30,50; fillers: 20,40)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sequence, is_variance FROM m_bom_line WHERE bom_id = ? ORDER BY sequence")) {
            ps.setString(1, "SY_FILL_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                int[] expectedSeqs = {10, 20, 30, 40, 50};
                int[] expectedVar  = { 0,  1,  0,  1,  0};
                for (int i = 0; i < expectedSeqs.length; i++) {
                    assertTrue(rs.next(), "row " + i + " should exist");
                    assertEquals(expectedSeqs[i], rs.getInt("sequence"),
                        "sequence at position " + i);
                    assertEquals(expectedVar[i], rs.getInt("is_variance"),
                        "is_variance at position " + i);
                }
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H2-8: FILL BUFFERS with single item produces 0 fillers")
    void w_h2_8_singleItem() throws Exception {
        // Create a BOM with just 1 fixed child
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "INSERT INTO m_bom (bom_id, bom_name, bom_type, group_by, is_active) " +
                "VALUES ('SY_FILL_SINGLE', 'Single', 'SET', 'ROOM', 1)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, " +
                "allocated_width_mm, allocated_depth_mm, allocated_height_mm) " +
                "VALUES ('SY_FILL_SINGLE', 'ONLY_ITEM', 10, 0, 1, 500, 600, 800)");
        }

        VerbResult<?> result = registry.dispatch(ctx,
            "FILL BUFFERS IN BOM SY_FILL_SINGLE WIDTH 2000 DEPTH 600 HEIGHT 800");

        assertTrue(result.pass(), "should pass: " + result.summary());

        FillBuffersVerb.FillBuffersPayload p =
            (FillBuffersVerb.FillBuffersPayload) result.payload();
        assertEquals(1, p.fixedCount(), "1 fixed item");
        assertEquals(0, p.fillersInserted(), "no interstitial gaps with 1 item");

        // Cleanup
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM m_bom_line WHERE bom_id = 'SY_FILL_SINGLE'");
            stmt.executeUpdate("DELETE FROM m_bom WHERE bom_id = 'SY_FILL_SINGLE'");
        }
    }

    @Test @Order(3)
    @DisplayName("W-H2-9: FILL BUFFERS with overflow warns and clamps fillers to 0")
    void w_h2_9_overflow() throws Exception {
        // Clear fillers from test 1 first
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM m_bom_line WHERE bom_id = 'SY_FILL_TEST' AND is_variance = 1");
        }
        // Reset sequences for the fixed items
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE m_bom_line SET sequence = ? WHERE bom_id = 'SY_FILL_TEST' AND role = ?")) {
            ps.setInt(1, 10); ps.setString(2, "ITEM_A"); ps.executeUpdate();
            ps.setInt(1, 20); ps.setString(2, "ITEM_B"); ps.executeUpdate();
            ps.setInt(1, 30); ps.setString(2, "ITEM_C"); ps.executeUpdate();
        }

        // Parent width=500 < fixedSumW=1000 → overflow
        VerbResult<?> result = registry.dispatch(ctx,
            "FILL BUFFERS IN BOM SY_FILL_TEST WIDTH 500 DEPTH 600 HEIGHT 800");

        assertTrue(result.pass(), "should still pass with warnings: " + result.summary());

        FillBuffersVerb.FillBuffersPayload p =
            (FillBuffersVerb.FillBuffersPayload) result.payload();
        assertEquals(0, p.remainWidth(), "remainW should be clamped to 0");
        assertFalse(p.warnings().isEmpty(), "should have overflow warning");
        assertTrue(p.warnings().stream().anyMatch(w -> w.contains("exceeds")),
            "warning should mention 'exceeds'");
    }

    @Test @Order(99)
    void cleanup() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM m_bom_line WHERE bom_id LIKE 'SY_FILL%'");
            stmt.executeUpdate("DELETE FROM m_bom WHERE bom_id LIKE 'SY_FILL%'");
        }
    }
}
