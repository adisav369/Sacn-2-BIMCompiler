package com.bim.ormsandbox.po;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BOM Template Composer — the MRP explosion engine for template-driven buildings.
 *
 * <h3>When does this run?</h3>
 * <p>Activated by the Prime Rule when DocSubType='ST':
 * no BUILDING BOM matches the three-key (AABB + DocBaseType + DocSubType),
 * so the template path takes over. The composer selects best-fit BOMs from
 * the <b>entire catalog</b> (no doc_sub_type filter) using AABB matching
 * at every level of the M_BomCategoryLine template tree.
 *
 * <h3>Floor-container model</h3>
 * <p>A floor (GF, L1, L2) is a <b>container of sub-rooms</b>:
 * Kitchen, Living, Bedroom, Bathroom, Dining, Hallway, etc.
 * Each sub-room is a catalog item that users can browse and select —
 * "Kitchen Style A (L-shaped)" vs "Kitchen Style B (galley)".
 * AABB matching at each level drives the selection automatically.
 *
 * <h3>Packed-box principle</h3>
 * <p>Each BOM level is fully tiled: children AABBs + PHANTOM fillers = parent AABB.
 * Think of a furniture box: items are the BUY components, foam padding is the
 * PHANTOM filler. In BOM.db the box is packed (fillers present). In output.db
 * the box is unpacked — PHANTOMs stripped, content stays at tack positions.
 *
 * <h3>AABB cascade and selection</h3>
 * <ul>
 *   <li>Building envelope AABB cascades down: floor → sub-room → content</li>
 *   <li>num_units=2 activates the PR (Pair) branch, skipping GF (single-household)</li>
 *   <li>PR → 2× HU splits width by 2 (two half-units side by side)</li>
 *   <li>HU → L1/L2 splits height by Z ratios (0.5/0.5)</li>
 *   <li>At each leaf, {@link MBOM#findBestFitAnyOwner} picks from ALL owners</li>
 *   <li>Since only DX owns PR/HU BOMs, those self-select without owner filter</li>
 *   <li>Duplex mirroring (PARTY_WALL_PI) is handled by PR→HU — no need for
 *       duplicate categories (e.g. KA/KB). One KT BOM serves both half-units.</li>
 * </ul>
 *
 * <p>This proves the catalog cart mechanism: RE + AABB + num_units=2 → DX
 * structure emerges from catalog constraints alone.
 *
 * <h3>Template entry point (M_BomCategory)</h3>
 * <p>M_BomCategory entries with {@code doc_type='RE'} and {@code doc_sub_type='ST'}
 * are the Standard Template AABB entries (ST-SH, ST-DX). When the WALKTHRU path
 * enters this composer, it queries these entries and AABB-matches to determine
 * which building variant (SH or DX) to compose. The AABB alone distinguishes
 * the variant — both entries share {@code doc_sub_type='ST'}.
 *
 * @see BomTemplateContract
 * @see MBOM#findBestFitAnyOwner
 * @see <a href="scripts/run_RosettaStones.sh">Prime Rule data set</a>
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
     * <p>Template lookup is driven by {@code docType} — the M_BomCategory.doc_type
     * short code (RE, CO, IN). This finds the structural grammar root (e.g. RE →
     * Residential Template → {SL, GF, RF} → {LI, BD, KT, BT, DN}).
     * C_BPartner influences which M_BOM fills each slot, not which template is used.
     *
     * <p>The AABB cascades top-down through the template tree. At each leaf node,
     * {@link MBOM#findBestFitAnyOwner} selects the best BOM whose children fit
     * within the allocated AABB. This is the same AABB matching that drives the
     * Prime Rule at the BUILDING level — applied recursively at every level.
     *
     * @param conn      JDBC connection to BOM.db
     * @param docType   M_BomCategory.doc_type short code (RE, CO, IN)
     * @param widthMm   building envelope width in mm (from M_BomCategory AABB for ST)
     * @param depthMm   building envelope depth in mm
     * @param heightMm  building envelope height in mm
     * @param numUnits  number of household units (1=single/SH, 2=duplex/DX)
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

        // Root node: RE (Residential Template) receives the full building envelope AABB.
        // The tree walk cascades this AABB downward — each child gets a share:
        //   RE (full envelope) → SL (slab), GF (ground floor), RF (roof)
        //   GF → LI (living), BD (bedroom), KT (kitchen), BT (bathroom), DN (dining)
        // At each leaf, findBestFitAnyOwner picks the best BOM that fits.
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

            // ── AABB allocation ──────────────────────────────────────
            // Parent AABB is divided among children. Each child gets a share:
            //   Z: z_extent_ratio splits height (e.g. L1=0.5, L2=0.5 of floor height)
            //   W: PR→HU splits width by 2 (duplex: two half-units side by side)
            //   D: passes through unchanged (building depth is shared)
            //
            // This allocated AABB becomes the constraint for findBestFitAnyOwner:
            // "find me a KT (kitchen) BOM whose content fits in this room envelope."
            double zExtent = line.getZExtentRatio();
            int childH = zExtent > 0 ? (int)(allocH * zExtent) : allocH;
            int childW = allocW;
            int childD = allocD;

            // Duplex: PR (Pair) → 2× HU (Half-Unit), each gets half the width.
            // The second HU has PARTY_WALL_PI mirroring — one KT/LI/BD BOM serves
            // both half-units. No need for duplicate categories (KA/KB).
            if ("PR".equals(parentCategoryId) && "HU".equals(childId)) {
                childW = allocW / 2;
            }

            MBomCategory cat = MBomCategory.get(conn, childId);
            String catName = cat != null ? cat.getName() : childId;

            boolean isContainer = tree.containsKey(childId);

            if (isContainer) {
                // Container node (e.g. GF, L1, L2, PR, HU): structural level that
                // has sub-categories. Try to find a BOM at this level, then recurse
                // into children. The BOM here represents the container itself
                // (e.g. a floor assembly), while children represent sub-rooms.
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
                // Leaf node (e.g. KT, LI, BD, BT, DN): sub-room level.
                // This is a catalog item — the user's "browsable" choice.
                // findBestFitAnyOwner searches the ENTIRE catalog for the best
                // BOM of this category whose content fits the allocated AABB.
                //
                // The BOM at this level has room-envelope AABB. Its children are
                // content (cabinets, furniture, fixtures = BUY) + gap fillers
                // (PHANTOM). Packed-box principle: children + PHANTOMs = parent AABB.
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
