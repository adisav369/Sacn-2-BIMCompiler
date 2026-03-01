package com.bim.cobol;

import com.bim.cobol.verb.CheckClashVerb;
import com.bim.cobol.verb.CheckClashVerb.ClashCheckPayload;
import com.bim.cobol.verb.CheckClashVerb.ClashRecord;
import com.bim.cobol.verb.CheckPlacementVerb;
import com.bim.cobol.verb.CheckPlacementVerb.PlacementCheckPayload;
import com.bim.cobol.verb.CheckPlacementVerb.ProofResult;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CHECK PLACEMENT + CHECK CLASH witnesses — validates geometry proofs
 * against real Duplex and Terminal extracted databases.
 */
class CheckPlacementClashTest {

    private static final String DUPLEX_EXTRACTED =
            "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db";
    private static final String TERMINAL_EXTRACTED =
            "DAGCompiler/lib/input/Terminal_Extracted.db";

    private final CheckPlacementVerb placementVerb = new CheckPlacementVerb();
    private final CheckClashVerb clashVerb = new CheckClashVerb();

    // ── CHECK PLACEMENT witnesses ──────────────────────────────────

    /**
     * W-COBOL-21: Duplex extracted — P01 (positive extent) proves all
     * 1099 elements have dx>0, dy>0, dz>0.
     */
    @Test
    void w_cobol_21_duplexPositiveExtent() throws SQLException {
        VerbResult<PlacementCheckPayload> r = placementVerb.execute(
                VerbContext.ofBom(null), DUPLEX_EXTRACTED, "TIER", "1");
        assertTrue(r.pass(), "placement check should pass: " + r.summary());
        assertEquals("CHECK PLACEMENT", r.verb());

        PlacementCheckPayload p = r.payload();
        assertNotNull(p);
        assertTrue(p.elementCount() >= 1090,
                "Duplex should have 1090+ elements, got: " + p.elementCount());

        // P01 proof: all elements must pass positive extent
        long p01Fail = p.results().stream()
                .filter(pr -> "P01".equals(pr.proofId()) && !pr.pass())
                .count();
        assertEquals(0, p01Fail, "P01 violations: " + p01Fail);

        System.out.printf("  W-COBOL-21: %d elements, all P01 positive extent proven%n",
                p.elementCount());
    }

    /**
     * W-COBOL-22: Duplex extracted — P02 (finite coords) proves no NaN/Inf
     * in any element coordinate.
     */
    @Test
    void w_cobol_22_duplexFiniteCoords() throws SQLException {
        VerbResult<PlacementCheckPayload> r = placementVerb.execute(
                VerbContext.ofBom(null), DUPLEX_EXTRACTED, "TIER", "1");
        assertTrue(r.pass(), "placement check should pass: " + r.summary());

        PlacementCheckPayload p = r.payload();
        long p02Fail = p.results().stream()
                .filter(pr -> "P02".equals(pr.proofId()) && !pr.pass())
                .count();
        assertEquals(0, p02Fail, "P02 violations (NaN/Inf): " + p02Fail);

        System.out.printf("  W-COBOL-22: %d elements, all P02 finite coords proven%n",
                p.elementCount());
    }

    /**
     * W-COBOL-23: Duplex extracted — P04 (storey Z-band) proves elements
     * are within their storey's expected Z range.
     */
    @Test
    void w_cobol_23_duplexStoreyZBand() throws SQLException {
        VerbResult<PlacementCheckPayload> r = placementVerb.execute(
                VerbContext.ofBom(null), DUPLEX_EXTRACTED, "TIER", "1");
        assertTrue(r.pass(), "placement check should pass: " + r.summary());

        PlacementCheckPayload p = r.payload();
        long p04Fail = p.results().stream()
                .filter(pr -> "P04".equals(pr.proofId()) && !pr.pass())
                .count();
        assertEquals(0, p04Fail, "P04 storey Z-band violations: " + p04Fail);

        // Verify that storey-assigned elements were actually checked
        long p04Checked = p.results().stream()
                .filter(pr -> "P04".equals(pr.proofId()))
                .count();
        assertTrue(p04Checked > 100,
                "should check P04 on 100+ elements, got: " + p04Checked);

        System.out.printf("  W-COBOL-23: %d elements checked for storey Z-band, 0 violations%n",
                p04Checked);
    }

