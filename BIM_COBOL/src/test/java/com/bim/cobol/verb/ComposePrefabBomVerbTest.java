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
 * Witness tests for COMPOSE PREFAB BOM verb.
 * W-H2-1..3: happy path, error case, data integrity cross-check.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComposePrefabBomVerbTest {

    private static Connection conn;
    private static File tempDb;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        File source = new File("library/BOM.db");
        tempDb = File.createTempFile("compose_prefab_bom_test_", ".db");
        tempDb.deleteOnExit();
        Files.copy(source.toPath(), tempDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

        conn = DriverManager.getConnection("jdbc:sqlite:" + tempDb.getAbsolutePath());
        ctx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
        if (tempDb != null) tempDb.delete();
    }

    @Test @Order(1)
    @DisplayName("W-H2-1: COMPOSE PREFAB BOM creates m_bom + m_bom_line rows")
    void w_h2_1_happyPath() throws Exception {
        VerbResult<?> result = registry.dispatch(ctx,
            "COMPOSE PREFAB BOM SY_TEST_PREFAB NAME \"Test Prefab\" DESC \"Test desc\" TYPE FLOOR " +
            "CHILD SY_CHILD_A ROLE BED_1 SEQ 10 MIN_SPACE 100 " +
            "CHILD SY_CHILD_B ROLE BED_2 SEQ 20 MIN_SPACE 0");

        assertTrue(result.pass(), "COMPOSE PREFAB BOM should pass: " + result.summary());
        assertEquals("COMPOSE PREFAB BOM", result.verb());

        ComposePrefabBomVerb.ComposePrefabBomPayload p =
            (ComposePrefabBomVerb.ComposePrefabBomPayload) result.payload();
        assertNotNull(p);
        assertEquals("SY_TEST_PREFAB", p.bomId());
        assertTrue(p.bomInserted(), "BOM should be newly inserted");
        assertEquals(2, p.childrenAdded(), "Two children should be added");

        // Cross-check DB
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT bom_name, bom_type, group_by FROM m_bom WHERE bom_id = ?")) {
            ps.setString(1, "SY_TEST_PREFAB");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "m_bom row should exist");
                assertEquals("Test Prefab", rs.getString("bom_name"));
                assertEquals("FLOOR", rs.getString("bom_type"));
                assertEquals("BUILDING", rs.getString("group_by"));
            }
        }
    }

    @Test @Order(2)
    @DisplayName("W-H2-2: COMPOSE PREFAB BOM fails on missing NAME")
    void w_h2_2_errorMissingName() {
        VerbResult<?> result = registry.dispatch(ctx,
            "COMPOSE PREFAB BOM SY_BAD TYPE FLOOR");

        assertFalse(result.pass(), "should fail without NAME");
        assertTrue(result.summary().contains("NAME"),
            "error message should mention NAME: " + result.summary());
    }

    @Test @Order(3)
    @DisplayName("W-H2-3: COMPOSE PREFAB BOM idempotent — second call inserts 0")
    void w_h2_3_idempotent() throws Exception {
        // Call again with same bomId
        VerbResult<?> result = registry.dispatch(ctx,
            "COMPOSE PREFAB BOM SY_TEST_PREFAB NAME \"Test Prefab\" DESC \"desc\" TYPE FLOOR " +
            "CHILD SY_CHILD_A ROLE BED_1 SEQ 10 MIN_SPACE 100");

        assertTrue(result.pass(), "idempotent call should still pass: " + result.summary());

        ComposePrefabBomVerb.ComposePrefabBomPayload p =
            (ComposePrefabBomVerb.ComposePrefabBomPayload) result.payload();
        assertFalse(p.bomInserted(), "BOM should NOT be re-inserted (INSERT OR IGNORE)");
        assertEquals(0, p.childrenAdded(), "children should NOT be re-inserted (INSERT OR IGNORE)");

        // Cross-check: still exactly 2 children
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id = ?")) {
            ps.setString(1, "SY_TEST_PREFAB");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "still 2 children after idempotent re-run");
            }
        }
    }

    @Test @Order(99)
    void cleanup() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM m_bom_line WHERE bom_id = 'SY_TEST_PREFAB'");
            stmt.executeUpdate("DELETE FROM m_bom WHERE bom_id = 'SY_TEST_PREFAB'");
        }
    }
}
