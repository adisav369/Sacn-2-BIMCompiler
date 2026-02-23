package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_product_dim}.
 *
 * <p>Catalog master — intrinsic geometry ONLY (width, depth, height in METERS).
 * Analogous to iDempiere {@code M_Product}.
 *
 * <p>THREE-TABLE AUTHORITY: NEVER add rotation or position here.
 *
 * <p>Table: {@code ad_product_dim}
 * <pre>
 *   product_id    TEXT PRIMARY KEY  ('DOOR_D1', 'UNIT_2BR_A', 'FIXTURE_TOILET')
 *   product_type  TEXT NOT NULL     (DOOR, WINDOW, UNIT, FIXTURE, FURNITURE)
 *   width         REAL NOT NULL     (X dimension — METERS, not mm)
 *   depth         REAL NOT NULL     (Y dimension — METERS)
 *   height        REAL NOT NULL     (Z dimension — METERS)
 *   clear_front   REAL DEFAULT 0
 *   clear_back    REAL DEFAULT 0
 *   clear_left    REAL DEFAULT 0
 *   clear_right   REAL DEFAULT 0
 *   clear_above   REAL DEFAULT 0
 *   clear_below   REAL DEFAULT 0
 *   fits_in       TEXT              (JSON: ["BEDROOM","BATHROOM"])
 *   requires_host TEXT              (WALL, CEILING, FLOOR, null)
 *   host_min_width  REAL
 *   host_min_height REAL
 *   qty_per_area  REAL
 *   qty_per_room  INTEGER
 *   qty_per_person REAL
 *   max_spacing   REAL
 *   conn_points   TEXT              (JSON connection points)
 *   code_ref      TEXT
 *   is_active     INTEGER DEFAULT 1
 *   extracted_from TEXT NOT NULL DEFAULT 'PENDING'
 * </pre>
 *
 * <p>TRAP: columns are {@code width}, {@code depth}, {@code height} in METERS —
 * NOT width_mm. Do NOT divide by 1000 in computeBomAnchor().
 */
public class X_AdProductDim extends BasePO {

    public static final String Table_Name                    = "ad_product_dim";
    public static final String COLUMNNAME_product_id         = "product_id";
    public static final String COLUMNNAME_product_type       = "product_type";
    public static final String COLUMNNAME_width              = "width";
    public static final String COLUMNNAME_depth              = "depth";
    public static final String COLUMNNAME_height             = "height";
    public static final String COLUMNNAME_clear_front        = "clear_front";
    public static final String COLUMNNAME_clear_back         = "clear_back";
    public static final String COLUMNNAME_clear_left         = "clear_left";
    public static final String COLUMNNAME_clear_right        = "clear_right";
    public static final String COLUMNNAME_clear_above        = "clear_above";
    public static final String COLUMNNAME_clear_below        = "clear_below";
    public static final String COLUMNNAME_fits_in            = "fits_in";
    public static final String COLUMNNAME_requires_host      = "requires_host";
    public static final String COLUMNNAME_host_min_width     = "host_min_width";
    public static final String COLUMNNAME_host_min_height    = "host_min_height";
    public static final String COLUMNNAME_qty_per_area       = "qty_per_area";
    public static final String COLUMNNAME_qty_per_room       = "qty_per_room";
    public static final String COLUMNNAME_qty_per_person     = "qty_per_person";
    public static final String COLUMNNAME_max_spacing        = "max_spacing";
    public static final String COLUMNNAME_conn_points        = "conn_points";
    public static final String COLUMNNAME_code_ref           = "code_ref";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_extracted_from     = "extracted_from";

    public X_AdProductDim(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_product_id; }

