package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * CLEAR VARIANCE FROM BOM &lt;bomId&gt;
 *
 * <p>Wraps TopologyWriter.fillBuffers() step 2: delete variance (buffer) rows
 * from m_bom_line. Prepares a SET BOM for re-fill.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class ClearVarianceVerb implements Verb<ClearVarianceVerb.ClearVariancePayload> {

    @Override
    public String keyword() { return "CLEAR VARIANCE FROM BOM"; }

    @Override
    public VerbResult<ClearVariancePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: CLEAR VARIANCE FROM BOM <bomId>", null);

        String bomId = args[0];
        Connection conn = ctx.bomConn();

        int deletedCount;
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM m_bom_line WHERE bom_id = ? AND is_variance = 1")) {
            del.setString(1, bomId);
            deletedCount = del.executeUpdate();
        }

        ClearVariancePayload payload = new ClearVariancePayload(bomId, deletedCount);

        return VerbResult.ok(keyword(),
            String.format("CLEAR VARIANCE FROM BOM %s: %d rows deleted", bomId, deletedCount),
            payload);
    }

    public record ClearVariancePayload(String bomId, int deletedCount) {}
}
