package com.bim.designer.api;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Room-level context extracted from C_OrderLine for suggestion engines.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
 */
public record RoomContext(int orderLineId, String bomCategory, double areaSqM) {

    /**
     * Find all ROOM-level C_OrderLines in the given order.
     * Room area is computed from AABB width × depth (mm² → m²).
     */
    static List<RoomContext> findRooms(Connection woConn, String orderId) throws SQLException {
        String sql = "SELECT C_OrderLine_ID, bom_category, "
                + "COALESCE(aabb_width_mm, 0) * COALESCE(aabb_depth_mm, 0) / 1e6 AS area_sqm "
                + "FROM C_OrderLine "
                + "WHERE C_Order_ID = ? AND host_type = 'ROOM' "
                + "ORDER BY Line";
        List<RoomContext> rooms = new ArrayList<>();
        try (PreparedStatement ps = woConn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rooms.add(new RoomContext(
                            rs.getInt("C_OrderLine_ID"),
                            rs.getString("bom_category"),
                            rs.getDouble("area_sqm")));
                }
            }
        }
        return rooms;
    }
}
