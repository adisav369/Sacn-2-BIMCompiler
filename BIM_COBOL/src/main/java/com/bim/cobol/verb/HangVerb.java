package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * HANG &lt;element&gt; FROM &lt;surface&gt; WITH &lt;hanger_type&gt; SPACING &lt;mm&gt;
 *
 * <p>Validates hanger spacing per code and computes hanger count.
 * Purely parametric — no DB access.
 *
 * <p>Compliance: hanger spacing &le; 1200mm (SMACNA duct support standard).
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class HangVerb implements Verb<HangVerb.HangPayload> {

    /** SMACNA maximum hanger spacing (mm). */
    static final double MAX_HANGER_SPACING_MM = 1200.0;

    private static final String USAGE =
            "usage: HANG <element> FROM <surface> WITH <hanger_type> SPACING <mm>";

    @Override
    public String keyword() { return "HANG"; }

    @Override
    public VerbResult<HangPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element  1:FROM  2:surface  3:WITH  4:hanger_type  5:SPACING  6:mm
        if (args.length < 7)
            return VerbResult.fail(keyword(), USAGE, null);

        String element = args[0];
        // args[1] = FROM
        String surface = args[2];
        // args[3] = WITH
        String hangerType = args[4];
        // args[5] = SPACING

        double spacingMm;
        try {
            spacingMm = Double.parseDouble(args[6]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        if (spacingMm <= 0)
            return VerbResult.fail(keyword(),
                    "SPACING must be > 0, got " + spacingMm, null);

        // Compute hanger count (assume element length from element name is not available;
        // count is derived from a nominal run length encoded in a follow-up LENGTH arg,
        // but for the parametric verb we report count as ceil(1 + 0) = minimum 2 hangers
        // unless a LENGTH arg is provided after SPACING)
        double lengthMm = 0;
        for (int i = 7; i < args.length - 1; i++) {
            if ("LENGTH".equals(args[i])) {
                try {
                    lengthMm = Double.parseDouble(args[i + 1]);
                } catch (NumberFormatException ignored) { /* skip */ }
            }
        }
        int hangerCount = lengthMm > 0
                ? (int) Math.ceil(lengthMm / spacingMm) + 1
                : 2; // minimum 2 hangers (start + end)

        boolean spacingCompliant = spacingMm <= MAX_HANGER_SPACING_MM;

        HangPayload payload = new HangPayload(element, surface, hangerType,
                spacingMm, hangerCount);

        String status = spacingCompliant ? "PASS" : "FAIL";
        String summary = String.format(
                "HANG %s FROM %s: %s @ %.0fmm spacing, %d hangers, spacing %s",
                element, surface, hangerType, spacingMm, hangerCount, status);

        return spacingCompliant
                ? VerbResult.ok(keyword(), summary, payload)
                : VerbResult.fail(keyword(), summary, payload);
    }

    /** Payload carrying hang result. */
    public record HangPayload(String element, String surface,
                               String hangerType, double spacingMm,
                               int hangerCount) {}
}
