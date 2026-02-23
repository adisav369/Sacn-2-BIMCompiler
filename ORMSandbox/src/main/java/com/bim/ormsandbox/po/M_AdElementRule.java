package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_element_rule}.
 * Adds factory methods for building-level rule lookup.
 */
public class M_AdElementRule extends X_AdElementRule {

    public M_AdElementRule(Connection conn) { super(conn); }

    /** Load by integer PK. Returns null if not found. */
    public static M_AdElementRule get(Connection conn, int id) throws SQLException {
        M_AdElementRule r = new M_AdElementRule(conn);
        return r.load(id) ? r : null;
    }

    /**
     * All active element rules for a building, ordered by id (= insertion order,
     * which drives roofIndex numbering — matches RelationalResolver.loadRules() ORDER BY id).
     */
    public static List<M_AdElementRule> getByBuilding(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdElementRule::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_id)
            .list();
    }

    /** Rules for a specific storey within a building. */
    public static List<M_AdElementRule> getByBuildingStorey(
            Connection conn, String buildingType, String storey) throws SQLException {
        return new ModelQuery<>(conn, M_AdElementRule::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_storey + " = ?", storey)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_id)
            .list();
    }
}
