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
 * REMOVE ROOM &lt;role_or_category&gt; FROM &lt;floor_bom_id&gt;
 *
 * <p>Level 2 floor verb (§18.7). Deletes m_bom_line by role or category match.
 * Guards entity_type=U (cannot remove from dictionary BOMs).
 *
 * <p>Grammar:
 * <pre>
 *   REMOVE ROOM ROOM_KT FROM SY_FLOOR_GF_10000x8000
 *   REMOVE ROOM KT FROM SY_FLOOR_GF_10000x8000
 * </pre>
 */
public class RemoveRoomVerb implements Verb<RemoveRoomVerb.RemoveRoomPayload> {

    @Override
    public String keyword() { return "REMOVE ROOM"; }

    @Override
    public VerbResult<RemoveRoomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: REMOVE ROOM <role_or_category> FROM <floor_bom_id>", null);

        String roleOrCategory = args[0].toUpperCase();

        if (!"FROM".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected FROM, got: " + args[1], null);

        String floorBomId = args[2];

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

        // Find matching line: first try by role, then by category suffix
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

        int reclaimedW = target.getAllocatedWidthMm();
        int reclaimedD = target.getAllocatedDepthMm();
        String removedRole = target.getRole();

        target.delete();

        return VerbResult.ok(keyword(),
            String.format("REMOVE ROOM %s FROM %s (reclaimed %dx%d mm)",
                removedRole, floorBomId, reclaimedW, reclaimedD),
            new RemoveRoomPayload(floorBomId, removedRole, reclaimedW, reclaimedD));
    }

    public record RemoveRoomPayload(
        String floorBomId, String removedRole,
        int reclaimedW, int reclaimedD
    ) {}
}
