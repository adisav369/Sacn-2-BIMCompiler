package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_opening_family}.
 *
 * <p>Door and window family catalog — default dimensions, fire rating, IFC class.
 * {@code depth_mm} is nullable (NULL → caller uses 100mm default).
 *
 * <p>Table: {@code ad_opening_family}
 * <pre>
 *   family_id         TEXT PRIMARY KEY
 *   family_name       TEXT NOT NULL
 *   opening_type      TEXT NOT NULL     -- 'DOOR' or 'WINDOW'
 *   ifc_class         TEXT NOT NULL     -- 'IfcDoor' or 'IfcWindow'
 *   default_width_mm  INTEGER NOT NULL
 *   default_height_mm INTEGER NOT NULL
 *   is_fire_rated     INTEGER DEFAULT 0
 *   description       TEXT
 *   is_active         INTEGER DEFAULT 1
 *   depth_mm          INTEGER DEFAULT NULL
 * </pre>
 *
 * <p>Consumers: OpeningBomAD, CatalogValidator, MetadataValidator (dim check).
 */
public class X_AdOpeningFamily extends BasePO {

    public static final String Table_Name                    = "ad_opening_family";
    public static final String COLUMNNAME_family_id          = "family_id";
    public static final String COLUMNNAME_family_name        = "family_name";
    public static final String COLUMNNAME_opening_type       = "opening_type";
    public static final String COLUMNNAME_ifc_class          = "ifc_class";
    public static final String COLUMNNAME_default_width_mm   = "default_width_mm";
    public static final String COLUMNNAME_default_height_mm  = "default_height_mm";
    public static final String COLUMNNAME_is_fire_rated      = "is_fire_rated";
    public static final String COLUMNNAME_description        = "description";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_depth_mm           = "depth_mm";

    public X_AdOpeningFamily(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_family_id; }

    public String  getFamilyId()         { return get_ValueAsString(COLUMNNAME_family_id); }
    public String  getFamilyName()       { return get_ValueAsString(COLUMNNAME_family_name); }
    public String  getOpeningType()      { return get_ValueAsString(COLUMNNAME_opening_type); }
    public String  getIfcClass()         { return get_ValueAsString(COLUMNNAME_ifc_class); }
    public int     getDefaultWidthMm()   { return get_ValueAsInt(COLUMNNAME_default_width_mm); }
    public int     getDefaultHeightMm()  { return get_ValueAsInt(COLUMNNAME_default_height_mm); }
    public boolean isFireRated()         { return get_ValueAsBoolean(COLUMNNAME_is_fire_rated); }
    public String  getDescription()      { return get_ValueAsString(COLUMNNAME_description); }
    public boolean isActive()            { return get_ValueAsBoolean(COLUMNNAME_is_active); }

    /**
     * depth_mm is nullable. Returns raw int (0 if NULL — use {@link #getDepthMmWithDefault()} for safe access).
     */
    public int getDepthMm()              { return get_ValueAsInt(COLUMNNAME_depth_mm); }

    public void setFamilyId(String v)        { set_Value(COLUMNNAME_family_id, v); }
    public void setFamilyName(String v)      { set_Value(COLUMNNAME_family_name, v); }
    public void setOpeningType(String v)     { set_Value(COLUMNNAME_opening_type, v); }
    public void setIfcClass(String v)        { set_Value(COLUMNNAME_ifc_class, v); }
    public void setDefaultWidthMm(int v)     { set_Value(COLUMNNAME_default_width_mm, v); }
    public void setDefaultHeightMm(int v)    { set_Value(COLUMNNAME_default_height_mm, v); }
    public void setIsFireRated(boolean v)    { set_Value(COLUMNNAME_is_fire_rated, v ? 1 : 0); }
    public void setDescription(String v)     { set_Value(COLUMNNAME_description, v); }
    public void setIsActive(boolean v)       { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setDepthMm(Integer v)        { set_Value(COLUMNNAME_depth_mm, v); }
}
