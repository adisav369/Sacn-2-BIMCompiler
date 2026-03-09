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
     * EntityType guard: dictionary records are read-only through PO layer.
     * Only User (U) and Application (A) records can be modified.
     */
    @Override
    protected void beforeSave(boolean newRecord) {
        if (!newRecord && !X_M_BOM.isGodMode() && X_M_BOM.ENTITYTYPE_Dictionary.equals(getEntityType()))
            throw new IllegalStateException(
                "MBOMLine " + getBomChildId() + " (bom=" + getBomId()
                + ") is EntityType=D (Dictionary) — read-only. "
                + "Place GodMode.txt in working directory to override.");
    }

    /**
     * EntityType guard on delete: dictionary records cannot be deleted through PO layer.
     */
    @Override
    public boolean delete() throws java.sql.SQLException {
        if (!X_M_BOM.isGodMode() && X_M_BOM.ENTITYTYPE_Dictionary.equals(getEntityType()))
            throw new IllegalStateException(
                "MBOMLine " + getBomChildId() + " (bom=" + getBomId()
                + ") is EntityType=D (Dictionary) — cannot delete. "
                + "Place GodMode.txt in working directory to override.");
        return super.delete();
    }

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

    /**
     * MAKE = sub-assembly. child_product_id matches another bom_id in m_bom.
     * The BOM walker recurses into this child's BOM and walks its children.
     * Represents a structural level: BUILDING → FLOOR → SET → room content.
     */
    public boolean isNestedBom() { return "MAKE".equals(getComponentType()); }

    /**
     * BUY = leaf component from the component library with real geometry.
     * Produces a C_OrderLine + placement in the output DB. All content
     * (cabinets, walls, slabs, fixtures) ends up here after compilation.
     */
    public boolean isLeaf() { return "BUY".equals(getComponentType()); }

    /**
     * PHANTOM = gap filler in the BOM hierarchy. Exists in BOM.db to fully
     * tile the parent AABB (packed-box principle: children + PHANTOMs = parent).
     * Stripped at compile time — no output element, no geometry, no C_OrderLine.
     * Like foam packaging: present in the box, removed when placed on the floor.
     */
    public boolean isPhantom() { return "PHANTOM".equals(getComponentType()); }

    /**
     * Buffer = variable-AABB spacer that absorbs remaining parent space.
     * A specific kind of PHANTOM used for dynamic gap-filling.
     */
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
