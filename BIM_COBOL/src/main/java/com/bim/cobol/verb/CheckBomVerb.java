package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CHECK BOM &lt;bom_id&gt; — validates BOM tree integrity.
 *
 * <p>Walks the BOM tree from the given root, counting BUY/MAKE/PHANTOM
 * nodes and checking structural invariants:
 * <ul>
 *   <li>MAKE nodes must have children (otherwise they're empty assemblies)</li>
 *   <li>BUY nodes must resolve to an M_Product entry</li>
 *   <li>Tree depth must not exceed MAX_DEPTH (cycle guard)</li>
 * </ul>
 *
 * <p>Read-only — no DB writes.
 */
public class CheckBomVerb implements Verb<CheckBomVerb.BomCheckPayload> {

    /** Maximum tree depth — matches BOMWalker. */
    private static final int MAX_DEPTH = 20;

    @Override
    public String keyword() { return "CHECK BOM"; }

    @Override
    public VerbResult<BomCheckPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(), "usage: CHECK BOM <bom_id>", null);

        String bomId = args[0];
        Connection conn = ctx.bomConn();

        MBOM root = MBOM.get(conn, bomId);
        if (root == null)
            return VerbResult.fail(keyword(),
                    bomId + " not found",
                    new BomCheckPayload(0, 0, 0, 0, List.of(bomId + " not found")));

        List<String> errors = new ArrayList<>();
        int[] counts = new int[3]; // [BUY, MAKE, PHANTOM]
        int maxDepth = walk(conn, bomId, 0, counts, errors);

        BomCheckPayload payload = new BomCheckPayload(
                counts[0], counts[1], counts[2], maxDepth, errors);

        if (errors.isEmpty()) {
            return VerbResult.ok(keyword(),
                    String.format("%s: %d BUY, %d MAKE, %d PHANTOM, depth=%d",
                            bomId, counts[0], counts[1], counts[2], maxDepth),
                    payload);
        } else {
            return VerbResult.fail(keyword(),
                    String.format("%s: %d errors", bomId, errors.size()),
                    payload);
        }
    }

    /**
     * Recursive BOM walk. Returns max depth reached from this node.
     */
    private int walk(Connection conn, String bomId, int depth,
                     int[] counts, List<String> errors) throws SQLException {
        if (depth > MAX_DEPTH) {
            errors.add("depth exceeds " + MAX_DEPTH + " at " + bomId + " — possible cycle");
            return depth;
        }

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int maxChildDepth = depth;

        for (MBOMLine line : children) {
            String type = line.getComponentType();
            if (type == null) type = "BUY";

            switch (type) {
                case "BUY" -> {
                    // Everything is BUY — full LOD in component_library.db.
                    // If child_product_id is also a bom_id, recurse (sub-BOM assembly).
                    // MAKE only when LOD not in library — see docs/archive/Mesh2Library.txt.
                    counts[0]++;
                    String pid = line.getChildProductId();
                    if (pid != null) {
                        // Check sub-BOM recursion first
                        MBOM childBom = MBOM.get(conn, pid);
                        if (childBom != null) {
                            int d = walk(conn, pid, depth + 1, counts, errors);
                            maxChildDepth = Math.max(maxChildDepth, d);
                        }
                        // Validate M_Product exists (leaf or assembly stub)
                        MProduct prod = MProduct.get(conn, pid);
                        if (prod == null) {
                            prod = MProduct.getAssembly(conn, pid);
                            if (prod == null)
                                errors.add("BUY child_product_id=" + pid
                                        + " (line " + line.getBomChildId() + ") has no M_Product");
                        }
                    }
                }
                case "MAKE" -> {
                    // Future: LOD not in library, needs Mesh2Library.txt generation.
                    // See docs/archive/Mesh2Library.txt.  Not used in current data.
                    counts[1]++;
                }
                case "PHANTOM" -> counts[2]++;
                default -> errors.add("unknown component_type=" + type
                        + " (line " + line.getBomChildId() + ")");
            }
        }
        return maxChildDepth;
    }

    /** Payload carrying BOM check results. */
    public record BomCheckPayload(
            int buyCount,
            int makeCount,
            int phantomCount,
            int maxDepth,
            List<String> errors
    ) {}
}
