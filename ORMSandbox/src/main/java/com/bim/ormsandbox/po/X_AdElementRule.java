package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_element_rule}.
 *
 * <p>Primary reader: {@code RelationalResolver.loadRules()} in DAGCompiler.
 * Each row describes one element placement for a given building + storey.
 *
 * <p>Table: {@code ad_element_rule}
 * <pre>
 *   id               INTEGER PRIMARY KEY AUTOINCREMENT
 *   building_type    TEXT NOT NULL
 *   storey           TEXT NOT NULL
 *   element_ref      TEXT NOT NULL  (unique element name within building+storey)
 *   ifc_class        TEXT NOT NULL  ('IfcDoor', 'IfcWindow', ...)
 *   discipline       TEXT DEFAULT 'ARC'
 *   host_type        TEXT NOT NULL  ('WALL','ROOM','GRID','BUILDING','SLAB')
 *   host_ref         TEXT NOT NULL  (wall face key, room name, grid label, etc.)
 *   position_rule    TEXT NOT NULL  ('CENTER','FRACTION','OFFSET','GRID_INTERSECTION',...)
 *   position_value   REAL           (fraction 0-1, offset mm, spacing mm)
 *   position_value_2 REAL
 *   position_value_3 REAL DEFAULT 0.0
 *   height_mm        REAL           (mounting height above host floor)
 *   family_ref       TEXT           (→ ad_opening_family or component name)
 *   width_mm         REAL
 *   height_extent_mm REAL           (element height in mm — must be set or dz=0)
 *   depth_mm         REAL
 *   orientation      TEXT           ('ALONG_HOST','PERPENDICULAR','NS','EW')
 *   geometry_hash    TEXT
 *   material_name    TEXT
 *   material_rgba    TEXT
 *   is_active        INTEGER DEFAULT 1
 *   building_id      INTEGER REFERENCES ad_building(id)
 *   UNIQUE(building_type, storey, element_ref)
 * </pre>
 */
public class X_AdElementRule extends BasePO {

    public static final String Table_Name                    = "ad_element_rule";
    public static final String COLUMNNAME_id                 = "id";
    public static final String COLUMNNAME_building_type      = "building_type";
    public static final String COLUMNNAME_storey             = "storey";
    public static final String COLUMNNAME_element_ref        = "element_ref";
    public static final String COLUMNNAME_ifc_class          = "ifc_class";
    public static final String COLUMNNAME_discipline         = "discipline";
    public static final String COLUMNNAME_host_type          = "host_type";
    public static final String COLUMNNAME_host_ref           = "host_ref";
    public static final String COLUMNNAME_position_rule      = "position_rule";
    public static final String COLUMNNAME_position_value     = "position_value";
    public static final String COLUMNNAME_position_value_2   = "position_value_2";
    public static final String COLUMNNAME_position_value_3   = "position_value_3";
    public static final String COLUMNNAME_height_mm          = "height_mm";
    public static final String COLUMNNAME_family_ref         = "family_ref";
    public static final String COLUMNNAME_width_mm           = "width_mm";
    public static final String COLUMNNAME_height_extent_mm   = "height_extent_mm";
    public static final String COLUMNNAME_depth_mm           = "depth_mm";
    public static final String COLUMNNAME_orientation        = "orientation";
    public static final String COLUMNNAME_geometry_hash      = "geometry_hash";
    public static final String COLUMNNAME_material_name      = "material_name";
    public static final String COLUMNNAME_material_rgba      = "material_rgba";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_building_id        = "building_id";

    public X_AdElementRule(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_id; }

    public int    getId()               { return get_ValueAsInt(COLUMNNAME_id); }
    public String getBuildingType()     { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getStorey()           { return get_ValueAsString(COLUMNNAME_storey); }
    public String getElementRef()       { return get_ValueAsString(COLUMNNAME_element_ref); }
    public String getIfcClass()         { return get_ValueAsString(COLUMNNAME_ifc_class); }
    public String getDiscipline()       { return get_ValueAsString(COLUMNNAME_discipline); }
    public String getHostType()         { return get_ValueAsString(COLUMNNAME_host_type); }
    public String getHostRef()          { return get_ValueAsString(COLUMNNAME_host_ref); }
    public String getPositionRule()     { return get_ValueAsString(COLUMNNAME_position_rule); }
    public double getPositionValue()    { return get_ValueAsDouble(COLUMNNAME_position_value); }
    public double getPositionValue2()   { return get_ValueAsDouble(COLUMNNAME_position_value_2); }
    public double getPositionValue3()   { return get_ValueAsDouble(COLUMNNAME_position_value_3); }
    public double getHeightMm()         { return get_ValueAsDouble(COLUMNNAME_height_mm); }
    public String getFamilyRef()        { return get_ValueAsString(COLUMNNAME_family_ref); }
    public double getWidthMm()          { return get_ValueAsDouble(COLUMNNAME_width_mm); }
    public double getHeightExtentMm()   { return get_ValueAsDouble(COLUMNNAME_height_extent_mm); }
    public double getDepthMm()          { return get_ValueAsDouble(COLUMNNAME_depth_mm); }
    public String getOrientation()      { return get_ValueAsString(COLUMNNAME_orientation); }
    public String getGeometryHash()     { return get_ValueAsString(COLUMNNAME_geometry_hash); }
    public String getMaterialName()     { return get_ValueAsString(COLUMNNAME_material_name); }
    public String getMaterialRgba()     { return get_ValueAsString(COLUMNNAME_material_rgba); }
    public boolean isActive()           { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public int    getBuildingId()       { return get_ValueAsInt(COLUMNNAME_building_id); }

    public void setBuildingType(String v)    { set_Value(COLUMNNAME_building_type, v); }
    public void setStorey(String v)          { set_Value(COLUMNNAME_storey, v); }
    public void setElementRef(String v)      { set_Value(COLUMNNAME_element_ref, v); }
    public void setIfcClass(String v)        { set_Value(COLUMNNAME_ifc_class, v); }
    public void setDiscipline(String v)      { set_Value(COLUMNNAME_discipline, v); }
    public void setHostType(String v)        { set_Value(COLUMNNAME_host_type, v); }
    public void setHostRef(String v)         { set_Value(COLUMNNAME_host_ref, v); }
    public void setPositionRule(String v)    { set_Value(COLUMNNAME_position_rule, v); }
    public void setPositionValue(double v)   { set_Value(COLUMNNAME_position_value, v); }
    public void setPositionValue2(double v)  { set_Value(COLUMNNAME_position_value_2, v); }
    public void setPositionValue3(double v)  { set_Value(COLUMNNAME_position_value_3, v); }
    public void setHeightMm(double v)        { set_Value(COLUMNNAME_height_mm, v); }
    public void setFamilyRef(String v)       { set_Value(COLUMNNAME_family_ref, v); }
    public void setWidthMm(double v)         { set_Value(COLUMNNAME_width_mm, v); }
    public void setHeightExtentMm(double v)  { set_Value(COLUMNNAME_height_extent_mm, v); }
    public void setDepthMm(double v)         { set_Value(COLUMNNAME_depth_mm, v); }
    public void setOrientation(String v)     { set_Value(COLUMNNAME_orientation, v); }
    public void setGeometryHash(String v)    { set_Value(COLUMNNAME_geometry_hash, v); }
    public void setMaterialName(String v)    { set_Value(COLUMNNAME_material_name, v); }
    public void setMaterialRgba(String v)    { set_Value(COLUMNNAME_material_rgba, v); }
    public void setIsActive(boolean v)       { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setBuildingId(int v)         { set_Value(COLUMNNAME_building_id, v); }
}
