package com.bim.backoffice.report;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Window Schedule — BIM-RPT-06.
 *
 * <p>Reads from output.db {@code elements_meta} WHERE ifc_class = 'IfcWindow',
 * joined with {@code elements_rtree} for width/height dimensions.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-06 — Witness: W-RPT-WINDOW
 */
public class WindowScheduleReport {

    private static final String TAG = "WindowScheduleReport";

    public record WindowLine(String guid, String mark, String windowType,
                             double widthMm, double heightMm,
                             String storey, String glazingType,
                             String uValue) {}

    public record WindowSchedule(String buildingId, int windowCount,
                                 List<WindowLine> windows) {}

    /**
     * Generate window schedule from output.db.
     *
     * <p>elements_meta has ifc_class, element_name (mark), element_type (window type),
     * storey. AABB from elements_rtree. Glazing type and U-value are not in the
     * current output schema — output as dash.
     */
    public WindowSchedule generate(Connection outputConn, String buildingId) throws SQLException {
        List<WindowLine> windows = new ArrayList<>();

        String sql = """
                SELECT em.guid, em.element_name, em.element_type,
                       em.storey,
                       rt.maxX - rt.minX AS width_m,
                       rt.maxZ - rt.minZ AS height_m
                FROM elements_meta em
                LEFT JOIN elements_rtree rt ON rt.id = em.id
                WHERE em.ifc_class = 'IfcWindow'
                ORDER BY em.storey, em.element_name
                """;

        try (PreparedStatement ps = outputConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double widthMm = rs.getDouble("width_m") * 1000.0;
                double heightMm = rs.getDouble("height_m") * 1000.0;

                windows.add(new WindowLine(
                        rs.getString("guid"),
                        rs.getString("element_name") != null ? rs.getString("element_name") : "\u2014",
                        rs.getString("element_type") != null ? rs.getString("element_type") : "\u2014",
                        Math.round(widthMm * 10.0) / 10.0,
                        Math.round(heightMm * 10.0) / 10.0,
                        rs.getString("storey") != null ? rs.getString("storey") : "\u2014",
                        "\u2014",  // glazing type — not in output schema
                        "\u2014"   // U-value — not in output schema
                ));
            }
        }

        BIMLogger.info(TAG, "{} windows found for {}", windows.size(), buildingId);
        return new WindowSchedule(buildingId, windows.size(), windows);
    }
}
