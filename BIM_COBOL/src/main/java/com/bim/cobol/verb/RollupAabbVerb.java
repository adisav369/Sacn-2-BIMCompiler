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
 * ROLLUP AABB ON &lt;bom_id&gt; — compute envelope AABB from children.
 *
 * <p>iDempiere convention: product dimensions (AABB) live on the child BOM
 * header ({@code m_bom.aabb_*}), not on {@code m_bom_line.allocated_*}.
 * The {@code allocated_*} columns are the strip-packing extension for
 * room/furniture planning (SET level only) — separate concern.
 *
 * <p>Envelope computation per axis:
 * <pre>
 *   width  = MAX(child.dx*1000 + child_bom.aabb_width_mm)  - MIN(child.dx*1000)
 *   depth  = MAX(child.dy*1000 + child_bom.aabb_depth_mm)  - MIN(child.dy*1000)
 *   height = MAX(child.dz*1000 + child_bom.aabb_height_mm) - MIN(child.dz*1000)
 * </pre>
 *
 * <p>Stores the result on the parent BOM's {@code aabb_*} columns via DAO save.
 *
 * <p>Grammar: {@code ROLLUP AABB ON <bom_id>}
 */
public class RollupAabbVerb implements Verb<RollupAabbVerb.RollupPayload> {

    @Override
    public String keyword() { return "ROLLUP AABB"; }

    @Override
    public VerbResult<RollupPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 2 || !"ON".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(),
                "usage: ROLLUP AABB ON <bom_id>", null);

        String bomId = args[1];
        Connection conn = ctx.bomConn();

        // Load parent BOM
        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Load active children
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        if (children.isEmpty())
            return VerbResult.fail(keyword(),
                bomId + " has no active children — nothing to roll up", null);

        // Compute envelope from children's tack positions + their own AABB
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int resolved = 0;

        for (MBOMLine line : children) {
            String childId = line.getChildProductId();
            if (childId == null) continue;

            // Look up child BOM's own AABB (product dimensions)
            MBOM childBom = MBOM.get(conn, childId);
            if (childBom == null) continue;

            int cw = childBom.getAabbWidthMm();
            int cd = childBom.getAabbDepthMm();
            int ch = childBom.getAabbHeightMm();

            // Skip children with zero AABB — they haven't been measured yet
            if (cw == 0 && cd == 0 && ch == 0) continue;

            // Child position in mm (dx/dy/dz are in metres)
            double px = line.getDx() * 1000.0;
            double py = line.getDy() * 1000.0;
            double pz = line.getDz() * 1000.0;

            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px + cw);
            maxY = Math.max(maxY, py + cd);
            maxZ = Math.max(maxZ, pz + ch);
            resolved++;
        }

        if (resolved == 0)
            return VerbResult.fail(keyword(),
                bomId + ": no children have non-zero AABB — run ROLLUP on children first", null);

        int widthMm  = (int) Math.round(maxX - minX);
        int depthMm  = (int) Math.round(maxY - minY);
        int heightMm = (int) Math.round(maxZ - minZ);

        // Store on parent
        parent.setAabbWidthMm(widthMm);
        parent.setAabbDepthMm(depthMm);
        parent.setAabbHeightMm(heightMm);
        parent.save();

        String summary = String.format(
            "ROLLUP %s: %dx%dx%d mm (from %d/%d children)",
            bomId, widthMm, depthMm, heightMm, resolved, children.size());

        return VerbResult.ok(keyword(), summary,
            new RollupPayload(bomId, widthMm, depthMm, heightMm, resolved, children.size()));
    }

    public record RollupPayload(
        String bomId,
        int widthMm, int depthMm, int heightMm,
        int resolvedChildren, int totalChildren
    ) {}
}
