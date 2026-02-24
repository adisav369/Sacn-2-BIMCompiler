package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code wm_empty_storage_line}.
 *
 * <p>WMS instance model — one row per active M_Locator per room per compilation.
 * Mirrors iDempiere WMS Empty Storage Line concept (WMS_Excel sheet 8_WM_EmptyStorageLine).
 *
 * <p>The AD layer (template) defines locator types via {@code ad_bom_child.locator_ref}.
 * This table holds the live instance values: current fill state, remaining capacity,
 * and the next available putaway anchor point in mm — all at the M_Locator grain.
 *
 * <p>ALB (Aisle/Level/Bin) address columns match the building's mm coordinate system:
 * <ul>
 *   <li>{@code building_type} — Location (Warehouse; FK → ad_building_registry)</li>
 *   <li>{@code storey}        — Level (storey/floor label)</li>
 *   <li>{@code room_name}     — Bin (FK → ad_room_boundary)</li>
 *   <li>{@code locator_ref}   — M_Locator (NORTH_WALL, CENTRE, FLOAT…
 *                               resolves to mm grid cell via ad_building_grid)</li>
 * </ul>
 *
 * <p>DocStatus lifecycle (iDempiere standard):
 * <ul>
 *   <li>{@code DR} — Draft: PhantomLayout in progress during BOM resolution</li>
 *   <li>{@code CO} — Complete: compilation done, record final</li>
 *   <li>{@code VO} — Void: invalidated — recompile required</li>
 * </ul>
 *
 * <p>Table: {@code wm_empty_storage_line}
 * <pre>
 *   line_id           INTEGER PRIMARY KEY AUTOINCREMENT
 *   building_type     TEXT NOT NULL
 *   storey            TEXT NOT NULL
 *   room_name         TEXT NOT NULL
 *   locator_ref       TEXT NOT NULL
 *   capacity_mm       REAL NOT NULL
 *   filled_mm         REAL NOT NULL DEFAULT 0
 *   remaining_mm      REAL NOT NULL
 *   next_anchor_x_mm  REAL NOT NULL DEFAULT 0
 *   next_anchor_y_mm  REAL NOT NULL DEFAULT 0
 *   next_anchor_z_mm  REAL NOT NULL DEFAULT 0
 *   doc_status        TEXT NOT NULL DEFAULT 'DR'
 *   created           TEXT NOT NULL DEFAULT (datetime('now'))
 *   updated           TEXT NOT NULL DEFAULT (datetime('now'))
 * </pre>
 */
public class X_WmEmptyStorageLine extends BasePO {

    public static final String Table_Name                         = "wm_empty_storage_line";
    public static final String COLUMNNAME_line_id                 = "line_id";
    public static final String COLUMNNAME_building_type           = "building_type";
    public static final String COLUMNNAME_storey                  = "storey";
    public static final String COLUMNNAME_room_name               = "room_name";
    public static final String COLUMNNAME_locator_ref             = "locator_ref";
    public static final String COLUMNNAME_capacity_mm             = "capacity_mm";
    public static final String COLUMNNAME_filled_mm               = "filled_mm";
    public static final String COLUMNNAME_remaining_mm            = "remaining_mm";
    public static final String COLUMNNAME_next_anchor_x_mm        = "next_anchor_x_mm";
    public static final String COLUMNNAME_next_anchor_y_mm        = "next_anchor_y_mm";
    public static final String COLUMNNAME_next_anchor_z_mm        = "next_anchor_z_mm";
    public static final String COLUMNNAME_doc_status              = "doc_status";
    public static final String COLUMNNAME_created                 = "created";
    public static final String COLUMNNAME_updated                 = "updated";

    /** DocStatus constants — iDempiere standard lifecycle. */
    public static final String DOCSTATUS_Draft    = "DR";
    public static final String DOCSTATUS_Complete = "CO";
    public static final String DOCSTATUS_Void     = "VO";

    public X_WmEmptyStorageLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_line_id; }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int    getLineId()          { return get_ValueAsInt(COLUMNNAME_line_id); }
    public String getBuildingType()    { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getStorey()          { return get_ValueAsString(COLUMNNAME_storey); }
    public String getRoomName()        { return get_ValueAsString(COLUMNNAME_room_name); }
    public String getLocatorRef()      { return get_ValueAsString(COLUMNNAME_locator_ref); }
    public double getCapacityMm()      { return get_ValueAsDouble(COLUMNNAME_capacity_mm); }
    public double getFilledMm()        { return get_ValueAsDouble(COLUMNNAME_filled_mm); }
    public double getRemainingMm()     { return get_ValueAsDouble(COLUMNNAME_remaining_mm); }
    public double getNextAnchorXMm()   { return get_ValueAsDouble(COLUMNNAME_next_anchor_x_mm); }
    public double getNextAnchorYMm()   { return get_ValueAsDouble(COLUMNNAME_next_anchor_y_mm); }
    public double getNextAnchorZMm()   { return get_ValueAsDouble(COLUMNNAME_next_anchor_z_mm); }
    public String getDocStatus()       { return get_ValueAsString(COLUMNNAME_doc_status); }
    public String getCreated()         { return get_ValueAsString(COLUMNNAME_created); }
    public String getUpdated()         { return get_ValueAsString(COLUMNNAME_updated); }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setBuildingType(String v)    { set_Value(COLUMNNAME_building_type, v); }
    public void setStorey(String v)          { set_Value(COLUMNNAME_storey, v); }
    public void setRoomName(String v)        { set_Value(COLUMNNAME_room_name, v); }
    public void setLocatorRef(String v)      { set_Value(COLUMNNAME_locator_ref, v); }
    public void setCapacityMm(double v)      { set_Value(COLUMNNAME_capacity_mm, v); }
    public void setFilledMm(double v)        { set_Value(COLUMNNAME_filled_mm, v); }
    public void setRemainingMm(double v)     { set_Value(COLUMNNAME_remaining_mm, v); }
    public void setNextAnchorXMm(double v)   { set_Value(COLUMNNAME_next_anchor_x_mm, v); }
    public void setNextAnchorYMm(double v)   { set_Value(COLUMNNAME_next_anchor_y_mm, v); }
    public void setNextAnchorZMm(double v)   { set_Value(COLUMNNAME_next_anchor_z_mm, v); }
    public void setDocStatus(String v)       { set_Value(COLUMNNAME_doc_status, v); }
    public void setUpdated(String v)         { set_Value(COLUMNNAME_updated, v); }

    // ── Computed helpers ─────────────────────────────────────────────────────

    /** True when this Locator is full — remaining_mm ≤ 0. */
    public boolean isFull()       { return getRemainingMm() <= 0; }

    /** True when remaining_mm is negative — GIC violation: fixed children overflow Locator. */
    public boolean isOverflow()   { return getRemainingMm() < 0; }

    /** True when DocStatus is Draft (PhantomLayout in progress). */
    public boolean isDraft()      { return DOCSTATUS_Draft.equals(getDocStatus()); }

    /** True when DocStatus is Complete (compilation finalised). */
    public boolean isComplete()   { return DOCSTATUS_Complete.equals(getDocStatus()); }
}
