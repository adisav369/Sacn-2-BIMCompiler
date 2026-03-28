package com.bim.cobol.forge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Writes ForgeResult fabrication maps to ad_forge_fabrication in output.db.
 *
 * // Implementing FORGE_SUITE_SRS.md §9 Part ⑤ — Witness: W-FORGE-FAB
 */
public class ForgeFabricationWriter {

    private static final String INSERT_SQL =
        "INSERT INTO AD_Forge_Fabrication (Element_ID, PieceType, Name, Value, Unit) VALUES (?, ?, ?, ?, ?)";

    private final Connection conn;

    public ForgeFabricationWriter(Connection conn) {
        this.conn = conn;
    }

    /**
     * Write all fabrication entries from a ForgeResult.
     * Each GeometryRecord's fabrication map entries become rows.
     */
    public int write(ForgeResult result) throws SQLException {
        if (result == null || !result.pass() || result.records().isEmpty()) return 0;

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (GeometryRecord rec : result.records()) {
                Map<String, Double> fab = rec.fabrication();
                if (fab == null || fab.isEmpty()) continue;

                // Element_ID: use bomLineId if available, else productId, else "FORGE_" + index
                String elementId = rec.bomLineId() != null ? rec.bomLineId()
                    : rec.productId() != null ? rec.productId()
                    : "FORGE_" + count;

                for (Map.Entry<String, Double> entry : fab.entrySet()) {
                    ps.setString(1, elementId);
                    ps.setString(2, result.pieceType());
                    ps.setString(3, entry.getKey());
                    ps.setDouble(4, entry.getValue());
                    ps.setString(5, guessUnit(entry.getKey()));
                    ps.addBatch();
                    count++;
                }
            }
            ps.executeBatch();
        }
        return count;
    }

    /**
     * Write fabrication data from a list of ForgeResults.
     */
    public int writeAll(List<ForgeResult> results) throws SQLException {
        int total = 0;
        for (ForgeResult r : results) {
            total += write(r);
        }
        return total;
    }

    private static String guessUnit(String paramName) {
        if (paramName.contains("angle") || paramName.contains("pitch") || paramName.contains("degrees"))
            return "degrees";
        if (paramName.contains("depth") || paramName.contains("length") || paramName.contains("width")
                || paramName.contains("radius") || paramName.contains("height") || paramName.contains("spacing"))
            return "mm";
        if (paramName.contains("count") || paramName.contains("step") || paramName.contains("segment"))
            return "count";
        if (paramName.contains("weight"))
            return "kg";
        return null;
    }
}
