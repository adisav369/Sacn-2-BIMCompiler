package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code m_attribute} (iDempiere: M_Attribute / M_AttributeInstance).
 *
 * <p>Product-level attributes — intrinsic to the leaf item (Sink, Shower, Outlet).
 * Contains connection port definitions, UBBL clearance rules, and other properties
 * that AD_Val_Rule processes at compile time.
 *
 * <p>NOT placement offsets (those are {@code m_bom_line.dx/dy/dz}).
 *
 * <p>Table: {@code m_attribute}
 * <pre>
 *   param_id      INTEGER PRIMARY KEY AUTOINCREMENT
 *   M_BOM_Line_ID  INTEGER NOT NULL FK → m_bom_line
 *   param_key     TEXT NOT NULL     (port_type, clearance_front, ubbl_min_dim, ...)
 *   param_value   TEXT NOT NULL
 *   param_type    TEXT DEFAULT 'DOUBLE'
 *   unit          TEXT
 *   description   TEXT
 *   source_code   TEXT
 *   is_active     INTEGER DEFAULT 1
 *   UNIQUE(M_BOM_Line_ID, param_key)
 * </pre>
 */
public class X_M_Attribute extends BasePO {

    public static final String Table_Name                    = "m_attribute";
    public static final String COLUMNNAME_param_id           = "param_id";
    public static final String COLUMNNAME_M_BOM_Line_ID       = "M_BOM_Line_ID";
    public static final String COLUMNNAME_param_key          = "param_key";
    public static final String COLUMNNAME_param_value        = "param_value";
    public static final String COLUMNNAME_param_type         = "param_type";
    public static final String COLUMNNAME_unit               = "unit";
    public static final String COLUMNNAME_description        = "description";
    public static final String COLUMNNAME_source_code        = "source_code";
    public static final String COLUMNNAME_is_active          = "is_active";

    public X_M_Attribute(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_param_id; }

    public int    getParamId()      { return get_ValueAsInt(COLUMNNAME_param_id); }
    public int    getBomLineId()   { return get_ValueAsInt(COLUMNNAME_M_BOM_Line_ID); }
    public String getParamKey()     { return get_ValueAsString(COLUMNNAME_param_key); }
    public String getParamValue()   { return get_ValueAsString(COLUMNNAME_param_value); }
    public String getParamType()    { return get_ValueAsString(COLUMNNAME_param_type); }
    public String getUnit()         { return get_ValueAsString(COLUMNNAME_unit); }
    public String getDescription()  { return get_ValueAsString(COLUMNNAME_description); }
    public String getSourceCode()   { return get_ValueAsString(COLUMNNAME_source_code); }
    public boolean isActive()       { return get_ValueAsBoolean(COLUMNNAME_is_active); }

    public void setBomLineId(int v)    { set_Value(COLUMNNAME_M_BOM_Line_ID, v); }
    public void setParamKey(String v)   { set_Value(COLUMNNAME_param_key, v); }
    public void setParamValue(String v) { set_Value(COLUMNNAME_param_value, v); }
    public void setParamType(String v)  { set_Value(COLUMNNAME_param_type, v); }
    public void setUnit(String v)       { set_Value(COLUMNNAME_unit, v); }
    public void setDescription(String v){ set_Value(COLUMNNAME_description, v); }
    public void setSourceCode(String v) { set_Value(COLUMNNAME_source_code, v); }
    public void setIsActive(boolean v)  { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
}
