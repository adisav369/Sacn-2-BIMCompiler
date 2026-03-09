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
 * SWAP ROOM &lt;role_or_category&gt; IN &lt;floor_bom_id&gt; WITH &lt;new_bom_id&gt;
 *
 * <p>Level 2 floor verb (§18.7). Updates m_bom_line.child_product_id.
 * Validates new BOM fits the allocated AABB slot.
 *
 * <p>Guard: new BOM dimensions must not exceed the slot's allocated AABB.
 *
 * <p>Grammar:
 * <pre>
 *   SWAP ROOM ROOM_KT IN SY_FLOOR_GF_10000x8000 WITH SY_KT_3000x2500
 * </pre>
 */
public class SwapRoomVerb implements Verb<SwapRoomVerb.SwapPayload> {

    @Override
    public String keyword() { return "SWAP ROOM"; }

    @Override
    public VerbResult<SwapPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: SWAP ROOM <role_or_category> IN <floor_bom_id> WITH <new_bom_id>
        if (args.length < 5)
            return VerbResult.fail(keyword(),
                "usage: SWAP ROOM <role_or_category> IN <floor_bom_id> WITH <new_bom_id>", null);

        String roleOrCategory = args[0].toUpperCase();

        if (!"IN".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected IN, got: " + args[1], null);

        String floorBomId = args[2];

        if (!"WITH".equalsIgnoreCase(args[3]))
            return VerbResult.fail(keyword(),
                "expected WITH, got: " + args[3], null);

        String newBomId = args[4];

        Connection conn = ctx.bomConn();

        // Validate floor BOM exists
        MBOM floor = MBOM.get(conn, floorBomId);
        if (floor == null)
            return VerbResult.fail(keyword(),
                "floor BOM not found: " + floorBomId, null);

        // EntityType guard
        if (MBOM.ENTITYTYPE_Dictionary.equals(floor.getEntityType()))
            return VerbResult.fail(keyword(),
                floorBomId + " is read-only (EntityType=D)", null);

        // Validate new BOM exists
        MBOM newBom = MBOM.get(conn, newBomId);
        if (newBom == null)
            return VerbResult.fail(keyword(),
                "replacement BOM not found: " + newBomId, null);

        // Find matching line by role or category
        List<MBOMLine> children = MBOMLine.getByBom(conn, floorBomId);
        MBOMLine target = null;
        for (MBOMLine line : children) {
            String role = line.getRole();
            if (role != null && (role.equalsIgnoreCase(roleOrCategory)
                    || role.equalsIgnoreCase("ROOM_" + roleOrCategory))) {
                target = line;
                break;
            }
        }

        if (target == null)
            return VerbResult.fail(keyword(),
                "no room with role/category '" + roleOrCategory + "' in " + floorBomId, null);

        // Guard: validate new BOM fits the allocated slot
        int slotW = target.getAllocatedWidthMm();
        int slotD = target.getAllocatedDepthMm();
        int slotH = target.getAllocatedHeightMm();

        int[] newSpace = MBOM.computeTotalChildSpace(conn, newBomId);
        int fitMarginW = slotW - newSpace[0];
        int fitMarginD = slotD - newSpace[1];
        int fitMarginH = slotH - newSpace[2];

        // Only check if slot has non-zero dimensions (meaningful constraint)
        if (slotW > 0 && newSpace[0] > 0 && fitMarginW < 0)
            return VerbResult.fail(keyword(),
                String.format("new BOM width %d exceeds slot width %d (margin=%d)",
                    newSpace[0], slotW, fitMarginW), null);
        if (slotD > 0 && newSpace[1] > 0 && fitMarginD < 0)
            return VerbResult.fail(keyword(),
                String.format("new BOM depth %d exceeds slot depth %d (margin=%d)",
                    newSpace[1], slotD, fitMarginD), null);
        if (slotH > 0 && newSpace[2] > 0 && fitMarginH < 0)
            return VerbResult.fail(keyword(),
                String.format("new BOM height %d exceeds slot height %d (margin=%d)",
                    newSpace[2], slotH, fitMarginH), null);

        String previousBomId = target.getChildProductId();
        String role = target.getRole();

        // Swap: update child_product_id
        target.setChildProductId(newBomId);
        target.save();

        int minMargin = Math.min(fitMarginW, Math.min(fitMarginD, fitMarginH));

        return VerbResult.ok(keyword(),
            String.format("SWAP ROOM %s IN %s: %s → %s (margin=%d)",
                role, floorBomId, previousBomId, newBomId, minMargin),
            new SwapPayload(floorBomId, role, previousBomId, newBomId, minMargin));
    }

    public record SwapPayload(
        String floorBomId, String role,
        String previousBomId, String newBomId,
        int fitMargin
    ) {}
}
