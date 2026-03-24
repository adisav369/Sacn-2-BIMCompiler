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
 * SELECT BOM &lt;bom_id&gt; [WHERE &lt;field&gt; = &lt;value&gt;] — query BOM tree children.
 *
 * <p>Returns a tabular listing of all BOM children matching the filter.
 * If no WHERE clause, lists all active children.
 *
 * <p>Supported WHERE fields: component_type, role, child_product_id, locator_ref.
 *
 * <p>Examples:
 * <pre>
 * SELECT BOM EB_SH
 * SELECT BOM EB_DX WHERE component_type = BUY
 * SELECT BOM DUPLEX_SET_STD WHERE role = KITCHEN
 * </pre>
 *
 * <p>Read-only — no DB writes.
 */
public class SelectBomVerb implements Verb<SelectBomVerb.SelectBomPayload> {

    @Override
    public String keyword() { return "SELECT BOM"; }

    @Override
    public VerbResult<SelectBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: SELECT BOM <bom_id> [WHERE <field> = <value>]", null);

        String bomId = args[0];
        Connection conn = ctx.bomConn();

        MBOM bom = MBOM.get(conn, bomId);
        if (bom == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Parse optional WHERE clause
        String filterField = null;
        String filterValue = null;
        if (args.length >= 4 && "WHERE".equalsIgnoreCase(args[1])
                && "=".equals(args[3])) {
            // SELECT BOM <id> WHERE <field> = <value>
            filterField = args[2].toLowerCase();
            filterValue = args.length > 4 ? args[4] : "";
        } else if (args.length >= 4 && "WHERE".equalsIgnoreCase(args[1])) {
            // Try: SELECT BOM <id> WHERE <field> = <value> (= may be part of args[3])
            filterField = args[2].toLowerCase();
            filterValue = args[3];
        }

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        List<ChildRow> rows = new ArrayList<>();

        for (MBOMLine line : children) {
            if (filterField != null && !matchesFilter(line, filterField, filterValue))
                continue;

            rows.add(new ChildRow(
                line.getBomChildId(),
                line.getBomId(),
                line.getChildProductId(),
                line.getComponentType(),
                line.getRole(),
                line.getLocatorRef(),
                line.getSequence(),
                line.getDx(), line.getDy(), line.getDz(),
                line.getRotationRule(),
                line.getAllocatedWidthMm(),
                line.getAllocatedDepthMm(),
                line.getAllocatedHeightMm(),
                line.isVariance()
            ));
        }

        SelectBomPayload payload = new SelectBomPayload(
            bomId, bom.getBomName(), bom.getBomType(), bom.getProductCategory(),
            children.size(), rows.size(), filterField, filterValue, rows);

        String filterDesc = filterField != null
            ? String.format(" (WHERE %s=%s)", filterField, filterValue)
            : "";

        return VerbResult.ok(keyword(),
            String.format("%s: %d/%d children%s, type=%s, category=%s",
                bomId, rows.size(), children.size(), filterDesc,
                bom.getBomType(), bom.getProductCategory()),
            payload);
    }

    private boolean matchesFilter(MBOMLine line, String field, String value) {
        String actual = switch (field) {
            case "component_type" -> line.getComponentType();
            case "role"           -> line.getRole();
            case "child_product_id" -> line.getChildProductId();
            case "locator_ref"    -> line.getLocatorRef();
            case "storey"         -> line.getStorey();
            case "orientation"    -> line.getOrientation();
            default               -> null;
        };
        if (actual == null) return value == null || value.isEmpty();
        return actual.equalsIgnoreCase(value);
    }

    public record ChildRow(
        int bomChildId, String bomId, String childProductId,
        String componentType, String role, String locatorRef,
        int sequence, double dx, double dy, double dz,
        String rotationRule,
        int allocatedWidthMm, int allocatedDepthMm, int allocatedHeightMm,
        boolean isVariance
    ) {}

    public record SelectBomPayload(
        String bomId, String bomName, String bomType, String productCategory,
        int totalChildren, int matchedChildren,
        String filterField, String filterValue,
        List<ChildRow> rows
    ) {}
}
