package com.bim.ormsandbox.po;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BOM Category Chooser — walks the M_BomCategoryLine template tree with an AABB
 * + numUnits, selects best-fit BOMs from the <b>entire catalog</b>
 * (no doc_sub_type filter). Also known as BOMCategoryChooser in design docs.
 *
 * <p>Skipped when MCDocType.getDocSubType() directly resolves to an MBOM with
 * getBomCategory()='UN' (direct-match buildings: SH, DX, TB). Active only for
 * ST mode where no pre-matched UNIT BOM exists — the chooser must select from
 * the M_BomCategory catalog.
 *
 * <p>The AABB constraint and template branching drive selection:
 * <ul>
 *   <li>num_units=2 activates the PR (Pair) branch, skipping GF (single-household)
 *   <li>PR → 2× HU splits width by 2 (two half-units side by side)
 *   <li>HU → L1/L2 splits height by Z ratios (0.5/0.5)
 *   <li>At each leaf, {@code findBestFitAnyOwner} picks from ALL owners
 *   <li>Since only DX owns PR/HU BOMs, those self-select without owner filter
 * </ul>
 *
 * <p>This proves the catalog cart mechanism: RE + AABB + num_units=2 → DX
 * structure emerges from catalog constraints alone.
 *
 * @see BomTemplateContract
 * @see MBOM#findBestFitAnyOwner
 */
public class BomTemplateComposer {

    /** One node in the composition tree. */
    public record NodeSelection(
        String categoryId,
        String categoryName,
        int level,
        int allocW, int allocD, int allocH,
        String selectedBomId,
        String selectedOwner,
        boolean isLeaf,
        String mirroringRule
    ) {}

    /** Full composition report. */
    public record CompositionReport(
        int inputW, int inputD, int inputH,
        int numUnits,
        List<NodeSelection> selections,
        List<String> gaps
    ) {
        public boolean isComplete() {
            return selections.stream()
                .filter(NodeSelection::isLeaf)
                .allMatch(s -> s.selectedBomId() != null);
        }
    }

    /**
     * Compose a building from template + AABB + numUnits.
     *
     * <p>Template lookup is driven by {@code docType} (from c_order.building_type),
     * not by C_BPartner. The template defines structural grammar (slots);
     * C_BPartner influences which M_BOM fills each slot.
     *
     * <p>c_order.building_type maps to M_BomCategory.doc_type:
     * RESIDENTIAL→Residential, COMMERCIAL→Commercial, INDUSTRIAL→Industrial.
     *
     * @param conn      JDBC connection to BOM.db
     * @param docType   M_BomCategory doc_type (Residential, Commercial, Industrial)
     * @param widthMm   building envelope width in mm
     * @param depthMm   building envelope depth in mm
     * @param heightMm  building envelope height in mm
     * @param numUnits  number of household units (1=SH, 2=DX)
     * @return structured report with per-node selections and gaps
     */
    public static CompositionReport compose(
            Connection conn, String docType,
            int widthMm, int depthMm, int heightMm,
            int numUnits) throws SQLException {

        Map<String, List<MBomCategoryLine>> tree =
            MBomCategoryLine.getTemplateTreeByDocType(conn, docType);

        if (tree.isEmpty()) {
            return new CompositionReport(widthMm, depthMm, heightMm, numUnits,
                List.of(), List.of("No template found for doc_type=" + docType));
        }

        List<NodeSelection> selections = new ArrayList<>();
        List<String> gaps = new ArrayList<>();

        // Root node: RE gets the full AABB
        MBomCategory rootCat = MBomCategory.get(conn, "RE");
        String rootName = rootCat != null ? rootCat.getName() : "RE";
        selections.add(new NodeSelection(
            "RE", rootName, 0,
            widthMm, depthMm, heightMm,
            null, null, false, "NONE"));

        walkTree(conn, "RE", tree, numUnits,
                 widthMm, depthMm, heightMm, 1,
                 selections, gaps);

        return new CompositionReport(widthMm, depthMm, heightMm, numUnits,
            List.copyOf(selections), List.copyOf(gaps));
    }

    private static void walkTree(
            Connection conn, String parentCategoryId,
            Map<String, List<MBomCategoryLine>> tree,
            int numUnits,
            int allocW, int allocD, int allocH,
            int level,
            List<NodeSelection> selections, List<String> gaps)
            throws SQLException {

        List<MBomCategoryLine> children = tree.get(parentCategoryId);
        if (children == null) return;

        for (MBomCategoryLine line : children) {
            int lineNumUnits = line.getNumUnits();

            // Filter by num_units: 0=universal (always include),
            // otherwise must match the requested numUnits
            if (lineNumUnits != 0 && lineNumUnits != numUnits) {
                continue;
            }

            String childId = line.getChildCategoryId();
            String mirrorRule = line.getMirroringRule();
            if (mirrorRule == null) mirrorRule = "NONE";

            // Compute allocated AABB for this child
            double zExtent = line.getZExtentRatio();
            int childH = zExtent > 0 ? (int)(allocH * zExtent) : allocH;
            int childW = allocW;
            int childD = allocD;

            // PR → HU: split width by 2 (two half-units side by side)
            if ("PR".equals(parentCategoryId) && "HU".equals(childId)) {
                childW = allocW / 2;
            }

            MBomCategory cat = MBomCategory.get(conn, childId);
            String catName = cat != null ? cat.getName() : childId;

            boolean isContainer = tree.containsKey(childId);

            if (isContainer) {
                // Container node: try to find a BOM, then recurse
                MBOM bom = MBOM.findBestFitAnyOwner(conn, childId,
                    childW, childD, childH);

                selections.add(new NodeSelection(
                    childId, catName, level,
                    childW, childD, childH,
                    bom != null ? bom.getBomId() : null,
                    bom != null ? bom.getDocSubType() : null,
                    false, mirrorRule));

                walkTree(conn, childId, tree, numUnits,
                         childW, childD, childH, level + 1,
                         selections, gaps);
            } else {
                // Leaf node: find best-fit BOM from any owner
                MBOM bom = MBOM.findBestFitAnyOwner(conn, childId,
                    childW, childD, childH);

                selections.add(new NodeSelection(
                    childId, catName, level,
                    childW, childD, childH,
                    bom != null ? bom.getBomId() : null,
                    bom != null ? bom.getDocSubType() : null,
                    true, mirrorRule));

                if (bom == null && line.getMinQty() > 0) {
                    gaps.add(catName + " (" + childId + "): no BOM fits "
                        + childW + "x" + childD + "x" + childH);
                }
            }
        }
    }

    private BomTemplateComposer() {} // utility class
}
