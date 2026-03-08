package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * RESIZE ROOM &lt;source_bom_id&gt; TO &lt;width&gt; &lt;depth&gt; &lt;height&gt; [AS &lt;new_bom_id&gt;]
 *
 * <p>Level 1 convenience verb (§18.6). Clones a room SET with a new AABB.
 * Products that no longer fit the new dimensions are dropped. Without AS,
 * modifies the source BOM in-place (removes children that don't fit and
 * updates remaining dimensions).
 *
 * <p>Composes: VALIDATE AABB → CREATE BOM (if AS) → ADD LINE (per kept child) → SET DIMENSIONS
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class ResizeRoomVerb implements Verb<ResizeRoomVerb.ResizePayload> {

    @Override
    public String keyword() { return "RESIZE ROOM"; }

    @Override
    public VerbResult<ResizePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: RESIZE ROOM <source_bom_id> TO <width> <depth> <height> [AS <new_bom_id>]
        if (args.length < 5)
            return VerbResult.fail(keyword(),
                "usage: RESIZE ROOM <source_bom_id> TO <width> <depth> <height> [AS <new_bom_id>]", null);

        String sourceBomId = args[0];

        if (!"TO".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected TO after source bom_id, got: " + args[1], null);

        int widthMm, depthMm, heightMm;
        try {
            widthMm = Integer.parseInt(args[2]);
            depthMm = Integer.parseInt(args[3]);
            heightMm = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(), "invalid dimension value", null);
        }

        // Parse optional AS
        String newBomId = null;
        for (int i = 5; i < args.length - 1; i++) {
            if ("AS".equalsIgnoreCase(args[i])) {
                newBomId = args[i + 1];
                i++;
            }
        }

        Connection conn = ctx.bomConn();

        // Validate source BOM exists
        MBOM source = MBOM.get(conn, sourceBomId);
        if (source == null)
            return VerbResult.fail(keyword(),
                "source BOM not found: " + sourceBomId, null);

        // Get source children
        List<MBOMLine> sourceChildren = MBOMLine.getByBom(conn, sourceBomId);

        String targetBomId;
        if (newBomId != null) {
            // AS mode: create new BOM (clone header)
            MBOM existing = MBOM.get(conn, newBomId);
            if (existing != null)
                return VerbResult.fail(keyword(),
                    newBomId + " already exists", null);

            // Validate SY_ prefix for new BOM
            if (!newBomId.startsWith("SY_"))
                return VerbResult.fail(keyword(),
                    "synthetic BOMs must use SY_ prefix, got: " + newBomId, null);

            MBOM newBom = new MBOM(conn);
            newBom.setBomId(newBomId);
            newBom.setBomName(newBomId);
            newBom.setBomType(source.getBomType());
            newBom.setBomCategory(source.getBomCategory());
            newBom.setGroupBy(source.getGroupBy());
            newBom.setIsActive(true);
            newBom.setBomLevel(source.getBomLevel());
            newBom.setSeqNo(source.getSeqNo());
            newBom.setEntityType(MBOM.ENTITYTYPE_User);
            if (source.getDocSubType() != null)
                newBom.setDocSubType(source.getDocSubType());
            newBom.save();

            targetBomId = newBomId;
        } else {
            // In-place mode: remove children that don't fit, keep the rest
            targetBomId = sourceBomId;
        }

        // Evaluate each child: fits in new AABB?
        int keptCount = 0;
        List<String> droppedProducts = new ArrayList<>();

        for (MBOMLine src : sourceChildren) {
            int childW = src.getAllocatedWidthMm();
            int childD = src.getAllocatedDepthMm();
            int childH = src.getAllocatedHeightMm();

            // A child fits if its allocated dimensions fit within the new AABB
            // Zero-dimension children (no allocated size) always fit
            boolean fits = (childW == 0 && childD == 0 && childH == 0)
                || (childW <= widthMm && childD <= depthMm && childH <= heightMm);

            if (fits) {
                if (newBomId != null) {
                    // Clone mode: add line to new BOM
                    MBOMLine line = new MBOMLine(conn);
                    line.setBomId(targetBomId);
                    line.setChildProductId(src.getChildProductId());
                    line.setRole(src.getRole());
                    line.setSequence(src.getSequence());
                    line.setDx(src.getDx());
                    line.setDy(src.getDy());
                    line.setDz(src.getDz());
                    line.setRotationRule(src.getRotationRule());
                    line.setAllocatedWidthMm(childW);
                    line.setAllocatedDepthMm(childD);
                    line.setAllocatedHeightMm(childH);
                    line.setComponentType(src.getComponentType());
                    line.setEntityType(MBOM.ENTITYTYPE_User);
                    line.setIsActive(true);
                    line.save();
                }
                keptCount++;
            } else {
                if (newBomId == null) {
                    // In-place mode: remove the line that doesn't fit
                    src.delete();
                }
                droppedProducts.add(src.getChildProductId());
            }
        }

        return VerbResult.ok(keyword(),
            String.format("RESIZE ROOM %s → %s %dx%dx%d: kept=%d, dropped=%d",
                sourceBomId, targetBomId, widthMm, depthMm, heightMm,
                keptCount, droppedProducts.size()),
            new ResizePayload(targetBomId, keptCount,
                droppedProducts.toArray(new String[0])));
    }

    public record ResizePayload(
        String newBomId, int keptCount, String[] droppedProducts
    ) {}
}
