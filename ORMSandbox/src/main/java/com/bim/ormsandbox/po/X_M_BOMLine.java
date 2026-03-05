package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code m_bom_line} (iDempiere: M_BOM_Line).
 *
 * <p>One row per child in a BOM assembly. AllocatedSize (AABB) lives here —
 * full 3D bounding box for every child including buffers.
 *
 * <p>Table: {@code m_bom_line}
 * <pre>
 *   bom_child_id        INTEGER PRIMARY KEY AUTOINCREMENT
 *   bom_id              TEXT NOT NULL FK → m_bom
 *   child_product_id    TEXT FK → M_Product  (NORM-2: replaces child_bom_id + product_ref + child_ifc_class)
 *   child_element_type  TEXT
 *   child_name_pattern  TEXT                 (LIKE pattern)
 *   role                TEXT NOT NULL
 *   qty_type            TEXT DEFAULT 'VARIABLE'
 *   sequence            INTEGER DEFAULT 100
 *   is_active           INTEGER DEFAULT 1
 *   z_rule              TEXT
 *   dx                  REAL DEFAULT 0.0     (assembly-relative, METRES)
 *   dy                  REAL DEFAULT 0.0
 *   dz                  REAL DEFAULT 0.0
 *   rotation_rule       TEXT DEFAULT '0'
 *   fit_priority        INTEGER DEFAULT 20
 *   min_space_mm        INTEGER DEFAULT 0
 *   locator_ref         TEXT DEFAULT 'FLOAT'
 *   is_variance         INTEGER DEFAULT 0
 *   anchor_face         TEXT DEFAULT 'BACK'
 *   layout_strategy     TEXT DEFAULT 'LINEAR'
 *   allocated_width_mm  INTEGER DEFAULT 0    (AllocatedSize X — AABB; null = M_Product dims × 1000)
 *   allocated_depth_mm  INTEGER DEFAULT 0    (AllocatedSize Y — AABB)
 *   allocated_height_mm INTEGER DEFAULT 0    (AllocatedSize Z — AABB)
 *   component_type      TEXT NOT NULL DEFAULT 'MAKE'  (BUY | MAKE | PHANTOM — Libero PP)
 *   storey              TEXT                 (P0.2: spatial partition, instance-specific)
 *   element_ref         TEXT                 (P0.2: descriptive key from IFC extraction)
 *   ordinal             INTEGER DEFAULT 0    (P0.2: position-sorted order within class+storey)
 *   orientation         TEXT                 (P0.2: NS/EW/POINT for walls, NULL otherwise)
 *   material_name       TEXT                 (P0.2: human-readable material name)
 *   material_rgba       TEXT                 (P0.2: R,G,B,A surface style)
 * </pre>
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept — §2.3, §4</a>
 */
public class X_M_BOMLine extends BasePO {

    public static final String Table_Name                       = "m_bom_line";
    public static final String COLUMNNAME_bom_child_id          = "bom_child_id";
    public static final String COLUMNNAME_bom_id                = "bom_id";
    public static final String COLUMNNAME_child_product_id      = "child_product_id";
    public static final String COLUMNNAME_child_element_type    = "child_element_type";
    public static final String COLUMNNAME_child_name_pattern    = "child_name_pattern";
    public static final String COLUMNNAME_role                  = "role";
    public static final String COLUMNNAME_qty_type              = "qty_type";
    public static final String COLUMNNAME_sequence              = "sequence";
    public static final String COLUMNNAME_is_active             = "is_active";
    public static final String COLUMNNAME_z_rule                = "z_rule";
    public static final String COLUMNNAME_dx                    = "dx";
    public static final String COLUMNNAME_dy                    = "dy";
    public static final String COLUMNNAME_dz                    = "dz";
    public static final String COLUMNNAME_rotation_rule         = "rotation_rule";
    public static final String COLUMNNAME_fit_priority          = "fit_priority";
    public static final String COLUMNNAME_min_space_mm          = "min_space_mm";
    public static final String COLUMNNAME_locator_ref           = "locator_ref";
    public static final String COLUMNNAME_is_variance           = "is_variance";
    public static final String COLUMNNAME_anchor_face           = "anchor_face";
    public static final String COLUMNNAME_layout_strategy       = "layout_strategy";
    public static final String COLUMNNAME_allocated_width_mm    = "allocated_width_mm";
    public static final String COLUMNNAME_allocated_depth_mm    = "allocated_depth_mm";
    public static final String COLUMNNAME_allocated_height_mm   = "allocated_height_mm";
    public static final String COLUMNNAME_component_type        = "component_type";
    public static final String COLUMNNAME_storey               = "storey";
    public static final String COLUMNNAME_element_ref          = "element_ref";
    public static final String COLUMNNAME_ordinal              = "ordinal";
    public static final String COLUMNNAME_orientation          = "orientation";
    public static final String COLUMNNAME_material_name        = "material_name";
    public static final String COLUMNNAME_material_rgba        = "material_rgba";

