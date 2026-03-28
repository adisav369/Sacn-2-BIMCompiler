package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * ADD LINE TO &lt;bom_id&gt; CHILD &lt;child&gt; ROLE &lt;role&gt; SEQ &lt;n&gt;
 *     [DX &lt;dx&gt;] [DY &lt;dy&gt;] [DZ &lt;dz&gt;] [ROTATION &lt;r&gt;]
 *     [WIDTH &lt;w&gt;] [DEPTH &lt;d&gt;] [HEIGHT &lt;h&gt;]
 *     [COMPONENT_TYPE &lt;BUY|MAKE|PHANTOM&gt;]
 *
 * <p>Level 0 primitive (§18.4). Creates one {@code m_bom_line} row.
 * component_type defaults to BUY (all LODs from component_library.db).
 * Override with explicit COMPONENT_TYPE.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class AddLineVerb implements Verb<AddLineVerb.AddLinePayload> {

    @Override
    public String keyword() { return "ADD LINE"; }

    @Override
    public VerbResult<AddLinePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: ADD LINE TO <bom_id> CHILD <child> ROLE <role> SEQ <n> [options]
        if (args.length < 6)
            return VerbResult.fail(keyword(),
                "usage: ADD LINE TO <bom_id> CHILD <child> ROLE <role> SEQ <n> [DX n] [DY n] [DZ n] [ROTATION r] [WIDTH w] [DEPTH d] [HEIGHT h] [COMPONENT_TYPE t]", null);

        // First token must be TO
        if (!"TO".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(),
                "expected TO after ADD LINE, got: " + args[0], null);

        String bomId = args[1];

        // Parse named params from remaining args
        String childProductId = null;
        String role = null;
        int sequence = 100;
        double dx = 0, dy = 0, dz = 0;
        String rotationRule = "0";
        int widthMm = 0, depthMm = 0, heightMm = 0;
        String componentType = null;  // auto-detect if null

        for (int i = 2; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "CHILD"          -> { childProductId = val; i++; }
                case "ROLE"           -> { role = val; i++; }
                case "SEQ"            -> { sequence = Integer.parseInt(val); i++; }
                case "DX"             -> { dx = Double.parseDouble(val); i++; }
                case "DY"             -> { dy = Double.parseDouble(val); i++; }
                case "DZ"             -> { dz = Double.parseDouble(val); i++; }
                case "ROTATION"       -> { rotationRule = val; i++; }
                case "WIDTH"          -> { widthMm = Integer.parseInt(val); i++; }
                case "DEPTH"          -> { depthMm = Integer.parseInt(val); i++; }
                case "HEIGHT"         -> { heightMm = Integer.parseInt(val); i++; }
                case "COMPONENT_TYPE" -> { componentType = val.toUpperCase(); i++; }
            }
        }

        if (childProductId == null)
            return VerbResult.fail(keyword(), "CHILD is required", null);
        if (role == null)
            return VerbResult.fail(keyword(), "ROLE is required", null);

        Connection conn = ctx.bomConn();

        // Validate parent BOM exists
        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(),
                "parent BOM not found: " + bomId, null);

        // component_type is not a decision field — currently all BUY.
        // All LODs must exist in component_library.db (designer prerequisite).
        // Future: MAKE = LOD created on-the-fly via Mesh2Library.txt.
        if (componentType == null) {
            componentType = "BUY";
        }

        // Create the line
        MBOMLine line = new MBOMLine(conn);
        line.setBomId(bomId);
        line.setChildProductId(childProductId);
        line.setRole(role);
        line.setSequence(sequence);
        line.setDx(dx);
        line.setDy(dy);
        line.setDz(dz);
        line.setRotationRule(rotationRule);
        line.setAllocatedWidthMm(widthMm);
        line.setAllocatedDepthMm(depthMm);
        line.setAllocatedHeightMm(heightMm);
        line.setComponentType(componentType);
        line.setEntityType(MBOM.ENTITYTYPE_User);
        line.setIsActive(true);
        line.save();

        AddLinePayload payload = new AddLinePayload(
            bomId, childProductId, role, sequence, dx, dy, dz,
            rotationRule, widthMm, depthMm, heightMm, componentType,
            line.getBomLineId());

        return VerbResult.ok(keyword(),
            String.format("ADD LINE %s → %s (role=%s, seq=%d, type=%s)",
                bomId, childProductId, role, sequence, componentType),
            payload);
    }

    public record AddLinePayload(
        String bomId, String childProductId, String role, int sequence,
        double dx, double dy, double dz, String rotationRule,
        int widthMm, int depthMm, int heightMm, String componentType,
        int bomChildId
    ) {}
}
