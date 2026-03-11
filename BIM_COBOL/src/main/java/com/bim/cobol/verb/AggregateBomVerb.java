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
 * AGGREGATE BOM &lt;bom_id&gt; BY &lt;dimension&gt; — grouped BOM summary.
 *
 * <p>Walks the BOM tree and groups children by a dimension, computing
 * COUNT, SUM (allocated space), and unique products per group.
 *
 * <p>Supported dimensions:
 * <ul>
 *   <li>{@code component_type} — BUY/MAKE/PHANTOM breakdown</li>
 *   <li>{@code role} — by functional role (KITCHEN, BEDROOM, etc.)</li>
 *   <li>{@code locator_ref} — by spatial locator</li>
 *   <li>{@code bom_level} — by tree depth</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 * AGGREGATE BOM EB_DX BY component_type
 * AGGREGATE BOM EB_SH BY role
 * AGGREGATE BOM DUPLEX_SET_STD BY locator_ref
 * </pre>
 *
 * <p>Read-only — no DB writes.
 */
public class AggregateBomVerb implements Verb<AggregateBomVerb.AggregateBomPayload> {

    @Override
    public String keyword() { return "AGGREGATE BOM"; }

    @Override
    public VerbResult<AggregateBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 3 || !"BY".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "usage: AGGREGATE BOM <bom_id> BY <dimension>", null);

        String bomId = args[0];
        String dimension = args[2].toLowerCase();
        Connection conn = ctx.bomConn();

        MBOM root = MBOM.get(conn, bomId);
        if (root == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Walk tree and collect groups
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        int totalLines = walkAggregate(conn, bomId, 0, dimension, groups);

        // Convert to payload
        List<GroupResult> results = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            GroupAccumulator acc = entry.getValue();
            results.add(new GroupResult(
                entry.getKey(), acc.count, acc.uniqueProducts.size(),
                acc.sumWidthMm, acc.sumDepthMm, acc.sumHeightMm));
        }

        // Sort by count descending
        results.sort(Comparator.comparingInt(GroupResult::count).reversed());

        AggregateBomPayload payload = new AggregateBomPayload(
            bomId, dimension, totalLines, results.size(), results);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("AGGREGATE %s BY %s: %d lines → %d groups",
            bomId, dimension, totalLines, results.size()));
        for (GroupResult g : results) {
            summary.append(String.format(" | %s=%d", g.groupKey(), g.count()));
        }

        return VerbResult.ok(keyword(), summary.toString(), payload);
    }

    private int walkAggregate(Connection conn, String bomId, int level,
                               String dimension, Map<String, GroupAccumulator> groups)
            throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int count = 0;

        for (MBOMLine line : children) {
            String key = extractGroupKey(line, dimension, level);
            if (key == null) key = "(null)";

            groups.computeIfAbsent(key, k -> new GroupAccumulator())
                .add(line);
            count++;

            // Recurse into sub-BOMs (tree structure, not component_type).
            // component_type is not a decision field — currently all BUY.
            // Future: MAKE = LOD created on-the-fly via Mesh2Library.txt.
            if (line.getChildProductId() != null) {
                MBOM childBom = MBOM.get(conn, line.getChildProductId());
                if (childBom != null) {
                    count += walkAggregate(conn, line.getChildProductId(),
                        level + 1, dimension, groups);
                }
            }
        }
        return count;
    }

    private String extractGroupKey(MBOMLine line, String dimension, int level) {
        return switch (dimension) {
            case "component_type" -> line.getComponentType();
            case "role"           -> line.getRole();
            case "locator_ref"    -> line.getLocatorRef();
            case "bom_level"      -> "L" + level;
            case "storey"         -> line.getStorey();
            case "orientation"    -> line.getOrientation();
            default               -> line.getComponentType();
        };
    }

    private static class GroupAccumulator {
        int count = 0;
        Set<String> uniqueProducts = new HashSet<>();
        long sumWidthMm = 0;
        long sumDepthMm = 0;
        long sumHeightMm = 0;

        void add(MBOMLine line) {
            count++;
            if (line.getChildProductId() != null)
                uniqueProducts.add(line.getChildProductId());
            sumWidthMm += line.getAllocatedWidthMm();
            sumDepthMm += line.getAllocatedDepthMm();
            sumHeightMm += line.getAllocatedHeightMm();
        }
    }

    public record GroupResult(
        String groupKey, int count, int uniqueProducts,
        long sumWidthMm, long sumDepthMm, long sumHeightMm
    ) {}

    public record AggregateBomPayload(
        String bomId, String dimension,
        int totalLines, int groupCount,
        List<GroupResult> groups
    ) {}
}
