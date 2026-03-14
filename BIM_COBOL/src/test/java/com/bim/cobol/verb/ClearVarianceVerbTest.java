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
 * Witness tests for CLEAR VARIANCE FROM BOM verb.
 * W-H2-4..6: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClearVarianceVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File(System.getProperty("bom.db"));
        tempDb = File.createTempFile("clear_variance_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        ctx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();

        // Seed: create a test BOM with 2 fixed children + 1 variance child
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "INSERT INTO m_bom (bom_id, bom_name, bom_type, group_by, is_active) " +
                "VALUES ('SY_CLR_TEST', 'ClearTest', 'SET', 'ROOM', 1)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, allocated_width_mm) " +
                "VALUES ('SY_CLR_TEST', 'FIXED_1', 10, 0, 1, 500)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, allocated_width_mm) " +
                "VALUES ('SY_CLR_TEST', 'BUFFER', 20, 1, 1, 200)");
            stmt.executeUpdate(
                "INSERT INTO m_bom_line (bom_id, role, sequence, is_variance, is_active, allocated_width_mm) " +
                "VALUES ('SY_CLR_TEST', 'FIXED_2', 30, 0, 1, 300)");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
        if (tempDb != null) tempDb.delete();
    }

    @Test @Order(1)
    @DisplayName("W-H2-4: CLEAR VARIANCE deletes variance rows, keeps fixed")
    void w_h2_4_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "CLEAR VARIANCE FROM BOM SY_CLR_TEST");

        assertTrue(result.pass(), "should pass: " + result.summary());
        assertEquals("CLEAR VARIANCE FROM BOM", result.verb());

        ClearVarianceVerb.ClearVariancePayload p =
            (ClearVarianceVerb.ClearVariancePayload) result.payload();
        assertNotNull(p);
        assertEquals("SY_CLR_TEST", p.bomId());
        assertEquals(1, p.deletedCount(), "1 variance row should be deleted");

        // Cross-check: only fixed children remain
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id = ? AND is_variance = 0")) {
            ps.setString(1, "SY_CLR_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "2 fixed children should remain");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id = ? AND is_variance = 1")) {
            ps.setString(1, "SY_CLR_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "0 variance rows should remain");
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H2-5: CLEAR VARIANCE on empty BOM deletes 0")
    void w_h2_5_noVariance() {
        VerbResult<?> result = registry.dispatch(ctx,
            "CLEAR VARIANCE FROM BOM SY_CLR_TEST");

        assertTrue(result.pass(), "should pass even with 0 deletes: " + result.summary());

        ClearVarianceVerb.ClearVariancePayload p =
            (ClearVarianceVerb.ClearVariancePayload) result.payload();
        assertEquals(0, p.deletedCount(), "no variance rows to delete");
    }

    @Test @Order(3)
    @DisplayName("W-H2-6: CLEAR VARIANCE on nonexistent BOM deletes 0 without error")
    void w_h2_6_nonexistentBom() {
        VerbResult<?> result = registry.dispatch(ctx,
            "CLEAR VARIANCE FROM BOM SY_NONEXISTENT_999");

        assertTrue(result.pass(), "should pass gracefully: " + result.summary());

        ClearVarianceVerb.ClearVariancePayload p =
            (ClearVarianceVerb.ClearVariancePayload) result.payload();
        assertEquals(0, p.deletedCount());
    }

    @Test @Order(99)
    void cleanup() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM m_bom_line WHERE bom_id = 'SY_CLR_TEST'");
            stmt.executeUpdate("DELETE FROM m_bom WHERE bom_id = 'SY_CLR_TEST'");
        }
    }
}
