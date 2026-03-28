package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * VALIDATE AABB &lt;w&gt; &lt;d&gt; &lt;h&gt; FOR &lt;category&gt;
 *
 * <p>§18.5 Utility verb. Checks if an AABB is viable for a given BOM category.
 * Catches impossible rooms before creation. Read-only — does not mutate anything.
 *
 * <p>Looks for existing BOMs in the category whose SpaceSize fits within
 * the requested AABB. Returns candidate count and best-fit BOM id.
 */
public class ValidateAabbVerb implements Verb<ValidateAabbVerb.ValidationPayload> {

    @Override
    public String keyword() { return "VALIDATE AABB"; }

    @Override
    public VerbResult<ValidationPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: VALIDATE AABB <w> <d> <h> FOR <category>
        if (args.length < 5)
            return VerbResult.fail(keyword(),
                "usage: VALIDATE AABB <width_mm> <depth_mm> <height_mm> FOR <category>", null);

        int widthMm, depthMm, heightMm;
        try {
            widthMm = Integer.parseInt(args[0]);
            depthMm = Integer.parseInt(args[1]);
            heightMm = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(), "invalid dimension value", null);
        }

        if (!"FOR".equalsIgnoreCase(args[3]))
            return VerbResult.fail(keyword(), "expected FOR after dimensions", null);

        String category = args[4].toUpperCase();

        Connection conn = ctx.bomConn();

        // Find all BOMs in this category
        List<MBOM> allInCategory = MBOM.getByCategory(conn, category);
        if (allInCategory.isEmpty())
            return VerbResult.fail(keyword(),
                "no BOMs found for category: " + category,
                new ValidationPayload(false, category, 0, null, widthMm, depthMm, heightMm,
                    "unknown category or empty catalog"));

        // Count how many fit within the requested AABB
        int candidateCount = 0;
        MBOM bestFit = null;
        long bestVolume = -1;

        for (MBOM bom : allInCategory) {
            int[] space = MBOM.computeTotalChildSpace(conn, bom.getValue());
            int tw = space[0], td = space[1], th = space[2];

            // Zero-space BOMs (empty containers) always fit
            boolean fits = (tw == 0 && td == 0 && th == 0)
                || (tw <= widthMm && td <= depthMm && th <= heightMm);

            if (fits) {
                candidateCount++;
                long vol = (long) tw * td * th;
                if (vol > bestVolume) {
                    bestVolume = vol;
                    bestFit = bom;
                }
            }
        }

        String bestFitId = bestFit != null ? bestFit.getValue() : null;

        if (candidateCount == 0) {
            return VerbResult.fail(keyword(),
                String.format("no %s BOMs fit within %dx%dx%d mm (%d checked)",
                    category, widthMm, depthMm, heightMm, allInCategory.size()),
                new ValidationPayload(false, category, 0, null,
                    widthMm, depthMm, heightMm,
                    "all " + allInCategory.size() + " BOMs exceed requested AABB"));
        }

        return VerbResult.ok(keyword(),
            String.format("VALIDATE %s %dx%dx%d: OK — %d candidates, best=%s",
                category, widthMm, depthMm, heightMm, candidateCount, bestFitId),
            new ValidationPayload(true, category, candidateCount, bestFitId,
                widthMm, depthMm, heightMm, null));
    }

    public record ValidationPayload(
        boolean valid, String category, int candidateCount, String bestFitBomId,
        int widthMm, int depthMm, int heightMm, String message
    ) {}
}
