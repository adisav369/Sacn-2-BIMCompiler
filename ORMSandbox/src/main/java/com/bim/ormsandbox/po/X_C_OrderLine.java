package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * PO layer for {@code c_orderline} — WHAT to build (order topics ONLY).
 *
 * <h3>FIRST PRINCIPLE: c_orderline = WHAT. No placement. No material dims.</h3>
 *
 * <ul>
 *   <li><b>WHAT</b> (order topics) = this class</li>
 *   <li><b>HOW</b>  (production operations) = {@code PP_Order_Node}</li>
 *   <li><b>WHERE</b> (spatial workstation) = {@code co_empty_space_line} (S_Resource)</li>
 *   <li><b>WITH WHAT</b> (material dims) = {@code M_Product}</li>
 * </ul>
 *
 * <p>Placement columns (host_type, host_ref, position_rule, position_value ×3,
 * height_mm, orientation) have been <b>removed</b>. They belong in PP_Order_Node
 * + PP_Order_NodeProduct. If your code needs placement data, use those tables.
 *
 * <p>Material columns (width_mm, height_extent_mm, depth_mm, geometry_hash,
 * material_name, material_rgba) have been <b>removed</b>. They belong in
 * {@code M_Product}. If your code needs material dimensions, use M_Product.
 *
 * <p>This is the structural guard that prevents flat-data shortcuts from being
 * reintroduced. If the Java interface won't let you write or read placement
 * data on c_orderline, the anti-pattern is impossible by construction.
 *
 * @see <a href="docs/ConstructionAsERP.md §11.9">Three-concern separation</a>
 * @see <a href="docs/BIM_COBOL.md §15.6">PP_Order_Node DDL</a>
 */
public class X_C_OrderLine extends BasePO {

    public static final String Table_Name                    = "c_orderline";

    // ── WHAT columns (order topics) — the ONLY columns on this PO ──
    public static final String COLUMNNAME_id                 = "id";
    public static final String COLUMNNAME_building_type      = "building_type";
    public static final String COLUMNNAME_storey             = "storey";
    public static final String COLUMNNAME_element_ref        = "element_ref";
    public static final String COLUMNNAME_ifc_class          = "ifc_class";
    public static final String COLUMNNAME_discipline         = "discipline";
    public static final String COLUMNNAME_family_ref         = "family_ref";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_building_id        = "building_id";

    // ── NO placement columns (host_type, host_ref, position_rule, etc.) ──
    // ── NO material columns (width_mm, depth_mm, geometry_hash, etc.) ──
    // FIRST PRINCIPLE (§11.9): c_orderline = WHAT only.
    // Placement → PP_Order_Node. Material dims → M_Product.
    // This is not a TODO — this is a structural guard.

    public X_C_OrderLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_id; }

    // ── Getters ──

    public int    getId()               { return get_ValueAsInt(COLUMNNAME_id); }
    public String getBuildingType()     { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getStorey()           { return get_ValueAsString(COLUMNNAME_storey); }
    public String getElementRef()       { return get_ValueAsString(COLUMNNAME_element_ref); }
    public String getIfcClass()         { return get_ValueAsString(COLUMNNAME_ifc_class); }
    public String getDiscipline()       { return get_ValueAsString(COLUMNNAME_discipline); }
    public String getFamilyRef()        { return get_ValueAsString(COLUMNNAME_family_ref); }
    public boolean isActive()           { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public int    getBuildingId()       { return get_ValueAsInt(COLUMNNAME_building_id); }

    // ── Setters ──

    public void setBuildingType(String v)    { set_Value(COLUMNNAME_building_type, v); }
    public void setStorey(String v)          { set_Value(COLUMNNAME_storey, v); }
    public void setElementRef(String v)      { set_Value(COLUMNNAME_element_ref, v); }
    public void setIfcClass(String v)        { set_Value(COLUMNNAME_ifc_class, v); }
    public void setDiscipline(String v)      { set_Value(COLUMNNAME_discipline, v); }
    public void setFamilyRef(String v)       { set_Value(COLUMNNAME_family_ref, v); }
    public void setIsActive(boolean v)       { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setBuildingId(int v)         { set_Value(COLUMNNAME_building_id, v); }
}
