package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code C_Campaign} (iDempiere: C_Campaign).
 *
 * <p>4 entries: SCAN (Scandinavian), TROP (Tropical),
 * INST (Institutional), INDU (Industrial).
 *
 * @see <a href="docs/ACTION_ROADMAP.md">ACTION_ROADMAP — Phase H0</a>
 */
public class MCCampaign extends X_CCampaign {

    public MCCampaign(Connection conn) { super(conn); }

    /** Load by C_Campaign_ID (SCAN, TROP, INST, INDU). Returns null if not found or inactive. */
    public static MCCampaign get(Connection conn, String ccampaignId) throws SQLException {
        MCCampaign c = new MCCampaign(conn);
        return c.load(ccampaignId) && c.isActive() ? c : null;
    }

    /** All active campaigns, ordered by ID. */
    public static List<MCCampaign> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MCCampaign::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_C_Campaign_ID)
            .list();
    }

    /** Find by Value (search key, e.g. 'Scandinavian'). Returns null if not found. */
    public static MCCampaign getByValue(Connection conn, String value) throws SQLException {
        return new ModelQuery<>(conn, MCCampaign::new, Table_Name)
            .where(COLUMNNAME_Value + " = ?", value)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }
}
