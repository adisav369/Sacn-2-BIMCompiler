package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code M_Product_Category}.
 *
 * // Implementing SpecsAnalysis.txt §3 — drop Parent_Category_ID
 * <p>iDempiere M_Product_Category analogue — FLAT product classification.
 * Discipline parents (Structural, MEP, Architectural, Assembly) and IFC class leaves
 * coexist as flat categories. Hierarchy lives in M_BOM_Line, not here.
 *
 * <p>Table: {@code M_Product_Category} (ERP.db)
 * <pre>
 *   M_Product_Category_ID  INTEGER PRIMARY KEY AUTOINCREMENT  (opaque surrogate)
 *   Value                  TEXT NOT NULL UNIQUE                (search key — 'STR', 'MEP', ...)
 *   Name                   TEXT NOT NULL                      ('Structural', 'MEP', ...)
 *   Description            TEXT
 *   IFC_Class              TEXT              (IFC4 class name — leaf nodes only)
 *   SeqNo                  INTEGER           (display order)
 *   IsActive               INTEGER DEFAULT 1
 * </pre>
 */
public class X_M_Product_Category extends BasePO {

    public static final String Table_Name                           = "M_Product_Category";
    public static final String COLUMNNAME_M_Product_Category_ID     = "M_Product_Category_ID";
    public static final String COLUMNNAME_Value                     = "Value";
    public static final String COLUMNNAME_Name                      = "Name";
    public static final String COLUMNNAME_Description               = "Description";
    public static final String COLUMNNAME_IFC_Class                 = "IFC_Class";
    public static final String COLUMNNAME_SeqNo                     = "SeqNo";
    public static final String COLUMNNAME_IsActive                  = "IsActive";

    public X_M_Product_Category(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_M_Product_Category_ID; }

    public int     getCategoryId()       { return get_ValueAsInt(COLUMNNAME_M_Product_Category_ID); }
    public String  getValue()            { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getName()             { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDescription()      { return get_ValueAsString(COLUMNNAME_Description); }
    public String  getIfcClass()         { return get_ValueAsString(COLUMNNAME_IFC_Class); }
    public int     getSeqNo()            { return get_ValueAsInt(COLUMNNAME_SeqNo); }
    public boolean isActive()            { return get_ValueAsBoolean(COLUMNNAME_IsActive); }

    public void setCategoryId(int v)            { set_Value(COLUMNNAME_M_Product_Category_ID, v); }
    public void setValue(String v)              { set_Value(COLUMNNAME_Value, v); }
    public void setName(String v)               { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v)        { set_Value(COLUMNNAME_Description, v); }
    public void setIfcClass(String v)           { set_Value(COLUMNNAME_IFC_Class, v); }
    public void setSeqNo(int v)                 { set_Value(COLUMNNAME_SeqNo, v); }
    public void setIsActive(boolean v)          { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
}
