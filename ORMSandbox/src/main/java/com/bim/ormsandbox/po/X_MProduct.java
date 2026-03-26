package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code M_Product}.
 *
 * <p>iDempiere M_Product analogue — catalog master for both leaf products (BUY)
 * and assembly BOMs (MAKE). Intrinsic geometry in METERS for BUY products;
 * dims=0.001 sentinel for MAKE/assembly stubs (spatial dims come from M_BOM_Line dx/dy/dz).
 *
 * <p>THREE-TABLE AUTHORITY: NEVER add rotation or position here.
 *
 * <p>Table: {@code M_Product}
 * <pre>
 *   product_id    TEXT PRIMARY KEY  ('DOOR_D1', 'FLOOR_SH_GF_STD', 'FIXTURE_TOILET')
 *   product_type  TEXT NOT NULL     (DOOR, WINDOW, UNIT, FIXTURE, FURNITURE, FLOOR, SET, …)
 *   width         REAL NOT NULL     (X dimension — METERS, not mm)
 *   depth         REAL NOT NULL     (Y dimension — METERS)
 *   height        REAL NOT NULL     (Z dimension — METERS)
 *   clear_front … clear_below REAL  (clearance rules, BUY only)
 *   fits_in       TEXT              (JSON: ["BEDROOM","BATHROOM"])
 *   requires_host TEXT              (WALL, CEILING, FLOOR, null)
 *   component_id  INTEGER           (logical FK → component_library.component_definitions)
 *   bom_id        TEXT              (FK → m_bom.bom_id — populated for MAKE/assembly rows)
 *   ifc_class     TEXT              (IFC element type, e.g. IfcDoor — NORM-2)
 *   is_active     INTEGER DEFAULT 1 (0 = assembly stub, not a placeable catalog item)
 *   extracted_from TEXT NOT NULL DEFAULT 'PENDING'
 * </pre>
 *
 * <p>TRAP: columns are {@code width}, {@code depth}, {@code height} in METERS —
 * NOT width_mm. Do NOT divide by 1000 in computeBomAnchor().
 */
public class X_MProduct extends BasePO {

    public static final String Table_Name                    = "M_Product";
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
    public static final String COLUMNNAME_material_name      = "material_name";
    public static final String COLUMNNAME_material_rgba      = "material_rgba";
    public static final String COLUMNNAME_component_id       = "component_id";
    public static final String COLUMNNAME_bom_id             = "bom_id";
    public static final String COLUMNNAME_ifc_class          = "ifc_class";

    // ── Tier 2: INTEGER PK + iDempiere triad (Phase E) ─────────────────────
    public static final String COLUMNNAME_M_Product_ID      = "M_Product_ID";
    public static final String COLUMNNAME_Value             = "Value";
    public static final String COLUMNNAME_Name_IDV          = "Name";

    public X_MProduct(Connection conn) { super(conn); }

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
    public String  getMaterialName()   { return get_ValueAsString(COLUMNNAME_material_name); }
    public String  getMaterialRgba()   { return get_ValueAsString(COLUMNNAME_material_rgba); }
    public int     getComponentId()    { return get_ValueAsInt(COLUMNNAME_component_id); }
    public String  getBomId()          { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String  getIfcClass()       { return get_ValueAsString(COLUMNNAME_ifc_class); }

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
    public void setMaterialName(String v)  { set_Value(COLUMNNAME_material_name, v); }
    public void setMaterialRgba(String v)  { set_Value(COLUMNNAME_material_rgba, v); }
    public void setComponentId(int v)      { set_Value(COLUMNNAME_component_id, v); }
    public void setBomId(String v)         { set_Value(COLUMNNAME_bom_id, v); }
    public void setIfcClass(String v)      { set_Value(COLUMNNAME_ifc_class, v); }

    // ── Tier 2: INTEGER PK accessors (Phase E) ──────────────────────────────
    /** M_Product_ID integer PK — AUTOINCREMENT surrogate (backfilled by IFCtoBOM). */
    public int     getMProductId()         { return get_ValueAsInt(COLUMNNAME_M_Product_ID); }
    public String  getValue()              { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getNameIDV()            { return get_ValueAsString(COLUMNNAME_Name_IDV); }
    public void    setMProductId(int v)    { set_Value(COLUMNNAME_M_Product_ID, v); }
    public void    setValue(String v)       { set_Value(COLUMNNAME_Value, v); }
    public void    setNameIDV(String v)    { set_Value(COLUMNNAME_Name_IDV, v); }
}
