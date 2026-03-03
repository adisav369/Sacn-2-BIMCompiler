package com.bim.cobol;

import com.bim.cobol.geometry.LinearArray;
import com.bim.cobol.geometry.LinearArray.ArrayResult;
import com.bim.cobol.geometry.LinearArray.Direction;
import com.bim.cobol.verb.ArrayVerb;
import com.bim.cobol.verb.ArrayVerb.ArrayPayload;
import com.bim.compiler.geometry.Point3D;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for ARRAY verb and LinearArray geometry.
 *
 * <p>No DB connection needed — purely parametric computation.
 */
class ArrayVerbTest {

    private final ArrayVerb verb = new ArrayVerb();

    /** W-COBOL-53: ARRAY computes correct rebar count and positions. */
    @Test
    void w_cobol_53_correctCount() {
        // LENGTH 6000 SPACING 150 COVER 40 → count = floor((6000 - 80) / 150) + 1 = 40
        ArrayResult result = LinearArray.generate(0, 0, 0, 6000, 150, 40, Direction.X);

        assertEquals(40, result.count(), "floor((6000-80)/150)+1 = 40");
        assertEquals(40, result.positions().size(), "positions count matches");

        // First position at cover = 40mm = 0.040m
        Point3D first = result.positions().get(0);
        assertEquals(0.040, first.x(), 1e-9, "first at cover offset");
        assertEquals(0.0, first.y(), 1e-9);
        assertEquals(0.0, first.z(), 1e-9);

        // Last position at cover + 39×spacing = 40 + 39×150 = 5890mm = 5.890m
        Point3D last = result.positions().get(result.positions().size() - 1);
        assertEquals(5.890, last.x(), 1e-9, "last at 5890mm");

        // Monotonically increasing
        for (int i = 1; i < result.positions().size(); i++) {
            assertTrue(result.positions().get(i).x() > result.positions().get(i - 1).x(),
                    "position " + i + " > position " + (i - 1));
        }
    }

    /** W-COBOL-54: Cover compliance check detects under-minimum. */
    @Test
    void w_cobol_54_coverComplianceFail() throws SQLException {
        // COVER 20 → below 25mm BS 8110 minimum
        VerbResult<ArrayPayload> r = verb.execute(VerbContext.ofBom(null),
                "SLAB_001", "WITH", "REBAR_T16",
                "LENGTH", "6000", "SPACING", "150", "COVER", "20");

        // Verb returns fail because compliance failed
        assertFalse(r.pass(), "should fail due to cover non-compliance");

        ArrayPayload p = r.payload();
        assertNotNull(p, "payload should be present even on compliance fail");
        assertFalse(p.coverCompliant(), "cover 20mm < 25mm → not compliant");
        assertTrue(p.spacingCompliant(), "spacing 150mm <= 300mm → compliant");
        assertTrue(r.summary().contains("cover FAIL"), "summary: " + r.summary());
    }

    /** W-COBOL-55: ARRAY via VerbRegistry dispatch with DIRECTION Y. */
    @Test
    void w_cobol_55_registryDispatchDirectionY() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "ARRAY SLAB_001 WITH REBAR_T16 LENGTH 3000 SPACING 150 COVER 40 DIRECTION Y");

        assertTrue(r.pass(), "dispatch should pass: " + r.summary());
        assertEquals("ARRAY", r.verb());

        ArrayPayload payload = (ArrayPayload) r.payload();
        // count = floor((3000 - 80) / 150) + 1 = floor(19.467) + 1 = 20
        assertEquals(20, payload.count(), "floor((3000-80)/150)+1 = 20");

        // All positions should have x=0 (arrayed along Y)
        for (Point3D pos : payload.positions()) {
            assertEquals(0.0, pos.x(), 1e-9, "x should be 0 for Y direction");
            assertEquals(0.0, pos.z(), 1e-9, "z should be 0 for Y direction");
        }
        // First Y at cover offset
        assertEquals(0.040, payload.positions().get(0).y(), 1e-9,
                "first Y at cover 40mm");
    }

    /** W-COBOL-56: Insufficient args return fail. */
    @Test
    void w_cobol_56_invalidArgs() throws SQLException {
        // Missing LENGTH, SPACING, COVER
        VerbResult<ArrayPayload> r1 = verb.execute(VerbContext.ofBom(null),
                "SLAB_001", "WITH", "REBAR_T16");
        assertFalse(r1.pass(), "insufficient args should fail");
        assertTrue(r1.summary().contains("usage"), "summary contains usage: " + r1.summary());

        // Missing COVER value
        VerbResult<ArrayPayload> r2 = verb.execute(VerbContext.ofBom(null),
                "SLAB_001", "WITH", "REBAR_T16",
                "LENGTH", "6000", "SPACING", "150");
        assertFalse(r2.pass(), "missing COVER should fail");
    }
}
