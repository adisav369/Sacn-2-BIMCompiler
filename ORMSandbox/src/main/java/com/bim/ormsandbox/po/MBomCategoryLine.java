package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model layer for {@code M_BomCategoryLine}.
 *
 * <p>Defines the decomposition recipe for template-driven compilation.
 * Each line maps a parent M_BomCategory to a child M_BomCategory,
 * forming a recursive tree:
 * <pre>
 *   RE → {SL(seq=10), GF(seq=20), RF(seq=30)}
 *   GF → {LI(seq=10), BD(seq=20), DN(seq=30), KT(seq=40), BT(seq=50)}
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §3.8</a>
 */
public class MBomCategoryLine extends X_MBomCategoryLine {

    public MBomCategoryLine(Connection conn) { super(conn); }

    /**
     * Load all active children of a parent category, ordered by Sequence.
     * E.g. getByCategory(conn, "RE") → [SL, GF, RF]
     */
    public static List<MBomCategoryLine> getByCategory(Connection conn, String categoryId) throws SQLException {
        return new ModelQuery<>(conn, MBomCategoryLine::new, Table_Name)
            .where(COLUMNNAME_M_BomCategory_ID + " = ?", categoryId)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_Sequence)
            .list();
    }

    /**
     * Load the full template tree for a C_BPartner, starting from the root category.
     * Returns a map of parent_category_id → ordered list of children.
     *
     * <p>Walks the M_BomCategory with matching C_BPartner_ID to find the root,
     * then recursively loads all M_BomCategoryLine descendants.
     *
     * @return map keyed by parent category ID, values are ordered child lines.
     *         Empty map if no template exists for this partner.
     */
    public static Map<String, List<MBomCategoryLine>> getTemplateTree(Connection conn, String cbpartnerId)
            throws SQLException {
        // Find root category for this C_BPartner
        MBomCategory root = MBomCategory.getTemplateByCBPartner(conn, cbpartnerId);
        if (root == null) return Map.of();

        Map<String, List<MBomCategoryLine>> tree = new LinkedHashMap<>();
        loadRecursive(conn, root.getCategoryId(), tree);
        return tree;
    }

    private static void loadRecursive(Connection conn, String categoryId,
                                       Map<String, List<MBomCategoryLine>> tree) throws SQLException {
        if (tree.containsKey(categoryId)) return; // guard against cycles

        List<MBomCategoryLine> children = getByCategory(conn, categoryId);
        if (children.isEmpty()) return;

        tree.put(categoryId, children);
        for (MBomCategoryLine child : children) {
            loadRecursive(conn, child.getChildCategoryId(), tree);
        }
    }
}
