package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * FURNISH ROOM &lt;bom_id&gt; WITH &lt;product1&gt; [&lt;product2&gt;...] [COUNT &lt;n&gt;] [ALONG &lt;wall&gt;]
 *
 * <p>Level 1 convenience verb (§18.6). Adds leaf products to an existing room SET.
 * For each product name, looks up M_Product in component_library.db for dimensions,
 * then appends m_bom_line rows with auto-computed tack offsets (strip model).
 *
 * <p>COUNT mode: add N copies of the last-named product with spacing.
 * ALONG mode: set dy based on wall face (NORTH_WALL=0, SOUTH_WALL=depth, etc.).
 *
 * <p>Requires componentConn (component_library.db) for product lookup.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class FurnishRoomVerb implements Verb<FurnishRoomVerb.FurnishPayload> {

    @Override
    public String keyword() { return "FURNISH ROOM"; }

    @Override
    public VerbResult<FurnishPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: FURNISH ROOM <bom_id> WITH <products...> [COUNT <n>] [ALONG <wall>]
        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: FURNISH ROOM <bom_id> WITH <product1> [product2...] [COUNT <n>] [ALONG <wall>]", null);

        String bomId = args[0];

        if (!"WITH".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected WITH after bom_id, got: " + args[1], null);

        // Parse product names, COUNT, and ALONG
        List<String> productNames = new ArrayList<>();
        int count = 1;
        String alongWall = null;

        for (int i = 2; i < args.length; i++) {
            String token = args[i].toUpperCase();
            if ("COUNT".equals(token) && i + 1 < args.length) {
                count = Integer.parseInt(args[i + 1]);
                i++;
            } else if ("ALONG".equals(token) && i + 1 < args.length) {
                alongWall = args[i + 1].toUpperCase();
                i++;
            } else {
                productNames.add(args[i]);
            }
        }

        if (productNames.isEmpty())
            return VerbResult.fail(keyword(), "at least one product name is required", null);

        Connection bomConn = ctx.bomConn();

        // Validate target BOM exists and is a SET
        MBOM parent = MBOM.get(bomConn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        if (!"SET".equals(parent.getBomType()))
            return VerbResult.fail(keyword(),
                bomId + " is type " + parent.getBomType() + ", expected SET", null);

        // Determine connection for product lookup
        Connection compConn = ctx.componentConn();
        if (compConn == null)
            return VerbResult.fail(keyword(),
                "FURNISH ROOM requires componentConn for product lookup", null);

        // Get existing children to compute next seq and dx offset
        List<MBOMLine> existing = MBOMLine.getByBom(bomConn, bomId);
        int nextSeq = existing.isEmpty() ? 10
            : existing.get(existing.size() - 1).getSequence() + 10;
        double currentDx = 0;
        for (MBOMLine line : existing) {
            // Accumulate strip offset: dx of last child + its width in meters
            double lineEnd = line.getDx() + line.getAllocatedWidthMm() / 1000.0;
            if (lineEnd > currentDx) currentDx = lineEnd;
        }

        List<String> placed = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        int addedCount = 0;

        // Expand COUNT: if count > 1, repeat the last product name
        List<String> expandedProducts = new ArrayList<>(productNames);
        if (count > 1 && !productNames.isEmpty()) {
            String lastProduct = productNames.get(productNames.size() - 1);
            // Already have 1 copy from the list, add count-1 more
            for (int c = 1; c < count; c++) {
                expandedProducts.add(lastProduct);
            }
        }

        for (String productName : expandedProducts) {
            MProduct product = MProduct.get(compConn, productName);
            if (product == null) {
                unplaced.add(productName);
                continue;
            }

            // Product dimensions are in METERS — convert to mm for allocated dims
            int widthMm = (int) Math.round(product.getWidth() * 1000);
            int depthMm = (int) Math.round(product.getDepth() * 1000);
            int heightMm = (int) Math.round(product.getHeight() * 1000);

            // Compute tack position — strip model: dx increments by product width
            double dy = 0;
            double dz = 0;
            if (alongWall != null) {
                // ALONG sets the dy based on wall face
                // NORTH_WALL = dy=0, SOUTH_WALL = dy approaches depth
                // EAST_WALL = swap axes conceptually, WEST_WALL = dx=0
                switch (alongWall) {
                    case "NORTH_WALL" -> dy = 0;
                    case "SOUTH_WALL" -> dy = depthMm / 1000.0;
                    case "EAST_WALL"  -> dy = widthMm / 1000.0;
                    case "WEST_WALL"  -> dy = 0;
                }
            }

            MBOMLine line = new MBOMLine(bomConn);
            line.setBomId(bomId);
            line.setChildProductId(productName);
            line.setRole(productName.toUpperCase() + "_" + (addedCount + 1));
            line.setSequence(nextSeq);
            line.setDx(currentDx);
            line.setDy(dy);
            line.setDz(dz);
            line.setRotationRule("0");
            line.setAllocatedWidthMm(widthMm);
            line.setAllocatedDepthMm(depthMm);
            line.setAllocatedHeightMm(heightMm);
            line.setComponentType("BUY");
            line.setEntityType(MBOM.ENTITYTYPE_User);
            line.setIsActive(true);
            line.save();

            placed.add(productName);
            addedCount++;
            nextSeq += 10;
            currentDx += widthMm / 1000.0;
        }

        return VerbResult.ok(keyword(),
            String.format("FURNISH ROOM %s: added %d, unplaced %d",
                bomId, addedCount, unplaced.size()),
            new FurnishPayload(bomId, addedCount,
                placed.toArray(new String[0]),
                unplaced.toArray(new String[0])));
    }

    public record FurnishPayload(
        String bomId, int addedCount,
        String[] placedProducts, String[] unplacedProducts
    ) {}
}
