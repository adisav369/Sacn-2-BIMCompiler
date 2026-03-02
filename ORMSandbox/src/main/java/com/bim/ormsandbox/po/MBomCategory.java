package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code M_BomCategory} (iDempiere: M_Product_Category).
 *
 * <p>19 codes: LI (Living), BD (Bedroom), KT (Kitchen), BT (Bathroom),
 * DN (Dining), FR (Furniture), ST (Space/Buffer), L1 (Level 1),
 * L2 (Level 2), UN (Unit), WL (Wall), PH (Porch), RF (Roof),
 * SL (Slab), PR (Pair), HU (HalfUnit), MP (MEP), GF (GroundFloor),
 * RE (ResidentialTemplate).
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept — §2.1</a>
 */
public class MBomCategory extends X_M_BomCategory {

    public MBomCategory(Connection conn) { super(conn); }

    /** Load by category code (LI, BD, KT, ...). Returns null if not found or inactive. */
    public static MBomCategory get(Connection conn, String categoryId) throws SQLException {
        MBomCategory cat = new MBomCategory(conn);
        return cat.load(categoryId) && cat.isActive() ? cat : null;
    }

    /** All active categories, ordered by ID. */
    public static List<MBomCategory> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MBomCategory::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_M_BomCategory_ID)
            .list();
    }

    /** True if this category represents a buffer/spacer (variable AABB). */
    public boolean isBufferCategory() {
        return CATEGORY_SPACE.equals(getCategoryId());
    }

    /** True if this category represents a room-level BOM (LI, BD, KT, BT, DN). */
    public boolean isRoomCategory() {
        String id = getCategoryId();
        return CATEGORY_LIVING.equals(id) || CATEGORY_BEDROOM.equals(id)
            || CATEGORY_KITCHEN.equals(id) || CATEGORY_BATHROOM.equals(id)
            || CATEGORY_DINING.equals(id);
    }

    /**
     * Find the template root category by doc_type.
     * E.g. doc_type='Residential' → returns the RE (Residential Template) category.
     *
     * <p>Template roots are M_BomCategory entries that define the structural
     * decomposition grammar (RE → SL, GF, RF). They are generic — not tied
     * to any C_BPartner. C_BPartner influences which M_BOM fills each slot,
     * not which template structure is used.
     *
     * @param conn    BOM.db connection
     * @param docType document type: Residential, Commercial, Industrial
     * @return the template root category, or null if not found
     */
    public static MBomCategory getTemplateByDocType(Connection conn, String docType) throws SQLException {
        return new ModelQuery<>(conn, MBomCategory::new, Table_Name)
            .where(COLUMNNAME_doc_type + " = ?", docType)
            .andWhere(COLUMNNAME_C_BPartner_ID + " IS NULL")
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .andWhere("Value LIKE '%Template'")
            .first()
            .orElse(null);
    }

    /**
     * @deprecated Use {@link #getTemplateByDocType} instead. Templates are
     * generic (no C_BPartner). ST is a test/demo partner, not a template owner.
     */
    @Deprecated
    public static MBomCategory getTemplateByCBPartner(Connection conn, String cbpartnerId) throws SQLException {
        return new ModelQuery<>(conn, MBomCategory::new, Table_Name)
            .where(COLUMNNAME_C_BPartner_ID + " = ?", cbpartnerId)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }

    /**
     * Find an AABB template category by category type and dimensions (walk-path lookup).
     *
     * <p>Used by the EXPLODE walk path: when a building's room dimensions match a
     * known M_BomCategory AABB template, that template's slot descriptors drive placement.
     * Tolerates a ±1% rounding difference (IFC export precision).
     *
     * @param conn       BOM.db connection
     * @param categoryId room category code (LI, BD, KT, BT, DN)
     * @param widthMm    actual room width in mm
     * @param depthMm    actual room depth in mm
     * @return matching template category, or null if no AABB match found
     */
    public static MBomCategory findByTypeAndAabb(
            Connection conn, String categoryId, double widthMm, double depthMm)
            throws SQLException {
        double tol = 0.01;  // 1% tolerance for IFC export rounding
        return new ModelQuery<>(conn, MBomCategory::new, Table_Name)
            .where(COLUMNNAME_M_BomCategory_ID + " LIKE ?", categoryId + "_%")
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .andWhere(COLUMNNAME_aabb_width_mm + " BETWEEN ? AND ?",
                      widthMm * (1 - tol), widthMm * (1 + tol))
            .andWhere(COLUMNNAME_aabb_depth_mm + " BETWEEN ? AND ?",
                      depthMm * (1 - tol), depthMm * (1 + tol))
            .first()
            .orElse(null);
    }
}
