package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * EXTRACT AABB FROM POINTS &lt;p1&gt; &lt;p2&gt;
 * EXTRACT AABB FROM BOM &lt;bom_id&gt;
 *
 * <p>§18.5 Utility verb. Computes axis-aligned bounding box from coordinates
 * or from an existing BOM's child space. Turns a Bonsai drag-box into
 * verb parameters. Read-only — does not mutate anything.
 *
 * <p>The payload feeds directly into COMPOSE BUILDING, CREATE FLOOR,
 * CREATE ROOM, and RESIZE ROOM as their AABB parameters.
 */
public class ExtractAabbVerb implements Verb<ExtractAabbVerb.AabbPayload> {

    @Override
    public String keyword() { return "EXTRACT AABB"; }

    @Override
    public VerbResult<AabbPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 2)
            return VerbResult.fail(keyword(),
                "usage: EXTRACT AABB FROM POINTS (x1,y1,z1) (x2,y2,z2) | FROM BOM <bom_id>", null);

        if (!"FROM".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(), "expected FROM after EXTRACT AABB", null);

        String source = args[1].toUpperCase();

        return switch (source) {
            case "POINTS" -> extractFromPoints(args);
            case "BOM"    -> extractFromBom(ctx.bomConn(), args);
            default       -> VerbResult.fail(keyword(),
                "expected POINTS or BOM, got: " + source, null);
        };
    }

    /**
     * EXTRACT AABB FROM POINTS (x1,y1,z1) (x2,y2,z2)
     * Coordinates in metres, output in mm.
     */
    private VerbResult<AabbPayload> extractFromPoints(String... args) {
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: EXTRACT AABB FROM POINTS (x1,y1,z1) (x2,y2,z2)", null);

        double[] p1 = parsePoint(args[2]);
        double[] p2 = parsePoint(args[3]);

        if (p1 == null || p2 == null)
            return VerbResult.fail(keyword(),
                "invalid point format — use (x,y,z) e.g. (0,0,0)", null);

        double minX = Math.min(p1[0], p2[0]);
        double minY = Math.min(p1[1], p2[1]);
        double minZ = Math.min(p1[2], p2[2]);
        double maxX = Math.max(p1[0], p2[0]);
        double maxY = Math.max(p1[1], p2[1]);
        double maxZ = Math.max(p1[2], p2[2]);

        int widthMm  = (int) Math.round((maxX - minX) * 1000);
        int depthMm  = (int) Math.round((maxY - minY) * 1000);
        int heightMm = (int) Math.round((maxZ - minZ) * 1000);

        AabbPayload payload = new AabbPayload(
            widthMm, depthMm, heightMm,
            minX, minY, minZ, maxX, maxY, maxZ);

        return VerbResult.ok(keyword(),
            String.format("AABB from points: %dx%dx%d mm", widthMm, depthMm, heightMm),
            payload);
    }

    /**
     * EXTRACT AABB FROM BOM &lt;bom_id&gt;
     * Reads allocated dimensions from m_bom's children.
     */
    private VerbResult<AabbPayload> extractFromBom(Connection conn, String... args)
            throws SQLException {

        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: EXTRACT AABB FROM BOM <bom_id>", null);

        String bomId = args[2];
        MBOM bom = MBOM.get(conn, bomId);
        if (bom == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        int[] space = MBOM.computeTotalChildSpace(conn, bomId);
        int widthMm = space[0], depthMm = space[1], heightMm = space[2];

        AabbPayload payload = new AabbPayload(
            widthMm, depthMm, heightMm,
            bom.getOriginX(), bom.getOriginY(), bom.getOriginZ(),
            bom.getOriginX() + widthMm / 1000.0,
            bom.getOriginY() + depthMm / 1000.0,
            bom.getOriginZ() + heightMm / 1000.0);

        return VerbResult.ok(keyword(),
            String.format("AABB from BOM %s: %dx%dx%d mm", bomId, widthMm, depthMm, heightMm),
            payload);
    }

    /**
     * Parse a point string: "(x,y,z)" or "x,y,z".
     * Returns double[3] or null if invalid.
     */
    static double[] parsePoint(String s) {
        String clean = s.replace("(", "").replace(")", "").trim();
        String[] parts = clean.split(",");
        if (parts.length != 3) return null;
        try {
            return new double[]{
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record AabbPayload(
        int widthMm, int depthMm, int heightMm,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}
}
