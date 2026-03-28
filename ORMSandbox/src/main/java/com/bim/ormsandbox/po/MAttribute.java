package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code m_attribute} (iDempiere: M_Attribute).
 *
 * <p>Product-level attributes on leaf items — connection ports, UBBL clearances,
 * MEP properties. Read by AD_Val_Rule at compile time.
 */
public class MAttribute extends X_M_Attribute {

    public MAttribute(Connection conn) { super(conn); }

    /** All active attributes for a given BOM line (leaf item). */
    public static List<MAttribute> getByBomChild(Connection conn, int bomChildId)
            throws SQLException {
        return new ModelQuery<>(conn, MAttribute::new, Table_Name)
            .where(COLUMNNAME_M_BOM_Line_ID + " = ?", bomChildId)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_param_key)
            .list();
    }
}
