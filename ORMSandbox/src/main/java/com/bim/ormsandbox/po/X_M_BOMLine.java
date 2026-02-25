package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code m_bom_line} (iDempiere: M_BOM_Line).
 *
 * <p>One row per child in a BOM assembly. SpaceSize (AABB) lives here —
 * full 3D bounding box for every child including buffers.
 *
 * <p>Table: {@code m_bom_line}
 * <pre>
 *   bom_child_id    INTEGER PRIMARY KEY AUTOINCREMENT
 *   bom_id          TEXT NOT NULL FK → m_bom
 *   child_ifc_class TEXT
 *   child_element_type TEXT
 *   child_name_pattern TEXT        (LIKE pattern)
 *   child_bom_id    TEXT           FK → m_bom (nested BOM, NULL for leaf)
 *   role            TEXT NOT NULL
 *   qty_type        TEXT DEFAULT 'VARIABLE'
 *   sequence        INTEGER DEFAULT 100
 *   is_active       INTEGER DEFAULT 1
 *   z_rule          TEXT
 *   dx              REAL DEFAULT 0.0  (assembly-relative, METRES)
 *   dy              REAL DEFAULT 0.0
 *   dz              REAL DEFAULT 0.0
 *   rotation_rule   TEXT DEFAULT '0'
 *   fit_priority    INTEGER DEFAULT 20
 *   min_space_mm    INTEGER DEFAULT 0
 *   product_ref     TEXT FK → ad_product_dim
 *   locator_ref     TEXT DEFAULT 'FLOAT'
 *   is_variance     INTEGER DEFAULT 0
 *   anchor_face     TEXT DEFAULT 'BACK'
 *   layout_strategy TEXT DEFAULT 'LINEAR'
 *   space_width_mm  INTEGER DEFAULT 0  (SpaceSize X — AABB)
 *   space_depth_mm  INTEGER DEFAULT 0  (SpaceSize Y — AABB)
 *   space_height_mm INTEGER DEFAULT 0  (SpaceSize Z — AABB)
 * </pre>
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept — §2.3, §4</a>
 */
public class X_M_BOMLine extends BasePO {

    public static final String Table_Name                       = "m_bom_line";
    public static final String COLUMNNAME_bom_child_id          = "bom_child_id";
    public static final String COLUMNNAME_bom_id                = "bom_id";
    public static final String COLUMNNAME_child_ifc_class       = "child_ifc_class";
    public static final String COLUMNNAME_child_element_type    = "child_element_type";
    public static final String COLUMNNAME_child_name_pattern    = "child_name_pattern";
    public static final String COLUMNNAME_child_bom_id          = "child_bom_id";
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
    public static final String COLUMNNAME_product_ref           = "product_ref";
    public static final String COLUMNNAME_locator_ref           = "locator_ref";
    public static final String COLUMNNAME_is_variance           = "is_variance";
    public static final String COLUMNNAME_anchor_face           = "anchor_face";
    public static final String COLUMNNAME_layout_strategy       = "layout_strategy";
    public static final String COLUMNNAME_space_width_mm        = "space_width_mm";
    public static final String COLUMNNAME_space_depth_mm        = "space_depth_mm";
    public static final String COLUMNNAME_space_height_mm       = "space_height_mm";

