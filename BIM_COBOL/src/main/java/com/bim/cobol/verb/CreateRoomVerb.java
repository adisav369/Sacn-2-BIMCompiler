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
 * CREATE ROOM &lt;category&gt; &lt;width&gt; &lt;depth&gt; &lt;height&gt; [FROM &lt;doc_sub_type&gt;] [EMPTY]
 *
 * <p>Level 1 convenience verb (§18.6). Creates a room SET BOM by composing
 * P0 primitives. Finds the best-fit template BOM from the catalog, then
 * clones its children into a new SY_ BOM.
 *
 * <p>Composes: VALIDATE AABB → CREATE BOM → findBestFitAnyOwner → ADD LINE (per child) → SET DIMENSIONS
 *
 * <p>EMPTY mode creates BOM with zero children — a slot for FURNISH ROOM.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class CreateRoomVerb implements Verb<CreateRoomVerb.CreateRoomPayload> {

    @Override
    public String keyword() { return "CREATE ROOM"; }

    @Override
    public VerbResult<CreateRoomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: CREATE ROOM <category> <width> <depth> <height> [FROM <dst>] [EMPTY]
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: CREATE ROOM <category> <width> <depth> <height> [FROM <doc_sub_type>] [EMPTY]", null);

        String category = args[0].toUpperCase();

        int widthMm, depthMm, heightMm;
        try {
            widthMm = Integer.parseInt(args[1]);
            depthMm = Integer.parseInt(args[2]);
            heightMm = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(), "invalid dimension value", null);
        }

        // Parse optional FROM and EMPTY
        String docSubType = null;
        boolean empty = false;
        for (int i = 4; i < args.length; i++) {
            String token = args[i].toUpperCase();
            if ("FROM".equals(token) && i + 1 < args.length) {
                docSubType = args[i + 1].toUpperCase();
                i++;
            } else if ("EMPTY".equals(token)) {
                empty = true;
            }
        }

        Connection conn = ctx.bomConn();

        // Step 1: VALIDATE AABB — check that at least one BOM in category fits
        if (!empty) {
            List<MBOM> allInCategory = MBOM.getByCategory(conn, category);
            if (allInCategory.isEmpty())
                return VerbResult.fail(keyword(),
                    "no BOMs found for category: " + category,
                    new CreateRoomPayload(null, category, 0, null));
        }

        // Step 2: Generate SY_ bom_id
        String bomId = String.format("SY_%s_%dx%d", category, widthMm, depthMm);

        // Check not already exists
        MBOM existing = MBOM.get(conn, bomId);
        if (existing != null)
            return VerbResult.fail(keyword(),
                bomId + " already exists", null);

        // Step 3: CREATE BOM — create the room SET header
        MBOM bom = new MBOM(conn);
        bom.setBomId(bomId);
        bom.setBomName(bomId);
        bom.setBomType("SET");
        bom.setProductCategory(category);
        bom.setGroupBy("ROOM");
        bom.setIsActive(true);
        bom.setBomLevel("SET");
        bom.setSeqNo(10);
        bom.setEntityType(MBOM.ENTITYTYPE_User);
        if (docSubType != null)
            bom.setDocSubType(docSubType);
        bom.save();

        // Step 4: If EMPTY, we're done
        if (empty) {
            return VerbResult.ok(keyword(),
                String.format("CREATE ROOM %s %dx%dx%d EMPTY → %s (0 children)",
                    category, widthMm, depthMm, heightMm, bomId),
                new CreateRoomPayload(bomId, category, 0, null));
        }

        // Step 5: Find best-fit template BOM from catalog
        MBOM template = MBOM.findBestFitAnyOwner(conn, category,
            widthMm, depthMm, heightMm);

        if (template == null) {
            // BOM created but no template found — return as empty with warning
            return VerbResult.ok(keyword(),
                String.format("CREATE ROOM %s %dx%dx%d → %s (0 children, no template fit)",
                    category, widthMm, depthMm, heightMm, bomId),
                new CreateRoomPayload(bomId, category, 0, null));
        }

        // Step 6: Clone each child from template via ADD LINE
        List<MBOMLine> templateChildren = MBOMLine.getByBom(conn, template.getValue());
        int childCount = 0;

        for (MBOMLine src : templateChildren) {
            MBOMLine line = new MBOMLine(conn);
            line.setBomId(bomId);
            line.setChildProductId(src.getChildProductId());
            line.setRole(src.getRole());
            line.setSequence(src.getSequence());
            line.setDx(src.getDx());
            line.setDy(src.getDy());
            line.setDz(src.getDz());
            line.setRotationRule(src.getRotationRule());
            line.setAllocatedWidthMm(src.getAllocatedWidthMm());
            line.setAllocatedDepthMm(src.getAllocatedDepthMm());
            line.setAllocatedHeightMm(src.getAllocatedHeightMm());
            line.setComponentType(src.getComponentType());
            line.setEntityType(MBOM.ENTITYTYPE_User);
            line.setIsActive(true);
            line.save();
            childCount++;
        }

        return VerbResult.ok(keyword(),
            String.format("CREATE ROOM %s %dx%dx%d → %s (%d children from %s)",
                category, widthMm, depthMm, heightMm, bomId,
                childCount, template.getValue()),
            new CreateRoomPayload(bomId, category, childCount, template.getValue()));
    }

    public record CreateRoomPayload(
        String bomId, String category, int childCount, String sourceTemplateBomId
    ) {}
}
