package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * SET DIMENSIONS ON &lt;bom_id&gt; LINE &lt;role_or_child&gt; WIDTH &lt;w&gt; DEPTH &lt;d&gt; HEIGHT &lt;h&gt;
 *
 * <p>Level 0 primitive (§18.4). Updates allocated_width/depth/height_mm on an
 * existing {@code m_bom_line}. Only specified axes are updated — omitted axes
 * are left unchanged.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class SetDimensionsVerb implements Verb<SetDimensionsVerb.SetDimensionsPayload> {

    @Override
    public String keyword() { return "SET DIMENSIONS"; }

    @Override
    public VerbResult<SetDimensionsPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: SET DIMENSIONS ON <bom_id> LINE <role_or_child> [WIDTH w] [DEPTH d] [HEIGHT h]
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: SET DIMENSIONS ON <bom_id> LINE <role_or_child> [WIDTH w] [DEPTH d] [HEIGHT h]", null);

        if (!"ON".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(), "expected ON after SET DIMENSIONS", null);

        String bomId = args[1];

        if (!"LINE".equalsIgnoreCase(args[2]))
            return VerbResult.fail(keyword(), "expected LINE after bom_id", null);

        String lineRef = args[3];

        // Parse optional WIDTH/DEPTH/HEIGHT
        Integer width = null, depth = null, height = null;
        for (int i = 4; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "WIDTH"  -> { width = Integer.parseInt(val); i++; }
                case "DEPTH"  -> { depth = Integer.parseInt(val); i++; }
                case "HEIGHT" -> { height = Integer.parseInt(val); i++; }
            }
        }

        if (width == null && depth == null && height == null)
            return VerbResult.fail(keyword(),
                "at least one of WIDTH, DEPTH, HEIGHT must be specified", null);

        Connection conn = ctx.bomConn();

        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        MBOMLine target = SetTackVerb.findLine(conn, bomId, lineRef);
        if (target == null)
            return VerbResult.fail(keyword(),
                "no line matching '" + lineRef + "' in " + bomId, null);

        if (width != null)  target.setAllocatedWidthMm(width);
        if (depth != null)  target.setAllocatedDepthMm(depth);
        if (height != null) target.setAllocatedHeightMm(height);
        target.save();

        SetDimensionsPayload payload = new SetDimensionsPayload(
            bomId, lineRef, target.getBomChildId(),
            target.getAllocatedWidthMm(),
            target.getAllocatedDepthMm(),
            target.getAllocatedHeightMm());

        return VerbResult.ok(keyword(),
            String.format("SET DIMENSIONS %s.%s → %dx%dx%d mm",
                bomId, lineRef,
                target.getAllocatedWidthMm(),
                target.getAllocatedDepthMm(),
                target.getAllocatedHeightMm()),
            payload);
    }

    public record SetDimensionsPayload(
        String bomId, String lineRef, int bomChildId,
        int widthMm, int depthMm, int heightMm
    ) {}
}
