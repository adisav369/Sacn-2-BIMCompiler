package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.geometry.TileGrid;
import com.bim.compiler.geometry.Point3D;

import java.sql.SQLException;
import java.util.List;

/**
 * TILE SURFACE &lt;surface_name&gt; WITH &lt;product_name&gt; ORIGIN &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *     GRID &lt;nx&gt; &lt;ny&gt; STEP &lt;dx_mm&gt; &lt;dy_mm&gt;
 *
 * <p>Computes tile positions on a rectangular grid. Purely parametric — no DB access.
 * 19 TILE formulas replace 33,324 flat plate coordinates in the Terminal model.
 */
public class TileSurfaceVerb implements Verb<TileSurfaceVerb.TilePayload> {

    private static final String USAGE =
            "usage: TILE SURFACE <surface> WITH <product> ORIGIN <x> <y> <z>"
                    + " GRID <nx> <ny> STEP <dx_mm> <dy_mm>";

    @Override
    public String keyword() { return "TILE SURFACE"; }

    @Override
    public VerbResult<TilePayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:surface  1:WITH  2:product  3:ORIGIN  4:x  5:y  6:z
        // 7:GRID  8:nx  9:ny  10:STEP  11:dx_mm  12:dy_mm
        if (args.length < 13)
            return VerbResult.fail(keyword(), USAGE, null);

        String surfaceName = args[0];
        // args[1] = WITH
        String productName = args[2];
        // args[3] = ORIGIN

        double originX, originY, originZ;
        int nx, ny;
        double stepXMm, stepYMm;
        try {
            originX = Double.parseDouble(args[4]);
            originY = Double.parseDouble(args[5]);
            originZ = Double.parseDouble(args[6]);
            // args[7] = GRID
            nx = Integer.parseInt(args[8]);
            ny = Integer.parseInt(args[9]);
            // args[10] = STEP
            stepXMm = Double.parseDouble(args[11]);
            stepYMm = Double.parseDouble(args[12]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        if (nx < 1 || ny < 1)
            return VerbResult.fail(keyword(),
                    "GRID dimensions must be >= 1, got " + nx + "x" + ny, null);
        if (stepXMm <= 0 || stepYMm <= 0)
            return VerbResult.fail(keyword(),
                    "STEP must be > 0, got " + stepXMm + "x" + stepYMm, null);

        // Convert mm → meters
        double stepXM = stepXMm / 1000.0;
        double stepYM = stepYMm / 1000.0;

        List<Point3D> positions = TileGrid.generate(
                originX, originY, originZ, nx, ny, stepXM, stepYM);

        int totalCount = positions.size();
        TilePayload payload = new TilePayload(
                surfaceName, productName, nx, ny, stepXMm, stepYMm,
                totalCount, positions);

        return VerbResult.ok(keyword(),
                String.format("%s: %d\u00d7%d = %,d tiles, step %.0f\u00d7%.0fmm",
                        surfaceName, nx, ny, totalCount, stepXMm, stepYMm),
                payload);
    }

    /** Payload carrying tile grid results. */
    public record TilePayload(String surfaceName, String productName,
                               int nx, int ny, double stepXMm, double stepYMm,
                               int totalCount, List<Point3D> positions) {}
}
