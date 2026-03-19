package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;
import java.util.Set;

/**
 * ATTACH &lt;element&gt; TO &lt;host_surface&gt; FACE &lt;face&gt; OFFSET &lt;mm&gt;
 *
 * <p>Validates host_type (CEILING/WALL/FLOOR) and attachment_face compatibility.
 * Purely parametric — no DB access.
 */
// Implementing BIM_COBOL.md §4.6 — Witness: W-COBOL-JOIN
public class AttachVerb implements Verb<AttachVerb.AttachPayload> {

    private static final String USAGE =
            "usage: ATTACH <element> TO <host_surface> FACE <face> OFFSET <mm>";

    private static final Set<String> VALID_HOSTS =
            Set.of("CEILING", "WALL", "FLOOR");

    private static final Set<String> VALID_FACES =
            Set.of("TOP", "BOTTOM", "FRONT", "BACK", "LEFT", "RIGHT");

    @Override
    public String keyword() { return "ATTACH"; }

    @Override
    public VerbResult<AttachPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Expected args (after keyword stripped):
        // 0:element  1:TO  2:host_surface  3:FACE  4:face  5:OFFSET  6:mm
        if (args.length < 7)
            return VerbResult.fail(keyword(), USAGE, null);

        String element = args[0];
        // args[1] = TO
        String hostSurface = args[2];
        // args[3] = FACE
        String face = args[4].toUpperCase();
        // args[5] = OFFSET

        double offsetMm;
        try {
            offsetMm = Double.parseDouble(args[6]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(),
                    "invalid number: " + e.getMessage() + " — " + USAGE, null);
        }

        // Validate host type from surface name
        String hostType = extractHostType(hostSurface);
        if (!VALID_HOSTS.contains(hostType))
            return VerbResult.fail(keyword(),
                    "invalid host type: " + hostType
                            + " (expected CEILING, WALL, or FLOOR)", null);

        if (!VALID_FACES.contains(face))
            return VerbResult.fail(keyword(),
                    "invalid face: " + face
                            + " (expected TOP, BOTTOM, FRONT, BACK, LEFT, RIGHT)", null);

        AttachPayload payload = new AttachPayload(element, hostSurface, face, offsetMm);

        return VerbResult.ok(keyword(),
                String.format("ATTACH %s TO %s face %s offset %.0fmm",
                        element, hostSurface, face, offsetMm),
                payload);
    }

    /** Extract host type from surface name (e.g. "CEILING_01" → "CEILING"). */
    private static String extractHostType(String surface) {
        String upper = surface.toUpperCase();
        for (String type : VALID_HOSTS) {
            if (upper.startsWith(type)) return type;
        }
        return upper;
    }

    /** Payload carrying attach result. */
    public record AttachPayload(String element, String hostSurface,
                                 String attachmentFace, double offsetMm) {}
}
