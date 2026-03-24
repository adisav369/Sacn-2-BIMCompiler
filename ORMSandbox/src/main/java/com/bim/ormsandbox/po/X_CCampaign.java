package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code C_Campaign} (iDempiere: C_Campaign).
 *
 * <p>Design theme classification. Determines the material palette and
 * aesthetic language applied to a building compilation. Orthogonal to
 * M_Product_Category (RE/CO/IN) and C_BPartner (scope owner).
 *
 * <p>Table: {@code C_Campaign}
 * <pre>
 *   C_Campaign_ID   TEXT PRIMARY KEY  (SCAN, TROP, INST, INDU)
 *   Value           TEXT NOT NULL UNIQUE  search key
 *   Name            TEXT NOT NULL         human-readable
 *   Description     TEXT
 *   IsActive        INTEGER DEFAULT 1
 *   entity_type     TEXT DEFAULT 'D'
 * </pre>
 *
 * @see <a href="docs/ACTION_ROADMAP.md">ACTION_ROADMAP — Phase H0</a>
 */
public class X_CCampaign extends BasePO {

    public static final String Table_Name                  = "C_Campaign";
    public static final String COLUMNNAME_C_Campaign_ID    = "C_Campaign_ID";
    public static final String COLUMNNAME_Value            = "Value";
    public static final String COLUMNNAME_Name             = "Name";
    public static final String COLUMNNAME_Description      = "Description";
    public static final String COLUMNNAME_IsActive         = "IsActive";
    public static final String COLUMNNAME_entity_type      = "entity_type";

    public X_CCampaign(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_Campaign_ID; }

    public String  getCCampaignId() { return get_ValueAsString(COLUMNNAME_C_Campaign_ID); }
    public String  getValue()      { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getName()       { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDescription() { return get_ValueAsString(COLUMNNAME_Description); }
    public boolean isActive()      { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String  getEntityType() { return get_ValueAsString(COLUMNNAME_entity_type); }

    public void setCCampaignId(String v) { set_Value(COLUMNNAME_C_Campaign_ID, v); }
    public void setValue(String v)       { set_Value(COLUMNNAME_Value, v); }
    public void setName(String v)        { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v) { set_Value(COLUMNNAME_Description, v); }
    public void setIsActive(boolean v)   { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setEntityType(String v)  { set_Value(COLUMNNAME_entity_type, v); }
}
