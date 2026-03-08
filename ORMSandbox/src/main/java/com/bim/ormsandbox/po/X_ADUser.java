package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code AD_User} (iDempiere: AD_User).
 *
 * <p>Sales representative / project handler. Links compilations to
 * responsible persons for ERP integration.
 *
 * <p>Table: {@code AD_User}
 * <pre>
 *   AD_User_ID      INTEGER PRIMARY KEY
 *   Name            TEXT NOT NULL
 *   Value           TEXT NOT NULL UNIQUE  search key
 *   Email           TEXT
 *   IsActive        INTEGER DEFAULT 1
 *   entity_type     TEXT DEFAULT 'D'
 * </pre>
 *
 * @see <a href="docs/ACTION_ROADMAP.md">ACTION_ROADMAP — Phase H0</a>
 */
public class X_ADUser extends BasePO {

    public static final String Table_Name                = "AD_User";
    public static final String COLUMNNAME_AD_User_ID     = "AD_User_ID";
    public static final String COLUMNNAME_Name           = "Name";
    public static final String COLUMNNAME_Value          = "Value";
    public static final String COLUMNNAME_Email          = "Email";
    public static final String COLUMNNAME_IsActive       = "IsActive";
    public static final String COLUMNNAME_entity_type    = "entity_type";

    public X_ADUser(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_AD_User_ID; }

    public int     getADUserId()    { return get_ValueAsInt(COLUMNNAME_AD_User_ID); }
    public String  getName()        { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getValue()       { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getEmail()       { return get_ValueAsString(COLUMNNAME_Email); }
    public boolean isActive()       { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String  getEntityType()  { return get_ValueAsString(COLUMNNAME_entity_type); }

    public void setADUserId(int v)       { set_Value(COLUMNNAME_AD_User_ID, v); }
    public void setName(String v)        { set_Value(COLUMNNAME_Name, v); }
    public void setValue(String v)       { set_Value(COLUMNNAME_Value, v); }
    public void setEmail(String v)       { set_Value(COLUMNNAME_Email, v); }
    public void setIsActive(boolean v)   { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setEntityType(String v)  { set_Value(COLUMNNAME_entity_type, v); }
}
