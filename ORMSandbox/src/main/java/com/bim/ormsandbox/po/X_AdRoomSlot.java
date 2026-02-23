package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_room_slot}.
 *
 * <p>Room template dispatch slots — maps room_type → BOM assembly.
 * Analogous to iDempiere {@code M_BOM_Line} (room template slots, generic).
 *
 * <p>Table: {@code ad_room_slot}
 * <pre>
 *   slot_id       INTEGER PRIMARY KEY AUTOINCREMENT
 *   room_type     TEXT NOT NULL
 *   slot_name     TEXT NOT NULL
 *   assembly_id   TEXT         (FK → ad_bom.bom_id dispatched for this slot)
 *   version_range TEXT DEFAULT '[1.0,2.0)'
 *   slot_face     TEXT
 *   slot_priority INTEGER DEFAULT 100
 *   is_required   INTEGER DEFAULT 0
 *   profile       TEXT DEFAULT NULL
 *   min_area      REAL DEFAULT 0.0
 *   building_type TEXT DEFAULT NULL   (NULL=global; non-NULL=building-specific)
 *   UNIQUE(room_type, slot_name, profile)
 * </pre>
 *
 * <p>TRAP: {@code assembly_id} = BOM ID dispatched for that slot (FK to {@code ad_bom.bom_id})
 */
public class X_AdRoomSlot extends BasePO {

    public static final String Table_Name                    = "ad_room_slot";
    public static final String COLUMNNAME_slot_id            = "slot_id";
    public static final String COLUMNNAME_room_type          = "room_type";
    public static final String COLUMNNAME_slot_name          = "slot_name";
    public static final String COLUMNNAME_assembly_id        = "assembly_id";
    public static final String COLUMNNAME_version_range      = "version_range";
    public static final String COLUMNNAME_slot_face          = "slot_face";
    public static final String COLUMNNAME_slot_priority      = "slot_priority";
    public static final String COLUMNNAME_is_required        = "is_required";
    public static final String COLUMNNAME_profile            = "profile";
    public static final String COLUMNNAME_min_area           = "min_area";
    public static final String COLUMNNAME_building_type      = "building_type";

    public X_AdRoomSlot(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_slot_id; }

    public int    getSlotId()       { return get_ValueAsInt(COLUMNNAME_slot_id); }
    public String getRoomType()     { return get_ValueAsString(COLUMNNAME_room_type); }
    public String getSlotName()     { return get_ValueAsString(COLUMNNAME_slot_name); }
    public String getAssemblyId()   { return get_ValueAsString(COLUMNNAME_assembly_id); }
    public String getVersionRange() { return get_ValueAsString(COLUMNNAME_version_range); }
    public String getSlotFace()     { return get_ValueAsString(COLUMNNAME_slot_face); }
    public int    getSlotPriority() { return get_ValueAsInt(COLUMNNAME_slot_priority); }
    public boolean isRequired()     { return get_ValueAsBoolean(COLUMNNAME_is_required); }
    public String getProfile()      { return get_ValueAsString(COLUMNNAME_profile); }
    public double getMinArea()      { return get_ValueAsDouble(COLUMNNAME_min_area); }
    public String getBuildingType() { return get_ValueAsString(COLUMNNAME_building_type); }

    public void setRoomType(String v)     { set_Value(COLUMNNAME_room_type, v); }
    public void setSlotName(String v)     { set_Value(COLUMNNAME_slot_name, v); }
    public void setAssemblyId(String v)   { set_Value(COLUMNNAME_assembly_id, v); }
    public void setVersionRange(String v) { set_Value(COLUMNNAME_version_range, v); }
    public void setSlotFace(String v)     { set_Value(COLUMNNAME_slot_face, v); }
    public void setSlotPriority(int v)    { set_Value(COLUMNNAME_slot_priority, v); }
    public void setIsRequired(boolean v)  { set_Value(COLUMNNAME_is_required, v ? 1 : 0); }
    public void setProfile(String v)      { set_Value(COLUMNNAME_profile, v); }
    public void setMinArea(double v)      { set_Value(COLUMNNAME_min_area, v); }
    public void setBuildingType(String v) { set_Value(COLUMNNAME_building_type, v); }
}
