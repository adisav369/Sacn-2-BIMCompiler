package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * CLAMP &lt;element&gt; TO &lt;support&gt; WITH &lt;clamp_type&gt;
 *
 * <p>Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class ClampVerb implements Verb<ClampVerb.ClampPayload> {

    private static final String USAGE =
            "usage: CLAMP <element> TO <support> WITH <clamp_type>";

    @Override
    public String keyword() { return "CLAMP"; }

    @Override
    public VerbResult<ClampPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element  1:TO  2:support  3:WITH  4:clamp_type
        if (args.length < 5)
            return VerbResult.fail(keyword(), USAGE, null);

        String element = args[0];
        // args[1] = TO
        String support = args[2];
        // args[3] = WITH
        String clampType = args[4];

        ClampPayload payload = new ClampPayload(element, support, clampType);

        return VerbResult.ok(keyword(),
                String.format("CLAMP %s TO %s with %s", element, support, clampType),
                payload);
    }

    /** Payload carrying clamp result. */
    public record ClampPayload(String element, String support,
                                String clampType) {}
}
