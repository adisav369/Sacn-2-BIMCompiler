package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code C_BPartner} (iDempiere: C_BPartner).
 *
 * <p>Lookup table for building pattern owners / compilation scopes.
 * Determines WHO the BOM tree belongs to. Orthogonal to M_BomCategory (WHAT)
 * and SpaceSize (HOW MUCH).
 *
 * <p>Table: {@code C_BPartner}
 * <pre>
 *   C_BPartner_ID   TEXT PRIMARY KEY  (SH, DX, TB, TE, ST)
 *   Value            TEXT NOT NULL UNIQUE  CamelCase search key ('SampleHouse')
 *   Name             TEXT NOT NULL         human-readable ('Sample House')
 *   Description      TEXT
 *   IsActive         INTEGER DEFAULT 1
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §3.8</a>
 */
public class X_CBPartner extends BasePO {

    public static final String Table_Name                  = "C_BPartner";
    public static final String COLUMNNAME_C_BPartner_ID    = "C_BPartner_ID";
    public static final String COLUMNNAME_Value            = "Value";
    public static final String COLUMNNAME_Name             = "Name";
    public static final String COLUMNNAME_Description      = "Description";
    public static final String COLUMNNAME_IsActive         = "IsActive";

    public X_CBPartner(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_BPartner_ID; }

    public String  getCBPartnerId() { return get_ValueAsString(COLUMNNAME_C_BPartner_ID); }
    public String  getValue()       { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getName()        { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDescription() { return get_ValueAsString(COLUMNNAME_Description); }
    public boolean isActive()       { return get_ValueAsBoolean(COLUMNNAME_IsActive); }

    public void setCBPartnerId(String v) { set_Value(COLUMNNAME_C_BPartner_ID, v); }
    public void setValue(String v)       { set_Value(COLUMNNAME_Value, v); }
    public void setName(String v)        { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v) { set_Value(COLUMNNAME_Description, v); }
    public void setIsActive(boolean v)   { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
}
