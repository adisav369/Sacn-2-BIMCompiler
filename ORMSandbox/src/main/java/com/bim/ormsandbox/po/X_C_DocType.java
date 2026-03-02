package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code C_DocType} (iDempiere: C_DocType).
 *
 * <p>Document type classification for construction orders. Borrowed from
 * iDempiere's C_DocType model: DocBaseType (3-char category) + DocSubType
 * (variant within category).
 *
 * <p>Replaces the dual c_order.building_type + c_order.c_bpartner with a
 * single FK. DocBaseType drives template selection (RE → Residential template).
 * DocSubType drives BOM scoping (SH BOMs for SH buildings).
 *
 * <p>Table: {@code C_DocType}
 * <pre>
 *   C_DocType_ID   TEXT PRIMARY KEY     composite: DocBaseType + '_' + DocSubType
 *   Name           TEXT NOT NULL         human-readable ('Sample House', 'Duplex')
 *   DocBaseType    TEXT NOT NULL          RE (Residential), CO (Commercial), IN (Industrial)
 *   DocSubType     TEXT                   SH, DX, TB, TE, ST (NULL = generic)
 *   IsDefault      INTEGER DEFAULT 0     default DocType for this DocBaseType
 *   IsActive       INTEGER DEFAULT 1
 *   Description    TEXT
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §11.36</a>
 */
public class X_C_DocType extends BasePO {

    public static final String Table_Name                    = "C_DocType";
    public static final String COLUMNNAME_C_DocType_ID       = "C_DocType_ID";
    public static final String COLUMNNAME_Name               = "Name";
    public static final String COLUMNNAME_DocBaseType        = "DocBaseType";
    public static final String COLUMNNAME_DocSubType         = "DocSubType";
    public static final String COLUMNNAME_IsDefault          = "IsDefault";
    public static final String COLUMNNAME_IsActive           = "IsActive";
    public static final String COLUMNNAME_Description        = "Description";

    public X_C_DocType(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_DocType_ID; }

    public String  getDocTypeId()    { return get_ValueAsString(COLUMNNAME_C_DocType_ID); }
    public String  getName()         { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDocBaseType()  { return get_ValueAsString(COLUMNNAME_DocBaseType); }
    public String  getDocSubType()   { return get_ValueAsString(COLUMNNAME_DocSubType); }
    public boolean isDefault()       { return get_ValueAsBoolean(COLUMNNAME_IsDefault); }
    public boolean isActive()        { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String  getDescription()  { return get_ValueAsString(COLUMNNAME_Description); }

    public void setDocTypeId(String v)   { set_Value(COLUMNNAME_C_DocType_ID, v); }
    public void setName(String v)        { set_Value(COLUMNNAME_Name, v); }
    public void setDocBaseType(String v) { set_Value(COLUMNNAME_DocBaseType, v); }
    public void setDocSubType(String v)  { set_Value(COLUMNNAME_DocSubType, v); }
    public void setIsDefault(boolean v)  { set_Value(COLUMNNAME_IsDefault, v ? 1 : 0); }
    public void setIsActive(boolean v)   { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setDescription(String v) { set_Value(COLUMNNAME_Description, v); }
}
