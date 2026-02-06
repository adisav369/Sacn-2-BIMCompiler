package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 90: Ceiling Fan Check
 *
 * Validates that each habitable room has at least 1 IfcFan or IfcAirTerminal (diffuser).
 * Uses guid matching since diffuser GUIDs contain room names.
 */
public class CeilingFanCheck implements SanityCheck {

    private static final Set<String> EXEMPT_PATTERNS = Set.of(
        "stair", "shaft", "riser", "void"
    );

    @Override
    public String getId() { return "ceiling_fan"; }

    @Override
    public String getName() { return "Ceiling Fan/Diffuser Presence"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();
        List<String> roomsWithout = new ArrayList<>();
        int totalRooms = 0;
        int roomsWithFan = 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Get all diffuser/fan GUIDs (contain room names)
            Set<String> ventGuids = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT guid FROM elements_meta " +
                    "WHERE ifc_class IN ('IfcFan', 'IfcAirTerminal')")) {
                while (rs.next()) {
                    ventGuids.add(rs.getString("guid").toUpperCase());
                }
            }

            // Check each IfcSpace
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT e.element_name, " +
                    "  (r.maxX - r.minX) * (r.maxY - r.minY) as area " +
                    "FROM elements_meta e " +
                    "JOIN elements_rtree r ON e.id = r.id " +
                    "WHERE e.ifc_class = 'IfcSpace'")) {
                while (rs.next()) {
                    String name = rs.getString("element_name");
                    if (name == null) continue;
                    String lower = name.toLowerCase();

                    boolean exempt = false;
                    for (String ex : EXEMPT_PATTERNS) {
                        if (lower.contains(ex)) { exempt = true; break; }
                    }
                    if (exempt) continue;

                    double area = rs.getDouble("area");
                    if (area < 4.0) continue;

                    totalRooms++;

                    // Check if any vent GUID contains this room name
                    String nameUpper = name.toUpperCase();
                    boolean hasVent = false;
                    for (String guid : ventGuids) {
                        if (guid.contains(nameUpper)) {
                            hasVent = true;
                            break;
                        }
                    }

                    if (hasVent) {
                        roomsWithFan++;
                    } else {
                        roomsWithout.add(name);
                    }
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("SQL error: " + e.getMessage())
                .build();
        }

        if (totalRooms == 0) {
            return CheckResult.pass(getId(), getName())
                .summary("No rooms to check for ventilation")
                .build();
        }

        if (roomsWithout.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d rooms have ceiling fan/diffuser", totalRooms))
                .data("totalRooms", totalRooms)
                .build();
        }

        CheckResult.Builder result = CheckResult.warning(getId(), getName())
            .summary(String.format("%d of %d rooms have no ceiling fan/diffuser",
                roomsWithout.size(), totalRooms));

        for (int i = 0; i < Math.min(roomsWithout.size(), 10); i++) {
            result.detail("No fan: " + roomsWithout.get(i));
        }
        if (roomsWithout.size() > 10) {
            result.detail(String.format("... and %d more", roomsWithout.size() - 10));
        }

        result.guidance("Add ceiling fan or supply diffuser to each habitable room");
        result.data("roomsWithout", roomsWithout.size());
        result.data("totalRooms", totalRooms);

        return result.build();
    }
}
