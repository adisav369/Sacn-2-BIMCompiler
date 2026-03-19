package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * CORNER &lt;face_a&gt; &lt;face_b&gt; PLACE &lt;product&gt;
 *
 * <p>Computes position at the intersection of two wall faces.
 * Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class CornerVerb implements Verb<CornerVerb.CornerPayload> {

    private static final String USAGE =
            "usage: CORNER <face_a> <face_b> PLACE <product>";

    @Override
    public String keyword() { return "CORNER"; }

    @Override
    public VerbResult<CornerPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:face_a  1:face_b  2:PLACE  3:product
        if (args.length < 4)
            return VerbResult.fail(keyword(), USAGE, null);

        String faceA = args[0];
        String faceB = args[1];
        // args[2] = PLACE
        String product = args[3];

        // Compute corner position based on face naming convention
        // NORTH = maxY, SOUTH = 0, EAST = maxX, WEST = 0
        double posX = computeAxis(faceA, faceB, "EAST", "WEST");
        double posY = computeAxis(faceA, faceB, "NORTH", "SOUTH");

        CornerPayload payload = new CornerPayload(faceA, faceB, product, posX, posY);

        return VerbResult.ok(keyword(),
                String.format("CORNER %s/%s: %s at (%.1f, %.1f)",
                        faceA, faceB, product, posX, posY),
                payload);
    }

    /**
     * Return 1.0 if either face contains the positive direction name,
     * 0.0 if either contains the negative direction name, else 0.5 (center).
     */
    private static double computeAxis(String faceA, String faceB,
                                       String posDir, String negDir) {
        String a = faceA.toUpperCase();
        String b = faceB.toUpperCase();
        if (a.contains(posDir) || b.contains(posDir)) return 1.0;
        if (a.contains(negDir) || b.contains(negDir)) return 0.0;
        return 0.5;
    }

    /** Payload carrying corner result. */
    public record CornerPayload(String faceA, String faceB,
                                 String product,
                                 double positionX, double positionY) {}
}
