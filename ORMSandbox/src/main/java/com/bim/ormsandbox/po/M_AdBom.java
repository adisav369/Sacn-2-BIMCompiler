package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_bom}.
 * Adds factory methods for BOM chain navigation.
 */
public class M_AdBom extends X_AdBom {

    public M_AdBom(Connection conn) { super(conn); }

    /** Load by bom_id TEXT PK. Returns null if not found or inactive. */
    public static M_AdBom get(Connection conn, String bomId) throws SQLException {
        M_AdBom bom = new M_AdBom(conn);
        return bom.load(bomId) && bom.isActive() ? bom : null;
    }

    /** All active BOMs of a given type (UNIT, FLOOR, ROOM, SET, ITEM). */
    public static List<M_AdBom> getByType(Connection conn, String bomType) throws SQLException {
        return new ModelQuery<>(conn, M_AdBom::new, Table_Name)
            .where(COLUMNNAME_bom_type + " = ?", bomType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_bom_id)
            .list();
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (getGroupBy() == null)
            throw new IllegalStateException("group_by must not be null (ad_bom NOT NULL constraint)");
    }
}
