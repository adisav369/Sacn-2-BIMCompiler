package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_product_dim}.
 * Catalog master — intrinsic dimensions in METERS.
 */
public class M_AdProductDim extends X_AdProductDim {

    public M_AdProductDim(Connection conn) { super(conn); }

    /** Load by product_id TEXT PK. Returns null if not found or inactive. */
    public static M_AdProductDim get(Connection conn, String productId) throws SQLException {
        M_AdProductDim p = new M_AdProductDim(conn);
        return p.load(productId) && p.isActive() ? p : null;
    }

    /** All active products of a given type (DOOR, WINDOW, FIXTURE, FURNITURE, etc.). */
    public static List<M_AdProductDim> getByType(Connection conn, String productType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdProductDim::new, Table_Name)
            .where(COLUMNNAME_product_type + " = ?", productType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_product_id)
            .list();
    }

    /** All active products, ordered by product_id. */
    public static List<M_AdProductDim> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, M_AdProductDim::new, Table_Name)
            .where(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_product_id)
            .list();
    }

    /**
     * Volume in m³ — convenience for placement calculations.
     * TRAP: dimensions are METERS, not mm.
     */
    public double volumeM3() { return getWidth() * getDepth() * getHeight(); }
}
