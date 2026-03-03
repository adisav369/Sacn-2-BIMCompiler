package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.geometry.LinearArray;
import com.bim.cobol.geometry.LinearArray.ArrayResult;
import com.bim.cobol.geometry.LinearArray.Direction;
import com.bim.compiler.geometry.Point3D;

import java.sql.SQLException;
import java.util.List;

/**
 * ARRAY &lt;host_element&gt; WITH &lt;product_name&gt; LENGTH &lt;mm&gt; SPACING &lt;mm&gt;
 *     COVER &lt;mm&gt; [DIRECTION X|Y|Z]
 *
 * <p>Computes rebar/element positions along a single axis. Purely parametric — no DB access.
 * Compliance checks: cover &ge; 25mm (BS 8110), spacing &le; 300mm (EC2).
 */
public class ArrayVerb implements Verb<ArrayVerb.ArrayPayload> {

    /** BS 8110 minimum concrete cover (mm). */
    private static final double MIN_COVER_MM = 25.0;
    /** EC2 maximum bar spacing (mm). */
    private static final double MAX_SPACING_MM = 300.0;

    private static final String USAGE =
            "usage: ARRAY <host> WITH <product> LENGTH <mm> SPACING <mm> COVER <mm>"
                    + " [DIRECTION X|Y|Z]";

    @Override
    public String keyword() { return "ARRAY"; }

    @Override
    public VerbResult<ArrayPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:host  1:WITH  2:product  3:LENGTH  4:mm  5:SPACING  6:mm  7:COVER  8:mm
        // Optional: 9:DIRECTION  10:X|Y|Z
        if (args.length < 9)
            return VerbResult.fail(keyword(), USAGE, null);

        String hostElement = args[0];
        // args[1] = WITH
        String productName = args[2];
        // args[3] = LENGTH

        double lengthMm, spacingMm, coverMm;
        try {
            lengthMm = Double.parseDouble(args[4]);
            // args[5] = SPACING
            spacingMm = Double.parseDouble(args[6]);
            // args[7] = COVER
            coverMm = Double.parseDouble(args[8]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        // Optional DIRECTION (default X)
        Direction direction = Direction.X;
        for (int i = 9; i < args.length - 1; i++) {
            if ("DIRECTION".equals(args[i])) {
                try {
                    direction = Direction.valueOf(args[i + 1].toUpperCase());
                } catch (IllegalArgumentException e) {
                    return VerbResult.fail(keyword(),
                            "invalid direction: " + args[i + 1] + " (expected X, Y, or Z)", null);
                }
            }
        }

        ArrayResult result = LinearArray.generate(0, 0, 0,
                lengthMm, spacingMm, coverMm, direction);

        // Compliance checks
        boolean coverCompliant = coverMm >= MIN_COVER_MM;
        boolean spacingCompliant = spacingMm <= MAX_SPACING_MM;

        ArrayPayload payload = new ArrayPayload(
                hostElement, productName, result.count(),
                spacingMm, coverMm, lengthMm,
                coverCompliant, spacingCompliant, result.positions());

        String coverStatus = coverCompliant ? "PASS" : "FAIL";
        String spacingStatus = spacingCompliant ? "PASS" : "FAIL";
        boolean allPass = coverCompliant && spacingCompliant;

        String summary = String.format(
                "%s: %d bars @ %.0fmm spacing, cover %.0fmm, cover %s, spacing %s",
                hostElement, result.count(), spacingMm, coverMm,
                coverStatus, spacingStatus);

        return allPass
                ? VerbResult.ok(keyword(), summary, payload)
                : VerbResult.fail(keyword(), summary, payload);
    }

    /** Payload carrying array results and compliance status. */
    public record ArrayPayload(String hostElement, String productName,
                                int count, double spacingMm, double coverMm,
                                double hostLengthMm, boolean coverCompliant,
                                boolean spacingCompliant, List<Point3D> positions) {}
}
