package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.compiler.geometry.Point3D;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ALONG &lt;wall_face&gt; PLACE &lt;product&gt; COUNT &lt;n&gt; SPACING &lt;mm&gt;
 *
 * <p>Computes positions along a wall face at regular spacing.
 * Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class AlongVerb implements Verb<AlongVerb.AlongPayload> {

    private static final String USAGE =
            "usage: ALONG <wall_face> PLACE <product> COUNT <n> SPACING <mm>";

    @Override
    public String keyword() { return "ALONG"; }

    @Override
    public VerbResult<AlongPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:wall_face  1:PLACE  2:product  3:COUNT  4:n  5:SPACING  6:mm
        if (args.length < 7)
            return VerbResult.fail(keyword(), USAGE, null);

        String wallFace = args[0];
        // args[1] = PLACE
        String product = args[2];
        // args[3] = COUNT

        int count;
        double spacingMm;
        try {
            count = Integer.parseInt(args[4]);
            // args[5] = SPACING
            spacingMm = Double.parseDouble(args[6]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        if (count < 1)
            return VerbResult.fail(keyword(),
                    "COUNT must be >= 1, got " + count, null);
        if (spacingMm <= 0)
            return VerbResult.fail(keyword(),
                    "SPACING must be > 0, got " + spacingMm, null);

        // Compute positions along the wall face
        // Convention: NORTH_WALL → along X axis, EAST_WALL → along Y axis, etc.
        double stepM = spacingMm / 1000.0;
        List<Point3D> positions = new ArrayList<>(count);
        boolean alongY = wallFace.toUpperCase().contains("EAST")
                || wallFace.toUpperCase().contains("WEST");

        for (int i = 0; i < count; i++) {
            double offset = i * stepM;
            if (alongY) {
                positions.add(new Point3D(0, offset, 0));
            } else {
                positions.add(new Point3D(offset, 0, 0));
            }
        }

        AlongPayload payload = new AlongPayload(wallFace, product, count,
                spacingMm, positions);

        return VerbResult.ok(keyword(),
                String.format("ALONG %s: %d × %s @ %.0fmm spacing",
                        wallFace, count, product, spacingMm),
                payload);
    }

    /** Payload carrying along-wall result. */
    public record AlongPayload(String wallFace, String product,
                                int count, double spacingMm,
                                List<Point3D> positions) {}
}
