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
 * REMOVE LINE FROM &lt;bom_id&gt; CHILD &lt;child&gt;
 * REMOVE LINE FROM &lt;bom_id&gt; ROLE &lt;role&gt;
 *
 * <p>Level 0 primitive (§18.4). Deletes one {@code m_bom_line} row
 * matching by child_product_id or role.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class RemoveLineVerb implements Verb<RemoveLineVerb.RemoveLinePayload> {

    @Override
    public String keyword() { return "REMOVE LINE"; }

    @Override
    public VerbResult<RemoveLinePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: REMOVE LINE FROM <bom_id> CHILD <child> | ROLE <role>
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: REMOVE LINE FROM <bom_id> CHILD <child> | ROLE <role>", null);

        if (!"FROM".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(), "expected FROM after REMOVE LINE", null);

        String bomId = args[1];
        String matchType = args[2].toUpperCase();
        String matchValue = args[3];

        if (!"CHILD".equals(matchType) && !"ROLE".equals(matchType))
            return VerbResult.fail(keyword(),
                "expected CHILD or ROLE, got: " + matchType, null);

        Connection conn = ctx.bomConn();

        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Find matching line(s)
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        MBOMLine target = null;
        for (MBOMLine line : children) {
            boolean match = "CHILD".equals(matchType)
                ? matchValue.equals(line.getChildProductId())
                : matchValue.equalsIgnoreCase(line.getRole());
            if (match) {
                target = line;
                break;
            }
        }

        if (target == null)
            return VerbResult.fail(keyword(),
                "no line matching " + matchType + "=" + matchValue + " in " + bomId, null);

        String removedChild = target.getChildProductId();
        String removedRole = target.getRole();
        target.delete();

        RemoveLinePayload payload = new RemoveLinePayload(
            bomId, removedChild, removedRole);

        return VerbResult.ok(keyword(),
            String.format("REMOVE LINE %s: %s=%s (child=%s)",
                bomId, matchType, matchValue, removedChild),
            payload);
    }

    public record RemoveLinePayload(
        String bomId, String removedChild, String removedRole
    ) {}
}
