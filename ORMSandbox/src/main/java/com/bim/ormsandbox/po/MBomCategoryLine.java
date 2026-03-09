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
     * Load the full template tree by doc_type, starting from the root category.
     * E.g. "RE" → finds RE, walks RE → SL, GF, RF → LI, BD, DN, KT, BT.
     *
     * <p>Templates are generic (C_BPartner_ID IS NULL). The doc_type determines
     * which structural grammar applies. C_BPartner influences BOM selection
     * within each slot, not the template structure itself.
     *
     * @param conn    BOM.db connection
     * @param docType document type: RE, CO, IN (short code matching C_DocType.DocBaseType)
     * @return map keyed by parent category ID, values are ordered child lines.
     *         Empty map if no template exists for this doc_type.
     */
    public static Map<String, List<MBomCategoryLine>> getTemplateTreeByDocType(
            Connection conn, String docType) throws SQLException {
        MBomCategory root = MBomCategory.getTemplateByDocType(conn, docType);
        if (root == null) return Map.of();

        Map<String, List<MBomCategoryLine>> tree = new LinkedHashMap<>();
        loadRecursive(conn, root.getCategoryId(), tree);
        return tree;
    }

    /**
     * @deprecated Use {@link #getTemplateTreeByDocType} instead. Templates are
     * generic (no C_BPartner). ST is a test/demo partner, not a template owner.
     */
    @Deprecated
    public static Map<String, List<MBomCategoryLine>> getTemplateTree(Connection conn, String cbpartnerId)
            throws SQLException {
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
