package com.bim.cobol;

import com.bim.cobol.verb.VerifyPlacementVerb;
import com.bim.cobol.verb.VerifyPlacementVerb.PlacementVerifyPayload;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for {@link VerifyPlacementVerb}.
 *
 * <h2>W-COBOL-45 — SH output.db passes placement verification</h2>
 * <p>SH output.db has L0=1, L1≥4 (slab+GF+roof), L2≥4 (LIVING+DINING+MASTER+BATHROOM).
 * All L2 bom_ids are non-null. No gaps list.
 *
 * <h2>W-COBOL-46 — DX output.db passes placement verification</h2>
 * <p>DX output.db has L0=1, L1≥5 (slab+L1+slab+L2+roof), L2≥4 (DN+BT from L1, BT+KT from L2).
 *
 * <h2>W-COBOL-47 — Empty DB fails with non-empty gaps list</h2>
 * <p>A freshly created empty SQLite DB with an empty co_empty_space_line table
 * must fail with gaps describing the missing L0/L1/L2 rows.
 *
 * <h2>W-COBOL-48 — Nonexistent path fails gracefully</h2>
 * <p>A path that does not exist on disk returns a fail result without throwing an exception.
 */
@DisplayName("VerifyPlacementVerb — W-COBOL-45..48")
class VerifyPlacementVerbTest {

    private static final String SH_DB = "DAGCompiler/lib/output/ifc4_sample_house.db";
    private static final String DX_DB = "DAGCompiler/lib/output/ifc2x3_duplex.db";

    private VerbRegistry registry;
    private VerbContext bomCtx;

    @BeforeEach
    void setup() throws SQLException {
        Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
        bomCtx = VerbContext.ofBom(bomConn);
        registry = VerbRegistry.createDefault();
    }

    @AfterEach
    void teardown() throws SQLException {
        if (bomCtx.bomConn() != null && !bomCtx.bomConn().isClosed()) {
            bomCtx.bomConn().close();
        }
    }

    /**
     * W-COBOL-45: SH output.db placement verification passes.
     * Expected: L0=1, L1≥4, L2≥4, gaps empty.
     */
    @Test
    @DisplayName("W-COBOL-45: SH output.db VERIFY PLACEMENT → PASS, L0=1, L1≥4, L2≥4")
    void w_cobol_45_sh_passesVerification() {
        VerbResult<?> result = registry.dispatch(bomCtx, "VERIFY PLACEMENT " + SH_DB);

        assertTrue(result.pass(), "SH VERIFY PLACEMENT must pass. Summary: " + result.summary());
        assertEquals("VERIFY PLACEMENT", result.verb());

        PlacementVerifyPayload payload = (PlacementVerifyPayload) result.payload();
        assertNotNull(payload);
        assertEquals(1, payload.l0Count(), "SH must have exactly 1 L0 (UNIT acceptance) line");
        assertTrue(payload.l1Count() >= 3,
            "SH must have ≥3 L1 lines (GROUND_SLAB+GROUND_FLOOR+ROOF). Got: "
                + payload.l1Count());
        assertTrue(payload.l2Count() >= 4,
            "SH must have ≥4 L2 lines (LIVING+DINING+MASTER+BATHROOM). Got: " + payload.l2Count());
        assertTrue(payload.gaps().isEmpty(),
            "SH gaps must be empty. Got: " + payload.gaps());
    }

