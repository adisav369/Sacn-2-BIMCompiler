package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 90: Room Door Check
 *
 * Validates that every non-utility room has at least 1 door or opens_to connection.
 * Uses door GUIDs which contain room names (e.g., DOOR_ENTRANCE_LOBBY_DOOR_WEST_Ground).
 */
public class RoomDoorCheck implements SanityCheck {

    private static final Set<String> EXEMPT_PATTERNS = Set.of(
        "shaft", "riser", "void", "tank", "machine"
    );

    @Override
    public String getId() { return "room_door_access"; }

    @Override
    public String getName() { return "Room Door Access"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();
        List<String> roomsWithoutDoors = new ArrayList<>();
        int totalRooms = 0;
        int roomsWithDoors = 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Get all distinct IfcSpace names (rooms)
            Set<String> roomNames = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT DISTINCT element_name FROM elements_meta WHERE ifc_class = 'IfcSpace'")) {
                while (rs.next()) {
                    String name = rs.getString("element_name");
                    if (name != null) roomNames.add(name);
                }
            }

            // Get all door GUIDs (which encode room names)
            Set<String> doorGuids = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT guid FROM elements_meta WHERE ifc_class = 'IfcDoor'")) {
                while (rs.next()) {
                    doorGuids.add(rs.getString("guid").toUpperCase());
                }
            }

            // Check each room
            for (String room : roomNames) {
                String lower = room.toLowerCase();

                // Skip exempt types
                boolean exempt = false;
                for (String ex : EXEMPT_PATTERNS) {
                    if (lower.contains(ex)) { exempt = true; break; }
                }
                if (exempt) continue;

                totalRooms++;

                // Check if any door GUID contains this room name (case-insensitive)
                String roomUpper = room.toUpperCase();
                boolean hasDoor = false;
                for (String guid : doorGuids) {
                    if (guid.contains(roomUpper)) {
                        hasDoor = true;
                        break;
                    }
                }

                if (hasDoor) {
                    roomsWithDoors++;
                } else {
                    roomsWithoutDoors.add(room);
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("SQL error: " + e.getMessage())
                .build();
        }

        if (roomsWithoutDoors.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d habitable rooms have door access", totalRooms))
                .data("totalRooms", totalRooms)
                .build();
        }

        int missing = roomsWithoutDoors.size();
        CheckResult.Builder result;
        if (missing > totalRooms / 2) {
            result = CheckResult.fail(getId(), getName());
        } else {
            result = CheckResult.warning(getId(), getName());
        }

        result.summary(String.format("%d of %d rooms have no door access", missing, totalRooms));
        for (int i = 0; i < Math.min(missing, 10); i++) {
            result.detail("No door: " + roomsWithoutDoors.get(i));
        }
        if (missing > 10) {
            result.detail(String.format("... and %d more", missing - 10));
        }
        result.guidance("Add doors or opens_to connections for accessible rooms");
        result.data("roomsWithoutDoors", missing);

        return result.build();
    }
}
