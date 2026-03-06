package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code m_bom_line} (iDempiere: M_BOM_Line).
 */
public class MBOMLine extends X_M_BOMLine {

    public MBOMLine(Connection conn) { super(conn); }

    /**
     * All active children of a given BOM, ordered by sequence.
     */
    public static List<MBOMLine> getByBom(Connection conn, String bomId)
            throws SQLException {
        return new ModelQuery<>(conn, MBOMLine::new, Table_Name)
            .where(COLUMNNAME_bom_id + " = ?", bomId)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_sequence)
            .list();
    }

    /** True if this child references a nested BOM (UNIT/FLOOR/SET chain). */
    public boolean isNestedBom() { return "MAKE".equals(getComponentType()); }

    /** True if this child references a leaf element (geometry via child_name_pattern or product_ref). */
    public boolean isLeaf() { return "BUY".equals(getComponentType()); }

    /** True if this child is a space placeholder expanded inline with no output record. */
    public boolean isPhantom() { return "PHANTOM".equals(getComponentType()); }

    /** True if this child is a buffer/spacer (variable AABB, absorbs remaining parent space). */
    public boolean isBuffer() { return isVariance(); }

    /**
     * Backward compat: returns child_product_id when this is a MAKE (nested BOM) child.
     * NORM-2: child_bom_id was merged into child_product_id.
     */
    public String getChildBomId() { return isNestedBom() ? getChildProductId() : null; }

    /** True if AllocatedSize is set (any axis > 0). */
    public boolean hasSpaceSize() {
        return getAllocatedWidthMm() > 0 || getAllocatedDepthMm() > 0 || getAllocatedHeightMm() > 0;
    }

    /** Three-table authority: dx/dy/dz are assembly-relative offsets ONLY (Rule 8). */
    public String describeOffset() {
        return String.format("dx=%.3f dy=%.3f dz=%.3f rot=%s",
            getDx(), getDy(), getDz(), getRotationRule());
    }

    /**
     * Rule 8 batch check: scans all active lines of a BOM for world-absolute
     * coordinates. Returns list of diagnostic messages (empty = clean).
     *
     * @param conn    BOM.db connection
     * @param bomId   parent BOM id
     * @param parentWidthMm  parent AABB width mm
     * @param parentDepthMm  parent AABB depth mm
     * @param parentHeightMm parent AABB height mm
     */
    public static List<String> checkRule8(Connection conn, String bomId,
                                          double parentWidthMm,
                                          double parentDepthMm,
                                          double parentHeightMm) throws SQLException {
        List<String> violations = new java.util.ArrayList<>();
        for (MBOMLine line : getByBom(conn, bomId)) {
            String msg = line.validateParentRelative(parentWidthMm, parentDepthMm, parentHeightMm);
            if (msg != null) {
                violations.add(String.format("[%s seq=%d] %s",
                    line.getBomId(), line.getSequence(), msg));
            }
        }
        return violations;
    }

    /** AllocatedSize summary for debugging: WxDxH mm. */
    public String describeSpace() {
        return String.format("%dx%dx%d mm",
            getAllocatedWidthMm(), getAllocatedDepthMm(), getAllocatedHeightMm());
    }
}
