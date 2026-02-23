package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_bom_child_param}.
 */
public class M_AdBomChildParam extends X_AdBomChildParam {

    public M_AdBomChildParam(Connection conn) { super(conn); }

    /** All active params for a given BOM child. */
    public static List<M_AdBomChildParam> getByBomChild(Connection conn, int bomChildId)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdBomChildParam::new, Table_Name)
            .where(COLUMNNAME_bom_child_id + " = ?", bomChildId)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_param_key)
            .list();
    }
}
