package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_room_boundary}.
 *
 * <p>Room spatial footprints. G8 gate reads these for centroid calibration.
 *
 * <p>Table: {@code ad_room_boundary}
 * <pre>
 *   id               INTEGER PRIMARY KEY AUTOINCREMENT
 *   building_type    TEXT NOT NULL
 *   storey           TEXT NOT NULL
 *   room_name        TEXT NOT NULL
 *   room_type        TEXT NOT NULL
 *   grid_min_x       TEXT NOT NULL
 *   grid_max_x       TEXT NOT NULL
 *   grid_min_y       TEXT NOT NULL
 *   grid_max_y       TEXT NOT NULL
 *   z_offset_mm      REAL DEFAULT 0
 *   is_active        INTEGER DEFAULT 1
 *   min_x_mm         REAL
 *   max_x_mm         REAL
 *   min_y_mm         REAL
 *   max_y_mm         REAL
 *   building_id      INTEGER (FK)
 *   extracted_from   TEXT NOT NULL DEFAULT 'GRID_DERIVED'
 *   coordinate_frame TEXT NOT NULL DEFAULT 'LOCAL_MM'
 *   UNIQUE(building_type, storey, room_name)
 * </pre>
 *
 * <p>G8 calibration: min_x_mm/max_x_mm/min_y_mm/max_y_mm must match IFC global frame.
 * coordinate_frame = 'IFC_GLOBAL_MM' for calibrated rows, 'DERIVED_MM' for TopologyMaker rows.
 */
public class X_AdRoomBoundary extends BasePO {

    public static final String Table_Name                     = "ad_room_boundary";
    public static final String COLUMNNAME_id                  = "id";
    public static final String COLUMNNAME_building_type       = "building_type";
    public static final String COLUMNNAME_storey              = "storey";
    public static final String COLUMNNAME_room_name           = "room_name";
    public static final String COLUMNNAME_room_type           = "room_type";
    public static final String COLUMNNAME_grid_min_x          = "grid_min_x";
    public static final String COLUMNNAME_grid_max_x          = "grid_max_x";
    public static final String COLUMNNAME_grid_min_y          = "grid_min_y";
    public static final String COLUMNNAME_grid_max_y          = "grid_max_y";
    public static final String COLUMNNAME_z_offset_mm         = "z_offset_mm";
    public static final String COLUMNNAME_is_active           = "is_active";
    public static final String COLUMNNAME_min_x_mm            = "min_x_mm";
    public static final String COLUMNNAME_max_x_mm            = "max_x_mm";
    public static final String COLUMNNAME_min_y_mm            = "min_y_mm";
    public static final String COLUMNNAME_max_y_mm            = "max_y_mm";
    public static final String COLUMNNAME_building_id         = "building_id";
    public static final String COLUMNNAME_extracted_from      = "extracted_from";
    public static final String COLUMNNAME_coordinate_frame    = "coordinate_frame";

    public X_AdRoomBoundary(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_id; }

    public int    getId()              { return get_ValueAsInt(COLUMNNAME_id); }
    public String getBuildingType()    { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getStorey()          { return get_ValueAsString(COLUMNNAME_storey); }
    public String getRoomName()        { return get_ValueAsString(COLUMNNAME_room_name); }
    public String getRoomType()        { return get_ValueAsString(COLUMNNAME_room_type); }
    public String getGridMinX()        { return get_ValueAsString(COLUMNNAME_grid_min_x); }
    public String getGridMaxX()        { return get_ValueAsString(COLUMNNAME_grid_max_x); }
    public String getGridMinY()        { return get_ValueAsString(COLUMNNAME_grid_min_y); }
    public String getGridMaxY()        { return get_ValueAsString(COLUMNNAME_grid_max_y); }
    public double getZOffsetMm()       { return get_ValueAsDouble(COLUMNNAME_z_offset_mm); }
    public boolean isActive()          { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public double getMinXMm()          { return get_ValueAsDouble(COLUMNNAME_min_x_mm); }
    public double getMaxXMm()          { return get_ValueAsDouble(COLUMNNAME_max_x_mm); }
    public double getMinYMm()          { return get_ValueAsDouble(COLUMNNAME_min_y_mm); }
    public double getMaxYMm()          { return get_ValueAsDouble(COLUMNNAME_max_y_mm); }
    public int    getBuildingId()      { return get_ValueAsInt(COLUMNNAME_building_id); }
    public String getExtractedFrom()   { return get_ValueAsString(COLUMNNAME_extracted_from); }
    public String getCoordinateFrame() { return get_ValueAsString(COLUMNNAME_coordinate_frame); }

    public void setBuildingType(String v)    { set_Value(COLUMNNAME_building_type, v); }
    public void setStorey(String v)          { set_Value(COLUMNNAME_storey, v); }
    public void setRoomName(String v)        { set_Value(COLUMNNAME_room_name, v); }
    public void setRoomType(String v)        { set_Value(COLUMNNAME_room_type, v); }
    public void setGridMinX(String v)        { set_Value(COLUMNNAME_grid_min_x, v); }
    public void setGridMaxX(String v)        { set_Value(COLUMNNAME_grid_max_x, v); }
    public void setGridMinY(String v)        { set_Value(COLUMNNAME_grid_min_y, v); }
    public void setGridMaxY(String v)        { set_Value(COLUMNNAME_grid_max_y, v); }
    public void setZOffsetMm(double v)       { set_Value(COLUMNNAME_z_offset_mm, v); }
    public void setIsActive(boolean v)       { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setMinXMm(double v)          { set_Value(COLUMNNAME_min_x_mm, v); }
    public void setMaxXMm(double v)          { set_Value(COLUMNNAME_max_x_mm, v); }
    public void setMinYMm(double v)          { set_Value(COLUMNNAME_min_y_mm, v); }
    public void setMaxYMm(double v)          { set_Value(COLUMNNAME_max_y_mm, v); }
    public void setBuildingId(int v)         { set_Value(COLUMNNAME_building_id, v); }
    public void setExtractedFrom(String v)   { set_Value(COLUMNNAME_extracted_from, v); }
    public void setCoordinateFrame(String v) { set_Value(COLUMNNAME_coordinate_frame, v); }

    /** Centroid X in mm — used by G8 calibration gate. */
    public double centroidXMm() { return (getMinXMm() + getMaxXMm()) / 2.0; }

    /** Centroid Y in mm — used by G8 calibration gate. */
    public double centroidYMm() { return (getMinYMm() + getMaxYMm()) / 2.0; }

    /** Floor area in mm². */
    public double areaMm2() { return (getMaxXMm() - getMinXMm()) * (getMaxYMm() - getMinYMm()); }
}
