package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Model layer for {@code m_bom} (iDempiere: MBOM extends X_M_BOM).
 *
 * <p>Three orthogonal dimensions:
 * <ul>
 *   <li>{@code m_product_category_id} — M_Product_Category FK (WHAT: LI, BD, KT, FR, ST, ...)</li>
 *   <li>{@code doc_base_type} — C_DocType.DocBaseType (RE=Residential, CO=Commercial, IN=Industrial)</li>
 *   <li>{@code doc_sub_type} — C_DocType.DocSubType (WHICH variant: SH, DX, TB, TE; NULL = generic)</li>
 *   <li>SpaceSize — on M_BOM_Line children (HOW MUCH: AABB in mm)</li>
 * </ul>
 *
 * <p>Business logic:
 * <ul>
 *   <li>{@link #isSpaceSizeValid} — W-SPACESIZE-1: parent AABB == SUM(children AABB)</li>
 *   <li>{@link #fillSpaceBufferChildren} — compute buffer children to make invariant true</li>
 *   <li>{@link #findNextFitSpace} — find M_BOM by category with AABB &lt;= requested size</li>
 * </ul>
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept</a>
 */
public class MBOM extends X_M_BOM {

    public MBOM(Connection conn) { super(conn); }

    // ─── Factory Methods ────────────────────────────────────────────────────

    /** Load by bom_id TEXT PK. Returns null if not found or inactive. */
    public static MBOM get(Connection conn, String bomId) throws SQLException {
        MBOM bom = new MBOM(conn);
        return bom.load(bomId) && bom.isActive() ? bom : null;
    }

    /** All active BOMs of a given type (UNIT, FLOOR, ROOM, SET, ITEM). */
    public static List<MBOM> getByType(Connection conn, String bomType) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_bom_type + " = ?", bomType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no + ", " + COLUMNNAME_bom_id)
            .list();
    }

    /** All active BOMs of a given functional category (LI, BD, KT, FR, ST, ...). */
    public static List<MBOM> getByCategory(Connection conn, String productCategory) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_m_product_category_id + " = ?", productCategory)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no + ", " + COLUMNNAME_bom_id)
            .list();
    }

    /**
     * Find the top-level BUILDING BOM for a given DocSubType (e.g. "SH" → BUILDING_SH_STD).
     * In iDempiere Mfg, this is the finished goods BOM — one per building type.
     * Returns null if not found.
     */
    public static MBOM getBuildingBom(Connection conn, String docSubType) throws SQLException {
        List<MBOM> bldgs = new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_bom_type + " = ?", "BUILDING")
            .andWhere(COLUMNNAME_doc_sub_type + " = ?", docSubType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no)
            .list();
        return bldgs.isEmpty() ? null : bldgs.get(0);
    }

    /** All active BOMs whose bom_id starts with the given prefix. */
    public static List<MBOM> getByBomIdPrefix(Connection conn, String prefix) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_bom_id + " LIKE ?", prefix + "%")
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no + ", " + COLUMNNAME_bom_id)
            .list();
    }

    /**
     * All active BOMs within a given variant scope (DocSubType).
     * Includes generic BOMs (doc_sub_type IS NULL).
     */
    public static List<MBOM> getByDocSubType(Connection conn, String docSubType) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where("(" + COLUMNNAME_doc_sub_type + " = ? OR " + COLUMNNAME_doc_sub_type + " IS NULL)", docSubType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no + ", " + COLUMNNAME_bom_id)
            .list();
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (getGroupBy() == null)
            throw new IllegalStateException("group_by must not be null (m_bom NOT NULL constraint)");
        // EntityType guard: dictionary records are read-only through PO layer
        if (!newRecord && !isGodMode() && ENTITYTYPE_Dictionary.equals(getEntityType()))
            throw new IllegalStateException(
                "MBOM " + getBomId() + " is EntityType=D (Dictionary) — read-only. "
                + "Use verbs to create new SY_ records (EntityType=U). "
                + "Place GodMode.txt in working directory to override.");

        // ValidateBOM (iDempiere convention): conformity check on save.
        // Construction quirk: envelope is top-down (known from design brief),
        // not bottom-up like manufacturing. ValidateBOM warns, never blocks.
        //
        // SET level (room → furniture): auto-fill gaps with buffer fillers
        // between tight BBoxes, then validate strip-packing invariant.
        // BUILDING/FLOOR level: warn if children exceed declared envelope.
        if (!newRecord && getAabbWidthMm() > 0) {
            try {
                if ("SET".equals(getBomType())) {
                    // Strip-packing: fill gaps between furniture children
                    Filler.fill(conn, getBomId(),
                        getAabbWidthMm(), getAabbDepthMm(), getAabbHeightMm());
                }
                // Warn if children exceed declared parent AABB (all levels)
                int[] childSpace = computeTotalChildSpace(conn, getBomId());
                if (childSpace[0] > getAabbWidthMm()
                        || childSpace[1] > getAabbDepthMm()
                        || childSpace[2] > getAabbHeightMm()) {
                    System.err.printf("[ValidateBOM] %s: children %dx%dx%d exceed parent %dx%dx%d%n",
                        getBomId(), childSpace[0], childSpace[1], childSpace[2],
                        getAabbWidthMm(), getAabbDepthMm(), getAabbHeightMm());
                }
            } catch (java.sql.SQLException e) {
                System.err.printf("[ValidateBOM] %s: validation failed — %s%n",
                    getBomId(), e.getMessage());
            }
        }
    }

    /**
     * EntityType guard on delete: dictionary records cannot be deleted through PO layer.
     * Only User (U) and Application (A) records can be deleted.
     */
    @Override
    public boolean delete() throws java.sql.SQLException {
        if (!isGodMode() && ENTITYTYPE_Dictionary.equals(getEntityType()))
            throw new IllegalStateException(
                "MBOM " + getBomId() + " is EntityType=D (Dictionary) — cannot delete. "
                + "Only SY_ records (EntityType=U) can be deleted. "
                + "Place GodMode.txt in working directory to override.");
        return super.delete();
    }

    // ─── SpaceSize Invariant ────────────────────────────────────────────────

    /**
     * Result of a SpaceSize validation check.
     *
     * <p>Axis model: Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance).
     */
    public record SpaceSizeResult(boolean valid,
                                  int parentW, int parentD, int parentH,
                                  int sumW, int maxD, int maxH,
                                  String message) {

        public static SpaceSizeResult ok(int pw, int pd, int ph) {
            return new SpaceSizeResult(true, pw, pd, ph, pw, pd, ph, "");
        }
    }

    /**
     * W-SPACESIZE-1 gate: validates that a parent BOM's SpaceSize matches its children.
     *
     * <p>Axis model: Width=SUM (must equal parent), Depth=MAX (&lt;= parent),
     * Height=MAX (&lt;= parent).
     *
     * @param conn           JDBC connection
     * @param bomId          the parent BOM to validate
     * @param parentWidthMm  expected parent width in mm
     * @param parentDepthMm  expected parent depth in mm
     * @param parentHeightMm expected parent height in mm
     * @return validation result with axis-by-axis comparison
     */
    public static SpaceSizeResult isSpaceSizeValid(Connection conn, String bomId,
                                                    int parentWidthMm, int parentDepthMm,
                                                    int parentHeightMm)
            throws SQLException {

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        if (children.isEmpty()) {
            return SpaceSizeResult.ok(parentWidthMm, parentDepthMm, parentHeightMm);
        }

        int sumW = 0, maxD = 0, maxH = 0;
        for (MBOMLine child : children) {
            sumW += child.getAllocatedWidthMm();
            maxD = Math.max(maxD, child.getAllocatedDepthMm());
            maxH = Math.max(maxH, child.getAllocatedHeightMm());
        }

        boolean valid = (sumW == parentWidthMm) && (maxD <= parentDepthMm) && (maxH <= parentHeightMm);

        if (valid) {
            return SpaceSizeResult.ok(parentWidthMm, parentDepthMm, parentHeightMm);
        }

        String msg = String.format(
            "BOM %s: parent=%dx%dx%d, children sumW=%d maxD=%d maxH=%d, delta_w=%+d",
            bomId, parentWidthMm, parentDepthMm, parentHeightMm,
            sumW, maxD, maxH, sumW - parentWidthMm);

        return new SpaceSizeResult(false, parentWidthMm, parentDepthMm, parentHeightMm,
                                   sumW, maxD, maxH, msg);
    }

    // ─── Buffer Fill ────────────────────────────────────────────────────────

    /**
     * Create interstitial fillers between consecutive fixed items so the strip invariant holds.
     *
     * <p>Delegates to {@link Filler#fill} which handles the full lifecycle:
     * delete old buffers, renumber, create N−1 interstitial fillers, save.
     *
     * <p>Axis model: Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance).
     *
     * @return the newly created filler MBOMLine records (already saved)
     */
    public static List<MBOMLine> fillSpaceBufferChildren(Connection conn, String bomId,
                                                          int parentWidthMm, int parentDepthMm,
                                                          int parentHeightMm)
            throws SQLException {

        Filler.FillResult result = Filler.fill(conn, bomId, parentWidthMm, parentDepthMm, parentHeightMm);
        return result.created();
    }

    // ─── Fit Finder ─────────────────────────────────────────────────────────

    /**
     * Finds the best-fit M_BOM within a functional category whose SpaceSize
     * fits within the requested AABB (all three axes &lt;= requested).
     *
     * <p>Selection cascade: AABB fit (primary) → largest volume → lowest seq_no (tiebreaker).
     * Variant-specific BOMs (doc_sub_type match) are included alongside generics.
     */
    public static MBOM findNextFitSpace(Connection conn, String productCategory,
                                         String docSubType,
                                         int maxWidthMm, int maxDepthMm, int maxHeightMm)
            throws SQLException {

        String ownerFilter = docSubType != null
            ? "(" + COLUMNNAME_doc_sub_type + " = ? OR " + COLUMNNAME_doc_sub_type + " IS NULL)"
            : COLUMNNAME_doc_sub_type + " IS NULL";

        ModelQuery<MBOM> query = new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_m_product_category_id + " = ?", productCategory)
            .andWhere(COLUMNNAME_is_active + " = ?", 1);

        if (docSubType != null) {
            query.andWhere(ownerFilter, docSubType);
        } else {
            query.andWhere(ownerFilter);
        }

        List<MBOM> candidates = query.orderBy(COLUMNNAME_seq_no + ", " + COLUMNNAME_bom_id).list();

        MBOM bestFit = null;
        long bestVolume = -1;

        for (MBOM candidate : candidates) {
            int[] totalSpace = computeTotalChildSpace(conn, candidate.getBomId());
            int tw = totalSpace[0], td = totalSpace[1], th = totalSpace[2];

            if (tw <= maxWidthMm && td <= maxDepthMm && th <= maxHeightMm) {
                long volume = (long) tw * td * th;
                if (volume > bestVolume) {
                    bestVolume = volume;
                    bestFit = candidate;
                }
            }
        }

        return bestFit;
    }

    /**
     * Find best-fit BOM in a category from ALL variants (no doc_sub_type filter).
     *
     * <p>This is the core AABB matching engine used by {@link BomTemplateComposer}
     * at every level of the template tree. Given a category (KT, LI, BD, etc.)
     * and an allocated AABB (the room envelope from the parent floor), it searches
     * the <b>entire catalog</b> for the best BOM whose content fits within that space.
     *
     * <h4>Why no doc_sub_type filter?</h4>
     * <p>The template path is variant-agnostic. AABB alone drives selection.
     * Since only DX owns PR/HU BOMs, those self-select without needing a filter.
     * A kitchen BOM (KT) that fits SH dimensions will be selected for SH builds;
     * one that fits DX half-unit dimensions will be selected for DX builds.
     * The catalog constraints do the work — no explicit variant matching needed.
     *
     * <h4>Selection cascade</h4>
     * <ol>
     *   <li>AABB fit: children must fit within allocated space (primary gate)</li>
     *   <li>Largest volume: prefer fuller utilization of available space</li>
     *   <li>Lowest seq_no: tiebreaker for equal-volume candidates</li>
     * </ol>
     *
     * <h4>Fit model by bom_type</h4>
     * <ul>
     *   <li>SET: 1D strip — Width=SUM must fit, Depth=MAX, Height=MAX.
     *       Room sub-BOMs pack content along one axis (e.g. kitchen cabinets
     *       along a wall). The strip must fit within the room envelope.</li>
     *   <li>FLOOR/UNIT: 2D room tiling — rooms tile within a floor plan,
     *       not in a 1D strip. Accept if BOM exists (structural container).</li>
     *   <li>No children (all-zero space): always fits (leaf or empty container)</li>
     * </ul>
     *
     * <h4>Packed-box principle</h4>
     * <p>Each BOM's children (BUY) + gap fillers (PHANTOM) should fully tile
     * the parent AABB. In BOM.db the box is packed; in output.db PHANTOMs are
     * stripped and only real content (BUY) remains at tack positions.
     */
    public static MBOM findBestFitAnyOwner(Connection conn, String productCategory,
                                            int maxWidthMm, int maxDepthMm, int maxHeightMm)
            throws SQLException {

        List<MBOM> candidates = new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_m_product_category_id + " = ?", productCategory)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no + ", " + COLUMNNAME_bom_id)
            .list();

        MBOM bestFit = null;
        long bestVolume = -1;

        for (MBOM candidate : candidates) {
            int[] totalSpace = computeTotalChildSpace(conn, candidate.getBomId());
            int tw = totalSpace[0], td = totalSpace[1], th = totalSpace[2];

            boolean fits;
            if (tw == 0 && td == 0 && th == 0) {
                // No children or all-zero space → always fits
                fits = true;
            } else if ("SET".equals(candidate.getBomType())) {
                // SET: 1D strip model — Width=SUM, Depth=MAX, Height=MAX
                fits = tw <= maxWidthMm && td <= maxDepthMm && th <= maxHeightMm;
            } else {
                // FLOOR/UNIT: 2D room tiling — accept if exists
                // Rooms tile within a floor plan, not in a 1D strip
                fits = true;
            }

            if (fits) {
                long volume = (long) tw * td * th;
                if (volume > bestVolume) {
                    bestVolume = volume;
                    bestFit = candidate;
                }
            }
        }

        return bestFit;
    }

    /**
     * Computes the total SpaceSize of a BOM from its children.
     *
     * <p>Axis model: Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance).
     *
     * @return int[3] = {sumWidth, maxDepth, maxHeight} in mm
     */
    public static int[] computeTotalChildSpace(Connection conn, String bomId)
            throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int sumW = 0, maxD = 0, maxH = 0;
        for (MBOMLine child : children) {
            sumW += child.getAllocatedWidthMm();
            maxD = Math.max(maxD, child.getAllocatedDepthMm());
            maxH = Math.max(maxH, child.getAllocatedHeightMm());
        }
        return new int[]{sumW, maxD, maxH};
    }
}
