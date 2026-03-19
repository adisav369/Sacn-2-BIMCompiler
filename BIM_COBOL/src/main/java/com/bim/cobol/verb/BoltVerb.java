package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * BOLT &lt;element_a&gt; TO &lt;element_b&gt; WITH &lt;bolt_spec&gt;
 *
 * <p>Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class BoltVerb implements Verb<BoltVerb.BoltPayload> {

    private static final String USAGE =
            "usage: BOLT <element_a> TO <element_b> WITH <bolt_spec>";

    @Override
    public String keyword() { return "BOLT"; }

    @Override
    public VerbResult<BoltPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element_a  1:TO  2:element_b  3:WITH  4:bolt_spec
        if (args.length < 5)
            return VerbResult.fail(keyword(), USAGE, null);

        String elementA = args[0];
        // args[1] = TO
        String elementB = args[2];
        // args[3] = WITH
        String boltSpec = args[4];

        BoltPayload payload = new BoltPayload(elementA, elementB, boltSpec);

        return VerbResult.ok(keyword(),
                String.format("BOLT %s TO %s with %s", elementA, elementB, boltSpec),
                payload);
    }

    /** Payload carrying bolt result. */
    public record BoltPayload(String elementA, String elementB,
                               String boltSpec) {}
}
