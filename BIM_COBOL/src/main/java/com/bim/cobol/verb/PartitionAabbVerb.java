package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PARTITION AABB &lt;width&gt; &lt;depth&gt; ROOMS &lt;cat1&gt; [cat2...]
 *
 * <p>Utility verb. Pure geometry — no DB writes. Strip-packs rooms
 * left-to-right within the given AABB. Queries
 * {@link MBOM#findBestFitAnyOwner} per category to get actual dims.
 *
 * <p>Grammar:
 * <pre>
 *   PARTITION AABB 10000 8000 ROOMS LI BD KT BT
 * </pre>
 */
public class PartitionAabbVerb implements Verb<PartitionAabbVerb.PartitionPayload> {

    @Override
    public String keyword() { return "PARTITION AABB"; }

    @Override
    public VerbResult<PartitionPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: PARTITION AABB <width> <depth> ROOMS <cat1> [cat2...]
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: PARTITION AABB <width> <depth> ROOMS <cat1> [cat2...]", null);

        int widthMm, depthMm;
        try {
            widthMm = Integer.parseInt(args[0]);
            depthMm = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(), "invalid dimension: " + e.getMessage(), null);
        }

        if (!"ROOMS".equalsIgnoreCase(args[2]))
            return VerbResult.fail(keyword(),
                "expected ROOMS keyword, got: " + args[2], null);

        List<String> categories = new ArrayList<>();
        for (int i = 3; i < args.length; i++)
            categories.add(args[i].toUpperCase());

        if (categories.isEmpty())
            return VerbResult.fail(keyword(), "at least one room category required", null);

        Connection conn = ctx.bomConn();

        // Strip-pack left-to-right: each room gets full depth, allocated width from best-fit
        List<Slot> slots = new ArrayList<>();
        int usedWidth = 0;

        for (String cat : categories) {
            int remainingW = widthMm - usedWidth;
            if (remainingW <= 0) {
                slots.add(new Slot(cat, null, 0, 0, usedWidth, 0));
                continue;
            }

            // Find best-fit BOM for this category within remaining space
            // Height is unconstrained for partitioning (use large value)
            MBOM bestFit = MBOM.findBestFitAnyOwner(conn, cat,
                remainingW, depthMm, Integer.MAX_VALUE);

            if (bestFit == null) {
                // No fit — record slot with zero allocation
                slots.add(new Slot(cat, null, 0, 0, usedWidth, 0));
                continue;
            }

            // Get actual dims from the best-fit BOM's children
            int[] childSpace = MBOM.computeTotalChildSpace(conn, bestFit.getValue());
            int allocW = childSpace[0] > 0 ? childSpace[0] : remainingW;
            int allocD = childSpace[1] > 0 ? childSpace[1] : depthMm;

            slots.add(new Slot(cat, bestFit.getValue(), allocW, allocD, usedWidth, 0));
            usedWidth += allocW;
        }

        double wastePercent = widthMm > 0
            ? 100.0 * (widthMm - usedWidth) / widthMm
            : 0.0;

        return VerbResult.ok(keyword(),
            String.format("PARTITION AABB %dx%d → %d slots, %.1f%% waste",
                widthMm, depthMm, slots.size(), wastePercent),
            new PartitionPayload(slots, wastePercent));
    }

    public record Slot(
        String category, String bestFitBomId,
        int allocW, int allocD,
        int offsetX, int offsetY
    ) {}

    public record PartitionPayload(
        List<Slot> slots, double wastePercent
    ) {}
}
