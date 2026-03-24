package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBomCategory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * REGISTER BOM &lt;bom_id&gt; AS &lt;category&gt; [WIDTH &lt;w&gt;] [DEPTH &lt;d&gt;] [HEIGHT &lt;h&gt;]
 *
 * <p>Level 4 catalog verb (§18.9). Updates m_bom.m_product_category_id to make
 * the BOM selectable by {@link MBOM#findBestFitAnyOwner}. Optional
 * WIDTH/DEPTH/HEIGHT validate against actual child dimensions.
 *
 * <p>Grammar:
 * <pre>
 *   REGISTER BOM CLASSROOM_SET_A AS CL WIDTH 8000 DEPTH 6000
 *   REGISTER BOM SY_MY_FLOOR AS GF
 * </pre>
 */
public class RegisterBomVerb implements Verb<RegisterBomVerb.RegisterPayload> {

    @Override
    public String keyword() { return "REGISTER BOM"; }

    @Override
    public VerbResult<RegisterPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: REGISTER BOM <bom_id> AS <category> [WIDTH w] [DEPTH d] [HEIGHT h]
        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: REGISTER BOM <bom_id> AS <category> [WIDTH w] [DEPTH d] [HEIGHT h]", null);

        String bomId = args[0];

        if (!"AS".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected AS after bom_id, got: " + args[1], null);

        String categoryId = args[2].toUpperCase();

        // Parse optional dims
        int widthMm = 0, depthMm = 0, heightMm = 0;
        for (int i = 3; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "WIDTH"  -> { widthMm = Integer.parseInt(val); i++; }
                case "DEPTH"  -> { depthMm = Integer.parseInt(val); i++; }
                case "HEIGHT" -> { heightMm = Integer.parseInt(val); i++; }
            }
        }

        Connection conn = ctx.bomConn();

        // Validate BOM exists
        MBOM bom = MBOM.get(conn, bomId);
        if (bom == null)
            return VerbResult.fail(keyword(),
                "BOM not found: " + bomId, null);

        // Validate category exists
        MBomCategory cat = MBomCategory.get(conn, categoryId);
        if (cat == null)
            return VerbResult.fail(keyword(),
                "category not found: " + categoryId, null);

        // Validate dimensions if provided — compare against actual child space
        if (widthMm > 0 || depthMm > 0 || heightMm > 0) {
            int[] childSpace = MBOM.computeTotalChildSpace(conn, bomId);
            if (widthMm > 0 && childSpace[0] > 0 && childSpace[0] > widthMm)
                return VerbResult.fail(keyword(),
                    String.format("children sumW=%d exceeds claimed WIDTH=%d",
                        childSpace[0], widthMm), null);
            if (depthMm > 0 && childSpace[1] > 0 && childSpace[1] > depthMm)
                return VerbResult.fail(keyword(),
                    String.format("children maxD=%d exceeds claimed DEPTH=%d",
                        childSpace[1], depthMm), null);
            if (heightMm > 0 && childSpace[2] > 0 && childSpace[2] > heightMm)
                return VerbResult.fail(keyword(),
                    String.format("children maxH=%d exceeds claimed HEIGHT=%d",
                        childSpace[2], heightMm), null);
        }

        // Update m_product_category_id
        bom.setBomCategory(categoryId);
        bom.save();

        return VerbResult.ok(keyword(),
            String.format("REGISTER BOM %s AS %s (%s)",
                bomId, categoryId, cat.getName()),
            new RegisterPayload(bomId, categoryId, widthMm, depthMm, heightMm));
    }

    public record RegisterPayload(
        String bomId, String categoryId,
        int widthMm, int depthMm, int heightMm
    ) {}
}