    public String  getProductId()      { return get_ValueAsString(COLUMNNAME_product_id); }
    public String  getProductType()    { return get_ValueAsString(COLUMNNAME_product_type); }
    public double  getWidth()          { return get_ValueAsDouble(COLUMNNAME_width); }
    public double  getDepth()          { return get_ValueAsDouble(COLUMNNAME_depth); }
    public double  getHeight()         { return get_ValueAsDouble(COLUMNNAME_height); }
    public double  getClearFront()     { return get_ValueAsDouble(COLUMNNAME_clear_front); }
    public double  getClearBack()      { return get_ValueAsDouble(COLUMNNAME_clear_back); }
    public double  getClearLeft()      { return get_ValueAsDouble(COLUMNNAME_clear_left); }
    public double  getClearRight()     { return get_ValueAsDouble(COLUMNNAME_clear_right); }
    public double  getClearAbove()     { return get_ValueAsDouble(COLUMNNAME_clear_above); }
    public double  getClearBelow()     { return get_ValueAsDouble(COLUMNNAME_clear_below); }
    public String  getFitsIn()         { return get_ValueAsString(COLUMNNAME_fits_in); }
    public String  getRequiresHost()   { return get_ValueAsString(COLUMNNAME_requires_host); }
    public double  getHostMinWidth()   { return get_ValueAsDouble(COLUMNNAME_host_min_width); }
    public double  getHostMinHeight()  { return get_ValueAsDouble(COLUMNNAME_host_min_height); }
    public double  getQtyPerArea()     { return get_ValueAsDouble(COLUMNNAME_qty_per_area); }
    public int     getQtyPerRoom()     { return get_ValueAsInt(COLUMNNAME_qty_per_room); }
    public double  getQtyPerPerson()   { return get_ValueAsDouble(COLUMNNAME_qty_per_person); }
    public double  getMaxSpacing()     { return get_ValueAsDouble(COLUMNNAME_max_spacing); }
    public String  getConnPoints()     { return get_ValueAsString(COLUMNNAME_conn_points); }
    public String  getCodeRef()        { return get_ValueAsString(COLUMNNAME_code_ref); }
    public boolean isActive()          { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public String  getExtractedFrom()  { return get_ValueAsString(COLUMNNAME_extracted_from); }

    public void setProductId(String v)     { set_Value(COLUMNNAME_product_id, v); }
    public void setProductType(String v)   { set_Value(COLUMNNAME_product_type, v); }
    public void setWidth(double v)         { set_Value(COLUMNNAME_width, v); }
    public void setDepth(double v)         { set_Value(COLUMNNAME_depth, v); }
    public void setHeight(double v)        { set_Value(COLUMNNAME_height, v); }
    public void setClearFront(double v)    { set_Value(COLUMNNAME_clear_front, v); }
    public void setClearBack(double v)     { set_Value(COLUMNNAME_clear_back, v); }
    public void setClearLeft(double v)     { set_Value(COLUMNNAME_clear_left, v); }
    public void setClearRight(double v)    { set_Value(COLUMNNAME_clear_right, v); }
    public void setClearAbove(double v)    { set_Value(COLUMNNAME_clear_above, v); }
    public void setClearBelow(double v)    { set_Value(COLUMNNAME_clear_below, v); }
    public void setFitsIn(String v)        { set_Value(COLUMNNAME_fits_in, v); }
    public void setRequiresHost(String v)  { set_Value(COLUMNNAME_requires_host, v); }
    public void setHostMinWidth(double v)  { set_Value(COLUMNNAME_host_min_width, v); }
    public void setHostMinHeight(double v) { set_Value(COLUMNNAME_host_min_height, v); }
    public void setQtyPerArea(double v)    { set_Value(COLUMNNAME_qty_per_area, v); }
    public void setQtyPerRoom(int v)       { set_Value(COLUMNNAME_qty_per_room, v); }
    public void setQtyPerPerson(double v)  { set_Value(COLUMNNAME_qty_per_person, v); }
    public void setMaxSpacing(double v)    { set_Value(COLUMNNAME_max_spacing, v); }
    public void setConnPoints(String v)    { set_Value(COLUMNNAME_conn_points, v); }
    public void setCodeRef(String v)       { set_Value(COLUMNNAME_code_ref, v); }
    public void setIsActive(boolean v)     { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setExtractedFrom(String v) { set_Value(COLUMNNAME_extracted_from, v); }
}
