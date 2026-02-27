package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code M_BomCategoryLine}.
 *
 * <p>Master-detail child of M_BomCategory. Defines the decomposition recipe
 * for template-driven compilation (ST mode). Each line maps a parent category
 * to a child category with ordering and Z-extent ratios.
 *
 * <p>Table: {@code M_BomCategoryLine}
 * <pre>
 *   M_BomCategoryLine_ID  INTEGER PRIMARY KEY AUTOINCREMENT
 *   M_BomCategory_ID      TEXT NOT NULL  FK → M_BomCategory (parent)
 *   Child_BomCategory_ID  TEXT NOT NULL  FK → M_BomCategory (child)
 *   Sequence              INTEGER NOT NULL DEFAULT 10
 *   Value                 TEXT NOT NULL  CamelCase search key ('FloorSlab')
 *   Name                  TEXT NOT NULL  human-readable ('Floor Slab')
 *   Z_Offset_Ratio        REAL DEFAULT 0.0   fraction of parent height for Z start
 *   Z_Extent_Ratio        REAL DEFAULT 0.0   fraction of parent height for Z extent
 *   IsActive              INTEGER DEFAULT 1
 *   MinQty                INTEGER NOT NULL DEFAULT 1  minimum required BOMs of this category
 *   MaxQty                INTEGER NOT NULL DEFAULT 1  maximum allowed BOMs of this category
 *   num_units             INTEGER NOT NULL DEFAULT 0  0=universal, 1=single-household, 2=dual
 *   storey_count          INTEGER NOT NULL DEFAULT 0  how many storeys this subtree spans
 *   mirroring_rule        TEXT NOT NULL DEFAULT 'NONE'  'NONE' or 'PARTY_WALL_PI'
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §3.8</a>
 */
public class X_MBomCategoryLine extends BasePO {

    public static final String Table_Name                          = "M_BomCategoryLine";
    public static final String COLUMNNAME_M_BomCategoryLine_ID     = "M_BomCategoryLine_ID";
    public static final String COLUMNNAME_M_BomCategory_ID         = "M_BomCategory_ID";
    public static final String COLUMNNAME_Child_BomCategory_ID     = "Child_BomCategory_ID";
    public static final String COLUMNNAME_Sequence                 = "Sequence";
    public static final String COLUMNNAME_Value                    = "Value";
    public static final String COLUMNNAME_Name                     = "Name";
    public static final String COLUMNNAME_Z_Offset_Ratio           = "Z_Offset_Ratio";
    public static final String COLUMNNAME_Z_Extent_Ratio           = "Z_Extent_Ratio";
    public static final String COLUMNNAME_IsActive                 = "IsActive";
    public static final String COLUMNNAME_MinQty                   = "MinQty";
    public static final String COLUMNNAME_MaxQty                   = "MaxQty";
    public static final String COLUMNNAME_num_units                = "num_units";
    public static final String COLUMNNAME_storey_count             = "storey_count";
    public static final String COLUMNNAME_mirroring_rule           = "mirroring_rule";

    public X_MBomCategoryLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_M_BomCategoryLine_ID; }

    public int     getLineId()             { return get_ValueAsInt(COLUMNNAME_M_BomCategoryLine_ID); }
    public String  getCategoryId()         { return get_ValueAsString(COLUMNNAME_M_BomCategory_ID); }
    public String  getChildCategoryId()    { return get_ValueAsString(COLUMNNAME_Child_BomCategory_ID); }
    public int     getSequence()           { return get_ValueAsInt(COLUMNNAME_Sequence); }
    public String  getValue()              { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getName()               { return get_ValueAsString(COLUMNNAME_Name); }
    public double  getZOffsetRatio()       { return get_ValueAsDouble(COLUMNNAME_Z_Offset_Ratio); }
    public double  getZExtentRatio()       { return get_ValueAsDouble(COLUMNNAME_Z_Extent_Ratio); }
    public boolean isActive()              { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public int     getMinQty()             { return get_ValueAsInt(COLUMNNAME_MinQty); }
    public int     getMaxQty()             { return get_ValueAsInt(COLUMNNAME_MaxQty); }
    public int     getNumUnits()           { return get_ValueAsInt(COLUMNNAME_num_units); }
    public int     getStoreyCount()        { return get_ValueAsInt(COLUMNNAME_storey_count); }
    public String  getMirroringRule()      { return get_ValueAsString(COLUMNNAME_mirroring_rule); }

    public void setLineId(int v)              { set_Value(COLUMNNAME_M_BomCategoryLine_ID, v); }
    public void setCategoryId(String v)       { set_Value(COLUMNNAME_M_BomCategory_ID, v); }
    public void setChildCategoryId(String v)  { set_Value(COLUMNNAME_Child_BomCategory_ID, v); }
    public void setSequence(int v)            { set_Value(COLUMNNAME_Sequence, v); }
    public void setValue(String v)            { set_Value(COLUMNNAME_Value, v); }
    public void setName(String v)             { set_Value(COLUMNNAME_Name, v); }
    public void setZOffsetRatio(double v)     { set_Value(COLUMNNAME_Z_Offset_Ratio, v); }
    public void setZExtentRatio(double v)     { set_Value(COLUMNNAME_Z_Extent_Ratio, v); }
    public void setIsActive(boolean v)        { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setMinQty(int v)              { set_Value(COLUMNNAME_MinQty, v); }
    public void setMaxQty(int v)              { set_Value(COLUMNNAME_MaxQty, v); }
    public void setNumUnits(int v)            { set_Value(COLUMNNAME_num_units, v); }
    public void setStoreyCount(int v)         { set_Value(COLUMNNAME_storey_count, v); }
    public void setMirroringRule(String v)    { set_Value(COLUMNNAME_mirroring_rule, v); }
}
