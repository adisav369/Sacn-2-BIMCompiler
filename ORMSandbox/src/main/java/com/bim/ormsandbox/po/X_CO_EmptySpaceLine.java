package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code co_empty_space_line}.
 *
 * <p>Alignment record — WHERE the BOM box sits + orientation.
 * Does NOT repeat the BOM (that's intact on C_OrderLine.BOM.BOMLine).
 * Says: "this BOM construct goes HERE, facing THIS way."
 *
 * <p>For SH/DX: as few as ONE record — accepting the top-level UNIT BOM
 * into the full AABB. All children translate deterministically.
 *
 * <p>Table: {@code co_empty_space_line}
 * <pre>
 *   line_id             INTEGER PRIMARY KEY AUTOINCREMENT
 *   co_emptyspace_id    INTEGER NOT NULL     -- FK → co_empty_space
 *   bom_line_seq        INTEGER NOT NULL     -- sequence from M_BOM_Line
 *   bom_id              TEXT NOT NULL        -- which M_BOM this line accepts
 *   bom_line_role       TEXT                 -- role from M_BOM_Line
 *   bom_level           INTEGER DEFAULT 0    -- depth (0=top, 1=floor, 2=room…)
 *   before_x_mm         REAL                 -- anchor BEFORE this item
 *   before_y_mm         REAL
 *   before_z_mm         REAL
 *   next_x_mm           REAL                 -- anchor AFTER this item
 *   next_y_mm           REAL
 *   next_z_mm           REAL
 *   orientation_rad     REAL DEFAULT 0       -- resolved orientation
 *   capacity_mm         REAL                 -- locator extent
 *   filled_mm           REAL DEFAULT 0
 *   remaining_mm        REAL                 -- available space
 *   storey              TEXT
 *   room_name           TEXT
 *   locator_ref         TEXT                 -- NORTH_WALL, CENTRE, FLOAT…
 *   doc_status          TEXT NOT NULL DEFAULT 'DR'
 *   created             TEXT NOT NULL DEFAULT (datetime('now'))
 *   updated             TEXT NOT NULL DEFAULT (datetime('now'))
 * </pre>
 */
public class X_CO_EmptySpaceLine extends BasePO {

    public static final String Table_Name                         = "co_empty_space_line";
    public static final String COLUMNNAME_line_id                 = "line_id";
    public static final String COLUMNNAME_co_emptyspace_id        = "co_emptyspace_id";
    public static final String COLUMNNAME_bom_line_seq            = "bom_line_seq";
    public static final String COLUMNNAME_bom_id                  = "bom_id";
    public static final String COLUMNNAME_bom_line_role           = "bom_line_role";
    public static final String COLUMNNAME_bom_level               = "bom_level";
    public static final String COLUMNNAME_before_x_mm             = "before_x_mm";
    public static final String COLUMNNAME_before_y_mm             = "before_y_mm";
    public static final String COLUMNNAME_before_z_mm             = "before_z_mm";
    public static final String COLUMNNAME_next_x_mm               = "next_x_mm";
    public static final String COLUMNNAME_next_y_mm               = "next_y_mm";
    public static final String COLUMNNAME_next_z_mm               = "next_z_mm";
    public static final String COLUMNNAME_orientation_rad         = "orientation_rad";
    public static final String COLUMNNAME_capacity_mm             = "capacity_mm";
    public static final String COLUMNNAME_filled_mm               = "filled_mm";
    public static final String COLUMNNAME_remaining_mm            = "remaining_mm";
    public static final String COLUMNNAME_storey                  = "storey";
    public static final String COLUMNNAME_room_name               = "room_name";
    public static final String COLUMNNAME_locator_ref             = "locator_ref";
    public static final String COLUMNNAME_doc_status              = "doc_status";
    public static final String COLUMNNAME_created                 = "created";
    public static final String COLUMNNAME_updated                 = "updated";

    public X_CO_EmptySpaceLine(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_line_id; }

    // ── Getters ─────────────────────────────────────────────────────────────

    public int    getLineId()            { return get_ValueAsInt(COLUMNNAME_line_id); }
    public int    getCoEmptyspaceId()    { return get_ValueAsInt(COLUMNNAME_co_emptyspace_id); }
    public int    getBomLineSeq()        { return get_ValueAsInt(COLUMNNAME_bom_line_seq); }
    public String getBomId()             { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String getBomLineRole()       { return get_ValueAsString(COLUMNNAME_bom_line_role); }
    public int    getBomLevel()          { return get_ValueAsInt(COLUMNNAME_bom_level); }
    public double getBeforeXMm()         { return get_ValueAsDouble(COLUMNNAME_before_x_mm); }
    public double getBeforeYMm()         { return get_ValueAsDouble(COLUMNNAME_before_y_mm); }
    public double getBeforeZMm()         { return get_ValueAsDouble(COLUMNNAME_before_z_mm); }
    public double getNextXMm()           { return get_ValueAsDouble(COLUMNNAME_next_x_mm); }
    public double getNextYMm()           { return get_ValueAsDouble(COLUMNNAME_next_y_mm); }
    public double getNextZMm()           { return get_ValueAsDouble(COLUMNNAME_next_z_mm); }
    public double getOrientationRad()    { return get_ValueAsDouble(COLUMNNAME_orientation_rad); }
    public double getCapacityMm()        { return get_ValueAsDouble(COLUMNNAME_capacity_mm); }
    public double getFilledMm()          { return get_ValueAsDouble(COLUMNNAME_filled_mm); }
    public double getRemainingMm()       { return get_ValueAsDouble(COLUMNNAME_remaining_mm); }
    public String getStorey()            { return get_ValueAsString(COLUMNNAME_storey); }
    public String getRoomName()          { return get_ValueAsString(COLUMNNAME_room_name); }
    public String getLocatorRef()        { return get_ValueAsString(COLUMNNAME_locator_ref); }
    public String getDocStatus()         { return get_ValueAsString(COLUMNNAME_doc_status); }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setCoEmptyspaceId(int v)      { set_Value(COLUMNNAME_co_emptyspace_id, v); }
    public void setBomLineSeq(int v)          { set_Value(COLUMNNAME_bom_line_seq, v); }
    public void setBomId(String v)            { set_Value(COLUMNNAME_bom_id, v); }
    public void setBomLineRole(String v)      { set_Value(COLUMNNAME_bom_line_role, v); }
    public void setBomLevel(int v)            { set_Value(COLUMNNAME_bom_level, v); }
    public void setBeforeXMm(double v)        { set_Value(COLUMNNAME_before_x_mm, v); }
    public void setBeforeYMm(double v)        { set_Value(COLUMNNAME_before_y_mm, v); }
    public void setBeforeZMm(double v)        { set_Value(COLUMNNAME_before_z_mm, v); }
    public void setNextXMm(double v)          { set_Value(COLUMNNAME_next_x_mm, v); }
    public void setNextYMm(double v)          { set_Value(COLUMNNAME_next_y_mm, v); }
    public void setNextZMm(double v)          { set_Value(COLUMNNAME_next_z_mm, v); }
    public void setOrientationRad(double v)   { set_Value(COLUMNNAME_orientation_rad, v); }
    public void setCapacityMm(double v)       { set_Value(COLUMNNAME_capacity_mm, v); }
    public void setFilledMm(double v)         { set_Value(COLUMNNAME_filled_mm, v); }
    public void setRemainingMm(double v)      { set_Value(COLUMNNAME_remaining_mm, v); }
    public void setStorey(String v)           { set_Value(COLUMNNAME_storey, v); }
    public void setRoomName(String v)         { set_Value(COLUMNNAME_room_name, v); }
    public void setLocatorRef(String v)       { set_Value(COLUMNNAME_locator_ref, v); }
    public void setDocStatus(String v)        { set_Value(COLUMNNAME_doc_status, v); }
}
