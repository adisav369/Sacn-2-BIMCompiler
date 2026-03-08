package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * DELETE BOM &lt;bom_id&gt;
 *
 * <p>Level 0 primitive (§18.4). Deletes an {@code m_bom} row and all its
 * {@code m_bom_line} children. Fails if the BOM is referenced as a child
 * by another BOM (referential integrity).
 *
 * <p>Only SY_ prefix BOMs can be deleted (safety guard against
 * deleting extracted or walk-through reference BOMs).
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class DeleteBomVerb implements Verb<DeleteBomVerb.DeleteBomPayload> {

    @Override
    public String keyword() { return "DELETE BOM"; }

    @Override
    public VerbResult<DeleteBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: DELETE BOM <bom_id>", null);

        String bomId = args[0];

        // Safety: only SY_ BOMs can be deleted
        if (!bomId.startsWith("SY_"))
            return VerbResult.fail(keyword(),
                "only SY_ prefix BOMs can be deleted, got: " + bomId, null);

        Connection conn = ctx.bomConn();

        MBOM bom = MBOM.get(conn, bomId);
        if (bom == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Referential integrity: check if any other BOM references this as a child
        int refCount = countReferences(conn, bomId);
        if (refCount > 0)
            return VerbResult.fail(keyword(),
                bomId + " is referenced by " + refCount + " parent BOM line(s) — remove references first", null);

        // Delete children first
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine child : children) {
            child.delete();
        }

        // Delete the BOM itself
        bom.delete();

        DeleteBomPayload payload = new DeleteBomPayload(bomId, children.size());

        return VerbResult.ok(keyword(),
            String.format("DELETE BOM %s: deleted %d lines + header",
                bomId, children.size()),
            payload);
    }

    /**
     * Count how many m_bom_line rows reference this bom_id as child_product_id.
     */
    private int countReferences(Connection conn, String bomId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM m_bom_line WHERE child_product_id = ? AND is_active = 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public record DeleteBomPayload(String bomId, int deletedLineCount) {}
}
