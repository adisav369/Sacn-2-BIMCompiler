package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Model layer for the LOD product geometry library.
 *
 * <p>Provides typed query methods over {@code lod_product_geometry} view
 * in component_library.db. Read-only — library data is populated by
 * migration scripts, not by the compiler pipeline.
 *
 * <p>Usage:
 * <pre>
 *   Connection libConn = DriverManager.getConnection("jdbc:sqlite:library/component_library.db");
 *   M_LOD_Library smoke = M_LOD_Library.getByProduct(libConn, "FP_SMOKE");
 *   String hash = smoke.getGeometryHash();   // canonical mesh reference
 *   String face = smoke.getAttachmentFace();  // "TOP" — ceiling-mount
 * </pre>
 */
public class M_LOD_Library extends X_LOD_Library {

    public M_LOD_Library(Connection conn) { super(conn); }

    /**
     * Look up the canonical geometry entry for a product.
     *
     * @param conn   connection to component_library.db
     * @param productId  M_Product_ID (e.g. "FP_SMOKE", "DOOR_FLUSH_864")
     * @return the LOD library entry, or null if product has no geometry
     */
    public static M_LOD_Library getByProduct(Connection conn, String productId)
            throws SQLException {
        return new ModelQuery<>(conn, M_LOD_Library::new, Table_Name)
            .where(COLUMNNAME_M_Product_ID + " = ?", productId)
            .first()
            .orElse(null);
    }

    /**
     * All LOD library entries, ordered by product ID.
     */
    public static List<M_LOD_Library> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, M_LOD_Library::new, Table_Name)
            .orderBy(COLUMNNAME_M_Product_ID)
            .list();
    }

    /**
     * All LOD library entries with a given attachment face.
     * Useful for filtering floor-standing (BOTTOM) vs ceiling-mount (TOP)
     * vs inline (CENTER) products.
     */
    public static List<M_LOD_Library> getByAttachmentFace(Connection conn, String face)
            throws SQLException {
        return new ModelQuery<>(conn, M_LOD_Library::new, Table_Name)
            .where(COLUMNNAME_attachment_face + " = ?", face)
            .orderBy(COLUMNNAME_M_Product_ID)
            .list();
    }

    /**
     * Check whether a product has geometry in the library.
     */
    public static boolean hasGeometry(Connection conn, String productId)
            throws SQLException {
        return new ModelQuery<>(conn, M_LOD_Library::new, Table_Name)
            .where(COLUMNNAME_M_Product_ID + " = ?", productId)
            .first()
            .isPresent();
    }
}
