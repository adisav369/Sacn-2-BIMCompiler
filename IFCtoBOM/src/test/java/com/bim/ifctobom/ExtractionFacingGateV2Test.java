package com.bim.ifctobom;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABSTRACT ORIENTATION witness (SPATIAL_DEPENDENCY_GRAPH.md §DOCTRINE / RESUME_GIGO_FACING_TEST). The
 * orientation classifier is now CLASS-AGNOSTIC: there is no "does this class have a front?" whitelist
 * (the deleted hasFront(Wall/Plate/Furniture) + DIRECTIONAL_ROLE_TOKENS) — every element's facing IS its
 * captured world yaw, and the only question is "is a real transform captured?". This is what lets the
 * same code orient a bridge girder or a shopfloor jig with zero new rules.
 *
 * <p>Each test names the issue it proves:
 * <ul>
 *   <li>W-FACING-ABSTRACT-1: orientation is class-agnostic — a SLAB (old hasFront=false) with a captured
 *       yaw now PASSES carrying that yaw; class membership no longer gates facing.</li>
 *   <li>W-FACING-ABSTRACT-2: an element with NO captured front returns an HONEST NULL (chair AND wall) —
 *       never a hard fail, never a fabricated rotation_rule=0.</li>
 *   <li>W-FACING-ABSTRACT-3: a real captured front passes through, carrying that front.</li>
 *   <li>W-FACING-ABSTRACT-4: the AABB EW/NS proxy is gone — a long wall with no captured yaw returns null
 *       (the classifier never invents EW/NS).</li>
 *   <li>W-FACING-ABSTRACT-5: the v2 classifier ships ON by default.</li>
 * </ul>
 */
class ExtractionFacingGateV2Test {

    /** 13-arg ctor → rotationZ=null = NO captured placement (the uncaptured state). */
    private static ExtractionPopulator.RawElement chairUncaptured() {
        return new ExtractionPopulator.RawElement(
                "IfcFurniture", "Dining_Chair:285330", "Ground Floor", "ARC",
                0, 0.5, 0, 0.5, 0, 0.9, null, null, "GUID-CHAIR-1");
    }
    private static ExtractionPopulator.RawElement wallUncaptured() {
        return new ExtractionPopulator.RawElement(
                "IfcWall", "Basic Wall:Ext:285330", "Ground Floor", "ARC",
                0, 9.0, 0, 0.1, 0, 2.7, null, null, "GUID-WALL-1");   // dx >> dy → v1 would say "EW"
    }
    /** 14-arg ctor → a captured yaw (here a SLAB — old hasFront would have said "no front"). */
    private static ExtractionPopulator.RawElement slabFacing(double yaw) {
        return new ExtractionPopulator.RawElement(
                "IfcSlab", "Floor Slab:285", "Ground Floor", "STR",
                0, 8.0, 0, 6.0, 0, 0.2, null, null, "GUID-SLAB-1", yaw);
    }

    @Test @DisplayName("W-FACING-ABSTRACT-1: class-agnostic — a slab with a captured yaw passes (no whitelist)")
    void classAgnosticFacing() {
        ExtractionPopulator.FRONT_SOURCE = defaultSource();
        String front = ExtractionPopulator.classifyOrientationV2(
                slabFacing(Math.PI), "IfcSlab", "Floor Slab");
        assertEquals(String.valueOf(Math.PI), front,
                "a slab is no longer exempt — its captured yaw flows through; facing is not class-gated");
    }

    @Test @DisplayName("W-FACING-ABSTRACT-2: no captured front → honest NULL (chair AND wall), never a fake 0")
    void uncapturedReturnsNull() {
        ExtractionPopulator.FRONT_SOURCE = defaultSource();
        assertNull(ExtractionPopulator.classifyOrientationV2(chairUncaptured(), "IfcFurniture", "Dining_Chair"),
                "uncaptured chair → null orientation (no rotation_rule), not a hard fail and not a fabricated 0");
        assertNull(ExtractionPopulator.classifyOrientationV2(wallUncaptured(), "IfcWall", "Basic Wall"),
                "uncaptured wall → null too — walls get no special hard-fail treatment");
    }

    @Test @DisplayName("W-FACING-ABSTRACT-3: a real captured front passes through, carrying that front")
    void passWhenFrontCaptured() {
        String prev = setFrontSource(e -> "S");
        try {
            assertEquals("S", ExtractionPopulator.classifyOrientationV2(wallUncaptured(), "IfcWall", "Basic Wall"),
                    "an injected captured front passes, carrying that front");
        } finally { restoreFrontSource(prev); }
    }

    @Test @DisplayName("W-FACING-ABSTRACT-4: AABB EW/NS is gone — uncaptured long wall returns null")
    void ewNsGone() {
        ExtractionPopulator.FRONT_SOURCE = defaultSource();
        assertNull(ExtractionPopulator.classifyOrientationV2(wallUncaptured(), "IfcWall", "Basic Wall"),
                "the long wall v1 called 'EW' yields null — the classifier never invents an EW/NS facing");
    }

    @Test @DisplayName("W-FACING-ABSTRACT-5: the v2 classifier ships ON by default")
    void gateOnByDefault() {
        assertTrue(ExtractionPopulator.FACING_GATE_V2,
                "v2 (captured-yaw, class-agnostic) is the default orientation path");
    }

    // ── helpers to swap the injectable front source around a test
    private static String token = "x";
    private static java.util.function.Function<ExtractionPopulator.RawElement, String> saved;
    private static java.util.function.Function<ExtractionPopulator.RawElement, String> defaultSource() {
        return e -> e.rotationZ() == null ? null : String.valueOf(e.rotationZ());
    }
    private static String setFrontSource(java.util.function.Function<ExtractionPopulator.RawElement, String> src) {
        saved = ExtractionPopulator.FRONT_SOURCE;
        ExtractionPopulator.FRONT_SOURCE = src;
        return token;
    }
    private static void restoreFrontSource(String t) {
        ExtractionPopulator.FRONT_SOURCE = saved;
    }

    @AfterEach void resetFrontSource() {
        ExtractionPopulator.FRONT_SOURCE = defaultSource();
    }
}
