package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionPopulator.FacingNotCapturedException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FACING GATE v2 (WIDENED) witness — GIGO at the SOURCE (IFCtoBOM): any element with a FRONT may
 * NOT reach the BOM with no real captured front. v1 silently wrote rotation_rule="0" for furniture
 * (and a 180°-flipped wall passed unnoticed because the AABB EW/NS proxy can't tell inward from
 * outward), so a chair faced one way and a wall's brick could face into the room. v2 refuses, no
 * fallback — and now covers WALLS too, not just directional furniture.
 *
 * <p>Each test names the issue it proves:
 * <ul>
 *   <li>W-FACING-GATE-1: "has a front" now includes WALLS (room-side vs outside) and directional
 *       furniture; slabs/columns/plain tables have no horizontal front (up-only / yaw-free).</li>
 *   <li>W-FACING-GATE-2: a fronted element with NO captured front HARD-FAILS — chair AND wall.</li>
 *   <li>W-FACING-GATE-3: when a REAL front is captured, it passes through (pass path via source).</li>
 *   <li>W-FACING-GATE-4: the AABB EW/NS proxy NO LONGER counts — a long wall v1 called "EW"
 *       hard-fails under v2 (EW/NS can't distinguish inward from outward).</li>
 *   <li>W-FACING-GATE-5: the gate ships ON by default (pipeline enforces GIGO).</li>
 * </ul>
 */
class ExtractionFacingGateV2Test {

    private static ExtractionPopulator.RawElement chair() {
        return new ExtractionPopulator.RawElement(
                "IfcFurniture", "Dining_Chair:285330", "Ground Floor", "ARC",
                0, 0.5, 0, 0.5, 0, 0.9, null, null, "GUID-CHAIR-1");
    }
    private static ExtractionPopulator.RawElement wallEW() {
        return new ExtractionPopulator.RawElement(
                "IfcWall", "Basic Wall:Ext:285330", "Ground Floor", "ARC",
                0, 9.0, 0, 0.1, 0, 2.7, null, null, "GUID-WALL-1");   // dx >> dy → v1 would say "EW"
    }
    private static ExtractionPopulator.RawElement slab() {
        return new ExtractionPopulator.RawElement(
                "IfcSlab", "Floor Slab:285", "Ground Floor", "STR",
                0, 8.0, 0, 6.0, 0, 0.2, null, null, "GUID-SLAB-1");
    }

    @Test @DisplayName("W-FACING-GATE-1: walls now have a front too; slabs/plain tables do not")
    void frontMembership() {
        assertTrue (ExtractionPopulator.hasFront("IfcWall", "Basic Wall"),
                "a wall has a front: room-side vs outside");
        assertTrue (ExtractionPopulator.hasFront("IfcFurniture", "Dining_Chair"),
                "a chair has a look-direction");
        assertFalse(ExtractionPopulator.hasFront("IfcSlab", "Floor Slab"),
                "a slab is up-only / yaw-free — no horizontal front here");
        assertFalse(ExtractionPopulator.hasFront("IfcFurniture", "Dining_Table"),
                "round vs rectangular table needs geometry (Fix-1) — not required here");
    }

    @Test @DisplayName("W-FACING-GATE-2: fronted element with no captured front HARD-FAILS (chair AND wall)")
    void hardFailOnUncapturedFront() {
        String prev = setFrontSource(e -> null);   // nothing captured (the real state today)
        try {
            FacingNotCapturedException c = assertThrows(FacingNotCapturedException.class,
                    () -> ExtractionPopulator.classifyOrientationV2(chair(), "IfcFurniture", "Dining_Chair"));
            assertTrue(c.getMessage().contains("GUID-CHAIR-1"), "names the offending chair");
            FacingNotCapturedException w = assertThrows(FacingNotCapturedException.class,
                    () -> ExtractionPopulator.classifyOrientationV2(wallEW(), "IfcWall", "Basic Wall"),
                    "a wall with no captured front must hard-fail too — walls are not exempt");
            assertTrue(w.getMessage().contains("GUID-WALL-1"), "names the offending wall");
        } finally { restoreFrontSource(prev); }
    }

    @Test @DisplayName("W-FACING-GATE-3: a real captured front passes through")
    void passWhenFrontCaptured() {
        String prev = setFrontSource(e -> "S");     // Fix-1 will supply this from IFC/mesh
        try {
            assertEquals("S", ExtractionPopulator.classifyOrientationV2(wallEW(), "IfcWall", "Basic Wall"),
                    "a wall with a captured front passes, carrying that front");
        } finally { restoreFrontSource(prev); }
    }

    @Test @DisplayName("W-FACING-GATE-4: AABB EW/NS no longer counts — long wall hard-fails")
    void ewNsNoLongerCounts() {
        String prev = setFrontSource(e -> null);
        try {
            assertThrows(FacingNotCapturedException.class,
                    () -> ExtractionPopulator.classifyOrientationV2(wallEW(), "IfcWall", "Basic Wall"),
                    "the long wall v1 classified 'EW' now hard-fails: EW/NS can't tell inward from outward");
        } finally { restoreFrontSource(prev); }
    }

    @Test @DisplayName("W-FACING-GATE-5: the gate ships ON by default (pipeline enforces GIGO)")
    void gateOnByDefault() {
        assertTrue(ExtractionPopulator.FACING_GATE_V2,
                "v2 facing gate is enabled by default — the pipeline hard-fails uncaptured fronts");
    }

    // ── helpers to swap the injectable front source around a test
    private static String token = "x";
    private static java.util.function.Function<ExtractionPopulator.RawElement, String> saved;
    private static String setFrontSource(java.util.function.Function<ExtractionPopulator.RawElement, String> src) {
        saved = ExtractionPopulator.FRONT_SOURCE;
        ExtractionPopulator.FRONT_SOURCE = src;
        return token;
    }
    private static void restoreFrontSource(String t) {
        ExtractionPopulator.FRONT_SOURCE = saved;
    }
}
