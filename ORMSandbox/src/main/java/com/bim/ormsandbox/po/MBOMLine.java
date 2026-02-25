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
    public boolean isNestedBom() { return getChildBomId() != null; }

    /** True if this child references a leaf element (geometry via child_name_pattern). */
    public boolean isLeaf() { return getChildBomId() == null && getChildNamePattern() != null; }

    /** True if this child is a buffer/spacer (variable AABB, absorbs remaining parent space). */
    public boolean isBuffer() { return isVariance(); }

    /** True if SpaceSize is set (any axis > 0). */
    public boolean hasSpaceSize() {
        return getSpaceWidthMm() > 0 || getSpaceDepthMm() > 0 || getSpaceHeightMm() > 0;
    }

    /** Three-table authority: dx/dy/dz are assembly-relative offsets ONLY. */
    public String describeOffset() {
        return String.format("dx=%.3f dy=%.3f dz=%.3f rot=%s",
            getDx(), getDy(), getDz(), getRotationRule());
    }

    /** SpaceSize summary for debugging: WxDxH mm. */
    public String describeSpace() {
        return String.format("%dx%dx%d mm",
            getSpaceWidthMm(), getSpaceDepthMm(), getSpaceHeightMm());
    }
}
