package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBomCategory;
import com.bim.ormsandbox.po.MBomCategoryLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * ADD TEMPLATE RULE &lt;parent&gt; CONTAINS &lt;child&gt; [MIN &lt;n&gt;] [MAX &lt;n&gt;]
 *     [Z_EXTENT &lt;ratio&gt;] [MIRRORING &lt;rule&gt;]
 *
 * <p>Level 4 catalog verb (§18.9). Creates an M_BomCategoryLine row,
 * extending the template routing grammar. New building types emerge
 * from data alone — zero Java.
 *
 * <p>Grammar:
 * <pre>
 *   ADD TEMPLATE RULE GF CONTAINS CL MIN 4 MAX 8
 *   ADD TEMPLATE RULE RE CONTAINS SL MIN 1 MAX 1 Z_EXTENT 0.05
 * </pre>
 */
public class AddTemplateRuleVerb implements Verb<AddTemplateRuleVerb.AddRulePayload> {

    @Override
    public String keyword() { return "ADD TEMPLATE RULE"; }

    @Override
    public VerbResult<AddRulePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: ADD TEMPLATE RULE <parent> CONTAINS <child> [options...]
        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: ADD TEMPLATE RULE <parent> CONTAINS <child> [MIN n] [MAX n] [Z_EXTENT ratio] [MIRRORING rule]", null);

        String parent = args[0].toUpperCase();

        if (!"CONTAINS".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected CONTAINS after parent category, got: " + args[1], null);

        String child = args[2].toUpperCase();

        // Parse optional params
        int minQty = 1, maxQty = 1;
        double zExtent = 0.0;
        String mirroringRule = "NONE";

        for (int i = 3; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "MIN"       -> { minQty = Integer.parseInt(val); i++; }
                case "MAX"       -> { maxQty = Integer.parseInt(val); i++; }
                case "Z_EXTENT"  -> { zExtent = Double.parseDouble(val); i++; }
                case "MIRRORING" -> { mirroringRule = val.toUpperCase(); i++; }
            }
        }

        Connection conn = ctx.bomConn();

        // Validate parent category exists
        MBomCategory parentCat = MBomCategory.get(conn, parent);
        if (parentCat == null)
            return VerbResult.fail(keyword(),
                "parent category not found: " + parent, null);

        // Validate child category exists
        MBomCategory childCat = MBomCategory.get(conn, child);
        if (childCat == null)
            return VerbResult.fail(keyword(),
                "child category not found: " + child, null);

        // Compute sequence: next after existing children
        List<MBomCategoryLine> existing = MBomCategoryLine.getByCategory(conn, parent);
        int seq = existing.isEmpty() ? 10 : existing.get(existing.size() - 1).getSequence() + 10;

        // Create the rule
        MBomCategoryLine line = new MBomCategoryLine(conn);
        line.setCategoryId(parent);
        line.setChildCategoryId(child);
        line.setSequence(seq);
        line.setValue(parentCat.getName() + "_" + childCat.getName());
        line.setName(parentCat.getName() + " → " + childCat.getName());
        line.setMinQty(minQty);
        line.setMaxQty(maxQty);
        line.setZExtentRatio(zExtent);
        line.setMirroringRule(mirroringRule);
        line.setIsActive(true);
        line.save();

        return VerbResult.ok(keyword(),
            String.format("ADD TEMPLATE RULE %s CONTAINS %s (seq=%d, min=%d, max=%d)",
                parent, child, seq, minQty, maxQty),
            new AddRulePayload(parent, child, minQty, maxQty, zExtent, mirroringRule));
    }

    public record AddRulePayload(
        String parent, String child,
        int minQty, int maxQty,
        double zExtent, String mirroringRule
    ) {}
}
