package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code AD_User} (iDempiere: AD_User).
 *
 * <p>1 entry: System (ID=100).
 *
 * @see <a href="docs/ACTION_ROADMAP.md">ACTION_ROADMAP — Phase H0</a>
 */
public class MADUser extends X_ADUser {

    public MADUser(Connection conn) { super(conn); }

    /** Load by AD_User_ID. Returns null if not found or inactive. */
    public static MADUser get(Connection conn, int adUserId) throws SQLException {
        MADUser u = new MADUser(conn);
        return u.load(adUserId) && u.isActive() ? u : null;
    }

    /** All active users, ordered by ID. */
    public static List<MADUser> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MADUser::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_AD_User_ID)
            .list();
    }
}
