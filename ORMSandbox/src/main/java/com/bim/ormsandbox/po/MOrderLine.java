package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code c_orderline}.
 * Adds factory methods for building-level rule lookup.
 */
public class MOrderLine extends X_C_OrderLine {

    public MOrderLine(Connection conn) { super(conn); }

    /** Load by integer PK. Returns null if not found. */
    public static MOrderLine get(Connection conn, int id) throws SQLException {
        MOrderLine r = new MOrderLine(conn);
        return r.load(id) ? r : null;
    }

    /**
     * All active element rules for a building, ordered by id (= insertion order,
     * which drives roofIndex numbering — matches RelationalResolver.loadRules() ORDER BY id).
     */
    public static List<MOrderLine> getByBuilding(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, MOrderLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_id)
            .list();
    }

    // ── Nullable getters (for RelationalResolver — DB NULL must not become 0.0) ──

    public Double getHeightMmOrNull()       { return asDoubleOrNull(get_Value(COLUMNNAME_height_mm)); }
    public Double getWidthMmOrNull()        { return asDoubleOrNull(get_Value(COLUMNNAME_width_mm)); }
    public Double getHeightExtentMmOrNull() { return asDoubleOrNull(get_Value(COLUMNNAME_height_extent_mm)); }
    public Double getDepthMmOrNull()        { return asDoubleOrNull(get_Value(COLUMNNAME_depth_mm)); }
    public Double getPositionValueOrNull()  { return asDoubleOrNull(get_Value(COLUMNNAME_position_value)); }
    public Double getPositionValue2OrNull() { return asDoubleOrNull(get_Value(COLUMNNAME_position_value_2)); }

    private static Double asDoubleOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Floor-level rules (discipline=FURN, host_type=UNIT) for a building.
     * Used by RelationalResolver to derive floorStoreys, floorZOffsets, floorOrientations
     * in a single query instead of three separate ones.
     */
    public static List<MOrderLine> getFloorRules(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, MOrderLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_discipline + " = ?", "FURN")
            .andWhere(COLUMNNAME_host_type + " = ?", "UNIT")
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_id)
            .list();
    }

    /** Rules for a specific storey within a building. */
    public static List<MOrderLine> getByBuildingStorey(
            Connection conn, String buildingType, String storey) throws SQLException {
        return new ModelQuery<>(conn, MOrderLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_storey + " = ?", storey)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_id)
            .list();
    }
}
