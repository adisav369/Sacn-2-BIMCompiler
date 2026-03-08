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
 * SET TACK ON &lt;bom_id&gt; LINE &lt;role_or_child&gt; [DX &lt;dx&gt;] [DY &lt;dy&gt;] [DZ &lt;dz&gt;]
 *
 * <p>Level 0 primitive (§18.4). Updates dx/dy/dz on an existing {@code m_bom_line}.
 * Matches the target line by role (preferred) or child_product_id.
 * Only updates axes that are explicitly specified — omitted axes are left unchanged.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class SetTackVerb implements Verb<SetTackVerb.SetTackPayload> {

    @Override
    public String keyword() { return "SET TACK"; }

    @Override
    public VerbResult<SetTackPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: SET TACK ON <bom_id> LINE <role_or_child> [DX n] [DY n] [DZ n]
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: SET TACK ON <bom_id> LINE <role_or_child> [DX n] [DY n] [DZ n]", null);

        if (!"ON".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(), "expected ON after SET TACK", null);

        String bomId = args[1];

        if (!"LINE".equalsIgnoreCase(args[2]))
            return VerbResult.fail(keyword(), "expected LINE after bom_id", null);

        String lineRef = args[3];

        // Parse optional DX/DY/DZ
        Double dx = null, dy = null, dz = null;
        for (int i = 4; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "DX" -> { dx = Double.parseDouble(val); i++; }
                case "DY" -> { dy = Double.parseDouble(val); i++; }
                case "DZ" -> { dz = Double.parseDouble(val); i++; }
            }
        }

        if (dx == null && dy == null && dz == null)
            return VerbResult.fail(keyword(),
                "at least one of DX, DY, DZ must be specified", null);

        Connection conn = ctx.bomConn();

        // Validate parent BOM exists
        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Find matching line by role or child_product_id
        MBOMLine target = findLine(conn, bomId, lineRef);
        if (target == null)
            return VerbResult.fail(keyword(),
                "no line matching '" + lineRef + "' in " + bomId, null);

        // Update only specified axes
        if (dx != null) target.setDx(dx);
        if (dy != null) target.setDy(dy);
        if (dz != null) target.setDz(dz);
        target.save();

        SetTackPayload payload = new SetTackPayload(
            bomId, lineRef, target.getBomChildId(),
            target.getDx(), target.getDy(), target.getDz());

        return VerbResult.ok(keyword(),
            String.format("SET TACK %s.%s → dx=%.3f dy=%.3f dz=%.3f",
                bomId, lineRef, target.getDx(), target.getDy(), target.getDz()),
            payload);
    }

    /**
     * Find a line by role first, then by child_product_id.
     */
    static MBOMLine findLine(Connection conn, String bomId, String ref)
            throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        // Try role match first
        for (MBOMLine line : children) {
            if (ref.equalsIgnoreCase(line.getRole())) return line;
        }
        // Fall back to child_product_id match
        for (MBOMLine line : children) {
            if (ref.equals(line.getChildProductId())) return line;
        }
        return null;
    }

    public record SetTackPayload(
        String bomId, String lineRef, int bomChildId,
        double dx, double dy, double dz
    ) {}
}
