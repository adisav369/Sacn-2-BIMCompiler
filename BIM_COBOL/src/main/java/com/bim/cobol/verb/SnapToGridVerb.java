package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SNAP TO GRID (x,y) SPACING &lt;mm&gt;
 * SNAP TO GRID AABB &lt;w&gt; &lt;d&gt; [&lt;h&gt;] SPACING &lt;mm&gt;
 *
 * <p>§18.5 Utility verb. Aligns arbitrary coordinates or AABB dimensions
 * to a structural grid. Essential for Bonsai: the user drags freely,
 * the verb snaps to the nearest grid intersection.
 *
 * <p>Snap always rounds UP to ensure the AABB encloses the drawn box.
 * Read-only — does not mutate anything.
 */
public class SnapToGridVerb implements Verb<SnapToGridVerb.GridSnapPayload> {

    @Override
    public String keyword() { return "SNAP TO GRID"; }

    @Override
    public VerbResult<GridSnapPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 2)
            return VerbResult.fail(keyword(),
                "usage: SNAP TO GRID (x,y) SPACING <mm> | SNAP TO GRID AABB <w> <d> [<h>] SPACING <mm>", null);

        if ("AABB".equalsIgnoreCase(args[0])) {
            return snapAabb(args);
        } else {
            return snapPoint(args);
        }
    }

    /**
     * SNAP TO GRID AABB &lt;w&gt; &lt;d&gt; [&lt;h&gt;] SPACING &lt;mm&gt;
     */
    private VerbResult<GridSnapPayload> snapAabb(String... args) {
        // Parse: AABB <w> <d> [<h>] SPACING <mm>
        int spacing = 0;
        int w = 0, d = 0, h = 0;
        boolean hasHeight = false;

        try {
            w = Integer.parseInt(args[1]);
            d = Integer.parseInt(args[2]);

            // Check if next is SPACING or a height value
            int spacingIdx = 3;
            if (args.length > 3 && !"SPACING".equalsIgnoreCase(args[3])) {
                h = Integer.parseInt(args[3]);
                hasHeight = true;
                spacingIdx = 4;
            }

            if (spacingIdx >= args.length || !"SPACING".equalsIgnoreCase(args[spacingIdx]))
                return VerbResult.fail(keyword(), "expected SPACING", null);
            spacing = Integer.parseInt(args[spacingIdx + 1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return VerbResult.fail(keyword(),
                "invalid number in AABB dimensions or SPACING", null);
        }

        if (spacing <= 0)
            return VerbResult.fail(keyword(), "SPACING must be > 0", null);

        List<String> adjustments = new ArrayList<>();
        int sw = snapUp(w, spacing);
        int sd = snapUp(d, spacing);
        int sh = hasHeight ? snapUp(h, spacing) : 0;

        if (sw != w) adjustments.add("WIDTH " + w + "→" + sw);
        if (sd != d) adjustments.add("DEPTH " + d + "→" + sd);
        if (hasHeight && sh != h) adjustments.add("HEIGHT " + h + "→" + sh);

        GridSnapPayload payload = new GridSnapPayload(
            sw, sd, sh, spacing, adjustments);

        return VerbResult.ok(keyword(),
            String.format("SNAP AABB %dx%d%s → %dx%d%s @%dmm%s",
                w, d, hasHeight ? "x" + h : "",
                sw, sd, hasHeight ? "x" + sh : "",
                spacing,
                adjustments.isEmpty() ? " (no change)" : " adjusted: " + adjustments),
            payload);
    }

    /**
     * SNAP TO GRID (x,y) SPACING &lt;mm&gt;
     */
    private VerbResult<GridSnapPayload> snapPoint(String... args) {
        double[] point = ExtractAabbVerb.parsePoint(args[0]);
        if (point == null || point.length < 2)
            return VerbResult.fail(keyword(),
                "invalid point format — use (x,y) or (x,y,z)", null);

        int spacingIdx = 1;
        if ("SPACING".equalsIgnoreCase(args[spacingIdx])) spacingIdx = 1;
        else return VerbResult.fail(keyword(), "expected SPACING", null);

        int spacing;
        try {
            spacing = Integer.parseInt(args[spacingIdx + 1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return VerbResult.fail(keyword(), "invalid SPACING value", null);
        }

        if (spacing <= 0)
            return VerbResult.fail(keyword(), "SPACING must be > 0", null);

        // Snap point coords (metres → mm → snap → mm)
        int xMm = (int) Math.round(point[0] * 1000);
        int yMm = (int) Math.round(point[1] * 1000);
        int sx = snapUp(xMm, spacing);
        int sy = snapUp(yMm, spacing);

        List<String> adjustments = new ArrayList<>();
        if (sx != xMm) adjustments.add("X " + xMm + "→" + sx);
        if (sy != yMm) adjustments.add("Y " + yMm + "→" + sy);

        GridSnapPayload payload = new GridSnapPayload(
            sx, sy, 0, spacing, adjustments);

        return VerbResult.ok(keyword(),
            String.format("SNAP (%d,%d) → (%d,%d) @%dmm%s",
                xMm, yMm, sx, sy, spacing,
                adjustments.isEmpty() ? " (no change)" : " adjusted: " + adjustments),
            payload);
    }

    /** Snap up to next grid multiple (ceiling). */
    static int snapUp(int value, int spacing) {
        if (value % spacing == 0) return value;
        return ((value / spacing) + 1) * spacing;
    }

    public record GridSnapPayload(
        int snappedWidthMm, int snappedDepthMm, int snappedHeightMm,
        int gridSpacingMm, List<String> adjustments
    ) {}
}
