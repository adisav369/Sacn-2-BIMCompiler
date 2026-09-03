package com.bim.backoffice.report;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Door Schedule — BIM-RPT-05.
 *
 * <p>Reads from output.db {@code elements_meta} WHERE ifc_class = 'IfcDoor',
 * joined with {@code elements_rtree} for width/height dimensions.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-05 — Witness: W-RPT-DOOR
 */
public class DoorScheduleReport {

    private static final String TAG = "DoorScheduleReport";

    public record DoorLine(String guid, String mark, String doorType,
                           double widthMm, double heightMm,
                           String storey, String hostWall,
                           String fireRating, String hardware) {}

    public record DoorSchedule(String buildingId, int doorCount,
                               List<DoorLine> doors) {}

    /**
     * Generate door schedule from output.db.
     *
     * <p>elements_meta has ifc_class, element_name (mark), element_type (door type),
     * storey, fire_rating_hr. AABB from elements_rtree. Host wall not directly
     * available in output schema (would need rel_fills_host which is reference-only).
     */
    public DoorSchedule generate(Connection outputConn, String buildingId) throws SQLException {
        List<DoorLine> doors = new ArrayList<>();

        String sql = """
                SELECT em.guid, em.element_name, em.element_type,
                       em.storey, em.fire_rating_hr, em.element_ref,
                       rt.maxX - rt.minX AS width_m,
                       rt.maxZ - rt.minZ AS height_m
                FROM elements_meta em
                LEFT JOIN elements_rtree rt ON rt.id = em.id
                WHERE em.ifc_class = 'IfcDoor'
                ORDER BY em.storey, em.element_name
                """;

        try (PreparedStatement ps = outputConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double widthMm = rs.getDouble("width_m") * 1000.0;
                double heightMm = rs.getDouble("height_m") * 1000.0;

                double fireHr = rs.getDouble("fire_rating_hr");
                String fireRating = rs.wasNull() ? "\u2014"
                        : String.format("%.1f hr", fireHr);

                doors.add(new DoorLine(
                        rs.getString("guid"),
                        rs.getString("element_name") != null ? rs.getString("element_name") : "\u2014",
                        rs.getString("element_type") != null ? rs.getString("element_type") : "\u2014",
                        Math.round(widthMm * 10.0) / 10.0,
                        Math.round(heightMm * 10.0) / 10.0,
                        rs.getString("storey") != null ? rs.getString("storey") : "\u2014",
                        "\u2014",  // host wall — not in output schema
                        fireRating,
                        "\u2014"   // hardware — not in output schema
                ));
            }
        }

        BIMLogger.info(TAG, "{} doors found for {}", doors.size(), buildingId);
        return new DoorSchedule(buildingId, doors.size(), doors);
    }
}
