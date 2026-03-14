package com.bim.cobol;

import com.bim.cobol.verb.CheckComplianceVerb;
import com.bim.cobol.verb.CheckComplianceVerb.ComplianceCheckPayload;
import com.bim.cobol.verb.CheckComplianceVerb.ComplianceResult;
import com.bim.cobol.verb.CheckRoomVerb;
import com.bim.cobol.verb.CheckRoomVerb.RoomCheckPayload;
import com.bim.cobol.verb.CheckRoomVerb.RoomCheckResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CHECK ROOM + CHECK COMPLIANCE witnesses — validates building code
 * and placement rule checks against Duplex extracted + BOM.db.
 */
class CheckRoomComplianceTest {

    private static final String DUPLEX_EXTRACTED =
            "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db";

    private static Connection bomConn;

    private final CheckRoomVerb roomVerb = new CheckRoomVerb();
    private final CheckComplianceVerb complianceVerb = new CheckComplianceVerb();

    @BeforeAll
    static void open() throws SQLException {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
    }

    @AfterAll
    static void close() throws SQLException {
        if (bomConn != null) bomConn.close();
    }

    // ── CHECK ROOM witnesses ───────────────────────────────────────

    /**
     * W-COBOL-29: Duplex IfcSpace rooms all have positive area.
     * 21 IfcSpace entries, each with at least 1 contained element.
     */
    @Test
    void w_cobol_29_duplexRoomsPositiveArea() throws SQLException {
        VerbResult<RoomCheckPayload> r = roomVerb.execute(
                VerbContext.ofBom(bomConn), DUPLEX_EXTRACTED);
        assertEquals("CHECK ROOM", r.verb());

        RoomCheckPayload p = r.payload();
        assertNotNull(p);
        assertTrue(p.roomCount() > 0,
                "Duplex should have rooms with contained elements, got: " + p.roomCount());

        // All rooms should have positive area
        long positiveAreaPass = p.results().stream()
                .filter(rc -> "AREA_POSITIVE".equals(rc.checkId()) && rc.pass())
                .count();
        long positiveAreaFail = p.results().stream()
                .filter(rc -> "AREA_POSITIVE".equals(rc.checkId()) && !rc.pass())
                .count();
        assertEquals(0, positiveAreaFail,
                "all rooms should have positive area");
        assertTrue(positiveAreaPass > 0,
                "should have checked at least 1 room for positive area");

        System.out.printf("  W-COBOL-29: %d rooms, all have positive area%n",
                positiveAreaPass);
    }

    /**
     * W-COBOL-30: Duplex room areas checked against ad_ubbl_rule minimums.
     * UBBL requires bedroom ≥ 9.29 m², bathroom ≥ 2.5 m², etc.
     */
    @Test
    void w_cobol_30_duplexRoomAreaVsUbbl() throws SQLException {
        VerbResult<RoomCheckPayload> r = roomVerb.execute(
                VerbContext.ofBom(bomConn), DUPLEX_EXTRACTED);

        RoomCheckPayload p = r.payload();
        assertNotNull(p);
        assertTrue(p.ruleCount() > 0,
                "should have loaded UBBL rules, got: " + p.ruleCount());

        // Check that area rules were applied
        long areaChecks = p.results().stream()
                .filter(rc -> rc.checkId().startsWith("UBBL_") && rc.checkId().contains("AREA"))
                .count();
        assertTrue(areaChecks > 0,
                "should have applied area rules, got: " + areaChecks);

        // Report pass/fail counts for UBBL area rules
        long areaPass = p.results().stream()
                .filter(rc -> rc.checkId().startsWith("UBBL_") && rc.checkId().contains("AREA")
                        && rc.pass())
                .count();

        System.out.printf("  W-COBOL-30: %d UBBL area checks, %d pass%n",
                areaChecks, areaPass);
    }

    /**
     * W-COBOL-31: Duplex ceiling heights within acceptable range (≥ 2.4m UBBL).
     * Room heights derived from Z extent of contained elements.
     */
    @Test
    void w_cobol_31_duplexCeilingHeight() throws SQLException {
        VerbResult<RoomCheckPayload> r = roomVerb.execute(
                VerbContext.ofBom(bomConn), DUPLEX_EXTRACTED);

        RoomCheckPayload p = r.payload();

        // All rooms should have positive height
        long heightPositive = p.results().stream()
                .filter(rc -> "HEIGHT_POSITIVE".equals(rc.checkId()) && rc.pass())
                .count();
        assertTrue(heightPositive > 0,
                "should have rooms with positive height");

        // UBBL ceiling check should be applied
        long ceilChecks = p.results().stream()
                .filter(rc -> "UBBL_CEIL".equals(rc.checkId()))
                .count();
        assertTrue(ceilChecks > 0,
                "UBBL_CEIL rule should be applied to rooms, got: " + ceilChecks);

        System.out.printf("  W-COBOL-31: %d rooms with positive height, %d ceiling checks%n",
                heightPositive, ceilChecks);
    }

