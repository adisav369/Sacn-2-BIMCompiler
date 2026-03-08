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
 * DESCRIBE BOM &lt;bom_id&gt; — structural breakdown of a BOM tree.
 *
 * <p>Walks the BOM tree and produces a hierarchical indented view showing
 * each level with type, role, product, and dimensions. Useful for
 * understanding BOM structure at a glance.
 *
 * <p>Examples:
 * <pre>
 * DESCRIBE BOM EB_SH
 * DESCRIBE BOM DUPLEX_SET_STD
 * </pre>
 *
 * <p>Read-only — no DB writes.
 */
public class DescribeBomVerb implements Verb<DescribeBomVerb.DescribeBomPayload> {

    private static final int MAX_DEPTH = 20;

    @Override
    public String keyword() { return "DESCRIBE BOM"; }

    @Override
    public VerbResult<DescribeBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: DESCRIBE BOM <bom_id>", null);

        String bomId = args[0];
        Connection conn = ctx.bomConn();

        MBOM root = MBOM.get(conn, bomId);
        if (root == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        List<TreeLine> tree = new ArrayList<>();
        tree.add(new TreeLine(0, bomId, "ROOT",
            root.getBomType(), root.getBomCategory(),
            root.getBomName(), root.getDocSubType()));

        int[] counts = new int[3]; // BUY, MAKE, PHANTOM
        int maxDepth = walkDescribe(conn, bomId, 1, tree, counts);

        DescribeBomPayload payload = new DescribeBomPayload(
            bomId, root.getBomName(), root.getBomType(), root.getBomCategory(),
            counts[0], counts[1], counts[2], maxDepth, tree);

        return VerbResult.ok(keyword(),
            String.format("DESCRIBE %s: %d BUY, %d MAKE, %d PHANTOM, depth=%d, %d nodes total",
                bomId, counts[0], counts[1], counts[2], maxDepth, tree.size()),
            payload);
    }

    private int walkDescribe(Connection conn, String bomId, int depth,
                              List<TreeLine> tree, int[] counts) throws SQLException {
        if (depth > MAX_DEPTH) return depth;

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int maxChildDepth = depth;

        for (MBOMLine line : children) {
            String type = line.getComponentType();
            if (type == null) type = "MAKE";

            String dimStr = "";
            if (line.getAllocatedWidthMm() > 0 || line.getAllocatedDepthMm() > 0) {
                dimStr = String.format(" [%dx%dx%d mm]",
                    line.getAllocatedWidthMm(),
                    line.getAllocatedDepthMm(),
                    line.getAllocatedHeightMm());
            }
            String offsetStr = "";
            if (line.getDx() != 0 || line.getDy() != 0 || line.getDz() != 0) {
                offsetStr = String.format(" @(%.3f,%.3f,%.3f)",
                    line.getDx(), line.getDy(), line.getDz());
            }

            tree.add(new TreeLine(depth,
                line.getChildProductId(), type,
                line.getRole(), line.getLocatorRef(),
                line.getChildNamePattern(),
                dimStr + offsetStr));

            switch (type) {
                case "BUY"     -> counts[0]++;
                case "MAKE"    -> {
                    counts[1]++;
                    if (line.getChildProductId() != null) {
                        MBOM childBom = MBOM.get(conn, line.getChildProductId());
                        if (childBom != null) {
                            int d = walkDescribe(conn, line.getChildProductId(),
                                depth + 1, tree, counts);
                            maxChildDepth = Math.max(maxChildDepth, d);
                        }
                    }
                }
                case "PHANTOM" -> counts[2]++;
            }
        }
        return maxChildDepth;
    }

    public record TreeLine(
        int depth, String productId, String componentType,
        String role, String qualifier, String namePattern, String detail
    ) {}

    public record DescribeBomPayload(
        String bomId, String bomName, String bomType, String bomCategory,
        int buyCount, int makeCount, int phantomCount,
        int maxDepth, List<TreeLine> tree
    ) {}
}