    public X_M_BOMLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_bom_child_id; }

    public int    getBomChildId()          { return get_ValueAsInt(COLUMNNAME_bom_child_id); }
    public String getBomId()               { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String getChildProductId()      { return get_ValueAsString(COLUMNNAME_child_product_id); }
    public String getChildElementType()    { return get_ValueAsString(COLUMNNAME_child_element_type); }
    public String getChildNamePattern()    { return get_ValueAsString(COLUMNNAME_child_name_pattern); }
    public String getRole()                { return get_ValueAsString(COLUMNNAME_role); }
    public String getQtyType()             { return get_ValueAsString(COLUMNNAME_qty_type); }
    public int    getSequence()            { return get_ValueAsInt(COLUMNNAME_sequence); }
    public boolean isActive()              { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public String getZRule()               { return get_ValueAsString(COLUMNNAME_z_rule); }
    public double getDx()                  { return get_ValueAsDouble(COLUMNNAME_dx); }
    public double getDy()                  { return get_ValueAsDouble(COLUMNNAME_dy); }
    public double getDz()                  { return get_ValueAsDouble(COLUMNNAME_dz); }
    public String getRotationRule()        { return get_ValueAsString(COLUMNNAME_rotation_rule); }
    public int    getFitPriority()         { return get_ValueAsInt(COLUMNNAME_fit_priority); }
    public int    getMinSpaceMm()          { return get_ValueAsInt(COLUMNNAME_min_space_mm); }
    public String getLocatorRef()          { return get_ValueAsString(COLUMNNAME_locator_ref); }
    public boolean isVariance()            { return get_ValueAsBoolean(COLUMNNAME_is_variance); }
    public String getAnchorFace()          { return get_ValueAsString(COLUMNNAME_anchor_face); }
    public String getLayoutStrategy()      { return get_ValueAsString(COLUMNNAME_layout_strategy); }
    public int    getAllocatedWidthMm()    { return get_ValueAsInt(COLUMNNAME_allocated_width_mm); }
    public int    getAllocatedDepthMm()    { return get_ValueAsInt(COLUMNNAME_allocated_depth_mm); }
    public int    getAllocatedHeightMm()   { return get_ValueAsInt(COLUMNNAME_allocated_height_mm); }

    /** Exact REAL width (mm) — use for digest verification where ±1mm matters. */
    public double getAllocatedWidthMmExact()  { return get_ValueAsDouble(COLUMNNAME_allocated_width_mm); }
    /** Exact REAL depth (mm) — use for digest verification where ±1mm matters. */
    public double getAllocatedDepthMmExact()  { return get_ValueAsDouble(COLUMNNAME_allocated_depth_mm); }
    /** Exact REAL height (mm) — use for digest verification where ±1mm matters. */
    public double getAllocatedHeightMmExact() { return get_ValueAsDouble(COLUMNNAME_allocated_height_mm); }
    public String getComponentType()       { return get_ValueAsString(COLUMNNAME_component_type); }
    public String getStorey()              { return get_ValueAsString(COLUMNNAME_storey); }
    public String getElementRef()          { return get_ValueAsString(COLUMNNAME_element_ref); }
    public int    getOrdinal()             { return get_ValueAsInt(COLUMNNAME_ordinal); }
    public String getOrientation()         { return get_ValueAsString(COLUMNNAME_orientation); }
    public String getMaterialName()        { return get_ValueAsString(COLUMNNAME_material_name); }
    public String getMaterialRgba()        { return get_ValueAsString(COLUMNNAME_material_rgba); }

    public void setBomId(String v)                { set_Value(COLUMNNAME_bom_id, v); }
    public void setChildProductId(String v)       { set_Value(COLUMNNAME_child_product_id, v); }
    public void setChildElementType(String v)     { set_Value(COLUMNNAME_child_element_type, v); }
    public void setChildNamePattern(String v)     { set_Value(COLUMNNAME_child_name_pattern, v); }
    public void setRole(String v)                 { set_Value(COLUMNNAME_role, v); }
    public void setQtyType(String v)              { set_Value(COLUMNNAME_qty_type, v); }
    public void setSequence(int v)                { set_Value(COLUMNNAME_sequence, v); }
    public void setIsActive(boolean v)            { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setZRule(String v)                { set_Value(COLUMNNAME_z_rule, v); }
    public void setDx(double v)                   { set_Value(COLUMNNAME_dx, v); }
    public void setDy(double v)                   { set_Value(COLUMNNAME_dy, v); }
    public void setDz(double v)                   { set_Value(COLUMNNAME_dz, v); }
    public void setRotationRule(String v)         { set_Value(COLUMNNAME_rotation_rule, v); }
    public void setFitPriority(int v)             { set_Value(COLUMNNAME_fit_priority, v); }
    public void setMinSpaceMm(int v)              { set_Value(COLUMNNAME_min_space_mm, v); }
    public void setLocatorRef(String v)           { set_Value(COLUMNNAME_locator_ref, v); }
    public void setIsVariance(boolean v)          { set_Value(COLUMNNAME_is_variance, v ? 1 : 0); }
    public void setAnchorFace(String v)           { set_Value(COLUMNNAME_anchor_face, v); }
    public void setLayoutStrategy(String v)       { set_Value(COLUMNNAME_layout_strategy, v); }
    public void setAllocatedWidthMm(int v)        { set_Value(COLUMNNAME_allocated_width_mm, v); }
    public void setAllocatedDepthMm(int v)        { set_Value(COLUMNNAME_allocated_depth_mm, v); }
    public void setAllocatedHeightMm(int v)       { set_Value(COLUMNNAME_allocated_height_mm, v); }
    public void setComponentType(String v)        { set_Value(COLUMNNAME_component_type, v); }
    public void setStorey(String v)               { set_Value(COLUMNNAME_storey, v); }
    public void setElementRef(String v)           { set_Value(COLUMNNAME_element_ref, v); }
    public void setOrdinal(int v)                 { set_Value(COLUMNNAME_ordinal, v); }
    public void setOrientation(String v)          { set_Value(COLUMNNAME_orientation, v); }
    public void setMaterialName(String v)         { set_Value(COLUMNNAME_material_name, v); }
    public void setMaterialRgba(String v)         { set_Value(COLUMNNAME_material_rgba, v); }
}
