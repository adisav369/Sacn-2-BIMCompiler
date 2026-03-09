package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code M_BomCategory} (iDempiere: M_Product_Category).
 *
 * <p>Functional category lookup. Determines WHAT the BOM contains.
 * Orthogonal to c_bpartner (WHO) and SpaceSize (HOW MUCH).
 *
 * <p>Table: {@code M_BomCategory}
 * <pre>
 *   M_BomCategory_ID  TEXT PRIMARY KEY  (LI, BD, KT, BT, DN, FR, ST, L1, L2, UN, GF, RE)
 *   Name               TEXT NOT NULL     human-readable ('Living Room', 'Ground Floor')
 *   Description         TEXT
 *   IsActive            INTEGER DEFAULT 1
 *   C_BPartner_ID       TEXT              FK → C_BPartner — owner scope for templates (NULL = shared)
 *   Value               TEXT              CamelCase search key ('Living', 'GroundFloor')
 *   aabb_width_mm       REAL              template AABB width — NULL for non-template categories
 *   aabb_depth_mm       REAL              template AABB depth
 *   aabb_height_mm      REAL              template AABB height
 *   doc_type            TEXT              RE | CO | IN | ST (NULL = generic)
 * </pre>
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept — §2.1</a>
 */
public class X_M_BomCategory extends BasePO {

    public static final String Table_Name                     = "M_BomCategory";
    public static final String COLUMNNAME_M_BomCategory_ID    = "M_BomCategory_ID";
    public static final String COLUMNNAME_Name                = "Name";
    public static final String COLUMNNAME_Description         = "Description";
    public static final String COLUMNNAME_IsActive            = "IsActive";
    public static final String COLUMNNAME_C_BPartner_ID      = "C_BPartner_ID";
    public static final String COLUMNNAME_Value              = "Value";
    public static final String COLUMNNAME_aabb_width_mm      = "aabb_width_mm";
    public static final String COLUMNNAME_aabb_depth_mm      = "aabb_depth_mm";
    public static final String COLUMNNAME_aabb_height_mm     = "aabb_height_mm";
    public static final String COLUMNNAME_doc_type           = "doc_type";

    /**
     * Functional category codes — the template tree hierarchy.
     *
     * <p>A floor is a <b>container of sub-rooms</b>. Each sub-room (KT, LI, BD,
     * BT, DN) is a catalog item users can browse and select — "Kitchen Style A"
     * vs "Kitchen Style B". AABB matching at each level drives the selection.
     *
     * <pre>
     *   RE (Residential Template — structural grammar root)
     *    ├─ SL  (Slab)
     *    ├─ GF  (Ground Floor — container of sub-rooms)
     *    │   ├─ LI  (Living — sub-room, catalog item)
     *    │   ├─ BD  (Bedroom — sub-room, catalog item)
     *    │   ├─ KT  (Kitchen — sub-room, catalog item)
     *    │   ├─ BT  (Bathroom — sub-room, catalog item)
     *    │   └─ DN  (Dining — sub-room, catalog item)
     *    └─ RF  (Roof)
     *
     *   For duplex (num_units=2):
     *    ├─ PR  (Pair — splits width by 2)
     *    │   ├─ HU  (Half-Unit A)
     *    │   │   ├─ L1  → {LI, BD, KT, BT, DN}
     *    │   │   └─ L2  → {BD, KT, BT}
     *    │   └─ HU  (Half-Unit B — PARTY_WALL_PI mirroring)
     *    │       └─ (same as A, rotated π)
     * </pre>
     *
     * <p>Each sub-room BOM has room-envelope AABB. Its children are content
     * (cabinets, furniture = BUY) + gap fillers (PHANTOM). Packed-box principle:
     * children + PHANTOMs = parent AABB. At output, PHANTOMs are stripped.
     */
    public static final String CATEGORY_LIVING       = "LI";  // sub-room: living room
    public static final String CATEGORY_BEDROOM      = "BD";  // sub-room: bedroom
    public static final String CATEGORY_KITCHEN      = "KT";  // sub-room: kitchen
    public static final String CATEGORY_BATHROOM     = "BT";  // sub-room: bathroom/toilet
    public static final String CATEGORY_DINING       = "DN";  // sub-room: dining room
    public static final String CATEGORY_FURNITURE    = "FR";  // content: furniture items
    public static final String CATEGORY_SPACE        = "ST";  // filler: variable-AABB spacer
    public static final String CATEGORY_LEVEL1       = "L1";  // container: ground floor level
    public static final String CATEGORY_LEVEL2       = "L2";  // container: upper floor level
    public static final String CATEGORY_UNIT         = "UN";  // container: complete building unit
    public static final String CATEGORY_GROUND_FLOOR = "GF";  // container: ground floor
    public static final String CATEGORY_RESIDENTIAL  = "RE";  // template root: residential grammar

    public X_M_BomCategory(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_M_BomCategory_ID; }

    public String  getCategoryId()   { return get_ValueAsString(COLUMNNAME_M_BomCategory_ID); }
    public String  getName()         { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDescription()  { return get_ValueAsString(COLUMNNAME_Description); }
    public boolean isActive()        { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String  getCBPartnerId()  { return get_ValueAsString(COLUMNNAME_C_BPartner_ID); }
    public String  getValue()        { return get_ValueAsString(COLUMNNAME_Value); }

    /** Template AABB width in mm. Returns 0 when not set (non-template categories). */
    public double  getAabbWidthMm()  { return get_ValueAsDouble(COLUMNNAME_aabb_width_mm); }
    /** Template AABB depth in mm. Returns 0 when not set. */
    public double  getAabbDepthMm()  { return get_ValueAsDouble(COLUMNNAME_aabb_depth_mm); }
    /** Template AABB height in mm. Returns 0 when not set. */
    public double  getAabbHeightMm() { return get_ValueAsDouble(COLUMNNAME_aabb_height_mm); }

    /** DocType scoping: RE, CO, IN, ST. NULL = generic/all. */
    public String  getDocType()      { return get_ValueAsString(COLUMNNAME_doc_type); }

    public void setCategoryId(String v)    { set_Value(COLUMNNAME_M_BomCategory_ID, v); }
    public void setName(String v)          { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v)   { set_Value(COLUMNNAME_Description, v); }
    public void setIsActive(boolean v)     { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setCBPartnerId(String v)   { set_Value(COLUMNNAME_C_BPartner_ID, v); }
    public void setValue(String v)         { set_Value(COLUMNNAME_Value, v); }
    public void setAabbWidthMm(double v)   { set_Value(COLUMNNAME_aabb_width_mm, v); }
    public void setAabbDepthMm(double v)   { set_Value(COLUMNNAME_aabb_depth_mm, v); }
    public void setAabbHeightMm(double v)  { set_Value(COLUMNNAME_aabb_height_mm, v); }
    public void setDocType(String v)      { set_Value(COLUMNNAME_doc_type, v); }
}
