package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code C_BPartner} (iDempiere: C_BPartner).
 *
 * <p>5 entries: SH (Sample House), DX (Duplex), TB (Terrace Block),
 * TE (Terminal), ST (Standard Mode).
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §3.8</a>
 */
public class MCBPartner extends X_CBPartner {

    public MCBPartner(Connection conn) { super(conn); }

    /** Load by C_BPartner_ID (SH, DX, TB, TE, ST). Returns null if not found or inactive. */
    public static MCBPartner get(Connection conn, String cbpartnerId) throws SQLException {
        MCBPartner bp = new MCBPartner(conn);
        return bp.load(cbpartnerId) && bp.isActive() ? bp : null;
    }

    /** All active partners, ordered by ID. */
    public static List<MCBPartner> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MCBPartner::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_C_BPartner_ID)
            .list();
    }

    /** Find by Value (search key, e.g. 'SampleHouse'). Returns null if not found. */
    public static MCBPartner getByValue(Connection conn, String value) throws SQLException {
        return new ModelQuery<>(conn, MCBPartner::new, Table_Name)
            .where(COLUMNNAME_Value + " = ?", value)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }
}
