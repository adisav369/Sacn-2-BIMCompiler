package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_room_slot}.
 */
public class M_AdRoomSlot extends X_AdRoomSlot {

    public M_AdRoomSlot(Connection conn) { super(conn); }

    /**
     * All slots for a given room_type, ordered by priority.
     * Matches the query used by BOMAssemblerAD when dispatching room furnishing.
     */
    public static List<M_AdRoomSlot> getByRoomType(Connection conn, String roomType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdRoomSlot::new, Table_Name)
            .where(COLUMNNAME_room_type + " = ?", roomType)
            .orderBy(COLUMNNAME_slot_priority)
            .list();
    }

    /**
     * All slots that dispatch a BOM (assembly_id IS NOT NULL), ordered by room_type.
     *
     * <p>Used by Preflight Check H to audit global slot authority against a specific building.
     * ARCHITECTURE NOTE: {@code ad_room_slot} has no {@code building_type} column — slots are
     * globally scoped. This is the root cause of the SH/DX cross-contamination risk.
     */
    public static List<M_AdRoomSlot> getWithAssembly(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, M_AdRoomSlot::new, Table_Name)
            .where(COLUMNNAME_assembly_id + " IS NOT NULL")
            .orderBy(COLUMNNAME_room_type)
            .list();
    }
}
