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
 *   <li>{@code bom_category} — M_BomCategory FK (WHAT: LI, BD, KT, FR, ST, ...)</li>
 *   <li>{@code bom_owner} — C_BPartner code (WHO: SH, DX, TB, TE; NULL = generic)</li>
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
            .orderBy(COLUMNNAME_bom_id)
            .list();
    }

    /** All active BOMs of a given functional category (LI, BD, KT, FR, ST, ...). */
    public static List<MBOM> getByCategory(Connection conn, String bomCategory) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_bom_category + " = ?", bomCategory)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_bom_id)
            .list();
    }

    /**
     * All active BOMs within a given owner scope (C_BPartner).
     * Includes generic BOMs (bom_owner IS NULL).
     */
    public static List<MBOM> getByOwner(Connection conn, String bomOwner) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where("(" + COLUMNNAME_bom_owner + " = ? OR " + COLUMNNAME_bom_owner + " IS NULL)", bomOwner)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_bom_id)
            .list();
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (getGroupBy() == null)
            throw new IllegalStateException("group_by must not be null (m_bom NOT NULL constraint)");
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
            sumW += child.getSpaceWidthMm();
            maxD = Math.max(maxD, child.getSpaceDepthMm());
            maxH = Math.max(maxH, child.getSpaceHeightMm());
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
     * Returns the largest that fits (maximize space usage).
     */
    public static MBOM findNextFitSpace(Connection conn, String bomCategory,
                                         String bomOwner,
                                         int maxWidthMm, int maxDepthMm, int maxHeightMm)
            throws SQLException {

        String ownerFilter = bomOwner != null
            ? "(" + COLUMNNAME_bom_owner + " = ? OR " + COLUMNNAME_bom_owner + " IS NULL)"
            : COLUMNNAME_bom_owner + " IS NULL";

        ModelQuery<MBOM> query = new ModelQuery<>(conn, MBOM::new, Table_Name)
            .where(COLUMNNAME_bom_category + " = ?", bomCategory)
            .andWhere(COLUMNNAME_is_active + " = ?", 1);

        if (bomOwner != null) {
            query.andWhere(ownerFilter, bomOwner);
        } else {
            query.andWhere(ownerFilter);
        }

        List<MBOM> candidates = query.orderBy(COLUMNNAME_bom_id).list();

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
            sumW += child.getSpaceWidthMm();
            maxD = Math.max(maxD, child.getSpaceDepthMm());
            maxH = Math.max(maxH, child.getSpaceHeightMm());
        }
        return new int[]{sumW, maxD, maxH};
    }
}
