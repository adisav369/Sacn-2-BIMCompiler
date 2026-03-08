package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * STRIP ROOM &lt;bom_id&gt; [KEEP STRUCTURE] [ROLE &lt;role&gt;]
 *
 * <p>Level 1 convenience verb (§18.6). Removes children from a room SET,
 * leaving the container for repopulation.
 *
 * <p>No qualifier: removes all children (BOM becomes empty container).
 * KEEP STRUCTURE: removes BUY items only (keeps MAKE sub-assemblies).
 * ROLE filter: removes only children matching the given role.
 *
 * <p>Composes: REMOVE LINE (per matching child)
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class StripRoomVerb implements Verb<StripRoomVerb.StripPayload> {

    @Override
    public String keyword() { return "STRIP ROOM"; }

    @Override
    public VerbResult<StripPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: STRIP ROOM <bom_id> [KEEP STRUCTURE] [ROLE <role>]
        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: STRIP ROOM <bom_id> [KEEP STRUCTURE] [ROLE <role>]", null);

        String bomId = args[0];

        // Parse optional qualifiers
        boolean keepStructure = false;
        String roleFilter = null;

        for (int i = 1; i < args.length; i++) {
            String token = args[i].toUpperCase();
            if ("KEEP".equals(token) && i + 1 < args.length
                    && "STRUCTURE".equalsIgnoreCase(args[i + 1])) {
                keepStructure = true;
                i++;
            } else if ("ROLE".equals(token) && i + 1 < args.length) {
                roleFilter = args[i + 1];
                i++;
            }
        }

        Connection conn = ctx.bomConn();

        // Validate target BOM exists
        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int removedCount = 0;

        for (MBOMLine line : children) {
            boolean shouldRemove;

            if (roleFilter != null) {
                // ROLE mode: remove only matching role
                shouldRemove = roleFilter.equalsIgnoreCase(line.getRole());
            } else if (keepStructure) {
                // KEEP STRUCTURE: remove BUY items only (keep MAKE sub-assemblies)
                shouldRemove = !"MAKE".equals(line.getComponentType());
            } else {
                // No qualifier: remove all
                shouldRemove = true;
            }

            if (shouldRemove) {
                line.delete();
                removedCount++;
            }
        }

        int remainingCount = children.size() - removedCount;

        String qualifier = keepStructure ? " KEEP STRUCTURE"
            : roleFilter != null ? " ROLE " + roleFilter
            : "";

        return VerbResult.ok(keyword(),
            String.format("STRIP ROOM %s%s: removed=%d, remaining=%d",
                bomId, qualifier, removedCount, remainingCount),
            new StripPayload(bomId, removedCount, remainingCount));
    }

    public record StripPayload(
        String bomId, int removedCount, int remainingCount
    ) {}
}
