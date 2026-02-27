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
     * Find the template root category for a given C_BPartner.
     * E.g. C_BPartner_ID='ST' → returns the RE (Residential Template) category.
     * Returns null if no template is defined for that partner.
     */
    public static MBomCategory getTemplateByCBPartner(Connection conn, String cbpartnerId) throws SQLException {
        return new ModelQuery<>(conn, MBomCategory::new, Table_Name)
            .where(COLUMNNAME_C_BPartner_ID + " = ?", cbpartnerId)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }
}
