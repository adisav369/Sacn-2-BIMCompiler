package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * LIST BOMS [&lt;prefix&gt;] — enumerate BOMs in BOM.db.
 *
 * <p>Lists all active BOMs, optionally filtered by ID prefix.
 * Returns bom_id, bom_name, bom_type, m_product_category_id, doc_sub_type for each.
 *
 * <p>Examples:
 * <pre>
 * LIST BOMS
 * LIST BOMS EB_
 * LIST BOMS WT_
 * LIST BOMS FLOOR_
 * </pre>
 *
 * <p>Read-only — no DB writes.
 */
public class ListBomVerb implements Verb<ListBomVerb.ListBomPayload> {

    @Override
    public String keyword() { return "LIST BOMS"; }

    @Override
    public VerbResult<ListBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        Connection conn = ctx.bomConn();
        String prefix = args.length > 0 ? args[0] : null;

        List<MBOM> boms;
        if (prefix != null) {
            boms = MBOM.getByBomIdPrefix(conn, prefix);
        } else {
            boms = MBOM.getByType(conn, "BUILDING");
            // Also get FLOOR, SET, ITEM — merge all types
            List<MBOM> all = new ArrayList<>(boms);
            for (String type : List.of("FLOOR", "SET", "ITEM", "ROOM")) {
                all.addAll(MBOM.getByType(conn, type));
            }
            boms = all;
        }

        List<BomEntry> entries = new ArrayList<>();
        for (MBOM bom : boms) {
            entries.add(new BomEntry(
                bom.getBomId(), bom.getBomName(),
                bom.getBomType(), bom.getProductCategory(),
                bom.getDocSubType(), bom.getSeqNo()));
        }

        // Sort by bom_id
        entries.sort((a, b) -> a.bomId().compareTo(b.bomId()));

        String filterDesc = prefix != null ? " (prefix=" + prefix + ")" : "";
        ListBomPayload payload = new ListBomPayload(entries.size(), prefix, entries);

        return VerbResult.ok(keyword(),
            String.format("LIST BOMS%s: %d BOMs found", filterDesc, entries.size()),
            payload);
    }

    public record BomEntry(
        String bomId, String bomName,
        String bomType, String productCategory,
        String docSubType, int seqNo
    ) {}

    public record ListBomPayload(
        int count, String prefix, List<BomEntry> entries
    ) {}
}
