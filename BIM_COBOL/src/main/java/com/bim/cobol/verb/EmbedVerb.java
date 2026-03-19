package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * EMBED &lt;element&gt; IN &lt;host&gt; COVER &lt;depth_mm&gt;
 *
 * <p>Validates cover &ge; 25mm (ACI 318 minimum concrete cover).
 * Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class EmbedVerb implements Verb<EmbedVerb.EmbedPayload> {

    /** ACI 318 minimum concrete cover (mm). */
    static final double MIN_COVER_MM = 25.0;

    private static final String USAGE =
            "usage: EMBED <element> IN <host> COVER <depth_mm>";

    @Override
    public String keyword() { return "EMBED"; }

    @Override
    public VerbResult<EmbedPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element  1:IN  2:host  3:COVER  4:depth_mm
        if (args.length < 5)
            return VerbResult.fail(keyword(), USAGE, null);

        String element = args[0];
        // args[1] = IN
        String host = args[2];
        // args[3] = COVER

        double coverMm;
        try {
            coverMm = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        boolean coverCompliant = coverMm >= MIN_COVER_MM;

        EmbedPayload payload = new EmbedPayload(element, host, coverMm, coverCompliant);

        String status = coverCompliant ? "PASS" : "FAIL";
        String summary = String.format(
                "EMBED %s IN %s: cover %.0fmm, cover %s",
                element, host, coverMm, status);

        return coverCompliant
                ? VerbResult.ok(keyword(), summary, payload)
                : VerbResult.fail(keyword(), summary, payload);
    }

    /** Payload carrying embed result. */
    public record EmbedPayload(String element, String host,
                                double coverMm, boolean coverCompliant) {}
}
