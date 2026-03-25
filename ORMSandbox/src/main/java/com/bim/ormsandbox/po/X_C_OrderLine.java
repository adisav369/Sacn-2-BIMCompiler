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
 * <p>Table: {@code c_orderline} — iDempiere CamelCase naming
 * <pre>
 *   C_OrderLine_ID   INTEGER PRIMARY KEY AUTOINCREMENT
 *   C_Order_ID       TEXT NOT NULL       -- FK to c_order
 *   Storey           TEXT NOT NULL       -- storey identifier (no FK table yet)
 *   Name             TEXT NOT NULL       -- element instance identifier
 *   IfcClass         TEXT NOT NULL       -- IFC entity type
 *   Discipline       TEXT DEFAULT 'ARC'  -- ARC|STR|MEP|FURN|ELE|PLB|FPR
 *   AD_Org_ID        INTEGER             -- FK to AD_Org (ERP.db) — integer discipline
 *   M_Product_ID     TEXT                -- FK to M_Product (assembly family)
 *   IsActive         INTEGER DEFAULT 1
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md §11.9">Three-concern separation</a>
 * @see <a href="docs/BIM_COBOL.md §15.6">PP_Order_Node DDL</a>
 */
public class X_C_OrderLine extends BasePO {

    public static final String Table_Name                    = "c_orderline";

    // ── WHAT columns (order topics) — the ONLY columns on this PO ──
    public static final String COLUMNNAME_C_OrderLine_ID     = "C_OrderLine_ID";
    public static final String COLUMNNAME_C_Order_ID         = "C_Order_ID";
    public static final String COLUMNNAME_Storey             = "Storey";
    public static final String COLUMNNAME_Name               = "Name";
    public static final String COLUMNNAME_IfcClass           = "IfcClass";
    public static final String COLUMNNAME_Discipline         = "Discipline";
    public static final String COLUMNNAME_AD_Org_ID          = "AD_Org_ID";
    public static final String COLUMNNAME_M_Product_ID       = "M_Product_ID";
    public static final String COLUMNNAME_IsActive           = "IsActive";
    // ── NO placement columns (host_type, host_ref, position_rule, etc.) ──
    // ── NO material columns (width_mm, depth_mm, geometry_hash, etc.) ──
    // FIRST PRINCIPLE (§11.9): c_orderline = WHAT only.
    // Placement → PP_Order_Node. Material dims → M_Product.
    // This is not a TODO — this is a structural guard.

    public X_C_OrderLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_OrderLine_ID; }

    // ── Getters ──

    public int    getCOrderLineId()      { return get_ValueAsInt(COLUMNNAME_C_OrderLine_ID); }
    public String getCOrderId()          { return get_ValueAsString(COLUMNNAME_C_Order_ID); }
    public String getStorey()            { return get_ValueAsString(COLUMNNAME_Storey); }
    public String getName()              { return get_ValueAsString(COLUMNNAME_Name); }
    public String getIfcClass()          { return get_ValueAsString(COLUMNNAME_IfcClass); }
    public String getDiscipline()        { return get_ValueAsString(COLUMNNAME_Discipline); }
    public int    getAD_Org_ID()         { return get_ValueAsInt(COLUMNNAME_AD_Org_ID); }
    public String getMProductId()        { return get_ValueAsString(COLUMNNAME_M_Product_ID); }
    public boolean isActive()            { return get_ValueAsBoolean(COLUMNNAME_IsActive); }

    // ── Setters ──

    public void setCOrderId(String v)        { set_Value(COLUMNNAME_C_Order_ID, v); }
    public void setStorey(String v)          { set_Value(COLUMNNAME_Storey, v); }
    public void setName(String v)            { set_Value(COLUMNNAME_Name, v); }
    public void setIfcClass(String v)        { set_Value(COLUMNNAME_IfcClass, v); }
    public void setDiscipline(String v)      { set_Value(COLUMNNAME_Discipline, v); }
    public void setAD_Org_ID(int v)          { set_Value(COLUMNNAME_AD_Org_ID, v); }
    public void setMProductId(String v)      { set_Value(COLUMNNAME_M_Product_ID, v); }
    public void setIsActive(boolean v)       { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
}
