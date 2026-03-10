package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * VOID EMPTY_SPACE FOR BUILDING &lt;buildingType&gt;
 *
 * <p>Voids all non-void WMS EmptyStorage lines for a building — called at start
 * of recompilation to invalidate stale fill state before fresh BOM resolution.
 *
 * <p>Replaces {@code M_WmEmptyStorageLine.voidForBuilding()} raw SQL.
 * Writes to output.db — requires outputConn to be non-null.
 */
public class VoidEmptySpaceVerb implements Verb<VoidEmptySpaceVerb.VoidEmptySpacePayload> {

    @Override
    public String keyword() { return "VOID EMPTY_SPACE FOR BUILDING"; }

    @Override
    public VerbResult<VoidEmptySpacePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: VOID EMPTY_SPACE FOR BUILDING <buildingType>", null);

        String buildingType = args[0];
        Connection conn = ctx.outputConn();

        if (conn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — WMS lines live in output.db", null);

        int voidedCount;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE wm_empty_storage_line"
                + " SET doc_status = 'VO', updated = datetime('now')"
                + " WHERE building_type = ? AND doc_status != 'VO'")) {
            ps.setString(1, buildingType);
            voidedCount = ps.executeUpdate();
        }

        VoidEmptySpacePayload payload = new VoidEmptySpacePayload(buildingType, voidedCount);

        return VerbResult.ok(keyword(),
            String.format("VOID EMPTY_SPACE FOR BUILDING %s: %d lines voided",
                buildingType, voidedCount),
            payload);
    }

    public record VoidEmptySpacePayload(String buildingType, int voidedCount) {}
}
