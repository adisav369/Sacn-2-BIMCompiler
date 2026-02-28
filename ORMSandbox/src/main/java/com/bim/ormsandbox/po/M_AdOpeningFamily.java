package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_opening_family}.
 * Factory methods for all-active and type-filtered family lookup.
 */
public class M_AdOpeningFamily extends X_AdOpeningFamily {

    /** depth_mm fallback: matches Phase 119B default in OpeningBomAD. */
    private static final int DEFAULT_DEPTH_MM = 100;

    public M_AdOpeningFamily(Connection conn) { super(conn); }

    /**
     * depth_mm with fallback: returns 100 when the column is NULL.
     * Matches the Phase 119B graceful-fallback behaviour in OpeningBomAD.
     */
    public int getDepthMmWithDefault() {
        Object raw = get_Value(COLUMNNAME_depth_mm);
        if (raw == null) return DEFAULT_DEPTH_MM;
        int v = get_ValueAsInt(COLUMNNAME_depth_mm);
        return v == 0 ? DEFAULT_DEPTH_MM : v;
    }

    /**
     * All active families, ordered by family_id.
     * Used by OpeningBomAD.ensureFamiliesLoaded().
     */
    public static List<M_AdOpeningFamily> listActive(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, M_AdOpeningFamily::new, Table_Name)
            .where(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_family_id)
            .list();
    }

    /**
     * Active families filtered by opening_type ("DOOR" or "WINDOW").
     * Used by CatalogValidator.loadOpeningFamilies().
     */
    public static List<M_AdOpeningFamily> getByType(Connection conn, String openingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdOpeningFamily::new, Table_Name)
            .where(COLUMNNAME_opening_type + " = ?", openingType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_family_id)
            .list();
    }
}
