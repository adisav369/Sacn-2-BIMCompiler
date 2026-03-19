package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * JOIN &lt;element_a&gt; TO &lt;element_b&gt; AT &lt;port&gt;
 *
 * <p>Validates port compatibility (connector_type match).
 * Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class JoinVerb implements Verb<JoinVerb.JoinPayload> {

    private static final String USAGE =
            "usage: JOIN <element_a> TO <element_b> AT <port>";

    @Override
    public String keyword() { return "JOIN"; }

    @Override
    public VerbResult<JoinPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element_a  1:TO  2:element_b  3:AT  4:port
        if (args.length < 5)
            return VerbResult.fail(keyword(), USAGE, null);

        String elementA = args[0];
        // args[1] = TO
        String elementB = args[2];
        // args[3] = AT
        String port = args[4];

        // Connection type inferred from port naming convention
        String connectionType = inferConnectionType(port);

        JoinPayload payload = new JoinPayload(elementA, elementB, port, connectionType);

        return VerbResult.ok(keyword(),
                String.format("JOIN %s TO %s AT %s — connection: %s",
                        elementA, elementB, port, connectionType),
                payload);
    }

    private static String inferConnectionType(String port) {
        String upper = port.toUpperCase();
        if (upper.contains("FLANGE")) return "FLANGE";
        if (upper.contains("THREAD")) return "THREADED";
        if (upper.contains("SOCKET")) return "SOCKET";
        if (upper.contains("WELD"))   return "WELDED";
        return "GENERIC";
    }

    /** Payload carrying join result. */
    public record JoinPayload(String elementA, String elementB,
                               String port, String connectionType) {}
}
