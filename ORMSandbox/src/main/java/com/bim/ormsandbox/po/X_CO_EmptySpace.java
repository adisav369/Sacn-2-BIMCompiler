package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code co_empty_space}.
 *
 * <p>Construction site header — one record per C_Order (c_order).
 * The AABB is the total intended construction space. IsAvailable is a quality
 * gate: it only goes to N when the output is proven correct (tests GREEN).
 *
 * <p>IsAvailable lifecycle:
 * <ol>
 *   <li>Project start: {@code is_available = 1} (Y), AABB = full space.</li>
 *   <li>During processing: stays 1.</li>
 *   <li>After output + tests GREEN: {@code is_available = 0} (N) — confirmed consumed.</li>
 *   <li>Reprocess: reset to 1.</li>
 * </ol>
 *
 * <p>Table: {@code co_empty_space}
 * <pre>
 *   co_emptyspace_id    INTEGER PRIMARY KEY AUTOINCREMENT
 *   c_order_id          TEXT NOT NULL        -- FK → c_order
 *   origin_x_mm         REAL NOT NULL DEFAULT 0
 *   origin_y_mm         REAL NOT NULL DEFAULT 0
 *   origin_z_mm         REAL NOT NULL DEFAULT 0
 *   aabb_width_mm       REAL NOT NULL        -- total X
 *   aabb_depth_mm       REAL NOT NULL        -- total Y
 *   aabb_height_mm      REAL NOT NULL        -- total Z
 *   is_available        INTEGER NOT NULL DEFAULT 1
 *   doc_status          TEXT NOT NULL DEFAULT 'DR'
 *   created             TEXT NOT NULL DEFAULT (datetime('now'))
 *   updated             TEXT NOT NULL DEFAULT (datetime('now'))
 * </pre>
 */
public class X_CO_EmptySpace extends BasePO {

    public static final String Table_Name                       = "co_empty_space";
    public static final String COLUMNNAME_co_emptyspace_id      = "co_emptyspace_id";
    public static final String COLUMNNAME_c_order_id            = "c_order_id";
    public static final String COLUMNNAME_origin_x_mm           = "origin_x_mm";
    public static final String COLUMNNAME_origin_y_mm           = "origin_y_mm";
    public static final String COLUMNNAME_origin_z_mm           = "origin_z_mm";
    public static final String COLUMNNAME_aabb_width_mm         = "aabb_width_mm";
    public static final String COLUMNNAME_aabb_depth_mm         = "aabb_depth_mm";
    public static final String COLUMNNAME_aabb_height_mm        = "aabb_height_mm";
    public static final String COLUMNNAME_is_available          = "is_available";
    public static final String COLUMNNAME_doc_status            = "doc_status";
    public static final String COLUMNNAME_created               = "created";
    public static final String COLUMNNAME_updated               = "updated";

    public static final String DOCSTATUS_Draft      = "DR";
    public static final String DOCSTATUS_InProgress = "IP";
    public static final String DOCSTATUS_Complete   = "CO";
    public static final String DOCSTATUS_Rejected   = "RE";

    public X_CO_EmptySpace(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_co_emptyspace_id; }

    // ── Getters ─────────────────────────────────────────────────────────────

    public int    getCoEmptyspaceId()  { return get_ValueAsInt(COLUMNNAME_co_emptyspace_id); }
    public String getCOrderId()        { return get_ValueAsString(COLUMNNAME_c_order_id); }
    public double getOriginXMm()       { return get_ValueAsDouble(COLUMNNAME_origin_x_mm); }
    public double getOriginYMm()       { return get_ValueAsDouble(COLUMNNAME_origin_y_mm); }
    public double getOriginZMm()       { return get_ValueAsDouble(COLUMNNAME_origin_z_mm); }
    public double getAabbWidthMm()     { return get_ValueAsDouble(COLUMNNAME_aabb_width_mm); }
    public double getAabbDepthMm()     { return get_ValueAsDouble(COLUMNNAME_aabb_depth_mm); }
    public double getAabbHeightMm()    { return get_ValueAsDouble(COLUMNNAME_aabb_height_mm); }
    public boolean isAvailable()       { return get_ValueAsBoolean(COLUMNNAME_is_available); }
    public String getDocStatus()       { return get_ValueAsString(COLUMNNAME_doc_status); }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setCOrderId(String v)        { set_Value(COLUMNNAME_c_order_id, v); }
    public void setOriginXMm(double v)       { set_Value(COLUMNNAME_origin_x_mm, v); }
    public void setOriginYMm(double v)       { set_Value(COLUMNNAME_origin_y_mm, v); }
    public void setOriginZMm(double v)       { set_Value(COLUMNNAME_origin_z_mm, v); }
    public void setAabbWidthMm(double v)     { set_Value(COLUMNNAME_aabb_width_mm, v); }
    public void setAabbDepthMm(double v)     { set_Value(COLUMNNAME_aabb_depth_mm, v); }
    public void setAabbHeightMm(double v)    { set_Value(COLUMNNAME_aabb_height_mm, v); }
    public void setIsAvailable(boolean v)    { set_Value(COLUMNNAME_is_available, v ? 1 : 0); }
    public void setDocStatus(String v)       { set_Value(COLUMNNAME_doc_status, v); }
}
