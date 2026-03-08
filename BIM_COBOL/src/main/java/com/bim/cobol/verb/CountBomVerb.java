package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * COUNT BOM &lt;bom_id&gt; [RECURSIVE] — count BOM children, optionally recursing.
 *
 * <p>Without RECURSIVE, counts only direct children (level 1).
 * With RECURSIVE, walks the entire tree and counts all descendants.
 *
 * <p>Examples:
 * <pre>
 * COUNT BOM EB_SH
 * COUNT BOM EB_DX RECURSIVE
 * COUNT BOM DUPLEX_SET_STD RECURSIVE
 * </pre>
 *
 * <p>Read-only — no DB writes.
 */
public class CountBomVerb implements Verb<CountBomVerb.CountBomPayload> {

    @Override
    public String keyword() { return "COUNT BOM"; }

    @Override
    public VerbResult<CountBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: COUNT BOM <bom_id> [RECURSIVE]", null);

        String bomId = args[0];
        boolean recursive = args.length > 1
            && "RECURSIVE".equalsIgnoreCase(args[1]);

        Connection conn = ctx.bomConn();

        MBOM root = MBOM.get(conn, bomId);
        if (root == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        if (recursive) {
            int[] counts = new int[4]; // BUY, MAKE, PHANTOM, total
            int maxDepth = countRecursive(conn, bomId, 0, counts);

            CountBomPayload payload = new CountBomPayload(
                bomId, true, counts[3], counts[0], counts[1], counts[2],
                maxDepth);

            return VerbResult.ok(keyword(),
                String.format("COUNT %s RECURSIVE: %d total (%d BUY, %d MAKE, %d PHANTOM), depth=%d",
                    bomId, counts[3], counts[0], counts[1], counts[2], maxDepth),
                payload);
        } else {
            List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
            int buy = 0, make = 0, phantom = 0;
            for (MBOMLine line : children) {
                switch (line.getComponentType()) {
                    case "BUY"     -> buy++;
                    case "MAKE"    -> make++;
                    case "PHANTOM" -> phantom++;
                }
            }

            CountBomPayload payload = new CountBomPayload(
                bomId, false, children.size(), buy, make, phantom, 1);

            return VerbResult.ok(keyword(),
                String.format("COUNT %s: %d direct children (%d BUY, %d MAKE, %d PHANTOM)",
                    bomId, children.size(), buy, make, phantom),
                payload);
        }
    }

    private int countRecursive(Connection conn, String bomId, int depth,
                                int[] counts) throws SQLException {
        if (depth > 20) return depth;

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int maxChildDepth = depth;

        for (MBOMLine line : children) {
            counts[3]++; // total
            String type = line.getComponentType();
            if (type == null) type = "MAKE";

            switch (type) {
                case "BUY"     -> counts[0]++;
                case "MAKE"    -> {
                    counts[1]++;
                    if (line.getChildProductId() != null) {
                        MBOM childBom = MBOM.get(conn, line.getChildProductId());
                        if (childBom != null) {
                            int d = countRecursive(conn, line.getChildProductId(),
                                depth + 1, counts);
                            maxChildDepth = Math.max(maxChildDepth, d);
                        }
                    }
                }
                case "PHANTOM" -> counts[2]++;
            }
        }
        return maxChildDepth;
    }

    public record CountBomPayload(
        String bomId, boolean recursive,
        int totalCount, int buyCount, int makeCount, int phantomCount,
        int maxDepth
    ) {}
}
