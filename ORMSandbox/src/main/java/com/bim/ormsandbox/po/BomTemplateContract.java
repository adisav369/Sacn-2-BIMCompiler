package com.bim.ormsandbox.po;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates BOM catalog completeness against the M_BomCategoryLine template.
 *
 * <p>Walks the template tree (RE→{SL,GF,RF}, GF→{LI,BD,DN,KT,BT}) and for each
 * leaf category, counts how many BOMs of that category exist within a given
 * doc_sub_type scope (variant-specific + generic NULL BOMs).
 *
 * <p>Info-rich: returns structured {@link TemplateReport} with per-category
 * {@link CategoryCheck} records. Callers can iterate, filter, format for
 * logs or tests. Not just pass/fail.
 *
 * @see MBomCategoryLine#getTemplateTree
 */
public class BomTemplateContract {

    /** One category check result. */
    public record CategoryCheck(
        String categoryId,
        String categoryName,
        int minQty, int maxQty,
        int found,
        String bestFitBomId,
        boolean required,
        boolean satisfied
    ) {}

    /** Full template report for a doc_sub_type scope. */
    public record TemplateReport(
        String docSubType,
        String templateRoot,
        List<CategoryCheck> checks,
        List<String> gaps
    ) {
        public boolean isComplete() {
            return checks.stream().allMatch(CategoryCheck::satisfied);
        }
    }

    /**
     * Check BOM catalog completeness against the template for a given scope.
     *
     * <p>Walks the Residential template tree (RE → SL, GF, RF → leaf rooms).
     * Template lookup is by doc_type, not C_BPartner.
     * At each leaf: queries MBOM catalog for matching bom_category + doc_sub_type.
     * Reports what exists, what's missing, what's optional.
     *
     * @param conn        JDBC connection to BOM.db
     * @param docSubType  variant scope (SH, DX, MY, TB). BOMs with this
     *                    doc_sub_type OR NULL are in scope.
     * @return structured report with per-category checks and gap list
     */
    public static TemplateReport check(Connection conn, String docSubType)
            throws SQLException {

        // Load template tree by doc_type — RE is the Residential root
        Map<String, List<MBomCategoryLine>> tree =
            MBomCategoryLine.getTemplateTreeByDocType(conn, "Residential");

        if (tree.isEmpty()) {
            return new TemplateReport(docSubType, "RE", List.of(),
                List.of("No template found for doc_type=Residential"));
        }

        List<CategoryCheck> checks = new ArrayList<>();
        List<String> gaps = new ArrayList<>();

        // Walk from root "RE"
        walkTree(conn, docSubType, "RE", tree, checks, gaps);

        return new TemplateReport(docSubType, "RE", List.copyOf(checks), List.copyOf(gaps));
    }

    private static void walkTree(Connection conn, String docSubType, String categoryId,
                                  Map<String, List<MBomCategoryLine>> tree,
                                  List<CategoryCheck> checks, List<String> gaps)
            throws SQLException {

        List<MBomCategoryLine> children = tree.get(categoryId);
        if (children == null) return;

        for (MBomCategoryLine line : children) {
            String childId = line.getChildCategoryId();

            if (tree.containsKey(childId)) {
                // Container node — recurse into children
                walkTree(conn, docSubType, childId, tree, checks, gaps);
            } else {
                // Leaf node — check catalog
                checkLeaf(conn, docSubType, line, checks, gaps);
            }
        }
    }

    private static void checkLeaf(Connection conn, String docSubType,
                                    MBomCategoryLine line,
                                    List<CategoryCheck> checks, List<String> gaps)
            throws SQLException {

        String categoryId = line.getChildCategoryId();
        int minQty = line.getMinQty();
        int maxQty = line.getMaxQty();

        // Look up category name
        MBomCategory cat = MBomCategory.get(conn, categoryId);
        String categoryName = cat != null ? cat.getName() : categoryId;

        // Query BOMs: category match + variant scope (docSubType OR NULL)
        List<MBOM> allOfCategory = MBOM.getByCategory(conn, categoryId);
        List<MBOM> inScope = allOfCategory.stream()
            .filter(b -> b.getDocSubType() == null || b.getDocSubType().equals(docSubType))
            .toList();

        int found = inScope.size();
        String bestFitBomId = inScope.isEmpty() ? null : inScope.get(0).getBomId();
        boolean required = minQty > 0;
        boolean satisfied = found >= minQty;

        checks.add(new CategoryCheck(
            categoryId, categoryName, minQty, maxQty,
            found, bestFitBomId, required, satisfied));

        if (!satisfied) {
            gaps.add(categoryName + " (" + categoryId + "): found=" + found
                + " but min=" + minQty
                + (required ? " REQUIRED" : " optional"));
        }
    }

    private BomTemplateContract() {} // utility class
}
