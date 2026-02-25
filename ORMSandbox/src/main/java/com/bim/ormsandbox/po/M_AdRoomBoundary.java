package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_room_boundary}.
 * Adds factory methods for G8 calibration and room lookup.
 */
public class M_AdRoomBoundary extends X_AdRoomBoundary {

    public M_AdRoomBoundary(Connection conn) { super(conn); }

    /**
     * All active boundaries for a building_type, ordered by room_name.
     * Used by BuildingInspector.dumpRoomBoundaries() and G8 calibration.
     */
    public static List<M_AdRoomBoundary> getByBuilding(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdRoomBoundary::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_room_name)
            .list();
    }

    // ── Nullable coord getters (NULL means "use grid fallback") ──

    public Double getMinXMmOrNull() { return asDoubleOrNull(get_Value(COLUMNNAME_min_x_mm)); }
    public Double getMaxXMmOrNull() { return asDoubleOrNull(get_Value(COLUMNNAME_max_x_mm)); }
    public Double getMinYMmOrNull() { return asDoubleOrNull(get_Value(COLUMNNAME_min_y_mm)); }
    public Double getMaxYMmOrNull() { return asDoubleOrNull(get_Value(COLUMNNAME_max_y_mm)); }

    private static Double asDoubleOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** Find a room boundary by building + room name. Returns null if not found. */
    public static M_AdRoomBoundary get(Connection conn, String buildingType, String roomName)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdRoomBoundary::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_room_name + " = ?", roomName)
            .first()
            .orElse(null);
    }
}
