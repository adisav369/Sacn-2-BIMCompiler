package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * WELD &lt;element_a&gt; TO &lt;element_b&gt; WITH &lt;weld_spec&gt;
 *
 * <p>Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class WeldVerb implements Verb<WeldVerb.WeldPayload> {

    private static final String USAGE =
            "usage: WELD <element_a> TO <element_b> WITH <weld_spec>";

    @Override
    public String keyword() { return "WELD"; }

    @Override
    public VerbResult<WeldPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element_a  1:TO  2:element_b  3:WITH  4:weld_spec
        if (args.length < 5)
            return VerbResult.fail(keyword(), USAGE, null);

        String elementA = args[0];
        // args[1] = TO
        String elementB = args[2];
        // args[3] = WITH
        String weldSpec = args[4];

        WeldPayload payload = new WeldPayload(elementA, elementB, weldSpec);

        return VerbResult.ok(keyword(),
                String.format("WELD %s TO %s with %s", elementA, elementB, weldSpec),
                payload);
    }

    /** Payload carrying weld result. */
    public record WeldPayload(String elementA, String elementB,
                               String weldSpec) {}
}
