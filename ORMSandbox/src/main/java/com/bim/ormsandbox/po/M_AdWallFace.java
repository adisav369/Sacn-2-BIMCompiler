package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_wall_face}.
 * Adds factory methods for building-wide and room-specific wall face lookup.
 */
public class M_AdWallFace extends X_AdWallFace {

    public M_AdWallFace(Connection conn) { super(conn); }

    /**
     * All active wall faces for a building, ordered by room_name then face.
     * Used by RelationalResolver and MetadataValidator.
     */
    public static List<M_AdWallFace> getByBuilding(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdWallFace::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_room_name + ", " + COLUMNNAME_face)
            .list();
    }

    /**
     * All active wall faces for a specific room within a building.
     * At most 4 rows (one per cardinal direction).
     */
    public static List<M_AdWallFace> getByRoom(Connection conn, String buildingType, String roomName)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdWallFace::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_room_name + " = ?", roomName)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_face)
            .list();
    }
}
