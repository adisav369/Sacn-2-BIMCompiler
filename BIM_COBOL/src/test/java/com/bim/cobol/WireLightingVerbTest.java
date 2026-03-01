package com.bim.cobol;

import com.bim.cobol.geometry.ConduitRouter.ConduitRoutingResult;
import com.bim.cobol.verb.WireLightingVerb;
import com.bim.cobol.verb.WireLightingVerb.LightingComplianceResult;
import com.bim.cobol.verb.WireLightingVerb.LightingPayload;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for WIRE LIGHTING verb.
 *
 * <p>Each test is a numbered witness (W-COBOL-N) that proves
 * lighting design against live DB data (TB_LKTN building).
 */
class WireLightingVerbTest {

    private static Connection bomConn;
    private final WireLightingVerb verb = new WireLightingVerb();

    @BeforeAll
    static void open() throws SQLException {
        bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
    }

    @AfterAll
    static void close() throws SQLException {
        if (bomConn != null) bomConn.close();
    }

    /** W-COBOL-37: bilik_utama (BEDROOM 3.1x3.1m): 1 fixture (light_points=1), compliance PASS, lux>0, area~9.61m2. */
    @Test
    void w_cobol_37_bilikUtama() throws SQLException {
        VerbResult<LightingPayload> r = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "bilik_utama");

        assertTrue(r.pass(), r.summary());
        assertEquals("WIRE LIGHTING", r.verb());

        LightingPayload p = r.payload();
        assertEquals("TB_LKTN", p.buildingType());
        assertEquals("bilik_utama", p.roomName());
        assertEquals("BEDROOM", p.roomType());
        assertEquals(1, p.fixtureCount(), "3.1x3.1m room with 1 light_point → 1 fixture");
        assertTrue(p.compliancePass(), "compliance should pass: " + p.compliance());
        assertTrue(p.luxLevel() > 0, "lux > 0: " + p.luxLevel());
        assertTrue(p.roomAreaM2() > 9.0 && p.roomAreaM2() < 10.0,
                "room area ~9.61 m2: " + p.roomAreaM2());
    }

    /** W-COBOL-38: common (COMMON 3.7x6.2m): >=2 fixtures (light_points=2), max_spacing<=4.6m, conduit length>0. */
    @Test
    void w_cobol_38_common() throws SQLException {
        VerbResult<LightingPayload> r = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "common");

        assertTrue(r.pass(), r.summary());

        LightingPayload p = r.payload();
        assertEquals("COMMON", p.roomType());
        assertTrue(p.fixtureCount() >= 2,
                "common needs >= 2 fixtures (light_points=2): " + p.fixtureCount());

        // Compliance: max spacing between adjacent fixtures <= 4.6m
        assertTrue(p.compliancePass(), "compliance should pass: " + p.compliance());
        LightingComplianceResult spacingCheck = p.compliance().stream()
                .filter(c -> "MAX_SPACING".equals(c.rule()))
                .findFirst().orElse(null);
        assertNotNull(spacingCheck, "should have MAX_SPACING check");
        assertTrue(spacingCheck.actual() <= 4.6,
                "max spacing " + spacingCheck.actual() + " should be <= 4.6m");

        assertTrue(p.routing().totalLengthM() > 0,
                "conduit length > 0: " + p.routing().totalLengthM());
    }

    /** W-COBOL-39: common conduit routing: totalLength>0, one branch per fixture, fittings non-empty. */
    @Test
    void w_cobol_39_conduitRouting() throws SQLException {
        VerbResult<LightingPayload> r = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "common");

        assertTrue(r.pass(), r.summary());
        LightingPayload p = r.payload();
        ConduitRoutingResult routing = p.routing();

        assertTrue(routing.totalLengthM() > 0,
                "total conduit length > 0: " + routing.totalLengthM());
        assertEquals(p.fixtureCount(), routing.branchSpecs().size(),
                "one branch per fixture");
        assertFalse(routing.fittings().isEmpty(), "should have fittings");
    }

    /** W-COBOL-40: Error cases: nonexistent room=fail, missing storey=fail, insufficient args=fail. */
    @Test
    void w_cobol_40_errorCases() throws SQLException {
        // Nonexistent room
        VerbResult<LightingPayload> r1 = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Ground Floor", "NONEXISTENT_ROOM");
        assertFalse(r1.pass(), "nonexistent room should fail");
        assertTrue(r1.summary().contains("not found"), "summary: " + r1.summary());

        // Missing storey
        VerbResult<LightingPayload> r2 = verb.execute(
                VerbContext.ofBom(bomConn),
                "TB_LKTN", "Level 99", "bilik_utama");
        assertFalse(r2.pass(), "missing storey should fail");

        // Insufficient args
        VerbResult<LightingPayload> r3 = verb.execute(
                VerbContext.ofBom(bomConn), "TB_LKTN");
        assertFalse(r3.pass(), "insufficient args should fail");
    }
}
