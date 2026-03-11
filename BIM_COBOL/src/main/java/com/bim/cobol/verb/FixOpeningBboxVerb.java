package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;

/**
 * FIX OPENING BBOX &lt;doc_sub_type&gt; GUID &lt;guid&gt;
 *     MINX &lt;v&gt; MAXX &lt;v&gt; MINY &lt;v&gt; MAXY &lt;v&gt; MINZ &lt;v&gt; MAXZ &lt;v&gt;
 *
 * <p>Wraps BuildingWriter.fixOpeningPositions() — patches door/window bounding boxes
 * in elements_rtree by GUID lookup.
 *
 * <p>Processes one element per invocation. The caller (BuildingWriter) dispatches
 * one verb line per door/window element to fix.
 *
 * <p>Requires outputConn (write elements_rtree).
 */
public class FixOpeningBboxVerb implements Verb<FixOpeningBboxVerb.FixOpeningPayload> {

    @Override
    public String keyword() { return "FIX OPENING BBOX"; }

    @Override
    public VerbResult<FixOpeningPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: FIX OPENING BBOX <doc_sub_type> GUID <guid> " +
                "MINX <v> MAXX <v> MINY <v> MAXY <v> MINZ <v> MAXZ <v>", null);

        String docSubType = args[0];

        // Parse named params
        String guid = null;
        double minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;

        for (int i = 1; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "GUID" -> { guid = val; i++; }
                case "MINX" -> { minX = Double.parseDouble(val); i++; }
                case "MAXX" -> { maxX = Double.parseDouble(val); i++; }
                case "MINY" -> { minY = Double.parseDouble(val); i++; }
                case "MAXY" -> { maxY = Double.parseDouble(val); i++; }
                case "MINZ" -> { minZ = Double.parseDouble(val); i++; }
                case "MAXZ" -> { maxZ = Double.parseDouble(val); i++; }
            }
        }

        if (guid == null)
            return VerbResult.fail(keyword(), "GUID is required", null);

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — FIX OPENING BBOX writes to output.db", null);

        // Look up element ID by GUID
        int elementId = -1;
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT id FROM elements_meta WHERE guid = ?")) {
            ps.setString(1, guid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) elementId = rs.getInt(1);
            }
        }

        if (elementId < 0)
            return VerbResult.fail(keyword(),
                "no element found for GUID " + guid, null);

        // Update R-tree bounding box
        try (PreparedStatement ps = outputConn.prepareStatement(
                "UPDATE elements_rtree SET minX=?, maxX=?, minY=?, maxY=?, minZ=?, maxZ=? WHERE id=?")) {
            ps.setDouble(1, minX);
            ps.setDouble(2, maxX);
            ps.setDouble(3, minY);
            ps.setDouble(4, maxY);
            ps.setDouble(5, minZ);
            ps.setDouble(6, maxZ);
            ps.setInt(7, elementId);
            ps.executeUpdate();
        }

        FixOpeningPayload payload = new FixOpeningPayload(docSubType, guid, elementId);

        return VerbResult.ok(keyword(),
            String.format("FIX OPENING BBOX %s: guid=%s, id=%d bbox updated",
                docSubType, guid, elementId),
            payload);
    }

    public record FixOpeningPayload(
        String docSubType, String guid, int elementId
    ) {}
}
