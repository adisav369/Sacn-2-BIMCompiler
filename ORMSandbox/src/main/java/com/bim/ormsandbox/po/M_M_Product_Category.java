package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code M_Product_Category}.
 *
 * // Implementing SpecsAnalysis.txt §3 — drop Parent_Category_ID
 * <p>Provides query methods for the FLAT IFC classification table.
 * Categories are flat tags (STR, MEP, ARC, ASM, IFC_WALL, etc.).
 * Hierarchy lives in M_BOM_Line, not in this table.
 */
public class M_M_Product_Category extends X_M_Product_Category {

    public M_M_Product_Category(Connection conn) { super(conn); }

    /** Discipline categories (no IFC class — grouping tags like STR, MEP, ARC, ASM). */
    public static List<M_M_Product_Category> getDisciplines(Connection conn)
            throws SQLException {
        return new ModelQuery<>(conn, M_M_Product_Category::new, Table_Name)
            .where(COLUMNNAME_IFC_Class + " IS NULL")
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
            .orderBy(COLUMNNAME_SeqNo)
            .list();
    }
}
