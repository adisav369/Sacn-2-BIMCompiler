package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code M_Product_Category}.
 *
 * <p>Provides query methods for the IFC classification tree.
 * Tree structure: discipline parents (STR/MEP/ARC/ASM) → IFC class leaves.
 */
public class M_M_Product_Category extends X_M_Product_Category {

    public M_M_Product_Category(Connection conn) { super(conn); }

    /** Root categories (disciplines) — Parent_Category_ID IS NULL. */
    public static List<M_M_Product_Category> getRoots(Connection conn)
            throws SQLException {
        return new ModelQuery<>(conn, M_M_Product_Category::new, Table_Name)
            .where(COLUMNNAME_Parent_Category_ID + " IS NULL")
            .orderBy(COLUMNNAME_SeqNo)
            .list();
    }

    /** Children of a parent category, ordered by SeqNo. */
    public static List<M_M_Product_Category> getChildren(Connection conn, String parentId)
            throws SQLException {
        return new ModelQuery<>(conn, M_M_Product_Category::new, Table_Name)
            .where(COLUMNNAME_Parent_Category_ID + " = ?", parentId)
            .orderBy(COLUMNNAME_SeqNo)
            .list();
    }

    /** Look up category by IFC class name (e.g. "IfcWall" → IFC_WALL). */
    public static M_M_Product_Category getByIfcClass(Connection conn, String ifcClass)
            throws SQLException {
        return new ModelQuery<>(conn, M_M_Product_Category::new, Table_Name)
            .where(COLUMNNAME_IFC_Class + " = ?", ifcClass)
            .first()
            .orElse(null);
    }

    /** All leaf categories (those with IFC_Class set). */
    public static List<M_M_Product_Category> getLeaves(Connection conn)
            throws SQLException {
        return new ModelQuery<>(conn, M_M_Product_Category::new, Table_Name)
            .where(COLUMNNAME_IFC_Class + " IS NOT NULL")
            .orderBy(COLUMNNAME_Parent_Category_ID + ", " + COLUMNNAME_SeqNo)
            .list();
    }
}
