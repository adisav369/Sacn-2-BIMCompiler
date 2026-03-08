package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * SET ROTATION ON &lt;bom_id&gt; LINE &lt;role_or_child&gt; TO &lt;value&gt;
 *
 * <p>Level 0 primitive (§18.4). Updates rotation_rule on an existing
 * {@code m_bom_line}. This is how elements orient — including the
 * DX party-wall mirror (rotation_rule=3.14159 = π = 180°).
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class SetRotationVerb implements Verb<SetRotationVerb.SetRotationPayload> {

    @Override
    public String keyword() { return "SET ROTATION"; }

    @Override
    public VerbResult<SetRotationPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: SET ROTATION ON <bom_id> LINE <role_or_child> TO <value>
        if (args.length < 5)
            return VerbResult.fail(keyword(),
                "usage: SET ROTATION ON <bom_id> LINE <role_or_child> TO <value>", null);

        if (!"ON".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(), "expected ON after SET ROTATION", null);

        String bomId = args[1];

        if (!"LINE".equalsIgnoreCase(args[2]))
            return VerbResult.fail(keyword(), "expected LINE after bom_id", null);

        String lineRef = args[3];

        if (!"TO".equalsIgnoreCase(args[4]))
            return VerbResult.fail(keyword(), "expected TO after line reference", null);

        if (args.length < 6)
            return VerbResult.fail(keyword(), "rotation value is required after TO", null);

        String rotationValue = args[5];

        Connection conn = ctx.bomConn();

        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        MBOMLine target = SetTackVerb.findLine(conn, bomId, lineRef);
        if (target == null)
            return VerbResult.fail(keyword(),
                "no line matching '" + lineRef + "' in " + bomId, null);

        target.setRotationRule(rotationValue);
        target.save();

        SetRotationPayload payload = new SetRotationPayload(
            bomId, lineRef, target.getBomChildId(), rotationValue);

        return VerbResult.ok(keyword(),
            String.format("SET ROTATION %s.%s → %s",
                bomId, lineRef, rotationValue),
            payload);
    }

    public record SetRotationPayload(
        String bomId, String lineRef, int bomChildId, String rotationRule
    ) {}
}