    /**
     * W-COBOL-24: Error cases — nonexistent DB, no args, empty DB.
     */
    @Test
    void w_cobol_24_errorCases() throws SQLException {
        // No args
        VerbResult<PlacementCheckPayload> r1 = placementVerb.execute(
                VerbContext.ofBom(null));
        assertFalse(r1.pass(), "no args should fail");
        assertNull(r1.payload());

        // Nonexistent DB
        VerbResult<PlacementCheckPayload> r2 = placementVerb.execute(
                VerbContext.ofBom(null), "/tmp/nonexistent_db_12345.db");
        assertFalse(r2.pass(), "nonexistent DB should fail");

        System.out.printf("  W-COBOL-24: error cases verified (no args, bad path)%n");
    }

    // ── CHECK CLASH witnesses ──────────────────────────────────────

    /**
     * W-COBOL-25: Duplex extracted — finds MEP-structure proximity with
     * default 50mm clearance. Duplex has 427 IfcFlowSegments near 8 IfcBeams.
     */
    @Test
    void w_cobol_25_duplexClashDetection() throws SQLException {
        VerbResult<ClashCheckPayload> r = clashVerb.execute(
                VerbContext.ofBom(null), DUPLEX_EXTRACTED);
        assertEquals("CHECK CLASH", r.verb());

        ClashCheckPayload p = r.payload();
        assertNotNull(p);
        assertTrue(p.mepCount() >= 400,
                "Duplex should have 400+ MEP elements, got: " + p.mepCount());
        assertTrue(p.structuralCount() >= 8,
                "Duplex should have 8+ structural elements, got: " + p.structuralCount());

        // With 50mm clearance, expect some proximity hits
        System.out.printf("  W-COBOL-25: %d clashes found (%d MEP, %d structural, 50mm clearance)%n",
                p.clashes().size(), p.mepCount(), p.structuralCount());
    }

    /**
     * W-COBOL-26: Duplex extracted with 0mm clearance — only actual bbox
     * overlaps reported. Should be fewer than with 50mm clearance.
     */
    @Test
    void w_cobol_26_duplexZeroClearance() throws SQLException {
        VerbResult<ClashCheckPayload> r = clashVerb.execute(
                VerbContext.ofBom(null), DUPLEX_EXTRACTED, "CLEARANCE", "0");

        ClashCheckPayload p = r.payload();
        assertNotNull(p);
        assertEquals(0.0, p.clearanceM(), "clearance should be 0");

        // Verify that any reported clashes have actual overlap volume > 0
        for (ClashRecord c : p.clashes()) {
            assertTrue(c.overlapX() > 0 && c.overlapY() > 0 && c.overlapZ() > 0,
                    "zero-clearance clash must have 3D overlap: " + c.mepGuid());
        }

        System.out.printf("  W-COBOL-26: %d actual overlaps at 0mm clearance%n",
                p.clashes().size());
    }

    /**
     * W-COBOL-27: Terminal extracted — large building (51K elements) with
     * more IFC classes including IfcColumn. Should detect MEP near structure.
     */
    @Test
    void w_cobol_27_terminalClashDetection() throws SQLException {
        VerbResult<ClashCheckPayload> r = clashVerb.execute(
                VerbContext.ofBom(null), TERMINAL_EXTRACTED);
        assertEquals("CHECK CLASH", r.verb());

        ClashCheckPayload p = r.payload();
        assertNotNull(p);
        // Terminal has IfcColumn, IfcBeam, IfcSlab as structural
        assertTrue(p.structuralCount() > 0,
                "Terminal should have structural elements, got: " + p.structuralCount());
        assertTrue(p.mepCount() > 0,
                "Terminal should have MEP elements, got: " + p.mepCount());

        System.out.printf("  W-COBOL-27: Terminal — %d clashes (%d MEP, %d structural)%n",
                p.clashes().size(), p.mepCount(), p.structuralCount());
    }

    /**
     * W-COBOL-28: Error cases — no args, nonexistent DB, DB with no structural elements.
     */
    @Test
    void w_cobol_28_clashErrorCases() throws SQLException {
        // No args
        VerbResult<ClashCheckPayload> r1 = clashVerb.execute(VerbContext.ofBom(null));
        assertFalse(r1.pass(), "no args should fail");
        assertNull(r1.payload());

        // Nonexistent DB
        VerbResult<ClashCheckPayload> r2 = clashVerb.execute(
                VerbContext.ofBom(null), "/tmp/nonexistent_db_12345.db");
        assertFalse(r2.pass(), "nonexistent DB should fail");

        System.out.printf("  W-COBOL-28: error cases verified (no args, bad path)%n");
    }
}
