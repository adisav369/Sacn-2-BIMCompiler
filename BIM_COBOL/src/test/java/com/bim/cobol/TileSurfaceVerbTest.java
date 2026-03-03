package com.bim.cobol;

import com.bim.cobol.geometry.TileGrid;
import com.bim.cobol.geometry.TileGrid.Panel;
import com.bim.cobol.geometry.TileGrid.TileResult;
import com.bim.cobol.verb.TileSurfaceVerb;
import com.bim.cobol.verb.TileSurfaceVerb.TilePayload;
import com.bim.compiler.geometry.Point3D;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for TILE SURFACE verb and TileGrid geometry.
 *
 * <p>No DB connection needed — purely parametric computation.
 * Real Terminal data for Z19: origin west (92.49, -42.16, 19.0), 15×294, step (0.495, 0.150).
 */
class TileSurfaceVerbTest {

    private final TileSurfaceVerb verb = new TileSurfaceVerb();

    /** W-COBOL-49: Single-panel TILE produces correct count and positions. */
    @Test
    void w_cobol_49_singlePanelTile() {
        // Z19 west panel: 15×294 = 4,410 tiles, step 495×150mm → 0.495×0.150m
        double originX = 92.49, originY = -42.16, originZ = 19.0;
        double stepXM = 0.495, stepYM = 0.150;
        int nx = 15, ny = 294;

        List<Point3D> positions = TileGrid.generate(originX, originY, originZ,
                nx, ny, stepXM, stepYM);

        assertEquals(4410, positions.size(), "15×294 = 4,410 tiles");

        // First position = origin
        Point3D first = positions.get(0);
        assertEquals(originX, first.x(), 1e-9, "first X = origin X");
        assertEquals(originY, first.y(), 1e-9, "first Y = origin Y");
        assertEquals(originZ, first.z(), 1e-9, "first Z = origin Z");

        // Last position = origin + (14×stepX, 293×stepY, 0)
        Point3D last = positions.get(positions.size() - 1);
        assertEquals(originX + 14 * stepXM, last.x(), 1e-9, "last X");
        assertEquals(originY + 293 * stepYM, last.y(), 1e-9, "last Y");
        assertEquals(originZ, last.z(), 1e-9, "last Z = origin Z");
    }

    /** W-COBOL-50: Multi-panel TileGrid produces additive positions with no overlap. */
    @Test
    void w_cobol_50_multiPanelNoOverlap() {
        // Three panels from real Z19 data (west / central / east)
        List<Panel> panels = List.of(
                new Panel("Z19_WEST",    92.49,  -42.16, 19.0, 15, 294, 0.495, 0.150),
                new Panel("Z19_CENTRAL", 100.00, -42.16, 19.0, 10, 196, 0.495, 0.150),
                new Panel("Z19_EAST",    105.00, -42.16, 19.0, 10, 100, 0.495, 0.150)
        );

        // Expected: 15×294 + 10×196 + 10×100 = 4410 + 1960 + 1000 = 7370
        TileResult result = TileGrid.generateMulti(panels);
        assertEquals(7370, result.totalCount(),
                "15×294 + 10×196 + 10×100 = 7,370 tiles");
        assertEquals(3, result.panels().size(), "3 panels");

        // No duplicate positions (Set size == list size)
        Set<Point3D> uniqueSet = new HashSet<>(result.positions());
        assertEquals(result.positions().size(), uniqueSet.size(),
                "no duplicate positions across panels");
    }

    /** W-COBOL-51: TILE via VerbRegistry dispatch parses correctly. */
    @Test
    void w_cobol_51_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "TILE SURFACE ROOF_DECK_Z19_WEST WITH PLATE_500x150x106"
                        + " ORIGIN 92.49 -42.16 19.0 GRID 15 294 STEP 495 150");

        assertTrue(r.pass(), "dispatch should pass: " + r.summary());
        assertEquals("TILE SURFACE", r.verb());

        TilePayload payload = (TilePayload) r.payload();
        assertEquals(4410, payload.totalCount(), "15×294 = 4,410");
        assertEquals("ROOF_DECK_Z19_WEST", payload.surfaceName());
        assertEquals("PLATE_500x150x106", payload.productName());
        assertEquals(15, payload.nx());
        assertEquals(294, payload.ny());
        assertEquals(495.0, payload.stepXMm(), 1e-9);
        assertEquals(150.0, payload.stepYMm(), 1e-9);
    }

    /** W-COBOL-52: Insufficient/invalid args return fail. */
    @Test
    void w_cobol_52_invalidArgs() throws SQLException {
        // Missing GRID and STEP → too few args
        VerbResult<TilePayload> r1 = verb.execute(VerbContext.ofBom(null),
                "ROOF", "WITH", "PLATE", "ORIGIN", "0", "0", "0");
        assertFalse(r1.pass(), "insufficient args should fail");
        assertTrue(r1.summary().contains("usage"), "summary contains usage: " + r1.summary());

        // nx = 0 → invalid
        VerbResult<TilePayload> r2 = verb.execute(VerbContext.ofBom(null),
                "ROOF", "WITH", "PLATE", "ORIGIN", "0", "0", "0",
                "GRID", "0", "5", "STEP", "500", "150");
        assertFalse(r2.pass(), "nx=0 should fail");
        assertTrue(r2.summary().contains(">= 1"), "summary mentions >= 1: " + r2.summary());

        // Negative step
        VerbResult<TilePayload> r3 = verb.execute(VerbContext.ofBom(null),
                "ROOF", "WITH", "PLATE", "ORIGIN", "0", "0", "0",
                "GRID", "3", "4", "STEP", "-500", "150");
        assertFalse(r3.pass(), "negative step should fail");
    }
}
