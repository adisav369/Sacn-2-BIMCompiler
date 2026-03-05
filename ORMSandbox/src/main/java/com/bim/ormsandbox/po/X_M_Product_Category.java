package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code M_Product_Category}.
 *
 * <p>iDempiere M_Product_Category analogue — self-referencing tree of product
 * classifications. Parent nodes are disciplines (Structural, MEP, Architectural,
 * Assembly). Leaf nodes carry the IFC class name.
 *
 * <p>Table: {@code M_Product_Category} (BOM.db)
 * <pre>
 *   M_Product_Category_ID  TEXT PRIMARY KEY  ('STR', 'MEP', 'ARC', 'ASM', 'IFC_WALL', ...)
 *   Name                   TEXT NOT NULL     ('Wall', 'Pipe / Duct Segment', 'Door', ...)
 *   Description            TEXT              (human-readable explanation)
 *   Parent_Category_ID     TEXT              (FK to self — NULL for root nodes)
 *   IFC_Class              TEXT              (IFC4 class name — leaf nodes only)
 *   SeqNo                  INTEGER           (display order within parent)
 *   IsActive               INTEGER DEFAULT 1
 * </pre>
 */
public class X_M_Product_Category extends BasePO {

    public static final String Table_Name                           = "M_Product_Category";
    public static final String COLUMNNAME_M_Product_Category_ID     = "M_Product_Category_ID";
    public static final String COLUMNNAME_Name                      = "Name";
    public static final String COLUMNNAME_Description               = "Description";
    public static final String COLUMNNAME_Parent_Category_ID        = "Parent_Category_ID";
    public static final String COLUMNNAME_IFC_Class                 = "IFC_Class";
    public static final String COLUMNNAME_SeqNo                     = "SeqNo";
    public static final String COLUMNNAME_IsActive                  = "IsActive";

    public X_M_Product_Category(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_M_Product_Category_ID; }

    public String  getCategoryId()       { return get_ValueAsString(COLUMNNAME_M_Product_Category_ID); }
    public String  getName()             { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDescription()      { return get_ValueAsString(COLUMNNAME_Description); }
    public String  getParentCategoryId() { return get_ValueAsString(COLUMNNAME_Parent_Category_ID); }
    public String  getIfcClass()         { return get_ValueAsString(COLUMNNAME_IFC_Class); }
    public int     getSeqNo()            { return get_ValueAsInt(COLUMNNAME_SeqNo); }
    public boolean isActive()            { return get_ValueAsBoolean(COLUMNNAME_IsActive); }

    public void setCategoryId(String v)       { set_Value(COLUMNNAME_M_Product_Category_ID, v); }
    public void setName(String v)             { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v)      { set_Value(COLUMNNAME_Description, v); }
    public void setParentCategoryId(String v) { set_Value(COLUMNNAME_Parent_Category_ID, v); }
    public void setIfcClass(String v)         { set_Value(COLUMNNAME_IFC_Class, v); }
    public void setSeqNo(int v)               { set_Value(COLUMNNAME_SeqNo, v); }
    public void setIsActive(boolean v)        { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
}
