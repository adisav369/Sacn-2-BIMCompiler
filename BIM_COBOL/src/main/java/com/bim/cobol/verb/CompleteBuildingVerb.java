package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * COMPLETE BUILDING &lt;buildingId&gt; DIGEST &lt;sha&gt; ELEMENTS &lt;n&gt; CHECKSUM &lt;cs&gt;
 *
 * <p>Wraps CompilationPipeline.updateCOrderComputedResults() raw SQL.
 * UPDATE c_order SET digest/count/checksum, DocStatus IP-&gt;CO.
 *
 * <p>Requires outputConn (write c_order).
 */
public class CompleteBuildingVerb implements Verb<CompleteBuildingVerb.CompleteBuildingPayload> {

    @Override
    public String keyword() { return "COMPLETE BUILDING"; }

    @Override
    public VerbResult<CompleteBuildingPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: COMPLETE BUILDING <buildingId> DIGEST <sha> ELEMENTS <n> CHECKSUM <cs>", null);

        String buildingId = args[0];
        String digest = null;
        int elements = 0;
        String checksum = null;

        for (int i = 1; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "DIGEST"   -> { digest = val; i++; }
                case "ELEMENTS" -> { elements = Integer.parseInt(val); i++; }
                case "CHECKSUM" -> { checksum = val; i++; }
            }
        }

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — COMPLETE BUILDING writes to output.db", null);

        int updated;
        try (PreparedStatement ps = outputConn.prepareStatement("""
                UPDATE c_order SET
                    SpatialDigest = ?,
                    ExpectedElements = ?,
                    EmptySpaceChecksum = ?,
                    DocStatus = 'CO'
                WHERE C_Order_ID = ?
                """)) {
            ps.setString(1, digest);
            ps.setInt(2, elements);
            ps.setString(3, checksum);
            ps.setString(4, buildingId);
            updated = ps.executeUpdate();
        }

        if (updated == 0)
            return VerbResult.fail(keyword(),
                "no c_order found for " + buildingId,
                new CompleteBuildingPayload(buildingId, "IP", false));

        CompleteBuildingPayload payload = new CompleteBuildingPayload(
            buildingId, "CO", true);

        return VerbResult.ok(keyword(),
            String.format("COMPLETE BUILDING %s: DocStatus=CO, elements=%d",
                buildingId, elements),
            payload);
    }

    public record CompleteBuildingPayload(
        String buildingId, String docStatus, boolean updated
    ) {}
}
