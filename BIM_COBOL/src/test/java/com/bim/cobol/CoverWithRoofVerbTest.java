package com.bim.cobol;

import com.bim.cobol.geometry.ValleyStitcher.ValleyResult;
import com.bim.cobol.verb.CoverWithRoofVerb;
import com.bim.cobol.verb.CoverWithRoofVerb.CompoundRoofPayload;
import com.bim.compiler.geometry.Point3D;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for COVER WITH COMPOUND_ROOF verb.
 *
 * <p>Each test is a numbered witness (W-COBOL-N) that proves
 * compound roof valley stitching against live DB data.
 */
class CoverWithRoofVerbTest {

    private static Connection bomConn;
    private static Connection compConn;
    private final CoverWithRoofVerb verb = new CoverWithRoofVerb();

    @BeforeAll
    static void open() throws SQLException {
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
        compConn = DriverManager.getConnection("jdbc:sqlite:library/component_library.db");
    }

    @AfterAll
    static void close() throws SQLException {
        if (bomConn != null) bomConn.close();
        if (compConn != null) compConn.close();
    }

    /** W-COBOL-5: HIP_ROOF_MY compound: pass=true, 1 subsidiary=GABLE_PORCH_MY, no violations. */
    @Test
    void w_cobol_5_compoundRoof() throws SQLException {
        VerbResult<CompoundRoofPayload> r = verb.execute(
                VerbContext.of(bomConn, compConn), "HIP_ROOF_MY");

        assertTrue(r.pass(), r.summary());
        assertEquals("COVER WITH COMPOUND_ROOF", r.verb());

        CompoundRoofPayload p = r.payload();
        assertEquals("HIP_ROOF_MY", p.primaryType());
        assertEquals(1, p.subsidiaryTypes().size(), "subsidiary count");
        assertEquals("GABLE_PORCH_MY", p.subsidiaryTypes().get(0));
        assertTrue(p.violations().isEmpty(), "violations: " + p.violations());
    }

    /** W-COBOL-6: Meeting point Z > 0, Z < 1.30 (below hip ridge 1.259m), on all 3 planes. */
    @Test
    void w_cobol_6_meetingPoint() throws SQLException {
        VerbResult<CompoundRoofPayload> r = verb.execute(
                VerbContext.of(bomConn, compConn), "HIP_ROOF_MY");

        assertTrue(r.pass(), r.summary());
        CompoundRoofPayload p = r.payload();
        assertFalse(p.valleys().isEmpty(), "should have valley results");

        ValleyResult v = p.valleys().get(0);
        Point3D meet = v.meetingPoint();
        assertTrue(meet.z() > 0, "meeting Z > 0: " + meet);
        assertTrue(meet.z() < 1.30,
                "meeting Z < 1.30 (below hip ridge 1.259m): " + meet);

        // Meeting point must lie on all 3 planes (validated by ValleyStitcher)
        assertTrue(v.isValid(), "valley violations: " + v.violations());
    }

    /** W-COBOL-7: Valley V-angle 30-150°, both valley lines descend (dir.z ≤ 0). */
    @Test
    void w_cobol_7_valleyAngle() throws SQLException {
        VerbResult<CompoundRoofPayload> r = verb.execute(
                VerbContext.of(bomConn, compConn), "HIP_ROOF_MY");

        assertTrue(r.pass(), r.summary());
        CompoundRoofPayload p = r.payload();
        ValleyResult v = p.valleys().get(0);

        double angle = v.meetingAngleDeg();
        assertTrue(angle >= 30 && angle <= 150,
                "V-angle " + String.format("%.1f", angle) + "° outside 30-150° range");

        assertTrue(v.westValley().direction().z() <= 0,
                "west valley should descend: " + v.westValley().direction());
        assertTrue(v.eastValley().direction().z() <= 0,
                "east valley should descend: " + v.eastValley().direction());
    }

    /** W-COBOL-8: Error cases: NONEXISTENT_ROOF→fail, null componentConn→fail, no args→fail. */
    @Test
    void w_cobol_8_errorCases() throws SQLException {
        // Nonexistent mesh type
        VerbResult<CompoundRoofPayload> r1 = verb.execute(
                VerbContext.of(bomConn, compConn), "NONEXISTENT_ROOF");
        assertFalse(r1.pass(), "nonexistent should fail");

        // Null componentConn
        VerbResult<CompoundRoofPayload> r2 = verb.execute(
                VerbContext.ofBom(bomConn), "HIP_ROOF_MY");
        assertFalse(r2.pass(), "null componentConn should fail");
        assertTrue(r2.summary().contains("componentConn"),
                "summary should mention componentConn: " + r2.summary());

        // No args
        VerbResult<CompoundRoofPayload> r3 = verb.execute(
                VerbContext.of(bomConn, compConn));
        assertFalse(r3.pass(), "no args should fail");
    }
}
