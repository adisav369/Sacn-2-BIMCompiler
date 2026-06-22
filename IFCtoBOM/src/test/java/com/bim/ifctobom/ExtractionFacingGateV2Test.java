package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionPopulator.FacingNotCapturedException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FACING GATE v2 witness — proves the GIGO hard-fail at the SOURCE (IFCtoBOM): a directional
 * element (one with a FRONT) may NOT reach the BOM with no captured facing. v1 silently wrote
 * rotation_rule="0" for furniture (classifyOrientation returns null → "0"), so a chair landed
 * with no front and faced one way on drop. v2 refuses, with no fallback.
 *
 * <p>Each test names the issue it proves:
 * <ul>
 *   <li>W-FACING-GATE-1: directionality is decided by "has a front", not by IFC class —
 *       a chair requires facing, a wall and a plain table do not.</li>
 *   <li>W-FACING-GATE-2: a directional element with NO captured facing HARD-FAILS (throws) —
 *       this is the GIGO gate; bad data cannot pass.</li>
 *   <li>W-FACING-GATE-3: the gate does NOT break walls — they still classify EW/NS by AABB.</li>
 *   <li>W-FACING-GATE-4: a non-directional box returns null (orientation N/A) — NOT a fake "0".</li>
 *   <li>W-FACING-GATE-5: the gate ships ON by default — the IFCtoBOM pipeline enforces GIGO,
 *       it is not an opt-in switch someone must remember to flip.</li>
 * </ul>
 */
class ExtractionFacingGateV2Test {

    // helpers — build the package-private RawElement
    private static ExtractionPopulator.RawElement chair() {
        return new ExtractionPopulator.RawElement(
                "IfcFurniture", "Dining_Chair:285330", "Ground Floor", "ARC",
                0, 0.5, 0, 0.5, 0, 0.9, null, null, "GUID-CHAIR-1");   // ~square footprint
    }
    private static ExtractionPopulator.RawElement plainTable() {
        return new ExtractionPopulator.RawElement(
                "IfcFurniture", "Dining_Table:285331", "Ground Floor", "ARC",
                0, 1.2, 0, 1.2, 0, 0.75, null, null, "GUID-TABLE-1");  // round/square table: no front
    }
    private static ExtractionPopulator.RawElement wallEW() {
        return new ExtractionPopulator.RawElement(
                "IfcWall", "Basic Wall:Ext:285330", "Ground Floor", "ARC",
                0, 9.0, 0, 0.1, 0, 2.7, null, null, "GUID-WALL-1");    // dx >> dy → EW
    }

    @Test @DisplayName("W-FACING-GATE-1: directionality = has a front (chair yes; wall, plain table no)")
    void directionalityByFront() {
        assertTrue (ExtractionPopulator.requiresFacing("IfcFurniture", "Dining_Chair"),
                "a chair has a front and requires facing");
        assertFalse(ExtractionPopulator.requiresFacing("IfcWall", "Basic Wall"),
                "a wall is not facing-directional (AABB EW/NS covers it)");
        assertFalse(ExtractionPopulator.requiresFacing("IfcFurniture", "Dining_Table"),
                "a plain table has no inherent front");
    }

    @Test @DisplayName("W-FACING-GATE-2: directional element with no captured facing HARD-FAILS")
    void hardFailOnUncapturedFacing() {
        boolean prev = ExtractionPopulator.FACING_GATE_V2;
        ExtractionPopulator.FACING_GATE_V2 = true;
        try {
            FacingNotCapturedException ex = assertThrows(FacingNotCapturedException.class,
                    () -> ExtractionPopulator.classifyOrientationV2(chair(), "IfcFurniture", "Dining_Chair"),
                    "a chair with no captured facing must hard-fail, not default to 0");
            assertTrue(ex.getMessage().contains("GUID-CHAIR-1"), "failure names the offending element");
            assertTrue(ex.getMessage().contains("GIGO hard fail"), "failure is the GIGO gate");
        } finally {
            ExtractionPopulator.FACING_GATE_V2 = prev;
        }
    }

    @Test @DisplayName("W-FACING-GATE-3: gate does not break walls — still EW by AABB")
    void wallsStillClassify() {
        assertEquals("EW", ExtractionPopulator.classifyOrientationV2(wallEW(), "IfcWall", "Basic Wall"),
                "a long wall classifies EW; the facing gate never touches it");
    }

    @Test @DisplayName("W-FACING-GATE-4: non-directional box returns null (not a fake 0)")
    void nonDirectionalReturnsNull() {
        assertNull(ExtractionPopulator.classifyOrientationV2(plainTable(), "IfcFurniture", "Dining_Table"),
                "a plain table has no orientation — null means N/A, never a disguised 0");
    }

    @Test @DisplayName("W-FACING-GATE-5: the gate ships ON by default (pipeline enforces GIGO)")
    void gateOnByDefault() {
        assertTrue(ExtractionPopulator.FACING_GATE_V2,
                "v2 facing gate is enabled by default — the IFCtoBOM pipeline hard-fails uncaptured "
                + "facing rather than relying on someone to flip a switch");
    }
}
