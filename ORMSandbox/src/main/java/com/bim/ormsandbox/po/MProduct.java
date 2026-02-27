package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code M_Product}.
 * iDempiere M_Product analogue — intrinsic dimensions in METERS for BUY products.
 * Assembly stubs (MAKE, is_active=0) are accessible via {@link #getAssembly}.
 */
public class MProduct extends X_MProduct {

    public MProduct(Connection conn) { super(conn); }

    /** Load active placeable product by product_id. Returns null if not found or inactive. */
    public static MProduct get(Connection conn, String productId) throws SQLException {
        MProduct p = new MProduct(conn);
        return p.load(productId) && p.isActive() ? p : null;
    }

    /** Load any product by product_id regardless of is_active (for assembly stubs). */
    public static MProduct getAssembly(Connection conn, String productId) throws SQLException {
        MProduct p = new MProduct(conn);
        return p.load(productId) ? p : null;
    }

    /** All active products of a given type (DOOR, WINDOW, FIXTURE, FURNITURE, etc.). */
    public static List<MProduct> getByType(Connection conn, String productType)
            throws SQLException {
        return new ModelQuery<>(conn, MProduct::new, Table_Name)
            .where(COLUMNNAME_product_type + " = ?", productType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_product_id)
            .list();
    }

    /** All active products, ordered by product_id. */
    public static List<MProduct> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MProduct::new, Table_Name)
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
