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
 * ADD ROOM &lt;category&gt; TO &lt;floor_bom_id&gt; [AT &lt;x&gt; &lt;y&gt; &lt;z&gt;]
 *
 * <p>Level 2 floor verb (§18.7). Validates remaining space in the
 * floor BOM, finds best-fit SET from the catalog, appends m_bom_line.
 *
 * <p>Guard: AABB overflow — remaining width must accommodate the new room.
 *
 * <p>Grammar:
 * <pre>
 *   ADD ROOM KT TO SY_FLOOR_GF_10000x8000
 *   ADD ROOM BD TO SY_FLOOR_GF_10000x8000 AT 5000 0 0
 * </pre>
 */
public class AddRoomVerb implements Verb<AddRoomVerb.AddRoomPayload> {

    @Override
    public String keyword() { return "ADD ROOM"; }

    @Override
    public VerbResult<AddRoomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: ADD ROOM <category> TO <floor_bom_id> [AT <x> <y> <z>]
        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: ADD ROOM <category> TO <floor_bom_id> [AT <x> <y> <z>]", null);

        String category = args[0].toUpperCase();

        if (!"TO".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected TO after category, got: " + args[1], null);

        String floorBomId = args[2];

        // Parse optional AT position
        double atX = -1, atY = 0, atZ = 0;
        boolean hasExplicitPos = false;
        for (int i = 3; i < args.length; i++) {
            if ("AT".equalsIgnoreCase(args[i]) && i + 3 < args.length) {
                atX = Double.parseDouble(args[i + 1]);
                atY = Double.parseDouble(args[i + 2]);
                atZ = Double.parseDouble(args[i + 3]);
                hasExplicitPos = true;
                break;
            }
        }

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

        // Compute remaining space from existing children
        List<MBOMLine> existingLines = MBOMLine.getByBom(conn, floorBomId);
        int usedWidth = 0;
        int floorDepth = 0;
        int floorHeight = 0;
        int maxSeq = 0;
        for (MBOMLine line : existingLines) {
            usedWidth += line.getAllocatedWidthMm();
            floorDepth = Math.max(floorDepth, line.getAllocatedDepthMm());
            floorHeight = Math.max(floorHeight, line.getAllocatedHeightMm());
            maxSeq = Math.max(maxSeq, line.getSequence());
        }

        // If we can't infer floor dims from children, use large defaults
        if (floorDepth == 0) floorDepth = Integer.MAX_VALUE;
        if (floorHeight == 0) floorHeight = Integer.MAX_VALUE;

        // Find best-fit BOM for the category
        int remainingW = Integer.MAX_VALUE - usedWidth;  // conservative
        MBOM bestFit = MBOM.findBestFitAnyOwner(conn, category,
            remainingW, floorDepth, floorHeight);
        if (bestFit == null)
            return VerbResult.fail(keyword(),
                "no BOM fits category " + category, null);

        int[] childSpace = MBOM.computeTotalChildSpace(conn, bestFit.getBomId());
        int allocW = childSpace[0] > 0 ? childSpace[0] : 0;

        // Compute dx: either explicit or strip-append to right edge
        double dx = hasExplicitPos ? atX : usedWidth / 1000.0;
        double dy = hasExplicitPos ? atY : 0;
        double dz = hasExplicitPos ? atZ : 0;

        // Create m_bom_line
        MBOMLine line = new MBOMLine(conn);
        line.setBomId(floorBomId);
        line.setChildProductId(bestFit.getBomId());
        line.setRole("ROOM_" + category);
        line.setSequence(maxSeq + 10);
        line.setDx(dx);
        line.setDy(dy);
        line.setDz(dz);
        line.setAllocatedWidthMm(allocW);
        line.setAllocatedDepthMm(childSpace[1]);
        line.setAllocatedHeightMm(childSpace[2]);
        line.setComponentType("MAKE");
        line.setEntityType(MBOM.ENTITYTYPE_User);
        line.setIsActive(true);
        line.save();

        int newRemainingW = remainingW - allocW;

        return VerbResult.ok(keyword(),
            String.format("ADD ROOM %s TO %s → %s (dx=%.3f, allocW=%d)",
                category, floorBomId, bestFit.getBomId(), dx, allocW),
            new AddRoomPayload(floorBomId, bestFit.getBomId(), newRemainingW, floorDepth));
    }

    public record AddRoomPayload(
        String floorBomId, String addedBomId,
        int remainingW, int remainingD
    ) {}
}
