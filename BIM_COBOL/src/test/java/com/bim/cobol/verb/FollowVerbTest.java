package com.bim.cobol.verb;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-FOLLOW-1: FOLLOW ceiling produces PipeSegment × ceil(run_length / stock_pipe_length).
 * Fitting count = 0 (straight run only).
 *
 * <p>Seeds a test room with known dimensions rather than depending on SH BOM.db.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.4 — Witness: W-FOLLOW-1
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FollowVerbTest {

    private static Connection conn;
    private static VerbContext ctx;
    private static VerbRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            // Minimal m_bom schema for room dimension lookup
            st.execute("CREATE TABLE m_bom ("
                    + "M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "Value TEXT,"
                    + "Name TEXT,"
                    + "is_active INTEGER DEFAULT 1,"
                    + "aabb_width_mm INTEGER DEFAULT 0,"
                    + "aabb_depth_mm INTEGER DEFAULT 0,"
                    + "aabb_height_mm INTEGER DEFAULT 0)");

            // Seed: 6000mm × 4000mm × 3000mm room (exactly 1 stock pipe on ceiling)
            st.execute("INSERT INTO m_bom (Value, Name, aabb_width_mm, aabb_depth_mm, aabb_height_mm)"
                    + " VALUES ('TEST_ROOM_6M', 'Test Room 6m', 6000, 4000, 3000)");

            // Seed: 8868mm × 1975mm room (SH living room dimensions — 2 segments at 6000mm stock)
            st.execute("INSERT INTO m_bom (Value, Name, aabb_width_mm, aabb_depth_mm, aabb_height_mm)"
                    + " VALUES ('SH_LIVING_SET', 'LIVING SET', 8868, 1975, 1170)");

            // Seed: zero-dimension room (should fail)
            st.execute("INSERT INTO m_bom (Value, Name, aabb_width_mm, aabb_depth_mm, aabb_height_mm)"
                    + " VALUES ('EMPTY_ROOM', 'Empty Room', 0, 0, 0)");
        }
        ctx = VerbContext.ofBom(conn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    /**
     * W-FOLLOW-1a: FOLLOW CEILING on a 6m room with 6000mm stock → 1 segment.
     */
    @Test
    @Order(1)
    void w_follow_1a_ceiling_exact_fit() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW CEILING TEST_ROOM_6M STOCK_LENGTH 6000");

        assertTrue(result.pass(), "FOLLOW should pass: " + result.summary());

        FollowVerb.FollowPayload payload = (FollowVerb.FollowPayload) result.payload();
        assertEquals("CEILING", payload.surfaceType());
        assertEquals("TEST_ROOM_6M", payload.roomRef());
        assertEquals(6000, payload.runLengthMm(), "run_length = max(6000, 4000) = 6000");
        assertEquals(1, payload.segmentCount(), "ceil(6000/6000) = 1");
        assertEquals(0, payload.fittingCount(), "FOLLOW = straight only, zero fittings");
    }

    /**
     * W-FOLLOW-1b: FOLLOW CEILING on a 6m room with 3000mm stock → 2 segments.
     */
    @Test
    @Order(2)
    void w_follow_1b_ceiling_half_stock() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW CEILING TEST_ROOM_6M STOCK_LENGTH 3000");

        assertTrue(result.pass(), "FOLLOW should pass: " + result.summary());

        FollowVerb.FollowPayload payload = (FollowVerb.FollowPayload) result.payload();
        assertEquals(2, payload.segmentCount(), "ceil(6000/3000) = 2");
        assertEquals(0, payload.fittingCount());
    }

    /**
     * W-FOLLOW-1c: FOLLOW CEILING on SH living room (8868mm) with 6000mm stock → 2 segments.
     */
    @Test
    @Order(3)
    void w_follow_1c_sh_living_room() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW CEILING SH_LIVING_SET");

        assertTrue(result.pass(), "FOLLOW should pass: " + result.summary());

        FollowVerb.FollowPayload payload = (FollowVerb.FollowPayload) result.payload();
        assertEquals(8868, payload.runLengthMm(), "run_length = max(8868, 1975) = 8868");
        assertEquals(2, payload.segmentCount(), "ceil(8868/6000) = 2");
        assertEquals(0, payload.fittingCount());
        assertEquals(25, payload.diameterMm(), "default diameter 25mm");
    }

    /**
     * W-FOLLOW-1d: FOLLOW CEILING on SH living room with 3000mm stock → 3 segments.
     */
    @Test
    @Order(4)
    void w_follow_1d_sh_living_short_stock() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW CEILING SH_LIVING_SET STOCK_LENGTH 3000");

        assertTrue(result.pass(), "FOLLOW should pass: " + result.summary());

        FollowVerb.FollowPayload payload = (FollowVerb.FollowPayload) result.payload();
        assertEquals(3, payload.segmentCount(), "ceil(8868/3000) = 3");
    }

    /**
     * W-FOLLOW-1e: Unknown room → FAIL.
     */
    @Test
    @Order(5)
    void w_follow_1e_unknown_room_fails() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW CEILING NONEXISTENT_ROOM");

        assertFalse(result.pass());
        assertTrue(result.summary().contains("not found"));
    }

    /**
     * W-FOLLOW-1f: Zero-dimension room → FAIL.
     */
    @Test
    @Order(6)
    void w_follow_1f_zero_dimension_fails() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW CEILING EMPTY_ROOM");

        assertFalse(result.pass());
    }

    /**
     * W-FOLLOW-1g: Invalid surface type → FAIL.
     */
    @Test
    @Order(7)
    void w_follow_1g_invalid_surface_fails() {
        VerbResult<?> result = registry.dispatch(ctx,
                "FOLLOW FLOOR TEST_ROOM_6M");

        assertFalse(result.pass());
        assertTrue(result.summary().contains("CEILING, WALL, or BEAM"));
    }

    /**
     * W-FOLLOW-1h: Missing args → FAIL with usage.
     */
    @Test
    @Order(8)
    void w_follow_1h_missing_args_shows_usage() {
        VerbResult<?> result = registry.dispatch(ctx, "FOLLOW");

        assertFalse(result.pass());
        assertTrue(result.summary().contains("usage:"));
    }
}
