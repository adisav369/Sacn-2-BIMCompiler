package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_building_grid}.
 *
 * <p>Structural grid axes for relational placement. X-axis labels are letters (A, B, C...),
 * Y-axis labels are numbers (1, 2, 3...). position_mm is absolute from building origin.
 *
 * <p>Table: {@code ad_building_grid}
 * <pre>
 *   id            INTEGER PRIMARY KEY AUTOINCREMENT
 *   building_type TEXT NOT NULL
 *   axis          TEXT NOT NULL        -- 'X' or 'Y'
 *   grid_label    TEXT NOT NULL        -- 'A', 'B', '1', '2' etc.
 *   position_mm   REAL NOT NULL        -- absolute position in mm from building origin
 *   is_active     INTEGER DEFAULT 1
 *   building_id   INTEGER REFERENCES ad_building(id)
 *   UNIQUE(building_type, axis, grid_label)
 * </pre>
 *
 * <p>Consumers: RelationalResolver, PlacementProver, MetadataValidator.
 */
public class X_AdBuildingGrid extends BasePO {

    public static final String Table_Name              = "ad_building_grid";
    public static final String COLUMNNAME_id           = "id";
    public static final String COLUMNNAME_building_type = "building_type";
    public static final String COLUMNNAME_axis         = "axis";
    public static final String COLUMNNAME_grid_label   = "grid_label";
    public static final String COLUMNNAME_position_mm  = "position_mm";
    public static final String COLUMNNAME_is_active    = "is_active";
    public static final String COLUMNNAME_building_id  = "building_id";

    public X_AdBuildingGrid(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_id; }

    public int    getId()           { return get_ValueAsInt(COLUMNNAME_id); }
    public String getBuildingType() { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getAxis()         { return get_ValueAsString(COLUMNNAME_axis); }
    public String getGridLabel()    { return get_ValueAsString(COLUMNNAME_grid_label); }
    public double getPositionMm()   { return get_ValueAsDouble(COLUMNNAME_position_mm); }
    public boolean isActive()       { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public int    getBuildingId()   { return get_ValueAsInt(COLUMNNAME_building_id); }

    public void setBuildingType(String v)  { set_Value(COLUMNNAME_building_type, v); }
    public void setAxis(String v)          { set_Value(COLUMNNAME_axis, v); }
    public void setGridLabel(String v)     { set_Value(COLUMNNAME_grid_label, v); }
    public void setPositionMm(double v)    { set_Value(COLUMNNAME_position_mm, v); }
    public void setIsActive(boolean v)     { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setBuildingId(int v)       { set_Value(COLUMNNAME_building_id, v); }
}
