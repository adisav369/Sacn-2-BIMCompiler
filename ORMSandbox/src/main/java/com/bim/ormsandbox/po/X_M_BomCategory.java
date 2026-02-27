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

    /** Functional category codes. */
    public static final String CATEGORY_LIVING       = "LI";
    public static final String CATEGORY_BEDROOM      = "BD";
    public static final String CATEGORY_KITCHEN      = "KT";
    public static final String CATEGORY_BATHROOM     = "BT";
    public static final String CATEGORY_DINING       = "DN";
    public static final String CATEGORY_FURNITURE    = "FR";
    public static final String CATEGORY_SPACE        = "ST";
    public static final String CATEGORY_LEVEL1       = "L1";
    public static final String CATEGORY_LEVEL2       = "L2";
    public static final String CATEGORY_UNIT         = "UN";
    public static final String CATEGORY_GROUND_FLOOR = "GF";
    public static final String CATEGORY_RESIDENTIAL  = "RE";

    public X_M_BomCategory(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_M_BomCategory_ID; }

    public String  getCategoryId()  { return get_ValueAsString(COLUMNNAME_M_BomCategory_ID); }
    public String  getName()        { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDescription()  { return get_ValueAsString(COLUMNNAME_Description); }
    public boolean isActive()        { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String  getCBPartnerId()  { return get_ValueAsString(COLUMNNAME_C_BPartner_ID); }
    public String  getValue()        { return get_ValueAsString(COLUMNNAME_Value); }

    public void setCategoryId(String v)   { set_Value(COLUMNNAME_M_BomCategory_ID, v); }
    public void setName(String v)         { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v)  { set_Value(COLUMNNAME_Description, v); }
    public void setIsActive(boolean v)    { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setCBPartnerId(String v)  { set_Value(COLUMNNAME_C_BPartner_ID, v); }
    public void setValue(String v)        { set_Value(COLUMNNAME_Value, v); }
}
