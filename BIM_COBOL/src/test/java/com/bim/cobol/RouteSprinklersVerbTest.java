package com.bim.cobol;

import com.bim.cobol.geometry.ComplianceChecker.ComplianceResult;
import com.bim.cobol.geometry.PipeRouter.PipeRoutingResult;
import com.bim.cobol.verb.RouteSprinklersVerb;
import com.bim.cobol.verb.RouteSprinklersVerb.SprinklerRoutingPayload;
import com.bim.compiler.geometry.Point3D;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for ROUTE SPRINKLERS verb.
 *
 * <p>Each test is a numbered witness (W-COBOL-N) that proves
 * sprinkler routing against live DB data (TB_LKTN building).
 */
class RouteSprinklersVerbTest {

    private static Connection bomConn;
    private final RouteSprinklersVerb verb = new RouteSprinklersVerb();

    @BeforeAll
    static void open() throws SQLException {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
    }

    @AfterAll
    static void close() throws SQLException {
        if (bomConn != null) bomConn.close();
    }

    /** W-COBOL-9: bilik_utama (BEDROOM 3.1x3.1m): head count > 0, pass=true, compliance pass. */
    @Test
    void w_cobol_9_bilikUtama() throws SQLException {
        VerbResult<SprinklerRoutingPayload> r = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "bilik_utama");

        assertTrue(r.pass(), r.summary());
        assertEquals("ROUTE SPRINKLERS", r.verb());

        SprinklerRoutingPayload p = r.payload();
        assertEquals("TB_LKTN", p.buildingType());
        assertEquals("bilik_utama", p.roomName());
        assertEquals("BEDROOM", p.roomType());
        assertTrue(p.headCount() > 0, "head count > 0");
        assertTrue(p.compliancePass(), "compliance should pass: " + p.compliance());

        // Room is 3.1x3.1m = 9.61 m2 — with 3m default spacing, expect 1 head
        assertEquals(1, p.headCount(), "3.1x3.1m room with 3m spacing → 1 head");
        assertTrue(p.roomAreaM2() > 9.0 && p.roomAreaM2() < 10.0,
                "room area ~9.61 m2: " + p.roomAreaM2());
    }

    /** W-COBOL-10: common (COMMON 3.7x6.2m): head count correct for LIGHT hazard, max spacing <= 4.6m. */
    @Test
    void w_cobol_10_common() throws SQLException {
        VerbResult<SprinklerRoutingPayload> r = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "common");

        assertTrue(r.pass(), r.summary());

        SprinklerRoutingPayload p = r.payload();
        assertEquals("COMMON", p.roomType());
        // 3.7x6.2m = 22.94 m2 — with 3m spacing: nx=1, ny=2 → 2 heads
        assertEquals(2, p.headCount(), "3.7x6.2m room with 3m spacing → 2 heads");

        // Compliance: max spacing between adjacent heads ≤ 4.6m
        assertTrue(p.compliancePass(), "compliance should pass: " + p.compliance());
        ComplianceResult spacingCheck = p.compliance().stream()
                .filter(c -> "SPACING".equals(c.rule()))
                .findFirst().orElse(null);
        assertNotNull(spacingCheck, "should have spacing check");
        assertTrue(spacingCheck.actual() <= 4.6,
                "max spacing " + spacingCheck.actual() + " should be <= 4.6m");
    }

    /** W-COBOL-11: Pipe routing: total pipe length > 0, all heads connected (branch for each head). */
    @Test
    void w_cobol_11_pipeRouting() throws SQLException {
        VerbResult<SprinklerRoutingPayload> r = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "common");

        assertTrue(r.pass(), r.summary());
        SprinklerRoutingPayload p = r.payload();
        PipeRoutingResult routing = p.routing();

        assertTrue(routing.totalLengthM() > 0,
                "total pipe length > 0: " + routing.totalLengthM());
        assertEquals(p.headCount(), routing.branchSpecs().size(),
                "one branch per head");
        assertFalse(routing.fittings().isEmpty(), "should have fittings");
    }

    /** W-COBOL-12: Error cases: nonexistent room -> fail, missing storey -> fail. */
    @Test
    void w_cobol_12_errorCases() throws SQLException {
        // Nonexistent room
        VerbResult<SprinklerRoutingPayload> r1 = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "NONEXISTENT_ROOM");
        assertFalse(r1.pass(), "nonexistent room should fail");
        assertTrue(r1.summary().contains("not found"), "summary: " + r1.summary());

        // Missing storey
        VerbResult<SprinklerRoutingPayload> r2 = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Level 99", "bilik_utama");
        assertFalse(r2.pass(), "missing storey should fail");

        // Insufficient args
        VerbResult<SprinklerRoutingPayload> r3 = verb.execute(
                VerbContext.ofBom(bomConn), "TB_LKTN");
        assertFalse(r3.pass(), "insufficient args should fail");
    }
}
