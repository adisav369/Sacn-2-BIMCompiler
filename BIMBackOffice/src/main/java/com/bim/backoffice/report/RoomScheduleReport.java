package com.bim.backoffice.report;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Room Schedule — BIM-RPT-04.
 *
 * <p>Reads from output.db {@code spatial_structure} joined with
 * {@code elements_rtree} for AABB dimensions. Rooms are IfcSpace
 * entries in spatial_structure (type column).
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-04 — Witness: W-RPT-ROOM
 */
public class RoomScheduleReport {

    private static final String TAG = "RoomScheduleReport";

    public record RoomLine(String guid, String name, String storey,
                           double widthMm, double depthMm, double heightMm,
                           double areaM2, double perimeterM,
                           String objectType, String compliance) {}

    public record RoomSchedule(String buildingId, int roomCount,
                               List<RoomLine> rooms) {}

    /**
     * Generate room schedule from output.db.
     *
     * <p>spatial_structure holds the hierarchy (IfcSpace rows have type='IfcSpace').
     * Parent IfcBuildingStorey provides storey name. AABB comes from elements_rtree
     * joined via elements_meta.id. Area = width × depth / 1e6 (m²).
     */
    public RoomSchedule generate(Connection outputConn, String buildingId) throws SQLException {
        List<RoomLine> rooms = new ArrayList<>();

        // IfcSpace lives in spatial_structure. Parent = storey (IfcBuildingStorey).
        // AABB from elements_rtree via elements_meta (matched by guid).
        String sql = """
                SELECT ss.guid, ss.name,
                       parent_ss.name AS storey,
                       ss.object_type,
                       em.id AS em_id,
                       rt.maxX - rt.minX AS width_m,
                       rt.maxY - rt.minY AS depth_m,
                       rt.maxZ - rt.minZ AS height_m
                FROM spatial_structure ss
                LEFT JOIN spatial_structure parent_ss
                    ON ss.parent_guid = parent_ss.guid
                LEFT JOIN elements_meta em
                    ON em.guid = ss.guid
                LEFT JOIN elements_rtree rt
                    ON rt.id = em.id
                WHERE ss.type IN ('IfcSpace', 'IfcSpatialZone')
                ORDER BY storey, ss.name
                """;

        try (PreparedStatement ps = outputConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double widthM = rs.getDouble("width_m");
                double depthM = rs.getDouble("depth_m");
                double heightM = rs.getDouble("height_m");
                double widthMm = widthM * 1000.0;
                double depthMm = depthM * 1000.0;
                double heightMm = heightM * 1000.0;
                double areaM2 = widthM * depthM;
                double perimeterM = 2.0 * (widthM + depthM);

                rooms.add(new RoomLine(
                        rs.getString("guid"),
                        rs.getString("name"),
                        rs.getString("storey") != null ? rs.getString("storey") : "\u2014",
                        Math.round(widthMm * 10.0) / 10.0,
                        Math.round(depthMm * 10.0) / 10.0,
                        Math.round(heightMm * 10.0) / 10.0,
                        Math.round(areaM2 * 100.0) / 100.0,
                        Math.round(perimeterM * 100.0) / 100.0,
                        rs.getString("object_type") != null ? rs.getString("object_type") : "\u2014",
                        "\u2014"  // compliance not yet linked
                ));
            }
        }

        BIMLogger.info(TAG, "{} rooms found for {}", rooms.size(), buildingId);
        return new RoomSchedule(buildingId, rooms.size(), rooms);
    }
}
