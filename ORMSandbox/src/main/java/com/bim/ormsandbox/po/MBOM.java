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
     */
    public record SpaceSizeResult(boolean valid,
                                  int parentW, int parentD, int parentH,
                                  int sumW, int sumD, int sumH,
                                  String message) {

        public static SpaceSizeResult ok(int pw, int pd, int ph) {
            return new SpaceSizeResult(true, pw, pd, ph, pw, pd, ph, "");
        }
    }

    /**
     * W-SPACESIZE-1 gate: validates that a parent BOM's SpaceSize equals the sum
     * of its children's SpaceSize, per axis.
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

        int sumW = 0, sumD = 0, sumH = 0;
        for (MBOMLine child : children) {
            sumW += child.getSpaceWidthMm();
            sumD += child.getSpaceDepthMm();
            sumH += child.getSpaceHeightMm();
        }

        boolean valid = (sumW == parentWidthMm) && (sumD == parentDepthMm) && (sumH == parentHeightMm);

        if (valid) {
            return SpaceSizeResult.ok(parentWidthMm, parentDepthMm, parentHeightMm);
        }

        String msg = String.format(
            "BOM %s: parent=%dx%dx%d, children_sum=%dx%dx%d, delta=(%+d, %+d, %+d)",
            bomId, parentWidthMm, parentDepthMm, parentHeightMm,
            sumW, sumD, sumH,
            sumW - parentWidthMm, sumD - parentDepthMm, sumH - parentHeightMm);

        return new SpaceSizeResult(false, parentWidthMm, parentDepthMm, parentHeightMm,
                                   sumW, sumD, sumH, msg);
    }

    // ─── Buffer Fill ────────────────────────────────────────────────────────

    /**
     * Computes SpaceSize for buffer children (is_variance=1) so the invariant holds.
     *
     * <p>Does NOT save — caller must call {@code child.save()} after review.
     */
    public static List<MBOMLine> fillSpaceBufferChildren(Connection conn, String bomId,
                                                          int parentWidthMm, int parentDepthMm,
                                                          int parentHeightMm)
            throws SQLException {

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);

        List<MBOMLine> fixed = new ArrayList<>();
        List<MBOMLine> buffers = new ArrayList<>();
        for (MBOMLine child : children) {
            if (child.isBuffer()) {
                buffers.add(child);
            } else {
                fixed.add(child);
            }
        }

        if (buffers.isEmpty()) {
            return buffers;
        }

        int fixedW = 0, fixedD = 0, fixedH = 0;
        for (MBOMLine f : fixed) {
            fixedW += f.getSpaceWidthMm();
            fixedD += f.getSpaceDepthMm();
            fixedH += f.getSpaceHeightMm();
        }

        int remainW = parentWidthMm - fixedW;
        int remainD = parentDepthMm - fixedD;
        int remainH = parentHeightMm - fixedH;

        if (remainW < 0 || remainD < 0 || remainH < 0) {
            throw new IllegalStateException(String.format(
                "BOM %s: fixed children exceed parent — parent=%dx%dx%d, fixed_sum=%dx%dx%d",
                bomId, parentWidthMm, parentDepthMm, parentHeightMm, fixedW, fixedD, fixedH));
        }

        int n = buffers.size();
        for (int i = 0; i < n; i++) {
            MBOMLine buf = buffers.get(i);
            if (i < n - 1) {
                buf.setSpaceWidthMm(remainW / n);
                buf.setSpaceDepthMm(remainD / n);
                buf.setSpaceHeightMm(remainH / n);
            } else {
                buf.setSpaceWidthMm(remainW - (remainW / n) * (n - 1));
                buf.setSpaceDepthMm(remainD - (remainD / n) * (n - 1));
                buf.setSpaceHeightMm(remainH - (remainH / n) * (n - 1));
            }
        }

        return buffers;
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
     * Computes the total SpaceSize of a BOM from the sum of its children's SpaceSize.
     *
     * @return int[3] = {sumWidth, sumDepth, sumHeight} in mm
     */
    public static int[] computeTotalChildSpace(Connection conn, String bomId)
            throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int sumW = 0, sumD = 0, sumH = 0;
        for (MBOMLine child : children) {
            sumW += child.getSpaceWidthMm();
            sumD += child.getSpaceDepthMm();
            sumH += child.getSpaceHeightMm();
        }
        return new int[]{sumW, sumD, sumH};
    }
}
