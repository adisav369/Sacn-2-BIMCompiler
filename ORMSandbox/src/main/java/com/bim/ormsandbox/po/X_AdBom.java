package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_bom}.
 *
 * <p>Table: {@code ad_bom}
 * <pre>
 *   bom_id           TEXT PRIMARY KEY
 *   bom_name         TEXT NOT NULL
 *   description      TEXT
 *   target_ifc_class TEXT DEFAULT 'IfcElementAssembly'
 *   group_by         TEXT NOT NULL   (ROOM|BUILDING — NOT NULL, always set)
 *   is_active        INTEGER DEFAULT 1
 *   bom_level        TEXT DEFAULT 'SET'
 *   bom_type         TEXT NOT NULL CHECK(UNIT|FLOOR|ROOM|SET|ITEM)
 * </pre>
 *
 * <p>TRAP: {@code group_by} is NOT NULL — always set ('ROOM' for room mods, 'BUILDING' for wall/floor/unit)
 */
public class X_AdBom extends BasePO {

    public static final String Table_Name                    = "ad_bom";
    public static final String COLUMNNAME_bom_id             = "bom_id";
    public static final String COLUMNNAME_bom_name           = "bom_name";
    public static final String COLUMNNAME_description        = "description";
    public static final String COLUMNNAME_target_ifc_class   = "target_ifc_class";
    public static final String COLUMNNAME_group_by           = "group_by";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_bom_level          = "bom_level";
    public static final String COLUMNNAME_bom_type           = "bom_type";

    public X_AdBom(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_bom_id; }

    public String  getBomId()           { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String  getBomName()         { return get_ValueAsString(COLUMNNAME_bom_name); }
    public String  getDescription()     { return get_ValueAsString(COLUMNNAME_description); }
    public String  getTargetIfcClass()  { return get_ValueAsString(COLUMNNAME_target_ifc_class); }
    public String  getGroupBy()         { return get_ValueAsString(COLUMNNAME_group_by); }
    public boolean isActive()           { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public String  getBomLevel()        { return get_ValueAsString(COLUMNNAME_bom_level); }
    public String  getBomType()         { return get_ValueAsString(COLUMNNAME_bom_type); }

    public void setBomId(String v)          { set_Value(COLUMNNAME_bom_id, v); }
    public void setBomName(String v)        { set_Value(COLUMNNAME_bom_name, v); }
    public void setDescription(String v)    { set_Value(COLUMNNAME_description, v); }
    public void setTargetIfcClass(String v) { set_Value(COLUMNNAME_target_ifc_class, v); }
    public void setGroupBy(String v)        { set_Value(COLUMNNAME_group_by, v); }
    public void setIsActive(boolean v)      { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setBomLevel(String v)       { set_Value(COLUMNNAME_bom_level, v); }
    public void setBomType(String v)        { set_Value(COLUMNNAME_bom_type, v); }
}
