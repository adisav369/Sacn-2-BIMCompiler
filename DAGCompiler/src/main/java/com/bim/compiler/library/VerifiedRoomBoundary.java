package com.bim.compiler.library;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * View-mapped record for v_verified_room_boundary.
 * All coordinate values are in mm (IFC global frame).
 * width_mm = max_x_mm − min_x_mm, depth_mm = max_y_mm − min_y_mm (computed by view).
 */
public record VerifiedRoomBoundary(
    int    roomId,
    String buildingType,
    String roomType,
    double minXMm,
    double maxXMm,
    double minYMm,
    double maxYMm,
    String storeyId,
    String extractedFrom,
    String coordinateFrame,
    double widthMm,
    double depthMm
) {
    public static VerifiedRoomBoundary from(ResultSet rs) throws SQLException {
        return new VerifiedRoomBoundary(
            rs.getInt("room_id"),
            rs.getString("building_type"),
            rs.getString("room_type"),
            rs.getDouble("min_x_mm"),
            rs.getDouble("max_x_mm"),
            rs.getDouble("min_y_mm"),
            rs.getDouble("max_y_mm"),
            rs.getString("storey_id"),
            rs.getString("extracted_from"),
            rs.getString("coordinate_frame"),
            rs.getDouble("width_mm"),
            rs.getDouble("depth_mm")
        );
    }
}
