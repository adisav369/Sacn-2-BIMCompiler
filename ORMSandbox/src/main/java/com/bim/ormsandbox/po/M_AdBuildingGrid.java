package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model layer for {@code ad_building_grid}.
 * Adds factory methods for building-wide grid lookup and axis-map utilities.
 */
public class M_AdBuildingGrid extends X_AdBuildingGrid {

    public M_AdBuildingGrid(Connection conn) { super(conn); }

    /**
     * All active grid lines for a building, ordered by axis then grid_label.
     * Used by RelationalResolver, PlacementProver, and MetadataValidator.
     */
    public static List<M_AdBuildingGrid> getByBuilding(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdBuildingGrid::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_axis + ", " + COLUMNNAME_grid_label)
            .list();
    }

    /**
     * Build a label → position_mm map for one axis from a pre-loaded grid list.
     * Avoids a second DB round-trip when both axes are needed.
     *
     * @param rows  result of {@link #getByBuilding}
     * @param axis  "X" or "Y"
     */
    public static Map<String, Double> buildAxisMap(List<M_AdBuildingGrid> rows, String axis) {
        Map<String, Double> map = new HashMap<>();
        for (M_AdBuildingGrid row : rows) {
            if (axis.equals(row.getAxis())) {
                map.put(row.getGridLabel(), row.getPositionMm());
            }
        }
        return map;
    }

    /**
     * Look up position_mm for a specific axis + label from a pre-loaded grid list.
     * Returns null if not found.
     */
    public static Double getPosition(List<M_AdBuildingGrid> rows, String axis, String label) {
        for (M_AdBuildingGrid row : rows) {
            if (axis.equals(row.getAxis()) && label.equals(row.getGridLabel())) {
                return row.getPositionMm();
            }
        }
        return null;
    }
}
