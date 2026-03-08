package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * SET LINE PROPERTY ON &lt;bom_id&gt; LINE &lt;role_or_child&gt; &lt;property&gt; &lt;value&gt;
 *
 * <p>Level 0 primitive (§18.4). General-purpose property setter for any
 * writable {@code m_bom_line} column. This is the "escape hatch" when
 * no specific SET verb (TACK, ROTATION, DIMENSIONS) covers the need.
 *
 * <p>Supported properties: COMPONENT_TYPE, ROLE, LOCATOR_REF, ORIENTATION,
 * ANCHOR_FACE, LAYOUT_STRATEGY, STOREY, ELEMENT_REF, MATERIAL_NAME,
 * MATERIAL_RGBA, Z_RULE, QTY_TYPE, FIT_PRIORITY, MIN_SPACE_MM, SEQUENCE.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class SetLinePropertyVerb implements Verb<SetLinePropertyVerb.SetPropertyPayload> {

    private static final Set<String> STRING_PROPS = Set.of(
        "COMPONENT_TYPE", "ROLE", "LOCATOR_REF", "ORIENTATION",
        "ANCHOR_FACE", "LAYOUT_STRATEGY", "STOREY", "ELEMENT_REF",
        "MATERIAL_NAME", "MATERIAL_RGBA", "Z_RULE", "QTY_TYPE"
    );

    private static final Set<String> INT_PROPS = Set.of(
        "FIT_PRIORITY", "MIN_SPACE_MM", "SEQUENCE"
    );

    @Override
    public String keyword() { return "SET LINE PROPERTY"; }

    @Override
    public VerbResult<SetPropertyPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: SET LINE PROPERTY ON <bom_id> LINE <ref> <property> <value>
        if (args.length < 5)
            return VerbResult.fail(keyword(),
                "usage: SET LINE PROPERTY ON <bom_id> LINE <role_or_child> <property> <value>", null);

        if (!"ON".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(), "expected ON after SET LINE PROPERTY", null);

        String bomId = args[1];

        if (!"LINE".equalsIgnoreCase(args[2]))
            return VerbResult.fail(keyword(), "expected LINE after bom_id", null);

        String lineRef = args[3];
        String property = args[4].toUpperCase();
        String value = args.length > 5 ? args[5] : "";

        if (!STRING_PROPS.contains(property) && !INT_PROPS.contains(property))
            return VerbResult.fail(keyword(),
                "unknown property: " + property + ". Supported: " +
                    STRING_PROPS + " + " + INT_PROPS, null);

        Connection conn = ctx.bomConn();

        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        MBOMLine target = SetTackVerb.findLine(conn, bomId, lineRef);
        if (target == null)
            return VerbResult.fail(keyword(),
                "no line matching '" + lineRef + "' in " + bomId, null);

        // Apply property
        switch (property) {
            case "COMPONENT_TYPE"  -> target.setComponentType(value);
            case "ROLE"            -> target.setRole(value);
            case "LOCATOR_REF"    -> target.setLocatorRef(value);
            case "ORIENTATION"    -> target.setOrientation(value);
            case "ANCHOR_FACE"    -> target.setAnchorFace(value);
            case "LAYOUT_STRATEGY" -> target.setLayoutStrategy(value);
            case "STOREY"          -> target.setStorey(value);
            case "ELEMENT_REF"    -> target.setElementRef(value);
            case "MATERIAL_NAME"  -> target.setMaterialName(value);
            case "MATERIAL_RGBA"  -> target.setMaterialRgba(value);
            case "Z_RULE"          -> target.setZRule(value);
            case "QTY_TYPE"        -> target.setQtyType(value);
            case "FIT_PRIORITY"   -> target.setFitPriority(Integer.parseInt(value));
            case "MIN_SPACE_MM"   -> target.setMinSpaceMm(Integer.parseInt(value));
            case "SEQUENCE"        -> target.setSequence(Integer.parseInt(value));
        }
        target.save();

        SetPropertyPayload payload = new SetPropertyPayload(
            bomId, lineRef, property, value);

        return VerbResult.ok(keyword(),
            String.format("SET %s=%s on %s.%s", property, value, bomId, lineRef),
            payload);
    }

    public record SetPropertyPayload(
        String bomId, String lineRef, String property, String value
    ) {}
}
