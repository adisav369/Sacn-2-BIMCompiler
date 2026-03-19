package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;

/**
 * MOUNT &lt;element&gt; ON &lt;surface&gt; WITH &lt;bracket_type&gt; CLEARANCE &lt;mm&gt;
 *
 * <p>Validates clearance around element.
 * Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class MountVerb implements Verb<MountVerb.MountPayload> {

    private static final String USAGE =
            "usage: MOUNT <element> ON <surface> WITH <bracket_type> CLEARANCE <mm>";

    @Override
    public String keyword() { return "MOUNT"; }

    @Override
    public VerbResult<MountPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element  1:ON  2:surface  3:WITH  4:bracket_type  5:CLEARANCE  6:mm
        if (args.length < 7)
            return VerbResult.fail(keyword(), USAGE, null);

        String element = args[0];
        // args[1] = ON
        String surface = args[2];
        // args[3] = WITH
        String bracketType = args[4];
        // args[5] = CLEARANCE

        double clearanceMm;
        try {
            clearanceMm = Double.parseDouble(args[6]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        if (clearanceMm < 0)
            return VerbResult.fail(keyword(),
                    "CLEARANCE must be >= 0, got " + clearanceMm, null);

        MountPayload payload = new MountPayload(element, surface, bracketType, clearanceMm);

        return VerbResult.ok(keyword(),
                String.format("MOUNT %s ON %s with %s, clearance %.0fmm",
                        element, surface, bracketType, clearanceMm),
                payload);
    }

    /** Payload carrying mount result. */
    public record MountPayload(String element, String surface,
                                String bracketType, double clearanceMm) {}
}
