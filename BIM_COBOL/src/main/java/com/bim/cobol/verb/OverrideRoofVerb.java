package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;

/**
 * OVERRIDE ROOF &lt;doc_sub_type&gt;
 *
 * <p>Wraps BuildingWriter.overrideRoofPosition() — patches IfcRoof bounding box
 * and metadata (storey, material) in output element tables.
 *
 * <p>For roof index 0: updates existing IfcRoof element.
 * For additional roofs: emits new elements.
 *
 * <p>Requires outputConn (write elements_rtree, elements_meta, element_instances).
 */
public class OverrideRoofVerb implements Verb<OverrideRoofVerb.OverrideRoofPayload> {

    @Override
    public String keyword() { return "OVERRIDE ROOF"; }

    @Override
    public VerbResult<OverrideRoofPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: OVERRIDE ROOF <doc_sub_type> [ID <id>] " +
                "MINX <v> MAXX <v> MINY <v> MAXY <v> MINZ <v> MAXZ <v> " +
                "STOREY <s> [MATERIAL_NAME <n>] [MATERIAL_RGBA <r>] [GEO_HASH <h>]", null);

        String docSubType = args[0];

        // Parse named params
        int roofId = -1;
        double minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
        String storey = null, materialName = null, materialRgba = null, geoHash = null;
        boolean hasCoords = false;

        for (int i = 1; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "ID"             -> { roofId = Integer.parseInt(val); i++; }
                case "MINX"           -> { minX = Double.parseDouble(val); hasCoords = true; i++; }
                case "MAXX"           -> { maxX = Double.parseDouble(val); i++; }
                case "MINY"           -> { minY = Double.parseDouble(val); i++; }
                case "MAXY"           -> { maxY = Double.parseDouble(val); i++; }
                case "MINZ"           -> { minZ = Double.parseDouble(val); i++; }
                case "MAXZ"           -> { maxZ = Double.parseDouble(val); i++; }
                case "STOREY"         -> { storey = val; i++; }
                case "MATERIAL_NAME"  -> { materialName = val; i++; }
                case "MATERIAL_RGBA"  -> { materialRgba = val; i++; }
                case "GEO_HASH"       -> { geoHash = val; i++; }
            }
        }

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — OVERRIDE ROOF writes to output.db", null);

        // Find existing IfcRoof if no ID specified
        if (roofId < 0) {
            try (Statement stmt = outputConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT m.id FROM elements_meta m WHERE m.ifc_class = 'IfcRoof' LIMIT 1")) {
                if (rs.next()) roofId = rs.getInt(1);
            }
        }

        if (roofId < 0)
            return VerbResult.fail(keyword(),
                "no IfcRoof element found to override for " + docSubType, null);

        int updated = 0;

        // Update R-tree bounding box
        if (hasCoords) {
            try (PreparedStatement ps = outputConn.prepareStatement(
                    "UPDATE elements_rtree SET minX=?, maxX=?, minY=?, maxY=?, minZ=?, maxZ=? WHERE id=?")) {
                ps.setDouble(1, minX);
                ps.setDouble(2, maxX);
                ps.setDouble(3, minY);
                ps.setDouble(4, maxY);
                ps.setDouble(5, minZ);
                ps.setDouble(6, maxZ);
                ps.setInt(7, roofId);
                updated += ps.executeUpdate();
            }
        }

        // Update metadata (storey + material)
        if (storey != null) {
            try (PreparedStatement ps = outputConn.prepareStatement(
                    "UPDATE elements_meta SET storey=?, material_name=?, material_rgba=? WHERE id=?")) {
                ps.setString(1, storey);
                ps.setString(2, materialName);
                ps.setString(3, materialRgba);
                ps.setInt(4, roofId);
                updated += ps.executeUpdate();
            }
        }

        // Update geometry hash if provided
        if (geoHash != null) {
            try (PreparedStatement ps = outputConn.prepareStatement(
                    "UPDATE element_instances SET geometry_hash=? WHERE guid=(SELECT guid FROM elements_meta WHERE id=?)")) {
                ps.setString(1, geoHash);
                ps.setInt(2, roofId);
                updated += ps.executeUpdate();
            }
        }

        OverrideRoofPayload payload = new OverrideRoofPayload(docSubType, roofId, updated);

        return VerbResult.ok(keyword(),
            String.format("OVERRIDE ROOF %s: id=%d, %d updates applied",
                docSubType, roofId, updated),
            payload);
    }

    public record OverrideRoofPayload(
        String docSubType, int roofId, int updatesApplied
    ) {}
}
