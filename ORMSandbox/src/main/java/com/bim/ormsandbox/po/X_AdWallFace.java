package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_wall_face}.
 *
 * <p>Per-room wall face specifications. Each row describes one cardinal face of a room:
 * wall type, interior/exterior flag, and the adjacent room (if interior).
 *
 * <p>Table: {@code ad_wall_face}
 * <pre>
 *   id            INTEGER PRIMARY KEY AUTOINCREMENT
 *   building_type TEXT NOT NULL
 *   storey        TEXT NOT NULL
 *   room_name     TEXT NOT NULL       -- owner room
 *   face          TEXT NOT NULL       -- 'NORTH', 'SOUTH', 'EAST', 'WEST'
 *   wall_type_id  TEXT NOT NULL       -- → ad_wall_type
 *   is_exterior   INTEGER NOT NULL    -- 1 = exterior envelope, 0 = interior
 *   adjacent_room TEXT               -- NULL if exterior, else neighbour room name
 *   is_active     INTEGER DEFAULT 1
 *   building_id   INTEGER REFERENCES ad_building(id)
 *   UNIQUE(building_type, storey, room_name, face)
 * </pre>
 *
 * <p>Consumers: RelationalResolver, MetadataValidator (×2).
 */
public class X_AdWallFace extends BasePO {

    public static final String Table_Name                = "ad_wall_face";
    public static final String COLUMNNAME_id             = "id";
    public static final String COLUMNNAME_building_type  = "building_type";
    public static final String COLUMNNAME_storey         = "storey";
    public static final String COLUMNNAME_room_name      = "room_name";
    public static final String COLUMNNAME_face           = "face";
    public static final String COLUMNNAME_wall_type_id   = "wall_type_id";
    public static final String COLUMNNAME_is_exterior    = "is_exterior";
    public static final String COLUMNNAME_adjacent_room  = "adjacent_room";
    public static final String COLUMNNAME_is_active      = "is_active";
    public static final String COLUMNNAME_building_id    = "building_id";

    public X_AdWallFace(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_id; }

    public int    getId()            { return get_ValueAsInt(COLUMNNAME_id); }
    public String getBuildingType()  { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getStorey()        { return get_ValueAsString(COLUMNNAME_storey); }
    public String getRoomName()      { return get_ValueAsString(COLUMNNAME_room_name); }
    public String getFace()          { return get_ValueAsString(COLUMNNAME_face); }
    public String getWallTypeId()    { return get_ValueAsString(COLUMNNAME_wall_type_id); }
    public boolean isExterior()      { return get_ValueAsBoolean(COLUMNNAME_is_exterior); }
    public String getAdjacentRoom()  { return get_ValueAsString(COLUMNNAME_adjacent_room); }
    public boolean isActive()        { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public int    getBuildingId()    { return get_ValueAsInt(COLUMNNAME_building_id); }

    public void setBuildingType(String v)  { set_Value(COLUMNNAME_building_type, v); }
    public void setStorey(String v)        { set_Value(COLUMNNAME_storey, v); }
    public void setRoomName(String v)      { set_Value(COLUMNNAME_room_name, v); }
    public void setFace(String v)          { set_Value(COLUMNNAME_face, v); }
    public void setWallTypeId(String v)    { set_Value(COLUMNNAME_wall_type_id, v); }
    public void setIsExterior(boolean v)   { set_Value(COLUMNNAME_is_exterior, v ? 1 : 0); }
    public void setAdjacentRoom(String v)  { set_Value(COLUMNNAME_adjacent_room, v); }
    public void setIsActive(boolean v)     { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setBuildingId(int v)       { set_Value(COLUMNNAME_building_id, v); }
}