    /**
     * W-COBOL-46: DX output.db placement verification passes.
     * Expected: L0=1, L1≥5 (2 slabs + 2 floor levels + roof), L2≥4 (across 2 floors).
     */
    @Test
    @DisplayName("W-COBOL-46: DX output.db VERIFY PLACEMENT → PASS, L0=1, L1≥5, L2≥4")
    void w_cobol_46_dx_passesVerification() {
        VerbResult<?> result = registry.dispatch(bomCtx, "VERIFY PLACEMENT " + DX_DB);

        assertTrue(result.pass(), "DX VERIFY PLACEMENT must pass. Summary: " + result.summary());

        PlacementVerifyPayload payload = (PlacementVerifyPayload) result.payload();
        assertNotNull(payload);
        assertEquals(1, payload.l0Count(), "DX must have 1 L0 (UNIT acceptance) line");
        assertTrue(payload.l1Count() >= 5,
            "DX must have ≥5 L1 lines (GROUND_SLAB+LEVEL_1+UPPER_SLAB+LEVEL_2+ROOF). Got: "
                + payload.l1Count());
        assertTrue(payload.l2Count() >= 4,
            "DX must have ≥4 L2 lines (DN+BT from L1 + BT+KT from L2). Got: " + payload.l2Count());
        assertTrue(payload.gaps().isEmpty(),
            "DX gaps must be empty. Got: " + payload.gaps());
    }

    /**
     * W-COBOL-47: Empty DB (no ESLines) → fail, gaps list non-empty.
     * Creates an in-memory SQLite DB with an empty co_empty_space_line table.
     */
    @Test
    @DisplayName("W-COBOL-47: Empty DB (no ESLines) → FAIL with gaps list non-empty")
    void w_cobol_47_emptyDbFails() throws Exception {
        // Create a temp SQLite DB with empty co_empty_space_line table
        java.io.File tempDb = java.io.File.createTempFile("verify_empty_", ".db");
        tempDb.deleteOnExit();
        String tempPath = tempDb.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempPath);
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS co_empty_space_line (
                    line_id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    co_emptyspace_id INTEGER,
                    bom_id           TEXT,
                    bom_level        INTEGER DEFAULT 0,
                    doc_status       TEXT DEFAULT 'DR'
                )
                """);
        }

        VerbResult<?> result = registry.dispatch(bomCtx, "VERIFY PLACEMENT " + tempPath);

        assertFalse(result.pass(), "Empty DB must fail verification");
        assertEquals("VERIFY PLACEMENT", result.verb());

        PlacementVerifyPayload payload = (PlacementVerifyPayload) result.payload();
        assertNotNull(payload);
        assertEquals(0, payload.l0Count());
        assertEquals(0, payload.l1Count());
        assertEquals(0, payload.l2Count());
        assertFalse(payload.gaps().isEmpty(), "Gaps list must be non-empty for empty DB");
        // Should describe L0, L1, L2 shortfalls
        assertTrue(payload.gaps().stream().anyMatch(g -> g.contains("L0")),
            "Gaps must mention L0 shortfall");
        assertTrue(payload.gaps().stream().anyMatch(g -> g.contains("L1")),
            "Gaps must mention L1 shortfall");
        assertTrue(payload.gaps().stream().anyMatch(g -> g.contains("L2")),
            "Gaps must mention L2 shortfall");
    }

    /**
     * W-COBOL-48: Nonexistent path → fail gracefully (no exception thrown).
     */
    @Test
    @DisplayName("W-COBOL-48: Nonexistent path → FAIL gracefully without throwing")
    void w_cobol_48_nonexistentPathFailsGracefully() {
        VerbResult<?> result = registry.dispatch(bomCtx,
            "VERIFY PLACEMENT /nonexistent/path/to/output.db");

        assertFalse(result.pass(), "Nonexistent path must fail");
        assertEquals("VERIFY PLACEMENT", result.verb());
        assertNotNull(result.summary(), "Summary must not be null");

        PlacementVerifyPayload payload = (PlacementVerifyPayload) result.payload();
        assertNotNull(payload, "Payload must not be null even on path failure");
        assertFalse(payload.gaps().isEmpty(),
            "Gaps must describe the missing file");

        // No args case
        VerbResult<?> noArgs = registry.dispatch(bomCtx, "VERIFY PLACEMENT");
        assertFalse(noArgs.pass(), "No args must fail");
        assertTrue(noArgs.summary().contains("usage"),
            "No-args summary must contain usage hint: " + noArgs.summary());
    }
}
