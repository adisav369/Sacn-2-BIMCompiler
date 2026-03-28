package com.bim.cobol.forge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

/**
 * Writes ForgeResult fabrication data to ad_forge_fabrication in output.db.
 *
 * <p>Each GeometryRecord's fabrication map becomes N rows (one per parameter).
 * The standard dimensions (length, width, depth) are also written as fabrication
 * rows so the cut list is self-contained.
 *
 * <p>Transaction ownership: caller must commit. This class does not call commit().
 *
 * // Implementing FORGE_SUITE_SRS.md §9 Part ⑤ — Witness: W-FORGE-FAB-1
 */
public class ForgeFabricationWriter {

    private static final String INSERT_SQL =
            "INSERT INTO ad_forge_fabrication "
            + "(W_Verb_Node_ID, Element_ID, PieceType, ParamName, ParamValue, Unit) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    private final Connection conn;

    public ForgeFabricationWriter(Connection conn) {
        this.conn = conn;
    }

    /**
     * Persist all fabrication data from a ForgeResult.
     *
     * @param verbNodeId  the W_Verb_Node_ID that produced this result (nullable)
     * @param result      the ForgeResult from ForgeEngine.compute()
     * @return number of ad_forge_fabrication rows written
     */
    public int write(Integer verbNodeId, ForgeResult result) throws SQLException {
        if (!result.pass() || result.records().isEmpty()) {
            return 0;
        }

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (int i = 0; i < result.records().size(); i++) {
                GeometryRecord rec = result.records().get(i);
                String elementId = rec.bomLineId() != null ? rec.bomLineId()
                        : rec.productId() != null ? rec.productId()
                        : result.pieceType() + "_" + i;

                // Standard dimensions as fabrication rows
                count += writeParam(ps, verbNodeId, elementId, result.pieceType(),
                        "length_mm", rec.lengthMm(), "mm");
                count += writeParam(ps, verbNodeId, elementId, result.pieceType(),
                        "width_mm", rec.widthMm(), "mm");
                count += writeParam(ps, verbNodeId, elementId, result.pieceType(),
                        "depth_mm", rec.depthMm(), "mm");

                // Fabrication map entries (cut angles, notch depth, step count, etc.)
                for (Map.Entry<String, Double> entry : rec.fabrication().entrySet()) {
                    String unit = inferUnit(entry.getKey());
                    count += writeParam(ps, verbNodeId, elementId, result.pieceType(),
                            entry.getKey(), entry.getValue(), unit);
                }
            }
        }
        return count;
    }

    private int writeParam(PreparedStatement ps, Integer verbNodeId,
                           String elementId, String pieceType,
                           String paramName, double paramValue, String unit)
            throws SQLException {
        if (verbNodeId != null) {
            ps.setInt(1, verbNodeId);
        } else {
            ps.setNull(1, java.sql.Types.INTEGER);
        }
        ps.setString(2, elementId);
        ps.setString(3, pieceType);
        ps.setString(4, paramName);
        ps.setDouble(5, paramValue);
        ps.setString(6, unit);
        ps.executeUpdate();
        return 1;
    }

    /**
     * Write all fabrication data from a list of ForgeResults.
     *
     * @param results list of ForgeResult objects
     * @return total number of rows written
     */
    public int writeAll(java.util.List<ForgeResult> results) throws SQLException {
        int total = 0;
        for (ForgeResult result : results) {
            total += write(null, result);
        }
        return total;
    }

    /**
     * Infer unit from parameter name. Follows forge engine conventions.
     */
    static String inferUnit(String paramName) {
        if (paramName == null) return null;
        String lower = paramName.toLowerCase();
        if (lower.endsWith("_mm") || lower.contains("length") || lower.contains("depth")
                || lower.contains("width") || lower.contains("radius")
                || lower.contains("rise") || lower.contains("span")) {
            return "mm";
        }
        if (lower.contains("angle") || lower.contains("pitch")) {
            return "deg";
        }
        if (lower.contains("count") || lower.contains("steps") || lower.contains("rings")
                || lower.contains("segments") || lower.contains("ribs")) {
            return "ea";
        }
        if (lower.contains("cost")) {
            return "RM";
        }
        if (lower.contains("hours") || lower.contains("labour")) {
            return "hr";
        }
        return null;
    }
}