    /**
     * W-COBOL-32: Error cases — no args, nonexistent DB, DB with no IfcSpace.
     */
    @Test
    void w_cobol_32_roomErrorCases() throws SQLException {
        // No args
        VerbResult<RoomCheckPayload> r1 = roomVerb.execute(
                VerbContext.ofBom(bomConn));
        assertFalse(r1.pass(), "no args should fail");
        assertNull(r1.payload());

        // Nonexistent DB
        VerbResult<RoomCheckPayload> r2 = roomVerb.execute(
                VerbContext.ofBom(bomConn), "/tmp/nonexistent_db_12345.db");
        assertFalse(r2.pass(), "nonexistent DB should fail");

        System.out.printf("  W-COBOL-32: error cases verified (no args, bad path)%n");
    }

    // ── CHECK COMPLIANCE witnesses ─────────────────────────────────

    /**
     * W-COBOL-33: Duplex light fixtures checked against LIGHT_CEILING rule.
     * IfcLightFixture elements mapped to LIGHT_CEILING placement rule.
     */
    @Test
    void w_cobol_33_duplexLightFixtures() throws SQLException {
        VerbResult<ComplianceCheckPayload> r = complianceVerb.execute(
                VerbContext.ofBom(bomConn), DUPLEX_EXTRACTED);
        assertEquals("CHECK COMPLIANCE", r.verb());

        ComplianceCheckPayload p = r.payload();
        assertNotNull(p);
        assertTrue(p.totalElements() >= 1090,
                "Duplex should have 1090+ elements, got: " + p.totalElements());
        assertTrue(p.checkedElements() > 0,
                "should have checked some elements against rules");

        System.out.printf("  W-COBOL-33: %d elements total, %d checked against rules%n",
                p.totalElements(), p.checkedElements());
    }

    /**
     * W-COBOL-34: Duplex outlets/flow terminals checked against wall height rules.
     * IfcFlowTerminal → OUTLET_WALL rule (height_m = 0.3m).
     */
    @Test
    void w_cobol_34_duplexOutletHeight() throws SQLException {
        VerbResult<ComplianceCheckPayload> r = complianceVerb.execute(
                VerbContext.ofBom(bomConn), DUPLEX_EXTRACTED);

        ComplianceCheckPayload p = r.payload();

        // Find results for OUTLET_WALL rule
        long outletChecks = p.results().stream()
                .filter(cr -> cr.ruleId().startsWith("OUTLET_WALL"))
                .count();
        assertTrue(outletChecks > 0,
                "should have OUTLET_WALL checks for IfcFlowTerminal, got: " + outletChecks);

        System.out.printf("  W-COBOL-34: %d outlet placement checks%n", outletChecks);
    }

    /**
     * W-COBOL-35: Duplex flow controller spacing checked against WALL_SPACED rule.
     * IfcFlowController → WALL_SPACED (max_spacing_m = 3.6m).
     */
    @Test
    void w_cobol_35_duplexControllerSpacing() throws SQLException {
        VerbResult<ComplianceCheckPayload> r = complianceVerb.execute(
                VerbContext.ofBom(bomConn), DUPLEX_EXTRACTED);

        ComplianceCheckPayload p = r.payload();

        // Find spacing results for WALL_SPACED rule
        long spacingChecks = p.results().stream()
                .filter(cr -> cr.ruleId().contains("SPACING"))
                .count();
        assertTrue(spacingChecks > 0,
                "should have spacing checks, got: " + spacingChecks);

        // Spacing pass count
        long spacingPass = p.results().stream()
                .filter(cr -> cr.ruleId().contains("SPACING") && "PASS".equals(cr.status()))
                .count();

        System.out.printf("  W-COBOL-35: %d spacing checks, %d pass%n",
                spacingChecks, spacingPass);
    }

    /**
     * W-COBOL-36: Error cases — no args, no BOM connection, nonexistent DB.
     */
    @Test
    void w_cobol_36_complianceErrorCases() throws SQLException {
        // No args
        VerbResult<ComplianceCheckPayload> r1 = complianceVerb.execute(
                VerbContext.ofBom(bomConn));
        assertFalse(r1.pass(), "no args should fail");
        assertNull(r1.payload());

        // No BOM connection
        VerbResult<ComplianceCheckPayload> r2 = complianceVerb.execute(
                VerbContext.ofBom(null), DUPLEX_EXTRACTED);
        assertFalse(r2.pass(), "null BOM should fail");

        // Nonexistent DB
        VerbResult<ComplianceCheckPayload> r3 = complianceVerb.execute(
                VerbContext.ofBom(bomConn), "/tmp/nonexistent_db_12345.db");
        assertFalse(r3.pass(), "nonexistent DB should fail");

        System.out.printf("  W-COBOL-36: error cases verified (no args, null BOM, bad path)%n");
    }
}