    public X_M_BOMLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_bom_child_id; }

    public int    getBomChildId()       { return get_ValueAsInt(COLUMNNAME_bom_child_id); }
    public String getBomId()            { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String getChildIfcClass()    { return get_ValueAsString(COLUMNNAME_child_ifc_class); }
    public String getChildElementType() { return get_ValueAsString(COLUMNNAME_child_element_type); }
    public String getChildNamePattern() { return get_ValueAsString(COLUMNNAME_child_name_pattern); }
    public String getChildBomId()       { return get_ValueAsString(COLUMNNAME_child_bom_id); }
    public String getRole()             { return get_ValueAsString(COLUMNNAME_role); }
    public String getQtyType()          { return get_ValueAsString(COLUMNNAME_qty_type); }
    public int    getSequence()         { return get_ValueAsInt(COLUMNNAME_sequence); }
    public boolean isActive()           { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public String getZRule()            { return get_ValueAsString(COLUMNNAME_z_rule); }
    public double getDx()               { return get_ValueAsDouble(COLUMNNAME_dx); }
    public double getDy()               { return get_ValueAsDouble(COLUMNNAME_dy); }
    public double getDz()               { return get_ValueAsDouble(COLUMNNAME_dz); }
    public String getRotationRule()     { return get_ValueAsString(COLUMNNAME_rotation_rule); }
    public int    getFitPriority()      { return get_ValueAsInt(COLUMNNAME_fit_priority); }
    public int    getMinSpaceMm()       { return get_ValueAsInt(COLUMNNAME_min_space_mm); }
    public String getProductRef()       { return get_ValueAsString(COLUMNNAME_product_ref); }
    public String getLocatorRef()       { return get_ValueAsString(COLUMNNAME_locator_ref); }
    public boolean isVariance()         { return get_ValueAsBoolean(COLUMNNAME_is_variance); }
    public String getAnchorFace()       { return get_ValueAsString(COLUMNNAME_anchor_face); }
    public String getLayoutStrategy()   { return get_ValueAsString(COLUMNNAME_layout_strategy); }
    public int    getSpaceWidthMm()     { return get_ValueAsInt(COLUMNNAME_space_width_mm); }
    public int    getSpaceDepthMm()     { return get_ValueAsInt(COLUMNNAME_space_depth_mm); }
    public int    getSpaceHeightMm()    { return get_ValueAsInt(COLUMNNAME_space_height_mm); }

    public void setBomId(String v)             { set_Value(COLUMNNAME_bom_id, v); }
    public void setChildIfcClass(String v)     { set_Value(COLUMNNAME_child_ifc_class, v); }
    public void setChildElementType(String v)  { set_Value(COLUMNNAME_child_element_type, v); }
    public void setChildNamePattern(String v)  { set_Value(COLUMNNAME_child_name_pattern, v); }
    public void setChildBomId(String v)        { set_Value(COLUMNNAME_child_bom_id, v); }
    public void setRole(String v)              { set_Value(COLUMNNAME_role, v); }
    public void setQtyType(String v)           { set_Value(COLUMNNAME_qty_type, v); }
    public void setSequence(int v)             { set_Value(COLUMNNAME_sequence, v); }
    public void setIsActive(boolean v)         { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setZRule(String v)             { set_Value(COLUMNNAME_z_rule, v); }
    public void setDx(double v)               { set_Value(COLUMNNAME_dx, v); }
    public void setDy(double v)               { set_Value(COLUMNNAME_dy, v); }
    public void setDz(double v)               { set_Value(COLUMNNAME_dz, v); }
    public void setRotationRule(String v)      { set_Value(COLUMNNAME_rotation_rule, v); }
    public void setFitPriority(int v)          { set_Value(COLUMNNAME_fit_priority, v); }
    public void setMinSpaceMm(int v)           { set_Value(COLUMNNAME_min_space_mm, v); }
    public void setProductRef(String v)        { set_Value(COLUMNNAME_product_ref, v); }
    public void setLocatorRef(String v)        { set_Value(COLUMNNAME_locator_ref, v); }
    public void setIsVariance(boolean v)       { set_Value(COLUMNNAME_is_variance, v ? 1 : 0); }
    public void setAnchorFace(String v)        { set_Value(COLUMNNAME_anchor_face, v); }
    public void setLayoutStrategy(String v)    { set_Value(COLUMNNAME_layout_strategy, v); }
    public void setSpaceWidthMm(int v)         { set_Value(COLUMNNAME_space_width_mm, v); }
    public void setSpaceDepthMm(int v)         { set_Value(COLUMNNAME_space_depth_mm, v); }
    public void setSpaceHeightMm(int v)        { set_Value(COLUMNNAME_space_height_mm, v); }
}
