package com.bim.cobol;

import com.bim.cobol.verb.TrimWallsToRoofVerb;
import com.bim.cobol.verb.TrimWallsToRoofVerb.TrimEntry;
import com.bim.cobol.verb.TrimWallsToRoofVerb.TrimPayload;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for TRIM WALLS TO ROOF verb.
 * // Implementing BIM_COBOL.md §17.3 — Witness: W-TRIM-*
 *
 * <p>Tests against SampleHouse extracted DB (barrel vault roof — measures
 * roof surface from AABB, no pitch parameter) and a synthetic pitched roof
 * scenario.
 */
@DisplayName("TRIM WALLS TO ROOF — verb proof")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrimWallsToRoofVerbTest {

    private static final String SH_DB =
            "DAGCompiler/lib/input/SampleHouse_extracted.db";

    private final TrimWallsToRoofVerb verb = new TrimWallsToRoofVerb();

    // ═══════════════════════════════════════════════════════════════════
    //  SampleHouse: barrel vault — walls at eave edges flagged for trim
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-TRIM-1: SH barrel vault — verb passes, walls at eave edges trimmed")
    void w_trim_1_sh_barrel_vault() throws Exception {
        Assumptions.assumeTrue(new File(SH_DB).exists(),
                "SH extracted DB missing — skip");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB)) {
            VerbResult<TrimPayload> r = verb.execute(
                    VerbContext.withOutput(null, null, conn));

            assertTrue(r.pass(), r.summary());
            TrimPayload p = r.payload();
            assertNotNull(p, "payload must not be null");
            assertTrue(p.wallsUnderRoof() > 0,
                    "SH has walls under roof: " + p.wallsUnderRoof());
            // SH barrel vault: exterior walls near eave edges exceed the
            // linearly-interpolated roof surface → flagged for trimming.
            assertTrue(p.wallsTrimmed() >= 2,
                    "barrel vault: exterior walls at eave edges need trimming — " + describeTrims(p));
        }
    }

    @Test @Order(2)
    @DisplayName("W-TRIM-2: SH — all 5 walls detected under roof footprint")
    void w_trim_2_sh_walls_under_roof() throws Exception {
        Assumptions.assumeTrue(new File(SH_DB).exists(),
                "SH extracted DB missing — skip");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB)) {
            VerbResult<TrimPayload> r = verb.execute(
                    VerbContext.withOutput(null, null, conn));

            assertTrue(r.pass(), r.summary());
            TrimPayload p = r.payload();
            // SH has 5 IfcWall elements, all under the roof footprint
            assertEquals(5, p.wallsUnderRoof(),
                    "SH has 5 walls under roof footprint");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Synthetic pitched roof — walls at edges should be trimmed
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("W-TRIM-3: Pitched roof — wall at eave edge TRIMMED")
    void w_trim_3_pitched_roof_trim() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            seedPitchedScenario(conn);

            VerbResult<TrimPayload> r = verb.execute(
                    VerbContext.withOutput(null, null, conn));

            assertTrue(r.pass(), r.summary());
            TrimPayload p = r.payload();
            assertTrue(p.wallsTrimmed() > 0,
                    "pitched roof: wall at eave should need trimming");

            // The tall wall at Y=0 (south edge, near eave) extends to 4.0m
            // but the roof surface at Y=0 is near eave height (3.0m)
            TrimEntry tallWall = p.entries().stream()
                    .filter(e -> e.wallGuid().equals("WALL_TALL"))
                    .findFirst().orElseThrow();
            assertTrue(tallWall.needsTrim(),
                    "tall wall at eave must be trimmed: " + tallWall);
            assertTrue(tallWall.trimAmount() > 0.5,
                    "trim amount should be >0.5m: " + tallWall.trimAmount());
        }
    }

    @Test @Order(11)
    @DisplayName("W-TRIM-4: Pitched roof — short wall at ridge NOT trimmed")
    void w_trim_4_pitched_roof_no_trim() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            seedPitchedScenario(conn);

            VerbResult<TrimPayload> r = verb.execute(
                    VerbContext.withOutput(null, null, conn));

            assertTrue(r.pass(), r.summary());
            TrimPayload p = r.payload();

            // The short wall at Y=5 (center, near ridge) extends to 3.5m
            // but the roof ridge is at 4.6m — no trimming needed
            TrimEntry shortWall = p.entries().stream()
                    .filter(e -> e.wallGuid().equals("WALL_SHORT"))
                    .findFirst().orElseThrow();
            assertFalse(shortWall.needsTrim(),
                    "short wall at ridge should not be trimmed: " + shortWall);
        }
    }

    @Test @Order(12)
    @DisplayName("W-TRIM-5: Pitched roof — curtain wall TRIMMED at slope")
    void w_trim_5_curtain_wall_trimmed() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            seedPitchedScenario(conn);

            VerbResult<TrimPayload> r = verb.execute(
                    VerbContext.withOutput(null, null, conn));

            assertTrue(r.pass(), r.summary());
            TrimPayload p = r.payload();

            TrimEntry curtain = p.entries().stream()
                    .filter(e -> e.wallGuid().equals("CURTAIN_WALL"))
                    .findFirst().orElseThrow();
            assertTrue(curtain.needsTrim(),
                    "curtain wall at mid-slope must be trimmed: " + curtain);
            // Curtain wall at Y=2 (quarter span): roofZ ≈ 3.0 + 1.6*0.6 = 3.96
            // Wall maxZ = 4.5 → trim ~0.54m
            assertTrue(curtain.trimAmount() > 0.3 && curtain.trimAmount() < 1.0,
                    "trim amount should be 0.3-1.0m: " + curtain.trimAmount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Error cases
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("W-TRIM-6: No connections → fail")
    void w_trim_6_no_connection() throws Exception {
        VerbResult<TrimPayload> r = verb.execute(
                VerbContext.ofBom(DriverManager.getConnection("jdbc:sqlite::memory:")));
        assertFalse(r.pass(), "should fail without outputConn/componentConn");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  New: SH walls at eave edges ARE flagged (the visible bug fix)
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(30)
    @DisplayName("W-TRIM-7: SH exterior walls at eave edges flagged for trimming")
    void w_trim_7_sh_eave_walls_flagged() throws Exception {
        Assumptions.assumeTrue(new File(SH_DB).exists(),
                "SH extracted DB missing — skip");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SH_DB)) {
            VerbResult<TrimPayload> r = verb.execute(
                    VerbContext.withOutput(null, null, conn));

            assertTrue(r.pass(), r.summary());
            TrimPayload p = r.payload();

            // At least one exterior wall near the eave edge should be flagged.
            // The barrel vault roof slopes from eave (minZ≈1.74) to crown
            // (maxZ≈3.48). Exterior walls near the eave extend above the
            // linearly-interpolated roof surface at their Y position.
            long trimmedExterior = p.entries().stream()
                    .filter(TrimEntry::needsTrim)
                    .filter(e -> e.elementName().contains("Ext"))
                    .count();
            assertTrue(trimmedExterior >= 1,
                    "at least one exterior wall at eave edge should be flagged: "
                    + describeTrims(p));

            // Verify slope metadata is populated
            TrimEntry anyTrimmed = p.entries().stream()
                    .filter(TrimEntry::needsTrim)
                    .findFirst().orElseThrow();
            assertTrue(anyTrimmed.roofSlopeAngle() > 0,
                    "barrel vault slope angle should be > 0°");
            assertNotNull(anyTrimmed.roofSlopeDirection(),
                    "slope direction must not be null");
            assertEquals("ANGLED", anyTrimmed.trimProfileType(),
                    "barrel vault approximated as ANGLED (linear slope from AABB)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Scenario seeder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Seeds an in-memory DB with a pitched gable roof (ridge E-W)
     * and 3 walls: tall (at eave), short (at ridge), curtain (mid-slope).
     *
     * <pre>
     *   Roof: X[0,12] Y[0,10] Z[3.0, 4.6] — gable, ridge along X
     *   WALL_TALL:    Y≈0    (near south eave), maxZ=4.0 → should be trimmed
     *   WALL_SHORT:   Y≈5    (near ridge), maxZ=3.5 → should NOT be trimmed
     *   CURTAIN_WALL: Y≈2    (quarter span), maxZ=4.5 → should be trimmed
     * </pre>
     */
    private void seedPitchedScenario(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE elements_meta (
                    id INTEGER PRIMARY KEY, guid TEXT, ifc_class TEXT,
                    element_name TEXT, storey TEXT)""");
            st.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id, minX, maxX, minY, maxY, minZ, maxZ)""");

            // Pitched gable roof — ridge along X, eave Z=3.0, ridge Z=4.6
            st.execute("""
                INSERT INTO elements_meta VALUES (1,'ROOF_GABLE','IfcRoof','Test Roof','Roof')""");
            st.execute("""
                INSERT INTO elements_rtree VALUES (1, 0, 12, 0, 10, 3.0, 4.6)""");

            // Wall 1: tall, at south edge (Y≈0), full height to 4.0m
            st.execute("""
                INSERT INTO elements_meta VALUES (2,'WALL_TALL','IfcWall','Tall Wall South','GF')""");
            st.execute("""
                INSERT INTO elements_rtree VALUES (2, 1, 5, -0.1, 0.1, 0, 4.0)""");

            // Wall 2: short, at center (Y≈5), only to 3.5m (below ridge)
            st.execute("""
                INSERT INTO elements_meta VALUES (3,'WALL_SHORT','IfcWall','Short Wall Center','GF')""");
            st.execute("""
                INSERT INTO elements_rtree VALUES (3, 1, 5, 4.9, 5.1, 0, 3.5)""");

            // Wall 3: curtain wall at Y≈2 (quarter span), to 4.5m
            st.execute("""
                INSERT INTO elements_meta VALUES (4,'CURTAIN_WALL','IfcCurtainWall','Glass Wall','GF')""");
            st.execute("""
                INSERT INTO elements_rtree VALUES (4, 2, 8, 1.9, 2.1, 0, 4.5)""");
        }
    }

    private String describeTrims(TrimPayload p) {
        StringBuilder sb = new StringBuilder();
        for (TrimEntry e : p.entries()) {
            if (e.needsTrim()) {
                sb.append("\n  %s: maxZ=%.3f, roofZ=%.3f, trim=%.3f, slope=%.1f° %s %s"
                    .formatted(e.wallGuid(), e.originalMaxZ(), e.roofSurfaceZ(),
                               e.trimAmount(), e.roofSlopeAngle(),
                               e.roofSlopeDirection(), e.trimProfileType()));
            }
        }
        return sb.isEmpty() ? "(none)" : sb.toString();
    }
}
